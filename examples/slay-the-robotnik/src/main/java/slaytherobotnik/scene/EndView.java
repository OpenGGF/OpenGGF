package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import slaytherobotnik.core.RunStats;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;

/** Game over, or victory: the run summary, then back to the title. */
final class EndView implements RunScreen.RoomView {
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
        if (age > 60 && (shell.in.accept || shell.in.back || shell.in.mouse.leftPressed())) {
            shell.run = null;
            shell.go(new TitleScreen());
        }
    }

    @Override
    public void draw(Shell shell, RunScreen screen, SceneCanvas c) {
        int w = shell.width();
        int h = shell.height();
        if (victory) {
            Backdrops.menu(c, w, h, shell.ticks);
        } else {
            Gfx.gradient(c, 0, 0, w, h, 0xFF200000, 0xFF000000);
        }
        var f = shell.font;
        String title = victory ? "ROBOTNIK SLAIN!" : "GAME OVER";
        f.drawOutlined(c, title, (w - f.width(title) * 3) / 2, 26, victory ? Colors.GOLD : Colors.TEXT_BAD, 3);
        Poses.hero(shell, c, shell.run.state().character().id(), victory ? Poses.VICTORY : Poses.DEATH, age,
                w / 2f, 104, SceneDraw.plain());
        if (!victory && killedBy != null) {
            String line = "DEFEATED BY " + killedBy.toUpperCase();
            f.drawShadowed(c, line, (w - f.width(line)) / 2, 108, Colors.TEXT);
        }
        RunStats s = shell.run.state().stats();
        Gfx.panel(c, w / 2 - 90, 120, 180, 76);
        String[][] rows = {
                {"FLOORS CLIMBED", Integer.toString(shell.run.state().floor())},
                {"ENEMIES DEFEATED", Integer.toString(s.enemiesDefeated)},
                {"ELITES DEFEATED", Integer.toString(s.elitesDefeated)},
                {"BOSSES DEFEATED", Integer.toString(s.bossesDefeated)},
                {"RINGS COLLECTED", Integer.toString(s.ringsCollected)},
                {"SCORE", Integer.toString(score)},
        };
        int y = 126;
        for (String[] row : rows) {
            f.drawShadowed(c, row[0], w / 2 - 82, y, Colors.TEXT_DIM);
            f.drawShadowed(c, row[1], w / 2 + 82 - f.width(row[1]), y, row[0].equals("SCORE") ? Colors.GOLD : Colors.TEXT);
            y += 11;
        }
        if (age > 60) {
            String hint = "PRESS ANY BUTTON";
            f.drawShadowed(c, hint, (w - f.width(hint)) / 2, 206, Colors.TEXT_DIM);
        }
    }
}
