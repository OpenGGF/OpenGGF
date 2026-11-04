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
 * Complete original USA R11B Monitor/Time Post raw tables and the existing pure format decoder.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Monitor and Time Post/Main.asm:179-183,300-305,363-367,644-650;
 * complete Data/Animations.asm:1-50; complete Data/Mappings.asm:1-176;
 * Palmtree Panic/Data (Act 1 Past).asm:74-75,370-371.
 * Complete literals were expanded independently from pinned declarations and matched to original
 * media. No new visual owner, art bank, source, decoder, residency or runtime body is installed.
 * Actual USA media is a hard prerequisite; every Java case is initially UNRUN.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11bMonitorTimeAssets {
    private static final String PAST_SHA =
            "b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265";
    private static final byte[] NATIVE_ANIMATION = HexFormat.of().parseHex(
            "00140020002c003800440050005c00680074007e"
                    + "01100000080000090000ff0001100101080101090101ff00"
                    + "01100202080202090202ff0001100303080303090303ff00"
                    + "01100404080404090404ff0001100505080505090505ff00"
                    + "01100606080606090606ff0001100707080707090707ff00"
                    + "010a0e0f0e0b0e0f0eff010c0e0f0e0d0e0f0eff");
    private static final byte[] NATIVE_MAPPING = HexFormat.of().parseHex(
            "0028003e0054006a0080009600ac00c200d800ee0104011e01380152016c018601a001b001b601bc"
                    + "04f6050012f8f0060000f0f006080000080c0006f000"
                    + "04f6050016f8f0060000f0f006080000080c0006f000"
                    + "04f605001af8f0060000f0f006080000080c0006f000"
                    + "04f605001ef8f0060000f0f006080000080c0006f000"
                    + "04f6050022f8f0060000f0f006080000080c0006f000"
                    + "04f6050026f8f0060000f0f006080000080c0006f000"
                    + "04f605002af8f0060000f0f006080000080c0006f000"
                    + "04f605002ef8f0060000f0f006080000080c0006f000"
                    + "04f6050048f8f0060000f0f006080000080c0006f000"
                    + "04f6050848f8f0060000f0f006080000080c0006f000"
                    + "05d80d0032f0e803004cf8e803084c0008010050f80801085000"
                    + "05d80d0832f0e803004cf8e803084c0008010050f80801085000"
                    + "05d80d003af0e803004cf8e803084c0008010050f80801085000"
                    + "05d80d083af0e803004cf8e803084c0008010050f80801085000"
                    + "05d8050042f8e803004cf8e803084c0008010050f80801085000"
                    + "05d8010046fce803004cf8e803084c0008010050f80801085000"
                    + "03f0060000f0f006080000080c0006f001000d000af001f80d0032f001f80d003af0");

    @BeforeAll void requireAuthenticatedOriginalUsaMedia() throws Exception {
        retail();
    }

    @Test void oneRawAnimationAppendsWithoutChangingExistingMappingArtReadersOrPlcSources() throws Exception {
        var a = retail().assets(); var key = ANI_MONITOR; var view = a.view(key);
        assertEquals(125, CdR11bAssets.Asset.values().length); assertEquals(89, key.ordinal());
        assertEquals("Ani_Monitor", key.nativeLabel()); assertEquals(0xA81E, key.fileOffset());
        assertEquals(136, key.length()); assertSame(key, view.asset()); assertEquals(key.range(), view.range());
        assertEquals(CdNativeFile.R11B, view.range().file());
        assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, view.range().domain());
        assertEquals(0x20A81E, view.range().nativeAddress()); assertEquals(0x20A8A6, view.range().endExclusive());
        assertEquals("c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36", sha(view.bytes().copy()));
        assertFalse(key.isPlcSource()); assertTrue(key.readerProfile().isEmpty());
        for (var kind : CdR11bAssets.ReaderKind.values())
            assertThrows(IllegalArgumentException.class, () -> a.readerView(key, kind));
        assertThrows(IllegalArgumentException.class,
                () -> new CdR11bLoadPlan.PlcEntry(key, key.range(), 0xB500));
        assertEquals(76, MAP_SPR_MONITOR_TIME.ordinal()); assertEquals(47, ART_MONITOR_TIME_POSTS.ordinal());
        assertEquals(87, ANI_TUNNEL_DOOR.ordinal()); assertEquals(88, MAP_SPR_TUNNEL_DOOR.ordinal());
        assertEquals(0xA8A6, MAP_SPR_MONITOR_TIME.fileOffset()); assertEquals(450, MAP_SPR_MONITOR_TIME.length());
        assertEquals(37, Arrays.stream(CdR11bAssets.Asset.values()).filter(k -> k.readerProfile().isPresent()).count());
        assertEquals(36, Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
    }

    @Test void actualMonitorAndTimePostOperandsSelectTheSameCompletePastTables() throws Exception {
        var f = retail(); var mmd = f.programs().r11b();
        assertEquals(0x20A4BAL, f.assets().view(OBJECT_INDEX).bytes().unsignedLong((0x19 - 1) * 4));
        assertArrayEquals(HexFormat.of().parseHex("0020a4ba"), mmd.readFile(0x3C98, 4));
        for (int offset : new int[]{0xA2D4, 0xA34C, 0xA500})
            assertArrayEquals(HexFormat.of().parseHex("217c0020a8a60004"), mmd.readFile(offset, 8));
        for (int offset : new int[]{0xA42C, 0xA5AC})
            assertArrayEquals(HexFormat.of().parseHex("43f90020a81e"), mmd.readFile(offset, 6));
        assertArrayEquals(HexFormat.of().parseHex("317c05a80002"), mmd.readFile(0xA508, 6));
        assertEquals(MAP_SPR_MONITOR_TIME.range().nativeAddress(), ANI_MONITOR.range().endExclusive());
        // These are retained instruction operands, not an executed dispatch or an assumed live subtype.
    }

    @Test void completeTwentyFrameMappingRetainsEveryPieceRealAlignmentByteAndTileBound() throws Exception {
        var a = retail().assets(); var mapping = a.view(MAP_SPR_MONITOR_TIME).bytes(); var visual = visual(a);
        assertEquals(450, NATIVE_MAPPING.length); assertArrayEquals(NATIVE_MAPPING, mapping.copy());
        assertEquals("00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca", sha(mapping.copy()));
        assertEquals(0, visual.mappingTableOffset()); assertArrayEquals(NATIVE_MAPPING, visual.mappings().copy());
        int[] roots = {40, 62, 84, 106, 128, 150, 172, 194, 216, 238,
                260, 286, 312, 338, 364, 390, 416, 432, 438, 444};
        int[] counts = {4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 3, 1, 1, 1};
        boolean[] selected = new boolean[82]; int pieces = 0;
        for (int frame = 0; frame < roots.length; frame++) {
            var body = visual.mappingFrames().get(frame); int consumed = 1 + counts[frame] * 5;
            int end = frame + 1 < roots.length ? roots[frame + 1] : 450;
            assertEquals(roots[frame], mapping.unsignedWord(frame * 2));
            assertEquals(roots[frame], body.relativeOffset()); assertEquals(counts[frame], body.pieces().size());
            assertEquals(expectedPieces(frame), body.pieces(), "frame=" + frame);
            assertEquals(consumed, body.bytes().length());
            assertArrayEquals(mapping.slice(roots[frame], consumed).copy(), body.bytes().copy());
            assertEquals(consumed + (frame < 10 ? 1 : 0), end - roots[frame]);
            if (frame < 10) assertEquals(0, mapping.unsignedByte(end - 1));
            for (var piece : body.pieces()) {
                for (int tile = piece.tileOffset(); tile < piece.tileOffset() + piece.tileCount(); tile++) selected[tile] = true;
                pieces++;
            }
        }
        assertEquals(76, pieces);
        for (int tile = 0; tile < selected.length; tile++) assertTrue(selected[tile], "source tile=" + tile);
        assertThrows(IndexOutOfBoundsException.class, () -> visual.mappingFrames().get(20));
        assertThrows(UnsupportedOperationException.class, () -> visual.mappingFrames().clear());
        assertThrows(UnsupportedOperationException.class, () -> visual.mappingFrames().getFirst().pieces().clear());
    }

    @Test void completeTenScriptsKeepAllNativeFramesStrictFfLoopsAndRealEvenBytes() throws Exception {
        var a = retail().assets(); var animation = a.view(ANI_MONITOR).bytes(); var visual = visual(a);
        assertEquals(136, NATIVE_ANIMATION.length); assertArrayEquals(NATIVE_ANIMATION, animation.copy());
        assertArrayEquals(NATIVE_ANIMATION, visual.animationTable().copy()); assertEquals(10, visual.animations().size());
        int[] roots = {20, 32, 44, 56, 68, 80, 92, 104, 116, 126};
        for (int i = 0; i < roots.length; i++) {
            var script = visual.animations().get(i); int end = i + 1 < roots.length ? roots[i + 1] : 136;
            assertEquals(roots[i], animation.unsignedWord(i * 2)); assertEquals(roots[i], script.relativeOffset());
            assertEquals(1, script.delay()); assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF, script.terminal());
            List<Integer> expected = i < 8 ? List.of(16, i, i, 8, i, i, 9, i, i)
                    : i == 8 ? List.of(10, 14, 15, 14, 11, 14, 15, 14)
                    : List.of(12, 14, 15, 14, 13, 14, 15, 14);
            assertEquals(expected, script.frameBytes()); assertEquals(i < 8 ? 12 : 10, script.bytes().length());
            assertArrayEquals(animation.slice(roots[i], end - roots[i]).copy(), script.bytes().copy());
            assertEquals(0xFF, animation.unsignedByte(end - (i < 8 ? 2 : 1)));
            if (i < 8) assertEquals(0, animation.unsignedByte(end - 1));
            assertThrows(UnsupportedOperationException.class, () -> script.frameBytes().clear());
        }
        assertThrows(IndexOutOfBoundsException.class, () -> visual.animations().get(10));
        // The post scripts are ten bytes each; no final alignment or adjacent mapping byte is appended.
    }

    @Test void existingEightyTwoTileSourceAndFiniteReadersKeepTheSameStandardPlcEntry() throws Exception {
        var f = retail(); var a = f.assets(); var art = a.view(ART_MONITOR_TIME_POSTS).bytes();
        assertEquals(0x356F6, ART_MONITOR_TIME_POSTS.fileOffset()); assertEquals(1540, art.length());
        assertEquals(0x8052, art.unsignedWord(0));
        assertEquals("ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a", sha(art.copy()));
        var reader = ART_MONITOR_TIME_POSTS.readerProfile().orElseThrow();
        assertTrue(ART_MONITOR_TIME_POSTS.isPlcSource()); assertEquals(2624, reader.outputBytes());
        assertEquals(82, reader.outputBytes() / 32); assertEquals(1540, reader.sourceBlobRange().length());
        assertEquals(1541, reader.javaInputRange().length()); assertEquals(1541, reader.nativeInputRange().length());
        assertEquals(1541, reader.javaConsumedInputBytes());
        for (var kind : CdR11bAssets.ReaderKind.values()) {
            var bytes = a.readerView(ART_MONITOR_TIME_POSTS, kind).bytes();
            assertEquals(1541, bytes.length()); assertArrayEquals(art.copy(), bytes.slice(0, 1540).copy());
            assertEquals(0, bytes.unsignedByte(1540));
            assertEquals("a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902", sha(bytes.copy()));
        }
        var plan = a.loadPlan(); assertEquals(19, plan.plcs().size()); assertEquals(4, plan.sections().size());
        var standard = plan.plcs().get(1); assertEquals(0xF1C6, standard.bodyRange().fileOffset()); assertEquals(15, standard.entries().size());
        var row = standard.entries().get(10); assertSame(ART_MONITOR_TIME_POSTS, row.source());
        assertEquals(ART_MONITOR_TIME_POSTS.range(), row.sourceRange()); assertEquals(0xB500, row.vramAddress());
        assertArrayEquals(HexFormat.of().parseHex("002356f6b500"), f.programs().r11b().readFile(0xF204, 6));
        // Source/reader/PLC declarations do not establish loaded or known current pixels.
    }

    @Test void finiteRawViewsAreDetachedReadOnlyAndCannotBroadenNamedRanges() throws Exception {
        var f = retail(); var a = f.assets(); byte[] before = f.programs().r11b().readFile(0, 262144);
        for (var key : List.of(ANI_MONITOR, MAP_SPR_MONITOR_TIME)) {
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
            var shifted = new CdR11bAssets.FileRange(CdNativeFile.R11B, CdMmdImage.Domain.ORIGINAL_WORD_RAM,
                    key.fileOffset() + 2, key.length());
            assertThrows(IllegalArgumentException.class, () -> new CdR11bAssets.View(key, shifted, bytes));
        }
        assertThrows(NullPointerException.class, () -> a.view(null));
        assertArrayEquals(before, f.programs().r11b().readFile(0, 262144)); assertEquals(PAST_SHA, sha(before));
    }

    @Test void malformedMappingDirectoriesBodiesAlignmentAndTileBoundsStopWithoutInputWrites() {
        byte[] beforeIndex = NATIVE_MAPPING.clone(); beforeIndex[1] = 38;
        byte[] gap = NATIVE_MAPPING.clone(); gap[1] = 42;
        byte[] outside = NATIVE_MAPPING.clone(); outside[0] = (byte) 0xFF;
        byte[] crossing = NATIVE_MAPPING.clone(); crossing[40] = 5;
        byte[] falseEven = NATIVE_MAPPING.clone(); falseEven[61] = 1;
        for (byte[] mapping : new byte[][]{beforeIndex, gap, outside, crossing, falseEven, Arrays.copyOf(NATIVE_MAPPING, 449)})
            assertVisualRejectedWithoutWrites(mapping, NATIVE_ANIMATION.clone(), 82);
        assertVisualRejectedWithoutWrites(NATIVE_MAPPING.clone(), NATIVE_ANIMATION.clone(), 81);
    }

    @Test void malformedAnimationDirectoriesFramesCommandsAndFiniteEndsStopWithoutInputWrites() {
        byte[] beforeIndex = NATIVE_ANIMATION.clone(); beforeIndex[1] = 18;
        byte[] gap = NATIVE_ANIMATION.clone(); gap[1] = 22;
        byte[] absentFrame = NATIVE_ANIMATION.clone(); absentFrame[21] = 20;
        byte[] unknownCommand = NATIVE_ANIMATION.clone(); unknownCommand[30] = (byte) 0xFD;
        byte[] falseEven = NATIVE_ANIMATION.clone(); falseEven[31] = 1;
        byte[] falseTail = Arrays.copyOf(NATIVE_ANIMATION, 137); falseTail[136] = 1;
        for (byte[] animation : new byte[][]{beforeIndex, gap, absentFrame, unknownCommand, falseEven,
                Arrays.copyOf(NATIVE_ANIMATION, 135), falseTail})
            assertVisualRejectedWithoutWrites(NATIVE_MAPPING.clone(), animation, 82);
    }

    @Test void rawViewsAndPureTableDecodeNeverEagerlyDecodeArtOrReplaceAggregateOwners() throws Exception {
        var f = retail(); byte[] before = f.programs().r11b().readFile(0, 262144);
        try (var decodes = mockStatic(CdNemesisAsset.class, CALLS_REAL_METHODS)) {
            var a = CdR11bAssets.fromPrograms(f.programs());
            var identity = a.identity(); var terrain = a.terrain(); var plan = a.loadPlan(); var placements = a.placements();
            var sonic = a.sonic(); var player = a.player(); var blocks = a.blocksDecode(); var tiles = a.levelTilesDecode();
            a.view(ANI_MONITOR); a.view(MAP_SPR_MONITOR_TIME); visual(a); a.view(ART_MONITOR_TIME_POSTS);
            for (var kind : CdR11bAssets.ReaderKind.values()) a.readerView(ART_MONITOR_TIME_POSTS, kind);
            assertSame(identity, a.identity()); assertSame(terrain, a.terrain()); assertSame(plan, a.loadPlan());
            assertSame(placements, a.placements()); assertSame(sonic, a.sonic()); assertSame(player, a.player());
            assertSame(blocks, a.blocksDecode()); assertSame(tiles, a.levelTilesDecode());
            decodes.verify(() -> CdNemesisAsset.decode(any(byte[].class), eq(5280), eq(5280)));
            decodes.verify(() -> CdNemesisAsset.decode(any(byte[].class), eq(23872), eq(23872)));
            decodes.verifyNoMoreInteractions();
        }
        assertArrayEquals(before, f.programs().r11b().readFile(0, 262144));
    }

    @Test void foreignClaimsAndRangeValuesCannotAuthenticateAnotherVariantOrRelocation() throws Exception {
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
                CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0xA81E, 136));
        assertThrows(IllegalArgumentException.class, () -> new CdR11bAssets.FileRange(
                CdNativeFile.R11B, CdMmdImage.Domain.RELOCATED_COPY, 0xA81E, 136));
        assertThrows(IllegalArgumentException.class, () -> new CdR11bAssets.FileRange(
                CdNativeFile.R11B, CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x3FFF8, 136));
        assertEquals(PAST_SHA, sha(f.programs().r11b().readFile(0, 262144)));
    }

    private static List<CdObjectVisualAssets.Piece> expectedPieces(int frame) {
        var left = new CdObjectVisualAssets.Piece(-16, 6, 0, -16);
        var right = new CdObjectVisualAssets.Piece(-16, 6, 0x800, 0);
        var bottom = new CdObjectVisualAssets.Piece(8, 12, 6, -16);
        if (frame < 10) {
            int tile = frame < 8 ? 0x12 + 4 * frame : frame == 8 ? 0x48 : 0x848;
            return List.of(new CdObjectVisualAssets.Piece(-10, 5, tile, -8), left, right, bottom);
        }
        if (frame < 16) {
            int[] tiles = {0x32, 0x832, 0x3A, 0x83A, 0x42, 0x46};
            var top = new CdObjectVisualAssets.Piece(-40, frame < 14 ? 13 : frame == 14 ? 5 : 1,
                    tiles[frame - 10], frame < 14 ? -16 : frame == 14 ? -8 : -4);
            return List.of(top, new CdObjectVisualAssets.Piece(-24, 3, 0x4C, -8),
                    new CdObjectVisualAssets.Piece(-24, 3, 0x84C, 0),
                    new CdObjectVisualAssets.Piece(8, 1, 0x50, -8), new CdObjectVisualAssets.Piece(8, 1, 0x850, 0));
        }
        if (frame == 16) return List.of(left, right, bottom);
        if (frame == 17) return List.of(new CdObjectVisualAssets.Piece(0, 13, 10, -16));
        return List.of(new CdObjectVisualAssets.Piece(-8, 13, frame == 18 ? 0x32 : 0x3A, -16));
    }

    private static CdObjectVisualAssets visual(CdR11bAssets owner) {
        return CdObjectVisualAssets.decode(owner.view(MAP_SPR_MONITOR_TIME).bytes().copy(), 20,
                owner.view(ANI_MONITOR).bytes().copy(), 10, 82);
    }
    private static void assertVisualRejectedWithoutWrites(byte[] mapping, byte[] animation, int artTiles) {
        byte[] mappingBefore = mapping.clone(); byte[] animationBefore = animation.clone();
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(mapping, 20, animation, 10, artTiles));
        assertArrayEquals(mappingBefore, mapping); assertArrayEquals(animationBefore, animation);
    }

    private String retainedRoot;
    private Fixture retained;
    private record Fixture(CdTimeWarpPrograms programs, CdR11bAssets assets) { }
    private Fixture retail() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assertNotNull(root, "Explicit Monitor/Time Post source cases require the configured original USA media root");
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
