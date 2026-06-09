package com.openggf.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestBundledConfigResource {

    @Test
    void bundledYamlExistsAndFlattensCleanly() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/config.yaml")) {
            assertNotNull(is, "bundled /config.yaml must exist");
            ObjectMapper mapper = new YAMLMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> nested = mapper.readValue(is, Map.class);
            ConfigFlattener.Result r = ConfigFlattener.flatten(nested);
            assertTrue(r.unknownKeys().isEmpty(), "bundled config has unknown keys: " + r.unknownKeys());
            assertTrue(r.flat().containsKey(SonicConfiguration.DEFAULT_ROM.name()));
            assertEquals(Boolean.TRUE, r.flat().get(SonicConfiguration.TRACE_SHOW_DESYNC_GHOSTS.name()));
            assertEquals(Boolean.TRUE, r.flat().get(SonicConfiguration.TRACE_SHOW_GAME_HUD.name()));
            assertEquals(Boolean.FALSE, r.flat().get(SonicConfiguration.TRACE_SHOW_DEBUG_HUD.name()));
        }
    }

    @Test
    void legacyJsonResourceIsGone() {
        assertNull(getClass().getResourceAsStream("/config.json"),
                "bundled config.json should be replaced by config.yaml");
    }
}
