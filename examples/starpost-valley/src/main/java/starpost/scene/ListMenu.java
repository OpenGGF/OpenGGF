package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import java.util.List;
import starpost.core.Item;
import starpost.ui.Text;

/** A scrolling list of items with a cursor: the base of the shop and shipping menus. */
abstract class ListMenu implements Screen {
    protected static final int ROWS = 7;
    protected int cursor;
    protected int top;

    protected abstract String title(Shell shell);

    protected abstract List<Item> rows(Shell shell);

    protected abstract String right(Shell shell, Item item);

    protected abstract void choose(Shell shell, Item item);

    protected String footer(Shell shell) {
        return "";
    }

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void update(Shell shell) {
        List<Item> rows = rows(shell);
        if (shell.in.back || shell.in.menu && !shell.in.confirm) {
            shell.pop();
            return;
        }
        if (rows.isEmpty()) {
            return;
        }
        if (shell.in.downPressed) {
            cursor = (cursor + 1) % rows.size();
            shell.sfx(Sfx.SWITCH);
        } else if (shell.in.upPressed) {
            cursor = (cursor + rows.size() - 1) % rows.size();
            shell.sfx(Sfx.SWITCH);
        }
        cursor = Math.min(cursor, rows.size() - 1);
        top = Math.max(0, Math.min(top, cursor));
        if (cursor >= top + ROWS) {
            top = cursor - ROWS + 1;
        }
        if (shell.in.confirm) {
            choose(shell, rows.get(cursor));
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        int w = 300, h = 34 + ROWS * 20 + 18, x = (canvas.width() - w) / 2, y = 22;
        Text.panel(canvas, x, y, w, h);
        Text.shadow(canvas, title(shell), x + 10, y + 8, Text.YELLOW);
        Text.right(canvas, shell.game.rings + " RINGS", x + w - 10, y + 8, Text.WHITE);
        List<Item> rows = rows(shell);
        if (rows.isEmpty()) {
            Text.centred(canvas, "NOTHING HERE", y + 60, Text.GREY);
        }
        for (int i = top; i < Math.min(rows.size(), top + ROWS); i++) {
            Item item = rows.get(i);
            int ry = y + 26 + (i - top) * 20;
            if (i == cursor) {
                canvas.fill(x + 6, ry - 2, w - 12, 19, 0x60B66D24);
            }
            shell.art.icons.draw(canvas, item, x + 10, ry, SceneDraw.plain());
            Text.shadow(canvas, item.name(), x + 32, ry + 4, i == cursor ? Text.YELLOW : Text.WHITE);
            Text.right(canvas, right(shell, item), x + w - 10, ry + 4, Text.WHITE);
        }
        if (!rows.isEmpty()) {
            Item item = rows.get(Math.min(cursor, rows.size() - 1));
            Text.shadow(canvas, item.text(), x + 10, y + h - 30, Text.GREY);
        }
        Text.shadow(canvas, footer(shell), x + 10, y + h - 16, Text.BLUE);
    }
}
