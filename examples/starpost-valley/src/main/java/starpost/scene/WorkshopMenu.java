package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import starpost.core.Game;
import starpost.core.Item;
import starpost.core.Recipe;
import com.openggf.mods.ui.CompactFont;
import starpost.ui.Text;

/**
 * Tails's workshop: upgrades (the Water Shield's tank, more monitor slots, the Fire and Lightning
 * Shields, a fishing rod) and things to build from scrap, wood and marble. Rows whose ingredients
 * the game does not know yet (an uninstalled system) are left out.
 */
final class WorkshopMenu implements Screen {
    private static final int ROWS = 6;
    private static final int ROW_H = 24;

    /** One row: what it makes, its price, and what happens when it is bought. */
    private record Offer(String name, String icon, int rings, Map<String, Integer> inputs, Runnable effect, String text) {
    }

    private int cursor;
    private int top;

    @Override
    public boolean overlay() {
        return true;
    }

    private List<Offer> offers(Shell shell) {
        Game game = shell.game;
        List<Offer> out = new ArrayList<>();
        int cap = game.waterCapacity;
        if (cap < 80) {
            int next = cap * 2;
            out.add(new Offer("WATER SHIELD TANK " + next, "water_shield", cap == 10 ? 500 : cap == 20 ? 2000 : 5000,
                    inputs("marble_chip", cap == 10 ? 10 : 0, "scrap", cap == 10 ? 0 : cap == 20 ? 25 : 50),
                    () -> {
                        game.waterCapacity = next;
                        game.waterCharges = next;
                    }, "HOLDS WATER FOR " + next + " PLOTS."));
        }
        if (game.inventory.size() < 36) {
            int slots = game.inventory.size() + 12;
            out.add(new Offer("MONITOR SLOTS " + slots, "item_monitor", slots == 24 ? 2000 : 10000, Map.of(),
                    () -> game.inventory.resize(slots), "CARRY " + slots + " STACKS."));
        }
        int open = game.farm.open();
        if (open < starpost.core.Farm.COLUMNS) {
            int next = Math.min(starpost.core.Farm.COLUMNS, open + 12);
            int price = open < 36 ? 1500 : open < 48 ? 4000 : 9000;
            out.add(new Offer("CLEAR MORE LAND", "palm_wood", price, inputs("palm_wood", open < 36 ? 20 : 50, "scrap", 0),
                    () -> game.farm.open(next), "TAILS CLEARS 12 MORE COLUMNS OF FIELD (" + open + " TO " + next + ")."));
        }
        tool(out, game, "fire_shield", 300, inputs("scrap", 5, "marble_chip", 0));
        tool(out, game, "lightning_shield", 2500, inputs("scrap", 20, "marble_chip", 20));
        tool(out, game, "fishing_rod", 400, inputs("scrap", 3, "palm_wood", 10));
        for (Recipe recipe : shell.catalog.recipes()) {
            Item product = game.item(recipe.product());
            out.add(new Offer(product.name() + (recipe.count() > 1 ? " X" + recipe.count() : ""), product.id(),
                    recipe.rings(), recipe.inputs(), () -> game.inventory.add(product, recipe.count()), product.text()));
        }
        out.removeIf(o -> !o.inputs().keySet().stream().allMatch(shell.catalog::hasItem));
        return out;
    }

    private static void tool(List<Offer> out, Game game, String id, int rings, Map<String, Integer> inputs) {
        if (game.inventory.total(id) == 0 && !game.flags.contains("built_" + id)) {
            Item item = game.item(id);
            out.add(new Offer(item.name(), id, rings, inputs, () -> {
                game.inventory.add(item, 1);
                game.flags.add("built_" + id);
            }, item.text()));
        }
    }

    private static Map<String, Integer> inputs(String a, int na, String b, int nb) {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (na > 0) {
            m.put(a, na);
        }
        if (nb > 0) {
            m.put(b, nb);
        }
        return m;
    }

    private static boolean affordable(Game game, Offer offer) {
        if (game.rings < offer.rings()) {
            return false;
        }
        for (Map.Entry<String, Integer> e : offer.inputs().entrySet()) {
            if (game.inventory.total(e.getKey()) < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void update(Shell shell) {
        if (shell.in.back || shell.in.menu && !shell.in.confirm) {
            shell.pop();
            return;
        }
        List<Offer> offers = offers(shell);
        if (offers.isEmpty()) {
            return;
        }
        if (shell.in.downPressed) {
            cursor = (cursor + 1) % offers.size();
            shell.sfx(Sfx.SWITCH);
        } else if (shell.in.upPressed) {
            cursor = (cursor + offers.size() - 1) % offers.size();
            shell.sfx(Sfx.SWITCH);
        }
        cursor = Math.min(cursor, offers.size() - 1);
        top = Math.max(0, Math.min(top, cursor));
        if (cursor >= top + ROWS) {
            top = cursor - ROWS + 1;
        }
        if (shell.in.confirm) {
            Offer offer = offers.get(cursor);
            Game game = shell.game;
            if (!affordable(game, offer)) {
                shell.toast("NOT ENOUGH");
                shell.sfx(Sfx.ERROR);
                return;
            }
            game.rings -= offer.rings();
            for (Map.Entry<String, Integer> e : offer.inputs().entrySet()) {
                game.inventory.remove(e.getKey(), e.getValue());
            }
            offer.effect().run();
            shell.sfx(Sfx.REGISTER);
            shell.toast("TAILS BUILT " + offer.name());
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        Game game = shell.game;
        List<Offer> offers = offers(shell);
        int w = 340, h = 34 + ROWS * ROW_H + 24, x = (canvas.width() - w) / 2, y = 14;
        Text.panel(canvas, x, y, w, h);
        Text.shadow(canvas, "TAILS' WORKSHOP", x + 10, y + 8, Text.YELLOW);
        Text.right(canvas, game.rings + " RINGS", x + w - 10, y + 8, Text.WHITE);
        for (int i = top; i < Math.min(offers.size(), top + ROWS); i++) {
            Offer offer = offers.get(i);
            int ry = y + 26 + (i - top) * ROW_H;
            if (i == cursor) {
                canvas.fill(x + 6, ry - 2, w - 12, ROW_H - 1, 0x60B66D24);
            }
            shell.art.icons.draw(canvas, game.item(offer.icon()), x + 10, ry + 2, SceneDraw.plain());
            boolean can = affordable(game, offer);
            Text.shadow(canvas, offer.name(), x + 32, ry + 1, can ? (i == cursor ? Text.YELLOW : Text.WHITE) : Text.GREY);
            StringBuilder cost = new StringBuilder(offer.rings() > 0 ? offer.rings() + " RINGS" : "");
            for (Map.Entry<String, Integer> e : offer.inputs().entrySet()) {
                boolean have = game.inventory.total(e.getKey()) >= e.getValue();
                cost.append(cost.isEmpty() ? "" : "  ").append(e.getValue()).append(" ").append(game.item(e.getKey()).name())
                        .append(have ? "" : "!");
            }
            CompactFont.shadowed(canvas, cost.toString(), x + 32, ry + 13, 1, can ? 0xFF92DBFF : 0xFFFF6D6D, 0xFF000000);
        }
        if (!offers.isEmpty()) {
            Text.note(canvas, offers.get(Math.min(cursor, offers.size() - 1)).text(), x + 10, y + h - 16, w - 20, Text.GREY);
        }
    }

}
