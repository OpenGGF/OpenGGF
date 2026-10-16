package com.openggf.tools;

import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestSessionOutputPaths;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@code GameplayCaptureTool --audio --native-pcm} on the production presentation path:
 * one stage session, every completed NTSC frame drained exactly once (blip_buf's own sample
 * count for the frames the chip has crossed), and the ordinary 48 kHz capture still written.
 * Wiring evidence only; it asserts no waveform parity.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestNativePcmCaptureLive {

    private static final Path EMERALDS = Path.of(
            "src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/s3k-sonic-tails-complete-emeralds.bk2");
    private static final int FRAMES = 70;

    @Test
    void liveCaptureDrainsEveryCompletedNtscFrameFromOneSession() throws Exception {
        Path rom = RomTestUtils.ensureSonic3kRomAvailable().toPath();
        Path outDir = TestSessionOutputPaths.diagnostics("native-pcm-live");
        GameplayCaptureTool.Arguments arguments = GameplayCaptureTool.Arguments.parse(new String[] {
                "--game", "s3k", "--rom", rom.toString(), "--zone", "aiz", "--act", "1",
                "--sidekick", "tails", "--input", EMERALDS.toString(), "--input-start", "577",
                "--frames", Integer.toString(FRAMES), "--audio", "--native-pcm", "--no-video",
                "--stop-on-death", "false", "--out-dir", outDir.toString()});
        try {
            GameplayCaptureTool.run(arguments);
        } catch (IllegalStateException | UnsatisfiedLinkError glUnavailable) {
            assumeTrue(false, "offscreen GL unavailable: " + glUnavailable.getMessage());
            return;
        }
        List<String> rows = Files.readAllLines(outDir.resolve("native-pcm-frames.tsv"), StandardCharsets.UTF_8);
        assertEquals(FRAMES + 1, rows.size());
        long cumulative = Long.parseLong(rows.get(FRAMES).split("\t")[2]);
        for (String row : rows.subList(1, rows.size())) {
            assertEquals("1", row.split("\t")[3], "the presentation session was never rebuilt");
        }
        assertEquals(cumulative * 4, Files.size(outDir.resolve("native-pcm.s16")));
        int framesEnded = -1;
        for (int k = 0; k <= FRAMES + 1; k++) {
            if (blipSamplesForFrames(k) == cumulative) {
                framesEnded = k;
            }
        }
        assertTrue(framesEnded >= FRAMES - 2 && framesEnded <= FRAMES,
                "drained " + cumulative + " stereo frames is not blip_buf's count for a whole number of NTSC frames");
        assertEquals(FRAMES, Files.readAllLines(outDir.resolve("chip-writes.jsonl"), StandardCharsets.UTF_8).size());
        assertTrue(Files.size(outDir.resolve("audio.wav")) > 44, "ordinary 48 kHz capture still written");
    }

    @Test
    void mameCoreMovieIsRefusedBeforeBoot() {
        Path rom = RomTestUtils.ensureSonic3kRomAvailable().toPath();
        Path outDir = TestSessionOutputPaths.diagnostics("native-pcm-live-mame");
        GameplayCaptureTool.Arguments arguments = GameplayCaptureTool.Arguments.parse(new String[] {
                "--game", "s3k", "--rom", rom.toString(), "--zone", "aiz", "--act", "1",
                "--input", "src/test/resources/traces/s3k/_movies/s3k-complete-sonic-tails.bk2",
                "--frames", "2", "--audio", "--native-pcm", "--no-video", "--out-dir", outDir.toString()});
        assertThrows(IllegalArgumentException.class, () -> GameplayCaptureTool.run(arguments));
    }

    /** blip_buf samples for whole NTSC frames from a cleared buffer at 53,693,175 Hz to 44.1 kHz. */
    private static long blipSamplesForFrames(int frames) {
        double exact = (double) (1L << 52) * 44_100.0 / 53_693_175.0;
        long factor = (long) exact;
        if (factor < exact) {
            factor++;
        }
        long offset = factor / 2;
        long total = 0;
        for (int frame = 0; frame < frames; frame++) {
            offset += 896_040L * factor;
            long samples = offset >>> 52;
            total += samples;
            offset -= samples << 52;
        }
        return total;
    }
}
