package com.openggf.audio;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.presentation.SmpsCompositeVoice;
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
 * Device-agnostic base for the SMPS source-construction backend.
 *
 * <p>This class owns SMPS sequencer/driver construction, the music-stack and
 * SFX lifecycle, speed/tempo state, and logical music-source descriptors. It
 * owns <strong>no</strong> presentation state: the presentation clock, final
 * PCM, rewind history, reverse cursor, and capture leases all belong to
 * {@code AudioPresentationProducer}, and the only writer of a real audio
 * device is {@code OpenAlPcmSink}. It contains <strong>zero</strong> OpenAL
 * calls and no {@code org.lwjgl.openal} imports.
 *
 * <p>The remaining device lifecycle hooks are {@code protected abstract} and
 * implemented as no-ops by both concrete subclasses
 * ({@link LWJGLAudioBackend}, {@link HeadlessSmpsAudioBackend}); the headless
 * backend additionally fixes the device-facing fallback rate to 48000.
 */
public abstract class AbstractSmpsAudioBackend implements AudioBackend {
    private static final Logger LOGGER = Logger.getLogger(AbstractSmpsAudioBackend.class.getName());

    protected final Object streamLock = new Object();
    protected final SonicConfigurationService configService;
    /**
     * Currently unread: the {@code audio.music_stream} / {@code audio.sfx_stream}
     * / {@code audio.upload} profile sections this fed disappeared with the
     * backend's stream fill, and audio timing is now profiled on the
     * presentation producer instead. The field and its constructor argument are
     * deliberately retained so this deletion task does not churn every
     * backend construction site; nothing in the backend may start profiling
     * again without owning a section on the producer side.
     */
    protected final PerformanceProfiler profiler;

    protected static final int STREAM_BUFFER_SIZE = 1024;

    protected AudioStream currentStream;
    protected AudioStream sfxStream;
    private SmpsSequencer currentSmps;
    private SmpsDriver smpsDriver;

    private static class MusicState {
        final AudioStream stream;
        final SmpsSequencer smps;
        final SmpsDriver driver;
        final int musicId;
        final AudioSourceDescriptor descriptor;
        final StreamedMusicPort.State streamedState;

        MusicState(AudioStream stream, SmpsSequencer smps, SmpsDriver driver, int musicId,
                   AudioSourceDescriptor descriptor) {
            this.stream = stream;
            this.smps = smps;
            this.driver = driver;
            this.musicId = musicId;
            this.descriptor = descriptor;
            this.streamedState = null;
        }

        MusicState(StreamedMusicPort.State streamedState, int musicId,
                   AudioSourceDescriptor descriptor) {
            this.stream = null;
            this.smps = null;
            this.driver = null;
            this.musicId = musicId;
            this.descriptor = descriptor;
            this.streamedState = Objects.requireNonNull(streamedState, "streamedState");
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
    private final Object streamedPortTransitionLock = new Object();
    private sealed interface StreamedTransition { }
    private record InstallStreamedPort(StreamedMusicPort port) implements StreamedTransition { }
    private record PlayStreamedOrElse(int musicId, Runnable fallback) implements StreamedTransition { }
    private record PlayNamespacedTrack(StreamedMusicPort.TrackRef track) implements StreamedTransition { }
    private record SetStreamedPause(int reason, boolean paused) implements StreamedTransition { }
    private record FadeForeground(int steps, int delay) implements StreamedTransition { }
    private record SetStreamedSpeed(int multiplier) implements StreamedTransition { }
    private record StopForeground() implements StreamedTransition { }
    private record RestoreForeground() implements StreamedTransition { }
    private record EndForegroundOverride(int musicId) implements StreamedTransition { }
    private final Deque<StreamedTransition> pendingStreamedTransitions = new ArrayDeque<>();
    private StreamedMusicPort streamedMusicPort = StreamedMusicPort.EMPTY;
    private volatile StreamedMusicPort streamedMusicPreflightPort = StreamedMusicPort.EMPTY;
    private volatile boolean streamedOverrideReplayBypass;
    private boolean streamedRestoreFadeUnblocksSfx;
    private int streamedGlobalPauseMask;

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
     * sample rate). Must establish {@link #getDeviceSampleRate()}.
     */
    protected abstract void hookInitDevice();

    /** Tears the output device down (frees buffers/sources/context/device). */
    protected abstract void hookDestroyDevice();

    /**
     * Begins a source lifecycle transition into "playing". No presentation
     * output is attached to the backend; both subclasses are no-ops.
     */
    protected abstract void hookStartStream();

    /**
     * Ends a source lifecycle transition out of "playing" for the music slot.
     * Both subclasses are no-ops.
     */
    protected abstract void hookStopStreamSource();

    /**
     * Per-{@link #update()} device pump. No presentation output is attached to
     * the backend; both subclasses are no-ops.
     */
    protected abstract void hookUpdateStream();

    /**
     * Detaches the music slot without ending queued output. Used by the
     * override-swap paths in {@code playSmps}. Both subclasses are no-ops.
     */
    protected abstract void hookStopAndClearMusicSource();

    /**
     * Ends all queued music output. Used by {@code doRestoreMusic}. Both
     * subclasses are no-ops.
     */
    protected abstract void hookStopAndUnqueueAllMusicBuffers();

    /**
     * Ends all queued music output and detaches the music slot. Used by
     * {@code stopPlayback}. Both subclasses are no-ops.
     */
    protected abstract void hookStopAndClearAllMusicBuffers();

    /**
     * Restarts a dry music slot: the {@code playSfxSmps} "ensure stream is
     * running" tail. Both subclasses are no-ops.
     */
    protected abstract void hookRestartStreamIfDry();

    /** Stops and deletes any WAV-based SFX sources. Both subclasses: no-op. */
    protected abstract void hookStopAndDeleteWavSfxSources();

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
    public void registerAudioProfileCoordHandlers(GameAudioProfile profile) {
        // Presentation owns the sole session coordination state after cutover.
    }

    @Override
    public AudioPresentationTuning presentationTuning() {
        SmpsSequencer.Region region =
                "PAL".equalsIgnoreCase(
                        configService.getString(SonicConfiguration.REGION))
                        ? SmpsSequencer.Region.PAL
                        : SmpsSequencer.Region.NTSC;
        return new AudioPresentationTuning(
                region,
                configService.getBoolean(SonicConfiguration.DAC_INTERPOLATE),
                configService.getBoolean(
                        SonicConfiguration.PSG_NOISE_SHIFT_EVERY_TOGGLE),
                configService.getBoolean(SonicConfiguration.FM6_DAC_OFF));
    }

    @Override
    public void init() {
        hookInitDevice();
    }

    @Override
    public void installStreamedMusicPort(StreamedMusicPort port) {
        Objects.requireNonNull(port, "port");
        int expectedRate = (int) Math.round(getSmpsOutputRate());
        int actualRate = port != StreamedMusicPort.EMPTY ? port.outputRate() : 0;
        if (port != StreamedMusicPort.EMPTY && actualRate != expectedRate) {
            port.close();
            throw new IllegalArgumentException("Streamed output rate " + actualRate
                    + " differs from presentation rate " + expectedRate);
        }
        synchronized (streamedPortTransitionLock) {
            streamedMusicPreflightPort = port;
            pendingStreamedTransitions.addLast(new InstallStreamedPort(port));
        }
    }

    @Override
    public void playStreamedMusicOrElse(int musicId, Runnable stockFallback) {
        if (musicId < 0) throw new IllegalArgumentException("Music id must be nonnegative");
        Objects.requireNonNull(stockFallback, "stockFallback");
        synchronized (streamedPortTransitionLock) {
            pendingStreamedTransitions.addLast(new PlayStreamedOrElse(musicId, stockFallback));
        }
    }

    @Override
    public boolean tryPlayStreamedMusic(StreamedMusicPort.TrackRef track) {
        Objects.requireNonNull(track, "track");
        synchronized (streamedPortTransitionLock) {
            if (!streamedMusicPreflightPort.hasTrack(track)) {
                return false;
            }
            pendingStreamedTransitions.addLast(new PlayNamespacedTrack(track));
            return true;
        }
    }

    @Override
    public boolean hasStreamedMusic(StreamedMusicPort.TrackRef track) {
        Objects.requireNonNull(track, "track");
        synchronized (streamedPortTransitionLock) {
            return streamedMusicPreflightPort.hasTrack(track);
        }
    }

    @Override
    public boolean tryPlayStreamedSfx(StreamedMusicPort.SfxRef sfx) {
        Objects.requireNonNull(sfx, "sfx");
        // Preflight only. The one-shot itself is materialized by the presentation
        // command path as an ordinary sample voice, so the backend no longer keeps
        // a private one-shot list to mix.
        synchronized (streamedPortTransitionLock) {
            return !sfxBlocked && streamedMusicPreflightPort.hasSfx(sfx);
        }
    }

    @Override
    public void beginStreamedOverrideReplayBypass() {
        streamedOverrideReplayBypass = true;
    }

    @Override
    public void endStreamedOverrideReplayBypass() {
        streamedOverrideReplayBypass = false;
    }

    private void applyPendingStreamedPortTransitions() {
        while (true) {
            StreamedTransition transition;
            synchronized (streamedPortTransitionLock) {
                transition = pendingStreamedTransitions.pollFirst();
            }
            if (transition == null) {
                return;
            }
            if (transition instanceof InstallStreamedPort install) {
                StreamedMusicPort replacement = install.port();
                StreamedMusicPort previous = streamedMusicPort;
                streamedMusicPort = replacement;
                        applyGlobalPausesToPort();
                if (previous != StreamedMusicPort.EMPTY && previous != replacement) {
                    previous.stop();
                    hookStopAndClearAllMusicBuffers();
                    previous.close();
                }
            } else if (transition instanceof PlayStreamedOrElse play) {
                if (!streamedOverrideReplayBypass && streamedMusicPort.hasStockOverride(play.musicId())) {
                    if (currentStream != null || !streamedMusicPort.isCurrentStockOverride(play.musicId())) {
                        prepareForStreamedForeground(play.musicId());
                    }
                    streamedMusicPort.playStockOverride(play.musicId());
                    streamedMusicPort.resume(StreamedMusicPort.PAUSE_JINGLE);
                    applyGlobalPausesToPort();
                    currentMusicId = play.musicId();
                    currentMusicDescriptor = consumePendingMusicDescriptor(play.musicId());
                } else {
                    play.fallback().run();
                }
            } else if (transition instanceof PlayNamespacedTrack play) {
                if (!streamedMusicPort.hasTrack(play.track())) {
                    throw new IllegalArgumentException("Unknown namespaced streamed track: " + play.track());
                }
                stopStream();
                hookStopAndClearAllMusicBuffers();
                streamedMusicPort.stop();
                clearMusicStack();
                stopAllSfx();
                streamedMusicPort.playTrack(play.track());
                streamedMusicPort.resume(StreamedMusicPort.PAUSE_JINGLE);
                applyGlobalPausesToPort();
                currentMusicId = -1;
                currentMusicDescriptor = null;
                pendingMusicDescriptor = null;
            } else if (transition instanceof SetStreamedPause pause) {
                if (pause.reason() == StreamedMusicPort.PAUSE_APP
                        || pause.reason() == StreamedMusicPort.PAUSE_REWIND) {
                    if (pause.paused()) streamedGlobalPauseMask |= pause.reason();
                    else streamedGlobalPauseMask &= ~pause.reason();
                }
                if (pause.paused()) streamedMusicPort.pause(pause.reason());
                else streamedMusicPort.resume(pause.reason());
            } else if (transition instanceof FadeForeground fade) {
                if (currentSmps != null) currentSmps.triggerFadeOut(fade.steps(), fade.delay());
                else if (streamedMusicPort.hasSource()) streamedMusicPort.fadeOut(fade.steps(), fade.delay());
            } else if (transition instanceof SetStreamedSpeed speed) {
                streamedMusicPort.setSpeedMultiplier(speed.multiplier());
            } else if (transition instanceof StopForeground) {
                stopForegroundNow();
            } else if (transition instanceof RestoreForeground) {
                if (!musicStack.isEmpty()) doRestoreMusic();
            } else if (transition instanceof EndForegroundOverride end) {
                if ((currentSmps != null || streamedMusicPort.hasSource())
                        && currentMusicId == end.musicId()) {
                    if (!musicStack.isEmpty()) doRestoreMusic();
                } else {
                    removeSavedOverride(end.musicId());
                }
            }
        }
    }

    @Override
    public void resetStreamedMusicPort() {
        Set<StreamedMusicPort> toClose = Collections.newSetFromMap(new IdentityHashMap<>());
        synchronized (streamedPortTransitionLock) {
            streamedMusicPreflightPort = StreamedMusicPort.EMPTY;
            StreamedTransition pending;
            while ((pending = pendingStreamedTransitions.pollFirst()) != null) {
                if (pending instanceof InstallStreamedPort install
                        && install.port() != StreamedMusicPort.EMPTY) {
                    toClose.add(install.port());
                }
            }
        }
        StreamedMusicPort active = streamedMusicPort;
        streamedMusicPort = StreamedMusicPort.EMPTY;
        streamedGlobalPauseMask = 0;
        if (active != StreamedMusicPort.EMPTY) {
            active.stop();
            toClose.add(active);
        }
        hookStopAndClearAllMusicBuffers();
        toClose.forEach(StreamedMusicPort::close);
    }

    private void prepareForStreamedForeground(int musicId) {
        boolean override = audioProfile != null && audioProfile.isMusicOverride(musicId);
        if (override) {
            if (audioProfile.isSfxBlockingMusic(musicId)) {
                stopAllSfx();
                sfxBlocked = true;
            }
            boolean currentIsOverride = audioProfile.isMusicOverride(currentMusicId);
            if (!currentIsOverride || currentMusicId != musicId) {
                pushCurrentState();
            }
            hookStopAndClearAllMusicBuffers();
            currentStream = null;
            currentSmps = null;
            smpsDriver = null;
        } else {
            stopStream();
            hookStopAndClearAllMusicBuffers();
            streamedMusicPort.stop();
            clearMusicStack();
            stopAllSfx();
        }
    }

    private void stopForegroundNow() {
        stopStream();
        hookStopAndClearAllMusicBuffers();
        streamedMusicPort.stop();
        currentMusicId = -1;
        currentMusicDescriptor = null;
        pendingMusicDescriptor = null;
        clearMusicStack();
    }

    @Override
    public void playMusic(int musicId) {
        if (streamedOverrideReplayBypass && streamedMusicPort.hasSource()
                && (audioProfile == null || !audioProfile.isMusicOverride(musicId))) {
            pendingMusicDescriptor = null;
            return;
        }
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
        if (streamedOverrideReplayBypass && streamedMusicPort.hasSource()
                && (audioProfile == null || !audioProfile.isMusicOverride(musicId))) {
            pendingMusicDescriptor = null;
            return;
        }
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

            if (streamedMusicPort.hasSource()) {
                streamedMusicPort.pause(StreamedMusicPort.PAUSE_JINGLE);
            }

            // Just disconnect the current driver from the source without stopping/clearing it.
            hookStopAndClearAllMusicBuffers();
            currentStream = null;
            currentSmps = null;
            smpsDriver = null;
        } else {
            streamedMusicPort.stop();
            stopStream();
            // Stop music source if playing wav
            hookStopAndClearAllMusicBuffers();
            clearMusicStack();
            // Clean up standalone SFX stream - stopStream() only handles currentStream/smpsDriver,
            // but SFX played before any music was active use a separate sfxStream SmpsDriver.
            // Without this, the sfxStream persists indefinitely.
            synchronized (streamLock) {
                if (sfxStream instanceof SmpsDriver sfxDriver) {
                    sfxDriver.stopAll();
                }
                sfxStream = null;
            }
        }

        AudioSourceDescriptor musicDescriptor = consumePendingMusicDescriptor(musicId);
        SmpsCompositeVoice legacyMusic = createLegacyMusic(
                data, dacData, requireSmpsConfig(), musicDescriptor);
        smpsDriver = legacyMusic.driver();
        SmpsSequencer seq = smpsDriver.firstMusicSequencer();
        currentSmps = seq;
        currentMusicId = musicId;
        currentMusicDescriptor = musicDescriptor;

        updateSynthesizerConfig();
        synchronized (streamLock) {
            currentStream = smpsDriver;
        }
        startStream();
    }

    @Override
    public void playSmps(AbstractSmpsData data, DacData dacData,
                         SmpsSequencerConfig config, boolean forceOverride) {
        SmpsSequencerConfig effectiveConfig = legacySequencerConfig(
                (config != null) ? config : requireSmpsConfig());

        int musicId = data.getId();
        if (streamedOverrideReplayBypass && streamedMusicPort.hasSource()
                && !forceOverride && (audioProfile == null || !audioProfile.isMusicOverride(musicId))) {
            pendingMusicDescriptor = null;
            return;
        }
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
                }
                sfxBlocked = true;
            }
            // Push current state unless re-triggering the same override.
            boolean currentIsOverride = audioProfile != null && audioProfile.isMusicOverride(currentMusicId);
            if (!currentIsOverride || currentMusicId != musicId) {
                pushCurrentState();
            }
            if (streamedMusicPort.hasSource()) {
                streamedMusicPort.pause(StreamedMusicPort.PAUSE_JINGLE);
            }
            hookStopAndClearAllMusicBuffers();
            currentStream = null;
            currentSmps = null;
            smpsDriver = null;
        } else {
            streamedMusicPort.stop();
            stopStream();
            hookStopAndClearAllMusicBuffers();
            clearMusicStack();
            synchronized (streamLock) {
                if (sfxStream instanceof SmpsDriver sfxDriver) {
                    sfxDriver.stopAll();
                }
                sfxStream = null;
            }
        }

        AudioSourceDescriptor musicDescriptor = consumePendingMusicDescriptor(musicId);
        SmpsCompositeVoice legacyMusic = createLegacyMusic(
                data, dacData, effectiveConfig, musicDescriptor);
        smpsDriver = legacyMusic.driver();
        SmpsSequencer seq = smpsDriver.firstMusicSequencer();
        currentSmps = seq;
        currentMusicId = musicId;
        currentMusicDescriptor = musicDescriptor;

        updateSynthesizerConfig();
        synchronized (streamLock) {
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

        SmpsSequencerConfig effectiveConfig = legacySequencerConfig(
                (config != null) ? config : requireSmpsConfig());

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

        if (smpsDriver != null && currentStream == smpsDriver) {
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
                    applyUserMasks(sfxDriver, hasAnyUserSolo());
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
        if (smpsDriver != null) {
            smpsDriver.stopAll();
            smpsDriver = null;
        }
        currentMusicId = -1;
        currentMusicDescriptor = null;
    }

    @Override
    public void restoreMusic() {
        enqueueStreamedTransition(new RestoreForeground());
    }

    protected void doRestoreMusic() {
        MusicState savedState = musicStack.pollFirst();
        if (savedState == null) {
            return;
        }

        // Release any queued output for the current (invincibility/extra-life)
        // music slot before the saved state is reinstated.
        hookStopAndUnqueueAllMusicBuffers();

        // Stop the current (non-saved) foreground.
        if (smpsDriver != null && smpsDriver != savedState.driver) {
            smpsDriver.stopAll();
        }

        if (savedState.streamedState != null) {
            currentStream = null;
            currentSmps = null;
            smpsDriver = null;
            currentMusicId = savedState.musicId;
            currentMusicDescriptor = savedState.descriptor;
            if (!streamedMusicPort.restoreState(savedState.streamedState)) {
                streamedMusicPort.stop();
                currentMusicId = -1;
                currentMusicDescriptor = null;
                sfxBlocked = false;
                streamedRestoreFadeUnblocksSfx = false;
                pendingRestore = false;
                return;
            }
            streamedMusicPort.resume(StreamedMusicPort.PAUSE_JINGLE);
            applyGlobalPausesToPort();
            if (sfxBlocked) {
                streamedMusicPort.fadeIn(requireSmpsConfig().getFadeInSteps(),
                        requireSmpsConfig().getFadeInDelay());
                if (audioProfile != null && !audioProfile.blocksSfxDuringMusicRestoreFadeIn()) {
                    sfxBlocked = false;
                } else {
                    streamedRestoreFadeUnblocksSfx = true;
                }
            }
            startStream();
            return;
        }
        if (savedState.stream == null || savedState.smps == null || savedState.driver == null) {
            return;
        }

        streamedMusicPort.stop();

        // Restore saved state
        synchronized (streamLock) {
            currentStream = savedState.stream;
            currentSmps = savedState.smps;
            smpsDriver = savedState.driver;
            currentMusicId = savedState.musicId;
            currentMusicDescriptor = savedState.descriptor;
            updateSynthesizerConfig();
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

    protected double getSmpsOutputRate() {
        boolean internalRate = configService.getBoolean(SonicConfiguration.AUDIO_INTERNAL_RATE_OUTPUT);
        // Use device's native sample rate to avoid OpenAL resampling - our BlipResampler handles it
        return internalRate ? Ym2612Chip.getInternalRate() : getDeviceSampleRate();
    }

    protected void applyPsgNoiseConfig(SmpsDriver driver) {
        boolean everyToggle = configService.getBoolean(SonicConfiguration.PSG_NOISE_SHIFT_EVERY_TOGGLE);
        driver.setPsgNoiseShiftOnEveryToggle(everyToggle);
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

    StateForTesting stateForTesting() {
        synchronized (streamLock) {
            List<OverrideStateForTesting> overrides =
                    new ArrayList<>(musicStack.size());
            for (MusicState state : musicStack) {
                overrides.add(new OverrideStateForTesting(
                        state.stream, state.smps, state.driver,
                        state.musicId, state.descriptor,
                        state.driver != null
                                ? state.driver.captureSnapshot() : null,
                        state.driver != null
                                ? state.driver
                                        .sequencersForTesting()
                                : List.of()));
            }
            return new StateForTesting(
                    currentStream, sfxStream, currentSmps, smpsDriver,
                    currentMusicDescriptor, currentMusicId,
                    pendingMusicDescriptor, sfxBlocked, pendingRestore,
                    speedShoesEnabled, speedMultiplier, overrides,
                    smpsDriver != null ? smpsDriver.captureSnapshot() : null,
                    smpsDriver != null
                            ? smpsDriver.sequencersForTesting()
                            : List.of(),
                    sfxStream instanceof SmpsDriver driver
                            ? driver.captureSnapshot() : null,
                    sfxStream instanceof SmpsDriver driver
                            ? driver.sequencersForTesting()
                            : List.of(),
                    maskOf(fmUserMutes), maskOf(fmUserSolos),
                    maskOf(psgUserMutes), maskOf(psgUserSolos));
        }
    }

    record OverrideStateForTesting(
            AudioStream stream,
            SmpsSequencer sequencer,
            SmpsDriver driver,
            int musicId,
            AudioSourceDescriptor descriptor,
            SmpsDriverSnapshot driverSnapshot,
            List<SmpsSequencer> sequencers) {
        OverrideStateForTesting {
            sequencers = List.copyOf(sequencers);
        }
    }

    record StateForTesting(
            AudioStream currentStream,
            AudioStream sfxStream,
            SmpsSequencer currentSmps,
            SmpsDriver musicDriver,
            AudioSourceDescriptor currentMusic,
            int currentMusicId,
            AudioSourceDescriptor pendingMusic,
            boolean sfxBlocked,
            boolean pendingRestore,
            boolean speedShoesEnabled,
            int speedMultiplier,
            List<OverrideStateForTesting> overrideStack,
            SmpsDriverSnapshot musicDriverSnapshot,
            List<SmpsSequencer> musicSequencers,
            SmpsDriverSnapshot standaloneSfxDriverSnapshot,
            List<SmpsSequencer> standaloneSfxSequencers,
            int fmUserMuteMask,
            int fmUserSoloMask,
            int psgUserMuteMask,
            int psgUserSoloMask) {
        StateForTesting {
            overrideStack = List.copyOf(overrideStack);
            musicSequencers = List.copyOf(musicSequencers);
            standaloneSfxSequencers =
                    List.copyOf(standaloneSfxSequencers);
        }
    }

    private SmpsDriver newConfiguredSmpsDriver() {
        SmpsDriver driver = new SmpsDriver(getSmpsOutputRate());
        driver.setDacInterpolate(configService.getBoolean(SonicConfiguration.DAC_INTERPOLATE));
        driver.setOutputSampleRate(getSmpsOutputRate());
        applyPsgNoiseConfig(driver);
        return driver;
    }

    private SmpsCompositeVoice createLegacyMusic(
            AbstractSmpsData data,
            DacData dacData,
            SmpsSequencerConfig sequencerConfig,
            AudioSourceDescriptor descriptor) {
        SmpsDriver driver = newConfiguredSmpsDriver();
        driver.setRegion("PAL".equalsIgnoreCase(
                configService.getString(SonicConfiguration.REGION))
                ? SmpsSequencer.Region.PAL
                : SmpsSequencer.Region.NTSC);
        SmpsSequencer sequencer = new SmpsSequencer(
                data, dacData, driver, sequencerConfig);
        sequencer.setSourceDescriptor(
                describeSmpsSource(descriptor, data, false));
        sequencer.setSampleRate(driver.getOutputSampleRate());
        sequencer.setSpeedShoes(speedShoesEnabled);
        sequencer.setSpeedMultiplier(speedMultiplier);
        sequencer.setFm6DacOff(configService.getBoolean(
                SonicConfiguration.FM6_DAC_OFF));
        sequencer.setFallbackVoiceData(data);
        driver.addSequencer(sequencer, false);
        return new SmpsCompositeVoice(
                0, 0, data.getId(), descriptor,
                STREAM_BUFFER_SIZE, driver);
    }

    private SmpsSequencerConfig legacySequencerConfig(
            SmpsSequencerConfig config) {
        return AudioManager.presentationOwner()
                .bindLegacyConfigToPresentationOwner(config);
    }

    @Override
    public void prepareLogicalMusicSource(AudioSourceDescriptor descriptor) {
        pendingMusicDescriptor = descriptor;
    }

    @Override
    public int outputSampleRate() {
        return (int) Math.round(getSmpsOutputRate());
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
        enqueueStreamedTransition(new StopForeground());
        stopStream();
        hookStopAndClearAllMusicBuffers();
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
        }
        // Stop and cleanup WAV-based SFX sources
        hookStopAndDeleteWavSfxSources();
    }

    @Override
    public void fadeOutMusic(int steps, int delay) {
        enqueueStreamedTransition(new FadeForeground(steps, delay));
    }

    @Override
    public void endMusicOverride(int musicId) {
        enqueueStreamedTransition(new EndForegroundOverride(musicId));
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
        enqueueStreamedTransition(new SetStreamedSpeed(enabled ? 2 : 1));
    }

    @Override
    public void setSpeedMultiplier(int multiplier) {
        this.speedMultiplier = multiplier;
        synchronized (streamLock) {
            if (currentSmps != null) {
                currentSmps.setSpeedMultiplier(multiplier);
            }
        }
        enqueueStreamedTransition(new SetStreamedSpeed(multiplier));
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
        boolean anySolo = hasAnyUserSolo();

        if (smpsDriver != null) {
            applyUserMasks(smpsDriver, anySolo);
        }
        if (sfxStream instanceof SmpsDriver sfxDriver
                && sfxDriver != smpsDriver) {
            applyUserMasks(sfxDriver, anySolo);
        }
    }

    private boolean hasAnyUserSolo() {
        for (boolean solo : fmUserSolos) {
            if (solo) {
                return true;
            }
        }
        for (boolean solo : psgUserSolos) {
            if (solo) {
                return true;
            }
        }
        return false;
    }

    private void applyUserMasks(SmpsDriver driver, boolean anySolo) {
        for (int channel = 0; channel < fmUserMutes.length; channel++) {
            boolean muted = fmUserMutes[channel];
            if (fmUserSolos[channel]) {
                muted = false;
            } else if (anySolo) {
                muted = true;
            }
            driver.setFmMute(channel, muted);
        }
        for (int channel = 0; channel < psgUserMutes.length; channel++) {
            boolean muted = psgUserMutes[channel];
            if (psgUserSolos[channel]) {
                muted = false;
            } else if (anySolo) {
                muted = true;
            }
            driver.setPsgMute(channel, muted);
        }
    }

    private static int maskOf(boolean[] values) {
        int mask = 0;
        for (int index = 0; index < values.length; index++) {
            if (values[index]) {
                mask |= 1 << index;
            }
        }
        return mask;
    }

    private void restoreUserMasks(
            int fmMuteMask,
            int fmSoloMask,
            int psgMuteMask,
            int psgSoloMask) {
        restoreMask(fmUserMutes, fmMuteMask);
        restoreMask(fmUserSolos, fmSoloMask);
        restoreMask(psgUserMutes, psgMuteMask);
        restoreMask(psgUserSolos, psgSoloMask);
    }

    private static void restoreMask(boolean[] target, int mask) {
        for (int index = 0; index < target.length; index++) {
            target[index] = (mask & (1 << index)) != 0;
        }
    }

    private SmpsSequencerConfig requireSmpsConfig() {
        if (smpsConfig == null) {
            throw new IllegalStateException("SMPS sequencer config not set");
        }
        return smpsConfig;
    }

    private void pushCurrentState() {
        if (currentStream != null && currentSmps != null && smpsDriver != null) {
            musicStack.push(new MusicState(currentStream, currentSmps, smpsDriver, currentMusicId,
                    currentMusicDescriptor));
        } else {
            streamedMusicPort.captureState().ifPresent(state -> musicStack.push(
                    new MusicState(state, currentMusicId, currentMusicDescriptor)));
        }
    }

    private void clearMusicStack() {
        musicStack.clear();
        pendingRestore = false;
        sfxBlocked = false;  // Unblock SFX when stack is cleared (e.g., level transition)
        streamedRestoreFadeUnblocksSfx = false;
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
            // Streamed creator audio is decoded PCM presented as a sample
            // voice; it has no SMPS source and never reaches this mapping.
            case STREAMED_TRACK, STREAMED_SFX -> throw new IllegalArgumentException(
                    "streamed route has no SMPS source: " + descriptor.route());
        };
    }

    @Override
    public void update() {
        applyPendingStreamedPortTransitions();
        if (pendingRestore) {
            pendingRestore = false;
            doRestoreMusic();
        }
        hookUpdateStream();
        streamedMusicPort.advanceFade();
        if (sfxBlocked && streamedRestoreFadeUnblocksSfx) {
            if (!streamedMusicPort.fadeActive() && streamedMusicPort.fadeAtFullGain()) {
                sfxBlocked = false;
                streamedRestoreFadeUnblocksSfx = false;
            }
        }
        hookCleanupStoppedWavSfx();
    }

    @Override
    public void destroy() {
        resetStreamedMusicPort();
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
        }
    }

    @Override
    public void pause() {
        enqueueStreamedPause(StreamedMusicPort.PAUSE_APP, true);
        hookPause();
    }

    @Override
    public void resume() {
        enqueueStreamedPause(StreamedMusicPort.PAUSE_APP, false);
        hookResume();
    }


    private void enqueueStreamedPause(int reason, boolean paused) {
        enqueueStreamedTransition(new SetStreamedPause(reason, paused));
    }

    private void enqueueStreamedTransition(StreamedTransition transition) {
        synchronized (streamedPortTransitionLock) {
            pendingStreamedTransitions.addLast(transition);
        }
    }

    private void applyGlobalPausesToPort() {
        if ((streamedGlobalPauseMask & StreamedMusicPort.PAUSE_APP) != 0) {
            streamedMusicPort.pause(StreamedMusicPort.PAUSE_APP);
        }
        if ((streamedGlobalPauseMask & StreamedMusicPort.PAUSE_REWIND) != 0) {
            streamedMusicPort.pause(StreamedMusicPort.PAUSE_REWIND);
        }
    }
}
