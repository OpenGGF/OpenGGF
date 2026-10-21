package openggf.timeattack.mp;

import com.openggf.mods.ui.CompactFont;
import com.openggf.mods.ui.LevelOverlayCanvas;
import com.openggf.net.protocol.ControlMessage;
import openggf.timeattack.TimeAttackTrackCatalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bounded room panels that share the run clock's right-hand column and clear stock HUD/cards. */
public final class MultiplayerHudRenderer {
    private MultiplayerHudRenderer() { }

    public static void render(LevelOverlayCanvas canvas, MultiplayerHudState state,
                              int levelWidth, int localCentreX, boolean showMinimap) {
        render(canvas, state, levelWidth, localCentreX, showMinimap, false);
    }

    public static void render(LevelOverlayCanvas canvas, MultiplayerHudState state,
                              int levelWidth, int localCentreX, boolean showMinimap, boolean localFinished) {
        HudTextLayout.Panel panel = layout(canvas.width(), canvas.height(), state, localCentreX, localFinished);
        if (panel == null) return;
        HudTextLayout.drawPanel(canvas, panel);
        if (state.remainingCountdownMillis() > 0) {
            String count = Long.toString(Math.max(1, (state.remainingCountdownMillis() + 999) / 1000));
            CompactFont.shadowed(canvas, count, (canvas.width() - CompactFont.width(count, 2)) / 2,
                    88, 2, HudTextLayout.GOLD, 0xC0000000);
        }
        if (showMinimap && !localFinished && "RUNNING".equals(state.phase()) && levelWidth > 0) {
            drawProgress(canvas, state, levelWidth, localCentreX);
        }
    }

    /** Pure layout used by the renderer and viewport/phase contract tests. */
    public static HudTextLayout.Panel layout(int width, int height, MultiplayerHudState state,
                                             int localCentreX, boolean localFinished) {
        if (state == null || !state.active()) return null;
        List<HudTextLayout.Line> lines = new ArrayList<>();
        int accent = HudTextLayout.ACCENT;
        int desiredWidth = HudTextLayout.PANEL_WIDTH;
        int top = HudTextLayout.ROOM_TOP;
        if (state.connectionLost() || state.kickReason() != null) {
            lines.add(new HudTextLayout.Line(state.connectionLost() ? "CONNECTION LOST"
                    : "KICKED: " + state.kickReason(), 0xFFFF8E83));
            accent = 0xFFFF8E83;
            desiredWidth = 220;
            top = height - 42;
        } else if ("ROUND_END".equals(state.phase())) {
            lines.add(new HudTextLayout.Line("ROUND OVER  " + result(state), HudTextLayout.GOLD));
            int available = Math.min(220, width - 104) - 12;
            state.podiumRows().stream().limit(3).forEach(row -> lines.add(
                    new HudTextLayout.Line(HudTextLayout.compactStanding(row, available),
                            row.rank() == state.localRank() ? HudTextLayout.WHITE : HudTextLayout.MUTED)));
            desiredWidth = 220;
            top = height - 24 - (8 + lines.size() * HudTextLayout.LINE_HEIGHT);
        } else if ("VOTE".equals(state.phase())) {
            lines.add(new HudTextLayout.Line("VOTE 1-3  " + HudTextLayout.clock(state.voteRemainingMillis()),
                    HudTextLayout.GOLD));
            int available = Math.min(220, width - 104) - 12;
            for (int index = 0; index < Math.min(3, state.voteOptions().size()); index++) {
                String key = state.voteOptions().get(index);
                int votes = state.voteCounts().stream().filter(count -> count.trackKey().equals(key))
                        .mapToInt(ControlMessage.VoteCount::votes).findFirst().orElse(0);
                String prefix = (index + 1) + " ";
                String suffix = " [" + votes + "]";
                String label = CompactFont.fit(trackLabel(key),
                        available - CompactFont.width(prefix + suffix, 1) - 1, 1);
                lines.add(new HudTextLayout.Line(prefix + label + suffix, HudTextLayout.WHITE));
            }
            desiredWidth = 220;
            top = height - 24 - (8 + lines.size() * HudTextLayout.LINE_HEIGHT);
        } else if (localFinished) {
            lines.add(new HudTextLayout.Line(result(state) + "  ROOM "
                    + HudTextLayout.clock(state.remainingWindowMillis()), HudTextLayout.WHITE));
            desiredWidth = 220;
            top = height - 42;
        } else {
            String roomHeader = "RUNNING".equals(state.phase())
                    ? "ROOM " + HudTextLayout.clock(state.remainingWindowMillis()) + "  "
                        + state.totalPlayers() + " PLAYERS"
                    : "ROOM " + state.totalPlayers() + " PLAYERS";
            lines.add(new HudTextLayout.Line(roomHeader, HudTextLayout.ACCENT));
            if ("RUNNING".equals(state.phase())) {
                lines.add(new HudTextLayout.Line(progressPosition(state, localCentreX), HudTextLayout.WHITE));
                int available = Math.min(desiredWidth, width - 104) - 12;
                if (state.localRank() > 0) {
                    for (var row : HudTextLayout.nearbyStandings(state.standings(), state.localRank())) {
                        lines.add(new HudTextLayout.Line(HudTextLayout.compactStanding(row, available),
                                row.rank() == state.localRank() ? HudTextLayout.WHITE : HudTextLayout.MUTED));
                    }
                } else if (localCentreX >= 0) {
                    // Positions are coarse, transient network observations, never official standings.
                    state.farPlayers().stream().filter(player -> player.status() == -1 || player.status() == 1)
                            .sorted(Comparator.comparingLong(player -> Math.abs((long) player.cellX() * 64 - localCentreX)))
                            .limit(3).forEach(player -> {
                                int distance = player.cellX() * 64 - localCentreX;
                                String suffix = distance >= 0 ? " AHEAD" : " BEHIND";
                                lines.add(new HudTextLayout.Line(CompactFont.fit(player.displayName(),
                                        available - CompactFont.width(suffix, 1) - 1, 1) + suffix,
                                        HudTextLayout.MUTED));
                            });
                }
            } else if ("LOBBY".equals(state.phase()) && state.voteResultTrackKey() != null) {
                lines.add(new HudTextLayout.Line("NEXT " + trackLabel(state.voteResultTrackKey()), HudTextLayout.WHITE));
            } else {
                lines.add(new HudTextLayout.Line("GET READY", HudTextLayout.WHITE));
            }
        }
        return HudTextLayout.panel(width, top, desiredWidth, lines, accent);
    }

    static String progressPosition(MultiplayerHudState state, int localCentreX) {
        if (localCentreX < 0) return "PROGRESS --/" + state.totalPlayers();
        long ahead = state.farPlayers().stream()
                .filter(player -> player.status() == -1 || player.status() == 1 || player.status() == 2)
                .filter(player -> player.status() == 2 || (long) player.cellX() * 64 > localCentreX).count();
        // An idle player (between attempts, reloading at the start) is seen but never ahead:
        // its last cell is where the previous attempt ended, not where it is now.
        long seen = state.farPlayers().stream()
                .filter(player -> player.status() >= -1 && player.status() <= 2).count();
        if (seen < state.totalPlayers() - 1) return "AHEAD " + ahead + " / " + seen + " SEEN";
        return "PROGRESS EST " + (ahead + 1) + "/" + state.totalPlayers();
    }

    private static String result(MultiplayerHudState state) {
        return state.localRank() > 0 ? "YOU " + state.localRank() + "/" + state.totalPlayers()
                : "ROUND_END".equals(state.phase()) ? "NO RANKED TIME" : "FINISHED";
    }

    static String trackLabel(String key) {
        String[] parts = key == null ? new String[0] : key.split(":", -1);
        if (parts.length != 3) return key == null ? "?" : key;
        try {
            int zone = Integer.parseInt(parts[1]);
            int act = Integer.parseInt(parts[2]);
            return TimeAttackTrackCatalog.tracksFor(parts[0]).stream()
                    .filter(track -> track.zone() == zone && track.act() == act)
                    .map(TimeAttackTrackCatalog.Track::label).findFirst().orElse(key);
        } catch (NumberFormatException ignored) {
            return key;
        }
    }

    private static void drawProgress(LevelOverlayCanvas canvas, MultiplayerHudState state,
                                     int levelWidth, int localCentreX) {
        int width = Math.min(HudTextLayout.PANEL_WIDTH, canvas.width() - 104);
        int x = canvas.width() - width - 4;
        int y = canvas.height() - 24;
        canvas.fill(x, y, width, 20, 0xE5122032);
        CompactFont.draw(canvas, "START", x + 5, y + 3, 1, HudTextLayout.MUTED);
        CompactFont.draw(canvas, "FINISH", x + width - 40, y + 3, 1, HudTextLayout.MUTED);
        int left = x + 6;
        int trackWidth = width - 12;
        canvas.fill(left, y + 14, trackWidth, 2, 0xFF596F85);
        for (var player : state.farPlayers()) {
            if (player.status() != -1 && player.status() != 1 && player.status() != 2) continue;
            int position = player.status() == 2 ? levelWidth : player.cellX() * 64;
            int dot = progressX(position, levelWidth, trackWidth);
            canvas.fill(left + dot, y + 12, 2, 5, HudTextLayout.MUTED);
        }
        if (localCentreX >= 0) {
            int dot = progressX(localCentreX, levelWidth, trackWidth);
            canvas.fill(left + dot - 1, y + 11, 3, 7, HudTextLayout.GOLD);
        }
    }

    private static int progressX(int position, int levelWidth, int width) {
        return (int) Math.max(0, Math.min(width - 2, (long) position * (width - 2) / levelWidth));
    }
}
