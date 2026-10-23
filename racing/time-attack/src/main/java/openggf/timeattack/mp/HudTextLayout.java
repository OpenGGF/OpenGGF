package openggf.timeattack.mp;

import openggf.timeattack.TimeAttackTimeFormat;
import com.openggf.mods.ui.CompactFont;
import com.openggf.mods.ui.PixelCanvas;
import com.openggf.mods.ui.NameFont;
import com.openggf.net.protocol.ControlMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Pure text composition for the multiplayer HUD. */
public final class HudTextLayout {
    /** Widest right-hand panel: 21 compact-font columns, about 44% of a native 320-pixel view. */
    public static final int PANEL_WIDTH = 140;
    public static final int LINE_HEIGHT = 9;
    /** Rows that show a player's name use the name font's taller line, so any script fits. */
    public static final int NAME_LINE_HEIGHT = NameFont.LINE_HEIGHT;
    /** Below the run clock's tallest (three-line, 4 + 3 * 9 pixel) panel at y=4, with a three-pixel gap. */
    public static final int ROOM_TOP = 4 + (4 + 3 * LINE_HEIGHT) + 3;
    public static final int WHITE = 0xFFF5F8FF;
    public static final int MUTED = 0xFFB9CADC;
    public static final int ACCENT = 0xFF67DDF5;
    public static final int GOLD = 0xFFFFDD67;
    /** Translucent enough to keep the level readable behind the white text. */
    public static final int FILL = 0xC8122032;
    private static final int TEXT_INSET = 6;
    private static final int TEXT_TOP = 3;

    /**
     * One panel row in the compact font. A name row also shows a player's name in the name font
     * and their colour after {@code text}, with {@code suffix} (a time or AHEAD/BEHIND) right-aligned
     * in the compact font, so the column stays put whatever script or length the name has.
     *
     * @param name      the player's name, or null for a plain row
     * @param nameColor the name's {@code 0xAARRGGBB}
     */
    public record Line(String text, int color, String name, int nameColor, String suffix) {
        public Line(String text, int color) {
            this(text, color, null, 0, "");
        }

        public Line {
            suffix = suffix == null ? "" : suffix;
        }

        /** A row showing {@code prefix}, then {@code name} in {@code nameColor}, then {@code suffix} on the right. */
        public static Line named(String prefix, String name, int nameColor, String suffix, int color) {
            return new Line(prefix, color, name.replaceAll("[\\r\\n\\t]", " "), nameColor, suffix);
        }

        public boolean hasName() {
            return name != null;
        }

        public int height() {
            return hasName() ? NAME_LINE_HEIGHT : LINE_HEIGHT;
        }

        /** Everything the row shows, as one string (tests and logs). */
        public String plain() {
            return hasName() ? text + name + (suffix.isEmpty() ? "" : " " + suffix) : text;
        }

        /** Pixels the row needs, with a gap before a right-aligned suffix. */
        public int width() {
            if (!hasName()) return CompactFont.width(text, 1);
            int width = text.isEmpty() ? 0 : CompactFont.width(text, 1) + 1;
            width += NameFont.width(name);
            return suffix.isEmpty() ? width : width + SUFFIX_GAP + CompactFont.width(suffix, 1);
        }
    }

    private static final int SUFFIX_GAP = 6;

    /** Right-anchored, measured panel. Every text line is fitted before drawing. */
    public record Panel(int x, int y, int width, int height, List<Line> lines, int accent) {
        public Panel { lines = List.copyOf(lines); }
    }

    /** Shrinks to the widest fitted line, never wider than {@code desiredWidth}. */
    public static Panel panel(int viewportWidth, int top, int desiredWidth,
                              List<Line> lines, int accent) {
        // Reserve the left 100 pixels for the stock SCORE/TIME/RINGS and lives HUD.
        int maximum = Math.max(1, Math.min(desiredWidth, viewportWidth - 104));
        List<Line> fitted = lines.stream().map(line -> fit(line, maximum - 12)).toList();
        int text = fitted.stream().mapToInt(Line::width).max().orElse(0);
        int width = Math.min(maximum, text + 12);
        return new Panel(viewportWidth - width - 4, top, width,
                4 + fitted.stream().mapToInt(Line::height).sum(), fitted, accent);
    }

    /** Fits a row into {@code pixels}: plain rows by characters, name rows by cutting the name (whole graphemes). */
    private static Line fit(Line line, int pixels) {
        if (!line.hasName()) return new Line(CompactFont.fit(line.text(), pixels, 1), line.color());
        String prefix = CompactFont.fit(line.text(), pixels, 1);
        String suffix = line.suffix();
        int fixed = (prefix.isEmpty() ? 0 : CompactFont.width(prefix, 1) + 1)
                + (suffix.isEmpty() ? 0 : SUFFIX_GAP + CompactFont.width(suffix, 1));
        if (fixed > pixels) {
            suffix = "";
            fixed = prefix.isEmpty() ? 0 : CompactFont.width(prefix, 1) + 1;
        }
        return new Line(prefix, line.color(), NameFont.fit(line.name(), Math.max(0, pixels - fixed)),
                line.nameColor(), suffix);
    }

    public static int panelHeight(int lines) {
        return 4 + LINE_HEIGHT * lines;
    }

    /** Top of the one-line strip the run clock collapses to after the act is cleared. */
    public static int finishStripTop(int viewportHeight) {
        return viewportHeight - 4 - panelHeight(1);
    }

    /** Top of a panel stacked just above the finish strip, clear of the stock act-clear card. */
    public static int aboveFinishStrip(int viewportHeight, int lines) {
        return finishStripTop(viewportHeight) - 3 - panelHeight(lines);
    }

    /** {@link #aboveFinishStrip(int, int)} for rows of mixed height (name rows are taller). */
    public static int aboveFinishStrip(int viewportHeight, List<Line> lines) {
        return finishStripTop(viewportHeight) - 3 - (4 + lines.stream().mapToInt(Line::height).sum());
    }

    public static void drawPanel(PixelCanvas canvas, Panel panel) {
        canvas.fill(panel.x(), panel.y(), panel.width(), panel.height(), FILL);
        canvas.fill(panel.x(), panel.y(), 2, panel.height(), panel.accent());
        int y = panel.y() + TEXT_TOP;
        for (Line line : panel.lines()) {
            int x = panel.x() + TEXT_INSET;
            if (!line.hasName()) {
                CompactFont.draw(canvas, line.text(), x, y, 1, line.color());
                y += LINE_HEIGHT;
                continue;
            }
            // The name's baseline (row 10 of its 13-row box) lines up with the compact glyphs' last row.
            int boxTop = y - 1;
            int compactY = boxTop + NameFont.BASELINE - 7;
            if (!line.text().isEmpty()) {
                CompactFont.draw(canvas, line.text(), x, compactY, 1, line.color());
                x += CompactFont.width(line.text(), 1) + 1;
            }
            NameFont.draw(canvas, line.name(), x, boxTop, line.nameColor());
            if (!line.suffix().isEmpty()) {
                CompactFont.draw(canvas, line.suffix(), panel.x() + panel.width() - TEXT_INSET
                        - CompactFont.width(line.suffix(), 1), compactY, 1, line.color());
            }
            y += NAME_LINE_HEIGHT;
        }
    }

    public static String clock(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }

    public static List<ControlMessage.StandingsRow> nearbyStandings(
            List<ControlMessage.StandingsRow> standings, int localRank) {
        if (localRank < 1) return List.of();
        List<ControlMessage.StandingsRow> sorted = standings.stream()
                .sorted(java.util.Comparator.comparingInt(ControlMessage.StandingsRow::rank)).toList();
        int index = 0;
        while (index < sorted.size() && sorted.get(index).rank() < localRank) index++;
        int start = Math.max(0, Math.min(index - 1, sorted.size() - 3));
        return sorted.subList(start, Math.min(sorted.size(), start + 3));
    }

    private HudTextLayout() {
    }

    public static String characterBadge(String policy, String character) {
        if (!"OPEN".equals(policy)) {
            return "";
        }
        return character == null || character.isBlank() ? "[?] "
                : "[" + Character.toUpperCase(character.charAt(0)) + "] ";
    }

    public static String standingsLine(ControlMessage.StandingsRow row, String policy) {
        String verification = switch (row.verifyState()) {
            case "PENDING" -> " ..";
            case "VERIFIED" -> " *";
            default -> "";
        };
        return "%2d %-8s %s%s%s".formatted(row.rank(), row.displayName(),
                characterBadge(policy, row.character()),
                TimeAttackTimeFormat.frames(row.bestTimeFrames()), verification);
    }

    public static List<String> podiumLines(List<ControlMessage.StandingsRow> podiumRows,
                                           int localRank,
                                           List<ControlMessage.StandingsRow> standings,
                                           int localSlot, String policy) {
        List<String> lines = new ArrayList<>();
        lines.add("ROUND OVER");
        podiumRows.stream().limit(3).map(row -> standingsLine(row, policy))
                .forEach(lines::add);
        lines.add("");
        if (localRank < 0) {
            lines.add("YOU: no time");
        } else {
            ControlMessage.StandingsRow local = standings.stream()
                    .filter(row -> localSlot >= 0 ? row.slot() == localSlot
                            : row.rank() == localRank)
                    .findFirst().orElse(null);
            lines.add("YOU: #" + localRank + (local == null ? ""
                    : " " + TimeAttackTimeFormat.frames(local.bestTimeFrames())));
        }
        return List.copyOf(lines);
    }

    public static List<String> voteLines(List<String> options,
                                         List<ControlMessage.VoteCount> counts,
                                         long remainingMillis,
                                         Function<String, String> labeler) {
        List<String> lines = new ArrayList<>();
        lines.add("NEXT TRACK - VOTE 1-3 (" + Math.max(0, remainingMillis / 1000) + "s)");
        for (int index = 0; index < options.size(); index++) {
            String key = options.get(index);
            int votes = counts.stream().filter(count -> count.trackKey().equals(key))
                    .mapToInt(ControlMessage.VoteCount::votes).findFirst().orElse(0);
            lines.add((index + 1) + " " + labeler.apply(key) + "   " + votes + " votes");
        }
        return List.copyOf(lines);
    }

    public static String voteResultLine(String key, Function<String, String> labeler) {
        return key == null ? "" : "NEXT: " + labeler.apply(key);
    }
}
