package com.openggf.game.soniccd.audio;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage music against the native game, one stage per zone plus both Palmtree Panic futures.
 *
 * <p>Native side: BizHawk 2.11 gpgx with BIOS US 1.10 played each rehost fixture route
 * ({@code <native.root>/rehost-fixtures/<payload>/schedule.json}); the probe recorded the
 * core's audio and the command word in d0 whenever the main CPU wrote GACOMCMD0
 * ({@code <native.root>/aud1-fixtures/<payload>-audio}; tools in
 * {@code ~/Downloads/Sonic-CD/native/aud1-tools}). The engine side is the payload's
 * {@link SonicCdAudioProfile#stageMusicCommand}, the generated command table and the
 * {@link CdCddaPlayer} reading the disc.
 *
 * <p>Two checks: the engine sends the command word the native game wrote, and the engine's
 * rendered CD audio correlates with the native audio after that command (best lag within the
 * native seek time, which the engine does not model).
 */
class TestCdStageMusicNative {
    private static CdProgramAssets programs;

    @BeforeAll
    static void open() throws IOException {
        programs = CdAudioTestMedia.usaPrograms();
    }

    @AfterAll
    static void close() throws IOException {
        if (programs != null) programs.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"R11A", "R31A", "R41A", "R51A", "R61A", "R71A", "R81A", "R11C", "R11D"})
    void stageStartPlaysTheDiscTrackTheNativeGamePlays(String name) throws IOException {
        CdStagePayload payload = CdStagePayload.valueOf(name);
        Path dir = CdAudioTestMedia.nativeDir("aud1-fixtures/" + name + "-audio");
        int command = SonicCdAudioProfile.stageMusicCommand(payload);
        int commandFrame = nativeCommandFrame(dir, command);
        assertTrue(commandFrame > 0, String.format("native %s wrote engine command %02X", name, command));

        short[] engine = render(command, 44_100 * 3);
        double best = bestCorrelation(dir, commandFrame, engine);
        assertTrue(best > 0.85, String.format("%s: engine CD audio correlates %.3f with native", name, best));
    }

    /** A neighbouring track must not pass, or the correlation could not tell tracks apart. */
    @ParameterizedTest
    @ValueSource(strings = {"R11A", "R41A"})
    void aNeighbouringTrackDoesNotMatchTheNativeAudio(String name) throws IOException {
        CdStagePayload payload = CdStagePayload.valueOf(name);
        Path dir = CdAudioTestMedia.nativeDir("aud1-fixtures/" + name + "-audio");
        int command = SonicCdAudioProfile.stageMusicCommand(payload);
        int commandFrame = nativeCommandFrame(dir, command);
        short[] wrong = render(command + 1, 44_100 * 3);
        double best = bestCorrelation(dir, commandFrame, wrong);
        assertTrue(best < 0.6, String.format("%s: the next command's track correlates %.3f", name, best));
    }

    private static short[] render(int command, int frames) {
        CdSubCpuAudio audio = new CdSubCpuAudio(() -> new CdCddaPlayer.TrackSource() {
            @Override public long programFrames(int track) { return programs.audioTrackFrames(track); }
            @Override public byte[] readFrames(int track, long first, int count) throws IOException {
                return programs.readAudioFrames(track, first, count);
            }
        });
        audio.bindOutputRate(44_100);
        assertTrue(audio.submit(command));
        short[] out = new short[frames * 2];
        audio.render(out, frames);
        return out;
    }

    /** Frame of the first GACOMCMD0 write carrying {@code command}. */
    private static int nativeCommandFrame(Path dir, int command) throws IOException {
        for (String line : Files.readAllLines(dir.resolve("subcmd.csv")).subList(1, Files.readAllLines(dir.resolve("subcmd.csv")).size())) {
            String[] f = line.split(",");
            if (Integer.parseInt(f[1], 16) == command) return Integer.parseInt(f[0]);
        }
        return -1;
    }

    /**
     * Normalised correlation of one second of engine audio (both channels) with the native audio
     * at the lag where a 2048-sample window one second into the render matches best, searched
     * over four seconds after the command frame.
     */
    private static double bestCorrelation(Path dir, int commandFrame, short[] engine) throws IOException {
        List<String> rows = Files.readAllLines(dir.resolve("frames.csv"));
        long start = 0;
        for (int i = 1; i < commandFrame; i++) start += Integer.parseInt(rows.get(i).split(",")[1]);
        ByteBuffer raw = ByteBuffer.wrap(Files.readAllBytes(dir.resolve("audio.raw"))).order(ByteOrder.LITTLE_ENDIAN);
        int nativeFrames = raw.capacity() / 4;
        int window = 2048;
        int offset = 44_100;
        double[] e = new double[window];
        double eNorm = 0;
        for (int k = 0; k < window; k++) {
            e[k] = engine[(offset + k) * 2];
            eNorm += e[k] * e[k];
        }
        eNorm = Math.sqrt(eNorm);
        double best = -1;
        long bestBase = -1;
        for (int lag = 0; lag < 44_100 * 4; lag++) {
            long base = start + offset + lag;
            if (base + 44_100 >= nativeFrames) break;
            double dot = 0;
            double nNorm = 0;
            for (int k = 0; k < window; k++) {
                double n = raw.getShort((int) ((base + k) * 4));
                dot += n * e[k];
                nNorm += n * n;
            }
            double c = nNorm > 0 ? dot / (Math.sqrt(nNorm) * eNorm) : -1;
            if (c > best) {
                best = c;
                bestBase = base;
            }
        }
        double dot = 0, nNorm = 0, en = 0;
        for (int k = 0; k < 44_100 && offset + k < engine.length / 2; k++) {
            for (int ch = 0; ch < 2; ch++) {
                double n = raw.getShort((int) ((bestBase + k) * 4 + ch * 2));
                double v = engine[(offset + k) * 2 + ch];
                dot += n * v;
                nNorm += n * n;
                en += v * v;
            }
        }
        return dot / Math.sqrt(nNorm * en);
    }
}
