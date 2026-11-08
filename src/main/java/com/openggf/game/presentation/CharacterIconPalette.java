package com.openggf.game.presentation;

import com.openggf.game.*;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import java.io.IOException;
import java.util.stream.IntStream;

/** Icon faces share the custom leader's private normal palette, never native stage rows. */
public final class CharacterIconPalette {
    private CharacterIconPalette() { }

    public static boolean hasHudIcon() throws IOException {
        var presentation = StageCharacterPresentation.active();
        var host = switch (GameServices.module().getGameId()) {
            case S1 -> CharacterPresentation.Host.S1;
            case S2 -> CharacterPresentation.Host.S2;
            case S3K -> CharacterPresentation.Host.S3K;
            case STANDALONE -> null;
        };
        var art = presentation == null || host == null ? null : presentation.art().get(host);
        return art != null && art.hudLifeIcon() != null;
    }

    /** The palette context is installed by playable art initialization before the first draw. */
    public static int activeLine(int fallback) {
        var sprites = GameServices.spritesOrNull();
        var player = sprites == null ? null : sprites.getMainPlayable();
        if (player == null || player.characterKey().isBuiltin()) return fallback;
        var renderer = player.getSpriteRenderer();
        var context = renderer == null ? null : renderer.getRenderContext();
        return context == null ? fallback : context.getEffectivePaletteLine(renderer.paletteLine());
    }

    public static ObjectSpriteSheet monitor(ObjectSpriteSheet sheet, CharacterPresentation presentation,
            CharacterPresentation.Host host, int firstTile) {
        var art = presentation == null || host == null ? null : presentation.art().get(host);
        if (art == null || art.oneUpFace() == null || StageCharacterPresentation.activeDefinition() == null) return sheet;
        var frames = IntStream.range(0, sheet.getFrameCount()).mapToObj(sheet::getFrame).toList();
        return new ObjectSpriteSheet(sheet.getPatterns(), frames, sheet.getPaletteIndex(), sheet.getFrameDelay()) {
            @Override public SpriteMappingFrame getFrame(int index) {
                var frame = super.getFrame(index);
                return new SpriteMappingFrame(frame.pieces().stream().map(piece -> {
                    if (piece.tileIndex() < firstTile || piece.tileIndex() >= firstTile + 4) return piece;
                    return new SpriteMappingPiece(piece.xOffset(), piece.yOffset(), piece.widthTiles(),
                            piece.heightTiles(), piece.tileIndex(), piece.hFlip(), piece.vFlip(),
                            activeLine(piece.paletteIndex()), piece.priority());
                }).toList());
            }
        };
    }
}
