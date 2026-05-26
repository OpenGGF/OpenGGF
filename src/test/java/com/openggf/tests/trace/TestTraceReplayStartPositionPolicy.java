package com.openggf.tests.trace;

import com.openggf.trace.*;

import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
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
                "The legacy S3K AIZ full-run trace starts from power-on state, so replay must "
                        + "keep the engine's live intro spawn instead of applying frame-zero "
                        + "start_x/start_y from stale Player_1 RAM.");
    }

    @Test
    void s3kEndToEndTraceStartsAtFrameZeroWithoutSkippingIntro() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun"));

        assertFalse(TraceReplayBootstrap.shouldUseLegacyS3kAizIntroWarmup(trace));
        assertFalse(TraceReplayBootstrap.shouldSeedFrameZeroForTraceReplay(trace));
        assertEquals(0, TraceReplayBootstrap.replaySeedTraceIndexForTraceReplay(trace));
        // Strict comparison starts on the first real AIZ level frame, where
        // the ROM has switched to Game_Mode 0x0C and spawned Obj_AIZPlaneIntro.
        assertEquals(289, TraceReplayBootstrap.strictStartTraceIndexForTraceReplay(trace),
                "AIZ frame-0 Player_1 RAM is still the title banner object; strict "
                        + "gameplay comparison starts when the ROM reaches Game_Mode 0x0C.");
        assertEquals(trace.metadata().bk2FrameOffset() - 1,
                TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace),
                "AIZ row 0 is emitted immediately after recorder arming, so the BK2 cursor "
                        + "starts one input frame earlier while still playing the full intro prefix.");
        assertEquals(0,
                TraceReplayBootstrap.preTraceOscillationFramesForTraceReplay(trace, -1),
                "The AIZ prefix is simulated from frame 0, so no separate oscillator seed is required.");
        assertEquals(0,
                TraceReplayBootstrap.initialOscillationSuppressionFramesForTraceReplay(trace),
                "The AIZ intro prefix now drives the first LevelLoop tick natively, so "
                        + "Obj_FloatingPlatform must see the live OscillateNumDo phase without "
                        + "an extra replay-local suppression.");
    }

    @Test
    void s3kEndToEndTracePreLevelPrefixAdvancesMovieWithoutTickingLevel() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun"));

        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceReplayBootstrap.phaseForReplay(trace, null, trace.getFrame(0)),
                "Frame 0 is Game_Mode 0x4C and Player_1/Player_2 RAM belongs to title-screen objects.");
        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceReplayBootstrap.phaseForReplay(trace, trace.getFrame(287), trace.getFrame(288)),
                "The BK2 cursor should advance through the pre-level prefix without starting AIZ early.");
        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceReplayBootstrap.phaseForReplay(trace, trace.getFrame(288), trace.getFrame(289)),
                "Frame 289 is the Game_Mode 0x0C setup boundary; native level playback starts "
                        + "on the next LevelLoop row.");
    }

    @Test
    void vblankOnlyRowsAdvanceMovieButDoNotCompareGameplayState() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun"));

        assertFalse(TraceReplayBootstrap.shouldCompareGameplayStateForReplay(
                        TraceReplayBootstrap.phaseForReplay(trace, null, trace.getFrame(0))),
                "Pre-level AIZ rows sample title/intro RAM, not loaded-level Sonic state.");
        assertFalse(TraceReplayBootstrap.shouldCompareGameplayStateForReplay(
                        TraceReplayBootstrap.phaseForReplay(trace, trace.getFrame(287), trace.getFrame(288))),
                "VBLANK_ONLY rows should only advance BK2/VBlank timing.");
        assertFalse(TraceReplayBootstrap.shouldCompareGameplayStateForReplay(
                        TraceReplayBootstrap.phaseForReplay(trace, trace.getFrame(288), trace.getFrame(289))),
                "The first Game_Mode 0x0C setup boundary is still VBLANK_ONLY for gameplay comparison.");
    }

    @Test
    void s3kGameplayTraceSeedsFrameZeroAfterSidekickOnlyPrelude() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s3k/cnz"));

        assertFalse(trace.preTraceObjectSnapshots().isEmpty(),
                "CNZ records object snapshots for randomised balloon bob phases.");
        assertFalse(TraceReplayBootstrap.shouldUseLegacyS3kAizIntroWarmup(trace));
        assertEquals(0, TraceReplayBootstrap.replaySeedTraceIndexForTraceReplay(trace));
        assertFalse(TraceReplayBootstrap.shouldSeedFrameZeroForTraceReplay(trace));
        assertEquals(1,
                TraceReplayBootstrap.sidekickTitleCardPreludeFramesForTraceReplay(trace),
                "S3K Sonic+Tails seed-frame traces need the single native sidekick setup tick "
                        + "observed before the first compared row.");
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
                "Legacy AIZ full-intro replay now drives oscillator timing natively as well.");
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

        assertTrue(TraceReplayBootstrap.usesS2TornadoRideStartForTraceReplay(scz),
                "SCZ starts on ObjB2 after the native title-card object prelude, not as a ground spawn.");
        assertTrue(TraceReplayBootstrap.usesS2TornadoRideStartForTraceReplay(wfz),
                "WFZ starts on ObjB2 after the native title-card object prelude, not as a ground spawn.");
    }

    @Test
    void s2SlotMachinePreludeUsesRecordedFeatureCapability() throws Exception {
        TraceData slotMachineTrace = TraceData.load(Path.of("src/test/resources/traces/s2/cnz"));
        TraceData tornadoTrace = TraceData.load(Path.of("src/test/resources/traces/s2/scz"));

        assertTrue(slotMachineTrace.metadata().hasPerFrameCnzSlotMachineState());
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

        assertFalse(TraceReplayBootstrap.usesS2TornadoRideStartForTraceReplay(trace));
    }
}
