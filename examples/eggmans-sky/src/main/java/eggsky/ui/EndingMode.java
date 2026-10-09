package eggsky.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import eggsky.Game;
import eggsky.Mode;
import eggsky.art.Art;
import eggsky.art.PixelArt;
import eggsky.core.Colour;
import eggsky.core.Rng;
import eggsky.core.Sound;
import eggsky.game.Player;

/**
 * The galactic core: the seven Chaos Emeralds circle the Master Emerald of the Stars, Eggman
 * proclaims his empire, the expedition's records roll, and a new (harder, richer) galaxy begins
 * with all technology kept.
 */
public final class EndingMode implements Mode {
    private int age;
    private final SceneSprite[] gems = new SceneSprite[7];

    @Override
    public boolean live() {
        return false;
    }

    @Override
    public void enter(Game g) {
        g.sound.resetMusic();
        g.sound.music(Sound.M_ENDING);
        g.player.coreReached = true;
        int[] colours = {0xFF40E060, 0xFFFFE040, 0xFF4080FF, 0xFFFF70C0, 0xFF40F0F0, 0xFFFF4040, 0xFFE0E0F0};
        for (int i = 0; i < 7; i++) {
            gems[i] = PixelArt.emerald(colours[i]);
        }
        g.save();
    }

    @Override
    public void update(Game g) {
        age++;
        if (age == 200 || age == 420) {
            g.sound.sfx(Sound.SUPER_EMERALD);
            g.flash(0xFFFFFFFF, 12);
        }
        if (age > 900 && g.in.confirmPressed) {
            Player old = g.player;
            int next = old.galaxyNumber + 1;
            old.rings += 25000;
            old.shards += 2500;
            g.newExpedition(g.newSeed(), next, old);
            g.banner("GALAXY " + next, "The Eggman Empire expands", 0xFFFFD040);
            g.land(0, true);
        }
    }

    @Override
    public void draw(Game g, SceneCanvas c) {
        c.clear(0x000000);
        // Spiral arms of the core.
        for (int i = 0; i < 400; i++) {
            long h = Rng.mix(i * 31L);
            double r = (h & 0xFF) * 0.9 + 4;
            double a = ((h >>> 8) & 0xFFFF) / 65536.0 * Math.PI * 2 + r * 0.03 + age * 0.004;
            int x = (int) (g.width / 2 + Math.cos(a) * r * 1.5);
            int y = (int) (g.height / 2 + Math.sin(a) * r * 0.6);
            c.fill(x, y, 2, 1, Colour.alpha(i % 3 == 0 ? 0xFFFFE0A0 : 0xFF8070FF, 120 + (int) (h >>> 56) / 2));
        }
        float cx = g.width / 2f;
        float cy = g.height / 2f - 10;
        // The seven emeralds circle in.
        float radius = Math.max(26, 110 - age * 0.25f);
        for (int i = 0; i < 7; i++) {
            double a = age * 0.03 + i * Math.PI * 2 / 7;
            float x = cx + (float) Math.cos(a) * radius * 1.4f;
            float y = cy + (float) Math.sin(a) * radius * 0.55f;
            c.draw(gems[i], x, y, SceneDraw.plain().withScale(1.4f));
        }
        if (age > 200) {
            // The Master Emerald of the Stars.
            var master = g.art.set("hpz_emerald");
            float glow = (float) (0.5 + 0.5 * Math.sin(age * 0.08));
            c.fill((int) cx - 30, (int) cy - 24, 60, 48, Colour.alpha(0xFF60FFA0, (int) (40 + glow * 50)));
            if (master != null && master.frameCount() > 0) {
                c.draw(master.frame(0), cx, cy, SceneDraw.plain().withScale(2));
            } else {
                SceneSprite big = PixelArt.emerald(0xFF30E070);
                c.draw(big, cx, cy, SceneDraw.plain().withScale(3));
            }
        }
        if (age > 300) {
            SceneSprite body = g.art.frame("ship", Art.SHIP_BODY);
            SceneSprite head = g.art.frame("ship", (age / 12) % 2 == 0 ? Art.SHIP_HEAD_LAUGH : Art.SHIP_HEAD_IDLE0);
            float sx = Math.min(80, -60 + (age - 300) * 1.2f);
            if (head != null) {
                c.draw(head, sx, 150 - 0x1C, SceneDraw.plain().withFlipX(true));
            }
            if (body != null) {
                c.draw(body, sx, 150, SceneDraw.plain().withFlipX(true));
            }
        }
        Font f = g.font;
        if (age > 420) {
            f.drawBig(c, "THE EGGMAN EMPIRE", g.width / 2f, 12, 3, 0xFFFFFFFF, 0xFFFFC020, Math.min(255, (age - 420) * 4));
        }
        if (age > 520) {
            Player p = g.player;
            String[] lines = {
                    "\"HOHOHO! THE GALAXY IS MINE!\"",
                    "Species catalogued: " + p.statSpecies + "    Planets: " + p.statPlanets,
                    "Warps: " + p.statWarps + "    Heroes repelled: " + p.statSonicRepelled,
                    "Rings earned: " + Ui.num(p.statRingsEarned),
            };
            for (int i = 0; i < lines.length; i++) {
                if (age > 520 + i * 60) {
                    f.centre(c, lines[i], g.width / 2, g.height - 58 + i * 10, i == 0 ? Ui.GOLD : 0xFFE0E8FF);
                }
            }
        }
        if (age > 900 && (age / 20) % 2 == 0) {
            f.centre(c, "CONFIRM: CONQUER THE NEXT GALAXY", g.width / 2, g.height - 12, 0xFF80FFFF);
        }
    }
}
