package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import java.util.List;
import slaytherobotnik.run.StartRoom;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;
import slaytherobotnik.ui.Hotspots;
import slaytherobotnik.ui.SmallFont;

/** The Tornado flight over the act's zone, with the cargo-hold bonuses. */
final class StartView implements RunScreen.RoomView {
    private final StartRoom room;
    private final Hotspots spots = new Hotspots();
    private int age;

    StartView(StartRoom room) {
        this.room = room;
    }

    @Override
    public boolean showsHud() {
        return true;
    }

    private void layout(Shell shell) {
        spots.clear();
        int w = shell.width();
        if (room.done()) {
            spots.add("go", w / 2 - 50, 196, 100, 16);
            return;
        }
        List<StartRoom.Option> options = room.options();
        int y = 104;
        for (int i = 0; i < options.size(); i++) {
            spots.add("opt" + i, 40, y, w - 80, 20);
            y += 22;
        }
    }

    @Override
    public void update(Shell shell, RunScreen screen) {
        age++;
        if (age < 30) {
            return;
        }
        layout(shell);
        String before = spots.focused();
        String picked = spots.update(shell.in);
        if (before != null && !before.equals(spots.focused())) {
            shell.sfx(Sounds.SFX_CURSOR);
        }
        if (picked == null) {
            return;
        }
        if (picked.equals("go")) {
            shell.sfx(Sounds.SFX_SPRING);
            room.proceed();
            return;
        }
        room.choose(Integer.parseInt(picked.substring(3)));
        shell.sfx(Sounds.SFX_SUPER_EMERALD);
    }

    @Override
    public void draw(Shell shell, RunScreen screen, SceneCanvas c) {
        int w = shell.width();
        long t = shell.ticks;
        Backdrops.zone(shell, c, shell.run.act().zone(), shell.run.act().zoneAct(), t * 3);
        // The Tornado crossing the sky, with the hero standing on the wing.
        float bob = (float) Math.sin(t * 0.06) * 3;
        int px = 110 + (int) (Math.sin(t * 0.013) * 30);
        int py = 66 + Math.round(bob);
        SceneSprite plane = shell.art.romFrame("tornado", 0);
        String who = shell.run.state().character().id();
        if (plane != null) {
            // The plane art includes Tails at the controls, so in a Tails run Sonic rides the wing.
            // In the AIZ intro the plane sits at (rider -$22, rider +$2C).
            String rider = who.equals("tails") ? "sonic" : who;
            Poses.hero(shell, c, rider, Poses.WAIT, t, px + 0x22, py - 0x2C + 24, SceneDraw.plain());
            c.draw(plane, px, py, SceneDraw.plain());
            int[] propeller = {1, 2, 3, 4, 3, 2};
            c.draw(shell.art.romFrame("tornado", propeller[(int) (t % propeller.length)]), px + 0x38, py + 4,
                    SceneDraw.plain());
            c.draw(shell.art.romFrame("tornado", t % 2 == 0 ? 5 : 6), px + 0x18, py + 0x18, SceneDraw.plain());
        } else {
            c.fill(px - 30, py - 4, 60, 8, 0xFFDA2424);
            Poses.hero(shell, c, who, Poses.WAIT, t, px - 4, py, SceneDraw.plain());
        }
        SmallFont f = shell.font;
        String zone = shell.run.act().zoneName().toUpperCase() + " ZONE";
        f.drawOutlined(c, shell.run.act().name().toUpperCase(), w - 20 - f.width(shell.run.act().name()) * 2,
                RunScreen.HUD_HEIGHT + 8, Colors.WHITE, 2);
        f.drawOutlined(c, zone, w - 20 - f.width(zone) * 2, RunScreen.HUD_HEIGHT + 22, Colors.GOLD, 2);
        if (age < 30) {
            return;
        }
        Gfx.panel(c, 30, 82, w - 60, room.done() ? 36 : 26 + room.options().size() * 22);
        List<String> speech = f.wrap(room.done() ? "\"Hold on tight - we're going in!\"" : room.speech(), w - 80);
        int y = 87;
        for (String line : speech) {
            f.drawShadowed(c, line, 40, y, Colors.TEXT);
            y += SmallFont.LINE;
        }
        layout(shell);
        if (!room.done()) {
            for (int i = 0; i < room.options().size(); i++) {
                Hotspots.Spot s = spots.spot("opt" + i);
                StartRoom.Option o = room.options().get(i);
                boolean focus = spots.isFocused(s.id());
                Gfx.button(c, f, "", s.x(), s.y(), s.w(), s.h(), focus, true, t);
                f.drawShadowed(c, o.label().toUpperCase(), s.x() + 6, s.y() + 4, Colors.GOLD);
                f.drawShadowed(c, o.detail().toUpperCase(), s.x() + 6, s.y() + 12, Colors.TEXT);
            }
        } else {
            Hotspots.Spot go = spots.spot("go");
            Gfx.button(c, f, "LAND!", go.x(), go.y(), go.w(), go.h(), spots.isFocused("go"), true, t);
        }
    }
}
