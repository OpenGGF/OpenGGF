package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;

/**
 * Event illustrations, keyed by {@link slaytherobotnik.core.EventDef#art()}. Each is a small
 * scene in a 126x146 panel: ROM sprites where the game has the thing (monitors, rings, Giant
 * Rings, Flickies, emeralds, the Egg Mobile) and simple pixel shapes for the rest.
 */
final class EventArt {
    static final int SKY_TOP = 0xFF246CB6;
    static final int SKY_BOTTOM = 0xFF0A1C48;
    static final int CAVE_TOP = 0xFF1C1424;
    static final int CAVE_BOTTOM = 0xFF3C2C24;
    static final int LAB_TOP = 0xFF102030;
    static final int LAB_BOTTOM = 0xFF203848;
    static final int GRASS = 0xFF24A048;
    static final int GRASS_DARK = 0xFF146C24;
    static final int STONE = 0xFF8C7C6C;
    static final int STONE_DARK = 0xFF5C4C40;
    static final int GOLD = 0xFFFFDA24;
    static final int GOLD_DARK = 0xFFB48C00;

    private EventArt() {
    }

    /** Draws event art {@code art} (pictures with their own class draw themselves; see {@link EventPictures}). */
    static void draw(Shell shell, SceneCanvas c, String art, int x, int y, int w, int h) {
        long t = shell.ticks;
        int cx = x + w / 2;
        int ground = y + h - 22;
        switch (art) {
            case "event:giant_ring" -> {
                level(shell, c, x, y, w, h, ground);
                SceneSprite ring = bigRing(shell, t);
                if (ring != null) {
                    Poses.centre(c, ring, cx, y + 56 + bob(t, 3), SceneDraw.plain());
                } else {
                    drawnRing(c, cx, y + 56, 30);
                }
                sparkles(shell, c, cx, y + 56, 40, t);
            }
            case "event:medic" -> {
                outdoors(c, x, y, w, h, ground);
                Poses.stand(c, shell.art.romFrame("starpost", 0), cx + 22, ground, SceneDraw.plain());
                // A first-aid box: white case, red cross.
                c.fill(cx - 34, ground - 18, 30, 18, Colors.BLACK);
                c.fill(cx - 33, ground - 17, 28, 16, Colors.WHITE);
                c.fill(cx - 21, ground - 15, 4, 12, 0xFFDA2424);
                c.fill(cx - 25, ground - 11, 12, 4, 0xFFDA2424);
                hearts(c, cx - 19, ground - 28, t);
            }
            case "event:monitor_row" -> {
                outdoors(c, x, y, w, h, ground);
                String[] faces = {"4", "1UP", "3"};
                for (int i = 0; i < 3; i++) {
                    monitor(shell, c, cx - 36 + i * 36, ground, faces[i], t + i * 7);
                }
            }
            case "event:emerald_altar" -> {
                cave(c, x, y, w, h, ground);
                pedestal(c, cx, ground, 30, 40);
                emerald(shell, c, cx, ground - 48 + bob(t, 2), 0, t);
                // A boulder lurking in the dark at the top of the corridor.
                c.fill(x + 6, y + 10, 26, 22, 0xFF3C3028);
                c.fill(x + 8, y + 12, 18, 6, 0xFF5C4C40);
            }
            case "event:mural" -> {
                cave(c, x, y, w, h, ground);
                c.fill(x + 10, y + 14, w - 20, 82, STONE_DARK);
                c.fill(x + 12, y + 16, w - 24, 78, STONE);
                Poses.hero(shell, c, "sonic", Poses.VICTORY, 0, cx, y + 86,
                        SceneDraw.plain().withScale(1.5f).withFlash(0xE0000000 | (GOLD & 0xFFFFFF)));
                for (int i = 0; i < 3; i++) {
                    emerald(shell, c, cx - 32 + i * 32, ground - 4, i * 2, t + i * 10);
                }
            }
            case "event:scrapyard" -> {
                outdoors(c, x, y, w, h, ground);
                // A heap of wrecked badniks, upside down and dimmed.
                SceneDraw wreck = SceneDraw.plain().withTint(0xFF8C8C8C);
                Poses.stand(c, shell.art.romFrame("rhinobot", 0), cx - 20, ground, wreck.withFlipY(true));
                Poses.stand(c, shell.art.romFrame("monkey_dude", 0), cx + 18, ground, wreck);
                Poses.stand(c, shell.art.romFrame("caterkiller_jr", 1), cx - 2, ground - 16, wreck.withFlipX(true));
                Poses.stand(c, shell.art.romFrame("bloominator", 0), cx + 4, ground - 4, wreck.withFlipY(true));
                SceneSprite smoke = shell.art.romFrame("explosion", (int) ((t / 6) % 5));
                if (smoke != null) {
                    Poses.centre(c, smoke, cx + 6, ground - 40 - (t / 3) % 10, SceneDraw.plain().withAlpha(0.7f));
                }
                if ((t / 20) % 6 == 0) {
                    c.fill(cx - 2, ground - 22, 3, 3, 0xFFFF4848); // something twitches
                }
            }
            case "event:mushrooms" -> {
                outdoors(c, x, y, w, h, ground);
                mushroom(c, cx - 30, ground, 18, 30, 0xFFDA4890, t);
                mushroom(c, cx + 26, ground, 22, 38, 0xFF9048DA, t + 15);
                mushroom(c, cx, ground, 14, 20, 0xFFFF9024, t + 30);
                int eyes = (int) ((t / 30) % 4);
                if (eyes != 3) {
                    c.fill(cx - 5, ground - 8, 2, 2, Colors.WHITE);
                    c.fill(cx + 3, ground - 8, 2, 2, Colors.WHITE);
                }
            }
            case "event:ring_shrine" -> {
                outdoors(c, x, y, w, h, ground);
                pedestal(c, cx, ground, 40, 20);
                for (int i = 0; i < 8; i++) {
                    double a = t * 0.02 + i * Math.PI / 4;
                    ring(shell, c, cx + (int) (Math.cos(a) * 34), y + 54 + (int) (Math.sin(a) * 26), t + i * 2);
                }
                for (int i = 0; i < 5; i++) {
                    ring(shell, c, cx - 14 + i * 7, ground - 28 - (i % 2) * 4, t + i * 3);
                }
            }
            case "event:workbench" -> {
                indoors(c, x, y, w, h, ground, 0xFF4C3424, 0xFF2C1C14);
                // Bench, tools and a spring on top.
                c.fill(cx - 44, ground - 26, 88, 6, 0xFF8C5C2C);
                c.fill(cx - 40, ground - 20, 4, 20, 0xFF6C4420);
                c.fill(cx + 36, ground - 20, 4, 20, 0xFF6C4420);
                Poses.stand(c, shell.art.romFrame("spring", 0), cx + 20, ground - 26, SceneDraw.plain());
                c.fill(cx - 30, ground - 30, 14, 3, 0xFFB6B6B6);  // spanner
                c.fill(cx - 32, ground - 32, 4, 7, 0xFFB6B6B6);
                c.fill(cx - 8, ground - 34, 3, 8, 0xFFDA2424);    // screwdriver
                c.fill(cx - 8, ground - 30, 3, 4, 0xFFFFDA24);
                Poses.hero(shell, c, "tails", Poses.WAIT, t, cx - 26, ground, SceneDraw.plain().withAlpha(
                        shell.run != null && "tails".equals(shell.run.state().character().id()) ? 1f : 0.25f));
            }
            case "event:waterfall" -> {
                outdoors(c, x, y, w, h, ground);
                c.fill(cx - 20, y, 40, ground - y, 0xFF2448B6);
                for (int i = 0; i < 6; i++) {
                    int sy = (int) ((t * 3 + i * 23) % (ground - y));
                    c.fill(cx - 18 + i * 6, y + sy, 2, 14, 0xFFB6DAFF);
                }
                c.fill(x, ground - 4, w, 8, 0xFF4890FF);
                for (int i = 0; i < 4; i++) {
                    int px = cx - 30 + (int) ((t + i * 17) % 60);
                    c.fill(px, ground - 6, 3, 2, Colors.WHITE);
                }
            }
            case "event:special_stage" -> {
                Gfx.gradient(c, x, y, w, h, 0xFF000024, 0xFF200048);
                stars(c, x, y, w, h, t);
                checkerSphere(c, cx, y + 64, 26, t);
                SceneSprite ring = bigRing(shell, t);
                if (ring != null) {
                    Poses.centre(c, ring, cx, y + 64, SceneDraw.plain().withScale(0.9f).withAlpha(0.8f));
                }
            }
            case "event:mirror_monitor" -> {
                indoors(c, x, y, w, h, ground, LAB_TOP, LAB_BOTTOM);
                String hero = shell.run != null ? shell.run.state().character().id() : "sonic";
                Poses.hero(shell, c, hero, Poses.WAIT, t, cx - 22, ground - 26, SceneDraw.plain());
                Poses.hero(shell, c, hero, Poses.WAIT, t, cx + 22, ground - 26,
                        SceneDraw.plain().withFlipX(true).withTint(0xA0B6DAFF));
                monitor(shell, c, cx, ground, "1UP", t);
            }
            case "event:clogged_pipe" -> {
                indoors(c, x, y, w, h, ground, LAB_TOP, LAB_BOTTOM);
                c.fill(x, y + 50, w, 30, 0xFF485868);
                c.fill(x, y + 54, w, 22, 0xFF6C7C8C);
                c.fill(cx - 16, y + 46, 32, 38, 0xFF384858);
                c.fill(cx - 12, y + 52, 24, 26, Colors.BLACK);
                if ((t / 15) % 4 != 0) {
                    c.fill(cx - 2, y + 64, 4, 4, GOLD);
                    c.fill(cx - 1, y + 63, 2, 1, Colors.WHITE);
                }
                for (int i = 0; i < 3; i++) {
                    int d = (int) ((t + i * 20) % 60);
                    c.fill(cx - 10 + i * 10, y + 84 + d, 3, 3, 0xFF6C9048);
                }
            }
            case "event:campfire" -> {
                Gfx.gradient(c, x, y, w, h, 0xFF0A1030, 0xFF241848);
                stars(c, x, y, w, h, t);
                c.fill(x, ground, w, h - (ground - y), GRASS_DARK);
                fire(c, cx, ground, t);
                for (int i = 0; i < 4; i++) {
                    double a = Math.PI * (0.15 + i * 0.23);
                    int fx = cx + (int) (Math.cos(a) * 42) * (i % 2 == 0 ? 1 : -1);
                    SceneSprite flicky = shell.art.romFrame("flicky", (int) (((t + i * 5) / 8) % 2));
                    Poses.stand(c, flicky, fx, ground + 4, SceneDraw.plain().withFlipX(fx < cx));
                }
            }
            case "event:terminal" -> {
                indoors(c, x, y, w, h, ground, LAB_TOP, LAB_BOTTOM);
                c.fill(cx - 46, y + 20, 92, 70, 0xFF6C7C8C);
                c.fill(cx - 42, y + 24, 84, 58, 0xFF001800);
                for (int i = 0; i < 9; i++) {
                    int len = 10 + (int) (((i * 37 + t / 6) * 13) % 60);
                    c.fill(cx - 38, y + 28 + i * 6, len, 3, 0xFF24DA48);
                }
                if ((t / 20) % 2 == 0) {
                    c.fill(cx - 38 + 4, y + 28 + 54, 4, 3, 0xFF24DA48);
                }
                c.fill(cx - 10, y + 90, 20, 10, 0xFF485868);
                c.fill(cx - 30, ground - 8, 60, 8, 0xFF485868);
            }
            case "event:lab" -> {
                indoors(c, x, y, w, h, ground, LAB_TOP, LAB_BOTTOM);
                int[] liquids = {0xFF24DA48, 0xFFDA2490, 0xFF2490FF, 0xFFFFDA24};
                for (int i = 0; i < 4; i++) {
                    tube(c, x + 12 + i * 28, ground, liquids[i], t + i * 11);
                }
            }
            case "event:chao_cart" -> {
                outdoors(c, x, y, w, h, ground);
                c.fill(cx - 40, ground - 34, 80, 24, 0xFFB46C24);
                c.fill(cx - 42, ground - 40, 84, 6, 0xFFDA4848);
                c.fill(cx - 42, ground - 46, 84, 6, Colors.WHITE);
                c.fill(cx - 30, ground - 10, 10, 10, Colors.BLACK);
                c.fill(cx + 20, ground - 10, 10, 10, Colors.BLACK);
                int[] colours = {0xFFDA2424, 0xFF2490FF, 0xFF24DA48, 0xFFFFDA24};
                for (int i = 0; i < 4; i++) {
                    int bx = cx - 30 + i * 16;
                    c.fill(bx, ground - 54, 8, 12, Colors.BLACK);
                    c.fill(bx + 1, ground - 52, 6, 9, colours[i]);
                    c.fill(bx + 2, ground - 57, 4, 4, 0xFFB6B6B6);
                    if ((t / 10 + i) % 5 == 0) {
                        c.fill(bx + 3, ground - 62, 2, 2, Colors.WHITE);
                    }
                }
                chao(c, cx, ground - 64 + bob(t, 2), t);
            }
            case "event:collector" -> {
                cave(c, x, y, w, h, ground);
                for (int i = 0; i < 7; i++) {
                    ring(shell, c, cx - 36 + i * 12, ground - 4 - (i % 3) * 5, t + i);
                }
                monitor(shell, c, cx - 34, ground - 6, "5", t);
                monitor(shell, c, cx + 34, ground - 6, "8", t);
                // Egg Robo as ChildObjDat_919D0 builds it: gun arm and legs behind the body.
                int rx = cx;
                int ry = ground - 40 + bob(t, 3);
                SceneSprite arm = shell.art.romFrame("egg_robo", 2);
                SceneSprite legs = shell.art.romFrame("egg_robo", 5);
                SceneSprite robo = shell.art.romFrame("egg_robo", t % 2 == 0 ? 1 : 3);
                if (robo != null) {
                    c.draw(arm, rx - 0x1C, ry - 4, SceneDraw.plain());
                    c.draw(legs, rx - 0xC, ry + 0x1C, SceneDraw.plain());
                    c.draw(robo, rx, ry, SceneDraw.plain());
                }
            }
            case "event:hyper_remote" -> {
                indoors(c, x, y, w, h, ground, 0xFF200810, 0xFF481020);
                c.fill(cx - 18, y + 28, 36, 80, Colors.BLACK);
                c.fill(cx - 16, y + 30, 32, 76, 0xFF6C6C6C);
                c.fill(cx - 12, y + 34, 24, 14, 0xFF241C1C);
                int[] buttons = {0xFFDA2424, 0xFFFFDA24, 0xFF2490FF};
                for (int i = 0; i < 3; i++) {
                    boolean lit = (t / 15) % 3 == i;
                    c.fill(cx - 8, y + 56 + i * 16, 16, 10, Colors.BLACK);
                    c.fill(cx - 7, y + 57 + i * 16, 14, 8, lit ? Colors.WHITE : buttons[i]);
                }
                SceneSprite head = shell.art.romFrame("robotnik_ship", (int) ((t / 20) % 2));
                if (head != null) {
                    Poses.centre(c, head, cx, y + 41, SceneDraw.plain().withScale(0.75f));
                }
            }
            case "event:tablets" -> {
                cave(c, x, y, w, h, ground);
                for (int i = 0; i < 3; i++) {
                    int tx = cx - 44 + i * 32;
                    c.fill(tx, y + 26 + i * 4, 24, 60, STONE_DARK);
                    c.fill(tx + 2, y + 28 + i * 4, 20, 56, STONE);
                    for (int k = 0; k < 6; k++) {
                        int glow = (t / 8 + k + i) % 9 == 0 ? 0xFF24DA48 : STONE_DARK;
                        c.fill(tx + 5, y + 34 + i * 4 + k * 8, 6 + (k * 5 + i * 3) % 10, 3, glow);
                    }
                }
                emerald(shell, c, cx, ground - 6, 0, t);
            }
            case "event:robotnik_offer" -> {
                indoors(c, x, y, w, h, ground, LAB_TOP, LAB_BOTTOM);
                c.fill(cx - 44, y + 16, 88, 70, 0xFF485868);
                c.fill(cx - 40, y + 20, 80, 60, 0xFF102040);
                SceneSprite head = shell.art.romFrame("robotnik_ship", (int) ((t / 8) % 2));
                SceneSprite mobile = shell.art.romFrame("robotnik_ship", 5);
                if (mobile != null) {
                    Poses.centre(c, mobile, cx, y + 60, SceneDraw.plain());
                }
                if (head != null) {
                    Poses.centre(c, head, cx, y + 44, SceneDraw.plain());
                }
                // Scanlines over the screen.
                for (int i = 0; i < 30; i++) {
                    c.fill(cx - 40, y + 20 + i * 2, 80, 1, 0x30000000);
                }
                c.fill(cx - 6, y + 90, 12, 18, 0xFF384858);
            }
            default -> {
                Gfx.gradient(c, x, y, w, h, SKY_TOP, SKY_BOTTOM);
                var icon = shell.art.icon("node_event");
                c.draw(icon, x + w / 2f - icon.width() * 1.5f, y + h / 2f - icon.height() * 1.5f,
                        SceneDraw.plain().withScale(3));
            }
        }
    }

    // ------------------------------------------------------------------ backdrops

    /**
     * The act's real level as the picture's setting: the room's stage in the window, its floor
     * on row {@code ground}; the drawn outdoors without ROM art. Returns the placement (window
     * columns to window rows: {@code placement.feet(px - x) + y}) or null.
     */
    static LevelStages.Placement level(Shell shell, SceneCanvas c, int x, int y, int w, int h, int ground) {
        LevelStages.Placement placement = LevelStages.drawWindow(shell, c, x, y, w, h, ground - y);
        if (placement == null) {
            outdoors(c, x, y, w, h, ground);
        }
        return placement;
    }

    static void outdoors(SceneCanvas c, int x, int y, int w, int h, int ground) {
        Gfx.gradient(c, x, y, w, h, SKY_TOP, 0xFF6CB6FF);
        c.fill(x, ground, w, y + h - ground, GRASS);
        c.fill(x, ground, w, 3, 0xFF6CDA48);
        c.fill(x, ground + 8, w, y + h - ground - 8, GRASS_DARK);
    }

    static void cave(SceneCanvas c, int x, int y, int w, int h, int ground) {
        Gfx.gradient(c, x, y, w, h, CAVE_TOP, CAVE_BOTTOM);
        c.fill(x, ground, w, y + h - ground, STONE_DARK);
        for (int i = 0; i < w; i += 16) {
            c.fill(x + i, ground, 15, 7, STONE);
        }
    }

    static void indoors(SceneCanvas c, int x, int y, int w, int h, int ground, int top, int bottom) {
        Gfx.gradient(c, x, y, w, h, top, bottom);
        c.fill(x, ground, w, y + h - ground, 0xFF242C34);
        c.fill(x, ground, w, 2, 0xFF6C7C8C);
    }

    // ------------------------------------------------------------------ props

    static int bob(long t, int amount) {
        return (int) Math.round(Math.sin(t * 0.06) * amount);
    }

    /** A monitor standing on {@code ground}; its face blinks to static now and then, like the ROM's. */
    static void monitor(Shell shell, SceneCanvas c, int x, int ground, String face, long t) {
        SceneSprite box = shell.art.romFrame("monitor", 0);
        if (box == null) {
            c.fill(x - 14, ground - 30, 28, 30, 0xFF6C6C6C);
            return;
        }
        float y = ground - (box.height() - box.originY());
        HudIcons.monitor(shell, c, (t / 4) % 12 == 0 ? "static" : face, x, y, 1f, t);
    }

    /** The Giant Ring turning: Map_SSEntryRing frames 8-11 (0-7 are it forming). */
    static SceneSprite bigRing(Shell shell, long t) {
        return shell.art.romFrame("big_ring", 8 + (int) ((t / 6) % 4));
    }

    static void ring(Shell shell, SceneCanvas c, int x, int y, long t) {
        SceneSprite ring = shell.art.romFrame("ring", (int) ((t / 8) % 4));
        if (ring != null) {
            c.draw(ring, x, y, SceneDraw.plain());
        } else {
            c.fill(x - 4, y - 4, 8, 8, GOLD);
        }
    }

    static void drawnRing(SceneCanvas c, int cx, int cy, int r) {
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI / 24;
            c.fill(cx + (int) (Math.cos(a) * r) - 2, cy + (int) (Math.sin(a) * r) - 2, 4, 4, GOLD);
        }
    }

    static void sparkles(Shell shell, SceneCanvas c, int cx, int cy, int r, long t) {
        for (int i = 0; i < 4; i++) {
            SceneSprite s = shell.art.romFrame("ring", 4 + (int) (((t / 6) + i) % 4));
            double a = t * 0.03 + i * Math.PI / 2;
            int sx = cx + (int) (Math.cos(a) * r);
            int sy = cy + (int) (Math.sin(a) * r * 0.7);
            if (s != null) {
                c.draw(s, sx, sy, SceneDraw.plain());
            } else {
                c.fill(sx, sy, 2, 2, Colors.WHITE);
            }
        }
    }

    static void hearts(SceneCanvas c, int x, int y, long t) {
        int rise = (int) ((t / 3) % 16);
        int col = Colors.alpha(0xFFFF4890, 1f - rise / 16f);
        c.fill(x, y - rise, 2, 2, col);
        c.fill(x + 3, y - rise, 2, 2, col);
        c.fill(x + 1, y + 2 - rise, 3, 2, col);
    }

    static void pedestal(SceneCanvas c, int cx, int ground, int w, int h) {
        c.fill(cx - w / 2 - 4, ground - 6, w + 8, 6, STONE_DARK);
        c.fill(cx - w / 2, ground - h, w, h - 6, STONE);
        c.fill(cx - w / 2 - 3, ground - h - 4, w + 6, 5, STONE_DARK);
    }

    /** One of the intro's Chaos Emeralds (frames 0-6 are the seven colours). */
    static void emerald(Shell shell, SceneCanvas c, int x, int y, int colour, long t) {
        SceneSprite gem = shell.art.romFrame("intro_emeralds", colour);
        if (gem != null) {
            Poses.centre(c, gem, x, y, SceneDraw.plain().withScale(2).withFlash((t / 10) % 8 == 0 ? 0x80FFFFFF : 0));
        } else {
            c.fill(x - 5, y - 4, 10, 8, 0xFF24DA48);
        }
    }

    static void mushroom(SceneCanvas c, int x, int ground, int r, int stalk, int cap, long t) {
        int squash = (int) Math.round(Math.abs(Math.sin(t * 0.05)) * 3);
        c.fill(x - 3, ground - stalk, 6, stalk, 0xFFDAC8A0);
        int top = ground - stalk - 10 + squash;
        c.fill(x - r, top, 2 * r, 10 - squash, cap);
        c.fill(x - r + 3, top - 4 + squash, 2 * r - 6, 4, cap);
        c.fill(x - r / 2, top + 2, 4, 3, Colors.WHITE);
        c.fill(x + r / 3, top + 4, 3, 2, Colors.WHITE);
    }

    static void stars(SceneCanvas c, int x, int y, int w, int h, long t) {
        for (int i = 0; i < 24; i++) {
            int sx = x + (i * 53) % w;
            int sy = y + (i * 31) % (h - 30);
            if ((t / 12 + i) % 7 != 0) {
                c.fill(sx, sy, 1, 1, Colors.WHITE);
            }
        }
    }

    /** The Special Stage globe: a blue and white checkered sphere that scrolls. */
    static void checkerSphere(SceneCanvas c, int cx, int cy, int r, long t) {
        for (int dy = -r; dy <= r; dy += 2) {
            int half = (int) Math.sqrt(r * r - dy * dy);
            for (int dx = -half; dx <= half; dx += 2) {
                double u = Math.asin(Math.max(-1, Math.min(1, dx / (double) Math.max(1, half)))) + t * 0.03;
                double v = Math.asin(dy / (double) r);
                boolean light = ((int) Math.floor(u * 3) + (int) Math.floor(v * 3)) % 2 == 0;
                c.fill(cx + dx, cy + dy, 2, 2, light ? 0xFF6CB6FF : 0xFF2448DA);
            }
        }
    }

    static void fire(SceneCanvas c, int cx, int ground, long t) {
        c.fill(cx - 12, ground - 3, 24, 4, 0xFF5C3C1C);
        for (int i = 0; i < 6; i++) {
            int fh = 8 + (int) ((Math.sin(t * 0.3 + i * 1.7) + 1) * 6);
            int fx = cx - 9 + i * 3;
            c.fill(fx, ground - 3 - fh, 3, fh, i % 2 == 0 ? 0xFFFF9024 : 0xFFFFDA24);
        }
        c.fill(cx - 2, ground - 10, 4, 6, Colors.WHITE);
    }

    static void tube(SceneCanvas c, int x, int ground, int liquid, long t) {
        c.fill(x, ground - 70, 20, 70, 0xFF485868);
        c.fill(x + 2, ground - 66, 16, 62, 0xFF203040);
        int level = 30 + (int) (Math.sin(t * 0.04) * 4);
        c.fill(x + 2, ground - 4 - level, 16, level, liquid);
        for (int i = 0; i < 3; i++) {
            int by = (int) ((t + i * 13) % level);
            c.fill(x + 5 + i * 4, ground - 6 - by, 2, 2, Colors.WHITE);
        }
        c.fill(x - 1, ground - 72, 22, 4, 0xFF6C7C8C);
    }

    /** A Chao: round blue body, yellow-tipped head, and a floating ball above. */
    static void chao(SceneCanvas c, int cx, int top, long t) {
        c.fill(cx - 2, top - 8, 4, 4, GOLD);
        c.fill(cx - 7, top - 2, 14, 12, 0xFF6CB6FF);
        c.fill(cx - 5, top - 4, 10, 2, 0xFF6CB6FF);
        c.fill(cx - 4, top + 2, 2, 3, Colors.BLACK);
        c.fill(cx + 2, top + 2, 2, 3, Colors.BLACK);
        c.fill(cx - 6, top + 10, 12, 6, 0xFF6CB6FF);
        c.fill(cx - 10, top + 9 + (int) ((t / 10) % 2), 4, 3, 0xFF6CB6FF);
        c.fill(cx + 6, top + 9 + (int) ((t / 10 + 1) % 2), 4, 3, 0xFF6CB6FF);
        c.fill(cx - 5, top + 15, 10, 2, GOLD_DARK);
    }
}
