package com.openggf.tools.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.sound.sampled.AudioSystem;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TestStereoPcmWavWriter {
    @TempDir Path directory;

    @Test
    void writesSignedLittleEndianStereoAndTruncatesIncompleteFrame() throws Exception {
        Path output = directory.resolve("samples.wav");
        StereoPcmWavWriter.write(output, new short[] {Short.MIN_VALUE, Short.MAX_VALUE, -1, 0, 123}, 8000.5);
        try (var stream = AudioSystem.getAudioInputStream(output.toFile())) {
            assertEquals(2, stream.getFrameLength());
            assertEquals(2, stream.getFormat().getChannels());
            assertEquals(16, stream.getFormat().getSampleSizeInBits());
            assertFalse(stream.getFormat().isBigEndian());
            // The WAV integer sample-rate field truncates the supplied fractional rate.
            assertEquals(8000f, stream.getFormat().getSampleRate());
            assertArrayEquals(new byte[] {0, (byte) 128, (byte) 255, 127, (byte) 255, (byte) 255, 0, 0},
                    stream.readAllBytes());
        }
    }

    @Test
    void writesEmptyStream() throws Exception {
        Path output = directory.resolve("empty.wav");
        StereoPcmWavWriter.write(output, new short[0], 44100);
        try (var stream = AudioSystem.getAudioInputStream(output.toFile())) {
            assertEquals(0, stream.getFrameLength());
            assertEquals(0, stream.readAllBytes().length);
        }
    }
}
