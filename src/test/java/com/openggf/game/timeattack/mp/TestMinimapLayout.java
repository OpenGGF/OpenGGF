package com.openggf.game.timeattack.mp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestMinimapLayout {
    @Test
    void dotsAreProportionalAndUsePresentationPrecedence() {
        String strip = MinimapLayout.compose(4000, List.of(
                new MinimapLayout.Dot(0, '.'), new MinimapLayout.Dot(2000, 'o'),
                new MinimapLayout.Dot(2000, '*'), new MinimapLayout.Dot(3999, '+')));
        assertEquals(MinimapLayout.COLUMNS, strip.length());
        assertEquals('.', strip.charAt(0));
        assertEquals('*', strip.charAt(MinimapLayout.COLUMNS / 2));
        assertEquals('+', strip.charAt(MinimapLayout.COLUMNS - 1));
    }

    @Test
    void invalidWidthsAndPositionsClampSafely() {
        assertEquals(MinimapLayout.COLUMNS, MinimapLayout.compose(0,
                List.of(new MinimapLayout.Dot(500, '*'))).length());
        String strip = MinimapLayout.compose(1000, List.of(
                new MinimapLayout.Dot(-50, 'o'), new MinimapLayout.Dot(99_999, 'o')));
        assertEquals('o', strip.charAt(0));
        assertEquals('o', strip.charAt(MinimapLayout.COLUMNS - 1));
        assertEquals('+', MinimapLayout.glyphForFarStatus(2));
        assertEquals('.', MinimapLayout.glyphForFarStatus(1));
        assertEquals(' ', MinimapLayout.glyphForFarStatus(0));
    }
}
