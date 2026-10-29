package com.openggf.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestMenuFieldLines {
    @Test
    void wrapsAtWordsByMeasuredWidthAndKeepsEveryCharacter() {
        String text = "hardware_timing.jsonl row 1882: kind VDP_DMA does not match the prepared production job";
        List<String> lines = MenuFieldLines.wrap(text, 150);
        assertEquals(text, String.join("", lines));
        assertTrue(lines.size() > 1);
        for (String line : lines) {
            assertTrue(MenuChrome.compactWidth(line.stripTrailing()) <= 150, line);
            assertFalse(line.startsWith(" "), "Breaks keep the space on the previous line: " + line);
        }
        assertTrue(lines.get(0).endsWith(" "), "A word is never split while a space is available");
    }

    @Test
    void identifiersThatFitTheColumnMoveWholeToTheNextLine() {
        String text = "row 1882: kind VDP_DMA ordinal 4 does not match; next hardware_timing.jsonl";
        for (int w = 60; w <= 200; w += 7) {
            final int width = w;
            List<String> lines = MenuFieldLines.wrap(text, width);
            assertEquals(text, String.join("", lines), "every character kept at " + width);
            for (String identifier : List.of("VDP_DMA", "hardware_timing.jsonl")) {
                if (MenuChrome.compactWidth(identifier) > width) continue;
                assertTrue(lines.stream().anyMatch(line -> line.contains(identifier)),
                        identifier + " stays whole at " + width + ": " + lines);
            }
            lines.forEach(line -> assertTrue(MenuChrome.compactWidth(line.stripTrailing()) <= width, line));
        }
    }

    @Test
    void longIdentifiersBreakAfterPathPunctuationThenByWidth() {
        List<String> path = MenuFieldLines.wrap("recordings/s2/emerald-hill-route-with-sonic-and-tails.bk2", 120);
        assertEquals("recordings/s2/emerald-hill-route-with-sonic-and-tails.bk2", String.join("", path));
        assertTrue(path.get(0).endsWith("/") || path.get(0).endsWith("-"), path.get(0));
        List<String> token = MenuFieldLines.wrap("x".repeat(60), 60);
        assertEquals("x".repeat(60), String.join("", token));
        token.forEach(line -> assertTrue(MenuChrome.compactWidth(line) <= 60, line));
    }

    @Test
    void layoutLabelsOnlyTheFirstLineOfEachValue() {
        var fields = List.of(new MenuFieldLines.Field("Reason", "contract violation ".repeat(6)),
                new MenuFieldLines.Field("Steps", "12999"));
        int labelWidth = MenuFieldLines.labelWidth(fields);
        assertEquals(MenuChrome.compactWidth("Reason") + 8, labelWidth);
        var lines = MenuFieldLines.layout(fields, labelWidth, 120);
        assertEquals("Reason", lines.get(0).label());
        assertNull(lines.get(1).label());
        assertEquals("Steps", lines.get(lines.size() - 1).label());
        assertEquals(List.of(""), MenuFieldLines.wrap("", 100));
    }

    @Test
    void narrowLinesKeepUnicodeCodePointsIntactAndAlwaysMakeProgress() {
        String text = "a\uD83D\uDE80b";
        for (int width : new int[] {0, 1, 6}) {
            List<String> lines = MenuFieldLines.wrap(text, width);
            assertEquals(text, String.join("", lines));
            assertEquals(List.of("a", "\uD83D\uDE80", "b"), lines);
        }
        assertEquals(List.of(""), MenuFieldLines.wrap(null, 0));
        assertEquals(List.of("first", "second"), MenuFieldLines.wrap("first\nsecond", 100));
    }
}
