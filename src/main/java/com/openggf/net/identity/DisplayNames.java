package com.openggf.net.identity;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Objects;

/**
 * Canonical player display names, shared by clients and hosts so a name typed in any script
 * reaches every player unchanged. Canonical form is NFC with single spaces, at most
 * {@link #MAX_CODE_POINTS} code points and {@link #MAX_UTF8_BYTES} bytes. It removes code
 * points that cannot be shown or that disguise text: controls, format characters (including
 * bidirectional overrides and zero-width joiners), private use, unassigned and lone surrogates,
 * marks without a base, and marks stacked more than {@link #MAX_MARKS_PER_BASE} deep.
 * {@code "} and {@code \} stay excluded, as host labels have always required.
 */
@com.openggf.game.ModApi
public final class DisplayNames {
    public static final int MAX_CODE_POINTS = 20;
    public static final int MAX_UTF8_BYTES = 64;
    public static final int MAX_MARKS_PER_BASE = 2;

    private DisplayNames() { }

    /** The canonical form of {@code raw}; empty when nothing displayable remains. */
    public static String sanitize(String raw) {
        String text = Normalizer.normalize(Objects.requireNonNullElse(raw, ""), Normalizer.Form.NFC);
        StringBuilder out = new StringBuilder(text.length());
        int codePoints = 0;
        int bytes = 0;
        int marks = 0;
        boolean pendingSpace = false;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = !out.isEmpty();
                continue;
            }
            if (!displayable(codePoint)) continue;
            boolean mark = isMark(codePoint);
            if (mark && (out.isEmpty() || pendingSpace || marks >= MAX_MARKS_PER_BASE)) continue;
            int needed = utf8Length(codePoint) + (pendingSpace ? 1 : 0);
            int count = 1 + (pendingSpace ? 1 : 0);
            if (codePoints + count > MAX_CODE_POINTS || bytes + needed > MAX_UTF8_BYTES) break;
            if (pendingSpace) out.append(' ');
            out.appendCodePoint(codePoint);
            codePoints += count;
            bytes += needed;
            marks = mark ? marks + 1 : 0;
            pendingSpace = false;
        }
        // A cut may leave a base whose marks were dropped; NFC keeps the remainder canonical.
        return Normalizer.normalize(out.toString(), Normalizer.Form.NFC);
    }

    /** Whether {@code name} is already canonical and non-empty. */
    public static boolean isCanonical(String name) {
        return name != null && !name.isEmpty() && sanitize(name).equals(name);
    }

    private static boolean displayable(int codePoint) {
        if (codePoint == '"' || codePoint == '\\') return false;
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL, Character.FORMAT, Character.PRIVATE_USE, Character.SURROGATE,
                 Character.UNASSIGNED, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> false;
            default -> true;
        };
    }

    private static boolean isMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                || type == Character.COMBINING_SPACING_MARK;
    }

    private static int utf8Length(int codePoint) {
        return new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
    }
}
