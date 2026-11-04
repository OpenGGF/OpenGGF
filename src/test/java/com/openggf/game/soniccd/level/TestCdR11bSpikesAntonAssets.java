package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
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

/** Original-USA B data only: immutable N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * _Objects/Spikes/Main.asm:20-25,87-89; Palmtree Panic/Objects/Anton/Main.asm:27-34,85,98-103
 * and complete Data/Animations.asm + Data/Mappings.asm. No body, art service, current upload or World runs.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11bSpikesAntonAssets {
    private static final List<CdR11bAssets.Asset> KEYS = List.of(MAP_SPR_SPIKES, ANI_ANTON, MAP_SPR_ANTON);
    private static final String[] HASHES = {
        "db6b4414c086240e34231326d9832e5785550bdd914ef9d933cea9079bc78607",
        "623c70ccd1429002e1d4e85929b53064bf0a1d7e0e56537d8d86c0bc94a7785d",
        "898f3a56606b9c82c0c0a4c2197b0860353cbce320baf1026bd7f717b300e808"};
    private Fixture retained;
    private record Fixture(CdR11bAssets assets, CdTimeWarpPrograms programs, CdMmdImage present) { }

    @Test void threeRawViewsAppendAfterAllOldHundredSevenWithoutReadersOrPlcs() throws Exception {
        var f = retail(); int[] offsets = {0xC048, 0xB1F6, 0xB202}, lengths = {24, 12, 112};
        String[] labels = {"MapSpr_Spikes", "Ani_Anton", "MapSpr_Anton"};
        assertEquals(133, CdR11bAssets.Asset.values().length);
        assertEquals(KEYS, Arrays.asList(CdR11bAssets.Asset.values()).subList(107, 110));
        assertEquals(100, MAP_SPR_COLLAPSE_PLATFORM_1.ordinal()); assertEquals(106, MAP_SPR_BOULDER.ordinal());
        for (int i = 0; i < KEYS.size(); i++) {
            var key = KEYS.get(i); var view = f.assets().view(key);
            assertEquals(107 + i, key.ordinal()); assertEquals(labels[i], key.nativeLabel());
            assertEquals(offsets[i], key.fileOffset()); assertEquals(lengths[i], key.length());
            assertEquals(HASHES[i], sha(view.bytes().copy())); assertEquals(key.range(), view.range());
            assertEquals(0x200000 + offsets[i], view.range().nativeAddress());
            assertEquals(CdNativeFile.R11B, view.range().file());
            assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, view.range().domain());
            assertArrayEquals(f.programs().r11b().readFile(offsets[i], lengths[i]), view.bytes().copy());
            assertFalse(key.isPlcSource()); assertTrue(key.readerProfile().isEmpty());
            for (var kind : CdR11bAssets.ReaderKind.values())
                assertThrows(IllegalArgumentException.class, () -> f.assets().readerView(key, kind));
        }
        assertEquals(36, Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
        assertEquals(37, Arrays.stream(CdR11bAssets.Asset.values()).filter(k -> k.readerProfile().isPresent()).count());
    }

    @Test void actualBDispatchMappingTileAndAnimationInstructionsOwnTheNamedSources() throws Exception {
        var image = retail().programs().r11b();
        assertArrayEquals(hex("0020bf72"), image.readFile(0x3CCC, 4));
        assertArrayEquals(hex("0020b10c"), image.readFile(0x3D30, 4));
        assertArrayEquals(hex("217c0020c0480004"), image.readFile(0xBF88, 8));
        assertArrayEquals(hex("700a4eba0fd2"), image.readFile(0xBF9C, 6));
        assertEquals(0x20CF72, 0x20BFA0 + (short) 0x0FD2);
        assertArrayEquals(hex("217c0020b2020004"), image.readFile(0xB142, 8));
        assertArrayEquals(hex("70024eb90020cf72"), image.readFile(0xB168, 8));
        assertArrayEquals(hex("43fa001a"), image.readFile(0xB1DA, 4));
        assertEquals(ANI_ANTON.range().nativeAddress(), 0x20B1DC + (short) 0x001A);
    }

    @Test void completeLiteralMappingsAndAntonScriptsDecodeTheirActualFiniteFormats() throws Exception {
        var a = retail().assets();
        assertArrayEquals(hex("000204f0050000f000050004f0f005000000000500040000"), a.view(MAP_SPR_SPIKES).bytes().copy());
        assertArrayEquals(hex("00040008030001ff070203ff"), a.view(ANI_ANTON).bytes().copy());
        var spikes = CdObjectVisualAssets.decodeStatic(a.view(MAP_SPR_SPIKES).bytes().copy(), 1, 8);
        assertEquals(4, spikes.mappingFrames().getFirst().pieces().size());
        var anton = CdObjectVisualAssets.decode(a.view(MAP_SPR_ANTON).bytes().copy(), 4,
                a.view(ANI_ANTON).bytes().copy(), 2, 22);
        int[] offsets = {8, 34, 60, 86};
        for (int i = 0; i < 4; i++) {
            assertEquals(offsets[i], anton.mappingFrames().get(i).relativeOffset());
            assertEquals(5, anton.mappingFrames().get(i).pieces().size());
        }
        assertEquals(List.of(0, 1), anton.animations().get(0).frameBytes());
        assertEquals(List.of(2, 3), anton.animations().get(1).frameBytes());
        assertEquals(3, anton.animations().get(0).delay()); assertEquals(7, anton.animations().get(1).delay());
        assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF, anton.animations().get(0).terminal());
        assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF, anton.animations().get(1).terminal());
    }

    @Test void actualB72DirectoryRetainsRawWordsRatherThanPresentEntriesOrPadding() throws Exception {
        var tiles = retail().assets().view(OBJECT_TILE_IDS).bytes();
        assertEquals(72, tiles.length()); assertEquals(0x28, tiles.unsignedWord(4));
        assertEquals(0x23ED, tiles.unsignedWord(0x28)); assertEquals(0x2000, tiles.unsignedWord(0x2A));
        assertEquals(0x30, tiles.unsignedWord(20)); assertEquals(0x0320, tiles.unsignedWord(0x30));
        assertEquals(0x0381, tiles.unsignedWord(0x32));
        assertEquals(0xCFBC, OBJECT_TILE_IDS.fileOffset() + 0x28);
        assertEquals(0xCFC4, OBJECT_TILE_IDS.fileOffset() + 0x30);
        assertThrows(IndexOutOfBoundsException.class, () -> tiles.unsignedWord(72));
    }

    @Test void wholeSpansEndAtGenuineFollowingNativeOwners() throws Exception {
        var f = retail();
        assertEquals(0x20C060, MAP_SPR_SPIKES.range().endExclusive());
        assertEquals(MAP_SPR_ANTON.range().nativeAddress(), ANI_ANTON.range().endExclusive());
        assertEquals(0x20B272, MAP_SPR_ANTON.range().endExclusive());
        assertEquals(0, f.assets().view(MAP_SPR_SPIKES).bytes().unsignedByte(23));
        assertArrayEquals(hex("700010280024"), f.programs().r11b().readFile(0xC060, 6));
        assertArrayEquals(hex("4eb90020edee"), f.programs().r11b().readFile(0xB272, 6));
    }

    @Test void detachedViewsNeverBorrowPresentRangesOrMutateExistingOwners() throws Exception {
        var f = retail(); var a = f.assets(); var terrain = a.terrain(); var plan = a.loadPlan();
        var placements = a.placements(); var sonic = a.sonic(); var player = a.player();
        for (var key : KEYS) {
            var bytes = a.view(key).bytes(); var copy = bytes.copy(); int first = bytes.unsignedByte(0); copy[0] ^= 0x55;
            assertEquals(first, a.view(key).bytes().unsignedByte(0)); assertNotEquals(first, copy[0] & 0xFF);
            assertThrows(ReadOnlyBufferException.class, () -> bytes.buffer().put(0, (byte) 1));
            assertNotEquals(sha(bytes.copy()), sha(f.present().readFile(key.fileOffset(), key.length())));
        }
        assertSame(terrain, a.terrain()); assertSame(plan, a.loadPlan()); assertSame(placements, a.placements());
        assertSame(sonic, a.sonic()); assertSame(player, a.player());
    }

    private Fixture retail() throws Exception {
        if (retained != null) return retained;
        var root = System.getProperty("soniccd.media.root"); assertNotNull(root); assertFalse(root.isBlank());
        Path cue;
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
