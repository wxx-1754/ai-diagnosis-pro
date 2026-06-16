package com.wuxx.diagnosis.arthas;

import com.wuxx.diagnosis.domain.AppInstance;
import com.wuxx.diagnosis.domain.ArthasExecuteResponse;

public interface ArthasCommandExecutor {

    ArthasExecuteResponse execute(AppInstance instance, String requestNo, String command, String commandType);
}
