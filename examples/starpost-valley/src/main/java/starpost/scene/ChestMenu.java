package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import starpost.core.Game;
import starpost.core.Inventory;
import starpost.core.Item;
import starpost.ui.Text;

/** An opened Item Monitor: two grids, the monitor's and the farmer's; confirm moves a stack across. */
final class ChestMenu implements Screen {
    private static final int COLUMNS = 12;
    private final Inventory chest;
    private boolean inChest = true;
    private int cursor;

    ChestMenu(Inventory chest) {
        this.chest = chest;
    }

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void update(Shell shell) {
        Game game = shell.game;
        if (shell.in.back || shell.in.menu && !shell.in.confirm) {
            shell.pop();
            return;
        }
        Inventory here = inChest ? chest : game.inventory;
        int size = here.size();
        if (shell.in.rightPressed) {
            cursor = (cursor + 1) % size;
        } else if (shell.in.leftPressed) {
            cursor = (cursor + size - 1) % size;
        } else if (shell.in.downPressed) {
            if (inChest && cursor + COLUMNS >= size) {
                inChest = false;
                cursor = cursor % COLUMNS;
            } else if (!inChest && cursor + COLUMNS >= size) {
                inChest = true;
                cursor = cursor % COLUMNS;
            } else {
                cursor += COLUMNS;
            }
        } else if (shell.in.upPressed) {
            if (cursor < COLUMNS) {
                inChest = !inChest;
                Inventory other = inChest ? chest : game.inventory;
                cursor = Math.min(other.size() - 1, other.size() - COLUMNS + cursor);
            } else {
                cursor -= COLUMNS;
            }
        }
        if (shell.in.confirm && here.id(cursor) != null) {
            Inventory there = inChest ? game.inventory : chest;
            Item item = game.item(here.id(cursor));
            int count = here.count(cursor);
            int left = there.add(item, count);
            here.set(cursor, item.id(), left);
            shell.sfx(left == count ? Sfx.ERROR : Sfx.SWITCH);
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        int w = COLUMNS * 20 + 20, x = (canvas.width() - w) / 2;
        int chestRows = (chest.size() + COLUMNS - 1) / COLUMNS;
        int invRows = (shell.game.inventory.size() + COLUMNS - 1) / COLUMNS;
        int h = 40 + chestRows * 20 + 20 + invRows * 20 + 16;
        int y = Math.max(8, (canvas.height() - h) / 2 - 10);
        Text.panel(canvas, x, y, w, h);
        Text.shadow(canvas, "ITEM MONITOR", x + 10, y + 8, Text.YELLOW);
        grid(shell, canvas, chest, x + 10, y + 22, inChest);
        int iy = y + 22 + chestRows * 20 + 8;
        Text.shadow(canvas, "CARRYING", x + 10, iy, Text.YELLOW);
        grid(shell, canvas, shell.game.inventory, x + 10, iy + 12, !inChest);
    }

    private void grid(Shell shell, SceneCanvas canvas, Inventory inv, int x, int y, boolean active) {
        for (int i = 0; i < inv.size(); i++) {
            int sx = x + (i % COLUMNS) * 20, sy = y + (i / COLUMNS) * 20;
            boolean sel = active && i == cursor;
            canvas.fill(sx, sy, 18, 18, sel ? 0xFFFFDB00 : 0xFF203060);
            canvas.fill(sx + 1, sy + 1, 16, 16, 0xFF101838);
            if (inv.id(i) != null) {
                shell.art.icons.draw(canvas, shell.game.item(inv.id(i)), sx + 1, sy + 1, SceneDraw.plain());
                if (inv.count(i) > 1) {
                    String n = Integer.toString(inv.count(i));
                    canvas.text(n, sx + 18 - canvas.textWidth(n), sy + 10, Text.WHITE);
                }
            }
        }
    }
}
