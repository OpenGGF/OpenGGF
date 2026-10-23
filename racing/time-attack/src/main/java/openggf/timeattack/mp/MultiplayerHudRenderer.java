package openggf.timeattack.mp;

import com.openggf.mods.ui.CompactFont;
import com.openggf.mods.ui.LevelOverlayCanvas;
import com.openggf.net.protocol.ControlMessage;
import openggf.timeattack.TimeAttackTimeFormat;
import openggf.timeattack.TimeAttackTrackCatalog;
import com.openggf.net.client.RemoteGhostRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Compact room panels that share the run clock's right-hand column and clear stock HUD/cards. */
public final class MultiplayerHudRenderer {
    private static final int PROGRESS_MAX_WIDTH = 256;
    private static final int PROGRESS_HEIGHT = 9;

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
            drawCountdown(canvas, Math.max(1, (state.remainingCountdownMillis() + 999) / 1000));
        }
        if (showMinimap && !localFinished && "RUNNING".equals(state.phase()) && levelWidth > 0) {
            drawProgress(canvas, state, levelWidth, localCentreX);
        }
    }

    /** The room's countdown digit, centred above the action; also drawn after a fast restart. */
    public static void drawCountdown(LevelOverlayCanvas canvas, long seconds) {
        String count = Long.toString(seconds);
        CompactFont.shadowed(canvas, count, (canvas.width() - CompactFont.width(count, 2)) / 2,
                88, 2, HudTextLayout.GOLD, 0xC0000000);
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
            top = HudTextLayout.aboveFinishStrip(height, 1);
        } else if ("ROUND_END".equals(state.phase())) {
            lines.add(new HudTextLayout.Line("ROUND OVER  " + result(state), HudTextLayout.GOLD));
            state.podiumRows().stream().limit(3).forEach(row -> lines.add(standing(state, row)));
            desiredWidth = 220;
            top = HudTextLayout.aboveFinishStrip(height, lines);
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
            top = HudTextLayout.aboveFinishStrip(height, lines);
        } else if (localFinished) {
            lines.add(new HudTextLayout.Line(result(state) + "  ROOM "
                    + HudTextLayout.clock(state.remainingWindowMillis()), HudTextLayout.WHITE));
            desiredWidth = 220;
            top = HudTextLayout.aboveFinishStrip(height, 1);
        } else if ("RUNNING".equals(state.phase())) {
            lines.add(new HudTextLayout.Line("ROOM " + HudTextLayout.clock(state.remainingWindowMillis()),
                    HudTextLayout.ACCENT));
            lines.add(new HudTextLayout.Line(progressPosition(state, localCentreX), HudTextLayout.WHITE));
            if (state.localRank() > 0) {
                for (var row : HudTextLayout.nearbyStandings(state.standings(), state.localRank())) {
                    lines.add(standing(state, row));
                }
            } else if (localCentreX >= 0) {
                // Positions are coarse, transient network observations, never official standings.
                // One rival either side is enough; the progress strip carries the rest of the field.
                nearestRival(state, localCentreX, true).ifPresent(player -> lines.add(HudTextLayout.Line.named("",
                        player.displayName(), state.nameColour(player.slot(), HudTextLayout.MUTED), "AHEAD",
                        HudTextLayout.MUTED)));
                nearestRival(state, localCentreX, false).ifPresent(player -> lines.add(HudTextLayout.Line.named("",
                        player.displayName(), state.nameColour(player.slot(), HudTextLayout.MUTED), "BEHIND",
                        HudTextLayout.MUTED)));
            }
        } else {
            lines.add(new HudTextLayout.Line("ROOM " + state.totalPlayers() + " PLAYERS", HudTextLayout.ACCENT));
            if ("LOBBY".equals(state.phase()) && state.voteResultTrackKey() != null) {
                lines.add(new HudTextLayout.Line("NEXT " + trackLabel(state.voteResultTrackKey()), HudTextLayout.WHITE));
            } else {
                lines.add(new HudTextLayout.Line("GET READY", HudTextLayout.WHITE));
            }
        }
        return HudTextLayout.panel(width, top, desiredWidth, lines, accent);
    }

    /** A ranked row: rank, the player's name in their colour, and the time in a right-aligned column. */
    private static HudTextLayout.Line standing(MultiplayerHudState state, ControlMessage.StandingsRow row) {
        int colour = row.rank() == state.localRank() ? HudTextLayout.WHITE : HudTextLayout.MUTED;
        return HudTextLayout.Line.named(row.rank() + ". ", row.displayName(), state.nameColour(row.slot(), colour),
                TimeAttackTimeFormat.frames(row.bestTimeFrames()), colour);
    }

    private static java.util.Optional<RemoteGhostRegistry.FarPlayer> nearestRival(
            MultiplayerHudState state, int localCentreX, boolean ahead) {
        return state.farPlayers().stream().filter(player -> player.status() == -1 || player.status() == 1)
                .filter(player -> ((long) player.cellX() * 64 > localCentreX) == ahead)
                .min(Comparator.comparingLong(player -> Math.abs((long) player.cellX() * 64 - localCentreX)));
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
            return TimeAttackTrackCatalog.eligibleFor(parts[0]).stream()
                    .filter(track -> track.zone() == zone && track.act() == act)
                    .map(TimeAttackTrackCatalog.Track::label).findFirst().orElse(key);
        } catch (NumberFormatException ignored) {
            return key;
        }
    }

    /** A slim start-to-finish strip along the bottom, right of the lives counter's area (hidden in Time Attack runs); the local runner is the gold marker. */
    private static void drawProgress(LevelOverlayCanvas canvas, MultiplayerHudState state,
                                     int levelWidth, int localCentreX) {
        int width = Math.min(PROGRESS_MAX_WIDTH, canvas.width() - 108);
        if (width < 32) return;
        int x = canvas.width() - width - 4;
        int y = canvas.height() - PROGRESS_HEIGHT - 3;
        canvas.fill(x, y, width, PROGRESS_HEIGHT, HudTextLayout.FILL);
        int left = x + 4;
        int trackWidth = width - 12;
        int middle = y + PROGRESS_HEIGHT / 2;
        canvas.fill(left, middle, trackWidth, 1, 0xFF596F85);
        canvas.fill(left, middle - 2, 1, 5, HudTextLayout.MUTED);
        // Chequered finish flag.
        int flag = left + trackWidth + 1;
        canvas.fill(flag, middle - 3, 4, 6, 0xFF101010);
        canvas.fill(flag, middle - 3, 2, 2, HudTextLayout.WHITE);
        canvas.fill(flag + 2, middle - 1, 2, 2, HudTextLayout.WHITE);
        canvas.fill(flag, middle + 1, 2, 2, HudTextLayout.WHITE);
        for (var player : state.farPlayers()) {
            if (player.status() != -1 && player.status() != 1 && player.status() != 2) continue;
            int position = player.status() == 2 ? levelWidth : player.cellX() * 64;
            int dot = progressX(position, levelWidth, trackWidth);
            canvas.fill(left + dot, middle - 1, 2, 3, HudTextLayout.MUTED);
        }
        if (localCentreX >= 0) {
            int dot = progressX(localCentreX, levelWidth, trackWidth);
            canvas.fill(left + dot - 1, y + 1, 3, PROGRESS_HEIGHT - 2, HudTextLayout.GOLD);
        }
    }

    private static int progressX(int position, int levelWidth, int width) {
        return (int) Math.max(0, Math.min(width - 2, (long) position * (width - 2) / levelWidth));
    }
}
