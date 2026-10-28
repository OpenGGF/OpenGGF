package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdObjectState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Literal R11A Main/Collision Check/Object Functions expectations. */
class TestCdPlayerMovement {
    @Test void idleRunsActualBalanceChargeAndGroundOwnersWithoutCopyingMainTailAngles() {
        Fixture f = new Fixture();
        Actor a = new Actor(); a.pri = 0x12; a.sec = 0x34;
        var r = f.step(a, 0, 0);
        assertEquals(0x00400000, r.state().centreX16_16());
        assertEquals(0x004C0000, r.state().centreY16_16());
        assertEquals(5, r.state().animation());
        assertEquals(0, r.state().flags());
        assertEquals(0x12, r.state().previousPrimaryAngle());
        assertEquals(0x34, r.state().previousSecondaryAngle());
        assertEquals(0, r.environment().globals().primaryAngle());
        assertEquals(0, r.environment().globals().secondaryAngle());
        assertEquals(CdPlayerMovement.Boundary.MODE_RETURN, r.boundary());
        assertTrue(r.effects().contains(new CdPlayerMovement.ChargeEffect(
                new CdPlayerCharge.SoundRequest(CdPlayerCharge.Sound.FM_CHARGESTOP))));
    }

    @Test void rightAccelerationKeepsEveryFractionBitAndGroundAlignmentDoesNotClearIt() {
        Fixture f = new Fixture(); Actor a = new Actor();
        a.x = 0x00401234; a.y = 0x004CABCD;
        var r = f.step(a, 8, 0).state();
        assertEquals(12, r.groundVelocity()); assertEquals(12, r.xVelocity());
        assertEquals(0, r.yVelocity());
        assertEquals(0x00401E34, r.centreX16_16());
        assertEquals(0x004CABCD, r.centreY16_16());
    }

    @Test void leftThenRightAreSeparateCallsAndSkidDoesNotAutomaticallyChangeFacing() {
        Actor a = new Actor(); a.flags = 0x20;
        var r = new Fixture().step(a, 12, 0).state();
        assertEquals(0x80, r.groundVelocity()); // Left -12; Right ADD.W $80 carries, crossing -> +$80.
        assertEquals(1, r.previousAnimation());
        assertEquals(1, r.flags() & 1); // Right .Skid does not face right at this low speed.
        assertEquals(0, r.flags() & 0x20);
    }

    @ParameterizedTest @CsvSource({"5,0", "12,0", "13,1", "-5,0", "-12,0", "-13,-1"})
    void noDirectionFrictionStopsOnUnsignedCarryBorrow(int velocity, int expected) {
        Actor a = new Actor(); a.gv = velocity;
        assertEquals(expected, new Fixture().step(a, 0, 0).state().groundVelocity());
    }

    @ParameterizedTest @CsvSource({"1023,4,895,0", "1152,4,1024,13", "-1152,8,-1024,13"})
    void brakingRetainsNativeRegisterHighByteDuringAngleTests(int velocity, int held,
                                                              int expectedVelocity, int expectedAnimation) {
        Actor a = new Actor(); a.gv = velocity;
        var r = new Fixture().step(a, held, 0).state();
        assertEquals(expectedVelocity, r.groundVelocity());
        assertEquals(expectedAnimation, r.animation());
    }

    @Test void nativeWordAccelerationOverflowIsSignedBeforeTopSpeedComparison() {
        Fixture f = new Fixture(); f.physics = new CdPlayerMovement.Physics(0x7FFF, 12, 0x80);
        Actor a = new Actor(); a.x = 0x1000 << 16; a.gv = 0x7FFB;
        var r = f.step(a, 8, 0).state();
        assertEquals(-32761, r.groundVelocity());
        assertEquals(-32761, r.xVelocity());
    }

    @Test void jumpPopsTheModeReturnBeforeSlopeMovementBoundsIntegrationOrGroundCollision() {
        Actor a = new Actor(); a.x = 0x00401234; a.y = 0x004CABCD; a.lock = 9;
        var r = new Fixture().step(a, 0x10, 0x10);
        assertEquals(CdPlayerMovement.Boundary.JUMP_MODE_RETURN, r.boundary());
        assertEquals(0x00401234, r.state().centreX16_16());
        assertEquals(0x0051ABCD, r.state().centreY16_16());
        assertEquals(-0x680, r.state().yVelocity());
        assertEquals(6, r.state().flags()); assertEquals(1, r.state().playerJump());
        assertEquals(7, r.state().xRadius()); assertEquals(14, r.state().yRadius());
        assertEquals(9, r.state().moveLock());
        assertEquals(0x80, r.environment().globals().primaryAngle());
        assertEquals(0x80, r.environment().globals().secondaryAngle());
        assertTrue(r.effects().contains(new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_JUMP)));
    }

    @Test void blockedHeadroomAndStationaryUpDownDoNotBecomeJumps() {
        Fixture f = new Fixture(); f.row(3, 0x5003);
        assertEquals(CdPlayerMovement.Boundary.MODE_RETURN, f.step(new Actor(), 0x10, 0x10).boundary());
        var up = new Fixture().step(new Actor(), 0x11, 0x10);
        assertEquals(0, up.state().playerJump()); assertEquals(1, up.state().charge());
    }

    @Test void rollJumpKeepsRadiusAndYButMarksRollJump() {
        Actor a = rolled(); a.gv = 0x200;
        var r = new Fixture().step(a, 0x10, 0x10).state();
        assertEquals(a.y, r.centreY16_16()); assertEquals(0x16, r.flags());
        assertEquals(-0x680, r.yVelocity());
    }

    @ParameterizedTest @CsvSource({"0,1883887,-968", "16,1720047,-1608"})
    void jumpReleaseCapsBeforeOldVelocityIntegrationAndGravity(int held, int expectedYPosition, int finalVelocity) {
        Actor a = airborne(); a.y = 0x0020BEEF; a.yv = -0x680; a.jump = 1;
        var r = new Fixture().step(a, held, 0).state();
        assertEquals(expectedYPosition, r.centreY16_16()); // $001CBEEF / $001A3EEF, prior to gravity.
        assertEquals(finalVelocity, r.yVelocity());
    }

    @Test void nonJumpUpwardCapAndDownwardCapDoNotChangeTheIntegratedOldVelocity() {
        Fixture f = new Fixture(false);
        Actor up = airborne(); up.y = 0x02001234; up.yv = -0x2000;
        var u = f.step(up, 0, 0).state();
        assertEquals(0x01F05234, u.centreY16_16()); assertEquals(-3976, u.yVelocity());
        Actor down = airborne(); down.y = 0x0200ABCD; down.yv = 0x1100;
        var d = f.step(down, 0, 0).state();
        assertEquals(0x0211ABCD, d.centreY16_16()); assertEquals(0x1000, d.yVelocity());
    }

    @Test void airControlDragUsesUnsignedYComparisonAndSignedArithmeticShift() {
        Fixture f = new Fixture(false); Actor a = airborne(); a.y = 0x02000000;
        a.xv = -33; a.yv = -0x400; a.jump = 1;
        var r = f.step(a, 0x10, 0).state();
        assertEquals(-31, r.xVelocity()); // -33 ASR.W 5 = -2, not truncation toward zero.
        a.yv = 0; a.xv = 100;
        assertEquals(100, f.step(a, 0, 0).state().xVelocity()); // 0 unsigned < $FC00: no drag.
        a.yv = -0x401;
        assertEquals(100, f.step(a, 0x10, 0).state().xVelocity());
    }

    @Test void downAirLandingResizesAfterAlignmentAndPreservesFractionsAndStoredActorAngles() {
        Actor a = airborne(); a.y = 0x0052ABCD; a.yv = 0x100; a.xv = 0x80; a.jump = 1;
        a.pri = 0x26; a.sec = 0x64;
        var r = new Fixture().step(a, 0, 0);
        assertEquals(0x004CABCD, r.state().centreY16_16());
        assertEquals(0, r.state().flags()); assertEquals(0, r.state().playerJump());
        assertEquals(19, r.state().yRadius()); assertEquals(9, r.state().xRadius());
        assertEquals(0, r.state().yVelocity()); assertEquals(0x80, r.state().groundVelocity());
        assertEquals(0, r.environment().globals().scoreChain());
        assertEquals(0x26, r.state().previousPrimaryAngle()); assertEquals(0x64, r.state().previousSecondaryAngle());
    }

    @Test void zeroFloorDistanceDoesNotLandBeforeTheSpeedThreshold() {
        Actor zero = airborne(); zero.y = 80 << 16; zero.yv = 0x100;
        var z = new Fixture().step(zero, 0, 0);
        assertEquals(6, z.state().flags()); assertEquals(81 << 16, z.state().centreY16_16());
        assertEquals(0, z.environment().globals().debugFloorDistance());
    }

    @ParameterizedTest @CsvSource({"256,4980736", "448,5029888"})
    void postGravity0138And01F8ShareHighMemoryByteLandingThreshold(int oldYVelocity, int expectedYPosition) {
        // Old $0100/$01C0 integrate to $00530000/$0053C000; gravity stores $0138/$01F8.
        // Main.asm:2093 reads MEMORY byte $01 in BOTH rows, producing threshold -9.
        // Both real floor sensors return -2, so both land; integer corrections retain the fraction.
        Actor a = airborne(); a.y = 82 << 16; a.yv = oldYVelocity; a.jump = 1;
        var r = new Fixture().step(a, 0, 0);
        assertEquals(expectedYPosition, r.state().centreY16_16()); // $004C0000 / $004CC000.
        assertEquals(0, r.state().flags()); assertEquals(0, r.state().playerJump());
        assertEquals(19, r.state().yRadius()); assertEquals(9, r.state().xRadius());
        assertEquals(0, r.state().yVelocity()); assertEquals(0, r.state().groundVelocity());
        assertEquals(0, r.environment().globals().debugQuadrant());
        assertEquals(0xFE, r.environment().globals().debugFloorDistance());
        assertEquals(0, r.environment().globals().scoreChain());
        assertTrue(r.effects().contains(new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_CHARGESTOP)));
    }

    @Test void bothFloorSensorsBelowNegativeNineRejectLanding() {
        Fixture f = new Fixture(); Arrays.fill(f.heights, 16, 32, (byte) 24);
        // Both sensors: Y97, height24 -> distance -10. Stored $0138 reads memory byte $01 -> -9.
        Actor a = airborne(); a.y = 82 << 16; a.yv = 0x100; a.jump = 1;
        var r = f.step(a, 0, 0);
        assertEquals(0x00530000, r.state().centreY16_16()); assertEquals(0x138, r.state().yVelocity());
        assertEquals(6, r.state().flags()); assertEquals(1, r.state().playerJump());
        assertEquals(14, r.state().yRadius()); assertEquals(7, r.state().xRadius());
        assertEquals(0, r.environment().globals().debugQuadrant());
        assertEquals(0xF6, r.environment().globals().debugFloorDistance());
        assertEquals(9, r.environment().globals().scoreChain()); assertTrue(r.effects().isEmpty());
    }

    @Test void rightAirWallCollisionReturnsBeforeFloorAndKeepsAirStatus() {
        Fixture f = new Fixture(false); f.column(6, 0x4002);
        Actor a = airborne(); a.x = 84 << 16; a.y = 70 << 16; a.xv = 0x300; a.yv = 0;
        var r = f.step(a, 0, 0).state();
        assertEquals(85 << 16, r.centreX16_16()); assertEquals(0, r.xVelocity());
        assertEquals(0x38, r.groundVelocity()); assertEquals(6, r.flags());
    }

    @Test void flatCeilingStopsVerticalVelocityWithoutPretendingToLand() {
        Fixture f = new Fixture(false); f.row(2, 0x5003);
        Actor a = airborne(); a.y = 63 << 16; a.yv = -0x200; a.jump = 1;
        var r = f.step(a, 0x10, 0).state();
        assertEquals(62 << 16, r.centreY16_16()); assertEquals(0, r.yVelocity());
        assertEquals(6, r.flags()); assertEquals(1, r.playerJump());
    }

    @ParameterizedTest @CsvSource({"140,512,128,4227072", "-140,-128,-128,4161536"})
    void rollEntryAsymmetryHappensAfterGroundProjection(int initial, int finalInertia, int integratedXVelocity, int expectedXPosition) {
        Actor a = new Actor(); a.gv = initial;
        var r = new Fixture().step(a, 2, 0).state();
        assertEquals(finalInertia, r.groundVelocity()); assertEquals(integratedXVelocity, r.xVelocity());
        assertEquals(expectedXPosition, r.centreX16_16()); // $00408000 / $003F8000.
        assertEquals(81 << 16, r.centreY16_16()); assertEquals(4, r.flags() & 4);
    }

    @Test void rollStopExecutesCalcXYAndRemainingModeTailInsteadOfStoppingAtChargeHelper() {
        Actor a = rolled(); a.gv = 5; a.y |= 0xBEEF;
        var r = new Fixture().step(a, 0, 0).state();
        assertEquals(0, r.groundVelocity()); assertEquals(0, r.flags());
        assertEquals(0x004CBEEF, r.centreY16_16()); assertEquals(0, r.xVelocity());
        assertEquals(5, r.animation());
    }

    @Test void heldPeeloutRetainsStaleXYAndGroundModeStillIntegratesIt() {
        Actor a = new Actor(); a.charge = 20; a.gv = 1900; a.xv = 0x100;
        var r = new Fixture().step(a, 1, 0).state();
        assertEquals(20, r.charge()); assertEquals(2000, r.groundVelocity());
        assertEquals(0x100, r.xVelocity()); assertEquals(65 << 16, r.centreX16_16());
    }

    @Test void heldSpinDoesNotIntegrateButStillRunsGroundCollisionAndFalloffLock() {
        Actor a = rolled(); a.charge = 20; a.gv = 1800; a.xv = 0x100; a.lock = 3;
        var r = new Fixture().step(a, 2, 0).state();
        assertEquals(20, r.charge()); assertEquals(1875, r.groundVelocity());
        assertEquals(64 << 16, r.centreX16_16()); assertEquals(2, r.moveLock());
    }

    @Test void matureSpinReleaseExecutesFacingRoutineFrictionProjectionAndIntegration() {
        Actor a = rolled(); a.charge = 45; a.gv = 0xC00; a.xv = 33;
        var r = new Fixture().step(a, 0, 0).state();
        assertEquals(0, r.charge()); assertEquals(3066, r.groundVelocity());
        assertEquals(3066, r.xVelocity()); assertEquals(0x004BFA00, r.centreX16_16());
    }

    @Test void earlySpinReleaseUnrollsThenContinuesCollisionWithoutAnExtraSettleSound() {
        Actor a = rolled(); a.charge = 44; a.gv = 3000; a.xv = 44; a.yv = 5;
        var r = new Fixture().step(a, 0, 0);
        assertEquals(0, r.state().charge()); assertEquals(0, r.state().groundVelocity());
        assertEquals(0, r.state().flags()); assertEquals(76 << 16, r.state().centreY16_16());
        assertEquals(1, r.effects().stream().filter(e -> e.equals(new CdPlayerMovement.ChargeEffect(
                new CdPlayerCharge.SoundRequest(CdPlayerCharge.Sound.FM_CHARGESTOP)))).count());
    }

    @Test void movingLockBypassesDirectionAndStillSettlesAndDecrementsOnlyAtFalloff() {
        Actor a = new Actor(); a.lock = 2; a.gv = 0x200;
        var r = new Fixture().step(a, 4, 0).state();
        assertEquals(0x200, r.groundVelocity()); assertEquals(1, r.moveLock());
        assertEquals(0, r.flags() & 1);
    }

    @Test void groundLedgeUsesPreviousActorAngleBytesForBalanceAndDoesNotCopyCurrentProbeBytes() {
        Fixture f = new Fixture(false); Actor a = new Actor(); a.pri = 3; a.sec = 0;
        var r = f.step(a, 0, 0);
        assertEquals(6, r.state().animation()); assertEquals(2, r.state().flags() & 2);
        assertEquals(3, r.state().previousPrimaryAngle()); assertEquals(3, r.environment().globals().secondaryAngle());
    }

    @Test void steepLowSpeedInstallsThirtyFrameLockAfterGroundAlignmentWithoutImmediateDecrement() {
        Fixture f = new Fixture(false); f.column(3, 0x2802); f.angles[2] = (byte) 0xC0;
        Actor a = new Actor(); a.x = 83 << 16; a.y = 70 << 16; a.angle = 0x40;
        var r = f.step(a, 0, 0).state();
        assertEquals(0, r.groundVelocity()); assertEquals(30, r.moveLock());
        assertEquals(2, r.flags() & 2);
    }

    @Test void nativeSideBoundsClearFractionBeforeIntegrationAndBottomStopsAtExactKillCall() {
        Fixture f = new Fixture(false); Actor a = airborne(); a.x = 0x0010FFFF; a.xv = -0x100;
        a.y = 100 << 16;
        var side = f.step(a, 0, 0).state();
        assertEquals(16 << 16, side.centreX16_16()); assertEquals(0, side.xVelocity());
        a = airborne(); a.y = 0x1000 << 16;
        var kill = f.step(a, 0, 0);
        assertEquals(CdPlayerMovement.Boundary.KILL_PLAYER, kill.boundary());
        assertEquals(CdPlayerMovement.Continuation.MD_JUMP_AFTER_LEVEL_BOUND, kill.continuation());
        assertEquals(a.y, kill.state().centreY16_16()); assertEquals(0, kill.state().yVelocity());
        assertTrue(kill.effects().contains(new CdPlayerMovement.KillRequest()));
    }

    @Test void boredomActivationIsModeLocalButVintIncrementRemainsCallerOwned() {
        Actor a = new Actor(); a.anim = 5; Fixture f = new Fixture();
        assertEquals(1, f.step(a, 0, 0).environment().globals().boredTimer());
        f.globals = globals(0, 0x60, 0, 0, 10799, 9);
        assertEquals(10799, f.step(a, 0, 0).environment().globals().boredTimer());
        f.globals = globals(0, 0x60, 0, 0, 10800, 9);
        assertBoundary(f, a, CdPlayerMovement.UnsupportedBoundary.BORED_GIVE_UP);
    }

    @ParameterizedTest @CsvSource({"0,0,0,0", "4,384,0,4", "0,384,2,4"})
    void rampGroundIdleRollAndRollEntryKeepTheNativeChargeCalls(int flags, int velocity, int held, int expectedRoll) {
        Fixture f = new Fixture(false); f.fg[2 + 2 * 42 + 3] = 6;
        Actor a = new Actor(); a.x = 0x03010000; a.y = 0x0A010000; a.flags = flags; a.gv = velocity;
        var result = f.step(a, held, 0);
        assertEquals(CdPlayerMovement.Boundary.KILL_PLAYER, result.boundary(), "the unchanged bottom cut follows movement");
        assertEquals(2, result.state().playerControl()); assertEquals(expectedRoll, result.state().flags() & 4);
    }

    @Test void nativeRampGateReadsDataRawChunkAndHighXByteWithY380Mask() {
        Fixture f = new Fixture(false); f.fg[2 + 2 * 42 + 3] = 6;
        Actor a = new Actor(); a.x = 0x03010000; a.y = 0x0A010000;
        a.ctrl = 0xA0;
        assertEquals(0xA2, f.step(a, 0, 0).state().playerControl());
        f.fg[2 + 2 * 42 + 3] = (byte) 0x86;
        assertEquals(0xA0, f.step(a, 0, 0).state().playerControl()); // Raw ID high bit is not masked.
        f.fg[2 + 2 * 42 + 3] = 6;
        f.ramps[0] = 9; f.ramps[1] = 10; f.ramps[2] = 11; f.ramps[3] = 12; f.ramps[4] = 13;
        assertEquals(0xA0, f.step(a, 0, 0).state().playerControl()); // No hardcoded ramp table substitute.
    }

    @Test void timewarpGateIsAfterChargeGateAndHasNoInventedSafeFlag() {
        Fixture f = new Fixture(); f.gates = new CdPlayerMovement.Gates(0, 1, 0, 0, 0, 0, 1);
        assertBoundary(f, new Actor(), CdPlayerMovement.UnsupportedBoundary.TIME_WARP_ACTIVE);
        Actor charging = new Actor(); charging.charge = 20; charging.gv = 1900;
        assertDoesNotThrow(() -> f.step(charging, 1, 0));
    }

    @Test void unsupportedEntriesAreBoundedAndFailedProbePublishesNothing() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.flags = 8;
        assertBoundary(f, a, CdPlayerMovement.UnsupportedBoundary.OBJECT_SUPPORT);
        a.flags = 0; a.ctrl = 1;
        assertBoundary(f, a, CdPlayerMovement.UnsupportedBoundary.OBJECT_CONTROL);
        a.ctrl = 0; a.routine = 4;
        assertBoundary(f, a, CdPlayerMovement.UnsupportedBoundary.ROUTINE);
        a.routine = 2; a.spr = 0x44; a.x = 256 << 16; f.fg[3] = (byte) 0xF9;
        var original = a.state(); var env = f.environment(); var owner = f.owner();
        assertThrows(IllegalStateException.class, () -> owner.step(original, new CdPlayerMovement.Input(8, 0), env));
        assertEquals(original, a.state()); assertEquals(env, f.environment());
    }

    @Test void immutableCaptureRestoreAndInterleavedOwnersReplayTheSameActualTerrainSequence() {
        Fixture f = new Fixture(); var owner = f.owner(); var initial = new Actor().state(); var env = f.environment();
        var saved = owner.step(initial, new CdPlayerMovement.Input(8, 0), env);
        var first = owner.step(saved.state(), new CdPlayerMovement.Input(0x10, 0x10), saved.environment());
        owner.step(initial, new CdPlayerMovement.Input(4, 0), env);
        assertEquals(first, owner.step(saved.state(), new CdPlayerMovement.Input(0x10, 0x10), saved.environment()));
        assertThrows(UnsupportedOperationException.class, () -> first.effects().clear());
        assertEquals(initial, new Actor().state());
    }

    @Test void balanceProbeLeavesSolidityThirteenInTheGroundSettleAccelerationRegister() {
        Actor peel = new Actor(); peel.charge = 30; peel.gv = 2000;
        var released = new Fixture().step(peel, 0, 0).state();
        assertEquals(1987, released.groundVelocity()); assertEquals(1987, released.xVelocity());
        peel.gv = 3072;
        var full = new Fixture().step(peel, 0, 0);
        assertEquals(3059, full.state().groundVelocity()); assertEquals(3059, full.state().xVelocity());
        assertEquals(12, full.environment().physics().acceleration()); // d5 clobber is not a global write.
        Actor spin = new Actor();
        var started = new Fixture().step(spin, 2, 0x10).state();
        assertEquals(1, started.charge()); assertEquals(512, started.groundVelocity()); // CheckRoll calls StartRoll again after projection.
        assertEquals(499, started.xVelocity()); assertEquals(0x0041F300, started.centreX16_16());
        assertEquals(81 << 16, started.centreY16_16());
    }

    @Test void leftSpindashEntryUsesNegativeTwentyTwoThenThirteenFrictionWithoutPositiveRollFloor() {
        Actor a = new Actor(); a.flags = 1;
        var r = new Fixture().step(a, 2, 0x10).state();
        assertEquals(-9, r.groundVelocity()); assertEquals(-9, r.xVelocity());
        assertEquals(5, r.flags()); assertEquals(1, r.charge());
    }

    @Test void movingWithoutChargeBypassesBalanceAndRetainsConfiguredFriction() {
        Fixture f = new Fixture(); f.physics = new CdPlayerMovement.Physics(0x600, 7, 0x80);
        Actor a = new Actor(); a.gv = 2000;
        assertEquals(1993, f.step(a, 0, 0).state().groundVelocity());
    }

    @Test void modeAirResetsWalkingAnimationButModeJumpKeepsItAndWindTunnelBreathingGateWins() {
        Fixture f = new Fixture(false); Actor a = new Actor(); a.flags = 2; a.anim = 7; a.y = 200 << 16;
        assertEquals(0, f.step(a, 0, 0).state().animation());
        a.flags = 6;
        assertEquals(7, f.step(a, 0, 0).state().animation());
        a.flags = 2; a.anim = 0x15;
        assertEquals(0x15, f.step(a, 0, 0).state().animation());
        a.anim = 7; f.gates = new CdPlayerMovement.Gates(0, 1, 0, 0, 1, 0, 0);
        assertEquals(7, f.step(a, 0, 0).state().animation());
    }

    @Test void actualWaterFlagChangesJumpReleaseAndPostGravityButUsesCallerPhysicsWords() {
        Actor a = new Actor(); a.flags = 0x40;
        var jump = new Fixture().step(a, 0x10, 0x10).state();
        assertEquals(-0x380, jump.yVelocity()); assertEquals(0x46, jump.flags());
        a = airborne(); a.flags |= 0x40; a.y = 0x00201234; a.yv = -0x380; a.jump = 1;
        var air = new Fixture().step(a, 0, 0).state();
        assertEquals(0x001E1234, air.centreY16_16()); assertEquals(-496, air.yVelocity());
    }

    @Test void nativePpAirLaunchWindowReturnsBeforeAirControlCameraAndDrag() {
        Fixture f = new Fixture(false); f.globals = globals(0x80, 80, 0, 0, 0, 9);
        Actor a = airborne(); a.x = 0x6C8 << 16; a.y = 200 << 16; a.yv = -0x100; a.xv = 0x400;
        var r = f.step(a, 4, 0);
        assertEquals(0x400, r.state().xVelocity()); assertEquals(80, r.environment().globals().cameraYCentre());
        assertEquals(0, r.state().flags() & 1);
        a.x = 0x840 << 16;
        var edge = f.step(a, 4, 0);
        assertEquals(969, edge.state().xVelocity()); // 1024-24=1000; ASR 5=31, then drag.
        assertEquals(82, edge.environment().globals().cameraYCentre());
    }

    @Test void actualTrigAssetsDecideJumpAndProjectionWithoutSharedTableFallback() {
        Fixture f = new Fixture(); f.sine(0x40, 128); f.sine(0xC0, -128);
        Actor a = new Actor(); a.gv = 0x200;
        assertEquals(250, f.step(a, 0, 0).state().xVelocity()); // (512-12)*128 >>8.
        assertEquals(-0x340, f.step(new Actor(), 0x10, 0x10).state().yVelocity());
    }

    @Test void rollProjectionCapsOnlyXAfterSignedWordNarrowingAndStillAppliesModeTail() {
        Fixture f = new Fixture(false); Actor a = rolled(); a.gv = 0x1806; a.y = 200 << 16;
        var r = f.step(a, 0, 0).state();
        assertEquals(0x1800, r.groundVelocity()); assertEquals(0x1000, r.xVelocity());
        assertEquals(80 << 16, r.centreX16_16()); assertEquals(6, r.flags());
    }

    @Test void leftAirWallUsesNativeIntegerCorrectionBeforeReturning() {
        Fixture f = new Fixture(false); f.column(3, 0x4802);
        Actor a = airborne(); a.x = 76 << 16; a.y = 70 << 16; a.xv = -0x300;
        var left = f.step(a, 0, 0).state();
        assertEquals(74 << 16, left.centreX16_16()); assertEquals(0, left.xVelocity());
        assertEquals(0x38, left.groundVelocity()); assertEquals(6, left.flags());
    }

    @Test void downLandingOnSteepSlopeCapsYAndSetsSignedGroundSpeedWithoutZeroingIt() {
        Fixture f = new Fixture(); f.angles[1] = 0x40;
        Actor a = airborne(); a.y = 67 << 16; a.yv = 0xFC8;
        var r = f.step(a, 0, 0).state();
        assertEquals(0xFC0, r.yVelocity()); assertEquals(0xFC0, r.groundVelocity());
        assertEquals(0x40, r.angle()); assertEquals(0, r.xVelocity()); assertEquals(0, r.flags());
    }

    @Test void partialSlopeHalvesLandingYAndNegativeAngleNegatesGroundSpeed() {
        Fixture f = new Fixture(); f.angles[1] = (byte) 0xE0;
        Actor a = airborne(); a.y = 82 << 16; a.yv = 0x100;
        var r = f.step(a, 0, 0).state();
        assertEquals(156, r.yVelocity()); assertEquals(-156, r.groundVelocity()); assertEquals(0xE0, r.angle());
    }

    @Test void steepCeilingAttachmentKeepsRollingRadiiAndDoesNotSettleLikeFloorLanding() {
        Fixture f = new Fixture(false); f.row(2, 0x5003); f.angles[3] = 0x40;
        Actor a = airborne(); a.y = 63 << 16; a.yv = -0x200; a.jump = 1;
        var r = f.step(a, 0x10, 0).state();
        assertEquals(0x40, r.angle()); assertEquals(4, r.flags());
        assertEquals(14, r.yRadius()); assertEquals(62 << 16, r.centreY16_16());
        assertEquals(-456, r.groundVelocity()); assertEquals(-456, r.yVelocity()); assertEquals(0, r.playerJump());
    }

    @Test void stickBypassesExistingFalloffLockDecrementAndGroundStillOwnsAlignment() {
        Actor a = new Actor(); a.lock = -32768; a.stick = 0x80;
        assertEquals(-32768, new Fixture().step(a, 0, 0).state().moveLock());
        a.stick = 0;
        assertEquals(32767, new Fixture().step(a, 0, 0).state().moveLock());
    }

    @Test void nativeWordCoordinateWrapAndFullFractionCarryComeFromObjMoveNotCoordinateClamp() {
        Fixture f = new Fixture(false); f.bounds = new CdPlayerMovement.Bounds(0xFFF0, 0x1000, 0x7F00, 0);
        Actor a = airborne(); a.x = 0xFFFFFEFF; a.y = 0xFFFFBEEF; a.xv = 2; a.yv = 1;
        var r = f.step(a, 0, 0).state();
        assertEquals(0x000000FF, r.centreX16_16()); assertEquals(0xFFFFBFEF, r.centreY16_16());
    }

    @Test void modeRestoreAcrossLandingReplaysAllOwnedGlobalsAndEffectsNotOnlyActorPosition() {
        Fixture f = new Fixture(); var owner = f.owner(); Actor a = airborne(); a.y = 82 << 16; a.yv = 0x100;
        var state = a.state(); var env = f.environment();
        var first = owner.step(state, new CdPlayerMovement.Input(0, 0), env);
        assertEquals(first, owner.step(state, new CdPlayerMovement.Input(0, 0), env));
        assertEquals(first.state().previousPrimaryAngle(), state.previousPrimaryAngle());
        assertEquals(9, env.globals().scoreChain()); assertEquals(0, first.environment().globals().scoreChain());
    }

    @Test void secondFloorDistanceCanAdmitLandingWhenTheChosenDistanceFailsTheByteThreshold() {
        Fixture f = new Fixture(); f.block(4, 6, 0x6004); f.ids[4] = 4;
        Arrays.fill(f.heights, 64, 80, (byte) 127);
        Actor a = airborne(); a.y = 82 << 16; a.yv = 0x100;
        var r = f.step(a, 0, 0).state();
        assertEquals(0xFFDD0000, r.centreY16_16()); // -113 chosen, -2 other; high MEMORY byte $01 gives threshold -9.
        assertEquals(0, r.flags()); assertEquals(0, r.yVelocity());
    }

    @Test void sideAirFloorLandingPreservesFractionThroughWordOnlyCorrections() {
        Actor a = airborne(); a.y = 82 << 16; a.yv = 0x1C0; a.xv = 0x800;
        var r = new Fixture().step(a, 0, 0);
        assertEquals(0xC0, r.environment().globals().debugQuadrant());
        assertEquals(0x004CC000, r.state().centreY16_16()); assertEquals(0, r.state().flags());
        assertEquals(0x800, r.state().groundVelocity()); assertEquals(0, r.state().yVelocity());
    }

    @Test void rollCapUsesLowSignedProductWordRatherThanTheWiderPositiveProduct() {
        Fixture f = new Fixture(false); f.physics = new CdPlayerMovement.Physics(0x600, 0, 0x80);
        Actor a = rolled(); a.x = 0x1000 << 16; a.y = 200 << 16; a.gv = -32768; a.angle = 0x80;
        var r = f.step(a, 0, 0).state();
        assertEquals(-0x1000, r.xVelocity()); assertEquals(-32768, r.groundVelocity());
    }

    @ParameterizedTest @CsvSource({"0,0", "1,0", "2,0", "3,1", "127,125", "128,130", "254,0", "255,0"})
    void jumpAngleUsesByteSignedBranchAndCarryBorrow(int angle, int expected) {
        Actor a = airborne(); a.y = 200 << 16; a.angle = angle;
        assertEquals(expected, new Fixture(false).step(a, 0, 0).state().angle());
    }

    @Test void nativeStateAndEnvironmentRejectOutOfWidthValuesButKeepAllPositionBits() {
        Actor a = new Actor(); a.x = Integer.MIN_VALUE; a.y = Integer.MAX_VALUE;
        assertEquals(Integer.MIN_VALUE, a.state().centreX16_16());
        assertEquals(Integer.MAX_VALUE, a.state().centreY16_16());
        a.xv = 32768;
        assertThrows(IllegalArgumentException.class, a::state);
        a.xv = 0; a.pri = 256;
        assertThrows(IllegalArgumentException.class, a::state);
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.Input(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.Physics(32768, 12, 128));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.Bounds(0, 0x10000, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> globals(0, 0x10000, 0, 0, 0, 0));
    }

    @Test void gravityAddsAsWordBeforeSignedDownwardCapSoPositiveOverflowRemainsNegative() {
        Actor a = airborne(); a.y = 200 << 16; a.yv = 32760;
        var r = new Fixture(false).step(a, 0, 0).state();
        assertEquals(0x0147F800, r.centreY16_16()); assertEquals(-32720, r.yVelocity());
    }

    @Test void bossBoundsUseNativeRightEqualityAndDoNotApplyTheNonbossFiftySixPixelExtension() {
        Fixture f = new Fixture(false); f.bounds = new CdPlayerMovement.Bounds(0, 64, 0x710, 2);
        Actor a = airborne(); a.x = 0x016FFFFF; a.y = 200 << 16; a.xv = 2;
        var boss = f.step(a, 0, 0).state();
        assertEquals(368 << 16, boss.centreX16_16()); assertEquals(0, boss.xVelocity());
        f.bounds = new CdPlayerMovement.Bounds(0, 64, 0x710, 0);
        assertEquals(0x017001FF, f.step(a, 0, 0).state().centreX16_16());
    }

    @ParameterizedTest @CsvSource({"0,MD_GROUND_AFTER_LEVEL_BOUND,268435456", "2,MD_AIR_AFTER_LEVEL_BOUND,268435456",
            "4,MD_ROLL_AFTER_LEVEL_BOUND,268107776", "6,MD_JUMP_AFTER_LEVEL_BOUND,268435456"})
    void killContinuationNamesTheOriginalModeAndStopsBeforeItsIntegrationTail(
            int mode, CdPlayerMovement.Continuation expected, int expectedYPosition) {
        Actor a = (mode & 4) != 0 ? rolled() : new Actor(); a.flags = mode; a.y = 0x1000 << 16;
        var r = new Fixture(false).step(a, 0, 0);
        assertEquals(CdPlayerMovement.Boundary.KILL_PLAYER, r.boundary()); assertEquals(expected, r.continuation());
        assertEquals(expectedYPosition, r.state().centreY16_16()); // Roll unrolls to $0FFB0000 before LevelBound.
        assertEquals(0, r.state().yVelocity());
        if (mode == 4) {
            assertEquals(0, r.state().flags()); assertEquals(19, r.state().yRadius()); assertEquals(9, r.state().xRadius());
            assertEquals(5, r.state().animation());
            assertEquals(2, r.effects().size());
            assertEquals(new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_CHARGESTOP), r.effects().get(0));
            assertEquals(new CdPlayerMovement.KillRequest(), r.effects().get(1));
        }
    }

    @Test void fractionCarryCrossesActualLayoutChunkAndGroundChoosesTheNewResidentMaterial() {
        Fixture f = new Fixture(false); f.fg[3] = 3; f.ids[4] = 4;
        Arrays.fill(f.heights, 64, 80, (byte) 12);
        for (int x = 0; x < 16; x++) word(f.chunks, 1024 + (6 * 16 + x) * 2, 0x6004);
        Actor a = new Actor(); a.x = 0x00FFFFFF; a.gv = 0x200;
        var r = f.step(a, 8, 0);
        assertEquals(0x01020BFF, r.state().centreX16_16()); assertEquals(80 << 16, r.state().centreY16_16());
        assertEquals(0, r.state().flags()); assertEquals(0, r.environment().globals().primaryAngle());
        assertEquals(3, r.environment().globals().secondaryAngle());
    }

    @Test void lookPriorityUsesChargeOwnerCameraWritesThenCompletesGroundSettleAndMode() {
        Fixture f = new Fixture(); f.globals = globals(0x80, 0xC6, 0, 0, 0, 9);
        Actor a = new Actor(); a.charge = 30; a.gv = 3072;
        var r = f.step(a, 1, 0);
        assertEquals(0xC8, r.environment().globals().cameraYCentre()); assertEquals(7, r.state().animation());
        assertEquals(30, r.state().charge()); assertEquals(3059, r.state().groundVelocity());
        assertEquals(0x004BF300, r.state().centreX16_16()); assertEquals(CdPlayerMovement.Boundary.MODE_RETURN, r.boundary());
    }

    // Shared Player_ResetOnFloor (Sonic/Main.asm:2295-2330). Written before the extraction; UNRUN by the external author.
    @Test void sharedResetOnFloorNormalBranchUnrollsByWordAndRequestsOneChargeStopLeavingEveryOtherFieldAlone() {
        var before = new CdPlayerMotionState(0x12345678, 0x0003ABCD, -0x123, 0x456, -0x789, 4, 0x9A, 0xFF, 0xBC,
                7, 14, 2, 0x1D, 1, 0x2E, -2, 0x80, 0x3F, 0x41, 0x52);
        var reset = CdPlayerMovement.resetOnFloor(before, 0);
        // BCLR 5/1/4 then 2 ($FF -> $C9); $13/9 radii; SUBQ.W #5 wraps only the integer word and keeps the fraction.
        assertEquals(new CdPlayerMotionState(0x12345678, 0xFFFEABCD, -0x123, 0x456, -0x789, 4, 0x9A, 0xC9, 0xBC,
                9, 0x13, 0, 0x1D, 0, 0x2E, -2, 0x80, 0x3F, 0x41, 0x52), reset.motion());
        assertEquals(0, reset.scoreChain());
        assertEquals(List.of(new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_CHARGESTOP)), reset.sounds());
        assertThrows(UnsupportedOperationException.class, () -> reset.sounds().clear());
    }

    @ParameterizedTest @CsvSource({"1", "128", "255"})
    void sharedResetOnFloorMiniatureBranchUsesTenFiveRadiiAndATwoPixelWordLift(int miniSonic) {
        var before = new CdPlayerMotionState(0x00400000, 0x0001BEEF, 0, 0x300, 0, 2, 0, 0x16, 4,
                7, 14, 2, 0, 1, 0, 0, 0, 0, 0, 0);
        var reset = CdPlayerMovement.resetOnFloor(before, miniSonic); // TST.B miniSonic: any nonzero byte.
        assertEquals(new CdPlayerMotionState(0x00400000, 0xFFFFBEEF, 0, 0x300, 0, 2, 0, 0, 4,
                5, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0, 0), reset.motion());
        assertEquals(0, reset.scoreChain());
        assertEquals(List.of(new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_CHARGESTOP)), reset.sounds());
    }

    @ParameterizedTest @CsvSource({"0", "1"})
    void sharedResetOnFloorWithoutBitTwoClearsOnlyStatusJumpAndChainWithNoSound(int miniSonic) {
        var before = new CdPlayerMotionState(0x00400000, 0x00501234, 5, 6, 7, 2, 0x20, 0xFB, 4,
                9, 0x13, 0x1A, 3, 1, 9, 1, 2, 0, 0x11, 0x22);
        var reset = CdPlayerMovement.resetOnFloor(before, miniSonic);
        // BTST #4 / NOP has no effect; $FB loses bits 5/1/4 only. No radius, Y or animation write.
        assertEquals(new CdPlayerMotionState(0x00400000, 0x00501234, 5, 6, 7, 2, 0x20, 0xC9, 4,
                9, 0x13, 0x1A, 3, 0, 9, 1, 2, 0, 0x11, 0x22), reset.motion());
        assertEquals(0, reset.scoreChain()); assertTrue(reset.sounds().isEmpty());
    }

    @Test void sharedResetOnFloorRejectsNullMotionAndNonByteMiniatureFlag() {
        var state = new Actor().state();
        assertThrows(NullPointerException.class, () -> CdPlayerMovement.resetOnFloor(null, 0));
        assertThrows(IllegalArgumentException.class, () -> CdPlayerMovement.resetOnFloor(state, 256));
        assertThrows(IllegalArgumentException.class, () -> CdPlayerMovement.resetOnFloor(state, -1));
        assertThrows(NullPointerException.class, () -> new CdPlayerMovement.ResetOnFloorResult(null, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.ResetOnFloorResult(state, 0x10000, List.of()));
    }

    @Test void modeDownAirLandingRunsTheSharedFloorResetOnceWithCompleteStateAndExactEffectOrder() {
        Actor a = airborne(); a.y = 0x0052ABCD; a.yv = 0x100; a.xv = 0x80; a.jump = 1; a.pri = 0x26; a.sec = 0x64;
        var r = new Fixture().step(a, 0, 0);
        assertEquals(new CdPlayerMotionState(0x00408000, 0x004CABCD, 0x80, 0, 0x80, 2, 0, 0, 4,
                9, 19, 0, 0, 0, 0, 0, 0, 0, 0x26, 0x64), r.state());
        assertEquals(List.of(new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_CHARGESTOP)), r.effects());
        assertEquals(0, r.environment().globals().scoreChain());
    }

    @Test void modeSideAirLandingUsesTheSameSharedResetAndEmitsItsChargeStopExactlyOnce() {
        Actor a = airborne(); a.y = 82 << 16; a.yv = 0x1C0; a.xv = 0x800;
        var r = new Fixture().step(a, 0, 0);
        assertEquals(0xC0, r.environment().globals().debugQuadrant());
        assertEquals(0, r.state().flags()); assertEquals(0, r.state().animation());
        assertEquals(19, r.state().yRadius()); assertEquals(9, r.state().xRadius());
        assertEquals(List.of(new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_CHARGESTOP)), r.effects());
    }

    @Test void steepCeilingAttachmentKeepsTheSeparateSteepResetWithoutFloorUnrollOrChargeStop() {
        // .LandSteepSlope (2228-2235) calls Player_ResetOnSteepSlope (2339-2345), which has no bit-2 branch.
        Fixture f = new Fixture(false); f.row(2, 0x5003); f.angles[3] = 0x40;
        Actor a = airborne(); a.y = 63 << 16; a.yv = -0x200; a.jump = 1;
        var r = f.step(a, 0x10, 0);
        assertEquals(4, r.state().flags()); assertEquals(7, r.state().xRadius()); assertEquals(14, r.state().yRadius());
        assertEquals(2, r.state().animation()); assertEquals(62 << 16, r.state().centreY16_16());
        assertEquals(0, r.state().playerJump()); assertEquals(0, r.environment().globals().scoreChain());
        assertTrue(r.effects().isEmpty());
    }

    @Test void miniatureModeStillRejectsBeforeTheSharedFloorResetCanBeReached() {
        Fixture f = new Fixture(); f.gates = new CdPlayerMovement.Gates(0, 1, 1, 0, 0, 0, 0);
        Actor a = airborne(); a.y = 0x0052ABCD; a.yv = 0x100;
        assertBoundary(f, a, CdPlayerMovement.UnsupportedBoundary.MINIATURE);
    }

    private static void assertBoundary(Fixture f, Actor a, CdPlayerMovement.UnsupportedBoundary boundary) {
        var ex = assertThrows(CdPlayerMovement.UnsupportedMotion.class, () -> f.step(a, 0, 0));
        assertEquals(boundary, ex.boundary());
    }
    private static Actor rolled() { Actor a = new Actor(); a.flags = 4; a.xr = 7; a.yr = 14; a.y = 81 << 16; a.anim = 2; return a; }
    @Test void hurtAirCollisionUsesTheExistingFloorOwnerWithoutIntegratingOrRunningMode() {
        var f = new Fixture(); Actor a = airborne(); a.routine = 4;
        a.y = 0x0053ABCD; a.yv = 0x138; a.xv = 0x80; a.jump = 1; a.charge = 17;
        var r = f.owner().collideInAir(a.state(), f.environment());
        assertEquals(0x00400000, r.state().centreX16_16(), "the caller already executed ObjMove");
        assertEquals(0x004CABCD, r.state().centreY16_16());
        assertEquals(4, r.state().routine()); assertEquals(0, r.state().flags());
        assertEquals(0, r.state().yVelocity()); assertEquals(0x80, r.state().groundVelocity());
        assertEquals(17, r.state().charge(), "no predispatch or charge helper in the collision call");
        assertEquals(0, r.environment().globals().scoreChain());
        assertEquals(List.of(new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_CHARGESTOP)), r.effects());
    }

    @Test void directAirCollisionStillReadsTheHighMemoryYVelocityByteForTheLandingThreshold() {
        Fixture f = new Fixture(); Arrays.fill(f.heights, 16, 32, (byte) 24);
        Actor a = airborne(); a.routine = 4; a.y = 0x00530000; a.yv = 0x138; a.jump = 1;
        var r = f.owner().collideInAir(a.state(), f.environment());
        assertEquals(a.state(), r.state(), "two -10 sensors are below the high-memory-byte threshold -9");
        assertEquals(0xF6, r.environment().globals().debugFloorDistance());
        assertEquals(9, r.environment().globals().scoreChain()); assertTrue(r.effects().isEmpty());
    }

    @Test void directCeilingCollisionUsesTheExistingSteepResetAndPreservesRoutineFour() {
        Fixture f = new Fixture(false); f.row(2, 0x5003); f.angles[3] = 0x40;
        Actor a = airborne(); a.routine = 4; a.y = 61 << 16; a.yv = -0x1C8; a.jump = 1;
        var r = f.owner().collideInAir(a.state(), f.environment());
        assertEquals(62 << 16, r.state().centreY16_16());
        assertEquals(0x40, r.state().angle()); assertEquals(4, r.state().flags());
        assertEquals(14, r.state().yRadius()); assertEquals(7, r.state().xRadius());
        assertEquals(2, r.state().animation()); assertEquals(4, r.state().routine());
        assertEquals(-0x1C8, r.state().groundVelocity()); assertTrue(r.effects().isEmpty());
    }

    @Test void directLevelBoundsStopsAtTheKillCallAndNeverIntegratesAnActor() {
        Fixture f = new Fixture(false); Actor a = airborne(); a.routine = 4;
        a.x = 0x0010FFFF; a.xv = -0x100; a.y = 100 << 16;
        var side = CdPlayerMovement.applyLevelBounds(a.state(), f.environment());
        assertEquals(16 << 16, side.state().centreX16_16());
        assertEquals(a.y, side.state().centreY16_16()); assertEquals(0, side.state().xVelocity());
        assertEquals(4, side.state().routine()); assertFalse(side.killRequested());
        a.x = 64 << 16; a.xv = 0x100; a.y = 0x1000ABCD;
        var kill = CdPlayerMovement.applyLevelBounds(a.state(), f.environment());
        assertTrue(kill.killRequested()); assertEquals(a.state(), kill.state());
        assertEquals(f.environment(), kill.environment());
    }

    @Test void directLevelBoundKeepsItsSignedBottomCompareDistinctFromHurtChkLandUnsignedCompare() {
        Fixture f = new Fixture(false); f.bounds = new CdPlayerMovement.Bounds(0, 0x1000, 0x7F10, 0);
        Actor a = airborne(); a.routine = 4; a.y = 0x80001234;
        assertFalse(CdPlayerMovement.applyLevelBounds(a.state(), f.environment()).killRequested());
        a.y = 0x7FF11234;
        assertTrue(CdPlayerMovement.applyLevelBounds(a.state(), f.environment()).killRequested());
    }

    @Test void directLevelBoundsRejectsTheUnownedBoredDeathBranchOnlyWhenBottomIsCrossed() {
        Fixture f = new Fixture(false); Actor a = airborne(); a.routine = 4; a.anim = 0x2B;
        a.y = 100 << 16;
        assertFalse(CdPlayerMovement.applyLevelBounds(a.state(), f.environment()).killRequested());
        a.y = 0x1000ABCD;
        var failure = assertThrows(CdPlayerMovement.UnsupportedMotion.class,
                () -> CdPlayerMovement.applyLevelBounds(a.state(), f.environment()));
        assertEquals(CdPlayerMovement.UnsupportedBoundary.BORED_GIVE_UP, failure.boundary());
    }

    @Test void collisionAndBoundsPortsRejectNullInputsAndKeepTheirOutputListsImmutable() {
        Fixture f = new Fixture(false); Actor a = airborne(); a.routine = 4; a.y = 200 << 16;
        var owner = f.owner(); var r = owner.collideInAir(a.state(), f.environment());
        assertThrows(UnsupportedOperationException.class, () -> r.effects().clear());
        assertThrows(NullPointerException.class, () -> owner.collideInAir(null, f.environment()));
        assertThrows(NullPointerException.class, () -> owner.collideInAir(a.state(), null));
        assertThrows(NullPointerException.class, () -> CdPlayerMovement.applyLevelBounds(null, f.environment()));
        assertThrows(NullPointerException.class, () -> CdPlayerMovement.applyLevelBounds(a.state(), null));
    }

    // KillPlayer's RTS into the stopped MODE: LevelBound .Bottom BNE.W KillPlayer (Sonic/Main.asm:1703-1705)
    // runs PP Player Object Collision.asm:279-298, whose RTS lands after BSR.W ObjSonic_LevelBound.
    // These tests play Body: ONE shared CdPlayerTouchResponse.killPlayer at the stop, then the resume.
    // Written before resumeAfterLevelBound; UNRUN by the external author.

    @Test void airKillReturnRunsObjMoveGrvOnTheKilledWordsThenJumpAngleAndUpwardAirCollisionOnly() {
        Fixture f = new Fixture(false); var owner = f.owner();
        var stopped = owner.step(airPit().state(), new CdPlayerMovement.Input(0, 0), f.environment());
        // MdAir resets oAnim (713-720); signed $0710+224 = $07F0 < $07F1 branches to KillPlayer.
        assertEquals(new CdPlayerMotionState(0x00400000, 0x07F18000, 0x123, 0x300, 0, 2, 0xFF, 2, 4, 9, 19,
                0, 0, 0, 0, 0, 0, 0, 0x26, 0x64), stopped.state());
        assertEquals(CdPlayerMovement.Continuation.MD_AIR_AFTER_LEVEL_BOUND, stopped.continuation());
        assertEquals(List.of(new CdPlayerMovement.KillRequest()), stopped.effects());
        var kill = killAt(stopped, 0);
        // 282-298: routine 6, ResetOnFloor without bit 2, bit 1, -$700/0/0, oY word $07F1 split into $38/$39.
        var killed = new CdPlayerMotionState(0x00400000, 0x07F18000, 0, -0x700, 0, 6, 0xFF, 2, 4, 9, 19,
                0x18, 0, 0, 0, 0, 0x07, 0, 0x26, 0x64);
        assertEquals(new CdPlayerTouchResponse.KillResult(new CdPlayerTouchResponse.Actor(killed, 0x8780, 0, 0x33, 0xF1),
                0, 0, List.of(new CdPlayerTouchResponse.ResetOnFloor(),
                new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_HURT)),
                CdPlayerTouchResponse.Exit.KILL_PLAYER), kill);
        var r = resume(owner, stopped, kill);
        // 728 ObjMoveGrv: Y adds the OLD -$700 ($07F1.8000 -> $07EA.8000), stores $F938, BMI skips the cap.
        // 734 ADDQ.B #2 carries $FF to zero. 735 CalcAngle(0,-$6C8) = $C0: shift $A0, quadrant $80; blank probes.
        assertEquals(new CdPlayerMotionState(0x00400000, 0x07EA8000, 0, -0x6C8, 0, 6, 0, 2, 4, 9, 19,
                0x18, 0, 0, 0, 0, 0x07, 0, 0x26, 0x64), r.state());
        assertEquals(new CdPlayerMovement.Globals(0, 0x60, 0, 0, 0, 0, 0xC0, 0xA0, 0x80, 0), r.environment().globals());
        assertEquals(stopped.environment().physics(), r.environment().physics());
        assertEquals(stopped.environment().bounds(), r.environment().bounds());
        assertEquals(CdPlayerMovement.Boundary.MODE_RETURN, r.boundary());
        assertEquals(CdPlayerMovement.Continuation.NONE, r.continuation());
        assertTrue(r.effects().isEmpty(), "tail only: neither KillRequest nor KillPlayer's calls repeat");
    }

    @Test void airDebugNoWriteReturnContinuesThePreKillWordsThroughGravityAndDownwardCollision() {
        Fixture f = new Fixture(false); var owner = f.owner();
        var stopped = owner.step(airPit().state(), new CdPlayerMovement.Input(0, 0), f.environment());
        // ObjSonic tests only debugMode's high byte (56-73); KillPlayer's TST.W sees the whole word $0001 (280-281).
        var kill = killAt(stopped, 1);
        assertEquals(CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE, kill.exit());
        assertEquals(stopped.state(), kill.actor().motion());
        assertTrue(kill.calls().isEmpty()); assertEquals(9, kill.scoreChain()); assertEquals(1, kill.invincible());
        var r = resume(owner, stopped, kill);
        // ObjMoveGrv: X +$123<<8, Y +$300<<8 (OLD word), stores $338. CalcAngle($123,$338): $12300/$338 = 90,
        // fixture atan[90] = 11, $40-11 = $35; shift $15, quadrant 0. Blank floor sensors at Y $0807 return 24.
        assertEquals(new CdPlayerMotionState(0x00412300, 0x07F48000, 0x123, 0x338, 0, 2, 0, 2, 4, 9, 19,
                0, 0, 0, 0, 0, 0, 0, 0x26, 0x64), r.state());
        assertEquals(new CdPlayerMovement.Globals(0, 0x60, 0, 0, 0, 9, 0x35, 0x15, 0, 0x18), r.environment().globals());
        assertEquals(CdPlayerMovement.Boundary.MODE_RETURN, r.boundary()); assertTrue(r.effects().isEmpty());
    }

    @Test void groundKillReturnIntegratesKilledWordsAndTheKillPlayerStickByteAlignsGroundColAndSkipsFalloffLock() {
        Fixture f = new Fixture(false); var owner = f.owner();
        var stopped = owner.step(groundPit().state(), new CdPlayerMovement.Input(0, 0), f.environment());
        // Locked MoveGround settles $100 by 12 and projects; CalcRoomInFront leaves $C0 in both angle buffers.
        assertEquals(new CdPlayerMotionState(0x00401234, 0x07F1ABCD, 244, 0, 244, 2, 0, 0, 4, 9, 19,
                0, 0, 0, 0, 5, 0, 0, 0x26, 0x64), stopped.state());
        assertEquals(new CdPlayerMovement.Globals(0, 0x60, 0xC0, 0xC0, 0, 9, 0, 0, 0, 0), stopped.environment().globals());
        assertEquals(CdPlayerMovement.Continuation.MD_GROUND_AFTER_LEVEL_BOUND, stopped.continuation());
        assertEquals(List.of(new CdPlayerMovement.KillRequest()), stopped.effects());
        var kill = killAt(stopped, 0);
        assertEquals(new CdPlayerMotionState(0x00401234, 0x07F1ABCD, 0, -0x700, 0, 6, 0, 2, 4, 9, 19,
                0x18, 0, 0, 0, 5, 0x07, 0, 0x26, 0x64), kill.actor().motion());
        assertEquals(0xF1, kill.actor().rotationDistance());
        var r = resume(owner, stopped, kill);
        // 702 ObjMove: X unchanged, Y -7. 703 GroundCol ignores the new air bit; blank sensors at Y $07FD return 18.
        // Above 14, .CheckStick (Collision Floor.asm:134-136) reads oPlayerStick $07, the high byte of KillPlayer's
        // MOVE.W oY, and aligns +18. 704 CheckFallOff (1976-1977) returns on it before the move-lock decrement.
        assertEquals(new CdPlayerMotionState(0x00401234, 0x07FCABCD, 0, -0x700, 0, 6, 0, 2, 4, 9, 19,
                0x18, 0, 0, 0, 5, 0x07, 0, 0x26, 0x64), r.state());
        assertEquals(new CdPlayerMovement.Globals(0, 0x60, 3, 3, 0, 0, 0, 0, 0, 0), r.environment().globals());
        assertEquals(CdPlayerMovement.Boundary.MODE_RETURN, r.boundary()); assertTrue(r.effects().isEmpty());
    }

    @Test void groundDebugNoWriteReturnIntegratesTheOldSpeedAndAZeroStickLedgeFallsOffAndDecrementsTheLock() {
        Fixture f = new Fixture(false); var owner = f.owner();
        var stopped = owner.step(groundPit().state(), new CdPlayerMovement.Input(0, 0), f.environment());
        var kill = killAt(stopped, 1);
        assertEquals(stopped.state(), kill.actor().motion());
        var r = resume(owner, stopped, kill);
        // ObjMove adds 244<<8. Blank sensors at Y $0804 return 27; stick 0 takes the fall-off writes (138-140).
        assertEquals(new CdPlayerMotionState(0x00410634, 0x07F1ABCD, 244, 0, 244, 2, 0, 2, 4, 9, 19,
                0, 1, 0, 0, 4, 0, 0, 0x26, 0x64), r.state());
        assertEquals(new CdPlayerMovement.Globals(0, 0x60, 3, 3, 0, 9, 0, 0, 0, 0), r.environment().globals());
        assertTrue(r.effects().isEmpty());
    }

    @Test void rollKillReturnTestsThePostKillChargeByteSoAHeldSpindashSkipsObjMoveButRunsGroundColAndFalloff() {
        Fixture f = new Fixture(false); f.bounds = new CdPlayerMovement.Bounds(0, 0x2000, 0, 0); var owner = f.owner();
        var stopped = owner.step(heldSpinPit().state(), new CdPlayerMovement.Input(2, 0), f.environment());
        // Held DOWN: the charge helper's two ADD.W/cap writes (+75), then MOVE_ROLL_RETURN before Settle/CalcXY.
        assertEquals(new CdPlayerMotionState(0x00400000, 0x00F08000, 0x100, 0, 1875, 2, 0, 4, 4, 7, 14,
                2, 0, 0, 20, 3, 0, 0, 0x26, 0x64), stopped.state());
        var write = new CdPlayerMovement.ChargeEffect(
                new CdPlayerCharge.ActorWrite(CdPlayerCharge.ActorField.GROUND_VELOCITY, 1875));
        assertEquals(List.of(write, write, new CdPlayerMovement.KillRequest()), stopped.effects());
        assertEquals(CdPlayerMovement.Continuation.MD_ROLL_AFTER_LEVEL_BOUND, stopped.continuation());
        var kill = killAt(stopped, 0);
        // ResetOnFloor clears bit 2 (Y -5 = $00EB, radii $13/9, FM_CHARGESTOP); oPlayerCharge is not written.
        assertEquals(new CdPlayerMotionState(0x00400000, 0x00EB8000, 0, -0x700, 0, 6, 0, 2, 4, 9, 0x13,
                0x18, 0, 0, 20, 3, 0, 0, 0x26, 0x64), kill.actor().motion());
        assertEquals(List.of(new CdPlayerTouchResponse.ResetOnFloor(),
                new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP),
                new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_HURT)), kill.calls());
        var r = resume(owner, stopped, kill);
        // 749-750 TST.B oPlayerCharge = 20: no ObjMove despite -$700 and the post-kill air flags.
        // 754 GroundCol: blank sensors at Y $00FE return 17; stick 0 falls off (oPrevAnim 1). 755: lock 3 -> 2.
        assertEquals(new CdPlayerMotionState(0x00400000, 0x00EB8000, 0, -0x700, 0, 6, 0, 2, 4, 9, 0x13,
                0x18, 1, 0, 20, 2, 0, 0, 0x26, 0x64), r.state());
        assertEquals(new CdPlayerMovement.Globals(0, 0x60, 3, 3, 0, 0, 0, 0, 0, 0), r.environment().globals());
        assertTrue(r.effects().isEmpty());
    }

    @Test void rollKillAfterThePrefixUnrolledStillReturnsIntoTheRollTailAndIntegratesWithZeroCharge() {
        Fixture f = new Fixture(false); f.bounds = new CdPlayerMovement.Bounds(0, 0x2000, 0, 0); var owner = f.owner();
        Actor a = rolled(); a.y = 0x00F51234; a.gv = 5; a.pri = 0x26; a.sec = 0x64;
        var stopped = owner.step(a.state(), new CdPlayerMovement.Input(0, 0), f.environment());
        // .Settle 5 by 6 borrows to zero: FM_CHARGESTOP and .StopRolling to Y $00F0 (1443-1486), then LevelBound.
        assertEquals(new CdPlayerMotionState(0x00400000, 0x00F01234, 0, 0, 0, 2, 0, 0, 4, 9, 19,
                5, 0, 0, 0, 0, 0, 0, 0x26, 0x64), stopped.state());
        assertEquals(List.of(new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_CHARGESTOP),
                new CdPlayerMovement.KillRequest()), stopped.effects());
        assertEquals(CdPlayerMovement.Continuation.MD_ROLL_AFTER_LEVEL_BOUND, stopped.continuation());
        var kill = killAt(stopped, 0);
        assertEquals(List.of(new CdPlayerTouchResponse.ResetOnFloor(),
                new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_HURT)), kill.calls());
        var r = resume(owner, stopped, kill);
        // 751 ObjMove with zero charge: Y -7 = $00E9. Sensors at Y $00FC return 19; stick 0 falls off.
        assertEquals(new CdPlayerMotionState(0x00400000, 0x00E91234, 0, -0x700, 0, 6, 0, 2, 4, 9, 19,
                0x18, 1, 0, 0, 0, 0, 0, 0x26, 0x64), r.state());
        assertEquals(new CdPlayerMovement.Globals(0, 0x60, 3, 3, 0, 0, 0, 0, 0, 0), r.environment().globals());
        assertTrue(r.effects().isEmpty());
    }

    @Test void rollDebugNoWriteReturnKeepsRollingRadiiAndTheHeldChargeStillSkipsObjMove() {
        Fixture f = new Fixture(false); f.bounds = new CdPlayerMovement.Bounds(0, 0x2000, 0, 0); var owner = f.owner();
        var stopped = owner.step(heldSpinPit().state(), new CdPlayerMovement.Input(2, 0), f.environment());
        var kill = killAt(stopped, 1);
        assertEquals(stopped.state(), kill.actor().motion());
        var r = resume(owner, stopped, kill);
        // Charge 20 skips ObjMove (X keeps despite oXVel $100); radii 7/14 put sensors at Y $00FE: 17, fall off.
        assertEquals(new CdPlayerMotionState(0x00400000, 0x00F08000, 0x100, 0, 1875, 2, 0, 6, 4, 7, 14,
                2, 1, 0, 20, 2, 0, 0, 0x26, 0x64), r.state());
        assertEquals(new CdPlayerMovement.Globals(0, 0x60, 3, 3, 0, 9, 0, 0, 0, 0), r.environment().globals());
        assertTrue(r.effects().isEmpty(), "the prefix's charge writes stay in the stopped result");
    }

    @Test void jumpKillReturnUnderwaterAppliesGravityThenWaterResistanceAndAPositiveJumpAngleStep() {
        Fixture f = new Fixture(false); var owner = f.owner();
        Actor a = airborne(); a.flags = 0x46; a.y = 0x07F1BEEF; a.yv = 0x200; a.jump = 1; a.angle = 0x41;
        a.pri = 0x26; a.sec = 0x64;
        var stopped = owner.step(a.state(), new CdPlayerMovement.Input(0, 0), f.environment());
        assertEquals(a.state(), stopped.state()); // MdJump: no oAnim reset; JumpHeight/MoveAir leave this actor.
        assertEquals(CdPlayerMovement.Continuation.MD_JUMP_AFTER_LEVEL_BOUND, stopped.continuation());
        var kill = killAt(stopped, 0);
        // ResetOnFloor: $46 -> $44 -> $40, Y -5 = $07EC, radii $13/9; KillPlayer BSET 1 gives $42. Bit 6 survives.
        assertEquals(new CdPlayerMotionState(0x00400000, 0x07ECBEEF, 0, -0x700, 0, 6, 0x41, 0x42, 4, 9, 0x13,
                0x18, 0, 0, 0, 0, 0x07, 0, 0x26, 0x64), kill.actor().motion());
        assertEquals(0xEC, kill.actor().rotationDistance());
        var r = resume(owner, stopped, kill);
        // 768 ObjMoveGrv: Y -7 = $07E5, yv $F938; 769-771 bit 6 SUBI.W #$28: $F910; 774 SUBQ.B #2: $41 -> $3F.
        assertEquals(new CdPlayerMotionState(0x00400000, 0x07E5BEEF, 0, -0x6F0, 0, 6, 0x3F, 0x42, 4, 9, 0x13,
                0x18, 0, 0, 0, 0, 0x07, 0, 0x26, 0x64), r.state());
        assertEquals(new CdPlayerMovement.Globals(0, 0x60, 0, 0, 0, 0, 0xC0, 0xA0, 0x80, 0), r.environment().globals());
        assertTrue(r.effects().isEmpty());
    }

    @Test void jumpDebugNoWriteReturnLandsThroughTheSharedFloorResetAndPublishesOnlyThatTailChargeStop() {
        Fixture f = new Fixture(); f.bounds = new CdPlayerMovement.Bounds(0, 0x2000, 0xFF20, 0);
        f.globals = globals(0, 0x60, 0x12, 0x34, 0, 9); var owner = f.owner();
        Actor a = airborne(); a.y = 82 << 16; a.yv = 0x100; a.jump = 1; a.pri = 0x26; a.sec = 0x64;
        var stopped = owner.step(a.state(), new CdPlayerMovement.Input(0, 0), f.environment());
        // $FF20+224 wraps to the signed word 0, so BLT kills at Y 82, above the actual floor.
        assertEquals(CdPlayerMovement.Continuation.MD_JUMP_AFTER_LEVEL_BOUND, stopped.continuation());
        assertEquals(a.state(), stopped.state());
        assertEquals(List.of(new CdPlayerMovement.KillRequest()), stopped.effects());
        var r = resume(owner, stopped, killAt(stopped, 1));
        // The ordinary $0138 landing: both real sensors -2, high-memory-byte threshold -9, then ResetOnFloor.
        assertEquals(new CdPlayerMotionState(0x00400000, 0x004C0000, 0, 0, 0, 2, 0, 0, 4, 9, 19,
                0, 0, 0, 0, 0, 0, 0, 0x26, 0x64), r.state());
        assertEquals(new CdPlayerMovement.Globals(0, 0x60, 0, 0, 0, 0, 0x40, 0x20, 0, 0xFE), r.environment().globals());
        assertEquals(List.of(new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_CHARGESTOP)), r.effects());
    }

    @ParameterizedTest @CsvSource({"0,MD_GROUND_AFTER_LEVEL_BOUND", "4,MD_ROLL_AFTER_LEVEL_BOUND",
            "2,MD_AIR_AFTER_LEVEL_BOUND", "6,MD_JUMP_AFTER_LEVEL_BOUND"})
    void debugNoWriteReturnRunsTheOrdinaryModeTailOnceWithoutReplayingItsPrefix(
            int mode, CdPlayerMovement.Continuation expected) {
        Fixture f = new Fixture(); var owner = f.owner();
        Actor a = (mode & 4) != 0 ? rolled() : new Actor(); a.flags = mode; a.gv = 0x200; a.xv = 0x123; a.yv = 0x100;
        if (mode == 6) a.y = 82 << 16;
        var input = new CdPlayerMovement.Input(8, 0); // RIGHT: a replayed prefix would accelerate twice.
        var ordinary = owner.step(a.state(), input, f.environment()); // Bottom $0710: LevelBound returns.
        f.bounds = new CdPlayerMovement.Bounds(0, 0x2000, 0xFF20, 0); // Same sides; only the bottom branch differs.
        var stopped = owner.step(a.state(), input, f.environment());
        assertEquals(CdPlayerMovement.Boundary.MODE_RETURN, ordinary.boundary());
        assertEquals(expected, stopped.continuation());
        var r = resume(owner, stopped, killAt(stopped, 1));
        assertEquals(ordinary.state(), r.state());
        assertEquals(ordinary.environment().globals(), r.environment().globals());
        var sourceOrder = new ArrayList<>(stopped.effects().subList(0, stopped.effects().size() - 1));
        sourceOrder.addAll(r.effects());
        assertEquals(ordinary.effects(), sourceOrder);
    }

    @Test void onlyAStoppedKillPlayerResumesSoNoModeTailRunsTwice() {
        Fixture f = new Fixture(); var owner = f.owner(); var env = f.environment();
        var completed = owner.step(new Actor().state(), new CdPlayerMovement.Input(0, 0), env);
        var jumped = owner.step(new Actor().state(), new CdPlayerMovement.Input(0x10, 0x10), env);
        assertEquals(CdPlayerMovement.Boundary.MODE_RETURN, completed.boundary());
        assertEquals(CdPlayerMovement.Boundary.JUMP_MODE_RETURN, jumped.boundary());
        for (var done : List.of(completed, jumped)) {
            assertEquals(CdPlayerMovement.Continuation.NONE, done.continuation());
            assertThrows(IllegalArgumentException.class,
                    () -> owner.resumeAfterLevelBound(done, done.state(), done.environment()));
        }
        Fixture pit = new Fixture(false); var pitOwner = pit.owner();
        var stopped = pitOwner.step(airPit().state(), new CdPlayerMovement.Input(0, 0), pit.environment());
        var resumed = resume(pitOwner, stopped, killAt(stopped, 0));
        assertThrows(IllegalArgumentException.class,
                () -> pitOwner.resumeAfterLevelBound(resumed, resumed.state(), resumed.environment()));
        // A stop never carries NONE: the existing Result invariant already rejects that shape.
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.Result(stopped.state(),
                stopped.environment(), stopped.effects(), CdPlayerMovement.Boundary.KILL_PLAYER,
                CdPlayerMovement.Continuation.NONE));
    }

    @Test void aStopMustEndAtItsSingleKillRequestAndItsPrefixEffectsAreNeverReplayed() {
        Fixture f = new Fixture(false); var owner = f.owner();
        var stopped = owner.step(airPit().state(), new CdPlayerMovement.Input(0, 0), f.environment());
        var kill = killAt(stopped, 0); var killed = kill.actor().motion();
        var env = withScoreChain(stopped.environment(), kill.scoreChain());
        var chargeStop = new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_CHARGESTOP);
        var killRequest = new CdPlayerMovement.KillRequest();
        for (List<CdPlayerMovement.Effect> effects : List.of(List.<CdPlayerMovement.Effect>of(),
                List.<CdPlayerMovement.Effect>of(chargeStop), List.<CdPlayerMovement.Effect>of(killRequest, killRequest),
                List.<CdPlayerMovement.Effect>of(killRequest, chargeStop))) {
            var malformed = new CdPlayerMovement.Result(stopped.state(), stopped.environment(), effects,
                    CdPlayerMovement.Boundary.KILL_PLAYER, stopped.continuation());
            assertThrows(IllegalArgumentException.class, () -> owner.resumeAfterLevelBound(malformed, killed, env));
        }
        var prefixed = new CdPlayerMovement.Result(stopped.state(), stopped.environment(),
                List.of(chargeStop, killRequest), CdPlayerMovement.Boundary.KILL_PLAYER, stopped.continuation());
        var r = owner.resumeAfterLevelBound(prefixed, killed, env);
        assertEquals(resume(owner, stopped, kill), r);
        assertTrue(r.effects().isEmpty());
    }

    @Test void returnedStateGuardCoversOnlyFieldsKillPlayerAndResetOnFloorNeverWrite() {
        Fixture f = new Fixture(false); var owner = f.owner();
        var stopped = owner.step(airPit().state(), new CdPlayerMovement.Input(0, 0), f.environment());
        var killed = killAt(stopped, 0).actor().motion(); var env = withScoreChain(stopped.environment(), 0);
        // CdPlayerMotionState components: X long, Y fraction, angle, oFlags bits 0/3/6/7, oSprFlags, oPrevAnim,
        // charge, move lock, oPlayerCtrl and both previous actor angle bytes are outside that write set.
        int[][] foreign = {{0, 0x00400001}, {1, 0x07F18001}, {6, 0xFE}, {7, 3}, {7, 0x0A}, {7, 0x42}, {7, 0x82},
                {8, 0x44}, {12, 1}, {14, 1}, {15, 1}, {17, 2}, {18, 0x27}, {19, 0x65}};
        for (int[] change : foreign) {
            var value = with(killed, change[0], change[1]);
            assertThrows(IllegalArgumentException.class, () -> owner.resumeAfterLevelBound(stopped, value, env),
                    "component " + change[0]);
        }
        // Written components are not policed: value consistency only, never proof that a KillPlayer call ran.
        int[][] written = {{1, 0x07F08000}, {2, 5}, {3, 0x123}, {4, -7}, {5, 8}, {7, 0x34}, {9, 7}, {10, 14},
                {11, 1}, {13, 1}, {16, 0x55}};
        for (int[] change : written) {
            var value = with(killed, change[0], change[1]);
            assertDoesNotThrow(() -> owner.resumeAfterLevelBound(stopped, value, env), "component " + change[0]);
        }
    }

    @Test void returnedEnvironmentMayDifferOnlyInTheScoreChainWordResetOnFloorWrites() {
        Fixture f = new Fixture(false); var owner = f.owner();
        var stopped = owner.step(airPit().state(), new CdPlayerMovement.Input(0, 0), f.environment());
        var killed = killAt(stopped, 0).actor().motion(); var e = stopped.environment(); var g = e.globals();
        var foreign = List.of(
                new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600, 13, 0x80), e.bounds(), e.gates(), g),
                new CdPlayerMovement.Environment(e.physics(), new CdPlayerMovement.Bounds(0, 0x2000, 0x711, 0), e.gates(), g),
                new CdPlayerMovement.Environment(e.physics(), e.bounds(), new CdPlayerMovement.Gates(0, 1, 1, 0, 0, 0, 0), g),
                new CdPlayerMovement.Environment(e.physics(), e.bounds(), e.gates(), new CdPlayerMovement.Globals(
                        g.lookMode(), g.cameraYCentre(), 3, g.secondaryAngle(), g.boredTimer(), 0, g.debugAngle(),
                        g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance())),
                new CdPlayerMovement.Environment(e.physics(), e.bounds(), e.gates(), new CdPlayerMovement.Globals(
                        g.lookMode(), g.cameraYCentre(), g.primaryAngle(), g.secondaryAngle(), g.boredTimer(), 0, 0x40,
                        g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance())));
        for (var environment : foreign) {
            assertThrows(IllegalArgumentException.class, () -> owner.resumeAfterLevelBound(stopped, killed, environment));
        }
        // The tail reads the RETURNED environment; this air tail writes no chain value of its own.
        var r = owner.resumeAfterLevelBound(stopped, killed, withScoreChain(e, 0x1234));
        assertEquals(0x1234, r.environment().globals().scoreChain());
    }

    @Test void resumeRejectsNullInputs() {
        Fixture f = new Fixture(false); var owner = f.owner();
        var stopped = owner.step(airPit().state(), new CdPlayerMovement.Input(0, 0), f.environment());
        var killed = killAt(stopped, 0).actor().motion(); var env = withScoreChain(stopped.environment(), 0);
        assertThrows(NullPointerException.class, () -> owner.resumeAfterLevelBound(null, killed, env));
        assertThrows(NullPointerException.class, () -> owner.resumeAfterLevelBound(stopped, null, env));
        assertThrows(NullPointerException.class, () -> owner.resumeAfterLevelBound(stopped, killed, null));
    }

    @Test void resumeIsAValueReplayWithoutIdentityTokensOrRetainedCallState() {
        Fixture f = new Fixture(); f.bounds = new CdPlayerMovement.Bounds(0, 0x2000, 0xFF20, 0); var owner = f.owner();
        Actor a = airborne(); a.y = 82 << 16; a.yv = 0x100; a.jump = 1;
        var initial = a.state(); var env = f.environment();
        var stopped = owner.step(initial, new CdPlayerMovement.Input(0, 0), env);
        var kill = killAt(stopped, 1);
        var first = resume(owner, stopped, kill);
        var rebuilt = new CdPlayerMovement.Result(stopped.state(), stopped.environment(), stopped.effects(),
                stopped.boundary(), stopped.continuation());
        owner.step(new Actor().state(), new CdPlayerMovement.Input(8, 0), env); // An interleaved owner call.
        assertEquals(first, owner.resumeAfterLevelBound(rebuilt, kill.actor().motion(), stopped.environment()));
        assertEquals(first, resume(f.owner(), stopped, kill)); // A fresh owner over the same immutable terrain.
        assertEquals(initial, stopped.state()); assertEquals(env, stopped.environment());
        assertThrows(UnsupportedOperationException.class, () -> first.effects().clear());
    }

    /** Caller-held oTile/oPriority/hurt/byte39/invincible bytes MODE never reads; the shared owner's ONE call. */
    private static CdPlayerTouchResponse.KillResult killAt(CdPlayerMovement.Result stopped, int debugMode) {
        var e = stopped.environment();
        return CdPlayerTouchResponse.killPlayer(new CdPlayerTouchResponse.Actor(stopped.state(), 0x0780, 2, 0x33, 0x5A),
                1, debugMode, e.globals().scoreChain(), e.gates().miniSonic());
    }
    /** Body's adoption: killed motion, and the Movement environment with KillResult's scoreChain word only. */
    private static CdPlayerMovement.Result resume(CdPlayerMovement owner, CdPlayerMovement.Result stopped,
                                                  CdPlayerTouchResponse.KillResult kill) {
        return owner.resumeAfterLevelBound(stopped, kill.actor().motion(),
                withScoreChain(stopped.environment(), kill.scoreChain()));
    }
    private static CdPlayerMovement.Environment withScoreChain(CdPlayerMovement.Environment e, int scoreChain) {
        var g = e.globals();
        return new CdPlayerMovement.Environment(e.physics(), e.bounds(), e.gates(), new CdPlayerMovement.Globals(
                g.lookMode(), g.cameraYCentre(), g.primaryAngle(), g.secondaryAngle(), g.boredTimer(), scoreChain,
                g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance()));
    }
    /** One record component replaced, in CdPlayerMotionState declaration order. */
    private static CdPlayerMotionState with(CdPlayerMotionState s, int component, int value) {
        int[] v = {s.centreX16_16(), s.centreY16_16(), s.xVelocity(), s.yVelocity(), s.groundVelocity(), s.routine(),
                s.angle(), s.flags(), s.spriteFlags(), s.xRadius(), s.yRadius(), s.animation(), s.previousAnimation(),
                s.playerJump(), s.charge(), s.moveLock(), s.stick(), s.playerControl(), s.previousPrimaryAngle(),
                s.previousSecondaryAngle()};
        v[component] = value;
        return new CdPlayerMotionState(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11],
                v[12], v[13], v[14], v[15], v[16], v[17], v[18], v[19]);
    }
    /** MdAir over a blank pit: bottom $0710's signed limit $07F0 is one pixel above Y. */
    private static Actor airPit() {
        Actor a = new Actor(); a.flags = 2; a.anim = 7; a.y = 0x07F18000; a.xv = 0x123; a.yv = 0x300;
        a.angle = 0xFF; a.pri = 0x26; a.sec = 0x64; return a;
    }
    /** Locked, moving MdGround at the same pit edge; the high Y byte $07 later becomes oPlayerStick. */
    private static Actor groundPit() {
        Actor a = new Actor(); a.x = 0x00401234; a.y = 0x07F1ABCD; a.gv = 0x100; a.lock = 5;
        a.pri = 0x26; a.sec = 0x64; return a;
    }
    /** Held spindash at Y $00F0 under bottom 0 (limit 224); Y below 256 leaves the stick byte zero. */
    private static Actor heldSpinPit() {
        Actor a = rolled(); a.y = 0x00F08000; a.charge = 20; a.gv = 1800; a.xv = 0x100; a.lock = 3;
        a.pri = 0x26; a.sec = 0x64; return a;
    }

    @Test void sharedFloorProbeReportsSameValueWritesToBothNativeAngleBuffers() {
        var result = new Fixture().owner().checkFloor(new Actor().state());
        assertEquals(0, result.distance()); // Feet95, full floor begins96: native distance96-95-1.
        assertEquals(java.util.OptionalInt.of(0), result.primaryAngleWrite());
        assertEquals(java.util.OptionalInt.of(0), result.secondaryAngleWrite());
    }

    @ParameterizedTest @CsvSource({"4,true,false", "3,false,true"})
    void floorProbeSeparatesTheTwoSensorWriteSets(int column, boolean primary, boolean secondary) {
        Fixture f = new Fixture(false); f.block(column, 6, 0x2001);
        var result = f.owner().checkFloor(new Actor().state());
        assertEquals(0, result.distance());
        assertEquals(primary ? java.util.OptionalInt.of(0) : java.util.OptionalInt.empty(), result.primaryAngleWrite());
        assertEquals(secondary ? java.util.OptionalInt.of(0) : java.util.OptionalInt.empty(), result.secondaryAngleWrite());
    }

    @Test void absentTerrainLeavesFloorAndCeilingBuffersUnwrittenButRoomSeedsBoth() {
        var owner = new Fixture(false).owner(); var actor = new Actor().state();
        var floor = owner.checkFloor(actor);
        assertEquals(16, floor.distance()); assertTrue(floor.primaryAngleWrite().isEmpty());
        assertTrue(floor.secondaryAngleWrite().isEmpty());
        var ceiling = owner.getCeilDist(actor);
        assertEquals(18, ceiling.distance()); assertTrue(ceiling.primaryAngleWrite().isEmpty());
        assertTrue(ceiling.secondaryAngleWrite().isEmpty());
        var room = owner.calcRoomInFront(actor, 8);
        assertEquals(25, room.distance());
        assertEquals(java.util.OptionalInt.of(8), room.primaryAngleWrite());
        assertEquals(java.util.OptionalInt.of(8), room.secondaryAngleWrite());
    }

    @Test void roomPredictionIncludesFractionCarryAndUsesTheExistingRightWallProbe() {
        Fixture f = new Fixture(false); f.column(5, 0x4002);
        Actor a = new Actor(); a.x = 0x003FFFFF; a.xv = 1;
        var result = f.owner().calcRoomInFront(a.state(), 0xC0);
        assertEquals(5, result.distance()); // PredictedX64, sensor74, full wall starts80:80-74-1.
        assertEquals(java.util.OptionalInt.of(0xC0), result.primaryAngleWrite());
        assertEquals(java.util.OptionalInt.of(0xC0), result.secondaryAngleWrite());
    }

    @Test void sharedCeilingProbeWritesPrimaryOnlyAtTheNativeTenPixelSensor() {
        Fixture f = new Fixture(false); f.row(3, 0x5001);
        Actor a = new Actor(); a.y = 80 << 16;
        var result = f.owner().getCeilDist(a.state());
        assertEquals(6, result.distance());
        assertEquals(java.util.OptionalInt.of(0x80), result.primaryAngleWrite());
        assertTrue(result.secondaryAngleWrite().isEmpty());
    }

    @Test void zeroMetricStillWritesTheAnglesBeforeReturningAnEmptyDistance() {
        Fixture f = new Fixture(); Arrays.fill(f.heights, 16, 32, (byte) 0);
        var result = f.owner().checkFloor(new Actor().state());
        assertEquals(16, result.distance());
        assertEquals(java.util.OptionalInt.of(0), result.primaryAngleWrite());
        assertEquals(java.util.OptionalInt.of(0), result.secondaryAngleWrite());
    }

    @Test void probePortsValidateTheirExplicitNativeInputs() {
        var owner = new Fixture().owner(); var actor = new Actor().state();
        assertThrows(NullPointerException.class, () -> owner.checkFloor(null));
        assertThrows(NullPointerException.class, () -> owner.getCeilDist(null));
        assertThrows(NullPointerException.class, () -> owner.calcRoomInFront(null, 0));
        assertThrows(IllegalArgumentException.class, () -> owner.calcRoomInFront(actor, -1));
        assertThrows(IllegalArgumentException.class, () -> owner.calcRoomInFront(actor, 256));
    }


    // R11A ramp/support unit: authored and frozen before production; ALL JAVA UNRUN.
    // Every byte fixture below is CONSTRUCTED. Source arithmetic is not live World evidence.

    @Test void constructedNativeLayoutRowsDistinguishRampNeighbourAndHeightWithoutAnXOnlyFence() {
        Fixture f = new Fixture(false);
        f.fg[2 + 7 * 42 + 6] = 6; f.fg[2 + 7 * 42 + 5] = 0x4B; f.fg[2 + 6] = 0;
        Actor a = airborne(); a.x = 0x600 << 16; a.y = 0x76B << 16; a.ctrl = 0x80;
        assertEquals(0x82, f.step(a, 0, 0).state().playerControl());
        a.x = 0x5FF << 16;
        assertEquals(0x80, f.step(a, 0, 0).state().playerControl());
        a.x = 0x600 << 16; a.y = 0x6B << 16;
        assertEquals(0x80, f.step(a, 0, 0).state().playerControl());
    }

    @ParameterizedTest @CsvSource({"-2049,0,130,1536", "-2049,1,130,-1536", "-2048,0,130,291",
            "-2047,0,130,291", "0,0,130,291", "256,0,130,291", "-2049,0,128,291"})
    void rampExitUsesOldBitAndUnsignedF800ThresholdBeforeTheStoppedTail(
            int yVelocity, int facing, int control, int expectedX) {
        Actor a = airborne(); a.flags |= facing; a.x = 0x840 << 16; a.y = 0xA01 << 16;
        a.ctrl = control; a.yv = yVelocity; a.xv = 0x123; a.jump = 1;
        var r = new Fixture(false).step(a, 0x10, 0);
        assertEquals(CdPlayerMovement.Boundary.KILL_PLAYER, r.boundary());
        assertEquals(expectedX, r.state().xVelocity()); assertEquals(0x80, r.state().playerControl());
    }

    @Test void rampMoveAirSkipsAccelerationAndFacingButKeepsCameraAndDragOutsideLaunchWindow() {
        Fixture f = new Fixture(false); f.fg[2 + 6] = 6; f.globals = globals(0x80, 80, 0, 0, 0, 9);
        Actor a = airborne(); a.x = 0x600 << 16; a.y = 200 << 16; a.xv = 0x400; a.yv = -0x100;
        var ramp = f.step(a, 4, 0);
        assertEquals(0x3E0, ramp.state().xVelocity()); assertEquals(0, ramp.state().flags() & 1);
        assertEquals(82, ramp.environment().globals().cameraYCentre());
        assertEquals(2, ramp.state().playerControl());
        f.fg[2 + 6] = 2;
        var plain = f.step(a, 4, 0);
        assertEquals(0x3C9, plain.state().xVelocity()); assertEquals(1, plain.state().flags() & 1);
        assertEquals(82, plain.environment().globals().cameraYCentre());
        f.fg[2 + 6] = 6; a.x = 0x6C8 << 16;
        var locked = f.step(a, 4, 0);
        assertEquals(0x400, locked.state().xVelocity()); assertEquals(80, locked.environment().globals().cameraYCentre());
        assertEquals(0, locked.state().flags() & 1);
        f.fg[2 + 8] = 6; a.x = 0x840 << 16;
        assertEquals(0x3E0, f.step(a, 4, 0).state().xVelocity());
    }

    @ParameterizedTest @CsvSource({"2,-3072,-3072,-786432", "2,-2048,-1992,-524288",
            "2,-2047,-1991,-524032", "2,3072,3128,786432", "0,4095,4096,1048320",
            "0,4096,4096,1048576", "0,32760,-32720,8386560", "4,-3072,-3072,-786432",
            "8,-2048,-2048,-524288", "12,4352,4096,1114112", "10,-3072,-3072,-786432",
            "6,-2048,-2048,-524288", "0,-32768,-32712,-8388608", "0,0,56,0"})
    void sharedGravityUsesOldWordsControlGatesSignedOverflowAndTheNewWordCap(
            int control, int oldY, int storedY, int literalDelta) {
        var before = new CdPlayerMotionState(0x05B01234, 0x01EDABCD, 0x180, oldY, -7, 6, 0x9A, 0xFF, 0xBC,
                7, 14, 0x1A, 0x1D, 1, 0x2E, -2, 0x80, control, 0x41, 0x52);
        var expected = with(with(with(before, 0, 0x05B19234), 1, 0x01EDABCD + literalDelta), 3, storedY);
        assertEquals(expected, CdPlayerMovement.moveWithGravity(before));
    }

    @Test void sharedMovementPreservesFractionCarryAndWrapWithoutChangingAnyUnownedField() {
        var before = new CdPlayerMotionState(0xFFFFFEFF, 0xFFFFBEEF, 2, 1, -7, 4, 0x9A, 0xC1, 0xBC,
                7, 14, 0x1A, 0x1D, 1, 0x2E, -2, 0x80, 0xFF, 0x41, 0x52);
        var expected = with(with(before, 0, 0x000000FF), 1, 0xFFFFBFEF);
        assertEquals(expected, CdPlayerMovement.moveWithoutGravity(before, Optional.empty()));
        assertEquals(expected, CdPlayerMovement.moveWithGravity(before)); // Pole/bar bits skip gravity.
    }

    @ParameterizedTest @CsvSource({"0,0,95523380", "18,0,95523380", "30,0,95457844", "30,1,95588916"})
    void objMoveReadsActualIdAndUsesFlipperLocalResistanceWithoutStoringXVelocity(int id, int flags, int expectedX) {
        var pool = new CdObjectPool(); putSupport(pool, 32, id, flags, 12, 0x5B0, 0x20C);
        Actor a = supported(); a.x = 0x05B01234; a.y = 0x01EDABCD; a.xv = 0x180; a.yv = -0x200;
        var before = a.state(); var cut = context(pool, 32);
        var r = CdPlayerMovement.moveWithoutGravity(before, cut);
        assertEquals(with(with(before, 0, expectedX), 1, 0x01EBABCD), r);
        assertEquals(0x180, r.xVelocity()); assertEquals(32, cut.orElseThrow().standObject());
    }

    @Test void objMoveFlipperLocalWordOverflowsBeforeSignExtension() {
        var pool = new CdObjectPool(); putSupport(pool, 127, 0x1E, 1, 12, 0x5B0, 0x20C);
        Actor a = supported(); a.x = 0x05B0FFFE; a.xv = 0x7FFF;
        var moved = CdPlayerMovement.moveWithoutGravity(a.state(), context(pool, 127));
        assertEquals(0x0531FEFE, moved.centreX16_16()); // local $80FF = -32513, not +33023.
        assertEquals(0x7FFF, moved.xVelocity());
    }

    @ParameterizedTest @CsvSource({"1,FFD040,RESERVED_OBJECT_SLOT", "31,FFD7C0,RESERVED_OBJECT_SLOT",
            "128,FFF000,OBJECT_ADDRESS", "191,FFFFC0,OBJECT_ADDRESS", "192,000000,OBJECT_ADDRESS",
            "255,000FC0,OBJECT_ADDRESS"})
    void reachedObjectMoveRejectsReservedOr24BitAddressesWithoutWrappingToDynamicSlots(
            int link, String address, CdPlayerMovement.UnsupportedBoundary boundary) {
        var ex = assertThrows(CdPlayerMovement.UnsupportedMotion.class,
                () -> CdPlayerMovement.moveWithoutGravity(supported().state(), context(new CdObjectPool(), link)));
        assertEquals(boundary, ex.boundary()); assertTrue(ex.getMessage().contains(address));
        assertTrue(ex.getMessage().contains("ObjMove"));
    }

    @ParameterizedTest @CsvSource({"0", "1", "31", "32", "127", "128", "191", "192", "255"})
    void rawLinksAreRetainedAndDoNotRequireReadsInAirOrWhenSupportBitIsClear(int link) {
        var pool = new CdObjectPool(); var cut = context(pool, link); Fixture f = new Fixture(false);
        Actor a = airborne(); a.flags |= 8; a.y = 200 << 16;
        var owner = f.owner(); var input = new CdPlayerMovement.Input(0, 0);
        assertEquals(owner.step(a.state(), input, f.environment()), owner.step(a.state(), input, f.environment(), cut));
        assertEquals(link, cut.orElseThrow().standObject());
        a.flags &= ~8;
        assertEquals(a.state(), CdPlayerMovement.moveWithoutGravity(a.state(), cut));
    }

    @Test void missingContextStopsAtTheFirstActualJumpBalanceOrMoveReadAndNeverAtAdmission() {
        Fixture f = new Fixture(false); var owner = f.owner(); Actor a = supported();
        var jump = assertThrows(CdPlayerMovement.UnsupportedMotion.class,
                () -> owner.step(a.state(), new CdPlayerMovement.Input(0x10, 0x10), f.environment()));
        assertEquals(CdPlayerMovement.UnsupportedBoundary.OBJECT_SUPPORT, jump.boundary());
        assertTrue(jump.getMessage().contains("ChkFlipper"));
        var balance = assertThrows(CdPlayerMovement.UnsupportedMotion.class, () -> f.step(a, 0, 0));
        assertTrue(balance.getMessage().contains("CheckBalance"));
        a.lock = 1;
        var move = assertThrows(CdPlayerMovement.UnsupportedMotion.class, () -> f.step(a, 0, 0));
        assertTrue(move.getMessage().contains("ObjMove"));
        Actor held = rolled(); held.flags |= 8; held.charge = 20; held.gv = 1800;
        assertDoesNotThrow(() -> f.step(held, 2, 0)); // CheckJump's charge guard; roll skips ObjMove.
        Actor air = airborne(); air.flags |= 8; air.y = 200 << 16;
        assertDoesNotThrow(() -> f.step(air, 0, 0));
    }

    @ParameterizedTest @CsvSource({"1", "31", "128", "192", "255"})
    void reachedJumpAndBalanceCutsHaveTheSameTypedAddressGuards(int link) {
        Fixture f = new Fixture(false); var cut = context(new CdObjectPool(), link); var owner = f.owner();
        for (int tap : new int[]{0, 0x10}) {
            var ex = assertThrows(CdPlayerMovement.UnsupportedMotion.class,
                    () -> owner.step(supported().state(), new CdPlayerMovement.Input(0, tap), f.environment(), cut));
            assertEquals(link < 32 ? CdPlayerMovement.UnsupportedBoundary.RESERVED_OBJECT_SLOT
                    : CdPlayerMovement.UnsupportedBoundary.OBJECT_ADDRESS, ex.boundary());
            assertTrue(ex.getMessage().contains(tap == 0 ? "CheckBalance" : "ChkFlipper"));
        }
    }

    @ParameterizedTest @CsvSource({"16,-1608,32210944", "0,-968,32374784"})
    void nonflipperSupportJumpKeepsStandLinkAndRunsNativeHeldOrReleasedAirNextCall(
            int heldNext, int expectedVelocity, int expectedY) {
        Fixture f = new Fixture(false); var pool = new CdObjectPool();
        putSupport(pool, 32, 0x12, 0, 12, 0x5B0, 0x20C); var cut = context(pool, 32);
        Actor a = supported(); a.x = 0x5B0 << 16; a.y = 0x1ED << 16;
        var owner = f.owner(); var jumped = owner.step(a.state(), new CdPlayerMovement.Input(0x10, 0x10), f.environment(), cut);
        assertEquals(CdPlayerMovement.Boundary.JUMP_MODE_RETURN, jumped.boundary());
        assertEquals(a.x, jumped.state().centreX16_16()); assertEquals(0x1F2 << 16, jumped.state().centreY16_16());
        assertEquals(-0x680, jumped.state().yVelocity()); assertEquals(14, jumped.state().flags());
        assertEquals(1, jumped.state().playerJump()); assertEquals(7, jumped.state().xRadius());
        assertEquals(14, jumped.state().yRadius()); assertEquals(32, cut.orElseThrow().standObject());
        assertEquals(List.of(new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_JUMP)), jumped.effects());
        var next = owner.step(jumped.state(), new CdPlayerMovement.Input(heldNext, 0), jumped.environment(), Optional.empty());
        assertEquals(expectedVelocity, next.state().yVelocity()); assertEquals(expectedY, next.state().centreY16_16());
        assertEquals(8, next.state().flags() & 8); assertEquals(32, cut.orElseThrow().standObject());
    }

    @ParameterizedTest @CsvSource({"1447,0,0,12,50", "1448,0,0,12,5", "1464,0,0,12,6",
            "1447,1,0,12,6", "1464,1,0,12,50", "1456,0,0,255,5", "1447,0,128,12,5"})
    void supportBalanceUsesBothSignedWordEdgesFacingNegativeFlagsAndUnsignedWidth(
            int playerX, int facing, int objectFlags, int width, int expectedAnimation) {
        Fixture f = new Fixture(false); var pool = new CdObjectPool();
        putSupport(pool, 32, 0x12, objectFlags, width, 0x5B0, 0x20C);
        Actor a = supported(); a.x = playerX << 16; a.y = 0x1ED << 16; a.flags |= facing;
        var r = f.owner().step(a.state(), new CdPlayerMovement.Input(0, 0), f.environment(), context(pool, 32));
        assertEquals(expectedAnimation, r.state().animation()); assertEquals(8, r.state().flags() & 8);
        assertEquals(a.y, r.state().centreY16_16()); assertEquals(0, r.environment().globals().primaryAngle());
        assertEquals(0, r.environment().globals().secondaryAngle());
    }

    @Test void supportBalanceFlagsGuardPrecedesFlipperIdAndSkipsTerrainCentreProbe() {
        Fixture f = new Fixture(false); Arrays.fill(f.fg, 2, f.fg.length, (byte) 0xF9);
        var pool = new CdObjectPool(); putSupport(pool, 32, 0x1E, 0x80, 0xFF, 0xFFFF, 0xFFFF);
        var owner = f.owner(); var input = new CdPlayerMovement.Input(0, 0); Actor a = supported();
        var hazardous = owner.step(a.state(), input, f.environment(), context(pool, 32));
        assertEquals(5, hazardous.state().animation()); // Negative flags branch to CheckCharge, before ID.
        putSupport(pool, 32, 0x1E, 0, 0xFF, 0xFFFF, 0xFFFF);
        assertEquals(0, owner.step(a.state(), input, f.environment(), context(pool, 32)).state().animation());
        assertEquals(a.pri, hazardous.state().previousPrimaryAngle());
        assertEquals(0, hazardous.environment().globals().primaryAngle());
        assertEquals(0, hazardous.environment().globals().secondaryAngle()); // GroundCol support bypass.
    }

    @Test void supportBalanceKeepsAccelerationTwelveWhileTerrainProbeClobbersOnlyD5ToThirteen() {
        Fixture f = new Fixture(); var pool = new CdObjectPool(); putSupport(pool, 32, 0x12, 0, 12, 64, 96);
        Actor a = supported(); a.charge = 30; a.gv = 0xC00;
        var supported = f.owner().step(a.state(), new CdPlayerMovement.Input(0, 0), f.environment(), context(pool, 32));
        assertEquals(0xBF4, supported.state().groundVelocity()); assertEquals(0xBF4, supported.state().xVelocity());
        a.flags = 0; var terrain = f.step(a, 0, 0);
        assertEquals(0xBF3, terrain.state().groundVelocity()); assertEquals(0xBF3, terrain.state().xVelocity());
        assertEquals(12, supported.environment().physics().acceleration());
    }

    @ParameterizedTest @CsvSource({"0,0,-4640", "1,0,-3040", "0,1,-4640"})
    void completeFlipperJumpUsesCalcAngleSignedForceOneAnimationPatchAndOrderedNativeSounds(
            int objectFlags, int oldAnimation, int expectedVelocity) {
        Fixture f = new Fixture(false); Arrays.fill(f.fg, 2, f.fg.length, (byte) 0xF9); // Headroom would fail.
        var pool = new CdObjectPool(); putSupport(pool, 32, 0x1E, objectFlags, 12, 0x5B0, 0x20C);
        pool.write(pool.state(32).moveByte(CdObjectState.ByteField.ANIMATION, oldAnimation));
        var before = pool.state(32); var cut = context(pool, 32);
        Actor a = supported(); a.x = 0x5B0 << 16; a.y = 0x1ED << 16;
        var r = f.owner().step(a.state(), new CdPlayerMovement.Input(0x10, 0x10), f.environment(), cut);
        var patch = new CdPlayerMovement.SlotPatch(before, before.moveByte(CdObjectState.ByteField.ANIMATION, 1));
        assertEquals(CdPlayerMovement.Boundary.JUMP_MODE_RETURN, r.boundary());
        assertEquals(0, r.state().xVelocity()); assertEquals(expectedVelocity, r.state().yVelocity());
        assertEquals(0x1F2 << 16, r.state().centreY16_16()); assertEquals(14, r.state().flags());
        assertEquals(List.of(new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_SPRING),
                new CdPlayerMovement.ObjectPatch(patch), new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_JUMP)), r.effects());
        assertEquals(0x98, CdPlayerMovement.Sound.FM_SPRING.nativeId());
        assertEquals(before, pool.state(32)); assertEquals(before, cut.orElseThrow().slots().slots().get(0));
    }

    @Test void flipperLaunchUsesSuppliedCalcAngleAndSineAssetsRatherThanAnAssumedCardinalLaunch() {
        Fixture f = new Fixture(false);
        // Valid monotonic, endpoint-preserving table: vector (7,55) indexes32, now8 instead of4.
        // CalcAngle gives $40-8=$38. Force64-(12-7)=59 gives speed-$1338.
        for (int i = 32; i < 64; i++) f.atan[i] = 8;
        f.sine(0x38, 128); f.sine(0x78, 64);
        var pool = new CdObjectPool(); putSupport(pool, 32, 0x1E, 0, 12, 0x5B0, 0x20C);
        Actor a = supported(); a.x = 0x5A9 << 16; a.y = 0x1ED << 16;
        var r = f.owner().step(a.state(), new CdPlayerMovement.Input(0x10, 0x10), f.environment(), context(pool, 32));
        assertEquals(-0x4CE, r.state().xVelocity()); assertEquals(-0x99C, r.state().yVelocity());
    }

    @ParameterizedTest @CsvSource({"819,30216", "820,-4608", "-820,-512"})
    void flipperDivsOverflowRetainsProductLowWordBeforeTheFinalSpeedAdd(int force, int expectedSpeed) {
        Fixture f = new Fixture(false);
        // CONSTRUCTED constant sine/cosine table exposes d2 unchanged by angle selection, not a native table.
        for (int i = 0; i < 320; i++) word(f.sine, i * 2, 256);
        var pool = new CdObjectPool(); putSupport(pool, 32, 0x1E, 1, 12, 0x5B0, 0x20C);
        Actor a = supported(); a.x = (0x5B0 + force - 12) << 16; a.y = 0x1ED << 16;
        var r = f.owner().step(a.state(), new CdPlayerMovement.Input(0x10, 0x10), f.environment(), context(pool, 32));
        assertEquals(expectedSpeed, r.state().xVelocity()); assertEquals(expectedSpeed, r.state().yVelocity());
    }

    @Test void standZeroAliasesCurrentCursorHeaderWidthFlagsAndPositionRatherThanAnAbsentPoolSlot() {
        Fixture f = new Fixture(false); Actor a = supported();
        var pool = new CdObjectPool(); var cut = Optional.of(new CdPlayerMovement.ObjectContext(pool.capture(), 0, 1, 0xFF));
        assertEquals(5, f.owner().step(a.state(), new CdPlayerMovement.Input(0, 0), f.environment(), cut).state().animation());
        // CONSTRUCTED zero acceleration keeps zero inertia while Left changes facing before the alias read.
        f.physics = new CdPlayerMovement.Physics(0x600, 0, 0x80);
        // Current player width2 puts its own d1=2 at the left edge.
        cut = Optional.of(new CdPlayerMovement.ObjectContext(pool.capture(), 0, 1, 2));
        assertEquals(6, f.owner().step(a.state(), new CdPlayerMovement.Input(4, 0), f.environment(), cut).state().animation());
        var flipper = Optional.of(new CdPlayerMovement.ObjectContext(pool.capture(), 0, 0x1E, 12));
        a.xv = 0x180;
        assertEquals(a.x + (0x80 << 8), CdPlayerMovement.moveWithoutGravity(a.state(), flipper).centreX16_16());
        a.xv = 0; a.flags = 9;
        assertEquals(a.x + (0x100 << 8), CdPlayerMovement.moveWithoutGravity(a.state(), flipper).centreX16_16());
        var jumped = f.owner().step(a.state(), new CdPlayerMovement.Input(0x10, 0x10), f.environment(), flipper);
        assertEquals(-0xBE0, jumped.state().yVelocity()); // Current player flags0=1, force12.
        assertFalse(jumped.effects().stream().anyMatch(CdPlayerMovement.ObjectPatch.class::isInstance));
    }

    @Test void slotPatchRequiresSameRealOwnerSlotAndExactlyTheAnimationByteIncludingSameValueStores() {
        var pool = new CdObjectPool(); var before = pool.state(32);
        assertEquals(before, new CdPlayerMovement.SlotPatch(before, before).after());
        var after = before.moveByte(CdObjectState.ByteField.ANIMATION, 1);
        assertEquals(after, new CdPlayerMovement.SlotPatch(before, after).after());
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.SlotPatch(before, pool.state(33)));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.SlotPatch(before, new CdObjectPool().state(32)));
        for (var field : CdObjectState.ByteField.values()) {
            if (field == CdObjectState.ByteField.ANIMATION) continue;
            assertThrows(IllegalArgumentException.class,
                    () -> new CdPlayerMovement.SlotPatch(before, after.moveByte(field, 1)), field.name());
        }
        assertThrows(NullPointerException.class, () -> new CdPlayerMovement.SlotPatch(null, after));
        assertThrows(NullPointerException.class, () -> new CdPlayerMovement.SlotPatch(before, null));
        assertThrows(NullPointerException.class, () -> new CdPlayerMovement.ObjectPatch(null));
    }

    @Test void objectContextValidatesAllRequiredBytesAndNewPortsRejectNullOptionals() {
        var pool = new CdObjectPool(); var slots = pool.capture(); var state = supported().state();
        assertThrows(NullPointerException.class, () -> new CdPlayerMovement.ObjectContext(null, 0, 1, 12));
        for (int bad : new int[]{-1, 256}) {
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.ObjectContext(slots, bad, 1, 12));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.ObjectContext(slots, 0, bad, 12));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.ObjectContext(slots, 0, 1, bad));
        }
        assertThrows(NullPointerException.class, () -> CdPlayerMovement.moveWithGravity(null));
        assertThrows(NullPointerException.class, () -> CdPlayerMovement.moveWithoutGravity(null, Optional.empty()));
        assertThrows(NullPointerException.class, () -> CdPlayerMovement.moveWithoutGravity(state, null));
        var f = new Fixture(false); var owner = f.owner();
        assertThrows(NullPointerException.class, () -> owner.step(state, new CdPlayerMovement.Input(0, 0), f.environment(), null));
        var stopped = owner.step(airPit().state(), new CdPlayerMovement.Input(0, 0), f.environment());
        assertThrows(NullPointerException.class,
                () -> owner.resumeAfterLevelBound(stopped, stopped.state(), stopped.environment(), null));
    }

    @ParameterizedTest @CsvSource({"0", "4", "2", "6"})
    void supportedKillResumeBorrowsSameContextAndPreservesDispatchTailWithoutReplayingThePrefix(int mode) {
        Fixture f = new Fixture(false); var pool = new CdObjectPool(); putSupport(pool, 32, 0x1E, 1, 12, 64, 96);
        var cut = context(pool, 32); var owner = f.owner(); Actor a = new Actor();
        a.flags = mode | 8; a.gv = 0x200; a.xv = 0x123; a.yv = 0x100; a.lock = 5; a.y = 200 << 16;
        var input = new CdPlayerMovement.Input(8, 0);
        var ordinary = owner.step(a.state(), input, f.environment(), cut);
        f.bounds = new CdPlayerMovement.Bounds(0, 0x2000, 0xFF20, 0);
        var stopped = owner.step(a.state(), input, f.environment(), cut);
        assertEquals(CdPlayerMovement.Boundary.KILL_PLAYER, stopped.boundary());
        var noWrite = killAt(stopped, 1);
        var resumed = owner.resumeAfterLevelBound(stopped, noWrite.actor().motion(), stopped.environment(), cut);
        assertEquals(ordinary.state(), resumed.state()); assertEquals(ordinary.environment().globals(), resumed.environment().globals());
        assertTrue(resumed.effects().isEmpty());
        assertEquals(resumed, f.owner().resumeAfterLevelBound(stopped, stopped.state(), stopped.environment(), cut));
        if ((mode & 2) == 0) {
            var ex = assertThrows(CdPlayerMovement.UnsupportedMotion.class,
                    () -> owner.resumeAfterLevelBound(stopped, stopped.state(), stopped.environment()));
            assertEquals(CdPlayerMovement.UnsupportedBoundary.OBJECT_SUPPORT, ex.boundary());
            assertTrue(ex.getMessage().contains("ObjMove"));
        }
    }

    @Test void borrowedContextIsPureInputAndNeverRetainedInResultsOrMovementOwner() {
        Fixture f = new Fixture(false); var owner = f.owner(); var pool = new CdObjectPool();
        putSupport(pool, 32, 0x12, 0, 12, 64, 96); var firstCut = context(pool, 32);
        Actor a = supported(); a.lock = 1; a.gv = 0x200;
        var input = new CdPlayerMovement.Input(0, 0); var env = f.environment();
        var first = owner.step(a.state(), input, env, firstCut);
        putSupport(pool, 32, 0x1E, 1, 12, 64, 96); var nextCut = context(pool, 32);
        var next = owner.step(a.state(), input, env, nextCut);
        assertEquals(first.state().centreX16_16() + (0x100 << 8), next.state().centreX16_16());
        assertEquals(first, owner.step(a.state(), input, env, firstCut));
        assertEquals(first, f.owner().step(a.state(), input, env, firstCut));
        for (var field : CdPlayerMovement.class.getDeclaredFields()) {
            assertNotEquals(CdObjectPool.Snapshot.class, field.getType());
            assertNotEquals(CdPlayerMovement.ObjectContext.class, field.getType());
            assertNotEquals(Optional.class, field.getType());
        }
        for (var component : CdPlayerMovement.Result.class.getRecordComponents()) {
            assertNotEquals(CdObjectPool.Snapshot.class, component.getType());
            assertNotEquals(CdPlayerMovement.ObjectContext.class, component.getType());
            assertNotEquals(Optional.class, component.getType());
        }
    }

    private static Actor supported() { Actor a = new Actor(); a.flags = 8; return a; }
    private static Optional<CdPlayerMovement.ObjectContext> context(CdObjectPool pool, int link) {
        return Optional.of(new CdPlayerMovement.ObjectContext(pool.capture(), link, 1, 9));
    }
    private static void putSupport(CdObjectPool pool, int slot, int id, int flags, int width, int x, int y) {
        pool.write(pool.state(slot).moveByte(CdObjectState.ByteField.ID, id).moveByte(CdObjectState.ByteField.FLAGS, flags)
                .moveByte(CdObjectState.ByteField.WIDTH, width).moveWord(CdObjectState.WordField.X, x)
                .moveWord(CdObjectState.WordField.Y, y));
    }

    private static Actor airborne() { Actor a = rolled(); a.flags = 6; return a; }
    private static CdPlayerMovement.Globals globals(int look, int camera, int pri, int sec, int bored, int score) {
        return new CdPlayerMovement.Globals(look, camera, pri, sec, bored, score, 0, 0, 0, 0);
    }

    /** Test-only semantic setup; no engine/RAM interpreter and no alternate movement oracle. */
    private static final class Actor {
        int x = 64 << 16, y = 76 << 16, xv, yv, gv, routine = 2, angle, flags, spr = 4;
        int xr = 9, yr = 19, anim, prev, jump, charge, lock, stick, ctrl, pri, sec;
        CdPlayerMotionState state() {
            return new CdPlayerMotionState(x, y, xv, yv, gv, routine, angle, flags, spr, xr, yr,
                    anim, prev, jump, charge, lock, stick, ctrl, pri, sec);
        }
    }

    /** CONSTRUCTED byte shapes, decoded by production owners; no retail route or authenticated-media claim. */
    private static final class Fixture {
        final byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50];
        final byte[] ids = new byte[900], angles = new byte[256], heights = new byte[4096], widths = new byte[4096];
        final byte[] sine = new byte[640], atan = new byte[257];
        final byte[] ramps = {6, 7, 8, 0x49, 0x4C, (byte) 0xFF};
        CdPlayerMovement.Physics physics = new CdPlayerMovement.Physics(0x600, 12, 0x80);
        CdPlayerMovement.Bounds bounds = new CdPlayerMovement.Bounds(0, 0x2000, 0x710, 0);
        CdPlayerMovement.Gates gates = new CdPlayerMovement.Gates(0, 1, 0, 0, 0, 0, 0);
        CdPlayerMovement.Globals globals = globals(0, 0x60, 0, 0, 0, 9);
        Fixture() { this(true); }
        Fixture(boolean floor) {
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7;
            Arrays.fill(fg, 2, fg.length, (byte) 2);
            ids[1] = 1; ids[2] = 2; ids[3] = 3;
            Arrays.fill(heights, 16, 32, (byte) 16);
            Arrays.fill(widths, 32, 48, (byte) 16);
            Arrays.fill(heights, 48, 64, (byte) 16);
            angles[2] = (byte) 0xC0; angles[3] = (byte) 0x80;
            sine(0x20, 181); sine(0x40, 256); sine(0x60, 181);
            sine(0xA0, -181); sine(0xC0, -256); sine(0xE0, -181);
            for (int i = 0; i <= 256; i++) { atan[i] = (byte) (i / 8); }
            if (floor) row(6, 0x6001);
        }
        void sine(int i, int value) { word(sine, i * 2, value); if (i < 64) word(sine, (i + 256) * 2, value); }
        void block(int x, int y, int value) { word(chunks, 512 + (y * 16 + x) * 2, value); }
        void row(int y, int value) { for (int x = 0; x < 16; x++) block(x, y, value); }
        void column(int x, int value) { for (int y = 0; y < 16; y++) block(x, y, value); }
        CdPlayerMovement.Environment environment() { return new CdPlayerMovement.Environment(physics, bounds, gates, globals); }
        CdPlayerMovement.Result step(Actor a, int held, int tap) { return owner().step(a.state(), new CdPlayerMovement.Input(held, tap), environment()); }
        CdPlayerMovement owner() {
            var terrain = CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg, ids, angles, heights, widths);
            byte[] animations = new byte[578];
            for (int i = 0; i < 60; i++) word(animations, i * 2, 120);
            animations[122] = (byte) 0xFF;
            byte[] mappings = new byte[398], dplcs = new byte[398];
            for (int i = 0; i < 198; i++) { word(mappings, i * 2, 396); word(dplcs, i * 2, 396); }
            var visual = CdSonicVisualAssets.decode(mappings, dplcs, new byte[69472]);
            var assets = CdR11aPlayerAssets.decode(animations, new byte[60], ramps, new byte[4], sine, atan, visual);
            return new CdPlayerMovement(terrain, assets);
        }
    }
    private static void word(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }

    // Shared raw-object ObjMove/ObjMoveGrv extraction. Tests authored/frozen before its production mirror.
    // ALL JAVA UNRUN. Literal vectors derive from Object Functions.asm:42-128 at the class's source pin.
    @Test void rawObjectGravityLiteralFallUsesOldWordsAndRetainsEveryFractionBit() {
        var before = new CdPlayerMovement.RawObjectMotion(0x10001234, 0x02005678, 0x100, -0x180, 0, 0, 0);
        assertEquals(new CdPlayerMovement.RawObjectMoveResult(0x10011234, 0x01FED678, 0x100, -0x148),
                CdPlayerMovement.moveObjectWithGravity(before));
        assertEquals(new CdPlayerMovement.RawObjectMotion(0x10001234, 0x02005678, 0x100, -0x180, 0, 0, 0), before);
    }

    @ParameterizedTest @CsvSource({"-384,4,-384,01FED678", "-384,8,-384,01FED678",
            "-384,128,-328,01FED678", "-2049,2,-2049,01F85578", "-2049,130,-2049,01F85578",
            "-2048,2,-1992,01F85678", "-2047,2,-1991,01F85778", "0,2,56,02005678",
            "256,2,312,02015678", "-32768,0,-32712,01805678", "4352,12,4096,02115678"})
    void rawObjectGravityReadsControl2CAndPreservesOldYSignAcrossBtst(
            int oldY, int control2C, int storedY, String expectedY) {
        // Bit 3/pole and bit 2/bar suppress; bit 1 suppresses only negative unsigned Y<$F800.
        // Control's sign does not replace the MOVE.W old-Y N flag. The cap still runs on .NoGravity.
        var before = new CdPlayerMovement.RawObjectMotion(0x10001234, 0x02005678, 0x100, oldY, 0, control2C, 255);
        assertEquals(new CdPlayerMovement.RawObjectMoveResult(0x10011234, rawObjectBits(expectedY), 0x100, storedY),
                CdPlayerMovement.moveObjectWithGravity(before));
    }

    @ParameterizedTest @CsvSource({"32752,-32728,02804678", "4064,4096,02103678", "4096,4096,02105678"})
    void rawObjectGravityNarrowsAddedWordBeforeCapButIntegratesTheOldWord(
            int oldY, int storedY, String expectedY) {
        // $7FF0+$38=$8028 stays negative/uncapped; $0FE0+$38=$1018 caps, with old delta $0FE000.
        var before = new CdPlayerMovement.RawObjectMotion(0x10001234, 0x02005678, 0x100, oldY, 8, 0, 192);
        assertEquals(new CdPlayerMovement.RawObjectMoveResult(0x10011234, rawObjectBits(expectedY), 0x100, storedY),
                CdPlayerMovement.moveObjectWithGravity(before));
    }

    @ParameterizedTest @CsvSource({"0", "1", "31", "32", "127", "128", "191", "192", "255"})
    void rawObjectNonstandingMoveIgnoresMissingOrForeignSupportAndControl(int link) {
        var before = new CdPlayerMovement.RawObjectMotion(0x10001234, 0x02005678, 0x100, -0x180, 0, 0xFF, link);
        var expected = new CdPlayerMovement.RawObjectMoveResult(0x10011234, 0x01FED678, 0x100, -0x180);
        assertEquals(expected, CdPlayerMovement.moveObject(before));
        var foreign = Optional.of(new CdPlayerMovement.RawObjectSupport(link == 33 ? 32 : 33, 0x1E, 1));
        assertEquals(expected, CdPlayerMovement.moveObject(before, foreign));
        assertEquals(link, before.standLink3D());
    }

    @ParameterizedTest @CsvSource({"0", "1", "31", "32", "127", "128", "191", "192", "255"})
    void rawObjectGravityNeverDereferencesStandingLinksEvenWhenStatusBitThreeIsSet(int link) {
        var before = new CdPlayerMovement.RawObjectMotion(0x10001234, 0x02005678, 0x100, -0x180, 8, 0, link);
        assertEquals(new CdPlayerMovement.RawObjectMoveResult(0x10011234, 0x01FED678, 0x100, -0x148),
                CdPlayerMovement.moveObjectWithGravity(before));
    }

    @ParameterizedTest @CsvSource({"32,0,1,9,10019234", "127,18,1,8,10019234",
            "32,30,0,9,10009234", "127,30,1,8,10029234",
            "32,30,128,9,10009234", "127,30,129,8,10029234"})
    void rawObjectMoveUsesSelectedWholeSlotIdAndFlagsForLocalResistanceOnly(
            int link, int id, int supportFlags, int bodyFlags, String expectedX) {
        var before = new CdPlayerMovement.RawObjectMotion(0x10001234, 0x02005678, 0x180, -0x200, bodyFlags, 0xFF, link);
        var support = new CdPlayerMovement.RawObjectSupport(link, id, supportFlags);
        assertEquals(new CdPlayerMovement.RawObjectMoveResult(rawObjectBits(expectedX), 0x01FE5678, 0x180, -0x200),
                CdPlayerMovement.moveObject(before, Optional.of(support)));
        assertEquals(0x180, before.xVelocity());
        assertEquals(supportFlags, support.flags22());
    }

    @ParameterizedTest @CsvSource({"1,1,10019234", "30,0,10009234", "30,1,10029234"})
    void rawObjectStandZeroRequiresActualPrimaryIdAndFlagsRatherThanTheMovingBodyFlags(
            int primaryId, int primaryFlags, String expectedX) {
        // This raw body is not player0. Its flags0=1 must not supply the primary's flags0.
        var before = new CdPlayerMovement.RawObjectMotion(0x10001234, 0x02005678, 0x180, -0x200, 9, 0, 0);
        var primary = new CdPlayerMovement.RawObjectSupport(0, primaryId, primaryFlags);
        assertEquals(new CdPlayerMovement.RawObjectMoveResult(rawObjectBits(expectedX), 0x01FE5678, 0x180, -0x200),
                CdPlayerMovement.moveObject(before, Optional.of(primary)));
    }

    @ParameterizedTest @CsvSource({"32767,1,0531FEFE", "-32768,0,062FFFFE"})
    void rawObjectResistanceWrapsTheLocalSignedWordBeforeLongSignExtension(
            int xVelocity, int supportFlags, String expectedX) {
        var before = new CdPlayerMovement.RawObjectMotion(0x05B0FFFE, 0x02005678, xVelocity, 0, 8, 0, 127);
        assertEquals(new CdPlayerMovement.RawObjectMoveResult(rawObjectBits(expectedX), 0x02005678, xVelocity, 0),
                CdPlayerMovement.moveObject(before, Optional.of(new CdPlayerMovement.RawObjectSupport(127, 0x1E, supportFlags))));
    }

    @ParameterizedTest @CsvSource({"FFFFFEFF,00000001,2,-1,000000FF,FFFFFF01",
            "1234FFFE,0000FFFF,1,1,123500FE,000100FF"})
    void rawObjectBothPortsKeepFullFractionCarryAndNativeLongWrap(
            String x, String y, int xv, int yv, String expectedX, String expectedY) {
        var before = new CdPlayerMovement.RawObjectMotion(rawObjectBits(x), rawObjectBits(y), xv, yv, 0, 12, 255);
        var expected = new CdPlayerMovement.RawObjectMoveResult(rawObjectBits(expectedX), rawObjectBits(expectedY), xv, yv);
        assertEquals(expected, CdPlayerMovement.moveObject(before));
        assertEquals(expected, CdPlayerMovement.moveObjectWithGravity(before)); // Control 12 suppresses gravity.
    }

    @ParameterizedTest @CsvSource({"0", "32", "127"})
    void rawObjectReachedSupportedAddressWithoutItsActualCutStopsAsObjectSupport(int link) {
        var before = new CdPlayerMovement.RawObjectMotion(0x10001234, 0x02005678, 0x180, -0x200, 8, 0, link);
        var ex = assertThrows(CdPlayerMovement.UnsupportedMotion.class, () -> CdPlayerMovement.moveObject(before));
        assertEquals(CdPlayerMovement.UnsupportedBoundary.OBJECT_SUPPORT, ex.boundary());
        assertTrue(ex.getMessage().contains("ObjMove"));
        assertEquals(link, before.standLink3D());
    }

    @ParameterizedTest @CsvSource({"0,32", "32,0", "127,32"})
    void rawObjectReachedCutMustMatchTheWholeIndexWithoutAliasNormalization(int link, int foreignWhole) {
        var before = new CdPlayerMovement.RawObjectMotion(0x10001234, 0x02005678, 0x180, -0x200, 8, 0, link);
        var foreign = Optional.of(new CdPlayerMovement.RawObjectSupport(foreignWhole, 0x1E, 1));
        var ex = assertThrows(CdPlayerMovement.UnsupportedMotion.class,
                () -> CdPlayerMovement.moveObject(before, foreign));
        assertEquals(CdPlayerMovement.UnsupportedBoundary.OBJECT_SUPPORT, ex.boundary());
        assertTrue(ex.getMessage().contains("ObjMove"));
    }

    @ParameterizedTest @CsvSource({"1,FFD040,RESERVED_OBJECT_SLOT", "31,FFD7C0,RESERVED_OBJECT_SLOT",
            "128,FFF000,OBJECT_ADDRESS", "191,FFFFC0,OBJECT_ADDRESS", "192,000000,OBJECT_ADDRESS",
            "255,000FC0,OBJECT_ADDRESS"})
    void rawObjectReachedReservedAndOutsideAddressesStayTypedEvenWithAMatchingValuePacket(
            int link, String address, CdPlayerMovement.UnsupportedBoundary boundary) {
        var before = new CdPlayerMovement.RawObjectMotion(0x10001234, 0x02005678, 0x180, -0x200, 8, 0, link);
        var matching = Optional.of(new CdPlayerMovement.RawObjectSupport(link, 0x1E, 1));
        for (var cut : List.of(Optional.<CdPlayerMovement.RawObjectSupport>empty(), matching)) {
            var ex = assertThrows(CdPlayerMovement.UnsupportedMotion.class,
                    () -> CdPlayerMovement.moveObject(before, cut));
            assertEquals(boundary, ex.boundary());
            assertTrue(ex.getMessage().contains("ObjMove")); assertTrue(ex.getMessage().contains(address));
        }
    }

    @ParameterizedTest @CsvSource({"0,9,10029234", "32,0,10009234", "127,1,10029234"})
    void rawObjectExtractionMatchesExistingPlayerPortsAndPreservesAllOtherMotionFields(
            int link, int supportFlags, String expectedX) {
        var before = new CdPlayerMotionState(0x10001234, 0x02005678, 0x180, -0x200, -7, 6, 0x9A, 9, 0xBC,
                7, 14, 0x1A, 0x1D, 1, 0x2E, -2, 0x80, 0, 0x41, 0x52);
        var pool = new CdObjectPool();
        if (link != 0) putSupport(pool, link, 0x1E, supportFlags, 12, 0x5B0, 0x20C);
        var snapshot = pool.capture();
        var oldCut = Optional.of(new CdPlayerMovement.ObjectContext(snapshot, link, 0x1E, 12));
        var raw = new CdPlayerMovement.RawObjectMotion(0x10001234, 0x02005678, 0x180, -0x200, 9, 0, link);
        var expectedMove = new CdPlayerMovement.RawObjectMoveResult(rawObjectBits(expectedX), 0x01FE5678, 0x180, -0x200);
        assertEquals(expectedMove, CdPlayerMovement.moveObject(raw,
                Optional.of(new CdPlayerMovement.RawObjectSupport(link, 0x1E, supportFlags))));
        assertEquals(with(with(before, 0, rawObjectBits(expectedX)), 1, 0x01FE5678),
                CdPlayerMovement.moveWithoutGravity(before, oldCut));
        assertEquals(new CdPlayerMovement.RawObjectMoveResult(0x10019234, 0x01FE5678, 0x180, -0x1C8),
                CdPlayerMovement.moveObjectWithGravity(raw));
        assertEquals(with(with(with(before, 0, 0x10019234), 1, 0x01FE5678), 3, -0x1C8),
                CdPlayerMovement.moveWithGravity(before));
        assertEquals(snapshot.slots(), pool.capture().slots());
    }

    @Test void rawObjectControlSupportDoesNotBroadenExistingModeAdmission() {
        var raw = new CdPlayerMovement.RawObjectMotion(0x10001234, 0x02005678, 0x100, -0x180, 0, 12, 255);
        assertEquals(new CdPlayerMovement.RawObjectMoveResult(0x10011234, 0x01FED678, 0x100, -0x180),
                CdPlayerMovement.moveObjectWithGravity(raw));
        Actor a = new Actor(); a.ctrl = 12;
        assertBoundary(new Fixture(false), a, CdPlayerMovement.UnsupportedBoundary.CONTROL_OWNER);
    }

    @Test void rawObjectPacketsRejectOutOfWidthValuesAndPortsRejectNullInputs() {
        for (int bad : new int[]{-32769, 32768}) {
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.RawObjectMotion(0, 0, bad, 0, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.RawObjectMotion(0, 0, 0, bad, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.RawObjectMoveResult(0, 0, bad, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.RawObjectMoveResult(0, 0, 0, bad));
        }
        for (int bad : new int[]{-1, 256}) {
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.RawObjectMotion(0, 0, 0, 0, bad, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.RawObjectMotion(0, 0, 0, 0, 0, bad, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.RawObjectMotion(0, 0, 0, 0, 0, 0, bad));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.RawObjectSupport(bad, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.RawObjectSupport(0, bad, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.RawObjectSupport(0, 0, bad));
        }
        var bits = new CdPlayerMovement.RawObjectMotion(Integer.MIN_VALUE, Integer.MAX_VALUE, -32768, 32767, 255, 255, 255);
        assertEquals(Integer.MIN_VALUE, bits.centreX16_16()); assertEquals(Integer.MAX_VALUE, bits.centreY16_16());
        assertThrows(NullPointerException.class, () -> CdPlayerMovement.moveObject(null));
        assertThrows(NullPointerException.class, () -> CdPlayerMovement.moveObject(null, Optional.empty()));
        assertThrows(NullPointerException.class, () -> CdPlayerMovement.moveObject(bits, null));
        assertThrows(NullPointerException.class, () -> CdPlayerMovement.moveObjectWithGravity(null));
    }

    private static int rawObjectBits(String literal) { return Integer.parseUnsignedInt(literal, 16); }

    // Collapse player-probe prefixes. Constructed decoder input only; ALL JAVA UNRUN.
    @Test void prefixCompleteFloorMatchesLegacyAndReportsSameValuePrimaryThenSecondaryStores() {
        var f = new Fixture(); var owner = f.owner(); var a = prefixMotion(0x00401234, 0x004CABCD, 0, 0, 4);
        var before = f.environment(); var writes = new PrefixAngles(0, 0);
        var result = owner.checkFloorWithPrefix(a, writes);
        assertEquals(owner.checkFloor(a), result); assertEquals(0, result.distance());
        assertEquals(List.of(new PrefixStore(CdPlayerMovement.AngleBuffer.PRIMARY, 0),
                new PrefixStore(CdPlayerMovement.AngleBuffer.SECONDARY, 0)), writes.events);
        assertEquals(java.util.OptionalInt.of(0), result.primaryAngleWrite());
        assertEquals(java.util.OptionalInt.of(0), result.secondaryAngleWrite());
        assertEquals(prefixMotion(0x00401234, 0x004CABCD, 0, 0, 4), a); assertEquals(before, f.environment());
    }

    @Test void prefixBlankFloorAndCeilingDoNotPreclearEitherExistingByte() {
        var f = new Fixture(false); var owner = f.owner(); var a = prefixMotion(0x00401234, 0x004CABCD, 0, 0, 4);
        var writes = new PrefixAngles(0x12, 0x34);
        assertEquals(owner.checkFloor(a), owner.checkFloorWithPrefix(a, writes));
        assertEquals(owner.getCeilDist(a), owner.getCeilDistWithPrefix(a, writes));
        assertTrue(writes.events.isEmpty()); assertEquals(0x12, writes.primary); assertEquals(0x34, writes.secondary);
        assertEquals(prefixMotion(0x00401234, 0x004CABCD, 0, 0, 4), a);
    }

    @Test void prefixFirstFloorFaultWritesNeitherBufferAndKeepsItsPrimaryCallIdentity() {
        var f = new Fixture(false); f.fg[3] = (byte) 0xF9; var owner = f.owner();
        var a = prefixMotion(0x01008000, 0x00611234, 0x123, 0x400, 0x44); var writes = new PrefixAngles(0x12, 0x34);
        var failure = assertThrows(CdPlayerMovement.UnownedProbeRead.class, () -> owner.checkFloorWithPrefix(a, writes));
        assertPrefixWord(failure, CdPlayerMovement.AngleBuffer.PRIMARY, 1, 0xF2E0);
        assertTrue(writes.events.isEmpty()); assertEquals(0x12, writes.primary); assertEquals(0x34, writes.secondary);
        var legacy = assertThrows(IllegalStateException.class, () -> owner.checkFloor(a));
        assertEquals(IllegalStateException.class, legacy.getClass());
        assertEquals(failure.terrainRead().originalException().getMessage(), legacy.getMessage());
        assertEquals(prefixMotion(0x01008000, 0x00611234, 0x123, 0x400, 0x44), a);
    }

    @Test void prefixPrimaryRawWriteThenItsOwnAdjacentFaultIsStillPrimaryNotSecondary() {
        var f = new Fixture(false); f.fg[2 + 42 + 1] = (byte) 0xF9;
        f.block(0, 15, 0x2001); f.angles[1] = 3; Arrays.fill(f.heights, 16, 32, (byte) 0);
        var owner = f.owner(); var a = prefixMotion(0x01008000, 0x00EC1234, 0, 0, 0x44);
        var writes = new PrefixAngles(0x12, 0x34);
        var failure = assertThrows(CdPlayerMovement.UnownedProbeRead.class, () -> owner.checkFloorWithPrefix(a, writes));
        assertPrefixWord(failure, CdPlayerMovement.AngleBuffer.PRIMARY, 0x81, 0xF200);
        assertEquals(List.of(new PrefixStore(CdPlayerMovement.AngleBuffer.PRIMARY, 3)), writes.events);
        assertEquals(3, writes.primary); assertEquals(0x34, writes.secondary);
        assertEquals(prefixMotion(0x01008000, 0x00EC1234, 0, 0, 0x44), a);
    }

    @Test void prefixCompletedPrimaryWithoutAnyWriteThenSecondaryFaultUsesTheActualSecondCall() {
        var f = new Fixture(false); f.fg[2] = (byte) 0xF9;
        var owner = f.owner(); var a = prefixMotion(0x00F88000, 0x00611234, 0, 0, 0x44);
        var writes = new PrefixAngles(0x12, 0x34);
        var failure = assertThrows(CdPlayerMovement.UnownedProbeRead.class, () -> owner.checkFloorWithPrefix(a, writes));
        assertPrefixWord(failure, CdPlayerMovement.AngleBuffer.SECONDARY, 0, 0xF2FC);
        assertTrue(writes.events.isEmpty()); assertEquals(0x12, writes.primary); assertEquals(0x34, writes.secondary);
        assertEquals(prefixMotion(0x00F88000, 0x00611234, 0, 0, 0x44), a);
    }

    @Test void prefixCompletedFirstFootRawZeroSurvivesTheSecondFootFirstReadFailure() {
        var f = new Fixture(false); f.fg[2] = (byte) 0xF9; f.block(0, 7, 0x2001);
        Arrays.fill(f.heights, 16, 32, (byte) 1); var owner = f.owner();
        var a = prefixMotion(0x00F88000, 0x00611234, 0, 0, 0x44); var writes = new PrefixAngles(0x12, 0x34);
        var failure = assertThrows(CdPlayerMovement.UnownedProbeRead.class, () -> owner.checkFloorWithPrefix(a, writes));
        assertPrefixWord(failure, CdPlayerMovement.AngleBuffer.SECONDARY, 0, 0xF2FC);
        assertEquals(List.of(new PrefixStore(CdPlayerMovement.AngleBuffer.PRIMARY, 0)), writes.events);
        assertEquals(0, writes.primary); assertEquals(0x34, writes.secondary);
    }

    @Test void prefixSecondaryRawStoreBeforeItsAdjacentFailureKeepsBothReachedFootWrites() {
        var f = new Fixture(false); f.fg[2 + 42] = (byte) 0xF9;
        f.block(0, 15, 0x2001); Arrays.fill(f.heights, 16, 32, (byte) 1);
        f.block(15, 15, 0x2004); f.ids[4] = 4; f.angles[4] = 3;
        var owner = f.owner(); var a = prefixMotion(0x01008000, 0x00EC1234, 0, 0, 0x44);
        var writes = new PrefixAngles(0x12, 0x34);
        var failure = assertThrows(CdPlayerMovement.UnownedProbeRead.class, () -> owner.checkFloorWithPrefix(a, writes));
        assertPrefixWord(failure, CdPlayerMovement.AngleBuffer.SECONDARY, 0x80, 0xF21E);
        assertEquals(List.of(new PrefixStore(CdPlayerMovement.AngleBuffer.PRIMARY, 0),
                new PrefixStore(CdPlayerMovement.AngleBuffer.SECONDARY, 3)), writes.events);
        assertEquals(0, writes.primary); assertEquals(3, writes.secondary);
    }

    @Test void prefixRoomRightWallEmitsBothSeedsThenRawOddByteBeforeAdjacentFailure() {
        var f = new Fixture(false); f.fg[3] = (byte) 0xF9; f.block(15, 6, 0x4001); f.angles[1] = 3;
        var owner = f.owner(); var a = prefixMotion(0x00F50000, 0x00611234, 0, 0, 0x44);
        var writes = new PrefixAngles(0x12, 0x34);
        var failure = assertThrows(CdPlayerMovement.UnownedProbeRead.class,
                () -> owner.calcRoomInFrontWithPrefix(a, 0xC0, writes));
        assertPrefixWord(failure, CdPlayerMovement.AngleBuffer.PRIMARY, 1, 0xF2C0);
        assertEquals(List.of(new PrefixStore(CdPlayerMovement.AngleBuffer.PRIMARY, 0xC0),
                new PrefixStore(CdPlayerMovement.AngleBuffer.SECONDARY, 0xC0),
                new PrefixStore(CdPlayerMovement.AngleBuffer.PRIMARY, 3)), writes.events);
        assertEquals(3, writes.primary); assertEquals(0xC0, writes.secondary);
        assertEquals(prefixMotion(0x00F50000, 0x00611234, 0, 0, 0x44), a);
        assertEquals(IllegalStateException.class, assertThrows(IllegalStateException.class,
                () -> owner.calcRoomInFront(a, 0xC0)).getClass());
    }

    @Test void prefixRoomLeftWallUsesTheNativeLowWordMirrorAndNegativeStep() {
        var f = new Fixture(false); f.fg[2] = (byte) 0xF9; f.block(0, 6, 0x4001); f.angles[1] = 3;
        var owner = f.owner(); var a = prefixMotion(0x010A0000, 0x00611234, 0, 0, 0x44);
        var writes = new PrefixAngles(0x12, 0x34);
        var failure = assertThrows(CdPlayerMovement.UnownedProbeRead.class,
                () -> owner.calcRoomInFrontWithPrefix(a, 0x40, writes));
        assertPrefixWord(failure, CdPlayerMovement.AngleBuffer.PRIMARY, 0, 0xF2DE);
        assertEquals(List.of(new PrefixStore(CdPlayerMovement.AngleBuffer.PRIMARY, 0x40),
                new PrefixStore(CdPlayerMovement.AngleBuffer.SECONDARY, 0x40),
                new PrefixStore(CdPlayerMovement.AngleBuffer.PRIMARY, 3)), writes.events);
    }

    @Test void prefixRoomSeedsSameValueBytesInSourceOrderBeforeItsFirstUnavailableRead() {
        var f = new Fixture(false); f.fg[3] = (byte) 0xF9; var owner = f.owner();
        var a = prefixMotion(0x01000000, 0x00611234, 0, 0, 0x44); var writes = new PrefixAngles(0xC0, 0xC0);
        var failure = assertThrows(CdPlayerMovement.UnownedProbeRead.class,
                () -> owner.calcRoomInFrontWithPrefix(a, 0xC0, writes));
        assertPrefixWord(failure, CdPlayerMovement.AngleBuffer.PRIMARY, 1, 0xF2C0);
        assertEquals(List.of(new PrefixStore(CdPlayerMovement.AngleBuffer.PRIMARY, 0xC0),
                new PrefixStore(CdPlayerMovement.AngleBuffer.SECONDARY, 0xC0)), writes.events);
    }

    @Test void prefixCeilingNeverSeedsAndCarriesItsRawByteAcrossItsOwnAdjacentFailure() {
        var f = new Fixture(false); f.fg[3] = (byte) 0xF9; f.block(0, 0, 0x4001);
        f.angles[1] = 3; Arrays.fill(f.heights, 16, 32, (byte) 0);
        var owner = f.owner(); var a = prefixMotion(0x01008000, 0x010A1234, 0, -0x100, 0x44);
        var writes = new PrefixAngles(0x12, 0x34);
        var failure = assertThrows(CdPlayerMovement.UnownedProbeRead.class, () -> owner.getCeilDistWithPrefix(a, writes));
        assertPrefixWord(failure, CdPlayerMovement.AngleBuffer.PRIMARY, 1, 0xF3E0);
        assertEquals(List.of(new PrefixStore(CdPlayerMovement.AngleBuffer.PRIMARY, 3)), writes.events);
        assertEquals(3, writes.primary); assertEquals(0x34, writes.secondary);
        assertEquals(IllegalStateException.class, assertThrows(IllegalStateException.class,
                () -> owner.getCeilDist(a)).getClass());
        assertEquals(prefixMotion(0x01008000, 0x010A1234, 0, -0x100, 0x44), a);
    }

    @Test void prefixCeilingFirstUnavailableReadKeepsBothIncomingBytesUnwritten() {
        var f = new Fixture(false); f.fg[2 + 42 + 1] = (byte) 0xF9; var owner = f.owner();
        var a = prefixMotion(0x01008000, 0x010A1234, 0, 0, 0x44); var writes = new PrefixAngles(0x12, 0x34);
        var failure = assertThrows(CdPlayerMovement.UnownedProbeRead.class, () -> owner.getCeilDistWithPrefix(a, writes));
        assertPrefixWord(failure, CdPlayerMovement.AngleBuffer.PRIMARY, 0x81, 0xF200);
        assertTrue(writes.events.isEmpty()); assertEquals(0x12, writes.primary); assertEquals(0x34, writes.secondary);
    }

    @Test void prefixCompleteCeilingRetainsRawOddFlipWritesInsteadOfNormalizingTheBuffer() {
        var f = new Fixture(false); f.row(3, 0x5001); f.angles[1] = 3; var owner = f.owner();
        var a = prefixMotion(0x00401234, 0x0050ABCD, 0x123, -0x456, 4); var writes = new PrefixAngles(0x12, 0x34);
        var result = owner.getCeilDistWithPrefix(a, writes);
        assertEquals(owner.getCeilDist(a), result); assertEquals(6, result.distance());
        assertEquals(List.of(new PrefixStore(CdPlayerMovement.AngleBuffer.PRIMARY, 3),
                new PrefixStore(CdPlayerMovement.AngleBuffer.PRIMARY, 0x43),
                new PrefixStore(CdPlayerMovement.AngleBuffer.PRIMARY, 0xBD),
                new PrefixStore(CdPlayerMovement.AngleBuffer.PRIMARY, 0x7D)), writes.events);
        assertEquals(java.util.OptionalInt.of(0x7D), result.primaryAngleWrite());
        assertTrue(result.secondaryAngleWrite().isEmpty()); assertEquals(0x34, writes.secondary);
    }

    @Test void prefixCollisionIdStopCarriesTheSameActualTerrainMarkerThroughTheKnownFoot() {
        var f = new Fixture(false); f.fg[3] = (byte) 0x82;
        word(f.chunks, 1024 + (7 * 16) * 2, 0x2384); var owner = f.owner();
        var a = prefixMotion(0x01008000, 0x00611234, 0, 0, 0x44); var writes = new PrefixAngles(0x12, 0x34);
        var failure = assertThrows(CdPlayerMovement.UnownedProbeRead.class, () -> owner.checkFloorWithPrefix(a, writes));
        assertEquals(CdPlayerMovement.AngleBuffer.PRIMARY, failure.buffer());
        assertSame(failure.terrainRead(), failure.getCause());
        assertEquals(new CdPlayerTerrain.CollisionId(900), failure.terrainRead().detail());
        assertEquals("Collision ID requires unavailable resident bytes: 900", failure.terrainRead().originalException().getMessage());
        assertEquals(IllegalStateException.class, failure.terrainRead().originalException().getClass());
        assertSame(failure.terrainRead().originalException(), failure.terrainRead().getCause()); assertTrue(writes.events.isEmpty());
    }

    @Test void prefixRoomSignedRoundingAndAllQuadrantsStillProduceExactlyTheLegacyResult() {
        var owner = new Fixture(false).owner(); var a = prefixMotion(0x0040FFFF, 0x004CFFFF, -1, 1, 4);
        for (int angle : new int[]{0, 0x1F, 0x20, 0x3F, 0x40, 0x5F, 0x60, 0x7F,
                0x80, 0x9F, 0xA0, 0xBF, 0xC0, 0xDF, 0xE0, 0xFF}) {
            var writes = new PrefixAngles(angle, angle);
            assertEquals(owner.calcRoomInFront(a, angle), owner.calcRoomInFrontWithPrefix(a, angle, writes));
            assertEquals(List.of(new PrefixStore(CdPlayerMovement.AngleBuffer.PRIMARY, angle),
                    new PrefixStore(CdPlayerMovement.AngleBuffer.SECONDARY, angle)), writes.events);
        }
        assertEquals(prefixMotion(0x0040FFFF, 0x004CFFFF, -1, 1, 4), a);
    }

    @Test void prefixRoomPredictsFullLongWrapCarryBorrowAndUsesTheAdjustedYSample() {
        var f = new Fixture(false); f.block(0, 0, 0x4001); f.angles[1] = 3;
        f.widths[16 + 8] = 1; f.widths[16 + 7] = 1; var owner = f.owner();
        var cases = List.of(prefixMotion(0xFFFFFEFF, 0xFFFFFEFF, 2, 2, 4),
                prefixMotion(0x00000001, 0x00000001, -1, -1, 4));
        int[] expectedDistances = {4, 5};
        for (int i = 0; i < cases.size(); i++) {
            var writes = new PrefixAngles(0x12, 0x34); var a = cases.get(i);
            var result = owner.calcRoomInFrontWithPrefix(a, 0xC0, writes);
            assertEquals(expectedDistances[i], result.distance()); assertEquals(owner.calcRoomInFront(a, 0xC0), result);
            assertEquals(List.of(new PrefixStore(CdPlayerMovement.AngleBuffer.PRIMARY, 0xC0),
                    new PrefixStore(CdPlayerMovement.AngleBuffer.SECONDARY, 0xC0),
                    new PrefixStore(CdPlayerMovement.AngleBuffer.PRIMARY, 3)), writes.events);
        }
    }

    @Test void prefixFloorUsesSignedByteRadiiAndNativeWordSensorsWithoutChangingAnyMotionField() {
        var f = new Fixture(false); f.block(15, 5, 0x2001); Arrays.fill(f.heights, 16, 32, (byte) 1);
        f.block(0, 5, 0x2004); f.ids[4] = 4; f.angles[4] = 4; Arrays.fill(f.heights, 64, 80, (byte) 2);
        var owner = f.owner(); var a = with(with(prefixMotion(0x01001234, 0x0061ABCD, -32768, 32767, 4), 9, 0xFF), 10, 0xFE);
        var writes = new PrefixAngles(0x12, 0x34); var result = owner.checkFloorWithPrefix(a, writes);
        assertEquals(-2, result.distance()); assertEquals(owner.checkFloor(a), result);
        assertEquals(List.of(new PrefixStore(CdPlayerMovement.AngleBuffer.PRIMARY, 0),
                new PrefixStore(CdPlayerMovement.AngleBuffer.SECONDARY, 4)), writes.events);
        assertEquals(with(with(prefixMotion(0x01001234, 0x0061ABCD, -32768, 32767, 4), 9, 0xFF), 10, 0xFE), a);
    }

    @Test void prefixCallbacksThrowTheirActualHostExceptionAtSeedsAndWithinRawFootStores() {
        var blank = new Fixture(false).owner(); var a = prefixMotion(0x00401234, 0x004CABCD, 0, 0, 4);
        for (int cut : new int[]{1, 2}) {
            var raw = new ArrayList<PrefixStore>(); var sentinel = new IllegalStateException("seed " + cut);
            assertSame(sentinel, assertThrows(IllegalStateException.class,
                    () -> blank.calcRoomInFrontWithPrefix(a, 0xC0, (buffer, value) -> {
                        raw.add(new PrefixStore(buffer, value)); if (raw.size() == cut) throw sentinel;
                    })));
            assertEquals(cut, raw.size());
        }
        var f = new Fixture(); var owner = f.owner();
        for (var sentinel : new RuntimeException[]{new RuntimeException("host"), new IllegalStateException("state"),
                new IllegalArgumentException("argument")}) {
            var raw = new ArrayList<PrefixStore>();
            assertSame(sentinel, assertThrows(RuntimeException.class, () -> owner.checkFloorWithPrefix(a, (buffer, value) -> {
                raw.add(new PrefixStore(buffer, value)); if (buffer == CdPlayerMovement.AngleBuffer.SECONDARY) throw sentinel;
            })));
            assertEquals(List.of(new PrefixStore(CdPlayerMovement.AngleBuffer.PRIMARY, 0),
                    new PrefixStore(CdPlayerMovement.AngleBuffer.SECONDARY, 0)), raw);
        }
    }

    @Test void prefixValidationInvariantsAndPrivateMarkersStaySeparateFromNativeReadFailures() {
        var owner = new Fixture(false).owner(); var a = prefixMotion(0x00401234, 0x004CABCD, 0, 0, 4);
        var writes = new PrefixAngles(0x12, 0x34);
        assertThrows(NullPointerException.class, () -> owner.calcRoomInFrontWithPrefix(null, 0, writes));
        assertThrows(NullPointerException.class, () -> owner.checkFloorWithPrefix(null, writes));
        assertThrows(NullPointerException.class, () -> owner.getCeilDistWithPrefix(null, writes));
        assertThrows(NullPointerException.class, () -> owner.calcRoomInFrontWithPrefix(a, 0, null));
        assertThrows(NullPointerException.class, () -> owner.checkFloorWithPrefix(a, null));
        assertThrows(NullPointerException.class, () -> owner.getCeilDistWithPrefix(a, null));
        for (int angle : new int[]{-1, 256}) {
            assertEquals(IllegalArgumentException.class, assertThrows(IllegalArgumentException.class,
                    () -> owner.calcRoomInFrontWithPrefix(a, angle, writes)).getClass());
        }
        assertTrue(writes.events.isEmpty());
        assertEquals(IllegalArgumentException.class, assertThrows(IllegalArgumentException.class,
                () -> new CdPlayerMovement.ProbeResult(32768, java.util.OptionalInt.empty(), java.util.OptionalInt.empty())).getClass());
        assertThrows(IllegalArgumentException.class,
                () -> new CdPlayerMovement.ProbeResult(0, java.util.OptionalInt.of(256), java.util.OptionalInt.empty()));
        assertThrows(NullPointerException.class,
                () -> new CdPlayerMovement.ProbeResult(0, null, java.util.OptionalInt.empty()));
        assertTrue(Arrays.stream(CdPlayerMovement.UnownedProbeRead.class.getDeclaredConstructors())
                .allMatch(c -> java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
        for (var field : CdPlayerMovement.class.getDeclaredFields()) assertNotEquals(CdPlayerMovement.ProbeAngleWrite.class, field.getType());
    }

    @Test void prefixWritePortsAreNotRetainedOrReplayedByLaterPureOrPrefixCalls() {
        var f = new Fixture(); var owner = f.owner(); var a = prefixMotion(0x00401234, 0x004CABCD, 0, 0, 4);
        var first = new PrefixAngles(0x12, 0x34); var result = owner.checkFloorWithPrefix(a, first);
        var events = List.copyOf(first.events); assertEquals(result, owner.checkFloor(a));
        assertEquals(events, first.events); var second = new PrefixAngles(0x56, 0x78);
        assertEquals(result, owner.checkFloorWithPrefix(a, second)); assertEquals(events, second.events);
        assertEquals(events, first.events); assertEquals(result, f.owner().checkFloor(a));
    }

    private record PrefixStore(CdPlayerMovement.AngleBuffer buffer, int rawByte) { }
    private static final class PrefixAngles implements CdPlayerMovement.ProbeAngleWrite {
        final List<PrefixStore> events = new ArrayList<>(); int primary, secondary;
        PrefixAngles(int primary, int secondary) { this.primary = primary; this.secondary = secondary; }
        @Override public void store(CdPlayerMovement.AngleBuffer buffer, int rawByte) {
            assertTrue((rawByte & ~0xFF) == 0); events.add(new PrefixStore(buffer, rawByte));
            if (buffer == CdPlayerMovement.AngleBuffer.PRIMARY) primary = rawByte; else secondary = rawByte;
        }
    }
    private static CdPlayerMotionState prefixMotion(int x, int y, int xv, int yv, int spriteFlags) {
        return new CdPlayerMotionState(x, y, xv, yv, -7, 6, 0x9A, 0xFF, spriteFlags, 9, 0x13,
                0x1A, 0x1D, 1, 0x2E, -2, 0x80, 0xFF, 0x41, 0x52);
    }
    private static void assertPrefixWord(CdPlayerMovement.UnownedProbeRead failure,
                                         CdPlayerMovement.AngleBuffer buffer, int layoutOffset, int chunkOffset) {
        assertEquals(buffer, failure.buffer()); var terrainRead = failure.terrainRead();
        assertSame(terrainRead, failure.terrainRead()); assertSame(terrainRead, failure.getCause());
        assertEquals(new CdPlayerTerrain.TerrainWord(
                new com.openggf.game.soniccd.level.CdTerrainCollision.Address(layoutOffset, 0xF9, 122, chunkOffset),
                com.openggf.game.soniccd.level.CdTerrainCollision.UnavailableReason.OUTSIDE_SUPPLIED_CHUNKS), terrainRead.detail());
        var original = terrainRead.originalException(); assertEquals(IllegalStateException.class, original.getClass());
        assertSame(original, terrainRead.originalException()); assertSame(original, terrainRead.getCause()); assertNull(original.getCause());
        assertEquals("Unavailable native collision word at $" + Integer.toHexString(0x210000 | chunkOffset)
                + ": OUTSIDE_SUPPLIED_CHUNKS", original.getMessage());
        assertEquals(original.getMessage(), terrainRead.getMessage()); assertEquals(terrainRead.getMessage(), failure.getMessage());
    }

    // Root R2: an old genuine marker thrown by the public callback is a host interruption.
    @ParameterizedTest @CsvSource({"FLOOR,PRIMARY", "FLOOR,SECONDARY", "CEILING,PRIMARY", "WALL,PRIMARY"})
    void prefixRetainedTerrainMarkerThrownByCallbackKeepsItsExactIdentity(String family, String foot) {
        var unavailable = new Fixture(false); unavailable.fg[3] = (byte) 0xF9;
        var prior = assertThrows(CdPlayerMovement.UnownedProbeRead.class,
                () -> unavailable.owner().checkFloorWithPrefix(
                        prefixMotion(0x01008000, 0x00611234, 0, 0, 0x44), (buffer, raw) -> { }));
        var retained = prior.terrainRead();
        var f = new Fixture();
        if (family.equals("CEILING")) { f.row(3, 0x5001); f.angles[1] = 3; }
        if (family.equals("WALL")) f.column(5, 0x4002);
        var owner = f.owner();
        var a = family.equals("CEILING")
                ? prefixMotion(0x00401234, 0x0050ABCD, 0, 0, 4)
                : prefixMotion(0x00401234, 0x004CABCD, 0, 0, 4);
        var target = CdPlayerMovement.AngleBuffer.valueOf(foot);
        var events = new ArrayList<PrefixStore>();
        CdPlayerMovement.ProbeAngleWrite callback = (buffer, raw) -> {
            events.add(new PrefixStore(buffer, raw));
            if (buffer == target && (!family.equals("WALL") || events.size() > 2)) throw retained;
        };
        var actual = assertThrows(RuntimeException.class, () -> {
            switch (family) {
                case "FLOOR" -> owner.checkFloorWithPrefix(a, callback);
                case "CEILING" -> owner.getCeilDistWithPrefix(a, callback);
                case "WALL" -> owner.calcRoomInFrontWithPrefix(a, 0xC0, callback);
                default -> throw new AssertionError(family);
            }
        });
        assertSame(retained, actual, "a callback cannot become this leaf's native-read stop");
        var primary = CdPlayerMovement.AngleBuffer.PRIMARY;
        var secondary = CdPlayerMovement.AngleBuffer.SECONDARY;
        List<PrefixStore> expected = switch (family) {
            case "FLOOR" -> target == primary ? List.of(new PrefixStore(primary, 0))
                    : List.of(new PrefixStore(primary, 0), new PrefixStore(secondary, 0));
            case "CEILING" -> List.of(new PrefixStore(primary, 3));
            case "WALL" -> List.of(new PrefixStore(primary, 0xC0), new PrefixStore(secondary, 0xC0),
                    new PrefixStore(primary, 0xC0));
            default -> throw new AssertionError(family);
        };
        assertEquals(expected, events, "only reached callback stores survive the interruption");
        assertSame(retained, prior.terrainRead());
    }

    // TimeWarp call-cut/resume: authored before its Movement implementation; no owner or input is retained.
    @ParameterizedTest @CsvSource({"0,MD_GROUND_AFTER_TIME_WARP", "2,MD_AIR_AFTER_TIME_WARP",
            "4,MD_ROLL_AFTER_TIME_WARP", "6,MD_JUMP_AFTER_TIME_WARP"})
    void timeWarpCutAndOriginalSelectedTailReproduceTheOrdinaryNoWarpMode(
            int mode, CdPlayerMovement.Continuation expectedContinuation) {
        Fixture f = new Fixture(false); var owner = f.owner();
        Actor a = (mode & 4) != 0 ? rolled() : new Actor();
        a.flags = mode; a.x = 0x00401234; a.y = 0x00C8ABCD; a.xv = 0x123; a.yv = 0x100;
        a.gv = 0x200; a.anim = 7; a.lock = 5;
        var initial = a.state(); var input = new CdPlayerMovement.Input(8, 0); var env = f.environment();
        var ordinary = owner.step(initial, input, env);
        var stopped = owner.stepToTimeWarp(initial, input, env, Optional.empty());
        assertEquals(CdPlayerMovement.Boundary.TIME_WARP, stopped.boundary());
        assertEquals(expectedContinuation, stopped.continuation());
        assertEquals(mode == 2 ? with(initial, 11, 0) : initial, stopped.state(),
                "Only the actual MdAir prelude resets animation; no velocity/integration tail ran");
        assertTrue(stopped.effects().isEmpty());
        var resumed = owner.resumeAfterTimeWarp(stopped, stopped.environment(), input, Optional.empty());
        assertEquals(ordinary.state(), resumed.state()); assertEquals(ordinary.environment(), resumed.environment());
        assertEquals(ordinary.boundary(), resumed.boundary()); assertEquals(ordinary.continuation(), resumed.continuation());
        var combined = new ArrayList<>(stopped.effects()); combined.addAll(resumed.effects());
        assertEquals(ordinary.effects(), combined);
        assertEquals(initial, a.state()); assertEquals(env, f.environment());
    }

    @ParameterizedTest @CsvSource({"0,0", "0,255", "1,255", "255,1"})
    void explicitCutOccursAtTheNativeCallEvenWhenChargeOrDirectionMakesWarpReturn(int charge, int direction) {
        Fixture f = new Fixture(); f.gates = new CdPlayerMovement.Gates(0, 1, 0, 0, 0, 0, direction);
        var a = new Actor(); a.charge = charge; a.anim = 5;
        var stopped = f.owner().stepToTimeWarp(a.state(), new CdPlayerMovement.Input(0, 0), f.environment(), Optional.empty());
        assertEquals(CdPlayerMovement.Boundary.TIME_WARP, stopped.boundary());
        assertEquals(CdPlayerMovement.Continuation.MD_GROUND_AFTER_TIME_WARP, stopped.continuation());
        assertEquals(1, stopped.environment().globals().boredTimer(), "Boredom occurs before the native call");
        assertEquals(charge, stopped.state().charge()); assertTrue(stopped.effects().isEmpty());
        if (charge == 0 && direction != 0) {
            var failure = assertThrows(CdPlayerMovement.UnsupportedMotion.class,
                    () -> f.step(a, 0, 0));
            assertEquals(CdPlayerMovement.UnsupportedBoundary.TIME_WARP_ACTIVE, failure.boundary(),
                    "Legacy entry still has no TimeWarp owner");
        }
    }

    @Test void timeWarpResumeUsesActualLatchedHeldAndTapInsteadOfAZeroInputTail() {
        Fixture f = new Fixture(); var owner = f.owner(); Actor a = new Actor(); a.y = 0x004CABCD;
        var input = new CdPlayerMovement.Input(0x10, 0x10);
        var stopped = owner.stepToTimeWarp(a.state(), input, f.environment(), Optional.empty());
        assertEquals(0, stopped.state().playerJump()); assertEquals(0, stopped.state().yVelocity());
        assertTrue(stopped.effects().isEmpty());
        var resumed = owner.resumeAfterTimeWarp(stopped, stopped.environment(), input, Optional.empty());
        assertEquals(CdPlayerMovement.Boundary.JUMP_MODE_RETURN, resumed.boundary());
        assertEquals(-0x680, resumed.state().yVelocity()); assertEquals(6, resumed.state().flags());
        assertEquals(1, resumed.state().playerJump()); assertEquals(0x0051ABCD, resumed.state().centreY16_16());
        assertEquals(1, resumed.effects().stream().filter(e -> e.equals(
                new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_JUMP))).count());
        assertEquals(owner.step(a.state(), input, f.environment()), resumed);
    }

    @Test void postWarpRollUnrollDoesNotRedispatchToGroundBeforeTheExistingKillContinuation() {
        Fixture f = new Fixture(false); var owner = f.owner(); Actor a = rolled();
        f.bounds = new CdPlayerMovement.Bounds(0, 0x2000, 0xFF20, 0);
        a.y = 200 << 16; a.gv = 0;
        var input = new CdPlayerMovement.Input(0, 0);
        var stopped = owner.stepToTimeWarp(a.state(), input, f.environment(), Optional.empty());
        assertEquals(4, stopped.state().flags());
        var kill = owner.resumeAfterTimeWarp(stopped, stopped.environment(), input, Optional.empty());
        assertEquals(CdPlayerMovement.Boundary.KILL_PLAYER, kill.boundary());
        assertEquals(0, kill.state().flags());
        assertEquals(CdPlayerMovement.Continuation.MD_ROLL_AFTER_LEVEL_BOUND, kill.continuation());
        assertEquals(owner.step(a.state(), input, f.environment()), kill);
        assertEquals(resume(owner, kill, killAt(kill, 1)),
                owner.resumeAfterLevelBound(kill, kill.state(), kill.environment()));
    }

    @ParameterizedTest @CsvSource({"0", "2", "4", "6"})
    void timeWarpResumePreservesARealLaterKillStopAndItsSingleUnfinishedTail(int mode) {
        Fixture f = new Fixture(false); var owner = f.owner();
        f.bounds = new CdPlayerMovement.Bounds(0, 0x2000, 0xFF20, 0);
        Actor a = (mode & 4) != 0 ? rolled() : new Actor();
        a.flags = mode; a.y = 200 << 16; a.gv = 0x200; a.xv = 0x123; a.yv = 0x100;
        var input = new CdPlayerMovement.Input(8, 0);
        var expected = owner.step(a.state(), input, f.environment());
        var stopped = owner.stepToTimeWarp(a.state(), input, f.environment(), Optional.empty());
        var resumed = owner.resumeAfterTimeWarp(stopped, stopped.environment(), input, Optional.empty());
        assertEquals(CdPlayerMovement.Boundary.KILL_PLAYER, resumed.boundary());
        assertEquals(expected, resumed);
        assertEquals(new CdPlayerMovement.KillRequest(), resumed.effects().getLast());
        assertEquals(resume(owner, expected, killAt(expected, 1)), resume(owner, resumed, killAt(resumed, 1)));
    }

    @Test void returnedWarpEnvironmentMayChangeOnlyTheActualDirectionByte() {
        Fixture f = new Fixture(false); f.gates = new CdPlayerMovement.Gates(0, 1, 0, 0, 0, 0, 255);
        var owner = f.owner(); var input = new CdPlayerMovement.Input(8, 0); Actor a = airborne(); a.y = 200 << 16;
        var stopped = owner.stepToTimeWarp(a.state(), input, f.environment(), Optional.empty());
        var e = stopped.environment(); var g = e.globals();
        var changed = warpEnvironmentWithDirection(e, 0);
        var resumed = owner.resumeAfterTimeWarp(stopped, changed, input, Optional.empty());
        assertEquals(0, resumed.environment().gates().timeWarpDirection());
        var foreign = List.of(
                new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600, 13, 0x80), e.bounds(), e.gates(), g),
                new CdPlayerMovement.Environment(e.physics(), new CdPlayerMovement.Bounds(1, 0x2000, 0x710, 0), e.gates(), g),
                new CdPlayerMovement.Environment(e.physics(), e.bounds(), new CdPlayerMovement.Gates(0, 2, 0, 0, 0, 0, 0), g),
                new CdPlayerMovement.Environment(e.physics(), e.bounds(), new CdPlayerMovement.Gates(0, 1, 1, 0, 0, 0, 0), g),
                new CdPlayerMovement.Environment(e.physics(), e.bounds(), new CdPlayerMovement.Gates(0, 1, 0, 1, 0, 0, 0), g),
                new CdPlayerMovement.Environment(e.physics(), e.bounds(), new CdPlayerMovement.Gates(0, 1, 0, 0, 1, 0, 0), g),
                new CdPlayerMovement.Environment(e.physics(), e.bounds(), new CdPlayerMovement.Gates(0, 1, 0, 0, 0, 1, 0), g),
                new CdPlayerMovement.Environment(e.physics(), e.bounds(), e.gates(), new CdPlayerMovement.Globals(
                        g.lookMode(), g.cameraYCentre(), 3, g.secondaryAngle(), g.boredTimer(), g.scoreChain(),
                        g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance())));
        for (var value : foreign) assertThrows(IllegalArgumentException.class,
                () -> owner.resumeAfterTimeWarp(stopped, value, input, Optional.empty()));
        assertEquals(255, e.gates().timeWarpDirection());
    }

    @Test void timeWarpCutKeepsEarlierAdmissionAndRampReadsBeforeTheUnownedCall() {
        Fixture f = new Fixture(); var owner = f.owner(); Actor a = new Actor();
        a.routine = 4;
        assertEquals(CdPlayerMovement.UnsupportedBoundary.ROUTINE, assertThrows(CdPlayerMovement.UnsupportedMotion.class,
                () -> owner.stepToTimeWarp(a.state(), new CdPlayerMovement.Input(0, 0), f.environment(), Optional.empty())).boundary());
        a.routine = 2; a.ctrl = 1;
        assertEquals(CdPlayerMovement.UnsupportedBoundary.OBJECT_CONTROL, assertThrows(CdPlayerMovement.UnsupportedMotion.class,
                () -> owner.stepToTimeWarp(a.state(), new CdPlayerMovement.Input(0, 0), f.environment(), Optional.empty())).boundary());
        a.ctrl = 0; f.gates = new CdPlayerMovement.Gates(0, 0, 0, 0, 0, 0, 255);
        assertEquals(CdPlayerMovement.UnsupportedBoundary.SOURCE_FAMILY, assertThrows(CdPlayerMovement.UnsupportedMotion.class,
                () -> owner.stepToTimeWarp(a.state(), new CdPlayerMovement.Input(0, 0), f.environment(), Optional.empty())).boundary());
        f.gates = new CdPlayerMovement.Gates(0, 1, 0, 0, 0, 0, 255);
        f.fg[2] = 6;
        var ramp = f.owner().stepToTimeWarp(a.state(), new CdPlayerMovement.Input(0, 0), f.environment(), Optional.empty());
        assertEquals(2, ramp.state().playerControl(), "Actual raw ramp table is read before the TimeWarp cut");
        assertEquals(0, a.state().playerControl());
    }

    @Test void warpCutPerformsRampExitExactlyBeforeTheCallAndResumeUsesTheAlteredVelocity() {
        Fixture f = new Fixture(false); var owner = f.owner(); Actor a = airborne();
        a.x = 0x00401234; a.y = 200 << 16; a.ctrl = 2; a.yv = -0x900; a.xv = 0;
        var input = new CdPlayerMovement.Input(0, 0);
        var stopped = owner.stepToTimeWarp(a.state(), input, f.environment(), Optional.empty());
        assertEquals(0, stopped.state().playerControl()); assertEquals(0x600, stopped.state().xVelocity());
        assertEquals(a.x, stopped.state().centreX16_16()); assertEquals(a.y, stopped.state().centreY16_16());
        var ordinary = owner.step(a.state(), input, f.environment());
        var resumed = owner.resumeAfterTimeWarp(stopped, stopped.environment(), input, Optional.empty());
        assertEquals(ordinary, resumed);
    }

    @Test void borrowedSupportIsReadOnlyByTheReachedPostWarpTailAndNeverDefaulted() {
        Fixture f = new Fixture(false); var owner = f.owner(); Actor a = supported(); a.gv = 0x200; a.lock = 1;
        var input = new CdPlayerMovement.Input(0, 0);
        var stopped = owner.stepToTimeWarp(a.state(), input, f.environment(), Optional.empty());
        assertEquals(CdPlayerMovement.Boundary.TIME_WARP, stopped.boundary());
        var failure = assertThrows(CdPlayerMovement.UnsupportedMotion.class,
                () -> owner.resumeAfterTimeWarp(stopped, stopped.environment(), input, Optional.empty()));
        assertEquals(CdPlayerMovement.UnsupportedBoundary.OBJECT_SUPPORT, failure.boundary());
        var pool = new CdObjectPool(); putSupport(pool, 32, 0x12, 0, 12, 64, 96);
        var objects = context(pool, 32); var before = pool.capture();
        var resumed = owner.resumeAfterTimeWarp(stopped, stopped.environment(), input, objects);
        assertEquals(owner.step(a.state(), input, f.environment(), objects), resumed);
        assertEquals(before.slots(), pool.capture().slots());
    }

    @Test void warpResultContinuationInvariantSeparatesBothUnfinishedCallFamilies() {
        var state = new Actor().state(); var env = new Fixture().environment();
        for (var boundary : CdPlayerMovement.Boundary.values()) {
            for (var continuation : CdPlayerMovement.Continuation.values()) {
                boolean kill = continuation.name().endsWith("_AFTER_LEVEL_BOUND");
                boolean warp = continuation.name().endsWith("_AFTER_TIME_WARP");
                boolean valid = boundary == CdPlayerMovement.Boundary.KILL_PLAYER ? kill
                        : boundary == CdPlayerMovement.Boundary.TIME_WARP ? warp
                        : continuation == CdPlayerMovement.Continuation.NONE;
                if (valid) assertDoesNotThrow(() -> new CdPlayerMovement.Result(state, env, List.of(), boundary, continuation));
                else assertThrows(IllegalArgumentException.class,
                        () -> new CdPlayerMovement.Result(state, env, List.of(), boundary, continuation));
            }
        }
    }

    @Test void warpResumeRejectsCompletedStopsOtherFamiliesAndNullsWithoutRetainingInput() {
        Fixture f = new Fixture(); var owner = f.owner(); var input = new CdPlayerMovement.Input(8, 0);
        var stopped = owner.stepToTimeWarp(new Actor().state(), input, f.environment(), Optional.empty());
        var completed = owner.resumeAfterTimeWarp(stopped, stopped.environment(), input, Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> owner.resumeAfterTimeWarp(completed, completed.environment(), input, Optional.empty()));
        var pit = new Fixture(false); var killed = pit.owner().step(airPit().state(), input, pit.environment());
        assertThrows(IllegalArgumentException.class,
                () -> owner.resumeAfterTimeWarp(killed, killed.environment(), input, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> owner.resumeAfterLevelBound(stopped, stopped.state(), stopped.environment()));
        assertThrows(NullPointerException.class, () -> owner.resumeAfterTimeWarp(null, stopped.environment(), input, Optional.empty()));
        assertThrows(NullPointerException.class, () -> owner.resumeAfterTimeWarp(stopped, null, input, Optional.empty()));
        assertThrows(NullPointerException.class, () -> owner.resumeAfterTimeWarp(stopped, stopped.environment(), null, Optional.empty()));
        assertThrows(NullPointerException.class, () -> owner.resumeAfterTimeWarp(stopped, stopped.environment(), input, null));
        for (var component : CdPlayerMovement.Result.class.getRecordComponents()) {
            assertNotEquals(CdPlayerMovement.Input.class, component.getType());
            assertNotEquals(CdPlayerMovement.ObjectContext.class, component.getType());
        }
    }

    private static CdPlayerMovement.Environment warpEnvironmentWithDirection(CdPlayerMovement.Environment e, int direction) {
        var g = e.gates();
        return new CdPlayerMovement.Environment(e.physics(), e.bounds(), new CdPlayerMovement.Gates(
                g.zoneAct(), g.timeZone(), g.miniSonic(), g.waterSlide(), g.windTunnel(), g.speedShoes(), direction), e.globals());
    }
}
