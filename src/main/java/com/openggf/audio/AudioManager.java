package com.openggf.audio;

import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.driver.SfxContentionObserver;
import com.openggf.audio.driver.SmpsDriverServiceObserver;
import com.openggf.audio.driver.SmpsRequestAdmissionPolicy;
import com.openggf.audio.driver.SmpsRequestAdmissionPolicy.AdmissionResult;
import com.openggf.audio.driver.SmpsRequestAdmissionPolicy.RejectionReason;
import com.openggf.audio.driver.SmpsRequestAdmissionPolicy.SmpsAdmissionContext;
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
import com.openggf.audio.session.LegacyCompatibilitySmpsPhysicalPolicy;
import com.openggf.audio.session.SmpsDriverSession;
import com.openggf.audio.session.SmpsDriverSessionConfiguration;
import com.openggf.audio.session.SmpsPhysicalDevice;
import com.openggf.audio.session.SmpsPhysicalPolicy;
import com.openggf.audio.session.SmpsSessionProfileFingerprint;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.audio.presentation.AudioPresentationCommand;
import com.openggf.audio.presentation.AudioPresentationDependencyResolver;
import com.openggf.audio.presentation.AudioPresentationCommandQueue;
import com.openggf.audio.presentation.AudioPresentationCommandResolver;
import com.openggf.audio.presentation.AudioPresentationForwardService;
import com.openggf.audio.presentation.AudioPresentationMixer;
import com.openggf.audio.presentation.AudioPresentationParityProbe;
import com.openggf.audio.presentation.AudioPresentationProducer;
import com.openggf.audio.presentation.AudioPresentationSnapshot;
import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.openggf.audio.presentation.AudioPresentationSessionCommandApplier;
import com.openggf.audio.presentation.AudioRequestService;
import com.openggf.audio.presentation.AudioVoiceRegistry;
import com.openggf.audio.presentation.DecodedPcmCache;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.presentation.PresentationVoiceSnapshot;
import com.openggf.audio.presentation.SmpsAssetKey;
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

@com.openggf.game.ModApi
public class AudioManager implements MusicRestoreSink {
    private static final Logger LOGGER = Logger.getLogger(AudioManager.class.getName());
    private static final int PCM_HISTORY_SECONDS = 60;
    private static final int REVERSE_RELEASE_CROSSFADE_MS = 45;
    private static AudioManager instance;
    private AudioBackend backend;
    private final SourcePublication sourcePublication = new SourcePublication();
    private Map<GameSound, Integer> soundMap;
    // Director-owned playlist admission survives producer/shot replacement until its scope closes.
    private final LevelMusicAccess levelMusicAccess = new LevelMusicAccess(this);
    private final DiagnosticInstallation diagnosticInstallation = new DiagnosticInstallation();
    // Internal world ownership never replaces the single external diagnostic observer.
    private final AudioMutatorAccess mutatorAccess = new AudioMutatorAccess();

    static final AudioMutatorAccess mutatorAccess(AudioManager audio) {
        return audio.mutatorAccess;
    }

    final LevelMusicAccess levelMusicAccess() {
        return levelMusicAccess;
    }

    private void mutatorMusicIntent(AudioMutatorAccess.Kind kind, int value) {
        mutatorAccess.musicIntent(kind, value);
    }

    private void mutatorSfxIntent(int id) {
        mutatorAccess.sfxIntent(id);
    }

    void prepareMutatorCue(int id, boolean music) {
        var source = sourcePublication.base();
        if (!(source.profile() instanceof NativeAudioEffectCues cues)
                || !(music ? cues.permitsTemporaryMusic(id) : cues.permitsLandingCue(id)))
            throw new IllegalArgumentException("Disallowed native " + (music ? "music" : "landing") + " cue: " + id);
        if (source.rom() == null || source.loader() == null || !hasCatalogDependencies(source.dac(), source.config()))
            throw new IllegalStateException("Native ROM audio is not prepared");
        presentationBinding.ensureShadowPresentation();
        if (music) {
            var key = new SmpsAssetKey(baseGameId(source), SmpsAssetKey.Route.BASE_MUSIC, id, null);
            if (presentationBinding.shadowFactory.findRegisteredSmpsMusicAsset(key, source.generation()) == null) {
                var data = source.loader().loadMusic(id);
                if (data == null) throw new IllegalStateException("Native ROM music cue is unavailable: " + id);
                presentationBinding.shadowFactory.registerSmpsMusicAsset(key, source.generation(), data, source.dac(), source.config());
            }
        } else if (!ensureRegisteredSmpsSfx(new SmpsAssetKey(baseGameId(source), SmpsAssetKey.Route.BASE_ID, id, null),
                sourcePublication.baseSfxSource(source))) {
            throw new IllegalStateException("Native ROM landing cue is unavailable: " + id);
        }
    }

    boolean mutatorWarningActive() {
        if (!(sourcePublication.base().profile() instanceof NativeAudioEffectCues cues) || presentationBinding.shadowProducer == null) return false;
        var logical = presentationBinding.shadowProducer.snapshot().smpsLogical();
        if (logical == null) return false;
        return logical.sequencers().stream().anyMatch(entry -> entry.sfx()
                && cues.isWarningCue(entry.smpsData().getId())
                && entry.snapshot().tracks().stream().anyMatch(com.openggf.audio.rewind.SmpsTrackSnapshot::active));
    }

    /**
     * One immutable, complete diagnostic observer installation. The tuple is
     * intentionally owned by AudioManager so tooling cannot observe only one
     * side of a backend/presentation rebuild.
     */
    public record DiagnosticObserverSet(
            AudioRequestObserver request,
            AudioAdmissionObserver admission,
            SmpsDriverServiceObserver driverService,
            ChipWriteObserver chipWrite,
            SfxContentionObserver sfxContention) {
        public DiagnosticObserverSet {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(admission, "admission");
            Objects.requireNonNull(driverService, "driverService");
            Objects.requireNonNull(chipWrite, "chipWrite");
            Objects.requireNonNull(sfxContention, "sfxContention");
        }

        private static DiagnosticObserverSet none() {
            return new DiagnosticObserverSet(
                    AudioRequestObserver.NONE,
                    AudioAdmissionObserver.NONE,
                    SmpsDriverServiceObserver.NONE,
                    ChipWriteObserver.NONE,
                    SfxContentionObserver.NONE);
        }

        private boolean isNone() {
            return request == AudioRequestObserver.NONE
                    && admission == AudioAdmissionObserver.NONE
                    && driverService == SmpsDriverServiceObserver.NONE
                    && chipWrite == ChipWriteObserver.NONE
                    && sfxContention == SfxContentionObserver.NONE;
        }
    }

    /** Exclusive, owner-thread cleanup token for a diagnostic installation. */
    public interface DiagnosticObserverHandle extends AutoCloseable {
        boolean active();

        @Override
        void close();
    }
    private int rewindReplaySuppressionDepth;
    private final AudioCommandTimeline commandTimeline = new AudioCommandTimeline();
    /** Capture lifetime spans presentation producer generations for this manager. */
    /** Physical assembly and its publication slots have one manager-lifetime owner. */
    private final PresentationBinding presentationBinding = new PresentationBinding();
    private final CaptureLeases captureLeases = new CaptureLeases();
    /** Logical ring and publication protocol span physical producer generations. */
    private final LogicalPublication logicalPublication = new LogicalPublication();
    /** Held logical target policy survives presentation producer generations. */
    private final HeldReverseRestore heldReverseRestore = new HeldReverseRestore();
    private boolean audioFrameOwnedExternally;
    private boolean audioFrameAdvanced;
    private Runnable streamedMusicSessionInvalidator = () -> { };
    /**
     * Where an injected reverse-release failure is raised, relative to
     * successful return from {@code presentationBinding.shadowProducer.endReverse(...)}. Exceptions
     * inside that call can follow partial restoration or cursor consumption.
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
    private boolean shadowRestoreRequested;
    private Object offlineCadenceOwner;
    private int offlineServiceHz;
    private String standaloneGameId;
    private long standaloneVoiceId = 1;
    /** Transferred launch-port lifetime remains with the manager. */
    private StreamedMusicPort streamedMusicPort = StreamedMusicPort.EMPTY;

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
            GameAudioProfile sfxPolicyProfile,
            long generation) {
    }

    private boolean silentSimulation;

    private AudioManager() {
        // Default to NullBackend
        backend = new NullAudioBackend();
    }

    /** Creates an isolated, device-free audio sink for native simulation. */
    public static AudioManager createSilent() {
        AudioManager manager = new AudioManager();
        manager.silentSimulation = true;
        return manager;
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
        manager.sourcePublication.initializeStandalone(profile);
        manager.presentationBinding.installStandalone(sink, coordFlagHandlers);
        manager.standaloneGameId = gameId;
        manager.presentationBinding.initializeStandalone(
                gameId, profile, config, coordFlagHandlers);
        return manager;
    }

    public AudioBackend getBackend() {
        return backend;
    }

    /** Transfers a launch-scoped prepared streamed-music lease to presentation. */
    public synchronized void installStreamedMusicPort(StreamedMusicPort port) {
        StreamedMusicPort replacement = java.util.Objects.requireNonNull(port, "port");
        int expectedRate = outputSampleRate();
        int actualRate = replacement != StreamedMusicPort.EMPTY
                ? replacement.outputRate() : expectedRate;
        if (actualRate != expectedRate) {
            replacement.close();
            throw new IllegalArgumentException("Streamed output rate " + actualRate
                    + " differs from presentation rate " + expectedRate);
        }
        if (replacement == streamedMusicPort) {
            return;
        }
        releaseStreamedMusicPort();
        try {
            presentationBinding.installStreamedPort(replacement);
            streamedMusicPort = replacement;
        } catch (RuntimeException failure) {
            try {
                replacement.close();
            } catch (RuntimeException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    /** Clears launch-scoped streamed presentation state from both owners. */
    public synchronized void resetStreamedMusicPort() {
        releaseStreamedMusicPort();
    }

    /**
     * Retires presentation cursors before releasing the transferred port.
     * Backend reset is retained only for direct compatibility clients; the
     * manager never transfers its presentation-owned port to the backend.
     */
    private synchronized void releaseStreamedMusicPort() {
        StreamedMusicPort previous = streamedMusicPort;
        streamedMusicPort = StreamedMusicPort.EMPTY;
        RuntimeException failure = null;
        try {
            presentationBinding.retireStreamedCursors(previous);
        } catch (RuntimeException retireFailure) {
            failure = retireFailure;
        }
        if (backend != null) {
            try {
                backend.resetStreamedMusicPort();
            } catch (RuntimeException resetFailure) {
                if (failure == null) failure = resetFailure;
                else failure.addSuppressed(resetFailure);
            }
        }
        if (previous != StreamedMusicPort.EMPTY) {
            try {
                previous.close();
            } catch (RuntimeException closeFailure) {
                if (failure == null) failure = closeFailure;
                else failure.addSuppressed(closeFailure);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** Invalidates the external view without allowing callback failure to leak its port. */
    private void invalidateAndReleaseStreamedMusicSession() {
        RuntimeException failure = null;
        try {
            streamedMusicSessionInvalidator.run();
        } catch (RuntimeException invalidationFailure) {
            failure = invalidationFailure;
        }
        try {
            releaseStreamedMusicPort();
        } catch (RuntimeException releaseFailure) {
            if (failure == null) failure = releaseFailure;
            else failure.addSuppressed(releaseFailure);
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** Composition-root hook keeping the external-content view aligned with backend lifetime. */
    public void setStreamedMusicSessionInvalidator(Runnable invalidator) {
        streamedMusicSessionInvalidator = java.util.Objects.requireNonNull(invalidator, "invalidator");
    }

    /** Tests whether the exact expected backend remains installed. */
    public synchronized boolean hasInstalledBackend(AudioBackend expected) {
        return backend == java.util.Objects.requireNonNull(expected, "expected");
    }

    /**
     * The active backend's output (synthesis) sample rate. Routed through
     * AudioManager so callers don't reach the backend directly (see
     * TestAudioBackendBypassGuard).
     */
    public int outputSampleRate() {
        return presentationBinding.outputSampleRate();
    }

    /** Whether live output can follow the producer clock without long-term speaker drift. */
    public boolean isSpeakerRateRegulated() {
        AudioPresentationSink sink = presentationBinding.presentationSink;
        if (sink instanceof OpenAlPcmSink speaker) return speaker.isRateRegulated();
        return sink instanceof NoDeviceAudioSink
                || (sink == null && (backend == null || backend instanceof NullAudioBackend));
    }

    /** Host-owned finite scene music routed through the existing capture/speaker producer. */
    public void setScenePcmSource(com.openggf.audio.presentation.ScenePcmSource source) {
        presentationBinding.setScenePcmSource(source);
    }

    /**
     * Prepares at most 64 MiB of short-lived scene voice assets without replacing the launch's
     * streamed music port. Four simultaneous scopes are permitted. Closing a scope stops only
     * its own voices; samples are added before the ordinary history/capture/speaker boundary.
     * The handle belongs to the current audio producer and expires when that producer changes.
     */
    public SceneAudioSamples prepareSceneSamples(java.util.Map<String, StreamedMusicPort.SfxPcm> samples) {
        presentationBinding.ensureShadowPresentation();
        return new SceneAudioSamples(java.util.Objects.requireNonNull(samples));
    }

    @com.openggf.game.ModApi
    public final class SceneAudioSamples implements AutoCloseable {
        private final com.openggf.audio.presentation.AudioPresentationProducer producer = presentationBinding.shadowProducer;
        private final java.util.Map<String, com.openggf.audio.presentation.DecodedPcm> samples = new java.util.HashMap<>();
        private final java.util.List<com.openggf.audio.presentation.SampleBackedVoice> voices = new java.util.ArrayList<>();
        private final java.util.Map<String, Float> gains = new java.util.HashMap<>();
        private final com.openggf.audio.presentation.ScenePcmSource overlay = this::mix;
        private long[] accumulation = new long[0];
        private long nextVoice;
        private boolean closed;

        private SceneAudioSamples(java.util.Map<String, StreamedMusicPort.SfxPcm> offered) {
            if (offered.size() > 256) throw new IllegalArgumentException("Too many scene voice assets");
            long bytes = 0;
            for (var entry : offered.entrySet()) {
                String key = java.util.Objects.requireNonNull(entry.getKey());
                if (key.isEmpty() || key.length() > 1024) throw new IllegalArgumentException("Invalid scene sample key");
                var value = java.util.Objects.requireNonNull(entry.getValue());
                bytes = Math.addExact(bytes, (long) value.samples().length * Short.BYTES);
                if (bytes > 64L * 1024 * 1024) throw new IllegalArgumentException("Scene voice assets exceed 64 MiB");
                if (value.channels() < 1 || value.channels() > 2 || !Float.isFinite(value.gain()) || value.gain() < 0 || value.gain() > 1)
                    throw new IllegalArgumentException("Scene samples require mono/stereo PCM and gain 0..1");
                gains.put(key, value.gain());
                // DecodedPcm copies caller-owned samples; later mutations cannot alter an admitted asset.
                samples.put(key, new com.openggf.audio.presentation.DecodedPcm(key, value.channels(), value.sampleRate(), value.samples()));
            }
            producer.addPcmOverlay(overlay);
        }

        public void play(String key, float gain) {
            requireCurrent();
            if (!Float.isFinite(gain) || gain < 0 || gain > 1) throw new IllegalArgumentException("Scene gain must be 0..1");
            var pcm = samples.get(key);
            if (pcm == null) throw new IllegalArgumentException("Scene sample was not prepared: " + key);
            if (suppressingRewindReplay() || heldReverseRestore.blocksSfxAdmission()) return;
            voices.removeIf(com.openggf.audio.presentation.SampleBackedVoice::isComplete);
            if (voices.size() >= 32) throw new IllegalStateException("Scene voice overlap exceeds 32 voices");
            voices.add(com.openggf.audio.presentation.SampleBackedVoice.oneShot(++nextVoice, 0, pcm,
                    outputSampleRate(), 1, gain * gains.get(key)));
        }

        /** Admits generated scene speech without growing an asset cache. Each copied packet is at most half a second. */
        public void playTransient(StreamedMusicPort.SfxPcm pcm) {
            requireCurrent(); java.util.Objects.requireNonNull(pcm);
            if (pcm.sampleRate() < 8000 || pcm.sampleRate() > 192000 || pcm.channels() < 1 || pcm.channels() > 2
                    || pcm.samples().length == 0 || pcm.samples().length % pcm.channels() != 0
                    || pcm.samples().length / pcm.channels() > pcm.sampleRate() / 2
                    || !Float.isFinite(pcm.gain()) || pcm.gain() < 0 || pcm.gain() > 1)
                throw new IllegalArgumentException("Transient scene PCM needs at most 0.5 seconds of mono/stereo audio and gain 0..1");
            if (suppressingRewindReplay() || heldReverseRestore.blocksSfxAdmission()) return;
            voices.removeIf(com.openggf.audio.presentation.SampleBackedVoice::isComplete);
            if (voices.size() >= 32) throw new IllegalStateException("Scene voice overlap exceeds 32 voices");
            var decoded = new com.openggf.audio.presentation.DecodedPcm("transient", pcm.channels(), pcm.sampleRate(), pcm.samples());
            voices.add(com.openggf.audio.presentation.SampleBackedVoice.oneShot(++nextVoice, 0, decoded,
                    outputSampleRate(), 1, pcm.gain()));
        }

        /** Seeking and cancellation silence owned voices without changing the ROM soundtrack. */
        public void stop() { voices.clear(); }
        private void requireCurrent() {
            if (closed || producer != presentationBinding.shadowProducer) throw new IllegalStateException("Scene audio scope has expired");
        }
        private void mix(short[] target, int frames) {
            if (voices.isEmpty()) return;
            int count = frames * 2;
            if (accumulation.length < count) accumulation = new long[count];
            java.util.Arrays.fill(accumulation, 0, count, 0);
            for (var voice : voices) voice.mixInto(accumulation, frames);
            // Reserve headroom by scaling an overloaded block, never flattening its peaks against the PCM rails.
            long peak = 0;
            boolean audibleOverlay = false;
            for (int i = 0; i < count; i++) {
                audibleOverlay |= accumulation[i] != 0;
                peak = Math.max(peak, Math.abs(target[i] + accumulation[i]));
            }
            double gain = audibleOverlay && peak > 30000 ? 30000.0 / peak : 1;
            for (int i = 0; i < count; i++) target[i] = (short) Math.round((target[i] + accumulation[i]) * gain);
            voices.removeIf(com.openggf.audio.presentation.SampleBackedVoice::isComplete);
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            producer.removePcmOverlay(overlay);
            voices.clear(); samples.clear(); gains.clear(); accumulation = new long[0];
        }
    }

    /** Audible device coordinate, or -1 for a device-free presentation. */
    public long consumedPresentationStereoFrames() {
        return presentationBinding.consumedPresentationStereoFrames();
    }

    /** Speaker starvation transitions in the current flushed source coordinate. */
    public long presentationUnderrunCount() {
        return presentationBinding.presentationUnderrunCount();
    }

    /**
     * Opts new presentation sessions into the capture/diagnostic
     * native-compatible PCM stage (see
     * {@link com.openggf.audio.synth.NativeCompatiblePcmOutput}); {@code null}
     * restores the default of none for sessions built later. Ordinary output is
     * unchanged. Enable it before the presentation exists: an existing session
     * keeps its configuration.
     * Package-private: tools reach it through {@link AudioNativePcmCaptureAccess},
     * outside the creator-facing API.
     */
    synchronized void setNativeCompatiblePcmCapture(
            com.openggf.audio.synth.NativeCompatiblePcmOutput.Config config) {
        presentationBinding.setNativeCompatiblePcmCapture(config);
    }

    /** Moves completed native-compatible stereo frames; zero without the stage or a session. */
    synchronized int drainNativeCompatiblePcm(short[] target, int offsetSamples, int maxFrames) {
        return presentationBinding.drainNativeCompatiblePcm(target, offsetSamples, maxFrames);
    }

    /** Presentation sessions built with the stage so far; more than one means its clock origin restarted. */
    synchronized int nativeCompatiblePcmSessions() {
        return presentationBinding.nativeCompatiblePcmSessions();
    }

    public synchronized LiveCaptureAudioHandle beginLiveCaptureAudio(int frameRate) {
        return captureLeases.beginLiveCaptureAudio(frameRate);
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
     * frames each drain asks for. This compatibility API's callers pair one
     * production call with one output picture; repacketization alone cannot
     * change that picture cadence or supply missing audio time. Callers that
     * need a different capture frame rate must realize the producer at that
     * rate first (see {@link #presentationFrameRate()}).
     *
     * <p>Presentation itself remains the caller's outer-frame responsibility:
     * drive exactly one {@link #presentFrame(PresentationMode)} per presented
     * outer frame, then {@link #drainCaptureFrame} that packet once.
     */
    public synchronized void beginCaptureMode(int sampleRate, int frameRate) {
        captureLeases.beginCaptureMode(sampleRate, frameRate);
    }

    /**
     * The frame rate the authoritative presentation producer is clocked at,
     * realizing it if necessary. Offline capture callers must clock both their
     * lease and their container at this rate: it is the rate at which packets
     * are actually presented, and it can differ from a requested capture frame
     * rate (for example a PAL region pins the engine to 50 fps).
     */
    public synchronized int presentationFrameRate() {
        return captureLeases.presentationFrameRate();
    }

    /**
     * Drains one clocked capture interval into {@code target}, returning its
     * stereo-frame count. Queued PCM remains in order across producer packet
     * boundaries; unavailable frames are filled with silence. This lease does
     * not consume the speaker stream or any live-recording lease.
     */
    public synchronized int drainCaptureFrame(short[] target) {
        return captureLeases.drainCaptureFrame(target);
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
        captureLeases.endCaptureMode();
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

    public synchronized void setBackend(AudioBackend backend) {
        invalidateAndReleaseStreamedMusicSession();
        heldReverseRestore.invalidatePreparation();
        // A backend swap replaces the output device; it is not a reason to end
        // a recording of what the engine is playing.
        captureLeases.detachLiveCaptureAudioHandleForRebuild();
        presentationBinding.closeShadowPresentation();
        destroyBackendQuietly(this.backend, "previous AudioBackend");
        this.backend = backend;
        try {
            this.backend.init();
            this.backend.setAudioProfile(sourcePublication.base().profile());
            diagnosticInstallation.installBackendDiagnosticObservers();
            presentationBinding.installBackendPresentationSink();
            LOGGER.info("AudioBackend initialized: " + backend.getClass().getSimpleName());
        } catch (Exception e) {
            AudioDiagnosticObserverException.rethrowIfPresent(e);
            LOGGER.log(Level.SEVERE, "Failed to initialize AudioBackend", e);
            destroyBackendQuietly(this.backend, "failed AudioBackend");
            this.backend = new NullAudioBackend();
            this.backend.init();
            this.backend.setAudioProfile(sourcePublication.base().profile());
            diagnosticInstallation.installBackendDiagnosticObservers();
            presentationBinding.installFailedBackendSink();
        }
    }

    /** Launch path variant that reports initialization failure instead of silently continuing. */
    public void setBackendForLaunch(AudioBackend backend) {
        invalidateAndReleaseStreamedMusicSession();
        heldReverseRestore.invalidatePreparation();
        captureLeases.detachLiveCaptureAudioHandleForRebuild();
        presentationBinding.closeShadowPresentation();
        destroyBackendQuietly(this.backend, "previous AudioBackend");
        this.backend = java.util.Objects.requireNonNull(backend, "backend");
        try {
            this.backend.init();
            this.backend.setAudioProfile(sourcePublication.base().profile());
            diagnosticInstallation.installBackendDiagnosticObservers();
            presentationBinding.installBackendPresentationSink();
            LOGGER.info("AudioBackend initialized: " + backend.getClass().getSimpleName());
        } catch (Exception error) {
            LOGGER.log(Level.SEVERE, "Failed to initialize AudioBackend", error);
            destroyBackendQuietly(this.backend, "failed AudioBackend");
            this.backend = new NullAudioBackend();
            this.backend.init();
            this.backend.setAudioProfile(sourcePublication.base().profile());
            diagnosticInstallation.installBackendDiagnosticObservers();
            presentationBinding.installBackendPresentationSink();
            presentationBinding.markSpeakerFailed();
            throw new IllegalStateException("Audio backend initialization failed", error);
        }
    }

    /**
     * Reinstalls the backend-owned speaker sink after a mode reset. A reset
     * intentionally closes the presentation producer and its sink while
     * retaining the backend instance; the next title/game mode must ask the
     * backend for a fresh sink before the producer is rebuilt.
     */
    public void ensurePresentationSink() {
        presentationBinding.ensurePresentationSink();
    }

    public void replaceFailedPresentationSink(Throwable failure) {
        presentationBinding.handlePresentationSinkFailure(failure);
    }

    private static void destroyBackendQuietly(AudioBackend backend, String description) {
        if (backend == null) {
            return;
        }
        try {
            backend.destroy();
        } catch (Exception e) {
            AudioDiagnosticObserverException.rethrowIfPresent(e);
            LOGGER.log(Level.WARNING, "Failed to destroy " + description, e);
        }
    }

    public synchronized void setAudioProfile(GameAudioProfile audioProfile) {
        sourcePublication.setAudioProfile(audioProfile);
    }

    /** Host-owned complete source selection, including an absent ROM or profile. */
    synchronized void installMatchedSource(Rom rom, GameAudioProfile audioProfile) {
        sourcePublication.installMatchedSource(rom, audioProfile);
    }

    public GameAudioProfile getAudioProfile() {
        return sourcePublication.base().profile();
    }

    public synchronized void setRom(Rom rom) {
        sourcePublication.setRom(rom);
    }

    public void setSoundMap(Map<GameSound, Integer> soundMap) {
        this.soundMap = soundMap;
    }

    public void resetRingSound() {
        logicalPublication.setRingLeft(true);
        recordTimelineCommand(new AudioCommand.ResetRingAlternation(true));
    }

    public AudioCommandTimeline commandTimeline() {
        return commandTimeline;
    }

    public void beginCommandTimelineFrame(long frame) {
        commandTimeline.beginFrame(frame);
        heldReverseRestore.refreshDeferredReverseLogicalSnapshot();
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
        return logicalPublication.captureLogicalSnapshot();
    }

    public void restoreLogicalSnapshot(AudioLogicalSnapshot snapshot) {
        heldReverseRestore.restoreLogicalSnapshot(snapshot);
    }

    private boolean restoreLogicalSnapshotNow(
            AudioLogicalSnapshot snapshot, boolean preservePresentation) {
        return logicalPublication.restoreLogicalSnapshotNow(snapshot, preservePresentation);
    }

    public void playSegaPcm() {
        playSegaPcmCommand(-1);
    }

    public void playSegaPcmCommand(int sourceCommandId) {
        mutatorMusicIntent(AudioMutatorAccess.Kind.COMMAND, sourceCommandId);
        BaseAudioSource source = sourcePublication.base();
        if (suppressingRewindReplay()
                || source.profile() == null || source.rom() == null) {
            return;
        }
        SegaPcmSpec spec = source.profile().getSegaPcmSpec();
        if (spec == null) {
            return;
        }
        try {
            // The runtime-layer profile owns the ROM read; the audio layer
            // must not depend on com.openggf.data (TestArchUnitRules).
            byte[] pcm = source.profile().loadSegaPcm(source.rom());
            if (pcm == null) {
                return;
            }
            recordTimelineCommand(new AudioCommand.PlaySegaPcm(
                    sourceCommandId, pcm, spec.sampleRate()));
        } catch (Exception e) {
            AudioDiagnosticObserverException.rethrowIfPresent(e);
            LOGGER.log(Level.WARNING, "Failed to play SEGA PCM sample", e);
        }
    }

    public void stopSegaPcm() {
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.StopRawPcm());
    }

    /** S3K FE: remove raw PCM now and retain one global driver stop. */
    public void stopSegaPcmAndRetainGlobalStop(int sourceCommandId) {
        mutatorMusicIntent(AudioMutatorAccess.Kind.STOP, sourceCommandId);
        if (suppressingRewindReplay()) {
            return;
        }
        logicalPublication.setRingLeft(true);
        recordTimelineCommand(
                new AudioCommand.StopSegaPcmAndRetainGlobalStop(
                        sourceCommandId));
    }

    public void retainGlobalStop(int sourceCommandId) {
        mutatorMusicIntent(AudioMutatorAccess.Kind.STOP, sourceCommandId);
        if (suppressingRewindReplay()) {
            return;
        }
        logicalPublication.setRingLeft(true);
        recordTimelineCommand(
                new AudioCommand.RetainGlobalStop(sourceCommandId));
    }

    public void stopSmpsSfx(int sourceCommandId) {
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(
                new AudioCommand.StopSmpsSfx(sourceCommandId));
    }

    public void silencePsg(int sourceCommandId) {
        mutatorMusicIntent(AudioMutatorAccess.Kind.COMMAND, sourceCommandId);
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.SilencePsg(sourceCommandId));
    }

    public void recordReferenceLimitation(
            int sourceCommandId, String reason) {
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.ReferenceLimitation(
                sourceCommandId, reason));
    }

    public void playStandaloneMusic(
            AbstractSmpsData data, DacData dac) {
        ensureStandalonePresentation();
        BaseAudioSource base = sourcePublication.base();
        int musicId = data.getId();
        int maxFrames = (outputSampleRate() + presentationBinding.configuredFrameRate() - 1)
                / presentationBinding.configuredFrameRate();
        var music = presentationBinding.shadowFactory.musicSmps(
                standaloneGameId, musicId, standaloneVoiceId++,
                data, dac, base.config(),
                AudioSourceDescriptor.baseMusic(musicId), maxFrames);
        presentationBinding.shadowCommands.submit(
                new AudioPresentationCommand.ReplaceMusic(music),
                () -> true, this::drainShadowCommandsAtOwnerBoundary);
    }

    boolean standaloneMusicComplete() {
        ensureStandalonePresentation();
        return presentationBinding.shadowSmpsSession != null && presentationBinding.shadowSmpsSession.musicComplete();
    }

    public void playStandaloneSfx(
            AbstractSmpsData data, DacData dac, float pitch) {
        ensureStandalonePresentation();
        BaseAudioSource base = sourcePublication.base();
        GameAudioProfile profile = base.profile();
        int sfxId = data.getId();
        var key = new com.openggf.audio.presentation.SmpsAssetKey(
                standaloneGameId,
                com.openggf.audio.presentation.SmpsAssetKey.Route.BASE_ID,
                sfxId, null);
        presentationBinding.shadowFactory.registerSmpsSfxAsset(
                key, base.generation(), data, dac, base.config(),
                new SmpsSfxPlaybackPolicy(
                        profile.getSfxPriority(sfxId),
                        profile.isSpecialSfx(sfxId),
                        profile.isContinuousSfx(sfxId)));
        int continuous = profile.isContinuousSfx(sfxId)
                ? sfxId : 0;
        int maxFrames = (outputSampleRate() + presentationBinding.configuredFrameRate() - 1)
                / presentationBinding.configuredFrameRate();
        var source = presentationBinding.shadowFactory.resolveSmpsSfx(
                standaloneVoiceId++, key, base.generation(),
                Math.max(1, Math.round(pitch * 65_536.0f)),
                profile.getSfxPriority(sfxId), continuous,
                data.getChannels() + data.getPsgChannels(), maxFrames);
        presentationBinding.shadowCommands.submit(
                new AudioPresentationCommand.AddSmpsSfx(source),
                () -> true, this::drainShadowCommandsAtOwnerBoundary);
    }

    public void stopStandalonePlayback() {
        ensureStandalonePresentation();
        presentationBinding.shadowCommands.submit(new AudioPresentationCommand.StopMusic(),
                () -> true, this::drainShadowCommandsAtOwnerBoundary);
        presentationBinding.shadowCommands.submit(new AudioPresentationCommand.StopAllSfx(),
                () -> true, this::drainShadowCommandsAtOwnerBoundary);
        presentationBinding.shadowCommands.submit(new AudioPresentationCommand.StopRawPcm(),
                () -> true, this::drainShadowCommandsAtOwnerBoundary);
    }

    SmpsSequencerConfig bindLegacyConfigToPresentationOwner(
            SmpsSequencerConfig config) {
        presentationBinding.ensureShadowPresentation();
        String gameId = config.getCoordFlagHandler() == null
                ? (sourcePublication.base().profile() != null
                ? sourcePublication.base().profile().presentationGameId() : "base")
                : "s3k";
        return presentationBinding.shadowFactory.legacySequencerConfig(gameId, config);
    }

    private void ensureStandalonePresentation() {
        if (standaloneGameId == null) {
            throw new IllegalStateException(
                    "not a standalone presentation manager");
        }
        presentationBinding.ensureShadowPresentation();
    }

    public AudioReplayScope beginRewindReplay(int fromFrame, int targetFrame, AudioReplayReason reason) {
        if (rewindReplaySuppressionDepth == 0 && backend != null) {
            backend.beginStreamedOverrideReplayBypass();
        }
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
                    if (rewindReplaySuppressionDepth == 0 && backend != null) {
                        backend.endStreamedOverrideReplayBypass();
                    }
                }
            }
        };
    }

    public boolean isRewindReplaySuppressed() {
        return silentSimulation || rewindReplaySuppressionDepth > 0;
    }

    public void replayTimelineCommand(AudioCommand command) {
        if (backend == null || command == null) {
            return;
        }
        switch (command) {
            case AudioCommand.PlayMusic playMusic -> replayMusic(playMusic);
            case AudioCommand.PlayNamespacedMusic namespaced -> requireNamespacedMusicAccepted(namespaced.track());
            case AudioCommand.PlayNamespacedSfx namespaced ->
                    requireNamespacedSfxAccepted(namespaced.sfx());
            case AudioCommand.PlaySfx playSfx -> replaySfx(playSfx);
            case AudioCommand.FadeOutMusic fade -> backend.fadeOutMusic(fade.steps(), fade.delay());
            case AudioCommand.StopMusic ignored -> backend.stopPlayback();
            case AudioCommand.StopAllSfx ignored -> backend.stopAllSfx();
            case AudioCommand.StopSmpsSfx ignored -> backend.stopAllSfx();
            case AudioCommand.SilencePsg ignored -> {
                // The authoritative presentation owns physical PSG writes.
            }
            case AudioCommand.RetainGlobalStop ignored ->
                    {
                        logicalPublication.setRingLeft(true);
                        backend.stopPlayback();
                    }
            case AudioCommand.PlaySegaPcm ignored -> {
                // The authoritative presentation owns raw SEGA PCM.
            }
            case AudioCommand.StopRawPcm ignored -> {
                // The authoritative presentation owns raw SEGA PCM.
            }
            case AudioCommand.StopSegaPcmAndRetainGlobalStop ignored -> {
                logicalPublication.setRingLeft(true);
                backend.stopPlayback();
            }
            case AudioCommand.ReferenceLimitation ignored -> {
                // Explicitly unsupported commands perform no mutation.
            }
            case AudioCommand.EndMusicOverride end -> backend.endMusicOverride(end.musicId());
            case AudioCommand.RestoreMusic ignored -> backend.restoreMusic();
            case AudioCommand.SetSpeedShoes speed -> backend.setSpeedShoes(speed.enabled());
            case AudioCommand.SetSpeedMultiplier speed -> backend.setSpeedMultiplier(speed.multiplier());
            case AudioCommand.SetMusicTempoPercent tempo -> backend.setMusicTempoPercent(tempo.percent());
            case AudioCommand.ChangeMusicTempo tempo -> backend.changeMusicTempo(tempo.dividingTiming());
            case AudioCommand.ResetRingAlternation reset -> logicalPublication.setRingLeft(reset.ringLeft());
        }
    }

    public void replayTimelineCommandLogically(AudioCommand command) {
        heldReverseRestore.replayTimelineCommandLogically(command);
    }

    private void replayMusic(AudioCommand.PlayMusic command) {
        switch (command.route()) {
            case BASE_SMPS -> {
                BaseAudioSource source = sourcePublication.base();
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
                DonorAudioSource source = sourcePublication.donor(
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
                BaseAudioSource source = sourcePublication.base();
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
                BaseAudioSource source = sourcePublication.base();
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
                DonorAudioSource source = sourcePublication.donor(
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
        return silentSimulation || rewindReplaySuppressionDepth > 0;
    }

    public boolean afterRewindRestore(
            int frame, AudioPresentationPolicy policy) {
        return heldReverseRestore.afterRewindRestore(frame, policy);
    }

    public void beginReverseAudioPresentation() {
        heldReverseRestore.beginReverseAudioPresentation();
    }

    public boolean isReverseAudioPresentationActive() {
        return heldReverseRestore.isReverseAudioPresentationActive();
    }

    /** Audible reverse ownership, including a scoped captured presentation.
     * Logical rewind restore decisions must keep using {@link #isReverseAudioPresentationActive()}. */
    public boolean isReverseAudioOutputActive() {
        return heldReverseRestore.isReverseAudioOutputActive();
    }

    /** Records actual final forward PCM without changing developer rewind history.
     * Session-owned public replay capabilities retain and close this exact producer handle. */
    public AudioReplay recordPresentationAudio(int maxSeconds) {
        presentationBinding.ensureShadowPresentation();
        return new AudioReplay(presentationBinding.shadowProducer.recordReplay(maxSeconds));
    }

    /**
     * Ends the held reverse presentation and publishes the selected logical
     * target.
     *
     * <p>Successful producer return separates attempt-owned preparation cleanup
     * from manager publication. Exceptions inside the producer may follow partial
     * restoration or cursor consumption; ledger publication can also fail after
     * its counter/ring writes. These existing partial effects are preserved.
     */
    public boolean endReverseAudioPresentation() {
        return heldReverseRestore.endReverseAudioPresentation();
    }

    private void resetLogicalRestorePublicationCount() {
        logicalPublication.resetLogicalRestorePublicationCount();
    }

    /**
     * Delegates the fixed ledger effect without moving held selection or release policy.
     */
    private void publishHeldRestoreLedger(
            AudioLogicalSnapshot selected,
            Map<GameSound, DonorSfxBinding> publishedBindings) {
        logicalPublication.publishHeldRestoreLedger(selected, publishedBindings);
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
        return heldReverseRestore.preparePostBoundaryReverseRelease();
    }

    /**
     * Prepares the one manager-owned logical restore at a held-rewind commit
     * boundary without consuming the reverse PCM cursor.
     */
    public boolean commitDeferredReverseLogicalRestore() {
        return heldReverseRestore.commitDeferredReverseLogicalRestore();
    }

    public void setReversePlaybackRate(double rate) {
        presentationBinding.ensureShadowPresentation();
        presentationBinding.shadowProducer.setReverseRate(rate);
    }

    /**
     * Forward playback rate, 1.0 being real time. Owners that run the
     * simulation faster than real time (visual Trace Test Mode fast-forward)
     * set this to the same rate so the picture and the audio speed up
     * together; every such owner must restore 1.0 when it stops, since the
     * rate outlives the consumer that set it.
     */
    public void setForwardPlaybackRate(double rate) {
        presentationBinding.ensureShadowPresentation();
        presentationBinding.shadowProducer.setForwardRate(rate);
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
        presentationBinding.ensureShadowPresentation();
        presentationBinding.shadowProducer.clearHistory();
        presentationBinding.shadowParity.historyBoundary();
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
        presentationBinding.ensureShadowPresentation();
        presentationBinding.shadowProducer.setHistoryArmed(armed);
    }

    public void presentFrame(PresentationMode mode) {
        presentationBinding.ensureShadowPresentation();
        AudioPresentationProducer.PresentationResult result =
                presentationBinding.shadowProducer.present(commandTimeline.currentFrame(), mode);
        if (result == AudioPresentationProducer.PresentationResult
                .REQUEST_REJECTED) {
            return;
        }
        audioFrameAdvanced = true;
        presentationBinding.shadowParity.presented(mode);
        if (shadowRestoreRequested) {
            shadowRestoreRequested = false;
            mirrorShadowCommand(() -> presentationBinding.shadowCommands.submit(
                    new AudioPresentationCommand.RestoreMusicOverride(),
                    () -> true,
                    this::drainShadowCommandsAtOwnerBoundary));
        }
    }

    /**
     * Immutable diagnostic counters for the temporary shadow-parity gate.
     */
    AudioPresentationParityProbe.Snapshot shadowParitySnapshot() {
        presentationBinding.ensureShadowPresentation();
        return presentationBinding.shadowParity.snapshot();
    }

    LiveCaptureAudioHandle attachShadowCaptureForTesting(int frameRate) {
        presentationBinding.ensureShadowPresentation();
        return presentationBinding.shadowProducer.attachCapture(frameRate);
    }

    AudioLogicalSnapshot deferredReverseLogicalSnapshotForTesting() {
        return heldReverseRestore.selectedSnapshot();
    }

    int logicalRestorePublicationsForTesting() {
        return logicalPublication.logicalRestorePublications();
    }

    void resetLogicalRestorePublicationsForTesting() {
        logicalPublication.resetLogicalRestorePublicationCount();
    }

    void failNextReverseReleaseForTesting(ReverseReleaseFailurePoint point) {
        heldReverseRestore.armReleaseFailure(point);
    }

    AudioPresentationTuning shadowTuningForTesting() {
        presentationBinding.ensureShadowPresentation();
        return presentationBinding.shadowTuning;
    }

    SmpsCoordFlagHandlerOwner presentationCoordFlagHandlersForTesting() {
        presentationBinding.ensureShadowPresentation();
        return presentationBinding.presentationCoordFlagHandlers;
    }

    SmpsDriverSnapshot shadowSmpsDriverSnapshotForTesting() {
        presentationBinding.ensureShadowPresentation();
        return presentationBinding.shadowSmpsSession.captureLogicalSnapshot();
    }

    AudioPresentationSourceFactory shadowFactoryForTesting() {
        presentationBinding.ensureShadowPresentation();
        return presentationBinding.shadowFactory;
    }

    ReleaseStateForTesting releaseStateForTesting() {
        presentationBinding.ensureShadowPresentation();
        AbstractSmpsAudioBackend.StateForTesting backendState =
                backend instanceof AbstractSmpsAudioBackend smpsBackend
                        ? smpsBackend.stateForTesting() : null;
        return new ReleaseStateForTesting(
                captureLogicalSnapshot(),
                backendState,
                presentationBinding.shadowProducer.transactionFingerprint());
    }

    record ReleaseStateForTesting(
            AudioLogicalSnapshot logical,
            AbstractSmpsAudioBackend.StateForTesting backend,
            AudioPresentationProducer.TransactionFingerprint producer) {
    }

    void submitShadowRawPcmForTesting(
            byte[] pcm, int sourceSampleRate) {
        presentationBinding.ensureShadowPresentation();
        presentationBinding.shadowResolver.submitRawPcm(pcm, sourceSampleRate);
    }

    public void restoreMusic() {
        mutatorMusicIntent(AudioMutatorAccess.Kind.RESTORE, 0);
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.RestoreMusic(AudioCommand.RestoreCause.EXPLICIT));
        if (sendLiveBackendCommands()) {
            backend.restoreMusic();
        }
    }

    public void setSpeedShoes(boolean enabled) {
        mutatorMusicIntent(AudioMutatorAccess.Kind.SPEED_SHOES, enabled ? 1 : 0);
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.SetSpeedShoes(enabled));
        if (sendLiveBackendCommands()) {
            backend.setSpeedShoes(enabled);
        }
    }

    public void setSpeedMultiplier(int multiplier) {
        mutatorMusicIntent(AudioMutatorAccess.Kind.SPEED_MULTIPLIER, multiplier);
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.SetSpeedMultiplier(multiplier));
        if (sendLiveBackendCommands()) {
            backend.setSpeedMultiplier(multiplier);
        }
    }

    /**
     * Models a ROM {@code PlayMusic} call whose operand is a native sound id
     * rather than a music id.
     *
     * <p>Sonic 2's {@code PlayMusic} is only the music mailbox
     * ({@code Sound_Queue.Music0}/{@code Music1}); the disassembly says so
     * outright, "Despite the name, this can actually be used for playing
     * sounds" (docs/s2disasm/s2.asm:1517-1527). Several ROM sites use it with
     * an {@code SndID_} operand, so the byte reaches the driver through
     * {@code QueueToPlay} instead of the three-entry SFX queue that
     * {@code sndDriverInput} services (:1270-1332). The driver then classifies
     * the byte by range and sends an SFX id on to
     * {@code zPlaySound_CheckRing} exactly as if it had arrived by the other
     * route (docs/s2disasm/s2.sounddriver.asm:1565-1571, :2116-2135), so the
     * ring speaker alternation, the gloop toggle and the spindash frequency
     * step all still apply.
     *
     * <p>{@link #playMusic(int)} deliberately classifies its operand as music,
     * because the engine's own music ids overlap the native SFX range. This
     * entry point is for the other case: the caller is passing a native ROM
     * request byte, so the payload takes the driver's own classification.
     */
    public void playMusicMailboxNativeRequest(int nativeRequestId) {
        mutatorSfxIntent(nativeRequestId);
        if (suppressingRewindReplay()) {
            return;
        }
        BaseAudioSource source = sourcePublication.base();
        if (source.profile() != null) {
            presentationBinding.ensureShadowPresentation();
            if (presentationBinding.shadowRequestService != null) {
                diagnosticInstallation.requestObserver().onRequested(sfxRequestClass(nativeRequestId),
                        nativeRequestId);
                presentationBinding.shadowRequestService.submitMusic(nativeRequestId,
                        commandForNativeRequest(source, nativeRequestId));
                return;
            }
        }
        // Without the request service there is no mailbox to model, so the
        // fallback presentation plays the sound the driver would have reached.
        // A profile rule names the music a driver reaches for a request id
        // outside its music range; the request keeps its raw observed identity.
        if (source.profile() instanceof NativeMailboxMusicRoutes routes) {
            int musicId = routes.musicForNativeRequest(nativeRequestId);
            if (musicId >= 0) {
                diagnosticInstallation.requestObserver().onRequested(sfxRequestClass(nativeRequestId),
                        nativeRequestId);
                admitMusicWithoutRequestService(source, musicId);
                return;
            }
        }
        playSfx(nativeRequestId);
    }

    /** Internal native PauseGame operation; preserves chip time and mailbox ownership. */
    void requestNativePause(boolean paused) {
        heldReverseRestore.requestNativePause(paused);
    }

    void replayNativePause(boolean paused, AudioPresentationForwardService.Snapshot forward) {
        heldReverseRestore.replayNativePause(paused, forward);
    }

    void advanceNativeHistoryCursor(int cursor) {
        heldReverseRestore.advanceNativeHistoryCursor(cursor);
    }

    public void playMusic(int musicId) {
        if (!LevelMusicAccess.allowsEntryMusic(this)) return;
        mutatorMusicIntent(musicId >= 0xE0 ? AudioMutatorAccess.Kind.COMMAND : AudioMutatorAccess.Kind.MUSIC, musicId);
        if (suppressingRewindReplay()) {
            return;
        }
        diagnosticInstallation.requestObserver().onRequested(musicId >= 0xE0
                ? AudioRequestObserver.RequestClass.COMMAND
                : AudioRequestObserver.RequestClass.MUSIC, musicId);
        BaseAudioSource source = sourcePublication.base();
        GameAudioProfile profile = source.profile();
        if (profile != null) {
            presentationBinding.ensureShadowPresentation();
            if (presentationBinding.shadowRequestService != null) {
                presentationBinding.shadowRequestService.submitMusic(
                        musicId, commandForNativeMusicRequest(source, musicId));
                return;
            }
        }
        admitMusicWithoutRequestService(source, musicId);
    }

    /** {@link #playMusic(int)} after its request observation, without a request service. */
    private void admitMusicWithoutRequestService(BaseAudioSource source, int musicId) {
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
        GameAudioProfile.MusicDuringOverridePolicy musicDuringOverridePolicy =
                profile != null
                        ? profile.getMusicDuringOverridePolicy()
                        : GameAudioProfile.MusicDuringOverridePolicy
                                .REPLACE_IMMEDIATELY;
        GameAudioProfile.MusicOverrideRetriggerPolicy retriggerPolicy =
                profile != null
                        ? profile.getMusicOverrideRetriggerPolicy()
                        : GameAudioProfile.MusicOverrideRetriggerPolicy.IGNORE;

        if (source.loader() != null) {
            presentationBinding.ensureShadowPresentation();
            SmpsAssetKey key = new SmpsAssetKey(
                    baseGameId(source), SmpsAssetKey.Route.BASE_MUSIC,
                    musicId, null);
            boolean registered = presentationBinding.shadowFactory.findRegisteredSmpsMusicAsset(
                    key, source.generation()) != null;
            AbstractSmpsData data = null;
            if (!registered) {
                data = source.loader().loadMusic(musicId);
                if (data != null) {
                    presentationBinding.shadowFactory.registerSmpsMusicAsset(
                            key, source.generation(), data,
                            source.dac(), source.config());
                    registered = true;
                }
            }
            if (registered) {
                recordTimelineCommand(new AudioCommand.PlayMusic(
                        musicId, AudioCommand.MusicRoute.BASE_SMPS, override, null,
                        musicDuringOverridePolicy, retriggerPolicy));
                if (sendLiveBackendCommands()) {
                    var playback = presentationBinding.shadowFactory
                            .requireRegisteredSmpsMusicPlayback(
                                    key, source.generation());
                    backend.playStreamedMusicOrElse(musicId, () -> {
                        backend.prepareLogicalMusicSource(AudioSourceDescriptor.baseMusic(musicId));
                        backend.playSmps(playback.program(), playback.dac());
                    });
                }
                return;
            }
        }
        recordTimelineCommand(new AudioCommand.PlayMusic(
                musicId, AudioCommand.MusicRoute.FALLBACK_WAV, override, null,
                musicDuringOverridePolicy, retriggerPolicy));
        if (sendLiveBackendCommands()) {
            backend.playStreamedMusicOrElse(musicId, () -> {
                backend.prepareLogicalMusicSource(AudioSourceDescriptor.fallbackMusic(musicId));
                backend.playMusic(musicId);
            });
        }
    }

    /** Plays an exact namespaced streamed track without allocating a stock music id. */
    public boolean playNamespacedMusic(StreamedMusicPort.TrackRef track) {
        mutatorMusicIntent(AudioMutatorAccess.Kind.FOREIGN, -1);
        java.util.Objects.requireNonNull(track, "track");
        if (suppressingRewindReplay()) {
            return false;
        }
        if (!streamedMusicPort.hasTrack(track)) {
            throw new IllegalArgumentException("Unknown namespaced streamed track: " + track);
        }
        recordTimelineCommand(new AudioCommand.PlayNamespacedMusic(track));
        return true;
    }

    /**
     * Replay guard: a recorded namespaced track must still resolve on replay, or
     * the timeline is being replayed against a different mod set than recorded it.
     */
    private void requireNamespacedMusicAccepted(StreamedMusicPort.TrackRef track) {
        if (!streamedMusicPort.hasTrack(track)) {
            throw new IllegalArgumentException(
                    "Unknown namespaced streamed track: " + track);
        }
    }

    /**
     * Replay guard matching the track contract: creator PCM must still be
     * present when a recorded one-shot is replayed.
     */
    private void requireNamespacedSfxAccepted(StreamedMusicPort.SfxRef sfx) {
        if (!streamedMusicPort.hasSfx(sfx)) {
            throw new IllegalArgumentException(
                    "Unknown namespaced streamed SFX: " + sfx);
        }
    }

    /**
     * Plays an exact namespaced one-shot through the deterministic audio timeline.
     * Creator one-shots resolve to ordinary sample voices, so they record and
     * replay like stock SFX rather than bypassing rewind.
     */
    public boolean playNamespacedSfx(StreamedMusicPort.SfxRef sfx) {
        java.util.Objects.requireNonNull(sfx, "sfx");
        if (suppressingRewindReplay() || heldReverseRestore.blocksSfxAdmission()) return false;
        // Preflight only: the presentation layer materializes the one-shot
        // from the recorded command as an ordinary sample voice.
        if (!streamedMusicPort.hasSfx(sfx)) {
            return false;
        }
        recordTimelineCommand(new AudioCommand.PlayNamespacedSfx(sfx));
        return true;
    }

    public boolean playMusic(GameMusic music) {
        Integer musicId = resolveMusic(sourcePublication.base().profile(), music);
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
        if (sfxName == null) {
            recordTimelineCommand(new AudioCommand.PlaySfx(
                    -1, null, AudioCommand.SfxRoute.FALLBACK_NAME,
                    pitch, null));
            if (sendLiveBackendCommands()) {
                backend.playSfx(null, pitch);
            }
            return;
        }
        BaseAudioSource source = sourcePublication.base();
        if (source.loader() != null) {
            if (sfxName.isBlank()) {
                AbstractSmpsData loaded = source.loader().loadSfx(sfxName);
                if (loaded != null && loaded.getId() >= 0) {
                    playLoadedBaseSfx(source, loaded, pitch);
                    return;
                }
                recordTimelineCommand(new AudioCommand.PlaySfx(
                        -1, sfxName, AudioCommand.SfxRoute.FALLBACK_NAME,
                        pitch, null));
                if (sendLiveBackendCommands()) {
                    backend.playSfx(sfxName, pitch);
                }
                return;
            }
            if (!hasCatalogDependencies(source.dac(), source.config())) {
                AbstractSmpsData sfx = source.loader().loadSfx(sfxName);
                if (sfx != null) {
                    recordTimelineCommand(new AudioCommand.PlaySfx(
                            -1, sfxName,
                            AudioCommand.SfxRoute.BASE_SMPS_NAME,
                            pitch, null));
                    if (sendLiveBackendCommands()) {
                        backend.playSfxSmps(sfx, source.dac(), pitch);
                    }
                    return;
                }
                recordTimelineCommand(new AudioCommand.PlaySfx(
                        -1, sfxName, AudioCommand.SfxRoute.FALLBACK_NAME,
                        pitch, null));
                if (sendLiveBackendCommands()) {
                    backend.playSfx(sfxName, pitch);
                }
                return;
            }
            presentationBinding.ensureShadowPresentation();
            var captured = sourcePublication.baseSfxSource(source);
            SmpsAssetKey key = new SmpsAssetKey(
                    captured.gameId(), SmpsAssetKey.Route.BASE_NAME,
                    -1, sfxName);
            if (ensureRegisteredSmpsSfx(key, captured)) {
                AudioCommand.PlaySfx command = new AudioCommand.PlaySfx(
                        -1, sfxName, AudioCommand.SfxRoute.BASE_SMPS_NAME,
                        pitch, null);
                recordRegisteredSmpsSfxCommand(command, captured);
                if (sendLiveBackendCommands()) {
                    var playback = presentationBinding.shadowFactory
                            .requireRegisteredSmpsSfxPlayback(
                                    key, captured.dependencyGeneration());
                    dispatchLiveRegisteredSfx(playback, pitch);
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
        playSfx(sound, pitch, false);
    }

    public void playSecondarySfx(GameSound sound) {
        playSfx(sound, 1.0f, true);
    }

    /** Native operand ingress for the secondary sound mailbox, independent of GameSound mappings. */
    void playSecondaryNativeRequest(int nativeId) {
        mutatorSfxIntent(nativeId);
        if (suppressingRewindReplay()) {
            return;
        }
        BaseAudioSource source = sourcePublication.base();
        GameAudioProfile profile = source.profile();
        AudioCommand command = null;
        if (profile != null) {
            presentationBinding.ensureShadowPresentation();
            command = commandForNativeRequest(source, nativeId);
            if (presentationBinding.shadowRequestService != null) {
                diagnosticInstallation.requestObserver().onRequested(sfxRequestClass(nativeId), nativeId);
                presentationBinding.shadowRequestService.submitSecondarySound(nativeId, command);
                return;
            }
        }
        // Fallback consequences may call public controls; the raw intent already belongs
        // to this submission and must not be observed again through those controls.
        AudioCommand fallbackCommand = command;
        mutatorAccess.submit(() -> {
            if (fallbackCommand instanceof AudioCommand.PlayMusic music) {
                diagnosticInstallation.requestObserver().onRequested(sfxRequestClass(nativeId), nativeId);
                admitMusicWithoutRequestService(source, music.musicId());
                return;
            }
            if (profile != null && profile.handleSystemCommand(nativeId, this)) {
                diagnosticInstallation.requestObserver().onRequested(AudioRequestObserver.RequestClass.COMMAND, nativeId);
                return;
            }
            diagnosticInstallation.requestObserver().onRequested(sfxRequestClass(nativeId), nativeId);
            if (isRingSpeakerToggleRequest(nativeId)) {
                playRingWithoutRequestService(nativeId, 1.0f);
                return;
            }
            playBaseSfx(source, nativeId, 1.0f);
        });
    }

    private void playSfx(GameSound sound, float pitch, boolean secondaryRequest) {
        Integer observedId = sound == GameSound.RING
                ? soundMap == null ? null : soundMap.get(GameSound.RING_RIGHT)
                : soundMap == null ? null : soundMap.get(sound);
        if (observedId != null) mutatorSfxIntent(observedId);
        if (suppressingRewindReplay()) {
            return;
        }
        Integer rawSoundId = sound == GameSound.RING
                ? soundMap == null ? null : soundMap.get(GameSound.RING_RIGHT)
                : soundMap == null ? null : soundMap.get(sound);
        if (rawSoundId != null) {
            diagnosticInstallation.requestObserver().onRequested(sfxRequestClass(rawSoundId), rawSoundId);
            presentationBinding.ensureShadowPresentation();
            if (presentationBinding.shadowRequestService != null) {
                AudioCommand command = commandForNativeRequest(
                        sourcePublication.base(), rawSoundId);
                if (secondaryRequest) {
                    presentationBinding.shadowRequestService.submitSecondarySound(rawSoundId, command);
                } else {
                    presentationBinding.shadowRequestService.submitSound(rawSoundId, command);
                }
                return;
            }
        }
        if (sound == GameSound.RING) {
            playRingWithoutRequestService(rawSoundId, pitch);
            return;
        }

        playGameSfxResolved(sound, pitch);
    }

    private void playRingWithoutRequestService(Integer rawSoundId, float pitch) {
        // Suppression precedes the driver's ring-speaker selection.
        // The registry still rejects all other SFX at admission.
        if (heldReverseRestore.blocksRingAdmission()) {
            observeBlockedRingAdmission(rawSoundId);
            return;
        }
        logicalPublication.playFallbackRing(pitch);
    }

    /** Reports the caller-owned discard before ring-speaker selection mutates. */
    private void observeBlockedRingAdmission(Integer rawSoundId) {
        if (rawSoundId == null || diagnosticInstallation.admissionObserver() == AudioAdmissionObserver.NONE) {
            return;
        }
        GameAudioProfile profile = sourcePublication.base().profile();
        int priority = profile == null
                ? SmpsRequestAdmissionPolicy.NO_PRIORITY
                : profile.getSfxPriority(rawSoundId);
        boolean special = profile != null && profile.isSpecialSfx(rawSoundId);
        SmpsAdmissionContext context = new SmpsAdmissionContext(
                rawSoundId, rawSoundId, priority,
                SmpsRequestAdmissionPolicy.NO_PRIORITY, special, false);
        AdmissionResult result = new AdmissionResult(false,
                RejectionReason.BLOCKED,
                context.priorityBefore(), context.priorityBefore(),
                context.resolvedSoundId());
        AudioDiagnosticObserverException.invoke(() ->
                diagnosticInstallation.admissionObserver().onDecision(
                        new AudioAdmissionObserver.AudioAdmissionDecision(
                                context, result)));
    }

    private void playGameSfxResolved(GameSound sound, float pitch) {
        BaseAudioSource base = sourcePublication.base();
        GameAudioProfile profile = base.profile();
        float effectivePitch = profile != null
                ? profile.adjustSfxPitch(sound, pitch) : pitch;
        boolean played = false;
        if (soundMap != null && soundMap.containsKey(sound)) {
            played = playBaseSfx(
                    base, soundMap.get(sound), effectivePitch);
        }
        if (!played) {
            DonorSfxBinding binding = sourcePublication.soundBinding(sound);
            if (binding != null) {
                DonorAudioSource donor = sourcePublication.donor(
                        binding.gameId());
                if (donor != null) {
                    donor = completeLegacyDonorSource(
                            binding.gameId(), donor);
                    if (binding.sfxId() < 0) {
                        AbstractSmpsData loaded = donor.loader().loadSfx(
                                binding.sfxId());
                        if (loaded != null && loaded.getId() >= 0) {
                            played = playLoadedDonorSfx(
                                    binding.gameId(), donor, loaded,
                                    effectivePitch, sound.name());
                        }
                    } else if (!hasCatalogDependencies(
                            donor.dac(), donor.config())) {
                        AbstractSmpsData sfx = donor.loader().loadSfx(
                                binding.sfxId());
                        if (sfx != null) {
                            recordTimelineCommand(new AudioCommand.PlaySfx(
                                    binding.sfxId(), sound.name(),
                                    AudioCommand.SfxRoute.DONOR_SMPS,
                                    effectivePitch, binding.gameId()));
                            if (sendLiveBackendCommands()) {
                                backend.playSfxSmps(
                                        sfx, donor.dac(), effectivePitch);
                            }
                            played = true;
                        }
                        if (played) {
                            return;
                        }
                    } else {
                        presentationBinding.ensureShadowPresentation();
                        var captured = sourcePublication.donorSfxSource(
                                binding.gameId(), donor);
                        SmpsAssetKey key = new SmpsAssetKey(
                                captured.gameId(),
                                SmpsAssetKey.Route.DONOR_ID,
                                binding.sfxId(), null);
                        if (ensureRegisteredSmpsSfx(key, captured)) {
                            AudioCommand.PlaySfx command =
                                    new AudioCommand.PlaySfx(
                                            binding.sfxId(),
                                            sound.name(),
                                            AudioCommand.SfxRoute.DONOR_SMPS,
                                            effectivePitch,
                                            binding.gameId());
                            recordRegisteredSmpsSfxCommand(
                                    command, captured);
                            if (sendLiveBackendCommands()) {
                                playLiveRegisteredDonorSfx(
                                        key, captured, effectivePitch);
                            }
                            played = true;
                        }
                    }
                }
            }
        }
        if (!played) {
            AudioCommand.SfxRoute route = sound == GameSound.RING_LEFT || sound == GameSound.RING_RIGHT
                    ? AudioCommand.SfxRoute.RING_RESOLVED
                    : AudioCommand.SfxRoute.FALLBACK_NAME;
            int resolvedId = route == AudioCommand.SfxRoute.RING_RESOLVED
                    && soundMap != null && soundMap.containsKey(sound)
                    ? soundMap.get(sound) : -1;
            recordTimelineCommand(new AudioCommand.PlaySfx(
                    resolvedId, sound.name(), route, effectivePitch, null));
            if (sendLiveBackendCommands()) {
                backend.playSfx(sound.name(), effectivePitch);
            }
        }
    }

    public boolean playSfx(int sfxId) {
        return playSfx(sfxId, 1.0f);
    }

    public boolean playSfx(int sfxId, float pitch) {
        mutatorSfxIntent(sfxId);
        if (suppressingRewindReplay()) {
            return false;
        }
        BaseAudioSource source = sourcePublication.base();
        GameAudioProfile profile = source.profile();
        if (profile != null) {
            presentationBinding.ensureShadowPresentation();
            if (presentationBinding.shadowRequestService != null) {
                diagnosticInstallation.requestObserver().onRequested(sfxRequestClass(sfxId), sfxId);
                var command = commandForNativeRequest(source, sfxId, pitch);
                if (LevelMusicAccess.allowsEntryRequest(this, command))
                    presentationBinding.shadowRequestService.submitSound(sfxId, command);
                return true;
            }
        }
        if (profile != null && profile.handleSystemCommand(sfxId, this)) {
            diagnosticInstallation.requestObserver().onRequested(
                    AudioRequestObserver.RequestClass.COMMAND, sfxId);
            return true;
        }
        if (isRingSpeakerToggleRequest(sfxId)) {
            // Mirrors the sound driver's ring-speaker toggle, which keys on
            // the raw ring request id rather than on the caller: every ROM
            // ring collect (level GiveRing, the special-stage sphere/ring
            // routine, badniks that award rings) sends the same right-side id
            // and the driver alternates it with the left-side id itself
            // (S3K: zPlaySound_CheckRing, skdisasm/Sound/Z80 Sound Driver.asm
            // :1919-1925; S2: zPlaySound_CheckRing, s2disasm/s2.sounddriver.asm
            // :2127-2135; S1: Sound_ChkRing, s1disasm/s1.sounddriver.asm
            // :984-991). Requesting the raw id therefore takes the same
            // alternation as GameSound.RING; only an explicit left-side id
            // bypasses the toggle, as in the ROM.
            playSfx(GameSound.RING, pitch);
            return true;
        }
        diagnosticInstallation.requestObserver().onRequested(sfxRequestClass(sfxId), sfxId);
        return playBaseSfx(sourcePublication.base(), sfxId, pitch);
    }

    private AudioCommand commandForNativeRequest(
            BaseAudioSource source, int nativeId) {
        return commandForNativeRequest(source, nativeId, 1.0f);
    }

    /**
     * Resolves only immutable production content/route identity; the game-owned request service
     * still decides whether and when the request dispatches. The S2 dispatcher classifies
     * music/SFX/command ranges and F1-F7 gaps at {@code s2.sounddriver.asm:1555-1600}; its F8-FD
     * jump table owns the six system consequences in that same range. F9's literal fade values
     * are loaded by {@code zFadeOutMusic} at {@code :2423-2436}. FE/FF are consumed earlier by
     * the 68K mailbox bridge at {@code s2.asm:1270-1332}, so their placeholder command is never
     * submitted to presentation.
     */
    private AudioCommand commandForNativeRequest(
            BaseAudioSource source, int nativeId, float pitch) {
        Objects.requireNonNull(source.profile(), "audio profile");
        if (source.profile() instanceof NativeMailboxMusicRoutes routes) {
            int musicId = routes.musicForNativeRequest(nativeId);
            if (musicId >= 0) {
                // Translate only raw driver requests. Ordinary playMusic already receives an
                // engine id, which can name a different song at the same numeric value.
                return commandForNativeMusicRequest(source, musicId);
            }
        }
        if (nativeId >= 0xA0 && nativeId <= 0xF7) {
            return new AudioCommand.PlaySfx(nativeId, null,
                    AudioCommand.SfxRoute.BASE_SMPS_ID, pitch, null);
        }
        return commandForMusicOrSystemRequest(source, nativeId);
    }

    /**
     * Classifies a request the caller already knows is music.
     *
     * <p>The engine's Sonic 2 music ids are not the ROM's native ids: the ROM
     * stops at MusID__End = A0 (s2disasm/s2.constants.asm:865), while
     * GAME_OVER (B8), GOT_EMERALD (BA), CREDITS (BD) and UNDERWATER (DC) sit
     * above it, inside the A0..F7 span the native classifier treats as sound
     * effects. Routing playMusic through that branch published the drowning
     * countdown as SndID_Fire (s2.constants.asm:931) instead of music, so the
     * music ingress skips the sound branch and classifies directly.
     */
    private AudioCommand commandForNativeMusicRequest(
            BaseAudioSource source, int musicId) {
        Objects.requireNonNull(source.profile(), "audio profile");
        return commandForMusicOrSystemRequest(source, musicId);
    }

    private AudioCommand commandForMusicOrSystemRequest(
            BaseAudioSource source, int nativeId) {
        GameAudioProfile profile = Objects.requireNonNull(
                source.profile(), "audio profile");
        return switch (nativeId) {
            case 0xF8 -> new AudioCommand.StopAllSfx();
            case 0xF9 -> new AudioCommand.FadeOutMusic(0x28, 3);
            case 0xFA -> segaPcmCommand(source, nativeId);
            case 0xFB -> new AudioCommand.SetSpeedShoes(true);
            case 0xFC -> new AudioCommand.SetSpeedShoes(false);
            case 0xFD -> new AudioCommand.RetainGlobalStop(nativeId);
            case 0xFE, 0xFF -> new AudioCommand.StopMusic();
            default -> new AudioCommand.PlayMusic(nativeId,
                    source.loader() != null
                            ? AudioCommand.MusicRoute.BASE_SMPS
                            : AudioCommand.MusicRoute.FALLBACK_WAV,
                    profile.isMusicOverride(nativeId), null);
        };
    }

    private AudioCommand segaPcmCommand(BaseAudioSource source, int commandId) {
        SegaPcmSpec spec = source.profile().getSegaPcmSpec();
        if (spec == null || source.rom() == null) {
            return new AudioCommand.ReferenceLimitation(
                    commandId, "SEGA PCM source is unavailable");
        }
        try {
            // Same runtime-layer read as playSegaPcmCommand: the audio layer
            // describes the span, the game profile fetches the bytes.
            byte[] pcm = source.profile().loadSegaPcm(source.rom());
            if (pcm == null) {
                return new AudioCommand.ReferenceLimitation(
                        commandId, "SEGA PCM source is unavailable");
            }
            return new AudioCommand.PlaySegaPcm(commandId, pcm,
                    spec.sampleRate());
        } catch (java.io.IOException unavailable) {
            return new AudioCommand.ReferenceLimitation(
                    commandId, "SEGA PCM source could not be read");
        }
    }

    /**
     * True when {@code sfxId} is the active profile's raw ring request id —
     * the id every ROM ring collect sends and the sound driver alternates
     * between speakers before playback.
     */
    private boolean isRingSpeakerToggleRequest(int sfxId) {
        if (soundMap == null) {
            return false;
        }
        Integer ringRight = soundMap.get(GameSound.RING_RIGHT);
        return ringRight != null && ringRight == sfxId;
    }

    private boolean playBaseSfx(
            BaseAudioSource source, int sfxId, float pitch) {
        if (source.loader() != null) {
            if (sfxId < 0) {
                AbstractSmpsData loaded = source.loader().loadSfx(sfxId);
                return loaded != null && loaded.getId() >= 0
                        && playLoadedBaseSfx(source, loaded, pitch);
            }
            if (!hasCatalogDependencies(source.dac(), source.config())) {
                AbstractSmpsData sfx = source.loader().loadSfx(sfxId);
                if (sfx != null) {
                    recordTimelineCommand(new AudioCommand.PlaySfx(
                            sfxId, null,
                            AudioCommand.SfxRoute.BASE_SMPS_ID,
                            pitch, null));
                    if (sendLiveBackendCommands()) {
                        backend.playSfxSmps(sfx, source.dac(), pitch);
                    }
                    return true;
                }
                return false;
            }
            presentationBinding.ensureShadowPresentation();
            var captured = sourcePublication.baseSfxSource(source);
            SmpsAssetKey key = new SmpsAssetKey(
                    captured.gameId(), SmpsAssetKey.Route.BASE_ID,
                    sfxId, null);
            if (ensureRegisteredSmpsSfx(key, captured)) {
                AudioCommand.PlaySfx command = new AudioCommand.PlaySfx(
                        sfxId, null, AudioCommand.SfxRoute.BASE_SMPS_ID,
                        pitch, null);
                recordRegisteredSmpsSfxCommand(command, captured);
                if (sendLiveBackendCommands()) {
                    var playback = presentationBinding.shadowFactory
                            .requireRegisteredSmpsSfxPlayback(
                                    key, captured.dependencyGeneration());
                    dispatchLiveRegisteredSfx(playback, pitch);
                }
                return true;
            }
        }
        return false;
    }

    public synchronized DiagnosticObserverHandle acquireDiagnosticObservers(
            DiagnosticObserverSet observers) {
        return diagnosticInstallation.acquireDiagnosticObservers(observers);
    }

    public synchronized void setRequestObserver(AudioRequestObserver observer) {
        diagnosticInstallation.setRequestObserver(observer);
    }

    public synchronized void setAdmissionObserver(AudioAdmissionObserver observer) {
        diagnosticInstallation.setAdmissionObserver(observer);
    }

    public synchronized void setDriverServiceObserver(
            SmpsDriverServiceObserver observer) {
        diagnosticInstallation.setDriverServiceObserver(observer);
    }

    public synchronized void setChipWriteObserver(ChipWriteObserver observer) {
        diagnosticInstallation.setChipWriteObserver(observer);
    }

    public synchronized void setSfxContentionObserver(
            SfxContentionObserver observer) {
        diagnosticInstallation.setSfxContentionObserver(observer);
    }

    /** Complete manager-lifetime diagnostic installation and lease authority. */
    private final class DiagnosticInstallation {
        private AudioRequestObserver requestObserver = AudioRequestObserver.NONE;
        private AudioAdmissionObserver admissionObserver =
                AudioAdmissionObserver.NONE;
        private SmpsDriverServiceObserver driverServiceObserver =
                SmpsDriverServiceObserver.NONE;
        private ChipWriteObserver chipWriteObserver = ChipWriteObserver.NONE;
        private SfxContentionObserver sfxContentionObserver =
                SfxContentionObserver.NONE;
        private DiagnosticObserverLease activeDiagnosticObserverLease;

        private DiagnosticObserverHandle acquireDiagnosticObservers(
                DiagnosticObserverSet observers) {
            Objects.requireNonNull(observers, "observers");
            if (activeDiagnosticObserverLease != null
                    || !currentDiagnosticObservers().isNone()) {
                throw new IllegalStateException(
                        "diagnostic observers are already installed");
            }
            applyDiagnosticObservers(observers);
            DiagnosticObserverLease lease = new DiagnosticObserverLease(
                    Thread.currentThread());
            activeDiagnosticObserverLease = lease;
            return lease;
        }

        private void setRequestObserver(AudioRequestObserver observer) {
            rejectObserverReplacementWhileLeased();
            DiagnosticObserverSet current = currentDiagnosticObservers();
            applyDiagnosticObservers(new DiagnosticObserverSet(
                    observer == null ? AudioRequestObserver.NONE : observer,
                    current.admission(), current.driverService(),
                    current.chipWrite(), current.sfxContention()));
        }

        private void setAdmissionObserver(AudioAdmissionObserver observer) {
            rejectObserverReplacementWhileLeased();
            DiagnosticObserverSet current = currentDiagnosticObservers();
            applyDiagnosticObservers(new DiagnosticObserverSet(
                    current.request(),
                    observer == null ? AudioAdmissionObserver.NONE : observer,
                    current.driverService(), current.chipWrite(),
                    current.sfxContention()));
        }

        private void setDriverServiceObserver(
                SmpsDriverServiceObserver observer) {
            rejectObserverReplacementWhileLeased();
            DiagnosticObserverSet current = currentDiagnosticObservers();
            applyDiagnosticObservers(new DiagnosticObserverSet(
                    current.request(), current.admission(),
                    observer == null
                            ? SmpsDriverServiceObserver.NONE : observer,
                    current.chipWrite(), current.sfxContention()));
        }

        private void setChipWriteObserver(ChipWriteObserver observer) {
            rejectObserverReplacementWhileLeased();
            DiagnosticObserverSet current = currentDiagnosticObservers();
            applyDiagnosticObservers(new DiagnosticObserverSet(
                    current.request(), current.admission(),
                    current.driverService(),
                    observer == null ? ChipWriteObserver.NONE : observer,
                    current.sfxContention()));
        }

        private void setSfxContentionObserver(
                SfxContentionObserver observer) {
            rejectObserverReplacementWhileLeased();
            DiagnosticObserverSet current = currentDiagnosticObservers();
            applyDiagnosticObservers(new DiagnosticObserverSet(
                    current.request(), current.admission(),
                    current.driverService(), current.chipWrite(),
                    observer == null ? SfxContentionObserver.NONE : observer));
        }

        private void rejectObserverReplacementWhileLeased() {
            if (activeDiagnosticObserverLease != null) {
                throw new IllegalStateException(
                        "diagnostic observers are owned by an active lease");
            }
        }

        private DiagnosticObserverSet currentDiagnosticObservers() {
            return new DiagnosticObserverSet(
                    requestObserver, admissionObserver, driverServiceObserver,
                    chipWriteObserver, sfxContentionObserver);
        }

        private void applyDiagnosticObservers(DiagnosticObserverSet desired) {
            DiagnosticObserverSet previous = currentDiagnosticObservers();
            try {
                propagateDiagnosticObservers(desired);
            } catch (RuntimeException | Error failure) {
                try {
                    propagateDiagnosticObservers(previous);
                } catch (RuntimeException | Error rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
            requestObserver = desired.request();
            admissionObserver = desired.admission();
            driverServiceObserver = desired.driverService();
            chipWriteObserver = desired.chipWrite();
            sfxContentionObserver = desired.sfxContention();
        }

        private void propagateDiagnosticObservers(DiagnosticObserverSet observers) {
            if (backend != null) {
                backend.setAdmissionObserver(observers.admission());
                backend.setDriverServiceObserver(observers.driverService());
                backend.setChipWriteObserver(observers.chipWrite());
                backend.setSfxContentionObserver(observers.sfxContention());
            }
            presentationBinding.propagateDiagnosticObservers(observers);
        }

        private boolean active(DiagnosticObserverLease lease) {
            synchronized (AudioManager.this) {
                return lease.active;
            }
        }

        private void close(DiagnosticObserverLease lease) {
            synchronized (AudioManager.this) {
                if (!lease.active) {
                    return;
                }
                if (Thread.currentThread() != lease.ownerThread) {
                    throw new IllegalStateException(
                            "diagnostic observer lease must close on its owner thread");
                }
                if (activeDiagnosticObserverLease != lease) {
                    throw new IllegalStateException(
                            "diagnostic observer lease ownership was lost");
                }
                applyDiagnosticObservers(DiagnosticObserverSet.none());
                lease.active = false;
                activeDiagnosticObserverLease = null;
            }
        }

        private void installBackendDiagnosticObservers() {
            backend.setAdmissionObserver(admissionObserver);
            backend.setDriverServiceObserver(driverServiceObserver);
            backend.setChipWriteObserver(chipWriteObserver);
            backend.setSfxContentionObserver(sfxContentionObserver);
        }

        private void reset() {
            if (activeDiagnosticObserverLease == null) {
                this.requestObserver = AudioRequestObserver.NONE;
                this.admissionObserver = AudioAdmissionObserver.NONE;
                this.driverServiceObserver = SmpsDriverServiceObserver.NONE;
                this.chipWriteObserver = ChipWriteObserver.NONE;
                this.sfxContentionObserver = SfxContentionObserver.NONE;
            }
            if (backend != null) {
                installBackendDiagnosticObservers();
            }
        }

        private AudioRequestObserver requestObserver() {
            return requestObserver;
        }

        private AudioAdmissionObserver admissionObserver() {
            return admissionObserver;
        }

        private SmpsDriverServiceObserver driverServiceObserver() {
            return driverServiceObserver;
        }

        private ChipWriteObserver chipWriteObserver() {
            return chipWriteObserver;
        }

        private SfxContentionObserver sfxContentionObserver() {
            return sfxContentionObserver;
        }
    }

    private final class DiagnosticObserverLease
            implements DiagnosticObserverHandle {
        private final Thread ownerThread;
        private boolean active = true;

        private DiagnosticObserverLease(Thread ownerThread) {
            this.ownerThread = ownerThread;
        }

        @Override
        public boolean active() {
            return diagnosticInstallation.active(this);
        }

        @Override
        public void close() {
            diagnosticInstallation.close(this);
        }
    }

    private SmpsRequestAdmissionPolicy sfxAdmissionPolicy() {
        GameAudioProfile profile = sourcePublication.base().profile();
        return profile != null
                ? profile.getSfxAdmissionPolicy()
                : SmpsRequestAdmissionPolicy.PERMISSIVE;
    }

    private static AudioRequestObserver.RequestClass sfxRequestClass(int soundId) {
        if (soundId >= 0xD0 && soundId < 0xE0) {
            return AudioRequestObserver.RequestClass.SPECIAL_SFX;
        }
        return AudioRequestObserver.RequestClass.SFX;
    }

    private boolean playLoadedBaseSfx(
            BaseAudioSource source,
            AbstractSmpsData loaded,
            float pitch) {
        if (!hasCatalogDependencies(source.dac(), source.config())) {
            return false;
        }
        presentationBinding.ensureShadowPresentation();
        var captured = sourcePublication.baseSfxSource(source);
        int resolvedId = loaded.getId();
        SmpsAssetKey key = new SmpsAssetKey(
                captured.gameId(), SmpsAssetKey.Route.BASE_ID,
                resolvedId, null);
        registerLoadedSmpsSfx(key, captured, loaded, resolvedId);
        AudioCommand.PlaySfx command = new AudioCommand.PlaySfx(
                resolvedId, null, AudioCommand.SfxRoute.BASE_SMPS_ID,
                pitch, null);
        recordRegisteredSmpsSfxCommand(command, captured);
        if (sendLiveBackendCommands()) {
            dispatchLiveRegisteredSfx(
                    presentationBinding.shadowFactory.requireRegisteredSmpsSfxPlayback(
                            key, captured.dependencyGeneration()),
                    pitch);
        }
        return true;
    }

    private boolean playLoadedDonorSfx(
            String gameId,
            DonorAudioSource source,
            AbstractSmpsData loaded,
            float pitch,
            String requestedName) {
        if (!hasCatalogDependencies(source.dac(), source.config())) {
            return false;
        }
        presentationBinding.ensureShadowPresentation();
        var captured = sourcePublication.donorSfxSource(gameId, source);
        int resolvedId = loaded.getId();
        SmpsAssetKey key = new SmpsAssetKey(
                captured.gameId(), SmpsAssetKey.Route.DONOR_ID,
                resolvedId, null);
        registerLoadedSmpsSfx(key, captured, loaded, resolvedId);
        AudioCommand.PlaySfx command = new AudioCommand.PlaySfx(
                resolvedId, requestedName,
                AudioCommand.SfxRoute.DONOR_SMPS,
                pitch, gameId);
        recordRegisteredSmpsSfxCommand(command, captured);
        if (sendLiveBackendCommands()) {
            playLiveRegisteredDonorSfx(key, captured, pitch);
        }
        return true;
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
        DonorAudioSource source = sourcePublication.donor(donorGameId);
        if (source != null) {
            source = completeLegacyDonorSource(donorGameId, source);
            if (sfxId < 0) {
                AbstractSmpsData loaded = source.loader().loadSfx(sfxId);
                if (loaded != null && loaded.getId() >= 0) {
                    playLoadedDonorSfx(
                            donorGameId, source, loaded, 1.0f, null);
                }
                return;
            }
            if (!hasCatalogDependencies(source.dac(), source.config())) {
                AbstractSmpsData sfx = source.loader().loadSfx(sfxId);
                if (sfx != null) {
                    recordTimelineCommand(new AudioCommand.PlaySfx(
                            sfxId, null, AudioCommand.SfxRoute.DONOR_SMPS,
                            1.0f, donorGameId));
                    if (sendLiveBackendCommands()) {
                        backend.playSfxSmps(sfx, source.dac(), 1.0f);
                    }
                }
                return;
            }
            presentationBinding.ensureShadowPresentation();
            var captured = sourcePublication.donorSfxSource(donorGameId, source);
            SmpsAssetKey key = new SmpsAssetKey(
                    captured.gameId(), SmpsAssetKey.Route.DONOR_ID,
                    sfxId, null);
            if (ensureRegisteredSmpsSfx(key, captured)) {
                AudioCommand.PlaySfx command = new AudioCommand.PlaySfx(
                        sfxId, null, AudioCommand.SfxRoute.DONOR_SMPS,
                        1.0f, donorGameId);
                recordRegisteredSmpsSfxCommand(command, captured);
                if (sendLiveBackendCommands()) {
                    playLiveRegisteredDonorSfx(key, captured, 1.0f);
                }
            }
        }
    }

    private boolean ensureRegisteredSmpsSfx(
            SmpsAssetKey key,
            AudioPresentationCommandResolver.SourceAccess source) {
        if (presentationBinding.shadowFactory.findRegisteredSmpsSfxAsset(
                key, source.dependencyGeneration()) != null) {
            return true;
        }
        AbstractSmpsData data = key.route() == SmpsAssetKey.Route.BASE_NAME
                ? source.loader().loadSfx(key.assetName())
                : source.loader().loadSfx(key.assetId());
        if (data == null) {
            return false;
        }
        int resolvedId = key.route() == SmpsAssetKey.Route.BASE_NAME
                ? data.getId() : key.assetId();
        registerLoadedSmpsSfx(key, source, data, resolvedId);
        return true;
    }

    private void registerLoadedSmpsSfx(
            SmpsAssetKey key,
            AudioPresentationCommandResolver.SourceAccess source,
            AbstractSmpsData data,
            int resolvedId) {
        presentationBinding.shadowFactory.registerSmpsSfxAsset(
                key, source.dependencyGeneration(), data,
                source.dac(), source.config(),
                new SmpsSfxPlaybackPolicy(
                        source.sfxPolicy().priority(resolvedId),
                        source.sfxPolicy().special(resolvedId),
                        source.sfxPolicy().continuous(resolvedId)));
    }

    private static boolean hasCatalogDependencies(
            DacData dac, SmpsSequencerConfig config) {
        return dac != null && config != null;
    }

    private synchronized DonorAudioSource completeLegacyDonorSource(
            String gameId, DonorAudioSource observed) {
        return sourcePublication.completeLegacyDonorSource(gameId, observed);
    }

    private AudioTimelineEntry recordRegisteredSmpsSfxCommand(
            AudioCommand.PlaySfx command,
            AudioPresentationCommandResolver.SourceAccess source) {
        if (suppressingRewindReplay()) {
            return null;
        }
        AudioTimelineEntry entry = commandTimeline.record(command);
        mirrorShadowCommand(() ->
                presentationBinding.shadowResolver.submitRegisteredSmpsSfx(command, source));
        return entry;
    }

    private void playLiveRegisteredDonorSfx(
            SmpsAssetKey key,
            AudioPresentationCommandResolver.SourceAccess source,
            float pitch) {
        var playback = presentationBinding.shadowFactory.requireRegisteredSmpsSfxPlayback(
                key, source.dependencyGeneration());
        dispatchLiveRegisteredSfx(playback, pitch);
    }

    void dispatchLiveRegisteredSfx(
            AudioPresentationSourceFactory.RegisteredSmpsPlayback playback,
            float pitch) {
        backend.playSfxSmps(
                playback.program(), playback.dac(), pitch,
                playback.config(), playback.policy());
    }

    public void update() {
        servicePresentationDevice();
        finishOuterAudioFrame();
    }

    /** Device housekeeping alone cannot produce PCM or retire a command frame. */
    void servicePresentationDevice() {
        presentationBinding.updateDevice();
    }

    /** Explicit offline driver lifetime; display.fps never chooses this clock. */
    void beginOfflineServiceCadence(Object owner, int hz) {
        if (hz <= 0) throw new IllegalArgumentException("offline service rate must be positive");
        if (offlineCadenceOwner != null) throw new IllegalStateException("offline cadence already owned");
        requireNoCaptureForCadenceChange();
        presentationBinding.closeShadowPresentation();
        offlineCadenceOwner = Objects.requireNonNull(owner);
        offlineServiceHz = hz;
    }

    void endOfflineServiceCadence(Object owner) {
        if (offlineCadenceOwner != owner) throw new IllegalStateException("foreign offline cadence owner");
        requireNoCaptureForCadenceChange();
        presentationBinding.closeShadowPresentation();
        offlineServiceHz = 0;
        offlineCadenceOwner = null;
    }

    private void requireNoCaptureForCadenceChange() {
        if (captureLeases.offlineCaptureHandle != null || captureLeases.activeLiveCaptureAudioHandle != null)
            throw new IllegalStateException("close capture leases before changing offline service cadence");
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
        mutatorMusicIntent(AudioMutatorAccess.Kind.FOREIGN, musicId);
        if (suppressingRewindReplay()) {
            return;
        }
        DonorAudioSource source = sourcePublication.donor(donorGameId);
        if (source != null) {
            source = completeLegacyDonorSource(donorGameId, source);
            if (!hasCatalogDependencies(source.dac(), source.config())) {
                return;
            }
            presentationBinding.ensureShadowPresentation();
            SmpsAssetKey key = new SmpsAssetKey(
                    donorGameId, SmpsAssetKey.Route.DONOR_MUSIC,
                    musicId, null);
            boolean registered = presentationBinding.shadowFactory.findRegisteredSmpsMusicAsset(
                    key, source.generation()) != null;
            AbstractSmpsData data = null;
            if (!registered) {
                data = source.loader().loadMusic(musicId);
                if (data != null) {
                    presentationBinding.shadowFactory.registerSmpsMusicAsset(
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
                        musicId, AudioCommand.MusicRoute.DONOR_SMPS, false,
                        donorGameId,
                        sourcePublication.base().profile() != null
                                ? sourcePublication.base().profile()
                                        .getMusicDuringOverridePolicy()
                                : GameAudioProfile.MusicDuringOverridePolicy
                                        .REPLACE_IMMEDIATELY));
                if (sendLiveBackendCommands()) {
                    var playback = presentationBinding.shadowFactory
                            .requireRegisteredSmpsMusicPlayback(
                                    key, source.generation());
                    backend.prepareLogicalMusicSource(AudioSourceDescriptor.donorMusic(donorGameId, musicId));
                    backend.playSmps(
                            playback.program(), playback.dac(),
                            playback.config(), false);
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
        mutatorMusicIntent(AudioMutatorAccess.Kind.END_OVERRIDE, musicId);
        if (suppressingRewindReplay()) {
            return;
        }
        recordTimelineCommand(new AudioCommand.EndMusicOverride(musicId));
        if (sendLiveBackendCommands()) {
            backend.endMusicOverride(musicId);
        }
    }

    public boolean endMusicOverride(GameMusic music) {
        GameAudioProfile profile = sourcePublication.base().profile();
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
        Map<GameMusic, Integer> musicMap = sourcePublication.musicBinding(donorGameId);
        if (musicMap == null) {
            return null;
        }
        return musicMap.get(music);
    }

    /** Sets creator ROM music pacing; the default 100 preserves shipped-ROM timing. */
    public void setMusicTempoPercent(int percent) {
        MusicTempoPercent.requireValid(percent);
        mutatorMusicIntent(AudioMutatorAccess.Kind.TEMPO, percent);
        if (suppressingRewindReplay()) return;
        recordTimelineCommand(new AudioCommand.SetMusicTempoPercent(percent));
        // Publish this opt-in scene control before an immediate native-act source
        // rebuild. Applying pending commands here does not advance the sound clock.
        if (presentationBinding.shadowProducer != null) presentationBinding.shadowProducer.applyPendingCommandsAtOwnerBoundary();
        if (sendLiveBackendCommands()) backend.setMusicTempoPercent(percent);
    }

    /**
     * Change the music dividing timing (tempo).
     * ROM: Change_Music_Tempo. Lower values = faster playback.
     * @param newDividingTiming the new dividing timing value
     */
    public void changeMusicTempo(int newDividingTiming) {
        mutatorMusicIntent(AudioMutatorAccess.Kind.TEMPO, newDividingTiming);
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
        if (!LevelMusicAccess.allowsEntryMusic(this)) return;
        mutatorMusicIntent(AudioMutatorAccess.Kind.STOP, 0);
        if (suppressingRewindReplay()) {
            return;
        }
        GameAudioProfile profile = sourcePublication.base().profile();
        recordTimelineCommand(new AudioCommand.StopMusic(
                profile != null
                        ? profile.getSystemCommandDuringOverridePolicy()
                        : GameAudioProfile.SystemCommandDuringOverridePolicy
                                .APPLY));
        if (sendLiveBackendCommands()) {
            backend.stopPlayback();
        }
    }

    /**
     * Fade out the currently playing music using ROM default timing.
     * ROM equivalent: MusID_FadeOut (0xF9) / zFadeOutMusic.
     * SFX admission follows the active game's retail fade policy.
     * Host policy owns the command's pre-fade effects; Sonic 1 stops its
     * normal and special SFX tracks before fading the music channels.
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

    /**
     * Fades the current music out with the active game's own ROM parameters.
     *
     * <p>The step count and delay belong to the game, not to this class: S1 and
     * S2 fade over 120 frames, S3K over 240. Routing through the profile keeps
     * the object-services fade and the fade-out system command on one set of
     * values.
     */
    public void fadeOutMusic() {
        GameAudioProfile.fadeOut(getAudioProfile(), this);
    }

    /**
     * Fade out the currently playing music over time.
     * ROM equivalent: MusID_FadeOut (0xF9) / zFadeOutMusic.
     * SFX admission follows the active game's retail fade policy.
     * Host policy owns the command's pre-fade effects; Sonic 1 stops its
     * normal and special SFX tracks before fading the music channels.
     *
     * @param steps total number of volume steps (ROM default: 0x28 = 40)
     * @param delay frames between each volume step (ROM default: 3)
     */
    public void fadeOutMusic(int steps, int delay) {
        if (!LevelMusicAccess.allowsEntryMusic(this)) return;
        mutatorMusicIntent(AudioMutatorAccess.Kind.FADE, 0);
        if (suppressingRewindReplay()) {
            return;
        }
        fadeOutMusicCount++;
        GameAudioProfile profile = sourcePublication.base().profile();
        recordTimelineCommand(new AudioCommand.FadeOutMusic(steps, delay,
                profile != null
                        ? profile.getSystemCommandDuringOverridePolicy()
                        : GameAudioProfile.SystemCommandDuringOverridePolicy
                                .APPLY));
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
        sourcePublication.registerDonorLoader(gameId, loader, dacData, config, donorProfile);
    }

    private static String requireDonorGameId(String gameId) {
        String resolved = Objects.requireNonNull(gameId, "gameId");
        if (resolved.isBlank()) {
            throw new IllegalArgumentException("gameId must not be blank");
        }
        return resolved;
    }

    public void registerDonorMusicMap(String gameId, Map<GameMusic, Integer> musicMap) {
        sourcePublication.registerDonorMusicMap(gameId, musicMap);
    }

    /**
     * Registers a donor sound binding so that a GameSound missing from the
     * base game's sound map will be routed through the specified donor loader.
     */
    public void registerDonorSound(GameSound sound, String gameId, int sfxId) {
        sourcePublication.registerDonorSound(sound, gameId, sfxId);
    }

    /**
     * Clears all donor audio state (loaders, DAC data, and sound bindings).
     */
    public synchronized void clearDonorAudio() {
        sourcePublication.clearDonorAudio();
    }

    /**
     * One donor key's route and bindings, captured by {@link ScopedDonorAudio}
     * before a scoped registration replaces them. Engine-internal and opaque.
     */
    static final class DonorKeyState {
        private final String gameId;
        private final DonorAudioSource source;
        private final Map<GameMusic, Integer> music;
        private final Map<GameSound, DonorSfxBinding> sounds;

        private DonorKeyState(String gameId, DonorAudioSource source,
                              Map<GameMusic, Integer> music,
                              Map<GameSound, DonorSfxBinding> sounds) {
            this.gameId = gameId;
            this.source = source;
            this.music = music;
            this.sounds = sounds;
        }
    }

    synchronized DonorKeyState captureDonorKeyState(String gameId) {
        return sourcePublication.captureDonorKeyState(gameId);
    }

    /**
     * Puts one key's captured route and bindings back. The route keeps its
     * loader, DAC, sequencer config and profiles under a fresh generation, so
     * no asset cached for the scoped registration can answer for it.
     */
    synchronized void restoreDonorKeyState(DonorKeyState state) {
        sourcePublication.restoreDonorKeyState(state);
    }

    /**
     * Resets mutable state without destroying the singleton instance.
     * Used by TestEnvironment to prevent state leaking between tests
     * (e.g. Sonic 1 SMPS loader contaminating Sonic 2 tests).
     */
    public synchronized void resetState() {
        mutatorAccess.reset();
        invalidateAndReleaseStreamedMusicSession();
        sourcePublication.reset();
    }

    /** Fixed non-source reset effects, between empty base and donor clearing. */
    private void resetNonSourceState() {
        this.soundMap = null;
        diagnosticInstallation.reset();
        logicalPublication.setRingLeft(true);
        this.rewindReplaySuppressionDepth = 0;
        this.audioFrameOwnedExternally = false;
        this.audioFrameAdvanced = false;
        heldReverseRestore.reset();
        this.commandTimeline.clear();
        // A mode transition rebuilds the presentation; it does not end a
        // recording of the window. Entering a game from the master title screen
        // runs Engine.resetForGameplayFromMasterTitle -> resetState() before
        // initializeGlobalGameplayServices -> ensurePresentationSink, so
        // retiring the lease here killed the recording's audio before the
        // retained backend could rebuild its sink. Only destroy() is a genuine
        // teardown.
        captureLeases.detachLiveCaptureAudioHandleForRebuild();
        presentationBinding.closeShadowPresentation();
    }

    private AudioTimelineEntry recordTimelineCommand(AudioCommand command) {
        if (!suppressingRewindReplay()) {
            AudioTimelineEntry entry = commandTimeline.record(command);
            mirrorShadowCommand(() -> presentationBinding.shadowResolver.submit(command));
            if (presentationBinding.shadowSmpsSession != null) {
                presentationBinding.shadowSmpsSession.setNativeHistoryCursor(
                        com.openggf.audio.rewind.AudioTimelineInternalAccess.cursor(commandTimeline));
            }
            return entry;
        }
        return null;
    }

    /** Publishes one command selected by the optional game-owned request service. */
    private void submitForwardRequestCommand(AudioCommand command) {
        presentationBinding.shadowResolver.submit(command);
        commandTimeline.record(command);
        presentationBinding.shadowParity.commandSubmitted();
    }

    private void mirrorShadowCommand(Runnable submission) {
        try {
            presentationBinding.ensureShadowPresentation();
            submission.run();
            presentationBinding.shadowParity.commandSubmitted();
        } catch (RuntimeException failure) {
            AudioDiagnosticObserverException.rethrowIfPresent(failure);
            LOGGER.log(Level.WARNING,
                    "Presentation shadow command mirror failed", failure);
        }
    }

    private void drainShadowCommandsAtOwnerBoundary() {
        if (presentationBinding.shadowProducer == null) {
            throw new IllegalStateException(
                    "presentation producer is not installed");
        }
        presentationBinding.shadowProducer.applyPendingCommandsAtOwnerBoundary();
    }

    private boolean sendLiveBackendCommands() {
        return false;
    }

    public void toggleMute(ChannelType type, int channel) {
        mirrorShadowCommand(() -> presentationBinding.shadowCommands.submit(
                new AudioPresentationCommand.ToggleMute(type, channel),
                () -> true, this::drainShadowCommandsAtOwnerBoundary));
    }

    public void toggleSolo(ChannelType type, int channel) {
        mirrorShadowCommand(() -> presentationBinding.shadowCommands.submit(
                new AudioPresentationCommand.ToggleSolo(type, channel),
                () -> true, this::drainShadowCommandsAtOwnerBoundary));
    }

    public boolean isMuted(ChannelType type, int channel) {
        presentationBinding.ensureShadowPresentation();
        return presentationBinding.shadowRegistry.isMuted(type, channel);
    }

    public boolean isSoloed(ChannelType type, int channel) {
        presentationBinding.ensureShadowPresentation();
        return presentationBinding.shadowRegistry.isSoloed(type, channel);
    }

    private void resetShadowRestoreIntent() {
        shadowRestoreRequested = false;
    }

    private void restoreShadowMusic() {
        // Native E4 completion is intent authority too, independent of the external observer.
        mutatorMusicIntent(AudioMutatorAccess.Kind.NATURAL_RESTORE, 0);
        presentationBinding.shadowRegistry.requestMusicOverrideRestore();
        shadowRestoreRequested = true;
    }

    private String baseGameId(BaseAudioSource source) {
        try {
            return source.profile() != null
                    ? source.profile().presentationGameId() : "base";
        } catch (RuntimeException unavailable) {
            return "base";
        }
    }

    private AudioPresentationCommandResolver.SfxPolicy sfxPolicyFor(
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
                return profile != null && profile.isContinuousSfx(sfxId);
            }
        };
    }

    private static GameAudioProfile.OrdinaryMusicSfxPolicy
            ordinaryMusicSfxPolicyFor(GameAudioProfile profile) {
        return profile != null
                ? profile.getOrdinaryMusicSfxPolicy()
                : GameAudioProfile.OrdinaryMusicSfxPolicy.STOP_ALL;
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
            return sourcePublication.sourceFor(route, donorGameId);
        }

        @Override public int maxStereoFrames() {
            return maxFrames;
        }
    }

    /**
     * Owns the manager's current physical assembly, including partial publication
     * and retirement. Logical source, held target and capture continuity policy
     * remain with their existing owners; collaborator reads stay live.
     */
    private final class PresentationBinding {
        private AudioPresentationCommandQueue shadowCommands;
        private AudioPresentationSourceFactory shadowFactory;
        private AudioPresentationCommandResolver shadowResolver;
        private AudioVoiceRegistry shadowRegistry;
        private AudioPresentationProducer shadowProducer;
        private SmpsDriverSession shadowSmpsSession;
        private AudioRequestService shadowRequestService;
        private int shadowFrameRate;
        private AudioPresentationParityProbe shadowParity;
        private AudioPresentationTuning shadowTuning;
        private com.openggf.audio.synth.NativeCompatiblePcmOutput.Config nativeCompatiblePcmCapture;
        private int nativeCompatiblePcmSessions;
        private AudioPresentationTuning standaloneTuning;
        private int standaloneFrameRate;
        private SmpsCoordFlagHandlerOwner presentationCoordFlagHandlers;
        private final Set<String> presentationCoordHandlerGameIds = new LinkedHashSet<>();
        private AudioPresentationSink presentationSink;
        private boolean presentationSpeakerFailed;

        private record PresentationCoordHandlerPreparation(
                SmpsCoordFlagHandlerOwner owner,
                Set<String> gameIds,
                boolean publishOwner,
                boolean configureCandidate,
                String candidateGameId) {
        }

        private void installStandalone(AudioPresentationSink sink, SmpsCoordFlagHandlerOwner coordFlagHandlers) {
            presentationSink = java.util.Objects.requireNonNull(sink, "sink");
            presentationCoordFlagHandlers = java.util.Objects.requireNonNull(coordFlagHandlers, "coordFlagHandlers");
        }

        private void initializeStandalone(String gameId, GameAudioProfile profile,
                                         SonicConfigurationService config,
                                         SmpsCoordFlagHandlerOwner coordFlagHandlers) {
            standaloneFrameRate = FrameRateResolver.simulation(java.util.Objects.requireNonNull(config, "config"));
            standaloneTuning = new AudioPresentationTuning(
                    "PAL".equalsIgnoreCase(config.getString(SonicConfiguration.REGION))
                            ? SmpsSequencer.Region.PAL : SmpsSequencer.Region.NTSC,
                    config.getBoolean(SonicConfiguration.DAC_INTERPOLATE),
                    config.getBoolean(SonicConfiguration.FM6_DAC_OFF));
            profile.configurePresentationCoordFlagHandlers(coordFlagHandlers);
            presentationCoordHandlerGameIds.add(gameId);
            ensureShadowPresentation();
        }

        private PresentationCoordHandlerPreparation prepareBaseCoordination(GameAudioProfile candidate) {
            return preparePresentationCoordHandlers(candidate, true);
        }

        private PresentationCoordHandlerPreparation prepareDonorCoordination(GameAudioProfile candidate) {
            return preparePresentationCoordHandlers(candidate);
        }

        private void applyBaseCoordination(PresentationCoordHandlerPreparation preparation,
                                           SourcePublication.BaseBackendAttempt attempt) {
            if (preparation.configureCandidate()) {
                preparation.owner().configureTransactionally(
                        owner -> attempt.candidate.configurePresentationCoordFlagHandlers(owner), attempt::run);
            } else {
                attempt.run();
            }
        }

        private void applyDonorCoordination(PresentationCoordHandlerPreparation preparation,
                                            SourcePublication.DonorBackendAttempt attempt) {
            if (preparation.configureCandidate()) {
                preparation.owner().configureTransactionally(
                        owner -> attempt.candidate.configurePresentationCoordFlagHandlers(owner), attempt::run);
            } else {
                attempt.run();
            }
        }

        private void profilePublished() {
            if (shadowProducer != null) {
                replaceShadowPresentationForBaseMutation();
            } else if (shadowFactory != null) {
                shadowFactory.setSfxAdmissionPolicy(sfxAdmissionPolicy());
            }
        }

        private void romPublished() {
            if (shadowProducer != null) {
                replaceShadowPresentationForBaseMutation();
            }
        }

        private void installStreamedPort(StreamedMusicPort replacement) {
            ensureShadowPresentation();
            shadowFactory.installStreamedMusicPort(replacement);
        }

        private void retireStreamedCursors(StreamedMusicPort previous) {
            if (shadowFactory != null) {
                shadowFactory.retireStreamedMusicPort();
            } else if (previous != StreamedMusicPort.EMPTY) {
                previous.stop();
            }
        }

        private void propagateDiagnosticObservers(DiagnosticObserverSet observers) {
            if (shadowFactory != null) {
                shadowFactory.setAdmissionObserver(observers.admission());
                shadowFactory.setDriverServiceObserver(observers.driverService());
                shadowFactory.setChipWriteObserver(observers.chipWrite());
                shadowFactory.setSfxContentionObserver(observers.sfxContention());
            }
        }

        private void installFailedBackendSink() {
            presentationSink = new NoDeviceAudioSink(backend.outputSampleRate());
            presentationSpeakerFailed = true;
        }

        private void markSpeakerFailed() {
            presentationSpeakerFailed = true;
        }

        private void ensurePresentationSink() {
            if (backend != null && presentationSink == null) {
                installBackendPresentationSink();
            }
        }

        private void updateDevice() {
            if (presentationSink instanceof OpenAlPcmSink openAlSink) {
                openAlSink.updateDevice();
            }
        }

        private void pause() {
            if (presentationSink instanceof OpenAlPcmSink openAlSink) {
                openAlSink.pause();
            }
            if (shadowProducer != null) shadowProducer.pauseScenePcmSource();
        }

        private void resume() {
            boolean resumeSpeaker = shadowProducer == null || shadowProducer.resumeScenePcmSource();
            if (resumeSpeaker && presentationSink instanceof OpenAlPcmSink openAlSink) {
                openAlSink.resume();
            }
        }

        private int configuredFrameRate() {
            if (offlineServiceHz > 0) return offlineServiceHz;
            if (standaloneFrameRate > 0) {
                return standaloneFrameRate;
            }
            var config = configuredServicesOrNull();
            if (config == null) {
                return 60;
            }
            return FrameRateResolver.simulation(config);
        }

        private void installBackendPresentationSink() {
            try {
                presentationSink = backend.createPresentationSink(
                        this::handlePresentationSinkFailure,
                        warning -> LOGGER.warning("Speaker output: " + warning));
                presentationSpeakerFailed = false;
            } catch (Throwable failure) {
                AudioDiagnosticObserverException.rethrowIfPresent(failure);
                LOGGER.log(Level.WARNING,
                        "Speaker device unavailable; continuing without audio output",
                        failure);
                presentationSink =
                        new NoDeviceAudioSink(backend.outputSampleRate());
                presentationSpeakerFailed = true;
            }
        }

        private void handlePresentationSinkFailure(Throwable failure) {
            presentationSpeakerFailed = true;
            LOGGER.log(Level.WARNING,
                    "Speaker output failed; continuing without audio output",
                    failure);
            AudioPresentationSink replacement =
                    new NoDeviceAudioSink(AudioManager.this.outputSampleRate());
            presentationSink = replacement;
            if (shadowProducer != null) {
                shadowProducer.replaceSink(replacement);
            }
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
                            tuning.fm6DacOff(),
                            false, 1, AudioManager.this::restoreShadowMusic,
                            new DecodedPcmCache(),
                            AudioManager.class.getClassLoader()::getResourceAsStream);
            BaseAudioSource base = sourcePublication.base();
            SmpsPhysicalPolicy physicalPolicy = base.profile() != null
                    ? Objects.requireNonNull(
                            base.profile().smpsPhysicalPolicy(),
                            "smpsPhysicalPolicy")
                    : LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE;
            com.openggf.configuration.SonicConfigurationService fmCoreConfig = configuredServicesOrNull();
            // One presentation is one ROM hardware frame of sound-chip time, not 1/frameRate s of wall time.
            boolean palRegion = fmCoreConfig != null && "PAL".equalsIgnoreCase(
                    fmCoreConfig.getString(SonicConfiguration.REGION));
            SmpsPhysicalDevice.Settings physicalSettings =
                    new SmpsPhysicalDevice.Settings(
                            com.openggf.audio.session.SmpsHardwareFrameBudget.exactSynthesisRate(
                                    sampleRate, frameRate, palRegion),
                            tuning.dacInterpolate(),
                            fmCoreConfig != null
                                    ? com.openggf.audio.synth.FmCoreSelection.fromConfig(
                                            fmCoreConfig.getString(SonicConfiguration.AUDIO_FM_CORE))
                                    : com.openggf.audio.synth.FmCoreSelection.ACCURATE,
                            nativeCompatiblePcmCapture);
            if (nativeCompatiblePcmCapture != null) {
                nativeCompatiblePcmSessions++;
            }
            SmpsDriverSessionConfiguration sessionConfiguration =
                    new SmpsDriverSessionConfiguration(base.profile() != null
                            ? base.profile().smpsStatefulCommandPolicy()
                            : com.openggf.audio.session.SmpsStatefulCommandPolicy.NONE);
            SmpsDriverSession smpsSession = new SmpsDriverSession(
                    physicalSettings, physicalPolicy, diagnosticInstallation.chipWriteObserver(),
                    new SmpsSessionProfileFingerprint(
                            baseGameId(base), base.generation(),
                            physicalPolicy.identity(), physicalSettings,
                            sessionConfiguration.statefulCommandPolicy()
                                    .identity()),
                    sessionConfiguration);
            shadowFactory = new AudioPresentationSourceFactory(
                    () -> true, presentationCoordFlagHandlers, settings,
                    smpsSession);
            shadowFactory.installStreamedMusicPort(streamedMusicPort);
            shadowFactory.setStreamedRestoreConfig(base.config(),
                    base.profile() == null || base.profile().blocksSfxDuringMusicRestoreFadeIn());
            shadowFactory.setAdmissionObserver(diagnosticInstallation.admissionObserver());
            shadowFactory.setDriverServiceObserver(diagnosticInstallation.driverServiceObserver());
            shadowFactory.setChipWriteObserver(diagnosticInstallation.chipWriteObserver());
            shadowFactory.setSfxContentionObserver(diagnosticInstallation.sfxContentionObserver());
            shadowFactory.setSfxAdmissionPolicy(sfxAdmissionPolicy());
            shadowCommands = new AudioPresentationCommandQueue();
            shadowRegistry = new AudioVoiceRegistry(shadowFactory, shadowFactory,
                    presentationCoordFlagHandlers,
                    warning -> LOGGER.warning("Presentation shadow: " + warning),
                    smpsSession);
            shadowResolver = new AudioPresentationCommandResolver(shadowCommands,
                    shadowFactory, new ShadowSources(maxFrames),
                    warning -> LOGGER.warning("Presentation shadow: " + warning),
                    () -> true, AudioManager.this::drainShadowCommandsAtOwnerBoundary);
            shadowRequestService = base.profile() != null
                    ? base.profile().createAudioRequestService() : null;
            AudioPresentationMixer mixer = new AudioPresentationMixer(maxFrames);
            AudioPresentationSink sink = presentationSink != null
                    ? presentationSink : new NoDeviceAudioSink(sampleRate);
            if (sink.sampleRate() != sampleRate) {
                sink.close();
                sink = new NoDeviceAudioSink(sampleRate);
                presentationSink = sink;
            }
            shadowParity = new AudioPresentationParityProbe(sampleRate, frameRate);
            shadowProducer = shadowRequestService == null
                    ? new AudioPresentationProducer(sampleRate, frameRate,
                    Math.max(maxFrames, configuredPcmHistoryFrames(sampleRate)),
                    Math.max(1, sampleRate * REVERSE_RELEASE_CROSSFADE_MS / 1000),
                    shadowRegistry, shadowCommands, mixer, sink, smpsSession)
                    : new AudioPresentationProducer(sampleRate, frameRate,
                    Math.max(maxFrames, configuredPcmHistoryFrames(sampleRate)),
                    Math.max(1, sampleRate * REVERSE_RELEASE_CROSSFADE_MS / 1000),
                    shadowRegistry, shadowCommands, mixer,
                    sink, smpsSession, shadowRequestService,
                    shadowResolver, commandTimeline, shadowParity);
            shadowSmpsSession = smpsSession;
            shadowFrameRate = frameRate;
            captureLeases.rebindLiveCaptureAudioHandle(sampleRate);
        }

        private void replaceShadowPresentationForBaseMutation() {
            int musicTempoPercent = shadowSmpsSession == null ? 100
                    : shadowSmpsSession.captureSnapshot().musicTempoPercent();
            heldReverseRestore.invalidatePreparation();
            captureLeases.detachLiveCaptureAudioHandleForRebuild();
            closeShadowPresentation();
            AudioManager.this.ensurePresentationSink();
            ensureShadowPresentation();
            if (musicTempoPercent != 100) {
                shadowSmpsSession.applyCommand(
                        new com.openggf.audio.session.SmpsSessionCommand.SetMusicTempoPercent(musicTempoPercent));
            }
        }

        private void closeShadowPresentation() {
            // Producer teardown stays here. The capture owner reconciles accepted
            // release against the current producer, including callback replacements.
            if (shadowProducer != null) {
                try {
                    shadowProducer.close();
                } finally {
                    captureLeases.reconcileProducerClose();
                }
            } else {
                captureLeases.retireWithoutProducer();
                if (presentationSink != null) {
                    presentationSink.close();
                }
            }
            presentationSink = null;
            shadowProducer = null;
            shadowFrameRate = 0;
            shadowResolver = null;
            shadowRegistry = null;
            shadowSmpsSession = null;
            shadowRequestService = null;
            shadowCommands = null;
            shadowFactory = null;
            shadowParity = null;
            shadowTuning = null;
            standaloneTuning = null;
            resetShadowRestoreIntent();
            presentationCoordFlagHandlers = null;
            presentationCoordHandlerGameIds.clear();
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
                        sourcePublication.base().profile(), preparedOwner,
                        preparedGameIds);
            }
            sourcePublication.donors().values().stream()
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

        private int outputSampleRate() {
            return presentationSink != null
                    ? presentationSink.sampleRate()
                    : backend.outputSampleRate();
        }

        private void setScenePcmSource(com.openggf.audio.presentation.ScenePcmSource source) {
            ensureShadowPresentation();
            shadowProducer.setScenePcmSource(source);
            if (source != null && presentationSpeakerFailed) source.onSpeakerFailure();
        }

        private long consumedPresentationStereoFrames() {
            return presentationSink instanceof OpenAlPcmSink sink
                    ? sink.consumedStereoFrames() : -1;
        }

        private long presentationUnderrunCount() {
            return presentationSink instanceof OpenAlPcmSink sink ? sink.underrunCount() : 0;
        }

        private void setNativeCompatiblePcmCapture(
                com.openggf.audio.synth.NativeCompatiblePcmOutput.Config config) {
            if (config != null && shadowSmpsSession != null) {
                throw new IllegalStateException("native-compatible PCM capture must be set before the presentation exists");
            }
            nativeCompatiblePcmCapture = config;
            if (config != null) {
                nativeCompatiblePcmSessions = 0; // count this capture's sessions only
            }
        }

        private int drainNativeCompatiblePcm(short[] target, int offsetSamples, int maxFrames) {
            return shadowSmpsSession == null ? 0
                    : shadowSmpsSession.drainNativeCompatiblePcm(target, offsetSamples, maxFrames);
        }

        private int nativeCompatiblePcmSessions() {
            return nativeCompatiblePcmSessions;
        }
    }

    /**
     * Complete manager-lifetime logical source namespace. Construction is cold;
     * presentation/backend collaborators are read from the manager only at their
     * original operation points, and physical residency remains session-owned.
     * Mutation admission uses AudioManager.this's existing wrapper monitor.
     */
    private final class SourcePublication {
        /** Volatile publication of the complete immutable base dependency tuple. */
        private volatile BaseAudioSource baseAudioSource =
                new BaseAudioSource(null, null, null, null, null, 0);
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


        private BaseAudioSource base() { return baseAudioSource; }
        private DonorAudioSource donor(String gameId) { return donorAudioSources.get(gameId); }
        private Map<String, DonorAudioSource> donors() { return donorAudioSources; }
        private Set<String> donorGameIds() { return donorAudioSources.keySet(); }
        private DonorSfxBinding soundBinding(GameSound sound) { return donorSoundBindings.get(sound); }
        private Map<GameMusic, Integer> musicBinding(String gameId) { return donorMusicBindings.get(gameId); }

        private Set<AudioLogicalSnapshot.DonorSfxBindingSnapshot> captureSoundBindings() {
            Set<AudioLogicalSnapshot.DonorSfxBindingSnapshot> bindings = new LinkedHashSet<>();
            for (Map.Entry<GameSound, DonorSfxBinding> entry : donorSoundBindings.entrySet()) {
                DonorSfxBinding binding = entry.getValue();
                bindings.add(new AudioLogicalSnapshot.DonorSfxBindingSnapshot(
                        entry.getKey(), binding.gameId(), binding.sfxId()));
            }
            return bindings;
        }

        private Map<GameSound, DonorSfxBinding> copySoundBindings() {
            return new EnumMap<>(donorSoundBindings);
        }

        private Map<GameSound, DonorSfxBinding> materializeSoundBindings(
                Set<AudioLogicalSnapshot.DonorSfxBindingSnapshot> bindings) {
            Map<GameSound, DonorSfxBinding> materialized = new EnumMap<>(GameSound.class);
            for (AudioLogicalSnapshot.DonorSfxBindingSnapshot binding : bindings) {
                materialized.put(binding.sound(), new DonorSfxBinding(binding.donorGameId(), binding.sfxId()));
            }
            return materialized;
        }

        private void restoreSoundBindings(Set<AudioLogicalSnapshot.DonorSfxBindingSnapshot> bindings) {
            donorSoundBindings.clear();
            for (AudioLogicalSnapshot.DonorSfxBindingSnapshot binding : bindings) {
                donorSoundBindings.put(binding.sound(), new DonorSfxBinding(binding.donorGameId(), binding.sfxId()));
            }
        }

        private void restoreSoundBindings(Map<GameSound, DonorSfxBinding> bindings) {
            donorSoundBindings.clear();
            donorSoundBindings.putAll(bindings);
        }

        private final class BaseBackendAttempt {
            private final GameAudioProfile candidate;
            private boolean attempted;

            private BaseBackendAttempt(GameAudioProfile candidate) {
                this.candidate = candidate;
            }

            private void run() {
                if (backend != null) {
                    attempted = true;
                    backend.setAudioProfile(candidate);
                }
            }
        }

        private final class DonorBackendAttempt {
            private final GameAudioProfile candidate;
            private final DonorAudioSource previous;
            private boolean attempted;

            private DonorBackendAttempt(GameAudioProfile candidate, DonorAudioSource previous) {
                this.candidate = candidate;
                this.previous = previous;
            }

            private void run() {
                boolean clearsPreviousProfile = candidate == null
                        && previous != null && previous.profile() != null;
                if (backend != null && (candidate != null || clearsPreviousProfile)) {
                    attempted = true;
                    backend.registerAudioProfileCoordHandlers(candidate);
                }
            }
        }

        private void initializeStandalone(GameAudioProfile profile) {
            GameAudioProfile resolvedProfile = java.util.Objects.requireNonNull(profile, "profile");
            baseAudioSource = new BaseAudioSource(null, resolvedProfile, null, null,
                    resolvedProfile.getSequencerConfig(), 1);
        }

        private void setAudioProfile(GameAudioProfile audioProfile) {
            if (reinstallsInstalledSource(audioProfile, baseAudioSource.rom())) {
                return;
            }
            beginSourceMutation();
            try {
                BaseAudioSource previous = baseAudioSource;
                long candidateGeneration = nextGeneration(previous.generation());
                Rom sourceRom = previous.rom();
                SmpsLoader candidateLoader = audioProfile != null
                        && sourceRom != null
                        ? audioProfile.createSmpsLoader(sourceRom) : null;
                DacData candidateDac = loadDac(candidateLoader);
                SmpsSequencerConfig candidateConfig = audioProfile != null
                        ? audioProfile.getSequencerConfig() : null;
                PresentationBinding.PresentationCoordHandlerPreparation presentationPreparation =
                        presentationBinding.prepareBaseCoordination(audioProfile);
                BaseBackendAttempt backendConfiguration = new BaseBackendAttempt(audioProfile);
                try {
                    presentationBinding.applyBaseCoordination(presentationPreparation, backendConfiguration);
                } catch (RuntimeException | Error failure) {
                    if (backendConfiguration.attempted) {
                        try {
                            backend.setAudioProfile(previous.profile());
                        } catch (RuntimeException | Error restoreFailure) {
                            failure.addSuppressed(restoreFailure);
                        }
                    }
                    throw failure;
                }
                presentationBinding.publishPresentationCoordHandlers(presentationPreparation);
                baseAudioSource = new BaseAudioSource(
                        previous.rom(), audioProfile,
                        candidateLoader, candidateDac,
                        candidateConfig, candidateGeneration);
                mutatorMusicIntent(AudioMutatorAccess.Kind.SOURCE, 0);
                presentationBinding.profilePublished();
            } finally {
                endSourceMutation();
            }
        }

        private void installMatchedSource(Rom rom, GameAudioProfile audioProfile) {
            beginSourceMutation();
            try {
                BaseAudioSource previous = baseAudioSource;
                if (previous.profile() == audioProfile && previous.rom() == rom) {
                    return;
                }
                long candidateGeneration = nextGeneration(previous.generation());
                SmpsLoader candidateLoader = audioProfile != null && rom != null
                        ? audioProfile.createSmpsLoader(rom) : null;
                DacData candidateDac = loadDac(candidateLoader);
                SmpsSequencerConfig candidateConfig = audioProfile != null
                        ? audioProfile.getSequencerConfig() : null;
                PresentationBinding.PresentationCoordHandlerPreparation presentationPreparation =
                        presentationBinding.prepareBaseCoordination(audioProfile);
                BaseBackendAttempt backendConfiguration = new BaseBackendAttempt(audioProfile);
                try {
                    presentationBinding.applyBaseCoordination(presentationPreparation, backendConfiguration);
                } catch (RuntimeException | Error failure) {
                    if (backendConfiguration.attempted) {
                        try {
                            backend.setAudioProfile(previous.profile());
                        } catch (RuntimeException | Error restoreFailure) {
                            failure.addSuppressed(restoreFailure);
                        }
                    }
                    throw failure;
                }
                presentationBinding.publishPresentationCoordHandlers(presentationPreparation);
                baseAudioSource = new BaseAudioSource(
                        rom, audioProfile, candidateLoader, candidateDac,
                        candidateConfig, candidateGeneration);
                mutatorMusicIntent(AudioMutatorAccess.Kind.SOURCE, 0);
                presentationBinding.profilePublished();
            } finally {
                endSourceMutation();
            }
        }

        private void setRom(Rom rom) {
            if (reinstallsInstalledSource(baseAudioSource.profile(), rom)) {
                return;
            }
            beginSourceMutation();
            try {
                BaseAudioSource previous = baseAudioSource;
                long candidateGeneration = nextGeneration(previous.generation());
                SmpsLoader candidateLoader = previous.profile() != null
                        && rom != null
                        ? previous.profile().createSmpsLoader(rom) : null;
                DacData candidateDac = loadDac(candidateLoader);
                baseAudioSource = new BaseAudioSource(
                        rom, previous.profile(), candidateLoader, candidateDac,
                        previous.config(),
                        candidateGeneration);
                mutatorMusicIntent(AudioMutatorAccess.Kind.SOURCE, 0);
                presentationBinding.romPublished();
            } finally {
                endSourceMutation();
            }
        }

        /**
         * Whether installing {@code profile} with {@code rom} would reinstall the
         * very source already installed: the same profile and ROM instances, with
         * its loader built. That is a same-game level entry
         * ({@code LevelManager.configureAudio} on every load), not a source change.
         * The ROM's Z80 driver keeps running into the level, so the presentation
         * device (chip, filter, PSG and buffer state, the running session) is kept.
         * A new ROM or profile instance, a reset, or a donor or mod source still
         * goes through the full replacement.
         */
        private boolean reinstallsInstalledSource(GameAudioProfile profile, Rom rom) {
            BaseAudioSource installed = baseAudioSource;
            return profile != null && rom != null
                    && profile == installed.profile() && rom == installed.rom()
                    && installed.loader() != null;
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

        private AudioPresentationCommandResolver.SourceAccess baseSfxSource(
                BaseAudioSource source) {
            return new AudioPresentationCommandResolver.SourceAccess(
                    baseGameId(source), source.generation(), source.loader(),
                    source.dac(), source.config(), sfxPolicyFor(source.profile()),
                    ordinaryMusicSfxPolicyFor(source.profile()));
        }

        private AudioPresentationCommandResolver.SourceAccess donorSfxSource(
                String gameId, DonorAudioSource source) {
            return new AudioPresentationCommandResolver.SourceAccess(
                    gameId, source.generation(), source.loader(), source.dac(),
                    source.config(), sfxPolicyFor(source.sfxPolicyProfile()),
                    ordinaryMusicSfxPolicyFor(source.profile()));
        }

        private DonorAudioSource completeLegacyDonorSource(
                String gameId, DonorAudioSource observed) {
            if (observed.config() != null || observed.profile() != null) {
                return observed;
            }
            DonorAudioSource current = donorAudioSources.get(gameId);
            if (current == null || current != observed) {
                return current != null ? current : observed;
            }
            BaseAudioSource legacyOwner = baseAudioSource;
            if (legacyOwner.config() == null || legacyOwner.profile() == null) {
                return observed;
            }
            DonorAudioSource completed = new DonorAudioSource(
                    observed.loader(), observed.dac(), legacyOwner.config(),
                    null, legacyOwner.profile(), observed.generation());
            publishDonorSource(gameId, completed);
            return completed;
        }

        private void registerDonorLoader(
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
                BaseAudioSource legacyOwner = baseAudioSource;
                SmpsSequencerConfig resolvedConfig = config != null
                        ? config
                        : donorProfile != null
                        ? donorProfile.getSequencerConfig()
                        : legacyOwner.config();
                GameAudioProfile resolvedSfxPolicyProfile = donorProfile != null
                        ? donorProfile
                        : config == null ? legacyOwner.profile() : null;
                DonorAudioSource previous = donorAudioSources.get(resolvedGameId);
                long retainedGeneration = donorGenerationCounters.getOrDefault(
                        resolvedGameId,
                        previous != null ? previous.generation() : 0L);
                long candidateGeneration = nextGeneration(retainedGeneration);
                PresentationBinding.PresentationCoordHandlerPreparation presentationPreparation =
                        presentationBinding.prepareDonorCoordination(donorProfile);
                DonorBackendAttempt backendConfiguration = new DonorBackendAttempt(donorProfile, previous);
                try {
                    presentationBinding.applyDonorCoordination(presentationPreparation, backendConfiguration);
                } catch (RuntimeException | Error failure) {
                    Throwable restoreFailure =
                            backendConfiguration.attempted
                                    ? restoreDonorBackendConfiguration(previous)
                                    : null;
                    if (restoreFailure != null) {
                        removeDonorSource(
                                resolvedGameId, candidateGeneration);
                        failure.addSuppressed(restoreFailure);
                    }
                    throw failure;
                }
                presentationBinding.publishPresentationCoordHandlers(presentationPreparation);
                publishDonorSource(resolvedGameId, new DonorAudioSource(
                        resolvedLoader, resolvedDac, resolvedConfig, donorProfile,
                        resolvedSfxPolicyProfile, candidateGeneration));
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

        private void registerDonorMusicMap(String gameId, Map<GameMusic, Integer> musicMap) {
            if (gameId == null || musicMap == null || musicMap.isEmpty()) {
                return;
            }
            donorMusicBindings.put(gameId, Map.copyOf(musicMap));
        }

        private void registerDonorSound(GameSound sound, String gameId, int sfxId) {
            donorSoundBindings.put(sound, new DonorSfxBinding(gameId, sfxId));
        }

        private void clearDonorAudio() {
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

        private DonorKeyState captureDonorKeyState(String gameId) {
            String key = requireDonorGameId(gameId);
            Map<GameSound, DonorSfxBinding> sounds = new EnumMap<>(GameSound.class);
            donorSoundBindings.forEach((sound, binding) -> {
                if (key.equals(binding.gameId())) {
                    sounds.put(sound, binding);
                }
            });
            return new DonorKeyState(key, donorAudioSources.get(key),
                    donorMusicBindings.get(key), sounds);
        }

        private void restoreDonorKeyState(DonorKeyState state) {
            beginSourceMutation();
            try {
                String key = state.gameId;
                long generation = nextGeneration(
                        donorGenerationCounters.getOrDefault(key, 0L));
                if (state.source == null) {
                    removeDonorSource(key, generation);
                } else {
                    DonorAudioSource previous = state.source;
                    publishDonorSource(key, new DonorAudioSource(
                            previous.loader(), previous.dac(), previous.config(),
                            previous.profile(), previous.sfxPolicyProfile(),
                            generation));
                }
                if (state.music == null) {
                    donorMusicBindings.remove(key);
                } else {
                    donorMusicBindings.put(key, state.music);
                }
                donorSoundBindings.values().removeIf(
                        binding -> key.equals(binding.gameId()));
                donorSoundBindings.putAll(state.sounds);
                Throwable failure = restoreDonorBackendConfiguration(state.source);
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (failure instanceof Error error) {
                    throw error;
                }
            } finally {
                endSourceMutation();
            }
        }

        private void reset() {
            beginSourceMutation();
            try {
                heldReverseRestore.invalidatePreparation();
                if (backend != null) {
                    backend.stopPlayback();
                }
                this.baseAudioSource =
                        new BaseAudioSource(null, null, null, null, null, 0);
                resetNonSourceState();
                clearDonorAudioState();
                donorGenerationCounters = Map.of();
            } finally {
                endSourceMutation();
            }
        }

        private AudioPresentationCommandResolver.SourceAccess sourceFor(
                com.openggf.audio.presentation.SmpsAssetKey.Route route,
                String donorGameId) {
            return switch (route) {
                case BASE_MUSIC, BASE_ID, BASE_NAME -> {
                    BaseAudioSource source = baseAudioSource;
                    yield new AudioPresentationCommandResolver.SourceAccess(
                            baseGameId(source), source.generation(),
                            source.loader(), source.dac(), source.config(),
                            sfxPolicyFor(source.profile()),
                            ordinaryMusicSfxPolicyFor(source.profile()));
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
                            sfxPolicyFor(source != null
                                    ? source.sfxPolicyProfile() : null),
                            source != null && source.profile() != null
                                    ? source.profile().getOrdinaryMusicSfxPolicy()
                                    : GameAudioProfile.OrdinaryMusicSfxPolicy.STOP_ALL);
                }
                case FALLBACK_NAME -> throw new IllegalArgumentException(
                        "fallback assets have no SMPS source");
            };
        }
    }

    /**
     * Manager-lifetime logical publication only. Collaborators are read from
     * AudioManager at the original operation boundaries, never retained here.
     * Timeline history, held selection and physical resources keep their owners.
     */
    private final class LogicalPublication {
        private boolean ringLeft = true;
        /**
         * Count of logical audio restores actually published to the presentation
         * producer. Held rewind defers per-frame restores and publishes exactly one
         * at release; this counter is the observable for that contract now that the
         * legacy backend restore (which tests counted) is gone.
         */
        private int logicalRestorePublications;

        private boolean rawRingLeft() {
            return ringLeft;
        }

        private void setRingLeft(boolean value) {
            ringLeft = value;
        }

        private void playFallbackRing(float pitch) {
            AudioManager.this.playGameSfxResolved(
                    ringLeft ? GameSound.RING_LEFT : GameSound.RING_RIGHT, pitch);
            ringLeft = !ringLeft;
        }

        /**
         * The ring left/right alternation callers should observe. A game whose
         * request service owns dispatch-time alternation (Sonic 2's
         * {@code zPlaySound_CheckRing}) reports its own value; {@code ringLeft}
         * never advances for such a game, since {@link #playSfx} hands ring
         * requests straight to the mailbox and returns before its own
         * caller-side toggle. Other games have no request service, so {@code
         * ringLeft} stays the sole source of truth for them.
         */
        private boolean currentRingLeft() {
            if (presentationBinding.shadowRequestService != null) {
                Boolean serviceRingLeft = presentationBinding.shadowRequestService.ringLeft();
                if (serviceRingLeft != null) {
                    return serviceRingLeft;
                }
            }
            return ringLeft;
        }

        private AudioLogicalSnapshot captureLogicalSnapshot() {
            presentationBinding.ensureShadowPresentation();
            Set<String> donorGameIds = new LinkedHashSet<>();
            donorGameIds.addAll(sourcePublication.donorGameIds());

            Set<AudioLogicalSnapshot.DonorSfxBindingSnapshot> donorBindings =
                    sourcePublication.captureSoundBindings();

            return new AudioLogicalSnapshot(
                    currentRingLeft(),
                    commandTimeline.currentFrame(),
                    commandTimeline.nextOrder(),
                    commandTimeline.entryCount(),
                    presentationBinding.shadowProducer.snapshot(),
                    donorGameIds,
                    donorBindings,
                    presentationBinding.shadowRequestService == null
                            ? null : presentationBinding.shadowRequestService.snapshot());
        }

        private boolean restoreLogicalSnapshotNow(
                AudioLogicalSnapshot snapshot, boolean preservePresentation) {
            AudioPresentationSnapshot previousPresentation =
                    presentationBinding.shadowProducer.snapshot();
            AudioPresentationForwardService.Snapshot previousForwardService =
                    presentationBinding.shadowRequestService == null
                            ? null : presentationBinding.shadowRequestService.snapshot();
            boolean previousRingLeft = ringLeft;
            long previousTimelineFrame = commandTimeline.currentFrame();
            int previousTimelineOrder = commandTimeline.nextOrder();
            Map<GameSound, DonorSfxBinding> previousBindings =
                    sourcePublication.copySoundBindings();
            try {
                if ((presentationBinding.shadowRequestService == null)
                        != (snapshot.forwardServiceSnapshot() == null)) {
                    throw new IllegalArgumentException(
                            "audio snapshot forward-service ownership differs from the live profile");
                }
                presentationBinding.shadowProducer.restore(snapshot.presentation(), presentationBinding.shadowFactory,
                        preservePresentation);
                if (presentationBinding.shadowRequestService != null) {
                    presentationBinding.shadowRequestService.restore(snapshot.forwardServiceSnapshot());
                }
                ringLeft = snapshot.ringLeft();
                commandTimeline.restoreCursor(snapshot.commandTimelineFrame(),
                        snapshot.commandTimelineNextOrder());
                sourcePublication.restoreSoundBindings(snapshot.donorBindings());
                logicalRestorePublications++;
                return true;
            } catch (RuntimeException failure) {
                ringLeft = previousRingLeft;
                commandTimeline.restoreCursor(previousTimelineFrame,
                        previousTimelineOrder);
                sourcePublication.restoreSoundBindings(previousBindings);
                try {
                    presentationBinding.shadowProducer.restore(previousPresentation, presentationBinding.shadowFactory,
                            preservePresentation);
                    if (presentationBinding.shadowRequestService != null) {
                        presentationBinding.shadowRequestService.restore(previousForwardService);
                    }
                } catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                AudioDiagnosticObserverException.rethrowIfPresent(failure);
                LOGGER.log(Level.WARNING,
                        "Audio snapshot restore failed; retained prior state",
                        failure);
                return false;
            }
        }

        private void publishHeldRestoreLedger(
                AudioLogicalSnapshot selected,
                Map<GameSound, DonorSfxBinding> publishedBindings) {
            logicalRestorePublications++;
            ringLeft = selected.ringLeft();
            commandTimeline.restoreCursor(
                    selected.commandTimelineFrame(),
                    selected.commandTimelineNextOrder());
            sourcePublication.restoreSoundBindings(publishedBindings);
        }

        private int logicalRestorePublications() {
            return logicalRestorePublications;
        }

        private void resetLogicalRestorePublicationCount() {
            logicalRestorePublications = 0;
        }
    }

    /**
     * Manager-lifetime ownership of the held logical target and its release protocol.
     * Collaborators are read from AudioManager at their original execution points;
     * construction stores no producer, session, factory or presentation resources.
     */
    private final class HeldReverseRestore {
        private boolean reverseAudioPresentationActive;
        /** The selected metadata can differ from the producer's acknowledged preparation. */
        private AudioLogicalSnapshot deferredReverseLogicalSnapshot;
        private boolean deferredReverseLogicalPrepared;
        /** Fresh boundary targets survive retries and bypass later transient cleanup. */
        private boolean postBoundaryReverseTarget;
        /** One-shot test control, consumed at release entry even if that attempt fails. */
        private ReverseReleaseFailurePoint failNextReverseRelease;

        private void restoreLogicalSnapshot(AudioLogicalSnapshot snapshot) {
            if (snapshot == null) {
                return;
            }
            presentationBinding.ensureShadowPresentation();
            if (reverseAudioPresentationActive) {
                deferredReverseLogicalSnapshot = snapshot;
                return;
            }
            restoreLogicalSnapshotNow(snapshot, false);
        }

        private void replayTimelineCommandLogically(AudioCommand command) {
            if (command == null) {
                return;
            }
            stageDeferredPresentationCommand(command);
            refreshDeferredReverseLogicalSnapshot();
        }

        /**
         * Replays one logical command into detached presentation metadata and a
         * transactionally borrowed view of the one persistent SMPS session. The
         * session is rolled back byte-for-byte after the staged snapshot is
         * captured, so the live shadow and its rewind/history cursor remain
         * untouched until held release invokes the producer restore.
         */
        private void stageDeferredPresentationCommand(AudioCommand command) {
            stageDeferredPresentationCommand(command, null, null);
        }

        private void stageDeferredPresentationCommand(AudioCommand command, Boolean nativePause,
                AudioPresentationForwardService.Snapshot nativeForward) {
            if (command == null && nativePause == null) {
                return;
            }
            presentationBinding.ensureShadowPresentation();
            if (deferredReverseLogicalSnapshot == null) {
                if (nativePause != null) {
                    var previousForward = presentationBinding.shadowRequestService.snapshot();
                    SmpsDriverSession.LiveMutationToken token = presentationBinding.shadowSmpsSession.captureLiveMutation();
                    try {
                        presentationBinding.shadowSmpsSession.replayNativePause(nativePause);
                        presentationBinding.shadowRequestService.restore(nativeForward);
                        presentationBinding.shadowSmpsSession.commitLiveMutation(token);
                    } catch (RuntimeException failure) {
                        try { presentationBinding.shadowRequestService.restore(previousForward); }
                        catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
                        try { presentationBinding.shadowSmpsSession.rollbackLiveMutation(token); }
                        catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
                        throw failure;
                    }
                } else {
                    presentationBinding.shadowResolver.submit(command);
                    presentationBinding.shadowProducer.applyPendingCommandsAtOwnerBoundary();
                    logicalPublication.setRingLeft(managerRingAfter(logicalPublication.rawRingLeft(), command));
                }
                return;
            }
            AudioLogicalSnapshot selected = deferredReverseLogicalSnapshot;
            SmpsCoordFlagRuntimeState.Snapshot liveCoord =
                    presentationBinding.presentationCoordFlagHandlers.state().snapshot();
            AudioPresentationCommandQueue stagedCommands =
                    new AudioPresentationCommandQueue();
            AudioVoiceRegistry stagedRegistry = new AudioVoiceRegistry(
                    presentationBinding.shadowFactory, presentationBinding.shadowFactory, presentationBinding.presentationCoordFlagHandlers,
                    warning -> LOGGER.warning(
                            "Presentation rewind staging: " + warning),
                    presentationBinding.shadowSmpsSession);
            AudioPresentationDependencyResolver.DiagnosticTransaction
                    stagingDiagnostics =
                    presentationBinding.shadowFactory.beginDiagnosticTransaction();
            SmpsDriverSession.LiveMutationToken sessionMutation = null;
            RuntimeException primaryFailure = null;
            AudioLogicalSnapshot stagedSelection;
            try {
                stagedRegistry.restore(selected.presentation(), presentationBinding.shadowFactory);
                String[] resolutionFailure = new String[1];
                AudioPresentationCommandResolver stagedResolver =
                        new AudioPresentationCommandResolver(
                                stagedCommands, presentationBinding.shadowFactory,
                                new ShadowSources(
                                        (Math.max(1, backend != null
                                                ? backend.outputSampleRate()
                                                : 48_000)
                                                + presentationBinding.configuredFrameRate() - 1)
                                                / presentationBinding.configuredFrameRate()),
                                warning -> resolutionFailure[0] = warning,
                                () -> true,
                                () -> {
                                    throw new IllegalStateException(
                                            "detached staging queue exceeded capacity");
                                });
                long nextVoice = selected.presentation().voices().stream()
                        .mapToLong(voice -> switch (voice) {
                            case PresentationVoiceSnapshot.Sample sample ->
                                    sample.voiceId();
                            case PresentationVoiceSnapshot.Streamed streamed ->
                                    streamed.voiceId();
                        })
                        .max().orElse(0L) + 1L;
                nextVoice = Math.max(
                        nextVoice, selected.presentation().nextVoiceId());
                stagedResolver.reserveVoiceIdsThrough(nextVoice);
                if (command != null) stagedResolver.submit(command);
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
                if (selected.presentation().smpsSession() == null
                        || selected.presentation().smpsLogical() == null) {
                    throw new IllegalArgumentException(
                            "held-rewind staging requires one SMPS session/logical "
                                    + "snapshot pair");
                }
                SmpsDriverSession.PreparedRestore preparedSession =
                        presentationBinding.shadowSmpsSession.prepareRestore(
                                selected.presentation().smpsSession(),
                                selected.presentation().smpsLogical(),
                                presentationBinding.shadowFactory::resolveDac);
                sessionMutation = presentationBinding.shadowSmpsSession.captureLiveMutation();
                presentationBinding.shadowSmpsSession.commitRestore(preparedSession);
                if (nativePause != null) presentationBinding.shadowSmpsSession.replayNativePause(nativePause);
                stagedCommands.applyPendingAtomically(commandToApply ->
                        AudioPresentationSessionCommandApplier.apply(
                                presentationBinding.shadowSmpsSession, stagedRegistry,
                                commandToApply));
                AudioPresentationSnapshot staged = stagedRegistry.snapshot();
                boolean stagedManagerRingLeft =
                        managerRingAfter(selected.ringLeft(), command);
                stagedSelection = new AudioLogicalSnapshot(
                        stagedManagerRingLeft,
                        selected.commandTimelineFrame(),
                        selected.commandTimelineNextOrder(),
                        selected.commandEntryCount(),
                        staged,
                        selected.donorGameIds(),
                        selected.donorBindings(),
                        nativePause == null ? selected.forwardServiceSnapshot() : nativeForward);
            } catch (RuntimeException failure) {
                primaryFailure = failure;
                throw failure;
            } finally {
                try {
                    if (sessionMutation != null) {
                        presentationBinding.shadowSmpsSession.rollbackLiveMutation(
                                sessionMutation);
                    }
                } catch (RuntimeException rollbackFailure) {
                    if (primaryFailure != null) {
                        primaryFailure.addSuppressed(rollbackFailure);
                    } else {
                        throw rollbackFailure;
                    }
                } finally {
                    try {
                        stagedRegistry.clear();
                    } finally {
                        try {
                            stagingDiagnostics.endPreparation();
                        } finally {
                            try {
                                stagingDiagnostics.discard();
                            } finally {
                                presentationBinding.presentationCoordFlagHandlers.state()
                                        .restore(liveCoord);
                            }
                        }
                    }
                }
            }
            publishDeferredNativeSelection(stagedSelection);
        }

        /** Invalidate an older prepared target before publishing a replacement. */
        private void publishDeferredNativeSelection(AudioLogicalSnapshot selected) {
            if (deferredReverseLogicalPrepared) {
                presentationBinding.shadowProducer.discardPreparedRestoreSelection();
                deferredReverseLogicalPrepared = false;
            }
            deferredReverseLogicalSnapshot = selected;
        }

        /** Internal native PauseGame operation; preserves chip time and mailbox ownership. */
        private void requestNativePause(boolean paused) {
            if (rewindReplaySuppressionDepth > 0) return;
            presentationBinding.ensureShadowPresentation();
            presentationBinding.shadowSmpsSession.requireNativePauseSupported();
            if (!(presentationBinding.shadowRequestService instanceof AudioNativePauseAccess.Mailbox mailbox)) {
                throw new UnsupportedOperationException("native pause mailbox is unavailable");
            }
            if (deferredReverseLogicalSnapshot == null) {
                var previous = presentationBinding.shadowRequestService.snapshot();
                try { mailbox.submitNativePause(paused); }
                catch (RuntimeException failure) {
                    try { presentationBinding.shadowRequestService.restore(previous); }
                    catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
                    throw failure;
                }
                return;
            }
            AudioLogicalSnapshot selected = deferredReverseLogicalSnapshot;
            AudioPresentationForwardService.Snapshot previous = presentationBinding.shadowRequestService.snapshot();
            AudioPresentationForwardService.Snapshot staged;
            RuntimeException primary = null;
            try {
                presentationBinding.shadowRequestService.restore(selected.forwardServiceSnapshot());
                mailbox.submitNativePause(paused);
                staged = presentationBinding.shadowRequestService.snapshot();
            } catch (RuntimeException failure) {
                primary = failure;
                throw failure;
            } finally {
                try { presentationBinding.shadowRequestService.restore(previous); }
                catch (RuntimeException rollback) {
                    if (primary != null) primary.addSuppressed(rollback);
                    else throw rollback;
                }
            }
            publishDeferredNativeSelection(new AudioLogicalSnapshot(selected.ringLeft(),
                    selected.commandTimelineFrame(), selected.commandTimelineNextOrder(),
                    selected.commandEntryCount(), selected.presentation(), selected.donorGameIds(),
                    selected.donorBindings(), staged));
        }

        private void replayNativePause(boolean paused, AudioPresentationForwardService.Snapshot forward) {
            Objects.requireNonNull(forward, "forward");
            presentationBinding.ensureShadowPresentation();
            // Validate the source-owned snapshot before publishing any detached
            // session selection; a failed validation restores the live source.
            var previous = presentationBinding.shadowRequestService.snapshot();
            RuntimeException primary = null;
            try { presentationBinding.shadowRequestService.restore(forward); }
            catch (RuntimeException failure) { primary = failure; throw failure; }
            finally {
                try { presentationBinding.shadowRequestService.restore(previous); }
                catch (RuntimeException rollback) {
                    if (primary != null) primary.addSuppressed(rollback);
                    else throw rollback;
                }
            }
            stageDeferredPresentationCommand(null, paused, forward);
            refreshDeferredReverseLogicalSnapshot();
        }

        private void advanceNativeHistoryCursor(int cursor) {
            presentationBinding.ensureShadowPresentation();
            if (deferredReverseLogicalSnapshot == null) {
                presentationBinding.shadowSmpsSession.setNativeHistoryCursor(cursor);
                return;
            }
            AudioLogicalSnapshot selected = deferredReverseLogicalSnapshot;
            AudioPresentationSnapshot p = selected.presentation();
            var s = p.smpsSession();
            if (s == null) return;
            var session = new com.openggf.audio.session.SmpsDriverSessionSnapshot(s.initialized(),
                    s.pendingGlobalCommand(), s.profile(), s.selectedDacSource(), s.speedShoesEnabled(),
                    s.speedMultiplier(), s.musicTempoPercent(), s.ringLeft(), s.musicFmDacTrackCount(),
                    s.segaPcmTransport(), s.physical(), s.nativePause(), cursor);
            var presentation = new AudioPresentationSnapshot(p.nextVoiceId(), p.voices(), p.activeMusic(),
                    p.overrideStack(), p.rawPcmVoiceId(), p.fmMuteMask(), p.fmSoloMask(), p.psgMuteMask(),
                    p.psgSoloMask(), p.sfxBlocked(), p.sfxBlockHeldThroughFadeIn(), p.pendingRestore(),
                    p.speedShoesEnabled(), p.speedMultiplier(), p.ringLeft(), p.coordFlagRuntimeState(),
                    session, p.smpsLogical(), p.forwardTiming());
            publishDeferredNativeSelection(new AudioLogicalSnapshot(selected.ringLeft(),
                    selected.commandTimelineFrame(), selected.commandTimelineNextOrder(),
                    selected.commandEntryCount(), presentation, selected.donorGameIds(),
                    selected.donorBindings(), selected.forwardServiceSnapshot()));
        }

        private boolean managerRingAfter(
                boolean current, AudioCommand command) {
            if (command instanceof AudioCommand.ResetRingAlternation reset) {
                return reset.ringLeft();
            }
            if (command instanceof AudioCommand.RetainGlobalStop
                    || command instanceof AudioCommand
                    .StopSegaPcmAndRetainGlobalStop) {
                return true;
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
                    selected.donorBindings(),
                    selected.forwardServiceSnapshot());
        }

        private boolean afterRewindRestore(
                int frame, AudioPresentationPolicy policy) {
            if (policy == null) {
                return true;
            }
            presentationBinding.ensureShadowPresentation();
            boolean preservePostBoundarySources =
                    postBoundaryReverseTarget;
            if (policy != AudioPresentationPolicy.SUPPRESSED_INTERNAL_RESTORE) {
                if (!AudioManager.this.endReverseAudioPresentation()) {
                    return false;
                }
                presentationBinding.shadowProducer.applyPendingCommandsAtOwnerBoundary();
            }
            if (preservePostBoundarySources) {
                return true;
            }
            switch (policy) {
                case SUPPRESSED_INTERNAL_RESTORE -> {
                }
                case STOP_TRANSIENT_SFX_RESYNC_MUSIC -> {
                    presentationBinding.shadowCommands.submit(
                            new AudioPresentationCommand.RestoreMusicOverride(),
                            () -> true,
                            AudioManager.this::drainShadowCommandsAtOwnerBoundary);
                    presentationBinding.shadowProducer
                            .stopTransientVoicesThenApplyPendingCommandsAtOwnerBoundary();
                }
                case STOP_TRANSIENT_SFX ->
                        presentationBinding.shadowProducer.stopTransientVoicesAtOwnerBoundary();
                case STOP_ALL_PRESENTATION -> {
                    presentationBinding.shadowCommands.submit(
                            new AudioPresentationCommand.HardReset(),
                            () -> true,
                            AudioManager.this::drainShadowCommandsAtOwnerBoundary);
                    presentationBinding.shadowProducer.applyPendingCommandsAtOwnerBoundary();
                    presentationBinding.shadowProducer.clearHistory();
                    presentationBinding.shadowParity.historyBoundary();
                }
            }
            return true;
        }

        private void beginReverseAudioPresentation() {
            presentationBinding.ensureShadowPresentation();
            // Check presentation ownership before replacing the logical rewind
            // ledger: a scoped recorded replay may already own audible reverse.
            presentationBinding.shadowProducer.beginReverse(1.0);
            deferredReverseLogicalSnapshot = null;
            deferredReverseLogicalPrepared = false;
            postBoundaryReverseTarget = false;
            reverseAudioPresentationActive = true;
        }

        private boolean isReverseAudioPresentationActive() {
            return reverseAudioPresentationActive;
        }

        private boolean isReverseAudioOutputActive() {
            return reverseAudioPresentationActive
                    || presentationBinding.shadowProducer != null && presentationBinding.shadowProducer.isReplayReverseActive();
        }

        /** Successful producer return is the publication boundary, not universal atomicity. */
        private boolean endReverseAudioPresentation() {
            AudioLogicalSnapshot selected = deferredReverseLogicalSnapshot;
            boolean preparedDuringAttempt = false;
            boolean producerReturned = false;
            AudioPresentationForwardService.Snapshot previousForward = null;
            boolean forwardRestoreAttempted = false;
            // Consumed once per entry, whichever exit this attempt takes, so an
            // armed injection can never leak into a later unrelated release.
            ReverseReleaseFailurePoint injectFailure = failNextReverseRelease;
            failNextReverseRelease = null;
            Map<GameSound, DonorSfxBinding> publishedBindings = null;
            try {
                if (selected != null && !deferredReverseLogicalPrepared) {
                    if (!AudioManager.this.commitDeferredReverseLogicalRestore()) {
                        return false;
                    }
                    preparedDuringAttempt = true;
                }
                if (selected != null) {
                    // Materialize bindings before calling the producer; later
                    // failures may still leave the characterized partial effects.
                    publishedBindings = sourcePublication.materializeSoundBindings(
                            selected.donorBindings());
                    if ((presentationBinding.shadowRequestService == null)
                            != (selected.forwardServiceSnapshot() == null)) {
                        throw new IllegalArgumentException("reverse target forward-service ownership differs");
                    }
                    if (presentationBinding.shadowRequestService != null) {
                        previousForward = presentationBinding.shadowRequestService.snapshot();
                        forwardRestoreAttempted = true;
                        presentationBinding.shadowRequestService.restore(selected.forwardServiceSnapshot());
                    }
                }
                if (injectFailure
                        == ReverseReleaseFailurePoint.BEFORE_PRODUCER_COMMIT) {
                    throw new IllegalStateException(
                            "injected reverse release publication failure");
                }
                if (presentationBinding.shadowProducer != null) {
                    presentationBinding.shadowProducer.endReverse(
                            !postBoundaryReverseTarget);
                }
                producerReturned = true;
                if (injectFailure
                        == ReverseReleaseFailurePoint.AFTER_PRODUCER_COMMIT) {
                    throw new IllegalStateException(
                            "injected reverse release publication failure");
                }
            } catch (RuntimeException failure) {
                if (producerReturned) {
                    publishReverseReleaseLedger(selected, publishedBindings);
                    AudioDiagnosticObserverException.rethrowIfPresent(failure);
                    LOGGER.log(Level.WARNING,
                            "Audio reverse release publication failed after the "
                                    + "producer commit; completed the release from "
                                    + "the pre-commit ledger", failure);
                    return true;
                }
                if (forwardRestoreAttempted) {
                    try { presentationBinding.shadowRequestService.restore(previousForward); }
                    catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
                }
                if (preparedDuringAttempt) {
                    try {
                        presentationBinding.shadowProducer.discardPreparedRestoreSelection();
                    } catch (RuntimeException discardFailure) {
                        failure.addSuppressed(discardFailure);
                    }
                    deferredReverseLogicalPrepared = false;
                }
                AudioDiagnosticObserverException.rethrowIfPresent(failure);
                LOGGER.log(Level.WARNING,
                        "Audio reverse release failed; retained prior live state",
                        failure);
                return false;
            }
            publishReverseReleaseLedger(selected, publishedBindings);
            return true;
        }

        private boolean preparePostBoundaryReverseRelease() {
            if (!reverseAudioPresentationActive) {
                return true;
            }
            presentationBinding.ensureShadowPresentation();
            if (!postBoundaryReverseTarget) {
                AudioLogicalSnapshot fresh;
                try {
                    // A level/seamless transition may initialize presentation
                    // sources after the final frame packet. Publish that ledger at
                    // this owner boundary without advancing PCM cadence/history,
                    // then capture the complete fresh producer state.
                    presentationBinding.shadowProducer.applyPendingCommandsAtOwnerBoundary();
                    fresh = AudioManager.this.captureLogicalSnapshot();
                } catch (RuntimeException failure) {
                    // Successfully applied predecessors have already left the
                    // ledger; the failing command and successors remain queued.
                    // The stale selected/prepared target is retained until a
                    // complete drain and fresh capture can replace it.
                    AudioDiagnosticObserverException.rethrowIfPresent(failure);
                    LOGGER.log(Level.WARNING,
                            "Audio post-boundary command publication failed; "
                                    + "retained coherent live state for retry",
                            failure);
                    return false;
                }
                invalidatePreparation();
                presentationBinding.shadowProducer.discardPreparedRestoreSelection();
                deferredReverseLogicalSnapshot = fresh;
                deferredReverseLogicalPrepared = false;
                postBoundaryReverseTarget = true;
            }
            return AudioManager.this.commitDeferredReverseLogicalRestore();
        }

        private boolean commitDeferredReverseLogicalRestore() {
            if (!reverseAudioPresentationActive
                    || deferredReverseLogicalSnapshot == null
                    || deferredReverseLogicalPrepared) {
                return deferredReverseLogicalPrepared;
            }
            AudioLogicalSnapshot selected = deferredReverseLogicalSnapshot;
            try {
                presentationBinding.shadowProducer.prepareRestoreSelection(
                        selected.presentation(), presentationBinding.shadowFactory);
                deferredReverseLogicalPrepared = true;
                return true;
            } catch (RuntimeException failure) {
                deferredReverseLogicalPrepared = false;
                AudioDiagnosticObserverException.rethrowIfPresent(failure);
                LOGGER.log(Level.WARNING,
                        "Audio reverse target preparation failed; retained live "
                                + "state for retry",
                        failure);
                return false;
            }
        }

        private void publishReverseReleaseLedger(
                AudioLogicalSnapshot selected,
                Map<GameSound, DonorSfxBinding> publishedBindings) {
            if (selected != null) {
                publishHeldRestoreLedger(selected, publishedBindings);
            }
            // Only retire after every root ledger write returns successfully.
            deferredReverseLogicalSnapshot = null;
            deferredReverseLogicalPrepared = false;
            reverseAudioPresentationActive = false;
            postBoundaryReverseTarget = false;
        }

        private boolean blocksSfxAdmission() {
            return reverseAudioPresentationActive;
        }

        private boolean blocksRingAdmission() {
            return deferredReverseLogicalSnapshot != null
                    ? deferredReverseLogicalSnapshot.presentation().sfxBlocked()
                    : presentationBinding.shadowProducer != null && presentationBinding.shadowProducer.areSfxRequestsBlocked();
        }

        private AudioLogicalSnapshot selectedSnapshot() {
            return deferredReverseLogicalSnapshot;
        }

        private void armReleaseFailure(ReverseReleaseFailurePoint point) {
            failNextReverseRelease = java.util.Objects.requireNonNull(point, "point");
        }

        /** Producer close/discard owns its resources; this invalidates only our acknowledgment. */
        private void invalidatePreparation() {
            deferredReverseLogicalPrepared = false;
        }

        private void reset() {
            reverseAudioPresentationActive = false;
            deferredReverseLogicalSnapshot = null;
            deferredReverseLogicalPrepared = false;
            postBoundaryReverseTarget = false;
            resetLogicalRestorePublicationCount();
            failNextReverseRelease = null;
        }
    }

    /**
     * Owns capture admission, accepted release and continuity across producer
     * generations. Packet storage and clocks remain with the producer. Monitor
     * coverage follows the compatibility caller or the explicit manager regions;
     * this owner never substitutes its own monitor.
     */
    private final class CaptureLeases {
        /** Single non-consuming offline compatibility lease. */
        private LiveCaptureAudioHandle offlineCaptureHandle;
        private ManagerLiveCaptureAudioHandle activeLiveCaptureAudioHandle;
        private boolean liveCaptureAwaitingRebind;

        private LiveCaptureAudioHandle beginLiveCaptureAudio(int frameRate) {
            // Scene music uses the same final packets and therefore requires no second capture lease.
            if (activeLiveCaptureAudioHandle != null) {
                throw new IllegalStateException("A live capture audio handle is already attached");
            }
            presentationBinding.ensureShadowPresentation();
            LiveCaptureAudioHandle capture = presentationBinding.shadowProducer.attachCapture(frameRate);
            ManagerLiveCaptureAudioHandle handle =
                    new ManagerLiveCaptureAudioHandle(capture, frameRate);
            activeLiveCaptureAudioHandle = handle;
            return handle;
        }

        private void beginCaptureMode(int sampleRate, int frameRate) {
            if (offlineCaptureHandle != null) {
                throw new IllegalStateException(
                        "An offline capture lease is already attached");
            }
            if (frameRate <= 0) {
                throw new IllegalArgumentException("frameRate must be positive");
            }
            presentationBinding.ensureShadowPresentation();
            // ensureShadowPresentation guarantees the presentation sink and the
            // producer share one sample rate (a mismatched sink is replaced before
            // the producer is built, and replaceSink rejects an incompatible one),
            // so the sink rate is the producer rate.
            int producerSampleRate = AudioManager.this.outputSampleRate();
            if (sampleRate != producerSampleRate) {
                throw new IllegalArgumentException(
                        "offline capture sample rate " + sampleRate
                                + " does not match the presentation producer rate "
                                + producerSampleRate);
            }
            if (frameRate != presentationBinding.shadowFrameRate) {
                throw new IllegalArgumentException(
                        "offline capture frame rate " + frameRate
                                + " does not match the presentation producer frame"
                                + " rate " + presentationBinding.shadowFrameRate
                                + "; offline callers require one producer packet per output frame");
            }
            offlineCaptureHandle = presentationBinding.shadowProducer.attachCapture(frameRate);
        }

        private int presentationFrameRate() {
            presentationBinding.ensureShadowPresentation();
            return presentationBinding.shadowFrameRate;
        }

        private int drainCaptureFrame(short[] target) {
            if (offlineCaptureHandle == null) {
                throw new IllegalStateException("beginCaptureMode() not called");
            }
            return offlineCaptureHandle.drainPresentationFrame(target);
        }

        private void endCaptureMode() {
            LiveCaptureAudioHandle handle = offlineCaptureHandle;
            if (handle == null) {
                return;
            }
            handle.close();
            offlineCaptureHandle = null;
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
        private void closeLiveCaptureAudio(ManagerLiveCaptureAudioHandle handle) {
            synchronized (AudioManager.this) {
                if (handle.closed) {
                    return;
                }
                handle.capture.close();
                handle.closed = true;
                if (activeLiveCaptureAudioHandle == handle) {
                    activeLiveCaptureAudioHandle = null;
                }
            }
        }

        /**
         * Reconcile against the producer currently published after close callbacks.
         * A foreign-thread close rejects before release. An accepted close marks
         * leases closed before fallible teardown, so offline admission retires even
         * if later manager-wide cleanup is skipped. Live retirement separately takes
         * the manager monitor and preserves any pending rebuild carry.
         */
        private void reconcileProducerClose() {
            // Preserve the live read and its failure precedence if a callback
            // cleared the producer; an earlier snapshot or null guard changes it.
            if (presentationBinding.shadowProducer.isClosed()) {
                offlineCaptureHandle = null;
                retireLiveCaptureAudioHandle();
            }
        }

        private void retireWithoutProducer() {
            offlineCaptureHandle = null;
            retireLiveCaptureAudioHandle();
        }

        /**
         * A rebuild of the presentation must not end a recording that is already
         * running. Backend replacement, launch replacement, reset and base-source
         * replacement mark the lease here. Retiring it at a mode transition meant a
         * recording started on the master title screen lost its audio the moment the
         * player started a game, and the Task 11 degradation turned that into
         * permanent silence rather than a visible failure.
         *
         * <p>{@link AudioManager#destroy()} does not mark. It normally retires,
         * but an already-pending carry survives that teardown too.
         */
        private void detachLiveCaptureAudioHandleForRebuild() {
            synchronized (AudioManager.this) {
                ManagerLiveCaptureAudioHandle live = activeLiveCaptureAudioHandle;
                liveCaptureAwaitingRebind = live != null && !live.closed;
            }
        }

        /**
         * Re-attaches a carried lease to the freshly built producer.
         *
         * <p>Ordinary attachment failures retire the recording, which the recorder
         * degrades to phase-correct clocked silence with one warning. Diagnostic
         * observer failures escape before retirement. Sample-rate validation stays
         * outside that attachment catch.
         *
         * @param producerSampleRate the rebuilt producer's rate. A recording is
         *        muxed at the rate captured when it started (ffmpeg {@code -ar}),
         *        so carrying a lease onto a producer running at a different rate
         *        would write pitch-shifted audio — which looks like it worked.
         *        Refusing the carry is the honest answer.
         */
        private void rebindLiveCaptureAudioHandle(int producerSampleRate) {
            synchronized (AudioManager.this) {
                if (!liveCaptureAwaitingRebind) {
                    return;
                }
                liveCaptureAwaitingRebind = false;
                ManagerLiveCaptureAudioHandle live = activeLiveCaptureAudioHandle;
                if (live == null || live.closed || presentationBinding.shadowProducer == null) {
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
                    live.capture = presentationBinding.shadowProducer.attachCapture(
                            live.requestedFrameRate, live.capture.clockSnapshot());
                    live.carriedStereoFrames += carried;
                } catch (RuntimeException rebindFailure) {
                    AudioDiagnosticObserverException.rethrowIfPresent(rebindFailure);
                    LOGGER.log(Level.WARNING,
                            "Live recording lease could not be carried across a"
                                    + " presentation rebuild", rebindFailure);
                    retireLiveCaptureAudioHandle();
                }
            }
        }

        private void retireLiveCaptureAudioHandle() {
            synchronized (AudioManager.this) {
                if (liveCaptureAwaitingRebind) {
                    // Preserve the pending carry even through a later destroy;
                    // successful realization can still bind it to a new producer.
                    return;
                }
                ManagerLiveCaptureAudioHandle live = activeLiveCaptureAudioHandle;
                if (live != null) {
                    live.closed = true;
                    activeLiveCaptureAudioHandle = null;
                }
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
    }

    public void destroy() {
        invalidateAndReleaseStreamedMusicSession();
        heldReverseRestore.invalidatePreparation();
        presentationBinding.closeShadowPresentation();
        if (backend != null) {
            backend.destroy();
        }
    }

    /**
     * Pauses audio playback. Called when the game window is minimized or loses focus.
     */
    public void pause() {
        presentationBinding.pause();
        AudioDiagnosticObserverException.invoke(() ->
                diagnosticInstallation.driverServiceObserver().onLifecycle(
                        SmpsDriverServiceObserver.LifecycleEvent.session(
                                SmpsDriverServiceObserver.LifecycleKind.PAUSE)));
    }

    /**
     * Resumes audio playback after being paused.
     */
    public void resume() {
        presentationBinding.resume();
        AudioDiagnosticObserverException.invoke(() ->
                diagnosticInstallation.driverServiceObserver().onLifecycle(
                        SmpsDriverServiceObserver.LifecycleEvent.session(
                                SmpsDriverServiceObserver.LifecycleKind.RESUME)));
    }
}
