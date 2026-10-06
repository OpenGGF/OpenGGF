package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.core.RunStats;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Ease;
import slaytherobotnik.ui.Gfx;

/**
 * Game over, or victory, staged on the act's own level, then the run summary and back to the
 * title.
 *
 * <p>Game over plays out as in the games: the hero is knocked up and falls away (Kill_Character
 * sets y_vel to -$700; ObjectMoveAndFall adds $38 a frame), then Obj_GameOver's two words slide
 * in from either side at $10 pixels a frame until they meet in the middle (they start $D0 either
 * side of it). Victory runs the hero in to strike the victory pose under a shower of rings, with
 * the summary beside them.
 */
final class EndView implements RunScreen.RoomView {
    /** The row the stage's floor sits on. */
    private static final int GROUND = 150;
    /** Kill_Character's launch and ObjectMoveAndFall's gravity, in pixels (8.8 fixed point / 256). */
    private static final float DEATH_LAUNCH = -0x700 / 256f;
    private static final float GRAVITY = 0x38 / 256f;
    /** Obj_GameOver: $10 a frame, from $50 and $1F0 to $120. */
    private static final int WORD_SPEED = 0x10;
    private static final int WORD_TRAVEL = 0x120 - 0x50;
    /** Frames before the words start (the hero's fall), and before the summary rises in. */
    private static final int WORDS_AT = 44;
    private static final int SUMMARY_AT = WORDS_AT + WORD_TRAVEL / WORD_SPEED + 16;
    private static final int SUMMARY_RISE = 20;
    /** Victory: frames the hero takes to run in, and when the summary slides in beside them. */
    private static final int RUN_IN = 28;
    private static final int VICTORY_SUMMARY_AT = 40;
    private final boolean victory;
    private final String killedBy;
    private final int score;
    private int age;

    EndView(Shell shell, String killedBy, int score, boolean victory) {
        this.victory = victory;
        this.killedBy = killedBy;
        this.score = score;
        String id = shell.run.state().character().id();
        shell.profile.max("floor." + id, shell.run.state().floor());
        shell.profile.max("score", score);
        if (victory) {
            shell.profile.add("wins." + id, 1);
        }
        shell.profile.save(shell.ctx.storage());
        shell.deleteRun();
    }

    @Override
    public boolean showsHud() {
        return false;
    }

    @Override
    public void update(Shell shell, RunScreen screen) {
        age++;
        if (age > SUMMARY_AT + SUMMARY_RISE && (shell.in.accept || shell.in.back || shell.in.mouse.leftPressed())) {
            shell.run = null;
            shell.go(new TitleScreen());
        }
    }

    @Override
    public void draw(Shell shell, RunScreen screen, SceneCanvas c) {
        int w = shell.width();
        int h = shell.height();
        LevelStages.Placement stage = LevelStages.draw(shell, c, GROUND);
        c.fill(0, 0, w, h, victory ? 0x60000820 : 0xA0100008);
        var f = shell.font;
        String hero = shell.run.state().character().id();
        // Victory leaves the right side for the summary; game over centres everything.
        int heroX = victory ? w * 27 / 100 : w / 2;
        if (victory) {
            drawRingShower(shell, c, w, h);
            if (age < RUN_IN) {
                // Running in from the left edge, slowing to a stop on the spot.
                int x = Math.round(-24 + (heroX + 24) * Ease.outCubic(age / (float) RUN_IN));
                Poses.hero(shell, c, hero, Poses.RUN, age, x, LevelStages.feet(stage, x, GROUND), SceneDraw.plain());
            } else {
                Poses.hero(shell, c, hero, Poses.VICTORY, age - RUN_IN, heroX, LevelStages.feet(stage, heroX, GROUND),
                        SceneDraw.plain());
            }
            String title = "ROBOTNIK SLAIN!";
            float drop = Math.min(1f, age / 30f);
            f.drawOutlined(c, title, (w - f.width(title) * 3) / 2, Math.round(-30 + 54 * Ease.outBack(drop)),
                    Colors.GOLD, 3);
        } else {
            // The hero is knocked up and falls away, as when a life is lost.
            int feet = LevelStages.feet(stage, heroX, GROUND);
            float y = feet + DEATH_LAUNCH * age + GRAVITY * age * (age - 1) / 2f;
            if (y < h + 48) {
                Poses.hero(shell, c, hero, Poses.DEATH, age, heroX, y, SceneDraw.plain());
            }
            drawGameOverWords(shell, c, w);
            if (killedBy != null && age > SUMMARY_AT - 8) {
                String line = "DEFEATED BY " + killedBy.toUpperCase();
                f.drawShadowed(c, line, (w - f.width(line)) / 2, 66, Colors.alpha(Colors.TEXT,
                        Math.min(1f, (age - SUMMARY_AT + 8) / 12f)));
            }
        }
        drawSummary(shell, c, w);
    }

    /** Obj_GameOver's GAME and OVER sliding in from either side to meet in the middle. */
    private void drawGameOverWords(Shell shell, SceneCanvas c, int w) {
        int t = age - WORDS_AT;
        if (t < 0) {
            return;
        }
        int travel = Math.min(WORD_TRAVEL, t * WORD_SPEED);
        SceneSprite game = shell.art.romFrame("game_over", 0);
        SceneSprite over = shell.art.romFrame("game_over", 1);
        if (game == null || over == null) {
            String text = "GAME OVER";
            shell.font.drawOutlined(c, text, (w - shell.font.width(text) * 3) / 2, 36, Colors.TEXT_BAD, 3);
            return;
        }
        int y = 44;
        c.draw(game, w / 2f - WORD_TRAVEL + travel, y, SceneDraw.plain());
        c.draw(over, w / 2f + WORD_TRAVEL - travel, y, SceneDraw.plain());
    }

    /**
     * The run's numbers once the scene has played: rising in under GAME OVER, or sliding in from
     * the right beside the victorious hero.
     */
    private void drawSummary(Shell shell, SceneCanvas c, int w) {
        int start = victory ? VICTORY_SUMMARY_AT : SUMMARY_AT;
        float in = Math.min(1f, Math.max(0f, (age - start) / (float) SUMMARY_RISE));
        if (in <= 0f) {
            return;
        }
        var f = shell.font;
        float slide = 1f - Ease.outCubic(in);
        int cx = victory ? w * 68 / 100 + Math.round(slide * (w * 32 / 100 + 90)) : w / 2;
        int top = victory ? 84 : Math.round(118 + slide * 40);
        RunStats s = shell.run.state().stats();
        Gfx.panel(c, cx - 90, top, 180, 76);
        String[][] rows = {
                {"FLOORS CLIMBED", Integer.toString(shell.run.state().floor())},
                {"ENEMIES DEFEATED", Integer.toString(s.enemiesDefeated)},
                {"ELITES DEFEATED", Integer.toString(s.elitesDefeated)},
                {"BOSSES DEFEATED", Integer.toString(s.bossesDefeated)},
                {"RINGS COLLECTED", Integer.toString(s.ringsCollected)},
                {"SCORE", Integer.toString(score)},
        };
        int y = top + 6;
        for (String[] row : rows) {
            f.drawShadowed(c, row[0], cx - 82, y, Colors.TEXT_DIM);
            f.drawShadowed(c, row[1], cx + 82 - f.width(row[1]), y,
                    row[0].equals("SCORE") ? Colors.GOLD : Colors.TEXT);
            y += 11;
        }
        if (age > start + SUMMARY_RISE && (age / 30) % 2 == 0) {
            String hint = "PRESS ANY BUTTON";
            f.drawShadowed(c, hint, cx - f.width(hint) / 2, victory ? top + 82 : 206, Colors.TEXT_DIM);
        }
    }

    /** Rings tumbling down the screen, spinning, for the win. */
    private void drawRingShower(Shell shell, SceneCanvas c, int w, int h) {
        for (int i = 0; i < 24; i++) {
            int speed = 1 + i % 3;
            int x = (i * 97 + 13) % w;
            int y = (int) ((age * speed + i * 53) % (h + 40)) - 20;
            HudIcons.ring(shell, c, x, y, 12, age + i * 3L);
        }
    }
}
