package starpost.ruins;

import com.openggf.mods.scene.SceneCanvas;
import java.util.function.IntConsumer;
import starpost.scene.Screen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.ui.Text;

/**
 * A short list of choices over the Ruins or the valley: the elevator's floors, leaving, riding up.
 * Confirm picks; back closes it and answers -1.
 */
final class RuinsMenu implements Screen {
    private final String title;
    private final String[] options;
    private final IntConsumer chosen;
    private int cursor;

    RuinsMenu(String title, String[] options, IntConsumer chosen) {
        this.title = title;
        this.options = options;
        this.chosen = chosen;
    }

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void update(Shell shell) {
        if (shell.in.downPressed) {
            cursor = (cursor + 1) % options.length;
            shell.sfx(Sfx.SWITCH);
        } else if (shell.in.upPressed) {
            cursor = (cursor + options.length - 1) % options.length;
            shell.sfx(Sfx.SWITCH);
        } else if (shell.in.confirm) {
            shell.pop();
            chosen.accept(cursor);
        } else if (shell.in.back || shell.in.menu) {
            shell.pop();
            chosen.accept(-1);
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        int w = canvas.textWidth(title) + 40;
        for (String option : options) {
            w = Math.max(w, canvas.textWidth(option) + 56);
        }
        int h = 34 + options.length * 14;
        int x = (canvas.width() - w) / 2, y = Math.max(30, (canvas.height() - h) / 2 - 10);
        Text.panel(canvas, x, y, w, h);
        Text.centred(canvas, title, y + 9, Text.YELLOW);
        for (int i = 0; i < options.length; i++) {
            int ty = y + 27 + i * 14;
            boolean sel = i == cursor;
            Text.shadow(canvas, (sel ? "> " : "  ") + options[i], x + 16, ty, sel ? Text.YELLOW : Text.WHITE);
        }
    }
}
