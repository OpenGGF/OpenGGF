package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.core.Card;
import slaytherobotnik.ui.Colors;

/**
 * The Wandering Medic, on the act's level: Pocky the rabbit (ArtNem_Rabbit, Map_Animals5) by a
 * first-aid box and a Starpost. Idle, the rabbit stands as an animal does when it pops out of a
 * badnik (frame 2) and now and then hops the way Obj_Animal's rabbit hops. Paying sends five
 * rings from the hero to the rabbit, pulled like the Lightning Shield pulls rings
 * (AttractedRing_Move) and sparkling as they are collected. [Heal]: the rabbit hops, the
 * Starpost's ball orbits and the hero glitters back to health; the Starpost keeps flashing.
 * [Purify]: the chosen card rises over the hero and, after the rabbit's hop, lifts away out of
 * the sky with a teleporter's hum.
 */
final class MedicPicture extends TimedPicture {
    private static final int PAY = 0;
    private static final int HOP = 1;
    private static final int ORBIT = 2;
    private static final int GLOW = 3;
    private static final int LIFT = 4;
    private static final int DONE = 5;

    /** Rings paid: one every six frames (mod timing), five of them. */
    private static final int RINGS = 5;
    private static final int RING_GAP = 6;
    /** The rabbit's hop speed: word_2C7EA, Obj_Animal type 0 (the rabbit), second word. */
    private static final int HOP_SPEED = -0x400;
    /** Obj_StarPost loc_2D12E: the ball orbits for $20 frames (see RestView). */
    private static final int ORBIT_FRAMES = 0x20;
    /** How long the hero glitters once healed (mod timing). */
    private static final int GLOW_FRAMES = 48;
    /** Idle: the rabbit hops once every 150 frames (mod timing). */
    private static final int IDLE_HOP = 150;

    private int x = 12;
    private int y = 36;
    private int heroX;
    private int heroFeet;
    private int rabbitX;
    private int rabbitFeet;

    private int phase = DONE;
    private int phaseStart;
    private RomBody[] rings = new RomBody[0];
    private int[] collected = new int[0];
    private RomBody rabbit;
    private int rabbitFrame = 2;
    private boolean lit;
    private Card card;
    private RomBody lift;

    MedicPicture(Shell shell) {
        layout(null, 12, 36, EventPicture.WIDTH, EventPicture.HEIGHT);
    }

    @Override
    void begin(Shell shell, String verb, String argument) {
        rings = new RomBody[RINGS];
        collected = new int[RINGS];
        for (int i = 0; i < RINGS; i++) {
            collected[i] = -1;
        }
        card = verb.equals("purify") ? card(shell, argument) : null;
        lift = null;
        phase = PAY;
        phaseStart = clock;
    }

    @Override
    boolean playing() {
        return phase != DONE;
    }

    private void enter(int next) {
        phase = next;
        phaseStart = clock;
    }

    @Override
    void step(Shell shell) {
        stepRabbit(shell);
        if (detail == null || phase == DONE) {
            return;
        }
        int t = clock - phaseStart;
        switch (phase) {
            case PAY -> {
                boolean all = true;
                for (int i = 0; i < RINGS; i++) {
                    all &= stepRing(shell, i, t);
                }
                // A ring that somehow never arrives is counted after two and a half seconds.
                if (all || t > 150) {
                    enter(HOP);
                    rabbit = new RomBody(rabbitX, rabbitFeet, 0, HOP_SPEED);
                }
            }
            case HOP -> {
                if (rabbit == null) {
                    if (verb().equals("heal")) {
                        enter(ORBIT);
                        shell.sfx(Sounds.SFX_STARPOST);
                    } else {
                        enter(LIFT);
                        lift = new RomBody(heroX, cardTop(), 0, 0);
                        shell.sfx(Sounds.SFX_TRANSPORTER);
                    }
                }
            }
            case ORBIT -> {
                if (t >= ORBIT_FRAMES) {
                    lit = true;
                    enter(GLOW);
                }
            }
            case GLOW -> {
                if (t >= GLOW_FRAMES) {
                    enter(DONE);
                }
            }
            case LIFT -> {
                // The card rises faster and faster: $18 a frame, a monitor icon's pull turned upwards.
                lift.moveSprite2();
                lift.yVel -= RomBody.LIGHT_GRAVITY;
                if (lift.py() < y - CardRenderer.SMALL_H - 8 || t > 120) {
                    enter(DONE);
                }
            }
            default -> {
            }
        }
        for (int i = 0; i < rings.length; i++) {
            if (collected[i] >= 0) {
                collected[i]++;
            }
        }
    }

    /** One frame of paid ring {@code i}; true once it has reached the rabbit. */
    private boolean stepRing(Shell shell, int i, int t) {
        if (collected[i] >= 0) {
            return true;
        }
        int release = i * RING_GAP;
        if (t < release) {
            return false;
        }
        if (rings[i] == null) {
            // Tossed up out of the hero's hands (mod launch speed), then pulled to the rabbit.
            rings[i] = new RomBody(heroX + 6, heroFeet - 24, 0x100, -0x300);
            return false;
        }
        RomBody r = rings[i];
        float tx = rabbitX;
        float ty = rabbitFeet - 12;
        r.attractTo(tx, ty);
        if (Math.abs(r.px() - tx) < 8 && Math.abs(r.py() - ty) < 8) {
            collected[i] = 0;
            shell.sfx(Sounds.SFX_RING);
            return true;
        }
        return false;
    }

    /** The rabbit's hops: during a scene, and once every {@link #IDLE_HOP} frames while idle. */
    private void stepRabbit(Shell shell) {
        if (rabbit == null) {
            rabbitFrame = 2;
            if ((detail == null || phase == DONE) && shell.ticks % IDLE_HOP == IDLE_HOP - 1) {
                rabbit = new RomBody(rabbitX, rabbitFeet, 0, HOP_SPEED);
            }
            return;
        }
        rabbitFrame = rabbit.hop(rabbitFeet, 0);
        if (rabbit.yVel == 0 && rabbit.y == rabbitFeet * 256) {
            rabbit = null;
            rabbitFrame = 2;
        }
    }

    private float cardTop() {
        return heroFeet - 44 - CardRenderer.SMALL_H;
    }

    private void layout(LevelStages.Placement placement, int wx, int wy, int w, int h) {
        x = wx;
        y = wy;
        int ground = wy + h - 22;
        heroX = wx + 22;
        rabbitX = wx + 60;
        heroFeet = feet(placement, wx, wy, heroX, ground);
        rabbitFeet = feet(placement, wx, wy, rabbitX, ground);
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int wx, int wy, int w, int h) {
        long t = shell.ticks;
        int ground = wy + h - 22;
        LevelStages.Placement placement = EventArt.level(shell, c, wx, wy, w, h, ground);
        layout(placement, wx, wy, w, h);
        int boxX = wx + 84;
        int postX = wx + 108;
        firstAidBox(c, boxX, feet(placement, wx, wy, boxX, ground));
        drawStarpost(shell, c, postX, feet(placement, wx, wy, postX, ground));

        // The rabbit faces the hero: Map_Animals5 art faces right and Obj_Animal mirrors it.
        SceneSprite bunny = shell.art.romFrame("rabbit", rabbitFrame);
        float bunnyFeet = rabbit != null ? rabbit.py() : rabbitFeet;
        if (bunny != null) {
            Poses.stand(c, bunny, rabbitX, bunnyFeet, SceneDraw.plain().withFlipX(rabbitFrame != 2));
        } else {
            c.fill(rabbitX - 5, Math.round(bunnyFeet) - 14, 10, 14, Colors.WHITE);
        }

        boolean glowing = phase == GLOW;
        int flash = glowing && (clock / 4) % 2 == 0 ? 0x60FFFFFF : 0;
        Poses.hero(shell, c, hero(shell), Poses.WAIT, t, heroX, heroFeet, SceneDraw.plain().withFlash(flash));
        if (glowing) {
            int g = clock - phaseStart;
            for (int i = 0; i < 6; i++) {
                int age = g - i * 6;
                double a = i * Math.PI / 3;
                sparkle(shell, c, heroX + (float) Math.cos(a) * 14, heroFeet - 18 + (float) Math.sin(a) * 14 - age / 3f,
                        age);
            }
            EventArt.hearts(c, heroX - 2, heroFeet - 44, clock);
        }

        if (phase != DONE) {
            for (int i = 0; i < rings.length; i++) {
                RomBody r = rings[i];
                if (r == null) {
                    continue;
                }
                if (collected[i] < 0) {
                    if (phase != PAY) {
                        continue;
                    }
                    // Attracted rings turn a frame every four (Obj_Attracted_RingAnimate).
                    SceneSprite ring = shell.art.romFrame("ring", ((clock - i * RING_GAP) / 4) & 3);
                    if (ring != null) {
                        c.draw(ring, r.px(), r.py(), SceneDraw.plain());
                    } else {
                        c.fill(Math.round(r.px()) - 4, Math.round(r.py()) - 4, 8, 8, EventArt.GOLD);
                    }
                } else {
                    sparkle(shell, c, r.px(), r.py(), collected[i]);
                }
            }
        }
        if (card != null && phase != DONE && phase != GLOW) {
            int top = lift != null ? Math.round(lift.py()) : Math.round(cardTop());
            drawCard(shell, c, card, heroX + 8, top);
            if (lift != null) {
                int age = clock - phaseStart;
                for (int i = 0; i < 4; i++) {
                    sparkle(shell, c, heroX - 12 + i * 13, top + CardRenderer.SMALL_H + 4 + (i % 2) * 6,
                            (age + i * 5) % 24);
                }
            }
        }
    }

    /** A first-aid box (the games have none): white case, red cross, grey handle. */
    private static void firstAidBox(SceneCanvas c, int cx, int feet) {
        c.fill(cx - 4, feet - 19, 8, 2, 0xFF6C6C6C);
        c.fill(cx - 11, feet - 17, 22, 17, Colors.BLACK);
        c.fill(cx - 10, feet - 16, 20, 15, Colors.WHITE);
        c.fill(cx - 10, feet - 3, 20, 2, 0xFFB6B6B6);
        c.fill(cx - 2, feet - 14, 4, 10, 0xFFDA2424);
        c.fill(cx - 5, feet - 11, 10, 4, 0xFFDA2424);
    }

    /**
     * The ROM's Starpost: idle (Ani_Starpost_Idle, frame 0); while its ball orbits, the post
     * without a ball (frame 1) and the ball (frame 2) circling 12 pixels around a point $14
     * above it, its angle stepping -$10 a frame from -$40 (loc_2D12E); once used, flashing
     * frames 0 and 4 every four frames (Ani_Starpost_Spinning).
     */
    private void drawStarpost(Shell shell, SceneCanvas c, int px, int feet) {
        SceneSprite idle = shell.art.romFrame("starpost", 0);
        if (idle == null) {
            c.fill(px - 1, feet - 40, 3, 40, 0xFFB6B6B6);
            c.fill(px - 4, feet - 46, 8, 8, 0xFFDA2424);
            return;
        }
        float originY = feet - (idle.height() - idle.originY());
        boolean orbiting = phase == ORBIT;
        int frame = orbiting ? 1 : lit && (shell.ticks / 4) % 2 == 1 ? 4 : 0;
        c.draw(shell.art.romFrame("starpost", frame), px, originY, SceneDraw.plain());
        if (orbiting) {
            int angle = (-0x10 * (clock - phaseStart) - 0x40) & 0xFF;
            double radians = angle * Math.PI * 2 / 256;
            c.draw(shell.art.romFrame("starpost", 2), Math.round(px + Math.cos(radians) * 12),
                    Math.round(originY - 0x14 + Math.sin(radians) * 12), SceneDraw.plain());
        }
    }
}
