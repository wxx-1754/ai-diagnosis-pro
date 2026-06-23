package com.wuxx.diagnosis.knowledge.ingestion;

import com.wuxx.diagnosis.domain.DiagnoseReport;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.knowledge.config.KnowledgeBaseProperties;
import com.wuxx.diagnosis.knowledge.domain.KbDocument;
import com.wuxx.diagnosis.knowledge.domain.KnowledgeTextRequest;
import com.wuxx.diagnosis.knowledge.mapper.KbDocumentMapper;
import com.wuxx.diagnosis.service.DiagnoseReportService;
import com.wuxx.diagnosis.service.DiagnoseTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 历史诊断报告自动提交到知识库审核队列。
 *
 * <p>报告生成并落库后触发，保存脱敏原文并标记为 PENDING_REVIEW。
 * 审核通过前不分片、不生成向量，也不会参与检索。按 source_ref=taskNo 去重，
 * 同一任务不重复提交审核。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "diagnosis.kb", name = "enabled", havingValue = "true")
public class HistoryReportIngestor {

    private final KnowledgeBaseProperties properties;
    private final KnowledgeIngestionService ingestionService;
    private final KbDocumentMapper documentMapper;
    private final DiagnoseTaskService diagnoseTaskService;
    private final DiagnoseReportService diagnoseReportService;

    /**
     * 提交单个任务的报告等待审核。失败仅记录日志，不影响主诊断链路。
     */
    public void ingest(String taskNo) {
        if (!properties.isAutoIngestHistory()) {
            return;
        }
        try {
            if (!StringUtils.hasText(taskNo)) {
                return;
            }
            if (documentMapper.findBySourceRef(taskNo) != null) {
                log.debug("History report already ingested, skip, taskNo={}", taskNo);
                return;
            }
            DiagnoseTask task = diagnoseTaskService.getByTaskNo(taskNo);
            DiagnoseReport report = diagnoseReportService.getByTaskNo(taskNo);
            String markdown = report.getReportMarkdown();
            if (!StringUtils.hasText(markdown)
                    || markdown.length() < properties.getHistoryMinContentLength()) {
                log.debug("History report too short to ingest, taskNo={}, length={}",
                        taskNo, markdown == null ? 0 : markdown.length());
                return;
            }

            KnowledgeTextRequest request = new KnowledgeTextRequest();
            request.setTitle(buildTitle(task, report));
            request.setContent(markdown);
            request.setCategory("CASE");
            request.setDiagnoseType(task.getDiagnoseType());
            request.setAppId(task.getAppId());
            request.setEnv(task.getEnv());
            request.setSourceRef(taskNo);
            request.setUploadedBy("system");
            KbDocument document = ingestionService.createPendingReview(request,
                    markdown.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            log.info("History report submitted for review, taskNo={}, docNo={}, qualityStatus={}",
                    taskNo, document.getDocNo(), document.getQualityStatus());
        } catch (Exception exception) {
            log.warn("History report ingest failed, taskNo={}, message={}",
                    taskNo, exception.getMessage());
        }
    }

    private String buildTitle(DiagnoseTask task, DiagnoseReport report) {
        String title = StringUtils.hasText(report.getReportTitle()) ? report.getReportTitle() : "诊断报告";
        return title + "（" + task.getTaskNo() + "）";
    }
}
