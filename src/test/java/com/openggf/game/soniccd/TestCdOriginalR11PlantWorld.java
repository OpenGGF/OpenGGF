package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.Cd3dPlant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Genuine public original entry and controller/registry only. No private placement access,
 * supplied Snapshot/Frame/pool or current-art/pixel manufacture. All Java UNRUN. */
@Isolated
class TestCdOriginalR11PlantWorld extends TestCdOriginalR11LoadingWorld {
    @Test void actualOriginalControllerMustReturnThePlantGraphAndReplayThroughFullAndCourseCuts()throws Exception{
        var source=TestCdOriginalBootPrefix.source();
        var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);
        var module=attempt.module().orElseThrow();
        try(var boot=boot()){
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,module);
            var frame=module.originalR11EntryFrame();var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var full=registry.capture();var course=registry.captureCourse();var services=module.programServicesState();
            int rows=0;
            while(rows<4096&&!familyReturned(module)){step(loop,8);rows++;}
            assertTrue(familyReturned(module),"A real original Plant parent and complete child graph must return; no unsupported stop counts as success");
            assertFalse(frame.closed());assertTrue(frame.stageSource().ownsPresent(source.assets()));
            var expected=image(module);int reachedRows=rows;
            registry.restore(full);for(int i=0;i<reachedRows;i++)step(loop,8);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(module));
            registry.restoreCourse(course);for(int i=0;i<reachedRows;i++)step(loop,8);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(module));
            assertSame(frame,module.originalR11EntryFrame());assertSame(services.loads(),module.programServicesState().loads());
            assertEquals(services.completedCalls(),module.programServicesState().completedCalls());
        }
    }
    private static void ready(GameLoop loop,SonicCdGameModule module){
        for(int row=0;row<256&&module.originalR11LoadingPrefix().main().levelStartedByte()==0;row++)step(loop,0);
        assertEquals(1,module.originalR11LoadingPrefix().main().levelStartedByte());
    }
    private static boolean familyReturned(SonicCdGameModule module){
        var family=module.originalR11PlacementImage().pool().slots().stream().filter(s->s.id()==Cd3dPlant.OBJECT_ID&&s.routine()==2).toList();
        // One native anchor/Y group must be complete; unrelated partial groups cannot combine.
        var groups=family.stream().collect(Collectors.groupingBy(s->List.of(
                s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.PLANT_ANCHOR_X),
                s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.Y))));
        return groups.values().stream().anyMatch(group->(group.size()==5||group.size()==7)
                &&group.stream().filter(s->s.subtype()==1
                    &&s.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.MAP_FRAME)==1
                    &&s.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.PRIORITY)==4).count()==3
                &&group.stream().filter(s->s.subtype()!=1).count()==group.size()-3);
    }
    private static Object image(SonicCdGameModule module){
        var frame=module.originalR11EntryFrame();
        var placement=module.originalR11PlacementImage();
        return List.of(placement.status(),placement.pool().slots(),placement.savedFlags(),frame.captureActor(),frame.captureShared(),module.originalR11LoadingPrefix(),module.originalR11LoadingImage());
    }
}
