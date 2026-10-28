package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR11bAssets.Asset.*;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed metadata controls confer no media identity; the separate retail check uses actual supplied R11B. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11bLoadPlan {
    private static final String INDEX = "0323cabe0223ba3a0021000000810505";
    // Independent complete N dc expansion, including all nineteen relative directory slots and real aliases.
    private static final String PLCS = "0026002e008a002601c601ce00c2010c013e0170018401aa01be00260026002601d6008a01de00000023cabe0000000e002376ba65000023718c66800023413c6c0000236d907a00002369829000002351c8920000236eba97c000238d7c9ee0002354d4a40000236856ad00002356f6b50000235cfad00000232180d8c000236b0adae0002361e2f5c0000800238bd464000023707e68c000232c966b40002373286e4000238982702000237be672c000237a027da00023850c806000237f3487e0000b00238bd464000023707e68c000232c966b40002373286e40002389827020002373ce73c00023b896776000236a7c79600023b9087b6000237a027da00023f610806000237f3487e0000700238bd464000023707e68c0002373286e4000238982702000237be672c00023850c806000237f3487e000232c966b40000700238bd464000023707e68c0002373286e4000238982702000237be672c00023850c80600023f2ac87e000232c966b40000200237be672c000237a027da00023850c80600005002373ce73c00023b896776000236a7c79600023b9087b6000237a027da00023f6108060000200237be672c00023850c806000237f3487e000000023f2ac87e0000000238d7c9ee000000023f45e9ee00000002334327880000200232e5687800023288c91000020db867de0";
    private static final int[] OFFSETS = {0xF1BE,0xF1C6,0xF222,0xF1BE,0xF35E,0xF366,0xF25A,0xF2A4,0xF2D6,
            0xF308,0xF31C,0xF342,0xF356,0xF1BE,0xF1BE,0xF1BE,0xF36E,0xF222,0xF376};

    @Test void actualMediaPlanMatchesEntireNativeIndexPlcAndPaletteBytes() throws Exception {
        var f = retail(); var a = f.assets(); var p = a.loadPlan();
        assertEquals("96b52218edc1462781e36a978b769918c3bd260a50f5cd824ef1d62e155c23d7",
                sha(f.programs().r11b().readFile(0xF188, 514)));
        assertEquals("9c7ee668df0b36a446de88501d146b5c16bdd37d3c0ede2c626eb6b70c8afb61", sha(a.view(PLC_LISTS).bytes().copy()));
        assertEquals("abcb4db263f7966ba4cf1e38e8f0cb147020846b0f82d4922a648bcf838569e2", sha(p.initialPalette().copy()));
        assertEquals(19, p.plcs().size()); assertEquals(4, p.sections().size());
        assertEquals(new CdR11bLoadPlan.InitialCentre(0x50, 0x1E8), p.initialCentre());
        assertEquals(new CdR11bLoadPlan.Bounds(4, 0, 0x2897, 0, 0x310, 0x60), p.bounds());
    }

    @Test void levelIndexRetainsExactTagsPointersTrailingAndPaletteIds() {
        var p = CdR11bLoadPlan.decodeLevelIndex(bytes(INDEX));
        assertEquals(3, p.levelArt().plcId()); assertEquals(0x23CABE, p.levelArt().nativeAddress());
        assertEquals(2, p.blocks().plcId()); assertEquals(0x23BA3A, p.blocks().nativeAddress());
        assertEquals(0x210000, p.chunksNativeAddress());
        assertEquals("0081", HexFormat.of().formatHex(p.uninterpretedTrailing().copy()));
        assertEquals("0505", HexFormat.of().formatHex(p.paletteIds().copy()));
        for (int at : new int[]{0, 3, 4, 7, 8, 11, 14, 15}) {
            var b = bytes(INDEX); b[at] ^= 1;
            assertThrows(IllegalArgumentException.class, () -> CdR11bLoadPlan.decodeLevelIndex(b));
        }
        var trailing = bytes(INDEX); trailing[13] ^= 1;
        assertThrows(UnsupportedOperationException.class, () -> CdR11bLoadPlan.decodeLevelIndex(trailing));
        for (long bad : new long[]{-1, 0x100000000L, 0x13200000L, 0x03E00000L, 0x031FFFFFL, 0x03240000L}) {
            assertThrows(IllegalArgumentException.class, () -> new CdR11bLoadPlan.TaggedPointer(bad));
        }
    }

    @Test void primitiveCentreAndBoundsAreUnsignedFormatsWithExactExtents() {
        assertEquals(new CdR11bLoadPlan.InitialCentre(65535, 32768),
                CdR11bLoadPlan.decodeInitialCentre(bytes("ffff8000")));
        assertEquals(new CdR11bLoadPlan.Bounds(4, 0, 0x2897, 0, 0x310, 0x60),
                CdR11bLoadPlan.decodeBounds(bytes("000400002897000003100060")));
        for (int n : new int[]{0, 3, 5}) {
            assertThrows(IllegalArgumentException.class, () -> CdR11bLoadPlan.decodeInitialCentre(new byte[n]));
        }
        for (int n : new int[]{0, 11, 13}) {
            assertThrows(IllegalArgumentException.class, () -> CdR11bLoadPlan.decodeBounds(new byte[n]));
        }
        assertThrows(IllegalArgumentException.class, () -> CdR11bLoadPlan.decodeBounds(bytes("000400020001000000010060")));
        assertThrows(IllegalArgumentException.class, () -> CdR11bLoadPlan.decodeBounds(bytes("000400000001000200010060")));
        assertThrows(IllegalArgumentException.class, () -> new CdR11bLoadPlan.InitialCentre(-1, 0));
    }

    @Test void standardFifteenEntryListKeepsNativeOrderAndDestinations() throws Exception {
        var p = plan(); var std = p.plcs().get(1);
        assertEquals(15, std.entries().size()); assertEquals(92, std.bodyRange().length());
        String expected = "ART_TUNNEL_DOOR:6500 ART_FLOAT_BLOCK:6680 ART_TITLE_CARD:6c00 ART_TITLE_CARD_TEXT:7a00 "
                + "ART_CHECKPOINT:9000 ART_DIAGONAL_SPRING:9200 ART_PLATFORM:97c0 ART_ANIMALS:9ee0 ART_SPRINGS:a400 "
                + "ART_HUD:ad00 ART_MONITOR_TIME_POSTS:b500 ART_EXPLOSIONS:d000 ART_POINTS:d8c0 ART_FLOWER:dae0 ART_RINGS:f5c0";
        assertEquals(expected, std.entries().stream().map(e -> e.source().name() + ":" + Integer.toHexString(e.vramAddress()))
                .collect(java.util.stream.Collectors.joining(" ")));
    }

    @Test void everyPlcSlotRetainsPhysicalRangeOrderAndIntentionalAliases() throws Exception {
        var p = plan(); assertEquals(19, p.plcs().size());
        for (int slot = 0; slot < 19; slot++) {
            var l = p.plcs().get(slot); assertEquals(slot, l.nativeId());
            assertEquals(OFFSETS[slot], l.bodyRange().fileOffset());
            assertEquals(2 + 6 * l.entries().size(), l.bodyRange().length());
            for (var e : l.entries()) { assertEquals(e.source().range(), e.sourceRange()); assertTrue(e.source().isPlcSource()); }
        }
        for (int i : new int[]{3,13,14,15}) { assertEquals(p.plcs().get(0).bodyRange(), p.plcs().get(i).bodyRange()); }
        assertEquals(p.plcs().get(2).bodyRange(), p.plcs().get(17).bodyRange());
        assertEquals(ART_ROBOT_GENERATOR, p.plcs().get(8).entries().get(6).source());
        assertEquals(8, p.plcs().get(7).entries().size()); // Keep actual Cam5_Full, without inventing a Mosqui row.
    }

    @Test void fourSectionsAndPaletteRowsThreeAndFiveRemainMetadataOnly() throws Exception {
        var p = plan();
        assertEquals(List.of(new CdR11bLoadPlan.SectionArt(0x1100,2,9), new CdR11bLoadPlan.SectionArt(0x1A00,6,10),
                new CdR11bLoadPlan.SectionArt(0x2000,7,11), new CdR11bLoadPlan.SectionArt(0xFFFF,8,12)), p.sections());
        var s = p.paletteBindings().get(0); var l = p.paletteBindings().get(1);
        assertEquals(3, s.nativeId()); assertEquals(0xFB00, s.destinationWord()); assertEquals(7, s.longwordCounter());
        assertEquals(PAL_SONIC, s.source()); assertEquals(16, s.colorCount());
        assertEquals(5, l.nativeId()); assertEquals(0xFB20, l.destinationWord()); assertEquals(23, l.longwordCounter());
        assertEquals(PAL_LEVEL, l.source()); assertEquals(48, l.colorCount());
        assertEquals(128, p.initialPalette().length()); assertEquals(0x0001, p.colorWord(0)); assertEquals(0x5E5F, p.colorWord(63));
        assertThrows(IndexOutOfBoundsException.class, () -> p.colorWord(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> p.colorWord(64));
    }

    @Test void primitivePlcHonorsPositiveCountMaxFifteenExactWidthAndKnownSources() {
        assertEquals(1, CdR11bLoadPlan.decodePlcBody(bytes("00000023cabe0000")).size());
        var all = bytes(PLCS); assertEquals(15, CdR11bLoadPlan.decodePlcBody(Arrays.copyOfRange(all, 0x2E, 0x8A)).size());
        for (String bad : new String[]{"ffff", "000f", "00000023cabe0001", "00000323cabe0000",
                "0000002000000000", "00000023ba3a0000", "00000023cabeffe0", "00000023cabe000000"}) {
            assertThrows(IllegalArgumentException.class, () -> CdR11bLoadPlan.decodePlcBody(bytes(bad)), bad);
        }
        assertThrows(NullPointerException.class, () -> CdR11bLoadPlan.decodePlcBody(null));
    }

    @Test void valueRecordsDefendWidthsMembershipAndMutableListInputs() throws Exception {
        var p = plan(); var original = p.plcs().get(1);
        var mutable = new ArrayList<>(original.entries());
        var copied = new CdR11bLoadPlan.PlcList(1, original.bodyRange(), mutable);
        mutable.clear(); assertEquals(15, copied.entries().size());
        assertThrows(UnsupportedOperationException.class, () -> copied.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> p.sections().clear());
        assertThrows(UnsupportedOperationException.class, () -> p.paletteBindings().clear());
        assertThrows(IllegalArgumentException.class, () -> new CdR11bLoadPlan.SectionArt(0x10000, 2, 9));
        assertThrows(IllegalArgumentException.class, () -> new CdR11bLoadPlan.SectionArt(1, 19, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdR11bLoadPlan.Bounds(0, 2, 1, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdR11bLoadPlan.PlcEntry(PAL_LEVEL, PAL_LEVEL.range(), 0));
        assertThrows(IllegalArgumentException.class, () -> new CdR11bLoadPlan.PlcEntry(ART_RINGS, ART_RINGS.range(), 1));
        var palette = p.initialPalette().copy(); palette[0] = 1; assertEquals(0, p.initialPalette().unsignedByte(0));
    }

    @Test void wrongImageExtentHeaderOrIndexCannotDecodeAPlan() throws Exception {
        assertThrows(NullPointerException.class, () -> CdR11bLoadPlan.decode(null));
        byte[] badHeader = constructed(); badHeader[1] = 1;
        assertThrows(IllegalArgumentException.class, () -> CdR11bLoadPlan.decode(CdMmdImage.parse(badHeader)));
        var small = Arrays.copyOf(constructed(), 0x3FFFF);
        assertThrows(IllegalArgumentException.class, () -> CdR11bLoadPlan.decode(CdMmdImage.parse(small)));
        byte[] wrong = constructed(); wrong[0xF188] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> CdR11bLoadPlan.decode(CdMmdImage.parse(wrong)));
    }

    @Test void signedOddAndOutsidePlcDirectoryPointersRejectBeforeBodies() throws Exception {
        for (int value : new int[]{0xFFFF, 0x8000, 0x25, 0x27, 498, 500}) {
            var b = constructed(); word(b, 0xF198, value);
            assertThrows(IllegalArgumentException.class, () -> CdR11bLoadPlan.decode(CdMmdImage.parse(b)));
        }
        var b = constructed(); word(b, 0xF1C6, 0xFFFF);
        assertThrows(IllegalArgumentException.class, () -> CdR11bLoadPlan.decode(CdMmdImage.parse(b)));
    }

    @Test void fullPlanAuthenticatesAliasesRowsAndDestinationsRatherThanOnlyShape() throws Exception {
        for (int at : new int[]{0xF19E, 0xF1C8, 0xF1CC, 0xF2A6, 0xF308 + 6}) {
            byte[] b = constructed(); b[at] ^= 2;
            assertThrows(IllegalArgumentException.class, () -> CdR11bLoadPlan.decode(CdMmdImage.parse(b)));
        }
        var b = constructed(); word(b, 0xF198 + 17 * 2, 0x26);
        assertThrows(IllegalArgumentException.class, () -> CdR11bLoadPlan.decode(CdMmdImage.parse(b)));
    }

    @Test void sectionThresholdAndInitialOrUpdateIdChangesRejectWithoutRunningSelection() throws Exception {
        for (int at = 0xCF5A; at < 0xCF72; at += 2) {
            byte[] b = constructed(); b[at + 1] ^= 1;
            assertThrows(IllegalArgumentException.class, () -> CdR11bLoadPlan.decode(CdMmdImage.parse(b)));
        }
    }

    @Test void paletteAndTerrainPointerMutationsDoNotBorrowPresentTables() throws Exception {
        for (int at : new int[]{0x5C0 + 3 * 8, 0x5C0 + 3 * 8 + 4, 0x5C0 + 5 * 8 + 6,
                0x3B594, 0x3B596, 0x1A56, 0x1A56 + 28}) {
            byte[] b = constructed(); b[at + 1] ^= 2;
            assertThrows(IllegalArgumentException.class, () -> CdR11bLoadPlan.decode(CdMmdImage.parse(b)));
        }
    }

    private static CdR11bLoadPlan plan() throws Exception { return CdR11bLoadPlan.decode(CdMmdImage.parse(constructed())); }
    private static byte[] bytes(String hex) { return HexFormat.of().parseHex(hex); }
    private static void word(byte[] b, int at, int value) { b[at] = (byte) (value >>> 8); b[at + 1] = (byte) value; }
    private static void lng(byte[] b, int at, int value) { word(b, at, value >>> 16); word(b, at + 2, value); }
    private static byte[] constructed() {
        byte[] b = new byte[262144];
        ByteBuffer.wrap(b).put((byte)0).put((byte)0).putInt(0).putShort((short)0)
                .putInt(0x200100).putInt(0x20010C).putInt(0x200112);
        System.arraycopy(bytes(INDEX), 0, b, 0xF188, 16);
        System.arraycopy(bytes(PLCS), 0, b, 0xF198, 498);
        System.arraycopy(bytes("005001e8"), 0, b, 0x29D4, 4);
        System.arraycopy(bytes("000400002897000003100060"), 0, b, 0x2910, 12);
        System.arraycopy(bytes("11001a002000ffff0009000a000b000c0002000600070008"), 0, b, 0xCF5A, 24);
        word(b, 0x3B594, 0x48); word(b, 0x3B596, 0xF2);
        for (int i = 0; i < 8; i++) { lng(b, 0x1A56 + 4 * i, 0x23B274); }
        lng(b, 0x5C0 + 3 * 8, 0x2006F0); word(b, 0x5C0 + 3 * 8 + 4, 0xFB00); word(b, 0x5C0 + 3 * 8 + 6, 7);
        lng(b, 0x5C0 + 5 * 8, 0x200710); word(b, 0x5C0 + 5 * 8 + 4, 0xFB20); word(b, 0x5C0 + 5 * 8 + 6, 23);
        for (int i = 0; i < 32; i++) { b[0x6F0 + i] = (byte)i; }
        for (int i = 0; i < 96; i++) { b[0x710 + i] = (byte)i; }
        return b; // Finite metadata fixture; it is deliberately not an authentic R11B asset owner.
    }

    private String retainedRoot;
    private Fixture retained;
    private record Fixture(CdTimeWarpPrograms programs, CdR11bAssets assets, CdMmdImage present) { }
    private Fixture retail() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Actual USA media requires soniccd.media.root");
        if (retained != null && root.equals(retainedRoot)) { return retained; }
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured USA source must name exactly one existing CUE");
            cue = cues.getFirst();
        }
        CdTimeWarpPrograms programs; CdMmdImage present;
        try (var owner = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            programs = CdTimeWarpPrograms.fromPrograms(owner); present = owner.mmd(CdNativeFile.R11A);
        }
        retained = new Fixture(programs, CdR11bAssets.fromPrograms(programs), present); retainedRoot = root;
        return retained; // Immutable detached values only; no file owner/handle or cached validation verdict.
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

}
