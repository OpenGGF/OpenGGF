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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Comparison-only measurement of the engine's dynamic-slot occupancy against
 * the ROM trace timeline using {@link ObjectOccupancyOracle}.
 *
 * <p>This is a MEASUREMENT test (Task 1.4): it drives the MTZ1 level-select
 * trace through the engine and reports the first per-frame slot-occupancy
 * divergence rather than asserting parity. The S2 ROM-windowing timing fixes
 * (Tasks 1.4b-1.6) have not landed yet, so the engine is expected to diverge
 * at the windowing-drift frame; Task 1.7 flips this to an assertion once the
 * windowing is ROM-timed.
 *
 * <p><strong>Comparison-only invariant:</strong> the oracle and this test read
 * trace data and engine state and report; they never write engine state from
 * the trace.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestS2ObjectOccupancyOracle {

    private static final int FIRST_DYNAMIC_SLOT = ObjectSlotLayout.SONIC_2.firstDynamicSlot();

    @Test
    public void measureMtz1OccupancyDivergence() throws Exception {
        ObjectOccupancyOracle.Divergence first =
                measureFirstDivergence("mtz", Sonic2ZoneConstants.ZONE_MTZ, 0);
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
        // Measurement only: do not assert. Task 1.7 enables the assertion.
    }

    @Test
    public void measureEhz1OccupancyDivergence() throws Exception {
        // A green S2 sample alongside MTZ1, per the plan's "MTZ + one green
        // S2 trace" measurement target.
        Path traceDir = Path.of("src/test/resources/traces/s2").resolve("ehz1_fullrun");
        Assumptions.assumeTrue(Files.isDirectory(traceDir),
                "EHZ1 trace directory not found: " + traceDir);
        ObjectOccupancyOracle.Divergence first =
                measureFirstDivergence("ehz1_fullrun", Sonic2ZoneConstants.ZONE_EHZ, 0);
        if (first == null) {
            System.out.println(
                    "[occupancy-oracle] EHZ1: no dynamic-slot occupancy divergence "
                            + "across the trace.");
        } else {
            System.out.printf(
                    "[occupancy-oracle] EHZ1 first divergence: frame=%d slot=%d "
                            + "expectedId=0x%02X actualId=0x%02X%n",
                    first.frame(), first.slot(),
                    first.expectedId() & 0xFF, first.actualId() & 0xFF);
        }
    }

    /**
     * Drives the named S2 level-select trace through the engine (mirroring the
     * S2 branch of {@code AbstractTraceReplayTest.replayMatchesTrace}) and
     * returns the first dynamic-slot occupancy divergence reported by the
     * oracle, or {@code null} when occupancy matched for every replayed frame.
     */
    private ObjectOccupancyOracle.Divergence measureFirstDivergence(
            String route, int zone, int act) throws Exception {
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
                ObjectOccupancyOracle.Divergence divergence =
                        ObjectOccupancyOracle.firstDivergence(trace, om, i, FIRST_DYNAMIC_SLOT);
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
