package com.openggf.configuration;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_O;

class CaptureConfigDefaultsTest {

    @Test
    void captureDefaults() {
        SonicConfigurationService c = SonicConfigurationService.createStandalone();
        assertEquals("target/trace-videos", c.getString(SonicConfiguration.CAPTURE_OUTPUT_DIR));
        assertEquals(4, c.getInt(SonicConfiguration.CAPTURE_SCALE));
        assertEquals(60, c.getInt(SonicConfiguration.CAPTURE_FPS));
        assertEquals("ffv1", c.getString(SonicConfiguration.CAPTURE_CODEC));
        assertEquals(GLFW_KEY_O, c.getInt(SonicConfiguration.CAPTURE_TOGGLE_KEY));
    }

    @Test
    void resourceYamlDeclaresLiveCaptureToggleKey() {
        try (InputStream in = getClass().getResourceAsStream("/config.yaml")) {
            assertNotNull(in);
            Map<String, Object> yaml = new Yaml().load(in);
            ConfigFlattener.Result flattened = ConfigFlattener.flatten(yaml);
            assertEquals("O", flattened.flat().get(SonicConfiguration.CAPTURE_TOGGLE_KEY.name()));
        } catch (Exception e) {
            fail(e);
        }
    }
}
