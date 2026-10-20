package com.openggf.tools;

import com.openggf.capture.FfmpegEncoder;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestTempFiles;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@RequiresRom(SonicGame.SONIC_2)
class TestGameplayCaptureVideoEncoding {
    @Test
    void nonDividingStrideKeepsTheExactCaptureFrameRate() throws Exception {
        Path ffmpeg = FfmpegEncoder.findFfmpeg().orElse(null);
        assumeTrue(ffmpeg != null, "ffmpeg required");
        Path directory = TestTempFiles.createTempDirectory("capture-video-stride-'");
        var args = GameplayCaptureTool.Arguments.parse(new String[] {
                "--game", "s2", "--rom", RomTestUtils.ensureSonic2RomAvailable().getAbsolutePath(),
                "--zone", "ehz", "--act", "1", "--frames", "15", "--out-dir", directory.toString(),
                "--every", "7", "--fps", "60", "--scale", "1"});
        var report = GameplayCaptureTool.run(args);
        assertEquals(3, report.pngCount());
        assertNotNull(report.video(), report.videoNote());
        assertEquals("60/7", probe(report.video(), "r_frame_rate"),
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
