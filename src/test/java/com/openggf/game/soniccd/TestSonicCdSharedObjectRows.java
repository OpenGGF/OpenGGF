package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.objects.shared.SharedObjectCatalogue;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Position of the shared objects against the native full-act routes ({@code rehost-long/<payload>/trace/aux_state.jsonl},
 * comparison only): on every row before the stage's first divergence ({@link CdLongRoutePins}), where Sonic and the
 * camera equal the trace, each {@code object_near} entry of an ID that one of these classes holds in the stage (the
 * stage's {@code <routine>Id} catalogue column) has an engine object of that class at exactly its X and Y. The platform's fall and
 * rise forms, the crushers' cycles, the tube door's leaf, the seesaws' pads and Dango's walk are covered where the routes
 * reach them. {@code object_near} lists objects within 160 pixels of the player, so the check reads one way: an object
 * native holds near the player must exist in the engine at the same place; extra engine objects are not judged.
 */
class TestSonicCdSharedObjectRows {
    /** Catalogue column to the engine class that hosts that routine. */
    private static final Map<String, String> CLASS_BY_COLUMN = new LinkedHashMap<>();

    static {
        CLASS_BY_COLUMN.put("qqzPlatformId", "ObjQqzPlatform");
        CLASS_BY_COLUMN.put("wwzPlatformId", "ObjWwzPlatform");
        CLASS_BY_COLUMN.put("wwzCrusherId", "ObjWwzCrusher");
        CLASS_BY_COLUMN.put("crusherId", "ObjCrusher");
        CLASS_BY_COLUMN.put("mmzTubeDoorId", "ObjMmzTubeDoor");
        CLASS_BY_COLUMN.put("wwzTubeDoorId", "ObjWwzTubeDoor");
        CLASS_BY_COLUMN.put("dangoId", "ObjDango");
        CLASS_BY_COLUMN.put("mmzDangoId", "ObjDango");
        CLASS_BY_COLUMN.put("wwzSeesawId", "ObjWwzSeesaw");
        CLASS_BY_COLUMN.put("mmzSeesawId", "ObjMmzSeesaw");
    }

    private static final String PACKAGE = "com.openggf.game.soniccd.objects.shared.";

    private static final Pattern NEAR = Pattern.compile(
            "\"frame\":(\\d+),.*\"event\":\"object_near\",\"slot\":(\\d+),\"type\":\"0x([0-9A-F]+)\",\"x\":\"0x([0-9A-F]+)\","
                    + "\"y\":\"0x([0-9A-F]+)\"");

    @TempDir
    Path tempDir;

    static Stream<CdStagePayload> stages() {
        return CdLongRoutePins.FIRST_DIVERGENCE.keySet().stream();
    }

    /** One native entry. */
    private record Near(int slot, int type, int x, int y) { }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stages")
    void sharedObjectsAreWhereNativeHoldsThem(CdStagePayload payload) throws Exception {
        Path root = CdTestMedia.nativeFile("rehost-long/README.md").getParent();
        var fixture = CdFixtureReplay.Fixture.under(root, payload);
        org.junit.jupiter.api.Assumptions.assumeTrue(fixture.present(), "Native route absent: " + payload);
        Map<Integer, String> classByType = new LinkedHashMap<>();
        for (var e : CLASS_BY_COLUMN.entrySet()) {
            int id = SharedObjectCatalogue.offset(payload, e.getKey());
            if (id >= 0) classByType.put(id, PACKAGE + e.getValue());
        }
        // Fixed IDs of the Metallic Madness index (MmzObjects): the spin platform's track.
        if (payload.name().startsWith("R8")) classByType.put(0x2A, "com.openggf.game.soniccd.objects.mmz.MmzSpinPlatform");
        Map<Integer, List<Near>> nativeNear = new TreeMap<>();
        for (String line : Files.readAllLines(fixture.traceDir().resolve("aux_state.jsonl"))) {
            if (!line.contains("\"object_near\"")) continue;
            Matcher m = NEAR.matcher(line);
            if (!m.find()) continue;
            int type = Integer.parseInt(m.group(3), 16);
            if (!classByType.containsKey(type)) continue;
            nativeNear.computeIfAbsent(Integer.parseInt(m.group(1)), k -> new ArrayList<>())
                    .add(new Near(Integer.parseInt(m.group(2)), type, Integer.parseInt(m.group(4), 16),
                            Integer.parseInt(m.group(5), 16)));
        }
        // The recorder polls RAM after each hardware frame, so a level frame that overran its V-blank is sampled mid-pass (the
        // player has run, later slots have not); the engine steps whole level frames, so an engine row is compared with the last
        // native row that repeats its level frame counter (CdFixtureReplay.completedRow).
        com.openggf.trace.TraceData trace = com.openggf.trace.TraceData.load(fixture.traceDir());
        int last = CdLongRoutePins.FIRST_DIVERGENCE.get(payload) - 1;
        Map<String, int[]> tally = new TreeMap<>();      // class -> {entries compared, entries at the native place}
        List<String> misses = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, Files.createDirectories(tempDir.resolve(payload.name())), fixture, last, false,
                    -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void row(int row, AbstractPlayableSprite sonic) {
                            if (row > last) return;
                            List<Near> entries = nativeNear.get(CdFixtureReplay.completedRow(trace, row));
                            if (entries == null || row > last) return;
                            var active = GameServices.level().getObjectManager().getActiveObjects();
                            for (Near n : entries) {
                                String cls = classByType.get(n.type());
                                String shortName = cls.substring(cls.lastIndexOf('.') + 1);
                                int[] t = tally.computeIfAbsent(shortName, k -> new int[2]);
                                t[0]++;
                                boolean found = false;
                                String nearest = "none";
                                for (ObjectInstance o : active) {
                                    String name = o.getClass().getName();
                                    // The class itself or a class nested in it (the seesaw's pads).
                                    if (o.isDestroyed() || !(name.equals(cls) || name.startsWith(cls + "$"))) continue;
                                    if ((o.getX() & 0xFFFF) == n.x() && (o.getY() & 0xFFFF) == n.y()) { found = true; break; }
                                    if (nearest.equals("none") || Math.abs(o.getX() - n.x()) + Math.abs(o.getY() - n.y()) < 0x40) {
                                        nearest = String.format("%04X,%04X", o.getX() & 0xFFFF, o.getY() & 0xFFFF);
                                    }
                                }
                                if (found) t[1]++;
                                else if (misses.size() < 12) {
                                    misses.add(String.format("row %d %s (type %02X slot %d) native %04X,%04X; engine nearest %s",
                                            row, shortName, n.type(), n.slot(), n.x(), n.y(), nearest));
                                }
                            }
                        }
                    });
        }
        StringBuilder report = new StringBuilder(payload + " through row " + last + ":");
        tally.forEach((cls, t) -> report.append(String.format(" %s %d/%d", cls, t[1], t[0])));
        System.out.println("Shared object rows: " + report);
        Path out = Path.of("target", "soniccd-shared-rows");
        Files.createDirectories(out);
        Files.writeString(out.resolve(payload + ".txt"), report + "\n" + String.join("\n", misses) + "\n");
        int compared = tally.values().stream().mapToInt(t -> t[0]).sum();
        int matched = tally.values().stream().mapToInt(t -> t[1]).sum();
        assertEquals(compared, matched, report + "\n" + String.join("\n", misses));
    }
}
