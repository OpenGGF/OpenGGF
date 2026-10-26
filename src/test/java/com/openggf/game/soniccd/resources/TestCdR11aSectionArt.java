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
        assertEquals(13, sources.sources().size());
        assertEquals(List.of(ART_PATA_BATA, ART_ANTON, ART_TAMABBOH, ART_MOSQUI, ART_3D_PLANT,
                ART_3D_RAMP_BOOST, ART_BOULDER, ART_DIAGONAL_SPRING, ART_SPIKES, ART_CHECKPOINT, ART_PLATFORM, ART_ANIMALS, ART_GOAL_POST),
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
        var assets=collapseActualAssets();var bank=assets.openingObjects().sectionArtSources();assertEquals(13,bank.sources().size());
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

    // BEGIN GOALPOST SECTION PROOF R1 -- ALL ORACLES UNRUN.
    // Actual bytes enter only through collapseActualAssets(), the existing opt-in USA loader.
    // All devState/constructed queues below are TEST ingress, never cold/restart/controller proof.
    // N 1ecc5f3a: PLC_Cam2_Full (Data (Act 1 Present).asm:102-115);
    // ProcessPLCs / DecompPLCSlow / DecompPLC_Pop (Functions (General).asm:664-785).
    @Test void goalPost13ActualSourceCacheAndExactAliasInventory() throws Exception {
        var assets = collapseActualAssets();
        var objects = assets.openingObjects();
        var bank = objects.sectionArtSources();
        var expected = List.of(ART_PATA_BATA, ART_ANTON, ART_TAMABBOH, ART_MOSQUI,
                ART_3D_PLANT, ART_3D_RAMP_BOOST, ART_BOULDER, ART_DIAGONAL_SPRING,
                ART_SPIKES, ART_CHECKPOINT, ART_PLATFORM, ART_ANIMALS, ART_GOAL_POST);
        assertEquals(13, bank.sources().size());
        assertEquals(expected, bank.sources().stream().map(source -> source.source()).toList());
        assertSame(bank, objects.sectionArtSources());
        int[] tileCounts = {50, 22, 59, 70, 28, 63, 19, 46, 8, 12, 57, 20, 24};
        int[] javaLengths = {714, 484, 1142, 846, 393, 677, 270, 779, 86, 211, 452, 445, 448};
        int[] nativeLengths = {715, 485, 1143, 847, 393, 678, 271, 780, 87, 212, 453, 446, 449};
        int[] canonicalIds = {9, 9, 9, 2, 2, 2, 2, 1, 1, 0, 1, 2, 2};
        int[] canonicalRows = {0, 1, 2, 2, 4, 5, 0, 5, 0, 1, 6, 1, 6};
        int[] destinations = {0x7600, 0x8120, 0x8500, 0x7400, 0x8480, 0x8820, 0x6E80,
                0x9200, 0x6400, 0xD960, 0x97C0, 0x7100, 0x9EE0};
        for (int index = 0; index < expected.size(); index++) {
            var source = bank.sources().get(index);
            assertSame(source, bank.source(expected.get(index)));
            assertEquals(tileCounts[index], source.tileCount());
            assertEquals(tileCounts[index] * 32, source.decode().bytes().length());
            assertEquals(javaLengths[index], source.decode().readerInputRange().length());
            assertEquals(javaLengths[index], source.decode().consumedInputBytes());
            assertEquals(nativeLengths[index], source.readerInputRange().length());
            assertEquals(nativeLengths[index], source.readerBytes().length());
            assertEquals(canonicalIds[index], source.canonicalPlcId());
            assertEquals(canonicalRows[index], source.canonicalRowIndex());
            assertEquals(destinations[index], source.canonicalPlc().vramAddress());
            assertEquals(assets.loadPlan().plcs().get(canonicalIds[index]).entries().get(canonicalRows[index]), source.canonicalPlc());
            assertSame(source, objects.sectionArtSources().sources().get(index));
            assertSame(source.decode(), objects.sectionArtSources().source(expected.get(index)).decode());
            assertSame(source.readerBytes(), objects.sectionArtSources().source(expected.get(index)).readerBytes());
        }
        // Exact inverse of the complete bank list: no implied admission of Signpost or another blob.
        for (var asset : CdR11aAsset.values()) {
            if (expected.contains(asset)) assertSame(bank.sources().get(expected.indexOf(asset)), bank.source(asset));
            else assertThrows(UnsupportedOperationException.class, () -> bank.source(asset));
        }
        assertEquals(ART_ANIMALS, bank.sources().get(11).source());
        var goal = bank.sources().get(12);
        assertEquals(ART_GOAL_POST, goal.source());
        assertEquals(2, goal.canonicalPlcId()); assertEquals(6, goal.canonicalRowIndex());
        assertEquals(assets.loadPlan().plcs().get(2).entries().get(6), goal.canonicalPlc());
        assertEquals(0x9EE0, goal.canonicalPlc().vramAddress());
        var view = assets.view(ART_GOAL_POST);
        assertEquals(view.range(), goal.canonicalPlc().sourceRange());
        assertEquals(view.range(), goal.decode().sourceBlobRange());
        assertEquals(448, view.range().length()); assertEquals(0x320DA, view.range().fileOffset());
        assertEquals(0x2320DA, view.range().nativeAddress());
        assertEquals(ART_GOAL_POST, goal.decode().asset());
        assertEquals(view.range(), goal.decode().readerInputRange());
        assertEquals(448, goal.decode().consumedInputBytes());
        assertEquals(449, goal.readerInputRange().length()); assertEquals(449, goal.readerBytes().length());
        assertEquals(view.range().file(), goal.readerInputRange().file());
        assertEquals(view.range().domain(), goal.readerInputRange().domain());
        assertEquals(0x320DA, goal.readerInputRange().fileOffset());
        assertEquals(0x3229B, goal.readerInputRange().endExclusive());
        assertEquals(0x80, goal.readerBytes().unsignedByte(448)); // Actual adjacent Signpost byte at $3229A.
        assertEquals(0x8018, goal.readerBytes().unsignedWord(0)); assertEquals(24, goal.tileCount());
        assertEquals(768, goal.decode().bytes().length());
        assertArrayEquals(view.bytes().copy(), goal.readerBytes().slice(0, 448).copy());
        assertEquals("24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f",
                goalPost13Sha256(view.bytes()));
        assertEquals("1ba1c7e9a34a071c8786cc2eb94eaf2adf0e58b480ef8d62f6501bb7f3006250",
                goalPost13Sha256(goal.readerBytes()));
        assertEquals("68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a",
                goalPost13Sha256(goal.decode().bytes()));
        // Exact inverse over all 19 actual PLC slots, including aliases and incremental absences.
        int[] goalRows = {-1, -1, 6, -1, 5, 8, 6, 4, -1, -1, -1, -1, -1, 6, 6, 6, -1, 6, -1};
        int[] bodyOffsets = {0xFC50, 0xFC7C, 0xFCA2, 0xFCDA, 0xFD06};
        int[] fullIds = {2, 4, 5, 6, 7};
        int[] fullSizes = {7, 6, 9, 7, 5};
        assertEquals(19, assets.loadPlan().plcs().size());
        for (int index = 0; index < fullIds.length; index++) {
            var plc = assets.loadPlan().plcs().get(fullIds[index]);
            assertEquals(bodyOffsets[index], plc.bodyRange().fileOffset());
            assertEquals(fullSizes[index], plc.entries().size());
        }
        int occurrences = 0;
        for (int id = 0; id < goalRows.length; id++) {
            var plc = assets.loadPlan().plcs().get(id);
            for (int row = 0; row < plc.entries().size(); row++) {
                var entry = plc.entries().get(row);
                if (row == goalRows[id]) {
                    occurrences++;
                    assertEquals(ART_GOAL_POST, entry.source());
                    assertEquals(view.range(), entry.sourceRange());
                    assertEquals(0x2320DA, entry.sourceRange().nativeAddress());
                    assertEquals(0x9EE0, entry.vramAddress()); assertSame(goal, bank.source(entry));
                } else assertNotEquals(ART_GOAL_POST, entry.source());
            }
        }
        assertEquals(9, occurrences);
        for (int alias : new int[]{13, 14, 15, 17}) {
            assertEquals(assets.loadPlan().plcs().get(2).bodyRange(), assets.loadPlan().plcs().get(alias).bodyRange());
            assertEquals(assets.loadPlan().plcs().get(2).entries(), assets.loadPlan().plcs().get(alias).entries());
        }
        var wrongRange = new CdR11aProfile.FileRange(view.range().file(), view.range().domain(), 0x320DA, 449);
        assertThrows(IllegalArgumentException.class,
                () -> bank.source(new CdR11aLoadPlan.PlcEntry(ART_GOAL_POST, wrongRange, 0x9EE0)));
        assertThrows(IllegalArgumentException.class,
                () -> bank.source(new CdR11aLoadPlan.PlcEntry(ART_GOAL_POST, PROFILE.range(ART_PLATFORM), 0x9EE0)));
    }

    @Test void goalPost13FiniteReadersRetainSixIncomingTableHoles() throws Exception {
        var assets = collapseActualAssets();
        var goal = assets.openingObjects().sectionArtSources().source(ART_GOAL_POST);
        var actual448 = assets.view(ART_GOAL_POST).bytes();
        var javaRead = com.openggf.game.soniccd.level.CdNemesisAsset.decode(actual448.copy(), 768, 768);
        assertEquals(448, javaRead.consumedInputBytes());
        assertArrayEquals(goal.decode().bytes().copy(), javaRead.bytes().copy());
        assertThrows(java.io.IOException.class,
                () -> com.openggf.game.soniccd.level.CdNemesisAsset.decode(actual448.slice(0, 447).copy(), 768, 768));
        var zero = CdNemesisPlcDecoder.prepare(goal.readerBytes(), 0x2320DA, CdAssetBytes.copyOf(new byte[512]));
        var supplied = CdNemesisPlcDecoder.prepare(goal.readerBytes(), 0x2320DA, CdAssetBytes.copyOf(repeated(512, 0xA5)));
        goalPost13AssertRegisters(zero, 88, 24, 0x12D7, 16, 0, 0, 0);
        goalPost13AssertRegisters(supplied, 88, 24, 0x12D7, 16, 0, 0, 0);
        assertEquals("e1160cda631357f98dabf0816437156f64051b379948e23e920a76e8ea32df9a",
                goalPost13Sha256(zero.codeTable()));
        int holes = 0;
        for (int offset = 0; offset < 512; offset += 2) {
            if (zero.codeTable().unsignedWord(offset) != supplied.codeTable().unsignedWord(offset)) {
                holes++;
                assertEquals(0, zero.codeTable().unsignedWord(offset));
                assertEquals(0xA5A5, supplied.codeTable().unsignedWord(offset));
            }
        }
        assertEquals(6, holes); // 250 written entries; the other six retain their actual incoming RAM bytes.
        for (int tile = 0; tile < 24; tile++) {
            var a = CdNemesisPlcDecoder.decodeTile(zero);
            var b = CdNemesisPlcDecoder.decodeTile(supplied);
            assertArrayEquals(goal.decode().bytes().slice(tile * 32, 32).copy(), a.bytes().copy());
            assertArrayEquals(a.bytes().copy(), b.bytes().copy());
            zero = a.state(); supplied = b.state();
        }
        goalPost13AssertRegisters(zero, 449, 0, 0xC880, 11, 0, 9, 0x11111111);
        goalPost13AssertRegisters(supplied, 449, 0, 0xC880, 11, 0, 9, 0x11111111);
        assertEquals(449, zero.sourceBytes().length()); // Finite view cannot read following $4C at $3229B.
    }

    @Test void goalPost13ActualFullPlc4PreparesOnlyThenSlowCutsAndNativePop() throws Exception {
        var assets = collapseActualAssets();
        var goal = assets.openingObjects().sectionArtSources().source(ART_GOAL_POST);
        var owner = CdR11aSectionArt.fromAssets(assets);
        var before = goalPost13ActualPrefix(owner, assets, collapseResident(assets));
        assertEquals(0x0200, before.sectionWord()); assertEquals(0x2320DAL, before.queue().headSource());
        assertEquals(0x9EE0, before.queue().headDestination()); assertEquals(0, before.remainingTilesWord());
        assertEquals(2, before.processBudgetWord()); // Actual preceding Tamabboh final two tiles.
        assertSame(before, owner.updateSection(before, 0xA17)); // Unchanged section cannot force canonical PLC2.
        var ready = owner.prepare(before);
        assertSame(before.residency(), ready.residency()); assertEquals(2, ready.processBudgetWord());
        assertEquals(0x9EE0, ready.queue().headDestination()); assertEquals(0x232132L, ready.queue().headSource());
        goalPost13AssertRegisters(ready.decoder().orElseThrow(), 88, 24, 0x12D7, 16, 0, 0, 0);
        goalPost13AssertSaved(ready.savedDecoder(), 0x12D7, 16, 0, 0, 0);
        // Identify untouched table words without guessing their indices; prepare each comparison seed once.
        var zeroTable = CdNemesisPlcDecoder.prepare(goal.readerBytes(), 0x2320DA, CdAssetBytes.copyOf(new byte[512])).codeTable();
        var a5Table = CdNemesisPlcDecoder.prepare(goal.readerBytes(), 0x2320DA, CdAssetBytes.copyOf(repeated(512, 0xA5))).codeTable();
        int holes = 0;
        for (int offset = 0; offset < 512; offset += 2) {
            if (zeroTable.unsignedWord(offset) != a5Table.unsignedWord(offset)) {
                holes++; assertEquals(before.codeTable().unsignedWord(offset), ready.codeTable().unsignedWord(offset));
            }
        }
        assertEquals(6, holes);
        goalPost13AssertPrefix(before.residency(), ready.residency(), goal.decode().bytes(), 0, 0x9EE0);
        var unknown = assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
                () -> CdR11aCameraArtResidency.readTile(ready.residency(), 0x4F7));
        assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN, unknown.boundary());
        int[] offsets = {124, 182, 236, 291, 349, 384, 399, 449};
        long[] pointers = {0x232156L, 0x232190L, 0x2321C6L, 0x2321FDL, 0x232237L, 0x23225AL, 0x232269L, 0x23229BL};
        int[] destinations = {0x9F40, 0x9FA0, 0xA000, 0xA060, 0xA0C0, 0xA120, 0xA180, 0xA1E0};
        int[] reads = {0x0FFB, 0x00C7, 0x0FC1, 0x0F44, 0xF7BB, 0x0049, 0x16BA, 0xC880};
        int[] shifts = {15, 16, 11, 13, 16, 13, 14, 11};
        int[] repeats = {0, 0, 0, 0, 0, 6, 0, 0};
        int[] pixels = {0, 0, 0, 0, 0, 0, 0, 9};
        int[] previous = {0, 0, 0xEEE5EBBE, 0xBFBEB6FB, 0xEE000000, 0, 0x98766789, 0x11111111};
        var state = ready;
        var cuts = new ArrayList<CdR11aSectionArt.State>(); cuts.add(ready);
        for (int call = 0; call < 8; call++) {
            var prior = state;
            state = owner.serviceSlow(state, 0);
            cuts.add(state);
            assertEquals(21 - call * 3, state.remainingTilesWord());
            goalPost13AssertRegisters(state.decoder().orElseThrow(), offsets[call], 21 - call * 3,
                    reads[call], shifts[call], repeats[call], pixels[call], previous[call]);
            assertEquals(pointers[call], state.decoder().orElseThrow().sourcePointer());
            if (call < 7) {
                assertEquals(pointers[call], state.queue().headSource());
                assertEquals(destinations[call], state.queue().headDestination()); assertEquals(0, state.processBudgetWord());
                goalPost13AssertSaved(state.savedDecoder(), reads[call], shifts[call], repeats[call], pixels[call], previous[call]);
            } else {
                assertEquals(1, state.processBudgetWord()); assertEquals(prior.savedDecoder(), state.savedDecoder());
                goalPost13AssertSaved(state.savedDecoder(), 0x16BA, 14, 0, 0, 0x98766789);
                assertArrayEquals(goalPost13ExpectedPop(prior.queue(), 0xA1E0), state.queue().bytes().copy());
                assertArrayEquals(prior.queue().bytes().slice(88, 8).copy(), state.queue().bytes().slice(88, 8).copy());
            }
        }
        assertEquals(0, state.queue().headSource()); assertSame(state, owner.serviceSlow(state, 0));
        assertSame(state, owner.serviceFast(state)); assertSame(state, owner.prepare(state));
        goalPost13AssertPrefix(ready.residency(), state.residency(), goal.decode().bytes(), 24, 0x9EE0);
        // State itself is this owner's immutable capture value. Reusing a cut performs no restore-time service.
        for (int cut = 0; cut < cuts.size(); cut++) {
            var replay = cuts.get(cut);
            for (int call = cut; call < 8; call++) replay = owner.serviceSlow(replay, 0);
            assertEquals(state, replay); assertEquals(state.hashCode(), replay.hashCode());
        }
        assertEquals(24, ready.remainingTilesWord()); assertEquals(0x232132L, ready.queue().headSource());
        assertSame(before.residency(), ready.residency());
    }

    @Test void goalPost13FastHighByteBranchesPreserveMasksAndReplayValues() throws Exception {
        var assets = collapseActualAssets();
        var goal = assets.openingObjects().sectionArtSources().source(ART_GOAL_POST);
        var owner = CdR11aSectionArt.fromAssets(assets);
        var before = goalPost13ActualPrefix(owner, assets, goalPost13Resident(assets));
        var ready = owner.prepare(before);
        var lowByteOnly = owner.serviceSlow(ready, 0x0001);
        assertEquals(21, lowByteOnly.remainingTilesWord()); assertEquals(0x9F40, lowByteOnly.queue().headDestination());
        var highByte = owner.serviceSlow(ready, 0x0100);
        assertEquals(6, highByte.remainingTilesWord()); assertEquals(0xA120, highByte.queue().headDestination());
        assertEquals(0, highByte.processBudgetWord()); assertEquals(owner.serviceFast(ready), highByte);
        goalPost13AssertRegisters(highByte.decoder().orElseThrow(), 384, 6, 0x0049, 13, 6, 0, 0);
        goalPost13AssertPrefix(ready.residency(), lowByteOnly.residency(), goal.decode().bytes(), 3, 0x9EE0);
        goalPost13AssertPrefix(ready.residency(), highByte.residency(), goal.decode().bytes(), 18, 0x9EE0);
        var done = owner.serviceFast(highByte);
        assertEquals(0, done.remainingTilesWord()); assertEquals(13, done.processBudgetWord());
        assertEquals(highByte.savedDecoder(), done.savedDecoder());
        goalPost13AssertSaved(done.savedDecoder(), 0x0049, 13, 6, 0, 0);
        goalPost13AssertRegisters(done.decoder().orElseThrow(), 449, 0, 0xC880, 11, 0, 9, 0x11111111);
        assertArrayEquals(goalPost13ExpectedPop(highByte.queue(), 0xA360), done.queue().bytes().copy());
        goalPost13AssertPrefix(ready.residency(), done.residency(), goal.decode().bytes(), 24, 0x9EE0);
        // The all-region byte/mask comparison includes Platform57 at $97C0 and trailing17 at $A1E0.
        assertTile(ready, 0x50E, repeated(32, 0x6D));
        assertEquals(24, ready.remainingTilesWord()); assertEquals(0x9EE0, ready.queue().headDestination());
        for (var cut : List.of(ready, highByte)) assertEquals(done, drainOne(owner, cut, true));
        assertEquals(drainOne(owner, ready, false), drainOne(owner, lowByteOnly, false));
        assertThrows(IllegalArgumentException.class, () -> CdR11aSectionArt.fromAssets(assets).prepare(ready));
    }

    @Test void goalPost13Finite448IsTerminalAfterTwentyThreeCommittedTiles() throws Exception {
        var assets = collapseActualAssets();
        var actual448 = assets.view(ART_GOAL_POST).bytes(); // Actual finite source, NOT an incorrect inventory range.
        var output = assets.openingObjects().sectionArtSources().source(ART_GOAL_POST).decode().bytes();
        assertEquals(448, actual448.length());
        // Declared support-negative only: one actual row with a deliberately short native reader.
        var owner = goalPost13DeclaredOwner(assets, actual448, 0x9EE0);
        var initial = owner.devState(goalPost13Resident(assets), CdAssetBytes.copyOf(new byte[512]), 0x0200);
        var ready = owner.prepare(owner.initList(initial, 4));
        var state = ready;
        for (int call = 0; call < 7; call++) state = owner.serviceSlow(state, 0);
        var prefix21 = state;
        assertEquals(3, prefix21.remainingTilesWord()); assertEquals(0x232269L, prefix21.queue().headSource());
        var fault = assertThrows(CdR11aSectionArt.SectionArtException.class, () -> owner.serviceSlow(prefix21, 0));
        assertEquals(DECODE_TILE, fault.reason()); assertEquals(0x23229AL, fault.sourcePointer());
        assertEquals(0xA1C0, fault.destinationWord());
        assertEquals("R11A section art DECODE_TILE at source $23229a, VRAM $a1c0", fault.getMessage());
        var failed = fault.state();
        assertTrue(failed.stopped()); assertEquals(1, failed.remainingTilesWord()); assertEquals(1, failed.processBudgetWord());
        assertEquals(0xA1E0, failed.queue().headDestination()); assertEquals(0x232269L, failed.queue().headSource());
        assertEquals(prefix21.savedDecoder(), failed.savedDecoder());
        goalPost13AssertSaved(failed.savedDecoder(), 0x16BA, 14, 0, 0, 0x98766789);
        assertEquals(437, failed.decoder().orElseThrow().inputOffset()); assertEquals(1, failed.decoder().orElseThrow().remainingTiles());
        var cause = assertInstanceOf(CdNemesisPlcDecoder.DecodeException.class, fault.getCause());
        assertEquals(CdNemesisPlcDecoder.Reason.SOURCE_EXHAUSTED, cause.reason());
        assertEquals("Reached byte read outside the finite source view [INLINE_VALUE_REFILL, input+448]", cause.getMessage());
        assertNull(cause.getCause());
        var diagnostic = cause.diagnostic();
        assertEquals("INLINE_VALUE_REFILL", diagnostic.phase()); assertEquals(448, diagnostic.inputOffset());
        assertEquals(0x23229AL, diagnostic.sourcePointer()); assertEquals(-1, diagnostic.tableOffset());
        assertEquals(0xC800, diagnostic.readWord()); assertEquals(11, diagnostic.shiftWord());
        assertEquals(0x0050, diagnostic.repeatWord()); assertEquals(9, diagnostic.pixelNibble());
        assertEquals(0x77888888, diagnostic.previousRow()); assertEquals(0x66, diagnostic.row());
        assertEquals(2, diagnostic.pixelsInRow()); assertEquals(28, diagnostic.emittedRows().length());
        assertEquals(failed.decoder().orElseThrow(), diagnostic.boundaryState());
        assertArrayEquals(output.slice(736, 28).copy(), diagnostic.emittedRows().copy());
        goalPost13AssertPrefix(ready.residency(), failed.residency(), output, 23, 0x9EE0); // 736 resident bytes, not 764.
        assertTile(failed, 0x50E, repeated(32, 0x6D)); // Seven diagnostic rows never replace the old final tile.
        assertTile(ready, 0x50E, repeated(32, 0x6D));
        assertEquals(3, prefix21.remainingTilesWord()); assertEquals(24, ready.remainingTilesWord());
        assertEquals(0xA180, prefix21.queue().headDestination());
        assertThrows(IllegalStateException.class, () -> owner.prepare(failed));
        assertThrows(IllegalStateException.class, () -> owner.serviceSlow(failed, 0));
        assertThrows(IllegalStateException.class, () -> owner.serviceFast(failed));
        assertThrows(IllegalStateException.class, () -> owner.initList(failed, 4));
        assertThrows(IllegalStateException.class, () -> owner.appendList(failed, 4));
        assertThrows(IllegalStateException.class, () -> owner.updateSection(failed, 0xA17));
        assertThrows(IllegalStateException.class, () -> owner.loadSection(failed, 0xA17));
        var foreign = goalPost13DeclaredOwner(assets, actual448, 0x9EE0);
        assertThrows(IllegalArgumentException.class, () -> foreign.prepare(failed));
        // No captureClosedCut/validateRestore method exists on SectionArt: controller rejection belongs to Root.
    }

    @Test void goalPost13Finite448FastFailureExposesA360FullPreadvance() throws Exception {
        var assets = collapseActualAssets();
        var owner = goalPost13DeclaredOwner(assets, assets.view(ART_GOAL_POST).bytes(), 0x9EE0);
        var ready = owner.prepare(owner.initList(owner.devState(goalPost13Resident(assets),
                CdAssetBytes.copyOf(new byte[512]), 0x0200), 4));
        var prefix18 = owner.serviceFast(ready);
        assertEquals(6, prefix18.remainingTilesWord()); assertEquals(0xA120, prefix18.queue().headDestination());
        var fault = assertThrows(CdR11aSectionArt.SectionArtException.class, () -> owner.serviceFast(prefix18));
        assertEquals(DECODE_TILE, fault.reason()); assertEquals(0xA1C0, fault.destinationWord());
        assertEquals(0x23229AL, fault.sourcePointer()); assertEquals(0xA360, fault.state().queue().headDestination());
        assertEquals(1, fault.state().remainingTilesWord()); assertEquals(13, fault.state().processBudgetWord());
        assertEquals(prefix18.savedDecoder(), fault.state().savedDecoder());
        var cause = assertInstanceOf(CdNemesisPlcDecoder.DecodeException.class, fault.getCause());
        assertEquals(CdNemesisPlcDecoder.Reason.SOURCE_EXHAUSTED, cause.reason());
        assertEquals("INLINE_VALUE_REFILL", cause.diagnostic().phase());
        goalPost13AssertPrefix(ready.residency(), fault.state().residency(),
                assets.openingObjects().sectionArtSources().source(ART_GOAL_POST).decode().bytes(), 23, 0x9EE0);
        assertTile(fault.state(), 0x50E, repeated(32, 0x6D));
    }

    @Test void goalPost13ActualSourceHonorsRelocatedTestQueueDestination() throws Exception {
        var assets = collapseActualAssets();
        var goal = assets.openingObjects().sectionArtSources().source(ART_GOAL_POST);
        // TEST ingress relocation changes only the queue destination; canonical PLC2 remains descriptive.
        var relocated = new CdR11aLoadPlan.PlcEntry(ART_GOAL_POST, goal.canonicalPlc().sourceRange(), 0x9000);
        var bank = assets.openingObjects().sectionArtSources();
        assertSame(bank.source(ART_GOAL_POST), bank.source(relocated));
        var owner = goalPost13DeclaredOwner(assets, goal.readerBytes(), 0x9000);
        var ingress = owner.devState(goalPost13Resident(assets), CdAssetBytes.copyOf(new byte[512]), 0x0200);
        var ready = owner.prepare(owner.initList(ingress, 4));
        var done = drainOne(owner, ready, true);
        assertEquals(0x9000, ready.queue().headDestination()); assertEquals(0x9EE0, goal.canonicalPlc().vramAddress());
        goalPost13AssertPrefix(ready.residency(), done.residency(), goal.decode().bytes(), 24, 0x9000);
        assertTile(done, 0x50E, repeated(32, 0x6D)); // Canonical final tile was never selected.
    }

    @Test void goalPost13DeclaredFullQueueRetainsNativePopTailAndWaitsForPrepare() throws Exception {
        var assets = collapseActualAssets();
        var goal = assets.openingObjects().sectionArtSources().source(ART_GOAL_POST);
        var actual = assets.loadPlan().plcs().get(4);
        var rows = new ArrayList<CdR11aLoadPlan.PlcEntry>();
        // TEST ingress: sixteen repeated actual row5 descriptors, NOT a claim about native PLC4's six-row body.
        for (int row = 0; row < 16; row++) rows.add(actual.entries().get(5));
        var owner = new CdR11aSectionArt(List.of(new CdR11aLoadPlan.PlcList(4, actual.bodyRange(), rows)),
                assets.loadPlan().sections(), List.of(new CdR11aSectionArt.SourceProgram(actual.entries().get(5), goal.readerBytes())));
        var ready = owner.prepare(owner.initList(owner.devState(collapseResident(assets), CdAssetBytes.copyOf(new byte[512]), 0x0200), 4));
        var last = ready;
        for (int call = 0; call < 7; call++) last = owner.serviceSlow(last, 0);
        var done = owner.serviceSlow(last, 0);
        assertArrayEquals(goalPost13ExpectedPop(last.queue(), 0xA1E0), done.queue().bytes().copy());
        assertArrayEquals(last.queue().bytes().slice(88, 8).copy(), done.queue().bytes().slice(88, 8).copy());
        assertEquals(0x2320DAL, done.queue().bytes().unsignedLong(90));
        assertEquals(0x9EE0, done.queue().bytes().unsignedWord(94));
        assertEquals(0x2320DAL, done.queue().headSource()); assertEquals(0x9EE0, done.queue().headDestination());
        assertEquals(0, done.remainingTilesWord()); assertEquals(1, done.processBudgetWord());
        assertEquals(0, done.decoder().orElseThrow().remainingTiles());
        assertSame(done, owner.serviceFast(done)); assertSame(done, owner.serviceSlow(done, 0));
        var nextPrepared = owner.prepare(done);
        assertEquals(24, nextPrepared.remainingTilesWord()); assertSame(done.residency(), nextPrepared.residency());
        assertEquals(1, nextPrepared.processBudgetWord());
    }

    private static String goalPost13Sha256(CdAssetBytes bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.copy()));
    }

    private static void goalPost13AssertRegisters(CdNemesisPlcDecoder.State state, int offset, int remaining,
                                                  int read, int shift, int repeat, int pixel, int previous) {
        assertEquals(0x8018, state.headerWord()); assertTrue(state.xorMode());
        assertEquals(0x2320DAL, state.nativeAddress()); assertEquals(offset, state.inputOffset());
        assertEquals(0x2320DAL + offset, state.sourcePointer()); assertEquals(remaining, state.remainingTiles());
        assertEquals(read, state.readWord()); assertEquals(shift, state.shiftWord());
        assertEquals(repeat, state.repeatWord()); assertEquals(pixel, state.pixelNibble());
        assertEquals(previous, state.previousRow());
    }

    private static void goalPost13AssertSaved(CdR11aSectionArt.SavedDecoder saved, int read, int shift,
                                             int repeat, int pixel, int previous) {
        assertEquals(CdR11aSectionArt.Writer.XOR, saved.writer()); assertEquals(read, saved.readWord());
        assertEquals(shift, saved.shiftWord()); assertEquals(repeat, saved.repeatWord());
        assertEquals(pixel, saved.pixelNibble()); assertEquals(previous, saved.previousRow());
    }

    private static CdR11aSectionArt.State goalPost13ActualPrefix(CdR11aSectionArt owner, CdR11aAssets assets,
                                                                CdR11aCameraArtResidency.State resident) {
        // Actual full PLC4 selected through the production owner; only initialization is declared TEST ingress.
        var actual = assets.loadPlan().plcs().get(4);
        assertEquals(6, actual.entries().size());
        assertEquals(List.of(ART_BOULDER, ART_ANIMALS, ART_PATA_BATA, ART_ANTON, ART_TAMABBOH, ART_GOAL_POST),
                actual.entries().stream().map(CdR11aLoadPlan.PlcEntry::source).toList());
        var state = owner.initList(owner.devState(resident, CdAssetBytes.copyOf(repeated(512, 0xA5)), 0x0200), 4);
        assertEquals(0xFC7C, actual.bodyRange().fileOffset()); assertEquals(38, actual.bodyRange().length());
        int[] nativePointers = {0x2364C2, 0x2381C0, 0x237378, 0x236E46, 0x237950, 0x2320DA};
        int[] nativeDestinations = {0x6E80, 0x7100, 0x7600, 0x8120, 0x8500, 0x9EE0};
        for (int row = 0; row < 6; row++) {
            assertEquals(nativePointers[row], actual.entries().get(row).sourceRange().nativeAddress());
            assertEquals(nativeDestinations[row], actual.entries().get(row).vramAddress());
            assertEquals(Integer.toUnsignedLong(nativePointers[row]), state.queue().bytes().unsignedLong(row * 6));
            assertEquals(nativeDestinations[row], state.queue().bytes().unsignedWord(row * 6 + 4));
        }
        int[] tiles = {19, 20, 50, 22, 59};
        for (int row = 0; row < 5; row++) {
            assertEquals(Integer.toUnsignedLong(actual.entries().get(row).sourceRange().nativeAddress()), state.queue().headSource());
            state = owner.prepare(state); assertEquals(tiles[row], state.remainingTilesWord());
            state = drainOne(owner, state, false);
            assertEquals(0, state.remainingTilesWord());
        }
        assertEquals(0x2320DAL, state.queue().headSource()); assertEquals(2, state.processBudgetWord());
        return state;
    }

    private static CdR11aCameraArtResidency.State goalPost13Resident(CdR11aAssets assets) throws Exception {
        // TEST ingress: actual Platform57 plus explicit old final/trailing sentinels, never a cold preload.
        var resident = collapseResident(assets);
        var platform = assets.openingObjects().sectionArtSources().source(ART_PLATFORM).decode().bytes();
        for (int tile = 0; tile < 57; tile++)
            resident = CdR11aCameraArtResidency.writeTile(resident, 0x97C0 + tile * 32, platform.slice(tile * 32, 32));
        resident = CdR11aCameraArtResidency.writeTile(resident, 0xA1C0, CdAssetBytes.copyOf(repeated(32, 0x6D)));
        for (int tile = 0; tile < 17; tile++)
            resident = CdR11aCameraArtResidency.writeTile(resident, 0xA1E0 + tile * 32, CdAssetBytes.copyOf(repeated(32, 0x70 + tile)));
        return resident;
    }

    private static CdR11aSectionArt goalPost13DeclaredOwner(CdR11aAssets assets, CdAssetBytes reader, int destination) {
        var actual = assets.loadPlan().plcs().get(4);
        var row = actual.entries().get(5);
        var selected = new CdR11aLoadPlan.PlcEntry(row.source(), row.sourceRange(), destination);
        // TEST ingress/support-negative or relocation: constructor declaration authenticates nothing.
        return new CdR11aSectionArt(List.of(new CdR11aLoadPlan.PlcList(4, actual.bodyRange(), List.of(selected))),
                assets.loadPlan().sections(), List.of(new CdR11aSectionArt.SourceProgram(row, reader)));
    }

    private static byte[] goalPost13ExpectedPop(CdR11aPlcBuffer.State before, int preadvancedDestination) {
        byte[] bytes = before.bytes().copy();
        bytes[4] = (byte) (preadvancedDestination >>> 8); bytes[5] = (byte) preadvancedDestination;
        System.arraycopy(bytes, 6, bytes, 0, 88); // Independent byte oracle; retain original bytes88..95.
        return bytes;
    }

    private static void goalPost13AssertPrefix(CdR11aCameraArtResidency.State before, CdR11aCameraArtResidency.State after,
                                               CdAssetBytes output, int completeTiles, int destination) {
        assertEquals(9, CdR11aCameraArtResidency.Region.values().length);
        int physicalTiles = 0;
        for (var region : CdR11aCameraArtResidency.Region.values()) {
            physicalTiles += region.tileCount();
            var old = before.region(region); var current = after.region(region);
            var oldMask = old.knownMask(); var currentMask = current.knownMask();
            for (int offset = 0; offset < region.byteLength(); offset++) {
                int address = region.destinationWord() + offset;
                if (address >= destination && address < destination + completeTiles * 32) {
                    assertEquals(1, currentMask.unsignedByte(offset));
                    assertEquals(output.unsignedByte(address - destination), current.knownByte(offset).orElseThrow());
                } else {
                    assertEquals(oldMask.unsignedByte(offset), currentMask.unsignedByte(offset));
                    assertEquals(old.knownByte(offset), current.knownByte(offset));
                }
            }
        }
        assertEquals(452, physicalTiles);
    }
    // END GOALPOST SECTION PROOF R1
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
