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

/**
 * The Collision Chaos boss's native replay comparison (after {@code CdBossNativeReplay}, which requires the player to
 * match through the capsule's break). The arena is a pinball table whose bumpers ($1D) and flippers ($1E, with the
 * player's flipper hook) are hosted by other lanes and not on this branch, so a route that touches one leaves the
 * native route there. This comparison therefore covers every row before the first player/camera divergence and
 * reports that row with the first native row where the player touches a bumper or flipper (a bumper's animation
 * turns to 1, or a flipper's routine or frame changes, in the probe's {@code objects.csv}).
 *
 * <p>Per compared row (row N = BK2 row {@code bk2_frame_offset + N} = probe frame {@code bk2_frame_offset + N + 1}):
 * boss, part and capsule appearances (row, ID) against the aux {@code object_appeared} events, paired per ID in slot
 * order; every boss slot's ID, X, Y, routine, collision type and mapping frame, and the hit count ({@code collide_status}) of the
 * machine and the entry barrier, against native RAM; the camera's left, right and bottom bounds and
 * {@code camYCenter} against {@code globals.csv}.
 */
final class CczBossNativeReplay {
    /** IDs compared: capsule $15, entry barrier $30, machine $31, gear $32, Eggman $33, launcher $3D, bomb $3E, exit barrier $3F. */
    static final java.util.Set<Integer> IDS = java.util.Set.of(0x15, 0x30, 0x31, 0x32, 0x33, 0x3D, 0x3E, 0x3F);
    /** IDs whose {@code collide_status} is a hit count: the entry barrier and the machine. */
    static final java.util.Set<Integer> HIT_IDS = java.util.Set.of(0x30, 0x31);

    private static final Pattern APPEARED = Pattern.compile(
            "\"frame\":(\\d+),\"vfc\":\\d+,\"event\":\"object_appeared\",\"slot\":(\\d+),\"object_type\":\"0x([0-9A-F]+)\"");

    /** One movie's result: the replay, the compared window and every difference inside it. */
    record Result(CdFixtureReplay.Outcome outcome, int comparedRows, int firstUnhostedContactRow,
                  TreeSet<String> nativeAppearances, TreeSet<String> engineAppearances, List<String> mismatches,
                  List<String> boundMismatches, Map<Integer, Integer> rowsPerId, int machineFightRows) {
        int firstErrorRow() { return outcome.firstErrorRow(); }
    }

    private CczBossNativeReplay() { }

    static Path root(String movie) throws Exception {
        return CdTestMedia.nativeFile("rehost-boss-ccz/" + movie + "/manifest.json").getParent();
    }

    static Result check(Path tempDir, String movie, CdStagePayload payload, int bombRows) throws Exception {
        return check(tempDir, movie, payload, bombRows, null);
    }

    /**
     * Replays {@code movie}. Bomb slots ($3E) are compared on rows before {@code bombRows} (the movie's first row where
     * a bomb leaves native through the terrain, stated by the caller with its evidence); {@code perturb} (tests of
     * the comparison only) runs on each row before it is compared.
     */
    static Result check(Path tempDir, String movie, CdStagePayload payload, int bombRows,
                        java.util.function.IntConsumer perturb) throws Exception {
        Path root = root(movie);
        Path traceDir = root.resolve("trace");
        int offset = Integer.parseInt(json(Files.readString(traceDir.resolve("metadata.json")), "bk2_frame_offset"));
        Map<Integer, Map<Integer, String[]>> nativeSlots = nativeSlots(root.resolve("probe/objects.csv"));
        Map<Integer, String[]> nativeGlobals = nativeGlobals(root.resolve("probe/globals.csv"));
        Map<Integer, Integer> frameRow = CdBossNativeReplay.completedFrameRows(nativeGlobals, offset);
        TreeSet<String> nativeAppear = new TreeSet<>();
        for (String a : nativeAppearances(traceDir.resolve("aux_state.jsonl"))) {
            String[] c = a.split(":");
            nativeAppear.add(CdBossNativeReplay.levelFrameGroupStart(nativeGlobals, offset, Integer.parseInt(c[0])) + ":" + c[1]
                    + ":" + c[2]);
        }
        TreeSet<String> engineAppear = new TreeSet<>();
        Map<Integer, CdBossPart> byNativeSlot = new HashMap<>();
        Map<ObjectInstance, Boolean> seen = new java.util.IdentityHashMap<>();
        List<String> mismatches = new ArrayList<>();
        List<String> boundMismatches = new ArrayList<>();
        Map<Integer, Integer> rowsPerId = new java.util.TreeMap<>();
        List<Integer> fightRowList = new ArrayList<>();
        List<String[]> rowDiag = new ArrayList<>();

        CdFixtureReplay.Outcome outcome;
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            outcome = CdFixtureReplay.replay(disc, tempDir,
                    new CdFixtureReplay.Fixture(payload, traceDir, root.resolve("movie.bk2")), Integer.MAX_VALUE,
                    false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void row(int row, AbstractPlayableSprite sonic) {
                            if (perturb != null) perturb.accept(row);
                            int frame = offset + frameRow.getOrDefault(row, row) + 1;
                            Map<Integer, String[]> nat = nativeSlots.getOrDefault(frame, Map.of());
                            List<CdBossPart> appeared = new ArrayList<>();
                            engineSlots(seen, engineAppear, row, appeared);
                            pair(row, nat, nativeAppear, appeared, byNativeSlot);
                            compareSlots(row, nat, byNativeSlot, mismatches, rowsPerId, fightRowList, bombRows);
                            compareBounds(row, nativeGlobals.get(frame), boundMismatches);
                        }
                    });
        }
        int firstError = outcome.firstErrorRow();
        int window = firstError < 0 ? outcome.rowsCompared() : firstError;
        TreeSet<String> nativeIn = new TreeSet<>(), engineIn = new TreeSet<>();
        for (String a : nativeAppear) if (Integer.parseInt(a.split(":")[0]) < window) nativeIn.add(a);
        for (String a : engineAppear) if (Integer.parseInt(a.split(":")[0]) < window) engineIn.add(a);
        List<String> inWindow = mismatches.stream().filter(m -> rowOf(m) < window).toList();
        List<String> boundsIn = boundMismatches.stream().filter(m -> rowOf(m) < window).toList();
        int contact = CdBossNativeReplay.firstContactRow(root.resolve("probe/objects.csv"), traceDir.resolve("physics.csv"),
                offset, java.util.Set.of(0x1D, 0x1E), java.util.Set.of(0x1E));
        int fightRows = (int) fightRowList.stream().filter(row -> row < window).count();
        Result result = new Result(outcome, window, contact, rowIds(nativeIn), rowIds(engineIn), inWindow, boundsIn,
                rowsPerId, fightRows);
        System.out.printf("CCZBOSS %s: %d rows replayed, first player/camera divergence row %d %s; first native"
                        + " bumper/flipper contact row %d%n", movie, outcome.rowsCompared(), firstError, outcome.fields(),
                contact);
        outcome.rows().forEach(r -> System.out.println("CCZBOSS " + movie + " row " + r.format()));
        System.out.println("CCZBOSS " + movie + " compared rows " + window + ", appearances native=" + nativeIn.size()
                + " engine=" + engineIn.size() + ", slot rows per id " + rowsPerId + ", machine fight rows "
                + fightRows + ", slot mismatches " + inWindow.size() + ", bound mismatches " + boundsIn.size());
        Map<String, List<String>> byId = new java.util.TreeMap<>();
        for (String m : inWindow) {
            java.util.regex.Matcher id = Pattern.compile("native id ([0-9A-F]{2})").matcher(m);
            byId.computeIfAbsent(id.find() ? id.group(1) : "slot", k -> new ArrayList<>()).add(m);
        }
        byId.forEach((id, list) -> {
            System.out.println("CCZBOSS " + movie + " mismatches id " + id + ": " + list.size());
            list.stream().limit(6).forEach(m -> System.out.println("CCZBOSS " + movie + " mismatch " + m));
        });
        boundsIn.stream().limit(10).forEach(m -> System.out.println("CCZBOSS " + movie + " bounds " + m));
        if (!result.nativeAppearances().equals(result.engineAppearances())) {
            TreeSet<String> onlyNative = new TreeSet<>(result.nativeAppearances());
            onlyNative.removeAll(result.engineAppearances());
            TreeSet<String> onlyEngine = new TreeSet<>(result.engineAppearances());
            onlyEngine.removeAll(result.nativeAppearances());
            System.out.println("CCZBOSS " + movie + " appearances only native " + onlyNative + " only engine " + onlyEngine);
        }
        return result;
    }



    private static int rowOf(String m) {
        return Integer.parseInt(m.split(" ")[1]);
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
     * Pairs this row's native appearances with the engine's, per ID in slot order. A slot freed and refilled with the
     * same ID within one frame has no {@code object_appeared} event (the recorder stamps an ID change only): a native
     * slot that still holds its ID while its paired engine part is gone is paired again with this row's new parts and
     * counted as an appearance, the rule {@code CdBossNativeReplay} applies (R33C-fight row 1765: the bomb in slot 32
     * is deleted and a defeat explosion $3E takes the slot in the same pass).
     */
    private static void pair(int row, Map<Integer, String[]> nat, TreeSet<String> nativeAppear, List<CdBossPart> appeared,
                             Map<Integer, CdBossPart> byNativeSlot) {
        Map<Integer, List<Integer>> nativeSlotsById = new HashMap<>();
        for (String a : nativeAppear) {
            String[] c = a.split(":");
            if (Integer.parseInt(c[0]) == row) {
                nativeSlotsById.computeIfAbsent(Integer.parseInt(c[2], 16), k -> new ArrayList<>()).add(Integer.parseInt(c[1]));
            }
        }
        for (var e : nat.entrySet()) {
            CdBossPart old = byNativeSlot.get(e.getKey());
            int id = Integer.parseInt(e.getValue()[2], 16);
            if (old != null && old.isDestroyed() && old.getSpawn().objectId() == id && IDS.contains(id)) {
                List<Integer> slots = nativeSlotsById.computeIfAbsent(id, k -> new ArrayList<>());
                if (!slots.contains(e.getKey())) {
                    slots.add(e.getKey());
                    nativeAppear.add(String.format("%d:%d:%02X", row, e.getKey(), id));
                }
            }
        }
        Map<Integer, List<CdBossPart>> engineById = new HashMap<>();
        for (CdBossPart part : appeared) engineById.computeIfAbsent(part.getSpawn().objectId(), k -> new ArrayList<>()).add(part);
        for (var e : nativeSlotsById.entrySet()) {
            List<Integer> slots = e.getValue();
            slots.sort(null);
            List<CdBossPart> parts = new ArrayList<>(engineById.getOrDefault(e.getKey(), List.of()));
            parts.sort(java.util.Comparator.comparingInt(CdBossPart::getSlotIndex));
            for (int i = 0; i < slots.size() && i < parts.size(); i++) byNativeSlot.put(slots.get(i), parts.get(i));
        }
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

    /** objects.csv: frame,slot,id,sprflags,routine,routine2,x,y,col_type,col_status,map_frame,anim,... */
    private static void compareSlots(int row, Map<Integer, String[]> nat, Map<Integer, CdBossPart> eng,
                                     List<String> out, Map<Integer, Integer> rowsPerId, List<Integer> fightRows, int bombRows) {
        for (var e : nat.entrySet()) {
            String[] c = e.getValue();
            int id = Integer.parseInt(c[2], 16);
            if (!IDS.contains(id) || (id == 0x3E && row >= bombRows)) continue;
            CdBossPart part = eng.get(e.getKey());
            String want = String.format("id %02X x %04X y %04X rt %02X col %s fr %02X", id, Integer.parseInt(c[6]),
                    Integer.parseInt(c[7]), Integer.parseInt(c[4]), c[8], Integer.parseInt(c[10]));
            if (part == null || part.isDestroyed()) {
                out.add("row " + row + " slot " + e.getKey() + " native " + want + " engine none");
                continue;
            }
            String got = String.format("id %02X x %04X y %04X rt %02X col %02X fr %02X", part.getSpawn().objectId(),
                    part.getX() & 0xFFFF, part.getY() & 0xFFFF, part.routine(), part.colType(), part.mapFrame());
            if (HIT_IDS.contains(id)) {
                want += " hits " + c[9];
                got += " hits " + part.getCollisionProperty();
                if (id == 0x31 && "4".equals(c[4])) fightRows.add(row);
            }
            rowsPerId.merge(id, 1, Integer::sum);
            if (!want.equals(got)) out.add("row " + row + " slot " + e.getKey() + " native " + want + " engine " + got);
        }
        for (var e : eng.entrySet()) {
            if (row >= bombRows && e.getValue().getSpawn().objectId() == 0x3E) continue;
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
        String want = String.format("L %04X R %04X B %04X YC %04X", Integer.parseInt(g[13]), Integer.parseInt(g[14]),
                Integer.parseInt(g[15]), Integer.parseInt(g[17]));
        String got = String.format("L %04X R %04X B %04X YC %04X", camera.getMinX() & 0xFFFF, camera.getMaxX() & 0xFFFF,
                camera.getMaxY() & 0xFFFF, camera.getYPosBias() & 0xFFFF);
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

    static Map<Integer, String[]> nativeGlobals(Path csv) throws Exception {
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

    static String json(String text, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([0-9A-Za-z]+)").matcher(text);
        if (!m.find()) throw new IllegalStateException("no " + key);
        return m.group(1);
    }
}
