package com.openggf.game.soniccd.objects;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.openggf.game.soniccd.objects.CdObjectDrawQueue.*;
import static com.openggf.game.soniccd.objects.TestCdObjectDrawQueue.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests-first companion: UNCOMPILED / UNRUN; root owns execution and composite integration. */
class TestCdObjectDrawQueueLifecycle {
    @Test
    void physicalRefsDescribeAllSlotsButFiniteAdmissionRejectsReservedOneThroughThirtyOneBeforeMutation() {
        for (int whole = 0; whole < 128; whole++) {
            var ref = new CdSlotRef(whole);
            assertEquals(0xD000 + whole * 64, ref.nativePointerWord());
            assertEquals(0xFFD000 + whole * 64, ref.nativeAddress24());
        }
        for (int invalid : new int[]{-1, 128, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new CdSlotRef(invalid));
        }
        var h = new Harness();
        h.queue.beginPass();
        for (int reserved = 1; reserved < 32; reserved++) {
            int ref = reserved;
            assertThrows(IllegalArgumentException.class, () -> h.enqueue(ref, 0));
            assertThrows(IllegalArgumentException.class, () -> h.read(new CdSlotRef(ref)));
            assertThrows(IllegalArgumentException.class, () -> h.writeOnScreenBit(new CdSlotRef(ref), true));
        }
        assertTrue(h.writes.isEmpty());
        assertEquals(0, h.reads);
        assertEquals(0, h.queue.finishAfterCamera(cameras(0, 0)).spriteCount());
    }

    @Test
    void everyNativeWidthRejectsNoncanonicalValuesAndPointersKeepAllThirtyTwoBits() {
        for (int bad : new int[]{-1, 256, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            for (int field : new int[]{0, 3, 4, 5}) {
                int[] v = {0, 0, 0, 0, 0, 0};
                v[field] = bad;
                assertThrows(IllegalArgumentException.class, () -> cut(32, v[0], v[1], v[2], v[3], v[4], v[5]));
            }
            for (int field : new int[]{0, 1, 5}) {
                int[] v = {0, 0, 0, 0, 0, 0, 0};
                v[field] = bad;
                assertThrows(IllegalArgumentException.class, () -> live(v[0], v[1], v[2], v[3], v[4], 0, v[5], v[6]));
            }
        }
        for (int bad : new int[]{-1, 65536, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new CameraWords(bad, 0));
            assertThrows(IllegalArgumentException.class, () -> new CameraWords(0, bad));
            assertThrows(IllegalArgumentException.class, () -> cut(32, 0, bad, 0, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> cut(32, 0, 0, bad, 0, 0, 0));
            for (int field : new int[]{2, 3, 4, 6}) {
                int[] v = {0, 0, 0, 0, 0, 0, 0};
                v[field] = bad;
                assertThrows(IllegalArgumentException.class, () -> live(v[0], v[1], v[2], v[3], v[4], 0, v[5], v[6]));
            }
            assertThrows(IllegalArgumentException.class, () -> new RawPiece(0, 0, bad, 0));
        }
        for (int bad : new int[]{-129, 128, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new RawPiece(bad, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new RawPiece(0, 0, 0, bad));
        }
        for (int bad : new int[]{-1, 16, 255, 256}) {
            assertThrows(IllegalArgumentException.class, () -> new RawPiece(0, bad, 0, 0));
        }
        for (int bits : new int[]{0, 0x00200000, 0xFF200000, Integer.MIN_VALUE, -1}) {
            assertEquals(bits, live(255, 255, 65535, 65535, 65535, bits, 255, 65535).mappingAddressBits());
        }
        assertThrows(NullPointerException.class, () -> new DrawCallCut(null, 0, 0, 0, 0, 0, 0));
        assertThrows(NullPointerException.class, () -> new DrawCameras(null, new CameraWords(0, 0), new CameraWords(0, 0)));
        assertThrows(NullPointerException.class, () -> new DrawCameras(new CameraWords(0, 0), null, new CameraWords(0, 0)));
        assertThrows(NullPointerException.class, () -> new DrawCameras(new CameraWords(0, 0), new CameraWords(0, 0), null));
    }

    @Test
    void boundedFramePolicyExplicitlyDiffersFromNativeNegativeCountEmptyBranch() {
        assertEquals(0, new FrameMapping(0, List.of()).countByte());
        assertEquals(128, pieces(128).pieces().size());
        for (int count = 129; count < 256; count++) {
            int rejected = count;
            var failure = assertThrows(UnsupportedOperationException.class,
                    () -> new FrameMapping(rejected, List.of()));
            assertTrue(failure.getMessage().contains("native empty"));
            assertTrue(failure.getMessage().contains(Integer.toString(count)));
        }
        for (int bad : new int[]{-1, 256, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new FrameMapping(bad, List.of()));
        }
        assertThrows(IllegalArgumentException.class, () -> new FrameMapping(1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new FrameMapping(0, List.of(new RawPiece(0, 0, 0, 0))));
        var withNull = new ArrayList<RawPiece>();
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> new FrameMapping(1, withNull));
        assertThrows(NullPointerException.class, () -> new FrameMapping(0, null));
        assertThrows(NullPointerException.class, () -> new StaticMapping(null));
        var mutable = new ArrayList<>(List.of(new RawPiece(0, 0, 0, 0)));
        var body = new FrameMapping(1, mutable);
        mutable.clear();
        assertEquals(1, body.pieces().size());
        assertThrows(UnsupportedOperationException.class, () -> body.pieces().clear());
    }

    @Test
    void initialRawImageIsRequiredPreservedAndCopiedAndSuccessfulPacketsAreImmutable() {
        byte[] seed = new byte[640];
        Arrays.fill(seed, (byte) 0xD7);
        var h = new Harness(seed);
        var initial = h.queue.cpuSat();
        seed[0] = 1;
        assertEquals(0xD7, Byte.toUnsignedInt(initial.bytes()[0]));
        assertEquals(0, initial.spriteCount());
        assertTrue(initial.provenance().isEmpty());
        Arrays.fill(initial.bytes(), (byte) 0);
        assertEquals(0xD7, Byte.toUnsignedInt(h.queue.cpuSat().bytes()[0]));
        h.put(32, live(1, 0, 128, 0, 128, MAP, 0, 7));
        h.queue.beginPass();
        h.enqueue(32, 0);
        var first = h.queue.finishAfterCamera(cameras(0, 0));
        assertNotSame(initial, first);
        assertEquals(0xD7, Byte.toUnsignedInt(initial.bytes()[0]));
        byte[] firstCopy = first.bytes();
        Arrays.fill(first.bytes(), (byte) 0);
        assertArrayEquals(firstCopy, first.bytes());
        assertThrows(UnsupportedOperationException.class, () -> first.provenance().clear());
        h.queue.beginPass();
        var second = h.queue.finishAfterCamera(cameras(0, 0));
        assertNotSame(first, second);
        assertArrayEquals(firstCopy, first.bytes());
        assertEquals(0, second.spriteCount());
        assertEquals(7, word(second, 4), "empty pass retains old tile/X tail");
        assertThrows(IllegalArgumentException.class, () -> new CdObjectDrawQueue(h, h.resolver, new byte[639]));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectDrawQueue(h, h.resolver, new byte[641]));
        assertThrows(NullPointerException.class, () -> new CdObjectDrawQueue(h, h.resolver, null));
        assertThrows(NullPointerException.class, () -> new CdObjectDrawQueue(null, h.resolver, new byte[640]));
        assertThrows(NullPointerException.class, () -> new CdObjectDrawQueue(h, null, new byte[640]));
    }

    @Test
    void passPhasesAndClosedCutCaptureRestoreNeverReplayPortsOrReopenTerminalOwners() {
        var h = new Harness();
        assertEquals(Phase.CLOSED_CUT, h.queue.phase());
        var initial = h.queue.captureClosedCut();
        assertThrows(IllegalStateException.class, () -> h.enqueue(32, 0));
        assertThrows(IllegalStateException.class, () -> h.queue.finishAfterCamera(cameras(0, 0)));
        h.put(32, live(1, 0, 128, 0, 128, MAP, 0, 7));
        h.queue.beginPass();
        assertEquals(Phase.COLLECTING, h.queue.phase());
        assertThrows(IllegalStateException.class, h.queue::beginPass);
        assertThrows(IllegalStateException.class, h.queue::captureClosedCut);
        assertThrows(IllegalStateException.class, h.queue::cpuSat);
        assertThrows(IllegalStateException.class, () -> h.queue.validateRestore(initial));
        assertThrows(IllegalStateException.class, () -> h.queue.restore(initial));
        h.enqueue(32, 0);
        var first = h.queue.finishAfterCamera(cameras(0, 0));
        var completed = h.queue.captureClosedCut();
        assertThrows(IllegalStateException.class, () -> h.queue.finishAfterCamera(cameras(0, 0)));
        h.queue.beginPass();
        h.queue.finishAfterCamera(cameras(0, 0));
        int reads = h.reads;
        int writes = h.writes.size();
        int resolves = h.resolutions.size();
        h.queue.validateRestore(completed);
        h.queue.restore(completed);
        assertSame(first, h.queue.cpuSat());
        assertEquals(reads, h.reads);
        assertEquals(writes, h.writes.size());
        assertEquals(resolves, h.resolutions.size());
        var foreign = new Harness();
        assertThrows(IllegalArgumentException.class, () -> foreign.queue.validateRestore(completed));
        assertThrows(IllegalArgumentException.class, () -> foreign.queue.restore(completed));
        assertEquals(0, foreign.queue.cpuSat().spriteCount());
        assertThrows(NullPointerException.class, () -> h.queue.validateRestore(null));
        assertSame(first, h.queue.cpuSat());
        h.queue.close();
        h.queue.close();
        assertEquals(Phase.CLOSED, h.queue.phase());
        assertThrows(IllegalStateException.class, h.queue::beginPass);
        assertThrows(IllegalStateException.class, h.queue::captureClosedCut);
        assertThrows(IllegalStateException.class, () -> h.queue.restore(completed));
        assertThrows(IllegalStateException.class, h.queue::cpuSat);
    }

    @Test
    void resolverFailureClosesWithoutPublishingPartialSatOrRollingBackCommittedBits() {
        var h = new Harness();
        var oldPacket = h.queue.cpuSat();
        var oldCapture = h.queue.captureClosedCut();
        h.put(32, live(1, 0, 128, 0, 128, MAP, 0, 0));
        h.put(33, live(1, 0, 128, 0, 128, 0xF1234567, 255, 0));
        h.queue.beginPass();
        h.enqueue(32, 0);
        h.enqueue(33, 0);
        var failure = assertThrows(UnsupportedOperationException.class,
                () -> h.queue.finishAfterCamera(cameras(0, 0)));
        assertTrue(failure.getMessage().contains("33"));
        assertTrue(failure.getMessage().contains("F1234567"));
        assertTrue(failure.getMessage().contains("255"));
        assertTrue(failure.getMessage().contains("static=false"));
        assertEquals(Phase.FAILED, h.queue.phase());
        assertEquals(0x80, h.live[32].spriteFlagsByte(), "already committed DrawDone is not rolled back");
        assertEquals(0, h.live[33].spriteFlagsByte());
        assertArrayEquals(new byte[640], oldPacket.bytes());
        assertThrows(IllegalStateException.class, h.queue::cpuSat);
        assertThrows(IllegalStateException.class, h.queue::beginPass);
        assertThrows(IllegalStateException.class, () -> h.queue.finishAfterCamera(cameras(0, 0)));
        assertThrows(IllegalStateException.class, () -> h.queue.validateRestore(oldCapture));
        assertThrows(IllegalStateException.class, () -> h.queue.restore(oldCapture));
        h.queue.close();
        assertEquals(Phase.FAILED, h.queue.phase());
    }

    @Test
    void fullSatCannotMaskLaterUnsupportedHeaderCountOrZeroPointer() {
        for (int policy : new int[]{0, 1}) {
            var h = new Harness();
            h.map(MAP, false, 0, pieces(80));
            h.put(32, live(1, 0, 128, 0, 128, MAP, 0, 0));
            h.put(33, live(1, 0, 128, 0, 128, 0, 129, 0));
            var defaultResolver = h.resolver;
            h.resolver = (address, isStatic, frame) -> {
                if (address == 0) {
                    if (policy == 0) { return new FrameMapping(129, List.of()); }
                    throw new UnsupportedOperationException("No authenticated mapping at pointer zero");
                }
                return defaultResolver.resolve(address, isStatic, frame);
            };
            h.queue.beginPass();
            h.enqueue(32, 0);
            h.enqueue(33, 7);
            assertThrows(UnsupportedOperationException.class, () -> h.queue.finishAfterCamera(cameras(0, 0)));
            assertEquals(2, h.resolutions.size());
            assertEquals(0x80, h.live[32].spriteFlagsByte());
            assertEquals(0, h.live[33].spriteFlagsByte());
            assertEquals(Phase.FAILED, h.queue.phase());
        }
    }

    @Test
    void slotReadWriteAndMappingNullOrModeMismatchAreFailuresWithNoRetry() {
        for (int kind = 0; kind < 7; kind++) {
            var h = new Harness();
            h.put(32, live(1, 0, 128, 0, 128, MAP, 0, 0));
            h.queue.beginPass();
            h.enqueue(32, 0);
            switch (kind) {
                case 0 -> h.readHook = ref -> { throw new IllegalStateException("read failed"); };
                case 1 -> h.writeHook = (ref, value) -> { throw new IllegalStateException("bit write failed"); };
                case 2 -> h.live[32] = null;
                case 3 -> h.resolver = (address, isStatic, frame) -> null;
                case 4 -> h.resolver = (address, isStatic, frame) -> new StaticMapping(new RawPiece(0, 0, 0, 0));
                case 5 -> {
                    h.put(32, live(1, 0x20, 128, 0, 128, MAP, 0, 0));
                    h.resolver = (address, isStatic, frame) -> new FrameMapping(0, List.of());
                }
                default -> h.resolver = (address, isStatic, frame) -> { throw new AssertionError("port failed"); };
            }
            if (kind == 6) {
                assertThrows(AssertionError.class, () -> h.queue.finishAfterCamera(cameras(0, 0)));
            } else {
                assertThrows(RuntimeException.class, () -> h.queue.finishAfterCamera(cameras(0, 0)));
            }
            assertEquals(Phase.FAILED, h.queue.phase(), "failure kind " + kind);
            assertThrows(IllegalStateException.class, h.queue::beginPass);
            assertThrows(IllegalStateException.class, () -> h.queue.finishAfterCamera(cameras(0, 0)));
        }
    }

    @Test
    void entryBitCallbackFailureDoesNotClaimToUndoItsMutation() {
        var h = new Harness();
        h.put(32, live(1, 0xF4, 128, 0, 128, MAP, 0, 0));
        h.writeHook = (ref, value) -> {
            h.put(32, live(1, 0x74, 128, 0, 128, MAP, 0, 0));
            throw new IllegalStateException("callback changed bit then failed");
        };
        h.queue.beginPass();
        assertThrows(IllegalStateException.class, () -> h.enqueue(32, 0));
        assertEquals(0x74, h.live[32].spriteFlagsByte());
        assertEquals(Phase.FAILED, h.queue.phase());
        assertThrows(IllegalStateException.class, h.queue::captureClosedCut);
    }

    @Test
    void reentrantCallbacksAndClosingDuringDrainCannotPublishOrReopenEvenIfCallbackSwallowsError() {
        for (boolean swallow : new boolean[]{false, true}) {
            var h = new Harness();
            h.put(32, live(1, 0, 128, 0, 128, MAP, 0, 0));
            h.queue.beginPass();
            h.enqueue(32, 0);
            h.readHook = ref -> {
                if (swallow) {
                    assertThrows(IllegalStateException.class, h.queue::captureClosedCut);
                } else {
                    h.queue.close();
                }
            };
            assertThrows(IllegalStateException.class, () -> h.queue.finishAfterCamera(cameras(0, 0)));
            assertEquals(Phase.FAILED, h.queue.phase());
            assertThrows(IllegalStateException.class, h.queue::beginPass);
        }
    }

    // U5-D restart cut, written before production (pin 1ecc5f3a). Level_MainLoop:407-408 branches to LevelStart
    // after RunObjects, so DrawObjects (:421) never drains this pass; LevelStart rewrites objDrawQueue at :170-176
    // with no reader in between. Catches draining, bit-7 writes, mapping or camera work at the cut, a republished
    // or cleared CPU SAT, or pointers that survive into the next pass.
    @Test
    void levelStartAbandonsOnlyTheCollectingPassAndKeepsTheExactCpuSatWithoutPortWork() {
        var h = new Harness();
        h.put(0, live(1, 0, 128, 0, 128, MAP, 0, 5));
        h.put(32, live(1, 0, 128, 0, 128, MAP, 0, 7));
        h.put(33, live(1, 0, 128, 0, 128, MAP, 0, 9));
        h.queue.beginPass();
        h.enqueue(32, 0);
        var built = h.queue.finishAfterCamera(cameras(0, 0));
        assertEquals(1, built.spriteCount());
        var builtCut = h.queue.captureClosedCut();
        h.queue.beginPass();
        for (int priority = 0; priority < BUCKET_COUNT; priority++) {
            assertEquals(EnqueueResult.QUEUED, h.enqueue(33, priority));
        }
        assertEquals(EnqueueResult.QUEUED, h.enqueue(0, 3));
        int reads = h.reads;
        var writes = List.copyOf(h.writes);
        int resolutions = h.resolutions.size();
        var player = h.live[0];
        var later = h.live[33];
        h.queue.abandonPassAtLevelStart();
        assertEquals(Phase.CLOSED_CUT, h.queue.phase());
        assertSame(built, h.queue.cpuSat(), "no DrawObjects: the previous bytes, count and provenance stay exactly");
        assertEquals(reads, h.reads);
        assertEquals(writes, h.writes, "no DrawDone bit-7 write for an abandoned pointer");
        assertEquals(resolutions, h.resolutions.size());
        assertSame(player, h.live[0]);
        assertSame(later, h.live[33]);
        var cut = h.queue.captureClosedCut();
        h.queue.validateRestore(builtCut);
        h.queue.restore(builtCut);
        assertSame(built, h.queue.cpuSat());
        h.queue.restore(cut);
        assertSame(built, h.queue.cpuSat());
        h.queue.beginPass(); // Undrained pointers would fail this pass; LevelStart's clear leaves none.
        var next = h.queue.finishAfterCamera(cameras(0, 0));
        assertEquals(0, next.spriteCount());
        assertEquals(reads, h.reads, "abandoned pointers are never dereferenced later");
        assertEquals(writes, h.writes);
        assertEquals(resolutions, h.resolutions.size());
    }

    // Catches reopening a terminal owner, a second abandon of the same pass, or a reentrant abandon that a
    // swallowing callback could use to publish. Misphase keeps the existing non-failing rejection.
    @Test
    void levelStartAbandonFollowsTheExistingMisphasePolicyAndNeverReopensTerminalOwners() {
        var h = new Harness();
        var initial = h.queue.cpuSat();
        assertThrows(IllegalStateException.class, h.queue::abandonPassAtLevelStart);
        assertEquals(Phase.CLOSED_CUT, h.queue.phase(), "rejected like a misphased beginPass, without failing");
        assertSame(initial, h.queue.cpuSat());
        h.queue.beginPass();
        h.queue.abandonPassAtLevelStart();
        assertThrows(IllegalStateException.class, h.queue::abandonPassAtLevelStart, "one pass is abandoned once");
        assertEquals(Phase.CLOSED_CUT, h.queue.phase());
        assertSame(initial, h.queue.cpuSat());
        for (boolean drain : new boolean[]{false, true}) {
            var r = new Harness();
            r.put(32, live(1, 0, 128, 0, 128, MAP, 0, 0));
            r.queue.beginPass();
            if (drain) {
                r.enqueue(32, 0);
                r.readHook = ref -> assertThrows(IllegalStateException.class, r.queue::abandonPassAtLevelStart);
                assertThrows(IllegalStateException.class, () -> r.queue.finishAfterCamera(cameras(0, 0)));
            } else {
                r.writeHook = (ref, value) -> assertThrows(IllegalStateException.class, r.queue::abandonPassAtLevelStart);
                assertThrows(IllegalStateException.class, () -> r.enqueue(32, 0));
            }
            assertEquals(Phase.FAILED, r.queue.phase());
            assertThrows(IllegalStateException.class, r.queue::abandonPassAtLevelStart);
            assertThrows(IllegalStateException.class, r.queue::beginPass);
            assertThrows(IllegalStateException.class, r.queue::captureClosedCut);
            assertEquals(Phase.FAILED, r.queue.phase());
        }
        var closed = new Harness();
        closed.queue.beginPass();
        closed.queue.close();
        assertEquals(Phase.CLOSED, closed.queue.phase());
        assertThrows(IllegalStateException.class, closed.queue::abandonPassAtLevelStart);
        assertThrows(IllegalStateException.class, closed.queue::captureClosedCut);
        assertEquals(Phase.CLOSED, closed.queue.phase());
    }
}
