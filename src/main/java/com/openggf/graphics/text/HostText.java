package com.openggf.graphics.text;

import java.lang.Character.UnicodeScript;
import java.text.Bidi;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The host interface's one text owner. Every width, fit, wrap, draw position, caret and
 * hitbox comes from the same {@link ShapedLine}, so measurement and drawing cannot disagree.
 *
 * <p>Each line is split into bidirectional runs ({@link Bidi}, first-strong unless the
 * language says otherwise), then into script items and extended grapheme clusters. A run
 * uses the first face in its fallback chain that supports its script; clusters that face
 * lacks fall back individually. Scripts that need contextual shaping (Arabic, Indic, Thai
 * and anything outside {@link HostFace#SIMPLE_SCRIPTS}) are drawn only by OpenType faces
 * through HarfBuzz; if none can, the cluster is reported missing and drawn as a box, never
 * as isolated bitmap forms. Plain printable ASCII keeps the original fixed-cell layout.
 */
public final class HostText {
    private static final int CACHE_LIMIT = 4096;
    private static final String ELLIPSIS = "...";

    private record Key(String text, TextFace role, TextLanguage language) { }
    private record WrapKey(String text, int width, TextFace role, TextLanguage language) { }
    private record Segment(int start, int end, HostFace face, UnicodeScript script) { }

    private static final class Holder {
        static final HostText SHARED = new HostText(FontRegistry.bundled());
    }

    private final FontRegistry registry;
    private final Map<Key, ShapedLine> cache = lru();
    private final Map<WrapKey, List<String>> wraps = lru();

    HostText(FontRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public static HostText shared() {
        return Holder.SHARED;
    }

    private static <K, V> Map<K, V> lru() {
        return new LinkedHashMap<>(256, .75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > CACHE_LIMIT;
            }
        };
    }

    /** One line; line separators inside {@code text} are treated as invisible. */
    public ShapedLine shape(String text, TextFace role, TextLanguage language) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(language, "language");
        Key key = new Key(text, role, language);
        synchronized (cache) {
            ShapedLine cached = cache.get(key);
            if (cached != null) return cached;
        }
        ShapedLine shaped = shapeUncached(text, role, language);
        synchronized (cache) {
            cache.put(key, shaped);
        }
        return shaped;
    }

    /** Widest line of {@code text} in unscaled pixels. */
    public int width(String text, TextFace role, TextLanguage language) {
        int width = 0;
        for (String line : lines(text)) width = Math.max(width, shape(line, role, language).width());
        return width;
    }

    /** Line pitch and overhang for stacking lines of this role in this language. */
    public LineMetrics lineMetrics(TextFace role, TextLanguage language) {
        NativeFace nativeFace = registry.nativeFace(role);
        LineMetrics metrics = new LineMetrics(nativeFace.cellHeight(), nativeFace.baseline(),
                nativeFace.ascent(), nativeFace.descent());
        for (HostFace face : registry.preferred(role, language)) {
            if (face.available()) metrics = metrics.include(face.ascent(), face.descent());
        }
        return metrics;
    }

    /** Lines of a block, split at CR, LF or CRLF; empty lines are kept. */
    public static List<String> lines(String text) {
        return List.of(text.split("\\r\\n|\\r|\\n", -1));
    }

    /**
     * {@code text} if it fits, otherwise its longest grapheme-safe prefix followed by "...".
     * When even the ellipsis does not fit, as many dots as do.
     */
    public String fit(String text, int maxWidth, TextFace role, TextLanguage language) {
        if (text == null || maxWidth <= 0) return "";
        if (width(text, role, language) <= maxWidth) return text;
        int ellipsis = width(ELLIPSIS, role, language);
        if (ellipsis > maxWidth) return ".".repeat(Math.max(0, maxWidth / Math.max(1, width(".", role, language))));
        List<Integer> boundaries = new ArrayList<>();
        for (int index = Graphemes.next(text, 0); index < text.length(); index = Graphemes.next(text, index)) {
            boundaries.add(index);
        }
        int low = 0;
        int high = boundaries.size() - 1;
        int best = 0;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int end = boundaries.get(middle);
            if (width(text.substring(0, end) + ELLIPSIS, role, language) <= maxWidth) {
                best = end;
                low = middle + 1;
            } else high = middle - 1;
        }
        return text.substring(0, best) + ELLIPSIS;
    }

    /**
     * Wraps at Unicode line-break opportunities for the language (spaces, CJK ideographs,
     * Thai words), breaking an over-wide segment between grapheme clusters. Explicit line
     * breaks and blank lines survive; trailing spaces are not drawn.
     */
    public List<String> wrap(String text, int maxWidth, TextFace role, TextLanguage language) {
        Objects.requireNonNull(text, "text");
        if (maxWidth < 1) throw new IllegalArgumentException("Positive text area required");
        WrapKey key = new WrapKey(text, maxWidth, role, language);
        synchronized (wraps) {
            List<String> cached = wraps.get(key);
            if (cached != null) return cached;
        }
        List<String> out = new ArrayList<>();
        BreakIterator breaks = BreakIterator.getLineInstance(language.locale());
        for (String paragraph : lines(text)) {
            if (paragraph.isEmpty()) {
                out.add("");
                continue;
            }
            breaks.setText(paragraph);
            int lineStart = 0;
            int fitted = -1;
            int boundary = breaks.following(0);
            while (boundary != BreakIterator.DONE) {
                String candidate = stripTrailing(paragraph.substring(lineStart, boundary));
                if (width(candidate, role, language) <= maxWidth) {
                    fitted = boundary;
                    boundary = breaks.following(boundary);
                    continue;
                }
                if (fitted > lineStart) {
                    out.add(stripTrailing(paragraph.substring(lineStart, fitted)));
                    lineStart = fitted;
                } else {
                    // One unbreakable segment is wider than the area: split between clusters.
                    int end = Graphemes.next(paragraph, lineStart);
                    for (int next = Graphemes.next(paragraph, end); next <= boundary && end < boundary;
                         next = Graphemes.next(paragraph, end)) {
                        if (width(paragraph.substring(lineStart, next), role, language) > maxWidth) break;
                        end = next;
                        if (next == paragraph.length()) break;
                    }
                    out.add(paragraph.substring(lineStart, end));
                    lineStart = end;
                }
                fitted = -1;
                if (lineStart >= boundary) boundary = breaks.following(lineStart);
            }
            if (lineStart < paragraph.length()) {
                String rest = stripTrailing(paragraph.substring(lineStart));
                if (!rest.isEmpty() || out.isEmpty()) out.add(rest);
            }
        }
        List<String> result = List.copyOf(out);
        synchronized (wraps) {
            wraps.put(key, result);
        }
        return result;
    }

    private static String stripTrailing(String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }

    private ShapedLine shapeUncached(String text, TextFace role, TextLanguage language) {
        NativeFace nativeFace = registry.nativeFace(role);
        LineMetrics metrics = new LineMetrics(nativeFace.cellHeight(), nativeFace.baseline(),
                nativeFace.ascent(), nativeFace.descent());
        boolean defaultRtl = com.ibm.icu.util.ULocale.forLocale(language.locale()).isRightToLeft();
        if (text.isEmpty()) return new ShapedLine(text, List.of(), 0, metrics, new int[] {0}, List.of(), defaultRtl);
        if (printableAscii(text) && (!defaultRtl || text.chars().anyMatch(Character::isLetter)))
            return shapeAscii(text, nativeFace, metrics);

        List<PlacedGlyph> glyphs = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int[] caret = new int[text.length() + 1];
        Arrays.fill(caret, -1);
        int flags = switch (language.direction()) {
            case LTR -> Bidi.DIRECTION_LEFT_TO_RIGHT;
            case RTL -> Bidi.DIRECTION_RIGHT_TO_LEFT;
            case AUTO -> defaultRtl ? Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT : Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT;
        };
        Bidi bidi = new Bidi(text, flags);
        int runCount = bidi.getRunCount();
        Integer[] order = new Integer[runCount];
        byte[] levels = new byte[runCount];
        for (int run = 0; run < runCount; run++) {
            order[run] = run;
            levels[run] = (byte) bidi.getRunLevel(run);
        }
        Bidi.reorderVisually(levels, 0, order, 0, runCount);
        long pen = 0; // 26.6 fixed point
        for (int visual = 0; visual < runCount; visual++) {
            int run = order[visual];
            int start = bidi.getRunStart(run);
            int limit = bidi.getRunLimit(run);
            boolean rtl = (bidi.getRunLevel(run) & 1) != 0;
            List<Segment> segments = itemize(text, start, limit, role, language);
            if (rtl) java.util.Collections.reverse(segments);
            for (Segment segment : segments) {
                pen = emit(text, segment, rtl, language, nativeFace, pen, glyphs, missing, caret);
                if (segment.face() != null) metrics = metrics.include(segment.face().ascent(), segment.face().descent());
            }
        }
        int width = (int) Math.round(pen / 64.0);
        fillCaret(caret, width, bidi.baseIsLeftToRight());
        return new ShapedLine(text, glyphs, width, metrics, caret, missing, !bidi.baseIsLeftToRight());
    }

    private static boolean printableAscii(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 32 || c > 126) return false;
        }
        return true;
    }

    private static ShapedLine shapeAscii(String text, NativeFace face, LineMetrics metrics) {
        int advance = face.advance();
        List<PlacedGlyph> glyphs = new ArrayList<>(text.length());
        int[] caret = new int[text.length() + 1];
        for (int i = 0; i < text.length(); i++) {
            caret[i] = i * advance;
            char c = text.charAt(i);
            if (c != ' ') glyphs.add(new PlacedGlyph(PlacedGlyph.Kind.NATIVE, face, face.ascii(c), i * advance, 0, advance, i));
        }
        caret[text.length()] = text.length() * advance;
        return new ShapedLine(text, glyphs, text.length() * advance, metrics, caret, List.of(), false);
    }

    /** Script items of one bidi run, each split into runs of clusters drawn by one face. */
    private List<Segment> itemize(String text, int start, int limit, TextFace role, TextLanguage language) {
        List<int[]> clusters = new ArrayList<>(); // {start, end}
        List<UnicodeScript> scripts = new ArrayList<>();
        BreakIterator characters = BreakIterator.getCharacterInstance();
        characters.setText(text.substring(start, limit));
        int previous = 0;
        for (int next = characters.next(); next != BreakIterator.DONE; next = characters.next()) {
            clusters.add(new int[] {start + previous, start + next});
            scripts.add(clusterScript(text, start + previous, start + next));
            previous = next;
        }
        // Common and inherited clusters (spaces, punctuation, digits) join their neighbours' script.
        UnicodeScript carry = null;
        for (int i = 0; i < scripts.size(); i++) {
            if (scripts.get(i) != UnicodeScript.COMMON) carry = scripts.get(i);
            else if (carry != null) scripts.set(i, carry);
        }
        carry = null;
        for (int i = scripts.size() - 1; i >= 0; i--) {
            if (scripts.get(i) != UnicodeScript.COMMON) carry = scripts.get(i);
            else if (carry != null) scripts.set(i, carry);
        }
        List<HostFace> chain = registry.chain(role, language);
        List<Segment> segments = new ArrayList<>();
        int i = 0;
        while (i < clusters.size()) {
            UnicodeScript script = scripts.get(i);
            int itemEnd = i;
            while (itemEnd < clusters.size() && scripts.get(itemEnd) == script) itemEnd++;
            HostFace primary = null;
            for (HostFace face : chain) {
                if (face.supportsScript(script) && covers(face, text, clusters.get(i))) {
                    primary = face;
                    break;
                }
            }
            for (int c = i; c < itemEnd; c++) {
                int[] cluster = clusters.get(c);
                HostFace chosen = primary != null && covers(primary, text, cluster) ? primary : null;
                if (chosen == null) {
                    UnicodeScript own = clusterScript(text, cluster[0], cluster[1]);
                    for (HostFace face : chain) {
                        // Punctuation may use a simple face even beside a joined script. Letters,
                        // including Common-script joining letters such as tatweel, may not.
                        if (face.supportsScript(own) && (face.supportsScript(script) || independentCommon(text, cluster))
                                && covers(face, text, cluster)) {
                            chosen = face;
                            break;
                        }
                    }
                }
                Segment last = segments.isEmpty() ? null : segments.getLast();
                if (last != null && last.face() == chosen && last.script() == script && last.end() == cluster[0]
                        && chosen != null) {
                    segments.set(segments.size() - 1, new Segment(last.start(), cluster[1], chosen, script));
                } else segments.add(new Segment(cluster[0], cluster[1], chosen, script));
            }
            i = itemEnd;
        }
        return segments;
    }

    private static UnicodeScript clusterScript(String text, int start, int end) {
        for (int i = start; i < end; ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            UnicodeScript script = UnicodeScript.of(codePoint);
            if (script != UnicodeScript.COMMON && script != UnicodeScript.INHERITED && script != UnicodeScript.UNKNOWN)
                return script;
        }
        return UnicodeScript.COMMON;
    }

    private static boolean independentCommon(String text, int[] cluster) {
        for (int i = cluster[0]; i < cluster[1]; ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (BitmapFaceBase.ignorable(cp)) continue;
            int type = Character.getType(cp);
            if (UnicodeScript.of(cp) != UnicodeScript.COMMON || Character.isLetter(cp)
                    || type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) return false;
        }
        return true;
    }

    private static boolean covers(HostFace face, String text, int[] cluster) {
        if (!face.available()) return false;
        String value = text.substring(cluster[0], cluster[1]);
        if (face instanceof OpenTypeFace openType) return openType.covers(value);
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            i += Character.charCount(codePoint);
            if (!HostFace.SIMPLE_SCRIPTS.contains(UnicodeScript.of(codePoint)) && !BitmapFaceBase.ignorable(codePoint))
                return false;
        }
        return ((BitmapFaceBase) face).cluster(value) != null;
    }

    private long emit(String text, Segment segment, boolean rtl, TextLanguage language, NativeFace nativeFace,
                      long pen, List<PlacedGlyph> glyphs, List<String> missing, int[] caret) {
        if (segment.face() instanceof OpenTypeFace openType) {
            OpenTypeFace.Run run = openType.shape(text, segment.start(), segment.end(), rtl, segment.script(),
                    language.locale());
            if (run != null) {
                for (int i = 0; i < run.glyphs().length; i++) {
                    int cluster = run.clusters()[i];
                    int x = (int) Math.round((pen + run.xOffsets()[i]) / 64.0);
                    int y = (int) -Math.round(run.yOffsets()[i] / 64.0);
                    int advance = (int) Math.round(run.advances()[i] / 64.0);
                    if (run.glyphs()[i] == 0) {
                        // .notdef: the font lacks a glyph the shaper needed. Never report success.
                        int end = Graphemes.next(text, cluster);
                        missing.add(text.substring(cluster, Math.min(end, segment.end())));
                        glyphs.add(new PlacedGlyph(PlacedGlyph.Kind.MISSING, openType, 0, x, 0,
                                Math.max(advance, nativeFace.advance()), cluster));
                        pen += Math.max(run.advances()[i], nativeFace.advance() * 64L) - run.advances()[i];
                    } else {
                        glyphs.add(new PlacedGlyph(PlacedGlyph.Kind.BITMAP, openType, run.glyphs()[i], x, y, advance, cluster));
                    }
                    markCaret(caret, cluster, (int) Math.round(pen / 64.0));
                    pen += run.advances()[i];
                }
                return Math.round(pen / 64.0) * 64; // keep later bitmap glyphs on the pixel grid
            }
        }
        // Bitmap faces and missing clusters: one cluster at a time, visual order within the run.
        List<int[]> clusters = new ArrayList<>();
        for (int index = segment.start(); index < segment.end(); ) {
            int next = Math.min(segment.end(), Graphemes.next(text, index));
            clusters.add(new int[] {index, next});
            index = next;
        }
        if (rtl) java.util.Collections.reverse(clusters);
        for (int[] cluster : clusters) {
            String value = text.substring(cluster[0], cluster[1]);
            int penX = (int) Math.round(pen / 64.0);
            markCaret(caret, cluster[0], penX);
            if (!(segment.face() instanceof BitmapFaceBase face)) {
                missing.add(value);
                glyphs.add(new PlacedGlyph(PlacedGlyph.Kind.MISSING, null, 0, penX, 0, nativeFace.advance(), cluster[0]));
                pen += nativeFace.advance() * 64L;
                continue;
            }
            ClusterGlyphs drawn = face.cluster(rtl ? mirrored(value) : value);
            if (drawn == null) drawn = face.cluster(value);
            int baseX = penX;
            for (int g = 0; g < drawn.glyphs().length; g++) {
                int x = drawn.overlays()[g] ? baseX : (int) Math.round(pen / 64.0);
                if (!drawn.overlays()[g]) baseX = x;
                glyphs.add(new PlacedGlyph(drawn.kinds()[g], face, drawn.glyphs()[g], x, 0, drawn.advances()[g], cluster[0]));
                pen += drawn.advances()[g] * 64L;
            }
        }
        return pen;
    }

    /** Right-to-left carets are approximate: a cluster's caret is its left edge. */
    private static void markCaret(int[] caret, int index, int x) {
        if (index < 0 || index >= caret.length) return;
        if (caret[index] < 0) caret[index] = x;
    }

    /** Boundaries without their own glyph take the next known caret (or the line end). */
    private static void fillCaret(int[] caret, int width, boolean leftToRight) {
        int end = leftToRight ? width : 0;
        if (caret[caret.length - 1] < 0) caret[caret.length - 1] = end;
        for (int i = caret.length - 2; i >= 0; i--) if (caret[i] < 0) caret[i] = caret[i + 1];
    }

    /** Bracket pairs swap in right-to-left runs drawn by bitmap faces (HarfBuzz mirrors its own). */
    private static String mirrored(String cluster) {
        if (cluster.length() != 1) return cluster;
        char c = cluster.charAt(0);
        char mirror = switch (c) {
            case '(' -> ')'; case ')' -> '(';
            case '[' -> ']'; case ']' -> '[';
            case '{' -> '}'; case '}' -> '{';
            case '<' -> '>'; case '>' -> '<';
            case '«' -> '»'; case '»' -> '«';
            case '‹' -> '›'; case '›' -> '‹';
            default -> c;
        };
        return String.valueOf(mirror);
    }
}
