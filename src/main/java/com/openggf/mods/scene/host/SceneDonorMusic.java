package com.openggf.mods.scene.host;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.ScopedDonorAudio;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.data.Rom;
import com.openggf.game.BuiltInRomDetectors;
import com.openggf.game.GameId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A scene's songs from any supplied stock ROM, all through the base game's sound driver.
 * The running game's songs take the base route. Another game's song takes the cross-game
 * donor route under a donor registration scoped to this scene, which closing restores.
 * Engine-internal.
 */
final class SceneDonorMusic implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(SceneDonorMusic.class.getName());
    private static final List<String> GAMES = List.of("s1", "s2", "s3k");
    private final AudioManager audio;
    private final SceneRomLibrary library;
    private final Map<String, GameAudioProfile> profiles = new HashMap<>();
    private final Map<String, SmpsLoader> loaders = new HashMap<>();
    private final Map<String, ScopedDonorAudio> scopes = new LinkedHashMap<>();
    private boolean donorPlaying;
    private boolean closed;

    SceneDonorMusic(AudioManager audio, SceneRomLibrary library) {
        this.audio = Objects.requireNonNull(audio, "audio");
        this.library = Objects.requireNonNull(library, "library");
    }

    /** The lower-case stock game code; throws for unknown games and negative music ids. */
    static String validate(String gameId, int musicId) {
        String game = Objects.requireNonNull(gameId, "gameId").toLowerCase(Locale.ROOT);
        if (!GAMES.contains(game)) throw new IllegalArgumentException("Unknown stock game: " + gameId);
        if (musicId < 0) throw new IllegalArgumentException("music id must be non-negative");
        return game;
    }

    /** Plays a supplied ROM's song as the current music, or returns false when it is unavailable. */
    boolean play(String gameId, int musicId) {
        String game = validate(gameId, musicId);
        if (closed) return false;
        Rom rom = library.sourceRom(game);
        if (rom == null) return false;
        GameAudioProfile profile = profiles.computeIfAbsent(game,
                code -> BuiltInRomDetectors.forGame(GameId.fromCode(code)).createModule().getAudioProfile());
        SmpsLoader loader = loaders.get(game);
        try {
            if (loader == null) {
                loader = profile.createSmpsLoader(rom);
                loaders.put(game, loader);
            }
            if (loader.loadMusic(musicId) == null) return false;
        } catch (RuntimeException unavailable) {
            LOG.log(Level.FINE, "Scene music unavailable: " + game + " 0x" + Integer.toHexString(musicId),
                    unavailable);
            return false;
        }
        if (game.equals(library.activeGame())) {
            audio.playMusic(musicId);
            donorPlaying = false;
            return true;
        }
        if (!scopes.containsKey(game)) {
            scopes.put(game, ScopedDonorAudio.register(audio, game, loader, loader.loadDacData(),
                    profile.getSequencerConfig(), profile));
        }
        audio.playDonorMusic(game, musicId);
        donorPlaying = true;
        return true;
    }

    /** The scene asked the base driver for a song or command by its own id. */
    void driverMusicRequested(int musicId) {
        GameAudioProfile base = audio.getAudioProfile();
        // An override such as the 1-up hands the current song back when it ends.
        if (base == null || !base.isMusicOverride(musicId)) donorPlaying = false;
    }

    /** The base driver's music was stopped through the scene. */
    void driverMusicStopped() {
        donorPlaying = false;
    }

    /** Park borrowed routes without retiring this retained scene's decoded audio. */
    void suspend() {
        if (donorPlaying) audio.stopMusic();
        donorPlaying = false;
        List<ScopedDonorAudio> opened = new ArrayList<>(scopes.values());
        scopes.clear();
        for (int index = opened.size() - 1; index >= 0; index--) opened.get(index).close();
    }

    /** Stops another game's song still playing, then restores every borrowed donor route. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        try {
            if (donorPlaying) audio.stopMusic();
        } finally {
            List<ScopedDonorAudio> opened = new ArrayList<>(scopes.values());
            scopes.clear();
            loaders.clear();
            RuntimeException failure = null;
            for (int index = opened.size() - 1; index >= 0; index--) {
                try {
                    opened.get(index).close();
                } catch (RuntimeException restore) {
                    if (failure == null) failure = restore; else failure.addSuppressed(restore);
                }
            }
            if (failure != null) throw failure;
        }
    }
}
