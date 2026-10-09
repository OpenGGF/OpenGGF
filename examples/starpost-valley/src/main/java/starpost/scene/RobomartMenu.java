package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.ui.CompactFont;
import java.util.ArrayList;
import java.util.List;
import starpost.core.Game;
import starpost.core.Item;
import starpost.ui.Text;

/**
 * Robomart (design doc §6.4): Robotnik's store, staffed by Egg Robos. Seeds at a
 * mark-up, cheaper than Dandel's for members. The Robomart Membership seals the Great Capsule for
 * good; after that the Valley Development Form sells each chamber's reward for rings, and
 * badniks build it. The cost is not in the menu: the animals leave (other systems read the
 * {@code robo_member} flag).
 */
final class RobomartMenu implements Screen {
    private static final int ROWS = 7;

    private record Offer(String name, String icon, int rings, Runnable effect, String text) {
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
        boolean member = game.flags.contains("robo_member");
        if (!member) {
            out.add(new Offer("ROBOMART MEMBERSHIP", "item_monitor", 5000, () -> {
                game.flags.add("robo_member");
                shell.toast("WELCOME TO THE ROBOMART FAMILY! HO HO HO!");
            }, "CHEAPER SEEDS. THE CAPSULE IS SEALED FOR GOOD."));
        } else {
            form(out, game, "capsule_garden", "ROBO GREENHOUSE", 35000, "BADNIKS BUILD THE CAPSULE GARDEN.");
            form(out, game, "minecart", "ROBO MINECART", 15000, "BADNIKS RUN THE RUINS MINECART.");
            form(out, game, "lake_bridge", "ROBO BRIDGE", 25000, "BADNIKS REBUILD THE LAKE BRIDGE.");
            form(out, game, "big_coop", "ROBO COOP", 20000, "A BIGGER COOP AND PEN, BADNIK-BUILT.");
            form(out, game, "flicky_roost", "ROBO ROOSTS", 20000, "ROOSTS FOR FLICKIES. THEY DON'T LIKE THEM.");
            form(out, game, "farm_open", "ROBO LAND CLEARANCE", 40000, "THE WHOLE FARM, CLEARED BY BULLDOZERS.");
        }
        for (Item seed : shell.catalog.seedsFor(game.calendar.season())) {
            int base = shell.catalog.seedPrice(seed.id());
            int price = member ? base * 4 / 5 : base * 5 / 4;
            out.add(new Offer(seed.name(), seed.id(), price, () -> game.inventory.add(seed, 1), seed.text()));
        }
        return out;
    }

    private static void form(List<Offer> out, Game game, String flag, String name, int rings, String text) {
        if (!game.flags.contains(flag)) {
            out.add(new Offer(name, "scrap", rings, () -> {
                game.flags.add(flag);
                game.flags.add("robo_" + flag);
                if (flag.equals("farm_open")) {
                    game.farm.open(starpost.core.Farm.COLUMNS);
                }
            }, text));
        }
    }

    @Override
    public void update(Shell shell) {
        if (shell.in.back || shell.in.menu && !shell.in.confirm) {
            shell.pop();
            return;
        }
        List<Offer> offers = offers(shell);
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
            if (shell.game.rings < offer.rings()) {
                shell.toast("NO RINGS, NO DEAL");
                shell.sfx(Sfx.ERROR);
                return;
            }
            shell.game.rings -= offer.rings();
            offer.effect().run();
            shell.sfx(Sfx.REGISTER);
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        Game game = shell.game;
        List<Offer> offers = offers(shell);
        int w = 360, h = 44 + ROWS * 20 + 30, x = (canvas.width() - w) / 2, y = 14;
        canvas.fill(x, y, w, h, 0xE0202430);
        canvas.fill(x, y, w, 3, 0xFFDB0000);
        canvas.fill(x, y + h - 3, w, 3, 0xFFDB0000);
        Text.shadow(canvas, "ROBOMART", x + 10, y + 8, Text.RED);
        CompactFont.shadowed(canvas, "EVERYTHING YOU NEED. NOTHING YOU WANT.", x + 10, y + 21, 1, 0xFFDBDBDB, 0xFF000000);
        Text.right(canvas, game.rings + " RINGS", x + w - 10, y + 8, Text.WHITE);
        if (shell.art.eggRobo != null && shell.art.eggRobo.frameCount() > 0) {
            var robo = shell.art.eggRobo.frame(0);
            canvas.draw(robo, x + w - 30, y + h - 8 - (robo.height() - robo.originY()), SceneDraw.plain().withFlipX(true));
        }
        for (int i = top; i < Math.min(offers.size(), top + ROWS); i++) {
            Offer offer = offers.get(i);
            int ry = y + 34 + (i - top) * 20;
            if (i == cursor) {
                canvas.fill(x + 6, ry - 2, w - 60, 19, 0x60DB0000);
            }
            shell.art.icons.draw(canvas, game.item(offer.icon()), x + 10, ry, SceneDraw.plain());
            boolean can = game.rings >= offer.rings();
            Text.shadow(canvas, Text.fit(canvas, offer.name(), w - 150), x + 32, ry + 4,
                    can ? (i == cursor ? Text.YELLOW : Text.WHITE) : Text.GREY);
            Text.right(canvas, Integer.toString(offer.rings()), x + w - 60, ry + 4, can ? Text.WHITE : Text.RED);
        }
        Text.note(canvas, offers.get(Math.min(cursor, offers.size() - 1)).text(), x + 10, y + h - 18, w - 50, Text.GREY);
    }
}
