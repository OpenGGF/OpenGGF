package paradise.ui;

import com.openggf.graphics.GraphicsManager;
import paradise.model.GolfRules;
import java.util.List;
import java.util.Objects;

/**
 * Read-only UI values supplied by the authoritative adapter or guest presentation.
 * Layout: score chips across the top, a shot panel that slides up while the golfer has
 * choices to make and away while the ball moves, and a scorecard that tallies like the
 * Sonic 2 results screen. Animation values arrive in {@link Motion}; nothing here keeps time.
 */
public final class GolfOverlay {
    public static final int SONIC_BLUE = 0x3C78E6, TAILS_ORANGE = 0xF4A23C, RED = 0xE5484D,
            ORANGE = 0xF59B3A, WHITE = 0xFFFFFF, NIGHT = 0x0B2230, MUTED = 0x9DB7C4;

    public record PlayerScore(String name, int strokes, int penalties, boolean finished, boolean dnf) {
        public PlayerScore {
            Objects.requireNonNull(name, "name");
            if (strokes < 0 || penalties < 0) throw new IllegalArgumentException("Negative score");
        }
        public int total() { return Math.addExact(strokes, penalties); }
    }

    public record View(String mode, String stage, int elevationDegrees, int direction, int power,
                       int spin, int targetSpin, int meterValue, int activePlayer,
                       List<PlayerScore> players, int actIndex, String message, boolean paused, boolean results,
                       boolean showShotControls) {
        public View(String mode, String stage, int elevationDegrees, int direction, int power,
                    int spin, int targetSpin, int meterValue, int activePlayer,
                    List<PlayerScore> players, int actIndex, String message, boolean paused, boolean results) {
            this(mode, stage, elevationDegrees, direction, power, spin, targetSpin, meterValue, activePlayer,
                    players, actIndex, message, paused, results, true);
        }
        public View {
            Objects.requireNonNull(mode, "mode"); Objects.requireNonNull(stage, "stage"); Objects.requireNonNull(message, "message");
            players = List.copyOf(players);
            if (players.isEmpty() || players.size() > 2 || activePlayer < 0 || activePlayer >= players.size()
                    || actIndex < 0 || actIndex > 1 || elevationDegrees < 0 || elevationDegrees > 90
                    || (direction != -1 && direction != 1) || power < 0 || power > 1000
                    || spin < -100 || spin > 100 || targetSpin < -100 || targetSpin > 100
                    || meterValue < (stage.equals("SPIN") ? -100 : 0)
                    || meterValue > (stage.equals("SPIN") ? 100 : 1000)) throw new IllegalArgumentException("Invalid golf view");
        }
    }

    /** Per-frame animation values; {@link #still} draws a settled frame for previews and tests. */
    public record Motion(float hudShown, float lockFlash, float pop0, float pop1, long clock, int shake,
                         int tally, boolean tallyDone, long resultsAge, String watchDetail, Progress progress,
                         boolean pill) {
        // resultsAge counts from the scorecard's drop; negative while a finish celebration plays.
        public Motion { Objects.requireNonNull(watchDetail); }
        public static Motion still(View view) {
            int most = view.players().stream().mapToInt(PlayerScore::total).max().orElse(0);
            return new Motion(controlStage(view) ? 1 : 0, 0, 0, 0, 0, 0, most, true, 1000, "", null, true);
        }
    }
    /** Course x positions for the hole chip's progress line; a negative lie is unknown. */
    public record Progress(int startX, int finishX, List<Integer> lies) {
        public Progress { lies = List.copyOf(lies); }
    }

    private GolfOverlay() { }

    public static int characterColour(String name) {
        return name.equalsIgnoreCase("TAILS") ? TAILS_ORANGE : SONIC_BLUE;
    }

    /** The shot panel belongs on screen only while the viewer is choosing or locking a shot. */
    public static boolean controlStage(View view) {
        return view.showShotControls() && !view.paused() && !view.results() && switch (view.stage()) {
            case "AIM", "SPIN", "POWER", "FEEDBACK", "PRE_RELEASE" -> true;
            default -> false;
        };
    }

    public static void draw(GraphicsManager graphics, int logicalWidth, View view) {
        Objects.requireNonNull(view, "view");
        draw(new GolfCanvas(graphics, Math.clamp(logicalWidth, 320, 800), 224), view, Motion.still(view));
    }

    public static void draw(GolfCanvas canvas, View view, Motion motion) {
        Objects.requireNonNull(view, "view"); Objects.requireNonNull(motion, "motion");
        topBar(canvas, view, motion);
        if (view.results()) { scorecard(canvas, view, motion); return; }
        if (view.paused()) return; // GolfOverlay.pause draws the menu over a dimmed course.
        float shown = motion.hudShown();
        if (shown > 0.001f) controls(canvas.offset(0, Math.round((1 - shown) * 46)), view, motion);
        if (shown < 0.999f && motion.pill()) watchPill(canvas.fade(1 - shown), view, motion);
    }

    // ---- top bar ------------------------------------------------------------------------
    private static String modeLabel(String mode) {
        return switch (mode) {
            case "PRACTICE" -> "PRACTICE"; case "LOCAL" -> "LOCAL MATCH";
            case "HOST" -> "ONLINE HOST"; case "JOIN" -> "ONLINE GUEST"; default -> mode;
        };
    }

    private static void topBar(GolfCanvas c, View view, Motion motion) {
        String hole = "EMERALD HILL " + (view.actIndex() + 1);
        String mode = modeLabel(view.mode());
        int left = 6, chipWidth = Math.max(GolfText.width(hole, 1), GolfText.width(mode, 1)) + 16;
        c.panel(left, 4, chipWidth, 25, NIGHT, 0.86f, GolfText.GOLD);
        c.rect(left, 4, 3, 25, GolfText.GOLD);
        c.text(hole, left + 8, 8, 1, GolfText.GOLD);
        c.text(mode, left + 8, 16, 1, MUTED);
        var progress = motion.progress();
        if (progress != null && !view.results()) {
            int[] lies = progress.lies().stream().mapToInt(Integer::intValue).toArray();
            String[] names = view.players().stream().map(PlayerScore::name).toArray(String[]::new);
            if (lies.length == names.length)
                GolfCards.progress(c, left + 8, chipWidth - 14, 26, progress.startX(), progress.finishX(), lies, names,
                        view.activePlayer(), motion.clock());
        }

        int count = view.players().size();
        int available = c.width() - (left + chipWidth + 8) - 6;
        int chip = Math.min(96, (available - (count - 1) * 4) / count);
        int x = c.width() - 6 - count * chip - (count - 1) * 4;
        for (int i = 0; i < count; i++, x += chip + 4) {
            var golfer = view.players().get(i);
            boolean active = i == view.activePlayer() && !view.results();
            float pop = i == 0 ? motion.pop0() : motion.pop1();
            int lift = Math.round(GolfMotion.easeOut(pop) * 2);
            c.panel(x, 4, chip, 25, active ? 0x173A4F : NIGHT, 0.86f, active ? GolfText.GOLD : 0x2E5568);
            c.rect(x, 4, 3, 25, characterColour(golfer.name()));
            String name = GolfText.fit("P" + (i + 1) + " " + golfer.name(), chip - 12, 1);
            c.text(name, x + 7, 8, 1, active ? GolfText.CREAM : MUTED);
            String status = golfer.dnf() ? "DNF" : golfer.finished() ? "DONE" : "";
            String total = Integer.toString(golfer.total());
            int numberColour = pop > 0 ? GolfMotion.mix(GolfText.GOLD, WHITE, pop) : golfer.finished() ? 0x7BE0A2 : GolfText.GOLD;
            c.shadowed(total, x + 7, 17 - lift, 1, golfer.dnf() ? RED : numberColour);
            String detail = golfer.penalties() > 0 ? "+" + golfer.penalties() + " PEN" : !status.isEmpty() ? status
                    : golfer.total() == 1 ? "STROKE" : "STROKES";
            if (golfer.penalties() > 0 && !status.isEmpty()) detail = detail + " " + status;
            detail = GolfText.fit(detail, chip - 16 - GolfText.width(total, 1), 1);
            c.text(detail, x + chip - 5 - GolfText.width(detail, 1), 17, 1, golfer.penalties() > 0 ? 0xFFA0A0 : MUTED);
            if (active && count > 1) {
                // Active-golfer marker bobs under its chip.
                int bob = Math.round(GolfMotion.pulse(motion.clock(), 40) * 2), mid = x + chip / 2;
                c.rect(mid - 3, 31 + bob, 7, 1, GolfText.GOLD); c.rect(mid - 2, 32 + bob, 5, 1, GolfText.GOLD);
                c.rect(mid - 1, 33 + bob, 3, 1, GolfText.GOLD); c.rect(mid, 34 + bob, 1, 1, GolfText.GOLD);
            }
        }
    }

    // ---- shot panel ---------------------------------------------------------------------
    private static String stageColourLabel(String stage) { return GolfHud.stageLabel(stage); }
    private static int stageColour(String stage) {
        return switch (stage) {
            case "AIM", "SPIN" -> GolfText.CYAN;
            case "POWER" -> GolfText.PINK;
            case "FEEDBACK", "PRE_RELEASE", "CHARGING" -> ORANGE;
            default -> GolfText.GOLD;
        };
    }

    private static void controls(GolfCanvas c, View view, Motion motion) {
        int width = c.width(), bottom = 180;
        c.panel(6, bottom, width - 12, 38, NIGHT, 0.9f, GolfText.GOLD);
        String aim = (view.direction() < 0 ? "< " : "> ") + (view.elevationDegrees() == 0 ? "PUTT"
                : "CHIP " + view.elevationDegrees() + " DEG");
        c.text(aim, 12, bottom + 5, 1, GolfText.GOLD);
        String stage = GolfText.fit(stageColourLabel(view.stage()), width - 154, 1);
        int stageWidth = GolfText.width(stage, 1) + 8, colour = stageColour(view.stage());
        c.rect(width - 12 - stageWidth, bottom + 3, stageWidth, 11, colour, 0.22f);
        c.text(stage, width - 8 - stageWidth, bottom + 5, 1, colour);

        boolean spinStage = view.stage().equals("SPIN"), powerStage = view.stage().equals("POWER");
        int barWidth = width - 112, maximum = spinStage ? 200 : 1000;
        int value = spinStage ? view.meterValue() + 100 : powerStage ? view.meterValue() : view.power();
        int inner = barWidth - 2, fill = inner * value / maximum, y = bottom + 16;
        c.rect(12, y, barWidth, 8, 0x0F2733);
        if (spinStage) {
            c.rect(13, y + 1, inner, 6, GolfText.BLUE);
            int low = Math.max(-100, view.targetSpin() - GolfRules.NEUTRAL_SPIN_WINDOW);
            int high = Math.min(100, view.targetSpin() + GolfRules.NEUTRAL_SPIN_WINDOW);
            int l = inner * (low + 100) / 200, r = inner * (high + 100) / 200;
            float glow = 0.7f + 0.3f * GolfMotion.pulse(motion.clock(), 24);
            c.rect(13 + l, y + 1, Math.min(r - l + 1, inner - l), 6, GolfText.CYAN, glow);
            c.rect(13 + inner / 2, y + 1, 1, 6, GolfText.CREAM, 0.35f);
        } else {
            boolean full = value == GolfRules.MAX_POWER;
            // Gold rises through orange to pink; a full sweep is solid, pulsing pink.
            int goldEnd = Math.min(fill, inner * 6 / 10), orangeEnd = Math.min(fill, inner * 9 / 10);
            if (full) {
                c.rect(13, y + 1, inner, 6, GolfText.PINK);
                c.rect(13, y + 1, inner, 2, WHITE, 0.25f + 0.25f * GolfMotion.pulse(motion.clock(), 16));
            } else {
                c.rect(13, y + 1, goldEnd, 6, GolfText.GOLD);
                if (orangeEnd > goldEnd) c.rect(13 + goldEnd, y + 1, orangeEnd - goldEnd, 6, ORANGE);
                if (fill > orangeEnd) c.rect(13 + orangeEnd, y + 1, fill - orangeEnd, 6, GolfText.PINK);
                if (fill > 0) c.rect(13, y + 1, fill, 2, WHITE, 0.22f);
            }
            if (motion.lockFlash() > 0) c.rect(13, y + 1, Math.max(1, fill), 6, WHITE, 0.85f * motion.lockFlash());
        }
        for (int q = 1; q < 4; q++) c.rect(13 + inner * q / 4, y + 5, 1, 2, NIGHT, 0.8f);
        c.frame(12, y, barWidth, 8, GolfText.CREAM);
        if (spinStage || powerStage) {
            int marker = 13 + (barWidth - 3) * value / maximum;
            c.rect(marker, y - 2, 1, 12, WHITE);
            c.rect(marker - 1, y - 3, 3, 1, WHITE);
        }
        String meter = spinStage ? "SPIN " + (view.meterValue() > 0 ? "+" : "") + view.meterValue() : "POWER " + value / 10 + "%";
        c.text(meter, width - 92, bottom + 17, 1, !spinStage && value == GolfRules.MAX_POWER ? GolfText.PINK : GolfText.CREAM);
        if (view.elevationDegrees() > 0 && (spinStage || powerStage)) drawHitPanel(c, view, motion);
        c.text(GolfText.fit(view.message(), width - 24, 1), 12, bottom + 29, 1, GolfText.CREAM);
    }

    /** Contact-point diagram is code drawn; the golfer itself remains ROM-backed native art. */
    private static void drawHitPanel(GolfCanvas c, View view, Motion motion) {
        int left = c.width() - 66, centreX = left + 30, centreY = 140, radius = 12;
        c.panel(left, 112, 60, 53, NIGHT, 0.92f, GolfText.GOLD);
        c.frame(left, 112, 60, 53, GolfText.GOLD);
        c.text("TOP", left + 21, 116, 1, GolfText.CREAM);
        c.text("BACK", left + 18, 155, 1, GolfText.CREAM);
        for (int y = -radius; y <= radius; y++) {
            int dx = (int) Math.round(Math.sqrt(radius * radius - y * y));
            c.rect(centreX - dx, centreY + y, 1, 1, GolfText.GOLD);
            c.rect(centreX + dx, centreY + y, 1, 1, GolfText.GOLD);
        }
        int target = view.stage().equals("SPIN") ? view.targetSpin() : view.spin();
        int targetY = centreY - radius * target / 100;
        c.rect(centreX - radius - 4, targetY - 1, 2 * radius + 9, 3, GolfText.CYAN, 0.8f);
        int marker = view.stage().equals("SPIN") ? view.meterValue() : view.spin();
        int markerY = centreY - radius * marker / 100;
        c.rect(centreX - radius - 8, markerY, 6, 1, GolfText.CREAM);
        c.rect(centreX + radius + 3, markerY, 6, 1, GolfText.CREAM);
        // Two dots orbit the ball in the chosen spin's direction (top = forward roll).
        int spin = view.stage().equals("SPIN") ? view.targetSpin() : view.spin();
        if (spin != 0) {
            double turn = motion.clock() * 0.12 * Math.signum(spin) * view.direction();
            for (int k = 0; k < 2; k++) {
                double angle = turn + k * Math.PI;
                c.rect(centreX + (int) Math.round(Math.cos(angle) * (radius - 4)) - 1,
                        centreY + (int) Math.round(Math.sin(angle) * (radius - 4)) - 1, 2, 2, GolfText.CREAM);
            }
        }
    }

    /** While the ball moves the panel steps aside; a compact pill keeps the stage and hint. */
    private static void watchPill(GolfCanvas c, View view, Motion motion) {
        String stage = stageColourLabel(view.stage());
        String hint = view.message();
        boolean shot = view.power() > 0 || view.stage().equals("WATCH");
        String locked = view.elevationDegrees() == 0 ? "PUTT" : "CHIP " + view.elevationDegrees();
        String power = view.power() / 10 + "%";
        int stageWidth = GolfText.width(stage, 1) + 10;
        int lockedWidth = shot && view.showShotControls() ? GolfText.width(locked + " " + power, 1) + 10 : 0;
        int hintRoom = c.width() - 12 - stageWidth - lockedWidth - 14;
        // The remaining-rewind count belongs beside the rewind control; Start-to-pause goes first if room is short.
        String wide = motion.watchDetail().isEmpty() ? hint : hint.contains(" REWIND")
                ? hint.replaceFirst(" REWIND", " REWIND " + motion.watchDetail()) : hint + "  " + motion.watchDetail();
        String compact = wide.replace("  START PAUSE", "").replace(" - START PAUSE", "");
        String fitted = GolfText.width(wide, 1) <= hintRoom ? wide : GolfText.fit(compact, hintRoom, 1);
        int width = stageWidth + lockedWidth + GolfText.width(fitted, 1) + 14;
        int x = 6, y = 202;
        c.panel(x, y, width, 16, NIGHT, 0.88f, stageColour(view.stage()));
        int colour = stageColour(view.stage());
        boolean blink = view.stage().equals("REWIND") && !GolfMotion.blink(motion.clock(), 20);
        if (!blink) c.text(stage, x + 5, y + 5, 1, colour);
        int cursor = x + stageWidth;
        if (lockedWidth > 0) {
            boolean full = view.power() == GolfRules.MAX_POWER;
            c.rect(cursor - 2, y + 3, lockedWidth - 4, 10, full ? GolfText.PINK : GolfText.BLUE, full ? 0.9f : 0.8f);
            c.text(locked + " " + power, cursor + 2, y + 5, 1, full ? WHITE : GolfText.CREAM);
            cursor += lockedWidth;
        }
        c.text(fitted, cursor + 2, y + 5, 1, GolfText.CREAM);
    }

    // ---- results ------------------------------------------------------------------------
    private static void scorecard(GolfCanvas c, View view, Motion motion) {
        if (motion.resultsAge() < 0) return; // A finish celebration plays before the card drops.
        int width = c.width(), panelWidth = Math.min(width - 32, 300), left = (width - panelWidth) / 2;
        float drop = GolfMotion.easeOutBack(GolfMotion.progress(motion.resultsAge(), GolfFeedback.CARD_DROP));
        int rowsEnd = 80 + view.players().size() * 18, height = rowsEnd + 34 - 40; // rowsEnd is a screen y below the 40px card top.
        int top = GolfMotion.lerp(-height - 10, 40, drop);
        c.rect(0, 0, width, 224, NIGHT, 0.35f * Math.min(1, motion.resultsAge() / 20f));
        var card = c.offset(0, top - 40);
        card.panel(left, 40, panelWidth, height, NIGHT, 0.95f, GolfText.GOLD);
        card.frame(left, 40, panelWidth, height, GolfText.GOLD);
        card.rect(left, 40, panelWidth, 22, 0xAC2638, 0.95f);
        String title = view.players().stream().anyMatch(PlayerScore::dnf) ? "MATCH OVER"
                : view.players().size() > 1 ? "MATCH RESULT" : "HOLE COMPLETE";
        card.centeredShadowed(title, 45, 2, GolfText.GOLD);
        int winner = winner(view.players());
        card.text("GOLFER", left + 12, 68, 1, MUTED);
        card.text("STROKES", left + panelWidth - 150, 68, 1, MUTED);
        card.text("PEN", left + panelWidth - 92, 68, 1, MUTED);
        card.text("TOTAL", left + panelWidth - 48, 68, 1, MUTED);
        for (int i = 0; i < view.players().size(); i++) {
            var golfer = view.players().get(i);
            int rowY = 80 + i * 18;
            boolean win = motion.tallyDone() && i == winner && view.players().size() > 1;
            if (win) card.rect(left + 6, rowY - 3, panelWidth - 12, 15, GolfText.GOLD, GolfMotion.blink(motion.clock(), 30) ? 0.3f : 0.18f);
            card.rect(left + 8, rowY - 2, 3, 13, characterColour(golfer.name()));
            card.text("P" + (i + 1) + " " + golfer.name(), left + 16, rowY + 1, 1, GolfText.CREAM);
            int counted = Math.min(golfer.total(), motion.tally());
            card.text(Integer.toString(Math.min(golfer.strokes(), counted)), left + panelWidth - 136, rowY + 1, 1, GolfText.CREAM);
            card.text(Integer.toString(golfer.penalties()), left + panelWidth - 86, rowY + 1, 1, golfer.penalties() > 0 ? 0xFFA0A0 : GolfText.CREAM);
            String total = golfer.dnf() ? "DNF" : Integer.toString(counted);
            card.shadowed(total, left + panelWidth - 44, rowY - 1, 2, golfer.dnf() ? RED : GolfText.GOLD);
        }
        String verdict = !motion.tallyDone() ? "" : view.players().size() == 1
                ? (view.players().getFirst().dnf() ? "CONCEDED" : view.players().getFirst().total() + " STROKES - WELL PLAYED!")
                : winner < 0 ? "DRAW!" : "P" + (winner + 1) + " " + view.players().get(winner).name() + " WINS!";
        if (!verdict.isEmpty()) card.centeredShadowed(GolfText.fit(verdict, panelWidth - 16, 1), rowsEnd + 6, 1, GolfText.GOLD);
        // The verdict line already states a normal result; the prompt only adds what to press.
        String prompt = view.message().matches("(DRAW|P[12] .+ WINS|HOLE COMPLETE) - START FOR MENU")
                ? "PRESS START FOR THE MENU" : view.message();
        if (motion.tallyDone() && GolfMotion.blink(motion.clock(), 40))
            card.centered(GolfText.fit(prompt, panelWidth - 16, 1), rowsEnd + 22, 1, GolfText.CREAM);
    }

    private static int winner(List<PlayerScore> players) {
        if (players.size() == 1) return 0;
        if (players.get(0).dnf() != players.get(1).dnf()) return players.get(0).dnf() ? 1 : 0;
        return Integer.compare(players.get(0).total(), players.get(1).total()) < 0 ? 0
                : players.get(0).total() > players.get(1).total() ? 1 : -1;
    }

    /** Start menu over a dimmed, frozen course. Unavailable items stay visible but muted. */
    public static void pause(GolfCanvas c, List<String> items, List<Boolean> enabled, int row, String detail, long age) {
        float t = GolfMotion.easeOut(GolfMotion.progress(age, 10));
        c.rect(0, 0, c.width(), 224, NIGHT, 0.5f * t);
        int panelWidth = Math.min(c.width() - 32, 220), left = (c.width() - panelWidth) / 2;
        var panel = c.offset(0, GolfMotion.lerp(-24, 0, t)).fade(t);
        panel.panel(left, 62, panelWidth, 112, NIGHT, 0.95f, GolfText.GOLD);
        panel.frame(left, 62, panelWidth, 112, GolfText.GOLD);
        panel.centeredShadowed("PAUSED", 70, 2, GolfText.GOLD);
        for (int i = 0; i < items.size(); i++) {
            int y = 93 + i * 14;
            boolean on = enabled.get(i), selected = i == row;
            if (selected) panel.rect(left + 10, y - 3, panelWidth - 20, 13, 0xAC2638, 0.95f);
            String label = (selected ? "> " : "  ") + items.get(i);
            panel.text(label, left + 18, y, 1, !on ? 0x6C8794 : selected ? GolfText.GOLD : GolfText.CREAM);
        }
        panel.centered(GolfText.fit(detail, panelWidth - 16, 1), 160, 1, MUTED);
    }
}
