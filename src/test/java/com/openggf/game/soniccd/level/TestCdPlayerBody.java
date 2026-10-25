package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerCharge;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerObjectTouch;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import com.openggf.game.soniccd.objects.CdLevelGlobals;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.objects.CdRingGlobals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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
    // S2: these native bytes survive ObjSonic; LevelStart's ClearObjects owns their initial zero.
    @Test void objectLinksRequireTwoExplicitNativeBytesAndANonNullStateComponent() {
        for (int bad : new int[]{-1, 256, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.ObjectLinks(bad, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.ObjectLinks(0, bad));
        }
        assertEquals(255, new CdPlayerBody.ObjectLinks(255, 128).pushObject());
        assertEquals(128, new CdPlayerBody.ObjectLinks(255, 128).standObject());
        Actor a = new Actor(); a.objectLinks = null;
        assertThrows(NullPointerException.class, a::state);
    }

    @ParameterizedTest @ValueSource(ints = {0, 2, 4})
    void initMainAndHurtPreserveObjectLinksWithoutNormalizingOrClearingThem(int routine) {
        Fixture f = new Fixture(); Actor a = new Actor(); a.routine = routine;
        a.objectLinks = new CdPlayerBody.ObjectLinks(0xA1, 0xFE);
        if (routine == 4) a.flags = 2;
        var before = a.state(); var result = f.owner().step(before, f.environment());
        assertEquals(before.objectLinks(), result.state().objectLinks());
    }

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

    // The former KILL_PLAYER stop: LevelBound's BNE.W KillPlayer RTS returns into the dispatched MODE routine
    // (Sonic/Main.asm:701,727,748,767,1703-1705), so every mode now completes Main through ONE shared call.
    @ParameterizedTest @CsvSource({"0,0", "2,0", "4,0", "6,0", "0,1", "2,1", "4,1", "6,1"})
    void everyDispatchedModeKillCompletesMainThroughOneSharedKillPlayerCall(int mode, int debugMode) {
        Fixture f = new Fixture(); f.debugMode = debugMode; Actor a = new Actor(); a.flags = mode; a.y = 0x1000 << 16;
        a.anim = (mode & 4) == 0 ? 0 : 2;
        var state = a.state(); var env = f.environment();
        var r = f.owner().step(state, env);
        assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, r.boundary());
        assertEquals(CdPlayerMovement.Boundary.MODE_RETURN, r.modeBoundary().orElseThrow());
        assertEquals(1, kills(r).size());
        assertEquals(debugMode == 0 ? CdPlayerTouchResponse.Exit.KILL_PLAYER : CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE,
                kills(r).getFirst().exit());
        assertEquals(debugMode == 0 ? 6 : 2, r.state().motion().routine());
        int marker = r.effects().indexOf(KILL_MARKER);
        assertEquals(marker, r.effects().lastIndexOf(KILL_MARKER), "one marker, never a replayed prefix");
        assertEquals(new CdPlayerBody.KillPlayerCall(kills(r).getFirst()), r.effects().get(marker + 1));
        assertEquals(new CdPlayerBody.CallReturned(CdPlayerBody.NativeCall.KILL_PLAYER), r.effects().get(marker + 2));
        assertEquals(1, calls(r).stream().filter(c -> c == CdPlayerBody.NativeCall.MODE).count());
        assertTrue(indexCall(r, CdPlayerBody.NativeCall.KILL_PLAYER) < indexCall(r, CdPlayerBody.NativeCall.MODE));
        assertTrue(indexCall(r, CdPlayerBody.NativeCall.MODE) < indexCall(r, CdPlayerBody.NativeCall.DISPLAY));
        assertTrue(r.display().isPresent());
        assertEquals(0, state.history().index()); assertEquals(0, state.animation().mapFrame());
        assertEquals(r, f.owner().step(state, env));
    }

    // Routine 4 is now the owned ObjSonic_Hurt; 10 indexes past the five-entry table (Main.asm:101-106).
    // Without an explicit LevelContext, routines 6 and 8 keep their typed stops.
    @ParameterizedTest @CsvSource({"10,ROUTINE_DISPATCH", "6,DEAD_ROUTINE", "8,RESTART_ROUTINE", "1,ROUTINE_DISPATCH"})
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

    @Test void actualMovementRampContinuesWhileMiniAndTimeWarpPropagateWithoutPublication() {
        Fixture f = new Fixture(); Actor a = new Actor(); f.cell(0, 0, 6);
        var ramp = f.owner().step(a.state(), f.environment());
        assertEquals(2, ramp.state().motion().playerControl() & 2);
        assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, ramp.boundary());
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
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Timers(65536, 0, 0, 0));
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

    // Selected ENEMY/HAZARD response through the real Body call path. Written before the composition.
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

    @ParameterizedTest @CsvSource({"70,MONITOR,false", "199,SPECIAL,true", "199,SPECIAL,false", "43,ENEMY,false", "171,HAZARD,false"})
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
                for (String forbidden : List.of("TouchContext", "ResponseContext", "ContactGlobals", "ObjectContext", "Snapshot",
                        "CdPlayerTouchResponse")) {
                    assertFalse(name.contains(forbidden), type.getSimpleName() + "." + component.getName() + " " + name);
                }
            }
        }
    }

    // Routine-4 ObjSonic_Hurt through the real Body call path. Written before production.
    // Source pin 1ecc5f3a: Sonic/Main.asm:56-106,1673-1714,2351-2391; Object Functions.asm:91-128;
    // PP Player Special Collision.asm:7-8; shared KillPlayer, PP Player Object Collision.asm:279-298.

    @Test void hurtIntegratesOldVelocitiesAddsGravityThenDrawsUnconditionallyWithoutMainServices() {
        Fixture f = new Fixture(); Actor a = hurtActor();
        a.y = 200 << 16; a.xv = -0x200; a.yv = -0x400; a.charge = 29; a.pri = 0x12; a.sec = 0x34;
        a.spr = 0xC4; a.ctrl = 0x40; a.hurt = 120; a.invinc = 5; a.shoes = 5;
        // Main would skip Display (warp 210, invisible, hurt bit 2 clear), expire timers and reject absent touch RAM.
        f.timeWarpTimer = 210; f.invincible = 1; f.speedShoes = 1; f.slots = CdPlayerBody.DynamicSlots.UNIMPLEMENTED;
        f.camX = 150; f.rawHeld = f.rawTap = 8; f.logicalHeld = f.logicalTap = 4;
        var env = f.environment(); var r = f.owner().step(a.state(), env);
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, r.boundary()); assertTrue(r.modeBoundary().isEmpty());
        assertEquals(hurtCalls(CdPlayerBody.NativeCall.LEVEL_COLLISION_IN_AIR, CdPlayerBody.NativeCall.HURT_CHECK_LAND,
                CdPlayerBody.NativeCall.LEVEL_BOUND), calls(r));
        var m = r.state().motion();
        // ObjMove adds the OLD words (X 64-2, Y 200-4) before ADDI.W #$30,oYVel.
        assertEquals(62 << 16, m.centreX16_16()); assertEquals(196 << 16, m.centreY16_16());
        assertEquals(-0x200, m.xVelocity()); assertEquals(-0x3D0, m.yVelocity()); assertEquals(0, m.groundVelocity());
        assertEquals(4, m.routine()); assertEquals(2, m.flags()); assertEquals(30, m.charge(), "header predispatch only");
        assertEquals(0x1A, m.animation()); assertEquals(0xC4, m.spriteFlags(), "no SpecialChunks bit-6 clear");
        assertEquals(0x12, m.previousPrimaryAngle()); assertEquals(0x34, m.previousSecondaryAngle());
        assertEquals(List.of(new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.CENTRE_X_LONG, 62 << 16),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.CENTRE_Y_LONG, 196 << 16),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.Y_VELOCITY, -0x3D0)), actorWrites(r));
        assertEquals(new CdPlayerBody.Timers(120, 5, 5, 0), r.state().timers(), "no Display: hurt, invincibility and shoes hold");
        assertEquals(env.globals(), r.environment().globals(), "no ExtCamera, expiry or other Main global write");
        assertEquals(env.controls(), r.environment().controls(), "no raw-to-logical copy");
        var g = r.environment().movement().globals();
        // Player_LevelColInAir: CalcAngle(-$200,-$3D0) = $B0, the up quadrant; no probe meets terrain at Y196.
        assertEquals(0xB0, g.debugAngle()); assertEquals(0x90, g.debugAngleShift()); assertEquals(0x80, g.debugQuadrant());
        assertEquals(0x26, g.primaryAngle()); assertEquals(0x64, g.secondaryAngle()); assertEquals(9, g.scoreChain());
        assertEquals(env.movement().physics(), r.environment().movement().physics());
        assertEquals(env.movement().gates(), r.environment().movement().gates());
        assertEquals(4, r.state().history().index());
        assertEquals(62, r.state().history().bytes().unsignedWord(0)); assertEquals(196, r.state().history().bytes().unsignedWord(2));
        assertEquals(new CdPlayerBody.AnimationCursor(1, 3, 7), r.state().animation());
        var cut = onlyCut(r);
        assertEquals(new CdPlayerBody.DisplayEligibility(true, 62, 196, 19, 0xC4, 24, 2), cut);
        assertTrue(indexCall(r, CdPlayerBody.NativeCall.ANIMATE) < r.effects().indexOf(cut));
        assertTrue(r.effects().indexOf(cut) < indexCall(r, CdPlayerBody.NativeCall.DRAW_OBJECT));
        assertEquals(new CdPlayerBody.DisplayPublication(true, f.mapAddress(), 0x780, 2, 24, 7, 0xC4, 62 << 16, 196 << 16),
                r.display().orElseThrow());
        assertTrue(bodySounds(r).isEmpty());
        assertFalse(r.effects().stream().anyMatch(e -> e instanceof CdPlayerBody.TouchScanCompleted
                || e instanceof CdPlayerBody.ObjectTouchResult || e instanceof CdPlayerBody.ObjectTouchResponse
                || e instanceof CdPlayerBody.ModeEffect || e instanceof CdPlayerBody.KillPlayerCall));
        assertEquals(r, f.owner().step(a.state(), env));
    }

    @ParameterizedTest @CsvSource({"false,-32752,128,38,100,0", "true,32752,0,0,0,5"})
    void hurtGravityIsTwoNativeWordWritesWithNoFallCapAndTheWrappedWordReachesTheCollision(boolean underwater,
            int yVelocity, int quadrant, int primary, int secondary, int floorDistance) {
        Fixture f = new Fixture(); Actor a = hurtActor(); a.y = 200 << 16; a.yv = 0x7FE0;
        if (underwater) a.flags |= 0x40;
        var r = f.owner().step(a.state(), f.environment());
        var m = r.state().motion();
        // ObjMove adds $7FE0 << 8 first; ADDI.W #$30 then wraps to $8010, and SUBI.W #$20 wraps back (2354-2357).
        assertEquals((327 << 16) | 0xE000, m.centreY16_16()); assertEquals(yVelocity, m.yVelocity());
        var expected = new ArrayList<>(List.of(new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.CENTRE_X_LONG, 64 << 16),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.CENTRE_Y_LONG, (327 << 16) | 0xE000),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.Y_VELOCITY, -0x7FF0)));
        if (underwater) expected.add(new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.Y_VELOCITY, 0x7FF0));
        assertEquals(expected, actorWrites(r));
        // Dry: up quadrant, nothing probed. Underwater: down quadrant, the row-6 band below writes angle 0 at +5.
        var g = r.environment().movement().globals();
        assertEquals(quadrant, g.debugQuadrant()); assertEquals(primary, g.primaryAngle());
        assertEquals(secondary, g.secondaryAngle()); assertEquals(floorDistance, g.debugFloorDistance());
        assertEquals(4, m.routine()); assertEquals(underwater ? 0x42 : 2, m.flags());
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, r.boundary());
    }

    @ParameterizedTest @CsvSource({"264,2048,90,58", "456,51200,80,48"})
    void hurtLandingReadsTheHighMemoryYVelocityByteThenStopsAndReturnsToMainWithAFixedHurtWord(int oldYVelocity,
            int fraction, int angle, int shift) {
        Fixture f = new Fixture(); Actor a = hurtActor(); a.y = 77 << 16; a.xv = -0x100; a.yv = oldYVelocity;
        a.hurt = 7; a.pri = 0x12; a.sec = 0x34;
        var r = f.owner().step(a.state(), f.environment());
        var m = r.state().motion();
        // Post-gravity $0138/$01F8 both read memory byte $01, threshold -9 (Main.asm:2093). Both Y97 sensors
        // return -2, so the floor owner lands at 76 and keeps the ObjMove fraction (:2102-2105).
        assertEquals(63 << 16, m.centreX16_16()); assertEquals((76 << 16) | fraction, m.centreY16_16());
        // HurtChkLand (:2382-2388): zero Y/X/ground words, oAnim 0, SUBQ.B #2,oRoutine, then oPlayerHurt 120.
        assertEquals(0, m.yVelocity()); assertEquals(0, m.xVelocity()); assertEquals(0, m.groundVelocity());
        assertEquals(2, m.routine()); assertEquals(0, m.flags()); assertEquals(0, m.animation());
        assertEquals(120, r.state().timers().hurt());
        assertEquals(List.of(new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.CENTRE_X_LONG, 63 << 16),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.CENTRE_Y_LONG, (78 << 16) | fraction),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.Y_VELOCITY, oldYVelocity + 0x30),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.Y_VELOCITY, 0),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.X_VELOCITY, 0),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.GROUND_VELOCITY, 0),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.ANIMATION, 0),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.ROUTINE, 2),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.HURT_TIMER, 120)), actorWrites(r));
        var g = r.environment().movement().globals();
        assertEquals(angle, g.debugAngle()); assertEquals(shift, g.debugAngleShift()); assertEquals(0, g.debugQuadrant());
        assertEquals(0xFE, g.debugFloorDistance()); assertEquals(0, g.scoreChain());
        // The floor probes write the scratch angle globals; only Main:330-331 copies them into the actor.
        assertEquals(0, g.primaryAngle()); assertEquals(0, g.secondaryAngle());
        assertEquals(0x12, m.previousPrimaryAngle()); assertEquals(0x34, m.previousSecondaryAngle());
        assertFalse(r.effects().stream().anyMatch(CdPlayerBody.AirCollisionEffect.class::isInstance), "bit 2 was clear");
        assertEquals(new CdPlayerBody.DisplayEligibility(true, 63, 76, 19, 4, 24, 2), onlyCut(r));
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, r.boundary());
        var main = f.owner().step(r.state(), r.environment());
        assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, main.boundary());
        assertEquals(119, main.state().timers().hurt(), "Main's Display owns the first decrement");
        assertFalse(main.display().orElseThrow().queued(), "old 120 has bit 2 clear");
    }

    @Test void hurtHighMemoryVelocityByteRejectsTwoTenPixelSensorsSoTheHurtWordStaysFixedInTheAir() {
        Fixture f = new Fixture(); Arrays.fill(f.heights, 16, 32, (byte) 24); // Both Y97 sensors return -10.
        Actor a = hurtActor(); a.y = 77 << 16; a.yv = 0x108; a.hurt = 7;
        var r = f.owner().step(a.state(), f.environment());
        var m = r.state().motion();
        // Stored $0138 reads memory byte $01, threshold -9; a low-byte $38 read would land here instead.
        assertEquals((78 << 16) | 0x800, m.centreY16_16()); assertEquals(0x138, m.yVelocity());
        assertEquals(4, m.routine()); assertEquals(2, m.flags()); assertEquals(0x1A, m.animation());
        assertEquals(7, r.state().timers().hurt(), "airborne Hurt neither decrements nor rewrites the word");
        var g = r.environment().movement().globals();
        assertEquals(0xF6, g.debugFloorDistance()); assertEquals(0, g.primaryAngle()); assertEquals(0, g.secondaryAngle());
        assertEquals(9, g.scoreChain());
        assertEquals(hurtCalls(CdPlayerBody.NativeCall.LEVEL_COLLISION_IN_AIR, CdPlayerBody.NativeCall.HURT_CHECK_LAND,
                CdPlayerBody.NativeCall.LEVEL_BOUND), calls(r));
        assertEquals(3, actorWrites(r).size(), "ObjMove X/Y and gravity only; no landing writes");
    }

    @ParameterizedTest @CsvSource({"2,9,19,2040,0,2036,2,false", "6,7,14,2042,0,2033,2,true",
            "6,7,14,2040,0,2031,1,true", "6,5,10,2040,1,2034,2,true"})
    void hurtPitCallsTheSharedKillPlayerAtBothNativeSitesThenStillRecordsAnimatesAndDraws(int flags, int xRadius,
            int yRadius, int y, int mini, int finalY, int killCount, boolean chargeStop) {
        Fixture f = new Fixture(); f.mini = mini; f.invincible = 1;
        Actor a = hurtActor(); a.flags = flags; a.xr = xRadius; a.yr = yRadius; a.y = y << 16;
        a.xv = -0x200; a.yv = -0x400; a.hurt = 120; a.invinc = 5; a.shoes = 5; a.rotationDistance = 0xA5;
        var r = f.owner().step(a.state(), f.environment());
        var kills = kills(r);
        assertEquals(killCount, kills.size());
        // HurtChkLand's unsigned BCS.W (:2373-2376) sees the post-ObjMove/gravity actor; its KillPlayer returns into Hurt.
        var preKill = new CdPlayerMotionState(62 << 16, (y - 4) << 16, -0x200, -0x3D0, 0, 4, 0, flags, 4, xRadius, yRadius,
                0x1A, 0x1A, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(CdPlayerTouchResponse.killPlayer(new CdPlayerTouchResponse.Actor(preKill, 0x780, 2, 120, 0xA5),
                1, 0, 9, mini), kills.getFirst());
        var reset = new CdPlayerTouchResponse.ResetOnFloor();
        var hurtSound = new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_HURT);
        var chargeStopSound = new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP);
        List<CdPlayerTouchResponse.Call> first = chargeStop ? List.of(reset, chargeStopSound, hurtSound)
                : List.of(reset, hurtSound);
        assertEquals(first, kills.getFirst().calls());
        if (killCount == 2) {
            // ObjSonic_LevelBound's signed BLT (:1697-1705) reads the first call's post-reset Y and oAnim $18.
            assertEquals(List.of(reset, hurtSound), kills.get(1).calls(), "bit 2 is already clear");
            assertEquals(hurtCalls(CdPlayerBody.NativeCall.KILL_PLAYER, CdPlayerBody.NativeCall.HURT_CHECK_LAND,
                    CdPlayerBody.NativeCall.KILL_PLAYER, CdPlayerBody.NativeCall.LEVEL_BOUND), calls(r));
        } else {
            assertEquals(hurtCalls(CdPlayerBody.NativeCall.KILL_PLAYER, CdPlayerBody.NativeCall.HURT_CHECK_LAND,
                    CdPlayerBody.NativeCall.LEVEL_BOUND), calls(r));
        }
        for (var kill : kills) assertEquals(CdPlayerTouchResponse.Exit.KILL_PLAYER, kill.exit());
        var m = r.state().motion();
        assertEquals(6, m.routine()); assertEquals(2, m.flags()); assertEquals(finalY << 16, m.centreY16_16());
        assertEquals(62 << 16, m.centreX16_16());
        assertEquals(0, m.xVelocity()); assertEquals(-0x700, m.yVelocity()); assertEquals(0, m.groundVelocity());
        assertEquals(0x18, m.animation()); assertEquals(0x18, m.previousAnimation());
        assertEquals(mini == 0 ? 9 : 5, m.xRadius()); assertEquals(mini == 0 ? 19 : 10, m.yRadius());
        // MOVE.W oY,oPlayerStick (:289) writes byte 38 and byte 39 at each executed call.
        assertEquals(finalY >>> 8, m.stick()); assertEquals(finalY & 0xFF, r.state().rotationDistance());
        assertEquals(new CdPlayerBody.Header(1, f.mapAddress(), 0x8780, 0, 24), r.state().header());
        assertEquals(new CdPlayerBody.Timers(120, 5, 5, 0), r.state().timers());
        assertEquals(0, r.environment().globals().invincible());
        assertEquals(0, r.environment().movement().globals().scoreChain());
        assertEquals(new CdPlayerBody.DisplayEligibility(true, 62, finalY, mini == 0 ? 19 : 10, 4, 24, 0), onlyCut(r));
        assertTrue(r.display().orElseThrow().queued()); assertEquals(0, r.display().orElseThrow().priority());
        assertTrue(bodySounds(r).isEmpty(), "KillPlayer requests stay in their pure call results");
        assertEquals(finalY, r.state().history().bytes().unsignedWord(2));
        var next = r.state(); var nextEnvironment = r.environment();
        assertEquals(CdPlayerBody.UnsupportedBoundary.DEAD_ROUTINE, assertThrows(CdPlayerBody.UnsupportedBody.class,
                () -> f.owner().step(next, nextEnvironment)).boundary());
    }

    @ParameterizedTest @CsvSource({"1,0", "256,1"})
    void hurtDebugWordStillDispatchesHurtButBothKillPlayerCallsReturnWithoutWrites(int debugMode, int timeAttack) {
        // ObjSonic tests the high byte unless time attack bypasses it (:57-71); KillPlayer:280 tests the WORD.
        Fixture f = new Fixture(); f.debugMode = debugMode; f.timeAttack = timeAttack; f.invincible = 1;
        Actor a = hurtActor(); a.y = 2040 << 16; a.xv = -0x200; a.yv = -0x400; a.rotationDistance = 0xA5;
        var state = a.state(); var r = f.owner().step(state, f.environment());
        var kills = kills(r);
        assertEquals(2, kills.size());
        for (var kill : kills) {
            assertEquals(CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE, kill.exit());
            assertTrue(kill.calls().isEmpty());
        }
        assertEquals(hurtCalls(CdPlayerBody.NativeCall.KILL_PLAYER, CdPlayerBody.NativeCall.HURT_CHECK_LAND,
                CdPlayerBody.NativeCall.KILL_PLAYER, CdPlayerBody.NativeCall.LEVEL_BOUND), calls(r));
        var m = r.state().motion();
        assertEquals(4, m.routine()); assertEquals(2036 << 16, m.centreY16_16()); assertEquals(-0x3D0, m.yVelocity());
        assertEquals(0x1A, m.animation()); assertEquals(0, m.stick()); assertEquals(0xA5, r.state().rotationDistance());
        assertEquals(state.header(), r.state().header()); assertEquals(1, r.environment().globals().invincible());
        assertEquals(9, r.environment().movement().globals().scoreChain());
        assertEquals(debugMode, r.environment().globals().debugMode());
        assertEquals(new CdPlayerBody.DisplayEligibility(true, 62, 2036, 19, 4, 24, 2), onlyCut(r));
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, f.owner().step(r.state(), r.environment()).boundary(),
                "the next call is Hurt again, not a death");
    }

    @Test void hurtUnsignedLandCheckAndSignedLevelBoundAreDistinctKillPredicates() {
        // HurtChkLand: $7FE0 <u $8000 kills; LevelBound's signed $7FE0 < -$8000 then does not (:2373-2376, :1697-1700).
        Fixture f = new Fixture(); f.bottom = 0x7F00; Actor a = hurtActor(); a.y = 0x8000 << 16;
        var unsigned = f.owner().step(a.state(), f.environment());
        assertEquals(1, kills(unsigned).size());
        assertEquals(hurtCalls(CdPlayerBody.NativeCall.KILL_PLAYER, CdPlayerBody.NativeCall.HURT_CHECK_LAND,
                CdPlayerBody.NativeCall.LEVEL_BOUND), calls(unsigned));
        assertEquals(6, unsigned.state().motion().routine()); assertEquals(0x80, unsigned.state().motion().stick());
        assertEquals(0, unsigned.state().rotationDistance());
        // Bottom $7F20 makes HurtChkLand's word $8000: unsigned no, so Player_LevelColInAir runs; the signed BLT kills.
        f.bottom = 0x7F20; a.y = 0x100 << 16;
        var signed = f.owner().step(a.state(), f.environment());
        assertEquals(1, kills(signed).size());
        assertEquals(hurtCalls(CdPlayerBody.NativeCall.LEVEL_COLLISION_IN_AIR, CdPlayerBody.NativeCall.HURT_CHECK_LAND,
                CdPlayerBody.NativeCall.KILL_PLAYER, CdPlayerBody.NativeCall.LEVEL_BOUND), calls(signed));
        assertEquals(6, signed.state().motion().routine()); assertEquals(1, signed.state().motion().stick());
        assertEquals(0, signed.state().rotationDistance());
        assertEquals(28, signed.environment().movement().globals().debugFloorDistance(), "the air collision did run");
    }

    @Test void hurtBoredAnimationRejectsOnlyAtTheSignedBottomBranchWithoutPublication() {
        // ObjSonic_LevelBound .Bottom (:1703-1707) sets routine 6 directly for $2B: the existing port's typed stop.
        Fixture f = new Fixture(); Actor a = hurtActor(); a.anim = a.prev = 0x2B; a.y = 200 << 16;
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, f.owner().step(a.state(), f.environment()).boundary());
        f.bottom = 0x7F20; a.y = 0x100 << 16;
        var state = a.state(); var env = f.environment();
        var failure = assertThrows(CdPlayerMovement.UnsupportedMotion.class, () -> f.owner().step(state, env));
        assertEquals(CdPlayerMovement.UnsupportedBoundary.BORED_GIVE_UP, failure.boundary());
        assertEquals(0, state.history().index()); assertEquals(0x2B, state.motion().animation());
    }

    @Test void hurtObjMoveStandingObjectReadIsAnUnownedPoolBoundaryBeforeAnyPublication() {
        // ObjMove (Object Functions.asm:97-114) reads oPlayerStandObj's oID for the CCZ flipper resistance.
        Fixture f = new Fixture(); Actor a = hurtActor(); a.flags = 0x0A; a.y = 200 << 16; a.yv = -0x400;
        var state = a.state(); var env = f.environment();
        assertEquals(CdPlayerBody.UnsupportedBoundary.STANDING_OBJECT_MOVE,
                assertThrows(CdPlayerBody.UnsupportedBody.class, () -> f.owner().step(state, env)).boundary());
        assertEquals(200 << 16, state.motion().centreY16_16()); assertEquals(0, state.history().index());
        a.flags = 2;
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, f.owner().step(a.state(), env).boundary());
    }

    @Test void hurtNeverSamplesSuppliedTouchInputOrTheUnimplementedTouchFallback() {
        Fixture f = new Fixture(); f.slots = CdPlayerBody.DynamicSlots.UNIMPLEMENTED;
        CdObjectPool pool = new CdObjectPool(); touchSlot(pool, 32, 0x40, 62, 196); touchSlot(pool, 33, 0xAB, 62, 196);
        var context = responseTouch(pool, new Contact()); // A sampled scan rejects selector 0 first.
        Actor a = hurtActor(); a.y = 200 << 16; a.xv = -0x200; a.yv = -0x400;
        var withContext = f.owner().step(a.state(), f.environment(), Optional.of(context));
        assertEquals(f.owner().step(a.state(), f.environment()), withContext);
        assertFalse(withContext.effects().stream().anyMatch(e -> e instanceof CdPlayerBody.TouchScanCompleted
                || e instanceof CdPlayerBody.ObjectTouchResult || e instanceof CdPlayerBody.ObjectTouchResponse));
        assertEquals(pool.capture().slots(), context.snapshot().slots());
        Actor main = new Actor();
        assertThrows(IllegalArgumentException.class, () -> f.owner().step(main.state(), f.environment(), Optional.of(context)),
                "Main's actual Player_ObjCollide call does sample the same context");
    }

    @Test void hurtLevelBoundSideClampFollowsTheAirCollisionAndPrecedesHistoryAndDraw() {
        Fixture f = new Fixture(); Actor a = hurtActor(); a.x = 19 << 16; a.y = 200 << 16; a.xv = -0x200; a.yv = -0x400;
        var r = f.owner().step(a.state(), f.environment());
        // ObjMove leaves X17; LevelBound predicts 15 < left+16, so .Sides writes X16, oXSub 0 and both speeds 0.
        assertEquals(new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.CENTRE_X_LONG, 17 << 16), actorWrites(r).getFirst());
        var m = r.state().motion();
        assertEquals(16 << 16, m.centreX16_16()); assertEquals(0, m.xVelocity()); assertEquals(0, m.groundVelocity());
        assertEquals(16, r.state().history().bytes().unsignedWord(0));
        assertEquals(16, onlyCut(r).centreXWord());
    }

    @Test void hurtRecordsIntegerWordsAtTheCurrentNativeIndexAndRetainsBothFractions() {
        Fixture f = new Fixture(); Actor a = hurtActor();
        a.x = (64 << 16) | 0x1234; a.y = (200 << 16) | 0xABCD; a.xv = -0x200; a.yv = -0x400;
        byte[] original = historyBytes(); a.history = new CdPlayerBody.History(CdAssetBytes.copyOf(original), 0xFC);
        var r = f.owner().step(a.state(), f.environment());
        assertEquals((62 << 16) | 0x1234, r.state().motion().centreX16_16());
        assertEquals((196 << 16) | 0xABCD, r.state().motion().centreY16_16());
        assertEquals(0, r.state().history().index(), "ADDQ.B wraps only the low byte");
        assertEquals(62, r.state().history().bytes().unsignedWord(0xFC));
        assertEquals(196, r.state().history().bytes().unsignedWord(0xFE));
        for (int i = 0; i < 0xFC; i++) assertEquals(Byte.toUnsignedInt(original[i]), r.state().history().bytes().unsignedByte(i));
        assertTrue(r.effects().contains(new CdPlayerBody.PositionHistoryWrite(0xFC, 62, 196, 0)));
    }

    @Test void hurtLandingResetRequestIsPublishedAsTheCollisionOwnersEffectAtItsCall() {
        // Player_LevelColInAir's landing calls Player_ResetOnFloor (:2104); ingress bit 2 makes FM_CHARGESTOP.
        Fixture f = new Fixture(); Actor a = hurtActor(); a.flags = 6; a.xr = 7; a.yr = 14; a.y = 82 << 16; a.yv = 0x100;
        var r = f.owner().step(a.state(), f.environment());
        var m = r.state().motion();
        assertEquals(76 << 16, m.centreY16_16()); assertEquals(9, m.xRadius()); assertEquals(19, m.yRadius());
        assertEquals(0, m.flags()); assertEquals(2, m.routine()); assertEquals(120, r.state().timers().hurt());
        var effect = new CdPlayerBody.AirCollisionEffect(new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_CHARGESTOP));
        assertEquals(1, r.effects().stream().filter(CdPlayerBody.AirCollisionEffect.class::isInstance).count());
        assertTrue(indexCall(r, CdPlayerBody.NativeCall.PP_SPECIAL_COLLISION) < r.effects().indexOf(effect));
        assertTrue(r.effects().indexOf(effect) < indexCall(r, CdPlayerBody.NativeCall.LEVEL_COLLISION_IN_AIR));
        assertTrue(bodySounds(r).isEmpty());
        assertEquals(new CdPlayerBody.DisplayEligibility(true, 64, 76, 19, 4, 24, 2), onlyCut(r));
    }

    @Test void resultAdmitsADrawnHurtOnlyWithAQueuedPoseAndNoModeBoundary() {
        Fixture f = new Fixture(); Actor a = hurtActor(); a.y = 200 << 16;
        var r = f.owner().step(a.state(), f.environment());
        var pose = r.display().orElseThrow();
        var hidden = new CdPlayerBody.DisplayPublication(false, pose.mappingAddress(), pose.tile(), pose.priority(),
                pose.width(), pose.mapFrame(), pose.spriteFlags(), pose.centreX16_16(), pose.centreY16_16());
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Result(r.state(), r.environment(), r.effects(),
                Optional.empty(), CdPlayerBody.Boundary.HURT_RETURN, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Result(r.state(), r.environment(), r.effects(),
                Optional.of(hidden), CdPlayerBody.Boundary.HURT_RETURN, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Result(r.state(), r.environment(), r.effects(),
                r.display(), CdPlayerBody.Boundary.HURT_RETURN, Optional.of(CdPlayerMovement.Boundary.MODE_RETURN)));
        assertEquals(r, new CdPlayerBody.Result(r.state(), r.environment(), r.effects(), r.display(),
                CdPlayerBody.Boundary.HURT_RETURN, Optional.empty()));
        assertThrows(NullPointerException.class, () -> new CdPlayerBody.KillPlayerCall(null));
        assertThrows(NullPointerException.class, () -> new CdPlayerBody.AirCollisionEffect(null));
    }

    // U5-B0 oPlayerReset ($3A word, Level/_Definitions.inc:86) as the fourth Timers word. Written before production.
    // Source pin 1ecc5f3a: only ObjSonic_DeadChkGone writes it (Sonic/Main.asm:2439,2444) and only
    // ObjSonic_Restart reads and counts it (:2454-2457). Routines 0, 2 and 4 never address it.

    @Test void timersRequireFourUnsignedWordsAndTheResetWordIsPartOfTheValue() {
        assertEquals(0xFFFF, new CdPlayerBody.Timers(0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF).reset());
        assertNotEquals(new CdPlayerBody.Timers(120, 5, 5, 0), new CdPlayerBody.Timers(120, 5, 5, 0x003C));
        for (int invalid : new int[]{-1, 0x10000, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Timers(0, 0, 0, invalid));
        }
    }

    @ParameterizedTest @CsvSource({"0,1", "0,65535", "2,60", "2,480", "4,60", "4,65535"})
    void nonzeroResetWordIsIngressThatInitMainAndAirborneHurtNeverWrite(int routine, int reset) {
        Fixture f = new Fixture(); Actor a = routine == 4 ? hurtActor() : new Actor();
        a.routine = routine; a.reset = reset;
        if (routine == 2) { a.hurt = 5; a.invinc = 2; f.invincible = 1; } // Display counts the other words down.
        if (routine == 4) a.y = 200 << 16; // Airborne Hurt: no landing and no kill.
        var state = a.state(); var env = f.environment();
        var r = f.owner().step(state, env);
        assertEquals(reset, r.state().timers().reset());
        if (routine == 2) assertEquals(new CdPlayerBody.Timers(4, 1, 0, reset), r.state().timers());
        assertTrue(actorWrites(r).stream().noneMatch(w -> w.field() == CdPlayerBody.ActorField.RESET_TIMER));
        assertEquals(reset, state.timers().reset(), "ingress is immutable");
        assertEquals(r, f.owner().step(state, env), "the same four-word ingress replays equal");
    }

    @Test void hurtPitKillPlayerWordWriteAtByte38LeavesTheAdjacentResetWord() {
        // MOVE.W oY,oPlayerStick (PP Player Object Collision.asm:289) writes bytes $38-$39 at both calls; $3A-$3B stay.
        Fixture f = new Fixture(); f.invincible = 1;
        Actor a = hurtActor(); a.y = 2040 << 16; a.xv = -0x200; a.yv = -0x400; a.reset = 0x01E0;
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(2, kills(r).size()); assertEquals(6, r.state().motion().routine());
        assertEquals(0x07, r.state().motion().stick()); assertEquals(0xF4, r.state().rotationDistance());
        assertEquals(new CdPlayerBody.Timers(0x78, 0, 0, 0x01E0), r.state().timers());
        assertTrue(actorWrites(r).stream().noneMatch(w -> w.field() == CdPlayerBody.ActorField.RESET_TIMER));
    }

    // U5-K: Main's MODE KillPlayer return through the real Body call path. Written before production.
    // Source pin 1ecc5f3a: Sonic/Main.asm:285-351 (Main), 701-707/727-736/748-756/767-776 (tails), 1673-1714
    // (LevelBound), Collision Floor.asm:30-141 (GroundCol), PP Player Object Collision.asm:279-298 (KillPlayer).
    // Bounds bottom $0710: the signed LevelBound limit is $07F0, so Y $07F1 kills. Rows 8-15 of the constructed
    // layout and blocks outside each chunk's row-6 band are blank: a blank probe returns 31 - (coordinate & 15).

    @Test void airPitKillCallsTheSharedKillPlayerAtTheMarkerThenResumesObjMoveGrvAndTheUpwardAirCollision() {
        Fixture f = new Fixture(); f.invincible = 1;
        Actor a = new Actor(); a.flags = 2; a.y = 2033 << 16; a.pri = 0x12; a.sec = 0x34; a.invinc = 5; a.rotationDistance = 0xA5;
        var r = f.owner().step(a.state(), f.environment());
        assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, r.boundary());
        assertEquals(CdPlayerMovement.Boundary.MODE_RETURN, r.modeBoundary().orElseThrow());
        // KillPlayer (282-295) at the stopped MdAir actor: routine 6, air bit, -$700, oPlayerStick word $07F1.
        var killed = new CdPlayerMotionState(64 << 16, 2033 << 16, 0, -0x700, 0, 6, 0, 2, 4, 9, 19, 0x18, 0, 0, 0, 0,
                7, 0, 0x12, 0x34);
        assertEquals(List.of(new CdPlayerTouchResponse.KillResult(new CdPlayerTouchResponse.Actor(killed, 0x8780, 0, 0, 0xF1),
                0, 0, List.of(RESET, HURT_FM), CdPlayerTouchResponse.Exit.KILL_PLAYER)), kills(r));
        int marker = r.effects().indexOf(KILL_MARKER);
        assertEquals(new CdPlayerBody.KillPlayerCall(kills(r).getFirst()), r.effects().get(marker + 1));
        assertEquals(new CdPlayerBody.CallReturned(CdPlayerBody.NativeCall.KILL_PLAYER), r.effects().get(marker + 2));
        assertEquals(new CdPlayerBody.CallReturned(CdPlayerBody.NativeCall.MODE), r.effects().get(marker + 3),
                "MdAir's resumed tail publishes no effect here");
        assertEquals(1, r.effects().stream().filter(CdPlayerBody.ModeEffect.class::isInstance).count(), "only the marker");
        assertEquals(killedMainCalls(), calls(r));
        var m = r.state().motion();
        // ObjMoveGrv from the killed -$700: Y $07F1-7 = $07EA, yv $F938 (728-731); CalcAngle(0,$F938) = $C0, up.
        assertEquals(64 << 16, m.centreX16_16()); assertEquals(0x07EA0000, m.centreY16_16());
        assertEquals(-0x6C8, m.yVelocity()); assertEquals(0, m.xVelocity()); assertEquals(0, m.groundVelocity());
        assertEquals(6, m.routine()); assertEquals(2, m.flags()); assertEquals(0x18, m.animation());
        assertEquals(7, m.stick()); assertEquals(0xF1, r.state().rotationDistance());
        // Main:330-331 copies the CURRENT buffers only after MODE; KillPlayer and the tail kept $12/$34.
        assertEquals(0x26, m.previousPrimaryAngle()); assertEquals(0x64, m.previousSecondaryAngle());
        var g = r.environment().movement().globals();
        assertEquals(0xC0, g.debugAngle()); assertEquals(0xA0, g.debugAngleShift()); assertEquals(0x80, g.debugQuadrant());
        assertEquals(0x26, g.primaryAngle()); assertEquals(0x64, g.secondaryAngle()); assertEquals(0, g.scoreChain());
        assertEquals(0, r.environment().globals().invincible());
        assertEquals(new CdPlayerBody.Header(1, 0x21F200, 0x8780, 0, 24), r.state().header());
        // KillPlayer cleared invincible BEFORE Display, so Display does not count the timer (Main:389-392).
        assertEquals(new CdPlayerBody.Timers(0, 5, 0, 0), r.state().timers());
        assertEquals(new CdPlayerBody.DisplayEligibility(true, 64, 0x7EA, 19, 4, 24, 0), onlyCut(r),
                "Main's Display follows MODE: the priority-0 killed pose");
        assertEquals(0x7EA, r.state().history().bytes().unsignedWord(2));
        assertEquals(List.of(new CdPlayerBody.TouchScanCompleted(96)), touchVisits(r));
        assertTrue(bodySounds(r).isEmpty(), "FM_HURT stays in the KillPlayer call result");
        assertEquals(r, f.owner().step(a.state(), f.environment()));
    }

    @Test void airDebugWordReachesModeButKillPlayerReturnsUnwrittenAndTheSameTailRunsOnce() {
        // ObjSonic tests only the high byte (Main.asm:69); KillPlayer tests the WORD (PP Player Object Collision.asm:280).
        Fixture f = new Fixture(); f.invincible = 1; f.debugMode = 1;
        Actor a = new Actor(); a.flags = 2; a.y = 2033 << 16; a.pri = 0x12; a.sec = 0x34; a.invinc = 5; a.rotationDistance = 0xA5;
        var r = f.owner().step(a.state(), f.environment());
        var stopped = new CdPlayerMotionState(64 << 16, 2033 << 16, 0, 0, 0, 2, 0, 2, 4, 9, 19, 0, 0, 0, 0, 0, 0, 0, 0x12, 0x34);
        assertEquals(List.of(new CdPlayerTouchResponse.KillResult(new CdPlayerTouchResponse.Actor(stopped, 0x780, 2, 0, 0xA5),
                1, 9, List.of(), CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE)), kills(r));
        assertEquals(killedMainCalls(), calls(r));
        var m = r.state().motion();
        // ObjMoveGrv from the unwritten 0: yv $38, Y kept; CalcAngle(0,$38) = $40, the down quadrant's blank floor 27.
        assertEquals(2033 << 16, m.centreY16_16()); assertEquals(0x38, m.yVelocity()); assertEquals(2, m.routine());
        assertEquals(0, m.stick()); assertEquals(0xA5, r.state().rotationDistance());
        var g = r.environment().movement().globals();
        assertEquals(0x40, g.debugAngle()); assertEquals(0x20, g.debugAngleShift()); assertEquals(0, g.debugQuadrant());
        assertEquals(0x1B, g.debugFloorDistance()); assertEquals(9, g.scoreChain());
        assertEquals(1, r.environment().globals().invincible()); assertEquals(4, r.state().timers().invincibility());
        assertEquals(a.state().header(), r.state().header());
        assertEquals(new CdPlayerBody.DisplayEligibility(true, 64, 2033, 19, 4, 24, 2), onlyCut(r));
        assertEquals(1, r.environment().globals().debugMode());
    }

    @Test void groundPitKillIntegratesTheKilledWordsAndGroundColAlignsByTheStickSoFallOffKeepsTheLock() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.gv = 0x100; a.lock = 5; a.y = (2033 << 16) | 0xABCD;
        a.pri = 0x12; a.sec = 0x34; a.rotationDistance = 0xA5;
        var r = f.owner().step(a.state(), f.environment());
        // MdGround's lock branch settles 256-12 = 244 into both speeds; its $C0 wall probe meets a blank cell.
        var killed = new CdPlayerMotionState(64 << 16, (2033 << 16) | 0xABCD, 0, -0x700, 0, 6, 0, 2, 4, 9, 19, 0x18, 0, 0, 0, 5,
                7, 0, 0x12, 0x34);
        assertEquals(List.of(new CdPlayerTouchResponse.KillResult(new CdPlayerTouchResponse.Actor(killed, 0x8780, 0, 0, 0xF1),
                0, 0, List.of(RESET, HURT_FM), CdPlayerTouchResponse.Exit.KILL_PLAYER)), kills(r));
        var m = r.state().motion();
        // ObjMove -7 (702), then GroundCol's blank 18 aligns because stick $07 is set (Collision Floor.asm:134-136).
        assertEquals(64 << 16, m.centreX16_16()); assertEquals(0x07FCABCD, m.centreY16_16());
        assertEquals(-0x700, m.yVelocity()); assertEquals(0, m.xVelocity()); assertEquals(0, m.groundVelocity());
        assertEquals(5, m.moveLock(), "CheckFallOff returns on the stick byte before the lock (1974-1977)");
        assertEquals(2, m.flags()); assertEquals(0, m.angle()); assertEquals(6, m.routine());
        // GroundCol starts both buffers at 3 and these blank probes write no angle (Collision Floor.asm:39-42).
        var g = r.environment().movement().globals();
        assertEquals(3, g.primaryAngle()); assertEquals(3, g.secondaryAngle());
        assertEquals(3, m.previousPrimaryAngle()); assertEquals(3, m.previousSecondaryAngle());
        assertEquals(new CdPlayerBody.DisplayEligibility(true, 64, 0x7FC, 19, 4, 24, 0), onlyCut(r));
        assertEquals(1, r.effects().stream().filter(CdPlayerBody.ModeEffect.class::isInstance).count());
    }

    @Test void groundDebugWordReturnIntegratesThePreKillWordsDetachesAndCountsTheLock() {
        Fixture f = new Fixture(); f.debugMode = 1;
        Actor a = new Actor(); a.gv = 0x100; a.lock = 5; a.y = (2033 << 16) | 0xABCD; a.pri = 0x12; a.sec = 0x34;
        a.rotationDistance = 0xA5;
        var r = f.owner().step(a.state(), f.environment());
        var stopped = new CdPlayerMotionState(64 << 16, (2033 << 16) | 0xABCD, 0xF4, 0, 0xF4, 2, 0, 0, 4, 9, 19, 0, 0, 0, 0, 5,
                0, 0, 0x12, 0x34);
        assertEquals(List.of(new CdPlayerTouchResponse.KillResult(new CdPlayerTouchResponse.Actor(stopped, 0x780, 2, 0, 0xA5),
                0, 9, List.of(), CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE)), kills(r));
        var m = r.state().motion();
        // ObjMove adds $F4 << 8 to X (702); GroundCol's blank 27 > 14 with stick 0 detaches (Collision Floor.asm:134-141).
        assertEquals(0x0040F400, m.centreX16_16()); assertEquals((2033 << 16) | 0xABCD, m.centreY16_16());
        assertEquals(0xF4, m.xVelocity()); assertEquals(0xF4, m.groundVelocity());
        assertEquals(2, m.flags()); assertEquals(4, m.moveLock()); assertEquals(2, m.routine());
        var g = r.environment().movement().globals();
        assertEquals(3, g.primaryAngle()); assertEquals(3, g.secondaryAngle());
    }

    @Test void rollPitKillTestsThePostKillChargeByteSoAHeldSpindashSkipsObjMove() {
        Fixture f = new Fixture(); f.rawHeld = 2; // DOWN keeps the charge: MoveRoll.NotRight returns (1366-1438).
        Actor a = new Actor(); a.flags = 4; a.xr = 7; a.yr = 14; a.anim = a.prev = 2; a.charge = 20; a.y = 2033 << 16;
        a.pri = 0x12; a.sec = 0x34; a.rotationDistance = 0xA5;
        var r = f.owner().step(a.state(), f.environment());
        var gv75 = new CdPlayerBody.ModeEffect(new CdPlayerMovement.ChargeEffect(
                new CdPlayerCharge.ActorWrite(CdPlayerCharge.ActorField.GROUND_VELOCITY, 75)));
        int marker = r.effects().indexOf(KILL_MARKER);
        assertEquals(List.of(gv75, gv75), r.effects().subList(marker - 2, marker), "the prefix charge writes, once");
        // Player_ResetOnFloor unrolls inside KillPlayer (Main.asm:2305-2325): radii $13/9, Y-5, FM_CHARGESTOP.
        var killed = new CdPlayerMotionState(64 << 16, 2028 << 16, 0, -0x700, 0, 6, 0, 2, 4, 9, 19, 0x18, 2, 0, 21, 0,
                7, 0, 0x12, 0x34);
        assertEquals(List.of(new CdPlayerTouchResponse.KillResult(new CdPlayerTouchResponse.Actor(killed, 0x8780, 0, 0, 0xEC),
                0, 0, List.of(RESET, CHARGE_STOP_FM, HURT_FM), CdPlayerTouchResponse.Exit.KILL_PLAYER)), kills(r));
        var m = r.state().motion();
        // TST.B oPlayerCharge (749-751) reads 21: no ObjMove despite -$700; GroundCol's blank 16 aligns by stick $07.
        assertEquals(0x07FC0000, m.centreY16_16()); assertEquals(-0x700, m.yVelocity());
        assertEquals(21, m.charge()); assertEquals(0, m.groundVelocity()); assertEquals(2, m.flags());
        assertEquals(9, m.xRadius()); assertEquals(19, m.yRadius()); assertEquals(6, m.routine());
        assertEquals(new CdPlayerBody.DisplayEligibility(true, 64, 0x7FC, 19, 4, 24, 0), onlyCut(r));
    }

    @Test void rollDebugWordReturnStillSkipsObjMoveOnTheHeldChargeAndDetachesWithRollingRadii() {
        Fixture f = new Fixture(); f.debugMode = 1; f.rawHeld = 2;
        Actor a = new Actor(); a.flags = 4; a.xr = 7; a.yr = 14; a.anim = a.prev = 2; a.charge = 20; a.y = 2033 << 16;
        a.pri = 0x12; a.sec = 0x34; a.rotationDistance = 0xA5;
        var r = f.owner().step(a.state(), f.environment());
        var stopped = new CdPlayerMotionState(64 << 16, 2033 << 16, 0, 0, 75, 2, 0, 4, 4, 7, 14, 2, 2, 0, 21, 0, 0, 0, 0x12, 0x34);
        assertEquals(List.of(new CdPlayerTouchResponse.KillResult(new CdPlayerTouchResponse.Actor(stopped, 0x780, 2, 0, 0xA5),
                0, 9, List.of(), CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE)), kills(r));
        var m = r.state().motion();
        // No ObjMove (charge 21); GroundCol's blank 16 > 14 with stick 0 sets the air bit.
        assertEquals(2033 << 16, m.centreY16_16()); assertEquals(6, m.flags()); assertEquals(75, m.groundVelocity());
        assertEquals(21, m.charge()); assertEquals(7, m.xRadius()); assertEquals(14, m.yRadius()); assertEquals(2, m.routine());
    }

    @Test void jumpPitKillUnderwaterResumesWithTheWaterDecrementAndOnePositiveJumpAngleStep() {
        Fixture f = new Fixture();
        Actor a = new Actor(); a.flags = 0x46; a.angle = 0x41; a.xr = 7; a.yr = 14; a.anim = a.prev = 2;
        a.y = (2033 << 16) | 0xBEEF; a.pri = 0x12; a.sec = 0x34; a.rotationDistance = 0xA5;
        var r = f.owner().step(a.state(), f.environment());
        var killed = new CdPlayerMotionState(64 << 16, (2028 << 16) | 0xBEEF, 0, -0x700, 0, 6, 0x41, 0x42, 4, 9, 19, 0x18, 2,
                0, 0, 0, 7, 0, 0x12, 0x34);
        assertEquals(List.of(new CdPlayerTouchResponse.KillResult(new CdPlayerTouchResponse.Actor(killed, 0x8780, 0, 0, 0xEC),
                0, 0, List.of(RESET, CHARGE_STOP_FM, HURT_FM), CdPlayerTouchResponse.Exit.KILL_PLAYER)), kills(r));
        var m = r.state().motion();
        // ObjMoveGrv $F938, BTST #6 then SUBI.W #$28 = $F910 (768-771); JumpAngle $41 -> $3F (2013-2036).
        assertEquals(0x07E5BEEF, m.centreY16_16()); assertEquals(-0x6F0, m.yVelocity()); assertEquals(0x3F, m.angle());
        assertEquals(0x42, m.flags()); assertEquals(6, m.routine());
        var g = r.environment().movement().globals();
        assertEquals(0xC0, g.debugAngle()); assertEquals(0x80, g.debugQuadrant());
        assertEquals(new CdPlayerBody.DisplayEligibility(true, 64, 0x7E5, 19, 4, 24, 0), onlyCut(r));
    }

    @Test void jumpDebugWordReturnAppliesGravityAndTheWaterWordOnceAndStepsTheAngleOnce() {
        Fixture f = new Fixture(); f.debugMode = 1;
        Actor a = new Actor(); a.flags = 0x46; a.angle = 0x41; a.xr = 7; a.yr = 14; a.anim = a.prev = 2;
        a.y = (2033 << 16) | 0xBEEF; a.pri = 0x12; a.sec = 0x34; a.rotationDistance = 0xA5;
        var r = f.owner().step(a.state(), f.environment());
        var stopped = new CdPlayerMotionState(64 << 16, (2033 << 16) | 0xBEEF, 0, 0, 0, 2, 0x41, 0x46, 4, 7, 14, 2, 2, 0, 0, 0,
                0, 0, 0x12, 0x34);
        assertEquals(List.of(new CdPlayerTouchResponse.KillResult(new CdPlayerTouchResponse.Actor(stopped, 0x780, 2, 0, 0xA5),
                0, 9, List.of(), CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE)), kills(r));
        var m = r.state().motion();
        // 0+$38-$28 = $10 (768-771); CalcAngle(0,$10) = $40: the down quadrant's blank floor 16, no landing.
        assertEquals((2033 << 16) | 0xBEEF, m.centreY16_16()); assertEquals(0x10, m.yVelocity()); assertEquals(0x3F, m.angle());
        assertEquals(0x46, m.flags()); assertEquals(2, m.routine());
        assertEquals(0x10, r.environment().movement().globals().debugFloorDistance());
    }

    @Test void mainTouchAfterTheResumedKillScansTheFinalPostTailPosition() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.flags = 2; a.y = 2033 << 16;
        CdObjectPool pool = new CdObjectPool(); var ring = touchSlot(pool, 32, 0x47, 64, 2008);
        // At the stopped Y $07F1 the player top $07E1 is below this item's bottom; at the resumed $07EA it overlaps.
        var r = f.owner().step(a.state(), f.environment(), Optional.of(touchContext(pool)));
        assertEquals(0x07EA0000, r.state().motion().centreY16_16());
        assertSame(ring, objectTouch(r).selectedSlot().orElseThrow());
        assertEquals(4, objectTouch(r).changedSlot().orElseThrow().routine(), "the post-Display hurt word 0 is below $5A");
        assertEquals(List.of(new CdPlayerBody.TouchScanCompleted(1)), touchVisits(r));
        assertTrue(indexOf(r, CdPlayerBody.KillPlayerCall.class) < indexOf(r, CdPlayerBody.ObjectTouchResult.class));
        assertEquals(2, pool.state(32).routine());
    }

    @Test void killAndResumeBeforeALaterMainOwnerBoundaryPublishNothingAndRetryIsDeterministic() {
        // The tunnel chunk reaches StartRoll's admission with KillPlayer's air bit: an owner boundary after the resume.
        Fixture f = new Fixture(); f.special[3] = 2; Actor a = new Actor(); a.flags = 2; a.y = 2033 << 16;
        var state = a.state(); var env = f.environment(); var failing = f.owner();
        List<CdPlayerBody.Result> publications = new ArrayList<>();
        var failure = assertThrows(CdPlayerBody.UnsupportedBody.class, () -> publications.add(failing.step(state, env)));
        assertEquals(CdPlayerBody.UnsupportedBoundary.ROLL_TUNNEL_OWNER, failure.boundary());
        assertTrue(publications.isEmpty());
        assertEquals(2, state.motion().routine()); assertEquals(0, state.history().index());
        f.special[3] = 0x7F; var safe = f.owner();
        var r = safe.step(state, env);
        assertEquals(6, r.state().motion().routine()); assertEquals(r, safe.step(state, env));
    }

    @Test void resultInvariantStillRejectsAnUnfinishedModeKillPublication() {
        Fixture f = new Fixture(); Actor a = new Actor(); a.flags = 2; a.y = 2033 << 16;
        var r = f.owner().step(a.state(), f.environment());
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Result(r.state(), r.environment(), r.effects(),
                r.display(), CdPlayerBody.Boundary.MAIN_RETURN, Optional.of(CdPlayerMovement.Boundary.KILL_PLAYER)));
        assertEquals(r, new CdPlayerBody.Result(r.state(), r.environment(), r.effects(), r.display(),
                CdPlayerBody.Boundary.MAIN_RETURN, Optional.of(CdPlayerMovement.Boundary.MODE_RETURN)));
        assertFalse(Arrays.stream(CdPlayerBody.UnsupportedBoundary.values()).anyMatch(b -> b.name().equals("KILL_PLAYER")),
                "the MODE kill continuation is owned, so its former stop is retired");
    }

    // U5-B1 routine-6 ObjSonic_Dead through the real Body call path. Written before production.
    // Source pin 1ecc5f3a: Sonic/Main.asm:2397-2447; Object Functions.asm:42-82 (ObjMoveGrv) and 137 (DrawObject);
    // Object Spawner.asm:329-340 (FindObjSlot); Level/_Definitions.inc:165-174 (dynObjectsEnd $FFF000).

    @Test void deadAtTheUnsignedGoneLineOnlyFallsThroughObjMoveGrvThenRecordsAnimatesAndDraws() {
        Fixture f = new Fixture(); f.animationFirstFrame = 0x6A; // SonAni_Death dc.b 3,$6A,$FF (Data/Animations.asm:101).
        Actor a = deadActor(); a.y = 0x0810 << 16; a.pri = 0x12; a.sec = 0x34;
        var level = new LevelCut().context(); var state = a.state(); var env = f.environment();
        var r = f.owner().step(state, env, Optional.empty(), Optional.of(level));
        assertEquals(CdPlayerBody.Boundary.DEAD_RETURN, r.boundary()); assertTrue(r.modeBoundary().isEmpty());
        // CMP.W oY,d0 / BCC.W with d0 = $0710+$100: an equal word is not gone (2411-2414).
        assertEquals(List.of(CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH, CdPlayerBody.NativeCall.DEAD_CHECK_GONE,
                CdPlayerBody.NativeCall.OBJ_MOVE_GRAVITY, CdPlayerBody.NativeCall.RECORD_POSITION,
                CdPlayerBody.NativeCall.ANIMATE, CdPlayerBody.NativeCall.DRAW_OBJECT), calls(r));
        assertTrue(levelWrites(r).isEmpty()); assertTrue(gone(r).isEmpty());
        // ObjMoveGrv: $F900+$38 = $F938; Y adds the OLD word, $0810.0000 - $7.0000 (Object Functions.asm:51-81).
        assertEquals(List.of(new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.Y_VELOCITY, -0x6C8),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.CENTRE_X_LONG, 0x00400000),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.CENTRE_Y_LONG, 0x08090000)), actorWrites(r));
        var m = r.state().motion();
        assertEquals(0x00400000, m.centreX16_16()); assertEquals(0x08090000, m.centreY16_16());
        assertEquals(-0x6C8, m.yVelocity()); assertEquals(6, m.routine()); assertEquals(0x18, m.previousAnimation());
        assertEquals(0x12, m.previousPrimaryAngle()); assertEquals(0x34, m.previousSecondaryAngle(), "no Main angle copy");
        assertEquals(new CdPlayerBody.AnimationCursor(1, 3, 0x6A), r.state().animation());
        assertEquals(new CdPlayerBody.Timers(0, 0, 0, 0), r.state().timers());
        assertEquals(new CdPlayerBody.DisplayEligibility(true, 64, 0x809, 19, 4, 24, 0), onlyCut(r));
        assertEquals(new CdPlayerBody.DisplayPublication(true, 0x21F200, 0x8780, 0, 24, 0x6A, 4, 0x00400000, 0x08090000),
                r.display().orElseThrow());
        assertEquals(4, r.state().history().index());
        assertEquals(64, r.state().history().bytes().unsignedWord(0)); assertEquals(0x809, r.state().history().bytes().unsignedWord(2));
        assertEquals(env, r.environment(), "no ExtCamera, control copy, Display timer or global write");
        assertTrue(bodySounds(r).isEmpty());
        assertEquals(r, f.owner().step(state, env, Optional.empty(), Optional.of(level)));
    }

    @Test void goneWritesTheNativeOrderAllocatesTheGameOverObjectAndStillFallsWithTheNewWord() {
        Fixture f = new Fixture(); Actor a = deadActor(); a.y = 0x0811 << 16;
        LevelCut level = new LevelCut(); // lives 3, ring HUD lives 0, all 96 dynamic slots clear
        var context = level.context();
        var r = f.owner().step(a.state(), f.environment(), Optional.empty(), Optional.of(context));
        var staged = context.snapshot().slots().getFirst().moveByte(CdObjectState.ByteField.ID, 0x3B);
        assertEquals(List.of(new CdPlayerBody.CallReturned(CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH),
                // DeadChkGone (2416-2444): yv, routine, HUD time, HUD lives, lives and BPL, then .LoadGameOver.
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.Y_VELOCITY, -0x38),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.ROUTINE, 8),
                new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.UPDATE_HUD_TIME, 0),
                new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.UPDATE_HUD_LIVES, 1),
                new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.LIVES, 2),
                new CdPlayerBody.CallReturned(CdPlayerBody.NativeCall.FIND_OBJ_SLOT),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.RESET_TIMER, 0x1E0),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.RESET_TIMER, 0x3C),
                new CdPlayerBody.DeadCheckGone(2, 1, 0, Optional.of(new CdPlayerTouchResponse.FindObjSlot(OptionalInt.of(32))),
                        Optional.of(staged)),
                new CdPlayerBody.CallReturned(CdPlayerBody.NativeCall.DEAD_CHECK_GONE),
                // ObjMoveGrv reads the NEW -$38: +$38 = 0, no cap; Y adds the old -$38 word (Object Functions.asm:51-81).
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.Y_VELOCITY, 0),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.CENTRE_X_LONG, 0x00400000),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.CENTRE_Y_LONG, 0x0810C800),
                new CdPlayerBody.CallReturned(CdPlayerBody.NativeCall.OBJ_MOVE_GRAVITY)), r.effects().subList(0, 15));
        var image = gone(r).getFirst().gameOverImage().orElseThrow();
        byte[] expected = new byte[CdObjectState.BYTE_SIZE]; expected[0] = 0x3B;
        assertArrayEquals(expected, image.copyBytes()); assertEquals(32, image.identity().wholeIndex());
        var m = r.state().motion();
        assertEquals(8, m.routine()); assertEquals(0, m.yVelocity()); assertEquals(0x0810C800, m.centreY16_16());
        assertEquals(new CdPlayerBody.Timers(0, 0, 0, 0x3C), r.state().timers(), "only the final reset word is adopted");
        assertEquals(CdPlayerBody.Boundary.DEAD_RETURN, r.boundary());
        assertTrue(r.display().orElseThrow().queued());
        assertEquals(new CdPlayerBody.DisplayEligibility(true, 64, 0x810, 19, 4, 24, 0), onlyCut(r));
        assertEquals(context.snapshot().slots(), level.pool.capture().slots(), "Body writes no pool slot");
        assertEquals(0, level.pool.state(32).id());
        assertTrue(bodySounds(r).isEmpty());
        assertEquals(r, f.owner().step(a.state(), f.environment(), Optional.empty(), Optional.of(context)));
    }

    @ParameterizedTest @CsvSource({"3,0,2,false,2,1,false", "1,0,0,false,0,1,true", "0,0,255,true,0,1,true",
            "129,0,128,true,0,1,true", "128,0,127,false,127,1,false", "3,255,2,false,2,0,false"})
    void livesSubqBplCapAndTheHudLivesByteWrapSelectTheResetDelay(int lives, int hudLives, int decremented, boolean capped,
                                                                  int finalLives, int finalHud, boolean gameOver) {
        Fixture f = new Fixture(); Actor a = deadActor(); a.y = 0x0811 << 16;
        LevelCut level = new LevelCut(); level.lives = lives; level.hudLives = hudLives;
        var r = stepLevel(f, a, level);
        var writes = new ArrayList<>(List.of(new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.UPDATE_HUD_TIME, 0),
                new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.UPDATE_HUD_LIVES, finalHud),
                new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.LIVES, decremented)));
        if (capped) writes.add(new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.LIVES, 0)); // BPL.S fails: CLR.B (2422-2423).
        assertEquals(writes, levelWrites(r));
        // MOVE.W #8*60 always on .LoadGameOver; TST.B lives then BEQ.S skips the 60 (2439-2444).
        assertEquals(gameOver ? List.of(0x1E0) : List.of(0x1E0, 0x3C), resetWrites(r));
        assertEquals(gameOver ? 0x1E0 : 0x3C, r.state().timers().reset());
        var summary = gone(r).getFirst();
        assertEquals(finalLives, summary.lives()); assertEquals(finalHud, summary.updateHudLives());
        assertEquals(0, summary.updateHudTime());
        assertEquals(Optional.of(new CdPlayerTouchResponse.FindObjSlot(OptionalInt.of(32))), summary.allocation());
    }

    @ParameterizedTest @CsvSource({"24,3,false,0,60", "43,1,true,0,480", "43,3,true,2,60"})
    void timeAttackClearsLivesWithoutTheAllocationUnlessTheGiveUpAnimationSelectsIt(int animation, int lives, boolean allocated,
                                                                                    int finalLives, int reset) {
        Fixture f = new Fixture(); f.timeAttack = 1;
        Actor a = deadActor(); a.anim = animation; a.y = 0x0811 << 16;
        LevelCut level = new LevelCut(); level.lives = lives;
        var r = stepLevel(f, a, level);
        var summary = gone(r).getFirst();
        assertEquals(finalLives, summary.lives()); assertEquals(reset, r.state().timers().reset());
        assertEquals(allocated, calls(r).contains(CdPlayerBody.NativeCall.FIND_OBJ_SLOT));
        if (allocated) {
            // CMPI.B #$2B,oAnim precedes the time-attack test (2426-2430).
            assertEquals(Optional.of(new CdPlayerTouchResponse.FindObjSlot(OptionalInt.of(32))), summary.allocation());
            assertTrue(summary.gameOverImage().isPresent());
        } else {
            assertEquals(Optional.empty(), summary.allocation()); assertEquals(Optional.empty(), summary.gameOverImage());
            // MOVE.B #0,lives then BRA.S .ResetDelay (2432-2433,2443-2444): no 8*60 write and no object.
            assertEquals(List.of(new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.UPDATE_HUD_TIME, 0),
                    new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.UPDATE_HUD_LIVES, 1),
                    new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.LIVES, 2),
                    new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.LIVES, 0)), levelWrites(r));
            assertEquals(List.of(0x3C), resetWrites(r));
        }
    }

    @Test void fullPoolAnswerIsTheUncheckedEndAliasWithNoSlotImageOrPoolWrite() {
        Fixture f = new Fixture(); Actor a = deadActor(); a.y = 0x0811 << 16; LevelCut level = new LevelCut();
        for (int whole = 32; whole < 128; whole++) {
            level.pool.write(level.pool.state(whole).moveByte(CdObjectState.ByteField.ID, 0x10));
        }
        var context = level.context();
        var r = f.owner().step(a.state(), f.environment(), Optional.empty(), Optional.of(context));
        // The exhausted DBF leaves a1 = dynObjectsEnd; MOVE.B #$3B,oID(a1) has no branch (2436-2437).
        int find = indexCall(r, CdPlayerBody.NativeCall.FIND_OBJ_SLOT);
        assertEquals(List.of(new CdPlayerBody.UncheckedAliasWrite(0xFFF000, 0x3B),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.RESET_TIMER, 0x1E0),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.RESET_TIMER, 0x3C),
                new CdPlayerBody.DeadCheckGone(2, 1, 0, Optional.of(new CdPlayerTouchResponse.FindObjSlot(OptionalInt.empty())),
                        Optional.empty()),
                new CdPlayerBody.CallReturned(CdPlayerBody.NativeCall.DEAD_CHECK_GONE)), r.effects().subList(find + 1, find + 6));
        assertEquals(1, r.effects().stream().filter(CdPlayerBody.UncheckedAliasWrite.class::isInstance).count());
        assertEquals(context.snapshot().slots(), level.pool.capture().slots());
        assertEquals(8, r.state().motion().routine()); assertEquals(0x3C, r.state().timers().reset());
    }

    @Test void findObjSlotTakesTheFirstIdZeroSlotAndStagesOnlyItsIdOverDirtyBytes() {
        Fixture f = new Fixture(); Actor a = deadActor(); a.y = 0x0811 << 16; LevelCut level = new LevelCut();
        level.pool.write(level.pool.state(32).moveByte(CdObjectState.ByteField.ID, 0x10).moveByte(CdObjectState.ByteField.ROUTINE, 2));
        // An ObjNull-style slot: oID zero, other bytes dirty. FindObjSlot tests only TST.B (a1) (Object Spawner.asm:334).
        var dirty = level.pool.state(33).moveByte(CdObjectState.ByteField.ROUTINE, 4).moveByte(CdObjectState.ByteField.SUBTYPE, 7)
                .moveWord(CdObjectState.WordField.X, 0x1234).moveByte(CdObjectState.ByteField.SPRITE_FLAGS, 0x84)
                .moveByte(CdObjectState.ByteField.COLLISION_TYPE, 0x47);
        level.pool.write(dirty);
        var r = stepLevel(f, a, level);
        var summary = gone(r).getFirst();
        assertEquals(Optional.of(new CdPlayerTouchResponse.FindObjSlot(OptionalInt.of(33))), summary.allocation());
        var image = summary.gameOverImage().orElseThrow();
        byte[] expected = dirty.copyBytes(); expected[0] = 0x3B;
        assertArrayEquals(expected, image.copyBytes()); assertEquals(33, image.identity().wholeIndex());
        assertEquals(4, image.routine()); assertEquals(0x1234, image.centreX());
        assertSame(dirty, level.pool.state(33));
        LevelCut last = new LevelCut();
        for (int whole = 32; whole < 127; whole++) {
            last.pool.write(last.pool.state(whole).moveByte(CdObjectState.ByteField.ID, 1));
        }
        assertEquals(Optional.of(new CdPlayerTouchResponse.FindObjSlot(OptionalInt.of(127))),
                gone(stepLevel(f, a, last)).getFirst().allocation());
    }

    @ParameterizedTest @CsvSource({"1808,2064,false", "1808,2065,true", "1808,32768,true", "65408,128,false",
            "65408,129,true", "65535,255,false", "65535,256,true"})
    void goneLineIsTheUnsignedWordBottomPlus256(int bottom, int y, boolean goneExpected) {
        // ADDI.W #256 wraps ($FF80 -> $0080); CMP.W/BCC.W is unsigned, so Y $8000 is below, not above, the line.
        Fixture f = new Fixture(); f.bottom = bottom; Actor a = deadActor(); a.y = y << 16;
        var r = stepLevel(f, a, new LevelCut());
        assertEquals(goneExpected ? 8 : 6, r.state().motion().routine());
        assertEquals(goneExpected ? 1 : 0, gone(r).size());
        assertEquals(CdPlayerBody.Boundary.DEAD_RETURN, r.boundary());
    }

    @ParameterizedTest @CsvSource({
            "4048,0,4104,true,4096,68145152",       // $0FD0+$38 = $1008, then BMI clear and the unsigned cap
            "4040,0,4096,true,4096,68143104",       // $1000 is not below: BCS.S fails and MOVE.W rewrites it
            "4039,0,4095,false,4095,68142848",
            "-1,0,55,false,55,67108608",            // $FFFF+$38 crosses zero
            "32736,0,-32744,false,-32744,75489280", // $7FE0+$38 wraps to $8018: TST.W/BMI skips the cap
            "-2049,2,,false,-2049,66584320",        // ramp bit and $F7FF <u $F800: no gravity (:58-61)
            "-2048,2,-1992,false,-1992,66584576",   // $F800 is not below: gravity
            "256,2,312,false,312,67174400",         // BPL on the OLD word reaches .CheckGravity first (:51-56)
            "-2048,4,,false,-2048,66584576",        // bar bit: no gravity (:64-65)
            "256,4,,false,256,67174400",
            "-2048,8,,false,-2048,66584576",        // pole bit precedes every gravity test (:53-54)
            "4608,8,,true,4096,68288512",           // no gravity, but the fall cap still writes $1000 (:68-73)
            "-30000,10,,false,-30000,59428864"})
    void objMoveGrvTestsThePlayerControlBitsInSourceOrderAndIntegratesTheOldWord(int yVelocity, int control,
            Integer gravityWord, boolean capped, int finalYVelocity, int finalY) {
        Fixture f = new Fixture(); Actor a = deadActor(); a.y = 0x400 << 16; a.yv = yVelocity; a.xv = -0x123; a.ctrl = control;
        var r = stepLevel(f, a, new LevelCut());
        var expected = new ArrayList<CdPlayerBody.ActorWrite>();
        if (gravityWord != null) expected.add(new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.Y_VELOCITY, gravityWord));
        if (capped) expected.add(new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.Y_VELOCITY, 0x1000));
        // X adds EXT.L oXVel << 8 = -$12300; Y adds the OLD word << 8 (Object Functions.asm:43-49,76-81).
        expected.add(new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.CENTRE_X_LONG, 0x003EDD00));
        expected.add(new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.CENTRE_Y_LONG, finalY));
        assertEquals(expected, actorWrites(r));
        assertEquals(finalYVelocity, r.state().motion().yVelocity());
        assertEquals(control, r.state().motion().playerControl(), "ObjMoveGrv only reads oPlayerCtrl");
        assertEquals(CdPlayerBody.Boundary.DEAD_RETURN, r.boundary());
    }

    @ParameterizedTest @CsvSource({"6,DEAD_ROUTINE", "8,RESTART_ROUTINE"})
    void withoutLevelContextDeadAndRestartKeepTheirTypedStopsAndPublishNothing(int routine,
                                                                              CdPlayerBody.UnsupportedBoundary expected) {
        Fixture f = new Fixture(); Actor a = deadActor(); a.routine = routine; a.y = 0x0811 << 16; a.reset = 1; a.charge = 29;
        var state = a.state(); var env = f.environment(); var owner = f.owner();
        assertEquals(expected, assertThrows(CdPlayerBody.UnsupportedBody.class, () -> owner.step(state, env)).boundary());
        assertEquals(expected, assertThrows(CdPlayerBody.UnsupportedBody.class,
                () -> owner.step(state, env, Optional.empty())).boundary());
        assertEquals(expected, assertThrows(CdPlayerBody.UnsupportedBody.class,
                () -> owner.step(state, env, Optional.empty(), Optional.empty())).boundary());
        assertEquals(29, state.motion().charge()); assertEquals(1, state.timers().reset());
        assertThrows(NullPointerException.class, () -> owner.step(state, env, Optional.empty(), null));
    }

    @Test void deadDrawsOnlyThroughTheSourceBoundMapping() {
        Fixture f = new Fixture(); Actor a = deadActor(); a.header = new CdPlayerBody.Header(1, 0, 0x8780, 0, 24);
        var state = a.state();
        assertEquals(CdPlayerBody.UnsupportedBoundary.MAPPING_BINDING, assertThrows(CdPlayerBody.UnsupportedBody.class,
                () -> f.owner().step(state, f.environment(), Optional.empty(), Optional.of(new LevelCut().context()))).boundary());
    }

    @Test void levelContextRequiresAllThreeDeclaredPureInputs() {
        LevelCut level = new LevelCut(); var snapshot = level.pool.capture(); var ring = level.ring(); var globals = level.globals();
        var context = new CdPlayerBody.LevelContext(snapshot, ring, globals);
        assertSame(snapshot, context.snapshot()); assertSame(ring, context.ring()); assertSame(globals, context.level());
        assertThrows(NullPointerException.class, () -> new CdPlayerBody.LevelContext(null, ring, globals));
        assertThrows(NullPointerException.class, () -> new CdPlayerBody.LevelContext(snapshot, null, globals));
        assertThrows(NullPointerException.class, () -> new CdPlayerBody.LevelContext(snapshot, ring, null));
    }

    @Test void initMainHurtAndAbsentSlotsNeverSampleALevelContext() {
        Fixture f = new Fixture(); var owner = f.owner();
        LevelCut level = new LevelCut(); touchSlot(level.pool, 32, 0x40, 64, 76); // A scanned selector 0 would reject.
        var context = Optional.of(level.context());
        Actor main = new Actor();
        assertEquals(owner.step(main.state(), f.environment()), owner.step(main.state(), f.environment(), Optional.empty(), context));
        var touched = owner.step(main.state(), f.environment(), Optional.of(touchContext(new CdObjectPool())), context);
        assertEquals(CdPlayerObjectTouch.Kind.NONE, objectTouch(touched).kind(), "Main's touch reads only the TouchContext");
        Actor init = new Actor(); init.routine = 0;
        assertEquals(owner.step(init.state(), f.environment()), owner.step(init.state(), f.environment(), Optional.empty(), context));
        Actor hurt = hurtActor(); hurt.y = 200 << 16;
        assertEquals(owner.step(hurt.state(), f.environment()), owner.step(hurt.state(), f.environment(), Optional.empty(), context));
        Actor absent = new Actor(); absent.header = new CdPlayerBody.Header(0, 0, 0, 0, 0);
        var none = owner.step(absent.state(), f.environment(), Optional.empty(), context);
        assertEquals(CdPlayerBody.Boundary.SLOT_ABSENT, none.boundary()); assertTrue(none.effects().isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {6, 8})
    void deadAndRestartNeverSampleSuppliedTouchInput(int routine) {
        Fixture f = new Fixture(); f.slots = CdPlayerBody.DynamicSlots.UNIMPLEMENTED;
        CdObjectPool pool = new CdObjectPool(); touchSlot(pool, 32, 0x40, 64, 0x810); // A scanned selector 0 would reject.
        var touch = Optional.of(responseTouch(pool, new Contact()));
        Actor a = deadActor(); a.routine = routine; a.y = 0x0811 << 16; a.reset = 2;
        var level = Optional.of(new LevelCut().context());
        var with = f.owner().step(a.state(), f.environment(), touch, level);
        assertEquals(f.owner().step(a.state(), f.environment(), Optional.empty(), level), with);
        assertFalse(with.effects().stream().anyMatch(e -> e instanceof CdPlayerBody.TouchScanCompleted
                || e instanceof CdPlayerBody.ObjectTouchResult || e instanceof CdPlayerBody.ObjectTouchResponse));
    }

    @Test void levelContextAndRuntimeLevelValuesNeverBecomeBodyStateEnvironmentOrResultComponents() {
        for (var type : List.of(CdPlayerBody.State.class, CdPlayerBody.Environment.class, CdPlayerBody.Result.class)) {
            for (var component : type.getRecordComponents()) {
                String name = component.getGenericType().getTypeName();
                for (String forbidden : List.of("LevelContext", "CdLevelGlobals", "CdRingGlobals", "Snapshot")) {
                    assertFalse(name.contains(forbidden), type.getSimpleName() + "." + component.getName() + " " + name);
                }
            }
        }
    }

    @Test void resultAdmitsADrawnDeadOnlyWithAQueuedPoseAndARestartOnlyWithoutDisplayOrMode() {
        Fixture f = new Fixture(); var dead = stepLevel(f, deadActor(), new LevelCut());
        var pose = dead.display().orElseThrow();
        var hidden = new CdPlayerBody.DisplayPublication(false, pose.mappingAddress(), pose.tile(), pose.priority(),
                pose.width(), pose.mapFrame(), pose.spriteFlags(), pose.centreX16_16(), pose.centreY16_16());
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Result(dead.state(), dead.environment(),
                dead.effects(), Optional.empty(), CdPlayerBody.Boundary.DEAD_RETURN, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Result(dead.state(), dead.environment(),
                dead.effects(), Optional.of(hidden), CdPlayerBody.Boundary.DEAD_RETURN, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Result(dead.state(), dead.environment(),
                dead.effects(), dead.display(), CdPlayerBody.Boundary.DEAD_RETURN, Optional.of(CdPlayerMovement.Boundary.MODE_RETURN)));
        assertEquals(dead, new CdPlayerBody.Result(dead.state(), dead.environment(), dead.effects(), dead.display(),
                CdPlayerBody.Boundary.DEAD_RETURN, Optional.empty()));
        Actor a = deadActor(); a.routine = 8; a.reset = 2; var restart = stepLevel(f, a, new LevelCut());
        assertTrue(restart.display().isEmpty()); assertTrue(restart.modeBoundary().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Result(restart.state(), restart.environment(),
                restart.effects(), Optional.of(pose), CdPlayerBody.Boundary.RESTART_RETURN, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Result(restart.state(), restart.environment(),
                restart.effects(), Optional.empty(), CdPlayerBody.Boundary.RESTART_RETURN,
                Optional.of(CdPlayerMovement.Boundary.MODE_RETURN)));
        assertEquals(restart, new CdPlayerBody.Result(restart.state(), restart.environment(), restart.effects(),
                Optional.empty(), CdPlayerBody.Boundary.RESTART_RETURN, Optional.empty()));
    }

    @Test void newDeathAndRestartEffectsValidateNativeWidthsAndTheGameOverImageShape() {
        assertThrows(NullPointerException.class, () -> new CdPlayerBody.LevelWrite(null, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.LIVES, 256));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.LEVEL_RESTART, 0x10000));
        assertEquals(-1, new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.FLOWER_COUNT_LONG, -1).value(), "a whole longword");
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Z80RamWrite(0x2000, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Z80RamWrite(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Z80RamWrite(0x1C3E, 256));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.UncheckedAliasWrite(0x1000000, 0x3B));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.UncheckedAliasWrite(0xFFF000, 256));
        assertThrows(NullPointerException.class, () -> new CdPlayerBody.RestartCut(null, true));
        var slot = new CdObjectPool().state(40); var image = slot.moveByte(CdObjectState.ByteField.ID, 0x3B);
        var found = Optional.of(new CdPlayerTouchResponse.FindObjSlot(OptionalInt.of(40)));
        assertEquals(Optional.of(image), new CdPlayerBody.DeadCheckGone(2, 1, 0, found, Optional.of(image)).gameOverImage());
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.DeadCheckGone(2, 1, 0,
                Optional.of(new CdPlayerTouchResponse.FindObjSlot(OptionalInt.of(41))), Optional.of(image)));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.DeadCheckGone(2, 1, 0, found, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.DeadCheckGone(2, 1, 0,
                Optional.of(new CdPlayerTouchResponse.FindObjSlot(OptionalInt.empty())), Optional.of(image)));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.DeadCheckGone(2, 1, 0, Optional.empty(), Optional.of(image)));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.DeadCheckGone(2, 1, 0, found,
                Optional.of(slot.moveByte(CdObjectState.ByteField.ID, 0x3C))));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.DeadCheckGone(256, 1, 0, Optional.empty(), Optional.empty()));
        assertThrows(NullPointerException.class, () -> new CdPlayerBody.DeadCheckGone(0, 0, 0, null, Optional.empty()));
        assertThrows(NullPointerException.class, () -> new CdPlayerBody.DeadCheckGone(0, 0, 0, Optional.empty(), null));
    }

    // U5-B2 routine-8 ObjSonic_Restart through the real Body call path. Written before production.
    // Source pin 1ecc5f3a: Sonic/Main.asm:2453-2494; Object Spawner.asm:11-20 (ResetSavedObjFlags, not executed here);
    // Functions (General).asm:234-251 (StopZ80/StartZ80) and Sub CPU.asm:21-42 (SubCPUCmd) are typed observations only.

    @Test void zeroResetWordIsNeverCountedYetTheHeaderPredispatchStillRuns() {
        Fixture f = new Fixture(); Actor a = deadActor(); a.routine = 8; a.reset = 0; a.charge = 29;
        var state = a.state(); var r = stepLevel(f, a, new LevelCut());
        assertEquals(CdPlayerBody.Boundary.RESTART_RETURN, r.boundary());
        assertTrue(r.display().isEmpty()); assertTrue(r.modeBoundary().isEmpty());
        // ObjSonic .NormalMode (75-91) still adds 29+1 before .RunRoutines; TST.W/BEQ.W .End then writes nothing (2454-2455).
        assertEquals(List.of(new CdPlayerBody.ChargeEffect(new CdPlayerCharge.ActorWrite(CdPlayerCharge.ActorField.CHARGE, 30)),
                new CdPlayerBody.CallReturned(CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH)), r.effects());
        assertEquals(30, r.state().motion().charge()); assertEquals(0, r.state().timers().reset());
        assertEquals(state.history(), r.state().history()); assertEquals(state.animation(), r.state().animation());
        assertEquals(state.header(), r.state().header()); assertEquals(state.motion().centreY16_16(), r.state().motion().centreY16_16());
    }

    @Test void restartCountsItsWordEachCallAndOnlyTheZeroCallPublishesTheNativeOrderOnce() {
        Fixture f = new Fixture(); var owner = f.owner();
        Actor a = deadActor(); a.routine = 8; a.reset = 0x3C; a.yv = 0; a.y = 0x0810C800;
        LevelCut level = new LevelCut(); level.lives = 2; level.hudTime = 0; level.flowers = 0x030201; level.powerup = 8;
        var context = Optional.of(level.context());
        var state = a.state(); var env = f.environment();
        for (int expected = 0x3B; expected >= 1; expected--) {
            var r = owner.step(state, env, Optional.empty(), context);
            assertEquals(CdPlayerBody.Boundary.RESTART_RETURN, r.boundary()); assertTrue(r.display().isEmpty());
            assertEquals(List.of(new CdPlayerBody.CallReturned(CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH),
                    new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.RESET_TIMER, expected)), r.effects());
            assertEquals(expected, r.state().timers().reset());
            state = r.state(); env = r.environment();
        }
        var cut = owner.step(state, env, Optional.empty(), context);
        assertEquals(List.of(new CdPlayerBody.CallReturned(CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH),
                new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.RESET_TIMER, 0),
                new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.LEVEL_RESTART, 1),
                new CdPlayerBody.CallReturned(CdPlayerBody.NativeCall.STOP_Z80),
                new CdPlayerBody.Z80RamWrite(0x1C3E, 1),
                new CdPlayerBody.CallReturned(CdPlayerBody.NativeCall.START_Z80),
                new CdPlayerBody.ResetSavedObjectFlagsRequest(),
                // CLR.L flowerCount spans the three count bytes and fadeEnableDisplay (2466; Main CPU Variables.inc:149-150).
                new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.FLOWER_COUNT_LONG, 0),
                new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.PLC_LOAD_FLAGS, 1),
                new CdPlayerBody.SoundRequest(CdPlayerBody.Sound.SCMD_FADECDA),
                new CdPlayerBody.RestartCut(new CdLevelGlobals(1, 0, 0, 0, 1, 0, 0, 8, 0), true)), cut.effects());
        assertEquals(0, cut.state().timers().reset()); assertEquals(8, cut.state().motion().routine());
        assertTrue(cut.display().isEmpty()); assertEquals(env, cut.environment());
        var after = owner.step(cut.state(), cut.environment(), Optional.empty(), context);
        assertEquals(List.of(new CdPlayerBody.CallReturned(CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH)), after.effects(),
                "a zero word is never counted again");
    }

    @Test void checkpointKeepsTheTitleCardBitAndSelectsCheckpointSpawnWhileLivesRemain() {
        Fixture f = new Fixture(); Actor a = deadActor(); a.routine = 8; a.reset = 1;
        LevelCut level = new LevelCut(); level.lives = 2; level.checkpoint = 2;
        var r = stepLevel(f, a, level);
        // TST.B checkpoint skips the BCLR (2468-2469); lives, present and a checkpoint set spawnMode 1 (2477-2484).
        assertEquals(List.of(new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.LEVEL_RESTART, 1),
                new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.FLOWER_COUNT_LONG, 0),
                new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.SPAWN_MODE, 1)), levelWrites(r));
        assertEquals(List.of(new CdPlayerBody.RestartCut(new CdLevelGlobals(1, 1, 2, 1, 3, 0, 0, 0, 0), true)), restartCuts(r));
        assertEquals(List.of(new CdPlayerBody.SoundRequest(CdPlayerBody.Sound.SCMD_FADECDA)), bodySounds(r));
    }

    @ParameterizedTest @CsvSource({"0,3,1", "2,3,3", "0,1,1"})
    void zeroLivesStillSendsTheFadeAndClearsOnlyTheTitleCardBitWithoutTouchingSpawnMode(int checkpoint, int plc, int finalPlc) {
        Fixture f = new Fixture(); Actor a = deadActor(); a.routine = 8; a.reset = 1;
        LevelCut level = new LevelCut(); level.lives = 0; level.checkpoint = checkpoint; level.plcFlags = plc; level.spawnMode = 5;
        var r = stepLevel(f, a, level);
        var writes = new ArrayList<>(List.of(new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.LEVEL_RESTART, 1),
                new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.FLOWER_COUNT_LONG, 0)));
        // BCLR #1 to memory is a read-modify-write: it stores the byte even when bit 1 is already clear (2472).
        if (checkpoint == 0) writes.add(new CdPlayerBody.LevelWrite(CdPlayerBody.LevelField.PLC_LOAD_FLAGS, finalPlc));
        assertEquals(writes, levelWrites(r));
        // TST.B lives / BEQ.S .SendCmd still reaches BRA.W SubCPUCmd (2477-2478,2490-2491).
        assertEquals(List.of(new CdPlayerBody.SoundRequest(CdPlayerBody.Sound.SCMD_FADECDA)), bodySounds(r));
        assertEquals(new CdLevelGlobals(1, 1, checkpoint, 5, finalPlc, 0, 0, 0, 0), restartCuts(r).getFirst().next());
    }

    @ParameterizedTest @ValueSource(ints = {0, 2, 0x81})
    void nonPresentTimeZoneStopsAtTheR11aSourceFamilyBeforeAnyRestartWork(int timeZone) {
        // The native CMPI.B #1,timeZone branches (2470-2471,2479-2480,2487-2488) are implemented but unreachable here:
        // ObjSonic's admitted R11A family is zoneAct 0 / timeZone 1, and it rejects before dispatch.
        Fixture f = new Fixture(); f.timeZone = timeZone; Actor a = deadActor(); a.routine = 8; a.reset = 1;
        var state = a.state(); var env = f.environment(); var context = Optional.of(new LevelCut().context());
        assertEquals(CdPlayerBody.UnsupportedBoundary.SOURCE_FAMILY, assertThrows(CdPlayerBody.UnsupportedBody.class,
                () -> f.owner().step(state, env, Optional.empty(), context)).boundary());
        assertEquals(1, state.timers().reset());
    }

    /** Post-KillPlayer routine-6 actor (PP Player Object Collision.asm:282-292): air bit, oAnim $18, oTile bit 15, priority 0. */

    // Support context is a per-invocation borrow. Native pin 1ecc5f3a:
    // Sonic/Main.asm:285-351,675-776,1779-1861,2351-2366,2397-2404,3112-3157;
    // Object Functions.asm:42-82,91-128. Constructed fixtures, not a retail route.
    @ParameterizedTest @CsvSource({"0,0,95420416", "4,-12,95417344", "8,12,95423488"})
    void supportedFloatMainUsesNormalInputsAndRunsTheUnchangedTail(int held, int speed, int x) {
        Fixture f = new Fixture(); f.rawHeld = held; Actor a = supportActor(); var pool = supportPool(0x12, 0);
        var before = pool.capture(); var objects = supportContext(before, a);
        var r = f.owner().step(a.state(), f.environment(), Optional.empty(), Optional.empty(), objects);
        assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, r.boundary());
        assertEquals(x, r.state().motion().centreX16_16()); assertEquals(speed, r.state().motion().xVelocity());
        assertEquals(speed, r.state().motion().groundVelocity());
        assertEquals(0x01ED0000, r.state().motion().centreY16_16());
        assertEquals(8, r.state().motion().flags() & 8); assertEquals(a.objectLinks, r.state().objectLinks());
        assertEquals(0, r.state().motion().previousPrimaryAngle());
        assertEquals(0, r.state().motion().previousSecondaryAngle());
        assertEquals(4, r.state().history().index()); assertEquals(List.of(new CdPlayerBody.TouchScanCompleted(96)), touchVisits(r));
        assertEquals(1, calls(r).stream().filter(c -> c == CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH).count());
        assertEquals(1, calls(r).stream().filter(c -> c == CdPlayerBody.NativeCall.ANIMATE).count());
        assertTrue(indexCall(r, CdPlayerBody.NativeCall.MODE) < indexCall(r, CdPlayerBody.NativeCall.DISPLAY));
        assertEquals(before.slots(), pool.capture().slots()); assertTrue(modePatches(r).isEmpty());
    }

    @ParameterizedTest @CsvSource({"16,-1608,32210944", "0,-968,32374784"})
    void supportedFloatJumpKeepsLinksAndNextHeldOrReleasedActionNeedsNoSupportRead(int held, int velocity, int y) {
        Fixture f = new Fixture(); f.rawHeld = f.rawTap = 0x10; Actor a = supportActor();
        var pool = supportPool(0x12, 0); var snapshot = pool.capture(); var owner = f.owner();
        var jumped = owner.step(a.state(), f.environment(), Optional.empty(), Optional.empty(), supportContext(snapshot, a));
        assertEquals(CdPlayerMovement.Boundary.JUMP_MODE_RETURN, jumped.modeBoundary().orElseThrow());
        assertEquals(-0x680, jumped.state().motion().yVelocity());
        assertEquals(0x01F20000, jumped.state().motion().centreY16_16());
        assertEquals(14, jumped.state().motion().flags()); assertEquals(a.objectLinks, jumped.state().objectLinks());
        assertEquals(List.of(new CdPlayerBody.ModeEffect(new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_JUMP))),
                jumped.effects().stream().filter(CdPlayerBody.ModeEffect.class::isInstance).toList());
        var e = jumped.environment(); var controls = new CdPlayerBody.Controls(held, 0, 0, 0, 0);
        var nextEnv = new CdPlayerBody.Environment(e.movement(), controls, e.globals(), e.dynamicSlots());
        var next = owner.step(jumped.state(), nextEnv); // Bit3/link32 survive; air MODE has no stood-slot read.
        assertEquals(velocity, next.state().motion().yVelocity()); assertEquals(y, next.state().motion().centreY16_16());
        assertEquals(a.objectLinks, next.state().objectLinks()); assertEquals(8, next.state().motion().flags() & 8);
        assertEquals(8, next.state().history().index()); assertEquals(snapshot.slots(), pool.capture().slots());
    }

    @Test void supportIsRequiredOnlyAtTheActualMainReadAndNeverForInitAbsentOrAir() {
        Fixture f = new Fixture(); Actor a = supportActor(); var state = a.state(); var environment = f.environment();
        var balance = assertThrows(CdPlayerMovement.UnsupportedMotion.class, () -> f.owner().step(state, environment));
        assertEquals(CdPlayerMovement.UnsupportedBoundary.OBJECT_SUPPORT, balance.boundary());
        assertTrue(balance.getMessage().contains("CheckBalance"));
        f.rawHeld = f.rawTap = 0x10;
        var jump = assertThrows(CdPlayerMovement.UnsupportedMotion.class, () -> f.owner().step(state, f.environment()));
        assertTrue(jump.getMessage().contains("ChkFlipper"));
        f.rawHeld = 8; f.rawTap = 0; a.gv = 0x200;
        var move = assertThrows(CdPlayerMovement.UnsupportedMotion.class, () -> f.owner().step(a.state(), f.environment()));
        assertTrue(move.getMessage().contains("ObjMove"));
        f.rawHeld = 0; a.routine = 0;
        assertEquals(CdPlayerBody.Boundary.INIT_RETURN, f.owner().step(a.state(), environment).boundary());
        a.routine = 2; a.flags = 10; a.yv = -0x100;
        assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, f.owner().step(a.state(), environment).boundary());
        a.header = new CdPlayerBody.Header(0, 0, 0, 0, 0);
        assertEquals(CdPlayerBody.Boundary.SLOT_ABSENT, f.owner().step(a.state(), environment).boundary());
        assertEquals(0, state.history().index()); assertEquals(0x01ED0000, state.motion().centreY16_16());
    }

    @ParameterizedTest @CsvSource({"18,0,false,95523380,-464", "30,0,false,95457844,-464",
            "30,1,false,95588916,-464", "18,0,true,95523380,-496"})
    void hurtUsesSharedObjMoveAndThenItsOwnDryOrWaterGravity(int id, int flags, boolean water, int x, int yVelocity) {
        Fixture f = new Fixture(); Actor a = hurtActor(); a.flags = water ? 0x4A : 0x0A;
        a.objectLinks = new CdPlayerBody.ObjectLinks(127, 32);
        a.x = 0x05B01234; a.y = 0x01EDABCD; a.xv = 0x180; a.yv = -0x200;
        var pool = supportPool(id, flags); var snapshot = pool.capture();
        var r = f.owner().step(a.state(), f.environment(), Optional.empty(), Optional.empty(), supportContext(snapshot, a));
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, r.boundary()); assertTrue(r.modeBoundary().isEmpty());
        assertEquals(x, r.state().motion().centreX16_16()); assertEquals(0x01EBABCD, r.state().motion().centreY16_16());
        assertEquals(0x180, r.state().motion().xVelocity(), "Flipper resistance changes only ObjMove's local word");
        assertEquals(yVelocity, r.state().motion().yVelocity()); assertEquals(a.objectLinks, r.state().objectLinks());
        var writes = actorWrites(r);
        assertEquals(new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.CENTRE_X_LONG, x), writes.get(0));
        assertEquals(new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.CENTRE_Y_LONG, 0x01EBABCD), writes.get(1));
        assertEquals(new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.Y_VELOCITY, -0x1D0), writes.get(2));
        if (water) assertEquals(new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.Y_VELOCITY, -0x1F0), writes.get(3));
        assertEquals(1, calls(r).stream().filter(c -> c == CdPlayerBody.NativeCall.OBJ_MOVE).count());
        assertTrue(touchVisits(r).isEmpty()); assertTrue(modePatches(r).isEmpty()); assertEquals(snapshot.slots(), pool.capture().slots());
    }

    @ParameterizedTest @CsvSource({"1,RESERVED_OBJECT_SLOT", "31,RESERVED_OBJECT_SLOT",
            "128,OBJECT_ADDRESS", "255,OBJECT_ADDRESS"})
    void hurtPropagatesReservedAndOutsideAddressesOnlyAtObjMove(int link, CdPlayerMovement.UnsupportedBoundary boundary) {
        Fixture f = new Fixture(); Actor a = hurtActor(); a.flags |= 8; a.objectLinks = new CdPlayerBody.ObjectLinks(0, link);
        var snapshot = new CdObjectPool().capture(); var state = a.state();
        var failure = assertThrows(CdPlayerMovement.UnsupportedMotion.class, () -> f.owner().step(state, f.environment(),
                Optional.empty(), Optional.empty(), supportContext(snapshot, a)));
        assertEquals(boundary, failure.boundary()); assertTrue(failure.getMessage().contains("ObjMove"));
        assertEquals(0, state.history().index());
    }

    @ParameterizedTest @CsvSource({"-3072,2,-3072,66322432", "-2048,2,-1992,66584576",
            "4095,0,4096,68157184", "4608,8,4096,68288512"})
    void deadUsesSharedGravityWithoutReadingTheSuppliedReservedSupport(int oldY, int control, int nextY, int position) {
        Fixture f = new Fixture(); Actor a = deadActor(); a.y = 0x04000000; a.yv = oldY; a.flags |= 8; a.ctrl = control;
        a.objectLinks = new CdPlayerBody.ObjectLinks(255, 255); var level = new LevelCut();
        var r = f.owner().step(a.state(), f.environment(), Optional.empty(), Optional.of(level.context()),
                supportContext(level.pool.capture(), a));
        assertEquals(nextY, r.state().motion().yVelocity()); assertEquals(position, r.state().motion().centreY16_16());
        assertEquals(a.objectLinks, r.state().objectLinks());
        assertEquals(1, calls(r).stream().filter(c -> c == CdPlayerBody.NativeCall.OBJ_MOVE_GRAVITY).count());
        assertTrue(touchVisits(r).isEmpty()); assertTrue(modePatches(r).isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {0, 4})
    void killResumeForGroundAndRollReusesSupportWithoutReplayingThePrefix(int mode) {
        Fixture f = new Fixture(); f.rawHeld = 8; f.debugMode = 1;
        Actor a = supportActor(); a.x = 64 << 16; a.y = 200 << 16; a.flags = mode | 8;
        a.anim = mode == 4 ? 2 : 0; a.gv = 0x200; a.xv = 0x123; a.lock = 5;
        var pool = supportPool(0x1E, 1); var objects = supportContext(pool.capture(), a); var owner = f.owner();
        var ordinary = owner.step(a.state(), f.environment(), Optional.empty(), Optional.empty(), objects);
        f.bottom = 0xFF20;
        var r = owner.step(a.state(), f.environment(), Optional.empty(), Optional.empty(), objects);
        assertEquals(ordinary.state(), r.state());
        assertEquals(CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE, kills(r).getFirst().exit());
        assertEquals(1, kills(r).size()); assertEquals(1, r.effects().stream().filter(KILL_MARKER::equals).count());
        assertEquals(1, calls(r).stream().filter(c -> c == CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH).count());
        assertEquals(1, calls(r).stream().filter(c -> c == CdPlayerBody.NativeCall.EXTENDED_CAMERA).count());
        assertEquals(1, calls(r).stream().filter(c -> c == CdPlayerBody.NativeCall.MODE).count());
        assertEquals(new CdPlayerBody.KillPlayerCall(kills(r).getFirst()), r.effects().get(r.effects().indexOf(KILL_MARKER) + 1));
        assertTrue(indexCall(r, CdPlayerBody.NativeCall.KILL_PLAYER) < indexCall(r, CdPlayerBody.NativeCall.MODE));
        assertEquals(a.objectLinks, r.state().objectLinks());
    }

    @ParameterizedTest @CsvSource({"47,ENEMY", "111,ITEM", "70,MONITOR"})
    void flipperPatchPrecedesActualLaterTouchOfTheSameSlotAndEveryImageKeepsAnimationOne(
            int collisionType, CdPlayerObjectTouch.Kind kind) {
        Fixture f = new Fixture(); f.rawHeld = f.rawTap = 0x10;
        Actor a = supportActor(); var pool = supportPool(0x1E, 0);
        pool.write(pool.state(32).moveByte(CdObjectState.ByteField.SPRITE_FLAGS, 0x80)
                .moveByte(CdObjectState.ByteField.COLLISION_TYPE, collisionType));
        var snapshot = pool.capture(); var before = pool.state(32); var contact = new Contact();
        var touch = new CdPlayerBody.TouchContext(touchScanner(), snapshot, contact.context());
        var r = f.owner().step(a.state(), f.environment(), Optional.of(touch), Optional.empty(), supportContext(snapshot, a));
        var patch = modePatches(r).getFirst().patch();
        assertSame(before, patch.before()); assertEquals(before.moveByte(CdObjectState.ByteField.ANIMATION, 1), patch.after());
        assertEquals(1, modePatches(r).size());
        assertEquals(List.of(new CdPlayerBody.ModeEffect(new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_SPRING)),
                new CdPlayerBody.ModeEffect(new CdPlayerMovement.ObjectPatch(patch)),
                new CdPlayerBody.ModeEffect(new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_JUMP))),
                r.effects().stream().filter(CdPlayerBody.ModeEffect.class::isInstance).toList());
        int later;
        if (kind == CdPlayerObjectTouch.Kind.ENEMY) {
            var response = response(r); later = indexOf(r, CdPlayerBody.ObjectTouchResponse.class);
            assertEquals(CdPlayerTouchResponse.Exit.KILL_ENEMY, response.exit());
            assertEquals(1, response.changedSlots().size()); var changed = response.changedSlots().getFirst();
            assertEquals(32, changed.identity().wholeIndex()); assertEquals(0x18, changed.id()); assertEquals(0, changed.routine());
            assertEquals(1, changed.unsignedByte(CdObjectState.ByteField.ANIMATION));
            assertEquals(1, changed.subtype());
        } else if (kind == CdPlayerObjectTouch.Kind.ITEM) {
            var item = objectTouch(r); later = indexOf(r, CdPlayerBody.ObjectTouchResult.class);
            assertEquals(CdPlayerObjectTouch.Kind.ITEM, item.kind());
            assertEquals(patch.after(), item.selectedSlot().orElseThrow());
            assertEquals(1, item.selectedSlot().orElseThrow().unsignedByte(CdObjectState.ByteField.ANIMATION));
            assertEquals(1, item.changedSlot().orElseThrow().unsignedByte(CdObjectState.ByteField.ANIMATION));
            assertEquals(4, item.changedSlot().orElseThrow().routine());
        } else {
            // Player_TouchMonitor: player Y $1F2-$10 < object Y $20C; the selection still ends the scan.
            later = indexOf(r, CdPlayerBody.ObjectTouchResponse.class);
            assertEquals(CdPlayerTouchResponse.Exit.MONITOR_UPWARD_NO_WRITE, response(r).exit());
            assertTrue(response(r).changedSlots().isEmpty()); assertTrue(response(r).calls().isEmpty());
            assertEquals(patch.after().moveByte(CdObjectState.ByteField.ANIMATION, 1), patch.after());
        }
        assertTrue(r.effects().indexOf(new CdPlayerBody.ModeEffect(new CdPlayerMovement.ObjectPatch(patch))) < later);
        assertTrue(indexCall(r, CdPlayerBody.NativeCall.ANIMATE) < later);
        assertEquals(List.of(new CdPlayerBody.TouchScanCompleted(1)), touchVisits(r));
        assertEquals(0, before.unsignedByte(CdObjectState.ByteField.ANIMATION));
        assertSame(before, pool.state(32)); assertEquals(snapshot.slots(), pool.capture().slots());
    }

    @Test void flipperOverlayRequiresTheIdenticalInitialSnapshotOnlyWhenAPatchReachesTouch() {
        Fixture f = new Fixture(); f.rawHeld = f.rawTap = 0x10; Actor a = supportActor(); var pool = supportPool(0x1E, 0);
        var original = pool.capture(); var other = pool.capture();
        assertNotSame(original, other); assertSame(original.slots().getFirst(), other.slots().getFirst());
        var touch = new CdPlayerBody.TouchContext(touchScanner(), other);
        assertThrows(IllegalArgumentException.class, () -> f.owner().step(a.state(), f.environment(), Optional.of(touch),
                Optional.empty(), supportContext(original, a)));
        pool.write(pool.state(32).moveByte(CdObjectState.ByteField.ID, 0x12));
        var noPatchOriginal = pool.capture(); var noPatchOther = pool.capture();
        var r = f.owner().step(a.state(), f.environment(), Optional.of(new CdPlayerBody.TouchContext(touchScanner(), noPatchOther)),
                Optional.empty(), supportContext(noPatchOriginal, a));
        assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, r.boundary()); assertTrue(modePatches(r).isEmpty());
        assertEquals(0, pool.state(32).unsignedByte(CdObjectState.ByteField.ANIMATION));
    }

    @Test void flipperWithoutTouchPublishesOnlyItsPatchAndNeverInventsAScanner() {
        Fixture f = new Fixture(); f.rawHeld = f.rawTap = 0x10; f.slots = CdPlayerBody.DynamicSlots.UNIMPLEMENTED;
        Actor a = supportActor(); var pool = supportPool(0x1E, 0); var snapshot = pool.capture();
        var r = f.owner().step(a.state(), f.environment(), Optional.empty(), Optional.empty(), supportContext(snapshot, a));
        assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, r.boundary()); assertEquals(1, modePatches(r).size());
        assertTrue(touchVisits(r).isEmpty());
        assertFalse(r.effects().stream().anyMatch(e -> e instanceof CdPlayerBody.ObjectTouchResult || e instanceof CdPlayerBody.ObjectTouchResponse));
        assertEquals(snapshot.slots(), pool.capture().slots());
    }

    @Test void negativeControlSkipsEvenAMismatchedMalformedTouchAfterTheFlipperPatch() {
        Fixture f = new Fixture(); f.rawHeld = f.rawTap = 0x10; Actor a = supportActor(); a.ctrl = 0x80;
        var pool = supportPool(0x1E, 0); var original = pool.capture();
        var foreign = new CdObjectPool(); touchSlot(foreign, 32, 0x40, 0x5B0, 0x1ED);
        var r = f.owner().step(a.state(), f.environment(), Optional.of(touchContext(foreign)), Optional.empty(), supportContext(original, a));
        assertEquals(1, modePatches(r).size()); assertTrue(touchVisits(r).isEmpty());
        assertEquals(original.slots(), pool.capture().slots());
    }

    @Test void lateFailureAfterFlipperAndSameSlotResponsePublishesNothingAndLeavesAllInputsReusable() {
        Fixture f = new Fixture(); f.rawHeld = f.rawTap = 0x10; f.special[2] = 2;
        Actor a = supportActor(); var pool = supportPool(0x1E, 0);
        pool.write(pool.state(32).moveByte(CdObjectState.ByteField.SPRITE_FLAGS, 0x80)
                .moveByte(CdObjectState.ByteField.COLLISION_TYPE, 0x2F));
        var original = pool.capture(); var before = a.state(); var env = f.environment();
        var touch = new CdPlayerBody.TouchContext(touchScanner(), original, new Contact().context());
        var owner = f.owner(); var objects = supportContext(original, a);
        for (int attempt = 0; attempt < 2; attempt++) {
            assertEquals(CdPlayerBody.UnsupportedBoundary.ROLL_TUNNEL_OWNER,
                    assertThrows(CdPlayerBody.UnsupportedBody.class, () -> owner.step(before, env, Optional.of(touch),
                            Optional.empty(), objects)).boundary());
            assertEquals(0, before.history().index()); assertEquals(0, before.motion().playerJump());
            assertEquals(original.slots(), pool.capture().slots());
        }
    }

    @Test void supportContextsAreNotRetainedByBodyRecordsOrOwnerAndOldCutsReplayPurely() {
        Fixture f = new Fixture(); Actor a = supportActor(); var pool = supportPool(0x12, 0); var owner = f.owner();
        var snapshot = pool.capture(); var input = a.state(); var env = f.environment(); var objects = supportContext(snapshot, a);
        var first = owner.step(input, env, Optional.empty(), Optional.empty(), objects);
        pool.write(pool.state(32).moveByte(CdObjectState.ByteField.ID, 0x1E).moveByte(CdObjectState.ByteField.FLAGS, 1));
        var second = owner.step(input, env, Optional.empty(), Optional.empty(), supportContext(pool.capture(), a));
        assertEquals(first.state().motion().centreX16_16() + 0x10000, second.state().motion().centreX16_16());
        assertEquals(first, owner.step(input, env, Optional.empty(), Optional.empty(), objects));
        assertEquals(CdPlayerMovement.UnsupportedBoundary.OBJECT_SUPPORT,
                assertThrows(CdPlayerMovement.UnsupportedMotion.class, () -> owner.step(input, env)).boundary());
        for (var field : CdPlayerBody.class.getDeclaredFields()) {
            assertFalse(field.getGenericType().getTypeName().contains("ObjectContext"));
            assertNotEquals(CdObjectPool.Snapshot.class, field.getType());
        }
        for (var type : List.of(CdPlayerBody.State.class, CdPlayerBody.Environment.class, CdPlayerBody.Result.class)) {
            for (var component : type.getRecordComponents()) {
                assertFalse(component.getGenericType().getTypeName().contains("ObjectContext"));
                assertFalse(component.getGenericType().getTypeName().contains("Snapshot"));
            }
        }
        assertThrows(NullPointerException.class, () -> owner.step(input, env, Optional.empty(), Optional.empty(), null));
    }


    // Monitor composition: native Player_TouchMonitor (:102-125) at ObjSonic_Main (:324-351).
    // Frozen before production; hand literals below are expectations, ALL JAVA UNRUN.
    @Test void ownedMonitorNoopEndsBeforeTheLaterRingAndPublishesOneResponseAndVisitCount() {
        Fixture f = new Fixture(); Actor a = new Actor(); f.slots = CdPlayerBody.DynamicSlots.UNIMPLEMENTED;
        CdObjectPool pool = new CdObjectPool(); var monitor = touchSlot(pool, 33, 0x46, 64, 76);
        var ring = touchSlot(pool, 34, 0x47, 64, 76); touchSlot(pool, 35, 0x40, 64, 76);
        var context = responseTouch(pool, new Contact()); var before = pool.capture();
        var r = f.owner().step(a.state(), f.environment(), Optional.of(context));
        assertEquals(CdPlayerTouchResponse.Exit.MONITOR_DOWNWARD_NO_WRITE, response(r).exit());
        assertTrue(response(r).changedSlots().isEmpty()); assertTrue(response(r).calls().isEmpty());
        assertEquals(OptionalInt.empty(), response(r).d0());
        assertEquals(List.of(new CdPlayerBody.TouchScanCompleted(2)), touchVisits(r));
        assertFalse(r.effects().stream().anyMatch(CdPlayerBody.ObjectTouchResult.class::isInstance));
        assertTrue(indexCall(r, CdPlayerBody.NativeCall.ANIMATE) < indexOf(r, CdPlayerBody.ObjectTouchResponse.class));
        assertTrue(indexOf(r, CdPlayerBody.ObjectTouchResponse.class) < indexOf(r, CdPlayerBody.TouchScanCompleted.class));
        assertTrue(indexOf(r, CdPlayerBody.TouchScanCompleted.class) < indexCall(r, CdPlayerBody.NativeCall.SPECIAL_CHUNKS));
        assertEquals(0, r.state().motion().yVelocity()); assertEquals(5, r.state().motion().animation());
        assertEquals(64 << 16, r.state().motion().centreX16_16()); assertEquals(76 << 16, r.state().motion().centreY16_16());
        assertEquals(before.slots(), pool.capture().slots()); assertSame(monitor, pool.state(33)); assertSame(ring, pool.state(34));
        assertTrue(bodySounds(r).isEmpty());
    }

    @ParameterizedTest @CsvSource({"65,true,1664", "66,false,-1664"})
    void monitorJumpUsesThePostModeCentreAndPostDisplayActorWithoutAnImmunityGate(
            int monitorY, boolean writes, int expectedVelocity) {
        Fixture f = new Fixture(); Actor a = new Actor(); f.rawHeld = f.rawTap = 0x10;
        a.x |= 0x1234; a.y |= 0xABCD; a.hurt = 91; a.invinc = 5; a.reset = 0xBEEF; a.rotationDistance = 0xA5;
        f.invincible = 0x80;
        Contact contact = new Contact(); contact.timeWarp = 0x80; contact.shield = 0x80;
        CdObjectPool pool = new CdObjectPool(); var monitor = touchSlot(pool, 33, 0x46, 64, monitorY);
        touchSlot(pool, 34, 0x47, 64, 81); var before = pool.capture();
        var context = responseTouch(pool, contact); var owner = f.owner(); var input = a.state(); var env = f.environment();
        var r = owner.step(input, env, Optional.of(context)); var result = response(r);
        assertEquals(writes ? CdPlayerTouchResponse.Exit.MONITOR_UPWARD_BOUNCE
                : CdPlayerTouchResponse.Exit.MONITOR_UPWARD_NO_WRITE, result.exit());
        // CheckJump's integer +5 retains $ABCD; no later MODE tail in JUMP_MODE_RETURN. $51-$10 == $41.
        assertEquals(0x00401234, r.state().motion().centreX16_16());
        assertEquals(0x0051ABCD, r.state().motion().centreY16_16());
        assertEquals(expectedVelocity, r.state().motion().yVelocity()); assertEquals(6, r.state().motion().flags());
        assertEquals(2, r.state().motion().animation()); assertEquals(14, r.state().motion().yRadius());
        assertEquals(new CdPlayerBody.Timers(90, 4, 0, 0xBEEF), r.state().timers());
        assertEquals(90, result.actor().hurt()); assertEquals(0xA5, result.actor().rotationDistance());
        assertEquals(0x80, r.environment().globals().invincible());
        assertEquals(contact.context().world(), result.world());
        assertEquals(contact.context().contact(), responseEffect(r).contact());
        assertEquals(9, result.player().scoreChain()); assertTrue(result.calls().isEmpty());
        assertEquals(OptionalInt.empty(), result.d0()); assertEquals(input.header(), r.state().header());
        assertEquals(81, onlyCut(r).centreYWord()); assertEquals(81, r.state().history().bytes().unsignedWord(2));
        assertEquals(List.of(new CdPlayerBody.TouchScanCompleted(2)), touchVisits(r));
        if (writes) {
            byte[] bytes = monitor.copyBytes(); bytes[0x12] = (byte) 0xFE; bytes[0x13] = (byte) 0x80; bytes[0x25] = 4;
            assertEquals(1, result.changedSlots().size()); assertArrayEquals(bytes, result.changedSlots().getFirst().copyBytes());
            assertEquals(monitor.identity(), result.changedSlots().getFirst().identity());
        } else assertTrue(result.changedSlots().isEmpty());
        assertEquals(before.slots(), pool.capture().slots()); assertEquals(r, owner.step(input, env, Optional.of(context)));
        assertEquals(result.actor().motion(), r.state().motion(), "the final actor is adopted once");
    }

    @ParameterizedTest @CsvSource({"2,true,-56", "0,false,56"})
    void monitorReadsTheActualAnimatorAnimationWriteRatherThanTheIncomingPose(int animation, boolean writes, int velocity) {
        Fixture f = new Fixture(); Actor a = new Actor(); a.flags = 2; a.y = 200 << 16; a.anim = a.prev = 5;
        a.cursor = new CdPlayerBody.AnimationCursor(1, 0, 4); f.wind = 1;
        f.animationCommand = 0xFD; f.animationOperand = animation;
        CdObjectPool pool = new CdObjectPool(); var monitor = touchSlot(pool, 32, 0x46, 64, 200);
        var r = f.owner().step(a.state(), f.environment(), Optional.of(responseTouch(pool, new Contact())));
        assertEquals(animation, r.state().motion().animation()); assertEquals(5, r.state().motion().previousAnimation());
        assertEquals(2, r.state().motion().flags(), "animation 2 is sufficient without rolling status bit2");
        assertEquals(200 << 16, r.state().motion().centreY16_16()); assertEquals(velocity, r.state().motion().yVelocity());
        assertEquals(new CdPlayerBody.AnimationCursor(1, 3, 4), r.state().animation());
        assertEquals(writes ? CdPlayerTouchResponse.Exit.MONITOR_DOWNWARD_BOUNCE
                : CdPlayerTouchResponse.Exit.MONITOR_DOWNWARD_NO_WRITE, response(r).exit());
        if (writes) {
            byte[] bytes = monitor.copyBytes(); bytes[0x24] = 4;
            assertEquals(1, response(r).changedSlots().size()); assertArrayEquals(bytes, response(r).changedSlots().getFirst().copyBytes());
        } else assertTrue(response(r).changedSlots().isEmpty());
        assertEquals(1, r.effects().stream().filter(CdPlayerBody.AnimationPublication.class::isInstance).count());
        assertEquals(List.of(new CdPlayerBody.TouchScanCompleted(1)), touchVisits(r)); assertSame(monitor, pool.state(32));
    }

    @Test void monitorSelectionUsesTheCurrentDuckMappingAndThenTheLaterRingOnlyWhenTheMonitorMisses() {
        Fixture f = new Fixture(); Actor a = new Actor(); CdObjectPool pool = new CdObjectPool();
        var monitor = touchSlot(pool, 32, 0x46, 64, 44); var ring = touchSlot(pool, 33, 0x47, 64, 76);
        var context = responseTouch(pool, new Contact());
        var normal = f.owner().step(a.state(), f.environment(), Optional.of(context));
        assertEquals(CdPlayerTouchResponse.Exit.MONITOR_DOWNWARD_NO_WRITE, response(normal).exit());
        assertEquals(List.of(new CdPlayerBody.TouchScanCompleted(1)), touchVisits(normal));
        f.animationFirstFrame = 0x39;
        var duck = f.owner().step(a.state(), f.environment(), Optional.of(context));
        assertEquals(0x39, duck.state().animation().mapFrame());
        assertFalse(duck.effects().stream().anyMatch(CdPlayerBody.ObjectTouchResponse.class::isInstance));
        assertEquals(CdPlayerObjectTouch.Kind.ITEM, objectTouch(duck).kind()); assertSame(ring, objectTouch(duck).selectedSlot().orElseThrow());
        assertEquals(List.of(new CdPlayerBody.TouchScanCompleted(2)), touchVisits(duck)); assertSame(monitor, pool.state(32));
    }

    @Test void monitorResponseThenLateSpecialChunksFailurePublishesNoDetachedActorSlotsOrSounds() {
        Fixture f = new Fixture(); Actor a = new Actor(); f.rawHeld = f.rawTap = 0x10;
        CdObjectPool pool = new CdObjectPool(); touchSlot(pool, 33, 0x46, 64, 65);
        var context = responseTouch(pool, new Contact()); var before = pool.capture(); var input = a.state(); var env = f.environment();
        var safe = f.owner(); var control = safe.step(input, env, Optional.of(context));
        assertEquals(CdPlayerTouchResponse.Exit.MONITOR_UPWARD_BOUNCE, response(control).exit());
        assertEquals(1, response(control).changedSlots().size());
        f.special[3] = 2; var failing = f.owner(); List<CdPlayerBody.Result> publications = new ArrayList<>();
        var failure = assertThrows(CdPlayerBody.UnsupportedBody.class,
                () -> publications.add(failing.step(input, env, Optional.of(context))));
        assertEquals(CdPlayerBody.UnsupportedBoundary.ROLL_TUNNEL_OWNER, failure.boundary()); assertTrue(publications.isEmpty());
        assertEquals(before.slots(), pool.capture().slots()); assertEquals(0, input.history().index()); assertEquals(0, input.motion().yVelocity());
        assertEquals(control, safe.step(input, env, Optional.of(context)));
    }

    @Test void monitorReplayBorrowsTheSavedPoolCutWithoutRetainingOrCommittingIt() {
        Fixture f = new Fixture(); Actor a = new Actor(); f.rawHeld = f.rawTap = 0x10;
        CdObjectPool pool = new CdObjectPool(); touchSlot(pool, 33, 0x46, 64, 65);
        var context = responseTouch(pool, new Contact()); var input = a.state(); var env = f.environment(); var owner = f.owner();
        var first = owner.step(input, env, Optional.of(context)); var saved = context.snapshot();
        pool.deleteObject(33);
        assertEquals(first, owner.step(input, env, Optional.of(context)));
        pool.restore(saved);
        assertEquals(first, owner.step(input, env, Optional.of(responseTouch(pool, new Contact()))));
        assertEquals(saved.slots(), pool.capture().slots()); assertEquals(2, pool.state(33).routine());
        assertEquals(0, pool.state(33).signedWord(CdObjectState.WordField.Y_VELOCITY));
        assertEquals(0, input.history().index()); assertEquals(0, input.animation().mapFrame());
    }

    @Test void routineFourNeverSamplesAnOwnedMonitorContextOrPublishesATouchEffect() {
        Fixture f = new Fixture(); Actor a = hurtActor(); a.y = 200 << 16; a.xv = -0x200; a.yv = -0x400;
        CdObjectPool pool = new CdObjectPool(); touchSlot(pool, 33, 0x46, 62, 196);
        var before = pool.capture(); var input = a.state(); var env = f.environment(); var owner = f.owner();
        var result = owner.step(input, env, Optional.of(responseTouch(pool, new Contact())));
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, result.boundary());
        assertEquals(196 << 16, result.state().motion().centreY16_16()); assertEquals(-0x3D0, result.state().motion().yVelocity());
        assertEquals(owner.step(input, env), result); assertTrue(touchVisits(result).isEmpty());
        assertFalse(result.effects().stream().anyMatch(CdPlayerBody.ObjectTouchResponse.class::isInstance));
        assertEquals(before.slots(), pool.capture().slots());
    }

    private static Actor supportActor() {
        Actor a = new Actor(); a.x = 0x05B00000; a.y = 0x01ED0000; a.flags = 8;
        a.objectLinks = new CdPlayerBody.ObjectLinks(127, 32); return a;
    }
    private static CdObjectPool supportPool(int id, int flags) {
        var pool = new CdObjectPool();
        pool.write(pool.state(32).moveByte(CdObjectState.ByteField.ID, id).moveByte(CdObjectState.ByteField.FLAGS, flags)
                .moveByte(CdObjectState.ByteField.WIDTH, 12).moveByte(CdObjectState.ByteField.ROUTINE, 2)
                .moveWord(CdObjectState.WordField.X, 0x5B0).moveWord(CdObjectState.WordField.Y, 0x20C));
        return pool;
    }
    private static Optional<CdPlayerMovement.ObjectContext> supportContext(CdObjectPool.Snapshot snapshot, Actor actor) {
        return Optional.of(new CdPlayerMovement.ObjectContext(snapshot, actor.objectLinks.standObject(),
                actor.header.slotId(), actor.header.width()));
    }
    private static List<CdPlayerMovement.ObjectPatch> modePatches(CdPlayerBody.Result result) {
        return result.effects().stream().filter(CdPlayerBody.ModeEffect.class::isInstance)
                .map(CdPlayerBody.ModeEffect.class::cast).map(CdPlayerBody.ModeEffect::effect)
                .filter(CdPlayerMovement.ObjectPatch.class::isInstance).map(CdPlayerMovement.ObjectPatch.class::cast).toList();
    }

    private static Actor deadActor() {
        Actor a = new Actor(); a.routine = 6; a.flags = 2; a.anim = 0x18; a.yv = -0x700; a.stick = 7; a.rotationDistance = 0xF1;
        a.header = new CdPlayerBody.Header(1, 0x21F200, 0x8780, 0, 24);
        return a;
    }
    private static CdPlayerBody.Result stepLevel(Fixture f, Actor a, LevelCut level) {
        return f.owner().step(a.state(), f.environment(), Optional.empty(), Optional.of(level.context()));
    }
    private static List<CdPlayerBody.NativeCall> killedMainCalls() {
        return List.of(CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH, CdPlayerBody.NativeCall.EXTENDED_CAMERA,
                CdPlayerBody.NativeCall.WATERFALL_SPLASH_CHECK, CdPlayerBody.NativeCall.KILL_PLAYER, CdPlayerBody.NativeCall.MODE,
                CdPlayerBody.NativeCall.PP_SPECIAL_COLLISION, CdPlayerBody.NativeCall.DISPLAY,
                CdPlayerBody.NativeCall.RECORD_POSITION, CdPlayerBody.NativeCall.PP_WATER, CdPlayerBody.NativeCall.ANIMATE,
                CdPlayerBody.NativeCall.SPECIAL_CHUNKS);
    }
    private static List<CdPlayerBody.LevelWrite> levelWrites(CdPlayerBody.Result r) {
        return r.effects().stream().filter(CdPlayerBody.LevelWrite.class::isInstance).map(CdPlayerBody.LevelWrite.class::cast).toList();
    }
    private static List<CdPlayerBody.DeadCheckGone> gone(CdPlayerBody.Result r) {
        return r.effects().stream().filter(CdPlayerBody.DeadCheckGone.class::isInstance).map(CdPlayerBody.DeadCheckGone.class::cast).toList();
    }
    private static List<CdPlayerBody.RestartCut> restartCuts(CdPlayerBody.Result r) {
        return r.effects().stream().filter(CdPlayerBody.RestartCut.class::isInstance).map(CdPlayerBody.RestartCut.class::cast).toList();
    }
    private static List<Integer> resetWrites(CdPlayerBody.Result r) {
        return actorWrites(r).stream().filter(w -> w.field() == CdPlayerBody.ActorField.RESET_TIMER)
                .map(CdPlayerBody.ActorWrite::value).toList();
    }
    private static final CdPlayerBody.ModeEffect KILL_MARKER = new CdPlayerBody.ModeEffect(new CdPlayerMovement.KillRequest());
    private static final CdPlayerTouchResponse.ResetOnFloor RESET = new CdPlayerTouchResponse.ResetOnFloor();
    private static final CdPlayerTouchResponse.PlayFm HURT_FM = new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_HURT);
    private static final CdPlayerTouchResponse.PlayFm CHARGE_STOP_FM =
            new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP);

    /** Runtime-owned call-cut globals and dynamic pool for routines 6/8; declared per test, never Body state. */
    static final class LevelCut {
        final CdObjectPool pool = new CdObjectPool();
        int rings, lives = 3, livesFlags, hudRings, hudLives, timeStop, ringTimer, ringFrame;
        int levelRestart, hudTime = 1, checkpoint, spawnMode, plcFlags = 3, flowers, fade = 2, powerup, timeOver;
        CdRingGlobals ring() {
            return new CdRingGlobals(rings, lives, livesFlags, hudRings, hudLives, timeStop, ringTimer, ringFrame);
        }
        CdLevelGlobals globals() {
            return new CdLevelGlobals(levelRestart, hudTime, checkpoint, spawnMode, plcFlags, flowers, fade, powerup, timeOver);
        }
        CdPlayerBody.LevelContext context() { return new CdPlayerBody.LevelContext(pool.capture(), ring(), globals()); }
    }

    /** Post-HurtPlayer actor shape (PP Player Object Collision.asm:246-266): air bit, oAnim $1A, hurt $78. */
    private static Actor hurtActor() {
        Actor a = new Actor(); a.routine = 4; a.flags = 2; a.anim = a.prev = 0x1A; a.hurt = 0x78;
        return a;
    }
    private static List<CdPlayerBody.NativeCall> hurtCalls(CdPlayerBody.NativeCall... checks) {
        var calls = new ArrayList<>(List.of(CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH,
                CdPlayerBody.NativeCall.OBJ_MOVE, CdPlayerBody.NativeCall.PP_SPECIAL_COLLISION));
        calls.addAll(List.of(checks));
        calls.addAll(List.of(CdPlayerBody.NativeCall.RECORD_POSITION, CdPlayerBody.NativeCall.ANIMATE,
                CdPlayerBody.NativeCall.DRAW_OBJECT));
        return calls;
    }
    private static List<CdPlayerBody.ActorWrite> actorWrites(CdPlayerBody.Result r) {
        return r.effects().stream().filter(CdPlayerBody.ActorWrite.class::isInstance)
                .map(CdPlayerBody.ActorWrite.class::cast).toList();
    }
    private static List<CdPlayerTouchResponse.KillResult> kills(CdPlayerBody.Result r) {
        return r.effects().stream().filter(CdPlayerBody.KillPlayerCall.class::isInstance)
                .map(CdPlayerBody.KillPlayerCall.class::cast).map(CdPlayerBody.KillPlayerCall::result).toList();
    }
    private static CdPlayerBody.DisplayEligibility onlyCut(CdPlayerBody.Result r) {
        var cuts = r.effects().stream().filter(CdPlayerBody.DisplayEligibility.class::isInstance)
                .map(CdPlayerBody.DisplayEligibility.class::cast).toList();
        assertEquals(1, cuts.size()); return cuts.getFirst();
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
    static CdPlayerObjectTouch touchScanner() {
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
        int xr = 9, yr = 19, anim, prev, jump, charge, lock, stick, ctrl, pri, sec, hurt, invinc, shoes, reset, rotationDistance;
        CdPlayerBody.ObjectLinks objectLinks = new CdPlayerBody.ObjectLinks(0, 0);
        CdPlayerBody.Header header = new CdPlayerBody.Header(1, 0x21F200, 0x780, 2, 24);
        CdPlayerBody.AnimationCursor cursor = new CdPlayerBody.AnimationCursor(0, 0, 0);
        CdPlayerBody.History history = new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[256]), 0);
        CdPlayerBody.State state() {
            return new CdPlayerBody.State(new CdPlayerMotionState(x, y, xv, yv, gv, routine, angle, flags, spr, xr, yr,
                    anim, prev, jump, charge, lock, stick, ctrl, pri, sec), cursor, header,
                    new CdPlayerBody.Timers(hurt, invinc, shoes, reset), history, rotationDistance, objectLinks);
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
        int timeZone = 1;
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
                    new CdPlayerMovement.Gates(0, timeZone, mini, 0, wind, speedShoes, warpDir),
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
