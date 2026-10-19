package sonicbros.view;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import java.util.ArrayList;
import java.util.List;
import sonicbros.sim.Arena;
import sonicbros.sim.Event;
import sonicbros.sim.Fighter;
import sonicbros.sim.Hit;
import sonicbros.sim.Item;
import sonicbros.sim.Match;
import sonicbros.sim.Move;
import sonicbros.sim.Projectile;
import sonicbros.sim.Rules;

/**
 * Draws a match: the zone's parallax backdrop, the carved island and platforms through a
 * camera that follows and zooms to the fighters, every fighter with its shields and power-ups,
 * projectiles, monitors, hit sparks, dust, explosions and KO blasts, the off-screen bubbles and
 * the damage panels. Reads the {@link Match} and its events; never changes it.
 */
public final class MatchView {
    private static final int P_SPARK = 0;
    private static final int P_RING = 1;
    private static final int P_DUST = 2;
    private static final int P_EXPLOSION = 3;
    private static final int P_BEAM = 4;
    private static final int P_SPARKLE = 5;
    private static final int P_STAR_KO = 6;
    private static final int P_MONITOR = 7;
    private static final int P_TEXT = 8;
    private static final int P_FLASH = 9;

    /** One effect, in world coordinates unless noted. */
    private static final class Particle {
        int type;
        double x, y, vx, vy, dx, dy;
        int age, life, color, size;
        String text;
    }

    private final Art art;
    private final FighterArt fighters;
    private final BigFont font;
    private final StageBuilder.Built stage;
    private final Match match;
    private final List<Particle> particles = new ArrayList<>();
    private final boolean[] cpu;
    public double camX;
    public double camY;
    public double zoom = 1;
    private double shake;
    private long ticks;
    private int width = 400;
    private int height = 224;
    private final double[] hudShake;
    private final double[] shownDamage;
    private int goTimer = -1;
    /** Draw the damage panels (off for the move list's demos). */
    public boolean hud = true;
    private String[] tags;
    private int[] colors;

    public MatchView(Art art, BigFont font, StageBuilder.Built stage, Match match) {
        this.art = art;
        this.font = font;
        this.fighters = new FighterArt(art);
        this.stage = stage;
        this.match = match;
        this.cpu = new boolean[match.fighters.size()];
        for (int i = 0; i < cpu.length; i++) cpu[i] = match.isCpu(i);
        hudShake = new double[cpu.length];
        shownDamage = new double[cpu.length];
        tags = new String[cpu.length];
        colors = new int[cpu.length];
        for (int i = 0; i < cpu.length; i++) {
            tags[i] = cpu[i] ? "CPU" : "P" + (i + 1);
            colors[i] = Costumes.playerColor(i, cpu[i]);
        }
        camX = 0;
        camY = stage.arena().floorLevel() - 40;
        zoom = 0.9;
    }

    // ------------------------------------------------------------------ update

    /** Reads the match's events and moves the camera; call after each match step (or idle tick). */
    public void update(List<Event> events, int width, int height, boolean stepped) {
        this.width = width;
        this.height = height;
        ticks++;
        if (stepped) {
            for (Event e : events) onEvent(e);
        }
        for (int i = 0; i < cpu.length; i++) {
            Fighter f = match.fighters.get(i);
            if (f.damage > shownDamage[i]) { hudShake[i] = Math.min(6, 2 + (f.damage - shownDamage[i]) * 0.3); }
            shownDamage[i] = f.damage;
            if (hudShake[i] > 0) hudShake[i] = Math.max(0, hudShake[i] - 0.4);
        }
        for (Particle p : particles) {
            p.age++;
            p.x += p.vx;
            p.y += p.vy;
            if (p.type == P_DUST) { p.vx *= 0.9; p.vy *= 0.9; }
            if (p.type == P_MONITOR) p.vy += 0.15;
            if (p.type == P_SPARKLE) p.vy += 0.02;
        }
        particles.removeIf(p -> p.age >= p.life);
        if (shake > 0) shake = Math.max(0, shake - 0.35);
        if (match.countdown == 0 && goTimer < 0) goTimer = 0;
        if (goTimer >= 0) goTimer++;
        updateCamera();
    }

    private double lockX = Double.NaN;
    private double lockY;
    private double lockZoom;

    /** Fixes the camera (the move list's close-up). */
    public void lockCamera(double x, double y, double z) {
        lockX = camX = x;
        lockY = camY = y;
        lockZoom = zoom = z;
    }

    private void updateCamera() {
        if (!Double.isNaN(lockX)) { camX = lockX; camY = lockY; zoom = lockZoom; return; }
        Arena a = match.arena;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        int count = 0;
        for (Fighter f : match.fighters) {
            if (!f.alive() || f.state == Fighter.State.DEAD) continue;
            double fx = Math.max(a.blastLeft + 30, Math.min(a.blastRight - 30, f.x));
            double fy = Math.max(a.blastTop + 40, Math.min(a.blastBottom - 20, f.y));
            minX = Math.min(minX, fx);
            maxX = Math.max(maxX, fx);
            minY = Math.min(minY, fy - f.height());
            maxY = Math.max(maxY, fy);
            count++;
        }
        // Keep the stage itself framed.
        minX = Math.min(minX, a.left + 40);
        maxX = Math.max(maxX, a.right() - 40);
        minY = Math.min(minY, a.floorLevel() - 70);
        maxY = Math.max(maxY, a.floorLevel() + 30);
        if (count == 0) { minX = a.left; maxX = a.right(); }
        double boxW = maxX - minX + 110;
        double boxH = maxY - minY + 90;
        double hudSpace = 46;
        double targetZoom = Math.min(width / boxW, (height - hudSpace) / boxH);
        targetZoom = Math.max(0.58, Math.min(1.15, targetZoom));
        double tx = (minX + maxX) / 2;
        double ty = (minY + maxY) / 2 + hudSpace / 2 / targetZoom * 0.6;
        zoom += (targetZoom - zoom) * 0.06;
        camX += (tx - camX) * 0.08;
        camY += (ty - camY) * 0.08;
        // Never show far beyond the blast lines.
        double halfW = width / 2.0 / zoom;
        double halfH = height / 2.0 / zoom;
        camX = Math.max(a.blastLeft + halfW - 40, Math.min(a.blastRight - halfW + 40, camX));
        camY = Math.max(a.blastTop + halfH - 40, Math.min(a.blastBottom - halfH + 60, camY));
    }

    private Particle add(int type, double x, double y, int life) {
        Particle p = new Particle();
        p.type = type;
        p.x = x;
        p.y = y;
        p.life = life;
        particles.add(p);
        return p;
    }

    private void onEvent(Event e) {
        switch (e.type()) {
            case Event.HIT -> {
                int kb = e.a();
                Particle p = add(P_SPARK, e.x(), e.y(), kb > 100 ? 16 : 11);
                p.size = kb > 140 ? 3 : kb > 80 ? 2 : 1;
                p.color = switch (e.b()) {
                    case Hit.FX_FIRE -> 0xFFFF8020;
                    case Hit.FX_ELECTRIC -> 0xFF60E0FF;
                    case Hit.FX_EXPLOSION -> 0xFFFFC040;
                    case Hit.FX_SLASH -> 0xFFE0E8FF;
                    default -> kb > 120 ? 0xFFFF3030 : 0xFFFFE060;
                };
                Particle r = add(P_RING, e.x(), e.y(), 10);
                r.color = p.color;
                r.size = p.size;
                if (e.b() == Hit.FX_EXPLOSION || e.b() == Hit.FX_FIRE && kb > 60) explosion(e.x(), e.y(), 1);
                if (kb > 120) shake = Math.max(shake, 3);
            }
            case Event.BLOCK -> {
                Particle p = add(P_RING, e.x(), e.y(), 8);
                p.color = e.b() == 1 ? 0xFFFFFFFF : 0xFF80C0FF;
                p.size = 1;
            }
            case Event.DUST -> {
                int n = e.a() == 1 ? 4 : 2;
                for (int i = 0; i < n; i++) {
                    Particle p = add(P_DUST, e.x() + (i - n / 2.0) * 5, e.y() - 3, 16 + i * 2);
                    p.vx = (i - (n - 1) / 2.0) * 0.6;
                    p.vy = -0.3;
                }
            }
            case Event.EXPLOSION -> explosion(e.x(), e.y(), e.a() > 20 ? 2 : 1);
            case Event.KO -> koBlast(e.x(), e.y(), e.a(), e.port());
            case Event.STAR_KO -> {
                Particle p = add(P_STAR_KO, e.x(), e.y(), 70);
                p.color = colorOf(e.port());
                shake = Math.max(shake, 2);
            }
            case Event.SHIELD_BREAK -> {
                for (int i = 0; i < 10; i++) {
                    Particle p = add(P_SPARKLE, e.x(), e.y(), 30);
                    double ang = i / 10.0 * Math.PI * 2;
                    p.vx = Math.cos(ang) * 2.2;
                    p.vy = Math.sin(ang) * 2.2;
                    p.color = 0xFFFF80FF;
                }
                text(e.x(), e.y() - 30, "BREAK!", 0xFFFF60FF);
            }
            case Event.ITEM -> {
                Particle p = add(P_MONITOR, e.x(), e.y(), 34);
                p.size = e.a();
                p.vy = -2.2;
                if (e.port() >= 0) text(e.x(), e.y() - 28, Item.name(e.a()), 0xFFFFFFFF);
            }
            case Event.SPARKLE -> {
                int n = e.a() == 0 ? 3 : 10;
                for (int i = 0; i < n; i++) {
                    Particle p = add(P_SPARKLE, e.x() + (i * 37 % 21) - 10, e.y() + (i * 53 % 25) - 12, 24);
                    p.vy = -0.6;
                    p.color = e.a() == 2 ? 0xFFFFE040 : 0xFFFFFFFF;
                }
            }
            case Event.REFLECT -> {
                Particle p = add(P_RING, e.x(), e.y(), 10);
                p.color = 0xFF80F0FF;
                p.size = 2;
            }
            case Event.TECH -> {
                Particle p = add(P_FLASH, e.x(), e.y(), 8);
                p.color = 0xFFFFFFFF;
            }
            case Event.SHAKE -> shake = Math.max(shake, e.a());
            default -> { }
        }
    }

    private void text(double x, double y, String t, int color) {
        Particle p = add(P_TEXT, x, y, 50);
        p.text = t;
        p.color = color;
        p.vy = -0.5;
    }

    private void explosion(double x, double y, int size) {
        Particle p = add(P_EXPLOSION, x, y, 20);
        p.size = size;
    }

    private void koBlast(double x, double y, int side, int port) {
        Particle p = add(P_BEAM, x, y, 56);
        p.color = colorOf(port);
        // The beam points from the blast line back toward the stage centre.
        double cx = 0;
        double cy = match.arena.floorLevel() - 40;
        double dx = cx - x, dy = cy - y;
        double len = Math.max(1, Math.hypot(dx, dy));
        p.dx = dx / len;
        p.dy = dy / len;
        for (int i = 0; i < 14; i++) {
            Particle s = add(P_SPARKLE, x, y, 30 + i);
            double ang = Math.atan2(p.dy, p.dx) + (i - 7) * 0.12;
            double sp = 2 + (i % 5);
            s.vx = Math.cos(ang) * sp;
            s.vy = Math.sin(ang) * sp;
            s.color = i % 2 == 0 ? p.color : 0xFFFFFFFF;
        }
        Particle flash = add(P_FLASH, 0, 0, 6);
        flash.color = 0xFFFFFFFF;
        flash.size = 1;
        shake = Math.max(shake, 7);
    }

    private int colorOf(int port) {
        if (port < 0 || port >= match.fighters.size()) return 0xFFFFFFFF;
        Fighter f = match.fighters.get(port);
        if (match.rules.teams) return Costumes.teamColor(f.team);
        return colors[port];
    }

    /** Overrides the over-head tags and colours (players keep their slot's number and colour). */
    public void setPlayers(String[] tags, int[] colors) {
        this.tags = tags.clone();
        this.colors = colors.clone();
    }

    // ------------------------------------------------------------------ drawing

    private double sx(double wx) { return (wx - camX) * zoom + width / 2.0 + shakeX(); }

    private double sy(double wy) { return (wy - camY) * zoom + height / 2.0 + shakeY(); }

    private double shakeX() { return shake > 0 ? Math.sin(ticks * 2.7) * shake : 0; }

    private double shakeY() { return shake > 0 ? Math.cos(ticks * 3.3) * shake * 0.6 : 0; }

    public void draw(SceneCanvas c, boolean paused) {
        drawBackdrop(c);
        drawStage(c);
        drawItems(c);
        for (Fighter f : match.fighters) drawShadow(c, f);
        for (Fighter f : match.fighters) drawFighter(c, f);
        drawProjectiles(c);
        drawParticles(c, false);
        for (Fighter f : match.fighters) drawTag(c, f);
        drawOffscreen(c);
        drawParticles(c, true);
        if (hud) {
            drawHud(c);
            drawBanner(c);
        }
    }

    private void drawBackdrop(SceneCanvas c) {
        var bd = stage.backdrop();
        int imgH = bd.image().height();
        int top = (int) Math.round(stage.stage().bgTop() + (camY - match.arena.floorLevel() + 40) * 0.12);
        top = Math.max(0, Math.min(Math.max(0, imgH - height), top));
        c.clear(0x000000);
        if (imgH >= height) {
            c.drawBackdrop(bd, top, camX * 1.0, ticks);
        } else {
            c.drawBackdrop(bd, 0, 0, imgH, imgH, 0, camX, ticks);
            // Short backdrops: stretch the last row down the rest of the screen.
            c.drawRegion(bd.image(), 0, imgH - 1, Math.min(bd.image().width(), width), 1, 0, imgH, width, height - imgH, SceneDraw.plain());
        }
        if (stage.stage().tint() != 0) c.fill(0, 0, width, height, stage.stage().tint());
    }

    private void drawStage(SceneCanvas c) {
        SceneDraw style = SceneDraw.plain().withScale((float) zoom);
        c.draw(stage.island(), (float) sx(stage.islandX()), (float) sy(stage.islandY()), style);
        Arena a = match.arena;
        long f = match.frame;
        for (int i = 0; i < a.platforms.size(); i++) {
            Arena.Platform p = a.platforms.get(i);
            StageBuilder.PlatformArt pa = stage.platforms().get(i);
            c.draw(pa.image(), (float) sx(p.left(f) + pa.offsetX()), (float) sy(p.top(f) + pa.offsetY()), style);
        }
    }

    private void drawShadow(SceneCanvas c, Fighter f) {
        if (!f.onStage()) return;
        Arena a = match.arena;
        double floor = Double.NaN;
        if (a.over(f.x) && f.y <= a.surfaceAt(f.x) + 1) floor = a.surfaceAt(f.x);
        for (Arena.Platform p : a.platforms) {
            double top = p.top(match.frame);
            if (f.x >= p.left(match.frame) && f.x <= p.right(match.frame) && f.y <= top + 1 && (Double.isNaN(floor) || top < floor)) floor = top;
        }
        if (Double.isNaN(floor) || floor - f.y > 140) return;
        double fade = 1 - (floor - f.y) / 140.0;
        SceneImage sh = art.image("shadow");
        double w = sh.width() * zoom * (f.def.stats.width() / 20.0) * (0.6 + 0.4 * fade);
        c.draw(sh, (float) (sx(f.x) - w / 2), (float) (sy(floor) - 3 * zoom),
                SceneDraw.plain().withScale((float) (w / sh.width()), (float) zoom).withAlpha((float) fade));
    }

    private void drawFighter(SceneCanvas c, Fighter f) {
        if (!f.alive() || f.state == Fighter.State.DEAD) return;
        double x = sx(f.x);
        double feet = sy(f.y);
        double center = sy(f.centerY());
        if (f.shaking()) x += ((match.frame + f.port) % 2 == 0 ? 2 : -2) * zoom;
        float alpha = 1f;
        if (f.state == Fighter.State.RESPAWN || f.invincible > 0 && f.invincible % 4 < 2) alpha = 0.55f;
        int flash = 0;
        if (f.superTimer > 0) flash = ((int) (120 + 80 * Math.sin(ticks * 0.3)) << 24) | 0xFFE040;
        else if (f.starTimer > 0) flash = (90 << 24) | switch ((int) (ticks / 3 % 4)) {
            case 0 -> 0xFF4040;
            case 1 -> 0x40FF40;
            case 2 -> 0x4080FF;
            default -> 0xFFFF40;
        };
        else if (f.state == Fighter.State.ATTACK && f.move != null && f.move.chargeFrame >= 0 && f.moveFrame == f.move.chargeFrame && f.charge > 0) {
            flash = ((int) (60 + 60 * Math.sin(ticks * 0.8)) << 24) | 0xFFFFFF;
        } else if (f.shaking() && f.state == Fighter.State.HITSTUN) flash = 0x60FFFFFF;
        else if (f.state == Fighter.State.ATTACK && f.armored()) flash = 0x40FF8040;
        if (f.state == Fighter.State.RESPAWN) drawRespawnPad(c, f);
        boolean onFeet = f.grounded() || f.state == Fighter.State.LEDGE;
        int pose = f.pose(match.frame);
        // Speed trail for hard launches.
        if (f.state == Fighter.State.HITSTUN && f.tumble && Math.hypot(f.lx, f.ly) > 4 && !f.shaking()) {
            for (int i = 1; i <= 3; i++) {
                double tx = sx(f.x - f.lx * i * 2.5);
                double ty = sy(f.centerY() - f.ly * i * 2.5);
                fighters.draw(c, f.def.id, pose, tx, ty + (feet - center), ty, false, f.facing, zoom, f.costume,
                        (200 << 24) | (colorOf(f.port) & 0xFFFFFF), 0.4f / i, ticks);
            }
        }
        drawMoveProps(c, f, true);
        x += lunge(f) * zoom;
        fighters.draw(c, f.def.id, pose, x, feet, center, onFeet, f.facing, zoom, f.costume, flash, alpha, ticks);
        drawMoveProps(c, f, false);
        drawAuras(c, f, x, center);
    }

    /**
     * The fighters with few animation frames (Robotnik, Egg Robo, Rhinobot) lean into their
     * attacks: a few pixels toward the hit while it is live, easing back after.
     */
    private double lunge(Fighter f) {
        String id = f.def.id;
        if (!(id.equals("robotnik") || id.equals("eggrobo") || id.equals("rhinobot"))) return 0;
        if (f.state != Fighter.State.ATTACK || f.move == null || f.rolling) return 0;
        double best = 0;
        for (Hit h : f.move.hits) {
            int mf = f.moveFrame;
            double amount;
            if (mf < h.start() - 6 || mf > h.end() + 8) continue;
            if (mf < h.start()) amount = -2 * (1 - (h.start() - mf) / 6.0);
            else if (mf <= h.end()) amount = 5;
            else amount = 5 * (1 - (mf - h.end()) / 8.0);
            double dir = Math.signum(h.x()) == 0 ? 0 : Math.signum(h.x());
            if (Math.abs(amount * dir) > Math.abs(best)) best = amount * dir;
        }
        return best * f.facing;
    }

    private void drawRespawnPad(SceneCanvas c, Fighter f) {
        SceneImage disc = art.image("soft16");
        double y = sy(f.y + 2);
        int col = colorOf(f.port);
        c.draw(disc, (float) (sx(f.x) - 22 * zoom), (float) (y - 6 * zoom),
                SceneDraw.plain().withScale((float) (44 * zoom / disc.width()), (float) (12 * zoom / disc.height())).withTint(col));
        c.draw(disc, (float) (sx(f.x) - 14 * zoom), (float) (y - 4 * zoom),
                SceneDraw.plain().withScale((float) (28 * zoom / disc.width()), (float) (7 * zoom / disc.height())).withAlpha(0.8f));
    }

    private void drawAuras(SceneCanvas c, Fighter f, double x, double center) {
        if (f.shielding()) {
            double r = f.shieldRadius() * zoom;
            SceneImage b = art.image("bubble24");
            int col = colorOf(f.port);
            float hp = (float) (f.shieldHp / 50.0);
            c.draw(b, (float) (x - r), (float) (center - r), SceneDraw.plain().withScale((float) (2 * r / b.width()))
                    .withTint((col & 0xFFFFFF) | ((int) (150 + 80 * (1 - hp)) << 24)));
        }
        if (f.barrier != 0) {
            double r = 22 * zoom;
            int col = switch (f.barrier) {
                case Item.FIRE_SHIELD -> 0xC0FF7020;
                case Item.BUBBLE_SHIELD -> 0xA060D0FF;
                default -> 0xB0FFF060;
            };
            SceneImage b = art.image("bubble24");
            double pulse = 1 + Math.sin(ticks * 0.2) * 0.04;
            c.draw(b, (float) (x - r * pulse), (float) (center - r * pulse), SceneDraw.plain().withScale((float) (2 * r * pulse / b.width())).withTint(col));
            if (f.barrier == Item.LIGHTNING_SHIELD && ticks % 6 < 3) {
                SceneImage s = art.image("sparkle");
                c.draw(s, (float) (x + Math.cos(ticks * 0.5) * r - 5), (float) (center + Math.sin(ticks * 0.5) * r - 5), SceneDraw.plain().withTint(0xFFFFFF80));
            }
        }
        if (f.starTimer > 0 || f.superTimer > 0) {
            SceneImage s = art.image("sparkle");
            for (int i = 0; i < 4; i++) {
                double ang = ticks * 0.18 + i * Math.PI / 2;
                double rr = 18 * zoom;
                c.draw(s, (float) (x + Math.cos(ang) * rr - 5), (float) (center + Math.sin(ang) * rr * 0.9 - 5),
                        SceneDraw.plain().withTint(f.superTimer > 0 ? 0xFFFFE060 : 0xFFFFFFFF));
            }
        }
        if (f.shoesTimer > 0 && Math.abs(f.vx) > 1 && ticks % 4 == 0) {
            Particle p = add(P_DUST, f.x - Math.signum(f.vx) * 8, f.y - 4, 10);
            p.vy = -0.2;
        }
        if (f.state == Fighter.State.DIZZY) {
            SceneImage s = art.image("sparkle");
            for (int i = 0; i < 3; i++) {
                double ang = ticks * 0.12 + i * 2.1;
                c.draw(s, (float) (x + Math.cos(ang) * 10 * zoom - 5), (float) (sy(f.y - f.height() - 4) + Math.sin(ang) * 3 - 5),
                        SceneDraw.plain().withTint(0xFFFFFF40));
            }
        }
    }

    /** Weapons and effects attached to moves: wrecking balls, flames, insta-shield rings, slashes. */
    private void drawMoveProps(SceneCanvas c, Fighter f, boolean behind) {
        if (f.state != Fighter.State.ATTACK || f.move == null) return;
        Move m = f.move;
        String id = m.id;
        int mf = f.moveFrame;
        double cx = f.x;
        double cy = f.centerY();
        if (behind) {
            if (id.equals("robotnik.utilt") || id.equals("robotnik.uair")) {
                if (mf >= 4 && mf <= 16) flame(c, cx, cy - 18, true, 1.2);
            }
            if (id.equals("robotnik.thrusters") || id.equals("robo.jetpack")) {
                if (mf >= 4 && mf <= m.duration - 6) flame(c, cx, cy + (id.startsWith("robo") ? 18 : 12), false, 1.0);
            }
            if (id.equals("robotnik.bair") || id.equals("robo.bair") || id.equals("robotnik.dash") || id.equals("robo.dash")) {
                if (mf >= 3 && mf <= 16) flameSide(c, cx - f.facing * 18, cy + 2, -f.facing);
            }
            if (id.equals("robo.usmash") && mf >= 10 && mf <= 22) flame(c, cx, cy + 18, false, 1.3);
            return;
        }
        switch (id) {
            case "robotnik.fsmash", "robotnik.usmash", "robotnik.fair" -> wreckingBall(c, f, m, mf);
            case "robotnik.ftilt" -> {
                double reach = mf < 8 ? 6 + mf : mf <= 11 ? 30 : Math.max(6, 30 - (mf - 11) * 2);
                chain(c, cx + f.facing * 10, cy, cx + f.facing * reach, cy, 3);
                c.draw(art.prop("ball_small"), (float) sx(cx + f.facing * reach), (float) sy(cy), SceneDraw.plain().withScale((float) zoom));
            }
            case "sonic.insta", "mecha.counter" -> {
                if (mf <= 9) {
                    double r = (12 + mf * 2.2) * zoom;
                    SceneImage ring = art.image("ring24");
                    int col = id.startsWith("sonic") ? 0xFF80E0FF : 0xFFC080FF;
                    c.draw(ring, (float) (sx(cx) - r), (float) (sy(cy) - r), SceneDraw.plain().withScale((float) (2 * r / ring.width()))
                            .withTint(col).withAlpha((float) (1 - mf / 11.0)));
                }
            }
            case "robo.overload" -> {
                if (mf < 22 && ticks % 4 < 2) {
                    SceneImage s = art.image("sparkle");
                    c.draw(s, (float) (sx(cx + Math.cos(ticks) * 14) - 5), (float) (sy(cy + Math.sin(ticks * 1.3) * 18) - 5),
                            SceneDraw.plain().withTint(0xFF80E0FF));
                } else if (mf >= 22 && mf <= 30) {
                    double r = 34 * zoom * (0.6 + (mf - 22) * 0.06);
                    SceneImage ring = art.image("ring40");
                    c.draw(ring, (float) (sx(cx) - r), (float) (sy(cy) - r), SceneDraw.plain().withScale((float) (2 * r / ring.width()))
                            .withTint(0xFF80E0FF).withAlpha((float) (1 - (mf - 22) / 9.0)));
                }
            }
            case "knuckles.maxheat" -> {
                if (mf < 30 && f.holdFrames > 0 && ticks % 3 == 0) {
                    Particle p = add(P_SPARKLE, cx + (ticks * 7 % 24) - 12, cy + 10, 16);
                    p.vy = -1.4;
                    p.color = 0xFFFF8020;
                }
                if (mf >= 30 && mf <= 34) flameSide(c, cx + f.facing * 16, cy - 4, f.facing);
            }
            default -> { }
        }
        // Generic hit effects: slashes, electric arcs and explosions where hitboxes are live.
        for (Hit h : m.hits) {
            if (!h.live(mf)) continue;
            double hx = cx + h.x() * f.facing;
            double hy = cy + h.y();
            if (h.grounded() && mf == h.start() && f.hitlag == 0) {
                // Ground slams kick up dust along the floor both ways.
                for (int k = 0; k < 6; k++) {
                    Particle p = add(P_DUST, f.x + (k < 3 ? -1 : 1) * (6 + (k % 3) * 9), f.y - 2, 18 + (k % 3) * 3);
                    p.vx = (k < 3 ? -1 : 1) * (1.2 + (k % 3) * 0.6);
                    p.vy = -0.2;
                }
                shake = Math.max(shake, h.damage() >= 12 ? 2 : 1);
            }
            if (h.rehit() > 0 && ticks % 3 == 0 && h.effect() == Hit.FX_NORMAL) {
                SceneImage s = art.image("sparkle");
                c.draw(s, (float) (sx(hx + (ticks * 7 % 11) - 5) - 5), (float) (sy(hy + (ticks * 5 % 9) - 4) - 5),
                        SceneDraw.plain().withTint(0xFFFFF0A0));
            }
            if (h.effect() == Hit.FX_SLASH) {
                SceneImage st = art.image("streak");
                double w = h.r() * 2.6 * zoom;
                c.draw(st, (float) (sx(hx) - w / 2), (float) sy(hy - 2), SceneDraw.plain().withScale((float) (w / st.width()), (float) zoom * 2)
                        .withFlipX(f.facing < 0).withTint(0xE0E0F0FF));
            } else if (h.effect() == Hit.FX_EXPLOSION && h.start() == mf) {
                explosion(hx, hy, 1);
            } else if (h.effect() == Hit.FX_ELECTRIC && ticks % 3 == 0) {
                SceneImage s = art.image("sparkle_big");
                c.draw(s, (float) (sx(hx) - 9), (float) (sy(hy) - 9), SceneDraw.plain().withTint(0xFFA0F0FF));
            } else if (h.effect() == Hit.FX_FIRE && (id.startsWith("robo.utilt") || id.startsWith("robo.uair"))) {
                flame(c, hx, hy + 8, true, 0.8);
            }
        }
    }

    private void wreckingBall(SceneCanvas c, Fighter f, Move m, int mf) {
        // The ball follows its hitbox while live, winds up behind beforehand and swings back after.
        Hit first = m.hits.get(0);
        Hit last = m.hits.get(m.hits.size() - 1);
        double cx = f.x;
        double cy = f.centerY();
        double bx, by;
        if (mf < first.start()) {
            double t = mf / (double) Math.max(1, first.start());
            double ang = Math.PI * (0.5 + 0.9 * t);
            bx = cx - f.facing * Math.cos(ang - Math.PI / 2) * 26 * t;
            by = cy + 18 - Math.sin(ang) * 6;
            bx = cx + f.facing * (-10 - 18 * t);
            by = cy + 16 - 22 * t;
        } else if (mf <= last.end()) {
            Hit cur = first;
            for (Hit h : m.hits) if (h.live(mf)) cur = h;
            bx = cx + cur.x() * f.facing;
            by = cy + cur.y();
        } else {
            double t = Math.min(1, (mf - last.end()) / 14.0);
            bx = cx + last.x() * f.facing * (1 - t);
            by = cy + last.y() * (1 - t) + 18 * t;
        }
        chain(c, cx, cy + 4, bx, by, 5);
        c.draw(art.prop("ball"), (float) sx(bx), (float) sy(by), SceneDraw.plain().withScale((float) zoom));
    }

    private void chain(SceneCanvas c, double x0, double y0, double x1, double y1, int links) {
        SceneImage d = art.image("disc8");
        for (int i = 1; i < links; i++) {
            double t = i / (double) links;
            double x = x0 + (x1 - x0) * t;
            double y = y0 + (y1 - y0) * t;
            double r = 2.2 * zoom;
            c.draw(d, (float) (sx(x) - r), (float) (sy(y) - r), SceneDraw.plain().withScale((float) (2 * r / d.width())).withTint(0xFF8890A0));
        }
    }

    private void flame(SceneCanvas c, double x, double y, boolean up, double scale) {
        SceneSprite fl = art.prop("bigflame" + (ticks / 2) % 2);
        c.draw(fl, (float) sx(x), (float) sy(y), SceneDraw.plain().withScale((float) (zoom * scale)).withFlipY(up));
    }

    private void flameSide(SceneCanvas c, double x, double y, int dir) {
        SceneSprite fl = art.prop("flame" + (ticks / 2) % 3);
        // A downward flame laid on its side: squash it wide and short.
        c.draw(fl, (float) sx(x), (float) sy(y - 5), SceneDraw.plain().withScale((float) (zoom * 1.6), (float) (zoom * 0.6)).withFlipX(dir < 0));
    }

    private void drawTag(SceneCanvas c, Fighter f) {
        if (!f.onStage() && f.state != Fighter.State.RESPAWN) return;
        String tag = tags[f.port];
        int col = colorOf(f.port);
        double x = sx(f.x);
        double y = sy(f.y - f.height()) - 14;
        int w = font.width(tag);
        // A small pointer under the tag.
        c.fill((int) x - 1, (int) y + 9, 3, 2, col);
        c.fill((int) x, (int) y + 11, 1, 1, col);
        font.draw(c, tag, (int) (x - w / 2.0), (int) y - 1, col);
    }

    private void drawOffscreen(SceneCanvas c) {
        for (Fighter f : match.fighters) {
            if (!f.onStage()) continue;
            double x = sx(f.x);
            double y = sy(f.centerY());
            if (x >= -4 && x <= width + 4 && y >= -4 && y <= height + 4) continue;
            double bx = Math.max(18, Math.min(width - 18, x));
            double by = Math.max(18, Math.min(height - 64, y));
            int col = colorOf(f.port);
            SceneImage disc = art.image("soft16");
            c.draw(disc, (float) (bx - 16), (float) (by - 16), SceneDraw.plain().withTint(0xC0000000 | (col & 0xFFFFFF)));
            SceneImage ring = art.image("ring12");
            c.draw(ring, (float) (bx - 13), (float) (by - 13), SceneDraw.plain().withScale(2f * 13 / ring.width() * 13f / 13f).withTint(col));
            SceneImage face = art.portrait(f.def.id, f.costume, 16);
            c.draw(face, (float) (bx - 8), (float) (by - 8));
            // Arrow toward the fighter.
            double ang = Math.atan2(y - by, x - bx);
            double ax = bx + Math.cos(ang) * 15;
            double ay = by + Math.sin(ang) * 15;
            c.fill((int) ax - 2, (int) ay - 2, 4, 4, col);
            // Distance hint: the further, the smaller the bubble flashes.
            double dist = Math.hypot(x - bx, y - by);
            if (dist > 120 && ticks % 20 < 10) c.fill((int) bx - 1, (int) by + 11, 3, 3, 0xFFFFFFFF);
        }
    }

    private void drawItems(SceneCanvas c) {
        for (Item item : match.items) {
            if (item.age > Item.LIFE - 180 && (item.age / 4) % 2 == 0) continue;
            int frame = switch (item.kind) {
                case Item.SUPER_RING -> 4;
                case Item.FIRE_SHIELD -> 6;
                case Item.LIGHTNING_SHIELD -> 7;
                case Item.BUBBLE_SHIELD -> 8;
                case Item.INVINCIBILITY -> 9;
                case Item.SPEED_SHOES -> 5;
                case Item.EGGMAN -> 3;
                default -> 10;
            };
            // The screen shows static now and then, as the originals' monitors do.
            if (ticks % 40 < 3) frame = (int) (ticks % 3);
            SceneSprite s = art.frame("monitor", frame);
            double y = sy(item.y) - (s.height() - s.originY()) * zoom;
            c.draw(s, (float) sx(item.x), (float) y, SceneDraw.plain().withScale((float) zoom));
            if (item.kind == Item.SUPER && ticks % 10 < 5) {
                SceneImage sp = art.image("sparkle");
                c.draw(sp, (float) (sx(item.x) - 5 + 10), (float) (y - 14), SceneDraw.plain().withTint(0xFFFFE060));
            }
        }
    }

    private void drawProjectiles(SceneCanvas c) {
        for (Projectile p : match.projectiles) {
            double x = sx(p.x);
            double y = sy(p.y);
            SceneDraw st = SceneDraw.plain().withScale((float) zoom);
            switch (p.def.kind()) {
                case Projectile.K_RING -> {
                    SceneSprite s = art.frame("ring", (int) ((ticks / 4) % 4));
                    boolean danger = p.def.life() - p.age < 30 && (ticks / 3) % 2 == 0;
                    c.draw(s, (float) x, (float) y, danger ? st.withFlash(0xA0FF2020) : st);
                }
                case Projectile.K_BOMB -> {
                    boolean blink = p.def.life() - p.age < 40 && (ticks / 3) % 2 == 0;
                    c.draw(art.prop("bomb"), (float) x, (float) y, blink ? st.withFlash(0xC0FF3030) : st);
                    if (ticks % 4 < 2) c.fill((int) x - 1, (int) (y - 7 * zoom - 6), 2, 2, 0xFFFFE040);
                }
                case Projectile.K_MISSILE -> {
                    c.draw(art.prop("missile"), (float) x, (float) y, st.withFlipX(p.vx < 0));
                    SceneSprite fl = art.prop("flame" + (ticks / 2) % 3);
                    c.draw(fl, (float) (x - Math.signum(p.vx) * 12 * zoom), (float) (y - 3 * zoom),
                            SceneDraw.plain().withScale((float) (zoom * 1.0), (float) (zoom * 0.45)).withFlipX(p.vx > 0));
                }
                case Projectile.K_LASER -> {
                    SceneSprite s = art.frame("eggrobo", 7);
                    c.draw(s, (float) x, (float) y, SceneDraw.plain().withScale((float) (zoom * 0.5), (float) zoom).withFlipX(p.vx > 0));
                }
                case Projectile.K_MINE -> {
                    SceneSprite s = art.frame("orbinaut", 1);
                    boolean armed = p.resting && (ticks / 8) % 2 == 0;
                    c.draw(s, (float) x, (float) y, armed ? st.withFlash(0x60FF4040) : st);
                }
                case Projectile.K_SHOCKWAVE -> {
                    SceneSprite s = art.frame("explosion", 1 + (int) ((p.age / 4) % 2));
                    c.draw(s, (float) x, (float) y, SceneDraw.plain().withScale((float) (zoom * 0.45)).withAlpha(0.85f));
                }
                case Projectile.K_FIREBALL -> {
                    SceneSprite s = art.frame("eggrobo", 2);
                    c.draw(s, (float) x, (float) y, st.withFlipX(p.vx > 0));
                    SceneSprite fl = art.prop("flame" + (ticks / 2) % 3);
                    c.draw(fl, (float) (x - Math.signum(p.vx) * 14 * zoom), (float) (y - 3 * zoom),
                            SceneDraw.plain().withScale((float) zoom, (float) (zoom * 0.45)).withFlipX(p.vx > 0));
                }
                case Projectile.K_SPRING -> c.draw(art.prop("spring"), (float) x, (float) (y + p.def.radius() * zoom), st);
                default -> {
                    SceneImage d = art.image("soft16");
                    double r = p.def.radius() * 1.6 * zoom;
                    c.draw(d, (float) (x - r), (float) (y - r), SceneDraw.plain().withScale((float) (2 * r / d.width())).withTint(0xFF80E0FF));
                }
            }
        }
    }

    private void drawParticles(SceneCanvas c, boolean overlay) {
        for (Particle p : particles) {
            boolean isOverlay = p.type == P_FLASH && p.size == 1 || p.type == P_TEXT || p.type == P_BEAM || p.type == P_STAR_KO;
            if (isOverlay != overlay) continue;
            double t = p.age / (double) p.life;
            double x = sx(p.x);
            double y = sy(p.y);
            switch (p.type) {
                case P_SPARK -> {
                    SceneImage s = art.image(p.size >= 2 ? "sparkle_big" : "sparkle");
                    double sc = (1 + p.size * 0.6) * (1.2 - t) * zoom * 1.4;
                    c.draw(s, (float) (x - s.width() * sc / 2), (float) (y - s.height() * sc / 2),
                            SceneDraw.plain().withScale((float) sc).withTint(p.color).withAlpha((float) (1 - t)));
                    if (p.age < 3) {
                        SceneImage d = art.image("soft16");
                        double r = (8 + p.size * 6) * zoom;
                        c.draw(d, (float) (x - r), (float) (y - r), SceneDraw.plain().withScale((float) (2 * r / d.width())).withAlpha(0.9f));
                    }
                }
                case P_RING -> {
                    SceneImage r = art.image("ring24");
                    double rad = (6 + t * (14 + p.size * 8)) * zoom;
                    c.draw(r, (float) (x - rad), (float) (y - rad), SceneDraw.plain().withScale((float) (2 * rad / r.width()))
                            .withTint(p.color).withAlpha((float) (1 - t)));
                }
                case P_DUST -> {
                    SceneSprite s = art.frame("explosion", 1 + Math.min(2, (int) (t * 3)));
                    c.draw(s, (float) x, (float) y, SceneDraw.plain().withScale((float) (zoom * (0.28 + t * 0.15))).withAlpha((float) (0.85 * (1 - t))));
                }
                case P_EXPLOSION -> {
                    int frame = Math.min(4, (int) (t * 5));
                    SceneSprite s = art.frame("explosion", frame);
                    c.draw(s, (float) x, (float) y, SceneDraw.plain().withScale((float) (zoom * (p.size == 2 ? 1.4 : 1.0))));
                }
                case P_SPARKLE -> {
                    SceneImage s = art.image("sparkle");
                    c.draw(s, (float) (x - 5), (float) (y - 5), SceneDraw.plain().withTint(p.color).withAlpha((float) (1 - t)));
                }
                case P_MONITOR -> {
                    SceneSprite s = art.frame("monitor", 11);
                    if (p.age < 4) c.draw(s, (float) x, (float) (y + 6 * zoom), SceneDraw.plain().withScale((float) zoom));
                    int icon = switch (p.size) {
                        case Item.SUPER_RING -> 4;
                        case Item.FIRE_SHIELD -> 6;
                        case Item.LIGHTNING_SHIELD -> 7;
                        case Item.BUBBLE_SHIELD -> 8;
                        case Item.INVINCIBILITY -> 9;
                        case Item.SPEED_SHOES -> 5;
                        case Item.EGGMAN -> 3;
                        default -> 10;
                    };
                    SceneSprite m = art.frame("monitor", icon);
                    c.clip((int) (x - 9 * zoom), (int) (y - 30 * zoom), (int) (18 * zoom), (int) (17 * zoom));
                    c.draw(m, (float) x, (float) (y - 14 * zoom), SceneDraw.plain().withScale((float) zoom).withAlpha((float) (1 - t)));
                    c.unclip();
                }
                case P_BEAM -> drawBeam(c, p, t);
                case P_STAR_KO -> {
                    // A twinkle far away at the top of the screen.
                    double px = Math.max(20, Math.min(width - 20, x));
                    double py = 20 + t * 30;
                    SceneImage s = art.image("sparkle_big");
                    double sc = (1 - t) * 2 + 0.5;
                    if ((p.age / 3) % 2 == 0) {
                        c.draw(s, (float) (px - 9 * sc), (float) (py - 9 * sc), SceneDraw.plain().withScale((float) sc).withTint(p.color));
                    }
                }
                case P_TEXT -> {
                    int w = font.width(p.text);
                    float a = (float) Math.min(1, (1 - t) * 3);
                    font.draw(c, p.text, (int) (x - w / 2.0) + 1, (int) y + 1, ((int) (a * 200) << 24));
                    font.draw(c, p.text, (int) (x - w / 2.0), (int) y, ((int) (a * 255) << 24) | (p.color & 0xFFFFFF));
                }
                case P_FLASH -> {
                    if (p.size == 1) c.fill(0, 0, width, height, ((int) (120 * (1 - t)) << 24) | 0xFFFFFF);
                    else {
                        SceneImage d = art.image("soft32");
                        double r = (16 + t * 20) * zoom;
                        c.draw(d, (float) (x - r), (float) (y - r), SceneDraw.plain().withScale((float) (2 * r / d.width())).withAlpha((float) (1 - t)));
                    }
                }
                default -> { }
            }
        }
    }

    private void drawBeam(SceneCanvas c, Particle p, double t) {
        // The classic KO blast: a widening coloured beam from the blast line back into the stage.
        double x0 = Math.max(-20, Math.min(width + 20, sx(p.x)));
        double y0 = Math.max(-20, Math.min(height + 20, sy(p.y)));
        double len = 260 * Math.min(1, t * 4);
        double fade = t < 0.25 ? 1 : 1 - (t - 0.25) / 0.75;
        SceneImage d = art.image("soft32");
        int steps = 26;
        for (int i = 0; i < steps; i++) {
            double u = i / (double) steps;
            double x = x0 + p.dx * len * u;
            double y = y0 + p.dy * len * u;
            double r = (30 * (1 - u) + 6) * (0.6 + 0.4 * fade);
            c.draw(d, (float) (x - r), (float) (y - r), SceneDraw.plain().withScale((float) (2 * r / d.width()))
                    .withTint(p.color).withAlpha((float) (0.5 * fade * (1 - u * 0.7))));
        }
        for (int i = 0; i < steps; i++) {
            double u = i / (double) steps;
            double x = x0 + p.dx * len * u;
            double y = y0 + p.dy * len * u;
            double r = (12 * (1 - u) + 2) * fade;
            c.draw(d, (float) (x - r), (float) (y - r), SceneDraw.plain().withScale((float) (2 * r / d.width())).withAlpha((float) (0.8 * fade)));
        }
    }

    // ------------------------------------------------------------------ HUD

    private void drawHud(SceneCanvas c) {
        int n = match.fighters.size();
        int panelW = 84;
        int gap = Math.max(4, (width - n * panelW) / (n + 1));
        int y = height - 42;
        for (int i = 0; i < n; i++) {
            Fighter f = match.fighters.get(i);
            int x = gap + i * (panelW + gap);
            drawPanel(c, f, x, y, panelW);
        }
        if (match.rules.mode == Rules.Mode.TIME && match.framesLeft >= 0) {
            int secs = (match.framesLeft + 59) / 60;
            String t = (secs / 60) + ":" + String.format("%02d", secs % 60);
            boolean hurry = secs <= 10 && match.countdown == 0;
            SceneImage img = font.text(t, 2, hurry ? 0xFFFF8080 : 0xFFFFFFFF, hurry ? 0xFFFF2020 : 0xFFA0C8FF);
            c.draw(img, width / 2f - img.width() / 2f, 4);
        }
        if (match.suddenDeath && match.countdown == 0) {
            SceneImage img = font.text("SUDDEN DEATH", 1, 0xFFFF8080, 0xFFFF2020);
            c.draw(img, width / 2f - img.width() / 2f, 4);
        }
    }

    private void drawPanel(SceneCanvas c, Fighter f, int x, int y, int w) {
        int col = colorOf(f.port);
        boolean out = f.state == Fighter.State.OUT;
        c.fill(x, y, w, 38, out ? 0x90000000 : 0xB0101828);
        c.fill(x, y, w, 2, col);
        c.fill(x, y + 36, w, 2, (col & 0xFFFFFF) | 0x80000000);
        SceneImage face = art.portrait(f.def.id, f.costume, 24);
        c.draw(face, x + 3, y + 5, out ? SceneDraw.plain().withAlpha(0.35f) : SceneDraw.plain());
        String name = f.def.name.length() > 9 ? f.def.name.substring(0, f.def.name.indexOf(' ') > 0 ? f.def.name.indexOf(' ') : 9) : f.def.name;
        font.draw(c, name, x + 29, y + 3, out ? 0xFF606060 : 0xFFE8E8F0);
        double shakeX = hudShake[f.port] > 0 ? Math.sin(ticks * 3.1) * hudShake[f.port] : 0;
        double shakeY = hudShake[f.port] > 0 ? Math.cos(ticks * 2.3) * hudShake[f.port] * 0.6 : 0;
        if (out) {
            SceneImage img = font.text("OUT", 2, 0xFF909090, 0xFF505050);
            c.draw(img, x + 30, y + 14);
        } else if (match.rules.mode == Rules.Mode.STAMINA) {
            int hp = (int) Math.ceil(Math.max(0, f.stamina));
            double frac = Math.max(0, f.stamina / match.rules.stamina);
            int top = frac > 0.5 ? 0xFFA0FFA0 : frac > 0.25 ? 0xFFFFF080 : 0xFFFF8080;
            SceneImage img = font.text(hp + "HP", 2, 0xFFFFFFFF, top);
            c.draw(img, (float) (x + 29 + shakeX), (float) (y + 13 + shakeY));
        } else {
            int dmg = (int) Math.floor(f.damage);
            double heat = Math.min(1, f.damage / 160.0);
            int bottom = BigFont.lerp(0xFFFFFFFF, 0xFFFF1010, heat);
            if (f.damage > 160) bottom = BigFont.lerp(0xFFFF1010, 0xFF800000, Math.min(1, (f.damage - 160) / 120));
            int top = BigFont.lerp(0xFFFFFFFF, 0xFFFFB0B0, heat);
            if (f.state == Fighter.State.DEAD) { top = 0xFF808080; bottom = 0xFF404040; }
            SceneImage img = font.text(dmg + "%", 2, top, bottom);
            c.draw(img, (float) (x + 29 + shakeX), (float) (y + 12 + shakeY));
        }
        if (match.rules.mode == Rules.Mode.STOCK && !out) {
            int s = Math.max(0, f.stocks);
            if (s <= 5) {
                for (int i = 0; i < s; i++) c.fill(x + 4 + i * 6, y + 31, 4, 4, col);
            } else {
                c.fill(x + 4, y + 31, 4, 4, col);
                font.draw(c, "x" + s, x + 10, y + 28, 0xFFFFFFFF);
            }
        } else if (match.rules.mode == Rules.Mode.TIME) {
            String sc = (f.score >= 0 ? "+" : "") + f.score;
            font.draw(c, sc, x + 4, y + 28, f.score >= 0 ? 0xFFA0FFA0 : 0xFFFF8080);
        }
        if (f.combo >= 3 && f.comboTimer > 0 && f.state == Fighter.State.HITSTUN) {
            font.draw(c, f.combo + " HIT", x + w - 40, y - 11, 0xFFFFE060);
        }
    }

    private void drawBanner(SceneCanvas c) {
        int cd = match.countdown;
        if (cd > 0) {
            String t = cd > 110 ? "3" : cd > 60 ? "2" : cd > 10 ? "1" : "GO!";
            int phase = cd > 110 ? cd - 110 : cd > 60 ? cd - 60 : cd > 10 ? cd - 10 : cd;
            int scale = 8 + (phase > 40 ? (phase - 40) / 2 : 0);
            SceneImage img = font.text(t, Math.min(14, scale), 0xFFFFF080, 0xFFF08010);
            c.draw(img, width / 2f - img.width() / 2f, height / 2f - img.height() / 2f - 20);
            if (match.suddenDeath && cd > 10) {
                SceneImage sd = font.text("SUDDEN DEATH", 2, 0xFFFF8080, 0xFFFF2020);
                c.draw(sd, width / 2f - sd.width() / 2f, 30);
            }
        } else if (goTimer >= 0 && goTimer < 36) {
            SceneImage img = font.text("GO!", 6, 0xFFFFF080, 0xFFF08010);
            float a = (float) Math.max(0, 1 - goTimer / 36.0);
            c.draw(img, width / 2f - img.width() / 2f, height / 2f - img.height() / 2f - 20, SceneDraw.plain().withAlpha(a));
        }
        if (match.over) {
            int t = match.endTimer;
            int scale = Math.max(6, 12 - t / 2);
            SceneImage img = font.text("GAME!", scale, 0xFFFFFFFF, 0xFFF0A020);
            c.draw(img, width / 2f - img.width() / 2f, height / 2f - img.height() / 2f - 24);
        }
    }

    /** Draws a stage preview: the backdrop and island scaled into a box (menus). */
    public static void preview(SceneCanvas c, StageBuilder.Built s, int x, int y, int w, int h, long ticks) {
        var bd = s.backdrop();
        int imgH = bd.image().height();
        int top = Math.max(0, Math.min(Math.max(0, imgH - h), s.stage().bgTop() + 40));
        c.drawBackdrop(bd, x, y, w, Math.min(h, imgH), top, ticks * 0.3, ticks);
        double scale = Math.min(w * 0.8 / s.island().width(), h * 0.55 / s.island().height());
        double ix = x + w / 2.0 - s.island().width() * scale / 2;
        double iy = y + h * 0.42;
        c.draw(s.island(), (float) ix, (float) iy, SceneDraw.plain().withScale((float) scale));
        Arena a = s.arena();
        for (int i = 0; i < a.platforms.size(); i++) {
            Arena.Platform p = a.platforms.get(i);
            StageBuilder.PlatformArt pa = s.platforms().get(i);
            double px = x + w / 2.0 + p.x0() * scale;
            double py = iy + (p.y() - s.islandY() + pa.offsetY()) * scale;
            c.draw(pa.image(), (float) px, (float) py, SceneDraw.plain().withScale((float) scale));
        }
    }
}
