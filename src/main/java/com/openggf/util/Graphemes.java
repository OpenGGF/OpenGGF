package com.openggf.util;

import java.text.BreakIterator;

/**
 * Extended grapheme cluster boundaries (Unicode 16, including Indic conjunct and emoji
 * sequences, as implemented by the JDK). Editing, fitting and wrapping never split these.
 */
public final class Graphemes {
    private static final ThreadLocal<BreakIterator> ITERATOR =
            ThreadLocal.withInitial(BreakIterator::getCharacterInstance);

    private Graphemes() { }

    /** The boundary after {@code index}, or {@code text.length()}. */
    public static int next(String text, int index) {
        if (index >= text.length()) return text.length();
        BreakIterator iterator = ITERATOR.get();
        iterator.setText(text);
        int next = iterator.following(Math.max(0, index));
        return next == BreakIterator.DONE ? text.length() : next;
    }

    /** The boundary before {@code index}, or zero. */
    public static int previous(String text, int index) {
        if (index <= 0) return 0;
        BreakIterator iterator = ITERATOR.get();
        iterator.setText(text);
        int previous = iterator.preceding(Math.min(text.length(), index));
        return previous == BreakIterator.DONE ? 0 : previous;
    }

    /** Whether {@code index} is a cluster boundary. */
    public static boolean isBoundary(String text, int index) {
        if (index <= 0 || index >= text.length()) return true;
        BreakIterator iterator = ITERATOR.get();
        iterator.setText(text);
        return iterator.isBoundary(index);
    }

    /** Number of user-perceived characters. */
    public static int count(String text) {
        int count = 0;
        for (int index = 0; index < text.length(); index = next(text, index)) count++;
        return count;
    }
}
