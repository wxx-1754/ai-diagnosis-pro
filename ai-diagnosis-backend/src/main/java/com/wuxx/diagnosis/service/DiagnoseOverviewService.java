package com.wuxx.diagnosis.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.wuxx.diagnosis.domain.DiagnoseOverviewStats;
import com.wuxx.diagnosis.domain.DiagnoseTaskListItem;
import com.wuxx.diagnosis.domain.DiagnoseTaskQuery;
import com.wuxx.diagnosis.domain.PageResponse;
import com.wuxx.diagnosis.mapper.DiagnoseTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DiagnoseOverviewService {

    private final DiagnoseTaskMapper diagnoseTaskMapper;

    public PageResponse<DiagnoseTaskListItem> page(DiagnoseTaskQuery query) {
        long total = diagnoseTaskMapper.count(query);
        List<DiagnoseTaskListItem> list = total == 0
                ? List.of()
                : diagnoseTaskMapper.pageQuery(query, query.offset(), query.limit());
        int pageNo = query.getPageNo() == null || query.getPageNo() < 1 ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 20 : query.limit();
        return new PageResponse<>(list, total, pageNo, pageSize);
    }

    public DiagnoseOverviewStats stats(int days) {
        int safeDays = Math.max(1, Math.min(days, 90));
        LocalDateTime startTime = LocalDate.now().minusDays(safeDays - 1L).atStartOfDay();

        DiagnoseOverviewStats stats = new DiagnoseOverviewStats();

        Map<String, Object> statusCounts = diagnoseTaskMapper.countByStatus(startTime);
        long total = toLong(statusCounts.get("total"));
        long running = toLong(statusCounts.get("running"));
        long finished = toLong(statusCounts.get("finished"));
        long failed = toLong(statusCounts.get("failed"));
        long created = toLong(statusCounts.get("created"));
        stats.setTotal(total);
        stats.setRunning(running);
        stats.setFinished(finished);
        stats.setFailed(failed);
        stats.setCreated(created);
        long settled = finished + failed;
        stats.setSuccessRate(settled == 0 ? 0d
                : round1(finished * 100.0 / settled));

        for (Map<String, Object> row : diagnoseTaskMapper.countByType(startTime)) {
            String type = String.valueOf(row.get("diagnoseType"));
            stats.getTypeCounts().put(type, toLong(row.get("cnt")));
        }

        stats.setDailyTrend(buildDailyTrend(startTime, safeDays));
        return stats;
    }

    private List<DiagnoseOverviewStats.DailyTrend> buildDailyTrend(LocalDateTime startTime, int days) {
        Map<String, DiagnoseOverviewStats.DailyTrend> byDate = new TreeMap<>();
        for (int i = 0; i < days; i++) {
            String key = LocalDate.now().minusDays(days - 1L - i).toString();
            byDate.put(key, new DiagnoseOverviewStats.DailyTrend(key, 0, 0, 0));
        }
        for (Map<String, Object> row : diagnoseTaskMapper.dailyTrend(startTime)) {
            Object day = row.get("day");
            if (day == null) {
                continue;
            }
            String key = day instanceof java.sql.Date
                    ? ((java.sql.Date) day).toLocalDate().toString()
                    : String.valueOf(day);
            DiagnoseOverviewStats.DailyTrend trend = byDate.getOrDefault(key,
                    new DiagnoseOverviewStats.DailyTrend(key, 0, 0, 0));
            trend.setCount(toLong(row.get("cnt")));
            trend.setFinished(toLong(row.get("finished")));
            trend.setFailed(toLong(row.get("failed")));
            byDate.put(key, trend);
        }
        return new ArrayList<>(byDate.values());
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return new BigDecimal(String.valueOf(value)).longValue();
    }

    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, java.math.RoundingMode.HALF_UP).doubleValue();
    }
}
