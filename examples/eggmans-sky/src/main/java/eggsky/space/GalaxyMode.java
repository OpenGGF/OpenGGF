package eggsky.space;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneKeys;
import eggsky.Game;
import eggsky.Mode;
import eggsky.core.Colour;
import eggsky.core.Controls;
import eggsky.core.Rng;
import eggsky.core.Sound;
import eggsky.game.Catalog;
import eggsky.game.Player;
import eggsky.ui.Font;
import eggsky.ui.Ui;
import eggsky.world.StarSystem;
import java.util.List;

/**
 * The galaxy map: nearby stars by class, the hyperdrive's range, visited systems, the Chaos
 * Emerald signal and the way to the core. Pick a star with the arrows (or the mouse) and warp,
 * spending a Warp Cell.
 */
public final class GalaxyMode implements Mode {
    private final Mode back;
    private StarSystem here;
    private StarSystem selected;
    private float viewX;
    private float viewY;
    private float scale = 0.2f;
    private List<StarSystem> stars;
    private int age;
    private String message;
    private int messageTicks;

    public GalaxyMode(Mode back) {
        this.back = back;
    }

    @Override
    public boolean live() {
        return false;
    }

    @Override
    public void enter(Game g) {
        here = g.system();
        selected = here;
        viewX = here.x;
        viewY = here.y;
        refresh(g);
        g.sound.music(Sound.M_SPECIAL);
    }

    private void refresh(Game g) {
        stars = g.galaxy.near(viewX, viewY, Math.max(900, g.width / scale));
        if (Math.hypot(viewX, viewY) < 2000) {
            StarSystem core = g.galaxy.core();
            if (!stars.contains(core)) {
                stars.add(core);
            }
        }
    }

    @Override
    public void update(Game g) {
        age++;
        Controls in = g.in;
        if (messageTicks > 0) {
            messageTicks--;
        }
        if (in.backPressed || in.menuPressed && age > 2) {
            g.sound.music(Sound.M_DATA_SELECT);
            g.setMode(back);
            return;
        }
        int dx = in.leftRepeat ? -1 : in.rightRepeat ? 1 : 0;
        int dy = in.upRepeat ? -1 : in.downRepeat ? 1 : 0;
        if (dx != 0 || dy != 0) {
            StarSystem next = step(dx, dy);
            if (next != null) {
                selected = next;
                g.sound.sfx(Sound.PLINK, 2);
            }
        }
        if (in.wheel != 0 || g.ctx.keyPressed(SceneKeys.EQUAL) || g.ctx.keyPressed(SceneKeys.MINUS)) {
            float factor = in.wheel > 0 || g.ctx.keyPressed(SceneKeys.EQUAL) ? 1.25f : 0.8f;
            scale = Math.max(0.03f, Math.min(1.2f, scale * factor));
            refresh(g);
        }
        if (in.mouseLeftPressed) {
            StarSystem best = null;
            float bestD = 10;
            for (StarSystem s : stars) {
                float sx = sx(g, s.x);
                float sy = sy(g, s.y);
                float d = (float) Math.hypot(sx - in.mouseX, sy - in.mouseY);
                if (d < bestD) {
                    bestD = d;
                    best = s;
                }
            }
            if (best != null) {
                if (best == selected) {
                    warp(g);
                    return;
                }
                selected = best;
                g.sound.sfx(Sound.PLINK, 2);
            }
        }
        // Keep the selection in view.
        viewX += (selected.x - viewX) * 0.12f;
        viewY += (selected.y - viewY) * 0.12f;
        if (age % 20 == 0) {
            refresh(g);
        }
        if (in.confirmPressed && age > 2) {
            warp(g);
        }
    }

    /** The star nearest in a direction from the selection. */
    private StarSystem step(int dx, int dy) {
        StarSystem best = null;
        double bestScore = Double.MAX_VALUE;
        for (StarSystem s : stars) {
            if (s == selected) {
                continue;
            }
            double ox = s.x - selected.x;
            double oy = s.y - selected.y;
            double along = ox * dx + oy * dy;
            if (along <= 0) {
                continue;
            }
            double across = Math.abs(ox * -dy + oy * dx);
            double score = along + across * 2.2;
            if (score < bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    private void say(Game g, String text) {
        message = text;
        messageTicks = 150;
        g.sound.sfx(Sound.ERROR, 10);
    }

    private void warp(Game g) {
        Player p = g.player;
        if (selected == here) {
            say(g, "YOU ARE HERE");
            return;
        }
        float d = here.distanceTo(selected);
        if (selected.starClass == StarSystem.CORE) {
            if (p.emeraldCount() < 7) {
                say(g, "THE CORE REPELS YOU. BRING ALL SEVEN CHAOS EMERALDS.");
                return;
            }
            if (d > p.warpRange()) {
                say(g, "OUT OF RANGE: UPGRADE THE HYPERDRIVE");
                return;
            }
        } else if (d > p.warpRange()) {
            say(g, "OUT OF RANGE (" + Math.round(d) + " / " + Math.round(p.warpRange()) + " LY)");
            return;
        } else if (!p.canReach(selected.starClass)) {
            say(g, "THIS STAR NEEDS A " + (selected.starClass == StarSystem.RED ? "CADMIUM"
                    : selected.starClass == StarSystem.GREEN ? "EMERIL" : "INDIUM") + " DRIVE");
            return;
        }
        if (!p.cargo.has(Catalog.WARP_CELL, 1)) {
            say(g, "NO WARP CELL: CRAFT ONE (ANTIMATTER + HOUSING) OR BUY ONE");
            return;
        }
        p.cargo.remove(Catalog.WARP_CELL, 1);
        g.setMode(new WarpMode(selected, false));
    }

    private float sx(Game g, float x) {
        return g.width / 2f - 60 + (x - viewX) * scale;
    }

    private float sy(Game g, float y) {
        return g.height / 2f + (y - viewY) * scale;
    }

    @Override
    public void draw(Game g, SceneCanvas c) {
        Player p = g.player;
        Font f = g.font;
        c.clear(0x04040C);
        // Dust lanes swirling around the core.
        for (int i = 0; i < 260; i++) {
            long h = Rng.mix(i * 7919L + g.galaxy.seed);
            double r = ((h & 0xFFFF) / 65536.0) * 3600;
            double ang = ((h >>> 16) & 0xFFFF) / 65536.0 * Math.PI * 2 + r * 0.0012;
            float x = sx(g, (float) (Math.cos(ang) * r));
            float y = sy(g, (float) (Math.sin(ang) * r));
            if (x < 0 || y < 0 || x > g.width || y > g.height) {
                continue;
            }
            int col = Colour.alpha(i % 3 == 0 ? 0xFF6040A0 : 0xFF304080, 60);
            int s = 2 + (int) ((h >>> 40) & 3) * Math.max(1, (int) (scale * 8));
            c.fill((int) x - s / 2, (int) y - s / 2, s, s, col);
        }
        // The core's glow.
        float cx = sx(g, 0);
        float cy = sy(g, 0);
        for (int r = 40; r > 0; r -= 4) {
            int a = (40 - r) * 3;
            int rr = (int) (r * Math.max(0.6f, scale * 4));
            c.fill((int) cx - rr, (int) cy - rr / 2, rr * 2, rr, Colour.alpha(0xFFFFE0C0, Math.min(90, a)));
        }
        // Warp range.
        float range = p.warpRange() * scale;
        float hx = sx(g, here.x);
        float hy = sy(g, here.y);
        for (int i = 0; i < 90; i++) {
            double a = i * Math.PI * 2 / 90 + age * 0.002;
            c.fill((int) (hx + Math.cos(a) * range), (int) (hy + Math.sin(a) * range), 2, 1, 0x7060C0FF);
        }
        // Route.
        if (selected != here) {
            float tx = sx(g, selected.x);
            float ty = sy(g, selected.y);
            int steps = (int) Math.max(1, Math.hypot(tx - hx, ty - hy) / 4);
            boolean ok = here.distanceTo(selected) <= p.warpRange();
            for (int i = 0; i < steps; i += 2) {
                float t = i / (float) steps;
                c.fill((int) (hx + (tx - hx) * t), (int) (hy + (ty - hy) * t), 2, 2, ok ? 0xC060FF80 : 0xC0FF6060);
            }
        }
        for (StarSystem s : stars) {
            float x = sx(g, s.x);
            float y = sy(g, s.y);
            if (x < -4 || y < -4 || x > g.width + 4 || y > g.height + 4) {
                continue;
            }
            int col = s.colour();
            int size = s.starClass == StarSystem.CORE ? 5 : s.starClass == StarSystem.BLUE ? 3 : 2;
            if (s.starClass == StarSystem.BLACK_HOLE) {
                c.fill((int) x - 3, (int) y - 3, 6, 6, 0xFF8040C0);
                c.fill((int) x - 1, (int) y - 1, 2, 2, 0xFF000000);
            } else {
                c.fill((int) x - size, (int) y - size, size * 2, size * 2, Colour.alpha(col, 70));
                c.fill((int) x - size / 2, (int) y - size / 2, Math.max(1, size), Math.max(1, size), col);
            }
            if (p.visitedSystems.contains(s.id)) {
                c.fill((int) x - 4, (int) y + 4, 8, 1, 0xA0FFFFFF);
            }
            if (s.id == p.shrineSystem && (age / 10) % 2 == 0) {
                f.centre(c, "*", (int) x, (int) y - 11, 0xFF60FF80);
            }
        }
        // Current and selected.
        int pulse = (int) (Math.sin(age * 0.2) * 2);
        c.fill((int) hx - 6 - pulse, (int) hy, 3, 1, 0xFFFFD040);
        c.fill((int) hx + 4 + pulse, (int) hy, 3, 1, 0xFFFFD040);
        float selX = sx(g, selected.x);
        float selY = sy(g, selected.y);
        int r = 7;
        c.fill((int) selX - r, (int) selY - r, 4, 1, Ui.CYAN);
        c.fill((int) selX - r, (int) selY - r, 1, 4, Ui.CYAN);
        c.fill((int) selX + r - 3, (int) selY - r, 4, 1, Ui.CYAN);
        c.fill((int) selX + r, (int) selY - r, 1, 4, Ui.CYAN);
        c.fill((int) selX - r, (int) selY + r, 4, 1, Ui.CYAN);
        c.fill((int) selX - r, (int) selY + r - 3, 1, 4, Ui.CYAN);
        c.fill((int) selX + r - 3, (int) selY + r, 4, 1, Ui.CYAN);
        c.fill((int) selX + r, (int) selY + r - 3, 1, 4, Ui.CYAN);
        // Core direction, at the edge.
        double toCore = Math.atan2(-here.y, -here.x);
        int ax = (int) (g.width / 2 - 60 + Math.cos(toCore) * 120);
        int ay = (int) (g.height / 2 + Math.sin(toCore) * 90);
        f.centre(c, "CORE", ax, ay, 0xFFFFE0C0);
        // Info panel.
        int px = g.width - 128;
        Ui.panel(c, px, 6, 122, 148);
        f.draw(c, selected.name.toUpperCase(), px + 6, 11, Ui.WHITE);
        Ui.rule(c, px + 6, 20, 110);
        int ly = 25;
        f.draw(c, selected.className(), px + 6, ly, selected.colour());
        ly += 10;
        float d = here.distanceTo(selected);
        f.draw(c, Math.round(d) + " LY", px + 6, ly, d <= p.warpRange() ? Ui.GREEN : Ui.RED);
        ly += 10;
        if (selected.planetCount > 0) {
            f.draw(c, selected.planetCount + " PLANETS", px + 6, ly, Ui.GREY);
            ly += 10;
        }
        f.draw(c, selected.wealthName().toUpperCase(), px + 6, ly, Ui.GREY);
        ly += 10;
        f.draw(c, selected.conflictName().toUpperCase(), px + 6, ly, selected.conflict > 1 ? Ui.RED : Ui.GREY);
        ly += 10;
        f.draw(c, "CORE " + Ui.num((long) selected.distanceToCore()) + " LY", px + 6, ly, 0xFFFFE0C0);
        ly += 10;
        f.draw(c, p.visitedSystems.contains(selected.id) ? "VISITED" : "UNEXPLORED", px + 6, ly,
                p.visitedSystems.contains(selected.id) ? Ui.CYAN : Ui.GOLD);
        ly += 10;
        if (selected.id == p.shrineSystem) {
            f.draw(c, "EMERALD SIGNAL!", px + 6, ly, 0xFF60FF80);
            ly += 10;
        }
        if (!p.canReach(selected.starClass) && selected.starClass <= StarSystem.BLUE) {
            f.draw(c, "NEEDS DRIVE UPGRADE", px + 6, ly, Ui.RED);
            ly += 10;
        }
        f.draw(c, "WARP CELLS: " + p.cargo.count(Catalog.WARP_CELL), px + 6, 132, Ui.CYAN);
        f.draw(c, "RANGE " + Math.round(p.warpRange()) + " LY", px + 6, 142, Ui.GREY);
        // Footer.
        f.draw(c, "GALAXY MAP", 6, 6, Ui.GOLD);
        f.draw(c, "ARROWS SELECT  ENTER WARP  WHEEL/+- ZOOM  X BACK", 6, g.height - 10, Ui.DIM);
        if (messageTicks > 0) {
            int w = Font.width(message) + 12;
            Ui.panel(c, (g.width - w) / 2 - 60, g.height - 34, w, 14, 0xE0401018);
            f.centre(c, message, g.width / 2 - 60, g.height - 31, 0xFFFFA0A0);
        }
    }
}
