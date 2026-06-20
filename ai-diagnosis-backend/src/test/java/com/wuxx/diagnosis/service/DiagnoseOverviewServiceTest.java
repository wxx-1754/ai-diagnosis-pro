package com.wuxx.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.wuxx.diagnosis.domain.DiagnoseOverviewStats;
import com.wuxx.diagnosis.domain.DiagnoseTaskQuery;
import com.wuxx.diagnosis.mapper.DiagnoseTaskMapper;
import org.junit.jupiter.api.Test;

class DiagnoseOverviewServiceTest {

    @Test
    void pageReturnsEmptyListWhenCountIsZero() {
        StubMapper mapper = new StubMapper();
        mapper.count = 0L;
        DiagnoseOverviewService service = new DiagnoseOverviewService(mapper);

        var page = service.page(new DiagnoseTaskQuery());

        assertThat(page.getList()).isEmpty();
        assertThat(page.getTotal()).isZero();
        assertThat(page.getPageNo()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(20);
    }

    @Test
    void statsComputesSuccessRateAndTrend() {
        StubMapper mapper = new StubMapper();
        mapper.statusCounts = Map.of("total", 10L, "running", 1L, "finished", 7L, "failed", 2L, "created", 0L);
        mapper.typeCounts = List.of(Map.of("diagnoseType", "HIGH_CPU", "cnt", 4L));
        LocalDate today = LocalDate.now();
        mapper.trend = List.of(Map.of("day", java.sql.Date.valueOf(today), "cnt", 3L, "finished", 2L, "failed", 1L));
        DiagnoseOverviewService service = new DiagnoseOverviewService(mapper);

        DiagnoseOverviewStats stats = service.stats(7);

        assertThat(stats.getTotal()).isEqualTo(10L);
        assertThat(stats.getFinished()).isEqualTo(7L);
        assertThat(stats.getFailed()).isEqualTo(2L);
        // finished / (finished + failed) = 7 / 9
        assertThat(stats.getSuccessRate()).isEqualTo(77.8);
        assertThat(stats.getDailyTrend()).hasSize(7);
        assertThat(stats.getDailyTrend().get(6).getDate()).isEqualTo(today.toString());
        assertThat(stats.getDailyTrend().get(6).getCount()).isEqualTo(3L);
    }

    private static class StubMapper implements DiagnoseTaskMapper {

        long count;
        Map<String, Object> statusCounts = Map.of();
        List<Map<String, Object>> typeCounts = List.of();
        List<Map<String, Object>> trend = List.of();

        @Override
        public long count(com.wuxx.diagnosis.domain.DiagnoseTaskQuery query) {
            return count;
        }

        @Override
        public java.util.List<com.wuxx.diagnosis.domain.DiagnoseTaskListItem> pageQuery(
                com.wuxx.diagnosis.domain.DiagnoseTaskQuery query, int offset, int limit) {
            return List.of();
        }

        @Override
        public java.util.Map<String, Object> countByStatus(java.time.LocalDateTime startTime) {
            return statusCounts;
        }

        @Override
        public java.util.List<java.util.Map<String, Object>> countByType(java.time.LocalDateTime startTime) {
            return typeCounts;
        }

        @Override
        public java.util.List<java.util.Map<String, Object>> dailyTrend(java.time.LocalDateTime startTime) {
            return trend;
        }

        @Override
        public int insert(com.wuxx.diagnosis.domain.DiagnoseTask task) {
            return 0;
        }

        @Override
        public com.wuxx.diagnosis.domain.DiagnoseTask findByTaskNo(String taskNo) {
            return null;
        }

        @Override
        public int deleteByTaskNo(String taskNo) {
            return 0;
        }

        @Override
        public int updateStatus(String taskNo, String status) {
            return 0;
        }

        @Override
        public int markInterruptedIfActive(String taskNo, String reason) {
            return 0;
        }

        @Override
        public java.util.List<com.wuxx.diagnosis.domain.DiagnoseTask> findActiveTasks() {
            return java.util.List.of();
        }

        @Override
        public int updateIntent(String taskNo, String diagnoseType, String targetClass, String targetMethod) {
            return 0;
        }

        @Override
        public int finishTask(String taskNo, String status, String conclusion, String errorMessage) {
            return 0;
        }
    }
}
