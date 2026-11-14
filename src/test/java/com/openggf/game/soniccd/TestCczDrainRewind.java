package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
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
 * Rewind of the Collision Chaos act 3 drain alone ({@code CczDrain}: the layout's switch, the block it allocates and its
 * lights): R33C-top (native reference, comparison only) is replayed to row 1000, where the switch and its block exist;
 * the session's rewind registry captures, 120 frames run, the capture is restored and the same frames run again. The
 * restore must rebuild the drain (class, slot, position, routine, frame and the block's link to its switch) and replay
 * forward identically.
 */
class TestCczDrainRewind {
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
        System.out.println("CCZDRAIN rewind snapshot " + atSnapshot[0]);
        assertTrue(atSnapshot[0].contains("Switch") && atSnapshot[0].contains("Block switch=set"), atSnapshot[0]);
        assertEquals(atSnapshot[0], atSnapshot[1], "restore rebuilds the captured boss graph");
        assertEquals(first, second, "the restored graph replays forward identically");
    }

    private static String graph() {
        TreeMap<Integer, String> slots = new TreeMap<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || !o.getClass().getName().contains("CczDrain")) continue;
            var a = (com.openggf.level.objects.AbstractObjectInstance) o;
            String link = "";
            try {
                if (!(o instanceof com.openggf.game.soniccd.objects.ccz.CczDrain.Switch)) {
                    var f = o.getClass().getDeclaredField("drainSwitch");
                    f.setAccessible(true);
                    link = f.get(o) == null ? " switch=null" : " switch=set";
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            slots.put(a.getSlotIndex(), String.format("%s%s x=%04X y=%04X rt=%d fr=%d", o.getClass().getSimpleName(),
                    link, o.getX(), o.getY(), field(o, "routine"), field(o, "mapFrame")));
        }
        return slots.toString();
    }

    private static int field(Object o, String name) {
        try {
            var f = com.openggf.game.soniccd.objects.ccz.CczObject.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.getInt(o);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
