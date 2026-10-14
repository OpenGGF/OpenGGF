package com.openggf.game.sonic3k;

import com.openggf.level.Pattern;
import com.openggf.level.objects.HudStaticArt;
import com.openggf.level.objects.HudStaticArtFactory;
import com.openggf.level.objects.HudStaticArtFactory.Layout;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;

import java.util.List;

public final class Sonic3kHudStaticArtFactory {
    private static final Layout LAYOUT = new Layout(1, null, 1, false);

    private Sonic3kHudStaticArtFactory() {
    }

    public static HudStaticArt create(Pattern[] textPatterns, Pattern[] livesPatterns) {
        HudStaticArt common = HudStaticArtFactory.create(textPatterns, livesPatterns, LAYOUT);
        // Map_HUD's TIME piece is four contiguous columns at tile $10.
        // The I at $12 differs from the RINGS I at $0A in the ROM blob.
        SpriteMappingFrame time = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0, 0, 1, 2, 16, false, false, 1),
                new SpriteMappingPiece(8, 0, 1, 2, 18, false, false, 1),
                new SpriteMappingPiece(16, 0, 1, 2, 20, false, false, 1),
                new SpriteMappingPiece(24, 0, 1, 2, 22, false, false, 1)));
        // Map_HUD's RINGS row starts at tile $08; SCORE uses the distinct R at $06.
        // Both glyphs come from the same ROM bank, but differ at two native pixels.
        SpriteMappingFrame rings = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0, 0, 1, 2, 8, false, false, 1),
                new SpriteMappingPiece(8, 0, 1, 2, 10, false, false, 1),
                new SpriteMappingPiece(16, 0, 1, 2, 12, false, false, 1),
                new SpriteMappingPiece(24, 0, 1, 2, 14, false, false, 1),
                new SpriteMappingPiece(32, 0, 1, 2, 0, false, false, 1)));
        return new HudStaticArt(common.patterns(), common.scoreFrame(), common.debugScoreFrame(),
                time, common.timeFlashFrame(), rings, common.ringsFlashFrame(),
                common.livesFrame());
    }
}
