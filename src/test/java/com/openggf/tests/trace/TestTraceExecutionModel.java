package com.openggf.tests.trace;

import com.openggf.trace.*;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestTraceExecutionModel {

    @Test
    void sonic1CounterDelta_fullLevelFrame() {
        TraceFrame previous = TraceFrame.executionTestFrame(0, 0x0120, 0x3456, 0);
        TraceFrame current = TraceFrame.executionTestFrame(1, 0x0121, 0x3457, 0);

        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceExecutionModel.forGame("s1").phaseFor(previous, current));
    }

    @Test
    void sonic1VblankDeltaWithoutGameplayDelta_vblankOnly() {
        TraceFrame previous = TraceFrame.executionTestFrame(0, 0x0120, 0x3456, 0);
        TraceFrame current = TraceFrame.executionTestFrame(1, 0x0121, 0x3456, 0);

        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceExecutionModel.forGame("s1").phaseFor(previous, current));
    }

    @Test
    void sonic1NoCounterDelta_gameplayPlateauIsVblankOnly() {
        TraceFrame previous = TraceFrame.executionTestFrame(0, 0x0120, 0x3456, 0);
        TraceFrame current = TraceFrame.executionTestFrame(1, 0x0120, 0x3456, 0);

        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceExecutionModel.forGame("s1").phaseFor(previous, current));
    }

    @Test
    void sonic1LagCounterDeltaWithoutGameplayDelta_vblankOnly() {
        TraceFrame previous = TraceFrame.executionTestFrame(0, 0x0120, 0x3456, 3);
        TraceFrame current = TraceFrame.executionTestFrame(1, 0x0120, 0x3456, 4);

        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceExecutionModel.forGame("s1").phaseFor(previous, current));
    }

    @Test
    void sonic1GameplayCounterDeltaWinsOverLagCounterDelta() {
        TraceFrame previous = TraceFrame.executionTestFrame(0, 0x0120, 0x3456, 3);
        TraceFrame current = TraceFrame.executionTestFrame(1, 0x0121, 0x3457, 4);

        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceExecutionModel.forGame("s1").phaseFor(previous, current));
    }

    @Test
    void sonic2VblankDeltaWithoutGameplayDelta_vblankOnly() {
        TraceFrame previous = TraceFrame.executionTestFrame(0, 0x0220, 0x1456, 0);
        TraceFrame current = TraceFrame.executionTestFrame(1, 0x0221, 0x1456, 0);

        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceExecutionModel.forGame("s2").phaseFor(previous, current));
    }

    @Test
    void sonic2NoCounterDelta_gameplayPlateauIsVblankOnly() {
        TraceFrame previous = TraceFrame.executionTestFrame(0, 0x0220, 0x1456, 0);
        TraceFrame current = TraceFrame.executionTestFrame(1, 0x0220, 0x1456, 0);

        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceExecutionModel.forGame("s2").phaseFor(previous, current));
    }

    @Test
    void sonic2LagCounterDeltaWithoutGameplayDelta_vblankOnly() {
        TraceFrame previous = TraceFrame.executionTestFrame(0, 0x0220, 0x1456, 3);
        TraceFrame current = TraceFrame.executionTestFrame(1, 0x0220, 0x1456, 4);

        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceExecutionModel.forGame("s2").phaseFor(previous, current));
    }

    @Test
    void sonic2GameplayCounterDeltaWinsOverLagCounterDelta() {
        TraceFrame previous = TraceFrame.executionTestFrame(0, 0x0220, 0x1456, 3);
        TraceFrame current = TraceFrame.executionTestFrame(1, 0x0221, 0x1457, 4);

        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceExecutionModel.forGame("s2").phaseFor(previous, current));
    }

    @Test
    void sonic3kLagCounterDelta_vblankOnly() {
        TraceFrame previous = TraceFrame.executionTestFrame(0, 0x2000, 0x0100, 3);
        TraceFrame current = TraceFrame.executionTestFrame(1, 0x2001, 0x0100, 4);

        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceExecutionModel.forGame("s3k").phaseFor(previous, current));
    }

    @Test
    void sonic3kLagCounterAloneSelectsVblankOnly() {
        TraceFrame previous = TraceFrame.executionTestFrame(0, 0x2000, 0x0100, 3);
        TraceFrame current = TraceFrame.executionTestFrame(1, 0x2000, 0x0100, 4);

        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceExecutionModel.forGame("s3k").phaseFor(previous, current));
    }

    @Test
    void sonic3kGameplayCounterDeltaWinsOverLagCounterDelta() {
        TraceFrame previous = TraceFrame.executionTestFrame(0, 0x2000, 0x0100, 3);
        TraceFrame current = TraceFrame.executionTestFrame(1, 0x2001, 0x0101, 4);

        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceExecutionModel.forGame("s3k").phaseFor(previous, current));
    }

    @Test
    void sonic3kVblankCounterWithStateChangeIsFullLevelFrame() {
        TraceFrame previous = new TraceFrame(0, 0,
                (short) 0x0FDE, (short) 0x0326,
                (short) 0x0622, (short) 0x0285, (short) 0x06A8,
                (byte) 0x10, false, false, 0,
                0, 0, 2, -1, -1, 99, 0,
                0, 9, 0x0300, 0);
        TraceFrame current = new TraceFrame(1, 0,
                (short) 0x0FE4, (short) 0x0329,
                (short) 0x062D, (short) 0x028A, (short) 0x06B4,
                (byte) 0x10, false, false, 0,
                0, 0, 2, -1, -1, 100, 0,
                0, 9, 0x0400, 0);

        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceExecutionModel.forGame("s3k").phaseFor(previous, current));
    }

    @Test
    void sonic3kFrozenNormalLevelModeRowsStayFullLevelFrames() throws Exception {
        TraceData trace = twoFrameS3kTraceWithGameMode(0x0C);
        TraceFrame previous = trace.getFrame(0);
        TraceFrame current = trace.getFrame(1);

        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceReplayBootstrap.phaseForReplay(trace, previous, current));
    }

    @Test
    void sonic3kFrozenTransitionLevelModeRowsAreVblankOnly() throws Exception {
        TraceData trace = twoFrameS3kTraceWithGameMode(0x8C);
        TraceFrame previous = trace.getFrame(0);
        TraceFrame current = trace.getFrame(1);

        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceReplayBootstrap.phaseForReplay(trace, previous, current));
    }

    @Test
    void preLevelS3kIntroPrefixTicksReplayAsFullFramesBeforeGameplayStart() throws Exception {
        TraceData trace = TraceData.load(
                Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun"));
        // Frames 500/501 are well into the AIZ1 intro cutscene (past the first
        // in-level frame at 289, before gameplay_start). This section is
        // native level execution, so it must tick as full frames even while
        // player control is still locked by the intro object and the regenerated
        // trace keeps Level_frame_counter at zero.
        TraceFrame previous = trace.getFrame(500);
        TraceFrame current = trace.getFrame(501);

        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceReplayBootstrap.phaseForReplay(trace, previous, current));
    }

    @Test
    void s2VblankSplitUsesFollowingVisualDiagnosticsOnly() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s2/mtz3"));
        TraceFrame previous = trace.getFrame(5179);
        TraceFrame current = trace.getFrame(5180);
        TraceFrame next = trace.getFrame(5181);
        TraceExecutionPhase phase = TraceReplayBootstrap.phaseForReplay(trace, previous, current);

        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME, phase);
        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceReplayBootstrap.phaseForReplay(trace, current, next));
        assertEquals(current.gameplayFrameCounter(), next.gameplayFrameCounter());
        assertEquals(current.x(), next.x());
        assertEquals(current.y(), next.y());
        assertNotEquals(current.cameraY(), next.cameraY());

        TraceFrame comparison = TraceReplayBootstrap.frameForGameplayComparison(
                trace, 5180, previous, current, phase);

        assertEquals(current.x(), comparison.x());
        assertEquals(current.y(), comparison.y());
        assertEquals(next.cameraX(), comparison.cameraX());
        assertEquals(next.cameraY(), comparison.cameraY());
        assertEquals(next.rings(), comparison.rings());
    }

    @Test
    void s3kRingDiagnosticComparisonKeepsCurrentTraceRingCount() throws Exception {
        TraceData trace = TraceData.load(
                Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun"));
        TraceFrame current = trace.getFrame(6203);
        TraceFrame next = trace.getFrame(6204);
        EngineDiagnostics engineDiag = new EngineDiagnostics(-1, -1, -1, next.rings(),
                -1, current.cameraX(), current.cameraY(), -1, -1, -1, -1,
                "", -1, -1, -1, -1);

        assertEquals(current.rings() + 1, next.rings());

        TraceFrame comparison = TraceReplayBootstrap.s3kFrameForRingDiagnosticComparison(
                trace, 6203, current, engineDiag);

        assertEquals(current.x(), comparison.x());
        assertEquals(current.y(), comparison.y());
        assertEquals(current.cameraX(), comparison.cameraX());
        assertEquals(current.cameraY(), comparison.cameraY());
        assertEquals(current.rings(), comparison.rings());
    }

    @Test
    void s3kRingDiagnosticComparisonKeepsPersistentMismatchVisible() throws Exception {
        TraceData trace = TraceData.load(
                Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun"));
        TraceFrame current = trace.getFrame(6203);
        TraceFrame next = trace.getFrame(6204);
        EngineDiagnostics engineDiag = new EngineDiagnostics(-1, -1, -1, next.rings() + 1,
                -1, current.cameraX(), current.cameraY(), -1, -1, -1, -1,
                "", -1, -1, -1, -1);

        TraceFrame comparison = TraceReplayBootstrap.s3kFrameForRingDiagnosticComparison(
                trace, 6203, current, engineDiag);

        assertEquals(current.rings(), comparison.rings());
    }

    @Test
    void firstFrameDefaultsToFullLevelFrame() {
        TraceFrame current = TraceFrame.executionTestFrame(0, 0x0120, 0x3456, 0);

        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceExecutionModel.forGame("s1").phaseFor(null, current));
    }

    @Test
    void legacyTraceWithoutVblankCounter_fallsBackToStateHeuristic() {
        TraceFrame previous = new TraceFrame(0, 0,
                (short) 0x0050, (short) 0x03B0,
                (short) 0x000C, (short) 0x0000, (short) 0x000C,
                (byte) 0x00, false, false, 0,
                0, 0, -1, -1, -1, -1, -1,
                0, -1, -1, -1);
        TraceFrame current = new TraceFrame(1, 0,
                (short) 0x0050, (short) 0x03B0,
                (short) 0x000C, (short) 0x0000, (short) 0x000C,
                (byte) 0x00, false, false, 0,
                0, 0, -1, -1, -1, -1, -1,
                0, -1, -1, -1);

        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceExecutionModel.forGame("s1").phaseFor(previous, current));
    }

    @Test
    void legacyTraceWithoutVblankCounter_usesStateChangeForFullFrame() {
        TraceFrame previous = new TraceFrame(0, 0,
                (short) 0x0050, (short) 0x03B0,
                (short) 0x000C, (short) 0x0000, (short) 0x000C,
                (byte) 0x00, false, false, 0,
                0, 0, -1, -1, -1, -1, -1,
                0, -1, -1, -1);
        TraceFrame current = new TraceFrame(1, 0,
                (short) 0x0051, (short) 0x03B0,
                (short) 0x000C, (short) 0x0000, (short) 0x000C,
                (byte) 0x00, false, false, 0,
                0, 0, -1, -1, -1, -1, -1,
                0, -1, -1, -1);

        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceExecutionModel.forGame("s1").phaseFor(previous, current));
    }

    @Test
    void unsupportedGameThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> TraceExecutionModel.forGame("bad"));
    }

    private static TraceData twoFrameS3kTraceWithGameMode(int gameMode) throws Exception {
        Path dir = Files.createTempDirectory("s3k-phase-model");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "AIZ",
              "zone_id": 0,
              "act": 0,
              "bk2_frame_offset": 0,
              "trace_frame_count": 2,
              "start_x": "0x0000",
              "start_y": "0x0000",
              "recording_date": "2026-06-09",
              "lua_script_version": "6.25-s3k",
              "trace_schema": 5,
              "characters": ["sonic", "tails"]
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj
            0000,0000,4AD8,0342,0000,0C08,0000,00,1,0,0,4100,7B00,02,4A38,02B6,0061,02,0000,08,0500,0000,1,4AE3,0346,0000,0C08,0000,00,1,0,0,E300,7C00,02,03,08
            0001,0000,4AD8,0342,0000,0C08,0000,00,1,0,0,4100,7B00,02,4A38,02B6,0061,02,0000,08,0500,0000,1,4AE3,0346,0000,0C08,0000,00,1,0,0,E300,7C00,02,03,08
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), String.format(
                "{\"frame\":0,\"event\":\"zone_act_state\",\"actual_zone_id\":1,"
                        + "\"actual_act\":0,\"apparent_act\":0,\"game_mode\":%d}%n",
                gameMode));
        return TraceData.load(dir);
    }
}
