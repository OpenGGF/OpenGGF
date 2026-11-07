package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.ready;

/** Tests FIRST, Java UNRUN. Original controller bootstrap through actual R13D loading/Title.
 * The sole subsequent actor/camera change is labelled COMPONENT ingress over the actual owners.
 * Tests actual Module publication, FULL/course replay and terminal later level-flow retirement;
 * does not claim natural death, completed fight, restart, next-zone admission or pixels.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
@Isolated
class TestSonicCdAct3FutureDeathCameraModule {
    @Test void actualModuleDeathContinuesAndRestoresBothScopesBeforeGenuineRestartStops() throws Exception {
        var fixture=new TestSonicCdPastLoadingWorld();fixture.setup();
        try(var egl=TestSonicCdPastResultsRoute.Egl.open()) {
            var prepared=ready();var h=prepared.harness();var module=h.module();
            var early=TestSonicCdAct2ControllerRoute.class.getDeclaredMethod("controller",int.class);early.setAccessible(true);
            var goal=TestSonicCdAct2ControllerRoute.class.getDeclaredMethod("naturalR12bGoalController",int.class);goal.setAccessible(true);
            var same=TestSonicCdAct2ControllerRoute.class.getDeclaredMethod("assertNaturalR13SnapshotValue",Object.class,Object.class,String.class);same.setAccessible(true);
            var right=LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8,0,0,0,false,false),PlayerInputState.neutral());
            for(int call=prepared.returned()+1;call<=17142;call++)step(h,call<=14516?(LogicalInputSnapshot)early.invoke(null,call):call<=16187?(LogicalInputSnapshot)goal.invoke(null,call):right);
            assertTrue(module.act3FutureLoadingSelected());var core=module.act3FutureLoadingVisit();var host=module.act3FutureWorldHost();var frame=module.requirePlayerFrame();
            assertEquals(SonicCdAct3FutureLoadingVisit.Phase.READY,core.state().phase());
            var source=frame.stageSource();var world=h.mode().getWorldSession();long generation=h.mode().getLevelManager().getCompletedProductionLoadGeneration();
            var registry=h.mode().getRewindRegistry();
            TestSonicCdAct3FutureDeathCameraWorld.actor(frame,6,0x200,0,-0x100,0);
            TestSonicCdAct3FutureDeathCameraWorld.camera(host,0x0101BEEF,0x789,0xAB00);
            // Complete the labelled component camera ingress through the actual engine projection before capture.
            host.projectCamera(com.openggf.game.GameServices.camera());
            var full=registry.capture();var course=registry.captureCourse();step(h,right);
            var afterFull=registry.capture();var afterCourse=registry.captureCourse();
            assertEquals(0x101,frame.environment().movement().bounds().bottom());assertEquals(0x101,host.scroll().foreground().destBottomBound());
            assertFalse(frame.closed());assertFalse(core.failed());assertSame(source,module.requirePlayerFrame().stageSource());
            assertSame(world,h.mode().getWorldSession());assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
            for(boolean courseScope:new boolean[]{false,true}) {
                if(courseScope)registry.restoreCourse(course);else registry.restore(full);
                same.invoke(null,full,registry.capture(),"death restored FULL");same.invoke(null,course,registry.captureCourse(),"death restored course");
                step(h,right);same.invoke(null,afterFull,registry.capture(),"death replay FULL");same.invoke(null,afterCourse,registry.captureCourse(),"death replay course");
            }
            TestSonicCdAct3FutureDeathCameraWorld.actor(frame,8,0x200,1,0,0);
            var stopped=assertThrows(CdWorld.Stopped.class,()->step(h,right));
            assertEquals(CdWorld.Boundary.LEVEL_FLOW,stopped.boundary());assertEquals(1,core.state().level().levelRestart());
            assertEquals(0,frame.state().timers().reset());assertTrue(core.failed());assertTrue(frame.closed());
            assertSame(source,frame.stageSource());assertSame(world,h.mode().getWorldSession());assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
            assertThrows(IllegalStateException.class,()->module.serviceAct3FutureLoading(8));
        } finally {fixture.cleanup();}
    }
}
