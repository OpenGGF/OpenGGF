package com.openggf.net.chat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Local exact-token display filter, independent of engine configuration and network state.
 * Lookup uses NFKC, ROOT lowercase, then NFKC; unmatched text retains its original code points.
 * Letters, numbers, combining marks and connectors form a run, including internal apostrophes.
 * Accents are significant. In scripts without spaces, only a complete matching run is masked:
 * embedded CJK insults, inflections, split words, leetspeak and other evasions can pass through.
 * The small starter dictionary is not comprehensive or human-linguist-reviewed.
 *
 * <p>The dictionary loads once at class initialisation. Callers should cache rendered history
 * and invoke this only when a message or display preference changes, never on every frame.
 */
public final class ProfanityFilter {
    private static final int MAX_KEYS = 1024;
    private static final int MAX_RESOURCE_BYTES = 64 * 1024;
    private static final Set<String> WORDS = loadDictionary();

    private ProfanityFilter() { }

    /** Masks each matching original token span with {@code ***}, preserving everything else. */
    public static String censor(String text) {
        Objects.requireNonNull(text, "text");
        StringBuilder result = null;
        int copiedThrough = 0;
        int offset = 0;
        while (offset < text.length()) {
            int codePoint = text.codePointAt(offset);
            if (!tokenPart(codePoint)) {
                offset += Character.charCount(codePoint);
                continue;
            }
            int end = tokenEnd(text, offset);
            if (WORDS.contains(normalize(text.substring(offset, end)))) {
                if (result == null) result = new StringBuilder(text.length());
                result.append(text, copiedThrough, offset).append("***");
                copiedThrough = end;
            }
            offset = end;
        }
        return result == null ? text : result.append(text, copiedThrough, text.length()).toString();
    }

    private static int tokenEnd(String text, int offset) {
        while (offset < text.length()) {
            int codePoint = text.codePointAt(offset);
            int next = offset + Character.charCount(codePoint);
            if (tokenPart(codePoint)
                    || apostrophe(codePoint) && next < text.length() && tokenPart(text.codePointAt(next))) {
                offset = next;
            } else {
                break;
            }
        }
        return offset;
    }

    private static boolean tokenPart(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
                    Character.MODIFIER_LETTER, Character.OTHER_LETTER, Character.DECIMAL_DIGIT_NUMBER,
                    Character.LETTER_NUMBER, Character.OTHER_NUMBER, Character.NON_SPACING_MARK,
                    Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK,
                    Character.CONNECTOR_PUNCTUATION -> true;
            default -> false;
        };
    }

    private static boolean apostrophe(int codePoint) {
        return codePoint == '\'' || codePoint == 0x2019 || codePoint == 0xFF07;
    }

    private static String normalize(String token) {
        return Normalizer.normalize(Normalizer.normalize(token, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT), Normalizer.Form.NFKC);
    }

    private static Set<String> loadDictionary() {
        try (var stream = ProfanityFilter.class.getResourceAsStream("/net/chat/profanity-starter.txt")) {
            if (stream == null) throw new IllegalStateException("Missing profanity starter dictionary");
            byte[] data = stream.readNBytes(MAX_RESOURCE_BYTES + 1);
            if (data.length > MAX_RESOURCE_BYTES) throw new IllegalStateException("Profanity dictionary exceeds 64 KiB");
            Set<String> words = new HashSet<>();
            for (String line : new String(data, StandardCharsets.UTF_8).lines().toList()) {
                String word = line.strip();
                if (word.isEmpty() || word.startsWith("#")) continue;
                String normalized = normalize(word);
                if (!tokenPart(normalized.codePointAt(0)) || tokenEnd(normalized, 0) != normalized.length()) {
                    throw new IllegalStateException("Profanity dictionary entries must be complete tokens");
                }
                if (!words.add(normalized)) throw new IllegalStateException("Duplicate profanity dictionary token");
                if (words.size() > MAX_KEYS) throw new IllegalStateException("Profanity dictionary exceeds 1024 keys");
            }
            if (words.isEmpty()) throw new IllegalStateException("Empty profanity starter dictionary");
            return Set.copyOf(words);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load profanity starter dictionary", failure);
        }
    }
}
