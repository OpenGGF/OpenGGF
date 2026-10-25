package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.specialstage.Sonic2PerspectiveData;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageBomb;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageObject;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageRing;
import com.openggf.physics.TrigLookupTable;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/** Short retail script/projection checks, independent of stage progression and rendering. */
@RequiresRom(SonicGame.SONIC_2)
class TestCompetitiveHalfpipeItems {
    @Test
    void retailInstructionsAndAnimationScriptsAnchorUnfixedNativeBehavior() throws Exception {
        RomByteReader bytes = data().rom();
        assertBytes(bytes, 0x3512A,
                "082800070022663a0c380004db0d660c04a80000cccc00306f28600a04a80000cccd00306f1c");
        // No animation-size-change reset before SUBQ.B countdown (FixBugs=0).
        assertBytes(bytes, 0x3539E, "5328001e6a4470001028001cd040d2f10000");
        // Flipped bounds are WORD arithmetic; final comparisons are CMP.B.
        assertBytes(bytes, 0x35260,
                "123c00801c3100041e31000567149c419e414446444712280026b2076504b20665d6");
        assertBytes(bytes, 0x362D2, "0016001c00220028002e0034003a00400046004c0052");
        assertBytes(bytes, 0x362E8, "05000a140aff");
        assertBytes(bytes, 0x36324, "011e1f20ff");
        assertBytes(bytes, 0x364CE, "00160019001c001f002200250028002b002e00310034");
        assertBytes(bytes, 0x36502, "020a0b0cff");
    }

    @Test
    void sizeChangesKeepCountdownAndFramePointerIncludingWhenInvisible() throws Exception {
        CompetitiveHalfpipeRomData data = data();
        Sonic2SpecialStageObject ring = CompetitiveHalfpipeItems.create(data, false, 8, 0x40);
        Sonic2PerspectiveData projection = CompetitiveHalfpipeItems.perspective(data);
        int[] expectedSizes = {6, 7, 7, 8, 8, 8, 9, 9, 9};
        int[] expectedMappings = {6, 6, 6, 6, 6, 6, 19, 19, 19};
        for (int tick = 0; tick < expectedSizes.length; tick++) {
            // Invalid projection keeps the item offscreen without skipping its
            // native size selection or animation countdown.
            CompetitiveHalfpipeItems.advance(ring, -1, projection, false, tick % 5 == 4);
            CompetitiveHalfpipeItems.afterTouch(ring);
            assertEquals(expectedSizes[tick], ring.getAnimIndex(), "size at tick " + tick);
            assertEquals(expectedMappings[tick], ((Sonic2SpecialStageRing) ring).getMappingFrame(),
                    "mapping at tick " + tick);
            assertFalse(ring.isOnScreen());
        }
        assertEquals(0, ring.getDepth());
        assertFalse(ring.shouldRemove(), "positive fraction at integer depth zero is still allocated");
        CompetitiveHalfpipeItems.advance(ring, -1, projection, false, true);
        assertTrue(ring.shouldRemove(), "ten native decrements consume exactly eight fixed-point units");
    }

    @Test
    void ringCollectionKeepsNormalMappingAndAnimatesAfterSamePassTouch() throws Exception {
        CompetitiveHalfpipeRomData data = data();
        Sonic2PerspectiveData projection = CompetitiveHalfpipeItems.perspective(data);
        Sonic2SpecialStageRing ring = (Sonic2SpecialStageRing) CompetitiveHalfpipeItems.create(data, false, 20, 0x40);
        CompetitiveHalfpipeItems.advance(ring, 0x14, projection, false, false);
        assertEquals(1, ring.getAnimIndex());
        assertEquals(0, ring.getMappingFrame(), "touch precedes the first animation command");
        assertTrue(ring.collect());
        assertFalse(ring.collect());
        assertFalse(ring.isSparkle(), "routine six alone has not changed the native art/animation");
        CompetitiveHalfpipeItems.afterTouch(ring);
        assertEquals(1, ring.getMappingFrame());
        assertEquals(1, ring.getAnimFrame());
        CompetitiveHalfpipeItems.advance(ring, 0x14, projection, false, false);
        assertTrue(ring.isSparkle());
        assertEquals(30, ring.getMappingFrame());
        Object afterAnimation = CompetitiveHalfpipeItems.capture(ring);
        CompetitiveHalfpipeItems.afterTouch(ring);
        assertEquals(afterAnimation, CompetitiveHalfpipeItems.capture(ring), "effect pass has no second ring animation");
    }

    @Test
    void collectedRingDoesNotZeroExistingActiveCountdownButBombTouchDoes() throws Exception {
        CompetitiveHalfpipeRomData data = data();
        Sonic2PerspectiveData projection = CompetitiveHalfpipeItems.perspective(data);
        Sonic2SpecialStageRing ring = (Sonic2SpecialStageRing) CompetitiveHalfpipeItems.create(data, false, 20, 0x40);
        Sonic2SpecialStageBomb bomb = (Sonic2SpecialStageBomb) CompetitiveHalfpipeItems.create(data, true, 20, 0x40);
        CompetitiveHalfpipeItems.advance(ring, 0x14, projection, false, false);
        CompetitiveHalfpipeItems.afterTouch(ring);
        CompetitiveHalfpipeItems.advance(bomb, 0x14, projection, false, false);
        assertEquals(1, ring.getAnimFrame());
        assertEquals(1, bomb.getAnimFrame());
        assertTrue(ring.collect());
        assertTrue(bomb.explode());
        assertEquals(1, ring.getAnimFrame(), "Obj60 preserves its frame pointer on touch");
        assertEquals(0, bomb.getAnimFrame(), "Obj61 explicitly clears its frame pointer on touch");
        assertEquals(1, bomb.getMappingFrame(), "bomb art change starts on the following object pass");
        assertFalse(bomb.isExploding());
        CompetitiveHalfpipeItems.advance(bomb, 0x14, projection, false, false);
        assertTrue(bomb.isExploding());
        assertEquals(10, bomb.getMappingFrame());
    }

    @Test
    void effectScriptsLoopWhileDepthContinuesToApproach() throws Exception {
        CompetitiveHalfpipeRomData data = data();
        Sonic2PerspectiveData projection = CompetitiveHalfpipeItems.perspective(data);
        Sonic2SpecialStageRing ring = (Sonic2SpecialStageRing) CompetitiveHalfpipeItems.create(data, false, 20, 0x40);
        Sonic2SpecialStageBomb bomb = (Sonic2SpecialStageBomb) CompetitiveHalfpipeItems.create(data, true, 20, 0x40);
        assertTrue(ring.collect());
        assertTrue(bomb.explode());
        int[] ringMappings = {30, 30, 31, 31, 32, 32, 30, 30, 31, 31};
        int[] bombMappings = {10, 10, 10, 11, 11, 11, 12, 12, 12, 10};
        for (int tick = 0; tick < ringMappings.length; tick++) {
            CompetitiveHalfpipeItems.advance(ring, 0x14, projection, false, tick % 5 == 4);
            CompetitiveHalfpipeItems.advance(bomb, 0x14, projection, false, tick % 5 == 4);
            assertEquals(ringMappings[tick], ring.getMappingFrame());
            assertEquals(bombMappings[tick], bomb.getMappingFrame());
            assertFalse(ring.shouldRemove());
            assertFalse(bomb.shouldRemove());
        }
        assertEquals(12, ring.getDepth());
        assertEquals(12, bomb.getDepth());
        for (int tick = 10; tick < 25; tick++) {
            CompetitiveHalfpipeItems.advance(ring, 0x14, projection, false, tick % 5 == 4);
            CompetitiveHalfpipeItems.advance(bomb, 0x14, projection, false, tick % 5 == 4);
        }
        assertTrue(ring.shouldRemove());
        assertTrue(bomb.shouldRemove());
    }

    @Test
    void nativeProjectionUsesSignedYOnceAndWrappedFlippedByteBounds() throws Exception {
        CompetitiveHalfpipeRomData data = data();
        byte[] raw = data.perspective();
        Sonic2PerspectiveData projection = CompetitiveHalfpipeItems.perspective(data);
        boolean negativeYObserved = false;
        boolean cullingObserved = false;
        for (int frame = 0; frame < 56; frame++) {
            int table = word(raw, frame * 2);
            int maximum = word(raw, table);
            assertEquals(maximum, projection.getMaxDepthCount(frame));
            assertNull(projection.getEntry(frame, 0));
            assertNull(projection.getEntry(frame, maximum + 1));
            for (int depth = 1; depth <= maximum; depth++) {
                int offset = table + 2 + (depth - 1) * 6;
                Sonic2PerspectiveData.PerspectiveEntry entry = projection.getEntry(frame, depth);
                int y = raw[offset + 1];
                assertEquals(y, entry.yBase);
                negativeYObserved |= y < 0;
                int min = raw[offset + 4] & 0xFF;
                int max = raw[offset + 5] & 0xFF;
                cullingObserved |= max != 0;
                for (int angle = 0; angle < 256; angle++) {
                    assertEquals(max == 0 || angle < min || angle >= max,
                            entry.isAngleVisible(angle, false));
                    int upper = (0x80 - min) & 0xFF;
                    int lower = (0x80 - max) & 0xFF;
                    assertEquals(max == 0 || angle < lower || angle >= upper,
                            entry.isAngleVisible(angle, true));
                    if ((angle & 0x3F) == 0) {
                        int expectedX = (raw[offset] & 0xFF)
                                + ((TrigLookupTable.cosHex(angle) * (raw[offset + 2] & 0xFF)) >> 8);
                        int expectedY = y + ((TrigLookupTable.sinHex(angle) * (raw[offset + 3] & 0xFF)) >> 8);
                        assertArrayEquals(new int[]{expectedX, expectedY}, entry.calculateScreenPosition(angle, false));
                    }
                }
            }
        }
        assertTrue(negativeYObserved);
        assertTrue(cullingObserved);
    }

    @Test
    void pendingTouchAndEffectsRestoreWithIndependentForwardReplay() throws Exception {
        CompetitiveHalfpipeRomData data = data();
        Sonic2PerspectiveData projection = CompetitiveHalfpipeItems.perspective(data);
        for (boolean bomb : new boolean[]{false, true}) {
            Sonic2SpecialStageObject original = CompetitiveHalfpipeItems.create(data, bomb, 20, 0x40);
            CompetitiveHalfpipeItems.advance(original, 0x14, projection, false, false);
            Sonic2SpecialStageObject restored = CompetitiveHalfpipeItems.restore(data, CompetitiveHalfpipeItems.capture(original));
            assertNotSame(original, restored);
            if (bomb) {
                assertTrue(((Sonic2SpecialStageBomb) original).explode());
                assertTrue(((Sonic2SpecialStageBomb) restored).explode());
            } else {
                assertTrue(((Sonic2SpecialStageRing) original).collect());
                assertTrue(((Sonic2SpecialStageRing) restored).collect());
            }
            CompetitiveHalfpipeItems.afterTouch(original);
            CompetitiveHalfpipeItems.afterTouch(restored);
            assertEquals(CompetitiveHalfpipeItems.capture(original), CompetitiveHalfpipeItems.capture(restored));
            for (int tick = 0; tick < 30; tick++) {
                CompetitiveHalfpipeItems.advance(original, tick % 56, projection, tick % 2 == 0, tick % 5 == 4);
                CompetitiveHalfpipeItems.advance(restored, tick % 56, projection, tick % 2 == 0, tick % 5 == 4);
                CompetitiveHalfpipeItems.afterTouch(original);
                CompetitiveHalfpipeItems.afterTouch(restored);
                assertEquals(CompetitiveHalfpipeItems.capture(original), CompetitiveHalfpipeItems.capture(restored));
            }
            original.initialize(20, 0x10);
            assertTrue(restored.shouldRemove(), "reinitializing another lifetime cannot revive the restored item");
            assertFalse(original.shouldRemove());
        }
    }

    private static CompetitiveHalfpipeRomData data() throws Exception {
        return new CompetitiveHalfpipeRomData(TestEnvironment.currentRom());
    }

    private static void assertBytes(RomByteReader bytes, int address, String expected) {
        byte[] expectedBytes = HexFormat.of().parseHex(expected);
        assertArrayEquals(expectedBytes, bytes.slice(address, expectedBytes.length));
    }

    private static int word(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }
}
