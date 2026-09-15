package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Physical SOZ starpost activation followed by the production death/reload loop. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozCheckpointReloadProduction {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();SessionManager.clear();}
    @ParameterizedTest @CsvSource({"0,1,0x1A30,0x428,sonic", "0,1,0x1A30,0x428,tails", "0,1,0x1A30,0x428,knuckles",
            "1,2,0x860,0x5C8,sonic", "1,2,0x860,0x5C8,tails", "1,2,0x860,0x5C8,knuckles"})
    void touchCheckpointThenDeathReloadsItsNativePosition(int act,int index,int x,int y,String character){
        var config=SonicConfigurationService.getInstance();config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,character);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED,false);
        SessionManager.clear();TestEnvironment.activeGameplayMode();
        var f=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,act)
                .startPosition((short)(x-24),(short)(y+4)).startPositionIsCentre().withFreshLevelStartLifecycle().build();
        var manager=GameServices.level().getObjectManager();var p=f.sprite();
        assertFalse(GameServices.level().getCheckpointState().isActive());
        for(int i=0;i<60&&!GameServices.level().getCheckpointState().isActive();i++) {
            var registry=f.gameplayMode().getRewindRegistry();var before=registry.capture();
            f.stepFrame(false,false,false,true,false);
            if(GameServices.level().getCheckpointState().isActive()) {
                var after=registry.capture();manager.setRewindInPlaceRestoreEnabledForTest(false);
                registry.restore(before);same(before,registry.capture());
                f.stepFrame(false,false,false,true,false);same(after,registry.capture());
            }
        }
        assertEquals(index,GameServices.level().getCheckpointState().getLastCheckpointIndex(),
                "walking into the real placed post must activate it");
        assertFalse(p.getDead());
        assertTrue(p.applyPitDeath());
        var loop=new GameLoop(new InputHandler());loop.setGameplayMode(f.gameplayMode());loop.setGameMode(GameMode.LEVEL);
        try {
            boolean reloaded=false;
            for(int i=0;i<1200;i++){
                f.gameplayMode().getFadeManager().update();loop.step();
                if(GameServices.level().getObjectManager()!=manager){
                    reloaded=true;assertEquals(8,GameServices.level().getCurrentZone());assertEquals(act,GameServices.level().getCurrentAct());
                    assertEquals(index,GameServices.level().getCheckpointState().getLastCheckpointIndex());
                    assertEquals(x,GameServices.camera().getFocusedSprite().getCentreX()&65535);
                    assertEquals(y,GameServices.camera().getFocusedSprite().getCentreY()&65535);
                    assertFalse(GameServices.camera().getFocusedSprite().getDead());break;
                }
            }
            assertTrue(reloaded,"native death countdown must reach GameLoop reload");
        }finally{loop.closePresence();}
    }
    private static void same(com.openggf.game.rewind.CompositeSnapshot expected,
            com.openggf.game.rewind.CompositeSnapshot actual) {
        for(var key:expected.entries().keySet()) {
            var diff=com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key));
            assertTrue(diff.isEmpty(),key+": "+diff);
        }
    }
}
