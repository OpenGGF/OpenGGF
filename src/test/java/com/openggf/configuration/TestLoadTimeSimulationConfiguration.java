package com.openggf.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestLoadTimeSimulationConfiguration {

    @Test
    void defaultsToNone() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();

        assertEquals("NONE",
                config.getString(SonicConfiguration.LOAD_TIME_SIMULATION));
    }

    @Test
    void catalogUsesGameplayPathAndSupportedValues() {
        ConfigKeyMeta meta = ConfigCatalog.meta(
                SonicConfiguration.LOAD_TIME_SIMULATION);

        assertEquals("gameplay.loadTimeSimulation", meta.path());
        assertEquals(
                java.util.Set.of("NONE", "PROFILED", "FAST", "REALISTIC"),
                meta.allowedValues());
    }
}
