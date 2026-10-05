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

    /**
     * Row one: the five hero cards. Row two: the colourless cards, then relics and monitors.
     * The left column shows the Egg Robo, or the focused card at full size.
     */
    private void layout(Shell shell) {
        spots.clear();
        List<ShopRoom.Item> items = room.items();
        int heroCards = 0;
        int lowerCards = 0;
        int others = 0;
        for (int i = 0; i < items.size(); i++) {
            ShopRoom.Item item = items.get(i);
            boolean sold = room.sold(i);
            if (item.card() != null && item.kind().equals(ShopRoom.CARD)) {
                if (!sold) {
                    spots.add("i" + i, 104 + heroCards * 50, 36, CardRenderer.SMALL_W, CardRenderer.SMALL_H);
                }
                heroCards++;
            } else if (item.card() != null) {
                if (!sold) {
                    spots.add("i" + i, 104 + lowerCards * 50, 114, CardRenderer.SMALL_W, CardRenderer.SMALL_H);
                }
                lowerCards++;
            } else {
                if (!sold) {
                    spots.add("i" + i, 212 + others * 30, 124, 26, 26);
                }
                others++;
            }
        }
        spots.add("remove", 104, 192, 120, 16, !room.removalUsed() && shell.run.state().rings() >= room.removalPrice());
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
        layout(shell);
        ShopRoom.Item hovered = null;
        for (int i = 0; i < room.items().size(); i++) {
            if (spots.isFocused("i" + i) && spots.spot("i" + i) != null) {
                hovered = room.items().get(i);
            }
        }
        // The shopkeeper, or the focused card at full size in his place.
        Gfx.panel(c, 4, 32, 94, 160, 0xE0100820, 0xFF904890);
        if (hovered != null && hovered.card() != null) {
            screen.cards.drawBig(c, hovered.card(), null, null, 7, 34, false);
        } else {
            drawEggRobo(shell, c, 51, 96);
            f.drawCentered(c, "EGG ROBO", 51, 136, Colors.TEXT_BAD);
            f.drawCentered(c, "(OFF DUTY)", 51, 144, Colors.TEXT_DIM);
        }
        if (line != null) {
            Gfx.panel(c, 4, 160, 94, 32, 0xF0FFFFFF, Colors.BLACK);
            int ly = 164;
            for (String l : f.wrap(line, 86)) {
                f.draw(c, l, 8, ly, 0xFF101010);
                ly += SmallFont.LINE;
            }
        }
        f.drawShadowed(c, "CARDS", 104, 27, Colors.GOLD);
        f.drawShadowed(c, "RELICS & MONITORS", 212, 114, Colors.GOLD);
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
                // Shown full size in the left column instead.
            } else if (focusItem.relicId() != null) {
                Relic r = shell.catalog.newRelic(focusItem.relicId());
                screen.tooltip(r.name().toUpperCase(), r.description(), Math.min(focusSpot.x(), w - 150),
                        focusSpot.y() + 40);
            } else {
                var p = focusItem.potion();
                screen.tooltip(p.name().toUpperCase(), p.describe(shell.run.state().potionPotency(p)),
                        Math.min(focusSpot.x(), w - 150), focusSpot.y() + 40);
            }
        }
    }

    /** The Egg Robo as ChildObjDat_919D0 builds it: gun arm and legs behind the hovering body. */
    private static void drawEggRobo(Shell shell, SceneCanvas c, float x, float y) {
        float bob = (float) Math.sin(shell.ticks * 0.05) * 3;
        SceneSprite arm = shell.art.romFrame("egg_robo", 2);
        SceneSprite legs = shell.art.romFrame("egg_robo", 5);
        SceneSprite body = shell.art.romFrame("egg_robo", shell.ticks % 2 == 0 ? 1 : 3);
        if (body == null) {
            c.draw(shell.art.icon("node_shop"), x - 12, y - 12, SceneDraw.plain().withScale(2));
            return;
        }
        SceneDraw style = SceneDraw.plain();
        if (arm != null) {
            c.draw(arm, x - 0x1C, y - 4 + bob, style);
        }
        if (legs != null) {
            c.draw(legs, x - 0xC, y + 0x1C + bob, style);
        }
        c.draw(body, x, y + bob, style);
    }
}
