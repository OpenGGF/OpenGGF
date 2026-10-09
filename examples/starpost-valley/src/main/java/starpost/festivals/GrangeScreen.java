package starpost.festivals;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import java.util.ArrayList;
import java.util.List;
import starpost.core.Game;
import starpost.core.Item;
import starpost.core.Kind;
import starpost.scene.Screen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.ui.Text;

/**
 * The grange display at the Valley Fair (an overlay): nine places on a plank table to fill from
 * the farmer's monitors (the goods are only shown, and stay the farmer's), then the judge. Up,
 * down, left and right move between the places and the two buttons under them.
 */
final class GrangeScreen implements Screen {
    private final FairScreen fair;
    private int cursor;

    GrangeScreen(FairScreen fair) {
        this.fair = fair;
        while (fair.display.size() < Fair.DISPLAY_SLOTS) {
            fair.display.add(null);
        }
    }

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void update(Shell shell) {
        var in = shell.in;
        if (in.back) {
            shell.pop();
            return;
        }
        if (cursor < Fair.DISPLAY_SLOTS) {
            if (in.leftPressed && cursor % 3 > 0) {
                cursor--;
            } else if (in.rightPressed && cursor % 3 < 2) {
                cursor++;
            } else if (in.upPressed && cursor >= 3) {
                cursor -= 3;
            } else if (in.downPressed) {
                cursor = cursor + 3 < Fair.DISPLAY_SLOTS ? cursor + 3 : Fair.DISPLAY_SLOTS;
            }
        } else if (in.upPressed) {
            cursor = cursor == Fair.DISPLAY_SLOTS ? 7 : Fair.DISPLAY_SLOTS;
        } else if (in.downPressed && cursor == Fair.DISPLAY_SLOTS) {
            cursor = Fair.DISPLAY_SLOTS + 1;
        }
        if (!in.confirm) {
            return;
        }
        if (cursor == Fair.DISPLAY_SLOTS) {
            if (fair.display.stream().allMatch(java.util.Objects::isNull)) {
                shell.toast("PUT SOMETHING ON THE TABLE FIRST");
                shell.sfx(Sfx.ERROR);
                return;
            }
            shell.pop();
            fair.callJudge();
        } else if (cursor == Fair.DISPLAY_SLOTS + 1) {
            shell.pop();
        } else {
            choose(shell);
        }
    }

    /** Picks what goes in the chosen place (or clears it). */
    private void choose(Shell shell) {
        Game game = shell.game;
        List<String> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Item> icons = new ArrayList<>();
        for (int i = 0; i < game.inventory.size(); i++) {
            String id = game.inventory.id(i);
            if (id == null || ids.contains(id) || !Fair.displayable(game.item(id))) {
                continue;
            }
            int shown = 0;
            for (String d : fair.display) {
                if (id.equals(d)) {
                    shown++;
                }
            }
            if (shown < game.inventory.total(id) || id.equals(fair.display.get(cursor))) {
                ids.add(id);
                labels.add(game.item(id).name());
                icons.add(game.item(id));
            }
        }
        labels.add("(EMPTY)");
        icons.add(null);
        int slot = cursor;
        shell.push(new Choose("WHAT GOES HERE?", labels, icons, i -> {
            if (i < 0) {
                return;
            }
            String id = i < ids.size() ? ids.get(i) : null;
            fair.display.set(slot, id);
            if (id != null && Fair.robomart(id)) {
                fair.caption("robotnik", "AH. ROBO COLA. A FARMER OF REFINED TASTE.");
            }
            shell.sfx(id == null ? Sfx.SWITCH : Sfx.GRAB);
        }));
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        Game game = shell.game;
        int w = 260, h = 176, x = (canvas.width() - w) / 2, y = 30;
        Text.panel(canvas, x, y, w, h);
        Text.shadow(canvas, "GRANGE DISPLAY", x + 10, y + 8, Text.YELLOW);
        int kinds = 0;
        java.util.Set<Kind> seen = new java.util.HashSet<>();
        for (String d : fair.display) {
            if (d != null && seen.add(game.item(d).kind())) {
                kinds++;
            }
        }
        Text.right(canvas, kinds + " KINDS", x + w - 10, y + 8, Text.WHITE);
        // The plank table: three rows of three places.
        int tx = x + 60, ty = y + 28;
        canvas.fill(tx - 10, ty - 6, 160, 96, 0xFF924900);
        canvas.fill(tx - 10, ty - 6, 160, 3, 0xFFDB9249);
        for (int i = 0; i < Fair.DISPLAY_SLOTS; i++) {
            int sx = tx + i % 3 * 48, sy = ty + i / 3 * 30;
            canvas.fill(sx, sy, 40, 24, i == cursor ? 0xFFB66D24 : 0xFF6D2400);
            canvas.fill(sx + 1, sy + 1, 38, 22, 0xFF492400);
            String id = fair.display.get(i);
            if (id != null) {
                shell.art.icons.draw(canvas, game.item(id), sx + 12, sy + 4, SceneDraw.plain());
            }
        }
        int by = y + 128;
        Text.shadow(canvas, (cursor == Fair.DISPLAY_SLOTS ? "> " : "  ") + "CALL THE JUDGE", x + 70, by,
                cursor == Fair.DISPLAY_SLOTS ? Text.YELLOW : Text.WHITE);
        Text.shadow(canvas, (cursor == Fair.DISPLAY_SLOTS + 1 ? "> " : "  ") + "NOT YET", x + 70, by + 14,
                cursor == Fair.DISPLAY_SLOTS + 1 ? Text.YELLOW : Text.WHITE);
        String note = cursor < Fair.DISPLAY_SLOTS && fair.display.get(cursor) != null
                ? game.item(fair.display.get(cursor)).name()
                : "VARIETY AND VALUE WIN. THE JUDGE LIKES ROBOMART.";
        Text.note(canvas, note, x + 10, y + h - 16, w - 20, Text.GREY);
    }
}
