package eggsky.surface;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import eggsky.Game;
import eggsky.core.Colour;
import eggsky.game.Catalog;
import eggsky.game.Player;
import eggsky.ui.Font;
import eggsky.ui.Ui;
import eggsky.world.Biome;
import eggsky.world.Species;
import java.util.List;

/** The surface heads-up display, the analysis visor and dialog boxes. */
public final class SurfaceHud {
    private SurfaceHud() {
    }

    public static void draw(SurfaceMode s, Game g, SceneCanvas c) {
        Player p = g.player;
        Font f = g.font;
        // Currencies and vital systems, top left.
        Ui.panel(c, 3, 3, 122, s.planet.spec.biome.hazardous() ? 74 : 64);
        SceneSprite ring = g.art.frame("ring", (int) ((g.ticks / 8) % 4));
        if (ring != null) {
            c.draw(ring, 12, 12, SceneDraw.plain());
        }
        f.draw(c, Ui.num(p.rings), 22, 9, Ui.GOLD);
        c.fill(76, 9, 5, 5, 0xFF80FFE0);
        c.fill(77, 8, 3, 7, 0xFF80FFE0);
        f.draw(c, Ui.num(p.shards), 85, 9, 0xFF80FFE0);
        int y = 21;
        y = vital(c, f, g, "HULL", p.hull / p.maxHull(), 0xFFFF6040, y, p.hull < p.maxHull() * 0.25f);
        if (p.maxShield() > 0) {
            y = vital(c, f, g, "SHLD", p.shield / p.maxShield(), 0xFF40E8FF, y, false);
        }
        y = vital(c, f, g, "LIFE", p.life / p.maxLife(), 0xFF60FF80, y, p.life < p.maxLife() * 0.2f);
        if (s.planet.spec.biome.hazardous()) {
            String h = Biome.hazardName(s.planet.spec.biome.climate());
            y = vital(c, f, g, h.length() > 4 ? h.substring(0, 4) : h, p.hazard / p.maxHazard(),
                    hazardColour(s.planet.spec.biome.climate()), y, p.hazard < 20);
        }
        y = vital(c, f, g, "JETS", p.jet / p.maxJet(), 0xFFC0C0FF, y, false);
        vital(c, f, g, "FUEL", p.launchFuel / 100f, 0xFFFF9020, y, false);
        // Wanted stars, top centre.
        int stars = (int) s.wanted;
        if (s.wanted > 0.05f) {
            int cx = g.width / 2;
            for (int i = 0; i < 5; i++) {
                int x = cx - 40 + i * 16;
                boolean on = i < stars;
                boolean partial = i == stars;
                int col = on ? ((s.wantedFlash() / 6) % 2 == 0 ? 0xFFFF3030 : 0xFFFFFFFF) : 0xFF404858;
                star(c, x, 10, col);
                if (partial) {
                    int fill = Math.round((s.wanted - stars) * 10);
                    c.fill(x - 5, 16, fill, 2, 0xFFFF8080);
                }
            }
        }
        // Planet, time and weather, top right.
        String name = g.displayName(s.planet.spec);
        f.right(c, name.toUpperCase(), g.width - 5, 5, 0xFFFFFFFF);
        f.right(c, s.planet.spec.summary().toUpperCase(), g.width - 5, 14, 0xFF80C0FF);
        String status = s.weather.timeOfDay() + (s.weather.stormActive ? "  STORM" : "");
        f.right(c, status, g.width - 5, 23, s.weather.stormActive ? 0xFFFF8060 : 0xFFA0B0D0);
        if (s.sheltered() && s.hazardDrain() == 0 && s.planet.spec.biome.hazardous()) {
            f.right(c, "SHELTERED", g.width - 5, 32, 0xFF80FF80);
        }
        // Objective, bottom left.
        String objective = s.objective();
        if (objective != null) {
            int w = Math.min(g.width - 10, Font.width(objective) + 12);
            Ui.panel(c, 4, g.height - 26, w, 21, 0xD0201040);
            f.draw(c, "OBJECTIVE", 9, g.height - 23, Ui.GOLD);
            f.draw(c, objective, 9, g.height - 14, 0xFFFFFFFF);
        }
        // Laser heat and launch charge, near the pod.
        float shipSx = s.screenX(s.ship.x);
        float shipSy = s.screenY(s.ship.y);
        if (s.heat > 1 || s.overheated) {
            int bx = Math.round(shipSx) - 16;
            int by = Math.round(shipSy + s.ship.bottom + 8);
            Ui.bar(c, bx, by, 32, 3, s.heat / 100f, s.overheated ? 0xFFFF2020 : Colour.lerp(0xFFFFE040, 0xFFFF3010, s.heat / 100f),
                    s.overheated && (g.ticks / 4) % 2 == 0);
        }
        if (s.launchProgress() > 0) {
            int bx = Math.round(shipSx) - 24;
            int by = Math.round(shipSy + s.ship.top - 14);
            Ui.bar(c, bx, by, 48, 4, s.launchProgress(), 0xFFFF9020, false);
            f.centre(c, "LAUNCH", Math.round(shipSx), by - 10, 0xFFFFC060);
        }
        if (s.invincibleTicks() > 0) {
            f.centre(c, "INVINCIBLE " + (s.invincibleTicks() / 60 + 1), g.width / 2, 24, 0xFFFFFFFF);
        }
        // Controls reminder for a little while after landing.
        if (p.tutorial <= 1 && s.playing() && (g.ticks / 240) % 3 != 2) {
            f.centre(c, "ARROWS FLY  SPACE LASER  X SCAN/VISOR  C BOOST  ENTER MENU", g.width / 2,
                    g.height - 36, 0xC0FFFFFF);
        }
    }

    private static int hazardColour(int climate) {
        return switch (climate) {
            case Biome.CLIMATE_HOT -> 0xFFFF7030;
            case Biome.CLIMATE_COLD -> 0xFF80D0FF;
            case Biome.CLIMATE_TOXIC -> 0xFFB0FF30;
            default -> 0xFFFFFF40;
        };
    }

    private static int vital(SceneCanvas c, Font f, Game g, String label, float fraction, int colour, int y,
            boolean warn) {
        boolean flash = warn && (g.ticks / 8) % 2 == 0;
        f.draw(c, label, 8, y, flash ? 0xFFFF4040 : 0xFFC0C8E0);
        Ui.bar(c, 36, y + 1, 84, 5, fraction, colour, false);
        return y + 9;
    }

    private static void star(SceneCanvas c, int x, int y, int col) {
        c.fill(x - 1, y - 5, 3, 3, col);
        c.fill(x - 5, y - 2, 11, 3, col);
        c.fill(x - 3, y + 1, 7, 2, col);
        c.fill(x - 4, y + 3, 3, 2, col);
        c.fill(x + 2, y + 3, 3, 2, col);
    }

    /** The analysis visor: a scanning tint, brackets on the target and its catalogue card. */
    public static void drawVisor(SurfaceMode s, Game g, SceneCanvas c, float cx, float cy) {
        c.fill(0, 0, g.width, g.height, 0x3010A0C0);
        for (int y = (int) (g.ticks % 4); y < g.height; y += 4) {
            c.fill(0, y, g.width, 1, 0x1880FFFF);
        }
        int sweep = (int) ((g.ticks * 3) % g.height);
        c.fill(0, sweep, g.width, 2, 0x5080FFFF);
        Font f = g.font;
        f.draw(c, "ANALYSIS VISOR", 6, g.height - 46, 0xFF80FFFF);
        Object target = s.visorTarget;
        Species sp = s.speciesOf(target);
        if (target == null || sp == null) {
            f.centre(c, "NO TARGET - POINT AT A CREATURE, PLANT OR ROCK", g.width / 2, g.height / 2 + 40, 0xFFA0F0FF);
            return;
        }
        float tx;
        float ty;
        float hw;
        float hh;
        if (target instanceof Creature cr) {
            tx = cr.x;
            ty = cr.centreY();
            hw = cr.halfWidth() + 6;
            hh = cr.height() / 2 + 6;
        } else {
            Thing t = (Thing) target;
            tx = t.x;
            ty = t.centreY();
            hw = t.halfWidth() + 6;
            hh = t.height() / 2 + 6;
        }
        float sx = s.screenX(tx);
        float sy = ty - cy;
        boolean known = g.player.discovered.contains(sp.id);
        int col = known ? 0xFF80FF80 : 0xFFFFE040;
        bracket(c, sx - hw, sy - hh, sx + hw, sy + hh, col);
        if (!known) {
            int bw = 50;
            Ui.bar(c, Math.round(sx) - bw / 2, Math.round(sy + hh + 6), bw, 4, s.analysis, 0xFFFFE040, false);
        }
        // Catalogue card.
        int px = g.width - 150;
        int py = 40;
        Ui.panel(c, px, py, 146, 92, 0xD0081830);
        f.draw(c, known ? sp.name.toUpperCase() : "UNKNOWN " + (sp.kind == Species.FAUNA ? "CREATURE" :
                sp.kind == Species.FLORA ? "FLORA" : "MINERAL"), px + 5, py + 5, col);
        Ui.rule(c, px + 5, py + 14, 136);
        int ly = py + 19;
        f.draw(c, sp.kindName(), px + 5, ly, 0xFFC0D0F0);
        ly += 9;
        if (sp.kind == Species.FAUNA) {
            f.draw(c, "BODY: " + sp.body.name(), px + 5, ly, 0xFFC0D0F0);
            ly += 9;
            f.draw(c, known ? "TEMPER: " + sp.temperamentName() : "TEMPER: ???", px + 5, ly,
                    sp.temperament >= Species.AGGRESSIVE && known ? 0xFFFF8080 : 0xFFC0D0F0);
            ly += 9;
            f.draw(c, known ? "DIET: " + sp.diet : "DIET: ???", px + 5, ly, 0xFFC0D0F0);
            ly += 9;
            f.draw(c, known ? String.format("%.1fM  %.0fKG", sp.heightM, sp.weightKg) : "SIZE: ???", px + 5, ly, 0xFFC0D0F0);
            ly += 9;
            if (known) {
                for (String line : Ui.wrap(sp.note, 23)) {
                    f.draw(c, line, px + 5, ly, 0xFF90A0C0);
                    ly += 9;
                }
            }
        } else {
            Catalog.Item item = g.catalog.item(sp.yield);
            f.draw(c, "YIELDS: " + (item == null ? "-" : item.name()), px + 5, ly, item == null ? 0xFFC0D0F0 : item.colour());
            ly += 9;
            f.draw(c, known ? "CATALOGUED" : "HOLD SCAN TO ANALYSE", px + 5, ly, known ? 0xFF80FF80 : 0xFFFFE040);
        }
        if (known) {
            f.centre(c, "DISCOVERED", Math.round(sx), Math.round(sy - hh - 12), 0xFF80FF80);
        }
    }

    private static void bracket(SceneCanvas c, float x0, float y0, float x1, float y1, int col) {
        int l = 6;
        int a = Math.round(x0);
        int b = Math.round(y0);
        int e = Math.round(x1);
        int d = Math.round(y1);
        c.fill(a, b, l, 2, col);
        c.fill(a, b, 2, l, col);
        c.fill(e - l, b, l, 2, col);
        c.fill(e - 2, b, 2, l, col);
        c.fill(a, d - 2, l, 2, col);
        c.fill(a, d - l, 2, l, col);
        c.fill(e - l, d - 2, l, 2, col);
        c.fill(e - 2, d - l, 2, l, col);
    }

    public static void drawDialog(Game g, SceneCanvas c, String title, List<String> lines, String[] options,
            int choice) {
        Font f = g.font;
        int w = 320;
        int h = 34 + lines.size() * 9 + options.length * 12;
        int x = (g.width - w) / 2;
        int y = (g.height - h) / 2;
        Ui.dim(c, 0x70);
        Ui.panel(c, x, y, w, h);
        f.drawBig(c, title, g.width / 2f, y + 6, 2, 0xFFFFFFFF, Ui.GOLD, 255);
        int ly = y + 26;
        for (String line : lines) {
            f.centre(c, line, g.width / 2, ly, 0xFFE0E8FF);
            ly += 9;
        }
        ly += 4;
        for (int i = 0; i < options.length; i++) {
            int ow = Font.width(options[i]) + 16;
            int ox = g.width / 2 - ow / 2;
            if (i == choice) {
                Ui.focus(c, ox, ly - 2, ow, 11, g.ticks);
            }
            f.centre(c, options[i], g.width / 2, ly, i == choice ? 0xFFFFFFFF : 0xFF8090B0);
            ly += 12;
        }
    }
}
