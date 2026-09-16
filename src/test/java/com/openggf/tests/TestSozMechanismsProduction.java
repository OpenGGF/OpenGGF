package com.openggf.tests;

import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.rewind.*;
import com.openggf.game.sonic3k.objects.*;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.EnumMap;
import static org.junit.jupiter.api.Assertions.*;

/** Positioned entry to a connected placed puzzle; all subsequent travel uses controller input. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozMechanismsProduction {
    @ParameterizedTest
    @CsvSource({"320,sonic,tails,off", "640,sonic,tails,off", "320,tails,none,off", "320,knuckles,none,off", "320,sonic,'tails,knuckles',off", "320,sonic,tails,s1"})
    void pushSwitchOpensPlacedDoorAndPlayerTraversesWithRewind(int width,String character,String followers,String donor) {
        var config=SonicConfigurationService.getInstance();
        var saved=new EnumMap<SonicConfiguration,Object>(SonicConfiguration.class);
        for(var key:SonicConfiguration.values()) if(config.hasSessionOverride(key)) saved.put(key,config.getConfigValue(key));
        try {
            config.clearSessionOverrides();
            config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,character);
            config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,followers.equals("none")?"":followers);
            config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
            config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,false);
            CrossGameFeatureProvider.getInstance().resetState(); SessionManager.clear();
            TestEnvironment.activeGameplayMode();
            if(donor.equals("s1")) {
                var rom=RomTestUtils.ensureSonic1RomAvailable();assertNotNull(rom);
                config.setSessionOverride(SonicConfiguration.SONIC_1_ROM,rom.getAbsolutePath());
                config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,true);
                config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE,"s1");
            }
            var builder=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,1)
                    .startPosition((short)0x2600,(short)0x1A4).startPositionIsCentre()
                    .withFreshLevelStartLifecycle();
            if(donor.equals("s1"))builder.withCrossGameDonation("s1");
            var fixture=builder.build();
            assertEquals(width,fixture.camera().getWidth()&0xFFFF);
            assertEquals(followers.equals("none")?0:followers.split(",").length,GameServices.sprites().getSidekicks().size());
            assertEquals(!donor.equals("s1"),fixture.sprite().getGameRules().playerCapability().spindashEnabled());
            var player=fixture.sprite(); player.setAir(true); var registry=fixture.gameplayMode().getRewindRegistry();
            boolean charged=false,opened=false,passed=false,retained=false;
            int jumpPasses=0;
            StringBuilder diagnostic=new StringBuilder();
            for(int frame=0;frame<600;frame++) {
                boolean jump=SozZoneRuntimeState.trigger(8)>=120 || jumpPasses>0 && jumpPasses<20
                        || passed && player.getPushing();
                if(jump) jumpPasses++;
                var before=registry.capture();
                fixture.stepFrame(false,false,false,true,jump);
                var door=GameServices.level().getObjectManager().getActiveObjects().stream()
                        .filter(SozDoorObjectInstance.class::isInstance).map(SozDoorObjectInstance.class::cast)
                        .filter(o -> o.getSpawn().x()==0x268C).findFirst();
                if(frame%30==0) diagnostic.append(frame).append(":").append(player.getCentreX()).append(",")
                        .append(player.getCentreY()).append(" air=").append(player.getAir()).append(" push=").append(player.getPushing()).append(" control=").append(player.isObjectControlled()).append(" trigger=").append(SozZoneRuntimeState.trigger(8))
                        .append(" door=").append(door.map(SozDoorObjectInstance::getY).orElse(-1)).append("; ");
                if(!charged && SozZoneRuntimeState.trigger(8)>10) {
                    replay(fixture,before,jump,"switch charge");charged=true;
                }
                if(!opened && door.isPresent() && door.get().getY()>=0x1C0+80) {
                    replay(fixture,before,jump,"door opening");opened=true;
                }
                if(opened && !passed && player.getCentreX()>0x26B0) {
                    replay(fixture,before,jump,"door traversal");passed=true;
                }
                if(passed) {
                    var switchOwner=GameServices.level().getObjectManager().getActiveObjects().stream()
                            .filter(SozPushSwitchObjectInstance.class::isInstance).map(SozPushSwitchObjectInstance.class::cast)
                            .filter(o -> o.getSpawn().x()==0x2630 && !o.isSolidFor(player)).findFirst();
                    if(switchOwner.isPresent()) { replay(fixture,before,jump,"retained offscreen switch");retained=true;break; }
                }
                assertFalse(player.getDead(),diagnostic.toString());
            }
            assertTrue(charged && opened && passed && retained,"connected puzzle flags="+charged+","+opened+","+passed+","+retained+" "+diagnostic);
        } finally {
            CrossGameFeatureProvider.getInstance().resetState();config.clearSessionOverrides();
            saved.forEach(config::setSessionOverride);config.resolveDisplayAspect();
            SessionManager.clear();TestEnvironment.activeGameplayMode();
        }
    }
    @ParameterizedTest
    @CsvSource({"0,0x8E0,0x670", "1,0x303F,0x2C0"})
    void movingPlacedPillarSupportsAndCarriesWithRewind(int act,int x,int y) {
        var config=SonicConfigurationService.getInstance();
        var saved=new EnumMap<SonicConfiguration,Object>(SonicConfiguration.class);
        for(var key:SonicConfiguration.values()) if(config.hasSessionOverride(key)) saved.put(key,config.getConfigValue(key));
        try {
            config.clearSessionOverrides();config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
            config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"tails");
            config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,320);
            config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,false);
            CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();TestEnvironment.activeGameplayMode();
            var fixture=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,act)
                    .startPosition((short)x,(short)(y-160)).startPositionIsCentre().withFreshLevelStartLifecycle().build();
            fixture.sprite().setAir(true);
            assertFalse(fixture.sprite().getDead(),"alive after positioned fixture construction");
            for(int i=0;i<3;i++) {
                fixture.stepFrame(false,false,false,false,false);
                assertFalse(fixture.sprite().getDead(),"pillar setup warmup "+i);
            }
            var manager=GameServices.level().getObjectManager();
            var pillar=manager.getActiveObjects().stream().filter(SozFloatingPillarObjectInstance.class::isInstance)
                    .map(SozFloatingPillarObjectInstance.class::cast).filter(o -> o.getSpawn().x()==x).findFirst().orElseThrow();
            var player=fixture.sprite();
            com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(player,pillar.getX());
            com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(player,pillar.getY()-85);
            player.setAir(true);player.setOnObject(false);player.setYSpeed((short)0x100);
            var registry=fixture.gameplayMode().getRewindRegistry();
            boolean landed=false,moved=false;
            int initialX=0,initialY=0;
            for(int frame=0;frame<150;frame++) {
                var before=registry.capture();fixture.stepFrame(false,false,false,false,false);
                var support=manager.getRidingObject(player);
                if(support instanceof SozFloatingPillarObjectInstance && support.getSpawn().x()==x) {
                    if(!landed) { replayNeutral(fixture,before,"pillar landing");landed=true;initialX=player.getCentreX();initialY=player.getCentreY(); }
                    else if(Math.abs(player.getCentreX()-initialX)+Math.abs(player.getCentreY()-initialY)>=8) {
                        replayNeutral(fixture,before,"pillar carry");moved=true;break;
                    }
                }
                assertFalse(player.getDead(),"frame="+frame+" player="+player.getCentreX()+","+player.getCentreY()
                        +" pillar="+pillar.getX()+","+pillar.getY()+" landed="+landed+" support="+support);
            }
            assertTrue(landed && moved,"placed moving pillar must carry its rider: "+player.getCentreX()+","+player.getCentreY());
        } finally {
            CrossGameFeatureProvider.getInstance().resetState();config.clearSessionOverrides();saved.forEach(config::setSessionOverride);
            config.resolveDisplayAspect();SessionManager.clear();TestEnvironment.activeGameplayMode();
        }
    }
    @Test void placedSpecialRockPublishesSlotAndInvalidatesAfterFall() {
        var config=SonicConfigurationService.getInstance();
        var saved=new EnumMap<SonicConfiguration,Object>(SonicConfiguration.class);
        for(var key:SonicConfiguration.values()) if(config.hasSessionOverride(key))saved.put(key,config.getConfigValue(key));
        try {
            config.clearSessionOverrides();config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
            config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"tails");
            config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,320);
            config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,false);
            CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();TestEnvironment.activeGameplayMode();
            var fixture=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,1)
                    .startPosition((short)0x4754,(short)0x5AC).startPositionIsCentre().withFreshLevelStartLifecycle().build();
            var player=fixture.sprite();var registry=fixture.gameplayMode().getRewindRegistry();
            player.setRingCount(99);
            // Settle production-owned player effects before the first rewind spot.
            for(int i=0;i<3;i++)fixture.stepFrame(false,false,false,false,false);
            var state=assertInstanceOf(SozZoneRuntimeState.class,GameServices.zoneRuntimeRegistry().current());
            assertTrue(state.pushableRockSlot()>=0,"placed special rock publishes its SST slot");
            assertFalse(player.getAir(), "positioned start must stand on the actual ledge beside the rock");
            assertTrue(Math.abs(player.getCentreX()-0x4770)<0x30, "start must reach the native rock push face");
            boolean published=false,invalidated=false,pushed=false;StringBuilder diagnostic=new StringBuilder();
            for(int frame=0;frame<500;frame++) {
                var before=registry.capture();fixture.stepFrame(false,false,false,true,false);
                pushed |= player.getPushing();
                if(frame%100==0)diagnostic.append(frame).append(":").append(player.getCentreX()).append(",")
                        .append(player.getCentreY()).append(" signal=").append(SozZoneRuntimeState.trigger(11)).append("; ");
                if(!published && state.pushableRockSlot()>=0) {
                    replay(fixture,before,false,"special rock publication");published=true;
                } else if(published && state.pushableRockSlot()<0) {
                    replay(fixture,before,false,"fall invalidates special rock link");invalidated=true;break;
                }
                assertFalse(player.getDead(),diagnostic.toString());
            }
            assertTrue(published && invalidated,"placed special rock link lifecycle: "+diagnostic);
            assertTrue(pushed, "real player contact must push the placed rock before it falls");
        } finally {
            CrossGameFeatureProvider.getInstance().resetState();config.clearSessionOverrides();saved.forEach(config::setSessionOverride);
            config.resolveDisplayAspect();SessionManager.clear();TestEnvironment.activeGameplayMode();
        }
    }
    private static void replayNeutral(HeadlessTestFixture fixture,CompositeSnapshot before,String label) {
        var registry=fixture.gameplayMode().getRewindRegistry();var after=registry.capture();
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(before);same(before,registry.capture(),label+" restore");
            fixture.stepFrame(false,false,false,false,false);same(after,registry.capture(),label+" replay");
        }
    }
    private static void replay(HeadlessTestFixture fixture,CompositeSnapshot before,boolean jump,String label) {
        var registry=fixture.gameplayMode().getRewindRegistry();var after=registry.capture();
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(before);same(before,registry.capture(),label+" restore");
            fixture.stepFrame(false,false,false,true,jump);same(after,registry.capture(),label+" replay");
        }
    }
    private static void same(CompositeSnapshot expected,CompositeSnapshot actual,String label) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet(),label);
        for(String key:expected.entries().keySet()) {
            var diff=RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key));
            assertTrue(diff.isEmpty(),label+" "+key+": "+diff);
        }
    }
}
