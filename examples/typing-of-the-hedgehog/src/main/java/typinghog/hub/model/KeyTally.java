package typinghog.hub.model;

import java.util.Map;
import java.util.TreeMap;

/**
 * Per-key counts in the form the course writes into {@code ActResult.keyHits/keyMisses}:
 * {@code c:n,c:n,...}, lower-case, with {@code _} standing for the space bar. Engine-free.
 */
public final class KeyTally {
    private final Map<Character, Long> counts = new TreeMap<>();

    /** Adds every {@code c:n} pair in {@code text}; malformed pairs are skipped. */
    public KeyTally addEncoded(String text) {
        if (text == null || text.isBlank()) return this;
        for (String pair : text.split(",")) {
            int colon = pair.lastIndexOf(':');
            if (colon != 1) continue;
            char key = pair.charAt(0) == '_' ? ' ' : Character.toLowerCase(pair.charAt(0));
            try {
                long n = Long.parseLong(pair.substring(colon + 1).strip());
                if (n > 0) add(key, n);
            } catch (NumberFormatException ignored) {
                // A damaged pair costs only itself.
            }
        }
        return this;
    }

    public void add(char key, long n) {
        if (key < 32 || key > 126 || n <= 0) return;
        counts.merge(Character.toLowerCase(key), n, Long::sum);
    }

    public void addAll(KeyTally other) {
        other.counts.forEach(this::add);
    }

    public long get(char key) {
        return counts.getOrDefault(Character.toLowerCase(key), 0L);
    }

    public long total() {
        long sum = 0;
        for (long n : counts.values()) sum += n;
        return sum;
    }

    public Map<Character, Long> asMap() { return java.util.Collections.unmodifiableMap(counts); }

    public boolean isEmpty() { return counts.isEmpty(); }

    public String encode() {
        StringBuilder out = new StringBuilder();
        counts.forEach((c, n) -> {
            if (c == ',' || c == ':' || c == '=') return;
            if (out.length() > 0) out.append(',');
            out.append(c == ' ' ? '_' : c).append(':').append(n);
        });
        return out.toString();
    }
}
