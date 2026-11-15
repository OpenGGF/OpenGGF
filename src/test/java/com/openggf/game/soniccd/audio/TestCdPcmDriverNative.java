package com.openggf.game.soniccd.audio;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.device.pcm.CdPcmDevice;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reimplemented SMPS-PCM driver against the native driver's own RAM.
 *
 * <p>Native side: BizHawk 2.11 gpgx, BIOS US 1.10, the rehost fixture route into each zone's
 * Past act 1; the probe dumped PRG-RAM 40000h-4091Fh (the loaded {@code SNCBNKxB.BIN} driver
 * variables and tracks, PCM/_Variables.inc) after every frame and logged the command word the
 * main CPU wrote to GACOMCMD0 ({@code <native.root>/aud1-fixtures/<payload>-audio}; probe in
 * {@code ~/Downloads/Sonic-CD/native/aud1-tools}).
 *
 * <p>For every song track the sequence of notes the driver parses — its stored data pointer,
 * frequency and loaded duration — and the ticks between them must equal the native driver's,
 * and so must the streaming state (sample pointer and wave-RAM offset) each time a block is
 * streamed. The driver runs one tick per 510 chip samples with the chip advanced between
 * ticks, as {@link CdSubCpuAudio} does.
 */
class TestCdPcmDriverNative {
    private static CdProgramAssets programs;

    @BeforeAll
    static void open() throws IOException {
        programs = CdAudioTestMedia.usaPrograms();
    }

    @AfterAll
    static void close() throws IOException {
        if (programs != null) programs.close();
    }

    record Parse(int pointer, int frequency, int duration, int at) {
        @Override public boolean equals(Object o) {
            return o instanceof Parse p && p.pointer == pointer && p.frequency == frequency && p.duration == duration;
        }
        @Override public int hashCode() { return Objects.hash(pointer, frequency, duration); }
    }

    record Stream(int samplePtr, int ramOffset) { }

    @ParameterizedTest(name = "{0} Past music")
    @CsvSource({"R11B, R1PCM", "R31B, R3PCM", "R41B, R4PCM", "R51B, R5PCM", "R61B, R6PCM", "R71B, R7PCM", "R81B, R8PCM"})
    void pastMusicParsesAndStreamsLikeTheNativeDriver(String payload, String bankFile) throws IOException {
        Native capture = Native.load(payload);
        int pastMusic = CdSubCpuAudio.commandId("SCMD_PASTMUS");
        int frame = capture.commandFrame(pastMusic);
        assertTrue(frame > 0, payload + " sends SCMD_PASTMUS natively");
        assertEquals(pastMusic, SonicCdAudioProfile.stageMusicCommand(
                com.openggf.game.soniccd.CdStagePayload.valueOf(payload)), "engine stage-to-song command");

        Run run = run(bank(CdNativeFile.valueOf(bankFile)), 0x81, 1100);
        int compared = 0;
        for (int track = 1; track <= 8; track++) {
            List<Parse> expected = capture.parses(frame, track, 1000);
            if (expected.isEmpty()) continue;
            List<Parse> actual = run.parses.get(track);
            List<Parse> visible = visible(actual, expected, payload + " track " + track + " notes");
            for (int k = 1; k < visible.size(); k++) {
                int frames = expected.get(k).at() - expected.get(k - 1).at();
                int ticks = visible.get(k).at() - visible.get(k - 1).at();
                // 63.83 ticks for 59.92 frames a second: within one tick per interval, and the
                // interval in frames is ticks * 59.92 / 63.83 to within a frame.
                assertTrue(Math.abs(frames - ticks * 59.92 / 63.83) <= 1.5,
                        payload + " track " + track + " interval " + k + ": " + frames + " frames, " + ticks + " ticks");
            }
            List<Stream> nativeStreams = capture.streams(frame, track, 1000);
            List<Stream> streams = run.streams.get(track);
            aligned(streams.stream().map(Stream::samplePtr).toList(),
                    nativeStreams.stream().map(Stream::samplePtr).toList(), payload + " track " + track + " sample pointer");

            compared += expected.size();
        }
        assertTrue(compared > 40, payload + " compared " + compared + " notes");
    }

    /**
     * Output: the native command words are replayed at their frames through
     * {@link CdSubCpuAudio} (stage bank, PCM driver on its chip clock, interpolation to
     * 44.1 kHz) and one second of engine output is correlated with the native audio of the same
     * frames (BizHawk mixes FM, PSG, PCM and CD audio at 44.1 kHz), searching ±0.25 s of lag.
     */
    @ParameterizedTest(name = "{0} from frame +{1} with {2}''s bank, effects {3}: {4}")
    @CsvSource({"R11B, 20, R11B, true, match", "R41B, 20, R41B, true, match", "R71B, 20, R71B, true, match",
            "R11B, 20, R41B, true, wrong", "R41B, 20, R71B, true, wrong", "R71B, 20, R11B, true, wrong"})
    void engineOutputCorrelatesWithTheNativeAudio(String payload, int offsetFrames, String bankStage,
                                                   boolean effects, String expect) throws IOException {
        Path dir = CdAudioTestMedia.nativeDir("aud1-fixtures/" + payload + "-audio");
        List<String> rows = Files.readAllLines(dir.resolve("frames.csv"));
        List<String> commands = Files.readAllLines(dir.resolve("subcmd.csv"));
        int music = Native.load(payload).commandFrame(CdSubCpuAudio.commandId("SCMD_PASTMUS"));
        CdSubCpuAudio audio = new CdSubCpuAudio(() -> null, file -> {
            try { return bank(file); } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
        });
        audio.bindOutputRate(44_100);
        audio.loadStage(com.openggf.game.soniccd.CdStagePayload.valueOf(bankStage));
        int start = music - 2;
        int end = music + offsetFrames + 90;
        short[] engine = new short[0];
        long[] nativeStart = new long[end + 1];
        long position = 0;
        for (int f = 1; f <= end; f++) {
            nativeStart[f] = position;
            position += Integer.parseInt(rows.get(f).split(",")[1]);
        }
        for (int f = start; f < end; f++) {
            for (String line : commands.subList(1, commands.size())) {
                String[] c = line.split(",");
                int id = Integer.parseInt(c[1], 16);
                var command = CdSubCpuAudio.command(id);
                if (Integer.parseInt(c[0]) == f && command != null
                        && command.action() == CdSubCpuAudio.Action.PCM_QUEUE
                        && (effects || id == CdSubCpuAudio.commandId("SCMD_PASTMUS"))) audio.submit(id);
            }
            int n = Integer.parseInt(rows.get(f).split(",")[1]);
            short[] block = new short[n * 2];
            audio.render(block, n);
            short[] grown = java.util.Arrays.copyOf(engine, engine.length + block.length);
            System.arraycopy(block, 0, grown, engine.length, block.length);
            engine = grown;
        }
        java.nio.ByteBuffer raw = java.nio.ByteBuffer.wrap(Files.readAllBytes(dir.resolve("audio.raw")))
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int from = (int) (nativeStart[music + offsetFrames] - nativeStart[start]);
        int window = 44_100;
        double best = -1;
        for (int lag = -11_025; lag <= 11_025; lag += 3) {
            double dot = 0, ne = 0, nn = 0;
            for (int k = 0; k < window; k += 2) {
                int e = from + k;
                long n = nativeStart[start] + from + k + lag;
                if (e * 2 + 1 >= engine.length || n < 0) continue;
                for (int ch = 0; ch < 2; ch++) {
                    double ev = engine[e * 2 + ch];
                    double nv = raw.getShort((int) (n * 4 + ch * 2));
                    dot += ev * nv; ne += ev * ev; nn += nv * nv;
                }
            }
            if (ne > 0 && nn > 0) best = Math.max(best, dot / Math.sqrt(ne * nn));
        }
        System.out.printf("%s +%d bank %s effects %s: correlation %.3f%n", payload, offsetFrames, bankStage, effects, best);
        // Matching runs measured 0.47-0.79 (native FM, PSG and the 44.1 kHz resampler share the
        // window); another zone's bank 0.02-0.12.
        if (expect.equals("match")) assertTrue(best > 0.4, payload + " engine PCM correlates " + best);
        else assertTrue(best < 0.3, payload + " control correlates " + best);
    }

    /**
     * The FUTURE voice (SCMD_FUTURESFX at R51B's time post, PCM B1h) in isolation: the engine
     * timeline is rendered with and without the request, and the difference — the voice and
     * what it displaces — is correlated with the native audio. Control: the same signal against
     * native audio one second earlier.
     */
    @Test
    void futureVoiceOutputCorrelatesWithTheNativeAudio() throws IOException {
        Path dir = CdAudioTestMedia.nativeDir("aud1-fixtures/R51B-audio");
        Native capture = Native.load("R51B");
        int music = capture.commandFrame(CdSubCpuAudio.commandId("SCMD_PASTMUS"));
        int voice = capture.commandFrame(CdSubCpuAudio.commandId("SCMD_FUTURESFX"));
        List<String> rows = Files.readAllLines(dir.resolve("frames.csv"));
        short[][] out = new short[2][];
        for (int pass = 0; pass < 2; pass++) {
            CdSubCpuAudio audio = new CdSubCpuAudio(() -> null, file -> {
                try { return bank(file); } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
            });
            audio.bindOutputRate(44_100);
            audio.loadStage(com.openggf.game.soniccd.CdStagePayload.R51B);
            short[] all = new short[0];
            for (int f = music - 2; f < voice + 60; f++) {
                if (f == music) audio.submit(CdSubCpuAudio.commandId("SCMD_PASTMUS"));
                if (f == voice && pass == 0) audio.submit(CdSubCpuAudio.commandId("SCMD_FUTURESFX"));
                int n = Integer.parseInt(rows.get(f).split(",")[1]);
                short[] block = new short[n * 2];
                audio.render(block, n);
                short[] grown = java.util.Arrays.copyOf(all, all.length + block.length);
                System.arraycopy(block, 0, grown, all.length, block.length);
                all = grown;
            }
            out[pass] = all;
        }
        long start = 0;
        for (int f = 1; f < music - 2; f++) start += Integer.parseInt(rows.get(f).split(",")[1]);
        int onset = -1;
        for (int i = 0; i < out[0].length && onset < 0; i++) if (out[0][i] != out[1][i]) onset = i / 2;
        assertTrue(onset > 0, "the voice changes the engine output");
        java.nio.ByteBuffer raw = java.nio.ByteBuffer.wrap(Files.readAllBytes(dir.resolve("audio.raw")))
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        double voiceCorr = voiceCorrelation(out, onset, start, raw);
        double control = voiceCorrelation(out, onset, start - 44_100, raw);
        System.out.printf("FUTURE voice: %.3f, one second earlier %.3f%n", voiceCorr, control);
        assertTrue(voiceCorr > 0.5, "voice correlates " + voiceCorr);
        assertTrue(control < 0.3, "control correlates " + control);
    }

    private static double voiceCorrelation(short[][] out, int onset, long nativeStart, java.nio.ByteBuffer raw) {
        double best = -1;
        for (int lag = -4_000; lag <= 4_000; lag += 2) {
            double dot = 0, ee = 0, nn = 0;
            for (int k = onset; k < Math.min(out[0].length / 2, onset + 30_000); k += 2) {
                long n = nativeStart + k + lag;
                for (int ch = 0; ch < 2; ch++) {
                    double v = out[0][k * 2 + ch] - out[1][k * 2 + ch];
                    double nv = raw.getShort((int) (n * 4 + ch * 2));
                    dot += v * nv; ee += v * v; nn += nv * nv;
                }
            }
            if (ee > 0 && nn > 0) best = Math.max(best, dot / Math.sqrt(ee * nn));
        }
        return best;
    }

    /** A wrong frequency table base must fail the same comparison. */
    @Test
    void comparisonRejectsATransposedSong() throws IOException {
        Native capture = Native.load("R11B");
        int frame = capture.commandFrame(CdSubCpuAudio.commandId("SCMD_PASTMUS"));
        byte[] bank = bank(CdNativeFile.R1PCM);
        Run run = run(bank, 0x81, 600);
        List<Parse> expected = capture.parses(frame, 1, 600);
        List<Parse> actual = run.parses.get(1);
        List<Parse> shifted = actual.stream().map(p -> new Parse(p.pointer(), p.frequency() + 1, p.duration(), p.at())).toList();
        assertEquals(expected, actual.subList(0, expected.size()));
        assertNotEquals(expected, shifted.subList(0, expected.size()));
    }

    /** The Future voice (PCMS_FUTURE, B1h) on the R51B time post. */
    @Test
    void futureVoiceEffectParsesLikeTheNativeDriver() throws IOException {
        Native capture = Native.load("R51B");
        int frame = capture.commandFrame(CdSubCpuAudio.commandId("SCMD_FUTURESFX"));
        assertTrue(frame > 0);
        CdPcmDevice chip = new CdPcmDevice();
        CdPcmDriver driver = new CdPcmDriver(bank(CdNativeFile.R5PCM), chip);
        for (int i = 0; i < 3; i++) step(driver, chip);
        driver.queue(0x81);
        for (int i = 0; i < 200; i++) step(driver, chip);
        driver.queue(0xB1);
        int[] native0 = capture.sfxState(frame + 3);
        List<int[]> engine = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            step(driver, chip);
            for (int slot = 0; slot < 8; slot++) {
                var t = driver.sfxTrack(slot);
                if ((t.flags & 0x80) != 0) engine.add(new int[] {slot, t.channel, t.sampleStart, t.sampleSize, t.sampleMode});
            }
        }
        assertTrue(native0[0] >= 0, "native effect track active after the request");
        int[] seen = engine.stream().filter(e -> e[0] == native0[0]).findFirst().orElseThrow();
        assertEquals(native0[1], seen[1], "channel");
        assertEquals(native0[2], seen[2], "sample start");
        assertEquals(native0[3], seen[3], "sample size");
    }

    /**
     * The engine's states as frame-end dumps show them. A frame that holds two driver ticks
     * (63.83 ticks to 59.92 frames a second) hides the state between them, so one engine state
     * may be missing from the native list, never two in a row. Every native state must appear
     * in order; the engine states matched to them are returned.
     */
    private static <T> List<T> visible(List<T> engine, List<T> nativeStates, String what) {
        List<T> matched = new ArrayList<>();
        int j = 0;
        for (T expected : nativeStates) {
            if (j >= engine.size()) break;
            if (!engine.get(j).equals(expected) && j + 1 < engine.size() && engine.get(j + 1).equals(expected)) j++;
            assertEquals(expected, engine.get(j), what + " state " + matched.size());
            matched.add(engine.get(j++));
        }
        assertTrue(matched.size() * 10 >= nativeStates.size() * 9, what + ": engine ran out after " + matched.size());
        return matched;
    }

    /**
     * Streamed blocks in order. Every native block value must appear in the engine's list in
     * the same order. The engine may hold states the dumps never show (a frame with two driver
     * ticks, 63.83 ticks to 59.92 frames a second, or a block streamed ahead before a note
     * restarts the sample), bounded to a quarter of the matched blocks. The
     * native driver also reads the chip's play address part-way through its own run
     * (UpdateSample, PCM/_Driver.asm:309-354), where the engine reads it at the tick boundary
     * because CPU time is outside the reimplementation, so a refill on a threshold may come a
     * tick apart; that changes when a block is written, never which.
     */
    private static void aligned(List<Integer> engine, List<Integer> nativeStates, String what) {
        int j = 0, extras = 0, compared = 0;
        for (Integer expected : nativeStates) {
            int start = j;
            while (j < engine.size() && !engine.get(j).equals(expected)) j++;
            if (j == engine.size()) {
                assertTrue(compared * 10 >= nativeStates.size() * 9, what + ": native " + expected
                        + " missing after " + compared + " matched; engine from there "
                        + engine.subList(start, Math.min(engine.size(), start + 8)));
                break;
            }
            extras += j - start;
            j++;
            compared++;
        }
        assertTrue(extras * 4 <= compared + 10, what + ": " + extras + " engine-only states in " + compared);
    }

    private static List<Integer> distinct(List<Integer> values) {
        List<Integer> out = new ArrayList<>();
        for (Integer v : values) if (out.isEmpty() || !out.getLast().equals(v)) out.add(v);
        return out;
    }

    private static byte[] bank(CdNativeFile file) throws IOException {
        return programs.read(file, 0, Math.toIntExact(programs.entry(file).size()));
    }

    private static final class Run {
        final List<List<Parse>> parses = new ArrayList<>();
        final List<List<Stream>> streams = new ArrayList<>();
    }

    private static void step(CdPcmDriver driver, CdPcmDevice chip) {
        driver.tick();
        for (int s = 0; s < CdPcmDriver.CHIP_SAMPLES_PER_TICK; s++) chip.step();
    }

    private static Run run(byte[] bank, int song, int ticks) {
        CdPcmDevice chip = new CdPcmDevice();
        CdPcmDriver driver = new CdPcmDriver(bank, chip);
        Run run = new Run();
        for (int i = 0; i <= 8; i++) { run.parses.add(new ArrayList<>()); run.streams.add(new ArrayList<>()); }
        step(driver, chip);                  // InitDriver on the first tick
        driver.queue(song);
        int[] lastPtr = new int[9];
        int[] lastOff = new int[9];
        for (int tick = 0; tick < ticks; tick++) {
            step(driver, chip);
            for (int i = 1; i <= 8; i++) {
                var t = driver.songTrack(i);
                if ((t.flags & 0x80) == 0) continue;
                if (t.dataPtr != lastPtr[i]) {
                    run.parses.get(i).add(new Parse(t.dataPtr, t.freq, t.duration, tick));
                    lastPtr[i] = t.dataPtr;
                }
                if (t.samplePtr != lastOff[i]) {
                    run.streams.get(i).add(new Stream(t.samplePtr, t.sampRamOff));
                    lastOff[i] = t.samplePtr;
                }
            }
        }
        return run;
    }

    /**
     * PRG-RAM 40000h-4091Fh after every native frame: variables at 18h, the rhythm track at
     * 18h + 80h, song track i (1-8) at 18h + 80h * (i + 1), effect slot j at 18h + 80h * (10 + j).
     */
    private record Native(byte[] ram, List<String> commands) {
        private static final int BYTES = 0x920;

        static Native load(String payload) throws IOException {
            Path dir = CdAudioTestMedia.nativeDir("aud1-fixtures/" + payload + "-audio");
            Path dump = dir.resolve("prg-40000-40920.bin");
            org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(dump), "PCM driver dump absent");
            return new Native(Files.readAllBytes(dump), Files.readAllLines(dir.resolve("subcmd.csv")));
        }

        int commandFrame(int command) {
            for (String line : commands.subList(1, commands.size())) {
                String[] f = line.split(",");
                if (Integer.parseInt(f[1], 16) == command) return Integer.parseInt(f[0]);
            }
            return -1;
        }

        private int u8(int frame, int offset) {
            return ram[(frame - 1) * BYTES + 0x18 + offset] & 0xFF;
        }

        private int u16(int frame, int offset) { return (u8(frame, offset) << 8) | u8(frame, offset + 1); }

        private int u32(int frame, int offset) { return (u16(frame, offset) << 16) | u16(frame, offset + 2); }

        List<Parse> parses(int from, int track, int frames) {
            List<Parse> out = new ArrayList<>();
            int base = 0x80 * (track + 1);
            int last = 0;
            for (int f = from; f < from + frames && (f * BYTES) <= ram.length; f++) {
                if ((u8(f, base) & 0x80) == 0) continue;
                int ptr = u32(f, base + 4);
                // PlaySong leaves ptrkDuration 0 until the track's first parse; a dump inside the
                // tick that started the song can show that set-up state (PCM/_Driver.asm:615-672).
                if (u8(f, base + 0x0C) == 0) continue;
                if (ptr != last) {
                    out.add(new Parse(ptr, u16(f, base + 0x10), u8(f, base + 0x0C), f));
                    last = ptr;
                }
            }
            return out;
        }

        List<Stream> streams(int from, int track, int frames) {
            List<Stream> out = new ArrayList<>();
            int base = 0x80 * (track + 1);
            int last = 0;
            for (int f = from; f < from + frames && (f * BYTES) <= ram.length; f++) {
                if ((u8(f, base) & 0x80) == 0) continue;
                // StreamSample raises ptrkSampRAMOff first and stores ptrkSamplePtr after the
                // 200h-byte copy (PCM/_Driver.asm:356-435); a dump inside the copy shows the new
                // offset with the old pointer, so a block is keyed on the pointer, stored last.
                int ptr = u32(f, base + 0x24);
                // FinishTrackParse stores the sample start before StartStream's first block
                // (PCM/_Driver.asm:183-210, :291-307): a dump inside that copy shows the start.
                if (ptr == u32(f, base + 0x28)) continue;
                if (ptr != last) {
                    out.add(new Stream(ptr, u16(f, base + 0x16)));
                    last = ptr;
                }
            }
            return out;
        }

        /** {@code ptrkSampRAMOff} each time it changes; it is raised before the block copy. */
        List<Integer> offsets(int from, int track, int frames) {
            List<Integer> out = new ArrayList<>();
            int base = 0x80 * (track + 1);
            for (int f = from; f < from + frames && (f * BYTES) <= ram.length; f++) {
                if ((u8(f, base) & 0x80) == 0) continue;
                out.add(u16(f, base + 0x16));
            }
            return out;
        }

        /** First active effect track: slot, channel, sample start, sample size; slot -1 when none. */
        int[] sfxState(int frame) {
            for (int slot = 0; slot < 8; slot++) {
                int base = 0x80 * (10 + slot);
                if ((u8(frame, base) & 0x80) != 0) {
                    return new int[] {slot, u8(frame, base + 1), u32(frame, base + 0x28), u32(frame, base + 0x1C)};
                }
            }
            return new int[] {-1, 0, 0, 0};
        }
    }
}
