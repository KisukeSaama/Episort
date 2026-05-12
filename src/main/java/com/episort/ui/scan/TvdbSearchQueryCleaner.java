package com.episort.ui.scan;

import java.util.Locale;

final class TvdbSearchQueryCleaner {
    private TvdbSearchQueryCleaner() {
    }

    static String clean(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String stem = stripExtension(value);
        String normalized = stem
                .replaceAll("[._]+", " ")
                .replaceAll("[\\(\\[\\{].*?[\\)\\]\\}]", " ")
                .replaceAll("[\\(\\)\\[\\]\\{\\}]+", " ")
                .replaceAll("\\s*-\\s*", " ")
                .replaceAll("\\s+", " ")
                .trim();
        String[] parts = normalized.split("\\s+");
        StringBuilder title = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (isStopToken(part)) {
                break;
            }
            if (!title.isEmpty()) {
                title.append(' ');
            }
            title.append(part);
        }
        String cleaned = title.isEmpty() ? normalized : title.toString();
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private static String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        if (dot <= 0 || dot == value.length() - 1) {
            return value;
        }
        String extension = value.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (extension.matches("avi|mp4|mkv|mov|wmv|m4v")) {
            return value.substring(0, dot);
        }
        return value;
    }

    private static boolean isStopToken(String part) {
        String token = part.toLowerCase(Locale.ROOT)
                .replaceAll("^[^a-z0-9]+|[^a-z0-9]+$", "");
        if (token.isBlank()) {
            return false;
        }
        if (token.matches("s\\d{1,2}(e\\d{1,3})?") || token.matches("e\\d{1,3}")) {
            return true;
        }
        if (token.matches("19\\d{2}|20\\d{2}")) {
            return true;
        }
        if (token.matches("720p|1080p|2160p|4320p|4k|8k")) {
            return true;
        }
        return token.matches("bluray|bdrip|brrip|webrip|webdl|web|web-dl|hdtv|dvdrip|x264|x265|h264|h265|hevc|av1|"
                + "multi|multilang|vf|vf2|vff|vfi|vostfr|aac|ac3|eac3|dts|truehd|atmos|hdr|hdr10|hdr10plus|dolby|"
                + "vision|remux|proper|repack|internal");
    }
}
