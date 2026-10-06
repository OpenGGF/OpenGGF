package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.core.Card;

/**
 * An event picture that plays each choice as a short scene: {@link #show} records the detail
 * and starts a frame clock, {@link #busy} holds the event until {@link #playing} says the scene
 * is over, and the picture draws from the clock (and keeps the result afterwards). Details are
 * a verb with an optional argument after the first colon: "heal", "tinker:sonic:spin_dash+"
 * (a card id, with "+" when upgraded) or "search:potion:a,potion:b".
 */
abstract class TimedPicture extends EventPicture {
    /** The detail shown last, or null before any. */
    String detail;
    /** Frames since {@link #show}, or -1 before. */
    int clock = -1;
    private CardRenderer cards;

    @Override
    final void show(Shell shell, String shown) {
        detail = shown;
        clock = 0;
        begin(shell, verb(shown), argument(shown));
    }

    @Override
    final boolean busy() {
        return detail != null && playing();
    }

    @Override
    final void tick(Shell shell) {
        if (detail != null) {
            clock++;
        }
        step(shell);
    }

    /** Starts the scene for a choice. */
    abstract void begin(Shell shell, String verb, String argument);

    /** True while the scene for the current detail is still playing. */
    abstract boolean playing();

    /** One frame (sounds, motion); runs before and after any detail, for idle life. */
    void step(Shell shell) {
    }

    /** The verb of the detail shown, or "" before any. */
    String verb() {
        return detail == null ? "" : verb(detail);
    }

    static String verb(String detail) {
        int colon = detail.indexOf(':');
        return colon < 0 ? detail : detail.substring(0, colon);
    }

    static String argument(String detail) {
        int colon = detail.indexOf(':');
        return colon < 0 ? "" : detail.substring(colon + 1);
    }

    /** A card named the way details name them (its id, "+" when upgraded), or null. */
    static Card card(Shell shell, String named) {
        if (named == null || named.isEmpty()) {
            return null;
        }
        boolean upgraded = named.endsWith("+");
        String id = upgraded ? named.substring(0, named.length() - 1) : named;
        return shell.catalog.hasCard(id) ? new Card(shell.catalog.card(id), upgraded) : null;
    }

    /** Draws a hand-sized card ({@link CardRenderer#SMALL_W} x {@link CardRenderer#SMALL_H}) centred on x. */
    void drawCard(Shell shell, SceneCanvas c, Card card, int centreX, int top) {
        if (card == null) {
            return;
        }
        if (cards == null) {
            cards = new CardRenderer(shell);
        }
        cards.drawSmall(c, card, null, centreX - CardRenderer.SMALL_W / 2, top, true, false);
    }

    static String hero(Shell shell) {
        return shell.run != null ? shell.run.state().character().id() : "sonic";
    }

    /** The row of the floor under window column {@code px}: the level's, else the flat ground. */
    static int feet(LevelStages.Placement placement, int x, int y, int px, int ground) {
        return placement == null ? ground : placement.feet(px - x) + y;
    }

    /**
     * A collected ring's sparkle {@code age} frames in (Ani_RingSparkle: Map_Ring frames 4-7,
     * six frames each, then gone). Returns false once it is over.
     */
    static boolean sparkle(Shell shell, SceneCanvas c, float x, float y, int age) {
        if (age < 0 || age >= 24) {
            return age < 0;
        }
        SceneSprite s = shell.art.romFrame("ring", 4 + age / 6);
        if (s != null) {
            c.draw(s, x, y, SceneDraw.plain());
        } else {
            c.fill(Math.round(x) - 1, Math.round(y) - 1, 2, 2, 0xFFFFFFFF);
        }
        return true;
    }

    /**
     * A Lightning Shield spark {@code age} frames in (Obj_LightningShield_Spark, anim 1 of
     * Ani_LightningShield: frames $C, $D and the blank $17 a frame each, 20 frames in all).
     */
    static void spark(Shell shell, SceneCanvas c, float x, float y, int age) {
        if (age < 0 || age >= 20 || age % 3 == 2) {
            return;
        }
        SceneSprite s = shell.art.romFrame("lightning_sparks", age % 3 == 0 ? 0xC : 0xD);
        if (s != null) {
            c.draw(s, x, y, SceneDraw.plain());
        } else {
            c.fill(Math.round(x) - 1, Math.round(y) - 1, 3, 3, 0xFFFFFFB6);
        }
    }

    /** Obj_LightningShield_CreateSpark's four sparks (SparkVelocities), from (x, y). */
    static RomBody[] sparkBurst(float x, float y) {
        return new RomBody[] {
                new RomBody(x, y, -0x200, -0x200), new RomBody(x, y, 0x200, -0x200),
                new RomBody(x, y, -0x200, 0x200), new RomBody(x, y, 0x200, 0x200)};
    }

    /** One frame of a spark: MoveSprite2 then $18 gravity (Obj_LightningShield_Spark). */
    static void moveSpark(RomBody spark) {
        spark.moveSprite2();
        spark.yVel += RomBody.LIGHT_GRAVITY;
    }
}
