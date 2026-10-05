package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import java.util.List;
import slaytherobotnik.core.Relic;
import slaytherobotnik.run.ShopRoom;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;
import slaytherobotnik.ui.Hotspots;
import slaytherobotnik.ui.SmallFont;

/** The Egg Robo's shop. "Don't tell Robotnik, ya hear?!" */
final class ShopView implements RunScreen.RoomView {
    private final String[] greetings = {
            "Psst! Over here! Don't tell Robotnik, ya hear?!",
            "Rings for gear, gear for rings. Fair's fair!",
            "Robotnik doesn't pay overtime. You do!",
    };
    private final String[] thanks = {
            "Pleasure doin' business!", "Don't tell Robotnik, ya hear?!", "Ka-ching! Rings, rings, rings!",
    };
    private final String[] broke = {
            "No rings, no deal, hedgehog.", "That's more rings than you got!",
    };

    private final ShopRoom room;
    private final Hotspots spots = new Hotspots();
    private String line;
    private int lineIndex;

    ShopView(ShopRoom room) {
        this.room = room;
    }

    private void say(Shell shell, String[] pool) {
        lineIndex = (lineIndex + 1 + (int) (shell.ticks % 3)) % pool.length;
        line = pool[lineIndex];
    }

    private void layout(Shell shell) {
        spots.clear();
        List<ShopRoom.Item> items = room.items();
        int cardIndex = 0;
        int otherIndex = 0;
        for (int i = 0; i < items.size(); i++) {
            ShopRoom.Item item = items.get(i);
            if (room.sold(i)) {
                if (item.card() != null) {
                    cardIndex++;
                } else {
                    otherIndex++;
                }
                continue;
            }
            if (item.card() != null) {
                int x = 120 + cardIndex * 40;
                spots.add("i" + i, x, 40, CardRenderer.SMALL_W, CardRenderer.SMALL_H);
                cardIndex++;
            } else {
                int x = 120 + otherIndex * 34;
                spots.add("i" + i, x, 118, 26, 26);
                otherIndex++;
            }
        }
        spots.add("remove", 120, 160, 120, 16, !room.removalUsed() && shell.run.state().rings() >= room.removalPrice());
        spots.add("leave", shell.width() - 90, 196, 80, 16);
    }

    @Override
    public void update(Shell shell, RunScreen screen) {
        if (line == null) {
            say(shell, greetings);
        }
        layout(shell);
        String picked = spots.update(shell.in);
        if (shell.in.back) {
            room.leave();
            return;
        }
        if (picked == null) {
            return;
        }
        switch (picked) {
            case "leave" -> {
                shell.sfx(Sounds.SFX_SPRING);
                room.leave();
            }
            case "remove" -> {
                if (room.startRemoval()) {
                    shell.sfx(Sounds.SFX_SWITCH);
                }
            }
            default -> {
                int i = Integer.parseInt(picked.substring(1));
                if (room.buy(i)) {
                    shell.sfx(Sounds.SFX_REGISTER);
                    say(shell, thanks);
                } else {
                    shell.sfx(Sounds.SFX_ERROR);
                    say(shell, shell.run.state().rings() < room.items().get(i).price() ? broke
                            : new String[] {"Your item belt's full, pal."});
                }
            }
        }
    }

    @Override
    public void draw(Shell shell, RunScreen screen, SceneCanvas c) {
        int w = shell.width();
        int h = shell.height();
        Gfx.checker(c, 0, 0, w, h, 16, 0xFF241848, 0xFF2C2058, (int) (shell.ticks / 4));
        SmallFont f = shell.font;
        // The shopkeeper.
        Gfx.panel(c, 6, 32, 104, 160, 0xE0100820, 0xFF904890);
        SceneSprite robo = shell.art.romFrame("egg_robo", 0);
        if (robo != null) {
            c.draw(robo, 58, 128 + (float) Math.sin(shell.ticks * 0.08) * 2, SceneDraw.plain().withScale(1));
        } else {
            c.draw(shell.art.icon("node_shop"), 40, 80, SceneDraw.plain().withScale(2));
        }
        f.drawCentered(c, "EGG ROBO", 58, 138, Colors.TEXT_BAD);
        f.drawCentered(c, "(OFF DUTY)", 58, 146, Colors.TEXT_DIM);
        if (line != null) {
            Gfx.panel(c, 10, 156, 96, 32, 0xF0FFFFFF, Colors.BLACK);
            int ly = 160;
            for (String l : f.wrap(line, 88)) {
                f.draw(c, l, 14, ly, 0xFF101010);
                ly += SmallFont.LINE;
            }
        }
        f.drawShadowed(c, "CARDS", 120, 32, Colors.GOLD);
        f.drawShadowed(c, "RELICS & MONITORS", 120, 108, Colors.GOLD);
        layout(shell);
        List<ShopRoom.Item> items = room.items();
        ShopRoom.Item focusItem = null;
        Hotspots.Spot focusSpot = null;
        for (int i = 0; i < items.size(); i++) {
            Hotspots.Spot s = spots.spot("i" + i);
            if (s == null) {
                continue;
            }
            ShopRoom.Item item = items.get(i);
            boolean focus = spots.isFocused(s.id());
            boolean affordable = shell.run.state().rings() >= item.price();
            if (item.card() != null) {
                screen.cards.drawSmall(c, item.card(), null, s.x(), s.y() - (focus ? 3 : 0), true, false);
            } else if (item.relicId() != null) {
                Gfx.panel(c, s.x(), s.y(), s.w(), s.h());
                HudIcons.relic(shell, c, shell.catalog.newRelic(item.relicId()), s.x() + 5, s.y() + 5, 16);
            } else {
                Gfx.panel(c, s.x(), s.y(), s.w(), s.h());
                HudIcons.potion(shell, c, item.potion(), s.x() + 7, s.y() + 7);
            }
            String price = Integer.toString(item.price());
            int py = s.y() + s.h() + 2;
            HudIcons.ring(shell, c, s.x(), py - 2, shell.ticks);
            f.drawShadowed(c, price, s.x() + 12, py + 1, affordable ? (item.onSale() ? Colors.TEXT_GOOD : Colors.RING)
                    : Colors.TEXT_BAD);
            if (item.onSale()) {
                f.drawOutlined(c, "SALE", s.x() + 4, s.y() - 6, Colors.TEXT_GOOD, 1);
            }
            if (focus) {
                Gfx.focusFrame(c, s.x(), s.y() - (item.card() != null ? 3 : 0), s.w(), s.h(), shell.ticks);
                focusItem = item;
                focusSpot = s;
            }
        }
        Hotspots.Spot remove = spots.spot("remove");
        Gfx.button(c, f, room.removalUsed() ? "REMOVAL SOLD OUT" : "REMOVE A CARD: " + room.removalPrice(), remove.x(),
                remove.y(), remove.w(), remove.h(), spots.isFocused("remove"), remove.enabled(), shell.ticks);
        Hotspots.Spot leave = spots.spot("leave");
        Gfx.button(c, f, "LEAVE", leave.x(), leave.y(), leave.w(), leave.h(), spots.isFocused("leave"), true,
                shell.ticks);
        if (focusItem != null) {
            if (focusItem.card() != null) {
                screen.cards.drawBig(c, focusItem.card(), null, null, w - CardRenderer.BIG_W - 8, 36, false);
            } else if (focusItem.relicId() != null) {
                Relic r = shell.catalog.newRelic(focusItem.relicId());
                screen.tooltip(r.name().toUpperCase(), r.description(), focusSpot.x(), focusSpot.y() + 40);
            } else {
                var p = focusItem.potion();
                screen.tooltip(p.name().toUpperCase(), p.describe(shell.run.state().potionPotency(p)), focusSpot.x(),
                        focusSpot.y() + 40);
            }
        }
    }
}
