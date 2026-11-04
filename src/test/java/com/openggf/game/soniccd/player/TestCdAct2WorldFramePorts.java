package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdR12aTerrainAssets;
import com.openggf.game.soniccd.level.CdR12aPlayerAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.BeforeAll;
import com.openggf.game.soniccd.objects.CdTitleCard;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Actual detached USA source, explicit dirty component values; no World or programme admission is inferred. */
class TestCdAct2WorldFramePorts {
    private static CdR12aTerrainAssets terrain; private static CdR12aPlayerAssets player; private static CdR11bAssets past;
    @BeforeAll
    static void loadConfiguredOriginalUsa() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        if (root == null || root.isBlank()) return;
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            terrain = CdR12aTerrainAssets.fromPrograms(source); player = CdR12aPlayerAssets.fromPrograms(source);
            past = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
    }

    @Test void literalControlByteReplacesAllBitsAndPreservesCompleteActorOtherEnvironmentArtClockAndDisplay() {
        requireActual();
        for(int value:new int[]{0,1}) {
            var f=frame(1,1); var actor=f.captureActor(); var shared=f.captureShared(); var e=f.environment();
            var ready=f.prepareObjectTitleCardGlobal(f.state(),e,CdTitleCard.StoreField.CONTROL_LOCK_BYTE,value);
            assertSame(actor.state(),ready.expectedState()); assertSame(e,ready.expectedEnvironment());
            assertSame(actor.state(),ready.finalState()); assertEquals(value,ready.finalEnvironment().controls().controlLocked());
            assertSame(e.movement(),ready.finalEnvironment().movement()); assertSame(e.globals(),ready.finalEnvironment().globals());
            assertSame(e.dynamicSlots(),ready.finalEnvironment().dynamicSlots());
            var c=e.controls(); assertEquals(new CdPlayerBody.Controls(c.rawHeld(),c.rawTap(),c.logicalHeld(),c.logicalTap(),value),ready.finalEnvironment().controls());
            assertEquals(actor,f.captureActor()); assertEquals(shared,f.captureShared());
            f.validatePreparedObjectSolidPublication(ready); f.applyPreparedObjectSolidPublication(ready);
            assertSame(ready.finalEnvironment(),f.environment()); assertSame(actor.state(),f.state());
            assertSame(shared.artState(),f.artState()); assertSame(shared.vIntClock(),f.vIntClock()); assertEquals(actor.display(),f.display());
        }
    }
    @Test void sameLiteralRetainsActualReferencesAndPreparedFullPairCasRejectsInterveningOwnerChange() {
        requireActual(); var f=frame(1,1);
        var one=f.prepareObjectTitleCardGlobal(f.state(),f.environment(),CdTitleCard.StoreField.CONTROL_LOCK_BYTE,1);
        f.applyPreparedObjectSolidPublication(one);
        var e=f.environment(); var same=f.prepareObjectTitleCardGlobal(f.state(),e,CdTitleCard.StoreField.CONTROL_LOCK_BYTE,1);
        assertSame(e,same.finalEnvironment());
        var zero=f.prepareObjectTitleCardGlobal(f.state(),e,CdTitleCard.StoreField.CONTROL_LOCK_BYTE,0);
        f.applyPreparedObjectSolidPublication(zero);
        assertThrows(IllegalArgumentException.class,()->f.validatePreparedObjectSolidPublication(same));
        assertThrows(IllegalArgumentException.class,()->f.applyPreparedObjectSolidPublication(same));
        assertEquals(0,f.environment().controls().controlLocked());
    }
    @Test void wrongOwnerFieldWidthsNullAndStalePairsLeaveTheWholeClosedCutUntouched() {
        requireActual(); var f=frame(1,1); var a=f.captureActor(); var s=f.captureShared(); var e=f.environment();
        for(int bad:new int[]{-1,2,255,256}) assertThrows(IllegalArgumentException.class,()->
                f.prepareObjectTitleCardGlobal(a.state(),e,CdTitleCard.StoreField.CONTROL_LOCK_BYTE,bad));
        assertThrows(IllegalArgumentException.class,()->f.prepareObjectTitleCardGlobal(a.state(),e,CdTitleCard.StoreField.SCROLL_LOCK_BYTE,1));
        assertThrows(NullPointerException.class,()->f.prepareObjectTitleCardGlobal(null,e,CdTitleCard.StoreField.CONTROL_LOCK_BYTE,0));
        assertThrows(NullPointerException.class,()->f.prepareObjectTitleCardGlobal(a.state(),null,CdTitleCard.StoreField.CONTROL_LOCK_BYTE,0));
        assertThrows(NullPointerException.class,()->f.prepareObjectTitleCardGlobal(a.state(),e,null,0));
        var otherEnvironment=new CdPlayerBody.Environment(e.movement(),new CdPlayerBody.Controls(1,2,3,4,5),e.globals(),e.dynamicSlots());
        assertThrows(IllegalArgumentException.class,()->f.prepareObjectTitleCardGlobal(a.state(),otherEnvironment,CdTitleCard.StoreField.CONTROL_LOCK_BYTE,0));
        var otherState=new CdPlayerBody.State(a.state().motion(),a.state().animation(),new CdPlayerBody.Header(2,0,0,0,0),
                a.state().timers(),a.state().history(),a.state().rotationDistance(),a.state().objectLinks(),a.state().rotationAngle());
        assertThrows(IllegalArgumentException.class,()->f.prepareObjectTitleCardGlobal(otherState,e,CdTitleCard.StoreField.CONTROL_LOCK_BYTE,0));
        assertEquals(a,f.captureActor()); assertEquals(s,f.captureShared()); assertFalse(f.closed());
    }
    @Test void pendingAndClosedFrameGuardsDoNotCreateEntitlementsOrExecuteAnySourceWork() {
        requireActual(); var f=frame(1,1); var state=f.state(); var environment=f.environment();
        f.prepareInput(8);
        assertThrows(IllegalStateException.class,()->f.prepareObjectTitleCardGlobal(state,environment,CdTitleCard.StoreField.CONTROL_LOCK_BYTE,0));
        assertThrows(IllegalStateException.class,f::applyLevelStartNormalPlayer);
        assertThrows(IllegalStateException.class,()->f.initializeLevelSizeCentrePlayerValues(80,389));
        f.clearPendingInput(); assertSame(state,f.state()); assertSame(environment,f.environment());
        f.close();
        assertThrows(IllegalStateException.class,()->f.prepareObjectTitleCardGlobal(state,environment,CdTitleCard.StoreField.CONTROL_LOCK_BYTE,0));
        assertThrows(IllegalStateException.class,f::applyLevelStartNormalPlayer);
        assertThrows(IllegalStateException.class,()->f.initializeLevelSizeCentrePlayerValues(80,389));
    }
    @Test void spawnZeroLoadPlayerWritesOnlyNativeIdAndNeverTheWarpHurtTimer() {
        requireActual(); var state=state(); var h=state.header();
        var dirty=new CdPlayerBody.State(state.motion(),state.animation(),new CdPlayerBody.Header(7,h.mappingAddress(),h.tile(),h.priority(),h.width()),
                state.timers(),state.history(),state.rotationDistance(),state.objectLinks(),state.rotationAngle());
        var f=CdPlayerFrameRuntime.fromAct2Entry(terrain,player,dirty,environment(1,1,1,1),art(),clock()); var shared=f.captureShared();
        f.applyLevelStartNormalPlayer();
        assertEquals(new CdPlayerBody.State(dirty.motion(),dirty.animation(),new CdPlayerBody.Header(1,h.mappingAddress(),h.tile(),h.priority(),h.width()),
                dirty.timers(),dirty.history(),dirty.rotationDistance(),dirty.objectLinks(),dirty.rotationAngle()),f.state());
        assertSame(dirty.timers(),f.state().timers()); assertEquals(shared,f.captureShared()); assertTrue(f.display().isEmpty());
    }
    @Test void levelSizeNativeWordCentresRetainFractionsAndAllOtherMotionStateSharedAndClockValues() {
        requireActual(); var f=frame(1,1); var before=f.state(); var m=before.motion(); var shared=f.captureShared();
        for(int bad:new int[]{-1,0x10000}) {
            assertThrows(IllegalArgumentException.class,()->f.initializeLevelSizeCentrePlayerValues(bad,389));
            assertThrows(IllegalArgumentException.class,()->f.initializeLevelSizeCentrePlayerValues(80,bad));
        }
        assertSame(before,f.state()); f.initializeLevelSizeCentrePlayerValues(80,389);
        var expected=new CdPlayerMotionState((80<<16)|(m.centreX16_16()&65535),(389<<16)|(m.centreY16_16()&65535),
                m.xVelocity(),m.yVelocity(),m.groundVelocity(),m.routine(),m.angle(),m.flags(),m.spriteFlags(),m.xRadius(),m.yRadius(),
                m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
        assertEquals(new CdPlayerBody.State(expected,before.animation(),before.header(),before.timers(),before.history(),before.rotationDistance(),before.objectLinks(),before.rotationAngle()),f.state());
        assertEquals(shared,f.captureShared()); assertTrue(f.display().isEmpty());
    }
    private static void requireActual() { assumeTrue(terrain != null && player != null && past != null, "Set soniccd.media.root to original USA media"); }
    private static CdPlayerFrameRuntime frame(int act, int period) {
        return CdPlayerFrameRuntime.fromAct2Entry(terrain, player, state(), environment(act, period, 1, 1), art(), clock());
    }
    private static CdPlayerFrameRuntime.VIntClock clock() { return new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(), 0); }
    private static CdPlayerArtRuntime.State art() {
        byte[] cpu = new byte[0x300], resident = new byte[0x300];
        for (int i = 0; i < cpu.length; i++) { cpu[i] = (byte) (i * 7 + 3); resident[i] = (byte) (i * 11 + 5); }
        return new CdPlayerArtRuntime.State(cpu, resident, 7, true);
    }
    private static CdPlayerBody.State state() {
        byte[] history = new byte[0x100]; for (int i = 0; i < history.length; i++) history[i] = (byte) (i * 13 + 7);
        return new CdPlayerBody.State(new CdPlayerMotionState(0x01234567, 0x023489AB, -0x123, 0x234, -0x345,
                0, 0x56, 0x42, 0x23, 7, 11, 6, 7, 9, 0, -0x1234, 0x44, 0x10, 0x77, 0x88),
                new CdPlayerBody.AnimationCursor(5, 6, 3), new CdPlayerBody.Header(1, 0x12345678, 0x2345, 3, 0x21),
                new CdPlayerBody.Timers(0x78, 0x1234, 0x2345, 0x3456), new CdPlayerBody.History(CdAssetBytes.copyOf(history), 0x42),
                0x67, new CdPlayerBody.ObjectLinks(0x20, 0x7F), 0x89);
    }
    private static CdPlayerBody.Environment environment(int act, int period, int bored, int warp) {
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x123, 0x23, 0x34),
                new CdPlayerMovement.Bounds(0x12, 0x2345, 0x3456, 0x78), new CdPlayerMovement.Gates(act, period, 0x12, 0x34, 0x56, 0x78, 0xFF),
                new CdPlayerMovement.Globals(0x12, 0x2345, 0x34, 0x56, bored, 0x4567, 0x78, 0x89, 0x9A, 0xAB)),
                new CdPlayerBody.Controls(0x24, 0x18, 8, 0x10, 0x81), new CdPlayerBody.Globals(0x1234, 0x2345, 0x12, 0x34, 0x4567, warp, 0x56, 0x78),
                CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
    }
}
