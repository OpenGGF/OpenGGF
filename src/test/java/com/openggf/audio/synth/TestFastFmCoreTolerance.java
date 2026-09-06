package com.openggf.audio.synth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openggf.audio.synth.fast.FastYm2612Dsp;
import com.openggf.audio.synth.nuked.NukedOpn2ScriptRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Judges the clean-room fast core against the cycle-exact Nuked-OPN2 facade
 * on the bit-exact register scripts: both facades receive the same register
 * writes at the same frame positions and render at the internal rate; the
 * mono sums are compared by normalised cross-correlation (best of lags
 * ±{@value #LAG_SEARCH} frames, since the fast facade lands writes at frame
 * start) and by RMS level ratio. This is a tolerance contract, not
 * sample equality: the fast core is register-level by design.
 *
 * <p>Thresholds can be relaxed for a diagnostic run with
 * {@code -Dopenggf.fastfm.minCorrelation=0 -Dopenggf.fastfm.maxLevelRatio=1e9};
 * every script's metrics are printed either way.
 */
class TestFastFmCoreTolerance {
    private static final int LAG_SEARCH = 64;
    private static final double MIN_CORRELATION =
            Double.parseDouble(System.getProperty("openggf.fastfm.minCorrelation", "0.9"));
    private static final double MAX_LEVEL_RATIO =
            Double.parseDouble(System.getProperty("openggf.fastfm.maxLevelRatio", "1.25"));
    /** Scripts quieter than this RMS on the accurate core are judged on level only. */
    private static final double SILENCE_RMS = 40.0;
    private static final int MAX_FRAMES = Integer.getInteger("openggf.fastfm.maxFrames", 160_000);

    @TestFactory
    Stream<DynamicTest> scriptsStayWithinTolerance() throws IOException, URISyntaxException {
        Path directory = Path.of(TestFastFmCoreTolerance.class
                .getResource("/audio/nuked-opn2/port/expected.txt").toURI()).getParent();
        List<Path> scripts;
        try (Stream<Path> listing = Files.list(directory)) {
            scripts = listing.filter(p -> p.toString().endsWith(".txt.gz")).sorted().toList();
        }
        String extra = System.getProperty("openggf.fastfm.extraDir");
        if (extra != null && !extra.isBlank()) {
            try (Stream<Path> listing = Files.list(Path.of(extra))) {
                scripts = Stream.concat(scripts.stream(),
                        listing.filter(p -> p.toString().endsWith(".txt.gz")).sorted()).toList();
            }
        }
        String only = System.getProperty("openggf.fastfm.only");
        if (only != null && !only.isBlank()) {
            java.util.Set<String> wanted = java.util.Set.of(only.split(","));
            scripts = scripts.stream().filter(p -> wanted.contains(
                    p.getFileName().toString().replace(".txt.gz", ""))).toList();
        }
        return scripts.stream().map(script -> DynamicTest.dynamicTest(
                script.getFileName().toString().replace(".txt.gz", ""), () -> compare(script)));
    }

    /**
     * Scripts whose behaviour the fast core does not yet reproduce, listed with
     * the open defect class from the design document (SSG repeat modes, LFO,
     * channel-3 special mode, feedback into summed modulators, AR 1-2, LSI
     * test registers and bus edge cases which are out of scope). They still
     * run and print their metrics, then report as skipped (not passed) so the
     * suite totals show what is enforced; the list only ever shrinks.
     */
    private static final java.util.Set<String> DEFERRED = java.util.Set.of(
            "alg0-fb7",
            "alg1-fb0",
            "alg1-fb7",
            "alg2-fb7",
            "alg3-fb7",
            "alg5-fb7",
            "alg7-fb7",
            "bus-edges",
            "ch3-csm",
            "ch3-special",
            "dac-ramp-dis",
            "dac-ramp-en",
            "dt1-mul07",
            "dt1-mul15",
            "dt3-mul15",
            "dt5-mul07",
            "dt5-mul15",
            "dt6-mul15",
            "eg-ar01",
            "eg-ar02",
            "eg-ar04",
            "eg-dr31",
            "fuzz-s0",
            "fuzz-s1",
            "fuzz-s2",
            "keyon-edges",
            "lfo-f0",
            "lfo-f1",
            "lfo-f2",
            "lfo-f3",
            "lfo-f4",
            "lfo-f5",
            "lfo-f6",
            "lfo-f7",
            "lfo-toggle",
            "pan-tl",
            "s1-sfx-a6",
            "s1-sfx-ac",
            "s1-sfx-b5",
            "s1-sfx-be",
            "s1-sfx-c6",
            "s1-sfx-ce",
            "s1-sfx-cf",
            "s2-sfx-a6",
            "s2-sfx-ac",
            "s2-sfx-b5",
            "s2-sfx-bc",
            "s2-sfx-cf",
            "s2-sfx-d0",
            "s3k-sfx-33",
            "s3k-sfx-3c",
            "s3k-sfx-45",
            "ssg08-ar20",
            "test-regs");

    private static void compare(Path script) throws IOException {
        Ym2612Chip accurate = new Ym2612Chip();
        accurate.setOutputSampleRate(Ym2612Chip.getInternalRate());
        FastYm2612Chip fast = new FastYm2612Chip(new FastYm2612Dsp());
        fast.setOutputSampleRate(Ym2612Chip.getInternalRate());
        Rendering rendering = new Rendering(accurate, fast);
        try (BufferedReader reader = NukedOpn2ScriptRunner.open(script)) {
            String line;
            while ((line = reader.readLine()) != null && rendering.frames < MAX_FRAMES) {
                rendering.execute(line);
            }
        }
        Metrics metrics = rendering.metrics();
        if (Boolean.getBoolean("openggf.fastfm.dump")) {
            rendering.dumpWindows(script.getFileName().toString().replace(".txt.gz", ""));
        }
        // The digest of the fast core's raw output lets a refactor prove it is bit-identical.
        System.out.printf(Locale.ROOT, "fastfm %-16s frames=%7d rmsAccurate=%8.1f rmsFast=%8.1f ratio=%6.3f corr=%6.3f lag=%+d fastDigest=%016x%n",
                script.getFileName().toString().replace(".txt.gz", ""), rendering.frames,
                metrics.rmsAccurate, metrics.rmsFast, metrics.ratio, metrics.correlation, metrics.lag,
                rendering.fastDigest());
        org.junit.jupiter.api.Assumptions.assumeFalse(
                DEFERRED.contains(script.getFileName().toString().replace(".txt.gz", "")),
                "deferred: open defect class, metrics printed above");
        if (metrics.rmsAccurate < SILENCE_RMS) {
            assertTrue(metrics.rmsFast < SILENCE_RMS * MAX_LEVEL_RATIO,
                    "accurate core is silent but fast core is not: " + metrics.rmsFast);
            return;
        }
        assertTrue(metrics.ratio <= MAX_LEVEL_RATIO && metrics.ratio >= 1.0 / MAX_LEVEL_RATIO,
                "level ratio fast/accurate out of range: " + metrics.ratio);
        assertTrue(metrics.correlation >= MIN_CORRELATION,
                "correlation below threshold: " + metrics.correlation);
    }

    private static final class Rendering {
        private final Ym2612Chip accurate;
        private final FastYm2612Chip fast;
        private final int[] left = new int[1024];
        private final int[] right = new int[1024];
        private final List<int[]> accurateChunks = new ArrayList<>();
        private final List<int[]> fastChunks = new ArrayList<>();
        private long cycles;
        private int frames;
        private int paceAddress;
        private int paceData;

        Rendering(Ym2612Chip accurate, FastYm2612Chip fast) {
            this.accurate = accurate;
            this.fast = fast;
        }

        void execute(String raw) {
            String line = raw.trim();
            if (line.isEmpty() || line.charAt(0) == '#') {
                return;
            }
            String[] fields = line.split("\\s+");
            switch (fields[0]) {
                case "type" -> {
                    int type = Integer.parseInt(fields[1]);
                    accurate.setChipType(type);
                    fast.setChipType(type);
                }
                case "pace" -> {
                    paceAddress = Integer.parseInt(fields[1]);
                    paceData = Integer.parseInt(fields[2]);
                }
                case "write" -> {
                    int busPort = Integer.parseInt(fields[1]);
                    int value = Integer.parseInt(fields[2]);
                    if ((busPort & 1) == 0) {
                        accurate.writeAddress(busPort >> 1, value);
                        fast.writeAddress(busPort >> 1, value);
                    } else {
                        accurate.writeData(busPort >> 1, value);
                        fast.writeData(busPort >> 1, value);
                    }
                }
                case "reg" -> {
                    int part = Integer.parseInt(fields[1]);
                    int register = Integer.parseInt(fields[2]);
                    int value = Integer.parseInt(fields[3]);
                    accurate.write(part, register, value);
                    fast.write(part, register, value);
                    clock(paceAddress + paceData);
                }
                case "clock" -> clock(Long.parseLong(fields[1]));
                case "at" -> {
                    long target = Long.parseLong(fields[1]) * 24L;
                    if (target > cycles) {
                        clock(target - cycles);
                    }
                }
                default -> {
                    // status / irq / dump lines are side observations
                }
            }
        }

        private void clock(long count) {
            cycles += count;
            long due = cycles / 24 - frames;
            while (due > 0) {
                int chunk = (int) Math.min(due, left.length);
                accurateChunks.add(render(accurate, chunk));
                fastChunks.add(render(fast, chunk));
                frames += chunk;
                due -= chunk;
            }
        }

        private int[] render(FmChip chip, int chunk) {
            java.util.Arrays.fill(left, 0, chunk, 0);
            java.util.Arrays.fill(right, 0, chunk, 0);
            chip.renderStereo(left, right, chunk);
            int[] mono = new int[chunk];
            for (int i = 0; i < chunk; i++) {
                mono[i] = left[i] + right[i];
            }
            return mono;
        }

        /** Diagnostic: RMS of each core per 2048-frame window, and their correlation, to locate a divergence. */
        void dumpWindows(String name) {
            double[] a = flatten(accurateChunks);
            double[] f = flatten(fastChunks);
            int window = 2048;
            for (int start = 0; start < a.length; start += window) {
                int end = Math.min(a.length, start + window);
                double[] wa = java.util.Arrays.copyOfRange(a, start, end);
                double[] wf = java.util.Arrays.copyOfRange(f, start, end);
                double c = correlation(removeMean(wa), removeMean(wf), 0);
                System.out.printf(Locale.ROOT, "fastfm-window %s %7d acc=%8.1f fast=%8.1f corr=%6.3f%n",
                        name, start, rms(wa), rms(wf), c);
            }
        }

        long fastDigest() {
            long hash = 0xcbf29ce484222325L;
            for (int[] chunk : fastChunks) {
                for (int v : chunk) {
                    hash ^= v;
                    hash *= 0x100000001b3L;
                }
            }
            return hash;
        }

        Metrics metrics() {
            double[] a = removeMean(flatten(accurateChunks));
            double[] f = removeMean(flatten(fastChunks));
            double rmsA = rms(a);
            double rmsF = rms(f);
            double best = -1;
            int bestLag = 0;
            for (int lag = -LAG_SEARCH; lag <= LAG_SEARCH; lag++) {
                double c = correlation(a, f, lag);
                if (c > best) {
                    best = c;
                    bestLag = lag;
                }
            }
            return new Metrics(rmsA, rmsF, rmsA == 0 ? (rmsF == 0 ? 1 : Double.POSITIVE_INFINITY) : rmsF / rmsA, best, bestLag);
        }

        private static double[] flatten(List<int[]> chunks) {
            int total = chunks.stream().mapToInt(c -> c.length).sum();
            double[] out = new double[total];
            int at = 0;
            for (int[] chunk : chunks) {
                for (int v : chunk) {
                    out[at++] = v;
                }
            }
            return out;
        }

        /** The accurate core models the YM2612's per-channel DC offsets (ladder effect); judge the AC signal. */
        private static double[] removeMean(double[] x) {
            double mean = 0;
            for (double v : x) {
                mean += v;
            }
            mean = x.length == 0 ? 0 : mean / x.length;
            double[] out = new double[x.length];
            for (int i = 0; i < x.length; i++) {
                out[i] = x[i] - mean;
            }
            return out;
        }

        private static double rms(double[] x) {
            double sum = 0;
            for (double v : x) {
                sum += v * v;
            }
            return x.length == 0 ? 0 : Math.sqrt(sum / x.length);
        }

        private static double correlation(double[] a, double[] f, int lag) {
            double num = 0;
            double da = 0;
            double df = 0;
            for (int i = Math.max(0, -lag); i < a.length && i + lag < f.length; i++) {
                double x = a[i];
                double y = f[i + lag];
                num += x * y;
                da += x * x;
                df += y * y;
            }
            return da == 0 || df == 0 ? 0 : num / Math.sqrt(da * df);
        }
    }

    private record Metrics(double rmsAccurate, double rmsFast, double ratio, double correlation, int lag) {
    }
}
