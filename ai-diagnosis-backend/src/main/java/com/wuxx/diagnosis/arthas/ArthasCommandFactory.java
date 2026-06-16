package com.wuxx.diagnosis.arthas;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ArthasCommandFactory {

    public String buildCommand(String commandType) {
        return ArthasCommandType.fromCode(commandType).getCommand();
    }

    public String buildTraceCommand(String className, String methodName) {
        validateClassName(className);
        validateMethodName(methodName);
        return "trace " + className + " " + methodName + " -n 3";
    }

    private void validateClassName(String className) {
        if (!StringUtils.hasText(className)) {
            throw new IllegalArgumentException("targetClass不能为空");
        }
        if (!className.matches("[a-zA-Z_$][a-zA-Z\\d_$]*(\\.[a-zA-Z_$][a-zA-Z\\d_$]*)*")) {
            throw new IllegalArgumentException("targetClass不是合法Java类名：" + className);
        }
    }

    private void validateMethodName(String methodName) {
        if (!StringUtils.hasText(methodName)) {
            throw new IllegalArgumentException("targetMethod不能为空");
        }
        if (!methodName.matches("[a-zA-Z_$][a-zA-Z\\d_$]*")) {
            throw new IllegalArgumentException("targetMethod不是合法Java方法名：" + methodName);
        }
    }
}
