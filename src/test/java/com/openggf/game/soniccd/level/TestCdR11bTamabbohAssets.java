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
 * Original-USA Past Tamabboh metadata only. No object body, PLC service, Tamabboh art decode,
 * current residency, World or pixels execute; the unchanged aggregate decodes its core terrain.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * ObjTamabboh_Init (Tamabboh/Main.asm:29-58), ObjTamabbohMissile_Init/Animate (226-282);
 * Ani_Tamabboh1/2 and Ani_TamabbohMissile (Data/Animations (Normal).asm:1-5,
 * Data/Animations (Damaged).asm:1-5, Data/Animations (Missile).asm:1-3);
 * MapSpr_Tamabboh1/2 and MapSpr_TamabbohMissile (Data/Mappings.asm:1-59,
 * Data/Mappings (Missile).asm:1-39); SetObjectTileID (Level/Object Tile ID Set.asm:7-17).
 * Full literal streams include genuine trailing bytes, not padding. Source operand observations
 * do not claim a dispatched body or completed controller frame. Author discovery and execution UNRUN.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11bTamabbohAssets {
    private static final String PAST_SHA =
            "b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265";
    private static final byte[] NATIVE_NORMAL_ANIMATIONS = HexFormat.of().parseHex(
            "000400081d0001ff3b02fc00");
    private static final byte[] NATIVE_DAMAGED_ANIMATIONS = HexFormat.of().parseHex(
            "000400160500010001000100010001000100010001fc0b02fc00");
    private static final byte[] NATIVE_PARENT_MAPPINGS = HexFormat.of().parseHex(
            "00060022003203f10d0000f0010c0008f00908000cf80006000c001c01f00f002bf0"
                    + "03f20d0000f0020c000ff00a080013f802f0080016f7f80e0019ef00");
    private static final byte[] NATIVE_MISSILE_ANIMATIONS = HexFormat.of().parseHex(
            "00020200010203ff");
    private static final byte[] NATIVE_MISSILE_MAPPINGS = HexFormat.of().parseHex(
            "0008000e0014001f01fc000025fc01fc000026fc02f8040027f800041027f802f8040029f800041029f8");
    private static final byte[] NATIVE_TILE_IDS = HexFormat.of().parseHex(
            "002400260028002a002c002e00380032003600340030003a003c003e0040004200440046"
                    + "2396243f23ed2000240343340320038140004000037203468328000000000490039e0000");
    private static final List<CdR11bAssets.Asset> TABLES = List.of(
            ANI_TAMABBOH_NORMAL, ANI_TAMABBOH_DAMAGED, MAP_SPR_TAMABBOH,
            ANI_TAMABBOH_MISSILE, MAP_SPR_TAMABBOH_MISSILE);

    @BeforeAll void requireAuthenticatedOriginalUsaMedia() throws Exception {
        retail();
    }

    @Test void exactlyFiveFiniteViewsAppendWithoutNewReadersPlcsOrArtOwners() throws Exception {
        var f = retail(); var a = f.assets();
        assertEquals(114, CdR11bAssets.Asset.values().length);
        assertEquals(TABLES, Arrays.asList(CdR11bAssets.Asset.values()).subList(95, 100));
        assertEquals(92, ANI_PATA_BATA.ordinal()); assertEquals(94, MAP_SPR_PATA_BATA2.ordinal());
        String[] labels = {"Ani_Tamabboh1", "Ani_Tamabboh2", "MapSpr_Tamabboh1/MapSpr_Tamabboh2",
                "Ani_TamabbohMissile", "MapSpr_TamabbohMissile"};
        int[] offsets = {0xB79A, 0xB7A6, 0xB7C0, 0xB8C0, 0xB8C8};
        int[] lengths = {12, 26, 62, 8, 42};
        String[] hashes = {
                "ad1878521436e3e2ad03488f523fb96cda2ce64229265514a4bb6972a7e50474",
                "09a26ea9ee38de8e1799d02b191d160beac335155b9d6bae6c3d1ed678577f84",
                "e30906f183ff4995fb9bfb0661be6003d3cd0431caa10b9d784ecf64a5b36847",
                "764bc1e99533aeb906c6b6ecf4627f5fc11904307d477b1827a49776f41c57a5",
                "10cba8d961bb868885a64ddb360f0182e18b4b5256eebac3665363c984e992cb"};
        for (int i = 0; i < TABLES.size(); i++) {
            var key = TABLES.get(i); var view = a.view(key);
            assertEquals(95 + i, key.ordinal()); assertEquals(labels[i], key.nativeLabel());
            assertEquals(offsets[i], key.fileOffset()); assertEquals(lengths[i], key.length());
            assertSame(key, view.asset()); assertEquals(key.range(), view.range());
            assertEquals(CdNativeFile.R11B, view.range().file());
            assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, view.range().domain());
            assertEquals(0x200000 + offsets[i], view.range().nativeAddress());
            assertEquals(0x200000 + offsets[i] + lengths[i], view.range().endExclusive());
            assertEquals(hashes[i], sha(view.bytes().copy()));
            assertArrayEquals(f.programs().r11b().readFile(offsets[i], lengths[i]), view.bytes().copy());
            assertFalse(key.isPlcSource()); assertTrue(key.readerProfile().isEmpty());
            for (var kind : CdR11bAssets.ReaderKind.values())
                assertThrows(IllegalArgumentException.class, () -> a.readerView(key, kind));
            assertThrows(IllegalArgumentException.class, () -> new CdR11bLoadPlan.PlcEntry(key, key.range(), 0x8060));
            assertThrows(IndexOutOfBoundsException.class, () -> view.bytes().unsignedByte(key.length()));
        }
        assertEquals(36, Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
        assertEquals(37, Arrays.stream(CdR11bAssets.Asset.values()).filter(k -> k.readerProfile().isPresent()).count());
    }

    @Test void completeAnimationStreamsKeepBothDirectoriesCommandsAndRealFinalBytes() throws Exception {
        var a = retail().assets();
        var normal = a.view(ANI_TAMABBOH_NORMAL).bytes();
        var damaged = a.view(ANI_TAMABBOH_DAMAGED).bytes();
        var missile = a.view(ANI_TAMABBOH_MISSILE).bytes();
        assertArrayEquals(NATIVE_NORMAL_ANIMATIONS, normal.copy());
        assertArrayEquals(NATIVE_DAMAGED_ANIMATIONS, damaged.copy());
        assertArrayEquals(NATIVE_MISSILE_ANIMATIONS, missile.copy());
        assertEquals(4, normal.unsignedWord(0)); assertEquals(8, normal.unsignedWord(2));
        assertEquals(0x1D, normal.unsignedByte(4)); assertEquals(0x3B, normal.unsignedByte(8));
        assertArrayEquals(HexFormat.of().parseHex("0001ff"), normal.slice(5, 3).copy());
        assertArrayEquals(HexFormat.of().parseHex("02fc00"), normal.slice(9, 3).copy());
        assertEquals(4, damaged.unsignedWord(0)); assertEquals(22, damaged.unsignedWord(2));
        assertEquals(5, damaged.unsignedByte(4)); assertEquals(0x0B, damaged.unsignedByte(22));
        assertArrayEquals(HexFormat.of().parseHex("00010001000100010001000100010001"),
                damaged.slice(5, 16).copy());
        assertEquals(0xFC, damaged.unsignedByte(21));
        assertArrayEquals(HexFormat.of().parseHex("02fc00"), damaged.slice(23, 3).copy());
        assertEquals(2, missile.unsignedWord(0)); assertEquals(2, missile.unsignedByte(2));
        assertArrayEquals(HexFormat.of().parseHex("00010203ff"), missile.slice(3, 5).copy());
        assertThrows(IndexOutOfBoundsException.class, () -> normal.slice(12, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> damaged.slice(26, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> missile.slice(8, 1));
    }

    @Test void fullParentAndMissileMapsRetainBothRootsAndGenuineInterleavedTail() throws Exception {
        var a = retail().assets();
        var parent = a.view(MAP_SPR_TAMABBOH).bytes(); var missile = a.view(MAP_SPR_TAMABBOH_MISSILE).bytes();
        assertArrayEquals(NATIVE_PARENT_MAPPINGS, parent.copy());
        assertArrayEquals(NATIVE_MISSILE_MAPPINGS, missile.copy());
        int[] normalRoots = {6, 34, 50}, normalPieces = {3, 3, 2};
        int[] damagedRelative = {6, 12, 28}, damagedPieces = {1, 3, 2};
        for (int i = 0; i < 3; i++) {
            assertEquals(normalRoots[i], parent.unsignedWord(i * 2));
            assertEquals(normalPieces[i], parent.unsignedByte(normalRoots[i]));
            assertEquals(damagedRelative[i], parent.unsignedWord(22 + i * 2));
            assertEquals(damagedPieces[i], parent.unsignedByte(22 + damagedRelative[i]));
        }
        assertArrayEquals(HexFormat.of().parseHex("0006000c001c01f00f002bf0"), parent.slice(22, 12).copy());
        assertEquals(0, parent.unsignedByte(61)); // Actual explicit final dc.b 0 inside the full62 span.
        assertEquals(37, maximumExclusiveTile(parent, 0, 3));
        assertEquals(59, maximumExclusiveTile(parent, 22, 3));
        int[] missileRoots = {8, 14, 20, 31}, missilePieces = {1, 1, 2, 2};
        for (int i = 0; i < 4; i++) {
            assertEquals(missileRoots[i], missile.unsignedWord(i * 2));
            assertEquals(missilePieces[i], missile.unsignedByte(missileRoots[i]));
        }
        assertEquals(43, maximumExclusiveTile(missile, 0, 4));
        assertEquals(0xF8, missile.unsignedByte(41)); // Genuine last X offset; no manufactured EVEN byte.
        assertThrows(IndexOutOfBoundsException.class, () -> parent.slice(62, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> missile.slice(42, 1));
    }

    @Test void actualUsaDispatchPcLeasAndAbsoluteMissileStoreBindPastRoots() throws Exception {
        var f = retail(); var mmd = f.programs().r11b(); var a = f.assets();
        assertArrayEquals(HexFormat.of().parseHex("d040d04043f900203c38227100fc4e91"), mmd.readFile(0x3822, 16));
        assertEquals(0x20B5BCL, a.view(OBJECT_INDEX).bytes().unsignedLong((0x22 - 1) * 4));
        assertArrayEquals(HexFormat.of().parseHex("0020b5bc"), mmd.readFile(0x3CBC, 4));
        assertArrayEquals(HexFormat.of().parseHex("0c28000100286700023a4eb90020edee"), mmd.readFile(0xB5BC, 16));
        assertArrayEquals(HexFormat.of().parseHex("70044eb90020cf72"), mmd.readFile(0xB624, 8));
        assertArrayEquals(HexFormat.of().parseHex("43f90020cf94"), mmd.readFile(0xCF72, 6));
        assertArrayEquals(HexFormat.of().parseHex("43fa018c"), mmd.readFile(0xB632, 4));
        assertArrayEquals(HexFormat.of().parseHex("45fa0162"), mmd.readFile(0xB636, 4));
        assertArrayEquals(HexFormat.of().parseHex("43fa0192"), mmd.readFile(0xB642, 4));
        assertArrayEquals(HexFormat.of().parseHex("45fa015e"), mmd.readFile(0xB646, 4));
        assertEquals(MAP_SPR_TAMABBOH.range().nativeAddress(), 0x20B634 + (short) 0x018C);
        assertEquals(ANI_TAMABBOH_NORMAL.range().nativeAddress(), 0x20B638 + (short) 0x0162);
        assertEquals(MAP_SPR_TAMABBOH.range().nativeAddress() + 22, 0x20B644 + (short) 0x0192);
        assertEquals(ANI_TAMABBOH_DAMAGED.range().nativeAddress(), 0x20B648 + (short) 0x015E);
        assertArrayEquals(HexFormat.of().parseHex("217c0020b8c80004"), mmd.readFile(0xB838, 8));
        assertArrayEquals(HexFormat.of().parseHex("43fa0008"), mmd.readFile(0xB8B6, 4));
        assertEquals(ANI_TAMABBOH_MISSILE.range().nativeAddress(), 0x20B8B8 + (short) 8);
        assertEquals(MAP_SPR_TAMABBOH_MISSILE.range().nativeAddress(), 0x20B8C8);
        assertFalse(Arrays.equals(mmd.readFile(0x3CBC, 4), f.present().readFile(0x3CBC, 4)));
        assertFalse(Arrays.equals(mmd.readFile(0xB632, 4), f.present().readFile(0xB632, 4)));
        // Static operands authenticate addresses, not a live dispatch, successful floor probe or controller frame.
    }

    @Test void genuineSeventyTwoByteTileTableAndAllElevenPlacementsKeepRawSecondaryZero() throws Exception {
        var f = retail(); var a = f.assets(); var tiles = a.view(OBJECT_TILE_IDS).bytes();
        assertEquals(72, OBJECT_TILE_IDS.length()); assertArrayEquals(NATIVE_TILE_IDS, tiles.copy());
        assertEquals("7f6759c3ab336c2eee70a2fea5128e9920c86d418590ae557e0310948c563da1", sha(tiles.copy()));
        int relative = (short) tiles.unsignedWord(4 * 2);
        assertEquals(0x2C, relative); assertEquals(0x20CFC0, OBJECT_TILE_IDS.range().nativeAddress() + relative);
        int[] neighbors = {0x2403, 0x4334, 0x0320, 0x0381, 0x4000, 0x4000, 0x0372,
                0x0346, 0x8328, 0, 0, 0x0490, 0x039E, 0};
        for (int secondary = 0; secondary < neighbors.length; secondary++)
            assertEquals(neighbors[secondary], tiles.unsignedWord(relative + (secondary << 1)));
        assertEquals(0x403, tiles.unsignedWord(relative) & 0x7FF);
        assertEquals(0x8060, (tiles.unsignedWord(relative) & 0x7FF) << 5);
        assertThrows(IndexOutOfBoundsException.class, () -> tiles.unsignedWord(relative + (14 << 1)));
        assertThrows(IndexOutOfBoundsException.class, () -> tiles.unsignedWord(relative + (0x80 << 1)));
        assertThrows(IndexOutOfBoundsException.class, () -> tiles.unsignedWord(relative + (0xFF << 1)));
        int[] rows = {148, 155, 207, 247, 248, 254, 276, 314, 373, 472, 485};
        int[] saved = {0x4A, 0x50, 0x65, 0x72, 0x73, 0x77, 0x7E, 0x93, 0xA7, 0xDF, 0xE9};
        String[] raw = {"0d900100a2002000", "0e2302d3a2ff8000", "12100211a2004000",
                "14b00365a2004000", "14b00365a2ff8000", "15100060a2ff8000",
                "15e80060a2ffc000", "1898026ba2004000", "1d600150a2002000",
                "239a00aba2002000", "24b602afa2002000"};
        assertEquals(497, a.placements().records().size());
        var actualRows = a.placements().records().stream().filter(p -> p.objectId() == 0x22)
                .map(CdObjectPlacements.Placement::index).toList();
        assertEquals(Arrays.stream(rows).boxed().toList(), actualRows);
        var records = a.view(OBJECT_PLACEMENT_RECORDS).bytes();
        for (int i = 0; i < rows.length; i++) {
            var p = a.placements().records().get(rows[i]); var bytes = records.slice(rows[i] * 8, 8);
            assertArrayEquals(HexFormat.of().parseHex(raw[i]), bytes.copy());
            assertArrayEquals(bytes.copy(), f.programs().r11b().readFile(0x775E + rows[i] * 8, 8));
            assertTrue(p.hasSavedFlag()); assertEquals(0x22, p.objectId()); assertEquals(0, p.subtype2());
            assertEquals(bytes.unsignedWord(0), p.centreX()); assertEquals(bytes.unsignedWord(2), p.packedY());
            assertEquals(bytes.unsignedByte(5), p.subtype()); assertEquals(bytes.unsignedByte(6), p.periodFlags());
            long ordinal = a.placements().records().subList(0, rows[i] + 1).stream()
                    .filter(CdObjectPlacements.Placement::hasSavedFlag).count();
            assertEquals(saved[i], ordinal);
        }
        assertEquals(0x8306, 0x775E + 373 * 8);
        // Neighbor words are address facts, not admission of nonzero secondary values or 11 actual Past visits.
    }

    @Test void existingTamabbohBlobFiniteReadersAndActualPlcOperandsAreUnchanged() throws Exception {
        var f = retail(); var a = f.assets(); var p = ART_TAMABBOH.readerProfile().orElseThrow();
        assertTrue(ART_TAMABBOH.ordinal() < 95); assertTrue(ART_TAMABBOH.isPlcSource());
        assertEquals(0x3850C, ART_TAMABBOH.fileOffset()); assertEquals(1142, ART_TAMABBOH.length());
        assertEquals(1142, p.sourceBlobRange().length()); assertEquals(1142, p.javaInputRange().length());
        assertEquals(1143, p.nativeInputRange().length()); assertEquals(1142, p.javaConsumedInputBytes());
        assertEquals(1888, p.outputBytes()); assertEquals(59, p.outputBytes() / 32);
        assertEquals("39b42a53ad2a2b1e50c73d2f0abdb918304fd8987499af1a4d8773b9239c309f",
                sha(a.view(ART_TAMABBOH).bytes().copy()));
        assertEquals(0x803B, a.view(ART_TAMABBOH).bytes().unsignedWord(0));
        assertArrayEquals(a.view(ART_TAMABBOH).bytes().copy(),
                a.readerView(ART_TAMABBOH, CdR11bAssets.ReaderKind.JAVA).bytes().copy());
        var nativeInput = a.readerView(ART_TAMABBOH, CdR11bAssets.ReaderKind.NATIVE).bytes();
        assertEquals(1143, nativeInput.length());
        assertArrayEquals(a.view(ART_TAMABBOH).bytes().copy(), nativeInput.slice(0, 1142).copy());
        assertEquals(0, nativeInput.unsignedByte(1142));
        assertArrayEquals(HexFormat.of().parseHex("0015"), f.programs().r11b().readFile(0x38982, 2));
        for (int offset : new int[]{0xF24E, 0xF2C4, 0xF2F6, 0xF316, 0xF34A, 0xF24E})
            assertArrayEquals(HexFormat.of().parseHex("0023850c8060"), f.programs().r11b().readFile(offset, 6));
        assertEquals(ART_TAMABBOH.range(), p.sourceBlobRange());
        // Inherited1142/1143 profile and genuine Springboard00 are facts; this test does not measure decoder minima.
    }

    @Test void detachedCopiesLeaveOriginalSourceAndEveryExistingCachedFormatOwnerUnchanged() throws Exception {
        var f = retail(); var a = f.assets();
        assertSame(f, retail()); assertSame(a, retail().assets());
        var identity = a.identity(); var source = f.programs().source();
        var terrain = a.terrain(); var plan = a.loadPlan(); var placements = a.placements();
        var sonic = a.sonic(); var player = a.player(); var profile = ART_TAMABBOH.readerProfile().orElseThrow();
        for (var key : TABLES) {
            var view = a.view(key); byte[] copy = view.bytes().copy(); int first = view.bytes().unsignedByte(0);
            copy[0] ^= 0x55; assertNotEquals(first, copy[0] & 0xFF);
            assertEquals(first, view.bytes().unsignedByte(0)); assertEquals(first, a.view(key).bytes().unsignedByte(0));
            assertThrows(ReadOnlyBufferException.class, () -> view.bytes().buffer().put(0, (byte) 1));
            var buffer = view.bytes().buffer(); buffer.get(); assertEquals(0, view.bytes().buffer().position());
            assertArrayEquals(f.programs().r11b().readFile(key.fileOffset(), key.length()), view.bytes().copy());
        }
        assertSame(identity, a.identity()); assertSame(source, f.programs().source());
        assertSame(terrain, a.terrain()); assertSame(plan, a.loadPlan()); assertSame(placements, a.placements());
        assertSame(sonic, a.sonic()); assertSame(player, a.player());
        assertEquals(profile, ART_TAMABBOH.readerProfile().orElseThrow());
        assertEquals(PAST_SHA, sha(f.programs().r11b().readFile(0, 262144)));
    }

    @Test void foreignRegionSameHeaderPresentAndMutatedTablesCannotAuthenticatePast() throws Exception {
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
        for (var key : TABLES) {
            byte[] changed = original.readFile(0, 262144); changed[key.fileOffset() + key.length() - 1] ^= 1;
            var wrong = CdMmdImage.parse(changed); assertEquals(original.header(), wrong.header());
            var bundle = mock(CdTimeWarpPrograms.class);
            when(bundle.source()).thenReturn(f.programs().source()); when(bundle.r11b()).thenReturn(wrong);
            assertThrows(IOException.class, () -> CdR11bAssets.fromPrograms(bundle)); verify(bundle, never()).warp();
        }
        assertEquals(PAST_SHA, sha(original.readFile(0, 262144)));
        assertEquals(CdStagePayload.R11B.stage(), f.assets().identity().stage());
    }

    private static int maximumExclusiveTile(CdAssetBytes mappings, int root, int frames) {
        int maximum = 0;
        for (int frame = 0; frame < frames; frame++) {
            int at = root + (short) mappings.unsignedWord(root + frame * 2);
            for (int piece = 0; piece < mappings.unsignedByte(at); piece++) {
                int pos = at + 1 + piece * 5, size = mappings.unsignedByte(pos + 1);
                int tiles = (((size >>> 2) & 3) + 1) * ((size & 3) + 1);
                maximum = Math.max(maximum, (mappings.unsignedWord(pos + 2) & 0x7FF) + tiles);
            }
        }
        return maximum;
    }

    private String retainedRoot;
    private Fixture retained;
    private record Fixture(CdTimeWarpPrograms programs, CdR11bAssets assets, CdMmdImage present) { }

    private Fixture retail() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assertNotNull(root, "Past Tamabboh metadata cases require the configured original USA media root");
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
        return retained; // Immutable detached values only; no retained file handle or execution verdict.
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
