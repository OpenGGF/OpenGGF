package com.openggf.graphics;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.level.render.SpritePresentationRenderer;

import org.junit.jupiter.api.Test;

/** Directed palette masks survive native SAT admission, including pixel-row splits. */
class TestObjectVisualSatMasks {
    @Test
    void explicitDirectedAttachmentPreservesMaskAndOrdinaryNativeBooleanPathDoesNotChange() {
        GraphicsManager graphics = new GraphicsManager();
        graphics.setHeadlessMode(true);
        for (boolean directed : new boolean[] {false, true, false}) {
            var frame =
                    SpritePresentationRenderer.prepare(
                            graphics,
                            0,
                            0,
                            () -> {
                                graphics.beginSpriteSatCollection();
                                graphics.setCurrentSpriteTileOcclusionPaletteMask(7);
                                graphics.submitSpriteSatPiece(piece(100, 80, 512, 1));
                                if (directed) {
                                    ObjectVisualGraphicsAccess.attachLastVisualMask(graphics, 7);
                                    ObjectVisualGraphicsAccess.clipLastSatTile(graphics, 2, 6);
                                }
                                graphics.endSpriteSatCollectionAndReplay();
                            });
            assertEquals(1, frame.tiles().size());
            var tile = frame.tiles().getFirst();
            assertEquals(directed ? 7 : 15, tile.occlusionMask());
            assertEquals(directed ? 2 : 0, tile.rowStart());
            assertEquals(directed ? 6 : 8, tile.rowEnd());
        }
    }

    @Test
    void nativeMaskSplitsKeepDirectedAttachmentOnEveryRemainingScanlineRun() {
        GraphicsManager graphics = new GraphicsManager();
        graphics.setHeadlessMode(true);
        var frame =
                SpritePresentationRenderer.prepare(
                        graphics,
                        0,
                        0,
                        () -> {
                            graphics.beginSpriteSatCollection();
                            graphics.submitSpriteSatPiece(piece(100, 0, 500, 4));
                            graphics.requestSpriteMask();
                            graphics.submitSpriteSatControlEntry(108, 6, 1, 1, 0x7c0);
                            graphics.submitSpriteSatControlEntry(100, 6, 1, 1, 0);
                            graphics.submitSpriteSatPiece(piece(120, 0, 512, 4));
                            ObjectVisualGraphicsAccess.attachLastVisualMask(graphics, 7);
                            graphics.endSpriteSatCollectionAndReplay();
                        });
        var directed = frame.tiles().stream().filter(tile -> tile.patternId() >= 512).toList();
        assertEquals(4, directed.size());
        assertTrue(directed.stream().allMatch(tile -> tile.occlusionMask() == 7));
        assertTrue(directed.stream().anyMatch(tile -> tile.rowStart() == 0 && tile.rowEnd() == 6));
        assertTrue(directed.stream().anyMatch(tile -> tile.rowStart() == 6 && tile.rowEnd() == 8));
        assertTrue(
                frame.tiles().stream()
                        .filter(tile -> tile.patternId() < 512)
                        .allMatch(tile -> tile.occlusionMask() == 15));
    }

    private static SpritePieceRenderer.PreparedPiece piece(int x, int y, int pattern, int height) {
        return new SpritePieceRenderer.PreparedPiece(
                x,
                y,
                1,
                height,
                pattern,
                pattern & 0x7ff,
                0,
                false,
                false,
                false,
                false,
                0,
                height);
    }
}
