package com.openggf.mods.scene;

import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.data.Rom;
import com.openggf.game.GameId;
import com.openggf.level.render.ZonePictureSource;
import java.util.function.Supplier;

/** Builds the engine's {@link SceneRomArt} for a stock game. Engine-internal. */
public final class SceneRomArtFactory {
    private SceneRomArtFactory() {
    }

    /** ROM art without zone pictures. */
    public static SceneRomArt create(Rom rom, GameId game, Supplier<PlayerSpriteArtProvider> players,
            Supplier<com.openggf.sprites.art.SpriteArtSet> tailsTails) {
        return create(rom, game, players, tailsTails, null);
    }

    /**
     * ROM art whose {@link SceneRomArt#zoneBackdrop} and {@link SceneRomArt#levelOverview}
     * come from {@code zones} (null for none).
     */
    public static SceneRomArt create(Rom rom, GameId game, Supplier<PlayerSpriteArtProvider> players,
            Supplier<com.openggf.sprites.art.SpriteArtSet> tailsTails, ZonePictureSource zones) {
        return new RomSceneArt(rom, game, players, tailsTails, zones);
    }
}
