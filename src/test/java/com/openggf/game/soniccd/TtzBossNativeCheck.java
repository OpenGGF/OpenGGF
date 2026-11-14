package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.game.soniccd.objects.boss.ttz.TtzBossBubble;
import com.openggf.game.soniccd.objects.boss.ttz.TtzBossEggman;
import com.openggf.game.soniccd.objects.boss.ttz.TtzChaseMobile;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One Tidal Tempest boss movie ({@code native/rehost-boss-ttz/<movie>/}: BK2, v5 trace and the boss probe's post-frame
 * RAM, comparison only) replayed through the engine, by {@link CdBossNativeReplay}'s pattern: per trace row (row N =
 * probe frame {@code bk2_frame_offset + N + 1}) the player and camera rows, the boss appearances (row, ID) against the
 * aux {@code object_appeared} events, every boss slot's ID, X, Y, routine and collision type, the chase Egg Mobile's
 * and Eggman's {@code collide_status} (their hit counts), and the camera's left, right and bottom bounds. Everything
 * is recorded with its row so a test can bound it by the replay's first player/camera divergence; the native rows
 * where the chase ends (the Egg Mobile's slot empties) and where the player first goes under water (probe
 * {@code p_flags} bit 6) are returned too.
 */
final class TtzBossNativeCheck {
    static final String ROOT = "rehost-boss-ttz";
    /** The door, the chase pair, Eggman, the Egg Mobile, missiles, bubbles and the capsule. */
    static final Set<Integer> IDS = Set.of(0x15, 0x47, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F);
    private static final Pattern APPEARED = Pattern.compile(
            "\"frame\":(\\d+),\"vfc\":\\d+,\"event\":\"object_appeared\",\"slot\":(\\d+),\"object_type\":\"0x([0-9A-F]+)\"");

    /** A row-tagged finding. */
    record Note(int row, String text) {
        @Override public String toString() { return "row " + row + " " + text; }
    }

    record Result(String movie, int rowsCompared, int firstError, String fields, List<Note> mismatches,
                  List<String> nativeAppear, List<String> engineAppear, List<Note> bounds, List<Note> slotNotes,
                  int chaseHitRows, int fightHitRows, int chaseEndRow, int fleeRow, int firstUnderwaterRow,
                  int firstWrapRow, int lastRow, int defeatRow, int firstPopEndRow, List<Note> drown) {
        /**
         * The first row an unhosted feature (the water) shows in the native player; or -1. The stage's vertical wrap at
         * $800 is hosted ({@code ScrollFgY}, r4/scroll_3c.asm:497-535) and no longer bounds the comparison.
         */
        int firstUnhostedRow() {
            return firstUnderwaterRow;
        }

        /** Findings before {@code row} (all of them for -1). */
        List<Note> beforeRow(List<Note> notes, int row) {
            return notes.stream().filter(n -> row < 0 || n.row() < row).toList();
        }

        /** Findings before the first player/camera divergence (all of them on a clean replay). */
        List<Note> before(List<Note> notes) {
            return notes.stream().filter(n -> firstError < 0 || n.row() < firstError).toList();
        }

        /** Appearances (row:ID:count) before the first divergence. */
        TreeSet<String> appearBefore(List<String> appear) {
            TreeSet<String> out = new TreeSet<>(java.util.Comparator.comparingInt((String k) -> Integer.parseInt(k.split(":")[0]))
                    .thenComparing(k -> k));
            for (String a : appear) if (firstError < 0 || Integer.parseInt(a.split(":")[0]) < firstError) out.add(a);
            return out;
        }
    }

    private TtzBossNativeCheck() { }

    static Result run(Path tempDir, String movie, CdStagePayload payload) throws Exception {
        Path root = CdTestMedia.nativeFile(ROOT + "/" + movie + "/manifest.json").getParent();
        Path traceDir = root.resolve("trace");
        int offset = Integer.parseInt(json(Files.readString(traceDir.resolve("metadata.json")), "bk2_frame_offset"));
        Map<Integer, Map<Integer, String[]>> nativeSlots = csvBySlot(root.resolve("probe/objects.csv"));
        Map<Integer, String[]> nativeGlobals = csvByFrame(root.resolve("probe/globals.csv"));
        TreeSet<String> nativeAppear = nativeAppearances(traceDir.resolve("aux_state.jsonl"));
        List<String> engineAppear = new ArrayList<>();
        Map<Integer, CdBossPart> byNativeSlot = new HashMap<>();
        Map<ObjectInstance, Boolean> seen = new IdentityHashMap<>();
        List<Note> mismatches = new ArrayList<>(), bounds = new ArrayList<>(), slotNotes = new ArrayList<>();
        List<Note> drown = new ArrayList<>();
        int[] hitRows = {0, 0};
        Set<CdBossPart> otherOrder = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Integer> otherOrderSlots = new java.util.HashSet<>();
        int[] lastRow = {-1};

        CdFixtureReplay.Outcome outcome;
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            outcome = CdFixtureReplay.replay(disc, tempDir, new CdFixtureReplay.Fixture(payload, traceDir,
                    root.resolve("movie.bk2")), Integer.MAX_VALUE, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void row(int row, AbstractPlayableSprite sonic) {
                            lastRow[0] = row;
                            int frame = offset + row + 1;
                            List<CdBossPart> appeared = new ArrayList<>();
                            Set<ObjectInstance> live = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
                            for (ObjectInstance object : GameServices.level().getObjectManager().getActiveObjects()) {
                                if (!(object instanceof CdBossPart part) || part.isDestroyed()) continue;
                                live.add(object);
                                if (seen.put(object, Boolean.TRUE) == null) {
                                    engineAppear.add(String.format("%d:%02X", row, part.getSpawn().objectId()));
                                    appeared.add(part);
                                }
                            }
                            pair(row, nativeAppear, appeared, byNativeSlot, slotNotes);
                            // An object the spawner unloaded leaves the active set without being destroyed.
                            byNativeSlot.values().removeIf(part -> !live.contains(part));
                            compareSlots(row, nativeSlots.getOrDefault(frame, Map.of()), byNativeSlot, mismatches, hitRows,
                                    otherOrder, otherOrderSlots, slotNotes);
                            compareBounds(row, nativeGlobals.get(frame), bounds);
                            compareDrown(row, nativeGlobals.get(frame), sonic, drown);
                        }
                    });
        }
        return new Result(movie, outcome.rowsCompared(), outcome.firstErrorRow(), String.valueOf(outcome.fields()),
                mismatches, counted(nativeAppear), counted(engineAppear), bounds, slotNotes,
                hitRows[0], hitRows[1], chaseEndRow(nativeSlots, offset), fleeRow(nativeSlots, offset),
                firstUnderwaterRow(nativeGlobals, offset), firstWrapRow(nativeGlobals, offset), lastRow[0],
                firstRoutineRow(nativeSlots, offset, TtzBossEggman.ID, 0x12),
                firstRoutineRow(nativeSlots, offset, TtzBossBubble.ID, 0xE), drown);
    }

    /** row:ID entries (native ones carry the slot: row:slot:ID) counted per row and ID: row:ID:count. */
    private static List<String> counted(java.util.Collection<String> appear) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String a : appear) {
            String[] c = a.split(":");
            counts.merge(c[0] + ":" + c[c.length - 1], 1, Integer::sum);
        }
        List<String> out = new ArrayList<>();
        counts.forEach((k, v) -> out.add(k + ":" + v));
        return out;
    }

    /** Pairs this row's native appearances with the engine's, per ID in slot order (as CdBossNativeReplay). */
    private static void pair(int row, TreeSet<String> nativeAppear, List<CdBossPart> appeared,
                             Map<Integer, CdBossPart> byNativeSlot, List<Note> notes) {
        Map<Integer, List<Integer>> nativeById = new HashMap<>();
        for (String a : nativeAppear) {
            String[] c = a.split(":");
            if (Integer.parseInt(c[0]) == row) {
                nativeById.computeIfAbsent(Integer.parseInt(c[2], 16), k -> new ArrayList<>()).add(Integer.parseInt(c[1]));
            }
        }
        Map<Integer, List<CdBossPart>> engineById = new HashMap<>();
        for (CdBossPart part : appeared) engineById.computeIfAbsent(part.getSpawn().objectId(), k -> new ArrayList<>()).add(part);
        for (var e : nativeById.entrySet()) {
            List<Integer> slots = e.getValue();
            slots.sort(null);
            List<CdBossPart> parts = new ArrayList<>(engineById.getOrDefault(e.getKey(), List.of()));
            parts.sort(java.util.Comparator.comparingInt(CdBossPart::getSlotIndex));
            for (int i = 0; i < slots.size() && i < parts.size(); i++) {
                byNativeSlot.put(slots.get(i), parts.get(i));
                if (slots.get(i) != parts.get(i).getSlotIndex()) {
                    notes.add(new Note(row, String.format("id %02X native slot %d engine slot %d", e.getKey(), slots.get(i),
                            parts.get(i).getSlotIndex())));
                }
            }
        }
    }

    /** objects.csv: frame,slot,id,sprflags,routine,routine2,x,y,col_type,col_status,... */
    private static void compareSlots(int row, Map<Integer, String[]> nat, Map<Integer, CdBossPart> eng, List<Note> out,
                                     int[] hitRows, Set<CdBossPart> otherOrder, Set<Integer> otherOrderSlots,
                                     List<Note> notes) {
        otherOrderSlots.removeIf(slot -> !nat.containsKey(slot));      // the native object left its slot
        int nativeEggman = -1;
        for (var e : nat.entrySet()) if (Integer.parseInt(e.getValue()[2], 16) == TtzBossEggman.ID) nativeEggman = e.getKey();
        CdBossPart engineEggman = nativeEggman < 0 ? null : eng.get(nativeEggman);
        for (var e : nat.entrySet()) {
            String[] c = e.getValue();
            int id = Integer.parseInt(c[2], 16);
            if (!IDS.contains(id)) continue;
            CdBossPart part = eng.get(e.getKey());
            // Eggman's Egg Mobile, missiles and bubbles read his state: one in a lower slot than his runs before him and
            // reads it a frame earlier. Where the free slots put a part on the other side of him than native's (the
            // shared spawner's slot order, not the boss's code), the part is noted and not compared.
            if (part != null && engineEggman != null && id >= 0x4D && id <= 0x4F && !otherOrder.contains(part)
                    && (e.getKey() < nativeEggman) != (part.getSlotIndex() < engineEggman.getSlotIndex())) {
                otherOrder.add(part);
                otherOrderSlots.add(e.getKey());
                notes.add(new Note(row, String.format("id %02X native slot %d engine slot %d on the other side of Eggman "
                        + "(native %d, engine %d): not compared", id, e.getKey(), part.getSlotIndex(), nativeEggman,
                        engineEggman.getSlotIndex())));
            }
            if (part != null ? otherOrder.contains(part) : otherOrderSlots.contains(e.getKey())) continue;
            String want = String.format("id %02X x %04X y %04X rt %02X col %s", id, Integer.parseInt(c[6]),
                    Integer.parseInt(c[7]), Integer.parseInt(c[4]), c[8]);
            if (part == null || part.isDestroyed()) {
                out.add(new Note(row, "slot " + e.getKey() + " native " + want + " engine none"));
                continue;
            }
            String got = String.format("id %02X x %04X y %04X rt %02X col %02X", part.getSpawn().objectId(),
                    part.getX() & 0xFFFF, part.getY() & 0xFFFF, part.routine(), part.colType());
            if (part instanceof TtzChaseMobile mobile) {
                want += " hits " + c[9];
                got += " hits " + mobile.hitsLeft();
                hitRows[0]++;
            } else if (part instanceof TtzBossEggman eggman) {
                want += " hits " + c[9];
                got += " hits " + eggman.hitsLeft();
                hitRows[1]++;
            }
            if (id == TtzBossBubble.ID && (part.routine() == 2 || part.routine() == 4)) {
                // A rising bubble's sway and its first orbit come from Random (BossBubbleObject_0_Routine0's divs of Random,
                // objects/r4/boss.asm:620-650), whose state follows every Random call in the level: only its routine
                // and collision, and while rising its Y, are compared (as TestTtzBossFightTimeline).
                want = want.replaceFirst(" x [0-9A-F]{4}", "");
                got = got.replaceFirst(" x [0-9A-F]{4}", "");
                if (part.routine() == 4) {
                    want = want.replaceFirst(" y [0-9A-F]{4}", "");
                    got = got.replaceFirst(" y [0-9A-F]{4}", "");
                }
            }
            if (!want.equals(got)) out.add(new Note(row, "slot " + e.getKey() + " native " + want + " engine " + got));
        }
        for (var e : eng.entrySet()) {
            if (!nat.containsKey(e.getKey()) && !e.getValue().isDestroyed() && !otherOrder.contains(e.getValue())) {
                out.add(new Note(row, "native slot " + e.getKey() + " empty, engine "
                        + e.getValue().getClass().getSimpleName() + " live"));
            }
        }
        eng.entrySet().removeIf(e -> e.getValue().isDestroyed() && !nat.containsKey(e.getKey()));
    }

    /** globals.csv: ...,left (13), right (14), bottom (15). */
    private static void compareBounds(int row, String[] g, List<Note> out) {
        if (g == null) return;
        var camera = GameServices.camera();
        String want = String.format("L %04X R %04X B %04X", Integer.parseInt(g[13]), Integer.parseInt(g[14]),
                Integer.parseInt(g[15]));
        String got = String.format("L %04X R %04X B %04X", camera.getMinX() & 0xFFFF, camera.getMaxX() & 0xFFFF,
                camera.getMaxY() & 0xFFFF);
        if (!want.equals(got)) out.add(new Note(row, "bounds native " + want + " engine " + got));
    }

    /**
     * The player's air ({@code drown_timer}, globals.csv column 21) against {@code CdPlayerWater}: the water entry's
     * and a popped bubble's {@code PlayerResetDrown} (objects/r4/player_bubbles.asm:153-177) and the countdown.
     */
    private static void compareDrown(int row, String[] g, AbstractPlayableSprite sonic, List<Note> out) {
        if (g == null || g.length <= 21) return;
        if (!(sonic.currentGameModule().getPhysicsProvider()
                instanceof com.openggf.game.soniccd.player.SonicCdPhysicsProvider provider)) return;
        int want = Integer.parseInt(g[21]), got = provider.stage().water().drownTimer();
        if (want != got) out.add(new Note(row, "drown native " + want + " engine " + got));
    }

    private static Map<Integer, Map<Integer, String[]>> csvBySlot(Path csv) throws Exception {
        Map<Integer, Map<Integer, String[]>> out = new HashMap<>();
        List<String> lines = Files.readAllLines(csv);
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            out.computeIfAbsent(Integer.parseInt(c[0]), k -> new HashMap<>()).put(Integer.parseInt(c[1]), c);
        }
        return out;
    }

    private static Map<Integer, String[]> csvByFrame(Path csv) throws Exception {
        Map<Integer, String[]> out = new HashMap<>();
        List<String> lines = Files.readAllLines(csv);
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            out.put(Integer.parseInt(c[0]), c);
        }
        return out;
    }

    private static TreeSet<String> nativeAppearances(Path aux) throws Exception {
        TreeSet<String> out = new TreeSet<>();
        for (String line : Files.readAllLines(aux)) {
            Matcher m = APPEARED.matcher(line);
            if (!m.find()) continue;
            int id = Integer.parseInt(m.group(3), 16);
            int slot = Integer.parseInt(m.group(2));
            if (slot < 32 || !IDS.contains(id)) continue;
            out.add(String.format("%d:%d:%02X", Integer.parseInt(m.group(1)), slot, id));
        }
        return out;
    }

    /** The first row after which no native slot holds the chase Egg Mobile ($4A) again, once it existed; or -1. */
    private static int chaseEndRow(Map<Integer, Map<Integer, String[]>> slots, int offset) {
        int lastSeen = -1;
        for (var e : slots.entrySet()) {
            for (String[] c : e.getValue().values()) {
                if (Integer.parseInt(c[2], 16) == TtzChaseMobile.ID) lastSeen = Math.max(lastSeen, e.getKey());
            }
        }
        if (lastSeen < 0 || lastSeen >= maxFrame(slots)) return -1;
        return lastSeen + 1 - offset - 1;
    }

    private static int maxFrame(Map<Integer, ?> map) {
        return map.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
    }

    /**
     * The first row whose native slot holds {@code id} in {@code routine} (the probe prints routines in decimal), or -1:
     * Eggman's defeat (routine $12, EggmanObject_0_Routine12, objects/r4/boss.asm:455-494) and a popped bubble's last
     * frame (routine $E, BossBubbleObject_0_RoutineE, objects/r4/boss.asm:1054-1056).
     */
    private static int firstRoutineRow(Map<Integer, Map<Integer, String[]>> slots, int offset, int id, int routine) {
        int first = -1;
        for (var e : slots.entrySet()) {
            for (String[] c : e.getValue().values()) {
                if (Integer.parseInt(c[2], 16) == id && Integer.parseInt(c[4]) == routine
                        && (first < 0 || e.getKey() < first)) first = e.getKey();
            }
        }
        return first < 0 ? -1 : first - offset - 1;
    }

    /** The first row whose native chase Egg Mobile is in routine 6 (four hits taken), or -1. */
    private static int fleeRow(Map<Integer, Map<Integer, String[]>> slots, int offset) {
        int first = -1;
        for (var e : slots.entrySet()) {
            for (String[] c : e.getValue().values()) {
                if (Integer.parseInt(c[2], 16) == TtzChaseMobile.ID && "6".equals(c[4])
                        && (first < 0 || e.getKey() < first)) first = e.getKey();
            }
        }
        return first < 0 ? -1 : first - offset - 1;
    }

    /**
     * The first row whose native player or camera Y jumps by $400 or more from the row before: the act 3 stages'
     * vertical wrap at $800 ({@code ScrollFgY}, r4/scroll_3c.asm:497-528); or -1.
     */
    private static int firstWrapRow(Map<Integer, String[]> globals, int offset) {
        String[] prev = null;
        for (int frame = offset + 1; globals.containsKey(frame); frame++) {
            String[] g = globals.get(frame);
            if (prev != null && (Math.abs(Integer.parseInt(g[4]) - Integer.parseInt(prev[4])) >= 0x400
                    || Math.abs(Integer.parseInt(g[12]) - Integer.parseInt(prev[12])) >= 0x400)) {
                return frame - offset - 1;
            }
            prev = g;
        }
        return -1;
    }

    /** The first row whose native player has the underwater bit (globals.csv p_flags, column 7, bit 6); or -1. */
    private static int firstUnderwaterRow(Map<Integer, String[]> globals, int offset) {
        for (int frame = offset + 1; globals.containsKey(frame); frame++) {
            if ((Integer.parseInt(globals.get(frame)[7]) & 0x40) != 0) return frame - offset - 1;
        }
        return -1;
    }

    private static String json(String text, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([0-9A-Za-z]+)").matcher(text);
        if (!m.find()) throw new IllegalStateException("no " + key);
        return m.group(1);
    }
}
