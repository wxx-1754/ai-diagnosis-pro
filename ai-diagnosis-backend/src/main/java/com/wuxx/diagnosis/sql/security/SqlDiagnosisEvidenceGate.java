package com.wuxx.diagnosis.sql.security;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wuxx.diagnosis.config.DiagnosisSqlProperties;
import com.wuxx.diagnosis.domain.ArthasCommandRecord;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * SQL 诊断证据门禁。
 *
 * <p>接口慢诊断中，只有当 Agent 已通过 Arthas trace 命中 MyBatis/JDBC 调用链、且该节点耗时达到阈值时，
 * 才允许进入 SQL 诊断流程（{@code listSqlDatasources} / {@code explainCapturedSql} / SQL 捕获 watch）。
 *
 * <p>这把 system prompt 规则 8b“调用链出现 MyBatis/JDBC 耗时”的软约束下沉为代码级硬约束，
 * 避免模型在以下情况误入 SQL 诊断分支：
 * <ul>
 *   <li>循环证据：SQL 捕获用的 watch 命令本身含 ibatis/baseexecutor 关键字，不能当作“进入 SQL 诊断的证据”；</li>
 *   <li>关键字误判：任何 MyBatis 应用的 trace 调用链都会出现 Mapper 节点，但 SQL 可能并非瓶颈。</li>
 * </ul>
 *
 * <p>判据不是“关键字出现”，而是“trace 输出里 MyBatis/JDBC 节点的耗时占比或绝对耗时超过阈值”。
 *
 * <p><b>输出格式</b>：Arthas HTTP API 返回的是结构化 JSON 树（非交互式控制台的文本树），
 * 每个节点形如 {@code "className" : "...", "cost" : 6798343800, ...}，其中 {@code cost} 为<b>纳秒</b>、
 * 字段按字母序排列（{@code className} 紧邻 {@code cost}），且<b>不含</b>预计算的百分比。
 * 因此本门禁成对提取 {@code (className, cost)}，将纳秒换算为毫秒，并以全树最大 cost 作为总耗时代理计算占比。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlDiagnosisEvidenceGate {

    private static final List<String> SQL_EVIDENCE_KEYWORDS = List.of(
            "mybatis",
            "ibatis",
            "mapper",
            "baseexecutor",
            "statementhandler",
            "preparedstatement",
            "boundsql",
            "jdbc",
            "java.sql",
            "jdbctemplate",
            "resultset"
    );

    private static final long NANOS_PER_MILLIS = 1_000_000L;

    /**
     * Arthas HTTP API 的 trace 节点：className 与 cost 字段按字母序相邻，
     * 形如 {@code "className" : "org.apache.ibatis...",\n  "cost" : 6798343800,}。
     * 该正则成对提取，对 4000 字符的输出摘要截断依然鲁棒（只要该节点完整即命中）。
     */
    private static final Pattern JSON_NODE_COST = Pattern.compile(
            "\"className\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"cost\"\\s*:\\s*(\\d+)");

    /** 交互式控制台文本树格式的兜底：形如 `---[85% 170ms ] className:method()。 */
    private static final Pattern TEXT_NODE_PERCENT = Pattern.compile("\\[?(\\d+(?:\\.\\d+)?)\\s*%[^\\]]*?(\\d+(?:\\.\\d+)?)\\s*ms");

    private final ArthasCommandRecordMapper commandRecordMapper;

    private final DiagnosisSqlProperties properties;

    /**
     * 校验当前任务是否已具备进入 SQL 诊断流程的证据。
     *
     * <p>证据定义：存在至少一条成功的 trace 命令记录，其输出中存在某节点同时满足
     * “含 MyBatis/JDBC 关键字”且“耗时占比 ≥ 阈值 或 绝对耗时 ≥ 阈值”。
     *
     * @throws IllegalStateException 无证据时抛出，消息引导 Agent 先做 trace 并确认 SQL 层耗时
     */
    public void verify(String taskNo) {
        List<ArthasCommandRecord> records = commandRecordMapper.findByTaskNo(taskNo);
        if (records == null || records.isEmpty()) {
            throw noEvidence(taskNo);
        }
        boolean hasEvidence = records.stream()
                .filter(this::isTraceRecord)
                .filter(record -> Boolean.TRUE.equals(record.getSuccess()))
                .anyMatch(this::containsSqlHotspot);
        if (!hasEvidence) {
            log.info("SQL evidence gate rejected, taskNo={}, recordCount={}, traceSuccessCount={}",
                    taskNo, records.size(),
                    records.stream().filter(r -> isTraceRecord(r) && Boolean.TRUE.equals(r.getSuccess())).count());
            throw noEvidence(taskNo);
        }
        log.info("SQL evidence gate passed, taskNo={}, recordCount={}", taskNo, records.size());
    }

    /**
     * 只认可 trace 命令作为证据来源。watch/dashboard 等不计入，尤其排除 SQL 捕获用的 watch 命令
     * （其命令文本含 ibatis 关键字，属于循环证据）。
     */
    private boolean isTraceRecord(ArthasCommandRecord record) {
        String command = record.getCommand();
        return StringUtils.hasText(command) && command.trim().startsWith("trace ");
    }

    private boolean containsSqlHotspot(ArthasCommandRecord record) {
        String output = record.getOutputExcerpt();
        if (!StringUtils.hasText(output)) {
            return false;
        }
        return hasJsonTreeHotspot(output) || hasTextTreeHotspot(output);
    }

    /**
     * 解析 Arthas HTTP API 的 JSON 树：成对提取 (className, cost)，以全树最大 cost 作为总耗时代理，
     * 判断含 SQL 关键字的节点是否为耗时热点。
     */
    private boolean hasJsonTreeHotspot(String output) {
        List<double[]> sqlNodes = new ArrayList<>();
        double maxCost = 0;
        Matcher matcher = JSON_NODE_COST.matcher(output);
        while (matcher.find()) {
            String className = matcher.group(1);
            double cost = Double.parseDouble(matcher.group(2));
            maxCost = Math.max(maxCost, cost);
            if (matchesSqlKeyword(className)) {
                sqlNodes.add(new double[] {cost, 0});
            }
        }
        if (sqlNodes.isEmpty() || maxCost <= 0) {
            return false;
        }
        double minPct = properties.getEvidenceMinCostPercent();
        double minMs = properties.getEvidenceMinCostMillis();
        for (double[] node : sqlNodes) {
            double costMs = node[0] / NANOS_PER_MILLIS;
            double pct = node[0] / maxCost * 100.0;
            if (pct >= minPct || costMs >= minMs) {
                return true;
            }
        }
        return false;
    }

    /**
     * 兜底：交互式控制台文本树格式（含 [占比% 耗时ms] 标记）。
     * 主要用于非 HTTP API 场景或未来格式变化时尽力识别。
     */
    private boolean hasTextTreeHotspot(String output) {
        double minPct = properties.getEvidenceMinCostPercent();
        double minMs = properties.getEvidenceMinCostMillis();
        Matcher matcher = TEXT_NODE_PERCENT.matcher(output);
        while (matcher.find()) {
            int start = Math.max(0, matcher.start() - 200);
            int end = Math.min(output.length(), matcher.end() + 200);
            String window = output.substring(start, end);
            if (!matchesSqlKeyword(window)) {
                continue;
            }
            double pct = Double.parseDouble(matcher.group(1));
            double ms = Double.parseDouble(matcher.group(2));
            if (pct >= minPct || ms >= minMs) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSqlKeyword(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase();
        return SQL_EVIDENCE_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private static IllegalStateException noEvidence(String taskNo) {
        return new IllegalStateException(
                "当前任务的 trace 调用链未发现 MyBatis/JDBC 耗时热点，禁止进入 SQL 诊断流程。"
                        + "请先使用 trace 对目标 Controller 方法采样，"
                        + "仅当调用链中 MyBatis、Mapper、Executor 或 JDBC 节点耗时占比或绝对耗时明显偏高时，"
                        + "再调用 SQL 诊断工具；若 SQL 非瓶颈，应回到 Java 侧继续定位。taskNo=" + taskNo
        );
    }
}
