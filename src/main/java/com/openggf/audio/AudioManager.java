package com.openggf.audio;

import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioCommandTimeline;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.rewind.AudioPresentationPolicy;
import com.openggf.audio.rewind.AudioReplayReason;
import com.openggf.audio.rewind.AudioReplayScope;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.AudioTimelineEntry;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.runtime.PcmHistoryRing;
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
import com.openggf.audio.presentation.SmpsAssetKey;
import com.openggf.audio.presentation.SmpsCompositeVoice;
import com.openggf.audio.output.NoDeviceAudioSink;
import com.openggf.audio.output.AudioPresentationSink;
import com.openggf.audio.output.OpenAlPcmSink;
import com.openggf.configuration.FrameRateResolver;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AudioManager implements MusicRestoreSink {
    private static final Logger LOGGER = Logger.getLogger(AudioManager.class.getName());
    private static final int PCM_HISTORY_SECONDS = 60;
    private static final int REVERSE_RELEASE_CROSSFADE_MS = 45;
    private static AudioManager instance;
    private AudioBackend backend;
    /** Volatile publication of the complete immutable base dependency tuple. */
    private volatile BaseAudioSource baseAudioSource =
            new BaseAudioSource(null, null, null, null, null, 0);
    private Map<GameSound, Integer> soundMap;
    private boolean ringLeft = true;
    private int rewindReplaySuppressionDepth;
    private final AudioCommandTimeline commandTimeline = new AudioCommandTimeline();
    /**
     * Single offline-capture compatibility lease over the authoritative
     * producer. It is an ordinary non-consuming capture handle: it never
     * replaces the producer, registry, or sink.
     */
    private LiveCaptureAudioHandle offlineCaptureHandle;
    private ManagerLiveCaptureAudioHandle activeLiveCaptureAudioHandle;
    private boolean liveCaptureAwaitingRebind;
    private boolean audioFrameOwnedExternally;
    private boolean audioFrameAdvanced;
    private boolean reverseAudioPresentationActive;
    /** Single selected restore, committed only at reverse release. */
    private AudioLogicalSnapshot deferredReverseLogicalSnapshot;
    private boolean deferredReverseLogicalPrepared;
    /** True once a boundary has replaced the stale rewind target with fresh live state. */
    private boolean postBoundaryReverseTarget;
    /**
     * Count of logical audio restores actually published to the presentation
     * producer. Held rewind defers per-frame restores and publishes exactly one
     * at release; this counter is the observable for that contract now that the
     * legacy backend restore (which tests counted) is gone.
     */
    private int logicalRestorePublications;
    /**
     * Single-shot release-failure injection for tests. The producer is now the
     * only restore owner, so there is no second (backend) publication step a
     * test double could fail; this hook keeps the "a failed release is retained
     * and retried" contract testable from production hosts.
     *
     * <p>Null means "inject nothing". The point selects which side of the one
     * irreversible step in {@link #endReverseAudioPresentation()} the failure
     * lands on — the two sides carry different contracts, so a single boolean
     * could only ever exercise one of them.
     */
    private ReverseReleaseFailurePoint failNextReverseRelease;

    /**
     * Where an injected reverse-release failure is raised, relative to
     * {@code shadowProducer.endReverse(...)} — the single irreversible step of
     * the release (it consumes the reverse PCM cursor and commits the prepared
     * registry restore).
     */
    enum ReverseReleaseFailurePoint {
        /**
         * Before the producer commit. Nothing observable has been mutated by
         * this attempt, so the release must be exactly retryable.
         */
        BEFORE_PRODUCER_COMMIT,
        /**
         * After the producer commit, while the manager-local ledger is being
         * published. The reverse session no longer exists and cannot be
         * recreated, so the release must complete rather than report a
         * retryable failure.
         */
        AFTER_PRODUCER_COMMIT
    }
    private AudioPresentationCommandQueue shadowCommands;
    private AudioPresentationSourceFactory shadowFactory;
    private AudioPresentationCommandResolver shadowResolver;
    private AudioVoiceRegistry shadowRegistry;
    private AudioPresentationProducer shadowProducer;
    /**
     * Frame rate the live {@link #shadowProducer} was constructed with. The
     * producer owns the presentation clock, so this is the only rate at which
     * a packet is presented; capture leases must be clocked to match it or
     * every packet is truncated (lease slower) or zero-padded (lease faster).
     */
    private int shadowFrameRate;
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
    /**
     * Volatile immutable snapshots keep readers lock-free while donor
     * mutators publish a whole route table at one visibility boundary.
     */
    private volatile Map<String, DonorAudioSource> donorAudioSources =
            Map.of();
    private volatile Map<String, Long> donorGenerationCounters = Map.of();
    /**
     * Guarded by this manager's monitor. Java monitors are reentrant, so the
     * synchronized source mutators also need an explicit same-thread guard to
     * keep dependency callbacks from starting a nested publication.
     */
    private boolean sourceMutationInProgress;
    private final Map<GameSound, DonorSfxBinding> donorSoundBindings = new EnumMap<>(GameSound.class);
    private final Map<String, Map<GameMusic, Integer>> donorMusicBindings = new HashMap<>();

    private record DonorSfxBinding(String gameId, int sfxId) {}

    private record BaseAudioSource(
            Rom rom,
            GameAudioProfile profile,
            SmpsLoader loader,
            DacData dac,
            SmpsSequencerConfig config,
            long generation) {
    }

    private record DonorAudioSource(
            SmpsLoader loader,
            DacData dac,
            SmpsSequencerConfig config,
            GameAudioProfile profile,
            long generation) {
    }

    private record PresentationCoordHandlerPreparation(
            SmpsCoordFlagHandlerOwner owner,
            Set<String> gameIds,
            boolean publishOwner,
            boolean configureCandidate,
            String candidateGameId) {
    }

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
        GameAudioProfile resolvedProfile = java.util.Objects.requireNonNull(
                profile, "profile");
        manager.baseAudioSource = new BaseAudioSource(
                null, resolvedProfile, null, null,
                resolvedProfile.getSequencerConfig(), 1);
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

    public synchronized LiveCaptureAudioHandle beginLiveCaptureAudio(int frameRate) {
        if (activeLiveCaptureAudioHandle != null) {
            throw new IllegalStateException("A live capture audio handle is already attached");
        }
        ensureShadowPresentation();
        LiveCaptureAudioHandle capture = shadowProducer.attachCapture(frameRate);
        ManagerLiveCaptureAudioHandle handle =
                new ManagerLiveCaptureAudioHandle(capture, frameRate);
        activeLiveCaptureAudioHandle = handle;
        return handle;
    }

    /**
     * Idempotently closes the live-recording lease. Identical rule to
     * {@link #endCaptureMode()}: the manager's view of the lease is retired
     * only after the producer has accepted the detach.
     *
     * <p>A capture lease can only be released on the producer's owner thread,
     * so an off-thread close throws with the lease still attached. Marking the
     * handle closed or clearing {@code activeLiveCaptureAudioHandle} first
     * would leave this manager believing it holds no lease while the producer
     * keeps copying every presented packet into the orphan, would let the next
     * {@code beginLiveCaptureAudio} attach a second lease instead of rejecting
     * it, and would make a retry from the owner thread a no-op so the orphan
     * could never be detached at all.
     */
    private synchronized void closeLiveCaptureAudio(ManagerLiveCaptureAudioHandle handle) {
        if (handle.closed) {
            return;
        }
        handle.capture.close();
        handle.closed = true;
        if (activeLiveCaptureAudioHandle == handle) {
            activeLiveCaptureAudioHandle = null;
        }
    }

    /**
     * Offline-capture compatibility entry point. Attaches exactly one
     * non-consuming capture lease to the already-authoritative presentation
     * producer, so headless capture renders the same final SMPS/WAV/raw-PCM
     * packets the speaker path renders. It never replaces the
     * producer/registry/sink, and never opens an audio device.
     *
     * <p>{@code sampleRate} must equal the producer's rate: the producer owns
     * the clock, history, and every voice cursor, so a rate change after
     * sources have been admitted is rejected rather than migrated. Callers
     * that need a different rate must initialize the headless producer at that
     * rate (via its backend/sink) before admitting sources.
     *
     * <p>{@code frameRate} must likewise equal the producer's frame rate. The
     * producer presents one packet of {@code sampleRate / producerFrameRate}
     * stereo frames per outer frame, while the lease clock decides how many
     * frames each drain asks for: a slower lease permanently discards the
     * tail of every packet and a faster one zero-pads it, in both cases
     * silently. Callers that need a different capture frame rate must realize
     * the producer at that rate first (see {@link #presentationFrameRate()}).
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
        if (frameRate != shadowFrameRate) {
            throw new IllegalArgumentException(
                    "offline capture frame rate " + frameRate
                            + " does not match the presentation producer frame"
                            + " rate " + shadowFrameRate
                            + "; a mismatched lease would truncate or zero-pad"
                            + " every presented packet");
        }
        offlineCaptureHandle = shadowProducer.attachCapture(frameRate);
    }

    /**
     * The frame rate the authoritative presentation producer is clocked at,
     * realizing it if necessary. Offline capture callers must clock both their
     * lease and their container at this rate: it is the rate at which packets
     * are actually presented, and it can differ from a requested capture frame
     * rate (for example a PAL region pins the engine to 50 fps).
     */
    public synchronized int presentationFrameRate() {
        ensureShadowPresentation();
        return shadowFrameRate;
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
     *
     * <p>The handle reference is dropped only after the producer has accepted
     * the detach. A capture lease can only be released on the producer's owner
     * thread, so an off-thread call throws with the lease still attached;
     * clearing the field first would leave this manager believing it holds no
     * lease while the producer keeps feeding the orphaned one, and would let
     * the next {@code beginCaptureMode} attach a second lease instead of
     * rejecting it.
     */
    public synchronized void endCaptureMode() {
        LiveCaptureAudioHandle handle = offlineCaptureHandle;
        if (handle == null) {
            return;
        }
        handle.close();
        offlineCaptureHandle = null;
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
        clearPreparedReverseRestore();
        // A backend swap replaces the output device; it is not a reason to end
        // a recording of what the engine is playing.
        detachLiveCaptureAudioHandleForRebuild();
        closeShadowPresentation();
        destroyBackendQuietly(this.backend, "previous AudioBackend");
        this.backend = backend;
        try {
            this.backend.init();
            this.backend.setAudioProfile(baseAudioSource.profile());
            installBackendPresentationSink();
            LOGGER.info("AudioBackend initialized: " + backend.getClass().getSimpleName());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize AudioBackend", e);
            destroyBackendQuietly(this.backend, "failed AudioBackend");
            this.backend = new NullAudioBackend();
            this.backend.init();
            this.backend.setAudioProfile(baseAudioSource.profile());
            presentationSink =
                    new NoDeviceAudioSink(this.backend.outputSampleRate());
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

    /**
     * Reinstalls the backend-owned speaker sink after a mode reset. A reset
     * intentionally closes the presentation producer and its sink while
     * retaining the backend instance; the next title/game mode must ask the
     * backend for a fresh sink before the producer is rebuilt.
     */
    public void ensurePresentationSink() {
        if (backend != null && presentationSink == null) {
            installBackendPresentationSink();
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

    public synchronized void setAudioProfile(GameAudioProfile audioProfile) {
        beginSourceMutation();
        try {
            BaseAudioSource previous = baseAudioSource;
            long candidateGeneration = nextGeneration(previous.generation());
            SmpsLoader candidateLoader = createLoader(
                    audioProfile, previous.rom());
            DacData candidateDac = loadDac(candidateLoader);
            SmpsSequencerConfig candidateConfig = audioProfile != null
                    ? audioProfile.getSequencerConfig() : null;
            PresentationCoordHandlerPreparation presentationPreparation =
                    preparePresentationCoordHandlers(audioProfile, true);
            boolean[] backendConfigurationAttempted = {false};
            Runnable backendConfiguration = () -> {
                if (backend != null) {
                    backendConfigurationAttempted[0] = true;
                    backend.setAudioProfile(audioProfile);
                }
            };
            try {
                if (presentationPreparation.configureCandidate()) {
                    presentationPreparation.owner().configureTransactionally(
                            owner -> audioProfile
                                    .configurePresentationCoordFlagHandlers(owner),
                            backendConfiguration);
                } else {
                    backendConfiguration.run();
                }
            } catch (RuntimeException | Error failure) {
                if (backendConfigurationAttempted[0]) {
                    try {
                        backend.setAudioProfile(previous.profile());
                    } catch (RuntimeException | Error restoreFailure) {
                        failure.addSuppressed(restoreFailure);
                    }
                }
                throw failure;
            }
            publishPresentationCoordHandlers(presentationPreparation);
            baseAudioSource = new BaseAudioSource(
                    previous.rom(), audioProfile, candidateLoader, candidateDac,
                    candidateConfig, candidateGeneration);
        } finally {
            endSourceMutation();
        }
    }

    public GameAudioProfile getAudioProfile() {
        return baseAudioSource.profile();
    }

    public synchronized void setRom(Rom rom) {
        beginSourceMutation();
        try {
            BaseAudioSource previous = baseAudioSource;
            SmpsLoader candidateLoader = createLoader(previous.profile(), rom);
            DacData candidateDac = loadDac(candidateLoader);
            baseAudioSource = new BaseAudioSource(
                    rom, previous.profile(), candidateLoader, candidateDac,
                    previous.config(),
                    nextGeneration(previous.generation()));
        } finally {
            endSourceMutation();
        }
    }

    private static SmpsLoader createLoader(
            GameAudioProfile profile, Rom rom) {
        return profile != null && rom != null
                ? profile.createSmpsLoader(rom) : null;
    }

    private static DacData loadDac(SmpsLoader loader) {
        return loader != null ? loader.loadDacData() : null;
    }

    private static long nextGeneration(long generation) {
        return Math.incrementExact(generation);
    }

    private void beginSourceMutation() {
        if (sourceMutationInProgress) {
            throw new IllegalStateException(
                    "Audio source mutation must not be re-entered");
        }
        sourceMutationInProgress = true;
    }

    private void endSourceMutation() {
        sourceMutationInProgress = false;
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
        donorGameIds.addAll(donorAudioSources.keySet());

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
        AudioPresentationSnapshot previousPresentation =
                shadowProducer.snapshot();
        boolean previousRingLeft = ringLeft;
        long previousTimelineFrame = commandTimeline.currentFrame();
        int previousTimelineOrder = commandTimeline.nextOrder();
        Map<GameSound, DonorSfxBinding> previousBindings =
                new EnumMap<>(donorSoundBindings);
        try {
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
            logicalRestorePublications++;
            return true;
        } catch (RuntimeException failure) {
            ringLeft = previousRingLeft;
            commandTimeline.restoreCursor(previousTimelineFrame,
                    previousTimelineOrder);
            donorSoundBindings.clear();
            donorSoundBindings.putAll(previousBindings);
            try {
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
        BaseAudioSource source = baseAudioSource;
        if (suppressingRewindReplay()
                || source.profile() == null || source.rom() == null) {
            return;
        }
        SegaPcmSpec spec = source.profile().getSegaPcmSpec();
        if (spec == null) {
            return;
        }
        try {
            byte[] pcm = source.rom().readBytes(
                    spec.address(), spec.length());
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
        BaseAudioSource base = baseAudioSource;
        int musicId = data.getId();
        int maxFrames = (outputSampleRate() + configuredFrameRate() - 1)
                / configuredFrameRate();
        var music = shadowFactory.musicSmps(
                standaloneGameId, musicId, standaloneVoiceId++,
                data, dac, base.config(),
                AudioSourceDescriptor.baseMusic(musicId), maxFrames);
        shadowCommands.submit(
                new AudioPresentationCommand.ReplaceMusic(music),
                () -> true, shadowRegistry::apply);
    }

    public void playStandaloneSfx(
            AbstractSmpsData data, DacData dac, float pitch) {
        ensureStandalonePresentation();
        BaseAudioSource base = baseAudioSource;
        GameAudioProfile profile = base.profile();
        int sfxId = data.getId();
        var key = new com.openggf.audio.presentation.SmpsAssetKey(
                standaloneGameId,
                com.openggf.audio.presentation.SmpsAssetKey.Route.BASE_ID,
                sfxId, null);
        shadowFactory.registerSmpsSfxAsset(
                key, base.generation(), data, dac, base.config(),
                profile.isSpecialSfx(sfxId));
        int continuous = profile.isContinuousSfx(sfxId)
                ? sfxId : 0;
        int maxFrames = (outputSampleRate() + configuredFrameRate() - 1)
                / configuredFrameRate();
        var source = shadowFactory.resolveSmpsSfx(
                standaloneVoiceId++, key, base.generation(),
                Math.max(1, Math.round(pitch * 65_536.0f)),
                profile.getSfxPriority(sfxId), continuous,
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
                ? (baseAudioSource.profile() != null
                ? baseAudioSource.profile().presentationGameId() : "base")
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
            boolean stagedManagerRingLeft =
                    managerRingAfter(selected.ringLeft(), command);
            deferredReverseLogicalSnapshot = new AudioLogicalSnapshot(
                    stagedManagerRingLeft,
                    selected.commandTimelineFrame(),
                    selected.commandTimelineNextOrder(),
                    selected.commandEntryCount(),
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
                selected.presentation(),
                selected.donorGameIds(),
                selected.donorBindings());
    }

    private void replayMusic(AudioCommand.PlayMusic command) {
        switch (command.route()) {
            case BASE_SMPS -> {
                BaseAudioSource source = baseAudioSource;
                if (source.loader() != null) {
                    AbstractSmpsData data = source.loader().loadMusic(
                            command.musicId());
                    if (data != null) {
                        backend.prepareLogicalMusicSource(AudioSourceDescriptor.baseMusic(command.musicId()));
                        backend.playSmps(data, source.dac());
                    }
                }
            }
            case DONOR_SMPS -> {
                DonorAudioSource source = donorAudioSources.get(
                        command.donorGameId());
                if (source != null) {
                    AbstractSmpsData data = source.loader().loadMusic(
                            command.musicId());
                    if (data != null) {
                        backend.prepareLogicalMusicSource(AudioSourceDescriptor.donorMusic(
                                command.donorGameId(), command.musicId()));
                        backend.playSmps(data, source.dac(),
                                source.config(), true);
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
                BaseAudioSource source = baseAudioSource;
                if (source.loader() != null) {
                    AbstractSmpsData sfx = source.loader().loadSfx(
                            command.sfxId());
                    if (sfx != null) {
                        backend.playSfxSmps(
                                sfx, source.dac(), command.pitch());
                    }
                }
            }
            case BASE_SMPS_NAME -> {
                BaseAudioSource source = baseAudioSource;
                if (source.loader() != null) {
                    AbstractSmpsData sfx = source.loader().loadSfx(
                            command.sfxName());
                    if (sfx != null) {
                        backend.playSfxSmps(
                                sfx, source.dac(), command.pitch());
                    }
                }
            }
            case DONOR_SMPS -> {
                DonorAudioSource source = donorAudioSources.get(
                        command.donorGameId());
                if (source != null) {
                    AbstractSmpsData sfx = source.loader().loadSfx(
                            command.sfxId());
                    if (sfx != null) {
                        if (source.config() != null) {
                            backend.playSfxSmps(sfx, source.dac(),
                                    command.pitch(), source.config());
                        } else {
                            backend.playSfxSmps(
                                    sfx, source.dac(), command.pitch());
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
        deferredReverseLogicalSnapshot = null;
        deferredReverseLogicalPrepared = false;
        postBoundaryReverseTarget = false;
        reverseAudioPresentationActive = true;
        ensureShadowPresentation();
        shadowProducer.beginReverse(1.0);
    }

    public boolean isReverseAudioPresentationActive() {
        return reverseAudioPresentationActive;
    }

    /**
     * Ends the held reverse presentation and publishes the selected logical
     * target.
     *
     * <p>The release is split around exactly one irreversible step,
     * {@code shadowProducer.endReverse(...)}: it consumes the reverse PCM
     * cursor and commits the prepared registry restore, and no retry can
     * recreate the session it destroyed. Every fallible step — preparing the
     * restore selection and materializing the donor-binding replacement —
     * therefore runs strictly <em>before</em> it, so a failure up to that point
     * leaves the release exactly retryable. Everything after it is
     * unconditional local publication that must not be skipped: if it somehow
     * throws, the release still completes from the values prepared before the
     * commit, because reporting a retryable failure there would make the retry
     * re-prepare a restore selection that {@code endReverse} can no longer
     * commit (the reverse session is already gone), leaking every voice that
     * preparation recreated.
     */
    public boolean endReverseAudioPresentation() {
        AudioLogicalSnapshot selected = deferredReverseLogicalSnapshot;
        boolean preparedDuringAttempt = false;
        boolean producerCommitted = false;
        // Consumed once per entry, whichever exit this attempt takes, so an
        // armed injection can never leak into a later unrelated release.
        ReverseReleaseFailurePoint injectFailure = failNextReverseRelease;
        failNextReverseRelease = null;
        Map<GameSound, DonorSfxBinding> publishedBindings = null;
        try {
            if (selected != null && !deferredReverseLogicalPrepared) {
                if (!commitDeferredReverseLogicalRestore()) {
                    return false;
                }
                preparedDuringAttempt = true;
            }
            if (selected != null) {
                // Built before the irreversible commit so a malformed or
                // unmappable binding fails while the release is still exactly
                // retryable, rather than half-published afterwards.
                publishedBindings = new EnumMap<>(GameSound.class);
                for (AudioLogicalSnapshot.DonorSfxBindingSnapshot binding
                        : selected.donorBindings()) {
                    publishedBindings.put(binding.sound(),
                            new DonorSfxBinding(binding.donorGameId(),
                                    binding.sfxId()));
                }
            }
            if (injectFailure
                    == ReverseReleaseFailurePoint.BEFORE_PRODUCER_COMMIT) {
                throw new IllegalStateException(
                        "injected reverse release publication failure");
            }
            if (shadowProducer != null) {
                shadowProducer.endReverse(
                        !postBoundaryReverseTarget);
            }
            producerCommitted = true;
            if (injectFailure
                    == ReverseReleaseFailurePoint.AFTER_PRODUCER_COMMIT) {
                throw new IllegalStateException(
                        "injected reverse release publication failure");
            }
        } catch (RuntimeException failure) {
            if (producerCommitted) {
                publishReverseReleaseLedger(selected, publishedBindings);
                LOGGER.log(Level.WARNING,
                        "Audio reverse release publication failed after the "
                                + "producer commit; completed the release from "
                                + "the pre-commit ledger", failure);
                return true;
            }
            if (preparedDuringAttempt) {
                try {
                    shadowProducer.discardPreparedRestoreSelection();
                } catch (RuntimeException discardFailure) {
                    failure.addSuppressed(discardFailure);
                }
                deferredReverseLogicalPrepared = false;
            }
            LOGGER.log(Level.WARNING,
                    "Audio reverse release failed; retained prior live state",
                    failure);
            return false;
        }
        publishReverseReleaseLedger(selected, publishedBindings);
        return true;
    }

    /**
     * Unconditional post-commit publication. Every value it writes was
     * computed before the producer commit, so it cannot fail.
     */
    private void publishReverseReleaseLedger(
            AudioLogicalSnapshot selected,
            Map<GameSound, DonorSfxBinding> publishedBindings) {
        if (selected != null) {
            logicalRestorePublications++;
            ringLeft = selected.ringLeft();
            commandTimeline.restoreCursor(
                    selected.commandTimelineFrame(),
                    selected.commandTimelineNextOrder());
            donorSoundBindings.clear();
            donorSoundBindings.putAll(publishedBindings);
        }
        deferredReverseLogicalSnapshot = null;
        deferredReverseLogicalPrepared = false;
        reverseAudioPresentationActive = false;
        postBoundaryReverseTarget = false;
    }

    /**
     * Replaces any selected/prepared pre-boundary rewind target with a capture
     * of the already initialized post-boundary producer state.
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
            clearPreparedReverseRestore();
            shadowProducer.discardPreparedRestoreSelection();
            deferredReverseLogicalSnapshot = fresh;
            deferredReverseLogicalPrepared = false;
            postBoundaryReverseTarget = true;
        }
        return commitDeferredReverseLogicalRestore();
    }

    /**
     * Prepares the one manager-owned logical restore at a held-rewind commit
     * boundary without consuming the reverse PCM cursor.
     */
    public boolean commitDeferredReverseLogicalRestore() {
        if (!reverseAudioPresentationActive
                || deferredReverseLogicalSnapshot == null
                || deferredReverseLogicalPrepared) {
            return deferredReverseLogicalPrepared;
        }
        AudioLogicalSnapshot selected = deferredReverseLogicalSnapshot;
        try {
            shadowProducer.prepareRestoreSelection(
                    selected.presentation(), shadowFactory);
            deferredReverseLogicalPrepared = true;
            return true;
        } catch (RuntimeException failure) {
            deferredReverseLogicalPrepared = false;
            LOGGER.log(Level.WARNING,
                    "Audio reverse target preparation failed; retained live "
                            + "state for retry",
                    failure);
            return false;
        }
    }

    /**
     * Marks any manager-side prepared reverse target as unpublished. The
     * producer owns the prepared registry restore itself and releases it
     * through {@code discardPreparedRestoreSelection()} or {@code close()}.
     */
    private void clearPreparedReverseRestore() {
        deferredReverseLogicalPrepared = false;
    }

    public void setReversePlaybackRate(double rate) {
        ensureShadowPresentation();
        shadowProducer.setReverseRate(rate);
    }

    /**
     * Forward playback rate, 1.0 being real time. Owners that run the
     * simulation faster than real time (visual Trace Test Mode fast-forward)
     * set this to the same rate so the picture and the audio speed up
     * together; every such owner must restore 1.0 when it stops, since the
     * rate outlives the consumer that set it.
     */
    public void setForwardPlaybackRate(double rate) {
        ensureShadowPresentation();
        shadowProducer.setForwardRate(rate);
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

    int logicalRestorePublicationsForTesting() {
        return logicalRestorePublications;
    }

    void resetLogicalRestorePublicationsForTesting() {
        logicalRestorePublications = 0;
    }

    void failNextReverseReleaseForTesting(ReverseReleaseFailurePoint point) {
        failNextReverseRelease =
                java.util.Objects.requireNonNull(point, "point");
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
        BaseAudioSource source = baseAudioSource;
        GameAudioProfile profile = source.profile();
        if (profile != null) {
            if (profile.handleSystemCommand(musicId, this)) {
                return;
            }
            if (musicId == profile.getSpeedShoesOnCommandId()) {
                if (profile.getSpeedMode() == GameAudioProfile.SpeedMode.FRAME_MULTIPLY) {
                    setSpeedMultiplier(profile.getSpeedMultiplierValue());
                } else {
                    setSpeedShoes(true);
                }
                return;
            } else if (musicId == profile.getSpeedShoesOffCommandId()) {
                if (profile.getSpeedMode() == GameAudioProfile.SpeedMode.FRAME_MULTIPLY) {
                    setSpeedMultiplier(1);
                } else {
                    setSpeedShoes(false);
                }
                return;
            }
        }

        // The 1-up jingle, invincibility and Super themes interrupt the zone
        // music instead of replacing it: the interrupted song is pushed so the
        // driver's "fade in to previous" (E4) and the power-up timeouts can
        // bring it back. Without this the interrupted song is destroyed and the
        // restore finds an empty stack, leaving the level silent.
        boolean override = profile != null && profile.isMusicOverride(musicId);

        if (source.loader() != null) {
            ensureShadowPresentation();
            SmpsAssetKey key = new SmpsAssetKey(
                    baseGameId(source), SmpsAssetKey.Route.BASE_MUSIC,
                    musicId, null);
            boolean registered = shadowFactory.findRegisteredSmpsMusicAsset(
                    key, source.generation()) != null;
            AbstractSmpsData data = null;
            if (!registered) {
                data = source.loader().loadMusic(musicId);
                if (data != null) {
                    shadowFactory.registerSmpsMusicAsset(
                            key, source.generation(), data,
                            source.dac(), source.config());
                    registered = true;
                }
            }
            if (registered) {
                recordTimelineCommand(new AudioCommand.PlayMusic(
                        musicId, AudioCommand.MusicRoute.BASE_SMPS, override, null));
                if (sendLiveBackendCommands()) {
                    backend.prepareLogicalMusicSource(AudioSourceDescriptor.baseMusic(musicId));
                    backend.playSmps(data, source.dac());
                }
                return;
            }
        }
        recordTimelineCommand(new AudioCommand.PlayMusic(
                musicId, AudioCommand.MusicRoute.FALLBACK_WAV, override, null));
        if (sendLiveBackendCommands()) {
            backend.prepareLogicalMusicSource(AudioSourceDescriptor.fallbackMusic(musicId));
            backend.playMusic(musicId);
        }
    }

    public boolean playMusic(GameMusic music) {
        Integer musicId = resolveMusic(baseAudioSource.profile(), music);
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
        BaseAudioSource source = baseAudioSource;
        if (source.loader() != null) {
            AbstractSmpsData sfx = source.loader().loadSfx(sfxName);
            if (sfx != null) {
                recordTimelineCommand(new AudioCommand.PlaySfx(
                        -1, sfxName, AudioCommand.SfxRoute.BASE_SMPS_NAME, pitch, null));
                if (sendLiveBackendCommands()) {
                    backend.playSfxSmps(sfx, source.dac(), pitch);
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

        GameAudioProfile profile = baseAudioSource.profile();
        float effectivePitch = profile != null
                ? profile.adjustSfxPitch(sound, pitch) : pitch;
        boolean played = false;
        if (soundMap != null && soundMap.containsKey(sound)) {
            played = playSfx(soundMap.get(sound), effectivePitch);
        }
        if (!played) {
            DonorSfxBinding binding = donorSoundBindings.get(sound);
            if (binding != null) {
                DonorAudioSource donor = donorAudioSources.get(
                        binding.gameId());
                if (donor != null) {
                    AbstractSmpsData sfx = donor.loader().loadSfx(
                            binding.sfxId());
                    if (sfx != null) {
                        recordTimelineCommand(new AudioCommand.PlaySfx(
                                binding.sfxId(),
                                sound.name(),
                                AudioCommand.SfxRoute.DONOR_SMPS,
                                effectivePitch,
                                binding.gameId()));
                        if (sendLiveBackendCommands()) {
                            if (donor.config() != null) {
                                backend.playSfxSmps(sfx, donor.dac(),
                                        effectivePitch, donor.config());
                            } else {
                                backend.playSfxSmps(
                                        sfx, donor.dac(), effectivePitch);
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
        BaseAudioSource source = baseAudioSource;
        if (source.loader() != null) {
            AbstractSmpsData sfx = source.loader().loadSfx(sfxId);
            if (sfx != null) {
                recordTimelineCommand(new AudioCommand.PlaySfx(
                        sfxId, null, AudioCommand.SfxRoute.BASE_SMPS_ID, pitch, null));
                if (sendLiveBackendCommands()) {
                    backend.playSfxSmps(sfx, source.dac(), pitch);
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
        DonorAudioSource source = donorAudioSources.get(donorGameId);
        if (source != null) {
            AbstractSmpsData sfx = source.loader().loadSfx(sfxId);
            if (sfx != null) {
                recordTimelineCommand(new AudioCommand.PlaySfx(
                        sfxId, null, AudioCommand.SfxRoute.DONOR_SMPS, 1.0f, donorGameId));
                if (sendLiveBackendCommands()) {
                    if (source.config() != null) {
                        backend.playSfxSmps(
                                sfx, source.dac(), 1.0f, source.config());
                    } else {
                        backend.playSfxSmps(sfx, source.dac(), 1.0f);
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
        DonorAudioSource source = donorAudioSources.get(donorGameId);
        if (source != null) {
            ensureShadowPresentation();
            SmpsAssetKey key = new SmpsAssetKey(
                    donorGameId, SmpsAssetKey.Route.DONOR_MUSIC,
                    musicId, null);
            boolean registered = shadowFactory.findRegisteredSmpsMusicAsset(
                    key, source.generation()) != null;
            AbstractSmpsData data = null;
            if (!registered) {
                data = source.loader().loadMusic(musicId);
                if (data != null) {
                    shadowFactory.registerSmpsMusicAsset(
                            key, source.generation(), data,
                            source.dac(), source.config());
                    registered = true;
                }
            }
            if (registered) {
                // Donor music replaces the foreground like any other song. Donor
                // ids are only ever used for cross-game Super and data-select
                // music, none of which the ROM saves and restores — only the
                // 1-up jingle does that, and it is never a donor track.
                recordTimelineCommand(new AudioCommand.PlayMusic(
                        musicId, AudioCommand.MusicRoute.DONOR_SMPS, false, donorGameId));
                if (sendLiveBackendCommands()) {
                    backend.prepareLogicalMusicSource(AudioSourceDescriptor.donorMusic(donorGameId, musicId));
                    backend.playSmps(
                            data, source.dac(), source.config(), false);
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
        GameAudioProfile profile = baseAudioSource.profile();
        if (music == null || profile == null) {
            return false;
        }
        Integer musicId = profile.getMusicMap().get(music);
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
        registerDonorLoader(gameId, loader, dacData, null, null);
    }

    /**
     * Registers a donor SmpsLoader, DacData, and SmpsSequencerConfig for cross-game SFX playback.
     * The config will be passed to the backend so the donor SFX uses the correct driver settings.
     */
    public void registerDonorLoader(String gameId, SmpsLoader loader, DacData dacData,
                                    SmpsSequencerConfig config) {
        registerDonorLoader(gameId, loader, dacData, config, null);
    }

    public synchronized void registerDonorLoader(
            String gameId,
            SmpsLoader loader,
            DacData dacData,
            SmpsSequencerConfig config,
            GameAudioProfile donorProfile) {
        beginSourceMutation();
        try {
            String resolvedGameId = requireDonorGameId(gameId);
            SmpsLoader resolvedLoader = Objects.requireNonNull(loader, "loader");
            DacData resolvedDac = Objects.requireNonNull(dacData, "dacData");
            DonorAudioSource previous = donorAudioSources.get(resolvedGameId);
            long retainedGeneration = donorGenerationCounters.getOrDefault(
                    resolvedGameId,
                    previous != null ? previous.generation() : 0L);
            long candidateGeneration = nextGeneration(retainedGeneration);
            PresentationCoordHandlerPreparation presentationPreparation =
                    preparePresentationCoordHandlers(donorProfile);
            boolean[] backendConfigurationAttempted = {false};
            Runnable backendConfiguration = () -> {
                boolean clearsPreviousProfile = donorProfile == null
                        && previous != null && previous.profile() != null;
                if (backend != null
                        && (donorProfile != null || clearsPreviousProfile)) {
                    backendConfigurationAttempted[0] = true;
                    backend.registerAudioProfileCoordHandlers(donorProfile);
                }
            };
            try {
                if (presentationPreparation.configureCandidate()) {
                    presentationPreparation.owner().configureTransactionally(
                            owner -> donorProfile
                                    .configurePresentationCoordFlagHandlers(owner),
                            backendConfiguration);
                } else {
                    backendConfiguration.run();
                }
            } catch (RuntimeException | Error failure) {
                Throwable restoreFailure =
                        backendConfigurationAttempted[0]
                                ? restoreDonorBackendConfiguration(previous)
                                : null;
                if (restoreFailure != null) {
                    removeDonorSource(
                            resolvedGameId, candidateGeneration);
                    failure.addSuppressed(restoreFailure);
                }
                throw failure;
            }
            publishPresentationCoordHandlers(presentationPreparation);
            publishDonorSource(resolvedGameId, new DonorAudioSource(
                    resolvedLoader, resolvedDac, config, donorProfile,
                    candidateGeneration));
        } finally {
            endSourceMutation();
        }
    }

    private Throwable restoreDonorBackendConfiguration(
            DonorAudioSource previous) {
        try {
            if (backend != null) {
                backend.registerAudioProfileCoordHandlers(
                        previous != null ? previous.profile() : null);
            }
        } catch (RuntimeException | Error failure) {
            return failure;
        }
        return null;
    }

    private void publishDonorSource(
            String gameId, DonorAudioSource source) {
        Map<String, Long> generations =
                new HashMap<>(donorGenerationCounters);
        generations.put(gameId, source.generation());
        donorGenerationCounters = Map.copyOf(generations);
        Map<String, DonorAudioSource> sources =
                new HashMap<>(donorAudioSources);
        sources.put(gameId, source);
        donorAudioSources = Map.copyOf(sources);
    }

    private void removeDonorSource(String gameId, long generation) {
        Map<String, Long> generations =
                new HashMap<>(donorGenerationCounters);
        generations.put(gameId, generation);
        donorGenerationCounters = Map.copyOf(generations);
        Map<String, DonorAudioSource> sources =
                new HashMap<>(donorAudioSources);
        sources.remove(gameId);
        donorAudioSources = Map.copyOf(sources);
    }

    private static String requireDonorGameId(String gameId) {
        String resolved = Objects.requireNonNull(gameId, "gameId");
        if (resolved.isBlank()) {
            throw new IllegalArgumentException("gameId must not be blank");
        }
        return resolved;
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
    public synchronized void clearDonorAudio() {
        beginSourceMutation();
        try {
            clearDonorAudioState();
        } finally {
            endSourceMutation();
        }
    }

    private void clearDonorAudioState() {
        Map<String, Long> advancedGenerations = new HashMap<>();
        donorAudioSources.forEach((gameId, source) ->
                advancedGenerations.put(
                        gameId, nextGeneration(source.generation())));
        Map<String, Long> generations =
                new HashMap<>(donorGenerationCounters);
        generations.putAll(advancedGenerations);
        donorGenerationCounters = Map.copyOf(generations);
        donorAudioSources = Map.of();
        donorSoundBindings.clear();
        donorMusicBindings.clear();
    }

    /**
     * Resets mutable state without destroying the singleton instance.
     * Used by TestEnvironment to prevent state leaking between tests
     * (e.g. Sonic 1 SMPS loader contaminating Sonic 2 tests).
     */
    public synchronized void resetState() {
        beginSourceMutation();
        try {
            clearPreparedReverseRestore();
            if (backend != null) {
                backend.stopPlayback();
            }
            this.baseAudioSource =
                    new BaseAudioSource(null, null, null, null, null, 0);
            this.soundMap = null;
            this.ringLeft = true;
            this.rewindReplaySuppressionDepth = 0;
            this.audioFrameOwnedExternally = false;
            this.audioFrameAdvanced = false;
            this.reverseAudioPresentationActive = false;
            this.deferredReverseLogicalSnapshot = null;
            this.deferredReverseLogicalPrepared = false;
            this.postBoundaryReverseTarget = false;
            this.logicalRestorePublications = 0;
            this.failNextReverseRelease = null;
            this.commandTimeline.clear();
            // A mode transition rebuilds the presentation; it does not end a
            // recording of the window. Entering a game from the master title screen
            // runs Engine.resetForGameplayFromMasterTitle -> resetState() before
            // initializeGlobalGameplayServices -> ensurePresentationSink, so
            // retiring the lease here killed the recording's audio before the
            // retained backend could rebuild its sink. Only destroy() is a genuine
            // teardown.
            detachLiveCaptureAudioHandleForRebuild();
            closeShadowPresentation();
            clearDonorAudioState();
            donorGenerationCounters = Map.of();
        } finally {
            endSourceMutation();
        }
    }

    private AudioTimelineEntry recordTimelineCommand(AudioCommand command) {
        if (!suppressingRewindReplay()) {
            AudioTimelineEntry entry = commandTimeline.record(command);
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
            publishPresentationCoordHandlers(
                    preparePresentationCoordHandlers(null));
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
        shadowFrameRate = frameRate;
        shadowParity = new AudioPresentationParityProbe(sampleRate, frameRate);
        rebindLiveCaptureAudioHandle(sampleRate);
    }

    private void restoreShadowMusic() {
        shadowRestoreRequested = true;
    }

    private void closeShadowPresentation() {
        // Closing the producer closes every lease attached to it, so neither
        // the offline compatibility lease nor the live-recording lease may
        // outlive its producer. Both manager-side references are retired here
        // rather than through closeLiveCaptureAudio/endCaptureMode: the
        // producer releases the underlying leases itself on close, and a
        // detach can only be issued on its owner thread.
        //
        // Both follow one rule — the reference is dropped exactly when the
        // producer accepted the detach — because the producer has two distinct
        // close failure modes:
        //   * an off-owner-thread close is rejected before anything is
        //     released, so the manager must keep believing it still holds both
        //     leases and the caller can retry from the owner thread;
        //   * a close on the owner thread marks every lease closed and detaches
        //     it before any teardown step that can throw, so once the producer
        //     reports itself closed the manager must drop both references even
        //     if teardown then failed. Keeping a dead handle would refuse every
        //     later beginCaptureMode/beginLiveCaptureAudio for the rest of the
        //     process; dropping a live one would let the next lease attach
        //     twice.
        if (shadowProducer != null) {
            try {
                shadowProducer.close();
            } finally {
                if (shadowProducer.isClosed()) {
                    offlineCaptureHandle = null;
                    retireLiveCaptureAudioHandle();
                }
            }
        } else {
            offlineCaptureHandle = null;
            retireLiveCaptureAudioHandle();
            if (presentationSink != null) {
                presentationSink.close();
            }
        }
        presentationSink = null;
        shadowProducer = null;
        shadowFrameRate = 0;
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

    /**
     * A rebuild of the presentation must not end a recording that is already
     * running. Both rebuild entry points mark the lease here: {@link
     * #setBackend} (entering gameplay installs the real audio backend) and
     * {@link #resetState} (a mode transition such as leaving the master title
     * screen, or a level teardown). Retiring the lease at either of them meant a
     * recording started on the master title screen lost its audio the moment the
     * player started a game, and the Task 11 degradation turned that into
     * permanent silence rather than a visible failure.
     *
     * <p>{@link #destroy()} does not mark, so a genuine teardown still retires.
     */
    private synchronized void detachLiveCaptureAudioHandleForRebuild() {
        ManagerLiveCaptureAudioHandle live = activeLiveCaptureAudioHandle;
        liveCaptureAwaitingRebind = live != null && !live.closed;
    }

    /**
     * Re-attaches a carried lease to the freshly built producer.
     *
     * <p>Nothing here may escape: {@link #ensureShadowPresentation()} is reached
     * from most of this class, so a throw would surface as an unrelated failure
     * far from the recording. A lease that cannot be carried is retired instead,
     * which the recorder already degrades to phase-correct clocked silence with
     * one logged warning.
     *
     * @param producerSampleRate the rebuilt producer's rate. A recording is
     *        muxed at the rate captured when it started (ffmpeg {@code -ar}),
     *        so carrying a lease onto a producer running at a different rate
     *        would write pitch-shifted audio — which looks like it worked.
     *        Refusing the carry is the honest answer.
     */
    private synchronized void rebindLiveCaptureAudioHandle(int producerSampleRate) {
        if (!liveCaptureAwaitingRebind) {
            return;
        }
        liveCaptureAwaitingRebind = false;
        ManagerLiveCaptureAudioHandle live = activeLiveCaptureAudioHandle;
        if (live == null || live.closed || shadowProducer == null) {
            return;
        }
        if (live.capture.sampleRate() != producerSampleRate) {
            LOGGER.warning("Live recording lease cannot follow the presentation"
                    + " from " + live.capture.sampleRate() + " Hz to "
                    + producerSampleRate + " Hz; the recording continues"
                    + " without audio rather than pitch-shifted");
            retireLiveCaptureAudioHandle();
            return;
        }
        try {
            // Carry the phase so the recorded audio does not jump at the swap.
            long carried = live.capture.totalStereoFrames();
            live.capture = shadowProducer.attachCapture(
                    live.requestedFrameRate, live.capture.clockSnapshot());
            live.carriedStereoFrames += carried;
        } catch (RuntimeException rebindFailure) {
            LOGGER.log(Level.WARNING,
                    "Live recording lease could not be carried across a"
                            + " presentation rebuild", rebindFailure);
            retireLiveCaptureAudioHandle();
        }
    }

    private synchronized void retireLiveCaptureAudioHandle() {
        if (liveCaptureAwaitingRebind) {
            // A rebuild is in flight and will rebind this lease to the new
            // producer. Only a genuine teardown retires it.
            return;
        }
        ManagerLiveCaptureAudioHandle live = activeLiveCaptureAudioHandle;
        if (live != null) {
            live.closed = true;
            activeLiveCaptureAudioHandle = null;
        }
    }

    private PresentationCoordHandlerPreparation
            preparePresentationCoordHandlers(GameAudioProfile candidate) {
        return preparePresentationCoordHandlers(candidate, false);
    }

    private PresentationCoordHandlerPreparation
            preparePresentationCoordHandlers(
                    GameAudioProfile candidate,
                    boolean replacesBaseProfile) {
        if (candidate == null && presentationCoordFlagHandlers != null) {
            return new PresentationCoordHandlerPreparation(
                    presentationCoordFlagHandlers,
                    Set.copyOf(presentationCoordHandlerGameIds),
                    false, false, null);
        }
        if (presentationCoordFlagHandlers != null) {
            String candidateGameId = requireDonorGameId(
                    candidate.presentationGameId());
            return new PresentationCoordHandlerPreparation(
                    presentationCoordFlagHandlers,
                    Set.copyOf(presentationCoordHandlerGameIds),
                    false,
                    !presentationCoordHandlerGameIds.contains(
                            candidateGameId),
                    candidateGameId);
        }
        SmpsCoordFlagHandlerOwner preparedOwner =
                new SmpsCoordFlagHandlerOwner(
                        new SmpsCoordFlagRuntimeState());
        Set<String> preparedGameIds = new LinkedHashSet<>();
        if (replacesBaseProfile) {
            configurePreparedPresentationCoordHandlers(
                    candidate, preparedOwner, preparedGameIds);
        } else {
            configurePreparedPresentationCoordHandlers(
                    baseAudioSource.profile(), preparedOwner,
                    preparedGameIds);
        }
        donorAudioSources.values().stream()
                .map(DonorAudioSource::profile)
                .filter(Objects::nonNull)
                .forEach(profile -> configurePreparedPresentationCoordHandlers(
                        profile, preparedOwner, preparedGameIds));
        if (!replacesBaseProfile) {
            configurePreparedPresentationCoordHandlers(
                    candidate, preparedOwner, preparedGameIds);
        }
        return new PresentationCoordHandlerPreparation(
                preparedOwner, Set.copyOf(preparedGameIds), true, false,
                null);
    }

    private static void configurePreparedPresentationCoordHandlers(
            GameAudioProfile profile,
            SmpsCoordFlagHandlerOwner owner,
            Set<String> configuredGameIds) {
        if (profile == null) {
            return;
        }
        String gameId = requireDonorGameId(profile.presentationGameId());
        if (!configuredGameIds.contains(gameId)) {
            profile.configurePresentationCoordFlagHandlers(owner);
            configuredGameIds.add(gameId);
        }
    }

    private void publishPresentationCoordHandlers(
            PresentationCoordHandlerPreparation preparation) {
        if (preparation.publishOwner()) {
            presentationCoordFlagHandlers = preparation.owner();
            presentationCoordHandlerGameIds.clear();
            presentationCoordHandlerGameIds.addAll(
                    preparation.gameIds());
        } else if (preparation.configureCandidate()) {
            presentationCoordHandlerGameIds.add(
                    preparation.candidateGameId());
        }
    }

    private String baseGameId(BaseAudioSource source) {
        try {
            return source.profile() != null
                    ? source.profile().presentationGameId() : "base";
        } catch (RuntimeException unavailable) {
            return "base";
        }
    }

    private final class ShadowSources implements AudioPresentationCommandResolver.Sources {
        private final int maxFrames;

        private ShadowSources(int maxFrames) {
            this.maxFrames = maxFrames;
        }

        @Override
        public AudioPresentationCommandResolver.SourceAccess sourceFor(
                com.openggf.audio.presentation.SmpsAssetKey.Route route,
                String donorGameId) {
            return switch (route) {
                case BASE_MUSIC, BASE_ID, BASE_NAME -> {
                    BaseAudioSource source = baseAudioSource;
                    yield new AudioPresentationCommandResolver.SourceAccess(
                            baseGameId(source), source.generation(),
                            source.loader(), source.dac(), source.config(),
                            policyFor(source.profile()));
                }
                case DONOR_MUSIC, DONOR_ID -> {
                    String gameId = requireDonorGameId(donorGameId);
                    DonorAudioSource source =
                            donorAudioSources.get(gameId);
                    long generation = source != null
                            ? source.generation()
                            : donorGenerationCounters.getOrDefault(
                                    gameId, 0L);
                    yield new AudioPresentationCommandResolver.SourceAccess(
                            gameId, generation,
                            source != null ? source.loader() : null,
                            source != null ? source.dac() : null,
                            source != null ? source.config() : null,
                            policyFor(source != null
                                    ? source.profile() : null));
                }
                case FALLBACK_NAME -> throw new IllegalArgumentException(
                        "fallback assets have no SMPS source");
            };
        }

        private String baseGameId(BaseAudioSource source) {
            return AudioManager.this.baseGameId(source);
        }

        private AudioPresentationCommandResolver.SfxPolicy policyFor(
                GameAudioProfile profile) {
            return new AudioPresentationCommandResolver.SfxPolicy() {
                @Override public int priority(int sfxId) {
                    return profile != null
                            ? profile.getSfxPriority(sfxId) : 0x70;
                }

                @Override public boolean special(int sfxId) {
                    return profile != null && profile.isSpecialSfx(sfxId);
                }

                @Override public boolean continuous(int sfxId) {
                    return profile != null
                            && profile.isContinuousSfx(sfxId);
                }
            };
        }

        @Override public int maxStereoFrames() {
            return maxFrames;
        }
    }

    private final class ManagerLiveCaptureAudioHandle implements LiveCaptureAudioHandle {
        private LiveCaptureAudioHandle capture;
        private final int requestedFrameRate;
        /**
         * Frames drained through leases this handle has already outlived. A
         * fresh lease deliberately starts its own total at zero, but a rebind
         * continues one recording, so the totals are summed rather than reset.
         */
        private long carriedStereoFrames;
        private boolean closed;

        private ManagerLiveCaptureAudioHandle(LiveCaptureAudioHandle capture,
                                              int requestedFrameRate) {
            this.capture = capture;
            this.requestedFrameRate = requestedFrameRate;
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
                return carriedStereoFrames + capture.totalStereoFrames();
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
        clearPreparedReverseRestore();
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
