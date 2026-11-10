package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests FIRST, ALL Java UNRUN. Natural controller obligations never inject slots/poses,
 * clear warp direction, accept a failed row, or treat a completed snapshot as invocation.
 * Direct reader/preparation tests are separately COMPONENT controls, not returned routes. */
@Isolated class TestCdOriginalR11ProgressionWorld extends TestCdOriginalR11LoadingWorld {
    @Test void actualCheckpointFamilyMustReturnAndReplayFullAndCourse()throws Exception { witness(0x13,false); }
    @Test void actualTimePostFamilyMustReturnAndReplayFullAndCourse()throws Exception { witness(0x19,false); }
    @Test void actualCheckpointActivationMustSaveBeforeSoundAndReplayFullAndCourse()throws Exception { witness(0x13,true); }
    @Test void actualTimePostActivationMustRetainRawDirectionAndReplayFullAndCourse()throws Exception { witness(0x19,true); }
    private void witness(int id,boolean activated)throws Exception {
        var source=TestCdOriginalBootPrefix.source();
        var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);
        var module=attempt.module().orElseThrow();
        try(var boot=boot()){
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,module);
            var f=module.originalR11EntryFrame();var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var full=registry.capture();var course=registry.captureCourse();int rows=0;
            while(rows<4096&&!returned(module,id,activated)){step(loop,8);rows++;assertFalse(f.closed());}
            assertTrue(returned(module,id,activated),"Require the genuine returned family/activation, not a later handler's failed prefix");
            assertTrue(module.originalR11WorldImage().orElseThrow().cpu().isPresent());
            if(activated&&id==0x13){
                var g=module.originalR11LoadingImage().globals();var l=module.originalR11LoadingPrefix().main().level();
                assertEquals(1,l.spawnMode());assertEquals(1,g.checkpointSaved().read(CdCheckpointSaveData.SavedField.SPAWN_MODE));
                assertEquals(f.state().motion().centreX16_16()>>>16,g.checkpointSaved().read(CdCheckpointSaveData.SavedField.PRIMARY_X));
            }
            if(activated&&id==0x19)assertTrue(Set.of(1,255).contains(f.environment().movement().gates().timeWarpDirection()));
            var expected=image(module);int n=rows;
            registry.restore(full);for(int i=0;i<n;i++)step(loop,8);
            assertFalse(f.closed());assertTrue(returned(module,id,activated));CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(module));
            registry.restoreCourse(course);for(int i=0;i<n;i++)step(loop,8);
            assertFalse(f.closed());assertTrue(returned(module,id,activated));CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(module));
            assertSame(f,module.originalR11EntryFrame());
        }
    }
    @Test void componentReaderUsesCurrentIndependentCameraWordsAndStopsAtUnknownLiveByte()throws Exception {
        var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);
        var module=attempt.module().orElseThrow();
        try(var boot=boot()){
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,module);
            var f=module.originalR11EntryFrame();var s=module.originalR11LoadingImage();var scroll=s.scroll();var b=scroll.background();var fg=scroll.foreground();
            var independent=new com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll.State(0x1234ABCD,0x2345BCDE,0x3456CDEF,0x4567DEF0,0x5678EF01,0x6789F012,b.crossingRam(),b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3(),b.cloudRam(),b.deformationRam(),b.hscrollRam(),b.vscroll());
            var current=new SonicCdR31aLoadingVisit.Scroll(fg,independent,scroll.copies(),scroll.foregroundXFractionWord(),scroll.topBoundWord(),scroll.waterDeformWord());
            var fields=List.of(CdCheckpointSaveData.Source.BG_X,CdCheckpointSaveData.Source.BG_Y,CdCheckpointSaveData.Source.BG2_X,CdCheckpointSaveData.Source.BG2_Y,CdCheckpointSaveData.Source.BG3_X,CdCheckpointSaveData.Source.BG3_Y);
            int[] expected={0x1234,0x2345,0x3456,0x4567,0x5678,0x6789};
            for(int i=0;i<fields.size();i++)assertEquals(expected[i],CdWorld.originalR11CheckpointSource(fields.get(i),f.state(),f.environment(),current,s.globals(),module.originalR11LoadingPrefix().main().level()));
            var g=s.globals();var unknown=SonicCdR31aWorldHost.with(g,g.ring(),CdCheckpointLiveAux.unknown(),g.contact(),g.score(),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7());
            var failure=assertThrows(CdCheckpointLiveAux.UnknownLiveByte.class,()->CdWorld.originalR11CheckpointSource(CdCheckpointSaveData.Source.WATER_ROUTINE,f.state(),f.environment(),current,unknown,module.originalR11LoadingPrefix().main().level()));
            assertEquals(0xFFF64D,failure.nativeAddress());assertSame(s,module.originalR11LoadingImage());
        }
    }
    @Test void componentDirectionPreparationRejectsStaleFullPairAndDoesNotConsumePendingInput()throws Exception {
        var a=TestCdOriginalBootPrefix.source().assets();
        try(var f=CdPlayerFrameRuntime.terrainOnlyDevelopment(a,(request,resident)->fail("Direction-only preparation keeps the clean mapFrame0; no art publication is permitted"))){
            var actor=f.captureActor();var shared=f.captureShared();var s=f.state();var e=f.environment();
            var p=f.prepareObjectTimePostDirection(s,e,e.movement().gates().timeWarpDirection(),255);
            CdCapturedValueAssertions.assertCapturedValueEquals(actor,f.captureActor());CdCapturedValueAssertions.assertCapturedValueEquals(shared,f.captureShared());
            f.applyPreparedObjectSolidPublication(p);assertEquals(255,f.environment().movement().gates().timeWarpDirection());
            assertThrows(IllegalArgumentException.class,()->f.applyPreparedObjectSolidPublication(p));
            assertThrows(IllegalArgumentException.class,()->f.prepareObjectTimePostDirection(s,e,255,1));
            var pending=f.prepareInput(8);
            assertThrows(IllegalStateException.class,()->f.prepareObjectTimePostDirection(f.state(),f.environment(),255,1));
            f.serviceVBlank(pending.phase());assertEquals(8,f.environment().controls().rawHeld(),"The rejected store leaves the actual pending sample for selector8");
        }
    }
    @Test void componentOriginalCheckpointFactoryRejectsUnbackedPlacementWithoutCallingPorts()throws Exception {
        var a=TestCdOriginalBootPrefix.source().assets();
        var port=new CdCheckpoint.LivePort(){
            public CdLevelGlobals readLevel(CdCheckpoint.LevelReadCall c){throw new AssertionError("No invocation");}
            public int readParent(CdCheckpoint.ParentByteCall c){throw new AssertionError("No invocation");}
            public CdCheckpointSaveData.State readSaved(CdCheckpoint.SavedReadCall c){throw new AssertionError("No invocation");}
            public void store(CdCheckpoint.GlobalStoreCall c){throw new AssertionError("No invocation");}
            public CdCheckpointSaveData.Result save(CdCheckpoint.SaveDataCall c){throw new AssertionError("No invocation");}
            public void fm(CdCheckpoint.FmRequestCall c){throw new AssertionError("No invocation");}
        };
        var declared=new CdObjectPlacementRuntime(a.placements());var before=declared.capture();
        assertThrows(IllegalArgumentException.class,()->CdCheckpoint.fromOriginalPresentAssets(declared,a,port,(pool,id)->fail("No Draw")));
        assertEquals(before.pool().slots(),declared.pool().capture().slots());assertArrayEquals(before.savedFlags().copy(),declared.capture().savedFlags().copy());
        assertNotNull(CdCheckpoint.fromOriginalPresentAssets(CdObjectPlacementRuntime.fromAssets(a),a,port,(pool,id)->fail("Construction is not invocation")));
    }
    private static boolean returned(SonicCdGameModule m,int id,boolean activated){
        if(activated)return m.originalR11WorldImage().orElseThrow().sounds().stream().anyMatch(s->id==0x13?s instanceof SonicCdGameModule.PastCheckpointFm:s instanceof SonicCdGameModule.PastTimePostSubCpu);
        return m.originalR11PlacementImage().pool().slots().stream().anyMatch(s->s.id()==id&&s.routine()!=0&&(id!=0x19||s.subtype()>=8));
    }
    private static void ready(GameLoop loop,SonicCdGameModule m){for(int i=0;i<256&&m.originalR11LoadingPrefix().main().levelStartedByte()==0;i++)step(loop,0);assertEquals(1,m.originalR11LoadingPrefix().main().levelStartedByte());}
    private static Object image(SonicCdGameModule m){var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();var w=m.originalR11WorldImage().orElseThrow();return List.of(p.status(),p.pool().slots(),p.savedFlags(),f.captureActor(),f.captureShared(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),w.cpu(),w.sounds(),m.programServicesState(),m.selectedProfileState());}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
