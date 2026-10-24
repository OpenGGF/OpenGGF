package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

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

    private static void assertBoundary(Fixture f, Actor a, CdPlayerMovement.UnsupportedBoundary boundary) {
        var ex = assertThrows(CdPlayerMovement.UnsupportedMotion.class, () -> f.step(a, 0, 0));
        assertEquals(boundary, ex.boundary());
    }
    private static Actor rolled() { Actor a = new Actor(); a.flags = 4; a.xr = 7; a.yr = 14; a.y = 81 << 16; a.anim = 2; return a; }
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
