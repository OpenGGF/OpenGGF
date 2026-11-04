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
 * Finite original-USA Past Pata-Bata metadata only; no body, PLC service, Pata-Bata art decode or World executes.
 * The unchanged aggregate loader still decodes its core blocks and terrain art.
 * Native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * ObjPataBata (Level/Palmtree Panic/Objects/Pata-Bata/Main.asm:7-93),
 * Ani_PataBata (Data/Animations.asm:1-5), MapSpr_PataBata1/2 (Data/Mappings (Normal).asm:1-32,
 * Data/Mappings (Damaged).asm:1-38), SetObjectTileID (Level/Object Tile ID Set.asm:7-17),
 * ObjectTileIDs (Level/Palmtree Panic/Object Tile IDs (Act 1 Past).asm:7-44).
 * Literal tables resolve those complete declarations and final EVEN independently of production Java.
 * Configured original USA media is mandatory. Discovery, RED, compilation and execution are UNRUN.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11bPataBataAssets {
    private static final String PAST_SHA =
            "b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265";
    private static final byte[] NATIVE_ANIMATIONS = HexFormat.of().parseHex(
            "0004000b070000010201ff030000010201ff");
    private static final byte[] NATIVE_NORMAL = HexFormat.of().parseHex(
            "00060011001c02f0070000f0f00700080002f80d0010f0080000180001f00f0019f0");
    private static final byte[] NATIVE_DAMAGED = HexFormat.of().parseHex(
            "00060011001c02f0070000f0f00700290002f80d0010f00800001800020000003100f00f0019f000");
    private static final byte[] NATIVE_TILE_IDS = HexFormat.of().parseHex(
            "002400260028002a002c002e00380032003600340030003a003c003e0040004200440046"
                    + "2396243f23ed2000240343340320038140004000037203468328000000000490039e0000");
    private static final List<CdR11bAssets.Asset> TABLES =
            List.of(ANI_PATA_BATA, MAP_SPR_PATA_BATA1, MAP_SPR_PATA_BATA2);

    @BeforeAll void requireAuthenticatedOriginalUsaMedia() throws Exception {
        retail();
    }

    @Test void exactlyThreeFiniteViewsAppendWithoutChangingAnyReaderOrPlcCount() throws Exception {
        var a = retail().assets();
        assertEquals(114, CdR11bAssets.Asset.values().length);
        assertEquals(TABLES, Arrays.asList(CdR11bAssets.Asset.values()).subList(92, 95));
        assertEquals(90, ANI_FLOAT_BLOCK.ordinal()); assertEquals(91, MAP_SPR_FLOAT_BLOCK.ordinal());
        String[] labels = {"Ani_PataBata", "MapSpr_PataBata1", "MapSpr_PataBata2"};
        int[] offsets = {0xB374, 0xB386, 0xB3A8}, lengths = {18, 34, 40};
        String[] hashes = {
                "a68b2c9721811294f0f2ae43e0f78acc91ed8211a5d0da2d08d2ba230b6a25f3",
                "9165ad8d9f95bda2639172fe900f16dad44924af5f896a0223e6b9cb8bb7087b",
                "96d4c533000d636452afa838266996b9c4853824e7b3377868f7876492858889"};
        for (int i = 0; i < TABLES.size(); i++) {
            var key = TABLES.get(i); var view = a.view(key);
            assertEquals(92 + i, key.ordinal()); assertEquals(labels[i], key.nativeLabel());
            assertEquals(offsets[i], key.fileOffset()); assertEquals(lengths[i], key.length());
            assertSame(key, view.asset()); assertEquals(key.range(), view.range());
            assertEquals(CdNativeFile.R11B, view.range().file());
            assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, view.range().domain());
            assertEquals(0x200000 + offsets[i], view.range().nativeAddress());
            assertEquals(0x200000 + offsets[i] + lengths[i], view.range().endExclusive());
            assertEquals(hashes[i], sha(view.bytes().copy()));
            assertArrayEquals(retail().programs().r11b().readFile(offsets[i], lengths[i]), view.bytes().copy());
            assertFalse(key.isPlcSource()); assertTrue(key.readerProfile().isEmpty());
            for (var kind : CdR11bAssets.ReaderKind.values())
                assertThrows(IllegalArgumentException.class, () -> a.readerView(key, kind));
            assertThrows(IllegalArgumentException.class, () -> new CdR11bLoadPlan.PlcEntry(key, key.range(), 0x87E0));
            assertThrows(IndexOutOfBoundsException.class, () -> view.bytes().unsignedByte(key.length()));
        }
        assertEquals(36, Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
        assertEquals(37, Arrays.stream(CdR11bAssets.Asset.values()).filter(k -> k.readerProfile().isPresent()).count());
    }

    @Test void fullNativeLiteralsKeepBothScriptsBothMappingFamiliesAndActualEven() throws Exception {
        var a = retail().assets();
        var ani = a.view(ANI_PATA_BATA).bytes();
        var normal = a.view(MAP_SPR_PATA_BATA1).bytes(); var damaged = a.view(MAP_SPR_PATA_BATA2).bytes();
        assertArrayEquals(NATIVE_ANIMATIONS, ani.copy());
        assertArrayEquals(NATIVE_NORMAL, normal.copy()); assertArrayEquals(NATIVE_DAMAGED, damaged.copy());
        assertEquals(4, (short) ani.unsignedWord(0)); assertEquals(11, (short) ani.unsignedWord(2));
        assertEquals(7, ani.unsignedByte(4)); assertEquals(3, ani.unsignedByte(11));
        assertArrayEquals(HexFormat.of().parseHex("0000010201ff"), ani.slice(5, 6).copy());
        assertArrayEquals(ani.slice(5, 6).copy(), ani.slice(12, 6).copy());
        int[] roots = {6, 17, 28}, normalPieces = {2, 2, 1}, damagedPieces = {2, 2, 2};
        for (int frame = 0; frame < roots.length; frame++) {
            assertEquals(roots[frame], (short) normal.unsignedWord(frame * 2));
            assertEquals(roots[frame], (short) damaged.unsignedWord(frame * 2));
            assertEquals(normalPieces[frame], normal.unsignedByte(roots[frame]));
            assertEquals(damagedPieces[frame], damaged.unsignedByte(roots[frame]));
        }
        assertEquals(40, maximumSourceTile(normal)); assertEquals(49, maximumSourceTile(damaged));
        assertEquals(-16, (byte) normal.unsignedByte(7)); assertEquals(-16, (byte) normal.unsignedByte(11));
        assertEquals(0x29, damaged.unsignedWord(14)); assertEquals(0x31, damaged.unsignedWord(31));
        assertEquals(0, damaged.unsignedByte(39)); // Real final EVEN, inside the authenticated40-byte include.
        assertThrows(IndexOutOfBoundsException.class, () -> ani.slice(18, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> normal.slice(34, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> damaged.slice(40, 1));
    }

    @Test void originalIndexPlacementAndPcRelativeBodyOperandsSelectTheseActualBRoots() throws Exception {
        var f = retail(); var a = f.assets(); var mmd = f.programs().r11b();
        assertEquals(0x20B272L, a.view(OBJECT_INDEX).bytes().unsignedLong((0x3E - 1) * 4));
        assertArrayEquals(HexFormat.of().parseHex("0020b272"), mmd.readFile(0x3D2C, 4));
        assertEquals("7720507fabd986167ab2edbf77c9a09adc65efe87fb409fd8147a5bae9955a60",
                sha(mmd.readFile(0xB272, 258)));
        assertArrayEquals(HexFormat.of().parseHex("43fa00ae"), mmd.readFile(0xB2D6, 4));
        assertArrayEquals(HexFormat.of().parseHex("43fa00bc"), mmd.readFile(0xB2EA, 4));
        assertArrayEquals(HexFormat.of().parseHex("43fa0018"), mmd.readFile(0xB35A, 4));
        assertEquals(MAP_SPR_PATA_BATA1.range().nativeAddress(), 0x20B2D8 + (short) 0x00AE);
        assertEquals(MAP_SPR_PATA_BATA2.range().nativeAddress(), 0x20B2EC + (short) 0x00BC);
        assertEquals(ANI_PATA_BATA.range().nativeAddress(), 0x20B35C + (short) 0x0018);
        assertArrayEquals(HexFormat.of().parseHex("70014eb90020cf72"), mmd.readFile(0xB2BA, 8));
        assertArrayEquals(HexFormat.of().parseHex("43f90020cf94"), mmd.readFile(0xCF72, 6));
        var row = a.view(OBJECT_PLACEMENT_RECORDS).bytes().slice(361 * 8, 8);
        assertArrayEquals(HexFormat.of().parseHex("1c470221be002000"), row.copy());
        assertArrayEquals(row.copy(), mmd.readFile(0x82A6, 8));
        assertEquals(0x3E, row.unsignedByte(4) & 0x7F); assertEquals(0, row.unsignedByte(5));
        assertEquals(0x20, row.unsignedByte(6)); assertEquals(0, row.unsignedByte(7));
        // Placement/operand data neither supplies a live64-byte slot nor proves dispatch or a returned frame.
    }

    @Test void signedRelativeSelectorOneUsesFullSubtypeByteWithinTheGenuineSeventyTwoBytes() throws Exception {
        var tiles = retail().assets().view(OBJECT_TILE_IDS).bytes();
        assertEquals(72, OBJECT_TILE_IDS.length()); assertArrayEquals(NATIVE_TILE_IDS, tiles.copy());
        assertEquals("7f6759c3ab336c2eee70a2fea5128e9920c86d418590ae557e0310948c563da1", sha(tiles.copy()));
        int relative = (short) tiles.unsignedWord(2);
        assertEquals(0x26, relative); assertEquals(0x20CFBA, OBJECT_TILE_IDS.range().nativeAddress() + relative);
        assertEquals(0x243F, tiles.unsignedWord(relative));
        assertEquals(0x23ED, tiles.unsignedWord(relative + (1 << 1))); // Actual neighboring body, no new semantic bound.
        assertEquals(0, tiles.unsignedWord(relative + (16 << 1))); // Last real word; subtype2 is not nibble-masked.
        assertEquals(70, relative + (16 << 1));
        assertThrows(IndexOutOfBoundsException.class, () -> tiles.unsignedWord(relative + (17 << 1)));
        assertThrows(IndexOutOfBoundsException.class, () -> tiles.unsignedWord(relative + (0x80 << 1)));
        assertThrows(IndexOutOfBoundsException.class, () -> tiles.unsignedWord(relative + (0xFF << 1)));
        assertEquals(0x43F, tiles.unsignedWord(relative) & 0x7FF); assertEquals(0x87E0, 0x43F << 5);
    }

    @Test void existingArtSourceAndFiniteReadersRemainDistinctFromTheThreeMetadataViews() throws Exception {
        var f = retail(); var a = f.assets(); var p = ART_PATA_BATA.readerProfile().orElseThrow();
        assertEquals(49, ART_PATA_BATA.ordinal()); assertTrue(ART_PATA_BATA.isPlcSource());
        assertEquals(0x37F34, ART_PATA_BATA.fileOffset()); assertEquals(714, ART_PATA_BATA.length());
        assertEquals(714, p.sourceBlobRange().length()); assertEquals(714, p.javaInputRange().length());
        assertEquals(715, p.nativeInputRange().length()); assertEquals(714, p.javaConsumedInputBytes());
        assertEquals(1600, p.outputBytes()); assertEquals(50, p.outputBytes() / 32);
        assertEquals("e492d968000f8097ed65ddaaf3734c11dce4d389ea74488610defa87f923f34f",
                sha(a.view(ART_PATA_BATA).bytes().copy()));
        assertEquals(0x8032, a.view(ART_PATA_BATA).bytes().unsignedWord(0));
        assertEquals(715, a.readerView(ART_PATA_BATA, CdR11bAssets.ReaderKind.NATIVE).bytes().length());
        for (int offset : new int[]{0xF254, 0xF29E, 0xF2CA, 0xF350})
            assertArrayEquals(HexFormat.of().parseHex("00237f3487e0"), f.programs().r11b().readFile(offset, 6));
        // Header and PLC operands are source facts; no decoder, service, current art or pixels are exercised.
    }

    @Test void detachedTableCopiesCannotAlterOriginalSourceOrExistingCachedFormatOwners() throws Exception {
        var f = retail(); var a = f.assets();
        assertSame(f, retail()); assertSame(a, retail().assets());
        var terrain = a.terrain(); var plan = a.loadPlan(); var placements = a.placements();
        var sonic = a.sonic(); var player = a.player();
        for (var key : TABLES) {
            var first = a.view(key); byte[] copy = first.bytes().copy(); int original = first.bytes().unsignedByte(0);
            copy[0] ^= 0x55; assertNotEquals(original, copy[0] & 0xFF);
            assertEquals(original, first.bytes().unsignedByte(0));
            assertEquals(original, a.view(key).bytes().unsignedByte(0));
            assertThrows(ReadOnlyBufferException.class, () -> first.bytes().buffer().put(0, (byte) 1));
            var buffer = first.bytes().buffer(); buffer.get(); assertEquals(0, first.bytes().buffer().position());
            assertArrayEquals(f.programs().r11b().readFile(key.fileOffset(), key.length()), first.bytes().copy());
        }
        assertSame(terrain, a.terrain()); assertSame(plan, a.loadPlan()); assertSame(placements, a.placements());
        assertSame(sonic, a.sonic()); assertSame(player, a.player());
        assertEquals(PAST_SHA, sha(f.programs().r11b().readFile(0, 262144)));
    }

    @Test void foreignSourceSameHeaderPresentAndTableMutationsCannotAuthenticatePast() throws Exception {
        var f = retail(); var original = f.programs().r11b();
        var foreign = mock(CdTimeWarpPrograms.class);
        when(foreign.source()).thenReturn(new CdTimeWarpPrograms.Source(CdReleaseVariant.JAPAN,
                CdRetailReleases.USA.normalizedSha256(), "unused", PAST_SHA));
        assertThrows(IOException.class, () -> CdR11bAssets.fromPrograms(foreign));
        verify(foreign, never()).r11b(); verify(foreign, never()).warp();
        assertEquals(original.header(), f.present().header());
        var present = mock(CdTimeWarpPrograms.class);
        when(present.source()).thenReturn(f.programs().source()); when(present.r11b()).thenReturn(f.present());
        assertThrows(IOException.class, () -> CdR11bAssets.fromPrograms(present)); verify(present, never()).warp();
        int[] changedOffsets = {ANI_PATA_BATA.fileOffset() + 17, MAP_SPR_PATA_BATA1.fileOffset(),
                MAP_SPR_PATA_BATA2.fileOffset() + 39}; // Script terminator, mapping directory, real final EVEN.
        for (int offset : changedOffsets) {
            byte[] changed = original.readFile(0, 262144); changed[offset] ^= 1;
            var wrong = CdMmdImage.parse(changed); assertEquals(original.header(), wrong.header());
            var bundle = mock(CdTimeWarpPrograms.class);
            when(bundle.source()).thenReturn(f.programs().source()); when(bundle.r11b()).thenReturn(wrong);
            assertThrows(IOException.class, () -> CdR11bAssets.fromPrograms(bundle)); verify(bundle, never()).warp();
        }
        assertEquals(PAST_SHA, sha(original.readFile(0, 262144)));
        assertEquals(CdStagePayload.R11B.stage(), f.assets().identity().stage());
    }

    private static int maximumSourceTile(CdAssetBytes mappings) {
        int maximum = -1;
        for (int frame = 0; frame < 3; frame++) {
            int root = (short) mappings.unsignedWord(frame * 2);
            for (int piece = 0; piece < mappings.unsignedByte(root); piece++) {
                int at = root + 1 + piece * 5, size = mappings.unsignedByte(at + 1);
                int width = ((size >>> 2) & 3) + 1, height = (size & 3) + 1;
                maximum = Math.max(maximum, (mappings.unsignedWord(at + 2) & 0x7FF) + width * height - 1);
            }
        }
        return maximum;
    }

    private String retainedRoot;
    private Fixture retained;
    private record Fixture(CdTimeWarpPrograms programs, CdR11bAssets assets, CdMmdImage present) { }

    private Fixture retail() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assertNotNull(root, "Past Pata-Bata metadata cases require the configured original USA media root");
        assertFalse(root.isBlank(), "The original USA media root must not be blank");
        if (retained != null && root.equals(retainedRoot)) { return retained; }
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured USA source must name exactly one existing CUE");
            cue = cues.getFirst();
        }
        CdTimeWarpPrograms programs; CdMmdImage present;
        try (var owner = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            programs = CdTimeWarpPrograms.fromPrograms(owner); present = owner.mmd(CdNativeFile.R11A);
        }
        var assets = CdR11bAssets.fromPrograms(programs);
        assertEquals(PAST_SHA, sha(programs.r11b().readFile(0, 262144)));
        retained = new Fixture(programs, assets, present); retainedRoot = root;
        return retained; // Only immutable detached values; no retained file handles or execution verdict.
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
