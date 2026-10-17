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
        List<String> ledger = Files.readAllLines(outDir.resolve("chip-writes.jsonl"), StandardCharsets.UTF_8);
        assertEquals(FRAMES + 1, ledger.size(), "boot record plus one line per presented frame");
        assertTrue(ledger.get(0).startsWith("{\"frame\":-1,\"writes\":[[")
                        && ledger.get(0).contains("[\"ym\",0,43,0]"),
                "the session's driver-initialisation program (ending in its 2Bh = 0) is recorded at boot");
        assertTrue(Files.size(outDir.resolve("audio.wav")) > 44, "ordinary 48 kHz capture still written");
    }

    /**
     * Cadence regression (round-2 lane pcm-output, 2026-10-16): each production presentation must advance the
     * sound hardware by exactly one ROM hardware frame, 3420 * 262 = 896,040 master clocks at 53,693,175 Hz.
     * The first real window measured 600 presentations completing only 599 NTSC frames (800 samples at 48 kHz =
     * 894,886.2 master clocks each). With the budget right, every presentation crosses exactly one hardware-frame
     * boundary (the chip's fixed resampler look-ahead only sets the phase), the carry never drifts, and the
     * ordinary 48 kHz presentation still delivers 800 samples per presentation.
     */
    @Test
    void everyPresentationAdvancesExactlyOneHardwareFrame() throws Exception {
        Path rom = RomTestUtils.ensureSonic3kRomAvailable().toPath();
        Path outDir = TestSessionOutputPaths.diagnostics("native-pcm-cadence");
        int presentations = 600;
        GameplayCaptureTool.Arguments arguments = GameplayCaptureTool.Arguments.parse(new String[] {
                "--game", "s3k", "--rom", rom.toString(), "--zone", "aiz", "--act", "1",
                "--sidekick", "tails", "--input", EMERALDS.toString(), "--input-start", "577",
                "--frames", Integer.toString(presentations), "--audio", "--native-pcm", "--no-video",
                "--stop-on-death", "false", "--out-dir", outDir.toString()});
        try {
            GameplayCaptureTool.run(arguments);
        } catch (IllegalStateException | UnsatisfiedLinkError glUnavailable) {
            assumeTrue(false, "offscreen GL unavailable: " + glUnavailable.getMessage());
            return;
        }
        List<String> rows = Files.readAllLines(outDir.resolve("native-pcm-frames.tsv"), StandardCharsets.UTF_8);
        assertEquals(presentations + 1, rows.size());
        long cumulative = Long.parseLong(rows.get(presentations).split("\t")[2]);
        assertEquals(blipSamplesForFrames(presentations), cumulative,
                "600 presentations must complete 600 NTSC hardware frames");
        for (String row : rows.subList(1, rows.size())) {
            int drained = Integer.parseInt(row.split("\t")[1]);
            assertTrue(drained == 735 || drained == 736,
                    "presentation " + row.split("\t")[0] + " crossed " + drained + " samples, not one hardware frame");
        }
        // Control: the ordinary presentation is still 800 stereo frames of 48 kHz per presentation.
        assertEquals(44 + (long) presentations * 800 * 4, Files.size(outDir.resolve("audio.wav")));
    }

    /**
     * Same-game level entry must not reset the sound hardware (round-2 lane pcm-output, 2026-10-16). From the
     * production title start, the emeralds movie leaves DATA_SELECT for AIZ1 inside the window (the capture's own
     * state log must show it; frame 208 when this was written, 238 once the title ran its ROM fades)
     * ({@code NativeDataSelectLaunchFlow.launch} -> fresh level load -> {@code LevelManager.configureAudio}), which
     * re-installs the same ROM and the same audio profile. The ROM's Z80 driver keeps running into the level, so
     * the presentation device (chip, filter, PSG and buffer state) must survive: one stage session, one hardware
     * frame per presentation, across the handoff.
     */
    @Test
    void dataSelectLevelEntryKeepsThePresentationDevice() throws Exception {
        Path rom = RomTestUtils.ensureSonic3kRomAvailable().toPath();
        Path outDir = TestSessionOutputPaths.diagnostics("native-pcm-data-select-entry");
        int presentations = 260;
        GameplayCaptureTool.Arguments arguments = GameplayCaptureTool.Arguments.parse(new String[] {
                "--game", "s3k", "--rom", rom.toString(), "--zone", "aiz", "--act", "1", "--title-screen",
                "--sidekick", "tails", "--input", EMERALDS.toString(), "--input-start", "0",
                "--frames", Integer.toString(presentations), "--audio", "--native-pcm", "--no-video",
                "--stop-on-death", "false", "--out-dir", outDir.toString()});
        try {
            GameplayCaptureTool.run(arguments);
        } catch (IllegalStateException | UnsatisfiedLinkError glUnavailable) {
            assumeTrue(false, "offscreen GL unavailable: " + glUnavailable.getMessage());
            return;
        }
        List<String> rows = Files.readAllLines(outDir.resolve("native-pcm-frames.tsv"), StandardCharsets.UTF_8);
        assertEquals(presentations + 1, rows.size());
        for (String row : rows.subList(1, rows.size())) {
            assertEquals("1", row.split("\t")[3], "presentation session rebuilt at frame " + row.split("\t")[0]);
        }
        long cumulative = Long.parseLong(rows.get(presentations).split("\t")[2]);
        assertEquals(blipSamplesForFrames(presentations), cumulative,
                "the device kept one hardware frame per presentation across the level entry");
        List<String> states = Files.readAllLines(outDir.resolve("state.csv"), StandardCharsets.UTF_8);
        int modeColumn = List.of(states.get(0).split(",")).indexOf("mode");
        List<String> modes = states.subList(1, states.size()).stream().map(row -> row.split(",")[modeColumn]).toList();
        int levelEntry = modes.indexOf("LEVEL");
        assertTrue(levelEntry > 0 && "DATA_SELECT".equals(modes.get(levelEntry - 1)),
                "the window crosses the DATA_SELECT -> LEVEL entry it is a witness for; modes changed at "
                        + levelEntry);
    }

    /**
     * Title_Screen's first request is {@code cmd_FadeOut} (sonic3k.asm:5393-5394). The Z80 driver's first service
     * after {@code zInitAudioDriver} runs it through {@code zFadeOutMusic} -> {@code zHaltDACPSG} ->
     * {@code zPSGSilenceAll} (Sound/Z80 Sound Driver.asm:2307-2325, 2587-2597), so the four PSG silence writes
     * follow the DAC idle loop's 2Bh = 0, as in the native startup ledger. The 68000's earlier power-on PSG
     * writes ({@code Init_InputPSG}, sonic3k.asm:177-180) have no engine owner and are not asserted here.
     */
    @Test
    void titleStartServicesTitleScreensFadeOutAfterTheDriverInit() throws Exception {
        Path rom = RomTestUtils.ensureSonic3kRomAvailable().toPath();
        Path outDir = TestSessionOutputPaths.diagnostics("native-pcm-title-fade");
        GameplayCaptureTool.Arguments arguments = GameplayCaptureTool.Arguments.parse(new String[] {
                "--game", "s3k", "--rom", rom.toString(), "--zone", "aiz", "--act", "1", "--title-screen",
                "--sidekick", "tails", "--input", EMERALDS.toString(), "--input-start", "0",
                "--frames", "2", "--audio", "--native-pcm", "--no-video",
                "--stop-on-death", "false", "--out-dir", outDir.toString()});
        try {
            GameplayCaptureTool.run(arguments);
        } catch (IllegalStateException | UnsatisfiedLinkError glUnavailable) {
            assumeTrue(false, "offscreen GL unavailable: " + glUnavailable.getMessage());
            return;
        }
        StringBuilder writes = new StringBuilder();
        for (String line : Files.readAllLines(outDir.resolve("chip-writes.jsonl"), StandardCharsets.UTF_8)) {
            if (line.startsWith("{\"frame\":-1,") || line.startsWith("{\"frame\":0,")) {
                writes.append(line, line.indexOf("\"writes\":[") + 10, line.lastIndexOf("]}")).append(',');
            }
        }
        String startup = writes.toString();
        int init = startup.indexOf("[\"ym\",0,39,0]");
        assertTrue(init >= 0, "zStopAllSound's closing 27h = 0 is recorded: " + startup);
        assertTrue(startup.startsWith("[\"ym\",0,43,0],[\"psg\",159],[\"psg\",191],[\"psg\",223],[\"psg\",255],",
                        init + "[\"ym\",0,39,0],".length()),
                "the idle loop's 2Bh = 0, then cmd_FadeOut's zPSGSilenceAll, follow the driver init: " + startup);
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
