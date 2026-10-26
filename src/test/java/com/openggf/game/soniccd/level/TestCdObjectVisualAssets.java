package com.openggf.game.soniccd.level;

import org.junit.jupiter.api.Test;

import java.nio.ReadOnlyBufferException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
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


    @Test
    void nativeSharedIncludeKeepsThreeRealRootsAndOneRealScript() throws Exception {
        byte[] maps = springMappings();
        byte[] scripts = springAnimations();
        assertEquals("09fbb070ca8ab3354808535b21668a1c6faeac9fa88fb78628753fe9c8f8377c",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(maps)));
        assertEquals("7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(scripts)));
        int[][] layouts = {{0, 12, 46, 36}, {6, 46, 80, 36}, {80, 86, 200, 46}};
        int[][] relative = {{12, 24, 30}, {40, 52, 58}, {6, 42, 78}};
        int[][] counts = {{2, 1, 3}, {2, 1, 3}, {7, 7, 8}};
        int[] greatestTile = {17, 35, 45};
        for (int family = 0; family < layouts.length; family++) {
            int[] layout = layouts[family];
            var data = CdObjectVisualAssets.decodeIndexed(maps, layout[0], 3, layout[1], layout[2],
                    scripts, 1, layout[3]);
            assertEquals(layout[0], data.mappingTableOffset());
            assertEquals(200, data.mappings().length());
            assertArrayEquals(maps, data.mappings().copy());
            assertArrayEquals(scripts, data.animationTable().copy());
            assertEquals(3, data.mappingFrames().size());
            for (int frame = 0; frame < 3; frame++) {
                var body = data.mappingFrames().get(frame);
                int absolute = layout[0] + relative[family][frame];
                assertEquals(relative[family][frame], body.relativeOffset());
                assertEquals(relative[family][frame], (short) data.mappings().unsignedWord(layout[0] + frame * 2));
                assertEquals(counts[family][frame], body.pieces().size());
                assertEquals(1 + 5 * counts[family][frame], body.bytes().length());
                assertArrayEquals(Arrays.copyOfRange(maps, absolute, absolute + body.bytes().length()),
                        body.bytes().copy());
            }
            assertEquals(greatestTile[family], data.mappingFrames().stream().flatMap(f -> f.pieces().stream())
                    .mapToInt(p -> p.tileOffset() + p.tileCount() - 1).max().orElseThrow());
            assertEquals(1, data.animations().size());
            var script = data.animations().getFirst();
            assertEquals(2, script.relativeOffset());
            assertEquals(0, script.delay());
            assertEquals(List.of(0, 1, 1, 2, 2, 2, 2, 2, 2, 1), script.frameBytes());
            assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC, script.terminal());
            assertEquals(12, script.bytes().length());
        }
        for (int pad : new int[]{23, 57, 199}) assertEquals(0, maps[pad]);
    }

    @Test
    void oldEntriesStillRejectSharedPrefixesAndInventedCombinedIndices() {
        byte[] maps = springMappings();
        byte[] scripts = springAnimations();
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 3, scripts, 1, 46));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(maps, 3, 46));
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(maps, 3, scripts, 1, 46, Map.of(0, 1)));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(
                Arrays.copyOfRange(maps, 6, 80), 3, scripts, 1, 36));
        for (int frames : new int[]{6, 9}) {
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, frames, scripts, 1, 46));
        }
        // The third native table already fits the old strict contract when supplied as its own include.
        assertEquals(0, CdObjectVisualAssets.decode(Arrays.copyOfRange(maps, 80, 200), 3, scripts, 1, 46)
                .mappingTableOffset());
        assertEquals(0, CdObjectVisualAssets.decodeStatic(aliasMaps(), 3, 14).mappingTableOffset());
        assertEquals(0, decodeMaps(aliasMaps()).mappingTableOffset());
    }

    @Test
    void indexedAliasesRetainSignedSourceWordsAndBodiesBeforeTheTable() {
        byte[] maps = {55, 66, 0, 0, 1, -8, 5, 0x18, 10, -4, 77, 88, 99, 44, 55, 66,
                -1, -12, -1, -14, -1, -12};
        var data = CdObjectVisualAssets.decodeIndexed(maps, 16, 3, 2, 10, springAnimations(), 1, 14);
        assertEquals(16, data.mappingTableOffset());
        assertEquals(List.of(-12, -14, -12), data.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertArrayEquals(maps, data.mappings().copy());
        assertEquals(new CdObjectVisualAssets.Piece(-8, 5, 0x180A, -4),
                data.mappingFrames().getFirst().pieces().getFirst());
        assertTrue(data.mappingFrames().get(1).pieces().isEmpty());
        assertArrayEquals(new byte[]{0}, data.mappingFrames().get(1).bytes().copy());
        assertArrayEquals(Arrays.copyOfRange(maps, 4, 10), data.mappingFrames().getFirst().bytes().copy());
        assertArrayEquals(data.mappingFrames().getFirst().bytes().copy(), data.mappingFrames().getLast().bytes().copy());
    }

    @Test
    void indexedBodiesCanStartOddAndOnlyRealEvenAlignmentIsAdmitted() {
        byte[] oddBody = {0, 4, 0, 5, 0, 1, 0, 0, 0, 0, 0, 0};
        var data = CdObjectVisualAssets.decodeIndexed(oddBody, 0, 2, 4, 12, oneFrameAnimation(), 1, 1);
        assertEquals(5, data.mappingFrames().getLast().relativeOffset());
        assertEquals(6, data.mappingFrames().getLast().bytes().length());
        assertEquals(12, data.mappings().length());
        // A final alignment byte may lie outside the consumed-body window, but stays in the raw include.
        assertEquals(12, CdObjectVisualAssets.decodeIndexed(oddBody, 0, 2, 4, 11, oneFrameAnimation(), 1, 1)
                .mappings().length());
        rejectIndexed("Object mapping zero is not an even-alignment byte",
                new byte[]{0, 3, 55, 0, 0}, 0, 1, 3, 5, oneFrameAnimation(), 1, 1);
        rejectIndexed("Unreviewed bytes after object visual body",
                new byte[]{0, 4, 55, 66, 0, 0, 0}, 0, 1, 4, 7, oneFrameAnimation(), 1, 1);
    }

    @Test
    void indexedHeaderAndWindowExtentsRejectOverlapAndIntegerOverflowBeforeReads() {
        for (int[] bounds : new int[][]{
                {-1, 12, 46}, {1, 12, 46}, {Integer.MIN_VALUE, 12, 46}, {Integer.MAX_VALUE - 1, 12, 46},
                {196, 12, 46}, {0, -1, 46}, {0, 12, 12}, {0, 46, 12}, {0, 12, 201},
                {0, 0, 46}, {0, 4, 46}, {0, Integer.MIN_VALUE, 46}, {0, 12, Integer.MAX_VALUE}}) {
            rejectIndexed("Object indexed mapping header/window extent", springMappings(), bounds[0], 3,
                    bounds[1], bounds[2], springAnimations(), 1, 36);
        }
        rejectIndexed("Unreviewed gap before indexed mapping bodies", springMappings(), 6, 3, 45, 80,
                springAnimations(), 1, 36);
        rejectIndexed("Object visual include extent", new byte[6], 0, 3, 6, 7, springAnimations(), 1, 36);
        rejectIndexed("Object visual include extent", new byte[0x8000], 0, 3, 12, 46, springAnimations(), 1, 36);
        for (int frames : new int[]{2, 4}) {
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeIndexed(
                    springMappings(), 0, frames, 12, 46, springAnimations(), 1, 36));
        }
    }

    @Test
    void indexedCountsRemainFiniteBeforeAnyExtentArithmetic() {
        for (int frames : new int[]{Integer.MIN_VALUE, -1, 0, 33, Integer.MAX_VALUE}) {
            rejectIndexed("Object visual counts exceed the bounded native contract", springMappings(), 0, frames,
                    12, 46, springAnimations(), 1, 36);
        }
        for (int count : new int[]{Integer.MIN_VALUE, -1, 0}) {
            rejectIndexed("Animated object visuals require a script", springMappings(), 0, 3, 12, 46,
                    springAnimations(), count, 36);
        }
        for (int count : new int[]{257, Integer.MAX_VALUE}) {
            rejectIndexed("Object visual counts exceed the bounded native contract", springMappings(), 0, 3,
                    12, 46, springAnimations(), count, 36);
        }
        for (int tiles : new int[]{Integer.MIN_VALUE, 0, 2049, Integer.MAX_VALUE}) {
            rejectIndexed("Object visual counts exceed the bounded native contract", springMappings(), 0, 3,
                    12, 46, springAnimations(), 1, tiles);
        }
        // Largest admitted include, with a real signed backward pointer: no int addition or unsigned rebasing.
        byte[] largest = new byte[0x7FFF];
        word(largest, 32764, -32764);
        var data = CdObjectVisualAssets.decodeIndexed(largest, 32764, 1, 0, 1, oneFrameAnimation(), 1, 2048);
        assertEquals(-32764, data.mappingFrames().getFirst().relativeOffset());
        assertTrue(data.mappingFrames().getFirst().pieces().isEmpty());
    }

    @Test
    void indexedSignedPointersCannotReachHeaderGapOtherFamiliesOrWindowEnd() {
        for (int relative : new int[]{0, 5, 39, 74, 75, 0x7FFF, 0x8000, 0xFFFF}) {
            byte[] maps = springMappings();
            word(maps, 6, relative);
            rejectIndexed("Object signed relative pointer is outside its declared mapping body window",
                    maps, 6, 3, 46, 80, springAnimations(), 1, 36);
        }
        // The raw data outside this root's body window remains retained and deliberately unparsed.
        byte[] unrelatedBody = springMappings();
        unrelatedBody[12] = (byte) 0xFF;
        var data = CdObjectVisualAssets.decodeIndexed(unrelatedBody, 6, 3, 46, 80, springAnimations(), 1, 36);
        assertEquals(255, data.mappings().unsignedByte(12));
        assertEquals(2, data.mappingFrames().getFirst().pieces().size());
    }

    @Test
    void indexedCountedBodiesCannotCrossNextDistinctTargetOrWindowEnd() {
        for (int countAt : new int[]{12, 30}) {
            byte[] maps = springMappings();
            maps[countAt]++;
            rejectIndexed("Object mapping crosses its next distinct body or include end", maps, 0, 3,
                    12, 46, springAnimations(), 1, 36);
        }
        byte[] nested = springMappings();
        word(nested, 2, 13);
        rejectIndexed("Object mapping crosses its next distinct body or include end", nested, 0, 3,
                12, 46, springAnimations(), 1, 36);
        rejectIndexed("Object mapping crosses its next distinct body or include end", springMappings(), 6, 3,
                46, 79, springAnimations(), 1, 36);
        byte[] full128 = new byte[2 + 1 + 128 * 5];
        word(full128, 0, 2);
        full128[2] = (byte) 128;
        assertEquals(128, CdObjectVisualAssets.decodeIndexed(full128, 0, 1, 2, full128.length,
                oneFrameAnimation(), 1, 1).mappingFrames().getFirst().pieces().size());
        byte[] full129 = new byte[2 + 1 + 129 * 5];
        word(full129, 0, 2);
        full129[2] = (byte) 129; // The complete body fits: only the native count limit can reject it.
        rejectIndexed("Object mapping crosses its next distinct body or include end", full129, 0, 1,
                2, full129.length, oneFrameAnimation(), 1, 1);
    }

    @Test
    void indexedNativePadsAndPieceBoundsRemainStrictForEveryFamily() {
        int[][] layouts = {{0, 12, 46, 23, 36}, {6, 46, 80, 57, 36}, {80, 86, 200, 199, 46}};
        for (int[] layout : layouts) {
            byte[] maps = springMappings();
            maps[layout[3]] = 1;
            rejectIndexed("Unreviewed bytes after object visual body", maps, layout[0], 3,
                    layout[1], layout[2], springAnimations(), 1, layout[4]);
        }
        byte[] invalidSize = springMappings();
        invalidSize[14] = 0x10;
        rejectIndexed("Not a native five-byte object mapping piece", invalidSize, 0, 3, 12, 46,
                springAnimations(), 1, 36);
        for (int[] layout : new int[][]{{0, 12, 46, 17}, {6, 46, 80, 35}, {80, 86, 200, 45}, {80, 86, 200, 36}}) {
            rejectIndexed("Object mapping reads beyond its associated decoded art source", springMappings(),
                    layout[0], 3, layout[1], layout[2], springAnimations(), 1, layout[3]);
        }
        assertEquals(3, CdObjectVisualAssets.decodeIndexed(springMappings(), 0, 3, 12, 46,
                springAnimations(), 1, 18).mappingFrames().size());
    }

    @Test
    void indexedScriptsKeepTheOriginalSignedIndexFrameAndTerminalChecks() {
        byte[] absentFrame = springAnimations();
        absentFrame[3] = 3;
        rejectIndexed("Object animation selects an absent mapping frame", springMappings(), 6, 3, 46, 80,
                absentFrame, 1, 36);
        byte[] unsupportedCommand = springAnimations();
        unsupportedCommand[13] = (byte) 0xFE;
        rejectIndexed("Object animation command has no reviewed consumer", springMappings(), 6, 3, 46, 80,
                unsupportedCommand, 1, 36);
        for (byte[] scripts : new byte[][]{Arrays.copyOf(springAnimations(), 13), {0, 2, 0, (byte) 0xFC}}) {
            rejectIndexed("Object script lacks frames or a bounded FF/FC terminal", springMappings(), 6, 3,
                    46, 80, scripts, 1, 36);
        }
        byte[] badPointer = springAnimations();
        word(badPointer, 0, 0xFFFF);
        rejectIndexed("Object signed relative pointer is outside its include bodies", springMappings(), 6, 3,
                46, 80, badPointer, 1, 36);
        rejectIndexed("Object signed relative pointer is outside its include bodies", springMappings(), 6, 3,
                46, 80, springAnimations(), 2, 36);
        byte[] scriptPad = Arrays.copyOf(springAnimations(), 15);
        scriptPad[14] = 1;
        rejectIndexed("Unreviewed bytes after object visual body", springMappings(), 6, 3, 46, 80,
                scriptPad, 1, 36);
    }

    @Test
    void indexedStorageKeepsTheCompleteRawIncludeAndDetachedConsumedViews() {
        byte[] maps = springMappings();
        byte[] scripts = springAnimations();
        byte[] expectedMaps = maps.clone();
        byte[] expectedScripts = scripts.clone();
        var data = CdObjectVisualAssets.decodeIndexed(maps, 6, 3, 46, 80, scripts, 1, 36);
        Arrays.fill(maps, (byte) 0);
        Arrays.fill(scripts, (byte) 0);
        assertArrayEquals(expectedMaps, data.mappings().copy());
        assertArrayEquals(expectedScripts, data.animationTable().copy());
        var frame = data.mappingFrames().getFirst();
        byte[] copy = frame.bytes().copy();
        copy[0] = 99;
        assertEquals(2, frame.bytes().unsignedByte(0));
        byte[] wholeCopy = data.mappings().copy();
        wholeCopy[199] = 99;
        assertEquals(0, data.mappings().unsignedByte(199));
        assertArrayEquals(Arrays.copyOfRange(expectedMaps, 46, 57), frame.bytes().copy());
        assertArrayEquals(Arrays.copyOfRange(expectedScripts, 2, 14), data.animations().getFirst().bytes().copy());
        frame.bytes().buffer().position(2);
        data.mappings().buffer().position(80);
        data.animations().getFirst().bytes().buffer().position(2);
        assertEquals(0, frame.bytes().buffer().position());
        assertEquals(0, data.mappings().buffer().position());
        assertEquals(0, data.animations().getFirst().bytes().buffer().position());
        assertThrows(ReadOnlyBufferException.class, () -> data.mappings().buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> frame.bytes().buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> data.animationTable().buffer().put((byte) 0));
        assertThrows(UnsupportedOperationException.class, () -> data.mappingFrames().clear());
        assertThrows(UnsupportedOperationException.class, () -> frame.pieces().clear());
        assertThrows(UnsupportedOperationException.class, () -> data.animations().clear());
        assertThrows(UnsupportedOperationException.class, () -> data.animations().getFirst().frameBytes().clear());
    }

    private static void rejectIndexed(String message, byte[] maps, int tableOffset, int frameCount,
                                      int bodyStart, int bodyEnd, byte[] scripts, int animationCount, int artTileCount) {
        var failure = assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeIndexed(
                maps, tableOffset, frameCount, bodyStart, bodyEnd, scripts, animationCount, artTileCount));
        assertEquals(message, failure.getMessage());
    }
    private static byte[] oneFrameAnimation() { return new byte[]{0, 2, 0, 0, (byte) 0xFC}; }
    private static byte[] springAnimations() { return HexFormat.of().parseHex("00020000010102020202020201fc"); }
    private static byte[] springMappings() {
        // scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
        // MapSpr_Spring1/2/3 (Spring/Data/Mappings.asm:1-82), plus Spring/Main.asm:495-496's real EVEN.
        // Literal source data only: no media read, art, rebasing, stripped headers or synthetic animation.
        return HexFormat.of().parseHex(
                "000c0018001e00280034003a02f80c00"
                + "00f0000c0004f00001000c0000f003e0"
                + "0c0000f0e8060008f8000c000ef002f0"
                + "03001200f0030016f80001f0030012f8"
                + "03f003001218f809001a00f0030020f8"
                + "0006002a004e0708000000f000000001"
                + "f008000002f8f0080003f0f8000006f0"
                + "f8090007f80804000d000708000000f0"
                + "0000000ff008000010f8f0050011f0f8"
                + "0000050000000015f800050016000808"
                + "000000f00000001af00800001bf8e008"
                + "000300e80e001c00f8000028f0f00200"
                + "29f80001002c0000");
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

    @Test void oldOptionalZeroRuleRemainsIndependentOfIndexedEvenAlignment() {
        byte[] maps = {0, 4, 0, 5, 0, 0, 0};
        var old = CdObjectVisualAssets.decodeStatic(maps, 2, 1);
        assertEquals(List.of(4, 5), old.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(0, 0), old.mappingFrames().stream().map(f -> f.pieces().size()).toList());
        // The same real zero at absolute6 is permitted by the OLD API, but is not native EVEN for indexed data.
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeIndexed(
                maps, 0, 2, 4, 7, new byte[]{0, 2, 0, 0, (byte) 0xFC}, 1, 1));
    }

    @Test void indexedPriorBodyWindowMayTouchTheWordIndexWithoutOverlappingIt() {
        byte[] maps = {0, 0, (byte) 0xFF, (byte) 0xFE};
        var visual = CdObjectVisualAssets.decodeIndexed(maps, 2, 1, 0, 2,
                new byte[]{0, 2, 0, 0, (byte) 0xFC}, 1, 1);
        assertEquals(2, visual.mappingTableOffset());
        assertEquals(-2, visual.mappingFrames().getFirst().relativeOffset());
        assertArrayEquals(new byte[]{0}, visual.mappingFrames().getFirst().bytes().copy());
        assertTrue(visual.mappingFrames().getFirst().pieces().isEmpty());
        assertArrayEquals(maps, visual.mappings().copy());
    }
}
