package com.wuxx.diagnosis.service;

import java.util.List;

import com.wuxx.diagnosis.domain.AppInstanceOption;
import com.wuxx.diagnosis.mapper.AppInstanceOptionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppInstanceOptionService {

    private final AppInstanceOptionMapper appInstanceOptionMapper;

    public List<AppInstanceOption> listOnlineOptions() {
        return appInstanceOptionMapper.findOnlineOptions();
    }
}
