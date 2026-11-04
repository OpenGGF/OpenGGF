package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.ReadOnlyBufferException;
import java.nio.charset.StandardCharsets;
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
 * Original-USA B raw metadata, not object execution, art service, residency or pixels.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * ObjCollapsePlatform_Init (Palmtree Panic/Objects/Collapsing Platform/Main.asm:32-68),
 * child mapping stores (:183,:281), complete Data (Past).asm:7-83 and its four Past mapping includes;
 * ObjBoulder_Init and MapSpr_Boulder (_Objects/Boulder/Main.asm:26-35,57-59).
 * Source even directives retain genuine alignment00 in mapping4 and Boulder. B's two ledge frames,
 * seven second-family child frames and two sizes extents are independently B facts, not A defaults.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11bCollapseBoulderAssets {
    private static final String PAST_SHA =
            "b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265";
    private static final List<CdR11bAssets.Asset> TABLES = List.of(
            MAP_SPR_COLLAPSE_PLATFORM_1, COLLAPSE_PLATFORM_SIZES_1, MAP_SPR_COLLAPSE_PLATFORM_3,
            MAP_SPR_COLLAPSE_PLATFORM_2, COLLAPSE_PLATFORM_SIZES_2, MAP_SPR_COLLAPSE_PLATFORM_4, MAP_SPR_BOULDER);
    private static final String MANIFEST = """
MAP_SPR_COLLAPSE_PLATFORM_1 MapSpr_CollapsePlatform1 c378 202 eea06694c49702bbe30130b462bd797d000721083bb4273f870e2636f36863fb
COLLAPSE_PLATFORM_SIZES_1 ObjCollapsePlatform_Sizes1 c442 40 00363b76286a0d4ffd6fb533d1db85933736514264acc11dd481fd7bbf9996f0
MAP_SPR_COLLAPSE_PLATFORM_3 MapSpr_CollapsePlatform3 c46a 62 77e4add5588163b238981a19f8715f1033c2c07ea2e639c223486ecc1c8e7f2c
MAP_SPR_COLLAPSE_PLATFORM_2 MapSpr_CollapsePlatform2 c4a8 782 e8d169901d047ca9f3a76b31ed77d7d042160ff3da746284bf2d3d4bdb883f53
COLLAPSE_PLATFORM_SIZES_2 ObjCollapsePlatform_Sizes2 c7b6 58 b56f8dcf32505575926facb4006c58da5826c9d24358438b485e2ecf3bf3e5cf
MAP_SPR_COLLAPSE_PLATFORM_4 MapSpr_CollapsePlatform4 c7f0 172 f372b9f9d23c8ba0ebc6dc22993f60078891ea2843910a197cfe43e45a075a76
MAP_SPR_BOULDER MapSpr_Boulder 94ac 14 f1613b80dc72cd1d153d190f3a660e3990f92648d54e65c989aed93bc9189652
""";

    @Test void sevenRawViewsAppendExactOriginalUsaRangesAndWholeHashes() throws Exception {
        var f = retail(); var a = f.assets(); var rows = MANIFEST.strip().lines().toList();
        assertEquals(120, CdR11bAssets.Asset.values().length);
        assertEquals(TABLES, Arrays.asList(CdR11bAssets.Asset.values()).subList(100, 107));
        for (int i = 0; i < rows.size(); i++) {
            var columns = rows.get(i).split(" "); var key = TABLES.get(i); var view = a.view(key);
            assertEquals(100 + i, key.ordinal()); assertEquals(columns[0], key.name());
            assertEquals(columns[1], key.nativeLabel()); assertEquals(Integer.parseInt(columns[2], 16), key.fileOffset());
            assertEquals(Integer.parseInt(columns[3]), key.length()); assertEquals(columns[4], sha(view.bytes().copy()));
            assertSame(key, view.asset()); assertEquals(key.range(), view.range());
            assertEquals(CdNativeFile.R11B, view.range().file());
            assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, view.range().domain());
            assertEquals(0x200000 + key.fileOffset(), view.range().nativeAddress());
            assertEquals(0x200000 + key.fileOffset() + key.length(), view.range().endExclusive());
            assertArrayEquals(f.programs().r11b().readFile(key.fileOffset(), key.length()), view.bytes().copy());
            assertFalse(key.isPlcSource()); assertTrue(key.readerProfile().isEmpty());
            for (var kind : CdR11bAssets.ReaderKind.values())
                assertThrows(IllegalArgumentException.class, () -> a.readerView(key, kind));
        }
    }

    @Test void oldHundredOrdinalsMetadataAndEveryReaderContractRemainExact() throws Exception {
        var metadata = new StringBuilder();
        for (var key : Arrays.asList(CdR11bAssets.Asset.values()).subList(0, 100)) {
            metadata.append(key.ordinal()).append('|').append(key.name()).append('|').append(key.nativeLabel())
                    .append('|').append(key.fileOffset()).append('|').append(key.length())
                    .append('|').append(key.isPlcSource()).append('|');
            if (key.readerProfile().isPresent()) {
                var p = key.readerProfile().orElseThrow();
                metadata.append(p.javaInputRange().length()).append('|').append(p.nativeInputRange().length())
                        .append('|').append(p.outputBytes()).append('|').append(p.javaConsumedInputBytes());
                assertEquals(key.range(), p.sourceBlobRange());
            } else metadata.append('-');
            metadata.append('\n');
        }
        assertEquals("ba7e43f9c18a7cb106ed312a5e17ed21d631547faed081baf987734003606421",
                sha(metadata.toString().getBytes(StandardCharsets.UTF_8)));
        assertEquals(36, Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
        assertEquals(37, Arrays.stream(CdR11bAssets.Asset.values()).filter(k -> k.readerProfile().isPresent()).count());
        assertEquals(95, ANI_TAMABBOH_NORMAL.ordinal()); assertEquals(99, MAP_SPR_TAMABBOH_MISSILE.ordinal());
    }

    @Test void actualDispatchAndReachedNativeOperandsSelectTheseBViews() throws Exception {
        var image = retail().programs().r11b();
        assertArrayEquals(hex("00209442"), image.readFile(0x3CA0, 4)); // ID1B.
        assertArrayEquals(hex("0020c060"), image.readFile(0x3CB4, 4)); // ID20.
        assertArrayEquals(hex("317c44be0002"), image.readFile(0xC09C, 6));
        assertArrayEquals(hex("43fa02d445fa039a"), image.readFile(0xC0A2, 8));
        assertEquals(MAP_SPR_COLLAPSE_PLATFORM_1.range().nativeAddress(), 0x20C0A4 + (short) 0x02D4);
        assertEquals(COLLAPSE_PLATFORM_SIZES_1.range().nativeAddress(), 0x20C0A8 + (short) 0x039A);
        assertArrayEquals(hex("43fa03f645fa0700"), image.readFile(0xC0B0, 8));
        assertEquals(MAP_SPR_COLLAPSE_PLATFORM_2.range().nativeAddress(), 0x20C0B2 + (short) 0x03F6);
        assertEquals(COLLAPSE_PLATFORM_SIZES_2.range().nativeAddress(), 0x20C0B6 + (short) 0x0700);
        assertArrayEquals(hex("237c0020c46a0004"), image.readFile(0xC206, 8));
        assertArrayEquals(hex("237c0020c7f00004"), image.readFile(0xC30E, 8));
        assertArrayEquals(hex("217c002094ac0004"), image.readFile(0x9470, 8));
        assertArrayEquals(hex("700b4eb90020cf72"), image.readFile(0x948A, 8));
        assertArrayEquals(hex("43f90020cf94d040"), image.readFile(0xCF72, 8));
    }

    @Test void pastMappingsRetainBothLedgesAndAllSevenSecondFamilyChildren() throws Exception {
        var a = retail().assets();
        var keys = List.of(MAP_SPR_COLLAPSE_PLATFORM_1, MAP_SPR_COLLAPSE_PLATFORM_2,
                MAP_SPR_COLLAPSE_PLATFORM_3, MAP_SPR_COLLAPSE_PLATFORM_4, MAP_SPR_BOULDER);
        int[][] offsets = {{4, 120}, {12, 12, 103, 329, 430, 531}, {14, 20, 26, 32, 38, 44, 50},
                {14, 30, 56, 82, 108, 129, 150}, {2}};
        int[][] counts = {{23, 16}, {18, 18, 45, 20, 20, 50}, {1, 1, 1, 1, 1, 1, 2},
                {3, 5, 5, 5, 4, 4, 4}, {2}};
        for (int k = 0; k < keys.size(); k++) {
            var raw = a.view(keys.get(k)).bytes();
            int bound = k == 4 ? 19 : 57;
            var decoded = CdObjectVisualAssets.decodeStatic(raw.copy(), counts[k].length, bound);
            assertEquals(counts[k].length, decoded.mappingFrames().size());
            assertTrue(decoded.animations().isEmpty());
            for (int i = 0; i < counts[k].length; i++) {
                assertEquals(offsets[k][i], raw.unsignedWord(i * 2));
                var frame = decoded.mappingFrames().get(i);
                assertEquals(offsets[k][i], frame.relativeOffset()); assertEquals(counts[k][i], frame.pieces().size());
                assertEquals(1 + counts[k][i] * 5, frame.bytes().length());
            }
        }
    }

    @Test void bothCompletePastSizeStreamsKeepNativeRelativeTargetsAndTail() throws Exception {
        var a = retail().assets();
        var first = a.view(COLLAPSE_PLATFORM_SIZES_1).bytes();
        var second = a.view(COLLAPSE_PLATFORM_SIZES_2).bytes();
        assertArrayEquals(hex("0004001a0403ffff0000000102030304000505050506060606060302010203030505050506060606"), first.copy());
        assertArrayEquals(hex("000c000c0014001f0026002d05010000000000000803010303030303030302040204060606060402060606060509030103030303030303030200"), second.copy());
        for (int i = 0; i < 2; i++) assertTrue(first.unsignedWord(i * 2) < first.length());
        for (int i = 0; i < 6; i++) assertTrue(second.unsignedWord(i * 2) < second.length());
        assertEquals(0xFF, first.unsignedByte(6)); assertEquals(0xFF, first.unsignedByte(7));
        assertEquals(0, second.unsignedByte(57)); // Actual even directive, not a supplied extra byte.
    }

    @Test void boulderUsesActualSeventyTwoByteDirectoryAndSelectorElevenWord() throws Exception {
        var a = retail().assets(); var tiles = a.view(OBJECT_TILE_IDS).bytes();
        assertEquals(72, tiles.length()); assertEquals(0x3A, tiles.unsignedWord(11 * 2));
        int selected = (short) tiles.unsignedWord(11 * 2);
        assertEquals(0xCFCE, OBJECT_TILE_IDS.fileOffset() + selected);
        assertEquals(0x0346, tiles.unsignedWord(selected));
        assertEquals(selected + 2, CdObjectTileIds.endOfBody(tiles, 11));
        assertArrayEquals(hex("000202f8020000ecf00f0003f400"), a.view(MAP_SPR_BOULDER).bytes().copy());
        assertThrows(IndexOutOfBoundsException.class, () -> tiles.unsignedWord(72));
    }

    @Test void sourceAlignmentEndsBeforeAdjacentNativeObjectsWithoutPadding() throws Exception {
        var f = retail(); var a = f.assets();
        for (int i = 0; i < 5; i++) assertEquals(TABLES.get(i).range().endExclusive(), TABLES.get(i + 1).range().nativeAddress());
        assertEquals(0x20C89C, MAP_SPR_COLLAPSE_PLATFORM_4.range().endExclusive());
        assertEquals(0x2094BA, MAP_SPR_BOULDER.range().endExclusive());
        assertEquals(0, a.view(MAP_SPR_COLLAPSE_PLATFORM_4).bytes().unsignedByte(171));
        assertEquals(0, a.view(MAP_SPR_BOULDER).bytes().unsignedByte(13));
        assertArrayEquals(hex("700010280024"), f.programs().r11b().readFile(0x94BA, 6));
        assertArrayEquals(hex("700010280024"), f.programs().r11b().readFile(0xC89C, 6));
    }

    @Test void viewsStayDetachedAndNeverBorrowPresentBytesOrChangeExistingOwners() throws Exception {
        var f = retail(); var a = f.assets(); var terrain = a.terrain(); var plan = a.loadPlan();
        var placements = a.placements(); var player = a.player(); var sonic = a.sonic(); var identity = a.identity();
        for (var key : TABLES) {
            var raw = a.view(key).bytes(); int first = raw.unsignedByte(0); byte[] copy = raw.copy(); copy[0] ^= 0x55;
            assertEquals(first, a.view(key).bytes().unsignedByte(0)); assertNotEquals(first, copy[0] & 0xFF);
            assertThrows(ReadOnlyBufferException.class, () -> raw.buffer().put(0, (byte) 1));
            assertThrows(IndexOutOfBoundsException.class, () -> raw.slice(key.length(), 1));
            assertNotEquals(sha(f.present().readFile(key.fileOffset(), key.length())), sha(raw.copy()), key.name());
        }
        assertSame(terrain, a.terrain()); assertSame(plan, a.loadPlan()); assertSame(placements, a.placements());
        assertSame(player, a.player()); assertSame(sonic, a.sonic()); assertSame(identity, a.identity());
        assertEquals(PAST_SHA, sha(f.programs().r11b().readFile(0, 262144)));
    }

    private String retainedRoot;
    private Fixture retained;
    private record Fixture(CdTimeWarpPrograms programs, CdR11bAssets assets, CdMmdImage present) { }

    private Fixture retail() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assertNotNull(root, "Past Collapse/Boulder metadata requires the configured original USA media root");
        assertFalse(root.isBlank());
        if (retained != null && root.equals(retainedRoot)) return retained;
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured USA source must name exactly one original CUE"); cue = cues.getFirst();
        }
        CdTimeWarpPrograms programs; CdMmdImage present;
        try (var owner = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            programs = CdTimeWarpPrograms.fromPrograms(owner); present = owner.mmd(CdNativeFile.R11A);
        }
        var assets = CdR11bAssets.fromPrograms(programs);
        assertEquals(PAST_SHA, sha(programs.r11b().readFile(0, 262144)));
        retained = new Fixture(programs, assets, present); retainedRoot = root;
        return retained;
    }
    private static byte[] hex(String text) { return HexFormat.of().parseHex(text); }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
