package com.openggf.audio;

import org.lwjgl.openal.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.PerformanceProfiler;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.ALC11.*;
import static org.lwjgl.openal.SOFTHRTF.*;

public class LWJGLAudioBackend extends AbstractSmpsAudioBackend {
    private static final Logger LOGGER = Logger.getLogger(LWJGLAudioBackend.class.getName());

    private long device;
    private long context;

    private final Map<String, Integer> buffers = new HashMap<>();
    private final List<Integer> sfxSources = new ArrayList<>();
    private int musicSource = -1;

    private int[] streamBuffers;
    private static final int STREAM_BUFFER_COUNT = 3;
    private int deviceSampleRate = 48000;  // Default fallback, updated in init()
    // Reusable DirectBuffer to avoid allocation in fillBuffer() hot path
    private ShortBuffer directShortBuffer;

    public LWJGLAudioBackend() {
        this(SonicConfigurationService.createStandalone(), null);
    }

    public LWJGLAudioBackend(SonicConfigurationService configService) {
        this(configService, null);
    }

    public LWJGLAudioBackend(SonicConfigurationService configService, PerformanceProfiler profiler) {
        super(configService, profiler);
    }

    @Override
    protected int getDeviceSampleRate() {
        return deviceSampleRate;
    }

    @Override
    protected void hookInitDevice() {
        try {
            // Open default device
            device = alcOpenDevice((ByteBuffer) null);
            if (device == 0) {
                throw new RuntimeException("Could not open ALC device");
            }

            // Request 48000 Hz sample rate explicitly and disable HRTF for clean stereo output
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer contextAttribs = stack.ints(
                    ALC_FREQUENCY, 48000,
                    ALC_HRTF_SOFT, ALC_FALSE,  // Disable HRTF processing
                    0  // Terminate list
                );
                context = alcCreateContext(device, contextAttribs);
            }
            if (context == 0) {
                throw new RuntimeException("Could not create ALC context");
            }

            alcMakeContextCurrent(context);

            // Create capabilities (required for LWJGL OpenAL)
            ALCCapabilities alcCaps = ALC.createCapabilities(device);
            AL.createCapabilities(alcCaps);

            // Verify the actual frequency (may differ from request)
            deviceSampleRate = alcGetInteger(device, ALC_FREQUENCY);
            if (deviceSampleRate <= 0) {
                deviceSampleRate = 48000;  // Use our requested rate as fallback
            }

            if (alGetError() != AL_NO_ERROR) {
                throw new RuntimeException("AL Error during init");
            }

            // Log HRTF status
            if (ALC.getCapabilities().ALC_SOFT_HRTF) {
                int hrtfStatus = alcGetInteger(device, ALC_HRTF_STATUS_SOFT);
                String hrtfStatusStr = switch (hrtfStatus) {
                    case ALC_HRTF_DISABLED_SOFT -> "Disabled";
                    case ALC_HRTF_ENABLED_SOFT -> "Enabled";
                    case ALC_HRTF_DENIED_SOFT -> "Denied";
                    case ALC_HRTF_REQUIRED_SOFT -> "Required";
                    case ALC_HRTF_HEADPHONES_DETECTED_SOFT -> "Headphones Detected";
                    case ALC_HRTF_UNSUPPORTED_FORMAT_SOFT -> "Unsupported Format";
                    default -> "Unknown (" + hrtfStatus + ")";
                };
                LOGGER.info("HRTF Status: " + hrtfStatusStr);
            }

            // Log device info
            String deviceName = alcGetString(device, ALC_DEVICE_SPECIFIER);
            int monoSources = alcGetInteger(device, ALC_MONO_SOURCES);
            int stereoSources = alcGetInteger(device, ALC_STEREO_SOURCES);
            LOGGER.info("OpenAL Device: " + deviceName);
            LOGGER.info("Mono sources: " + monoSources + ", Stereo sources: " + stereoSources);

            LOGGER.info("LWJGL OpenAL Initialized. Device sample rate: " + deviceSampleRate + " Hz, Buffer Size: " + STREAM_BUFFER_SIZE);
            pcmHistory = new com.openggf.audio.runtime.PcmHistoryRing(Math.max(STREAM_BUFFER_SIZE,
                    com.openggf.audio.runtime.PcmHistoryRing.capacityFramesFor(
                            deviceSampleRate,
                            configService.getString(com.openggf.configuration.SonicConfiguration.REWIND_AUDIO_HISTORY_LIMIT_TYPE),
                            configService.getInt(com.openggf.configuration.SonicConfiguration.REWIND_AUDIO_HISTORY_SECONDS),
                            configService.getInt(com.openggf.configuration.SonicConfiguration.REWIND_AUDIO_HISTORY_SIZE_MB))));

            // Preload SFX
            for (String sfxPath : sfxFallback.values()) {
                loadWav(sfxPath);
            }

            // Generate music source
            musicSource = alGenSources();

            // Pre-allocate reusable DirectBuffer to avoid per-fillBuffer allocations
            if (directShortBuffer != null) {
                MemoryUtil.memFree(directShortBuffer);
            }
            directShortBuffer = MemoryUtil.memAllocShort(STREAM_BUFFER_SIZE * 2);

        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "LWJGL OpenAL Init failed", t);
            destroyDeviceAfterFailedInit(t);
            throw new RuntimeException(t);
        }
    }

    private void destroyDeviceAfterFailedInit(Throwable primary) {
        try {
            hookDestroyDevice();
        } catch (Throwable cleanupFailure) {
            primary.addSuppressed(cleanupFailure);
            LOGGER.log(Level.WARNING, "Failed to clean up partial LWJGL OpenAL init", cleanupFailure);
        }
    }

    @Override
    protected void playWavMusic(String filename, int musicId) {
        playWav(filename, musicSource, true);
    }

    @Override
    protected void hookStartStream() {
        if (streamBuffers == null) {
            streamBuffers = new int[STREAM_BUFFER_COUNT];
            for (int i = 0; i < STREAM_BUFFER_COUNT; i++) {
                streamBuffers[i] = alGenBuffers();
            }
        }

        for (int i = 0; i < STREAM_BUFFER_COUNT; i++) {
            fillBuffer(streamBuffers[i]);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer bufferIds = stack.mallocInt(STREAM_BUFFER_COUNT);
            for (int i = 0; i < STREAM_BUFFER_COUNT; i++) {
                bufferIds.put(i, streamBuffers[i]);
            }
            alSourceQueueBuffers(musicSource, bufferIds);
        }
        alSourcePlay(musicSource);
    }

    @Override
    protected void hookStopStreamSource() {
        if (musicSource >= 0) {
            alSourceStop(musicSource);
            int queued = alGetSourcei(musicSource, AL_BUFFERS_QUEUED);
            while (queued > 0) {
                alSourceUnqueueBuffers(musicSource);
                queued--;
            }
            alSourcei(musicSource, AL_BUFFER, 0);
        }
    }

    @Override
    protected void hookUpdateStream() {
        // Check for pending music restoration (deferred from E4 handler)
        if (pendingRestore) {
            pendingRestore = false;
            doRestoreMusic();
            return;
        }

        boolean hasStream;
        synchronized (streamLock) {
            hasStream = currentStream != null || sfxStream != null || runtimeProvidesPresentationPcm();
        }
        if (hasStream && streamBuffers == null) {
            startStream();
        }
        if (hasStream) {
            int state = alGetSourcei(musicSource, AL_SOURCE_STATE);
            int queued = alGetSourcei(musicSource, AL_BUFFERS_QUEUED);
            if (streamBuffers == null || queued == 0) {
                startStream();
                return;
            }
            int processed = alGetSourcei(musicSource, AL_BUFFERS_PROCESSED);

            while (processed > 0) {
                int bufferId = alSourceUnqueueBuffers(musicSource);
                fillBuffer(bufferId);
                alSourceQueueBuffers(musicSource, bufferId);
                processed--;
            }

            // Check state again
            state = alGetSourcei(musicSource, AL_SOURCE_STATE);
            if (state != AL_PLAYING) {
                alSourcePlay(musicSource);
            }
        }
    }

    @Override
    protected void hookStopAndClearMusicSource() {
        alSourceStop(musicSource);
        alSourcei(musicSource, AL_BUFFER, 0);
    }

    @Override
    protected void hookStopAndUnqueueAllMusicBuffers() {
        alSourceStop(musicSource);

        // Unqueue ALL buffers (both processed and queued) to avoid OpenAL errors
        int queued = alGetSourcei(musicSource, AL_BUFFERS_QUEUED);
        for (int i = 0; i < queued; i++) {
            alSourceUnqueueBuffers(musicSource);
        }
    }

    @Override
    protected void hookStopAndClearAllMusicBuffers() {
        if (musicSource >= 0) {
            alSourceStop(musicSource);
            int queued = alGetSourcei(musicSource, AL_BUFFERS_QUEUED);
            while (queued > 0) {
                alSourceUnqueueBuffers(musicSource);
                queued--;
            }
            alSourcei(musicSource, AL_BUFFER, 0);
        }
    }

    @Override
    protected void hookRestartStreamIfDry() {
        int queued = alGetSourcei(musicSource, AL_BUFFERS_QUEUED);
        if (queued == 0) {
            alSourceStop(musicSource);
            alSourcei(musicSource, AL_BUFFER, 0);
            startStream();
        }
    }

    @Override
    protected void hookUploadStreamBuffer(int bufferId, short[] pcm, int sampleRate) {
        directShortBuffer.clear();
        directShortBuffer.put(pcm);
        directShortBuffer.flip();
        alBufferData(bufferId, AL_FORMAT_STEREO16, directShortBuffer, sampleRate);
    }

    @Override
    protected void hookPlayWavSfx(String sfxName, float pitch) {
        String filename = sfxFallback.get(sfxName);
        if (filename != null) {
            int source = alGenSources();
            sfxSources.add(source);
            playWav(filename, source, false, pitch);
        }
    }

    @Override
    protected void hookStopAndDeleteWavSfxSources() {
        for (int source : sfxSources) {
            alSourceStop(source);
            alDeleteSources(source);
        }
        sfxSources.clear();
    }

    @Override
    protected void hookCleanupStoppedWavSfx() {
        // Cleanup stopped sources
        Iterator<Integer> it = sfxSources.iterator();
        while (it.hasNext()) {
            int src = it.next();
            int state = alGetSourcei(src, AL_SOURCE_STATE);
            if (state == AL_STOPPED) {
                alDeleteSources(src);
                it.remove();
            }
        }
    }

    private void playWav(String resourcePath, int source, boolean loop) {
        playWav(resourcePath, source, loop, 1.0f);
    }

    private void playWav(String resourcePath, int source, boolean loop, float pitch) {
        try {
            // Check if buffer exists
            if (!buffers.containsKey(resourcePath)) {
                loadWav(resourcePath);
            }

            Integer buffer = buffers.get(resourcePath);
            if (buffer != null) {
                alSourceStop(source);
                alSourcei(source, AL_BUFFER, buffer);
                alSourcei(source, AL_LOOPING, loop ? AL_TRUE : AL_FALSE);
                alSourcef(source, AL_PITCH, pitch);
                alSourcePlay(source);
            } else {
                LOGGER.fine("Could not load buffer for: " + resourcePath);
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to play WAV: " + resourcePath + " - " + e.getMessage());
        }
    }

    private void loadWav(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOGGER.fine("Audio resource not found: " + resourcePath);
                return;
            }

            WavDecoder wav = WavDecoder.decode(is);

            int alFormat;
            if (wav.channels == 1) {
                alFormat = (wav.bitsPerSample == 8) ? AL_FORMAT_MONO8 : AL_FORMAT_MONO16;
            } else {
                alFormat = (wav.bitsPerSample == 8) ? AL_FORMAT_STEREO8 : AL_FORMAT_STEREO16;
            }

            ByteBuffer bufferData = MemoryUtil.memAlloc(wav.data.length);
            bufferData.put(wav.data);
            bufferData.flip();

            int buf = alGenBuffers();
            alBufferData(buf, alFormat, bufferData, wav.sampleRate);

            MemoryUtil.memFree(bufferData);

            buffers.put(resourcePath, buf);
        } catch (Exception e) {
            LOGGER.warning("Error loading WAV " + resourcePath + ": " + e.getMessage());
        }
    }

    @Override
    protected void hookDestroyDevice() {
        // Free the pre-allocated buffer
        if (directShortBuffer != null) {
            MemoryUtil.memFree(directShortBuffer);
            directShortBuffer = null;
        }

        // Delete all buffers
        for (int bufferId : buffers.values()) {
            alDeleteBuffers(bufferId);
        }
        buffers.clear();

        // Delete stream buffers
        if (streamBuffers != null) {
            for (int bufferId : streamBuffers) {
                alDeleteBuffers(bufferId);
            }
            streamBuffers = null;
        }

        // Delete sources
        if (musicSource >= 0) {
            alDeleteSources(musicSource);
            musicSource = -1;
        }
        for (int source : sfxSources) {
            alDeleteSources(source);
        }
        sfxSources.clear();

        // Destroy context and close device
        if (context != 0) {
            alcMakeContextCurrent(0);
            alcDestroyContext(context);
            context = 0;
        }
        if (device != 0) {
            alcCloseDevice(device);
            device = 0;
        }
    }

    @Override
    protected void hookPause() {
        if (musicSource >= 0) {
            alSourcePause(musicSource);
        }
        for (int src : sfxSources) {
            alSourcePause(src);
        }
    }

    @Override
    protected void hookResume() {
        if (musicSource >= 0) {
            int state = alGetSourcei(musicSource, AL_SOURCE_STATE);
            if (state == AL_PAUSED) {
                alSourcePlay(musicSource);
            }
        }
        for (int src : sfxSources) {
            int state = alGetSourcei(src, AL_SOURCE_STATE);
            if (state == AL_PAUSED) {
                alSourcePlay(src);
            }
        }
    }
}
