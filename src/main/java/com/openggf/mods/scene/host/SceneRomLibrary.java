package com.openggf.mods.scene.host;

import com.openggf.data.Rom;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomManager;
import com.openggf.game.GameModule;
import com.openggf.game.BuiltInRomDetectors;
import com.openggf.game.GameId;
import com.openggf.mods.scene.SceneRomArt;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Scene-owned views of the player's available stock ROMs. The active ROM is borrowed;
 * additional logical-ROM views are owned here and closed with the scene. No session,
 * default-game setting or shared secondary-ROM cache is changed. Engine-internal.
 */
public final class SceneRomLibrary implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(SceneRomLibrary.class.getName());
    private static final List<String> GAMES = List.of("s1", "s2", "s3k");
    private final Map<String, SceneRomArt> art = new LinkedHashMap<>();
    private final Map<String, GameModule> modules = new LinkedHashMap<>();
    private final Map<String, Rom> sourceRoms = new LinkedHashMap<>();
    private final List<Rom> ownedRoms = new ArrayList<>();
    private final SceneRomAudioSources audioSources = new SceneRomAudioSources(this);
    private boolean closed;
    private final String activeGame;

    public SceneRomLibrary(GameModule activeModule, Rom activeRom, RomManager roms) {
        Objects.requireNonNull(activeModule, "activeModule");
        Objects.requireNonNull(activeRom, "activeRom");
        Objects.requireNonNull(roms, "roms");
        String active = activeModule.getGameId().code();
        activeGame = active;
        for (String code : GAMES) {
            if (code.equals(active)) {
                art.put(code, SceneRomArtFactory.forModule(activeModule, activeRom));
                sourceRoms.put(code, activeRom);
                modules.put(code, activeModule);
                continue;
            }
            RomIdentity identity = identity(code);
            if (!roms.isLogicalRomAvailable(identity)) continue;
            Rom extra = null;
            try {
                extra = Rom.fromReader(roms.openLogicalRom(identity), "scene " + code);
                GameModule extraModule = stockModule(code);
                SceneRomArt prepared = SceneRomArtFactory.forModule(extraModule, extra);
                ownedRoms.add(extra);
                art.put(code, prepared);
                sourceRoms.put(code, extra);
                modules.put(code, extraModule);
            } catch (IOException | RuntimeException failure) {
                if (extra != null) extra.close();
                LOG.log(Level.WARNING, "Scene ROM unavailable: " + code, failure);
            }
        }
    }

    /** CPU-only supplied-ROM library for offline evaluation; every opened ROM is owned here. */
    public static SceneRomLibrary open(Map<String, java.nio.file.Path> paths) throws IOException {
        return new SceneRomLibrary(paths);
    }

    /** Exact offline logical-ROM views; this library owns wrappers, while readers remain immutable. */
    public static SceneRomLibrary openReaders(Map<String, com.openggf.data.RomByteReader> readers) throws IOException {
        return openReaders(readers,GAMES.stream().filter(readers::containsKey).findFirst().orElse(""));
    }

    public static SceneRomLibrary openReaders(Map<String, com.openggf.data.RomByteReader> readers, String activeGame) throws IOException {
        return new SceneRomLibrary(readers,"scene logical ",activeGame);
    }

    private SceneRomLibrary(Map<String, com.openggf.data.RomByteReader> readers, String description, String activeGame) throws IOException {
        Objects.requireNonNull(readers);
        if (readers.isEmpty() || !GAMES.containsAll(readers.keySet()))
            throw new IllegalArgumentException("Supply one or more named stock ROMs");
        if (!readers.containsKey(activeGame)) throw new IllegalArgumentException("Supply the active scene ROM: " + activeGame);
        this.activeGame = activeGame;
        try {
            for (String code : GAMES) {
                if (!readers.containsKey(code)) continue;
                Rom rom = Rom.fromReader(Objects.requireNonNull(readers.get(code)), description + code);
                ownedRoms.add(rom);
                var detector = BuiltInRomDetectors.forGame(GameId.fromCode(code));
                if (!detector.canHandle(rom)) throw new IOException("ROM does not match requested game " + code);
                GameModule module = detector.createModule();
                modules.put(code, module); sourceRoms.put(code, rom);
                art.put(code, SceneRomArtFactory.forModule(module, rom));
            }
        } catch (IOException | RuntimeException failure) {
            close(); throw failure;
        }
    }

    private SceneRomLibrary(Map<String, java.nio.file.Path> paths) throws IOException {
        Objects.requireNonNull(paths);
        if (paths.isEmpty() || !GAMES.containsAll(paths.keySet()))
            throw new IllegalArgumentException("Supply one or more named stock ROMs");
        activeGame = GAMES.stream().filter(paths::containsKey).findFirst().orElseThrow();
        try {
            for (String code : GAMES) {
                if (!paths.containsKey(code)) continue;
                Rom rom = new Rom(); ownedRoms.add(rom);
                var detector = BuiltInRomDetectors.forGame(GameId.fromCode(code));
                if (!rom.open(paths.get(code).toAbsolutePath().toString()) || !detector.canHandle(rom))
                    throw new IOException("ROM does not match requested game " + code);
                GameModule module = detector.createModule();
                modules.put(code, module); sourceRoms.put(code, rom);
                art.put(code, SceneRomArtFactory.forModule(module, rom));
            }
        } catch (IOException | RuntimeException failure) {
            close(); throw failure;
        }
    }

    /** Engine-internal physical resources from the same exact modules/ROMs used for presentation. */
    public GameModule module(String gameId) {
        requireOpen();
        GameModule module = modules.get(gameId);
        if (module == null) throw new IllegalArgumentException("Supply the " + gameId + " ROM");
        return module;
    }

    public com.openggf.sprites.art.SpriteArtSet characterArt(String gameId, String character) {
        requireOpen(); module(gameId);
        return ((RomSceneArt) art.get(gameId)).playerArt(character);
    }

    public com.openggf.level.Level level(String gameId, int zone, int act) {
        requireOpen(); module(gameId);
        var level = ((RomSceneArt) art.get(gameId)).puppetLevel(zone, act);
        if (level == null) throw new IllegalArgumentException("Set has no detached native terrain");
        return level;
    }

    public List<String> availableGames() {
        requireOpen();
        return List.copyOf(art.keySet());
    }

    public SceneRomArt rom(String gameId) {
        requireOpen();
        if (gameId == null || !GAMES.contains(gameId)) throw new IllegalArgumentException("Unknown stock game: " + gameId);
        return art.get(gameId);
    }

    public String activeGame() {
        requireOpen();
        return activeGame;
    }

    /** One native route per game and producer epoch; cue/effect leases may close in either order. */
    public SceneRomAudioSources.Lease acquireAudioSource(com.openggf.audio.AudioManager audio, String game) {
        requireOpen();
        return audioSources.acquire(audio,game);
    }

    /** The same supplied-ROM view used by art; borrowed by the scene's audio owner. */
    public Rom sourceRom(String gameId) {
        requireOpen();
        if (gameId == null || !GAMES.contains(gameId)) {
            throw new IllegalArgumentException("Unknown stock game: " + gameId);
        }
        return sourceRoms.get(gameId);
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Scene ROM library is closed");
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try { audioSources.close(); }
        finally {
            art.clear(); sourceRoms.clear(); modules.clear();
            ownedRoms.forEach(Rom::close); ownedRoms.clear();
        }
    }

    private static RomIdentity identity(String code) {
        return switch (code) {
            case "s1" -> RomIdentity.S1;
            case "s2" -> RomIdentity.S2;
            default -> RomIdentity.S3K;
        };
    }

    private static GameModule stockModule(String code) {
        return BuiltInRomDetectors.forGame(GameId.fromCode(code)).createModule();
    }
}
