package com.openggf.mods.code;

import com.openggf.io.ModInputLimits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestBakedSheetRef {
    @Test
    void retainsNormalizedBoundedJarEntryPath() {
        assertEquals("art/objects/motobug.ggfsheet",
                new BakedSheetRef("art/objects/motobug.ggfsheet").entryPath());
    }

    @Test
    void rejectsUnsafeBlankAndUtf8OversizedPaths() {
        for (String path : new String[] {"", "../art.bin", "/art.bin", "art\\sheet.bin",
                "C:/art.bin", "art//sheet.bin", "art/./sheet.bin"}) {
            assertThrows(IllegalArgumentException.class, () -> new BakedSheetRef(path), path);
        }
        String oversized = "é".repeat(ModInputLimits.DEFAULT_MAX_ENTRY_NAME_BYTES / 2 + 1);
        assertThrows(IllegalArgumentException.class, () -> new BakedSheetRef(oversized));
        assertThrows(NullPointerException.class, () -> new BakedSheetRef(null));
    }
}
