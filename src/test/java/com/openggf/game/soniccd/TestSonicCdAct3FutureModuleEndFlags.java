package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.CdR13CapsuleAssets;
import com.openggf.game.soniccd.level.CdR13ResultsAssets;
import com.openggf.game.soniccd.objects.CdObjectState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.ready;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/** Original controller bootstrap to actual R13D; then one explicitly CONSTRUCTED Results126 /
 * later unbound Powerup127 ingress. This is actual private Module publication coverage, not a
 * natural Boss defeat, completed Results route or successor-admission proof. No exports. */
@Isolated
class TestSonicCdAct3FutureModuleEndFlags {
    @Test void actualModuleResultsClrUsesOriginalReceiptAndPreservesEveryOtherProgrammeField() throws Exception {
        var fixture=new TestSonicCdPastLoadingWorld();fixture.setup();
        try(var egl=TestSonicCdPastResultsRoute.Egl.open()) {
            var prepared=ready();var h=prepared.harness();var module=h.module();
            var early=TestSonicCdAct2ControllerRoute.class.getDeclaredMethod("controller",int.class);early.setAccessible(true);
            var goal=TestSonicCdAct2ControllerRoute.class.getDeclaredMethod("naturalR12bGoalController",int.class);goal.setAccessible(true);
            var right=LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8,0,0,0,false,false),PlayerInputState.neutral());
            for(int call=prepared.returned()+1;call<=17142;call++)
                step(h,call<=14516?(LogicalInputSnapshot)early.invoke(null,call):call<=16187?(LogicalInputSnapshot)goal.invoke(null,call):right);
            assertTrue(module.act3FutureLoadingSelected());var frame=module.requirePlayerFrame();var source=frame.stageSource();
            var core=module.act3FutureLoadingVisit();assertEquals(SonicCdAct3FutureLoadingVisit.Phase.READY,core.state().phase());
            var host=module.act3FutureWorldHost();var world=(CdWorld)actualField(module,"act3FutureWorld");
            var pass=(SonicCdAct3FutureObjectPass)actualField(actualField(world,"binding"),"objects");
            var authority=(SonicCdAct3FutureWorldHost.Authority)actualField(module,"act3Authority");
            var before=(SonicCdGameModule.LevelProgramState)actualField(module,"levelProgram");
            assertThrows(IllegalStateException.class,()->authority.resultsFlags(host,pass));
            assertSame(before,actualField(module,"levelProgram"));assertFalse(frame.closed());
            var data=CdR13ResultsAssets.bind(core.assets(),CdR13CapsuleAssets.bind(core.assets()));var pool=host.placement().pool();
            // The source metadata is original. Only the finite, fully stated component ingress is constructed.
            pool.write(pool.state(126).moveByte(ID,0x3A).moveByte(ROUTINE,8).moveByte(SPRITE_FLAGS,0).moveByte(PRIORITY,0)
                    .moveWord(TILE,0x83C4).moveLong(MAPPINGS,data.mappingAddress()).moveByte(MAP_FRAME,0)
                    .moveWord(X,0).moveWord(RESULTS_SCREEN_Y_WORD,0xCC).moveWord(RESULTS_DEST_X,0x120));
            pool.write(pool.state(127).moveByte(ID,0x03).moveByte(ROUTINE,0));
            var stopped=assertThrows(SonicCdAct3FutureObjectPass.Unowned.class,()->step(h,right));
            assertEquals(SonicCdAct3FutureObjectPass.Boundary.OBJECT_FAMILY,stopped.boundary());assertEquals(127,stopped.wholeIndex());assertEquals(3,stopped.value());
            var after=(SonicCdGameModule.LevelProgramState)actualField(module,"levelProgram");
            assertEquals(new SonicCdGameModule.LevelProgramState(before.demoMode(),before.timeStones(),before.specialStage(),before.usePlayer2(),
                    before.palFadeFlags(),before.gameMode(),before.levelStarted(),before.whiteFadeSteps(),before.phase(),OptionalInt.of(0)),after);
            assertSame(source,frame.stageSource());assertEquals(1,frame.environment().movement().gates().timeZone());assertEquals(0x100,frame.environment().movement().gates().zoneAct());
            assertEquals(2,core.state().level().levelRestart());assertEquals(8,pool.state(126).routine());assertEquals(3,pool.state(127).id());
            // Completed stores precede the genuine later terminal boundary; no failure replay is invented.
        } finally {fixture.cleanup();}
    }
    @Test void actualModuleRetainsOldExitAndBothRewindScopesAtTheUnknownUnlockFence() throws Exception {
        var fixture=new TestSonicCdPastLoadingWorld();fixture.setup();
        try(var egl=TestSonicCdPastResultsRoute.Egl.open()) {
            var prepared=ready();var h=prepared.harness();var module=h.module();
            var early=TestSonicCdAct2ControllerRoute.class.getDeclaredMethod("controller",int.class);early.setAccessible(true);
            var goal=TestSonicCdAct2ControllerRoute.class.getDeclaredMethod("naturalR12bGoalController",int.class);goal.setAccessible(true);
            var same=TestSonicCdAct2ControllerRoute.class.getDeclaredMethod("assertNaturalR13SnapshotValue",Object.class,Object.class,String.class);same.setAccessible(true);
            var right=LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8,0,0,0,false,false),PlayerInputState.neutral());
            for(int call=prepared.returned()+1;call<=17142;call++)
                step(h,call<=14516?(LogicalInputSnapshot)early.invoke(null,call):call<=16187?(LogicalInputSnapshot)goal.invoke(null,call):right);
            var core=module.act3FutureLoadingVisit();var host=module.act3FutureWorldHost();var frame=module.requirePlayerFrame();var source=frame.stageSource();
            var world=h.mode().getWorldSession();long generation=h.mode().getLevelManager().getCompletedProductionLoadGeneration();var registry=h.mode().getRewindRegistry();
            var data=CdR13ResultsAssets.bind(core.assets(),CdR13CapsuleAssets.bind(core.assets()));var pool=host.placement().pool();
            // Sole constructed ingress; every subsequent fade/clear/blank/restore uses the real Module/Core owners.
            pool.write(pool.state(127).moveByte(ID,0x3A).moveByte(ROUTINE,8).moveByte(SPRITE_FLAGS,0).moveByte(PRIORITY,0)
                    .moveWord(TILE,0x83C4).moveLong(MAPPINGS,data.mappingAddress()).moveByte(MAP_FRAME,0)
                    .moveWord(X,0).moveWord(RESULTS_SCREEN_Y_WORD,0xCC).moveWord(RESULTS_DEST_X,0x120));
            step(h,right);assertEquals(SonicCdAct3FutureLoadingVisit.Phase.OLD_BLACK_FADE,core.state().phase());
            assertEquals(SonicCdGameModule.LevelProgramPhase.OLD_BLACK_FADE,module.levelProgramState().phase());
            var actor=frame.state();var art=frame.artState();int levelFrames=frame.environment().globals().levelFrames();var clock=frame.vIntClock();
            boolean replayedFade=false,replayedReturned=false,replayedBlank=false;
            for(int i=0;i<64&&core.state().phase()!=SonicCdAct3FutureLoadingVisit.Phase.OLD_IPX_RETURNED;i++) {
                var beforePhase=core.state().phase();var beforeClock=frame.vIntClock();var full=registry.capture();var course=registry.captureCourse();
                step(h,right);var afterPhase=core.state().phase();var afterFull=registry.capture();var afterCourse=registry.captureCourse();
                if(beforePhase==SonicCdAct3FutureLoadingVisit.Phase.OLD_RETURNED)assertEquals(beforeClock,frame.vIntClock(),"IPX blank does not run retained level Finish");
                assertEquals(actor,frame.state());assertSame(art,frame.artState());assertEquals(levelFrames,frame.environment().globals().levelFrames());
                boolean replay=i==10||afterPhase==SonicCdAct3FutureLoadingVisit.Phase.OLD_RETURNED||afterPhase==SonicCdAct3FutureLoadingVisit.Phase.OLD_IPX_RETURNED;
                if(replay)for(boolean courseScope:new boolean[]{false,true}) {
                    if(courseScope)registry.restoreCourse(course);else registry.restore(full);
                    same.invoke(null,full,registry.capture(),"actual Module exit restored FULL");same.invoke(null,course,registry.captureCourse(),"actual Module exit restored course");
                    step(h,right);same.invoke(null,afterFull,registry.capture(),"actual Module exit FULL after replay");same.invoke(null,afterCourse,registry.captureCourse(),"actual Module exit course after replay");
                }
                replayedFade|=i==10;replayedReturned|=afterPhase==SonicCdAct3FutureLoadingVisit.Phase.OLD_RETURNED;replayedBlank|=afterPhase==SonicCdAct3FutureLoadingVisit.Phase.OLD_IPX_RETURNED;
            }
            assertTrue(replayedFade&&replayedReturned&&replayedBlank);assertEquals(22,core.oldExitSteps());
            assertEquals(SonicCdAct3FutureLoadingVisit.Phase.OLD_IPX_RETURNED,core.state().phase());assertEquals(clock.deltaSinceAdmittedCut()+23,frame.vIntClock().deltaSinceAdmittedCut());
            assertEquals(SonicCdGameModule.LevelProgramPhase.OLD_IPX_RETURNED,module.levelProgramState().phase());
            assertEquals(0,module.ipxProgressState().savedLevel().word().orElseThrow());
            var missing=assertThrows(SonicCdAct3FutureLoadingVisit.UnsupportedLoading.class,()->module.serviceAct3FutureLoading(8));
            assertEquals(SonicCdAct3FutureLoadingVisit.Boundary.IPX_RUN_R1_TIME_ATTACK_UNLOCK_OWNER,missing.boundary());
            assertEquals(0x100,module.ipxProgressState().savedLevel().word().orElseThrow());
            assertEquals(SonicCdAct3FutureLoadingVisit.Phase.OLD_IPX_BOSS_RETURNED,core.state().phase());
            var beforeFull=registry.capture();var beforeCourse=registry.captureCourse();
            var repeated=assertThrows(SonicCdAct3FutureLoadingVisit.UnsupportedLoading.class,()->module.serviceAct3FutureLoading(8));
            assertEquals(SonicCdAct3FutureLoadingVisit.Boundary.IPX_RUN_R1_TIME_ATTACK_UNLOCK_OWNER,repeated.boundary());
            assertFalse(frame.closed());assertFalse(core.failed());assertSame(frame,module.requirePlayerFrame());assertSame(source,frame.stageSource());assertSame(world,h.mode().getWorldSession());
            assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration());assertEquals(0x100,frame.environment().movement().gates().zoneAct());assertEquals(1,frame.environment().movement().gates().timeZone());
            same.invoke(null,beforeFull,registry.capture(),"healthy missing-owner FULL nonwrite");same.invoke(null,beforeCourse,registry.captureCourse(),"healthy missing-owner course nonwrite");
        } finally {fixture.cleanup();}
    }

}
