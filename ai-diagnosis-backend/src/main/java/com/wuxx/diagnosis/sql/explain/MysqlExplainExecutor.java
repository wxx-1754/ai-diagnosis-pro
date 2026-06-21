package com.wuxx.diagnosis.sql.explain;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxx.diagnosis.sql.datasource.DynamicSqlDatasourceFactory;
import com.wuxx.diagnosis.sql.domain.SqlDatasourceConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MysqlExplainExecutor {

    private final DynamicSqlDatasourceFactory datasourceFactory;
    private final ObjectMapper objectMapper;

    public SqlExplainResult explain(SqlDatasourceConfig datasource, String normalizedSql) {
        long start = System.currentTimeMillis();
        String explainSql = "EXPLAIN FORMAT=JSON " + normalizedSql;
        JdbcTemplate jdbcTemplate = datasourceFactory.getJdbcTemplate(datasource);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(explainSql);
        if (rows.isEmpty() || rows.get(0).isEmpty()) {
            throw new IllegalStateException("MySQL Explain未返回执行计划");
        }
        Object value = rows.get(0).values().iterator().next();
        String result;
        try {
            if (value instanceof String text) {
                result = objectMapper.readTree(text).toPrettyString();
            } else {
                result = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rows);
            }
        } catch (Exception exception) {
            result = String.valueOf(value);
        }
        return SqlExplainResult.builder()
                .explainSql(explainSql)
                .explainResult(result)
                .costMillis(System.currentTimeMillis() - start)
                .build();
    }
}
