package com.openggf.tests.trace;

import com.openggf.tests.SessionInvocationExtension.SessionInvocation;
import com.openggf.tests.TestSessionOutputPaths.ReportAllocation;
import com.openggf.trace.DivergenceReport;
import com.openggf.trace.TraceVerificationScope;

import java.nio.file.Path;
import java.util.Locale;

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
        printSummary(report, TraceVerificationScope.ALL);
    }

    static void printSummary(DivergenceReport report, TraceVerificationScope scope) {
        if (PRINT_SUMMARY) {
            if (scope == TraceVerificationScope.ALL) {
                System.out.println(report.toCompactSummary());
            } else {
                System.out.println("Trace verification [" + scope.name().toLowerCase() + "]: "
                        + report.toAssertionSummary(scope));
            }
        }
    }

    static void printDiagnosticRoute(SessionInvocation invocation, Path fixture,
                                     ReportAllocation allocation, boolean replayFailed) {
        if (replayFailed) {
            System.err.print(diagnosticRoute(invocation, fixture, allocation));
        } else if (PRINT_SUMMARY) {
            System.out.print(diagnosticRoute(invocation, fixture, allocation));
        }
    }

    /** Exact published output; no filename or game/zone inference is needed to inspect it. */
    static String diagnosticRoute(SessionInvocation invocation, Path fixture, ReportAllocation allocation) {
        String arguments = "--report " + execArgument(allocation.physicalPath())
                + " --fixture " + execArgument(fixture);
        String command = "python3 tools/testing/maven_queue.py -Dmse=off exec:java "
                + "-Dexec.mainClass=com.openggf.tools.TraceTriageTool "
                + shellArgument("-Dexec.args=" + arguments);
        return "Trace diagnostics: " + invocation.className() + "#" + invocation.methodName() + "\n"
                + "  fixture: " + fixture + "\n"
                + "  report: " + allocation.physicalPath() + "\n"
                + "  triage: " + command + "\n";
    }

    private static String execArgument(Path path) {
        // Maven's argument parser accepts adjacent quoted fragments. A literal
        // double quote is carried inside a single-quoted fragment between them.
        return "\"" + path.toString().replace("\"", "\"'\"'\"") + "\"";
    }

    private static String shellArgument(String argument) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        return "'" + argument.replace("'", windows ? "''" : "'\"'\"'") + "'";
    }
}
