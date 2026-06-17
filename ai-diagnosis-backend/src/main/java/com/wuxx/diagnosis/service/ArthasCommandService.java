package com.wuxx.diagnosis.service;

import java.time.LocalDateTime;
import java.util.UUID;

import com.wuxx.diagnosis.arthas.ArthasCommandExecutor;
import com.wuxx.diagnosis.arthas.ArthasCommandFactory;
import com.wuxx.diagnosis.config.DiagnosisArthasProperties;
import com.wuxx.diagnosis.domain.AppInstance;
import com.wuxx.diagnosis.domain.ArthasCommandRecord;
import com.wuxx.diagnosis.domain.ArthasExecuteRequest;
import com.wuxx.diagnosis.domain.ArthasExecuteResponse;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
import com.wuxx.diagnosis.sse.DiagnoseEvent;
import com.wuxx.diagnosis.sse.DiagnoseEventType;
import com.wuxx.diagnosis.sse.DiagnoseSseManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArthasCommandService {

    private final AppInstanceService appInstanceService;

    private final ArthasCommandFactory commandFactory;

    private final ArthasCommandExecutor commandExecutor;

    private final ArthasCommandRecordMapper commandRecordMapper;

    private final DiagnosisArthasProperties properties;

    private final DiagnoseTaskService diagnoseTaskService;

    private final DiagnoseSseManager sseManager;

    public ArthasExecuteResponse execute(ArthasExecuteRequest request) {
        // 安全边界：外部只传 commandType，真实 Arthas 命令只能从后端固定映射生成。
        String command = commandFactory.buildCommand(request.getCommandType());
        return executeCommand(request, command);
    }

    public ArthasExecuteResponse executeCommand(ArthasExecuteRequest request, String command) {
        String requestNo = generateRequestNo();
        if (StringUtils.hasText(request.getTaskNo())) {
            diagnoseTaskService.markRunning(request.getTaskNo());
        }

        sendEvent(request, DiagnoseEventType.TOOL_CALL_START, "正在执行 Arthas 命令：" + command,
                command, null, null);
        try {
            AppInstance instance = appInstanceService.getOnlineInstance(request.getAppId(), request.getEnv());
            log.info("Dispatch Arthas command, requestNo={}, taskNo={}, appId={}, env={}, commandType={}, command={}",
                    requestNo, request.getTaskNo(), request.getAppId(), request.getEnv(), request.getCommandType(), command);

            ArthasExecuteResponse response = commandExecutor.execute(
                    instance,
                    requestNo,
                    command,
                    request.getCommandType()
            );
            log.info("Arthas command completed, requestNo={}, success={}, costMillis={}",
                    response.getRequestNo(), response.isSuccess(), response.getCostMillis());
            saveRecordQuietly(request, response);

            if (response.isSuccess()) {
                sendEvent(request, DiagnoseEventType.TOOL_CALL_SUCCESS, "Arthas 命令执行成功：" + command,
                        command, true, response);
            } else {
                sendEvent(request, DiagnoseEventType.TOOL_CALL_FAILED,
                        "Arthas 命令执行失败：" + response.getErrorMessage(), command, false, response);
            }

            if (StringUtils.hasText(request.getTaskNo()) && !response.isSuccess()) {
                diagnoseTaskService.markFailed(request.getTaskNo(), response.getErrorMessage());
            }
            return response;
        } catch (Exception exception) {
            sendEvent(request, DiagnoseEventType.TOOL_CALL_FAILED,
                    "Arthas 命令执行异常：" + exception.getMessage(), command, false, null);
            throw exception;
        }
    }

    private void saveRecordQuietly(ArthasExecuteRequest request, ArthasExecuteResponse response) {
        try {
            // 审计记录是诊断链路的一部分，但保存失败不能覆盖 Arthas 命令本身的执行结果。
            ArthasCommandRecord record = new ArthasCommandRecord();
            record.setRequestNo(response.getRequestNo());
            record.setTaskNo(request.getTaskNo());
            record.setAppId(response.getAppId());
            record.setEnv(response.getEnv());
            record.setCommand(response.getCommand());
            record.setCommandType(request.getCommandType());
            record.setSuccess(response.isSuccess());
            record.setCostMillis(response.getCostMillis());
            record.setOutputExcerpt(excerpt(response.getOutput(), properties.getAuditOutputExcerptLength()));
            record.setErrorMessage(excerpt(response.getErrorMessage(), properties.getAuditOutputExcerptLength()));
            record.setCreatedAt(LocalDateTime.now());
            commandRecordMapper.insert(record);
            log.info("Saved Arthas command audit record, requestNo={}", response.getRequestNo());
        } catch (Exception exception) {
            log.error("Failed to save Arthas command record, requestNo={}", response.getRequestNo(), exception);
        }
    }

    private String excerpt(String text, int maxLength) {
        if (text == null || maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private String generateRequestNo() {
        return "ARTHAS-" + UUID.randomUUID().toString().replace("-", "");
    }

    private void sendEvent(ArthasExecuteRequest request,
                           DiagnoseEventType eventType,
                           String message,
                           String command,
                           Boolean success,
                           Object data) {
        if (sseManager == null || request == null || !StringUtils.hasText(request.getTaskNo())) {
            return;
        }
        sseManager.send(request.getTaskNo(), DiagnoseEvent.builder()
                .taskNo(request.getTaskNo())
                .eventType(eventType.name())
                .message(message)
                .command(command)
                .toolName(request.getCommandType())
                .success(success)
                .data(data)
                .time(LocalDateTime.now())
                .build());
    }
}
