package com.wuxx.diagnosis.service.ai;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

public final class SensitiveDataMasker {

    private static final String MASK = "$1=******";

    private static final List<Pattern> LINE_PATTERNS = List.of(
            Pattern.compile("(?i)(authorization|cookie)\\s*[:=]\\s*[^\\r\\n]+")
    );

    private static final List<Pattern> KEY_VALUE_PATTERNS = List.of(
            Pattern.compile("(?i)(password|passwd|token|secret|authorization|cookie)\\s*=\\s*([^\\s,;]+)"),
            Pattern.compile("(?i)(password|passwd|token|secret|authorization|cookie)\\s*:\\s*([^\\s,;]+)")
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b1[3-9]\\d{9}\\b");

    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\b\\d{17}[0-9Xx]\\b");

    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("\\b\\d{16,19}\\b");

    private SensitiveDataMasker() {
    }

    public static String mask(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }

        String masked = text;
        for (Pattern pattern : LINE_PATTERNS) {
            masked = pattern.matcher(masked).replaceAll(MASK);
        }
        for (Pattern pattern : KEY_VALUE_PATTERNS) {
            masked = pattern.matcher(masked).replaceAll(MASK);
        }
        masked = EMAIL_PATTERN.matcher(masked).replaceAll("******@******");
        masked = PHONE_PATTERN.matcher(masked).replaceAll("******");
        masked = ID_CARD_PATTERN.matcher(masked).replaceAll("******");
        masked = BANK_CARD_PATTERN.matcher(masked).replaceAll("******");
        return masked;
    }
}
