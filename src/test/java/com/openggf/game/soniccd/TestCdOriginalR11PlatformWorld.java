package com.openggf.game.soniccd;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Strict future public-engine obligations; Java UNRUN. Earlier unowned families are failures,
 * not evidence of either platform. No actor/pool/clock/Frame state is injected. */
@Isolated class TestCdOriginalR11PlatformWorld extends TestCdOriginalR11LoadingWorld {
    @Test void originalCollapseFamilyMustReturnWithNontrivialFullAndCourseReplay()throws Exception{family(0x20);}
    @Test void originalPlatformFamilyMustReturnWithNontrivialFullAndCourseReplay()throws Exception{family(0x21);}
    private void family(int id)throws Exception{
        var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var module=attempt.module().orElseThrow();
        try(var boot=boot()){
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);
            for(int n=0;n<256&&module.originalR11LoadingPrefix().main().levelStartedByte()==0;n++)step(loop,0);
            assertEquals(1,module.originalR11LoadingPrefix().main().levelStartedByte());var frame=module.originalR11EntryFrame();var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
            int calls=0;do{step(loop,8);calls++;assertFalse(frame.closed());assertTrue(frame.state().motion().routine()<6,"Returned death ends the route obligation");}while(calls<4096&&!returned(module,id));
            assertTrue(calls>0);assertTrue(returned(module,id),"No returned actual source-placed platform within bound");var expected=image(module);int replay=calls;
            registry.restore(full);for(int n=0;n<replay;n++){step(loop,8);assertTrue(frame.state().motion().routine()<6);}assertFalse(frame.closed());assertTrue(returned(module,id));CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(module));
            registry.restoreCourse(course);for(int n=0;n<replay;n++){step(loop,8);assertTrue(frame.state().motion().routine()<6);}assertFalse(frame.closed());assertTrue(returned(module,id));CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(module));assertSame(frame,module.originalR11EntryFrame());
        }
    }
    private static boolean returned(SonicCdGameModule module,int id){return module.originalR11PlacementImage().pool().slots().stream().anyMatch(s->s.id()==id&&s.routine()!=0&&s.word(CdObjectState.WordField.TILE)==0x44BE&&s.longBits(CdObjectState.LongField.MAPPINGS)!=0);}
    private static Object image(SonicCdGameModule module){var f=module.originalR11EntryFrame();var w=module.originalR11WorldImage().orElseThrow();var p=module.originalR11PlacementImage();return List.of(p.status(),p.pool().slots(),p.savedFlags(),f.captureActor(),f.captureShared(),module.originalR11LoadingPrefix(),module.originalR11LoadingImage(),w.cpu(),w.sounds(),w.tunnelServices(),module.selectedProfileState(),module.programServicesState());}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
