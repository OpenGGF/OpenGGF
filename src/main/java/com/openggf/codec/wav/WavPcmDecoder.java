package com.openggf.codec.wav;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

/** Narrow, dependency-neutral RIFF/WAVE PCM parser shared by engine and mod audio paths. */
public final class WavPcmDecoder {
    private static final int RIFF = 0x46464952;
    private static final int WAVE = 0x45564157;
    private static final int FMT = 0x20746d66;
    private static final int DATA = 0x61746164;

    private WavPcmDecoder() {
    }

    public static Result decode(byte[] encoded) throws IOException {
        return decode(encoded, Long.MAX_VALUE);
    }

    public static Result decode(byte[] encoded, long maxNormalizedPcmBytes) throws IOException {
        return decode(encoded, maxNormalizedPcmBytes, Long.MAX_VALUE);
    }

    public static Result decode(byte[] encoded, long maxNormalizedPcmBytes,
                                long maxSeconds) throws IOException {
        return decode(encoded, maxNormalizedPcmBytes, maxSeconds, Long.MAX_VALUE);
    }

    public static Result decode(byte[] encoded, long maxNormalizedPcmBytes,
                                long maxSeconds, long maxWorkingBytes) throws IOException {
        Objects.requireNonNull(encoded, "encoded");
        if (maxNormalizedPcmBytes <= 0) throw new IllegalArgumentException("PCM cap must be positive");
        if (maxSeconds <= 0) throw new IllegalArgumentException("duration cap must be positive");
        if (maxWorkingBytes <= 0) throw new IllegalArgumentException("working cap must be positive");
        ByteBuffer input = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        if (input.remaining() < 12) throw new IOException("WAV file too short for RIFF header");
        if (input.getInt() != RIFF) throw new IOException("Not a little-endian RIFF file");
        long riffBytes = Integer.toUnsignedLong(input.getInt());
        if (riffBytes != encoded.length - 8L) throw new IOException("RIFF size does not match input length");
        if (input.getInt() != WAVE) throw new IOException("Not a WAVE file");

        int channels = 0;
        int sampleRate = 0;
        int bitsPerSample = 0;
        int blockAlign = 0;
        byte[] pcm = null;
        boolean sawFmt = false;
        while (input.hasRemaining()) {
            if (input.remaining() < 8) throw new IOException("Truncated WAV chunk header");
            int chunkId = input.getInt();
            long unsignedSize = Integer.toUnsignedLong(input.getInt());
            if (unsignedSize > input.remaining() || unsignedSize > Integer.MAX_VALUE) {
                throw new IOException("WAV chunk exceeds remaining input");
            }
            int size = (int) unsignedSize;
            int start = input.position();
            if (chunkId == FMT) {
                if (sawFmt) throw new IOException("Duplicate fmt chunk");
                if (size < 16) throw new IOException("fmt chunk too small");
                sawFmt = true;
                int format = Short.toUnsignedInt(input.getShort());
                if (format != 1) throw new IOException("Only integer PCM WAV format 1 is supported");
                channels = Short.toUnsignedInt(input.getShort());
                sampleRate = input.getInt();
                long byteRate = Integer.toUnsignedLong(input.getInt());
                blockAlign = Short.toUnsignedInt(input.getShort());
                bitsPerSample = Short.toUnsignedInt(input.getShort());
                if (channels < 1 || channels > 2) throw new IOException("WAV channels must be mono or stereo");
                if (sampleRate < 8_000 || sampleRate > 192_000) throw new IOException("Unsupported WAV sample rate");
                if (bitsPerSample != 8 && bitsPerSample != 16) throw new IOException("WAV PCM must be 8-bit or 16-bit");
                int expectedAlign = Math.multiplyExact(channels, bitsPerSample / 8);
                long expectedRate = Math.multiplyExact((long) sampleRate, expectedAlign);
                if (blockAlign != expectedAlign || byteRate != expectedRate) {
                    throw new IOException("WAV byte rate or block alignment is inconsistent");
                }
            } else if (chunkId == DATA) {
                if (!sawFmt) throw new IOException("WAV fmt chunk must precede data");
                if (pcm != null) throw new IOException("Duplicate data chunk");
                if (size == 0 || size % blockAlign != 0) throw new IOException("WAV data is empty or misaligned");
                long normalizedBytes = Math.multiplyExact((long) size / (bitsPerSample / 8), Short.BYTES);
                if (normalizedBytes > maxNormalizedPcmBytes) {
                    throw new IOException("WAV normalized PCM exceeds byte limit");
                }
                long workingBytes = Math.addExact(Math.multiplyExact((long) encoded.length, 2L),
                        Math.multiplyExact(normalizedBytes, 2L));
                if (workingBytes > maxWorkingBytes) throw new IOException("WAV decode peak exceeds working limit");
                long frames = size / (long) blockAlign;
                long maxFrames = maxSeconds > Long.MAX_VALUE / sampleRate
                        ? Long.MAX_VALUE : maxSeconds * sampleRate;
                if (frames > maxFrames) {
                    throw new IOException("WAV duration exceeds limit");
                }
                pcm = Arrays.copyOfRange(encoded, start, start + size);
            }
            input.position(start + size);
            if ((size & 1) != 0) {
                if (!input.hasRemaining()) throw new IOException("Missing WAV chunk padding byte");
                input.get();
            }
        }
        if (!sawFmt) throw new IOException("No fmt chunk found in WAV file");
        if (pcm == null) throw new IOException("No data chunk found in WAV file");
        return new Result(channels, sampleRate, bitsPerSample, pcm);
    }

    /** The returned data array is exclusively owned by the result and transferred to trusted callers. */
    public static final class Result {
        private final int channels;
        private final int sampleRate;
        private final int bitsPerSample;
        private final byte[] data;

        private Result(int channels, int sampleRate, int bitsPerSample, byte[] data) {
            this.channels = channels;
            this.sampleRate = sampleRate;
            this.bitsPerSample = bitsPerSample;
            this.data = Objects.requireNonNull(data, "data");
        }

        public int channels() { return channels; }
        public int sampleRate() { return sampleRate; }
        public int bitsPerSample() { return bitsPerSample; }
        public byte[] data() { return data; }
    }
}
