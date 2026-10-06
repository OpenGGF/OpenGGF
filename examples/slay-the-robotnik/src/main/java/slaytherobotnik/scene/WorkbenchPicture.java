package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.core.Card;
import slaytherobotnik.ui.Colors;

/**
 * The Abandoned Workbench, out on the act's level: a wooden bench with Tails' tools and a red
 * spring (Map_Spring) on it. Tails' note waits on the bench unless Tails is the one visiting.
 * [Tinker] (after the card is picked): the card is set down over the bench, the hero leans in
 * to work while three bursts of sparks fly off it (the Lightning Shield's four sparks, thrown
 * along SparkVelocities and falling at $18 a frame) to clanks and Mecha Sonic's spark hiss;
 * then the card flashes into its upgrade and the spring fires (Ani_Spring_VerticalTriggered),
 * bouncing it up like a monitor's icon. The upgraded card stays over the bench.
 */
final class WorkbenchPicture extends TimedPicture {
    /** Mod timing: the card settles, three bursts 20 frames apart, the flash, the spring. */
    private static final int SETTLE = 16;
    private static final int BURST_GAP = 20;
    private static final int FLASH = 76;
    private static final int SPRING = 88;
    private static final int LENGTH = 120;
    /** Ani_Spring_VerticalTriggered: one frame each (delay 0), then back to idle frame 0. */
    private static final String SPRING_FRAMES = "100222222";

    private Card before;
    private Card after;
    private RomBody[][] bursts = new RomBody[0][];
    private int[] burstAt = new int[0];
    private RomBody bounce;

    WorkbenchPicture(Shell shell) {
    }

    @Override
    void begin(Shell shell, String verb, String argument) {
        after = card(shell, argument);
        before = after == null ? null : new Card(after.def(), false);
        bursts = new RomBody[3][];
        burstAt = new int[] {SETTLE, SETTLE + BURST_GAP, SETTLE + 2 * BURST_GAP};
        bounce = null;
    }

    @Override
    boolean playing() {
        return clock < LENGTH;
    }

    @Override
    void step(Shell shell) {
        if (detail == null || clock > LENGTH + 30) {
            return;
        }
        for (int i = 0; i < burstAt.length; i++) {
            if (clock == burstAt[i]) {
                shell.sfx(i == 1 ? Sounds.SFX_MECHA_SPARK : Sounds.SFX_CLANK);
            }
            if (bursts[i] != null && clock - burstAt[i] < 20) {
                for (RomBody s : bursts[i]) {
                    moveSpark(s);
                }
            }
        }
        if (clock == SPRING) {
            shell.sfx(Sounds.SFX_SPRING);
            // The card hops up off the spring and settles as a monitor's icon does (mod launch speed).
            bounce = new RomBody(0, 0, 0, -0x200);
        }
        if (bounce != null && !bounce.riseIcon() && bounce.y < 0) {
            // Falling back: the same $18 a frame, until it is back on its spot.
            bounce.moveSprite2();
            bounce.yVel += RomBody.LIGHT_GRAVITY;
            if (bounce.y >= 0) {
                bounce.y = 0;
                bounce.yVel = 0;
            }
        }
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        int ground = y + h - 22;
        LevelStages.Placement placement = EventArt.level(shell, c, x, y, w, h, ground);
        int benchX = x + 78;
        int feet = feet(placement, x, y, benchX, ground);
        int top = feet - 24;
        String hero = hero(shell);
        boolean tails = hero.equals("tails");

        // The bench: a plank on two legs, Tails' tools on top.
        c.fill(benchX - 36, top + 6, 4, feet - top - 6, 0xFF6C4420);
        c.fill(benchX + 32, top + 6, 4, feet - top - 6, 0xFF6C4420);
        c.fill(benchX - 34, top + 14, 66, 3, 0xFF5C3818);
        c.fill(benchX - 39, top, 78, 6, 0xFF8C5C2C);
        c.fill(benchX - 39, top, 78, 1, 0xFFB47C40);
        c.fill(benchX - 30, top - 3, 14, 3, 0xFFB6B6B6);   // spanner
        c.fill(benchX - 32, top - 5, 4, 7, 0xFFB6B6B6);
        c.fill(benchX - 12, top - 2, 9, 2, 0xFFDA2424);   // screwdriver
        c.fill(benchX - 3, top - 2, 6, 1, 0xFFB6B6B6);
        if (!tails && detail == null) {
            // BACK IN 5 MINUTES. -T.
            c.fill(benchX + 2, top - 4, 10, 4, Colors.WHITE);
            c.fill(benchX + 3, top - 3, 7, 1, 0xFF6C6C6C);
        }
        // A gleam runs along the spanner now and then.
        if (detail == null) {
            sparkle(shell, c, benchX - 26, top - 3, (int) (t % 120) - 90);
        }
        int springFrame = 0;
        if (detail != null && clock >= SPRING && clock - SPRING < SPRING_FRAMES.length()) {
            springFrame = SPRING_FRAMES.charAt(clock - SPRING) - '0';
        }
        SceneSprite spring = shell.art.romFrame("spring", springFrame);
        if (spring != null) {
            // Every spring frame shares the object's origin, 8 pixels above its base.
            c.draw(spring, benchX + 22, top - 8, SceneDraw.plain());
        }

        boolean working = detail != null && clock >= SETTLE && clock < FLASH;
        int heroX = x + 26;
        Poses.hero(shell, c, hero, working ? Poses.PUSH : Poses.WAIT, working ? t / 2 : t, heroX,
                feet(placement, x, y, heroX, ground), SceneDraw.plain());

        if (detail == null) {
            return;
        }
        // The card: set down over the spring, worked on, flashed into its upgrade; the spring
        // fires up into it.
        int cardX = benchX + 22;
        int cardTop = top - 18 - CardRenderer.SMALL_H;
        if (clock < SETTLE) {
            cardTop -= (SETTLE - clock) * 5;
        }
        if (bounce != null) {
            cardTop += Math.round(bounce.py());
        }
        Card shown = clock < FLASH + 6 ? before : after;
        drawCard(shell, c, shown, cardX, cardTop);
        if (shown != null && clock >= FLASH && clock < FLASH + 12) {
            float k = 1f - Math.abs(clock - FLASH - 6) / 6f;
            c.fill(cardX - CardRenderer.SMALL_W / 2, cardTop, CardRenderer.SMALL_W, CardRenderer.SMALL_H,
                    Colors.alpha(Colors.WHITE, k));
        }
        if (shown != null && clock >= FLASH + 6) {
            // Sparkles twinkle at the card's corners, clear of its name.
            int half = CardRenderer.SMALL_W / 2;
            int[][] corners = {{-half, 0}, {half, 6}, {half - 2, CardRenderer.SMALL_H - 2}, {-half + 2, 30}};
            for (int i = 0; i < corners.length; i++) {
                sparkle(shell, c, cardX + corners[i][0], cardTop + corners[i][1], (clock - FLASH - 6 - i * 15) % 60);
            }
        }
        for (int i = 0; i < burstAt.length; i++) {
            int age = clock - burstAt[i];
            if (age < 0 || age >= 20) {
                continue;
            }
            if (bursts[i] == null) {
                bursts[i] = sparkBurst(benchX - 14 + i * 10, top - 2);
            }
            for (RomBody s : bursts[i]) {
                spark(shell, c, s.px(), s.py(), age);
            }
        }
    }
}
