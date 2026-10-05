package paradise.ui;

import com.openggf.graphics.GraphicsManager;
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
                       int firstCharge, int secondCharge, int meterValue, int activePlayer,
                       List<PlayerScore> players, int actIndex, String message, boolean paused, boolean results) {
        public View {
            Objects.requireNonNull(mode, "mode"); Objects.requireNonNull(stage, "stage"); Objects.requireNonNull(message, "message");
            players = List.copyOf(players);
            if (players.isEmpty() || players.size() > 2 || activePlayer < 0 || activePlayer >= players.size()
                    || actIndex < 0 || actIndex > 1 || elevationDegrees < 0 || elevationDegrees > 75
                    || (direction != -1 && direction != 1) || power < 0 || power > 1000
                    || firstCharge < 0 || firstCharge > 500 || secondCharge < 0 || secondCharge > 500
                    || meterValue < 0 || meterValue > 500) throw new IllegalArgumentException("Invalid golf view");
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
        String stage = GolfText.fit(view.stage().replace('_', ' '), width - 154, 1);
        GolfText.draw(graphics, stage, width - 12 - GolfText.width(stage, 1), bottom + 5);
        boolean charging = view.stage().contains("CHARGE");
        GolfText.meter(graphics, 12, bottom + 16, Math.min(140, width / 2 - 20),
                charging ? view.meterValue() : view.power(), charging ? 500 : 1000);
        String power = "POWER " + (view.power() / 10) + "%  " + view.firstCharge() + "+" + view.secondCharge();
        GolfText.draw(graphics, GolfText.fit(power, width - 174, 1), 166, bottom + 17);
        GolfText.draw(graphics, GolfText.fit(view.message(), width - 24, 1), 12, bottom + 29);
    }
}
