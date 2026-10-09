package flappytails;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Short-lived dressing that makes a moment land: ring sparkles, explosions, rising text, a
 * screen shake and a white flash. None of it touches the rules; the scene adds effects when
 * the run reports an event and advances them in {@code update}, and {@link #draw} only reads.
 */
final class Effects {
    /** A sprite animation playing once at a screen position, drifting left with the course. */
    private static final class Burst {
        final SceneSpriteSet set;
        final int firstFrame;
        final int frames;
        final int frameTicks;
        float x;
        final float y;
        final float drift;
        int age;

        Burst(SceneSpriteSet set, int firstFrame, int frames, int frameTicks, float x, float y, float drift) {
            this.set = set;
            this.firstFrame = firstFrame;
            this.frames = frames;
            this.frameTicks = frameTicks;
            this.x = x;
            this.y = y;
            this.drift = drift;
        }

        boolean done() { return age >= frames * frameTicks; }
    }

    /** Text that rises and fades. */
    private static final class Popup {
        final String text;
        final float x;
        float y;
        final int color;
        int age;

        Popup(String text, float x, float y, int color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
        }
    }

    private final List<Burst> bursts = new ArrayList<>();
    private final List<Popup> popups = new ArrayList<>();
    private int shake;
    private int shakeStrength;
    private int flash;
    private int flashLength = 1;
    private int flashColor = 0xFFFFFF;
    private long ticks;

    /** A ring's sparkle (frames 4 to 7 of the ROM's ring, six ticks each, as {@code Ani_RingSparkle}). */
    void sparkle(SceneSpriteSet ring, float x, float y, float drift) {
        if (ring != null) bursts.add(new Burst(ring, 4, 4, 6, x, y, drift));
    }

    /** The ROM's explosion (five frames). */
    void explode(SceneSpriteSet explosion, float x, float y, float drift) {
        if (explosion != null) bursts.add(new Burst(explosion, 0, 5, 4, x, y, drift));
    }

    void popup(String text, float x, float y, int color) {
        popups.add(new Popup(text, x, y, color));
    }

    /** Shakes the screen for {@code ticks} ticks by up to {@code strength} pixels. */
    void shake(int ticks, int strength) {
        shake = Math.max(shake, ticks);
        shakeStrength = Math.max(shakeStrength, strength);
    }

    /** Flashes the screen {@code rgb}, fading over {@code ticks} ticks. */
    void flash(int rgb, int ticks) {
        flash = ticks;
        flashLength = Math.max(1, ticks);
        flashColor = rgb & 0xFFFFFF;
    }

    void clear() {
        bursts.clear();
        popups.clear();
        shake = 0;
        flash = 0;
    }

    void update() {
        ticks++;
        for (Burst burst : bursts) {
            burst.age++;
            burst.x -= burst.drift;
        }
        bursts.removeIf(Burst::done);
        for (Popup popup : popups) {
            popup.age++;
            popup.y -= popup.age < 20 ? 1f : 0.25f;
        }
        popups.removeIf(p -> p.age > 50);
        if (shake > 0 && --shake == 0) shakeStrength = 0;
        if (flash > 0) flash--;
    }

    /** The screen offset this tick: a deterministic jitter that settles as the shake ends. */
    int shakeX() {
        if (shake <= 0) return 0;
        return (int) Math.round(Math.sin(ticks * 2.3) * shakeStrength * Math.min(1, shake / 8.0));
    }

    int shakeY() {
        if (shake <= 0) return 0;
        return (int) Math.round(Math.cos(ticks * 3.1) * shakeStrength * Math.min(1, shake / 8.0));
    }

    void draw(SceneCanvas canvas) {
        for (Burst burst : bursts) {
            int frame = burst.firstFrame + Math.min(burst.frames - 1, burst.age / burst.frameTicks);
            canvas.draw(burst.set.frame(frame), burst.x, burst.y, SceneDraw.plain());
        }
        for (Popup popup : popups) {
            float alpha = popup.age < 35 ? 1f : 1f - (popup.age - 35) / 15f;
            int argb = Math.round(alpha * 255) << 24 | popup.color;
            Hud.outlined(canvas, popup.text, Math.round(popup.x - Hud.width(popup.text, 1) / 2f),
                    Math.round(popup.y), 1, argb);
        }
    }

    /** The flash, drawn over everything. */
    void drawFlash(SceneCanvas canvas) {
        if (flash <= 0) return;
        int alpha = Math.round(220f * flash / flashLength);
        canvas.fill(0, 0, canvas.width(), canvas.height(), alpha << 24 | flashColor);
    }
}
