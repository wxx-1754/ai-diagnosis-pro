package com.wuxx.diagnosis.domain;

/**
 * App Instance 连通性测试结果。
 */
public record AppInstanceTestResult(boolean ok, long latencyMs, String message) {
}
