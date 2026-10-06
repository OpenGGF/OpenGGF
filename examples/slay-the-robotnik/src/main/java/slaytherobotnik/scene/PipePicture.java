package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.ui.Colors;

/**
 * The Clogged Pipe: the open end of a thick pipe in the act's level, its throat bristling with
 * spikes (Map_Spikes, ArtNem_SpikesSprings) and a glint of something golden deep inside, a ring
 * from Map_Ring, smaller the deeper it sits. "cut:N" and "prize:N": the hero walks up and pushes
 * an arm in (AniSonic $04), rummages, then is cut on the spikes with HurtCharacter's knockback,
 * spike sound and blink; after a cut the glint has worked closer. With the prize, the glint flies
 * out with him into his hands.
 */
final class PipePicture extends EventPicture {
    private static final int HERO_X = 30;
    private static final int REACH_X = 50;
    private static final int PIPE_X = 84;
    /** How high the pipe's mouth sits above the floor. */
    private static final int PIPE_RISE = 42;
    private static final int RADIUS = 30;
    /** The walk up and the rummage before the cut are the mod's own timing; the knockback is the ROM's. */
    private static final int WALK_FRAMES = 16;
    private static final int CUT_AT = 44;
    /** Map_Spikes: frame 0 is the short upright row. */
    private static final int SPIKE_FRAME = 0;
    private static final int PUSH = 0x04;

    private String phase = "";
    private int amount;
    private int age;
    /** How many times the hero has been cut; the glint is that much closer. */
    private int closer;
    private float heroX = HERO_X;
    /** Where the hero set off from for this reach (after a cut he starts where he landed). */
    private float startX = HERO_X;
    private EventActors.Fling knock;
    private int landAt = -1;
    private int invulnerable;
    private boolean prizeOut;

    PipePicture(Shell shell) {
    }

    @Override
    void show(Shell shell, String detail) {
        int colon = detail.indexOf(':');
        phase = colon < 0 ? detail : detail.substring(0, colon);
        amount = colon < 0 ? 0 : Integer.parseInt(detail.substring(colon + 1));
        age = 0;
        landAt = -1;
        knock = null;
        startX = heroX;
    }

    @Override
    boolean busy() {
        return !phase.isEmpty() && (landAt < 0 || age < landAt + 30);
    }

    @Override
    void tick(Shell shell) {
        age++;
        if (invulnerable > 0) {
            invulnerable--;
        }
        if (phase.isEmpty()) {
            return;
        }
        if (age <= WALK_FRAMES) {
            heroX = startX + (REACH_X - startX) * age / (float) WALK_FRAMES;
        }
        if (age == 20 || age == 32) {
            shell.sfx(Sounds.SFX_CLANK);           // fingers among the scrap
        }
        if (age == CUT_AT) {
            shell.sfx(Sounds.SFX_SPIKE_HIT);       // HurtCharacter: sfx_SpikeHit for spikes
            knock = new EventActors.Fling(heroX, 0, EventActors.HURT_X_VEL, EventActors.HURT_Y_VEL,
                    EventActors.HURT_GRAVITY);
            invulnerable = EventActors.INVULNERABLE;
            if (phase.equals("prize")) {
                prizeOut = true;
            }
        }
        if (knock != null) {
            knock.step();
            if (knock.px() < 8) {
                knock.x = 8 * 256;                  // Player_LevelBound: the window's edge stops him
            }
            heroX = knock.px();
            if (knock.yVel > 0 && knock.py() >= 0) {
                knock = null;
                landAt = age;
                if (phase.equals("prize")) {
                    shell.sfx(Sounds.SFX_RING);
                } else {
                    closer++;
                }
            }
        }
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        int ground = y + h - 22;
        LevelStages.Placement placement = EventArt.level(shell, c, x, y, w, h, ground);
        int px = x + PIPE_X;
        int py = EventActors.floor(placement, x, y, px, ground) - PIPE_RISE;
        pipe(shell, c, px, py, t);
        drawHero(shell, c, x, y, ground, placement, t);
        if (phase.isEmpty()) {
            return;
        }
        int heroFloor = EventActors.floor(placement, x, y, x + Math.round(heroX), ground);
        if (prizeOut) {
            // The prize comes out with his hand and lands in it.
            float k = landAt < 0 ? Math.min(1f, (age - CUT_AT) / 40f) : 1f;
            float gx = px + (x + heroX + 8 - px) * k;
            float gy = py + (heroFloor - 30 - py) * k - (float) Math.sin(k * Math.PI) * 16;
            SceneSprite ring = shell.art.romFrame("ring", (int) ((t / 4) % 4));
            if (ring != null && (landAt < 0 || age < landAt + 24)) {
                c.draw(ring, gx, gy, SceneDraw.plain());
            }
            EventActors.sparkle(shell, c, gx + 4, gy - 6, (int) ((age * 2) % 24));
        }
        if (age >= CUT_AT) {
            EventActors.number(shell, c, "-" + amount, null, Colors.TEXT_BAD, x + Math.round(heroX), heroFloor - 56,
                    x, age - CUT_AT, 60);
        }
    }

    private void drawHero(Shell shell, SceneCanvas c, int x, int y, int ground, LevelStages.Placement placement,
            long t) {
        if (!EventActors.blinkVisible(invulnerable)) {
            return;
        }
        int hx = Math.round(heroX);
        float feet = EventActors.floor(placement, x, y, x + hx, ground);
        int anim = Poses.WAIT;
        if (!phase.isEmpty() && age < CUT_AT) {
            anim = age < WALK_FRAMES ? Poses.WALK : PUSH;
        } else if (knock != null) {
            anim = Poses.HURT;
            feet += knock.py();
        }
        EventActors.hero(shell, c, anim, t, x + hx, feet, false, SceneDraw.plain());
    }

    /**
     * The pipe seen end on: a bright rim, the throat darkening into the wall, spikes jutting from
     * its lips, slime dripping from it, and the glint inside.
     */
    private void pipe(Shell shell, SceneCanvas c, int px, int py, long t) {
        // The rim, and the throat shading into the dark.
        disc(c, px, py, RADIUS + 2, 0xFF182430);
        disc(c, px, py, RADIUS, 0xFF8CA0B4);
        disc(c, px, py, RADIUS - 3, 0xFF485868);
        int[] throat = {0xFF2C3844, 0xFF1C2630, 0xFF121820, 0xFF080C10};
        for (int i = 0; i < throat.length; i++) {
            disc(c, px, py, RADIUS - 6 - i * 5, throat[i]);
        }
        // The glint: further in, smaller and dimmer; each cut brings it closer.
        int depth = Math.max(0, 3 - closer);
        if (!prizeOut) {
            float scale = 0.45f + 0.18f * (3 - depth);
            SceneSprite ring = shell.art.romFrame("ring", (int) ((t / 8) % 4));
            float dim = 0.5f + 0.17f * (3 - depth);
            if (ring != null) {
                c.draw(ring, px, py + 2, SceneDraw.plain().withScale(scale).withAlpha(Math.min(1f, dim)));
            }
            if ((t / 30) % 3 == 0) {
                EventActors.sparkle(shell, c, px + 3, py - 2, (int) (t % 30));
            }
        }
        // Spikes jut from the throat's lips, up from the bottom and down from the top (the short
        // row at half size: it is deep in the shade), with torn scrap round the sides.
        SceneSprite spikes = shell.art.romFrame("spikes", SPIKE_FRAME);
        if (spikes != null) {
            SceneDraw shade = SceneDraw.plain().withTint(0xFF9098A8).withScale(0.5f);
            float dx = (spikes.originX() - spikes.width() / 2f) * 0.5f;
            float dy = (spikes.originY() - spikes.height() / 2f) * 0.5f;
            float half = spikes.height() / 4f;
            c.draw(spikes, px + dx, py + RADIUS - 8 - half + dy, shade);
            c.draw(spikes, px + dx, py - RADIUS + 8 + half - dy, shade.withFlipY(true));
        }
        int[][] shards = {{-17, -6, 7, 3}, {-18, 2, 5, 2}, {12, -3, 6, 2}, {11, 5, 8, 3}};
        for (int[] sh : shards) {
            c.fill(px + sh[0], py + sh[1], sh[2], sh[3], 0xFF6C7C8C);
            c.fill(px + sh[0], py + sh[1], sh[2], 1, 0xFFB6C8DA);
        }
        // Slime drips from the rim.
        for (int i = 0; i < 3; i++) {
            int d = (int) ((t + i * 20) % 50);
            c.fill(px - 10 + i * 10, py + RADIUS + d, 3, 3, Colors.alpha(0xFF6C9048, 1f - d / 60f));
        }
    }

    /** A filled circle. */
    private static void disc(SceneCanvas c, int cx, int cy, int r, int colour) {
        if (r <= 0) {
            return;
        }
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt((double) r * r - dy * dy));
            c.fill(cx - half, cy + dy, half * 2 + 1, 1, colour);
        }
    }
}
