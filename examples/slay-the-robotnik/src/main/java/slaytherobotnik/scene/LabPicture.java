package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.ui.Colors;

/**
 * Robotnik's Lab, in a room of his base: three bubbling specimen tubes on a counter, each with
 * an item monitor (this mod's potions) floating inside, murky with static. [Search]: the tubes
 * drain one after another (gurgling with sfx_Bubble), their glass lifts, and each monitor drops
 * to the floor the way a knocked monitor falls (Obj_MonitorFall: MoveSprite's $38 gravity, its
 * speed cleared when it lands). On the floor they show the potions found, flickering through
 * static as Ani_Monitor does. The tubes stay empty and open.
 */
final class LabPicture extends TimedPicture {
    /** Mod timing: tube i starts draining at 8 + 16i, drains for 30 frames, its glass lifts for 12. */
    private static final int FIRST = 8;
    private static final int STAGGER = 16;
    private static final int DRAIN = 30;
    private static final int LIFT = 12;
    private static final int TUBE_H = 66;
    private static final int TUBE_W = 34;

    private String[] faces = new String[0];
    private RomBody[] falling = new RomBody[3];
    private boolean[] landed = new boolean[3];
    private int ground = 36 + EventPicture.HEIGHT - 22;
    private int counterTop;
    private int[] tubeX = new int[3];

    LabPicture(Shell shell) {
    }

    @Override
    void begin(Shell shell, String verb, String argument) {
        String[] ids = argument.isEmpty() ? new String[0] : argument.split(",");
        faces = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            faces[i] = HudIcons.monitorFace(ids[i]);
        }
        falling = new RomBody[3];
        landed = new boolean[3];
    }

    @Override
    boolean playing() {
        for (int i = 0; i < faces.length && i < 3; i++) {
            if (!landed[i]) {
                return true;
            }
        }
        return clock < FIRST + 2 * STAGGER + DRAIN + LIFT + 16;
    }

    private int drainStart(int i) {
        return FIRST + i * STAGGER;
    }

    @Override
    void step(Shell shell) {
        if (detail == null) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            int start = drainStart(i);
            if (clock == start) {
                shell.sfx(Sounds.SFX_BUBBLE);
            }
            if (clock == start + DRAIN) {
                shell.sfx(Sounds.SFX_SWITCH);
            }
            if (i >= faces.length) {
                continue;
            }
            if (clock == start + DRAIN + LIFT && falling[i] == null) {
                falling[i] = new RomBody(tubeX[i], floatY(i, 0), 0, 0);
            }
            RomBody m = falling[i];
            if (m != null && !landed[i]) {
                // Obj_MonitorFall: MoveSprite; once moving down and at the floor, stop there.
                m.moveSprite();
                if (m.yVel >= 0 && m.py() + 16 >= ground) {
                    m.y = (ground - 16) * 256;
                    m.yVel = 0;
                    landed[i] = true;
                }
            }
        }
    }

    /** The centre row of a monitor floating in tube {@code i}. */
    private float floatY(int i, long t) {
        return counterTop - TUBE_H / 2f - 2 + (float) Math.sin(t * 0.05 + i * 2.1) * 2;
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        ground = y + h - 22;
        counterTop = ground - 36;
        BaseInterior.draw(c, x, y, w, h, ground, 0xFF10242C, 0xFF20383C);
        // The counter.
        c.fill(x + 2, counterTop, w - 4, ground - 5 - counterTop, 0xFF384858);
        c.fill(x + 2, counterTop, w - 4, 2, 0xFF8C9CB0);
        c.fill(x + 6, counterTop + 8, w - 12, 1, 0xFF283440);
        int[] liquids = {0xFF24DA48, 0xFFDA2490, 0xFF2490FF};
        for (int i = 0; i < 3; i++) {
            tubeX[i] = x + 25 + i * 38;
            drawTube(shell, c, i, tubeX[i], liquids[i], t);
        }
        for (int i = 0; i < faces.length && i < 3; i++) {
            RomBody m = falling[i];
            if (m != null) {
                drawItemMonitor(shell, c, landed[i] ? faces[i] : "static", tubeX[i], m.py(), 1f, t + i * 5);
            }
        }
    }

    private void drawTube(Shell shell, SceneCanvas c, int i, int cx, int liquid, long t) {
        int left = cx - TUBE_W / 2;
        int top = counterTop - TUBE_H;
        int start = drainStart(i);
        boolean searching = detail != null;
        float full = 0.85f;
        float level = full;
        int lift = 0;
        if (searching) {
            int d = clock - start;
            level = d <= 0 ? full : Math.max(0f, full * (1f - d / (float) DRAIN));
            lift = Math.max(0, Math.min(LIFT, d - DRAIN)) * (TUBE_H - 8) / LIFT;
        }
        // Pipe up to the ceiling and the tube's caps.
        c.fill(cx - 3, top - 22, 6, 22, 0xFF485868);
        c.fill(cx - 2, top - 22, 1, 22, 0xFF8C9CB0);
        c.fill(left - 2, top - 6, TUBE_W + 4, 6, 0xFF6C7C8C);
        c.fill(left - 2, counterTop - 5, TUBE_W + 4, 5, 0xFF6C7C8C);
        int inside = TUBE_H - 5;
        // The specimen: a monitor in the murk, until it drops out.
        boolean holding = i < faces.length ? falling[i] == null : true;
        if (holding) {
            drawItemMonitor(shell, c, "static", cx, floatY(i, t), 1f, t + i * 7);
        }
        int liquidH = Math.round(inside * level);
        if (liquidH > 0) {
            c.fill(left + 1, counterTop - 5 - liquidH, TUBE_W - 2, liquidH, Colors.alpha(liquid, 0.55f));
            c.fill(left + 1, counterTop - 5 - liquidH, TUBE_W - 2, 1, Colors.alpha(Colors.WHITE, 0.5f));
            for (int b = 0; b < 4; b++) {
                int rise = (int) ((t * (searching ? 2 : 1) + b * 17 + i * 9) % Math.max(1, liquidH));
                c.fill(left + 5 + b * 7, counterTop - 7 - rise, 2, 2, Colors.alpha(Colors.WHITE, 0.8f));
            }
        }
        // The glass: a faint pane with a highlight, lifting into the top cap once drained.
        int glassH = inside - lift;
        if (glassH > 0) {
            c.fill(left, top, TUBE_W, glassH, 0x18B6DAFF);
            c.fill(left + 3, top + 2, 2, glassH - 4, 0x60FFFFFF);
            c.fill(left, top, 1, glassH, 0x80B6DAFF);
            c.fill(left + TUBE_W - 1, top, 1, glassH, 0x80B6DAFF);
        }
    }

    /**
     * An item monitor as the ROM animates one: Ani_Monitor shows static (Map_Monitor frames 0
     * and 1) for two frames in every six, the icon otherwise. Faces as {@link HudIcons#monitor};
     * "1UP" shows the hero's life icon, as the real 1-Up monitor does.
     */
    static void drawItemMonitor(Shell shell, SceneCanvas c, String face, float x, float centreY, float scale,
            long t) {
        SceneSprite box = shell.art.romFrame("monitor", 0);
        if (box == null) {
            c.fill(Math.round(x) - 14, Math.round(centreY) - 15, 28, 30, 0xFF6C6C6C);
            return;
        }
        int step = (int) ((t / 2) % 6);
        boolean staticFrame = face.equals("static") || step == 0 || step == 3;
        if (staticFrame) {
            c.draw(shell.art.romFrame("monitor", step >= 3 ? 1 : 0), x, centreY, SceneDraw.plain().withScale(scale));
            return;
        }
        if (face.equals("1UP")) {
            SceneSprite icon = shell.art.romFrame("life_icon_" + hero(shell), 2);
            if (icon != null) {
                c.draw(box, x, centreY, SceneDraw.plain().withScale(scale));
                c.draw(icon, x, centreY, SceneDraw.plain().withScale(scale));
                return;
            }
        }
        HudIcons.monitor(shell, c, face, x, centreY, scale, t);
    }
}
