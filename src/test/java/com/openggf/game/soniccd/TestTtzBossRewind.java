package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.game.soniccd.objects.boss.ttz.TtzBossEggman;
import com.openggf.game.soniccd.objects.boss.ttz.TtzChaseMobile;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rewind of the Tidal Tempest boss graph, as {@code TestPpzBossRewind}: the session's rewind registry captures, 60
 * frames run, the capture is restored and the same 60 frames run again; both passes must give the same graph (every
 * part's class, slot, 16.16 position, routine, collision type and hit count) frame by frame, and the restore must
 * rebuild the graph captured. Mid-chase: the R43C chase movie (native reference, comparison only) replayed to a row
 * where the Egg Mobile, its pilot and the three doors are live. Mid-fight: the act 3 fixture's stage, the player set
 * down in the arena (a test setup, not a native row) and run until Eggman sweeps inside his bubble ring and fires.
 */
class TestTtzBossRewind {
    @TempDir
    Path tempDir;

    @Test
    void chaseGraphRestoresAndReplaysForward() throws Exception {
        Path root = CdTestMedia.nativeFile(TtzBossNativeCheck.ROOT + "/R43C-chase/manifest.json").getParent();
        String[] snapshot = rewind(new CdFixtureReplay.Fixture(CdStagePayload.R43C, root.resolve("trace"),
                root.resolve("movie.bk2")), Integer.getInteger("ttzboss.chaseRewindRow", 2000), null);
        assertTrue(snapshot[0].contains("TtzChaseMobile") && snapshot[0].contains("TtzChaseEggman")
                && snapshot[0].contains("TtzBossDoor"), snapshot[0]);
    }

    @Test
    void fightGraphRestoresAndReplaysForward() throws Exception {
        Path root = CdTestMedia.nativeFile("rehost-fixtures/R43C/manifest.json").getParent();
        String[] snapshot = rewind(new CdFixtureReplay.Fixture(CdStagePayload.R43C, root.resolve("trace"),
                root.resolve("movie.bk2")), 825, runner -> {
            AbstractPlayableSprite sonic = GameServices.camera().getFocusedSprite();
            NativePositionOps.writeXPosResetSubpixel(sonic, 0xA60);
            NativePositionOps.writeYPosResetSubpixel(sonic, 0x540);
            GameServices.camera().setX((short) 0x9B0);
            GameServices.camera().setY((short) 0x4C0);
            // Until Eggman sweeps (routine C) with his ring touchable and a volley in the air.
            for (int i = 0; i < 2400 && !(eggmanRoutine() == 0xC && graph().contains("TtzBossMissile")); i++) {
                runner.stepFrame(false, false, false, false, false);
            }
        });
        assertTrue(snapshot[0].contains("TtzBossEggman") && snapshot[0].contains("TtzBossBubble")
                && snapshot[0].contains("TtzBossMissile") && snapshot[0].contains("TtzEggMobile"), snapshot[0]);
    }

    /**
     * {@code bossFlags} rewinds with the stage: the R43C fight movie (native reference, comparison only) is replayed past
     * the row where native RAM shows the chase Eggman's {@code move.b #4,boss_flags} (probe/globals.csv: 0 through row
     * 3074, 4 from row 3075, player at ($8B5,$550)); a capture taken at row 3060 restores it to 0, and one taken after
     * restores 4.
     */
    @Test
    void bossFlagsRestoreWithTheStage() throws Exception {
        Path root = CdTestMedia.nativeFile(TtzBossNativeCheck.ROOT + "/R43C-fight/manifest.json").getParent();
        CompositeSnapshot[] before = new CompositeSnapshot[1];
        int[] seen = new int[4];
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, tempDir, new CdFixtureReplay.Fixture(CdStagePayload.R43C, root.resolve("trace"),
                    root.resolve("movie.bk2")), 3100, false, -1, -1, new CdFixtureReplay.Hooks() {
                @Override
                public void row(int row, AbstractPlayableSprite sonic) {
                    if (row == 3060) {
                        seen[0] = bossFlags();
                        before[0] = SessionManager.getCurrentGameplayMode().getRewindRegistry().capture();
                    }
                }

                @Override
                public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                    var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                    seen[1] = bossFlags();
                    CompositeSnapshot after = registry.capture();
                    registry.restore(before[0]);
                    seen[2] = bossFlags();
                    registry.restore(after);
                    seen[3] = bossFlags();
                }
            });
        }
        assertEquals(List.of(0, 4, 0, 4), List.of(seen[0], seen[1], seen[2], seen[3]),
                "bossFlags at row 3060, at row 3100, restored to row 3060, restored to row 3100");
    }

    private static int bossFlags() {
        return GameServices.level().getCurrentLevel() instanceof com.openggf.game.soniccd.level.CdLevel level
                ? level.flags().bossFlags : -1;
    }

    /** Replays to {@code row}, runs {@code setup}, then captures, runs, restores and runs again. */
    private String[] rewind(CdFixtureReplay.Fixture fixture, int row, Setup setup) throws Exception {
        List<String> first = new ArrayList<>(), second = new ArrayList<>();
        String[] atSnapshot = new String[2];
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, tempDir, fixture, row, false, -1, -1, new CdFixtureReplay.Hooks() {
                @Override
                public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                    if (setup != null) setup.run(runner);
                    var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                    atSnapshot[0] = graph();
                    CompositeSnapshot snapshot = registry.capture();
                    for (int i = 0; i < 60; i++) {
                        runner.stepFrame(false, false, false, false, i % 30 < 10);
                        first.add(graph());
                    }
                    registry.restore(snapshot);
                    atSnapshot[1] = graph();
                    for (int i = 0; i < 60; i++) {
                        runner.stepFrame(false, false, false, false, i % 30 < 10);
                        second.add(graph());
                    }
                }
            });
        }
        System.out.println("TTZBOSS rewind at snapshot " + atSnapshot[0]);
        assertEquals(atSnapshot[0], atSnapshot[1], "restore rebuilds the captured boss graph");
        assertEquals(first, second, "the restored graph replays forward identically");
        return atSnapshot;
    }

    private interface Setup {
        void run(HeadlessTestRunner runner);
    }

    private static int eggmanRoutine() {
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o instanceof TtzBossEggman e && !e.isDestroyed()) return e.routine();
        }
        return -1;
    }

    private static String graph() {
        TreeMap<Integer, String> slots = new TreeMap<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (!(o instanceof CdBossPart part) || part.isDestroyed()) continue;
            String s = String.format("%s x=%04X.%04X y=%04X.%04X rt=%02X col=%02X", part.getClass().getSimpleName(),
                    part.getX(), part.xLong() & 0xFFFF, part.getY(), part.yLong() & 0xFFFF, part.routine(), part.colType());
            if (part instanceof TtzBossEggman eggman) s += " hits=" + eggman.hitsLeft();
            if (part instanceof TtzChaseMobile mobile) s += " hits=" + mobile.hitsLeft();
            slots.put(part.getSlotIndex(), s);
        }
        return slots.toString();
    }
}
