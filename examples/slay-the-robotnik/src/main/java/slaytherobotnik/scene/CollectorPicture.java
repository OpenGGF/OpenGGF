package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.content.Relics;
import slaytherobotnik.core.Relic;

/**
 * The Collector: an Egg Robo (Obj_EggRobo, Map_EggRobo) hovering over its heap of rings and
 * monitors in the act's level, built as ChildObjDat_919D0 builds it (legs and gun arm behind the
 * body) and bobbing with Swing_UpAndDown at the speeds sub_918E2 sets. "trade:id": the hero's
 * relic floats over to the robot's arm, it snatches it (sfx_Grab) and shoots up for joy with the
 * rise and drop the badnik makes as it takes off (loc_9164E, loc_9167E), drops the relic on its
 * heap, and the Collector's Badge floats back to the hero.
 */
final class CollectorPicture extends EventPicture {
    private static final int HERO_X = 24;
    private static final int ROBO_X = 88;
    /** sub_918E2: Swing_UpAndDown with acceleration 8 and a top speed of $100. */
    private static final int SWING_ACCEL = 8;
    private static final int SWING_MAX = 0x100;
    /** ChildObjDat_919D0: the legs at (-$C, $1C) and the gun arm at (-$1C, -4) from the body. */
    private static final int LEGS_DX = -0xC;
    private static final int LEGS_DY = 0x1C;
    private static final int ARM_DX = -0x1C;
    private static final int ARM_DY = -4;
    /** The hand-over's timing is the mod's own; the robot's motion is the ROM's. */
    private static final int GRAB_AT = 48;
    private static final int RETURN_AT = 74;
    private static final int TRADE_FRAMES = 118;

    /** The hover's offset and speed, in 1/256 pixels (y_vel, as Swing_UpAndDown drives it). */
    private int swingY;
    private int swingVel = SWING_MAX;
    private boolean swingDown;
    /** The leap's offset and speed: 0 swinging; 1 rising (loc_9164E); 2 falling back (loc_9167E). */
    private int leapY;
    private int leapVel;
    private int leap;
    private String relicId;
    private Relic given;
    private Relic badge;
    private boolean onHeap;
    private int age = -1;

    CollectorPicture(Shell shell) {
    }

    @Override
    void show(Shell shell, String detail) {
        relicId = detail.startsWith("trade:") ? detail.substring(6) : null;
        given = relic(shell, relicId);
        badge = relic(shell, Relics.COLLECTORS_BADGE);
        onHeap = false;
        age = 0;
    }

    private static Relic relic(Shell shell, String id) {
        if (id == null) {
            return null;
        }
        try {
            return shell.catalog.newRelic(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    boolean busy() {
        return age >= 0 && age < TRADE_FRAMES;
    }

    @Override
    void tick(Shell shell) {
        if (age >= 0) {
            age++;
            if (age == GRAB_AT) {
                shell.sfx(Sounds.SFX_GRAB);
                grab();
            }
            if (age == RETURN_AT - 4) {
                onHeap = true;
            }
            if (age == TRADE_FRAMES - 10) {
                shell.sfx(Sounds.SFX_RING);
            }
        }
        move();
    }

    /** The robot has its prize: it shoots up as the badnik does taking off (loc_9164E). */
    void grab() {
        leap = 1;
        leapVel = 0;
    }

    /** The robot's height relative to its resting hover, in pixels (down is positive). */
    float height() {
        return (swingY + leapY) / 256f;
    }

    /** One frame of the robot's hover, and of its leap when it has its prize. */
    void move() {
        // Swing_UpAndDown: accelerate one way until the top speed, then turn round.
        if (!swingDown) {
            swingVel -= SWING_ACCEL;
            if (swingVel <= -SWING_MAX) {
                swingDown = true;
            }
        } else {
            swingVel += SWING_ACCEL;
            if (swingVel >= SWING_MAX) {
                swingDown = false;
            }
        }
        swingY += swingVel;
        if (leap == 1) {
            leapVel -= 0x10;                        // loc_9164E: rise until y_vel reaches -$200
            if (leapVel <= -0x200) {
                leap = 2;
            }
        } else if (leap == 2) {
            leapVel += 0x20;                        // loc_9167E: fall back until y_vel is $100
            if (leapVel >= 0x100) {
                leap = 0;
                leapVel = 0;
            }
        } else if (leapY < 0) {
            // The badnik would hover on from its new height; here it sinks back over its heap.
            leapY = Math.min(0, leapY + 0x80);
        }
        leapY += leapVel;
    }

    /** The robot's vertical speed this frame, for its legs. */
    private int velocity() {
        return swingVel + leapVel;
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        int ground = y + h - 22;
        LevelStages.Placement placement = EventArt.level(shell, c, x, y, w, h, ground);
        int heapFloor = EventActors.floor(placement, x, y, x + ROBO_X, ground);
        heap(shell, c, x + ROBO_X, heapFloor, t);
        if (onHeap && given != null) {
            HudIcons.relic(shell, c, given, x + ROBO_X - 4, heapFloor - 30, 20);
            c.clip(x, y, w, h);
        }
        int rx = x + ROBO_X;
        int ry = heapFloor - 56 + Math.round((swingY + leapY) / 256f);
        drawRobot(shell, c, rx, ry, t);

        int heroFloor = EventActors.floor(placement, x, y, x + HERO_X, ground);
        EventActors.hero(shell, c, Poses.WAIT, t, x + HERO_X, heroFloor, false, SceneDraw.plain());
        if (age < 0 || age >= TRADE_FRAMES) {
            return;
        }
        // The hand-over: out to the arm's tip, then the badge back.
        float handX = x + HERO_X + 12;
        float handY = heroFloor - 30;
        float armX = rx + ARM_DX - 12 - reach();
        float armY = ry + ARM_DY;
        if (age < GRAB_AT && given != null) {
            float k = Math.max(0, (age - 10) / (float) (GRAB_AT - 10));
            icon(shell, c, given, handX + (armX - handX) * k, handY + (armY - handY) * k
                    - (float) Math.sin(k * Math.PI) * 20, x, y, w, h);
        } else if (age < RETURN_AT && given != null) {
            icon(shell, c, given, armX, armY, x, y, w, h);
        } else if (badge != null) {
            float k = Math.min(1f, (age - RETURN_AT) / (float) (TRADE_FRAMES - 12 - RETURN_AT));
            icon(shell, c, badge, armX + (handX - armX) * k, armY + (handY - armY) * k
                    - (float) Math.sin(k * Math.PI) * 20, x, y, w, h);
            if (k >= 1) {
                EventActors.sparkle(shell, c, handX, handY, age - (TRADE_FRAMES - 12));
            }
        }
    }

    /** How far the gun arm reaches out for the prize (a mod flourish: it leans in to take it). */
    private int reach() {
        if (age < GRAB_AT - 12 || age > RETURN_AT + 10) {
            return 0;
        }
        return age < GRAB_AT ? (age - GRAB_AT + 12) / 2 : Math.max(0, 6 - (age - GRAB_AT) / 4);
    }

    private static void icon(Shell shell, SceneCanvas c, Relic relic, float cx, float cy, int x, int y, int w,
            int h) {
        HudIcons.relic(shell, c, relic, Math.round(cx) - 11, Math.round(cy) - 11, 22);
        c.clip(x, y, w, h);                         // HudIcons.relic clears the clip
    }

    private void drawRobot(Shell shell, SceneCanvas c, int rx, int ry, long t) {
        // sub_91988: the body shows frame 1 or 3 (thruster lit) on alternate frames.
        SceneSprite body = shell.art.romFrame("egg_robo", t % 2 == 0 ? 1 : 3);
        SceneSprite arm = shell.art.romFrame("egg_robo", 2);
        // loc_916CC: legs frame 6 while rising, 5 while barely falling, 4 when dropping faster.
        int legsFrame = velocity() < 0 ? 6 : velocity() < 0x20 ? 5 : 4;
        SceneSprite legs = shell.art.romFrame("egg_robo", legsFrame);
        if (body == null) {
            c.fill(rx - 14, ry - 14, 28, 28, 0xFF6C6C6C);
            return;
        }
        c.draw(arm, rx + ARM_DX - reach(), ry + ARM_DY, SceneDraw.plain());
        c.draw(legs, rx + LEGS_DX, ry + LEGS_DY, SceneDraw.plain());
        c.draw(body, rx, ry, SceneDraw.plain());
    }

    /** The robot's hoard: rings and two monitors. */
    private static void heap(Shell shell, SceneCanvas c, int cx, int floor, long t) {
        EventArt.monitor(shell, c, cx - 24, floor, "5", t);
        EventArt.monitor(shell, c, cx + 22, floor, "8", t + 5);
        for (int i = 0; i < 6; i++) {
            EventArt.ring(shell, c, cx - 14 + i * 6, floor - 6 - (i % 2) * 6, t + i * 3);
        }
        for (int i = 0; i < 3; i++) {
            EventArt.ring(shell, c, cx - 8 + i * 7, floor - 18, t + i * 5);
        }
    }
}
