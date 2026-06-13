package com.openggf.tests.trace.s1;

import com.openggf.game.GameServices;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.ObjectOccupancyOracle;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Local comparison-only probe for the S1 LZ2 Obj64 frontier. This class is not
 * picked up by the default Surefire include pattern; run it explicitly while
 * diagnosing the LZ2 complete-run trace.
 */
@RequiresRom(SonicGame.SONIC_1)
class DebugS1Lz2BubblesOccupancyProbe {
    private static final Path TRACE_DIR =
            Path.of("src/test/resources/traces/s1/lz2_completerun");
    private static final int ZONE_LZ = 3;
    private static final int ACT_2 = 1;
    private static final int OBJ_BUBBLES = 0x64;
    private static final int FIRST_DYNAMIC_SLOT =
            ObjectSlotLayout.SONIC_1.firstDynamicSlot();

    @Test
    void measureObj64CountFrontier() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR),
                "Trace directory not found: " + TRACE_DIR);
        Assumptions.assumeTrue(Files.exists(TRACE_DIR.resolve("metadata.json")),
                "metadata.json not found in " + TRACE_DIR);

        TraceData trace = TraceData.load(TRACE_DIR);
        TraceMetadata meta = trace.metadata();
        Assumptions.assumeTrue("s1".equals(meta.game()),
                "Expected S1 metadata, got " + meta.game());

        Path bk2Path = resolveBk2File(TRACE_DIR, meta);
        Assumptions.assumeTrue(bk2Path != null,
                "No BK2 found for " + TRACE_DIR);

        boolean requiresFreshLevelLoad =
                TraceReplayBootstrap.requiresFreshLevelLoadForTraceReplay(trace);
        TraceReplaySessionBootstrap.prepareConfiguration(trace, meta);

        SharedLevel sharedLevel = requiresFreshLevelLoad
                ? null
                : SharedLevel.load(SonicGame.SONIC_1, ZONE_LZ, ACT_2);
        try {
            HeadlessTestFixture.Builder fixtureBuilder = HeadlessTestFixture.builder()
                    .withRecording(bk2Path)
                    .withRecordingStartFrame(
                            TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace));
            if (sharedLevel != null) {
                fixtureBuilder.withSharedLevel(sharedLevel);
            } else {
                fixtureBuilder.withZoneAndAct(ZONE_LZ, ACT_2);
            }
            if (TraceReplayBootstrap.shouldApplyMetadataStartPositionForTraceReplay(trace)) {
                fixtureBuilder
                        .startPosition(meta.startX(), meta.startY())
                        .startPositionIsCentre();
            }
            HeadlessTestFixture fixture = fixtureBuilder.build();
            TraceReplaySessionBootstrap.applyStartPositionAndGroundSnap(trace, fixture);
            TraceReplaySessionBootstrap.BootstrapResult boot =
                    TraceReplaySessionBootstrap.applyBootstrap(trace, fixture, -1);

            ObjectManager objectManager = GameServices.level() != null
                    ? GameServices.level().getObjectManager()
                    : null;
            Assumptions.assumeTrue(objectManager != null,
                    "ObjectManager unavailable after bootstrap");
            System.out.printf("[s1-lz2-obj64-count] policy objectPreludeFrames=%d startTraceIndex=%d%n",
                    TraceReplayBootstrap.levelObjectTitleCardPreludeFramesForTraceReplay(trace),
                    boot.replayStart().startingTraceIndex());
            System.out.println("[s1-lz2-obj64-count] post-bootstrap engine Obj64: "
                    + summarizeEngineObj64(objectManager));

            int startTraceIndex = boot.replayStart().startingTraceIndex();
            for (int i = startTraceIndex; i < trace.frameCount(); i++) {
                TraceFrame expected = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceReplayBootstrap.phaseForReplay(trace, previous, expected);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }
                if (!TraceReplayBootstrap.shouldCompareGameplayStateForReplay(phase)) {
                    continue;
                }

                ObjectOccupancyOracle.CountDivergence divergence =
                        ObjectOccupancyOracle.firstTransientCountDivergence(
                                trace,
                                objectManager,
                                i,
                                FIRST_DYNAMIC_SLOT,
                                Set.of(OBJ_BUBBLES),
                                false);
                if (divergence != null) {
                    System.out.printf(
                            "[s1-lz2-obj64-count] first divergence frame=%d "
                                    + "id=0x%02X romCount=%d engineCount=%d%n",
                            divergence.frame(),
                            divergence.id(),
                            divergence.romCount(),
                            divergence.engineCount());
                    System.out.println("[s1-lz2-obj64-count] engine Obj64: "
                            + summarizeEngineObj64(objectManager));
                    System.out.println("[s1-lz2-obj64-count] ROM Obj64 prev: "
                            + summarizeRomObj64(trace, divergence.frame() - 1));
                    System.out.println("[s1-lz2-obj64-count] ROM Obj64 curr: "
                            + summarizeRomObj64(trace, divergence.frame()));
                    return;
                }
            }

            System.out.println("[s1-lz2-obj64-count] no Obj64 count divergence");
        } finally {
            if (sharedLevel != null) {
                sharedLevel.dispose();
            } else {
                TestEnvironment.resetAll();
            }
        }
    }

    private static Path resolveBk2File(Path traceDir, TraceMetadata meta) throws Exception {
        if (meta.sourceBk2() != null && !meta.sourceBk2().isBlank()) {
            Path shared = traceDir.getParent().resolve("_movies").resolve(meta.sourceBk2());
            if (Files.exists(shared)) {
                return shared;
            }
        }
        try (var files = Files.list(traceDir)) {
            return files
                    .filter(p -> p.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static String summarizeEngineObj64(ObjectManager objectManager) {
        List<String> parts = new ArrayList<>();
        for (var instance : objectManager.getActiveObjects()) {
            if (!(instance instanceof AbstractObjectInstance object)
                    || object.getSpawn() == null
                    || (object.getSpawn().objectId() & 0xFF) != OBJ_BUBBLES) {
                continue;
            }
            String details = object.traceDebugDetails();
            parts.add(String.format(
                    "s%02d @%04X,%04X spawn=@%04X,%04X%s",
                    object.getSlotIndex(),
                    object.getX() & 0xFFFF,
                    object.getY() & 0xFFFF,
                    object.getSpawn().x() & 0xFFFF,
                    object.getSpawn().y() & 0xFFFF,
                    details == null || details.isBlank() ? "" : " " + details));
        }
        return String.join(" | ", parts);
    }

    private static String summarizeRomObj64(TraceData trace, int frame) {
        if (frame < 0 || !trace.metadata().hasPerFrameS1Obj64State()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (TraceEvent.S1Obj64State state : trace.s1Obj64StatesForFrame(frame)) {
            parts.add(String.format(
                    "f%d s%02d @%04X,%04X r=%02X status=%02X render=%02X "
                            + "sub=%02X anim=%02X obj32=%02X obj33=%02X "
                            + "obj34=%04X obj36=%04X obj38=%04X obj3c=%08X",
                    frame,
                    state.slot(),
                    state.x() & 0xFFFF,
                    state.y() & 0xFFFF,
                    state.routine() & 0xFF,
                    state.status() & 0xFF,
                    state.renderFlags() & 0xFF,
                    state.subtype() & 0xFF,
                    state.anim() & 0xFF,
                    state.objoff32() & 0xFF,
                    state.objoff33() & 0xFF,
                    state.objoff34() & 0xFFFF,
                    state.objoff36() & 0xFFFF,
                    state.objoff38() & 0xFFFF,
                    state.objoff3c() & 0xFFFFFFFFL));
        }
        return String.join(" | ", parts);
    }
}
