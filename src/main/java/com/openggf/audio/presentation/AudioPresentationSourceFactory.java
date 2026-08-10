package com.openggf.audio.presentation;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioAdmissionObserver;
import com.openggf.audio.AudioAdmissionObserver.AudioAdmissionDecision;
import com.openggf.audio.AudioDiagnosticObserverException;
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
import java.util.Set;
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

    private record CachedSmpsSource(
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

    private final BooleanSupplier ownerThreadBoundary;
    private final SmpsCoordFlagHandlerOwner coordFlagHandlers;
    private final Settings settings;
    private final Map<SmpsAssetKey, CachedSmpsSource> sfxAssets =
            new HashMap<>();
    private final Map<SmpsSourceDescriptor, CachedSmpsSource> sourcesByDescriptor =
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
        String resolvedGameId = requireGameId(gameId);
        Objects.requireNonNull(descriptor, "descriptor");
        CachedSmpsSource source = snapshotSource(
                resolvedGameId, data, dac, config, false);
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
        CachedSmpsSource source = new CachedSmpsSource(
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
            CachedSmpsSource source,
            SmpsDriver driver) {
        SmpsSequencer sequencer = newSequencer(source, driver);
        sequencer.setSourceDescriptor(
                describeMusic(descriptor, source.data()));
        sequencer.setSpeedShoes(settings.speedShoesEnabled());
        sequencer.setSpeedMultiplier(settings.speedMultiplier());
        sequencer.setFallbackVoiceData(source.data());
        driver.addSequencer(sequencer, false);
        sourcesByDescriptor.put(sequencer.getSourceDescriptor(), source);

        SmpsCompositeVoice voice = new SmpsCompositeVoice(
                voiceId, 0, musicId, descriptor, maxStereoFrames, driver);
        return voice;
    }

    public void warmSmpsSfxAsset(
            SmpsAssetKey key,
            AbstractSmpsData data,
            DacData dac,
            SmpsSequencerConfig config) {
        warmSmpsSfxAsset(key, data, dac, config, false);
    }

    public void warmSmpsSfxAsset(
            SmpsAssetKey key,
            AbstractSmpsData data,
            DacData dac,
            SmpsSequencerConfig config,
            boolean specialSfx) {
        SmpsAssetKey resolvedKey =
                Objects.requireNonNull(key, "key");
        validateKey(resolvedKey);
        sfxAssets.put(resolvedKey, snapshotSource(
                resolvedKey.gameId(), data, dac, config, specialSfx));
    }

    public ResolvedSmpsSfxSource resolveSmpsSfx(
            long standaloneVoiceId,
            SmpsAssetKey assetKey,
            int pitchQ16,
            int priority,
            int continuousSfxId,
            int trackCount,
            int maxStereoFrames) {
        if (pitchQ16 <= 0) {
            throw new IllegalArgumentException("pitchQ16 must be positive");
        }
        validateKey(Objects.requireNonNull(assetKey, "assetKey"));
        CachedSmpsSource cached = sfxAssets.get(assetKey);
        return new ResolvedSmpsSfxSource(
                standaloneVoiceId, assetKey, pitchQ16, priority,
                continuousSfxId, trackCount, maxStereoFrames,
                cached != null ? cached.data().getId() : assetKey.sfxId(),
                cached != null && cached.specialSfx());
    }

    @Override
    public SmpsSequencer instantiateCached(
            ResolvedSmpsSfxSource source,
            SmpsDriver currentOwner) {
        assertOwnerBoundary();
        cacheLookupCount.incrementAndGet();
        CachedSmpsSource cached = requireCached(source);
        SmpsDriver owner = Objects.requireNonNull(
                currentOwner, "currentOwner");
        SmpsSequencer sequencer = newSequencer(
                freshSource(cached), owner);
        sequencer.setSourceDescriptor(
                describeSfx(source.assetKey(), cached.data()));
        sequencer.setSfxMode(true);
        sequencer.setPitch(source.pitchQ16() / 65_536.0f);
        sequencer.setSfxPriority(source.priority());
        sequencer.setSpecialSfx(cached.specialSfx());
        SmpsSequencer music = owner.firstMusicSequencer();
        if (music != null) {
            sequencer.setFallbackVoiceData(music.getSmpsData());
        }
        sourcesByDescriptor.put(sequencer.getSourceDescriptor(), cached);
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
                CachedSmpsSource cached =
                        sourcesByDescriptor.get(entry.source());
                AbstractSmpsData data = cached != null
                        ? cached.data()
                        : delegate.resolveSmpsData(entry);
                return copySmpsData(data);
            }

            @Override
            public DacData resolveDacData(
                    SmpsDriverSnapshot.SequencerEntry entry) {
                CachedSmpsSource cached =
                        sourcesByDescriptor.get(entry.source());
                DacData dac = cached != null
                        ? cached.dac()
                        : delegate.resolveDacData(entry);
                return copyDac(dac);
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
                CachedSmpsSource cached =
                        sourcesByDescriptor.get(entry.source());
                SmpsSequencerConfig sourceConfig = cached != null
                        ? cached.staticConfig()
                        : delegate.resolveConfig(entry);
                String gameId = cached != null
                        ? cached.gameId()
                        : gameIdFor(entry.source(), voiceGameId);
                return copyPresentationConfig(
                        gameId,
                        sourceConfig,
                        cached != null
                                ? cached.coordFlagHandlerRequired()
                                : sourceConfig.getCoordFlagHandler() != null);
            }
        };
    }

    private CachedSmpsSource requireCached(
            ResolvedSmpsSfxSource source) {
        Objects.requireNonNull(source, "source");
        CachedSmpsSource cached = sfxAssets.get(source.assetKey());
        if (cached == null) {
            throw new IllegalStateException(
                    "SMPS SFX asset cache miss: " + source.assetKey());
        }
        return cached;
    }

    private CachedSmpsSource snapshotSource(
            String gameId,
            AbstractSmpsData data,
            DacData dac,
            SmpsSequencerConfig config,
            boolean specialSfx) {
        return new CachedSmpsSource(
                requireGameId(gameId),
                copySmpsData(Objects.requireNonNull(data, "data")),
                copyDac(Objects.requireNonNull(dac, "dac")),
                copyStaticConfig(Objects.requireNonNull(config, "config")),
                config.getCoordFlagHandler() != null,
                specialSfx);
    }

    /**
     * Builds a per-instantiation view of a cached source.
     *
     * <p>The sequence data is <em>shared</em>, not re-copied. {@code cached.data()}
     * is already a {@code Frozen*} snapshot — {@code snapshotSource} froze it on
     * the way into the cache — and a frozen snapshot is immutable: its fields are
     * assigned once in the constructor, the only two setters on
     * {@code AbstractSmpsData} ({@code setId}, {@code setPalSpeedupDisabled}) are
     * called by the SMPS loaders at load time and never during playback, and no
     * code writes into the byte arrays handed out by {@code getData} /
     * {@code getVoice} / {@code getPsgEnvelope}. Re-freezing it per SFX trigger
     * therefore produced a byte-identical object at real cost: each copy clones
     * the sequence bytes, three {@code byte[256][]} tables, an {@code int[]} word
     * table, and 256 voices plus envelopes — and a zone like CNZ fires SFX
     * constantly.
     *
     * <p>The DAC and static config are still copied per instantiation. They are
     * not covered by the immutability argument above, and this change is
     * deliberately scoped to the one object proven safe to share.
     */
    private CachedSmpsSource freshSource(CachedSmpsSource cached) {
        return new CachedSmpsSource(
                cached.gameId(),
                cached.data(),
                copyDac(cached.dac()),
                copyStaticConfig(cached.staticConfig()),
                cached.coordFlagHandlerRequired(),
                cached.specialSfx());
    }

    private SmpsSequencer newSequencer(
            CachedSmpsSource source, SmpsDriver driver) {
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
        SmpsSequencerConfig.Builder copy =
                copyBuilder(sourceConfig);
        if (coordFlagHandlerRequired) {
            copy.coordFlagHandler(
                    coordFlagHandlers.handlerFor(requireGameId(gameId)));
        } else {
            copy.coordFlagHandler(null);
        }
        return copy.build();
    }

    private static SmpsSequencerConfig copyStaticConfig(
            SmpsSequencerConfig sourceConfig) {
        return copyBuilder(sourceConfig)
                .coordFlagHandler(null)
                .build();
    }

    private static SmpsSequencerConfig.Builder copyBuilder(
            SmpsSequencerConfig sourceConfig) {
        Objects.requireNonNull(sourceConfig, "sourceConfig");
        return new SmpsSequencerConfig.Builder()
                .speedUpTempos(sourceConfig.getSpeedUpTempos())
                .tempoModBase(sourceConfig.getTempoModBase())
                .fmChannelOrder(sourceConfig.getFmChannelOrder())
                .psgChannelOrder(sourceConfig.getPsgChannelOrder())
                .tempoMode(sourceConfig.getTempoMode())
                .coordFlagParamOverrides(
                        sourceConfig.getCoordFlagParamOverrides())
                .applyModOnNote(sourceConfig.isApplyModOnNote())
                .halveModSteps(sourceConfig.isHalveModSteps())
                .extraTrkEndFlags(Set.copyOf(
                        sourceConfig.getExtraTrkEndFlags()))
                .relativePointers(sourceConfig.isRelativePointers())
                .tempoOnFirstTick(sourceConfig.isTempoOnFirstTick())
                .direct68kDriver(sourceConfig.isDirect68kDriver())
                .fmSfxTakeoverMode(sourceConfig.getFmSfxTakeoverMode())
                .fmVoiceWriteProfile(sourceConfig.getFmVoiceWriteProfile())
                .volMode(sourceConfig.getVolMode())
                .psgEnvCmd80(sourceConfig.getPsgEnvCmd80())
                .noteOnPrevent(sourceConfig.getNoteOnPrevent())
                .delayFreq(sourceConfig.getDelayFreq())
                .modAlgo(sourceConfig.getModAlgo())
                .fadeOutDelay(sourceConfig.getFadeOutDelay())
                .fadeOutSteps(sourceConfig.getFadeOutSteps())
                .fadeInSteps(sourceConfig.getFadeInSteps())
                .fadeInDelay(sourceConfig.getFadeInDelay());
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

    private static SmpsSourceDescriptor describeMusic(
            AudioSourceDescriptor descriptor, AbstractSmpsData data) {
        return switch (descriptor.route()) {
            case BASE_MUSIC_ID -> SmpsSourceDescriptor.baseMusic(data);
            case DONOR_MUSIC_ID -> SmpsSourceDescriptor.donorMusic(
                    descriptor.donorGameId(), data);
            default -> SmpsSourceDescriptor.from(data);
        };
    }

    private static SmpsSourceDescriptor describeSfx(
            SmpsAssetKey key, AbstractSmpsData data) {
        return switch (key.route()) {
            case BASE_ID -> SmpsSourceDescriptor.baseSfx(data);
            case BASE_NAME -> SmpsSourceDescriptor.baseNamedSfx(
                    key.sfxName(), data);
            case DONOR_ID -> SmpsSourceDescriptor.donorSfx(
                    key.gameId(), data);
            case FALLBACK_NAME -> SmpsSourceDescriptor.from(data);
        };
    }

    private static void validateKey(SmpsAssetKey key) {
        switch (key.route()) {
            case BASE_ID, DONOR_ID -> {
                if (key.sfxId() < 0 || key.sfxName() != null) {
                    throw new IllegalArgumentException(
                            "id SMPS route requires only a non-negative id");
                }
            }
            case BASE_NAME, FALLBACK_NAME -> {
                if (key.sfxName() == null
                        || key.sfxName().isBlank()) {
                    throw new IllegalArgumentException(
                            "named SMPS route requires a name");
                }
            }
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

    private static DacData copyDac(DacData source) {
        Objects.requireNonNull(source, "source");
        Map<Integer, byte[]> samples = new HashMap<>();
        for (Map.Entry<Integer, byte[]> entry
                : source.samples.entrySet()) {
            samples.put(entry.getKey(), entry.getValue().clone());
        }
        Map<Integer, DacData.DacEntry> mapping = new HashMap<>();
        for (Map.Entry<Integer, DacData.DacEntry> entry
                : source.mapping.entrySet()) {
            DacData.DacEntry value = entry.getValue();
            mapping.put(entry.getKey(), new DacData.DacEntry(
                    value.sampleId, value.rate));
        }
        return new DacData(samples, mapping, source.baseCycles);
    }

    private static AbstractSmpsData copySmpsData(
            AbstractSmpsData source) {
        return source instanceof SmpsSfxData sfx
                ? new FrozenSfxData(source, sfx)
                : new FrozenSmpsData(source);
    }

    private static class FrozenSmpsData extends AbstractSmpsData {
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
