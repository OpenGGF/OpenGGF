package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;

/**
 * The run's hero in an event picture, drawn by its object centre ({@code x_pos}, {@code y_pos})
 * as the game positions a player in the air, so a jump or a knock-back can move it with
 * {@link Motion}. Standing poses use {@link Poses#hero}, feet on the floor.
 */
final class EventHero {
    /** Balancing on a ledge (Sonic3kAnimationIds.BALANCE), for unsteady legs. */
    static final int BALANCE = 0x06;

    private EventHero() {
    }

    /** The run's character id ("sonic", "tails", "knuckles"); Sonic outside a run. */
    static String id(Shell shell) {
        return shell.run != null ? shell.run.state().character().id() : "sonic";
    }

    /**
     * The standing y_radius: the object centre is this far above the floor ($13 for Sonic and
     * Knuckles, $F for Tails).
     */
    static int standRadius(String id) {
        return "tails".equals(id) ? 0x0F : 0x13;
    }

    /** The rolling y_radius ($E) a jumping player is drawn round. */
    static int rollRadius() {
        return 0x0E;
    }

    /**
     * Draws {@code anim} at {@code ticks} with the sprite's origin (the object centre) at
     * ({@code x}, {@code y}); Tails' tails follow him as the tails object does.
     */
    static void at(Shell shell, SceneCanvas c, String id, int anim, long ticks, float x, float y, SceneDraw style) {
        SceneSpriteSet set = shell.art.character(id);
        SceneSprite pose = anim == Poses.HURT || anim == Poses.DEATH ? Poses.still(set, anim)
                : Poses.frame(set, anim, ticks);
        if (pose == null) {
            return;
        }
        if ("tails".equals(id) && shell.art.hasRom()) {
            SceneSpriteSet tails = shell.art.rom().characterAccessory("tails");
            int tailFrame = switch (anim) {
                case Poses.ROLL, Poses.SPINDASH -> 5 + (int) ((ticks / 3) % 4);
                case Poses.WAIT, Poses.DUCK, Poses.LOOK_UP, Poses.VICTORY -> 0x22 + (int) ((ticks / 8) % 5);
                default -> -1;
            };
            if (tails != null && tailFrame >= 0 && tailFrame < tails.frameCount()) {
                c.draw(tails.frame(tailFrame), x, y, style);
            }
        }
        c.draw(pose, x, y, style);
    }
}
