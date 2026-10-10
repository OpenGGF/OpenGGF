package starpost.barn;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import starpost.core.Game;
import starpost.scene.Screen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.ui.Text;

/**
 * The coop's or pen's door: collect what the animals gave, fill the hopper, buy an animal (the
 * pen also finds you a Rocky for the pond), and see everyone's hearts and whether they ate.
 */
final class BarnMenu implements Screen {
    private static final int ROWS = 6;
    private static final int ROW_H = 24;

    /** One line of the menu: an item icon or an animal's sprite, words, and what choosing it does. */
    private record Row(String icon, String animal, String label, String right, Runnable action, String note, Animal who) {
    }

    private final BarnSystem sys;
    private final int home;
    private int cursor;
    private int top;

    BarnMenu(BarnSystem sys, int home) {
        this.sys = sys;
        this.home = home;
    }

    @Override
    public boolean overlay() {
        return true;
    }

    private List<Row> rows(Shell shell) {
        Game game = shell.game;
        Barn barn = sys.barn;
        List<Row> out = new ArrayList<>();
        int waiting = 0;
        String first = null;
        for (Map.Entry<String, Integer> e : barn.goods(home).entrySet()) {
            waiting += e.getValue();
            first = first == null ? e.getKey() : first;
        }
        if (waiting > 0) {
            String what = first;
            out.add(new Row(what, null, "COLLECT " + waiting, "", () -> {
                int n = barn.collect(game, home);
                shell.toast(n > 0 ? "COLLECTED " + n : "NO ROOM");
                shell.sfx(n > 0 ? Sfx.GRAB : Sfx.ERROR);
            }, "TAKE WHAT THEY GAVE.", null));
        }
        out.add(new Row("fibre", null, "FILL HOPPER", barn.feed(home) + "/" + Barn.MAX_FEED, () -> {
            int added = barn.fill(game, home);
            shell.toast(added > 0 ? "HOPPER +" + added : "NO FEED IN YOUR MONITORS");
            shell.sfx(added > 0 ? Sfx.SWITCH : Sfx.ERROR);
        }, home == Animals.COOP ? "A FIBRE FEEDS ONE BIRD A DAY; A SUNFLOWER, THREE. DRY DAYS THEY GRAZE."
                : "A FIBRE FEEDS ONE ANIMAL A DAY. ON DRY DAYS THEY GRAZE THE GRASS.", null));
        for (String kind : Animals.kinds()) {
            int kindHome = Animals.home(kind);
            if (kindHome != home && !(home == Animals.PEN && kindHome == Animals.POND)) {
                continue;
            }
            int price = Animals.price(kind);
            String product = Animals.product(kind);
            out.add(new Row(null, kind, "BUY A " + Animals.name(kind), price + " RINGS", () -> {
                String why = barn.buy(game, kind);
                if (why == null) {
                    Animal joined = barn.animals.get(barn.animals.size() - 1);
                    sys.arrive(joined);
                    shell.toast(joined.name + " THE " + Animals.name(kind) + " MOVED IN!");
                    shell.sfx(Sfx.REGISTER);
                } else {
                    shell.toast(why);
                    shell.sfx(Sfx.ERROR);
                }
            }, describe(kind, product, game), null));
        }
        for (Animal a : barn.animals) {
            int kindHome = Animals.home(a.kind);
            if (kindHome == home || home == Animals.PEN && kindHome == Animals.POND) {
                out.add(new Row(null, a.kind, a.name, "", null, Animals.name(a.kind) + ", " + a.age + " DAYS. "
                        + (a.fed ? "FED TODAY." : "HUNGRY!") + (a.petted ? " PETTED." : " WANTS A PET."), a));
            }
        }
        return out;
    }

    private static String describe(String kind, String product, Game game) {
        return switch (kind) {
            case "cucky" -> "AN EGG A DAY ONCE GROWN.";
            case "pecky" -> "ICE EGGS EVERY OTHER DAY, IN WINTER ONLY.";
            case "pocky" -> "FLUFF EVERY " + Animals.every(kind, game) + " DAYS.";
            case "picky" -> "DIGS TRUFFLES FROM OPEN GRASS ON DRY DAYS.";
            default -> "LIVES IN THE POND. CATCHES YOU A FISH A DAY.";
        };
    }

    @Override
    public void update(Shell shell) {
        if (shell.in.back || shell.in.menu && !shell.in.confirm) {
            shell.pop();
            return;
        }
        List<Row> rows = rows(shell);
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
        if (shell.in.confirm && rows.get(cursor).action() != null) {
            rows.get(cursor).action().run();
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        Barn barn = sys.barn;
        List<Row> rows = rows(shell);
        int w = 330, h = 34 + ROWS * ROW_H + 26, x = (canvas.width() - w) / 2, y = 14;
        Text.panel(canvas, x, y, w, h);
        boolean big = barn.level(home) == 2;
        String title = (home == Animals.COOP ? big ? "BIG COOP" : "CUCKY COOP" : big ? "BIG PEN" : "POCKY PEN") + "  "
                + barn.count(home) + "/" + barn.capacity(home);
        Text.shadow(canvas, title, x + 10, y + 8, Text.YELLOW);
        Text.right(canvas, shell.game.rings + " RINGS", x + w - 10, y + 8, Text.WHITE);
        for (int i = top; i < Math.min(rows.size(), top + ROWS); i++) {
            Row row = rows.get(i);
            int ry = y + 26 + (i - top) * ROW_H;
            if (i == cursor) {
                canvas.fill(x + 6, ry - 2, w - 12, ROW_H - 1, 0x60B66D24);
            }
            if (row.animal() != null) {
                SceneSpriteSet set = shell.art.animal(row.animal());
                if (set != null && set.frameCount() > 2) {
                    SceneSprite pose = set.frame(2);
                    canvas.draw(pose, x + 18, ry + 21 - (pose.height() - pose.originY()), SceneDraw.plain());
                }
            } else if (row.icon() != null) {
                shell.art.icons.draw(canvas, shell.game.item(row.icon()), x + 10, ry + 2, SceneDraw.plain());
            }
            int colour = row.action() == null ? Text.WHITE : i == cursor ? Text.YELLOW : Text.WHITE;
            Text.shadow(canvas, row.label(), x + 34, ry + 6, colour);
            if (row.who() != null) {
                drawHearts(canvas, row.who(), x + w - 12, ry + 7);
            } else {
                Text.right(canvas, row.right(), x + w - 12, ry + 6, Text.BLUE);
            }
        }
        if (!rows.isEmpty()) {
            Text.note(canvas, rows.get(Math.min(cursor, rows.size() - 1)).note(), x + 10, y + h - 22, w - 20, Text.GREY);
        }
    }

    private void drawHearts(SceneCanvas canvas, Animal a, int right, int y) {
        int w = sys.art.heart.width() + 1;
        for (int i = 0; i < 5; i++) {
            canvas.draw(i < a.hearts() ? sys.art.heart : sys.art.heartEmpty, right - (5 - i) * w, y, SceneDraw.plain());
        }
        if (!a.fed) {
            canvas.text("!", right - 5 * w - 10, y, Text.RED);
        }
    }
}
