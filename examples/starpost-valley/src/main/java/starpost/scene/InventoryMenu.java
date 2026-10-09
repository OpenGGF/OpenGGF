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
public final class InventoryMenu implements Screen {
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
        if (shell.ctx.keyPressed(com.openggf.mods.scene.SceneKeys.O)) {
            shell.push(new OptionsMenu(true));
            return;
        }
        if (shell.in.nextTool || shell.in.upPressed && cursor < COLUMNS) {
            // The neighbours' page sits "above" the monitor slots (starpost.people).
            shell.pop();
            shell.push(new starpost.people.SocialPage(() -> shell.push(new InventoryMenu())));
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
                    if (item.id().equals("fire_pepper")) {
                        game.flags.add(starpost.ruins.RuinsSection.LAVA_FLAG); // lava immunity until morning
                    }
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
        int slot = 20, w = 340, h = 38 + rows * slot + 74;
        int x = (canvas.width() - w) / 2, y = Math.max(6, (canvas.height() - h) / 2 - 8);
        int gx = x + (w - COLUMNS * slot) / 2;
        Text.panel(canvas, x, y, w, h);
        Text.shadow(canvas, game.farmName + " FARM", x + 10, y + 8, Text.YELLOW);
        Text.right(canvas, Calendar.seasonName(game.calendar.season()) + " " + game.calendar.day() + "  YEAR "
                + game.calendar.year(), x + w - 10, y + 8, Text.WHITE);
        com.openggf.mods.ui.CompactFont.shadowed(canvas, "RINGS " + game.rings + "   MOMENTUM " + game.momentum + "/"
                + game.maxMomentum, x + 10, y + 22, 1, 0xFFFFFFFF, 0xFF000000);
        com.openggf.mods.ui.CompactFont.shadowed(canvas, "VALLEY: " + game.population + " ANIMALS",
                x + w - 10 - com.openggf.mods.ui.CompactFont.width("VALLEY: " + game.population + " ANIMALS", 1), y + 22, 1,
                0xFF92FF49, 0xFF000000);
        for (int i = 0; i < inv.size(); i++) {
            int sx = gx + (i % COLUMNS) * slot, sy = y + 34 + (i / COLUMNS) * slot;
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
        int ty = y + 38 + rows * slot;
        if (inv.id(cursor) != null) {
            Item item = game.item(inv.id(cursor));
            Text.shadow(canvas, item.name(), x + 10, ty, Text.YELLOW);
            Text.note(canvas, item.text(), x + 10, ty + 12, w - 20, Text.GREY);
        }
        starpost.core.Skills skills = game.section(starpost.core.Skills.class);
        if (skills != null) {
            int sy = ty + 28;
            for (int s = 0; s < starpost.core.Skills.COUNT; s++) {
                int sx = x + 10 + s * 64;
                String[] shortNames = {"FARM", "RANGE", "FISH", "SCRAP", "BOP"};
                com.openggf.mods.ui.CompactFont.shadowed(canvas, shortNames[s] + " " + skills.level(s), sx, sy,
                        1, 0xFFFFDB00, 0xFF000000);
                canvas.fill(sx, sy + 9, 58, 3, 0xFF203060);
                canvas.fill(sx, sy + 9, Math.round(58 * skills.progress(s)), 3, 0xFF24B6FF);
            }
        }
        com.openggf.mods.ui.CompactFont.shadowed(canvas, "UP/E: NEIGHBOURS    O: OPTIONS    BACK: CLOSE", x + 10, y + h - 12,
                1, 0xFF92DBFF, 0xFF000000);
    }
}
