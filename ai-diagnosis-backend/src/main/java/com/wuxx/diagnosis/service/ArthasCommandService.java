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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArthasCommandService {

    private final AppInstanceService appInstanceService;

    private final ArthasCommandFactory commandFactory;

    private final ArthasCommandExecutor commandExecutor;

    private final ArthasCommandRecordMapper commandRecordMapper;

    private final DiagnosisArthasProperties properties;

    public ArthasExecuteResponse execute(ArthasExecuteRequest request) {
        String requestNo = generateRequestNo();
        AppInstance instance = appInstanceService.getOnlineInstance(request.getAppId(), request.getEnv());
        // 安全边界：外部只传 commandType，真实 Arthas 命令只能从后端固定映射生成。
        String command = commandFactory.buildCommand(request.getCommandType());
        log.info("Dispatch Arthas command, requestNo={}, appId={}, env={}, commandType={}, command={}",
                requestNo, request.getAppId(), request.getEnv(), request.getCommandType(), command);

        ArthasExecuteResponse response = commandExecutor.execute(
                instance,
                requestNo,
                command,
                request.getCommandType()
        );
        log.info("Arthas command completed, requestNo={}, success={}, costMillis={}",
                response.getRequestNo(), response.isSuccess(), response.getCostMillis());
        saveRecordQuietly(request, response);
        return response;
    }

    private void saveRecordQuietly(ArthasExecuteRequest request, ArthasExecuteResponse response) {
        try {
            // 审计记录是诊断链路的一部分，但保存失败不能覆盖 Arthas 命令本身的执行结果。
            ArthasCommandRecord record = new ArthasCommandRecord();
            record.setRequestNo(response.getRequestNo());
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
}
