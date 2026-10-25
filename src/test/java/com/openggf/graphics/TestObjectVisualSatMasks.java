package com.openggf.graphics;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.level.render.SpritePresentationRenderer;

import org.junit.jupiter.api.Test;

/** Directed palette masks survive native SAT admission, including pixel-row splits. */
class TestObjectVisualSatMasks {
    @Test
    void ordinaryNativeBooleanPriorityKeepsLowHighLowMasks() {
        GraphicsManager graphics = new GraphicsManager();
        graphics.setHeadlessMode(true);
        for (boolean high : new boolean[] {false, true, false}) {
            var frame =
                    SpritePresentationRenderer.prepare(
                            graphics,
                            0,
                            0,
                            () -> {
                                graphics.beginSpriteSatCollection();
                                graphics.setCurrentSpriteHighPriority(high);
                                graphics.submitSpriteSatPiece(piece(100, 80, 512, 1));
                                graphics.endSpriteSatCollectionAndReplay();
                            });
            assertEquals(1, frame.tiles().size());
            var tile = frame.tiles().getFirst();
            assertEquals(high ? 0 : 15, tile.occlusionMask());
            assertEquals(0, tile.rowStart());
            assertEquals(8, tile.rowEnd());
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

    @Test
    void ordinaryExplicitMaskIsCapturedAtSubmissionBeforeAmbientStateChangesAndPublication() {
        GraphicsManager graphics = new GraphicsManager();
        graphics.setHeadlessMode(true);
        var frame = SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
            graphics.beginSpriteSatCollection();
            graphics.setCurrentSpriteTileOcclusionPaletteMask(7);
            graphics.submitSpriteSatPiece(piece(100, 80, 512, 1));
            graphics.setCurrentSpriteHighPriority(false);
            graphics.submitSpriteSatPiece(piece(108, 80, 513, 1));
            graphics.endSpriteSatCollectionAndReplay();
        });
        graphics.setCurrentSpriteTileOcclusionPaletteMask(0);
        assertEquals(7, tile(frame, 512).occlusionMask());
        assertEquals(15, tile(frame, 513).occlusionMask());
        var replay = SpritePresentationRenderer.prepare(graphics, 0, 0,
                () -> SpritePresentationRenderer.draw(graphics, frame, 0, 0, layer -> true));
        assertEquals(7, tile(replay, 512).occlusionMask());
        assertEquals(15, tile(replay, 513).occlusionMask());
    }

    @Test
    void twoIndependentlyAttachedCompositeFragmentsKeepTheirMasksClipsAndFollowingOrdinaryTile() {
        GraphicsManager graphics = new GraphicsManager();
        graphics.setHeadlessMode(true);
        var frame = SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
            graphics.beginSpriteSatCollection();
            graphics.setCurrentSpriteHighPriority(false);
            graphics.submitSpriteSatPiece(piece(80, 80, 510, 1));
            graphics.submitSpriteSatGroups(java.util.List.of(SpriteSatEntry.of(
                    100, 80, 2, 1, 512, 0, false, false, false, false)), () -> {
                graphics.submitSpriteSatPiece(piece(100, 80, 512, 1));
                ObjectVisualGraphicsAccess.attachLastVisualMask(graphics, 7);
                ObjectVisualGraphicsAccess.clipLastSatTile(graphics, 2, 6);
                graphics.submitSpriteSatPiece(piece(108, 80, 513, 1));
                ObjectVisualGraphicsAccess.attachLastVisualMask(graphics, 3);
                ObjectVisualGraphicsAccess.clipLastSatTile(graphics, 1, 7);
            });
            assertThrows(IllegalStateException.class,
                    () -> ObjectVisualGraphicsAccess.attachLastVisualMask(graphics, 1));
            assertThrows(IllegalStateException.class,
                    () -> ObjectVisualGraphicsAccess.clipLastSatTile(graphics, 0, 8));
            graphics.submitSpriteSatPiece(piece(128, 80, 514, 1));
            graphics.endSpriteSatCollectionAndReplay();
        });
        assertEquals(4, frame.tiles().size());
        assertEquals(15, tile(frame, 510).occlusionMask());
        assertEquals(7, tile(frame, 512).occlusionMask());
        assertEquals(2, tile(frame, 512).rowStart());
        assertEquals(6, tile(frame, 512).rowEnd());
        assertEquals(3, tile(frame, 513).occlusionMask());
        assertEquals(1, tile(frame, 513).rowStart());
        assertEquals(7, tile(frame, 513).rowEnd());
        assertEquals(15, tile(frame, 514).occlusionMask());
        assertEquals(0, tile(frame, 514).rowStart());
        assertEquals(8, tile(frame, 514).rowEnd());
    }

    @Test
    void emptyActiveFragmentStreamCannotAttachToAnEarlierOrdinaryTile() {
        GraphicsManager graphics = new GraphicsManager();
        graphics.setHeadlessMode(true);
        graphics.beginSpriteSatCollection();
        graphics.submitSpriteSatPiece(piece(80, 80, 510, 1));
        graphics.submitSpriteSatGroups(java.util.List.of(SpriteSatEntry.of(
                100, 80, 1, 1, 512, 0, false, false, false, false)), () -> {
            assertThrows(IllegalStateException.class,
                    () -> ObjectVisualGraphicsAccess.attachLastVisualMask(graphics, 7));
            assertThrows(IllegalStateException.class,
                    () -> ObjectVisualGraphicsAccess.clipLastSatTile(graphics, 0, 8));
        });
        graphics.cancelSpritePresentationCollection();
        assertThrows(IllegalStateException.class,
                () -> ObjectVisualGraphicsAccess.attachLastVisualMask(graphics, 7));
    }

    @Test
    void artLeaseReleaseChangesLifetimeWithoutChangingPublishedMaskOrGhostColour() {
        GraphicsManager graphics = new GraphicsManager();
        graphics.setHeadlessMode(true);
        Object owner = new Object();
        var lease = ObjectVisualGraphicsAccess.acquireArt(graphics, owner, 1);
        int pattern = ObjectVisualGraphicsAccess.artBase(lease, graphics, owner);
        var frame = SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
            graphics.beginSpriteSatCollection();
            graphics.setCurrentSpriteHighPriority(false);
            graphics.beginGhostRenderEffect(.375f, true);
            graphics.submitSpriteSatPiece(piece(100, 80, pattern, 1));
            ObjectVisualGraphicsAccess.attachLastVisualMask(graphics, 7);
            graphics.endGhostRenderEffect();
            graphics.endSpriteSatCollectionAndReplay();
        });
        graphics.setCurrentSpriteTileOcclusionPaletteMask(5);
        ObjectVisualGraphicsAccess.releaseArt(lease, owner);
        assertFalse(ObjectVisualGraphicsAccess.validArt(lease, graphics, owner));
        assertEquals(5, graphics.getCurrentSpriteTileOcclusionPaletteMask());
        assertEquals(7, tile(frame, pattern).occlusionMask());
        assertTrue(tile(frame, pattern).ghost());
        assertTrue(tile(frame, pattern).ghostColour());
        assertEquals(.375f, tile(frame, pattern).ghostAlpha());
        var replay = SpritePresentationRenderer.prepare(graphics, 0, 0,
                () -> SpritePresentationRenderer.draw(graphics, frame, 0, 0, layer -> true));
        assertEquals(frame.tiles(), replay.tiles());
    }

    private static SpritePresentation.Tile tile(SpritePresentation.Frame frame, int pattern) {
        return frame.tiles().stream().filter(tile -> tile.patternId() == pattern).findFirst().orElseThrow();
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
