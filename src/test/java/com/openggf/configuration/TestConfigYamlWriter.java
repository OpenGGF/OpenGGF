package com.openggf.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestConfigYamlWriter {

    private Map<String, Object> defaults() {
        Map<String, Object> flat = new LinkedHashMap<>();
        for (SonicConfiguration key : ConfigCatalog.emitOrder()) {
            ConfigKeyMeta m = ConfigCatalog.meta(key);
            Object v = switch (m.type()) {
                case BOOL -> Boolean.FALSE;
                case INT -> 0;
                case DOUBLE -> 0.0;
                case KEY -> "SPACE";
                default -> "x";
            };
            flat.put(key.name(), v);
        }
        // a value that must be quoted (spaces, brackets, '!')
        flat.put(SonicConfiguration.SONIC_2_ROM.name(), "Sonic The Hedgehog 2 (W) (REV01) [!].gen");
        flat.put(SonicConfiguration.PLAYBACK_MOVIE_PATH.name(), "");
        return flat;
    }

    @Test
    void emitsParseableYamlWithSectionsAndDebugFence() throws Exception {
        String yaml = new ConfigYamlWriter().write(defaults());
        assertTrue(yaml.contains("display:"), yaml);
        assertTrue(yaml.contains("# ── Display ──"), yaml);
        assertTrue(yaml.contains("\ndebug:"), yaml);
        assertTrue(yaml.contains("DEBUG"), yaml);
        ObjectMapper mapper = new YAMLMapper();
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(yaml, Map.class);
        ConfigFlattener.Result r = ConfigFlattener.flatten(parsed);
        assertTrue(r.unknownKeys().isEmpty(), "unexpected unknown keys: " + r.unknownKeys());
        assertEquals("Sonic The Hedgehog 2 (W) (REV01) [!].gen",
                r.flat().get(SonicConfiguration.SONIC_2_ROM.name()));
    }

    @Test
    void derivedKeysAreNeverEmitted() {
        String yaml = new ConfigYamlWriter().write(defaults());
        assertFalse(yaml.contains("SCREEN_WIDTH_PIXELS"));
        assertFalse(yaml.contains("pixelWidth"));
    }

    @Test
    void outputIsDeterministic() {
        ConfigYamlWriter w = new ConfigYamlWriter();
        assertEquals(w.write(defaults()), w.write(defaults()));
    }
}
