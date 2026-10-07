package eggsky.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import eggsky.Game;
import eggsky.Mode;
import eggsky.art.Art;
import eggsky.core.Colour;
import eggsky.core.Rng;
import eggsky.core.Sound;

/**
 * How every expedition begins: the Egg Mobile fleeing the Tornado through space, a lucky shot,
 * and a crash toward an unknown planet, with Eggman's log on screen. Any confirm skips.
 */
public final class IntroMode implements Mode {
    private static final int LENGTH = 520;
    private int age;
    private final String[] log = {
            "DR. EGGMAN'S LOG. STARDATE 0.7.",
            "THAT BLASTED HEDGEHOG CHASED ME OFF MOBIUS ENTIRELY.",
            "NO MATTER. OUT HERE, A GENIUS CAN CLAIM A WHOLE GALAXY.",
            "...WHAT'S THAT NOISE? NOT THE TORNADO AGAIN!",
    };

    @Override
    public boolean live() {
        return false;
    }

    @Override
    public void enter(Game g) {
        g.sound.music(Sound.M_BOSS);
    }

    @Override
    public void update(Game g) {
        age++;
        if (age == 330) {
            g.sound.sfx(Sound.MISSILE_EXPLODE);
            g.flash(0xFFFFFFFF, 10);
            g.shake = 6;
        }
        if (age == 360) {
            g.sound.sfx(Sound.SIREN);
        }
        if (age >= LENGTH || g.in.confirmPressed && age > 20) {
            g.land(0, true);
            g.banner("CRASH LANDED", "Repair the Egg Mobile's launch thrusters", 0xFFFF8040);
        }
    }

    @Override
    public void draw(Game g, SceneCanvas c) {
        c.clear(0x020410);
        for (int i = 0; i < 120; i++) {
            long h = Rng.mix(i * 7919L);
            float speed = 1 + (h & 3);
            int x = (int) Math.floorMod((long) ((h >>> 8) % g.width - age * speed * 2), (long) g.width);
            int y = (int) ((h >>> 20) % g.height);
            c.fill(x, y, (int) speed, 1, speed > 2 ? 0xFFFFFFFF : 0xFF8090C0);
        }
        // A planet looming at the right.
        int px = g.width - 40 + Math.max(0, (360 - age) / 6);
        Ui.disc(c, px, g.height / 2, 90, 0xFF2A5A9A, 0xFF0A1830);
        float shipX = 120 + (float) Math.sin(age * 0.05) * 10 + Math.max(0, age - 330) * 0.9f;
        float shipY = 100 + (float) Math.cos(age * 0.07) * 8 + Math.max(0, age - 330) * 0.35f;
        boolean hit = age > 330;
        SceneSprite body = g.art.frame("ship", Art.SHIP_BODY);
        SceneSprite head = g.art.frame("ship", hit ? Art.SHIP_HEAD_HURT : age > 250 ? Art.SHIP_HEAD_IDLE1
                : (age / 30) % 3 == 0 ? Art.SHIP_HEAD_LAUGH : Art.SHIP_HEAD_IDLE0);
        SceneDraw style = SceneDraw.plain();
        if (hit && (age / 3) % 2 == 0) {
            style = style.withFlash(0xFFFFFFFF);
        }
        if (head != null) {
            c.draw(head, shipX, shipY - 0x1C, style);
        }
        if (body != null) {
            c.draw(body, shipX, shipY, style);
        }
        if (hit) {
            for (int i = 0; i < 8; i++) {
                c.fill((int) shipX - 30 - i * 9, (int) shipY - 6 + (int) (Math.sin(age * 0.3 + i) * 3), 6, 6,
                        Colour.alpha(0xFF606060, 200 - i * 22));
            }
        }
        // The Tornado in pursuit.
        if (age > 160) {
            float tx = Math.min(30, -80 + (age - 160) * 1.2f) - Math.max(0, age - 360) * 1.5f;
            float ty = 90 + (float) Math.sin(age * 0.06) * 12;
            SceneSprite plane = g.art.frame("tornado", 0);
            SceneSprite prop = g.art.frame("tornado", 1 + (age / 2) % 4);
            if (plane != null) {
                c.draw(plane, tx, ty, SceneDraw.plain());
            }
            if (prop != null) {
                c.draw(prop, tx, ty, SceneDraw.plain());
            }
            if (age > 300 && age < 332 && age % 6 < 3) {
                c.fill((int) tx + 40 + (age - 300) * 3, (int) ty, 8, 2, 0xFFFFFF60);
            }
        }
        int lines = Math.min(log.length, 1 + age / 80);
        Ui.panel(c, 10, g.height - 54, g.width - 20, 46, 0xD0081020);
        for (int i = 0; i < lines; i++) {
            String text = log[i];
            int shown = i == lines - 1 ? Math.min(text.length(), (age - i * 80) / 2) : text.length();
            g.font.draw(c, text.substring(0, Math.max(0, shown)), 16, g.height - 49 + i * 10, i == 3 ? 0xFFFF8060 : 0xFFE0E8FF);
        }
        g.font.right(c, "CONFIRM: SKIP", g.width - 6, 4, 0xFF606890);
    }
}
