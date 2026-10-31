package com.openggf.audio.output;

import org.junit.jupiter.api.Test;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.SOFTLoopback;
import org.lwjgl.openal.SOFTSourceLatency;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/** Deterministic native mixing, with no speaker, desktop, sleep or ROM. */
class TestOpenAlSourceFrameUnits {
    @Test
    void pitchChangesConsumptionButOffsetsAndQueueRetirementStayInSourceFrames() {
        long device = SOFTLoopback.alcLoopbackOpenDeviceSOFT((ByteBuffer) null);
        assertNotEquals(0, device, "the bundled OpenAL must support its loopback device");
        long context = 0;
        int source = 0;
        int[] buffers = new int[8];
        long previousContext = ALC10.alcGetCurrentContext();
        var previousCapabilities = previousContext != 0 ? AL.getCapabilities() : null;
        try (var stack = MemoryStack.stackPush()) {
            var capabilities = ALC.createCapabilities(device);
            context = ALC10.alcCreateContext(device, stack.ints(
                    ALC10.ALC_FREQUENCY, 48_000,
                    SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT, SOFTLoopback.ALC_STEREO_SOFT,
                    SOFTLoopback.ALC_FORMAT_TYPE_SOFT, SOFTLoopback.ALC_SHORT_SOFT, 0));
            assertNotEquals(0, context);
            assertTrue(ALC10.alcMakeContextCurrent(context));
            var al = AL.createCapabilities(capabilities);
            assertTrue(al.AL_SOFT_source_latency, "the bundled OpenAL exposes the fractional source cursor");
            source = AL10.alGenSources();
            for (int i = 0; i < buffers.length; i++) {
                buffers[i] = AL10.alGenBuffers();
                AL10.alBufferData(buffers[i], AL10.AL_FORMAT_STEREO16, new short[2_048], 48_000);
                AL10.alSourceQueueBuffers(source, buffers[i]);
            }
            AL10.alSourcef(source, AL10.AL_PITCH, 1.005f);
            AL10.alSourcePlay(source);
            SOFTLoopback.alcRenderSamplesSOFT(device, new short[4_800 * 2], 4_800);
            int offset = AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET);
            assertEquals(4_800 * 1.005, offset, 2, "AL_SAMPLE_OFFSET counts pitched source frames");
            var cursor = stack.mallocLong(2);
            SOFTSourceLatency.alGetSourcei64vSOFT(source, SOFTSourceLatency.AL_SAMPLE_OFFSET_LATENCY_SOFT, cursor);
            double fractionalOffset = cursor.get(0) / 0x1.0p32;
            assertEquals(offset, fractionalOffset, 1);
            int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
            assertEquals(4, processed);
            for (int i = 0; i < processed; i++) AL10.alSourceUnqueueBuffers(source);
            assertEquals(offset - processed * 1_024, AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET),
                    "unqueue subtracts source buffer lengths, independent of pitch");
            AL10.alSourcef(source, AL10.AL_PITCH, 0.995f);
            SOFTLoopback.alcRenderSamplesSOFT(device, new short[2_400 * 2], 2_400);
            long total = processed * 1_024L + AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET);
            assertEquals(4_800 * 1.005 + 2_400 * 0.995, total, 3);
            assertEquals(AL10.AL_NO_ERROR, AL10.alGetError());
        } finally {
            if (source != 0) AL10.alDeleteSources(source);
            for (int buffer : buffers) if (buffer != 0) AL10.alDeleteBuffers(buffer);
            ALC10.alcMakeContextCurrent(previousContext);
            AL.setCurrentProcess(previousCapabilities);
            if (context != 0) ALC10.alcDestroyContext(context);
            ALC10.alcCloseDevice(device);
        }
    }
}
