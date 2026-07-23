package com.openggf.capture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveCaptureMediaSmokeTest {
    private static final int WIDTH = 18;
    private static final int HEIGHT = 10;
    private static final int FRAME_RATE = 30;
    private static final int SAMPLE_RATE = 48_000;
    private static final int FRAME_COUNT = 6;
    private static final int STEREO_FRAMES_PER_VIDEO_FRAME = SAMPLE_RATE / FRAME_RATE;

    @Test
    void liveRecorderProducesLosslessViewportVideoAndExactPresentationAudio() throws Exception {
        Optional<Path> ffmpeg = FfmpegEncoder.findFfmpeg();
        Optional<Path> ffprobe = findFfprobe();
        Assumptions.assumeTrue(ffmpeg.isPresent() && ffprobe.isPresent(),
                "ffmpeg and ffprobe must both be on PATH");

        Path directory = Files.createTempDirectory("live-capture-media-smoke-");
        CaptureRecorder recorder = new CaptureRecorder(
                new FfmpegEncoder(ffmpeg.orElseThrow().toString(), 1),
                BackpressurePolicy.BLOCK, 8, directory, "live", "smoke");
        List<byte[]> submittedRgba = new ArrayList<>();
        short[] expectedPcm = presentationPcm();

        try {
            recorder.start(WIDTH, HEIGHT, FRAME_RATE, SAMPLE_RATE);
            for (int frame = 0; frame < FRAME_COUNT; frame++) {
                byte[] rgba = viewportFrame(frame);
                submittedRgba.add(rgba);
                short[] packet = Arrays.copyOfRange(expectedPcm,
                        frame * STEREO_FRAMES_PER_VIDEO_FRAME * 2,
                        (frame + 1) * STEREO_FRAMES_PER_VIDEO_FRAME * 2);
                recorder.submit(new CapturedFrame(rgba, WIDTH, HEIGHT, packet,
                        STEREO_FRAMES_PER_VIDEO_FRAME, frame));
            }
            Path output = recorder.stop();

            JsonNode probe = new ObjectMapper().readTree(run(ffprobe.orElseThrow().toString(),
                    "-v", "error", "-count_frames", "-show_streams", "-show_format",
                    "-of", "json", output.toString()));
            JsonNode video = stream(probe, "video");
            JsonNode audio = stream(probe, "audio");
            assertEquals("ffv1", video.path("codec_name").asText());
            assertEquals("flac", audio.path("codec_name").asText());
            assertEquals(2, audio.path("channels").asInt());
            assertEquals(WIDTH, video.path("width").asInt());
            assertEquals(HEIGHT, video.path("height").asInt());
            assertEquals(FRAME_COUNT, video.path("nb_read_frames").asInt());
            double expectedDuration = FRAME_COUNT / (double) FRAME_RATE;
            double probedAudioDuration = mediaDurationSeconds(audio, probe.path("format"));
            assertTrue(Math.abs(probedAudioDuration - expectedDuration) <= 1.0 / SAMPLE_RATE,
                    () -> "ffprobe audio duration " + probedAudioDuration + " differs from "
                            + expectedDuration + " by more than one sample");

            byte[] decodedRgba = run(ffmpeg.orElseThrow().toString(), "-v", "error",
                    "-i", output.toString(), "-map", "0:v:0", "-vf", "vflip",
                    "-pix_fmt", "rgba", "-f", "rawvideo", "pipe:1");
            int frameBytes = WIDTH * HEIGHT * 4;
            assertEquals(FRAME_COUNT * frameBytes, decodedRgba.length);
            for (int frame = 0; frame < FRAME_COUNT; frame++) {
                assertArrayEquals(submittedRgba.get(frame),
                        Arrays.copyOfRange(decodedRgba,
                                frame * frameBytes, (frame + 1) * frameBytes),
                        "decoded RGBA differs at frame " + frame);
            }

            byte[] decodedAudioBytes = run(ffmpeg.orElseThrow().toString(), "-v", "error",
                    "-i", output.toString(), "-map", "0:a:0", "-f", "s16le",
                    "-acodec", "pcm_s16le", "-ac", "2", "-ar",
                    Integer.toString(SAMPLE_RATE), "pipe:1");
            short[] decodedPcm = littleEndianShorts(decodedAudioBytes);
            assertArrayEquals(expectedPcm, decodedPcm,
                    "FLAC must preserve tone, silence, and reversed sample regions exactly");
            int decodedStereoFrames = decodedPcm.length / 2;
            double audioDuration = decodedStereoFrames / (double) SAMPLE_RATE;
            assertTrue(Math.abs(audioDuration - expectedDuration) <= 1.0 / SAMPLE_RATE,
                    () -> "audio duration " + audioDuration + " differs from "
                            + expectedDuration + " by more than one sample");
        } finally {
            recorder.abort();
            try (var files = Files.list(directory)) {
                files.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
            Files.deleteIfExists(directory);
        }
    }

    private static JsonNode stream(JsonNode probe, String codecType) {
        for (JsonNode stream : probe.path("streams")) {
            if (codecType.equals(stream.path("codec_type").asText())) {
                return stream;
            }
        }
        throw new AssertionError("missing " + codecType + " stream: " + probe);
    }

    private static double mediaDurationSeconds(JsonNode stream, JsonNode format) {
        JsonNode durationTs = stream.path("duration_ts");
        String timeBase = stream.path("time_base").asText();
        if (durationTs.canConvertToLong() && timeBase.contains("/")) {
            String[] fraction = timeBase.split("/", 2);
            return durationTs.asLong()
                    * Double.parseDouble(fraction[0])
                    / Double.parseDouble(fraction[1]);
        }
        String streamDuration = stream.path("duration").asText();
        if (!streamDuration.isBlank() && !"N/A".equals(streamDuration)) {
            return Double.parseDouble(streamDuration);
        }
        String taggedDuration = stream.path("tags").path("DURATION").asText();
        if (!taggedDuration.isBlank()) {
            String[] fields = taggedDuration.split(":");
            if (fields.length == 3) {
                return Double.parseDouble(fields[0]) * 3600
                        + Double.parseDouble(fields[1]) * 60
                        + Double.parseDouble(fields[2]);
            }
        }
        String formatDuration = format.path("duration").asText();
        if (!formatDuration.isBlank() && !"N/A".equals(formatDuration)) {
            return Double.parseDouble(formatDuration);
        }
        throw new AssertionError("ffprobe supplied no usable audio duration");
    }

    private static byte[] viewportFrame(int frame) {
        byte[] rgba = new byte[WIDTH * HEIGHT * 4];
        for (int pixel = 0; pixel < WIDTH * HEIGHT; pixel++) {
            int offset = pixel * 4;
            rgba[offset] = (byte) (frame * 31 + pixel);
            rgba[offset + 1] = (byte) (frame * 17 + pixel * 3);
            rgba[offset + 2] = (byte) (255 - frame * 13 - pixel);
            rgba[offset + 3] = (byte) 255;
        }
        int marker = (frame * 2 % WIDTH) * 4;
        rgba[marker] = (byte) 255;
        rgba[marker + 1] = 0;
        rgba[marker + 2] = 0;
        return rgba;
    }

    private static short[] presentationPcm() {
        short[] pcm = new short[FRAME_COUNT * STEREO_FRAMES_PER_VIDEO_FRAME * 2];
        int forwardFrames = 2 * STEREO_FRAMES_PER_VIDEO_FRAME;
        for (int sample = 0; sample < forwardFrames; sample++) {
            short value = (short) ((sample * 97) % 20_000 - 10_000);
            pcm[sample * 2] = value;
            pcm[sample * 2 + 1] = (short) -value;
        }
        int reverseStart = 4 * STEREO_FRAMES_PER_VIDEO_FRAME;
        for (int sample = 0; sample < forwardFrames; sample++) {
            int source = forwardFrames - 1 - sample;
            pcm[(reverseStart + sample) * 2] = pcm[source * 2];
            pcm[(reverseStart + sample) * 2 + 1] = pcm[source * 2 + 1];
        }
        return pcm;
    }

    private static short[] littleEndianShorts(byte[] bytes) {
        assertEquals(0, bytes.length % 2);
        short[] result = new short[bytes.length / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (short) ((bytes[i * 2] & 0xff) | (bytes[i * 2 + 1] << 8));
        }
        return result;
    }

    private static byte[] run(String executable, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        process.getInputStream().transferTo(output);
        int exit = process.waitFor();
        assertEquals(0, exit, () -> String.join(" ", command) + "\n" + output);
        return output.toByteArray();
    }

    private static Optional<Path> findFfprobe() {
        String path = System.getenv("PATH");
        if (path == null) {
            return Optional.empty();
        }
        String executable = FfmpegEncoder.isWindows() ? "ffprobe.exe" : "ffprobe";
        for (String directory : path.split(java.io.File.pathSeparator)) {
            if (directory.isBlank()) {
                continue;
            }
            Path candidate = Path.of(directory, executable);
            if (Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
