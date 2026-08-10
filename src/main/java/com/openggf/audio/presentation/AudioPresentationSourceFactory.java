package com.openggf.audio.presentation;

import com.openggf.audio.AudioManager;
import com.openggf.audio.MusicRestoreSink;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.presentation.AudioPresentationCommand.MusicVoiceEntry;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
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

    private record LegacySmpsSource(
            String gameId,
            AbstractSmpsData data,
            DacData dac,
            SmpsSequencerConfig staticConfig,
            boolean coordFlagHandlerRequired,
            boolean specialSfx) {
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
                musicId, voiceId, descriptor, maxStereoFrames, source);
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
                musicId, voiceId, descriptor, maxStereoFrames, source);
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
            SmpsAssetCatalog.ProgramEntry source) {
        SmpsDriver driver = newConfiguredDriver();
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
            LegacySmpsSource source) {
        SmpsDriver driver = newConfiguredDriver();
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
                specialSfx);
    }

    public SmpsAssetCatalog.ProgramEntry findRegisteredSmpsSfxAsset(
            SmpsAssetKey key, long dependencyGeneration) {
        SmpsAssetKey resolvedKey = Objects.requireNonNull(key, "key");
        validateSfxKey(resolvedKey);
        return assetCatalog.find(new SmpsAssetCatalog.ProgramKey(
                resolvedKey, dependencyGeneration));
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
                false);
    }

    public SmpsAssetCatalog.ProgramEntry findRegisteredSmpsMusicAsset(
            SmpsAssetKey key, long dependencyGeneration) {
        SmpsAssetKey resolvedKey = Objects.requireNonNull(key, "key");
        validateMusicKey(resolvedKey);
        return assetCatalog.find(new SmpsAssetCatalog.ProgramKey(
                resolvedKey, dependencyGeneration));
    }

    private SmpsAssetCatalog.ProgramEntry register(
            SmpsAssetCatalog.ProgramKey key,
            AbstractSmpsData data,
            DacData dac,
            SmpsSequencerConfig config,
            boolean specialSfx) {
        SmpsAssetCatalog.ProgramEntry entry = assetCatalog.register(
                key, data, dac, config, specialSfx);
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
                data, dependencies.dac(), dependencies.config(), specialSfx);
    }

    private SmpsAssetCatalog.ProgramEntry registerCompatibilitySmpsMusicAsset(
            SmpsAssetKey key,
            AbstractSmpsData data,
            DacData dac,
            SmpsSequencerConfig config) {
        return register(new SmpsAssetCatalog.ProgramKey(key, 0),
                data, dac, config, false);
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
        return new ResolvedSmpsSfxSource(
                standaloneVoiceId, assetKey, dependencyGeneration,
                pitchQ16, priority,
                continuousSfxId, trackCount, maxStereoFrames);
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
                source.maxStereoFrames(), newConfiguredDriver());
    }

    public SmpsCompositeVoice recreateSmps(
            PresentationVoiceSnapshot.Smps snapshot,
            SmpsDriverSnapshot.DependencyResolver dependencies) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(dependencies, "dependencies");
        SmpsDriver driver = newConfiguredDriver();
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

    private SmpsDriver newConfiguredDriver() {
        SmpsDriver driver =
                new SmpsDriver(settings.outputSampleRate());
        driver.setRegion(settings.region());
        driver.setDacInterpolate(settings.dacInterpolate());
        driver.setOutputSampleRate(settings.outputSampleRate());
        driver.setPsgNoiseShiftOnEveryToggle(
                settings.psgNoiseShiftEveryToggle());
        return driver;
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
        return SmpsAssetCatalog.freezeStandalone(
                Objects.requireNonNull(source, "source"));
    }
}
