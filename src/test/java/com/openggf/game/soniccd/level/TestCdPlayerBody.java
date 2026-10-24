package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerCharge;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerObjectTouch;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdObjectState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Package placement deliberately
 * permits construction of the real asset aggregate from production decoders,
 * without a second production constructor, reflection, mocks or media skips.
 * Constructed format inputs do not authenticate a retail disc or native frame.
 */
class TestCdPlayerBody {
    @Test void initIsOnlyInitializationAfterOnePredispatchAndKeepsUnwrittenIngress() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.routine = 0; a.charge = 29;
        a.xr = 3; a.yr = 4; a.spr = 0xC3; a.pri = 0x12; a.sec = 0x34;
        a.header = new CdPlayerBody.Header(1, 0, 0x1111, 8, 9);
        a.cursor = new CdPlayerBody.AnimationCursor(3, 0xFE, 0);
        byte[] history = historyBytes(); a.history = new CdPlayerBody.History(CdAssetBytes.copyOf(history), 0xFC);
        var original = a.state(); var env = f.environment();
        var r = f.owner().step(original, env);
        assertEquals(CdPlayerBody.Boundary.INIT_RETURN, r.boundary());
        assertEquals(2, r.state().motion().routine()); assertEquals(30, r.state().motion().charge());
        assertEquals(9, r.state().motion().xRadius()); assertEquals(19, r.state().motion().yRadius());
        assertEquals(4, r.state().motion().spriteFlags());
        assertEquals(new CdPlayerBody.Header(1, f.mapAddress(), 0x780, 2, 0x18), r.state().header());
        assertEquals(new CdPlayerMovement.Physics(0x600, 12, 0x80), r.environment().movement().physics());
        assertEquals(original.animation(), r.state().animation());
        assertEquals(original.history(), r.state().history()); assertEquals(original.timers(), r.state().timers());
        assertEquals(0x12, r.state().motion().previousPrimaryAngle());
        assertEquals(0x34, r.state().motion().previousSecondaryAngle());
        assertEquals(env.controls(), r.environment().controls()); assertEquals(env.globals(), r.environment().globals());
        assertTrue(r.display().isEmpty()); assertTrue(r.modeBoundary().isEmpty());
        assertEquals(List.of(CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH, CdPlayerBody.NativeCall.INIT), calls(r));
        assertEquals(29, original.motion().charge()); assertEquals(0, original.motion().routine());
    }

    @Test void absentSlotDoesNoChargeRoutineAdmissionOrTailWork() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.charge = 255; a.routine = 7;
        a.header = new CdPlayerBody.Header(0, 0, 0, 0, 0); f.debugMode = 0x100;
        var state = a.state(); var env = f.environment(); var r = f.owner().step(state, env);
        assertSame(state, r.state()); assertSame(env, r.environment());
        assertEquals(CdPlayerBody.Boundary.SLOT_ABSENT, r.boundary());
        assertTrue(r.effects().isEmpty()); assertTrue(r.display().isEmpty());
    }

    @ParameterizedTest @CsvSource({"0,29,30", "4,44,45", "0,255,0", "4,255,0"})
    void predispatchUsesPriorRollingBitAndNativeByteWrapEvenDuringInit(int flags, int charge, int expected) {
        Fixture f = new Fixture(); Actor a = new Actor(); a.routine = 0; a.flags = flags; a.charge = charge;
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(expected, r.state().motion().charge());
        assertEquals(1, calls(r).stream().filter(c -> c == CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH).count());
    }

    @Test void mainComposesActualModeThenDisplayHistoryWaterCopiesAnimationTouchAndChunks() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.pri = 0x12; a.sec = 0x34; a.spr = 0xC4;
        f.rawHeld = 8; f.rawTap = 8; f.logicalHeld = 4; f.logicalTap = 4;
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, r.boundary());
        assertEquals(CdPlayerMovement.Boundary.MODE_RETURN, r.modeBoundary().orElseThrow());
        assertEquals(12, r.state().motion().groundVelocity());
        assertEquals(0x00400C00, r.state().motion().centreX16_16());
        assertEquals(8, r.environment().controls().logicalHeld()); assertEquals(8, r.environment().controls().logicalTap());
        assertEquals(0, r.state().motion().previousPrimaryAngle());
        assertEquals(0, r.state().motion().previousSecondaryAngle());
        assertEquals(0x84, r.state().motion().spriteFlags(), "SpecialChunks clears bit6; queue/SAT owner writes bit7");
        assertEquals(7, r.state().animation().mapFrame()); assertEquals(3, r.state().animation().timer());
        assertEquals(1, r.state().animation().scriptIndex());
        assertEquals(List.of(CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH, CdPlayerBody.NativeCall.EXTENDED_CAMERA,
                CdPlayerBody.NativeCall.WATERFALL_SPLASH_CHECK, CdPlayerBody.NativeCall.MODE,
                CdPlayerBody.NativeCall.PP_SPECIAL_COLLISION, CdPlayerBody.NativeCall.DISPLAY,
                CdPlayerBody.NativeCall.RECORD_POSITION, CdPlayerBody.NativeCall.PP_WATER,
                CdPlayerBody.NativeCall.ANIMATE, CdPlayerBody.NativeCall.SPECIAL_CHUNKS), calls(r));
        assertEquals(1, r.effects().stream().filter(CdPlayerBody.AnimationPublication.class::isInstance).count());
        assertTrue(r.effects().contains(new CdPlayerBody.TouchScanCompleted(96)));
        var pose = r.display().orElseThrow(); assertTrue(pose.queued());
        assertEquals(7, pose.mapFrame()); assertEquals(0x84, pose.spriteFlags());
        assertEquals(f.mapAddress(), pose.mappingAddress());
        int display = indexOf(r, CdPlayerBody.DisplayEligibility.class);
        assertTrue(display < indexOf(r, CdPlayerBody.PositionHistoryWrite.class));
        assertTrue(indexOf(r, CdPlayerBody.PositionHistoryWrite.class) < indexOf(r, CdPlayerBody.AnimationPublication.class));
        assertTrue(indexOf(r, CdPlayerBody.AnimationPublication.class) < indexOf(r, CdPlayerBody.TouchScanCompleted.class));
    }

    @Test void lockedControlsRetainBothNonzeroLogicalBytesAndNeverCopyRawPair() {
        Fixture f = new Fixture(); Actor a = new Actor();
        f.ctrlLocked = 1; f.rawHeld = 4; f.rawTap = 4; f.logicalHeld = 8; f.logicalTap = 8;
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(12, r.state().motion().groundVelocity());
        assertEquals(8, r.environment().controls().logicalHeld()); assertEquals(8, r.environment().controls().logicalTap());
        assertFalse(r.effects().stream().anyMatch(e -> e instanceof CdPlayerBody.GlobalWrite w
                && (w.field() == CdPlayerBody.GlobalField.LOGICAL_HELD || w.field() == CdPlayerBody.GlobalField.LOGICAL_TAP)));
    }

    @Test void ordinaryAirMainIncrementsChargeOnceRatherThanOncePerComposedOwner() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.flags = 2; a.y = 200 << 16; a.charge = 20;
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(21, r.state().motion().charge());
        assertEquals(1, calls(r).stream().filter(c -> c == CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH).count());
    }

    @Test void jumpPoppedModeReturnStillRunsMainTailAndCopiesCurrentBuffersOnlyAfterMode() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.pri = 0x12; a.sec = 0x34;
        f.rawHeld = f.rawTap = 0x10;
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(CdPlayerMovement.Boundary.JUMP_MODE_RETURN, r.modeBoundary().orElseThrow());
        assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, r.boundary());
        assertEquals(0x00510000, r.state().motion().centreY16_16());
        assertEquals(-0x680, r.state().motion().yVelocity());
        assertEquals(0x80, r.state().motion().previousPrimaryAngle());
        assertEquals(0x80, r.state().motion().previousSecondaryAngle());
        assertEquals(0x51, r.state().history().bytes().unsignedWord(2));
        assertTrue(r.effects().contains(new CdPlayerBody.ModeEffect(
                new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_JUMP))));
        assertEquals(7, r.state().animation().mapFrame());
    }

    @Test void modeHistoricalChargeWritesAreNotReappliedOverCompletedMovement() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.charge = 29; a.gv = 0xC00;
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(0, r.state().motion().charge());
        assertEquals(3059, r.state().motion().groundVelocity());
        assertEquals(12, r.environment().movement().physics().acceleration());
        assertTrue(r.effects().contains(new CdPlayerBody.ModeEffect(new CdPlayerMovement.ChargeEffect(
                new CdPlayerCharge.SoundRequest(CdPlayerCharge.Sound.FM_91)))));
    }

    @Test void positionRecordWritesCurrentOffsetBeforeLowByteWrapAndOwnsAllBytes() {
        Fixture f = new Fixture(); Actor a = new Actor();
        byte[] original = historyBytes(); var immutable = CdAssetBytes.copyOf(original);
        a.history = new CdPlayerBody.History(immutable, 0xFC); a.x |= 0x1234; a.y |= 0xABCD;
        f.rawHeld = 8;
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(0, r.state().history().index());
        assertEquals(64, r.state().history().bytes().unsignedWord(0xFC));
        assertEquals(76, r.state().history().bytes().unsignedWord(0xFE));
        for (int i = 0; i < 0xFC; i++) assertEquals(Byte.toUnsignedInt(original[i]), r.state().history().bytes().unsignedByte(i));
        assertArrayEquals(original, immutable.copy());
        assertEquals(0x00401E34, r.state().motion().centreX16_16());
        assertEquals(0x004CABCD, r.state().motion().centreY16_16());
    }

    @Test void suppliedEvenHistoryOffsetNeedsNoInventedFourByteAlignment() {
        Fixture f = new Fixture(); Actor a = new Actor();
        a.history = new CdPlayerBody.History(CdAssetBytes.copyOf(historyBytes()), 2);
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(6, r.state().history().index());
        assertEquals(64, r.state().history().bytes().unsignedWord(2));
        assertEquals(76, r.state().history().bytes().unsignedWord(4));
        assertEquals(0xA5, r.state().history().bytes().unsignedByte(0));
    }

    @Test void animationFailureAfterTimersAndHistoryLeavesCapturedValuesReusable() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.invinc = 1; f.invincible = 1;
        a.cursor = new CdPlayerBody.AnimationCursor(0xFE, 0, 198); a.anim = a.prev = 5;
        var state = a.state(); var env = f.environment(); var owner = f.owner();
        assertThrows(UnsupportedOperationException.class, () -> owner.step(state, env));
        assertEquals(1, state.timers().invincibility()); assertEquals(0, state.history().index());
        assertEquals(1, env.globals().invincible()); assertEquals(0xFE, state.animation().scriptIndex());
        assertEquals(198, state.animation().mapFrame());
        a.cursor = new CdPlayerBody.AnimationCursor(0, 0, 0);
        var admitted = a.state(); var completed = owner.step(admitted, env);
        assertEquals(completed, owner.step(admitted, env));
        assertEquals(1, bodySounds(completed).size());
    }

    @Test void displayReadsDoNotAdvanceAnimationChargeHistoryOrEnvironment() {
        Fixture f = new Fixture(); Actor a = new Actor(); var owner = f.owner();
        var completed = owner.step(a.state(), f.environment());
        var saved = completed.state(); var env = completed.environment();
        for (int i = 0; i < 10; i++) {
            assertEquals(7, completed.display().orElseThrow().mapFrame());
            assertSame(saved, completed.state()); assertSame(env, completed.environment());
        }
        assertEquals(3, saved.animation().timer()); assertEquals(4, saved.history().index());
    }

    @ParameterizedTest @CsvSource({"0,0,true", "3,2,false", "4,3,true", "7,6,true", "8,7,false", "65535,65534,true"})
    void displayUsesPredecrementHurtBitTwo(int hurt, int expected, boolean visible) {
        Fixture f = new Fixture(); Actor a = new Actor(); a.hurt = hurt;
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(expected, r.state().timers().hurt()); assertEquals(visible, r.display().orElseThrow().queued());
    }

    @Test void warpDisplayGateSkipsHurtDecrementButStillExpiresInvincibilityThenShoes() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.hurt = 4; a.invinc = a.shoes = 1;
        f.timeWarpTimer = 210; f.invincible = f.speedShoes = 1;
        f.physics = new CdPlayerMovement.Physics(0xC00, 0x18, 0x80);
        var r = f.owner().step(a.state(), f.environment());
        assertFalse(r.display().orElseThrow().queued()); assertEquals(4, r.state().timers().hurt());
        assertEquals(0, r.state().timers().invincibility()); assertEquals(0, r.state().timers().speedShoes());
        assertEquals(0, r.environment().globals().invincible()); assertEquals(0, r.environment().movement().gates().speedShoes());
        assertEquals(new CdPlayerMovement.Physics(0x600, 12, 0x80), r.environment().movement().physics());
        assertEquals(List.of(new CdPlayerBody.SoundRequest(CdPlayerBody.Sound.SCMD_R1AMUS)), bodySounds(r));
        assertTrue(indexWrite(r, CdPlayerBody.GlobalField.INVINCIBLE) < indexWrite(r, CdPlayerBody.GlobalField.TOP_SPEED));
    }

    @Test void invisibilityStillDecrementsHurtAndPowerupZeroTimersDoNotExpire() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.ctrl = 0x40; a.hurt = 4;
        f.invincible = f.speedShoes = 1;
        var r = f.owner().step(a.state(), f.environment());
        assertFalse(r.display().orElseThrow().queued()); assertEquals(3, r.state().timers().hurt());
        assertEquals(1, r.environment().globals().invincible()); assertEquals(1, r.environment().movement().gates().speedShoes());
        assertTrue(bodySounds(r).isEmpty());
    }

    @Test void inactivePowerupFlagsLeaveNonzeroTimersUntouched() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.invinc = 0xFFFF; a.shoes = 2;
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(0xFFFF, r.state().timers().invincibility()); assertEquals(2, r.state().timers().speedShoes());
    }

    @Test void bossMusicSuppressesExpiryMusicWithoutSuppressingNativeFlagAndPhysicsWrites() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.invinc = a.shoes = 1;
        f.invincible = f.speedShoes = f.bossMusic = 1;
        var r = f.owner().step(a.state(), f.environment());
        assertTrue(bodySounds(r).isEmpty()); assertEquals(0, r.environment().globals().invincible());
        assertEquals(0, r.environment().movement().gates().speedShoes());
    }

    @ParameterizedTest @CsvSource({"1536,160,158", "-1536,160,162", "1535,97,99",
            "1536,96,96", "-1536,224,224", "1536,0,65534", "0,65535,65533", "-32768,160,162"})
    void extendedCameraUsesIncomingGroundSpeedUnsignedCapsAndNativeWordWrap(int speed, int centre, int expected) {
        Fixture f = new Fixture(); Actor a = new Actor(); a.gv = speed; f.camX = centre;
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(expected, r.environment().globals().cameraXCentre());
    }

    @Test void extendedCameraSeesPriorRampBitBeforeModeClearsIt() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.ctrl = 2; a.gv = 0x600; f.camX = 150;
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(152, r.environment().globals().cameraXCentre()); assertEquals(0, r.state().motion().playerControl());
    }

    @Test void waterfallCreationRejectsOnlyActualEvenFrameMaskedChunkXAndControlGate() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.ctrl = 0x40;
        f.cell(0, 0, 0xAF); f.levelFrames = 2;
        assertBoundary(f, a, CdPlayerBody.UnsupportedBoundary.WATERFALL_SPLASH_CREATION);
        f.levelFrames = 3; assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, f.owner().step(a.state(), f.environment()).boundary());
        f.levelFrames = 2; a.ctrl = 0;
        assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, f.owner().step(a.state(), f.environment()).boundary());
        a.ctrl = 0x40; a.x = 0x15C0 << 16; f.cell(21, 0, 0x2F);
        assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, f.owner().step(a.state(), f.environment()).boundary());
    }

    @Test void debugBypassIsBeforeChargeButTimeAttackUsesNormalDispatchAdmission() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.routine = 0; a.charge = 29; f.debugMode = 0x100;
        assertBoundary(f, a, CdPlayerBody.UnsupportedBoundary.DEBUG_UPDATE);
        f.timeAttack = 1;
        assertEquals(30, f.owner().step(a.state(), f.environment()).state().motion().charge());
    }

    @Test void headerTestsOnlyHighByteOfNativeDebugModeWordAndRetainsWholeWord() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.routine = 0; f.debugMode = 1;
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(CdPlayerBody.Boundary.INIT_RETURN, r.boundary());
        assertEquals(1, r.environment().globals().debugMode());
        f.debugMode = 0x8001;
        assertBoundary(f, a, CdPlayerBody.UnsupportedBoundary.DEBUG_UPDATE);
    }

    @Test void rawDebugTapIsCheckedBeforeLogicalCopyAndBeforeModeEvenWhileLocked() {
        Fixture f = new Fixture(); Actor a = new Actor(); f.debugCheat = 0x100; f.ctrlLocked = 1;
        f.rawTap = 0x10; f.logicalTap = 0;
        assertBoundary(f, a, CdPlayerBody.UnsupportedBoundary.DEBUG_ENTRY);
        f.rawTap = 0; f.logicalTap = 0x10; f.logicalHeld = 0x10;
        assertEquals(CdPlayerMovement.Boundary.JUMP_MODE_RETURN,
                f.owner().step(a.state(), f.environment()).modeBoundary().orElseThrow());
    }

    @Test void actualWaterfallCreationGatePrecedesRawDebugEntry() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.ctrl = 0x40;
        f.cell(0, 0, 0x2F); f.levelFrames = 2; f.debugCheat = 1; f.rawTap = 0x10;
        assertBoundary(f, a, CdPlayerBody.UnsupportedBoundary.WATERFALL_SPLASH_CREATION);
        f.levelFrames = 3;
        assertBoundary(f, a, CdPlayerBody.UnsupportedBoundary.DEBUG_ENTRY);
    }

    @Test void windRestoresPreviousAnimationBeforeExactlyOneActualAnimatorCall() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.flags = 2; a.y = 200 << 16;
        a.anim = 0; a.prev = 7; a.gv = 0x600; f.wind = 1;
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(7, r.state().motion().animation()); assertEquals(7, r.state().motion().previousAnimation());
        assertEquals(7, r.state().animation().mapFrame());
        assertEquals(1, r.effects().stream().filter(CdPlayerBody.AnimationPublication.class::isInstance).count());
    }

    @Test void specialChunkLookupUsesRawByteHighByteOfXAndThreeBitYMaskAndClearsBitSix() {
        Fixture f = new Fixture(); Actor a = new Actor();
        a.x = 0x02400000; a.y = 0x094C0000; a.spr = 0xC4; f.special[0] = 0x22; f.bottom = 0x1000;
        f.cell(2, 1, 0x22); // raw loop, X low=$40 and angle0: preserve current high path.
        f.cell(0, 1, 2); f.cell(2, 0, 2);
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(0xC4, r.state().motion().spriteFlags());
        f.cell(2, 1, 0xA2); // masked chunk identity is the same; raw identity is not.
        assertEquals(0x84, f.owner().step(a.state(), f.environment()).state().motion().spriteFlags());
    }

    @Test void specialChunksStillRunsWhenNegativeControlSkipsUnimplementedTouchScan() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.ctrl = 0x80; a.spr = 0xC4;
        f.slots = CdPlayerBody.DynamicSlots.UNIMPLEMENTED;
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(0x84, r.state().motion().spriteFlags());
        assertFalse(r.effects().stream().anyMatch(CdPlayerBody.TouchScanCompleted.class::isInstance));
    }

    @Test void animatorTypedChangeAppliesOnceAndCanSelectNativeTouchSkipWithoutASecondAnimationCall() {
        Fixture f = new Fixture(); Actor a = new Actor();
        f.animationCommand = 0xFD; f.animationOperand = 0x2B;
        f.slots = CdPlayerBody.DynamicSlots.UNIMPLEMENTED;
        a.anim = a.prev = 5; a.flags = 1; a.spr = 0xC6;
        a.cursor = new CdPlayerBody.AnimationCursor(1, 0, 4);
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(0x2B, r.state().motion().animation());
        assertEquals(5, r.state().motion().previousAnimation(), "FD defers the next animation reset");
        assertEquals(1, r.state().animation().scriptIndex()); assertEquals(3, r.state().animation().timer());
        assertEquals(4, r.display().orElseThrow().mapFrame(), "FD retains this call's mapping byte");
        assertEquals(0x85, r.state().motion().spriteFlags());
        assertFalse(r.effects().stream().anyMatch(CdPlayerBody.TouchScanCompleted.class::isInstance));
        var publications = r.effects().stream().filter(CdPlayerBody.AnimationPublication.class::isInstance)
                .map(CdPlayerBody.AnimationPublication.class::cast).toList();
        assertEquals(1, publications.size()); assertEquals(0x2B, publications.getFirst().effects().animationWrite().orElseThrow());
    }

    @Test void displayCutKeepsRadiusAndCentreBeforeSpecialChunksChangesTheCompletedPose() {
        Fixture f = new Fixture(); Actor a = new Actor(); f.special[3] = 2;
        a.y = 0x004CABCD; a.spr = 0xC4;
        var r = f.owner().step(a.state(), f.environment());
        var cut = r.effects().stream().filter(CdPlayerBody.DisplayEligibility.class::isInstance)
                .map(CdPlayerBody.DisplayEligibility.class::cast).findFirst().orElseThrow();
        assertTrue(cut.eligible());
        assertEquals(64, cut.centreXWord()); assertEquals(76, cut.centreYWord());
        assertEquals(19, cut.yRadius()); assertEquals(0xC4, cut.spriteFlags());
        assertEquals(14, r.state().motion().yRadius());
        assertEquals(0x0051ABCD, r.display().orElseThrow().centreY16_16());
        assertEquals(76, r.state().history().bytes().unsignedWord(2));
        assertThrows(UnsupportedOperationException.class, () -> r.effects().clear());
    }

    @Test void suppressedDisplayRetainsItsCutWithoutInventingTheDrawObjectClear() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.hurt = 3; a.spr = 0xC4;
        var r = f.owner().step(a.state(), f.environment());
        var cut = r.effects().stream().filter(CdPlayerBody.DisplayEligibility.class::isInstance)
                .map(CdPlayerBody.DisplayEligibility.class::cast).findFirst().orElseThrow();
        assertFalse(cut.eligible()); assertEquals(64, cut.centreXWord()); assertEquals(76, cut.centreYWord());
        assertEquals(19, cut.yRadius()); assertEquals(0xC4, cut.spriteFlags());
        assertEquals(0x84, r.state().motion().spriteFlags(), "bit6 changes later; queue owner alone writes bit7");
    }

    @Test void matchingRollTunnelUsesActualChargeStartRollAfterAnimationAndKeepsPublishedFrame() {
        Fixture f = new Fixture(); Actor a = new Actor(); f.special[3] = 2;
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(4, r.state().motion().flags() & 4); assertEquals(2, r.state().motion().animation());
        assertEquals(7, r.state().animation().mapFrame()); assertEquals(81 << 16, r.state().motion().centreY16_16());
        assertEquals(76, r.state().history().bytes().unsignedWord(2), "history precedes SpecialChunks radius shift");
        assertEquals(0x200, r.state().motion().groundVelocity());
        assertTrue(bodySounds(r).isEmpty(), "USA source omits roll-entry sound here");
        f.variant = CdReleaseVariant.JAPAN;
        assertEquals(List.of(new CdPlayerBody.SoundRequest(CdPlayerBody.Sound.FM_9C)),
                bodySounds(f.owner().step(a.state(), f.environment())));
    }

    @Test void touchFailureAfterAllEarlierStagedWorkPublishesNothingAndRetryIsDeterministic() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.invinc = 1; f.invincible = 1;
        f.slots = CdPlayerBody.DynamicSlots.UNIMPLEMENTED;
        var state = a.state(); var env = f.environment(); var owner = f.owner();
        assertThrows(CdPlayerBody.UnsupportedBody.class, () -> owner.step(state, env));
        assertEquals(1, state.timers().invincibility()); assertEquals(1, env.globals().invincible());
        assertEquals(0, state.animation().mapFrame()); assertEquals(0, state.history().index());
        f.slots = CdPlayerBody.DynamicSlots.EMPTY_DEVELOPMENT_96;
        var good = f.environment(); var r = owner.step(state, good);
        assertEquals(r, owner.step(state, good));
        assertEquals(List.of(new CdPlayerBody.SoundRequest(CdPlayerBody.Sound.SCMD_R1AMUS)), bodySounds(r));
        assertThrows(UnsupportedOperationException.class, () -> r.effects().clear());
    }

    @Test void replayRestoresWholeExplicitActorEnvironmentAndCompleteHistoryRatherThanReinitializing() {
        Fixture f = new Fixture(); var owner = f.owner(); Actor a = new Actor();
        a.history = new CdPlayerBody.History(CdAssetBytes.copyOf(historyBytes()), 0xFC);
        var saved = a.state(); var env = f.environment();
        var first = owner.step(saved, env);
        var next = owner.step(first.state(), first.environment());
        var replay = owner.step(saved, env);
        assertEquals(first, replay); assertEquals(next, owner.step(replay.state(), replay.environment()));
        assertEquals(0xFC, saved.history().index()); assertEquals(0, saved.animation().mapFrame());
    }

    @ParameterizedTest @CsvSource({"0,MD_GROUND_AFTER_LEVEL_BOUND", "2,MD_AIR_AFTER_LEVEL_BOUND",
            "4,MD_ROLL_AFTER_LEVEL_BOUND", "6,MD_JUMP_AFTER_LEVEL_BOUND"})
    void killRejectsUnfinishedExactModeContinuationWithoutMainTail(int mode, CdPlayerMovement.Continuation continuation) {
        Fixture f = new Fixture(); Actor a = new Actor(); a.flags = mode; a.y = 0x1000 << 16;
        a.anim = (mode & 4) == 0 ? 0 : 2;
        var state = a.state(); var env = f.environment();
        var ex = assertThrows(CdPlayerBody.UnsupportedBody.class, () -> f.owner().step(state, env));
        assertEquals(CdPlayerBody.UnsupportedBoundary.KILL_PLAYER, ex.boundary());
        assertEquals(continuation, ex.continuation());
        assertEquals(0, state.history().index()); assertEquals(0, state.animation().mapFrame());
    }

    @ParameterizedTest @CsvSource({"4,HURT_ROUTINE", "6,DEAD_ROUTINE", "8,RESTART_ROUTINE", "1,ROUTINE_DISPATCH"})
    void unownedHeaderRoutinesHaveExplicitBoundaries(int routine, CdPlayerBody.UnsupportedBoundary boundary) {
        Fixture f = new Fixture(); Actor a = new Actor(); a.routine = routine;
        assertBoundary(f, a, boundary);
    }

    @Test void miniInitObjectControlHistoryAndTouchRejectAtTheirOwningPredicates() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.routine = 0; f.mini = 1;
        assertBoundary(f, a, CdPlayerBody.UnsupportedBoundary.MINIATURE_INIT);
        f.mini = 0; a.routine = 2; a.ctrl = 1;
        assertBoundary(f, a, CdPlayerBody.UnsupportedBoundary.OBJECT_CONTROL);
        a.ctrl = 0; a.history = new CdPlayerBody.History(CdAssetBytes.copyOf(historyBytes()), 0x100);
        assertBoundary(f, a, CdPlayerBody.UnsupportedBoundary.HISTORY_ADDRESS);
        a.history = new CdPlayerBody.History(CdAssetBytes.copyOf(historyBytes()), 0);
        f.slots = CdPlayerBody.DynamicSlots.UNIMPLEMENTED;
        assertBoundary(f, a, CdPlayerBody.UnsupportedBoundary.TOUCH_SCAN);
    }

    @Test void actualMovementUnsupportedRampMiniAndTimeWarpPropagateWithoutPublication() {
        Fixture f = new Fixture(); Actor a = new Actor(); f.cell(0, 0, 6);
        var ramp = assertThrows(CdPlayerMovement.UnsupportedMotion.class, () -> f.owner().step(a.state(), f.environment()));
        assertEquals(CdPlayerMovement.UnsupportedBoundary.RAMP_ENTRY, ramp.boundary());
        f.cell(0, 0, 2); f.mini = 1;
        assertEquals(CdPlayerMovement.UnsupportedBoundary.MINIATURE,
                assertThrows(CdPlayerMovement.UnsupportedMotion.class, () -> f.owner().step(a.state(), f.environment())).boundary());
        f.mini = 0; f.warpDir = 1;
        assertEquals(CdPlayerMovement.UnsupportedBoundary.TIME_WARP_ACTIVE,
                assertThrows(CdPlayerMovement.UnsupportedMotion.class, () -> f.owner().step(a.state(), f.environment())).boundary());
    }

    @Test void headerMustRetainTheActualBoundMapAndNativeWidthsAreExplicit() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.header = new CdPlayerBody.Header(1, 0, 0x780, 2, 24);
        assertBoundary(f, a, CdPlayerBody.UnsupportedBoundary.MAPPING_BINDING);
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Header(1, 0, 0x10000, 2, 24));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.AnimationCursor(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Timers(65536, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[252]), 0));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Controls(256, 0, 0, 0, 0));
    }

    @Test void unsupportedSlotAndSourceFamilyDoNotPretendToBeR11a() {
        Fixture f = new Fixture(); Actor a = new Actor();
        a.header = new CdPlayerBody.Header(2, f.mapAddress(), 0x780, 2, 24);
        assertBoundary(f, a, CdPlayerBody.UnsupportedBoundary.SLOT_ID);
        a.header = new CdPlayerBody.Header(1, f.mapAddress(), 0x780, 2, 24);
        var old = f.environment(); var m = old.movement();
        var different = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(), m.bounds(),
                new CdPlayerMovement.Gates(0, 0, 0, 0, 0, 0, 0), m.globals()), old.controls(), old.globals(), old.dynamicSlots());
        assertEquals(CdPlayerBody.UnsupportedBoundary.SOURCE_FAMILY,
                assertThrows(CdPlayerBody.UnsupportedBody.class, () -> f.owner().step(a.state(), different)).boundary());
    }

    @Test void actualTouchStagesFirstPhysicalItemBetweenAnimateAndSpecialChunksWithoutPoolWrites() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.spr = 0xC4;
        f.slots = CdPlayerBody.DynamicSlots.UNIMPLEMENTED;
        CdObjectPool pool = new CdObjectPool();
        touchSlot(pool, 32, 0x47, 0x400, 76);
        var selected = touchSlot(pool, 34, 0x47, 64, 76);
        touchSlot(pool, 35, 0x40, 64, 76); // Invalid lookup is after the first return.
        var snapshot = pool.capture();
        var context = new CdPlayerBody.TouchContext(touchScanner(), snapshot);
        var r = f.owner().step(a.state(), f.environment(), Optional.of(context));
        var touch = objectTouch(r);
        assertEquals(CdPlayerObjectTouch.Kind.ITEM, touch.kind());
        assertSame(selected, touch.selectedSlot().orElseThrow());
        var patch = touch.changedSlot().orElseThrow();
        assertEquals(selected.identity(), patch.identity()); assertEquals(4, patch.routine());
        byte[] expected = selected.copyBytes(); expected[CdObjectState.ByteField.ROUTINE.byteOffset()] = 4;
        assertArrayEquals(expected, patch.copyBytes());
        assertEquals(snapshot.slots(), pool.capture().slots()); assertSame(selected, pool.state(34));
        assertEquals(CdPlayerBody.DynamicSlots.UNIMPLEMENTED, r.environment().dynamicSlots());
        assertTrue(indexCall(r, CdPlayerBody.NativeCall.ANIMATE) < indexOf(r, CdPlayerBody.ObjectTouchResult.class));
        assertTrue(indexOf(r, CdPlayerBody.ObjectTouchResult.class) < indexOf(r, CdPlayerBody.TouchScanCompleted.class));
        assertTrue(indexOf(r, CdPlayerBody.TouchScanCompleted.class) < indexCall(r, CdPlayerBody.NativeCall.SPECIAL_CHUNKS));
        assertEquals(List.of(new CdPlayerBody.TouchScanCompleted(3)), touchVisits(r));
        assertTrue(bodySounds(r).isEmpty());
    }

    @ParameterizedTest @CsvSource({"90,89,true", "91,90,false"})
    void touchUsesPostDisplayHurtWordAndStillSelectsTheFirstItem(int before, int after, boolean changed) {
        Fixture f = new Fixture(); Actor a = new Actor(); a.hurt = before;
        CdObjectPool pool = new CdObjectPool(); var first = touchSlot(pool, 32, 0x47, 64, 76);
        touchSlot(pool, 33, 0x40, 64, 76);
        var r = f.owner().step(a.state(), f.environment(), Optional.of(touchContext(pool)));
        assertEquals(after, r.state().timers().hurt());
        assertSame(first, objectTouch(r).selectedSlot().orElseThrow());
        assertEquals(changed, objectTouch(r).changedSlot().isPresent());
        assertEquals(List.of(new CdPlayerBody.TouchScanCompleted(1)), touchVisits(r));
        assertEquals(2, pool.state(32).routine());
    }

    @Test void timeWarpDisplayGateDoesNotInventAHurtDecrementForTouch() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.hurt = 90; f.timeWarpTimer = 210;
        CdObjectPool pool = new CdObjectPool(); touchSlot(pool, 32, 0x47, 64, 76);
        var r = f.owner().step(a.state(), f.environment(), Optional.of(touchContext(pool)));
        assertEquals(90, r.state().timers().hurt()); assertFalse(r.display().orElseThrow().queued());
        assertEquals(CdPlayerObjectTouch.Kind.ITEM, objectTouch(r).kind());
        assertTrue(objectTouch(r).changedSlot().isEmpty());
    }

    @Test void touchUsesAfterAnimationDuckMappingRatherThanTheIncomingFrame() {
        Fixture f = new Fixture(); Actor a = new Actor();
        CdObjectPool pool = new CdObjectPool(); touchSlot(pool, 32, 0x47, 64, 60);
        var context = touchContext(pool);
        var normal = f.owner().step(a.state(), f.environment(), Optional.of(context));
        assertEquals(7, normal.state().animation().mapFrame());
        assertEquals(CdPlayerObjectTouch.Kind.ITEM, objectTouch(normal).kind()); // Ring bottom 66 >= normal top 60.
        f.animationFirstFrame = 0x39;
        var duck = f.owner().step(a.state(), f.environment(), Optional.of(context));
        assertEquals(0, a.state().animation().mapFrame()); assertEquals(0x39, duck.state().animation().mapFrame());
        assertEquals(76, duck.state().motion().centreY16_16() >>> 16);
        assertEquals(CdPlayerObjectTouch.Kind.NONE, objectTouch(duck).kind()); // Ring bottom 66 < narrowed top 72.
        assertEquals(List.of(new CdPlayerBody.TouchScanCompleted(96)), touchVisits(duck));
        assertEquals(2, pool.state(32).routine());
    }

    @Test void touchReadsTheCompletedMovementCentreAtTheCallCut() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.gv = 0x200; f.rawHeld = 8;
        CdObjectPool pool = new CdObjectPool(); touchSlot(pool, 32, 0x47, 80, 76);
        var context = touchContext(pool);
        assertEquals(CdPlayerObjectTouch.Kind.NONE, context.scanner().scan(
                new CdPlayerObjectTouch.PlayerCut(64, 76, 19, 7, 0), context.snapshot()).kind());
        var r = f.owner().step(a.state(), f.environment(), Optional.of(context));
        assertEquals(66, r.state().motion().centreX16_16() >>> 16);
        assertEquals(76, r.state().motion().centreY16_16() >>> 16);
        assertEquals(CdPlayerObjectTouch.Kind.ITEM, objectTouch(r).kind());
    }

    @Test void itemThenSpecialChunksFailureReturnsNoPublicationPatchOrSoundsAndLeavesInputsReusable() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.flags = 2; a.y = 0x90 << 16;
        a.invinc = 1; f.invincible = 1; f.slots = CdPlayerBody.DynamicSlots.UNIMPLEMENTED;
        CdObjectPool pool = new CdObjectPool(); touchSlot(pool, 32, 0x47, 64, 0x90);
        var context = touchContext(pool); var snapshot = pool.capture(); var state = a.state(); var env = f.environment();
        var safeOwner = f.owner(); var control = safeOwner.step(state, env, Optional.of(context));
        assertEquals(0x90, control.state().motion().centreY16_16() >>> 16);
        assertEquals(2, control.state().motion().flags() & 2);
        assertEquals(CdPlayerObjectTouch.Kind.ITEM, objectTouch(control).kind());
        assertTrue(objectTouch(control).changedSlot().isPresent());
        assertEquals(List.of(new CdPlayerBody.SoundRequest(CdPlayerBody.Sound.SCMD_R1AMUS)), bodySounds(control));
        f.special[3] = 2; var failingOwner = f.owner();
        List<CdPlayerBody.Result> publications = new ArrayList<>();
        var failure = assertThrows(CdPlayerBody.UnsupportedBody.class,
                () -> publications.add(failingOwner.step(state, env, Optional.of(context))));
        assertEquals(CdPlayerBody.UnsupportedBoundary.ROLL_TUNNEL_OWNER, failure.boundary());
        assertTrue(publications.isEmpty()); assertEquals(snapshot.slots(), pool.capture().slots());
        assertEquals(1, state.timers().invincibility()); assertEquals(1, env.globals().invincible());
        assertEquals(0, state.history().index()); assertEquals(0, state.animation().mapFrame());
        var replay = safeOwner.step(state, env, Optional.of(context));
        assertEquals(control.state(), replay.state()); assertEquals(control.environment(), replay.environment());
        assertEquals(objectTouch(control).changedSlot(), objectTouch(replay).changedSlot());
        assertEquals(bodySounds(control), bodySounds(replay));
    }

    @ParameterizedTest @CsvSource({"7,ENEMY", "70,MONITOR", "135,HAZARD", "199,SPECIAL"})
    void selectedHandlerFailsWithItsKindAndSlotBeforeLaterSpecialChunks(int type, CdPlayerObjectTouch.Kind kind) {
        Fixture f = new Fixture(); Actor a = new Actor(); a.flags = 2; a.y = 0x90 << 16; f.special[3] = 2;
        CdObjectPool pool = new CdObjectPool(); var selected = touchSlot(pool, 33, type, 64, 0x90);
        touchSlot(pool, 34, 0x47, 64, 0x90); var snapshot = pool.capture();
        var failure = assertThrows(CdPlayerBody.UnsupportedTouchHandler.class,
                () -> f.owner().step(a.state(), f.environment(), Optional.of(touchContext(pool))));
        assertEquals(kind, failure.kind()); assertSame(selected, failure.selectedSlot());
        assertEquals(type, failure.selectedSlot().unsignedByte(CdObjectState.ByteField.COLLISION_TYPE));
        assertEquals(snapshot.slots(), pool.capture().slots());
    }

    @Test void negativeOwnControlDoesNotSampleSuppliedMalformedTouchSelector() {
        Fixture f = new Fixture(); Actor a = new Actor(); f.slots = CdPlayerBody.DynamicSlots.UNIMPLEMENTED;
        CdObjectPool pool = new CdObjectPool(); touchSlot(pool, 32, 0x40, 64, 76); var context = touchContext(pool);
        var owner = f.owner();
        assertThrows(IllegalArgumentException.class, () -> owner.step(a.state(), f.environment(), Optional.of(context)));
        a.ctrl = 0x80;
        var r = owner.step(a.state(), f.environment(), Optional.of(context));
        assertTrue(touchVisits(r).isEmpty());
        assertFalse(r.effects().stream().anyMatch(CdPlayerBody.ObjectTouchResult.class::isInstance));
        assertTrue(calls(r).contains(CdPlayerBody.NativeCall.SPECIAL_CHUNKS));
    }

    @Test void animationGiveUpGateDoesNotSampleSuppliedMalformedTouchSelector() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.anim = a.prev = 5; a.flags = 1;
        a.cursor = new CdPlayerBody.AnimationCursor(1, 0, 4);
        f.animationCommand = 0xFD; f.animationOperand = 0x2B; f.slots = CdPlayerBody.DynamicSlots.UNIMPLEMENTED;
        CdObjectPool pool = new CdObjectPool(); touchSlot(pool, 32, 0x40, 64, 76);
        var r = f.owner().step(a.state(), f.environment(), Optional.of(touchContext(pool)));
        assertEquals(0x2B, r.state().motion().animation()); assertTrue(touchVisits(r).isEmpty());
        assertFalse(r.effects().stream().anyMatch(CdPlayerBody.ObjectTouchResult.class::isInstance));
        assertTrue(calls(r).contains(CdPlayerBody.NativeCall.SPECIAL_CHUNKS));
    }

    @Test void absentAndInitReturnsDoNotSampleSuppliedMalformedTouchSelector() {
        Fixture f = new Fixture(); Actor a = new Actor(); f.slots = CdPlayerBody.DynamicSlots.UNIMPLEMENTED;
        CdObjectPool pool = new CdObjectPool(); touchSlot(pool, 32, 0x40, 64, 76); var context = touchContext(pool);
        a.header = new CdPlayerBody.Header(0, 0, 0, 0, 0);
        var absent = f.owner().step(a.state(), f.environment(), Optional.of(context));
        assertEquals(CdPlayerBody.Boundary.SLOT_ABSENT, absent.boundary()); assertTrue(absent.effects().isEmpty());
        a.header = new CdPlayerBody.Header(1, 0, 0, 0, 0); a.routine = 0;
        var init = f.owner().step(a.state(), f.environment(), Optional.of(context));
        assertEquals(CdPlayerBody.Boundary.INIT_RETURN, init.boundary()); assertTrue(touchVisits(init).isEmpty());
        assertFalse(init.effects().stream().anyMatch(CdPlayerBody.ObjectTouchResult.class::isInstance));
    }

    @Test void omittedContextKeepsTheExistingEmptyFallbackAndUnimplementedCallBoundary() {
        Fixture f = new Fixture(); Actor a = new Actor(); var owner = f.owner(); var state = a.state();
        var old = owner.step(state, f.environment());
        assertEquals(old, owner.step(state, f.environment(), Optional.empty()));
        assertEquals(List.of(new CdPlayerBody.TouchScanCompleted(96)), touchVisits(old));
        assertFalse(old.effects().stream().anyMatch(CdPlayerBody.ObjectTouchResult.class::isInstance));
        f.slots = CdPlayerBody.DynamicSlots.UNIMPLEMENTED;
        assertBoundary(f, a, CdPlayerBody.UnsupportedBoundary.TOUCH_SCAN);
        CdObjectPool pool = new CdObjectPool();
        var actualEmpty = owner.step(state, f.environment(), Optional.of(touchContext(pool)));
        assertEquals(CdPlayerObjectTouch.Kind.NONE, objectTouch(actualEmpty).kind());
        assertEquals(List.of(new CdPlayerBody.TouchScanCompleted(96)), touchVisits(actualEmpty));
        touchSlot(pool, 127, 0x47, 64, 76);
        var last = owner.step(state, f.environment(), Optional.of(touchContext(pool)));
        assertEquals(127, objectTouch(last).selectedSlot().orElseThrow().identity().wholeIndex());
        assertEquals(List.of(new CdPlayerBody.TouchScanCompleted(96)), touchVisits(last));
    }

    @Test void pureReplayKeepsExplicitContextImmutableAndNeverRetainsItInBodyOrEnvironment() {
        Fixture f = new Fixture(); Actor a = new Actor(); f.slots = CdPlayerBody.DynamicSlots.UNIMPLEMENTED;
        CdObjectPool pool = new CdObjectPool(); var selected = touchSlot(pool, 32, 0x47, 64, 76);
        var context = touchContext(pool); var saved = a.state(); var env = f.environment(); var owner = f.owner();
        var first = owner.step(saved, env, Optional.of(context));
        assertThrows(UnsupportedOperationException.class, () -> context.snapshot().slots().clear());
        byte[] detached = context.snapshot().slots().getFirst().copyBytes(); detached[0x24] = 99;
        pool.deleteObject(32);
        var replay = owner.step(saved, env, Optional.of(context));
        assertEquals(first.state(), replay.state()); assertEquals(first.environment(), replay.environment());
        assertEquals(first.display(), replay.display()); assertEquals(first.boundary(), replay.boundary());
        assertEquals(first.modeBoundary(), replay.modeBoundary());
        assertEquals(first.effects().stream().filter(e -> !(e instanceof CdPlayerBody.ObjectTouchResult)).toList(),
                replay.effects().stream().filter(e -> !(e instanceof CdPlayerBody.ObjectTouchResult)).toList());
        assertSame(selected, objectTouch(replay).selectedSlot().orElseThrow());
        assertEquals(objectTouch(first).changedSlot(), objectTouch(replay).changedSlot());
        assertEquals(2, context.snapshot().slots().getFirst().routine());
        assertEquals(CdPlayerBody.DynamicSlots.UNIMPLEMENTED, replay.environment().dynamicSlots());
        assertEquals(CdPlayerBody.UnsupportedBoundary.TOUCH_SCAN,
                assertThrows(CdPlayerBody.UnsupportedBody.class, () -> owner.step(saved, env)).boundary());
        var fresh = owner.step(saved, env, Optional.of(touchContext(pool)));
        assertEquals(CdPlayerObjectTouch.Kind.NONE, objectTouch(fresh).kind());
        assertEquals(0, saved.history().index()); assertEquals(0, saved.animation().mapFrame());
        assertThrows(UnsupportedOperationException.class, () -> first.effects().clear());
    }

    @Test void displayCutRetainsFullRawWidthAndPriorityBytesAndRejectsNonBytes() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.spr = 0xC4;
        a.header = new CdPlayerBody.Header(1, f.mapAddress(), 0x780, 0xFF, 0x80);
        var r = f.owner().step(a.state(), f.environment());
        var cut = r.effects().stream().filter(CdPlayerBody.DisplayEligibility.class::isInstance)
                .map(CdPlayerBody.DisplayEligibility.class::cast).findFirst().orElseThrow();
        assertEquals(new CdPlayerBody.DisplayEligibility(true, 64, 76, 19, 0xC4, 0x80, 0xFF), cut);
        assertThrows(IllegalArgumentException.class,
                () -> new CdPlayerBody.DisplayEligibility(true, 64, 76, 19, 4, 256, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CdPlayerBody.DisplayEligibility(true, 64, 76, 19, 4, 0, -1));
    }

    @Test void touchContextRequiresBothDeclaredPureInputs() {
        CdObjectPool pool = new CdObjectPool(); var scanner = touchScanner(); var snapshot = pool.capture();
        var context = new CdPlayerBody.TouchContext(scanner, snapshot);
        assertSame(scanner, context.scanner()); assertSame(snapshot, context.snapshot());
        assertThrows(NullPointerException.class, () -> new CdPlayerBody.TouchContext(null, snapshot));
        assertThrows(NullPointerException.class, () -> new CdPlayerBody.TouchContext(scanner, null));
    }

    // Selected ENEMY/HAZARD response through the real Body call path. Written before the composition; UNRUN.
    // Source pin 1ecc5f3a: Sonic/Main.asm:340-351 call cut, PP Player Object Collision.asm:130-298.

    @Test void jumpRollKillAdoptsTheSourceResponseBeforeSpecialChunksAndPublishesOneTypedResult() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.rotationDistance = 0xA5;
        f.rawHeld = f.rawTap = 0x10; // CheckJump sets the roll bit and oAnim 2 before Player_TouchEnemy (:130-136).
        CdObjectPool pool = new CdObjectPool(); var enemy = touchSlot(pool, 33, 0x2B, 64, 81);
        Contact contact = new Contact(); contact.score = 4900;
        var context = responseTouch(pool, contact); var before = pool.capture().slots();
        var r = f.owner().step(a.state(), f.environment(), Optional.of(context));
        var effect = responseEffect(r); var response = effect.result();
        assertEquals(CdPlayerTouchResponse.Exit.KILL_ENEMY, response.exit());
        // Old chain 9 clamps to index 6 (100 points, frame 6); the new chain 11 stays below $20 (:155-173).
        assertEquals(List.of(enemy.moveByte(CdObjectState.ByteField.FLAGS, 0x80)
                .moveWord(CdObjectState.WordField.EXPLOSION_POINTS_FRAME, 6).moveByte(CdObjectState.ByteField.ID, 0x18)
                .moveByte(CdObjectState.ByteField.ROUTINE, 0).moveByte(CdObjectState.ByteField.SUBTYPE, 1)),
                response.changedSlots());
        assertEquals(List.of(new CdPlayerTouchResponse.AddPoints(100, true),
                new CdPlayerTouchResponse.SubCpu(CdPlayerTouchResponse.SubCpuCommand.SCMD_YESSFX),
                new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_DESTROY)), response.calls());
        assertEquals(new CdPlayerTouchResponse.WorldGlobals(5, 4, 1, 5000, 10000, 1), response.world());
        assertEquals(new CdPlayerBody.ContactGlobals(0, 0, 1), effect.contact());
        assertEquals(response.actor().motion(), r.state().motion()); // SpecialChunks' bit-6 clear finds it clear.
        assertEquals(-0x580, r.state().motion().yVelocity()); // CheckJump's -$680, then .BounceDown +$100.
        assertEquals(6, r.state().motion().flags()); assertEquals(2, r.state().motion().animation());
        assertEquals(11, r.environment().movement().globals().scoreChain());
        assertEquals(0xA5, r.state().rotationDistance());
        assertEquals(a.state().header(), r.state().header()); assertEquals(a.state().timers(), r.state().timers());
        assertTrue(indexCall(r, CdPlayerBody.NativeCall.ANIMATE) < indexOf(r, CdPlayerBody.ObjectTouchResponse.class));
        assertTrue(indexOf(r, CdPlayerBody.ObjectTouchResponse.class) < indexOf(r, CdPlayerBody.TouchScanCompleted.class));
        assertTrue(indexOf(r, CdPlayerBody.TouchScanCompleted.class) < indexCall(r, CdPlayerBody.NativeCall.SPECIAL_CHUNKS));
        assertEquals(List.of(new CdPlayerBody.TouchScanCompleted(2)), touchVisits(r));
        assertFalse(r.effects().stream().anyMatch(CdPlayerBody.ObjectTouchResult.class::isInstance));
        assertTrue(bodySounds(r).isEmpty(), "Contact requests stay inside the pure Result for the Frame");
        assertEquals(before, pool.capture().slots()); assertSame(enemy, pool.state(33));
        assertEquals(r, f.owner().step(a.state(), f.environment(), Optional.of(context)));
    }

    @ParameterizedTest @CsvSource({"0,HURT,120", "1,HURT,120", "2,NO_HURT,1", "120,NO_HURT,119"})
    void hazardReadsThePostDisplayHurtWordSoDisplayDecrementsBeforeTheImmunityTest(int entry,
            CdPlayerTouchResponse.Exit exit, int finalHurt) {
        Fixture f = new Fixture(); Actor a = new Actor(); a.hurt = entry;
        CdObjectPool pool = new CdObjectPool(); touchSlot(pool, 33, 0xAB, 72, 76);
        var r = f.owner().step(a.state(), f.environment(), Optional.of(responseTouch(pool, new Contact())));
        assertEquals(exit, response(r).exit()); assertEquals(finalHurt, r.state().timers().hurt());
        if (exit == CdPlayerTouchResponse.Exit.NO_HURT) {
            var control = f.owner().step(a.state(), f.environment(),
                    Optional.of(responseTouch(new CdObjectPool(), new Contact())));
            assertEquals(control.state(), r.state()); assertEquals(control.environment(), r.environment());
            assertTrue(response(r).calls().isEmpty()); assertTrue(response(r).changedSlots().isEmpty());
        } else {
            assertEquals(4, r.state().motion().routine());
        }
    }

    @Test void timeWarpDisplayGateKeepsAOneFrameHurtWordSoTheHazardIsStillNoHurt() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.hurt = 1; f.timeWarpTimer = 210;
        CdObjectPool pool = new CdObjectPool(); touchSlot(pool, 33, 0xAB, 72, 76);
        var r = f.owner().step(a.state(), f.environment(), Optional.of(responseTouch(pool, new Contact())));
        assertEquals(CdPlayerTouchResponse.Exit.NO_HURT, response(r).exit());
        assertEquals(1, r.state().timers().hurt()); assertFalse(r.display().orElseThrow().queued());
    }

    @ParameterizedTest @CsvSource({"90,89,true", "91,90,false"})
    void responseOwnerLeavesTheItemPathAndItsPostDisplayGateUnchanged(int before, int after, boolean changed) {
        Fixture f = new Fixture(); Actor a = new Actor(); a.hurt = before;
        CdObjectPool pool = new CdObjectPool(); var first = touchSlot(pool, 32, 0x47, 64, 76);
        var r = f.owner().step(a.state(), f.environment(), Optional.of(responseTouch(pool, new Contact())));
        assertEquals(after, r.state().timers().hurt());
        assertSame(first, objectTouch(r).selectedSlot().orElseThrow());
        assertEquals(changed, objectTouch(r).changedSlot().isPresent());
        assertFalse(r.effects().stream().anyMatch(CdPlayerBody.ObjectTouchResponse.class::isInstance));
        var old = f.owner().step(a.state(), f.environment(), Optional.of(touchContext(pool)));
        assertEquals(old.state(), r.state()); assertEquals(old.environment(), r.environment());
        assertEquals(old.display(), r.display()); assertEquals(touchVisits(old), touchVisits(r));
    }

    @ParameterizedTest @CsvSource({"72,0,-512", "56,0,512", "72,1,-512"})
    void rollingJumpHazardHitTakesTheRingAtThePreResetYThenUnrollsAndKnocksBack(int hazardX, int shield, int xVelocity) {
        Fixture f = new Fixture(); Actor a = new Actor(); f.rawHeld = f.rawTap = 0x10;
        CdObjectPool pool = new CdObjectPool(); touchSlot(pool, 33, 0xAB, hazardX, 81);
        Contact contact = new Contact(); contact.shield = shield;
        var context = responseTouch(pool, contact);
        var r = f.owner().step(a.state(), f.environment(), Optional.of(context));
        var effect = responseEffect(r); var response = effect.result(); var m = r.state().motion();
        assertEquals(CdPlayerTouchResponse.Exit.HURT, response.exit());
        assertEquals(response.actor().motion(), m);
        assertEquals(4, m.routine()); assertEquals(2, m.flags()); assertEquals(9, m.xRadius()); assertEquals(19, m.yRadius());
        assertEquals(76 << 16, m.centreY16_16()); // CheckJump's +5, then ResetOnFloor's SUBQ.W #5.
        assertEquals(-0x400, m.yVelocity()); assertEquals(xVelocity, m.xVelocity()); assertEquals(0, m.groundVelocity());
        assertEquals(0x1A, m.animation()); assertEquals(0, m.playerJump()); assertEquals(0, m.charge());
        assertEquals(0x78, r.state().timers().hurt()); assertEquals(0, r.environment().movement().globals().scoreChain());
        if (shield == 0) {
            var ring = context.snapshot().slots().getFirst().moveByte(CdObjectState.ByteField.ID, 0x11)
                    .moveWord(CdObjectState.WordField.X, 64).moveWord(CdObjectState.WordField.Y, 81);
            assertEquals(List.of(ring), response.changedSlots()); // HurtPlayer:236-238 precede .SetHurt's reset.
            assertEquals(List.of(new CdPlayerTouchResponse.FindObjSlot(OptionalInt.of(32)),
                    new CdPlayerTouchResponse.ResetOnFloor(),
                    new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP)), response.calls());
            assertEquals(new CdPlayerBody.ContactGlobals(0, 0, 0), effect.contact());
        } else {
            assertTrue(response.changedSlots().isEmpty());
            assertEquals(List.of(new CdPlayerTouchResponse.ResetOnFloor(),
                    new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP)), response.calls());
            assertEquals(new CdPlayerBody.ContactGlobals(0, 0, 1), effect.contact()); // BCLR #0 keeps combineRing.
        }
        assertEquals(5, response.world().rings());
        assertTrue(bodySounds(r).isEmpty());
    }

    @Test void zeroRingRollingKillWritesTheStickWordIntoByte39AndOnlyThePublishedPoseSeesTheNewTileAndPriority() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.rotationDistance = 0xA5; f.rawHeld = f.rawTap = 0x10;
        CdObjectPool pool = new CdObjectPool(); touchSlot(pool, 33, 0xAB, 72, 81);
        Contact contact = new Contact(); contact.rings = 0;
        var r = f.owner().step(a.state(), f.environment(), Optional.of(responseTouch(pool, contact)));
        var response = response(r);
        assertEquals(CdPlayerTouchResponse.Exit.KILL_PLAYER, response.exit());
        assertEquals(List.of(new CdPlayerTouchResponse.ResetOnFloor(),
                new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP),
                new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_HURT)), response.calls());
        assertTrue(response.changedSlots().isEmpty());
        var m = r.state().motion();
        assertEquals(6, m.routine()); assertEquals(2, m.flags()); assertEquals(76 << 16, m.centreY16_16());
        assertEquals(-0x700, m.yVelocity()); assertEquals(0, m.xVelocity()); assertEquals(0, m.groundVelocity());
        assertEquals(0x18, m.animation()); assertEquals(0, m.stick());
        assertEquals(0x4C, r.state().rotationDistance()); // MOVE.W oY,oPlayerStick (:289): $38 = $00, $39 = $4C.
        assertEquals(new CdPlayerBody.Header(1, f.mapAddress(), 0x8780, 0, 24), r.state().header());
        assertEquals(0, r.state().timers().hurt()); assertEquals(0, r.environment().globals().invincible());
        var cut = r.effects().stream().filter(CdPlayerBody.DisplayEligibility.class::isInstance)
                .map(CdPlayerBody.DisplayEligibility.class::cast).findFirst().orElseThrow();
        assertEquals(new CdPlayerBody.DisplayEligibility(true, 64, 81, 14, 4, 24, 2), cut); // Display ran before the touch.
        assertEquals(new CdPlayerBody.DisplayPublication(true, f.mapAddress(), 0x8780, 0, 24, 7, 4, 64 << 16, 76 << 16),
                r.display().orElseThrow());
        assertTrue(bodySounds(r).isEmpty());
    }

    @ParameterizedTest @CsvSource({"120,0", "0,1"})
    void noHurtAndDebugKillReturnsLeaveByte39AndTheWholeActorExactlyAsTheNoTouchControl(int hurt, int debugMode) {
        Fixture f = new Fixture(); Actor a = new Actor(); a.hurt = hurt; a.rotationDistance = 0xA5;
        f.debugMode = debugMode; // ObjSonic tests only the high byte (Sonic/Main.asm:69); KillPlayer:280 tests the word.
        CdObjectPool pool = new CdObjectPool(); touchSlot(pool, 33, 0xAB, 72, 76);
        Contact contact = new Contact(); contact.rings = debugMode == 0 ? 5 : 0;
        var r = f.owner().step(a.state(), f.environment(), Optional.of(responseTouch(pool, contact)));
        var control = f.owner().step(a.state(), f.environment(), Optional.of(responseTouch(new CdObjectPool(), contact)));
        assertEquals(debugMode == 0 ? CdPlayerTouchResponse.Exit.NO_HURT
                : CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE, response(r).exit());
        assertEquals(control.state(), r.state()); assertEquals(control.environment(), r.environment());
        assertEquals(control.display(), r.display()); assertEquals(0xA5, r.state().rotationDistance());
        assertTrue(response(r).calls().isEmpty()); assertTrue(response(r).changedSlots().isEmpty());
        assertEquals(new CdPlayerBody.ContactGlobals(0, 0, 1), responseEffect(r).contact());
    }

    @ParameterizedTest @CsvSource({"70,MONITOR,true", "199,SPECIAL,true", "43,ENEMY,false", "171,HAZARD,false"})
    void unownedHandlersStillStopAtTheTypedBoundaryWithOrWithoutAResponseOwner(int type, CdPlayerObjectTouch.Kind kind,
                                                                              boolean owner) {
        Fixture f = new Fixture(); Actor a = new Actor();
        CdObjectPool pool = new CdObjectPool(); var selected = touchSlot(pool, 33, type, 72, 76); var snapshot = pool.capture();
        var context = owner ? responseTouch(pool, new Contact()) : touchContext(pool);
        var failure = assertThrows(CdPlayerBody.UnsupportedTouchHandler.class,
                () -> f.owner().step(a.state(), f.environment(), Optional.of(context)));
        assertEquals(kind, failure.kind()); assertSame(selected, failure.selectedSlot());
        assertEquals(snapshot.slots(), pool.capture().slots());
    }

    @Test void skippedTouchGatesNeverSampleTheScannerSnapshotOrResponseOwner() {
        Fixture f = new Fixture(); CdObjectPool pool = new CdObjectPool();
        touchSlot(pool, 32, 0x40, 64, 76); touchSlot(pool, 33, 0x2B, 64, 76); // A sampled scan rejects selector 0 first.
        var context = responseTouch(pool, new Contact());
        assertThrows(IllegalArgumentException.class,
                () -> f.owner().step(new Actor().state(), f.environment(), Optional.of(context)));
        Actor a = new Actor(); a.ctrl = 0x80;
        var negative = f.owner().step(a.state(), f.environment(), Optional.of(context));
        assertTrue(touchVisits(negative).isEmpty());
        assertFalse(negative.effects().stream().anyMatch(e -> e instanceof CdPlayerBody.ObjectTouchResponse
                || e instanceof CdPlayerBody.ObjectTouchResult));
        assertTrue(calls(negative).contains(CdPlayerBody.NativeCall.SPECIAL_CHUNKS));
        a.ctrl = 0; a.header = new CdPlayerBody.Header(0, 0, 0, 0, 0);
        assertTrue(f.owner().step(a.state(), f.environment(), Optional.of(context)).effects().isEmpty());
        a.header = new CdPlayerBody.Header(1, 0, 0, 0, 0); a.routine = 0;
        var init = f.owner().step(a.state(), f.environment(), Optional.of(context));
        assertEquals(CdPlayerBody.Boundary.INIT_RETURN, init.boundary()); assertTrue(touchVisits(init).isEmpty());
    }

    @Test void hurtThenSpecialChunksTunnelFailurePublishesNoResponseResultOrSoundsAndLeavesInputsReusable() {
        Fixture f = new Fixture(); Actor a = new Actor(); f.special[3] = 2;
        CdObjectPool pool = new CdObjectPool(); touchSlot(pool, 33, 0xAB, 72, 76);
        var context = responseTouch(pool, new Contact()); var snapshot = pool.capture();
        var state = a.state(); var env = f.environment(); var failingOwner = f.owner();
        List<CdPlayerBody.Result> publications = new ArrayList<>();
        var failure = assertThrows(CdPlayerBody.UnsupportedBody.class,
                () -> publications.add(failingOwner.step(state, env, Optional.of(context))));
        // HurtPlayer's BSET #1 meets the tunnel chunk's air-bit owner boundary in the same Main (:340-351).
        assertEquals(CdPlayerBody.UnsupportedBoundary.ROLL_TUNNEL_OWNER, failure.boundary());
        assertTrue(publications.isEmpty()); assertEquals(snapshot.slots(), pool.capture().slots());
        assertEquals(2, state.motion().routine()); assertEquals(0, state.timers().hurt());
        assertEquals(0, state.history().index()); assertEquals(0, state.animation().mapFrame());
        var grounded = failingOwner.step(state, env, Optional.of(responseTouch(new CdObjectPool(), new Contact())));
        assertEquals(4, grounded.state().motion().flags() & 4, "Without the hurt the same chunk is the StartRoll owner");
        f.special[3] = 0x7F; var safe = f.owner();
        var r = safe.step(state, env, Optional.of(context));
        assertEquals(CdPlayerTouchResponse.Exit.HURT, response(r).exit());
        assertEquals(r, safe.step(state, env, Optional.of(context)));
    }

    @Test void responseContextIsAnExplicitEphemeralOwnerAndTheTwoArgumentContextHasNone() {
        CdObjectPool pool = new CdObjectPool(); var scanner = touchScanner(); var snapshot = pool.capture();
        var owner = new Contact().context();
        assertEquals(Optional.empty(), new CdPlayerBody.TouchContext(scanner, snapshot).response());
        var withOwner = new CdPlayerBody.TouchContext(scanner, snapshot, owner);
        assertEquals(Optional.of(owner), withOwner.response());
        assertEquals(withOwner, new CdPlayerBody.TouchContext(scanner, snapshot, Optional.of(owner)));
        assertThrows(NullPointerException.class,
                () -> new CdPlayerBody.TouchContext(scanner, snapshot, (CdPlayerBody.ResponseContext) null));
        assertThrows(NullPointerException.class,
                () -> new CdPlayerBody.TouchContext(scanner, snapshot, (Optional<CdPlayerBody.ResponseContext>) null));
        assertThrows(NullPointerException.class, () -> new CdPlayerBody.ResponseContext(null, owner.contact(), owner.world()));
        assertThrows(NullPointerException.class, () -> new CdPlayerBody.ResponseContext(owner.response(), null, owner.world()));
        assertThrows(NullPointerException.class, () -> new CdPlayerBody.ResponseContext(owner.response(), owner.contact(), null));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.ContactGlobals(256, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.ContactGlobals(0, 256, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.ContactGlobals(0, 0, -1));
        assertThrows(NullPointerException.class, () -> new CdPlayerBody.ObjectTouchResponse(null));
    }

    @Test void bodyStateEnvironmentAndResultDeclareNoContactContextOrPoolCapture() {
        for (var type : List.of(CdPlayerBody.State.class, CdPlayerBody.Environment.class, CdPlayerBody.Result.class)) {
            for (var component : type.getRecordComponents()) {
                String name = component.getGenericType().getTypeName();
                for (String forbidden : List.of("TouchContext", "ResponseContext", "ContactGlobals", "Snapshot",
                        "CdPlayerTouchResponse")) {
                    assertFalse(name.contains(forbidden), type.getSimpleName() + "." + component.getName() + " " + name);
                }
            }
        }
    }

    private static CdPlayerObjectTouch.Result objectTouch(CdPlayerBody.Result r) {
        var touches = r.effects().stream().filter(CdPlayerBody.ObjectTouchResult.class::isInstance)
                .map(CdPlayerBody.ObjectTouchResult.class::cast).toList();
        assertEquals(1, touches.size()); return touches.getFirst().result();
    }
    private static List<CdPlayerBody.TouchScanCompleted> touchVisits(CdPlayerBody.Result r) {
        return r.effects().stream().filter(CdPlayerBody.TouchScanCompleted.class::isInstance)
                .map(CdPlayerBody.TouchScanCompleted.class::cast).toList();
    }
    private static int indexCall(CdPlayerBody.Result r, CdPlayerBody.NativeCall call) {
        for (int i = 0; i < r.effects().size(); i++) {
            if (r.effects().get(i) instanceof CdPlayerBody.CallReturned c && c.call() == call) return i;
        }
        fail("Missing call " + call); return -1;
    }
    private static CdObjectState touchSlot(CdObjectPool pool, int whole, int type, int x, int y) {
        var slot = pool.state(whole).moveByte(CdObjectState.ByteField.ID, 0x10)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, 0x80)
                .moveByte(CdObjectState.ByteField.COLLISION_TYPE, type)
                .moveByte(CdObjectState.ByteField.ROUTINE, 2)
                .moveWord(CdObjectState.WordField.X, x).moveWord(CdObjectState.WordField.Y, y);
        pool.write(slot); return slot;
    }
    private static CdPlayerBody.TouchContext touchContext(CdObjectPool pool) {
        return new CdPlayerBody.TouchContext(touchScanner(), pool.capture());
    }
    private static CdPlayerBody.TouchContext responseTouch(CdObjectPool pool, Contact contact) {
        return new CdPlayerBody.TouchContext(touchScanner(), pool.capture(), contact.context());
    }
    private static CdPlayerBody.ObjectTouchResponse responseEffect(CdPlayerBody.Result r) {
        var responses = r.effects().stream().filter(CdPlayerBody.ObjectTouchResponse.class::isInstance)
                .map(CdPlayerBody.ObjectTouchResponse.class::cast).toList();
        assertEquals(1, responses.size()); return responses.getFirst();
    }
    private static CdPlayerTouchResponse.Result response(CdPlayerBody.Result r) { return responseEffect(r).result(); }

    /** EnemyPoints dc.w 10,20,50,100 (PP Player Object Collision.asm:200-204); constructed literal, never a runtime fallback. */
    private static final byte[] ENEMY_POINTS = {0, 10, 0, 20, 0, 50, 0, 100};

    /** Runtime-owned contact/world bytes at the call cut; declared per test, never Body state. */
    static final class Contact {
        int timeWarp, shield, combineRing = 1, rings = 5, lives = 3, hudLives, score, next = 5000, hudScore = 0x80;
        CdPlayerBody.ResponseContext context() {
            return new CdPlayerBody.ResponseContext(new CdPlayerTouchResponse(CdAssetBytes.copyOf(ENEMY_POINTS)),
                    new CdPlayerBody.ContactGlobals(timeWarp, shield, combineRing),
                    new CdPlayerTouchResponse.WorldGlobals(rings, lives, hudLives, score, next, hudScore));
        }
    }
    /** Actual 63 ObjColSizes pairs, Player Object Collision.asm:398-461; constructed format input only. */
    private static CdPlayerObjectTouch touchScanner() {
        byte[] dimensions = {
                0x14, 0x14, 0x12, 0x0C, 0x0C, 0x10, 4, 0x10, 0x0C, 0x12, 0x10, 0x10, 6, 6,
                0x18, 0x0C, 0x0C, 0x10, 0x10, 0x0C, 8, 8, 0x14, 0x10, 0x14, 8, 0x0E, 0x0E,
                0x18, 0x18, 0x28, 0x10, 0x10, 0x18, 8, 0x10, 0x20, 0x70, 0x40, 0x20,
                (byte) 0x80, 0x20, 0x20, 0x20, 8, 8, 4, 4, 0x20, 8, 0x0C, 0x0C, 8, 4, 0x18, 4,
                0x28, 4, 4, 8, 4, 0x18, 4, 0x28, 4, 0x20, 0x18, 0x18, 0x0C, 0x18, 0x48, 8,
                8, 0x0C, 0x10, 8, 0x20, 0x10, 0x20, 0x10, 8, 0x10, 0x10, 0x10, 0x0C, 0x0C,
                0x10, 0x10, 4, 4, 0x10, 0x10, 0x16, 0x1A,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0x28, 0x24, 0x12, 0x11, 0x20, 0x18, 0x0C, 0x14, 0x20, 0x0C, 0x0C, 0x10
        };
        return new CdPlayerObjectTouch(CdR11aObjectAssets.decodeTouchDimensions(dimensions));
    }

    private static List<CdPlayerBody.NativeCall> calls(CdPlayerBody.Result r) {
        return r.effects().stream().filter(CdPlayerBody.CallReturned.class::isInstance)
                .map(CdPlayerBody.CallReturned.class::cast).map(CdPlayerBody.CallReturned::call).toList();
    }
    private static List<CdPlayerBody.SoundRequest> bodySounds(CdPlayerBody.Result r) {
        return r.effects().stream().filter(CdPlayerBody.SoundRequest.class::isInstance)
                .map(CdPlayerBody.SoundRequest.class::cast).toList();
    }
    private static int indexOf(CdPlayerBody.Result r, Class<?> type) {
        for (int i = 0; i < r.effects().size(); i++) if (type.isInstance(r.effects().get(i))) return i;
        fail("Missing effect " + type); return -1;
    }
    private static int indexWrite(CdPlayerBody.Result r, CdPlayerBody.GlobalField field) {
        for (int i = 0; i < r.effects().size(); i++) if (r.effects().get(i) instanceof CdPlayerBody.GlobalWrite w && w.field() == field) return i;
        fail("Missing global write " + field); return -1;
    }
    private static void assertBoundary(Fixture f, Actor a, CdPlayerBody.UnsupportedBoundary expected) {
        assertEquals(expected, assertThrows(CdPlayerBody.UnsupportedBody.class,
                () -> f.owner().step(a.state(), f.environment())).boundary());
    }
    private static byte[] historyBytes() {
        byte[] b = new byte[256]; for (int i = 0; i < b.length; i++) b[i] = (byte) (i ^ 0xA5); return b;
    }

    static final class Actor {
        int x = 64 << 16, y = 76 << 16, xv, yv, gv, routine = 2, angle, flags, spr = 4;
        int xr = 9, yr = 19, anim, prev, jump, charge, lock, stick, ctrl, pri, sec, hurt, invinc, shoes, rotationDistance;
        CdPlayerBody.Header header = new CdPlayerBody.Header(1, 0x21F200, 0x780, 2, 24);
        CdPlayerBody.AnimationCursor cursor = new CdPlayerBody.AnimationCursor(0, 0, 0);
        CdPlayerBody.History history = new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[256]), 0);
        CdPlayerBody.State state() {
            return new CdPlayerBody.State(new CdPlayerMotionState(x, y, xv, yv, gv, routine, angle, flags, spr, xr, yr,
                    anim, prev, jump, charge, lock, stick, ctrl, pri, sec), cursor, header,
                    new CdPlayerBody.Timers(hurt, invinc, shoes), history, rotationDistance);
        }
    }

    /** Constructed format data only; production body binds one actual aggregate. */
    static final class Fixture {
        final byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50], ids = new byte[900];
        final byte[] heights = new byte[4096], widths = new byte[4096], sine = new byte[640], atan = new byte[257];
        final byte[] special = {0x7F, 0x7F, 0x7F, 0x7F};
        CdReleaseVariant variant = CdReleaseVariant.USA;
        CdPlayerMovement.Physics physics = new CdPlayerMovement.Physics(0x600, 12, 0x80);
        int rawHeld, rawTap, logicalHeld, logicalTap, ctrlLocked, levelFrames = 1, camX = 160;
        int timeAttack, debugMode, debugCheat, timeWarpTimer, invincible, speedShoes, bossMusic, mini, wind, warpDir, bottom = 0x710;
        int animationFirstFrame = 7, animationCommand = 0xFF, animationOperand;
        CdPlayerBody.DynamicSlots slots = CdPlayerBody.DynamicSlots.EMPTY_DEVELOPMENT_96;
        Fixture() {
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7;
            Arrays.fill(fg, 2, fg.length, (byte) 2); ids[1] = 1;
            Arrays.fill(heights, 16, 32, (byte) 16);
            for (int x = 0; x < 16; x++) word(chunks, 512 + (6 * 16 + x) * 2, 0x6001);
            word(sine, 0x40 * 2, 256); word(sine, 0xC0 * 2, -256);
            for (int i = 0; i <= 256; i++) atan[i] = (byte) (i / 8);
        }
        void cell(int x, int y, int raw) { fg[2 + y * 42 + x] = (byte) raw; }
        int mapAddress() { return CdR11aProfile.forStage(variant, CdStagePayload.R11A.stage()).range(CdR11aAsset.MAP_SPR_SONIC).nativeAddress(); }
        CdPlayerBody.Environment environment() {
            return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(physics,
                    new CdPlayerMovement.Bounds(0, 0x2800, bottom, 0),
                    new CdPlayerMovement.Gates(0, 1, mini, 0, wind, speedShoes, warpDir),
                    new CdPlayerMovement.Globals(0, 0x60, 0x26, 0x64, 0, 9, 0, 0, 0, 0)),
                    new CdPlayerBody.Controls(rawHeld, rawTap, logicalHeld, logicalTap, ctrlLocked),
                    new CdPlayerBody.Globals(levelFrames, camX, timeAttack, debugMode, debugCheat, timeWarpTimer, invincible, bossMusic), slots);
        }
        CdPlayerBody owner() {
            var terrain = CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg, ids, new byte[256], heights, widths);
            byte[] maps = new byte[398], dplcs = new byte[398], animations = new byte[578];
            for (int i = 0; i < 198; i++) { word(maps, 2 * i, 396); word(dplcs, 2 * i, 396); }
            var sonic = CdSonicVisualAssets.decode(maps, dplcs, new byte[69472]);
            for (int i = 0; i < 60; i++) word(animations, 2 * i, 120);
            animations[120] = 3; animations[121] = (byte) animationFirstFrame; animations[122] = (byte) animationCommand;
            if (animationCommand != 0xFF) animations[123] = (byte) animationOperand;
            var player = CdR11aPlayerAssets.decode(animations, new byte[60],
                    new byte[]{6, 7, 8, 0x49, 0x4C, (byte) 0xFF}, special, sine, atan, sonic);
            var profile = CdR11aProfile.forStage(variant, CdStagePayload.R11A.stage());
            return new CdPlayerBody(new CdR11aAssets(profile, null, null, null, null,
                    terrain, null, null, sonic, player, null));
        }
    }
    private static void word(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }
}
