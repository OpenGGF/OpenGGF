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
        assertThrows(NullPointerException.class, () -> CdObjectPlacementRuntime.fromAssets(null, null));
        for (int length : new int[]{0, 2, 0x2FC, 0x2FD, 0x2FF, 0x300, 0x400}) {
            var bank = CdAssetBytes.copyOf(new byte[length]);
            assertThrows(IllegalArgumentException.class, () -> CdObjectPlacementRuntime.fromAssets(null, bank),
                    "length " + length);
        }
        var exact = CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]);
        assertThrows(NullPointerException.class, () -> CdObjectPlacementRuntime.fromAssets(null, exact));
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
}
