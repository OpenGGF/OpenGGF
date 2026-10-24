package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerCharge;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;

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

    private static final class Actor {
        int x = 64 << 16, y = 76 << 16, xv, yv, gv, routine = 2, angle, flags, spr = 4;
        int xr = 9, yr = 19, anim, prev, jump, charge, lock, stick, ctrl, pri, sec, hurt, invinc, shoes;
        CdPlayerBody.Header header = new CdPlayerBody.Header(1, 0x21F200, 0x780, 2, 24);
        CdPlayerBody.AnimationCursor cursor = new CdPlayerBody.AnimationCursor(0, 0, 0);
        CdPlayerBody.History history = new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[256]), 0);
        CdPlayerBody.State state() {
            return new CdPlayerBody.State(new CdPlayerMotionState(x, y, xv, yv, gv, routine, angle, flags, spr, xr, yr,
                    anim, prev, jump, charge, lock, stick, ctrl, pri, sec), cursor, header,
                    new CdPlayerBody.Timers(hurt, invinc, shoes), history);
        }
    }

    /** Constructed format data only; production body binds one actual aggregate. */
    private static final class Fixture {
        final byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50], ids = new byte[900];
        final byte[] heights = new byte[4096], widths = new byte[4096], sine = new byte[640], atan = new byte[257];
        final byte[] special = {0x7F, 0x7F, 0x7F, 0x7F};
        CdReleaseVariant variant = CdReleaseVariant.USA;
        CdPlayerMovement.Physics physics = new CdPlayerMovement.Physics(0x600, 12, 0x80);
        int rawHeld, rawTap, logicalHeld, logicalTap, ctrlLocked, levelFrames = 1, camX = 160;
        int timeAttack, debugMode, debugCheat, timeWarpTimer, invincible, speedShoes, bossMusic, mini, wind, warpDir, bottom = 0x710;
        int animationCommand = 0xFF, animationOperand;
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
            animations[120] = 3; animations[121] = 7; animations[122] = (byte) animationCommand;
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
