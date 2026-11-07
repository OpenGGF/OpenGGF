package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.openggf.game.soniccd.level.CdR12bProfile.Asset.*;
import static com.openggf.game.soniccd.level.CdStageTerrain.Plane.*;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed format controls only. No constructed image or metadata authenticates original media. */
class TestCdR12bData {
    private static CdR12bProfile profile() {
        return CdR12bProfile.forStage(CdReleaseVariant.USA, CdStagePayload.R12B.stage());
    }

    @Test void sourceProfileIsExplicitUsaR12bWithTwentyFiveBoundedDescriptors() {
        var p = profile();
        assertEquals(CdReleaseVariant.USA, p.variant()); assertEquals(CdStagePayload.R12B.stage(), p.stage());
        assertEquals(CdNativeFile.R12B, p.file()); assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, p.domain());
        assertEquals(262144, CdR12bProfile.FILE_BYTES); assertEquals(25, CdR12bProfile.Asset.values().length);
        assertEquals("14de4fdb4979125a8c0c197c926133353d20a2ca5b2d57db2e363c4e94460a64", p.expectedMmdSha256());
        for (var variant : new CdReleaseVariant[]{CdReleaseVariant.JAPAN, CdReleaseVariant.EUROPE})
            assertThrows(UnsupportedOperationException.class, () -> CdR12bProfile.forStage(variant, CdStagePayload.R12B.stage()));
        for (var stage : new com.openggf.game.soniccd.CdStageKey[]{CdStagePayload.R11B.stage(), CdStagePayload.R12A.stage()})
            assertThrows(UnsupportedOperationException.class, () -> CdR12bProfile.forStage(CdReleaseVariant.USA, stage));
        assertThrows(NullPointerException.class, () -> CdR12bProfile.forStage(null, CdStagePayload.R12B.stage()));
        assertThrows(NullPointerException.class, () -> CdR12bProfile.forStage(CdReleaseVariant.USA, null));
    }

    @Test void originalRangesRejectWrongFileDomainOverflowAndMissingViews() {
        var p = profile();
        var end = new CdR12bProfile.FileRange(CdNativeFile.R12B, p.domain(), 262144, 0);
        assertEquals(0x240000, end.nativeAddress()); assertEquals(262144, end.endExclusive());
        for (int[] v : new int[][]{{-1,1},{0,-1},{262144,1},{1,Integer.MAX_VALUE},{Integer.MAX_VALUE,0}})
            assertThrows(IllegalArgumentException.class, () -> new CdR12bProfile.FileRange(p.file(), p.domain(), v[0], v[1]));
        assertThrows(IllegalArgumentException.class, () -> new CdR12bProfile.FileRange(CdNativeFile.R11B, p.domain(), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdR12bProfile.FileRange(p.file(), CdMmdImage.Domain.RELOCATED_COPY, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdR12bProfile.FileRange(null, p.domain(), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdR12bProfile.FileRange(p.file(), null, 0, 1));
        assertThrows(NullPointerException.class, () -> p.range(null));
        assertThrows(NullPointerException.class, () -> p.range(LEVEL_CHUNKS).read(null));
    }

    @Test void onlyTheTwoQualifiedTerrainStreamsHaveCanonicalReaderRanges() {
        var p = profile();
        assertEquals(p.range(LEVEL_BLOCKS), p.readerRange(LEVEL_BLOCKS));
        assertEquals(p.range(ART_LEVEL_TILES), p.readerRange(ART_LEVEL_TILES));
        assertEquals(4468, p.readerRange(LEVEL_BLOCKS).length()); assertEquals(10802, p.readerRange(ART_LEVEL_TILES).length());
        for (var asset : CdR12bProfile.Asset.values()) if (asset != LEVEL_BLOCKS && asset != ART_LEVEL_TILES)
            assertThrows(IllegalArgumentException.class, () -> p.readerRange(asset));
        assertThrows(NullPointerException.class, () -> p.readerRange(null));
    }

    @Test void consistentPublicIdentityCannotConstructAnAuthenticatedAggregate() {
        var p = profile();
        var identity = new CdR12bTerrainAssets.Identity(p.variant(), p.stage(), p.file(), p.domain(), p.expectedMmdSha256(), "0".repeat(64));
        assertEquals(CdNativeFile.R12B, identity.file());
        for (var constructor : CdR12bTerrainAssets.class.getDeclaredConstructors()) assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        assertThrows(IllegalArgumentException.class, () -> new CdR12bTerrainAssets.Identity(CdReleaseVariant.JAPAN, p.stage(), p.file(), p.domain(), p.expectedMmdSha256(), "0".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new CdR12bTerrainAssets.Identity(p.variant(), CdStagePayload.R12A.stage(), p.file(), p.domain(), p.expectedMmdSha256(), "0".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new CdR12bTerrainAssets.Identity(p.variant(), p.stage(), CdNativeFile.R11B, p.domain(), p.expectedMmdSha256(), "0".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new CdR12bTerrainAssets.Identity(p.variant(), p.stage(), p.file(), CdMmdImage.Domain.RELOCATED_COPY, p.expectedMmdSha256(), "0".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new CdR12bTerrainAssets.Identity(p.variant(), p.stage(), p.file(), p.domain(), "bad", "0".repeat(64)));
        assertThrows(NullPointerException.class, () -> new CdR12bTerrainAssets.Identity(p.variant(), p.stage(), p.file(), p.domain(), p.expectedMmdSha256(), null));
    }

    @Test void plcEntriesAreOnlyFiniteOriginalPointersAndAlignedDestinationMetadata() {
        var entry = new CdR12bLoadPlan.PlcEntry(0x23CBA0L, 0);
        assertEquals(0x23CBA0L, entry.sourceNativeAddress()); assertEquals(0, entry.vramAddress());
        for (long pointer : new long[]{-1,0x1FFFFF,0x240000,0x100000000L})
            assertThrows(IllegalArgumentException.class, () -> new CdR12bLoadPlan.PlcEntry(pointer, 0));
        for (int destination : new int[]{-1,1,0xFFFF,0x10000})
            assertThrows(IllegalArgumentException.class, () -> new CdR12bLoadPlan.PlcEntry(0x23CBA0, destination));
        assertEquals(0xFFE0, new CdR12bLoadPlan.PlcEntry(0x23FFFF, 0xFFE0).vramAddress());
    }

    @Test void plcListMetadataFreezesRowsAndRetainsNativeAliasBodyRanges() {
        var rows = new ArrayList<>(List.of(new CdR12bLoadPlan.PlcEntry(0x23CBA0, 0)));
        var range = new CdR12bProfile.FileRange(CdNativeFile.R12B, CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0xF060, 8);
        var first = new CdR12bLoadPlan.PlcList(0, range, rows); var alias = new CdR12bLoadPlan.PlcList(3, range, rows);
        rows.clear(); assertEquals(1, first.entries().size()); assertEquals(first.bodyRange(), alias.bodyRange());
        assertThrows(UnsupportedOperationException.class, () -> first.entries().clear());
        assertThrows(IllegalArgumentException.class, () -> new CdR12bLoadPlan.PlcList(19, range, first.entries()));
        assertThrows(IllegalArgumentException.class, () -> new CdR12bLoadPlan.PlcList(0, range, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CdR12bLoadPlan.PlcList(0,
                new CdR12bProfile.FileRange(CdNativeFile.R12B, range.domain(), 0xF03A, 8), first.entries()));
        assertThrows(NullPointerException.class, () -> new CdR12bLoadPlan.PlcList(0, range, null));
    }

    @Test void paletteMetadataAdmitsOnlyTheTwoCompleteNativeBindings() {
        var sonic = new CdR12bLoadPlan.PaletteBinding(3, 0xFB00, 7, PAL_SONIC, CdAssetBytes.copyOf(new byte[32]));
        var level = new CdR12bLoadPlan.PaletteBinding(5, 0xFB20, 23, PAL_LEVEL, CdAssetBytes.copyOf(new byte[96]));
        assertEquals(16, sonic.colorCount()); assertEquals(48, level.colorCount());
        assertThrows(IllegalArgumentException.class, () -> new CdR12bLoadPlan.PaletteBinding(4, 0xFB20, 23, PAL_LEVEL, level.bytes()));
        assertThrows(IllegalArgumentException.class, () -> new CdR12bLoadPlan.PaletteBinding(3, 0xFB20, 7, PAL_SONIC, sonic.bytes()));
        assertThrows(IllegalArgumentException.class, () -> new CdR12bLoadPlan.PaletteBinding(5, 0xFB20, 7, PAL_LEVEL, level.bytes()));
        assertThrows(IllegalArgumentException.class, () -> new CdR12bLoadPlan.PaletteBinding(3, 0xFB00, 7, PAL_SONIC, CdAssetBytes.copyOf(new byte[31])));
        assertThrows(NullPointerException.class, () -> new CdR12bLoadPlan.PaletteBinding(3, 0xFB00, 7, null, sonic.bytes()));
    }

    @Test void originalShapeAndHashAreIndependentOfComponentMetadata() throws Exception {
        var p = profile(); var bytes = constructedImage(); var image = CdMmdImage.parse(bytes);
        assertTrue(CdR12bProfile.originalShape(image));
        assertThrows(IOException.class, () -> p.verifyProgram(p.file(), p.domain(), image), "Constructed zeros have no authentic file identity");
        assertThrows(IllegalArgumentException.class, () -> p.verifyProgram(CdNativeFile.R12A, p.domain(), image));
        assertThrows(IllegalArgumentException.class, () -> p.verifyProgram(p.file(), CdMmdImage.Domain.RELOCATED_COPY, image));
        assertThrows(NullPointerException.class, () -> p.verifyProgram(p.file(), p.domain(), null));
        assertFalse(CdR12bProfile.originalShape(CdMmdImage.parse(Arrays.copyOf(bytes, 262143))));
        bytes[8] ^= 1; assertFalse(CdR12bProfile.originalShape(CdMmdImage.parse(bytes)));
        assertThrows(NullPointerException.class, () -> CdR12bLoadPlan.decode(null, image));
        assertThrows(NullPointerException.class, () -> CdR12bLoadPlan.decode(p, null));
        assertThrows(IllegalArgumentException.class, () -> CdR12bLoadPlan.decode(p, CdMmdImage.parse(Arrays.copyOf(constructedImage(), 262143))));
        assertThrows(NullPointerException.class, () -> CdR12bTerrainAssets.fromPrograms(null));
    }

    @Test void newShapeAppendsOneExactFormatAndUsesTheExistingGeometryProjection() {
        assertArrayEquals(new CdStageTerrain.Shape[]{CdStageTerrain.Shape.PPZ1_PRESENT, CdStageTerrain.Shape.PPZ1_PAST,
                CdStageTerrain.Shape.PPZ1_GOOD_FUTURE, CdStageTerrain.Shape.PPZ1_BAD_FUTURE,
                CdStageTerrain.Shape.PPZ2_PRESENT, CdStageTerrain.Shape.PPZ2_PAST,
                CdStageTerrain.Shape.PPZ3_GOOD_FUTURE, CdStageTerrain.Shape.PPZ3_BAD_FUTURE, CdStageTerrain.Shape.CCZ1_PRESENT, CdStageTerrain.Shape.CCZ2_PRESENT}, CdStageTerrain.Shape.values());
        var t = new TerrainFixture().decode(); assertEquals(708, t.blockCount()); assertEquals(797, t.patternCount()); assertEquals(106, t.chunkCount());
        var geometry = new SonicCdLevelGeometry(t, CdAssetBytes.copyOf(new byte[128]), 0, 0, 0x2897, 0, 0x310);
        assertTrue(geometry.getObjects().isEmpty()); assertTrue(geometry.getRings().isEmpty());
        assertEquals("R12B static view cannot invent unloaded pattern/resident data",
                assertThrows(UnsupportedOperationException.class, () -> geometry.ensurePatternCapacity(798)).getMessage());
    }

    @Test void constructedLayoutUsesNativeClearedCellsWithoutRepeatingRows() {
        var f = new TerrainFixture(); f.fg[2] = 1; f.fg[2 + 3 * 42 + 41] = 106; f.bg[2 + 19] = 1;
        var t = f.decode(); var layout = t.layout();
        assertEquals(42, layout.columns(FOREGROUND)); assertEquals(4, layout.rows(FOREGROUND));
        assertEquals(10, layout.columns(BACKGROUND)); assertEquals(2, layout.rows(BACKGROUND));
        assertEquals(1, layout.cell(FOREGROUND, 0, 0).chunkId()); assertEquals(106, layout.cell(FOREGROUND, 41, 3).chunkId());
        assertEquals(1, layout.cell(BACKGROUND, 9, 1).chunkId());
        for (int y = 0; y < 16; y++) for (int x = 0; x < 64; x++) {
            if (x >= 42 || y >= 4) assertEquals(0, layout.nativeCell(FOREGROUND, x, y).rawByte());
            if (x >= 10 || y >= 2) assertEquals(0, layout.nativeCell(BACKGROUND, x, y).rawByte());
        }
        assertThrows(IndexOutOfBoundsException.class, () -> layout.cell(BACKGROUND, 10, 0));
    }

    @Test void constructedTerrainRejectsEveryWrongExtentAndWrongLayoutHeader() {
        for (int field = 0; field < 9; field++) for (int delta : new int[]{-1,1}) {
            var f = new TerrainFixture(); byte[][] arrays = f.arrays(); arrays[field] = Arrays.copyOf(arrays[field], arrays[field].length + delta);
            assertThrows(IllegalArgumentException.class, () -> decode(arrays));
        }
        var f = new TerrainFixture(); f.fg[0] = 40; assertThrows(IllegalArgumentException.class, f::decode);
        f = new TerrainFixture(); f.bg[1] = 2; assertThrows(IllegalArgumentException.class, f::decode);
    }

    @Test void constructedReferencesRetainTheOldPatternRenderAndCollisionMasks() {
        var f = new TerrainFixture(); f.blocks[0] = 3; f.blocks[1] = 0x1D;
        assertThrows(IllegalArgumentException.class, f::decode, "797 is outside 0..796");
        f = new TerrainFixture(); f.fg[2] = 107; assertThrows(IllegalArgumentException.class, f::decode);
        f = new TerrainFixture(); f.fg[2] = 1; f.chunks[0] = 3; f.chunks[1] = 0;
        assertThrows(IllegalArgumentException.class, f::decode, "Render block768 is not supplied");
        f = new TerrainFixture(); f.fg[2] = 1; f.chunks[0] = 4; f.chunks[1] = 0;
        assertThrows(IllegalArgumentException.class, f::decode, "Collision index1024 remains distinct from render index0");
        assertThrows(IndexOutOfBoundsException.class, () -> new TerrainFixture().decode().chunkWord(0, 0, 0));
    }

    @Test void constructedInputsAndExportedCopiesCannotMutateDetachedTerrain() {
        var f = new TerrainFixture(); var t = f.decode(); f.blocks[0] = 1; f.chunks[0] = 1; f.ids[0] = 1;
        byte[] exported = t.blocks().copy(); Arrays.fill(exported, (byte) 0xFF);
        assertEquals(0, t.blockWord(0, 0, 0).rawWord()); assertEquals(0, t.chunkWord(1, 0, 0).rawWord());
        assertEquals(0, t.collision().idForBlock(0)); assertEquals(0, t.layout().nativeCell(FOREGROUND, 0, 0).rawByte());
    }

    private static byte[] constructedImage() {
        var b = ByteBuffer.allocate(262144); b.put((byte) 0).put((byte) 0).putInt(0).putShort((short) 0);
        b.putInt(0x200100).putInt(0x20010C).putInt(0x200112); return b.array();
    }
    private static CdStageTerrain decode(byte[][] a) {
        return CdStageTerrain.decode(CdStageTerrain.Shape.PPZ2_PAST, a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],a[8]);
    }
    private static final class TerrainFixture {
        final byte[] blocks = new byte[5664], patterns = new byte[25504], chunks = new byte[54272];
        final byte[] fg = new byte[170], bg = new byte[22], ids = new byte[900], angles = new byte[256], heights = new byte[4096], widths = new byte[4096];
        TerrainFixture() { fg[0] = 41; fg[1] = 3; bg[0] = 9; bg[1] = 1; }
        byte[][] arrays() { return new byte[][]{blocks,patterns,chunks,fg,bg,ids,angles,heights,widths}; }
        CdStageTerrain decode() { return TestCdR12bData.decode(arrays()); }
    }
}
