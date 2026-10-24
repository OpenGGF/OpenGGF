package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    @Test void nativeRampGateReadsDataRawChunkAndHighXByteWithY380Mask() {
        Fixture f = new Fixture(false); f.fg[2 + 2 * 42 + 3] = 6;
        Actor a = new Actor(); a.x = 0x03010000; a.y = 0x0A010000;
        assertBoundary(f, a, CdPlayerMovement.UnsupportedBoundary.RAMP_ENTRY);
        f.ramps[0] = 9; f.ramps[1] = 10; f.ramps[2] = 11; f.ramps[3] = 12; f.ramps[4] = 13;
        assertDoesNotThrow(() -> f.step(a, 0, 0)); // No hardcoded ramp table substitute.
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

    /** Exact admitted byte shapes, decoded by actual production asset/terrain owners. */
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
}
