package com.wuxx.diagnosis.service.ai;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxx.diagnosis.config.DiagnosisAiProperties;
import com.wuxx.diagnosis.domain.ai.DiagnosisInsightSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "diagnosis.ai", name = "enable", havingValue = "true")
public class DiagnosisInsightSummarizer {

    private static final int ROOT_CAUSE_LIMIT = 72;
    private static final int REASON_LIMIT = 56;
    private static final int EXPECTED_EFFECT_LIMIT = 100;
    private static final int ACTION_LIMIT = 56;
    private static final int MAX_REASONS = 3;
    private static final int MAX_ACTIONS = 3;

    private static final String SYSTEM_PROMPT = """
            你是 Java 生产诊断结果摘要助手。
            你会收到一份完整诊断报告，需要重新理解报告后提炼用于窄栏界面展示的摘要。

            必须遵守：
            1. 只能输出 JSON，不要输出 Markdown 或代码围栏。
            2. rootCause 必须是一句话，最多 60 个中文字符，只写最核心根因。
            3. specificReasons 必须从诊断报告“根因分析”中的解释和证据提炼，最多 3 条，每条最多 45 个中文字符。
            4. specificReasons 要解释为什么形成该根因结论，不得重复 rootCause，不得写修复建议。
            5. expectedEffect 必须是一句话，最多 80 个中文字符；没有可靠数字时写“需修复后通过压测或同口径监控验证”。
            6. recommendedActions 最多 3 条，每条最多 45 个中文字符，必须具体、可执行、可验证。
            7. 不得照抄整段报告，不得包含标题、编号前缀、Markdown 符号或换行。
            8. 不得补充报告中没有依据的结论或效果数字。
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final DiagnosisAiProperties properties;

    public DiagnosisInsightSummary summarize(String reportMarkdown) {
        if (!StringUtils.hasText(reportMarkdown)) {
            return fallback("诊断报告为空，暂时无法生成摘要。");
        }

        try {
            String response = chatClient.prompt()
                    .options(OpenAiChatOptions.builder().temperature(0.0).build())
                    .system(SYSTEM_PROMPT)
                    .user("""
                            请根据下面的完整诊断报告生成界面摘要：

                            %s

                            只输出以下 JSON：
                            {
                              "rootCause": "一句话根因",
                              "specificReasons": ["具体原因一", "具体原因二", "具体原因三"],
                              "expectedEffect": "一句话预期效果",
                              "recommendedActions": ["操作一", "操作二", "操作三"]
                            }
                            """.formatted(reportMarkdown))
                    .call()
                    .content();

            return parseAndNormalize(response);
        } catch (Exception exception) {
            return fallback("AI 摘要生成失败，请下载完整报告查看。");
        }
    }

    DiagnosisInsightSummary parseAndNormalize(String response) {
        try {
            DiagnosisInsightSummary summary = objectMapper.readValue(
                    cleanupJson(response),
                    DiagnosisInsightSummary.class
            );
            return normalize(summary);
        } catch (Exception exception) {
            return fallback("AI 摘要生成失败，请下载完整报告查看。");
        }
    }

    DiagnosisInsightSummary fallback(String message) {
        DiagnosisInsightSummary summary = new DiagnosisInsightSummary();
        summary.setRootCause(trimToLimit(message, ROOT_CAUSE_LIMIT));
        summary.setSpecificReasons(List.of("完整报告未能生成可用的具体原因摘要。"));
        summary.setExpectedEffect("暂无可靠效果摘要，需修复后验证。");
        summary.setRecommendedActions(List.of("下载完整诊断报告并人工复核。"));
        return summary;
    }

    private DiagnosisInsightSummary normalize(DiagnosisInsightSummary summary) {
        if (summary == null) {
            return fallback("AI 摘要为空，请下载完整报告查看。");
        }

        summary.setRootCause(StringUtils.hasText(summary.getRootCause())
                ? trimToLimit(clean(summary.getRootCause()), ROOT_CAUSE_LIMIT)
                : "当前报告未形成明确的简要根因。");
        summary.setExpectedEffect(StringUtils.hasText(summary.getExpectedEffect())
                ? trimToLimit(clean(summary.getExpectedEffect()), EXPECTED_EFFECT_LIMIT)
                : "需修复后通过压测或同口径监控验证。");

        List<String> reasons = normalizeItems(summary.getSpecificReasons(), MAX_REASONS, REASON_LIMIT);
        if (reasons.isEmpty()) {
            reasons.add("完整报告未形成明确的具体原因摘要。");
        }
        summary.setSpecificReasons(reasons);

        List<String> actions = normalizeItems(summary.getRecommendedActions(), MAX_ACTIONS, ACTION_LIMIT);
        if (actions.isEmpty()) {
            actions.add("下载完整诊断报告并人工复核。");
        }
        summary.setRecommendedActions(actions);
        return summary;
    }

    private List<String> normalizeItems(List<String> items, int maxItems, int itemLimit) {
        List<String> normalized = new ArrayList<>();
        if (items == null) {
            return normalized;
        }
        items.stream()
                .filter(StringUtils::hasText)
                .map(this::clean)
                .map(item -> trimToLimit(item, itemLimit))
                .limit(maxItems)
                .forEach(normalized::add);
        return normalized;
    }

    private String cleanupJson(String response) {
        if (!StringUtils.hasText(response)) {
            return "{}";
        }
        String text = response.trim()
                .replaceFirst("^```json\\s*", "")
                .replaceFirst("^```\\s*", "")
                .replaceFirst("\\s*```$", "");
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : text;
    }

    private String clean(String value) {
        return String.valueOf(value)
                .replaceAll("[#*_`>~]", "")
                .replaceAll("^\\s*\\d+[.、)]\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String trimToLimit(String value, int limit) {
        String text = StringUtils.hasText(value) ? value.trim() : "";
        return text.length() > limit ? text.substring(0, limit - 1) + "…" : text;
    }
}
