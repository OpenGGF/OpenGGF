package com.openggf.mods.scene;

import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.data.Rom;
import com.openggf.game.GameId;
import java.util.function.Supplier;

/** Builds the engine's {@link SceneRomArt} for a stock game. Engine-internal. */
public final class SceneRomArtFactory {
    private SceneRomArtFactory() {
    }

    public static SceneRomArt create(Rom rom, GameId game, Supplier<PlayerSpriteArtProvider> players,
            Supplier<com.openggf.sprites.art.SpriteArtSet> tailsTails) {
        return new RomSceneArt(rom, game, players, tailsTails);
    }
}
