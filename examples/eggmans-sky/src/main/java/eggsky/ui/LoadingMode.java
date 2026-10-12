package eggsky.ui;

import com.openggf.mods.scene.SceneCanvas;
import eggsky.Game;
import eggsky.Mode;
import java.util.function.Supplier;

/**
 * Shows a short "entering atmosphere" card while the next screen is built (planets take a few
 * hundred milliseconds to remix), then switches to it. The card is drawn at least once before
 * the work starts so the hitch never shows a frozen game frame.
 */
public final class LoadingMode implements Mode {
    private final String title;
    private final String subtitle;
    private final Supplier<Mode> build;
    private int age;

    public LoadingMode(String title, String subtitle, Supplier<Mode> build) {
        this.title = title;
        this.subtitle = subtitle;
        this.build = build;
    }

    @Override
    public boolean live() {
        return false;
    }

    @Override
    public void update(Game g) {
        age++;
        if (age == 3) {
            g.setMode(build.get());
        }
    }

    @Override
    public void draw(Game g, SceneCanvas c) {
        c.clear(0x000008);
        // Streaks of re-entry fire converging on the centre.
        for (int i = 0; i < 40; i++) {
            long h = eggsky.core.Rng.mix(i * 7919L);
            float ang = (h & 0xFFFF) / 65536f * 6.283f;
            float r = 30 + ((h >>> 16) & 0xFF) + (g.ticks * 6 + i * 13) % 160;
            int x = (int) (g.width / 2 + Math.cos(ang) * r);
            int y = (int) (g.height / 2 + Math.sin(ang) * r * 0.6f);
            c.fill(x, y, 3, 1, i % 3 == 0 ? 0xFFFFC040 : 0xFFFF6020);
        }
        g.font.drawBig(c, title, g.width / 2f, g.height / 2f - 16, 2, 0xFFFFFFFF, 0xFFFFA040, 255);
        if (subtitle != null) {
            g.font.centre(c, subtitle, g.width / 2, g.height / 2 + 4, 0xFFC0D0FF);
        }
    }
}
