package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
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
    /** Ticks left of the Starpost ball's orbit. */
    private int spin;
    /** The row the stage's floor sits on: the hero and the Starpost stand above the options. */
    private static final int GROUND = 130;
    /**
     * Obj_StarPost (sonic3k.asm loc_2D12E): the touched post's ball orbits for $20 frames, its
     * angle stepping -$10 a frame from -$40, 12 pixels ($C00 * sine >> 16) around a point $14
     * above the post; then the post flashes frames 0 and 4 every 4 frames (Ani_Starpost_Spinning).
     */
    private static final int ORBIT_FRAMES = 0x20;

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
            spin = ORBIT_FRAMES;
            shell.sfx(Sounds.SFX_STARPOST);
        }
    }

    /** The ROM's Starpost standing at {@code x} on {@code ground}: idle, its ball orbiting, then flashing. */
    private void drawStarpost(Shell shell, SceneCanvas c, int x, int ground) {
        SceneSprite idle = shell.art.romFrame("starpost", 0);
        if (idle == null) {
            var icon = shell.art.icon("node_rest");
            c.draw(icon, x - icon.width() * 1.5f, ground - icon.height() * 3, SceneDraw.plain().withScale(3));
            return;
        }
        // Every frame shares the object's origin; the idle post's bottom stands on the ground.
        float originY = ground - (idle.height() - idle.originY());
        int frame = spin > 0 ? 1 : room.chosen() != null && (shell.ticks / 4) % 2 == 1 ? 4 : 0;
        c.draw(shell.art.romFrame("starpost", frame), x, originY, SceneDraw.plain());
        if (spin > 0) {
            int angle = (-0x10 * (ORBIT_FRAMES - spin) - 0x40) & 0xFF;
            double radians = angle * Math.PI * 2 / 256;
            c.draw(shell.art.romFrame("starpost", 2), Math.round(x + Math.cos(radians) * 12),
                    Math.round(originY - 0x14 + Math.sin(radians) * 12), SceneDraw.plain());
        }
    }

    @Override
    public void draw(Shell shell, RunScreen screen, SceneCanvas c) {
        int w = shell.width();
        LevelStages.Placement stage = LevelStages.draw(shell, c, GROUND);
        c.fill(0, 0, w, shell.height(), 0x30000020);
        SmallFont f = shell.font;
        f.drawOutlined(c, "STARPOST", (w - f.width("STARPOST") * 2) / 2, 36, Colors.GOLD, 2);
        drawStarpost(shell, c, w / 2 + 16, LevelStages.feet(stage, w / 2 + 16, GROUND));
        int heroX = w / 2 - 40;
        Poses.hero(shell, c, shell.run.state().character().id(),
                room.chosen() != null && room.chosen().equals("rest") ? Poses.DUCK : Poses.WAIT, shell.ticks,
                heroX, LevelStages.feet(stage, heroX, GROUND), SceneDraw.plain());
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
