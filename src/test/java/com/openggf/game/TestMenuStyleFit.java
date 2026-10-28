package com.openggf.game;

import com.openggf.graphics.MenuPixelFont;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pixel-based fitting, wrapping and elision for proportional menu text. */
class TestMenuStyleFit {
    private static final float C = MenuStyle.COMPACT;
    private static final String EMOJI = new String(Character.toChars(0x1F600));

    @Test
    void fitKeepsExactFitsAndUsesTheLongestPrefixThatFitsWithTheEllipsis() {
        assertEquals("", MenuStyle.fit(null, 50));
        assertEquals("", MenuStyle.fit("", 0));
        assertEquals("iiiiiiii", MenuStyle.fit("iiiiiiii", 16), "Eight two-pixel i fit exactly");
        assertEquals("WWWW", MenuStyle.fit("WWWW", 24));
        // W is six pixels and the ellipsis nine: WW... is 21 px and WWW... 27 px.
        assertEquals("WW...", MenuStyle.fit("WWWW", 23));
        assertEquals("iiiiiii...", MenuStyle.fit("iiiiiiiiiiii", 23), "Narrow letters buy more text");
        for (String text : List.of("Hello world", "iiiiWWWWiiii", "C:/ROMs/very/long/path/sonic2.gen", "a" + EMOJI + "b" + EMOJI + "c")) {
            for (int width = -3; width <= MenuPixelFont.textWidth(text, C) + 2; width++) {
                String fitted = MenuStyle.fit(text, width);
                assertTrue(MenuPixelFont.textWidth(fitted, C) <= Math.max(0, width), text + " at " + width + ": " + fitted);
                assertTrue(paired(fitted), "Never split a surrogate pair: " + fitted);
                if (fitted.endsWith("...") && !fitted.equals("..."))
                    assertTrue(text.startsWith(fitted.substring(0, fitted.length() - 3)), fitted);
            }
        }
    }

    private static boolean paired(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c) && (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(++i))))
                return false;
            if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }

    @Test
    void tinyWidthsShowAsManyDotsAsFit() {
        assertEquals("", MenuStyle.fit("Hello", 2));
        assertEquals(".", MenuStyle.fit("Hello", 3));
        assertEquals("..", MenuStyle.fit("Hello", 8));
        assertEquals("...", MenuStyle.fit("Hello", 9));
        assertEquals("", MenuStyle.fitLabel("Hello", 4));
        assertEquals(".", MenuStyle.fitLabel("Hello", 5));
        assertEquals("...", MenuStyle.fitLabel("Hello", 15));
    }

    @Test
    void labelsFitWithBoldMetrics() {
        assertEquals("WWWW", MenuStyle.fitLabel("WWWW", 36));
        assertEquals("WW...", MenuStyle.fitLabel("WWWW", 35));
        assertTrue(MenuChrome.labelWidth(MenuStyle.fitLabel("SONIC THE HEDGEHOG 2", 100)) <= 100);
    }

    @Test
    void wrapBreaksAtSpacesByWidthAndSplitsUnbrokenPathsWithoutLosingCharacters() {
        List<String> lines = MenuStyle.wrap("Scale ordinary dry air acceleration; jump impulse", 120, C);
        for (String line : lines) assertTrue(MenuChrome.compactWidth(line) <= 120, line);
        assertEquals("Scale ordinary dry air acceleration; jump impulse", String.join(" ", lines));
        String path = "Library/Genesis/roms/" + "x".repeat(60) + "/sonic.gen";
        List<String> pieces = MenuStyle.wrap(path, 90, C);
        assertEquals(path, String.join("", pieces), "Unbroken paths keep every character");
        for (String piece : pieces) assertTrue(MenuChrome.compactWidth(piece) <= 90, piece);
        assertEquals(List.of("a", "", "b"), MenuStyle.wrap("a\n\nb", 100, C));
        assertEquals(List.of(EMOJI, EMOJI), MenuStyle.wrap(EMOJI + EMOJI, 1, C), "Progress by code point");
        String arrows = "\u2190\u2192\u2191\u2193\u2190\u2192";
        assertEquals(List.of("\u2190\u2192\u2191", "\u2193\u2190\u2192"), MenuStyle.wrap(arrows, 18, C), "Arrows keep whole cells");
    }

    @Test
    void chunksKeepEveryCodePointAndFitTheWidth() {
        String text = "Expected: s2-ehz1-recorded-1234567890 Actual: " + EMOJI + "iiiiiiiiWWWWWWWW";
        List<String> chunks = MenuStyle.chunks(text, 60, C);
        assertEquals(text, String.join("", chunks));
        for (String chunk : chunks) {
            assertTrue(MenuChrome.compactWidth(chunk) <= 60, chunk);
            assertFalse(Character.isLowSurrogate(chunk.charAt(0)), chunk);
        }
        assertEquals(List.of(), MenuStyle.chunks("", 60, C));
    }

    @Test
    void elisionKeepsBothEndsAndMarqueeScrollsByWholeCharacters() {
        String name = "master-title-capture12345678901234567890";
        for (int width = 0; width <= 260; width++) {
            String middle = MenuStyle.elideMiddle(name, width, C, 3);
            assertTrue(MenuChrome.compactWidth(middle) <= width, middle);
            String start = MenuStyle.elideStart(name, Math.max(width, 15), C);
            assertTrue(start.equals(name) || start.startsWith("...") && name.endsWith(start.substring(3)), start);
        }
        String mid = MenuStyle.elideMiddle(name, 120, C, 3);
        assertTrue(mid.startsWith("master") && mid.contains("...") && mid.endsWith("890"), mid);
        String held = MenuStyle.marquee(name, 100, C, 0);
        assertTrue(name.startsWith(held) && MenuChrome.compactWidth(held) <= 100, held);
        String scrolled = MenuStyle.marquee(name, 100, C, 10_000 * 31 + 1000);
        assertTrue(name.contains(scrolled) && MenuChrome.compactWidth(scrolled) <= 100, scrolled);
        assertEquals("short", MenuStyle.marquee("short", 100, C, 77));
        boolean reachedEnd = false;
        for (int step = 0; step < 400; step++) reachedEnd |= name.endsWith(MenuStyle.marquee(name, 100, C, step));
        assertTrue(reachedEnd, "The marquee reveals the end of the text");
    }
}
