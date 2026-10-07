package com.openggf.mods.scene.host;

import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.CrossGameDonorProvider;
import com.openggf.game.GameId;
import com.openggf.game.GameModule;
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
     * ROM art for stock game {@code module}: character frames from the supplied
     * ROM's private stock decoder (built on first use), Tails' tails from that ROM,
     * and zone pictures and title cards when the module offers them
     * ({@code getGameService(ZonePictureSource.Factory.class)}).
     */
    public static SceneRomArt forModule(GameModule module, Rom rom) {
        CrossGameDonorProvider donor = module.getCrossGameDonorProvider();
        Supplier<PlayerSpriteArtProvider> players = new Supplier<>() {
            private PlayerSpriteArtProvider cached;

            @Override
            public PlayerSpriteArtProvider get() {
                if (cached == null) {
                    if (donor == null) return null;
                    try {
                        // Donor decoders read the supplied ROM without createGame's live PLC reset.
                        RomByteReader reader = RomByteReader.fromRom(rom);
                        PlayerSpriteArtProvider art = donor.createPlayerArtProvider(reader);
                        cached = new PlayerSpriteArtProvider() {
                            @Override
                            public com.openggf.sprites.art.SpriteArtSet loadPlayerSpriteArt(String code)
                                    throws java.io.IOException {
                                return art.loadPlayerSpriteArt(code);
                            }

                            @Override
                            public com.openggf.level.Palette loadCharacterPalette(String code) {
                                return donor.loadCharacterPalette(reader, code);
                            }
                        };
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException("Character art unavailable", e);
                    }
                }
                return cached;
            }
        };
        ZonePictureSource.Factory zones = module.getGameService(ZonePictureSource.Factory.class);
        Supplier<com.openggf.sprites.art.SpriteArtSet> tails = donor != null && donor.hasSeparateTailsTailArt()
                ? () -> {
                    try {
                        return donor.loadTailsTailArt(RomByteReader.fromRom(rom));
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException("Tails accessory art unavailable", e);
                    }
                } : null;
        return create(rom, module.getGameId(), players, tails,
                zones == null ? null : zones.create(rom));
    }

    /** ROM art whose zone pictures and title cards come from {@code zones} (null for none). */
    public static SceneRomArt create(Rom rom, GameId game, Supplier<PlayerSpriteArtProvider> players,
            Supplier<com.openggf.sprites.art.SpriteArtSet> tailsTails, ZonePictureSource zones) {
        return new RomSceneArt(rom, game, players, tailsTails, zones);
    }
}
