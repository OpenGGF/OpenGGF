package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aLoadPlan;
import com.openggf.game.soniccd.level.CdR11aProfile;
import com.openggf.game.soniccd.media.CdMmdImage;
import org.junit.jupiter.api.Test;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Assumptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static com.openggf.game.soniccd.level.CdR11aAsset.*;
import static com.openggf.game.soniccd.resources.CdR11aSectionArt.Reason.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ROM-free declared-source specifications. ALL JAVA UNRUN by this author.
 * Source authority: scddisasm 1ecc5f3a, Section Art Load.asm:7-46;
 * Palmtree Panic/Section Data (Act 1 Present).asm:7-26;
 * Palmtree Panic/Data (Act 1 Present).asm:85-229;
 * Functions (General).asm:588-785. No fixture authenticates media.
 */
class TestCdR11aSectionArt {
    private static final CdR11aProfile PROFILE = CdR11aProfile.forStage(
            CdReleaseVariant.USA, CdStagePayload.R11A.stage());
    private static final int MOSQUI_ONLY = 100;
    private static final int PATA_ONLY = 101;
    private static final int RELOCATED = 102;
    private static final int LONG_LIST = 103;
    private static final int UNKNOWN_AFTER_PATA = 104;

    // Catches confusing canonical full-list Pata row2 with actual incremental PLC9 row0.
    @Test void actualPlc9BodyRetainsAllThreeNativePointersAndDestinations() {
        var body = CdR11aLoadPlan.decodePlcBody(PROFILE, new byte[]{
                0, 2, 0, 0x23, 0x73, 0x78, 0x76, 0,
                0, 0x23, 0x6E, 0x46, (byte) 0x81, 0x20,
                0, 0x23, 0x79, 0x50, (byte) 0x85, 0});
        assertEquals(List.of(ART_PATA_BATA, ART_ANTON, ART_TAMABBOH),
                body.stream().map(CdR11aLoadPlan.PlcEntry::source).toList());
        assertEquals(List.of(0x237378, 0x236E46, 0x237950),
                body.stream().map(e -> e.sourceRange().nativeAddress()).toList());
        assertEquals(List.of(0x7600, 0x8120, 0x8500),
                body.stream().map(CdR11aLoadPlan.PlcEntry::vramAddress).toList());
        var owner = owner();
        var queued = owner.initList(dev(owner, 1), 9);
        assertEquals(0x237378L, queued.queue().bytes().unsignedLong(0));
        assertEquals(0x236E46L, queued.queue().bytes().unsignedLong(6));
        assertEquals(0x237950L, queued.queue().bytes().unsignedLong(12));
        assertEquals(0x8500, queued.queue().bytes().unsignedWord(16));
        assertEquals(0, queued.queue().bytes().unsignedLong(18));
    }

    // Catches signed comparisons, equality staying in the old section, and low-byte loss.
    @Test void thresholdsUseUnsignedWordEqualityAndAddressedHighByte() {
        var owner = owner();
        int[][] cases = {{0, 0}, {0x67F, 0}, {0x680, 2}, {0xF7F, 2},
                {0xF80, 4}, {0x197F, 4}, {0x1980, 6}, {0x1F7F, 6},
                {0x1F80, 8}, {0x8000, 8}, {0xFFFE, 8}};
        for (int[] c : cases) {
            var state = owner.updateSection(dev(owner, 0xFE01), c[0]);
            assertEquals((c[1] << 8) | 1, state.sectionWord());
        }
        assertEquals(0x0201, owner.updateSection(dev(owner, 1), 0x680).sectionWord());
    }

    // Catches queuing on an unchanged addressed byte, or treating its low byte as the ID.
    @Test void unchangedSectionReturnsExactStateAndNoRequest() {
        var owner = owner();
        var state = dev(owner, 0x0201);
        assertSame(state, owner.updateSection(state, 0x680));
        assertEquals(0, state.queue().headSource());
    }

    // Catches inventing a sentinel range or writing section before the reached table read.
    @Test void ffffStopsBeforeSectionWriteAndRetainsResidentValue() {
        var owner = owner();
        var state = dev(owner, 0x1234);
        var fault = assertThrows(CdR11aSectionArt.SectionArtException.class,
                () -> owner.updateSection(state, 0xFFFF));
        assertEquals(SECTION_TABLE, fault.reason());
        assertEquals(0x1234, fault.state().sectionWord());
        assertSame(state.residency(), fault.state().residency());
        assertTrue(fault.state().stopped());
        assertEquals(0, state.queue().headSource());
    }

    // Catches using the update list for LoadSectionArt or canceling existing active work.
    @Test void loadSectionAppendsCompleteFullListWithoutCancelingDecoder() {
        var owner = owner();
        var partial = owner.serviceSlow(owner.prepare(owner.initList(dev(owner, 1), PATA_ONLY)), 0);
        var appended = owner.loadSection(partial, 0x680);
        assertEquals(0x0201, appended.sectionWord());
        assertEquals(partial.decoder(), appended.decoder());
        assertEquals(47, appended.remainingTilesWord());
        assertEquals(0x2364C2L, appended.queue().bytes().unsignedLong(6));
        assertEquals(0x2381C0L, appended.queue().bytes().unsignedLong(12));
        assertEquals(0x237378L, appended.queue().bytes().unsignedLong(18));
        assertEquals(0x237950L, appended.queue().bytes().unsignedLong(30));
        assertEquals(0x2320DAL, appended.queue().bytes().unsignedLong(36));
        assertEquals(partial.residency(), appended.residency());
    }

    // Catches clearing state before the requested list pointer has been resolved.
    @Test void invalidListResolutionRetainsActiveWorkAndSectionStoreOrder() {
        var lists = new ArrayList<>(lists());
        var sections = List.of(new CdR11aLoadPlan.SectionArt(0xFFFF, 65000, 65000));
        var owner = new CdR11aSectionArt(lists, sections, programs());
        var active = owner.prepare(owner.initList(dev(owner, 0x0201), PATA_ONLY));
        var direct = assertThrows(CdR11aSectionArt.SectionArtException.class,
                () -> owner.initList(active, 65000));
        assertEquals(PLC_LIST, direct.reason());
        assertEquals(active.queue(), direct.state().queue());
        assertEquals(active.decoder(), direct.state().decoder());
        var section = assertThrows(CdR11aSectionArt.SectionArtException.class,
                () -> owner.updateSection(active, 0));
        assertEquals(1, section.state().sectionWord());
        assertEquals(active.queue(), section.state().queue());
        assertEquals(active.savedDecoder(), section.state().savedDecoder());
    }

    // Catches request or preparation writing pixels, and preparation implicitly servicing.
    @Test void requestPreparationAndServiceAreSeparateCuts() {
        var owner = owner();
        var initial = dev(owner, 1);
        var queued = owner.updateSection(initial, 0x680);
        assertSame(initial.residency(), queued.residency());
        assertEquals(0, queued.remainingTilesWord());
        assertTrue(queued.decoder().isEmpty());
        assertSame(queued, owner.serviceFast(queued));
        var prepared = owner.prepare(queued);
        assertSame(queued.residency(), prepared.residency());
        assertEquals(50, prepared.remainingTilesWord());
        assertEquals(0x237380L, prepared.queue().headSource());
        assertSame(prepared, owner.prepare(prepared));
        assertTile(prepared, 0x3B0, repeated(32, 0x11));
        assertTile(owner.serviceSlow(prepared, 0), 0x3B0, repeated(32, 0xAA));
    }

    // Catches erasing holes in the separate Nemesis table or resetting it in InitPLC.
    @Test void suppliedCodeTableHolesSurvivePrepareAndCancellation() {
        var owner = owner();
        var initial = dev(owner, 1);
        var prepared = owner.prepare(owner.initList(initial, PATA_ONLY));
        assertEquals(1, prepared.codeTable().unsignedByte(0));
        assertEquals(0x7A, prepared.codeTable().unsignedByte(1));
        assertEquals(0x4D, prepared.codeTable().unsignedByte(510));
        var canceled = owner.initList(prepared, 0);
        assertArrayEquals(prepared.codeTable().copy(), canceled.codeTable().copy());
        assertEquals(0x4D, initial.codeTable().unsignedByte(0));
    }

    // Catches wrong source destination selection and missing normal/XOR run semantics.
    @Test void literalNormalAndXorRunProgramsProduceDifferentRows() {
        var normal = single(false, 1, 0x7600);
        var xor = single(true, 1, 0x7600);
        var a = normal.serviceFast(normal.prepare(normal.initList(dev(normal, 0), PATA_ONLY)));
        var b = xor.serviceFast(xor.prepare(xor.initList(dev(xor, 0), PATA_ONLY)));
        assertTile(a, 0x3B0, repeated(32, 0xAA));
        byte[] want = new byte[32];
        for (int row = 0; row < 8; row += 2) Arrays.fill(want, row * 4, row * 4 + 4, (byte) 0xAA);
        assertTile(b, 0x3B0, want);
    }

    // Catches servicing 50 at once, double-decrementing, and incorrect final-budget convention.
    @Test void pataSlowCommitsThreeTilesPerCallThenTwo() {
        var owner = owner();
        var s = owner.prepare(owner.initList(dev(owner, 1), 9));
        for (int call = 1; call <= 16; call++) {
            s = owner.serviceSlow(s, 0);
            assertEquals(50 - call * 3, s.remainingTilesWord());
            assertEquals(0, s.processBudgetWord());
        }
        assertTile(s, 0x3DF, repeated(32, 0xAA));
        assertTile(s, 0x3E0, repeated(32, 0x11));
        s = owner.serviceSlow(s, 0);
        assertEquals(0, s.remainingTilesWord());
        assertEquals(2, s.processBudgetWord());
        assertEquals(0x236E46L, s.queue().headSource());
        assertTile(s, 0x3E1, repeated(32, 0xAA));
        assertTile(s, 0x3E2, repeated(32, 0x11));
        assertSame(s, owner.serviceSlow(s, 0));
    }

    // Catches treating fast as an atomic whole asset or decrementing budget on its final tile.
    @Test void pataFastUsesEighteenEighteenFourteenAndBudgetFive() {
        var owner = owner();
        var s = owner.prepare(owner.initList(dev(owner, 1), 9));
        s = owner.serviceFast(s); assertEquals(32, s.remainingTilesWord());
        s = owner.serviceFast(s); assertEquals(14, s.remainingTilesWord());
        s = owner.serviceFast(s); assertEquals(0, s.remainingTilesWord());
        assertEquals(5, s.processBudgetWord());
        assertEquals(0x236E46L, s.queue().headSource());
    }

    // Catches testing the numeric word or the low byte instead of the addressed high byte.
    @Test void slowScrollLock0001IsThreeBut0100IsEighteen() {
        var owner = owner();
        var s = owner.prepare(owner.initList(dev(owner, 1), PATA_ONLY));
        assertEquals(47, owner.serviceSlow(s, 0x0001).remainingTilesWord());
        assertEquals(32, owner.serviceSlow(s, 0x0100).remainingTilesWord());
        assertEquals(32, owner.serviceSlow(s, 0xFFFF).remainingTilesWord());
    }

    // Catches spending leftover service on the next head without a subsequent CPU preparation.
    @Test void wholePlc9NeedsFortyFiveSelectedSlowServicesAndCpuPrepareBetweenHeads() {
        var owner = owner();
        var s = owner.initList(dev(owner, 1), 9);
        int services = 0;
        int[] calls = {17, 8, 20};
        long[] heads = {0x236E46L, 0x237950L, 0};
        for (int row = 0; row < 3; row++) {
            assertEquals(0, s.remainingTilesWord());
            s = owner.prepare(s);
            for (int i = 0; i < calls[row]; i++) { s = owner.serviceSlow(s, 0); services++; }
            assertEquals(0, s.remainingTilesWord());
            assertEquals(heads[row], s.queue().headSource());
            assertSame(s, owner.serviceFast(s));
        }
        assertEquals(45, services); // Selected service calls, never gameframes/scanlines/native clocks.
        assertTile(s, 0x3B0, repeated(32, 0xAA));
        assertTile(s, 0x409, repeated(32, 0xBB));
        assertTile(s, 0x428, repeated(32, 0xCC));
        assertTile(s, 0x462, repeated(32, 0xCC));
        assertTile(s, 0x463, repeated(32, 0x33));
    }

    // Catches preadvancing by only written tiles, or storing final local decoder registers to RAM.
    @Test void finalTilePreadvancesFullBudgetAndRetainsEarlierSavedRegisters() {
        var owner = single(false, 1, 0x7600);
        var ready = owner.prepare(owner.initList(dev(owner, 0), PATA_ONLY));
        var done = owner.serviceFast(ready);
        assertEquals(18, done.processBudgetWord());
        assertEquals(ready.savedDecoder(), done.savedDecoder());
        assertNotEquals(done.decoder().orElseThrow().pixelNibble(), done.savedDecoder().pixelNibble());
        // One-entry POP copies a zero source long, but its destination comes from bytes10..11 (zero).
        assertEquals(0, done.queue().headSource());
        assertEquals(0, done.queue().headDestination());
        assertEquals(0, done.decoder().orElseThrow().remainingTiles());
    }

    // Catches queue cursor publication on every tile rather than at budget exhaustion.
    @Test void nonfinalBudgetExhaustionPublishesCursorAndSavedLowFields() {
        var owner = owner();
        var ready = owner.prepare(owner.initList(dev(owner, 1), PATA_ONLY));
        var partial = owner.serviceSlow(ready, 0);
        var decoder = partial.decoder().orElseThrow();
        assertEquals(decoder.sourcePointer(), partial.queue().headSource());
        assertEquals(0x7660, partial.queue().headDestination());
        assertEquals(47, decoder.remainingTiles());
        assertEquals(decoder.remainingTiles(), partial.remainingTilesWord());
        assertEquals(decoder.repeatWord(), partial.savedDecoder().repeatWord());
        assertEquals(decoder.pixelNibble(), partial.savedDecoder().pixelNibble());
        assertEquals(decoder.previousRow(), partial.savedDecoder().previousRow());
        assertEquals(decoder.readWord(), partial.savedDecoder().readWord());
        assertEquals(decoder.shiftWord(), partial.savedDecoder().shiftWord());
    }

    // Catches queue replacement retaining active work, clearing NEMBUFFER, or undoing resident writes.
    @Test void initCancelsActiveDecoderButRetainsCommittedPixelsAndTable() {
        var owner = owner();
        var partial = owner.serviceSlow(owner.prepare(owner.initList(dev(owner, 0x0201), 9)), 0);
        var canceled = owner.initList(partial, MOSQUI_ONLY);
        assertEquals(0x0201, canceled.sectionWord());
        assertTrue(canceled.decoder().isEmpty());
        assertEquals(0, canceled.remainingTilesWord());
        assertEquals(0, canceled.processBudgetWord());
        assertEquals(CdR11aSectionArt.Writer.CLEARED, canceled.savedDecoder().writer());
        assertEquals(0, canceled.savedDecoder().repeatWord());
        assertEquals(0, canceled.savedDecoder().pixelNibble());
        assertEquals(0, canceled.savedDecoder().previousRow());
        assertEquals(0, canceled.savedDecoder().readWord());
        assertEquals(0, canceled.savedDecoder().shiftWord());
        assertEquals(0, canceled.hintWord()); assertEquals(0, canceled.padWord());
        assertArrayEquals(partial.codeTable().copy(), canceled.codeTable().copy());
        assertEquals(partial.residency(), canceled.residency());
        assertTile(canceled, 0x3B0, repeated(32, 0xAA));
    }

    // Catches append canceling work or consulting a fictitious busy boolean.
    @Test void appendDuringActiveDecodePreservesContinuationAndBudget() {
        var owner = owner();
        var partial = owner.serviceSlow(owner.prepare(owner.initList(dev(owner, 0), PATA_ONLY)), 0);
        var next = owner.appendList(partial, MOSQUI_ONLY);
        assertEquals(partial.decoder(), next.decoder());
        assertEquals(partial.savedDecoder(), next.savedDecoder());
        assertEquals(partial.remainingTilesWord(), next.remainingTilesWord());
        assertEquals(partial.processBudgetWord(), next.processBudgetWord());
        assertEquals(partial.queue().headSource(), next.queue().headSource());
        assertEquals(0x23702AL, next.queue().bytes().unsignedLong(6));
    }

    // Catches reloading only Pata's subrange instead of all 70 physical Mosqui tiles.
    @Test void mosquiReloadReplacesSeventyWhileUnrelatedRegionsSurvive() {
        var owner = owner();
        var p = drainOne(owner, owner.prepare(owner.initList(dev(owner, 0), PATA_ONLY)), true);
        var s = owner.prepare(owner.initList(p, MOSQUI_ONLY));
        s = owner.serviceFast(s); assertEquals(52, s.remainingTilesWord());
        s = owner.serviceFast(s); assertEquals(34, s.remainingTilesWord());
        s = owner.serviceFast(s); assertEquals(16, s.remainingTilesWord());
        s = owner.serviceFast(s); assertEquals(0, s.remainingTilesWord());
        assertEquals(3, s.processBudgetWord());
        assertTile(s, 0x3A0, repeated(32, 0xDD));
        assertTile(s, 0x3B0, repeated(32, 0xDD));
        assertTile(s, 0x3E5, repeated(32, 0xDD));
        assertTile(s, 0x424, repeated(32, 0x22));
        assertTile(s, 0x441, repeated(32, 0x33));
    }

    // Catches filtering an unknown second producer away or failing before Pata is actually completed.
    @Test void unknownProducerStopsAtReachedPrepareAfterExactResidentPrefix() {
        var owner = new CdR11aSectionArt(lists(), sections(), List.of(programs().get(0)));
        var s = drainOne(owner, owner.prepare(owner.initList(dev(owner, 0), 9)), true);
        var completedPata = s;
        var fault = assertThrows(CdR11aSectionArt.SectionArtException.class, () -> owner.prepare(completedPata));
        assertEquals(UNKNOWN_SOURCE, fault.reason());
        assertEquals(0x236E46L, fault.sourcePointer());
        assertEquals(0x8120, fault.destinationWord());
        assertEquals(completedPata.residency(), fault.state().residency());
        assertEquals(completedPata.queue(), fault.state().queue());
        assertTile(fault.state(), 0x3E1, repeated(32, 0xAA));
        assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
                () -> CdR11aCameraArtResidency.readTile(fault.state().residency(), 0x409));
        assertThrows(IllegalStateException.class, () -> owner.prepare(fault.state()));
    }

    // Catches canonical destination being used instead of the actual queued destination.
    @Test void producerIdentityDoesNotOverrideRelocatedDestination() {
        var owner = owner();
        var s = owner.prepare(owner.initList(dev(owner, 0), RELOCATED));
        s = owner.serviceSlow(s, 0);
        assertTile(s, 0x3A0, repeated(32, 0xAA));
        assertTile(s, 0x3B0, repeated(32, 0x11));
        assertEquals(0x7460, s.queue().headDestination());
    }

    // Catches treating POP as a correct shift/erase rather than 22 forward MOVE.L stores.
    @Test void popRetainsLastEightBytesAndRequeuesTailSourceAtFullShape() {
        var rows = new ArrayList<CdR11aLoadPlan.PlcEntry>();
        for (int i = 0; i < 16; i++) rows.add(entry(ART_PATA_BATA, 0x7400 + i * 0x20));
        var owner = new CdR11aSectionArt(List.of(list(LONG_LIST, rows)), sections(),
                List.of(new CdR11aSectionArt.SourceProgram(entry(ART_PATA_BATA, 0x7600), run(1, false, 0xA))));
        var queued = owner.initList(dev(owner, 0), LONG_LIST);
        var before = owner.prepare(queued);
        byte[] tail = before.queue().bytes().slice(88, 8).copy();
        var after = owner.serviceSlow(before, 0);
        assertArrayEquals(tail, after.queue().bytes().slice(88, 8).copy());
        assertEquals(0x237378L, after.queue().bytes().unsignedLong(90));
        assertEquals(0x75E0, after.queue().bytes().unsignedWord(94));
        assertEquals(0x7420, after.queue().headDestination());
    }

    // Catches discarding all copied descriptors when a later descriptor crosses byte96.
    @Test void descriptorOverflowRetainsCompletedStoresAndClearState() {
        var rows = new ArrayList<CdR11aLoadPlan.PlcEntry>();
        for (int i = 0; i < 17; i++) rows.add(entry(ART_PATA_BATA, 0x7600));
        var owner = new CdR11aSectionArt(List.of(list(LONG_LIST, rows)), sections(), programs());
        var fault = assertThrows(CdR11aSectionArt.SectionArtException.class,
                () -> owner.initList(dev(owner, 0x1201), LONG_LIST));
        assertEquals(DESCRIPTOR_BUFFER, fault.reason());
        assertEquals(0x237378L, fault.state().queue().bytes().unsignedLong(90));
        assertEquals(0x7600, fault.state().queue().bytes().unsignedWord(94));
        assertEquals(0, fault.state().remainingTilesWord());
        assertEquals(CdR11aSectionArt.Writer.CLEARED, fault.state().savedDecoder().writer());
        assertInstanceOf(CdR11aPlcBuffer.BufferBoundary.class, fault.getCause());
        assertEquals(96, ((CdR11aPlcBuffer.BufferBoundary) fault.getCause()).byteOffset());
    }

    // Catches scanning beyond a full buffer silently, even when appending an empty native list.
    @Test void appendFullBufferStopsAtTheReachedSourceScan() {
        var rows = new ArrayList<CdR11aLoadPlan.PlcEntry>();
        for (int i = 0; i < 16; i++) rows.add(entry(ART_PATA_BATA, 0x7600));
        var owner = new CdR11aSectionArt(List.of(list(LONG_LIST, rows), list(0, List.of())), sections(), programs());
        var full = owner.initList(dev(owner, 0), LONG_LIST);
        var fault = assertThrows(CdR11aSectionArt.SectionArtException.class, () -> owner.appendList(full, 0));
        assertEquals(CdR11aPlcBuffer.Access.SOURCE_SCAN,
                ((CdR11aPlcBuffer.BufferBoundary) fault.getCause()).access());
        assertEquals(full.queue(), fault.state().queue());
    }

    // Catches losing native header/count and completed table stores at a prepare read fault.
    @Test void prepareFaultKeepsHeaderCountAndPartialTableButNoPixelsOrSavedRegisters() {
        var bytes = CdAssetBytes.copyOf(new byte[]{0, 3, (byte) 0x8A, 0x71, 0});
        var owner = declared(bytes, 0x7600);
        var queued = owner.initList(dev(owner, 0), PATA_ONLY);
        var fault = assertThrows(CdR11aSectionArt.SectionArtException.class, () -> owner.prepare(queued));
        assertEquals(DECODE_PREPARE, fault.reason());
        assertEquals(3, fault.state().remainingTilesWord());
        assertEquals(1, fault.state().codeTable().unsignedByte(0));
        assertEquals(0x7A, fault.state().codeTable().unsignedByte(1));
        assertEquals(0x4D, fault.state().codeTable().unsignedByte(510));
        assertEquals(queued.savedDecoder(), fault.state().savedDecoder());
        assertEquals(queued.queue(), fault.state().queue());
        assertSame(queued.residency(), fault.state().residency());
        assertTrue(fault.state().decoder().isEmpty());
        assertNull(((CdNemesisPlcDecoder.DecodeException) fault.getCause()).diagnostic().boundaryState());
    }

    // Catches publishing a header count before its atomic word read/alignment has succeeded.
    @Test void incompleteOrOddHeaderDoesNotPublishCountOrTableWrites() {
        var shortOwner = declared(CdAssetBytes.copyOf(new byte[]{0}), 0x7600);
        var queued = shortOwner.initList(dev(shortOwner, 0), PATA_ONLY);
        var shortFault = assertThrows(CdR11aSectionArt.SectionArtException.class, () -> shortOwner.prepare(queued));
        assertEquals(0, shortFault.state().remainingTilesWord());
        assertArrayEquals(queued.codeTable().copy(), shortFault.state().codeTable().copy());
        var original = PROFILE.range(ART_PATA_BATA);
        var oddRange = new CdR11aProfile.FileRange(original.file(), original.domain(), original.fileOffset() + 1, original.length());
        var oddEntry = new CdR11aLoadPlan.PlcEntry(ART_PATA_BATA, oddRange, 0x7600);
        var odd = new CdR11aSectionArt(List.of(list(PATA_ONLY, List.of(oddEntry))), sections(),
                List.of(new CdR11aSectionArt.SourceProgram(oddEntry, run(3, false, 0xA))));
        var oddQueued = odd.initList(dev(odd, 0), PATA_ONLY);
        var oddFault = assertThrows(CdR11aSectionArt.SectionArtException.class, () -> odd.prepare(oddQueued));
        assertEquals(DECODE_PREPARE, oddFault.reason());
        assertEquals(0, oddFault.state().remainingTilesWord());
        assertEquals(CdNemesisPlcDecoder.Reason.SOURCE_ADDRESS,
                ((CdNemesisPlcDecoder.DecodeException) oddFault.getCause()).reason());
    }

    // Catches silently popping zero headers, or skipping their mandatory table/prefetch work.
    @Test void zeroHeaderStillPreparesAndNextCpuPrepareStopsAtUnownedInteriorSource() {
        var owner = single(false, 0, 0x7600);
        var ready = owner.prepare(owner.initList(dev(owner, 0), PATA_ONLY));
        assertEquals(0, ready.remainingTilesWord());
        assertTrue(ready.decoder().isPresent());
        assertEquals(0x237380L, ready.queue().headSource());
        assertSame(ready, owner.serviceFast(ready));
        var fault = assertThrows(CdR11aSectionArt.SectionArtException.class, () -> owner.prepare(ready));
        assertEquals(UNKNOWN_SOURCE, fault.reason());
        assertEquals(0x237380L, fault.sourcePointer());
    }

    // Catches claiming malformed source row evidence has been committed or can resume midrow.
    @Test void tileFaultKeepsCompleteTilePrefixAndTerminalUncommittedRowDiagnostic() {
        var owner = declared(run(3, false, 0xA).slice(0, 9), 0x7600);
        var ready = owner.prepare(owner.initList(dev(owner, 0), PATA_ONLY));
        var fault = assertThrows(CdR11aSectionArt.SectionArtException.class, () -> owner.serviceSlow(ready, 0));
        assertEquals(DECODE_TILE, fault.reason());
        assertEquals(2, fault.state().remainingTilesWord());
        assertEquals(2, fault.state().processBudgetWord());
        assertEquals(0x7660, fault.state().queue().headDestination());
        assertEquals(ready.queue().headSource(), fault.state().queue().headSource());
        assertEquals(ready.savedDecoder(), fault.state().savedDecoder());
        assertTile(fault.state(), 0x3B0, repeated(32, 0xAA));
        assertTile(fault.state(), 0x3B1, repeated(32, 0x11));
        var diagnostic = ((CdNemesisPlcDecoder.DecodeException) fault.getCause()).diagnostic();
        assertEquals(28, diagnostic.emittedRows().length()); // Evidence only: seven uncommitted rows.
        assertEquals(fault.state().decoder().orElseThrow(), diagnostic.boundaryState());
        assertThrows(IllegalStateException.class, () -> owner.serviceFast(fault.state()));
    }

    // Catches consuming/decrementing a tile whose destination is outside the owned windows.
    @Test void unownedDestinationStopsBeforeNextTileAdmissionRetainingEarlierCommit() {
        var owner = single(false, 3, 0xA3E0);
        var ingress = dev(owner, 0);
        // The expanded envelope owns through A3FF. Explicitly seed its last tile, not an unknown default.
        var resident = CdR11aCameraArtResidency.writeTile(ingress.residency(), 0xA3E0,
                CdAssetBytes.copyOf(repeated(32, 0x11)));
        var before = owner.devState(resident, ingress.codeTable(), ingress.sectionWord());
        var ready = owner.prepare(owner.initList(before, PATA_ONLY));
        var fault = assertThrows(CdR11aSectionArt.SectionArtException.class, () -> owner.serviceSlow(ready, 0));
        assertEquals(RESIDENCY, fault.reason());
        assertEquals(0xA400, fault.destinationWord());
        assertEquals(2, fault.state().remainingTilesWord());
        assertEquals(2, fault.state().processBudgetWord());
        assertEquals(0xA440, fault.state().queue().headDestination());
        assertTile(fault.state(), 0x51F, repeated(32, 0xAA));
        assertTile(ready, 0x51F, repeated(32, 0x11));
    }

    // Catches unbounded destination addition or delaying its native preadvance until after decode.
    @Test void destinationPreadvanceWrapsWordBeforeUnownedWriteFault() {
        var owner = single(false, 1, 0xFFE0);
        var ready = owner.prepare(owner.initList(dev(owner, 0), PATA_ONLY));
        var fault = assertThrows(CdR11aSectionArt.SectionArtException.class, () -> owner.serviceSlow(ready, 0));
        assertEquals(RESIDENCY, fault.reason());
        assertEquals(0x0040, fault.state().queue().headDestination());
        assertEquals(0xFFE0, fault.destinationWord());
        assertEquals(1, fault.state().remainingTilesWord());
        assertEquals(3, fault.state().processBudgetWord());
        assertSame(ready.residency(), fault.state().residency());
    }

    // Catches restoring by replay-decoding, mutable component aliasing, or missing value equality.
    @Test void queuedPartialCompletedUnpreparedAndPartialTamSnapshotsReplayAsValues() {
        var owner = owner();
        var queued = owner.initList(dev(owner, 0x0201), 9);
        var partial = owner.serviceSlow(owner.prepare(queued), 0);
        var pataDone = drainOne(owner, partial, false);
        var antonDone = drainOne(owner, owner.prepare(pataDone), false);
        var tamPartial = owner.serviceSlow(owner.prepare(antonDone), 0);
        for (var snapshot : List.of(queued, partial, pataDone, tamPartial)) {
            var a = finishPlc9(owner, snapshot);
            var b = finishPlc9(owner, snapshot);
            assertEquals(a, b); assertEquals(a.hashCode(), b.hashCode());
            assertEquals(1, new HashSet<>(List.of(a, b)).size());
        }
        assertEquals(47, partial.remainingTilesWord());
        assertEquals(0, pataDone.remainingTilesWord());
        assertEquals(0x236E46L, pataDone.queue().headSource());
        assertEquals(56, tamPartial.remainingTilesWord());
        assertTile(queued, 0x3B0, repeated(32, 0x11));
        assertTile(partial, 0x3B3, repeated(32, 0x11));
        assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
                () -> CdR11aCameraArtResidency.readTile(queued.residency(), 0x409));
    }

    // Catches omitting table/section/residency identity from snapshot equality.
    @Test void contentEqualIngressStatesCompareEqualButDifferentKnownBytesDoNot() {
        var owner = owner();
        var a = dev(owner, 1); var b = dev(owner, 1);
        assertEquals(a, b); assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, dev(owner, 0));
        var c = owner.devState(seed(), CdAssetBytes.copyOf(repeated(512, 0x4E)), 1);
        assertNotEquals(a, c);
        var different = CdR11aCameraArtResidency.devSeed(CdAssetBytes.copyOf(repeated(2240, 0x99)),
                CdAssetBytes.copyOf(repeated(896, 0x22)), CdAssetBytes.copyOf(repeated(2016, 0x33)));
        assertNotEquals(a, owner.devState(different, a.codeTable(), 1));
    }

    // Catches recording final decoder RAM stores after an exact-budget completion.
    @Test void exactBudgetCompletionLeavesBudgetOneAndDoesNotSaveFinalRegisters() {
        var owner = single(false, 3, 0x7600);
        var ready = owner.prepare(owner.initList(dev(owner, 0), PATA_ONLY));
        var done = owner.serviceSlow(ready, 0);
        assertEquals(0, done.remainingTilesWord());
        assertEquals(1, done.processBudgetWord());
        assertEquals(ready.savedDecoder(), done.savedDecoder());
        assertTile(done, 0x3B2, repeated(32, 0xAA));
    }

    // Catches turning empty CPU/service cuts into initialization, or touching supplied ingress.
    @Test void emptyQueueAndEmptyListHaveNoPrepareOrResidentSideEffects() {
        var owner = owner(); var ingress = dev(owner, 0xA501);
        assertSame(ingress, owner.prepare(ingress));
        assertSame(ingress, owner.serviceSlow(ingress, 0xFFFF));
        assertSame(ingress, owner.serviceFast(ingress));
        var empty = owner.initList(ingress, 0);
        assertEquals(0xA501, empty.sectionWord());
        assertSame(ingress.residency(), empty.residency());
        assertArrayEquals(ingress.codeTable().copy(), empty.codeTable().copy());
        assertSame(empty, owner.prepare(empty));
    }

    // Catches accepting another owner's source/plan/table identity by structural equality alone.
    @Test void foreignOwnerRejectsEveryOperationBeforeDoingWork() {
        var a = owner(); var b = owner(); var foreign = dev(a, 0);
        assertNotEquals(foreign, dev(b, 0));
        assertThrows(IllegalArgumentException.class, () -> b.updateSection(foreign, 0));
        assertThrows(IllegalArgumentException.class, () -> b.loadSection(foreign, 0));
        assertThrows(IllegalArgumentException.class, () -> b.initList(foreign, 9));
        assertThrows(IllegalArgumentException.class, () -> b.appendList(foreign, 9));
        assertThrows(IllegalArgumentException.class, () -> b.prepare(foreign));
        assertThrows(IllegalArgumentException.class, () -> b.serviceSlow(foreign, 0));
        assertThrows(IllegalArgumentException.class, () -> b.serviceFast(foreign));
    }

    // Catches accepting invalid word widths or null ingress as guessed zero state.
    @Test void nullsAndWordWidthsFailWithoutCreatingNativeClaims() {
        var owner = owner(); var s = dev(owner, 0);
        assertThrows(NullPointerException.class, () -> owner.devState(null, s.codeTable(), 0));
        assertThrows(NullPointerException.class, () -> owner.devState(seed(), null, 0));
        assertThrows(NullPointerException.class, () -> owner.prepare(null));
        assertThrows(NullPointerException.class, () -> CdR11aSectionArt.fromAssets(null));
        assertThrows(IllegalArgumentException.class, () -> owner.devState(seed(), s.codeTable(), 65536));
        assertThrows(IllegalArgumentException.class, () -> owner.devState(seed(), CdAssetBytes.copyOf(new byte[511]), 0));
        assertThrows(IllegalArgumentException.class, () -> owner.updateSection(s, -1));
        assertThrows(IllegalArgumentException.class, () -> owner.loadSection(s, 65536));
        assertThrows(IllegalArgumentException.class, () -> owner.serviceSlow(s, 65536));
        assertThrows(IllegalArgumentException.class, () -> owner.initList(s, -1));
        assertThrows(IllegalArgumentException.class, () -> owner.appendList(s, 65536));
    }

    // Catches retained mutable input containers and mismatched source/range identity.
    @Test void constructorCopiesInputsAndRejectsAmbiguousOrMalformedDeclarations() {
        var sourceList = new ArrayList<>(programs());
        var listList = new ArrayList<>(lists());
        var sectionList = new ArrayList<>(sections());
        var owner = new CdR11aSectionArt(listList, sectionList, sourceList);
        sourceList.clear(); listList.clear(); sectionList.clear();
        assertEquals(50, owner.prepare(owner.initList(dev(owner, 0), 9)).remainingTilesWord());
        assertThrows(NullPointerException.class, () -> new CdR11aSectionArt(null, sections(), programs()));
        assertThrows(NullPointerException.class, () -> new CdR11aSectionArt(lists(), null, programs()));
        assertThrows(NullPointerException.class, () -> new CdR11aSectionArt(lists(), sections(), null));
        assertThrows(NullPointerException.class, () -> new CdR11aSectionArt.SourceProgram(null, run(1, false, 1)));
        assertThrows(NullPointerException.class, () -> new CdR11aSectionArt.SourceProgram(entry(ART_PATA_BATA, 0x7600), null));
        assertThrows(IllegalArgumentException.class, () -> new CdR11aSectionArt(List.of(list(0, List.of()), list(0, List.of())), sections(), programs()));
        assertThrows(IllegalArgumentException.class, () -> new CdR11aSectionArt(lists(), List.of(), programs()));
        assertThrows(IllegalArgumentException.class, () -> new CdR11aSectionArt(lists(), sections(), List.of(programs().get(0), programs().get(0))));
        var wrong = new CdR11aLoadPlan.PlcEntry(ART_PATA_BATA, PROFILE.range(ART_ANTON), 0x7600);
        assertThrows(IllegalArgumentException.class, () -> new CdR11aSectionArt(lists(), sections(),
                List.of(new CdR11aSectionArt.SourceProgram(wrong, run(1, false, 1)))));
    }

    private static CdR11aSectionArt owner() { return new CdR11aSectionArt(lists(), sections(), programs()); }
    private static CdR11aSectionArt single(boolean xor, int count, int destination) {
        return declared(run(count, xor, 0xA), destination);
    }
    private static CdR11aSectionArt declared(CdAssetBytes input, int destination) {
        return new CdR11aSectionArt(List.of(list(PATA_ONLY, List.of(entry(ART_PATA_BATA, destination)))),
                sections(), List.of(new CdR11aSectionArt.SourceProgram(entry(ART_PATA_BATA, 0x7600), input)));
    }
    private static List<CdR11aSectionArt.SourceProgram> programs() {
        return List.of(program(ART_PATA_BATA, 0x7600, 50, 0xA), program(ART_ANTON, 0x8120, 22, 0xB),
                program(ART_TAMABBOH, 0x8500, 59, 0xC), program(ART_MOSQUI, 0x7400, 70, 0xD),
                program(ART_3D_PLANT, 0x8480, 28, 0xE), program(ART_3D_RAMP_BOOST, 0x8820, 63, 0xF));
    }
    private static CdR11aSectionArt.SourceProgram program(CdR11aAsset source, int dest, int count, int nibble) {
        return new CdR11aSectionArt.SourceProgram(entry(source, dest), run(count, false, nibble));
    }
    /** Literal native descriptor: nibble, eight-pixel run, one-bit code0, terminator; zero stream + real finite refill bytes. */
    private static CdAssetBytes run(int tiles, boolean xor, int nibble) {
        byte[] bytes = new byte[tiles + 8];
        int header = tiles | (xor ? 0x8000 : 0);
        bytes[0] = (byte) (header >>> 8); bytes[1] = (byte) header;
        bytes[2] = (byte) (0x80 | nibble); bytes[3] = 0x71; bytes[4] = 0; bytes[5] = (byte) 0xFF;
        return CdAssetBytes.copyOf(bytes);
    }
    private static CdR11aCameraArtResidency.State seed() {
        return CdR11aCameraArtResidency.devSeed(CdAssetBytes.copyOf(repeated(2240, 0x11)),
                CdAssetBytes.copyOf(repeated(896, 0x22)), CdAssetBytes.copyOf(repeated(2016, 0x33)));
    }
    private static CdR11aSectionArt.State dev(CdR11aSectionArt owner, int word) {
        return owner.devState(seed(), CdAssetBytes.copyOf(repeated(512, 0x4D)), word);
    }
    private static byte[] repeated(int size, int value) {
        byte[] bytes = new byte[size]; Arrays.fill(bytes, (byte) value); return bytes;
    }
    private static void assertTile(CdR11aSectionArt.State state, int id, byte[] expected) {
        assertArrayEquals(expected, CdR11aCameraArtResidency.readTile(state.residency(), id).copy());
    }
    private static CdR11aSectionArt.State drainOne(CdR11aSectionArt owner, CdR11aSectionArt.State state, boolean fast) {
        int guard = 0;
        while (state.remainingTilesWord() != 0) {
            assertTrue(guard++ < 100, "bounded fixture service must terminate");
            state = fast ? owner.serviceFast(state) : owner.serviceSlow(state, 0);
        }
        return state;
    }
    private static CdR11aSectionArt.State finishPlc9(CdR11aSectionArt owner, CdR11aSectionArt.State state) {
        for (int row = 0; row < 4; row++) {
            if (state.remainingTilesWord() == 0 && state.queue().headSource() == 0) return state;
            if (state.remainingTilesWord() == 0) state = owner.prepare(state);
            state = drainOne(owner, state, false);
        }
        fail("bounded fixture queue must drain"); return state;
    }
    private static CdR11aLoadPlan.PlcEntry entry(CdR11aAsset asset, int dest) {
        return new CdR11aLoadPlan.PlcEntry(asset, PROFILE.range(asset), dest);
    }
    private static CdR11aLoadPlan.PlcList list(int id, List<CdR11aLoadPlan.PlcEntry> rows) {
        // Declared fixture metadata, not an authenticated body location or media object.
        return new CdR11aLoadPlan.PlcList(id, new CdR11aProfile.FileRange(
                CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0, 2 + rows.size() * 6), rows);
    }
    private static List<CdR11aLoadPlan.SectionArt> sections() {
        return List.of(new CdR11aLoadPlan.SectionArt(0x680, 2, 8), new CdR11aLoadPlan.SectionArt(0xF80, 4, 9),
                new CdR11aLoadPlan.SectionArt(0x1980, 5, 10), new CdR11aLoadPlan.SectionArt(0x1F80, 6, 11),
                new CdR11aLoadPlan.SectionArt(0xFFFF, 7, 12));
    }
    private static List<CdR11aLoadPlan.PlcList> lists() {
        var b = entry(ART_BOULDER, 0x6E80); var a = entry(ART_ANIMALS, 0x7100);
        var m = entry(ART_MOSQUI, 0x7400); var p = entry(ART_PATA_BATA, 0x7600);
        var t = entry(ART_ANTON, 0x8120); var l = entry(ART_3D_PLANT, 0x8480);
        var r = entry(ART_3D_RAMP_BOOST, 0x8820); var g = entry(ART_GOAL_POST, 0x9EE0);
        var h = entry(ART_TAMABBOH, 0x8500); var o = entry(ART_SONIC_HOLE, 0x7400);
        var w = entry(ART_TUNNEL_WATERFALL, 0x7C80); var d = entry(ART_SPINNING_DISC, 0x8120);
        var shadow = entry(ART_LOG_SHADOW, 0x8300); var relocated = entry(ART_ANTON, 0x6E00);
        var amy = entry(ART_AMY_ROSE, 0x6E00); var robot = entry(ART_ROBOT_GEN_WITHERED, 0x8120);
        return List.of(list(0, List.of()), list(2, List.of(b, a, m, t, l, r, g)),
                list(4, List.of(b, a, p, t, h, g)), list(5, List.of(b, a, o, p, w, d, shadow, h, g)),
                list(6, List.of(relocated, a, m, d, l, r, g)), list(7, List.of(amy, robot, l, r, g)),
                list(8, List.of(b, a, m, t, l, r)), list(9, List.of(p, t, h)),
                list(10, List.of(b, o, p, w, d, shadow, h)), list(11, List.of(relocated, a, m, d, l, r)),
                list(12, List.of(amy, robot)), list(MOSQUI_ONLY, List.of(m)), list(PATA_ONLY, List.of(p)),
                list(RELOCATED, List.of(entry(ART_PATA_BATA, 0x7400))),
                list(UNKNOWN_AFTER_PATA, List.of(p, b)));
    }
    // Root integration addition: actual retained USA sources through the complete native batch.
    @Test void optionalActualUsaFactoryCompletesAllThreePlc9SourcesAfterMediaCloseAndReplays() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for actual USA media");
        Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        CdR11aAssets assets;
        try (var programs = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            assets = CdR11aAssetLoader.load(programs, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        }
        var objects = assets.openingObjects();
        var sources = objects.sectionArtSources();
        assertEquals(12, sources.sources().size());
        assertEquals(List.of(ART_PATA_BATA, ART_ANTON, ART_TAMABBOH, ART_MOSQUI, ART_3D_PLANT,
                ART_3D_RAMP_BOOST, ART_BOULDER, ART_DIAGONAL_SPRING, ART_SPIKES, ART_CHECKPOINT, ART_PLATFORM, ART_ANIMALS),
                sources.sources().stream().map(source -> source.source()).toList());
        var mosqui = sources.source(ART_MOSQUI).decode().bytes();
        var plant = sources.source(ART_3D_PLANT).decode().bytes();
        var ramp = sources.source(ART_3D_RAMP_BOOST).decode().bytes();
        var resident = CdR11aCameraArtResidency.devSeed(mosqui, plant, ramp);
        var owner = CdR11aSectionArt.fromAssets(assets);
        var initial = owner.devState(resident, CdAssetBytes.copyOf(new byte[512]), 1);
        var queued = owner.updateSection(initial, 0x680);
        assertEquals(0x0201, queued.sectionWord());
        assertEquals(resident, queued.residency());
        assertArrayEquals(new byte[]{0,2,0,0x23,0x73,0x78,0x76,0,0,0x23,0x6E,0x46,(byte)0x81,0x20,0,0x23,0x79,0x50,(byte)0x85,0},
                concatCountAndQueue(queued));
        var cuts = new ArrayList<CdR11aSectionArt.State>();
        var state = queued;
        for (int call = 1; call <= 45; call++) {
            state = owner.prepare(state); // Separate CPU cut; never spends prior service budget on a next head.
            var saved = state.savedDecoder();
            state = owner.serviceSlow(state, 0);
            cuts.add(state);
            if (call == 16) { assertEquals(2, state.remainingTilesWord()); assertEquals(0x7C00, state.queue().headDestination()); }
            if (call == 17) { assertEquals(0, state.remainingTilesWord()); assertEquals(2, state.processBudgetWord()); assertEquals(0x236E46L, state.queue().headSource()); }
            if (call == 25) { assertEquals(0, state.remainingTilesWord()); assertEquals(0x237950L, state.queue().headSource()); }
            if (call == 45) { assertEquals(saved, state.savedDecoder()); assertEquals(2, state.processBudgetWord()); }
        }
        assertEquals(0, state.queue().headSource()); assertEquals(0, state.remainingTilesWord());
        assertEquals(0x237DC7L, state.decoder().orElseThrow().sourcePointer());
        byte[] alias = mosqui.copy();
        System.arraycopy(sources.source(ART_PATA_BATA).decode().bytes().copy(), 0, alias, 0x200, 1600);
        byte[] combined = new byte[0xB80];
        System.arraycopy(plant.copy(), 0, combined, 0, 896);
        System.arraycopy(ramp.copy(), 0, combined, 0x3A0, 2016);
        System.arraycopy(sources.source(ART_TAMABBOH).decode().bytes().copy(), 0, combined, 0x80, 1888);
        for (int tile = 0; tile < 70; tile++) assertArrayEquals(Arrays.copyOfRange(alias,tile*32,(tile+1)*32),
                CdR11aCameraArtResidency.readTile(state.residency(),0x3A0+tile).copy());
        for (int tile = 0; tile < 22; tile++) assertArrayEquals(sources.source(ART_ANTON).decode().bytes().slice(tile*32,32).copy(),
                CdR11aCameraArtResidency.readTile(state.residency(),0x409+tile).copy());
        for (int tile = 0; tile < 92; tile++) assertArrayEquals(Arrays.copyOfRange(combined,tile*32,(tile+1)*32),
                CdR11aCameraArtResidency.readTile(state.residency(),0x424+tile).copy());
        var replay = queued;
        for (var expected : cuts) { replay = owner.serviceSlow(owner.prepare(replay),0); assertEquals(expected,replay); }
        // Captured partial states are immutable; resuming one repeats only its remaining selected calls.
        replay = cuts.get(15);
        for (int index=16;index<cuts.size();index++) { replay=owner.serviceSlow(owner.prepare(replay),0);assertEquals(cuts.get(index),replay); }
        assertEquals(resident, initial.residency()); assertEquals(1,initial.sectionWord());
    }
    private static byte[] concatCountAndQueue(CdR11aSectionArt.State state) {
        byte[] bytes = new byte[20]; bytes[1]=2; System.arraycopy(state.queue().bytes().copy(),0,bytes,2,18); return bytes;
    }


    // BEGIN COLLAPSE PLATFORM PICTURE R1
    @Test void actualEleventhPlatformSourceKeepsCanonicalRowAndDistinctFiniteConsumers() throws Exception {
        var assets=collapseActualAssets();var bank=assets.openingObjects().sectionArtSources();assertEquals(12,bank.sources().size());
        var source=bank.sources().get(10);assertSame(source,bank.source(ART_PLATFORM));assertEquals(ART_PLATFORM,source.source());
        assertEquals(1,source.canonicalPlcId());assertEquals(6,source.canonicalRowIndex());assertEquals(0x97C0,source.canonicalPlc().vramAddress());
        assertEquals(57,source.tileCount());assertEquals(0x8039,source.readerBytes().unsignedWord(0));
        assertEquals(452,source.canonicalPlc().sourceRange().length());assertEquals(452,source.decode().readerInputRange().length());
        assertEquals(452,source.decode().consumedInputBytes());assertEquals(453,source.readerInputRange().length());assertEquals(453,source.readerBytes().length());
        assertEquals(0x80,source.readerBytes().unsignedByte(452));assertEquals(0x2362FE,source.canonicalPlc().sourceRange().nativeAddress());
        assertEquals(source.canonicalPlc(),assets.loadPlan().plcs().get(1).entries().get(6));assertSame(source,bank.source(source.canonicalPlc()));
        var decoded=com.openggf.game.soniccd.level.CdNemesisAsset.decode(source.readerBytes().slice(0,452).copy(),1824,1824);
        assertEquals(452,decoded.consumedInputBytes());assertArrayEquals(source.decode().bytes().copy(),decoded.bytes().copy());
        assertThrows(java.io.IOException.class,()->com.openggf.game.soniccd.level.CdNemesisAsset.decode(source.readerBytes().slice(0,451).copy(),1824,1824));
        var prepared=CdNemesisPlcDecoder.prepare(source.readerBytes(),0x2362FE,CdAssetBytes.copyOf(new byte[512]));
        assertEquals(64,prepared.inputOffset());assertEquals(57,prepared.remainingTiles());assertTrue(prepared.xorMode());
        var current=prepared;
        for(int tile=0;tile<57;tile++) {var result=CdNemesisPlcDecoder.decodeTile(current);assertArrayEquals(source.decode().bytes().slice(tile*32,32).copy(),result.bytes().copy());current=result.state();}
        assertEquals(453,current.inputOffset());assertEquals(13,current.shiftWord());assertEquals(0,current.remainingTiles());
        assertEquals(57,prepared.remainingTiles());assertEquals(64,prepared.inputOffset());
        var shortState=CdNemesisPlcDecoder.prepare(source.readerBytes().slice(0,452),0x2362FE,CdAssetBytes.copyOf(new byte[512]));
        for(int tile=0;tile<56;tile++)shortState=CdNemesisPlcDecoder.decodeTile(shortState).state();
        var lastBoundary=shortState;var fault=assertThrows(CdNemesisPlcDecoder.DecodeException.class,()->CdNemesisPlcDecoder.decodeTile(lastBoundary));
        assertEquals(CdNemesisPlcDecoder.Reason.SOURCE_EXHAUSTED,fault.reason());assertEquals(452,fault.diagnostic().inputOffset());
        assertEquals(1,lastBoundary.remainingTiles());assertEquals(57,prepared.remainingTiles());
    }

    @Test void actualPlatformStandaloneCanonicalRowServicesThreeAndEighteenWithImmutableReplayAndFinalSavedRam() throws Exception {
        var assets=collapseActualAssets();var source=assets.openingObjects().sectionArtSources().source(ART_PLATFORM);var std=assets.loadPlan().plcs().get(1);
        // Constructed one-row queue from REAL canonical metadata: component ingress, NOT full native Std history.
        var owner=new CdR11aSectionArt(List.of(new CdR11aLoadPlan.PlcList(1,std.bodyRange(),List.of(source.canonicalPlc()))),
                assets.loadPlan().sections(),List.of(new CdR11aSectionArt.SourceProgram(source.canonicalPlc(),source.readerBytes())));
        var resident=collapseResident(assets);var initial=owner.devState(resident,CdAssetBytes.copyOf(new byte[512]),0);
        var queued=owner.initList(initial,1);assertEquals(0x2362FEL,queued.queue().headSource());assertEquals(0x97C0,queued.queue().headDestination());
        var ready=owner.prepare(queued);assertEquals(57,ready.remainingTilesWord());assertEquals(resident,ready.residency());
        for(boolean fast:new boolean[]{false,true}) {
            var state=ready;var cuts=new ArrayList<CdR11aSectionArt.State>();cuts.add(ready);int calls=fast?4:19;
            for(int call=1;call<=calls;call++) {
                var before=state;state=fast?owner.serviceFast(state):owner.serviceSlow(state,0);cuts.add(state);
                int count=Math.min(57,call*(fast?18:3));assertEquals(57-count,state.remainingTilesWord());
                for(int tile=0;tile<count;tile++)assertArrayEquals(source.decode().bytes().slice(tile*32,32).copy(),CdR11aCameraArtResidency.readTile(state.residency(),0x4BE+tile).copy());
                if(count<57) {
                    int next=0x4BE+count;var prefix=state;
                    var unknown=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(prefix.residency(),next));
                    assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,unknown.boundary());
                    assertEquals(0x97C0+count*32,state.queue().headDestination());assertEquals(0,state.processBudgetWord());
                } else {assertEquals(before.savedDecoder(),state.savedDecoder());assertEquals(fast?16:1,state.processBudgetWord());}
            }
            assertEquals(0,state.queue().headSource());assertEquals(453,state.decoder().orElseThrow().inputOffset());
            for(int cut=0;cut<cuts.size();cut++) {var replay=cuts.get(cut);for(int remaining=cut;remaining<calls;remaining++)replay=fast?owner.serviceFast(replay):owner.serviceSlow(replay,0);assertEquals(state,replay);}
            assertEquals(57,ready.remainingTilesWord());assertEquals(resident,ready.residency());assertEquals(resident,initial.residency());
        }
        var shortOwner=new CdR11aSectionArt(List.of(new CdR11aLoadPlan.PlcList(1,std.bodyRange(),List.of(source.canonicalPlc()))),assets.loadPlan().sections(),
                List.of(new CdR11aSectionArt.SourceProgram(source.canonicalPlc(),source.readerBytes().slice(0,452))));
        var partial=shortOwner.prepare(shortOwner.initList(shortOwner.devState(resident,CdAssetBytes.copyOf(new byte[512]),0),1));
        for(int call=0;call<3;call++)partial=shortOwner.serviceFast(partial);
        var prefix54=partial;var fault=assertThrows(CdR11aSectionArt.SectionArtException.class,()->shortOwner.serviceFast(prefix54));
        assertEquals(DECODE_TILE,fault.reason());assertEquals(1,fault.state().remainingTilesWord());assertEquals(3,prefix54.remainingTilesWord());
        for(int tile=0;tile<56;tile++)assertArrayEquals(source.decode().bytes().slice(tile*32,32).copy(),CdR11aCameraArtResidency.readTile(fault.state().residency(),0x4BE+tile).copy());
        assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(fault.state().residency(),0x4F6));
    }

    @Test void actualFullStdRetainsEarlierUnsupportedProducerStopBeforePlatform() throws Exception {
        var assets=collapseActualAssets();var owner=CdR11aSectionArt.fromAssets(assets);var std=assets.loadPlan().plcs().get(1);
        assertEquals(14,std.entries().size());assertEquals(ART_TUNNEL_DOOR,std.entries().get(1).source());
        var initial=owner.devState(collapseResident(assets),CdAssetBytes.copyOf(new byte[512]),0);
        var ready=owner.prepare(owner.initList(initial,1));assertEquals(8,ready.remainingTilesWord());
        var after=owner.serviceSlow(owner.serviceSlow(owner.serviceSlow(ready,0),0),0);assertEquals(0,after.remainingTilesWord());
        assertEquals(Integer.toUnsignedLong(std.entries().get(1).sourceRange().nativeAddress()),after.queue().headSource());
        var fault=assertThrows(CdR11aSectionArt.SectionArtException.class,()->owner.prepare(after));assertEquals(UNKNOWN_SOURCE,fault.reason());
        assertEquals(after.residency(),fault.state().residency());
        for(int tile=0;tile<57;tile++){int selected=0x4BE+tile;assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(after.residency(),selected));}
    }

    private static CdR11aCameraArtResidency.State collapseResident(CdR11aAssets assets) throws Exception {
        var o=assets.openingObjects();return CdR11aCameraArtResidency.devSeed(o.initialArtBank().binding(ART_MOSQUI).bytes(),
                o.frontierArtBank().binding(ART_3D_PLANT).bytes(),o.frontierArtBank().binding(ART_3D_RAMP_BOOST).bytes());
    }

    private static CdR11aAssets collapseActualAssets() throws Exception {
        String root=System.getProperty("soniccd.media.root");Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Set soniccd.media.root for retained actual USA assets");
        Path cue;try(var files=Files.list(Path.of(root).resolve("USA"))) {var cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {return CdR11aAssetLoader.load(programs,CdReleaseVariant.USA,CdStageKey.parse("R11A"));}
    }
    // END COLLAPSE PLATFORM PICTURE R1
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
