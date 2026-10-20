package com.openggf.tools;

import com.openggf.capture.FfmpegEncoder;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestTempFiles;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@RequiresRom(SonicGame.SONIC_2)
class TestGameplayCaptureOutputIsolation {
    @Test
    void outputDirectoryWithLineFeedStillProducesTheCapturedVideo() throws Exception {
        assumeTrue(FfmpegEncoder.findFfmpeg().isPresent(), "ffmpeg required");
        Path directory;
        try {
            directory = TestTempFiles.createTempDirectory("capture-line\nbreak-'");
        } catch (InvalidPathException unsupportedName) {
            org.junit.jupiter.api.Assumptions.abort("filesystem does not support LF directory names: "
                    + unsupportedName.getReason());
            return;
        }
        var args = GameplayCaptureTool.Arguments.parse(new String[] {
                "--game", "s2", "--rom", RomTestUtils.ensureSonic2RomAvailable().getAbsolutePath(),
                "--zone", "ehz", "--act", "1", "--frames", "2", "--scale", "1",
                "--out-dir", directory.toString()});
        var report = GameplayCaptureTool.run(args);
        assertEquals(2, report.pngCount());
        assertNotNull(report.video(), report.videoNote());
        assertEquals("2", TestGameplayCaptureVideoEncoding.probe(report.video(), "nb_read_frames"));
    }

    @Test
    void rerunDoesNotIncludeFramesLeftByAnEarlierLongerCapture() throws Exception {
        assumeTrue(FfmpegEncoder.findFfmpeg().isPresent(), "ffmpeg required");
        Path directory = TestTempFiles.createTempDirectory("capture-output-isolation-");
        Path frames = Files.createDirectories(directory.resolve("frames"));
        Path stale = frames.resolve("00008.png");
        ScreenshotCapture.savePNG(new RgbaImage(320, 224, new int[320 * 224]), stale);
        var args = GameplayCaptureTool.Arguments.parse(new String[] {
                "--game", "s2", "--rom", RomTestUtils.ensureSonic2RomAvailable().getAbsolutePath(),
                "--zone", "ehz", "--act", "1", "--frames", "2", "--scale", "1",
                "--out-dir", directory.toString()});
        var report = GameplayCaptureTool.run(args);
        assertEquals(2, report.pngCount());
        assertNotNull(report.video(), report.videoNote());
        assertEquals("2", TestGameplayCaptureVideoEncoding.probe(report.video(), "nb_read_frames"),
                "the video must contain this invocation's frames, matching its report and state.csv");
        assertTrue(Files.exists(stale), "unselected files belong to their original invocation");
    }
}
