package com.openggf.game.soniccd.fmv;

import com.openggf.game.soniccd.program.CdProgramScreenRenderer;
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
 * Native references for the video and ending programs: {@code -Dsoniccd.media.root=<dir with USA/*.cue>}, native
 * runs under {@code <media.root>/../native/rehost-programs} (or {@code -Dsoniccd.native.root}), recorded with
 * {@code tools/soniccd/fmv_native_probe.sh}. Also a software picture of a program frame for pixel checks.
 */
final class CdFmvNative {
    private CdFmvNative() { }

    static Path usaCue() throws IOException {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for Sonic CD disc checks");
        try (Stream<Path> cues = Files.list(Path.of(root, "USA"))) {
            return cues.filter(p -> p.toString().endsWith(".cue")).findFirst()
                    .orElseThrow(() -> new IOException("No USA CUE under " + root));
        }
    }

    static Path file(String relative) {
        String configured = System.getProperty("soniccd.native.root");
        Path root = configured != null && !configured.isBlank() ? Path.of(configured)
                : Path.of(System.getProperty("soniccd.media.root", "."), "..", "native");
        Path file = root.resolve("rehost-programs").resolve(relative).normalize();
        Assumptions.assumeTrue(Files.isRegularFile(file), "Native fixture absent: " + file);
        return file;
    }

    static byte[] bytes(String relative) throws IOException { return Files.readAllBytes(file(relative)); }

    /** frames.csv as column maps keyed by frame. */
    static Map<Integer, Map<String, String>> frames(String run) throws IOException {
        List<String> lines = Files.readAllLines(file(run + "/frames.csv"));
        String[] header = lines.get(0).split(",", -1);
        Map<Integer, Map<String, String>> out = new HashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] cells = line.split(",", -1);
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < header.length; i++) row.put(header[i], cells[i]);
            out.put(Integer.parseInt(cells[0]), row);
        }
        return out;
    }

    /** Frames where the main program started a new bank: frameCount 0 and the first section queued. */
    static List<Integer> bankStarts(Map<Integer, Map<String, String>> frames) {
        List<Integer> out = new ArrayList<>();
        int last = frames.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (int f = 2; f <= last; f++) {
            Map<String, String> row = frames.get(f), before = frames.get(f - 1);
            if (row == null || before == null) continue;
            boolean start = row.get("c080").equals("0000") && (row.get("c086").equals("0010") || row.get("c086").equals("0008"));
            boolean was = before.get("c086").equals("0010") || before.get("c086").equals("0008");
            if (start && !was) out.add(f);
        }
        return out;
    }

    /**
     * Frames where the file engine swapped banks: the sector count passes each packet's 75th sector only after the
     * main program took the bank ({@code .GfxDone}, then {@code .Advance}, SP.asm:836-858).
     */
    static List<Integer> swaps(Map<Integer, Map<String, String>> frames) {
        List<Integer> out = new ArrayList<>();
        int last = frames.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        int next = 75;
        boolean loading = false;
        for (int f = 1; f <= last; f++) {
            Map<String, String> row = frames.get(f);
            if (row == null) continue;
            if (!loading) {                                // the FMV load: opermode 3
                if (row.get("opermode").equals("0003")) loading = true; else continue;
            }
            int read = Integer.parseInt(row.get("secread"), 16);
            while (read >= next) { out.add(f); next += 75; }
            if (row.get("opermode").equals("0000") && read == 0) break;
        }
        return out;
    }

    /**
     * Where a program's native video memory holds what {@link CdProgramScreenState} keeps: the tiles below
     * {@code tileLimit}, plane A and plane B maps, and the scroll table. Native dumps are translated here (test side).
     */
    public record Layout(int tileLimit, int planeA, int planeB, int hscroll) {
        /** FMV/Main (Opening).asm VDPRegs (:567-584) and the ending's EndingPaletteData (Main (Ending).asm:714-722). */
        public static final Layout VIDEO = new Layout(0xA000, 0xC000, 0xA000, 0xE400);
        /** Sound Test.asm VDPRegs (:970-990) and Sound Test Image.asm VDPRegs (:122-142). */
        public static final Layout SECRETS = new Layout(0xC000, 0xC000, 0xE000, 0xF400);
        /** Best Staff Times.asm VDPRegs (:471-491). */
        public static final Layout BEST_TIMES = new Layout(0xB000, 0xC000, 0xE000, 0xB400);
    }

    /**
     * Differences between a program's screen state and a native dump translated into the same terms: tiles, both
     * planes' cells, both horizontal scrolls (scroll table), both vertical scrolls (VSRAM 0, 1) and the 64 palette
     * entries (CRAM). Native video memory outside these (the sprite table, unused tiles) is not compared.
     */
    static List<String> memoryDifferences(CdProgramScreenState state, String run, int frame, Layout layout) throws IOException {
        byte[] vram = bytes(String.format("%s/vram-%05d.bin", run, frame));
        byte[] cram = bytes(String.format("%s/cram-%05d.bin", run, frame));
        byte[] vsram = bytes(String.format("%s/vsram-%05d.bin", run, frame));
        List<String> diff = new ArrayList<>();
        int tileDiff = 0, first = -1;
        for (int i = 0; i < layout.tileLimit(); i++) if (state.tiles()[i] != vram[i]) { tileDiff++; if (first < 0) first = i; }
        if (tileDiff > 0) diff.add(String.format("tiles %d bytes from tile $%03X", tileDiff, first / 32));
        int columns = state.planeColumns(), rows = state.planeRows();
        for (CdProgramScreenState.Plane plane : CdProgramScreenState.Plane.values()) {
            int base = plane == CdProgramScreenState.Plane.A ? layout.planeA() : layout.planeB(), cells = 0;
            for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) {
                if (state.planeCell(plane, column, row) != word(vram, base + 2 * (row * columns + column))) cells++;
            }
            if (cells > 0) diff.add("plane " + plane + " " + cells + " cells");
            int h = word(vram, layout.hscroll() + (plane == CdProgramScreenState.Plane.A ? 0 : 2));
            if (state.hScroll(plane) != h) diff.add(String.format("hscroll %s %X/%X", plane, state.hScroll(plane), h));
            int v = word(vsram, plane == CdProgramScreenState.Plane.A ? 0 : 2);
            if (state.vScroll(plane) != v) diff.add(String.format("vscroll %s %X/%X", plane, state.vScroll(plane), v));
        }
        for (int i = 0; i < 64; i++) {
            int n = word(cram, 2 * i);
            if (state.palette(i) != n) diff.add(String.format("palette %d %03X/%03X", i, state.palette(i), n));
        }
        return diff;
    }

    private static int word(byte[] data, int at) { return ((data[at] & 0xFF) << 8) | (data[at + 1] & 0xFF); }

    /** The 320x224 picture of a program frame (planes only, no sprites) as packed RGB, BizHawk GPGX levels (34 per step). */
    static int[] picture(CdProgramScreenRenderer.Frame frame) {
        int width = frame.width();
        int[] out = new int[width * 224];
        int backdrop = rgb(frame.palette()[0]);
        java.util.Arrays.fill(out, frame.display() ? backdrop : 0);
        if (!frame.display()) return out;
        for (int priority = 0; priority < 2; priority++) {
            plane(frame, frame.planeB(), frame.scrollB(), frame.vscrollB(), priority == 1, out, width);
            plane(frame, frame.planeA(), frame.scrollA(), frame.vscrollA(), priority == 1, out, width);
        }
        return out;
    }

    private static void plane(CdProgramScreenRenderer.Frame frame, int[] map, java.util.function.IntUnaryOperator scroll,
                              int vscroll, boolean high, int[] out, int width) {
        for (int y = 0; y < 224; y++) {
            int py = (y + vscroll) & 0xFF;
            int value = scroll.applyAsInt(y) & 0x1FF;
            for (int x = 0; x < width; x++) {
                int px = (x - value) & 0x1FF;
                int word = map[(py >> 3) * 64 + (px >> 3)];
                if (((word & 0x8000) != 0) != high) continue;
                int tile = word & 0x7FF, line = (word >> 13) & 3;
                int tx = px & 7, ty = py & 7;
                if ((word & 0x800) != 0) tx = 7 - tx;
                if ((word & 0x1000) != 0) ty = 7 - ty;
                int b = frame.tiles()[tile * 32 + ty * 4 + (tx >> 1)] & 0xFF;
                int index = (tx & 1) == 0 ? b >> 4 : b & 0xF;
                if (index != 0) out[y * width + x] = rgb(frame.palette()[line * 16 + index]);
            }
        }
    }

    private static int rgb(int word) {
        int r = (word >> 1) & 7, g = (word >> 5) & 7, b = (word >> 9) & 7;
        return (level(r) << 16) | (level(g) << 8) | level(b);
    }

    private static int level(int v) { return v * 34; }

    /** A native post-frame screenshot (PPM, 320 wide) as packed RGB. */
    static int[] screenshot(String run, int frame) throws IOException {
        byte[] ppm = bytes(String.format("%s/postframe-%05d.ppm", run, frame));
        int at = 0, fields = 0, width = 0;
        while (fields < 4) {                               // P6, width, height, max
            while (Character.isWhitespace(ppm[at])) at++;
            int from = at;
            while (!Character.isWhitespace(ppm[at])) at++;
            if (fields == 1) width = Integer.parseInt(new String(ppm, from, at - from, java.nio.charset.StandardCharsets.US_ASCII));
            fields++;
        }
        at++;
        int[] out = new int[width * 224];
        for (int i = 0; i < out.length; i++) {
            out[i] = ((ppm[at] & 0xFF) << 16) | ((ppm[at + 1] & 0xFF) << 8) | (ppm[at + 2] & 0xFF);
            at += 3;
        }
        return out;
    }

    static int equalPixels(int[] a, int[] b) {
        if (a.length != b.length) return 0;
        int n = 0;
        for (int i = 0; i < a.length; i++) if (a[i] == b[i]) n++;
        return n;
    }
}
