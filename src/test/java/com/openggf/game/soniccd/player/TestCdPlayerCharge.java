package com.openggf.game.soniccd.player;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.soniccd.player.CdPlayerCharge.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-literal boundary tests at scddisasm 1ecc5f3a, Main.asm:75-94,
 * 939-1136,1366-1438,1471-1486,1744-1773.
 * Held comparison literals are limited to the retained native charge interval;
 * no isolated helper result is claimed as release-post-frame parity.
 */
class TestCdPlayerCharge {
    private static final Body NORMAL = new Body(2, 0, 0);

    @ParameterizedTest
    @CsvSource({"0,0,0", "1,0,2", "28,0,29", "29,0,30", "30,0,30",
            "44,0,30", "45,0,30", "0,4,0", "1,4,2", "29,4,30",
            "30,4,31", "43,4,44", "44,4,45", "45,4,45",
            "254,0,30", "255,0,0", "254,4,45", "255,4,0"})
    void predispatchUsesPriorRollFlagAndByteAddBeforeUnsignedCap(int q, int flags, int expected) {
        State original = state(q, 32700, flags);
        Result result = beforeRoutineDispatch(original);
        assertEquals(expected, result.state().charge());
        assertEquals(32700, result.state().groundVelocity());
        assertEquals(Continuation.ROUTINE_DISPATCH, result.continuation());
        assertEquals(q == 0 ? List.of() : List.of(new ActorWrite(ActorField.CHARGE, expected)), result.effects());
    }

    @Test void zeroPredispatchDoesNoWorkAndDoesNotPrimeAnInitialCharge() {
        State idle = state(0, 0, 0);
        Result result = beforeRoutineDispatch(idle);
        assertSame(idle, result.state());
        assertTrue(result.effects().isEmpty());
        Result start = moveGroundAtCheckCharge(result.state(), input(0x41, 0x40));
        assertEquals(1, start.state().charge());
        assertEquals(2, beforeRoutineDispatch(start.state()).state().charge());
        // No helper hides another increment; caller must not call predispatch twice.
        assertEquals(1, start.state().charge());
    }

    @ParameterizedTest
    @CsvSource({"16", "32", "64"})
    void peeloutStartsAtOneForAnyNativeActionTapWithoutAnInitialSpeedIncrement(int tap) {
        State idle = state(0, 0, 0);
        Result result = moveGroundAtCheckCharge(idle, input(1 | tap, tap));
        assertEquals(1, result.state().charge());
        assertEquals(0, result.state().groundVelocity());
        assertEquals(7, result.state().animation());
        assertEquals(Continuation.GROUND_SETTLE, result.continuation());
        assertEquals(List.of(new GlobalWrite(GlobalField.LOOK_MODE, 0),
                new ActorWrite(ActorField.ANIMATION, 7), new ActorWrite(ActorField.CHARGE, 1),
                new SoundRequest(Sound.FM_9C)), result.effects());
    }

    @Test void heldActionWithoutTapDoesNotStartAndFreshUpTapTakesLookPathFirst() {
        assertEquals(0, moveGroundAtCheckCharge(state(0, 0, 0), input(0x41, 0)).state().charge());
        Result fresh = moveGroundAtCheckCharge(state(0, 0, 0), input(0x41, 0x41));
        assertEquals(0, fresh.state().charge());
        assertEquals(1, fresh.state().lookMode());
        assertTrue(sounds(fresh).isEmpty());
        Result later = moveGroundAtCheckCharge(fresh.state(), input(0x41, 0x40));
        assertEquals(1, later.state().charge());
        assertEquals(2, later.state().lookMode());
    }

    @ParameterizedTest
    @CsvSource({"0,100", "1,-100"})
    void heldPeeloutUsesSignedDecimal100AndLeavesStoredXYAlone(int facing, int expected) {
        State original = state(10, 0, facing);
        Result result = moveGroundAtCheckCharge(original, input(1, 0));
        assertEquals(expected, result.state().groundVelocity());
        assertEquals(10, result.state().charge());
        assertEquals(495, result.state().storedXVelocity());
        assertEquals(-49, result.state().storedYVelocity());
        assertEquals(original.centreY16_16(), result.state().centreY16_16());
        assertEquals(Continuation.MOVE_GROUND_RETURN, result.continuation());
        assertEquals(List.of(new GlobalWrite(GlobalField.LOOK_MODE, 0),
                new ActorWrite(ActorField.ANIMATION, 7), new ActorWrite(ActorField.ANIMATION, 0),
                new ActorWrite(ActorField.GROUND_VELOCITY, expected),
                new ActorWrite(ActorField.GROUND_VELOCITY, expected)), result.effects());
    }

    @ParameterizedTest
    @CsvSource({"0,3000,1536,0,3072", "1,-3000,1536,0,-3072",
            "0,4500,3072,1,4600", "1,-4500,3072,1,-4600",
            "0,4508,3072,1,4608", "1,-4508,3072,1,-4608",
            "0,4509,3072,1,4608", "1,-4509,3072,1,-4608",
            "0,6000,1536,0,3072", "1,-6000,1536,0,-3072",
            "0,5000,3073,128,4610", "1,-5000,3073,128,-4610"})
    void chargeSpeedUsesCurrentTopAndShoesFormulaEvenIfAlreadyPastCap(
            int facing, int velocity, int top, int shoes, int expected) {
        Result result = moveGroundAtCheckCharge(state(30, velocity, facing), input(1, 0, top, shoes));
        assertEquals(expected, result.state().groundVelocity());
        assertEquals(30, result.state().charge());
    }

    @ParameterizedTest
    @CsvSource({"0,32760,1536,0,-32676", "1,-32760,1536,0,32676",
            "0,0,20000,0,-25536", "1,0,20000,0,25536",
            "0,0,-3,1,-4", "1,0,-3,1,4",
            "0,0,16384,0,-32768", "1,0,16384,0,-100"})
    void nativeWordWrapAndArithmeticShiftAreNotJavaWideOrAbsoluteSpeedCaps(
            int facing, int velocity, int top, int shoes, int expected) {
        assertEquals(expected, moveGroundAtCheckCharge(state(2, velocity, facing),
                input(1, 0, top, shoes)).state().groundVelocity());
    }

    @ParameterizedTest
    @CsvSource({"28,0,0", "29,2800,1", "30,2900,1"})
    void peelReleaseSeesImmediatePredispatchIncrement(int prior, int inertia, int mature) {
        State advanced = beforeRoutineDispatch(state(prior, inertia, 0)).state();
        Result result = moveGroundAtCheckCharge(advanced, input(0, 0));
        assertEquals(0, result.state().charge());
        assertEquals(mature == 1 ? inertia : 0, result.state().groundVelocity());
        assertEquals(List.of(mature == 1 ? Sound.FM_91 : Sound.FM_CHARGESTOP), sounds(result));
        assertEquals(Continuation.GROUND_SETTLE, result.continuation());
        assertEquals(495, result.state().storedXVelocity()); // This span does NOT clear XY.
        assertEquals(-49, result.state().storedYVelocity());
    }

    @Test void charge29WithoutPredispatchStillCancelsAndDoesNotInventAFrame() {
        Result result = moveGroundAtCheckCharge(state(29, 2800, 0), input(0, 0));
        assertEquals(0, result.state().groundVelocity());
        assertEquals(List.of(Sound.FM_CHARGESTOP), sounds(result));
    }

    @Test void idleCheckChargeStillMakesTheSourceStopRequestAndWrites() {
        Result result = moveGroundAtCheckCharge(state(0, 0, 0), input(0, 0));
        assertEquals(List.of(new GlobalWrite(GlobalField.LOOK_MODE, 0),
                new SoundRequest(Sound.FM_CHARGESTOP), new ActorWrite(ActorField.CHARGE, 0),
                new ActorWrite(ActorField.GROUND_VELOCITY, 0), new GlobalWrite(GlobalField.LOOK_MODE, 0),
                new GlobalWrite(GlobalField.LOOK_MODE, 0)), result.effects());
    }

    @ParameterizedTest
    @CsvSource({"0,512", "1,-22"})
    void groundSpindashEntryHasSigned22ThenAsymmetricStartRoll(int facing, int expected) {
        State original = state(0, 0, facing);
        Result result = moveGroundAtCheckCharge(original, input(0x42, 0x40));
        assertEquals(1, result.state().charge());
        assertEquals(expected, result.state().groundVelocity());
        assertEquals(facing | 4, result.state().flags());
        assertEquals(7, result.state().xRadius());
        assertEquals(14, result.state().yRadius());
        assertEquals(original.centreY16_16() + (5 << 16), result.state().centreY16_16());
        assertEquals(2, result.state().animation());
        assertEquals(495, result.state().storedXVelocity());
        assertEquals(-49, result.state().storedYVelocity());
        assertEquals(Continuation.GROUND_SETTLE, result.continuation());
        int sound = result.effects().indexOf(new SoundRequest(Sound.FM_9C));
        int resize = result.effects().indexOf(new ActorWrite(ActorField.Y_RADIUS, 14));
        assertTrue(sound > 0 && resize > sound);
        assertEquals(Continuation.ROUTINE_DISPATCH,
                beforeRoutineDispatch(result.state()).continuation());
        assertEquals(2, beforeRoutineDispatch(result.state()).state().charge());
    }

    @Test void rightSpinStartRequestsKeepEverySourceWriteInItsOriginalOrder() {
        State original = state(0, 0, 0);
        Result result = moveGroundAtCheckCharge(original, input(0x42, 0x40));
        assertEquals(List.of(new GlobalWrite(GlobalField.LOOK_MODE, 0),
                new ActorWrite(ActorField.ANIMATION, 8), new ActorWrite(ActorField.CHARGE, 1),
                new ActorWrite(ActorField.GROUND_VELOCITY, 22), new SoundRequest(Sound.FM_9C),
                new ActorWrite(ActorField.FLAGS, 4), new ActorWrite(ActorField.Y_RADIUS, 14),
                new ActorWrite(ActorField.X_RADIUS, 7), new ActorWrite(ActorField.CENTRE_Y_WORD, 1903),
                new ActorWrite(ActorField.ANIMATION, 2), new ActorWrite(ActorField.GROUND_VELOCITY, 512)),
                result.effects());
    }

    @ParameterizedTest
    @CsvSource({"0,512", "22,512", "511,512", "512,512", "513,513",
            "-1,-1", "-22,-22", "-512,-512", "-32768,-32768"})
    void startRollFloorIsSignAsymmetricAndNotFacingDependent(int inertia, int expected) {
        for (int flags : new int[]{0, 1}) {
            Result result = startRoll(state(0, inertia, flags), NORMAL);
            assertEquals(expected, result.state().groundVelocity());
            assertEquals(Continuation.START_ROLL_RETURN, result.continuation());
            assertTrue(sounds(result).isEmpty());
        }
    }

    @Test void startRollPreservesFractionAndWrapsOnlyCentreWordAndDoesNotResizeTwice() {
        State original = positioned(state(0, 22, 0x60), 0xFFFD_ABCD, 0, 0x60);
        Result first = startRoll(original, NORMAL);
        assertEquals(0x0002_ABCD, first.state().centreY16_16());
        assertEquals(0x64, first.state().flags());
        Result second = startRoll(first.state(), NORMAL);
        assertEquals(first.state(), second.state());
        assertEquals(List.of(new ActorWrite(ActorField.ANIMATION, 2)), second.effects());
    }

    @ParameterizedTest
    @CsvSource({"4,512,587,587", "5,-22,-97,-97", "4,3070,3145,3072", "5,-3070,-3145,-3072",
            "4,32760,-32701,-32701", "5,-32760,32701,32701"})
    void heldSpinAddsSignedDecimal75ThenCapsWithoutProjectingStoredXY(int flags, int inertia, int uncapped, int expected) {
        State original = state(44, inertia, flags);
        Result result = moveRollAtNotRight(original, input(2, 0));
        assertEquals(expected, result.state().groundVelocity());
        assertEquals(44, result.state().charge());
        assertEquals(495, result.state().storedXVelocity());
        assertEquals(-49, result.state().storedYVelocity());
        assertEquals(original.centreY16_16(), result.state().centreY16_16());
        assertEquals(List.of(new ActorWrite(ActorField.GROUND_VELOCITY, uncapped),
                new ActorWrite(ActorField.GROUND_VELOCITY, expected)), result.effects());
        assertEquals(Continuation.MOVE_ROLL_RETURN, result.continuation());
    }

    @Test void spinShoesCapUsesSameNativeFormulaRatherThanSharedS1SpeedTable() {
        assertEquals(4608, moveRollAtNotRight(state(45, 4600, 4), input(2, 0, 3072, 1))
                .state().groundVelocity());
        assertEquals(-4608, moveRollAtNotRight(state(45, -4600, 5), input(2, 0, 3072, 1))
                .state().groundVelocity());
    }

    @ParameterizedTest
    @CsvSource({"43,0", "44,1", "45,1"})
    void spinReleaseThresholdIsAfterPredispatchAndAddsSpeedBeforeCheckingDown(int prior, int mature) {
        State original = state(prior, 1000, 4);
        Result result = moveRollAtNotRight(beforeRoutineDispatch(original).state(), input(0, 0));
        assertEquals(new ActorWrite(ActorField.GROUND_VELOCITY, 1075), result.effects().getFirst());
        assertEquals(0, result.state().charge());
        if (mature == 1) {
            assertEquals(1075, result.state().groundVelocity());
            assertEquals(4, result.state().flags());
            assertEquals(495, result.state().storedXVelocity());
            assertEquals(Continuation.ROLL_MOVE_RIGHT_THEN_SETTLE, result.continuation());
            assertEquals(List.of(Sound.FM_91), sounds(result));
        } else {
            assertEquals(0, result.state().groundVelocity());
            assertEquals(0, result.state().storedXVelocity());
            assertEquals(0, result.state().storedYVelocity());
            assertEquals(0, result.state().flags());
            assertEquals(9, result.state().xRadius());
            assertEquals(19, result.state().yRadius());
            assertEquals(original.centreY16_16() - (5 << 16), result.state().centreY16_16());
            assertEquals(5, result.state().animation());
            assertEquals(Continuation.ROLL_CALC_XY_VELOCITIES, result.continuation());
            assertEquals(List.of(Sound.FM_CHARGESTOP), sounds(result));
        }
    }

    @Test void spin44WithoutPredispatchCancelsAndEarlyCancelWriteOrderIsLiteralSourceOrder() {
        State original = positioned(state(44, -1000, 0x65), 0x0002_BEEF, 0x8A, 0xC8);
        Result result = moveRollAtNotRight(original, input(0, 0));
        assertEquals(List.of(new ActorWrite(ActorField.GROUND_VELOCITY, -1075),
                new ActorWrite(ActorField.GROUND_VELOCITY, -1075),
                new SoundRequest(Sound.FM_CHARGESTOP), new ActorWrite(ActorField.CHARGE, 0),
                new ActorWrite(ActorField.GROUND_VELOCITY, 0), new ActorWrite(ActorField.STORED_X_VELOCITY, 0),
                new ActorWrite(ActorField.STORED_Y_VELOCITY, 0), new ActorWrite(ActorField.FLAGS, 0x61),
                new ActorWrite(ActorField.Y_RADIUS, 19), new ActorWrite(ActorField.X_RADIUS, 9),
                new ActorWrite(ActorField.CENTRE_Y_WORD, 0xFFFD), new ActorWrite(ActorField.ANIMATION, 5)),
                result.effects());
        assertEquals(0xFFFD_BEEF, result.state().centreY16_16());
        assertEquals(0x8A, result.state().lookMode());
        assertEquals(0xC8, result.state().cameraYCentre());
    }

    @Test void matureLeftSpinRequestsActualDirectionalCallAndLeavesItsWritesToCaller() {
        Result result = moveRollAtNotRight(state(45, -1000, 5), input(0, 0));
        assertEquals(List.of(new ActorWrite(ActorField.GROUND_VELOCITY, -1075),
                new ActorWrite(ActorField.GROUND_VELOCITY, -1075),
                new ActorWrite(ActorField.CHARGE, 0), new SoundRequest(Sound.FM_91)), result.effects());
        assertEquals(Continuation.ROLL_MOVE_LEFT_THEN_SETTLE, result.continuation());
        assertEquals(-1075, result.state().groundVelocity()); // Not a post-Settle or post-frame velocity.
    }

    @Test void zeroSpinAtNotRightDoesNoWorkAndMoveLockDoesNotBlockThisBoundary() {
        State original = state(0, 512, 4);
        Result result = moveRollAtNotRight(original, input(0, 0));
        assertSame(original, result.state());
        assertTrue(result.effects().isEmpty());
        assertEquals(Continuation.ROLL_SETTLE, result.continuation());
        Input locked = new Input(NORMAL, 2, 0, 1536, 0, 0, -1, 0);
        assertEquals(587, moveRollAtNotRight(state(2, 512, 4), locked).state().groundVelocity());
    }

    @Test void activatedLookFlagsAndDoubleTapsGateChargeInsteadOfStartingIt() {
        State idle = state(0, 0, 0);
        Result up = moveGroundAtCheckCharge(positioned(idle, idle.centreY16_16(), 0x80, 0x60), input(0x41, 0x40));
        assertEquals(0, up.state().charge());
        assertEquals(0x62, up.state().cameraYCentre());
        assertEquals(7, up.state().animation());
        assertTrue(sounds(up).isEmpty());
        Result down = moveGroundAtCheckCharge(positioned(idle, idle.centreY16_16(), 0x40, 0x60), input(0x42, 0x40));
        assertEquals(0, down.state().charge());
        assertEquals(0x5E, down.state().cameraYCentre());
        assertEquals(8, down.state().animation());
        assertTrue(sounds(down).isEmpty());
        for (int direction : new int[]{1, 2}) {
            Result tap = moveGroundAtCheckCharge(positioned(idle, idle.centreY16_16(), 1, 0x60),
                    input(direction | 0x40, direction | 0x40));
            assertEquals(0, tap.state().charge());
            assertEquals(direction == 1 ? 0x82 : 0x42, tap.state().lookMode());
            assertTrue(sounds(tap).isEmpty());
        }
    }

    @Test void lookTimerByteCarryAndMaskAndCameraEndpointArePreserved() {
        State idle = state(0, 0, 0);
        Result rollover = moveGroundAtCheckCharge(positioned(idle, 0, 0x8F, 0xC8), input(0x41, 0x40));
        assertEquals(0x80, rollover.state().lookMode());
        assertEquals(0, rollover.state().charge());
        assertEquals(List.of(new GlobalWrite(GlobalField.LOOK_MODE, 0x90),
                new GlobalWrite(GlobalField.LOOK_MODE, 0x80), new ActorWrite(ActorField.ANIMATION, 7)), rollover.effects());
        assertEquals(8, moveGroundAtCheckCharge(positioned(idle, 0, 0x40, 8), input(0x42, 0x40))
                .state().cameraYCentre());
        // Native tests equality, not a clamped range: odd/out-of-range centres still add/subtract.
        assertEquals(0xCB, moveGroundAtCheckCharge(positioned(idle, 0, 0x80, 0xC9), input(1, 0))
                .state().cameraYCentre());
    }

    @Test void resetScreenPreservesBothNativeWritesAndUnsignedWordComparison() {
        State mature = positioned(state(30, 2900, 0), 0, 0, 0x5F);
        Result result = moveGroundAtCheckCharge(mature, input(0, 0));
        assertEquals(0, result.state().charge());
        assertEquals(List.of(Sound.FM_91), sounds(result));
        assertEquals(0x61, result.state().cameraYCentre());
        List<Effect> camera = result.effects().stream().filter(e -> e instanceof GlobalWrite w
                && w.field() == GlobalField.CAMERA_Y_CENTRE).toList();
        assertEquals(List.of(new GlobalWrite(GlobalField.CAMERA_Y_CENTRE, 0x63),
                new GlobalWrite(GlobalField.CAMERA_Y_CENTRE, 0x61)), camera);
        assertEquals(0xFFFD, moveGroundAtCheckCharge(positioned(state(30, 2900, 0), 0, 0, 0xFFFF), input(0, 0))
                .state().cameraYCentre());
        assertEquals(0, moveGroundAtCheckCharge(positioned(state(30, 2900, 0), 0, 0x80, 0x60), input(0, 0))
                .state().lookMode());
    }

    @Test void nativeLookFlagPriorityCanDeferReleaseOfAnExistingCharge() {
        State active = positioned(state(30, 2900, 0), 0, 0x80, 0x60);
        Result stillLooking = moveGroundAtCheckCharge(active, input(1, 0));
        assertEquals(30, stillLooking.state().charge());
        assertEquals(2900, stillLooking.state().groundVelocity());
        assertTrue(sounds(stillLooking).isEmpty());
        Result releasedUp = moveGroundAtCheckCharge(active, input(0, 0));
        assertEquals(30, releasedUp.state().charge()); // LookUp jumps to CheckForSpindash.
        assertTrue(sounds(releasedUp).isEmpty());
        assertEquals(Continuation.GROUND_SETTLE, releasedUp.continuation());
    }

    @Test void earlierGroundBypassesCannotBeSilentlyTreatedAsChargeAdmission() {
        State idle = state(0, 0, 0);
        for (Input blocked : List.of(
                new Input(NORMAL, 0x41, 0x40, 1536, 0, 0, 0, 1),
                new Input(NORMAL, 0x41, 0x40, 1536, 0, 0, 1, 0),
                new Input(NORMAL, 0x41, 0x40, 1536, 0, 0x20, 0, 0))) {
            assertThrows(UnsupportedOperationException.class, () -> moveGroundAtCheckCharge(idle, blocked));
        }
        assertThrows(UnsupportedOperationException.class,
                () -> moveGroundAtCheckCharge(state(0, 1, 0), input(0x41, 0x40)));
        assertThrows(UnsupportedOperationException.class, () -> moveRollAtNotRight(state(1, 512, 4),
                new Input(NORMAL, 2, 0, 1536, 0, 0, 0, 1)));
        assertEquals(0, idle.charge());
    }

    @Test void unsupportedFamiliesAndWrongModesFailBeforeAnyTransition() {
        for (Body body : List.of(new Body(0, 0, 0), new Body(4, 0, 0), new Body(2, 1, 0),
                new Body(2, 2, 0), new Body(2, 0, 1))) {
            Input input = new Input(body, 0x41, 0x40, 1536, 0, 0, 0, 0);
            assertThrows(UnsupportedOperationException.class, () -> moveGroundAtCheckCharge(state(0, 0, 0), input));
            assertThrows(UnsupportedOperationException.class, () -> moveRollAtNotRight(state(1, 512, 4), input));
            assertThrows(UnsupportedOperationException.class, () -> startRoll(state(0, 0, 0), body));
        }
        assertThrows(UnsupportedOperationException.class, () -> startRoll(state(0, 0, 2), NORMAL));
        assertThrows(UnsupportedOperationException.class, () -> moveGroundAtCheckCharge(state(0, 0, 4), input(1, 0)));
        assertThrows(UnsupportedOperationException.class, () -> moveRollAtNotRight(state(1, 0, 0), input(2, 0)));
    }

    @Test void heldComparisonLiteralsStopBeforeReleaseAndOutsideEntryFrameMovement() {
        // Native reference: comparison-only post-frame held samples, right-facing.
        // Seed at observed charge=1 AFTER the entry frame's ordinary movement;
        // compare only charge/inertia and held position through this finite interval.
        State peel = state(1, 0, 0);
        for (int dispatch = 2; dispatch <= 42; dispatch++) {
            peel = moveGroundAtCheckCharge(beforeRoutineDispatch(peel).state(), input(1, 0)).state();
            if (dispatch == 19) { assertEquals(19, peel.charge()); assertEquals(1800, peel.groundVelocity()); }
            if (dispatch == 29) { assertEquals(29, peel.charge()); assertEquals(2800, peel.groundVelocity()); }
            if (dispatch == 30) { assertEquals(30, peel.charge()); assertEquals(2900, peel.groundVelocity()); }
        }
        assertEquals(30, peel.charge());
        assertEquals(3072, peel.groundVelocity());
        State spin = state(1, 512, 4);
        int fixedY = spin.centreY16_16();
        for (int dispatch = 2; dispatch <= 59; dispatch++) {
            spin = moveRollAtNotRight(beforeRoutineDispatch(spin).state(), input(2, 0)).state();
            if (dispatch == 19) { assertEquals(19, spin.charge()); assertEquals(1862, spin.groundVelocity()); }
            if (dispatch == 44) { assertEquals(44, spin.charge()); assertEquals(3072, spin.groundVelocity()); }
            if (dispatch == 45) { assertEquals(45, spin.charge()); assertEquals(3072, spin.groundVelocity()); }
            assertEquals(495, spin.storedXVelocity());
            assertEquals(-49, spin.storedYVelocity());
            assertEquals(fixedY, spin.centreY16_16());
        }
        // Release needs normal movement/projection/integration; no post-frame assertion here.
    }

    @Test void capturesAndOrderedEffectsAreImmutableAndRestoreReplaysExactly() {
        State checkpoint = state(19, 1862, 4);
        List<Result> forward = spinContinuation(checkpoint);
        assertEquals(forward, spinContinuation(checkpoint));
        assertEquals(19, checkpoint.charge());
        assertEquals(1862, checkpoint.groundVelocity());
        assertEquals(0, forward.getLast().state().charge());
        assertThrows(UnsupportedOperationException.class,
                () -> forward.getFirst().effects().add(new SoundRequest(Sound.FM_9C)));
        List<Effect> mutable = new ArrayList<>();
        Result detached = new Result(checkpoint, mutable, Continuation.MOVE_ROLL_RETURN);
        mutable.add(new SoundRequest(Sound.FM_91));
        assertTrue(detached.effects().isEmpty());
    }

    @Test void inputAndStateRejectAccidentalWideIntegersInsteadOfLosingNativeBits() {
        assertThrows(IllegalArgumentException.class, () -> state(256, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> state(0, 32768, 0));
        assertThrows(IllegalArgumentException.class, () -> input(256, 0));
        assertThrows(IllegalArgumentException.class, () -> input(1, -1));
        assertThrows(IllegalArgumentException.class, () -> input(1, 0, 65535, 0));
        assertThrows(IllegalArgumentException.class, () -> positioned(state(0, 0, 0), 0, 0, 65536));
    }

    private static List<Result> spinContinuation(State checkpoint) {
        List<Result> results = new ArrayList<>();
        State state = checkpoint;
        for (int i = 0; i < 30; i++) {
            Result advance = beforeRoutineDispatch(state);
            results.add(advance);
            Result charge = moveRollAtNotRight(advance.state(), input(i == 29 ? 0 : 2, 0));
            results.add(charge);
            state = charge.state();
        }
        return List.copyOf(results);
    }

    private static State state(int charge, int inertia, int flags) {
        boolean rolling = (flags & 4) != 0;
        return new State(charge, inertia, 495, -49, flags, rolling ? 7 : 9,
                rolling ? 14 : 19, 0x076A_0042, 5, 0, 0x60);
    }
    private static State positioned(State s, int y, int look, int camera) {
        return new State(s.charge(), s.groundVelocity(), s.storedXVelocity(), s.storedYVelocity(),
                s.flags(), s.xRadius(), s.yRadius(), y, s.animation(), look, camera);
    }
    private static Input input(int held, int tap) { return input(held, tap, 1536, 0); }
    private static Input input(int held, int tap, int top, int shoes) {
        return new Input(NORMAL, held, tap, top, shoes, 0, 0, 0);
    }
    private static List<Sound> sounds(Result result) {
        return result.effects().stream().filter(SoundRequest.class::isInstance)
                .map(SoundRequest.class::cast).map(SoundRequest::sound).toList();
    }
}
