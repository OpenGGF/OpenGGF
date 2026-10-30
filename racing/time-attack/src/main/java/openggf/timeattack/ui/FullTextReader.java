package openggf.timeattack.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.ui.NameFont;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The racing menus' details page: the complete text a compact page can only preview, opened with
 * F1 (R3 on a controller) and read-only. It echoes the engine's details screens: the owning page's
 * title with a DETAILS chip, facts as a label column (every value starts after the widest short
 * label and wraps under itself), section captions, and sentences wrapped at spaces by measured
 * width. A word wider than a line breaks after a path or address separator when it has one, else
 * between graphemes. Wrapping never drops or adds a character, so the rows read in order are the
 * exact text; code points the bundled fonts cannot draw keep their identity as {@code [U+XXXX]}.
 */
public final class FullTextReader {
    /** What a details page says, in order. Blank values and sentences are left out. */
    public static final class Text {
        private final String title;
        private final String context;
        private final List<Entry> entries = new ArrayList<>();

        private Text(String title, String context) {
            this.title = title == null || title.isBlank() ? "DETAILS" : title;
            this.context = context == null ? "" : context;
        }

        /** A page titled {@code title} (the owning page's title, in capitals). */
        public static Text of(String title) { return new Text(title, ""); }

        /** As {@link #of(String)}, with a line under the title saying what the page is about. */
        public static Text of(String title, String context) { return new Text(title, context); }

        /** A caption over the entries that follow; a caption with nothing under it is not shown. */
        public Text section(String caption) {
            if (caption != null && !caption.isBlank()) entries.add(new Entry(Kind.SECTION, caption, ""));
            return this;
        }

        /** A fact: a short label and its complete value. */
        public Text field(String label, String value) {
            if (value != null && !value.isBlank()) entries.add(new Entry(Kind.FIELD, label == null ? "" : label, value));
            return this;
        }

        /** A sentence or a typed line, wrapped across the page. */
        public Text line(String text) {
            if (text != null && !text.isBlank()) entries.add(new Entry(Kind.LINE, "", text));
            return this;
        }

        public String title() { return title; }

        public String context() { return context; }

        /** Every caption, label, value and sentence in order (tests and logs). */
        public String plain() {
            StringBuilder out = new StringBuilder(title);
            if (!context.isEmpty()) out.append(" | ").append(context);
            for (Entry entry : entries) {
                out.append(" | ");
                if (entry.kind() == Kind.FIELD) out.append(entry.label()).append(": ");
                out.append(entry.kind() == Kind.SECTION ? entry.label() : entry.value());
            }
            return out.toString();
        }
    }

    private enum Kind { SECTION, FIELD, LINE }

    private record Entry(Kind kind, String label, String value) { }

    /** One drawn row: a caption, a field's label (on its first row) and value text, or sentence text. */
    private record Row(Kind kind, String label, String text) {
        String plain() { return label + text; }
    }

    private static final int LEFT = MenuPage.MARGIN + 6;
    private static final int LABEL_GAP = 10;

    private Supplier<Text> content;
    private int offset;
    private MenuPage.Keys keys = MenuPage.Keys.keyboard();
    private int capacity = 9;
    private int lineCount;
    private List<String> visible = List.of();

    public boolean isOpen() { return content != null; }

    public void open(Supplier<Text> source) {
        content = source;
        offset = 0;
    }

    public void close() { content = null; }

    /**
     * Consumes the entire tick while open (the closing confirm included) and the opening press, so
     * no menu action sees either. Up/Down move a line, Left/Right a page; Back, confirm, Escape and
     * the details key close it.
     */
    public boolean handle(ViewInput input, Supplier<Text> source) {
        keys = MenuPage.Keys.of(input, keys);
        if (!isOpen()) {
            if (!input.details()) return false;
            open(source);
            return true;
        }
        if (input.back() || input.accept() || input.keyPressed(SceneKeys.ESCAPE) || input.details()) {
            close();
            return true;
        }
        int step = input.up() || input.keyPressed(SceneKeys.UP) ? -1
                : input.down() || input.keyPressed(SceneKeys.DOWN) ? 1
                : input.left() || input.keyPressed(SceneKeys.LEFT) ? -capacity
                : input.right() || input.keyPressed(SceneKeys.RIGHT) ? capacity : 0;
        offset = Math.clamp(offset + step, 0, Math.max(0, lineCount - capacity));
        return true;
    }

    /** Plain lines under a plain title, for callers without fields (each string is one sentence). */
    public boolean handleLines(ViewInput input, Supplier<List<String>> source) {
        return handle(input, () -> {
            Text text = Text.of("DETAILS");
            for (String line : source.get()) text.line(line);
            return text;
        });
    }

    public boolean draw(SceneCanvas canvas) {
        if (!isOpen()) return false;
        Text text = content.get();
        int width = canvas.width();
        // The context line sits under the title when it fits there; a longer one opens the body.
        boolean underTitle = NameFont.width(text.context()) <= width - 2 * MenuPage.MARGIN;
        MenuPage.page(canvas, text.title(), "Details", underTitle ? text.context() : null);
        List<Row> rows = new ArrayList<>();
        if (!underTitle) {
            for (String line : wrap(readable(text.context()), room(width))) rows.add(new Row(Kind.LINE, "", line));
        }
        rows.addAll(layout(text, width));
        int firstRow = 0;
        int lastRow = NameFont.LINE_HEIGHT - 1;
        for (Row row : rows) {
            int[] extent = NameFont.extent(row.plain());
            firstRow = Math.min(firstRow, extent[0]);
            lastRow = Math.max(lastRow, extent[1]);
        }
        int pitch = lastRow - firstRow + 1;
        int bodyTop = text.context().isBlank() || !underTitle ? MenuPage.HEADER_H + 6 : MenuPage.BODY_TOP;
        int bodyBottom = MenuPage.FOOTER_Y - 4;
        capacity = Math.max(1, (bodyBottom - bodyTop) / pitch);
        lineCount = rows.size();
        offset = Math.clamp(offset, 0, Math.max(0, lineCount - capacity));
        List<Row> shown = rows.subList(offset, Math.min(rows.size(), offset + capacity));
        visible = shown.stream().map(Row::plain).toList();
        int valueX = LEFT + labelColumn(text, width);
        int y = bodyTop - firstRow;
        canvas.clip(MenuPage.MARGIN, bodyTop, Math.max(1, width - 2 * MenuPage.MARGIN), capacity * pitch);
        for (Row row : shown) {
            switch (row.kind()) {
                case SECTION -> MenuPage.section(canvas, row.text(), LEFT, y + 3, room(width));
                case FIELD -> {
                    if (!row.label().isEmpty()) NameFont.draw(canvas, row.label(), LEFT, y, MenuPage.MUTED);
                    NameFont.draw(canvas, row.text(), valueX, y, MenuPage.TEXT);
                }
                case LINE -> NameFont.draw(canvas, row.text(), LEFT, y, MenuPage.TEXT);
            }
            y += pitch;
        }
        canvas.unclip();
        MenuPage.scrollbar(canvas, width - MenuPage.MARGIN - 2, bodyTop, capacity * pitch, offset, capacity, lineCount);
        List<MenuPage.Hint> hints = new ArrayList<>();
        boolean scrolls = lineCount > capacity;
        if (scrolls) {
            hints.add(MenuPage.hint(MenuPage.ARROWS_UD, "Scroll"));
            hints.add(MenuPage.hint(MenuPage.ARROWS_LR, "Page"));
        }
        hints.add(MenuPage.hint(keys.back(), "Close"));
        String position = scrolls ? (offset + 1) + "-" + (offset + shown.size()) + " of " + lineCount : "";
        MenuPage.footer(canvas, position, MenuPage.DIM, hints);
        return true;
    }

    /** Text width the rows may use: the margins and the scroll bar's lane stay clear. */
    private static int room(int width) {
        return width - LEFT - MenuPage.MARGIN - 8;
    }

    /** The value column: the widest field label and a gap, at most two fifths of the row. */
    private static int labelColumn(Text text, int width) {
        int widest = 0;
        for (Entry entry : text.entries) {
            if (entry.kind() == Kind.FIELD) widest = Math.max(widest, NameFont.width(readable(entry.label())));
        }
        return widest == 0 ? 0 : Math.min(widest + LABEL_GAP, room(width) * 2 / 5);
    }

    private static List<Row> layout(Text text, int width) {
        int room = room(width);
        int column = labelColumn(text, width);
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < text.entries.size(); index++) {
            Entry entry = text.entries.get(index);
            switch (entry.kind()) {
                case SECTION -> {
                    boolean followed = index + 1 < text.entries.size()
                            && text.entries.get(index + 1).kind() != Kind.SECTION;
                    if (followed) rows.add(new Row(Kind.SECTION, "", entry.label()));
                }
                case FIELD -> {
                    String label = readable(entry.label());
                    // User-authored labels (such as pool names) need the same lossless fallback
                    // and wrapping as values, including when fallback notation widens a name.
                    boolean own = NameFont.width(label) + LABEL_GAP > column;
                    if (own) {
                        for (String line : wrap(label, room)) rows.add(new Row(Kind.FIELD, line, ""));
                    }
                    boolean first = !own;
                    for (String part : readable(entry.value()).split("\\R", -1)) {
                        for (String line : wrap(part, room - column)) {
                            rows.add(new Row(Kind.FIELD, first ? label : "", line));
                            first = false;
                        }
                    }
                }
                case LINE -> {
                    for (String part : readable(entry.value()).split("\\R", -1)) {
                        for (String line : wrap(part, room)) rows.add(new Row(Kind.LINE, "", line));
                    }
                }
            }
        }
        return rows;
    }

    /**
     * {@link NameFont} rows no wider than {@code width}, broken after the spaces between words (the
     * space stays at the end of its row, so the rows joined are exactly {@code text}). A word wider
     * than a row breaks after its last fitting separator ({@code / . - _ : ? & = #}) or grapheme.
     */
    public static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int used = 0;
        int index = 0;
        while (index < text.length()) {
            int end = index;
            while (end < text.length() && text.charAt(end) != ' ') end++;
            while (end < text.length() && text.charAt(end) == ' ') end++;
            String token = text.substring(index, end);
            int tokenWidth = NameFont.width(token);
            if (used + tokenWidth <= width) {
                line.append(token);
                used += tokenWidth;
            } else if (tokenWidth <= width) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(token);
                used = tokenWidth;
            } else {
                if (line.length() > 0) lines.add(line.toString());
                line.setLength(0);
                List<String> pieces = breakWord(token, width);
                lines.addAll(pieces.subList(0, pieces.size() - 1));
                line.append(pieces.getLast());
                used = NameFont.width(pieces.getLast());
            }
            index = end;
        }
        lines.add(line.toString());
        return lines;
    }

    private static final String SEPARATORS = "/.-_:?&=#";

    /** Splits one word into rows of at most {@code width}, after a separator where one fits. */
    private static List<String> breakWord(String word, int width) {
        List<String> pieces = new ArrayList<>();
        BreakIterator graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(word);
        int start = 0;
        int used = 0;
        int lastSeparator = -1;
        int previous = 0;
        for (int end = graphemes.next(); end != BreakIterator.DONE; previous = end, end = graphemes.next()) {
            String cluster = word.substring(previous, end);
            int clusterWidth = NameFont.width(cluster);
            if (used + clusterWidth > width && previous > start) {
                // Prefer the last separator in the second half of the row, so short heads stay whole.
                int cut = lastSeparator > start && NameFont.width(word.substring(start, lastSeparator)) >= width / 2
                        ? lastSeparator : previous;
                pieces.add(word.substring(start, cut));
                start = cut;
                used = NameFont.width(word.substring(start, previous));
                lastSeparator = -1;
            }
            used += clusterWidth;
            if (cluster.length() == 1 && SEPARATORS.indexOf(cluster.charAt(0)) >= 0) lastSeparator = end;
        }
        pieces.add(word.substring(start));
        return pieces;
    }

    /** Missing glyphs keep their exact identity in this bitmap-font reader. */
    public static String readable(String value) {
        StringBuilder out = new StringBuilder();
        value.codePoints().forEach(cp -> {
            if (cp == '\n' || cp == '\r' || NameFont.covers(cp)
                    && !Character.isISOControl(cp) && Character.getType(cp) != Character.FORMAT)
                out.appendCodePoint(cp);
            else out.append("[U+").append(Integer.toHexString(cp).toUpperCase(java.util.Locale.ROOT)).append(']');
        });
        return out.toString();
    }

    public int firstVisibleLine() { return offset; }

    /** The exact complete strings sent to the font on the last drawn page (label and value per row). */
    public List<String> visibleLines() { return visible; }
}
