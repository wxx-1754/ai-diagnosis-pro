package com.wuxx.diagnosis.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 概览页顶部统计聚合结果。
 */
@Data
public class DiagnoseOverviewStats {

    private long total;

    private long running;

    private long finished;

    private long failed;

    private long created;

    /**
     * 成功率，0-100，保留一位小数。total 为 0 时为 0。
     */
    private double successRate;

    /**
     * 按诊断类型计数，key 为类型名（HIGH_CPU 等），value 为数量。
     */
    private Map<String, Long> typeCounts = new LinkedHashMap<>();

    /**
     * 近 N 天每日趋势，按时间升序。
     */
    private List<DailyTrend> dailyTrend = new ArrayList<>();

    @Data
    public static class DailyTrend {

        private String date;

        private long count;

        private long finished;

        private long failed;

        public DailyTrend() {
        }

        public DailyTrend(String date, long count, long finished, long failed) {
            this.date = date;
            this.count = count;
            this.finished = finished;
            this.failed = failed;
        }
    }
}
