package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CharacterKey;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.SszArrivalControllerObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** SSZ2_ScreenInit / Obj_57C1E's existing Knuckles branch; not the missing act-2 controller or fight. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszKnucklesArrivalHeadless {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @ParameterizedTest @ValueSource(ints = {320, 800})
    void nativeArrivalRiseAndPriorityReleaseReplayAtBothWidths(int width) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                (width == 320 ? WidescreenAspect.NATIVE_4_3 : WidescreenAspect.SUPER_32_9).name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 1)
                .withFreshLevelStartLifecycle().build();
        assertEquals(CharacterKey.KNUCKLES, fixture.sprite().characterKey());
        assertEquals(width, fixture.camera().getWidth());
        fixture.stepIdleFrames(1);
        assertEquals(0x649, fixture.camera().getY() & 0xFFFF);
        assertEquals(0x6AE, fixture.sprite().getCentreY() & 0xFFFF);
        assertTrue(fixture.camera().getFrozen());
        assertTrue(fixture.sprite().isObjectControlled());
        fixture.stepIdleFrames(17);
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var snapshot = registry.capture();
        fixture.stepIdleFrames(12);
        int x = fixture.sprite().getCentreX(), y = fixture.sprite().getCentreY();
        int cameraY = fixture.camera().getY();
        registry.restore(snapshot); fixture.stepIdleFrames(12);
        assertEquals(x, fixture.sprite().getCentreX());
        assertEquals(y, fixture.sprite().getCentreY());
        assertEquals(cameraY, fixture.camera().getY());
        // $44 rise passes; the final decrement skips camera movement, just as in act 1.
        fixture.stepIdleFrames(0x44 - 29);
        assertEquals(0xA0, fixture.sprite().getCentreX() & 0xFFFF);
        assertEquals(0x6AE - 8 * 0x44, fixture.sprite().getCentreY() & 0xFFFF);
        assertEquals(0x649 - 8 * 0x43, fixture.camera().getY() & 0xFFFF);
        assertTrue(fixture.camera().getFrozen());
        fixture.stepIdleFrames(1);
        assertFalse(fixture.camera().getFrozen());
        assertTrue(fixture.sprite().isHighPriority(), "loc_57D3C sets art_tile bit 15 for act 2");
        assertEquals(SszArrivalControllerObjectInstance.PHASE_SWING,
                GameServices.level().getObjectManager()
                        .activeObjectsOfType(SszArrivalControllerObjectInstance.class).getFirst().phaseForTest());
        assertFalse(fixture.sprite().getDead());
    }
}
