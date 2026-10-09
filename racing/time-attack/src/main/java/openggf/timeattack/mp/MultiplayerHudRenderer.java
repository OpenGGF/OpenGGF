package openggf.timeattack.mp;

import com.openggf.mods.ui.CompactFont;
import com.openggf.mods.ui.LevelOverlayCanvas;
import openggf.racing.protocol.ControlMessage;
import openggf.timeattack.TimeAttackTrackCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless overlay renderer for multiplayer countdown, window, standings, votes and the
 * minimap progress strip, drawn over the level through the run host's overlay canvas.
 */
public final class MultiplayerHudRenderer {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFC0C0C0;
    private static final int YELLOW = 0xFFFFFF40;
    private static final int RED = 0xFFFF6060;
    private static final int SHADOW = 0xC0000000;
    private static final int LINE = 8;

    private MultiplayerHudRenderer() {
    }

    /**
     * @param levelWidth    width of the loaded act in pixels (0 hides the minimap)
     * @param localCentreX  the local player's centre X, or -1 when unknown
     * @param showMinimap   whether the minimap progress strip is enabled
     */
    public static void render(LevelOverlayCanvas canvas, MultiplayerHudState state,
                              int levelWidth, int localCentreX, boolean showMinimap) {
        if (state == null || !state.active()) {
            return;
        }
        int width = canvas.width();
        if (state.remainingCountdownMillis() > 0) {
            String count = Long.toString(Math.max(1, (state.remainingCountdownMillis() + 999) / 1000));
            CompactFont.shadowed(canvas, count, (width - CompactFont.width(count, 2)) / 2, 84, 2, YELLOW, SHADOW);
        }
        if (state.remainingWindowMillis() >= 0) {
            long seconds = state.remainingWindowMillis() / 1000;
            drawRight(canvas, "W %d:%02d".formatted(seconds / 60, seconds % 60), 4, WHITE);
        }
        int y = 4 + LINE;
        for (ControlMessage.StandingsRow row : state.standings()) {
            drawRight(canvas, HudTextLayout.standingsLine(row, state.characterPolicy()), y, GREY);
            y += LINE;
        }
        if ("ROUND_END".equals(state.phase())) {
            drawBlock(canvas, HudTextLayout.podiumLines(state.podiumRows(), state.localRank(),
                    state.standings(), -1, state.characterPolicy()), 72, 60, YELLOW);
        } else if ("VOTE".equals(state.phase())) {
            drawBlock(canvas, HudTextLayout.voteLines(state.voteOptions(), state.voteCounts(),
                    state.voteRemainingMillis(), MultiplayerHudRenderer::trackLabel), 56, 60, YELLOW);
        } else if ("LOBBY".equals(state.phase()) && state.voteResultTrackKey() != null) {
            CompactFont.shadowed(canvas, HudTextLayout.voteResultLine(state.voteResultTrackKey(),
                    MultiplayerHudRenderer::trackLabel), 8, 24, 1, YELLOW, SHADOW);
        }
        if (showMinimap) {
            drawMinimap(canvas, state, levelWidth, localCentreX);
        }
        if (state.connectionLost()) {
            CompactFont.shadowed(canvas, "CONNECTION LOST", 8, 8, 1, RED, SHADOW);
        } else if (state.kickReason() != null) {
            CompactFont.shadowed(canvas, "KICKED: " + state.kickReason(), 8, 8, 1, RED, SHADOW);
        }
    }

    private static void drawRight(LevelOverlayCanvas canvas, String line, int y, int argb) {
        CompactFont.shadowed(canvas, line, canvas.width() - 4 - CompactFont.width(line, 1), y, 1, argb, SHADOW);
    }

    private static void drawBlock(LevelOverlayCanvas canvas, List<String> lines, int x, int y, int argb) {
        for (String line : lines) {
            CompactFont.shadowed(canvas, line, x, y, 1, argb, SHADOW);
            y += LINE;
        }
    }

    static String trackLabel(String key) {
        String[] parts = key == null ? new String[0] : key.split(":", -1);
        if (parts.length != 3) {
            return key;
        }
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

    private static void drawMinimap(LevelOverlayCanvas canvas, MultiplayerHudState state, int levelWidth,
                                    int localCentreX) {
        if (!"RUNNING".equals(state.phase()) || levelWidth <= 0) {
            return;
        }
        List<MinimapLayout.Dot> dots = new ArrayList<>();
        for (var player : state.farPlayers()) {
            char glyph = player.status() == -1 ? 'o' : MinimapLayout.glyphForFarStatus(player.status());
            if (glyph != ' ') {
                dots.add(new MinimapLayout.Dot(player.cellX() * 64 + 32, glyph));
            }
        }
        if (localCentreX >= 0) {
            dots.add(new MinimapLayout.Dot(localCentreX, '*'));
        }
        String strip = "[" + MinimapLayout.compose(levelWidth, dots) + "]";
        CompactFont.shadowed(canvas, strip, 4, canvas.height() - 10, 1, GREY, SHADOW);
    }
}
