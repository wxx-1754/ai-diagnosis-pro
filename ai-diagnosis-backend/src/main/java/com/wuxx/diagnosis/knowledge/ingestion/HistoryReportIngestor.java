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
 * 历史诊断报告自动沉淀为知识库条目。
 *
 * <p>报告生成并落库后触发，将报告 Markdown 作为 source_type=HISTORY_REPORT 的知识入库，
 * 形成"平台记忆"——同类问题再次出现时可被检索复用。按 source_ref=taskNo 去重，
 * 同一任务不重复入库；报告重新生成时因内容变化会通过 content_hash 命中已存在则跳过，
 * 保证不重复消耗 embedding。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "diagnosis.kb", name = "enabled", havingValue = "true")
public class HistoryReportIngestor {

    private static final String SOURCE_TYPE = "HISTORY_REPORT";

    private final KnowledgeBaseProperties properties;
    private final KnowledgeIngestionService ingestionService;
    private final KbDocumentMapper documentMapper;
    private final DiagnoseTaskService diagnoseTaskService;
    private final DiagnoseReportService diagnoseReportService;

    /**
     * 沉淀单个任务的报告。失败仅记录日志，不影响主诊断链路。
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
            KbDocument document = ingestionService.ingest(request, SOURCE_TYPE,
                    markdown.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            log.info("History report ingested, taskNo={}, docNo={}, chunkCount={}",
                    taskNo, document.getDocNo(), document.getChunkCount());
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
