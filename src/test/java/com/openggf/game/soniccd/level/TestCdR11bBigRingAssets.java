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
 * Original-USA B source-only metadata. Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * _Objects/Level End/Main.asm:385-562 and complete Big Ring/Big Ring Flash Data includes.
 * No actor/object dispatch, special-stage entry, native execution, art decode/upload, PLC service or World runs.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11bBigRingAssets {
    private static final List<CdR11bAssets.Asset> KEYS = List.of(ANI_BIG_RING, MAP_SPR_BIG_RING,
            ANI_BIG_RING_FLASH, MAP_SPR_BIG_RING_FLASH);
    private static final byte[] RING_ANIMATIONS = hex("00020700010203ff");
    private static final byte[] FLASH_ANIMATIONS = hex("0002020001020304050607fc");
    private static final byte[] RING_MAPPINGS = hex(
            "0008003c0070007c0ae0090000e8e009000600e800000ce0e800000d18f007000ee010000016e010090017e81009001d"
                    + "001000002318f007002410000ae00d002cf0e8000034e8f0070035e81000003de8100d003ef0e800004610f000004700"
                    + "f007004808080000500010000051100002e00b0052f4000b005ef4000ae00d082cf0e800083410f0070835081000083d"
                    + "10100d083ef0e8000846e8f0000847f8f0070848e808000850f810000851e800");
    private static final byte[] FLASH_MAPPINGS = hex(
            "001000300050008400d6010a012a014a06e008000000e801000300e80a000508000a1005080801100300180810000000"
                    + "06e002000ef0e00f0011f8e8020021180002102118000f1011f80802100ef0000ae0070024e8e00d002cf8f0050034f8"
                    + "f005003408e802000b180002100b18000500340800050034f8100d102cf800071024e80010e0000802e8e000000210e8"
                    + "060038e0e005082ef0e005002e00f0050034f0f005003400e80608381000061038e018001802e800050034f01005182e"
                    + "f000050034001005102e0018001002100006183810000ae802080be0e00d082ce8f0050034e8f0050034f8e007082408"
                    + "0002180be000050034e800050034f8100d182ce800071824080006e8020821e0e00f0811e8e002080e0800021821e000"
                    + "0f1811e80802180e080006e0080800e8e8010803f8e80a0805e0000a1805e008011803f818081800e80011e004003e00"
                    + "e809004000e800004618f8040047100004104710080910400010001046181804103e00e004083ef0e8000846e0e80908"
                    + "40e8f8040847e000041847e010001846e008091840e81804183ef0f8050049f8");
    private Fixture retained;
    private record Fixture(CdR11bAssets assets, CdTimeWarpPrograms programs, CdMmdImage present) { }

    @Test void fourCompleteRawViewsAppendAfter127WithoutNewArtReadersOrPlcSources() throws Exception {
        var f = retail(); var a = f.assets();
        int[] offsets = {0x1D8EE, 0x1D8F6, 0xD9DA, 0xD9E6}; int[] lengths = {8, 176, 12, 416};
        String[] labels = {"Ani_BigRing", "MapSpr_BigRing", "Ani_BigRingFlash", "MapSpr_BigRingFlash"};
        String[] hashes = {"b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6",
                "c6edd39fb1e1cc977c264d87e2301816d01ff2c10660fb63f89d9148afdb30c4",
                "b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f",
                "b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10"};
        assertEquals(133, CdR11bAssets.Asset.values().length);
        assertEquals(KEYS, Arrays.asList(CdR11bAssets.Asset.values()).subList(127, 131));
        for (int i = 0; i < KEYS.size(); i++) {
            var key = KEYS.get(i); var view = a.view(key);
            assertEquals(127 + i, key.ordinal()); assertEquals(labels[i], key.nativeLabel());
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
            assertThrows(IllegalArgumentException.class, () -> new CdR11bLoadPlan.PlcEntry(key, key.range(), 0x9100));
            assertThrows(IndexOutOfBoundsException.class, () -> view.bytes().unsignedByte(key.length()));
        }
        assertEquals(36, Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
        assertEquals(37, Arrays.stream(CdR11bAssets.Asset.values()).filter(k -> k.readerProfile().isPresent()).count());
    }

    @Test void all127PriorDescriptorsIncludingProjectorGoalPostAndSignpostRemainExact() throws Exception {
        var metadata = new StringBuilder();
        for (var key : Arrays.asList(CdR11bAssets.Asset.values()).subList(0, 127)) {
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
        assertEquals("692a7589c27916d019eb0ca6283b9fcd997b0772de0a5e59ab74e0da7f8b1a43",
                sha(metadata.toString().getBytes(StandardCharsets.UTF_8)));
        assertEquals(125, ANI_SIGNPOST.ordinal()); assertEquals(126, LEVEL_END_TIME_BONUSES.ordinal());
    }

    @Test void actualId14DispatchAndSubtypeFirstBranchAuthenticateTheBigRingAndFlashRoles() throws Exception {
        var f = retail(); var image = f.programs().r11b();
        assertEquals(0x20D542L, f.assets().view(OBJECT_INDEX).bytes().unsignedLong((0x14 - 1) * 4));
        assertArrayEquals(hex("0020d542"), image.readFile(0x3C84, 4));
        assertArrayEquals(hex("4a28002866b60c79003200ff151264064ef9002076d6"), image.readFile(0xD542, 22));
        assertEquals(0x20D4FE, 0x20D548 + (byte) 0xB6);
        assertArrayEquals(hex("0006004e00b8"), image.readFile(0xD576, 6));
        assertArrayEquals(hex("0006001e002a"), image.readFile(0xD512, 6));
        assertArrayEquals(hex("0c39007f00ff0f2066064ef9002039a2"), image.readFile(0xD57C, 16));
        assertArrayEquals(hex("4a3900ff0f0167064ef9002039a2"), image.readFile(0xD58C, 14));
        assertArrayEquals(hex("297002989400a000"), image.readFile(0x86DE, 8));
    }

    @Test void actualInitMappingTileAndAnimationOperandsSelectAllFourNativeSources() throws Exception {
        var image = retail().programs().r11b();
        assertArrayEquals(hex("317c24880002"), image.readFile(0xD5A4, 6));
        assertArrayEquals(hex("217c0021d8f60004"), image.readFile(0xD5AA, 8));
        assertArrayEquals(hex("43f90021d8ee"), image.readFile(0xD62E, 6));
        assertArrayEquals(hex("317c03ef0002"), image.readFile(0xD522, 6));
        assertArrayEquals(hex("217c0020d9e60004"), image.readFile(0xD528, 8));
        assertArrayEquals(hex("43f90020d9da"), image.readFile(0xD530, 6));
        assertArrayEquals(hex("4ef9002039a2"), image.readFile(0xD53C, 6));
        assertArrayEquals(hex("4eb9002076a066ae137c00140000"), image.readFile(0xD60E, 14));
        assertEquals(0x20D5C4, 0x20D616 + (byte) 0xAE);
        assertArrayEquals(hex("137c001400003368000800083368000c000c137c00010028"), image.readFile(0xD616, 24));
    }

    @Test void completeRingTablesKeepFourFramesAllPaddingAndTheExact106TileBound() throws Exception {
        var a = retail().assets();
        assertArrayEquals(RING_ANIMATIONS, a.view(ANI_BIG_RING).bytes().copy());
        assertArrayEquals(RING_MAPPINGS, a.view(MAP_SPR_BIG_RING).bytes().copy());
        var visual = CdObjectVisualAssets.decode(RING_MAPPINGS, 4, RING_ANIMATIONS, 1, 106);
        int[] roots = {8, 60, 112, 124}; int[] counts = {10, 10, 2, 10};
        for (int i = 0; i < roots.length; i++) {
            var frame = visual.mappingFrames().get(i);
            assertEquals(roots[i], frame.relativeOffset()); assertEquals(counts[i], frame.pieces().size());
            assertEquals(1 + counts[i] * 5, frame.bytes().length());
        }
        for (int at : new int[]{59, 111, 123, 175}) assertEquals(0, RING_MAPPINGS[at]);
        assertEquals(List.of(0, 1, 2, 3), visual.animations().getFirst().frameBytes());
        assertEquals(7, visual.animations().getFirst().delay());
        assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF, visual.animations().getFirst().terminal());
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(RING_MAPPINGS, 4, RING_ANIMATIONS, 1, 105));
        assertEquals(MAP_SPR_BIG_RING.range().nativeAddress(), ANI_BIG_RING.range().endExclusive());
        assertEquals(ANI_SIGNPOST.range().nativeAddress(), MAP_SPR_BIG_RING.range().endExclusive());
    }

    @Test void completeFlashTablesKeepEightFramesFcDeletionAndTheExact77TileBound() throws Exception {
        var a = retail().assets();
        assertArrayEquals(FLASH_ANIMATIONS, a.view(ANI_BIG_RING_FLASH).bytes().copy());
        assertArrayEquals(FLASH_MAPPINGS, a.view(MAP_SPR_BIG_RING_FLASH).bytes().copy());
        var visual = CdObjectVisualAssets.decode(FLASH_MAPPINGS, 8, FLASH_ANIMATIONS, 1, 77);
        int[] roots = {16, 48, 80, 132, 214, 266, 298, 330}; int[] counts = {6, 6, 10, 16, 10, 6, 6, 17};
        for (int i = 0; i < roots.length; i++) {
            var frame = visual.mappingFrames().get(i);
            assertEquals(roots[i], frame.relativeOffset()); assertEquals(counts[i], frame.pieces().size());
            assertEquals(1 + counts[i] * 5, frame.bytes().length());
        }
        for (int at : new int[]{47, 79, 131, 213, 265, 297, 329}) assertEquals(0, FLASH_MAPPINGS[at]);
        assertEquals(0xF8, FLASH_MAPPINGS[415] & 255); // Final frame's real X byte, not invented alignment.
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), visual.animations().getFirst().frameBytes());
        assertEquals(2, visual.animations().getFirst().delay());
        assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC, visual.animations().getFirst().terminal());
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(FLASH_MAPPINGS, 8, FLASH_ANIMATIONS, 1, 76));
        assertEquals(MAP_SPR_BIG_RING_FLASH.range().nativeAddress(), ANI_BIG_RING_FLASH.range().endExclusive());
        assertEquals(ART_BIG_RING_FLASH.range().nativeAddress(), MAP_SPR_BIG_RING_FLASH.range().endExclusive());
    }

    @Test void existingCue18ReaderProfilesAndCurrentRegionsAlreadyCoverRingAndFlash() throws Exception {
        var a = retail().assets(); var cue = a.loadPlan().plcs().get(0x12);
        assertEquals(List.of(ART_SIGNPOST, ART_BIG_RING, ART_BIG_RING_FLASH), cue.entries().stream()
                .map(CdR11bLoadPlan.PlcEntry::source).toList());
        assertEquals(List.of(0x8780, 0x9100, 0x7DE0), cue.entries().stream().map(CdR11bLoadPlan.PlcEntry::vramAddress).toList());
        assertEquals(0x3288C, ART_BIG_RING.fileOffset()); assertEquals(1034, ART_BIG_RING.length());
        var ring = ART_BIG_RING.readerProfile().orElseThrow();
        assertEquals(1034, ring.javaInputRange().length()); assertEquals(1034, ring.nativeInputRange().length());
        assertEquals(1033, ring.javaConsumedInputBytes()); assertEquals(106 * 32, ring.outputBytes());
        assertEquals(0xDB86, ART_BIG_RING_FLASH.fileOffset()); assertEquals(384, ART_BIG_RING_FLASH.length());
        var flash = ART_BIG_RING_FLASH.readerProfile().orElseThrow();
        assertEquals(384, flash.javaInputRange().length()); assertEquals(385, flash.nativeInputRange().length());
        assertEquals(384, flash.javaConsumedInputBytes()); assertEquals(77 * 32, flash.outputBytes());
        var region = CdR11bCameraArtResidency.Region.AT_6B40;
        for (var e : cue.entries()) assertTrue(e.vramAddress() >= region.destinationWord()
                && e.vramAddress() + e.source().readerProfile().orElseThrow().outputBytes()
                <= region.destinationWord() + region.byteLength());
        assertEquals(0x9100, (0x2488 & 0x7FF) * 32); assertEquals(0x7DE0, 0x03EF * 32);
    }

    @Test void detachedViewsKeepAllPriorSourceOwnerIdentitiesAndCannotMutateBytes() throws Exception {
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
