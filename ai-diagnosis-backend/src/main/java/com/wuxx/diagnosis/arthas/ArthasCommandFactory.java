package com.wuxx.diagnosis.arthas;

import org.springframework.stereotype.Component;

@Component
public class ArthasCommandFactory {

    public String buildCommand(String commandType) {
        return ArthasCommandType.fromCode(commandType).getCommand();
    }
}
