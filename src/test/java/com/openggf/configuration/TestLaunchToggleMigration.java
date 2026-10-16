package com.openggf.configuration;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Legacy boolean launch-profile switches become Default/On/Off values. */
class TestLaunchToggleMigration {
    @Test void trueBecomesOnAndFalseFollowsTheAllGamesSetting() {
        Map<String, Object> config = new HashMap<>();
        config.put(SonicConfiguration.LAUNCH_S1_REWIND.name(), true);
        config.put(SonicConfiguration.LAUNCH_S2_REWIND.name(), false);
        config.put(SonicConfiguration.LAUNCH_S3K_DEBUG_TOOLS.name(), "true");
        config.put(SonicConfiguration.LAUNCH_S2_DEBUG_TOOLS.name(), "off");

        assertTrue(new ConfigMigrationService().migrateLaunchProfileToggles(config));

        assertEquals("on", config.get(SonicConfiguration.LAUNCH_S1_REWIND.name()));
        assertEquals("global", config.get(SonicConfiguration.LAUNCH_S2_REWIND.name()));
        assertEquals("on", config.get(SonicConfiguration.LAUNCH_S3K_DEBUG_TOOLS.name()));
        assertEquals("off", config.get(SonicConfiguration.LAUNCH_S2_DEBUG_TOOLS.name()), "new values are left alone");
        assertFalse(new ConfigMigrationService().migrateLaunchProfileToggles(config), "migration is idempotent");
    }
}
