package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.resources.CdR11bCameraArtResidency;
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
 * Original-USA B raw source views only. Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * _Objects/Level End/Main.asm:568-702; complete Data/Mappings (Post).asm;
 * Palmtree Panic/Data (Act 1 Past).asm:PLC_End/MapSpr_GoalSignpost.
 * No object, native execution, PLC service, upload, current residency or World runs here.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11bGoalPostAssets {
    private static final List<CdR11bAssets.Asset> KEYS = List.of(MAP_SPR_GOAL_SIGNPOST, GOAL_POST_BASE_TILES);
    private static final byte[] MAPPINGS = hex(
            "000c0022003200420058006804d40b0000e8d40b000c00f4050044f8040c0048f000"
                    + "03d40f0018f0f4050044f8040c0048f003d4030028fcf4050044f8040c0048f004d4"
                    + "0b002ce8d40b003800f4050044f8040c0048f00003d40f0818f0f4050044f8040c0048"
                    + "f004e4020000ece40f0003f404010013fc14080015f400");
    private static final byte[] TILES = hex(
            "035a04f704f704f7038104f704f704f70300030003000300030003000300030004f2"
                    + "04f204f204f204f204f204f204f202ba02cc02b302b102ba02cc02b302b10254022c"
                    + "029402380278028a02bc029803ae03ae03ae03ae03ae03ae03ae03ae02200221024c"
                    + "0236023e024a025d0246");
    private Fixture retained;
    private record Fixture(CdR11bAssets assets, CdTimeWarpPrograms programs, CdMmdImage present) { }

    @Test void twoFiniteRawViewsAppendAfterAll123WithoutNewArtReadersOrPlcSources() throws Exception {
        var f = retail(); var a = f.assets();
        int[] offsets = {0x1D9B4, 0xD78E}; int[] lengths = {126, 112};
        String[] labels = {"MapSpr_GoalSignpost", "ObjGoalPost_SetBaseTile.TileIDs"};
        String[] hashes = {"610a02baa99d911f509b5fc06a1e4c0c72bc7ef71fa56ecd72640e409734bad8",
                "82fd77609648d39ac2f50bc4c0e3762fccf3ef0986bf6bc8cef7f4cdeb3b3724"};
        assertEquals(125, CdR11bAssets.Asset.values().length);
        assertEquals(KEYS, Arrays.asList(CdR11bAssets.Asset.values()).subList(123, 125));
        for (int i = 0; i < KEYS.size(); i++) {
            var key = KEYS.get(i); var view = a.view(key);
            assertEquals(123 + i, key.ordinal()); assertEquals(labels[i], key.nativeLabel());
            assertEquals(offsets[i], key.fileOffset()); assertEquals(lengths[i], key.length());
            assertSame(key, view.asset()); assertEquals(key.range(), view.range());
            assertEquals(CdNativeFile.R11B, view.range().file());
            assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, view.range().domain());
            assertEquals(0x200000 + offsets[i], view.range().nativeAddress());
            assertEquals(hashes[i], sha(view.bytes().copy()));
            assertArrayEquals(f.programs().r11b().readFile(offsets[i], lengths[i]), view.bytes().copy());
            assertFalse(key.isPlcSource()); assertTrue(key.readerProfile().isEmpty());
            for (var kind : CdR11bAssets.ReaderKind.values())
                assertThrows(IllegalArgumentException.class, () -> a.readerView(key, kind));
            assertThrows(IllegalArgumentException.class, () -> new CdR11bLoadPlan.PlcEntry(key, key.range(), 0x6B40));
            assertThrows(IndexOutOfBoundsException.class, () -> view.bytes().unsignedByte(key.length()));
        }
        assertEquals(36, Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
        assertEquals(37, Arrays.stream(CdR11bAssets.Asset.values()).filter(k -> k.readerProfile().isPresent()).count());
    }

    @Test void everyOldOrdinalAndDescriptorIncludingStagedProjectorSourcesRemainsExact() throws Exception {
        var metadata = new StringBuilder();
        for (var key : Arrays.asList(CdR11bAssets.Asset.values()).subList(0, 123)) {
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
        assertEquals("07cdda61600e1b3e7d327e2cbe97232352d22894202377f000011a0410a8a109",
                sha(metadata.toString().getBytes(StandardCharsets.UTF_8)));
        assertEquals(120, ANI_METAL_SONIC_HOLOGRAM.ordinal()); assertEquals(122, PROJECTOR_EXPLOSION_SCHEDULE.ordinal());
    }

    @Test void actualBGoalPostIdInitMappingAndPcIndexedTileOperandSelectTheIndependentViews() throws Exception {
        var f = retail(); var image = f.programs().r11b();
        assertEquals(0x20D67AL, f.assets().view(OBJECT_INDEX).bytes().unsignedLong((0x16 - 1) * 4));
        assertArrayEquals(hex("0020d67a"), image.readFile(0x3C8C, 4));
        assertArrayEquals(hex("4df8d000700010280024303b001c4ebb0018"), image.readFile(0xD67A, 18));
        assertArrayEquals(hex("0006006a00b0"), image.readFile(0xD6A2, 6));
        assertArrayEquals(hex("217c0021d9b40004"), image.readFile(0xD6E8, 8));
        assertArrayEquals(hex("117c0005001a6100004a"), image.readFile(0xD702, 10));
        assertEquals(0x20D754, 0x20D70A + (short) 0x004A);
        assertArrayEquals(hex("317b00160002"), image.readFile(0xD776, 6));
        assertEquals(GOAL_POST_BASE_TILES.range().nativeAddress(), 0x20D778 + (byte) 0x16);
        assertArrayEquals(hex("0c39000300ff15066706006880000002"), image.readFile(0xD77C, 16));
        assertArrayEquals(hex("270002e496002000"), image.readFile(0x86AE, 8));
    }

    @Test void completeSharedStaticMapRetainsEveryFrameAndThreeRealAlignmentBytes() throws Exception {
        assertArrayEquals(MAPPINGS, retail().assets().view(MAP_SPR_GOAL_SIGNPOST).bytes().copy());
        var visual = CdObjectVisualAssets.decodeStatic(MAPPINGS, 6, 76);
        int[] roots = {12, 34, 50, 66, 88, 104}; int[] counts = {4, 3, 3, 4, 3, 4};
        for (int i = 0; i < roots.length; i++) {
            var frame = visual.mappingFrames().get(i);
            assertEquals(roots[i], frame.relativeOffset()); assertEquals(counts[i], frame.pieces().size());
            assertEquals(1 + 5 * counts[i], frame.bytes().length());
        }
        for (int at : new int[]{33, 87, 125}) assertEquals(0, MAPPINGS[at]);
        assertEquals(0, visual.animationTable().length()); assertTrue(visual.animations().isEmpty());
        var goal = visual.mappingFrames().get(5);
        assertEquals(104, goal.relativeOffset());
        assertArrayEquals(hex("04e4020000ece40f0003f404010013fc14080015f4"), goal.bytes().copy());
        assertEquals(List.of(0, 3, 0x13, 0x15), goal.pieces().stream().map(CdObjectVisualAssets.Piece::tileOffset).toList());
        for (var piece : goal.pieces()) assertTrue(piece.tileOffset() + piece.tileCount() <= 24);
        assertEquals(24, goal.pieces().stream().mapToInt(p -> p.tileOffset() + p.tileCount()).max().orElseThrow());
        // The common signpost frames require76; this does not enlarge GoalPost's selected24-tile art source.
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(MAPPINGS, 6, 75));
        assertEquals(0x21DA32, MAP_SPR_GOAL_SIGNPOST.range().endExclusive());
    }

    @Test void completeInlineFiftySixWordTileTableRetainsAllCrossZoneValues() throws Exception {
        var words = retail().assets().view(GOAL_POST_BASE_TILES).bytes(); assertArrayEquals(TILES, words.copy());
        assertEquals(112, words.length()); assertEquals(0x035A, words.unsignedWord(0));
        assertEquals(0x0381, words.unsignedWord(8));
        assertEquals(0x02BA, words.unsignedWord(48)); assertEquals(0x02B1, words.unsignedWord(62));
        assertEquals(0x0246, words.unsignedWord(110));
        assertThrows(IndexOutOfBoundsException.class, () -> words.unsignedWord(112));
        assertEquals(0x20D7FE, GOAL_POST_BASE_TILES.range().endExclusive());
        assertArrayEquals(hex("700010280024"), retail().programs().r11b().readFile(0xD7FE, 6));
        assertArrayEquals(hex("7000303900ff1506ef08e848123900ff152e0c010002"), retail().programs().r11b().readFile(0xD754, 22));
        assertArrayEquals(hex("6606d23900ff156ad201d001"), retail().programs().r11b().readFile(0xD76A, 12));
    }

    @Test void reachedNativeLoadPlcHex12UsesTheExistingSignpostAndBigRingSources() throws Exception {
        var f = retail(); var plan = f.assets().loadPlan(); var image = f.programs().r11b();
        assertArrayEquals(hex("70124ef9002024e0"), image.readFile(0xD748, 8));
        assertArrayEquals(hex("000200232e5687800023288c91000020db867de0"), image.readFile(0xF376, 20));
        var cue = plan.plcs().get(0x12);
        assertEquals(0x12, cue.nativeId()); assertEquals(0xF376, cue.bodyRange().fileOffset());
        assertEquals(List.of(ART_SIGNPOST, ART_BIG_RING, ART_BIG_RING_FLASH), cue.entries().stream()
                .map(CdR11bLoadPlan.PlcEntry::source).toList());
        assertEquals(List.of(0x8780, 0x9100, 0x7DE0), cue.entries().stream()
                .map(CdR11bLoadPlan.PlcEntry::vramAddress).toList());
        assertFalse(cue.entries().stream().anyMatch(e -> e.source() == ART_GOAL_POST));
        for (int plc : new int[]{2, 6, 7, 8, 17}) assertTrue(plan.plcs().get(plc).entries().stream()
                .anyMatch(e -> e.source() == ART_GOAL_POST && e.vramAddress() == 0x6B40));
        var region = CdR11bCameraArtResidency.Region.AT_6B40;
        for (var e : cue.entries()) assertTrue(e.vramAddress() >= region.destinationWord()
                && e.vramAddress() + e.source().readerProfile().orElseThrow().outputBytes()
                <= region.destinationWord() + region.byteLength());
    }

    @Test void detachedViewsKeepExistingOwnersAndGoalPostArtProfileUntouched() throws Exception {
        var f = retail(); var a = f.assets(); var terrain = a.terrain(); var plan = a.loadPlan();
        var placements = a.placements(); var sonic = a.sonic(); var player = a.player();
        for (var key : KEYS) {
            var bytes = a.view(key).bytes(); var copy = bytes.copy(); int first = bytes.unsignedByte(0); copy[0] ^= 0x55;
            assertEquals(first, a.view(key).bytes().unsignedByte(0)); assertNotEquals(first, copy[0] & 255);
            assertThrows(ReadOnlyBufferException.class, () -> bytes.buffer().put(0, (byte) 1));
            assertNotEquals(sha(bytes.copy()), sha(f.present().readFile(key.fileOffset(), key.length())));
        }
        assertSame(terrain, a.terrain()); assertSame(plan, a.loadPlan()); assertSame(placements, a.placements());
        assertSame(sonic, a.sonic()); assertSame(player, a.player());
        assertEquals(0x32C96, ART_GOAL_POST.fileOffset()); assertEquals(448, ART_GOAL_POST.length());
        var p = ART_GOAL_POST.readerProfile().orElseThrow();
        assertEquals(448, p.javaInputRange().length()); assertEquals(449, p.nativeInputRange().length());
        assertEquals(448, p.javaConsumedInputBytes()); assertEquals(24 * 32, p.outputBytes());
        assertEquals("24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f", sha(a.view(ART_GOAL_POST).bytes().copy()));
    }

    private Fixture retail() throws Exception {
        if (retained != null) return retained;
        var root = System.getProperty("soniccd.media.root"); assertNotNull(root); assertFalse(root.isBlank()); Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            var programs = CdTimeWarpPrograms.fromPrograms(source); var assets = CdR11bAssets.fromPrograms(programs);
            assertEquals("b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265", assets.identity().mmdSha256());
            retained = new Fixture(assets, programs, source.mmd(CdNativeFile.R11A));
        }
        return retained;
    }
    private static byte[] hex(String value) { return HexFormat.of().parseHex(value); }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
