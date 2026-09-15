package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.SozSolidSpritesObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.EnumMap;
import static org.junit.jupiter.api.Assertions.*;

/** Short positioned production spots, independent of full-act route reachability. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozRouteControllersProduction {
    @ParameterizedTest
    @CsvSource({"0,320,off,tails,0", "0,640,off,tails,0", "0,320,s1,tails,0", "0,320,off,'tails,knuckles',0", "1,320,off,tails,0", "0,320,off,tails,1", "1,320,off,tails,1"})
    void placedSolidSupportsLandingWithRewind(int act, int width, String donor, String followers, int subtype) {
        var config=SonicConfigurationService.getInstance();
        var saved=new EnumMap<SonicConfiguration,Object>(SonicConfiguration.class);
        for(var key:SonicConfiguration.values()) if(config.hasSessionOverride(key)) saved.put(key,config.getConfigValue(key));
        try {
            config.clearSessionOverrides();
            config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
            config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,followers);
            config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,WidescreenAspect.NATIVE_4_3.name());
            config.resolveDisplayAspect();
            config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
            config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,!donor.equals("off"));
            config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE,donor);
            if(donor.equals("s1")) {
                var rom=RomTestUtils.ensureSonic1RomAvailable(); assertNotNull(rom,"S1 donor ROM required");
                config.setSessionOverride(SonicConfiguration.SONIC_1_ROM,rom.getAbsolutePath());
            }
            CrossGameFeatureProvider.getInstance().resetState(); SessionManager.clear();
            TestEnvironment.activeGameplayMode();
            int solidX=act==0 ? (subtype==0 ? 0x2190 : 0x2198) : (subtype==0 ? 0x18F0 : 0xA82);
            int solidY=act==0 ? (subtype==0 ? 0x118 : 0x138) : (subtype==0 ? 0x218 : 0x608);
            int playerX=solidX+(subtype==1 && act==0 ? 24 : 0);
            var builder=HeadlessTestFixture.builder().withZoneAndAct(8,act)
                    .startPosition((short)playerX,(short)(solidY-(subtype==0 ? 24 : 8)-21)).startPositionIsCentre()
                    .withFreshLevelStartLifecycle();
            if(!donor.equals("off")) builder.withCrossGameDonation(donor);
            var fixture=builder.build();
            assertEquals(width,fixture.camera().getWidth()&0xFFFF);
            assertEquals(followers.split(",").length,GameServices.sprites().getSidekicks().size());
            assertEquals(!donor.equals("s1"),fixture.sprite().getGameRules().playerCapability().spindashEnabled());
            var player=fixture.sprite();
            for(int i=0;i<3;i++) fixture.stepFrame(false,false,false,false,false);
            com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(player,playerX);
            com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(player,solidY-(subtype==0 ? 24 : 8)-21);
            player.setOnObject(false);
            player.setAir(true); player.setYSpeed((short)0x100);
            var registry=fixture.gameplayMode().getRewindRegistry();
            boolean landed=false;
            for(int frame=0;frame<90;frame++) {
                var before=registry.capture();
                fixture.stepFrame(false,false,false,false,false);
                var support=GameServices.level().getObjectManager().getRidingObject(player);
                if(support instanceof SozSolidSpritesObjectInstance && support.getSpawn().x()==solidX) {
                    assertFalse(player.getAir());
                    verifyRewindStep(fixture,before,false,false,"solid landing");
                    for(int i=0;i<5;i++) fixture.stepFrame(false,false,false,false,false);
                    assertInstanceOf(SozSolidSpritesObjectInstance.class,
                            GameServices.level().getObjectManager().getRidingObject(player));
                    landed=true; break;
                }
                assertFalse(player.getDead());
            }
            assertTrue(landed,"must land on the placed solid, not nearby terrain; position="
                    +player.getCentreX()+","+player.getCentreY());
        } finally {
            CrossGameFeatureProvider.getInstance().resetState();
            config.clearSessionOverrides(); saved.forEach(config::setSessionOverride); config.resolveDisplayAspect();
            SessionManager.clear(); TestEnvironment.activeGameplayMode();
        }
    }

    @ParameterizedTest
    @CsvSource({"320,sonic", "640,sonic", "320,tails", "320,knuckles"})
    void placedLoopCapturesAndReleasesAtNativeDepthWithRewind(int width,String character) {
        var config=SonicConfigurationService.getInstance();
        var saved=new EnumMap<SonicConfiguration,Object>(SonicConfiguration.class);
        for(var key:SonicConfiguration.values()) if(config.hasSessionOverride(key)) saved.put(key,config.getConfigValue(key));
        try {
            config.clearSessionOverrides();
            config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,character);
            config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"tails");
            config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
            config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,false);
            CrossGameFeatureProvider.getInstance().resetState(); SessionManager.clear();
            TestEnvironment.activeGameplayMode();
            var fixture=HeadlessTestFixture.builder().withZoneAndAct(8,1)
                    .startPosition((short)0xB50,(short)0x130).startPositionIsCentre()
                    .withFreshLevelStartLifecycle().build();
            assertEquals(width,fixture.camera().getWidth()&0xFFFF);
            var player=fixture.sprite();
            for(int i=0;i<3;i++) fixture.stepFrame(false,false,false,false,false);
            com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(player,0xB50);
            com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(player,0x130);
            player.setAir(true); player.setYSpeed((short)0x800);
            var registry=fixture.gameplayMode().getRewindRegistry();
            boolean captured=false,released=false;
            for(int frame=0;frame<80;frame++) {
                var before=registry.capture();
                fixture.stepFrame(false,false,false,false,false);
                if(!captured && player.isObjectControlled()) {
                    assertTrue(player.isOnObject()); assertEquals(14,player.getYRadius());
                    verifyRewindStep(fixture,before,false,false,"loop capture"); captured=true;
                } else if(captured && !player.isObjectControlled()) {
                    assertTrue(player.getCentreY()>=0x350,"native release depth");
                    assertFalse(player.isOnObject());
                    verifyRewindStep(fixture,before,false,false,"loop release"); released=true; break;
                } else if(captured && frame==10) {
                    verifyRewindStep(fixture,before,false,false,"loop held movement");
                }
                assertFalse(player.getDead());
            }
            assertTrue(captured && released,"loop must capture then release; position="
                    +player.getCentreX()+","+player.getCentreY()+" captured="+captured);
        } finally {
            CrossGameFeatureProvider.getInstance().resetState();
            config.clearSessionOverrides(); saved.forEach(config::setSessionOverride); config.resolveDisplayAspect();
            SessionManager.clear(); TestEnvironment.activeGameplayMode();
        }
    }

    private static void verifyRewindStep(HeadlessTestFixture fixture, CompositeSnapshot before,
                                         boolean left, boolean right, String boundary) {
        assertNotNull(before, boundary + " must have a pre-contact snapshot");
        var registry = fixture.gameplayMode().getRewindRegistry();
        var after = registry.capture();
        assertTrue(before.containsKey("object-manager"));
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            assertSnapshotsEqual(before, registry.capture(), boundary + " restore " + cycle);
            fixture.stepFrame(false, false, left, right, false);
            assertSnapshotsEqual(after, registry.capture(), boundary + " replay " + cycle);
        }
    }

    private static void assertSnapshotsEqual(CompositeSnapshot expected, CompositeSnapshot actual,
                                             String boundary) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), boundary);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> boundary + " " + key + ": " + differences);
        }
    }

}
