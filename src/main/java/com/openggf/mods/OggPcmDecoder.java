package com.openggf.mods;

import com.openggf.io.ModInputLimits;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Objects;

/** Bounded stb_vorbis handle decoder; metadata is validated before native PCM allocation. */
public final class OggPcmDecoder {
    private final VorbisApi api;
    private final AllocationProbe probe;

    public OggPcmDecoder() { this(VorbisApi.NATIVE, AllocationProbe.NONE); }
    OggPcmDecoder(AllocationProbe probe) { this(VorbisApi.NATIVE, probe); }
    OggPcmDecoder(VorbisApi api, AllocationProbe probe) {
        this.api = Objects.requireNonNull(api, "api");
        this.probe = Objects.requireNonNull(probe, "probe");
    }

    public PcmData decode(byte[] encoded, ModInputLimits limits) throws IOException {
        return decode(encoded, limits, limits.maxAudioTrackPcmBytes(),
                limits.maxAudioTrackSeconds(), "track");
    }

    PcmData decode(byte[] encoded, ModInputLimits limits, long maxPcmBytes,
                   int maxSeconds, String limitName) throws IOException {
        Objects.requireNonNull(encoded, "encoded");
        Objects.requireNonNull(limits, "limits");
        if (encoded.length == 0 || encoded.length > limits.maxAssetBytes()) throw new IOException("OGG asset is empty or oversized");
        if (Math.multiplyExact((long) encoded.length, 2L) > limits.maxAudioCacheBytes()) {
            throw new IOException("OGG compressed heap/direct input exceeds working budget");
        }
        ByteBuffer directInput = null;
        ShortBuffer nativePcm = null;
        long handle = 0;
        try {
            directInput = api.allocateInput(encoded.length);
            directInput.put(encoded).flip();
            OpenResult opened = api.open(directInput);
            handle = opened.handle();
            if (handle == 0) throw new IOException("Unable to open OGG/Vorbis stream: " + opened.error());
            Info info = api.info(handle);
            int rate = info.sampleRate();
            int channels = info.channels();
            int frames = api.streamLength(handle);
            if (frames <= 0) throw new IOException("OGG stream has no decodable frames");
            long sampleCount = Math.multiplyExact((long) frames, channels);
            long pcmBytes = Math.multiplyExact(sampleCount, Short.BYTES);
            PcmDecoder.validateMetadata(rate, channels, pcmBytes, encoded.length, limits,
                    maxPcmBytes, maxSeconds, limitName);
            if (sampleCount > Integer.MAX_VALUE) throw new IOException("OGG sample count exceeds Java array limit");
            probe.beforeNativePcmAllocation(pcmBytes);
            nativePcm = api.allocatePcm((int) sampleCount);
            int decodedFrames = 0;
            while (decodedFrames < frames) {
                int sampleOffset = Math.multiplyExact(decodedFrames, channels);
                nativePcm.position(sampleOffset);
                int read = api.decode(handle, channels, nativePcm.slice());
                if (read <= 0) throw new IOException("OGG stream truncated during decode");
                decodedFrames = Math.addExact(decodedFrames, read);
                if (decodedFrames > frames) throw new IOException("OGG decoder exceeded declared frame count");
            }
            probe.beforeJavaPcmAllocation(pcmBytes);
            short[] samples = new short[(int) sampleCount];
            nativePcm.position(0);
            nativePcm.get(samples);
            return PcmData.takeOwnership(rate, channels, samples);
        } catch (ArithmeticException error) {
            throw new IOException("OGG decoded size overflow", error);
        } finally {
            if (handle != 0) { api.close(handle); probe.handleClosed(); }
            if (nativePcm != null) { nativePcm.position(0); api.freePcm(nativePcm); probe.nativePcmFreed(); }
            if (directInput != null) { api.freeInput(directInput); probe.directInputFreed(); }
        }
    }

    record OpenResult(long handle, int error) { }
    record Info(int sampleRate, int channels) { }

    interface VorbisApi {
        VorbisApi NATIVE = new VorbisApi() {
            public ByteBuffer allocateInput(int bytes) { return MemoryUtil.memAlloc(bytes); }
            public void freeInput(ByteBuffer input) { MemoryUtil.memFree(input); }
            public OpenResult open(ByteBuffer input) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer error = stack.mallocInt(1);
                    long handle = STBVorbis.stb_vorbis_open_memory(input, error, null);
                    return new OpenResult(handle, error.get(0));
                }
            }
            public Info info(long handle) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    STBVorbisInfo info = STBVorbisInfo.malloc(stack);
                    STBVorbis.stb_vorbis_get_info(handle, info);
                    return new Info(info.sample_rate(), info.channels());
                }
            }
            public int streamLength(long handle) { return STBVorbis.stb_vorbis_stream_length_in_samples(handle); }
            public ShortBuffer allocatePcm(int samples) { return MemoryUtil.memAllocShort(samples); }
            public int decode(long handle, int channels, ShortBuffer target) {
                return STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, target);
            }
            public void close(long handle) { STBVorbis.stb_vorbis_close(handle); }
            public void freePcm(ShortBuffer pcm) { MemoryUtil.memFree(pcm); }
        };
        ByteBuffer allocateInput(int bytes);
        void freeInput(ByteBuffer input);
        OpenResult open(ByteBuffer input);
        Info info(long handle);
        int streamLength(long handle);
        ShortBuffer allocatePcm(int samples);
        int decode(long handle, int channels, ShortBuffer target);
        void close(long handle);
        void freePcm(ShortBuffer pcm);
    }

    interface AllocationProbe {
        AllocationProbe NONE = new AllocationProbe() { };
        default void beforeNativePcmAllocation(long bytes) { }
        default void beforeJavaPcmAllocation(long bytes) { }
        default void handleClosed() { }
        default void nativePcmFreed() { }
        default void directInputFreed() { }
    }
}
