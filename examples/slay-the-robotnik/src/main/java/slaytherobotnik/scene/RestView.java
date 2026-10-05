package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import java.util.List;
import slaytherobotnik.core.RestOption;
import slaytherobotnik.run.RestRoom;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;
import slaytherobotnik.ui.Hotspots;
import slaytherobotnik.ui.SmallFont;

/** A Starpost: rest, tune up a card, or use a relic's option. */
final class RestView implements RunScreen.RoomView {
    private final RestRoom room;
    private final Hotspots spots = new Hotspots();
    private int spin;

    RestView(RestRoom room) {
        this.room = room;
    }

    private void layout(Shell shell) {
        spots.clear();
        int w = shell.width();
        if (room.chosen() != null) {
            spots.add("go", w / 2 - 50, 196, 100, 16);
            return;
        }
        List<RestOption> options = room.options();
        int bw = 96;
        int total = options.size() * bw + (options.size() - 1) * 10;
        for (int i = 0; i < options.size(); i++) {
            spots.add("o" + i, (w - total) / 2 + i * (bw + 10), 140, bw, 44, options.get(i).enabled());
        }
    }

    @Override
    public void update(Shell shell, RunScreen screen) {
        if (spin > 0) {
            spin--;
        }
        layout(shell);
        String picked = spots.update(shell.in);
        if (picked == null) {
            return;
        }
        if (picked.equals("go")) {
            shell.sfx(Sounds.SFX_SPRING);
            room.proceed();
            return;
        }
        RestOption option = room.options().get(Integer.parseInt(picked.substring(1)));
        if (room.choose(option.id())) {
            spin = 60;
            shell.sfx(Sounds.SFX_STARPOST);
        }
    }

    @Override
    public void draw(Shell shell, RunScreen screen, SceneCanvas c) {
        int w = shell.width();
        Backdrops.zone(shell, c, shell.run.act().zone(), shell.ticks / 8);
        c.fill(0, 0, w, shell.height(), 0x50000020);
        SmallFont f = shell.font;
        f.drawOutlined(c, "STARPOST", (w - f.width("STARPOST") * 2) / 2, 36, Colors.GOLD, 2);
        // The Starpost, spinning when used.
        var post = shell.art.icon("node_rest");
        float angle = spin > 0 ? spin * 0.4f : 0;
        c.draw(post, w / 2f - post.width() * 1.5f, 60, SceneDraw.plain().withScale(3));
        if (spin > 0) {
            for (int i = 0; i < 6; i++) {
                double a = angle + i * Math.PI / 3;
                int sx = (int) (w / 2 + Math.cos(a) * 26);
                int sy = (int) (75 + Math.sin(a) * 10);
                c.fill(sx - 1, sy - 1, 3, 3, i % 2 == 0 ? 0xFFFFDA24 : 0xFF6CDAFF);
            }
        }
        Poses.hero(shell, c, shell.run.state().character().id(),
                room.chosen() != null && room.chosen().equals("rest") ? Poses.DUCK : Poses.WAIT, shell.ticks,
                w / 2f - 40, 130, SceneDraw.plain());
        layout(shell);
        if (room.chosen() == null) {
            for (int i = 0; i < room.options().size(); i++) {
                RestOption o = room.options().get(i);
                Hotspots.Spot s = spots.spot("o" + i);
                boolean focus = spots.isFocused(s.id());
                Gfx.button(c, f, "", s.x(), s.y(), s.w(), s.h(), focus, o.enabled(), shell.ticks);
                f.drawCentered(c, o.label().toUpperCase(), s.x() + s.w() / 2, s.y() + 6,
                        o.enabled() ? Colors.GOLD : Colors.TEXT_DIM);
                int ly = s.y() + 16;
                for (String line : f.wrap(o.detail(), s.w() - 8)) {
                    f.drawCentered(c, line, s.x() + s.w() / 2, ly, o.enabled() ? Colors.TEXT : Colors.TEXT_DIM);
                    ly += SmallFont.LINE;
                }
            }
        } else {
            String done = switch (room.chosen()) {
                case "rest" -> "YOU CATCH YOUR BREATH.";
                case "smith" -> "GOOD AS NEW - BETTER, EVEN.";
                default -> "DONE.";
            };
            f.drawCentered(c, done, w / 2, 150, Colors.TEXT);
            Hotspots.Spot go = spots.spot("go");
            Gfx.button(c, f, "PROCEED", go.x(), go.y(), go.w(), go.h(), spots.isFocused("go"), true, shell.ticks);
        }
    }
}
