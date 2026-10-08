package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kDezConveyorBeltObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** DEZ1 record 25 ($780,$730): actual placed belt, terrain and player loop. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezConveyorBeltHeadless {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 800})
    void placedBeltCarriesIdleSonicAndReplaysAtBothWidths(int width) {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                (width == 800 ? WidescreenAspect.SUPER_32_9 : WidescreenAspect.NATIVE_4_3).name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0)
                .startPosition((short) 0x730, (short) 0x700).startPositionIsCentre().build();
        assertEquals(width, GameServices.camera().getWidth());
        fixture.sprite().setRingCount(7);
        fixture.stepIdleFrames(1);
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                .anyMatch(o -> o instanceof S3kDezConveyorBeltObjectInstance && o.getX() == 0x780));
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var before = registry.capture();
        int startX = fixture.sprite().getCentreX();
        List<String> first = ride(fixture, 20);
        assertEquals(startX + 40, fixture.sprite().getCentreX(), "idle grounded carry is two pixels per dispatch");
        assertFalse(fixture.sprite().getDead());
        registry.restore(before);
        assertEquals(first, ride(fixture, 20), "position, fractions and velocity replay with the placed owner");
    }

    private List<String> ride(HeadlessTestFixture fixture, int frames) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            fixture.stepIdleFrames(1);
            var p = fixture.sprite();
            result.add(p.getCentreX() + "," + p.getCentreY() + "," + p.getXSubpixelRaw()
                    + "," + p.getYSubpixelRaw() + "," + p.getXSpeed() + "," + p.getGSpeed());
        }
        return result;
    }
}
