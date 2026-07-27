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
import com.openggf.audio.smps.SmpsSfxData;

import java.io.IOException;
import java.io.InputStream;
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
        CachedSmpsSource source = new CachedSmpsSource(
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
            CachedSmpsSource source) {
        SmpsDriver driver = newConfiguredDriver();
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
        return new ResolvedSmpsSfxSource(
                standaloneVoiceId, assetKey, pitchQ16, priority,
                continuousSfxId, trackCount, maxStereoFrames);
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
        String assetId = fallbackSfxAsset(name);
        return SampleBackedVoice.oneShot(
                voiceId,
                priority,
                decode(assetId),
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
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static byte[] copyNullable(byte[] source) {
        return source == null ? null : Arrays.copyOf(source, source.length);
    }
}
