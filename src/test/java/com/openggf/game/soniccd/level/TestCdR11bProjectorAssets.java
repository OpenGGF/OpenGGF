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
 * Original-USA B source views only. Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Palmtree Panic/Objects/Projector/Main.asm:36-63,73-153,168-219,230-323 and complete Data includes.
 * No body, native execution, PLC service, current upload, residency or World runs in this class.
 * These source joins do not establish a naturally reached controller frontier.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11bProjectorAssets {
    private static final List<CdR11bAssets.Asset> KEYS = List.of(ANI_METAL_SONIC_HOLOGRAM,
            MAP_SPR_PROJECTOR, PROJECTOR_EXPLOSION_SCHEDULE);
    private static final byte[] ANIMATIONS = hex(
            "0004000a0002010301ff00040401040401040401040401040401010404010104040101"
                    + "0404010105050105050105050105050105050101050501010505010105050101ff");
    private static final byte[] MAPPINGS = hex(
            "000c001200130019001f002f01f40a0028f40001fc040031f801fc041031f803e80a08"
                    + "00fcf8050809ec0005080dfc04e80f0811f4e8000821ec00010822ec080c0824f4");
    private static final byte[] SCHEDULE = hex("01000005eef60af60a0f00ee14f612160817190df61cfde71e0a1420f602230df628f60aff00");
    private Fixture retained;
    private record Fixture(CdR11bAssets assets, CdTimeWarpPrograms programs, CdMmdImage present) { }

    @Test void threeRawViewsAppendAfterAll120WithExactCompleteOriginalBSpans() throws Exception {
        var f = retail(); var a = f.assets();
        int[] offsets = {0xED40, 0xED84, 0xEDC8}; int[] lengths = {68, 68, 38};
        String[] labels = {"Ani_MetalSonicHologram", "MapSpr_Projector", "ObjProjector_ExplosionLocs"};
        String[] hashes = {"2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f",
                "2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3",
                "5830294609ca462a39688a5a11982198b43d6b04058c4335d0b9d923064a74a3"};
        assertEquals(133, CdR11bAssets.Asset.values().length);
        assertEquals(KEYS, Arrays.asList(CdR11bAssets.Asset.values()).subList(120, 123));
        for (int i = 0; i < KEYS.size(); i++) {
            var key = KEYS.get(i); var view = a.view(key);
            assertEquals(120 + i, key.ordinal()); assertEquals(labels[i], key.nativeLabel());
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
            assertThrows(IllegalArgumentException.class, () -> new CdR11bLoadPlan.PlcEntry(key, key.range(), 0x8060));
            assertThrows(IndexOutOfBoundsException.class, () -> view.bytes().unsignedByte(key.length()));
        }
        assertEquals(36, Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
        assertEquals(37, Arrays.stream(CdR11bAssets.Asset.values()).filter(k -> k.readerProfile().isPresent()).count());
    }

    @Test void everyOldOrdinalMetadataAndReaderFieldRemainsExact() throws Exception {
        var metadata = new StringBuilder();
        for (var key : Arrays.asList(CdR11bAssets.Asset.values()).subList(0, 120)) {
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
        assertEquals("0fafb5f716838cc6ea0275535efb6d2658c75b557dd7aed9d2f2bb98b65bfc43",
                sha(metadata.toString().getBytes(StandardCharsets.UTF_8)));
        assertEquals(114, ANI_ROBOT_GENERATOR.ordinal()); assertEquals(119, MAP_SPR_EXPLOSION.ordinal());
    }

    @Test void actualBId2eDispatchBothMappingStoresAndPcAnimationOperandSelectTheseViews() throws Exception {
        var f = retail(); var image = f.programs().r11b();
        assertEquals(0x20EAAAL, f.assets().view(OBJECT_INDEX).bytes().unsignedLong((0x2E - 1) * 4));
        assertArrayEquals(hex("0020eaaa"), image.readFile(0x3CEC, 4));
        assertArrayEquals(hex("4a28002866000216"), image.readFile(0xEAAA, 8));
        assertEquals(0x20ECC6, 0x20EAB0 + (short) 0x0216);
        assertArrayEquals(hex("001001300148017201d0"), image.readFile(0xEAE4, 10));
        int[] routines = {0x20EAF4, 0x20EC14, 0x20EC2C, 0x20EC56, 0x20ECB4};
        var directory = CdAssetBytes.copyOf(image.readFile(0xEAE4, 10));
        for (int i = 0; i < routines.length; i++) assertEquals(routines[i], 0x20EAE4 + (short) directory.unsignedWord(2 * i));
        for (int at : new int[]{0xEB42, 0xED06})
            assertArrayEquals(hex("217c0020ed840004"), image.readFile(at, 8));
        assertArrayEquals(hex("217c0020edc8002c"), image.readFile(0xEB4A, 8));
        assertArrayEquals(hex("43fa000e"), image.readFile(0xED30, 4));
        assertEquals(ANI_METAL_SONIC_HOLOGRAM.range().nativeAddress(), 0x20ED32 + (short) 0x000E);
    }

    @Test void directBaseTilesAndSignedChildSubtypeCutsStayInNativeCodeRatherThanNewTables() throws Exception {
        var image = retail().programs().r11b();
        for (int at : new int[]{0xEB2E, 0xECF2})
            assertArrayEquals(hex("303c04034a3900ff15076704303c03af31400002"), image.readFile(at, 20));
        assertArrayEquals(hex("137c00ff00283348003e"), image.readFile(0xEB78, 10));
        assertArrayEquals(hex("137c000100283348003e"), image.readFile(0xEBA8, 10));
        assertArrayEquals(hex("3268003e0c29002e0000"), image.readFile(0xECC6, 10));
        assertArrayEquals(hex("4a29003f6600fe144a280024"), image.readFile(0xECD4, 12));
        assertArrayEquals(hex("7008720474004a2800286b06701472187401"), image.readFile(0xED0E, 18));
        assertArrayEquals(hex("1140001711400019114100161142001c"), image.readFile(0xED20, 16));
    }

    @Test void actualLoadPlcFivePrecedesInitWritesAndFourFollowsBothNativeRestoreCuts() throws Exception {
        var image = retail().programs().r11b();
        assertArrayEquals(hex("4a3900ff156d66f2"), image.readFile(0xEAF4, 8));
        assertArrayEquals(hex("303c00054eb9002024e054280024"), image.readFile(0xEAFC, 14));
        assertArrayEquals(hex("303c00044ef9002024e0"), image.readFile(0xEAD8, 10));
        assertArrayEquals(hex("54280024117c0001001a50e8003f303c00044eb9002024e0"), image.readFile(0xEC2C, 24));
        assertArrayEquals(hex("000000238d7c9ee000000023f45e9ee0"), image.readFile(0xF35E, 16));
        var plan = retail().assets().loadPlan();
        assertEquals(List.of(ART_ANIMALS), plan.plcs().get(4).entries().stream().map(CdR11bLoadPlan.PlcEntry::source).toList());
        assertEquals(List.of(ART_PROJ_ANIMALS), plan.plcs().get(5).entries().stream().map(CdR11bLoadPlan.PlcEntry::source).toList());
        for (int plc : new int[]{4, 5}) assertEquals(0x9EE0, plan.plcs().get(plc).entries().getFirst().vramAddress());
        // These calls change animal-art lineage; Projector base art already belongs to the camera lists.
        for (int plc : new int[]{6, 10}) assertTrue(plan.plcs().get(plc).entries().stream()
                .anyMatch(e -> e.source() == ART_PROJECTOR && e.vramAddress() == 0x8060));
    }

    @Test void completeRawMappingsKeepEmptyFrameOddBodiesAndExactFiftyOneTileBound() throws Exception {
        var a = retail().assets(); assertArrayEquals(MAPPINGS, a.view(MAP_SPR_PROJECTOR).bytes().copy());
        var visual = CdObjectVisualAssets.decode(MAPPINGS, 6, ANIMATIONS, 2, 51);
        int[] roots = {12, 18, 19, 25, 31, 47}; int[] counts = {1, 0, 1, 1, 3, 4};
        for (int i = 0; i < roots.length; i++) {
            var frame = visual.mappingFrames().get(i);
            assertEquals(roots[i], frame.relativeOffset()); assertEquals(counts[i], frame.pieces().size());
            assertEquals(1 + 5 * counts[i], frame.bytes().length());
            assertEquals(i < 5 ? roots[i + 1] : 68, roots[i] + frame.bytes().length());
        }
        var ordinary = visual.mappingFrames().get(0).pieces().getFirst();
        assertEquals(-12, ordinary.x()); assertEquals(-12, ordinary.y()); assertEquals(0x28, ordinary.rawTileWord());
        assertEquals(0x31, visual.mappingFrames().get(2).pieces().getFirst().rawTileWord());
        assertEquals(0x1031, visual.mappingFrames().get(3).pieces().getFirst().rawTileWord());
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(MAPPINGS, 6, ANIMATIONS, 2, 50));
        assertEquals(0x20EDC8, MAP_SPR_PROJECTOR.range().endExclusive());
    }

    @Test void bothCompleteZeroDelayFfScriptsRetainAllFourAndFiftySixFrameBytes() throws Exception {
        assertArrayEquals(ANIMATIONS, retail().assets().view(ANI_METAL_SONIC_HOLOGRAM).bytes().copy());
        var visual = CdObjectVisualAssets.decode(MAPPINGS, 6, ANIMATIONS, 2, 51);
        assertEquals(List.of(2, 1, 3, 1), visual.animations().get(0).frameBytes());
        assertEquals(List.of(4, 4, 1, 4, 4, 1, 4, 4, 1, 4, 4, 1, 4, 4, 1,
                1, 4, 4, 1, 1, 4, 4, 1, 1, 4, 4, 1, 1, 5, 5, 1,
                5, 5, 1, 5, 5, 1, 5, 5, 1, 5, 5, 1, 1, 5, 5, 1,
                1, 5, 5, 1, 1, 5, 5, 1, 1), visual.animations().get(1).frameBytes());
        assertEquals(4, visual.animations().get(0).relativeOffset()); assertEquals(10, visual.animations().get(1).relativeOffset());
        for (var script : visual.animations()) {
            assertEquals(0, script.delay()); assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF, script.terminal());
            assertEquals(255, script.bytes().unsignedByte(script.bytes().length() - 1));
        }
        assertEquals(0x20ED84, ANI_METAL_SONIC_HOLOGRAM.range().endExclusive());
    }

    @Test void completeScheduleIncludesTwelveRecordsNegativeSentinelAndExplicitUnusedZero() throws Exception {
        var bytes = retail().assets().view(PROJECTOR_EXPLOSION_SCHEDULE).bytes(); assertArrayEquals(SCHEDULE, bytes.copy());
        int[] ticks = {1, 5, 10, 15, 20, 22, 25, 28, 30, 32, 35, 40};
        for (int i = 0; i < ticks.length; i++) assertEquals(ticks[i], bytes.unsignedByte(3 * i));
        assertEquals(-18, bytes.signedByte(4)); assertEquals(-10, bytes.signedByte(5));
        assertEquals(-1, bytes.signedByte(36)); assertEquals(0, bytes.unsignedByte(37));
        assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedByte(38));
        assertEquals(0x20EDEE, PROJECTOR_EXPLOSION_SCHEDULE.range().endExclusive());
        assertArrayEquals(hex("4a3900ff156a"), retail().programs().r11b().readFile(0xEDEE, 6));
    }

    @Test void detachedViewsCannotMutateExistingSourcesOwnersOrArtReaderContracts() throws Exception {
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
        assertEquals(0x3F610, ART_PROJECTOR.fileOffset()); assertEquals(1024, ART_PROJECTOR.length());
        var profile = ART_PROJECTOR.readerProfile().orElseThrow();
        assertEquals(1024, profile.javaInputRange().length()); assertEquals(1024, profile.nativeInputRange().length());
        assertEquals(1024, profile.javaConsumedInputBytes()); assertEquals(51 * 32, profile.outputBytes());
        assertEquals("1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5", sha(a.view(ART_PROJECTOR).bytes().copy()));
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
