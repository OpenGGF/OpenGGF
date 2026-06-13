package com.openggf.tests.trace;

import com.openggf.trace.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestTraceBinder {

    @Test
    public void testExactMatchReturnsNoError() {
        TraceFrame frame = TraceFrame.of(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);

        assertFalse(result.hasDivergence());
        assertFalse(result.hasError());
    }

    @Test
    public void testDefaultPositionDivergenceIsError() {
        TraceFrame frame = TraceFrame.of(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0051, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);

        assertTrue(result.hasDivergence());
        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("x").severity());
    }

    @Test
    public void testPositionDivergenceError() {
        TraceFrame frame = TraceFrame.of(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0150, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);

        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("x").severity());
    }

    @Test
    public void testAirFlagMismatchIsError() {
        TraceFrame frame = TraceFrame.of(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, true, false, 0);

        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("air").severity());
    }

    @Test
    public void testSpeedSignChangeIsError() {
        TraceFrame frame = TraceFrame.of(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0010, (short) 0x0000, (short) 0x0010,
            (byte) 0x00, false, false, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) -0x0010, (short) 0x0000, (short) -0x0010,
            (byte) 0x00, false, false, 0);

        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("x_speed").severity());
    }

    @Test
    void testPrimaryRoutineMismatchIsErrorWhenDiagnosticsCarryRoutine() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, 0x02, -1, -1, -1, 0x00, -1, -1, -1, -1, null);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, new EngineDiagnostics(0x04, -1, -1, -1, 0x00, -1, -1,
                -1, -1, -1, -1, "", -1, -1, -1, -1));

        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("routine").severity());
    }

    @Test
    void testPrimaryStatusByteMismatchIsErrorWhenDiagnosticsCarryStatus() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, 0x02, -1, -1, -1, 0x04, -1, -1, -1, -1, null);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, new EngineDiagnostics(0x02, -1, -1, -1, 0x00, -1, -1,
                -1, -1, -1, -1, "", -1, -1, -1, -1));

        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("status_byte").severity());
    }

    @Test
    public void testInputValidationMatch() {
        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        TraceFrame frame = TraceFrame.of(0, 0x0008,
            (short) 0, (short) 0, (short) 0, (short) 0, (short) 0,
            (byte) 0, false, false, 0);
        assertTrue(binder.validateInput(frame, 0x0008));
    }

    @Test
    public void testInputValidationMismatch() {
        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        TraceFrame frame = TraceFrame.of(0, 0x0008,
            (short) 0, (short) 0, (short) 0, (short) 0, (short) 0,
            (byte) 0, false, false, 0);
        assertFalse(binder.validateInput(frame, 0x0004));
    }

    @Test
    void testSidekickStateMismatchIsReported() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            new TraceCharacterState(true,
                (short) 0x0040, (short) 0x03A0,
                (short) 0x0010, (short) 0x0000, (short) 0x0010,
                (byte) 0x00, false, false, 0,
                0, 0, 0x02, 0x00, 0x00));

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, null,
            new TraceCharacterState(true,
                (short) 0x0142, (short) 0x03A0,
                (short) 0x0010, (short) 0x0000, (short) 0x0010,
                (byte) 0x00, false, false, 0,
                0, 0, 0x02, 0x00, 0x00));

        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("sidekick_x").severity());
    }

    @Test
    void testSidekickStatusByteMismatchIsReported() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            new TraceCharacterState(true,
                (short) 0x0040, (short) 0x03A0,
                (short) 0x0010, (short) 0x0000, (short) 0x0010,
                (byte) 0x00, false, false, 0,
                0, 0, 0x02, 0x04, 0x00));

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, null, "tails",
            new TraceCharacterState(true,
                (short) 0x0040, (short) 0x03A0,
                (short) 0x0010, (short) 0x0000, (short) 0x0010,
                (byte) 0x00, false, false, 0,
                0, 0, 0x02, 0x00, 0x00));

        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("tails_status_byte").severity());
    }

    @Test
    void testSidekickRoutineMismatchIsReported() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            new TraceCharacterState(true,
                (short) 0x0040, (short) 0x03A0,
                (short) 0x0010, (short) 0x0000, (short) 0x0010,
                (byte) 0x00, false, false, 0,
                0, 0, 0x02, 0x00, 0x00));

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, null, "tails",
            new TraceCharacterState(true,
                (short) 0x0040, (short) 0x03A0,
                (short) 0x0010, (short) 0x0000, (short) 0x0010,
                (byte) 0x00, false, false, 0,
                0, 0, 0x04, 0x00, 0x00));

        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("tails_routine").severity());
    }

    @Test
    void testNamedCharacterLabelIsUsedForSecondaryComparisons() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            new TraceCharacterState(true,
                (short) 0x0040, (short) 0x03A0,
                (short) 0x0010, (short) 0x0000, (short) 0x0010,
                (byte) 0x00, false, false, 0,
                0, 0, 0x02, 0x00, 0x00));

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, null, "tails",
            new TraceCharacterState(true,
                (short) 0x0142, (short) 0x03A0,
                (short) 0x0010, (short) 0x0000, (short) 0x0010,
                (byte) 0x00, false, false, 0,
                0, 0, 0x02, 0x00, 0x00));

        assertTrue(result.hasError());
        assertTrue(result.fields().containsKey("tails_x"));
        assertEquals(Severity.ERROR, result.fields().get("tails_x").severity());
    }

    @Test
    void testSidekickCpuStateMismatchIsReportedBeforePositionFields() {
        TraceFrame frame = TraceFrame.of(3906, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);
        TraceEvent.CpuState expectedCpu = new TraceEvent.CpuState(
                3906, "tails", 0x11, 7, 299, 0x06,
                (short) 0x0613, (short) 0x0264, 0,
                1, 0x08, 0x10, 0, 0x4000,
                0x44, 0x00, (short) 0x0500, (short) 0x0200,
                0x0800, 0x08, 0x02, 0x11, 0x000C);
        EngineSidekickCpuState actualCpu = new EngineSidekickCpuState(
                7, 299, 0x11, 0x08, 0x0613, 0x0264,
                0x08, 0x10, 0x00, 1);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, null, "tails", null, expectedCpu, actualCpu);

        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("tails_cpu_routine").severity());
    }

    @Test
    void testSidekickCpuComparisonSkippedWhenTraceDoesNotCarryCpuState() {
        TraceFrame frame = TraceFrame.of(117, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);
        EngineSidekickCpuState actualCpu = new EngineSidekickCpuState(
                0, 0, 0x11, 0x06, 0x0000, 0x0000,
                0x10, 0x10, 0x0A, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, null, "tails", null, null, actualCpu);

        assertFalse(result.fields().containsKey("tails_cpu_present"));
        assertFalse(result.hasError());
    }

    @Test
    void testSidekickCpuPresentReportsEngineAbsenceWhenTraceCarriesCpuState() {
        TraceFrame frame = TraceFrame.of(117, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);
        TraceEvent.CpuState expectedCpu = new TraceEvent.CpuState(
                117, "tails", 0x11, 0, 0, 0x06,
                (short) 0x0000, (short) 0x0000, 0,
                0, 0x40, 0x40, 0, 0x0000,
                0x68, 0x28, (short) 0x0000, (short) 0x0000,
                0x0000, 0x00, 0x00, 0x00, 0x0000);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, null, "tails", null, expectedCpu, null);

        assertEquals(Severity.ERROR, result.fields().get("tails_cpu_present").severity());
        assertTrue(result.hasError());
    }

    @Test
    void testSidekickCpuFollowRingConvertsRomByteOffsetToEngineSlot() {
        TraceCharacterState tailsControl = new TraceCharacterState(
                true, (short) 0, (short) 0, (short) 0, (short) 0, (short) 0,
                (byte) 0, false, false, 0, 0, 0, 0x02, 0, 0);
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, 0x02, 0, 0, 0, 0, 0, 0, 0, 0,
            tailsControl);
        TraceEvent.CpuState expectedCpu = new TraceEvent.CpuState(
                0, "tails", 0x11, 7, 299, 0x06,
                (short) 0x0613, (short) 0x0264, 0,
                1, 0x08, 0x10, 0, 0x4000,
                0x68, 0x28, (short) 0x0500, (short) 0x0200,
                0x0800, 0x08, 0x02, 0x11, 0x000C);
        EngineSidekickCpuState actualCpu = new EngineSidekickCpuState(
                7, 299, 0x11, 0x06, 0x0613, 0x0264,
                0x08, 0x10, 0x0A, 1);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, null, "tails", tailsControl, expectedCpu, actualCpu);

        assertEquals(Severity.MATCH, result.fields().get("tails_cpu_follow_ring").severity());
        assertFalse(result.hasError());
    }

    @Test
    void testSidekickCpuFollowRingSkippedWhenObjectRoutineBypassesCpuControl() {
        TraceFrame frame = new TraceFrame(312, 0x0000,
            (short) 0x030D, (short) 0x03BC,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x04, false, false, 0,
            0, 0, 0x02, 0, 0, 2, 0, 0x139, 0, 0x0AAB, 0,
            new TraceCharacterState(true,
                (short) 0x0323, (short) 0x0364,
                (short) 0x0200, (short) 0xFC30, (short) 0x0000,
                (byte) 0x00, true, false, 0,
                0xAF00, 0x4700, 0x04, 0x03, 0x11));
        TraceEvent.CpuState expectedCpu = new TraceEvent.CpuState(
                312, "tails", 0x2B, 0, 0, 0x06,
                (short) 0x0000, (short) 0x0000, 0,
                0, 0x44, 0x04, 0, 0x0000,
                0x4C, 0x08, (short) 0x0000, (short) 0x0000,
                0x0800, 0x06, 0x03, 0x11, 0x0000);
        EngineSidekickCpuState actualCpu = new EngineSidekickCpuState(
                0, 0, 0x2B, 0x06, 0, 0,
                0x14, 0, -1, 0);
        TraceCharacterState actualTailsHurt = new TraceCharacterState(
                true, (short) 0x0323, (short) 0x0364,
                (short) 0x0200, (short) 0xFC30, (short) 0x0000,
                (byte) 0x00, true, false, 0,
                0xAF00, 0x4700, 0x04, 0x03, -1);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x030D, (short) 0x03BC,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x04, false, false, 0,
            null, null, "tails", actualTailsHurt, expectedCpu, actualCpu);

        assertFalse(result.fields().containsKey("tails_cpu_follow_ring"),
                "The recorder derives delayed_index every frame; compare it only when Obj02_Control runs");
        assertFalse(result.hasError());
    }

    @Test
    void testSidekickCpuFollowRingSkippedWhenHurtStopRestoresRoutineAfterCpuBypass() {
        TraceCharacterState tailsControlAfterHurtStop = new TraceCharacterState(
                true, (short) 0x0399, (short) 0x03C2,
                (short) 0, (short) 0, (short) 0,
                (byte) 0x04, false, false, 0,
                0xAF00, 0x2700, 0x02, 0x01, 0x11);
        TraceFrame frame = new TraceFrame(371, 0x0000,
            (short) 0x035D, (short) 0x03BD,
            (short) 0x02C7, (short) 0x0046, (short) 0x02CD,
            (byte) 0x04, false, false, 0,
            0, 0, 0x02, 0, 0, 2, 0, 0x174, 0, 0x0AE6, 0,
            tailsControlAfterHurtStop);
        TraceEvent.CpuState expectedCpu = new TraceEvent.CpuState(
                371, "tails", 0x2B, 0, 0, 0x06,
                (short) 0x0000, (short) 0x0000, 0,
                0, 0x44, 0x04, 0, 0x0000,
                0x38, 0xF4, (short) 0x0000, (short) 0x0000,
                0x0800, 0x00, 0x01, 0x11, 0x0000);
        EngineSidekickCpuState actualCpu = new EngineSidekickCpuState(
                0, 0, 0x2B, 0x06, 0, 0,
                0x14, 0, -1, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x035D, (short) 0x03BD,
            (short) 0x02C7, (short) 0x0046, (short) 0x02CD,
            (byte) 0x04, false, false, 0,
            null, null, "tails", tailsControlAfterHurtStop, expectedCpu, actualCpu);

        assertFalse(result.fields().containsKey("tails_cpu_follow_ring"),
                "A final routine byte of 2 is not enough; compare only when the engine CPU recorded a table read");
    }

    @Test
    void testSidekickCpuCtrl2LogicalNormalizesRomJumpButtons() {
        TraceFrame frame = TraceFrame.of(117, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);
        TraceEvent.CpuState expectedCpu = new TraceEvent.CpuState(
                117, "tails", 0x11, 0, 0, 0x06,
                (short) 0x0000, (short) 0x0000, 0,
                0, 0x40, 0x40, 0, 0x0000,
                0x68, 0x28, (short) 0x0000, (short) 0x0000,
                0x0000, 0x00, 0x00, 0x00, 0x0000);
        EngineSidekickCpuState actualCpu = new EngineSidekickCpuState(
                0, 0, 0x11, 0x06, 0x0000, 0x0000,
                0x10, 0x10, 0x0A, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, null, "tails", null, expectedCpu, actualCpu);

        assertEquals(Severity.MATCH, result.fields().get("tails_cpu_ctrl2_held").severity());
        assertEquals(Severity.MATCH, result.fields().get("tails_cpu_ctrl2_pressed").severity());
        assertFalse(result.hasError());
    }

    @Test
    void testSidekickCpuCtrl2PressedComparisonIgnoresDirectionalBits() {
        TraceFrame frame = TraceFrame.of(16, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);
        TraceEvent.CpuState expectedCpu = new TraceEvent.CpuState(
                16, "tails", 0x11, 0, 0, 0x06,
                (short) 0x0000, (short) 0x0000, 0,
                0, 0x08, 0x00, 0, 0x0000,
                0x68, 0x28, (short) 0x0000, (short) 0x0000,
                0x0800, 0x00, 0x00, 0x00, 0x0000);
        EngineSidekickCpuState actualCpu = new EngineSidekickCpuState(
                0, 0, 0x11, 0x06, 0x0000, 0x0000,
                0x08, 0x08, 0x0A, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, null, "tails", null, expectedCpu, actualCpu);

        assertEquals(Severity.MATCH, result.fields().get("tails_cpu_ctrl2_held").severity());
        assertEquals(Severity.MATCH, result.fields().get("tails_cpu_ctrl2_pressed").severity());
        assertFalse(result.hasError());
    }

    @Test
    void testSidekickCpuCtrl2UsesNormalStepWhenRecorderHasDecisionTimeInput() {
        TraceFrame frame = TraceFrame.of(20315, 0x0000,
            (short) 0x49DA, (short) 0x01FD,
            (short) 0x00CC, (short) 0x0000, (short) 0x00CC,
            (byte) 0x00, false, false, 0);
        TraceEvent.CpuState snapshotCpu = new TraceEvent.CpuState(
                20315, "tails", 0x02, 0, 0, 0x06,
                (short) 0x49A1, (short) 0x01FD, 0,
                0, 0x00, 0x00, 0, 0x0068,
                0x98, 0x00, (short) 0x49B3, (short) 0x01FD,
                0x0808, 0x00, 0x00, 0x00, 0x0000);
        TraceEvent.TailsCpuNormalStep normalStep = new TraceEvent.TailsCpuNormalStep(
                20315, "tails", 0x00, 0x00, 0x0000, 0x0000,
                0xFF, 0x0808, 0x98, 0x49B3, 0x01FD, 0xFFE1, 0xFFFC,
                "fallthrough_sub20", 0x0808, 0x08,
                0x0000, 0x0000, 0x00, 0x0006, 0x0006, 0x00);
        EngineSidekickCpuState actualCpu = new EngineSidekickCpuState(
                0, 0, 0x02, 0x06, 0x49A1, 0x01FD,
                0x08, 0x00, 0x16, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x49DA, (short) 0x01FD,
            (short) 0x00CC, (short) 0x0000, (short) 0x00CC,
            (byte) 0x00, false, false, 0,
            null, null, "tails", null, snapshotCpu, actualCpu, normalStep);

        assertEquals(Severity.MATCH, result.fields().get("tails_cpu_ctrl2_held").severity());
        assertEquals(Severity.MATCH, result.fields().get("tails_cpu_ctrl2_pressed").severity());
        assertFalse(result.hasError());
    }

    @Test
    void testSidekickCpuCtrl2StillAcceptsCpuStateWhenNormalStepTapIsZero() {
        TraceFrame frame = TraceFrame.of(1726, 0x0000,
            (short) 0x182F, (short) 0x0417,
            (short) 0x001F, (short) 0xFE00, (short) 0xFF74,
            (byte) 0x00, true, true, 0);
        TraceEvent.CpuState snapshotCpu = new TraceEvent.CpuState(
                1726, "tails", 0x00, 0, 0, 0x06,
                (short) 0x138A, (short) 0x041B, 0,
                0, 0x40, 0x40, 0, 0x0000,
                0x74, 0x00, (short) 0x1815, (short) 0x0461,
                0x4040, 0xFF, 0x07, 0x00, 0x0000);
        TraceEvent.TailsCpuNormalStep normalStep = new TraceEvent.TailsCpuNormalStep(
                1726, "tails", 0x01, 0x00, 0xFF74, 0xFF74,
                0x07, 0x4040, 0x74, 0x1815, 0x0461, 0xFFE2, 0x0001,
                "fallthrough_sub20", 0x0000, 0x00,
                0xFF74, 0xFF74, 0x07, 0x0000, 0x0000, 0x00);
        EngineSidekickCpuState actualCpu = new EngineSidekickCpuState(
                0, 0, 0x00, 0x06, 0x138A, 0x041B,
                0x10, 0x10, 0x13, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x182F, (short) 0x0417,
            (short) 0x001F, (short) 0xFE00, (short) 0xFF74,
            (byte) 0x00, true, true, 0,
            null, null, "tails", null, snapshotCpu, actualCpu, normalStep);

        assertEquals(Severity.MATCH, result.fields().get("tails_cpu_ctrl2_held").severity());
        assertEquals(Severity.MATCH, result.fields().get("tails_cpu_ctrl2_pressed").severity());
        assertFalse(result.hasError());
    }

    @Test
    void testSidekickCpuCtrl2UsesGeneratedNormalStepNotDelayedHeldInput() {
        TraceFrame frame = TraceFrame.of(2894, 0x0011,
            (short) 0x172E, (short) 0x0A5A,
            (short) 0x0400, (short) 0xFFC0, (short) 0x0300,
            (byte) 0x00, true, false, 0);
        TraceEvent.CpuState snapshotCpu = new TraceEvent.CpuState(
                2894, "tails", 0x00, 0, 0, 0x06,
                (short) 0x14F5, (short) 0x086C, 0,
                0, 0x15, 0x04, 0, 0x0000,
                0x00, 0xFF, (short) 0x0000, (short) 0x0000,
                0xFFFF, 0xFF, 0x00, 0x00, 0x0000);
        TraceEvent.TailsCpuNormalStep normalStep = new TraceEvent.TailsCpuNormalStep(
                2894, "tails", 0x43, 0x00, 0x0300, 0x0400,
                0x42, 0x1504, 0xFF, 0x168C, 0x0A74, 0xFFAD, 0xFFFF,
                "fallthrough_sub20", 0x0000, 0x00,
                0x0000, 0x0000, 0x00, 0x0000, 0x0000, 0x00);
        EngineSidekickCpuState actualCpu = new EngineSidekickCpuState(
                0, 0, 0x00, 0x06, 0x14F5, 0x086C,
                0x15, 0x10, 0x30, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x172E, (short) 0x0A5A,
            (short) 0x0400, (short) 0xFFC0, (short) 0x0300,
            (byte) 0x00, true, false, 0,
            null, null, "tails", null, snapshotCpu, actualCpu, normalStep);

        assertEquals(Severity.ERROR, result.fields().get("tails_cpu_ctrl2_pressed").severity());
        assertTrue(result.hasError());
    }

    @Test
    void testRingCountMismatchIsWarningWhenConfiguredWarnOnly() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, 0x02, 0, 0, 7, 0, 1, -1, -1, -1, null);

        TraceBinder binder = new TraceBinder(new ToleranceConfig(
            1, 1, 1, 1, true, 1, 1, 1, 1, ToleranceConfig.RingCountMode.WARN_ONLY));
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, new EngineDiagnostics(0x02, -1, -1, 8, 0, 0, 0,
                -1, -1, -1, -1, "", 0, 0, -1, -1));

        assertTrue(result.hasDivergence());
        assertFalse(result.hasError());
        assertEquals(Severity.WARNING, result.fields().get("rings").severity());
    }

    @Test
    void testDefaultRingCountMismatchIsError() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, 0x02, 0, 0, 7, 0, 1, -1, -1, -1, null);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, new EngineDiagnostics(0x02, -1, -1, 8, 0, 0, 0,
                -1, -1, -1, -1, "", 0, 0, -1, -1));

        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("rings").severity());
    }

    @Test
    void testWithRingCountModeFactoryDowngradesToWarning() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, 0x02, 0, 0, 7, 0, 1, -1, -1, -1, null);

        TraceBinder binder = new TraceBinder(
            ToleranceConfig.DEFAULT.withRingCountMode(ToleranceConfig.RingCountMode.WARN_ONLY));
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, new EngineDiagnostics(0x02, -1, -1, 8, 0, 0, 0,
                -1, -1, -1, -1, "", 0, 0, -1, -1));

        assertFalse(result.hasError());
        assertEquals(Severity.WARNING, result.fields().get("rings").severity());
    }

    @Test
    void testRingCountMismatchIsErrorWhenConfiguredForceError() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, 0x02, 0, 0, 7, 0, 1, -1, -1, -1, null);

        TraceBinder binder = new TraceBinder(new ToleranceConfig(
            1, 1, 1, 1, true, 1, 1, ToleranceConfig.RingCountMode.FORCE_ERROR));
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, new EngineDiagnostics(0x02, -1, -1, 8, 0, 0, 0,
                -1, -1, -1, -1, "", 0, 0, -1, -1));

        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("rings").severity());
    }

    @Test
    void testCameraMatchProducesNoCameraDivergence() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, 0x02, 0x0140, 0x00C0, -1, 0, 1, -1, -1, -1, null);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, new EngineDiagnostics(0x02, -1, -1, -1, 0, 0x0140, 0x00C0,
                -1, -1, -1, -1, "", 0, 0, -1, -1));

        assertEquals(Severity.MATCH, result.fields().get("camera_x").severity());
        assertEquals(Severity.MATCH, result.fields().get("camera_y").severity());
        assertFalse(result.hasError());
    }

    @Test
    void testCameraMismatchIsErrorByDefault() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, 0x02, 0x0140, 0x00C0, -1, 0, 1, -1, -1, -1, null);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, new EngineDiagnostics(0x02, -1, -1, -1, 0, 0x0142, 0x00C0,
                -1, -1, -1, -1, "", 0, 0, -1, -1));

        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("camera_x").severity());
        assertEquals(Severity.MATCH, result.fields().get("camera_y").severity());
    }

    @Test
    void testCameraComparisonSkippedWhenTraceCameraAbsent() {
        TraceFrame frame = TraceFrame.of(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, new EngineDiagnostics(0x02, -1, -1, -1, 0, 0x0140, 0x00C0,
                -1, -1, -1, -1, "", 0, 0, -1, -1));

        assertFalse(result.fields().containsKey("camera_x"));
        assertFalse(result.fields().containsKey("camera_y"));
    }

    @Test
    void testObjectNearSemanticMatchStillReportsSlotMismatch() {
        TraceFrame frame = TraceFrame.of(1217, 0xA0E0,
            (short) 0x0935, (short) 0x044C,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        binder.compareFrame(frame,
            (short) 0x0935, (short) 0x044C,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);

        binder.compareObjectNear(1217,
            java.util.List.of(new TraceEvent.ObjectNear(
                1217, "sonic", 72, "0x5F", (short) 0x0960, (short) 0x03D0, "0x02", "0x00")),
            java.util.List.of(new EngineNearbyObject(
                74, 0x5F, "Bomb", 0x0960, 0x03D0, 0x0960, 0x03D0, true,
                0x9A, 0x9A, 0x0960, 0x03D0, false, false, true)));

        DivergenceReport report = binder.buildReport();
        assertFalse(report.errors().isEmpty(), "slot mismatch should be reported");
        DivergenceGroup firstError = report.errors().getFirst();
        assertEquals("obj_s48_slot", firstError.field());
        assertEquals("0x48", firstError.expectedAtStart());
        assertEquals("0x4A", firstError.actualAtStart());
    }

    @Test
    void testCameraComparisonHandlesU16WraparoundOnEngineSide() {
        // Capture sites mask Camera.getX() with & 0xFFFF so engine values are
        // always stored as unsigned 16-bit. TraceBinder additionally masks both
        // sides before comparing, guaranteeing no spurious wraparound deltas.
        // 0xFFE0 (= 65504) on both sides must compare as MATCH.
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, 0x02, 0xFFE0, 0x0040, -1, 0, 1, -1, -1, -1, null);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, new EngineDiagnostics(0x02, -1, -1, -1, 0, 0xFFE0, 0x0040,
                -1, -1, -1, -1, "", 0, 0, -1, -1));

        assertEquals(Severity.MATCH, result.fields().get("camera_x").severity());
    }
}
