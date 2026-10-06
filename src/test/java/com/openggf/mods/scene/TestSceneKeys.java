package com.openggf.mods.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

/** {@link SceneKeys} are the engine's GLFW key codes under scene-friendly names. */
class TestSceneKeys {
    @Test
    void everyKeyIsTheMatchingGlfwCode() throws Exception {
        int checked = 0;
        for (Field field : SceneKeys.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) {
                continue;
            }
            String name = field.getName().startsWith("DIGIT_") ? field.getName().substring(6) : field.getName();
            assertEquals(GLFW.class.getField("GLFW_KEY_" + name).getInt(null), field.getInt(null), field.getName());
            checked++;
        }
        assertTrue(checked > 70, "letters, digits, editing and function keys: " + checked);
        assertEquals(SceneKeys.DIGIT_1 + 4, SceneKeys.DIGIT_5, "digits are consecutive");
        assertEquals(SceneKeys.A + 25, SceneKeys.Z, "letters are consecutive");
    }

    @Test
    void buttonsAreThePadsOwnBitLayout() {
        // SACBRLDU: one bit each, no overlaps.
        int all = 0;
        for (int bit : new int[] {SceneButtons.UP, SceneButtons.DOWN, SceneButtons.LEFT, SceneButtons.RIGHT,
                SceneButtons.B, SceneButtons.C, SceneButtons.A, SceneButtons.START}) {
            assertEquals(0, all & bit);
            assertEquals(1, Integer.bitCount(bit));
            all |= bit;
        }
        assertEquals(0xFF, all);
        assertEquals(0x0F, SceneButtons.DIRECTIONS);
        assertEquals(0x70, SceneButtons.ACTIONS);
    }
}
