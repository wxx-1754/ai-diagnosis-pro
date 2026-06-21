package com.wuxx.diagnosis.sql.metadata;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SqlMetadataBundle {

    private String tableMetaJson;
    private String indexMetaJson;
    private String tableStatsJson;
}
