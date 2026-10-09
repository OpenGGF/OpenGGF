package starpost.festivals;

import com.openggf.mods.scene.SceneCanvas;
import starpost.scene.Screen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.ui.Text;

/** A yes/not-yet question over the world (joining a festival, taking on a request). */
final class Ask implements Screen {
    private final String question;
    private final Runnable yes;
    private boolean choiceYes = true;

    Ask(String question, Runnable yes) {
        this.question = question;
        this.yes = yes;
    }

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void update(Shell shell) {
        if (shell.in.leftPressed || shell.in.rightPressed || shell.in.upPressed || shell.in.downPressed) {
            choiceYes = !choiceYes;
            shell.sfx(Sfx.SWITCH);
        } else if (shell.in.confirm) {
            shell.pop();
            if (choiceYes) {
                yes.run();
            }
        } else if (shell.in.back) {
            shell.pop();
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        int w = Math.max(220, canvas.textWidth(question) + 32), h = 52;
        int x = (canvas.width() - w) / 2, y = 70;
        Text.panel(canvas, x, y, w, h);
        Text.centred(canvas, question, y + 10, Text.WHITE);
        int cx = canvas.width() / 2;
        Text.shadow(canvas, choiceYes ? "> YES" : "  YES", cx - 70, y + 30, choiceYes ? Text.YELLOW : Text.GREY);
        Text.shadow(canvas, choiceYes ? "  NOT YET" : "> NOT YET", cx + 10, y + 30, choiceYes ? Text.GREY : Text.YELLOW);
    }
}
