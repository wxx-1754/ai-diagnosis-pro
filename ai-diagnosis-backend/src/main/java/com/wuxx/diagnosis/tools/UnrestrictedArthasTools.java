package com.wuxx.diagnosis.tools;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.wuxx.diagnosis.config.DiagnosisArthasProperties;
import com.wuxx.diagnosis.domain.ArthasCommandRecord;
import com.wuxx.diagnosis.domain.ArthasExecuteRequest;
import com.wuxx.diagnosis.domain.ArthasExecuteResponse;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
import com.wuxx.diagnosis.service.ArthasCommandService;
import com.wuxx.diagnosis.service.DiagnoseTaskService;
import com.wuxx.diagnosis.sql.security.SqlDiagnosisEvidenceGate;
import com.wuxx.diagnosis.sse.DiagnoseEvent;
import com.wuxx.diagnosis.sse.DiagnoseEventType;
import com.wuxx.diagnosis.sse.DiagnoseSseManager;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class UnrestrictedArthasTools {

    private final ArthasCommandService arthasCommandService;
    private final DiagnoseTaskService diagnoseTaskService;
    private final ArthasCommandRecordMapper commandRecordMapper;
    private final DiagnosisArthasProperties properties;
    private final DiagnoseSseManager sseManager;
    private final SqlDiagnosisEvidenceGate sqlEvidenceGate;

    @Tool(description = """
            在开发或测试环境执行一条原生 Arthas 命令。可以使用 watch、trace、stack、tt、monitor、
            sc、sm、jad、ognl 等 Arthas 支持的命令。命令会原样发送给目标 Arthas HTTP API。
            这是高权限诊断工具，仅在服务端显式开启且任务环境被允许时可用。
            watch/trace 等等待流量的命令必须限制采集次数，优先使用 -n 1，并控制输出深度和大小。
            """)
    public ArthasExecuteResponse executeRawArthasCommand(String taskNo,
                                                         String appId,
                                                         String env,
                                                         String command,
                                                         String reason) {
        if (!StringUtils.hasText(taskNo) || !StringUtils.hasText(appId) || !StringUtils.hasText(env)) {
            throw new IllegalArgumentException("taskNo、appId、env不能为空");
        }
        if (!StringUtils.hasText(command)) {
            throw new IllegalArgumentException("command不能为空");
        }
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("必须说明执行原生 Arthas 命令的诊断原因");
        }
        diagnoseTaskService.checkTaskAppEnv(taskNo, appId, env);
        checkCallLimit(taskNo);

        ArthasExecuteRequest request = new ArthasExecuteRequest();
        request.setTaskNo(taskNo);
        request.setAppId(appId);
        request.setEnv(env);
        request.setCommandType("rawArthas");
        String normalizedCommand = command.trim();
        boolean sqlWatch = isSqlWatch(normalizedCommand);
        if (sqlWatch) {
            // SQL 捕获 watch 是 SQL 诊断流程的入口之一，必须先有 trace 命中 MyBatis/JDBC 热点的证据，
            // 否则禁止执行，避免模型在未确认 SQL 为瓶颈时直接走 SQL 诊断分支。
            sqlEvidenceGate.verify(taskNo);
            send(taskNo, DiagnoseEventType.SQL_CAPTURE_WAITING,
                    "Agent 已启动 SQL Watch，请在采样窗口内请求目标 Controller",
                    Map.of("command", normalizedCommand, "reason", reason));
        }
        ArthasExecuteResponse response = arthasCommandService.executeCommand(request, normalizedCommand);
        if (sqlWatch) {
            send(taskNo,
                    response.isSuccess() ? DiagnoseEventType.SQL_CAPTURED : DiagnoseEventType.SQL_CAPTURE_FAILED,
                    response.isSuccess() ? "Arthas Watch 已返回 SQL 捕获结果"
                            : "SQL Watch 捕获失败：" + response.getErrorMessage(),
                    Map.of(
                            "command", normalizedCommand,
                            "success", response.isSuccess(),
                            "output", response.getOutput() == null ? "" : response.getOutput(),
                            "errorMessage", response.getErrorMessage() == null ? "" : response.getErrorMessage()
                    ));
        }
        return response;
    }

    private void checkCallLimit(String taskNo) {
        List<ArthasCommandRecord> records = commandRecordMapper.findByTaskNo(taskNo);
        long used = records == null ? 0 : records.stream()
                .filter(record -> "rawArthas".equals(record.getCommandType()))
                .count();
        if (used >= properties.getUnrestrictedAiMaxCallsPerTask()) {
            throw new SecurityException("AI 原生 Arthas 命令调用次数超过限制："
                    + properties.getUnrestrictedAiMaxCallsPerTask());
        }
    }

    private boolean isSqlWatch(String command) {
        String lower = command.toLowerCase();
        return lower.startsWith("watch ")
                && (lower.contains("mybatis")
                || lower.contains("baseexecutor")
                || lower.contains("statementhandler")
                || lower.contains("jdbc")
                || lower.contains("preparedstatement"));
    }

    private void send(String taskNo, DiagnoseEventType type, String message, Object data) {
        sseManager.send(taskNo, DiagnoseEvent.builder()
                .taskNo(taskNo)
                .eventType(type.name())
                .message(message)
                .data(data)
                .time(LocalDateTime.now())
                .build());
    }
}
