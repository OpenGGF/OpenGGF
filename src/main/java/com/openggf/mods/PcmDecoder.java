package com.openggf.mods;

import com.openggf.codec.wav.WavPcmDecoder;
import com.openggf.io.ModInputLimits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.Objects;

/** Bounded dispatcher that normalizes supported WAV/OGG assets to signed 16-bit PCM. */
public class PcmDecoder {
    private final OggPcmDecoder ogg = new OggPcmDecoder();
    private final AllocationProbe probe;

    public PcmDecoder() { this(AllocationProbe.NONE); }

    PcmDecoder(AllocationProbe probe) { this.probe = Objects.requireNonNull(probe, "probe"); }

    public PcmData decode(String assetPath, byte[] encoded, ModInputLimits limits) throws IOException {
        Objects.requireNonNull(assetPath, "assetPath");
        Objects.requireNonNull(encoded, "encoded");
        Objects.requireNonNull(limits, "limits");
        String lower = assetPath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ogg")) return ogg.decode(encoded, limits);
        if (!lower.endsWith(".wav")) throw new IOException("Unsupported audio asset extension");
        WavPcmDecoder.Result wav = WavPcmDecoder.decode(encoded, limits.maxAudioTrackPcmBytes(),
                limits.maxAudioTrackSeconds(), limits.maxAudioCacheBytes());
        int samples = wav.data().length / (wav.bitsPerSample() / 8);
        long normalizedBytes = Math.multiplyExact((long) samples, Short.BYTES);
        validateMetadata(wav.sampleRate(), wav.channels(), normalizedBytes, encoded.length, limits);
        probe.beforeJavaPcmAllocation(normalizedBytes);
        short[] normalized = new short[samples];
        if (wav.bitsPerSample() == 8) {
            for (int index = 0; index < samples; index++) normalized[index] = (short) ((wav.data()[index] & 0xff) - 128 << 8);
        } else {
            ByteBuffer bytes = ByteBuffer.wrap(wav.data()).order(ByteOrder.LITTLE_ENDIAN);
            for (int index = 0; index < samples; index++) normalized[index] = bytes.getShort();
        }
        return PcmData.takeOwnership(wav.sampleRate(), wav.channels(), normalized);
    }

    interface AllocationProbe {
        AllocationProbe NONE = bytes -> { };
        void beforeJavaPcmAllocation(long bytes);
    }

    static void validateMetadata(int rate, int channels, long pcmBytes, long encodedBytes,
                                 ModInputLimits limits) throws IOException {
        if (rate < limits.minAudioSampleRate() || rate > limits.maxAudioSampleRate()) {
            throw new IOException("Audio sample rate is outside configured limits");
        }
        if (channels < limits.minAudioChannels() || channels > limits.maxAudioChannels()) {
            throw new IOException("Audio channels are outside configured limits");
        }
        if (pcmBytes <= 0 || pcmBytes > limits.maxAudioTrackPcmBytes()) throw new IOException("Decoded PCM exceeds track limit");
        long frames = pcmBytes / (channels * (long) Short.BYTES);
        if (frames > Math.multiplyExact((long) rate, limits.maxAudioTrackSeconds())) {
            throw new IOException("Decoded audio exceeds duration limit");
        }
        long javaPcmBytes = pcmBytes;
        long encodedHeapAndDirect = Math.multiplyExact(encodedBytes, 2L);
        long peak = Math.addExact(encodedHeapAndDirect, Math.addExact(pcmBytes, javaPcmBytes));
        if (peak > limits.maxAudioCacheBytes()) throw new IOException("Audio decode peak exceeds cache budget");
    }
}
