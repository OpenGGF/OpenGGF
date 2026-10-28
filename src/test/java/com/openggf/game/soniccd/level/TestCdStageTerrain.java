package com.openggf.game.soniccd.level;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.openggf.game.soniccd.level.CdStageTerrain.Plane.*;
import static com.openggf.game.soniccd.level.CdStageTerrain.Shape.*;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed native-format witnesses, not authenticated media, stage execution or pixel parity. */
class TestCdStageTerrain {
    @Test
    void exactPublicSurfaceAndLegacyRecordTypesRemainAvailable() throws Exception {
        assertArrayEquals(new CdStageTerrain.Shape[]{PPZ1_PRESENT, PPZ1_PAST}, CdStageTerrain.Shape.values());
        assertEquals(Set.of("values", "valueOf"), Arrays.stream(CdStageTerrain.Shape.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).map(m -> m.getName()).collect(Collectors.toSet()));
        Set<String> legacy = Set.of("decode(" + "byte[],".repeat(8) + "byte[]):CdR11aTerrain",
                "blockCount():int", "patternCount():int", "chunkCount():int", "blocks():CdAssetBytes",
                "patterns():CdAssetBytes", "chunks():CdAssetBytes", "layout():Layout", "collision():Collision",
                "blockWord(int,int,int):PatternReference", "chunkWord(int,int,int):BlockReference", "pixel(int,int,int):int");
        assertEquals(legacy, publicMethods(CdR11aTerrain.class));
        Set<String> common = new HashSet<>(legacy);
        common.remove("decode(" + "byte[],".repeat(8) + "byte[]):CdR11aTerrain");
        common.add("decode(Shape," + "byte[],".repeat(8) + "byte[]):CdStageTerrain"); common.add("shape():Shape");
        assertEquals(common, publicMethods(CdStageTerrain.class));
        assertEquals(CdR11aTerrain.Layout.class, CdR11aTerrain.class.getMethod("layout").getReturnType());
        assertEquals(CdR11aTerrain.Collision.class, CdR11aTerrain.class.getMethod("collision").getReturnType());
        assertEquals(CdR11aTerrain.PatternReference.class, CdR11aTerrain.class.getMethod("blockWord", int.class, int.class, int.class).getReturnType());
        assertEquals(CdR11aTerrain.BlockReference.class, CdR11aTerrain.class.getMethod("chunkWord", int.class, int.class, int.class).getReturnType());
        var bridge = CdR11aTerrain.class.getDeclaredMethod("stageTerrain");
        assertEquals(CdStageTerrain.class, bridge.getReturnType());
        assertEquals(0, bridge.getModifiers() & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE | Modifier.STATIC));
        assertTrue(Modifier.isFinal(CdStageTerrain.class.getModifiers()));
        assertTrue(Arrays.stream(CdStageTerrain.class.getDeclaredConstructors()).allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void bothShapesUseExactFiniteExtentsAndIndependentCounts() {
        var present = new Fixture(PPZ1_PRESENT).decode(); var past = new Fixture(PPZ1_PAST).decode();
        assertSame(PPZ1_PRESENT, present.shape()); assertSame(PPZ1_PAST, past.shape());
        assertEquals(804, present.blockCount()); assertEquals(800, present.patternCount()); assertEquals(121, present.chunkCount());
        assertEquals(660, past.blockCount()); assertEquals(746, past.patternCount()); assertEquals(106, past.chunkCount());
        assertEquals(6432, present.blocks().length()); assertEquals(25600, present.patterns().length()); assertEquals(61952, present.chunks().length());
        assertEquals(5280, past.blocks().length()); assertEquals(23872, past.patterns().length()); assertEquals(54272, past.chunks().length());
        assertEquals(900, present.collision().ids().length()); assertEquals(800, past.collision().ids().length());
        for (var terrain : new CdStageTerrain[]{present, past}) {
            assertEquals(256, terrain.collision().angles().length()); assertEquals(4096, terrain.collision().heights().length());
            assertEquals(4096, terrain.collision().widths().length()); assertEquals(0x800, terrain.layout().ram().length());
        }
    }

    @Test
    void bothLayoutsPackNativeRowsIntoOneClearedRam() {
        for (var shape : CdStageTerrain.Shape.values()) {
            var f = new Fixture(shape); f.fg[2] = (byte) 0x81; f.bg[2] = (byte) 0x82; f.fg[44] = 2;
            f.fg[f.fg.length - 1] = (byte) (0x80 | f.chunkCount()); f.bg[f.bg.length - 1] = 1;
            byte[] expected = new byte[0x800]; expected[0] = (byte) 0x81; expected[0x40] = (byte) 0x82; expected[0x80] = 2;
            expected[shape == PPZ1_PRESENT ? 0x3A9 : 0x1A9] = (byte) (0x80 | f.chunkCount());
            expected[shape == PPZ1_PRESENT ? 0x3C5 : 0x0C9] = 1;
            var t = f.decode(); assertArrayEquals(expected, t.layout().ram().copy());
            assertEquals(42, t.layout().columns(FOREGROUND)); assertEquals(shape == PPZ1_PRESENT ? 6 : 10, t.layout().columns(BACKGROUND));
            assertEquals(shape == PPZ1_PRESENT ? 8 : 4, t.layout().rows(FOREGROUND));
            assertEquals(shape == PPZ1_PRESENT ? 8 : 2, t.layout().rows(BACKGROUND));
            assertEquals(0, t.layout().nativeCell(FOREGROUND, 63, 15).rawByte());
            assertEquals(0, t.layout().nativeCell(BACKGROUND, 63, 15).rawByte());
        }
    }

    @Test
    void pastRetainsRawFlagsLastChunkAndBothCoordinateDomains() {
        var f = new Fixture(PPZ1_PAST); f.fg[2] = (byte) 0xEA;
        word(f.blocks, f.blocks.length - 2, 0xFAE9); f.patterns[745 * 32] = (byte) 0xA3;
        word(f.chunks, f.chunks.length - 2, 0x7A93); f.ids[659] = (byte) 255;
        f.angles[255] = (byte) 0xE1; f.heights[255 * 16] = (byte) 0xF0; f.widths[255 * 16 + 15] = (byte) 0xFF;
        var t = f.decode(); var block = t.blockWord(659, 1, 1); var chunk = t.chunkWord(106, 15, 15);
        assertEquals(0xFAE9, block.rawWord()); assertEquals(745, block.patternIndex()); assertEquals(3, block.paletteLine());
        assertTrue(block.flipX()); assertTrue(block.flipY()); assertTrue(block.priority());
        assertEquals(10, t.pixel(745, 0, 0)); assertEquals(3, t.pixel(745, 1, 0));
        assertEquals(0x7A93, chunk.rawWord()); assertEquals(659, chunk.renderBlockIndex()); assertEquals(659, chunk.collisionBlockIndex());
        assertTrue(chunk.flipX()); assertTrue(chunk.flipY()); assertEquals(3, chunk.solidity());
        assertEquals(106, t.layout().cell(FOREGROUND, 0, 0).chunkId()); assertTrue(t.layout().cell(FOREGROUND, 0, 0).loopFlag());
        assertEquals(255, t.collision().idForBlock(659)); assertEquals(0xE1, t.collision().angle(255));
        assertEquals(-16, t.collision().height(255, 0)); assertEquals(-1, t.collision().width(255, 15));
    }

    @Test
    void nativeChunkIdsRemainOneBasedAndZeroLayoutIsBlank() {
        for (var shape : CdStageTerrain.Shape.values()) {
            var f = new Fixture(shape); f.fg[2] = (byte) 0x80; var t = f.decode();
            assertTrue(t.layout().cell(FOREGROUND, 0, 0).blank()); assertTrue(t.layout().cell(FOREGROUND, 0, 0).loopFlag());
            assertEquals(0, t.chunkWord(1, 0, 0).rawWord()); assertEquals(0, t.chunkWord(f.chunkCount(), 15, 15).rawWord());
            String message = "Chunk ID must be 1.." + f.chunkCount() + "; 0 is a blank layout cell";
            assertEquals(message, assertThrows(IndexOutOfBoundsException.class, () -> t.chunkWord(0, 0, 0)).getMessage());
            assertEquals(message, assertThrows(IndexOutOfBoundsException.class, () -> t.chunkWord(f.chunkCount() + 1, 0, 0)).getMessage());
        }
    }

    @Test
    void drawingAndCollisionBlockMasksRemainDistinct() {
        for (var shape : CdStageTerrain.Shape.values()) {
            var f = new Fixture(shape); word(f.chunks, f.chunks.length - 512, 0x0400);
            var raw = f.decode().chunkWord(f.chunkCount(), 0, 0);
            assertEquals(0, raw.renderBlockIndex()); assertEquals(1024, raw.collisionBlockIndex());
            f.fg[2] = (byte) f.chunkCount();
            assertEquals("Referenced chunk requires unloaded block/collision data", assertThrows(IllegalArgumentException.class, f::decode).getMessage());
        }
    }

    @Test
    void allBlockPatternWordsAreCheckedEvenForUnreachedLayouts() {
        for (var shape : CdStageTerrain.Shape.values()) {
            var f = new Fixture(shape); word(f.blocks, f.blocks.length - 2, f.patternCount());
            assertEquals("Block references an unloaded level pattern", assertThrows(IllegalArgumentException.class, f::decode).getMessage());
            word(f.blocks, f.blocks.length - 2, f.patternCount() - 1);
            assertEquals(f.patternCount() - 1, f.decode().blockWord(f.blockCount() - 1, 1, 1).patternIndex());
        }
    }

    @Test
    void onlyReferencedChunksDemandBlockAndCollisionResidency() {
        for (var shape : CdStageTerrain.Shape.values()) {
            var f = new Fixture(shape); word(f.chunks, f.chunks.length - 512, 1023);
            assertEquals(1023, f.decode().chunkWord(f.chunkCount(), 0, 0).renderBlockIndex());
            f.bg[2] = (byte) f.chunkCount();
            assertThrows(IllegalArgumentException.class, f::decode);
            f.bg[2] = (byte) (f.chunkCount() + 1);
            assertEquals("Layout chunk exceeds supplied extent", assertThrows(IllegalArgumentException.class, f::decode).getMessage());
        }
    }

    @Test
    void layoutBoundsRejectBeforeNativeClearedCellAccess() {
        for (var shape : CdStageTerrain.Shape.values()) {
            var t = new Fixture(shape).decode(); var l = t.layout();
            assertThrows(IndexOutOfBoundsException.class, () -> l.cell(FOREGROUND, 42, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> l.cell(BACKGROUND, l.columns(BACKGROUND), 0));
            assertThrows(IndexOutOfBoundsException.class, () -> l.cell(FOREGROUND, 0, l.rows(FOREGROUND)));
            assertEquals(0, l.nativeCell(FOREGROUND, 42, 0).rawByte());
            assertEquals(0, l.nativeCell(BACKGROUND, 0, l.rows(BACKGROUND)).rawByte());
            assertThrows(IndexOutOfBoundsException.class, () -> l.nativeCell(FOREGROUND, 64, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> l.nativeCell(BACKGROUND, 0, 16));
            assertThrows(IndexOutOfBoundsException.class, () -> l.nativeCell(BACKGROUND, -1, 0));
        }
    }

    @Test
    void allNineInputArraysAndReturnedCopiesRemainIsolated() {
        for (var shape : CdStageTerrain.Shape.values()) {
            var f = new Fixture(shape); var t = f.decode();
            for (byte[] input : f.arrays()) Arrays.fill(input, (byte) 0xFF);
            assertEquals(0, t.blockWord(0, 0, 0).rawWord()); assertEquals(0, t.pixel(0, 0, 0));
            assertEquals(0, t.chunkWord(1, 0, 0).rawWord()); assertEquals(0, t.layout().cell(FOREGROUND, 0, 0).rawByte());
            assertEquals(0, t.collision().idForBlock(0)); assertEquals(0, t.collision().angle(0));
            assertEquals(0, t.collision().height(0, 0)); assertEquals(0, t.collision().width(0, 0));
            for (CdAssetBytes value : new CdAssetBytes[]{t.blocks(), t.patterns(), t.chunks(), t.layout().ram(),
                    t.collision().ids(), t.collision().angles(), t.collision().heights(), t.collision().widths()}) {
                byte[] copy = value.copy(); Arrays.fill(copy, (byte) 0xFF); assertEquals(0, value.unsignedByte(0));
            }
        }
    }

    @Test
    void collisionSamplesStaySignedWhileAnglesAndIdsStayUnsigned() {
        for (var shape : CdStageTerrain.Shape.values()) {
            var f = new Fixture(shape); f.ids[f.ids.length - 1] = (byte) 255; f.angles[255] = (byte) 255;
            f.heights[255 * 16] = (byte) 0x80; f.heights[255 * 16 + 15] = 0x7F;
            f.widths[255 * 16] = (byte) 0xFF; f.widths[255 * 16 + 15] = (byte) 0x80;
            var c = f.decode().collision();
            assertEquals(255, c.idForBlock(f.ids.length - 1)); assertEquals(255, c.angle(255));
            assertEquals(-128, c.height(255, 0)); assertEquals(127, c.height(255, 15));
            assertEquals(-1, c.width(255, 0)); assertEquals(-128, c.width(255, 15));
        }
    }

    @Test
    void rawValueRecordsRejectHostWidthsButRetainAllNativeBits() {
        for (int value : new int[]{-1, 0x10000, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new CdStageTerrain.PatternReference(value));
            assertThrows(IllegalArgumentException.class, () -> new CdStageTerrain.BlockReference(value));
            assertThrows(IllegalArgumentException.class, () -> new CdR11aTerrain.PatternReference(value));
            assertThrows(IllegalArgumentException.class, () -> new CdR11aTerrain.BlockReference(value));
        }
        for (int value : new int[]{-1, 256, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new CdStageTerrain.LayoutCell(value));
            assertThrows(IllegalArgumentException.class, () -> new CdR11aTerrain.LayoutCell(value));
        }
        var p = new CdStageTerrain.PatternReference(0xFFFF); assertEquals(2047, p.patternIndex());
        assertTrue(p.flipX()); assertTrue(p.flipY()); assertTrue(p.priority()); assertEquals(3, p.paletteLine());
        var b = new CdStageTerrain.BlockReference(0xFFFF); assertEquals(1023, b.renderBlockIndex()); assertEquals(2047, b.collisionBlockIndex());
        assertTrue(b.flipX()); assertTrue(b.flipY()); assertEquals(3, b.solidity());
        var l = new CdStageTerrain.LayoutCell(0xFF); assertEquals(127, l.chunkId()); assertTrue(l.loopFlag()); assertFalse(l.blank());
    }

    @Test
    void everyExtentAndDimensionRejectsTruncationAndOtherStageShape() {
        for (var shape : CdStageTerrain.Shape.values()) for (int field = 0; field < 9; field++) for (int delta : new int[]{-1, 1}) {
            var f = new Fixture(shape); byte[] input = f.arrays()[field]; f.set(field, Arrays.copyOf(input, input.length + delta));
            assertThrows(IllegalArgumentException.class, f::decode);
        }
        var present = new Fixture(PPZ1_PRESENT); var past = new Fixture(PPZ1_PAST);
        assertThrows(IllegalArgumentException.class, () -> decode(PPZ1_PAST, present));
        assertThrows(IllegalArgumentException.class, () -> decode(PPZ1_PRESENT, past));
    }

    @Test
    void legacyConstructorRetainsExactFailurePrecedenceAndMessages() {
        var f = new Fixture(PPZ1_PRESENT); f.blocks = new byte[6431]; f.patterns = new byte[25599]; f.chunks = new byte[61951];
        f.fg[0] = 42; f.bg = null; f.ids = new byte[899]; f.angles = new byte[255]; f.heights = new byte[4095]; f.widths = new byte[4095];
        assertEquals("Wrong R11A decoded blocks extent", assertThrows(IllegalArgumentException.class, f::legacy).getMessage()); f.blocks = new byte[6432];
        assertEquals("Wrong R11A decoded level patterns extent", assertThrows(IllegalArgumentException.class, f::legacy).getMessage()); f.patterns = new byte[25600];
        assertEquals("Wrong R11A raw chunks extent", assertThrows(IllegalArgumentException.class, f::legacy).getMessage()); f.chunks = new byte[61952];
        assertEquals("Unsupported R11A layout dimensions or extent", assertThrows(IllegalArgumentException.class, f::legacy).getMessage()); f.fg[0] = 41;
        assertThrows(NullPointerException.class, f::legacy); f.bg = new byte[50]; f.bg[0] = 5; f.bg[1] = 7;
        assertEquals("Wrong R11A collision IDs extent", assertThrows(IllegalArgumentException.class, f::legacy).getMessage()); f.ids = new byte[900];
        assertEquals("Wrong R11A collision angles extent", assertThrows(IllegalArgumentException.class, f::legacy).getMessage()); f.angles = new byte[256];
        assertEquals("Wrong R11A height samples extent", assertThrows(IllegalArgumentException.class, f::legacy).getMessage()); f.heights = new byte[4096];
        assertEquals("Wrong R11A width samples extent", assertThrows(IllegalArgumentException.class, f::legacy).getMessage()); f.widths = new byte[4096];
        assertEquals(804, f.legacy().blockCount());
    }

    @Test
    void legacyPublicDataIdentitiesRemainStableAndBridgeRetainsOneModel() {
        var t = new Fixture(PPZ1_PRESENT).legacy(); var model = t.stageTerrain();
        assertSame(model, t.stageTerrain()); assertSame(PPZ1_PRESENT, model.shape());
        assertSame(t.blocks(), t.blocks()); assertSame(t.patterns(), t.patterns()); assertSame(t.chunks(), t.chunks());
        assertSame(t.layout(), t.layout()); assertSame(t.collision(), t.collision());
        assertSame(t.blocks(), model.blocks()); assertSame(t.patterns(), model.patterns()); assertSame(t.chunks(), model.chunks());
        assertSame(t.layout().ram(), model.layout().ram()); assertSame(t.collision().ids(), model.collision().ids());
        assertSame(t.collision().angles(), model.collision().angles()); assertSame(t.collision().heights(), model.collision().heights());
        assertSame(t.collision().widths(), model.collision().widths());
    }

    @Test
    void genericAndLegacyPresentYieldIdenticalImagesAndLiteralReferences() {
        var f = new Fixture(PPZ1_PRESENT); f.fg[2] = 1; f.bg[2] = (byte) 0x81;
        word(f.blocks, 0, 0xFB1F); f.patterns[799 * 32] = (byte) 0xA3; word(f.chunks, 0, 0x7B23);
        var common = f.decode(); var legacy = f.legacy();
        assertArrayEquals(common.blocks().copy(), legacy.blocks().copy()); assertArrayEquals(common.patterns().copy(), legacy.patterns().copy());
        assertArrayEquals(common.chunks().copy(), legacy.chunks().copy()); assertArrayEquals(common.layout().ram().copy(), legacy.layout().ram().copy());
        assertEquals(0xFB1F, legacy.blockWord(0, 0, 0).rawWord()); assertEquals(0xFB1F, common.blockWord(0, 0, 0).rawWord());
        assertEquals(0x7B23, legacy.chunkWord(1, 0, 0).rawWord()); assertEquals(0x7B23, common.chunkWord(1, 0, 0).rawWord());
        assertEquals(10, legacy.pixel(799, 0, 0)); assertEquals(3, common.pixel(799, 1, 0));
        assertEquals(121, legacy.chunkCount()); assertEquals(800, common.patternCount());
    }

    @Test
    void strictLegacyEntryRefusesPastExtentsInsteadOfRelabellingPresent() {
        var past = new Fixture(PPZ1_PAST);
        assertEquals("Wrong R11A decoded blocks extent", assertThrows(IllegalArgumentException.class, past::legacy).getMessage());
        assertEquals(660, past.decode().blockCount());
    }

    @Test
    void dimensionMetadataDoesNotInferRowsOrAcceptRepeatedPadding() {
        for (var shape : CdStageTerrain.Shape.values()) {
            var f = new Fixture(shape); f.fg[1] = (byte) 255;
            assertThrows(IllegalArgumentException.class, f::decode);
            f = new Fixture(shape); f.bg[0] = (byte) 255;
            assertThrows(IllegalArgumentException.class, f::decode);
            f = new Fixture(shape); f.bg[1] = (byte) (shape == PPZ1_PRESENT ? 1 : 7);
            assertThrows(IllegalArgumentException.class, f::decode);
            f = new Fixture(shape); f.fg[2] = 1; var t = f.decode();
            assertEquals(1, t.layout().nativeCell(FOREGROUND, 0, 0).rawByte());
            assertEquals(0, t.layout().nativeCell(FOREGROUND, 0, 1).rawByte());
            assertEquals(0, t.layout().nativeCell(FOREGROUND, 0, t.layout().rows(FOREGROUND)).rawByte());
        }
    }

    @Test
    void publicReadersRetainCoordinateFailureOrder() {
        var t = new Fixture(PPZ1_PAST).decode(); var legacy = new Fixture(PPZ1_PRESENT).legacy();
        assertIndexValue(-9, () -> t.blockWord(-9, -7, -5)); assertIndexValue(-7, () -> t.blockWord(0, -7, -5));
        assertIndexValue(-5, () -> t.blockWord(0, 0, -5)); assertIndexValue(-9, () -> legacy.blockWord(-9, -7, -5));
        assertEquals("Chunk ID must be 1..106; 0 is a blank layout cell", assertThrows(IndexOutOfBoundsException.class, () -> t.chunkWord(0, -7, -5)).getMessage());
        assertEquals("Chunk ID must be 1..121; 0 is a blank layout cell", assertThrows(IndexOutOfBoundsException.class, () -> legacy.chunkWord(0, -7, -5)).getMessage());
        assertIndexValue(-7, () -> t.layout().cell(FOREGROUND, -7, -5)); assertIndexValue(-7, () -> t.layout().nativeCell(BACKGROUND, -7, -5));
        assertIndexValue(-9, () -> t.collision().height(-9, -7)); assertIndexValue(-7, () -> t.collision().width(0, -7));
        assertIndexValue(-9, () -> t.pixel(-9, -7, -5)); assertIndexValue(-7, () -> t.pixel(0, -7, -5));
    }

    @Test
    void nullSourcesAndPlanesRejectWithoutAddedFallbacks() {
        assertThrows(NullPointerException.class, () -> CdStageTerrain.decode(null, null, null, null, null, null, null, null, null, null));
        for (var shape : CdStageTerrain.Shape.values()) {
            for (int at = 0; at < 9; at++) {
                var f = new Fixture(shape); f.set(at, null); assertThrows(NullPointerException.class, f::decode);
                if (shape == PPZ1_PRESENT) assertThrows(NullPointerException.class, f::legacy);
            }
            var l = new Fixture(shape).decode().layout();
            assertThrows(NullPointerException.class, () -> l.columns(null)); assertThrows(NullPointerException.class, () -> l.rows(null));
            assertThrows(NullPointerException.class, () -> l.cell(null, 0, 0)); assertThrows(NullPointerException.class, () -> l.nativeCell(null, 0, 0));
        }
    }

    private static Set<String> publicMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic())
                .map(m -> m.getName() + "(" + Arrays.stream(m.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(",")) + "):" + m.getReturnType().getSimpleName())
                .collect(Collectors.toSet());
    }
    private static void assertIndexValue(int value, org.junit.jupiter.api.function.Executable action) {
        assertTrue(assertThrows(IndexOutOfBoundsException.class, action).getMessage().contains(Integer.toString(value)));
    }
    private static void word(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }
    private static CdStageTerrain decode(CdStageTerrain.Shape shape, Fixture f) {
        return CdStageTerrain.decode(shape, f.blocks, f.patterns, f.chunks, f.fg, f.bg, f.ids, f.angles, f.heights, f.widths);
    }
    private static final class Fixture {
        private final CdStageTerrain.Shape shape;
        private byte[] blocks, patterns, chunks, fg, bg, ids, angles, heights, widths;
        Fixture(CdStageTerrain.Shape shape) {
            this.shape = shape; boolean present = shape == PPZ1_PRESENT;
            blocks = new byte[present ? 6432 : 5280]; patterns = new byte[present ? 25600 : 23872];
            chunks = new byte[present ? 61952 : 54272]; fg = new byte[present ? 338 : 170]; bg = new byte[present ? 50 : 22];
            ids = new byte[present ? 900 : 800]; angles = new byte[256]; heights = new byte[4096]; widths = new byte[4096];
            fg[0] = 41; fg[1] = (byte) (present ? 7 : 3); bg[0] = (byte) (present ? 5 : 9); bg[1] = (byte) (present ? 7 : 1);
        }
        int blockCount() { return shape == PPZ1_PRESENT ? 804 : 660; }
        int patternCount() { return shape == PPZ1_PRESENT ? 800 : 746; }
        int chunkCount() { return shape == PPZ1_PRESENT ? 121 : 106; }
        byte[][] arrays() { return new byte[][]{blocks, patterns, chunks, fg, bg, ids, angles, heights, widths}; }
        void set(int at, byte[] value) {
            switch (at) { case 0 -> blocks = value; case 1 -> patterns = value; case 2 -> chunks = value;
                case 3 -> fg = value; case 4 -> bg = value; case 5 -> ids = value; case 6 -> angles = value;
                case 7 -> heights = value; case 8 -> widths = value; default -> throw new IllegalArgumentException(); }
        }
        CdStageTerrain decode() { return TestCdStageTerrain.decode(shape, this); }
        CdR11aTerrain legacy() { return CdR11aTerrain.decode(blocks, patterns, chunks, fg, bg, ids, angles, heights, widths); }
    }
}
