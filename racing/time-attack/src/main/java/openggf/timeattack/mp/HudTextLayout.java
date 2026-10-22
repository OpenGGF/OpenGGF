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
    public static final int PANEL_WIDTH = 176;
    public static final int ROOM_TOP = 44;
    public static final int WHITE = 0xFFF5F8FF;
    public static final int MUTED = 0xFFB9CADC;
    public static final int ACCENT = 0xFF67DDF5;
    public static final int GOLD = 0xFFFFDD67;
    public static final int LINE_HEIGHT = 10;

    public record Line(String text, int color) { }
    /** Right-anchored, measured panel. Every text line is fitted before drawing. */
    public record Panel(int x, int y, int width, int height, List<Line> lines, int accent) {
        public Panel { lines = List.copyOf(lines); }
    }

    public static Panel panel(int viewportWidth, int top, int desiredWidth,
                              List<Line> lines, int accent) {
        // Reserve the left 100 pixels for the stock SCORE/TIME/RINGS and lives HUD.
        int width = Math.max(1, Math.min(desiredWidth, viewportWidth - 104));
        List<Line> fitted = lines.stream().map(line -> new Line(
                CompactFont.fit(line.text(), width - 12, 1), line.color())).toList();
        return new Panel(viewportWidth - width - 4, top, width,
                8 + LINE_HEIGHT * fitted.size(), fitted, accent);
    }

    public static void drawPanel(LevelOverlayCanvas canvas, Panel panel) {
        canvas.fill(panel.x(), panel.y(), panel.width(), panel.height(), 0xE5122032);
        canvas.fill(panel.x(), panel.y(), 2, panel.height(), panel.accent());
        int y = panel.y() + 5;
        for (Line line : panel.lines()) {
            CompactFont.draw(canvas, line.text(), panel.x() + 6, y, 1, line.color());
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
