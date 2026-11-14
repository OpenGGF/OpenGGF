package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.game.soniccd.objects.boss.ssz.SszBossEggman;
import com.openggf.game.soniccd.objects.boss.ssz.SszMetalSonic;
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
 * Rewind of the Stardust Speedway race mid-race: the R73C win movie (native reference, comparison only) is replayed
 * to a row where Eggman chases with his exhausts and laser, Metal Sonic runs and debris falls; the session's rewind
 * registry captures, 60 frames run, the capture is restored, and the same 60 frames run again. Both passes must
 * produce the same race graph (every object's class, slot, position, routine, collision, Eggman's bounds mode and
 * finish, Metal Sonic's step) frame by frame, and the restore must rebuild the graph captured at the snapshot.
 */
class TestSszBossRewind {
    @TempDir
    Path tempDir;

    @Test
    void raceGraphRestoresAndReplaysForward() throws Exception {
        Path root = CdTestMedia.nativeFile("rehost-boss-ssz/R73C-win/manifest.json").getParent();
        // The snapshot is taken mid-race (row 3300: Eggman's chase, exhausts and laser, Metal Sonic, debris). The whole
        // session is captured and restored, so the act's lamps (ObjLamp, its own check TestSszLampRewind) restore too.
        // Open (another owner's): the ring entry's dynamic children (CdRing, Ring/Main.asm:147-148) are not rebuilt by the
        // restore, so their slots (53, 55, 62, 63 here) are free in the second pass, which then allocates differently
        // (51 frames on, debris in slots 54 and 56 where the first pass had 53); the slot diagnostics below print it.
        int[] snapshotRow = {3300};
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        List<String> firstSlots = new ArrayList<>();
        List<String> secondSlots = new ArrayList<>();
        String[] slotsAtSnapshot = new String[2];
        String[] atSnapshot = new String[2];
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, tempDir, new CdFixtureReplay.Fixture(CdStagePayload.R73C, root.resolve("trace"),
                    root.resolve("movie.bk2")), snapshotRow[0], false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                            atSnapshot[0] = graph();
                            slotsAtSnapshot[0] = occupancy();
                            var active = GameServices.level().getObjectManager().getActiveObjects();
                            for (ObjectInstance o : active) {
                                if (!(o instanceof CdBossPart part)) continue;
                                for (Class<?> c = part.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                                    for (var f : c.getDeclaredFields()) {
                                        if (!ObjectInstance.class.isAssignableFrom(f.getType())) continue;
                                        f.setAccessible(true);
                                        Object target;
                                        try { target = f.get(part); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
                                        if (target == null) continue;
                                        boolean live = active.contains(target);
                                        if (!live || ((ObjectInstance) target).isDestroyed()) {
                                            System.out.println("BOSS-SSZ rewind dangling " + part.getClass().getSimpleName() + " slot "
                                                    + part.getSlotIndex() + (part.isDestroyed() ? " (destroyed)" : "") + " ." + f.getName() + " -> "
                                                    + target.getClass().getSimpleName() + " live=" + live + " destroyed="
                                                    + ((ObjectInstance) target).isDestroyed());
                                        }
                                    }
                                }
                            }
                            CompositeSnapshot snapshot = registry.capture();
                            for (int i = 0; i < 60; i++) {
                                runner.stepFrame(false, false, false, true, i % 30 < 10);
                                first.add(graph());
                                firstSlots.add(occupancy());
                            }
                            java.util.Set<Object> before = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                            before.addAll(GameServices.level().getObjectManager().getActiveObjects());
                            registry.restore(snapshot);
                            atSnapshot[1] = graph();
                            slotsAtSnapshot[1] = occupancy();
                            for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
                                if (o instanceof CdBossPart part) {
                                    StringBuilder refs = new StringBuilder();
                                    for (var f : part.getClass().getDeclaredFields()) {
                                        if (!ObjectInstance.class.isAssignableFrom(f.getType())) continue;
                                        f.setAccessible(true);
                                        try { refs.append(' ').append(f.getName()).append('=').append(f.get(part) == null ? "null" : "set"); }
                                        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
                                    }
                                    System.out.println("BOSS-SSZ rewind " + part.getClass().getSimpleName() + " slot " + part.getSlotIndex()
                                            + (before.contains(o) ? " same" : " recreated") + refs);
                                }
                            }
                            for (int i = 0; i < 60; i++) {
                                runner.stepFrame(false, false, false, true, i % 30 < 10);
                                second.add(graph());
                                secondSlots.add(occupancy());
                            }
                        }
                    });
        }
        // Diagnostics: every object's slot (any owner) at the snapshot, after the restore, and on the first frame the
        // two passes' slot occupancy differs.
        if (!slotsAtSnapshot[0].equals(slotsAtSnapshot[1])) {
            System.out.println("BOSS-SSZ rewind slots at snapshot " + slotsAtSnapshot[0] + "\nBOSS-SSZ rewind slots restored    "
                    + slotsAtSnapshot[1]);
        }
        for (int i = 0; i < Math.min(firstSlots.size(), secondSlots.size()); i++) {
            if (!firstSlots.get(i).equals(secondSlots.get(i))) {
                System.out.println("BOSS-SSZ rewind slots differ at frame " + i + "\n first  " + firstSlots.get(i)
                        + "\n second " + secondSlots.get(i) + (i > 0 ? "\n before " + firstSlots.get(i - 1)
                        + "\n before2 " + secondSlots.get(i - 1) : ""));
                break;
            }
        }
        assertTrue(atSnapshot[0].contains("SszMetalSonic") && atSnapshot[0].contains("SszBossLaser")
                && atSnapshot[0].contains("SszBossExhaust"), atSnapshot[0]);
        assertEquals(atSnapshot[0], atSnapshot[1], "restore rebuilds the captured race graph");
        assertEquals(first, second, "the restored graph replays forward identically");
    }

    /** Every live object's slot, class and ID (any owner). */
    private static String occupancy() {
        TreeMap<Integer, String> slots = new TreeMap<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || !(o instanceof com.openggf.level.objects.AbstractObjectInstance a)) continue;
            slots.merge(a.getSlotIndex(), String.format("%s:%02X@%04X", o.getClass().getSimpleName(),
                    o.getSpawn() == null ? 0 : o.getSpawn().objectId(), o.getX() & 0xFFFF), (x, y) -> x + "+" + y);
        }
        return slots.toString();
    }

    private static String graph() {
        TreeMap<Integer, String> slots = new TreeMap<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (!(o instanceof CdBossPart part) || part.isDestroyed()) continue;
            String s = String.format("%s x=%04X.%04X y=%04X.%04X rt=%02X col=%02X", part.getClass().getSimpleName(),
                    part.getX(), part.xLong() & 0xFFFF, part.getY(), part.yLong() & 0xFFFF, part.routine(), part.colType());
            if (part instanceof SszBossEggman eggman) s += " flags=" + eggman.bossFlags() + " finish=" + eggman.finish();
            if (part instanceof SszMetalSonic metal) s += " step=" + metal.scriptStep();
            slots.put(part.getSlotIndex(), s);
        }
        return slots.toString();
    }
}
