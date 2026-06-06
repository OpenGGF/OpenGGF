package com.openggf.tests.rules;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;

class TestRomCacheAvailability {

    @Test
    void invalidConfiguredRomFileIsUnavailable() throws Exception {
        Path invalidRom = Files.createTempFile("openggf-invalid-rom", ".gen");
        Files.writeString(invalidRom, "not a rom");

        String previous = System.getProperty("sonic1.rom.path");
        clearRomCache();
        System.setProperty("sonic1.rom.path", invalidRom.toString());
        try {
            assertNull(RomCache.getRom(SonicGame.SONIC_1),
                    "existing but invalid configured ROM files should not enable @RequiresRom tests");
        } finally {
            restoreProperty("sonic1.rom.path", previous);
            clearRomCache();
            Files.deleteIfExists(invalidRom);
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearRomCache() throws ReflectiveOperationException {
        Field cacheField = RomCache.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        ((Map<SonicGame, ?>) cacheField.get(null)).clear();
    }

    private static void restoreProperty(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }
}
