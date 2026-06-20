package com.wuxx.diagnosis.domain;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 诊断事件概览列表的查询条件。
 */
@Data
public class DiagnoseTaskQuery {

    private String appId;

    private String env;

    private String diagnoseType;

    private String status;

    /**
     * 模糊匹配 task_no 或 question。
     */
    private String keyword;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer pageNo = 1;

    private Integer pageSize = 20;

    public int offset() {
        int page = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 20 : pageSize;
        return (page - 1) * size;
    }

    public int limit() {
        int size = pageSize == null || pageSize < 1 ? 20 : pageSize;
        return Math.min(size, 100);
    }
}
