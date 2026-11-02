package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.graphics.SpritePresentation;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Inspect published native art without requiring scene-export recipes for donors. */
public final class SozWrapPresentationAssertions {
    private SozWrapPresentationAssertions() { }

    public static void assertPlayerArtPresent() {
        var tiles = GameServices.level().spritePresentationRenderer().spriteTables
                .capture().prepared().tiles();
        assertTrue(tiles.stream().anyMatch(tile -> tile.layer() == SpritePresentation.Layer.PLAYER),
                "sand carry must retain player art across the vertical seam");
    }
}
