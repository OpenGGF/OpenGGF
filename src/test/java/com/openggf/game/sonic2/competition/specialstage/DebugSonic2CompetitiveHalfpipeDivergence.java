package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageViewport;
import com.openggf.game.sonic2.competition.Sonic2CompetitiveSpecialStageAdapter;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.NativeReplayLagBoundary;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Purpose: compare the production standalone 2P halfpipe with native physical rows.
 * Inputs: s2comp.ss.refs (generic execution observer state.csv/events.csv),
 * s2comp.ss.movie (original BK2), s2comp.ss.out (report directory). Missing inputs skip.
 * Capture schema: tools/bizhawk/capture_execution_observations.lua; state fields
 * mode:F600/1, vint:FE0C/4, objects:B000/8192, track:DB00/176, global:FE00/432.
 * Event fields include mode:F600/1; callbacks FadeWhite:$24E8, RunObjects:$15F9C,
 * Vint_Lag:$484. The caller's explicit plan owns recording bounds/checkpoints.
 * Origin: lane F round4, 2026-11-08; native REV01, fixBugs=0, original special-route.
 * The FadeWhite callback identifies the production load boundary, not gameplay values.
 * CSV/RAM are comparison-only: no hydration, lag admission, skipping or re-alignment.
 * A successful diagnostic means it ran; read its report for parity/frontier limits.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_2)
class DebugSonic2CompetitiveHalfpipeDivergence {
    private static final Path DEFAULT = Path.of("/tmp/s2comp-analysis-20261107/native-refs");

    @Test void compareOriginalStandaloneMovie() throws Exception {
        Path refs = Path.of(System.getProperty("s2comp.ss.refs", DEFAULT.resolve("lane-f-r5/ss-warm").toString()));
        Path moviePath = Path.of(System.getProperty("s2comp.ss.movie", DEFAULT.resolve("special-route.bk2").toString()));
        assumeTrue(Files.isRegularFile(refs.resolve("state.csv")) && Files.isRegularFile(refs.resolve("events.csv"))
                && Files.isRegularFile(moviePath), "external native special-stage observations/BK2 absent");
        var raw = csv(refs.resolve("state.csv"));
        var events = csv(refs.resolve("events.csv"));
        int anchor = events.stream().filter(e -> e.get("event").equals("FadeWhite") && hex(e.get("mode")) == 16)
                .mapToInt(e -> Integer.parseInt(e.get("inflight_frame"))).findFirst().orElseThrow();
        var rows = new TreeMap<Integer, Map<String, String>>();
        for (var row : raw) assertNull(rows.put(Integer.parseInt(row.get("frame")), row), "duplicate physical sample");
        assertTrue(rows.containsKey(anchor), "FadeWhite anchor must have a completed-frame observation");
        var movie = new Bk2MovieLoader().load(moviePath);
        int last = Math.min(rows.lastKey(), movie.getFrameCount());
        var clocks = clocks(rows, events);
        var lag = NativeReplayLagBoundary.first(clocks, anchor + 1, last, "body_entries",
                (before, after) -> before.get("mode") == 16 && after.get("mode") == 16
                        && after.get("lag_callback") == 1);
        int end = lag == null ? last : lag.frame() - 1;
        Path out = Path.of(System.getProperty("s2comp.ss.out", "target/s2comp-special-divergence"));
        Files.createDirectories(out);
        var report = new StringBuilder("purpose=comparison only; source=" + refs + " movie=" + moviePath
                + "\nload boundary=FadeWhite physical " + anchor + " (SpecialStage / Pal_FadeFromWhite)\n"
                + "native lag boundary=" + lag + "; never execute that input or re-align\n");
        GameServices.audio().setBackend(new NullAudioBackend());
        var graphics = new GraphicsManager(); graphics.initHeadless();
        var vint = new AtomicInteger(); var results = new Results();
        int compared = 0, exactThrough = anchor - 1; String first = null;
        var window = new ArrayDeque<String>();
        try (var halfpipe = new Sonic2CompetitiveHalfpipe(new CompetitiveHalfpipeRomData(TestEnvironment.currentRom()),
                graphics, SpecialStageViewport.nativeViewport(), AudioManager.createSilent(), vint::get)) {
            halfpipe.loadCompetitiveStage(0, results);
            var input = new InputHandler();
            int remaining = -1;
            for (int physical = anchor; physical <= end; physical++) {
                if (physical > anchor) {
                    int index = physical - 1;
                    input.setLogicalOverride(RecordedInputSnapshots.fromBk2(movie.getFrame(index), movie.getFrame(index - 1)));
                    vint.incrementAndGet(); // own production wait clock; never seeded from observations
                    halfpipe.step(input);
                }
                var expected = nativeSample(rows.get(physical));
                var actual = engineSample(halfpipe, results);
                String breakField = System.getProperty("s2comp.ss.breakField");
                if (physical == anchor && breakField != null) expected.compute(breakField, (key, value) -> Objects.requireNonNull(value) + 1);
                var differences = difference(actual, expected);
                String line = "frame=" + physical + " phase=" + halfpipe.phase() + " actual=" + actual + " native=" + expected;
                window.addLast(line); while (window.size() > 7) window.removeFirst();
                compared++;
                if (first == null && !differences.isEmpty()) {
                    first = "frame=" + physical + " " + differences;
                    report.append("first divergence: ").append(first).append('\n');
                    window.forEach(value -> report.append(value).append('\n'));
                    remaining = 3;
                } else if (first != null) {
                    report.append(line).append('\n'); if (--remaining == 0) break;
                } else exactThrough = physical;
            }
            report.append("raw V-int: engine origin=0; native anchor=").append(hex(rows.get(anchor).get("vint")))
                    .append("; origin offset reported separately, never supplied to gameplay\n");
        } finally { graphics.cleanup(); }
        String result = "exact through " + exactThrough;
        if (first == null && lag != null) result += "; native lag at " + lag.frame() + " (unsupported: native CPU overrun)";
        else if (first != null) result += "; " + first;
        report.append("compared=").append(compared).append(' ').append(result).append('\n');
        Files.writeString(out.resolve("special-route.txt"), report);
        System.out.println("SPECIAL_FRONTIER compared=" + compared + " " + result + " nativeLag=" + lag);
    }

    @Test void comparisonDetectsEveryRecordedScalarAndRejectsTruncatedRows() throws Exception {
        var row = new LinkedHashMap<String, String>();
        byte[] objectRam = new byte[8192]; objectRam[8 * 64] = 0x60; objectRam[9 * 64] = 0x61;
        row.put("objects", HexFormat.of().formatHex(objectRam)); row.put("track", "00".repeat(176)); row.put("global", "00".repeat(432));
        var expected = nativeSample(row);
        assertTrue(expected.keySet().containsAll(List.of("p1_angle", "p2_ssz", "speed_current", "archive5",
                "item8_depth", "item9_routine", "item127_id")), "both actors, checkpoint and ring/bomb slots are observed");
        assertEquals("", difference(expected, expected));
        for (var key : expected.keySet()) {
            var broken = new LinkedHashMap<>(expected); broken.put(key, expected.get(key) + 1);
            assertTrue(difference(expected, broken).startsWith(key + " engine="), "deliberately broken comparison: " + key);
        }
        row.put("objects", "00"); assertThrows(IllegalArgumentException.class, () -> nativeSample(row));
    }

    private static NavigableMap<Integer, Map<String, Long>> clocks(NavigableMap<Integer, Map<String, String>> rows,
            List<Map<String, String>> events) {
        var entries = new TreeMap<Integer, Integer>(); var lag = new HashSet<Integer>();
        for (var event : events) {
            int frame = Integer.parseInt(event.get("inflight_frame"));
            if (event.get("event").equals("RunObjects")) entries.merge(frame, 1, Integer::sum);
            if (event.get("event").equals("Vint_Lag")) lag.add(frame);
        }
        int count = 0; var result = new TreeMap<Integer, Map<String, Long>>();
        for (var entry : rows.entrySet()) {
            int frame = entry.getKey(); count += entries.getOrDefault(frame, 0);
            result.put(frame, Map.of("body_entries", (long) count, "vint", hex(entry.getValue().get("vint")),
                    "mode", hex(entry.getValue().get("mode")), "lag_callback", lag.contains(frame) ? 1L : 0L));
        }
        return result;
    }

    private static LinkedHashMap<String, Long> nativeSample(Map<String, String> row) {
        byte[] objects = bytes(row, "objects", 8192), track = bytes(row, "track", 176), global = bytes(row, "global", 432);
        var result = new LinkedHashMap<String, Long>();
        for (int slot = 0; slot < 2; slot++) {
            int base = slot * 64; String p = "p" + (slot + 1) + "_";
            for (var field : List.of(new Field("angle", 38, 1), new Field("ssx", 42, 2), new Field("ssy", 46, 2),
                    new Field("ssz", 52, 2), new Field("x", 8, 2), new Field("y", 12, 2), new Field("inertia", 20, -2),
                    new Field("vx", 16, -2), new Field("vy", 18, -2), new Field("ssxsub", 44, 2), new Field("ssysub", 48, 2),
                    new Field("hurt", 54, 1), new Field("slide", 55, 1)))
                result.put(p + field.name(), number(objects, base + field.offset(), field.size()));
            result.put(p + "rings", number(global, slot == 0 ? 0x20 : 0xD0, 2));
        }
        for (var field : List.of(new Field("track_frame", 16, 1), new Field("track_geometry", 8, 1),
                new Field("track_segment", 10, 1), new Field("track_anim_frame", 11, 1), new Field("track_drawing", 13, 1),
                new Field("track_duration", 31, 1), new Field("track_anim_timer", 33, 1), new Field("speed_new", 18, 4),
                new Field("speed_current", 22, 4))) result.put(field.name(), number(track, field.offset(), field.size()));
        // SS_2P_BCD_Score / SS_NoCheckpoint_flag / SS_Checkpoint_Rainbow_flag
        // are DB93 / DB95 / DB98 (s2.constants.asm:2043-2056).
        for (var field : List.of(new Field("started", 35, 1), new Field("complete", 0x86, 1),
                new Field("no_checkpoint", 0x95, 1), new Field("rainbow", 0x98, 1)))
            result.put(field.name(), number(track, field.offset(), field.size()) == 0 ? 0L : 1L);
        result.put("wins_p1", (number(track, 0x93, 1) >>> 4) & 15); result.put("wins_p2", number(track, 0x93, 1) & 15);
        for (int index = 0; index < 6; index++) result.put("archive" + index, number(global, 0x1A0 + index * 2, 2));
        for (int slot = 0; slot < 128; slot++) {
            int base = slot * 64, id = objects[base] & 255; String p = "item" + slot + "_";
            result.put(p + "id", id == 0x60 || id == 0x61 ? (long) id : 0L);
            if (id != 0x60 && id != 0x61) continue;
            for (var field : List.of(new Field("angle", 38, 1), new Field("depth", 48, 4), new Field("x", 8, 2),
                    new Field("y", 12, 2), new Field("routine", 36, 1), new Field("anim", 28, 1), new Field("mapping", 26, 1)))
                result.put(p + field.name(), number(objects, base + field.offset(), field.size()));
        }
        return result;
    }

    private static LinkedHashMap<String, Long> engineSample(Sonic2CompetitiveHalfpipe halfpipe, Results results) throws Exception {
        var result = new LinkedHashMap<String, Long>(); var snapshot = halfpipe.capture();
        for (int slot = 0; slot < 2; slot++) {
            var player = halfpipe.player(slot); String p = "p" + (slot + 1) + "_";
            Object state = component(snapshot.players(), slot == 0 ? "sonic" : "tails");
            put(result, p + "angle", player.getAngle()); put(result, p + "ssx", player.getSSXPos() & 65535);
            put(result, p + "ssy", player.getSSYPos() & 65535); put(result, p + "ssz", player.getSSZPos() & 65535);
            put(result, p + "x", player.getXPos() & 65535); put(result, p + "y", player.getYPos() & 65535);
            put(result, p + "inertia", (short) player.getInertia());
            for (var field : Map.of("vx", "xVel", "vy", "yVel", "ssxsub", "ssXSub", "ssysub", "ssYSub").entrySet())
                put(result, p + field.getKey(), ((Number) component(state, field.getValue())).longValue());
            put(result, p + "hurt", player.getHurtTimer()); put(result, p + "slide", player.getSlideTimer());
            put(result, p + "rings", player.getRings());
        }
        var track = snapshot.track();
        put(result, "track_frame", track.frame()); put(result, "track_geometry", track.geometry());
        put(result, "track_segment", track.segment()); put(result, "track_anim_frame", track.animationFrame());
        put(result, "track_drawing", track.drawingIndex()); put(result, "track_duration", track.durationTimer());
        put(result, "track_anim_timer", track.animationTimer());
        // Competitive track is the shipped fixed speed12 table (no Obj59 emerald slowdown).
        // SpecialStage (s2.asm:6537-6642) requests $C0000; track owns its ROM-derived duration.
        put(result, "speed_new", 0xC0000); put(result, "speed_current", 0xC0000);
        var machine = snapshot.machine();
        put(result, "started", machine.started() ? 1 : 0); put(result, "complete", machine.complete() ? 1 : 0);
        put(result, "no_checkpoint", machine.noCheckpoint() ? 1 : 0); put(result, "rainbow", machine.rainbow() ? 1 : 0);
        put(result, "wins_p1", machine.checkpoint().p1Wins()); put(result, "wins_p2", machine.checkpoint().p2Wins());
        for (int index = 0; index < 6; index++) put(result, "archive" + index, results.rings[index]);
        for (var slot : machine.ram().slots()) {
            var node = slot.state(); String p = "item" + slot.index() + "_";
            Object item = node != null ? node.item() : null;
            put(result, p + "id", item == null ? 0 : Boolean.TRUE.equals(component(item, "bomb")) ? 0x61 : 0x60);
            if (item == null) continue;
            put(result, p + "angle", ((Number) component(item, "angle")).longValue());
            put(result, p + "depth", Integer.toUnsignedLong(((Number) component(item, "depthFixed")).intValue()));
            put(result, p + "x", ((Number) component(item, "x")).intValue() & 65535);
            put(result, p + "y", ((Number) component(item, "y")).intValue() & 65535);
            String state = component(item, "state").toString(); put(result, p + "routine", state.equals("ACTIVE") ? 2 : 6);
            put(result, p + "anim", ((Number) component(item, "animation")).longValue());
            put(result, p + "mapping", ((Number) component(item, "mappingFrame")).longValue());
        }
        return result;
    }

    private static String difference(Map<String, Long> actual, Map<String, Long> expected) {
        var result = new ArrayList<String>();
        for (var field : expected.entrySet()) if (!Objects.equals(actual.get(field.getKey()), field.getValue()))
            result.add(field.getKey() + " engine=" + actual.get(field.getKey()) + " native=" + field.getValue());
        for (var key : actual.keySet()) if (!expected.containsKey(key)) result.add(key + " missing native observation");
        return String.join("; ", result);
    }
    private record Field(String name, int offset, int size) { }
    private static Object component(Object record, String name) throws Exception {
        var method = record.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(record);
    }
    private static void put(Map<String, Long> map, String key, long value) { map.put(key, value); }
    private static long hex(String value) { return Long.parseUnsignedLong(value, 16); }
    private static byte[] bytes(Map<String, String> row, String key, int length) {
        byte[] result = HexFormat.of().parseHex(Objects.requireNonNull(row.get(key)));
        if (result.length != length) throw new IllegalArgumentException("Expected " + length + " bytes in " + key);
        return result;
    }
    private static long number(byte[] bytes, int offset, int length) {
        long result = 0; for (int i = 0; i < Math.abs(length); i++) result = result << 8 | bytes[offset + i] & 255;
        return length == -2 ? (short) result : result;
    }
    private static List<Map<String, String>> csv(Path path) throws Exception {
        var lines = Files.readAllLines(path); if (lines.size() < 2) throw new IllegalArgumentException("Empty CSV: " + path);
        String[] header = lines.getFirst().split(","); var result = new ArrayList<Map<String, String>>();
        for (String line : lines.subList(1, lines.size())) {
            String[] values = line.split(",", -1); if (values.length != header.length) throw new IllegalArgumentException("Malformed CSV: " + path);
            var row = new LinkedHashMap<String, String>(); for (int i = 0; i < header.length; i++) row.put(header[i], values[i]); result.add(row);
        }
        return result;
    }
    private static final class Results implements Sonic2CompetitiveSpecialStageAdapter.Events {
        final int[] rings = new int[6];
        public void collectedRing(int slot) { }
        public void hitBomb(int slot) { }
        public void checkpoint() { }
        public void archivedSector(int sector, int p1, int p2) { rings[sector * 2] = p1; rings[sector * 2 + 1] = p2; }
        public void tiedSector(int sector) { }
        public void stageComplete() { }
    }
}
