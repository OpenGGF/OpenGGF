package com.openggf.mods.testing;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import static org.junit.jupiter.api.Assertions.*;

class TestDeterministicInput {
    @Test void logicalSamplingUsesTheSuppliedConfigurationAfterLiveRemapping() {
        var configuration = com.openggf.configuration.SonicConfigurationService.createStandalone();
        var binding = com.openggf.configuration.SonicConfiguration.RIGHT;
        configuration.setConfigValue(binding, GLFW.GLFW_KEY_D);
        var input = new DeterministicInput(configuration);
        input.key(GLFW.GLFW_KEY_D, true);
        input.beginTick();
        assertTrue((input.handler().logical().player1().heldMask()
                & com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT) != 0);
        input.endTick();
        configuration.setConfigValue(binding, GLFW.GLFW_KEY_L);
        input.beginTick();
        assertEquals(0, input.handler().logical().player1().heldMask()
                & com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT);
        assertTrue(input.handler().capturePhysicalInput().keyDown(GLFW.GLFW_KEY_D));
        input.endTick();
        input.key(GLFW.GLFW_KEY_L, true);
        input.beginTick();
        assertTrue((input.handler().logical().player1().heldMask()
                & com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT) != 0);
        input.endTick();
    }

    @Test void explicitTimestampStartsANewAdvancingSixtyHertzClock() {
        var input = new DeterministicInput();
        input.atNanos(10_000_000_000L);
        for (int frame = 0; frame < 60; frame++) {
            input.beginTick();
            assertEquals(10_000_000_000L + (frame + 1L) * 1_000_000_000L / 60,
                    input.handler().capturePhysicalInput().timestampNanos());
            input.endTick();
        }
        assertEquals(11_000_000_000L, input.nanos());
        assertThrows(IllegalArgumentException.class, () -> input.atNanos(1));
    }

    @Test void physicalTransitionsRetainTheirObservationTimeAndHeldState() {
        var input = new DeterministicInput();
        input.atNanos(2_000_000_000L);
        input.key(GLFW.GLFW_KEY_SPACE, true);
        input.beginTick();
        var first = input.handler().capturePhysicalInput();
        assertTrue(first.keyDown(GLFW.GLFW_KEY_SPACE));
        assertEquals(1, first.events().size());
        assertTrue(first.events().getFirst().pressed());
        assertEquals(2_000_000_000L, first.events().getFirst().timestampNanos());
        input.endTick();
        input.key(GLFW.GLFW_KEY_SPACE, false);
        input.beginTick();
        var second = input.handler().capturePhysicalInput();
        assertFalse(second.keyDown(GLFW.GLFW_KEY_SPACE));
        assertEquals(1, second.events().size());
        assertTrue(second.events().getFirst().released());
        assertEquals(first.timestampNanos(), second.events().getFirst().timestampNanos());
        assertTrue(second.timestampNanos() > first.timestampNanos());
        input.endTick();
    }
}
