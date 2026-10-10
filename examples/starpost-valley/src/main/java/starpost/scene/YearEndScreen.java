package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import starpost.core.Capsule;
import starpost.core.Game;
import starpost.ui.Text;

/**
 * The Signpost Spin (design doc §9.16): on the first morning of the second year the Elder Totem
 * weighs the valley, and the old signpost spins. It flips once for each thing done well (rings
 * earned, animals home, the capsule restored, a well-kept farm). Four flips and it stops on the
 * hero's face, as Sonic 1's end-of-act signpost does; on the Robomart route it stops on Robotnik
 * whatever else was done. Then the credits.
 */
final class YearEndScreen implements Screen {
    private final Supplier<Screen> next;
    private final List<String> lines = new ArrayList<>();
    private int score;
    private boolean egg;
    private long opened;

    YearEndScreen(Supplier<Screen> next) {
        this.next = next;
    }

    /** Whether tonight's morning is the evaluation (and it has not happened). */
    static boolean due(Game game) {
        return game.calendar.year() == 2 && game.calendar.season() == 0 && game.calendar.day() == 1
                && !game.flags.contains("evaluated_y1");
    }

    @Override
    public void enter(Shell shell) {
        Game game = shell.game;
        game.flags.add("evaluated_y1");
        opened = shell.ticks;
        egg = game.flags.contains("robo_member");
        check(game.totalEarned >= 50000, "RINGS EARNED: " + game.totalEarned);
        check(game.population >= 30, "ANIMALS HOME: " + game.population);
        Capsule capsule = game.section(Capsule.class);
        boolean restored = capsule != null && capsule.restored(shell.catalog);
        check(restored, egg ? "THE CAPSULE: SEALED BY ROBOMART" : restored ? "THE GREAT CAPSULE RESTORED" : "THE GREAT CAPSULE: NOT YET");
        int tilled = 0;
        for (int r = 0; r < starpost.core.Farm.ROWS; r++) {
            for (int c = 0; c < starpost.core.Farm.COLUMNS; c++) {
                if (game.farm.raw(r, c).crop != null || game.farm.raw(r, c).object != null) {
                    tilled++;
                }
            }
        }
        check(tilled >= 60, "A LIVING FARM: " + tilled + " PLOTS");
        if (egg) {
            score = 0;
        }
        shell.music.want("s1", Music.S1_GOT_THROUGH);
        shell.sfx(Sfx.SIGNPOST);
        shell.save();
    }

    private void check(boolean done, String line) {
        if (done) {
            score++;
        }
        lines.add((done ? "* " : "  ") + line);
    }

    @Override
    public void update(Shell shell) {
        if (shell.ticks - opened > 300 && (shell.in.confirm || shell.in.act)) {
            shell.go(new CreditsScreen(score, egg, next));
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        int w = canvas.width();
        long age = shell.ticks - opened;
        canvas.drawBackdrop(shell.art.season(0).backdrop(1), 0, 0, w, canvas.height(), 8, age * 0.4, shell.ticks);
        canvas.fill(0, 0, w, canvas.height(), 0x40000010);
        shell.art.cardFont.centred(canvas, "THE SIGNPOST SPIN", 14, SceneDraw.plain());
        // Map_Sign: 0 Robotnik, 1-3 turning, 4 the hero. Spin fast, slow, then settle by the score.
        int frame;
        if (age < 150) {
            frame = (int) (age / Math.max(2, 2 + age / 25) % 4);
        } else {
            frame = score >= 4 ? 4 : 0;
        }
        SceneSprite sign = shell.art.signpost.frame(Math.min(frame, shell.art.signpost.frameCount() - 1));
        canvas.draw(sign, w / 2f, 84, SceneDraw.plain());
        for (int i = 0; i < lines.size(); i++) {
            if (age > 60 + i * 30) {
                Text.centred(canvas, lines.get(i), 112 + i * 14, lines.get(i).startsWith("*") ? Text.GREEN : Text.GREY);
            }
        }
        if (age > 180) {
            String verdict = egg ? "ROBOTNIK OWNS THE VALLEY NOW." : switch (score) {
                case 4 -> "A VALLEY TO COME HOME TO!";
                case 3 -> "ALMOST THERE. THE TOTEM SMILES.";
                case 2 -> "A GOOD START. KEEP GOING.";
                default -> "THE VALLEY IS STILL WAITING.";
            };
            Text.centred(canvas, verdict, 176, egg ? Text.RED : Text.YELLOW);
        }
    }
}
