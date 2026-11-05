package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.resources.CdR11aSectionArt.SavedDecoder;
import com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.openggf.game.soniccd.level.CdR12aProfile.Asset.*;
import static com.openggf.game.soniccd.resources.CdR12aSectionArt.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

/** ALL JAVA UNRUN. Original-USA data/component vectors only; no normal-entry/World/current producer claim.
 * Native1ecc5f3a General.Nemesis/LoadPLC:381-579,588-816, Section Art Load:7-46, Main.LoadLifeIcon:599-614.
 * Output hashes/reader extents come from authenticated original-file source-model metadata. Declared malformed
 * source mocks below are format fault vectors, not public retail ingress, a forged World or native parity. */
class TestCdR12aSectionArt {
    private static CdR12aTerrainAssets assets;
    private static CdR12aObjectAssets objects;
    private static CdR12aSectionArt owner;
    private static final SavedDecoder DIRTY = new SavedDecoder(Writer.XOR, 0x1234, 0xA, 0x89ABCDEF, 0x5678, 0x9ABC);
    private static final String HASHES = """
ART_ANIMALS 640 446 ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1
ART_ANTON 704 485 2e0a7e3f6719c8afa2c569d5078824dd7c522a04eeee75fa773830ceea95b101
ART_BIG_RING 3392 1034 31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c
ART_BIG_RING_FLASH 2464 385 39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6
ART_BOULDER 608 271 abdbe20715a2c4784fae851622bb276dc1630abc339efb1e13b99a5ddc4d0c7d
ART_CHECKPOINT 384 212 b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3
ART_DIAGONAL_SPRING 1472 780 1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b
ART_EXPLOSIONS 2240 1257 434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664
ART_FLOAT_BLOCK 480 231 df65b0523d19f52de3f61737c9d130bd493c1591077767651012ac94047e8362
ART_FLOWER 1248 646 db06551b90412becaec9aa45ccfd14dca62c94e1f165718629553f8fdaac6793
ART_GOAL_POST 768 449 68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a
ART_HUD 864 301 0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc
ART_LEVEL_TILES 25952 10723 8f4effe8f6355149077d0fcef7d6f933150a28f073ae7b08ac40148b9012cd97
ART_LOG_SHADOW 512 24 fa208fd33608e8a21ed13a7c9a92cdbbd6a936acd1a377f4ac10e9d333113866
ART_MONITOR_TIME_POSTS 2624 1541 7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f
ART_MOSQUI 2240 847 230205b2348e61dece2c63201ba1c46285dd2d0bb74d45fb4daa402ca91c62bd
ART_PATA_BATA 1600 715 8e7cc0d2af986240146ffd214038f1b2715c71227226261be477cb689eac8943
ART_PLATFORM 1824 453 b33a50d46c89a5115a18548e6a3b083428c8a5bd2bcafc3c4c45e2718c5c768a
ART_POINTS 160 101 2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db
ART_RESULTS 3840 1354 fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d
ART_RINGS 448 244 c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb
ART_ROBOT_GEN_WITHERED 480 332 ce19033d1380d75b2e7b816bd76bfdae7453c77dea728280dd3d2d90cfaaaa67
ART_SIGNPOST 2432 1501 7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c
ART_SPIKES 256 87 75e3391d4045ec96fa925a79bba808a88425444e43d4694f105e7085057c2f72
ART_SPINNING_DISC 384 167 97ff27ee8b9ef0f8903cf420bafd651f954e4dd1f3468bd71e829dc932f24e86
ART_SPRING_WHEEL 256 182 dae6ce7abe19d2a44be58d37d804aff175e3b0d541983ee782d60176bcaf63f2
ART_SPRINGBOARD 672 312 89c1122854699aa7cf8f11633d1512e0bf7acdc84eda2cfd945e9132e657594e
ART_SPRINGS 1152 547 8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416
ART_SWING 448 338 d19d3dc9345daa92d6937f38f716008340c820078be7f7bd4355cfbc63937da4
ART_TAGA_TAGA 1440 783 70dc53f69da2592c19459ec9b591980da55eb27e50e5352f9c5172121ab8fa49
ART_TAMABBOH 1888 1143 aa544f679799481cc55fe2f329ef30be56ee2c8f8a655e5509f02d4035cbed95
ART_TITLE_CARD 3488 940 68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a
ART_TITLE_CARD_TEXT 1664 298 66b74c71966af3b2bdf42828b3a0b5e5fcf73f05c8cae89fa2ff3f28eb01ee70
ART_TUNNEL_DOOR 384 154 dc7be7cdc86c642af5aafd7462269397a6d901678241fde33a35b9209eaf22a0
ART_TUNNEL_DOOR_SPLASH 2752 687 b38d090045f8bf7488b0bdd78cfb244c008764293bc64f275a3f7716d5918f38
            """;
    private static final String STAGE = """
ART_LEVEL_TILES 0
ART_CHECKPOINT d960
            """;
    private static final String STD = """
ART_SPIKES 6600
ART_SPRING_WHEEL 6700
ART_TUNNEL_DOOR 69c0
ART_TITLE_CARD 6c00
ART_TITLE_CARD_TEXT 7a00
ART_PLATFORM 97c0
ART_GOAL_POST 9ee0
ART_SPRINGS a400
ART_HUD ad00
ART_MONITOR_TIME_POSTS b500
ART_EXPLOSIONS d000
ART_POINTS d8c0
ART_FLOWER dae0
ART_RINGS f5c0
            """;

    @BeforeAll static void originalUsaSource() throws Exception {
        String root = System.getProperty("soniccd.media.root"); assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for original USA R12A"); Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) { var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList(); assertEquals(1, cues.size()); cue = cues.getFirst(); }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) { assets = CdR12aTerrainAssets.fromPrograms(source); }
        objects = assets.objects(); owner = fromAssets(assets);
    }

    @Test void sourceFactoryAndFullExplicitIngressCreateNoQueueClearDecoderPreparationOrCurrentKnowledge() {
        byte[] raw = new byte[96]; for (int i = 0; i < 96; i++) raw[i] = (byte) (i * 7 + 3);
        var queue = CdR11aPlcBuffer.fromBytes(CdAssetBytes.copyOf(raw)); var table = dirtyTable(); var current = CdR12aCameraArtResidency.unknown();
        var state = owner.fromValues(table, current, 0xA55A, queue, Optional.empty(), DIRTY, 0, 0x2345, 0x3456, 0x4567);
        assertSame(queue, state.queue()); assertArrayEquals(raw, state.queue().bytes().copy()); assertSame(table, state.codeTable()); assertSame(current, state.residency());
        assertSame(DIRTY, state.savedDecoder()); assertEquals(0xA55A, state.sectionWord()); assertEquals(0x2345, state.processBudgetWord());
        assertEquals(0x3456, state.hintWord()); assertEquals(0x4567, state.padWord()); assertTrue(state.decoder().isEmpty()); assertFalse(state.stopped());
        assertImage(Map.of(), state.residency());
    }

    @Test void all19ListsCopyTheirExactDescriptorBytesAndNoFutureHeaderOrCurrentBytes() {
        var before = fresh();
        for (var list : objects.plcLists()) {
            var state = owner.initList(before, list.nativeId()); byte[] expected = new byte[96];
            for (int i = 0; i < list.entries().size(); i++) descriptor(expected, i * 6, list.entries().get(i).source(), list.entries().get(i).destinationWord());
            assertArrayEquals(expected, state.queue().bytes().copy()); assertSame(before.codeTable(), state.codeTable()); assertSame(before.residency(), state.residency());
            assertTrue(state.decoder().isEmpty()); assertEquals(0, state.remainingTilesWord()); assertEquals(Writer.CLEARED, state.savedDecoder().writer());
        }
        assertArrayEquals(new byte[96], before.queue().bytes().copy());
    }

    @Test void fiveNativeSectionsUseStrictUnsignedThresholdsAndOnlyTheSectionHighByte() {
        int[] x = {0, 0xA00, 0x1500, 0x1800, 0x1D00}; int[] initial = {2, 4, 5, 6, 7}, update = {8, 9, 10, 11, 12};
        for (int i = 0; i < x.length; i++) {
            var before = fresh(); var loaded = owner.loadSection(before, x[i]);
            assertEquals((i * 2 << 8) | 0x5A, loaded.sectionWord()); assertEquals(owner.initList(before, initial[i]).queue(), loaded.queue());
            assertSame(before.codeTable(), loaded.codeTable()); assertSame(before.residency(), loaded.residency());
            assertSame(loaded, owner.updateSection(loaded, x[i]));
            var changed = owner.updateSection(owner.withSectionWord(before, 0xFE5A), x[i]);
            assertEquals((i * 2 << 8) | 0x5A, changed.sectionWord()); assertEquals(owner.initList(before, update[i]).queue(), changed.queue());
        }
        var before = fresh(); var failure = assertThrows(SectionArtException.class, () -> owner.loadSection(before, 0xFFFF));
        assertEquals(Reason.SECTION_TABLE, failure.reason()); assertTerminalCopy(before, failure.state());
    }

    @Test void nativeDescriptorOverflowRetainsTwoAppendedRowsAndTheWholePriorPrefixBeforeStopping() {
        var before = owner.initList(fresh(), 1); byte[] expected = before.queue().bytes().copy();
        descriptor(expected, 84, ART_SPIKES, 0x6600); descriptor(expected, 90, ART_SPRING_WHEEL, 0x6700);
        var failure = assertThrows(SectionArtException.class, () -> owner.appendList(before, 1));
        assertEquals(Reason.DESCRIPTOR_BUFFER, failure.reason()); assertArrayEquals(expected, failure.state().queue().bytes().copy());
        assertSame(before.residency(), failure.state().residency()); assertSame(before.codeTable(), failure.state().codeTable());
        assertThrows(IllegalStateException.class, () -> owner.clearPlcs(failure.state()));
        assertEquals(0, before.queue().bytes().unsignedLong(84));
    }

    @Test void all35ActualNativeReadersProduceTheIndependentLiteralFullSourceHashesAndFiniteCursors() throws Exception {
        assertEquals(35, objects.readerProfiles().size());
        for (String line : HASHES.strip().split("\n")) {
            var c = line.split(" "); var asset = CdR12aProfile.Asset.valueOf(c[0]); int size = Integer.parseInt(c[1]);
            var profile = objects.readerProfile(asset); var decoder = CdNemesisPlcDecoder.prepare(objects.readerView(asset, CdR12aObjectAssets.ReaderKind.NATIVE), profile.sourceBlobRange().nativeAddress(), dirtyTable());
            byte[] bytes = new byte[size]; int at = 0;
            while (decoder.remainingTiles() != 0) { var tile = CdNemesisPlcDecoder.decodeTile(decoder); System.arraycopy(tile.bytes().copy(), 0, bytes, at, 32); at += 32; decoder = tile.state(); }
            assertEquals(size, at); assertEquals(Integer.parseInt(c[2]), decoder.inputOffset(), asset.name());
            assertEquals(c[3], sha(CdAssetBytes.copyOf(bytes)), asset.name()); assertArrayEquals(reference(asset).copy(), bytes);
        }
    }

    @Test void immediateStage3ThenStd1IsOnlyTheirActualSixteenSourcesAndDoesNotAdmitTitleDeferredSection2() throws Exception {
        var before = fresh(); var stage = owner.loadImmediateList(before, 3); var state = owner.loadImmediateList(stage, 1);
        Map<Integer, byte[]> expected = new HashMap<>(); paintRows(expected, STAGE); assertImage(expected, stage.residency());
        paintRows(expected, STD); assertImage(expected, state.residency()); assertQueueFields(before, state); assertTrue(state.decoder().isEmpty());
        assertEquals(16, java.util.stream.Stream.concat(rows(STAGE).stream(), rows(STD).stream()).map(Row::asset).distinct().count());
        assertSame(before.queue(), state.queue()); assertEquals(0, state.queue().headSource()); assertEquals(0x1234, state.savedDecoder().repeatWord());
        assertEquals(CdR12aCameraArtResidency.Boundary.UNKNOWN_PATTERN,
                assertThrows(CdR12aCameraArtResidency.OwnershipBoundary.class, () -> CdR12aCameraArtResidency.readTile(state.residency(), 0x8200 >>> 5)).boundary());
    }

    @Test void queuedSection2StartsOnlyAfterAnExplicitRequestAndServicesThroughTheSameKernel() throws Exception {
        var before = coreAndStd(); var pending = owner.appendList(before, 2);
        assertSame(before.residency(), pending.residency()); assertSame(before.codeTable(), pending.codeTable());
        var done = drain(pending); Map<Integer, byte[]> expected = imageOf(before.residency());
        for (var e : objects.plcLists().get(2).entries()) paint(expected, e.source(), e.destinationWord());
        assertImage(expected, done.residency()); assertEquals(0, done.queue().headSource()); assertEquals(0, done.remainingTilesWord());
        assertArrayEquals(new byte[96], before.queue().bytes().copy());
    }

    @Test void queuedNativePopKeepsItsFinalBudgetAndSavedWordsAndSlowBudgetReadsOnlyLockHighByte() throws Exception {
        var ring = owner.prepare(withQueue(ART_RINGS, 0xF5C0)); var saved = ring.savedDecoder();
        assertEquals(14, ring.remainingTilesWord()); var complete = owner.serviceFast(ring);
        assertEquals(0, complete.remainingTilesWord()); assertEquals(5, complete.processBudgetWord()); assertEquals(saved, complete.savedDecoder());
        assertEquals(0, complete.queue().headSource()); assertEquals(14, knownTiles(complete.residency()));
        Map<Integer, byte[]> expected = new HashMap<>(); paint(expected, ART_RINGS, 0xF5C0); assertImage(expected, complete.residency());
        var prepared = owner.prepare(withQueue(ART_EXPLOSIONS, 0xD000)); var three = owner.serviceSlow(prepared, 1); var eighteen = owner.serviceSlow(prepared, 0x100);
        assertEquals(67, three.remainingTilesWord()); assertEquals(52, eighteen.remainingTilesWord());
        assertEquals(0xD060, three.queue().headDestination()); assertEquals(0xD240, eighteen.queue().headDestination());
        assertEquals(3, knownTiles(three.residency())); assertEquals(18, knownTiles(eighteen.residency()));
    }

    @Test void decoderCodeHolesRetainAllUnwrittenUpperBytesInTheOneIncoming512ByteTable() {
        var before = withQueue(ART_LOG_SHADOW, 0x7E00); var prepared = owner.prepare(before);
        for (int i = 0; i < 128; i++) assertEquals(0x0173, prepared.codeTable().unsignedWord(i * 2));
        assertArrayEquals(before.codeTable().slice(256, 256).copy(), prepared.codeTable().slice(256, 256).copy());
        assertSame(before.residency(), prepared.residency()); assertEquals(0x236A7CL, before.queue().headSource());
        assertEquals(0x5A5A, before.codeTable().unsignedWord(0));
    }

    @Test void nativeBlocksUseTheReal4601ByteReaderAndReturnTheExisting5824ByteProductWithoutCurrentWrites() throws Exception {
        var source = objects.blocksNativeReaderView(); assertSame(source, objects.blocksNativeReaderView());
        assertEquals(4600, objects.view(LEVEL_BLOCKS).length()); assertEquals(4601, source.length()); assertEquals(0x83, source.unsignedByte(4600));
        assertEquals("231f95494373efc60532fa5c48aaf14c684a0c0f71e001fa814fa9fb6c5322eb", sha(source));
        var before = owner.appendList(coreAndStd(), 2); var result = owner.decodeBlocksToRam(before);
        assertSame(assets.blocksDecode().bytes(), result.blocks()); assertEquals(5824, result.blocks().length());
        assertEquals("822d967657cdbed50369b7102726344a861ebd194c65485781d7f93070a3d6ff", sha(result.blocks()));
        assertQueueFields(before, result.state()); assertSame(before.residency(), result.state().residency());
        assertEquals(0x80, objects.view(LEVEL_BLOCKS).unsignedByte(0));
    }

    @Test void canonical4600BlocksAreAnActualShortNativeInputRatherThanInventedPadding() {
        var decoder = CdNemesisPlcDecoder.prepare(objects.view(LEVEL_BLOCKS), assets.profile().range(LEVEL_BLOCKS).nativeAddress(), dirtyTable());
        int completed = 0;
        try { while (decoder.remainingTiles() != 0) { decoder = CdNemesisPlcDecoder.decodeTile(decoder).state(); completed++; } fail("The canonical source does not cover the native refill"); }
        catch (CdNemesisPlcDecoder.DecodeException fault) {
            assertEquals(CdNemesisPlcDecoder.Reason.SOURCE_EXHAUSTED, fault.reason()); assertEquals(4600, fault.diagnostic().inputOffset());
            assertTrue(completed < 182); assertEquals(182 - completed, decoder.remainingTiles());
        }
    }

    @Test void lifeIconRawPresent1AndMarker81SelectExactlyTheMiddle128BytesEvenDuringActiveQueue() throws Exception {
        var before = owner.prepare(owner.initList(fresh(), 3));
        for (int period : new int[]{1, 0x81}) {
            var state = owner.loadLifeIcon(before, period); assertQueueFields(before, state); assertSame(before.codeTable(), state.codeTable());
            Map<Integer, byte[]> expected = new HashMap<>(); for (int i = 0; i < 4; i++) expected.put(0xB420 + i * 32, objects.view(ART_LIFE_ICON).slice(128 + i * 32, 32).copy());
            assertImage(expected, state.residency()); assertEquals(4, knownTiles(state.residency()));
        }
        var failure = assertThrows(SectionArtException.class, () -> owner.loadLifeIcon(before, 3));
        assertEquals(Reason.LIFE_ICON_SOURCE, failure.reason()); assertTerminalCopy(before, failure.state());
        assertEquals(0x236456L, failure.sourcePointer()); assertEquals(0xB420, failure.destinationByteOffset());
    }

    @Test void activeQueuedDecodeRefusesImmediateAndRamCallsBeforeAnyTableOrPatternWrite() {
        var before = owner.prepare(owner.initList(fresh(), 3));
        for (Consumer<State> call : List.<Consumer<State>>of(s -> owner.loadImmediateList(s, 1), owner::decodeBlocksToRam)) {
            var failure = assertThrows(SectionArtException.class, () -> call.accept(before)); assertEquals(Reason.ACTIVE_QUEUED_DECODE, failure.reason()); assertTerminalCopy(before, failure.state());
        }
    }

    @Test void residencyFailureKeepsOneCompletedTileAndTheNativeWholeBudgetDestinationPreadvance() throws Exception {
        var before = owner.prepare(withQueue(ART_RINGS, 0xF760)); var failure = assertThrows(SectionArtException.class, () -> owner.serviceFast(before)); var state = failure.state();
        assertEquals(Reason.RESIDENCY, failure.reason()); assertEquals(0xF780, failure.destinationByteOffset());
        assertEquals(0xF9A0, state.queue().headDestination()); assertEquals(13, state.remainingTilesWord()); assertEquals(17, state.processBudgetWord());
        assertEquals(before.savedDecoder(), state.savedDecoder()); assertSame(before.codeTable(), state.codeTable());
        assertImage(Map.of(0xF760, reference(ART_RINGS).slice(0, 32).copy()), state.residency()); assertEquals(0, knownTiles(before.residency()));
        assertThrows(IllegalStateException.class, () -> owner.prepare(state));
    }

    @Test void declaredMalformedPayloadReachesARealCodeHoleAfterPrepareAndRetainsNoPartialTile() {
        byte[] bad = objects.readerView(ART_RINGS, CdR12aObjectAssets.ReaderKind.NATIVE).copy(); bad[2] = (byte) 0xFF; bad[3] = 0; bad[4] = 0;
        var testOwner = fromAssets(declared(ART_RINGS, CdAssetBytes.copyOf(bad), null));
        var before = fresh(testOwner); var queue = CdR11aPlcBuffer.append(before.queue(), List.of(new CdR11aPlcBuffer.Entry(assets.profile().range(ART_RINGS).nativeAddress(), 0xF5C0)));
        var prepared = testOwner.prepare(testOwner.fromValues(before.codeTable(), before.residency(), before.sectionWord(), queue, Optional.empty(), DIRTY, 0, 0x2345, 0x3456, 0x4567));
        assertEquals(assets.profile().range(ART_RINGS).nativeAddress() + 5L, prepared.queue().headSource()); assertArrayEquals(before.codeTable().copy(), prepared.codeTable().copy());
        var failure = assertThrows(SectionArtException.class, () -> testOwner.serviceFast(prepared)); var state = failure.state();
        assertEquals(Reason.DECODE_TILE, failure.reason()); assertEquals(0xF800, state.queue().headDestination()); assertEquals(14, state.remainingTilesWord()); assertEquals(18, state.processBudgetWord());
        var cause = assertInstanceOf(CdNemesisPlcDecoder.DecodeException.class, failure.getCause());
        assertEquals(CdNemesisPlcDecoder.Reason.UNSUPPORTED_CODE_LENGTH, cause.reason()); assertEquals(0, cause.diagnostic().tableOffset()); assertEquals(0, cause.diagnostic().emittedRows().length());
        assertImage(Map.of(), state.residency()); assertEquals(prepared.savedDecoder(), state.savedDecoder()); assertArrayEquals(dirtyTable().copy(), state.codeTable().copy());
    }

    @Test void declaredMalformedTableRetainsTheHeaderCountAndEveryCompletedTableStoreBeforeItsReachedFault() {
        byte[] bad = objects.readerView(ART_RINGS, CdR12aObjectAssets.ReaderKind.NATIVE).copy();
        bad[2] = (byte) 0x80; bad[3] = 1; bad[4] = 0; bad[5] = (byte) 0x81; bad[6] = 1; bad[7] = 2;
        var testOwner = fromAssets(declared(ART_RINGS, CdAssetBytes.copyOf(bad), null)); var before = fresh(testOwner);
        var queue = CdR11aPlcBuffer.append(before.queue(), List.of(new CdR11aPlcBuffer.Entry(assets.profile().range(ART_RINGS).nativeAddress(), 0xF5C0)));
        var pending = testOwner.fromValues(before.codeTable(), before.residency(), before.sectionWord(), queue, Optional.empty(), DIRTY, 0, 0x2345, 0x3456, 0x4567);
        var failure = assertThrows(SectionArtException.class, () -> testOwner.prepare(pending)); var state = failure.state();
        assertEquals(Reason.DECODE_PREPARE, failure.reason()); assertEquals(14, state.remainingTilesWord()); assertTrue(state.decoder().isEmpty());
        assertSame(pending.queue(), state.queue()); assertSame(DIRTY, state.savedDecoder()); assertSame(pending.residency(), state.residency());
        for (int at = 0; at < 256; at += 2) assertEquals(0x0100, state.codeTable().unsignedWord(at));
        assertArrayEquals(pending.codeTable().slice(256, 256).copy(), state.codeTable().slice(256, 256).copy());
        var cause = assertInstanceOf(CdNemesisPlcDecoder.DecodeException.class, failure.getCause());
        assertEquals(CdNemesisPlcDecoder.Reason.TABLE_ADDRESS, cause.reason()); assertEquals(512, cause.diagnostic().tableOffset());
        assertArrayEquals(dirtyTable().copy(), pending.codeTable().copy());
    }

    @Test void unknownQueuedSourceStopsBeforeHeaderOrTableReadsWithEveryExplicitIncomingFieldRetained() {
        var before = fresh(); byte[] raw = before.queue().bytes().copy(); raw[0] = 0x12; raw[1] = 0x34; raw[2] = 0x56; raw[3] = 0x78; raw[4] = (byte) 0xD0;
        var queue = CdR11aPlcBuffer.fromBytes(CdAssetBytes.copyOf(raw));
        var pending = owner.fromValues(before.codeTable(), before.residency(), before.sectionWord(), queue, Optional.empty(), DIRTY, 0, 0x2345, 0x3456, 0x4567);
        var failure = assertThrows(SectionArtException.class, () -> owner.prepare(pending));
        assertEquals(Reason.UNKNOWN_SOURCE, failure.reason()); assertEquals(0x12345678L, failure.sourcePointer()); assertTerminalCopy(pending, failure.state());
    }

    @Test void explicitActiveIngressRetainsTheActualFullContinuationAndRejectsMismatchedSourceTableSavedAndCursor() {
        var before = owner.serviceSlow(owner.prepare(withQueue(ART_EXPLOSIONS, 0xD000)), 0);
        var copy = owner.fromValues(before.codeTable(), before.residency(), before.sectionWord(), before.queue(), before.decoder(), before.savedDecoder(), before.remainingTilesWord(), before.processBudgetWord(), before.hintWord(), before.padWord());
        assertEquals(before, copy); assertSame(before.queue(), copy.queue()); assertSame(before.decoder().orElseThrow(), copy.decoder().orElseThrow()); assertSame(before.codeTable(), copy.codeTable());
        assertEquals(owner.serviceFast(before), owner.serviceFast(copy));
        assertThrows(IllegalArgumentException.class, () -> owner.fromValues(dirtyTable(), before.residency(), 0, before.queue(), before.decoder(), before.savedDecoder(), before.remainingTilesWord(), 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> owner.fromValues(before.codeTable(), before.residency(), 0, before.queue(), before.decoder(), DIRTY, before.remainingTilesWord(), 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> owner.fromValues(before.codeTable(), before.residency(), 0, CdR11aPlcBuffer.withHeadSource(before.queue(), before.queue().headSource() + 1), before.decoder(), before.savedDecoder(), before.remainingTilesWord(), 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> owner.fromValues(before.codeTable(), before.residency(), 0, before.queue(), Optional.empty(), before.savedDecoder(), before.remainingTilesWord(), 0, 0, 0));
    }

    @Test void inactivePendingAndStaleFinalDecoderWordsSurviveImmediateWorkAndExplicitValueRecreation() {
        var completed = owner.serviceFast(owner.prepare(withQueue(ART_RINGS, 0xF5C0)));
        assertTrue(completed.decoder().isPresent()); assertEquals(0, completed.decoder().orElseThrow().remainingTiles());
        var pending = owner.appendList(completed, 2); var after = owner.loadImmediateList(pending, 3); assertQueueFields(pending, after);
        assertSame(pending.decoder().orElseThrow(), after.decoder().orElseThrow());
        var recreated = owner.fromValues(after.codeTable(), after.residency(), after.sectionWord(), after.queue(), after.decoder(), after.savedDecoder(), after.remainingTilesWord(), after.processBudgetWord(), after.hintWord(), after.padWord());
        assertEquals(after, recreated); assertSame(after.queue(), recreated.queue()); assertSame(after.codeTable(), recreated.codeTable()); assertSame(after.residency(), recreated.residency());
        assertEquals(owner.prepare(after), owner.prepare(recreated));
    }

    @Test void explicitCurrentTileAndSectionStoresPreserveEveryOtherNativeQueueFieldAndCaptureValue() throws Exception {
        var before = owner.prepare(owner.initList(fresh(), 3)); byte[] tile = new byte[32]; Arrays.fill(tile, (byte) 0xC7);
        var written = owner.writeCurrentTile(before, 0xB4A0, CdAssetBytes.copyOf(tile)); assertQueueFields(before, written); assertSame(before.codeTable(), written.codeTable());
        assertImage(Map.of(0xB4A0, tile), written.residency()); assertImage(Map.of(), before.residency());
        var section = owner.withSectionWord(written, 0x1234); assertEquals(0x1234, section.sectionWord()); assertSame(written.queue(), section.queue()); assertSame(written.residency(), section.residency()); assertSame(written.decoder().orElseThrow(), section.decoder().orElseThrow());
        assertSame(section, owner.withSectionWord(section, 0x1234));
        assertEquals(owner.serviceFast(section), owner.serviceFast(section));
    }

    @Test void clearOperationsOnlyPerformTheirOwnPureNativeValueTransforms() {
        var before = owner.serviceSlow(owner.prepare(withQueue(ART_EXPLOSIONS, 0xD000)), 0);
        var plcs = owner.clearPlcs(before); assertEquals(0, plcs.queue().headSource()); assertArrayEquals(new byte[96], plcs.queue().bytes().copy());
        assertEquals(new SavedDecoder(Writer.CLEARED, 0, 0, 0, 0, 0), plcs.savedDecoder()); assertEquals(0, plcs.remainingTilesWord()); assertEquals(0, plcs.processBudgetWord()); assertEquals(0, plcs.hintWord()); assertEquals(0, plcs.padWord());
        assertSame(before.codeTable(), plcs.codeTable()); assertSame(before.residency(), plcs.residency()); assertEquals(before.sectionWord(), plcs.sectionWord());
        var patterns = owner.clearCurrentPatterns(before); assertQueueFields(before, patterns); assertSame(before.codeTable(), patterns.codeTable());
        assertEquals(1633, knownTiles(patterns.residency()));
    }

    @Test void wrongOwnerTerminalAndScalarIngressChecksDoNotResumeOrDefaultAnActualValue() {
        var before = fresh(); var other = fromAssets(assets); var foreign = fresh(other);
        assertThrows(IllegalArgumentException.class, () -> owner.prepare(foreign)); assertThrows(NullPointerException.class, () -> owner.prepare(null));
        var failure = assertThrows(SectionArtException.class, () -> owner.initList(before, 0xFFFF)); assertTerminalCopy(before, failure.state());
        assertThrows(IllegalStateException.class, () -> owner.writeCurrentTile(failure.state(), 0xB4A0, null));
        assertThrows(IllegalArgumentException.class, () -> owner.fromValues(before.codeTable(), before.residency(), 0x10000, before.queue(), Optional.empty(), DIRTY, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> owner.fromValues(CdAssetBytes.copyOf(new byte[511]), before.residency(), 0, before.queue(), Optional.empty(), DIRTY, 0, 0, 0, 0));
        assertThrows(NullPointerException.class, () -> owner.fromValues(before.codeTable(), before.residency(), 0, null, Optional.empty(), DIRTY, 0, 0, 0, 0));
        assertThrows(NullPointerException.class, () -> owner.fromValues(before.codeTable(), before.residency(), 0, before.queue(), null, DIRTY, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> owner.serviceSlow(before, 0x10000));
    }

    @Test void factoryRejectsAJavaLengthBlockViewAndNativeReaderExtentMismatchBeforeAnyStateExists() {
        var data = declared(null, null, objects.view(LEVEL_BLOCKS));
        assertThrows(IllegalArgumentException.class, () -> fromAssets(data));
        var bad = objects.readerView(ART_RINGS, CdR12aObjectAssets.ReaderKind.NATIVE).slice(0, 243);
        assertThrows(IllegalArgumentException.class, () -> fromAssets(declared(ART_RINGS, bad, null)));
    }

    private static State fresh() { return fresh(owner); }
    private static State fresh(CdR12aSectionArt target) { return target.fromValues(dirtyTable(), CdR12aCameraArtResidency.unknown(), 0xA55A, CdR11aPlcBuffer.cleared(), Optional.empty(), DIRTY, 0, 0x2345, 0x3456, 0x4567); }
    private static CdAssetBytes dirtyTable() { byte[] bytes = new byte[512]; Arrays.fill(bytes, (byte) 0x5A); return CdAssetBytes.copyOf(bytes); }
    private static State withQueue(CdR12aProfile.Asset asset, int destination) {
        var before = fresh(); var queue = CdR11aPlcBuffer.append(before.queue(), List.of(new CdR11aPlcBuffer.Entry(assets.profile().range(asset).nativeAddress(), destination)));
        return owner.fromValues(before.codeTable(), before.residency(), before.sectionWord(), queue, Optional.empty(), DIRTY, 0, 0x2345, 0x3456, 0x4567);
    }
    private static State coreAndStd() { return owner.loadImmediateList(owner.loadImmediateList(fresh(), 3), 1); }
    private static State drain(State state) { int guard = 0; while (state.queue().headSource() != 0 || state.remainingTilesWord() != 0) { state = owner.prepare(owner.serviceFast(state)); assertTrue(++guard < 400, "finite declared component bound"); } return state; }
    private static void descriptor(byte[] bytes, int at, CdR12aProfile.Asset asset, int destination) { long pointer = assets.profile().range(asset).nativeAddress(); for (int i = 0; i < 4; i++) bytes[at + i] = (byte) (pointer >>> (24 - 8 * i)); bytes[at + 4] = (byte) (destination >>> 8); bytes[at + 5] = (byte) destination; }
    private static void assertQueueFields(State before, State after) { assertEquals(before.sectionWord(), after.sectionWord()); assertSame(before.queue(), after.queue()); assertSame(before.decoder().orElse(null), after.decoder().orElse(null)); assertSame(before.savedDecoder(), after.savedDecoder()); assertEquals(before.remainingTilesWord(), after.remainingTilesWord()); assertEquals(before.processBudgetWord(), after.processBudgetWord()); assertEquals(before.hintWord(), after.hintWord()); assertEquals(before.padWord(), after.padWord()); }
    private static void assertTerminalCopy(State before, State after) { assertTrue(after.stopped()); assertEquals(before.sectionWord(), after.sectionWord()); assertEquals(before.queue(), after.queue()); assertArrayEquals(before.codeTable().copy(), after.codeTable().copy()); assertEquals(before.decoder(), after.decoder()); assertEquals(before.savedDecoder(), after.savedDecoder()); assertEquals(before.remainingTilesWord(), after.remainingTilesWord()); assertEquals(before.processBudgetWord(), after.processBudgetWord()); assertEquals(before.hintWord(), after.hintWord()); assertEquals(before.padWord(), after.padWord()); assertSame(before.residency(), after.residency()); }
    private record Row(CdR12aProfile.Asset asset, int destination) { }
    private static List<Row> rows(String literal) { return literal.strip().lines().map(s -> s.split(" ")).map(c -> new Row(CdR12aProfile.Asset.valueOf(c[0]), Integer.parseInt(c[1], 16))).toList(); }
    private static CdAssetBytes reference(CdR12aProfile.Asset asset) throws Exception { var bytes = objects.decode(asset).bytes(); var row = HASHES.strip().lines().map(s -> s.split(" ")).filter(c -> c[0].equals(asset.name())).findFirst().orElseThrow(); assertEquals(row[3], sha(bytes)); return bytes; }
    private static void paint(Map<Integer, byte[]> image, CdR12aProfile.Asset asset, int destination) throws Exception { byte[] bytes = reference(asset).copy(); for (int at = 0; at < bytes.length; at += 32) image.put(destination + at, Arrays.copyOfRange(bytes, at, at + 32)); }
    private static void paintRows(Map<Integer, byte[]> image, String literal) throws Exception { for (var row : rows(literal)) paint(image, row.asset(), row.destination()); }
    private static void assertImage(Map<Integer, byte[]> expected, CdR12aCameraArtResidency.State actual) { for (var r : CdR12aCameraArtResidency.Region.values()) { byte[] mask = new byte[r.byteLength()]; for (int at = 0; at < r.byteLength(); at += 32) { var tile = expected.get(r.destinationWord() + at); if (tile != null) Arrays.fill(mask, at, at + 32, (byte) 1); for (int b = 0; b < 32; b++) { var value = actual.region(r).knownByte(at + b); if (tile == null) assertTrue(value.isEmpty()); else assertEquals(Byte.toUnsignedInt(tile[b]), value.orElseThrow()); } } assertArrayEquals(mask, actual.region(r).knownMask().copy(), r.name()); } }
    private static Map<Integer, byte[]> imageOf(CdR12aCameraArtResidency.State current) { Map<Integer, byte[]> image = new HashMap<>(); for (var r : CdR12aCameraArtResidency.Region.values()) for (int at = 0; at < r.byteLength(); at += 32) if (current.region(r).knownByte(at).isPresent()) image.put(r.destinationWord() + at, CdR12aCameraArtResidency.readTile(current, (r.destinationWord() + at) >>> 5).copy()); return image; }
    private static int knownTiles(CdR12aCameraArtResidency.State state) { return imageOf(state).size(); }
    private static String sha(CdAssetBytes bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.copy())); }
    /** Declared source-format fixtures only; actual authenticated retail owners cannot expose these mutations. */
    private static CdR12aTerrainAssets declared(CdR12aProfile.Asset alteredAsset, CdAssetBytes alteredBytes, CdAssetBytes blockBytes) {
        var a = mock(CdR12aTerrainAssets.class); var o = mock(CdR12aObjectAssets.class);
        when(a.identity()).thenReturn(assets.identity()); when(a.profile()).thenReturn(assets.profile()); when(a.objects()).thenReturn(o); when(a.blocksDecode()).thenReturn(assets.blocksDecode());
        when(o.identity()).thenReturn(objects.identity()); when(o.profile()).thenReturn(objects.profile()); when(o.readerProfiles()).thenReturn(objects.readerProfiles());
        when(o.plcLists()).thenReturn(objects.plcLists()); when(o.sections()).thenReturn(objects.sections());
        when(o.blocksNativeReaderView()).thenReturn(blockBytes == null ? objects.blocksNativeReaderView() : blockBytes);
        when(o.view(any())).thenAnswer(call -> objects.view(call.getArgument(0)));
        when(o.readerView(any(), any())).thenAnswer(call -> call.getArgument(0) == alteredAsset ? alteredBytes : objects.readerView(call.getArgument(0), call.getArgument(1)));
        return a;
    }
}
