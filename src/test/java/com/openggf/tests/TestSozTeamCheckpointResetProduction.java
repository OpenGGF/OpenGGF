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
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Consecutive real checkpoint death/reloads preserve mixed/duplicate CPU chains and isolate owners. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozTeamCheckpointResetProduction {
    @AfterEach void reset() { SonicConfigurationService.getInstance().clearSessionOverrides();SessionManager.clear(); }

    static Stream<Arguments> configurations() {
        var rows=new java.util.ArrayList<Arguments>();
        for(int act:new int[]{0,1})for(int width:new int[]{320,400,512,640,800})
            for(String donor:new String[]{"off","s1","s2"})
                for(String followers:new String[]{"tails","tails,knuckles","tails,tails,knuckles,sonic,knuckles,sonic"})
                    rows.add(Arguments.of(act,followers,width,donor));
        return rows.stream();
    }
    @ParameterizedTest @MethodSource("configurations")
    void mixedAndDuplicateTeamsSurviveTwoConsecutiveCheckpointReloads(int act,String followers,int width,String donor) {
        var config=SonicConfigurationService.getInstance();config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,followers);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED,true);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,!donor.equals("off"));
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE,donor);
        if(!donor.equals("off")) {
            var rom=donor.equals("s1")?RomTestUtils.ensureSonic1RomAvailable():RomTestUtils.ensureSonic2RomAvailable();
            config.setSessionOverride(donor.equals("s1")?SonicConfiguration.SONIC_1_ROM:SonicConfiguration.SONIC_2_ROM,rom.getAbsolutePath());
        }
        com.openggf.game.CrossGameFeatureProvider.getInstance().resetState();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,"NATIVE_4_3");
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED,false);
        SessionManager.clear();TestEnvironment.activeGameplayMode();
        int index=act==0?1:2,x=act==0?0x1A30:0x860,y=act==0?0x428:0x5C8;
        var builder=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,act)
                .startPosition((short)(x-24),(short)(y+4)).startPositionIsCentre().withFreshLevelStartLifecycle();
        if(!donor.equals("off"))builder.withCrossGameDonation(donor);
        var f=builder.build();
        assertTeam(followers,width,donor);
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
                        assertTeam(followers,width,donor);break;
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
                assertTeam(followers,width,donor);assertFalse(GameServices.sprites().getMainPlayable().getDead());
            }
        } finally {loop.closePresence();}
    }

    private static void assertTeam(String names,int width,String donor) {
        assertEquals(width,GameServices.camera().getWidth()&65535);
        assertEquals(!donor.equals("off"),com.openggf.game.CrossGameFeatureProvider.isActive());
        if(!donor.equals("off"))assertEquals(donor,com.openggf.game.CrossGameFeatureProvider.getInstance().getDonorGameId());
        assertEquals(!donor.equals("s1"),GameServices.sprites().getMainPlayable().getGameRules().playerCapability().spindashEnabled());
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
