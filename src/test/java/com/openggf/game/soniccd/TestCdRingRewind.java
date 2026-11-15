package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.common.CdRing;
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
 * Rewind of the ring objects ($10, {@code ObjRing}, Level/_Objects/Ring/Main.asm:7-287) alone, in a stage with no boss:
 * a layout entry places the other rings of its group in the slots after its own ({@code FindNextObjSlot},
 * Ring/Main.asm:147-148), dynamic objects a restore must rebuild in the same slots. The R11A stage fixture (native
 * reference, comparison only) is replayed to row {@value #ROW}, where groups are on screen ahead of the player; the
 * session's rewind registry captures, 90 frames run holding right (the player collects rings and the spawner places
 * more), the capture is restored and the same frames run again. Both passes must give the same rings (slot, X, Y,
 * state, ring, entry X) and the same dynamic slot owners frame by frame, and the restore must rebuild the captured
 * rings, the group's placed rings (dynamic, slots after the entry) included.
 */
class TestCdRingRewind {
    static final int ROW = 300;

    @TempDir
    Path tempDir;

    @Test
    void ringGroupsRestoreInTheirSlotsAndReplayForward() throws Exception {
        Path root = CdTestMedia.nativeFile("rehost-fixtures/R11A/manifest.json").getParent();
        List<String> first = new ArrayList<>(), second = new ArrayList<>();
        String[] atSnapshot = new String[2];
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, tempDir, new CdFixtureReplay.Fixture(CdStagePayload.R11A, root.resolve("trace"),
                    root.resolve("movie.bk2")), ROW, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                            atSnapshot[0] = slots();
                            CompositeSnapshot snapshot = registry.capture();
                            for (int i = 0; i < 90; i++) {
                                runner.stepFrame(false, false, false, true, i % 40 < 8);
                                first.add(slots());
                            }
                            registry.restore(snapshot);
                            atSnapshot[1] = slots();
                            for (int i = 0; i < 90; i++) {
                                runner.stepFrame(false, false, false, true, i % 40 < 8);
                                second.add(slots());
                            }
                        }
                    });
        }
        System.out.println("CD ring rewind at snapshot " + atSnapshot[0]);
        long rings = java.util.regex.Pattern.compile("=R ").matcher(atSnapshot[0]).results().count();
        assertTrue(rings > 1, "a ring group with placed rings is live at the snapshot: " + atSnapshot[0]);
        assertEquals(atSnapshot[0], atSnapshot[1], "restore rebuilds the captured rings in their slots");
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i), second.get(i), "frame " + (i + 1) + " after the restore");
        }
    }

    /**
     * Every live object in a dynamic slot ($20-$7F): rings with position, state, ring and entry X, others by class. The
     * group list is left out: only the entry's routine 0 reads it, and it is re-resolved there. The fixed slots below
     * $20 (HUD, time icon) are not this test's: a restore drops them today (reported to their owner).
     */
    private static String slots() {
        TreeMap<Integer, String> out = new TreeMap<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (!(o instanceof com.openggf.level.objects.AbstractObjectInstance a) || a.isDestroyed()
                    || a.getSlotIndex() < 0x20) continue;
            out.put(a.getSlotIndex(), o instanceof CdRing ring
                    ? String.format("R %04X,%04X %s", ring.getX() & 0xFFFF, ring.getY() & 0xFFFF,
                            ring.traceDebugDetails().replaceAll(" group=\\d+", ""))
                    : o.getClass().getSimpleName());
        }
        return out.toString();
    }
}
