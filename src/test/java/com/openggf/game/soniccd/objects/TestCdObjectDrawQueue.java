package com.openggf.game.soniccd.objects;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.openggf.game.soniccd.objects.CdObjectDrawQueue.*;
import static org.junit.jupiter.api.Assertions.*;

/** Authored before production code. UNCOMPILED / UNRUN by this external author. */
class TestCdObjectDrawQueue {
    // Literal outcomes from DrawObject (Object Functions.asm:137-196), not a second cull implementation.
    @Test
    void foregroundCullUsesSignedWordEdgesAndUnsignedHighWidthsForEveryRelativeMode() {
        int[][] cases = {
                // camera X, object X, width, admitted
                {0, 0, 0, 1}, {0, 319, 0, 1}, {0, 320, 0, 0}, {0, 0xFFFF, 0, 0},
                {0, 0xFFF8, 8, 1}, {0, 0xFFF7, 8, 0}, {0, 327, 8, 1}, {0, 328, 8, 0},
                {0, 0xFF80, 0x80, 1}, {0, 0xFF7F, 0x80, 0}, {0, 447, 0x80, 1}, {0, 448, 0x80, 0},
                {0, 0xFF01, 0xFF, 1}, {0, 0xFF00, 0xFF, 0}, {0, 574, 0xFF, 1}, {0, 575, 0xFF, 0},
                {0, 0x7FFF, 1, 0}, {0, 0x8000, 0xFF, 0}, {0, 0x7F01, 0xFF, 0},
                {0xFFF0, 0, 0, 1}, {0xFFFF, 0, 0, 1}, {0x8000, 0x8000, 0, 1},
                {0x8000, 0x7FFF, 0, 0}, {0x8000, 0x8000, 0xFF, 1}
        };
        for (int mode : new int[]{4, 8, 12}) {
            for (int[] c : cases) {
                var h = new Harness();
                h.put(32, live(1, 0xD3, 10, 0x400, 0, MAP, 0, 0));
                h.queue.beginPass();
                assertEquals(c[3] == 1 ? EnqueueResult.QUEUED : EnqueueResult.CULLED,
                        h.queue.drawObject(cut(32, mode, c[1], 0x400, 0, c[2], 0),
                                new CameraWords(c[0], 0x400)), Arrays.toString(c) + " mode " + mode);
                assertEquals(0x53, h.live[32].spriteFlagsByte(), "entry clears only live bit 7");
                h.queue.finishAfterCamera(cameras(0, 0x400));
            }
        }
    }

    @Test
    void verticalCullKeepsWordOverflowHighRadiiAndUnsignedWrapThresholds() {
        int[][] cases = {
                // FG Y, object Y, unsigned radius, admitted
                {0x400, 0x400, 0, 1}, {0x400, 0x4DF, 0, 1}, {0x400, 0x4E0, 0, 0},
                {0x400, 0x3FF, 0, 0}, {0x400, 0x380, 0x80, 1}, {0x400, 0x37F, 0x80, 0},
                {0x400, 0x55F, 0x80, 1}, {0x400, 0x560, 0x80, 0},
                {0x400, 0x301, 0xFF, 1}, {0x400, 0x300, 0xFF, 0},
                {0x400, 0x5DE, 0xFF, 1}, {0x400, 0x5DF, 0xFF, 0},
                {0x400, 0x83FF, 1, 0}, {0x400, 0x8400, 0xFF, 0},
                {0, 0x800, 0, 1}, {0, 0x7FF, 0xFF, 0},
                {0xFF, 0x800, 0xFF, 1}, {0x100, 0x800, 0xFF, 0},
                {0x6FF, 0, 0xFF, 0}, {0x700, 0, 32, 0}, {0x700, 0, 33, 1},
                {0x700, 0xDE, 0xFF, 1}, {0x700, 0xDF, 0xFF, 0},
                {0x700, 0xFF, 0xFF, 0}, {0x700, 0x100, 0xFF, 0},
                {0xFFFF, 0xFF, 0xFF, 0}, {0xFFFF, 0xFF01, 0xFF, 1},
                {0x8000, 0x8000, 0, 1}
        };
        for (int mode : new int[]{4, 8, 12}) {
            for (int[] c : cases) {
                var h = new Harness();
                h.put(32, live(1, 0x84, 0, 0, 0, MAP, 0, 0));
                h.queue.beginPass();
                assertEquals(c[3] == 1 ? EnqueueResult.QUEUED : EnqueueResult.CULLED,
                        h.queue.drawObject(cut(32, mode, 0, c[1], c[2], 0, 0),
                                new CameraWords(0, c[0])), Arrays.toString(c) + " mode " + mode);
                assertEquals(4, h.live[32].spriteFlagsByte());
                h.queue.finishAfterCamera(cameras(0, 0));
            }
        }
    }

    @Test
    void absoluteCallsSkipBothCullsAndSkippedCallsDoNotClearAnyOtherSlot() {
        var h = new Harness();
        h.put(0, live(1, 0xA0, 0x8000, 0x1234, 0xABCD, MAP, 0, 0));
        h.put(32, live(1, 0x80, 0, 0, 0, MAP, 0, 0));
        h.map(MAP, true, 0, new StaticMapping(new RawPiece(-1, 0, 0, 0)));
        h.queue.beginPass();
        assertEquals(EnqueueResult.QUEUED,
                h.queue.drawObject(cut(0, 0x20, 0x8000, 0xFFFF, 0, 0, 0), new CameraWords(0, 0)));
        assertEquals(0x20, h.live[0].spriteFlagsByte());
        var sat = h.queue.finishAfterCamera(cameras(0x7777, 0xFFFF));
        assertEquals(0xABCC, word(sat, 0)); // SCREEN_Y is a full word at $0A, not world Y at $0C.
        assertEquals(1, word(sat, 6)); // X $8000 masked to zero, then replaced with one.
        assertEquals(0xA0, h.live[0].spriteFlagsByte());
        assertEquals(0x80, h.live[32].spriteFlagsByte(), "no DrawObject call means no clear");
        assertEquals(List.of("0=false", "0=true"), h.writes);
    }

    @Test
    void drainUsesSelectedPostCameraWithoutRecullingAndRepeatsUnsignedYWrapping() {
        for (int mode : new int[]{4, 8, 12}) {
            var h = new Harness();
            h.put(32, live(1, mode, 0x50, 0x800, 0xDEAD, MAP, 0, 0));
            h.queue.beginPass();
            h.enqueue(32, 0); // Supplied pre-scroll cut is deliberately different from the live drain pose.
            var post = new DrawCameras(new CameraWords(0x10, 0),
                    new CameraWords(0x20, 0x100), new CameraWords(0x30, 0x700));
            var sat = h.queue.finishAfterCamera(post);
            assertEquals(switch (mode) { case 4 -> 0xC0; case 8 -> 0xB0; default -> 0xA0; }, word(sat, 6));
            assertEquals(switch (mode) { case 4 -> 0x80; case 8 -> 0x780; default -> 0x180; }, word(sat, 0));
            assertEquals(mode | 0x80, h.live[32].spriteFlagsByte());
        }
        int[][] wraps = {
                {0, 0x800, 0x80}, {0xFF, 0x7FF, 0x780}, {0x100, 0x800, 0x780},
                {0x6FF, 0, 0xF981}, {0x700, 0, 0x180}, {0x700, 0xFF, 0x27F},
                {0x700, 0x100, 0xFA80}, {0xFFFF, 0xFF, 0x980}, {0xFFFF, 0xFF01, 0xFF82},
                {0x8000, 0x8000, 0x80}
        };
        for (int[] c : wraps) {
            var h = new Harness();
            h.put(32, live(1, 4, 0, c[1], 0, MAP, 0, 0));
            h.queue.beginPass();
            h.enqueue(32, 0);
            assertEquals(c[2], word(h.queue.finishAfterCamera(cameras(0, c[0])), 0), Arrays.toString(c));
        }
    }

    @Test
    void allPriorityBytesMaskToEightBucketsIndependentlyOfTheAdjacentWidthByte() {
        for (int priority = 0; priority <= 255; priority++) {
            var h = new Harness();
            h.put(32, live(1, 0, 128, 0, 128, MAP, 0, 0));
            h.queue.beginPass();
            for (int width : new int[]{0, 1, 127, 128, 255}) {
                assertEquals(EnqueueResult.QUEUED,
                        h.queue.drawObject(cut(32, 0, 0, 0, 0, width, priority), new CameraWords(0, 0)));
            }
            var sat = h.queue.finishAfterCamera(cameras(0, 0));
            assertEquals(5, sat.spriteCount());
            for (int ordinal = 0; ordinal < 5; ordinal++) {
                assertEquals(new Provenance(priority & 7, ordinal, new CdSlotRef(32), 0), sat.provenance().get(ordinal));
            }
        }
    }

    @Test
    void allEightBucketsAcceptSixtyThreePointersThenRejectWithoutEvictionAndAllDrain() {
        var h = new Harness();
        h.put(32, live(1, 0x80, 128, 0, 128, MAP, 0, 0));
        // A second full pass also proves every bucket count was drained, including those after SAT capacity.
        for (int pass = 0; pass < 2; pass++) {
            h.queue.beginPass();
            for (int bucket = 7; bucket >= 0; bucket--) {
                for (int i = 0; i < 63; i++) {
                    assertEquals(EnqueueResult.QUEUED, h.enqueue(32, bucket));
                }
                h.put(32, live(1, 0x80, 128, 0, 128, MAP, 0, 0));
                assertEquals(EnqueueResult.BUCKET_FULL, h.enqueue(32, bucket));
                assertEquals(0, h.live[32].spriteFlagsByte());
            }
            var sat = h.queue.finishAfterCamera(cameras(0, 0));
            assertEquals(80, sat.spriteCount());
            assertEquals(new Provenance(0, 62, new CdSlotRef(32), 0), sat.provenance().get(62));
            assertEquals(new Provenance(1, 0, new CdSlotRef(32), 0), sat.provenance().get(63));
            assertEquals(new Provenance(1, 16, new CdSlotRef(32), 0), sat.provenance().get(79));
            assertEquals(0x80, h.live[32].spriteFlagsByte());
        }
        assertEquals(1008, h.resolutions.size(), "every nondeleted pointer resolves even after SAT fills");
    }

    @Test
    void playerZeroAndDynamicEndpointsShareAppendOrderAndOneSat() {
        var h = new Harness();
        h.put(0, live(1, 0, 128, 0, 128, MAP, 0, 0x100));
        h.put(32, live(0x10, 0, 128, 0, 128, MAP, 0, 0x200));
        h.put(127, live(0x20, 0, 128, 0, 128, MAP, 0, 0x300));
        h.queue.beginPass();
        h.enqueue(127, 2);
        h.enqueue(0, 2);
        h.enqueue(32, 2);
        var sat = h.queue.finishAfterCamera(cameras(0, 0));
        assertEquals(List.of(new Provenance(2, 0, new CdSlotRef(127), 0),
                new Provenance(2, 1, new CdSlotRef(0), 0), new Provenance(2, 2, new CdSlotRef(32), 0)), sat.provenance());
        assertEquals(0x300, word(sat, 4));
        assertEquals(0x100, word(sat, 12));
        assertEquals(0x200, word(sat, 20));
        // Same geometry remains in native append order; no painter/renderer sorting belongs here.
        assertEquals(word(sat, 0), word(sat, 8));
    }

    @Test
    void duplicatePointersRereadLivePoseMappingFlagsTileAndKeepTheirOriginalBuckets() {
        var h = new Harness();
        h.map(0xFFFFFFFC, false, 255, new FrameMapping(1, List.of(new RawPiece(-2, 0, 3, -1))));
        h.put(32, live(1, 0, 10, 20, 30, MAP, 0, 0));
        h.queue.beginPass();
        h.enqueue(32, 7);
        h.enqueue(32, 0);
        h.enqueue(32, 0);
        h.put(32, live(0x25, 0, 100, 0x2222, 200, 0xFFFFFFFC, 255, 0x7FF));
        h.readHook = ref -> {
            if (h.reads == 2) {
                h.put(32, live(0x26, 1, 200, 0x3333, 300, 0xFFFFFFFC, 255, 0xFFFF));
            }
        };
        var sat = h.queue.finishAfterCamera(cameras(0, 0));
        assertEquals(3, sat.spriteCount());
        assertEquals(198, word(sat, 0));
        assertEquals(99, word(sat, 6));
        assertEquals(0x802, word(sat, 4));
        assertEquals(298, word(sat, 8));
        assertEquals(193, word(sat, 14)); // -(signed -1) - 8 + live 200.
        assertEquals(0x802, word(sat, 12)); // (FFFF + 3) wraps to 2, then XOR $800.
        assertEquals(List.of(0, 0, 7), sat.provenance().stream().map(Provenance::queueBucket).toList());
        assertEquals(List.of(0, 1, 0), sat.provenance().stream().map(Provenance::queueOrdinal).toList());
        assertEquals(new Lookup(0xFFFFFFFC, false, 255), h.resolutions.getFirst());
        assertEquals(3, h.reads, "no captured enqueue pose or generation-qualified handle");
    }

    @Test
    void idOnlyNullFullDeleteAndReuseFollowTheLivePhysicalAddressWithoutIdTestingAtEnqueue() {
        var h = new Harness();
        h.put(32, live(0, 0x80, 10, 20, 30, MAP, 0, 7));
        h.put(33, live(1, 0x80, 10, 20, 30, MAP, 0, 9));
        h.put(34, live(1, 0x80, 10, 20, 30, MAP, 0, 11));
        h.put(35, live(1, 0x80, 10, 20, 30, MAP, 0, 13));
        h.queue.beginPass();
        for (int ref : new int[]{32, 33, 34, 35}) { assertEquals(EnqueueResult.QUEUED, h.enqueue(ref, 1)); }
        h.put(33, live(0, 0x25, 10, 20, 30, 0x12345678, 255, 9)); // ID-only Null; dirty bytes remain.
        h.put(34, live(0, 0, 0, 0, 0, 0, 0, 0)); // Full Delete.
        h.put(35, live(0, 0, 0, 0, 0, 0, 0, 0));
        h.put(35, live(0x55, 0, 150, 999, 250, MAP, 0, 0x444)); // Reuse the same address.
        var sat = h.queue.finishAfterCamera(cameras(0, 0));
        assertEquals(1, sat.spriteCount());
        assertEquals(new Provenance(1, 3, new CdSlotRef(35), 0), sat.provenance().getFirst());
        assertEquals(250, word(sat, 0));
        assertEquals(0x444, word(sat, 4));
        assertEquals(0, h.live[32].spriteFlagsByte());
        assertEquals(0x25, h.live[33].spriteFlagsByte(), "DrawDone is skipped for ID zero");
        assertEquals(0, h.live[34].spriteFlagsByte());
        assertEquals(0x80, h.live[35].spriteFlagsByte());
        assertEquals(1, h.resolutions.size());
    }

    @Test
    void resolverReceivesFullUnsignedFrameAndStaticModeAndCanResolveNegativeRelativeWords() {
        var h = new Harness();
        byte[] view = new byte[520];
        // Mapping root is at view offset 8; full frame 255 indexes 8 + 510.
        view[518] = (byte) 0xFF;
        view[519] = (byte) 0xF8; // signed relative -8 selects body at view offset 0.
        view[0] = 1;
        view[1] = (byte) 0xFE;
        view[2] = 0;
        view[3] = 0x18;
        view[4] = 0x23;
        view[5] = (byte) 0xFF;
        h.resolver = (address, isStatic, frame) -> {
            assertEquals(0xF0200008, address, "preserve all raw pointer bits at this port");
            assertFalse(isStatic);
            assertEquals(255, frame);
            int pointerAt = 8 + 2 * frame;
            int relative = (short) ((Byte.toUnsignedInt(view[pointerAt]) << 8) | Byte.toUnsignedInt(view[pointerAt + 1]));
            assertEquals(-8, relative);
            int bodyAt = 8 + relative;
            return new FrameMapping(Byte.toUnsignedInt(view[bodyAt]), List.of(new RawPiece(view[bodyAt + 1],
                    Byte.toUnsignedInt(view[bodyAt + 2]), 0x1823, view[bodyAt + 5])));
        };
        h.put(32, live(1, 0, 128, 0, 128, 0xF0200008, 255, 0));
        h.queue.beginPass();
        h.enqueue(32, 0);
        var sat = h.queue.finishAfterCamera(cameras(0, 0));
        assertEquals(126, word(sat, 0));
        assertEquals(127, word(sat, 6));
        assertEquals(0x1823, word(sat, 4));

        h.resolver = (address, isStatic, frame) -> {
            assertTrue(isStatic);
            assertEquals(233, frame, "static mode bypasses frame selection, without changing the passed byte");
            return new StaticMapping(new RawPiece(1, 0, 0x1234, 2));
        };
        h.put(32, live(1, 0x20, 128, 0, 128, 0xF0200008, 233, 0));
        h.queue.beginPass();
        h.enqueue(32, 0);
        sat = h.queue.finishAfterCamera(cameras(0, 0));
        assertEquals(1, sat.spriteCount());
        assertEquals(129, word(sat, 0));
        assertEquals(130, word(sat, 6));
    }

    @Test
    void emptyFrameSetsBitSevenAndOnlyClearsTheNextFirstLongwordLeavingTheRawTail() {
        byte[] seed = new byte[640];
        Arrays.fill(seed, (byte) 0xA5);
        var h = new Harness(seed);
        h.map(MAP, false, 0, new FrameMapping(0, List.of()));
        h.put(32, live(1, 0, 128, 0, 128, MAP, 0, 0));
        h.queue.beginPass();
        h.enqueue(32, 0);
        var sat = h.queue.finishAfterCamera(cameras(0, 0));
        byte[] expected = seed.clone();
        Arrays.fill(expected, 0, 4, (byte) 0);
        assertArrayEquals(expected, sat.bytes());
        assertEquals(0, sat.spriteCount());
        assertTrue(sat.provenance().isEmpty());
        assertEquals(0x80, h.live[32].spriteFlagsByte());
        h.map(MAP, false, 0, pieces(1));
        h.queue.beginPass();
        h.enqueue(32, 0);
        sat = h.queue.finishAfterCamera(cameras(0, 0));
        assertArrayEquals(new byte[]{0, (byte) 128, 0, 1, 0, 0, 0, (byte) 128}, Arrays.copyOf(sat.bytes(), 8));
        assertArrayEquals(new byte[4], Arrays.copyOfRange(sat.bytes(), 8, 12));
        assertArrayEquals(Arrays.copyOfRange(seed, 12, 640), Arrays.copyOfRange(sat.bytes(), 12, 640));
    }

    @Test
    void pieceCapacityTruncatesEightyOneAndOneHundredTwentyEightWithoutDroppingLaterDrawDone() {
        for (int count : new int[]{80, 81, 128}) {
            var h = new Harness();
            h.map(MAP, false, 0, pieces(count));
            h.map(MAP + 4, false, 0, new FrameMapping(0, List.of()));
            h.put(32, live(1, 0, 128, 0, 128, MAP, 0, 0));
            h.put(33, live(1, 0, 128, 0, 128, MAP + 4, 0, 0));
            h.queue.beginPass();
            h.enqueue(32, 0);
            h.enqueue(33, 7);
            var sat = h.queue.finishAfterCamera(cameras(0, 0));
            assertEquals(80, sat.spriteCount());
            for (int piece = 0; piece < 80; piece++) {
                assertEquals(piece, word(sat, piece * 8 + 4));
                assertEquals(piece == 79 ? 0 : piece + 1, Byte.toUnsignedInt(sat.bytes()[piece * 8 + 3]));
            }
            assertEquals(new Provenance(0, 0, new CdSlotRef(32), 79), sat.provenance().getLast());
            assertEquals(0x80, h.live[32].spriteFlagsByte());
            assertEquals(0x80, h.live[33].spriteFlagsByte());
            assertEquals(2, h.resolutions.size(), "count-zero mapping still resolves while full");
        }
    }

    @Test
    void capacityCanBeReachedInTheMiddleOfAnObjectAndLaterNonemptyMappingsStillResolve() {
        var h = new Harness();
        h.map(MAP, false, 0, pieces(79));
        h.map(MAP + 4, false, 0, new FrameMapping(2,
                List.of(new RawPiece(1, 15, 0xA123, 2), new RawPiece(3, 0, 0xB456, 4))));
        h.put(32, live(1, 0, 128, 0, 128, MAP, 0, 0));
        h.put(33, live(1, 0, 128, 0, 128, MAP + 4, 0, 0));
        h.put(34, live(1, 0, 128, 0, 128, MAP + 4, 0, 0));
        h.queue.beginPass();
        h.enqueue(32, 0);
        h.enqueue(33, 0);
        h.enqueue(34, 0);
        var sat = h.queue.finishAfterCamera(cameras(0, 0));
        assertEquals(80, sat.spriteCount());
        assertEquals(0xA123, word(sat, 636));
        assertEquals(15, Byte.toUnsignedInt(sat.bytes()[634]), "one 4x4 piece consumes one SAT entry");
        assertEquals(0, Byte.toUnsignedInt(sat.bytes()[635]));
        assertEquals(new Provenance(0, 1, new CdSlotRef(33), 0), sat.provenance().getLast());
        assertEquals(0x80, h.live[34].spriteFlagsByte());
        assertEquals(3, h.resolutions.size());
    }

    @Test
    void allObjectFlipsUseWholeNativeDimensionsThenAddAndXorTheCompleteTileWord() {
        // size 9 is 24x16; piece offsets (-5,-7), origin (100,200).
        int[][] cases = {{0, 195, 93, 0x0010}, {1, 195, 83, 0x0810},
                {2, 189, 93, 0x1010}, {3, 189, 83, 0x1810}};
        for (int[] c : cases) {
            var h = new Harness();
            h.map(MAP, false, 0, new FrameMapping(1, List.of(new RawPiece(-5, 9, 0xF810, -7))));
            h.put(32, live(1, c[0], 100, 0, 200, MAP, 0, 0x0800));
            h.queue.beginPass();
            h.enqueue(32, 0);
            var sat = h.queue.finishAfterCamera(cameras(0, 0));
            assertEquals(c[1], word(sat, 0));
            assertEquals(c[2], word(sat, 6));
            assertEquals(c[3], word(sat, 4));
            assertEquals(9, Byte.toUnsignedInt(sat.bytes()[2]));
        }
        int[][] carries = {{0x07FF, 1, 0x0800}, {0x1FFF, 1, 0x2000}, {0x7FFF, 1, 0x8000},
                {0x8000, 0x8000, 0}, {0xFFFF, 2, 1}, {0x1800, 0, 0x1800}};
        for (int[] c : carries) {
            var h = new Harness();
            h.map(MAP, false, 0, new FrameMapping(1, List.of(new RawPiece(0, 0, c[0], 0))));
            h.put(32, live(1, 0, 128, 0, 128, MAP, 0, c[1]));
            h.queue.beginPass();
            h.enqueue(32, 0);
            assertEquals(c[2], word(h.queue.finishAfterCamera(cameras(0, 0)), 4), Arrays.toString(c));
        }
    }

    @Test
    void everySizeAndFlipUsesNativeDimensionsIncludingSignedByteExtremesAndWordWrap() {
        int[] widths = {8, 8, 8, 8, 16, 16, 16, 16, 24, 24, 24, 24, 32, 32, 32, 32};
        int[] heights = {8, 16, 24, 32, 8, 16, 24, 32, 8, 16, 24, 32, 8, 16, 24, 32};
        for (int size = 0; size < 16; size++) {
            for (int flip = 0; flip < 4; flip++) {
                var h = new Harness();
                h.map(MAP, false, 0, new FrameMapping(1, List.of(new RawPiece(-128, size, 0x1800, 127))));
                h.put(32, live(1, flip, 1, 0, 0xFFFF, MAP, 0, 0));
                h.queue.beginPass();
                h.enqueue(32, 0);
                var sat = h.queue.finishAfterCamera(cameras(0, 0));
                assertEquals((flip & 2) == 0 ? 0xFF7F : 127 - heights[size], word(sat, 0));
                assertEquals((flip & 1) == 0 ? 128 : 386 - widths[size], word(sat, 6));
                assertEquals(0x1800 ^ (flip << 11), word(sat, 4));
            }
        }
    }

    @Test
    void emittedYRemainsAFullWordWhileXWrapsAtNineBitsAndNeverEmitsZero() {
        int[][] cases = {
                // absolute X, piece X, absolute screen Y, piece Y, expected X, expected Y
                {0, 0, 0xFFFF, 1, 1, 0},
                {0x1FF, 1, 0, -1, 1, 0xFFFF},
                {0xFFFE, 1, 0x8000, 127, 0x1FF, 0x807F},
                {0, -1, 0, -128, 0x1FF, 0xFF80},
                {0xFFFF, 1, 0xAB00, 1, 1, 0xAB01}
        };
        for (int[] c : cases) {
            var h = new Harness();
            h.map(MAP, false, 0, new FrameMapping(1, List.of(new RawPiece(c[3], 0, 0, c[1]))));
            h.put(32, live(1, 0, c[0], 0x1234, c[2], MAP, 0, 0));
            h.queue.beginPass();
            h.enqueue(32, 0);
            var sat = h.queue.finishAfterCamera(cameras(0, 0));
            assertEquals(c[4], word(sat, 6), Arrays.toString(c));
            assertEquals(c[5], word(sat, 0), Arrays.toString(c));
        }
    }

    static final int MAP = 0x00200100;

    static DrawCallCut cut(int ref, int flags, int x, int y, int radius, int width, int priority) {
        return new DrawCallCut(new CdSlotRef(ref), flags, x, y, radius, width, priority);
    }

    static LiveDrawSlot live(int id, int flags, int x, int y, int screenY, int map, int frame, int tile) {
        return new LiveDrawSlot(id, flags, x, y, screenY, map, frame, tile);
    }

    static DrawCameras cameras(int x, int y) {
        var camera = new CameraWords(x, y);
        return new DrawCameras(camera, camera, camera);
    }

    static FrameMapping pieces(int count) {
        var pieces = new ArrayList<RawPiece>();
        for (int i = 0; i < count; i++) { pieces.add(new RawPiece(0, 0, i, 0)); }
        return new FrameMapping(count, pieces);
    }

    static int word(CpuSat sat, int at) {
        byte[] bytes = sat.bytes();
        return (Byte.toUnsignedInt(bytes[at]) << 8) | Byte.toUnsignedInt(bytes[at + 1]);
    }

    record Lookup(int address, boolean staticPiece, int frame) {}

    /** Synthetic owned ports for CPU tests only; no media authentication claim. */
    static final class Harness implements CdDrawSlots {
        final LiveDrawSlot[] live = new LiveDrawSlot[128];
        final Map<Lookup, MappingBody> bodies = new HashMap<>();
        final List<Lookup> resolutions = new ArrayList<>();
        final List<String> writes = new ArrayList<>();
        Consumer<CdSlotRef> readHook = ignored -> {};
        BiConsumer<CdSlotRef, Boolean> writeHook = (ignored, value) -> {};
        CdDrawMappings resolver = (address, isStatic, frame) -> {
            var result = bodies.get(new Lookup(address, isStatic, frame));
            if (result == null) { throw new UnsupportedOperationException("Synthetic mapping is absent"); }
            return result;
        };
        int reads;
        final CdObjectDrawQueue queue;

        Harness() { this(new byte[640]); }
        Harness(byte[] seed) {
            for (int ref = 0; ref < 128; ref++) {
                if (ref == 0 || ref >= 32) { live[ref] = live(0, 0, 0, 0, 0, 0, 0, 0); }
            }
            map(MAP, false, 0, pieces(1));
            queue = new CdObjectDrawQueue(this, (address, isStatic, frame) -> {
                resolutions.add(new Lookup(address, isStatic, frame));
                return resolver.resolve(address, isStatic, frame);
            }, seed);
        }

        void put(int ref, LiveDrawSlot slot) { live[ref] = slot; }
        void map(int address, boolean isStatic, int frame, MappingBody body) {
            bodies.put(new Lookup(address, isStatic, frame), body);
        }
        EnqueueResult enqueue(int ref, int priority) {
            return queue.drawObject(cut(ref, 0, 128, 128, 0, 0, priority), new CameraWords(0, 0));
        }
        @Override public LiveDrawSlot read(CdSlotRef ref) {
            owned(ref);
            reads++;
            readHook.accept(ref);
            return live[ref.wholeIndex()];
        }
        @Override public void writeOnScreenBit(CdSlotRef ref, boolean value) {
            owned(ref);
            writeHook.accept(ref, value);
            var s = live[ref.wholeIndex()];
            int flags = (s.spriteFlagsByte() & 0x7F) | (value ? 0x80 : 0);
            live[ref.wholeIndex()] = live(s.idByte(), flags, s.xWord(), s.yWord(), s.screenYWord(),
                    s.mappingAddressBits(), s.mapFrameByte(), s.baseTileWord());
            writes.add(ref.wholeIndex() + "=" + value);
        }
        private void owned(CdSlotRef ref) {
            if (ref.wholeIndex() > 0 && ref.wholeIndex() < 32) {
                throw new IllegalArgumentException("Synthetic port has no reserved owner");
            }
        }
    }
}
