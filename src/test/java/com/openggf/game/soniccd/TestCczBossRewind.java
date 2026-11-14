package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectInstance;
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
 * Rewind of the Collision Chaos boss graph: the R33C-top movie (native reference, comparison only) is replayed to a
 * row where the machine tracks the player with its gear, Eggman and launcher ({@link #SNAPSHOT_ROW}); the session's
 * rewind registry captures, 120 frames run, the capture is restored and the same 120 frames run again. Both passes must produce the same boss graph (every part's class, slot, 16.16 position,
 * routine, collision and hit count) frame by frame, and the restore must rebuild the graph captured.
 */
class TestCczBossRewind {
    /** Row 1000 of R33C-top: two bombs in flight, the entry barrier and the drain spawned; 120 frames launch another. */
    private static final int SNAPSHOT_ROW = 1000;
    @TempDir
    Path tempDir;

    @Test
    void bossGraphRestoresAndReplaysForward() throws Exception {
        Path root = CczBossNativeReplay.root("R33C-top");
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        String[] atSnapshot = new String[2];
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, tempDir, new CdFixtureReplay.Fixture(CdStagePayload.R33C, root.resolve("trace"),
                    root.resolve("movie.bk2")), SNAPSHOT_ROW, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                            atSnapshot[0] = graph();
                            CompositeSnapshot snapshot = registry.capture();
                            for (int i = 0; i < 120; i++) {
                                runner.stepFrame(false, false, false, true, false);
                                first.add(graph());
                            }
                            registry.restore(snapshot);
                            atSnapshot[1] = graph();
                            for (int i = 0; i < 120; i++) {
                                runner.stepFrame(false, false, false, true, false);
                                second.add(graph());
                            }
                        }
                    });
        }
        System.out.println("CCZBOSS rewind snapshot " + atSnapshot[0]);
        for (String kind : new String[] {"CczBossMachine", "CczBossEggman", "CczBossGear", "CczBossBombLauncher",
                "CczBossBomb", "CczBossEntryBarrier"}) {
            assertTrue(atSnapshot[0].contains(kind), kind + " in the snapshot: " + atSnapshot[0]);
        }
        assertTrue(first.stream().anyMatch(g -> !g.equals(atSnapshot[0])), "the graph moves after the snapshot");
        assertEquals(atSnapshot[0], atSnapshot[1], "restore rebuilds the captured boss graph");
        assertEquals(first, second, "the restored graph replays forward identically");
    }

    private static String graph() {
        TreeMap<Integer, String> slots = new TreeMap<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (!(o instanceof CdBossPart part) || part.isDestroyed()) continue;
            slots.put(part.getSlotIndex(), String.format("%s x=%04X.%04X y=%04X.%04X rt=%02X col=%02X hits=%02X fr=%02X",
                    part.getClass().getSimpleName(), part.getX(), part.xLong() & 0xFFFF, part.getY(),
                    part.yLong() & 0xFFFF, part.routine(), part.colType(), part.getCollisionProperty(), part.mapFrame()));
        }
        return slots.toString();
    }
}
