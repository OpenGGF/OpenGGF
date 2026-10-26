package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aProfile;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

import static com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests-first SOURCE FREEZE; ALL JAVA UNRUN. Native 1ecc5f3a, Java 17cc0ebe.
 * Constructed bytes below are format fixtures, never a media authentication claim.
 * Oracles use explicit raw-word literals and independent world-coordinate tile images.
 */
class TestCdR11aBackgroundPlane {
    private static final FlagWords FLAGS = new FlagWords(0x005A, 0xA55A, 0x5AA5);

    @Test
    void explicitSeedIsFullNative64By32WordsWithoutImplicitClear() {
        byte[] bytes = seed();
        State state = new State(CdAssetBytes.copyOf(bytes));
        assertEquals(64, WIDTH_WORDS);
        assertEquals(32, HEIGHT_WORDS);
        assertEquals(4096, NAME_TABLE_BYTES);
        assertEquals(0xE000, VRAM_BASE);
        assertArrayEquals(bytes, state.nameTable().copy());
        assertEquals(0x8000, state.word(0, 0));
        assertEquals(0x87FF, state.word(63, 31));
        assertThrows(IndexOutOfBoundsException.class, () -> state.word(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> state.word(64, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> state.word(0, 32));
    }

    @Test
    void initialSixteenStaticRowsUseNonzeroAliasedBg1PointerAndCoverEntireRam() {
        Fixture f = new Fixture();
        var result = f.owner().initialize(state(), live(0x1234, 0), FLAGS);
        assertEquals(16, result.sections().size());
        assertEquals(512, result.blocks().size());
        assertEquals(128, result.sections().getFirst().tableIndex());
        assertEquals(15, result.sections().getLast().tableIndex());
        assertEquals(FLAGS, result.flags());
        byte[] expected = new byte[4096];
        for (int ty = 0; ty < 32; ty++) {
            for (int tx = 0; tx < 64; tx++) { word(expected, (ty * 64 + tx) * 2, gridTile(tx * 8, ty * 8)); }
        }
        assertArrayEquals(expected, result.state().nameTable().copy());
        var first = result.blocks().getFirst();
        assertEquals(CameraSource.LIVE_BG1, first.camera());
        assertEquals(0x6F000003, first.upperVdpCommand());
        assertEquals(0x6F800003, first.lowerVdpCommand());
        assertEquals(0x3C0, first.layoutByteOffset());
        assertEquals(480, first.metadata().byteOffset());
    }

    @Test
    void initialLookupWrapsButIncrementalTopUsesOffscreenByteZero() {
        Fixture f = new Fixture();
        f.ids[0] = 4;
        var owner = f.owner();
        var initial = owner.initialize(state(), live(16, 0), FLAGS);
        assertEquals(0, initial.sections().getFirst().selector());
        var result = owner.incremental(state(), live(500, 0), copied(16, 0, 48, 32, 80, 64), flags(1));
        assertEquals(1, result.sections().size());
        assertEquals(0, result.sections().getFirst().tableIndex());
        assertEquals(4, result.sections().getFirst().selector());
        assertEquals(22, result.blocks().size());
        var first = result.blocks().getFirst();
        assertEquals(CameraSource.COPIED_BG2, first.camera());
        assertEquals(32, first.worldXWord());
        assertEquals(16, first.worldYWord());
        assertEquals(0x61080003, first.upperVdpCommand());
        assertEquals(0x61880003, first.lowerVdpCommand());
        byte[] expected = seed();
        paintGridRow(expected, 32, 16, 22);
        assertArrayEquals(expected, result.state().nameTable().copy());
    }

    @ParameterizedTest
    @CsvSource({"0,32,LIVE_BG1", "2,22,LIVE_BG1", "4,22,LIVE_BG2", "6,22,LIVE_BG3"})
    void selectorIsDoubledByteOffsetIntoLongsAndZeroUsesAddFlags(int selector, int count, CameraSource source) {
        Fixture f = new Fixture();
        Arrays.fill(f.ids, (byte) selector);
        var cameras = new LiveCameras(camera(16, 0), camera(48, 32), camera(80, 64));
        var result = f.owner().initialize(state(), cameras, FLAGS);
        assertEquals(count * 16, result.blocks().size());
        assertEquals(source, result.blocks().getFirst().camera());
        assertEquals(selector, result.sections().getFirst().selector());
        assertEquals(selector == 0 ? 0 : selector == 2 ? 0 : selector == 4 ? 32 : 64,
                result.blocks().getFirst().worldXWord());
    }

    @Test
    void initialSelectorEightStopsAtTheReachedLongReadWithoutTouchingSeed() {
        Fixture f = new Fixture();
        f.ids[128] = 8; // First initialized row at live Y0 reads the real wrapped index128.
        var before = state(); var bytes = before.nameTable().copy();
        assertBoundary(Boundary.CAMERA_POINTER_READ, 16,
                () -> f.owner().initialize(before, live(0, 0), FLAGS));
        assertArrayEquals(bytes, before.nameTable().copy());
    }

    @ParameterizedTest
    @CsvSource({"0,40961,18434,4099,61444", "2048,16386,43009,63492,6147",
            "4096,3,57348,45057,22530", "6144,59396,2051,20482,47105"})
    void everyBlockFlipReordersWordsAndXorsExistingPatternFlipBits(int flip, int a, int b, int c, int d) {
        Fixture f = new Fixture();
        Arrays.fill(f.chunks, (byte) 0);
        word(f.chunks, 0, flip);
        var result = f.owner().incremental(state(), live(0, 0), copied(0, 16, 0, 0, 0, 0), flags(1));
        assertEquals(a, result.state().word(0, 0));
        assertEquals(b, result.state().word(1, 0));
        assertEquals(c, result.state().word(0, 1));
        assertEquals(d, result.state().word(1, 1));
        assertEquals(flip >>> 8, result.blocks().getFirst().metadata().highByte());
    }

    @Test
    void incrementalUsesLiveSelectionYButCopiedBlockAndVdpCoordinatesWithFractionsIgnored() {
        Fixture f = new Fixture();
        f.ids[70] = 4; // live $460 + top -16 => table index 70; copy Y deliberately differs.
        var l = new LiveCameras(new Camera(0x7FFF1234, 0x0460ABCD), camera(0, 0), camera(0, 0));
        var c = new CopiedCameras(camera(32, 48), new Camera(0x0200FFFF, 0x01100001), camera(0, 0));
        var result = f.owner().incremental(state(), l, c, flags(1));
        assertEquals(70, result.sections().getFirst().tableIndex());
        var block = result.blocks().getFirst();
        assertEquals(496, block.worldXWord());
        assertEquals(256, block.worldYWord());
        assertEquals(0x607C0003, block.upperVdpCommand());
        assertEquals(0x60800003, result.blocks().get(1).lowerVdpCommand());
        byte[] expected = seed();
        paintGridRow(expected, 496, 256, 22);
        assertArrayEquals(expected, result.state().nameTable().copy());
    }

    @Test
    void actualRetailIdRunsSelectDynamicTwoOnlyInTheSourceBands() {
        Fixture f = new Fixture();
        Arrays.fill(f.ids, 71, 81, (byte) 4);
        Arrays.fill(f.ids, 109, 119, (byte) 4);
        var result = f.owner().initialize(state(), live(16, 0x460), FLAGS);
        assertEquals(32 + 10 * 22 + 5 * 32, result.blocks().size());
        assertEquals(70, result.sections().getFirst().tableIndex());
        for (int row = 0; row < 16; row++) {
            assertEquals(row >= 1 && row <= 10 ? 4 : 0, result.sections().get(row).selector());
        }
    }

    @ParameterizedTest
    @CsvSource({"1,0,1,32,0", "2,0,1,32,0", "3,0,17,32,0", "4,0,16,0,0",
            "8,0,16,0,0", "16,0,16,0,0", "255,0,17,32,0"})
    void rowFlagsHaveTopPriorityAndRemainingByteControlsColumnScan(int bits, int unused, int reads, int writes, int remaining) {
        var result = new Fixture().owner().incremental(state(), live(0, 0), copied(16, 0, 48, 0, 80, 0), flags(bits));
        assertEquals(reads, result.sections().size());
        assertEquals(writes, result.blocks().size());
        assertEquals((remaining << 8) | 0x5A, result.flags().bg1());
        assertEquals(FLAGS.bg2(), result.flags().bg2());
        assertEquals(FLAGS.bg3(), result.flags().bg3());
        if ((bits & 1) != 0) { assertEquals(-16, result.sections().getFirst().relativeY()); }
        else if ((bits & 2) != 0) { assertEquals(224, result.sections().getFirst().relativeY()); }
    }

    @ParameterizedTest
    @CsvSource({"4,2,-16,0", "8,2,320,336", "16,4,-16,32", "32,4,320,368",
            "64,6,-16,64", "128,6,320,400", "36,2,320,-1", "36,4,320,368"})
    void columnFlagBitsGateSelectorAndRightMaskDropsAllLeftBits(int bits, int selector, int relativeX, int expectedX) {
        Fixture f = new Fixture();
        Arrays.fill(f.ids, (byte) selector);
        var result = f.owner().incremental(state(), live(0, 0), copied(16, 0, 48, 32, 80, 64), flags(bits));
        assertEquals(16, result.sections().size());
        assertEquals(expectedX == -1 ? 0 : 16, result.blocks().size());
        byte[] expected = seed();
        if (expectedX != -1) {
            int cy = selector == 2 ? 0 : selector == 4 ? 32 : 64;
            for (int row = 0; row < 16; row++) { paintGridBlock(expected, expectedX, (cy - 16 + row * 16) & 0xFFFF); }
            assertEquals(relativeX, result.blocks().getFirst().relativeX());
            assertEquals(expectedX, result.blocks().getFirst().worldXWord());
        }
        assertArrayEquals(expected, result.state().nameTable().copy());
        assertEquals(0x5A, result.flags().bg1());
    }

    @Test
    void rowThenColumnCombinationOverwritesIntersectionsInNativeOrder() {
        Fixture f = new Fixture();
        Arrays.fill(f.ids, (byte) 4);
        var result = f.owner().incremental(state(), live(0, 0), copied(16, 0, 0x1F0, 0x100, 80, 0), flags(0x21));
        assertEquals(17, result.sections().size());
        assertEquals(38, result.blocks().size());
        byte[] expected = seed();
        paintGridRow(expected, 0x1E0, 0xF0, 22);
        for (int y = 0xF0; y <= 0x1E0; y += 16) { paintGridBlock(expected, 0x330, y); }
        assertArrayEquals(expected, result.state().nameTable().copy());
        assertEquals(Phase.INCREMENTAL_ROW, result.sections().getFirst().phase());
        assertEquals(Phase.INCREMENTAL_COLUMN, result.sections().getLast().phase());
    }

    @Test
    void rowDestinationWrapsAt512And256WhileChunkReadsCross256Boundary() {
        Fixture f = new Fixture();
        Arrays.fill(f.ids, (byte) 4);
        for (int row = 0; row < 8; row++) { f.bg[2 + row * 6 + 2] = 2; }
        // A distinct second-chunk block defeats the fixture's ordinary256px mosaic period.
        word(f.chunks, 992, 137);
        word(f.blocks, 137 * 8, 0xA2AB); word(f.blocks, 137 * 8 + 2, 0x42AC);
        word(f.blocks, 137 * 8 + 4, 0x12AD); word(f.blocks, 137 * 8 + 6, 0xE2AE);
        var result = f.owner().incremental(state(), live(0, 0), copied(0, 0, 0x200, 0, 0, 0), flags(1));
        assertEquals(0x6F7C0003, result.blocks().getFirst().upperVdpCommand());
        assertEquals(0x6F000003, result.blocks().get(1).upperVdpCommand());
        assertEquals(1, result.blocks().getFirst().layoutRawByte());
        assertEquals(2, result.blocks().get(1).layoutRawByte());
        assertEquals(992, result.blocks().get(1).metadata().byteOffset());
        assertEquals(137 * 8, result.blocks().get(1).blockByteOffset());
        assertEquals(240, result.blocks().get(1).worldYWord() & 0xFF);
        assertEquals(0xA2AB, result.state().word(0, 30)); assertEquals(0x42AC, result.state().word(1, 30));
        assertEquals(0x12AD, result.state().word(0, 31)); assertEquals(0xE2AE, result.state().word(1, 31));
    }

    @Test
    void zeroFlagsReturnSameSeedWithoutReadingEvenInvalidCameraOrSectionContents() {
        Fixture f = new Fixture();
        Arrays.fill(f.ids, (byte) 0xFF);
        Arrays.fill(f.pointers, (byte) 0);
        State seed = state();
        var result = f.owner().incremental(seed, live(0, 0xFFFF), copied(0, 0, 0, 0, 0, 0), FLAGS);
        assertSame(seed, result.state());
        assertTrue(result.sections().isEmpty());
        assertTrue(result.blocks().isEmpty());
        assertEquals(FLAGS, result.flags());
    }

    @Test
    void finiteTableKeepsTail129ButNextActualReadStopsWithoutClampingOrPartialState() {
        Fixture f = new Fixture();
        var owner = f.owner();
        var tail = owner.incremental(state(), live(0, 0x720), copied(0, 0, 0, 0, 0, 0), flags(2));
        assertEquals(129, tail.sections().getFirst().tableIndex());
        State before = state();
        byte[] bytes = before.nameTable().copy();
        assertBoundary(Boundary.SECTION_READ, 130, () -> owner.incremental(before,
                live(0, 0x730), copied(0, 0, 0, 0, 0, 0), flags(2)));
        assertBoundary(Boundary.SECTION_READ, 130, () -> owner.incremental(before,
                live(0, 0x730), copied(0, 0, 0, 0, 0, 0), flags(4)));
        assertArrayEquals(bytes, before.nameTable().copy());
    }

    @Test
    void signedNegativeRowAndColumnLookupsHaveDifferentActualBounds() {
        var owner = new Fixture().owner();
        assertBoundary(Boundary.SECTION_READ, -1, () -> owner.incremental(state(), live(0, 0xFFF0),
                copied(0, 0, 0, 0, 0, 0), flags(1)));
        assertBoundary(Boundary.SECTION_READ, -1, () -> owner.incremental(state(), live(0, 0xFFF0),
                copied(0, 0, 0, 0, 0, 0), flags(4)));
        var initial = owner.initialize(state(), live(0, 0xFFF0), FLAGS);
        assertEquals(127, initial.sections().getFirst().tableIndex());
    }

    @Test
    void blankRowsUseBlockZeroAndCarryRealMetadataProvenanceRatherThanFabricateZeroTiles() {
        Fixture f = new Fixture();
        for (int row = 0; row < 8; row++) { f.bg[2 + row * 6 + 1] = 0; }
        var result = f.owner().incremental(state(), live(0, 0), copied(0, 16, 0, 0, 0, 0), flags(1));
        var blank = result.blocks().get(16);
        assertEquals(0, blank.layoutRawByte());
        assertEquals(0, blank.blockByteOffset());
        assertEquals(MetadataSource.CHUNKS, blank.metadata().source());
        assertEquals(30, blank.metadata().byteOffset());
        assertEquals(0x68, blank.metadata().highByte());
        assertEquals(0x4002, result.state().word(32, 0));
        assertEquals(0xA801, result.state().word(33, 0));
        Arrays.fill(f.bg, 2, f.bg.length, (byte) 0);
        f.ids[1] = 0x18; // only the actual metadata high byte is read on this top-row path.
        var firstBlank = f.owner().incremental(state(), live(0, 0), copied(0, 16, 0, 0, 0, 0), flags(1));
        assertEquals(MetadataSource.SECTION_IDS, firstBlank.blocks().getFirst().metadata().source());
        assertEquals(1, firstBlank.blocks().getFirst().metadata().byteOffset());
        assertEquals(0xE804, firstBlank.state().word(0, 0));
    }

    @Test
    void blankColumnReadsNextIdByteAndStopsAtActualMetadataReadBeyondTail() {
        Fixture f = new Fixture();
        Arrays.fill(f.bg, 2, f.bg.length, (byte) 0);
        f.ids[0] = 4;
        f.ids[1] = 0x18;
        var result = f.owner().incremental(state(), live(0, 0), copied(0, 0, 16, 16, 0, 0), flags(0x10));
        assertEquals(1, result.blocks().size());
        assertEquals(0xE804, result.state().word(0, 0));
        f.ids[0] = 0;
        f.ids[129] = 4;
        assertBoundary(Boundary.SECTION_READ, 130, () -> f.owner().incremental(state(), live(0, 0x720),
                copied(0, 0, 16, 16, 0, 0), flags(0x10)));
    }

    @Test
    void nativeLayoutAddressCanReachRetainedNextForegroundHalfWithoutInventedBgClamp() {
        Fixture f = new Fixture();
        f.fg[2 + 42] = 2; // native BG+$40 + column 64 = next FG row, present in retained RAM.
        Arrays.fill(f.ids, (byte) 4);
        var result = f.owner().incremental(state(), live(0, 0), copied(0, 0, 0x4010, 16, 0, 0), flags(0x10));
        var first = result.blocks().getFirst();
        assertEquals(0x80, first.layoutByteOffset());
        assertEquals(2, first.layoutRawByte());
        assertEquals(512, first.metadata().byteOffset());
    }

    @Test
    void layoutRaw80IsNotBlankAndFailsAtActualUnloadedChunk128Read() {
        Fixture f = new Fixture();
        f.bg[2] = (byte) 0x80; // terrain's semantic ID is zero; native TST.B is nonzero.
        assertBoundary(Boundary.CHUNK_READ, 127 * 512,
                () -> f.owner().incremental(state(), live(0, 0), copied(0, 16, 0, 0, 0, 0), flags(1)));
    }

    @Test
    void pointerReadsAreBoundedAndUnusedBadSelectorsDoNotGetEagerlyValidated() {
        Fixture f = new Fixture();
        f.ids[0] = 8;
        var skipped = f.owner().incremental(state(), live(0, 0), copied(0, 0, 0, 0, 0, 0), flags(4));
        assertTrue(skipped.blocks().isEmpty()); // BTST on byte uses bit 8 modulo 8: bit zero is unset.
        assertBoundary(Boundary.CAMERA_POINTER_READ, 16, () -> f.owner().incremental(state(), live(0, 0),
                copied(0, 0, 0, 0, 0, 0), flags(1)));
        f.ids[0] = (byte) 0x80;
        assertBoundary(Boundary.CAMERA_POINTER_READ, -256, () -> f.owner().incremental(state(), live(0, 0),
                copied(0, 0, 0, 0, 0, 0), flags(1)));
        Arrays.fill(f.ids, (byte) 0);
        f.pointers[1] = 0;
        assertBoundary(Boundary.CAMERA_ADDRESS, 0, () -> f.owner().initialize(state(), live(0, 0), FLAGS));
    }

    @Test
    void viewBindingChecksSelectorsAndBothExtentsButDoesNotAuthenticateFixtureViews() {
        Fixture f = new Fixture();
        var terrain = f.terrain();
        var ids = view(CdR11aAsset.BG_CAMERA_SECT_IDS, f.ids, 130);
        var pointers = view(CdR11aAsset.BG_CAMERA_SECTS, f.pointers, 16);
        assertEquals(512, fromViews(terrain, ids, pointers).initialize(state(), live(0, 0), FLAGS).blocks().size());
        assertBoundary(Boundary.VIEW_BINDING, 0, () -> fromViews(terrain, pointers, ids));
        assertBoundary(Boundary.VIEW_BINDING, 0, () -> fromViews(terrain,
                view(CdR11aAsset.BG_CAMERA_SECT_IDS, f.ids, 129), pointers));
        assertBoundary(Boundary.VIEW_BINDING, 0, () -> fromViews(terrain,
                view(CdR11aAsset.BG_CAMERA_SECT_IDS, new byte[129], 130), pointers));
    }

    @Test
    void malformedShapesAndUnsignedFlagWordsFailWithoutDefaultingOrNarrowing() {
        Fixture f = new Fixture();
        assertBoundary(Boundary.TABLE_SHAPE, 129, () -> fromTables(f.terrain(), CdAssetBytes.copyOf(new byte[129]), CdAssetBytes.copyOf(f.pointers)));
        assertBoundary(Boundary.TABLE_SHAPE, 15, () -> fromTables(f.terrain(), CdAssetBytes.copyOf(f.ids), CdAssetBytes.copyOf(new byte[15])));
        assertBoundary(Boundary.STATE_SHAPE, 4094, () -> new State(CdAssetBytes.copyOf(new byte[4094])));
        assertThrows(IllegalArgumentException.class, () -> new FlagWords(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new FlagWords(0, 65536, 0));
        assertThrows(IllegalArgumentException.class, () -> new FlagWords(0, 0, -1));
        assertThrows(NullPointerException.class, () -> new State(null));
        assertThrows(NullPointerException.class, () -> new LiveCameras(null, camera(0, 0), camera(0, 0)));
        assertThrows(NullPointerException.class, () -> new CopiedCameras(camera(0, 0), null, camera(0, 0)));
    }

    @Test
    void allBorrowsRemainImmutableAndOutputsNeverTickOrRetainCallerArrays() {
        Fixture f = new Fixture();
        var owner = f.owner();
        byte[] before = seed();
        State state = new State(CdAssetBytes.copyOf(before));
        Arrays.fill(f.ids, (byte) 0xFF);
        Arrays.fill(f.pointers, (byte) 0);
        Arrays.fill(f.blocks, (byte) 0);
        Arrays.fill(f.chunks, (byte) 0);
        Arrays.fill(f.bg, (byte) 0);
        Arrays.fill(before, (byte) 0);
        var a = owner.initialize(state, live(0, 0), FLAGS);
        var b = owner.initialize(state, live(0, 0), FLAGS);
        assertArrayEquals(seed(), state.nameTable().copy());
        assertArrayEquals(a.state().nameTable().copy(), b.state().nameTable().copy());
        assertEquals(a.blocks(), b.blocks());
        byte[] borrowed = a.state().nameTable().copy();
        Arrays.fill(borrowed, (byte) 0);
        assertNotEquals(0, a.state().word(0, 0));
        assertThrows(ReadOnlyBufferException.class, () -> a.state().nameTable().buffer().put(0, (byte) 0));
        assertThrows(UnsupportedOperationException.class, () -> a.blocks().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.sections().clear());
        assertEquals(a.state().word(0, 0), a.state().word(0, 0)); // capture/projection has no step operation.
    }

    @Test
    void optInSuppliedUsaMediaBindsOnlyExistingViewsAfterDiscClose() throws Exception {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.media.root for supplied-media binding");
        Path cue;
        try (var entries = Files.list(Path.of(configured).resolve("USA"))) {
            var cues = entries.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            cue = cues.getFirst();
        }
        CdR11aAssets assets;
        try (var disc = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            assets = CdR11aAssetLoader.load(disc, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        }
        var owner = bind(assets);
        var result = owner.initialize(state(), live(0, 0), FLAGS);
        assertEquals(16, result.sections().size());
        assertEquals(512, result.blocks().size());
        assertEquals(128, result.sections().getFirst().tableIndex());
        assertEquals(0, result.sections().getFirst().selector());
        assertEquals(0x6F000003, result.blocks().getFirst().upperVdpCommand());
        assertEquals(FLAGS, result.flags());
        // This is a source-view bind case, not a retail plane-image/pixel or PLC residency oracle.
    }

    private static void assertBoundary(Boundary expected, int offset, org.junit.jupiter.api.function.Executable action) {
        InvalidPlane failure = assertThrows(InvalidPlane.class, action);
        assertEquals(expected, failure.boundary());
        assertEquals(offset, failure.byteOffset());
    }

    private static CdR11aAssets.View view(CdR11aAsset asset, byte[] bytes, int rangeLength) {
        return new CdR11aAssets.View(asset, new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0, rangeLength), CdAssetBytes.copyOf(bytes));
    }

    private static FlagWords flags(int highByte) { return new FlagWords((highByte << 8) | 0x5A, FLAGS.bg2(), FLAGS.bg3()); }
    private static State state() { return new State(CdAssetBytes.copyOf(seed())); }
    private static Camera camera(int x, int y) { return new Camera(x << 16, y << 16); }
    private static LiveCameras live(int x, int y) { return new LiveCameras(camera(x, y), camera(48, y), camera(80, y)); }
    private static CopiedCameras copied(int x1, int y1, int x2, int y2, int x3, int y3) {
        return new CopiedCameras(camera(x1, y1), camera(x2, y2), camera(x3, y3));
    }
    private static byte[] seed() {
        byte[] bytes = new byte[4096];
        for (int i = 0; i < 2048; i++) { word(bytes, i * 2, 0x8000 + i); }
        return bytes;
    }
    private static void word(byte[] bytes, int at, int value) {
        bytes[at] = (byte) (value >>> 8);
        bytes[at + 1] = (byte) value;
    }

    // Independent expected image: a periodic 256px test mosaic, with checkerboard block reflections.
    // This derives expected raw tiles from each destination's world pixel, without any source/VDP helper.
    private static int gridTile(int worldX, int worldY) {
        int bx = (worldX & 255) / 16, by = (worldY & 255) / 16;
        int px = ((worldX & 15) / 8) ^ (bx & 1), py = ((worldY & 15) / 8) ^ (by & 1);
        int[] attributes = {0xA000, 0x4800, 0x1000, 0xF000};
        int index = py * 2 + px;
        return (attributes[index] | (((by * 16 + bx) % 128) * 4 + index + 1))
                ^ ((bx & 1) * 0x800) ^ ((by & 1) * 0x1000);
    }
    private static void paintGridBlock(byte[] ram, int x, int y) {
        x &= 0xFFF0;
        y &= 0xFFF0;
        for (int dy = 0; dy < 16; dy += 8) {
            for (int dx = 0; dx < 16; dx += 8) {
                int tx = ((x + dx) / 8) % 64, ty = ((y + dy) / 8) % 32;
                word(ram, (ty * 64 + tx) * 2, gridTile(x + dx, y + dy));
            }
        }
    }
    private static void paintGridRow(byte[] ram, int x, int y, int blocks) {
        for (int i = 0; i < blocks; i++) { paintGridBlock(ram, (x + 16 * i) & 0xFFFF, y); }
    }

    private static final class Fixture {
        final byte[] ids = new byte[130];
        final byte[] pointers = {0, (byte) 0xFF, (byte) 0xF7, 8, 0, (byte) 0xFF, (byte) 0xF7, 8,
                0, (byte) 0xFF, (byte) 0xF7, 16, 0, (byte) 0xFF, (byte) 0xF7, 24};
        final byte[] blocks = new byte[6432], patterns = new byte[25600], chunks = new byte[61952];
        final byte[] fg = new byte[338], bg = new byte[50];
        Fixture() {
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7;
            Arrays.fill(fg, 2, fg.length, (byte) 1);
            Arrays.fill(bg, 2, bg.length, (byte) 1);
            int[] attributes = {0xA000, 0x4800, 0x1000, 0xF000};
            for (int b = 0; b < 804; b++) {
                for (int p = 0; p < 4; p++) { word(blocks, b * 8 + p * 2, attributes[p] | ((b % 128) * 4 + p + 1)); }
            }
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    word(chunks, (y * 16 + x) * 2, 0x6000 | y * 16 + x | (x & 1) * 0x800 | (y & 1) * 0x1000);
                }
            }
        }
        CdR11aTerrain terrain() {
            return CdR11aTerrain.decode(blocks, patterns, chunks, fg, bg,
                    new byte[900], new byte[256], new byte[4096], new byte[4096]);
        }
        CdR11aBackgroundPlane owner() { return fromTables(terrain(), CdAssetBytes.copyOf(ids), CdAssetBytes.copyOf(pointers)); }
    }
}
