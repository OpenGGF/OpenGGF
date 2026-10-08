package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.ui.Colors;

/**
 * The Hidden Palace Mural: a wall of Hidden Palace's purple stone (Pal_HPZ) with a mural of the
 * golden hedgehog, Super Sonic's own standing frame (Map_SuperSonic) in his shimmering colours
 * (PalCycle_SuperSonic), above three sockets holding Super Emeralds (Obj_HPZSuperEmerald,
 * Map_HPZEmeraldMisc), each over its carved word. The emeralds twinkle as the shrine's do. After
 * the card pick, "forget" drains the left emerald to the gray of an unearned one (frame $1E,
 * ArtNem_HPZGrayEmerald), "change" flickers the middle one through every Super Emerald colour
 * and "grow" makes the right one blaze gold; each socket keeps its new look.
 */
final class MuralPicture extends EventPicture {
    /** Pal_HPZ: Hidden Palace's lines 1-3; line 2 holds the purple stone. */
    private static final int PAL_HPZ = 0x0669D2;
    /** Map_SuperSonic frame $A1: Super Sonic standing, quills up. */
    private static final int SUPER_SONIC_STAND = 0xA1;
    /** Map_HPZEmeraldMisc: frame 7 is the twinkle drawn over a Super Emerald, $1E the gray emerald. */
    private static final int TWINKLE_FRAME = 7;
    private static final int GRAY_FRAME = 0x1E;

    /** The socket effects' lengths are the mod's own (the shrine has no such effects). */
    private static final int FORGET_FRAMES = 60;
    private static final int CHANGE_FRAMES = 84;
    private static final int GROW_FRAMES = 72;

    private final String[] words = {"FORGET", "CHANGE", "GROW"};
    /** The Super Emerald (Obj_HPZSuperEmerald subtype) in each socket. */
    private final int[] subtypes = {3, 5, 0};
    private final boolean[] dimmed = new boolean[3];
    private final boolean[] blazing = new boolean[3];
    /** The wall's colours: Pal_HPZ line 2 from the ROM, or a stand-in purple. */
    private int[] stone;
    private String phase = "";
    private int age;

    MuralPicture(Shell shell) {
    }

    @Override
    void show(Shell shell, String detail) {
        phase = detail;
        age = 0;
        switch (detail) {
            case "change" -> shell.sfx(Sounds.SFX_SIGNPOST);
            case "grow" -> shell.sfx(Sounds.SFX_SUPER_TRANSFORM);
            default -> {
            }
        }
    }

    @Override
    boolean busy() {
        return switch (phase) {
            case "forget" -> age < FORGET_FRAMES;
            case "change" -> age < CHANGE_FRAMES;
            case "grow" -> age < GROW_FRAMES;
            default -> false;
        };
    }

    @Override
    void tick(Shell shell) {
        age++;
        switch (phase) {
            case "forget" -> {
                if (age == 40) {
                    dimmed[0] = true;
                    shell.sfx(Sounds.SFX_SWITCH);    // the socket clicks dark
                }
            }
            case "change" -> {
                if (age < 72 && age % 4 == 0) {
                    subtypes[1] = (subtypes[1] + 1) % 7;
                }
                if (age == 72) {
                    shell.sfx(Sounds.SFX_STARPOST);
                }
            }
            case "grow" -> {
                if (age == 24) {
                    blazing[2] = true;
                }
            }
            default -> {
            }
        }
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        int[] p = stone(shell);
        int ground = y + h - 24;
        wall(c, x, y, w, ground, p);
        mural(shell, c, x + 17, y + 3, w - 34, 98, p, t);
        // The floor: a step of stone with the three sockets and their carved words.
        c.fill(x, ground, w, y + h - ground, p[11]);
        c.fill(x, ground, w, 2, p[3]);
        for (int i = 0; i < 3; i++) {
            // Set out as the shrine sets its emeralds (word_90860): the middle one raised between the others.
            int sx = x + w / 2 + (i - 1) * 40;
            int top = i == 1 ? ground - 30 : ground - 8;
            socket(shell, c, sx, ground, top, i, p, t);
            int tw = shell.font.width(words[i]);
            shell.font.draw(c, words[i], sx - tw / 2 + 1, ground + 10, p[9]);
            shell.font.draw(c, words[i], sx - tw / 2, ground + 9, glowFor(i, p));
        }
    }

    /** The carved word's colour: lit like the emerald above it. */
    private int glowFor(int socket, int[] p) {
        if (dimmed[socket]) {
            return p[9];
        }
        return blazing[socket] ? EventArt.GOLD : p[1];
    }

    private int[] stone(Shell shell) {
        if (stone == null) {
            int[] line = null;
            if (shell.art.hasRom()) {
                try {
                    line = shell.art.rom().palette(PAL_HPZ + 0x20, 16);
                } catch (RuntimeException e) {
                    line = null;
                }
            }
            stone = line != null ? line : new int[] {0, 0xFFFCFCFC, 0xFFD8B4FC, 0xFFB46CD8, 0xFF6C4890, 0xFF48246C,
                    0xFFB4FCD8, 0xFF48D8B4, 0xFF246C48, 0xFF242424, 0xFF242448, 0xFF48246C, 0xFF24486C, 0xFF246CB4,
                    0xFFD8B424, 0xFF906C00};
        }
        return stone;
    }

    /** Hidden Palace's wall: dark purple blocks with lighter edges. */
    private static void wall(SceneCanvas c, int x, int y, int w, int ground, int[] p) {
        c.fill(x, y, w, ground - y, p[10]);
        for (int row = 0; row * 12 < ground - y; row++) {
            int shift = row % 2 == 0 ? 0 : 12;
            for (int col = -1; col * 24 < w; col++) {
                int bx = x + col * 24 + shift;
                int by = y + row * 12;
                c.fill(bx + 1, by + 1, 22, 10, p[5]);
                c.fill(bx + 1, by + 1, 22, 1, p[4]);
            }
        }
    }

    /**
     * The mural: the golden hedgehog carved twice life size into a framed panel of the wall,
     * shimmering through his Super colours.
     */
    private void mural(Shell shell, SceneCanvas c, int x, int y, int w, int h, int[] p, long t) {
        c.fill(x - 3, y - 3, w + 6, h + 6, p[11]);
        c.fill(x - 2, y - 2, w + 4, h + 4, p[3]);
        c.fill(x, y, w, h, p[4]);
        for (int i = 0; i < h; i += 6) {
            c.fill(x, y + i, w, 1, p[5]);
        }
        // A soft halo behind him, brighter while an emerald blazes.
        boolean blaze = phase.equals("grow") && age < GROW_FRAMES || blazing[2];
        int cx = x + w / 2;
        int cy = y + h / 2;
        for (int r = 3; r >= 1; r--) {
            int a = (blaze ? 0x24 : 0x12) * (4 - r);
            c.fill(cx - 13 * r, cy - 13 * r, 26 * r, 26 * r, (a << 24) | (EventArt.GOLD & 0xFFFFFF));
        }
        // SuperHyper_PalCycle_SuperSonic steps Palette_frame $24-$30 (entries 6-8) every 7 frames.
        int entry = 6 + (int) ((t / 7) % 3);
        SceneSprite hog = shell.art.romFrame("super_sonic_" + entry, SUPER_SONIC_STAND);
        SceneDraw paint = SceneDraw.plain().withScale(2).withTint(0xFFF0E0C8);
        if (hog != null) {
            Poses.stand(c, hog, cx, y + h - 2, paint.withFlash(blaze && phase.equals("grow") && (t / 4) % 2 == 0
                    ? 0x50FFFFFF : 0));
        } else {
            Poses.hero(shell, c, "sonic", Poses.WAIT, 0, cx, y + h - 2,
                    paint.withFlash(0xE0000000 | (EventArt.GOLD & 0xFFFFFF)));
        }
        // The carving's chiselled edge over the top.
        c.fill(x, y, w, 1, p[3]);
        c.fill(x, y + h - 1, w, 1, p[11]);
    }

    /** A socket: a stone post up to row {@code top} with a cup, and its emerald sitting in it. */
    private void socket(Shell shell, SceneCanvas c, int sx, int ground, int top, int i, int[] p, long t) {
        c.fill(sx - 6, top, 12, ground - top, p[5]);
        c.fill(sx - 5, top, 2, ground - top, p[4]);
        c.fill(sx - 14, top - 4, 28, 5, p[4]);
        c.fill(sx - 14, top - 4, 28, 1, p[3]);
        c.fill(sx - 14, top, 28, 1, p[11]);
        int gy = top - 16;
        if (phase.equals("grow") && i == 2 || blazing[i]) {
            glow(c, sx, gy, i);
        }
        emerald(shell, c, sx, gy, i, t);
        if (phase.equals("grow") && i == 2) {
            for (int k = 0; k < 4; k++) {
                double a = age * 0.12 + k * Math.PI / 2;
                EventActors.sparkle(shell, c, sx + (float) Math.cos(a) * 22, gy + (float) Math.sin(a) * 14,
                        (age + k * 6) % 24);
            }
        }
    }

    /** The blaze: a gold glow that swells as the emerald catches, then keeps pulsing. */
    private void glow(SceneCanvas c, int sx, int gy, int i) {
        float k = phase.equals("grow") && age < GROW_FRAMES ? Math.min(1f, age / 24f) : 1f;
        int pulse = (int) (Math.sin(age * 0.15) * 2);
        for (int r = 3; r >= 1; r--) {
            int size = Math.round((12 + r * 6 + pulse) * k);
            int a = 0x28 * (4 - r);
            c.fill(sx - size, gy - size * 2 / 3, size * 2, size * 4 / 3, (a << 24) | (EventArt.GOLD & 0xFFFFFF));
        }
    }

    /**
     * One socket's emerald. Obj_HPZSuperEmerald loc_908BE: on every other frame (V_int_run_count
     * bit 0) the twinkle frame is drawn in its place, on palette line 0; word_90816 gives each
     * subtype its palette line (Sonic's column).
     */
    private void emerald(Shell shell, SceneCanvas c, int sx, int gy, int i, long t) {
        boolean draining = phase.equals("forget") && i == 0 && age < 40;
        boolean gray = dimmed[i] || draining && (age / Math.max(2, 10 - age / 5)) % 2 == 1;
        SceneSprite gem;
        if (gray) {
            gem = shell.art.romFrame("hpz_gray_emerald", GRAY_FRAME);
        } else if (t % 2 == 1 && !(phase.equals("change") && i == 1 && age < CHANGE_FRAMES)) {
            gem = shell.art.romFrame("hpz_emerald_0", TWINKLE_FRAME);
        } else {
            gem = shell.art.romFrame("hpz_emerald_" + lineOf(subtypes[i]), subtypes[i]);
        }
        if (gem == null) {
            EventArt.emerald(shell, c, sx, gy, gray ? 4 : subtypes[i], t);
            return;
        }
        int flash = 0;
        if (blazing[i]) {
            flash = (t / 6) % 2 == 0 ? 0x90FFDA24 : 0x60FFDA24;
        } else if (phase.equals("grow") && i == 2) {
            flash = Colors.alpha(Colors.WHITE, Math.min(1f, age / 24f) * 0.75f);
        }
        c.draw(gem, sx, gy, SceneDraw.plain().withFlash(flash));
    }

    /** word_90816, Sonic's column: the palette line each Super Emerald subtype is drawn on. */
    static int lineOf(int subtype) {
        return switch (subtype) {
            case 0, 2 -> 2;
            case 5 -> 1;
            case 6 -> 3;
            default -> 0;
        };
    }
}
