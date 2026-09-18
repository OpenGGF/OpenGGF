package com.openggf;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.timing.VIntRunCounter;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The power-on {@code V_int_run_count} carrier ({@link VIntRunCounter}) is
 * serviced once per game-loop iteration in the modes that have no level object
 * clock, is bound to the object clock for the life of a gameplay session, and
 * is a registered rewind subsystem of that session.
 */
class TestGameLoopVIntRunCounter {
    private GameLoop gameLoop;

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        InputHandler input = mock(InputHandler.class);
        when(input.logical()).thenReturn(com.openggf.control.LogicalInputSnapshot.neutral());
        gameLoop = new GameLoop(input);
    }

    @AfterEach
    void tearDown() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SonicConfigurationService.getInstance().resolveDisplayAspect();
        gameLoop = null;
        SessionManager.clear();
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
    }

    private static VIntRunCounter counter() {
        return com.openggf.game.session.EngineTiming.vIntRunCounter();
    }

    private void stepBody(int iterations) throws Exception {
        Method body = GameLoop.class.getDeclaredMethod("stepInternalBody");
        body.setAccessible(true);
        for (int i = 0; i < iterations; i++) {
            body.invoke(gameLoop);
        }
    }

    @Test
    void nonGameplayIterationsAdvanceTheCounterAndSurviveSessionTurnover() throws Exception {
        counter().seed(1000);
        gameLoop.setGameMode(GameMode.LEGAL_DISCLAIMER);
        stepBody(5);
        assertEquals(1005L, counter().value(),
                "each boot/menu iteration is one serviced V-int (sonic3k.asm:542-543)");

        // Closing the session hands the count back; the next session's first
        // object clock starts from it instead of zero.
        SessionManager.clear();
        assertFalse(counter().isObjectClockBound());
        assertEquals(1005, counter().objectClockSeed());
        GameplayModeContext next = TestEnvironment.activeGameplayMode();
        assertTrue(counter().isObjectClockBound(), "a new session binds its object clock");
        stepBody(3);
        assertEquals(1008L, counter().value(),
                "until the session builds an ObjectManager the carrier keeps counting");
        assertEquals(next, SessionManager.getCurrentGameplayMode());
    }

    @Test
    void counterIsARegisteredRewindSubsystemOfTheSession() {
        GameplayModeContext context = TestEnvironment.activeGameplayMode();
        counter().seed(0x1598);
        CompositeSnapshot snapshot = context.getRewindRegistry().capture();
        assertTrue(snapshot.entries().containsKey(VIntRunCounter.REWIND_KEY),
                "keys: " + snapshot.entries().keySet());
        counter().serviceRepresentedVBlank();
        counter().serviceRepresentedVBlank();
        assertEquals(0x159AL, counter().value());
        context.getRewindRegistry().restore(snapshot);
        assertEquals(0x1598L, counter().value());
    }
}
