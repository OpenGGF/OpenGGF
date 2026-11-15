package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.game.soniccd.objects.boss.ppz.PpzBossEggman;
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
 * Rewind of the Palmtree Panic boss graph mid-fight: the R13C fight movie (native reference, comparison only) is
 * replayed to a row where all fourteen parts walk, the session's rewind registry captures, 60 frames run, the
 * capture is restored, and the same 60 frames run again. Both passes must produce the same boss graph (every part's
 * class, slot, position, routine and Eggman's hit count) frame by frame, and the restore must rebuild the graph
 * captured at the snapshot.
 */
class TestPpzBossRewind {
    @TempDir
    Path tempDir;

    @Test
    void bossGraphRestoresAndReplaysForward() throws Exception {
        Path root = CdTestMedia.nativeFile("rehost-boss-ppz/R13C-fight/manifest.json").getParent();
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        String[] atSnapshot = new String[2];
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, tempDir, new CdFixtureReplay.Fixture(CdStagePayload.R13C, root.resolve("trace"),
                    root.resolve("movie.bk2")), 1420, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                            atSnapshot[0] = graph();
                            CompositeSnapshot snapshot = registry.capture();
                            for (int i = 0; i < 60; i++) {
                                runner.stepFrame(false, false, false, false, i % 30 < 10);
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
                                    System.out.println("BOSS1 rewind " + part.getClass().getSimpleName() + " slot " + part.getSlotIndex()
                                            + (before.contains(o) ? " same" : " recreated") + refs);
                                }
                            }
                            for (int i = 0; i < 60; i++) {
                                runner.stepFrame(false, false, false, false, i % 30 < 10);
                                second.add(graph());
                            }
                        }
                    });
        }
        assertTrue(atSnapshot[0].contains("PpzBossBumper") && atSnapshot[0].contains("hits"), atSnapshot[0]);
        assertEquals(atSnapshot[0], atSnapshot[1], "restore rebuilds the captured boss graph");
        assertEquals(first, second, "the restored graph replays forward identically");
    }

    private static String graph() {
        TreeMap<Integer, String> slots = new TreeMap<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (!(o instanceof CdBossPart part) || part.isDestroyed()) continue;
            String s = String.format("%s x=%04X.%04X y=%04X.%04X rt=%02X col=%02X", part.getClass().getSimpleName(),
                    part.getX(), part.xLong() & 0xFFFF, part.getY(), part.yLong() & 0xFFFF, part.routine(), part.colType());
            if (part instanceof PpzBossEggman eggman) s += " hits=" + eggman.hitsLeft();
            slots.put(part.getSlotIndex(), s);
        }
        return slots.toString();
    }
}
