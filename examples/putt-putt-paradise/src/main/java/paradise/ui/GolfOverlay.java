package paradise.ui;

import com.openggf.graphics.GraphicsManager;
import paradise.model.GolfRules;
import java.util.List;
import java.util.Objects;

/** Read-only UI values supplied by the authoritative adapter or guest presentation. */
public final class GolfOverlay {
    public record PlayerScore(String name, int strokes, int penalties, boolean finished, boolean dnf) {
        public PlayerScore {
            Objects.requireNonNull(name, "name");
            if (strokes < 0 || penalties < 0) throw new IllegalArgumentException("Negative score");
        }
        public int total() { return Math.addExact(strokes, penalties); }
    }

    public record View(String mode, String stage, int elevationDegrees, int direction, int power,
                       int spin, int targetSpin, int meterValue, int activePlayer,
                       List<PlayerScore> players, int actIndex, String message, boolean paused, boolean results) {
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

    private GolfOverlay() { }

    public static void draw(GraphicsManager graphics, int logicalWidth, View view) {
        Objects.requireNonNull(view, "view");
        int width = Math.clamp(logicalWidth, 320, 800);
        GolfText.panel(graphics, 6, 6, width - 12, 24, GolfText.INK, 0.88f);
        GolfText.draw(graphics, "EHZ " + (view.actIndex() + 1) + "  " + view.mode(), 12, 11, 1, GolfText.GOLD);
        String player = "P" + (view.activePlayer() + 1) + " " + view.players().get(view.activePlayer()).name();
        player = GolfText.fit(player, (width - 24) / 2, 1);
        GolfText.draw(graphics, player, width - 12 - GolfText.width(player, 1), 11);
        String score = "";
        for (int i = 0; i < view.players().size(); i++) {
            var golfer = view.players().get(i);
            score += (i > 0 ? "    " : "") + "P" + (i + 1) + " " + golfer.strokes() + "+" + golfer.penalties()
                    + "=" + golfer.total() + (golfer.dnf() ? " DNF" : golfer.finished() ? " DONE" : "");
        }
        GolfText.draw(graphics, GolfText.fit(score, width - 24, 1), 12, 21);

        if (view.results() || view.paused()) {
            int panelWidth = Math.min(width - 32, 360), left = (width - panelWidth) / 2;
            GolfText.panel(graphics, left, 65, panelWidth, 82, GolfText.INK, 0.94f);
            GolfText.frame(graphics, left, 65, panelWidth, 82, GolfText.GOLD);
            GolfText.centered(graphics, view.results() ? "SCORECARD" : "PAUSED", width, 77, 2, GolfText.GOLD);
            for (int i = 0; view.results() && i < view.players().size(); i++) {
                var golfer = view.players().get(i);
                String line = golfer.name() + "  " + golfer.total() + (golfer.dnf() ? " DNF" : " STROKES");
                GolfText.draw(graphics, GolfText.fit(line, panelWidth - 24, 1), left + 12, 101 + i * 13);
            }
            if (view.results()) GolfText.draw(graphics, GolfText.fit(view.message(), panelWidth - 24, 1), left + 12, 132, 1, GolfText.CREAM);
        }

        int bottom = 180;
        GolfText.panel(graphics, 6, bottom, width - 12, 38, GolfText.INK, 0.9f);
        String aim = (view.direction() < 0 ? "< " : "> ") + (view.elevationDegrees() == 0 ? "PUTT" : "CHIP")
                + " " + view.elevationDegrees() + " DEG";
        GolfText.draw(graphics, aim, 12, bottom + 5, 1, GolfText.GOLD);
        String stage = GolfText.fit(switch(view.stage()) {
            case "SPIN" -> "HIT POINT";
            case "FEEDBACK" -> "CHARGING";
            case "PRE_RELEASE" -> "READY";
            default -> view.stage();
        }, width - 154, 1);
        GolfText.draw(graphics, stage, width - 12 - GolfText.width(stage, 1), bottom + 5);
        boolean spinStage = view.stage().equals("SPIN"), powerStage = view.stage().equals("POWER");
        int barWidth = width - 112, maximum = spinStage ? 200 : 1000;
        int value = spinStage ? view.meterValue() + 100 : powerStage ? view.meterValue() : view.power();
        int fill = (barWidth - 2) * value / maximum;
        GolfText.panel(graphics, 12, bottom + 16, barWidth, 8, GolfText.BLUE, 1);
        GolfText.panel(graphics, 13, bottom + 17, fill, 6,
                !spinStage && value == GolfRules.MAX_POWER ? GolfText.PINK : GolfText.GOLD, 1);
        GolfText.frame(graphics, 12, bottom + 16, barWidth, 8, GolfText.CREAM);
        if (spinStage) {
            int low = Math.max(-100, view.targetSpin() - GolfRules.NEUTRAL_SPIN_WINDOW);
            int high = Math.min(100, view.targetSpin() + GolfRules.NEUTRAL_SPIN_WINDOW);
            int left = (barWidth - 2) * (low + 100) / 200;
            int right = (barWidth - 2) * (high + 100) / 200;
            GolfText.panel(graphics, 13 + left, bottom + 17, Math.min(right - left + 1, barWidth - 2 - left), 6, GolfText.CYAN, 1);
        }
        if (spinStage || powerStage) GolfText.panel(graphics,
                13 + (barWidth - 3) * value / maximum, bottom + 14, 1, 12, GolfText.CREAM, 1);
        String power = spinStage ? "SPIN " + view.meterValue() : "POWER " + value / 10 + "%";
        GolfText.draw(graphics, power, width - 92, bottom + 17);
        if (view.elevationDegrees() > 0 && (spinStage || powerStage)) drawHitPanel(graphics, width, view);
        GolfText.draw(graphics, GolfText.fit(view.message(), width - 24, 1), 12, bottom + 29);
    }

    /** Contact-point diagram is code drawn; the golfer itself remains ROM-backed native art. */
    private static void drawHitPanel(GraphicsManager graphics, int width, View view) {
        int left = width - 66, centreX = left + 30, centreY = 140, radius = 12;
        GolfText.panel(graphics, left, 112, 60, 53, GolfText.INK, 0.92f);
        GolfText.frame(graphics, left, 112, 60, 53, GolfText.GOLD);
        GolfText.draw(graphics, "TOP", left + 21, 116, 1, GolfText.CREAM);
        GolfText.draw(graphics, "BACK", left + 18, 155, 1, GolfText.CREAM);
        for(int y=-radius;y<=radius;y++) {
            int dx=(int)Math.round(Math.sqrt(radius*radius-y*y));
            GolfText.panel(graphics, centreX-dx, centreY+y, 1, 1, GolfText.GOLD, 1);
            GolfText.panel(graphics, centreX+dx, centreY+y, 1, 1, GolfText.GOLD, 1);
        }
        int target = view.stage().equals("SPIN") ? view.targetSpin() : view.spin();
        int targetY = centreY - radius * target / 100;
        GolfText.panel(graphics, centreX-radius-4, targetY-1, 2*radius+9, 3, GolfText.CYAN, 0.8f);
        int marker = view.stage().equals("SPIN") ? view.meterValue() : view.spin();
        int markerY = centreY - radius * marker / 100;
        GolfText.panel(graphics, centreX-radius-8, markerY, 6, 1, GolfText.CREAM, 1);
        GolfText.panel(graphics, centreX+radius+3, markerY, 6, 1, GolfText.CREAM, 1);
    }
}
