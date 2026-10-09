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

    /** The question (wrapped to two lines when long) over YES and NOT YET, on a solid panel. */
    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        java.util.List<String> rows = Text.wrap(canvas, question, 336);
        int widest = 0;
        for (String row : rows) {
            widest = Math.max(widest, canvas.textWidth(row));
        }
        int w = Math.max(220, widest + 32), h = 40 + rows.size() * 12;
        int x = (canvas.width() - w) / 2, y = 70;
        FestivalScreen.solidPanel(canvas, x, y, w, h);
        for (int i = 0; i < rows.size(); i++) {
            Text.centred(canvas, rows.get(i), y + 10 + i * 12, Text.WHITE);
        }
        int cx = canvas.width() / 2, oy = y + h - 22;
        Text.shadow(canvas, choiceYes ? "> YES" : "  YES", cx - 70, oy, choiceYes ? Text.YELLOW : Text.GREY);
        Text.shadow(canvas, choiceYes ? "  NOT YET" : "> NOT YET", cx + 10, oy, choiceYes ? Text.GREY : Text.YELLOW);
    }
}
