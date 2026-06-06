package com.openggf.capture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FfmpegEncoderCommandTest {

    @Test
    void phase1CommandHasRawVideoInputAndScaledFfv1Output() {
        List<String> cmd = FfmpegEncoder.phase1Command(
                "ffmpeg", Path.of("/tmp/v.mkv"), 320, 224, 60, 4);
        // raw rgba stdin input
        assertTrue(cmd.contains("rawvideo"));
        assertEquals("rgba", cmd.get(cmd.indexOf("-pix_fmt") + 1));
        assertEquals("320x224", cmd.get(cmd.indexOf("-s") + 1));
        assertEquals("60", cmd.get(cmd.indexOf("-r") + 1));
        assertEquals("pipe:0", cmd.get(cmd.indexOf("-i") + 1));
        // vflip + 4x nearest-neighbor scale
        String vf = cmd.get(cmd.indexOf("-vf") + 1);
        assertTrue(vf.contains("vflip"), vf);
        assertTrue(vf.contains("scale=1280:896:flags=neighbor"), vf);
        // ffv1 video codec, output last
        assertEquals("ffv1", cmd.get(cmd.indexOf("-c:v") + 1));
        assertTrue(cmd.get(cmd.size() - 1).endsWith("v.mkv"));
    }

    @Test
    void phase2MuxCopiesVideoAndEncodesFlac() {
        List<String> cmd = FfmpegEncoder.phase2MuxCommand(
                "ffmpeg", Path.of("/tmp/v.mkv"), Path.of("/tmp/a.raw"),
                48000, Path.of("/out/final.mkv"));
        assertEquals("copy", cmd.get(cmd.indexOf("-c:v") + 1));
        assertEquals("flac", cmd.get(cmd.indexOf("-c:a") + 1));
        // raw s16le audio input declared
        assertTrue(cmd.contains("s16le"));
        assertEquals("48000", cmd.get(cmd.indexOf("-ar") + 1));
        assertEquals("2", cmd.get(cmd.indexOf("-ac") + 1));
        assertTrue(cmd.get(cmd.size() - 1).endsWith("final.mkv"));
    }

    @Test
    void findFfmpegReturnsEmptyForBogusPath() {
        assertTrue(FfmpegEncoder.findFfmpegOnPath("").isEmpty());
    }

    @Test
    void finishClosesAudioOutputBeforeCheckingVideoExit() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/capture/FfmpegEncoder.java"));

        assertFalse(source.contains("if (vexit != 0) throw new CaptureException(\"ffmpeg video exited \" + vexit);\n"
                        + "            audioOut.close();"),
                "finish must close audioOut even when the video ffmpeg process exits non-zero");
        assertTrue(source.contains("closeAudioOut();"),
                "finish should route audio cleanup through a guarded helper");
    }
}
