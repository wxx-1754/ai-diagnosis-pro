package com.wuxx.diagnosis.sql.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import com.wuxx.diagnosis.config.DiagnosisSqlProperties;
import com.wuxx.diagnosis.domain.ArthasCommandRecord;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqlDiagnosisEvidenceGateTest {

    private ArthasCommandRecordMapper commandRecordMapper;
    private DiagnosisSqlProperties properties;
    private SqlDiagnosisEvidenceGate gate;

    @BeforeEach
    void setUp() {
        commandRecordMapper = mock(ArthasCommandRecordMapper.class);
        properties = new DiagnosisSqlProperties();
        // 默认阈值：占比 30%，绝对耗时 100ms
        gate = new SqlDiagnosisEvidenceGate(commandRecordMapper, properties);
    }

    @Test
    void rejectsWhenNoRecords() {
        when(commandRecordMapper.findByTaskNo("T1")).thenReturn(Collections.emptyList());
        assertThatThrownBy(() -> gate.verify("T1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未发现 MyBatis/JDBC 耗时热点");
    }

    @Test
    void rejectsWhenTraceHasNoSqlKeyword() {
        // 真实 Arthas HTTP JSON：调用链完全没有 MyBatis/JDBC 节点（纯远程调用）
        ArthasCommandRecord record = traceRecord(arthasJson(
                "com.example.OrderService", 200_000_000L,
                "service.RemoteClient", 180_000_000L));
        when(commandRecordMapper.findByTaskNo("T2")).thenReturn(List.of(record));
        assertThatThrownBy(() -> gate.verify("T2"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsWhenSqlPresentButNotTheBottleneck() {
        // 用户场景的反面：trace 里出现 MyBatis，但只占 5%/10ms，不是瓶颈
        // 根节点 200ms，OrderMapper 仅 10ms（占比 5%）
        ArthasCommandRecord record = traceRecord(arthasJson(
                "com.example.OrderService", 200_000_000L,
                "org.apache.ibatis.executor.BaseExecutor", 10_000_000L));
        when(commandRecordMapper.findByTaskNo("T3")).thenReturn(List.of(record));
        assertThatThrownBy(() -> gate.verify("T3"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SQL 非瓶颈");
    }

    @Test
    void rejectsWatchCommandAsCircularEvidence() {
        // SQL 捕获用的 watch 命令本身含 ibatis 关键字，绝不能当作证据
        ArthasCommandRecord watch = new ArthasCommandRecord();
        watch.setCommand("watch org.apache.ibatis.executor.BaseExecutor query "
                + "'{params[0].getBoundSql(params[1]).sql,#cost}' -n 1 -x 4");
        watch.setOutputExcerpt("@A[true] cost=5ms");
        watch.setSuccess(true);
        when(commandRecordMapper.findByTaskNo("T4")).thenReturn(List.of(watch));
        assertThatThrownBy(() -> gate.verify("T4"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsFailedTraceRecord() {
        ArthasCommandRecord record = traceRecord(arthasJson(
                "org.apache.ibatis.executor.BaseExecutor", 170_000_000L));
        record.setSuccess(false);
        when(commandRecordMapper.findByTaskNo("T5")).thenReturn(List.of(record));
        assertThatThrownBy(() -> gate.verify("T5"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsWhenSqlNodeIsHotspotByPercent() {
        // 用户真实场景：OrderMapper 6.8s，占比 ~99.98%，是瓶颈
        ArthasCommandRecord record = traceRecord(arthasJson(
                "com.wuxx.demo.service.OrderService$$SpringCGLIB$$0", 6_799_540_000L,
                "com.wuxx.demo.mapper.OrderMapper", 6_798_343_800L));
        when(commandRecordMapper.findByTaskNo("T6")).thenReturn(List.of(record));
        assertThatCode(() -> gate.verify("T6")).doesNotThrowAnyException();
    }

    @Test
    void allowsWhenSqlNodeExceedsMillisThresholdEvenWithLowPercent() {
        // 占比没到 30%，但绝对耗时 150ms 超过 100ms 阈值（根 1000ms，SQL 150ms = 15%）
        ArthasCommandRecord record = traceRecord(arthasJson(
                "com.example.OrderService", 1_000_000_000L,
                "service.RemoteClient", 800_000_000L,
                "java.sql.PreparedStatement", 150_000_000L));
        when(commandRecordMapper.findByTaskNo("T7")).thenReturn(List.of(record));
        assertThatCode(() -> gate.verify("T7")).doesNotThrowAnyException();
    }

    @Test
    void allowsWhenJdbcKeywordInOutput() {
        ArthasCommandRecord record = traceRecord(arthasJson(
                "com.example.OrderService", 500_000_000L,
                "java.sql.PreparedStatement", 450_000_000L));
        when(commandRecordMapper.findByTaskNo("T8")).thenReturn(List.of(record));
        assertThatCode(() -> gate.verify("T8")).doesNotThrowAnyException();
    }

    @Test
    void allowsWhenUserReportedRealArthasJsonIsTruncated() {
        // 用户贴出的真实响应可能被 4000 字符摘要截断；只要热点节点完整即应识别。
        // 模拟截断尾部闭合括号，使 JSON 不完整但 SQL 热点节点在前且完整。
        String full = arthasJson(
                "com.wuxx.demo.mapper.OrderMapper", 6_798_343_800L,
                "com.wuxx.demo.service.OrderService$$SpringCGLIB$$0", 6_799_540_000L);
        String truncated = full.substring(0, full.length() - 20);
        ArthasCommandRecord record = traceRecord(truncated);
        when(commandRecordMapper.findByTaskNo("T9")).thenReturn(List.of(record));
        assertThatCode(() -> gate.verify("T9")).doesNotThrowAnyException();
    }

    private ArthasCommandRecord traceRecord(String output) {
        ArthasCommandRecord record = new ArthasCommandRecord();
        record.setCommand("trace com.example.OrderController list -n 3");
        record.setOutputExcerpt(output);
        record.setSuccess(true);
        return record;
    }

    /**
     * 构造 Arthas HTTP API 的 trace JSON 树格式：className 与 cost 按字母序相邻。
     * 每个参数对为 (className, costNanos)。
     */
    private String arthasJson(Object... classNameAndCost) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"results\":[{\"type\":\"trace\",\"root\":{\"children\":[");
        for (int i = 0; i < classNameAndCost.length; i += 2) {
            String className = (String) classNameAndCost[i];
            long cost = ((Number) classNameAndCost[i + 1]).longValue();
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"className\":\"").append(className).append("\",")
                    .append("\"cost\":").append(cost).append(",")
                    .append("\"methodName\":\"m\",")
                    .append("\"times\":1,")
                    .append("\"totalCost\":").append(cost).append(",")
                    .append("\"type\":\"method\"}");
        }
        sb.append("]}}]}");
        return sb.toString();
    }
}
