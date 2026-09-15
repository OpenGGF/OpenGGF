package com.openggf.tools.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFileFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;

/** Stereo signed 16-bit little-endian output shared by the SFX render tools. */
final class StereoPcmWavWriter {
    private StereoPcmWavWriter() {}

    static void write(Path path, short[] interleaved, double rate) throws IOException {
        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                (float) rate, 16, 2, 4, (float) rate, false);
        byte[] bytes = new byte[interleaved.length * 2];
        for (int i = 0; i < interleaved.length; i++) {
            bytes[i * 2] = (byte) interleaved[i];
            bytes[i * 2 + 1] = (byte) (interleaved[i] >> 8);
        }
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(bytes), format,
                interleaved.length / 2)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, path.toFile());
        }
    }
}
