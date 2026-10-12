package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import starpost.ui.Text;

/** A yes/no question over the game. */
final class ConfirmMenu implements Screen {
    private final String question;
    private final Runnable yes;
    private boolean choiceYes = true;

    ConfirmMenu(String question, Runnable yes) {
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
        int w = Math.max(200, canvas.textWidth(question) + 32), h = 52;
        int x = (canvas.width() - w) / 2, y = 70;
        Text.panel(canvas, x, y, w, h);
        Text.centred(canvas, question, y + 10, Text.WHITE);
        String yes = choiceYes ? "> YES" : "  YES", no = choiceYes ? "  NO" : "> NO";
        Text.shadow(canvas, yes, canvas.width() / 2 - 60, y + 30, choiceYes ? Text.YELLOW : Text.GREY);
        Text.shadow(canvas, no, canvas.width() / 2 + 20, y + 30, choiceYes ? Text.GREY : Text.YELLOW);
    }
}
