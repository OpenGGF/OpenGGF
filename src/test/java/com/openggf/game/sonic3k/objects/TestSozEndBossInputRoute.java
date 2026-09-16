package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real directional/jump input from arena approach through eight hits, capsule and LRZ. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozEndBossInputRoute {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();}
    @Test void controllerOnlyBattleAndCapsuleReachLrzWithGraphRewind(){
        var config=SonicConfigurationService.getInstance();config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        com.openggf.game.session.SessionManager.clear();TestEnvironment.activeGameplayMode();
        var f=HeadlessTestFixture.builder().withZoneAndAct(8,1)
                .startPosition((short)0x51C0,(short)0x620).startPositionIsCentre()
                .withFreshLevelStartLifecycle().build();
        f.sprite().setRingCount(99);
        var route=new SozEndBossVictoryRoute();var registry=f.gameplayMode().getRewindRegistry();
        var milestones=new java.util.LinkedHashSet<String>();
        int hits=0;StringBuilder trail=new StringBuilder();
        for(int tick=0;tick<8500&&GameServices.level().getCurrentZone()==8;tick++) {
            var boss=SozEndBossVictoryRoute.boss();
            var manager=GameServices.level().getObjectManager();
            int hp=boss==null?8:boss.getCollisionProperty();
            boolean open=boss!=null&&boss.shellOpen();
            boolean results=GameServices.gameState().isEndOfLevelActive();
            var input=route.input(tick,f.sprite());
            var before=tick>3?registry.capture():null;
            step(f,input);
            assertFalse(f.sprite().getDead(),"death at "+tick+" "+trail);
            if(GameServices.level().getCurrentZone()!=8)break;
            boss=SozEndBossVictoryRoute.boss();String milestone=null;
            if(boss!=null&&boss.getCollisionProperty()<hp) {
                assertEquals(hp-1,boss.getCollisionProperty());hits++;milestone="hit"+hits;
            } else if(!open&&boss!=null&&boss.shellOpen()&&!milestones.contains("shell"))milestone="shell";
            else if(!manager.activeObjectsOfType(SozEndBossEggCapsule.class).isEmpty()
                    &&manager.activeObjectsOfType(SozEndBossEggCapsule.class).getFirst().isOpened()
                    &&!milestones.contains("capsule"))milestone="capsule";
            else if(!results&&GameServices.gameState().isEndOfLevelActive()&&!milestones.contains("results"))milestone="results";
            else if(results&&!GameServices.gameState().isEndOfLevelActive())milestone="walk";
            var plane=com.openggf.game.sonic3k.runtime.S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow().events().postBossPlane();
            if(milestone==null && plane.revision()>0 && !milestones.contains("redraw"+plane.revision()))milestone="redraw"+plane.revision();
            if(milestone!=null&&milestones.add(milestone)) {
                trail.append(tick).append(':').append(milestone).append(' ');
                var after=registry.capture();manager.setRewindInPlaceRestoreEnabledForTest(false);
                assertNotNull(before);registry.restore(before);same(before,registry.capture(),milestone+" restore");
                step(f,input);same(after,registry.capture(),milestone+" replay");
            }
        }
        assertEquals(8,hits,trail.toString());
        assertTrue(milestones.containsAll(java.util.Set.of("shell","capsule","results","walk","redraw1","redraw2","redraw8")),trail.toString());
        assertEquals(9,GameServices.level().getCurrentZone(),trail.toString());
        assertEquals(0,GameServices.level().getCurrentAct());
        assertTrue(f.gameplayMode().isGameplayRuntimeReady());
    }
    private static void step(HeadlessTestFixture f,com.openggf.debug.playback.Bk2FrameInput input){
        int m=input.p1InputMask();f.stepFrame(false,false,(m&4)!=0,(m&8)!=0,(m&16)!=0);
    }
    private static void same(CompositeSnapshot expected,CompositeSnapshot actual,String label){
        assertEquals(expected.entries().keySet(),actual.entries().keySet(),label);
        for(var key:expected.entries().keySet())assertTrue(
                RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)).isEmpty(),
                ()->label+" "+key+": "+RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)));
    }
}
