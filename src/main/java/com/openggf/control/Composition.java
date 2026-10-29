package com.openggf.control;

import com.openggf.util.Graphemes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Copied host preedit. All indices are UTF-16, and the caret is a grapheme boundary. */
public record Composition(String text, List<Clause> clauses, int focusedClause, int caret) {
    public record Clause(int start, int end) {
        public Clause {
            if (start < 0 || end < start) throw new IllegalArgumentException("Invalid clause");
        }
    }
    public static final Composition EMPTY = new Composition("", List.of(), -1, 0);
    public static final int MAX_CODEPOINTS = 4096;

    public Composition {
        Objects.requireNonNull(text);
        clauses = List.copyOf(clauses);
        if (text.length() > MAX_CODEPOINTS * 2 || caret < 0 || caret > text.length()
                || !Graphemes.isBoundary(text, caret)) throw new IllegalArgumentException("Invalid preedit caret");
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            if (cp >= 0xD800 && cp <= 0xDFFF) throw new IllegalArgumentException("Preedit contains a non-scalar");
            offset += Character.charCount(cp);
        }
        int previous = 0;
        for (Clause clause : clauses) {
            if (clause.start() != previous || clause.end() <= clause.start() || clause.end() > text.length()
                    || !Graphemes.isBoundary(text, clause.start()) || !Graphemes.isBoundary(text, clause.end()))
                throw new IllegalArgumentException("Invalid preedit clauses");
            previous = clause.end();
        }
        if (previous != text.length()) throw new IllegalArgumentException("Incomplete preedit clauses");
        if (focusedClause < -1 || focusedClause >= clauses.size()) throw new IllegalArgumentException("Invalid focused clause");
    }

    /** Native block sizes and caret are code-point counts, never UTF-16 offsets. */
    public static Composition fromCodepoints(int[] codepoints, int[] blockSizes, int focused, int caret) {
        Objects.requireNonNull(codepoints);
        Objects.requireNonNull(blockSizes);
        if (codepoints.length > MAX_CODEPOINTS || blockSizes.length > MAX_CODEPOINTS)
            throw new IllegalArgumentException("Preedit exceeds bound");
        if (codepoints.length == 0) return EMPTY;
        StringBuilder text = new StringBuilder();
        int[] offsets = new int[codepoints.length + 1];
        for (int i = 0; i < codepoints.length; i++) {
            int cp = codepoints[i];
            if (!Character.isValidCodePoint(cp) || cp >= 0xD800 && cp <= 0xDFFF)
                throw new IllegalArgumentException("Preedit contains a non-scalar");
            text.appendCodePoint(cp);
            offsets[i + 1] = text.length();
        }
        String value = text.toString();
        List<Clause> clauses = new ArrayList<>();
        int used = 0;
        boolean valid = blockSizes.length > 0;
        for (int size : blockSizes) {
            if (size <= 0 || size > codepoints.length - used) { valid = false; break; }
            int start = offsets[used], end = offsets[used + size];
            if (!Graphemes.isBoundary(value, start) || !Graphemes.isBoundary(value, end)) valid = false;
            clauses.add(new Clause(start, end));
            used += size;
        }
        valid &= used == codepoints.length;
        if (!valid) { clauses = List.of(new Clause(0, value.length())); focused = -1; }
        else if (focused < 0 || focused >= clauses.size()) focused = -1;
        int boundary = offsets[Math.clamp(caret, 0, codepoints.length)];
        if (!Graphemes.isBoundary(value, boundary)) boundary = Graphemes.next(value, boundary);
        return new Composition(value, clauses, focused, boundary);
    }

    public boolean isEmpty() { return text.isEmpty(); }
}
