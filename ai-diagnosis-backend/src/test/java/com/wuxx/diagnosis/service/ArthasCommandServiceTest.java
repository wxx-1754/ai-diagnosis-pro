package com.wuxx.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.wuxx.diagnosis.arthas.ArthasCommandExecutor;
import com.wuxx.diagnosis.arthas.ArthasCommandFactory;
import com.wuxx.diagnosis.config.DiagnosisArthasProperties;
import com.wuxx.diagnosis.domain.AppInstance;
import com.wuxx.diagnosis.domain.ArthasCommandRecord;
import com.wuxx.diagnosis.domain.ArthasExecuteRequest;
import com.wuxx.diagnosis.domain.ArthasExecuteResponse;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
import org.junit.jupiter.api.Test;

class ArthasCommandServiceTest {

    @Test
    void executeBuildsFixedCommandAndSavesAuditRecord() {
        CapturingExecutor executor = new CapturingExecutor(true);
        CapturingRecordMapper recordMapper = new CapturingRecordMapper();
        ArthasCommandService service = service(executor, recordMapper);

        ArthasExecuteResponse response = service.execute(request("topThread"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(executor.command).isEqualTo("thread -n 5");
        assertThat(recordMapper.records).hasSize(1);
        ArthasCommandRecord record = recordMapper.records.getFirst();
        assertThat(record.getRequestNo()).startsWith("ARTHAS-");
        assertThat(record.getAppId()).isEqualTo("order-service");
        assertThat(record.getEnv()).isEqualTo("test");
        assertThat(record.getCommand()).isEqualTo("thread -n 5");
        assertThat(record.getCommandType()).isEqualTo("topThread");
        assertThat(record.getSuccess()).isTrue();
        assertThat(record.getOutputExcerpt()).isEqualTo("12345678");
    }

    @Test
    void executeSavesAuditRecordWhenCommandFails() {
        CapturingExecutor executor = new CapturingExecutor(false);
        CapturingRecordMapper recordMapper = new CapturingRecordMapper();
        ArthasCommandService service = service(executor, recordMapper);

        ArthasExecuteResponse response = service.execute(request("jvm"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(recordMapper.records).hasSize(1);
        ArthasCommandRecord record = recordMapper.records.getFirst();
        assertThat(record.getSuccess()).isFalse();
        assertThat(record.getErrorMessage()).isEqualTo("connecti");
    }

    private ArthasCommandService service(ArthasCommandExecutor executor, ArthasCommandRecordMapper recordMapper) {
        AppInstanceService appInstanceService = new AppInstanceService((appId, env) -> instance());
        DiagnosisArthasProperties properties = new DiagnosisArthasProperties();
        properties.setAuditOutputExcerptLength(8);
        return new ArthasCommandService(
                appInstanceService,
                new ArthasCommandFactory(),
                executor,
                recordMapper,
                properties
        );
    }

    private ArthasExecuteRequest request(String commandType) {
        ArthasExecuteRequest request = new ArthasExecuteRequest();
        request.setAppId("order-service");
        request.setEnv("test");
        request.setCommandType(commandType);
        return request;
    }

    private AppInstance instance() {
        AppInstance instance = new AppInstance();
        instance.setAppId("order-service");
        instance.setEnv("test");
        instance.setIp("127.0.0.1");
        instance.setArthasHttpPort(8563);
        instance.setAccessMode("HTTP");
        return instance;
    }

    private static class CapturingExecutor implements ArthasCommandExecutor {

        private final boolean success;

        private String command;

        CapturingExecutor(boolean success) {
            this.success = success;
        }

        @Override
        public ArthasExecuteResponse execute(AppInstance instance, String requestNo, String command, String commandType) {
            this.command = command;
            return ArthasExecuteResponse.builder()
                    .requestNo(requestNo)
                    .appId(instance.getAppId())
                    .env(instance.getEnv())
                    .command(command)
                    .success(success)
                    .output(success ? "1234567890" : null)
                    .errorMessage(success ? null : "connection refused")
                    .costMillis(10)
                    .build();
        }
    }

    private static class CapturingRecordMapper implements ArthasCommandRecordMapper {

        private final List<ArthasCommandRecord> records = new ArrayList<>();

        @Override
        public int insert(ArthasCommandRecord record) {
            records.add(record);
            return 1;
        }
    }
}
