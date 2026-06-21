package com.wuxx.diagnosis.sql.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SqlDatasourceOption {

    private String datasourceCode;
    private String datasourceName;
    private String dbType;
    private String env;
}
