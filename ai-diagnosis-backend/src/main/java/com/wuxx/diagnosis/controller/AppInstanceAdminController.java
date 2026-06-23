package com.wuxx.diagnosis.controller;

import java.util.List;

import com.wuxx.diagnosis.domain.AppInstanceTestResult;
import com.wuxx.diagnosis.domain.AppInstanceUpsertRequest;
import com.wuxx.diagnosis.domain.AppInstanceView;
import com.wuxx.diagnosis.security.AppInstanceAdminAccessGuard;
import com.wuxx.diagnosis.service.AppInstanceAdminService;
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
@RequestMapping("/api/admin/app-instances")
public class AppInstanceAdminController {

    private static final String TOKEN_HEADER = "X-Diagnosis-Admin-Token";

    private final AppInstanceAdminService adminService;
    private final AppInstanceAdminAccessGuard accessGuard;

    @GetMapping
    public List<AppInstanceView> list(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        accessGuard.check(token);
        return adminService.list();
    }

    @PostMapping
    public AppInstanceView create(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                  @Valid @RequestBody AppInstanceUpsertRequest request) {
        accessGuard.check(token);
        return adminService.create(request);
    }

    @PutMapping("/{id}")
    public AppInstanceView update(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                  @PathVariable Long id,
                                  @Valid @RequestBody AppInstanceUpsertRequest request) {
        accessGuard.check(token);
        return adminService.update(id, request);
    }

    @PostMapping("/{id}/test")
    public AppInstanceTestResult test(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                      @PathVariable Long id) {
        accessGuard.check(token);
        return adminService.test(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                       @PathVariable Long id) {
        accessGuard.check(token);
        adminService.delete(id);
    }
}
