package com.wuxx.diagnosis.service;

import java.util.List;

import com.wuxx.diagnosis.domain.ArthasCommandRecord;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskDetailResponse;
import com.wuxx.diagnosis.domain.DiagnoseTaskStatus;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
import com.wuxx.diagnosis.sse.DiagnoseEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DiagnoseTaskQueryService {

    private final DiagnoseTaskService diagnoseTaskService;

    private final ArthasCommandRecordMapper arthasCommandRecordMapper;

    private final DiagnoseEventService diagnoseEventService;

    private final DiagnoseTaskLifecycleService lifecycleService;

    public DiagnoseTaskDetailResponse detail(String taskNo) {
        DiagnoseTask task = lifecycleService.reconcile(taskNo);
        List<ArthasCommandRecord> records = arthasCommandRecordMapper.findByTaskNo(taskNo);
        List<DiagnoseEvent> events = diagnoseEventService.findAfter(taskNo, 0L);
        Long lastEventId = events.isEmpty() ? null : events.get(events.size() - 1).getId();
        boolean interrupted = DiagnoseTaskStatus.INTERRUPTED.name().equals(task.getStatus());
        boolean active = DiagnoseTaskStatus.CREATED.name().equals(task.getStatus())
                || DiagnoseTaskStatus.RUNNING.name().equals(task.getStatus());
        return DiagnoseTaskDetailResponse.builder()
                .task(task)
                .commandRecords(records)
                .events(events)
                .observationState(active ? "ACTIVE" : interrupted ? "INTERRUPTED" : "TERMINAL")
                .lastEventId(lastEventId)
                .restartAllowed(interrupted)
                .build();
    }
}
