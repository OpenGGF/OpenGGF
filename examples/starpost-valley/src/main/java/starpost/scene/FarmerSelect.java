package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import starpost.art.Anim;
import starpost.core.Game;
import starpost.ui.Text;

/** Choose the farmer, in the Sonic 3 &amp; Knuckles way; the other two become neighbours. */
final class FarmerSelect implements Screen {
    private int cursor;
    private final Anim wait = new Anim();

    private static String code(int i) {
        return i == 0 ? "sonic" : i == 1 ? "tails" : "knuckles";
    }

    private static String perk(int i) {
        return switch (i) {
            case 0 -> "FASTEST. SPIN DASH TILLS A WHOLE ROW.";
            case 1 -> "FLIES OVER CLIFFS. CARRIES MORE.";
            default -> "DIGS UP BURIED FINDS. PUNCHES ROCKS.";
        };
    }

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void update(Shell shell) {
        wait.set(Anim.WAIT, 6);
        wait.tick();
        if (shell.in.rightPressed) {
            cursor = (cursor + 1) % 3;
            shell.sfx(Sfx.SWITCH);
        } else if (shell.in.leftPressed) {
            cursor = (cursor + 2) % 3;
            shell.sfx(Sfx.SWITCH);
        } else if (shell.in.back) {
            shell.pop();
        } else if (shell.in.confirm) {
            shell.game = shell.newGame(System.nanoTime(), code(cursor));
            shell.sfx(Sfx.STARPOST);
            shell.go(new IntroScreen());
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        int w = canvas.width();
        Text.panel(canvas, 30, 40, w - 60, 140);
        Text.centred(canvas, "WHO WILL FARM THE VALLEY?", 50, Text.YELLOW);
        for (int i = 0; i < 3; i++) {
            int cx = w / 2 + (i - 1) * 100;
            SceneSprite pose = wait.pose(shell.art.farmer(code(i)));
            SceneDraw style = i == cursor ? SceneDraw.plain() : SceneDraw.plain().withTint(0xFF808080);
            canvas.draw(pose, cx, 120 - (pose.height() - pose.originY()), style);
            String name = code(i).toUpperCase();
            Text.shadow(canvas, name, cx - canvas.textWidth(name) / 2, 128, i == cursor ? Text.YELLOW : Text.GREY);
        }
        Text.centred(canvas, perk(cursor), 150, Text.WHITE);
        Text.centred(canvas, "LEFT/RIGHT CHOOSE   CONFIRM START", 164, Text.GREY);
    }
}
