package com.openggf.audio;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.rewind.AudioBackendLogicalSnapshot;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSequencerSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.PcmHistoryRing;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.synth.Ym2612Chip;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.PerformanceProfiler;

import java.util.*;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Device-agnostic base for the SMPS synthesis audio backend.
 *
 * <p>This class owns ALL SMPS synthesis, the sequencer / music-stack / SFX
 * lifecycle, speed/tempo state, logical snapshot capture/restore, the
 * PCM-history rewind ring, and deterministic-runtime binding. It contains
 * <strong>zero</strong> OpenAL ({@code al*}/{@code alc*}) calls and no
 * {@code org.lwjgl.openal.*} imports. Every point where synthesis output must
 * touch a real device goes through a {@code protected abstract} hook method,
 * which concrete subclasses implement:
 *
 * <ul>
 *   <li>{@link LWJGLAudioBackend} relocates the original OpenAL device code
 *       verbatim into each hook (live audio).</li>
 *   <li>{@link HeadlessSmpsAudioBackend} implements every hook as a no-op
 *       (except device init, which fixes the synthesis sample rate to 48000
 *       and initialises {@code pcmHistory}). The deterministic capture runtime
 *       is the sole consumer of synthesized PCM in that mode.</li>
 * </ul>
 */
public abstract class AbstractSmpsAudioBackend implements AudioBackend {
    private static final Logger LOGGER = Logger.getLogger(AbstractSmpsAudioBackend.class.getName());

    protected final Object streamLock = new Object();
    protected final SonicConfigurationService configService;
    protected final PerformanceProfiler profiler;

    protected static final int STREAM_BUFFER_SIZE = 1024;
    // Pre-allocated buffers for fillBuffer() to avoid per-call allocations (~43 times/sec)
    protected final short[] streamData = new short[STREAM_BUFFER_SIZE * 2];
    protected final short[] sfxStreamData = new short[STREAM_BUFFER_SIZE * 2];

    protected AudioStream currentStream;
    protected AudioStream sfxStream;
    private SmpsSequencer currentSmps;
    private SmpsDriver smpsDriver;
    private DeterministicAudioRuntime deterministicAudioRuntime;
    protected PcmHistoryRing pcmHistory;
    private PcmHistoryRing.ReverseCursor reverseCursor;
    private double pendingReverseRate = 1.0;

    private static class MusicState {
        final AudioStream stream;
        final SmpsSequencer smps;
        final SmpsDriver driver;
        final int musicId;
        final AudioSourceDescriptor descriptor;

        MusicState(AudioStream stream, SmpsSequencer smps, SmpsDriver driver, int musicId,
                   AudioSourceDescriptor descriptor) {
            this.stream = stream;
            this.smps = smps;
            this.driver = driver;
            this.musicId = musicId;
            this.descriptor = descriptor;
        }
    }

    private final Deque<MusicState> musicStack = new ArrayDeque<>();
    private int currentMusicId = -1;
    private AudioSourceDescriptor currentMusicDescriptor;
    private AudioSourceDescriptor pendingMusicDescriptor;
    protected volatile boolean pendingRestore = false;
    protected volatile boolean sfxBlocked = false;  // Block SFX during override jingle/fade-in (ROM: 1upPlaying, FadeInFlag)

    // Fallback mappings
    protected final Map<Integer, String> musicFallback = new HashMap<>();
    protected final Map<String, String> sfxFallback = new HashMap<>();

    // Mute/Solo State
    private final boolean[] fmUserMutes = new boolean[6];
    private final boolean[] fmUserSolos = new boolean[6];
    private final boolean[] psgUserMutes = new boolean[4];
    private final boolean[] psgUserSolos = new boolean[4];

    private boolean speedShoesEnabled = false;
    private int speedMultiplier = 1;
    private GameAudioProfile audioProfile;
    private SmpsSequencerConfig smpsConfig;

    protected AbstractSmpsAudioBackend(SonicConfigurationService configService, PerformanceProfiler profiler) {
        this.configService = Objects.requireNonNull(configService, "configService");
        this.profiler = profiler;
        // Initialize fallback mappings
        // SFX
        sfxFallback.put("JUMP", "sfx/jump.wav");
        sfxFallback.put("RING", "sfx/ring.wav");
        sfxFallback.put("SPINDASH", "sfx/spindash.wav");
        sfxFallback.put("SKID", "sfx/skid.wav");
    }

    // ------------------------------------------------------------------
    // Device-output hooks. Implemented with verbatim OpenAL code in
    // LWJGLAudioBackend; no-ops (plus sample-rate init) in headless.
    // ------------------------------------------------------------------

    /**
     * Opens/initialises the output device (or, headless, fixes the synthesis
     * sample rate and initialises {@link #pcmHistory}). Must establish
     * {@link #getDeviceSampleRate()} and create {@link #pcmHistory}.
     */
    protected abstract void hookInitDevice();

    /** Tears the output device down (frees buffers/sources/context/device). */
    protected abstract void hookDestroyDevice();

    /**
     * Begins streaming: fills and queues the initial stream buffers and starts
     * playback. Headless: no-op.
     */
    protected abstract void hookStartStream();

    /**
     * Stops streaming and unqueues/clears the music source's buffers (the body
     * of the old {@code stopStream()} device section). Guarded internally on a
     * valid music source. Headless: no-op.
     */
    protected abstract void hookStopStreamSource();

    /**
     * Pumps the device stream: unqueues processed buffers, refills them via
     * {@link #fillBuffer(int)}, re-queues, and re-starts playback if stalled.
     * Headless: no-op (the capture runtime reads synthesis directly).
     */
    protected abstract void hookUpdateStream();

    /**
     * Stops the music source and detaches its current buffer
     * ({@code alSourceStop} + {@code alSourcei(AL_BUFFER, 0)}), without
     * unqueueing streamed buffers. Used by the override-swap paths in
     * {@code playSmps}. Headless: no-op.
     */
    protected abstract void hookStopAndClearMusicSource();

    /**
     * Stops the music source and unqueues ALL queued buffers (no final
     * {@code alSourcei(AL_BUFFER,0)}). Used by {@code doRestoreMusic}.
     * Headless: no-op.
     */
    protected abstract void hookStopAndUnqueueAllMusicBuffers();

    /**
     * Stops the music source, unqueues ALL queued buffers, and detaches the
     * current buffer. Used by {@code restoreLogicalSnapshot} and
     * {@code stopPlayback}. Guarded internally on a valid music source.
     * Headless: no-op.
     */
    protected abstract void hookStopAndClearAllMusicBuffers();

    /**
     * Restarts the device stream if the music source has run dry (no queued
     * buffers): the {@code playSfxSmps} "ensure stream is running" tail.
     * Headless: no-op.
     */
    protected abstract void hookRestartStreamIfDry();

    /** Stops and deletes any WAV-based SFX sources. Headless: no-op. */
    protected abstract void hookStopAndDeleteWavSfxSources();

    /** Uploads one stream buffer's worth of PCM to the device. Headless: no-op. */
    protected abstract void hookUploadStreamBuffer(int bufferId, short[] pcm, int sampleRate);

    /** Plays a named WAV SFX through the device. Headless: no-op. */
    protected abstract void hookPlayWavSfx(String sfxName, float pitch);

    /** Cleans up finished WAV SFX sources (called from {@link #update()}). Headless: no-op. */
    protected abstract void hookCleanupStoppedWavSfx();

    /** Pauses device playback. Headless: no-op. */
    protected abstract void hookPause();

    /** Resumes device playback. Headless: no-op. */
    protected abstract void hookResume();

    /**
     * Returns the negotiated device sample rate used to drive synthesis output.
     * LWJGL returns the OpenAL-negotiated rate; headless returns 48000.
     */
    protected abstract int getDeviceSampleRate();

    // ------------------------------------------------------------------
    // Shared backend logic.
    // ------------------------------------------------------------------

    @Override
    public void setAudioProfile(GameAudioProfile profile) {
        this.audioProfile = profile;
        this.smpsConfig = profile != null ? profile.getSequencerConfig() : null;
    }

    @Override
    public void init() {
        hookInitDevice();
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

        playWavMusic(filename, musicId);
        currentMusicId = musicId;
        currentMusicDescriptor = consumePendingMusicDescriptor(musicId);
    }

    /**
     * Plays a WAV-backed music file on the device music source (loop). The base
     * has no device source, so this routes through the device hook. Default
     * fallback never loads a WAV in headless mode.
     */
    protected void playWavMusic(String filename, int musicId) {
        // Device subclasses override with the real WAV path; headless ignores.
    }

    @Override
    public void playSmps(AbstractSmpsData data, DacData dacData) {
        int musicId = data.getId();
        boolean isOverride = audioProfile != null && audioProfile.isMusicOverride(musicId);
        if (isOverride) {
            // ROM behavior: only 1-up jingle (isSfxBlockingMusic) kills active SFX.
            // Non-blocking overrides (invincibility, Super Sonic) let SFX continue.
            if (audioProfile.isSfxBlockingMusic(musicId)) {
                synchronized (streamLock) {
                    if (smpsDriver != null) {
                        smpsDriver.stopAllSfx();
                    }
                    if (sfxStream instanceof SmpsDriver sfxDriver) {
                        sfxDriver.stopAll();
                    }
                    sfxStream = null;
                    if (runtimeProvidesPresentationPcm()) {
                        deterministicAudioRuntime.clearSfxStream();
                    }
                }
                sfxBlocked = true;
            }
            // Push current state unless re-triggering the same override (e.g.
            // collecting invincibility while already invincible).  When a
            // *different* override starts (e.g. 1-up during invincibility), the
            // active override must be saved so it resumes when the new one ends.
            boolean currentIsOverride = audioProfile != null && audioProfile.isMusicOverride(currentMusicId);
            if (!currentIsOverride || currentMusicId != musicId) {
                pushCurrentState();
            }

            // Just disconnect the current driver from the source without stopping/clearing it.
            hookStopAndClearMusicSource();
            currentStream = null;
            currentSmps = null;
            smpsDriver = null;
        } else {
            stopStream();
            // Stop music source if playing wav
            hookStopAndClearMusicSource();
            clearMusicStack();
            // Clean up standalone SFX stream - stopStream() only handles currentStream/smpsDriver,
            // but SFX played before any music was active use a separate sfxStream SmpsDriver.
            // Without this, the sfxStream persists and keeps rendering into fillBuffer() indefinitely.
            synchronized (streamLock) {
                if (sfxStream instanceof SmpsDriver sfxDriver) {
                    sfxDriver.stopAll();
                }
                sfxStream = null;
                if (runtimeProvidesPresentationPcm()) {
                    deterministicAudioRuntime.clearSfxStream();
                }
            }
        }

        smpsDriver = new SmpsDriver(getSmpsOutputRate());

        // Configure Region
        String regionStr = configService.getString(SonicConfiguration.REGION);
        if ("PAL".equalsIgnoreCase(regionStr)) {
            smpsDriver.setRegion(SmpsSequencer.Region.PAL);
        } else {
            smpsDriver.setRegion(SmpsSequencer.Region.NTSC);
        }

        boolean dacInterpolate = configService.getBoolean(SonicConfiguration.DAC_INTERPOLATE);
        smpsDriver.setDacInterpolate(dacInterpolate);
        smpsDriver.setOutputSampleRate(getSmpsOutputRate());
        applyPsgNoiseConfig(smpsDriver);

        boolean fm6DacOff = configService.getBoolean(SonicConfiguration.FM6_DAC_OFF);

        AudioSourceDescriptor musicDescriptor = consumePendingMusicDescriptor(musicId);
        SmpsSequencer seq = new SmpsSequencer(data, dacData, smpsDriver, requireSmpsConfig());
        seq.setSourceDescriptor(describeSmpsSource(musicDescriptor, data, false));
        seq.setSampleRate(smpsDriver.getOutputSampleRate());
        seq.setSpeedShoes(speedShoesEnabled);
        seq.setSpeedMultiplier(speedMultiplier);
        seq.setFm6DacOff(fm6DacOff);
        // Music is the primary voice source for SFX fallback
        seq.setFallbackVoiceData(data);
        smpsDriver.addSequencer(seq, false);
        currentSmps = seq;
        currentMusicId = musicId;
        currentMusicDescriptor = musicDescriptor;

        updateSynthesizerConfig();
        synchronized (streamLock) {
            if (runtimeProvidesPresentationPcm()) {
                deterministicAudioRuntime.setMusicStream(smpsDriver);
            }
            currentStream = smpsDriver;
        }
        startStream();
    }

    @Override
    public void playSmps(AbstractSmpsData data, DacData dacData,
                         SmpsSequencerConfig config, boolean forceOverride) {
        SmpsSequencerConfig effectiveConfig = (config != null) ? config : requireSmpsConfig();

        int musicId = data.getId();
        boolean isOverride = forceOverride
                || (audioProfile != null && audioProfile.isMusicOverride(musicId));
        if (isOverride) {
            boolean sfxBlocking = audioProfile != null && audioProfile.isSfxBlockingMusic(musicId);
            // ROM: only the 1-up jingle (isSfxBlockingMusic) kills active SFX.
            // Non-blocking overrides (invincibility, Super Sonic) let SFX continue.
            if (sfxBlocking) {
                synchronized (streamLock) {
                    if (smpsDriver != null) {
                        smpsDriver.stopAllSfx();
                    }
                    if (sfxStream instanceof SmpsDriver sfxDriver) {
                        sfxDriver.stopAll();
                    }
                    sfxStream = null;
                    if (runtimeProvidesPresentationPcm()) {
                        deterministicAudioRuntime.clearSfxStream();
                    }
                }
                sfxBlocked = true;
            }
            // Push current state unless re-triggering the same override.
            boolean currentIsOverride = audioProfile != null && audioProfile.isMusicOverride(currentMusicId);
            if (!currentIsOverride || currentMusicId != musicId) {
                pushCurrentState();
            }
            hookStopAndClearMusicSource();
            currentStream = null;
            currentSmps = null;
            smpsDriver = null;
        } else {
            stopStream();
            hookStopAndClearMusicSource();
            clearMusicStack();
            synchronized (streamLock) {
                if (sfxStream instanceof SmpsDriver sfxDriver) {
                    sfxDriver.stopAll();
                }
                sfxStream = null;
                if (runtimeProvidesPresentationPcm()) {
                    deterministicAudioRuntime.clearSfxStream();
                }
            }
        }

        smpsDriver = new SmpsDriver(getSmpsOutputRate());

        String regionStr = configService.getString(SonicConfiguration.REGION);
        if ("PAL".equalsIgnoreCase(regionStr)) {
            smpsDriver.setRegion(SmpsSequencer.Region.PAL);
        } else {
            smpsDriver.setRegion(SmpsSequencer.Region.NTSC);
        }

        boolean dacInterpolate = configService.getBoolean(SonicConfiguration.DAC_INTERPOLATE);
        smpsDriver.setDacInterpolate(dacInterpolate);
        smpsDriver.setOutputSampleRate(getSmpsOutputRate());
        applyPsgNoiseConfig(smpsDriver);

        boolean fm6DacOff = configService.getBoolean(SonicConfiguration.FM6_DAC_OFF);

        AudioSourceDescriptor musicDescriptor = consumePendingMusicDescriptor(musicId);
        SmpsSequencer seq = new SmpsSequencer(data, dacData, smpsDriver, effectiveConfig);
        seq.setSourceDescriptor(describeSmpsSource(musicDescriptor, data, false));
        seq.setSampleRate(smpsDriver.getOutputSampleRate());
        seq.setSpeedShoes(speedShoesEnabled);
        seq.setSpeedMultiplier(speedMultiplier);
        seq.setFm6DacOff(fm6DacOff);
        seq.setFallbackVoiceData(data);
        smpsDriver.addSequencer(seq, false);
        currentSmps = seq;
        currentMusicId = musicId;
        currentMusicDescriptor = musicDescriptor;

        updateSynthesizerConfig();
        synchronized (streamLock) {
            if (runtimeProvidesPresentationPcm()) {
                deterministicAudioRuntime.setMusicStream(smpsDriver);
            }
            currentStream = smpsDriver;
        }
        startStream();
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
        // ROM behavior: completely block SFX during override jingle and fade-in period
        if (sfxBlocked) {
            return;
        }

        SmpsSequencerConfig effectiveConfig = (config != null) ? config : requireSmpsConfig();

        boolean dacInterpolate = configService.getBoolean(SonicConfiguration.DAC_INTERPOLATE);
        boolean fm6DacOff = configService.getBoolean(SonicConfiguration.FM6_DAC_OFF);

        // Look up SFX priority from game-specific audio profile
        int sfxPriority = (audioProfile != null) ? audioProfile.getSfxPriority(data.getId()) : 0x70;
        boolean specialSfx = (audioProfile != null) && audioProfile.isSpecialSfx(data.getId());

        // --- Continuous SFX detection (Z80: zPlaySound_Bankswitch lines 1937-1965) ---
        // If this SFX is continuous (S3K >= 0xBC) and the same one is already playing,
        // extend playback (set the flag) instead of restarting from scratch.
        boolean isContinuous = (audioProfile != null) && audioProfile.isContinuousSfx(data.getId());
        int contTrackCount = data.getChannels() + data.getPsgChannels();
        if (isContinuous) {
            SmpsDriver targetDriver = null;
            if (smpsDriver != null && currentStream == smpsDriver) {
                targetDriver = smpsDriver;
            } else {
                synchronized (streamLock) {
                    if (sfxStream instanceof SmpsDriver) {
                        targetDriver = (SmpsDriver) sfxStream;
                    }
                }
            }
            if (targetDriver != null && targetDriver.extendContinuousSfx(data.getId(), contTrackCount)) {
                return; // Extended existing playback — no new sequencer needed
            }
        }

        if (smpsDriver != null && (currentStream == smpsDriver || runtimeProvidesPresentationPcm())) {
            // Mix into current driver
            if (isContinuous) {
                smpsDriver.startContinuousSfx(data.getId(), contTrackCount);
            }
            SmpsSequencer seq = new SmpsSequencer(data, dacData, smpsDriver, effectiveConfig);
            seq.setSourceDescriptor(describeSmpsSource(null, data, true));
            seq.setSampleRate(smpsDriver.getOutputSampleRate());
            seq.setFm6DacOff(fm6DacOff);
            seq.setSfxMode(true);
            seq.setPitch(pitch);
            seq.setSfxPriority(sfxPriority);
            seq.setSpecialSfx(specialSfx);
            if (currentSmps != null) {
                seq.setFallbackVoiceData(currentSmps.getSmpsData());
            }
            smpsDriver.addSequencer(seq, true);
        } else {
            // Standalone SFX driver
            synchronized (streamLock) {
                SmpsDriver sfxDriver;
                if (sfxStream instanceof SmpsDriver) {
                    sfxDriver = (SmpsDriver) sfxStream;
                } else {
                    sfxDriver = new SmpsDriver(getSmpsOutputRate());
                    sfxDriver.setDacInterpolate(dacInterpolate);
                    sfxStream = sfxDriver;
                }
                sfxDriver.setOutputSampleRate(getSmpsOutputRate());
                applyPsgNoiseConfig(sfxDriver);
                if (isContinuous) {
                    sfxDriver.startContinuousSfx(data.getId(), contTrackCount);
                }
                SmpsSequencer seq = new SmpsSequencer(data, dacData, sfxDriver, effectiveConfig);
                seq.setSourceDescriptor(describeSmpsSource(null, data, true));
                seq.setSampleRate(sfxDriver.getOutputSampleRate());
                seq.setFm6DacOff(fm6DacOff);
                seq.setSfxMode(true);
                seq.setPitch(pitch);
                seq.setSfxPriority(sfxPriority);
                seq.setSpecialSfx(specialSfx);
                if (currentSmps != null) {
                    seq.setFallbackVoiceData(currentSmps.getSmpsData());
                }
                sfxDriver.addSequencer(seq, true);
                if (runtimeProvidesPresentationPcm()) {
                    deterministicAudioRuntime.setSfxStream(sfxDriver);
                }
            }
        }

        // Ensure stream is running
        hookRestartStreamIfDry();
    }

    protected void startStream() {
        hookStartStream();
    }

    protected void stopStream() {
        hookStopStreamSource();

        currentStream = null;
        currentSmps = null;
        if (runtimeProvidesPresentationPcm()) {
            deterministicAudioRuntime.clearMusicStream();
            deterministicAudioRuntime.flushPresentationFifo();
        }
        if (smpsDriver != null) {
            smpsDriver.stopAll();
            smpsDriver = null;
        }
        currentMusicId = -1;
        currentMusicDescriptor = null;
    }

    @Override
    public void restoreMusic() {
        // Defer actual restoration to next updateStream cycle to avoid
        // modifying buffers while they're being rendered
        if (!musicStack.isEmpty()) {
            pendingRestore = true;
        }
    }

    protected void doRestoreMusic() {
        MusicState savedState = musicStack.pollFirst();
        if (savedState == null || savedState.stream == null || savedState.smps == null
                || savedState.driver == null) {
            return;
        }

        // Stop the current (invincibility/extra-life) music stream and
        // unqueue ALL buffers (both processed and queued) to avoid OpenAL errors
        hookStopAndUnqueueAllMusicBuffers();

        // Stop the current (non-saved) smps driver
        if (smpsDriver != null && smpsDriver != savedState.driver) {
            smpsDriver.stopAll();
        }

        // Restore saved state
        synchronized (streamLock) {
            currentStream = savedState.stream;
            currentSmps = savedState.smps;
            smpsDriver = savedState.driver;
            currentMusicId = savedState.musicId;
            currentMusicDescriptor = savedState.descriptor;
            bindRuntimePresentationStreams();
        }

        if (currentSmps != null) {
            // Restore speed shoes state to the saved sequencer
            currentSmps.setSpeedShoes(speedShoesEnabled);
            currentSmps.refreshAllVoices();
            // ROM: only the 1-up jingle fades in on restore. S1/S2 keep SFX
            // blocked through FadeInFlag; S3K clears zFadeToPrevFlag when
            // zFadeInToPrevious starts and allows new SFX on the next driver cycle.
            if (sfxBlocked) {
                if (audioProfile == null || audioProfile.blocksSfxDuringMusicRestoreFadeIn()) {
                    currentSmps.setOnFadeComplete(() -> sfxBlocked = false);
                } else {
                    sfxBlocked = false;
                }
                currentSmps.triggerFadeIn();
            }
        }

        startStream();
    }

    /**
     * Renders one stream buffer's worth of PCM (music + SFX mix + pcmHistory
     * write) and uploads it to the device via {@link #hookUploadStreamBuffer}.
     * The synthesis/mix/history portion is shared; only the upload is the hook.
     */
    protected void fillBuffer(int bufferId) {
        // Profiler sections inside fillBuffer. Names describe what is actually wrapped,
        // not a clean SMPS/synth/resample split — SmpsDriver.read() interleaves the
        // sequencer, YM2612/PSG synthesis, and the blip resampler at sample granularity,
        // so those three phases cannot be separated at this seam.
        //   - audio.music_stream: music AudioStream read() (SMPS + synth + resample,
        //     interleaved).
        //   - audio.sfx_stream:   separate-SFX stream read() plus the music/SFX mix loop.
        //   - audio.upload:       DirectBuffer fill and OpenAL upload.
        // Sections do not nest (see PerformanceProfiler.beginSection), so starting any of
        // these inside the outer "audio" section in GameLoop will truncate that section
        // — that's expected. Called potentially multiple times per audio tick (once per
        // processed buffer in updateStream); PerformanceProfiler accumulates section time.
        int sampleRate;
        synchronized (streamLock) {
            beginProfileSection("audio.music_stream");
            try {
                // Clear and reuse pre-allocated buffer
                Arrays.fill(streamData, (short) 0);
                boolean runtimePresentation = runtimeProvidesPresentationPcm();
                if (reverseCursor != null) {
                    reverseCursor.readPrevious(streamData, STREAM_BUFFER_SIZE);
                } else if (runtimePresentation) {
                    deterministicAudioRuntime.drainPcm(streamData, STREAM_BUFFER_SIZE);
                    clearCompletedRuntimeSfxIfNeeded();
                } else if (currentStream != null) {
                    currentStream.read(streamData);
                }
            } finally {
                endProfileSection("audio.music_stream");
            }

            beginProfileSection("audio.sfx_stream");
            try {
                boolean runtimePresentation = runtimeProvidesPresentationPcm();
                if (!runtimePresentation && sfxStream != null) {
                    Arrays.fill(sfxStreamData, (short) 0);
                    sfxStream.read(sfxStreamData);

                    for (int i = 0; i < streamData.length; i++) {
                        int mixed = streamData[i] + sfxStreamData[i];
                        if (mixed > Short.MAX_VALUE)
                            mixed = Short.MAX_VALUE;
                        if (mixed < Short.MIN_VALUE)
                            mixed = Short.MIN_VALUE;
                        streamData[i] = (short) mixed;
                    }

                    if (sfxStream.isComplete()) {
                        sfxStream = null;
                    }
                }
                if (reverseCursor == null && pcmHistory != null) {
                    pcmHistory.write(streamData, STREAM_BUFFER_SIZE);
                }

                sampleRate = (int) Math.round(getStreamSampleRate());
            } finally {
                endProfileSection("audio.sfx_stream");
            }
        }

        // Keep DirectBuffer/OpenAL operations outside lock to minimize contention
        beginProfileSection("audio.upload");
        try {
            hookUploadStreamBuffer(bufferId, streamData, sampleRate);
        } finally {
            endProfileSection("audio.upload");
        }
    }

    protected void beginProfileSection(String section) {
        if (profiler != null) {
            profiler.beginSection(section);
        }
    }

    protected void endProfileSection(String section) {
        if (profiler != null) {
            profiler.endSection(section);
        }
    }

    protected boolean runtimeProvidesPresentationPcm() {
        return deterministicAudioRuntime != null && deterministicAudioRuntime.providesPresentationPcm();
    }

    protected void bindRuntimePresentationStreams() {
        if (!runtimeProvidesPresentationPcm()) {
            return;
        }
        deterministicAudioRuntime.setMusicStream(smpsDriver);
        if (sfxStream != null) {
            deterministicAudioRuntime.setSfxStream(sfxStream);
        } else {
            deterministicAudioRuntime.clearSfxStream();
        }
    }

    private void clearRuntimeSfxStream() {
        if (runtimeProvidesPresentationPcm()) {
            deterministicAudioRuntime.clearSfxStream();
        }
    }

    private void clearCompletedRuntimeSfxIfNeeded() {
        if (sfxStream != null && sfxStream.isComplete()) {
            sfxStream = null;
            clearRuntimeSfxStream();
        }
    }

    protected double getSmpsOutputRate() {
        boolean internalRate = configService.getBoolean(SonicConfiguration.AUDIO_INTERNAL_RATE_OUTPUT);
        // Use device's native sample rate to avoid OpenAL resampling - our BlipResampler handles it
        return internalRate ? Ym2612Chip.getInternalRate() : getDeviceSampleRate();
    }

    protected void applyPsgNoiseConfig(SmpsDriver driver) {
        boolean everyToggle = configService.getBoolean(SonicConfiguration.PSG_NOISE_SHIFT_EVERY_TOGGLE);
        driver.setPsgNoiseShiftOnEveryToggle(everyToggle);
    }

    protected double getStreamSampleRate() {
        double rate = getDeviceSampleRate();  // Use device rate as fallback to match getSmpsOutputRate()
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
            for (MusicState state : musicStack) {
                overrides.add(state.descriptor);
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
            if (runtimeProvidesPresentationPcm() && !preservePresentationQueue) {
                deterministicAudioRuntime.flushPresentationFifo();
            }
        }
        if (!preservePresentationQueue) {
            hookStopAndClearAllMusicBuffers();
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
            deterministicAudioRuntime = runtime;
            bindRuntimePresentationStreams();
        }
    }

    @Override
    public boolean supportsDeterministicRuntimePresentation() {
        return false;
    }

    @Override
    public int outputSampleRate() {
        return (int) Math.round(getSmpsOutputRate());
    }

    @Override
    public void beginReversePresentation() {
        synchronized (streamLock) {
            reverseCursor = pcmHistory != null ? pcmHistory.createReverseCursor() : null;
            if (reverseCursor != null) {
                reverseCursor.setRate(pendingReverseRate);
            }
        }
    }

    @Override
    public void endReversePresentation() {
        synchronized (streamLock) {
            if (pcmHistory != null) {
                pcmHistory.commitReverseCursor(reverseCursor);
            }
            reverseCursor = null;
            pendingReverseRate = 1.0;
        }
    }

    @Override
    public void setReversePlaybackRate(double rate) {
        double safeRate = (Double.isNaN(rate) || rate <= 0.0) ? 1.0 : rate;
        synchronized (streamLock) {
            pendingReverseRate = safeRate;
            if (reverseCursor != null) {
                reverseCursor.setRate(safeRate);
            }
        }
    }

    @Override
    public void playSfx(String sfxName) {
        playSfx(sfxName, 1.0f);
    }

    @Override
    public void playSfx(String sfxName, float pitch) {
        String filename = sfxFallback.get(sfxName);
        if (filename != null) {
            hookPlayWavSfx(sfxName, pitch);
        } else {
            LOGGER.fine("SFX not found in fallback map: " + sfxName);
        }
    }

    @Override
    public void stopPlayback() {
        stopStream();
        hookStopAndClearMusicSource();
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
            clearRuntimeSfxStream();
        }
        // Stop and cleanup WAV-based SFX sources
        hookStopAndDeleteWavSfxSources();
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
        musicStack.push(new MusicState(currentStream, currentSmps, smpsDriver, currentMusicId,
                currentMusicDescriptor));
    }

    private void clearMusicStack() {
        musicStack.clear();
        pendingRestore = false;
        sfxBlocked = false;  // Unblock SFX when stack is cleared (e.g., level transition)
    }

    private boolean removeSavedOverride(int musicId) {
        if (musicStack.isEmpty()) {
            return false;
        }
        for (Iterator<MusicState> iterator = musicStack.iterator(); iterator.hasNext();) {
            MusicState state = iterator.next();
            if (state.musicId == musicId) {
                iterator.remove();
                return true;
            }
        }
        return false;
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

    @Override
    public void update() {
        hookUpdateStream();
        hookCleanupStoppedWavSfx();
    }

    @Override
    public void destroy() {
        hookDestroyDevice();
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
            if (runtimeProvidesPresentationPcm()) {
                deterministicAudioRuntime.clearSfxStream();
            }
        }
    }

    @Override
    public void pause() {
        hookPause();
    }

    @Override
    public void resume() {
        hookResume();
    }
}
