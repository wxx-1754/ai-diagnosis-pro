package com.wuxx.diagnosis.sql.controller;

import java.util.List;

import com.wuxx.diagnosis.sql.datasource.SqlDatasourceService;
import com.wuxx.diagnosis.sql.domain.SqlDatasourceOption;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sql/datasources")
public class SqlDatasourceController {

    private final SqlDatasourceService datasourceService;

    @GetMapping("/options")
    public List<SqlDatasourceOption> options(@RequestParam String env) {
        return datasourceService.options(env);
    }
}
