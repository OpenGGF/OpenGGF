package com.openggf.configuration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestConfigCatalog {

    @Test
    void everyConstantExceptVersionHasMeta() {
        for (SonicConfiguration key : SonicConfiguration.values()) {
            if (key == SonicConfiguration.VERSION) {
                continue;
            }
            assertNotNull(ConfigCatalog.meta(key), "missing catalog meta for " + key);
            ConfigKeyMeta m = ConfigCatalog.meta(key);
            assertNotNull(m.type(), "missing type for " + key);
            assertNotNull(m.description(), "missing description for " + key);
            assertFalse(m.description().isBlank(), "blank description for " + key);
        }
    }

    @Test
    void persistedKeysHaveSectionAndLeaf() {
        for (SonicConfiguration key : ConfigCatalog.emitOrder()) {
            ConfigKeyMeta m = ConfigCatalog.meta(key);
            assertTrue(m.persisted(), "emitOrder must contain only persisted keys: " + key);
            assertNotNull(m.section(), "missing section for " + key);
            assertNotNull(m.leaf(), "missing leaf for " + key);
        }
    }

    @Test
    void enumTypedKeysHaveAllowedValues() {
        for (SonicConfiguration key : ConfigCatalog.emitOrder()) {
            ConfigKeyMeta m = ConfigCatalog.meta(key);
            if (m.type() == ConfigType.ENUM) {
                assertFalse(m.allowedValues().isEmpty(), "ENUM key without allowedValues: " + key);
            }
        }
    }

    @Test
    void derivedKeysAreNotInEmitOrder() {
        assertFalse(ConfigCatalog.emitOrder().contains(SonicConfiguration.SCREEN_WIDTH_PIXELS));
        assertFalse(ConfigCatalog.emitOrder().contains(SonicConfiguration.SCREEN_HEIGHT_PIXELS));
        assertFalse(ConfigCatalog.meta(SonicConfiguration.SCREEN_WIDTH_PIXELS).persisted());
    }

    @Test
    void debugSectionsAreContiguousAndLast() {
        List<SonicConfiguration> order = ConfigCatalog.emitOrder();
        boolean seenDebug = false;
        for (SonicConfiguration key : order) {
            boolean isDebug = ConfigCatalog.meta(key).section().startsWith("debug");
            if (isDebug) {
                seenDebug = true;
            } else {
                assertFalse(seenDebug, "normal key " + key + " appears after a debug key");
            }
        }
        assertTrue(seenDebug, "expected at least one debug.* key");
    }

    @Test
    void reverseLookupRoundTrips() {
        SonicConfiguration key = ConfigCatalog.byPath("input.player1.jump");
        assertEquals(SonicConfiguration.JUMP, key);
        assertNull(ConfigCatalog.byPath("nope.not.real"));
    }
}
