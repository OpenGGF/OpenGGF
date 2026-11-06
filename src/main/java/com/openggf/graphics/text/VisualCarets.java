package com.openggf.graphics.text;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Caret geometry of one {@link ShapedLine}, derived from the extents of the shaped clusters
 * exactly as they were drawn.
 *
 * <p>Shaping records each drawn cluster (a HarfBuzz cluster, or one bitmap grapheme) with its
 * logical UTF-16 range, its pen extent and its run direction, in drawing (left-to-right) order.
 * Those extents are cut at every extended grapheme boundary of the whole line: a boundary
 * inside a ligature uses the font's GDEF ligature carets when the font lists exactly one per
 * interior boundary, otherwise the cluster advance is divided evenly between its graphemes.
 * Pieces that meet inside one grapheme (a base and its marks) are merged, so no caret position
 * ever splits a grapheme.
 *
 * <p>The visual stops are the edges between those pieces from left to right. At each stop the
 * edge of the piece on its left and the edge of the piece on its right name a caret: a
 * left-to-right piece's left edge is its start's leading caret and its right edge its end's
 * trailing caret; a right-to-left piece is mirrored. When both name the same logical index
 * (a same-direction boundary) the stop is one position, represented by its leading caret.
 * When they name different indices (a bidi run boundary) the stop holds two positions at the
 * same x, left-attached first. Visual movement steps through this one ordered list, so every
 * step is reversible and every editable boundary is reachable in either direction.
 */
final class VisualCarets {
    /** Drawn cluster extents in drawing order; positions are 26.6 fixed-point pixels. */
    static final class Builder {
        private int size;
        private int[] starts = new int[8];
        private int[] ends = new int[8];
        private long[] lefts = new long[8];
        private long[] rights = new long[8];
        private boolean[] rtl = new boolean[8];
        private long[][] carets = new long[8][];

        /**
         * One drawn cluster covering text {@code [start, end)} between pen positions
         * {@code left} and {@code right}. {@code ligatureCarets}, when present, are absolute
         * 26.6 positions inside the cluster in ascending x.
         */
        void add(int start, int end, long left, long right, boolean rightToLeft, long[] ligatureCarets) {
            if (start >= end) return;
            if (size == starts.length) {
                int capacity = size * 2;
                starts = Arrays.copyOf(starts, capacity);
                ends = Arrays.copyOf(ends, capacity);
                lefts = Arrays.copyOf(lefts, capacity);
                rights = Arrays.copyOf(rights, capacity);
                rtl = Arrays.copyOf(rtl, capacity);
                carets = Arrays.copyOf(carets, capacity);
            }
            starts[size] = start;
            ends[size] = end;
            lefts[size] = left;
            rights[size] = Math.max(left, right);
            rtl[size] = rightToLeft;
            carets[size] = ligatureCarets;
            size++;
        }

        VisualCarets build(String text, int width) {
            return new VisualCarets(text, width, Arrays.copyOf(starts, size), Arrays.copyOf(ends, size),
                    Arrays.copyOf(lefts, size), Arrays.copyOf(rights, size), Arrays.copyOf(rtl, size),
                    Arrays.copyOf(carets, size));
        }
    }

    private record Piece(int start, int end, int left, int right, boolean rtl) { }

    /** Caret positions in visual order, and each caret's position. */
    private record Layout(int[] index, boolean[] trailing, int[] x, int[] leading, int[] trailingAt,
                          boolean[] boundary) { }

    private final String text;
    private final int width;
    private final int[] starts;
    private final int[] ends;
    private final long[] lefts;
    private final long[] rights;
    private final boolean[] rtl;
    private final long[][] ligatureCarets;
    private volatile Layout layout;

    private VisualCarets(String text, int width, int[] starts, int[] ends, long[] lefts, long[] rights,
                         boolean[] rtl, long[][] ligatureCarets) {
        this.text = text;
        this.width = width;
        this.starts = starts;
        this.ends = ends;
        this.lefts = lefts;
        this.rights = rights;
        this.rtl = rtl;
        this.ligatureCarets = ligatureCarets;
    }

    // --- Public operations, all on canonical carets ---

    ShapedLine.Caret caretAt(int index) {
        Layout resolved = layout();
        int at = snap(resolved, index);
        return new ShapedLine.Caret(at, at == text.length() && at > 0);
    }

    int caretX(ShapedLine.Caret caret) {
        Layout resolved = layout();
        if (resolved.x.length == 0) return 0;
        return resolved.x[position(resolved, caret)];
    }

    ShapedLine.Caret move(ShapedLine.Caret caret, int direction) {
        Layout resolved = layout();
        if (resolved.x.length == 0) return new ShapedLine.Caret(0, false);
        int position = position(resolved, caret);
        int target = Math.clamp(position + (long) Integer.signum(direction), 0, resolved.x.length - 1);
        return caretOf(resolved, target);
    }

    ShapedLine.Caret edge(int direction) {
        Layout resolved = layout();
        if (resolved.x.length == 0) return new ShapedLine.Caret(0, false);
        return caretOf(resolved, direction < 0 ? 0 : resolved.x.length - 1);
    }

    List<ShapedLine.VisualRange> ranges(int start, int end) {
        Layout resolved = layout();
        int first = Math.clamp(start, 0, text.length());
        int last = Math.clamp(end, first, text.length());
        if (last == first) return List.of();
        while (!resolved.boundary[first]) first--;
        while (!resolved.boundary[last]) last++;
        List<ShapedLine.VisualRange> spans = new ArrayList<>();
        for (int at = first; at < last;) {
            int next = at + 1;
            while (!resolved.boundary[next]) next++;
            int leading = caretX(new ShapedLine.Caret(at, false));
            int trailing = caretX(new ShapedLine.Caret(next, true));
            spans.add(new ShapedLine.VisualRange(Math.min(leading, trailing), Math.max(leading, trailing)));
            at = next;
        }
        spans.sort(java.util.Comparator.comparingInt(ShapedLine.VisualRange::left));
        List<ShapedLine.VisualRange> merged = new ArrayList<>();
        for (ShapedLine.VisualRange span : spans) {
            if (!merged.isEmpty() && span.left() <= merged.getLast().right()) {
                var previous = merged.removeLast();
                merged.add(new ShapedLine.VisualRange(previous.left(), Math.max(previous.right(), span.right())));
            } else merged.add(span);
        }
        return List.copyOf(merged);
    }

    /**
     * Nearest position to {@code x}. Equal distances to different x prefer the left one;
     * coincident positions resolve towards the side of the click.
     */
    ShapedLine.Caret hit(int x) {
        Layout resolved = layout();
        if (resolved.x.length == 0) return new ShapedLine.Caret(0, false);
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int p = 0; p < resolved.x.length; p++) {
            long distance = Math.abs((long) resolved.x[p] - x);
            boolean coincident = p > 0 && resolved.x[p] == resolved.x[best];
            if (distance < bestDistance || distance == bestDistance && coincident && x >= resolved.x[p]) {
                best = p;
                bestDistance = distance;
            }
        }
        return caretOf(resolved, best);
    }

    private static ShapedLine.Caret caretOf(Layout layout, int position) {
        return new ShapedLine.Caret(layout.index[position], layout.trailing[position]);
    }

    /** Position of a caret after snapping its index to a grapheme boundary and its edge to one that exists. */
    private int position(Layout layout, ShapedLine.Caret caret) {
        int at = snap(layout, caret.index());
        boolean trailing = caret.trailing();
        if (at == 0) trailing = false;
        if (at == text.length()) trailing = true;
        return trailing ? layout.trailingAt[at] : layout.leading[at];
    }

    /** Clamps to the text and moves an index inside a grapheme to the following boundary. */
    private int snap(Layout layout, int index) {
        int at = Math.clamp(index, 0, text.length());
        while (!layout.boundary[at]) at++;
        return at;
    }

    // --- Resolution ---

    private Layout layout() {
        Layout resolved = layout;
        if (resolved == null) layout = resolved = resolve();
        return resolved;
    }

    private Layout resolve() {
        int length = text.length();
        boolean[] boundary = new boolean[length + 1];
        BreakIterator characters = BreakIterator.getCharacterInstance();
        characters.setText(text);
        for (int b = characters.first(); b != BreakIterator.DONE; b = characters.next()) boundary[b] = true;
        boundary[0] = true;
        boundary[length] = true;

        List<Piece> pieces = merge(cut(boundary), boundary);
        coverGaps(pieces, boundary);

        int n = pieces.size();
        int[] index = new int[2 * n + 2];
        boolean[] trailing = new boolean[2 * n + 2];
        int[] xs = new int[2 * n + 2];
        int[] leading = new int[length + 1];
        int[] trailingAt = new int[length + 1];
        Arrays.fill(leading, -1);
        Arrays.fill(trailingAt, -1);
        int count = 0;
        if (length == 0) return new Layout(new int[0], new boolean[0], new int[0], leading, trailingAt, boundary);
        for (int stop = 0; stop <= n; stop++) {
            Piece left = stop > 0 ? pieces.get(stop - 1) : null;
            Piece right = stop < n ? pieces.get(stop) : null;
            // The left piece's right edge and the right piece's left edge.
            int leftIndex = left == null ? -1 : left.rtl() ? left.start() : left.end();
            boolean leftTrailing = left != null && !left.rtl();
            int rightIndex = right == null ? -1 : right.rtl() ? right.end() : right.start();
            boolean rightTrailing = right != null && right.rtl();
            boolean leftValid = left != null && boundary[leftIndex];
            boolean rightValid = right != null && boundary[rightIndex];
            if (leftValid && rightValid && leftIndex == rightIndex) {
                // Same-direction boundary: one position, named by its leading caret.
                boolean useRight = leftTrailing;
                index[count] = leftIndex;
                trailing[count] = useRight ? rightTrailing : leftTrailing;
                xs[count] = useRight ? right.left() : left.right();
                register(leading, trailingAt, leftIndex, leftTrailing, count);
                register(leading, trailingAt, rightIndex, rightTrailing, count);
                count++;
                continue;
            }
            if (leftValid) {
                index[count] = leftIndex;
                trailing[count] = leftTrailing;
                xs[count] = left.right();
                register(leading, trailingAt, leftIndex, leftTrailing, count++);
            }
            if (rightValid) {
                index[count] = rightIndex;
                trailing[count] = rightTrailing;
                xs[count] = right.left();
                register(leading, trailingAt, rightIndex, rightTrailing, count++);
            }
        }
        // Every boundary has both edges by construction except the text ends, which have one:
        // the missing affinity there (and any defensive gap) takes the other one.
        for (int at = 0; at <= length; at++) {
            if (!boundary[at]) continue;
            if (leading[at] < 0) leading[at] = Math.max(0, trailingAt[at]);
            if (trailingAt[at] < 0) trailingAt[at] = Math.max(0, leading[at]);
        }
        return new Layout(Arrays.copyOf(index, count), Arrays.copyOf(trailing, count), Arrays.copyOf(xs, count),
                leading, trailingAt, boundary);
    }

    private static void register(int[] leading, int[] trailingAt, int index, boolean trailing, int position) {
        if (trailing) trailingAt[index] = position;
        else leading[index] = position;
    }

    /** Splits each drawn cluster at the grapheme boundaries inside it, in drawing order. */
    private List<Piece> cut(boolean[] boundary) {
        List<Piece> pieces = new ArrayList<>(starts.length);
        for (int u = 0; u < starts.length; u++) {
            int start = starts[u];
            int end = ends[u];
            int interior = 0;
            for (int b = start + 1; b < end; b++) if (boundary[b]) interior++;
            if (interior == 0) {
                pieces.add(new Piece(start, end, px(lefts[u]), px(rights[u]), rtl[u]));
                continue;
            }
            int[] cuts = new int[interior + 2];
            cuts[0] = start;
            cuts[interior + 1] = end;
            for (int b = start + 1, c = 1; b < end; b++) if (boundary[b]) cuts[c++] = b;
            // Visual edges from left to right: the cluster's own edges and one per interior boundary.
            long[] edges = new long[interior + 2];
            edges[0] = lefts[u];
            edges[interior + 1] = rights[u];
            long[] font = ligatureCarets[u];
            boolean useFont = font != null && font.length == interior;
            for (int c = 0; useFont && c < interior; c++) {
                long previous = c == 0 ? lefts[u] : font[c - 1];
                useFont = font[c] >= previous && font[c] <= rights[u];
            }
            for (int c = 1; c <= interior; c++) {
                edges[c] = useFont ? font[c - 1]
                        : lefts[u] + (rights[u] - lefts[u]) * c / (interior + 1);
            }
            for (int v = 0; v <= interior; v++) {
                // Left-to-right: the v-th visual piece is the v-th logical one; right-to-left mirrors.
                int logical = rtl[u] ? interior - v : v;
                pieces.add(new Piece(cuts[logical], cuts[logical + 1], px(edges[v]), px(edges[v + 1]), rtl[u]));
            }
        }
        return pieces;
    }

    /** Joins visually adjacent pieces of one direction that meet inside a grapheme. */
    private static List<Piece> merge(List<Piece> pieces, boolean[] boundary) {
        List<Piece> merged = new ArrayList<>(pieces.size());
        for (Piece piece : pieces) {
            Piece last = merged.isEmpty() ? null : merged.getLast();
            if (last != null && last.rtl() == piece.rtl()) {
                // Shaped runs are monotone, so the shared index is on the reading-order side;
                // either side is accepted so that a base and its marks always join.
                int shared = last.end() == piece.start() ? piece.start()
                        : piece.end() == last.start() ? last.start() : -1;
                if (shared >= 0 && !boundary[shared]) {
                    merged.set(merged.size() - 1, new Piece(Math.min(last.start(), piece.start()),
                            Math.max(last.end(), piece.end()), last.left(), Math.max(last.right(), piece.right()),
                            piece.rtl()));
                    continue;
                }
            }
            merged.add(piece);
        }
        return merged;
    }

    /**
     * Shaping covers every character; should a range ever lack a drawn cluster, it becomes a
     * zero-width left-to-right piece at the line's right end so that its boundaries stay editable.
     */
    private void coverGaps(List<Piece> pieces, boolean[] boundary) {
        boolean[] covered = new boolean[text.length()];
        for (Piece piece : pieces) Arrays.fill(covered, piece.start(), piece.end(), true);
        for (int at = 0; at < covered.length; ) {
            if (covered[at]) {
                at++;
                continue;
            }
            int end = at + 1;
            while (end < covered.length && !covered[end] && !boundary[end]) end++;
            pieces.add(new Piece(at, end, width, width, false));
            at = end;
        }
    }

    private static int px(long fixed) {
        return (int) Math.round(fixed / 64.0);
    }
}
