package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import java.util.ArrayList;
import java.util.List;
import slaytherobotnik.ui.Ease;

/**
 * Things flying to where they now belong: claimed rings to the ring counter, a new card to the
 * deck, a monitor to its slot, a relic to the relic bar. Each one arcs up and over, eases into its
 * target, shrinks as it goes, and plays a sound on landing. {@link RunScreen} owns them and draws
 * them over everything, so a view only says what flies where.
 */
final class Flyers {
    /** Draws a flying thing centred on a point at a scale (1 is its normal size). */
    interface Picture {
        void draw(SceneCanvas c, float x, float y, float scale);
    }

    private static final class Flyer {
        final Picture picture;
        final float fromX;
        final float fromY;
        final float toX;
        final float toY;
        final float fromScale;
        final float toScale;
        final int life;
        final int landSfx;
        int delay;
        int age;

        Flyer(Picture picture, float fromX, float fromY, float toX, float toY, float fromScale, float toScale,
                int delay, int life, int landSfx) {
            this.picture = picture;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.fromScale = fromScale;
            this.toScale = toScale;
            this.delay = delay;
            this.life = life;
            this.landSfx = landSfx;
        }
    }

    private final List<Flyer> flying = new ArrayList<>();

    /**
     * Sends {@code picture} from one point to another over {@code life} frames after {@code delay},
     * scaling from {@code fromScale} to {@code toScale}; {@code landSfx} plays on arrival (-1 none).
     */
    void add(Picture picture, float fromX, float fromY, float toX, float toY, float fromScale, float toScale,
            int delay, int life, int landSfx) {
        flying.add(new Flyer(picture, fromX, fromY, toX, toY, fromScale, toScale, delay, Math.max(1, life), landSfx));
    }

    boolean busy() {
        return !flying.isEmpty();
    }

    void update(Shell shell) {
        for (int i = flying.size() - 1; i >= 0; i--) {
            Flyer f = flying.get(i);
            if (f.delay > 0) {
                f.delay--;
                continue;
            }
            if (++f.age >= f.life) {
                if (f.landSfx >= 0) {
                    shell.sfx(f.landSfx);
                }
                flying.remove(i);
            }
        }
    }

    void draw(SceneCanvas c) {
        for (Flyer f : flying) {
            if (f.delay > 0) {
                continue;
            }
            float p = f.age / (float) f.life;
            float e = Ease.inOutQuad(p);
            float x = f.fromX + (f.toX - f.fromX) * e;
            // An arc that rises a little more the further it travels.
            float lift = Math.min(40f, Math.abs(f.toX - f.fromX) * 0.15f + 12f);
            float y = f.fromY + (f.toY - f.fromY) * e - (float) Math.sin(p * Math.PI) * lift;
            float scale = f.fromScale + (f.toScale - f.fromScale) * Ease.inCubic(p);
            f.picture.draw(c, x, y, scale);
        }
    }
}
