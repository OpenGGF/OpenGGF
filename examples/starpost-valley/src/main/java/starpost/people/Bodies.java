package starpost.people;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;

/** Poses and the one way every villager body is drawn: feet on the ground, mirrored to face. */
public final class Bodies {
    public static final int IDLE = 0;
    public static final int WALK = 1;
    public static final int HAPPY = 2;
    public static final int LAUGH = 3;
    public static final int SURPRISE = 4;
    public static final int LOOK_UP = 5;
    public static final int HOP = 6;

    private Bodies() {
    }

    /** A pose by name (event scripts): {@code idle}, {@code happy}, {@code laugh}, {@code surprise}, {@code look_up}. */
    public static int pose(String name) {
        return switch (name) {
            case "happy" -> HAPPY;
            case "laugh" -> LAUGH;
            case "surprise" -> SURPRISE;
            case "look_up" -> LOOK_UP;
            case "hop" -> HOP;
            default -> IDLE;
        };
    }

    /** Walking speed in pixels per tick: heroes stroll, Robotnik waddles, small animals hop. */
    public static float speed(String body) {
        if (body.equals("hero:sonic")) {
            return 1.5f;
        }
        if (body.startsWith("hero:")) {
            return 1.1f;
        }
        if (body.equals("animal:flicky")) {
            return 1.2f;
        }
        return body.startsWith("animal:") ? 0.7f : 0.8f;
    }

    /** How high a body floats: Flickies hover, the Egg Robo flies on its jet. */
    public static float lift(String body, long ticks) {
        if (body.equals("animal:flicky")) {
            return 10 + (float) Math.sin(ticks * 0.15) * 2;
        }
        if (body.equals("eggrobo")) {
            return 2 + (float) Math.sin(ticks * 0.06) * 2;
        }
        return 0;
    }

    /**
     * Draws a body standing at screen ({@code x}, {@code feet}). {@code hop} lifts it (animals
     * hop as they walk).
     */
    public static void draw(PeopleArt art, SceneCanvas canvas, String body, int pose, long ticks, float x, float feet,
            boolean facingLeft, float hop, SceneDraw tint) {
        SceneSprite sprite = art.pose(body, pose, ticks);
        if (sprite == null) {
            return;
        }
        boolean flip = facingLeft != PeopleArt.facesLeft(body);
        SceneDraw style = tint.withFlipX(flip);
        float ground = feet - lift(body, ticks) - hop;
        float originY = ground - (sprite.height() - sprite.originY()) * style.scaleY();
        if (body.equals("hero:tails") && (pose == IDLE)) {
            art.tails(canvas, x, originY, ticks, style);
        }
        if (body.equals("eggrobo")) {
            // The jet flame hangs below the body: raise the body so the flame is the bottom.
            originY -= PeopleArt.EGGROBO_JET_Y + 12 - (sprite.height() - sprite.originY());
            SceneSprite jet = art.eggRoboJet(ticks);
            if (jet != null) {
                float jx = x + (flip ? -PeopleArt.EGGROBO_JET_X : PeopleArt.EGGROBO_JET_X);
                canvas.draw(jet, jx, originY + PeopleArt.EGGROBO_JET_Y, style);
            }
        }
        canvas.draw(sprite, x, originY, style);
    }

    /** A soft shadow under a body standing on the belt-view field. */
    public static void shadow(SceneCanvas canvas, float x, float feet, int width) {
        canvas.fill(Math.round(x) - width / 2, Math.round(feet) - 2, width, 4, 0x60000000);
    }
}
