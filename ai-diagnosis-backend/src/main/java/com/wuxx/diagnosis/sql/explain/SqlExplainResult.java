package com.wuxx.diagnosis.sql.explain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SqlExplainResult {

    private String explainSql;
    private String explainResult;
    private long costMillis;
}
