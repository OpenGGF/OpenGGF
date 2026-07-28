package com.openggf.audio;

import com.openggf.audio.presentation.DecodedPcm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    /**
     * Decodes a WAV file from the given input stream.
     *
     * @param is the input stream containing WAV data
     * @return a WavDecoder with parsed audio parameters and PCM data
     * @throws IOException if the stream is not a valid WAV file or cannot be read
     */
    public static WavDecoder decode(InputStream is) throws IOException {
        return decode(is, DEFAULT_MAX_WAV_BYTES);
    }

    /**
     * Bounded decode for creator-supplied audio. Creator assets must never be read
     * unbounded, so the byte ceiling is enforced while reading rather than after.
     *
     * @param input    the input stream containing WAV data
     * @param maxBytes the inclusive ceiling on encoded bytes read from {@code input}
     * @throws IOException if the input is not valid WAV data or exceeds {@code maxBytes}
     */
    public static WavDecoder decode(InputStream input, int maxBytes) throws IOException {
        ParsedWav parsed = parse(readBounded(input, maxBytes));
        return new WavDecoder(parsed.channels, parsed.sampleRate, parsed.bitsPerSample, parsed.data);
    }

    public static DecodedPcm decodePcm(String assetId, InputStream source) throws IOException {
        return decodePcm(assetId, source, DEFAULT_MAX_WAV_BYTES);
    }

    /** Bounded {@link #decodePcm(String, InputStream)} for creator-supplied audio. */
    public static DecodedPcm decodePcm(String assetId, InputStream source, int maxBytes)
            throws IOException {
        ParsedWav parsed = parse(readBounded(source, maxBytes));
        int sampleCount = parsed.data.length / (parsed.bitsPerSample / Byte.SIZE);
        short[] samples = new short[sampleCount];
        if (parsed.bitsPerSample == 8) {
            for (int index = 0; index < sampleCount; index++) {
                samples[index] = (short) (((parsed.data[index] & 0xFF) - 128) << 8);
            }
        } else {
            for (int index = 0; index < sampleCount; index++) {
                int byteIndex = index * Short.BYTES;
                samples[index] = (short) ((parsed.data[byteIndex] & 0xFF)
                        | (parsed.data[byteIndex + 1] << 8));
            }
        }
        return new DecodedPcm(assetId, parsed.channels, parsed.sampleRate, samples);
    }

    private static byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        Objects.requireNonNull(input, "input");
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.min(maxBytes, 8192))) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                if (read == 0) {
                    // A stream that returns 0 without being at EOF must not spin
                    // forever, and must not be allowed past the ceiling one byte
                    // at a time either.
                    int single = input.read();
                    if (single == -1) {
                        break;
                    }
                    if (bytes.size() >= maxBytes) {
                        throw new IOException("WAV input exceeds byte limit");
                    }
                    bytes.write(single);
                    continue;
                }
                if (bytes.size() > maxBytes - read) {
                    throw new IOException("WAV input exceeds byte limit");
                }
                bytes.write(chunk, 0, read);
            }
            return bytes.toByteArray();
        }
    }

    private static ParsedWav parse(byte[] all) throws IOException {
        if (all.length < 12 || !hasId(all, 0, "RIFF")) {
            throw new IOException("WAV file too short for RIFF header");
        }
        long riffSize = unsignedInt(all, 4);
        long riffEnd = 8L + riffSize;
        if (riffSize < 4 || riffEnd != all.length || !hasId(all, 8, "WAVE")) {
            throw new IOException("Invalid RIFF/WAVE bounds");
        }
        int channels = 0;
        int sampleRate = 0;
        int bitsPerSample = 0;
        int blockAlign = 0;
        byte[] data = null;
        boolean fmtFound = false;
        int position = 12;
        while (position < riffEnd) {
            if (riffEnd - position < 8) {
                throw new IOException("Truncated WAV chunk header");
            }
            long chunkSize = unsignedInt(all, position + 4);
            long chunkDataStart = position + 8L;
            long chunkDataEnd = chunkDataStart + chunkSize;
            long paddedEnd = chunkDataEnd + (chunkSize & 1L);
            if (chunkDataEnd > riffEnd || paddedEnd > riffEnd || chunkSize > Integer.MAX_VALUE) {
                throw new IOException("WAV chunk exceeds RIFF bounds");
            }
            int size = (int) chunkSize;
            int dataStart = (int) chunkDataStart;
            if (hasId(all, position, "fmt ")) {
                if (fmtFound || size < 16) {
                    throw new IOException("Invalid fmt chunk");
                }
                int format = unsignedShort(all, dataStart);
                channels = unsignedShort(all, dataStart + 2);
                long parsedSampleRate = unsignedInt(all, dataStart + 4);
                long byteRate = unsignedInt(all, dataStart + 8);
                blockAlign = unsignedShort(all, dataStart + 12);
                bitsPerSample = unsignedShort(all, dataStart + 14);
                if (format != 1 || (channels != 1 && channels != 2)
                        || (bitsPerSample != 8 && bitsPerSample != 16)
                        || parsedSampleRate == 0 || parsedSampleRate > Integer.MAX_VALUE) {
                    throw new IOException("Unsupported PCM WAV format");
                }
                sampleRate = (int) parsedSampleRate;
                int expectedBlockAlign = channels * (bitsPerSample / Byte.SIZE);
                long expectedByteRate = (long) sampleRate * expectedBlockAlign;
                if (blockAlign != expectedBlockAlign || byteRate != expectedByteRate) {
                    throw new IOException("Inconsistent PCM WAV format fields");
                }
                fmtFound = true;
            } else if (hasId(all, position, "data")) {
                if (data != null) {
                    throw new IOException("Multiple WAV data chunks are unsupported");
                }
                data = new byte[size];
                System.arraycopy(all, dataStart, data, 0, size);
            }
            position = (int) paddedEnd;
        }
        if (!fmtFound || data == null) {
            throw new IOException("WAV requires fmt and data chunks");
        }
        if (data.length % blockAlign != 0) {
            throw new IOException("WAV data does not contain complete source frames");
        }
        return new ParsedWav(channels, sampleRate, bitsPerSample, data);
    }

    private static boolean hasId(byte[] data, int offset, String id) {
        return offset >= 0 && offset + 4 <= data.length
                && data[offset] == id.charAt(0) && data[offset + 1] == id.charAt(1)
                && data[offset + 2] == id.charAt(2) && data[offset + 3] == id.charAt(3);
    }

    private static int unsignedShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static long unsignedInt(byte[] data, int offset) {
        return (data[offset] & 0xFFL) | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16) | ((data[offset + 3] & 0xFFL) << 24);
    }

    private record ParsedWav(int channels, int sampleRate, int bitsPerSample, byte[] data) {
    }
}
