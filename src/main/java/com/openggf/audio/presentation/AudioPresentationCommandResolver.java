package com.openggf.audio.presentation;

import com.openggf.audio.presentation.AudioPresentationCommand.AddSmpsSfx;
import com.openggf.audio.presentation.AudioPresentationCommand.ChangeMusicTempo;
import com.openggf.audio.presentation.AudioPresentationCommand.EndMusicOverride;
import com.openggf.audio.presentation.AudioPresentationCommand.FadeMusic;
import com.openggf.audio.presentation.AudioPresentationCommand.PushMusicOverride;
import com.openggf.audio.presentation.AudioPresentationCommand.ReplaceMusic;
import com.openggf.audio.presentation.AudioPresentationCommand.ResetRingAlternation;
import com.openggf.audio.presentation.AudioPresentationCommand.RestoreMusicOverride;
import com.openggf.audio.presentation.AudioPresentationCommand.SetSpeedMultiplier;
import com.openggf.audio.presentation.AudioPresentationCommand.SetSpeedShoes;
import com.openggf.audio.presentation.AudioPresentationCommand.StartSampleSfx;
import com.openggf.audio.presentation.AudioPresentationCommand.StopAllSfx;
import com.openggf.audio.presentation.AudioPresentationCommand.StopMusic;
import com.openggf.audio.presentation.AudioPresentationCommand.StopRawPcm;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencerConfig;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Resolves logical audio commands into immutable presentation mutations.
 *
 * <p>This class has no registry or backend reference. Loading, WAV decoding,
 * and cache warming happen synchronously during submission; queued SMPS SFX
 * contain only an asset key and primitive metadata.
 */
public final class AudioPresentationCommandResolver {

    public interface Sources {
        String baseGameId();

        AbstractSmpsData loadBaseMusic(int musicId);

        AbstractSmpsData loadDonorMusic(String donorGameId, int musicId);

        AbstractSmpsData loadBaseSfx(int sfxId);

        AbstractSmpsData loadBaseSfx(String name);

        AbstractSmpsData loadDonorSfx(String donorGameId, int sfxId);

        DacData dacFor(String gameId);

        SmpsSequencerConfig configFor(String gameId);

        int sfxPriority(String gameId, int sfxId);

        boolean specialSfx(String gameId, int sfxId);

        boolean continuousSfx(String gameId, int sfxId);

        int maxStereoFrames();
    }

    private final AudioPresentationCommandQueue queue;
    private final AudioPresentationSourceFactory factory;
    private final Sources sources;
    private final Consumer<String> warningConsumer;
    private final BooleanSupplier ownerThreadBoundary;
    private final Consumer<AudioPresentationCommand> synchronousApply;
    private long nextVoiceId = 1;

    public AudioPresentationCommandResolver(
            AudioPresentationCommandQueue queue,
            AudioPresentationSourceFactory factory,
            Sources sources,
            Consumer<String> warningConsumer,
            BooleanSupplier ownerThreadBoundary,
            Consumer<AudioPresentationCommand> synchronousApply) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.factory = Objects.requireNonNull(factory, "factory");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.warningConsumer =
                Objects.requireNonNull(warningConsumer, "warningConsumer");
        this.ownerThreadBoundary = Objects.requireNonNull(
                ownerThreadBoundary, "ownerThreadBoundary");
        this.synchronousApply = Objects.requireNonNull(
                synchronousApply, "synchronousApply");
    }

    public static AudioPresentationCommandResolver controlsOnly(
            AudioPresentationCommandQueue queue,
            AudioPresentationSourceFactory factory,
            BooleanSupplier ownerThreadBoundary,
            Consumer<AudioPresentationCommand> synchronousApply) {
        return new AudioPresentationCommandResolver(
                queue, factory, new EmptySources(), ignored -> {
                }, ownerThreadBoundary, synchronousApply);
    }

    public void submit(AudioCommand command) {
        Objects.requireNonNull(command, "command");
        switch (command) {
            case AudioCommand.PlayMusic music -> submitMusic(music);
            case AudioCommand.PlaySfx sfx -> submitSfx(sfx);
            case AudioCommand.FadeOutMusic fade ->
                    enqueue(new FadeMusic(fade.steps(), fade.delay()));
            case AudioCommand.StopMusic ignored ->
                    enqueue(new StopMusic());
            case AudioCommand.StopAllSfx ignored ->
                    enqueue(new StopAllSfx());
            case AudioCommand.EndMusicOverride end ->
                    enqueue(new EndMusicOverride(end.musicId()));
            case AudioCommand.RestoreMusic ignored ->
                    enqueue(new RestoreMusicOverride());
            case AudioCommand.SetSpeedShoes speed ->
                    enqueue(new SetSpeedShoes(speed.enabled()));
            case AudioCommand.SetSpeedMultiplier speed ->
                    enqueue(new SetSpeedMultiplier(speed.multiplier()));
            case AudioCommand.ChangeMusicTempo tempo ->
                    enqueue(new ChangeMusicTempo(tempo.dividingTiming()));
            case AudioCommand.ResetRingAlternation ring ->
                    enqueue(new ResetRingAlternation(ring.ringLeft()));
        }
    }

    public void submitRawPcm(byte[] pcm, int sourceRate) {
        byte[] source = Objects.requireNonNull(pcm, "pcm").clone();
        String assetId = "sega-pcm:" + sourceRate + ":"
                + source.length + ":" + sha256(source);
        DecodedPcm registered = factory.registerUnsigned8Mono(
                assetId, source, sourceRate);
        enqueue(AudioPresentationCommand.ReplaceRawPcm.fromVoice(
                factory.segaPcm(allocateVoiceId(), registered)));
    }

    public void stopRawPcm() {
        enqueue(new StopRawPcm());
    }

    private void submitMusic(AudioCommand.PlayMusic command) {
        if (command.route() == AudioCommand.MusicRoute.SYSTEM_COMMAND) {
            warn("Rejected system music command " + command.musicId()
                    + ": presentation system-command semantics are unsupported");
            return;
        }
        AudioPresentationCommand resolved;
        try {
            AudioPresentationCommand.MusicVoiceEntry voice =
                    switch (command.route()) {
                        case BASE_SMPS -> resolveSmpsMusic(
                                sources.baseGameId(),
                                command.musicId(),
                                sources.loadBaseMusic(command.musicId()),
                                AudioSourceDescriptor.baseMusic(
                                        command.musicId()));
                        case DONOR_SMPS -> resolveSmpsMusic(
                                requireGameId(command.donorGameId()),
                                command.musicId(),
                                sources.loadDonorMusic(
                                        command.donorGameId(),
                                        command.musicId()),
                                AudioSourceDescriptor.donorMusic(
                                        command.donorGameId(),
                                        command.musicId()));
                        case FALLBACK_WAV -> factory.fallbackMusic(
                                allocateVoiceId(),
                                command.musicId(),
                                AudioSourceDescriptor.fallbackMusic(
                                        command.musicId()));
                        case SYSTEM_COMMAND ->
                                throw new AssertionError("handled above");
                    };
            resolved = command.override()
                    ? new PushMusicOverride(voice)
                    : new ReplaceMusic(voice);
        } catch (IOException | RuntimeException failure) {
            warn("Rejected music " + command.musicId()
                    + " via " + command.route() + ": "
                    + failure.getMessage());
            return;
        }
        enqueue(resolved);
    }

    private AudioPresentationCommand.MusicVoiceEntry resolveSmpsMusic(
            String gameId,
            int musicId,
            AbstractSmpsData data,
            AudioSourceDescriptor descriptor) {
        if (data == null) {
            throw new IllegalStateException("loader returned no SMPS data");
        }
        return factory.musicSmps(
                gameId,
                musicId,
                allocateVoiceId(),
                data,
                requireDac(gameId),
                requireConfig(gameId),
                descriptor,
                sources.maxStereoFrames());
    }

    private void submitSfx(AudioCommand.PlaySfx command) {
        AudioPresentationCommand resolved;
        try {
            resolved = switch (command.route()) {
                case BASE_SMPS_ID -> resolveSmpsSfxCommand(
                        sources.baseGameId(),
                        new SmpsAssetKey(
                                sources.baseGameId(),
                                SmpsAssetKey.Route.BASE_ID,
                                command.sfxId(), null),
                        command.sfxId(),
                        sources.loadBaseSfx(command.sfxId()),
                        command.pitch());
                case BASE_SMPS_NAME -> {
                    AbstractSmpsData data =
                            sources.loadBaseSfx(command.sfxName());
                    yield resolveSmpsSfxCommand(
                            sources.baseGameId(),
                            new SmpsAssetKey(
                                    sources.baseGameId(),
                                    SmpsAssetKey.Route.BASE_NAME,
                                    -1, command.sfxName()),
                            data != null ? data.getId() : -1,
                            data,
                            command.pitch());
                }
                case DONOR_SMPS -> resolveSmpsSfxCommand(
                        requireGameId(command.donorGameId()),
                        new SmpsAssetKey(
                                command.donorGameId(),
                                SmpsAssetKey.Route.DONOR_ID,
                                command.sfxId(), null),
                        command.sfxId(),
                        sources.loadDonorSfx(
                                command.donorGameId(), command.sfxId()),
                        command.pitch());
                case FALLBACK_NAME, RING_RESOLVED ->
                        StartSampleSfx.fromVoice(
                                factory.fallbackSfx(
                                        allocateVoiceId(),
                                        command.sfxName(),
                                        0,
                                        command.pitch()));
            };
        } catch (IOException | RuntimeException failure) {
            warn("Rejected SFX " + command.sfxName()
                    + "/" + command.sfxId() + " via "
                    + command.route() + ": " + failure.getMessage());
            return;
        }
        enqueue(resolved);
    }

    private AudioPresentationCommand resolveSmpsSfxCommand(
            String gameId,
            SmpsAssetKey key,
            int sfxId,
            AbstractSmpsData data,
            float pitch) {
        if (data == null) {
            throw new IllegalStateException("loader returned no SMPS data");
        }
        int priority = sources.sfxPriority(gameId, sfxId);
        factory.warmSmpsSfxAsset(
                key, data, requireDac(gameId), requireConfig(gameId),
                sources.specialSfx(gameId, sfxId));
        int continuousId =
                sources.continuousSfx(gameId, sfxId) ? sfxId : 0;
        return new AddSmpsSfx(factory.resolveSmpsSfx(
                allocateVoiceId(),
                key,
                pitchQ16(pitch),
                priority,
                continuousId,
                data.getChannels() + data.getPsgChannels(),
                sources.maxStereoFrames()));
    }

    private DacData requireDac(String gameId) {
        return Objects.requireNonNull(
                sources.dacFor(gameId),
                "no DAC data for " + gameId);
    }

    private SmpsSequencerConfig requireConfig(String gameId) {
        return Objects.requireNonNull(
                sources.configFor(gameId),
                "no sequencer config for " + gameId);
    }

    private void enqueue(AudioPresentationCommand command) {
        queue.submit(command, ownerThreadBoundary, synchronousApply);
    }

    private long allocateVoiceId() {
        return nextVoiceId++;
    }

    private void warn(String warning) {
        warningConsumer.accept(warning);
    }

    private static int pitchQ16(float pitch) {
        if (!Float.isFinite(pitch) || pitch <= 0.0f) {
            throw new IllegalArgumentException(
                    "pitch must be finite and positive");
        }
        long value = Math.round(pitch * 65_536.0);
        if (value <= 0 || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("pitch is outside Q16 range");
        }
        return (int) value;
    }

    private static String requireGameId(String gameId) {
        String value = Objects.requireNonNull(gameId, "gameId");
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "gameId must not be blank");
        }
        return value;
    }

    private static String sha256(byte[] source) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(source));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("SHA-256 is required by the JVM", impossible);
        }
    }

    private static final class EmptySources implements Sources {
        @Override
        public String baseGameId() {
            return "unconfigured";
        }

        @Override
        public AbstractSmpsData loadBaseMusic(int musicId) {
            return null;
        }

        @Override
        public AbstractSmpsData loadDonorMusic(
                String donorGameId, int musicId) {
            return null;
        }

        @Override
        public AbstractSmpsData loadBaseSfx(int sfxId) {
            return null;
        }

        @Override
        public AbstractSmpsData loadBaseSfx(String name) {
            return null;
        }

        @Override
        public AbstractSmpsData loadDonorSfx(
                String donorGameId, int sfxId) {
            return null;
        }

        @Override
        public DacData dacFor(String gameId) {
            return null;
        }

        @Override
        public SmpsSequencerConfig configFor(String gameId) {
            return null;
        }

        @Override
        public int sfxPriority(String gameId, int sfxId) {
            return 0;
        }

        @Override
        public boolean specialSfx(String gameId, int sfxId) {
            return false;
        }

        @Override
        public boolean continuousSfx(String gameId, int sfxId) {
            return false;
        }

        @Override
        public int maxStereoFrames() {
            return 0;
        }
    }
}
