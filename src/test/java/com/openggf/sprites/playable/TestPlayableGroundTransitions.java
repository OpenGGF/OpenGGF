package com.openggf.sprites.playable;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class TestPlayableGroundTransitions {
    @BeforeEach void setup() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        TestEnvironment.activeGameplayMode();
    }
    @AfterEach void cleanup() { SessionManager.clear(); GameModuleRegistry.reset(); }

    private static final class Character extends TestablePlayableSprite {
        int landings, transitions, resets;
        Character() { super("sonic", (short) 0, (short) 0); }
        @Override protected void onLanded() { assertFalse(getAir()); landings++; }
        @Override protected void onGroundStateChanged(boolean airborne) {
            assertEquals(airborne, getAir()); transitions++;
        }
        @Override protected void onLevelReset() { resets++; }
    }

    @Test void terrainAndObjectLandingsDoNotDependOnDrawing() {
        Character c = new Character();
        c.setAir(true);
        c.setAir(false);
        assertEquals(1, c.landings);
        c.draw(); c.draw(); c.setAir(false);
        assertEquals(1, c.landings);
        c.setAir(true);
        c.setAirAfterObjectHurtLanding();
        c.setAirAfterObjectHurtLanding();
        assertEquals(2, c.landings);
        assertEquals(4, c.transitions);
    }

    @Test void controlRestoreLoadAndRewindDoNotSynthesizeLandings() {
        Character c = new Character();
        c.setAir(true);
        var snapshot = c.captureRewindState();
        c.clearAirForNativeControlRestore();
        assertEquals(0, c.landings);
        assertEquals(2, c.transitions);
        c.restoreRewindState(snapshot);
        assertTrue(c.getAir());
        assertEquals(2, c.transitions, "rewind hydrates state without firing transitions");
        c.resetState();
        assertEquals(1, c.resets);
        assertEquals(0, c.landings, "level initialization is not a collision landing");
    }

    @Test void instanceSpecificationSurvivesModulePhysicsResolutionAndLevelReset() {
        var profile = com.openggf.game.PhysicsProfile.builder()
                .movement(0x20, 0x80, 0x20, 0x480, 0x780).shape(8, 16, 6, 12).build();
        AbstractPlayableSprite c = new AbstractPlayableSprite("sonic", (short) 0, (short) 0,
                new CharacterPhysicsSpec(profile)) {
            @Override public void draw() { }
            @Override protected void defineSpeeds() { fail("Instance specification must not invoke virtual speed initialization"); }
            @Override protected void createSensorLines() { fail("Instance specification must not invoke virtual sensor initialization"); }
        };
        assertEquals(profile, c.getPhysicsProfile());
        assertEquals(18, c.getWidth());
        assertEquals(32, c.getHeight());
        assertEquals(8, c.getGroundSensors()[1].getX());
        assertEquals(16, c.getGroundSensors()[1].getY());
        c.resetState();
        assertEquals(profile, c.getPhysicsProfile());
        assertEquals(32, c.getHeight());
    }

    @Test void groundAndLoadCallbacksRetainConstructionOwnerBoundary() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        Character c = com.openggf.game.CharacterConstructionScope.call(com.openggf.game.CharacterKey.SONIC,
                callback -> { calls.incrementAndGet(); return callback.get(); }, Character::new);
        c.setAir(true); c.setAir(false); c.resetState();
        assertEquals(3, calls.get());
        assertEquals(1, c.landings);
        assertEquals(1, c.resets);
    }
}
