package com.openggf.game.soniccd.flow;

import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A native reference run of the FLOW2 probe ({@code rehost-flow/<name>/frames.csv}: one row per BK2 row,
 * 68K RAM columns read after the frame) and its spec (inputs and setup pokes by BK2 row).
 */
final class CdNativeRun {
    final String name;
    private final List<Map<String, String>> rows = new ArrayList<>();

    private CdNativeRun(String name) { this.name = name; }

    static CdNativeRun load(String name) throws IOException {
        String configured = System.getProperty("soniccd.native.root");
        Path root = configured != null && !configured.isBlank() ? Path.of(configured)
                : Path.of(System.getProperty("soniccd.media.root", "."), "..", "native");
        Path csv = root.resolve("rehost-flow").resolve(name).resolve("frames.csv").normalize();
        Assumptions.assumeTrue(Files.isRegularFile(csv), "Native fixture absent: " + csv);
        CdNativeRun run = new CdNativeRun(name);
        List<String> lines = Files.readAllLines(csv);
        String[] header = lines.get(0).split(",", -1);
        for (int i = 1; i < lines.size(); i++) {
            String[] cells = lines.get(i).split(",", -1);
            Map<String, String> row = new HashMap<>();
            for (int c = 0; c < header.length; c++) row.put(header[c], c < cells.length ? cells[c] : "");
            run.rows.add(row);
        }
        return run;
    }

    /** BK2 row {@code r} (probe frame r + 1). */
    Map<String, String> row(int r) { return rows.get(r); }

    int size() { return rows.size(); }

    String text(int r, String column) { return row(r).get(column); }

    int dec(int r, String column) { return Integer.parseInt(text(r, column)); }

    int hex(int r, String column) { return (int) Long.parseLong(text(r, column), 16); }

    boolean lag(int r) { return dec(r, "lag") != 0; }

    /** The first row at or after {@code from} that satisfies {@code test}. */
    int first(int from, Predicate<Integer> test) {
        for (int r = from; r < rows.size(); r++) if (test.test(r)) return r;
        throw new AssertionError(name + ": no native row after " + from + " satisfies the condition");
    }

    /** Logic frames (rows the game polled the pad in) in [from, to). */
    int logicFrames(int from, int to) {
        int n = 0;
        for (int r = from; r < to; r++) if (!lag(r)) n++;
        return n;
    }

    /** {@code time} ({@code 00 mm ss ff}) as elapsed frames. */
    long timeFrames(int r) {
        int t = hex(r, "time");
        return ((t >> 16) & 0xFF) * 3600L + ((t >> 8) & 0xFF) * 60L + (t & 0xFF);
    }

    /** The pad held in row {@code r}: engine direction bits, action bits and Start. */
    int[] pad(int r) {
        int dirs = 0, actions = 0, start = 0;
        for (String b : text(r, "input").split("\\+")) {
            switch (b) {
                case "Up" -> dirs |= CdFlowHarness.UP;
                case "Down" -> dirs |= CdFlowHarness.DOWN;
                case "Left" -> dirs |= CdFlowHarness.LEFT;
                case "Right" -> dirs |= CdFlowHarness.RIGHT;
                case "A" -> actions |= CdFlowHarness.A;
                case "B" -> actions |= CdFlowHarness.B;
                case "C" -> actions |= CdFlowHarness.C;
                case "Start" -> start = 1;
                default -> { }
            }
        }
        return new int[] {dirs, actions, start};
    }
}
