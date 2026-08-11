package com.openggf.audio.presentation;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioAdmissionObserver;
import com.openggf.audio.AudioAdmissionObserver.AudioAdmissionDecision;
import com.openggf.audio.AudioDiagnosticObserverException;
import com.openggf.audio.SmpsSfxPlaybackPolicy;
import com.openggf.audio.MusicRestoreSink;
import com.openggf.audio.driver.SfxContentionObserver;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.driver.SmpsDriverServiceObserver;
import com.openggf.audio.driver.SmpsRequestAdmissionPolicy;
import com.openggf.audio.driver.SmpsRequestAdmissionPolicy.AdmissionResult;
import com.openggf.audio.driver.SmpsRequestAdmissionPolicy.SmpsAdmissionContext;
import com.openggf.audio.presentation.AudioPresentationCommand.MusicVoiceEntry;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsProgramView;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.smps.SmpsSfxData;
import com.openggf.audio.synth.ChipWriteObserver;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Builds presentation-owned audio sources before rendering begins.
 *
 * <p>SMPS SFX resolution is deliberately split in two. Command submission
 * snapshots loader-owned assets into this factory, while ordered registry
 * apply performs the first sequencer construction and driver attachment.
 */
public final class AudioPresentationSourceFactory
        implements SmpsSfxInstantiation, AudioPresentationDependencyResolver {

    @FunctionalInterface
    public interface WavAssets {
        InputStream open(String assetId);
    }

    public record Settings(
            double outputSampleRate,
            SmpsSequencer.Region region,
            boolean dacInterpolate,
            boolean psgNoiseShiftEveryToggle,
            boolean fm6DacOff,
            boolean speedShoesEnabled,
            int speedMultiplier,
            MusicRestoreSink audioManager,
            DecodedPcmCache pcmCache,
            WavAssets wavAssets) {
        public Settings {
            if (!Double.isFinite(outputSampleRate)
                    || outputSampleRate <= 0) {
                throw new IllegalArgumentException(
                        "outputSampleRate must be positive");
            }
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(audioManager, "audioManager");
            Objects.requireNonNull(pcmCache, "pcmCache");
            Objects.requireNonNull(wavAssets, "wavAssets");
        }

        public static Settings defaults() {
            ClassLoader loader =
                    AudioPresentationSourceFactory.class.getClassLoader();
            return new Settings(
                    48_000,
                    SmpsSequencer.Region.NTSC,
                    false,
                    false,
                    false,
                    false,
                    1,
                    AudioManager.presentationOwner(),
                    new DecodedPcmCache(),
                    loader::getResourceAsStream);
        }
    }

    /** Immutable catalog-owned values used only by the dormant live backend. */
    public record RegisteredSmpsPlayback(
            AbstractSmpsData program,
            DacData dac,
            SmpsSequencerConfig config,
            SmpsSfxPlaybackPolicy policy) {
        public RegisteredSmpsPlayback {
            Objects.requireNonNull(program, "program");
            Objects.requireNonNull(dac, "dac");
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(policy, "policy");
        }

        public RegisteredSmpsPlayback(
                AbstractSmpsData program,
                DacData dac,
                SmpsSequencerConfig config) {
            this(program, dac, config,
                    SmpsSfxPlaybackPolicy.defaults(false));
        }
    }

    private record LegacySmpsSource(
            String gameId,
            AbstractSmpsData data,
            DacData dac,
            SmpsSequencerConfig staticConfig,
            boolean coordFlagHandlerRequired,
            boolean specialSfx) {
    }

    private interface DiagnosticDispatcher {
        DiagnosticDispatcher IMMEDIATE = Runnable::run;

        void emit(Runnable callback);
    }

    private final class DeferredDiagnosticRoot
            implements DiagnosticDispatcher {
        private enum State {
            PREPARING,
            DEFERRED,
            COMMITTED,
            DISCARDED
        }

        private final List<Runnable> callbacks = new ArrayList<>();
        private State state = State.PREPARING;
        private long provisionalNextDriverOrdinal =
                nextDriverInstanceOrdinal;

        @Override
        public void emit(Runnable callback) {
            Objects.requireNonNull(callback, "callback");
            switch (state) {
                case PREPARING, DEFERRED -> callbacks.add(callback);
                case COMMITTED -> callback.run();
                case DISCARDED -> {
                    // Provisional voices remain bound here while being stopped.
                }
            }
        }

        private long allocateDriverOrdinal() {
            return provisionalNextDriverOrdinal++;
        }

        private void commitRoot() {
            state = State.COMMITTED;
            nextDriverInstanceOrdinal = provisionalNextDriverOrdinal;
            List<Runnable> deferred = List.copyOf(callbacks);
            callbacks.clear();
            for (Runnable callback : deferred) {
                callback.run();
            }
        }

        private void discardRoot() {
            state = State.DISCARDED;
            callbacks.clear();
        }

        private void rollbackTo(int callbackCount, long driverOrdinal) {
            callbacks.subList(callbackCount, callbacks.size()).clear();
            provisionalNextDriverOrdinal = driverOrdinal;
        }
    }

    private final class DeferredDiagnosticTransaction
            implements DiagnosticTransaction {
        private enum State {
            PREPARING,
            DEFERRED,
            COMMITTED,
            DISCARDED
        }

        private final DeferredDiagnosticRoot root;
        private final boolean rootOwner;
        private final int callbackStart;
        private final long ordinalStart;
        private State state = State.PREPARING;

        private DeferredDiagnosticTransaction(
                DeferredDiagnosticRoot root, boolean rootOwner) {
            this.root = root;
            this.rootOwner = rootOwner;
            callbackStart = root.callbacks.size();
            ordinalStart = root.provisionalNextDriverOrdinal;
        }

        @Override
        public void endPreparation() {
            if (state != State.PREPARING
                    || activeDiagnosticTransactions.isEmpty()
                    || activeDiagnosticTransactions.getLast() != this) {
                throw new IllegalStateException(
                        "diagnostic transaction is not preparing");
            }
            activeDiagnosticTransactions.removeLast();
            state = State.DEFERRED;
            if (activeDiagnosticTransactions.isEmpty()) {
                activeDiagnosticRoot = null;
                root.state = DeferredDiagnosticRoot.State.DEFERRED;
            }
        }

        @Override
        public void commit() {
            if (state != State.DEFERRED) {
                throw new IllegalStateException(
                        "diagnostic transaction cannot be committed");
            }
            state = State.COMMITTED;
            if (rootOwner) {
                root.commitRoot();
            }
        }

        @Override
        public void discard() {
            if (state == State.COMMITTED) {
                throw new IllegalStateException(
                        "committed diagnostic transaction cannot be discarded");
            }
            if (state == State.PREPARING) {
                if (activeDiagnosticTransactions.isEmpty()
                        || activeDiagnosticTransactions.getLast() != this) {
                    throw new IllegalStateException(
                            "diagnostic transaction is not current");
                }
                activeDiagnosticTransactions.removeLast();
            }
            state = State.DISCARDED;
            if (rootOwner) {
                activeDiagnosticTransactions.clear();
                activeDiagnosticRoot = null;
                root.discardRoot();
            } else {
                root.rollbackTo(callbackStart, ordinalStart);
            }
        }
    }

    private record CompatibilityDependencies(
            DacData dac, SmpsSequencerConfig config) {
    }

    private final BooleanSupplier ownerThreadBoundary;
    private final SmpsCoordFlagHandlerOwner coordFlagHandlers;
    private final Settings settings;
    private final SmpsAssetCatalog assetCatalog;
    private final Map<SmpsSourceDescriptor, SmpsAssetCatalog.ProgramEntry>
            sourcesByDescriptor =
            new HashMap<>();
    private final Map<SmpsAssetCatalog.DependencyKey,
            CompatibilityDependencies> compatibilityDependencies =
            new HashMap<>();
    private final Map<Long, PresentationVoiceSnapshot.Smps> musicBlueprints =
            new HashMap<>();
    private final Map<Long, String> musicGameIds = new HashMap<>();
    private final AtomicInteger cacheLookupCount = new AtomicInteger();
    private AudioAdmissionObserver admissionObserver =
            AudioAdmissionObserver.NONE;
    private SmpsDriverServiceObserver driverServiceObserver =
            SmpsDriverServiceObserver.NONE;
    private SmpsRequestAdmissionPolicy sfxAdmissionPolicy =
            SmpsRequestAdmissionPolicy.PERMISSIVE;
    private ChipWriteObserver chipWriteObserver = ChipWriteObserver.NONE;
    private SfxContentionObserver sfxContentionObserver =
            SfxContentionObserver.NONE;
    private long nextServiceOrdinal;
    private long nextDriverInstanceOrdinal;
    private DeferredDiagnosticRoot activeDiagnosticRoot;
    private final List<DeferredDiagnosticTransaction>
            activeDiagnosticTransactions = new ArrayList<>();

    public AudioPresentationSourceFactory(
            BooleanSupplier ownerThreadBoundary,
            SmpsCoordFlagHandlerOwner coordFlagHandlers) {
        this(ownerThreadBoundary, coordFlagHandlers, Settings.defaults());
    }

    public AudioPresentationSourceFactory(
            BooleanSupplier ownerThreadBoundary,
            SmpsCoordFlagHandlerOwner coordFlagHandlers,
            Settings settings) {
        this.ownerThreadBoundary =
                Objects.requireNonNull(ownerThreadBoundary,
                        "ownerThreadBoundary");
        this.coordFlagHandlers =
                Objects.requireNonNull(coordFlagHandlers, "coordFlagHandlers");
        this.settings = Objects.requireNonNull(settings, "settings");
        assetCatalog = new SmpsAssetCatalog(coordFlagHandlers);
    }

    public void setAdmissionObserver(AudioAdmissionObserver observer) {
        admissionObserver = Objects.requireNonNull(observer, "observer");
    }

    public void setDriverServiceObserver(
            SmpsDriverServiceObserver observer) {
        driverServiceObserver = Objects.requireNonNull(observer, "observer");
    }

    public void setSfxAdmissionPolicy(
            SmpsRequestAdmissionPolicy policy) {
        sfxAdmissionPolicy = Objects.requireNonNull(policy, "policy");
    }

    public void setChipWriteObserver(ChipWriteObserver observer) {
        chipWriteObserver = Objects.requireNonNull(observer, "observer");
    }

    public void setSfxContentionObserver(
            SfxContentionObserver observer) {
        sfxContentionObserver = Objects.requireNonNull(observer, "observer");
    }

    @Override
    public DiagnosticTransaction beginDiagnosticTransaction() {
        assertOwnerBoundary();
        boolean rootOwner = activeDiagnosticRoot == null;
        if (rootOwner) {
            activeDiagnosticRoot = new DeferredDiagnosticRoot();
        }
        DeferredDiagnosticTransaction transaction =
                new DeferredDiagnosticTransaction(
                        activeDiagnosticRoot, rootOwner);
        activeDiagnosticTransactions.add(transaction);
        return transaction;
    }

    public MusicVoiceEntry musicSmps(
            String gameId,
            int musicId,
            long voiceId,
            AbstractSmpsData data,
            DacData dac,
            SmpsSequencerConfig config,
            AudioSourceDescriptor descriptor,
            int maxStereoFrames) {
        return musicSmpsInternal(gameId, musicId, voiceId, 0,
                data, dac, config, descriptor, maxStereoFrames, true);
    }

    public MusicVoiceEntry musicSmps(
            String gameId,
            int musicId,
            long voiceId,
            long dependencyGeneration,
            AbstractSmpsData data,
            DacData dac,
            SmpsSequencerConfig config,
            AudioSourceDescriptor descriptor,
            int maxStereoFrames) {
        return musicSmpsInternal(gameId, musicId, voiceId,
                dependencyGeneration, data, dac, config, descriptor,
                maxStereoFrames, false);
    }

    private MusicVoiceEntry musicSmpsInternal(
            String gameId,
            int musicId,
            long voiceId,
            long dependencyGeneration,
            AbstractSmpsData data,
            DacData dac,
            SmpsSequencerConfig config,
            AudioSourceDescriptor descriptor,
            int maxStereoFrames,
            boolean compatibilityGenerationZero) {
        String resolvedGameId = requireGameId(gameId);
        Objects.requireNonNull(descriptor, "descriptor");
        SmpsAssetKey key = musicAssetKey(
                resolvedGameId, musicId, descriptor);
        CompatibilityDependencies dependencies = compatibilityGenerationZero
                ? compatibilityDependencies(key, dac, config)
                : new CompatibilityDependencies(dac, config);
        SmpsAssetCatalog.ProgramEntry source = compatibilityGenerationZero
                ? registerCompatibilitySmpsMusicAsset(
                key, data, dependencies.dac(), dependencies.config())
                : registerSmpsMusicAsset(
                key, dependencyGeneration, data,
                dependencies.dac(), dependencies.config());
        return musicSmpsFromRegistered(
                resolvedGameId, musicId, voiceId, descriptor,
                maxStereoFrames, source);
    }

    MusicVoiceEntry musicSmpsFromRegistered(
            String gameId,
            int musicId,
            long voiceId,
            AudioSourceDescriptor descriptor,
            int maxStereoFrames,
            SmpsAssetCatalog.ProgramEntry source) {
        String resolvedGameId = requireGameId(gameId);
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(source, "source");
        SmpsCompositeVoice voice = buildMusicVoice(
                musicId, voiceId, descriptor, maxStereoFrames, source,
                newConfiguredDriver(false, musicOrigin(voiceId, musicId)));
        PresentationVoiceSnapshot.Smps blueprint =
                (PresentationVoiceSnapshot.Smps) voice.snapshot();
        musicBlueprints.put(voiceId, blueprint);
        musicGameIds.put(voiceId, resolvedGameId);
        return MusicVoiceEntry.fromVoice(musicId, descriptor, voice);
    }

    /**
     * Builds the transitional backend-owned music voice without taking
     * ownership of the legacy loader's data or DAC bank.
     */
    public SmpsCompositeVoice legacyMusicSmps(
            String gameId,
            int musicId,
            long voiceId,
            AbstractSmpsData data,
            DacData dac,
            SmpsSequencerConfig config,
            AudioSourceDescriptor descriptor,
            int maxStereoFrames) {
        String resolvedGameId = requireGameId(gameId);
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(dac, "dac");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(descriptor, "descriptor");
        LegacySmpsSource source = new LegacySmpsSource(
                resolvedGameId,
                data,
                dac,
                copyStaticConfig(config),
                config.getCoordFlagHandler() != null,
                false);
        return buildMusicVoice(
                musicId, voiceId, descriptor, maxStereoFrames, source,
                newConfiguredDriver(true, musicOrigin(voiceId, musicId)));
    }

    /**
     * Copies a legacy sequencer profile while replacing its mutable
     * coordination handler with this backend's private owner.
     */
    public SmpsSequencerConfig legacySequencerConfig(
            String gameId, SmpsSequencerConfig config) {
        return copyPresentationConfig(
                requireGameId(gameId),
                Objects.requireNonNull(config, "config"),
                config.getCoordFlagHandler() != null);
    }

    private SmpsCompositeVoice buildMusicVoice(
            int musicId,
            long voiceId,
            AudioSourceDescriptor descriptor,
            int maxStereoFrames,
            SmpsAssetCatalog.ProgramEntry source,
            SmpsDriver driver) {
        SmpsSequencer sequencer = newSequencer(source, driver);
        sequencer.setSpeedShoes(settings.speedShoesEnabled());
        sequencer.setSpeedMultiplier(settings.speedMultiplier());
        sequencer.setFallbackVoiceData(source.program());
        driver.addSequencer(sequencer, false);

        SmpsCompositeVoice voice = new SmpsCompositeVoice(
                voiceId, 0, musicId, descriptor, maxStereoFrames, driver);
        return voice;
    }

    private SmpsCompositeVoice buildMusicVoice(
            int musicId,
            long voiceId,
            AudioSourceDescriptor descriptor,
            int maxStereoFrames,
            LegacySmpsSource source,
            SmpsDriver driver) {
        SmpsSequencer sequencer = newLegacySequencer(source, driver);
        sequencer.setSourceDescriptor(
                describeLegacyMusic(descriptor, source.data()));
        sequencer.setSpeedShoes(settings.speedShoesEnabled());
        sequencer.setSpeedMultiplier(settings.speedMultiplier());
        sequencer.setFallbackVoiceData(source.data());
        driver.addSequencer(sequencer, false);
        return new SmpsCompositeVoice(
                voiceId, 0, musicId, descriptor, maxStereoFrames, driver);
    }

    public SmpsAssetCatalog.ProgramEntry registerSmpsSfxAsset(
            SmpsAssetKey key,
            AbstractSmpsData data,
            DacData dac,
            SmpsSequencerConfig config) {
        return registerCompatibilitySmpsSfxAsset(
                key, data, dac, config, false);
    }

    public SmpsAssetCatalog.ProgramEntry registerSmpsSfxAsset(
            SmpsAssetKey key,
            AbstractSmpsData data,
            DacData dac,
            SmpsSequencerConfig config,
            boolean specialSfx) {
        return registerCompatibilitySmpsSfxAsset(
                key, data, dac, config, specialSfx);
    }

    public SmpsAssetCatalog.ProgramEntry registerSmpsSfxAsset(
            SmpsAssetKey key,
            long dependencyGeneration,
            AbstractSmpsData data,
            DacData dac,
            SmpsSequencerConfig config,
            boolean specialSfx) {
        SmpsAssetKey resolvedKey = Objects.requireNonNull(key, "key");
        validateSfxKey(resolvedKey);
        return register(new SmpsAssetCatalog.ProgramKey(
                resolvedKey, dependencyGeneration), data, dac, config,
                SmpsSfxPlaybackPolicy.defaults(specialSfx));
    }

    public SmpsAssetCatalog.ProgramEntry registerSmpsSfxAsset(
            SmpsAssetKey key,
            long dependencyGeneration,
            AbstractSmpsData data,
            DacData dac,
            SmpsSequencerConfig config,
            SmpsSfxPlaybackPolicy policy) {
        SmpsAssetKey resolvedKey = Objects.requireNonNull(key, "key");
        validateSfxKey(resolvedKey);
        return register(new SmpsAssetCatalog.ProgramKey(
                resolvedKey, dependencyGeneration), data, dac, config,
                policy);
    }

    public SmpsAssetCatalog.ProgramEntry findRegisteredSmpsSfxAsset(
            SmpsAssetKey key, long dependencyGeneration) {
        SmpsAssetKey resolvedKey = Objects.requireNonNull(key, "key");
        validateSfxKey(resolvedKey);
        return assetCatalog.find(new SmpsAssetCatalog.ProgramKey(
                resolvedKey, dependencyGeneration));
    }

    public RegisteredSmpsPlayback requireRegisteredSmpsSfxPlayback(
            SmpsAssetKey key, long dependencyGeneration) {
        SmpsAssetCatalog.ProgramEntry entry = findRegisteredSmpsSfxAsset(
                key, dependencyGeneration);
        if (entry == null) {
            throw new IllegalStateException(
                    "no registered SMPS SFX for " + key);
        }
        return registeredPlayback(entry);
    }

    public SmpsAssetCatalog.ProgramEntry registerSmpsMusicAsset(
            SmpsAssetKey key,
            long dependencyGeneration,
            AbstractSmpsData data,
            DacData dac,
            SmpsSequencerConfig config) {
        SmpsAssetKey resolvedKey = Objects.requireNonNull(key, "key");
        validateMusicKey(resolvedKey);
        return register(new SmpsAssetCatalog.ProgramKey(
                resolvedKey, dependencyGeneration), data, dac, config,
                SmpsSfxPlaybackPolicy.defaults(false));
    }

    public SmpsAssetCatalog.ProgramEntry findRegisteredSmpsMusicAsset(
            SmpsAssetKey key, long dependencyGeneration) {
        SmpsAssetKey resolvedKey = Objects.requireNonNull(key, "key");
        validateMusicKey(resolvedKey);
        return assetCatalog.find(new SmpsAssetCatalog.ProgramKey(
                resolvedKey, dependencyGeneration));
    }

    public RegisteredSmpsPlayback requireRegisteredSmpsMusicPlayback(
            SmpsAssetKey key, long dependencyGeneration) {
        SmpsAssetCatalog.ProgramEntry entry = findRegisteredSmpsMusicAsset(
                key, dependencyGeneration);
        if (entry == null) {
            throw new IllegalStateException(
                    "no registered SMPS music for " + key);
        }
        return registeredPlayback(entry);
    }

    private static RegisteredSmpsPlayback registeredPlayback(
            SmpsAssetCatalog.ProgramEntry entry) {
        return new RegisteredSmpsPlayback(
                entry.program(), entry.dac(), entry.staticConfig(),
                entry.sfxPolicy());
    }

    private SmpsAssetCatalog.ProgramEntry register(
            SmpsAssetCatalog.ProgramKey key,
            AbstractSmpsData data,
            DacData dac,
            SmpsSequencerConfig config,
            SmpsSfxPlaybackPolicy policy) {
        SmpsAssetCatalog.ProgramEntry entry = assetCatalog.register(
                key, data, dac, config, policy);
        SmpsAssetCatalog.ProgramEntry previous = sourcesByDescriptor.putIfAbsent(
                entry.sourceDescriptor(), entry);
        if (previous != null && previous != entry) {
            throw new IllegalStateException(
                    "SMPS source descriptor collision for "
                            + entry.sourceDescriptor());
        }
        return entry;
    }

    private SmpsAssetCatalog.ProgramEntry registerCompatibilitySmpsSfxAsset(
            SmpsAssetKey key,
            AbstractSmpsData data,
            DacData dac,
            SmpsSequencerConfig config,
            boolean specialSfx) {
        SmpsAssetKey resolvedKey = Objects.requireNonNull(key, "key");
        CompatibilityDependencies dependencies = compatibilityDependencies(
                resolvedKey, dac, config);
        return register(new SmpsAssetCatalog.ProgramKey(resolvedKey, 0),
                data, dependencies.dac(), dependencies.config(),
                SmpsSfxPlaybackPolicy.defaults(specialSfx));
    }

    private SmpsAssetCatalog.ProgramEntry registerCompatibilitySmpsMusicAsset(
            SmpsAssetKey key,
            AbstractSmpsData data,
            DacData dac,
            SmpsSequencerConfig config) {
        return register(new SmpsAssetCatalog.ProgramKey(key, 0),
                data, dac, config, SmpsSfxPlaybackPolicy.defaults(false));
    }

    private CompatibilityDependencies compatibilityDependencies(
            SmpsAssetKey key,
            DacData dac,
            SmpsSequencerConfig config) {
        SmpsAssetCatalog.DependencyKey dependencyKey =
                new SmpsAssetCatalog.ProgramKey(key, 0).dependencyKey();
        return compatibilityDependencies.computeIfAbsent(
                dependencyKey,
                ignored -> new CompatibilityDependencies(
                        Objects.requireNonNull(dac, "dac"),
                        Objects.requireNonNull(config, "config")));
    }

    public ResolvedSmpsSfxSource resolveSmpsSfx(
            long standaloneVoiceId,
            SmpsAssetKey assetKey,
            int pitchQ16,
            int priority,
            int continuousSfxId,
            int trackCount,
            int maxStereoFrames) {
        return resolveSmpsSfx(standaloneVoiceId, assetKey, 0,
                pitchQ16, priority, continuousSfxId, trackCount,
                maxStereoFrames);
    }

    public ResolvedSmpsSfxSource resolveSmpsSfx(
            long standaloneVoiceId,
            SmpsAssetKey assetKey,
            long dependencyGeneration,
            int pitchQ16,
            int priority,
            int continuousSfxId,
            int trackCount,
            int maxStereoFrames) {
        if (pitchQ16 <= 0) {
            throw new IllegalArgumentException("pitchQ16 must be positive");
        }
        validateSfxKey(Objects.requireNonNull(assetKey, "assetKey"));
        SmpsAssetCatalog.ProgramEntry cached =
                findRegisteredSmpsSfxAsset(
                        assetKey, dependencyGeneration);
        return new ResolvedSmpsSfxSource(
                standaloneVoiceId, assetKey, dependencyGeneration,
                pitchQ16, priority,
                continuousSfxId, trackCount, maxStereoFrames,
                cached != null ? cached.assetId() : assetKey.sfxId(),
                cached != null && cached.specialSfx());
    }

    @Override
    public SmpsSequencer instantiateCached(
            ResolvedSmpsSfxSource source,
            SmpsDriver currentOwner) {
        assertOwnerBoundary();
        cacheLookupCount.incrementAndGet();
        SmpsAssetCatalog.ProgramEntry cached = requireCached(source);
        SmpsDriver owner = Objects.requireNonNull(
                currentOwner, "currentOwner");
        SmpsSequencer sequencer = newSequencer(cached, owner);
        sequencer.setSfxMode(true);
        sequencer.setPitch(source.pitchQ16() / 65_536.0f);
        sequencer.setSfxPriority(source.priority());
        sequencer.setSpecialSfx(cached.specialSfx());
        SmpsSequencer music = owner.firstMusicSequencer();
        if (music != null) {
            sequencer.setFallbackVoiceData(music.getSmpsData());
        }
        return sequencer;
    }

    @Override
    public SmpsCompositeVoice instantiateStandaloneCached(
            ResolvedSmpsSfxSource source) {
        assertOwnerBoundary();
        cacheLookupCount.incrementAndGet();
        requireCached(source);
        return new SmpsCompositeVoice(
                source.standaloneVoiceId(), source.priority(), null, null,
                source.maxStereoFrames(), newConfiguredDriver(true,
                        sfxOrigin(source.standaloneVoiceId(),
                                source.assetKey().sfxId())));
    }

    @Override
    public Admission evaluateAdmission(
            ResolvedSmpsSfxSource source, SmpsDriver currentOwner) {
        Objects.requireNonNull(source, "source");
        int requestedId = source.assetKey().sfxId();
        SmpsAdmissionContext context = new SmpsAdmissionContext(
                requestedId, source.resolvedSoundId(), source.priority(),
                SmpsRequestAdmissionPolicy.NO_PRIORITY,
                source.specialSfx(), false);
        AdmissionResult result = Objects.requireNonNull(
                sfxAdmissionPolicy.evaluate(context),
                "SFX admission policy returned no result");
        return new Admission(context, result);
    }

    @Override
    public Admission rejectedAdmission(
            ResolvedSmpsSfxSource source,
            SmpsRequestAdmissionPolicy.RejectionReason reason) {
        Objects.requireNonNull(source, "source");
        SmpsAdmissionContext context = new SmpsAdmissionContext(
                source.assetKey().sfxId(), source.resolvedSoundId(),
                source.priority(), SmpsRequestAdmissionPolicy.NO_PRIORITY,
                source.specialSfx(), false);
        return new Admission(context, new AdmissionResult(false, reason,
                context.priorityBefore(), context.priorityBefore(),
                context.resolvedSoundId()));
    }

    @Override
    public void observeAdmission(Admission admission) {
        if (admissionObserver == AudioAdmissionObserver.NONE) {
            return;
        }
        DiagnosticDispatcher diagnostics = diagnosticDispatcher();
        diagnostics.emit(() ->
                AudioDiagnosticObserverException.invoke(() ->
                        admissionObserver.onDecision(
                                new AudioAdmissionDecision(
                                        admission.context(),
                                        admission.result()))));
    }

    @Override
    public void observeLifecycle(
            SmpsDriverServiceObserver.LifecycleEvent event) {
        if (driverServiceObserver == SmpsDriverServiceObserver.NONE) {
            return;
        }
        DiagnosticDispatcher diagnostics = diagnosticDispatcher();
        diagnostics.emit(() ->
                AudioDiagnosticObserverException.invoke(() ->
                        driverServiceObserver.onLifecycle(event)));
    }

    @Override
    public boolean hasPotentiallyThrowingObserver() {
        return admissionObserver != AudioAdmissionObserver.NONE
                || driverServiceObserver != SmpsDriverServiceObserver.NONE
                || chipWriteObserver != ChipWriteObserver.NONE
                || sfxContentionObserver != SfxContentionObserver.NONE;
    }

    public SmpsCompositeVoice recreateSmps(
            PresentationVoiceSnapshot.Smps snapshot,
            SmpsDriverSnapshot.DependencyResolver dependencies) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(dependencies, "dependencies");
        SmpsDriver driver = newConfiguredDriver(true,
                snapshot.musicId() != null
                        ? musicOrigin(snapshot.voiceId(), snapshot.musicId())
                        : sfxOrigin(snapshot.voiceId(),
                                restoredSfxSoundId(snapshot)));
        SmpsCompositeVoice voice = new SmpsCompositeVoice(
                snapshot.voiceId(), snapshot.priority(),
                snapshot.musicId(), snapshot.sourceDescriptor(),
                snapshot.maxStereoFrames(), driver);
        String voiceGameId = musicGameIds.get(snapshot.voiceId());
        voice.restore(snapshot, wrappingDependencies(
                dependencies, voiceGameId));
        return voice;
    }

    @Override
    public SmpsCompositeVoice recreateSmps(
            PresentationVoiceSnapshot.Smps snapshot) {
        return recreateSmps(snapshot,
                SmpsDriverSnapshot.liveReferences());
    }

    @Override
    public SmpsCompositeVoice recreateSmps(
            AudioPresentationCommand.SmpsVoiceDescriptor descriptor) {
        PresentationVoiceSnapshot.Smps blueprint =
                musicBlueprints.get(descriptor.voiceId());
        if (blueprint == null
                || blueprint.priority() != descriptor.priority()
                || !Objects.equals(blueprint.musicId(), descriptor.musicId())
                || !Objects.equals(blueprint.sourceDescriptor(),
                descriptor.sourceDescriptor())
                || blueprint.maxStereoFrames()
                != descriptor.maxStereoFrames()) {
            throw new IllegalStateException(
                    "no cached SMPS music for "
                            + descriptor.sourceDescriptor());
        }
        return recreateSmps(blueprint,
                SmpsDriverSnapshot.liveReferences());
    }

    public MusicVoiceEntry fallbackMusic(
            long voiceId,
            int musicId,
            AudioSourceDescriptor descriptor) throws IOException {
        Objects.requireNonNull(descriptor, "descriptor");
        String assetId = "music/"
                + Integer.toHexString(musicId).toUpperCase() + ".wav";
        DecodedPcm pcm = decode(assetId);
        SampleBackedVoice voice = SampleBackedVoice.loopingMusic(
                voiceId, pcm, roundedOutputSampleRate(), 1.0f);
        return MusicVoiceEntry.fromVoice(
                musicId, descriptor, voice);
    }

    public SampleBackedVoice fallbackSfx(
            long voiceId,
            String name,
            int priority,
            float pitch) throws IOException {
        DecodedPcm hostPcm = HostUiSfx.forCue(name);
        DecodedPcm pcm = hostPcm != null
                ? hostPcm
                : decode(fallbackSfxAsset(name));
        return SampleBackedVoice.oneShot(
                voiceId,
                priority,
                pcm,
                roundedOutputSampleRate(),
                pitch,
                1.0f);
    }

    public SampleBackedVoice segaPcm(
            long voiceId,
            DecodedPcm registeredPcm) {
        return SampleBackedVoice.rawSegaPcm(
                voiceId, 0,
                Objects.requireNonNull(registeredPcm, "registeredPcm"),
                roundedOutputSampleRate());
    }

    @Override
    public DecodedPcm resolvePcm(String assetId) {
        DecodedPcm pcm = settings.pcmCache().get(assetId);
        if (pcm == null) {
            pcm = HostUiSfx.forAsset(assetId);
        }
        if (pcm == null) {
            throw new IllegalStateException(
                    "no cached PCM for " + assetId);
        }
        return pcm;
    }

    public DecodedPcm registerUnsigned8Mono(
            String assetId, byte[] pcm, int sourceRate) {
        return settings.pcmCache().registerUnsigned8Mono(
                assetId, pcm, sourceRate);
    }

    AtomicInteger cacheLookupCountForTesting() {
        return cacheLookupCount;
    }

    private SmpsDriverSnapshot.DependencyResolver wrappingDependencies(
            SmpsDriverSnapshot.DependencyResolver delegate,
            String voiceGameId) {
        return new SmpsDriverSnapshot.DependencyResolver() {
            @Override
            public AbstractSmpsData resolveSmpsData(
                    SmpsDriverSnapshot.SequencerEntry entry) {
                SmpsAssetCatalog.ProgramEntry cached =
                        sourcesByDescriptor.get(entry.source());
                return cached != null
                        ? cached.program()
                        : copySmpsData(delegate.resolveSmpsData(entry));
            }

            @Override
            public DacData resolveDacData(
                    SmpsDriverSnapshot.SequencerEntry entry) {
                SmpsAssetCatalog.ProgramEntry cached =
                        sourcesByDescriptor.get(entry.source());
                DacData dac = cached != null
                        ? cached.dac()
                        : delegate.resolveDacData(entry);
                return Objects.requireNonNull(dac, "dac");
            }

            @Override
            public MusicRestoreSink resolveAudioManager(
                    SmpsDriverSnapshot.SequencerEntry entry) {
                MusicRestoreSink manager = delegate.resolveAudioManager(entry);
                return manager != null ? manager : settings.audioManager();
            }

            @Override
            public SmpsSequencerConfig resolveConfig(
                    SmpsDriverSnapshot.SequencerEntry entry) {
                SmpsAssetCatalog.ProgramEntry cached =
                        sourcesByDescriptor.get(entry.source());
                SmpsSequencerConfig sourceConfig = cached != null
                        ? cached.staticConfig()
                        : delegate.resolveConfig(entry);
                if (cached != null) {
                    return sourceConfig;
                }
                return copyPresentationConfig(
                        gameIdFor(entry.source(), voiceGameId),
                        sourceConfig,
                        sourceConfig.getCoordFlagHandler() != null);
            }
        };
    }

    private SmpsAssetCatalog.ProgramEntry requireCached(
            ResolvedSmpsSfxSource source) {
        Objects.requireNonNull(source, "source");
        SmpsAssetCatalog.ProgramEntry cached =
                findRegisteredSmpsSfxAsset(
                        source.assetKey(), source.dependencyGeneration());
        if (cached == null) {
            throw new IllegalStateException(
                    "SMPS SFX asset cache miss: " + source.assetKey());
        }
        return cached;
    }

    private SmpsSequencer newSequencer(
            SmpsAssetCatalog.ProgramEntry source, SmpsDriver driver) {
        SmpsSequencer sequencer = new SmpsSequencer(
                source.program(), source.dac(), driver,
                settings.audioManager(),
                source.staticConfig(), source.sourceDescriptor(),
                SmpsSequencer.SourceDescriptorTrust.PRECOMPUTED_IMMUTABLE);
        sequencer.setSampleRate(driver.getOutputSampleRate());
        sequencer.setFm6DacOff(settings.fm6DacOff());
        return sequencer;
    }

    private SmpsSequencer newLegacySequencer(
            LegacySmpsSource source, SmpsDriver driver) {
        SmpsSequencer sequencer = new SmpsSequencer(
                source.data(), source.dac(), driver,
                settings.audioManager(),
                copyPresentationConfig(
                        source.gameId(), source.staticConfig(),
                        source.coordFlagHandlerRequired()));
        sequencer.setSampleRate(driver.getOutputSampleRate());
        sequencer.setFm6DacOff(settings.fm6DacOff());
        return sequencer;
    }

    private SmpsDriver newConfiguredDriver(
            boolean observed,
            SmpsDriverServiceObserver.DriverAdmissionOrigin origin) {
        DiagnosticDispatcher diagnostics = observed
                ? diagnosticDispatcher()
                : DiagnosticDispatcher.IMMEDIATE;
        SmpsDriver driver =
                new SmpsDriver(settings.outputSampleRate(), observed
                        ? diagnosticChipWriteObserver(diagnostics)
                        : ChipWriteObserver.NONE);
        if (observed) {
            driver.setDiagnosticIdentity(
                    new SmpsDriverServiceObserver.DriverIdentity(
                            allocateDriverOrdinal(), origin));
            installDiagnosticObservers(driver, diagnostics);
        }
        driver.setRegion(settings.region());
        driver.setDacInterpolate(settings.dacInterpolate());
        driver.setOutputSampleRate(settings.outputSampleRate());
        driver.setPsgNoiseShiftOnEveryToggle(
                settings.psgNoiseShiftEveryToggle());
        if (observed) {
            driver.observeLifecycle(
                    SmpsDriverServiceObserver.LifecycleKind.DRIVER_CREATED);
        }
        return driver;
    }

    private DiagnosticDispatcher diagnosticDispatcher() {
        return activeDiagnosticRoot == null
                ? DiagnosticDispatcher.IMMEDIATE
                : activeDiagnosticRoot;
    }

    private long allocateDriverOrdinal() {
        return activeDiagnosticRoot == null
                ? nextDriverInstanceOrdinal++
                : activeDiagnosticRoot.allocateDriverOrdinal();
    }

    private void installDiagnosticObservers(
            SmpsDriver driver, DiagnosticDispatcher diagnostics) {
        if (sfxContentionObserver != SfxContentionObserver.NONE) {
            SfxContentionObserver observer = sfxContentionObserver;
            driver.setSfxContentionObserver(new SfxContentionObserver() {
                @Override
                public void onSfxAdmitted(Admission admission) {
                    diagnostics.emit(() ->
                            AudioDiagnosticObserverException.invoke(() ->
                                    observer.onSfxAdmitted(admission)));
                }

                @Override
                public void onRoleArbitrated(Arbitration arbitration) {
                    diagnostics.emit(() ->
                            AudioDiagnosticObserverException.invoke(() ->
                                    observer.onRoleArbitrated(arbitration)));
                }
            });
        }
        if (driverServiceObserver == SmpsDriverServiceObserver.NONE) {
            return;
        }
        SmpsDriverServiceObserver observer = driverServiceObserver;
        driver.setServiceObserver(new SmpsDriverServiceObserver() {
            private ServiceEvent activeEvent;

            @Override
            public void onServiceBegin(ServiceEvent event) {
                if (activeEvent != null) {
                    throw new IllegalStateException(
                            "SMPS driver service observer was re-entered");
                }
                activeEvent = new ServiceEvent(nextServiceOrdinal++,
                        event.driver(), event.sequencer(), event.kind());
                ServiceEvent emitted = activeEvent;
                diagnostics.emit(() ->
                        AudioDiagnosticObserverException.invoke(() ->
                                observer.onServiceBegin(emitted)));
            }

            @Override
            public void onServiceEnd(
                    ServiceEvent event,
                    SmpsDriverSnapshot snapshot) {
                ServiceEvent completed = activeEvent;
                if (completed == null) {
                    throw new IllegalStateException(
                            "SMPS driver service ended without a begin");
                }
                activeEvent = null;
                diagnostics.emit(() ->
                        AudioDiagnosticObserverException.invoke(() ->
                                observer.onServiceEnd(completed, snapshot)));
            }

            @Override
            public void onLifecycle(LifecycleEvent event) {
                diagnostics.emit(() ->
                        AudioDiagnosticObserverException.invoke(() ->
                                observer.onLifecycle(event)));
            }
        });
    }

    private static SmpsDriverServiceObserver.DriverAdmissionOrigin musicOrigin(
            long voiceId, int musicId) {
        return new SmpsDriverServiceObserver.DriverAdmissionOrigin(
                SmpsDriverServiceObserver.DriverOriginKind.MUSIC,
                voiceId, musicId);
    }

    private static SmpsDriverServiceObserver.DriverAdmissionOrigin sfxOrigin(
            long voiceId, int sfxId) {
        return new SmpsDriverServiceObserver.DriverAdmissionOrigin(
                SmpsDriverServiceObserver.DriverOriginKind.SFX,
                voiceId, sfxId);
    }

    private static int restoredSfxSoundId(
            PresentationVoiceSnapshot.Smps snapshot) {
        for (SmpsDriverSnapshot.SequencerEntry entry
                : snapshot.driver().sequencers()) {
            if (entry.sfx()) {
                return entry.source().id();
            }
        }
        return -1;
    }

    private ChipWriteObserver diagnosticChipWriteObserver(
            DiagnosticDispatcher diagnostics) {
        if (chipWriteObserver == ChipWriteObserver.NONE) {
            return ChipWriteObserver.NONE;
        }
        ChipWriteObserver observer = chipWriteObserver;
        return new ChipWriteObserver() {
            @Override
            public void onYm2612Write(
                    int port, int register, int value) {
                diagnostics.emit(() ->
                        AudioDiagnosticObserverException.invoke(() ->
                                observer.onYm2612Write(
                                        port, register, value)));
            }

            @Override
            public void onPsgWrite(int value) {
                diagnostics.emit(() ->
                        AudioDiagnosticObserverException.invoke(() ->
                                observer.onPsgWrite(value)));
            }
        };
    }

    private SmpsSequencerConfig copyPresentationConfig(
            String gameId,
            SmpsSequencerConfig sourceConfig,
            boolean coordFlagHandlerRequired) {
        return SmpsAssetCatalog.bindLegacyConfig(
                requireGameId(gameId), sourceConfig,
                coordFlagHandlerRequired, coordFlagHandlers);
    }

    private static SmpsSequencerConfig copyStaticConfig(
            SmpsSequencerConfig sourceConfig) {
        return SmpsAssetCatalog.copyConfigWithoutHandler(sourceConfig);
    }

    private DecodedPcm decode(String assetId) throws IOException {
        return settings.pcmCache().getOrDecode(
                assetId, () -> settings.wavAssets().open(assetId));
    }

    private int roundedOutputSampleRate() {
        long rounded = Math.round(settings.outputSampleRate());
        if (rounded <= 0 || rounded > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "output sample rate cannot be represented as an integer");
        }
        return (int) rounded;
    }

    private static String fallbackSfxAsset(String name) {
        String value = Objects.requireNonNull(name, "name");
        return switch (value) {
            case "JUMP" -> "sfx/jump.wav";
            case "RING", "RING_LEFT", "RING_RIGHT" -> "sfx/ring.wav";
            case "SPINDASH", "SPINDASH_CHARGE" -> "sfx/spindash.wav";
            case "SKID" -> "sfx/skid.wav";
            default -> "sfx/" + value.toLowerCase() + ".wav";
        };
    }

    private static String gameIdFor(
            SmpsSourceDescriptor source, String fallback) {
        if (source.donorGameId() != null) {
            return source.donorGameId();
        }
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException(
                "no game id cached for SMPS source " + source);
    }

    private static SmpsSourceDescriptor describeLegacyMusic(
            AudioSourceDescriptor descriptor, AbstractSmpsData data) {
        return switch (descriptor.route()) {
            case BASE_MUSIC_ID -> SmpsSourceDescriptor.baseMusic(data);
            case DONOR_MUSIC_ID -> SmpsSourceDescriptor.donorMusic(
                    descriptor.donorGameId(), data);
            default -> SmpsSourceDescriptor.from(data);
        };
    }

    private static SmpsAssetKey musicAssetKey(
            String gameId,
            int musicId,
            AudioSourceDescriptor descriptor) {
        SmpsAssetKey.Route route = switch (descriptor.route()) {
            case BASE_MUSIC_ID -> SmpsAssetKey.Route.BASE_MUSIC;
            case DONOR_MUSIC_ID -> SmpsAssetKey.Route.DONOR_MUSIC;
            default -> throw new IllegalArgumentException(
                    "SMPS music requires a base or donor descriptor");
        };
        return new SmpsAssetKey(gameId, route, musicId, null);
    }

    private static void validateSfxKey(SmpsAssetKey key) {
        switch (key.route()) {
            case BASE_ID, DONOR_ID -> {
                if (key.assetId() < 0 || key.assetName() != null) {
                    throw new IllegalArgumentException(
                            "id SMPS route requires only a non-negative id");
                }
            }
            case BASE_NAME, FALLBACK_NAME -> {
                if (key.assetName() == null
                        || key.assetName().isBlank()) {
                    throw new IllegalArgumentException(
                            "named SMPS route requires a name");
                }
            }
            case BASE_MUSIC, DONOR_MUSIC ->
                    throw new IllegalArgumentException(
                            "music route cannot register an SFX asset");
        }
    }

    private static void validateMusicKey(SmpsAssetKey key) {
        switch (key.route()) {
            case BASE_MUSIC, DONOR_MUSIC -> {
                if (key.assetId() < 0 || key.assetName() != null) {
                    throw new IllegalArgumentException(
                            "music route requires only a non-negative id");
                }
            }
            case BASE_ID, BASE_NAME, DONOR_ID, FALLBACK_NAME ->
                    throw new IllegalArgumentException(
                            "SFX route cannot register a music asset");
        }
    }

    private void assertOwnerBoundary() {
        if (!ownerThreadBoundary.getAsBoolean()) {
            throw new IllegalStateException(
                    "SMPS source instantiation requires the owner boundary");
        }
    }

    private static String requireGameId(String gameId) {
        String value = Objects.requireNonNull(gameId, "gameId");
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "gameId must not be blank");
        }
        return value;
    }

    private static AbstractSmpsData copySmpsData(
            AbstractSmpsData source) {
        if (source instanceof FrozenSmpsData) {
            return source;
        }
        return source instanceof SmpsSfxData sfx
                ? new FrozenSfxData(source, sfx)
                : new FrozenSmpsData(source);
    }

    private static class FrozenSmpsData extends AbstractSmpsData
            implements SmpsProgramView {
        private final byte[][] voices = new byte[256][];
        private final byte[][] psgEnvelopes = new byte[256][];
        private final byte[][] modEnvelopes = new byte[256][];
        private int[] words;
        private int baseNoteOffset;
        private int psgBaseNoteOffset;

        FrozenSmpsData(AbstractSmpsData source) {
            super(Objects.requireNonNull(source, "source")
                    .getData().clone(), source.getZ80StartAddress());
            voicePtr = source.getVoicePtr();
            channels = source.getChannels();
            psgChannels = source.getPsgChannels();
            dividingTiming = source.getDividingTiming();
            tempo = source.getTempo();
            dacPointer = source.getDacPointer();
            fmPointers = source.getFmPointers().clone();
            fmKeyOffsets = source.getFmKeyOffsets().clone();
            fmVolumeOffsets = source.getFmVolumeOffsets().clone();
            psgPointers = source.getPsgPointers().clone();
            psgKeyOffsets = source.getPsgKeyOffsets().clone();
            psgVolumeOffsets = source.getPsgVolumeOffsets().clone();
            psgModEnvs = source.getPsgModEnvs().clone();
            psgInstruments = source.getPsgInstruments().clone();
            id = source.getId();
            palSpeedupDisabled = source.isPalSpeedupDisabled();
            baseNoteOffset = source.getBaseNoteOffset();
            psgBaseNoteOffset = source.getPsgBaseNoteOffset();
            words = new int[Math.max(0, data.length - 1)];
            for (int index = 0; index < words.length; index++) {
                words[index] = source.read16(index);
            }
            for (int index = 0; index < 256; index++) {
                int lookupId = index;
                voices[index] = copyNullable(
                        safely(() -> source.getVoice(lookupId)));
                psgEnvelopes[index] = copyNullable(
                        safely(() -> source.getPsgEnvelope(lookupId)));
                modEnvelopes[index] = copyNullable(
                        safely(() -> source.getModEnvelope(lookupId)));
            }
        }

        @Override
        protected void parseHeader() {
            // Fields are copied from the already parsed source.
        }

        @Override
        public byte[] getData() {
            return data.clone();
        }

        @Override
        public int[] getFmPointers() {
            return fmPointers.clone();
        }

        @Override
        public int[] getFmKeyOffsets() {
            return fmKeyOffsets.clone();
        }

        @Override
        public int[] getFmVolumeOffsets() {
            return fmVolumeOffsets.clone();
        }

        @Override
        public int[] getPsgPointers() {
            return psgPointers.clone();
        }

        @Override
        public int[] getPsgKeyOffsets() {
            return psgKeyOffsets.clone();
        }

        @Override
        public int[] getPsgVolumeOffsets() {
            return psgVolumeOffsets.clone();
        }

        @Override
        public int[] getPsgModEnvs() {
            return psgModEnvs.clone();
        }

        @Override
        public int[] getPsgInstruments() {
            return psgInstruments.clone();
        }

        @Override
        public void setId(int id) {
            throw new UnsupportedOperationException(
                    "frozen SMPS metadata cannot be changed");
        }

        @Override
        public void setPalSpeedupDisabled(boolean disabled) {
            throw new UnsupportedOperationException(
                    "frozen SMPS metadata cannot be changed");
        }

        @Override
        public byte[] getVoice(int voiceId) {
            return copyAt(voices, voiceId);
        }

        @Override
        public byte[] getPsgEnvelope(int id) {
            return copyAt(psgEnvelopes, id);
        }

        @Override
        public byte[] getModEnvelope(int id) {
            return copyAt(modEnvelopes, id);
        }

        @Override
        public int voiceLength(int voiceId) {
            return lengthAt(voices, voiceId);
        }

        @Override
        public byte voiceByteAt(int voiceId, int index) {
            return voices[voiceId][index];
        }

        @Override
        public int psgEnvelopeLength(int envelopeId) {
            return lengthAt(psgEnvelopes, envelopeId);
        }

        @Override
        public byte psgEnvelopeByteAt(int envelopeId, int index) {
            return psgEnvelopes[envelopeId][index];
        }

        @Override
        public int modEnvelopeLength(int envelopeId) {
            return lengthAt(modEnvelopes, envelopeId);
        }

        @Override
        public byte modEnvelopeByteAt(int envelopeId, int index) {
            return modEnvelopes[envelopeId][index];
        }

        @Override
        protected byte[] materializeVoiceForSequencer(int voiceId) {
            return copyAt(voices, voiceId);
        }

        @Override
        protected byte[] materializePsgEnvelopeForSequencer(int envelopeId) {
            return copyAt(psgEnvelopes, envelopeId);
        }

        @Override
        protected byte[] materializeModEnvelopeForSequencer(int envelopeId) {
            return copyAt(modEnvelopes, envelopeId);
        }

        @Override
        public int read16(int offset) {
            if (offset < 0 || offset >= words.length) {
                throw new IndexOutOfBoundsException(offset);
            }
            return words[offset];
        }

        @Override
        public int getBaseNoteOffset() {
            return baseNoteOffset;
        }

        @Override
        public int getPsgBaseNoteOffset() {
            return psgBaseNoteOffset;
        }

        private static byte[] copyAt(byte[][] values, int index) {
            return index < 0 || index >= values.length
                    ? null : copyNullable(values[index]);
        }

        private static int lengthAt(byte[][] values, int index) {
            if (index < 0 || index >= values.length
                    || values[index] == null) {
                return 0;
            }
            return values[index].length;
        }
    }

    private static final class FrozenSfxData extends FrozenSmpsData
            implements SmpsSfxData {
        private final int tickMultiplier;
        private final List<FrozenTrack> tracks;

        FrozenSfxData(AbstractSmpsData source, SmpsSfxData sfx) {
            super(source);
            tickMultiplier = sfx.getTickMultiplier();
            tracks = sfx.getTrackEntries().stream()
                    .map(track -> new FrozenTrack(
                            track.channelMask(),
                            track.pointer(),
                            track.transpose(),
                            track.volume()))
                    .toList();
        }

        @Override
        public int getTickMultiplier() {
            return tickMultiplier;
        }

        @Override
        public List<? extends SmpsSfxTrack> getTrackEntries() {
            return tracks;
        }
    }

    private record FrozenTrack(
            int channelMask, int pointer, int transpose, int volume)
            implements SmpsSfxData.SmpsSfxTrack {
    }

    @FunctionalInterface
    private interface ByteArraySource {
        byte[] get();
    }

    private static byte[] safely(ByteArraySource source) {
        try {
            return source.get();
        } catch (RuntimeException failure) {
            AudioDiagnosticObserverException.rethrowIfPresent(failure);
            return null;
        }
    }

    private static byte[] copyNullable(byte[] source) {
        return source == null ? null : Arrays.copyOf(source, source.length);
    }
}
