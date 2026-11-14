package com.openggf.game.soniccd.special;

import com.openggf.game.soniccd.CdDiscDataSource;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Optional user inputs for the special-stage checks: the disc ({@code -Dsoniccd.media.root})
 * and the native references recorded by {@code ~/Downloads/Sonic-CD/native/rehost-special}
 * ({@code -Dsoniccd.special.native.root}, defaulting to {@code <media.root>/../native/rehost-special}).
 * Each run directory holds the probe's {@code frames.csv} (one row per frame, read from the
 * special stage's own PRG RAM) and Word RAM / PRG RAM dumps at stated frames.
 */
final class CdSpecialNative {
    private CdSpecialNative() { }

    static CdDiscDataSource disc() throws IOException {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for Sonic CD disc checks");
        Path cue;
        try (Stream<Path> cues = Files.list(Path.of(root, "USA"))) {
            cue = cues.filter(p -> p.toString().endsWith(".cue")).findFirst()
                    .orElseThrow(() -> new IOException("No USA CUE under " + root));
        }
        return CdDiscDataSource.open(cue);
    }

    static Path run(String name) {
        String configured = System.getProperty("soniccd.special.native.root");
        Path root = configured != null && !configured.isBlank() ? Path.of(configured)
                : Path.of(System.getProperty("soniccd.media.root", "."), "..", "native", "rehost-special");
        Path dir = root.resolve(name).normalize();
        Assumptions.assumeTrue(Files.isRegularFile(dir.resolve("frames.csv")), "Native special-stage run absent: " + dir);
        return dir;
    }

    /** {@code frames.csv} rows keyed by 1-based probe frame. */
    static Map<Integer, Map<String, String>> frames(Path run) throws IOException {
        List<String> lines = Files.readAllLines(run.resolve("frames.csv"));
        String[] header = lines.get(0).split(",", -1);
        Map<Integer, Map<String, String>> rows = new HashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] cells = line.split(",", -1);
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < header.length && i < cells.length; i++) row.put(header[i], cells[i]);
            rows.put(Integer.parseInt(cells[0]), row);
        }
        return rows;
    }

    static byte[] dump(Path run, String kind, int frame) throws IOException {
        Path file = run.resolve(String.format("%s-%05d.bin", kind, frame));
        Assumptions.assumeTrue(Files.isRegularFile(file), "Native dump absent: " + file);
        return Files.readAllBytes(file);
    }

    static List<Integer> dumpFrames(Path run, String kind) throws IOException {
        List<Integer> frames = new ArrayList<>();
        try (Stream<Path> files = Files.list(run)) {
            files.map(p -> p.getFileName().toString()).filter(n -> n.startsWith(kind + "-") && n.endsWith(".bin"))
                    .sorted().forEach(n -> frames.add(Integer.parseInt(n.substring(kind.length() + 1, n.length() - 4))));
        }
        return frames;
    }

    /** A native run of one special stage: its directory, 0-based stage and the frame it starts after. */
    record Case(String run, int stage, int fromFrame, int ringsAtEnd, int ufosAtEnd, CdSpecialStageFlags flags, boolean ends) {
        @Override public String toString() { return run + " (stage " + (stage + 1) + ")"; }
        boolean demo() { return flags.demoInput(); }
        CdSpecialStageEntry entry() { return new CdSpecialStageEntry(stage, flags, 0); }
    }

    /**
     * Stage-select Special Stage 1 and the title attract Special Stage 6 (disc demo input), and
     * Special Stage 8 from the sound-test secret driven by a recorded pad schedule (README in each run).
     */
    static java.util.stream.Stream<Case> cases() {
        return java.util.stream.Stream.of(new Case("ss1-demo", 0, 0, 160, 0, CdSpecialStageFlags.DEMO, true),
                new Case("ss6-attract", 5, 23560, 60, 4, CdSpecialStageFlags.DEMO, true),
                new Case("ss8-soundtest", 7, 2500, 0, 6, CdSpecialStageFlags.SECRET, true),
                // Authored over Special Stage 8's terrain: big booster, spring, small booster, fan,
                // bumpers and a hazard; the run stops before the stage ends.
                new Case("ss8-terrain", 7, 2500, 0, 6, CdSpecialStageFlags.SECRET, false));
    }

    private static final Map<Path, List<int[]>> SCHEDULES = new HashMap<>();
    private static final java.util.regex.Pattern INPUT = java.util.regex.Pattern.compile(
            "\\{\\s*\"first\":\\s*(\\d+),\\s*\"last\":\\s*(\\d+),\\s*\"button\":\\s*\"(\\w+)\"\\s*}");

    /** The pad byte held in probe frame {@code frame} (Start, A, C, B, Right, Left, Down, Up from bit 7). */
    static int pad(Path run, int frame) throws IOException {
        List<int[]> inputs = SCHEDULES.get(run);
        if (inputs == null) {
            inputs = new ArrayList<>();
            var m = INPUT.matcher(Files.readString(run.resolve("schedule.json")));
            while (m.find()) {
                int bit = switch (m.group(3)) {
                    case "Up" -> 0x01; case "Down" -> 0x02; case "Left" -> 0x04; case "Right" -> 0x08;
                    case "B" -> 0x10; case "C" -> 0x20; case "A" -> 0x40; case "Start" -> 0x80;
                    default -> throw new IOException("Unknown button " + m.group(3));
                };
                inputs.add(new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), bit});
            }
            SCHEDULES.put(run, inputs);
        }
        int value = 0;
        for (int[] in : inputs) if (frame >= in[0] && frame <= in[1]) value |= in[2];
        return value;
    }

    /**
     * Update frames of the stage: frames where the sub-CPU timer counter changed after Sonic
     * spawned ({@code UpdateTimer} changes it every update, scddisasm/src/Special Stage/Sub.asm:1724-1727). Index 0 is the
     * update {@code Start} runs before the loop (Main.asm:146).
     */
    static List<Integer> updates(Map<Integer, Map<String, String>> frames, int fromFrame) {
        int first = frames.keySet().stream().filter(f -> f > fromFrame && frames.get(f).get("s_id").equals("1"))
                .min(Integer::compare).orElseThrow();
        List<Integer> updates = new ArrayList<>();
        for (int f = first + 1; frames.containsKey(f); f++) {
            if (!frames.get(f).get("timerFrames").equals(frames.get(f - 1).get("timerFrames"))) updates.add(f);
        }
        // The program ends after the last update; stale RAM never changes the counter again.
        return updates;
    }

    static int hex(Map<String, String> row, String column) {
        return (int) Long.parseLong(row.get(column), 16);
    }

    static int word(byte[] data, int at) {
        return ((data[at] & 0xFF) << 8) | (data[at + 1] & 0xFF);
    }
}
