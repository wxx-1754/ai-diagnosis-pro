package com.wuxx.diagnosis.service.ai;

import java.util.ArrayList;
import java.util.List;

import com.wuxx.diagnosis.config.DiagnosisAiProperties;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseType;
import com.wuxx.diagnosis.tools.ArthasDiagnosticTools;
import com.wuxx.diagnosis.tools.UnrestrictedArthasTools;
import com.wuxx.diagnosis.sql.tool.SqlAgentDiagnosticTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "diagnosis.ai", name = "enable", havingValue = "true")
public class ToolCallingDiagnosisAgent {

    private static final String SYSTEM_PROMPT = """
            你是一个仅用于开发和测试环境的 Java + SQL 自主诊断 Agent。

            你可以使用受控工具和开发测试环境专用的原生 Arthas 工具收集运行时数据。

            你必须遵守：
            1. 只能调用系统提供的工具。
            2. 原生 Arthas 命令只允许用于当前 taskNo/appId/env，不得切换目标。
            3. watch、trace、monitor、stack、tt 等等待流量命令必须优先使用 -n 1，限制深度和输出大小。
            4. 每条原生命令必须提供明确的 reason，说明它验证哪个诊断假设。
            5. 如果用户问题是 CPU 高，优先调用 dashboard 和 topThreads。
            6. 如果用户问题是内存异常，优先调用 memoryInfo、dashboard、jvmInfo。
            7. 如果用户问题是线程阻塞，优先调用 dashboard，再查看线程信息。
            8. 如果用户问题是接口慢：
               a. 先 trace 用户提供的 Controller 方法；
               b. 如果调用链出现 MyBatis、Mapper、Executor 或 JDBC 耗时，必须继续使用原生 watch 捕获实际 SQL 与参数；
               c. MyBatis 场景优先观察 org.apache.ibatis.executor.BaseExecutor 的 query/update，
                  可通过 OGNL 调用 MappedStatement.getBoundSql(parameterObject) 获取 SQL；
                  推荐模板：
                  watch org.apache.ibatis.executor.BaseExecutor query
                  '{params[0].id,params[0].getBoundSql(params[1]).sql,params[1],#cost}' -n 1 -x 4
                  如果 BaseExecutor 未命中，再观察：
                  watch org.apache.ibatis.executor.statement.PreparedStatementHandler query
                  '{target.boundSql.sql,target.boundSql.parameterObject,#cost}' -n 1 -x 4
               d. 捕获到可执行 SELECT SQL 后，调用 listSqlDatasources；
               e. 数据源唯一时立即调用 explainCapturedSql；存在多个数据源时按名称选择最匹配者，
                  无法判断时在结论中明确说明数据源歧义；
               f. SQL 含 ? 时，应依据 parameterObject 和参数映射还原字面量；只有参数对应关系明确时才能还原，
                  不得猜测参数。还原后调用 explainCapturedSql；
               g. Explain 成功后必须结合 Java 调用耗时、执行计划、索引和表统计生成联合结论。
            9. watch 未命中通常表示采样窗口内没有真实请求，应明确提示需要在采样期间调用目标接口。
            10. 如果工具返回信息不足，需要明确说明还需要补充哪些信息。
            11. 输出最终诊断报告时必须基于工具返回结果，不得编造。
            """;

    private final ChatClient chatClient;

    private final ArthasDiagnosticTools arthasDiagnosticTools;

    private final UnrestrictedArthasTools unrestrictedArthasTools;

    private final SqlAgentDiagnosticTools sqlAgentDiagnosticTools;

    private final DiagnosisAiProperties properties;

    public String diagnose(DiagnoseTask task) {
        log.info("AI Tool Calling diagnose invoked, taskNo={}, diagnoseType={}, targetClass={}, targetMethod={}",
                task.getTaskNo(), task.getDiagnoseType(), task.getTargetClass(), task.getTargetMethod());
        long start = System.currentTimeMillis();
        String result = chatClient.prompt()
                .options(OpenAiChatOptions.builder()
                        .temperature(properties.getReportTemperature())
                        .build())
                .system(SYSTEM_PROMPT)
                .user("""
                        诊断任务信息：
                        taskNo: %s
                        appId: %s
                        env: %s
                        用户问题: %s
                        诊断类型: %s
                        targetClass: %s
                        targetMethod: %s

                        请根据问题选择合适的诊断工具，并在工具返回结果基础上生成诊断结论。
                        """.formatted(
                        task.getTaskNo(),
                        task.getAppId(),
                        task.getEnv(),
                        nullToEmpty(task.getQuestion()),
                        nullToEmpty(task.getDiagnoseType()),
                        nullToEmpty(task.getTargetClass()),
                        nullToEmpty(task.getTargetMethod())
                ))
                .tools(buildTools(task))
                .call()
                .content();
        long costMillis = System.currentTimeMillis() - start;
        String conclusion = result == null ? "" : (result.length() > 500 ? result.substring(0, 500) + "...(truncated)" : result);
        log.info("AI Tool Calling diagnose finished, taskNo={}, costMillis={}, resultBlank={}, conclusion={}",
                task.getTaskNo(), costMillis, result == null || result.isBlank(), conclusion);
        return result;
    }

    private String nullToEmpty(String value) {
        return StringUtils.hasText(value) ? value : "";
    }

    /**
     * 按诊断类型裁剪可用的工具集。
     *
     * <p>SQL 诊断工具仅在接口慢（SLOW_REQUEST）场景下注册，避免 CPU 高、内存异常、线程阻塞等
     * 无关场景下模型主动调用 SQL 工具产生偏航。SLOW_REQUEST 场景下仍由
     * {@link com.wuxx.diagnosis.sql.security.SqlDiagnosisEvidenceGate} 做证据门禁。
     */
    private Object[] buildTools(DiagnoseTask task) {
        List<Object> tools = new ArrayList<>();
        tools.add(arthasDiagnosticTools);
        tools.add(unrestrictedArthasTools);
        if (DiagnoseType.SLOW_REQUEST.name().equals(task.getDiagnoseType())) {
            tools.add(sqlAgentDiagnosticTools);
        }
        return tools.toArray();
    }
}
