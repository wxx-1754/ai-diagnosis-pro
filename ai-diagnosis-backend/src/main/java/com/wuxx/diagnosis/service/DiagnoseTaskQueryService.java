package com.wuxx.diagnosis.service;

import java.util.List;

import com.wuxx.diagnosis.domain.ArthasCommandRecord;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskDetailResponse;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DiagnoseTaskQueryService {

    private final DiagnoseTaskService diagnoseTaskService;

    private final ArthasCommandRecordMapper arthasCommandRecordMapper;

    public DiagnoseTaskDetailResponse detail(String taskNo) {
        DiagnoseTask task = diagnoseTaskService.getByTaskNo(taskNo);
        List<ArthasCommandRecord> records = arthasCommandRecordMapper.findByTaskNo(taskNo);
        return DiagnoseTaskDetailResponse.builder()
                .task(task)
                .commandRecords(records)
                .build();
    }
}
