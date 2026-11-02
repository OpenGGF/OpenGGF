package com.openggf.graphics;

import com.openggf.level.render.SpritePieceRenderer.PreparedPiece;
import com.openggf.level.render.SpritePresentationRenderer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSpriteSatVerticalWrap {
    @Test
    void piecesAcrossLevelSeamSurviveAdmissionAfterWrapScopeEnds() {
        for (int cameraY : new int[]{-100, 1948, 2080}) {
            GraphicsManager graphics = new GraphicsManager();
            graphics.setHeadlessMode(true);
            var frame = SpritePresentationRenderer.prepare(graphics, 0, cameraY, () -> {
                graphics.beginSpriteSatCollection();
                graphics.enableVerticalWrapAdjust(2048, cameraY);
                graphics.submitSpriteSatPiece(new PreparedPiece(100, Math.floorMod(cameraY + 80, 2048),
                        1, 1, 512, 0, 0, false, false, false, true, 0, 1));
                graphics.disableVerticalWrapAdjust();
                graphics.endSpriteSatCollectionAndReplay();
            });
            assertEquals(1, frame.tiles().size(), "wrapped body must survive SAT scanline admission");
            assertEquals(80, frame.tiles().getFirst().y());
        }
    }
    @Test
    void wrappedMaskControlsStayAlignedWithWrappedArt() {
        GraphicsManager graphics = new GraphicsManager();
        graphics.setHeadlessMode(true);
        var frame = SpritePresentationRenderer.prepare(graphics, 0, -100, () -> {
            graphics.beginSpriteSatCollection();
            graphics.enableVerticalWrapAdjust(2048, -100);
            graphics.requestSpriteMask();
            graphics.submitSpriteSatControlEntry(100, 2028, 1, 1, 0x7c0);
            graphics.submitSpriteSatControlEntry(100, 2028, 1, 1, 0);
            graphics.submitSpriteSatPiece(new PreparedPiece(120, 2028, 1, 1, 512,
                    0, 0, false, false, false, true, 0, 1));
            graphics.disableVerticalWrapAdjust();
            graphics.endSpriteSatCollectionAndReplay();
        });
        assertTrue(frame.tiles().isEmpty(), "wrapped mask must suppress the later body");
    }

    @Test
    void distantArtIsRejectedWhenLevelWrapIsDisabled() {
        GraphicsManager graphics = new GraphicsManager();
        graphics.setHeadlessMode(true);
        var frame = SpritePresentationRenderer.prepare(graphics, 0, -100, () -> {
            graphics.beginSpriteSatCollection();
            graphics.submitSpriteSatPiece(new PreparedPiece(100, 2028, 1, 1, 512,
                    0, 0, false, false, false, true, 0, 1));
            graphics.endSpriteSatCollectionAndReplay();
        });
        assertTrue(frame.tiles().isEmpty());
    }

}
