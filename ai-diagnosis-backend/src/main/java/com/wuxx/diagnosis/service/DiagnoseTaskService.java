package com.wuxx.diagnosis.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskCreateRequest;
import com.wuxx.diagnosis.domain.DiagnoseTaskCreateResponse;
import com.wuxx.diagnosis.domain.DiagnoseTaskStatus;
import com.wuxx.diagnosis.domain.DiagnoseType;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
import com.wuxx.diagnosis.mapper.DiagnoseReportMapper;
import com.wuxx.diagnosis.mapper.DiagnoseTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DiagnoseTaskService {

    private final DiagnoseTaskMapper diagnoseTaskMapper;

    private final ArthasCommandRecordMapper arthasCommandRecordMapper;

    private final DiagnoseReportMapper diagnoseReportMapper;

    private final DiagnoseEventService diagnoseEventService;

    @Autowired
    public DiagnoseTaskService(DiagnoseTaskMapper diagnoseTaskMapper,
                               ArthasCommandRecordMapper arthasCommandRecordMapper,
                               DiagnoseReportMapper diagnoseReportMapper,
                               DiagnoseEventService diagnoseEventService) {
        this.diagnoseTaskMapper = diagnoseTaskMapper;
        this.arthasCommandRecordMapper = arthasCommandRecordMapper;
        this.diagnoseReportMapper = diagnoseReportMapper;
        this.diagnoseEventService = diagnoseEventService;
    }

    public DiagnoseTaskService(DiagnoseTaskMapper diagnoseTaskMapper,
                               ArthasCommandRecordMapper arthasCommandRecordMapper,
                               DiagnoseReportMapper diagnoseReportMapper) {
        this(diagnoseTaskMapper, arthasCommandRecordMapper, diagnoseReportMapper, null);
    }

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

    public void updateIntent(String taskNo, String diagnoseType, String targetClass, String targetMethod) {
        getByTaskNo(taskNo);
        diagnoseTaskMapper.updateIntent(taskNo, resolveDiagnoseType(diagnoseType), targetClass, targetMethod);
    }

    public void checkTaskAppEnv(String taskNo, String appId, String env) {
        DiagnoseTask task = getByTaskNo(taskNo);
        if (!equalsIgnoreCase(task.getAppId(), appId) || !equalsIgnoreCase(task.getEnv(), env)) {
            throw new SecurityException("诊断任务与目标应用不匹配，taskNo=" + taskNo);
        }
    }

    public void markFinished(String taskNo, String conclusion) {
        getByTaskNo(taskNo);
        diagnoseTaskMapper.finishTask(taskNo, DiagnoseTaskStatus.FINISHED.name(), conclusion, null);
    }

    public void markFailed(String taskNo, String errorMessage) {
        getByTaskNo(taskNo);
        diagnoseTaskMapper.finishTask(taskNo, DiagnoseTaskStatus.FAILED.name(), null, errorMessage);
    }

    public boolean markInterrupted(String taskNo, String reason) {
        return diagnoseTaskMapper.markInterruptedIfActive(taskNo, reason) > 0;
    }

    /**
     * 硬删除诊断任务及其关联数据。RUNNING/CREATED 状态的任务可能仍在进行中（持有内存 SSE 与后台流程），
     * 不允许删除，需先结束或失败后再删。
     */
    @Transactional
    public void deleteTask(String taskNo) {
        DiagnoseTask task = getByTaskNo(taskNo);
        DiagnoseTaskStatus status = parseStatus(task.getStatus());
        if (status == DiagnoseTaskStatus.RUNNING || status == DiagnoseTaskStatus.CREATED) {
            throw new IllegalStateException("任务仍在进行中，无法删除，taskNo=" + taskNo);
        }
        arthasCommandRecordMapper.deleteByTaskNo(taskNo);
        diagnoseReportMapper.deleteByTaskNo(taskNo);
        if (diagnoseEventService != null) {
            diagnoseEventService.deleteByTaskNo(taskNo);
        }
        diagnoseTaskMapper.deleteByTaskNo(taskNo);
    }

    private DiagnoseTaskStatus parseStatus(String status) {
        try {
            return DiagnoseTaskStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            return DiagnoseTaskStatus.CREATED;
        }
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

    private boolean equalsIgnoreCase(String first, String second) {
        return first != null && second != null && first.equalsIgnoreCase(second);
    }
}
