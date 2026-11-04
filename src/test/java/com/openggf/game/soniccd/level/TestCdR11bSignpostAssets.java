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
 * Actual original-USA B raw views only. N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * _Objects/Level End/Main.asm:708-891 and complete Data/Animations (Signpost).asm.
 * No body, native execution, results creation, sound, PLC, current upload or World runs here.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11bSignpostAssets {
    private static final List<CdR11bAssets.Asset> KEYS = List.of(ANI_SIGNPOST, LEVEL_END_TIME_BONUSES);
    private static final byte[] ANIMATIONS = hex("000201000102040303010204ff00");
    private static final byte[] BONUSES = hex(
            "c350c350271013880fa00fa00bb80bb807d007d007d007d003e803e803e803e801f4"
                    + "01f401f401f40000");
    private Fixture retained;
    private record Fixture(CdR11bAssets assets, CdTimeWarpPrograms programs, CdMmdImage present) { }

    @Test void onlyAnimationAndTimeBonusViewsAppendAfter125WithoutAnotherMappingOrArtRoot() throws Exception {
        var f = retail(); var a = f.assets();
        int[] offsets = {0x1D9A6, 0xD978}; int[] lengths = {14, 42};
        String[] labels = {"Ani_Signpost", "StartResults.TimeBonuses"};
        String[] hashes = {"9912394f852b914651884191ea8532d60421a3bd40293510a3f356f86c69e691",
                "be40c9a172da6d18e694f0b8cfe698fa674eb88ab210958f4fa82713e0cf0861"};
        assertEquals(133, CdR11bAssets.Asset.values().length);
        assertEquals(KEYS, Arrays.asList(CdR11bAssets.Asset.values()).subList(125, 127));
        for (int i = 0; i < KEYS.size(); i++) {
            var key = KEYS.get(i); var view = a.view(key);
            assertEquals(125 + i, key.ordinal()); assertEquals(labels[i], key.nativeLabel());
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
            assertThrows(IllegalArgumentException.class, () -> new CdR11bLoadPlan.PlcEntry(key, key.range(), 0x8780));
            assertThrows(IndexOutOfBoundsException.class, () -> view.bytes().unsignedByte(key.length()));
        }
        assertEquals(123, MAP_SPR_GOAL_SIGNPOST.ordinal()); assertEquals(124, GOAL_POST_BASE_TILES.ordinal());
        assertEquals(1, Arrays.stream(CdR11bAssets.Asset.values()).filter(k -> k.nativeLabel().equals("MapSpr_GoalSignpost")).count());
        assertEquals(36, Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
        assertEquals(37, Arrays.stream(CdR11bAssets.Asset.values()).filter(k -> k.readerProfile().isPresent()).count());
    }

    @Test void all125OldDescriptorsReaderContractsAndProjectorGoalPostOrdinalsRemainExact() throws Exception {
        var metadata = new StringBuilder();
        for (var key : Arrays.asList(CdR11bAssets.Asset.values()).subList(0, 125)) {
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
        assertEquals("4c84779581b2c15e3a86b77c7bcbabe10fa770b9696cb7d8ad5581f13518767f",
                sha(metadata.toString().getBytes(StandardCharsets.UTF_8)));
        assertEquals(120, ANI_METAL_SONIC_HOLOGRAM.ordinal()); assertEquals(122, PROJECTOR_EXPLOSION_SCHEDULE.ordinal());
    }

    @Test void actualBId17SpinAndResultsOperandsSelectTheCompleteNamedSources() throws Exception {
        var f = retail(); var image = f.programs().r11b();
        assertEquals(0x20D7FEL, f.assets().view(OBJECT_INDEX).bytes().unsignedLong((0x17 - 1) * 4));
        assertArrayEquals(hex("0020d7fe"), image.readFile(0x3C90, 4));
        assertArrayEquals(hex("000a004a00a600ca0190"), image.readFile(0xD812, 10));
        int[] routines = {0x20D81C, 0x20D85C, 0x20D8B8, 0x20D8DC, 0x20D9A2};
        var directory = CdAssetBytes.copyOf(image.readFile(0xD812, 10));
        for (int i = 0; i < routines.length; i++) assertEquals(routines[i], 0x20D812 + (short) directory.unsignedWord(2 * i));
        assertArrayEquals(hex("43f90021d9a6"), image.readFile(0xD8B8, 6));
        assertArrayEquals(hex("31fb0014f7d2"), image.readFile(0xD962, 6));
        assertEquals(LEVEL_END_TIME_BONUSES.range().nativeAddress(), 0x20D964 + (byte) 0x14);
        assertArrayEquals(hex("293802f49700a000"), image.readFile(0x86CE, 8));
    }

    @Test void signpostReusesTheExistingSixFrameMappingAndDirectBaseTileImmediate() throws Exception {
        var image = retail().programs().r11b();
        assertArrayEquals(hex("317c043c00020c39000300ff15066706002800800002"), image.readFile(0xD83E, 22));
        assertArrayEquals(hex("217c0021d9b40004"), image.readFile(0xD854, 8));
        assertEquals(0x21D9B4, MAP_SPR_GOAL_SIGNPOST.range().nativeAddress());
        assertEquals(126, MAP_SPR_GOAL_SIGNPOST.length());
        var visual = CdObjectVisualAssets.decode(retail().assets().view(MAP_SPR_GOAL_SIGNPOST).bytes().copy(),
                6, retail().assets().view(ANI_SIGNPOST).bytes().copy(), 1, 76);
        assertEquals(List.of(12, 34, 50, 66, 88, 104), visual.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(4, 3, 3, 4, 3, 4), visual.mappingFrames().stream().map(f -> f.pieces().size()).toList());
        assertEquals(1, visual.animations().size());
    }

    @Test void completeNineFrameSpinScriptRetainsItsFfTerminalAndRealAlignmentZero() throws Exception {
        var a = retail().assets(); assertArrayEquals(ANIMATIONS, a.view(ANI_SIGNPOST).bytes().copy());
        var visual = CdObjectVisualAssets.decode(a.view(MAP_SPR_GOAL_SIGNPOST).bytes().copy(), 6, ANIMATIONS, 1, 76);
        var spin = visual.animations().getFirst();
        assertEquals(2, spin.relativeOffset()); assertEquals(1, spin.delay());
        assertEquals(List.of(0, 1, 2, 4, 3, 3, 1, 2, 4), spin.frameBytes());
        assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF, spin.terminal());
        assertEquals(255, ANIMATIONS[12] & 255); assertEquals(0, ANIMATIONS[13]);
        assertEquals(MAP_SPR_GOAL_SIGNPOST.range().nativeAddress(), ANI_SIGNPOST.range().endExclusive());
    }

    @Test void completeTwentyOneWordTimeBonusTableEndsAtTheRealResultsActiveReturn() throws Exception {
        var bytes = retail().assets().view(LEVEL_END_TIME_BONUSES).bytes(); assertArrayEquals(BONUSES, bytes.copy());
        int[] words = {50000, 50000, 10000, 5000, 4000, 4000, 3000, 3000, 2000, 2000, 2000, 2000,
                1000, 1000, 1000, 1000, 500, 500, 500, 500, 0};
        for (int i = 0; i < words.length; i++) assertEquals(words[i], bytes.unsignedWord(2 * i));
        assertEquals(42, bytes.length()); assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedWord(42));
        assertEquals(0x20D9A2, LEVEL_END_TIME_BONUSES.range().endExclusive());
        assertArrayEquals(hex("4e75"), retail().programs().r11b().readFile(0xD9A2, 2));
        assertArrayEquals(hex("80fc000f7214b04165023001d040"), retail().programs().r11b().readFile(0xD954, 14));
    }

    @Test void nativeSignpostUsesExistingFmAndSubCpuCutsWithoutAnotherPlcOrArtTable() throws Exception {
        var image = retail().programs().r11b();
        assertArrayEquals(hex("303c009d4ef900202316"), image.readFile(0xD8AC, 10));
        assertArrayEquals(hex("303c00824eb900205abe303c006b4eb900205abe"), image.readFile(0xD8EC, 20));
        assertArrayEquals(hex("137c003a0000137c00100032"), image.readFile(0xD92C, 12));
        assertEquals(List.of(ART_SIGNPOST, ART_BIG_RING, ART_BIG_RING_FLASH), retail().assets().loadPlan().plcs()
                .get(0x12).entries().stream().map(CdR11bLoadPlan.PlcEntry::source).toList());
        assertEquals(0x32E56, ART_SIGNPOST.fileOffset()); assertEquals(1500, ART_SIGNPOST.length());
        var profile = ART_SIGNPOST.readerProfile().orElseThrow();
        assertEquals(1500, profile.javaInputRange().length()); assertEquals(1501, profile.nativeInputRange().length());
        assertEquals(1500, profile.javaConsumedInputBytes()); assertEquals(76 * 32, profile.outputBytes());
    }

    @Test void detachedViewsKeepAllExistingSourceOwnerIdentitiesAndAreReadOnly() throws Exception {
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
