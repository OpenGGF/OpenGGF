package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;

/**
 * The Ring Shrine event: a stone shrine on the act's level under an arch of rings, its bowl
 * heaped with more, all spinning together as level rings do (ChangeRingFrame: a frame every 8),
 * now and then one glinting with the collect sparkle. Shown "pray", a handful of rings leave
 * the bowl and are pulled to the hero ({@link AttractedRings}), each collected with
 * sfx_RingRight and its sparkle. Shown "desecrate", the whole bowl comes the same way; then
 * the curse takes hold and rings burst out of the hero exactly as a hurt player loses them
 * ({@link SpilledRings}, sfx_RingLoss), bouncing on the level's floor until they blink out.
 */
final class RingShrinePicture extends StagedPicture {
    private static final int HERO_COL = 24;
    private static final int SHRINE_COL = 84;
    /** The rings heaped in the bowl, bottom row first. */
    private static final int PILE = 12;
    private static final int PRAY_RINGS = 6;
    /** How long the event waits after the spill starts (the rings keep bouncing afterwards). */
    private static final int SPILL_SHOWN = 60;

    private final String hero;
    private final int[] sine;
    private AttractedRings attracted;
    private SpilledRings spill;
    private boolean desecrate;
    private int sinceSpill = -1;
    private boolean done;
    private final int[] floorRows = new int[WIDTH];

    RingShrinePicture(Shell shell) {
        hero = EventHero.id(shell);
        sine = RomTables.sine(shell);
    }

    @Override
    void show(Shell shell, String detail) {
        if (attracted != null || shell.art.romFrame("ring", 0) == null) {
            return;
        }
        desecrate = detail.equals("desecrate");
        float heroY = floor(HERO_COL) - EventHero.standRadius(hero);
        attracted = new AttractedRings(desecrate ? PILE : PRAY_RINGS, SHRINE_COL, bowlTop() - 6, HERO_COL, heroY,
                desecrate ? 3 : 6);
    }

    @Override
    boolean busy() {
        return attracted != null && !done;
    }

    @Override
    void tick(Shell shell) {
        if (attracted == null) {
            return;
        }
        attracted.tick();
        for (int i = 0; i < attracted.collectedNow(); i++) {
            shell.sfx(Sounds.SFX_RING);
        }
        if (!attracted.done()) {
            return;
        }
        if (!desecrate) {
            done = true;
            return;
        }
        if (spill == null && sine != null) {
            for (int col = 0; col < WIDTH; col++) {
                floorRows[col] = floor(col);
            }
            float heroY = floor(HERO_COL) - EventHero.standRadius(hero);
            spill = new SpilledRings(sine, HERO_COL, heroY, 0x20);
            shell.sfx(Sounds.SFX_RING_LOSS);
            sinceSpill = 0;
        }
        if (spill != null) {
            spill.tick(floorRows);
            sinceSpill++;
        }
        done = spill == null || sinceSpill >= SPILL_SHOWN;
    }

    private int bowlTop() {
        return floor(SHRINE_COL) - 26;
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        drawLevel(shell, c, x, y, w, h);
        int sx = x + SHRINE_COL;
        int ground = y + floor(SHRINE_COL);
        drawShrine(c, sx, ground);
        int top = y + bowlTop();
        int heaped = PILE;
        if (attracted != null) {
            heaped = desecrate ? attracted.waiting() : PILE - (PRAY_RINGS - attracted.waiting());
        }
        drawPile(shell, c, sx, top, heaped, t);
        // An arch of rings over the shrine, spinning together; one glints now and then.
        int spinFrame = (int) ((t / 8) % 4);
        for (int i = 0; i < 9; i++) {
            double a = Math.PI + i * Math.PI / 8;
            int rx = sx + (int) Math.round(Math.cos(a) * 34);
            int ry = top - 4 + (int) Math.round(Math.sin(a) * 40);
            boolean glint = (t / 6 + i * 11) % 61 < 4;
            ring(shell, c, rx, ry, glint ? 4 + (int) ((t / 6) % 4) : spinFrame);
        }
        Poses.hero(shell, c, hero, attracted != null && !desecrate && !done ? Poses.LOOK_UP : Poses.WAIT, t,
                x + HERO_COL, y + floor(HERO_COL), SceneDraw.plain());
        if (attracted != null) {
            for (int i = 0; i < attracted.count(); i++) {
                int frame = attracted.frame(i);
                if (attracted.out(i) && frame >= 0) {
                    ring(shell, c, x + attracted.x(i), y + attracted.y(i), frame);
                }
            }
        }
        if (spill != null && spill.active()) {
            for (int i = 0; i < spill.count(); i++) {
                if (spill.alive(i)) {
                    ring(shell, c, x + spill.x(i), y + spill.y(i), spill.frame());
                }
            }
        }
    }

    /** A stone shrine (drawn: the games have none) with a gold offering bowl on top. */
    private static void drawShrine(SceneCanvas c, int sx, int ground) {
        EventArt.pedestal(c, sx, ground, 28, 18);
        int bowl = ground - 26;
        c.fill(sx - 23, bowl, 46, 2, EventArt.GOLD);
        c.fill(sx - 22, bowl + 2, 44, 2, EventArt.GOLD_DARK);
        c.fill(sx - 19, bowl + 4, 38, 2, EventArt.GOLD);
        c.fill(sx - 15, bowl + 6, 30, 2, EventArt.GOLD_DARK);
    }

    /** The heap in the bowl: rows of 5, 4 and 3 rings, emptied from the top. */
    private static void drawPile(Shell shell, SceneCanvas c, int sx, int top, int rings, long t) {
        int frame = (int) ((t / 8) % 4);
        int[] rows = {5, 4, 3};
        int drawn = 0;
        for (int row = 0; row < rows.length && drawn < rings; row++) {
            for (int i = 0; i < rows[row] && drawn < rings; i++, drawn++) {
                int rx = sx - (rows[row] - 1) * 4 + i * 8;
                int ry = top - 2 - row * 6;
                // Now and then a ring in the heap glints with the collect sparkle (Map_Ring 4-7).
                boolean glint = (t / 6 + drawn * 7) % 97 < 4;
                ring(shell, c, rx, ry, glint ? 4 + (int) ((t / 6) % 4) : frame);
            }
        }
    }

    private static void ring(Shell shell, SceneCanvas c, float x, float y, int frame) {
        SceneSprite ring = shell.art.romFrame("ring", frame);
        if (ring != null) {
            c.draw(ring, x, y, SceneDraw.plain());
        } else {
            c.fill(Math.round(x) - 4, Math.round(y) - 4, 8, 8, EventArt.GOLD);
        }
    }
}
