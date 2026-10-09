package threeislands.screen;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.ArrayList;
import java.util.List;
import threeislands.Game;
import threeislands.art.Font;
import threeislands.audio.Audio;
import threeislands.core.Item;
import threeislands.core.Progress;
import threeislands.view.Ui;

/** Trading with a physical traveller; closing the shop returns to the same field position. */
public final class ShopScreen implements Screen {
    private final Screen field;
    private int shopSelected;
    private String message;
    private int messageTicks;
    private long ticks;

    public ShopScreen(Game game, Screen field) {
        this.field = field;
    }

    @Override
    public String name() {
        return "SHOP";
    }

    @Override
    public void update(Game game) {
        ticks++;
        if (messageTicks > 0) messageTicks--;
        updateShop(game);
    }

    private void updateShop(Game game) {
        Item[] items = Item.values();
        if (game.controls.up()) {
            shopSelected = (shopSelected + items.length - 1) % items.length;
            game.audio.sfx(Audio.SFX_CURSOR);
        }
        if (game.controls.down()) {
            shopSelected = (shopSelected + 1) % items.length;
            game.audio.sfx(Audio.SFX_CURSOR);
        }
        if (game.controls.back()) {
            game.swap(field);
            return;
        }
        if (game.controls.accept()) {
            Item item = items[shopSelected];
            Progress progress = game.progress;
            if (progress.count(item) >= Progress.MAX_ITEMS) {
                game.audio.sfx(Audio.SFX_ERROR);
                message = "You can't carry any more.";
            } else if (!progress.spendRings(item.price)) {
                game.audio.sfx(Audio.SFX_ERROR);
                message = "Not enough rings!";
            } else {
                progress.addItem(item, 1);
                game.audio.sfx(Audio.SFX_RING);
                message = "Bought a " + item.label + ".";
            }
            messageTicks = 90;
        }
    }

    @Override
    public void draw(Game game, SceneCanvas c) {
        field.draw(game, c);
        int w = c.width();
        int h = c.height();
        c.fill(0, 0, w, h, 0x70000010);
        String title = "Pocky's travelling stall";
        game.ui.window(c, 10, 10, w - 20, 26);
        game.font.shadowed(c, title, 18, 18, Ui.GOLD);
        String rings = "~ " + game.progress.rings() + " rings";
        game.font.draw(c, rings, w - 20 - game.font.width(rings), 18, Ui.GOLD);
        drawShop(game, c, w, h);
        if (messageTicks > 0 && message != null) {
            game.ui.window(c, 10, h - 30, w - 20, 22);
            game.font.draw(c, message, 18, h - 24, Ui.TEXT);
        }
    }

    private void drawShop(Game game, SceneCanvas c, int w, int h) {
        Item[] items = Item.values();
        List<String> labels = new ArrayList<>();
        List<String> prices = new ArrayList<>();
        List<Boolean> enabled = new ArrayList<>();
        for (Item item : items) {
            labels.add(item.label + "  x" + game.progress.count(item));
            prices.add(item.price + "~");
            enabled.add(game.progress.rings() >= item.price);
        }
        game.ui.window(c, 10, 44, 200, items.length * Font.LINE + 12);
        game.ui.list(c, 16, 50, 186, items.length, labels, enabled, shopSelected, prices, ticks);
        Item item = items[shopSelected];
        int y = 44 + items.length * Font.LINE + 18;
        game.ui.window(c, 10, y, w - 20, 34);
        monitor(game, c, item, 30, y + 26);
        game.ui.paragraph(c, item.description, 50, y + 6, w - 76, Ui.TEXT, 2);
    }

    /** The item's own monitor (or a Special Stage sphere for EP), standing on (x, feet). */
    static void monitor(Game game, SceneCanvas c, Item item, int x, int feet) {
        if (item.icon < 0) {
            SceneSpriteSet sphere = game.art.sprites("s3k:sphere");
            if (sphere != null) c.draw(sphere.frame(0), x, feet - 10, SceneDraw.plain());
            return;
        }
        SceneSpriteSet set = game.art.sprites("s3k:monitor");
        if (set != null) c.draw(set.frame(item.icon), x, feet - 15, SceneDraw.plain());
    }
}
