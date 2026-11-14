package com.openggf.game.soniccd.fmv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** {@link CdFmvNative} for the program tests in other packages. */
public final class CdFmvNativeAccess {
    private CdFmvNativeAccess() { }

    public static final CdFmvNative.Layout VIDEO = CdFmvNative.Layout.VIDEO, SECRETS = CdFmvNative.Layout.SECRETS,
            BEST_TIMES = CdFmvNative.Layout.BEST_TIMES;

    public static Path usaCue() throws IOException { return CdFmvNative.usaCue(); }

    public static Map<Integer, Map<String, String>> frames(String run) throws IOException { return CdFmvNative.frames(run); }

    public static List<Integer> swaps(Map<Integer, Map<String, String>> frames) { return CdFmvNative.swaps(frames); }

    public static List<String> memoryDifferences(CdProgramScreenState state, String run, int frame, CdFmvNative.Layout layout)
            throws IOException {
        return CdFmvNative.memoryDifferences(state, run, frame, layout);
    }

    public static int pictureEqual(CdProgramScreenState vdp, String run, int frame) throws IOException {
        return CdFmvNative.equalPixels(CdFmvNative.picture(vdp.frame()), CdFmvNative.screenshot(run, frame));
    }

    public static int[] picture(CdProgramScreenState vdp) { return CdFmvNative.picture(vdp.frame()); }

    public static int[] screenshot(String run, int frame) throws IOException { return CdFmvNative.screenshot(run, frame); }

    public static List<Integer> dumpFrames(String run) throws IOException { return numbered(run, "vram-"); }

    public static List<Integer> screenshotFrames(String run) throws IOException { return numbered(run, "postframe-"); }

    private static List<Integer> numbered(String run, String prefix) throws IOException {
        Path dir = CdFmvNative.file(run + "/frames.csv").getParent();
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).filter(n -> n.startsWith(prefix))
                    .map(n -> Integer.parseInt(n.substring(prefix.length(), prefix.length() + 5))).sorted().toList();
        }
    }
}
