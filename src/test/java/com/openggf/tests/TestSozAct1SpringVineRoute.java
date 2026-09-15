package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.SozSpringVineObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.EnumMap;
import static org.junit.jupiter.api.Assertions.*;

/** Short cold-start traversal; no teleports or imported native gameplay state. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozAct1SpringVineRoute {
    @ParameterizedTest
    @CsvSource({"320,off,tails", "640,off,tails", "320,s1,tails", "320,off,'tails,knuckles'"})
    void ordinaryInputReachesAndLaunchesFromTheFirstPlacedVine(int width, String donor, String followers) {
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
            var builder=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,0).withFreshLevelStartLifecycle();
            if(!donor.equals("off")) builder.withCrossGameDonation(donor);
            var fixture=builder.build();
            assertEquals(width,fixture.camera().getWidth()&0xFFFF);
            assertEquals(followers.split(",").length,GameServices.sprites().getSidekicks().size());
            assertEquals(!donor.equals("s1"),fixture.sprite().getGameRules().playerCapability().spindashEnabled());
            // The native pilot starts input at LFC 35, after the opening fall.
            for(int frame=0;frame<35;frame++) fixture.stepFrame(false,false,false,false,false);
            boolean encountered=false, launched=false;
            int heldFrames=0;
            var registry=fixture.gameplayMode().getRewindRegistry();
            var diagnostic=new StringBuilder();
            for(int frame=0;frame<900;frame++) {
                var before=fixture.sprite().getCentreX() >= 0x250 ? registry.capture() : null;
                boolean jump=frame%70<20;
                fixture.stepFrame(false,false,false,true,jump);
                var player=fixture.sprite();
                if(frame%30==0) diagnostic.append(frame).append(":").append(player.getCentreX())
                        .append(",").append(player.getCentreY()).append(",").append(player.getXSpeed())
                        .append(",").append(player.getYSpeed()).append(" ");
                var vine=GameServices.level().getObjectManager().getActiveObjects().stream()
                        .filter(SozSpringVineObjectInstance.class::isInstance)
                        .map(SozSpringVineObjectInstance.class::cast)
                        .filter(v -> v.getX()==0x298).findFirst();
                if(vine.isPresent() && player.isOnObject() && player.getCentreX()>=0x268
                        && player.getCentreX()<0x2C8 && !player.getAir()) {
                    if(!encountered) verifyRewindStep(fixture,before,jump,"vine acquisition");
                    encountered=true;
                    if(++heldFrames==2) verifyRewindStep(fixture,before,jump,"vine tension");
                }
                if(encountered && player.getXSpeed()==-0xEF0 && player.getYSpeed()==-0xEF0) {
                    verifyRewindStep(fixture,before,jump,"vine launch");
                    launched=true; break;
                }
                assertFalse(player.getDead(),"died before vine launch at frame="+frame+" "+diagnostic);
            }
            assertTrue(encountered,"cold route must land on first vine: "+diagnostic);
            assertTrue(launched,"cold route must cross the vine launch threshold: "+diagnostic);
        } finally {
            CrossGameFeatureProvider.getInstance().resetState();
            config.clearSessionOverrides(); saved.forEach(config::setSessionOverride); config.resolveDisplayAspect();
            SessionManager.clear(); TestEnvironment.activeGameplayMode();
        }
    }

    private static void verifyRewindStep(HeadlessTestFixture fixture, CompositeSnapshot before,
                                         boolean jump, String boundary) {
        assertNotNull(before, boundary + " must have a pre-contact snapshot");
        var registry = fixture.gameplayMode().getRewindRegistry();
        var after = registry.capture();
        assertTrue(before.containsKey("object-manager"));
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            assertSnapshotsEqual(before, registry.capture(), boundary + " restore " + cycle);
            fixture.stepFrame(false, false, false, true, jump);
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
