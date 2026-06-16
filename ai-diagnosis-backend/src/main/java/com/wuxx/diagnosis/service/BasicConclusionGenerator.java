package com.wuxx.diagnosis.service;

import java.util.List;

import com.wuxx.diagnosis.domain.ArthasExecuteResponse;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import org.springframework.stereotype.Component;

@Component
public class BasicConclusionGenerator {

    public String generate(DiagnoseTask task, List<ArthasExecuteResponse> results) {
        return switch (task.getDiagnoseType()) {
            case "HIGH_CPU" -> highCpuConclusion();
            case "MEMORY_ABNORMAL" -> memoryConclusion();
            case "THREAD_BLOCKED" -> threadBlockedConclusion();
            case "SLOW_REQUEST" -> slowRequestConclusion();
            default -> "已完成基础诊断数据采集，请结合命令输出进一步分析。";
        };
    }

    private String highCpuConclusion() {
        return """
                已完成 CPU 高诊断基础数据采集。
                本次执行了 dashboard 和 thread -n 5。
                请重点关注 thread -n 5 输出中 CPU 占用最高的线程。
                如果高 CPU 线程位于业务方法，建议下一步使用 trace 追踪该方法耗时。
                如果高 CPU 线程为 GC 线程，建议结合 memory 和 GC 情况继续分析内存压力。
                """;
    }

    private String memoryConclusion() {
        return """
                已完成内存异常诊断基础数据采集。
                本次执行了 memory、dashboard 和 jvm。
                请重点关注 heap、non-heap、direct memory、GC 次数和 GC 时间。
                如果 Full GC 后内存无法明显下降，可能存在对象长期持有或缓存无界增长。
                本阶段不自动执行 heapdump，如需进一步确认，建议在低峰期人工审批后执行。
                """;
    }

    private String threadBlockedConclusion() {
        return """
                已完成线程阻塞诊断基础数据采集。
                本次执行了 dashboard、thread 和 thread -b。
                请重点关注 BLOCKED、WAITING、TIMED_WAITING 线程数量。
                如果 thread -b 返回阻塞线程，请优先分析对应锁对象和业务代码位置。
                如果大量线程等待连接池资源，建议检查数据库、Redis、HTTP/RPC 客户端连接池配置。
                """;
    }

    private String slowRequestConclusion() {
        return """
                已完成接口慢诊断基础数据采集。
                本次执行了 trace 目标类和方法。
                请重点关注 trace 输出中耗时占比最高的调用路径。
                如果耗时集中在 Mapper/DAO 层，建议下一阶段接入 SQL Explain 诊断。
                如果耗时集中在远程调用或缓存调用，建议检查下游服务耗时和超时配置。
                """;
    }
}
