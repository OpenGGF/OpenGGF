package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.scroll.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests written before this packet's production changes. ALL Java UNRUN.
 * Constructed camera values below are DATA-only controls, never installed into a World.
 * Natural obligations use only the actual public controller; an unsupported edge fails. */
class TestCdOriginalR11CheckpointReload extends TestCdOriginalR11LoadingWorld {
    private static void ready(GameLoop loop,SonicCdGameModule m){
        for(int i=0;i<512&&m.originalR11LoadingImage().phase()!=SonicCdR31aLoadingVisit.Phase.READY;i++)step(loop,0);
        assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());
    }
    private static void checkpoint(GameLoop loop,SonicCdGameModule m){
        for(int i=0;i<6000&&m.originalR11LoadingPrefix().main().level().checkpoint()==0;i++)step(loop,8);
        assertNotEquals(0,m.originalR11LoadingPrefix().main().level().checkpoint(),"Actual checkpoint activation is required");
        var saved=m.originalR11LoadingImage().globals().checkpointSaved();
        assertEquals(m.originalR11LoadingPrefix().main().level().spawnMode(),saved.read(CdCheckpointSaveData.SavedField.SPAWN_MODE));
        assertTrue(m.originalR11WorldImage().orElseThrow().sounds().stream().anyMatch(s->s instanceof SonicCdGameModule.PastCheckpointFm));
    }
    private static void reload(GameLoop loop,SonicCdGameModule m){
        var f=m.originalR11EntryFrame();int stock=m.originalR11LoadingPrefix().ring().lives();
        for(int i=0;i<6000&&f.state().motion().routine()<6;i++)step(loop,8);
        assertTrue(f.state().motion().routine()>=6,"Real native death required");
        for(int i=0;i<1200&&m.originalR11LoadingImage().phase()==SonicCdR31aLoadingVisit.Phase.READY;i++)step(loop,8);
        assertEquals(stock-1,m.originalR11LoadingPrefix().ring().lives());
        assertEquals(SonicCdR31aLoadingVisit.Phase.INITIAL_TO_BLACK,m.originalR11LoadingImage().phase());
        assertTrue(m.originalR11WorldImage().orElseThrow().sounds().stream().anyMatch(s->s instanceof SonicCdGameModule.PastGameOverSubCpu));
        assertSame(f,m.originalR11EntryFrame());
    }
    /** Read-only record materialization; no reflected mutation or owner construction. */
    private static Object material(Object x){
        if(x instanceof CdAssetBytes b)return Base64.getEncoder().encodeToString(b.copy());
        if(x instanceof byte[] b)return Base64.getEncoder().encodeToString(b);
        if(x instanceof int[] a)return Arrays.stream(a).boxed().toList();
        if(x instanceof Map<?,?> m){var out=new HashMap<Object,Object>();m.forEach((k,v)->out.put(material(k),material(v)));return out;}
        if(x instanceof CdObjectDrawQueue.CpuSat cpu)return material(List.of(cpu.bytes(),cpu.spriteCount(),cpu.provenance()));
        if(x instanceof CdWorld.R11Snapshot world)return material(List.of(world.cpu(),world.sounds()));
        if(x instanceof CdObjectPlacementRuntime.Snapshot p)return material(List.of(p.status(),p.savedFlags(),p.pool().slots().stream().map(slot->Base64.getEncoder().encodeToString(slot.copyBytes())).toList()));
        if(x instanceof List<?> v)return v.stream().map(TestCdOriginalR11CheckpointReload::material).toList();
        if(x instanceof Optional<?> v)return v.map(TestCdOriginalR11CheckpointReload::material);
        if(x!=null&&x.getClass().isRecord()){
            var out=new ArrayList<Object>();out.add(x.getClass());
            for(var c:x.getClass().getRecordComponents())try{var a=c.getAccessor();if(!a.trySetAccessible())throw new AssertionError("record reader");out.add(material(a.invoke(x)));}catch(ReflectiveOperationException e){throw new AssertionError(e);}
            return Collections.unmodifiableList(out);
        }
        return x;
    }
    private static Object partitions(SonicCdGameModule m){
        var f=m.originalR11EntryFrame();
        return material(List.of(f.captureActor(),f.captureShared(),m.originalR11LoadingImage(),m.originalR11LoadingPrefix(),
                m.originalR11PlacementImage(),m.originalR11WorldImage(),m.originalR11FirstLoad(),m.programServicesState()));
    }
    private static void replayService(GameLoop loop,SonicCdGameModule m,int held){
        var r=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=r.capture();var course=r.captureCourse();
        var before=m.originalR11EntryFrame().vIntClock();int waits=m.originalR11FirstLoad().completedWaits();
        step(loop,held);var expected=partitions(m);
        assertTrue(!m.originalR11EntryFrame().vIntClock().equals(before)||m.originalR11FirstLoad().completedWaits()>waits,"One real service must return");
        r.restore(full);step(loop,held);assertEquals(expected,partitions(m));
        r.restoreCourse(course);step(loop,held);assertEquals(expected,partitions(m));
    }
    @Test void actualCheckpointSaveMustReturnWithCompleteFullAndCourseReplay()throws Exception{
        var source=TestCdOriginalBootPrefix.source();var a=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=a.module().orElseThrow();
        try(var boot=boot()){
            var loop=boot.bootSonicCdSelectedProfile(source,a);ready(loop,m);checkpoint(loop,m);replayService(loop,m,8);
        }
    }
    @Test void actualSavedCheckpointMustSurviveDeathGameOverReloadAndOrdinaryService()throws Exception{
        var source=TestCdOriginalBootPrefix.source();var a=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=a.module().orElseThrow();
        try(var boot=boot()){
            var loop=boot.bootSonicCdSelectedProfile(source,a);ready(loop,m);checkpoint(loop,m);
            var saved=m.originalR11LoadingImage().globals().checkpointSaved();var f=m.originalR11EntryFrame();reload(loop,m);ready(loop,m);
            assertSame(f,m.originalR11EntryFrame());assertEquals(saved,m.originalR11LoadingImage().globals().checkpointSaved());
            int savedTime=saved.read(CdCheckpointSaveData.SavedField.TIME_LONG);
            int loadedTime=(savedTime&0xFFFF0000)|(((((savedTime>>>8)&255)-1)&255)<<8)|59;
            int actualTime=0;for(int address=0xFF1514;address<=0xFF1517;address++)actualTime=(actualTime<<8)|m.originalR11LoadingImage().globals().live().knownByte(address).orElseThrow();
            assertEquals(loadedTime,actualTime,"Post-PLC Main must retain the native loaded time");
            assertEquals(0,m.originalR11LoadingPrefix().main().level().spawnMode());
            assertEquals(0,m.originalR11LoadingPrefix().main().level().levelRestart());
            step(loop,0);assertEquals(2,f.state().motion().routine());
            assertTrue(m.originalR11WorldImage().orElseThrow().cpu().isPresent());replayService(loop,m,0);
        }
    }
    @Test void actualReloadWaitAndReadyMustReplayEveryOriginalPartition()throws Exception{
        var source=TestCdOriginalBootPrefix.source();var a=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=a.module().orElseThrow();
        try(var boot=boot()){
            var loop=boot.bootSonicCdSelectedProfile(source,a);ready(loop,m);checkpoint(loop,m);reload(loop,m);
            replayService(loop,m,0);
            for(int i=0;i<512&&m.originalR11LoadingImage().phase()==SonicCdR31aLoadingVisit.Phase.INITIAL_TO_BLACK;i++)step(loop,0);
            assertEquals(SonicCdR31aLoadingVisit.Phase.FIRST_PLC,m.originalR11LoadingImage().phase());
            // Rewind both sides of every genuine PLC service, including the one that
            // actually invokes LevelSizeLoad_StartPos:65 and returns its checkpoint load.
            for(int i=0;i<512&&m.originalR11LoadingImage().phase()==SonicCdR31aLoadingVisit.Phase.FIRST_PLC;i++)replayService(loop,m,0);
            assertEquals(SonicCdR31aLoadingVisit.Phase.LEVEL_PLC,m.originalR11LoadingImage().phase());
            ready(loop,m);replayService(loop,m,0);
        }
    }
    private static SonicCdR31aLoadingVisit.Scroll cameras(){
        var fg=new CdR11aForegroundScroll.State(0x1111,0x2222ABCD,0x10,0x10,3,4,5,0x6666,0x7777,0,0);
        var bg=new CdR11aBackgroundScroll.State(0x33330102,0x44440304,0x55550506,0x66660708,0x7777090A,0x88880B0C,
                CdAssetBytes.copyOf(new byte[CdR11aBackgroundScroll.CROSSING_RAM_BYTES]),0x100,0x200,0x300,
                CdAssetBytes.copyOf(new byte[CdR11aBackgroundScroll.CLOUD_RAM_BYTES]),CdAssetBytes.copyOf(new byte[CdR11aBackgroundScroll.DEFORMATION_RAM_BYTES]),CdAssetBytes.copyOf(new byte[CdR11aBackgroundScroll.HSCROLL_RAM_BYTES]),0x12345678);
        var cameras=new CdR11aBackgroundPlane.CopiedCameras(new CdR11aBackgroundPlane.Camera(1,2),new CdR11aBackgroundPlane.Camera(3,4),new CdR11aBackgroundPlane.Camera(5,6));
        var copies=new SonicCdR31aLoadingVisit.Copies(0x12345678,0x23456789,cameras,0x3456,new CdR11aBackgroundPlane.FlagWords(1,2,3));
        return new SonicCdR31aLoadingVisit.Scroll(fg,bg,copies,0x9876,0x9999,0xAAAA);
    }
    @Test void constructedCameraWordsAreIndependentAndRetainAllEightFractions(){
        var s=cameras();var before=s.background();
        var targets=List.of(CdCheckpointLoadData.StoreTarget.FG_X,CdCheckpointLoadData.StoreTarget.FG_Y,CdCheckpointLoadData.StoreTarget.BG_X,CdCheckpointLoadData.StoreTarget.BG_Y,CdCheckpointLoadData.StoreTarget.BG2_X,CdCheckpointLoadData.StoreTarget.BG2_Y,CdCheckpointLoadData.StoreTarget.BG3_X,CdCheckpointLoadData.StoreTarget.BG3_Y);
        for(int i=0;i<targets.size();i++){
            var t=targets.get(i);var old=s;int value=0xA000+i;
            s=SonicCdGameModule.originalCheckpointCameraStore(s,new CdCheckpointLoadData.StoreCall(t,OptionalInt.of(SonicCdGameModule.originalCheckpointCameraWord(s,t)),value));
            for(var other:targets)assertEquals(other==t?value:SonicCdGameModule.originalCheckpointCameraWord(old,other),SonicCdGameModule.originalCheckpointCameraWord(s,other));
        }
        assertEquals(0x9876,s.foregroundXFractionWord());assertEquals(0xABCD,s.foreground().cameraY16_16()&65535);
        assertEquals(0x0102,s.background().cameraBgX()&65535);assertEquals(0x0304,s.background().cameraBgY()&65535);
        assertEquals(0x0506,s.background().cameraBg2X()&65535);assertEquals(0x0708,s.background().cameraBg2Y()&65535);
        assertEquals(0x090A,s.background().cameraBg3X()&65535);assertEquals(0x0B0C,s.background().cameraBg3Y()&65535);
        assertSame(before.cloudRam(),s.background().cloudRam());assertSame(before.crossingRam(),s.background().crossingRam());
        assertSame(before.deformationRam(),s.background().deformationRam());assertSame(before.hscrollRam(),s.background().hscrollRam());
        assertEquals(before.vscroll(),s.background().vscroll());assertEquals(before.scrollFlagsBg2(),s.background().scrollFlagsBg2());
    }
    @Test void destinationBottomStoreIsSeparateAndStaleCameraCallIsRejected(){
        var s=cameras();var t=CdCheckpointLoadData.StoreTarget.DEST_BOTTOM_BOUND;
        var next=SonicCdGameModule.originalCheckpointCameraStore(s,new CdCheckpointLoadData.StoreCall(t,OptionalInt.of(0x6666),0xF123));
        assertEquals(0xF123,next.foreground().destBottomBound());assertEquals(0x7777,next.foreground().destLeftBound());
        assertSame(s.background(),next.background());assertEquals(s.foreground().cameraY16_16(),next.foreground().cameraY16_16());
        assertThrows(IllegalArgumentException.class,()->SonicCdGameModule.originalCheckpointCameraStore(s,new CdCheckpointLoadData.StoreCall(t,OptionalInt.of(0x1234),0xF123)));
        assertThrows(IllegalArgumentException.class,()->SonicCdGameModule.originalCheckpointCameraWord(s,CdCheckpointLoadData.StoreTarget.BOTTOM_BOUND));
    }
    @Test void constructedBeginMainUsesPreClearSpawnAndDoesNotReadSavedHistory()throws Exception{
        var source=TestCdOriginalBootPrefix.source();var a=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=a.module().orElseThrow();
        try(var boot=boot()){
            var loop=boot.bootSonicCdSelectedProfile(source,a);ready(loop,m);var g=m.originalR11LoadingImage().globals();var r=g.ring();
            var raw=CdCheckpointLiveAux.writeTimeLong(g.live(),0x0005FF3B);
            var v=SonicCdR31aWorldHost.with(g,new CdRingGlobals(0x1234,r.lives(),0xD3,r.updateHudRings(),r.updateHudLives(),r.timeStopTimer(),r.ringAnimationTimer(),r.ringAnimationFrame()),raw,g.contact(),g.score(),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7());
            var retained=v.beginMain(0x81);assertEquals(raw,retained.live());assertEquals(0x1234,retained.ring().rings());assertEquals(0xD3,retained.ring().livesFlags());
            var fresh=v.beginMain(0);assertEquals(0,fresh.ring().rings());assertEquals(0,fresh.ring().livesFlags());
            for(int addr=0xFF1514;addr<=0xFF1517;addr++)assertEquals(0,fresh.live().knownByte(addr).orElseThrow());
            assertSame(v.checkpointSaved(),retained.checkpointSaved());assertSame(v.checkpointSaved(),fresh.checkpointSaved());
        }
    }
    @Test void absentPrivateOriginalIssuerCannotInvokeTheExistingFrameWriter()throws Exception{
        var source=TestCdOriginalBootPrefix.source();var a=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=a.module().orElseThrow();
        try(var boot=boot()){
            var loop=boot.bootSonicCdSelectedProfile(source,a);ready(loop,m);var before=partitions(m);
            assertThrows(NullPointerException.class,()->m.originalR11EntryFrame().loadOriginalCheckpoint(null));
            assertEquals(before,partitions(m));replayService(loop,m,0);
        }
    }
    private static com.openggf.game.soniccd.player.CdPlayerFrameRuntime.RestartIngress componentIngress(){
        byte[] history=new byte[256],cpu=new byte[768],resident=new byte[768];
        Arrays.fill(history,(byte)0x51);Arrays.fill(cpu,(byte)0x72);Arrays.fill(resident,(byte)0x39);
        return new com.openggf.game.soniccd.player.CdPlayerFrameRuntime.RestartIngress(CdAssetBytes.copyOf(history),CdAssetBytes.copyOf(cpu),CdAssetBytes.copyOf(resident),1,2,3,4,0,8);
    }
    @Test void componentExistingWriterRejectsCrossThreadAndExpiresAfterReturnAndRestore()throws Exception{
        // Separate explicit DEV component. This Frame is never injected into a real route.
        var source=TestCdOriginalBootPrefix.source();
        var retained=new java.util.concurrent.atomic.AtomicReference<com.openggf.game.soniccd.player.CdPlayerFrameRuntime.CheckpointLoadPort>();
        try(var frame=com.openggf.game.soniccd.player.CdPlayerFrameRuntime.checkpointRestartDevelopment(source.assets(),17,componentIngress(),port->{
            retained.set(port);var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
            var other=new Thread(()->{try{port.read(com.openggf.game.soniccd.player.CdPlayerFrameRuntime.CheckpointLoadField.PRIMARY_X);}catch(Throwable e){failure.set(e);}});
            other.start();try{other.join();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
            assertInstanceOf(IllegalStateException.class,failure.get());
            var field=com.openggf.game.soniccd.player.CdPlayerFrameRuntime.CheckpointLoadField.PRIMARY_X;
            port.store(new com.openggf.game.soniccd.player.CdPlayerFrameRuntime.CheckpointLoadStore(field,port.read(field),0x1234));
            assertEquals(0x1234,port.read(field));
            assertThrows(IllegalArgumentException.class,()->port.store(new com.openggf.game.soniccd.player.CdPlayerFrameRuntime.CheckpointLoadStore(field,0,0x5678)));
        })){
            var a=frame.captureActor();var shared=frame.captureShared();
            assertThrows(IllegalStateException.class,()->retained.get().read(com.openggf.game.soniccd.player.CdPlayerFrameRuntime.CheckpointLoadField.PRIMARY_X));
            frame.restoreActor(a);frame.restoreShared(shared);
            assertThrows(IllegalStateException.class,()->retained.get().isOriginalInvocation(null,frame));
            assertEquals(a,frame.captureActor());assertEquals(shared,frame.captureShared());
        }
    }
    @Test void componentExistingWriterFailureKeepsFirstExceptionAndExpiresItsPort()throws Exception{
        var source=TestCdOriginalBootPrefix.source();var first=new IllegalStateException("first native failure");
        var retained=new java.util.concurrent.atomic.AtomicReference<com.openggf.game.soniccd.player.CdPlayerFrameRuntime.CheckpointLoadPort>();
        assertSame(first,assertThrows(IllegalStateException.class,()->com.openggf.game.soniccd.player.CdPlayerFrameRuntime.checkpointRestartDevelopment(source.assets(),17,componentIngress(),port->{retained.set(port);throw first;})));
        assertEquals(0,first.getSuppressed().length);
        assertThrows(IllegalStateException.class,()->retained.get().read(com.openggf.game.soniccd.player.CdPlayerFrameRuntime.CheckpointLoadField.PRIMARY_Y));
    }

}
