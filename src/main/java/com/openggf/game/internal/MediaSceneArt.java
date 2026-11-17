package com.openggf.game.internal;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameId;
import com.openggf.level.render.ZonePictureSource;
import com.openggf.sprites.art.PlayerSpriteArtProvider;
import com.openggf.sprites.art.SpriteArtSet;
import java.util.function.Supplier;

/** Detached game-owned art facts from file-addressed stock media. The session retains source ownership. */
public interface MediaSceneArt {
    /** The presentation host adapts this bounded view; game code never constructs a mod scene type. */
    record View(GameId game, String identity, RomByteReader reader, Supplier<PlayerSpriteArtProvider> players,
                Supplier<SpriteArtSet> tails, ZonePictureSource zones) { }
    /** Art and terrain for the registry's complete stage identity (including period). */
    View stage(GameDataSource source, int zone, int act);
    View defaultArt(GameDataSource source);
    View program(GameDataSource source, String path);
}
