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
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Physical SOZ starpost activation followed by the production death/reload loop. */
@RequiresRom(SonicGame.SONIC_3K)
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class TestSozCheckpointReloadProduction {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();SessionManager.clear();}
    static Stream<Arguments> scenarios() {
        var rows = new java.util.ArrayList<Arguments>();
        for (int act : new int[]{0, 1}) for (String character : new String[]{"sonic", "tails", "knuckles"})
            for (var aspect : WidescreenAspect.values()) for (String donor : new String[]{"off", "s1", "s2"})
                if(SozAcceptanceConfigurations.supportsCharacter(donor,character))
                    rows.add(Arguments.of(act, act == 0 ? 1 : 2, act == 0 ? 0x1A30 : 0x860,
                        act == 0 ? 0x428 : 0x5C8, character, aspect, donor));
        return rows.stream();
    }
    static Stream<Arguments> remainingPosts() {
        int[][] posts={{0,2,0x1780,0x708},{0,3,0x2760,0x228},{0,4,0x2C60,0x628},{0,5,0x3F00,0x6E8},
                {1,3,0x13F0,0x428},{1,4,0x1F00,0x108},{1,5,0x3280,0x1A8},{1,6,0x4EC0,0x4A8}};
        var rows=new java.util.ArrayList<Arguments>();
        for(var post:posts)for(String character:new String[]{"sonic","tails","knuckles"})
            for(var aspect:WidescreenAspect.values())for(String donor:new String[]{"off","s1","s2"})
                if(SozAcceptanceConfigurations.supportsCharacter(donor,character))
                    rows.add(Arguments.of(post[0],post[1],post[2],post[3],character,aspect,donor));
        return rows.stream();
    }
    static Stream<Arguments> shortScenarios() {
        return Stream.of(Arguments.of(0,1,0x1A30,0x428,"sonic",WidescreenAspect.NATIVE_4_3,"off"),
                Arguments.of(1,2,0x860,0x5C8,"knuckles",WidescreenAspect.NATIVE_4_3,"off"),
                Arguments.of(0,1,0x1A30,0x428,"sonic",WidescreenAspect.SUPER_32_9,"s1"),
                Arguments.of(1,2,0x860,0x5C8,"tails",WidescreenAspect.SUPER_32_9,"s2"));
    }
    @org.junit.jupiter.api.Order(1)
    @ParameterizedTest @MethodSource("shortScenarios")
    void shortSoloCheckpointsReplayReleasedIncomingWorlds(int act,int index,int x,int y,String character,WidescreenAspect aspect,String donor) {
        touchCheckpointThenDeathReloadsItsNativePosition(act,index,x,y,character,aspect,donor);
    }
    @org.junit.jupiter.api.Order(20)
    @ParameterizedTest @MethodSource("remainingPosts")
    void otherPlacedPostsActivateAndReload(int act,int index,int x,int y,String character,WidescreenAspect aspect,String donor) {
        assertAll(() -> touchCheckpointThenDeathReloadsItsNativePosition(act,index,x,y,character,aspect,donor));
    }
    @org.junit.jupiter.api.Order(20)
    @ParameterizedTest @MethodSource("scenarios")
    void touchCheckpointThenDeathReloadsItsNativePosition(int act,int index,int x,int y,String character,
            WidescreenAspect aspect,String donor){
        var config=SonicConfigurationService.getInstance();config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,character);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED,true);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED,false);
        SozAcceptanceConfigurations.selectDisplay(aspect);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,!donor.equals("off"));
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE,donor);
        if (!donor.equals("off")) {
            var rom = donor.equals("s1") ? RomTestUtils.ensureSonic1RomAvailable() : RomTestUtils.ensureSonic2RomAvailable();
            assertNotNull(rom,"required donor ROM");
            config.setSessionOverride(donor.equals("s1") ? SonicConfiguration.SONIC_1_ROM : SonicConfiguration.SONIC_2_ROM,rom.getAbsolutePath());
        }
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();TestEnvironment.activeGameplayMode();
        var builder=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,act)
                .startPosition((short)(x-24),(short)(y+4)).startPositionIsCentre().withFreshLevelStartLifecycle();
        if(!donor.equals("off"))builder.withCrossGameDonation(donor);
        var f=builder.build();
        SozAcceptanceConfigurations.assertUsableTeam(donor);
        SozAcceptanceConfigurations.assertDisplay(aspect);
        assertEquals(!donor.equals("off"),CrossGameFeatureProvider.isActive());
        if(!donor.equals("off"))assertEquals(donor,CrossGameFeatureProvider.getInstance().getDonorGameId());
        assertEquals(!donor.equals("s1"),f.sprite().getGameRules().playerCapability().spindashEnabled());
        f.stepIdleFrames(1);
        var manager=GameServices.level().getObjectManager();var p=f.sprite();
        assertFalse(GameServices.level().getCheckpointState().isActive());
        for(int i=0;i<60&&!GameServices.level().getCheckpointState().isActive();i++) {
            var registry=f.gameplayMode().getRewindRegistry();var before=registry.capture();
            f.stepFrame(false,false,false,true,false);
            if(GameServices.level().getCheckpointState().isActive()) {
                var after=registry.capture();manager.setRewindInPlaceRestoreEnabledForTest(false);
                for(int cycle=0;cycle<2;cycle++) {
                    registry.restore(before);same(before,registry.capture());
                    f.runner().primeInputState(new com.openggf.debug.playback.Bk2FrameInput(0,0,0,false,""));
                    f.stepFrame(false,false,false,true,false);same(after,registry.capture());
                }
            }
        }
        assertEquals(index,GameServices.level().getCheckpointState().getLastCheckpointIndex(),
                "walking into the real placed post must activate it");
        assertFalse(p.getDead());
        var input=new InputHandler();
        var neutral=new com.openggf.debug.playback.Bk2FrameInput(0,0,0,false,"");
        input.setLogicalOverride(com.openggf.debug.playback.RecordedInputSnapshots.fromBk2(neutral,neutral));
        var loop=new GameLoop(input);loop.setGameplayMode(f.gameplayMode());loop.setGameMode(GameMode.LEVEL);
        assertTrue(loop.ownsGameplayFadeLifecycle());
        try {
            for(int cycle=0;cycle<2;cycle++) {
                for(int frame=0;frame<30;frame++)step(loop);
                var outgoingObjects=GameServices.level().getObjectManager();
                var runtime=GameServices.zoneRuntimeRegistry().current();
                int lives=GameServices.gameState().getLives();
                assertTrue(GameServices.sprites().getMainPlayable().applyPitDeath());
                boolean reloaded=false;int largestOutgoingFrame=0;
                for(int i=0;i<1200;i++) {
                    step(loop);
                    var rewind=f.gameplayMode().getRewindController();
                    if(GameServices.level().getObjectManager()!=outgoingObjects) {
                        reloaded=true;assertEquals(8,GameServices.level().getCurrentZone());assertEquals(act,GameServices.level().getCurrentAct());
                        assertEquals(index,GameServices.level().getCheckpointState().getLastCheckpointIndex());
                        assertEquals(x,GameServices.camera().getFocusedSprite().getCentreX()&65535);
                        assertEquals(y,GameServices.camera().getFocusedSprite().getCentreY()&65535);
                        assertEquals(lives-1,GameServices.gameState().getLives(),"exact life loss at assembly");
                        assertNotSame(runtime,GameServices.zoneRuntimeRegistry().current());
                        assertTrue(largestOutgoingFrame>10,"actual outgoing history");
                        assertTrue(rewind==null||rewind.currentFrame()<largestOutgoingFrame,"isolated incoming history");
                        assertOwners(character,aspect,donor);break;
                    }
                    if(rewind!=null)largestOutgoingFrame=Math.max(largestOutgoingFrame,rewind.currentFrame());
                }
                assertTrue(reloaded,"native death countdown must reach GameLoop reload");
                boolean released=false;
                for(int frame=0;frame<500;frame++) {
                    step(loop);var title=GameServices.module().getTitleCardProvider();
                    if(loop.getCurrentGameMode()==GameMode.LEVEL&&(title==null||title.isComplete())
                            &&!f.gameplayMode().getFadeManager().isActive()
                            &&!f.gameplayMode().getFadeManager().hasPendingCompletion()
                            &&!GameServices.level().hasPendingFreshLevelTransitionBoundary()
                            &&!GameServices.sprites().getMainPlayable().isControlLocked()) {released=true;break;}
                }
                assertTrue(released,"actual title/fade/control release");
                assertEquals(lives-1,GameServices.gameState().getLives(),"release hides no restart");
                CheckpointIncomingReplay.verify(f,loop,input,index,()->assertOwners(character,aspect,donor));
            }
        }finally{loop.closePresence();}
    }
    private static void step(GameLoop loop) {
        loop.step();GameServices.graphics().runPendingRenderThreadTasks();
    }
    private static void assertOwners(String character,WidescreenAspect aspect,String donor) {
        var player=GameServices.sprites().getMainPlayable();
        assertSame(player,GameServices.camera().getFocusedSprite());assertFalse(player.getDead());
        assertEquals(character,player.characterKey().persisted());
        assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
        SozAcceptanceConfigurations.assertDisplay(aspect);SozAcceptanceConfigurations.assertUsableTeam(donor);
        assertEquals(!donor.equals("off"),CrossGameFeatureProvider.isActive());
        if(!donor.equals("off"))assertEquals(donor,CrossGameFeatureProvider.getInstance().getDonorGameId());
        assertEquals(!donor.equals("s1"),player.getGameRules().playerCapability().spindashEnabled());
    }
    private static void same(com.openggf.game.rewind.CompositeSnapshot expected,
            com.openggf.game.rewind.CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for(var key:expected.entries().keySet()) {
            var diff=com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key));
            assertTrue(diff.isEmpty(),key+": "+diff);
        }
    }
}
