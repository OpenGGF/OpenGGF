package com.openggf.audio;

import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;

import java.util.Objects;

/**
 * A donor-audio registration that lasts for one owner's scope, such as a mod scene.
 * Registering replaces the key's donor route like
 * {@link AudioManager#registerDonorLoader(String, SmpsLoader, DacData, SmpsSequencerConfig, GameAudioProfile)};
 * closing puts back the route and the music and sound bindings the key had when the
 * scope opened, so cross-game donation survives a scene that borrows its key.
 * Engine-internal; not creator API.
 */
public final class ScopedDonorAudio implements AutoCloseable {
    private final AudioManager audio;
    private final AudioManager.DonorKeyState previous;
    private boolean closed;

    private ScopedDonorAudio(AudioManager audio, AudioManager.DonorKeyState previous) {
        this.audio = audio;
        this.previous = previous;
    }

    /** Registers {@code gameId}'s donor route until {@link #close()}. */
    public static ScopedDonorAudio register(AudioManager audio, String gameId, SmpsLoader loader,
                                            DacData dac, SmpsSequencerConfig config,
                                            GameAudioProfile profile) {
        Objects.requireNonNull(audio, "audio");
        AudioManager.DonorKeyState previous = audio.captureDonorKeyState(gameId);
        audio.registerDonorLoader(gameId, loader, dac, config, profile);
        return new ScopedDonorAudio(audio, previous);
    }

    /** Restores the key's previous route and bindings; later calls do nothing. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        audio.restoreDonorKeyState(previous);
    }
}
