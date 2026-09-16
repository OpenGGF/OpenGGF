package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Real directional/jump input from arena approach through eight hits, capsule and LRZ. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozEndBossInputRoute {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();}
    static Stream<Arguments> configurations() {
        var rows=new java.util.ArrayList<Arguments>();
        for(String character:new String[]{"sonic","tails","knuckles"})
            for(int width:new int[]{320,400,512,640,800})
                for(String donor:new String[]{"off","s1","s2"}) {
                    if(!SozAcceptanceConfigurations.supportsCharacter(donor,character))continue;
                    rows.add(Arguments.of(character,width,donor));
                }
        return rows.stream();
    }
    @ParameterizedTest @MethodSource("configurations")
    void controllerOnlyBattleAndCapsuleReachLrzWithGraphRewind(String character,int width,String donor){
        var config=SonicConfigurationService.getInstance();config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,character);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED,false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,"NATIVE_4_3");
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,!donor.equals("off"));
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE,donor);
        if(!donor.equals("off")) {
            var rom=donor.equals("s1")?RomTestUtils.ensureSonic1RomAvailable():RomTestUtils.ensureSonic2RomAvailable();
            config.setSessionOverride(donor.equals("s1")?SonicConfiguration.SONIC_1_ROM:SonicConfiguration.SONIC_2_ROM,rom.getAbsolutePath());
        }
        com.openggf.game.CrossGameFeatureProvider.getInstance().resetState();
        com.openggf.game.session.SessionManager.clear();TestEnvironment.activeGameplayMode();
        var builder=HeadlessTestFixture.builder().withZoneAndAct(8,1)
                .startPosition((short)0x51C0,(short)0x620).startPositionIsCentre()
                .withFreshLevelStartLifecycle();
        if(!donor.equals("off"))builder.withCrossGameDonation(donor);
        var f=builder.build();
        SozAcceptanceConfigurations.assertUsableTeam(donor);
        assertEquals(character,f.sprite().getCode());
        assertEquals(width,GameServices.camera().getWidth());
        assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
        assertEquals(!donor.equals("off"),com.openggf.game.CrossGameFeatureProvider.isActive());
        if(!donor.equals("off"))assertEquals(donor,com.openggf.game.CrossGameFeatureProvider.getInstance().getDonorGameId());
        assertEquals(!donor.equals("s1"),f.sprite().getGameRules().playerCapability().spindashEnabled());
        f.sprite().setRingCount(99);
        var route=character.equals("knuckles")
                ? (donor.equals("off")?new SozEndBossVictoryRoute(40,4,32):new SozEndBossVictoryRoute(16,4,-48))
                : character.equals("tails")&&donor.equals("off")?new SozEndBossVictoryRoute(32,20,-32)
                : new SozEndBossVictoryRoute();
        var registry=f.gameplayMode().getRewindRegistry();
        var milestones=new java.util.LinkedHashSet<String>();
        int hits=0;StringBuilder trail=new StringBuilder();
        var previousInput=new com.openggf.debug.playback.Bk2FrameInput(-1,0,0,false,"");
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
                f.runner().primeInputState(previousInput);
                step(f,input);same(after,registry.capture(),milestone+" replay");
            }
            previousInput=input;
            if(milestones.containsAll(java.util.Set.of("shell","capsule","results","walk","redraw1","redraw2","redraw8")))break;
        }
        assertEquals(8,hits,trail.toString());
        assertTrue(milestones.containsAll(java.util.Set.of("shell","capsule","results","walk","redraw1","redraw2","redraw8")),trail.toString());
        assertEquals(8,GameServices.level().getCurrentZone(),"hand the pending load to GameLoop: "+trail);
        assertEquals(1,GameServices.level().getCurrentAct());
        assertTrue(f.gameplayMode().isGameplayRuntimeReady());
        var input=new com.openggf.control.InputHandler();
        var neutral=new com.openggf.debug.playback.Bk2FrameInput(0,0,0,false,"");
        input.setLogicalOverride(com.openggf.debug.playback.RecordedInputSnapshots.fromBk2(neutral,neutral));
        var loop=new com.openggf.GameLoop(input);
        loop.setGameplayMode(f.gameplayMode());loop.setGameMode(com.openggf.game.GameMode.LEVEL);
        try {
            boolean ready=false;
            for(int tick=0;tick<2000;tick++) {
                f.gameplayMode().getFadeManager().update();loop.step();
                var title=GameServices.module().getTitleCardProvider();
                if(GameServices.level().getCurrentZone()==9
                        && loop.getCurrentGameMode()==com.openggf.game.GameMode.LEVEL
                        && (title==null||title.isComplete())
                        && !f.gameplayMode().getFadeManager().isActive()
                        && !GameServices.camera().getFocusedSprite().isControlLocked()) {
                    ready=true;break;
                }
            }
            assertTrue(ready,"the real LRZ title/fade must release playable controls");
            SozAcceptanceConfigurations.assertUsableTeam(donor);
            assertEquals(9,GameServices.level().getCurrentZone());
            assertEquals(0,GameServices.level().getCurrentAct());
            assertEquals(character,GameServices.sprites().getMainPlayable().getCode());
            assertEquals(width,GameServices.camera().getWidth());
            assertFalse(GameServices.sprites().getMainPlayable().getDead());
            assertEquals(!donor.equals("s1"),GameServices.sprites().getMainPlayable().getGameRules().playerCapability().spindashEnabled());
        } finally {loop.closePresence();}
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
