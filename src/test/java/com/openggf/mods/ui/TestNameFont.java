package com.openggf.mods.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestNameFont {
    @Test
    void internationalNamesHaveRealGlyphs() {
        for (String name : List.of("Ana Müller", "Łukasz Żółw", "Đặng Văn Đức", "Ωμέγα", "Дмитрий",
                "ソニック速い", "소닉팬", "疾風の刃", "Çağrı Öztürk", "ไทย", "محمد")) {
            name.codePoints().filter(cp -> cp != ' ')
                    .forEach(cp -> assertTrue(NameFont.covers(cp), () -> name + " lacks U+" + Integer.toHexString(cp)));
            assertTrue(NameFont.width(name) > 0, name);
        }
    }

    @Test
    void fusionGlyphsStayInsideTheLineBoxAndKeepLowercase() {
        for (String name : List.of("Sonic_Fan99", "Ana Müller", "ソニック", "소닉", "Дмитрий gjpqy")) {
            int[] extent = NameFont.extent(name);
            assertEquals(0, extent[0], name);
            assertEquals(NameFont.LINE_HEIGHT - 1, extent[1], name);
        }
        assertNotEquals(runs("a"), runs("A"));
    }

    @Test
    void fitCutsWholeGraphemesAndEndsWithAnEllipsis() {
        String name = "Đặng Văn Đức Nguyễn";
        String fitted = NameFont.fit(name, 50);
        assertTrue(NameFont.width(fitted) <= 50, fitted);
        assertTrue(fitted.endsWith("…"), fitted);
        assertTrue(name.startsWith(fitted.substring(0, fitted.length() - 1)));
        // A decomposed mark never loses its base.
        String decomposed = "éééééééé";
        String cut = NameFont.fit(decomposed, 30);
        String kept = cut.substring(0, cut.length() - 1);
        assertEquals(0, kept.length() % 2, "base and mark stay together: " + kept);
        assertEquals("Sonic", NameFont.fit("Sonic", 200));
    }

    @Test
    void unsupportedCodePointsDrawAVisiblePlaceholder() {
        String rocket = new String(Character.toChars(0x1F680));
        assertFalse(NameFont.covers(0x1F680));
        assertTrue(NameFont.width(rocket) > 0);
        assertFalse(runs(rocket).isEmpty());
    }

    private static List<String> runs(String text) {
        List<String> runs = new ArrayList<>();
        NameFont.glyphs(text, 0, 0, (x, y, w, h) -> runs.add(x + "," + y + "," + w));
        return runs;
    }
}
