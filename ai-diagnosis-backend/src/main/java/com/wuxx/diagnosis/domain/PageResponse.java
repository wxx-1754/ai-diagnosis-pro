package com.wuxx.diagnosis.domain;

import java.util.List;

import lombok.Data;

/**
 * 分页响应包络。
 */
@Data
public class PageResponse<T> {

    private List<T> list;

    private long total;

    private int pageNo;

    private int pageSize;

    public PageResponse() {
    }

    public PageResponse(List<T> list, long total, int pageNo, int pageSize) {
        this.list = list;
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }
}
