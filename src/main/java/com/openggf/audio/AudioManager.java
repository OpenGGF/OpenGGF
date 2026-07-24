package com.openggf.audio;

import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioCommandTimeline;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.rewind.AudioBackendLogicalSnapshot;
import com.openggf.audio.rewind.AudioPresentationPolicy;
import com.openggf.audio.rewind.AudioReplayReason;
import com.openggf.audio.rewind.AudioReplayScope;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.AudioTimelineEntry;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.FrameAudioMode;
import com.openggf.audio.runtime.NoOpDeterministicAudioRuntime;
import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.runtime.AudioOutputFifo;
import com.openggf.audio.runtime.PcmHistoryRing;
import com.openggf.audio.runtime.PresentationAudioCapture;
import com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.presentation.AudioPresentationCommand;
import com.openggf.audio.presentation.AudioPresentationCommandQueue;
import com.openggf.audio.presentation.AudioPresentationCommandResolver;
import com.openggf.audio.presentation.AudioPresentationMixer;
import com.openggf.audio.presentation.AudioPresentationParityProbe;
import com.openggf.audio.presentation.AudioPresentationProducer;
import com.openggf.audio.presentation.AudioPresentationSnapshot;
import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.openggf.audio.presentation.AudioVoiceRegistry;
import com.openggf.audio.presentation.DecodedPcmCache;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.presentation.PresentationVoiceSnapshot;
import com.openggf.audio.presentation.SmpsCompositeVoice;
import com.openggf.audio.output.NoDeviceAudioSink;
import com.openggf.audio.output.AudioPresentationSink;
import com.openggf.audio.output.OpenAlPcmSink;
import com.openggf.configuration.FrameRateResolver;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AudioManager implements MusicRestoreSink {
    private static final Logger LOGGER = Logger.getLogger(AudioManager.class.getName());
    private static final int PCM_HISTORY_SECONDS = 60;
    private static final int OUTPUT_FIFO_SECONDS = 2;
    private static final int REVERSE_RELEASE_CROSSFADE_MS = 45;
    private static AudioManager instance;
    private AudioBackend backend;
    private SmpsLoader smpsLoader;
    private DacData dacData;
    private Rom rom;
    private Map<GameSound, Integer> soundMap;
    private GameAudioProfile audioProfile;
    private boolean ringLeft = true;
    private int rewindReplaySuppressionDepth;
    private final AudioCommandTimeline commandTimeline = new AudioCommandTimeline();
    private DeterministicAudioRuntime deterministicAudioRuntime = NoOpDeterministicAudioRuntime.INSTANCE;
    /**
     * Single offline-capture compatibility lease over the authoritative
     * producer. It is an ordinary non-consuming capture handle: it never
     * replaces the producer, registry, sink, or deterministic runtime.
     */
    private LiveCaptureAudioHandle offlineCaptureHandle;
    private ManagerLiveCaptureAudioHandle activeLiveCaptureAudioHandle;
    private boolean deterministicRuntimeExplicitlyConfigured;
    private boolean audioFrameOwnedExternally;
    private boolean audioFrameAdvanced;
    private boolean reverseAudioPresentationActive;
    /** Single selected dual-path restore, committed only at reverse release. */
    private AudioLogicalSnapshot deferredReverseLogicalSnapshot;
    private boolean deferredReverseLogicalPrepared;
    private AudioBackend.PreparedLogicalRestore deferredBackendRestore;
    /** True once a boundary has replaced the stale rewind target with fresh live state. */
    private boolean postBoundaryReverseTarget;
    private AudioPresentationCommandQueue shadowCommands;
    private AudioPresentationSourceFactory shadowFactory;
    private AudioPresentationCommandResolver shadowResolver;
    private AudioVoiceRegistry shadowRegistry;
    private AudioPresentationProducer shadowProducer;
    private AudioPresentationParityProbe shadowParity;
    private boolean shadowRestoreRequested;
    private AudioPresentationTuning shadowTuning;
    private AudioPresentationTuning standaloneTuning;
    private int standaloneFrameRate;
    private String standaloneGameId;
    private long standaloneVoiceId = 1;
    private SmpsCoordFlagHandlerOwner presentationCoordFlagHandlers;
    private AudioPresentationSink presentationSink;
    private final Set<String> presentationCoordHandlerGameIds =
            new LinkedHashSet<>();

    // Donor audio overlay: secondary SFX path for cross-game feature donation
    private final Map<String, SmpsLoader> donorLoaders = new HashMap<>();
    private final Map<String, DacData> donorDacData = new HashMap<>();
    private final Map<String, SmpsSequencerConfig> donorConfigs = new HashMap<>();
    private final Map<String, GameAudioProfile> donorProfiles = new HashMap<>();
    private final Map<GameSound, DonorSfxBinding> donorSoundBindings = new EnumMap<>(GameSound.class);
    private final Map<String, Map<GameMusic, Integer>> donorMusicBindings = new HashMap<>();
    private final Map<SmpsSourceDescriptor, AbstractSmpsData> restoreSmpsResolveCache = new HashMap<>();

    private record DonorSfxBinding(String gameId, int sfxId) {}

    private AudioManager() {
        // Default to NullBackend
        backend = new NullAudioBackend();
    }

    public static synchronized AudioManager getInstance() {
        if (instance == null) {
            instance = new AudioManager();
        }
        return instance;
    }

    /** Transitional dependency hook for presentation factories pending cutover. */
    public static synchronized AudioManager presentationOwner() {
        if (instance == null) {
            instance = new AudioManager();
        }
        return instance;
    }

    public static AudioManager createStandalonePresentation(
            String gameId,
            GameAudioProfile profile,
            SonicConfigurationService config,
            com.openggf.debug.PerformanceProfiler profiler,
            AudioPresentationSink sink,
            SmpsCoordFlagHandlerOwner coordFlagHandlers) {
        if (gameId == null || gameId.isBlank()) {
            throw new IllegalArgumentException("gameId must not be blank");
        }
        AudioManager manager = new AudioManager();
        manager.backend = new NullAudioBackend();
        manager.backend.init();
        manager.audioProfile = java.util.Objects.requireNonNull(
                profile, "profile");
        manager.presentationSink = java.util.Objects.requireNonNull(
                sink, "sink");
        manager.presentationCoordFlagHandlers =
                java.util.Objects.requireNonNull(
                        coordFlagHandlers, "coordFlagHandlers");
        manager.standaloneGameId = gameId;
        manager.standaloneFrameRate = FrameRateResolver.effective(
                java.util.Objects.requireNonNull(config, "config"));
        manager.standaloneTuning = new AudioPresentationTuning(
                "PAL".equalsIgnoreCase(config.getString(
                        SonicConfiguration.REGION))
                        ? SmpsSequencer.Region.PAL
                        : SmpsSequencer.Region.NTSC,
                config.getBoolean(SonicConfiguration.DAC_INTERPOLATE),
                config.getBoolean(
                        SonicConfiguration.PSG_NOISE_SHIFT_EVERY_TOGGLE),
                config.getBoolean(SonicConfiguration.FM6_DAC_OFF));
        profile.configurePresentationCoordFlagHandlers(coordFlagHandlers);
        manager.presentationCoordHandlerGameIds.add(gameId);
        manager.ensureShadowPresentation();
        return manager;
    }

    public AudioBackend getBackend() {
        return backend;
    }

    /**
     * The active backend's output (synthesis) sample rate. Routed through
     * AudioManager so callers don't reach the backend directly (see
     * TestAudioBackendBypassGuard).
     */
    public int outputSampleRate() {
        return presentationSink != null
                ? presentationSink.sampleRate()
                : backend.outputSampleRate();
    }

    void setDeterministicAudioRuntime(DeterministicAudioRuntime deterministicAudioRuntime) {
        deterministicRuntimeExplicitlyConfigured = true;
        applyDeterministicAudioRuntime(deterministicAudioRuntime);
    }

    private synchronized void applyDeterministicAudioRuntime(DeterministicAudioRuntime deterministicAudioRuntime) {
        if (activeLiveCaptureAudioHandle != null) {
            closeLiveCaptureAudio(activeLiveCaptureAudioHandle);
        }
        this.deterministicAudioRuntime = deterministicAudioRuntime != null
                ? deterministicAudioRuntime
                : NoOpDeterministicAudioRuntime.INSTANCE;
        this.deterministicAudioRuntime.setCommandHandler(this::replayTimelineCommand);
        if (backend != null) {
            backend.attachDeterministicAudioRuntime(this.deterministicAudioRuntime);
        }
    }

    public synchronized LiveCaptureAudioHandle beginLiveCaptureAudio(int frameRate) {
        if (activeLiveCaptureAudioHandle != null) {
            throw new IllegalStateException("A live capture audio handle is already attached");
        }
        ensureShadowPresentation();
        LiveCaptureAudioHandle capture = shadowProducer.attachCapture(frameRate);
        ManagerLiveCaptureAudioHandle handle =
                new ManagerLiveCaptureAudioHandle(capture);
        activeLiveCaptureAudioHandle = handle;
        return handle;
    }

    private synchronized void closeLiveCaptureAudio(ManagerLiveCaptureAudioHandle handle) {
        if (handle.closed) {
            return;
        }
        handle.closed = true;
        try {
            handle.capture.close();
        } finally {
            if (activeLiveCaptureAudioHandle == handle) {
                activeLiveCaptureAudioHandle = null;
            }
        }
    }

    /**
     * Offline-capture compatibility entry point. Attaches exactly one
     * non-consuming capture lease to the already-authoritative presentation
     * producer, so headless capture renders the same final SMPS/WAV/raw-PCM
     * packets the speaker path renders. It never installs a deterministic
     * runtime, replaces the producer/registry/sink, or opens an audio device.
     *
     * <p>{@code sampleRate} must equal the producer's rate: the producer owns
     * the clock, history, and every voice cursor, so a rate change after
     * sources have been admitted is rejected rather than migrated. Callers
     * that need a different rate must initialize the headless producer at that
     * rate (via its backend/sink) before admitting sources.
     *
     * <p>Presentation itself remains the caller's outer-frame responsibility:
     * drive exactly one {@link #presentFrame(PresentationMode)} per presented
     * outer frame, then {@link #drainCaptureFrame} that packet once.
     */
    public synchronized void beginCaptureMode(int sampleRate, int frameRate) {
        if (offlineCaptureHandle != null) {
            throw new IllegalStateException(
                    "An offline capture lease is already attached");
        }
        if (frameRate <= 0) {
            throw new IllegalArgumentException("frameRate must be positive");
        }
        ensureShadowPresentation();
        // ensureShadowPresentation guarantees the presentation sink and the
        // producer share one sample rate (a mismatched sink is replaced before
        // the producer is built, and replaceSink rejects an incompatible one),
        // so the sink rate is the producer rate.
        int producerSampleRate = outputSampleRate();
        if (sampleRate != producerSampleRate) {
            throw new IllegalArgumentException(
                    "offline capture sample rate " + sampleRate
                            + " does not match the presentation producer rate "
                            + producerSampleRate);
        }
        offlineCaptureHandle = shadowProducer.attachCapture(frameRate);
    }

    /**
     * Copies the most recently presented packet into {@code target} and
     * returns its clocked stereo-frame count. The lease is non-consuming with
     * respect to the speaker and any live-recording lease; a second drain
     * within the same presented frame yields fresh clocked silence rather than
     * stale PCM.
     */
    public synchronized int drainCaptureFrame(short[] target) {
        if (offlineCaptureHandle == null) {
            throw new IllegalStateException("beginCaptureMode() not called");
        }
        return offlineCaptureHandle.drainPresentationFrame(target);
    }

    /**
     * Idempotently closes the offline compatibility lease. Only that lease is
     * detached: the producer, registry, sink, history, rewind state, and any
     * live-recording lease are untouched.
     */
    public synchronized void endCaptureMode() {
        LiveCaptureAudioHandle handle = offlineCaptureHandle;
        if (handle == null) {
            return;
        }
        offlineCaptureHandle = null;
        handle.close();
    }

    private void configureDeterministicRuntimeForBackend() {
        if (deterministicRuntimeExplicitlyConfigured) {
            applyDeterministicAudioRuntime(deterministicAudioRuntime);
            return;
        }
        if (backend != null && backend.supportsDeterministicRuntimePresentation()) {
            int sampleRate = Math.max(1, backend.outputSampleRate());
            int frameRate = configuredFrameRate();
            int minFrameCapacity = Math.max(1, sampleRate / frameRate);
            int fifoFrames = Math.max(minFrameCapacity, sampleRate * OUTPUT_FIFO_SECONDS);
            int historyFrames = Math.max(minFrameCapacity, configuredPcmHistoryFrames(sampleRate));
            int crossfadeFrames = Math.max(1, sampleRate * REVERSE_RELEASE_CROSSFADE_MS / 1000);
            applyDeterministicAudioRuntime(new StreamBackedDeterministicAudioRuntime(
                    new AudioFrameClock(sampleRate, frameRate),
                    new AudioOutputFifo(fifoFrames),
                    new PcmHistoryRing(historyFrames),
                    crossfadeFrames));
        } else {
            applyDeterministicAudioRuntime(NoOpDeterministicAudioRuntime.INSTANCE);
        }
    }

    private int configuredFrameRate() {
        if (standaloneFrameRate > 0) {
            return standaloneFrameRate;
        }
        var config = configuredServicesOrNull();
        if (config == null) {
            return 60;
        }
        return FrameRateResolver.effective(config);
    }

    private static int configuredPcmHistoryFrames(int sampleRate) {
        var config = configuredServicesOrNull();
        if (config == null) {
            return sampleRate * PCM_HISTORY_SECONDS;
        }
        String limitType = config.getString(SonicConfiguration.REWIND_AUDIO_HISTORY_LIMIT_TYPE);
        int seconds = config.getInt(SonicConfiguration.REWIND_AUDIO_HISTORY_SECONDS);
        int sizeMB = config.getInt(SonicConfiguration.REWIND_AUDIO_HISTORY_SIZE_MB);
        return PcmHistoryRing.capacityFramesFor(sampleRate, limitType, seconds, sizeMB);
    }

    private static com.openggf.configuration.SonicConfigurationService configuredServicesOrNull() {
        try {
            return GameServices.configuration();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    public void setBackend(AudioBackend backend) {
        discardDeferredBackendRestore();
        closeShadowPresentation();
        destroyBackendQuietly(this.backend, "previous AudioBackend");
        this.backend = backend;
        try {
            this.backend.init();
            this.backend.setAudioProfile(audioProfile);
            installBackendPresentationSink();
            configureDeterministicRuntimeForBackend();
            LOGGER.info("AudioBackend initialized: " + backend.getClass().getSimpleName());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize AudioBackend", e);
            destroyBackendQuietly(this.backend, "failed AudioBackend");
            this.backend = new NullAudioBackend();
            this.backend.init();
            this.backend.setAudioProfile(audioProfile);
            presentationSink =
                    new NoDeviceAudioSink(this.backend.outputSampleRate());
            configureDeterministicRuntimeForBackend();
        }
    }

    private void installBackendPresentationSink() {
        try {
            presentationSink = backend.createPresentationSink(
                    this::handlePresentationSinkFailure,
                    warning -> LOGGER.warning("Speaker output: " + warning));
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING,
                    "Speaker device unavailable; continuing without audio output",
                    failure);
            presentationSink =
                    new NoDeviceAudioSink(backend.outputSampleRate());
        }
    }

    private void handlePresentationSinkFailure(Throwable failure) {
        LOGGER.log(Level.WARNING,
                "Speaker output failed; continuing without audio output",
                failure);
        AudioPresentationSink replacement =
                new NoDeviceAudioSink(outputSampleRate());
        presentationSink = replacement;
        if (shadowProducer != null) {
            shadowProducer.replaceSink(replacement);
        }
    }

    public void replaceFailedPresentationSink(Throwable failure) {
        handlePresentationSinkFailure(failure);
    }

    private static void destroyBackendQuietly(AudioBackend backend, String description) {
        if (backend == null) {
            return;
        }
        try {
            backend.destroy();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to destroy " + description, e);
        }
    }

    public void setAudioProfile(GameAudioProfile audioProfile) {
        this.audioProfile = audioProfile;
        clearRestoreSmpsResolveCache();
        if (backend != null) {
            backend.setAudioProfile(audioProfile);
        }
    }

    public GameAudioProfile getAudioProfile() {
        return audioProfile;
    }

    public void setRom(Rom rom) {
        this.rom = rom;
        clearRestoreSmpsResolveCache();
        if (audioProfile == null) {
            this.smpsLoader = null;
            this.dacData = null;
            return;
        }
        this.smpsLoader = audioProfile.createSmpsLoader(rom);
        this.dacData = smpsLoader != null ? smpsLoader.loadDacData() : null;
    }

    public void setSoundMap(Map<GameSound, Integer> soundMap) {
        this.soundMap = soundMap;
    }

    public void resetRingSound() {
        ringLeft = true;
        recordTimelineCommand(new AudioCommand.ResetRingAlternation(true));
    }

    public AudioCommandTimeline commandTimeline() {
        return commandTimeline;
    }

    public void beginCommandTimelineFrame(long frame) {
        commandTimeline.beginFrame(frame);
        refreshDeferredReverseLogicalSnapshot();
        audioFrameOwnedExternally = true;
        audioFrameAdvanced = false;
    }

    public void beginGameplayAudioFrame(long frame) {
        // Clamp forward-only. The GameLoop's gameplayAudioFrame counter only
        // increments while shouldAdvanceGameplayAudioForCurrentMode() is true
        // (LEVEL / BONUS_STAGE / TITLE_CARD), but commandTimeline.currentFrame
        // also advances every tick via audioManager.update() in non-gameplay
        // modes (MASTER_TITLE_SCREEN, LEVEL_SELECT, DATA_SELECT, etc.). If a
        // session transitions through a long non-gameplay mode and then enters
        // a level, currentFrame can be far ahead of gameplayAudioFrame at the
        // first gameplay tick. Without the clamp, beginCommandTimelineFrame
        // would set currentFrame BACKWARD to gameplayAudioFrame — and any
        // command submitted during the non-gameplay window (e.g. playMusic
        // for the selected level, or fadeOutMusic during exitLevelSelect)
        // would sit in pendingCommands with a frame number larger than the
        // new currentFrame, leaving consumeCommands' frame<=current filter
        // unable to drain it. SFX queued fresh in-level uses the new low
        // frame and processes immediately, which explains the music-delayed-
        // but-SFX-works symptom. Rewind seeks go through
        // beginCommandTimelineFrame directly and remain unaffected.
        long monotonic = Math.max(frame, commandTimeline.currentFrame() + 1);
        beginCommandTimelineFrame(monotonic);
    }

    public void discardAudioCommandsAfter(long frame) {
        commandTimeline.discardAfter(frame);
        deterministicAudioRuntime.discardSubmittedCommandsAfter(frame);
    }

    /**
     * Drops command-timeline history below the given absolute entry index.
     * Keyed off the earliest retained audio keyframe's
     * {@code commandEntryCount} when rewind history is pruned, so every
     * retained keyframe keeps a valid replay range while the timeline stays
     * bounded.
     */
    public void pruneAudioCommandsBefore(int entryIndex) {
        commandTimeline.pruneBefore(entryIndex);
    }

    public AudioLogicalSnapshot captureLogicalSnapshot() {
        ensureShadowPresentation();
        Set<String> donorGameIds = new LinkedHashSet<>();
        donorGameIds.addAll(donorLoaders.keySet());
        donorGameIds.addAll(donorDacData.keySet());
        donorGameIds.addAll(donorConfigs.keySet());

        Set<AudioLogicalSnapshot.DonorSfxBindingSnapshot> donorBindings = new LinkedHashSet<>();
        for (Map.Entry<GameSound, DonorSfxBinding> entry : donorSoundBindings.entrySet()) {
            DonorSfxBinding binding = entry.getValue();
            donorBindings.add(new AudioLogicalSnapshot.DonorSfxBindingSnapshot(
                    entry.getKey(), binding.gameId(), binding.sfxId()));
        }

        return new AudioLogicalSnapshot(
                ringLeft,
                commandTimeline.currentFrame(),
                commandTimeline.nextOrder(),
                commandTimeline.entryCount(),
                backend != null ? backend.captureLogicalSnapshot() : AudioBackendLogicalSnapshot.empty(),
                shadowProducer.snapshot(),
                donorGameIds,
                donorBindings);
    }

    public void restoreLogicalSnapshot(AudioLogicalSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        ensureShadowPresentation();
        if (reverseAudioPresentationActive) {
            deferredReverseLogicalSnapshot = snapshot;
            return;
        }
        restoreLogicalSnapshotNow(snapshot, false);
    }

    private boolean restoreLogicalSnapshotNow(
            AudioLogicalSnapshot snapshot, boolean preservePresentation) {
        AudioBackendLogicalSnapshot previousBackend = backend != null
                ? backend.captureLogicalSnapshot()
                : AudioBackendLogicalSnapshot.empty();
        AudioPresentationSnapshot previousPresentation =
                shadowProducer.snapshot();
        boolean previousRingLeft = ringLeft;
        long previousTimelineFrame = commandTimeline.currentFrame();
        int previousTimelineOrder = commandTimeline.nextOrder();
        Map<GameSound, DonorSfxBinding> previousBindings =
                new EnumMap<>(donorSoundBindings);
        try {
            if (backend != null) {
                backend.restoreLogicalSnapshot(
                        snapshot.backend(),
                        createSmpsDependencyResolver(),
                        preservePresentation);
            }
            shadowProducer.restore(snapshot.presentation(), shadowFactory,
                    preservePresentation);
            ringLeft = snapshot.ringLeft();
            commandTimeline.restoreCursor(snapshot.commandTimelineFrame(),
                    snapshot.commandTimelineNextOrder());
            donorSoundBindings.clear();
            for (AudioLogicalSnapshot.DonorSfxBindingSnapshot binding
                    : snapshot.donorBindings()) {
                donorSoundBindings.put(binding.sound(),
                        new DonorSfxBinding(binding.donorGameId(),
                                binding.sfxId()));
            }
            return true;
        } catch (RuntimeException failure) {
            ringLeft = previousRingLeft;
            commandTimeline.restoreCursor(previousTimelineFrame,
                    previousTimelineOrder);
            donorSoundBindings.clear();
            donorSoundBindings.putAll(previousBindings);
            try {
                if (backend != null) {
                    backend.restoreLogicalSnapshot(previousBackend,
                            createSmpsDependencyResolver(),
                            preservePresentation);
                }
                shadowProducer.restore(previousPresentation, shadowFactory,
                        preservePresentation);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            LOGGER.log(Level.WARNING,
                    "Audio snapshot restore failed; retained prior state",
                    failure);
            return false;
        }
    }

    public void playSegaPcm() {
        if (suppressingRewindReplay() || audioProfile == null || rom == null) {
            return;
        }
        SegaPcmSpec spec = audioProfile.getSegaPcmSpec();
        if (spec == null) {
            return;
        }
        try {
            byte[] pcm = rom.readBytes(spec.address(), spec.length());
            mirrorShadowCommand(() ->
                    shadowResolver.submitRawPcm(pcm, spec.sampleRate()));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to play SEGA PCM sample", e);
        }
    }

    public void stopSegaPcm() {
        if (suppressingRewindReplay()) {
            return;
        }
        mirrorShadowCommand(() -> shadowResolver.stopRawPcm());
    }

    public void playStandaloneMusic(
            AbstractSmpsData data, DacData dac) {
        ensureStandalonePresentation();
        int musicId = data.getId();
        int maxFrames = (outputSampleRate() + configuredFrameRate() - 1)
                / configuredFrameRate();
        var music = shadowFactory.musicSmps(
                standaloneGameId, musicId, standaloneVoiceId++,
                data, dac, audioProfile.getSequencerConfig(),
                AudioSourceDescriptor.baseMusic(musicId), maxFrames);
        shadowCommands.submit(
                new AudioPresentationCommand.ReplaceMusic(music),
                () -> true, shadowRegistry::apply);
    }

    public void playStandaloneSfx(
            AbstractSmpsData data, DacData dac, float pitch) {
        ensureStandalonePresentation();
        int sfxId = data.getId();
        var key = new com.openggf.audio.presentation.SmpsAssetKey(
                standaloneGameId,
                com.openggf.audio.presentation.SmpsAssetKey.Route.BASE_ID,
                sfxId, null);
        shadowFactory.warmSmpsSfxAsset(
                key, data, dac, audioProfile.getSequencerConfig(),
                audioProfile.isSpecialSfx(sfxId));
        int continuous = audioProfile.isContinuousSfx(sfxId)
                ? sfxId : 0;
        int maxFrames = (outputSampleRate() + configuredFrameRate() - 1)
                / configuredFrameRate();
        var source = shadowFactory.resolveSmpsSfx(
                standaloneVoiceId++, key,
                Math.max(1, Math.round(pitch * 65_536.0f)),
                audioProfile.getSfxPriority(sfxId), continuous,
                data.getChannels() + data.getPsgChannels(), maxFrames);
        shadowCommands.submit(
                new AudioPresentationCommand.AddSmpsSfx(source),
                () -> true, shadowRegistry::apply);
    }

    public void stopStandalonePlayback() {
        ensureStandalonePresentation();
        shadowCommands.submit(new AudioPresentationCommand.StopMusic(),
                () -> true, shadowRegistry::apply);
        shadowCommands.submit(new AudioPresentationCommand.StopAllSfx(),
                () -> true, shadowRegistry::apply);
        shadowCommands.submit(new AudioPresentationCommand.StopRawPcm(),
                () -> true, shadowRegistry::apply);
    }

    SmpsSequencerConfig bindLegacyConfigToPresentationOwner(
            SmpsSequencerConfig config) {
        ensureShadowPresentation();
        String gameId = config.getCoordFlagHandler() == null
                ? (audioProfile != null
                ? audioProfile.presentationGameId() : "base")
                : "s3k";
        return shadowFactory.legacySequencerConfig(gameId, config);
    }

    private void ensureStandalonePresentation() {
        if (standaloneGameId == null) {
            throw new IllegalStateException(
                    "not a standalone presentation manager");
        }
        ensureShadowPresentation();
    }

    private SmpsDriverSnapshot.DependencyResolver createSmpsDependencyResolver() {
        return new SmpsDriverSnapshot.DependencyResolver() {
            @Override
            public AbstractSmpsData resolveSmpsData(SmpsDriverSnapshot.SequencerEntry entry) {
                return resolveRestoreSmpsData(entry);
            }

            @Override
            public DacData resolveDacData(SmpsDriverSnapshot.SequencerEntry entry) {
                SmpsSourceDescriptor source = entry.source();
                return switch (source.kind()) {
                    case DONOR_MUSIC, DONOR_SFX_ID -> donorDacData.getOrDefault(source.donorGameId(), entry.dacData());
                    default -> dacData != null ? dacData : entry.dacData();
                };
            }

            @Override
            public AudioManager resolveAudioManager(SmpsDriverSnapshot.SequencerEntry entry) {
                return AudioManager.this;
            }

            @Override
            public SmpsSequencerConfig resolveConfig(SmpsDriverSnapshot.SequencerEntry entry) {
                SmpsSourceDescriptor source = entry.source();
                if (source.kind() == SmpsSourceDescriptor.Kind.DONOR_MUSIC
                        || source.kind() == SmpsSourceDescriptor.Kind.DONOR_SFX_ID) {
                    return donorConfigs.getOrDefault(source.donorGameId(), entry.config());
                }
                SmpsSequencerConfig config = audioProfile != null ? audioProfile.getSequencerConfig() : null;
                return config != null ? config : entry.config();
            }
        };
    }

    private AbstractSmpsData resolveRestoreSmpsData(SmpsDriverSnapshot.SequencerEntry entry) {
        SmpsSourceDescriptor source = entry.source();
        if (source.kind() == SmpsSourceDescriptor.Kind.UNKNOWN) {
            return entry.smpsData();
        }
        AbstractSmpsData cached = restoreSmpsResolveCache.get(source);
        if (cached != null) {
            return cached;
        }
        AbstractSmpsData resolved = switch (source.kind()) {
            case UNKNOWN -> entry.smpsData();
            case BASE_MUSIC -> smpsLoader != null ? smpsLoader.loadMusic(source.id()) : entry.smpsData();
            case BASE_SFX_ID -> smpsLoader != null ? smpsLoader.loadSfx(source.id()) : entry.smpsData();
            case BASE_SFX_NAME -> smpsLoader != null ? smpsLoader.loadSfx(source.name()) : entry.smpsData();
            case DONOR_MUSIC -> resolveDonorLoader(source).loadMusic(source.id());
            case DONOR_SFX_ID -> resolveDonorLoader(source).loadSfx(source.id());
        };
        if (resolved == null || !source.matchesData(resolved)) {
            resolved = entry.smpsData();
        }
        restoreSmpsResolveCache.put(source, resolved);
        return resolved;
    }

    private SmpsLoader resolveDonorLoader(SmpsSourceDescriptor source) {
        SmpsLoader loader = donorLoaders.get(source.donorGameId());
        if (loader == null) {
            throw new IllegalStateException("No donor SMPS loader registered for " + source.donorGameId());
        }
        return loader;
    }

    private void clearRestoreSmpsResolveCache() {
        restoreSmpsResolveCache.clear();
    }

    public AudioReplayScope beginRewindReplay(int fromFrame, int targetFrame, AudioReplayReason reason) {
        rewindReplaySuppressionDepth++;
        return new AudioReplayScope() {
            private boolean closed;

            @Override
            public void close() {
                if (closed) {
                    return;
                }
                closed = true;
                if (rewindReplaySuppressionDepth > 0) {
                    rewindReplaySuppressionDepth--;
                }
            }
        };
    }

    public boolean isRewindReplaySuppressed() {
        return rewindReplaySuppressionDepth > 0;
    }

    public void replayTimelineCommand(AudioCommand command) {
        if (backend == null || command == null) {
            return;
        }
        switch (command) {
            case AudioCommand.PlayMusic playMusic -> replayMusic(playMusic);
            case AudioCommand.PlaySfx playSfx -> replaySfx(playSfx);
            case AudioCommand.FadeOutMusic fade -> backend.fadeOutMusic(fade.steps(), fade.delay());
            case AudioCommand.StopMusic ignored -> backend.stopPlayback();
            case AudioCommand.StopAllSfx ignored -> backend.stopAllSfx();
            case AudioCommand.EndMusicOverride end -> backend.endMusicOverride(end.musicId());
            case AudioCommand.RestoreMusic ignored -> backend.restoreMusic();
            case AudioCommand.SetSpeedShoes speed -> backend.setSpeedShoes(speed.enabled());
            case AudioCommand.SetSpeedMultiplier speed -> backend.setSpeedMultiplier(speed.multiplier());
            case AudioCommand.ChangeMusicTempo tempo -> backend.changeMusicTempo(tempo.dividingTiming());
            case AudioCommand.ResetRingAlternation reset -> ringLeft = reset.ringLeft();
        }
    }

    public void replayTimelineCommandLogically(AudioCommand command) {
        if (command == null) {
            return;
        }
        stageDeferredPresentationCommand(command);
        refreshDeferredReverseLogicalSnapshot();
    }

    /**
     * Replays one logical command into a detached presentation registry.
     * The live shadow and its rewind/history cursor remain untouched until
     * the manager-owned dual restore commits at the selected target.
     */
    private void stageDeferredPresentationCommand(AudioCommand command) {
        if (command == null) {
            return;
        }
        ensureShadowPresentation();
        if (deferredReverseLogicalSnapshot == null) {
            shadowResolver.submit(command);
            shadowCommands.applyPending(shadowRegistry::apply);
            ringLeft = managerRingAfter(ringLeft, command);
            return;
        }
        AudioLogicalSnapshot selected = deferredReverseLogicalSnapshot;
        SmpsCoordFlagRuntimeState.Snapshot liveCoord =
                presentationCoordFlagHandlers.state().snapshot();
        AudioPresentationCommandQueue stagedCommands =
                new AudioPresentationCommandQueue();
        AudioVoiceRegistry stagedRegistry = new AudioVoiceRegistry(
                shadowFactory, shadowFactory, presentationCoordFlagHandlers,
                warning -> LOGGER.warning(
                        "Presentation rewind staging: " + warning));
        try {
            stagedRegistry.restore(selected.presentation(), shadowFactory);
            String[] resolutionFailure = new String[1];
            AudioPresentationCommandResolver stagedResolver =
                    new AudioPresentationCommandResolver(
                            stagedCommands, shadowFactory,
                            new ShadowSources(
                                    (Math.max(1, backend != null
                                            ? backend.outputSampleRate()
                                            : 48_000)
                                            + configuredFrameRate() - 1)
                                            / configuredFrameRate()),
                            warning -> resolutionFailure[0] = warning,
                            () -> true, stagedRegistry::apply);
            long nextVoice = selected.presentation().voices().stream()
                    .mapToLong(voice -> switch (voice) {
                        case PresentationVoiceSnapshot.Smps smps ->
                                smps.voiceId();
                        case PresentationVoiceSnapshot.Sample sample ->
                                sample.voiceId();
                    })
                    .max().orElse(0L) + 1L;
            nextVoice = Math.max(
                    nextVoice, selected.presentation().nextVoiceId());
            stagedResolver.reserveVoiceIdsThrough(nextVoice);
            stagedResolver.submit(command);
            boolean optionalFallbackSfx =
                    command instanceof AudioCommand.PlaySfx sfx
                            && (sfx.route()
                            == AudioCommand.SfxRoute.RING_RESOLVED
                            || sfx.route()
                            == AudioCommand.SfxRoute.FALLBACK_NAME);
            if (resolutionFailure[0] != null && !optionalFallbackSfx) {
                throw new IllegalStateException(
                        "Presentation rewind command resolution failed: "
                                + resolutionFailure[0]);
            }
            stagedCommands.applyPending(stagedRegistry::apply);
            AudioPresentationSnapshot staged = stagedRegistry.snapshot();
            AudioBackendLogicalSnapshot stagedBackend =
                    backendSnapshotFromPresentation(
                            selected.backend(), staged);
            boolean stagedManagerRingLeft =
                    managerRingAfter(selected.ringLeft(), command);
            deferredReverseLogicalSnapshot = new AudioLogicalSnapshot(
                    stagedManagerRingLeft,
                    selected.commandTimelineFrame(),
                    selected.commandTimelineNextOrder(),
                    selected.commandEntryCount(),
                    stagedBackend,
                    staged,
                    selected.donorGameIds(),
                    selected.donorBindings());
        } finally {
            stagedRegistry.clear();
            presentationCoordFlagHandlers.state().restore(liveCoord);
        }
    }

    private boolean managerRingAfter(
            boolean current, AudioCommand command) {
        if (command instanceof AudioCommand.ResetRingAlternation reset) {
            return reset.ringLeft();
        }
        if (command instanceof AudioCommand.PlaySfx sfx
                && sfx.route() == AudioCommand.SfxRoute.RING_RESOLVED) {
            if (GameSound.RING_LEFT.name().equals(sfx.sfxName())) {
                return false;
            }
            if (GameSound.RING_RIGHT.name().equals(sfx.sfxName())) {
                return true;
            }
        }
        return current;
    }

    private void refreshDeferredReverseLogicalSnapshot() {
        if (deferredReverseLogicalSnapshot == null) {
            return;
        }
        AudioLogicalSnapshot selected = deferredReverseLogicalSnapshot;
        deferredReverseLogicalSnapshot = new AudioLogicalSnapshot(
                selected.ringLeft(),
                commandTimeline.currentFrame(),
                commandTimeline.nextOrder(),
                selected.commandEntryCount(),
                selected.backend(),
                selected.presentation(),
                selected.donorGameIds(),
                selected.donorBindings());
    }

    private AudioBackendLogicalSnapshot backendSnapshotFromPresentation(
            AudioBackendLogicalSnapshot previous,
            AudioPresentationSnapshot presentation) {
        SmpsDriverSnapshot musicDriver = driverForVoice(
                presentation, presentation.activeMusic() == null
                        ? null : presentation.activeMusic().voiceId());
        SmpsDriverSnapshot standaloneSfxDriver = driverForVoice(
                presentation, presentation.standaloneSmpsVoiceId());
        List<AudioSourceDescriptor> overrides =
                presentation.overrideStack().stream()
                        .map(AudioPresentationSnapshot.MusicSlotSnapshot
                                ::sourceDescriptor)
                        .toList();
        return new AudioBackendLogicalSnapshot(
                presentation.activeMusic() == null
                        ? null
                        : presentation.activeMusic().sourceDescriptor(),
                presentation.sfxBlocked(),
                presentation.pendingRestore(),
                presentation.speedShoesEnabled(),
                presentation.speedMultiplier(),
                overrides,
                musicDriver,
                standaloneSfxDriver,
                presentation.fmMuteMask(),
                presentation.fmSoloMask(),
                presentation.psgMuteMask(),
                presentation.psgSoloMask());
    }

    private SmpsDriverSnapshot driverForVoice(
            AudioPresentationSnapshot presentation, Long voiceId) {
        if (voiceId == null) {
            return null;
        }
        for (PresentationVoiceSnapshot voice : presentation.voices()) {
            if (voice instanceof PresentationVoiceSnapshot.Smps smps
                    && smps.voiceId() == voiceId) {
                return smps.driver();
            }
        }
        throw new IllegalStateException(
                "Presentation slot references missing SMPS voice " + voiceId);
    }

    private void replayMusic(AudioCommand.PlayMusic command) {
        switch (command.route()) {
            case BASE_SMPS -> {
                if (smpsLoader != null) {
                    AbstractSmpsData data = smpsLoader.loadMusic(command.musicId());
                    if (data != null) {
                        backend.prepareLogicalMusicSource(AudioSourceDescriptor.baseMusic(command.musicId()));
                        backend.playSmps(data, dacData);
                    }
                }
            }
            case DONOR_SMPS -> {
                SmpsLoader loader = donorLoaders.get(command.donorGameId());
                DacData dData = donorDacData.get(command.donorGameId());
                if (loader != null && dData != null) {
                    AbstractSmpsData data = loader.loadMusic(command.musicId());
                    if (data != null) {
                        backend.prepareLogicalMusicSource(AudioSourceDescriptor.donorMusic(
                                command.donorGameId(), command.musicId()));
                        backend.playSmps(data, dData, donorConfigs.get(command.donorGameId()), true);
                    }
                }
            }
            case FALLBACK_WAV -> {
                backend.prepareLogicalMusicSource(AudioSourceDescriptor.fallbackMusic(command.musicId()));
                backend.playMusic(command.musicId());
            }
            case SYSTEM_COMMAND -> {
            }
        }
    }

    private void replaySfx(AudioCommand.PlaySfx command) {
        switch (command.route()) {
            case BASE_SMPS_ID -> {
                if (smpsLoader != null) {
                    AbstractSmpsData sfx = smpsLoader.loadSfx(command.sfxId());
                    if (sfx != null) {
                        backend.playSfxSmps(sfx, dacData, command.pitch());
                    }
                }
            }
            case BASE_SMPS_NAME -> {
                if (smpsLoader != null) {
                    AbstractSmpsData sfx = smpsLoader.loadSfx(command.sfxName());
                    if (sfx != null) {
                        backend.playSfxSmps(sfx, dacData, command.pitch());
                    }
                }
            }
            case DONOR_SMPS -> {
                SmpsLoader loader = donorLoaders.get(command.donorGameId());
                DacData dData = donorDacData.get(command.donorGameId());
                if (loader != null && dData != null) {
                    AbstractSmpsData sfx = loader.loadSfx(command.sfxId());
                    if (sfx != null) {
                        SmpsSequencerConfig config = donorConfigs.get(command.donorGameId());
                        if (config != null) {
                            backend.playSfxSmps(sfx, dData, command.pitch(), config);
                        } else {
                            backend.playSfxSmps(sfx, dData, command.pitch());
                        }
                    }
                }
            }
            case FALLBACK_NAME, RING_RESOLVED -> backend.playSfx(command.sfxName(), command.pitch());
        }
    }

    private boolean suppressingRewindReplay() {
        return rewindReplaySuppressionDepth > 0;
    }

    public boolean afterRewindRestore(
            int frame, AudioPresentationPolicy policy) {
        if (policy == null) {
            return true;
        }
        ensureShadowPresentation();
        boolean preservePostBoundarySources =
                postBoundaryReverseTarget;
        if (policy != AudioPresentationPolicy.SUPPRESSED_INTERNAL_RESTORE) {
            if (!endReverseAudioPresentation()) {
                return false;
            }
            shadowProducer.applyPendingCommandsAtOwnerBoundary();
        }
        if (preservePostBoundarySources) {
            return true;
        }
        switch (policy) {
            case SUPPRESSED_INTERNAL_RESTORE -> {
            }
            case STOP_TRANSIENT_SFX_RESYNC_MUSIC -> {
                shadowRegistry.stopTransientVoices();
                shadowRegistry.apply(
                        new AudioPresentationCommand.RestoreMusicOverride());
            }
            case STOP_TRANSIENT_SFX ->
                    shadowRegistry.stopTransientVoices();
            case STOP_ALL_PRESENTATION -> {
                shadowRegistry.clear();
                shadowProducer.clearHistory();
                shadowParity.historyBoundary();
            }
        }
        return true;
    }

    public void beginReverseAudioPresentation() {
        discardDeferredBackendRestore();
        deferredReverseLogicalSnapshot = null;
        deferredReverseLogicalPrepared = false;
        deferredBackendRestore = null;
        postBoundaryReverseTarget = false;
        reverseAudioPresentationActive = true;
        ensureShadowPresentation();
        shadowProducer.beginReverse(1.0);
    }

    public boolean isReverseAudioPresentationActive() {
        return reverseAudioPresentationActive;
    }

    public boolean endReverseAudioPresentation() {
        AudioLogicalSnapshot selected = deferredReverseLogicalSnapshot;
        boolean preparedDuringAttempt = false;
        boolean backendCommitAttempted = false;
        try {
            if (selected != null && !deferredReverseLogicalPrepared) {
                if (!commitDeferredReverseLogicalRestore()) {
                    return false;
                }
                preparedDuringAttempt = true;
            }
            if (deferredBackendRestore != null && backend != null) {
                backendCommitAttempted = true;
                backend.commitLogicalRestore(deferredBackendRestore);
            }
            if (shadowProducer != null) {
                shadowProducer.endReverse(
                        !postBoundaryReverseTarget);
            }
            if (selected != null) {
                ringLeft = selected.ringLeft();
                commandTimeline.restoreCursor(
                        selected.commandTimelineFrame(),
                        selected.commandTimelineNextOrder());
                donorSoundBindings.clear();
                for (AudioLogicalSnapshot.DonorSfxBindingSnapshot binding
                        : selected.donorBindings()) {
                    donorSoundBindings.put(binding.sound(),
                            new DonorSfxBinding(binding.donorGameId(),
                                    binding.sfxId()));
                }
            }
            deferredReverseLogicalSnapshot = null;
            deferredReverseLogicalPrepared = false;
            deferredBackendRestore = null;
            reverseAudioPresentationActive = false;
            postBoundaryReverseTarget = false;
        } catch (RuntimeException failure) {
            if (backendCommitAttempted && backend != null
                    && deferredBackendRestore != null) {
                try {
                    backend.rollbackLogicalRestore(deferredBackendRestore);
                } catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            if (preparedDuringAttempt) {
                if (backend != null && deferredBackendRestore != null) {
                    try {
                        backend.discardLogicalRestore(
                                deferredBackendRestore);
                    } catch (RuntimeException discardFailure) {
                        failure.addSuppressed(discardFailure);
                    }
                }
                try {
                    shadowProducer.discardPreparedRestoreSelection();
                } catch (RuntimeException discardFailure) {
                    failure.addSuppressed(discardFailure);
                }
                deferredReverseLogicalPrepared = false;
                deferredBackendRestore = null;
            }
            LOGGER.log(Level.WARNING,
                    "Audio reverse release failed; retained prior dual state",
                    failure);
            return false;
        }
        return true;
    }

    /**
     * Replaces any selected/prepared pre-boundary rewind target with a capture
     * of the already initialized post-boundary backend and producer state.
     *
     * <p>The first call owns replacement and preparation. Later calls are
     * retries: they retain the exact fresh prepared token after a failed
     * publication instead of recapturing or rebuilding it.
     */
    public boolean preparePostBoundaryReverseRelease() {
        if (!reverseAudioPresentationActive) {
            return true;
        }
        ensureShadowPresentation();
        if (!postBoundaryReverseTarget) {
            AudioLogicalSnapshot fresh;
            try {
                // A level/seamless transition may initialize presentation
                // sources after the final frame packet. Publish that ledger at
                // this owner boundary without advancing PCM cadence/history,
                // then capture the complete fresh producer state.
                shadowProducer.applyPendingCommandsAtOwnerBoundary();
                fresh = captureLogicalSnapshot();
            } catch (RuntimeException failure) {
                // Successfully applied predecessors have already left the
                // ledger; the failing command and successors remain queued.
                // The stale selected/prepared target is retained until a
                // complete drain and fresh capture can replace it.
                LOGGER.log(Level.WARNING,
                        "Audio post-boundary command publication failed; "
                                + "retained coherent live state for retry",
                        failure);
                return false;
            }
            discardDeferredBackendRestore();
            shadowProducer.discardPreparedRestoreSelection();
            deferredReverseLogicalSnapshot = fresh;
            deferredReverseLogicalPrepared = false;
            postBoundaryReverseTarget = true;
        }
        return commitDeferredReverseLogicalRestore();
    }

    /**
     * Prepares the one manager-owned dual logical restore at a held-rewind
     * commit boundary without consuming either reverse PCM cursor.
     */
    public boolean commitDeferredReverseLogicalRestore() {
        if (!reverseAudioPresentationActive
                || deferredReverseLogicalSnapshot == null
                || deferredReverseLogicalPrepared) {
            return deferredReverseLogicalPrepared;
        }
        AudioLogicalSnapshot selected = deferredReverseLogicalSnapshot;
        AudioBackend.PreparedLogicalRestore preparedBackend = null;
        try {
            preparedBackend = backend != null
                    ? backend.prepareLogicalRestore(
                            selected.backend(),
                            createSmpsDependencyResolver(), true)
                    : null;
            shadowProducer.prepareRestoreSelection(
                    selected.presentation(), shadowFactory);
            deferredBackendRestore = preparedBackend;
            deferredReverseLogicalPrepared = true;
            return true;
        } catch (RuntimeException failure) {
            if (preparedBackend != null && backend != null) {
                try {
                    backend.discardLogicalRestore(preparedBackend);
                } catch (RuntimeException discardFailure) {
                    failure.addSuppressed(discardFailure);
                }
            }
            deferredBackendRestore = null;
            deferredReverseLogicalPrepared = false;
            LOGGER.log(Level.WARNING,
                    "Audio reverse target preparation failed; retained live "
                            + "dual state for retry",
                    failure);
            return false;
        }
    }

    private void discardDeferredBackendRestore() {
        AudioBackend.PreparedLogicalRestore prepared =
                deferredBackendRestore;
        if (prepared == null) {
            return;
        }
        try {
            if (backend != null) {
                backend.discardLogicalRestore(prepared);
            }
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING,
                    "Failed to discard unpublished backend logical restore",
                    failure);
        } finally {
            deferredBackendRestore = null;
            deferredReverseLogicalPrepared = false;
        }
    }

    public void setReversePlaybackRate(double rate) {
        ensureShadowPresentation();
        shadowProducer.setReverseRate(rate);
    }

    /**
     * Clears the raw PCM rewind-history ring. Callers must invoke this at a
     * hard rewind boundary (e.g. a fresh level load) so a subsequent held
     * rewind cannot play back samples recorded before the boundary — the
     * ring is a fixed-duration buffer independent of the logical rewind
     * keyframe/frame-counter reset, so it survives that reset unless cleared
     * explicitly.
     */
    public void clearPcmHistory() {
        ensureShadowPresentation();
        shadowProducer.clearHistory();
        shadowParity.historyBoundary();
    }

    /**
     * Arms or disarms continuous PCM rewind-history recording on the
     * authoritative producer. Callers that own a rewind consumer's lifecycle
     * (held-key live
     * rewind, Trace Test Mode) should arm this only while that consumer is
     * actually able to be used, and disarm it the moment it no longer is —
     * recording unconditionally wastes a buffer copy every audio callback for
     * sessions that can never rewind, and leaves history around to leak across
     * a later boundary.
     */
    public void setRewindHistoryArmed(boolean armed) {
        ensureShadowPresentation();
        shadowProducer.setHistoryArmed(armed);
    }

    public void presentFrame(PresentationMode mode) {
        ensureShadowPresentation();
        shadowProducer.present(commandTimeline.currentFrame(), mode);
        audioFrameAdvanced = true;
        shadowParity.presented(mode);
        if (shadowRestoreRequested) {
            shadowRestoreRequested = false;
            mirrorShadowCommand(() -> shadowCommands.submit(
                    new AudioPresentationCommand.RestoreMusicOverride(),
                    () -> false, command -> { }));
        }
    }

    /**
     * Immutable diagnostic counters for the temporary shadow-parity gate.
     */
    AudioPresentationParityProbe.Snapshot shadowParitySnapshot() {
        ensureShadowPresentation();
        return shadowParity.snapshot();
    }

    LiveCaptureAudioHandle attachShadowCaptureForTesting(int frameRate) {
        ensureShadowPresentation();
        return shadowProducer.attachCapture(frameRate);
    }

    AudioLogicalSnapshot deferredReverseLogicalSnapshotForTesting() {
        return deferredReverseLogicalSnapshot;
    }

    AudioPresentationTuning shadowTuningForTesting() {
        ensureShadowPresentation();
        return shadowTuning;
    }

    SmpsCoordFlagHandlerOwner presentationCoordFlagHandlersForTesting() {
        ensureShadowPresentation();
        return presentationCoordFlagHandlers;
    }

    SmpsDriverSnapshot shadowSmpsDriverSnapshotForTesting() {
        ensureShadowPresentation();
        for (int index = 0; index < shadowRegistry.orderedVoiceCount(); index++) {
            if (shadowRegistry.orderedVoiceAt(index)
                    instanceof SmpsCompositeVoice composite) {
                return composite.driver().captureSnapshot();
            }
        }
        return null;
    }

    AudioPresentationSourceFactory shadowFactoryForTesting() {
        ensureShadowPresentation();
        return shadowFactory;
    }

    ReleaseStateForTesting releaseStateForTesting() {
        ensureShadowPresentation();
        AbstractSmpsAudioBackend.StateForTesting backendState =
                backend instanceof AbstractSmpsAudioBackend smpsBackend
                        ? smpsBackend.stateForTesting() : null;
        return new ReleaseStateForTesting(
                captureLogicalSnapshot(),
                backendState,
                shadowProducer.transactionFingerprint());
    }

    record ReleaseStateForTesting(
            AudioLogicalSnapshot logical,
            AbstractSmpsAudioBackend.StateForTesting backend,
            AudioPresentationProducer.TransactionFingerprint producer) {
    }

    void submitShadowRawPcmForTesting(
            byte[] pcm, int sourceSampleRate) {
        ensureShadowPresentation();
        shadowResolver.submitRawPcm(pcm, sourceSampleRate);
    }

    /** @deprecated Task 10 presentation uses {@link #presentFrame}. */
    @Deprecated
    public void advancePausedFrameStepAudio() {
        advanceRuntimeFrame(FrameAudioMode.SILENT_STEP);
    }

    /** @deprecated Task 10 presentation uses {@link #presentFrame}. */
    @Deprecated
    public void advanceGameplayFrameAudio() {
        advanceRuntimeFrame(FrameAudioMode.NORMAL);
    }

    private void advanceRuntimeFrame(FrameAudioMode mode) {
        deterministicAudioRuntime.advanceFrame(commandTimeline.currentFrame(), mode);
        audioFrameAdvanced = true;
    }

    public void restoreMusic() {
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.RestoreMusic(AudioCommand.RestoreCause.EXPLICIT));
        if (sendLiveBackendCommands()) {
            backend.restoreMusic();
        }
    }

    public void setSpeedShoes(boolean enabled) {
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.SetSpeedShoes(enabled));
        if (sendLiveBackendCommands()) {
            backend.setSpeedShoes(enabled);
        }
    }

    public void setSpeedMultiplier(int multiplier) {
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.SetSpeedMultiplier(multiplier));
        if (sendLiveBackendCommands()) {
            backend.setSpeedMultiplier(multiplier);
        }
    }

    public void playMusic(int musicId) {
        if (suppressingRewindReplay()) {
            return;
        }
        if (audioProfile != null) {
            if (audioProfile.handleSystemCommand(musicId, this)) {
                return;
            }
            if (musicId == audioProfile.getSpeedShoesOnCommandId()) {
                if (audioProfile.getSpeedMode() == GameAudioProfile.SpeedMode.FRAME_MULTIPLY) {
                    setSpeedMultiplier(audioProfile.getSpeedMultiplierValue());
                } else {
                    setSpeedShoes(true);
                }
                return;
            } else if (musicId == audioProfile.getSpeedShoesOffCommandId()) {
                if (audioProfile.getSpeedMode() == GameAudioProfile.SpeedMode.FRAME_MULTIPLY) {
                    setSpeedMultiplier(1);
                } else {
                    setSpeedShoes(false);
                }
                return;
            }
        }

        if (smpsLoader != null) {
            AbstractSmpsData data = smpsLoader.loadMusic(musicId);
            if (data != null) {
                recordTimelineCommand(new AudioCommand.PlayMusic(
                        musicId, AudioCommand.MusicRoute.BASE_SMPS, false, null));
                if (sendLiveBackendCommands()) {
                    backend.prepareLogicalMusicSource(AudioSourceDescriptor.baseMusic(musicId));
                    backend.playSmps(data, dacData);
                }
                return;
            }
        }
        recordTimelineCommand(new AudioCommand.PlayMusic(
                musicId, AudioCommand.MusicRoute.FALLBACK_WAV, false, null));
        if (sendLiveBackendCommands()) {
            backend.prepareLogicalMusicSource(AudioSourceDescriptor.fallbackMusic(musicId));
            backend.playMusic(musicId);
        }
    }

    public boolean playMusic(GameMusic music) {
        Integer musicId = resolveMusic(audioProfile, music);
        if (musicId == null) {
            return false;
        }
        playMusic(musicId);
        return true;
    }

    private static Integer resolveMusic(GameAudioProfile profile, GameMusic music) {
        if (music == null || profile == null) {
            return null;
        }
        return profile.getMusicMap().get(music);
    }

    public void playSfx(String sfxName) {
        playSfx(sfxName, 1.0f);
    }

    public void playSfx(String sfxName, float pitch) {
        if (suppressingRewindReplay()) {
            return;
        }
        if (smpsLoader != null) {
            AbstractSmpsData sfx = smpsLoader.loadSfx(sfxName);
            if (sfx != null) {
                recordTimelineCommand(new AudioCommand.PlaySfx(
                        -1, sfxName, AudioCommand.SfxRoute.BASE_SMPS_NAME, pitch, null));
                if (sendLiveBackendCommands()) {
                    backend.playSfxSmps(sfx, dacData, pitch);
                }
                return;
            }
        }
        recordTimelineCommand(new AudioCommand.PlaySfx(
                -1, sfxName, AudioCommand.SfxRoute.FALLBACK_NAME, pitch, null));
        if (sendLiveBackendCommands()) {
            backend.playSfx(sfxName, pitch);
        }
    }

    public void playSfx(GameSound sound) {
        playSfx(sound, 1.0f);
    }

    public void playSfx(GameSound sound, float pitch) {
        if (suppressingRewindReplay()) {
            return;
        }
        if (sound == GameSound.RING) {
            playSfx(ringLeft ? GameSound.RING_LEFT : GameSound.RING_RIGHT, pitch);
            ringLeft = !ringLeft;
            return;
        }

        float effectivePitch = audioProfile != null ? audioProfile.adjustSfxPitch(sound, pitch) : pitch;
        boolean played = false;
        if (soundMap != null && soundMap.containsKey(sound)) {
            played = playSfx(soundMap.get(sound), effectivePitch);
        }
        if (!played) {
            DonorSfxBinding binding = donorSoundBindings.get(sound);
            if (binding != null) {
                SmpsLoader loader = donorLoaders.get(binding.gameId());
                DacData dData = donorDacData.get(binding.gameId());
                if (loader != null && dData != null) {
                    AbstractSmpsData sfx = loader.loadSfx(binding.sfxId());
                    if (sfx != null) {
                        recordTimelineCommand(new AudioCommand.PlaySfx(
                                binding.sfxId(),
                                sound.name(),
                                AudioCommand.SfxRoute.DONOR_SMPS,
                                effectivePitch,
                                binding.gameId()));
                        SmpsSequencerConfig donorConfig = donorConfigs.get(binding.gameId());
                        if (sendLiveBackendCommands()) {
                            if (donorConfig != null) {
                                backend.playSfxSmps(sfx, dData, effectivePitch, donorConfig);
                            } else {
                                backend.playSfxSmps(sfx, dData, effectivePitch);
                            }
                        }
                        played = true;
                    }
                }
            }
        }
        if (!played) {
            AudioCommand.SfxRoute route = sound == GameSound.RING_LEFT || sound == GameSound.RING_RIGHT
                    ? AudioCommand.SfxRoute.RING_RESOLVED
                    : AudioCommand.SfxRoute.FALLBACK_NAME;
            recordTimelineCommand(new AudioCommand.PlaySfx(
                    -1, sound.name(), route, effectivePitch, null));
            if (sendLiveBackendCommands()) {
                backend.playSfx(sound.name(), effectivePitch);
            }
        }
    }

    public boolean playSfx(int sfxId) {
        return playSfx(sfxId, 1.0f);
    }

    public boolean playSfx(int sfxId, float pitch) {
        if (suppressingRewindReplay()) {
            return false;
        }
        if (smpsLoader != null) {
            AbstractSmpsData sfx = smpsLoader.loadSfx(sfxId);
            if (sfx != null) {
                recordTimelineCommand(new AudioCommand.PlaySfx(
                        sfxId, null, AudioCommand.SfxRoute.BASE_SMPS_ID, pitch, null));
                if (sendLiveBackendCommands()) {
                    backend.playSfxSmps(sfx, dacData, pitch);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Plays an SFX from a donor game's SMPS loader with the donor's sequencer config.
     * Used for cross-game SFX that aren't in the base game's sound map (e.g., S3K
     * Super Sonic transformation sound).
     *
     * @param donorGameId the donor game identifier (e.g., "s3k")
     * @param sfxId the SFX ID in the donor game's format
     */
    public void playDonorSfx(String donorGameId, int sfxId) {
        if (suppressingRewindReplay()) {
            return;
        }
        SmpsLoader loader = donorLoaders.get(donorGameId);
        DacData dData = donorDacData.get(donorGameId);
        if (loader != null && dData != null) {
            AbstractSmpsData sfx = loader.loadSfx(sfxId);
            if (sfx != null) {
                recordTimelineCommand(new AudioCommand.PlaySfx(
                        sfxId, null, AudioCommand.SfxRoute.DONOR_SMPS, 1.0f, donorGameId));
                SmpsSequencerConfig config = donorConfigs.get(donorGameId);
                if (sendLiveBackendCommands()) {
                    if (config != null) {
                        backend.playSfxSmps(sfx, dData, 1.0f, config);
                    } else {
                        backend.playSfxSmps(sfx, dData, 1.0f);
                    }
                }
            }
        }
    }

    public void update() {
        if (presentationSink instanceof OpenAlPcmSink openAlSink) {
            openAlSink.updateDevice();
        }
        finishOuterAudioFrame();
    }

    /** Transitional delegate retained until Task 10 removes simulation pumps. */
    public void updateLegacyDevice() {
        finishOuterAudioFrame();
    }

    private void finishOuterAudioFrame() {
        if (!audioFrameOwnedExternally) {
            commandTimeline.beginFrame(commandTimeline.currentFrame() + 1);
        }
        audioFrameOwnedExternally = false;
        audioFrameAdvanced = false;
    }

    /**
     * Plays music from a donor game's SMPS loader with the donor's sequencer config.
     * Used for cross-game Super Sonic music (e.g., S3K invincibility in an S2 base game).
     *
     * @param donorGameId the donor game identifier (e.g., "s3k")
     * @param musicId the music ID in the donor game's format
     */
    public void playDonorMusic(String donorGameId, int musicId) {
        if (suppressingRewindReplay()) {
            return;
        }
        SmpsLoader loader = donorLoaders.get(donorGameId);
        DacData dData = donorDacData.get(donorGameId);
        if (loader != null && dData != null) {
            AbstractSmpsData data = loader.loadMusic(musicId);
            if (data != null) {
                recordTimelineCommand(new AudioCommand.PlayMusic(
                        musicId, AudioCommand.MusicRoute.DONOR_SMPS, true, donorGameId));
                SmpsSequencerConfig config = donorConfigs.get(donorGameId);
                // forceOverride=true: the base game's audioProfile won't recognize
                // donor music IDs, so force the override path to push zone music
                // onto the stack for restoration when Super Sonic ends.
                if (sendLiveBackendCommands()) {
                    backend.prepareLogicalMusicSource(AudioSourceDescriptor.donorMusic(donorGameId, musicId));
                    backend.playSmps(data, dData, config, true);
                }
            }
        }
    }

    public boolean playDonorMusic(String donorGameId, GameMusic music) {
        Integer musicId = resolveDonorMusic(donorGameId, music);
        if (musicId == null) {
            return false;
        }
        playDonorMusic(donorGameId, musicId);
        return true;
    }

    public void endMusicOverride(int musicId) {
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.EndMusicOverride(musicId));
        if (sendLiveBackendCommands()) {
            backend.endMusicOverride(musicId);
        }
    }

    public boolean endMusicOverride(GameMusic music) {
        if (music == null || audioProfile == null) {
            return false;
        }
        Integer musicId = audioProfile.getMusicMap().get(music);
        if (musicId == null) {
            return false;
        }
        endMusicOverride(musicId);
        return true;
    }

    public boolean endDonorMusicOverride(String donorGameId, GameMusic music) {
        Integer musicId = resolveDonorMusic(donorGameId, music);
        if (musicId == null) {
            return false;
        }
        endMusicOverride(musicId);
        return true;
    }

    private Integer resolveDonorMusic(String donorGameId, GameMusic music) {
        if (donorGameId == null || music == null) {
            return null;
        }
        Map<GameMusic, Integer> musicMap = donorMusicBindings.get(donorGameId);
        if (musicMap == null) {
            return null;
        }
        return musicMap.get(music);
    }

    /**
     * Change the music dividing timing (tempo).
     * ROM: Change_Music_Tempo. Lower values = faster playback.
     *
     * @param newDividingTiming the new dividing timing value
     */
    public void changeMusicTempo(int newDividingTiming) {
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.ChangeMusicTempo(newDividingTiming));
        if (sendLiveBackendCommands()) {
            backend.changeMusicTempo(newDividingTiming);
        }
    }

    /**
     * Stops all playing SFX without affecting music.
     * Clears both SFX sequencers in the active music driver and the standalone SFX stream.
     */
    public void stopAllSfx() {
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.StopAllSfx());
        if (sendLiveBackendCommands()) {
            backend.stopAllSfx();
        }
    }

    /**
     * Stops all music and sound playback.
     * Used when exiting special stages or changing game modes.
     */
    public void stopMusic() {
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.StopMusic());
        if (sendLiveBackendCommands()) {
            backend.stopPlayback();
        }
    }

    /**
     * Fade out the currently playing music using ROM default timing.
     * ROM equivalent: MusID_FadeOut (0xF9) / zFadeOutMusic.
     * Does not affect SFX - only music channels fade.
     *
     * <p>ROM uses fadeOutMusic() in these situations (for future implementation):
     * <ul>
     *   <li>Special stage entry (s2.asm:6540) - IMPLEMENTED</li>
     *   <li>Special stage checkpoint fail (Obj5A, s2.asm:71358, 71878) - IMPLEMENTED</li>
     *   <li>Level entry - before entering a level with title card (s2.asm:4757) - IMPLEMENTED</li>
     *   <li>Boss area triggers - when approaching end-of-act boss fights
     *       (EHZ:20404, MTZ:20512, HTZ:21230, HPZ:21332, ARZ:21421, MCZ:21529, OOZ:21613, CNZ:21760)</li>
     *   <li>Title screen - starting new game (s2.asm:4526)</li>
     *   <li>Demo playback - before playing a demo (s2.asm:4581)</li>
     *   <li>WFZ/DEZ boss setup (s2.asm:77011, 80751)</li>
     *   <li>Ending sequence - final boss defeated, going to credits (s2.asm:82064, 82525)</li>
     * </ul>
     */
    /**
     * Monotonic count of music fade-out requests actually issued (excludes
     * rewind-replay-suppressed calls). Lets out-of-engine observers — e.g. the
     * trace video-capture tool — detect a music fade such as the AIZ2 end-boss
     * fade in {@code AizEndBossInstance} without inspecting object state.
     */
    private long fadeOutMusicCount;

    /** @return the monotonic {@link #fadeOutMusic(int, int)} request count. */
    public long musicFadeOutCount() {
        return fadeOutMusicCount;
    }

    public void fadeOutMusic() {
        // ROM default: 0x28 (40) steps, delay of 3 frames between steps
        fadeOutMusic(0x28, 3);
    }

    /**
     * Fade out the currently playing music over time.
     * ROM equivalent: MusID_FadeOut (0xF9) / zFadeOutMusic.
     * Does not affect SFX - only music channels fade.
     *
     * @param steps total number of volume steps (ROM default: 0x28 = 40)
     * @param delay frames between each volume step (ROM default: 3)
     */
    public void fadeOutMusic(int steps, int delay) {
        if (suppressingRewindReplay()) {
            return;
        }
        fadeOutMusicCount++;
        recordTimelineCommand(new AudioCommand.FadeOutMusic(steps, delay));
        if (sendLiveBackendCommands()) {
            backend.fadeOutMusic(steps, delay);
        }
    }

    /**
     * Registers a donor SmpsLoader and DacData for cross-game SFX playback.
     */
    public void registerDonorLoader(String gameId, SmpsLoader loader, DacData dacData) {
        clearRestoreSmpsResolveCache();
        donorLoaders.put(gameId, loader);
        this.donorDacData.put(gameId, dacData);
    }

    /**
     * Registers a donor SmpsLoader, DacData, and SmpsSequencerConfig for cross-game SFX playback.
     * The config will be passed to the backend so the donor SFX uses the correct driver settings.
     */
    public void registerDonorLoader(String gameId, SmpsLoader loader, DacData dacData,
                                    SmpsSequencerConfig config) {
        registerDonorLoader(gameId, loader, dacData, config, null);
    }

    public void registerDonorLoader(String gameId, SmpsLoader loader,
                                    DacData dacData,
                                    SmpsSequencerConfig config,
                                    GameAudioProfile donorProfile) {
        clearRestoreSmpsResolveCache();
        donorLoaders.put(gameId, loader);
        this.donorDacData.put(gameId, dacData);
        if (config != null) {
            donorConfigs.put(gameId, config);
        }
        if (donorProfile != null) {
            donorProfiles.put(gameId, donorProfile);
            if (backend != null) {
                backend.registerAudioProfileCoordHandlers(donorProfile);
            }
        }
        configurePresentationCoordHandlers(donorProfile);
    }

    public void registerDonorMusicMap(String gameId, Map<GameMusic, Integer> musicMap) {
        if (gameId == null || musicMap == null || musicMap.isEmpty()) {
            return;
        }
        donorMusicBindings.put(gameId, Map.copyOf(musicMap));
    }

    /**
     * Registers a donor sound binding so that a GameSound missing from the
     * base game's sound map will be routed through the specified donor loader.
     */
    public void registerDonorSound(GameSound sound, String gameId, int sfxId) {
        donorSoundBindings.put(sound, new DonorSfxBinding(gameId, sfxId));
    }

    /**
     * Clears all donor audio state (loaders, DAC data, and sound bindings).
     */
    public void clearDonorAudio() {
        clearRestoreSmpsResolveCache();
        donorLoaders.clear();
        donorDacData.clear();
        donorConfigs.clear();
        donorProfiles.clear();
        donorSoundBindings.clear();
        donorMusicBindings.clear();
    }

    /**
     * Resets mutable state without destroying the singleton instance.
     * Used by TestEnvironment to prevent state leaking between tests
     * (e.g. Sonic 1 SMPS loader contaminating Sonic 2 tests).
     */
    public void resetState() {
        discardDeferredBackendRestore();
        if (backend != null) {
            backend.stopPlayback();
        }
        this.smpsLoader = null;
        this.dacData = null;
        this.rom = null;
        this.soundMap = null;
        this.audioProfile = null;
        this.ringLeft = true;
        this.rewindReplaySuppressionDepth = 0;
        this.audioFrameOwnedExternally = false;
        this.audioFrameAdvanced = false;
        this.reverseAudioPresentationActive = false;
        this.deferredReverseLogicalSnapshot = null;
        this.deferredReverseLogicalPrepared = false;
        this.deferredBackendRestore = null;
        this.postBoundaryReverseTarget = false;
        this.deterministicAudioRuntime.clearSubmittedCommands();
        this.deterministicAudioRuntime.clearPcmHistory();
        this.commandTimeline.clear();
        closeShadowPresentation();
        clearDonorAudio();
        this.deterministicRuntimeExplicitlyConfigured = false;
        configureDeterministicRuntimeForBackend();
    }

    private AudioTimelineEntry recordTimelineCommand(AudioCommand command) {
        if (!suppressingRewindReplay()) {
            AudioTimelineEntry entry = commandTimeline.record(command);
            try {
                deterministicAudioRuntime.submit(entry);
            } catch (RuntimeException failure) {
                LOGGER.log(Level.WARNING,
                        "Deterministic audio command mirror failed", failure);
            }
            mirrorShadowCommand(() -> shadowResolver.submit(command));
            return entry;
        }
        return null;
    }

    private void mirrorShadowCommand(Runnable submission) {
        try {
            ensureShadowPresentation();
            submission.run();
            shadowParity.commandSubmitted();
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING,
                    "Presentation shadow command mirror failed", failure);
        }
    }

    private boolean sendLiveBackendCommands() {
        return false;
    }

    public void toggleMute(ChannelType type, int channel) {
        mirrorShadowCommand(() -> shadowCommands.submit(
                new AudioPresentationCommand.ToggleMute(type, channel),
                () -> true, shadowRegistry::apply));
    }

    public void toggleSolo(ChannelType type, int channel) {
        mirrorShadowCommand(() -> shadowCommands.submit(
                new AudioPresentationCommand.ToggleSolo(type, channel),
                () -> true, shadowRegistry::apply));
    }

    public boolean isMuted(ChannelType type, int channel) {
        ensureShadowPresentation();
        return shadowRegistry.isMuted(type, channel);
    }

    public boolean isSoloed(ChannelType type, int channel) {
        ensureShadowPresentation();
        return shadowRegistry.isSoloed(type, channel);
    }

    private void ensureShadowPresentation() {
        if (shadowProducer != null) {
            return;
        }
        int sampleRate = Math.max(1, presentationSink != null
                ? presentationSink.sampleRate()
                : backend != null ? backend.outputSampleRate() : 48_000);
        int frameRate = configuredFrameRate();
        int maxFrames = (sampleRate + frameRate - 1) / frameRate;
        AudioPresentationTuning tuning = standaloneTuning != null
                ? standaloneTuning : backend != null
                ? backend.presentationTuning()
                : AudioPresentationTuning.DEFAULT;
        shadowTuning = tuning;
        if (presentationCoordFlagHandlers == null) {
            presentationCoordFlagHandlers =
                    new SmpsCoordFlagHandlerOwner(
                            new SmpsCoordFlagRuntimeState());
            presentationCoordHandlerGameIds.clear();
            configurePresentationCoordHandlers(audioProfile);
            donorProfiles.values().forEach(
                    this::configurePresentationCoordHandlers);
        }
        AudioPresentationSourceFactory.Settings settings =
                new AudioPresentationSourceFactory.Settings(sampleRate,
                        tuning.region(), tuning.dacInterpolate(),
                        tuning.psgNoiseShiftEveryToggle(), tuning.fm6DacOff(),
                        false, 1, this::restoreShadowMusic,
                        new DecodedPcmCache(),
                        AudioManager.class.getClassLoader()::getResourceAsStream);
        shadowFactory = new AudioPresentationSourceFactory(
                () -> true, presentationCoordFlagHandlers, settings);
        shadowCommands = new AudioPresentationCommandQueue();
        shadowRegistry = new AudioVoiceRegistry(shadowFactory, shadowFactory,
                presentationCoordFlagHandlers,
                warning -> LOGGER.warning("Presentation shadow: " + warning));
        shadowResolver = new AudioPresentationCommandResolver(shadowCommands,
                shadowFactory, new ShadowSources(maxFrames),
                warning -> LOGGER.warning("Presentation shadow: " + warning),
                () -> true, shadowRegistry::apply);
        AudioPresentationMixer mixer = new AudioPresentationMixer(maxFrames);
        AudioPresentationSink sink = presentationSink != null
                ? presentationSink : new NoDeviceAudioSink(sampleRate);
        if (sink.sampleRate() != sampleRate) {
            sink.close();
            sink = new NoDeviceAudioSink(sampleRate);
            presentationSink = sink;
        }
        shadowProducer = new AudioPresentationProducer(sampleRate, frameRate,
                Math.max(maxFrames, configuredPcmHistoryFrames(sampleRate)),
                Math.max(1, sampleRate * REVERSE_RELEASE_CROSSFADE_MS / 1000),
                shadowRegistry, shadowCommands, mixer,
                sink);
        shadowParity = new AudioPresentationParityProbe(sampleRate, frameRate);
    }

    private void restoreShadowMusic() {
        shadowRestoreRequested = true;
    }

    private void closeShadowPresentation() {
        // Closing the producer closes every lease attached to it, so the
        // offline compatibility lease must never outlive its producer.
        offlineCaptureHandle = null;
        if (shadowProducer != null) {
            shadowProducer.close();
        } else if (presentationSink != null) {
            presentationSink.close();
        }
        presentationSink = null;
        shadowProducer = null;
        shadowResolver = null;
        shadowRegistry = null;
        shadowCommands = null;
        shadowFactory = null;
        shadowParity = null;
        shadowTuning = null;
        standaloneTuning = null;
        shadowRestoreRequested = false;
        presentationCoordFlagHandlers = null;
        presentationCoordHandlerGameIds.clear();
    }

    private void configurePresentationCoordHandlers(GameAudioProfile profile) {
        if (profile != null && presentationCoordFlagHandlers != null
                && presentationCoordHandlerGameIds.add(
                        profile.presentationGameId())) {
            profile.configurePresentationCoordFlagHandlers(
                    presentationCoordFlagHandlers);
        }
    }

    private final class ShadowSources implements AudioPresentationCommandResolver.Sources {
        private final int maxFrames;

        private ShadowSources(int maxFrames) {
            this.maxFrames = maxFrames;
        }

        @Override public String baseGameId() {
            try {
                return audioProfile != null
                        ? audioProfile.presentationGameId() : "base";
            } catch (RuntimeException unavailable) { return "base"; }
        }
        @Override public AbstractSmpsData loadBaseMusic(int id) {
            return smpsLoader != null ? smpsLoader.loadMusic(id) : null;
        }
        @Override public AbstractSmpsData loadDonorMusic(String gameId, int id) {
            SmpsLoader loader = donorLoaders.get(gameId);
            return loader != null ? loader.loadMusic(id) : null;
        }
        @Override public AbstractSmpsData loadBaseSfx(int id) {
            return smpsLoader != null ? smpsLoader.loadSfx(id) : null;
        }
        @Override public AbstractSmpsData loadBaseSfx(String name) {
            return smpsLoader != null ? smpsLoader.loadSfx(name) : null;
        }
        @Override public AbstractSmpsData loadDonorSfx(String gameId, int id) {
            SmpsLoader loader = donorLoaders.get(gameId);
            return loader != null ? loader.loadSfx(id) : null;
        }
        @Override public DacData dacFor(String gameId) {
            return gameId.equals(baseGameId()) ? dacData : donorDacData.get(gameId);
        }
        @Override public SmpsSequencerConfig configFor(String gameId) {
            return gameId.equals(baseGameId())
                    ? (audioProfile != null ? audioProfile.getSequencerConfig() : null)
                    : donorConfigs.get(gameId);
        }
        @Override public int sfxPriority(String gameId, int id) {
            return gameId.equals(baseGameId()) && audioProfile != null
                    ? audioProfile.getSfxPriority(id) : 0x70;
        }
        @Override public boolean specialSfx(String gameId, int id) {
            return gameId.equals(baseGameId()) && audioProfile != null
                    && audioProfile.isSpecialSfx(id);
        }
        @Override public boolean continuousSfx(String gameId, int id) {
            return gameId.equals(baseGameId()) && audioProfile != null
                    && audioProfile.isContinuousSfx(id);
        }
        @Override public int maxStereoFrames() {
            return maxFrames;
        }
    }

    private final class ManagerLiveCaptureAudioHandle implements LiveCaptureAudioHandle {
        private final LiveCaptureAudioHandle capture;
        private boolean closed;

        private ManagerLiveCaptureAudioHandle(LiveCaptureAudioHandle capture) {
            this.capture = capture;
        }

        @Override
        public int sampleRate() {
            return capture.sampleRate();
        }

        @Override
        public int frameRate() {
            return capture.frameRate();
        }

        @Override
        public int maxStereoFramesPerPacket() {
            return capture.maxStereoFramesPerPacket();
        }

        @Override
        public int drainPresentationFrame(short[] target) {
            synchronized (AudioManager.this) {
                if (closed) {
                    throw new IllegalStateException("Live capture audio handle is no longer attached");
                }
                return capture.drainPresentationFrame(target);
            }
        }

        @Override
        public long totalStereoFrames() {
            synchronized (AudioManager.this) {
                return capture.totalStereoFrames();
            }
        }

        @Override
        public AudioFrameClock.Snapshot clockSnapshot() {
            synchronized (AudioManager.this) {
                return capture.clockSnapshot();
            }
        }

        @Override
        public void close() {
            closeLiveCaptureAudio(this);
        }
    }

    public void destroy() {
        discardDeferredBackendRestore();
        closeShadowPresentation();
        if (backend != null) {
            backend.destroy();
        }
    }

    /**
     * Pauses audio playback. Called when the game window is minimized or loses focus.
     */
    public void pause() {
        if (presentationSink instanceof OpenAlPcmSink openAlSink) {
            openAlSink.pause();
        }
    }

    /**
     * Resumes audio playback after being paused.
     */
    public void resume() {
        if (presentationSink instanceof OpenAlPcmSink openAlSink) {
            openAlSink.resume();
        }
    }
}
