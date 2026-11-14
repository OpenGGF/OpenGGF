package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.game.soniccd.objects.boss.ttz.TtzBossEggman;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Tidal Tempest boss's own timeline against native, where the player cannot follow it: the fight is under water
 * and the route there passes the stage's vertical wrap, neither of which the engine's player hosts yet. From the frame
 * the player's X reaches $A50 (Eggman's routine 6) the boss runs on its own timers until the player touches a bubble
 * or Eggman, so the native movies {@code R43C-idle}/{@code R43D-idle} (native/rehost-boss-ttz; comparison only) bring
 * the player into the arena and leave it standing clear of the ring. The engine plays the act 3 fixture's stage, sets
 * the player down in the arena (a test setup, not a native row; rings so a missile cannot end the run) and idles.
 * Both runs are aligned on the frame Eggman enters routine 6; from there, frame by frame, the n-th Eggman, Egg Mobile,
 * missile and bubble to appear (by ID, in appearance order and slot order) must equal native RAM in X, Y, routine and
 * collision type, where the part runs on the same side of Eggman in both runs (a part in a lower slot runs before him
 * and reads his state a frame earlier; which slots are free is the allocator's, and the parts on the other side are
 * listed, not compared). A bubble's sway while it rises and its orbit before Eggman spreads the ring (routines 2 and 4) come
 * from {@code Random}, whose state follows the whole level's history, so there only its routine and, rising, its Y
 * are compared; from routine 6 on its place is fixed by its index and Eggman's.
 */
class TestTtzBossFightTimeline {
    private static final int FRAMES = Integer.getInteger("ttzboss.timelineFrames", 1400);
    /** The native player's Y on the arena floor (R43C-idle probe, rows after the fall). */
    private static final int ARENA_FLOOR = 0x5AC;

    @TempDir
    Path tempDir;

    @Test
    void r43cBossTimelineMatchesNative() throws Exception {
        check("R43C-idle", CdStagePayload.R43C);
    }

    @Test
    void r43dBossTimelineMatchesNative() throws Exception {
        check("R43D-idle", CdStagePayload.R43D);
    }

    private void check(String movie, CdStagePayload payload) throws Exception {
        Path root = CdTestMedia.nativeFile(TtzBossNativeCheck.ROOT + "/" + movie + "/manifest.json").getParent();
        List<Map<String, String>> nat = nativeTimeline(root.resolve("probe/objects.csv"), root.resolve("probe/globals.csv"));
        List<Map<String, String>> eng = engineTimeline(payload);
        List<String> mismatches = new ArrayList<>();
        Map<String, Integer> perId = new TreeMap<>(), skippedPerId = new TreeMap<>();
        java.util.Set<String> skipped = new java.util.TreeSet<>();
        int frames = Math.min(Math.min(nat.size(), eng.size()), FRAMES);
        int compared = 0;
        for (int k = 0; k < frames; k++) {
            Map<String, String> n = nat.get(k), e = eng.get(k);
            String nEgg = n.get("4C#1"), eEgg = e.get("4C#1");
            for (var entry : n.entrySet()) {
                String key = entry.getKey(), want = entry.getValue(), got = e.get(key);
                if (!key.equals("4C#1") && got != null && nEgg != null && eEgg != null
                        && (slot(want) < slot(nEgg)) != (slot(got) < slot(eEgg))) {
                    // The part runs before Eggman in one run and after him in the other: it reads his state a frame
                    // apart (the allocator's free slots, not the boss's code). Reported, not compared.
                    if (skipped.add(key)) skippedPerId.merge(key.substring(0, 2), 1, Integer::sum);
                }
                if (skipped.contains(key)) continue;
                compared++;
                if (!project(key, want).equals(project(key, got))) {
                    mismatches.add("frame +" + k + " " + key + " native " + want + " engine " + got);
                    perId.merge(key.substring(0, 2), 1, Integer::sum);
                }
            }
            for (String key : e.keySet()) {
                if (!n.containsKey(key) && !skipped.contains(key)) {
                    mismatches.add("frame +" + k + " " + key + " native none engine " + e.get(key));
                    perId.merge(key.substring(0, 2), 1, Integer::sum);
                }
            }
        }
        System.out.printf("TTZBOSS timeline %s: %d frames from routine 6 (native %d, engine %d), %d slot values compared, "
                + "%d mismatches %s; parts in the other order relative to Eggman, not compared: %s %s%n", movie, frames,
                nat.size(), eng.size(), compared, mismatches.size(), perId, skippedPerId, skipped);
        mismatches.stream().limit(25).forEach(m -> System.out.println("TTZBOSS timeline " + movie + " " + m));
        assertTrue(frames >= FRAMES, movie + ": " + frames + " aligned frames, fewer than " + FRAMES);
        assertTrue(mismatches.isEmpty(), () -> movie + ": " + mismatches.size() + " mismatches, first " + mismatches.getFirst());
    }

    /** The slot carried as the value's last field. */
    private static int slot(String value) {
        String[] v = value.split(" ");
        return Integer.parseInt(v[v.length - 1]);
    }

    /** What is compared of one object: "rt col" always, X and Y outside the bubbles' random phase. */
    private static String project(String key, String value) {
        if (value == null) return "none";
        String[] v = value.split(" ");          // x y rt col slot
        if (key.startsWith("4F#")) {
            int rt = Integer.parseInt(v[2], 16);
            if (rt == 2) return "y " + v[1] + " rt " + v[2] + " col " + v[3];
            if (rt == 4) return "rt " + v[2] + " col " + v[3];
        }
        return v[0] + " " + v[1] + " " + v[2] + " " + v[3];
    }

    /**
     * Per level frame from Eggman's routine 6: "ID#n" (n-th appearance of the ID) to "x y rt col". A lag frame (the
     * probe's {@code levelFrames} did not advance) ran no object code and is skipped, as the engine has none.
     */
    private static List<Map<String, String>> nativeTimeline(Path csv, Path globalsCsv) throws Exception {
        java.util.Set<Integer> logicFrames = new java.util.HashSet<>();
        List<String> globals = Files.readAllLines(globalsCsv);
        String previous = null;
        for (String line : globals.subList(1, globals.size())) {
            String[] g = line.split(",");
            if (previous != null && !g[2].equals(previous)) logicFrames.add(Integer.parseInt(g[0]));
            previous = g[2];
        }
        List<String> lines = Files.readAllLines(csv);
        TreeMap<Integer, List<String[]>> byFrame = new TreeMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            byFrame.computeIfAbsent(Integer.parseInt(c[0]), k -> new ArrayList<>()).add(c);
        }
        Integer start = null;
        for (var e : byFrame.entrySet()) {
            for (String[] c : e.getValue()) if ("4C".equals(c[2]) && "6".equals(c[4])) start = start == null ? e.getKey() : start;
            if (start != null) break;
        }
        assertTrue(start != null, "native Eggman never reaches routine 6");
        Map<String, String> ordinalBySlot = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        Map<Integer, String> slotId = new HashMap<>();
        List<Map<String, String>> out = new ArrayList<>();
        for (int frame = start; byFrame.containsKey(frame) || frame < start + FRAMES; frame++) {
            if (frame != start && !logicFrames.contains(frame)) continue;
            Map<String, String> row = new TreeMap<>();
            for (String[] c : byFrame.getOrDefault(frame, List.of())) {
                String id = c[2];
                if (!id.equals("4C") && !id.equals("4D") && !id.equals("4E") && !id.equals("4F")) continue;
                String slotKey = c[1];
                if (!id.equals(slotId.get(Integer.parseInt(c[1]))) || !ordinalBySlot.containsKey(slotKey)) {
                    int n = counts.merge(id, 1, Integer::sum);
                    ordinalBySlot.put(slotKey, id + "#" + n);
                }
                slotId.put(Integer.parseInt(c[1]), id);
                row.put(ordinalBySlot.get(slotKey), String.format("%04X %04X %02X %s %d", Integer.parseInt(c[6]),
                        Integer.parseInt(c[7]), Integer.parseInt(c[4]), c[8], Integer.parseInt(c[1])));
            }
            // A slot that empties forgets its object: the next object there is a new appearance.
            List<Integer> live = byFrame.getOrDefault(frame, List.of()).stream().map(c -> Integer.parseInt(c[1])).toList();
            slotId.keySet().removeIf(s -> !live.contains(s));
            ordinalBySlot.keySet().removeIf(s -> !live.contains(Integer.parseInt(s)));
            out.add(row);
            if (frame > byFrame.lastKey()) break;
        }
        return out;
    }

    /** The engine's run: the act 3 stage, the player set down in the arena, then idle frames. */
    private List<Map<String, String>> engineTimeline(CdStagePayload payload) throws Exception {
        Path root = CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/manifest.json").getParent();
        List<Map<String, String>> out = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, tempDir, new CdFixtureReplay.Fixture(payload, root.resolve("trace"),
                    root.resolve("movie.bk2")), 825, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                            // The arena's floor, left of the trigger first so Eggman loads and waits in routine 2 as on the
                            // native route, then past X $A50.
                            sonic.setRingCount(50);
                            NativePositionOps.writeXPosResetSubpixel(sonic, 0x9F0);
                            NativePositionOps.writeYPosResetSubpixel(sonic, ARENA_FLOOR);
                            GameServices.camera().setX((short) 0x950);
                            GameServices.camera().setY((short) 0x4F0);
                            // The bottom boundary the chase leaves (R43Events, boss_flags set by the chase Eggman).
                            GameServices.camera().setMaxY((short) 0x4F0);
                            for (int i = 0; i < 30; i++) runner.stepFrame(false, false, false, false, false);
                            NativePositionOps.writeXPosResetSubpixel(sonic, Integer.getInteger("ttzboss.arenaX", 0xAAF));
                            NativePositionOps.writeYPosResetSubpixel(sonic, ARENA_FLOOR);
                            Map<ObjectInstance, String> names = new IdentityHashMap<>();
                            Map<Integer, Integer> counts = new HashMap<>();
                            boolean started = false;
                            for (int i = 0; i < FRAMES + 2000 && out.size() < FRAMES; i++) {
                                runner.stepFrame(false, false, false, false, false);
                                Map<String, String> row = new TreeMap<>();
                                List<CdBossPart> parts = new ArrayList<>();
                                for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
                                    if (o instanceof CdBossPart p && !p.isDestroyed()) parts.add(p);
                                }
                                parts.sort(java.util.Comparator.comparingInt(CdBossPart::getSlotIndex));
                                for (CdBossPart p : parts) {
                                    int id = p.getSpawn().objectId();
                                    if (id < 0x4C || id > 0x4F) continue;
                                    String name = names.computeIfAbsent(p, k -> String.format("%02X#%d", id,
                                            counts.merge(id, 1, Integer::sum)));
                                    if (p instanceof TtzBossEggman e && e.routine() == 6) started = true;
                                    row.put(name, String.format("%04X %04X %02X %02X %d", p.getX() & 0xFFFF, p.getY() & 0xFFFF,
                                            p.routine(), p.colType(), p.getSlotIndex()));
                                }
                                if (started) out.add(row);
                            }
                        }
                    });
        }
        return out;
    }

}
