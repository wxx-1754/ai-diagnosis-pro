package com.wuxx.diagnosis.domain;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiagnoseTaskDetailResponse {

    private DiagnoseTask task;

    private List<ArthasCommandRecord> commandRecords;
}
