package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Strict public original-controller obligations. ALL Java UNRUN. No unsupported edge is success. */
@Isolated
class TestCdOriginalR11SolidHazardWorld extends TestCdOriginalR11LoadingWorld {
    @Test void actualRampMustReturnAndReplayFullAndCourse()throws Exception { returnedFamily(0x2A); }
    @Test void actualBoulderMustReturnAndReplayFullAndCourse()throws Exception { returnedFamily(0x1B); }
    @Test void actualSpikesMustReturnAndReplayFullAndCourse()throws Exception { returnedFamily(0x26); }
    @Test void actualSpringMustReturnAndReplayFullAndCourse()throws Exception { returnedFamily(0x0A); }
    private void returnedFamily(int id)throws Exception {
        var source=TestCdOriginalBootPrefix.source();
        var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);
        var module=attempt.module().orElseThrow();
        try(var boot=boot()){
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);
            for(int row=0;row<256&&module.originalR11LoadingPrefix().main().levelStartedByte()==0;row++)step(loop,0);
            assertEquals(1,module.originalR11LoadingPrefix().main().levelStartedByte());
            var frame=module.originalR11EntryFrame();var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var full=registry.capture();var course=registry.captureCourse();var services=module.programServicesState();
            // Require an actual ordinary service after the captured cut even if Ready initialized the family.
            int rows=0;do{step(loop,8);rows++;assertFalse(frame.closed());}while(rows<4096&&!returned(module,id));
            assertTrue(rows>0,"Replay must include a returned ordinary service");
            assertTrue(returned(module,id),"A healthy complete returned new family is required: "+id);
            assertTrue(module.originalR11WorldImage().orElseThrow().cpu().isPresent());
            var expected=image(module);int count=rows;
            registry.restore(full);for(int i=0;i<count;i++)step(loop,8);
            assertTrue(returned(module,id));assertFalse(frame.closed());CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(module));
            registry.restoreCourse(course);for(int i=0;i<count;i++)step(loop,8);
            assertTrue(returned(module,id));assertFalse(frame.closed());CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(module));
            assertSame(frame,module.originalR11EntryFrame());assertSame(services.loads(),module.programServicesState().loads());
            assertEquals(services.completedCalls(),module.programServicesState().completedCalls());
        }
    }
    private static boolean returned(SonicCdGameModule m,int id){
        return m.originalR11PlacementImage().pool().slots().stream().anyMatch(s->s.id()==id&&s.routine()!=0
                &&s.longBits(CdObjectState.LongField.MAPPINGS)!=0);
    }
    private static Object image(SonicCdGameModule module){
        var frame=module.originalR11EntryFrame();var world=module.originalR11WorldImage().orElseThrow();var p=module.originalR11PlacementImage();
        return List.of(p.status(),p.pool().slots(),p.savedFlags(),frame.captureActor(),frame.captureShared(),module.originalR11LoadingPrefix(),module.originalR11LoadingImage(),world.cpu(),world.sounds(),module.selectedProfileState(),module.programServicesState());
    }
    @Test void originalBindingsRejectConstructedPlacementBeforeAnyCallback()throws Exception {
        var a=TestCdOriginalBootPrefix.source().assets();var p=new CdObjectPlacementRuntime(a.placements());var solid=CdSolidObject.fromAssets(a);
        assertThrows(IllegalArgumentException.class,()->CdBoulder.fromOriginalPresentAssets(p,a,solid,c->fail(),(pool,id)->fail()));
        assertThrows(IllegalArgumentException.class,()->CdSpikes.fromOriginalPresentAssets(p,a,solid,c->fail(),c->fail(),c->fail(),(pool,id)->fail()));
        assertThrows(IllegalArgumentException.class,()->Cd3dRamp.fromOriginalPresentAssets(p,a,c->fail(),(pool,id)->fail()));
    }
    @Test void velocityPreparationPreservesCompletePairAndRejectsStalePublication()throws Exception {
        var a=TestCdOriginalBootPrefix.source().assets();try(var f=CdPlayerFrameRuntime.terrainOnlyDevelopment(a)){
            var s=f.state();var e=f.environment();var m=s.motion();
            var p=f.prepareObjectYVelocity(s,e,m.yVelocity()&65535,0xF400);
            assertSame(s,f.state());assertSame(e,f.environment());f.validatePreparedObjectSolidPublication(p);
            f.applyPreparedObjectSolidPublication(p);assertEquals((short)0xF400,f.state().motion().yVelocity());
            assertEquals(m.centreX16_16(),f.state().motion().centreX16_16());assertEquals(m.centreY16_16(),f.state().motion().centreY16_16());assertSame(e,f.environment());
            assertEquals(s.header(),f.state().header());assertEquals(s.timers(),f.state().timers());assertEquals(s.objectLinks(),f.state().objectLinks());
            assertThrows(IllegalArgumentException.class,()->f.applyPreparedObjectSolidPublication(p));
        }
    }
}
