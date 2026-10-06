package com.openggf.mods.scene.host;

import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.data.Rom;
import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.session.SessionManager;
import com.openggf.level.render.ZonePictureSource;
import com.openggf.mods.scene.SceneRomArt;
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
     * ROM art for the running stock game {@code module}: character frames from the game the
     * module creates for the current session (built on first use), Tails' tails from the module,
     * and zone pictures and title cards when the module offers them
     * ({@code getGameService(ZonePictureSource.Factory.class)}).
     */
    public static SceneRomArt forModule(GameModule module, Rom rom) {
        Supplier<PlayerSpriteArtProvider> players = new Supplier<>() {
            private PlayerSpriteArtProvider cached;

            @Override
            public PlayerSpriteArtProvider get() {
                if (cached == null) {
                    var session = SessionManager.getCurrentWorldSession();
                    Object game = session == null ? null : module.createGame(session.getDataSource());
                    cached = game instanceof PlayerSpriteArtProvider provider ? provider : null;
                }
                return cached;
            }
        };
        ZonePictureSource.Factory zones = module.getGameService(ZonePictureSource.Factory.class);
        return create(rom, module.getGameId(), players, module::loadTailsTailArt,
                zones == null ? null : zones.create(rom));
    }

    /** ROM art whose zone pictures and title cards come from {@code zones} (null for none). */
    public static SceneRomArt create(Rom rom, GameId game, Supplier<PlayerSpriteArtProvider> players,
            Supplier<com.openggf.sprites.art.SpriteArtSet> tailsTails, ZonePictureSource zones) {
        return new RomSceneArt(rom, game, players, tailsTails, zones);
    }
}
