package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.run.TreasureRoom;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;
import slaytherobotnik.ui.Hotspots;

/** An item capsule waiting to be opened. */
final class TreasureView implements RunScreen.RoomView {
    private final TreasureRoom room;
    private final Hotspots spots = new Hotspots();
    private int opening;

    TreasureView(TreasureRoom room) {
        this.room = room;
    }

    @Override
    public void update(Shell shell, RunScreen screen) {
        if (opening > 0) {
            if (--opening == 0) {
                room.open();
            }
            return;
        }
        spots.clear();
        int w = shell.width();
        spots.add("open", w / 2 - 50, 60, 100, 120);
        spots.add("leave", w - 90, 196, 80, 16);
        String picked = spots.update(shell.in);
        if ("open".equals(picked)) {
            opening = 30;
            shell.sfx(Sounds.SFX_BREAK);
        } else if ("leave".equals(picked)) {
            room.skip();
        }
    }

    @Override
    public void draw(Shell shell, RunScreen screen, SceneCanvas c) {
        int w = shell.width();
        Backdrops.zone(shell, c, shell.run.act().zone(), shell.run.act().zoneAct(), shell.ticks / 8);
        c.fill(0, 0, w, shell.height(), 0x50000020);
        var f = shell.font;
        String title = room.size().toUpperCase() + " EGG CAPSULE";
        f.drawOutlined(c, title, (w - f.width(title) * 2) / 2, 40, Colors.GOLD, 2);
        float scale = switch (room.size()) {
            case TreasureRoom.LARGE -> 2f;
            case TreasureRoom.MEDIUM -> 1.5f;
            default -> 1f;
        };
        boolean open = opening > 0 || room.opened();
        float jiggle = opening > 15 ? (float) Math.sin(opening * 1.3) * 2 : 0;
        float cx = w / 2f + jiggle;
        // Keep the button clear of the title: the capsule spans about -$2A..+$20 around its centre.
        float cy = 60 + 0x2A * scale;
        // Obj_EggCapsule: the capsule (frame 0, opened 1) with its button (5, pressed $C) at (0,-$24).
        SceneSprite capsule = shell.art.romFrame("egg_capsule", open ? 1 : 0);
        SceneSprite button = shell.art.romFrame("egg_capsule", open ? 0x0C : 5);
        if (capsule != null) {
            SceneDraw style = SceneDraw.plain().withScale(scale)
                    .withFlash(opening > 20 ? Colors.alpha(Colors.WHITE, (opening - 20) / 10f) : 0);
            c.draw(capsule, cx, cy, style);
            if (button != null) {
                c.draw(button, cx, cy - 0x24 * scale, style);
            }
        } else {
            var chest = shell.art.icon("node_treasure");
            c.draw(chest, cx - chest.width() * 1.5f, cy - chest.height() * 1.5f, SceneDraw.plain().withScale(3));
        }
        if (open) {
            // The freed animals flap out and away, as from a boss's capsule.
            long t = 30 - opening + (room.opened() ? shell.ticks % 600 : 0);
            for (int i = 0; i < 6; i++) {
                float fx = cx + (i - 2.5f) * 10 + (float) Math.sin(i * 1.7) * 4 + (i % 2 == 0 ? 1 : -1) * t * 0.8f;
                float fy = cy - 10 - t * (1.0f + (i % 3) * 0.25f);
                if (fy > 20) {
                    SceneSprite flicky = shell.art.romFrame("flicky", (int) ((t / 4 + i) % 2));
                    if (flicky != null) {
                        c.draw(flicky, fx, fy, SceneDraw.plain().withFlipX(i % 2 == 0));
                    }
                }
            }
        }
        if (!open) {
            if (spots.isFocused("open")) {
                int half = Math.round(0x24 * scale) + 6;
                Gfx.focusFrame(c, w / 2 - half, Math.round(cy - 0x2C * scale), half * 2, Math.round(0x4E * scale),
                        shell.ticks);
            }
            f.drawCentered(c, "PRESS THE BUTTON!", w / 2, Math.min(212, Math.round(cy + 0x20 * scale + 6)), Colors.TEXT);
        }
        Hotspots.Spot leave = spots.spot("leave");
        if (leave != null) {
            Gfx.button(c, f, "LEAVE", leave.x(), leave.y(), leave.w(), leave.h(), spots.isFocused("leave"), true,
                    shell.ticks);
        }
    }
}
