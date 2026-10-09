package starpost.festivals;

import com.openggf.mods.scene.SceneCanvas;
import java.util.ArrayList;
import java.util.List;
import starpost.core.Game;
import starpost.ui.Text;
import starpost.valley.Valley;

/**
 * The Valley Fair across the town, walked on the real controller: the grange display at the
 * plaza where Robotnik judges, the slot booth by Robomart (Casino Night's faces when Sonic 2 is
 * supplied), and the spring test at the west end. Each booth is a doorway (up) for the fair's
 * length; Carnival Night plays. Calling the judge at the grange ends the fair with the judging.
 */
final class FairScreen extends FestivalScreen {
    private static final int CNZ1 = 0x07;            // Sonic3kMusic CNZ1

    final List<String> display = new ArrayList<>();
    private boolean judging;
    private int judgedAt;
    private int score;
    private final List<Valley.Place> booths = new ArrayList<>();

    FairScreen(FestivalSystem sys, Festival festival) {
        super(sys, festival);
    }

    /** The booths' x in the valley: the plaza, between the Workshop and Robomart, and the west end of town. */
    static int boothX(int i) {
        return switch (i) {
            case 0 -> 2 * 256 + 200;
            case 1 -> 5 * 256 + 3;
            default -> 2 * 256 - 40;
        };
    }

    /** A booth's doorway id. */
    static String id(int i) {
        return switch (i) {
            case 0 -> "fair_grange";
            case 1 -> "fair_slots";
            default -> "fair_spring";
        };
    }

    static String boothName(int i) {
        return switch (i) {
            case 0 -> "GRANGE";
            case 1 -> "SLOTS";
            default -> "SPRING TEST";
        };
    }

    @Override
    void begin() {
        holdTown(boothX(0) - 50);
        play.valley().labels = true;
        Valley valley = play.valley().valley;
        String[] labels = {"GRANGE DISPLAY", "SLOT BOOTH", "SPRING TEST"};
        for (int i = 0; i < 3; i++) {
            Valley.Place place = new Valley.Place(id(i), boothX(i), 20, labels[i]);
            booths.add(place);
            valley.places.add(0, place);       // ahead of the festival's own doorway at the plaza
        }
        play.places.put(id(0), s -> s.push(new GrangeScreen(this)));
        play.places.put(id(1), s -> s.push(new SlotsScreen(this)));
        play.places.put(id(2), s -> s.push(new StrengthScreen(this)));
        shell.music.want("s3k", CNZ1);
    }

    @Override
    void step() {
        if (t == 20) {
            caption("robotnik", "WELCOME TO THE VALLEY FAIR. I AM YOUR JUDGE. SET UP YOUR GRANGE DISPLAY AT THE PLAZA. "
                    + "BRIBES AT THE CARAVAN.");
        }
        if (judging) {
            freezeInput();
            play.update(shell);
            int since = t - judgedAt;
            if (since == 1) {
                caption("robotnik", "HMM. " + Fair.ROBOTNIK + " POINTS FOR MY ROBOMART DELUXE HAMPER, NATURALLY. AND YOURS...");
            } else if (since == 150) {
                int place = Fair.place(score);
                caption("robotnik", place == 1 ? "...I CANNOT DENY IT. IT IS UNDENIABLE. FIRST PRIZE. I HATE THIS."
                        : "...A CLOSE SECOND. TO ME. OUTSTANDING JUDGING, IF I SAY SO MYSELF.");
            } else if (since == 300) {
                end();
            }
            return;
        }
        stepTown();
    }

    /** The grange booth's "call the judge": the fair ends with the judging. */
    void callJudge() {
        score = Fair.score(shell.game, display);
        judging = true;
        judgedAt = t;
    }

    private void end() {
        Game game = shell.game;
        List<String> lines = new ArrayList<>();
        lines.add("YOURS " + score + " POINTS, ROBOTNIK'S " + Fair.ROBOTNIK);
        lines.addAll(Fair.reward(game, festivals, score));
        int place = Fair.place(score);
        finish(place == 1 ? "BLUE RIBBON" : place == 2 ? "SECOND PRIZE" : "THIRD PRIZE", lines);
    }

    @Override
    void restore() {
        super.restore();
        play.valley().valley.places.removeAll(booths);
        for (int i = 0; i < 3; i++) {
            play.places.remove(id(i));
        }
    }

    @Override
    void paint(SceneCanvas canvas) {
        play.draw(shell, canvas);
    }

    @Override
    void paintOver(SceneCanvas canvas) {
        var hud = shell.art.hud;
        canvas.draw(hud.rings, 16, 6, com.openggf.mods.scene.SceneDraw.plain());
        hud.number(canvas, Integer.toString(shell.game.rings), 66, 2);
        long shown = display.stream().filter(java.util.Objects::nonNull).count();
        String note = shown == 0 ? "UP AT A BOOTH TO PLAY" : "DISPLAY: " + shown + " OF " + Fair.DISPLAY_SLOTS
                + ". THE JUDGE WAITS AT THE GRANGE";
        if (!judging && !shell.hasOverlay()) {
            Text.centred(canvas, note, canvas.height() - 16, Text.YELLOW);
        }
    }
}
