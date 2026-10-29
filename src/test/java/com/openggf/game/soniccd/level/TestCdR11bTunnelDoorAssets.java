package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.release.CdRetailReleases;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR11bAssets.Asset.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Original USA R11B horizontal Tunnel Door raw sources and the existing pure format decoder only.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Spin Tunnel/Main (Door).asm:7-20,55-84; complete Data/Animations (Door).asm:1-3;
 * complete Data/Mappings (Door).asm:1-27; Object Tile IDs (Act 1 Past).asm:7-44;
 * Data (Act 1 Past).asm:52-55,263-268,430-431.
 * Complete literals were independently expanded from pinned declarations and matched to original
 * media; the actual absolute LEA selects the animation, not its first byte occurrence.
 * Actual USA media is a hard prerequisite. No body, Solid call, animator, current publication,
 * World, renderer, sound or native routine executes. All Java execution is initially UNRUN.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11bTunnelDoorAssets {
    private static final String PAST_SHA =
            "b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265";
    private static final byte[] NATIVE_ANIMATION = HexFormat.of().parseHex("00020000000101010101010101fc");
    private static final byte[] NATIVE_MAPPING = HexFormat.of().parseHex(
            "0010000402d0030000e4f0010004e40002f80c0006e8f804000a0800");
    private static final byte[] NATIVE_TILE_IDS = HexFormat.of().parseHex(
            "002400260028002a002c002e00380032003600340030003a003c003e0040004200440046"
                    + "2396243f23ed2000240343340320038140004000037203468328000000000490039e0000");
    private static final byte[] NATIVE_ART = HexFormat.of().parseHex(
            "800c8004081302251c4634551e72008104097635860519151b87040a88040b89030315188e063a777c8f063b777dff5fb8e67f60e33e6000000000000000002fcfe95bc4c57e9bf533952b0a69a7135f9b6baee371857f9de357c987fb0955f9ec4e694a74a69a7135f9b6baee371857f9de357a3d1eff3ffa7e8a9fef11d9e7aff83e9f00d4fa7c0353e9f00d4fefe3d98ff7581fe1bf380000");
    private static final List<CdR11bAssets.Asset> DOOR_TABLES = List.of(ANI_TUNNEL_DOOR, MAP_SPR_TUNNEL_DOOR);

    @BeforeAll void requireAuthenticatedOriginalUsaMedia() throws Exception {
        retail();
    }

    @Test void twoRawViewsAppendExactIdentityWithoutReaderOrPlcOwnership() throws Exception {
        var a = retail().assets();
        String[] labels = {"Ani_TunnelDoor", "MapSpr_TunnelDoor"};
        int[] offsets = {0x1D6B6, 0x1D6C4}; int[] lengths = {14, 28};
        String[] hashes = {"398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0",
                "7260a75372713a37c9acb8b62f653a3d17c824b5e2eb88d6661789dd9cdaca92"};
        assertEquals(89, CdR11bAssets.Asset.values().length);
        for (int i = 0; i < DOOR_TABLES.size(); i++) {
            var key = DOOR_TABLES.get(i); var view = a.view(key);
            assertEquals(87 + i, key.ordinal()); assertEquals(labels[i], key.nativeLabel());
            assertEquals(offsets[i], key.fileOffset()); assertEquals(lengths[i], key.length());
            assertSame(key, view.asset()); assertEquals(key.range(), view.range());
            assertEquals(CdNativeFile.R11B, view.range().file());
            assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, view.range().domain());
            assertEquals(0x200000 + offsets[i], view.range().nativeAddress());
            assertEquals(0x200000 + offsets[i] + lengths[i], view.range().endExclusive());
            assertEquals(hashes[i], sha(view.bytes().copy()));
            assertFalse(key.isPlcSource()); assertTrue(key.readerProfile().isEmpty());
            for (var kind : CdR11bAssets.ReaderKind.values())
                assertThrows(IllegalArgumentException.class, () -> a.readerView(key, kind));
            assertThrows(IllegalArgumentException.class,
                    () -> new CdR11bLoadPlan.PlcEntry(key, key.range(), 0x6500));
        }
        assertEquals(84, PLATFORM_MAPPINGS.ordinal()); assertEquals(85, PLATFORM_WIDTHS.ordinal());
        assertEquals(86, PLATFORM_RANGES.ordinal()); assertEquals(83, OBJECT_TILE_IDS.ordinal());
        assertEquals(37, Arrays.stream(CdR11bAssets.Asset.values()).filter(k -> k.readerProfile().isPresent()).count());
        assertEquals(36, Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
    }

    @Test void actualId0dDirectoryAndInitOperandsSelectTheHorizontalDoorSources() throws Exception {
        var f = retail(); var mmd = f.programs().r11b();
        assertEquals(0x206084L, f.assets().view(OBJECT_INDEX).bytes().unsignedLong((0x0D - 1) * 4));
        assertArrayEquals(HexFormat.of().parseHex("00206084"), mmd.readFile(0x3C68, 4));
        assertArrayEquals(HexFormat.of().parseHex("0020ce22"), mmd.readFile(0x3D08, 4));
        assertArrayEquals(HexFormat.of().parseHex("700010280024303b00124ebb000e4eb9002038f64ef9002076d6"),
                mmd.readFile(0x6084, 26));
        assertArrayEquals(HexFormat.of().parseHex("0058009200a200ac"), mmd.readFile(0x609E, 8));
        int[] routines = {0x2060F6, 0x206130, 0x206140, 0x20614A};
        var directory = CdAssetBytes.copyOf(mmd.readFile(0x609E, 8));
        for (int i = 0; i < routines.length; i++)
            assertEquals(routines[i], 0x20609E + (short) directory.unsignedWord(2 * i));
        assertArrayEquals(HexFormat.of().parseHex("217c0021d6c40004"), mmd.readFile(0x60FA, 8));
        assertEquals(0x21D6C4, MAP_SPR_TUNNEL_DOOR.range().nativeAddress());
        assertArrayEquals(HexFormat.of().parseHex("700c4eb90020cf72"), mmd.readFile(0x6128, 8));
        assertArrayEquals(HexFormat.of().parseHex("43f90020cf94"), mmd.readFile(0xCF72, 6));
        // ID35's vertical owner is distinct; no routine dispatch or runtime object is executed.
    }

    @Test void completeMappingKeepsUnsortedTargetsAllFourPiecesAndBothRealZeroBytes() throws Exception {
        var a = retail().assets(); var mapping = a.view(MAP_SPR_TUNNEL_DOOR).bytes();
        assertEquals(28, NATIVE_MAPPING.length); assertArrayEquals(NATIVE_MAPPING, mapping.copy());
        var visual = CdObjectVisualAssets.decode(mapping.copy(), 2, a.view(ANI_TUNNEL_DOOR).bytes().copy(), 1, 12);
        assertEquals(0, visual.mappingTableOffset()); assertArrayEquals(NATIVE_MAPPING, visual.mappings().copy());
        int[] roots = {16, 4}; int[] ends = {28, 16};
        int[][][] literalPieces = {{{-8, 12, 6, -24}, {-8, 4, 10, 8}}, {{-48, 3, 0, -28}, {-16, 1, 4, -28}}};
        int[][] widths = {{4, 2}, {1, 1}}; int[][] heights = {{1, 1}, {4, 2}};
        boolean[] selected = new boolean[12]; int count = 0;
        for (int frame = 0; frame < roots.length; frame++) {
            var body = visual.mappingFrames().get(frame);
            assertEquals(roots[frame], mapping.unsignedWord(2 * frame));
            assertEquals(roots[frame], body.relativeOffset()); assertEquals(2, body.pieces().size());
            assertEquals(11, body.bytes().length());
            assertArrayEquals(mapping.slice(roots[frame], 11).copy(), body.bytes().copy());
            assertEquals(roots[frame] + 12, ends[frame]); assertEquals(0, mapping.unsignedByte(ends[frame] - 1));
            for (int i = 0; i < 2; i++) {
                int[] p = literalPieces[frame][i]; var piece = body.pieces().get(i);
                assertEquals(new CdObjectVisualAssets.Piece(p[0], p[1], p[2], p[3]), piece);
                assertEquals(widths[frame][i], piece.widthTiles()); assertEquals(heights[frame][i], piece.heightTiles());
                for (int tile = piece.tileOffset(); tile < piece.tileOffset() + piece.tileCount(); tile++) selected[tile] = true;
                count++;
            }
        }
        assertEquals(4, count); for (int tile = 0; tile < selected.length; tile++) assertTrue(selected[tile], "source tile=" + tile);
        assertThrows(IndexOutOfBoundsException.class, () -> visual.mappingFrames().get(2));
        assertThrows(UnsupportedOperationException.class, () -> visual.mappingFrames().clear());
        assertThrows(UnsupportedOperationException.class, () -> visual.mappingFrames().getFirst().pieces().clear());
    }

    @Test void animationAbsoluteLeaSelectsTheCompleteLaterScriptOverTheIdenticalEarlierCopy() throws Exception {
        var f = retail(); var mmd = f.programs().r11b(); var animation = f.assets().view(ANI_TUNNEL_DOOR).bytes();
        assertEquals(14, NATIVE_ANIMATION.length); assertArrayEquals(NATIVE_ANIMATION, animation.copy());
        assertArrayEquals(NATIVE_ANIMATION, mmd.readFile(0xCEDE, 14));
        assertArrayEquals(HexFormat.of().parseHex("43f90021d6b66000f9b6"), mmd.readFile(0x6140, 10));
        assertEquals(0x21D6B6, ANI_TUNNEL_DOOR.range().nativeAddress());
        assertNotEquals(0x20CEDE, ANI_TUNNEL_DOOR.range().nativeAddress());
        assertEquals(0x205AFE, 0x206148 + (short) 0xF9B6);
        var visual = CdObjectVisualAssets.decode(f.assets().view(MAP_SPR_TUNNEL_DOOR).bytes().copy(), 2,
                animation.copy(), 1, 12);
        assertArrayEquals(NATIVE_ANIMATION, visual.animationTable().copy()); assertEquals(1, visual.animations().size());
        var script = visual.animations().getFirst(); assertEquals(2, script.relativeOffset()); assertEquals(0, script.delay());
        assertEquals(List.of(0, 0, 1, 1, 1, 1, 1, 1, 1, 1), script.frameBytes());
        assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC, script.terminal());
        assertArrayEquals(Arrays.copyOfRange(NATIVE_ANIMATION, 2, 14), script.bytes().copy());
        assertThrows(IndexOutOfBoundsException.class, () -> visual.animations().get(1));
        assertThrows(UnsupportedOperationException.class, () -> script.frameBytes().clear());
        // Decoding a script is not animator execution, routine advancement or a normal Main return.
    }

    @Test void unchangedSeventyTwoByteTileIncludeHasOneFiniteSelectorCWordWithNativePriority() throws Exception {
        var ids = retail().assets().view(OBJECT_TILE_IDS).bytes();
        assertEquals(72, NATIVE_TILE_IDS.length); assertArrayEquals(NATIVE_TILE_IDS, ids.copy());
        assertEquals("7f6759c3ab336c2eee70a2fea5128e9920c86d418590ae557e0310948c563da1", sha(ids.copy()));
        int[] roots = {36, 38, 40, 42, 44, 46, 56, 50, 54, 52, 48, 58, 60, 62, 64, 66, 68, 70};
        for (int selector = 0; selector < roots.length; selector++) {
            assertEquals(roots[selector], ids.unsignedWord(2 * selector));
            assertEquals(roots[selector] + 2, CdObjectTileIds.endOfBody(ids, selector));
        }
        int root = ids.unsignedWord(0x0C * 2); assertEquals(60, root);
        assertEquals(0x20CFD0, OBJECT_TILE_IDS.range().nativeAddress() + root);
        assertEquals(62, CdObjectTileIds.endOfBody(ids, 0x0C)); assertEquals(0x8328, ids.unsignedWord(root));
        assertEquals(0x8000, ids.unsignedWord(root) & 0x8000); assertEquals(0x6500, (ids.unsignedWord(root) & 0x7FF) * 32);
        assertEquals(0x20CFDC, OBJECT_TILE_IDS.range().endExclusive());
        assertThrows(IndexOutOfBoundsException.class, () -> ids.unsignedWord(72));
        assertThrows(IllegalArgumentException.class, () -> CdObjectTileIds.endOfBody(ids, -1));
        assertThrows(IllegalArgumentException.class, () -> CdObjectTileIds.endOfBody(ids, 18));
        // The next selector owns the following word; there is no Door subtype2=1 body word or A-style padding.
    }

    @Test void existingTwelveTileArtReaderAndStandardPlcEntryRemainExact() throws Exception {
        var f = retail(); var a = f.assets(); var art = a.view(ART_TUNNEL_DOOR).bytes();
        assertEquals(154, NATIVE_ART.length); assertArrayEquals(NATIVE_ART, art.copy()); assertEquals(0x800C, art.unsignedWord(0));
        assertEquals("cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b", sha(art.copy()));
        assertEquals(0x376BA, ART_TUNNEL_DOOR.fileOffset()); assertEquals(154, ART_TUNNEL_DOOR.length());
        var reader = ART_TUNNEL_DOOR.readerProfile().orElseThrow();
        assertTrue(ART_TUNNEL_DOOR.isPlcSource()); assertEquals(384, reader.outputBytes()); assertEquals(12, reader.outputBytes() / 32);
        assertEquals(154, reader.sourceBlobRange().length()); assertEquals(154, reader.javaInputRange().length());
        assertEquals(154, reader.nativeInputRange().length()); assertEquals(153, reader.javaConsumedInputBytes());
        for (var kind : CdR11bAssets.ReaderKind.values()) assertArrayEquals(NATIVE_ART, a.readerView(ART_TUNNEL_DOOR, kind).bytes().copy());
        var plan = a.loadPlan(); assertEquals(19, plan.plcs().size()); assertEquals(4, plan.sections().size());
        var standard = plan.plcs().get(1); assertEquals(0xF1C6, standard.bodyRange().fileOffset()); assertEquals(15, standard.entries().size());
        var row = standard.entries().getFirst(); assertSame(ART_TUNNEL_DOOR, row.source());
        assertEquals(ART_TUNNEL_DOOR.range(), row.sourceRange()); assertEquals(0x6500, row.vramAddress());
        assertArrayEquals(HexFormat.of().parseHex("000e002376ba6500"), f.programs().r11b().readFile(0xF1C6, 8));
        // Only source/reader/PLC metadata is observed; no art decode, bank, upload or current pixels are produced.
    }

    @Test void rawViewsStayDetachedReadOnlyAndRejectUnsupportedNamedRanges() throws Exception {
        var f = retail(); var a = f.assets(); byte[] before = f.programs().r11b().readFile(0, 262144);
        for (var key : DOOR_TABLES) {
            var view = a.view(key); var bytes = view.bytes(); byte[] original = bytes.copy();
            byte[] changed = bytes.copy(); changed[0] ^= 0x55;
            assertNotSame(view, a.view(key)); assertArrayEquals(original, bytes.copy()); assertArrayEquals(original, a.view(key).bytes().copy());
            assertThrows(ReadOnlyBufferException.class, () -> bytes.buffer().put(0, (byte) 0));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedByte(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedByte(key.length()));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedWord(key.length() - 1));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.slice(key.length(), 1));
            assertEquals(0, bytes.slice(key.length(), 0).length());
            assertThrows(IllegalArgumentException.class, () -> new CdR11bAssets.View(key, key.range(), bytes.slice(0, key.length() - 1)));
            var shifted = new CdR11bAssets.FileRange(CdNativeFile.R11B, CdMmdImage.Domain.ORIGINAL_WORD_RAM, key.fileOffset() + 2, key.length());
            assertThrows(IllegalArgumentException.class, () -> new CdR11bAssets.View(key, shifted, bytes));
        }
        assertThrows(NullPointerException.class, () -> a.view(null));
        assertArrayEquals(before, f.programs().r11b().readFile(0, 262144)); assertEquals(PAST_SHA, sha(before));
    }

    @Test void malformedConstructedMappingsRetainTheExistingFiniteDecoderAndNeverWriteInputs() throws Exception {
        byte[] beforeIndex = NATIVE_MAPPING.clone(); beforeIndex[1] = 2;
        byte[] outside = NATIVE_MAPPING.clone(); outside[0] = (byte) 0xFF;
        byte[] crossing = NATIVE_MAPPING.clone(); crossing[4] = 3;
        byte[] firstEven = NATIVE_MAPPING.clone(); firstEven[15] = 1;
        byte[] finalEven = NATIVE_MAPPING.clone(); finalEven[27] = 1;
        for (byte[] mapping : new byte[][]{beforeIndex, outside, crossing, firstEven, finalEven, Arrays.copyOf(NATIVE_MAPPING, 26)})
            assertVisualRejectedWithoutWrites(mapping, NATIVE_ANIMATION.clone(), 12);
        assertVisualRejectedWithoutWrites(NATIVE_MAPPING.clone(), NATIVE_ANIMATION.clone(), 11);
        assertArrayEquals(NATIVE_MAPPING, retail().assets().view(MAP_SPR_TUNNEL_DOOR).bytes().copy());
    }

    @Test void malformedConstructedScriptsRetainNativeFrameAndTerminalBoundsWithoutInputWrites() throws Exception {
        byte[] beforeIndex = NATIVE_ANIMATION.clone(); beforeIndex[1] = 0;
        byte[] gap = NATIVE_ANIMATION.clone(); gap[1] = 4;
        byte[] absentFrame = NATIVE_ANIMATION.clone(); absentFrame[3] = 2;
        byte[] unknownCommand = NATIVE_ANIMATION.clone(); unknownCommand[13] = (byte) 0xFD;
        byte[] noTerminal = Arrays.copyOf(NATIVE_ANIMATION, 13);
        byte[] falseTail = Arrays.copyOf(NATIVE_ANIMATION, 15); falseTail[14] = 1;
        for (byte[] animation : new byte[][]{beforeIndex, gap, absentFrame, unknownCommand, noTerminal, falseTail})
            assertVisualRejectedWithoutWrites(NATIVE_MAPPING.clone(), animation, 12);
        assertArrayEquals(NATIVE_ANIMATION, retail().assets().view(ANI_TUNNEL_DOOR).bytes().copy());
    }

    @Test void rawMetadataAndPureVisualReadsNeverEagerlyDecodeArtOrReplaceAggregateOwners() throws Exception {
        var f = retail(); byte[] before = f.programs().r11b().readFile(0, 262144);
        try (var decodes = mockStatic(CdNemesisAsset.class, CALLS_REAL_METHODS)) {
            var a = CdR11bAssets.fromPrograms(f.programs());
            var identity = a.identity(); var terrain = a.terrain(); var plan = a.loadPlan(); var placements = a.placements();
            var sonic = a.sonic(); var player = a.player(); var blocks = a.blocksDecode(); var tiles = a.levelTilesDecode();
            for (var key : DOOR_TABLES) a.view(key);
            CdObjectVisualAssets.decode(a.view(MAP_SPR_TUNNEL_DOOR).bytes().copy(), 2, a.view(ANI_TUNNEL_DOOR).bytes().copy(), 1, 12);
            a.view(OBJECT_TILE_IDS); a.view(ART_TUNNEL_DOOR);
            for (var kind : CdR11bAssets.ReaderKind.values()) a.readerView(ART_TUNNEL_DOOR, kind);
            assertSame(identity, a.identity()); assertSame(terrain, a.terrain()); assertSame(plan, a.loadPlan());
            assertSame(placements, a.placements()); assertSame(sonic, a.sonic()); assertSame(player, a.player());
            assertSame(blocks, a.blocksDecode()); assertSame(tiles, a.levelTilesDecode());
            decodes.verify(() -> CdNemesisAsset.decode(any(byte[].class), eq(5280), eq(5280)));
            decodes.verify(() -> CdNemesisAsset.decode(any(byte[].class), eq(23872), eq(23872)));
            decodes.verifyNoMoreInteractions();
        }
        assertArrayEquals(before, f.programs().r11b().readFile(0, 262144));
    }

    @Test void foreignSourceClaimsAndValueRangesCannotBroadenTheUsaOriginalBContract() throws Exception {
        var f = retail(); var source = f.programs().source();
        assertEquals(CdReleaseVariant.USA, f.assets().identity().variant());
        assertEquals(CdStagePayload.R11B.stage(), f.assets().identity().stage());
        assertEquals(CdRetailReleases.USA.normalizedSha256(), source.wholeDiscSha256());
        var foreign = mock(CdTimeWarpPrograms.class);
        when(foreign.source()).thenReturn(new CdTimeWarpPrograms.Source(CdReleaseVariant.JAPAN,
                source.wholeDiscSha256(), source.warpSha256(), source.r11bSha256()));
        assertThrows(IOException.class, () -> CdR11bAssets.fromPrograms(foreign));
        verify(foreign, never()).r11b(); verify(foreign, never()).warp();
        assertThrows(IllegalArgumentException.class, () -> new CdR11bAssets.FileRange(
                CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x1D6B6, 14));
        assertThrows(IllegalArgumentException.class, () -> new CdR11bAssets.FileRange(
                CdNativeFile.R11B, CdMmdImage.Domain.RELOCATED_COPY, 0x1D6B6, 14));
        assertThrows(IllegalArgumentException.class, () -> new CdR11bAssets.FileRange(
                CdNativeFile.R11B, CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x3FFF8, 14));
        assertEquals(PAST_SHA, sha(f.programs().r11b().readFile(0, 262144)));
    }

    private static void assertVisualRejectedWithoutWrites(byte[] mapping, byte[] animation, int artTiles) {
        byte[] mappingBefore = mapping.clone(); byte[] animationBefore = animation.clone();
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(mapping, 2, animation, 1, artTiles));
        assertArrayEquals(mappingBefore, mapping); assertArrayEquals(animationBefore, animation);
    }

    private String retainedRoot;
    private Fixture retained;
    private record Fixture(CdTimeWarpPrograms programs, CdR11bAssets assets) { }
    private Fixture retail() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assertNotNull(root, "Explicit Tunnel Door source cases require the configured original USA media root");
        assertFalse(root.isBlank(), "The original USA media root must not be blank");
        if (retained != null && root.equals(retainedRoot)) return retained;
        Path usa = Path.of(root).resolve("USA"); assertTrue(Files.isDirectory(usa), "Original USA media directory must exist");
        Path cue;
        try (var paths = Files.list(usa)) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured USA source must name exactly one existing CUE"); cue = cues.getFirst();
        }
        CdTimeWarpPrograms programs;
        try (var owner = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            programs = CdTimeWarpPrograms.fromPrograms(owner);
        }
        var assets = CdR11bAssets.fromPrograms(programs);
        assertEquals(CdRetailReleases.USA.normalizedSha256(), programs.source().wholeDiscSha256());
        assertEquals(PAST_SHA, assets.identity().mmdSha256());
        retained = new Fixture(programs, assets); retainedRoot = root; return retained;
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
