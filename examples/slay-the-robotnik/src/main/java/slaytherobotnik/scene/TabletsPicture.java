package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import slaytherobotnik.ui.Colors;

/**
 * The Echidna Tablets: three carved stone tablets (the games have none, so they are drawn)
 * standing in the act's ruins, the hero reading them. Their carvings glow in the Master
 * Emerald's green, the two colours Obj_HPZMasterEmerald loc_90700 keeps in its palette. Each
 * reading ("read:T:N") lights tablet T's carvings row by row, then the hero reels in his hurt
 * pose with the ROM's hurt sound and blink, losing N; "take:N" lifts the last tablet off its
 * base into his hands; "stop:N" has him tear himself away as its light dies.
 */
final class TabletsPicture extends EventPicture {
    private static final int HERO_X = 20;
    private final int[] tabletX = {50, 78, 106};
    private static final int ROWS = 5;
    /** A row of carving lights every 6 frames; the toll comes once the tablet is lit (the mod's own timing). */
    private static final int ROW_FRAMES = 6;
    private static final int HURT_AT = ROWS * ROW_FRAMES + 6;
    private static final int HURT_POSE = 24;

    private final int[] litRows = new int[3];
    private boolean taken;
    private String phase = "";
    private int tablet;
    private int amount;
    private int age;
    private int invulnerable;
    private boolean turnedAway;
    private int[] green;

    TabletsPicture(Shell shell) {
    }

    @Override
    void show(Shell shell, String detail) {
        String[] parts = detail.split(":");
        phase = parts[0];
        age = 0;
        if (phase.equals("read")) {
            tablet = Integer.parseInt(parts[1]);
            amount = Integer.parseInt(parts[2]);
        } else {
            tablet = 2;
            amount = Integer.parseInt(parts[1]);
        }
        if (phase.equals("stop")) {
            turnedAway = true;
        }
    }

    /** When the hero is hurt, frames after {@link #show}. */
    private int hurtAt() {
        return switch (phase) {
            case "take" -> 58;
            case "stop" -> 16;
            default -> HURT_AT;
        };
    }

    @Override
    boolean busy() {
        return !phase.isEmpty() && age < hurtAt() + HURT_POSE + 20;
    }

    @Override
    void tick(Shell shell) {
        age++;
        if (invulnerable > 0) {
            invulnerable--;
        }
        switch (phase) {
            case "read" -> {
                if (age % ROW_FRAMES == 0 && litRows[tablet] < ROWS) {
                    litRows[tablet]++;
                }
                if (age == ROWS * ROW_FRAMES) {
                    shell.sfx(Sounds.SFX_SWITCH);
                }
            }
            case "take" -> {
                if (age == 1) {
                    shell.sfx(Sounds.SFX_GRAB);
                }
                if (age == 50) {
                    taken = true;
                }
            }
            case "stop" -> {
                if (age % 4 == 0 && litRows[2] > 0) {
                    litRows[2]--;
                }
            }
            default -> {
            }
        }
        if (age == hurtAt() && !phase.isEmpty()) {
            shell.sfx(Sounds.SFX_HURT);
            invulnerable = EventActors.INVULNERABLE;
        }
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        int ground = y + h - 22;
        LevelStages.Placement placement = EventArt.level(shell, c, x, y, w, h, ground);
        // The chamber is dim; the tablets' glow is most of the light.
        c.fill(x, y, w, h, 0x60000010);
        int[] g = green(shell);
        for (int i = 0; i < 3; i++) {
            int tx = x + tabletX[i];
            int floor = EventActors.floor(placement, x, y, tx, ground);
            float lift = 0;
            boolean held = false;
            if (i == 2 && phase.equals("take")) {
                lift = Math.min(1f, age / 24f) * 12;
                held = age >= 24;
            }
            base(c, tx, floor);
            if (i == 2 && taken && !(phase.equals("take") && age < 50)) {
                continue;
            }
            if (held) {
                // Carried across to the hero's hands, shrinking into them.
                float k = Math.min(1f, (age - 24) / 26f);
                int hx = x + HERO_X + 10;
                int hy = EventActors.floor(placement, x, y, hx, ground) - 20;
                float cx = tx + (hx - tx) * k;
                float top = floor - 58 - lift + (hy - (floor - 58 - lift)) * k;
                tablet(c, Math.round(cx), Math.round(top), 1f - 0.6f * k, i, g, t, true);
            } else {
                tablet(c, tx, Math.round(floor - 58 - lift), 1f, i, g, t, i == 2 && phase.equals("take"));
            }
        }
        if (phase.equals("take") && age >= 50) {
            int hx = x + HERO_X + 10;
            int hy = EventActors.floor(placement, x, y, hx, ground) - 24;
            EventActors.sparkle(shell, c, hx, hy, age - 50);
            EventActors.sparkle(shell, c, hx - 8, hy + 8, age - 54);
        }
        drawHero(shell, c, x, y, ground, placement, t);
        if (!phase.isEmpty()) {
            int hx = x + HERO_X;
            int feet = EventActors.floor(placement, x, y, hx, ground);
            EventActors.number(shell, c, "-" + amount, null, Colors.TEXT_BAD, hx + 6, feet - 56, x, age - hurtAt(),
                    60);
        }
    }

    private void drawHero(Shell shell, SceneCanvas c, int x, int y, int ground, LevelStages.Placement placement,
            long t) {
        if (!EventActors.blinkVisible(invulnerable)) {
            return;
        }
        int hurt = age - hurtAt();
        boolean reeling = !phase.isEmpty() && hurt >= 0 && hurt < HURT_POSE;
        float hx = HERO_X - (turnedAway ? Math.min(6, age / 3f) : 0);
        int anim = reeling ? Poses.HURT : phase.equals("read") && age < hurtAt() ? Poses.LOOK_UP : Poses.WAIT;
        float feet = EventActors.floor(placement, x, y, x + Math.round(hx), ground);
        EventActors.hero(shell, c, anim, t, x + hx, feet - (reeling ? 4 : 0), turnedAway, SceneDraw.plain());
    }

    /** The Master Emerald's two greens (the immediate of loc_90700's move.l at $9070C), bright first. */
    private int[] green(Shell shell) {
        if (green == null) {
            int[] colours = null;
            if (shell.art.hasRom()) {
                try {
                    colours = shell.art.rom().palette(0x09070E, 2);
                } catch (RuntimeException e) {
                    colours = null;
                }
            }
            green = colours != null ? colours : new int[] {0xFF00B46C, 0xFF6C6C00};
        }
        return green;
    }

    private static void base(SceneCanvas c, int tx, int floor) {
        c.fill(tx - 13, floor - 6, 26, 6, EventArt.STONE_DARK);
        c.fill(tx - 13, floor - 6, 26, 1, EventArt.STONE);
    }

    /**
     * A tablet 22 wide and 52 tall with its top at {@code top}, scaled about its centre by
     * {@code scale}: carved rows, lit in the emerald's green as far as it has been read.
     */
    private void tablet(SceneCanvas c, int tx, int top, float scale, int i, int[] g, long t, boolean glowing) {
        int w = Math.round(22 * scale);
        int hgt = Math.round(52 * scale);
        int left = tx - w / 2;
        int lit = litRows[i];
        if (glowing || lit == ROWS) {
            int a = glowing ? 0x60 + (int) (Math.sin(t * 0.3) * 0x20) : 0x30;
            c.fill(left - 3, top - 3, w + 6, hgt + 6, (a << 24) | (g[0] & 0xFFFFFF));
        }
        c.fill(left, top, w, hgt, EventArt.STONE_DARK);
        c.fill(left + 1, top + 1, w - 2, hgt - 2, EventArt.STONE);
        c.fill(left + 1, top + 1, w - 2, 1, 0xFFA89888);
        // The rounded crown of the tablet.
        c.fill(left + 2, top - 2, w - 4, 2, EventArt.STONE_DARK);
        if (scale < 0.75f) {
            return;
        }
        for (int row = 0; row < ROWS; row++) {
            int ry = top + 6 + row * 9;
            // The carvings drift when looked at: their lengths wander slowly.
            int len = 8 + (int) ((row * 5 + i * 3 + (t / 40)) % 7);
            int colour = row < lit ? (glowing && (t / 4) % 2 == 0 ? Colors.WHITE : g[0]) : EventArt.STONE_DARK;
            c.fill(left + 4, ry, len, 2, colour);
            c.fill(left + 4 + len + 2, ry, Math.max(2, 12 - len), 2, colour);
            c.fill(left + 4, ry + 4, 6, 1, row < lit ? g[1] : EventArt.STONE_DARK);
        }
    }
}
