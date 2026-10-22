package com.openggf.net.identity;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestDisplayNames {
    @Test
    void internationalNamesSurviveUnchanged() {
        for (String name : List.of("Ana Müller", "Łukasz Żółw", "Đặng Văn Đức", "Ωμέγα", "Дмитрий",
                "ソニック速い", "소닉팬", "疾風の刃", "Çağrı Öztürk", "हिंदी", "ไทย", "محمد", "[SPEED]+")) {
            assertEquals(name, DisplayNames.sanitize(name), name);
            assertTrue(DisplayNames.isCanonical(name), name);
        }
    }

    @Test
    void decomposedInputBecomesComposedAndSpacingCollapses() {
        assertEquals("Caf\u00E9", DisplayNames.sanitize("  Cafe\u0301 "));
        assertEquals("A B", DisplayNames.sanitize("A\t \u3000 B"));
        assertEquals("", DisplayNames.sanitize(" \n "));
        assertFalse(DisplayNames.isCanonical(""));
    }

    @Test
    void disguisingAndUndisplayableCodePointsAreRemoved() {
        // Right-to-left override, zero-width joiner, BOM, private use, control, quote and backslash.
        assertEquals("evilgnp", DisplayNames.sanitize("evil\u202Egnp\u200D\uFEFF\uE000\u0007\"\\"));
        // Marks need a base, and stacks are capped after composition (a + U+0301 is one letter).
        assertEquals("\u00E1\u0302\u0303", DisplayNames.sanitize("\u0301a\u0301\u0302\u0303\u0304\u0305"));
    }

    @Test
    void lengthIsBoundedByCodePointsAndBytes() {
        String longLatin = "x".repeat(40);
        assertEquals(DisplayNames.MAX_CODE_POINTS, DisplayNames.sanitize(longLatin).codePointCount(0,
                DisplayNames.sanitize(longLatin).length()));
        String longCjk = "速".repeat(40);
        assertTrue(DisplayNames.sanitize(longCjk).getBytes(StandardCharsets.UTF_8).length
                <= DisplayNames.MAX_UTF8_BYTES);
        String rockets = new String(Character.toChars(0x1F680)).repeat(30);
        assertTrue(DisplayNames.sanitize(rockets).getBytes(StandardCharsets.UTF_8).length
                <= DisplayNames.MAX_UTF8_BYTES);
    }
}
