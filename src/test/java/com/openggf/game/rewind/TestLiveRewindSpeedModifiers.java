package com.openggf.game.rewind;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestLiveRewindSpeedModifiers {

    @Test
    void noModifiersGiveUnitMultiplier() {
        assertEquals(1.0, LiveRewindManager.speedMultiplier(false, false), 1e-9);
    }

    @Test
    void halfModifierHalvesSpeed() {
        assertEquals(0.5, LiveRewindManager.speedMultiplier(true, false), 1e-9);
    }

    @Test
    void doubleModifierDoublesSpeed() {
        assertEquals(2.0, LiveRewindManager.speedMultiplier(false, true), 1e-9);
    }

    @Test
    void bothModifiersCancelToNormalSpeed() {
        assertEquals(1.0, LiveRewindManager.speedMultiplier(true, true), 1e-9);
    }

    @Test
    void modifierKeysMirrorAcrossLeftAndRightVariants() {
        assertEquals(GLFW.GLFW_KEY_RIGHT_CONTROL, LiveRewindManager.mirroredModifier(GLFW.GLFW_KEY_LEFT_CONTROL));
        assertEquals(GLFW.GLFW_KEY_LEFT_CONTROL, LiveRewindManager.mirroredModifier(GLFW.GLFW_KEY_RIGHT_CONTROL));
        assertEquals(GLFW.GLFW_KEY_RIGHT_SHIFT, LiveRewindManager.mirroredModifier(GLFW.GLFW_KEY_LEFT_SHIFT));
        assertEquals(GLFW.GLFW_KEY_LEFT_SHIFT, LiveRewindManager.mirroredModifier(GLFW.GLFW_KEY_RIGHT_SHIFT));
        assertEquals(GLFW.GLFW_KEY_RIGHT_ALT, LiveRewindManager.mirroredModifier(GLFW.GLFW_KEY_LEFT_ALT));
    }

    @Test
    void nonModifierKeysHaveNoMirror() {
        assertEquals(GLFW.GLFW_KEY_UNKNOWN, LiveRewindManager.mirroredModifier(GLFW.GLFW_KEY_R));
        assertEquals(GLFW.GLFW_KEY_UNKNOWN, LiveRewindManager.mirroredModifier(GLFW.GLFW_KEY_SPACE));
    }
}
