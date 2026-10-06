package com.openggf.game.mode;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The controller capability is reusable on a stock module, without loading golf. */
@RequiresRom(SonicGame.SONIC_3K)
class TestCourseControl {
    private SharedLevel bootstrap;

    @AfterEach void close() {
        if (bootstrap != null) bootstrap.dispose();
        SonicConfigurationService.getInstance().clearSessionOverrides();
    }

    private CourseControl course() throws Exception {
        bootstrap = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        return new CourseControl(fixture.runtime());
    }

    @Test void rollingImpulseAcceptsCreatorVelocityWithoutGolfLoftOrPowerLimits() throws Exception {
        var course = course();
        var before = course.playerState();
        course.launchRolling(-1, -0x40, -0x3000, 0, true);
        var launched = course.playerState();
        assertEquals(-0x40, launched.xSpeed());
        assertEquals(-0x3000, launched.ySpeed());
        assertEquals(0, launched.groundSpeed());
        assertTrue(launched.rolling());
        assertTrue(launched.airborne());
        assertEquals(before.x(), launched.x(), "flat rolling-radius change preserves X");
        assertEquals(before.y() + before.radius() - launched.radius(), launched.y());
        assertThrows(IllegalArgumentException.class, () -> course.launchRolling(0, 0, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> course.launchRolling(1, 32768, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> course.launchRolling(1, 0, -32769, 0, true));
        assertEquals(launched, course.playerState(), "invalid impulses make no partial changes");
    }

    @Test void registeredKnucklesAndExplicitHydrocityDestinationNeedNoGolfCarveouts() throws Exception {
        var course = course();
        course.selectCharacter("knuckles");
        assertEquals("knuckles", course.playerState().character());
        var player = GameServices.camera().getFocusedSprite();
        assertThrows(IllegalArgumentException.class, () -> course.selectCharacter("missing-author:missing"));
        assertSame(player, GameServices.camera().getFocusedSprite());
        assertEquals("knuckles", GameServices.configuration().getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        course.loadLevel(1, 0);
        assertEquals(1, GameServices.level().getCurrentZone());
        assertEquals(0, course.actIndex());
        assertThrows(IllegalArgumentException.class, () -> course.loadLevel(-1, 0));
        assertEquals(1, GameServices.level().getCurrentZone());
    }

    @Test void characterReplacementPreservesTheEntireCameraViewAndRebindsItsTarget() throws Exception {
        var course = course();
        var camera = GameServices.camera();
        var before = camera.capture();
        var oldPlayer = camera.getFocusedSprite();
        course.selectCharacter("knuckles");
        assertEquals(before, camera.capture(), "character selection must not reset a held camera to sprite top-left");
        assertNotSame(oldPlayer, camera.getFocusedSprite());
        assertEquals("knuckles", camera.getFocusedSprite().getCode());
        assertSame(GameServices.sprites().getSprite("knuckles"), camera.getFocusedSprite());
    }
}
