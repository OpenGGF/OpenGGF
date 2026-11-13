package openggf.timeattack.mp;

import com.openggf.mods.ui.CompactFont;
import com.openggf.net.protocol.ControlMessage;
import openggf.timeattack.CompetitionHudState;
import openggf.timeattack.TimeAttackTimeFormat;
import openggf.timeattack.TimeAttackTrackCatalog;
import openggf.timeattack.ui.LapTimeFormat;
import com.openggf.net.client.RemoteGhostRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Compact room panels that share the run clock's right-hand column and clear stock HUD/cards.
 *
 * <p>A Competition round replaces the Time Attack window rows with its own: a dim estimated
 * position ({@code P4/37}), and on an arena the lap in progress and the best lap (the native arena
 * HUD already shows the total and the lap number, so neither is repeated). Separate one-row plates
 * under it carry the armed cutoff (amber), a teleport swap (cyan) and the mass start. Every value
 * comes from {@link CompetitionHudState}; this class decides only where it goes.
 */
public final class MultiplayerHudRenderer {
    /**
     * Top of the right column over an arena: below the native lap digit, which the solo arena view
     * keeps at its native screen place (CompetitionHudObjectInstance's LAP piece: x_pos $190,
     * y_pos $98, twelve rows either side, so its bottom is screen row 36 of the native frame).
     */
    static final int ARENA_TOP = 0x98 - 0x80 + 0x0C + 4;
    /** Space between stacked right-column plates. */
    static final int PLATE_GAP = 3;
    private static final int PROGRESS_MARGIN = 4;
    private static final int PROGRESS_HEIGHT = 13;
    private static final int HURT = 0xFFFF3B3B;
    /** Racers within one column of this many track pixels share a stack. */
    static final int STACK_BIN = 4;
    /** The most segments a column draws; a taller column shows its count above. */
    static final int STACK_MAX = 5;
    private static final int SEGMENT_WIDTH = 3;
    private static final int SEGMENT_HEIGHT = 3;
    /** A segment and the dark pixel between it and the next one up. */
    private static final int STACK_STEP = SEGMENT_HEIGHT + 1;

    private MultiplayerHudRenderer() { }

    public static void render(com.openggf.mods.ui.PixelCanvas canvas, MultiplayerHudState state,
                              int levelWidth, int localCentreX, boolean showMinimap) {
        render(canvas, state, levelWidth, localCentreX, showMinimap, false);
    }

    public static void render(com.openggf.mods.ui.PixelCanvas canvas, MultiplayerHudState state,
                              int levelWidth, int localCentreX, boolean showMinimap, boolean localFinished) {
        HudTextLayout.Panel panel = layout(canvas.width(), canvas.height(), state, localCentreX, localFinished);
        if (panel == null) return;
        HudTextLayout.Panel chat = chatLayout(canvas.width(), canvas.height(), state, panel);
        if (chat != null) HudTextLayout.drawPanel(canvas, chat);
        HudTextLayout.drawPanel(canvas, panel);
        for (HudTextLayout.Panel plate : plates(canvas.width(), canvas.height(), state, panel, localFinished)) {
            HudTextLayout.drawPanel(canvas, plate);
        }
        // An arena's visible count is the ROM's own neon 3-2-1-GO; only acts draw the room's digit.
        boolean arena = state.competition().isCompetition() && state.competition().isArena();
        if (state.remainingCountdownMillis() > 0 && !arena) {
            drawCountdown(canvas, Math.max(1, (state.remainingCountdownMillis() + 999) / 1000));
        }
        if (showMinimap && !localFinished && "RUNNING".equals(state.phase()) && levelWidth > 0) {
            drawProgress(canvas, state, levelWidth, localCentreX);
        }
    }

    /** Latest player messages, already rendered/filtered by the session, above the progress strip. */
    public static HudTextLayout.Panel chatLayout(int width, int height, MultiplayerHudState state) {
        return chatLayout(width, height, state, layout(width, height, state, -1, false));
    }

    private static HudTextLayout.Panel chatLayout(int width, int height, MultiplayerHudState state,
                                                  HudTextLayout.Panel room) {
        if (state == null || !state.active() || state.chatLines().isEmpty()) return null;
        List<String> history = state.chatLines();
        List<HudTextLayout.Line> lines = new ArrayList<>();
        for (String text : history.subList(Math.max(0, history.size() - 2), history.size())) {
            // The name font supports Unicode player labels and message bodies alike.
            lines.add(HudTextLayout.Line.named("", text, HudTextLayout.WHITE, "", HudTextLayout.WHITE));
        }
        int top = Math.max(4, height - 62 - lines.stream().mapToInt(HudTextLayout.Line::height).sum());
        HudTextLayout.Panel chat = HudTextLayout.panelAt(4, top, Math.min(260, width - 8),
                lines, HudTextLayout.ACCENT);
        if (room != null && chat.x() < room.x() + room.width() && room.x() < chat.x() + chat.width()
                && chat.y() < room.y() + room.height() && room.y() < chat.y() + chat.height()) {
            // Result/vote notices take precedence; move chat above them rather than cover a podium.
            top = room.y() - chat.height() - 4;
            if (top < 4) return null;
            chat = new HudTextLayout.Panel(chat.x(), top, chat.width(), chat.height(), chat.lines(), chat.accent());
        }
        return chat;
    }

    /** The room's countdown digit, centred above the action; also drawn after a fast restart. */
    public static void drawCountdown(com.openggf.mods.ui.PixelCanvas canvas, long seconds) {
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
        CompetitionHudState competition = state.competition();
        boolean race = competition.isCompetition();
        if (race && !state.connectionLost() && state.kickReason() == null) {
            return competitionLayout(width, height, state, localFinished);
        }
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

    /**
     * The Competition panel: the mass start before release, the live finish order once the local
     * player is home, the results at round end, otherwise the position and (arenas) lap rows.
     */
    private static HudTextLayout.Panel competitionLayout(int width, int height, MultiplayerHudState state,
                                                         boolean localFinished) {
        CompetitionHudState competition = state.competition();
        List<HudTextLayout.Line> lines = new ArrayList<>();
        int top = competition.isArena() ? ARENA_TOP : HudTextLayout.ROOM_TOP;
        if (competition.lateJoin()) {
            lines.add(new HudTextLayout.Line("NEXT ROUND", HudTextLayout.GOLD));
            lines.add(new HudTextLayout.Line("JOINED AFTER START", HudTextLayout.MUTED));
            return HudTextLayout.panel(width, top, HudTextLayout.PANEL_WIDTH, lines, HudTextLayout.GOLD);
        }
        if (competition.massStartPhase() != CompetitionHudState.MassStartPhase.RELEASED
                && !"ROUND_END".equals(state.phase())) {
            lines.add(new HudTextLayout.Line(startLabel(competition), HudTextLayout.GOLD));
            if (competition.massStartPhase() == CompetitionHudState.MassStartPhase.LOADING) {
                lines.add(new HudTextLayout.Line("LOADING", HudTextLayout.MUTED));
            }
            return HudTextLayout.panel(width, top, HudTextLayout.PANEL_WIDTH, lines, HudTextLayout.GOLD);
        }
        boolean roundEnd = "ROUND_END".equals(state.phase());
        if (roundEnd || localFinished) {
            // The finish strip already says FINISHED; the head keeps the column's width for the place.
            String head = (roundEnd ? "FINAL " : "") + positionLabel(competition);
            lines.add(verified(head, roundEnd ? HudTextLayout.GOLD : HudTextLayout.WHITE, competition));
            displayOrder(competition.placements()).stream()
                    .filter(row -> roundEnd || row.outcome() == CompetitionHudState.Outcome.FINISHED)
                    .limit(3).forEach(row -> lines.add(placement(state, row)));
            return HudTextLayout.panel(width, HudTextLayout.aboveFinishStrip(height, lines), HudTextLayout.PANEL_WIDTH,
                    lines, roundEnd ? HudTextLayout.GOLD : HudTextLayout.ACCENT);
        }
        lines.add(verified(positionLabel(competition), HudTextLayout.MUTED, competition));
        if (competition.isArena()) {
            lines.add(new HudTextLayout.Line("LAP " + competition.currentLap() + "/" + competition.lapCount() + " "
                    + LapTimeFormat.lap(competition.currentLapFrames()), HudTextLayout.WHITE));
            if (competition.bestLapFrames() >= 0) {
                lines.add(new HudTextLayout.Line("BEST " + LapTimeFormat.lap(competition.bestLapFrames()),
                        HudTextLayout.MUTED));
            }
        }
        return HudTextLayout.panel(width, top, HudTextLayout.PANEL_WIDTH, lines, HudTextLayout.ACCENT);
    }

    /** {@code START 0:08}: the host's scheduled start, counted down in whole seconds. */
    static String startLabel(CompetitionHudState competition) {
        return "START " + LapTimeFormat.countdown(competition.timeToStartMillis());
    }

    /** {@code P4/37}, an estimate (never a placement): {@code P-/37} before one is known. */
    static String positionLabel(CompetitionHudState competition) {
        String position = competition.estimatedPosition() > 0 ? Integer.toString(competition.estimatedPosition()) : "-";
        return "P" + position + "/" + Math.max(competition.fieldSize(), competition.estimatedPosition());
    }

    /** A row with an amber UNVERIFIED tag at its right in an unverified room. */
    private static HudTextLayout.Line verified(String text, int colour, CompetitionHudState competition) {
        return competition.unverified() ? HudTextLayout.Line.split(text, colour, "UNVERIFIED", HudTextLayout.AMBER)
                : new HudTextLayout.Line(text, colour);
    }

    /** A placement row: rank, name, and the total (or DNF/DNS), with a dim mark on a swap-tainted row. */
    private static HudTextLayout.Line placement(MultiplayerHudState state, CompetitionHudState.PlacementRow row) {
        int colour = row.slot() >= 0 && row.slot() == state.localSlot() ? HudTextLayout.WHITE : HudTextLayout.MUTED;
        String rank = row.outcome() == CompetitionHudState.Outcome.FINISHED ? row.rank() + ". " : "-  ";
        return HudTextLayout.Line.named(rank, row.name() + (row.taintedSwap() ? " *" : ""),
                row.slot() >= 0 ? state.nameColour(row.slot(), colour) : colour,
                placementTime(state.competition(), row), colour);
    }

    /** A placement's shown time: the ROM display for the track, or the outcome when unfinished. */
    public static String placementTime(CompetitionHudState competition, CompetitionHudState.PlacementRow row) {
        return switch (row.outcome()) {
            case FINISHED -> LapTimeFormat.finish(competition.trackKind(), row.totalFrames(), row.lapFrames());
            case DNF -> "DNF";
            case DNS -> "DNS";
        };
    }

    /** Finishers in rank order, then DNF, then DNS (the host's ranks are kept; nothing is re-ranked). */
    public static List<CompetitionHudState.PlacementRow> displayOrder(List<CompetitionHudState.PlacementRow> rows) {
        return rows.stream().sorted(Comparator
                .comparingInt((CompetitionHudState.PlacementRow row) -> row.outcome().ordinal())
                .thenComparingInt(CompetitionHudState.PlacementRow::rank)).toList();
    }

    /**
     * The one-row plates stacked under the room panel in the right column: the armed cutoff
     * ({@code CUT 0:42}, amber) and a teleport swap ({@code SWAP ⇄ NAME} or {@code SWAP: NONE},
     * cyan) while its notice lasts. Each sizes to its text.
     */
    public static List<HudTextLayout.Panel> plates(int width, int height, MultiplayerHudState state,
                                                   HudTextLayout.Panel room, boolean localFinished) {
        if (state == null || !state.active()) return List.of();
        CompetitionHudState competition = state.competition();
        if (!competition.isCompetition() || competition.lateJoin() || state.connectionLost()
                || state.kickReason() != null || "ROUND_END".equals(state.phase())) {
            return List.of();
        }
        // Under a room panel in the top band; above the finish strip's panel, the top lane is free.
        boolean under = room != null && room.y() < height / 2;
        int y = under ? room.y() + room.height() + PLATE_GAP
                : competition.isArena() ? ARENA_TOP : HudTextLayout.ROOM_TOP;
        List<HudTextLayout.Panel> plates = new ArrayList<>();
        if (competition.cutoffArmed()) {
            HudTextLayout.Panel cut = HudTextLayout.panel(width, y, HudTextLayout.PANEL_WIDTH,
                    List.of(new HudTextLayout.Line("CUT " + LapTimeFormat.countdown(competition.cutoffRemainingMillis()),
                            HudTextLayout.AMBER)), HudTextLayout.AMBER);
            plates.add(cut);
            y += cut.height() + PLATE_GAP;
        }
        CompetitionHudState.SwapNotice swap = competition.swapNotice();
        if (swap.remainingFrames() > 0 && !localFinished) {
            HudTextLayout.Line line = swap.partnerName().isEmpty()
                    ? new HudTextLayout.Line("SWAP: NONE", HudTextLayout.ACCENT)
                    : HudTextLayout.Line.named("SWAP " + HudTextLayout.SWAP_MARK + " ", swap.partnerName(),
                            HudTextLayout.WHITE, "", HudTextLayout.ACCENT);
            plates.add(HudTextLayout.panel(width, y, HudTextLayout.PANEL_WIDTH, List.of(line), HudTextLayout.ACCENT));
        }
        return List.copyOf(plates);
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

    /** A track key's catalog label (an act's, or an arena's ROM zone name); the key itself when unknown. */
    static String trackLabel(String key) {
        if (key == null) return "?";
        return TimeAttackTrackCatalog.forKey(key).map(TimeAttackTrackCatalog.Track::label).orElse(key);
    }

    /**
     * The race along the bottom edge: the whole screen width (the lives counter is hidden in Time
     * Attack). Each racer is a segment in their name colour at their true place; racers at the same
     * place stack upwards, so a column's height counts them. The local player's segment is outlined
     * in white. A hurt racer's segment strobes red and shakes for {@link MultiplayerHudState#hurtFlash}.
     */
    public static void drawProgress(com.openggf.mods.ui.PixelCanvas canvas, MultiplayerHudState state,
                                    int levelWidth, int localCentreX) {
        int width = canvas.width() - 2 * PROGRESS_MARGIN;
        if (width < 32) return;
        int x = PROGRESS_MARGIN;
        int y = canvas.height() - PROGRESS_HEIGHT - 3;
        canvas.fill(x, y, width, PROGRESS_HEIGHT, HudTextLayout.FILL);
        int left = x + 5;
        int trackWidth = width - 15;
        int middle = y + PROGRESS_HEIGHT / 2;
        canvas.fill(left, middle, trackWidth, 1, 0xFF596F85);
        canvas.fill(left, middle - 3, 1, 7, HudTextLayout.MUTED);
        // Chequered finish flag.
        int flag = left + trackWidth + 2;
        canvas.fill(flag, middle - 4, 6, 8, 0xFF101010);
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 3; column++) {
                if ((row + column) % 2 == 0) canvas.fill(flag + column * 2, middle - 4 + row * 2, 2, 2,
                        HudTextLayout.WHITE);
            }
        }
        int stock = com.openggf.net.identity.PlayerAppearance.STOCK.nameArgb();
        // A Competition round places racers by their share of the whole race (lap or star post, then
        // the distance within it), so a lapped racer is behind on the strip, not level with you.
        CompetitionHudState competition = state.competition();
        java.util.Map<Integer, Integer> share = competition.isCompetition() ? competition.progressPermille()
                : java.util.Map.of();
        if (competition.isCompetition() && competition.isArena()) {
            for (int tick : MinimapLayout.lapTicks(competition.lapCount(), trackWidth)) {
                canvas.fill(left + tick + 1, middle - 2, 1, 5, 0xFF596F85);
            }
        }
        // {x on the track, slot, local}.
        List<int[]> markers = new ArrayList<>();
        Integer localShare = share.get(state.localSlot());
        if (localShare != null || localCentreX >= 0) {
            int at = localShare != null ? MinimapLayout.permilleX(localShare, trackWidth)
                    : progressX(localCentreX, levelWidth, trackWidth);
            markers.add(new int[] {at, state.localSlot(), 1, state.hurtFlash(state.localSlot()) > 0 ? 1 : 0});
        }
        for (var player : state.farPlayers()) {
            if (player.status() != -1 && player.status() != 1 && player.status() != 2) continue;
            if (player.slot() == state.localSlot()) continue;
            Integer racerShare = player.status() == 2 ? Integer.valueOf(MinimapLayout.PERMILLE) : share.get(player.slot());
            int at;
            if (!share.isEmpty()) {
                if (racerShare == null) continue;
                at = MinimapLayout.permilleX(racerShare, trackWidth);
            } else {
                at = progressX(player.status() == 2 ? levelWidth : player.cellX() * 64, levelWidth, trackWidth);
            }
            markers.add(new int[] {at, player.slot(), 0, state.hurtFlash(player.slot()) > 0 ? 1 : 0});
        }
        int base = y + PROGRESS_HEIGHT - 2;
        List<int[]> placed = stack(markers);
        // A column taller than STACK_MAX shows its first STACK_MAX segments (you, then anyone hurt,
        // then by slot) under the number of racers there, so a big room's pack stays one short
        // column instead of climbing the screen.
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        for (int[] p : placed) counts.merge(p[1], 1, Integer::sum);
        List<int[]> labels = new ArrayList<>();
        counts.entrySet().stream().filter(entry -> entry.getValue() > STACK_MAX)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(entry -> {
                    String count = Integer.toString(entry.getValue());
                    int w = CompactFont.width(count, 1);
                    int lx = left + entry.getKey() + SEGMENT_WIDTH / 2 - w / 2;
                    // Keep the larger counts when two columns' numbers would overlap.
                    for (int[] other : labels) if (lx < other[0] + other[1] + 2 && other[0] < lx + w + 2) return;
                    labels.add(new int[] {lx, w, entry.getValue()});
                });
        int labelY = base - STACK_MAX * STACK_STEP - 9;
        for (int[] label : labels) {
            CompactFont.shadowed(canvas, Integer.toString(label[2]), label[0], labelY, 1, HudTextLayout.WHITE,
                    0xC0000000);
        }
        // Hurt racers draw after the rest and the local player last, so no outline covers theirs.
        List<int[]> drawOrder = new ArrayList<>();
        for (int[] p : placed) if (p[2] < STACK_MAX) drawOrder.add(p);
        drawOrder.sort(java.util.Comparator.comparingInt(spot -> markers.get(spot[0])[2] == 1 ? 2
                : state.hurtFlash(markers.get(spot[0])[1]) > 0 ? 1 : 0));
        for (int[] spot : drawOrder) {
            int[] m = markers.get(spot[0]);
            int sx = left + spot[1];
            int sy = base - (spot[2] + 1) * STACK_STEP;
            int colour = state.nameColour(m[1], stock);
            int dx = 0;
            if (state.hurtFlash(m[1]) > 0) {
                if ((state.tick() / 3) % 2 == 0) colour = HURT;
                dx = (state.tick() / 2) % 2 == 0 ? -1 : 1;
            }
            boolean local = m[2] == 1;
            canvas.fill(sx - 1 + dx, sy - 1, SEGMENT_WIDTH + 2, SEGMENT_HEIGHT + 2,
                    local ? HudTextLayout.WHITE : 0xFF101820);
            canvas.fill(sx + dx, sy, SEGMENT_WIDTH, SEGMENT_HEIGHT, 0xFF000000 | colour);
        }
    }

    /**
     * Where each marker goes: {@code {marker index, x on the track, level}}. Positions are kept
     * true to within one {@link #STACK_BIN}-pixel column; racers in the same column stack upwards,
     * one segment each, so the column's height is how many are there (up to {@link #STACK_MAX},
     * then a number). The local player sits at the bottom of their column, then anyone hurt,
     * then the others in slot order. Markers are {@code {x, slot, local, hurt}}.
     */
    static List<int[]> stack(List<int[]> markers) {
        java.util.Map<Integer, Integer> heights = new java.util.HashMap<>();
        Integer[] order = new Integer[markers.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, java.util.Comparator
                .comparingInt((Integer i) -> -markers.get(i)[2])
                .thenComparingInt(i -> markers.get(i).length > 3 ? -markers.get(i)[3] : 0)
                .thenComparingInt(i -> markers.get(i)[1]));
        List<int[]> placed = new ArrayList<>();
        for (int i : order) {
            int column = markers.get(i)[0] / STACK_BIN;
            int level = heights.merge(column, 1, Integer::sum) - 1;
            placed.add(new int[] {i, column * STACK_BIN, level});
        }
        return placed;
    }

    private static int progressX(int position, int levelWidth, int width) {
        return (int) Math.max(0, Math.min(width - 2, (long) position * (width - 2) / levelWidth));
    }
}
