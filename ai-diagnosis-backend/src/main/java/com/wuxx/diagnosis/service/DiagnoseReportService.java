package com.wuxx.diagnosis.service;

import java.time.LocalDateTime;

import com.wuxx.diagnosis.domain.DiagnoseReport;
import com.wuxx.diagnosis.mapper.DiagnoseReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DiagnoseReportService {

    private final DiagnoseReportMapper diagnoseReportMapper;

    public void saveOrUpdate(String taskNo,
                             String reportTitle,
                             String reportMarkdown,
                             String reportJson,
                             String aiModel,
                             String promptVersion) {
        LocalDateTime now = LocalDateTime.now();
        DiagnoseReport report = new DiagnoseReport();
        report.setTaskNo(taskNo);
        report.setReportTitle(reportTitle);
        report.setReportMarkdown(reportMarkdown);
        report.setReportJson(reportJson);
        report.setAiModel(aiModel);
        report.setPromptVersion(promptVersion);
        report.setUpdatedAt(now);

        DiagnoseReport existing = diagnoseReportMapper.findByTaskNo(taskNo);
        if (existing == null) {
            report.setCreatedAt(now);
            diagnoseReportMapper.insert(report);
            return;
        }

        diagnoseReportMapper.updateByTaskNo(report);
    }

    public DiagnoseReport getByTaskNo(String taskNo) {
        DiagnoseReport report = diagnoseReportMapper.findByTaskNo(taskNo);
        if (report == null) {
            throw new IllegalArgumentException("诊断报告不存在，taskNo=" + taskNo);
        }
        return report;
    }
}
