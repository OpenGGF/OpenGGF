package com.openggf.game.soniccd;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameId;
import com.openggf.game.internal.MediaSceneArt;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.host.SceneRomArtFactory;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Detached file views: the default is PP1 Present, with explicit ISO names for every other program. */
final class CdMediaSceneArt implements MediaSceneArt {
    @Override public SceneRomArt defaultArt(GameDataSource source) {
        return program(source, CdStagePayload.R11A.file().isoName());
    }

    @Override public SceneRomArt program(GameDataSource source, String path) {
        if (!(source instanceof CdDiscDataSource disc)) throw new IllegalArgumentException("Disc source required");
        CdNativeFile file = java.util.Arrays.stream(CdNativeFile.values())
                .filter(f -> f.isoName().equals(path)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Not a disc program: " + path));
        try {
            Rom view = Rom.fromReader(new RomByteReader(disc.readFile(file)), "disc program " + path);
            CdStagePayload stage = java.util.Arrays.stream(CdStagePayload.values())
                    .filter(p -> p.file() == file).findFirst().orElse(null);
            return SceneRomArtFactory.create(view, GameId.SCD, () -> stage == null ? null
                    : new com.openggf.sprites.art.PlayerSpriteArtProvider() {
                        @Override public com.openggf.sprites.art.SpriteArtSet loadPlayerSpriteArt(String code) throws IOException {
                            return "sonic".equals(code) ? CdPlayerArt.load(new com.openggf.game.soniccd.level.CdStageImage(disc.readFile(file)),
                                    com.openggf.game.soniccd.level.CdStageCatalogue.entry(stage)) : null;
                        }
                        @Override public com.openggf.level.Palette loadCharacterPalette(String code) {
                            try { return new com.openggf.game.soniccd.level.CdStageLoader(disc).load(stage, 0, false).getPalette(0); }
                            catch (IOException e) { throw new UncheckedIOException(e); }
                        }
                    }, null);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read disc art " + path, e);
        }
    }
}
