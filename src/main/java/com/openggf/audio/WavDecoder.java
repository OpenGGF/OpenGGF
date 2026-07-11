package com.openggf.audio;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

/** Pure-Java RIFF/WAVE PCM facade used by the legacy OpenAL loading path. */
public final class WavDecoder {
    private static final int DEFAULT_MAX_WAV_BYTES = 64 << 20;
    public final int channels;
    public final int sampleRate;
    public final int bitsPerSample;
    public final byte[] data;

    private WavDecoder(int channels, int sampleRate, int bitsPerSample, byte[] data) {
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.bitsPerSample = bitsPerSample;
        this.data = data;
    }

    public static WavDecoder decode(InputStream input) throws IOException {
        return decode(input, DEFAULT_MAX_WAV_BYTES);
    }

    public static WavDecoder decode(InputStream input, int maxBytes) throws IOException {
        Objects.requireNonNull(input, "input");
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be positive");
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.min(maxBytes, 8192))) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                if (read == 0) {
                    int single = input.read();
                    if (single == -1) break;
                    if (bytes.size() >= maxBytes) throw new IOException("WAV input exceeds byte limit");
                    bytes.write(single);
                    continue;
                }
                if (bytes.size() > maxBytes - read) throw new IOException("WAV input exceeds byte limit");
                bytes.write(chunk, 0, read);
            }
            return decodeBytes(bytes.toByteArray());
        }
    }

    private static WavDecoder decodeBytes(byte[] encoded) throws IOException {
        ByteBuffer input = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        if (input.remaining() < 12 || input.getInt() != 0x46464952) throw new IOException("Not a RIFF file");
        long riffBytes = Integer.toUnsignedLong(input.getInt());
        if (riffBytes != encoded.length - 8L || input.getInt() != 0x45564157) {
            throw new IOException("Invalid RIFF/WAVE header");
        }
        int channels = 0;
        int sampleRate = 0;
        int bitsPerSample = 0;
        int blockAlign = 0;
        byte[] data = null;
        while (input.hasRemaining()) {
            if (input.remaining() < 8) throw new IOException("Truncated WAV chunk header");
            int id = input.getInt();
            long unsignedSize = Integer.toUnsignedLong(input.getInt());
            if (unsignedSize > input.remaining() || unsignedSize > Integer.MAX_VALUE) {
                throw new IOException("WAV chunk exceeds remaining input");
            }
            int size = (int) unsignedSize;
            int start = input.position();
            if (id == 0x20746d66) {
                if (channels != 0 || size < 16) throw new IOException("Invalid or duplicate fmt chunk");
                if (Short.toUnsignedInt(input.getShort()) != 1) throw new IOException("Only PCM format 1 is supported");
                channels = Short.toUnsignedInt(input.getShort());
                sampleRate = input.getInt();
                long byteRate = Integer.toUnsignedLong(input.getInt());
                blockAlign = Short.toUnsignedInt(input.getShort());
                bitsPerSample = Short.toUnsignedInt(input.getShort());
                if (channels < 1 || channels > 2 || sampleRate < 8_000 || sampleRate > 192_000
                        || (bitsPerSample != 8 && bitsPerSample != 16)) {
                    throw new IOException("Unsupported WAV PCM format");
                }
                int expectedAlign = channels * (bitsPerSample / 8);
                if (blockAlign != expectedAlign || byteRate != (long) sampleRate * expectedAlign) {
                    throw new IOException("Inconsistent WAV format rates");
                }
            } else if (id == 0x61746164) {
                if (channels == 0 || data != null || size == 0 || size % blockAlign != 0) {
                    throw new IOException("Invalid WAV data chunk");
                }
                data = Arrays.copyOfRange(encoded, start, start + size);
            }
            input.position(start + size);
            if ((size & 1) != 0) {
                if (!input.hasRemaining()) throw new IOException("Missing WAV chunk padding");
                input.get();
            }
        }
        if (channels == 0 || data == null) throw new IOException("WAV fmt or data chunk missing");
        return new WavDecoder(channels, sampleRate, bitsPerSample, data);
    }
}
