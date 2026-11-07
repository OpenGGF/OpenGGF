package com.openggf.game.sonic2.objects;

import com.openggf.game.GameServices;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.game.session.SessionManager;
import com.openggf.mods.run.MonitorRule;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@RequiresRom(SonicGame.SONIC_2)
class TestS2RandomisedMonitorContents {
    private AbstractPlayableSprite player;
    @BeforeEach void open() {
        HeadlessTestFixture.builder().withZoneAndAct(0,0).build();
        player = (AbstractPlayableSprite)GameServices.camera().getFocusedSprite();
    }
    @AfterEach void close() { SessionManager.clear(); GameModuleRegistry.reset(); }

    private void policy(MonitorRule rule,boolean swaps) {
        var base = GameplayRunPolicy.competition();
        SessionManager.getCurrentGameplayMode().beginGameplayRun(new GameplayRunPolicy(false,false,
                base.actCompletion(),false,false,base.deathRestart(),false,false,rule,swaps),0,0);
    }
    private MonitorContentsObjectInstance icon() {
        var placement = GameServices.level().getCurrentLevel().getObjects().stream()
                .filter(spawn -> spawn.objectId()==com.openggf.game.sonic2.constants.Sonic2ObjectIds.MONITOR)
                .findFirst().orElseThrow();
        var result = new MonitorContentsObjectInstance(placement.x(),placement.y(),4,player);
        result.setServices(GameServices.level().getObjectManager().getObjectServices());
        return result;
    }

    @Test void nativeFrameDrawAndFrozenTeleportGuard() {
        var level = GameServices.level();
        for (var rule : new MonitorRule[]{MonitorRule.RANDOMISED,MonitorRule.COMPETITION_ITEMS}) {
            for (boolean swaps : new boolean[]{false,true}) for (boolean running : new boolean[]{false,true}) {
                policy(rule,swaps);
                if (running) level.getLevelGamestate().resumeTimer();
                else level.getLevelGamestate().pauseTimer();
                for (int frame=0;frame<16;frame++) {
                    level.setFrameCounter(frame);
                    var contents = icon();
                    contents.update(0x4321,player); // A deliberately different V-int count.
                    int expected = rule==MonitorRule.RANDOMISED ? (frame & 7)+1 : (frame & 1)==0 ? 3 : 8;
                    if (expected==8 && (!running || !swaps)) expected=7;
                    assertEquals(expected,contents.selectedSubtype(),rule+" at level frame "+frame);
                }
            }
        }
        policy(MonitorRule.NORMAL,false);
        level.setFrameCounter(7);
        var ordinary = icon(); ordinary.update(0,player);
        assertEquals(4,ordinary.selectedSubtype(),"NORMAL retains its shipped placement content");
    }

    @Test void firstActualContentsExecutionLatchesAndRewindRestoresInitState() {
        policy(MonitorRule.RANDOMISED,true);
        var level = GameServices.level();
        var contents = icon();
        contents.delayFirstIconUpdateForPassedSlot();
        var uninitialized = contents.captureRewindState();
        level.setFrameCounter(3); contents.update(0,player);
        assertEquals(4,contents.selectedSubtype(),"passed SST slot must not draw yet");
        level.setFrameCounter(7); contents.update(1,player);
        assertEquals(8,contents.selectedSubtype());
        level.setFrameCounter(0); contents.update(2,player);
        assertEquals(8,contents.selectedSubtype(),"contents remain latched while rising");
        contents.restoreRewindState(uninitialized);
        contents.update(3,player); // The restored passed-slot delay.
        contents.update(4,player);
        assertEquals(1,contents.selectedSubtype(),"rewind must restore the uninitialized draw, not retain teleport");
    }

    @Test void namedLifeDrawCreditsItsNativeSlot() {
        policy(MonitorRule.RANDOMISED,true);
        var level = GameServices.level();
        int initial = GameServices.gameState().getLives();
        level.setFrameCounter(1); // tails_1up belongs to the absent P2 slot.
        var tails = icon(); for(int frame=0;frame<40;frame++) tails.update(frame,player);
        assertEquals(2,tails.selectedSubtype());
        assertEquals(initial,GameServices.gameState().getLives());
        level.setFrameCounter(0); // sonic_1up still belongs to P1.
        var sonic = icon(); for(int frame=0;frame<40;frame++) sonic.update(frame,player);
        assertEquals(1,sonic.selectedSubtype());
        assertEquals(initial+1,GameServices.gameState().getLives());
    }
}
