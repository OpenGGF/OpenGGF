package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdRobotGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.openggf.game.soniccd.level.CdR12aProfile.Asset.*;
import static org.junit.jupiter.api.Assertions.*;

/** Authored source-only; all Java/media/runtime proofs UNRUN.
 * Native1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Robot Generator/Main.asm:49-86,218-254,
 * Data/Mappings.asm, Data/Animations.asm and PP Objects/Robot Generator/Tile IDs.asm.
 * Full metadata bound23 is distinct from selected Present frame2's existing withered15 tiles.
 * No current-pattern, queue, invocation, route, decoder-execution or pixel certification. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR12aRobotGeneratorAssets {
    private static final HexFormat HEX = HexFormat.of();
    private static final CdR12aProfile.Asset[] KEYS = {ANI_ROBOT_GENERATOR, MAP_SPR_ROBOT_GENERATOR,
            ROBOT_GENERATOR_EXPLOSION_SCHEDULE, ROBOT_GENERATOR_BASE_TILES};
    private static final int[] ROOTS = {17, 38, 59, 120, 211, 332, 453, 16};
    private static final int[] COUNTS = {4, 4, 12, 18, 24, 24, 24, 0};
    private static final String[] LITERALS = {
            "00020303040506ff",
            "00110026003b007800d3014c01c50010000410090009e8100908090010090009d810090809100410090011e8100908110010090011d810090811100ce00a0000e8e00a080000080a1000e8080a180000e00a0000d8e00a0800f0080a1000d8080a1800f0e00a0000f8e00a080010080a1000f8080a18001012e00a0000e8e00a080000f801000dfaf801180ffe080a1000e8080a180000e00a0000d8e00a0800f0f801000deaf801180fee080a1000d8080a1800f0e00a0000f8e00a080010f801000d0af801180f0e080a1000f8080a18001018e0040009f8e00a0000e8e00a080000f801000ffaf801180dfe18041009f8080a1000e8080a180000e0040009e8e00a0000d8e00a0800f0f801000feaf801180dee18041009e8080a1000d8080a1800f0e004000908e00a0000f8e00a080010f801000f0af801180d0e1804100908080a1000f8080a18001018e004000bf8e00a0000e8e00a080000f801180ffaf801000dfe1804100bf8080a1000e8080a180000e004000be8e00a0000d8e00a0800f0f801180feaf801000dee1804100be8080a1000d8080a1800f0e004000b08e00a0000f8e00a080010f801180f0af801000d0e1804100b08080a1000f8080a18001018e0040009f8e00a0000e8e00a080000f801180dfaf801000ffe18041009f8080a1000e8080a180000e0040009e8e00a0000d8e00a0800f0f801180deaf801000fee18041009e8080a1000d8080a1800f0e004000908e00a0000f8e00a080010f801180d0af801000f0e1804100908080a1000f8080a180010",
            "01000002d8ec031c0a0412ee05eef60608f808ee0e0af60a0c1ef60f00ee1214f614f612160817190df61a17ea1cfde71e0a1420f602221ef8230df628f60aff",
            "043f0409000000000454040000000000000000000000"
    };
    private static final String[] HASHES = {
            "cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e",
            "69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59",
            "1827c7470bb98b70240de9237aeb30aec8841ae3cc1f2e7f51332b3b18155123",
            "3948236aa2a83a827ef6a7fb0f0e863b4a1ffc2b743355f954aed558420992b4"
    };

    @Test void appendedDescriptorsPreserveSplashOrdinalsAndExactOriginalWordRamProvenance() throws Exception {
        var o = retail().terrain().objects(); sameSource(o);
        assertEquals(146, CdR12aProfile.Asset.values().length);
        assertEquals(136, ANI_TUNNEL_DOOR_SPLASH.ordinal()); assertEquals(137, MAP_SPR_TUNNEL_DOOR_SPLASH.ordinal());
        int[] offsets = {0xE4EE, 0xE4F6, 0xE734, 0xE774}, lengths = {8, 574, 64, 22};
        for (int i = 0; i < KEYS.length; i++) {
            var a = KEYS[i]; var r = o.profile().range(a);
            assertEquals(138 + i, a.ordinal()); assertEquals(offsets[i], a.fileOffset()); assertEquals(lengths[i], a.length());
            assertEquals(CdNativeFile.R12A, r.file()); assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, r.domain());
            assertEquals(0x200000 + offsets[i], r.nativeAddress()); assertEquals(offsets[i] + lengths[i], r.endExclusive());
            sameView(o, i);
        }
    }

    @Test void actualDispatchAndInitOperandsSelectTheseTablesBeforeAnyPeriodGate() throws Exception {
        var f = retail().original();
        assertEquals("0020e322", HEX.formatHex(f.readFile(0x3D78, 4)));
        assertEquals("3936b3d14edc7b1085509e351aefdb0377f509b5215a91ae3cff3e793191271d",
                CdR11aProfile.sha256(f.readFile(0xE322, 36)));
        assertEquals("cd5efc7b404f4f06689bdf0b0dd9871d5942d2a0dce250ad09d5efdd07172233",
                CdR11aProfile.sha256(f.readFile(0xE34E, 150)));
        assertEquals("0020e4f6", HEX.formatHex(f.readFile(0xE38E, 4)));
        assertEquals("0020e734", HEX.formatHex(f.readFile(0xE396, 4)));
        var extension = CdAssetBytes.copyOf(f.readFile(0xE372, 2));
        assertEquals(0xE774, 0xE372 + (short) extension.unsignedWord(0));
    }

    @Test void allFourCompleteLiteralsAndHashesSurviveCatalogueCueCloseWithoutHostReopen() throws Exception {
        var o = retail().terrain().objects();
        for (int i = 0; i < KEYS.length; i++) sameView(o, i);
        assertSame(o, retail().terrain().objects()); assertSame(o.robotGenerator(), o.robotGenerator());
    }

    @Test void allEightRootsKeepEverySignedPieceAndExactUnpaddedBodyExtents() throws Exception {
        var o = retail().terrain().objects(); var v = o.robotGenerator(); var literal = HEX.parseHex(LITERALS[1]);
        assertEquals(0, v.mappingTableOffset()); assertEquals(8, v.mappingFrames().size());
        assertArrayEquals(literal, v.mappings().copy());
        for (int i = 0; i < ROOTS.length; i++) {
            var frame = v.mappingFrames().get(i); int root = ROOTS[i];
            assertEquals(root, frame.relativeOffset()); assertEquals(COUNTS[i], frame.pieces().size());
            assertEquals(root, o.view(MAP_SPR_ROBOT_GENERATOR).unsignedWord(i * 2));
            assertArrayEquals(Arrays.copyOfRange(literal, root, root + 1 + COUNTS[i] * 5), frame.bytes().copy());
            for (int j = 0; j < COUNTS[i]; j++) {
                int at = root + 1 + 5 * j; var p = frame.pieces().get(j);
                assertEquals((byte) literal[at], p.y()); assertEquals(literal[at + 1] & 255, p.sizeByte());
                assertEquals(((literal[at + 2] & 255) << 8) | (literal[at + 3] & 255), p.rawTileWord());
                assertEquals((byte) literal[at + 4], p.x()); assertTrue(p.tileOffset() + p.tileCount() <= 23);
            }
        }
        assertEquals(1, v.mappingFrames().get(7).bytes().length()); assertEquals(0, literal[16]);
        assertEquals(0x10, literal[573]); assertEquals(121, v.mappingFrames().get(6).bytes().length());
        assertEquals(574, ROOTS[6] + v.mappingFrames().get(6).bytes().length());
    }

    @Test void selectedPresentFrameTwoFitsExistingFifteenTilesWhileFullMetadataRequiresTwentyThree() throws Exception {
        var o = retail().terrain().objects(); var v = o.robotGenerator();
        int maximum = 0;
        for (var frame : v.mappingFrames()) for (var p : frame.pieces()) maximum = Math.max(maximum, p.tileOffset() + p.tileCount());
        assertEquals(23, maximum);
        for (var p : v.mappingFrames().get(2).pieces()) assertTrue(p.tileOffset() + p.tileCount() <= 15);
        assertEquals(9, v.mappingFrames().get(2).pieces().stream().mapToInt(p -> p.tileOffset() + p.tileCount()).max().orElseThrow());
        assertEquals(480, o.readerProfile(ART_ROBOT_GEN_WITHERED).outputBytes());
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(
                HEX.parseHex(LITERALS[1]), 8, HEX.parseHex(LITERALS[0]), 1, 15));
        var a = new CdRobotGenerator.Assets(o.view(ROBOT_GENERATOR_BASE_TILES), v,
                MAP_SPR_ROBOT_GENERATOR.nativeAddress(), o.view(ROBOT_GENERATOR_EXPLOSION_SCHEDULE),
                ROBOT_GENERATOR_EXPLOSION_SCHEDULE.nativeAddress());
        assertSame(v, a.visual()); assertSame(o.view(ROBOT_GENERATOR_BASE_TILES), a.baseTiles());
        assertSame(o.view(ROBOT_GENERATOR_EXPLOSION_SCHEDULE), a.explosionSchedule());
        assertEquals(0x20E4F6, a.mappingsAddress()); assertEquals(0x20E734, a.explosionScheduleAddress());
    }

    @Test void oneScriptRetainsDelayAllFourFramesAndFfTerminalWithoutAnimationExecution() throws Exception {
        var v = retail().terrain().objects().robotGenerator(); assertEquals(1, v.animations().size());
        assertArrayEquals(HEX.parseHex(LITERALS[0]), v.animationTable().copy());
        var s = v.animations().getFirst(); assertEquals(2, s.relativeOffset()); assertEquals(3, s.delay());
        assertEquals(List.of(3, 4, 5, 6), s.frameBytes()); assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF, s.terminal());
        assertEquals("0303040506ff", HEX.formatHex(s.bytes().copy()));
    }

    @Test void twentyOneTripletsAndTerminatorRetainAllSignedOffsetsAndExactFiniteSchedule() throws Exception {
        var raw = retail().terrain().objects().view(ROBOT_GENERATOR_EXPLOSION_SCHEDULE);
        int[] ticks = {1,2,3,4,5,6,8,10,12,15,18,20,22,25,26,28,30,32,34,35,40};
        int[] x = {0,-40,28,18,-18,8,-18,-10,30,0,20,-10,8,13,23,-3,10,-10,30,13,-10};
        int[] y = {0,-20,10,-18,-10,-8,14,10,-10,-18,-10,18,23,-10,-22,-25,20,2,-8,-10,10};
        for (int i = 0; i < 21; i++) {
            assertEquals(ticks[i], raw.unsignedByte(i * 3)); assertEquals(x[i], raw.signedByte(i * 3 + 1));
            assertEquals(y[i], raw.signedByte(i * 3 + 2));
        }
        assertEquals(64, raw.length()); assertEquals(255, raw.unsignedByte(63));
        assertThrows(IndexOutOfBoundsException.class, () -> raw.unsignedByte(64));
    }

    @Test void allElevenBaseWordsRetainActOneRawOneSelectionAndZeroUnusedEntries() throws Exception {
        var raw = retail().terrain().objects().view(ROBOT_GENERATOR_BASE_TILES);
        int[] words = {0x43F, 0x409, 0, 0, 0x454, 0x400, 0, 0, 0, 0, 0};
        for (int i = 0; i < words.length; i++) assertEquals(words[i], raw.unsignedWord(i * 2));
        assertEquals(0x400, raw.unsignedWord(((1 << 2) + 1) * 2));
        assertEquals(22, raw.length()); assertThrows(IndexOutOfBoundsException.class, () -> raw.unsignedWord(21));
    }

    @Test void lazyVisualTouchesOnlyItsTwoRawViewsAndLeavesAllArtReaderAndPlcCachesUncreated() throws Exception {
        var r = retail(); var o = new CdR12aObjectAssets(r.terrain(), r.original());
        assertNull(field(o, "robotGenerator")); assertEquals(Map.of(), field(o, "views"));
        var v = o.robotGenerator(); assertSame(v, o.robotGenerator()); assertSame(v, field(o, "robotGenerator"));
        assertEquals(Set.of(ANI_ROBOT_GENERATOR, MAP_SPR_ROBOT_GENERATOR), ((Map<?, ?>) field(o, "views")).keySet());
        for (String name : List.of("decoded", "javaInputs", "nativeInputs")) assertEquals(Map.of(), field(o, name));
        assertNull(field(o, "plcs"));
        assertThrows(UnsupportedOperationException.class, () -> v.mappingFrames().clear());
        assertThrows(UnsupportedOperationException.class, () -> v.mappingFrames().get(2).pieces().clear());
        assertThrows(UnsupportedOperationException.class, () -> v.animations().clear());
        assertThrows(UnsupportedOperationException.class, () -> v.animations().getFirst().frameBytes().clear());
        byte[] changed = v.mappings().copy(); changed[17] = 0; assertArrayEquals(HEX.parseHex(LITERALS[1]), v.mappings().copy());
        changed = v.animationTable().copy(); changed[2] = 0; assertArrayEquals(HEX.parseHex(LITERALS[0]), v.animationTable().copy());
    }

    @Test void existingWitheredSourceAndAllReaderPlcValuesRemainIdenticalAcrossMetadataAccess() throws Exception {
        var o = retail().terrain().objects(); var readers = o.readerProfiles(); var lists = o.plcLists();
        var readerBefore = readers.toString(); var plcBefore = lists.toString(); var art = o.view(ART_ROBOT_GEN_WITHERED);
        o.robotGenerator(); for (var key : KEYS) o.view(key);
        assertSame(readers, o.readerProfiles()); assertSame(lists, o.plcLists()); assertSame(art, o.view(ART_ROBOT_GEN_WITHERED));
        assertEquals(readerBefore, o.readerProfiles().toString()); assertEquals(plcBefore, o.plcLists().toString());
        assertEquals(35, readers.size()); assertEquals(19, lists.size());
        var p = o.readerProfile(ART_ROBOT_GEN_WITHERED); assertEquals(0x39028, p.sourceBlobRange().fileOffset());
        assertEquals(332, p.sourceBlobRange().length()); assertEquals(p.sourceBlobRange(), p.javaInputRange());
        assertEquals(p.sourceBlobRange(), p.nativeInputRange()); assertEquals(332, p.javaConsumedInputBytes());
        assertEquals("26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda", CdR11aProfile.sha256(art.copy()));
        int[] ids = {5, 6, 10, 11}, rows = {5, 6, 1, 2}, operands = {0xF1DA, 0xF218, 0xF2A4, 0xF2B8};
        for (int i = 0; i < ids.length; i++) {
            var e = lists.get(ids[i]).entries().get(rows[i]); assertEquals(ART_ROBOT_GEN_WITHERED, e.source());
            assertEquals(p.sourceBlobRange(), e.sourceRange()); assertEquals(0x8000, e.destinationWord());
            assertEquals("002390288000", HEX.formatHex(retail().original().readFile(operands[i], 6)));
        }
        for (var key : KEYS) {
            assertThrows(IllegalArgumentException.class, () -> o.readerProfile(key));
            assertThrows(IllegalArgumentException.class, () -> o.readerView(key, CdR12aObjectAssets.ReaderKind.JAVA));
            assertThrows(IllegalArgumentException.class, () -> o.readerView(key, CdR12aObjectAssets.ReaderKind.NATIVE));
            assertTrue(lists.stream().flatMap(l -> l.entries().stream()).noneMatch(e -> e.source() == key));
        }
    }

    @Test void changedOriginalTablesAndMalformedDirectoryPiecesEmptyRootAndScriptsCannotBecomeFallbacks() throws Exception {
        var r = retail(); var o = r.terrain().objects();
        for (int i = 0; i < KEYS.length; i++) {
            var raw = o.view(KEYS[i]); byte[] copy = raw.copy(); copy[0] ^= 1; sameView(o, i);
            assertThrows(IndexOutOfBoundsException.class, () -> raw.unsignedByte(raw.length()));
            byte[] corrupt = r.original().readFile(0, 262144); corrupt[KEYS[i].fileOffset()] ^= 1;
            assertThrows(IOException.class, () -> o.profile().verifyProgram(CdNativeFile.R12A,
                    CdMmdImage.Domain.ORIGINAL_WORD_RAM, CdMmdImage.parse(corrupt)));
        }
        byte[] map = HEX.parseHex(LITERALS[1]), ani = HEX.parseHex(LITERALS[0]);
        for (int kind = 0; kind < 5; kind++) {
            byte[] bad = map.clone();
            switch (kind) {
                case 0 -> { bad[0] = (byte) 0x80; bad[1] = 0; }
                case 1 -> bad[17] = 127;
                case 2 -> bad[19] = 16;
                case 3 -> { bad[20] = 7; bad[21] = (byte) 255; }
                case 4 -> bad[16] = 1;
            }
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(bad, 8, ani, 1, 23));
        }
        for (int kind = 0; kind < 4; kind++) {
            byte[] bad = ani.clone();
            switch (kind) {
                case 0 -> bad[1] = 1;
                case 1 -> bad[3] = 8;
                case 2 -> bad[7] = (byte) 0xFE;
                case 3 -> bad[7] = 0;
            }
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(map, 8, bad, 1, 23));
        }
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(Arrays.copyOf(map, 572), 8, ani, 1, 23));
        byte[] inventedPadding = Arrays.copyOf(map, map.length + 1); inventedPadding[map.length] = 1;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(inventedPadding, 8, ani, 1, 23));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(Arrays.copyOf(map, map.length + 2), 8, ani, 1, 23));
    }

    private void sameSource(CdR12aObjectAssets o) throws Exception {
        var t = retail().terrain(); assertSame(t.identity(), o.identity()); assertSame(t.profile(), o.profile());
        assertEquals(CdReleaseVariant.USA, o.identity().variant()); assertEquals(CdStagePayload.R12A.stage(), o.identity().stage());
        assertEquals("6091e19b34358cf241e9b51f222b6ea2202230ff0b684b44f9ed050cf840b5e7", o.identity().mmdSha256());
    }
    private void sameView(CdR12aObjectAssets o, int i) throws Exception {
        var raw = o.view(KEYS[i]); assertSame(raw, o.view(KEYS[i]));
        assertArrayEquals(HEX.parseHex(LITERALS[i]), raw.copy()); assertEquals(HASHES[i], CdR11aProfile.sha256(raw.copy()));
        assertArrayEquals(retail().original().readFile(KEYS[i].fileOffset(), KEYS[i].length()), raw.copy());
    }
    private static Object field(CdR12aObjectAssets o, String name) throws Exception {
        var f = CdR12aObjectAssets.class.getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
    private record Retail(CdR12aTerrainAssets terrain, CdMmdImage original) { }
    private Retail retained;
    private Retail retail() throws Exception {
        if (retained != null) return retained;
        String root = System.getProperty("soniccd.media.root"); assertNotNull(root, "Configure original CD media root");
        assertFalse(root.isBlank()); Path cue;
        try (var entries = Files.list(Path.of(root).resolve("USA"))) {
            var cues = entries.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            retained = new Retail(CdR12aTerrainAssets.fromPrograms(source), source.mmd(CdNativeFile.R12A));
        }
        return retained;
    }
}
