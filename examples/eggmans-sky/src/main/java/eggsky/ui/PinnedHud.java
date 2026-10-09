package eggsky.ui;

import com.openggf.mods.scene.SceneCanvas;
import eggsky.Game;
import eggsky.game.ShoppingList;

/** Compact recipe checklist; indentation marks refining/crafting intermediates. */
public final class PinnedHud {
    private PinnedHud() { }
    public static void draw(Game g, SceneCanvas c) {
        var needs = ShoppingList.needs(g.player);
        int extra = g.player.pinned < 0 ? 1 : 0;
        int height = 19 + needs.size() * 9 + extra * 9;
        int top = Math.max(82, g.height - 30 - height);
        Ui.panel(c, 4, top, 194, height, 0xC0081028);
        String title = ShoppingList.title(g.player);
        if (title.length() > 29) title = title.substring(0, 26) + "...";
        g.font.draw(c, "* " + title, 9, top + 5, Ui.GOLD);
        int y = top + 17;
        for (var need : needs) {
            String label = g.catalog.name(need.item()) + " " + need.owned() + "/" + need.required();
            g.font.draw(c, (need.depth() > 0 ? "> " : "") + label, 9 + need.depth() * 6, y,
                    need.owned() >= need.required() ? Ui.CYAN : Ui.WHITE);
            y += 9;
        }
        if (extra > 0) {
            var t = g.catalog.tech(-g.player.pinned - 1);
            int cost = g.player.level(t.id()) >= t.max() ? 0 : t.shards() * (g.player.level(t.id()) + 1);
            g.font.draw(c, "SHARDS " + g.player.shards + "/" + cost, 9, y, Ui.CYAN);
        }
    }
}
