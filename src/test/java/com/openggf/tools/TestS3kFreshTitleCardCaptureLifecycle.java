package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.objects.S3kFreshTitleCardOwnerObjectInstance;
import com.openggf.game.sonic3k.objects.S3kFreshTitleCardElementObjectInstance;
import com.openggf.level.LevelContinuationCarry;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** loc_63C14/loc_7F310 Act3 entry bypasses the whole title owner at loc_62B6. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kFreshTitleCardCaptureLifecycle {
    @org.junit.jupiter.api.AfterEach void reset() {
        GameServices.configuration().clearSessionOverrides();
        com.openggf.game.session.SessionManager.clear();
    }

    @ParameterizedTest @CsvSource({"22,0,320", "22,0,352", "22,0,400", "22,0,528", "22,0,800",
            "23,0,320", "23,0,352", "23,0,400", "23,0,528", "23,0,800"})
    void gameOwnedContinuationCreatesNoFreshTitleSstOrOmittedTeardown(int zone, int act, int width) throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var config = GameServices.configuration();
        var aspect = java.util.Arrays.stream(com.openggf.configuration.WidescreenAspect.values())
                .filter(candidate -> candidate.pixelWidth() == width).findFirst().orElseThrow();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.TEST_MODE_ENABLED, false);
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.resolveDisplayAspect();
        GameServices.camera().refreshViewportDimensions(config);
        assertEquals(width, GameServices.camera().getWidth(), "actual production preset before load");
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(11, 1).build();
        var level = GameServices.level();
        // Exact engine-internal semantic bridge used by native loc_63C14/7F310.
        // This short admission test is independent of the cold controller paths.
        LevelContinuationCarry.request(level, zone, act, 7, 1234, null);
        level.loadZoneAndActAtFreshTitleCardBoundary(zone, act);
        assertEquals(width, GameServices.camera().getWidth(), "actual production preset after load");
        assertEquals(zone, level.getCurrentZone());
        assertFalse(level.isTitleCardRequested(), "Act3 bypasses the locked native title loop");
        assertFalse(level.hasPendingFreshLevelTransitionBoundary());
        assertTrue(level.getObjectManager().activeObjectsOfType(
                S3kFreshTitleCardOwnerObjectInstance.class).isEmpty(),
                "loc_62B6 does not create the fixed SST8 owner when Act3_flag is set");
        assertTrue(level.getObjectManager().activeObjectsOfType(
                S3kFreshTitleCardElementObjectInstance.class).isEmpty());
        assertDoesNotThrow(() -> fixture.stepIdleFrames(48),
                "no orphan title actor may later consume an absent art admission lease");
    }
}
