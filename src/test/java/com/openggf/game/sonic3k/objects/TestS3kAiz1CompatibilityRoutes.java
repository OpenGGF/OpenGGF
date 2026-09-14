package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Exhaustive axis rows; native 320/off remains independently selected in the ordinary pilot. */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledIfSystemProperty(named = "openggf.aiz1.routes", matches = "true")
class TestS3kAiz1CompatibilityRoutes {
    @ParameterizedTest(name = "AIZ1 Sonic+Tails width={0} donor={1}")
    @CsvSource({"400,off", "512,off", "640,off", "800,off", "320,s1", "320,s2"})
    void axisRouteCompletes(int width, String donor) throws Exception {
        var saved = TraceReplaySessionBootstrap.snapshotGameplayConfig();
        var config = GameServices.configuration();
        var provider = CrossGameFeatureProvider.getInstance();
        try {
            provider.resetState();
            var setup = TestS3kAiz1RoutePilot.prepareRoute(() -> {
                config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                        (width == 320 ? WidescreenAspect.NATIVE_4_3 : WidescreenAspect.WIDE_16_9).name());
                config.resolveDisplayAspect();
                config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
                config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, donor);
                config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, !donor.equals("off"));
                if (!donor.equals("off")) {
                    String property = donor.equals("s1") ? "sonic1.rom.path" : "sonic2.rom.path";
                    String path = System.getProperty(property);
                    assertNotNull(path, "explicit axis validation requires -D" + property);
                    assertTrue(Files.isRegularFile(Path.of(path)), "donor ROM missing: " + path);
                    config.setSessionOverride(donor.equals("s1") ? SonicConfiguration.SONIC_1_ROM
                            : SonicConfiguration.SONIC_2_ROM, Path.of(path).toAbsolutePath().toString());
                }
                com.openggf.game.session.SessionManager.clear();
                com.openggf.tests.TestEnvironment.activeGameplayMode();
            });
            assertEquals(width, setup.fixture().camera().getWidth());
            assertEquals(!donor.equals("off"), CrossGameFeatureProvider.isActive());
            if (!donor.equals("off")) {
                assertNotSame(GameServices.module().getRules(), setup.fixture().sprite().getGameRules());
            }
            try {
                TestS3kAiz1RoutePilot.runRoute(setup);
                System.out.printf("AIZEVIDENCE width=%d donor=%s result=reload%n", width, donor);
            } catch (AssertionError failure) {
                throw new AssertionError("AIZEVIDENCE width=" + width + " donor=" + donor
                        + " " + failure.getMessage(), failure);
            }
        } finally {
            provider.resetState();
            config.clearSessionOverrides();
            TraceReplaySessionBootstrap.restoreGameplayConfig(saved);
        }
    }
}
