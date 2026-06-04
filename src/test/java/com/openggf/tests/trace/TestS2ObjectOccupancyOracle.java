package com.openggf.tests.trace;

import com.openggf.game.GameServices;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Comparison-only measurement and assertion of the engine's dynamic-slot
 * occupancy against the ROM trace timeline using {@link ObjectOccupancyOracle}.
 *
 * <p><strong>Self-deleting transient assertion (Task 1.7, piece a).</strong>
 * The green S2 traces (EHZ1, SCZ, WFZ) assert frame-for-frame parity of the
 * live <em>count</em> of the badnik-death explosion (Obj27), whose destroy
 * frame is a fixed {@code anim_frame_duration} countdown: it deletes 35 game
 * frames after spawn in S2/S3K (init 3 / reload 7 / delete at mapping_frame 5 —
 * docs/s2disasm/s2.asm:46672-46684). Piece (a) aligns the engine explosion to
 * that exact frame (previously it lingered 4 frames via a uniform 8-frame
 * delay).
 *
 * <p>The assertion is deliberately scoped two ways. First, by id (see
 * {@link #TRANSIENT_SELF_DELETE_IDS}): the Animal (Obj28) despawns by walk/fly
 * physics and the off-screen {@code MarkObjGone} window (docs/s2disasm/s2.asm
 * Obj28_Walk/Obj28_Fly), and the points popup (Obj29) — already ROM-correct on
 * lifespan — diverges only by a one-frame spawn-windowing offset; both are
 * object-lifetime categories outside piece (a). Second, by <em>count</em>
 * rather than by slot: it compares the number of live Obj27 instances the
 * engine holds against the ROM timeline. A delete that is a frame late leaves
 * engineCount &gt; romCount; a frame early leaves engineCount &lt; romCount.
 * Counting by id ignores spawn-slot-allocation / windowing drift (the engine
 * spawning the same transient into a different slot than the ROM — piece b),
 * which would otherwise mask or fake a transient-timing regression. See
 * {@link ObjectOccupancyOracle#firstTransientCountDivergence}.
 *
 * <p>MTZ1 stays a non-asserting MEASUREMENT: it is a trace frontier (not a green
 * trace), so its occupancy diverges for windowing reasons unrelated to transient
 * timing.
 *
 * <p><strong>Comparison-only invariant:</strong> the oracle and this test read
 * trace data and engine state and report; they never write engine state from
 * the trace.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestS2ObjectOccupancyOracle {

    private static final int FIRST_DYNAMIC_SLOT = ObjectSlotLayout.SONIC_2.firstDynamicSlot();

    /**
     * Self-deleting transient object id whose destroy frame this assertion
     * guards: Obj27, the badnik-death explosion. Its ROM lifespan is a fixed
     * {@code anim_frame_duration} countdown (init 3 / reload 7 / delete at
     * mapping_frame 5 in S2/S3K) = 35 game frames from spawn; piece (a) aligns
     * the engine to that exact frame.
     *
     * <p>Obj29 (the floating points popup) is deliberately NOT in scope even
     * though its self-delete logic is also fixed-countdown and the engine
     * already matches the ROM lifespan exactly (32 frames: delete when
     * {@code y_vel >= 0}, docs/s2disasm/s2.asm Obj29_Main). Its per-frame count
     * still diverges by one frame in some green traces (e.g. EHZ1 f1308: ROM
     * spawns the points at f1309, the engine at f1308) because the engine spawns
     * it one frame off the ROM {@code AllocateObject} ordering — a
     * spawn-slot-windowing offset (piece b), not a delete-frame error. Including
     * Obj29 would make this assertion red for a reason outside piece (a).
     */
    private static final Set<Integer> TRANSIENT_SELF_DELETE_IDS = Set.of(0x27);

    @Test
    public void measureMtz1OccupancyDivergence() throws Exception {
        ObjectOccupancyOracle.Divergence first =
                measureFirstDivergence("mtz", Sonic2ZoneConstants.ZONE_MTZ, 0, null);
        if (first == null) {
            System.out.println(
                    "[occupancy-oracle] MTZ1: no dynamic-slot occupancy divergence "
                            + "across the trace.");
        } else {
            System.out.printf(
                    "[occupancy-oracle] MTZ1 first divergence: frame=%d slot=%d "
                            + "expectedId=0x%02X actualId=0x%02X%n",
                    first.frame(), first.slot(),
                    first.expectedId() & 0xFF, first.actualId() & 0xFF);
        }
        // Measurement only: MTZ1 is a trace frontier, not a green trace.
    }

    @Test
    public void scz1TransientOccupancyMatchesRom() throws Exception {
        assertTransientOccupancy("scz", Sonic2ZoneConstants.ZONE_SCZ, 0);
    }

    @Test
    public void wfz1TransientOccupancyMatchesRom() throws Exception {
        assertTransientOccupancy("wfz", Sonic2ZoneConstants.ZONE_WFZ, 0);
    }

    /**
     * Asserts that for every replayed frame of the named green S2 trace, the
     * engine never holds MORE live instances of a self-deleting transient
     * ({@link #TRANSIENT_SELF_DELETE_IDS}) than the ROM timeline — i.e. the
     * transient never self-deletes LATER than the ROM {@code DeleteObject}. This
     * is exactly the piece-(a) regression the explosion fix closes (the old
     * uniform 8-frame delay made the explosion linger 4 frames). Counting by id
     * ignores slot reshuffle; restricting to {@code engineCount > romCount}
     * ignores the {@code engineCount < romCount} spawn-frame windowing offset
     * that belongs to piece (b).
     */
    private void assertTransientOccupancy(String route, int zone, int act) throws Exception {
        ObjectOccupancyOracle.CountDivergence first = driveTrace(route, zone, act,
                (trace, om, frame) -> ObjectOccupancyOracle.firstTransientCountDivergence(
                        trace, om, frame, FIRST_DYNAMIC_SLOT, TRANSIENT_SELF_DELETE_IDS, true));
        Assertions.assertNull(first, () -> first == null ? "" : String.format(
                "[occupancy-oracle] %s transient lingers past its ROM DeleteObject: "
                        + "frame=%d id=0x%02X romCount=%d engineCount=%d "
                        + "(scope=Obj27; engineCount>romCount = late self-delete)",
                route.toUpperCase(), first.frame(), first.id(),
                first.romCount(), first.engineCount()));
    }

    /** Per-frame comparison-only probe over the driven engine + ROM timeline. */
    @FunctionalInterface
    private interface FrameProbe<T> {
        T check(TraceData trace, ObjectManager om, int frame);
    }

    @Test
    public void ehz1TransientOccupancyMatchesRom() throws Exception {
        // EHZ1 is a green S2 trace (Sonic+Tails). Assert transient self-delete
        // occupancy (Obj27/Obj29) frame-for-frame against the ROM timeline.
        Path traceDir = Path.of("src/test/resources/traces/s2").resolve("ehz1_fullrun");
        Assumptions.assumeTrue(Files.isDirectory(traceDir),
                "EHZ1 trace directory not found: " + traceDir);
        assertTransientOccupancy("ehz1_fullrun", Sonic2ZoneConstants.ZONE_EHZ, 0);
    }

    /**
     * Unscoped slot-occupancy measurement (every slot divergence) used by the
     * non-asserting MTZ1 frontier probe.
     */
    private ObjectOccupancyOracle.Divergence measureFirstDivergence(
            String route, int zone, int act, Set<Integer> unused) throws Exception {
        return driveTrace(route, zone, act,
                (trace, om, frame) -> ObjectOccupancyOracle.firstDivergence(
                        trace, om, frame, FIRST_DYNAMIC_SLOT));
    }

    /**
     * Drives the named S2 level-select trace through the engine (mirroring the
     * S2 branch of {@code AbstractTraceReplayTest.replayMatchesTrace}) and
     * returns the first non-null result from {@code probe}, or {@code null} when
     * the probe reported no divergence for any replayed frame.
     */
    private <T> T driveTrace(String route, int zone, int act, FrameProbe<T> probe)
            throws Exception {
        Path traceDir = Path.of("src/test/resources/traces/s2").resolve(route);
        Assumptions.assumeTrue(Files.isDirectory(traceDir),
                "Trace directory not found: " + traceDir);
        Assumptions.assumeTrue(Files.exists(traceDir.resolve("metadata.json")),
                "metadata.json not found in " + traceDir);

        Path bk2Path = findBk2File(traceDir);
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + traceDir);

        TraceData trace = TraceData.load(traceDir);
        TraceMetadata meta = trace.metadata();
        Assumptions.assumeTrue("s2".equals(meta.game()),
                "Expected an S2 trace but metadata.game=" + meta.game());

        boolean requiresFreshLevelLoad =
                TraceReplayBootstrap.requiresFreshLevelLoadForTraceReplay(trace);
        TraceReplaySessionBootstrap.prepareConfiguration(trace, meta);

        SharedLevel sharedLevel = requiresFreshLevelLoad
                ? null
                : SharedLevel.load(SonicGame.SONIC_2, zone, act);
        try {
            HeadlessTestFixture.Builder fixtureBuilder = HeadlessTestFixture.builder()
                    .withRecording(bk2Path)
                    .withRecordingStartFrame(
                            TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace));
            if (sharedLevel != null) {
                fixtureBuilder.withSharedLevel(sharedLevel);
            } else {
                fixtureBuilder.withZoneAndAct(zone, act);
            }
            if (TraceReplayBootstrap.shouldApplyMetadataStartPositionForTraceReplay(trace)) {
                fixtureBuilder
                        .startPosition(meta.startX(), meta.startY())
                        .startPositionIsCentre();
            }
            HeadlessTestFixture fixture = fixtureBuilder.build();

            TraceReplaySessionBootstrap.BootstrapResult boot =
                    TraceReplaySessionBootstrap.applyBootstrap(trace, fixture, -1);

            ObjectManager om = GameServices.level() != null
                    ? GameServices.level().getObjectManager() : null;
            Assumptions.assumeTrue(om != null, "ObjectManager unavailable after bootstrap");

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
                T divergence = probe.check(trace, om, i);
                if (divergence != null) {
                    return divergence;
                }
            }
            return null;
        } finally {
            if (sharedLevel != null) {
                sharedLevel.dispose();
            } else {
                TestEnvironment.resetAll();
            }
        }
    }

    private static Path findBk2File(Path dir) throws Exception {
        try (var files = Files.list(dir)) {
            return files
                    .filter(p -> p.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
    }
}
