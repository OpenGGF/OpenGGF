package com.openggf.tools;

import com.openggf.capture.FfmpegEncoder;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tests.TestTempFiles;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestGameplayCaptureVideoEncoding {
    @Test
    void nonDividingStrideKeepsTheExactCaptureFrameRate() throws Exception {
        Path ffmpeg = FfmpegEncoder.findFfmpeg().orElse(null);
        assumeTrue(ffmpeg != null, "ffmpeg required");
        Path directory = TestTempFiles.createTempDirectory("capture-video-stride-'");
        Path frames = Files.createDirectories(directory.resolve("frames"));
        var pngs = new ArrayList<Path>();
        for (int i = 0; i < 3; i++) {
            Path png = frames.resolve(String.format("%05d.png", i * 7));
            ScreenshotCapture.savePNG(new RgbaImage(2, 2, new int[4]),
                    png);
            pngs.add(png);
        }
        var args = GameplayCaptureTool.Arguments.parse(new String[] {
                "--game", "s2", "--zone", "ehz", "--act", "1", "--out-dir", directory.toString(),
                "--every", "7", "--fps", "60", "--scale", "1"});
        Path video = GameplayCaptureTool.encodeVideo(ffmpeg, pngs, directory.resolve("capture.mp4"), args);
        assertNotNull(video);
        assertEquals("60/7", probe(video, "r_frame_rate"),
                "seven simulation frames between PNGs must remain 7/60 seconds in the video");
    }

    static String probe(Path video, String field) throws Exception {
        Process process = new ProcessBuilder("ffprobe", "-v", "error", "-count_frames", "-select_streams", "v:0",
                "-show_entries", "stream=" + field, "-of", "default=nw=1:nk=1", video.toString())
                .redirectErrorStream(true).start();
        assertTrue(process.waitFor(20, TimeUnit.SECONDS), "ffprobe completed");
        String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertEquals(0, process.exitValue(), result);
        return result;
    }
}
