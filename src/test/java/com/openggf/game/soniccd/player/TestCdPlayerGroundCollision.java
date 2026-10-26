package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdR11aTerrain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Constructed native terrain; expectations come from Collision Floor.asm:30-454. */
class TestCdPlayerGroundCollision {
    @Test void standingOnAnObjectClearsBothBuffersWithoutReadingUnavailableTerrain() {
        Fixture f = new Fixture();
        f.fg[2] = (byte) 0xF9; // Actor sprite bit6 would select unavailable chunk122.
        var state = new CdPlayerGroundCollision.State(0x00231357, 0x0023ABCD, 0xA0, 0xAD, 0x57);
        var input = new CdPlayerGroundCollision.Input(state, 9, 19, 0x40, 0);
        var result = f.owner().ground(input);
        assertEquals(state, result.state());
        assertEquals(0, result.primaryAngle());
        assertEquals(0, result.secondaryAngle());
        assertEquals(state, input.state());
    }

    @Test void floorQueriesRightThenLeftAndEqualDistancesChooseTheSecondaryAngle() {
        Fixture f = floorPair(8, 8, 0x12, 0x34);
        var state = new CdPlayerGroundCollision.State(0x00301234, 0x002DABCD, 0, 0xE5, 0x57);
        var result = f.owner().ground(new CdPlayerGroundCollision.Input(state, 9, 19, 0, 0));
        assertEquals(new CdPlayerGroundCollision.State(0x00301234, 0x0034ABCD, 0x34, 0xE5, 0x57),
                result.state()); // Sensors (57,64)/(39,64), both distance7.
        assertEquals(0x12, result.primaryAngle());
        assertEquals(0x34, result.secondaryAngle());
        assertEquals(0x002DABCD, state.yPosition());
    }

    @ParameterizedTest
    @CsvSource({"12,8,18,48", "8,12,52,48", "29,8,18,31", "8,29,52,31", "29,29,52,31"})
    void smallerSignedDistanceSelectsItsOwnAngle(int primaryHeight, int secondaryHeight, int angle, int y) {
        Fixture f = floorPair(primaryHeight, secondaryHeight, 0x12, 0x34);
        var result = f.owner().ground(input(48, 45, 0, 9, 19, 0, 0));
        assertState(result, 48, y, angle, 0xE5, 0x57);
        assertEquals(0x12, result.primaryAngle());
        assertEquals(0x34, result.secondaryAngle());
    }

    @Test void noWriteBuffersResetToThreeAndLedgeDetachOnlyChangesOwnedStatusBytes() {
        var input = input(48, 45, 0x1A, 9, 19, 0, 0);
        var result = new Fixture().owner().ground(input);
        assertState(result, 48, 45, 0, 0xC7, 1); // Blank floor distance31.
        assertEquals(3, result.primaryAngle());
        assertEquals(3, result.secondaryAngle());
        assertEquals(0xE5, input.state().flags());
        assertEquals(0x57, input.state().previousAnimation());
    }

    @ParameterizedTest
    @CsvSource({
            "0,68,90,0", "31,68,90,0", "32,68,90,64", "33,37,69,64",
            "95,37,69,64", "96,68,53,128", "127,68,53,128", "128,68,53,128",
            "159,68,53,128", "160,68,53,192", "161,90,69,192", "192,90,69,192",
            "223,90,69,192", "224,68,90,0", "255,68,90,0"
    })
    void signedDispatchRoundingIsDistinctFromOddAngleCanonicalization(
            int angle, int expectedX, int expectedY, int expectedAngle) {
        // Blank distances: floor21, right22, ceiling16, left31. Stick permits them.
        var result = new Fixture().owner().ground(input(68, 69, angle, 3, 5, 0, 0x80));
        assertState(result, expectedX, expectedY, expectedAngle, 0xE5, 0x57);
        assertEquals(3, result.primaryAngle());
        assertEquals(3, result.secondaryAngle());
    }

    @Test void rightWallUsesBit13AndWidthWithPrimaryAboveSecondary() {
        Fixture f = new Fixture();
        f.block(2, 6, 4, 0x2001); // (99,71), not native solidity bit14.
        f.block(2, 6, 5, 0x2002); // (99,89).
        f.shape(1, 7, 0x12, 1, 8);
        f.shape(2, 8, 0x34, 1, 8);
        var result = f.owner().ground(input(80, 80, 0xC0, 9, 19, 0, 0));
        assertState(result, 84, 80, 0x34, 0xE5, 0x57);
        assertEquals(0x12, result.primaryAngle());
        assertEquals(0x34, result.secondaryAngle());
    }

    @Test void ceilingXorsTheSensorYAndCancelsOnlyTheMetricYFlip() {
        Fixture f = new Fixture();
        f.block(2, 5, 3, 0x3001); // (89, (80-19)^15=50).
        f.block(2, 4, 3, 0x3002); // (71,50).
        f.shape(1, 7, 0x12, 8, 1);
        f.shape(2, 8, 0x34, 8, 1);
        var result = f.owner().ground(input(80, 80, 0x80, 9, 19, 0, 0));
        assertState(result, 80, 75, 0x4C, 0xE5, 0x57); // Distance5; original Y flips transform angles.
        assertEquals(0x6E, result.primaryAngle());
        assertEquals(0x4C, result.secondaryAngle());
    }

    @Test void leftWallXorsTheSensorXAndCancelsOnlyTheMetricXFlip() {
        Fixture f = new Fixture();
        f.block(2, 3, 4, 0x2801); // ((80-19)^15=50,71).
        f.block(2, 3, 5, 0x2802); // (50,89).
        f.shape(1, 7, 0x12, 1, 8);
        f.shape(2, 8, 0x34, 1, 8);
        var result = f.owner().ground(input(80, 80, 0x40, 9, 19, 0, 0));
        assertState(result, 75, 80, 0xCC, 0xE5, 0x57);
        assertEquals(0xEE, result.primaryAngle());
        assertEquals(0xCC, result.secondaryAngle());
    }

    @Test void anOddWrittenAngleUsesTheOldActorAngleButLeavesTheRawBuffer() {
        Fixture f = floorPair(8, 8, 0x22, 0x25);
        var result = f.owner().ground(input(48, 45, 0x20, 9, 19, 0, 0));
        assertState(result, 48, 52, 0x40, 0xE5, 0x57);
        assertEquals(0x22, result.primaryAngle());
        assertEquals(0x25, result.secondaryAngle());
    }

    @Test void aRealZeroAngleWriteIsDifferentFromAnUnwrittenBuffer() {
        Fixture f = new Fixture();
        f.block(2, 3, 4, 0x2001);
        f.shape(1, 7, 0x32, -8, 0); // Native downward negative-height branch writes angle0.
        var result = f.owner().ground(input(48, 48, 0x20, 9, 19, 0, 0));
        assertState(result, 48, 44, 0, 0xE5, 0x57); // ySensor67 => distance-4.
        assertEquals(0, result.primaryAngle());
        assertEquals(3, result.secondaryAngle());
    }

    @Test void zeroMetricWritesSurviveThePrimitivesBlankExtension() {
        Fixture f = floorPair(0, 8, 0x22, 0x34);
        var result = f.owner().ground(input(48, 45, 0, 9, 19, 0, 0));
        assertState(result, 48, 52, 0x34, 0xE5, 0x57);
        assertEquals(0x22, result.primaryAngle());
        assertEquals(0x34, result.secondaryAngle());
    }

    @ParameterizedTest
    @CsvSource({
            "30,false,0,32,229,87",  // -15: reject penetration, still write angle.
            "29,false,0,18,229,87",  // -14: inclusive alignment.
            "16,false,0,31,229,87",  // -1: full block checks behind.
            "15,false,0,32,229,87",  // 0: angle selection still happens.
            "1,false,0,46,229,87",   // +14: inclusive alignment.
            "16,true,0,32,199,1",    // +15: detach, no position write.
            "15,true,0,32,199,1",    // +16: detach.
            "16,true,128,47,229,87", // Any nonzero stick byte permits +15.
            "15,true,255,48,229,87", // And +16.
            "30,false,255,32,229,87" // Stick does not permit -15 penetration.
    })
    void floorAcceptanceIncludesFourteenAndPickSensorRunsBeforeDistanceRejection(
            int height, boolean extension, int stick, int y, int flags, int previousAnimation) {
        Fixture f = new Fixture();
        f.block(2, 2, extension ? 3 : 2, 0x2001);
        f.shape(1, 7, 0x22, height, 0);
        var result = f.owner().ground(input(32, 32, 0, 0, 0, 0, stick));
        assertState(result, 32, y, 0x22, flags, previousAnimation);
        assertEquals(0x22, result.primaryAngle());
        assertEquals(0x22, result.secondaryAngle());
    }

    @ParameterizedTest
    @CsvSource({"30,false,0,0,229,87", "29,false,0,-14,229,87",
            "1,false,0,14,229,87", "16,true,0,0,199,1",
            "16,true,128,15,229,87", "30,false,255,0,229,87"})
    void wallAndCeilingAcceptanceUseTheirOwnAlignmentDirection(
            int sample, boolean extension, int stick, int correction, int flags, int previousAnimation) {
        Fixture right = new Fixture();
        right.block(2, extension ? 3 : 2, 2, 0x2001);
        right.shape(1, 7, 0x22, 0, sample);
        var r = right.owner().ground(input(32, 32, 0xC0, 0, 0, 0, stick));
        assertState(r, 32 + correction, 32, 0x22, flags, previousAnimation);

        Fixture ceiling = new Fixture();
        ceiling.block(2, 2, extension ? 1 : 2, 0x3001);
        ceiling.shape(1, 7, 0x22, sample, 0);
        var c = ceiling.owner().ground(input(32, 47, 0x80, 0, 0, 0, stick));
        assertState(c, 32, 47 - correction, 0x5E, flags, previousAnimation);

        Fixture left = new Fixture();
        left.block(2, extension ? 1 : 2, 2, 0x2801);
        left.shape(1, 7, 0x22, 0, sample);
        var l = left.owner().ground(input(47, 32, 0x40, 0, 0, 0, stick));
        assertState(l, 47 - correction, 32, 0xDE, flags, previousAnimation);
    }

    @Test void signedWordDistanceIsNotNarrowedToAByteBeforePenetrationRejection() {
        Fixture f = new Fixture();
        f.block(2, 2, 2, 0x2001);
        f.block(2, 2, 1, 0x2002);
        f.shape(1, 7, 0x12, 16, 0);
        f.shape(2, 8, 0x22, 127, 0);
        var result = f.owner().ground(input(35, 47, 0, 0, 0, 0, 0xFF));
        assertState(result, 35, 47, 0x22, 0xE5, 0x57); // Distance-143, not +113.
        assertEquals(0x22, result.primaryAngle());
        assertEquals(0x22, result.secondaryAngle());
    }

    @Test void bothRadiusBytesAreSignExtendedBeforeSensorArithmetic() {
        Fixture f = new Fixture();
        f.fg[3] = 3;
        f.block(2, 2, 2, 0x2001); // (160-128,160-128).
        f.block(3, 2, 2, 0x2002); // (160+128,160-128).
        f.shape(1, 7, 0x12, 8, 0);
        f.shape(2, 8, 0x34, 4, 0);
        var result = f.owner().ground(input(160, 160, 0, 0x80, 0x80, 0, 0));
        assertState(result, 160, 167, 0x12, 0xE5, 0x57);
        assertEquals(0x12, result.primaryAngle());
        assertEquals(0x34, result.secondaryAngle());
    }

    @Test void radiusFFMeansMinusOneAndSensorCoordinatesWrapAsWords() {
        Fixture f = new Fixture();
        f.block(2, 0, 2, 0x2001); // Secondary X=0; primary X=$FFFE is cleared layout.
        f.shape(1, 7, 0x22, 8, 0);
        var state = new CdPlayerGroundCollision.State(0xFFFF1357, 0x0021BEEF, 0, 0xE5, 0x57);
        var result = f.owner().ground(new CdPlayerGroundCollision.Input(state, 0xFF, 0xFF, 0, 0));
        assertEquals(new CdPlayerGroundCollision.State(0xFFFF1357, 0x0028BEEF, 0x22, 0xE5, 0x57),
                result.state()); // Primary x=$FFFE is blank, secondary x=0 selects the shape.
        assertEquals(3, result.primaryAngle());
        assertEquals(0x22, result.secondaryAngle());
    }

    @Test void integerWordAdjustmentWrapsAndPreservesTheEntireFractionWord() {
        var state = new CdPlayerGroundCollision.State(0x00231357, 0xFFF8F00D, 0, 0xE5, 0x57);
        var result = new Fixture().owner().ground(new CdPlayerGroundCollision.Input(state, 0, 0, 0, 1));
        assertEquals(new CdPlayerGroundCollision.State(0x00231357, 0x000FF00D, 0, 0xE5, 0x57),
                result.state()); // Blank distance23: $FFF8 + 23 -> $000F.
    }

    @Test void reversedIntegerWordAdjustmentUnderflowsWithoutBorrowingFromTheFraction() {
        var state = new CdPlayerGroundCollision.State(0x0008F00D, 0x0023ABCD, 0x40, 0xE5, 0x57);
        var result = new Fixture().owner().ground(new CdPlayerGroundCollision.Input(state, 0, 0, 0, 1));
        assertEquals(new CdPlayerGroundCollision.State(0xFFF0F00D, 0x0023ABCD, 0x40, 0xE5, 0x57),
                result.state()); // Mirrored X7, blank distance24: 8 - 24 -> $FFF0.
    }

    @Test void queryingActorsSpriteFlagsChooseThePpLoopChunkWithoutRetainedContext() {
        Fixture f = new Fixture();
        f.fg[2] = (byte) 0xA8;
        f.fillChunk(40, 0x2001);
        f.fillChunk(81, 0x2002); // $28 + 1 -> $29 -> $51.
        f.shape(1, 7, 0x12, 8, 0);
        f.shape(2, 8, 0x34, 12, 0);
        var owner = new CdPlayerGroundCollision(new CdPlayerTerrain(f.terrain()));
        var normal = input(35, 35, 0, 0, 0, 0, 0);
        var alternate = input(35, 35, 0, 0, 0, 0x40, 0);
        var first = owner.ground(normal);
        assertState(first, 35, 39, 0x12, 0xE5, 0x57);
        assertState(owner.ground(alternate), 35, 35, 0x34, 0xE5, 0x57);
        assertEquals(first, owner.ground(normal)); // oFlags bit6 did not select the alternate chunk.
    }

    @Test void materialZeroAndOtherSolidityDoNotBecomeASelectedShape() {
        Fixture f = new Fixture();
        f.block(2, 3, 4, 0x2000);
        f.block(2, 2, 4, 0x4001);
        f.shape(0, 7, 0x12, 8, 0);
        f.shape(1, 8, 0x34, 8, 0);
        var result = f.owner().ground(input(48, 45, 0, 9, 19, 0, 0));
        assertState(result, 48, 45, 0, 0xC7, 1);
        assertEquals(3, result.primaryAngle());
        assertEquals(3, result.secondaryAngle());
    }

    @Test void primaryProbeRunsFirstAndRetainsTheNativeElevenBitMaterialIdentity() {
        Fixture f = new Fixture();
        f.fg[2] = (byte) 0xF9; // Secondary would fail on chunk122.
        f.fg[3] = (byte) 0xA8; // Primary remaps to chunk81.
        f.block(81, 0, 2, 0x2401); // Drawing1, native material1025, selected bit13.
        f.shape(1, 7, 0x22, 8, 0);
        var owner = f.owner();
        var failure = assertThrows(IllegalStateException.class,
                () -> owner.ground(input(256, 35, 0, 1, 0, 0x40, 0)));
        assertTrue(failure.getMessage().contains("unavailable resident bytes: 1025"));
    }

    @Test void anUnavailableSecondProbePublishesNoPartialActorOrAngleBufferState() {
        Fixture f = new Fixture();
        f.fg[2] = (byte) 0xF9;
        f.fg[3] = 2;
        f.block(2, 0, 2, 0x2001); // First query at (257,35) succeeds and writes angle.
        f.block(2, 1, 2, 0x2001);
        f.shape(1, 7, 0x22, 8, 0);
        var owner = f.owner();
        var good = input(272, 35, 0, 1, 0, 0x40, 0);
        var before = owner.ground(good);
        var bad = input(256, 35, 0, 1, 0, 0x40, 0);
        var original = bad.state();
        var failure = assertThrows(IllegalStateException.class, () -> owner.ground(bad));
        assertTrue(failure.getMessage().contains("OUTSIDE_SUPPLIED_CHUNKS"));
        assertEquals(original, bad.state());
        assertState(before, 272, 39, 0x22, 0xE5, 0x57);
        assertEquals(before, owner.ground(good));
    }

    private static Fixture floorPair(int primaryHeight, int secondaryHeight, int primaryAngle, int secondaryAngle) {
        Fixture f = new Fixture();
        f.block(2, 3, 4, 0x2001);
        f.block(2, 2, 4, 0x2002);
        f.shape(1, 7, primaryAngle, primaryHeight, 0);
        f.shape(2, 8, secondaryAngle, secondaryHeight, 0);
        return f;
    }

    private static CdPlayerGroundCollision.Input input(int x, int y, int angle, int xRadius, int yRadius,
                                                       int actorSprFlags, int stick) {
        var state = new CdPlayerGroundCollision.State((x << 16) | 0x1357, (y << 16) | 0xBEEF,
                angle, 0xE5, 0x57);
        return new CdPlayerGroundCollision.Input(state, xRadius, yRadius, actorSprFlags, stick);
    }

    private static void assertState(CdPlayerGroundCollision.Result result, int x, int y, int angle,
                                    int flags, int previousAnimation) {
        assertEquals(new CdPlayerGroundCollision.State((x << 16) | 0x1357, (y << 16) | 0xBEEF,
                angle, flags, previousAnimation), result.state());
    }

    /** Test-only byte construction, never an alternate collision solver or shared testing API. */
    private static final class Fixture {
        final byte[] chunks = new byte[61952];
        final byte[] fg = new byte[338];
        final byte[] bg = new byte[50];
        final byte[] ids = new byte[900];
        final byte[] angles = new byte[256];
        final byte[] heights = new byte[4096];
        final byte[] widths = new byte[4096];

        Fixture() {
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7;
            fg[2] = 2; // Avoid the native chunk1/local0 base-pointer sentinel.
        }

        void block(int chunk, int x, int y, int word) {
            int at = (chunk - 1) * 512 + (y * 16 + x) * 2;
            chunks[at] = (byte) (word >>> 8);
            chunks[at + 1] = (byte) word;
        }

        void fillChunk(int chunk, int word) {
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) { block(chunk, x, y, word); }
            }
        }

        void shape(int material, int id, int angle, int height, int width) {
            ids[material] = (byte) id;
            angles[id] = (byte) angle;
            Arrays.fill(heights, id * 16, id * 16 + 16, (byte) height);
            Arrays.fill(widths, id * 16, id * 16 + 16, (byte) width);
        }

        CdR11aTerrain terrain() {
            return CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg,
                    ids, angles, heights, widths);
        }

        CdPlayerGroundCollision owner() { return new CdPlayerGroundCollision(terrain()); }
    }
}
