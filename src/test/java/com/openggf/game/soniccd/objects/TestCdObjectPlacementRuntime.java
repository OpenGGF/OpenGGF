package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.openggf.game.soniccd.objects.CdObjectPlacementRuntime.SavedBit.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Literal source-branch cases, authored first. No comparison rows or gameplay entry. ALL UNRUN. */
class TestCdObjectPlacementRuntime {
    @Test
    void equalXUsesCommonOrderAndPeriodSkipsStillAdvanceSavedIds() {
        var source = layout(row(0, 0, 0x90, 1, 0x20, 0), row(0xF0, 0x720, 0x90, 0x21, 0x40, 0),
                row(0xF0, 0x730, 0xBD, 0, 0x40, 0));
        var runtime = new CdObjectPlacementRuntime(source);
        runtime.spawnObjects(0, 1);
        assertSame(source, runtime.placements());
        assertTrue(runtime.sourceIdentity().isEmpty());
        assertEquals(0x10, runtime.pool().state(32).id());
        assertEquals(2, runtime.pool().state(32).savedFlagsId());
        assertEquals(0x3D, runtime.pool().state(33).id());
        assertEquals(3, runtime.pool().state(33).savedFlagsId());
        assertEquals(3, runtime.status().rightRecordIndex());
        assertEquals(4, runtime.status().rightSavedId());
        assertEquals(1, runtime.status().leftSavedId());
        assertEquals(0, runtime.savedFlag(1, 1));
        assertEquals(0x80, runtime.savedFlag(2, 1));
        assertEquals(0, runtime.pool().state(32).routine(), "No ring body executes here");
        assertEquals(0, runtime.pool().state(34).id(), "No ring children are expanded here");
    }

    @Test
    void spawnAbiWritesOnlyNativeFieldsAndUnflaggedDoesNotWriteSavedId() {
        var runtime = new CdObjectPlacementRuntime(layout(row(0x12, 0xFABC, 0x10, 0x22, 0x40, 0x89)));
        var pool = runtime.pool();
        var dirty = pool.state(32).moveLong(X_POSITION, 0x1111ABCD).moveLong(Y_POSITION, 0x2222FEDC)
                .moveLong(MAPPINGS, 0x20CAFE).moveByte(SAVED_FLAGS_ID, 99).moveByte(ROUTINE, 8)
                .moveByte(COLLISION_TYPE, 0x47).moveByte(PRIORITY, 3).moveWord(TILE, 0xA7AE)
                .moveLong(MOSQUI_ANIMATION_SCRIPT, 0x20B6D8).moveByte(RING_MEMBER, 7);
        pool.write(dirty);
        runtime.spawnObjects(0, 1);
        var expected = dirty.moveWord(X, 0x12).moveWord(Y, 0xABC).moveByte(SPRITE_FLAGS, 3)
                .moveByte(FLAGS, 3).moveByte(ID, 0x10).moveByte(SUBTYPE, 0x22).moveByte(SUBTYPE2, 0x89);
        assertEquals(expected, pool.state(32));
        assertEquals(99, pool.state(32).savedFlagsId());
        assertEquals(0xABCD, pool.state(32).word(X_FRACTION));
        assertEquals(0xFEDC, pool.state(32).word(Y_FRACTION));
    }

    @Test
    void packedYBitsTwelveAndThirteenDoNotBecomeFlips() {
        var runtime = new CdObjectPlacementRuntime(layout(row(0, 0x3001, 0x10, 0, 0x40, 0)));
        runtime.spawnObjects(0, 1);
        assertEquals(1, runtime.pool().state(32).centreY());
        assertEquals(0, runtime.pool().state(32).spriteFlags());
        assertEquals(0, runtime.pool().state(32).flags());
    }

    @Test
    void forwardRightEdgeIsStrictAndUnchangedChunkDoesNothing() {
        var runtime = new CdObjectPlacementRuntime(layout(row(0x27F), row(0x280), row(0x300)));
        runtime.spawnObjects(0, 1);
        assertEquals(1, runtime.status().rightRecordIndex());
        runtime.spawnObjects(0x7F, 1);
        assertEquals(1, runtime.status().rightRecordIndex());
        runtime.spawnObjects(0x80, 1);
        assertEquals(2, runtime.status().rightRecordIndex());
        assertEquals(0x280, runtime.pool().state(33).centreX());
        assertEquals(0, runtime.pool().state(34).id());
    }

    @Test
    void initializationSeeksUnfilteredRecordsAndDoesNotResetCollectionBank() {
        var runtime = new CdObjectPlacementRuntime(layout(row(0), row(0x7F), row(0x80), row(0xFF), row(0x100)));
        runtime.bsetSavedFlag(5, 1, MEMBER_2);
        runtime.spawnObjects(0x180, 1);
        assertEquals(2, runtime.status().routine());
        assertEquals(5, runtime.status().rightRecordIndex());
        assertEquals(4, runtime.status().leftRecordIndex());
        assertEquals(6, runtime.status().rightSavedId());
        assertEquals(5, runtime.status().leftSavedId());
        assertEquals(0x82, runtime.savedFlag(5, 1));
        assertEquals(0x100, runtime.pool().state(32).centreX());
        assertEquals(1, runtime.pool().capture().slots().stream().filter(s -> s.id() != 0).count());
        assertEquals(0, runtime.status().nullRightByteOffset());
        assertEquals(0, runtime.status().nullLeftByteOffset());
    }

    @Test
    void backwardLeftEdgeIsStrictAndConsumesInReverseOrder() {
        var runtime = new CdObjectPlacementRuntime(layout(row(0), row(1), row(0x7F), row(0x80), row(0x100)));
        runtime.spawnObjects(0, 1);
        runtime.spawnObjects(0x100, 1);
        runtime.pool().clearDynamicSlots();
        for (int id = 1; id <= 5; id++) { runtime.bclrSavedFlag(id, 1, LOADED); }
        runtime.spawnObjects(0x80, 1);
        assertEquals(0x7F, runtime.pool().state(32).centreX());
        assertEquals(3, runtime.pool().state(32).savedFlagsId());
        assertEquals(1, runtime.pool().state(33).centreX());
        assertEquals(2, runtime.pool().state(33).savedFlagsId());
        assertEquals(0, runtime.pool().state(34).id());
        assertEquals(1, runtime.status().leftRecordIndex());
        assertEquals(2, runtime.status().leftSavedId());
    }

    @Test
    void subtractionBorrowAtLeftEdgeDoesNotWrapIntoASpawn() {
        var runtime = new CdObjectPlacementRuntime(layout(row(1), row(0x300)));
        runtime.spawnObjects(0x80, 1);
        runtime.spawnObjects(0, 1);
        assertEquals(0, runtime.status().leftRecordIndex());
        assertEquals(1, runtime.status().leftSavedId());
        assertEquals(1, runtime.status().rightRecordIndex());
        assertEquals(0, runtime.status().previousChunkWord());
    }

    @Test
    void signedDirectionAcrossSevenFFFAndUnsignedForwardOverflowStayLiteral() {
        var runtime = new CdObjectPlacementRuntime(layout(row(0x7F00), row(0x8000), row(0x8200)));
        runtime.spawnObjects(0x7F80, 1);
        assertEquals(2, runtime.status().rightRecordIndex());
        runtime.spawnObjects(0x8000, 1);
        assertEquals(2, runtime.status().rightRecordIndex(), "Signed BGE selects backward at this crossing");
        assertEquals(0, runtime.status().leftRecordIndex());
        runtime.spawnObjects(0xFF80, 1);
        assertEquals(2, runtime.status().rightRecordIndex(), "FF80+0280 wraps to unsigned 0200");
        assertEquals(0, runtime.status().leftRecordIndex(), "0200-0300 borrows, so no left seek");
        assertEquals(0xFF80, runtime.status().previousChunkWord());
    }

    @Test
    void backwardRightSeekUsesSignedBgtAndRemovesEqualBoundaryFromCursor() {
        var runtime = new CdObjectPlacementRuntime(layout(row(0x27F), row(0x280), row(0x300), row(0x37F)));
        runtime.spawnObjects(0, 1);
        runtime.spawnObjects(0x100, 1);
        assertEquals(4, runtime.status().rightRecordIndex());
        runtime.spawnObjects(0x80, 1);
        assertEquals(2, runtime.status().rightRecordIndex(), "Prior X equal to right boundary is rewound too");
        assertEquals(3, runtime.status().rightSavedId());
    }

    @Test
    void fullPoolRewindsForwardSavedIdAndTentativeLoadedBitWithoutStationaryRetry() {
        var runtime = new CdObjectPlacementRuntime(layout(row(0xF0)));
        fill(runtime.pool());
        runtime.bsetSavedFlag(1, 1, MEMBER_1);
        runtime.bsetSavedFlag(1, 1, MEMBER_3);
        runtime.spawnObjects(0, 1);
        assertEquals(0, runtime.status().rightRecordIndex());
        assertEquals(1, runtime.status().rightSavedId());
        assertEquals(5, runtime.savedFlag(1, 1));
        assertEquals(0, runtime.status().previousChunkWord());
        runtime.pool().deleteObject(32);
        runtime.spawnObjects(0, 1);
        assertEquals(0, runtime.pool().state(32).id());
        assertEquals(0, runtime.status().rightRecordIndex());
        runtime.spawnObjects(0x80, 1);
        assertEquals(0x10, runtime.pool().state(32).id());
        assertEquals(1, runtime.pool().state(32).savedFlagsId());
        assertEquals(0x85, runtime.savedFlag(1, 1));
    }

    @Test
    void fullPoolRewindsBackwardRecordAndIdAndLoadedBit() {
        var runtime = new CdObjectPlacementRuntime(layout(row(1), row(0x7F), row(0x80), row(0x400)));
        runtime.spawnObjects(0, 1);
        runtime.spawnObjects(0x100, 1);
        runtime.bclrSavedFlag(2, 1, LOADED);
        runtime.bsetSavedFlag(2, 1, MEMBER_7);
        fill(runtime.pool());
        runtime.spawnObjects(0x80, 1);
        assertEquals(2, runtime.status().leftRecordIndex());
        assertEquals(3, runtime.status().leftSavedId());
        assertEquals(0x40, runtime.savedFlag(2, 1));
        runtime.pool().deleteObject(127);
        runtime.spawnObjects(0x80, 1);
        assertEquals(0, runtime.pool().state(127).id());
        runtime.spawnObjects(0x100, 1);
        runtime.spawnObjects(0x80, 1);
        assertEquals(0x7F, runtime.pool().state(127).centreX());
        assertEquals(2, runtime.pool().state(127).savedFlagsId());
        assertEquals(0xC0, runtime.savedFlag(2, 1));
    }

    @Test
    void periodSkipBeforeFailureRemainsConsumedAndFailureCaptureRetainsNoRetryState() {
        var runtime = new CdObjectPlacementRuntime(layout(row(0, 0, 0x90, 0, 0x20, 0), row(1)));
        fill(runtime.pool());
        runtime.spawnObjects(0, 1);
        assertEquals(1, runtime.status().rightRecordIndex());
        assertEquals(2, runtime.status().rightSavedId());
        assertEquals(0, runtime.savedFlag(2, 1));
        var failed = runtime.capture();
        runtime.pool().deleteObject(127);
        runtime.spawnObjects(0x80, 1);
        assertEquals(2, runtime.pool().state(127).savedFlagsId());
        runtime.restore(failed);
        assertEquals(failed.status(), runtime.status());
        assertEquals(failed.pool().slots(), runtime.pool().capture().slots());
        runtime.pool().deleteObject(127);
        runtime.spawnObjects(0, 1);
        assertEquals(0, runtime.pool().state(127).id());
        runtime.spawnObjects(0x80, 1);
        assertEquals(2, runtime.pool().state(127).savedFlagsId());
    }

    @Test
    void unflaggedAllocationFailureLeavesSavedHeaderAndBankAlone() {
        var runtime = new CdObjectPlacementRuntime(layout(row(0, 0, 0x10, 0, 0x40, 0)));
        runtime.bsetSavedFlag(0, 1, MEMBER_1);
        fill(runtime.pool());
        runtime.spawnObjects(0, 1);
        assertEquals(0, runtime.status().rightRecordIndex());
        assertEquals(1, runtime.status().rightSavedId());
        assertEquals(1, runtime.savedFlag(0, 1));
        runtime.pool().deleteObject(32);
        runtime.spawnObjects(0x80, 1);
        assertEquals(1, runtime.status().rightRecordIndex());
        assertEquals(1, runtime.status().rightSavedId());
        assertEquals(1, runtime.savedFlag(0, 1));
    }

    @Test
    void loadedSkipSucceedsAndCollectionBitsSurvivePoolClearWhileResetIsExplicit() {
        var runtime = new CdObjectPlacementRuntime(layout(row(0), row(1)));
        runtime.bsetSavedFlag(1, 1, LOADED);
        runtime.bsetSavedFlag(1, 1, MEMBER_2);
        runtime.bsetSavedFlag(2, 0, MEMBER_3);
        runtime.spawnObjects(0, 0x81);
        assertEquals(2, runtime.pool().state(32).savedFlagsId());
        assertEquals(2, runtime.status().rightRecordIndex());
        assertEquals(3, runtime.status().rightSavedId());
        runtime.pool().clearDynamicSlots();
        assertEquals(0x82, runtime.savedFlag(1, 1));
        assertEquals(4, runtime.savedFlag(2, 0));
        assertEquals(766, runtime.capture().savedFlags().length());
        runtime.resetSavedObjectFlags();
        assertArrayEquals(new byte[0x2FC], runtime.capture().savedFlags().slice(2, 0x2FC).copy());
        assertEquals(1, runtime.status().rightSavedId());
        assertEquals(1, runtime.status().leftSavedId());
        assertEquals(2, runtime.status().rightRecordIndex(), "Bank reset is not cursor reinit");
    }

    @Test
    void allThreePeriodBitsAndTimeZoneHighBitUseNativeSelection() {
        for (int period = 0; period < 3; period++) {
            var runtime = new CdObjectPlacementRuntime(layout(row(0, 0, 0x90, 0, 0x20, 0),
                    row(1, 0, 0x90, 0, 0x40, 0), row(2, 0, 0x90, 0, 0x80, 0)));
            runtime.spawnObjects(0, 0x80 | period);
            assertEquals(period + 1, runtime.pool().state(32).savedFlagsId());
            assertEquals(1, runtime.pool().capture().slots().stream().filter(s -> s.id() != 0).count());
            assertEquals(4, runtime.status().rightSavedId());
        }
    }

    @Test
    void snapshotRestoresSinglePoolBankAndAllCursorsThenReplaysExactly() {
        var runtime = new CdObjectPlacementRuntime(layout(row(0), row(0x280), row(0x300)));
        runtime.spawnObjects(0, 1);
        var before = runtime.capture();
        runtime.spawnObjects(0x100, 1);
        var after = runtime.capture();
        runtime.pool().objNull(32);
        runtime.bsetSavedFlag(1, 2, MEMBER_5);
        byte[] detached = before.savedFlags().copy();
        detached[0] = 0;
        assertEquals(2, before.savedFlags().unsignedByte(0));
        assertThrows(UnsupportedOperationException.class, () -> before.pool().slots().clear());
        runtime.validateRestore(before);
        runtime.restore(before);
        assertEquals(before.status(), runtime.status());
        assertEquals(before.pool().slots(), runtime.pool().capture().slots());
        runtime.spawnObjects(0x100, 1);
        assertEquals(after.status(), runtime.status());
        assertArrayEquals(after.savedFlags().copy(), runtime.capture().savedFlags().copy());
        assertEquals(after.pool().slots(), runtime.pool().capture().slots());
    }

    @Test
    void independentOwnerEvenWithSameSourceRejectsRestoreBeforeAnyMutation() {
        var source = layout(row(0));
        var runtime = new CdObjectPlacementRuntime(source);
        runtime.spawnObjects(0, 1);
        var before = runtime.capture();
        var sameSourceForeignOwner = new CdObjectPlacementRuntime(source);
        var differentSource = new CdObjectPlacementRuntime(layout(row(1)));
        assertThrows(IllegalArgumentException.class, () -> runtime.restore(sameSourceForeignOwner.capture()));
        assertThrows(IllegalArgumentException.class, () -> runtime.restore(differentSource.capture()));
        assertEquals(before.status(), runtime.status());
        assertEquals(before.pool().slots(), runtime.pool().capture().slots());
        assertArrayEquals(before.savedFlags().copy(), runtime.capture().savedFlags().copy());
    }

    @Test
    void boundsAreExplicitAndARecordsOnlyDecoderDoesNotInventBackwardOrNullLists() {
        var runtime = new CdObjectPlacementRuntime(layout(row(0)));
        var before = runtime.capture();
        assertThrows(IllegalArgumentException.class, () -> runtime.spawnObjects(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> runtime.spawnObjects(0, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> runtime.savedFlag(254, 2));
        assertThrows(IllegalArgumentException.class, () -> runtime.savedFlag(256, 1));
        assertEquals(before.status(), runtime.status());
        assertThrows(IllegalArgumentException.class, () -> new CdObjectPlacementRuntime(
                CdObjectPlacements.decodeRecords(new byte[]{-1, -1, 0, 0, 0, 0, 0, 0})));
        var tooManySaved = IntStream.range(0, 255).mapToObj(i -> row(i)).toArray(int[][]::new);
        assertThrows(IndexOutOfBoundsException.class, () -> new CdObjectPlacementRuntime(layout(tooManySaved)));
    }

    // U5-D restart ingress, written before production (pin 1ecc5f3a). savedObjFlags is rs.b 2+OBJFLAGSCNT with
    // OBJFLAGSCNT $2FC (Main CPU Variables.inc:47-48). The carried bank is checked before the asset gate.
    @Test
    void carriedBankIngressRejectsNullAndEveryNonNativeLengthBeforeTheAssetGate() {
        assertEquals(0x2FC, CdObjectPlacementRuntime.SAVED_BODY_BYTES);
        assertEquals(766, CdObjectPlacementRuntime.SAVED_BANK_BYTES);
        assertThrows(NullPointerException.class, () -> CdObjectPlacementRuntime.fromAssets((CdR11aAssets) null, null));
        for (int length : new int[]{0, 2, 0x2FC, 0x2FD, 0x2FF, 0x300, 0x400}) {
            var bank = CdAssetBytes.copyOf(new byte[length]);
            assertThrows(IllegalArgumentException.class, () -> CdObjectPlacementRuntime.fromAssets((CdR11aAssets) null, bank),
                    "length " + length);
        }
        var exact = CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]);
        assertThrows(NullPointerException.class, () -> CdObjectPlacementRuntime.fromAssets((CdR11aAssets) null, exact));
    }

    // SpawnObjects_Init (Object Spawner.asm:42-101) writes savedObjFlags only at its header: MOVE.W #$101 (:55)
    // and the two ADDQ.B counts (:74, :92). Main's SpawnObject then BSETs each spawned row's loaded bit (:273).
    // Retail facts: the first camera-0 present pass spawns saved IDs 3, 8 and 10
    // (TestCdObjectPlacementRetailAssets).
    // Catches dropping or resetting any carried byte, inventing header IDs, or a Main write outside those BSETs.
    @Test
    void exactCarriedBankSurvivesIngressAndTheFirstSpawnRewritesOnlyHeaderIdsAndSpawnedLoadedBits()
            throws Exception {
        var assets = usa();
        byte[] bank = new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];
        // Loaded bits clear, so every spawn decision equals the cold bank's.
        for (int i = 0; i < bank.length; i++) { bank[i] = (byte) ((0x5B + i * 0x35) & 0x7F); }
        bank[0] = (byte) 0xC7;
        bank[1] = (byte) 0x9E;
        var carried = CdObjectPlacementRuntime.fromAssets(assets, CdAssetBytes.copyOf(bank));
        assertArrayEquals(bank, carried.capture().savedFlags().copy(),
                "every real byte is ingress, header included");
        assertEquals(new CdObjectPlacementRuntime.Status(0, 0, 0, 0, 0xC7, 0x9E, -1, -1), carried.status());
        assertSame(assets.placements(), carried.placements());
        assertEquals(Optional.of(assets.identity()), carried.sourceIdentity());
        assertTrue(carried.pool().capture().slots().stream().allMatch(slot -> slot.id() == 0));
        var cold = CdObjectPlacementRuntime.fromAssets(assets);
        assertArrayEquals(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES], cold.capture().savedFlags().copy(),
                "the cold DEV factory keeps its explicit all-zero bank");
        carried.spawnObjects(0, 1);
        cold.spawnObjects(0, 1);
        assertEquals(cold.status(), carried.status(), "Init's header rewrite and the same cursors");
        assertEquals(11, carried.status().rightSavedId());
        assertEquals(1, carried.status().leftSavedId());
        // Distinct pool ownership is intentional; compare each physical slot's native image.
        for (var expected : cold.pool().capture().slots()) {
            var actual = carried.pool().state(expected.identity().wholeIndex());
            assertEquals(expected.identity(), actual.identity());
            assertArrayEquals(expected.copyBytes(), actual.copyBytes());
        }
        byte[] coldAfter = cold.capture().savedFlags().copy();
        byte[] carriedAfter = carried.capture().savedFlags().copy();
        for (int i = 2; i < bank.length; i++) {
            assertEquals((bank[i] | coldAfter[i]) & 0xFF, carriedAfter[i] & 0xFF, "body byte " + i);
        }
        assertEquals(List.of(2 + 3 * 3 + 1, 2 + 3 * 8 + 1, 2 + 3 * 10 + 1),
                IntStream.range(2, bank.length).filter(i -> coldAfter[i] != 0).boxed().toList());
        for (int id : new int[]{3, 8, 10}) { assertEquals(0x80, carried.savedFlag(id, 1) & 0x80); }
    }

    // SpawnObject's BSET #7 precedes FindObjSlot and its BEQ skips an already-loaded row
    // (Object Spawner.asm:273-274).
    // Catches an ingress that silently clears the carried body: saved ID 3's row must not respawn.
    @Test
    void carriedLoadedBitIsLiveStateThatTheFirstSpawnHonoursInsteadOfResetting() throws Exception {
        var assets = usa();
        byte[] bank = new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];
        bank[0] = bank[1] = 1;
        bank[2 + 3 * 3 + 1] = (byte) 0x80;
        var runtime = CdObjectPlacementRuntime.fromAssets(assets, CdAssetBytes.copyOf(bank));
        runtime.spawnObjects(0, 1);
        var first = runtime.pool().state(32);
        var second = runtime.pool().state(33);
        assertEquals(List.of(0x1F0, 0x10, 8), List.of(first.centreX(), first.id(), first.savedFlagsId()));
        assertEquals(List.of(0x270, 0x3D, 10), List.of(second.centreX(), second.id(), second.savedFlagsId()));
        assertEquals(0, runtime.pool().state(34).id());
        assertEquals(12, runtime.status().rightRecordIndex(), "a loaded skip still consumes its record");
        assertEquals(11, runtime.status().rightSavedId());
        for (int id : new int[]{3, 8, 10}) { assertEquals(0x80, runtime.savedFlag(id, 1)); }
    }

    // Catches carrying the bank by restoring the previous visit's capture: the new owner rejects it before any
    // write, and the exact ingress keeps the bank while every other placement field starts cold
    // (.ClearObjects/.ClearCamera).
    @Test
    void anotherOwnersCaptureNeverCarriesTheBankSoTheRestartUsesTheExplicitIngress() throws Exception {
        var assets = usa();
        var old = CdObjectPlacementRuntime.fromAssets(assets);
        old.spawnObjects(0, 1);
        old.resetSavedObjectFlags(); // Routine 8's zero call (Sonic/Main.asm:2465).
        old.bsetSavedFlag(4, 2, MEMBER_3); // Constructed nontrivial body byte: explicit ingress, not a route.
        var oldCapture = old.capture();
        var next = CdObjectPlacementRuntime.fromAssets(assets, oldCapture.savedFlags());
        assertThrows(IllegalArgumentException.class, () -> next.validateRestore(oldCapture));
        assertThrows(IllegalArgumentException.class, () -> next.restore(oldCapture));
        assertArrayEquals(oldCapture.savedFlags().copy(), next.capture().savedFlags().copy());
        assertEquals(4, next.savedFlag(4, 2));
        assertEquals(new CdObjectPlacementRuntime.Status(0, 0, 0, 0, 1, 1, -1, -1), next.status());
        assertTrue(next.pool().capture().slots().stream().allMatch(slot -> slot.id() == 0));
        assertThrows(NullPointerException.class, () -> CdObjectPlacementRuntime.fromAssets(assets, null));
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectPlacementRuntime.fromAssets(assets, CdAssetBytes.copyOf(new byte[0x2FD])));
    }

    private static void fill(CdObjectPool pool) {
        for (int whole = 32; whole < 128; whole++) { pool.write(pool.state(whole).moveByte(ID, 1)); }
    }

    private static int[] row(int x) { return row(x, 0x720, 0x90, 0x21, 0x40, 0); }
    private static int[] row(int x, int y, int id, int subtype, int periods, int subtype2) {
        return new int[]{x, y, id, subtype, periods, subtype2};
    }

    private static CdObjectPlacements layout(int[]... records) {
        int nullAt = 12 + 8 * (records.length + 1);
        var bytes = ByteBuffer.allocate(nullAt + 6);
        bytes.putShort((short) 12).putShort((short) nullAt).putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        for (int[] row : records) {
            bytes.putShort((short) row[0]).putShort((short) row[1]).put((byte) row[2]).put((byte) row[3])
                    .put((byte) row[4]).put((byte) row[5]);
        }
        bytes.putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        bytes.putShort((short) 0xFFFF).putInt(0);
        return CdObjectPlacements.decodeLayout(bytes.array());
    }

    private static CdR11aAssets usaAssets;

    /** Actual USA media through the production loader; immutable assets only, never a runtime or a pass. */
    private static synchronized CdR11aAssets usa() throws Exception {
        if (usaAssets == null) {
            String root = System.getProperty("soniccd.media.root");
            Assumptions.assumeTrue(root != null && !root.isBlank(), "UNRUN media opt-in: set soniccd.media.root");
            Path cue;
            try (var files = Files.list(Path.of(root).resolve("USA"))) {
                var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue"))
                        .toList();
                assertEquals(1, cues.size(), "Configured actual USA media must contain exactly one CUE");
                cue = cues.getFirst();
            }
            try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
                usaAssets = CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
            }
        }
        return usaAssets;
    }


    // R11B factory only: actual authenticated data, no World/Frame/body/service admission.
    // SpawnObjects_Init resets only header0101; native N Object Spawner.asm:42-101,176-319.
    @Test
    void pastFactoryRejectsNullAndWrongBankLengthBeforeTheAssetGate() {
        assertThrows(NullPointerException.class, () -> CdObjectPlacementRuntime.fromAssets(
                (com.openggf.game.soniccd.level.CdR11bAssets) null, null));
        for (int length : new int[]{0, 1, 2, 0x2FC, 0x2FD, 0x2FF, 0x300, 0x400}) {
            var wrong = CdAssetBytes.copyOf(new byte[length]);
            assertThrows(IllegalArgumentException.class, () -> CdObjectPlacementRuntime.fromAssets(
                    (com.openggf.game.soniccd.level.CdR11bAssets) null, wrong), "length " + length);
        }
        assertThrows(NullPointerException.class, () -> CdObjectPlacementRuntime.fromAssets(
                (com.openggf.game.soniccd.level.CdR11bAssets) null,
                CdAssetBytes.copyOf(new byte[766])));
    }

    @Test
    void pastFactoryBindsTheActualPastLayoutAndPreservesAllCarriedBytesBeforeAnyScan() throws Exception {
        var assets = pastFactoryAssets();
        byte[] bank = pastFactoryBank();
        var immutable = CdAssetBytes.copyOf(bank);
        var runtime = CdObjectPlacementRuntime.fromAssets(assets, immutable);
        assertSame(assets.placements(), runtime.placements());
        assertTrue(runtime.sourceIdentity().isEmpty(), "The existing accessor never relabels B as an A identity");
        assertEquals(new CdObjectPlacementRuntime.Status(0, 0, 0, 0, 0xC7, 0x9E, -1, -1), runtime.status());
        assertArrayEquals(bank, runtime.capture().savedFlags().copy());
        for (var slot : runtime.pool().capture().slots()) assertArrayEquals(new byte[64], slot.copyBytes());
        assertEquals(497, runtime.placements().records().size());
        assertEquals(8, runtime.placements().backwardSentinel().orElseThrow().length());
        assertEquals(8, runtime.placements().forwardSentinel().length());
        assertEquals(6, runtime.placements().nullList().orElseThrow().length());
        assertEquals(0x207752, assets.view(com.openggf.game.soniccd.level.CdR11bAssets.Asset.OBJECT_LAYOUTS)
                .range().nativeAddress());
        assertEquals("b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265",
                assets.identity().mmdSha256());
        bank[17] ^= 0x7F;
        assertNotEquals(bank[17], runtime.capture().savedFlags().copy()[17], "Ingress is detached from the caller's array");
        runtime.bsetSavedFlag(1, 2, LOADED);
        assertArrayEquals(pastFactoryBank(), immutable.copy(), "Runtime writes cannot mutate the carried immutable value");
        assertThrows(NullPointerException.class, () -> CdObjectPlacementRuntime.fromAssets(assets, null));
        var method = CdObjectPlacementRuntime.class.getMethod("fromAssets",
                com.openggf.game.soniccd.level.CdR11bAssets.class, CdAssetBytes.class);
        assertEquals(CdObjectPlacementRuntime.class, method.getReturnType());
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()));
    }

    @Test
    void pastInitAtCameraZeroUsesLiteralPastRowsAndOnlyHeaderAndReachedLoadedStores() throws Exception {
        var runtime = CdObjectPlacementRuntime.fromAssets(pastFactoryAssets(), CdAssetBytes.copyOf(pastFactoryBank()));
        var before = runtime.capture();
        runtime.spawnObjects(0, 0);
        assertEquals(new CdObjectPlacementRuntime.Status(2, 0, 12, 0, 11, 1, 0, 0), runtime.status());
        // Native common rows0,1,2,5,6,7,9 under exclusive0280. Present-only rows still count marked IDs.
        int[][] literal = {{0x34, 0x1E6, 0x24, 1, 1}, {0x66, 0x16F, 0x24, 0, 0},
                {0x94, 0x1A2, 0x10, 0x21, 2}, {0x1B1, 0x1A9, 0x3F, 0, 5},
                {0x1EA, 0x1B0, 0x19, 4, 6}, {0x1F0, 0x1E6, 0x10, 0x22, 7},
                {0x25E, 0x12A, 0x24, 0, 0}};
        for (int i = 0; i < 96; i++) {
            var expected = before.pool().slots().get(i);
            if (i < literal.length) {
                var row = literal[i];
                expected = expected.moveWord(X, row[0]).moveWord(Y, row[1]).moveByte(ID, row[2])
                        .moveByte(SUBTYPE, row[3]).moveByte(SAVED_FLAGS_ID, row[4]);
            }
            assertArrayEquals(expected.copyBytes(), runtime.pool().state(32 + i).copyBytes(), "whole " + (32 + i));
        }
        byte[] expectedBank = pastFactoryBank(); expectedBank[0] = 11; expectedBank[1] = 1;
        for (int id : new int[]{1, 2, 5, 6, 7}) expectedBank[2 + 3 * id] |= (byte) 0x80;
        assertArrayEquals(expectedBank, runtime.capture().savedFlags().copy(), "all766 bytes, not only selected flags");
        assertEquals(7, runtime.pool().capture().slots().stream().filter(slot -> slot.id() != 0).count());
    }

    @Test
    void pastLoadedBitSkipsItsParentWhileOtherPeriodsAndWrongPeriodRowsRemainIndependent() throws Exception {
        byte[] bank = pastFactoryBank();
        bank[2 + 3 * 2] = (byte) 0x85; // Past saved ID2 is already loaded, retaining its member bits.
        bank[2 + 3 * 1 + 1] = (byte) 0xD2; // Present ID1's loaded bit cannot suppress its Past row.
        var runtime = CdObjectPlacementRuntime.fromAssets(pastFactoryAssets(), CdAssetBytes.copyOf(bank));
        runtime.spawnObjects(0, 0x80);
        assertEquals(new CdObjectPlacementRuntime.Status(2, 0, 12, 0, 11, 1, 0, 0), runtime.status());
        assertEquals(0x34, runtime.pool().state(32).centreX());
        assertEquals(1, runtime.pool().state(32).savedFlagsId());
        assertEquals(0x1B1, runtime.pool().state(34).centreX());
        assertEquals(0x3F, runtime.pool().state(34).id());
        assertEquals(5, runtime.pool().state(34).savedFlagsId());
        assertEquals(0, runtime.pool().state(38).id());
        assertEquals(6, runtime.pool().capture().slots().stream().filter(slot -> slot.id() != 0).count());
        byte[] expected = bank.clone(); expected[0] = 11; expected[1] = 1;
        for (int id : new int[]{1, 5, 6, 7}) expected[2 + 3 * id] |= (byte) 0x80;
        assertArrayEquals(expected, runtime.capture().savedFlags().copy());
    }

    @Test
    void pastFullPoolRetainsBankBodyAndTheSameChunkCannotRetryTheFailedFirstRow() throws Exception {
        var runtime = CdObjectPlacementRuntime.fromAssets(pastFactoryAssets(), CdAssetBytes.copyOf(pastFactoryBank()));
        fill(runtime.pool());
        var full = runtime.pool().capture();
        runtime.spawnObjects(0, 0);
        assertEquals(new CdObjectPlacementRuntime.Status(2, 0, 0, 0, 1, 1, 0, 0), runtime.status());
        assertEquals(full.slots(), runtime.pool().capture().slots());
        byte[] expected = pastFactoryBank(); expected[0] = expected[1] = 1;
        assertArrayEquals(expected, runtime.capture().savedFlags().copy(), "tentative BSET is undone on allocation failure");
        runtime.pool().deleteObject(32);
        runtime.spawnObjects(0x7F, 0);
        assertArrayEquals(new byte[64], runtime.pool().state(32).copyBytes());
        assertEquals(0, runtime.status().rightRecordIndex());
        runtime.spawnObjects(0x80, 0);
        assertEquals(new CdObjectPlacementRuntime.Status(2, 0x80, 1, 0, 2, 1, 0, 0), runtime.status());
        assertEquals(0x24, runtime.pool().state(32).id());
        assertEquals(0x34, runtime.pool().state(32).centreX());
        assertEquals(1, runtime.pool().state(32).savedFlagsId());
        expected[0] = 2; expected[2 + 3] |= (byte) 0x80;
        assertArrayEquals(expected, runtime.capture().savedFlags().copy());
        for (int whole = 33; whole < 128; whole++)
            assertEquals(full.slots().get(whole - 32), runtime.pool().state(whole));
    }

    @Test
    void pastSnapshotsRejectOtherPastConstructedAndPresentOwnersBeforeAnyAdoption() throws Exception {
        var assets = pastFactoryAssets();
        var runtime = CdObjectPlacementRuntime.fromAssets(assets, CdAssetBytes.copyOf(pastFactoryBank()));
        runtime.spawnObjects(0, 0);
        var before = runtime.capture();
        var otherPast = CdObjectPlacementRuntime.fromAssets(assets, before.savedFlags());
        var constructed = new CdObjectPlacementRuntime(assets.placements());
        var present = CdObjectPlacementRuntime.fromAssets(usa());
        assertTrue(present.sourceIdentity().isPresent());
        for (var foreign : List.of(otherPast.capture(), constructed.capture(), present.capture())) {
            assertThrows(IllegalArgumentException.class, () -> runtime.validateRestore(foreign));
            assertThrows(IllegalArgumentException.class, () -> runtime.restore(foreign));
            assertPastFactorySnapshot(before, runtime);
        }
        assertThrows(NullPointerException.class, () -> runtime.validateRestore(null));
        assertPastFactorySnapshot(before, runtime);
        assertThrows(NullPointerException.class, () -> runtime.restore(null));
        assertPastFactorySnapshot(before, runtime);
    }

    @Test
    void pastActualForwardAndBackwardScansReplayAllCursorsBankAndNinetySixImages() throws Exception {
        var runtime = CdObjectPlacementRuntime.fromAssets(pastFactoryAssets(), CdAssetBytes.copyOf(pastFactoryBank()));
        runtime.spawnObjects(0, 0);
        var before = runtime.capture();
        runtime.spawnObjects(0x180, 0);
        assertEquals(new CdObjectPlacementRuntime.Status(2, 0x180, 19, 4, 17, 4, 0, 0), runtime.status());
        assertEquals(10, runtime.pool().capture().slots().stream().filter(slot -> slot.id() != 0).count());
        assertEquals(0x286, runtime.pool().state(39).centreX());
        assertEquals(0x3D, runtime.pool().state(40).id()); assertEquals(13, runtime.pool().state(40).savedFlagsId());
        assertEquals(0x3E, runtime.pool().state(41).id()); assertEquals(14, runtime.pool().state(41).savedFlagsId());
        var forward = runtime.capture();
        runtime.spawnObjects(0x80, 0);
        assertEquals(new CdObjectPlacementRuntime.Status(2, 0x80, 13, 0, 11, 1, 0, 0), runtime.status());
        assertEquals(0x66, runtime.pool().state(42).centreX());
        assertEquals(0x24, runtime.pool().state(42).id());
        var backward = runtime.capture();
        runtime.pool().objNull(32); runtime.bsetSavedFlag(250, 2, MEMBER_6);
        runtime.restore(before); assertPastFactorySnapshot(before, runtime);
        runtime.spawnObjects(0x180, 0x80); assertPastFactorySnapshot(forward, runtime);
        runtime.spawnObjects(0x80, 0); assertPastFactorySnapshot(backward, runtime);
        runtime.restore(forward); assertPastFactorySnapshot(forward, runtime);
        runtime.spawnObjects(0x1FF, 0); assertPastFactorySnapshot(forward, runtime);
    }

    @Test
    void pastDirtyFreeSlotPreservesAllNonPlacementBytesAndCoordinateFractions() throws Exception {
        var runtime = CdObjectPlacementRuntime.fromAssets(pastFactoryAssets(), CdAssetBytes.copyOf(pastFactoryBank()));
        var dirty = runtime.pool().state(32).moveLong(X_POSITION, 0x1111ABCD).moveLong(Y_POSITION, 0x2222FEDC)
                .moveLong(MAPPINGS, 0x20CAFE).moveByte(SAVED_FLAGS_ID, 99).moveByte(ROUTINE, 0x78)
                .moveByte(COLLISION_TYPE, 0x47).moveByte(PRIORITY, 3).moveWord(TILE, 0xA7AE)
                .moveLong(MOSQUI_ANIMATION_SCRIPT, 0x20B6D8).moveByte(RING_MEMBER, 7);
        runtime.pool().write(dirty);
        runtime.spawnObjects(0, 0);
        var expected = dirty.moveWord(X, 0x34).moveWord(Y, 0x1E6).moveByte(SPRITE_FLAGS, 0).moveByte(FLAGS, 0)
                .moveByte(ID, 0x24).moveByte(SUBTYPE, 1).moveByte(SUBTYPE2, 0).moveByte(SAVED_FLAGS_ID, 1);
        assertArrayEquals(expected.copyBytes(), runtime.pool().state(32).copyBytes());
        assertEquals(0xABCD, runtime.pool().state(32).word(X_FRACTION));
        assertEquals(0xFEDC, runtime.pool().state(32).word(Y_FRACTION));
        assertEquals(0x78, runtime.pool().state(32).routine(), "Placement never executes or resets a body");
    }

    private static byte[] pastFactoryBank() {
        byte[] bank = new byte[766];
        for (int i = 0; i < bank.length; i++) bank[i] = (byte) ((0x5B + i * 0x35) & 0x7F);
        bank[0] = (byte) 0xC7; bank[1] = (byte) 0x9E;
        return bank;
    }

    private static void assertPastFactorySnapshot(CdObjectPlacementRuntime.Snapshot expected,
                                                 CdObjectPlacementRuntime actual) {
        var observed = actual.capture();
        assertEquals(expected.status(), observed.status());
        assertArrayEquals(expected.savedFlags().copy(), observed.savedFlags().copy());
        assertEquals(expected.pool().slots(), observed.pool().slots());
    }

    private static com.openggf.game.soniccd.level.CdR11bAssets pastFactoryAssetCache;
    private static String pastFactoryRoot;
    private static synchronized com.openggf.game.soniccd.level.CdR11bAssets pastFactoryAssets() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assertNotNull(root, "Explicit Past factory checks require the configured original USA media root");
        assertFalse(root.isBlank(), "The original USA media root must not be blank");
        if (pastFactoryAssetCache != null && root.equals(pastFactoryRoot)) return pastFactoryAssetCache;
        Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured original USA needs exactly one CUE");
            cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            var programs = com.openggf.game.soniccd.media.CdTimeWarpPrograms.fromPrograms(source);
            pastFactoryAssetCache = com.openggf.game.soniccd.level.CdR11bAssets.fromPrograms(programs);
            pastFactoryRoot = root;
            return pastFactoryAssetCache;
        }
    }
}
