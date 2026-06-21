package com.wuxx.diagnosis.sql.controller;

import java.util.List;

import com.wuxx.diagnosis.sql.datasource.SqlDatasourceService;
import com.wuxx.diagnosis.sql.domain.SqlDatasourceUpsertRequest;
import com.wuxx.diagnosis.sql.domain.SqlDatasourceView;
import com.wuxx.diagnosis.sql.security.SqlAdminAccessGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/sql/datasources")
public class SqlDatasourceAdminController {

    private static final String TOKEN_HEADER = "X-Diagnosis-Admin-Token";

    private final SqlDatasourceService datasourceService;
    private final SqlAdminAccessGuard accessGuard;

    @GetMapping
    public List<SqlDatasourceView> list(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        accessGuard.check(token);
        return datasourceService.list();
    }

    @PostMapping
    public SqlDatasourceView create(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                    @Valid @RequestBody SqlDatasourceUpsertRequest request) {
        accessGuard.check(token);
        return datasourceService.create(request);
    }

    @PutMapping("/{id}")
    public SqlDatasourceView update(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                    @PathVariable Long id,
                                    @Valid @RequestBody SqlDatasourceUpsertRequest request) {
        accessGuard.check(token);
        return datasourceService.update(id, request);
    }

    @PostMapping("/{id}/test")
    public void test(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                     @PathVariable Long id) {
        accessGuard.check(token);
        datasourceService.testConnection(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                       @PathVariable Long id) {
        accessGuard.check(token);
        datasourceService.delete(id);
    }
}
