package com.openggf.audio.presentation;

import com.openggf.audio.AudioDiagnosticObserverException;
import com.openggf.audio.GameSound;
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
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;

import java.io.IOException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Resolves logical audio commands into immutable presentation mutations.
 *
 * <p>This class has no registry or backend reference. Loading, WAV decoding,
 * and catalog registration happen synchronously during submission; queued
 * SMPS SFX contain only an asset key and primitive metadata.
 */
public final class AudioPresentationCommandResolver {

    public interface Sources {
        SourceAccess sourceFor(
                SmpsAssetKey.Route route, String donorGameId);

        int maxStereoFrames();
    }

    /** Immutable policy captured with one loader/dependency publication. */
    public interface SfxPolicy {
        int priority(int sfxId);

        boolean special(int sfxId);

        boolean continuous(int sfxId);
    }

    /**
     * One immutable route source handle. Resolution performs every loader,
     * dependency, and policy lookup through this captured value so a reentrant
     * source replacement cannot compose two published generations.
     */
    public record SourceAccess(
            String gameId,
            long dependencyGeneration,
            SmpsLoader loader,
            DacData dac,
            SmpsSequencerConfig config,
            SfxPolicy sfxPolicy) {
        public SourceAccess {
            requireGameId(gameId);
            if (dependencyGeneration < 0) {
                throw new IllegalArgumentException(
                        "dependencyGeneration must be non-negative");
            }
            Objects.requireNonNull(sfxPolicy, "sfxPolicy");
        }
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

    /**
     * Submits an SMPS SFX against the source tuple captured by its caller.
     *
     * <p>Public manager entry points classify and register before publishing
     * their logical command. Retaining that exact source here prevents a
     * reentrant ROM/profile/donor replacement during the first loader call
     * from resolving the published command against another generation.
     * Direct resolver callers continue to use {@link #submit(AudioCommand)}
     * and retain lookup-before-load miss handling.</p>
     */
    public void submitRegisteredSmpsSfx(
            AudioCommand.PlaySfx command, SourceAccess source) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(source, "source");
        if (command.route() == AudioCommand.SfxRoute.FALLBACK_NAME
                || command.route() == AudioCommand.SfxRoute.RING_RESOLVED) {
            throw new IllegalArgumentException(
                    "registered SMPS submission requires an SMPS route");
        }
        submitSfx(command, source);
    }

    public void submitRawPcm(byte[] pcm, int sourceRate) {
        byte[] source = Objects.requireNonNull(pcm, "pcm").clone();
        String assetId = "sega-pcm:" + sourceRate + ":"
                + source.length + ":" + HexFormat.of().formatHex(source);
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
                        case BASE_SMPS -> {
                            SourceAccess source = sources.sourceFor(
                                    SmpsAssetKey.Route.BASE_MUSIC, null);
                            yield resolveSmpsMusic(
                                    SmpsAssetKey.Route.BASE_MUSIC,
                                    source, command.musicId(),
                                    AudioSourceDescriptor.baseMusic(
                                            command.musicId()));
                        }
                        case DONOR_SMPS -> {
                            String gameId = requireGameId(
                                    command.donorGameId());
                            SourceAccess source = sources.sourceFor(
                                    SmpsAssetKey.Route.DONOR_MUSIC, gameId);
                            yield resolveSmpsMusic(
                                    SmpsAssetKey.Route.DONOR_MUSIC,
                                    source, command.musicId(),
                                    AudioSourceDescriptor.donorMusic(
                                            command.donorGameId(),
                                            command.musicId()));
                        }
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
            AudioDiagnosticObserverException.rethrowIfPresent(failure);
            warn("Rejected music " + command.musicId()
                    + " via " + command.route() + ": "
                    + failure.getMessage());
            return;
        }
        enqueue(resolved);
    }

    private AudioPresentationCommand.MusicVoiceEntry resolveSmpsMusic(
            SmpsAssetKey.Route route,
            SourceAccess source,
            int musicId,
            AudioSourceDescriptor descriptor) {
        SmpsAssetKey key = new SmpsAssetKey(
                source.gameId(), route, musicId, null);
        SmpsAssetCatalog.ProgramEntry entry =
                factory.findRegisteredSmpsMusicAsset(
                        key, source.dependencyGeneration());
        if (entry == null) {
            AbstractSmpsData data = Objects.requireNonNull(
                    loadMusic(source, musicId),
                    "loader returned no SMPS data");
            entry = factory.registerSmpsMusicAsset(
                    key, source.dependencyGeneration(), data,
                    requireDac(route, source),
                    requireConfig(route, source));
        }
        return factory.musicSmpsFromRegistered(
                source.gameId(),
                musicId,
                allocateVoiceId(),
                descriptor,
                sources.maxStereoFrames(), entry);
    }

    private void submitSfx(AudioCommand.PlaySfx command) {
        submitSfx(command, null);
    }

    private void submitSfx(
            AudioCommand.PlaySfx command, SourceAccess capturedSource) {
        if (command.route() == AudioCommand.SfxRoute.RING_RESOLVED) {
            enqueue(new ResetRingAlternation(
                    !GameSound.RING_LEFT.name().equals(command.sfxName())));
        }
        AudioPresentationCommand resolved;
        try {
            resolved = switch (command.route()) {
                case BASE_SMPS_ID -> {
                    SourceAccess source = capturedSource != null
                            ? capturedSource
                            : sources.sourceFor(
                            SmpsAssetKey.Route.BASE_ID, null);
                    SmpsAssetKey key = new SmpsAssetKey(
                            source.gameId(), SmpsAssetKey.Route.BASE_ID,
                            command.sfxId(), null);
                    yield resolveSmpsSfxCommand(
                            source, key, command.sfxId(),
                            () -> loadSfx(source, command.sfxId()),
                            command.pitch());
                }
                case BASE_SMPS_NAME -> {
                    SourceAccess source = capturedSource != null
                            ? capturedSource
                            : sources.sourceFor(
                            SmpsAssetKey.Route.BASE_NAME, null);
                    SmpsAssetKey key = new SmpsAssetKey(
                            source.gameId(), SmpsAssetKey.Route.BASE_NAME,
                            -1, command.sfxName());
                    yield resolveSmpsSfxCommand(
                            source, key, -1,
                            () -> loadSfx(source, command.sfxName()),
                            command.pitch());
                }
                case DONOR_SMPS -> {
                    String gameId = requireGameId(command.donorGameId());
                    SourceAccess source = capturedSource != null
                            ? capturedSource
                            : sources.sourceFor(
                            SmpsAssetKey.Route.DONOR_ID, gameId);
                    if (!gameId.equals(source.gameId())) {
                        throw new IllegalArgumentException(
                                "captured donor source does not match "
                                        + gameId);
                    }
                    SmpsAssetKey key = new SmpsAssetKey(
                            source.gameId(), SmpsAssetKey.Route.DONOR_ID,
                            command.sfxId(), null);
                    yield resolveSmpsSfxCommand(
                            source, key, command.sfxId(),
                            () -> loadSfx(source, command.sfxId()),
                            command.pitch());
                }
                case FALLBACK_NAME, RING_RESOLVED ->
                        StartSampleSfx.fromVoice(
                                factory.fallbackSfx(
                                        allocateVoiceId(),
                                        command.sfxName(),
                                        0,
                                        command.pitch()));
            };
        } catch (IOException | RuntimeException failure) {
            AudioDiagnosticObserverException.rethrowIfPresent(failure);
            warn("Rejected SFX " + command.sfxName()
                    + "/" + command.sfxId() + " via "
                    + command.route() + ": " + failure.getMessage());
            return;
        }
        enqueue(resolved);
    }

    private AudioPresentationCommand resolveSmpsSfxCommand(
            SourceAccess source,
            SmpsAssetKey key,
            int requestedSfxId,
            SmpsSfxLoader loader,
            float pitch) {
        long generation = source.dependencyGeneration();
        SmpsAssetCatalog.ProgramEntry entry =
                factory.findRegisteredSmpsSfxAsset(key, generation);
        if (entry == null) {
            AbstractSmpsData data = Objects.requireNonNull(
                    loader.load(), "loader returned no SMPS data");
            int resolvedSfxId = requestedSfxId >= 0
                    ? requestedSfxId : data.getId();
            entry = factory.registerSmpsSfxAsset(
                    key, generation, data,
                    requireDac(key.route(), source),
                    requireConfig(key.route(), source),
                    source.sfxPolicy().special(resolvedSfxId));
        }
        int sfxId = entry.assetId();
        int priority = source.sfxPolicy().priority(sfxId);
        int continuousId =
                source.sfxPolicy().continuous(sfxId)
                        ? sfxId : 0;
        return new AddSmpsSfx(factory.resolveSmpsSfx(
                allocateVoiceId(),
                key,
                generation,
                pitchQ16(pitch),
                priority,
                continuousId,
                entry.trackCount(),
                sources.maxStereoFrames()));
    }

    @FunctionalInterface
    private interface SmpsSfxLoader {
        AbstractSmpsData load();
    }

    private static AbstractSmpsData loadMusic(
            SourceAccess source, int musicId) {
        return source.loader() != null
                ? source.loader().loadMusic(musicId) : null;
    }

    private static AbstractSmpsData loadSfx(
            SourceAccess source, int sfxId) {
        return source.loader() != null
                ? source.loader().loadSfx(sfxId) : null;
    }

    private static AbstractSmpsData loadSfx(
            SourceAccess source, String name) {
        return source.loader() != null
                ? source.loader().loadSfx(name) : null;
    }

    private static DacData requireDac(
            SmpsAssetKey.Route route, SourceAccess source) {
        return Objects.requireNonNull(
                source.dac(),
                "no DAC data for " + source.gameId()
                        + " via " + route);
    }

    private static SmpsSequencerConfig requireConfig(
            SmpsAssetKey.Route route, SourceAccess source) {
        return Objects.requireNonNull(
                source.config(),
                "no sequencer config for " + source.gameId()
                        + " via " + route);
    }

    private void enqueue(AudioPresentationCommand command) {
        queue.submit(command, ownerThreadBoundary, synchronousApply);
    }

    private long allocateVoiceId() {
        return nextVoiceId++;
    }

    /**
     * Advances this resolver's allocation cursor when reconstructing a
     * detached logical timeline from an existing registry snapshot.
     */
    public void reserveVoiceIdsThrough(long nextAvailableVoiceId) {
        if (nextAvailableVoiceId < 0) {
            throw new IllegalArgumentException(
                    "nextAvailableVoiceId must be non-negative");
        }
        nextVoiceId = Math.max(nextVoiceId, nextAvailableVoiceId);
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

    private static final class EmptySources implements Sources {
        @Override
        public SourceAccess sourceFor(
                SmpsAssetKey.Route route, String donorGameId) {
            String gameId = donorGameId != null
                    ? requireGameId(donorGameId) : "unconfigured";
            return new SourceAccess(
                    gameId, 0, null, null, null,
                    new SfxPolicy() {
                        @Override public int priority(int sfxId) { return 0; }
                        @Override public boolean special(int sfxId) {
                            return false;
                        }
                        @Override public boolean continuous(int sfxId) {
                            return false;
                        }
                    });
        }

        @Override
        public int maxStereoFrames() {
            return 0;
        }
    }
}
