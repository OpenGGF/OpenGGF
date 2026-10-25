package typinghog.play;

import java.util.Map;
import java.util.TreeMap;

/** Per-character correct keystrokes and errors (the character that was expected) for the act. */
public final class KeyStats {
    private final Map<Character, Integer> hits = new TreeMap<>();
    private final Map<Character, Integer> misses = new TreeMap<>();

    void hit(int codePoint) { bump(hits, codePoint); }
    void miss(int expectedCodePoint) { bump(misses, expectedCodePoint); }

    private static void bump(Map<Character, Integer> map, int codePoint) {
        if (codePoint < 32 || codePoint > 126) return;
        map.merge(Character.toLowerCase((char) codePoint), 1, Integer::sum);
    }

    String hits() { return encode(hits); }
    String misses() { return encode(misses); }

    private static String encode(Map<Character, Integer> map) {
        StringBuilder out = new StringBuilder();
        map.forEach((c, n) -> {
            if (c == ',' || c == ':' || c == '=') return;
            if (out.length() > 0) out.append(',');
            out.append(c == ' ' ? '_' : c).append(':').append(n);
        });
        return out.toString();
    }

    void clear() {
        hits.clear();
        misses.clear();
    }
}
