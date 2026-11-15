package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.ssz.SszStageLayer;
import com.openggf.game.soniccd.player.SonicCdPhysicsProvider;
import com.openggf.game.soniccd.zones.mmz.MmzStageSignals;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Rewind of the zones' stage signals, the typed inputs zone objects write and the player reads
 * ({@code CdPlayerStage.signalsRewindAdapter}).
 *
 * <ul>
 *   <li>Stardust Speedway's {@code layer} byte ({@link SszStageLayer}): {@code rehost-zph/long-R71A} (native movie,
 *       BK2 drives the engine) sets it at row 891 and the path swap clears it at row 1408. Captured at row 1400, 60
 *       frames run (through the clear), restored: the byte is set again. (A whole-registry restore there fails on
 *       another object's missing dynamic reference, so the adapter is restored alone.)</li>
 *   <li>Metallic Madness's {@link MmzStageSignals} (no native route reaches the laser, revolve door or spin disc): on
 *       R81A every signal is written, captured, overwritten and restored.</li>
 * </ul>
 */
class TestCdZoneSignalsRewind {
    @TempDir
    Path tempDir;

    @Test
    void stardustSpeedwayLayerRestores() throws Exception {
        Path movie = CdTestMedia.nativeFile("rehost-zph/long-R71A/movie.bk2");
        int[] layer = new int[3];
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, tempDir, new CdFixtureReplay.Fixture(CdStagePayload.R71A,
                    movie.getParent().resolve("trace"), movie), 1400, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                            var adapter = ((SonicCdPhysicsProvider) sonic.currentGameModule().getPhysicsProvider())
                                    .stage().signalsRewindAdapter();
                            layer[0] = SszStageLayer.of(level()).value();
                            var snapshot = adapter.capture();
                            for (int i = 0; i < 60; i++) runner.stepFrame(false, false, false, true, false);
                            layer[1] = SszStageLayer.of(level()).value();
                            adapter.restore(snapshot);
                            layer[2] = SszStageLayer.of(level()).value();
                        }
                    });
        }
        assertEquals(1, layer[0], "the native route has set the layer by row 1400");
        assertEquals(0, layer[1], "the path swap clears it within the next 60 frames");
        assertEquals(1, layer[2], "restore puts the layer back");
    }

    @Test
    void metallicMadnessSignalsRestore() throws Exception {
        Path movie = CdTestMedia.nativeFile("rehost-fixtures/R81A/movie.bk2");
        String[] seen = new String[3];
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, tempDir, new CdFixtureReplay.Fixture(CdStagePayload.R81A,
                    movie.getParent().resolve("trace"), movie), 200, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                            MmzStageSignals signals = MmzStageSignals.of(sonic.currentGameModule(), level());
                            assertNotNull(signals, "R81A has Metallic Madness signals");
                            seen[0] = String.valueOf(signals.capture());
                            signals.flipStageLayer();
                            signals.flipShrunk();
                            signals.setDiscHold(1);
                            signals.setRevolveHold(true);
                            signals.setObjectState(0x15, 3);
                            signals.setBossFlags(8);
                            signals.setBossStarted(1);
                            signals.setBossDoorFlag(true);
                            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                            CompositeSnapshot snapshot = registry.capture();
                            seen[1] = String.valueOf(signals.capture());
                            signals.flipStageLayer();
                            signals.flipShrunk();
                            signals.setDiscHold(0);
                            signals.setRevolveHold(false);
                            signals.setObjectState(0x15, 0);
                            signals.setBossFlags(0);
                            signals.setBossStarted(0);
                            signals.setBossDoorFlag(false);
                            registry.restore(snapshot);
                            seen[2] = String.valueOf(signals.capture());
                        }
                    });
        }
        assertEquals("Snapshot[stageLayer=1, shrunk=true, discHold=1, revolveHold=true, objectStates={21=3}, bossFlags=8, "
                + "bossStarted=1, bossDoorFlag=true]", seen[1]);
        assertEquals(seen[1], seen[2], "restore puts every signal back");
    }

    private static CdLevel level() {
        return (CdLevel) GameServices.level().getCurrentLevel();
    }

}
