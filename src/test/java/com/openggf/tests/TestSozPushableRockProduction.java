package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.SozPushableRockObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.EnumMap;
import static org.junit.jupiter.api.Assertions.*;

/** Short positioned production spots, independent of full-act route reachability. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozPushableRockProduction {
    @ParameterizedTest
    @CsvSource({"0,320,off,tails", "0,640,off,tails", "0,320,s1,tails", "0,320,off,'tails,knuckles'", "1,320,off,tails"})
    void placedRockPushesFallsAndFollowsRomTrackWithRewind(int act, int width, String donor, String followers) {
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
            int rockX=act==0 ? 0x3E0 : 0x1530;
            int rockY=act==0 ? 0x5F4 : 0x235;
            var builder=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,act)
                    .startPosition((short)(rockX-48),(short)(rockY-8)).startPositionIsCentre()
                    .withFreshLevelStartLifecycle();
            if(!donor.equals("off")) builder.withCrossGameDonation(donor);
            var fixture=builder.build();
            assertEquals(width,fixture.camera().getWidth()&0xFFFF);
            assertEquals(followers.split(",").length,GameServices.sprites().getSidekicks().size());
            assertEquals(!donor.equals("s1"),fixture.sprite().getGameRules().playerCapability().spindashEnabled());
            var player=fixture.sprite();
            var registry=fixture.gameplayMode().getRewindRegistry();
            boolean pushed=false, fell=false, rode=false, stopped=false, aboard=false;
            int finalX=act==0 ? 0x4F0 : 0x1730;
            int finalY=act==0 ? 0x652 : 0x2D2;
            StringBuilder diagnostic=new StringBuilder();
            int previousRockX=rockX, previousRockY=rockY;
            int previousLevelFrame=GameServices.level().getFrameCounter();
            for(int frame=0;frame<1200;frame++) {
                var before=registry.capture();
                // Board the longer Act 2 track with ordinary input, brake, then ride.
                var support=GameServices.level().getObjectManager().getRidingObject(player);
                aboard |= support instanceof SozPushableRockObjectInstance && support.getSpawn().x()==rockX;
                boolean right=act==1 ? !aboard : !fell;
                boolean left=act==1 && aboard && player.getGSpeed()>0;
                fixture.stepFrame(false,false,left,right,false);
                int levelFrame=GameServices.level().getFrameCounter();
                assertTrue(levelFrame>previousLevelFrame,"unexpected level reset: "+diagnostic);
                previousLevelFrame=levelFrame;
                var rock=GameServices.level().getObjectManager().getActiveObjects().stream()
                        .filter(SozPushableRockObjectInstance.class::isInstance)
                        .map(SozPushableRockObjectInstance.class::cast)
                        .filter(r -> r.getSpawn().x()==rockX).findFirst();
                if(frame%60==0) diagnostic.append(frame).append(":")
                        .append(player.getCentreX()).append(",").append(player.getCentreY())
                        .append(" rock=").append(rock.map(r -> r.getX()+","+r.getY()).orElse("absent")).append("; ");
                if(rock.isPresent()) {
                    var r=rock.get();
                    if(act==1 && !aboard && GameServices.level().getObjectManager().isRidingObject(player,r)) {
                        verifyRewindStep(fixture,before,left,right,"boarding"); aboard=true;
                    }
                    if(!pushed && r.getX()>rockX) {
                        verifyRewindStep(fixture,before,left,right,"first push"); pushed=true;
                    }
                    // Floor-following during a push changes X and Y together. A vertical
                    // step with unchanged X identifies the initial free fall.
                    if(pushed && !fell && r.getX()==previousRockX && r.getY()>previousRockY) {
                        verifyRewindStep(fixture,before,left,right,"edge fall"); fell=true;
                    }
                    if(fell && !rode && r.carriesRiderOnHorizontalMove(player)) {
                        verifyRewindStep(fixture,before,left,right,"horizontal track start"); rode=true;
                    }
                    if(rode && r.getX()==finalX && r.getY()==finalY) {
                        assertTrue(act!=1 || GameServices.level().getObjectManager().isRidingObject(player,r),
                                "Act 2 player must still be riding at the terminal");
                        verifyRewindStep(fixture,before,left,right,"track terminal");
                        for(int i=0;i<3;i++) fixture.stepFrame(false,false,false,false,false);
                        var finalRock=GameServices.level().getObjectManager().getActiveObjects().stream()
                                .filter(SozPushableRockObjectInstance.class::isInstance)
                                .filter(o -> o.getSpawn().x()==rockX).findFirst().orElseThrow();
                        assertEquals(finalX,finalRock.getX()); assertEquals(finalY,finalRock.getY());
                        stopped=true; break;
                    }
                    previousRockX=r.getX(); previousRockY=r.getY();
                }
                assertFalse(player.getDead(),"player died before track completion: "+diagnostic);
            }
            assertTrue(pushed && fell && rode && stopped,
                    "placed rock must complete push/fall/ride/stop; flags="+pushed+","+fell+","+rode+","+stopped+" "+diagnostic);
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
