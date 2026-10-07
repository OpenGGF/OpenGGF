package com.openggf.tools.challenge;

import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.io.PixelImage;
import com.openggf.io.PngCodec;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Process-local normal-owner checkpoint/replay oracle, originating in the
 * 2026-10-07 Multigame prototype. Inputs are a supported ROM and an empty output
 * directory. Produces native PNGs, synchronized state/hash CSV and final 48kHz
 * stereo s16le PCM. No fixture values or arbitrary snapshot payloads are loaded.
 * Run in a fresh JVM in an empty owned working directory, like ChallengeWorker.
 */
public final class WorkerReplayDiagnostic {
    private static final int WARM_UP_LIMIT = 3000;
    private static final int REPLAY_TICKS = 36;

    private WorkerReplayDiagnostic() { }

    public static void main(String[] args) {
        try {
            Options options = Options.parse(args);
            if (!run(options)) System.exit(1);
        } catch (Exception | LinkageError failure) {
            failure.printStackTrace(System.err);
            System.exit(2);
        }
    }

    private static boolean run(Options options) throws IOException {
        // Create, rather than merge with, a prior capture. Inputs and comparisons
        // remain reproducible without quietly replacing earlier observations.
        Files.createDirectory(options.output());
        try (WorkerGameSession session = WorkerGameSession.open(options.game(), options.rom());
             BufferedWriter csv = Files.newBufferedWriter(options.output().resolve("state.csv"),
                     StandardCharsets.UTF_8)) {
            csv.write("pass,tick,held,mode,x,y,rings,vInt,levelFrame,polledMask,rgba_sha256,pcm_sha256\n");
            int warmup = 0, stable = 0;
            ChallengeProtocol.Frame checkpointFrame = session.initial(1, 0);
            while (stable < options.stableTicks() && warmup < WARM_UP_LIMIT) {
                checkpointFrame = session.step(1, ++warmup, 8);
                writeRow(csv, "warmup", warmup, 8, checkpointFrame);
                stable = session.diagnosticBoundaryStable() ? stable + 1 : 0;
            }
            if (stable < options.stableTicks())
                throw new IOException("Opening did not reach a stable rewind boundary within 3000 ticks");
            savePng(options.output().resolve("checkpoint.png"), checkpointFrame.rgba());
            var checkpoint = session.captureDiagnosticCheckpoint();
            List<Observation> forward = new ArrayList<>(REPLAY_TICKS);
            try (OutputStream pcm = Files.newOutputStream(options.output().resolve("forward.s16le"))) {
                for (int tick = 1; tick <= REPLAY_TICKS; tick++) {
                    var frame = session.step(1, warmup + tick, replayHeld(tick));
                    forward.add(new Observation(frame, session.captureDiagnosticGameplay()));
                    writeRow(csv, "forward", tick, replayHeld(tick), frame);
                    pcm.write(pcmBytes(frame.pcm()));
                }
            }
            savePng(options.output().resolve("forward-end.png"), forward.getLast().frame().rgba());
            session.restoreDiagnosticCheckpoint(checkpoint);
            List<String> differences = new ArrayList<>();
            int rgbaMatches = 0, pcmMatches = 0, nativeMatches = 0;
            ChallengeProtocol.Frame replay = null;
            try (OutputStream pcm = Files.newOutputStream(options.output().resolve("replay.s16le"))) {
                for (int tick = 1; tick <= REPLAY_TICKS; tick++) {
                    replay = session.step(1, warmup + tick, replayHeld(tick));
                    var expected = forward.get(tick - 1);
                    var nativeDiff = gameplayDifferences(expected.gameplay(), session.captureDiagnosticGameplay());
                    boolean rgba = Arrays.equals(expected.frame().rgba(), replay.rgba());
                    boolean sound = Arrays.equals(expected.frame().pcm(), replay.pcm());
                    boolean state = sameNativeFrame(expected.frame(), replay) && nativeDiff.isEmpty();
                    if (rgba) rgbaMatches++;
                    if (sound) pcmMatches++;
                    if (state) nativeMatches++;
                    if (differences.size() < 20 && (!rgba || !sound || !state)) {
                        String owner = !nativeDiff.isEmpty() ? nativeDiff.getFirst()
                                : !sameNativeFrame(expected.frame(), replay) ? "native-frame state"
                                : !rgba ? "GraphicsManager complete RGBA" : "AudioManager pre-focus PCM";
                        differences.add("tick " + tick + ": RGBA=" + rgba + " PCM=" + sound
                                + " native=" + state + " firstOwner=" + owner);
                    }
                    writeRow(csv, "replay", tick, replayHeld(tick), replay);
                    pcm.write(pcmBytes(replay.pcm()));
                }
            }
            savePng(options.output().resolve("replay-end.png"), replay.rgba());
            String report = "game=" + options.game() + "\nstableTicks=" + options.stableTicks()
                    + "\nwarmupTicks=" + warmup
                    + "\nreplayTicks=" + REPLAY_TICKS + "\nrgbaMatches=" + rgbaMatches
                    + "\npcmMatches=" + pcmMatches + "\nnativeMatches=" + nativeMatches
                    + "\npcmFormat=48000 Hz stereo signed 16-bit little-endian\n"
                    + String.join("\n", differences) + "\n";
            Files.writeString(options.output().resolve("result.txt"), report, StandardCharsets.UTF_8);
            System.out.print(report);
            return rgbaMatches == REPLAY_TICKS && pcmMatches == REPLAY_TICKS && nativeMatches == REPLAY_TICKS;
        }
    }

    // Only controller input is authored: hold right, tap A, release and resume.
    static int replayHeld(int tick) {
        if (tick < 1 || tick > REPLAY_TICKS) throw new IllegalArgumentException("Replay tick outside bounded script");
        return tick <= 4 ? 8 | 64 : tick >= 21 && tick <= 24 ? 0 : 8;
    }

    static boolean sameNativeFrame(ChallengeProtocol.Frame expected, ChallengeProtocol.Frame actual) {
        return expected.mode().equals(actual.mode()) && expected.x() == actual.x()
                && expected.y() == actual.y() && expected.rings() == actual.rings()
                && expected.vInt() == actual.vInt() && expected.levelFrame() == actual.levelFrame()
                && expected.polledMask() == actual.polledMask();
    }

    static List<String> gameplayDifferences(CompositeSnapshot expected, CompositeSnapshot actual) {
        List<String> differences = new ArrayList<>();
        if (!expected.entries().keySet().equals(actual.entries().keySet()))
            return List.of("Registry layout differs");
        for (String key : expected.entries().keySet()) {
            Object a = expected.get(key), b = actual.get(key);
            if (!RewindSnapshotDiff.keyEquals(key, a, b) && !Objects.deepEquals(a, b)) {
                var detail = RewindSnapshotDiff.diffKey(key, a, b);
                // Equality is authoritative even when a private engine record's
                // accessor cannot provide a printable field-level diagnostic.
                differences.addAll(detail.isEmpty() ? List.of(key + ": captured owner state differs") : detail);
            }
            if (differences.size() >= 20) return List.copyOf(differences.subList(0, 20));
        }
        return differences;
    }

    private static void writeRow(BufferedWriter csv, String pass, int tick, int held,
                                 ChallengeProtocol.Frame frame) throws IOException {
        csv.write(pass + "," + tick + "," + held + "," + frame.mode() + "," + frame.x() + ","
                + frame.y() + "," + frame.rings() + "," + frame.vInt() + "," + frame.levelFrame()
                + "," + frame.polledMask() + "," + hash(frame.rgba()) + "," + hash(pcmBytes(frame.pcm())) + "\n");
    }

    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static byte[] pcmBytes(short[] pcm) {
        byte[] bytes = new byte[pcm.length * 2];
        for (int i = 0; i < pcm.length; i++) {
            bytes[i * 2] = (byte) pcm[i];
            bytes[i * 2 + 1] = (byte) (pcm[i] >>> 8);
        }
        return bytes;
    }

    private static void savePng(Path path, byte[] rgba) throws IOException {
        int[] argb = new int[ChallengeProtocol.WIDTH * ChallengeProtocol.HEIGHT];
        for (int i = 0; i < argb.length; i++) {
            int at = i * 4;
            argb[i] = (rgba[at + 3] & 255) << 24 | (rgba[at] & 255) << 16
                    | (rgba[at + 1] & 255) << 8 | rgba[at + 2] & 255;
        }
        PngCodec.write(path, new PixelImage(ChallengeProtocol.WIDTH, ChallengeProtocol.HEIGHT, argb));
    }

    private record Observation(ChallengeProtocol.Frame frame, CompositeSnapshot gameplay) { }

    record Options(String game, Path rom, Path output, int stableTicks) {
        static Options parse(String[] args) {
            if ((args.length != 6 && args.length != 8) || !"--game".equals(args[0]) || !"--rom".equals(args[2])
                    || !"--output".equals(args[4]))
                throw new IllegalArgumentException("Expected --game s1|s2|s3k --rom absolute --output new-absolute-directory [--stable-ticks 1..120]");
            if (!List.of("s1", "s2", "s3k").contains(args[1])) throw new IllegalArgumentException("Unsupported game");
            Path rom = Path.of(args[3]), output = Path.of(args[5]);
            if (!rom.isAbsolute() || !output.isAbsolute()) throw new IllegalArgumentException("Paths must be absolute");
            int stableTicks = 10;
            if (args.length == 8) {
                if (!"--stable-ticks".equals(args[6])) throw new IllegalArgumentException("Unknown diagnostic option");
                stableTicks = Integer.parseInt(args[7]);
                if (stableTicks < 1 || stableTicks > 120)
                    throw new IllegalArgumentException("Stable tick count must be 1..120");
            }
            return new Options(args[1], rom, output, stableTicks);
        }
    }
}
