package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdNemesisAsset;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.openggf.game.soniccd.level.CdR11bAssets.Asset.*;
import static com.openggf.game.soniccd.resources.CdR11bSectionArt.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Original-USA component checks, not a new visit/Frame/GPU/native-timing proof.
 * Literal source-output hashes are the independently pinned profile research:
 * r11b-assets-profile-census-20261028.md SHA1553b1aa2794ad726c28e05c3b605f9530a694913789e695f9a9864480df00f9.
 * Independent Java-format products plus those literals supply expected physical
 * byte images; the new adapter is never replayed to manufacture expected pixels.
 * Native labels: LoadPLCImm (Functions (General).asm:794-816), NemDecMain:401-579,
 * LoadLevelData (Palmtree Panic/Load Level Data.asm:11-22), LoadLifeIcon (Main.asm:599-614),
 * PLC_Std/PLC_Cam4_Full (Data (Act 1 Past).asm:54-83,105-131), N1ecc5f3a.
 */
class TestCdR11bSectionArt {
    private static CdR11bAssets assets;
    private static CdR11bSectionArt owner;
    private static final String HASHES = """
LEVEL_BLOCKS 4228 5280 4227 bbbfc1389c418e33af33c83782981eb862a8b64842d31aa9262ee6f27b128d58
ART_LEVEL_TILES 10223 23872 10222 256dd866d08c90833dd3c7c8ac8e7e2cc970e43526785fb331a3ceb4a555a42e
ART_ANIMALS 446 640 445 ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1
ART_ANTON 485 704 484 2e0a7e3f6719c8afa2c569d5078824dd7c522a04eeee75fa773830ceea95b101
ART_BIG_RING 1034 3392 1033 31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c
ART_BIG_RING_FLASH 385 2464 384 39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6
ART_BOULDER 271 608 270 abdbe20715a2c4784fae851622bb276dc1630abc339efb1e13b99a5ddc4d0c7d
ART_CHECKPOINT 212 384 211 b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3
ART_DIAGONAL_SPRING 780 1472 779 1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b
ART_EXPLOSIONS 1257 2240 1256 434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664
ART_FLOAT_BLOCK 231 480 231 df65b0523d19f52de3f61737c9d130bd493c1591077767651012ac94047e8362
ART_FLOWER 646 1248 645 db06551b90412becaec9aa45ccfd14dca62c94e1f165718629553f8fdaac6793
ART_GOAL_POST 449 768 448 68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a
ART_HUD 301 864 300 0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc
ART_LOG_SHADOW 24 512 23 fa208fd33608e8a21ed13a7c9a92cdbbd6a936acd1a377f4ac10e9d333113866
ART_MONITOR_TIME_POSTS 1541 2624 1541 7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f
ART_MOSQUI 847 2240 846 230205b2348e61dece2c63201ba1c46285dd2d0bb74d45fb4daa402ca91c62bd
ART_PATA_BATA 715 1600 714 8e7cc0d2af986240146ffd214038f1b2715c71227226261be477cb689eac8943
ART_PLATFORM 453 1824 452 b33a50d46c89a5115a18548e6a3b083428c8a5bd2bcafc3c4c45e2718c5c768a
ART_POINTS 101 160 101 2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db
ART_PROJ_ANIMALS 435 640 434 608ee7b58e8aae521fda3eb07b348c8027cbdac0f5046081df1d0f6930606a3a
ART_PROJECTOR 1024 1632 1024 4f1061c0e3a9256e6fbdfe721e35b0021c91909a52815036c6456498ac2b7d77
ART_RESULTS 1354 3840 1354 fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d
ART_RINGS 244 448 244 c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb
ART_ROBOT_GENERATOR 434 736 434 672db05e55714cc5efa6a3f2d5cc21fa455e8d031aebc9f7d4fc14aa001565d6
ART_SCENERY 307 544 306 4dc8389222e4ffcd585afd720ef722fe1e9694399ea8f05eefb3c6748999cfbd
ART_SIGNPOST 1501 2432 1500 7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c
ART_SONIC_HOLE 114 448 113 8038f15c0f2952ab7ce2c2e9bb2823a14888aab2e5d9d77146ed01ade5845399
ART_SPIKES 87 256 86 75e3391d4045ec96fa925a79bba808a88425444e43d4694f105e7085057c2f72
ART_SPINNING_DISC 167 384 167 97ff27ee8b9ef0f8903cf420bafd651f954e4dd1f3468bd71e829dc932f24e86
ART_SPRINGBOARD 312 672 311 89c1122854699aa7cf8f11633d1512e0bf7acdc84eda2cfd945e9132e657594e
ART_SPRINGS 547 1152 547 8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416
ART_TAMABBOH 1143 1888 1142 aa544f679799481cc55fe2f329ef30be56ee2c8f8a655e5509f02d4035cbed95
ART_TITLE_CARD 940 3488 939 68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a
ART_TITLE_CARD_TEXT 298 1664 298 66b74c71966af3b2bdf42828b3a0b5e5fcf73f05c8cae89fa2ff3f28eb01ee70
ART_TUNNEL_DOOR 154 384 153 dc7be7cdc86c642af5aafd7462269397a6d901678241fde33a35b9209eaf22a0
ART_TUNNEL_WATERFALL 328 928 327 f3cc6438669479cd3bb36fa0f6cb176ed514865e9ec06d19ee0a5b80d993a8c6
            """;
    private static final String STD = """
ART_TUNNEL_DOOR 6500
ART_FLOAT_BLOCK 6680
ART_TITLE_CARD 6c00
ART_TITLE_CARD_TEXT 7a00
ART_CHECKPOINT 9000
ART_DIAGONAL_SPRING 9200
ART_PLATFORM 97c0
ART_ANIMALS 9ee0
ART_SPRINGS a400
ART_HUD ad00
ART_MONITOR_TIME_POSTS b500
ART_EXPLOSIONS d000
ART_POINTS d8c0
ART_FLOWER dae0
ART_RINGS f5c0
            """;
    private static final String CAM4 = """
ART_SPIKES 6400
ART_BOULDER 68c0
ART_GOAL_POST 6b40
ART_SPINNING_DISC 6e40
ART_SPRINGBOARD 7020
ART_TUNNEL_WATERFALL 73c0
ART_SONIC_HOLE 7760
ART_LOG_SHADOW 7960
ART_SCENERY 7b60
ART_ANTON 7da0
ART_PROJECTOR 8060
ART_PATA_BATA 87e0
            """;

    @BeforeAll
    static void loadOriginalPast() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for actual USA R11B");
        Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            assets = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
        owner = fromAssets(assets);
    }

    @Test
    void sourceBindingAndExplicitIngressCreateNoCurrentKnowledgeOrQueueWork() {
        var table = dirtyTable(); var resident = CdR11bCameraArtResidency.unknown();
        var state = owner.fromValues(table, resident, 0xA55A);
        assertSame(table, state.codeTable()); assertSame(resident, state.residency());
        assertEquals(0xA55A, state.sectionWord()); assertArrayEquals(new byte[96], state.queue().bytes().copy());
        assertTrue(state.decoder().isEmpty()); assertEquals(0, state.remainingTilesWord());
        assertEquals(0, state.processBudgetWord()); assertEquals(0, state.hintWord()); assertEquals(0, state.padWord());
        assertFalse(state.stopped());
        assertImage(Map.of(), state.residency());
    }

    @Test
    void everyRealListAliasCopiesItsExactFiniteDescriptorBytes() {
        var before = fresh();
        for (var list : assets.loadPlan().plcs()) {
            var state = owner.initList(before, list.nativeId());
            byte[] expected = new byte[96];
            for (int i = 0; i < list.entries().size(); i++) {
                var e = list.entries().get(i); long n = Integer.toUnsignedLong(e.sourceRange().nativeAddress());
                for (int b = 0; b < 4; b++) expected[i * 6 + b] = (byte) (n >>> (24 - 8 * b));
                expected[i * 6 + 4] = (byte) (e.vramAddress() >>> 8); expected[i * 6 + 5] = (byte) e.vramAddress();
            }
            assertArrayEquals(expected, state.queue().bytes().copy(), "Native slot " + list.nativeId());
            assertSame(before.codeTable(), state.codeTable()); assertSame(before.residency(), state.residency());
            assertEquals(before.sectionWord(), state.sectionWord()); assertTrue(state.decoder().isEmpty());
        }
        assertEquals(19, assets.loadPlan().plcs().size());
        for (int alias : new int[]{0, 3, 13, 14, 15}) {
            assertEquals(owner.initList(before, 3).queue(), owner.initList(before, alias).queue());
        }
        assertEquals(owner.initList(before, 2).queue(), owner.initList(before, 17).queue());
    }

    @Test
    void fourInitialSectionsUseUnsignedStrictCutsAndPreserveTheRawLowByte() {
        int[][] cuts = {{0,0,2},{0x10FF,0,2},{0x1100,2,6},{0x19FF,2,6},
                {0x1A00,4,7},{0x1FFF,4,7},{0x2000,6,8},{0xFFFE,6,8}};
        var before = fresh();
        for (int[] c : cuts) {
            var state = owner.loadSection(before, c[0]);
            assertEquals((c[1] << 8) | 0x5A, state.sectionWord());
            assertEquals(owner.initList(before, c[2]).queue(), state.queue());
            assertSame(before.codeTable(), state.codeTable()); assertSame(before.residency(), state.residency());
        }
        var failure = assertThrows(SectionArtException.class, () -> owner.loadSection(before, 0xFFFF));
        assertEquals(Reason.SECTION_TABLE, failure.reason()); assertEquals(Operation.QUEUED, failure.operation());
        assertTerminalCopy(before, failure.state());
    }

    @Test
    void sectionUpdatesDoNothingWithinRangeAndCancelAtTheRealCrossing() {
        var before = fresh();
        assertSame(before, owner.updateSection(before, 0x1000));
        var active = owner.prepare(owner.initList(before, 3));
        var state = owner.updateSection(active, 0x1100);
        assertEquals(0x025A, state.sectionWord()); assertEquals(owner.initList(before, 10).queue(), state.queue());
        assertEquals(0, state.remainingTilesWord()); assertTrue(state.decoder().isEmpty());
        assertSame(active.codeTable(), state.codeTable()); assertSame(active.residency(), state.residency());
        assertSame(state, owner.updateSection(state, 0x1900));
    }

    @Test
    void initialSectionAppendKeepsAnAlreadyActiveDecoderAndItsNativeCursor() {
        var active = owner.prepare(owner.initList(fresh(), 3));
        var state = owner.loadSection(active, 0x1900);
        assertEquals(0x025A, state.sectionWord()); assertEquals(active.queue().headSource(), state.queue().headSource());
        assertEquals(active.queue().headDestination(), state.queue().headDestination());
        assertSame(active.decoder().orElseThrow(), state.decoder().orElseThrow());
        assertSame(active.codeTable(), state.codeTable()); assertSame(active.residency(), state.residency());
        assertEquals(746, state.remainingTilesWord());
        assertEquals(Integer.toUnsignedLong(ART_SPIKES.range().nativeAddress()), state.queue().bytes().unsignedLong(6));
    }

    @Test
    void nativeDescriptorOverflowRetainsTheWrittenPrefixAndThenStops() {
        var before = owner.initList(fresh(), 1);
        var failure = assertThrows(SectionArtException.class, () -> owner.appendList(before, 1));
        assertEquals(Reason.DESCRIPTOR_BUFFER, failure.reason()); assertTrue(failure.state().stopped());
        byte[] original = before.queue().bytes().copy(), prefix = failure.state().queue().bytes().copy();
        assertArrayEquals(Arrays.copyOf(original, 90), Arrays.copyOf(prefix, 90));
        assertEquals(Integer.toUnsignedLong(ART_TUNNEL_DOOR.range().nativeAddress()), failure.state().queue().bytes().unsignedLong(90));
        assertEquals(0x6500, failure.state().queue().bytes().unsignedWord(94));
        assertArrayEquals(original, before.queue().bytes().copy());
        assertThrows(IllegalStateException.class, () -> owner.clearPlcs(failure.state()));
    }

    @Test
    void explicitPatternClearChangesOnlyTheExistingResidentValue() {
        var before = owner.appendList(fresh(), 4);
        var cleared = owner.clearCurrentPatterns(before);
        assertQueuedFields(before, cleared); assertSame(before.codeTable(), cleared.codeTable());
        for (var r : CdR11bCameraArtResidency.Region.values()) {
            var view = cleared.residency().region(r);
            for (int i = 0; i < r.byteLength(); i++) assertEquals(0, view.knownByte(i).orElseThrow());
            byte[] ones = new byte[r.byteLength()]; Arrays.fill(ones, (byte)1);
            assertArrayEquals(ones, view.knownMask().copy());
        }
        assertImage(Map.of(), before.residency());
        assertThrows(CdR11bCameraArtResidency.OwnershipBoundary.class,
                () -> CdR11bCameraArtResidency.readTile(cleared.residency(), 0xF000 >>> 5));
    }

    @Test
    void immediateCoreWrites746RealTilesWithoutAnyQueuedWork() throws Exception {
        var before = owner.appendList(fresh(), 4);
        var state = owner.loadImmediateList(before, 3);
        assertQueuedFields(before, state); assertEquals(746, knownTiles(state.residency()));
        Map<Integer, byte[]> expected = new HashMap<>(); paint(expected, ART_LEVEL_TILES, 0);
        assertImage(expected, state.residency());
        assertArrayEquals(dirtyTable().slice(0x1E6, 2).copy(), state.codeTable().slice(0x1E6, 2).copy());
        assertArrayEquals(dirtyTable().slice(0x1EA, 22).copy(), state.codeTable().slice(0x1EA, 22).copy());
    }

    @Test
    void immediateStandardExecutesAllFifteenSourcesIncludingBothTitleInputs() throws Exception {
        assertRows(STD, 1);
        var before = fresh(); var state = owner.loadImmediateList(before, 1);
        assertQueuedFields(before, state); assertEquals(596, knownTiles(state.residency()));
        Map<Integer, byte[]> expected = new HashMap<>(); paintRows(expected, STD); assertImage(expected, state.residency());
        assertEquals(0x0736, state.codeTable().unsignedWord(496));
        assertEquals(0x0736, state.codeTable().unsignedWord(498));
        assertEquals(0x0826, state.codeTable().unsignedWord(500));
        assertEquals(0x083C, state.codeTable().unsignedWord(502));
    }

    @Test
    void coreThenStandardWritesTheLiteral1342TileUnionAndKeepsTitlePixels() throws Exception {
        var state = coreAndStd();
        Map<Integer, byte[]> expected = new HashMap<>(); paint(expected, ART_LEVEL_TILES, 0); paintRows(expected, STD);
        assertEquals(1342, expected.size()); assertEquals(1342, knownTiles(state.residency())); assertImage(expected, state.residency());
        assertSourceSlice(state.residency(), ART_TITLE_CARD, 0x6C00, 0x6FC0, 0x60);
        assertSourceSlice(state.residency(), ART_TITLE_CARD, 0x6C00, 0x72C0, 0x100);
    }

    @Test
    void immediateLoadingPreservesPendingInactiveDescriptorsAndEverySavedField() {
        var pending = owner.appendList(fresh(), 4);
        var state = owner.loadImmediateList(pending, 3);
        assertSame(pending.queue(), state.queue()); assertQueuedFields(pending, state);
        assertTrue(state.decoder().isEmpty()); assertEquals(0, state.remainingTilesWord());
        assertSame(pending.residency().region(CdR11bCameraArtResidency.Region.AT_AD00),
                state.residency().region(CdR11bCameraArtResidency.Region.AT_AD00));
    }

    @Test
    void immediateLoadingRejectsActiveQueuedDecodeBeforeAnyTableOrPatternMutation() {
        var active = owner.prepare(owner.initList(fresh(), 3));
        var failure = assertThrows(SectionArtException.class, () -> owner.loadImmediateList(active, 1));
        assertEquals(Reason.ACTIVE_QUEUED_DECODE, failure.reason()); assertEquals(Operation.IMMEDIATE_LIST, failure.operation());
        assertTerminalCopy(active, failure.state());
    }

    @Test
    void nativeRamBlocksReturnTheEqualityProvenExistingProductWithoutPatternWrites() throws Exception {
        var before = owner.appendList(coreAndStd(), 4);
        var result = owner.decodeBlocksToRam(before); var state = result.state();
        assertSame(assets.blocksDecode().bytes(), result.blocks()); assertEquals(5280, result.blocks().length());
        assertEquals("bbbfc1389c418e33af33c83782981eb862a8b64842d31aa9262ee6f27b128d58", sha(result.blocks()));
        assertQueuedFields(before, state); assertSame(before.residency(), state.residency());
        assertArrayEquals(before.codeTable().slice(496, 16).copy(), state.codeTable().slice(496, 16).copy());
        assertEquals(0x0736, state.codeTable().unsignedWord(496)); assertEquals(0x083C, state.codeTable().unsignedWord(502));
    }

    @Test
    void ramBlockTableWritesPreserveAllSixteenIncomingTailBytes() {
        var before = fresh(); var result = owner.decodeBlocksToRam(before);
        assertArrayEquals(before.codeTable().slice(496, 16).copy(), result.state().codeTable().slice(496, 16).copy());
        var clearSeed = owner.fromValues(CdAssetBytes.copyOf(new byte[512]), before.residency(), before.sectionWord());
        var zero = owner.decodeBlocksToRam(clearSeed);
        assertArrayEquals(zero.state().codeTable().slice(0, 496).copy(), result.state().codeTable().slice(0, 496).copy(),
                "Additional metamorphic table-write coverage, not the sole source/output oracle");
        assertArrayEquals(dirtyTable().copy(), before.codeTable().copy()); assertQueuedFields(before, result.state());
    }

    @Test
    void ramBlocksRejectActiveQueuedDecodeBeforeWritingAnything() {
        var active = owner.prepare(owner.initList(fresh(), 3));
        var failure = assertThrows(SectionArtException.class, () -> owner.decodeBlocksToRam(active));
        assertEquals(Reason.ACTIVE_QUEUED_DECODE, failure.reason()); assertEquals(Operation.BLOCKS_RAM, failure.operation());
        assertTrue(failure.completedRamPrefix().isEmpty()); assertTerminalCopy(active, failure.state());
    }

    @Test
    void lifeIconCanRunDuringAnActiveQueueWithoutChangingItsTableOrSavedDecoder() {
        var active = owner.prepare(owner.initList(fresh(), 3));
        var state = owner.loadLifeIcon(active, 0x80);
        assertQueuedFields(active, state); assertSame(active.codeTable(), state.codeTable());
        assertEquals(4, knownTiles(state.residency()));
        var raw = assets.view(ART_LIFE_ICON).bytes();
        for (int i = 0; i < 4; i++) assertArrayEquals(raw.slice(i * 32, 32).copy(),
                CdR11bCameraArtResidency.readTile(state.residency(), (0xB420 >>> 5) + i).copy());
    }

    @Test
    void allThreeRawLifeIconQuarterSelectionsAndMarkedAliasesUseOriginalBytes() throws Exception {
        var raw = assets.view(ART_LIFE_ICON).bytes();
        assertEquals("0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a", sha(raw));
        for (int period : new int[]{0,1,2,0x80,0x81,0x82}) {
            var before = fresh(); var state = owner.loadLifeIcon(before, period);
            assertQueuedFields(before, state); assertSame(before.codeTable(), state.codeTable());
            Map<Integer, byte[]> expected = new HashMap<>();
            for (int i = 0; i < 4; i++) expected.put(0xB420 + i * 32, raw.slice((period & 0x7F) * 128 + i * 32, 32).copy());
            assertImage(expected, state.residency()); assertEquals(4, knownTiles(state.residency()));
        }
    }

    @Test
    void invalidLifeIconSelectionStopsBeforeItsFirstUnavailableByte() {
        for (int period : new int[]{3,0x7F,0x83,0xFF}) {
            var before = fresh();
            var failure = assertThrows(SectionArtException.class, () -> owner.loadLifeIcon(before, period));
            assertEquals(Reason.LIFE_ICON_SOURCE, failure.reason()); assertEquals(Operation.LIFE_ICON, failure.operation());
            assertEquals(0xB420, failure.destinationByteOffset());
            assertEquals(0x2362D6L + (period & 0x7F) * 128L, failure.sourcePointer());
            assertTerminalCopy(before, failure.state()); assertTrue(failure.completedRamPrefix().isEmpty());
        }
    }

    @Test
    void realCam4FastServiceHasTwelveOrderedHeadsAndTwentyTwoWaitIterations() throws Exception {
        var before = owner.decodeBlocksToRam(coreAndStd()).state();
        var state = owner.loadSection(before, 0x1900); List<Long> heads = new ArrayList<>();
        int services = 0;
        while (state.queue().headSource() != 0 || state.remainingTilesWord() != 0) {
            state = owner.serviceFast(state); services++;
            if (state.remainingTilesWord() == 0 && state.queue().headSource() != 0) heads.add(state.queue().headSource());
            state = owner.prepare(state);
            assertTrue(services < 100, "Finite component progress guard, not a fitted entry bound");
        }
        assertRows(CAM4, 6);
        var expectedHeads = rows(CAM4).stream().map(r -> Integer.toUnsignedLong(r.asset().range().nativeAddress())).toList();
        assertEquals(expectedHeads, heads); assertEquals(22, services);
        assertEquals(0x025A, state.sectionWord()); assertEquals(0, state.remainingTilesWord());
        assertEquals(5, state.processBudgetWord(), "Final Pata50 =18+18+14; native final tile skips its budget decrement");
        Map<Integer, byte[]> expected = new HashMap<>(); paint(expected, ART_LEVEL_TILES, 0); paintRows(expected, STD); paintRows(expected, CAM4);
        assertEquals(1478, expected.size()); assertImage(expected, state.residency()); assertEquals(1478, knownTiles(state.residency()));
        var life = owner.loadLifeIcon(state, 0x80);
        assertEquals(1482, knownTiles(life.residency())); assertQueuedFields(state, life); assertSame(state.codeTable(), life.codeTable());
    }

    @Test
    void logShadowPreparationRetainsTheCompletedSonicHoleUpperHalf() {
        var state = owner.loadSection(owner.decodeBlocksToRam(coreAndStd()).state(), 0x1900);
        long log = Integer.toUnsignedLong(ART_LOG_SHADOW.range().nativeAddress()); int attempts = 0;
        while (state.remainingTilesWord() != 0 || state.queue().headSource() != log) {
            state = state.remainingTilesWord() == 0 ? owner.prepare(state) : owner.serviceFast(state);
            assertTrue(++attempts < 100);
        }
        var before = state; assertEquals(0x0760, before.codeTable().unsignedWord(500));
        var prepared = owner.prepare(before);
        assertEquals(16, prepared.remainingTilesWord());
        for (int i = 0; i < 128; i++) assertEquals(0x0173, prepared.codeTable().unsignedWord(i * 2));
        assertArrayEquals(before.codeTable().slice(256, 256).copy(), prepared.codeTable().slice(256, 256).copy());
        assertSame(before.residency(), prepared.residency());
    }

    @Test
    void completeCam4PreservesTheRealEarlierTitleGapsAndOnlyOverwritesItsRanges() throws Exception {
        var state = drain(owner.loadSection(owner.decodeBlocksToRam(coreAndStd()).state(), 0x1900));
        assertSourceSlice(state.residency(), ART_TITLE_CARD, 0x6C00, 0x6FC0, 0x60);
        assertSourceSlice(state.residency(), ART_TITLE_CARD, 0x6C00, 0x72C0, 0x100);
        Map<Integer, byte[]> expected = new HashMap<>(); paint(expected, ART_LEVEL_TILES, 0); paintRows(expected, STD); paintRows(expected, CAM4);
        assertImage(expected, state.residency());
    }

    @Test
    void incrementalSectionUsesItsActualListAndRawSectionLowByte() {
        var state = owner.loadSection(fresh(), 0x1900);
        state = owner.prepare(state); var before = state;
        var crossed = owner.updateSection(before, 0x1A00);
        assertEquals(0x045A, crossed.sectionWord()); assertEquals(owner.initList(fresh(), 11).queue(), crossed.queue());
        assertEquals(0, crossed.remainingTilesWord()); assertTrue(crossed.decoder().isEmpty());
        assertSame(before.codeTable(), crossed.codeTable()); assertSame(before.residency(), crossed.residency());
    }

    @Test
    void slowServiceUsesOnlyTheLockHighByteForThreeOrEighteenTiles() {
        var prepared = owner.prepare(owner.initList(fresh(), 9));
        assertEquals(70, prepared.remainingTilesWord());
        var three = owner.serviceSlow(prepared, 0x0001); var eighteen = owner.serviceSlow(prepared, 0x0100);
        assertEquals(67, three.remainingTilesWord()); assertEquals(52, eighteen.remainingTilesWord());
        assertEquals(0x7320, three.queue().headDestination()); assertEquals(0x7500, eighteen.queue().headDestination());
        assertEquals(3, knownTiles(three.residency())); assertEquals(18, knownTiles(eighteen.residency()));
        assertEquals(0, three.processBudgetWord()); assertEquals(0, eighteen.processBudgetWord());
        assertEquals(70, prepared.remainingTilesWord()); assertEquals(0x72C0, prepared.queue().headDestination());
    }

    @Test
    void clearingPlcsDoesNotClearCurrentArtTheCodeTableOrSection() {
        var before = owner.serviceSlow(owner.prepare(owner.initList(fresh(), 9)), 0);
        var state = owner.clearPlcs(before);
        assertArrayEquals(new byte[96], state.queue().bytes().copy()); assertTrue(state.decoder().isEmpty());
        assertEquals(0, state.remainingTilesWord()); assertEquals(0, state.processBudgetWord());
        assertEquals(0, state.hintWord()); assertEquals(0, state.padWord());
        assertEquals(CdR11aSectionArt.Writer.CLEARED, state.savedDecoder().writer());
        assertSame(before.codeTable(), state.codeTable()); assertSame(before.residency(), state.residency());
        assertEquals(before.sectionWord(), state.sectionWord());
    }

    @Test
    void retainedWholeValuesReplayWithoutASecondDecodeOrServiceOnRestore() {
        var cut = owner.prepare(owner.loadSection(owner.decodeBlocksToRam(coreAndStd()).state(), 0x1900));
        var observedTable = cut.codeTable().copy(); var observedQueue = cut.queue().bytes().copy();
        var first = owner.prepare(owner.serviceFast(cut));
        var second = owner.prepare(owner.serviceFast(cut));
        assertEquals(first, second); assertEquals(first.hashCode(), second.hashCode());
        assertArrayEquals(observedTable, cut.codeTable().copy()); assertArrayEquals(observedQueue, cut.queue().bytes().copy());
        assertImage(imageOf(cut.residency()), cut.residency()); // Additional immutable export equality only.
    }

    @Test
    void foreignEqualOwnerValuesAreRejectedAndAReachedBoundaryIsTerminal() {
        var state = fresh(); var other = fromAssets(assets);
        assertNotEquals(state, other.fromValues(state.codeTable(), state.residency(), state.sectionWord()));
        for (Consumer<State> call : calls(owner)) {
            assertThrows(IllegalArgumentException.class, () -> call.accept(other.fromValues(state.codeTable(), state.residency(), state.sectionWord())));
            assertThrows(NullPointerException.class, () -> call.accept(null));
        }
        var failed = assertThrows(SectionArtException.class, () -> owner.initList(state, 0xFFFF)).state();
        for (Consumer<State> call : calls(owner)) assertThrows(IllegalStateException.class, () -> call.accept(failed));
    }

    @Test
    void widthsAndExplicitIngressRejectWithoutDefaultingOrMutating() {
        var before = fresh();
        for (int bad : new int[]{-1, 0x10000}) {
            assertThrows(IllegalArgumentException.class, () -> owner.initList(before, bad));
            assertThrows(IllegalArgumentException.class, () -> owner.appendList(before, bad));
            assertThrows(IllegalArgumentException.class, () -> owner.loadSection(before, bad));
            assertThrows(IllegalArgumentException.class, () -> owner.updateSection(before, bad));
            assertThrows(IllegalArgumentException.class, () -> owner.serviceSlow(before, bad));
            assertThrows(IllegalArgumentException.class, () -> owner.loadImmediateList(before, bad));
            assertThrows(IllegalArgumentException.class, () -> owner.fromValues(before.codeTable(), before.residency(), bad));
        }
        for (int bad : new int[]{-1, 0x100}) assertThrows(IllegalArgumentException.class, () -> owner.loadLifeIcon(before, bad));
        assertThrows(IllegalArgumentException.class, () -> owner.fromValues(CdAssetBytes.copyOf(new byte[511]), before.residency(), 0));
        assertThrows(IllegalArgumentException.class, () -> owner.fromValues(CdAssetBytes.copyOf(new byte[513]), before.residency(), 0));
        assertThrows(NullPointerException.class, () -> fromAssets(null));
        assertThrows(NullPointerException.class, () -> owner.fromValues(null, before.residency(), 0));
        assertThrows(NullPointerException.class, () -> owner.fromValues(before.codeTable(), null, 0));
        assertArrayEquals(dirtyTable().copy(), before.codeTable().copy()); assertImage(Map.of(), before.residency());
    }

    private record Row(CdR11bAssets.Asset asset, int destination) { }
    private static List<Row> rows(String literal) {
        return literal.strip().lines().map(line -> line.split(" ")).map(p -> new Row(CdR11bAssets.Asset.valueOf(p[0]), Integer.parseInt(p[1], 16))).toList();
    }
    private static void assertRows(String literal, int id) {
        var actual = assets.loadPlan().plcs().get(id).entries();
        var expected = rows(literal); assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).asset(), actual.get(i).source());
            assertEquals(expected.get(i).destination(), actual.get(i).vramAddress());
        }
    }
    private static List<Consumer<State>> calls(CdR11bSectionArt a) {
        return List.of(a::clearPlcs, s -> a.initList(s, 3), s -> a.appendList(s, 3), s -> a.loadSection(s, 0),
                s -> a.updateSection(s, 0), a::prepare, a::serviceFast, s -> a.serviceSlow(s, 0),
                a::clearCurrentPatterns, s -> a.loadImmediateList(s, 3), s -> a.decodeBlocksToRam(s), s -> a.loadLifeIcon(s, 0x80));
    }
    private static State fresh() { return owner.fromValues(dirtyTable(), CdR11bCameraArtResidency.unknown(), 0x005A); }
    private static CdAssetBytes dirtyTable() {
        byte[] bytes = new byte[512]; Arrays.fill(bytes, (byte)0x5A); return CdAssetBytes.copyOf(bytes);
    }
    private static State coreAndStd() { return owner.loadImmediateList(owner.loadImmediateList(fresh(), 3), 1); }
    private static State drain(State state) {
        int attempts = 0;
        while (state.queue().headSource() != 0 || state.remainingTilesWord() != 0) {
            state = owner.prepare(owner.serviceFast(state)); assertTrue(++attempts < 100);
        }
        return state;
    }
    private static void assertQueuedFields(State before, State after) {
        assertEquals(before.sectionWord(), after.sectionWord()); assertSame(before.queue(), after.queue());
        assertSame(before.decoder().orElse(null), after.decoder().orElse(null));
        assertEquals(before.savedDecoder(), after.savedDecoder()); assertEquals(before.remainingTilesWord(), after.remainingTilesWord());
        assertEquals(before.processBudgetWord(), after.processBudgetWord()); assertEquals(before.hintWord(), after.hintWord());
        assertEquals(before.padWord(), after.padWord()); assertEquals(before.stopped(), after.stopped());
    }
    private static void assertTerminalCopy(State before, State stopped) {
        assertTrue(stopped.stopped()); assertEquals(before.sectionWord(), stopped.sectionWord());
        assertEquals(before.queue(), stopped.queue()); assertArrayEquals(before.codeTable().copy(), stopped.codeTable().copy());
        assertEquals(before.decoder(), stopped.decoder()); assertEquals(before.savedDecoder(), stopped.savedDecoder());
        assertEquals(before.remainingTilesWord(), stopped.remainingTilesWord()); assertEquals(before.processBudgetWord(), stopped.processBudgetWord());
        assertEquals(before.hintWord(), stopped.hintWord()); assertEquals(before.padWord(), stopped.padWord()); assertEquals(before.residency(), stopped.residency());
    }
    private static CdAssetBytes reference(CdR11bAssets.Asset source) throws Exception {
        var p = source.readerProfile().orElseThrow();
        var bytes = CdNemesisAsset.decode(assets.readerView(source, CdR11bAssets.ReaderKind.JAVA).bytes().copy(), p.outputBytes(), p.outputBytes()).bytes();
        String expected = HASHES.strip().lines().map(l -> l.split(" ")).filter(pieces -> pieces[0].equals(source.name())).findFirst().orElseThrow()[4];
        assertEquals(expected, sha(bytes), source.name()); return bytes;
    }
    private static void paint(Map<Integer, byte[]> image, CdR11bAssets.Asset source, int destination) throws Exception {
        byte[] bytes = reference(source).copy();
        for (int at = 0; at < bytes.length; at += 32) image.put(destination + at, Arrays.copyOfRange(bytes, at, at + 32));
    }
    private static void paintRows(Map<Integer, byte[]> image, String literal) throws Exception {
        for (var row : rows(literal)) paint(image, row.asset(), row.destination());
    }
    private static void assertImage(Map<Integer, byte[]> expected, CdR11bCameraArtResidency.State actual) {
        for (var r : CdR11bCameraArtResidency.Region.values()) {
            var region = actual.region(r);
            byte[] expectedMask = new byte[r.byteLength()];
            for (int at = 0; at < r.byteLength(); at += 32) {
                byte[] tile = expected.get(r.destinationWord() + at);
                if (tile != null) Arrays.fill(expectedMask, at, at + 32, (byte)1);
                for (int b = 0; b < 32; b++) {
                    var value = region.knownByte(at + b);
                    if (tile == null) assertTrue(value.isEmpty(), "Unexpected known byte at " + Integer.toHexString(r.destinationWord() + at + b));
                    else assertEquals(Byte.toUnsignedInt(tile[b]), value.orElseThrow());
                }
            }
            assertArrayEquals(expectedMask, region.knownMask().copy(), r.name());
        }
    }
    private static Map<Integer, byte[]> imageOf(CdR11bCameraArtResidency.State state) {
        Map<Integer, byte[]> image = new HashMap<>();
        for (var r : CdR11bCameraArtResidency.Region.values()) for (int at = 0; at < r.byteLength(); at += 32) {
            if (state.region(r).knownByte(at).isPresent()) image.put(r.destinationWord() + at,
                    CdR11bCameraArtResidency.readTile(state, (r.destinationWord() + at) >>> 5).copy());
        }
        return image;
    }
    private static int knownTiles(CdR11bCameraArtResidency.State state) {
        int count = 0;
        for (var r : CdR11bCameraArtResidency.Region.values()) for (int at = 0; at < r.byteLength(); at += 32) {
            boolean first = state.region(r).knownByte(at).isPresent();
            for (int b = 1; b < 32; b++) assertEquals(first, state.region(r).knownByte(at + b).isPresent());
            if (first) count++;
        }
        return count;
    }
    private static void assertSourceSlice(CdR11bCameraArtResidency.State state, CdR11bAssets.Asset source,
                                          int sourceDestination, int from, int bytes) throws Exception {
        var reference = reference(source);
        for (int at = 0; at < bytes; at += 32) assertArrayEquals(reference.slice(from - sourceDestination + at, 32).copy(),
                CdR11bCameraArtResidency.readTile(state, (from + at) >>> 5).copy());
    }
    private static String sha(CdAssetBytes bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.copy()));
    }
}
