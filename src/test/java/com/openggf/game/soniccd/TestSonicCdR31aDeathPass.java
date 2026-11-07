package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/** Constructed complete component ingress through actual serviced private Frame/pass producers.
 * No World/Ready/original call is fabricated. The selected fresh-boot controller is separate.
 * Literal R3 PlayerDead/Restart, N objects/player.asm:2244-2352. */
class TestSonicCdR31aDeathPass {
    private static TestSonicCdR31aObjectPass.Harness h(int routine,int y,int reset)throws Exception{
        var h=new TestSonicCdR31aObjectPass.Harness();h.primary(1,0,0);h.service();h.walk();h.finish();
        var s=h.frame.state();var m=s.motion();var env=h.frame.environment();var art=h.frame.artState();var clock=h.frame.vIntClock();
        var motion=new CdPlayerMotionState(m.centreX16_16(),(y<<16)|0xBEEF,0,0,0,routine,m.angle(),2,0x84,m.xRadius(),m.yRadius(),0x18,m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
        var state=new CdPlayerBody.State(motion,s.animation(),s.header(),new CdPlayerBody.Timers(0,0,0,reset),s.history(),s.rotationDistance(),s.objectLinks(),s.rotationAngle());
        h.frame.close();h.frame=CdPlayerFrameRuntime.fromR31aEntry(h.assets,state,env,art,clock);h.pass=new SonicCdR31aObjectPass(h.assets,h.frame,h.placement,h.objects,h);h.realPrimary=true;return h;
    }
    // Harness bottom=$1000: $1101 is the first WORD strictly beyond bottom+$100.
    private static void full(TestSonicCdR31aObjectPass.Harness h){for(int w=32;w<128;w++)h.placement.pool().write(h.placement.pool().state(w).moveByte(ID,3).moveWord(X_FRACTION,w*257));}
    private static boolean suppressed(Throwable root,Throwable expected){return Arrays.stream(root.getSuppressed()).anyMatch(e->e==expected);}
    @Test void actualGonePublishesDirtyFirstFreeAndStopsAtItsPhysicalGameOverVisit()throws Exception{
        try(var h=h(6,0x1101,0)){
            h.placement.pool().write(h.placement.pool().state(32).moveWord(X_FRACTION,0xCAFE).moveByte(SUBTYPE,0xA5));var before=h.placement.capture();h.service();
            var stop=assertThrows(SonicCdR31aObjectPass.Unowned.class,h::walk);assertEquals(SonicCdR31aObjectPass.Boundary.OBJECT_FAMILY,stop.boundary());assertEquals(32,stop.wholeIndex());assertEquals(0x3B,stop.value());
            assertEquals(before.pool().slots().getFirst().moveByte(ID,0x3B),h.placement.pool().state(32));for(int n=1;n<96;n++)assertEquals(before.pool().slots().get(n),h.placement.pool().capture().slots().get(n));
            assertEquals(8,h.frame.state().motion().routine());assertEquals(60,h.frame.state().timers().reset());assertEquals(2,h.cut.ring().lives());assertEquals(1,h.cut.ring().updateHudLives());assertEquals(1,h.primaryPublications);
            assertThrows(IllegalStateException.class,h.lastPrimary::applyNative);assertThrows(IllegalStateException.class,h::walk);
        }
    }
    @Test void originalRestartCountsRawWordsAndReturnsWithoutAnyDrawPublication()throws Exception{
        for(int reset:new int[]{0,2,0x8000,0xFFFF})try(var h=h(8,0x200,reset)){var state=h.frame.state();var before=h.placement.capture();h.service();h.walk();
            assertEquals(reset==0?0:reset-1,h.frame.state().timers().reset());assertEquals(state.history(),h.frame.state().history());assertEquals(state.animation(),h.frame.state().animation());assertEquals(state.motion().spriteFlags(),h.frame.state().motion().spriteFlags());assertTrue(h.frame.display().isEmpty());assertEquals(0,h.cut.core().level().levelRestart());assertEquals(before.pool().slots(),h.placement.pool().capture().slots());h.finish();assertTrue(h.cut.completedCpu().orElseThrow().provenance().stream().noneMatch(p->p.slotRef().wholeIndex()==0));}
    }
    @Test void originalResetPublishesActualSavedClearLevelLongAndSuffixOnlyOnce()throws Exception{
        try(var h=h(8,0x200,1)){
            var e=h.cut;var core=e.core().level(new CdLevelGlobals(0,1,0,2,3,0x1234,0x56,7,8));h.cut=new SonicCdR31aObjectPass.Cut(core,e.ring(),e.currentTerrain(),e.contact(),e.world(),e.noBumperByte(),e.cameraXWord(),e.cameraYWord(),e.scrollLockWord(),e.layerByte(),e.cpuSat(),e.completedCpu(),e.sounds(),e.amyCapturedByte());
            h.placement.bsetSavedFlag(7,1,CdObjectPlacementRuntime.SavedBit.LOADED);h.placement.bsetSavedFlag(9,0,CdObjectPlacementRuntime.SavedBit.MEMBER_1);var status=h.placement.status();h.service();h.walk();
            assertEquals(new CdLevelGlobals(1,1,0,2,1,0,0,7,8),h.cut.core().level());var flags=h.placement.capture().savedFlags();assertEquals(1,flags.unsignedByte(0));assertEquals(1,flags.unsignedByte(1));for(int i=2;i<flags.length();i++)assertEquals(0,flags.unsignedByte(i));assertEquals(new CdObjectPlacementRuntime.Status(status.routine(),status.previousChunkWord(),status.rightRecordIndex(),status.leftRecordIndex(),1,1,status.nullRightByteOffset(),status.nullLeftByteOffset()),h.placement.status());
            assertEquals(0,h.frame.state().timers().reset());assertTrue(h.frame.display().isEmpty());assertEquals(List.of(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.DriverRamWrite(0x1C3E,1)),new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.BodySound(CdPlayerBody.Sound.SCMD_FADECDA))),h.cut.sounds());assertThrows(IllegalStateException.class,h.lastPrimary::applyNative);assertEquals(2,h.cut.sounds().size());h.finish();
        }
    }
    @Test void exhaustedNativeAliasRetainsCompletedPrefixWithoutQueueOrPoolCorruption()throws Exception{
        try(var h=h(6,0x1101,0)){full(h);var pool=h.placement.capture();var cpu=h.cut.completedCpu();h.service();var stop=assertThrows(CdWorld.Unowned.class,h::walk);assertEquals(CdWorld.Boundary.PRIMARY_DEATH_ALIAS,stop.boundary());
            assertEquals(pool.pool().slots(),h.placement.pool().capture().slots());assertArrayEquals(pool.savedFlags().copy(),h.placement.capture().savedFlags().copy());assertEquals(8,h.frame.state().motion().routine());assertEquals(60,h.frame.state().timers().reset());assertEquals(2,h.cut.ring().lives());assertSame(cpu,h.cut.completedCpu());assertEquals(0,stop.getSuppressed().length);assertThrows(IllegalStateException.class,h.lastPrimary::applyNative);}
    }
    @Test void nativeAliasRemainsOriginalWhenFirstFreshReadPreparationThrows()throws Exception{
        try(var h=h(6,0x1101,0)){full(h);h.service();var state=h.frame.state();var error=new AssertionError("first current-cut preparation");var once=new AtomicBoolean(true);
            h.onRead=()->{if(StackWalker.getInstance().walk(s->s.anyMatch(f->f.getClassName().equals(SonicCdR31aObjectPass.class.getName())&&f.getMethodName().startsWith("lambda$runPrimary$")))&&once.compareAndSet(true,false))throw error;};
            var stop=assertThrows(CdWorld.Unowned.class,h::walk);assertEquals(CdWorld.Boundary.PRIMARY_DEATH_ALIAS,stop.boundary());assertTrue(suppressed(stop,error));assertSame(state,h.frame.state());assertEquals(3,h.cut.ring().lives());assertEquals(0,h.primaryPublications);}
    }
    @Test void nativeAliasPrepublicationErrorDoesNotAdoptTheUnpreparedBodyPair()throws Exception{
        try(var h=h(6,0x1101,0)){full(h);h.service();var state=h.frame.state();var env=h.frame.environment();var error=new AssertionError("external complete prebuild");h.beforePrimary=()->{throw error;};
            var stop=assertThrows(CdWorld.Unowned.class,h::walk);assertEquals(CdWorld.Boundary.PRIMARY_DEATH_ALIAS,stop.boundary());assertTrue(suppressed(stop,error));assertSame(state,h.frame.state());assertSame(env,h.frame.environment());assertEquals(3,h.cut.ring().lives());assertThrows(IllegalStateException.class,h.lastPrimary::applyNative);}
    }
    @Test void nativeAliasPostpublicationErrorRetainsItsActualReachedPrefix()throws Exception{
        try(var h=h(6,0x1101,0)){full(h);h.service();var error=new AssertionError("after original native publication");h.failAfterPrimary=true;h.primaryFault=error;
            var stop=assertThrows(CdWorld.Unowned.class,h::walk);assertEquals(CdWorld.Boundary.PRIMARY_DEATH_ALIAS,stop.boundary());assertTrue(suppressed(stop,error));assertEquals(8,h.frame.state().motion().routine());assertEquals(60,h.frame.state().timers().reset());assertEquals(2,h.cut.ring().lives());assertTrue(h.frame.closed());assertThrows(IllegalStateException.class,h.lastPrimary::applyNative);}
    }
    @Test void completeFrameMismatchStaysSubordinateToReachedNativeAlias()throws Exception{
        try(var h=h(6,0x1101,0)){full(h);h.service();var before=h.frame.environment();var field=CdPlayerFrameRuntime.class.getDeclaredField("environment");field.setAccessible(true);
            h.beforePrimary=()->{var g=before.globals();var changed=new CdPlayerBody.Environment(before.movement(),before.controls(),new CdPlayerBody.Globals(g.levelFrames(),g.cameraXCentre(),g.timeAttackMode(),g.debugMode(),g.debugCheat(),g.timeWarpTimer(),1,g.bossMusic()),before.dynamicSlots());try{field.set(h.frame,changed);}catch(IllegalAccessException e){throw new AssertionError(e);}};
            var stop=assertThrows(CdWorld.Unowned.class,h::walk);assertEquals(CdWorld.Boundary.PRIMARY_DEATH_ALIAS,stop.boundary());assertTrue(stop.getSuppressed().length>=1);assertEquals(6,h.frame.state().motion().routine());assertEquals(3,h.cut.ring().lives());field.set(h.frame,before);}
    }
}
