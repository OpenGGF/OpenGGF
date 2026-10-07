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
    private final Map<String, Rom> sourceRoms = new LinkedHashMap<>();
    private final List<Rom> ownedRoms = new ArrayList<>();
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
                continue;
            }
            RomIdentity identity = identity(code);
            if (!roms.isLogicalRomAvailable(identity)) continue;
            Rom extra = null;
            try {
                extra = Rom.fromReader(roms.openLogicalRom(identity), "scene " + code);
                SceneRomArt prepared = SceneRomArtFactory.forModule(module(code), extra);
                ownedRoms.add(extra);
                art.put(code, prepared);
                sourceRoms.put(code, extra);
            } catch (IOException | RuntimeException failure) {
                if (extra != null) extra.close();
                LOG.log(Level.WARNING, "Scene ROM unavailable: " + code, failure);
            }
        }
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
        art.clear();
        sourceRoms.clear();
        ownedRoms.forEach(Rom::close);
        ownedRoms.clear();
    }

    private static RomIdentity identity(String code) {
        return switch (code) {
            case "s1" -> RomIdentity.S1;
            case "s2" -> RomIdentity.S2;
            default -> RomIdentity.S3K;
        };
    }

    private static GameModule module(String code) {
        return BuiltInRomDetectors.forGame(GameId.fromCode(code)).createModule();
    }
}
