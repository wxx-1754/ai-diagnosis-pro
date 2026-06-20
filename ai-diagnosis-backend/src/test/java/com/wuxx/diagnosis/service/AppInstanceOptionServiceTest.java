package com.wuxx.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.wuxx.diagnosis.domain.AppInstanceOption;
import org.junit.jupiter.api.Test;

class AppInstanceOptionServiceTest {

    @Test
    void listOnlineOptionsReturnsDatabaseOptions() {
        AppInstanceOption option = new AppInstanceOption();
        option.setAppId("order-service");
        option.setAppName("订单服务");
        option.setEnv("prod");

        AppInstanceOptionService service = new AppInstanceOptionService(() -> List.of(option));

        assertThat(service.listOnlineOptions()).containsExactly(option);
    }
}
