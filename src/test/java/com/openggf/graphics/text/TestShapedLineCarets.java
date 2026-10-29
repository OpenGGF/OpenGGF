package com.openggf.graphics.text;

import com.openggf.util.Graphemes;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** ROM-free and GL-free caret geometry and visual navigation of shaped host text. */
class TestShapedLineCarets {
    private static final HostText TEXT = HostText.shared();
    private static final TextLanguage EN = TextLanguage.of("en");
    private static final TextLanguage AR = TextLanguage.of("ar");
    private static final TextLanguage AR_RTL = new TextLanguage(Locale.forLanguageTag("ar"), TextDirection.RTL);
    private static final String ARABIC_COMPACT_FONT = "fonts/NotoSansArabicUI-Regular.ttf";
    private static final int ARABIC_COMPACT_PIXELS = 11;

    @Test
    void logicalRangesUseSeparateVisualSpansAcrossBidiAndLigatureEdges() {
        ShapedLine line = TEXT.shape("abc ابت xyz", TextFace.COMPACT, EN);
        var range = line.visualRanges(0, 5);
        assertEquals(2, range.size(), "The first Arabic character is on the other side of its visual run");
        assertEquals(new ShapedLine.VisualRange(0, line.caretX(new ShapedLine.Caret(4, true))), range.getFirst());
        assertEquals(new ShapedLine.VisualRange(line.caretX(new ShapedLine.Caret(5, true)),
                line.caretX(new ShapedLine.Caret(4, false))), range.getLast());
        ShapedLine ligature = TEXT.shape("لا", TextFace.COMPACT, AR);
        int interior = ligature.caretX(1);
        assertEquals(List.of(new ShapedLine.VisualRange(interior, ligature.width())), ligature.visualRanges(0, 1));
        assertEquals(List.of(new ShapedLine.VisualRange(0, interior)), ligature.visualRanges(1, 2));
        assertEquals(List.of(new ShapedLine.VisualRange(0, ligature.width())), ligature.visualRanges(0, 2));
        ShapedLine combining = TEXT.shape("aéb", TextFace.LABEL, EN);
        assertEquals(combining.visualRanges(1, 3), combining.visualRanges(2, 3), "A range never cuts a combining cluster");
        assertEquals(List.of(), combining.visualRanges(0, 0));
    }

    @Test
    void pureRightToLeftLeadingEdgesAreTheRightSidesOfTheShapedClusters() {
        ShapedLine line = TEXT.shape("سلام", TextFace.COMPACT, AR);
        PlacedGlyph seen = glyph(line, 0);
        assertEquals(line.width(), line.caretX(line.caretAt(0)),
                "The first logical letter's leading edge is the right end of a right-to-left line");
        assertEquals(seen.x() + seen.advance(), line.caretX(0), 1);
        assertEquals(seen.x(), line.caretX(line.caretAt(1)), 1, "Its trailing edge is the glyph's left side");
        assertEquals(0, line.caretX(line.caretAt(4)));
        assertEquals(new ShapedLine.Caret(4, true), line.caretAt(4));
        for (int index = 1; index <= 4; index++) {
            assertTrue(line.caretX(index) < line.caretX(index - 1), "Logical order runs leftwards at " + index);
        }
        assertNavigable(line);
    }

    @Test
    void ligatureInteriorBoundariesUseTheFontsLigatureCarets() throws Exception {
        byte[] font;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(ARABIC_COMPACT_FONT)) {
            font = in.readAllBytes();
        }
        for (String sample : new String[] {"لا", "سلام"}) {
            ShapedLine line = TEXT.shape(sample, TextFace.COMPACT, AR);
            int lam = sample.indexOf('ل');
            PlacedGlyph ligature = glyph(line, lam);
            assertTrue(line.glyphs().stream().noneMatch(g -> g.cluster() == lam + 1),
                    sample + ": lam-alef must be one ligature glyph");
            int boundary = line.caretX(lam + 1);
            assertTrue(boundary > ligature.x() && boundary < ligature.x() + ligature.advance(),
                    sample + ": the lam|alef boundary lies inside the ligature, not at an edge: " + boundary);
            int[] carets = gdefLigatureCarets(font, ligature.glyph());
            int expected = carets != null && carets.length == 1
                    ? ligature.x() + Math.round(carets[0] * (float) ARABIC_COMPACT_PIXELS / unitsPerEm(font))
                    : ligature.x() + ligature.advance() / 2;
            assertEquals(expected, boundary, 1, sample + " against the GDEF caret " + java.util.Arrays.toString(carets));
            assertEquals(line.caretX(new ShapedLine.Caret(lam + 1, true)), boundary,
                    "Inside one run both affinities of a boundary share its position");
            assertNavigable(line);
        }
    }

    @Test
    void mixedDirectionBoundariesHoldBothEditPointsInVisualOrder() {
        String text = "abc ابت xyz";
        ShapedLine line = TEXT.shape(text, TextFace.COMPACT, EN);
        assertFalse(line.rightToLeft());
        int arabicEnd = line.width() - 20;
        // Logical indices 4 and 7 each have two positions: the Arabic run's left and right ends.
        assertEquals(20, line.caretX(new ShapedLine.Caret(4, true)), "After the space");
        assertEquals(20, line.caretX(new ShapedLine.Caret(7, true)), "After the last Arabic letter");
        assertEquals(arabicEnd, line.caretX(line.caretAt(4)), "Before the first Arabic letter");
        assertEquals(arabicEnd, line.caretX(line.caretAt(7)), "Before the second space");
        List<ShapedLine.Caret> expected = List.of(c(0, false), c(1, false), c(2, false), c(3, false),
                c(4, true), c(7, true), c(6, false), c(5, false), c(4, false), c(7, false),
                c(8, false), c(9, false), c(10, false), c(11, true));
        assertEquals(expected, walk(line, 1));
        assertEquals(expected.reversed(), walk(line, -1));
        assertEquals(c(4, true), line.hitCaret(19), "A click left of the boundary attaches to the left run");
        assertEquals(c(7, true), line.hitCaret(21), "A click right of it attaches to the Arabic run");
        assertEquals(c(7, false), line.hitCaret(arabicEnd + 1));
        assertEquals(7, line.hitIndex(arabicEnd + 1));
        assertNavigable(line);
    }

    @Test
    void rightToLeftParagraphsWithEmbeddedLatinAndDigitsAreFullyNavigable() {
        for (String sample : new String[] {"حفظ ROM 123", "ابت abc", "abc ابت", "١٢٣ حفظ (ROM)"}) {
            ShapedLine line = TEXT.shape(sample, TextFace.COMPACT, AR_RTL);
            assertTrue(line.rightToLeft(), sample);
            assertEquals(line.width(), line.caretX(line.edgeCaret(1)), sample);
            assertNavigable(line);
        }
        ShapedLine embedded = TEXT.shape("ابت abc", TextFace.COMPACT, AR_RTL);
        assertEquals(c(0, false), embedded.edgeCaret(1), "The logical start is the right end");
        assertEquals(embedded.width(), embedded.caretX(embedded.caretAt(0)));
    }

    @Test
    void marksAccentsAndConjunctsNeverCreateCaretStopsInsideAGrapheme() {
        String[][] samples = {{"ar", "سَلَامٌ"}, {"ar", "الإعدادات"}, {"ur", "ٹھیک ہے"}, {"hi", "क्षमा कि"},
                {"bn", "ক্ষমা কি"}, {"th", "น้ำ การตั้งค่า"}, {"en", "Zoé!"}, {"fr", "déjà été"}};
        for (String[] sample : samples) {
            ShapedLine line = TEXT.shape(sample[1], TextFace.COMPACT, TextLanguage.of(sample[0]));
            assertNavigable(line);
            for (int index = 1; index < sample[1].length(); index++) {
                if (Graphemes.isBoundary(sample[1], index)) continue;
                assertEquals(Graphemes.next(sample[1], index), line.caretAt(index).index(),
                        sample[0] + ": an index inside a grapheme takes the following boundary");
            }
        }
        ShapedLine harakat = TEXT.shape("سَلَامٌ", TextFace.COMPACT, AR);
        List<Integer> boundaries = boundaries("سَلَامٌ");
        for (int i = 1; i < boundaries.size(); i++) {
            assertTrue(harakat.caretX(boundaries.get(i)) < harakat.caretX(boundaries.get(i - 1)),
                    "Each vowelled Arabic grapheme has its own advance");
        }
    }

    @Test
    void proportionalCaretPositionsAndFixedOverlayCellsKeepLogicalIndices() {
        ShapedLine ascii = TEXT.shape("AB", TextFace.COMPACT, EN);
        assertEquals(List.of(c(0, false), c(1, false), c(2, true)), walk(ascii, 1));
        assertEquals(6, ascii.caretX(new ShapedLine.Caret(1, true)));
        assertEquals(12, ascii.caretX(2));
        assertEquals(12, ascii.caretX(99), "Indices past the end clamp");
        assertEquals(0, ascii.caretX(-3));
        ShapedLine label = TEXT.shape("OpenGGF 1.0", TextFace.LABEL, EN);
        int[] positions = {0, 9, 18, 27, 36, 45, 54, 63, 67, 76, 81, 90};
        for (int index = 0; index <= 11; index++) assertEquals(positions[index], label.caretX(index));
        ShapedLine narrow = TEXT.shape("iWi", TextFace.COMPACT, EN);
        assertEquals(2, narrow.caretX(1));
        assertEquals(8, narrow.caretX(2));
        assertEquals(10, narrow.caretX(3));
        ShapedLine fixed = TEXT.shape("iWi", TextFace.FIXED, EN);
        for (int index = 0; index <= 3; index++) assertEquals(index * 6, fixed.caretX(index));
        assertNavigable(label);
        assertNavigable(narrow);
        assertNavigable(fixed);
    }

    @Test
    void invisibleControlsAndMissingClustersKeepDistinctStopsInLogicalOrder() {
        String controls = "⁨ROM⁩‍";
        ShapedLine line = TEXT.shape(controls, TextFace.COMPACT, EN);
        List<ShapedLine.Caret> stops = walk(line, 1);
        List<Integer> boundaries = boundaries(controls);
        assertEquals(boundaries, stops.stream().map(ShapedLine.Caret::index).toList(),
                "Zero-width graphemes are separate, ordered positions at the same x");
        for (int index : boundaries) {
            assertEquals(6 * Math.clamp(index - 1, 0, 3), line.caretX(index), "x at " + index);
        }
        assertNavigable(line);
        ShapedLine emoji = TEXT.shape("A👨‍👩‍👧B", TextFace.COMPACT, EN);
        assertEquals(List.of(0, 1, 9, 10), walk(emoji, 1).stream().map(ShapedLine.Caret::index).toList(),
                "A missing ZWJ sequence is one box with one boundary on each side");
        assertEquals(12, emoji.caretX(9));
        assertNavigable(emoji);
    }

    @Test
    void emptyLinesHaveOneCaretAtTheOrigin() {
        for (TextLanguage language : new TextLanguage[] {EN, AR, AR_RTL}) {
            ShapedLine line = TEXT.shape("", TextFace.COMPACT, language);
            ShapedLine.Caret origin = c(0, false);
            assertEquals(origin, line.caretAt(0));
            assertEquals(origin, line.caretAt(5));
            assertEquals(origin, line.edgeCaret(-1));
            assertEquals(origin, line.edgeCaret(1));
            assertEquals(origin, line.moveCaret(new ShapedLine.Caret(0, true), 1));
            assertEquals(origin, line.hitCaret(40));
            assertEquals(0, line.caretX(new ShapedLine.Caret(3, true)));
            assertEquals(0, line.hitIndex(-10));
        }
        assertThrows(IllegalArgumentException.class, () -> new ShapedLine.Caret(-1, false));
    }

    /**
     * Visual navigation invariants: a walk from either edge visits every grapheme boundary and
     * nothing else, x never decreases left to right, every step is undone by the opposite step,
     * and every caret of the line (both affinities) lies on the walk.
     */
    private static void assertNavigable(ShapedLine line) {
        String text = line.text();
        List<ShapedLine.Caret> right = walk(line, 1);
        assertEquals(line.edgeCaret(-1), right.getFirst(), text);
        assertEquals(line.edgeCaret(1), right.getLast(), text);
        assertEquals(right.reversed(), walk(line, -1), text + ": leftward and rightward walks agree");
        assertEquals(0, line.caretX(right.getFirst()), text);
        assertEquals(line.width(), line.caretX(right.getLast()), text);
        List<Integer> boundaries = boundaries(text);
        assertEquals(boundaries, right.stream().map(ShapedLine.Caret::index).distinct().sorted().toList(),
                text + ": every grapheme boundary and only those are reachable");
        for (int i = 1; i < right.size(); i++) {
            assertTrue(line.caretX(right.get(i)) >= line.caretX(right.get(i - 1)), text + " x order at " + i);
            assertEquals(right.get(i - 1), line.moveCaret(right.get(i), -1), text + " step " + i + " back");
            assertEquals(right.get(i), line.moveCaret(right.get(i - 1), 1), text + " step " + i);
        }
        for (int index : boundaries) {
            assertTrue(right.contains(line.caretAt(index)), text + ": caretAt(" + index + ") is a walk position");
            for (boolean trailing : new boolean[] {false, true}) {
                ShapedLine.Caret caret = new ShapedLine.Caret(index, trailing);
                int x = line.caretX(caret);
                ShapedLine.Caret same = line.moveCaret(caret, 0);
                assertTrue(right.contains(same), text + ": " + caret + " resolves to a walk position");
                assertEquals(x, line.caretX(same), text + ": " + caret);
                assertEquals(index, same.index(), text + ": " + caret + " keeps its edit point");
                for (int direction : new int[] {-1, 1}) {
                    ShapedLine.Caret moved = line.moveCaret(caret, direction);
                    if (moved.equals(same)) continue; // at a line edge
                    ShapedLine.Caret back = line.moveCaret(moved, -direction);
                    assertEquals(same, back, text + ": " + caret + " " + direction + " then back");
                }
                ShapedLine.Caret hit = line.hitCaret(x);
                assertEquals(x, line.caretX(hit), text + ": hit-testing a caret's x lands on that x");
            }
        }
    }

    /** Carets from the left (direction 1) or right edge until the walk stops moving. */
    private static List<ShapedLine.Caret> walk(ShapedLine line, int direction) {
        List<ShapedLine.Caret> out = new ArrayList<>();
        ShapedLine.Caret caret = line.edgeCaret(-direction);
        out.add(caret);
        for (int guard = 0; guard <= 2 * line.text().length() + 2; guard++) {
            ShapedLine.Caret next = line.moveCaret(caret, direction);
            if (next.equals(caret)) return out;
            out.add(caret = next);
        }
        fail(line.text() + ": visual walk does not terminate: " + out);
        return out;
    }

    private static List<Integer> boundaries(String text) {
        List<Integer> out = new ArrayList<>();
        out.add(0);
        for (int index = 0; index < text.length(); ) out.add(index = Graphemes.next(text, index));
        return out;
    }

    private static ShapedLine.Caret c(int index, boolean trailing) {
        return new ShapedLine.Caret(index, trailing);
    }

    private static PlacedGlyph glyph(ShapedLine line, int cluster) {
        return line.glyphs().stream().filter(g -> g.cluster() == cluster).findFirst()
                .orElseThrow(() -> new AssertionError("No glyph for cluster " + cluster + " in " + line.text()));
    }

    private static int unitsPerEm(byte[] font) {
        return u16(ByteBuffer.wrap(font), table(font, "head") + 18);
    }

    /**
     * Independent reference: the font's GDEF LigCaretList coordinates (font units) for
     * {@code glyph}, or null when the glyph has no entry or uses contour-point carets.
     */
    private static int[] gdefLigatureCarets(byte[] font, int glyph) {
        ByteBuffer data = ByteBuffer.wrap(font);
        int gdef = table(font, "GDEF");
        if (gdef < 0 || u16(data, gdef + 8) == 0) return null;
        int list = gdef + u16(data, gdef + 8);
        int coverage = list + u16(data, list);
        int coverageIndex = -1;
        if (u16(data, coverage) == 1) {
            for (int i = 0; i < u16(data, coverage + 2); i++) {
                if (u16(data, coverage + 4 + 2 * i) == glyph) coverageIndex = i;
            }
        } else {
            for (int r = 0; r < u16(data, coverage + 2); r++) {
                int range = coverage + 4 + 6 * r;
                if (glyph >= u16(data, range) && glyph <= u16(data, range + 2))
                    coverageIndex = u16(data, range + 4) + glyph - u16(data, range);
            }
        }
        if (coverageIndex < 0 || coverageIndex >= u16(data, list + 2)) return null;
        int ligGlyph = list + u16(data, list + 4 + 2 * coverageIndex);
        int[] carets = new int[u16(data, ligGlyph)];
        for (int i = 0; i < carets.length; i++) {
            int value = ligGlyph + u16(data, ligGlyph + 2 + 2 * i);
            if (u16(data, value) == 2) return null;
            carets[i] = data.getShort(value + 2);
        }
        java.util.Arrays.sort(carets);
        return carets;
    }

    private static int table(byte[] font, String tag) {
        ByteBuffer data = ByteBuffer.wrap(font);
        for (int i = 0; i < u16(data, 4); i++) {
            int record = 12 + 16 * i;
            if (new String(font, record, 4, java.nio.charset.StandardCharsets.ISO_8859_1).equals(tag))
                return data.getInt(record + 8);
        }
        return -1;
    }

    private static int u16(ByteBuffer data, int offset) {
        return data.getShort(offset) & 0xFFFF;
    }
}
