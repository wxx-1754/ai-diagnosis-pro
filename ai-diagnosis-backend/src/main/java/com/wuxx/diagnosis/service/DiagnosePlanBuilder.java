package com.wuxx.diagnosis.service;

import java.util.ArrayList;
import java.util.List;

import com.wuxx.diagnosis.arthas.ArthasCommandFactory;
import com.wuxx.diagnosis.domain.DiagnosePlan;
import com.wuxx.diagnosis.domain.DiagnoseStep;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiagnosePlanBuilder {

    private final ArthasCommandFactory commandFactory;

    public DiagnosePlan build(DiagnoseTask task) {
        DiagnoseType type = DiagnoseType.valueOf(task.getDiagnoseType());
        List<DiagnoseStep> steps = new ArrayList<>();

        switch (type) {
            case HIGH_CPU -> {
                steps.add(step(1, "dashboard", commandFactory.buildCommand("dashboard"), "查看应用整体运行状态"));
                steps.add(step(2, "topThread", commandFactory.buildCommand("topThread"), "查看 CPU 占用最高线程"));
            }
            case MEMORY_ABNORMAL -> {
                steps.add(step(1, "memory", commandFactory.buildCommand("memory"), "查看 JVM 内存区域使用情况"));
                steps.add(step(2, "dashboard", commandFactory.buildCommand("dashboard"), "查看 GC 和整体运行状态"));
                steps.add(step(3, "jvm", commandFactory.buildCommand("jvm"), "查看 JVM 参数和运行信息"));
            }
            case THREAD_BLOCKED -> {
                steps.add(step(1, "dashboard", commandFactory.buildCommand("dashboard"), "查看应用整体线程和负载状态"));
                steps.add(step(2, "thread", commandFactory.buildCommand("thread"), "查看线程状态和堆栈"));
                steps.add(step(3, "threadBlock", commandFactory.buildCommand("threadBlock"), "查看阻塞线程或死锁信息"));
            }
            case SLOW_REQUEST -> {
                String traceCommand = commandFactory.buildTraceCommand(task.getTargetClass(), task.getTargetMethod());
                steps.add(step(1, "trace", traceCommand, "追踪目标接口方法调用耗时"));
            }
            default -> throw new IllegalArgumentException("Unsupported diagnoseType: " + task.getDiagnoseType());
        }

        return DiagnosePlan.builder()
                .taskNo(task.getTaskNo())
                .appId(task.getAppId())
                .env(task.getEnv())
                .diagnoseType(task.getDiagnoseType())
                .steps(steps)
                .build();
    }

    private DiagnoseStep step(Integer stepNo, String commandType, String command, String purpose) {
        return DiagnoseStep.builder()
                .stepNo(stepNo)
                .commandType(commandType)
                .command(command)
                .purpose(purpose)
                .build();
    }
}
