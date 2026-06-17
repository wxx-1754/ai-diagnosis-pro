package com.wuxx.diagnosis.tools;

import com.wuxx.diagnosis.domain.ArthasExecuteRequest;
import com.wuxx.diagnosis.domain.ArthasExecuteResponse;
import com.wuxx.diagnosis.service.ArthasCommandService;
import com.wuxx.diagnosis.service.DiagnoseTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ArthasDiagnosticTools {

    private final ArthasCommandService arthasCommandService;

    private final DiagnoseTaskService diagnoseTaskService;

    private final ToolCallLimiter toolCallLimiter;

    @Tool(description = "获取 Java 应用实时运行面板，用于分析 CPU、线程、内存、GC、系统负载等整体状态")
    public ArthasExecuteResponse dashboard(String taskNo, String appId, String env) {
        return execute(taskNo, appId, env, "dashboard", "dashboard -n 1");
    }

    @Tool(description = "查询当前 CPU 占用最高的线程，用于排查 CPU 飙高问题。topN 最大为 10，默认 5")
    public ArthasExecuteResponse topThreads(String taskNo, String appId, String env, Integer topN) {
        int n = topN == null ? 5 : Math.min(Math.max(topN, 1), 10);
        return execute(taskNo, appId, env, "topThreads", "thread -n " + n);
    }

    @Tool(description = "根据线程 ID 查看线程堆栈，用于分析线程阻塞、死循环、慢调用、锁等待等问题")
    public ArthasExecuteResponse threadStack(String taskNo, String appId, String env, Long threadId) {
        if (threadId == null || threadId <= 0) {
            throw new IllegalArgumentException("threadId 非法");
        }
        return execute(taskNo, appId, env, "threadStack", "thread " + threadId);
    }

    @Tool(description = "查看 JVM 内存使用情况，用于分析堆、非堆、直接内存、Metaspace 等内存异常")
    public ArthasExecuteResponse memoryInfo(String taskNo, String appId, String env) {
        return execute(taskNo, appId, env, "memoryInfo", "memory");
    }

    @Tool(description = "查看 JVM 基本信息，包括 JVM 参数、线程数量、类加载、GC、文件句柄等")
    public ArthasExecuteResponse jvmInfo(String taskNo, String appId, String env) {
        return execute(taskNo, appId, env, "jvmInfo", "jvm");
    }

    @Tool(description = "追踪指定 Java 类和方法的调用耗时，用于分析接口慢或方法慢问题。只允许 className 和 methodName，执行次数固定为 3")
    public ArthasExecuteResponse traceMethod(String taskNo,
                                             String appId,
                                             String env,
                                             String className,
                                             String methodName) {
        validateClassName(className);
        validateMethodName(methodName);
        return execute(taskNo, appId, env, "traceMethod",
                "trace " + className + " " + methodName + " -n 3");
    }

    private ArthasExecuteResponse execute(String taskNo,
                                          String appId,
                                          String env,
                                          String toolName,
                                          String command) {
        ArthasExecuteRequest request = buildRequest(taskNo, appId, env, toolName);
        toolCallLimiter.check(taskNo, toolName);
        return arthasCommandService.executeCommand(request, command);
    }

    private ArthasExecuteRequest buildRequest(String taskNo,
                                              String appId,
                                              String env,
                                              String toolName) {
        if (!StringUtils.hasText(taskNo)) {
            throw new IllegalArgumentException("taskNo 不能为空");
        }
        if (!StringUtils.hasText(appId)) {
            throw new IllegalArgumentException("appId 不能为空");
        }
        if (!StringUtils.hasText(env)) {
            throw new IllegalArgumentException("env 不能为空");
        }

        diagnoseTaskService.checkTaskAppEnv(taskNo, appId, env);

        ArthasExecuteRequest request = new ArthasExecuteRequest();
        request.setTaskNo(taskNo);
        request.setAppId(appId);
        request.setEnv(env);
        request.setCommandType(toolName);
        return request;
    }

    private void validateClassName(String className) {
        if (!StringUtils.hasText(className)) {
            throw new IllegalArgumentException("className 不能为空");
        }
        if (!className.matches("[a-zA-Z_$][a-zA-Z\\d_$]*(\\.[a-zA-Z_$][a-zA-Z\\d_$]*)*")) {
            throw new IllegalArgumentException("className 不是合法 Java 全限定类名：" + className);
        }
    }

    private void validateMethodName(String methodName) {
        if (!StringUtils.hasText(methodName)) {
            throw new IllegalArgumentException("methodName 不能为空");
        }
        if (!methodName.matches("[a-zA-Z_$][a-zA-Z\\d_$]*")) {
            throw new IllegalArgumentException("methodName 不是合法 Java 方法名：" + methodName);
        }
    }
}
