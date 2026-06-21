package com.wuxx.diagnosis.domain;

import java.util.List;

import com.wuxx.diagnosis.sse.DiagnoseEvent;
import com.wuxx.diagnosis.sql.domain.SqlDiagnosisRecord;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiagnoseTaskDetailResponse {

    private DiagnoseTask task;

    private List<ArthasCommandRecord> commandRecords;

    private List<DiagnoseEvent> events;

    private String observationState;

    private Long lastEventId;

    private boolean restartAllowed;

    private List<SqlDiagnosisRecord> sqlDiagnosisRecords;

    private SqlDiagnosisRecord latestSqlDiagnosis;
}
