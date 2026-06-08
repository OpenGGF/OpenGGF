package com.openggf.tests.trace;

import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.DivergenceReport;
import com.openggf.trace.FieldComparison;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.Severity;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestTraceReplayReportPolicy {

    @Test
    void traceReplayWarningsAreReleaseBlockingByDefault() {
        ReportPolicySubject subject = new ReportPolicySubject(false);
        DivergenceReport report = warningOnlyReport();

        assertThrows(AssertionError.class,
                () -> subject.assertClean(report),
                "Warning-only trace reports must fail release replay by default.");
    }

    @Test
    void traceReplayWarningsCanBeMarkedDiagnosticOnly() {
        ReportPolicySubject subject = new ReportPolicySubject(true);
        DivergenceReport report = warningOnlyReport();

        assertDoesNotThrow(() -> subject.assertClean(report));
    }

    @Test
    void creditsDemoTraceWarningsAreReleaseBlockingByDefault() {
        CreditsReportPolicySubject subject = new CreditsReportPolicySubject(false);
        DivergenceReport report = warningOnlyReport();

        assertThrows(AssertionError.class,
                () -> subject.assertClean(report),
                "Credits demo warning-only reports must fail release replay by default.");
    }

    @Test
    void creditsDemoTraceWarningsCanBeMarkedDiagnosticOnly() {
        CreditsReportPolicySubject subject = new CreditsReportPolicySubject(true);
        DivergenceReport report = warningOnlyReport();

        assertDoesNotThrow(() -> subject.assertClean(report));
    }

    private static DivergenceReport warningOnlyReport() {
        Map<String, FieldComparison> fields = new LinkedHashMap<>();
        fields.put("bootstrap.object_slot[5]",
                new FieldComparison("bootstrap.object_slot[5]",
                        "present", "unavailable", Severity.WARNING, 1));
        return new DivergenceReport(List.of(new FrameComparison(0, fields)));
    }

    private static final class ReportPolicySubject extends AbstractTraceReplayTest {
        private final boolean diagnosticOnlyWarnings;

        private ReportPolicySubject(boolean diagnosticOnlyWarnings) {
            this.diagnosticOnlyWarnings = diagnosticOnlyWarnings;
        }

        void assertClean(DivergenceReport report) {
            assertReportHasNoReleaseBlockingDivergences(report);
        }

        @Override
        protected boolean allowDiagnosticOnlyWarnings() {
            return diagnosticOnlyWarnings;
        }

        @Override
        protected SonicGame game() {
            return SonicGame.SONIC_1;
        }

        @Override
        protected int zone() {
            return 0;
        }

        @Override
        protected int act() {
            return 0;
        }

        @Override
        protected Path traceDirectory() {
            return Path.of("unused");
        }
    }

    private static final class CreditsReportPolicySubject extends AbstractCreditsDemoTraceReplayTest {
        private final boolean diagnosticOnlyWarnings;

        private CreditsReportPolicySubject(boolean diagnosticOnlyWarnings) {
            this.diagnosticOnlyWarnings = diagnosticOnlyWarnings;
        }

        void assertClean(DivergenceReport report) {
            assertReportHasNoReleaseBlockingDivergences(report);
        }

        @Override
        protected boolean allowDiagnosticOnlyWarnings() {
            return diagnosticOnlyWarnings;
        }

        @Override
        protected int creditsDemoIndex() {
            return 0;
        }

        @Override
        protected Path traceDirectory() {
            return Path.of("unused");
        }
    }
}
