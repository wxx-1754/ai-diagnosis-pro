package com.wuxx.diagnosis.sql.security;

import org.springframework.stereotype.Component;

@Component
public class SqlSensitiveDataMasker {

    public String maskForAi(String sql) {
        if (sql == null) {
            return "";
        }
        return sql
                .replaceAll("'(?:''|[^'])*'", "'***'")
                .replaceAll("(?<![A-Za-z0-9_$])[-+]?\\d+(?:\\.\\d+)?", "?");
    }
}
