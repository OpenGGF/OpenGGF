package eggsky.space;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneLevelKit;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import eggsky.Game;
import eggsky.Mode;
import eggsky.art.Art;
import eggsky.art.FaunaDef;
import eggsky.core.Colour;
import eggsky.core.Controls;
import eggsky.core.Rng;
import eggsky.core.Sound;
import eggsky.game.Catalog;
import eggsky.game.Player;
import eggsky.ui.Font;
import eggsky.ui.MenuMode;
import eggsky.ui.Ui;
import eggsky.world.Planet;
import eggsky.world.PlanetSpec;
import eggsky.world.StarSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Flying the Egg Mobile through a star system in a first-person cockpit: steer between
 * ray-traced planets, pulse across the system, mine asteroids, fight pirates and the Tornado,
 * dive into an atmosphere to land and fly into the Egg Station to dock.
 */
public final class SpaceMode implements Mode {
    public static final int ARRIVE_LAUNCH = 0;
    public static final int ARRIVE_UNDOCK = 1;
    public static final int ARRIVE_WARP = 2;
    public static final int ARRIVE_RESUME = 3;

    private static final double CRUISE = 9;
    private static final double BOOST = 28;
    private static final double PULSE = 150;
    private static final int PULSE_HOLD = 36;

    private final int arrive;
    private final int fromPlanet;
    private Game game;
    private boolean initialised;
    private final Cam cam = new Cam();
    private SpaceRenderer renderer;
    private final List<Body> bodies = new ArrayList<>();
    private final List<Body> sorted = new ArrayList<>();
    private Body station;
    private StarSystem system;
    private double speed = CRUISE;
    private double yawRate;
    private double pitchRate;
    private int boostHeld;
    private boolean pulsing;
    private int textureQueue;
    private int textureDelay = 2;
    private Rng rng;
    // Asteroids: positions, sizes, health.
    private final List<double[]> rocks = new ArrayList<>();
    // Enemies: pirates and the Tornado.
    private final List<Ship3D> enemies = new ArrayList<>();
    private final List<double[]> bolts = new ArrayList<>();
    private final List<double[]> loot = new ArrayList<>();
    private final List<double[]> sparks = new ArrayList<>();
    private int fireCooldown;
    private int encounterTimer;
    private int arrivalTicks;
    private int transition;
    private int transitionKind;
    private int transitionTarget;
    private int hitFlash;
    private int scanTicks;
    private int shieldDelay;
    private int tornadoChase;
    private boolean combatMusic;
    private int dockPrompt;
    private final HashMap<String, SceneSpriteSet> enemySets = new HashMap<>();

    /** A ship in space: pirate fighter or the Tornado. */
    private static final class Ship3D {
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        double hp;
        double maxHp;
        int cooldown;
        int hit;
        boolean tornado;
        int sprite;
        boolean alive = true;
        int orbitDir;
    }

    public SpaceMode(int arrive, int fromPlanet) {
        this.arrive = arrive;
        this.fromPlanet = fromPlanet;
    }

    @Override
    public void enter(Game g) {
        this.game = g;
        if (initialised) {
            g.sound.music(combatMusic ? Sound.M_DDZ : Sound.M_DATA_SELECT);
            return;
        }
        initialised = true;
        system = g.system();
        rng = new Rng(Rng.hash(system.seed, g.ticks));
        cam.width = g.width;
        cam.height = g.height;
        renderer = new SpaceRenderer(system.seed, system.colour());
        renderer.sunSize = system.starClass == StarSystem.BLUE ? 2600 : system.starClass == StarSystem.RED ? 1300 : 1800;
        buildSystem(g);
        Player p = g.player;
        switch (arrive) {
            case ARRIVE_LAUNCH -> {
                Body from = fromPlanet >= 0 && fromPlanet < planets() ? bodies.get(fromPlanet) : bodies.get(0);
                // Above the day side, looking along the horizon with the sun behind.
                double sx = -from.x;
                double sz = -from.z;
                double sl = Math.max(1, Math.hypot(sx, sz));
                double ux = sx / sl * 0.75;
                double uy = 0.62;
                double uz = sz / sl * 0.75;
                double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
                ux /= ul;
                uy /= ul;
                uz /= ul;
                cam.x = from.x + ux * from.radius * 2.1;
                cam.y = from.y + uy * from.radius * 2.1;
                cam.z = from.z + uz * from.radius * 2.1;
                // Tangent: perpendicular to "up" in the horizontal plane, then tipped toward the planet.
                double tx = -uz;
                double tz = ux;
                double tl = Math.max(1e-6, Math.hypot(tx, tz));
                tx /= tl;
                tz /= tl;
                double fx = tx * 0.75 - ux * 0.6;
                double fy = -uy * 0.6;
                double fz = tz * 0.75 - uz * 0.6;
                double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
                cam.yaw = Math.atan2(fx / fl, fz / fl);
                cam.pitch = Math.asin(fy / fl);
                speed = CRUISE;
                arrivalTicks = 90;
                g.toast("LEFT " + (from.spec == null ? "ORBIT" : g.displayName(from.spec).toUpperCase()), 0xFF80C0FF);
                if (p.tutorial == 5) {
                    g.toast("FLY TO THE EGG STATION TO DOCK", 0xFFFFE060);
                }
            }
            case ARRIVE_UNDOCK -> {
                cam.x = station.x + station.radius * 1.9;
                cam.y = station.y + 200;
                cam.z = station.z;
                cam.yaw = Math.PI / 2;
                cam.pitch = 0;
                speed = BOOST;
                arrivalTicks = 40;
            }
            case ARRIVE_WARP -> {
                double ang = rng.range(0f, 6.28f);
                double r = 70000;
                cam.x = Math.cos(ang) * r;
                cam.z = Math.sin(ang) * r;
                cam.y = 3000;
                cam.yaw = Math.atan2(-cam.x, -cam.z);
                cam.pitch = -0.03;
                speed = PULSE;
                arrivalTicks = 90;
            }
            default -> {
                cam.x = p.spaceX;
                cam.y = p.spaceY;
                cam.z = p.spaceZ;
                cam.yaw = p.spaceYaw;
                cam.pitch = p.spacePitch;
            }
        }
        cam.update();
        buildAsteroids();
        encounterTimer = 60 * rng.range(20, 50);
        if (p.statWarps == 0 && arrive == ARRIVE_WARP) {
            encounterTimer = 60 * 60;
        }
        g.sound.music(Sound.M_DATA_SELECT);
        // Build the planet we launched from immediately (its kit is already loaded).
        textureQueue = 0;
        if (arrive == ARRIVE_LAUNCH && fromPlanet >= 0 && fromPlanet < planets()) {
            buildTexture(g, bodies.get(fromPlanet));
        }
        // The heroes give chase into orbit.
        if (arrive == ARRIVE_LAUNCH && g.lastWanted >= 2) {
            tornadoChase = 120;
        }
        g.lastWanted = 0;
    }

    private int planets() {
        return system.planetCount;
    }

    private void buildSystem(Game g) {
        Rng r = new Rng(Rng.hash(system.seed, 0x4F524249L));
        List<PlanetSpec> specs = system.planets(g.usableBiomes);
        for (int i = 0; i < specs.size(); i++) {
            double dist = 22000 + i * 15000 + r.range(-3000f, 3000f);
            double ang = r.range(0f, 6.28f);
            double radius = r.range(1500f, 2600f);
            Body b = new Body(i, Math.cos(ang) * dist, r.range(-1500f, 1500f), Math.sin(ang) * dist, radius,
                    r.range(-0.5f, 0.5f), r.range(0.0002f, 0.0008f), specs.get(i), false);
            b.spin = r.range(0f, 6.28f);
            bodies.add(b);
        }
        if (bodies.isEmpty()) {
            // A black hole or the core: one barren rock so there is somewhere to be.
            bodies.add(new Body(0, 20000, 0, 0, 1800, 0.2, 0.0005, null, false));
        }
        Body home = bodies.get(0);
        station = new Body(-1, home.x + 5200, home.y + 900, home.z + 2600, 650, 0.15, 0.0015, null, true);
        station.texture = PlanetTexture.station(system.seed);
        bodies.add(station);
        sorted.addAll(bodies);
    }

    private void buildAsteroids() {
        Rng r = new Rng(Rng.hash(system.seed, 0x524F434BL));
        int fields = 3 + r.nextInt(3);
        for (int f = 0; f < fields; f++) {
            Body near = bodies.get(r.nextInt(bodies.size()));
            double fx = near.x + r.range(-12000f, 12000f);
            double fy = near.y + r.range(-3000f, 3000f);
            double fz = near.z + r.range(-12000f, 12000f);
            int count = r.range(30, 60);
            for (int i = 0; i < count; i++) {
                double size = r.range(40f, 160f);
                rocks.add(new double[] {fx + r.range(-4000f, 4000f), fy + r.range(-1200f, 1200f),
                        fz + r.range(-4000f, 4000f), size, size * 0.8, r.nextInt(7)});
            }
        }
    }

    /** Builds one planet's orbital texture from its act's blocks (a few hundred milliseconds). */
    private void buildTexture(Game g, Body b) {
        if (b.texture != null || b.spec == null) {
            if (b.texture == null) {
                b.texture = PlanetTexture.build(fallbackSpec(), null);
            }
            return;
        }
        PlanetTexture cached = g.textureCache.get(system.id + ":" + b.index);
        if (cached != null) {
            b.texture = cached;
            return;
        }
        SceneLevelKit kit = null;
        try {
            kit = g.art.rom(b.spec.biome.game()).levelKit(b.spec.biome.zone(), b.spec.biome.act());
        } catch (RuntimeException failed) {
            kit = null;
        }
        b.texture = PlanetTexture.build(b.spec, kit);
        g.textureCache.put(system.id + ":" + b.index, b.texture);
    }

    private PlanetSpec fallbackSpec() {
        return new PlanetSpec(system.seed, 0, StarSystem.RED, game.usableBiomes);
    }

    @Override
    public void exit(Game g) {
        Player p = g.player;
        p.spaceX = (float) cam.x;
        p.spaceY = (float) cam.y;
        p.spaceZ = (float) cam.z;
        p.spaceYaw = (float) cam.yaw;
        p.spacePitch = (float) cam.pitch;
    }

    // ---------------------------------------------------------------- update

    @Override
    public void update(Game g) {
        this.game = g;
        Controls in = g.in;
        Player p = g.player;
        // Textures: one planet at a time, a few frames apart, so the frame rate never stalls long.
        if (textureDelay-- <= 0 && textureQueue < bodies.size()) {
            Body b = nearestUntextured();
            if (b != null) {
                buildTexture(g, b);
            }
            textureQueue++;
            textureDelay = 6;
        }
        for (Body b : bodies) {
            b.spin += b.spinSpeed;
        }
        if (transition > 0) {
            updateTransition(g);
            render(g);
            return;
        }
        if (in.menuPressed) {
            g.setMode(new MenuMode(this));
            return;
        }
        if (arrivalTicks > 0) {
            arrivalTicks--;
        }
        // Steering: arrows (or the mouse, steering toward the pointer).
        double turn = 0.0016;
        double tx = in.dx();
        double ty = -in.dy();
        if (in.mouseAim || (g.ctx.mouse().inside() && g.ctx.mouse().lastInputWasMouse() && !in.left && !in.right
                && !in.up && !in.down)) {
            double mx = (in.mouseX - g.width / 2.0) / (g.width / 2.0);
            double my = -(in.mouseY - g.height / 2.0) / (g.height / 2.0);
            if (Math.abs(mx) > 0.08) {
                tx = Math.max(-1, Math.min(1, mx * 1.4));
            }
            if (Math.abs(my) > 0.08) {
                ty = Math.max(-1, Math.min(1, my * 1.4));
            }
        }
        double agility = pulsing ? 0.35 : 1;
        yawRate += (tx * 0.028 * agility - yawRate) * 0.12;
        pitchRate += (ty * 0.022 * agility - pitchRate) * 0.12;
        cam.yaw += yawRate;
        cam.pitch = Math.max(-1.35, Math.min(1.35, cam.pitch + pitchRate));
        cam.roll += (-yawRate * 9 - cam.roll) * 0.1;
        // Throttle: boost, and the pulse drive after holding boost.
        Body nearest = nearestBody();
        double nearDist = nearest == null ? Double.MAX_VALUE : nearest.distance(cam.x, cam.y, cam.z) - nearest.radius;
        boolean hostiles = !enemies.isEmpty();
        if (in.boost) {
            boostHeld++;
        } else {
            boostHeld = 0;
        }
        boolean wantPulse = boostHeld > PULSE_HOLD && p.pulse > 1;
        if (wantPulse && (nearDist < 900 || hostiles)) {
            if (boostHeld == PULSE_HOLD + 1) {
                g.toast(hostiles ? "PULSE JAMMED: HOSTILES NEAR" : "TOO CLOSE TO PULSE", 0xFFFF8060);
                g.sound.sfx(Sound.ERROR, 30);
            }
            wantPulse = false;
        }
        if (wantPulse && !pulsing) {
            g.sound.sfx(Sound.LAUNCH_GO, 30);
        }
        pulsing = wantPulse;
        double pulseSpeed = PULSE * (1 + 0.25 * p.level(Catalog.T_PULSE));
        double target = pulsing ? pulseSpeed : in.boost ? BOOST * (1 + 0.1 * p.level(Catalog.T_PULSE)) : CRUISE;
        if (arrivalTicks > 0 && arrive == ARRIVE_WARP) {
            target = pulseSpeed * 0.6;
        }
        // Slow down near bodies so landing and docking are easy to aim.
        if (!pulsing && nearDist < 3000) {
            target = Math.min(target, Math.max(4, nearDist / 120));
        }
        speed += (target - speed) * (pulsing ? 0.04 : 0.08);
        if (pulsing) {
            p.pulse = Math.max(0, p.pulse - 0.1f);
        } else {
            p.pulse = Math.min(100, p.pulse + 0.06f);
        }
        cam.update();
        cam.x += cam.forward[0] * speed;
        cam.y += cam.forward[1] * speed;
        cam.z += cam.forward[2] * speed;
        // Bodies are solid: push out, and check landing and docking.
        for (Body b : bodies) {
            double d = b.distance(cam.x, cam.y, cam.z);
            if (b.station) {
                if (d < b.radius * 1.7) {
                    dockPrompt = 2;
                    if (d < b.radius * 1.35) {
                        startTransition(g, 2, -1);
                    }
                }
            } else if (b.spec != null && d < b.radius * 1.12 && arrivalTicks <= 0) {
                startTransition(g, 1, b.index);
            }
            if (d < b.radius * 1.02) {
                double k = b.radius * 1.02 / d;
                cam.x = b.x + (cam.x - b.x) * k;
                cam.y = b.y + (cam.y - b.y) * k;
                cam.z = b.z + (cam.z - b.z) * k;
            }
        }
        if (dockPrompt > 0) {
            dockPrompt--;
        }
        updateWeapons(g, in);
        updateEnemies(g);
        updateLoot(g);
        updateEncounters(g);
        if (in.scanPressed) {
            scanTicks = 60 * 12;
            g.sound.sfx(Sound.ENERGY_ZAP, 10);
        }
        if (scanTicks > 0) {
            scanTicks--;
        }
        if (hitFlash > 0) {
            hitFlash--;
        }
        if (shieldDelay > 0) {
            shieldDelay--;
        } else {
            p.shipShield = Math.min(p.maxShipShield(), p.shipShield + 0.08f);
        }
        render(g);
    }

    private void render(Game g) {
        sorted.sort((a, b) -> Double.compare(b.distance(cam.x, cam.y, cam.z), a.distance(cam.x, cam.y, cam.z)));
        renderer.render(cam, sorted, g.ticks);
    }

    private Body nearestUntextured() {
        Body best = null;
        double bestD = Double.MAX_VALUE;
        for (Body b : bodies) {
            if (b.texture == null) {
                double d = b.distance(cam.x, cam.y, cam.z);
                if (d < bestD) {
                    bestD = d;
                    best = b;
                }
            }
        }
        return best;
    }

    private Body nearestBody() {
        Body best = null;
        double bestD = Double.MAX_VALUE;
        for (Body b : bodies) {
            double d = b.distance(cam.x, cam.y, cam.z) - b.radius;
            if (d < bestD) {
                bestD = d;
                best = b;
            }
        }
        return best;
    }

    private void startTransition(Game g, int kind, int target) {
        if (transition > 0) {
            return;
        }
        transition = 1;
        transitionKind = kind;
        transitionTarget = target;
        g.sound.sfx(kind == 1 ? Sound.FIRE_SHIELD : Sound.DOOR_OPEN, 10);
    }

    private void updateTransition(Game g) {
        transition++;
        if (transitionKind == 1) {
            // Diving into the atmosphere.
            Body b = bodies.get(transitionTarget);
            double dx = b.x - cam.x;
            double dy = b.y - cam.y;
            double dz = b.z - cam.z;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            cam.x += dx / len * 30;
            cam.y += dy / len * 30;
            cam.z += dz / len * 30;
            g.shake = 3;
            if (transition > 45) {
                transition = 0;
                g.land(transitionTarget, true);
            }
        } else {
            double dx = station.x - cam.x;
            double dy = station.y - cam.y;
            double dz = station.z - cam.z;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            cam.yaw += (Math.atan2(dx, dz) - cam.yaw) * 0.1;
            cam.update();
            cam.x += dx / len * 14;
            cam.y += dy / len * 14;
            cam.z += dz / len * 14;
            if (transition > 50) {
                transition = 0;
                g.player.spaceX = (float) cam.x;
                if (g.player.tutorial == 5) {
                    g.player.tutorial = 6;
                }
                g.dock();
            }
        }
    }

    // ---------------------------------------------------------------- weapons

    private void updateWeapons(Game g, Controls in) {
        Player p = g.player;
        if (fireCooldown > 0) {
            fireCooldown--;
        }
        if (in.beam && fireCooldown == 0 && !pulsing) {
            fireCooldown = 7;
            double side = (g.ticks / 7) % 2 == 0 ? 1 : -1;
            double ox = cam.right[0] * side * 14 - cam.up[0] * 10;
            double oy = cam.right[1] * side * 14 - cam.up[1] * 10;
            double oz = cam.right[2] * side * 14 - cam.up[2] * 10;
            double sp = 70 + speed;
            bolts.add(new double[] {cam.x + ox, cam.y + oy, cam.z + oz, cam.forward[0] * sp, cam.forward[1] * sp,
                    cam.forward[2] * sp, 60, 0, 8 * (1 + 0.4 * p.level(Catalog.T_CANNON))});
            g.sound.sfx(Sound.LASER, 6);
        }
        for (int i = bolts.size() - 1; i >= 0; i--) {
            double[] b = bolts.get(i);
            b[0] += b[3];
            b[1] += b[4];
            b[2] += b[5];
            b[6]--;
            boolean hostile = b[7] > 0;
            boolean remove = b[6] <= 0;
            if (hostile) {
                double d = Math.sqrt(sq(b[0] - cam.x) + sq(b[1] - cam.y) + sq(b[2] - cam.z));
                if (d < 60) {
                    hurtShip(g, b[8]);
                    remove = true;
                }
            } else {
                for (double[] r : rocks) {
                    if (r[4] > 0 && sq(b[0] - r[0]) + sq(b[1] - r[1]) + sq(b[2] - r[2]) < sq(r[3] * 1.1)) {
                        r[4] -= b[8];
                        spark(r[0], r[1], r[2], 0xFFFFC080);
                        if (r[4] <= 0) {
                            breakRock(g, r);
                        }
                        remove = true;
                        break;
                    }
                }
                for (Ship3D e : enemies) {
                    if (e.alive && sq(b[0] - e.x) + sq(b[1] - e.y) + sq(b[2] - e.z) < sq(e.tornado ? 140 : 110)) {
                        e.hp -= b[8];
                        e.hit = 6;
                        spark(e.x, e.y, e.z, 0xFFFFFFFF);
                        g.sound.sfx(Sound.BOSS_HIT, 6);
                        if (e.hp <= 0) {
                            destroyEnemy(g, e);
                        }
                        remove = true;
                        break;
                    }
                }
            }
            if (remove) {
                bolts.remove(i);
            }
        }
        rocks.removeIf(r -> r[4] <= 0);
        for (int i = sparks.size() - 1; i >= 0; i--) {
            double[] s = sparks.get(i);
            s[0] += s[3];
            s[1] += s[4];
            s[2] += s[5];
            s[6]--;
            if (s[6] <= 0) {
                sparks.remove(i);
            }
        }
    }

    private static double sq(double v) {
        return v * v;
    }

    private void spark(double x, double y, double z, int colour) {
        for (int i = 0; i < 8; i++) {
            sparks.add(new double[] {x, y, z, rng.range(-6f, 6f), rng.range(-6f, 6f), rng.range(-6f, 6f),
                    rng.range(10, 24), colour});
        }
    }

    private void breakRock(Game g, double[] r) {
        g.sound.sfx(Sound.BREAK, 4);
        for (int i = 0; i < 16; i++) {
            sparks.add(new double[] {r[0], r[1], r[2], rng.range(-10f, 10f), rng.range(-10f, 10f), rng.range(-10f, 10f),
                    rng.range(20, 40), 0xFFA09080});
        }
        int roll = rng.nextInt(100);
        int item = roll < 50 ? Catalog.FERRITE : roll < 70 ? Catalog.SILVER : roll < 85 ? Catalog.GOLD
                : roll < 95 ? Catalog.COBALT : Catalog.DIHYDROGEN;
        int count = (int) (r[3] / 8) + rng.nextInt(10);
        loot.add(new double[] {r[0], r[1], r[2], item, count, 0});
        g.player.statMined++;
    }

    private void hurtShip(Game g, double damage) {
        Player p = g.player;
        shieldDelay = 200;
        hitFlash = 10;
        g.shake = 5;
        if (p.shipShield > 0) {
            float absorb = (float) Math.min(p.shipShield, damage);
            p.shipShield -= absorb;
            damage -= absorb;
            g.sound.sfx(Sound.SHIELD, 6);
        }
        if (damage > 0) {
            p.hull -= (float) damage;
            g.sound.sfx(Sound.BOSS_HIT, 6);
        }
        if (p.hull <= 0) {
            // Rescued by the station: half of every stack is lost to space.
            p.hull = p.maxHull() * 0.5f;
            p.shipShield = p.maxShipShield();
            for (int i = 0; i < p.cargo.slots(); i++) {
                p.cargo.set(i, p.cargo.itemAt(i), p.cargo.countAt(i) / 2);
            }
            p.statDeaths++;
            g.banner("EGG MOBILE DESTROYED", "Towed to the station. Half your cargo was lost.", 0xFFFF6060);
            g.sound.sfx(Sound.EXPLODE);
            enemies.clear();
            g.dock();
        }
    }

    // ---------------------------------------------------------------- enemies

    private void updateEncounters(Game g) {
        if (tornadoChase > 0) {
            tornadoChase--;
            if (tornadoChase == 0) {
                spawnEnemy(g, true);
                g.banner("THE TORNADO!", "Sonic and Tails are on your tail", 0xFF4080FF);
                g.sound.music(Sound.M_BOSS);
                combatMusic = true;
            }
        }
        if (!enemies.isEmpty() || system.conflict == 0 && rng.chance(0.5)) {
            return;
        }
        encounterTimer--;
        if (encounterTimer <= 0) {
            encounterTimer = 60 * rng.range(60, 150) / (1 + system.conflict);
            if (rng.chance(0.25 + 0.25 * system.conflict)) {
                int n = 2 + system.conflict + rng.nextInt(2);
                for (int i = 0; i < n; i++) {
                    spawnEnemy(g, false);
                }
                g.banner("PIRATES INBOUND!", n + " badnik raiders", 0xFFFF5050);
                g.sound.sfx(Sound.SIREN, 60);
                g.sound.music(Sound.M_DDZ);
                combatMusic = true;
            }
        }
    }

    /** Debug: a pirate squad (or the Tornado) right now. */
    public void debugPirates(Game g, boolean tornado) {
        if (tornado) {
            spawnEnemy(g, true);
        } else {
            for (int i = 0; i < 3; i++) {
                spawnEnemy(g, false);
            }
        }
        combatMusic = true;
        g.sound.music(Sound.M_DDZ);
    }

    private void spawnEnemy(Game g, boolean tornado) {
        Ship3D e = new Ship3D();
        double ang = rng.range(0f, 6.28f);
        double d = tornado ? 2500 : 5000 + rng.nextInt(2000);
        e.x = cam.x - cam.forward[0] * d * 0.5 + Math.cos(ang) * d;
        e.y = cam.y + rng.range(-800f, 800f);
        e.z = cam.z - cam.forward[2] * d * 0.5 + Math.sin(ang) * d;
        e.tornado = tornado;
        e.maxHp = tornado ? 220 : 40 + system.conflict * 15;
        e.hp = e.maxHp;
        e.sprite = rng.nextInt(3);
        e.orbitDir = rng.chance(0.5) ? 1 : -1;
        e.cooldown = 120;
        enemies.add(e);
    }

    private void updateEnemies(Game g) {
        for (Ship3D e : enemies) {
            if (!e.alive) {
                continue;
            }
            double dx = cam.x - e.x;
            double dy = cam.y - e.y;
            double dz = cam.z - e.z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double sp = e.tornado ? 26 : 20;
            double tx;
            double ty;
            double tz;
            if (d > 1400) {
                tx = dx / d;
                ty = dy / d;
                tz = dz / d;
            } else {
                // Circle and strafe.
                tx = dz / d * e.orbitDir + dx / d * 0.25;
                ty = dy / d * 0.3;
                tz = -dx / d * e.orbitDir + dz / d * 0.25;
            }
            e.vx += (tx * (sp + speed * 0.8) - e.vx) * 0.04;
            e.vy += (ty * (sp + speed * 0.8) - e.vy) * 0.04;
            e.vz += (tz * (sp + speed * 0.8) - e.vz) * 0.04;
            e.x += e.vx;
            e.y += e.vy;
            e.z += e.vz;
            if (e.hit > 0) {
                e.hit--;
            }
            e.cooldown--;
            if (e.cooldown <= 0 && d < 3500) {
                e.cooldown = e.tornado ? 28 : rng.range(50, 90);
                double lead = d / 80;
                double ax = cam.x + cam.forward[0] * speed * lead - e.x;
                double ay = cam.y + cam.forward[1] * speed * lead - e.y;
                double az = cam.z + cam.forward[2] * speed * lead - e.z;
                double al = Math.sqrt(ax * ax + ay * ay + az * az);
                double bs = 80;
                bolts.add(new double[] {e.x, e.y, e.z, ax / al * bs, ay / al * bs, az / al * bs, 70, 1,
                        e.tornado ? 9 : 5 + system.conflict * 2});
                g.sound.sfx(Sound.BOSS_PROJECTILE, 10);
            }
        }
        enemies.removeIf(e -> !e.alive);
        if (enemies.isEmpty() && combatMusic) {
            combatMusic = false;
            g.sound.music(Sound.M_DATA_SELECT);
        }
    }

    private void destroyEnemy(Game g, Ship3D e) {
        e.alive = false;
        g.sound.sfx(Sound.MISSILE_EXPLODE);
        for (int i = 0; i < 30; i++) {
            sparks.add(new double[] {e.x, e.y, e.z, rng.range(-14f, 14f), rng.range(-14f, 14f), rng.range(-14f, 14f),
                    rng.range(20, 50), i % 2 == 0 ? 0xFFFFA020 : 0xFFFFFF80});
        }
        Player p = g.player;
        if (e.tornado) {
            p.statSonicRepelled++;
            loot.add(new double[] {e.x, e.y, e.z, -1, 800, 0});
            loot.add(new double[] {e.x + 40, e.y, e.z, -2, 60, 0});
            g.banner("TORNADO DOWNED!", "Sonic and Tails bail out. +800 rings", 0xFF80FF80);
        } else {
            p.statPirates++;
            loot.add(new double[] {e.x, e.y, e.z, Catalog.BADNIK_SCRAP, 10 + rng.nextInt(15), 0});
            loot.add(new double[] {e.x, e.y + 30, e.z, -1, 150 + rng.nextInt(200), 0});
            if (rng.chance(0.3)) {
                loot.add(new double[] {e.x, e.y - 30, e.z, -2, 15 + rng.nextInt(20), 0});
            }
        }
    }

    private void updateLoot(Game g) {
        Player p = g.player;
        for (double[] l : loot) {
            double dx = cam.x - l[0];
            double dy = cam.y - l[1];
            double dz = cam.z - l[2];
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            l[5]++;
            if (d < 900 && l[5] > 20) {
                double sp = Math.min(60, 10 + l[5]);
                l[0] += dx / d * sp;
                l[1] += dy / d * sp;
                l[2] += dz / d * sp;
            }
            if (d < 80) {
                int item = (int) l[3];
                int count = (int) l[4];
                if (item == -1) {
                    p.rings += count;
                    p.statRingsEarned += count;
                    g.toast("+" + count + " RINGS", Ui.GOLD);
                    g.sound.sfx(Sound.RING, 3);
                } else if (item == -2) {
                    p.shards += count;
                    g.toast("+" + count + " CHAOS SHARDS", 0xFF80FFE0);
                    g.sound.sfx(Sound.BLUE_SPHERE, 3);
                } else {
                    int left = p.cargo.add(item, count);
                    if (left < count) {
                        g.toast("+" + (count - left) + " " + g.catalog.name(item), g.catalog.item(item).colour());
                        g.sound.sfx(Sound.PLINK, 3);
                    } else {
                        g.toast("CARGO FULL", 0xFFFF5050);
                    }
                }
                l[5] = -1;
            }
        }
        loot.removeIf(l -> l[5] < 0);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void draw(Game g, SceneCanvas c) {
        renderer.draw(c, g.width, g.height);
        renderer.drawStars(c, cam, g.ticks);
        drawSprites(g, c);
        if (pulsing || arrivalTicks > 0 && arrive == ARRIVE_WARP) {
            drawStreaks(g, c);
        }
        if (transition > 0 && transitionKind == 1) {
            drawReentry(g, c);
        }
        drawMarkers(g, c);
        drawCockpit(g, c);
        if (hitFlash > 0) {
            c.fill(0, 0, g.width, g.height, Colour.alpha(0xFFFF4020, hitFlash * 10));
        }
        if (transition > 0) {
            int a = Math.min(255, transition * (transitionKind == 1 ? 6 : 5));
            c.fill(0, 0, g.width, g.height, Colour.alpha(transitionKind == 1 ? 0xFFFFE0C0 : 0xFF000000, a));
        }
    }

    private void drawSprites(Game g, SceneCanvas c) {
        Art art = g.art;
        // Asteroids (back to front, culled and occluded by planets).
        List<double[]> visible = new ArrayList<>();
        for (double[] r : rocks) {
            double[] p = cam.project(r[0], r[1], r[2]);
            if (p == null || p[2] > 14000 || p[0] < -80 || p[0] > g.width + 80 || p[1] < -80 || p[1] > g.height + 80) {
                continue;
            }
            if (renderer.occluded(p[0], p[1], p[2], g.width, g.height)) {
                continue;
            }
            visible.add(new double[] {p[0], p[1], p[2], r[3], r[5], r[4]});
        }
        visible.sort((a, b) -> Double.compare(b[2], a[2]));
        for (double[] v : visible) {
            int frame = (int) v[4] % 3;
            SceneSprite rock = art.frame("rock", frame);
            double scale = v[3] / v[2] * cam.focal / 32.0;
            int light = Colour.lerp(0xFF8070A0, 0xFFFFFFFF, (float) Math.max(0, 1 - v[2] / 14000));
            if (rock != null && scale > 0.05) {
                c.draw(rock, (float) v[0], (float) v[1], SceneDraw.plain().withScale((float) Math.min(8, scale)).withTint(light));
            } else {
                c.fill((int) v[0], (int) v[1], 1, 1, 0xFF908070);
            }
        }
        // Loot.
        for (double[] l : loot) {
            double[] p = cam.project(l[0], l[1], l[2]);
            if (p == null) {
                continue;
            }
            int col = l[3] == -1 ? Ui.GOLD : l[3] == -2 ? 0xFF80FFE0 : g.catalog.item((int) l[3]).colour();
            int s = (int) Math.max(2, Math.min(8, 600 / p[2]));
            c.fill((int) p[0] - s, (int) p[1] - s, s * 2, s * 2, Colour.alpha(col, 80));
            c.fill((int) p[0] - s / 2, (int) p[1] - s / 2, s, s, col);
        }
        // Ships.
        for (Ship3D e : enemies) {
            double[] p = cam.project(e.x, e.y, e.z);
            if (p == null || renderer.occluded(p[0], p[1], p[2], g.width, g.height)) {
                continue;
            }
            double scale = (e.tornado ? 160 : 110) / p[2] * cam.focal / 48.0;
            scale = Math.max(0.1, Math.min(6, scale));
            SceneDraw style = SceneDraw.plain().withScale((float) scale).withFlipX(e.vx * cam.right[0] + e.vz * cam.right[2] > 0);
            if (e.hit > 0) {
                style = style.withFlash(0xFFFFFFFF);
            }
            if (e.tornado) {
                SceneSprite body = art.frame("tornado", 0);
                SceneSprite prop = art.frame("tornado", 1 + (int) ((g.ticks / 2) % 4));
                if (body != null) {
                    c.draw(body, (float) p[0], (float) p[1], style);
                }
                if (prop != null) {
                    c.draw(prop, (float) p[0], (float) p[1], style);
                }
            } else {
                SceneSprite sprite = pirateSprite(g, e);
                if (sprite != null) {
                    c.draw(sprite, (float) p[0], (float) p[1], style);
                } else {
                    int s = (int) (12 * scale);
                    c.fill((int) p[0] - s, (int) p[1] - s / 3, s * 2, s * 2 / 3, 0xFFC03030);
                }
            }
        }
        // Bolts and sparks.
        for (double[] b : bolts) {
            double[] p = cam.project(b[0], b[1], b[2]);
            if (p == null) {
                continue;
            }
            int s = (int) Math.max(1, Math.min(5, 400 / p[2]));
            int col = b[7] > 0 ? 0xFFFF4040 : 0xFF40FFFF;
            c.fill((int) p[0] - s, (int) p[1] - s, s * 2, s * 2, Colour.alpha(col, 120));
            c.fill((int) p[0] - s / 2, (int) p[1] - s / 2, Math.max(1, s), Math.max(1, s), 0xFFFFFFFF);
        }
        for (double[] s : sparks) {
            double[] p = cam.project(s[0], s[1], s[2]);
            if (p == null) {
                continue;
            }
            int size = (int) Math.max(1, Math.min(4, 300 / p[2]));
            c.fill((int) p[0], (int) p[1], size, size, Colour.fade((int) s[7], (float) Math.min(1, s[6] / 15)));
        }
    }

    private SceneSprite pirateSprite(Game g, Ship3D e) {
        String[] keys = {"balkiry", "nebula", "turtloid"};
        String key = keys[e.sprite % keys.length];
        FaunaDef def = g.art.fauna.byKey(key);
        if (def == null || g.art.rom(def.game()) == null) {
            def = g.art.fauna.byKey(e.sprite % 2 == 0 ? "flybot" : "batbot");
        }
        if (def == null || g.art.rom(def.game()) == null) {
            return null;
        }
        SceneSpriteSet set = enemySets.get(def.key());
        if (set == null && !enemySets.containsKey(def.key())) {
            int[] pal = def.game().equals("s2") ? s2Palette(g) : null;
            set = g.art.creature(def, pal == null ? s3kPalette(g) : pal, "space:" + def.key());
            enemySets.put(def.key(), set);
        }
        if (set == null || set.frameCount() == 0) {
            return null;
        }
        int[] frames = def.frames();
        return set.frame(Math.min(set.frameCount() - 1, frames[(int) ((g.ticks / 4) % frames.length)]));
    }

    private int[] s2Palette(Game g) {
        if (s2pal == null) {
            try {
                // Sky Chase's level palette (its badniks' home).
                s2pal = g.art.rom("s2").levelKit(8, 0).palette();
            } catch (RuntimeException failed) {
                s2pal = new int[64];
            }
        }
        return s2pal;
    }

    private int[] s3kPalette(Game g) {
        if (s3kpal == null) {
            try {
                s3kpal = g.art.s3k().levelKit(6, 0).palette();
            } catch (RuntimeException failed) {
                s3kpal = new int[64];
            }
        }
        return s3kpal;
    }

    private int[] s2pal;
    private int[] s3kpal;

    private void drawStreaks(Game g, SceneCanvas c) {
        int n = 70;
        float t = (float) Math.min(1, speed / 120);
        for (int i = 0; i < n; i++) {
            long h = Rng.mix(i * 31L + g.ticks / 3);
            double ang = (h & 0xFFFF) / 65536.0 * Math.PI * 2;
            double r0 = 30 + ((h >>> 16) & 0xFF) + ((g.ticks * 9 + i * 37) % 200);
            double len = 10 + 40 * t;
            int x0 = (int) (g.width / 2 + Math.cos(ang) * r0);
            int y0 = (int) (g.height / 2 + Math.sin(ang) * r0 * 0.6);
            for (int k = 0; k < len; k += 3) {
                int x = (int) (g.width / 2 + Math.cos(ang) * (r0 + k));
                int y = (int) (g.height / 2 + Math.sin(ang) * (r0 + k) * 0.6);
                c.fill(x, y, 2, 1, Colour.alpha(0xFFC0E0FF, 200 - k * 3));
            }
            c.fill(x0, y0, 1, 1, 0xFFFFFFFF);
        }
    }

    private void drawReentry(Game g, SceneCanvas c) {
        for (int i = 0; i < 60; i++) {
            long h = Rng.mix(i * 977L + g.ticks);
            int x = (int) ((h & 0x3FF) % g.width);
            int y = (int) (((h >>> 10) & 0x3FF) % g.height);
            int len = 6 + (int) ((h >>> 20) & 15);
            c.fill(x, y, 2, len, i % 2 == 0 ? 0xC0FFA020 : 0xA0FF4010);
        }
    }

    private void drawMarkers(Game g, SceneCanvas c) {
        Font f = g.font;
        for (Body b : bodies) {
            double d = b.distance(cam.x, cam.y, cam.z) - b.radius;
            String name = b.station ? "EGG STATION" : b.spec == null ? "ROCK" : g.displayName(b.spec).toUpperCase();
            int col = b.station ? 0xFFFFD040 : 0xFF80E0FF;
            String dist = d > 1000 ? String.format("%.1fK", d / 1000) : Integer.toString((int) d);
            double[] p = cam.project(b.x, b.y, b.z);
            if (p != null && p[0] > 10 && p[0] < g.width - 10 && p[1] > 10 && p[1] < g.height - 50) {
                double sr = b.radius / p[2] * cam.focal;
                if (sr < 30) {
                    int x = (int) p[0];
                    int y = (int) p[1];
                    int r = (int) Math.max(5, sr + 4);
                    c.fill(x - r, y - r, 3, 1, col);
                    c.fill(x - r, y - r, 1, 3, col);
                    c.fill(x + r - 2, y - r, 3, 1, col);
                    c.fill(x + r, y - r, 1, 3, col);
                    c.fill(x - r, y + r, 3, 1, col);
                    c.fill(x - r, y + r - 2, 1, 3, col);
                    c.fill(x + r - 2, y + r, 3, 1, col);
                    c.fill(x + r, y + r - 2, 1, 3, col);
                    f.centre(c, name, x, y + r + 3, col);
                    f.centre(c, dist, x, y + r + 12, 0xFFA0B0D0);
                    if (scanTicks > 0 && b.spec != null) {
                        f.centre(c, b.spec.summary().toUpperCase(), x, y - r - 20, 0xFFFFFFFF);
                        f.centre(c, b.spec.sentinelName().toUpperCase() + " / " + b.spec.stormName().toUpperCase(), x,
                                y - r - 11, 0xFFFFC080);
                    }
                } else if (scanTicks > 0 && b.spec != null) {
                    f.centre(c, name + " - " + b.spec.summary().toUpperCase(), g.width / 2, 40, col);
                }
            } else {
                // Off-screen: an arrow at the edge.
                double[] cc = cam.toCamera(b.x, b.y, b.z);
                double ax = cc[0];
                double ay = -cc[1];
                if (cc[2] < 0) {
                    ax = -ax;
                    ay = -ay;
                    if (Math.abs(ax) < 1 && Math.abs(ay) < 1) {
                        ay = 1;
                    }
                }
                double len = Math.max(0.001, Math.sqrt(ax * ax + ay * ay));
                int ex = (int) (g.width / 2 + ax / len * (g.width / 2 - 16));
                int ey = (int) (g.height / 2 - 20 + ay / len * (g.height / 2 - 40));
                c.fill(ex - 2, ey - 2, 5, 5, col);
                c.fill(ex - 1, ey - 1, 3, 3, 0xFFFFFFFF);
                String label = name.length() > 12 ? name.substring(0, 12) : name;
                int lx = Math.max(Font.width(label) / 2 + 2, Math.min(g.width - Font.width(label) / 2 - 2, ex));
                f.centre(c, label, lx, ey + 5, col);
                f.centre(c, dist, lx, ey + 13, 0xFFA0B0D0);
            }
        }
        for (Ship3D e : enemies) {
            double[] p = cam.project(e.x, e.y, e.z);
            int col = e.tornado ? 0xFF4080FF : 0xFFFF4040;
            if (p != null && p[0] > 0 && p[0] < g.width && p[1] > 0 && p[1] < g.height) {
                int x = (int) p[0];
                int y = (int) p[1];
                c.fill(x - 12, y - 12, 4, 1, col);
                c.fill(x + 8, y - 12, 4, 1, col);
                c.fill(x - 12, y + 12, 4, 1, col);
                c.fill(x + 8, y + 12, 4, 1, col);
                Ui.bar(c, x - 12, y + 15, 24, 2, (float) (e.hp / e.maxHp), col, false);
            } else {
                double[] cc = cam.toCamera(e.x, e.y, e.z);
                double ax = cc[2] < 0 ? -cc[0] : cc[0];
                double ay = cc[2] < 0 ? cc[1] : -cc[1];
                double len = Math.max(0.001, Math.sqrt(ax * ax + ay * ay));
                int ex = (int) (g.width / 2 + ax / len * (g.width / 2 - 12));
                int ey = (int) (g.height / 2 - 20 + ay / len * (g.height / 2 - 40));
                c.fill(ex - 2, ey - 2, 4, 4, (g.ticks / 6) % 2 == 0 ? col : 0xFFFFFFFF);
            }
        }
        // Crosshair.
        int cx = g.width / 2;
        int cy = g.height / 2 - 20;
        c.fill(cx - 8, cy, 5, 1, 0xC0FFFFFF);
        c.fill(cx + 4, cy, 5, 1, 0xC0FFFFFF);
        c.fill(cx, cy - 8, 1, 5, 0xC0FFFFFF);
        c.fill(cx, cy + 4, 1, 5, 0xC0FFFFFF);
        if (dockPrompt > 0) {
            f.centre(c, "DOCKING...", cx, cy + 20, 0xFFFFD040);
        }
    }

    private void drawCockpit(Game g, SceneCanvas c) {
        Player p = g.player;
        Font f = g.font;
        int w = g.width;
        int h = g.height;
        // The Egg Mobile's glass dome frame in the top corners.
        for (int i = 0; i < 28; i++) {
            int len = (int) (28 - Math.sqrt(Math.max(0, 28 * 28 - (28 - i) * (28 - i))));
            c.fill(0, i, len + 2, 1, 0xFF303848);
            c.fill(w - len - 2, i, len + 2, 1, 0xFF303848);
            c.fill(len + 2, i, 1, 1, 0xFF6878A0);
            c.fill(w - len - 3, i, 1, 1, 0xFF6878A0);
        }
        // Dashboard.
        int top = h - 38;
        c.fill(0, top, w, 38, 0xFF283040);
        c.fill(0, top, w, 2, 0xFF7888B0);
        c.fill(0, top + 2, w, 1, 0xFF101420);
        for (int x = 6; x < w; x += 24) {
            c.fill(x, top + 34, 2, 2, 0xFF505C78);
        }
        // Yellow-black hazard trim, as on the Egg Mobile.
        for (int x = 0; x < w; x += 8) {
            c.fill(x, h - 3, 4, 3, 0xFFE0C020);
            c.fill(x + 4, h - 3, 4, 3, 0xFF181818);
        }
        f.draw(c, "SHIELD", 8, top + 6, 0xFF80E8FF);
        Ui.bar(c, 46, top + 7, 90, 5, p.shipShield / p.maxShipShield(), 0xFF40E8FF, false);
        f.draw(c, "HULL", 8, top + 16, 0xFFFF9070);
        Ui.bar(c, 46, top + 17, 90, 5, p.hull / p.maxHull(), 0xFFFF6040, p.hull < p.maxHull() * 0.25f && (g.ticks / 8) % 2 == 0);
        f.draw(c, "PULSE", 8, top + 26, 0xFFC0A0FF);
        Ui.bar(c, 46, top + 27, 90, 5, p.pulse / 100f, pulsing ? 0xFFFFFFFF : 0xFFA080FF, false);
        // Eggman on the comm screen.
        int sx = w / 2 - 22;
        c.fill(sx - 2, top + 3, 48, 33, 0xFF0C1018);
        c.fill(sx, top + 5, 44, 29, 0xFF1A3040);
        int head = hitFlash > 0 ? Art.SHIP_HEAD_HURT : combatMusic && (g.ticks / 30) % 4 == 0 ? Art.SHIP_HEAD_LAUGH
                : (g.ticks / 12) % 2 == 0 ? Art.SHIP_HEAD_IDLE0 : Art.SHIP_HEAD_IDLE1;
        SceneSprite face = g.art.frame("ship", head);
        c.clip(sx, top + 5, 44, 29);
        if (face != null) {
            float fx = sx + 22 - (face.width() / 2f - face.originX()) * 1.3f;
            float fy = top + 19 - (face.height() / 2f - face.originY()) * 1.3f;
            c.draw(face, fx, fy, SceneDraw.plain().withScale(1.3f));
        }
        for (int y = top + 5; y < top + 34; y += 2) {
            c.fill(sx, y, 44, 1, 0x30000000);
        }
        c.unclip();
        // Speed, rings and the star system.
        String spd = pulsing ? "PULSE" : Integer.toString((int) (speed * 12)) + " U/S";
        f.draw(c, spd, w / 2 + 30, top + 6, pulsing ? 0xFFFFFFFF : 0xFF90F0A0);
        f.draw(c, Ui.num(p.rings), w / 2 + 30, top + 16, Ui.GOLD);
        f.draw(c, Ui.num(p.shards) + " CS", w / 2 + 30, top + 26, 0xFF80FFE0);
        f.right(c, system.name.toUpperCase(), w - 8, top + 6, 0xFFFFFFFF);
        f.right(c, system.className().toUpperCase(), w - 8, top + 16, system.colour());
        f.right(c, "CORE " + Ui.num((long) system.distanceToCore()) + " LY", w - 8, top + 26, 0xFFC0C8E0);
        if (g.player.tutorial <= 6 && arrivalTicks <= 0 && g.ticks % 600 < 300) {
            f.centre(c, "ARROWS STEER  SPACE FIRE  HOLD C BOOST/PULSE  X SCAN  ENTER MENU", w / 2, top - 10, 0xC0FFFFFF);
        }
    }

    @Override
    public boolean live() {
        return transition == 0;
    }
}
