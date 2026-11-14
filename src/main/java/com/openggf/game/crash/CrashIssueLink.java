package com.openggf.game.crash;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the query that opens {@code .github/ISSUE_TEMPLATE/crash.yml} with the report filled in.
 * Nothing is sent: the player reads the form in their browser and submits it themselves, and
 * attaches the saved report file by hand because a link cannot carry a file.
 *
 * <p>Field names here must match the form's {@code id}s.
 */
public final class CrashIssueLink {
    /** The issue form, written as {@link com.openggf.util.ExternalBrowser} expects (no scheme). */
    public static final String FORM = "github.com/OpenGGF/OpenGGF/issues/new";
    public static final String TEMPLATE = "crash.yml";
    /**
     * GitHub rejects request lines of about 8 KB; this leaves room for the address and headers.
     * The trace is shortened until the encoded query fits.
     */
    static final int MAX_QUERY_LENGTH = 6000;
    private static final int[] FRAME_STEPS = {12, 8, 5, 3, 1, 0};

    private CrashIssueLink() { }

    /** The encoded query, without the leading {@code ?}, no longer than {@link #MAX_QUERY_LENGTH}. */
    public static String query(CrashReport report, String reportFileName) {
        String query = "";
        for (int frames : FRAME_STEPS) {
            query = encode(fields(report, reportFileName, report.shortTrace(frames)));
            if (query.length() <= MAX_QUERY_LENGTH) return query;
        }
        // Even one line per throwable is too long (a huge message): cut the error text itself.
        String trace = report.shortTrace(0);
        while (query.length() > MAX_QUERY_LENGTH && trace.length() > 80) {
            trace = trace.substring(0, trace.length() * 3 / 4) + "\n... (shortened; see the attached report)";
            query = encode(fields(report, reportFileName, trace));
        }
        return query;
    }

    /** The whole address, for copying when no browser could be started. */
    public static String address(CrashReport report, String reportFileName) {
        return "https://" + FORM + "?" + query(report, reportFileName);
    }

    static Map<String, String> fields(CrashReport report, String reportFileName, String trace) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("template", TEMPLATE);
        fields.put("title", "[Crash] " + report.redact(firstLine(report.error())) + " at " + report.where());
        fields.put("version", CrashReport.lines(report.buildFacts()));
        fields.put("session", CrashReport.lines(report.sessionFacts()));
        fields.put("error", trace);
        if (reportFileName != null && !reportFileName.isBlank()) fields.put("report-file", reportFileName);
        return fields;
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        String line = newline < 0 ? text : text.substring(0, newline);
        return line.length() > 120 ? line.substring(0, 117) + "..." : line;
    }

    private static String encode(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(e -> component(e.getKey()) + "=" + component(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    /** Percent-encodes a query component; spaces become {@code %20}, which every opener passes intact. */
    static String component(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
