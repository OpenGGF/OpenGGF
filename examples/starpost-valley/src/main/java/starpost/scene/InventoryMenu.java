package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import starpost.core.Calendar;
import starpost.core.Game;
import starpost.core.Inventory;
import starpost.core.Item;
import starpost.ui.Text;

/**
 * The monitor slots: move the cursor, confirm to pick a stack up and confirm again to put it
 * down (swapping), or confirm on food to eat it. The first row is the hotbar.
 */
final class InventoryMenu implements Screen {
    private static final int COLUMNS = 12;
    private int cursor;
    private int held = -1;

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void enter(Shell shell) {
        cursor = shell.game.inventory.selected();
    }

    @Override
    public void update(Shell shell) {
        Inventory inv = shell.game.inventory;
        if (shell.in.back || shell.in.menu && !shell.in.confirm) {
            shell.pop();
            return;
        }
        int size = inv.size();
        if (shell.in.rightPressed) {
            cursor = (cursor + 1) % size;
        } else if (shell.in.leftPressed) {
            cursor = (cursor + size - 1) % size;
        } else if (shell.in.downPressed) {
            cursor = (cursor + COLUMNS) % size;
        } else if (shell.in.upPressed) {
            cursor = (cursor + size - COLUMNS) % size;
        }
        if (!shell.in.confirm) {
            return;
        }
        Game game = shell.game;
        if (held >= 0) {
            inv.swap(held, cursor);
            held = -1;
            shell.sfx(Sfx.SWITCH);
        } else if (inv.id(cursor) != null) {
            Item item = game.item(inv.id(cursor));
            if (item.edible()) {
                if (game.momentum >= game.maxMomentum) {
                    shell.toast("MOMENTUM IS ALREADY FULL");
                    shell.sfx(Sfx.ERROR);
                } else {
                    game.restore(item.momentum());
                    inv.useOne(cursor);
                    shell.sfx(Sfx.RING);
                    shell.toast("ATE " + item.name() + " +" + item.momentum());
                }
            } else {
                held = cursor;
                shell.sfx(Sfx.SWITCH);
            }
        }
        if (cursor < Inventory.HOTBAR) {
            inv.select(cursor);
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        Game game = shell.game;
        Inventory inv = game.inventory;
        int rows = (inv.size() + COLUMNS - 1) / COLUMNS;
        int slot = 20, w = COLUMNS * slot + 20, h = 60 + rows * slot + 30;
        int x = (canvas.width() - w) / 2, y = 30;
        Text.panel(canvas, x, y, w, h);
        Text.shadow(canvas, game.farmName + " FARM", x + 10, y + 8, Text.YELLOW);
        Text.right(canvas, Calendar.seasonName(game.calendar.season()) + " " + game.calendar.day() + "  YEAR "
                + game.calendar.year(), x + w - 10, y + 8, Text.WHITE);
        Text.shadow(canvas, "RINGS " + game.rings + "   MOMENTUM " + game.momentum + "/" + game.maxMomentum,
                x + 10, y + 22, Text.WHITE);
        for (int i = 0; i < inv.size(); i++) {
            int sx = x + 10 + (i % COLUMNS) * slot, sy = y + 40 + (i / COLUMNS) * slot;
            boolean sel = i == cursor;
            canvas.fill(sx, sy, 18, 18, sel ? 0xFFFFDB00 : i == held ? 0xFF92FF49 : 0xFF203060);
            canvas.fill(sx + 1, sy + 1, 16, 16, 0xFF101838);
            if (inv.id(i) != null) {
                shell.art.icons.draw(canvas, game.item(inv.id(i)), sx + 1, sy + 1, SceneDraw.plain());
                if (inv.count(i) > 1) {
                    String n = Integer.toString(inv.count(i));
                    canvas.text(n, sx + 18 - canvas.textWidth(n), sy + 10, Text.WHITE);
                }
            }
        }
        if (inv.id(cursor) != null) {
            Item item = game.item(inv.id(cursor));
            int ty = y + 44 + rows * slot;
            Text.shadow(canvas, item.name(), x + 10, ty, Text.YELLOW);
            Text.shadow(canvas, item.text(), x + 10, ty + 12, Text.GREY);
        }
    }
}
