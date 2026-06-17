package com.openggf.tests.trace;

import com.openggf.trace.DivergenceReport;

final class TraceReplayConsole {
    private static final int DEFAULT_CONTEXT_RADIUS = 2;
    private static final boolean PRINT_SUMMARY =
            Boolean.getBoolean("trace.print.summary");
    private static final boolean PRINT_BOOTSTRAP =
            Boolean.getBoolean("trace.print.bootstrap");
    private static final boolean PRINT_CONTEXT =
            Boolean.getBoolean("trace.print.context");

    private TraceReplayConsole() {
    }

    static boolean shouldPrintBootstrap() {
        return PRINT_BOOTSTRAP;
    }

    static boolean shouldPrintContext() {
        return PRINT_CONTEXT;
    }

    static int contextRadius() {
        return Integer.getInteger("trace.context.radius", DEFAULT_CONTEXT_RADIUS);
    }

    static void printSummary(DivergenceReport report) {
        if (PRINT_SUMMARY) {
            System.out.println(report.toCompactSummary());
        }
    }
}
