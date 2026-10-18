package com.openggf.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static com.openggf.configuration.SonicConfiguration.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.glfw.GLFW.*;

class TestConfigurationKeyBoundaries {
    @TempDir Path directory;

    @Test
    void lowercaseKeyNamesWorkOnTurkishInstallations() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var config = SonicConfigurationService.createStandalone(directory);
            config.setConfigValue(P1_A, "right_shift");
            config.setConfigValue(P2_UP, "i");
            assertEquals(GLFW_KEY_RIGHT_SHIFT, config.getInt(P1_A),
                    "case-insensitive GLFW names must not depend on the machine's language");
            assertEquals(GLFW_KEY_I, config.getInt(P2_UP));
            assertEquals(KeyChord.of(GLFW_KEY_RIGHT_SHIFT), config.getKeyChord(P1_A));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void upgradingLegacyKeypadBindingsPreservesTheirPhysicalKeys() throws Exception {
        // java.awt.event.KeyEvent: VK_NUMPAD0=96, VK_ADD=107. These are
        // ordinary legacy bindings, not GLFW's GRAVE_ACCENT=96 or unknown 107.
        Files.writeString(directory.resolve("config.json"), """
                {"UP":38,"DOWN":40,"LEFT":37,"RIGHT":39,"P1_A":96,"P2_A":107}
                """);
        var config = SonicConfigurationService.createStandalone(directory);
        assertEquals(GLFW_KEY_KP_0, config.getInt(P1_A));
        assertEquals(GLFW_KEY_KP_ADD, config.getInt(P2_A));
        var reloaded = SonicConfigurationService.createStandalone(directory);
        assertEquals(GLFW_KEY_KP_0, reloaded.getInt(P1_A));
        assertEquals(GLFW_KEY_KP_ADD, reloaded.getInt(P2_A));
    }
}
