package starpost.festivals;

import com.openggf.mods.scene.SceneCanvas;
import java.util.ArrayList;
import java.util.List;
import starpost.core.Game;
import starpost.ui.Text;
import starpost.valley.Valley;

/**
 * The Valley Fair is a menu of stationary booths: the grange display at the
 * plaza where Robotnik judges, the slot booth by Robomart (Casino Night's faces when Sonic 2 is
 * supplied), and the spring test at the west end. The fair does not need platforming; Carnival Night plays. Calling the judge at the grange ends the fair with the judging.
 */
final class FairScreen extends FestivalScreen {
    private static final int CNZ1 = 0x07;            // Sonic3kMusic CNZ1

    final List<String> display = new ArrayList<>();
    private boolean judging;
    private int judgedAt;
    private int score;
    private boolean choosing;

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
            int place = Fair.place(score);
            if (since == 1) {
                caption("robotnik", place == 0
                        ? "NO DISPLAY? THEN MY ROBOMART DELUXE HAMPER WINS BY DEFAULT. AS PLANNED."
                        : "HMM. " + Fair.ROBOTNIK + " POINTS FOR MY ROBOMART DELUXE HAMPER, NATURALLY. AND YOURS...");
            } else if (since == 150 && place > 0) {
                caption("robotnik", switch (place) {
                    case 1 -> "...I CANNOT DENY IT. IT IS UNDENIABLE. FIRST PRIZE. I HATE THIS.";
                    case 2 -> "...A CLOSE SECOND. TO ME. OUTSTANDING JUDGING, IF I SAY SO MYSELF.";
                    default -> "...THIRD. BEHIND MY HAMPER AND THE HEN'S PIES. AN HONOURABLE MENTION.";
                });
            } else if (since == (place == 0 ? 150 : 300)) {
                end();
            }
            return;
        }
        if(!choosing) {
            choosing=true;
            shell.push(new Choose("VALLEY FAIR",List.of("GRANGE DISPLAY","SLOT BOOTH","SPRING TEST","CALL THE JUDGE"),null,
                choice->{ choosing=false; if(choice<0) return; switch(choice) {
                    case 0->booth(new GrangeScreen(this)); case 1->booth(new SlotsScreen(this));
                    case 2->booth(new StrengthScreen(this)); default->callJudge();
                }}));
        }
    }

    /** Opens a booth's screen; the welcome speech has done its job by then. */
    private void booth(starpost.scene.Screen screen) {
        clearCaption();
        shell.push(screen);
    }

    /** Robotnik's lines wait under a booth's screen (they would show through it) and play out after. */
    @Override
    boolean captionHidden() {
        return shell.hasOverlay();
    }

    /** The grange booth's "call the judge": the fair ends with the judging. */
    void callJudge() {
        play.valley().labels = false;          // the booths are shut for the judging
        score = Fair.score(shell.game, display);
        judging = true;
        judgedAt = t;
    }

    private void end() {
        Game game = shell.game;
        List<String> lines = new ArrayList<>();
        lines.add(score == 0 ? "NO DISPLAY. ROBOTNIK'S HAMPER " + Fair.ROBOTNIK + " POINTS"
                : "YOURS " + score + " POINTS, ROBOTNIK'S " + Fair.ROBOTNIK);
        lines.addAll(Fair.reward(game, festivals, score));
        finish(headline(Fair.place(score)), lines);
    }

    /** The results' headline for a place (0: no display was judged). */
    static String headline(int place) {
        return switch (place) {
            case 1 -> "BLUE RIBBON";
            case 2 -> "SECOND PRIZE";
            case 3 -> "THIRD PRIZE";
            default -> "NO ENTRY";
        };
    }

    @Override
    void paint(SceneCanvas canvas) {
        play.draw(shell, canvas);
    }

    @Override
    void paintOver(SceneCanvas canvas) {
        var hud = shell.art.hud;
        hud.row(canvas, hud.rings, Integer.toString(shell.game.rings), 16, 6, 0x30, 3);
        long shown = display.stream().filter(java.util.Objects::nonNull).count();
        String note = shown == 0 ? "CHOOSE A BOOTH TO PLAY" : "DISPLAY: " + shown + " OF " + Fair.DISPLAY_SLOTS
                + ". THE JUDGE WAITS AT THE GRANGE";
        if (!judging && !shell.hasOverlay()) {
            Text.centred(canvas, note, canvas.height() - 16, Text.YELLOW);
        }
    }
}
