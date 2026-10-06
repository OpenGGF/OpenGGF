package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import slaytherobotnik.core.CharacterDef;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;

/** Per-character records from the profile. */
final class RecordsScreen implements Screen {
    @Override
    public void update(Shell shell) {
        if (shell.in.back || shell.in.accept || shell.in.mouse.leftPressed()) {
            shell.sfx(Sounds.SFX_SWITCH);
            shell.go(new TitleScreen());
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas c) {
        Backdrops.menu(c, shell.width(), shell.height(), shell.ticks);
        var f = shell.font;
        f.drawOutlined(c, "RECORDS", (shell.width() - f.width("RECORDS") * 2) / 2, 12, Colors.GOLD, 2);
        int x = shell.width() / 2 - 140;
        Gfx.panel(c, x, 36, 280, 140);
        int y = 46;
        f.drawShadowed(c, "HERO", x + 10, y, Colors.TEXT_DIM);
        f.drawShadowed(c, "RUNS", x + 90, y, Colors.TEXT_DIM);
        f.drawShadowed(c, "WINS", x + 140, y, Colors.TEXT_DIM);
        f.drawShadowed(c, "BEST FLOOR", x + 190, y, Colors.TEXT_DIM);
        y += 14;
        for (CharacterDef def : shell.catalog.characters()) {
            f.drawShadowed(c, def.name().toUpperCase(), x + 10, y, Colors.TEXT);
            f.drawShadowed(c, Integer.toString(shell.profile.get("runs." + def.id())), x + 90, y, Colors.TEXT);
            f.drawShadowed(c, Integer.toString(shell.profile.get("wins." + def.id())), x + 140, y, Colors.GOLD);
            f.drawShadowed(c, Integer.toString(shell.profile.get("floor." + def.id())), x + 190, y, Colors.TEXT);
            y += 12;
        }
        y += 10;
        f.drawShadowed(c, "HIGH SCORE  " + shell.profile.get("score"), x + 10, y, Colors.GOLD);
        String hint = "PRESS ANY BUTTON";
        f.drawShadowed(c, hint, (shell.width() - f.width(hint)) / 2, 190, Colors.TEXT_DIM);
    }
}
