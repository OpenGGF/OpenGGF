package com.openggf.game.soniccd.level;

import org.junit.jupiter.api.Test;

import java.nio.ReadOnlyBufferException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Constructed format inputs only; these do not construct an authenticated object-asset owner. */
class TestCdObjectVisualAssets {
    @Test
    void unscriptedMappingTableRetainsFramesWithoutInventingAnimation() {
        byte[] maps = aliasMaps();
        var data = CdObjectVisualAssets.decodeStatic(maps, 3, 14);
        Arrays.fill(maps, (byte) 0);
        assertEquals(List.of(12, 6, 12), data.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(0x180A, data.mappingFrames().get(1).pieces().getFirst().rawTileWord());
        assertEquals(0, data.animationTable().length());
        assertTrue(data.animations().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> data.animations().add(null));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(
                aliasMaps(), 3, new byte[0], 0, 14), "Animated entry still requires a script");
    }

    @Test
    void unscriptedTablesKeepPointerExtentAndArtBounds() {
        byte[] outsideArt = aliasMaps();
        outsideArt[10] = 11;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(outsideArt, 3, 14));
        byte[] pointerIntoIndex = aliasMaps();
        word(pointerIntoIndex, 0, 2);
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(pointerIntoIndex, 3, 14));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(aliasMaps(), 0, 14));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(aliasMaps(), 3, 0));
    }

    @Test
    void unscriptedFrameSelectionUsesTheFullNativeUnsignedByte() {
        byte[] mappings = new byte[519];
        for (int frame = 0; frame < 255; frame++) word(mappings, frame * 2, 512);
        word(mappings, 255 * 2, 513);
        mappings[513] = 1;
        mappings[514] = -2;
        mappings[517] = 1;
        mappings[518] = 3;
        var data = CdObjectVisualAssets.decodeStatic(mappings, 256, 2);
        assertTrue(data.mappingFrames().get(254).pieces().isEmpty());
        var last = data.mappingFrames().get(255).pieces().getFirst();
        assertEquals(new CdObjectVisualAssets.Piece(-2, 0, 1, 3), last);
        byte[] excessive = new byte[515];
        for (int frame = 0; frame < 257; frame++) word(excessive, frame * 2, 514);
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(excessive, 257, 2));
    }

    @Test
    void declaredUnconsumedMappingBytesStayInSourceButNeverBecomePieces() {
        byte[] maps = {0, 4, 0, 13, 1, 0, 0, 0, 0, 0, 77, 88, 99, 0};
        byte[] scripts = {0, 2, 0, 0, (byte) 0xFF};
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 2, scripts, 1, 1));
        var data = CdObjectVisualAssets.decode(maps, 2, scripts, 1, 1, Map.of(0, 3));
        assertArrayEquals(maps, data.mappings().copy());
        assertEquals(1, data.mappingFrames().getFirst().pieces().size());
        assertEquals(6, data.mappingFrames().getFirst().bytes().length());
        assertTrue(data.mappingFrames().getLast().pieces().isEmpty());
        assertEquals(77, data.mappings().unsignedByte(10));
        assertEquals(99, data.mappings().unsignedByte(12));
    }

    @Test
    void mappingTailDeclarationsMustMatchAnExistingFrameAndItsExactExtent() {
        byte[] maps = {0, 4, 0, 13, 1, 0, 0, 0, 0, 0, 77, 88, 99, 0};
        byte[] scripts = {0, 2, 0, 0, (byte) 0xFF};
        for (var declaration : List.of(Map.of(0, 2), Map.of(-1, 3), Map.of(2, 3), Map.of(0, -1), Map.of(0, 3, 2, 0))) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decode(maps, 2, scripts, 1, 1, declaration));
        }
        byte[] undeclaredTail = Arrays.copyOf(maps, maps.length + 1);
        undeclaredTail[14] = 5;
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(undeclaredTail, 2, scripts, 1, 1, Map.of(0, 3)));
        byte[] outsideArt = maps.clone();
        outsideArt[8] = 1;
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(outsideArt, 2, scripts, 1, 1, Map.of(0, 3)));
    }

    @Test
    void fiveBytePiecesKeepSignedPositionsRawWordsAndNonmonotonicAliases() {
        byte[] maps = aliasMaps();
        byte[] scripts = {0, 4, 0, 4, 5, 1, (byte) 0xFF};
        var data = CdObjectVisualAssets.decode(maps, 3, scripts, 2, 14);
        assertEquals(List.of(12, 6, 12), data.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertTrue(data.mappingFrames().getFirst().pieces().isEmpty());
        var piece = data.mappingFrames().get(1).pieces().getFirst();
        assertEquals(-8, piece.y());
        assertEquals(-4, piece.x());
        assertEquals(5, piece.sizeByte());
        assertEquals(0x180A, piece.rawTileWord());
        assertEquals(10, piece.tileOffset());
        assertEquals(2, piece.widthTiles());
        assertEquals(2, piece.heightTiles());
        assertEquals(4, piece.tileCount());
        assertEquals(4, data.animations().getFirst().relativeOffset());
        assertEquals(data.animations().getFirst().relativeOffset(), data.animations().getLast().relativeOffset());
        assertArrayEquals(data.animations().getFirst().bytes().copy(), data.animations().getLast().bytes().copy());
    }

    @Test
    void objectScriptsRetainFlipBitsAndOnlyReviewedFfFcTerminals() {
        var data = CdObjectVisualAssets.decode(new byte[]{0, 2, 0}, 1,
                new byte[]{0, 4, 0, 8, 5, 0x60, (byte) 0xFF, 0, 14, 0, (byte) 0xFC}, 2, 1);
        assertEquals(List.of(0x60), data.animations().getFirst().frameBytes());
        assertEquals(5, data.animations().getFirst().delay());
        assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF, data.animations().getFirst().terminal());
        assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC, data.animations().getLast().terminal());
        assertEquals(14, data.animations().getLast().delay());
        assertArrayEquals(new byte[]{5, 0x60, (byte) 0xFF, 0}, data.animations().getFirst().bytes().copy());
    }

    @Test
    void byteBodiesMayStartAtOddOffsetsAsInNativeMosquiMappings() {
        // First frame is empty; the second five-byte-piece body starts at byte 5.
        var data = CdObjectVisualAssets.decode(new byte[]{0, 4, 0, 5, 0, 1, 0, 0, 0, 0, 0}, 2,
                new byte[]{0, 2, 0, 1, (byte) 0xFF}, 1, 1);
        assertEquals(5, data.mappingFrames().getLast().relativeOffset());
        assertEquals(1, data.mappingFrames().getLast().pieces().size());
    }

    @Test
    void signedPointersCannotSelectAnIndexOrEscapeTheirInclude() {
        for (int offset : new int[]{0, 2, 5, 13, 0x8000, 0xFFFF}) {
            byte[] bad = aliasMaps();
            word(bad, 0, offset);
            assertThrows(IllegalArgumentException.class, () -> decodeMaps(bad));
        }
        for (int offset : new int[]{0, 1, 5, 0x8000, 0xFFFF}) {
            byte[] scripts = {0, 2, 5, 0, (byte) 0xFF};
            word(scripts, 0, offset);
            assertThrows(IllegalArgumentException.class, () -> decodeScripts(scripts));
        }
    }

    @Test
    void mappingCountCannotReadThroughTheNextDistinctBodyOrBeyondTheEnd() {
        byte[] crossing = aliasMaps();
        crossing[6] = 2;
        assertThrows(IllegalArgumentException.class, () -> decodeMaps(crossing));
        byte[] nested = aliasMaps();
        word(nested, 0, 7);
        assertThrows(IllegalArgumentException.class, () -> decodeMaps(nested));
        assertThrows(IllegalArgumentException.class, () -> decodeMaps(Arrays.copyOf(aliasMaps(), 12)));
        byte[] invalidSize = aliasMaps();
        invalidSize[8] = 0x10;
        assertThrows(IllegalArgumentException.class, () -> decodeMaps(invalidSize));
        byte[] outsideArt = aliasMaps();
        outsideArt[10] = 11; // Four tiles starting at 11 do not fit the 14-tile ring source.
        assertThrows(IllegalArgumentException.class, () -> decodeMaps(outsideArt));
        byte[] tail = Arrays.copyOf(aliasMaps(), 14);
        tail[13] = 1;
        assertThrows(IllegalArgumentException.class, () -> decodeMaps(tail));
    }

    @Test
    void scriptsRejectMissingTerminalsUnreviewedCommandsFramesAndCrossBodyReads() {
        for (byte[] script : new byte[][]{
                {0, 2, 5, 0}, {0, 2, 5, (byte) 0xFC},
                {0, 2, 5, 1, (byte) 0xFF}, {0, 2, 5, (byte) 0x80, (byte) 0xFF},
                {0, 2, 5, 0, (byte) 0xFE, 1}, {0, 2, 5, 0, (byte) 0xFD, 0},
                {0, 2, 5, 0, (byte) 0xFB}, {0, 2, 5, 0, (byte) 0xFA},
                {0, 2, 5, 0, (byte) 0xFF, 1}, {0, 2, 5, 0, (byte) 0xFF, 0, 0}}) {
            assertThrows(IllegalArgumentException.class, () -> decodeScripts(script));
        }
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(
                new byte[]{0, 2, 0}, 1, new byte[]{0, 4, 0, 6, 5, 0, 5, 0, (byte) 0xFF}, 2, 1));
    }

    @Test
    void storageAndNestedListsAreDetachedAndBufferCursorsAreIndependent() {
        byte[] maps = aliasMaps();
        byte[] scripts = {0, 2, 5, 1, (byte) 0xFF};
        var data = CdObjectVisualAssets.decode(maps, 3, scripts, 1, 14);
        Arrays.fill(maps, (byte) 0);
        Arrays.fill(scripts, (byte) 0);
        assertEquals(0x180A, data.mappingFrames().get(1).pieces().getFirst().rawTileWord());
        assertEquals(5, data.animations().getFirst().delay());
        byte[] detached = data.mappings().copy();
        detached[0] = 99;
        assertEquals(0, data.mappings().unsignedByte(0));
        data.animations().getFirst().bytes().buffer().position(2);
        assertEquals(0, data.animations().getFirst().bytes().buffer().position());
        assertThrows(ReadOnlyBufferException.class, () -> data.mappings().buffer().put((byte) 0));
        assertThrows(UnsupportedOperationException.class, () -> data.mappingFrames().clear());
        assertThrows(UnsupportedOperationException.class, () -> data.mappingFrames().get(1).pieces().clear());
        assertThrows(UnsupportedOperationException.class, () -> data.animations().clear());
        assertThrows(UnsupportedOperationException.class, () -> data.animations().getFirst().frameBytes().clear());
    }

    @Test
    void finiteIndexAndArtCapacitiesFailBeforeParsing() {
        for (int count : new int[]{0, 33, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(
                    new byte[]{0, 2, 0}, count, new byte[]{0, 2, 5, 0, (byte) 0xFF}, 1, 1));
        }
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(
                new byte[]{0, 2, 0}, 1, new byte[]{0, 2, 5, 0, (byte) 0xFF}, 257, 1));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(
                new byte[]{0, 2, 0}, 1, new byte[]{0, 2, 5, 0, (byte) 0xFF}, 1, 2049));
    }

    private static byte[] aliasMaps() {
        return new byte[]{0, 12, 0, 6, 0, 12, 1, (byte) 0xF8, 5, 0x18, 10, (byte) 0xFC, 0};
    }
    private static CdObjectVisualAssets decodeMaps(byte[] maps) {
        return CdObjectVisualAssets.decode(maps, 3, new byte[]{0, 2, 5, 1, (byte) 0xFF}, 1, 14);
    }
    private static CdObjectVisualAssets decodeScripts(byte[] scripts) {
        return CdObjectVisualAssets.decode(new byte[]{0, 2, 0}, 1, scripts, 1, 1);
    }
    private static void word(byte[] bytes, int at, int value) {
        bytes[at] = (byte) (value >>> 8);
        bytes[at + 1] = (byte) value;
    }
}
