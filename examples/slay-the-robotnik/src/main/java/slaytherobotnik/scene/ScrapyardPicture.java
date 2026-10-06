package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import java.util.ArrayList;
import java.util.List;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.core.EncounterDef;
import slaytherobotnik.core.Relic;
import slaytherobotnik.core.Rng;
import slaytherobotnik.ui.Colors;

/**
 * The Badnik Scrapyard: a smoking heap of Angel Island's own wrecked badniks in the act's level,
 * the hero beside it. Each search shakes the heap as the hero digs ("rings:N", "relic:id",
 * "bolts", "elite:encounter"), then out comes the find: rings flung the way a hit spills them
 * (Obj_Bouncing_Ring) and collected with their sparkle and alternating ring sounds, the relic
 * rising out of the scrap, a scatter of bolts, or the heap bursting in badnik explosions
 * (Obj_Explosion, sfx_Break) as the elite rises out of it.
 */
final class ScrapyardPicture extends EventPicture {
    private static final int HERO_X = 22;
    private static final int DIG_X = 42;
    private static final int PILE_X = 82;
    /** The heap shakes while the hero digs, then the find comes out (the mod's own timing). */
    private static final int DIG_FRAMES = 32;
    /** Obj_Bouncing_Ring loc_1A75C: $18 gravity; a bounce keeps three quarters of the speed. */
    private static final int RING_GRAVITY = 0x18;
    /** Obj_Explosion: frame 0 for 4 frames, then frames 1-4 for 8 each (loc_1E626, loc_1E66E). */
    private static final int EXPLOSION_FRAMES = 36;

    private String phase = "";
    private int age;
    private int amount;
    private String relicId;
    private List<Enemy> elite;
    private boolean burst;
    private final List<EventActors.Fling> rings = new ArrayList<>();
    private final List<EventActors.Fling> bolts = new ArrayList<>();
    private final List<EventActors.Fling> wreck = new ArrayList<>();
    /** Badniks of the heap: sprite key, frame, offset from the heap, flips. */
    private final String[] heapKeys = {"rhinobot", "monkey_dude", "caterkiller_jr", "bloominator"};
    private final int[][] heap = {{0, -20, 0, 0, 1}, {0, 18, 0, 0, 0}, {1, -2, -16, 1, 0}, {0, 4, -4, 0, 1}};

    ScrapyardPicture(Shell shell) {
    }

    @Override
    void show(Shell shell, String detail) {
        int colon = detail.indexOf(':');
        phase = colon < 0 ? detail : detail.substring(0, colon);
        String arg = colon < 0 ? "" : detail.substring(colon + 1);
        age = 0;
        relicId = null;
        rings.clear();
        switch (phase) {
            case "rings" -> amount = Integer.parseInt(arg);
            case "relic" -> relicId = arg;
            case "elite" -> elite = spawn(shell, arg);
            default -> {
            }
        }
    }

    private static List<Enemy> spawn(Shell shell, String encounterId) {
        try {
            EncounterDef def = shell.catalog.encounter(encounterId);
            return def.spawner().spawn(new Rng(1));
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    @Override
    boolean busy() {
        return switch (phase) {
            case "rings" -> age < 112;
            case "relic" -> age < 84;
            case "bolts" -> age < 80;
            case "elite" -> age < 130;
            default -> false;
        };
    }

    @Override
    void tick(Shell shell) {
        age++;
        if (phase.isEmpty()) {
            return;
        }
        if (age == 8 || age == 20) {
            shell.sfx(Sounds.SFX_CLANK);            // rummaging through the metal
        }
        if (age == DIG_FRAMES) {
            pop(shell);
        }
        stepFalling(rings, true);
        stepFalling(bolts, false);
        for (EventActors.Fling f : wreck) {
            f.step();
        }
        if (phase.equals("rings") && age >= 70 && (age - 70) % 4 == 0) {
            int ring = (age - 70) / 4;
            if (ring < rings.size()) {
                // Collect_Ring: the sound alternates right and left speaker ring by ring.
                shell.sfx(ring % 2 == 0 ? Sounds.SFX_RING : Sounds.SFX_RING_LEFT);
            }
        }
        if (phase.equals("bolts") && (age == DIG_FRAMES + 22 || age == DIG_FRAMES + 30)) {
            shell.sfx(Sounds.SFX_CLANK);
        }
        if (phase.equals("elite") && (age == DIG_FRAMES + 10 || age == DIG_FRAMES + 20)) {
            shell.sfx(Sounds.SFX_BREAK);
        }
    }

    private void pop(Shell shell) {
        switch (phase) {
            case "rings" -> {
                for (int[] v : spill(ringsShown(amount))) {
                    rings.add(new EventActors.Fling(PILE_X, -30, v[0], v[1], RING_GRAVITY));
                }
                shell.sfx(Sounds.SFX_RING_LOSS);
            }
            case "relic" -> shell.sfx(Sounds.SFX_SPRING);
            case "bolts" -> {
                bolts.clear();
                int[][] v = {{-0x140, -0x380}, {0xC0, -0x420}, {0x180, -0x300}, {-0x60, -0x2C0}};
                for (int[] b : v) {
                    bolts.add(new EventActors.Fling(PILE_X, -26, b[0], b[1], RING_GRAVITY));
                }
                shell.sfx(Sounds.SFX_CLANK);
            }
            case "elite" -> {
                burst = true;
                int[][] v = {{-0x200, -0x400}, {0x240, -0x380}, {-0x100, -0x480}, {0x180, -0x440}};
                for (int i = 0; i < heap.length; i++) {
                    wreck.add(new EventActors.Fling(PILE_X + heap[i][1], heap[i][2], v[i][0], v[i][1], 0x38));
                }
                shell.sfx(Sounds.SFX_BREAK);
                shell.shake(10);
            }
            default -> {
            }
        }
    }

    /** Falls under its gravity and bounces off the floor (row 0) keeping three quarters of its speed. */
    private static void stepFalling(List<EventActors.Fling> list, boolean keepBouncing) {
        for (EventActors.Fling f : list) {
            f.step();
            if (f.yVel > 0 && f.py() >= 0) {
                f.y = 0;
                f.yVel = keepBouncing || f.yVel > 0x100 ? -(f.yVel - (f.yVel >> 2)) : 0;
                if (!keepBouncing) {
                    f.xVel = f.yVel == 0 ? 0 : f.xVel / 2;
                }
            }
        }
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        int ground = y + h - 22;
        LevelStages.Placement placement = EventArt.level(shell, c, x, y, w, h, ground);
        int pileFloor = EventActors.floor(placement, x, y, x + PILE_X, ground);
        boolean digging = !phase.isEmpty() && age < DIG_FRAMES;
        drawHeap(shell, c, x + PILE_X, pileFloor, t, digging);
        if (!burst) {
            // Smoke curls up from the heap; something deep inside twitches.
            SceneSprite smoke = shell.art.romFrame("explosion", (int) ((t / 8) % 5));
            if (smoke != null) {
                Poses.centre(c, smoke, x + PILE_X + 6, pileFloor - 44 - (t / 3) % 12,
                        SceneDraw.plain().withAlpha(0.55f));
            }
            if ((t / 20) % 6 == 0) {
                c.fill(x + PILE_X - 2, pileFloor - 22, 3, 3, 0xFFFF4848);
            }
        }
        if (phase.equals("elite")) {
            drawElite(shell, c, x, y, w, h, pileFloor, t);
        }
        drawHero(shell, c, x, y, ground, placement, t);
        switch (phase) {
            case "rings" -> drawRings(shell, c, x, pileFloor, t);
            case "relic" -> drawRelic(shell, c, x, y, w, h, pileFloor, t);
            default -> {
            }
        }
        for (EventActors.Fling b : bolts) {
            bolt(c, Math.round(x + b.px()), Math.round(pileFloor + b.py()));
        }
    }

    private void drawHeap(Shell shell, SceneCanvas c, int px, int floor, long t, boolean digging) {
        SceneDraw wreckStyle = SceneDraw.plain().withTint(0xFF8C8C8C);
        for (int i = 0; i < heap.length; i++) {
            int[] part = heap[i];
            SceneSprite s = shell.art.romFrame(heapKeys[i], part[0]);
            SceneDraw style = wreckStyle.withFlipX(part[3] == 1).withFlipY(part[4] == 1);
            if (burst && i < wreck.size()) {
                EventActors.Fling f = wreck.get(i);
                if (f.py() < 60) {
                    Poses.centre(c, s, px - PILE_X + f.px(), floor + f.py() - 8, style);
                }
                continue;
            }
            // Digging rattles the heap a pixel this way and that.
            int jiggle = digging && age > 4 ? ((age / 2 + i) % 2 == 0 ? 1 : -1) : 0;
            Poses.stand(c, s, px + part[1] + jiggle, floor + part[2], style);
        }
        if (burst) {
            int[][] blasts = {{0, -10, 0}, {-14, -20, 6}, {12, -6, 12}, {2, -28, 18}};
            for (int[] b : blasts) {
                int e = age - DIG_FRAMES - b[2];
                if (e >= 0 && e < EXPLOSION_FRAMES) {
                    int frame = e < 4 ? 0 : 1 + (e - 4) / 8;
                    Poses.centre(c, shell.art.romFrame("explosion", frame), px + b[0], floor + b[1],
                            SceneDraw.plain());
                }
            }
        }
    }

    private void drawHero(Shell shell, SceneCanvas c, int x, int y, int ground, LevelStages.Placement placement,
            long t) {
        float hx = HERO_X;
        int anim = Poses.WAIT;
        if (!phase.isEmpty() && age < DIG_FRAMES) {
            hx = age < 8 ? HERO_X + (DIG_X - HERO_X) * age / 8f : DIG_X;
            anim = age < 8 ? Poses.WALK : 0x04;     // walks up, then pushes into the heap (AniSonic $04)
        } else if (!phase.isEmpty()) {
            // Back from the heap; well back when something climbs out of it.
            int to = phase.equals("elite") ? 12 : HERO_X;
            hx = DIG_X - (DIG_X - to) * Math.min(1f, (age - DIG_FRAMES) / 12f);
        }
        float feet = EventActors.floor(placement, x, y, x + Math.round(hx), ground);
        EventActors.hero(shell, c, anim, t, x + hx, feet, false, SceneDraw.plain());
    }

    private void drawRings(Shell shell, SceneCanvas c, int x, int floor, long t) {
        for (int i = 0; i < rings.size(); i++) {
            EventActors.Fling f = rings.get(i);
            int collected = age - 70 - i * 4;
            float rx = x + f.px();
            float ry = floor + f.py() - 8;
            if (collected < 0) {
                EventArt.ring(shell, c, Math.round(rx), Math.round(ry), t + i);
            } else {
                EventActors.sparkle(shell, c, rx, ry, collected);
            }
        }
        EventActors.number(shell, c, "+" + amount, "RINGS", EventArt.GOLD, x + PILE_X, floor - 60, x,
                age - 70, 60);
    }

    private void drawRelic(Shell shell, SceneCanvas c, int x, int y, int w, int h, int floor, long t) {
        if (age < DIG_FRAMES || relicId == null) {
            return;
        }
        float k = Math.min(1f, (age - DIG_FRAMES) / 24f);
        float eased = 1 - (1 - k) * (1 - k);
        int size = 20;
        int rx = x + PILE_X - size / 2;
        int ry = Math.round(floor - 30 - 34 * eased) + EventArt.bob(t, 1);
        Relic relic = relic(shell);
        if (relic != null) {
            HudIcons.relic(shell, c, relic, rx, ry, size);
            c.clip(x, y, w, h);                     // HudIcons.relic clears the clip
        }
        for (int k2 = 0; k2 < 3; k2++) {
            EventActors.sparkle(shell, c, rx + size / 2f + (k2 - 1) * 12, ry + 4 + (k2 % 2) * 12,
                    (int) ((age + k2 * 8) % 32) - 4);
        }
    }

    private Relic shown;

    private Relic relic(Shell shell) {
        if (shown == null || !shown.id().equals(relicId)) {
            try {
                shown = shell.catalog.newRelic(relicId);
            } catch (RuntimeException e) {
                shown = null;
            }
        }
        return shown;
    }

    private void drawElite(Shell shell, SceneCanvas c, int x, int y, int w, int h, int floor, long t) {
        if (elite == null || elite.isEmpty() || age < DIG_FRAMES + 16) {
            return;
        }
        // It climbs out of the ground where the heap stood, flashing as it wakes.
        float k = Math.min(1f, (age - DIG_FRAMES - 16) / 50f);
        int rise = Math.round((1 - k) * 60);
        float flash = age < DIG_FRAMES + 70 && (age / 4) % 2 == 0 ? 0.7f : 0f;
        c.clip(x, y, w, floor - y + 4);
        int n = elite.size();
        for (int i = 0; i < n; i++) {
            // A lone elite stands a little right of the heap, clear of the hero backing away.
            int ex = x + PILE_X + (n == 1 ? 10 : 0) + Math.round((i - (n - 1) / 2f) * 28);
            EnemyVisuals.draw(shell, c, elite.get(i), ex, floor + rise, t, flash, 1f);
        }
        c.clip(x, y, w, h);
    }

    /** A bolt: a hex head on a short thread, lying on its side. */
    private static void bolt(SceneCanvas c, int bx, int by) {
        c.fill(bx - 4, by - 6, 5, 6, 0xFF242424);
        c.fill(bx - 3, by - 5, 3, 4, 0xFFB6B6B6);
        c.fill(bx - 3, by - 5, 3, 1, Colors.WHITE);
        c.fill(bx + 1, by - 4, 5, 3, 0xFF242424);
        c.fill(bx + 1, by - 3, 4, 1, 0xFF909090);
    }

    /**
     * The rings' velocities as Obj_Bouncing_Ring loc_1A67A sets them: in mirrored pairs, the
     * angle stepping $10 from $88, sine and cosine shifted left by the speed in the angle's high
     * byte. The ROM's second round of speeds (shift 1, from $188) keeps them in the window.
     */
    static int[][] spill(int count) {
        int[][] velocities = new int[count][];
        int angle = 0x88;
        for (int i = 0; i + 1 < count; i += 2) {
            double a = angle / 256.0 * Math.PI * 2;
            int xVel = (int) Math.round(Math.sin(a) * 256) << 1;
            int yVel = (int) Math.round(Math.cos(a) * 256) << 1;
            velocities[i] = new int[] {xVel, yVel};
            velocities[i + 1] = new int[] {-xVel, yVel};
            angle += 0x10;
        }
        return velocities;
    }

    /** How many rings fly out for {@code amount}: an even number from 2 to 8, a ring for every four. */
    static int ringsShown(int amount) {
        return Math.max(2, Math.min(8, amount / 4)) & ~1;
    }
}
