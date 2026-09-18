package com.openggf;

import com.openggf.configuration.*;
import com.openggf.control.InputHandler;
import com.openggf.game.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.objects.SozBossWallObjectInstance;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** The debug checkpoint shortcut must cross the production load boundary. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozLastCheckpointShortcutProduction {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest @ValueSource(ints={320,400,800})
    void earlyTempleShortcutReloadsDestinationEventsBeforeBossEntry(int width) {
        var config=SonicConfigurationService.getInstance();config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,WidescreenAspect.NATIVE_4_3.name());
        config.resolveDisplayAspect();config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"tails");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,false);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED,true);
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED,false);
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();TestEnvironment.activeGameplayMode();
        var fixture=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,1)
                .withFreshLevelStartLifecycle().build();
        fixture.stepFrame(false,false,false,false,false);
        var state=S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        assertEquals(0x10,state.events().backgroundRoutine());
        state.lighting().initializeSeamlessDarkness();
        var level=GameServices.level();var oldObjects=level.getObjectManager();
        int lives = GameServices.gameState().getLives();
        var loop=new GameLoop(new InputHandler());
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        try {
            for (int i = 0; i < 60; i++) loop.step();
            var sourceHistory = fixture.gameplayMode().getRewindController();
            assertNotNull(sourceHistory);
            assertTrue(sourceHistory.currentFrame() >= 50, "source room has rewind history");
            var shortcuts=new GameLoopDebugShortcuts(()->GameMode.LEVEL,()->null,(a,b)->{});
            shortcuts.teleportToLastCheckpoint(level,GameServices.sprites(),fixture.camera(),config,"sonic");
            assertTrue(level.hasPendingLevelExit(),"a coordinate-only teleport retains early-room event state");
            boolean reloaded=false;
            for(int i=0;i<1200;i++) {
                fixture.gameplayMode().getFadeManager().update();loop.step();
                if(level.getObjectManager()!=oldObjects){reloaded=true;break;}
            }
            assertTrue(reloaded);
            var destinationHistory = fixture.gameplayMode().getRewindController();
            assertNotNull(destinationHistory);
            assertTrue(destinationHistory.currentFrame() <= 1, "reload resets the rewind timeline");
            assertFalse(destinationHistory.stepBackward(), "source room history must not cross the reload");
            assertEquals(lives, GameServices.gameState().getLives(), "debug reload must not cost a life");
            assertEquals(6,level.getCheckpointState().getLastCheckpointIndex());
            var player=GameServices.camera().getFocusedSprite();
            assertEquals(0x4EC0,player.getCentreX()&65535);
            assertEquals(0x4A8,player.getCentreY()&65535);
            assertEquals(width,GameServices.camera().getWidth());
            // Finish title/fade through the same loop before probing the room threshold.
            for(int i=0;i<240;i++){fixture.gameplayMode().getFadeManager().update();loop.step();}
            state=S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();
            assertEquals(0x20,state.events().backgroundRoutine(),"destination screen init must select the late temple");
            state.lighting().initializeSeamlessDarkness();
            NativePositionOps.writeXPosResetSubpixel(player,0x5000);
            NativePositionOps.writeYPosResetSubpixel(player,0x650);
            GameServices.camera().setY((short)0x500);GameServices.camera().setFrozen(true);
            loop.step();
            assertEquals(0,state.lighting().darknessLevel());
            assertEquals(0x24,state.events().backgroundRoutine());
            assertEquals(8,level.getObjectManager().activeObjectsOfType(SozBossWallObjectInstance.class).size());
            assertTrue(state.events().artJobOrdinal()>=0,"outer shell art must be submitted by real arena entry");
        } finally {loop.closePresence();}
    }
}
