package com.episort.logging;

import java.util.List;
import java.util.regex.Pattern;

public final class SecretRedactor {
    private static final String REDACTED = "$1[REDACTED]";
    private final List<Pattern> patterns = List.of(
            Pattern.compile("(?i)(api[-_ ]?key\"?\\s*[=:]\\s*\"?)\\S+?(\")?(?=\\s|,|}|$)"),
            Pattern.compile("(?i)(subscriber[-_ ]?pin\"?\\s*[=:]\\s*\"?)\\S+?(\")?(?=\\s|,|}|$)"),
            Pattern.compile("(?i)(password\"?\\s*[=:]\\s*\"?)\\S+?(\")?(?=\\s|,|}|$)"),
            Pattern.compile("(?i)(authorization\\s*:\\s*bearer\\s+)\\S+"),
            Pattern.compile("(?i)(authorization\"?\\s*:\\s*\"?bearer\\s+)\\S+?(\")?(?=\\s|,|}|$)"),
            Pattern.compile("(?i)(token\"?\\s*[=:]\\s*\"?)\\S+?(\")?(?=\\s|,|}|$)"));

    public String redact(String value) {
        if (value == null) {
            return null;
        }

        String redacted = value;
        for (Pattern pattern : patterns) {
            redacted = pattern.matcher(redacted).replaceAll(REDACTED);
        }
        return redacted;
    }
}
