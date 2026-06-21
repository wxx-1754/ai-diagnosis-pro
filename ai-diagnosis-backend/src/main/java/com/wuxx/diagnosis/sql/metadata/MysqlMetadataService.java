package com.wuxx.diagnosis.sql.metadata;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxx.diagnosis.sql.datasource.DynamicSqlDatasourceFactory;
import com.wuxx.diagnosis.sql.domain.SqlDatasourceConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MysqlMetadataService {

    private final DynamicSqlDatasourceFactory datasourceFactory;
    private final ObjectMapper objectMapper;

    public SqlMetadataBundle collect(SqlDatasourceConfig datasource, String tableName) {
        JdbcTemplate jdbcTemplate = datasourceFactory.getJdbcTemplate(datasource);
        String schema = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!StringUtils.hasText(schema)) {
            throw new IllegalStateException("当前数据源未选择默认数据库");
        }
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
                SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_KEY, EXTRA, COLUMN_COMMENT
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=? AND TABLE_NAME=?
                ORDER BY ORDINAL_POSITION
                """, schema, tableName);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("主表不存在或当前账号无权读取元数据：" + tableName);
        }
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList("""
                SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, COLLATION, CARDINALITY,
                       SUB_PART, NULLABLE, INDEX_TYPE
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA=? AND TABLE_NAME=?
                ORDER BY INDEX_NAME, SEQ_IN_INDEX
                """, schema, tableName);
        List<Map<String, Object>> stats = jdbcTemplate.queryForList("""
                SELECT TABLE_NAME, ENGINE, TABLE_ROWS, AVG_ROW_LENGTH, DATA_LENGTH, INDEX_LENGTH,
                       DATA_FREE, UPDATE_TIME, TABLE_COLLATION, TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA=? AND TABLE_NAME=?
                """, schema, tableName);
        try {
            return SqlMetadataBundle.builder()
                    .tableMetaJson(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(columns))
                    .indexMetaJson(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(indexes))
                    .tableStatsJson(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(stats))
                    .build();
        } catch (Exception exception) {
            throw new IllegalStateException("SQL元数据序列化失败", exception);
        }
    }
}
