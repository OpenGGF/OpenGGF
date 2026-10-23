package com.openggf.graphics;

import com.openggf.level.render.SpritePieceRenderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestSpritePiece {
    private record Tile(int id, boolean h, boolean v, int palette, int x, int y) { }

    @Test
    void twoByThreeExpansionKeepsColumnMajorArtAndSourceOrderForEveryFlipAndClip() {
        for (boolean h : List.of(false, true)) {
            for (boolean v : List.of(false, true)) {
                var legacy = new SpritePieceRenderer.PreparedPiece(100, 200, 2, 3, 0x20005, 0x7C0, 14,
                        h, v, true, false, SpriteMaskReplayRole.PRE_MASK_FRONT, 0, 2, 0, 3, "geometry");
                var piece = new SpritePiece(100, 200, 2, 3, 0x20005, 0x7C0, 14,
                        h, v, true, false, SpriteMaskReplayRole.PRE_MASK_FRONT, 0, 2, 0, 3, "geometry");
                int left = h ? 108 : 100, right = h ? 100 : 108;
                int top = v ? 216 : 200, bottom = v ? 200 : 216;
                var expected = List.of(
                        new Tile(0x20005, h, v, 14, left, top),
                        new Tile(0x20008, h, v, 14, right, top),
                        new Tile(0x20006, h, v, 14, left, 208),
                        new Tile(0x20009, h, v, 14, right, 208),
                        new Tile(0x20007, h, v, 14, left, bottom),
                        new Tile(0x2000A, h, v, 14, right, bottom));
                assertEquals(expected, tiles(piece));
                var direct = new ArrayList<Tile>();
                SpritePieceRenderer.renderPreparedPiece(legacy,
                        (id, hf, vf, p, x, y) -> direct.add(new Tile(id, hf, vf, p, x, y)));
                assertEquals(expected, direct, "legacy direct rendering uses the same geometry");

                var clipped = List.of(new Tile(0x20009, h, v, 14, right, 208),
                        new Tile(0x2000A, h, v, 14, right, bottom));
                assertEquals(clipped, tiles(piece.clipRect(1, 1, 1, 2)));
                direct.clear();
                SpritePieceRenderer.renderPreparedPiece(legacy.clipRect(1, 1, 1, 2),
                        (id, hf, vf, p, x, y) -> direct.add(new Tile(id, hf, vf, p, x, y)));
                assertEquals(clipped, direct);
                assertEquals(0x7C0, piece.rawTileWordLow11(), "raw marker identity is independent of relocated art");
            }
        }
    }

    @Test
    void emptyDimensionsAndSourceWindowsEmitNothing() {
        var full = new SpritePiece(1, 2, 2, 3, 4, 5, 6, false, false, false, false,
                null, 0, 2, 0, 3, null);
        assertTrue(tiles(full.clipRect(2, 0, 0, 3)).isEmpty());
        assertTrue(tiles(full.clipRect(0, 2, 3, 0)).isEmpty());
        assertTrue(tiles(new SpritePiece(1, 2, 0, 0, 4, 5, 6, true, true, false, false,
                null, 0, 0, 0, 0, null)).isEmpty());
        SpritePieceRenderer.renderPreparedPiece(null, (id, h, v, p, x, y) -> fail("null input"));
        SpritePieceRenderer.renderPreparedPiece(legacy(), null);
    }

    @Test
    void legacyConstructorsAndEveryCopyHelperKeepThePinnedShapeAndNullSemantics() {
        var original = legacy();
        assertEquals(116, original.endXExclusive());
        assertEquals(224, original.endYExclusive());
        assertEquals(new SpritePieceRenderer.PreparedPiece(100, 200, 2, 3, 0x20005, 0x7C0, 14,
                true, false, true, false, SpriteMaskReplayRole.NORMAL, 0, 2, 1, 2, null),
                new SpritePieceRenderer.PreparedPiece(100, 200, 2, 3, 0x20005, 0x7C0, 14,
                        true, false, true, false, 1, 2));
        assertEquals(new SpritePieceRenderer.PreparedPiece(100, 200, 2, 3, 0x20005, 0x7C0, 14,
                        true, false, false, true, null, 1, 1, 1, 2, null),
                original.clipRect(1, 1, 0, 3).clipRows(1, 2).withMaskReplayRole(null)
                        .withPriorityFlags(false, true).withDebugSource(null));
        assertEquals("legacy", original.debugSource(), "helpers leave the snapshot unchanged");
    }

    @Test
    void validationIsSharedWithoutTighteningLegacyInputs() {
        int[][] invalid = {{-1, 3, 0, 0, 0, 3}, {2, -1, 0, 2, 0, 0},
                {2, 3, -1, 1, 0, 3}, {2, 3, 0, -1, 0, 3}, {2, 3, 1, 2, 0, 3},
                {2, 3, 0, 2, -1, 1}, {2, 3, 0, 2, 0, -1}, {2, 3, 0, 2, 2, 2}};
        for (int[] w : invalid) {
            var canonical = assertThrows(IllegalArgumentException.class, () ->
                    new SpritePiece(0, 0, w[0], w[1], 0, 0, 0, false, false, false, false,
                            null, w[2], w[3], w[4], w[5], null));
            var compatibility = assertThrows(IllegalArgumentException.class, () ->
                    new SpritePieceRenderer.PreparedPiece(0, 0, w[0], w[1], 0, 0, 0, false, false, false, false,
                            null, w[2], w[3], w[4], w[5], null));
            assertEquals(canonical.getMessage(), compatibility.getMessage());
        }
    }

    @Test
    void envelopeCopiesShareGeometryAndKeepGhostSubjectAndExactScanlines() {
        var piece = new SpritePiece(10, 20, 2, 3, 0x20005, 0x7C0, 14, true, false, false, false,
                SpriteMaskReplayRole.NORMAL, 0, 2, 0, 3, "source");
        var subject = new SpritePresentation.Subject("player", SpritePresentation.Part.BODY, false, 100);
        var ghost = new SpriteSatEntry.Ghost(0.375f, true);
        var entry = new SpriteSatEntry(3, piece).withGhost(ghost).withPresentationSubject(subject)
                .withVisibleScanlines(23, 39);
        assertSame(piece, entry.piece());
        var clipped = entry.clipRect(1, 1, 0, 3).clipRows(1, 2)
                .withMaskReplayRole(SpriteMaskReplayRole.PRE_MASK_FRONT);
        assertEquals(piece.clipRect(1, 1, 1, 2).withMaskReplayRole(SpriteMaskReplayRole.PRE_MASK_FRONT), clipped.piece());
        assertSame(subject, clipped.presentationSubject());
        assertSame(ghost, clipped.ghost());
        assertEquals(3, clipped.priorityBucket());
        assertEquals(23, clipped.visibleTopY());
        assertEquals(39, clipped.visibleBottomY());
        assertEquals(3, clipped.visibleTileRowStart(20));
        assertEquals(7, clipped.visibleTileRowEnd(32));
        var unbounded = new SpriteSatEntry(0, piece);
        assertEquals(0, unbounded.visibleTileRowStart(Integer.MAX_VALUE));
        assertEquals(8, unbounded.visibleTileRowEnd(Integer.MIN_VALUE));
        assertEquals(26, piece.endXExclusive());
        assertEquals(44, piece.endYExclusive());
        assertEquals(piece, piece.withDebugSource("other").withDebugSource("source"));
        for (boolean mapping : List.of(false, true)) for (boolean global : List.of(false, true))
            assertEquals(mapping || global, piece.withPriorityFlags(mapping, global).effectiveHighPriority());
    }

    private static SpritePieceRenderer.PreparedPiece legacy() {
        return new SpritePieceRenderer.PreparedPiece(100, 200, 2, 3, 0x20005, 0x7C0, 14,
                true, false, true, false, SpriteMaskReplayRole.PRE_MASK_FRONT, 0, 2, 0, 3, "legacy");
    }

    private static List<Tile> tiles(SpritePiece piece) {
        var result = new ArrayList<Tile>();
        piece.forEachTile((id, h, v, p, x, y) -> result.add(new Tile(id, h, v, p, x, y)));
        return result;
    }
}
