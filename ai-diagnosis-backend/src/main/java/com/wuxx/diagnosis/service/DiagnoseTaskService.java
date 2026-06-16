package com.wuxx.diagnosis.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskCreateRequest;
import com.wuxx.diagnosis.domain.DiagnoseTaskCreateResponse;
import com.wuxx.diagnosis.domain.DiagnoseTaskStatus;
import com.wuxx.diagnosis.domain.DiagnoseType;
import com.wuxx.diagnosis.mapper.DiagnoseTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DiagnoseTaskService {

    private final DiagnoseTaskMapper diagnoseTaskMapper;

    public DiagnoseTaskCreateResponse createTask(DiagnoseTaskCreateRequest request) {
        String taskNo = generateTaskNo();
        LocalDateTime now = LocalDateTime.now();

        DiagnoseTask task = new DiagnoseTask();
        task.setTaskNo(taskNo);
        task.setAppId(request.getAppId());
        task.setEnv(request.getEnv());
        task.setUserId(request.getUserId());
        task.setQuestion(request.getQuestion());
        task.setDiagnoseType(resolveDiagnoseType(request.getDiagnoseType()));
        task.setTargetUri(request.getTargetUri());
        task.setTargetClass(request.getTargetClass());
        task.setTargetMethod(request.getTargetMethod());
        task.setStatus(DiagnoseTaskStatus.CREATED.name());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        diagnoseTaskMapper.insert(task);

        return DiagnoseTaskCreateResponse.builder()
                .taskNo(taskNo)
                .status(task.getStatus())
                .build();
    }

    public DiagnoseTask getByTaskNo(String taskNo) {
        DiagnoseTask task = diagnoseTaskMapper.findByTaskNo(taskNo);
        if (task == null) {
            throw new IllegalArgumentException("诊断任务不存在，taskNo=" + taskNo);
        }
        return task;
    }

    public void markRunning(String taskNo) {
        getByTaskNo(taskNo);
        diagnoseTaskMapper.updateStatus(taskNo, DiagnoseTaskStatus.RUNNING.name());
    }

    public void markFinished(String taskNo, String conclusion) {
        getByTaskNo(taskNo);
        diagnoseTaskMapper.finishTask(taskNo, DiagnoseTaskStatus.FINISHED.name(), conclusion, null);
    }

    public void markFailed(String taskNo, String errorMessage) {
        getByTaskNo(taskNo);
        diagnoseTaskMapper.finishTask(taskNo, DiagnoseTaskStatus.FAILED.name(), null, errorMessage);
    }

    private String resolveDiagnoseType(String diagnoseType) {
        if (!StringUtils.hasText(diagnoseType)) {
            return DiagnoseType.UNKNOWN.name();
        }
        try {
            return DiagnoseType.valueOf(diagnoseType.trim().toUpperCase()).name();
        } catch (IllegalArgumentException exception) {
            return DiagnoseType.UNKNOWN.name();
        }
    }

    private String generateTaskNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "DIAG-" + date + "-" + random;
    }
}
