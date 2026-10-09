package eggsky.space;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import eggsky.Game;
import eggsky.Mode;
import eggsky.core.Colour;
import eggsky.core.Rng;
import eggsky.core.Sound;
import eggsky.game.Player;
import eggsky.ui.EndingMode;
import eggsky.world.StarSystem;

/**
 * Hyperspace: a software-rendered tunnel of streaking stars and shifting rings while the Egg
 * Mobile shakes, then arrival in the new system (or, through a black hole, somewhere much nearer
 * the core; with all seven emeralds, the core itself).
 */
public final class WarpMode implements Mode {
    private static final int LENGTH = 200;
    private static final int TW = 200;
    private static final int TH = 112;

    private final StarSystem target;
    private final boolean viaRing;
    private int age;
    private final SceneImage tunnel = SceneImage.streaming(TW, TH);
    private final int[] pixels = new int[TW * TH];
    private final float[] angle = new float[TW * TH];
    private final float[] radius = new float[TW * TH];

    public WarpMode(StarSystem target, boolean viaRing) {
        this.target = target;
        this.viaRing = viaRing;
        for (int y = 0; y < TH; y++) {
            for (int x = 0; x < TW; x++) {
                float dx = (x - TW / 2f) / (TW / 2f);
                float dy = (y - TH / 2f) / (TH / 2f) * 0.6f;
                angle[y * TW + x] = (float) Math.atan2(dy, dx);
                radius[y * TW + x] = (float) Math.sqrt(dx * dx + dy * dy);
            }
        }
    }

    @Override
    public boolean live() {
        return false;
    }

    @Override
    public void enter(Game g) {
        g.sound.fadeOut();
        g.sound.sfx(viaRing ? Sound.BIG_RING : Sound.ENTER_SS);
        g.sound.sfx(Sound.GRAVITY_MACHINE, 10);
    }

    @Override
    public void update(Game g) {
        age++;
        g.shake = Math.min(4, age / 30f);
        renderTunnel(g);
        if (age == 60) {
            g.sound.music(Sound.M_SPECIAL);
        }
        if (age >= LENGTH) {
            arrive(g);
        }
    }

    private void renderTunnel(Game g) {
        float t = age / 60f;
        float hue = (age * 2) % 360;
        int a = viaRing ? 0xFFFFD040 : Colour.fromHsv(hue, 0.7f, 1f, 255);
        int b = viaRing ? 0xFFFF8020 : Colour.fromHsv(hue + 140, 0.8f, 0.6f, 255);
        float intensity = Math.min(1, age / 40f) * Math.min(1, (LENGTH - age) / 30f + 0.2f);
        for (int i = 0; i < pixels.length; i++) {
            float r = radius[i];
            if (r < 0.04f) {
                pixels[i] = 0xFFFFFFFF;
                continue;
            }
            float depth = 1 / r;
            float u = depth + t * 3;
            float v = angle[i] / (float) Math.PI * 4 + t * 0.6f;
            float stripe = (float) (0.5 + 0.5 * Math.sin(u * 3.0) * Math.sin(v * 3.0));
            float ring = (float) Math.pow(0.5 + 0.5 * Math.sin(u * 1.2), 6);
            int c = Colour.lerp(b, a, stripe);
            c = Colour.lerp(c, 0xFFFFFFFF, ring * 0.5f);
            float fade = Math.min(1, r * 1.4f) * intensity;
            pixels[i] = Colour.scale(c, Math.max(0.05f, fade * (0.25f + 0.75f * Math.min(1, depth * 0.15f))))
                    | 0xFF000000;
        }
        tunnel.update(pixels);
    }

    private void arrive(Game g) {
        Player p = g.player;
        StarSystem dest = target;
        if (target.starClass == StarSystem.BLACK_HOLE) {
            dest = beyondBlackHole(g, target);
            g.banner("THROUGH THE BLACK HOLE", "Flung " + Math.round(target.distanceToCore() - dest.distanceToCore())
                    + " ly toward the core", 0xFFC080FF);
            p.hull = Math.max(1, p.hull - p.maxHull() * 0.2f);
        }
        if (dest.starClass == StarSystem.CORE) {
            g.setMode(new EndingMode());
            return;
        }
        p.systemId = dest.id;
        p.statWarps++;
        p.planet = -1;
        if (p.visitedSystems.add(dest.id)) {
            int reward = 300 + dest.planetCount * 100;
            p.rings += reward;
            p.statRingsEarned += reward;
            g.banner(dest.name.toUpperCase(), "SYSTEM DISCOVERED  +" + reward + " RINGS", dest.colour());
        } else {
            g.banner(dest.name.toUpperCase(), dest.className() + " - " + dest.conflictName(), dest.colour());
        }
        g.sound.sfx(Sound.SIGNPOST);
        g.save();
        g.setMode(new SpaceMode(SpaceMode.ARRIVE_WARP, -1));
    }

    private static StarSystem beyondBlackHole(Game g, StarSystem hole) {
        Rng rng = new Rng(Rng.hash(hole.seed, g.player.statWarps));
        double r = Math.max(400, hole.distanceToCore() - rng.range(500f, 900f));
        double ang = Math.atan2(hole.y, hole.x) + rng.range(-0.4f, 0.4f);
        float x = (float) (Math.cos(ang) * r);
        float y = (float) (Math.sin(ang) * r);
        StarSystem best = null;
        float bestD = Float.MAX_VALUE;
        for (StarSystem s : g.galaxy.near(x, y, 300)) {
            if (s.planetCount > 0 && s.starClass <= StarSystem.BLUE) {
                float d = (float) Math.hypot(s.x - x, s.y - y);
                if (d < bestD) {
                    bestD = d;
                    best = s;
                }
            }
        }
        return best == null ? hole : best;
    }

    @Override
    public void draw(Game g, SceneCanvas c) {
        c.draw(tunnel, g.shakeX(), g.shakeY(), SceneDraw.plain().withScale(g.width / (float) TW, g.height / (float) TH));
        // The Egg Mobile's dashboard silhouette.
        c.fill(0, g.height - 30, g.width, 30, 0xE0101420);
        c.fill(0, g.height - 30, g.width, 1, 0xFF6070A0);
        g.font.centre(c, viaRing ? "GIANT RING WARP" : "HYPERDRIVE ENGAGED", g.width / 2, g.height - 24, 0xFFFFFFFF);
        g.font.centre(c, "DESTINATION: " + target.name.toUpperCase(), g.width / 2, g.height - 14, 0xFF80E0FF);
        if (age > LENGTH - 20) {
            c.fill(0, 0, g.width, g.height, Colour.alpha(0xFFFFFFFF, (age - (LENGTH - 20)) * 12));
        }
    }
}
