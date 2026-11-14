package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.TraceData;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Rings equal native over every full-act and loop recording (comparison only). Each recording under
 * {@code rehost-long} and {@code rehost-loop} in the native set is replayed from its movie; on every row before the
 * player or camera first differs from the trace (the replay's frontier), the engine's state after the row is compared
 * with the native row that completed the same level frame ({@link CdFixtureReplay#completedRow}):
 * <ul>
 *   <li><b>counter</b>: the ring counter;</li>
 *   <li><b>rings</b>: the positions of the slots holding object $10 ({@code ObjRing}, Level/_Objects/Ring/Main.asm:7-287),
 *       as a multiset (native holds two copies of a group where an untracked entry respawns while live);</li>
 *   <li><b>lost</b>: the number of slots holding object $11 ({@code ObjLostRing}, :317-460);</li>
 *   <li><b>slots</b>: which slot holds each of them.</li>
 * </ul>
 * A recording is {@code exact} (all four equal on every compared row), {@code positions} (the first three equal, slots
 * differ after some other object's lifetime differs; the listed row is the first, for information) or {@code differs}
 * (the counter, rings or lost rings differ; the listed row is the first measured, and the test fails if it moves
 * earlier). A recording that improves past its status fails until it is moved, as {@code TestSonicCdActEndNative}
 * lists its routes. Every recording must still compare at least its listed number of rows.
 *
 * <p>The native slot table is rebuilt from the trace's {@code object_appeared} and {@code object_removed} events,
 * which the recorder writes when a slot's object ID changes. A ring replaced by another ring in the same slot on the
 * same row would keep a stale position: the {@code object_near} rows (every object close to the player, with its
 * position) check every $10 entry the player passes and correct one they contradict; the checks and corrections are
 * reported (when written, about 5,000 checks a recording and no correction, and no near $10 in a slot the events do
 * not hold as $10). A ring far from the player is not checked. A $11 entry has no position (lost rings move every
 * frame); only their number is compared.
 *
 * <p>Roots: {@code -Dsoniccd.ring.roots=<dir>[,<dir>...]} replaces the default {@code <native>/rehost-long} and
 * {@code <native>/rehost-loop}. {@code -Dsoniccd.ring.alldiff=<name>[,<name>]} writes every change of the whole
 * dynamic-slot table difference ({@code target/ring-alldiff-<name>.txt}), the first place to look when slots differ.
 */
class TestCdRingNativeParity {
    private static final Pattern EVENT = Pattern.compile("\"frame\":(\\d+),\"vfc\":\\d+,\"event\":\"object_(appeared|removed)\","
            + "\"slot\":(\\d+),\"object_type\":\"0x([0-9A-F]+)\"(?:,\"x\":\"0x([0-9A-F]+)\",\"y\":\"0x([0-9A-F]+)\")?");
    private static final Pattern NEAR = Pattern.compile("\"frame\":(\\d+),\"vfc\":\\d+,\"event\":\"object_near\","
            + "\"slot\":(\\d+),\"type\":\"0x10\",\"x\":\"0x([0-9A-F]+)\",\"y\":\"0x([0-9A-F]+)\"");
    private static final int RING = 0x10;
    private static final int LOST_RING = 0x11;
    private static final int FIRST_DYNAMIC_SLOT = 32;

    @TempDir
    Path tempDir;

    private enum Kind { APPEARED, REMOVED, NEAR }

    private record Event(int row, Kind kind, int slot, int id, int x, int y) { }

    /** One recording's measure. Rows are -1 where the channel never differs. */
    record Result(String name, int rowsCompared, int rowsInTrace, int firstCounter, int firstRings, int firstLost,
                  int firstSlots, int counterRows, int ringRows, int lostRows, int slotRows, int reversals,
                  int nearChecks, int nearCorrections, int nearUnmatched) {
        int firstDifference() {
            int first = -1;
            for (int row : new int[] {firstCounter, firstRings, firstLost}) {
                if (row >= 0 && (first < 0 || row < first)) first = row;
            }
            return first;
        }

        String line() {
            return String.format("%s compared %d of %d rows (%d spawner reversals); counter %s; rings %s; lost %s; "
                            + "slots %s; native $10 positions checked against object_near %d, corrected %d, "
                            + "near a slot the events do not hold as $10 %d", name, rowsCompared, rowsInTrace, reversals,
                    channel(firstCounter, counterRows), channel(firstRings, ringRows), channel(firstLost, lostRows),
                    channel(firstSlots, slotRows), nearChecks, nearCorrections, nearUnmatched);
        }

        private static String channel(int first, int rows) {
            return first < 0 ? "equal" : "first row " + first + " (" + rows + " rows)";
        }
    }

    /**
     * Measured after the RINGS lane (2026-11-13). {@code positions}: the slot table differs first at the listed row,
     * after another object's lifetime differs (listed per stage in the re-host plan). {@code differs}: R81A and R81C,
     * whose spilled rings take other slots than native's because Metallic Madness's Poh-Bee ($2E,
     * objects/r8/poh_bee.asm) is not hosted (16 live natively at R81A's hurt, row 3201; in R81C one is destroyed in the
     * good future on row 2397 where the engine keeps one), so their floor checks, phased by {@code d7} (Ring/Main.asm:
     * 415-418), bounce them on other rows and a ring is collected on another row.
     */
    @ParameterizedTest(name = "{0}: {2}")
    @CsvSource({
            "R11A, 4220, exact, -1", "R11B, 3171, positions, 1600", "R11C, 5790, exact, -1", "R12A, 4244, exact, -1",
            "R31A, 5290, positions, 2836", "R31B, 8241, positions, 5488", "R31C, 6995, positions, 4098",
            "R32A, 6682, positions, 1756", "R41A, 5449, positions, 918", "R41B, 4721, positions, 292",
            "R41C, 6013, exact, -1", "R42A, 4286, positions, 1196", "R51A, 4756, exact, -1", "R51B, 5494, exact, -1",
            "R51C, 7151, exact, -1", "R52A, 4772, positions, 2202", "R61A, 4892, exact, -1",
            "R61B, 5449, positions, 2749", "R61C, 6767, positions, 5412", "R62A, 4787, exact, -1",
            "R71A, 3977, exact, -1", "R71B, 4918, exact, -1", "R71C, 6096, positions, 808", "R72A, 4399, exact, -1",
            "R81A, 4736, differs, 3343", "R81B, 3991, positions, 2566", "R81C, 4524, differs, 3968",
            "R82A, 4577, exact, -1", "R11B-loop, 2163, exact, -1", "R13C-loop, 785, exact, -1"})
    void ringsEqualNative(String recording, int rows, String status, int firstRow) throws Exception {
        Path dir = recordingDir(recording);
        Path cue = CdTestMedia.usaCue();
        Result result;
        try (CdDiscDataSource disc = CdDiscDataSource.open(cue)) {
            result = measure(disc, dir, recording);
        }
        System.out.println("[scd-rings] " + result.line());
        assertTrue(result.rowsCompared() >= rows, recording + " compares fewer rows than " + rows + ": " + result.line());
        int first = result.firstDifference();
        switch (status) {
            case "exact" -> {
                assertTrue(first < 0, recording + " counter or rings differ from native: " + result.line());
                assertTrue(result.firstSlots() < 0, recording + " ring slots differ from native: " + result.line());
            }
            case "positions" -> {
                assertTrue(first < 0, recording + " counter or rings differ from native: " + result.line());
                if (result.firstSlots() < 0) {
                    fail(recording + " ring slots now equal native on every row: move it into the exact set. "
                            + result.line());
                }
            }
            case "differs" -> {
                if (first < 0) fail(recording + " now equals native: move it into the positions or exact set. " + result.line());
                assertTrue(first >= firstRow, recording + " differs earlier than row " + firstRow + ": " + result.line());
            }
            default -> throw new IllegalArgumentException(status);
        }
    }

    private static Path recordingDir(String recording) {
        String roots = System.getProperty("soniccd.ring.roots");
        List<Path> candidates = new ArrayList<>();
        if (roots != null && !roots.isBlank()) {
            for (String root : roots.split(",")) candidates.add(Path.of(root.trim()));
        } else {
            for (String set : new String[] {"rehost-long", "rehost-loop"}) {
                Path readme = CdTestMedia.optionalNativeFile(set + "/README.md");
                if (readme != null) candidates.add(readme.getParent());
            }
        }
        for (Path root : candidates) {
            Path dir = root.resolve(recording);
            if (Files.isRegularFile(dir.resolve("trace/metadata.json")) && Files.isRegularFile(dir.resolve("movie.bk2"))) {
                return dir;
            }
        }
        Assumptions.abort("Native recording absent: " + recording + " under " + candidates);
        return null;
    }

    private Result measure(CdDiscDataSource disc, Path dir, String name) throws Exception {
        CdStagePayload payload = CdStagePayload.valueOf(name.split("-")[0]);
        var fixture = new CdFixtureReplay.Fixture(payload, dir.resolve("trace"), dir.resolve("movie.bk2"));
        TraceData trace = TraceData.load(fixture.traceDir());
        List<Event> events = events(fixture.traceDir().resolve("aux_state.jsonl"));
        List<Integer> reversalRows = reversalRows(fixture.traceDir().resolve("aux_state.jsonl"));
        int[] cursor = {0};
        int[] near = new int[3];                                 // checked, corrected, unmatched
        TreeMap<Integer, int[]> nativeSlots = new TreeMap<>();   // slot -> {id, x, y}
        List<int[]> perRow = new ArrayList<>();                  // per row: {counter, rings, lost, slots} differ (0/1)
        List<String> detail = new ArrayList<>();
        boolean allDiff = List.of(System.getProperty("soniccd.ring.alldiff", "").split(",")).contains(name);
        List<String> allDiffLines = new ArrayList<>();
        String[] lastAll = {null};
        Path config = Files.createDirectories(tempDir.resolve(name));
        var outcome = CdFixtureReplay.replay(disc, config, fixture, Integer.MAX_VALUE, true, -1, -1,
                new CdFixtureReplay.Hooks() {
                    @Override public void row(int row, AbstractPlayableSprite sonic) {
                        int nativeRow = CdFixtureReplay.completedRow(trace, row);
                        while (cursor[0] < events.size() && events.get(cursor[0]).row() <= nativeRow) {
                            Event e = events.get(cursor[0]++);
                            switch (e.kind()) {
                                case APPEARED -> nativeSlots.put(e.slot(), new int[] {e.id(), e.x(), e.y()});
                                case REMOVED -> nativeSlots.remove(e.slot());
                                case NEAR -> {
                                    int[] entry = nativeSlots.get(e.slot());
                                    if (entry == null || entry[0] != RING) {
                                        near[2]++;
                                    } else {
                                        near[0]++;
                                        if (entry[1] != e.x() || entry[2] != e.y()) {
                                            entry[1] = e.x();
                                            entry[2] = e.y();
                                            near[1]++;
                                        }
                                    }
                                }
                            }
                        }
                        TreeMap<Integer, String> nativeRings = new TreeMap<>();
                        TreeMap<Integer, String> nativeAll = new TreeMap<>();
                        for (var entry : nativeSlots.entrySet()) {
                            int[] v = entry.getValue();
                            if (v[0] == RING || v[0] == LOST_RING) nativeRings.put(entry.getKey(), describe(v[0], v[1], v[2]));
                            if (entry.getKey() >= FIRST_DYNAMIC_SLOT) nativeAll.put(entry.getKey(), String.format("%02X", v[0]));
                        }
                        TreeMap<Integer, String> engineRings = new TreeMap<>();
                        TreeMap<Integer, String> engineAll = new TreeMap<>();
                        var objects = GameServices.level().getObjectManager();
                        if (objects != null) {
                            for (ObjectInstance object : objects.getActiveObjects()) {
                                if (!(object instanceof AbstractObjectInstance aoi) || aoi.isDestroyed()
                                        || object.getSpawn() == null) continue;
                                int id = object.getSpawn().objectId();
                                int slot = aoi.getSlotIndex();
                                if (slot >= FIRST_DYNAMIC_SLOT) engineAll.put(slot, String.format("%02X", id));
                                if (id == RING || id == LOST_RING) {
                                    engineRings.put(slot, describe(id, object.getX() & 0xFFFF, object.getY() & 0xFFFF));
                                }
                            }
                        }
                        int nativeCounter = trace.getFrame(nativeRow).rings();
                        boolean counter = nativeCounter != sonic.getRingCount();
                        boolean rings = !placed(nativeRings).equals(placed(engineRings));
                        boolean lost = lostCount(nativeRings) != lostCount(engineRings);
                        boolean slots = !nativeRings.equals(engineRings);
                        perRow.add(new int[] {counter ? 1 : 0, rings ? 1 : 0, lost ? 1 : 0, slots ? 1 : 0});
                        if (counter || rings || lost || slots) {
                            detail.add(String.format("%s row %d: counter native=%d engine=%d; native-only=%s engine-only=%s",
                                    name, row, nativeCounter, sonic.getRingCount(), only(nativeRings, engineRings),
                                    only(engineRings, nativeRings)));
                        }
                        if (allDiff) {
                            String d = "native-only=" + only(nativeAll, engineAll) + " engine-only=" + only(engineAll, nativeAll);
                            if (!d.equals(lastAll[0])) {
                                lastAll[0] = d;
                                allDiffLines.add("row " + row + " " + d);
                            }
                        }
                    }
                });
        int through = Math.min(outcome.firstErrorRow() >= 0 ? outcome.firstErrorRow() : outcome.rowsCompared(),
                perRow.size());
        int[] first = {-1, -1, -1, -1};
        int[] count = new int[4];
        for (int row = 0; row < through; row++) {
            for (int c = 0; c < 4; c++) {
                if (perRow.get(row)[c] == 0) continue;
                count[c]++;
                if (first[c] < 0) first[c] = row;
            }
        }
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", "ring-parity-" + name + ".txt"),
                detail.stream().filter(line -> rowOf(line) < through).toList());
        if (allDiff) Files.write(Path.of("target", "ring-alldiff-" + name + ".txt"), allDiffLines);
        int reversals = (int) reversalRows.stream().filter(r -> r < through).count();
        return new Result(name, through, trace.frameCount(), first[0], first[1], first[2], first[3],
                count[0], count[1], count[2], count[3], reversals, near[0], near[1], near[2]);
    }

    private static String describe(int id, int x, int y) {
        return id == RING ? String.format("10@%04X,%04X", x, y) : "11";
    }

    /** The placed rings' positions as a sorted multiset. */
    private static List<String> placed(TreeMap<Integer, String> slots) {
        return slots.values().stream().filter(v -> v.startsWith("10@")).sorted().toList();
    }

    private static long lostCount(TreeMap<Integer, String> slots) {
        return slots.values().stream().filter("11"::equals).count();
    }

    private static TreeMap<Integer, String> only(TreeMap<Integer, String> a, TreeMap<Integer, String> b) {
        TreeMap<Integer, String> out = new TreeMap<>(a);
        out.entrySet().removeIf(e -> e.getValue().equals(b.get(e.getKey())));
        return out;
    }

    private static int rowOf(String detailLine) {
        Matcher m = Pattern.compile(" row (\\d+):").matcher(detailLine);
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    private static List<Event> events(Path aux) throws Exception {
        List<Event> out = new ArrayList<>();
        for (String line : Files.readAllLines(aux)) {
            Matcher m = EVENT.matcher(line);
            if (m.find()) {
                boolean appeared = m.group(2).equals("appeared");
                out.add(new Event(Integer.parseInt(m.group(1)), appeared ? Kind.APPEARED : Kind.REMOVED,
                        Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4), 16),
                        m.group(5) != null ? Integer.parseInt(m.group(5), 16) : 0,
                        m.group(6) != null ? Integer.parseInt(m.group(6), 16) : 0));
                continue;
            }
            Matcher near = NEAR.matcher(line);
            if (near.find()) {
                out.add(new Event(Integer.parseInt(near.group(1)), Kind.NEAR, Integer.parseInt(near.group(2)), RING,
                        Integer.parseInt(near.group(3), 16), Integer.parseInt(near.group(4), 16)));
            }
        }
        return out;
    }

    /** Rows whose {@code cursor_state} turns the spawner around ({@code SpawnObjects_Forward}/{@code _Backward}). */
    private static List<Integer> reversalRows(Path aux) throws Exception {
        Pattern state = Pattern.compile("\"frame\":(\\d+),.*\"event\":\"cursor_state\".*\"dir\":\"([LR])\"");
        List<Integer> rows = new ArrayList<>();
        String previous = null;
        for (String line : Files.readAllLines(aux)) {
            if (!line.contains("\"cursor_state\"")) continue;
            Matcher m = state.matcher(line);
            if (!m.find()) continue;
            if (previous != null && !previous.equals(m.group(2))) rows.add(Integer.parseInt(m.group(1)));
            previous = m.group(2);
        }
        return rows;
    }
}
