package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Quartz Quadrant boss's native replay check: {@code CdBossNativeReplay} (the shared boss check) with the
 * comparison widened for a boss that has no hit count. Every boss slot's collision type is compared (not only the main
 * object's), and on every row the booth's wear ({@code var_3e}) and the belt's speed and travel ({@code var_32},
 * {@code var_34}) against the QQZ probe's extra columns ({@code v3e}, {@code v32}, {@code v34w}); {@code Spec.hits} is
 * unused. Otherwise as the shared check: a movie recorded
 * with the boss tools ({@code <movieRoot>/<movie>/}: BK2, v5 trace, and the boss probe's post-frame RAM in
 * {@code probe/globals.csv} and {@code probe/objects.csv}; comparison only) replays through the engine. Per trace row
 * (row N = BK2 row {@code bk2_frame_offset + N} = probe frame {@code bk2_frame_offset + N + 1}): player and camera rows;
 * boss, part and capsule appearances (row, ID) against the aux {@code object_appeared} events, paired per ID in slot
 * order; every boss slot's ID, X, Y and routine, the main object's collision type and hit count, against native RAM;
 * camera bounds against {@code leftBound}, {@code rightBound}, {@code bottomBound}. Player and camera must match
 * through the capsule's break; later rows are reported.
 */
final class QqzBossNativeReplay {
    /**
     * @param movieRoot          directory under {@code native/} holding the movies (e.g. {@code rehost-boss-ppz})
     * @param ids                slot IDs of the boss, its parts and the capsule
     * @param mainId             the object whose collision type and hit count are compared
     * @param fightRoutine       its routine while hits count (native {@code objects.csv} column 4)
     * @param nativeHitsColumn   the {@code objects.csv} column holding the hit count (13 = {@code v2b})
     * @param hits               the engine's hit count for the main object
     * @param capsuleId          the capsule's ID in this act 3 index
     */
    record Spec(String movieRoot, java.util.Set<Integer> ids, int mainId, int fightRoutine, int nativeHitsColumn,
                java.util.function.ToIntFunction<CdBossPart> hits, int capsuleId) { }

    private static final Pattern APPEARED = Pattern.compile(
            "\"frame\":(\\d+),\"vfc\":\\d+,\"event\":\"object_appeared\",\"slot\":(\\d+),\"object_type\":\"0x([0-9A-F]+)\"");

    private QqzBossNativeReplay() { }

    /** One movie: the replay outcome and the per-row boss comparison. */
    static void check(Path tempDir, Spec spec, String movie, CdStagePayload payload) throws Exception {
        check(tempDir, spec, movie, payload, false);
    }

    /**
     * As {@link #check(Path, Spec, String, CdStagePayload)}; with {@code wholeMovie} the player and camera must equal
     * the trace on every row, not only through the capsule's break.
     */
    static void check(Path tempDir, Spec spec, String movie, CdStagePayload payload, boolean wholeMovie)
            throws Exception {
        Path root = CdTestMedia.nativeFile(spec.movieRoot() + "/" + movie + "/manifest.json").getParent();
        Path traceDir = root.resolve("trace");
        int offset = Integer.parseInt(json(Files.readString(traceDir.resolve("metadata.json")), "bk2_frame_offset"));
        Map<Integer, Map<Integer, String[]>> nativeSlots = nativeSlots(root.resolve("probe/objects.csv"));
        Map<Integer, String[]> nativeGlobals = nativeGlobals(root.resolve("probe/globals.csv"));
        TreeSet<String> nativeAppear = nativeAppearances(traceDir.resolve("aux_state.jsonl"), spec.ids());
        TreeSet<String> engineAppear = new TreeSet<>();
        Map<Integer, CdBossPart> byNativeSlot = new HashMap<>();
        List<String> slotNotes = new ArrayList<>();
        Map<ObjectInstance, Boolean> seen = new java.util.IdentityHashMap<>();
        List<String> mismatches = new ArrayList<>();
        List<String> boundMismatches = new ArrayList<>();
        int[] lastRow = {-1};
        int[] hitsSeen = {0};
        java.util.ArrayDeque<String> recent = new java.util.ArrayDeque<>();
        List<String> recentAtError = new ArrayList<>();
        int[] firstCam = {Integer.getInteger("qqzboss.diagRow", -1)};

        CdFixtureReplay.Outcome outcome;
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            outcome = CdFixtureReplay.replay(disc, tempDir,
                    new CdFixtureReplay.Fixture(payload, traceDir, root.resolve("movie.bk2")), Integer.MAX_VALUE,
                    false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void row(int row, AbstractPlayableSprite sonic) {
                            lastRow[0] = row;
                            int frame = offset + row + 1;
                            Map<Integer, String[]> nat = nativeSlots.getOrDefault(frame, Map.of());
                            List<CdBossPart> appeared = new ArrayList<>();
                            engineSlots(seen, engineAppear, row, appeared);
                            pair(row, nativeAppear, appeared, byNativeSlot, slotNotes);
                            compareSlots(spec, row, nat, byNativeSlot, mismatches, hitsSeen);
                            compareBounds(row, nativeGlobals.get(frame), boundMismatches);
                            String[] g = nativeGlobals.get(frame);
                            var cam = GameServices.camera();
                            recent.addLast(String.format("row %d cam %04X,%04X maxY %04X tgt %04X | native cam %s,%s bottom %s dest %s yc %s bf %s",
                                    row, cam.getX() & 0xFFFF, cam.getY() & 0xFFFF, cam.getMaxY() & 0xFFFF,
                                    cam.getMaxYTarget() & 0xFFFF, g == null ? "-" : g[11], g == null ? "-" : g[12],
                                    g == null ? "-" : g[15], g == null ? "-" : g[16], g == null ? "-" : g[17],
                                    g == null ? "-" : g[18]));
                            if (recent.size() > 12) recent.removeFirst();
                            if (row == firstCam[0]) recentAtError.addAll(recent);
                        }
                    });
        }
        String replay = String.format("%s: %d rows replayed, first player/camera divergence row %d %s", movie,
                outcome.rowsCompared(), outcome.firstErrorRow(), outcome.fields());
        TreeSet<String> nativeInWindow = new TreeSet<>();
        for (String a : nativeAppear) if (Integer.parseInt(a.split(":")[0]) <= lastRow[0]) nativeInWindow.add(a);
        System.out.println("QQZBOSS " + replay);
        outcome.rows().forEach(r -> System.out.println("QQZBOSS " + movie + " row " + r.format()));
        TreeSet<String> nativeRowIds = rowIds(nativeInWindow), engineRowIds = rowIds(engineAppear);
        slotNotes.stream().limit(30).forEach(n -> System.out.println("QQZBOSS " + movie + " slot " + n));
        System.out.println("QQZBOSS " + movie + " appearances native=" + nativeInWindow.size() + " engine=" + engineAppear.size()
                + " slot mismatches=" + mismatches.size() + " booth wear rows compared=" + hitsSeen[0]);
        recentAtError.forEach(r -> System.out.println("QQZBOSS " + movie + " diag " + r));
        mismatches.stream().limit(20).forEach(m -> System.out.println("QQZBOSS " + movie + " mismatch " + m));
        // The fight must replay exactly through the capsule's break (native capsule routine 4) and the
        // results' first frame of locked control; a whole-movie check holds every later row too.
        int capsuleHit = firstCapsuleBreakRow(nativeSlots, offset, spec.capsuleId());
        int firstError = outcome.firstErrorRow();
        System.out.println("QQZBOSS " + movie + " escape end row " + firstRowBossFlagsClear(nativeGlobals, offset)
                + ", capsule break row " + capsuleHit + ", player/camera equal through row "
                + (firstError < 0 ? outcome.rowsCompared() - 1 : firstError - 1));
        assertTrue(firstError == -1 || (!wholeMovie && capsuleHit >= 0 && firstError > capsuleHit), replay);
        List<String> boundsBefore = boundMismatches.stream()
                .filter(m -> firstError < 0 || Integer.parseInt(m.split(" ")[1]) < firstError).toList();
        boundsBefore.stream().limit(10).forEach(m -> System.out.println("QQZBOSS " + movie + " bounds " + m));
        assertTrue(boundsBefore.isEmpty(), movie + ": camera bounds differ from native: " + boundsBefore);
        assertEquals(nativeRowIds, engineRowIds, movie + ": boss appearances (row:ID:count)");
        assertTrue(mismatches.isEmpty(), movie + ": " + mismatches.size() + " boss slot/bound mismatches, first "
                + (mismatches.isEmpty() ? "" : mismatches.getFirst()));
        assertTrue(hitsSeen[0] > 0, movie + ": no booth rows compared");
    }

    private static void engineSlots(Map<ObjectInstance, Boolean> seen, TreeSet<String> appear, int row,
                                    List<CdBossPart> appeared) {
        var objects = GameServices.level().getObjectManager();
        for (ObjectInstance object : objects.getActiveObjects()) {
            if (!(object instanceof CdBossPart part) || part.isDestroyed()) continue;
            if (seen.put(object, Boolean.TRUE) == null) {
                appear.add(String.format("%d:%d:%02X", row, part.getSlotIndex(), part.getSpawn().objectId()));
                appeared.add(part);
            }
        }
    }

    /**
     * Pairs this row's native appearances with the engine's, per ID in slot order, so a native slot's RAM is
     * compared with the object that appeared for it even where other objects shifted the slot numbers. Slot
     * differences are noted with the engine's dynamic-slot occupancy.
     */
    private static void pair(int row, TreeSet<String> nativeAppear, List<CdBossPart> appeared,
                             Map<Integer, CdBossPart> byNativeSlot, List<String> notes) {
        Map<Integer, List<Integer>> nativeSlotsById = new HashMap<>();
        for (String a : nativeAppear) {
            String[] c = a.split(":");
            if (Integer.parseInt(c[0]) == row) {
                nativeSlotsById.computeIfAbsent(Integer.parseInt(c[2], 16), k -> new ArrayList<>()).add(Integer.parseInt(c[1]));
            }
        }
        Map<Integer, List<CdBossPart>> engineById = new HashMap<>();
        for (CdBossPart part : appeared) engineById.computeIfAbsent(part.getSpawn().objectId(), k -> new ArrayList<>()).add(part);
        for (var e : nativeSlotsById.entrySet()) {
            List<Integer> slots = e.getValue();
            slots.sort(null);
            List<CdBossPart> parts = engineById.getOrDefault(e.getKey(), List.of());
            parts = new ArrayList<>(parts);
            parts.sort(java.util.Comparator.comparingInt(CdBossPart::getSlotIndex));
            for (int i = 0; i < slots.size() && i < parts.size(); i++) {
                byNativeSlot.put(slots.get(i), parts.get(i));
                if (slots.get(i) != parts.get(i).getSlotIndex()) {
                    notes.add(String.format("row %d id %02X native slot %d engine slot %d%s", row, e.getKey(), slots.get(i),
                            parts.get(i).getSlotIndex(), notes.size() < 2 ? " occupancy " + occupancy() : ""));
                }
            }
        }
    }

    private static String occupancy() {
        StringBuilder out = new StringBuilder();
        var objects = GameServices.level().getObjectManager();
        java.util.TreeMap<Integer, String> slots = new java.util.TreeMap<>();
        for (ObjectInstance o : objects.getActiveObjects()) {
            if (o instanceof com.openggf.level.objects.AbstractObjectInstance a && !a.isDestroyed() && a.getSlotIndex() >= 32) {
                slots.put(a.getSlotIndex(), String.format("%02X:%s", o.getSpawn().objectId(), o.getClass().getSimpleName()));
            }
        }
        slots.forEach((k, v) -> out.append(' ').append(k).append('=').append(v));
        return out.toString();
    }

    private static TreeSet<String> rowIds(TreeSet<String> appearances) {
        Map<String, Integer> counts = new java.util.TreeMap<>();
        for (String a : appearances) {
            String[] c = a.split(":");
            counts.merge(c[0] + ":" + c[2], 1, Integer::sum);
        }
        TreeSet<String> out = new TreeSet<>(java.util.Comparator.comparingInt((String k) -> Integer.parseInt(k.split(":")[0]))
                .thenComparing(k -> k));
        counts.forEach((k, v) -> out.add(k + ":" + v));
        return out;
    }

    /**
     * objects.csv columns: frame,slot,id,sprflags,routine,routine2,x,y,col_type,col_status,map_frame,anim,v2a,v2b,v2c,
     * v2d,v34,v35,v30,v32,v2e,v3e,v34w,v36,v38,v3a,sprtile (the QQZ probe).
     */
    private static void compareSlots(Spec spec, int row, Map<Integer, String[]> nat, Map<Integer, CdBossPart> eng,
                                     List<String> out, int[] hitsSeen) {
        for (var e : nat.entrySet()) {
            String[] c = e.getValue();
            int id = Integer.parseInt(c[2], 16);
            if (!spec.ids().contains(id)) continue;
            CdBossPart part = eng.get(e.getKey());
            String want = String.format("id %02X x %04X y %04X rt %02X col %s", id, Integer.parseInt(c[6]),
                    Integer.parseInt(c[7]), Integer.parseInt(c[4]), c[8]);
            if (part == null || part.isDestroyed()) {
                out.add("row " + row + " slot " + e.getKey() + " native " + want + " engine none");
                continue;
            }
            String got = String.format("id %02X x %04X y %04X rt %02X col %02X", part.getSpawn().objectId(),
                    part.getX() & 0xFFFF, part.getY() & 0xFFFF, part.routine(), part.colType());
            if (part instanceof com.openggf.game.soniccd.objects.boss.qqz.QqzBossBooth booth) {
                want += " wear " + Integer.parseInt(c[21]);
                got += " wear " + (short) booth.wear();
                hitsSeen[0]++;
            }
            if (part instanceof com.openggf.game.soniccd.objects.boss.qqz.QqzBossBelt belt) {
                want += " belt " + Integer.parseInt(c[19]) + "/" + Integer.parseInt(c[22]);
                got += " belt " + (belt.speed() & 0xFFFF) + "/" + (belt.travel() & 0xFFFF);
            }
            if (!want.equals(got)) out.add("row " + row + " slot " + e.getKey() + " native " + want + " engine " + got);
        }
        for (var e : eng.entrySet()) {
            if (!nat.containsKey(e.getKey()) && !e.getValue().isDestroyed()) {
                out.add("row " + row + " native slot " + e.getKey() + " empty, engine "
                        + e.getValue().getClass().getSimpleName() + " live");
            }
        }
        eng.entrySet().removeIf(e -> e.getValue().isDestroyed() && !nat.containsKey(e.getKey()));
    }

    /** globals.csv: frame,lag,levelFrames,p_x,p_y,...,cam_x,cam_y,left,right,bottom,dest_bottom,cam_y_center,boss_flags,boss_fight. */
    private static void compareBounds(int row, String[] g, List<String> out) {
        if (g == null) return;
        var camera = GameServices.camera();
        String want = String.format("L %04X R %04X B %04X", Integer.parseInt(g[13]), Integer.parseInt(g[14]),
                Integer.parseInt(g[15]));
        String got = String.format("L %04X R %04X B %04X", camera.getMinX() & 0xFFFF, camera.getMaxX() & 0xFFFF,
                camera.getMaxY() & 0xFFFF);
        if (!want.equals(got)) out.add("row " + row + " bounds native " + want + " engine " + got);
    }

    private static Map<Integer, Map<Integer, String[]>> nativeSlots(Path csv) throws Exception {
        Map<Integer, Map<Integer, String[]>> out = new HashMap<>();
        List<String> lines = Files.readAllLines(csv);
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            out.computeIfAbsent(Integer.parseInt(c[0]), k -> new HashMap<>()).put(Integer.parseInt(c[1]), c);
        }
        return out;
    }

    private static Map<Integer, String[]> nativeGlobals(Path csv) throws Exception {
        Map<Integer, String[]> out = new HashMap<>();
        List<String> lines = Files.readAllLines(csv);
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            out.put(Integer.parseInt(c[0]), c);
        }
        return out;
    }

    private static TreeSet<String> nativeAppearances(Path aux, java.util.Set<Integer> ids) throws Exception {
        TreeSet<String> out = new TreeSet<>();
        for (String line : Files.readAllLines(aux)) {
            Matcher m = APPEARED.matcher(line);
            if (!m.find()) continue;
            int id = Integer.parseInt(m.group(3), 16);
            int slot = Integer.parseInt(m.group(2));
            if (slot < 32 || !ids.contains(id)) continue;
            out.add(String.format("%d:%d:%02X", Integer.parseInt(m.group(1)), slot, id));
        }
        return out;
    }

    /** The first row whose native RAM shows the capsule in routine 4 (broken), or -1. */
    private static int firstCapsuleBreakRow(Map<Integer, Map<Integer, String[]>> slots, int offset, int capsuleId) {
        for (int frame = offset + 1; frame < offset + 100000; frame++) {
            Map<Integer, String[]> at = slots.get(frame);
            if (at == null) {
                if (frame > offset + 10000) break;
                continue;
            }
            for (String[] c : at.values()) {
                if (Integer.parseInt(c[2], 16) == capsuleId && "4".equals(c[4])) return frame - offset - 1;
            }
        }
        return -1;
    }

    /** The first row after which native {@code bossFlags} reads clear again (globals.csv column 18), or -1. */
    private static int firstRowBossFlagsClear(Map<Integer, String[]> globals, int offset) {
        boolean seen = false;
        for (int frame = offset + 1; globals.containsKey(frame); frame++) {
            boolean set = !"0".equals(globals.get(frame)[18]);
            if (set) seen = true;
            else if (seen) return frame - offset - 1;
        }
        return -1;
    }

    private static String json(String text, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([0-9A-Za-z]+)").matcher(text);
        if (!m.find()) throw new IllegalStateException("no " + key);
        return m.group(1);
    }
}
