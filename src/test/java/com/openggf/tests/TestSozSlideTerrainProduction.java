package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Real SOZ layout lookup and loop-tail dispatch, including all registered player slots. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozSlideTerrainProduction {
    @AfterEach void reset() { SonicConfigurationService.getInstance().clearSessionOverrides(); SessionManager.clear(); }

    @ParameterizedTest @CsvSource({"0,0","0,4096","1,0","1,2048"})
    void terrainDispatcherUpdatesEveryPlayerAndReplaysCompositeState(int act, int wrappedOffset) {
        var config=SonicConfigurationService.getInstance(); config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"tails,sonic");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED,false);
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var f=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,act)
                .withFreshLevelStartLifecycle().build();
        f.stepFrame(false,false,false,false,false);
        var level=GameServices.level(); var map=level.getCurrentLevel().getMap();
        int slideX=-1,slideY=-1;
        outer:for(int y=0;y<map.getHeight();y++)for(int x=0;x<map.getWidth();x++) {
            if(level.getBlockIdAt(x*128,y*128)==0x77) {slideX=x*128+32;slideY=y*128;break outer;}
        }
        assertTrue(slideX>=0,"each shipped SOZ act must contain the positive full-chunk sand slide");
        slideY += wrappedOffset;
        var players=new ArrayList<AbstractPlayableSprite>();players.add(f.sprite());
        players.addAll(GameServices.sprites().getRegisteredSidekicks());assertEquals(3,players.size());
        for(var player:players) {
            NativePositionOps.writeXPosResetSubpixel(player,slideX);
            NativePositionOps.writeYPosResetSubpixel(player,slideY-0x14);
            player.setAir(false);player.setOnObject(false);player.setSliding(false);
            player.applyCustomRadii(9,19);player.setGSpeed((short)0x3CC);
        }
        var registry=f.gameplayMode().getRewindRegistry();var before=registry.capture();
        level.updateZoneFeaturesAfterObjectExecution();
        for(var player:players) assertFalse(player.isSliding(), "terrain must wait until after camera/events");
        GameServices.module().getZoneFeatureProvider().update(f.sprite(),GameServices.camera().getX(),8);
        var after=registry.capture();
        for(var player:players) {
            assertTrue(player.isSliding());assertEquals(0x40C,player.getGSpeed());
            assertEquals(slideY-0x14+5,player.getCentreY());assertEquals(14,player.getYRadius());
            assertEquals(0x19,player.getAnimationId());
        }
        level.getObjectManager().setRewindInPlaceRestoreEnabledForTest(false);
        registry.restore(before);same(before,registry.capture());
        GameServices.module().getZoneFeatureProvider().update(f.sprite(),GameServices.camera().getX(),8);same(after,registry.capture());
        if (wrappedOffset == 0) {
            registry.restore(before);
            var camera=GameServices.camera();
            int preSlideY=f.sprite().getCentreY();
            camera.setFrozen(false);camera.setLevelStarted(true);
            camera.setMinY((short)0);camera.setMaxY((short)0xFFF);
            camera.setY((short)(preSlideY-96));
            var frameBefore=registry.capture();
            Runnable frame=()->com.openggf.LevelFrameTestStep.execute(
                    com.openggf.LevelFrameContext.from(f.gameplayMode()),level,camera,()->{},(name,step)->{
                        if(name.equals("camera-scroll")) {
                            assertFalse(f.sprite().isSliding(),"camera must sample before sub_730C");
                            assertEquals(preSlideY,f.sprite().getCentreY());
                        }
                        step.run();
                        if(name.equals("camera-scroll")) assertEquals(preSlideY-96,camera.getY(),
                                "the grounded camera tracks the unadjusted player centre");
                    });
            frame.run();
            assertTrue(f.sprite().isSliding());assertEquals(preSlideY+5,f.sprite().getCentreY());
            assertEquals(preSlideY-96,camera.getY(),"slide Y adjustment cannot move this frame's camera");
            var frameAfter=registry.capture();registry.restore(frameBefore);
            same(frameBefore,registry.capture());frame.run();same(frameAfter,registry.capture());
        }
    }
    private static void same(com.openggf.game.rewind.CompositeSnapshot expected,
            com.openggf.game.rewind.CompositeSnapshot actual) {
        for(var key:expected.entries().keySet()) {
            var diff=com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key));
            assertTrue(diff.isEmpty(),()->key+": "+diff);
        }
    }
}
