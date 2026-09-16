package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Consecutive real checkpoint death/reloads preserve mixed/duplicate CPU chains and isolate owners. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozTeamCheckpointResetProduction {
    @AfterEach void reset() { SonicConfigurationService.getInstance().clearSessionOverrides();SessionManager.clear(); }

    @ParameterizedTest @CsvSource({"0,'tails,knuckles'", "1,'tails,knuckles'",
            "0,'tails,tails,knuckles,sonic,knuckles,sonic'", "1,'tails,tails,knuckles,sonic,knuckles,sonic'"})
    void mixedAndDuplicateTeamsSurviveTwoConsecutiveCheckpointReloads(int act,String followers) {
        var config=SonicConfigurationService.getInstance();config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,followers);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED,true);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,false);
        com.openggf.game.CrossGameFeatureProvider.getInstance().resetState();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,320);
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED,false);
        SessionManager.clear();TestEnvironment.activeGameplayMode();
        int index=act==0?1:2,x=act==0?0x1A30:0x860,y=act==0?0x428:0x5C8;
        var f=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,act)
                .startPosition((short)(x-24),(short)(y+4)).startPositionIsCentre().withFreshLevelStartLifecycle().build();
        assertTeam(followers);
        for(int i=0;i<60&&!GameServices.level().getCheckpointState().isActive();i++)
            f.stepFrame(false,false,false,true,false);
        assertEquals(index,GameServices.level().getCheckpointState().getLastCheckpointIndex(),
                "the leader must physically touch the placed starpost");
        var input=new InputHandler();
        var neutral=new com.openggf.debug.playback.Bk2FrameInput(0,0,0,false,"");
        input.setLogicalOverride(com.openggf.debug.playback.RecordedInputSnapshots.fromBk2(neutral,neutral));
        var loop=new GameLoop(input);loop.setGameplayMode(f.gameplayMode());loop.setGameMode(GameMode.LEVEL);
        try {
            for(int cycle=0;cycle<2;cycle++) {
                // Build a fresh outgoing timeline after each reload, without resetting the session.
                for(int i=0;i<30;i++){f.gameplayMode().getFadeManager().update();loop.step();}
                var objects=GameServices.level().getObjectManager();
                var runtime=assertInstanceOf(SozZoneRuntimeState.class,GameServices.zoneRuntimeRegistry().current());
                // Explicit stale-owner stimulus: this SST pointer must not survive the death load.
                runtime.publishPushableRockSlot(0x55);
                assertTrue(GameServices.sprites().getMainPlayable().applyPitDeath());
                int largestOldFrame=0;boolean loaded=false;
                for(int i=0;i<1200;i++) {
                    f.gameplayMode().getFadeManager().update();loop.step();
                    var rewind=f.gameplayMode().getRewindController();
                    if(GameServices.level().getObjectManager()!=objects) {
                        loaded=true;
                        assertEquals(8,GameServices.level().getCurrentZone());assertEquals(act,GameServices.level().getCurrentAct());
                        assertEquals(index,GameServices.level().getCheckpointState().getLastCheckpointIndex());
                        assertEquals(x,GameServices.sprites().getMainPlayable().getCentreX()&65535);
                        assertEquals(y,GameServices.sprites().getMainPlayable().getCentreY()&65535);
                        var next=assertInstanceOf(SozZoneRuntimeState.class,GameServices.zoneRuntimeRegistry().current());
                        assertNotSame(runtime,next);assertEquals(-1,next.pushableRockSlot());
                        assertTrue(largestOldFrame>10,"each death cycle must have its own outgoing history");
                        assertTrue(rewind==null||rewind.currentFrame()<largestOldFrame,"each load must reset the outgoing timeline");
                        assertTeam(followers);break;
                    }
                    if(rewind!=null)largestOldFrame=Math.max(largestOldFrame,rewind.currentFrame());
                }
                assertTrue(loaded,"GameLoop must reload cycle "+cycle);
                boolean ready=false;
                for(int i=0;i<500;i++) {
                    f.gameplayMode().getFadeManager().update();loop.step();
                    var title=GameServices.module().getTitleCardProvider();
                    if(loop.getCurrentGameMode()==GameMode.LEVEL&&(title==null||title.isComplete())
                            && !f.gameplayMode().getFadeManager().isActive()
                            && !GameServices.sprites().getMainPlayable().isControlLocked()) {ready=true;break;}
                }
                assertTrue(ready,"native title/fade must release controls after cycle "+cycle);
                assertTeam(followers);assertFalse(GameServices.sprites().getMainPlayable().getDead());
            }
        } finally {loop.closePresence();}
    }

    private static void assertTeam(String names) {
        assertEquals(320,GameServices.camera().getWidth()&65535);
        assertFalse(com.openggf.game.CrossGameFeatureProvider.isActive());
        String[] expected=names.split(",");var manager=GameServices.sprites();
        var followers=manager.getRegisteredSidekicks();assertEquals(expected.length,followers.size());
        var previous=manager.getMainPlayable();assertEquals("sonic",previous.getCode());
        var identities=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<com.openggf.sprites.playable.AbstractPlayableSprite,Boolean>());
        identities.add(previous);
        for(int i=0;i<expected.length;i++) {
            var follower=followers.get(i);assertTrue(identities.add(follower),"duplicate characters require independent sprites");
            assertEquals(expected[i],manager.getSidekickCharacterName(follower));assertTrue(follower.isCpuControlled());
            assertSame(previous,follower.getCpuController().getLeader(),"CPU follows the immediately preceding live slot");
            previous=follower;
        }
    }
}
