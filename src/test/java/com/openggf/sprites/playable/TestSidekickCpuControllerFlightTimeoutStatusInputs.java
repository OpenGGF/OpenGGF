package com.openggf.sprites.playable;

import com.openggf.game.PhysicsProfile;
import com.openggf.game.rules.GameRules;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.TestSidekickCpuControllerFlightAutoRecovery.TestableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.trace.TraceCharacterState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Declared status-input component controls, separate from the natural-facing old-red. */
class TestSidekickCpuControllerFlightTimeoutStatusInputs {
    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void s2TimeoutClearsStatusBitsWithoutRestoringRadiiOrWaterPhysics() {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        assertTimeoutStatusInputs(GameRules.SONIC_2);
    }

    @Test
    void s3kTimeoutClearsStatusBitsWithoutRestoringRadiiOrWaterPhysics() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        assertTimeoutStatusInputs(GameRules.SONIC_3K);
    }

    private void assertTimeoutStatusInputs(GameRules rules) {
        // Synthetic static component history; no movie/native state is consumed.
        var sonic = new TestableSprite("sonic");
        sonic.useGameRules(rules);
        sonic.setCentreX((short) 0x1000);
        sonic.setCentreY((short) 0x0400);
        short[] x = new short[64];
        short[] y = new short[64];
        Arrays.fill(x, (short) 0x1000);
        Arrays.fill(y, (short) 0x0400);
        sonic.hydrateRecordedHistory(x, y, new short[64], new byte[64], 16);
        var tails = new TestableSprite("tails_p2");
        tails.useGameRules(rules);
        tails.applyExternalPhysicsProfile(PhysicsProfile.SONIC_2_TAILS);
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x4000);
        tails.setCentreY((short) 0x0400);
        tails.setAir(true);
        tails.setRollingFlagPreserveRadii(true);
        tails.setRollingJump(true);
        tails.setOnObject(true); // Declared status bit, not a managed ride binding.
        tails.setPushing(true);
        tails.setInWater(true);
        tails.applyCustomRadii(7, 14);
        tails.setRenderFlagOnScreen(false);
        int underwaterAcceleration = tails.getRunAccel();
        assertEquals(6, underwaterAcceleration);
        int width = tails.getWidth();
        int height = tails.getHeight();
        var controller = new SidekickCpuController(tails, sonic);
        controller.setInitialState(SidekickCpuController.State.FLIGHT_AUTO_RECOVERY);
        assertEquals(0, controller.captureRewindState().flightTimer());
        for (int update = 1; update < 5 * 60; update++) {
            controller.update(update);
        }
        assertSame(SidekickCpuController.State.FLIGHT_AUTO_RECOVERY, controller.getState());
        assertEquals(299, controller.getDiagnosticRespawnCounter());
        assertEquals(Direction.LEFT, tails.getDirection());
        assertEquals(0x7F, TraceCharacterState.statusByteFromSprite(tails));
        assertTrue(tails.hasRenderFlagOnScreenState());
        assertFalse(tails.isRenderFlagOnScreen());

        controller.update(5 * 60);

        // sonic3k.asm:26574-26588 / s2.asm:39142-39155 write status=$02,
        // without writes to radii, dimensions or underwater speed constants.
        assertAll("Literal timeout status preserves independent body/physics fields",
                () -> assertSame(SidekickCpuController.State.CATCH_UP_FLIGHT, controller.getState()),
                () -> assertEquals(0, controller.captureRewindState().flightTimer()),
                () -> assertEquals(0x02, TraceCharacterState.statusByteFromSprite(tails)),
                () -> assertEquals(Direction.RIGHT, tails.getDirection()),
                () -> assertEquals(7, tails.getXRadius()),
                () -> assertEquals(14, tails.getYRadius()),
                () -> assertEquals(width, tails.getWidth()),
                () -> assertEquals(height, tails.getHeight()),
                () -> assertEquals(underwaterAcceleration, tails.getRunAccel()),
                () -> assertEquals(0x20, tails.getAnimationId()));
    }
}
