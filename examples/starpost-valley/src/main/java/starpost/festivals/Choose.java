package starpost.festivals;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import java.util.List;
import java.util.function.IntConsumer;
import starpost.core.Item;
import starpost.scene.Screen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.ui.Text;

/**
 * A short list to choose from over the world (an entry for the judging, a gift): up and down,
 * jump to choose; back chooses nothing (-1). Each row may show an item's icon.
 */
final class Choose implements Screen {
    private static final int ROWS = 7;
    private final String title;
    private final List<String> labels;
    private final List<Item> icons;
    private final IntConsumer done;
    private int cursor;
    private int top;

    Choose(String title, List<String> labels, List<Item> icons, IntConsumer done) {
        this.title = title;
        this.labels = labels;
        this.icons = icons;
        this.done = done;
    }

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void update(Shell shell) {
        if (shell.in.back) {
            shell.pop();
            done.accept(-1);
            return;
        }
        if (shell.in.downPressed) {
            cursor = (cursor + 1) % labels.size();
            shell.sfx(Sfx.SWITCH);
        } else if (shell.in.upPressed) {
            cursor = (cursor + labels.size() - 1) % labels.size();
            shell.sfx(Sfx.SWITCH);
        }
        top = Math.max(0, Math.min(top, cursor));
        if (cursor >= top + ROWS) {
            top = cursor - ROWS + 1;
        }
        if (shell.in.confirm) {
            shell.pop();
            done.accept(cursor);
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        int w = Math.min(380, Math.max(260, canvas.textWidth(title) + 24));
        int rows = Math.min(ROWS, labels.size()), h = 34 + rows * 20 + 6;
        int x = (canvas.width() - w) / 2, y = (canvas.height() - h) / 2;
        FestivalScreen.solidPanel(canvas, x, y, w, h);
        Text.shadow(canvas, title, x + 10, y + 8, Text.YELLOW);
        for (int i = top; i < Math.min(labels.size(), top + ROWS); i++) {
            int ry = y + 28 + (i - top) * 20;
            if (i == cursor) {
                canvas.fill(x + 6, ry - 2, w - 12, 19, 0x60B66D24);
            }
            Item icon = icons == null || i >= icons.size() ? null : icons.get(i);
            if (icon != null) {
                shell.art.icons.draw(canvas, icon, x + 10, ry, SceneDraw.plain());
            }
            Text.shadow(canvas, Text.fit(canvas, labels.get(i), w - 50), x + 32, ry + 4,
                    i == cursor ? Text.YELLOW : Text.WHITE);
        }
    }
}
