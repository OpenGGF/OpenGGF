package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.game.GameServices;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.CdGameOver;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Written FIRST. Actual selected NEW_GAME -> original Boot -> real Frame/World/loop.
 * Right input is the only gameplay writer. Bounds are finite requirements, not route evidence.
 * No pose, clock, pool, snapshot construction, synthetic Entry or DEV birth. ALL Java UNRUN. */
class TestCdOriginalR11DeathLifecycle extends TestCdOriginalR11LoadingWorld {
    private static void ready(GameLoop loop,SonicCdGameModule m){
        for(int i=0;i<512&&m.originalR11LoadingImage().phase()!=SonicCdR31aLoadingVisit.Phase.READY;i++)step(loop,0);
        assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());
        assertEquals(1,m.originalR11LoadingPrefix().main().levelStartedByte());
    }
    private static void death(GameLoop loop,SonicCdGameModule m){
        var f=m.originalR11EntryFrame();
        for(int i=0;i<6000&&f.state().motion().routine()<6;i++)step(loop,8);
        assertTrue(f.state().motion().routine()>=6,"Actual returned primary death required, no unsupported edge is success");
        assertFalse(f.closed());
    }
    private static void lostStock(GameLoop loop,SonicCdGameModule m,int lives){
        for(int i=0;i<600&&m.originalR11LoadingPrefix().ring().lives()==lives;i++)step(loop,8);
        assertEquals(lives-1,m.originalR11LoadingPrefix().ring().lives());
    }
    private static void transfer(GameLoop loop,SonicCdGameModule m){
        for(int i=0;i<600&&m.originalR11LoadingImage().phase()==SonicCdR31aLoadingVisit.Phase.READY;i++)step(loop,8);
        assertEquals(SonicCdR31aLoadingVisit.Phase.INITIAL_TO_BLACK,m.originalR11LoadingImage().phase());
        assertEquals(0x003F,m.originalR11LoadingImage().globals().tail().orElseThrow().paletteFadeInfoWord(),"FadeToBlack caller store precedes its first wait");
        assertEquals(0,m.originalR11LoadingPrefix().main().levelStartedByte());
    }
    private static AutoCloseable retire(){return ()->{
        Throwable first=null;try{SessionManager.clear();}catch(RuntimeException|Error e){first=e;}
        try{GameServices.graphics().cleanup();}catch(RuntimeException|Error e){if(first==null)first=e;else if(first!=e)first.addSuppressed(e);}
        if(first instanceof RuntimeException e)throw e;if(first instanceof Error e)throw e;
    };}
    private static Object facts(SonicCdGameModule m){
        var f=m.originalR11EntryFrame();var s=m.originalR11LoadingImage();var p=m.originalR11LoadingPrefix();
        var slots=m.originalR11PlacementImage().pool().slots().stream().map(x->Base64.getEncoder().encodeToString(x.copyBytes())).toList();
        return material(List.of(f.state(),f.environment(),f.vIntClock(),s.phase(),s.scroll(),s.globals(),s.fadeSteps(),s.displayEnabled(),s.clears(),p.main(),p.ring(),p.score(),p.calls(),slots,
            Base64.getEncoder().encodeToString(s.cpuSat().copy()),Base64.getEncoder().encodeToString(p.normalPalette().copy()),Base64.getEncoder().encodeToString(p.fadePalette().copy())));
    }

    /** Observe immutable bytes/records by content; do not invoke any writer or private field. */
    private static Object material(Object value){
        if(value instanceof com.openggf.game.soniccd.level.CdAssetBytes b)return Base64.getEncoder().encodeToString(b.copy());
        if(value instanceof byte[] b)return Base64.getEncoder().encodeToString(b);
        if(value instanceof List<?> list)return list.stream().map(TestCdOriginalR11DeathLifecycle::material).toList();
        if(value instanceof Optional<?> x)return x.map(TestCdOriginalR11DeathLifecycle::material);
        if(value!=null&&value.getClass().isRecord()){
            var out=new ArrayList<Object>();out.add(value.getClass());
            for(var field:value.getClass().getRecordComponents())try{var getter=field.getAccessor();if(!getter.trySetAccessible())throw new AssertionError("Immutable record reader unavailable");out.add(material(getter.invoke(value)));}catch(ReflectiveOperationException e){throw new AssertionError(e);}
            return Collections.unmodifiableList(out);
        }
        return value;
    }

    @Test void actualDeathFreezesBothNativeCameraBoundsAndStillBuildsThePicture()throws Exception{
        var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
        try(var boot=boot();var cleanup=retire()){
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);death(loop,m);
            var f=m.originalR11EntryFrame();var scroll=m.originalR11LoadingImage().scroll();
            assertEquals(scroll.foreground().cameraYWord(),f.environment().movement().bounds().bottom());
            assertEquals(scroll.foreground().cameraYWord(),scroll.foreground().destBottomBound());
            assertTrue(m.originalR11WorldImage().orElseThrow().cpu().isPresent());
        }
    }
    @Test void actualDeadCheckGoneRetainsStockAndRunsTheCachedGameOverAtItsPhysicalVisit()throws Exception{
        var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
        try(var boot=boot();var cleanup=retire()){
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);death(loop,m);lostStock(loop,m,3);
            assertEquals(0,m.originalR11LoadingPrefix().main().level().updateHudTime());
            assertTrue(m.originalR11WorldImage().orElseThrow().sounds().stream().anyMatch(x->x instanceof SonicCdGameModule.PastGameOverSubCpu s&&s.command()==CdGameOver.SubCpuCommand.SCMD_FADEPCM));
            assertTrue(m.originalR11PlacementImage().pool().slots().stream().noneMatch(x->x.id()==CdGameOver.OBJECT_ID),"Lives remain: Init deletes its own full slot");
        }
    }
    @Test void actualResetTransfersOnlyAfterTheCompletedWalkAndPreservesTheSelectedPicture()throws Exception{
        var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
        try(var boot=boot();var cleanup=retire()){
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);death(loop,m);lostStock(loop,m,3);transfer(loop,m);
            assertEquals(1,m.originalR11LoadingPrefix().main().level().levelRestart());
            assertEquals(2,m.originalR11LoadingPrefix().ring().lives());
            assertEquals(0,m.originalR11LoadingImage().fadeSteps());
            var retained=m.originalR11WorldImage().orElseThrow().cpu().orElseThrow();
            var raw=m.originalR11LoadingImage().cpuSat();boolean nonzero=false;for(byte value:raw.copy())nonzero|=value!=0;
            assertTrue(nonzero,"A genuine retained picture is required, not an empty-SAT control");
            assertSame(retained,m.originalR11FirstLoad().selection().inputs().selectedCpu().orElseThrow());
            step(loop,0); // Actual first selector12 publication, before any sprite-storage clear.
            assertSame(retained,m.originalR11FirstLoad().selection().inputs().selectedCpu().orElseThrow());
            assertArrayEquals(raw.copy(),m.originalR11FirstLoad().selection().inputs().selectedSat().copy());
            assertEquals(0x003F,m.originalR11LoadingImage().globals().tail().orElseThrow().paletteFadeInfoWord());
            m.drawOriginalR11World();
            assertFalse(m.originalR11EntryFrame().closed());
        }
    }
    @Test void actualReloadReentersReadyOnTheSameFrameWithoutStartPcmOrSaveReplay()throws Exception{
        var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
        try(var boot=boot();var cleanup=retire()){
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);var f=m.originalR11EntryFrame();var services=m.programServicesState();
            death(loop,m);lostStock(loop,m,3);transfer(loop,m);ready(loop,m);
            assertSame(f,m.originalR11EntryFrame());assertSame(services.loads(),m.programServicesState().loads());assertEquals(services.completedCalls(),m.programServicesState().completedCalls());
            assertEquals(2,m.originalR11LoadingPrefix().ring().lives());assertEquals(0,m.originalR11LoadingPrefix().main().level().levelRestart());assertEquals(0,f.state().motion().routine());
            assertTrue(m.originalR11WorldImage().orElseThrow().cpu().isEmpty(),"Actual storage/SAT clear retires the earlier CPU receipt");
            step(loop,8);assertEquals(2,f.state().motion().routine());assertFalse(f.closed());
        }
    }
    @Test void fullAndCourseRestoreReplayActualInitialFadeAndReloadWithoutAnotherBirth()throws Exception{
        var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
        try(var boot=boot();var cleanup=retire()){
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);death(loop,m);lostStock(loop,m,3);transfer(loop,m);
            var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var f=m.originalR11EntryFrame();
            step(loop,0);var expected=facts(m);registry.restore(full);step(loop,0);assertEquals(expected,facts(m));registry.restoreCourse(course);step(loop,0);assertEquals(expected,facts(m));assertSame(f,m.originalR11EntryFrame());
            ready(loop,m);var reloaded=registry.capture();var reloadedCourse=registry.captureCourse();step(loop,8);var ordinary=facts(m);
            registry.restore(reloaded);step(loop,8);assertEquals(ordinary,facts(m));registry.restoreCourse(reloadedCourse);step(loop,8);assertEquals(ordinary,facts(m));
        }
    }
    @Test void aPreTransferCaptureIsRejectedBeforeAnyNewLifetimePartitionChanges()throws Exception{
        var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
        try(var boot=boot();var cleanup=retire()){
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();death(loop,m);lostStock(loop,m,3);transfer(loop,m);var before=facts(m);
            assertThrows(IllegalArgumentException.class,()->registry.restore(full));assertEquals(before,facts(m));assertThrows(IllegalArgumentException.class,()->registry.restoreCourse(course));assertEquals(before,facts(m));
            step(loop,0);assertFalse(m.originalR11EntryFrame().closed());
        }
    }
    @Test void invalidHeldDuringTheRealReloadIsAHealthyNonwriteAndRetirementRemainsTerminal()throws Exception{
        var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
        try(var boot=boot();var cleanup=retire()){
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);death(loop,m);lostStock(loop,m,3);transfer(loop,m);var before=facts(m);var f=m.originalR11EntryFrame();
            assertThrows(IllegalArgumentException.class,()->m.serviceOriginalR11Loading(256));assertEquals(before,facts(m));step(loop,0);assertFalse(f.closed());
            var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();SessionManager.clear();assertTrue(f.closed());assertThrows(IllegalStateException.class,()->registry.restore(full));
        }
    }
    @Test void actualLastStockGameOverPerformsItsReturnWaitThenStopsAtTheOriginalCallerOwner()throws Exception{
        var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
        try(var boot=boot();var cleanup=retire()){
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);
            for(int lives=3;lives>0;lives--){death(loop,m);lostStock(loop,m,lives);
                if(lives==1){assertTrue(m.originalR11WorldImage().orElseThrow().sounds().stream().anyMatch(x->x instanceof SonicCdGameModule.PastGameOverSubCpu s&&s.command()==CdGameOver.SubCpuCommand.SCMD_GMOVERMUS));assertEquals(2,m.originalR11PlacementImage().pool().slots().stream().filter(x->x.id()==CdGameOver.OBJECT_ID).count());}
                transfer(loop,m);if(lives>1)ready(loop,m);
            }
            for(int i=0;i<22;i++)step(loop,0);assertEquals(SonicCdR31aLoadingVisit.Phase.CLEAR_RETURN_WAIT,m.originalR11LoadingImage().phase());
            assertArrayEquals(new byte[128],m.originalR11LoadingPrefix().normalPalette().copy());assertEquals(0,m.originalR11LoadingPrefix().main().level().plcLoadFlags());
            step(loop,0);assertEquals(SonicCdR31aLoadingVisit.Phase.RETURNED,m.originalR11LoadingImage().phase());
            var edge=assertThrows(UnsupportedOperationException.class,()->step(loop,0));assertTrue(edge.getMessage().contains("Original R11 native programme return"));
        }
    }
}
