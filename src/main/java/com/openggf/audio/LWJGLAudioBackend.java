package com.openggf.audio;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.rewind.AudioBackendLogicalSnapshot;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSequencerSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.NoOpDeterministicAudioRuntime;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import org.lwjgl.openal.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import com.openggf.audio.driver.MusicStackEntry;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.driver.SmpsPresentationReplay;
import com.openggf.audio.driver.SmpsPresentationState;
import com.openggf.audio.synth.Ym2612Chip;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.PerformanceProfiler;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.*;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.ALC11.*;
import static org.lwjgl.openal.SOFTHRTF.*;

public class LWJGLAudioBackend implements AudioBackend {
    private static final Logger LOGGER = Logger.getLogger(LWJGLAudioBackend.class.getName());

    private final Object streamLock = new Object();
    private final SonicConfigurationService configService;
    private final PerformanceProfiler profiler;

    private long device;
    private long context;

    private final Map<String, Integer> buffers = new HashMap<>();
    private final List<Integer> sfxSources = new ArrayList<>();
    private int musicSource = -1;

    private AudioStream currentStream;
    private AudioStream sfxStream;
    private int[] streamBuffers;
    private static final int STREAM_BUFFER_COUNT = 3;
    private static final int STREAM_BUFFER_SIZE = 1024;
    // Pre-allocated buffers for fillBuffer() to avoid per-call allocations (~43 times/sec)
    private final short[] streamData = new short[STREAM_BUFFER_SIZE * 2];
    private int deviceSampleRate = 48000;  // Default fallback, updated in init()
    // Reusable DirectBuffer to avoid allocation in fillBuffer() hot path
    private ShortBuffer directShortBuffer;
    private SmpsSequencer currentSmps;
    private SmpsDriver smpsDriver;
    private DeterministicAudioRuntime deterministicAudioRuntime = NoOpDeterministicAudioRuntime.INSTANCE;

    private final Deque<MusicStackEntry> musicStack = new ArrayDeque<>();
    private int currentMusicId = -1;
    private AudioSourceDescriptor currentMusicDescriptor;
    private AudioSourceDescriptor pendingMusicDescriptor;
    private volatile boolean pendingRestore = false;
    private volatile boolean sfxBlocked = false;  // Block SFX during override jingle/fade-in (ROM: 1upPlaying, FadeInFlag)

    // Fallback mappings
    private final Map<Integer, String> musicFallback = new HashMap<>();
    private final Map<String, String> sfxFallback = new HashMap<>();

    // Mute/Solo State
    private final boolean[] fmUserMutes = new boolean[6];
    private final boolean[] fmUserSolos = new boolean[6];
    private final boolean[] psgUserMutes = new boolean[4];
    private final boolean[] psgUserSolos = new boolean[4];

    private boolean speedShoesEnabled = false;
    private int speedMultiplier = 1;
    private GameAudioProfile audioProfile;
    private SmpsSequencerConfig smpsConfig;

    public LWJGLAudioBackend() {
        this(SonicConfigurationService.createStandalone(), null);
    }

    public LWJGLAudioBackend(SonicConfigurationService configService) {
        this(configService, null);
    }

    public LWJGLAudioBackend(SonicConfigurationService configService, PerformanceProfiler profiler) {
        this.configService = Objects.requireNonNull(configService, "configService");
        this.profiler = profiler;
        // Initialize fallback mappings
        // SFX
        sfxFallback.put("JUMP", "sfx/jump.wav");
        sfxFallback.put("RING", "sfx/ring.wav");
        sfxFallback.put("SPINDASH", "sfx/spindash.wav");
        sfxFallback.put("SKID", "sfx/skid.wav");
    }

    @Override
    public void setAudioProfile(GameAudioProfile profile) {
        this.audioProfile = profile;
        this.smpsConfig = profile != null ? profile.getSequencerConfig() : null;
    }

    @Override
    public void init() {
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
            throw new RuntimeException(t);
        }
    }

    @Override
    public void playMusic(int musicId) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info("Requesting Music ID: " + Integer.toHexString(musicId));
        }
        stopStream(); // Stop any running stream
        clearMusicStack();
        currentMusicId = -1;

        // Try fallback map first
        String filename = musicFallback.get(musicId);
        if (filename == null) {
            // Default naming convention
            filename = "music/" + Integer.toHexString(musicId).toUpperCase() + ".wav";
        }

        playWav(filename, musicSource, true);
        currentMusicId = musicId;
        currentMusicDescriptor = consumePendingMusicDescriptor(musicId);
    }

    @Override
    public void playSmps(AbstractSmpsData data, DacData dacData) {
        playSmpsInternal(data, dacData, requireSmpsConfig(), /*forceOverride=*/ false);
    }

    /**
     * Builds {@link MusicReplayDependencies} from current backend config and
     * calls {@link SmpsPresentationReplay#applyToMusicBase}. Writes the new
     * driver + sequencer back onto the backend's SMPS fields. Caller owns
     * runtime/OpenAL binding before/after.
     */
    private void applyMusicBaseStart(AbstractSmpsData data, DacData dacData,
                                      SmpsSequencerConfig config,
                                      AudioSourceDescriptor musicDescriptor) {
        SmpsSequencer.Region region = "PAL".equalsIgnoreCase(
                configService.getString(SonicConfiguration.REGION))
                ? SmpsSequencer.Region.PAL : SmpsSequencer.Region.NTSC;
        SmpsPresentationReplay.MusicReplayDependencies musicDeps =
                new SmpsPresentationReplay.MusicReplayDependencies(
                        getSmpsOutputRate(),
                        region,
                        configService.getBoolean(SonicConfiguration.DAC_INTERPOLATE),
                        configService.getBoolean(SonicConfiguration.FM6_DAC_OFF),
                        configService.getBoolean(SonicConfiguration.PSG_NOISE_SHIFT_EVERY_TOGGLE),
                        speedShoesEnabled,
                        speedMultiplier);
        SmpsPresentationState state = snapshotPresentationState();
        SmpsPresentationReplay.applyToMusicBase(
                state, data, dacData, config, musicDescriptor, musicDeps);
        writeBackPresentationState(state);
    }

    /**
     * Snapshots the backend's SMPS-logical fields into a fresh
     * {@link SmpsPresentationState} for handing to
     * {@link SmpsPresentationReplay}. The {@link #musicStack} reference is
     * shared, not copied — mutations made by the helper apply directly to
     * the backend's stack.
     */
    private SmpsPresentationState snapshotPresentationState() {
        SmpsPresentationState state = new SmpsPresentationState();
        state.musicDriver = smpsDriver;
        state.activeMusicStream = currentStream;
        state.activeMusicSequencer = currentSmps;
        state.sfxStream = sfxStream;
        state.sfxBlocked = sfxBlocked;
        state.musicStack = musicStack;
        state.currentMusicId = currentMusicId;
        state.currentMusicDescriptor = currentMusicDescriptor;
        return state;
    }

    /**
     * Copies SMPS-logical fields from the helper's state object back onto
     * the backend. The {@link #musicStack} reference was shared, so no copy
     * is needed for it.
     */
    private void writeBackPresentationState(SmpsPresentationState state) {
        smpsDriver = state.musicDriver;
        currentStream = state.activeMusicStream;
        currentSmps = state.activeMusicSequencer;
        sfxStream = state.sfxStream;
        sfxBlocked = state.sfxBlocked;
        currentMusicId = state.currentMusicId;
        currentMusicDescriptor = state.currentMusicDescriptor;
    }

    @Override
    public void playSmps(AbstractSmpsData data, DacData dacData,
                         SmpsSequencerConfig config, boolean forceOverride) {
        SmpsSequencerConfig effectiveConfig = (config != null) ? config : requireSmpsConfig();
        playSmpsInternal(data, dacData, effectiveConfig, forceOverride);
    }

    private void playSmpsInternal(AbstractSmpsData data, DacData dacData,
                                   SmpsSequencerConfig effectiveConfig,
                                   boolean forceOverride) {
        int musicId = data.getId();
        boolean isOverride = forceOverride
                || (audioProfile != null && audioProfile.isMusicOverride(musicId));
        if (isOverride) {
            // ROM: only the 1-up jingle (isSfxBlockingMusic) kills active SFX.
            // Non-blocking overrides (invincibility, Super Sonic) let SFX continue.
            boolean sfxBlocking = audioProfile != null && audioProfile.isSfxBlockingMusic(musicId);
            boolean currentIsOverride = audioProfile != null
                    && audioProfile.isMusicOverride(currentMusicId);
            synchronized (streamLock) {
                SmpsPresentationState state = snapshotPresentationState();
                boolean sfxStreamCleared = SmpsPresentationReplay.applyToMusicPreludeOverride(
                        state, musicId, sfxBlocking, currentIsOverride);
                writeBackPresentationState(state);
                if (sfxStreamCleared) {
                    deterministicAudioRuntime.clearSfxStream();
                }
            }
            // Just disconnect the current driver from the source without stopping/clearing it.
            alSourceStop(musicSource);
            alSourcei(musicSource, AL_BUFFER, 0);
        } else {
            stopStream();
            alSourceStop(musicSource);
            synchronized (streamLock) {
                SmpsPresentationState state = snapshotPresentationState();
                SmpsPresentationReplay.applyToMusicPreludeNonOverride(state);
                writeBackPresentationState(state);
                deterministicAudioRuntime.clearSfxStream();
            }
            // pendingRestore is backend-flow-control metadata, not SMPS state:
            // the non-override-prelude helper deliberately does not touch it.
            pendingRestore = false;
        }

        AudioSourceDescriptor musicDescriptor = consumePendingMusicDescriptor(musicId);
        applyMusicBaseStart(data, dacData, effectiveConfig, musicDescriptor);
        currentMusicId = musicId;
        currentMusicDescriptor = musicDescriptor;
        updateSynthesizerConfig();
        synchronized (streamLock) {
            deterministicAudioRuntime.setMusicStream(smpsDriver);
            currentStream = smpsDriver;
        }
    }

    @Override
    public void playSfxSmps(AbstractSmpsData data, DacData dacData) {
        playSfxSmps(data, dacData, 1.0f);
    }

    @Override
    public void playSfxSmps(AbstractSmpsData data, DacData dacData, float pitch) {
        playSfxSmps(data, dacData, pitch, null);
    }

    @Override
    public void playSfxSmps(AbstractSmpsData data, DacData dacData, float pitch,
                             SmpsSequencerConfig config) {
        // SMPS-logical mutation is delegated to SmpsPresentationReplay so the
        // reverse-resynth worker can apply the same logic to its private
        // presentation state without touching backend OpenAL/runtime state.
        // The backend retains responsibility for binding the runtime SFX
        // stream when a new standalone driver is created.
        SmpsPresentationReplay.SfxReplayDependencies deps =
                new SmpsPresentationReplay.SfxReplayDependencies(
                        getSmpsOutputRate(),
                        configService.getBoolean(SonicConfiguration.DAC_INTERPOLATE),
                        configService.getBoolean(SonicConfiguration.FM6_DAC_OFF),
                        configService.getBoolean(SonicConfiguration.PSG_NOISE_SHIFT_EVERY_TOGGLE),
                        audioProfile,
                        config != null ? config : requireSmpsConfig());
        // Capture the freshly-created standalone driver under the lock so a
        // concurrent path (e.g. stopAllSfx clearing sfxStream) can't slip in
        // between the unlock and the runtime binding and make us bind the
        // wrong reference.
        AudioStream newStandaloneStream = null;
        synchronized (streamLock) {
            SmpsPresentationState state = new SmpsPresentationState();
            state.musicDriver = smpsDriver;
            state.activeMusicStream = currentStream;
            state.activeMusicSequencer = currentSmps;
            state.sfxStream = sfxStream;
            state.sfxBlocked = sfxBlocked;
            SmpsPresentationReplay.SfxApplyResult result =
                    SmpsPresentationReplay.applyToSfx(
                            state, data, dacData, pitch, config, deps);
            // Write back any state the replay may have updated.
            sfxStream = state.sfxStream;
            if (result == SmpsPresentationReplay.SfxApplyResult.NEW_STANDALONE_DRIVER) {
                newStandaloneStream = sfxStream;
            }
        }
        if (newStandaloneStream != null) {
            deterministicAudioRuntime.setSfxStream(newStandaloneStream);
        }
    }

    private void startStream() {
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

    private void stopStream() {
        if (musicSource >= 0) {
            alSourceStop(musicSource);
            int queued = alGetSourcei(musicSource, AL_BUFFERS_QUEUED);
            while (queued > 0) {
                alSourceUnqueueBuffers(musicSource);
                queued--;
            }
            alSourcei(musicSource, AL_BUFFER, 0);
        }

        currentStream = null;
        currentSmps = null;
        deterministicAudioRuntime.clearMusicStream();
        deterministicAudioRuntime.flushPresentationFifo();
        if (smpsDriver != null) {
            smpsDriver.stopAll();
            smpsDriver = null;
        }
        currentMusicId = -1;
        currentMusicDescriptor = null;
    }

    private void updateStream() {
        // Check for pending music restoration (deferred from E4 handler)
        if (pendingRestore) {
            pendingRestore = false;
            doRestoreMusic();
            return;
        }

        boolean hasStream;
        synchronized (streamLock) {
            hasStream = currentStream != null
                    || sfxStream != null
                    || deterministicAudioRuntime.hasActivePresentation();
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
    public void restoreMusic() {
        // Defer actual restoration to next updateStream cycle to avoid
        // modifying buffers while they're being rendered
        if (!musicStack.isEmpty()) {
            pendingRestore = true;
        }
    }

    private void doRestoreMusic() {
        // Peek + validate before doing any OpenAL teardown. The original
        // implementation returned early on a malformed top-of-stack
        // *without* stopping the source; preserve that behaviour so a
        // pathological stack entry can't silence the currently-playing
        // override.
        MusicStackEntry peek = musicStack.peekFirst();
        if (peek == null || peek.stream() == null || peek.sequencer() == null
                || peek.driver() == null) {
            return;
        }

        // Stop the current (invincibility/extra-life) music stream and
        // unqueue ALL buffers before installing the new state — the buffer
        // purge must happen before startStream queues new buffers on top.
        alSourceStop(musicSource);
        int queued = alGetSourcei(musicSource, AL_BUFFERS_QUEUED);
        for (int i = 0; i < queued; i++) {
            alSourceUnqueueBuffers(musicSource);
        }

        SmpsPresentationReplay.RestoreResult result;
        synchronized (streamLock) {
            SmpsPresentationState state = snapshotPresentationState();
            // The fade-complete callback flips THIS backend's sfxBlocked
            // field when the restored 1-up fade finishes. The worker thread
            // (future) would supply a callback that mutates its own state.
            result = SmpsPresentationReplay.applyToRestoreMusic(
                    state, speedShoesEnabled, () -> sfxBlocked = false);
            writeBackPresentationState(state);
            if (result.restored()) {
                bindRuntimePresentationStreams();
            }
        }

        if (result.restored()) {
            startStream();
        }
    }

    private void fillBuffer(int bufferId) {
        // Profiler sections inside fillBuffer. Names describe what is actually wrapped,
        // not a clean SMPS/synth/resample split — SmpsDriver.read() interleaves the
        // sequencer, YM2612/PSG synthesis, and the blip resampler at sample granularity,
        // so those three phases cannot be separated at this seam.
        //   - audio.music_stream: presentation drain via fillPresentationBuffer (SMPS +
        //     synth + resample, interleaved with SFX mixing inside the deterministic
        //     runtime's advanceFrame).
        //   - audio.upload:       DirectBuffer fill and OpenAL upload.
        // Sections do not nest (see PerformanceProfiler.beginSection), so starting any of
        // these inside the outer "audio" section in GameLoop will truncate that section
        // — that's expected. Called potentially multiple times per audio tick (once per
        // processed buffer in updateStream); PerformanceProfiler accumulates section time.
        int sampleRate;
        synchronized (streamLock) {
            beginProfileSection("audio.music_stream");
            try {
                fillPresentationBuffer(streamData, STREAM_BUFFER_SIZE);
                sampleRate = (int) Math.round(getStreamSampleRate());
            } finally {
                endProfileSection("audio.music_stream");
            }
        }

        // Keep DirectBuffer/OpenAL operations outside lock to minimize contention
        beginProfileSection("audio.upload");
        try {
            directShortBuffer.clear();
            directShortBuffer.put(streamData);
            directShortBuffer.flip();
            alBufferData(bufferId, AL_FORMAT_STEREO16, directShortBuffer, sampleRate);
        } finally {
            endProfileSection("audio.upload");
        }
    }

    /**
     * Test seam for the music-presentation path. Drains the deterministic
     * runtime, which is the sole source of presentation PCM post-migration.
     * Package-private so {@code TestLwjglRuntimePresentationRoundTrip} can
     * exercise the drain without standing up OpenAL.
     */
    void fillPresentationBuffer(short[] target, int frames) {
        Arrays.fill(target, 0, frames * 2, (short) 0);
        requirePresentationRuntime().drainPcm(target, frames);
        clearCompletedRuntimeSfxIfNeeded();
    }

    /** Test seam: whether the OpenAL streaming buffers have been allocated. */
    boolean isStreamStarted() {
        return streamBuffers != null;
    }

    private void beginProfileSection(String section) {
        if (profiler != null) {
            profiler.beginSection(section);
        }
    }

    private void endProfileSection(String section) {
        if (profiler != null) {
            profiler.endSection(section);
        }
    }

    private DeterministicAudioRuntime requirePresentationRuntime() {
        DeterministicAudioRuntime runtime = deterministicAudioRuntime;
        if (!runtime.providesPresentationPcm()) {
            throw new IllegalStateException(
                    "Presentation runtime required but attached runtime is "
                            + runtime.getClass().getName());
        }
        return runtime;
    }

    private void bindRuntimePresentationStreams() {
        deterministicAudioRuntime.setMusicStream(smpsDriver);
        if (sfxStream != null) {
            deterministicAudioRuntime.setSfxStream(sfxStream);
        } else {
            deterministicAudioRuntime.clearSfxStream();
        }
    }

    private void clearCompletedRuntimeSfxIfNeeded() {
        if (sfxStream != null && sfxStream.isComplete()) {
            sfxStream = null;
            deterministicAudioRuntime.clearSfxStream();
        }
    }

    private double getSmpsOutputRate() {
        boolean internalRate = configService.getBoolean(SonicConfiguration.AUDIO_INTERNAL_RATE_OUTPUT);
        // Use device's native sample rate to avoid OpenAL resampling - our BlipResampler handles it
        return internalRate ? Ym2612Chip.getInternalRate() : deviceSampleRate;
    }

    private void applyPsgNoiseConfig(SmpsDriver driver) {
        boolean everyToggle = configService.getBoolean(SonicConfiguration.PSG_NOISE_SHIFT_EVERY_TOGGLE);
        driver.setPsgNoiseShiftOnEveryToggle(everyToggle);
    }

    private double getStreamSampleRate() {
        double rate = deviceSampleRate;  // Use device rate as fallback to match getSmpsOutputRate()
        synchronized (streamLock) {
            SmpsDriver musicDriver = (currentStream instanceof SmpsDriver driver) ? driver : null;
            SmpsDriver sfxDriver = (sfxStream instanceof SmpsDriver driver) ? driver : null;
            if (musicDriver != null) {
                rate = musicDriver.getOutputSampleRate();
            } else if (sfxDriver != null) {
                rate = sfxDriver.getOutputSampleRate();
            }
            if (musicDriver != null && sfxDriver != null) {
                double sfxRate = sfxDriver.getOutputSampleRate();
                if (Math.abs(rate - sfxRate) > 1e-6) {
                    LOGGER.warning("Audio stream sample rate mismatch: music=" + rate + " sfx=" + sfxRate);
                }
            }
        }
        return rate;
    }

    /**
     * Returns a debug snapshot of the current SMPS sequencer if one is playing.
     */
    public SmpsSequencer.DebugState getDebugState() {
        synchronized (streamLock) {
            return currentSmps != null ? currentSmps.debugState() : null;
        }
    }

    SmpsDriver musicDriverForTesting() {
        synchronized (streamLock) {
            return smpsDriver;
        }
    }

    @Override
    public AudioBackendLogicalSnapshot captureLogicalSnapshot() {
        synchronized (streamLock) {
            List<AudioSourceDescriptor> overrides = new ArrayList<>(musicStack.size());
            for (MusicStackEntry state : musicStack) {
                overrides.add(state.descriptor());
            }
            SmpsDriverSnapshot musicDriverSnapshot = smpsDriver != null ? smpsDriver.captureSnapshot() : null;
            SmpsDriverSnapshot sfxDriverSnapshot = sfxStream instanceof SmpsDriver sfxDriver
                    ? sfxDriver.captureSnapshot()
                    : null;
            return new AudioBackendLogicalSnapshot(
                    currentMusicDescriptor,
                    sfxBlocked,
                    pendingRestore,
                    speedShoesEnabled,
                    speedMultiplier,
                    overrides,
                    musicDriverSnapshot,
                    sfxDriverSnapshot);
        }
    }

    @Override
    public void restoreLogicalSnapshot(AudioBackendLogicalSnapshot snapshot) {
        restoreLogicalSnapshot(snapshot, SmpsDriverSnapshot.liveReferences());
    }

    @Override
    public void restoreLogicalSnapshot(
            AudioBackendLogicalSnapshot snapshot,
            SmpsDriverSnapshot.DependencyResolver resolver) {
        restoreLogicalSnapshot(snapshot, resolver, false);
    }

    @Override
    public void restoreLogicalSnapshot(
            AudioBackendLogicalSnapshot snapshot,
            SmpsDriverSnapshot.DependencyResolver resolver,
            boolean preservePresentationQueue) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(resolver, "resolver");
        synchronized (streamLock) {
            currentStream = null;
            currentSmps = null;
            smpsDriver = null;
            sfxStream = null;
            currentMusicDescriptor = snapshot.currentMusic();
            currentMusicId = snapshot.currentMusic() != null ? snapshot.currentMusic().id() : -1;
            pendingMusicDescriptor = null;
            pendingRestore = snapshot.pendingRestore();
            sfxBlocked = snapshot.sfxBlocked();
            speedShoesEnabled = snapshot.speedShoesEnabled();
            speedMultiplier = snapshot.speedMultiplier();

            musicStack.clear();
            pendingRestore = false;

            if (snapshot.musicDriver() != null) {
                smpsDriver = newConfiguredSmpsDriver();
                smpsDriver.restoreSnapshot(snapshot.musicDriver(), resolver);
                currentStream = smpsDriver;
                currentSmps = smpsDriver.firstMusicSequencer();
                rebindFadeCompleteCallbackIfNeeded();
            }
            if (snapshot.standaloneSfxDriver() != null) {
                SmpsDriver restoredSfxDriver = newConfiguredSmpsDriver();
                restoredSfxDriver.restoreSnapshot(snapshot.standaloneSfxDriver(), resolver);
                sfxStream = restoredSfxDriver;
            }
            bindRuntimePresentationStreams();
            if (!preservePresentationQueue) {
                deterministicAudioRuntime.flushPresentationFifo();
            }
        }
        if (!preservePresentationQueue && musicSource >= 0) {
            alSourceStop(musicSource);
            int queued = alGetSourcei(musicSource, AL_BUFFERS_QUEUED);
            while (queued > 0) {
                alSourceUnqueueBuffers(musicSource);
                queued--;
            }
            alSourcei(musicSource, AL_BUFFER, 0);
        }
    }

    private void rebindFadeCompleteCallbackIfNeeded() {
        if (sfxBlocked && smpsDriver != null && currentSmps != null) {
            SmpsSequencerSnapshot snapshot = currentSmps.captureSnapshot();
            if (snapshot.fade().active() && !snapshot.fade().fadeOut()) {
            smpsDriver.bindMusicFadeCompleteCallback(() -> sfxBlocked = false);
            }
        }
    }

    private SmpsDriver newConfiguredSmpsDriver() {
        SmpsDriver driver = new SmpsDriver(getSmpsOutputRate());
        driver.setDacInterpolate(configService.getBoolean(SonicConfiguration.DAC_INTERPOLATE));
        driver.setOutputSampleRate(getSmpsOutputRate());
        applyPsgNoiseConfig(driver);
        return driver;
    }

    @Override
    public void prepareLogicalMusicSource(AudioSourceDescriptor descriptor) {
        pendingMusicDescriptor = descriptor;
    }

    @Override
    public void attachDeterministicAudioRuntime(DeterministicAudioRuntime runtime) {
        synchronized (streamLock) {
            Objects.requireNonNull(runtime, "runtime");
            if (supportsDeterministicRuntimePresentation()
                    && !runtime.providesPresentationPcm()) {
                throw new IllegalStateException(
                        "LWJGLAudioBackend declares deterministic runtime presentation"
                                + " support but was attached a runtime that does not"
                                + " provide presentation PCM: "
                                + runtime.getClass().getName());
            }
            deterministicAudioRuntime = runtime;
            bindRuntimePresentationStreams();
        }
    }

    @Override
    public boolean supportsDeterministicRuntimePresentation() {
        return true;
    }

    @Override
    public int outputSampleRate() {
        return (int) Math.round(getSmpsOutputRate());
    }

    @Override
    public void beginReversePresentation() {
        // No-op: AudioManager.beginReverseAudioPresentation already invokes
        // deterministicAudioRuntime.beginReversePresentation independently.
        // The backend has no reverse-presentation state of its own
        // post-migration.
    }

    @Override
    public void endReversePresentation() {
        // No-op: symmetric with beginReversePresentation above.
    }

    @Override
    public void playSfx(String sfxName) {
        playSfx(sfxName, 1.0f);
    }

    @Override
    public void playSfx(String sfxName, float pitch) {
        String filename = sfxFallback.get(sfxName);
        if (filename != null) {
            int source = alGenSources();
            sfxSources.add(source);
            playWav(filename, source, false, pitch);
        } else {
            LOGGER.fine("SFX not found in fallback map: " + sfxName);
        }
    }

    @Override
    public void stopPlayback() {
        stopStream();
        alSourceStop(musicSource);
        alSourcei(musicSource, AL_BUFFER, 0);
        synchronized (streamLock) {
            currentStream = null;
            currentSmps = null;
            currentMusicId = -1;
            currentMusicDescriptor = null;
            clearMusicStack();
            // Also stop any playing SFX to prevent them persisting across level transitions
            if (sfxStream instanceof SmpsDriver sfxDriver) {
                sfxDriver.stopAll();
            }
            sfxStream = null;
            deterministicAudioRuntime.clearSfxStream();
        }
        // Stop and cleanup WAV-based SFX sources
        for (int source : sfxSources) {
            alSourceStop(source);
            alDeleteSources(source);
        }
        sfxSources.clear();
    }

    @Override
    public void fadeOutMusic(int steps, int delay) {
        // Fade only music, not SFX - delegated to the music sequencer
        if (currentSmps != null) {
            currentSmps.triggerFadeOut(steps, delay);
        }
    }

    @Override
    public void endMusicOverride(int musicId) {
        if (currentSmps != null && currentMusicId == musicId) {
            restoreMusic();
            return;
        }
        removeSavedOverride(musicId);
    }

    @Override
    public void toggleMute(ChannelType type, int channel) {
        switch (type) {
            case FM:
            case DAC:
                if (channel >= 0 && channel < 6) {
                    fmUserMutes[channel] = !fmUserMutes[channel];
                }
                break;
            case PSG:
                if (channel >= 0 && channel < 4) {
                    psgUserMutes[channel] = !psgUserMutes[channel];
                }
                break;
        }
        updateSynthesizerConfig();
    }

    @Override
    public void toggleSolo(ChannelType type, int channel) {
        switch (type) {
            case FM:
            case DAC:
                if (channel >= 0 && channel < 6) {
                    fmUserSolos[channel] = !fmUserSolos[channel];
                }
                break;
            case PSG:
                if (channel >= 0 && channel < 4) {
                    psgUserSolos[channel] = !psgUserSolos[channel];
                }
                break;
        }
        updateSynthesizerConfig();
    }

    @Override
    public boolean isMuted(ChannelType type, int channel) {
        return switch (type) {
            case FM, DAC -> (channel >= 0 && channel < 6) && fmUserMutes[channel];
            case PSG -> (channel >= 0 && channel < 4) && psgUserMutes[channel];
        };
    }

    @Override
    public boolean isSoloed(ChannelType type, int channel) {
        return switch (type) {
            case FM, DAC -> (channel >= 0 && channel < 6) && fmUserSolos[channel];
            case PSG -> (channel >= 0 && channel < 4) && psgUserSolos[channel];
        };
    }

    @Override
    public void setSpeedShoes(boolean enabled) {
        this.speedShoesEnabled = enabled;
        synchronized (streamLock) {
            if (currentSmps != null) {
                currentSmps.setSpeedShoes(enabled);
            }
        }
    }

    @Override
    public void setSpeedMultiplier(int multiplier) {
        this.speedMultiplier = multiplier;
        synchronized (streamLock) {
            if (currentSmps != null) {
                currentSmps.setSpeedMultiplier(multiplier);
            }
        }
    }

    @Override
    public void changeMusicTempo(int newDividingTiming) {
        synchronized (streamLock) {
            if (currentSmps != null) {
                currentSmps.updateDividingTiming(newDividingTiming);
            }
        }
    }

    private void updateSynthesizerConfig() {
        if (currentSmps == null || currentSmps.getSynthesizer() == null)
            return;
        var synth = currentSmps.getSynthesizer();

        boolean anyFmSolo = false;
        for (boolean s : fmUserSolos)
            if (s)
                anyFmSolo = true;

        boolean anyPsgSolo = false;
        for (boolean s : psgUserSolos)
            if (s)
                anyPsgSolo = true;

        boolean anySolo = anyFmSolo || anyPsgSolo;

        for (int i = 0; i < 6; i++) {
            boolean soloed = fmUserSolos[i];
            boolean muted = fmUserMutes[i];
            if (soloed)
                muted = false;
            else if (anySolo)
                muted = true;
            synth.setFmMute(i, muted);
        }

        for (int i = 0; i < 4; i++) {
            boolean soloed = psgUserSolos[i];
            boolean muted = psgUserMutes[i];
            if (soloed)
                muted = false;
            else if (anySolo)
                muted = true;
            synth.setPsgMute(i, muted);
        }
    }

    private SmpsSequencerConfig requireSmpsConfig() {
        if (smpsConfig == null) {
            throw new IllegalStateException("SMPS sequencer config not set");
        }
        return smpsConfig;
    }

    private void pushCurrentState() {
        if (currentStream == null || currentSmps == null || smpsDriver == null) {
            return;
        }
        musicStack.push(new MusicStackEntry(currentStream, currentSmps, smpsDriver, currentMusicId,
                currentMusicDescriptor));
    }

    private void clearMusicStack() {
        musicStack.clear();
        pendingRestore = false;
        sfxBlocked = false;  // Unblock SFX when stack is cleared (e.g., level transition)
    }

    private boolean removeSavedOverride(int musicId) {
        SmpsPresentationState state = snapshotPresentationState();
        return SmpsPresentationReplay.removeSavedOverride(state, musicId);
    }

    private static AudioSourceDescriptor describeMusic(int musicId) {
        return musicId >= 0 ? AudioSourceDescriptor.baseMusic(musicId) : null;
    }

    private AudioSourceDescriptor consumePendingMusicDescriptor(int musicId) {
        AudioSourceDescriptor descriptor = pendingMusicDescriptor != null
                ? pendingMusicDescriptor
                : describeMusic(musicId);
        pendingMusicDescriptor = null;
        return descriptor;
    }

    private static SmpsSourceDescriptor describeSmpsSource(
            AudioSourceDescriptor descriptor,
            AbstractSmpsData data,
            boolean sfx) {
        if (descriptor == null) {
            return sfx ? SmpsSourceDescriptor.baseSfx(data) : SmpsSourceDescriptor.baseMusic(data);
        }
        return switch (descriptor.route()) {
            case BASE_MUSIC_ID, FALLBACK_MUSIC_ID -> SmpsSourceDescriptor.baseMusic(data);
            case BASE_SFX_ID -> SmpsSourceDescriptor.baseSfx(data);
            case BASE_SFX_NAME, FALLBACK_SFX_NAME -> SmpsSourceDescriptor.baseNamedSfx(descriptor.name(), data);
            case DONOR_MUSIC_ID -> SmpsSourceDescriptor.donorMusic(descriptor.donorGameId(), data);
            case DONOR_SFX_ID -> SmpsSourceDescriptor.donorSfx(descriptor.donorGameId(), data);
            case SYSTEM_COMMAND -> SmpsSourceDescriptor.from(data);
        };
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
    public void update() {
        updateStream();

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

    @Override
    public void destroy() {
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
            alcDestroyContext(context);
            context = 0;
        }
        if (device != 0) {
            alcCloseDevice(device);
            device = 0;
        }
    }

    @Override
    public void stopAllSfx() {
        // Stop SFX sequencers in the active music driver (mixed into currentStream)
        if (smpsDriver != null) {
            smpsDriver.stopAllSfx();
        }
        // Stop standalone SFX stream (used when SFX played before any music started)
        synchronized (streamLock) {
            if (sfxStream instanceof SmpsDriver sfxDriver) {
                sfxDriver.stopAll();
            }
            sfxStream = null;
            deterministicAudioRuntime.clearSfxStream();
        }
    }

    @Override
    public void pause() {
        if (musicSource >= 0) {
            alSourcePause(musicSource);
        }
        for (int src : sfxSources) {
            alSourcePause(src);
        }
    }

    @Override
    public void resume() {
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
