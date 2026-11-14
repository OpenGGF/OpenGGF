package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.ssz.ObjLamp;
import com.openggf.level.objects.AbstractObjectInstance;
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
 * Rewind of Stardust Speedway act 3's lamp ($36, {@code LampObject}, objects/r7/lamp.asm:3-116) alone: the head spawns
 * its pole with {@code SpawnObjectAfter} and the two link each other ({@code obj.var_2e}); the pole is a dynamic object
 * whose link a restore must rebuild. The R73C win movie (native reference, comparison only) is replayed to row 1500,
 * where the camera is moving right; the session's rewind registry captures, 60 frames run holding right, the capture
 * is restored, and the same 60 frames run again. Both passes must give the same lamps (slot, subtype, X, Y) frame by
 * frame, the restore must rebuild the captured lamps, and each pole must stand at its head's X and head Y + $7C
 * (routine 4, :111-116) after the restore, which it can only do through a rebuilt link.
 */
class TestSszLampRewind {
    @TempDir
    Path tempDir;

    @Test
    void lampAndPoleRestoreAndReplayForward() throws Exception {
        Path root = CdTestMedia.nativeFile("rehost-boss-ssz/R73C-win/manifest.json").getParent();
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        String[] atSnapshot = new String[2];
        List<String> poles = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, tempDir, new CdFixtureReplay.Fixture(CdStagePayload.R73C, root.resolve("trace"),
                    root.resolve("movie.bk2")), 1500, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                            atSnapshot[0] = lamps();
                            CompositeSnapshot snapshot = registry.capture();
                            for (int i = 0; i < 60; i++) {
                                runner.stepFrame(false, false, false, true, false);
                                first.add(lamps());
                            }
                            registry.restore(snapshot);
                            atSnapshot[1] = lamps();
                            for (int i = 0; i < 60; i++) {
                                runner.stepFrame(false, false, false, true, false);
                                second.add(lamps());
                            }
                            poles.addAll(poleChecks());
                        }
                    });
        }
        System.out.println("SSZ lamp rewind at snapshot " + atSnapshot[0] + "; poles " + poles);
        assertTrue(atSnapshot[0].contains("sub=1"), "a lamp pole is live at the snapshot: " + atSnapshot[0]);
        assertEquals(atSnapshot[0], atSnapshot[1], "restore rebuilds the captured lamps");
        assertEquals(first, second, "the restored lamps replay forward identically");
        assertTrue(!first.getFirst().equals(first.getLast()), "the lamps moved in the window: " + first.getFirst());
        assertTrue(!poles.isEmpty() && poles.stream().allMatch(p -> p.endsWith("ok")), "poles follow their heads: " + poles);
    }

    private static String lamps() {
        TreeMap<Integer, String> slots = new TreeMap<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (!(o instanceof ObjLamp lamp) || lamp.isDestroyed()) continue;
            slots.put(lamp.getSlotIndex(), String.format("sub=%d x=%04X y=%04X", lamp.getSpawn().subtype(),
                    lamp.getX() & 0xFFFF, lamp.getY() & 0xFFFF));
        }
        return slots.toString();
    }

    /** Each pole against its linked head ({@code partner}): X equal and Y = head Y + $7C, or "no partner". */
    private static List<String> poleChecks() {
        List<String> out = new ArrayList<>();
        var active = GameServices.level().getObjectManager().getActiveObjects();
        for (ObjectInstance o : active) {
            if (!(o instanceof ObjLamp pole) || pole.isDestroyed() || pole.getSpawn().subtype() != 1) continue;
            Object head;
            try {
                var field = ObjLamp.class.getDeclaredField("partner");
                field.setAccessible(true);
                head = field.get(pole);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            if (!(head instanceof ObjLamp h) || !active.contains(h)) {
                out.add("slot " + pole.getSlotIndex() + " no partner");
                continue;
            }
            int headX = readInt(h, "headX"), headY = readInt(h, "headY");
            boolean ok = (pole.getX() & 0xFFFF) == headX && (pole.getY() & 0xFFFF) == ((headY + 0x7C) & 0xFFFF);
            out.add("slot " + pole.getSlotIndex() + " head slot " + ((AbstractObjectInstance) h).getSlotIndex()
                    + (ok ? " ok" : " off"));
        }
        return out;
    }

    private static int readInt(ObjLamp lamp, String name) {
        try {
            var field = ObjLamp.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(lamp);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
