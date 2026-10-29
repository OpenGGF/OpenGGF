package com.openggf.game;

import com.openggf.control.Composition;
import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.text.ShapedLine;

/** Underlines share the exact full-line shaped geometry and viewport used for the text. */
final class CompositionPresentation {
    private CompositionPresentation() { }
    static void underline(MenuPixelFont font, ShapedLine line, Composition composition, int anchor,
            int left, int top, int width, int offset) {
        int y = top + line.metrics().cellHeight() + line.below() + 1;
        for (int i = 0; i < composition.clauses().size(); i++) {
            var clause = composition.clauses().get(i);
            boolean focused = i == composition.focusedClause();
            for (var span : line.visualRanges(anchor + clause.start(), anchor + clause.end())) {
                int x = Math.max(left, left + span.left() - offset);
                int right = Math.min(left + width, left + span.right() - offset);
                if (right > x) font.fillRect(x, y, right - x, focused ? 2 : 1,
                        focused ? .3f : .55f, focused ? .9f : .75f, 1, 1);
            }
        }
    }
}
