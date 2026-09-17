package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.*;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSozAct1VictoryProduction {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();}
    static Stream<Arguments> configurations() {
        var rows=new java.util.ArrayList<Arguments>();
        for(String character:new String[]{"sonic","tails","knuckles"})
            for(int width:new int[]{320,400,512,640,800})
                for(String donor:new String[]{"off","s1","s2"})
                    if(SozAcceptanceConfigurations.supportsCharacter(donor,character))
                        rows.add(Arguments.of(character,width,donor));
        return rows.stream();
    }
    @ParameterizedTest @MethodSource("configurations")
    void positionedApproachLuresGolemIntoSandThenEntersAct2(String character,int width,String donor){
        var config=SonicConfigurationService.getInstance();config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,character);
        String followers=character.equals("sonic")&&!donor.equals("s1")?"tails":"";
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,followers);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,"NATIVE_4_3");
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,!donor.equals("off"));
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE,donor);
        if(!donor.equals("off")) {
            var rom=donor.equals("s1")?RomTestUtils.ensureSonic1RomAvailable():RomTestUtils.ensureSonic2RomAvailable();
            config.setSessionOverride(donor.equals("s1")?SonicConfiguration.SONIC_1_ROM:SonicConfiguration.SONIC_2_ROM,rom.getAbsolutePath());
        }
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS,true);
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();TestEnvironment.activeGameplayMode();
        var builder=HeadlessTestFixture.builder().withZoneAndAct(8,0).startPosition((short)0x43B0,(short)0x9D4)
                .startPositionIsCentre().withFreshLevelStartLifecycle();
        if(!donor.equals("off"))builder.withCrossGameDonation(donor);
        var f=builder.build();
        SozAcceptanceConfigurations.assertUsableTeam(donor);
        assertEquals(character,f.sprite().getCode());
        assertEquals(width,GameServices.camera().getWidth());
        assertEquals(followers.isEmpty()?0:1,GameServices.sprites().getRegisteredSidekicks().size());
        if(!followers.isEmpty())assertEquals(followers,GameServices.sprites().getSidekickCharacterName(GameServices.sprites().getRegisteredSidekicks().getFirst()));
        assertEquals(!donor.equals("off"),CrossGameFeatureProvider.isActive());
        if(!donor.equals("off"))assertEquals(donor,CrossGameFeatureProvider.getInstance().getDonorGameId());
        assertEquals(!donor.equals("s1"),f.sprite().getGameRules().playerCapability().spindashEnabled());
        f.sprite().setRingCount(99);
        // Test-only controller choice. Once flying Tails lost the Sonic-only
        // insta-shield invulnerability, native Tails at width 320 died at tick 3202 on
        // the right-side approach; the left-side approach completes every Tails row.
        var route=new SozAct1VictoryRoute(character.equals("knuckles")
                || donor.equals("s1") || character.equals("tails"));
        var registry=f.gameplayMode().getRewindRegistry();
        var milestones=new java.util.LinkedHashSet<String>();StringBuilder trail=new StringBuilder();
        var previousInput=new com.openggf.debug.playback.Bk2FrameInput(-1,0,0,false,"");
        for(int tick=0;tick<6000&&GameServices.level().getCurrentAct()==0;tick++) {
            var boss=SozAct1VictoryRoute.boss();var manager=GameServices.level().getObjectManager();
            int phase=boss==null?-1:boss.phase(),routine=boss==null?-1:boss.routine();
            boolean results=GameServices.gameState().isEndOfLevelActive();
            var input=route.input(tick,f.sprite());var before=registry.capture();
            step(f,input);
            assertFalse(f.sprite().getDead(),"player died at "+tick+"\n"+trail);
            if(GameServices.level().getCurrentAct()!=0)break;
            var afterBoss=SozAct1VictoryRoute.boss();String milestone=null;
            if(boss==null&&afterBoss!=null)milestone="admission";
            else if(manager.activeObjectsOfType(SozMinibossChild.class).size()>=10&&!milestones.contains("articulation"))milestone="articulation";
            else if(routine==22&&afterBoss!=null&&afterBoss.routine()==12&&!milestones.contains("attack"))milestone="attack";
            else if(phase!=3&&afterBoss!=null&&afterBoss.phase()==3)milestone="sink";
            else if(phase==3&&afterBoss==null)milestone="signpost";
            else if(!results&&GameServices.gameState().isEndOfLevelActive())milestone="results";
            else if(results&&!GameServices.gameState().isEndOfLevelActive())milestone="alignment";
            else if(com.openggf.game.sonic3k.runtime.S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry())
                    .orElseThrow().events().backgroundRoutine()==0xC&&!milestones.contains("fade"))milestone="fade";
            if(tick%240==0||milestone!=null)trail.append(tick).append(" p=").append(Integer.toHexString(f.sprite().getCentreX()&65535))
                    .append(',').append(Integer.toHexString(f.sprite().getCentreY()&65535))
                    .append(" cam=").append(Integer.toHexString(GameServices.camera().getX()&65535)).append(',').append(Integer.toHexString(GameServices.camera().getY()&65535))
                    .append(" limits=").append(Integer.toHexString(GameServices.camera().getMaxX()&65535)).append(',').append(Integer.toHexString(GameServices.camera().getMaxY()&65535)).append(" phase=").append(phase).append(" routine=").append(routine).append(' ').append(milestone).append('\n');
            if(milestone!=null&&milestones.add(milestone)) {
                var after=registry.capture();manager.setRewindInPlaceRestoreEnabledForTest(false);
                registry.restore(before);same(before,registry.capture(),milestone+" restore");
                f.runner().primeInputState(previousInput);
                step(f,input);same(after,registry.capture(),milestone+" replay");
            }
            previousInput=input;
        }
        assertEquals(1,GameServices.level().getCurrentAct(),"route did not enter Act2\n"+trail);
        assertTrue(route.sinking(),"native positional sink, not a seeded defeat flag, must win the battle");
        assertEquals(java.util.Set.of("admission","articulation","attack","sink","signpost","results","alignment","fade"),milestones,trail.toString());
        boolean ready=false;
        for(int i=0;i<500;i++) {
            f.stepFrame(false,false,false,false,false);
            var state=com.openggf.game.sonic3k.runtime.S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();
            if(!state.events().seamlessEntry()&&!f.sprite().isControlLocked()) {ready=true;break;}
        }
        assertTrue(ready,"the real seamless entry must release player controls");
        for(int line:new int[]{0,2,3}) {
            int palette=0;for(int color=0;color<16;color++)palette|=com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(GameServices.level().getCurrentLevel().getPalette(line).getColor(color));
            assertNotEquals(0,palette,"native entry fade must restore destination palette line "+line);
        }
        assertEquals(8,GameServices.level().getCurrentZone());
        SozAcceptanceConfigurations.assertUsableTeam(donor);
        assertEquals(1,GameServices.level().getCurrentAct());
        var before=registry.capture();f.stepFrame(false,false,false,false,false);var after=registry.capture();
        GameServices.level().getObjectManager().setRewindInPlaceRestoreEnabledForTest(false);
        registry.restore(before);same(before,registry.capture(),"Act2 restore");
        f.stepFrame(false,false,false,false,false);same(after,registry.capture(),"Act2 replay");
    }
    private static void step(HeadlessTestFixture fixture,com.openggf.debug.playback.Bk2FrameInput input) {
        int mask=input.p1InputMask();fixture.stepFrame(false,false,(mask&4)!=0,(mask&8)!=0,(mask&16)!=0);
    }
    private static void same(com.openggf.game.rewind.CompositeSnapshot expected,
            com.openggf.game.rewind.CompositeSnapshot actual,String label) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet(),label);
        for(var key:expected.entries().keySet()) {
            var diff=com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key));
            assertTrue(diff.isEmpty(),label+" "+key+": "+diff);
        }
    }
}
