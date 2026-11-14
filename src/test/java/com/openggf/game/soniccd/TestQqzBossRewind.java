package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.game.soniccd.objects.boss.qqz.QqzBossBelt;
import com.openggf.game.soniccd.objects.boss.qqz.QqzBossBooth;
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
 * Rewind of the Quartz Quadrant boss graph mid-fight: the R53C fight movie (native reference, comparison only) is
 * replayed to a row where the dropper carries a bomb over a running belt, the session's rewind registry captures, 60
 * frames run (holding Right, with a jump), the capture is restored, and the same 60 frames run again. Both passes must
 * produce the same boss graph (every part's class, slot, 16.16 position, routine, collision type, the booth's wear and
 * the belt's speed and travel) frame by frame, and the restore must rebuild the graph captured at the snapshot.
 */
class TestQqzBossRewind {
    /** A row of R53C-fight where the dropper carries a large bomb (set from the recorded movie). */
    private static final int REWIND_ROW = 1435;

    @TempDir
    Path tempDir;

    @Test
    void bossGraphRestoresAndReplaysForward() throws Exception {
        Path root = CdTestMedia.nativeFile("rehost-boss-qqz/R53C-fight/manifest.json").getParent();
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        String[] atSnapshot = new String[2];
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, tempDir, new CdFixtureReplay.Fixture(CdStagePayload.R53C, root.resolve("trace"),
                    root.resolve("movie.bk2")), REWIND_ROW, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                            atSnapshot[0] = graph();
                            CompositeSnapshot snapshot = registry.capture();
                            for (int i = 0; i < 60; i++) {
                                runner.stepFrame(false, false, false, true, i % 30 < 6);
                                first.add(graph());
                            }
                            java.util.Set<Object> before = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                            before.addAll(GameServices.level().getObjectManager().getActiveObjects());
                            registry.restore(snapshot);
                            atSnapshot[1] = graph();
                            for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
                                if (o instanceof CdBossPart part) {
                                    StringBuilder refs = new StringBuilder();
                                    for (var f : part.getClass().getDeclaredFields()) {
                                        if (!ObjectInstance.class.isAssignableFrom(f.getType())) continue;
                                        f.setAccessible(true);
                                        try { refs.append(' ').append(f.getName()).append('=').append(f.get(part) == null ? "null" : "set"); }
                                        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
                                    }
                                    System.out.println("QQZBOSS rewind " + part.getClass().getSimpleName() + " slot " + part.getSlotIndex()
                                            + (before.contains(o) ? " same" : " recreated") + refs);
                                }
                            }
                            for (int i = 0; i < 60; i++) {
                                runner.stepFrame(false, false, false, true, i % 30 < 6);
                                second.add(graph());
                            }
                        }
                    });
        }
        assertTrue(atSnapshot[0].contains("QqzLargeBomb") && atSnapshot[0].contains("speed="), atSnapshot[0]);
        assertEquals(atSnapshot[0], atSnapshot[1], "restore rebuilds the captured boss graph");
        assertEquals(first, second, "the restored graph replays forward identically");
    }

    private static String graph() {
        TreeMap<Integer, String> slots = new TreeMap<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (!(o instanceof CdBossPart part) || part.isDestroyed()) continue;
            String s = String.format("%s x=%04X.%04X y=%04X.%04X rt=%02X col=%02X", part.getClass().getSimpleName(),
                    part.getX(), part.xLong() & 0xFFFF, part.getY(), part.yLong() & 0xFFFF, part.routine(), part.colType());
            if (part instanceof QqzBossBooth booth) s += " wear=" + booth.wear();
            if (part instanceof QqzBossBelt belt) s += " speed=" + belt.speed() + " travel=" + belt.travel();
            slots.put(part.getSlotIndex(), s);
        }
        return slots.toString();
    }
}
