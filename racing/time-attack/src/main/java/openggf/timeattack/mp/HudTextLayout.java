package openggf.timeattack.mp;

import openggf.timeattack.TimeAttackTimeFormat;
import com.openggf.mods.ui.CompactFont;
import com.openggf.mods.ui.LevelOverlayCanvas;
import com.openggf.net.protocol.ControlMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Pure text composition for the multiplayer HUD. */
public final class HudTextLayout {
    /** Widest right-hand panel: 21 compact-font columns, about 44% of a native 320-pixel view. */
    public static final int PANEL_WIDTH = 140;
    public static final int LINE_HEIGHT = 9;
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

    public record Line(String text, int color) { }
    /** Right-anchored, measured panel. Every text line is fitted before drawing. */
    public record Panel(int x, int y, int width, int height, List<Line> lines, int accent) {
        public Panel { lines = List.copyOf(lines); }
    }

    /** Shrinks to the widest fitted line, never wider than {@code desiredWidth}. */
    public static Panel panel(int viewportWidth, int top, int desiredWidth,
                              List<Line> lines, int accent) {
        // Reserve the left 100 pixels for the stock SCORE/TIME/RINGS and lives HUD.
        int maximum = Math.max(1, Math.min(desiredWidth, viewportWidth - 104));
        List<Line> fitted = lines.stream().map(line -> new Line(
                CompactFont.fit(line.text(), maximum - 12, 1), line.color())).toList();
        int text = fitted.stream().mapToInt(line -> CompactFont.width(line.text(), 1)).max().orElse(0);
        int width = Math.min(maximum, text + 12);
        return new Panel(viewportWidth - width - 4, top, width,
                panelHeight(fitted.size()), fitted, accent);
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

    /** Compact-font columns available to text inside a panel no wider than {@code desiredWidth}. */
    public static int columns(int viewportWidth, int desiredWidth) {
        int maximum = Math.max(1, Math.min(desiredWidth, viewportWidth - 104));
        return Math.max(0, (maximum - 12 + 1) / 6);
    }

    /** Left text and a right-aligned suffix in a monospaced row; the left text is truncated first. */
    public static String aligned(String left, String right, int columns) {
        String clean = left.replaceAll("[\\r\\n\\t]", " ");
        int room = Math.max(0, columns - right.length() - 1);
        String fitted = clean.length() <= room ? clean
                : room <= 3 ? ".".repeat(room) : clean.substring(0, room - 3) + "...";
        return fitted + " ".repeat(Math.max(1, columns - fitted.length() - right.length())) + right;
    }

    public static void drawPanel(LevelOverlayCanvas canvas, Panel panel) {
        canvas.fill(panel.x(), panel.y(), panel.width(), panel.height(), FILL);
        canvas.fill(panel.x(), panel.y(), 2, panel.height(), panel.accent());
        int y = panel.y() + TEXT_TOP;
        for (Line line : panel.lines()) {
            CompactFont.draw(canvas, line.text(), panel.x() + TEXT_INSET, y, 1, line.color());
            y += LINE_HEIGHT;
        }
    }

    public static String clock(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }

    /** Preserve rank/time columns when a server name is longer than the available row. */
    public static String compactStanding(ControlMessage.StandingsRow row, int pixels) {
        String rank = row.rank() + ". ";
        String suffix = " " + TimeAttackTimeFormat.frames(row.bestTimeFrames());
        String name = row.displayName().replaceAll("[\\r\\n\\t]", " ");
        return rank + CompactFont.fit(name,
                pixels - CompactFont.width(rank + suffix, 1) - 1, 1) + suffix;
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
