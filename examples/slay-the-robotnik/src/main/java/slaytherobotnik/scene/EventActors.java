package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.ui.Colors;

/**
 * Pieces several event pictures share: the run's hero, the floor under a column, the ROM's
 * hurt knockback and invulnerability blink, bodies thrown the way objects move, damage
 * numbers and ring sparkles.
 */
final class EventActors {
    /** HurtCharacter: the player is knocked back at x_vel -$200, y_vel -$400 (8.8 pixels a frame). */
    static final int HURT_X_VEL = -0x200;
    static final int HURT_Y_VEL = -0x400;
    /** The hurt routine (Sonic_Index loc_122BE) adds $30 to y_vel every frame. */
    static final int HURT_GRAVITY = 0x30;
    /** HurtCharacter sets invulnerability_timer to 120 frames. */
    static final int INVULNERABLE = 120;
    /** Map_Ring frames 4-7 are the sparkle a collected ring leaves. */
    static final int SPARKLE_FRAMES = 4;

    private EventActors() {
    }

    /** The run's hero ("sonic", "tails", "knuckles"). */
    static String heroId(Shell shell) {
        return shell.run != null ? shell.run.state().character().id() : "sonic";
    }

    /** The window row of the floor under window column {@code px}: the level's, or {@code ground} without it. */
    static int floor(LevelStages.Placement placement, int x, int y, int px, int ground) {
        if (placement == null) {
            return ground;
        }
        int col = Math.max(0, Math.min(EventPicture.WIDTH - 1, px - x));
        return placement.feet(col) + y;
    }

    /**
     * Sonic_Display: while invulnerability_timer runs the player is drawn only when bit 2 of
     * the timer is set, four frames on and four off.
     */
    static boolean blinkVisible(int invulnerabilityTimer) {
        return invulnerabilityTimer <= 0 || (invulnerabilityTimer & 4) != 0;
    }

    /** The hero in pose {@code anim}, feet on {@code groundY}, facing left when {@code left}. */
    static void hero(Shell shell, SceneCanvas c, int anim, long ticks, float x, float groundY, boolean left,
            SceneDraw style) {
        Poses.hero(shell, c, heroId(shell), anim, ticks, x, groundY, style.withFlipX(left));
    }

    /**
     * A damage number rising from ({@code x}, {@code y}) as the fights show them (0.35 pixels a
     * frame), fading over its last ten frames, with an optional small caption under it ("MAX HP"),
     * kept inside the window that starts at column {@code left}; nothing once {@code age} passes
     * {@code life}.
     */
    static void number(Shell shell, SceneCanvas c, String text, String caption, int colour, int x, int y, int left,
            int age, int life) {
        if (age < 0 || age > life) {
            return;
        }
        int fade = Colors.alpha(colour, Math.min(1f, (life - age) / 10f));
        int tw = shell.font.width(text) * 2;
        int cw = caption == null ? 0 : shell.font.width(caption);
        int half = Math.max(tw, cw) / 2 + 2;
        int cx = Math.max(left + half, Math.min(left + EventPicture.WIDTH - half, x));
        int ty = Math.round(y - age * 0.35f);
        shell.font.drawOutlined(c, text, cx - tw / 2, ty, fade, 2);
        if (caption != null) {
            shell.font.drawOutlined(c, caption, cx - cw / 2, ty + 12, fade, 1);
        }
    }

    /** A collected ring's sparkle (Map_Ring frames 4-7, six frames each), {@code age} frames in; false once over. */
    static boolean sparkle(Shell shell, SceneCanvas c, float x, float y, int age) {
        if (age < 0 || age >= 24) {
            return age < 0;
        }
        SceneSprite s = shell.art.romFrame("ring", SPARKLE_FRAMES + age / 6);
        if (s != null) {
            c.draw(s, x, y, SceneDraw.plain());
        } else {
            c.fill(Math.round(x) - 1, Math.round(y) - 1, 3, 3, Colors.WHITE);
        }
        return true;
    }

    /**
     * A body moving as MoveSprite2 moves objects: position and velocity in 1/256 pixels, the
     * velocity added every frame, then {@code gravity} added to the vertical velocity (the ROM's
     * debris adds $18, the hurt player $30).
     */
    static final class Fling {
        int x;
        int y;
        int xVel;
        int yVel;
        final int gravity;

        Fling(float px, float py, int xVel, int yVel, int gravity) {
            this.x = Math.round(px * 256);
            this.y = Math.round(py * 256);
            this.xVel = xVel;
            this.yVel = yVel;
            this.gravity = gravity;
        }

        void step() {
            x += xVel;
            y += yVel;
            yVel += gravity;
        }

        float px() {
            return x / 256f;
        }

        float py() {
            return y / 256f;
        }
    }
}
