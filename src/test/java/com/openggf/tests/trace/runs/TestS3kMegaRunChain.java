package com.openggf.tests.trace.runs;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

/**
 * Chain integration test for the committed
 * {@code s3-knux-multibonus-ss} run (25 segments, 22 transitions: gumball x2,
 * slots x5, pachinko, blue spheres x3, Knuckles, zones AIZ -> HCZ -> MGZ).
 * Drives ONE continuous {@code GameLoop} through every segment via the shared
 * {@link AbstractRunChainTest} base, exercising both the {@code bonus_stage}
 * per-frame comparator path and the {@code special_stage} advance-uncompared
 * path, plus every {@code starpost_bonus} / {@code giant_ring} return-boundary
 * assertion this run carries.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMegaRunChain extends AbstractRunChainTest {

    private static final Path RUN_DIR = Path.of(
            "src", "test", "resources", "traces", "s3k", "runs", "s3-knux-multibonus-ss");

    @Test
    void bonusEntryRetainsRecordedTerrainReadiness() throws Exception {
        var observed = new java.util.concurrent.atomic.AtomicBoolean();
        var firstFadeClock = new java.util.concurrent.atomic.AtomicInteger(-1);
        var lastFadeClock = new java.util.concurrent.atomic.AtomicInteger(-1);
        afterProductionStep = () -> {
            int row = productionMovieRow;
            int clock = com.openggf.game.GameServices.level().getObjectManager().getVblaCounter();
            // This movie's native Pal_FadeToBlack owns rows 5461..5482.
            // Assertions observe the clock; these values never enter production.
            if (row == 5461) {
                firstFadeClock.set(clock);
            } else if (row == 5482) {
                lastFadeClock.set(clock);
            }
        };
        sourceLoadTailTimingObserver = snapshot -> {
            var terrainEdges = snapshot.schedule().edges().stream()
                    .filter(edge -> java.util.Set.of(4640, 4644, 4647, 4648).contains(edge.rawFrame()))
                    .toList();
            org.junit.jupiter.api.Assertions.assertEquals(4, terrainEdges.size());
            for (var edge : terrainEdges) {
                org.junit.jupiter.api.Assertions.assertTrue(snapshot.consumedIdentities()
                        .contains(edge.kind() + "#" + edge.ordinal()), edge.toString());
                org.junit.jupiter.api.Assertions.assertFalse(snapshot.unmatchedCompletions().stream()
                        .anyMatch(message -> message.contains(edge.kind() + "#" + edge.ordinal())),
                        "terrain completion must match its actual production job: " + edge);
                var handle = new com.openggf.game.timing.HardwareWorkHandle(
                        edge.kind(), edge.ordinal(), edge.submissionFingerprint());
                org.junit.jupiter.api.Assertions.assertFalse(
                        com.openggf.game.session.SessionManager.getCurrentGameplayMode()
                                .hardwareTiming().wasSubmittedUnrepresented(handle),
                        "terrain producer must submit under represented source ownership");
            }
            observed.set(true);
        };
        assertChainReplayThroughSegmentRow(RUN_DIR, 1, 1);
        org.junit.jupiter.api.Assertions.assertTrue(observed.get(),
                "the production source-load tail must run before the prefix stops");
        org.junit.jupiter.api.Assertions.assertTrue(firstFadeClock.get() >= 0 && lastFadeClock.get() >= 0,
                "both native fade endpoints must be observed");
        org.junit.jupiter.api.Assertions.assertEquals(21,
                (lastFadeClock.get() - firstFadeClock.get()) & 0xFFFF,
                "the 22 held fade rows must each advance the clock");
    }

    @Test
    void nativeLoadPhasesKeepTheirOwnReleaseClocks() throws Exception {
        var pendingBefore = new java.util.concurrent.atomic.AtomicBoolean();
        var seed = new java.util.concurrent.atomic.AtomicLong(-1);
        var releases = new java.util.ArrayList<Integer>();
        afterProductionStep = () -> {
            var level = com.openggf.game.GameServices.level();
            var phase = com.openggf.level.NativeLevelLoadAccess.pendingPhase(level);
            if (phase != null && phase.loadOrdinal() == 1 && phase.phase()
                    == com.openggf.game.internal.NativeLevelLoadPhaseAdmission.Phase.INITIAL_OBJECTS) {
                org.junit.jupiter.api.Assertions.assertFalse(level.getObjectManager().activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.GumballMachineObjectInstance.class).isEmpty());
                seed.set(com.openggf.game.GameServices.rng().getSeed());
            }
            boolean pending = com.openggf.level.NativeLevelLoadAccess.isPending(level);
            if (pendingBefore.getAndSet(pending) && !pending) {
                releases.add(level.getObjectManager().getVblaCounter());
                if (releases.size() == 1) {
                    org.junit.jupiter.api.Assertions.assertEquals(0x120,
                            com.openggf.game.GameServices.sprites().getMainPlayable().getCentreY(),
                            "native placement must clear inherited rolling geometry before writing the centre");
                }
            }
        };
        assertChainReplayThroughSegmentRow(RUN_DIR, 3, 1);
        org.junit.jupiter.api.Assertions.assertEquals(5528L, seed.get(),
                "the real Gumball object reads its seed before the final loading interrupt");
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(5529, 6957, 9097), releases,
                "native load clocks must reach their captured release boundaries before destination input");
    }

    @Test
    void multiBonusSpecialStageRoundTrip() throws Exception {
        assertChainReplay(RUN_DIR);
    }
}
