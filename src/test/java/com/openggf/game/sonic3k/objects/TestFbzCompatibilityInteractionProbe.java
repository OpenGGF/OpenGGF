package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused entry point for the exact-placement compatibility evidence. */
@RequiresRom(SonicGame.SONIC_3K)
class TestFbzCompatibilityInteractionProbe {
    @Test
    void exactPlacedOptionalInteractionsSupportFourParticipants() {
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        String previousMain = configuration.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        String previousSidekicks = configuration.getString(
                SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        String previousAspect = configuration.getString(SonicConfiguration.DISPLAY_ASPECT);
        try {
            CrossGameFeatureProvider.getInstance().resetState();
            configuration.clearSessionOverrides();
            configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    "tails,knuckles,sonic");
            configuration.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                    WidescreenAspect.NATIVE_4_3.name());
            configuration.resolveDisplayAspect();
            configuration.setSessionOverride(
                    SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, "off");
            SessionManager.clear();
            TestEnvironment.activeGameplayMode();

            FbzCompatibilityInteractionProbe.Evidence evidence =
                    FbzCompatibilityInteractionProbe.run(320);
            assertTrue(evidence.prisonOpened());
            assertTrue(evidence.flamethrowerHazardActive());
            assertTrue(evidence.flamethrowerStandingSuppressed());
            assertTrue(evidence.flamethrowerAllEligibleSolid());
            assertTrue(evidence.magneticBothSubtypesMoved());
            assertTrue(evidence.magneticAllEligibleCoherent());
            assertTrue(evidence.spiderMainCapturedMovedReleased());
            assertTrue(evidence.spiderSidekickAuthorityPreserved());
        } finally {
            CrossGameFeatureProvider.getInstance().resetState();
            configuration.clearSessionOverrides();
            configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                    previousMain == null ? "sonic" : previousMain);
            configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    previousSidekicks == null ? "tails" : previousSidekicks);
            configuration.setConfigValue(SonicConfiguration.DISPLAY_ASPECT,
                    previousAspect == null
                            ? WidescreenAspect.NATIVE_4_3.name() : previousAspect);
            configuration.resolveDisplayAspect();
            SessionManager.clear();
            TestEnvironment.activeGameplayMode();
        }
    }
}
