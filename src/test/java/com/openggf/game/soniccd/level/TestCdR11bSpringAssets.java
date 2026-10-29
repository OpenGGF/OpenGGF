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
 * Original USA B source views and existing pure format readers only. No Spring body or World executes.
 * Native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * ObjSpring2_Init/ObjSpring_Init/ObjSpring_Speeds
 * (Level/_Objects/Spring/Main.asm:110-118,186-244), animation operands (:281-283,347-349,400-402,470-472),
 * Ani_Spring/include EVEN (:492-496), Data/Animations.asm:1-8, Data/Mappings.asm:1-82;
 * SetObjectTileID (Level/Object Tile ID Set.asm:7-17),
 * ObjectTileIDs (Level/Palmtree Panic/Object Tile IDs (Act 1 Past).asm:7-44),
 * Act 1 Past.asm:73-75 and PLC_Std (Data (Act 1 Past).asm:52-83).
 * Literal bytes expand the complete native declarations, including real EVEN bytes, independently of Java.
 * Actual original USA media is a hard prerequisite; these cases never skip for missing media.
 * Java discovery, RED, compilation and positive execution are initially UNRUN.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11bSpringAssets {
    private static final String PAST_SHA =
            "b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265";
    private static final byte[] NATIVE_SPEEDS = HexFormat.of().parseHex("f000f600");
    private static final byte[] NATIVE_ANIMATION = HexFormat.of().parseHex("00020000010102020202020201fc");
    private static final byte[] NATIVE_MAPPING = HexFormat.of().parseHex(
            "000c0018001e00280034003a"
                    + "02f80c0000f0000c0004f00001000c0000f003e00c0000f0e8060008f8000c000ef0"
                    + "02f003001200f0030016f80001f0030012f803f003001218f809001a00f0030020f8"
                    + "0006002a004e"
                    + "0708000000f000000001f008000002f8f0080003f0f8000006f0f8090007f80804000d00"
                    + "0708000000f00000000ff008000010f8f0050011f0f80000050000000015f80005001600"
                    + "0808000000f00000001af00800001bf8e008000300e80e001c00f8000028f0f0020029f80001002c0000");
    private static final byte[] NATIVE_TILE_IDS = HexFormat.of().parseHex(
            "002400260028002a002c002e00380032003600340030003a003c003e0040004200440046"
                    + "2396243f23ed2000240343340320038140004000037203468328000000000490039e0000");
    private static final List<CdR11bAssets.Asset> SPRING_TABLES =
            List.of(SPRING_SPEEDS, ANI_SPRING, MAP_SPRING, OBJECT_TILE_IDS);

    @BeforeAll void requireAuthenticatedOriginalUsaMedia() throws Exception {
        retail();
    }

    @Test void exactlyFourRawViewsAppendAfterEveryInheritedOrdinal() throws Exception {
        var a = retail().assets();
        assertEquals(84, CdR11bAssets.Asset.values().length);
        assertEquals(SPRING_TABLES, Arrays.asList(CdR11bAssets.Asset.values()).subList(80, 84));
        String[] labels = {"ObjSpring_Speeds", "Ani_Spring", "MapSpr_Spring", "ObjectTileIDs"};
        int[] offsets = {0x9740, 0x99EA, 0x99F8, 0xCF94}, lengths = {4, 14, 200, 72};
        String[] hashes = {
                "5eda02f69ae1d12606f936787ccebdac8133522694513985847a115cc00f2621",
                "7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5",
                "09fbb070ca8ab3354808535b21668a1c6faeac9fa88fb78628753fe9c8f8377c",
                "7f6759c3ab336c2eee70a2fea5128e9920c86d418590ae557e0310948c563da1"};
        for (int i = 0; i < SPRING_TABLES.size(); i++) {
            var key = SPRING_TABLES.get(i); var view = a.view(key);
            assertEquals(80 + i, key.ordinal()); assertEquals(labels[i], key.nativeLabel());
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
            assertThrows(IllegalArgumentException.class, () -> new CdR11bLoadPlan.PlcEntry(key, key.range(), 0xA400));
        }
        assertEquals(77, RING_DELTAS.ordinal()); assertEquals(78, ANI_RING.ordinal());
        assertEquals(79, MAP_SPR_RING.ordinal());
        assertEquals(37, Arrays.stream(CdR11bAssets.Asset.values()).filter(k -> k.readerProfile().isPresent()).count());
        assertEquals(36, Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
    }

    @Test void originalIdTenAndEveryActualSpringOperandSelectTheseBAddresses() throws Exception {
        var f = retail(); var mmd = f.programs().r11b();
        assertEquals(PAST_SHA, sha(mmd.readFile(0, mmd.fileBytes())));
        assertEquals(0x209606L, f.assets().view(OBJECT_INDEX).bytes().unsignedLong((0x0A - 1) * 4));
        assertArrayEquals(HexFormat.of().parseHex("00209606"), mmd.readFile(0x3C5C, 4));
        assertArrayEquals(HexFormat.of().parseHex("0c280005002567aa700010280024"), mmd.readFile(0x9606, 14));
        // Mirror and ordinary Init own their distinct tile literals at the same vertical mapping root.
        assertArrayEquals(HexFormat.of().parseHex("217c002099f80004317c85200002"), mmd.readFile(0x95CA, 14));
        assertArrayEquals(HexFormat.of().parseHex("217c002099f80004317c05200002"), mmd.readFile(0x9694, 14));
        assertArrayEquals(HexFormat.of().parseHex("217c002099fe0004"), mmd.readFile(0x96DC, 8));
        assertArrayEquals(HexFormat.of().parseHex("217c00209a480004"), mmd.readFile(0x96FE, 8));
        assertEquals(MAP_SPRING.range().nativeAddress(), 0x2099F8);
        assertEquals(MAP_SPRING.range().nativeAddress() + 6, 0x2099FE);
        assertEquals(MAP_SPRING.range().nativeAddress() + 80, 0x209A48);
        assertArrayEquals(HexFormat.of().parseHex("2f00700f4eb90020cf72201f"), mmd.readFile(0x9706, 12));
        assertArrayEquals(HexFormat.of().parseHex("02400002317b000600304e75"), mmd.readFile(0x9734, 12));
        assertEquals(SPRING_SPEEDS.range().nativeAddress(), 0x20973A + 6); // Native PC-index base, not A plus a delta.
        for (int operand : new int[]{0x9794, 0x983C, 0x98A6, 0x9964})
            assertArrayEquals(HexFormat.of().parseHex("43f9002099ea"), mmd.readFile(operand, 6));
        assertEquals(0x2099EA, ANI_SPRING.range().nativeAddress());
        assertArrayEquals(HexFormat.of().parseHex("43f90020cf94"), mmd.readFile(0xCF72, 6));
        assertEquals(0x20CF94, OBJECT_TILE_IDS.range().nativeAddress());
    }

    @Test void completeSpeedWordsAndSingleNativeAnimationRetainTheirLiteralSemantics() throws Exception {
        var a = retail().assets(); var speeds = a.view(SPRING_SPEEDS).bytes(); var animation = a.view(ANI_SPRING).bytes();
        assertArrayEquals(NATIVE_SPEEDS, speeds.copy()); assertArrayEquals(NATIVE_ANIMATION, animation.copy());
        assertEquals(-4096, (short) speeds.unsignedWord(0)); assertEquals(-2560, (short) speeds.unsignedWord(2));
        for (int subtype = 0; subtype <= 0xFF; subtype++)
            assertEquals((subtype & 2) == 0 ? -4096 : -2560, (short) speeds.unsignedWord(subtype & 2));
        assertEquals(2, animation.unsignedWord(0)); assertEquals(0, animation.unsignedByte(2));
        assertArrayEquals(new byte[]{0, 1, 1, 2, 2, 2, 2, 2, 2, 1, (byte) 0xFC}, animation.slice(3, 11).copy());
        assertEquals(0x2099F8, ANI_SPRING.range().endExclusive());
        assertEquals(MAP_SPRING.range().nativeAddress(), ANI_SPRING.range().endExclusive());
        assertArrayEquals(HexFormat.of().parseHex("4e75"), retail().programs().r11b().readFile(0x973E, 2));
        assertArrayEquals(HexFormat.of().parseHex("362800083828000c"), retail().programs().r11b().readFile(0x9744, 8));
    }

    @Test void allThreeIndependentMappingRootsAndEveryCountedPieceBindTheExistingDecoder() throws Exception {
        var a = retail().assets(); var mapping = a.view(MAP_SPRING).bytes(); var animation = a.view(ANI_SPRING).bytes();
        assertArrayEquals(NATIVE_MAPPING, mapping.copy());
        int[][] windows = {{0, 12, 46, 36}, {6, 46, 80, 36}, {80, 86, 200, 46}};
        int[][] relative = {{12, 24, 30}, {40, 52, 58}, {6, 42, 78}};
        int[][] pieceCounts = {{2, 1, 3}, {2, 1, 3}, {7, 7, 8}};
        boolean[][] usedTiles = {new boolean[36], new boolean[46]};
        for (int family = 0; family < windows.length; family++) {
            int[] w = windows[family];
            var decoded = CdObjectVisualAssets.decodeIndexed(mapping.copy(), w[0], 3, w[1], w[2], animation.copy(), 1, w[3]);
            assertEquals(w[0], decoded.mappingTableOffset()); assertArrayEquals(NATIVE_MAPPING, decoded.mappings().copy());
            assertArrayEquals(NATIVE_ANIMATION, decoded.animationTable().copy()); assertEquals(3, decoded.mappingFrames().size());
            for (int frame = 0; frame < 3; frame++) {
                int start = w[0] + relative[family][frame]; var body = decoded.mappingFrames().get(frame);
                assertEquals(relative[family][frame], mapping.unsignedWord(w[0] + frame * 2));
                assertEquals(relative[family][frame], body.relativeOffset());
                assertEquals(pieceCounts[family][frame], body.pieces().size());
                assertEquals(1 + 5 * pieceCounts[family][frame], body.bytes().length());
                assertArrayEquals(mapping.slice(start, body.bytes().length()).copy(), body.bytes().copy());
                for (int pieceIndex = 0; pieceIndex < body.pieces().size(); pieceIndex++) {
                    int at = start + 1 + pieceIndex * 5; var piece = body.pieces().get(pieceIndex);
                    assertEquals((int) NATIVE_MAPPING[at], piece.y());
                    assertEquals(Byte.toUnsignedInt(NATIVE_MAPPING[at + 1]), piece.sizeByte());
                    assertEquals((Byte.toUnsignedInt(NATIVE_MAPPING[at + 2]) << 8)
                            | Byte.toUnsignedInt(NATIVE_MAPPING[at + 3]), piece.rawTileWord());
                    assertEquals((int) NATIVE_MAPPING[at + 4], piece.x());
                    for (int tile = piece.tileOffset(); tile < piece.tileOffset() + piece.tileCount(); tile++)
                        usedTiles[family == 2 ? 1 : 0][tile] = true;
                }
                int end = frame < 2 ? w[0] + relative[family][frame + 1] : w[2];
                if (start + body.bytes().length() != end) {
                    assertEquals(start + body.bytes().length() + 1, end);
                    assertEquals(1, (start + body.bytes().length()) & 1);
                    assertEquals(0, mapping.unsignedByte(end - 1));
                }
            }
            assertEquals(1, decoded.animations().size()); var script = decoded.animations().getFirst();
            assertEquals(2, script.relativeOffset()); assertEquals(0, script.delay());
            assertEquals(List.of(0, 1, 1, 2, 2, 2, 2, 2, 2, 1), script.frameBytes());
            assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC, script.terminal());
            assertArrayEquals(animation.slice(2, 12).copy(), script.bytes().copy());
        }
        for (var family : usedTiles) for (int tile = 0; tile < family.length; tile++) assertTrue(family[tile], "native relative tile " + tile);
        assertEquals(0, mapping.unsignedByte(199)); // Actual final include EVEN is inside the full200 view.
        assertEquals(0x209AC0, MAP_SPRING.range().endExclusive());
        assertArrayEquals(HexFormat.of().parseHex("0002080001ff"), retail().programs().r11b().readFile(0x9AC0, 6));
    }

    @Test void fullEighteenEntryTileTableEndsAtSeventyTwoBeforeTheAnimalRoutine() throws Exception {
        var f = retail(); var tiles = f.assets().view(OBJECT_TILE_IDS).bytes();
        assertArrayEquals(NATIVE_TILE_IDS, tiles.copy()); assertEquals(72, tiles.length());
        int[] directory = {36, 38, 40, 42, 44, 46, 56, 50, 54, 52, 48, 58, 60, 62, 64, 66, 68, 70};
        int[] values = {0x2396, 0x243F, 0x23ED, 0x2000, 0x2403, 0x4334, 0x0320, 0x0381, 0x4000,
                0x4000, 0x0372, 0x0346, 0x8328, 0, 0, 0x0490, 0x039E, 0};
        for (int i = 0; i < directory.length; i++) {
            assertEquals(directory[i], tiles.unsignedWord(2 * i));
            assertEquals(values[i], tiles.unsignedWord(36 + 2 * i));
        }
        int selected = tiles.unsignedWord(0xF * 2); assertEquals(0x42, selected);
        assertEquals(0x20CFD6, OBJECT_TILE_IDS.range().nativeAddress() + selected);
        assertEquals(0x0490, tiles.unsignedWord(selected + 0 * 2)); // Actual selector F, subtype2 zero.
        assertEquals(0x9200, tiles.unsignedWord(selected) * 32);
        assertEquals(0xA400, 0x0520 * 32); // Ordinary normal Spring's literal is independent of the tile selector.
        assertEquals(0, tiles.unsignedWord(70)); assertEquals(0x20CFDC, OBJECT_TILE_IDS.range().endExclusive());
        assertArrayEquals(HexFormat.of().parseHex("4eb9"), f.programs().r11b().readFile(0xCFDC, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> tiles.unsignedWord(72));
        assertThrows(IndexOutOfBoundsException.class, () -> tiles.slice(70, 4));
        // Following Animal JSR bytes are observed only through the original program, never padded into this view.
    }

    @Test void rawViewsStayDetachedAndMalformedViewsRespectOnlyTheirExistingFiniteContract() throws Exception {
        var f = retail(); var a = f.assets();
        for (var key : SPRING_TABLES) {
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
            assertThrows(IllegalArgumentException.class, () -> new CdR11bAssets.View(key, key.range(), bytes.slice(0, key.length() - 1)));
            var shifted = new CdR11bAssets.FileRange(CdNativeFile.R11B, CdMmdImage.Domain.ORIGINAL_WORD_RAM,
                    key.fileOffset() + 2, key.length());
            assertThrows(IllegalArgumentException.class, () -> new CdR11bAssets.View(key, shifted, bytes));
            assertThrows(NullPointerException.class, () -> new CdR11bAssets.View(null, key.range(), bytes));
            assertThrows(NullPointerException.class, () -> new CdR11bAssets.View(key, null, bytes));
            assertThrows(NullPointerException.class, () -> new CdR11bAssets.View(key, key.range(), null));
        }
        assertThrows(NullPointerException.class, () -> a.view(null));
    }

    @Test void malformedConstructedFormatInputsDoNotWidenTheAuthenticatedViews() throws Exception {
        var a = retail().assets(); byte[] mapping = a.view(MAP_SPRING).bytes().copy();
        byte[] animation = a.view(ANI_SPRING).bytes().copy();
        int[][] windows = {{0, 12, 46, 36}, {6, 46, 80, 36}, {80, 86, 200, 46}};
        for (int[] w : windows) {
            byte[] beforeBody = mapping.clone(); beforeBody[w[0]] = 0; beforeBody[w[0] + 1] = 0;
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decodeIndexed(beforeBody, w[0], 3, w[1], w[2], animation, 1, w[3]));
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decodeIndexed(mapping, w[0], 3, w[1], w[2] - 2, animation, 1, w[3]));
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decodeIndexed(mapping, w[0], 3, w[1], w[2], Arrays.copyOf(animation, 13), 1, w[3]));
            byte[] unsupported = animation.clone(); unsupported[13] = (byte) 0xFD;
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decodeIndexed(mapping, w[0], 3, w[1], w[2], unsupported, 1, w[3]));
        }
        byte[] falseEven = mapping.clone(); falseEven[199] = 1;
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decodeIndexed(falseEven, 80, 3, 86, 200, animation, 1, 46));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(mapping, 3, animation, 1, 46));
        assertArrayEquals(NATIVE_MAPPING, a.view(MAP_SPRING).bytes().copy());
        assertArrayEquals(NATIVE_ANIMATION, a.view(ANI_SPRING).bytes().copy());
    }

    @Test void existingSpringArtReadersPlcRowsAndAggregateOwnersRemainUnchangedByRawReads() throws Exception {
        var f = retail(); var a = f.assets(); var identity = a.identity(); var terrain = a.terrain();
        var plan = a.loadPlan(); var placements = a.placements(); var sonic = a.sonic(); var player = a.player();
        var blocks = a.blocksDecode(); var levelTiles = a.levelTilesDecode();
        for (var key : SPRING_TABLES) a.view(key);
        assertSame(identity, a.identity()); assertSame(terrain, a.terrain()); assertSame(plan, a.loadPlan());
        assertSame(placements, a.placements()); assertSame(sonic, a.sonic()); assertSame(player, a.player());
        assertSame(blocks, a.blocksDecode()); assertSame(levelTiles, a.levelTilesDecode());
        assertEquals(19, plan.plcs().size()); assertEquals(4, plan.sections().size());
        var standard = plan.plcs().get(1); assertEquals(0xF1C6, standard.bodyRange().fileOffset());
        assertEquals(15, standard.entries().size());
        var diagonal = standard.entries().get(5); var normal = standard.entries().get(8);
        assertSame(ART_DIAGONAL_SPRING, diagonal.source()); assertEquals(0x9200, diagonal.vramAddress());
        assertSame(ART_SPRINGS, normal.source()); assertEquals(0xA400, normal.vramAddress());
        assertArrayEquals(HexFormat.of().parseHex("002351c89200"), f.programs().r11b().readFile(0xF1E6, 6));
        assertArrayEquals(HexFormat.of().parseHex("002354d4a400"), f.programs().r11b().readFile(0xF1F8, 6));
        assertEquals(0x351C8, ART_DIAGONAL_SPRING.fileOffset()); assertEquals(780, ART_DIAGONAL_SPRING.length());
        assertEquals(0x354D4, ART_SPRINGS.fileOffset()); assertEquals(546, ART_SPRINGS.length());
        var diagonalReader = ART_DIAGONAL_SPRING.readerProfile().orElseThrow();
        assertEquals(1472, diagonalReader.outputBytes()); assertEquals(780, diagonalReader.javaInputRange().length());
        assertEquals(780, diagonalReader.nativeInputRange().length()); assertEquals(779, diagonalReader.javaConsumedInputBytes());
        var normalReader = ART_SPRINGS.readerProfile().orElseThrow();
        assertEquals(1152, normalReader.outputBytes()); assertEquals(547, normalReader.javaInputRange().length());
        assertEquals(547, normalReader.nativeInputRange().length()); assertEquals(547, normalReader.javaConsumedInputBytes());
        assertEquals("3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a", sha(a.view(ART_DIAGONAL_SPRING).bytes().copy()));
        assertEquals("19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831", sha(a.view(ART_SPRINGS).bytes().copy()));
    }

    private String retainedRoot;
    private Fixture retained;
    private record Fixture(CdTimeWarpPrograms programs, CdR11bAssets assets) { }
    private Fixture retail() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assertNotNull(root, "Explicit Spring source cases require the configured original USA media root");
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
