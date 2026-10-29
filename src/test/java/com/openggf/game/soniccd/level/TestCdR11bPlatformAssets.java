package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.release.CdRetailReleases;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

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

/**
 * Original USA Past Platform raw sources and the unchanged counted mapping decoder only.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * ObjPlatform/Init/Widths/Ranges (Platform/Main.asm:7-86),
 * complete Data/Mappings (Platform 1).asm:1-130 and PLC_Std (Data (Act 1 Past).asm:52-83).
 * Full literal source bytes are independent of the Java decoder; actual media is a hard prerequisite.
 * No object body, Solid/angle callback, current-art publication, World, renderer or native routine executes.
 * All Java discovery, compilation, RED and positive execution are initially UNRUN.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11bPlatformAssets {
    private static final String PAST_SHA =
            "b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265";
    private static final byte[] NATIVE_MAPPING = HexFormat.of().parseHex(
            "0006001c0046"
                    + "04f0050001f0f00508010000050005f0000508050000"
                    + "08f0050001e0f0050001f0f005000100f00500011000050005e000050009f00005000900000508051000"
                    + "0cf0050001d0f0050001e0f0050001f0f005000100f005000110f00500012000050005d000050009e000050009f000050009000005000910000508052000");
    private static final byte[] NATIVE_WIDTHS = HexFormat.of().parseHex("10203000");
    private static final byte[] NATIVE_RANGES = HexFormat.of().parseHex("02030406");
    private static final List<CdR11bAssets.Asset> PLATFORM_TABLES =
            List.of(PLATFORM_MAPPINGS, PLATFORM_WIDTHS, PLATFORM_RANGES);

    @BeforeAll void requireAuthenticatedOriginalUsaMedia() throws Exception {
        retail();
    }

    @Test void threeRawViewsAppendWithExactMetadataWithoutPlcOrReaderOwnership() throws Exception {
        var a = retail().assets();
        String[] labels = {"MapSpr_Platform", "ObjPlatform_Widths", "ObjPlatform_Ranges"};
        int[] offsets = {0xCD1A, 0xC992, 0xC996}; int[] lengths = {132, 4, 4};
        String[] hashes = {
                "a8b6e5695090a558c67e7e776db8c4b642731a7ef7445608264b29f15529afd7",
                "87d0fad66b7f6fe31844c828bbfa818f478c885f0f7672d1bb38d89ed6bfb9d3",
                "ce513997025ae549282209565f4c74aae2258cd6c391acd643d3b7f1b8071c9a"};
        assertEquals(89, CdR11bAssets.Asset.values().length);
        for (int i = 0; i < PLATFORM_TABLES.size(); i++) {
            var key = PLATFORM_TABLES.get(i); var view = a.view(key);
            assertEquals(84 + i, key.ordinal()); assertEquals(labels[i], key.nativeLabel());
            assertEquals(offsets[i], key.fileOffset()); assertEquals(lengths[i], key.length());
            assertSame(key, view.asset()); assertEquals(key.range(), view.range());
            assertEquals(CdNativeFile.R11B, view.range().file());
            assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, view.range().domain());
            assertEquals(0x200000 + offsets[i], view.range().nativeAddress());
            assertEquals(0x200000 + offsets[i] + lengths[i], view.range().endExclusive());
            assertEquals(lengths[i], view.bytes().length()); assertEquals(hashes[i], sha(view.bytes().copy()));
            assertFalse(key.isPlcSource()); assertTrue(key.readerProfile().isEmpty());
            for (var kind : CdR11bAssets.ReaderKind.values())
                assertThrows(IllegalArgumentException.class, () -> a.readerView(key, kind));
            assertThrows(IllegalArgumentException.class,
                    () -> new CdR11bLoadPlan.PlcEntry(key, key.range(), 0x97C0));
        }
        assertEquals(80, SPRING_SPEEDS.ordinal()); assertEquals(83, OBJECT_TILE_IDS.ordinal());
        assertEquals(37, Arrays.stream(CdR11bAssets.Asset.values()).filter(k -> k.readerProfile().isPresent()).count());
        assertEquals(36, Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
    }

    @Test void actualDispatchAndAllMappingAndPcOperandsSelectTheIndependentBRoots() throws Exception {
        var f = retail(); var mmd = f.programs().r11b();
        assertEquals(PAST_SHA, sha(mmd.readFile(0, mmd.fileBytes())));
        assertEquals(0x20C89CL, f.assets().view(OBJECT_INDEX).bytes().unsignedLong((0x21 - 1) * 4));
        assertArrayEquals(HexFormat.of().parseHex("0020c89c"), mmd.readFile(0x3CB8, 4));
        assertArrayEquals(HexFormat.of().parseHex("700010280024303b000e4ebb000a4eb9002038f64e75"),
                mmd.readFile(0xC89C, 22));
        assertArrayEquals(HexFormat.of().parseHex("001600e8"), mmd.readFile(0xC8B2, 4));
        assertEquals(0x20C8C8, 0x20C8B2 + 0x16); assertEquals(0x20C99A, 0x20C8B2 + 0xE8);
        for (int at : new int[]{0xC8EC, 0xC8FC, 0xC90C})
            assertArrayEquals(HexFormat.of().parseHex("203c0020cd1a"), mmd.readFile(at, 6));
        assertEquals(PLATFORM_MAPPINGS.range().nativeAddress(), 0x20CD1A);
        assertArrayEquals(HexFormat.of().parseHex("117b006c0019"), mmd.readFile(0xC924, 6));
        assertArrayEquals(HexFormat.of().parseHex("117b105e002d"), mmd.readFile(0xC936, 6));
        assertEquals(PLATFORM_WIDTHS.range().nativeAddress(), 0x20C926 + (byte) 0x6C);
        assertEquals(PLATFORM_RANGES.range().nativeAddress(), 0x20C938 + (byte) 0x5E);
        // Extension words precede native byte-width/directory reads; neither root is inferred from A offsets.
    }

    @Test void completeNativeMappingPreservesEveryRootPieceAttributeAndActualAlignmentByte() throws Exception {
        var mapping = retail().assets().view(PLATFORM_MAPPINGS).bytes();
        assertArrayEquals(NATIVE_MAPPING, mapping.copy()); assertEquals(132, NATIVE_MAPPING.length);
        var visual = CdObjectVisualAssets.decodeStatic(mapping.copy(), 3, 57);
        assertArrayEquals(NATIVE_MAPPING, visual.mappings().copy()); assertEquals(0, visual.mappingTableOffset());
        assertEquals(0, visual.animationTable().length()); assertTrue(visual.animations().isEmpty());
        int[] roots = {6, 28, 70}; int[] counts = {4, 8, 12};
        int[][] xs = {{-16, 0, -16, 0}, {-32, -16, 0, 16, -32, -16, 0, 16},
                {-48, -32, -16, 0, 16, 32, -48, -32, -16, 0, 16, 32}};
        int[][] tiles = {{1, 0x0801, 5, 0x0805}, {1, 1, 1, 1, 5, 9, 9, 0x0805},
                {1, 1, 1, 1, 1, 1, 5, 9, 9, 9, 9, 0x0805}};
        boolean[] selectedTiles = new boolean[57]; int pieces = 0;
        for (int frame = 0; frame < roots.length; frame++) {
            var body = visual.mappingFrames().get(frame);
            assertEquals(roots[frame], mapping.unsignedWord(frame * 2));
            assertEquals(roots[frame], body.relativeOffset()); assertEquals(counts[frame], body.pieces().size());
            int consumed = 1 + 5 * counts[frame]; assertEquals(consumed, body.bytes().length());
            assertArrayEquals(mapping.slice(roots[frame], consumed).copy(), body.bytes().copy());
            int next = frame < 2 ? roots[frame + 1] : mapping.length();
            assertEquals(roots[frame] + consumed + 1, next); assertEquals(0, mapping.unsignedByte(next - 1));
            for (int pieceIndex = 0; pieceIndex < counts[frame]; pieceIndex++) {
                var piece = body.pieces().get(pieceIndex);
                assertEquals(pieceIndex < counts[frame] / 2 ? -16 : 0, piece.y());
                assertEquals(xs[frame][pieceIndex], piece.x()); assertEquals(5, piece.sizeByte());
                assertEquals(tiles[frame][pieceIndex], piece.rawTileWord());
                assertEquals(2, piece.widthTiles()); assertEquals(2, piece.heightTiles());
                for (int t = piece.tileOffset(); t < piece.tileOffset() + piece.tileCount(); t++) selectedTiles[t] = true;
                pieces++;
            }
        }
        assertEquals(24, pieces); assertFalse(selectedTiles[0]);
        for (int t = 1; t <= 12; t++) assertTrue(selectedTiles[t], "native selected source tile=" + t);
        for (int t = 13; t < selectedTiles.length; t++) assertFalse(selectedTiles[t]);
        assertThrows(UnsupportedOperationException.class, () -> visual.mappingFrames().clear());
        assertThrows(UnsupportedOperationException.class, () -> visual.mappingFrames().getFirst().pieces().clear());
    }

    @Test void rawWidthAndRangeSelectorsKeepTheFourthZeroWithoutInventingAFourthMappingFrame() throws Exception {
        var a = retail().assets(); var widths = a.view(PLATFORM_WIDTHS).bytes();
        var ranges = a.view(PLATFORM_RANGES).bytes();
        assertArrayEquals(NATIVE_WIDTHS, widths.copy()); assertArrayEquals(NATIVE_RANGES, ranges.copy());
        int[] expectedWidths = {16, 32, 48, 0}; int[] expectedRanges = {2, 3, 4, 6};
        var visual = CdObjectVisualAssets.decodeStatic(a.view(PLATFORM_MAPPINGS).bytes().copy(), 3, 57);
        for (int subtype = 0; subtype <= 0xFF; subtype++) {
            int frame = subtype & 3; int range = (subtype >>> 2) & 3;
            assertEquals(expectedWidths[frame], widths.unsignedByte(frame));
            assertEquals(expectedRanges[range], ranges.unsignedByte(range));
            if (frame == 3) assertThrows(IndexOutOfBoundsException.class, () -> visual.mappingFrames().get(frame));
            else assertEquals(new int[]{4, 8, 12}[frame], visual.mappingFrames().get(frame).pieces().size());
        }
        assertEquals(0, widths.unsignedByte(3)); assertEquals(3, visual.mappingFrames().size());
        assertThrows(IndexOutOfBoundsException.class, () -> widths.unsignedByte(4));
        assertThrows(IndexOutOfBoundsException.class, () -> ranges.unsignedByte(4));
    }

    @Test void rawViewsStayDetachedAndTheirExistingFiniteContractsRejectOverflow() throws Exception {
        var f = retail(); var a = f.assets();
        for (var key : PLATFORM_TABLES) {
            var view = a.view(key); var bytes = view.bytes();
            byte[] original = f.programs().r11b().readFile(key.fileOffset(), key.length());
            assertArrayEquals(original, bytes.copy()); assertNotSame(view, a.view(key));
            byte[] changed = bytes.copy(); changed[0] ^= 0x55;
            assertArrayEquals(original, bytes.copy()); assertArrayEquals(original, a.view(key).bytes().copy());
            assertThrows(ReadOnlyBufferException.class, () -> bytes.buffer().put(0, (byte) 0));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedByte(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedByte(key.length()));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedWord(key.length() - 1));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.slice(key.length(), 1));
            assertEquals(0, bytes.slice(key.length(), 0).length());
            assertThrows(IllegalArgumentException.class,
                    () -> new CdR11bAssets.View(key, key.range(), bytes.slice(0, key.length() - 1)));
            var shifted = new CdR11bAssets.FileRange(CdNativeFile.R11B, CdMmdImage.Domain.ORIGINAL_WORD_RAM,
                    key.fileOffset() + 2, key.length());
            assertThrows(IllegalArgumentException.class, () -> new CdR11bAssets.View(key, shifted, bytes));
            assertThrows(NullPointerException.class, () -> new CdR11bAssets.View(null, key.range(), bytes));
            assertThrows(NullPointerException.class, () -> new CdR11bAssets.View(key, null, bytes));
            assertThrows(NullPointerException.class, () -> new CdR11bAssets.View(key, key.range(), null));
        }
        assertThrows(NullPointerException.class, () -> a.view(null));
    }

    @Test void malformedConstructedMappingsDoNotWidenTheAuthenticatedSourceOrPureDecoder() throws Exception {
        var mapping = retail().assets().view(PLATFORM_MAPPINGS).bytes().copy();
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(mapping, 4, 57));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(mapping, 3, 12));
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decodeStatic(Arrays.copyOf(mapping, 130), 3, 57));
        byte[] beforeIndex = mapping.clone(); beforeIndex[1] = 0;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(beforeIndex, 3, 57));
        byte[] crossingBody = mapping.clone(); crossingBody[6] = 5;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(crossingBody, 3, 57));
        byte[] falseEven = mapping.clone(); falseEven[131] = 1;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(falseEven, 3, 57));
        // This existing pure format accepts omission of an unconsumed final EVEN byte; the named source stays132.
        assertEquals(3, CdObjectVisualAssets.decodeStatic(Arrays.copyOf(mapping, 131), 3, 57).mappingFrames().size());
        assertEquals(132, retail().assets().view(PLATFORM_MAPPINGS).bytes().length());
        assertArrayEquals(NATIVE_MAPPING, retail().assets().view(PLATFORM_MAPPINGS).bytes().copy());
    }

    @Test void rawReadsLeaveExistingPlatformArtReadersPlcAndAggregateOwnersUnchanged() throws Exception {
        var f = retail(); var a = f.assets(); var identity = a.identity(); var terrain = a.terrain();
        var plan = a.loadPlan(); var placements = a.placements(); var sonic = a.sonic(); var player = a.player();
        var blocks = a.blocksDecode(); var levelTiles = a.levelTilesDecode();
        for (var key : PLATFORM_TABLES) a.view(key);
        assertSame(identity, a.identity()); assertSame(terrain, a.terrain()); assertSame(plan, a.loadPlan());
        assertSame(placements, a.placements()); assertSame(sonic, a.sonic()); assertSame(player, a.player());
        assertSame(blocks, a.blocksDecode()); assertSame(levelTiles, a.levelTilesDecode());
        assertEquals(19, plan.plcs().size()); assertEquals(4, plan.sections().size());
        var standard = plan.plcs().get(1); assertEquals(0xF1C6, standard.bodyRange().fileOffset());
        assertEquals(15, standard.entries().size()); var platform = standard.entries().get(6);
        assertSame(ART_PLATFORM, platform.source()); assertEquals(0x97C0, platform.vramAddress());
        assertArrayEquals(HexFormat.of().parseHex("00236eba97c0"), f.programs().r11b().readFile(0xF1EC, 6));
        assertEquals(0x36EBA, ART_PLATFORM.fileOffset()); assertEquals(452, ART_PLATFORM.length());
        assertEquals("0d6cb948568e1e1c82c94d57b3bf089175026ed05b21d9b477603c5a7725928d",
                sha(a.view(ART_PLATFORM).bytes().copy()));
        var reader = ART_PLATFORM.readerProfile().orElseThrow();
        assertEquals(1824, reader.outputBytes()); assertEquals(452, reader.javaInputRange().length());
        assertEquals(453, reader.nativeInputRange().length()); assertEquals(452, reader.javaConsumedInputBytes());
        assertEquals(0x8039, a.view(ART_PLATFORM).bytes().unsignedWord(0));
        assertEquals(0x97C0, (0x44BE & 0x7FF) * 32);
        // Raw source reads create no section/current owner, upload, decode, object or runtime service.
    }

    @Test void adjacentSecondMappingRemainsUnselectedOriginalDataOutsideTheNamedView() throws Exception {
        var f = retail(); var selected = f.assets().view(PLATFORM_MAPPINGS);
        assertEquals(0x20CD9E, selected.range().endExclusive());
        byte[] adjacent = f.programs().r11b().readFile(0xCD9E, 132);
        assertEquals("99255f57e1ce478e78aa5a85f634f55a46febb767404e1e2eefd248120f48fad", sha(adjacent));
        assertFalse(Arrays.equals(selected.bytes().copy(), adjacent));
        for (int at : new int[]{0xC8EC, 0xC8FC, 0xC90C})
            assertArrayEquals(HexFormat.of().parseHex("203c0020cd1a"), f.programs().r11b().readFile(at, 6));
        assertThrows(IndexOutOfBoundsException.class, () -> selected.bytes().unsignedByte(132));
        assertThrows(IndexOutOfBoundsException.class, () -> selected.bytes().slice(0, 264));
        // Presence of adjacent source data does not select it or register a second mapping root.
    }

    private String retainedRoot;
    private Fixture retained;
    private record Fixture(CdTimeWarpPrograms programs, CdR11bAssets assets) { }
    private Fixture retail() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assertNotNull(root, "Explicit Platform source cases require the configured original USA media root");
        assertFalse(root.isBlank(), "The original USA media root must not be blank");
        if (retained != null && root.equals(retainedRoot)) return retained;
        Path usa = Path.of(root).resolve("USA"); assertTrue(Files.isDirectory(usa), "Original USA media directory must exist");
        Path cue;
        try (var paths = Files.list(usa)) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured USA source must name exactly one existing CUE");
            cue = cues.getFirst();
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
