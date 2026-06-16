package com.openggf.tests.trace;

import com.openggf.trace.*;

import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTraceReplayStartPositionPolicy {

    @Test
    void s3kEndToEndTraceUsesLiveIntroSpawnInsteadOfRecordedFrameZeroPosition() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun"));
        TraceMetadata metadata = trace.metadata();

        AbstractTraceReplayTest subject = new AbstractTraceReplayTest() {
            @Override
            protected SonicGame game() {
                return SonicGame.SONIC_3K;
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
        };

        Method method = AbstractTraceReplayTest.class.getDeclaredMethod(
                "shouldApplyMetadataStartPosition",
                TraceData.class,
                TraceMetadata.class);
        method.setAccessible(true);

        boolean shouldApply = (boolean) method.invoke(subject, trace, metadata);

        assertFalse(
                shouldApply,
                "The pre-level-prefix S3K AIZ full-run trace starts before LEVEL mode, so replay must "
                        + "keep the engine's live intro spawn instead of applying frame-zero "
                        + "start_x/start_y from stale Player_1 RAM.");
    }

    @Test
    void s3kEndToEndTraceStartsAtFrameZeroWithoutSkippingIntro() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun"));

        assertTrue(trace.metadata().hasPreLevelIntroPrefix(),
                "Pre-level prefix behavior must be declared by generic fixture metadata.");
        assertTrue(TraceReplayBootstrap.releaseBlockersForTraceReplay(trace).isEmpty(),
                "The regenerated AIZ full-run fixture must not be release-blocked by legacy heuristics.");
        assertFalse(TraceReplayBootstrap.shouldSeedFrameZeroForTraceReplay(trace));
        assertEquals(0, TraceReplayBootstrap.replaySeedTraceIndexForTraceReplay(trace));
        // Strict comparison starts on the first real AIZ level frame, where
        // the ROM has switched to Game_Mode 0x0C and spawned Obj_AIZPlaneIntro.
        assertEquals(289, TraceReplayBootstrap.strictStartTraceIndexForTraceReplay(trace),
                "AIZ frame-0 Player_1 RAM is still the title banner object; strict "
                        + "gameplay comparison starts when the ROM reaches Game_Mode 0x0C.");
        assertEquals(trace.metadata().bk2FrameOffset(),
                TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace),
                "Pre-level-prefix replay should trust the recorder's BK2 frame offset.");
        assertEquals(0,
                TraceReplayBootstrap.preTraceOscillationFramesForTraceReplay(trace, -1),
                "The AIZ prefix is simulated from frame 0, so no separate oscillator seed is required.");
        assertEquals(0,
                TraceReplayBootstrap.initialOscillationSuppressionFramesForTraceReplay(trace),
                "The AIZ intro prefix now drives the first LevelLoop tick natively, so "
                        + "Obj_FloatingPlatform must see the live OscillateNumDo phase without "
                        + "an extra replay-local suppression.");
        assertEquals(0,
                TraceReplayBootstrap.s3kCompleteRunAnimatedTilePreludeFramesForTraceReplay(trace),
                "Pre-level-prefix full-run traces simulate the intro natively and must not "
                        + "receive complete-run segment animation prelude.");
    }

    @Test
    void s3kEndToEndTracePreLevelPrefixAdvancesMovieWithoutTickingLevel() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun"));

        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceReplayBootstrap.phaseForReplay(trace, null, trace.getFrame(0)),
                "Pre-level prefix rows advance the movie without ticking the loaded level.");
    }

    @Test
    void vblankOnlyRowsAdvanceMovieButDoNotCompareGameplayState() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun"));

        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceReplayBootstrap.phaseForReplay(trace, trace.getFrame(287), trace.getFrame(288)),
                "Rows before the first LEVEL-mode frame stay VBlank-only.");
    }

    @Test
    void preLevelPrefixInputEdgeWithoutStateAdvanceOnlyConsumesMovieInput() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun"));

        int inputOnlyIndex = firstInputOnlyStateRow(trace);
        TraceFrame previous = trace.getFrame(inputOnlyIndex - 1);
        TraceFrame current = trace.getFrame(inputOnlyIndex);

        assertEquals(TraceExecutionPhase.ADVANCE_ONLY,
                TraceReplayBootstrap.phaseForReplay(trace, previous, current),
                "Pre-level-prefix rows can record a new held input before the ROM applies it. "
                        + "Replay must advance native gameplay for counter parity but skip comparing "
                        + "the duplicated sampled state.");
        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceReplayBootstrap.phaseForReplay(trace, current, trace.getFrame(inputOnlyIndex + 1)),
                "The following row advances state and should consume the already-aligned input normally "
                        + "(selected trace row " + current.frame() + ").");
    }

    @Test
    void preLevelPrefixInputEdgeWithStateAdvanceStillTicksGameplay() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun"));

        int inputLatchIndex = firstStateAdvancingInputLatchRow(trace);
        TraceFrame previous = trace.getFrame(inputLatchIndex - 1);
        TraceFrame current = trace.getFrame(inputLatchIndex);

        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceReplayBootstrap.phaseForReplay(trace, previous, current),
                "When a pre-level-prefix row changes the sampled input while state still reflects "
                        + "the prior input, replay should still tick gameplay with the previous "
                        + "movie row (selected trace row " + current.frame() + ").");
        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceReplayBootstrap.phaseForReplay(trace, current, trace.getFrame(inputLatchIndex + 1)),
                "The following row should step with the latched input.");
        assertTrue(TraceReplayBootstrap.shouldUsePreviousRecordingInputForTraceReplay(trace),
                "Pre-level-prefix replay should validate the current BK2 row while driving "
                        + "state-advancing frames with the previous row.");
    }

    @Test
    void traceReplayBootstrapNeverReportsTraceFrameAsActualPrimaryState() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/trace/TraceReplayBootstrap.java"));

        assertFalse(source.contains("fromTraceFrame"),
                "Trace rows are comparison input only; replay bootstrap must not expose a helper "
                        + "that can turn a TraceFrame into the actual engine primary state.");
        assertFalse(source.contains("trace-vblank"),
                "Legacy pre-level rows may be skipped for comparison, but must not be reported "
                        + "as actual player state.");
    }

    @Test
    void frameZeroSidekickAndObjectBootstrapCoverageIsDocumentedAsPartial() throws Exception {
        String testBase = Files.readString(Path.of(
                "src/test/java/com/openggf/tests/trace/AbstractTraceReplayTest.java"));
        String releaseIssues = Files.readString(Path.of("docs/release-architecture-review-issues.md"));

        assertTrue(testBase.contains("Sidekick CPU state is now captured")
                        && testBase.contains("Per-slot SST snapshots are still left empty"),
                "The trace test base must keep captured sidekick CPU state and missing frame-0 SST views visible.");
        assertTrue(releaseIssues.contains("not a full sidekick/SST parity proof"),
                "Release notes must not claim warning-only bootstrap gaps prove strict parity.");
    }

    @Test
    void s3kGameplayTraceSeedsFrameZeroAfterSidekickOnlyPrelude() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s3k/cnz"));

        assertFalse(trace.preTraceObjectSnapshots().isEmpty(),
                "CNZ records object snapshots for randomised balloon bob phases.");
        assertEquals(0, TraceReplayBootstrap.replaySeedTraceIndexForTraceReplay(trace));
        assertFalse(TraceReplayBootstrap.shouldSeedFrameZeroForTraceReplay(trace));
        assertEquals(1,
                TraceReplayBootstrap.sidekickTitleCardPreludeFramesForTraceReplay(trace),
                "S3K Sonic+Tails seed-frame traces need the single native sidekick setup tick "
                        + "observed before the first compared row.");
        assertEquals(1,
                TraceReplayBootstrap.levelObjectTitleCardPreludeFramesForTraceReplay(trace),
                "The same seed-frame trace has already run the setup Process_Sprites pass "
                        + "that initializes level objects such as Obj_CNZBalloon natively.");
        assertEquals(new TraceReplayBootstrap.ReplayStartState(1, 0),
                TraceReplayBootstrap.applyReplayStartStateForTraceReplay(trace, null),
                "Frame 0 is still a strict seed comparison; normal full-frame driving starts "
                        + "with trace frame 1.");
        assertEquals(trace.metadata().bk2FrameOffset(),
                TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace),
                "Frame 0 is seed-compared from the recorded BK2 offset, then frame 1 drives "
                        + "with the next movie row.");
        assertEquals(1,
                TraceReplayBootstrap.preTraceOscillationFramesForTraceReplay(trace, -1),
                "Frame 0 is seed-compared, not driven, but the ROM row has already passed one "
                        + "OscillateNumDo tick.");
        assertEquals(0,
                TraceReplayBootstrap.initialOscillationSuppressionFramesForTraceReplay(trace),
                "Pre-level-prefix replay drives oscillator timing natively as well.");
    }

    @Test
    void s3kMgzGameplayTraceDrivesFrameZeroWhenSonicAlreadyMoved() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s3k/mgz"));

        assertEquals(0x18, trace.getFrame(0).xSpeed(),
                "MGZ frame 0 is after the first input-driven Obj_Sonic update: "
                        + "Sonic_Move accelerates right and MoveSprite_TestGravity applies gravity "
                        + "(docs/skdisasm/sonic3k.asm:7888-7894, 21967-21985, 22350-22361, "
                        + "22428-22443, 22858-22876, 36068-36077).");
        assertEquals(0,
                TraceReplayBootstrap.sidekickTitleCardPreludeFramesForTraceReplay(trace),
                "MGZ frame 0 is driven natively because Sonic has already moved, so the sidekick "
                        + "seed-frame prelude does not apply.");
        assertEquals(TraceReplayBootstrap.ReplayStartState.DEFAULT,
                TraceReplayBootstrap.applyReplayStartStateForTraceReplay(trace, null),
                "MGZ frame 0 is not a sidekick-only seed row. Sonic has already moved, so the "
                        + "first BK2 input must still be stepped and compared natively.");
        assertEquals(0,
                TraceReplayBootstrap.preTraceOscillationFramesForTraceReplay(trace, -1),
                "Driving frame 0 natively also runs the first OscillateNumDo pass in the normal "
                        + "LevelLoop order (docs/skdisasm/sonic3k.asm:7888-7909).");
    }

    @Test
    void s3kCompleteRunSegmentsDoNotSeedFrameZeroTraceState() throws Exception {
        for (String route : java.util.List.of("aiz_completerun", "hcz_completerun",
                "mgz_completerun", "cnz_completerun", "icz_completerun",
                "lbz_completerun", "mhz_completerun")) {
            TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s3k", route));

            assertTrue(TraceReplayBootstrap.isS3kCompleteRunSegment(trace),
                    route + " must be recognized as a complete-run segment.");
            assertTrue(TraceReplayBootstrap.shouldApplyMetadataStartPositionForTraceReplay(trace),
                    route + " still uses the metadata start centre as bounded frame-zero bootstrap debt.");
            assertFalse(TraceReplayBootstrap.shouldGroundSnapMetadataStartForTraceReplay(trace),
                    route + " metadata is an in-level handoff centre, not a fresh spawn needing snap.");
            assertFalse(TraceReplayBootstrap.shouldSeedFrameZeroForTraceReplay(trace),
                    route + " must not copy recorded frame-zero player state into the engine.");
            assertFalse(TraceReplayBootstrap.shouldSeedReplayStartStateForTraceReplay(trace, 0),
                    route + " must not copy recorded replay-start state into the engine.");
            assertEquals(TraceReplayBootstrap.ReplayStartState.DEFAULT,
                    TraceReplayBootstrap.applyReplayStartStateForTraceReplay(trace, null),
                    route + " uses default native replay state; phase policy handles handoff rows.");
            assertEquals(0, TraceReplayBootstrap.sidekickTitleCardPreludeFramesForTraceReplay(trace),
                    route + " complete-run segments must not receive the sidekick seed-row prelude.");
            assertEquals(1, TraceReplayBootstrap.levelObjectTitleCardPreludeFramesForTraceReplay(trace),
                    route + " complete-run segments must reproduce the native S3K setup Process_Sprites pass "
                            + "before the frame-zero RNG seed is installed.");
            assertEquals(1, TraceReplayBootstrap.preTraceOscillationFramesForTraceReplay(trace, -1),
                    route + " complete-run segments begin after the ROM's setup OscillateNumDo pass, "
                            + "so the first replay-driven object pass must read that prior oscillator phase.");
            TraceExecutionPhase frameZeroPhase =
                    TraceReplayBootstrap.phaseForReplay(trace, null, trace.getFrame(0));
            TraceFrame frameZero = trace.getFrame(0);
            boolean handoffBeforeNativeMotionRow = !frameZero.stateEquals(trace.getFrame(1))
                    && frameZero.xSpeed() == 0
                    && frameZero.ySpeed() == 0
                    && frameZero.gSpeed() == 0;
            boolean visibleVelocityHoldRow = frameZero.stateEquals(trace.getFrame(1))
                    && (frameZero.xSpeed() != 0 || frameZero.ySpeed() != 0 || frameZero.gSpeed() != 0)
                    && frameZero.gameplayFrameCounter() == trace.getFrame(1).gameplayFrameCounter()
                    && frameZero.vblankCounter() == trace.getFrame(1).vblankCounter()
                    && frameZero.lagCounter() == trace.getFrame(1).lagCounter();
            TraceExecutionPhase expectedFrameZeroPhase = handoffBeforeNativeMotionRow
                    || visibleVelocityHoldRow
                    ? TraceExecutionPhase.VBLANK_ONLY
                    : TraceExecutionPhase.FULL_LEVEL_FRAME;
            assertEquals(expectedFrameZeroPhase, frameZeroPhase,
                    route + " frame 0 phase follows the structural handoff shape.");
            assertEquals(handoffBeforeNativeMotionRow,
                    TraceReplayBootstrap.isS3kCompleteRunHandoffCounterTickRow(trace),
                    route + " only handoff-before-motion rows tick Level_frame_counter without driving compared gameplay.");
            assertEquals(1 + (handoffBeforeNativeMotionRow ? 1 : 0),
                    TraceReplayBootstrap.s3kCompleteRunAnimatedTilePreludeFramesForTraceReplay(trace),
                    route + " advances only native S3K Animate_Tiles calls skipped before the first driven motion row.");
        }
    }

    @Test
    void s3kCompleteRunAirborneStartsDriveFrameZeroWhenVelocityAlreadyIntegrated() throws Exception {
        TraceData hcz = TraceData.load(Path.of("src/test/resources/traces/s3k/hcz_completerun"));
        TraceData mgz = TraceData.load(Path.of("src/test/resources/traces/s3k/mgz_completerun"));

        assertEquals(0x0038, hcz.getFrame(0).ySpeed() & 0xFFFF,
                "HCZ frame 0 already includes the first S3K gravity step.");
        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceReplayBootstrap.phaseForReplay(hcz, null, hcz.getFrame(0)),
                "HCZ frame 0 must be driven and compared, otherwise replay is one gravity tick late.");
        assertEquals(0x0038, mgz.getFrame(0).ySpeed() & 0xFFFF,
                "MGZ frame 0 already includes the first S3K gravity step.");
        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceReplayBootstrap.phaseForReplay(mgz, null, mgz.getFrame(0)),
                "MGZ frame 0 must be driven and compared, otherwise replay is one gravity tick late.");
        assertFalse(TraceReplayBootstrap.isS3kCompleteRunHandoffCounterTickRow(hcz),
                "HCZ already includes native velocity on frame 0, so no handoff-before-motion counter tick applies.");
        assertFalse(TraceReplayBootstrap.isS3kCompleteRunHandoffCounterTickRow(mgz),
                "MGZ already includes native velocity on frame 0, so no handoff-before-motion counter tick applies.");
    }

    @Test
    void s3kCompleteRunRepeatedRowsTickHiddenStartupState() throws Exception {
        TraceData lbz = TraceData.load(Path.of("src/test/resources/traces/s3k/lbz_completerun"));
        TraceData cnz = TraceData.load(Path.of("src/test/resources/traces/s3k/cnz_completerun"));
        TraceData mhz = TraceData.load(Path.of("src/test/resources/traces/s3k/mhz_completerun"));

        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceReplayBootstrap.phaseForReplay(lbz, null, lbz.getFrame(0)),
                "LBZ starts with repeated visible player rows, but the launch object countdown must tick.");
        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceReplayBootstrap.phaseForReplay(lbz, lbz.getFrame(0), lbz.getFrame(1)),
                "Repeated LBZ rows still run hidden startup object state.");
        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceReplayBootstrap.phaseForReplay(cnz, cnz.getFrame(0), cnz.getFrame(1)),
                "CNZ row 1 advances state from the handoff row and should tick exactly once.");
        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceReplayBootstrap.phaseForReplay(cnz, null, cnz.getFrame(0)),
                "CNZ frame 0 is the visible state before the first level tick.");
        assertTrue(TraceReplayBootstrap.isS3kCompleteRunHandoffCounterTickRow(cnz),
                "CNZ's handoff-before-motion row consumes the ROM counter edge without driving carry physics.");
        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceReplayBootstrap.phaseForReplay(mhz, null, mhz.getFrame(0)),
                "MHZ frame 0 is the visible state before the first level tick.");
        assertTrue(TraceReplayBootstrap.isS3kCompleteRunHandoffCounterTickRow(mhz),
                "MHZ's handoff-before-motion row consumes the ROM counter edge without driving carry physics.");
        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceReplayBootstrap.phaseForReplay(mhz, mhz.getFrame(0), mhz.getFrame(1)),
                "MHZ row 1 advances state from the handoff row and should tick exactly once.");
    }

    @Test
    void s3kCompleteRunVisibleVelocityHoldRowsWaitForFirstStateChange() throws Exception {
        TraceData icz = TraceData.load(Path.of("src/test/resources/traces/s3k/icz_completerun"));
        TraceData lbz = TraceData.load(Path.of("src/test/resources/traces/s3k/lbz_completerun"));

        assertEquals(0x0800, icz.getFrame(0).xSpeed() & 0xFFFF,
                "ICZ frame 0 carries native launch velocity even though the visible row is still held.");
        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceReplayBootstrap.phaseForReplay(icz, null, icz.getFrame(0)),
                "The initial ICZ complete-run visible hold row is before the first native motion sample.");
        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceReplayBootstrap.phaseForReplay(icz, icz.getFrame(27), icz.getFrame(28)),
                "Repeated visible ICZ startup rows should not tick gameplay before motion appears.");
        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceReplayBootstrap.phaseForReplay(icz, icz.getFrame(28), icz.getFrame(29)),
                "The first ICZ state-changing row owns the native movement step.");

        assertEquals(0, lbz.getFrame(0).ySpeed(),
                "LBZ's repeated rows are a zero-velocity hidden launch countdown, not a visible velocity hold.");
        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceReplayBootstrap.phaseForReplay(lbz, null, lbz.getFrame(0)),
                "LBZ still ticks the hidden ground-launch countdown during repeated visible rows.");
    }

    @Test
    void s3kCompleteRunBootstrapDebtStaysNarrowAndDocumented() throws Exception {
        String replayBootstrap = Files.readString(Path.of(
                "src/main/java/com/openggf/trace/TraceReplayBootstrap.java"));
        String sessionBootstrap = Files.readString(Path.of(
                "src/main/java/com/openggf/trace/replay/TraceReplaySessionBootstrap.java"));
        String discrepancies = Files.readString(Path.of("docs/KNOWN_DISCREPANCIES.md"));
        String releaseIssues = Files.readString(Path.of("docs/release-architecture-review-issues.md"));

        assertFalse(sessionBootstrap.contains("seedS3kCompleteRunStartState"),
                "The retired S3K complete-run trace-state seed helper must not return.");
        assertTrue(replayBootstrap.matches(
                        "(?s).*public static boolean shouldSeedFrameZeroForTraceReplay\\(TraceData trace\\) \\{\\s*return false;\\s*\\}.*"),
                "TraceReplayBootstrap should keep frame-zero trace-row state seeding disabled.");
        assertTrue(replayBootstrap.matches(
                        "(?s).*public static boolean shouldSeedReplayStartStateForTraceReplay\\(TraceData trace,\\s*int requestedSeedTraceIndex\\) \\{\\s*return false;\\s*\\}.*"),
                "TraceReplayBootstrap should keep replay-start trace-row state seeding disabled.");
        assertTrue(discrepancies.contains("S3K Complete-Run Segment Start-Position Bootstrap Debt"),
                "The remaining metadata start-position bootstrap debt must stay documented.");
        assertTrue(releaseIssues.contains("| REL-034 | resolved | Trace policy |"),
                "REL-034 should remain resolved only while the seed path is removed or narrowed.");
    }

    @Test
    void s3kGameplayTraceStillDoesNotSeedFrameZeroWhenObjectSnapshotsExist() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s3k/cnz"));

        assertFalse(trace.preTraceObjectSnapshots().isEmpty());
        assertFalse(TraceReplayBootstrap.shouldSeedReplayStartStateForTraceReplay(trace, 0),
                "Pre-trace object snapshots and primary frame-0 rows are comparison data only.");
    }

    @Test
    void s2SczAndWfzLevelSelectUseNativeTornadoRideStart() throws Exception {
        TraceData scz = TraceData.load(Path.of("src/test/resources/traces/s2/scz"));
        TraceData wfz = TraceData.load(Path.of("src/test/resources/traces/s2/wfz"));

        assertTrue(TraceReplayBootstrap.isS2TornadoRideStartMetadataCandidate(scz),
                "SCZ metadata is eligible for the ObjB2-authorized Tornado ride-start prelude.");
        assertTrue(TraceReplayBootstrap.isS2TornadoRideStartMetadataCandidate(wfz),
                "WFZ metadata is eligible for the ObjB2-authorized Tornado ride-start prelude.");
        assertFalse(TraceReplayBootstrap.shouldApplyMetadataStartPositionForTraceReplay(scz),
                "S2 Tornado release replay must not copy metadata start_x/start_y into the player.");
        assertFalse(TraceReplayBootstrap.shouldApplyMetadataStartPositionForTraceReplay(wfz),
                "S2 Tornado release replay must not copy metadata start_x/start_y into the player.");
    }

    @Test
    void s2SlotMachinePreludeUsesRecordedFeatureCapability() throws Exception {
        TraceData slotMachineTrace = TraceData.load(Path.of("src/test/resources/traces/s2/cnz"));
        TraceData tornadoTrace = TraceData.load(Path.of("src/test/resources/traces/s2/scz"));

        assertTrue(slotMachineTrace.metadata().hasPerFrameSlotMachineState(),
                "Replay policy should consume generic slot-machine recorder capability metadata.");
        assertEquals(4,
                TraceReplayBootstrap.zoneFeatureTitleCardPreludeFramesForTraceReplay(slotMachineTrace),
                "SlotMachine state traces need the native short init window before comparison.");
        assertEquals(10,
                TraceReplayBootstrap.zoneFeatureTitleCardPreludeStartVblankOffsetForTraceReplay(slotMachineTrace));
        assertEquals(0,
                TraceReplayBootstrap.zoneFeatureTitleCardPreludeFramesForTraceReplay(tornadoTrace),
                "Traces without the slot-machine recorder schema must not receive a zone-id prelude.");
        assertEquals(0,
                TraceReplayBootstrap.levelObjectTitleCardPreludeFramesForTraceReplay(slotMachineTrace),
                "Metadata-only replay policy must not apply Tornado object preludes to non-Tornado routes.");
    }

    @Test
    void ordinaryS2TraceDoesNotUseTornadoRideStart() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s2/ehz1_fullrun"));

        assertFalse(TraceReplayBootstrap.isS2TornadoRideStartMetadataCandidate(trace));
    }

    private static int firstInputOnlyStateRow(TraceData trace) {
        boolean afterGameplayStart = false;
        for (int i = 1; i + 1 < trace.frameCount(); i++) {
            if (!afterGameplayStart) {
                for (TraceEvent event : trace.getEventsForFrame(i)) {
                    if (event instanceof TraceEvent.Checkpoint checkpoint
                            && "gameplay_start".equals(checkpoint.name())) {
                        afterGameplayStart = true;
                    }
                }
                continue;
            }
            TraceFrame previous = trace.getFrame(i - 1);
            TraceFrame current = trace.getFrame(i);
            if (current.input() != previous.input()
                    && current.stateEquals(previous)
                    && !trace.getFrame(i + 1).stateEquals(current)
                    && current.gameplayFrameCounter() == previous.gameplayFrameCounter()
                    && current.vblankCounter() == previous.vblankCounter()
                    && current.lagCounter() == previous.lagCounter()) {
                return i;
            }
        }
        throw new AssertionError("No input-only state row found");
    }

    private static int firstStateAdvancingInputLatchRow(TraceData trace) {
        boolean afterGameplayStart = false;
        for (int i = 1; i + 1 < trace.frameCount(); i++) {
            if (!afterGameplayStart) {
                for (TraceEvent event : trace.getEventsForFrame(i)) {
                    if (event instanceof TraceEvent.Checkpoint checkpoint
                            && "gameplay_start".equals(checkpoint.name())) {
                        afterGameplayStart = true;
                    }
                }
                continue;
            }
            TraceFrame previous = trace.getFrame(i - 1);
            TraceFrame current = trace.getFrame(i);
            if (current.input() != previous.input()
                    && !current.stateEquals(previous)
                    && current.gameplayFrameCounter() == previous.gameplayFrameCounter()
                    && current.vblankCounter() == previous.vblankCounter()
                    && current.lagCounter() == previous.lagCounter()) {
                return i;
            }
        }
        throw new AssertionError("No state-advancing input latch row found");
    }
}
