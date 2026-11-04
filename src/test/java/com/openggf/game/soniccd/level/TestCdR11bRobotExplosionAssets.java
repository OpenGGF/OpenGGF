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
 * Actual original-USA B source views only. N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * _Objects/Robot Generator/Main.asm:54-67,120-121,200-229 and its complete Data includes;
 * Palmtree Panic/Objects/Robot Generator/Tile IDs.asm:7-10; _Objects/Explosion/Main.asm:73,89-92
 * and its complete Data includes. No object, PLC service, current residency or World is executed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11bRobotExplosionAssets {
    private static final List<CdR11bAssets.Asset> KEYS = List.of(ANI_ROBOT_GENERATOR, MAP_SPR_ROBOT_GENERATOR,
            ROBOT_GENERATOR_BASE_TILES, ROBOT_GENERATOR_EXPLOSION_SCHEDULE, ANI_EXPLOSION, MAP_SPR_EXPLOSION);
    private static final String[] HASHES = {
            "cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e",
            "69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59",
            "3948236aa2a83a827ef6a7fb0f0e863b4a1ffc2b743355f954aed558420992b4",
            "1827c7470bb98b70240de9237aeb30aec8841ae3cc1f2e7f51332b3b18155123",
            "9119f6ae5b68628549bb895b3bb7dcc3a6d4d9cc81918efc848846f745359649",
            "0b509dfbd51d21babeb6f551b2032e8e82325c5a518f8c5e976b88b039624d44"};
    private Fixture retained;
    private record Fixture(CdR11bAssets assets, CdTimeWarpPrograms programs, CdMmdImage present) { }

    @Test void sixFiniteRawViewsAppendAfterAll114WithoutNewReadersOrPlcSources() throws Exception {
        var f = retail(); var a = f.assets();
        int[] offsets = {0xE80E, 0xE816, 0xEA94, 0xEA54, 0x1D700, 0x1D714};
        int[] lengths = {8, 574, 22, 64, 20, 146};
        String[] labels = {"Ani_RobotGenerator", "MapSpr_RobotGenerator", "ObjRobotGenerator_BaseTileList",
                "ObjRobotGenerator_ExplosionLocs", "Ani_Explosion", "MapSpr_Explosion"};
        assertEquals(133, CdR11bAssets.Asset.values().length);
        assertEquals(KEYS, Arrays.asList(CdR11bAssets.Asset.values()).subList(114, 120));
        for (int i = 0; i < KEYS.size(); i++) {
            var key = KEYS.get(i); var view = a.view(key);
            assertEquals(114 + i, key.ordinal()); assertEquals(labels[i], key.nativeLabel());
            assertEquals(offsets[i], key.fileOffset()); assertEquals(lengths[i], key.length());
            assertSame(key, view.asset()); assertEquals(key.range(), view.range());
            assertEquals(CdNativeFile.R11B, view.range().file());
            assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, view.range().domain());
            assertEquals(0x200000 + offsets[i], view.range().nativeAddress());
            assertEquals(HASHES[i], sha(view.bytes().copy()));
            assertArrayEquals(f.programs().r11b().readFile(offsets[i], lengths[i]), view.bytes().copy());
            assertFalse(key.isPlcSource()); assertTrue(key.readerProfile().isEmpty());
            for (var kind : CdR11bAssets.ReaderKind.values())
                assertThrows(IllegalArgumentException.class, () -> a.readerView(key, kind));
            assertThrows(IllegalArgumentException.class, () -> new CdR11bLoadPlan.PlcEntry(key, key.range(), 0x87E0));
            assertThrows(IndexOutOfBoundsException.class, () -> view.bytes().unsignedByte(key.length()));
        }
        assertEquals(36, Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
        assertEquals(37, Arrays.stream(CdR11bAssets.Asset.values()).filter(k -> k.readerProfile().isPresent()).count());
    }

    @Test void everyOldOrdinalAndMetadataFieldIncludingAllReaderContractsRemainsExact() throws Exception {
        var metadata = new StringBuilder();
        for (var key : Arrays.asList(CdR11bAssets.Asset.values()).subList(0, 114)) {
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
        assertEquals("d0a34fd5b47b18cb304d7e35301db6b910d1464a57d2b3c178f243d1c4fb11c2",
                sha(metadata.toString().getBytes(StandardCharsets.UTF_8)));
        assertEquals(110, ANI_MOSQUI_NORMAL.ordinal()); assertEquals(113, MAP_SPR_MOSQUI_DAMAGED.ordinal());
    }

    @Test void actualBDirectoriesAndNativeInstructionsSelectTheSixIndependentSources() throws Exception {
        var f = retail(); var image = f.programs().r11b(); var index = f.assets().view(OBJECT_INDEX).bytes();
        assertEquals(0x20E642L, index.unsignedLong((0x2D - 1) * 4));
        assertEquals(0x205E6AL, index.unsignedLong((0x18 - 1) * 4));
        assertArrayEquals(hex("0020e642"), image.readFile(0x3CE8, 4));
        assertArrayEquals(hex("00205e6a"), image.readFile(0x3C94, 4));
        assertArrayEquals(hex("0008009e01080166"), image.readFile(0xE666, 8));
        assertArrayEquals(hex("43fa0402"), image.readFile(0xE690, 4));
        assertEquals(ROBOT_GENERATOR_BASE_TILES.range().nativeAddress(), 0x20E692 + (short) 0x0402);
        assertArrayEquals(hex("217c0020e8160004"), image.readFile(0xE6AC, 8));
        assertArrayEquals(hex("217c0020ea54002c"), image.readFile(0xE6B4, 8));
        assertArrayEquals(hex("43fa00aa"), image.readFile(0xE762, 4));
        assertEquals(ANI_ROBOT_GENERATOR.range().nativeAddress(), 0x20E764 + (short) 0x00AA);
        assertArrayEquals(hex("217c0021d7140004"), image.readFile(0x5EA0, 8));
        assertArrayEquals(hex("43f90021d700"), image.readFile(0x5ECE, 6));
        assertArrayEquals(hex("317c86800002"), image.readFile(0x5E8E, 6));
    }

    @Test void completeRobotAnimationMappingsBaseListAndScheduleRetainTheirNativeFormats() throws Exception {
        var a = retail().assets();
        assertArrayEquals(hex("00020303040506ff"), a.view(ANI_ROBOT_GENERATOR).bytes().copy());
        assertArrayEquals(hex("043f0409000000000454040000000000000000000000"),
                a.view(ROBOT_GENERATOR_BASE_TILES).bytes().copy());
        var visual = CdObjectVisualAssets.decode(a.view(MAP_SPR_ROBOT_GENERATOR).bytes().copy(), 8,
                a.view(ANI_ROBOT_GENERATOR).bytes().copy(), 1, 23);
        int[] offsets = {17, 38, 59, 120, 211, 332, 453, 16}; int[] counts = {4, 4, 12, 18, 24, 24, 24, 0};
        for (int i = 0; i < 8; i++) {
            assertEquals(offsets[i], visual.mappingFrames().get(i).relativeOffset());
            assertEquals(counts[i], visual.mappingFrames().get(i).pieces().size());
        }
        assertEquals(List.of(3, 4, 5, 6), visual.animations().getFirst().frameBytes());
        assertEquals(3, visual.animations().getFirst().delay());
        assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF, visual.animations().getFirst().terminal());
        var schedule = a.view(ROBOT_GENERATOR_EXPLOSION_SCHEDULE).bytes();
        assertArrayEquals(hex("01000002d8ec031c0a0412ee05eef60608f808ee0e0af60a0c1ef60f00ee1214f614f612160817190df61a17ea1cfde71e0a1420f602221ef8230df628f60aff"), schedule.copy());
        int[] ticks = {1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 18, 20, 22, 25, 26, 28, 30, 32, 34, 35, 40};
        for (int i = 0; i < ticks.length; i++) assertEquals(ticks[i], schedule.unsignedByte(3 * i));
        assertEquals(-40, schedule.signedByte(4)); assertEquals(-20, schedule.signedByte(5));
        assertEquals(255, schedule.unsignedByte(63));
        assertEquals(MAP_SPR_ROBOT_GENERATOR.range().nativeAddress(), ANI_ROBOT_GENERATOR.range().endExclusive());
        assertEquals(ROBOT_GENERATOR_EXPLOSION_SCHEDULE.range().nativeAddress(), MAP_SPR_ROBOT_GENERATOR.range().endExclusive());
        assertEquals(ROBOT_GENERATOR_BASE_TILES.range().nativeAddress(), ROBOT_GENERATOR_EXPLOSION_SCHEDULE.range().endExclusive());
        assertArrayEquals(hex("4a280028"), retail().programs().r11b().readFile(0xEAAA, 4));
    }

    @Test void completeExplosionTablesRetainBothFcScriptsAndEveryCountedMapping() throws Exception {
        var a = retail().assets();
        assertArrayEquals(hex("0004000c030005060304fc00030001020304fc00"), a.view(ANI_EXPLOSION).bytes().copy());
        var visual = CdObjectVisualAssets.decode(a.view(MAP_SPR_EXPLOSION).bytes().copy(), 7,
                a.view(ANI_EXPLOSION).bytes().copy(), 2, 70);
        int[] offsets = {14, 26, 42, 64, 86, 108, 124}; int[] counts = {2, 3, 4, 4, 4, 3, 4};
        for (int i = 0; i < 7; i++) {
            assertEquals(offsets[i], visual.mappingFrames().get(i).relativeOffset());
            assertEquals(counts[i], visual.mappingFrames().get(i).pieces().size());
        }
        assertEquals(List.of(0, 5, 6, 3, 4), visual.animations().get(0).frameBytes());
        assertEquals(List.of(0, 1, 2, 3, 4), visual.animations().get(1).frameBytes());
        for (var script : visual.animations()) {
            assertEquals(3, script.delay()); assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC, script.terminal());
        }
        assertEquals(MAP_SPR_EXPLOSION.range().nativeAddress(), ANI_EXPLOSION.range().endExclusive());
        assertEquals(0, a.view(MAP_SPR_EXPLOSION).bytes().unsignedByte(145));
    }

    @Test void detachedViewsKeepExistingOwnersAndExistingRobotAndExplosionPlcBytes() throws Exception {
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
        assertEquals(0x3F2AC, ART_ROBOT_GENERATOR.fileOffset()); assertEquals(434, ART_ROBOT_GENERATOR.length());
        var robot = ART_ROBOT_GENERATOR.readerProfile().orElseThrow();
        assertEquals(434, robot.javaInputRange().length()); assertEquals(434, robot.nativeInputRange().length());
        assertEquals(434, robot.javaConsumedInputBytes()); assertEquals(23 * 32, robot.outputBytes());
        for (int plc : new int[]{8, 12}) assertTrue(plan.plcs().get(plc).entries().stream()
                .anyMatch(e -> e.source() == ART_ROBOT_GENERATOR && e.vramAddress() == 0x87E0));
        assertEquals(0x35CFA, ART_EXPLOSIONS.fileOffset()); assertEquals(1256, ART_EXPLOSIONS.length());
        assertEquals(70 * 32, ART_EXPLOSIONS.readerProfile().orElseThrow().outputBytes());
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
