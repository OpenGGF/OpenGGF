package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.SozBreakableSandRockObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.EnumMap;
import static org.junit.jupiter.api.Assertions.*;

/** Short positioned production spots, independent of full-act route reachability. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozSandRockProduction {
    @ParameterizedTest
    @CsvSource({"0,320,off,tails", "0,640,off,tails", "0,320,s1,tails", "0,320,off,'tails,knuckles'", "1,320,off,tails"})
    void placedRockBreaksAndRestoresThroughProductionCollision(int act, int width, String donor, String followers) {
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
            int rockX=act==0 ? 0x260 : 0x1C0;
            int rockY=act==0 ? 0x5B0 : 0x3B0;
            var builder=HeadlessTestFixture.builder().withZoneAndAct(8,act)
                    .startPosition((short)rockX,(short)(rockY-64)).startPositionIsCentre()
                    .withFreshLevelStartLifecycle();
            if(!donor.equals("off")) builder.withCrossGameDonation(donor);
            var fixture=builder.build();
            assertEquals(width,fixture.camera().getWidth()&0xFFFF);
            assertEquals(followers.split(",").length,GameServices.sprites().getSidekicks().size());
            assertEquals(!donor.equals("s1"),fixture.sprite().getGameRules().playerCapability().spindashEnabled());
            var player=fixture.sprite();
            player.setAir(true);
            player.setRollingFlagPreserveRadii(true);
            player.applyCustomRadii(7,14);
            player.setAnimationId(2);
            player.setYSpeed((short)0x100);
            boolean broken=false;
            var registry=fixture.gameplayMode().getRewindRegistry();
            for(int frame=0;frame<60;frame++) {
                var before=registry.capture();
                fixture.stepFrame(false,false,false,false,false);
                var rock=GameServices.level().getObjectManager().getActiveObjects().stream()
                        .filter(SozBreakableSandRockObjectInstance.class::isInstance)
                        .map(SozBreakableSandRockObjectInstance.class::cast)
                        .filter(r -> r.getX()==rockX).findFirst();
                if(rock.isPresent() && !rock.get().isSolidFor(player)) {
                    assertTrue(player.getAir());
                    verifyRewindStep(fixture,before,false,"sand rock breakup");
                    for(int phase=1;phase<=24;phase++) {
                        var preceding=registry.capture();
                        fixture.stepFrame(false,false,false,false,false);
                        if(phase==6 || phase==24) verifyRewindStep(fixture,preceding,false,
                                "breakup phase "+phase);
                    }
                    assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                            .noneMatch(o -> o instanceof SozBreakableSandRockObjectInstance
                                    && o.getSpawn().x()==rockX),"finished rock must be removed");
                    broken=true; break;
                }
            }
            assertTrue(broken,"positioned falling roll must break placed rock; player="
                    +player.getCentreX()+","+player.getCentreY()+","+player.getYSpeed());
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
            fixture.stepFrame(false, false, false, false, jump);
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
