package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
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
        spots.add("open", w / 2 - 40, 80, 80, 70);
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
        Backdrops.zone(shell, c, shell.run.act().zone(), shell.ticks / 8);
        c.fill(0, 0, w, shell.height(), 0x50000020);
        var f = shell.font;
        String title = room.size().toUpperCase() + " ITEM CAPSULE";
        f.drawOutlined(c, title, (w - f.width(title) * 2) / 2, 40, Colors.GOLD, 2);
        int scale = switch (room.size()) {
            case TreasureRoom.LARGE -> 4;
            case TreasureRoom.MEDIUM -> 3;
            default -> 3;
        };
        var chest = shell.art.icon("node_treasure");
        float jiggle = opening > 0 ? (float) Math.sin(opening * 1.3) * 2 : 0;
        c.draw(chest, w / 2f - chest.width() * scale / 2f + jiggle, 115 - chest.height() * scale / 2f,
                SceneDraw.plain().withScale(scale).withFlash(opening > 0 ? Colors.alpha(Colors.WHITE, (30 - opening) / 30f) : 0));
        if (spots.isFocused("open")) {
            Gfx.focusFrame(c, w / 2 - 40, 80, 80, 70, shell.ticks);
        }
        f.drawCentered(c, "OPEN IT!", w / 2, 160, Colors.TEXT);
        Hotspots.Spot leave = spots.spot("leave");
        if (leave != null) {
            Gfx.button(c, f, "LEAVE", leave.x(), leave.y(), leave.w(), leave.h(), spots.isFocused("leave"), true,
                    shell.ticks);
        }
    }
}
