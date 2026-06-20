package com.wuxx.diagnosis.controller;

import java.util.List;

import com.wuxx.diagnosis.domain.AppInstanceOption;
import com.wuxx.diagnosis.service.AppInstanceOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app-instances")
public class AppInstanceController {

    private final AppInstanceOptionService appInstanceOptionService;

    @GetMapping("/options")
    public List<AppInstanceOption> listOptions() {
        return appInstanceOptionService.listOnlineOptions();
    }
}
