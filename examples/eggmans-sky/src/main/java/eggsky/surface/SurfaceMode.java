package eggsky.surface;

import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import eggsky.Game;
import eggsky.Mode;
import eggsky.art.Art;
import eggsky.art.PixelArt;
import eggsky.core.Colour;
import eggsky.core.Controls;
import eggsky.core.Rng;
import eggsky.core.Sound;
import eggsky.game.Catalog;
import eggsky.game.Player;
import eggsky.game.Vitals;
import eggsky.ui.MenuMode;
import eggsky.world.Planet;
import eggsky.world.Species;
import eggsky.world.StarSystem;
import eggsky.world.Terrain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Exploring a planet's surface in the Egg Mobile: hover over remixed zone terrain, mine with the
 * laser, scan and analyse species, open monitors and capsules, read echidna ruins, survive the
 * climate and storms, and outrun (or repel) Sonic and friends when the wanted level rises.
 */
public final class SurfaceMode implements Mode {
    public static final int ARRIVE_DESCEND = 0;
    public static final int ARRIVE_RESUME = 1;

    private static final int DESCEND = 0;
    private static final int PLAY = 1;
    private static final int LAUNCH = 2;
    private static final int DEAD = 3;

    private static final float BEAM_RANGE = 130;
    private static final int LAUNCH_HOLD = 70;

    public Game game;
    public final Planet planet;
    public final Terrain terrain;
    public final Ship ship = new Ship();
    public final Particles particles = new Particles();
    public final List<Thing> things = new ArrayList<>();
    public final List<Creature> creatures = new ArrayList<>();
    public final List<Hero> heroes = new ArrayList<>();
    public final List<Pickup> pickups = new ArrayList<>();
    public final List<Shot> shots = new ArrayList<>();
    public Weather weather;
    private final int arrive;
    private boolean initialised;
    private int phase;
    private int phaseTicks;
    /** Camera: world x at the screen centre, world y at the screen top. */
    public float camX;
    public float camY;
    private float lastCamX;
    private float lastCamY;
    private final Rng rng;

    // Laser.
    public boolean beamOn;
    public float beamX;
    public float beamY;
    public float heat;
    public boolean overheated;
    private Object beamTarget;
    private int blasterCooldown;
    // Scanner.
    private float pulse = -1;
    private int pulseCooldown;
    private int scanHeld;
    public boolean visor;
    public Object visorTarget;
    public float analysis;
    // Sentinels.
    public float wanted;
    private int heroWave;
    private int unseenTicks;
    private int wantedFlash;
    private int shieldDelay;
    private int invincible;
    private int speedShoes;
    // Dialog.
    private String dialogTitle;
    private List<String> dialogLines;
    private String[] dialogOptions;
    private Runnable[] dialogActions;
    private int dialogChoice;
    // Caches.
    private final HashMap<String, SceneSpriteSet> creatureSets = new HashMap<>();
    private final HashMap<String, SceneSprite> creatureFrames = new HashMap<>();
    private final SceneSprite[] emeralds = new SceneSprite[7];
    private SceneImage backdropImage;
    private float hazardDrain;
    private boolean sheltered;
    private int tutorialShown = -1;
    private int noFuelToast;
    private float descentTargetY;

    public SurfaceMode(Planet planet, int arrive) {
        this.planet = planet;
        this.terrain = planet.terrain;
        this.arrive = arrive;
        this.rng = new Rng(Rng.hash(planet.spec.seed, 0x53555246L));
    }

    @Override
    public void enter(Game g) {
        this.game = g;
        if (initialised) {
            g.sound.music(planet.spec.biome.music());
            return;
        }
        initialised = true;
        ship.measure(g.art);
        weather = new Weather(planet.spec, Rng.hash(planet.spec.seed, 0x57455448L));
        backdropImage = planet.backdrop;
        populate(g);
        Player p = g.player;
        float startX;
        if (arrive == ARRIVE_RESUME && p.surfaceX >= 0) {
            startX = p.surfaceX;
            ship.x = startX;
            ship.y = p.surfaceY;
            phase = PLAY;
            if (ship.embedded(terrain)) {
                placeOnSurface(startX);
            }
        } else {
            startX = landingX(p);
            int floor = terrain.topFloor(startX);
            descentTargetY = (floor < 0 ? 200 : floor) - 40;
            ship.x = startX;
            ship.y = Math.min(descentTargetY - 360, -120);
            ship.vy = 5;
            phase = DESCEND;
            phaseTicks = 0;
        }
        camX = ship.x;
        camY = ship.y - g.height * 0.5f;
        lastCamX = camX;
        lastCamY = camY;
        g.sound.music(planet.spec.biome.music());
        String key = planet.key;
        if (!p.visitedPlanets.contains(key)) {
            p.visitedPlanets.add(key);
            p.statPlanets++;
            pendingPlanetBanner = true;
        } else {
            pendingPlanetBanner = arrive == ARRIVE_DESCEND;
            planetBannerRepeat = true;
        }
        if (p.visitedSystems.add(planet.system.id)) {
            p.statWarps += 0;
        }
    }

    private boolean pendingPlanetBanner;
    private boolean planetBannerRepeat;

    private float landingX(Player p) {
        if (p.beaconPlanet == planet.spec.index && p.systemId == planet.system.id && p.beaconX > 0 && arrive == ARRIVE_RESUME) {
            return p.beaconX;
        }
        // A clear patch of top floor with open sky above.
        for (int attempt = 0; attempt < 40; attempt++) {
            float x = rng.range(0f, terrain.width());
            int floor = terrain.topFloor(x);
            if (floor > 60) {
                return x;
            }
        }
        return terrain.width() / 2f;
    }

    private void placeOnSurface(float x) {
        int floor = terrain.topFloor(x);
        ship.x = x;
        ship.y = (floor < 0 ? 100 : floor) - ship.bottom - 12;
        ship.vx = 0;
        ship.vy = 0;
    }

    @Override
    public void exit(Game g) {
        saveSurfacePosition();
    }

    public void saveSurfacePosition() {
        if (game != null) {
            game.lastWanted = wanted;
        }
        if (game != null && game.player != null && phase == PLAY) {
            game.player.surfaceX = ship.x;
            game.player.surfaceY = ship.y;
        }
    }

    // ---------------------------------------------------------------- population

    private void populate(Game g) {
        Player p = g.player;
        for (Planet.Placement pl : planet.placements) {
            switch (pl.kind()) {
                case Planet.P_FAUNA -> {
                    Species s = planet.fauna.get(pl.variant());
                    int count = s.body.animal() ? rng.range(2, 4) : rng.range(1, 3);
                    for (int i = 0; i < count; i++) {
                        creatures.add(new Creature(s, pl.id() + "." + i, terrain.wrapX(pl.x() + i * 24), pl.y(),
                                Rng.hash(planet.spec.seed, pl.id().hashCode(), i)));
                    }
                }
                default -> {
                    Species species = null;
                    SceneSprite sprite = null;
                    switch (pl.kind()) {
                        case Planet.P_FLORA -> {
                            species = planet.flora.get(pl.variant());
                            sprite = species.sprites[(int) Math.floorMod(Rng.mix(pl.id().hashCode()), 3L)];
                        }
                        case Planet.P_MINERAL -> {
                            species = planet.minerals.get(pl.variant());
                            sprite = species.sprites[(int) Math.floorMod(Rng.mix(pl.id().hashCode()), 3L)];
                        }
                        case Planet.P_CRYSTAL -> sprite = planet.crystals[pl.variant()];
                        case Planet.P_COBALT -> sprite = planet.crystals[2];
                        case Planet.P_OXYGEN -> sprite = planet.oxygenPlant;
                        case Planet.P_SODIUM -> sprite = planet.sodiumPlant;
                        case Planet.P_SPECIAL -> sprite = planet.specialPlant;
                        case Planet.P_GOLD -> sprite = planet.goldVein;
                        default -> {
                        }
                    }
                    Thing t = new Thing(pl, species, sprite);
                    int yield = species != null ? species.yield : Thing.defaultYield(pl.kind(), planet, pl.variant());
                    int count = species != null ? species.yieldCount : switch (pl.kind()) {
                        case Planet.P_GOLD -> rng.range(6, 14);
                        case Planet.P_COBALT -> rng.range(10, 20);
                        case Planet.P_CRYSTAL -> rng.range(14, 30);
                        case Planet.P_WRECK -> rng.range(25, 45);
                        default -> rng.range(10, 22);
                    };
                    t.setYield(yield, count, Thing.defaultHealth(pl.kind()) * (species != null && pl.kind() == Planet.P_FLORA
                            && sprite != null && sprite.height() > 50 ? 1.6f : 1f));
                    if (p.looted.contains(pl.id())) {
                        if (pl.kind() == Planet.P_MONITOR || pl.kind() == Planet.P_RINGS || pl.kind() == Planet.P_WRECK
                                || pl.kind() == Planet.P_GIANT_RING) {
                            t.alive = pl.kind() != Planet.P_GIANT_RING && pl.kind() != Planet.P_RINGS;
                            t.spent = true;
                            if (pl.kind() == Planet.P_WRECK) {
                                t.alive = false;
                            }
                        } else {
                            t.spent = true;
                        }
                    }
                    if (pl.kind() == Planet.P_SHRINE && p.emeraldCount() >= 7) {
                        t.spent = true;
                    }
                    things.add(t);
                }
            }
        }
        if (p.graveSystem == planet.system.id && p.gravePlanet == planet.spec.index && !p.graveCargo.isEmpty()) {
            Planet.Placement grave = new Planet.Placement(Planet.P_WRECK, p.graveX, p.graveY, 0, "grave");
            Thing t = new Thing(grave, null, null);
            t.setYield(0, 0, 1);
            graveThing = t;
            things.add(t);
        }
    }

    private Thing graveThing;

    // ---------------------------------------------------------------- update

    @Override
    public void update(Game g) {
        this.game = g;
        Controls in = g.in;
        Player p = g.player;
        if (dialogTitle != null) {
            updateDialog(g);
            return;
        }
        if (in.menuPressed && phase == PLAY) {
            saveSurfacePosition();
            g.setMode(new MenuMode(this));
            return;
        }
        phaseTicks++;
        weather.update(g);
        float gravity = 0.11f * planet.spec.gravity;
        switch (phase) {
            case DESCEND -> {
                ship.vx *= 0.95f;
                float remaining = descentTargetY - ship.y;
                ship.vy = Math.max(0.8f, Math.min(6, remaining * 0.03f));
                ship.y += ship.vy;
                if (ship.y > -40 && phaseTicks % 2 == 0) {
                    particles.add(ship.x + rng.range(-8f, 8f), ship.y + ship.bottom, rng.range(-0.5f, 0.5f), 1.5f, 0,
                            3, 0xFFFFA030, 18, true);
                }
                if (remaining < 4 || phaseTicks > 400) {
                    phase = PLAY;
                    phaseTicks = 0;
                    g.sound.sfx(Sound.FLOOR_THUMP);
                    g.shake = 3;
                    for (int i = 0; i < 12; i++) {
                        particles.add(ship.x + rng.range(-20f, 20f), ship.y + ship.bottom + 10, rng.range(-1.5f, 1.5f),
                                -rng.range(0.2f, 1f), 0.03f, 4, 0xC0C8B898, 40, false);
                    }
                    afterLanding(g);
                }
            }
            case LAUNCH -> {
                ship.vy = Math.max(-9, ship.vy - 0.25f);
                ship.y += ship.vy;
                ship.vx *= 0.9f;
                ship.x = terrain.wrapX(ship.x + ship.vx);
                for (int i = 0; i < 3; i++) {
                    particles.add(ship.x + rng.range(-6f, 6f), ship.y + ship.bottom, rng.range(-0.6f, 0.6f),
                            rng.range(2f, 4f), 0, 4, i == 0 ? 0xFFFFFFA0 : 0xFFFF8020, 22, true);
                }
                g.shake = Math.max(g.shake, 2);
                if (phaseTicks > 110) {
                    g.launch();
                    return;
                }
            }
            case DEAD -> {
                ship.vy += 0.15f;
                ship.y += ship.vy;
                if (phaseTicks % 6 == 0 && phaseTicks < 80) {
                    explosion(ship.x + rng.range(-24f, 24f), ship.y + rng.range(-24f, 16f), true);
                }
                if (phaseTicks == 150) {
                    respawn(g);
                }
                return;
            }
            default -> {
                // Play.
            }
        }
        if (phase == PLAY) {
            boolean control = true;
            float speedBoost = speedShoes > 0 ? 1.5f : 1f;
            ship.update(g, terrain, in, gravity, control, speedBoost);
            if (ship.embedded(terrain)) {
                ship.y -= 2;
            }
            updateLaunchInput(g, in);
            updateBeam(g, in);
            updateScanner(g, in);
            updateVitals(g);
            updateWanted(g);
            updateInteractions(g);
            hotkeys(g);
            tutorial(g);
            if (invincible > 0) {
                invincible--;
                if (invincible == 0) {
                    g.sound.music(planet.spec.biome.music());
                }
                if (invincible % 4 == 0) {
                    particles.add(ship.x + rng.range(-20f, 20f), ship.y + rng.range(-30f, 10f), 0, -0.4f, 0, 2,
                            0xFFFFFFFF, 16, true);
                }
            }
            if (speedShoes > 0) {
                speedShoes--;
            }
        }
        updateEntities(g);
        updatePickups(g);
        updateShots(g);
        particles.update();
        if (ship.boosting && phase == PLAY && g.ticks % 2 == 0) {
            particles.add(ship.x - ship.facing * (ship.halfWidth + 8), ship.y + 2, -ship.facing * 1.5f + ship.vx * 0.3f,
                    rng.range(-0.3f, 0.3f), 0, 2.5f, 0xFFFFC060, 12, true);
        }
        updateCamera(g);
    }

    private void afterLanding(Game g) {
        Player p = g.player;
        if (pendingPlanetBanner) {
            pendingPlanetBanner = false;
            String name = g.displayName(planet.spec);
            if (planetBannerRepeat) {
                g.banner(name.toUpperCase(), planet.spec.summary(), 0xFF80C0FF);
            } else {
                int reward = 150 + 50 * planet.spec.index;
                p.rings += reward;
                p.statRingsEarned += reward;
                g.banner("PLANET DISCOVERED", name + "  +" + reward + " RINGS", 0xFF60FF90);
                g.sound.sfx(Sound.SIGNPOST);
            }
            if (planet.spec.biome.hazardous()) {
                g.toast("HAZARD: " + eggsky.world.Biome.hazardName(planet.spec.biome.climate()), 0xFFFF9040);
            }
            if (planet.spec.sentinels >= 2) {
                g.toast(planet.spec.sentinelName().toUpperCase(), 0xFFFF6060);
            }
        }
        g.save();
    }

    private void updateLaunchInput(Game g, Controls in) {
        Player p = g.player;
        if (in.up && in.boost) {
            ship.launchHold++;
            if (p.launchFuel < 25) {
                if (noFuelToast <= 0) {
                    g.toast("LAUNCH THRUSTERS NEED FUEL", 0xFFFF6040);
                    g.toast("Recharge with Di-hydrogen or Egg Fuel", 0xFFFFC080);
                    noFuelToast = 180;
                    g.sound.sfx(Sound.ERROR, 30);
                }
                ship.launchHold = 0;
            } else if (ship.launchHold == 1) {
                g.sound.sfx(Sound.LAUNCH_READY, 20);
            } else if (ship.launchHold >= LAUNCH_HOLD) {
                p.launchFuel -= 25;
                phase = LAUNCH;
                phaseTicks = 0;
                ship.vy = -1;
                g.sound.sfx(Sound.LAUNCH_GO);
                g.sound.sfx(Sound.RISING, 5);
                ship.laughTicks = 90;
                if (p.tutorial == 4) {
                    p.tutorial = 5;
                }
            }
        } else {
            ship.launchHold = 0;
        }
        if (noFuelToast > 0) {
            noFuelToast--;
        }
    }

    // ---------------------------------------------------------------- laser

    private void updateBeam(Game g, Controls in) {
        Player p = g.player;
        float cooling = 1.1f;
        if (overheated) {
            heat -= cooling;
            if (heat <= 0) {
                heat = 0;
                overheated = false;
            }
            beamOn = false;
            return;
        }
        beamOn = in.beam;
        if (!beamOn) {
            heat = Math.max(0, heat - cooling);
            beamTarget = null;
            return;
        }
        heat += 0.42f * (1 - 0.22f * p.level(Catalog.T_COOLER));
        if (heat >= 100) {
            heat = 100;
            overheated = true;
            g.toast("LASER OVERHEATED", 0xFFFF5030);
            g.sound.sfx(Sound.FIRE_SHIELD, 30);
            for (int i = 0; i < 10; i++) {
                particles.add(ship.gunX(), ship.gunY(), rng.range(-1f, 1f), rng.range(-2f, -0.5f), 0.02f, 3,
                        0xC0A0A0A0, 40, false);
            }
            return;
        }
        float gx = ship.gunX();
        float gy = ship.gunY();
        float dirX;
        float dirY;
        if (in.mouseAim) {
            float wx = camX - g.width / 2f + in.mouseX;
            float wy = camY + in.mouseY;
            dirX = terrain.dx(gx, wx);
            dirY = wy - gy;
            ship.facing = dirX >= 0 ? 1 : -1;
            gx = ship.gunX();
        } else {
            Object target = autoTarget(gx, gy);
            if (target != null) {
                float[] c = centreOf(target);
                dirX = terrain.dx(gx, c[0]);
                dirY = c[1] - gy;
            } else {
                dirX = ship.facing;
                dirY = in.down ? 1.2f : in.up ? -0.6f : 0.35f;
            }
        }
        float len = (float) Math.hypot(dirX, dirY);
        if (len < 0.001f) {
            dirX = ship.facing;
            dirY = 0;
            len = 1;
        }
        dirX /= len;
        dirY /= len;
        // March the ray; stop at the first entity or solid terrain.
        Object hit = null;
        float hx = gx;
        float hy = gy;
        boolean terrainHit = false;
        for (float d = 0; d <= BEAM_RANGE; d += 3) {
            hx = gx + dirX * d;
            hy = gy + dirY * d;
            hit = entityAt(hx, hy);
            if (hit != null) {
                break;
            }
            if (terrain.solid((int) hx, (int) hy)) {
                terrainHit = true;
                break;
            }
        }
        beamX = hx;
        beamY = hy;
        beamTarget = hit;
        float dps = 1.15f * (1 + 0.35f * p.level(Catalog.T_BEAM));
        if (g.ticks % 3 == 0) {
            particles.add(hx, hy, rng.range(-1.5f, 1.5f), rng.range(-2f, 0.5f), 0.08f, 2, 0xFFFFE060, 14, true);
        }
        g.sound.sfx(Sound.LASER, 9);
        if (hit instanceof Thing t) {
            damageThing(g, t, dps);
        } else if (hit instanceof Creature c) {
            damageCreature(g, c, dps * 0.6f);
        } else if (hit instanceof Hero h) {
            damageHero(g, h, dps * 0.5f);
        } else if (terrainHit) {
            int shaper = p.level(Catalog.T_SHAPER);
            if (shaper > 0 && g.ticks % 4 == 0) {
                int removed = terrain.carve((int) hx, (int) hy, 5 + shaper * 2);
                if (removed > 0) {
                    terrainYield += removed;
                    if (terrainYield > 90) {
                        terrainYield -= 90;
                        gain(g, Catalog.FERRITE, 2, hx, hy);
                    }
                    if (rng.chance(0.04)) {
                        gain(g, Catalog.CARBON, 1, hx, hy);
                    }
                    for (int i = 0; i < 3; i++) {
                        particles.add(hx, hy, rng.range(-2f, 2f), rng.range(-2.5f, 0f), 0.15f, 2,
                                0xFFA08060, 30, false);
                    }
                }
            } else if (g.ticks % 2 == 0) {
                particles.add(hx, hy, rng.range(-1.2f, 1.2f), rng.range(-1.6f, 0f), 0.1f, 1.5f, 0xFFFFFFFF, 10, true);
            }
        }
        // The Egg Blaster fires bolts at living targets while the laser is on them.
        if (p.level(Catalog.T_BLASTER) > 0 && (hit instanceof Creature || hit instanceof Hero)) {
            if (blasterCooldown-- <= 0) {
                blasterCooldown = 14 - p.level(Catalog.T_BLASTER) * 2;
                shots.add(new Shot(Shot.BOLT, false, gx, gy, dirX * 7, dirY * 7, 3 + 2 * p.level(Catalog.T_BLASTER), 40));
                g.sound.sfx(Sound.MISSILE_SHOOT, 6);
            }
        }
    }

    private int terrainYield;

    private Object autoTarget(float gx, float gy) {
        Object best = null;
        float bestScore = Float.MAX_VALUE;
        for (Hero h : heroes) {
            if (h.alive) {
                float s = targetScore(gx, gy, h.x, h.centreY()) - 60;
                if (s < bestScore) {
                    bestScore = s;
                    best = h;
                }
            }
        }
        for (Thing t : things) {
            if (t.mineable()) {
                float s = targetScore(gx, gy, t.x, t.centreY());
                if (s < bestScore) {
                    bestScore = s;
                    best = t;
                }
            }
        }
        for (Creature c : creatures) {
            if (c.alive && (c.state == Creature.ATTACK || c.angered)) {
                float s = targetScore(gx, gy, c.x, c.centreY()) - 30;
                if (s < bestScore) {
                    bestScore = s;
                    best = c;
                }
            }
        }
        return bestScore < BEAM_RANGE * 1.3f ? best : null;
    }

    private float targetScore(float gx, float gy, float x, float y) {
        float dx = terrain.dx(gx, x);
        float dy = y - gy;
        float dist = (float) Math.hypot(dx, dy);
        if (dist > BEAM_RANGE - 6 || dx * ship.facing < -8) {
            return Float.MAX_VALUE;
        }
        // Prefer things ahead and level with the pod.
        return dist + Math.abs(dy) * 0.6f;
    }

    private float[] centreOf(Object o) {
        if (o instanceof Thing t) {
            return new float[] {t.x, t.centreY()};
        }
        if (o instanceof Creature c) {
            return new float[] {c.x, c.centreY()};
        }
        Hero h = (Hero) o;
        return new float[] {h.x, h.centreY()};
    }

    private Object entityAt(float x, float y) {
        for (Hero h : heroes) {
            if (h.alive && Math.abs(terrain.dx(h.x, x)) < h.halfWidth() + 2 && y > h.y - h.height() - 2 && y < h.y + 2) {
                return h;
            }
        }
        for (Creature c : creatures) {
            if (c.alive && Math.abs(terrain.dx(c.x, x)) < c.halfWidth() && y > c.y - c.height() && y < c.y + 2) {
                return c;
            }
        }
        for (Thing t : things) {
            if (t.mineable() && Math.abs(terrain.dx(t.x, x)) < t.halfWidth() && y > t.y - t.height() && y < t.y + 2) {
                return t;
            }
        }
        return null;
    }

    private void damageThing(Game g, Thing t, float dps) {
        t.hp -= dps;
        t.hit = 4;
        if (g.ticks % 5 == 0) {
            int c = t.sprite != null ? t.sprite.image().pixel(t.sprite.width() / 2, t.sprite.height() / 2) : 0xFFA0A0A0;
            if ((c >>> 24) == 0) {
                c = 0xFFC0C0C0;
            }
            particles.add(beamX, beamY, rng.range(-2f, 2f), rng.range(-2.5f, -0.5f), 0.15f, 2.5f, c, 30, false);
        }
        if (t.hp > 0) {
            return;
        }
        t.alive = false;
        Player p = g.player;
        p.statMined++;
        switch (t.kind) {
            case Planet.P_MONITOR -> openMonitor(g, t);
            case Planet.P_WRECK -> {
                if (t == graveThing) {
                    return;
                }
                p.looted.add(t.id);
                explosion(t.x, t.y - 20, true);
                gain(g, Catalog.BADNIK_SCRAP, t.yieldCount, t.x, t.y - 20);
                gain(g, Catalog.BADNIK_CORE, 1, t.x, t.y - 20);
                scatterShards(t.x, t.y - 30, 40 + rng.nextInt(40));
                g.toast("SALVAGED A BADNIK WRECK", 0xFFFFD050);
            }
            default -> {
                g.sound.sfx(t.kind == Planet.P_FLORA || t.kind == Planet.P_OXYGEN || t.kind == Planet.P_SODIUM
                        ? Sound.GRAB : Sound.BREAK, 4);
                int colour = g.catalog.item(t.yield) == null ? 0xFFFFFFFF : g.catalog.item(t.yield).colour();
                particles.burst(t.x, t.centreY(), 14, 2.6f, colour, 0xFFFFFFFF, 0.12f, 30, t.seed);
                float bonus = 1 + 0.1f * p.level(Catalog.T_BEAM);
                gain(g, t.yield, Math.round(t.yieldCount * bonus), t.x, t.centreY());
                if (t.kind == Planet.P_CRYSTAL && t.variant == 3) {
                    gain(g, Catalog.STORM_CRYSTAL, 1, t.x, t.centreY());
                }
                // Heavy mining draws attention.
                float heat = t.kind == Planet.P_GOLD || t.kind == Planet.P_CRYSTAL ? 0.22f : 0.06f;
                addWanted(g, heat);
            }
        }
    }

    private void damageCreature(Game g, Creature c, float dps) {
        c.hp -= dps;
        c.hit = 4;
        c.angered = true;
        if (c.species.body.animal()) {
            addWanted(g, 0.012f);
        }
        if (c.hp > 0) {
            return;
        }
        c.alive = false;
        if (c.species.body.animal()) {
            // Animals are never killed: they flee off the screen in a puff of smoke.
            particles.burst(c.x, c.centreY(), 12, 2, 0xFFFFFFFF, 0xFFC0C0C0, 0, 20, Rng.mix(c.id.hashCode()));
            addWanted(g, 1.1f);
            g.toast("YOU HARMED WILDLIFE!", 0xFFFF5050);
            g.sound.sfx(Sound.ALARM, 30);
            return;
        }
        explosion(c.x, c.centreY(), false);
        gain(g, Catalog.BADNIK_SCRAP, Math.round(3 + 4 * c.species.scale), c.x, c.centreY());
        // A defeated badnik frees the animal inside, as ever.
        freeAnimal(c.x, c.centreY());
        p().statMined++;
    }

    private Player p() {
        return game.player;
    }

    private void damageHero(Game g, Hero h, float dps) {
        int rings = h.damage(dps, ship.x, terrain);
        if (rings > 0) {
            g.sound.sfx(Sound.RING_LOSS, 10);
            for (int i = 0; i < rings * 3; i++) {
                double a = Math.PI * (1.1 + rng.nextFloat() * 0.8);
                float sp = rng.range(2f, 4.5f);
                Pickup pk = new Pickup(Pickup.RINGS, 0, 5, h.x, h.centreY(), (float) Math.cos(a) * sp, (float) Math.sin(a) * sp);
                pk.scattered = true;
                pickups.add(pk);
            }
        }
        if (h.hit == 8) {
            g.sound.sfx(Sound.BOSS_HIT, 8);
        }
    }

    // ---------------------------------------------------------------- scanner

    private void updateScanner(Game g, Controls in) {
        Player p = g.player;
        if (pulseCooldown > 0) {
            pulseCooldown--;
        }
        if (pulse >= 0) {
            pulse += 7 + p.level(Catalog.T_SCANNER) * 1.5f;
            float range = 260 + p.level(Catalog.T_SCANNER) * 80;
            if (pulse > range) {
                pulse = -1;
            } else {
                int until = (int) g.ticks + 60 * 25;
                for (Thing t : things) {
                    if (Math.abs(terrain.dx(ship.x, t.x)) < pulse && Math.abs(t.y - ship.y) < pulse) {
                        t.scannedUntil = until;
                    }
                }
                for (Creature c : creatures) {
                    if (Math.abs(terrain.dx(ship.x, c.x)) < pulse && Math.abs(c.y - ship.y) < pulse) {
                        c.scannedUntil = until;
                    }
                }
            }
        }
        if (in.scan) {
            scanHeld++;
            if (scanHeld == 14) {
                visor = true;
                analysis = 0;
                visorTarget = null;
                g.sound.sfx(Sound.TARGETING, 10);
            }
        } else {
            if (scanHeld > 0 && scanHeld < 14 && pulseCooldown == 0) {
                pulse = 0;
                pulseCooldown = 150 - p.level(Catalog.T_SCANNER) * 25;
                g.sound.sfx(Sound.ENERGY_ZAP, 10);
            }
            scanHeld = 0;
            visor = false;
            visorTarget = null;
            analysis = 0;
        }
        if (visor) {
            Object target = visorPick(g, in);
            if (target != visorTarget) {
                visorTarget = target;
                analysis = 0;
            }
            Species s = speciesOf(visorTarget);
            if (s != null && !p.discovered.contains(s.id)) {
                analysis += (1 + 0.35f * p.level(Catalog.T_SCANNER)) / 55f;
                if (g.ticks % 8 == 0) {
                    g.sound.sfx(Sound.PLINK, 7);
                }
                if (analysis >= 1) {
                    discover(g, s);
                }
            }
        }
    }

    private Object visorPick(Game g, Controls in) {
        float ax = in.mouseAim || in.mouseMoved ? camX - g.width / 2f + in.mouseX : ship.x + ship.facing * 50;
        float ay = in.mouseAim || in.mouseMoved ? camY + in.mouseY : ship.y;
        Object best = null;
        float bestD = 170;
        for (Creature c : creatures) {
            if (!c.alive) {
                continue;
            }
            float d = (float) Math.hypot(terrain.dx(ax, c.x), c.centreY() - ay) - (g.player.discovered.contains(c.species.id) ? 0 : 40);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        for (Thing t : things) {
            if (!t.discoverable()) {
                continue;
            }
            float d = (float) Math.hypot(terrain.dx(ax, t.x), t.centreY() - ay) - (g.player.discovered.contains(t.species.id) ? 0 : 30);
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return best;
    }

    public Species speciesOf(Object o) {
        if (o instanceof Creature c) {
            return c.species;
        }
        if (o instanceof Thing t) {
            return t.species;
        }
        return null;
    }

    private void discover(Game g, Species s) {
        Player p = g.player;
        if (!p.discovered.add(s.id)) {
            return;
        }
        p.statSpecies++;
        int base = switch (s.kind) {
            case Species.FAUNA -> 220 + Math.round(s.scale * 80);
            case Species.FLORA -> 120;
            default -> 90;
        };
        int reward = Math.round(base * (1 + 0.4f * p.level(Catalog.T_SURVEY)));
        p.rings += reward;
        p.statRingsEarned += reward;
        int shards = s.kind == Species.FAUNA ? 6 : 3;
        p.shards += shards;
        ship.laughTicks = 60;
        g.sound.sfx(Sound.REGISTER);
        g.banner("NEW DISCOVERY", s.name + "   +" + reward + " RINGS  +" + shards + " SHARDS", 0xFF60FFB0);
        // Every species on the planet: a completion bonus.
        boolean all = true;
        for (Species other : planet.allSpecies()) {
            if (!p.discovered.contains(other.id)) {
                all = false;
                break;
            }
        }
        if (all) {
            p.shards += 120;
            p.rings += 1500;
            p.statRingsEarned += 1500;
            g.banner("PLANET 100% DISCOVERED", "+1500 RINGS  +120 SHARDS", 0xFFFFD040);
            g.sound.sfx(Sound.PERFECT);
        }
    }

    // ---------------------------------------------------------------- vitals

    private void updateVitals(Game g) {
        Player p = g.player;
        sheltered = false;
        for (int y = (int) ship.y - 40; y > ship.y - 200; y -= 8) {
            if (terrain.solid((int) ship.x, y)) {
                sheltered = true;
                break;
            }
        }
        p.life -= 100f / (60 * 60 * 6);
        if (planet.spec.biome.hazardous()) {
            float rate = 100f / (60 * 60 * 3.5f) * (1 + weather.storm * 3.5f) * (1 - 0.2f * p.level(Catalog.T_HAZARD));
            if (sheltered) {
                rate = 0;
                p.hazard = Math.min(p.maxHazard(), p.hazard + 0.03f);
            }
            hazardDrain = rate;
            p.hazard -= rate;
        } else {
            hazardDrain = 0;
            p.hazard = Math.min(p.maxHazard(), p.hazard + 0.05f);
        }
        if (p.level(Catalog.T_AUTO) > 0) {
            if (p.life < p.maxLife() * 0.25f && Vitals.canRecharge(p, Vitals.LIFE)) {
                g.toast("AUTO: " + Vitals.recharge(p, Vitals.LIFE), 0xFF80FFFF);
            }
            if (p.hazard < p.maxHazard() * 0.25f && Vitals.canRecharge(p, Vitals.HAZARD)) {
                g.toast("AUTO: " + Vitals.recharge(p, Vitals.HAZARD), 0xFF80FFFF);
            }
        }
        if (p.life <= 0) {
            p.life = 0;
            hurtHull(g, 0.05f);
            if (g.ticks % 120 == 0) {
                g.toast("LIFE SUPPORT DEPLETED!", 0xFFFF4040);
                g.sound.sfx(Sound.AIR_DING);
            }
        } else if (p.life < p.maxLife() * 0.2f && g.ticks % 300 == 0) {
            g.toast("LIFE SUPPORT LOW - PRESS 1", 0xFFFFA040);
            g.sound.sfx(Sound.AIR_DING);
        }
        if (p.hazard <= 0) {
            p.hazard = 0;
            hurtHull(g, 0.06f);
            if (g.ticks % 120 == 0) {
                g.toast("HAZARD PROTECTION FAILED!", 0xFFFF4040);
                g.sound.sfx(Sound.AIR_DING);
            }
        } else if (p.hazard < 25 && hazardDrain > 0 && g.ticks % 300 == 0) {
            g.toast("HAZARD PROTECTION LOW - PRESS 2", 0xFFFFA040);
        }
        if (shieldDelay > 0) {
            shieldDelay--;
        } else if (p.shield < p.maxShield()) {
            p.shield = Math.min(p.maxShield(), p.shield + 0.12f + 0.06f * p.level(Catalog.T_SHIELD));
        }
        // Storm crystals grow in storms.
        if (weather.storm > 0.7f && rng.chance(0.004)) {
            float x = ship.x + rng.range(-300f, 300f);
            int floor = terrain.topFloor(x);
            if (floor > 0) {
                Planet.Placement pl = new Planet.Placement(Planet.P_CRYSTAL, terrain.wrapX(x), floor, 3,
                        planet.key + "#storm" + g.ticks);
                Thing t = new Thing(pl, null, planet.crystals[3]);
                t.setYield(Catalog.DIHYDROGEN, 5, 60);
                things.add(t);
                particles.burst(t.x, floor - 10, 10, 2, 0xFFFFFFFF, 0xFF80E0FF, 0.05f, 30, g.ticks);
            }
        }
        // Being hurt shows on the pod: smoke from a damaged hull.
        if (p.hull < p.maxHull() * 0.35f && g.ticks % 5 == 0) {
            particles.add(ship.x + rng.range(-10f, 10f), ship.y - 10, rng.range(-0.3f, 0.3f), -0.8f, -0.005f, 3,
                    0xA0404040, 50, false);
        }
    }

    private void hurtHull(Game g, float amount) {
        Player p = g.player;
        p.hull -= amount;
        if (p.hull <= 0 && phase == PLAY) {
            die(g);
        }
    }

    /** A hit on the pod from anything; returns whether it landed. */
    public boolean hurtShip(float damage, float fromX) {
        Game g = game;
        Player p = g.player;
        if (phase != PLAY || ship.invulnerable > 0 || invincible > 0) {
            return false;
        }
        shieldDelay = 180;
        if (p.shield > 0) {
            float absorbed = Math.min(p.shield, damage);
            p.shield -= absorbed;
            damage -= absorbed;
            g.sound.sfx(Sound.SHIELD, 6);
            particles.burst(ship.x, ship.y, 8, 2, 0xFF60FFFF, 0xFFFFFFFF, 0, 16, g.ticks);
        }
        if (damage > 0) {
            p.hull -= damage;
            ship.hurtTicks = 30;
            g.sound.sfx(Sound.BOSS_HIT, 6);
            g.shake = 4;
        }
        ship.invulnerable = 60;
        ship.vx += Math.signum(terrain.dx(fromX, ship.x)) * 3;
        ship.vy -= 2;
        if (p.hull <= 0) {
            die(g);
        }
        return true;
    }

    public void creatureBite(Creature c) {
        hurtShip(4 + 3 * c.species.scale, c.x);
    }

    private void die(Game g) {
        Player p = g.player;
        p.hull = 0;
        phase = DEAD;
        phaseTicks = 0;
        ship.vy = -3;
        p.statDeaths++;
        g.sound.sfx(Sound.EXPLODE);
        g.sound.fadeOut();
        g.flash(0xFFFFFFFF, 8);
        // Cargo stays behind in the wreck.
        p.graveSystem = planet.system.id;
        p.gravePlanet = planet.spec.index;
        p.graveX = ship.x;
        int floor = terrain.groundBelow((int) ship.x, (int) ship.y, 600);
        p.graveY = floor < 0 ? ship.y : floor;
        p.graveCargo = p.cargo.encode();
        for (int i = 0; i < p.cargo.slots(); i++) {
            p.cargo.set(i, 0, 0);
        }
    }

    private void respawn(Game g) {
        Player p = g.player;
        p.refill();
        p.hull = p.maxHull() * 0.6f;
        wanted = 0;
        for (Hero h : heroes) {
            h.alive = false;
        }
        heroes.clear();
        float x = p.beaconPlanet == planet.spec.index && p.systemId == planet.system.id && p.beaconX > 0 ? p.beaconX
                : landingX(p);
        placeOnSurface(x);
        if (graveThing != null) {
            things.remove(graveThing);
        }
        Planet.Placement grave = new Planet.Placement(Planet.P_WRECK, p.graveX, p.graveY, 0, "grave");
        graveThing = new Thing(grave, null, null);
        graveThing.setYield(0, 0, 1);
        things.add(graveThing);
        phase = PLAY;
        phaseTicks = 0;
        ship.invulnerable = 120;
        g.sound.resetMusic();
        g.sound.music(planet.spec.biome.music());
        g.banner("EGG MOBILE REBUILT", "Your cargo waits in the wreck", 0xFFFF8060);
        g.save();
    }

    // ---------------------------------------------------------------- sentinels

    public void addWanted(Game g, float amount) {
        float mult = switch (planet.spec.sentinels) {
            case 0 -> 0.35f;
            case 1 -> 1f;
            case 2 -> 1.6f;
            default -> 2.6f;
        };
        mult *= 1 - 0.3f * g.player.level(Catalog.T_CLOAK);
        int before = (int) wanted;
        wanted = Math.min(5.2f, wanted + amount * mult);
        if ((int) wanted > before) {
            wantedFlash = 90;
            unseenTicks = 0;
            g.sound.sfx(Sound.SIREN, 40);
            g.toast("WANTED LEVEL " + (int) wanted, 0xFFFF4040);
            if ((int) wanted >= 3 && before < 3) {
                g.banner("SONIC IS COMING!", "Repel the heroes or escape", 0xFF4080FF);
            }
        }
    }

    private void updateWanted(Game g) {
        if (wantedFlash > 0) {
            wantedFlash--;
        }
        int level = (int) wanted;
        boolean seen = false;
        for (Hero h : heroes) {
            if (h.alive && Math.abs(terrain.dx(h.x, ship.x)) < 280 && !sheltered) {
                seen = true;
            }
        }
        if (level == 0) {
            wanted = Math.max(0, wanted - 0.0008f);
            return;
        }
        unseenTicks = seen ? 0 : unseenTicks + 1;
        if (unseenTicks > 60 * 12) {
            wanted -= 0.006f;
            if ((int) wanted < level) {
                if ((int) wanted == 0) {
                    g.toast("WANTED LEVEL CLEARED", 0xFF60FF60);
                    for (Hero h : heroes) {
                        h.hp = 0;
                    }
                    g.sound.music(planet.spec.biome.music());
                } else {
                    g.toast("WANTED LEVEL " + (int) wanted, 0xFFFFC040);
                }
            }
        }
        heroWave--;
        if (heroWave <= 0) {
            heroWave = 60 * 7;
            spawnHeroes(g, level);
        }
    }

    private void spawnHeroes(Game g, int level) {
        int[] want = new int[5];
        switch (Math.min(5, level)) {
            case 1 -> want[Hero.FLICKY] = 2;
            case 2 -> {
                want[Hero.FLICKY] = 3;
                want[Hero.TAILS] = 1;
            }
            case 3 -> {
                want[Hero.FLICKY] = 2;
                want[Hero.TAILS] = 1;
                want[Hero.SONIC] = 1;
            }
            case 4 -> {
                want[Hero.TAILS] = 1;
                want[Hero.SONIC] = 1;
                want[Hero.KNUCKLES] = 1;
            }
            default -> {
                want[Hero.SUPER] = 1;
                want[Hero.KNUCKLES] = 1;
                want[Hero.FLICKY] = 2;
            }
        }
        int[] have = new int[5];
        for (Hero h : heroes) {
            if (h.alive) {
                have[h.kind]++;
            }
        }
        for (int kind = 0; kind < 5; kind++) {
            for (int i = have[kind]; i < want[kind]; i++) {
                float side = rng.chance(0.5) ? 1 : -1;
                float x = terrain.wrapX(ship.x + side * (g.width / 2f + 40 + rng.nextInt(60)));
                float y;
                if (kind == Hero.FLICKY || kind == Hero.TAILS) {
                    y = ship.y - 80 - rng.nextInt(40);
                } else {
                    int floor = terrain.groundBelow((int) x, (int) ship.y - 100, 400);
                    y = floor < 0 ? ship.y : floor;
                }
                Hero h = new Hero(kind, x, y, Rng.hash(g.ticks, kind, i));
                heroes.add(h);
                if (kind == Hero.SONIC || kind == Hero.SUPER) {
                    g.sound.music(kind == Hero.SUPER ? Sound.M_INVINCIBLE : Sound.M_MINIBOSS);
                } else if (kind == Hero.KNUCKLES) {
                    g.sound.music(Sound.M_KNUCKLES);
                }
            }
        }
    }

    public void heroRepelled(Hero h) {
        Game g = game;
        Player p = g.player;
        p.statSonicRepelled++;
        wanted = Math.max(0, wanted - (h.kind == Hero.FLICKY ? 0.15f : 0.55f));
        int shards = switch (h.kind) {
            case Hero.FLICKY -> 3;
            case Hero.TAILS -> 15;
            case Hero.SONIC -> 30;
            case Hero.KNUCKLES -> 35;
            default -> 90;
        };
        scatterShards(h.x, h.centreY(), shards);
        if (h.kind != Hero.FLICKY) {
            g.sound.sfx(Sound.RING_LOSS);
            for (int i = 0; i < 16; i++) {
                double a = Math.PI * 2 * i / 16;
                Pickup pk = new Pickup(Pickup.RINGS, 0, 5, h.x, h.centreY(), (float) Math.cos(a) * 4,
                        (float) Math.sin(a) * 4 - 2);
                pk.scattered = true;
                pickups.add(pk);
            }
            ship.laughTicks = 100;
            g.toast(h.name().toUpperCase() + " REPELLED!", 0xFF80FF80);
        } else {
            explosion(h.x, h.centreY(), false);
            freeAnimal(h.x, h.centreY());
        }
        g.sound.sfx(Sound.BOSS_HIT);
    }

    public void spawnRingBomb(float x, float y, float vx, float vy) {
        shots.add(new Shot(Shot.RING_BOMB, true, x, y, vx, vy, 6, 160));
        game.sound.sfx(Sound.PROJECTILE, 10);
    }

    public boolean shipHidden() {
        return sheltered;
    }

    // ---------------------------------------------------------------- entities

    private void updateEntities(Game g) {
        float range = g.width;
        for (Creature c : creatures) {
            if (!c.alive) {
                continue;
            }
            if (Math.abs(terrain.dx(camX, c.x)) > range + 200) {
                continue;
            }
            c.update(this, terrain, ship.x, ship.y);
        }
        for (Hero h : heroes) {
            h.update(this, terrain, ship.x, ship.y);
        }
        heroes.removeIf(h -> !h.alive);
        for (Thing t : things) {
            if (t.hit > 0) {
                t.hit--;
            }
        }
    }

    private void updatePickups(Game g) {
        Player p = g.player;
        float magnet = 60 + 50 * p.level(Catalog.T_MAGNET);
        for (Pickup pk : pickups) {
            pk.age++;
            float dx = terrain.dx(pk.x, ship.x);
            float dy = ship.y - pk.y;
            float dist = (float) Math.hypot(dx, dy);
            boolean pulled = pk.age > (pk.scattered ? 20 : 14) && (dist < magnet || !pk.scattered && pk.age > 30);
            if (pulled && phase == PLAY) {
                float sp = Math.min(9, 2 + pk.age * 0.08f);
                pk.vx += (dx / Math.max(1, dist) * sp - pk.vx) * 0.25f;
                pk.vy += (dy / Math.max(1, dist) * sp - pk.vy) * 0.25f;
            } else {
                pk.vy += 0.18f;
                pk.vx *= 0.98f;
                int floor = terrain.groundBelow((int) pk.x, (int) pk.y, (int) Math.max(4, pk.vy + 4));
                if (pk.vy > 0 && floor >= 0) {
                    pk.y = floor - 1;
                    pk.vy = -pk.vy * 0.5f;
                    pk.vx *= 0.8f;
                }
            }
            pk.x = terrain.wrapX(pk.x + pk.vx);
            pk.y += pk.vy;
            if (dist < 22 && phase == PLAY) {
                collect(g, pk);
            }
            if (pk.scattered && pk.age > 60 * 8) {
                pk.alive = false;
            }
        }
        pickups.removeIf(pk -> !pk.alive);
    }

    private void collect(Game g, Pickup pk) {
        Player p = g.player;
        switch (pk.type) {
            case Pickup.RINGS -> {
                p.rings += pk.amount;
                p.statRingsEarned += pk.amount;
                g.sound.sfx((g.ticks & 1) == 0 ? Sound.RING : Sound.RING_LEFT, 2);
                pk.alive = false;
            }
            case Pickup.SHARDS -> {
                p.shards += pk.amount;
                g.sound.sfx(Sound.BLUE_SPHERE, 3);
                pk.alive = false;
            }
            default -> {
                int left = p.cargo.add(pk.item, pk.amount);
                int got = pk.amount - left;
                if (got > 0) {
                    g.toast("+" + got + " " + g.catalog.name(pk.item), g.catalog.item(pk.item).colour());
                    g.sound.sfx(Sound.PLINK, 4);
                }
                if (left > 0) {
                    pk.amount = left;
                    if (g.ticks % 90 == 0) {
                        g.toast("CARGO FULL", 0xFFFF5050);
                    }
                    pk.vx = -pk.vx;
                    pk.age = 0;
                    pk.scattered = true;
                } else {
                    pk.alive = false;
                }
            }
        }
    }

    /** Spawns loot pickups for {@code count} of {@code item}. */
    public void gain(Game g, int item, int count, float x, float y) {
        if (count <= 0 || g.catalog.item(item) == null) {
            return;
        }
        int pieces = Math.min(6, Math.max(1, count / 4));
        int each = count / pieces;
        int extra = count - each * pieces;
        for (int i = 0; i < pieces; i++) {
            double a = -Math.PI / 2 + rng.range(-1f, 1f);
            float sp = rng.range(1.5f, 3.5f);
            pickups.add(new Pickup(Pickup.ITEM, item, each + (i == 0 ? extra : 0), x, y, (float) Math.cos(a) * sp,
                    (float) Math.sin(a) * sp));
        }
    }

    private void scatterShards(float x, float y, int amount) {
        int pieces = Math.min(8, Math.max(1, amount / 5));
        for (int i = 0; i < pieces; i++) {
            double a = -Math.PI / 2 + rng.range(-1.2f, 1.2f);
            float sp = rng.range(2f, 4f);
            pickups.add(new Pickup(Pickup.SHARDS, 0, amount / pieces + (i == 0 ? amount % pieces : 0), x, y,
                    (float) Math.cos(a) * sp, (float) Math.sin(a) * sp));
        }
    }

    private void updateShots(Game g) {
        for (Shot s : shots) {
            s.life--;
            if (s.type == Shot.RING_BOMB) {
                s.vy += 0.12f;
            }
            s.x = terrain.wrapX(s.x + s.vx);
            s.y += s.vy;
            boolean hitTerrain = terrain.solid((int) s.x, (int) s.y);
            if (s.hostile) {
                if (Math.abs(terrain.dx(s.x, ship.x)) < ship.halfWidth && s.y > ship.y + ship.top && s.y < ship.y + ship.bottom) {
                    hurtShip(s.damage, s.x);
                    s.life = 0;
                }
            } else {
                Object hit = entityAt(s.x, s.y);
                if (hit instanceof Creature c) {
                    damageCreature(g, c, s.damage);
                    s.life = 0;
                } else if (hit instanceof Hero h) {
                    damageHero(g, h, s.damage);
                    s.life = 0;
                }
            }
            if (hitTerrain || s.life <= 0) {
                s.alive = false;
                if (s.type == Shot.RING_BOMB) {
                    explosion(s.x, s.y, false);
                    if (Math.hypot(terrain.dx(s.x, ship.x), s.y - ship.y) < 34) {
                        hurtShip(s.damage, s.x);
                    }
                } else {
                    particles.burst(s.x, s.y, 5, 1.5f, 0xFFFFE080, 0xFFFFFFFF, 0, 10, g.ticks);
                }
            }
        }
        shots.removeIf(s -> !s.alive);
    }

    private void explosion(float x, float y, boolean big) {
        game.sound.sfx(big ? Sound.MISSILE_EXPLODE : Sound.BREAK, 5);
        explosions.add(new float[] {x, y, 0, big ? 1 : 0});
        particles.burst(x, y, big ? 18 : 10, big ? 3 : 2, 0xFFFFA020, 0xFFFFFF80, 0.05f, 24, (long) (x * 31 + y));
    }

    private final List<float[]> explosions = new ArrayList<>();

    private void freeAnimal(float x, float y) {
        List<Species> animals = new ArrayList<>();
        for (Species s : planet.fauna) {
            if (s.body.animal()) {
                animals.add(s);
            }
        }
        if (animals.isEmpty()) {
            particles.add(x, y, 0, -1.5f, 0, 3, 0xFFFFFFFF, 30, true);
            return;
        }
        Species s = animals.get(rng.nextInt(animals.size()));
        Creature c = new Creature(s, "freed" + game.ticks, x, y, game.ticks);
        c.vy = -4;
        c.state = Creature.FLEE;
        c.stateTicks = 200;
        creatures.add(c);
    }

    // ---------------------------------------------------------------- interactions

    private boolean touching(Thing t) {
        return Math.abs(terrain.dx(t.x, ship.x)) < t.halfWidth() + ship.halfWidth - 4
                && ship.y + ship.bottom > t.y - t.height() && ship.y + ship.top < t.y;
    }

    private void updateInteractions(Game g) {
        Player p = g.player;
        for (Thing t : things) {
            if (Math.abs(terrain.dx(t.x, ship.x)) > 140) {
                continue;
            }
            if (t == graveThing && touching(t)) {
                Player pl = g.player;
                eggsky.game.Inventory dropped = new eggsky.game.Inventory(g.catalog, pl.slots());
                dropped.decode(pl.graveCargo);
                int recovered = 0;
                for (int i = 0; i < dropped.slots(); i++) {
                    if (dropped.itemAt(i) > 0) {
                        recovered += dropped.countAt(i) - pl.cargo.add(dropped.itemAt(i), dropped.countAt(i));
                    }
                }
                pl.graveCargo = "";
                pl.graveSystem = Long.MIN_VALUE;
                things.remove(t);
                graveThing = null;
                g.toast("CARGO RECOVERED (" + recovered + ")", 0xFF80FF80);
                g.sound.sfx(Sound.GRAB);
                return;
            }
            switch (t.kind) {
                case Planet.P_RINGS -> {
                    for (int i = 0; i < t.ringCount; i++) {
                        if ((t.ringMask & (1 << i)) == 0) {
                            continue;
                        }
                        float rx = t.x + (i - (t.ringCount - 1) / 2f) * 16;
                        float ry = t.y - 26 - Math.abs(i - (t.ringCount - 1) / 2f) * 3;
                        if (Math.abs(terrain.dx(rx, ship.x)) < ship.halfWidth + 6 && ry > ship.y + ship.top - 6
                                && ry < ship.y + ship.bottom + 6) {
                            t.ringMask &= ~(1 << i);
                            p.rings += 5;
                            p.statRingsEarned += 5;
                            g.sound.sfx((i & 1) == 0 ? Sound.RING : Sound.RING_LEFT, 2);
                            particles.add(rx, ry, 0, -0.5f, 0, 3, 0xFFFFFFA0, 12, true);
                            if (t.ringMask == 0) {
                                p.looted.add(t.id);
                                t.alive = false;
                            }
                        }
                    }
                }
                case Planet.P_STARPOST -> {
                    if (!t.spent && touching(t)) {
                        t.spent = true;
                        p.beaconPlanet = planet.spec.index;
                        p.beaconX = t.x;
                        p.beaconY = t.y;
                        p.life = Math.min(p.maxLife(), p.life + 25);
                        g.sound.sfx(Sound.STARPOST);
                        g.toast("PROGRESS SAVED", 0xFF60C0FF);
                        g.save();
                    }
                }
                case Planet.P_CAPSULE -> {
                    if (!t.spent && touching(t)) {
                        openCapsule(g, t);
                    }
                }
                case Planet.P_RUINS -> {
                    if (!t.spent && touching(t)) {
                        readRuins(g, t);
                    }
                }
                case Planet.P_GIANT_RING -> {
                    if (!t.spent && touching(t)) {
                        giantRing(g, t);
                    }
                }
                case Planet.P_SHRINE -> {
                    if (!t.spent && touching(t)) {
                        takeEmerald(g, t);
                    }
                }
                default -> {
                }
            }
        }
        things.removeIf(t -> !t.alive && t.kind != Planet.P_MONITOR && t.kind != Planet.P_WRECK && t != graveThing);
        explosions.removeIf(e -> ++e[2] > 24);
    }

    private void openMonitor(Game g, Thing t) {
        Player p = g.player;
        t.alive = true;
        t.spent = true;
        p.looted.add(t.id);
        g.sound.sfx(Sound.BREAK);
        explosion(t.x, t.y - 14, false);
        switch (t.variant) {
            case 0 -> {
                for (int i = 0; i < 10; i++) {
                    pickups.add(new Pickup(Pickup.RINGS, 0, 10, t.x, t.y - 16, rng.range(-2f, 2f), rng.range(-4f, -1.5f)));
                }
                g.toast("SUPER RING!", 0xFFFFE040);
            }
            case 1, 2 -> {
                p.shield = Math.max(p.shield, Math.max(p.maxShield(), 40));
                g.sound.sfx(t.variant == 1 ? Sound.FIRE_SHIELD : Sound.LIGHTNING_SHIELD);
                g.toast(t.variant == 1 ? "FIRE BARRIER!" : "THUNDER BARRIER!", 0xFF80E0FF);
            }
            case 3 -> {
                invincible = 60 * 15;
                g.sound.music(Sound.M_INVINCIBLE);
                g.toast("INVINCIBLE!", 0xFFFFFFFF);
            }
            case 4 -> {
                g.toast("IT'S A TRAP!", 0xFF4080FF);
                addWanted(g, Math.max(0, 3.05f - wanted) / Math.max(0.3f, planet.spec.sentinels == 0 ? 0.35f : 1f));
                heroWave = 0;
            }
            default -> {
                scatterShards(t.x, t.y - 16, 30 + rng.nextInt(40));
                ship.laughTicks = 80;
                g.toast("EGGMAN MONITOR! CHAOS SHARDS!", 0xFFFF8080);
            }
        }
    }

    private void openCapsule(Game g, Thing t) {
        Player p = g.player;
        t.spent = true;
        p.looted.add(t.id);
        g.sound.sfx(Sound.SWITCH);
        g.sound.sfx(Sound.DOOR_OPEN);
        for (int i = 0; i < 6; i++) {
            freeAnimal(t.x + rng.range(-16f, 16f), t.y - 30);
        }
        if (p.bonusSlots < 24) {
            p.bonusSlots++;
            p.cargo.resize(p.slots());
            g.banner("CARGO POD EXPANDED", "Egg Mobile cargo: " + p.slots() + " slots", 0xFFFFC040);
        } else {
            p.shards += 100;
            g.banner("CAPSULE SALVAGED", "+100 CHAOS SHARDS", 0xFFFFC040);
        }
        ship.laughTicks = 90;
    }

    private void readRuins(Game g, Thing t) {
        Player p = g.player;
        t.spent = true;
        p.looted.add(t.id);
        g.sound.sfx(Sound.GHOST_APPEAR);
        Rng r = new Rng(t.seed);
        String lore = pickLore(r);
        List<String> lines = new ArrayList<>(eggsky.ui.Ui.wrap(lore, 50));
        lines.add("");
        if (p.emeraldCount() < 7 && (p.shrineSystem == Long.MIN_VALUE || r.chance(0.0))) {
            StarSystem target = revealShrine(g, r);
            lines.add("THE GLYPHS PULSE GREEN: A CHAOS EMERALD SIGNAL!");
            lines.add("Signal: " + target.name + ", planet " + (p.shrinePlanet + 1));
            g.sound.sfx(Sound.SUPER_EMERALD);
        } else if (r.chance(0.5)) {
            gain(g, Catalog.ECHIDNA_RELIC, 1, t.x, t.y - 40);
            lines.add("A relic falls loose from the stone.");
        } else {
            scatterShards(t.x, t.y - 40, 40 + r.nextInt(30));
            lines.add("Chaos energy spills from the carvings.");
        }
        openDialog("ECHIDNA RUINS", lines, new String[] {"CONTINUE"}, new Runnable[] {() -> { }});
    }

    private static String pickLore(Rng r) {
        String[] lore = {
                "The ancients wrote of seven stones that bind the stars. Where the galaxy turns, their master waits.",
                "Here the guardians sealed the Emeralds away from a man with a magnificent moustache. It did not work.",
                "Beware the blue wind. It moves faster than thought and hates machines.",
                "The heart of the galaxy hungers for seven lights. Bring them, and the stars will kneel.",
                "Our people flew between these worlds on rings of gold. The rings remember the way to the centre.",
                "Chaos is power, power enriched by the heart. The servers are the seven Chaos.",
                "A tablet of tallies: 7 emeralds, 1 master, 0 hedgehogs welcome.",
                "Those who dig too greedily will hear the drums of the guardians.",
        };
        return lore[r.nextInt(lore.length)];
    }

    /** Picks the next emerald shrine: a system toward the core, within reach. */
    private StarSystem revealShrine(Game g, Rng r) {
        Player p = g.player;
        StarSystem here = g.system();
        float range = p.warpRange() * 1.6f;
        StarSystem best = here;
        float bestScore = Float.MAX_VALUE;
        for (StarSystem s : g.galaxy.near(here.x, here.y, range)) {
            if (s.planetCount == 0 || s.starClass > StarSystem.BLUE || s.id == here.id) {
                continue;
            }
            float score = s.distanceToCore() + r.nextFloat() * 200;
            if (score < bestScore) {
                bestScore = score;
                best = s;
            }
        }
        p.shrineSystem = best.id;
        p.shrinePlanet = r.nextInt(Math.max(1, best.planetCount));
        return best;
    }

    private void giantRing(Game g, Thing t) {
        g.sound.sfx(Sound.BIG_RING);
        openDialog("GIANT RING", List.of("The ring hums with stolen chaos energy.", "",
                        "Ride it toward the galactic core?", "(A free jump, further than any hyperdrive.)"),
                new String[] {"ENTER THE RING", "NOT YET"},
                new Runnable[] {() -> {
                    t.spent = true;
                    g.player.looted.add(t.id);
                    g.setMode(new eggsky.space.WarpMode(ringDestination(g), true));
                }, () -> { }});
    }

    private StarSystem ringDestination(Game g) {
        StarSystem here = g.system();
        float reach = g.player.warpRange() * 2.2f;
        StarSystem best = null;
        float bestCore = here.distanceToCore();
        for (StarSystem s : g.galaxy.near(here.x, here.y, reach)) {
            if (s.planetCount > 0 && s.starClass <= StarSystem.BLUE && s.distanceToCore() < bestCore) {
                bestCore = s.distanceToCore();
                best = s;
            }
        }
        return best == null ? here : best;
    }

    private void takeEmerald(Game g, Thing t) {
        Player p = g.player;
        t.spent = true;
        p.looted.add(t.id);
        int n = p.emeraldCount();
        p.emeralds |= 1 << n;
        p.shrineSystem = Long.MIN_VALUE;
        p.shrinePlanet = -1;
        p.shards += 250;
        g.sound.music(Sound.M_EMERALD);
        g.flash(0xFFFFFFFF, 12);
        ship.laughTicks = 200;
        if (p.emeraldCount() >= 7) {
            g.banner("ALL SEVEN CHAOS EMERALDS!", "The galactic core is open to you", emeraldColour(n));
        } else {
            g.banner("CHAOS EMERALD " + (n + 1) + " OF 7", "Find more echidna ruins for the next signal",
                    emeraldColour(n));
        }
        g.save();
    }

    public int emeraldColour(int i) {
        return switch (i % 7) {
            case 0 -> 0xFF40E060;
            case 1 -> 0xFFFFE040;
            case 2 -> 0xFF4080FF;
            case 3 -> 0xFFFF70C0;
            case 4 -> 0xFF40F0F0;
            case 5 -> 0xFFFF4040;
            default -> 0xFFE0E0F0;
        };
    }

    public SceneSprite emeraldSprite(int i) {
        int k = Math.max(0, Math.min(6, i));
        if (emeralds[k] == null) {
            emeralds[k] = PixelArt.emerald(emeraldColour(k));
        }
        return emeralds[k];
    }

    public SceneSprite wreckSprite() {
        return game.art.frame("wreck", 0);
    }

    // ---------------------------------------------------------------- dialogs and hotkeys

    public void openDialog(String title, List<String> lines, String[] options, Runnable[] actions) {
        dialogTitle = title;
        dialogLines = lines;
        dialogOptions = options;
        dialogActions = actions;
        dialogChoice = 0;
    }

    private void updateDialog(Game g) {
        Controls in = g.in;
        if (in.upRepeat || in.leftRepeat) {
            dialogChoice = Math.max(0, dialogChoice - 1);
            g.sound.sfx(Sound.SWITCH, 3);
        }
        if (in.downRepeat || in.rightRepeat) {
            dialogChoice = Math.min(dialogOptions.length - 1, dialogChoice + 1);
            g.sound.sfx(Sound.SWITCH, 3);
        }
        if (in.confirmPressed) {
            Runnable action = dialogActions[dialogChoice];
            dialogTitle = null;
            action.run();
        } else if (in.backPressed) {
            dialogTitle = null;
        }
    }

    private void hotkeys(Game g) {
        Player p = g.player;
        int[] keys = {SceneKeys.DIGIT_1, SceneKeys.DIGIT_2, SceneKeys.DIGIT_3, SceneKeys.DIGIT_4};
        int[] which = {Vitals.LIFE, Vitals.HAZARD, Vitals.LAUNCH, Vitals.HULL};
        for (int i = 0; i < keys.length; i++) {
            if (g.ctx.keyPressed(keys[i])) {
                String msg = Vitals.recharge(p, which[i]);
                if (msg != null) {
                    g.toast(Vitals.name(which[i]).toUpperCase() + " " + msg, 0xFF80FFFF);
                    g.sound.sfx(Sound.SHIELD, 5);
                } else {
                    g.toast("NOTHING TO RECHARGE " + Vitals.name(which[i]).toUpperCase(), 0xFFFF8080);
                    g.sound.sfx(Sound.ERROR, 10);
                }
            }
        }
    }

    // ---------------------------------------------------------------- tutorial

    private void tutorial(Game g) {
        Player p = g.player;
        int step = p.tutorial;
        switch (step) {
            case 0 -> {
                if (p.cargo.count(Catalog.FERRITE) >= 30) {
                    p.tutorial = 1;
                }
            }
            case 1 -> {
                if (p.cargo.count(Catalog.DIHYDROGEN) >= 40 || p.cargo.count(Catalog.DIHYDROGEN_JELLY) > 0) {
                    p.tutorial = 2;
                }
            }
            case 2 -> {
                if (p.cargo.count(Catalog.DIHYDROGEN_JELLY) >= 1 && p.cargo.count(Catalog.PURE_FERRITE) >= 20
                        || p.cargo.count(Catalog.EGG_FUEL) > 0 || p.launchFuel >= 25) {
                    p.tutorial = 3;
                }
            }
            case 3 -> {
                if (p.cargo.count(Catalog.EGG_FUEL) > 0 || p.launchFuel >= 25) {
                    p.tutorial = 4;
                }
            }
            default -> {
            }
        }
        if (p.tutorial != tutorialShown && p.tutorial <= 4) {
            tutorialShown = p.tutorial;
            g.sound.sfx(Sound.TRANSPORTER, 10);
        }
    }

    /** The objective text for the HUD, or null. */
    public String objective() {
        return switch (game.player.tutorial) {
            case 0 -> "Mine FERRITE from rocks: hold SPACE / Z  (30)";
            case 1 -> "Laser the blue DI-HYDROGEN crystals  (40)";
            case 2 -> "ENTER: menu > REFINE Di-hydrogen and Ferrite";
            case 3 -> "Menu > CRAFT: build EGG FUEL";
            case 4 -> "Press 3 to fuel thrusters, hold UP + C to launch";
            default -> game.player.emeraldCount() < 7 && game.player.shrineSystem != Long.MIN_VALUE
                    ? "Chaos Emerald signal: " + shrineName() : null;
        };
    }

    private String shrineName() {
        StarSystem s = game.galaxy.system(game.player.shrineSystem);
        if (s == null) {
            return "?";
        }
        boolean here = s.id == planet.system.id;
        return here ? "planet " + (game.player.shrinePlanet + 1) + " of this system" : s.name;
    }

    // ---------------------------------------------------------------- camera

    private void updateCamera(Game g) {
        float lead = ship.vx * 22;
        float targetX = ship.x + lead;
        float dx = terrain.dx(camX, targetX);
        camX = terrain.wrapX(camX + dx * 0.12f);
        float targetY = ship.y - g.height * 0.52f + ship.vy * 6;
        camY += (targetY - camY) * (phase == LAUNCH || phase == DESCEND ? 0.3f : 0.1f);
        camY = Math.max(-420, Math.min(terrain.height() - g.height + 40, camY));
    }

    // ---------------------------------------------------------------- creature art

    public SceneSpriteSet creatureArt(Species s) {
        String key = s.body.key() + "@" + planet.spec.biome.key();
        if (!creatureSets.containsKey(key)) {
            int[] palette = s.body.game().equals(planet.spec.biome.game()) ? planet.palette : null;
            if (palette == null) {
                palette = new int[64];
            }
            creatureSets.put(key, game.art.creature(s.body, palette, key));
        }
        return creatureSets.get(key);
    }

    public SceneSprite creatureFrame(Species s, int frame) {
        String key = s.id + "#" + frame;
        SceneSprite cached = creatureFrames.get(key);
        if (cached == null) {
            SceneSpriteSet set = creatureArt(s);
            SceneSprite raw;
            try {
                raw = set.frame(frame);
            } catch (RuntimeException broken) {
                raw = null;
            }
            cached = raw == null ? null : game.art.recolored(raw, s.recolor, key);
            if (cached != null) {
                creatureFrames.put(key, cached);
            }
        }
        return cached;
    }

    // ---------------------------------------------------------------- drawing

    public float screenX(float worldX) {
        return game.width / 2f + terrain.dx(camX, worldX);
    }

    public float screenY(float worldY) {
        return worldY - camY;
    }

    @Override
    public void draw(Game g, SceneCanvas c) {
        int tint = weather.tint();
        float shakeX = g.shakeX();
        float shakeY = g.shakeY();
        float cx = camX - shakeX;
        float cy = camY - shakeY;
        drawSky(g, c, tint, cx, cy);
        drawTerrain(g, c, tint, cx, cy);
        int entityTint = tint;
        for (Thing t : things) {
            float sx = g.width / 2f + terrain.dx(cx, t.x);
            float sy = t.y - cy;
            if (sx < -90 || sx > g.width + 90 || sy < -20 || sy > g.height + 120) {
                continue;
            }
            if (!t.alive && t.kind != Planet.P_MONITOR && t.kind != Planet.P_WRECK) {
                continue;
            }
            if (t == graveThing) {
                SceneSprite body = g.art.frame("ship", Art.SHIP_BODY);
                if (body != null) {
                    c.draw(body, sx, sy - 14, SceneDraw.plain().withTint(0xFF606060));
                }
                if (g.ticks % 10 == 0) {
                    particles.add(t.x, t.y - 20, 0.1f, -0.5f, 0, 3, 0x90505050, 60, false);
                }
                continue;
            }
            if (t.kind == Planet.P_WRECK && !t.alive) {
                continue;
            }
            t.draw(this, c, sx, sy, g.ticks, entityTint);
        }
        for (Creature cr : creatures) {
            if (!cr.alive) {
                continue;
            }
            float sx = g.width / 2f + terrain.dx(cx, cr.x);
            float sy = cr.y - cy;
            if (sx < -60 || sx > g.width + 60 || sy < -60 || sy > g.height + 60) {
                continue;
            }
            cr.draw(this, c, sx, sy, g.ticks, entityTint);
        }
        for (Pickup pk : pickups) {
            float sx = g.width / 2f + terrain.dx(cx, pk.x);
            float sy = pk.y - cy;
            if (pk.scattered && pk.age > 60 * 6 && (g.ticks / 3) % 2 == 0) {
                continue;
            }
            drawPickup(g, c, pk, sx, sy);
        }
        for (float[] e : explosions) {
            float sx = g.width / 2f + terrain.dx(cx, e[0]);
            float sy = e[1] - cy;
            String set = e[3] > 0 ? "boss_explosion" : "explosion";
            SceneSprite f = g.art.frame(set, Math.min(4, (int) (e[2] / 5)));
            if (f != null) {
                c.draw(f, sx, sy, SceneDraw.plain().withScale(e[3] > 0 ? 1.5f : 1));
            }
        }
        for (Hero h : heroes) {
            float sx = g.width / 2f + terrain.dx(cx, h.x);
            float sy = h.y - cy;
            h.draw(this, c, sx, sy, g.ticks, Colour.lerp(entityTint, 0xFFFFFFFF, 0.5f));
        }
        // Eggman.
        float shipSx = g.width / 2f + terrain.dx(cx, ship.x);
        float shipSy = ship.y - cy;
        if (beamOn && phase == PLAY) {
            drawBeam(g, c, shipSx, shipSy, cx, cy);
        }
        if (phase != DEAD || phaseTicks < 60) {
            ship.draw(g, c, shipSx, shipSy, phase == DESCEND || phase == LAUNCH || g.in.up && phase == PLAY,
                    phase == DEAD ? Art.SHIP_HEAD_HURT : -1);
        }
        if (invincible > 0 && (g.ticks / 2) % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                double a = g.ticks * 0.2 + i * Math.PI / 2;
                c.fill((int) (shipSx + Math.cos(a) * 28), (int) (shipSy + Math.sin(a) * 24), 3, 3, 0xFFFFFFFF);
            }
        }
        if (g.player.shield > 0 && ship.invulnerable > 40) {
            drawBubble(c, shipSx, shipSy, 0x6060FFFF);
        }
        for (Shot s : shots) {
            float sx = g.width / 2f + terrain.dx(cx, s.x);
            float sy = s.y - cy;
            if (s.type == Shot.RING_BOMB) {
                SceneSprite r = g.art.frame("ring", (int) ((g.ticks / 3) % 4));
                if (r != null) {
                    c.draw(r, sx, sy, SceneDraw.plain().withTint((g.ticks / 4) % 2 == 0 ? 0xFFFF8080 : 0xFFFFFFFF));
                }
            } else {
                c.fill((int) sx - 3, (int) sy - 1, 6, 3, 0xFF40FFFF);
                c.fill((int) sx - 2, (int) sy, 4, 1, 0xFFFFFFFF);
            }
        }
        particles.draw(c, cx, cy, terrain.width(), g.width, g.height);
        if (pulse >= 0) {
            drawPulse(g, c, shipSx, shipSy);
        }
        weather.drawOverlay(c, g, terrain.dx(lastCamX, camX), camY - lastCamY, sheltered);
        lastCamX = camX;
        lastCamY = camY;
        drawScanMarkers(g, c, cx, cy);
        if (visor) {
            SurfaceHud.drawVisor(this, g, c, cx, cy);
        }
        if (phase == LAUNCH) {
            float t = Math.min(1, phaseTicks / 110f);
            c.fill(0, 0, g.width, g.height, Colour.alpha(0xFF000010, Math.round(t * t * 255)));
        }
        if (phase == DEAD && phaseTicks > 90) {
            c.fill(0, 0, g.width, g.height, Colour.alpha(0xFF000000, Math.min(255, (phaseTicks - 90) * 6)));
        }
        SurfaceHud.draw(this, g, c);
        if (dialogTitle != null) {
            SurfaceHud.drawDialog(g, c, dialogTitle, dialogLines, dialogOptions, dialogChoice);
        }
    }

    private void drawSky(Game g, SceneCanvas c, int tint, float cx, float cy) {
        int top = Colour.argb(255, Colour.r(planet.skyTop) * Colour.r(tint) / 255, Colour.g(planet.skyTop) * Colour.g(tint) / 255,
                Colour.b(planet.skyTop) * Colour.b(tint) / 255);
        int bottom = Colour.argb(255, Colour.r(planet.skyBottom) * Colour.r(tint) / 255,
                Colour.g(planet.skyBottom) * Colour.g(tint) / 255, Colour.b(planet.skyBottom) * Colour.b(tint) / 255);
        // Higher means darker: the edge of space above the act.
        float space = Math.max(0, Math.min(1, -cy / 420f));
        top = Colour.lerp(top, 0xFF000010, space);
        c.clear(top & 0xFFFFFF);
        weather.drawStars(c, g, cx);
        if (space > 0.1f) {
            for (int i = 0; i < 40; i++) {
                long h = Rng.mix(i * 977L);
                c.fill((int) (h & 511) % g.width, (int) ((h >>> 9) % g.height), 1, 1, Colour.alpha(0xFFFFFFFF, Math.round(space * 200)));
            }
        }
        SceneImage bg = backdropImage;
        int bgH = bg.height();
        float ref = Math.max(-0.5f, Math.min(1, cy / Math.max(1, terrain.height() - g.height)));
        float bgY;
        if (bgH > g.height) {
            bgY = (g.height - bgH) * (0.3f + 0.6f * ref);
        } else {
            bgY = g.height - bgH - (1 - ref) * 30 + 20;
        }
        bgY -= Math.min(0, cy) * 0.35f;
        SceneDraw style = SceneDraw.plain().withTint(tint);
        int w = bg.width();
        for (SceneBackdrop.Band band : planet.bands) {
            float y0 = bgY + band.top();
            if (y0 > g.height || y0 + band.height() < 0) {
                continue;
            }
            double scroll = cx * band.speed() + band.drift() * g.ticks;
            int col = (int) Math.floorMod((long) Math.floor(scroll), (long) w);
            int done = 0;
            while (done < g.width) {
                int run = Math.min(w - col, g.width - done);
                c.drawRegion(bg, col, band.top(), run, band.height(), done, y0, run, band.height(), style);
                done += run;
                col = 0;
            }
        }
        float below = bgY + bgH;
        if (below < g.height) {
            c.fill(0, (int) below, g.width, g.height - (int) below + 1, bottom);
        }
    }

    private void drawTerrain(Game g, SceneCanvas c, int tint, float cx, float cy) {
        int size = terrain.blockSize();
        float left = cx - g.width / 2f;
        int c0 = (int) Math.floor(left / size);
        int c1 = (int) Math.floor((left + g.width) / size);
        int r0 = Math.max(0, (int) Math.floor(cy / size));
        int r1 = Math.min(terrain.rows() - 1, (int) Math.floor((cy + g.height) / size));
        SceneDraw style = SceneDraw.plain().withTint(tint);
        for (int row = r0; row <= r1; row++) {
            for (int col = c0; col <= c1; col++) {
                SceneImage img = terrain.image(col, row);
                if (img == null) {
                    continue;
                }
                float sx = col * size - left;
                float sy = row * size - cy;
                c.draw(img, Math.round(sx), Math.round(sy), style);
            }
        }
        float bottom = terrain.height() - cy;
        if (bottom < g.height) {
            c.fill(0, (int) bottom, g.width, g.height - (int) bottom + 1, Colour.scale(0xFF201810, Colour.r(tint) / 255f));
        }
    }

    private void drawBeam(Game g, SceneCanvas c, float shipSx, float shipSy, float cx, float cy) {
        float gx = g.width / 2f + terrain.dx(cx, ship.gunX());
        float gy = ship.gunY() - cy;
        float hx = g.width / 2f + terrain.dx(cx, beamX);
        float hy = beamY - cy;
        float len = (float) Math.hypot(hx - gx, hy - gy);
        int steps = Math.max(1, (int) (len / 2));
        float flicker = (g.ticks % 4) / 4f;
        int outer = heat > 75 ? 0x90FF2010 : 0x80FF6020;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = Math.round(gx + (hx - gx) * t);
            int y = Math.round(gy + (hy - gy) * t);
            int wobble = (int) (Math.sin(i * 0.9 + g.ticks * 0.8) * 1.2);
            c.fill(x - 2, y - 2 + wobble, 4, 4, outer);
            c.fill(x - 1, y - 1, 2, 2, 0xFFFFB040);
            if ((i + g.ticks) % 3 != 0) {
                c.fill(x, y, 1, 1, 0xFFFFFFE0);
            }
        }
        int glow = 6 + (int) (flicker * 4);
        c.fill(Math.round(hx) - glow / 2, Math.round(hy) - glow / 2, glow, glow, 0x80FFE080);
        c.fill(Math.round(hx) - 2, Math.round(hy) - 2, 4, 4, 0xFFFFFFFF);
        c.fill(Math.round(gx) - 3, Math.round(gy) - 3, 6, 6, 0xC0FFE0A0);
    }

    private void drawPulse(Game g, SceneCanvas c, float sx, float sy) {
        int segments = 72;
        float r = pulse;
        int alpha = Math.max(30, 220 - (int) (r * 0.6f));
        for (int i = 0; i < segments; i++) {
            double a = i * Math.PI * 2 / segments;
            int x = Math.round(sx + (float) Math.cos(a) * r);
            int y = Math.round(sy + (float) Math.sin(a) * r * 0.75f);
            c.fill(x - 1, y - 1, 3, 3, Colour.alpha(0xFF60E0FF, alpha));
        }
    }

    private void drawBubble(SceneCanvas c, float sx, float sy, int colour) {
        for (int i = 0; i < 40; i++) {
            double a = i * Math.PI * 2 / 40;
            c.fill(Math.round(sx + (float) Math.cos(a) * 34), Math.round(sy - 8 + (float) Math.sin(a) * 30), 2, 2, colour);
        }
    }

    private void drawPickup(Game g, SceneCanvas c, Pickup pk, float sx, float sy) {
        if (pk.type == Pickup.RINGS) {
            SceneSprite r = g.art.frame("ring", (int) ((g.ticks / 4 + pk.age) % 4));
            if (r != null) {
                c.draw(r, sx, sy, SceneDraw.plain());
            }
            return;
        }
        int colour = pk.type == Pickup.SHARDS ? 0xFF80FFE0 : g.catalog.item(pk.item).colour();
        float pulseSize = 1 + 0.2f * (float) Math.sin((g.ticks + pk.age) * 0.3);
        int s = Math.round(4 * pulseSize);
        c.fill(Math.round(sx) - s, Math.round(sy) - s, s * 2, s * 2, Colour.alpha(colour, 70));
        c.fill(Math.round(sx) - 2, Math.round(sy) - 3, 4, 6, colour);
        c.fill(Math.round(sx) - 3, Math.round(sy) - 2, 6, 4, colour);
        c.fill(Math.round(sx) - 1, Math.round(sy) - 2, 1, 1, 0xFFFFFFFF);
    }

    private void drawScanMarkers(Game g, SceneCanvas c, float cx, float cy) {
        int now = (int) g.ticks;
        for (Thing t : things) {
            if (t.scannedUntil < now || !t.alive && t.kind != Planet.P_MONITOR || t.spent && t.kind != Planet.P_STARPOST) {
                continue;
            }
            float sx = g.width / 2f + terrain.dx(cx, t.x);
            float sy = t.y - t.height() - 10 - cy;
            if (sx < 0 || sx > g.width || sy < 0 || sy > g.height) {
                continue;
            }
            String label;
            int colour;
            switch (t.kind) {
                case Planet.P_MONITOR -> {
                    label = "?";
                    colour = 0xFFFFFFFF;
                }
                case Planet.P_CAPSULE -> {
                    label = "POD";
                    colour = 0xFFFFC040;
                }
                case Planet.P_RUINS -> {
                    label = "RUIN";
                    colour = 0xFF40E0FF;
                }
                case Planet.P_STARPOST -> {
                    label = "SAVE";
                    colour = 0xFF60A0FF;
                }
                case Planet.P_GIANT_RING -> {
                    label = "RING";
                    colour = 0xFFFFE040;
                }
                case Planet.P_SHRINE -> {
                    label = "EMERALD";
                    colour = 0xFF60FF80;
                }
                case Planet.P_WRECK -> {
                    label = "WRECK";
                    colour = 0xFFFF8040;
                }
                case Planet.P_RINGS -> {
                    continue;
                }
                default -> {
                    Catalog.Item item = g.catalog.item(t.yield);
                    if (item == null) {
                        continue;
                    }
                    label = item.code();
                    colour = item.colour();
                }
            }
            int w = eggsky.ui.Font.width(label) + 6;
            c.fill(Math.round(sx) - w / 2, Math.round(sy) - 5, w, 10, 0xA0000018);
            c.fill(Math.round(sx) - 1, Math.round(sy) + 5, 2, 4, colour);
            g.font.centre(c, label, Math.round(sx), Math.round(sy) - 3, colour);
        }
        for (Creature cr : creatures) {
            if (cr.scannedUntil < now || !cr.alive) {
                continue;
            }
            float sx = g.width / 2f + terrain.dx(cx, cr.x);
            float sy = cr.y - cr.height() - 8 - cy;
            boolean known = g.player.discovered.contains(cr.species.id);
            int colour = cr.species.temperament >= Species.AGGRESSIVE ? 0xFFFF6060 : 0xFF80FF80;
            g.font.centre(c, known ? "*" : "?", Math.round(sx), Math.round(sy) - 3, colour);
        }
    }

    @Override
    public boolean live() {
        return phase == PLAY;
    }

    // Accessors for the HUD.
    public int phase() {
        return phase;
    }

    public boolean playing() {
        return phase == PLAY;
    }

    public float launchProgress() {
        return ship.launchHold / (float) LAUNCH_HOLD;
    }

    public boolean sheltered() {
        return sheltered;
    }

    public float hazardDrain() {
        return hazardDrain;
    }

    public int wantedFlash() {
        return wantedFlash;
    }

    public int invincibleTicks() {
        return invincible;
    }

    public Object beamTarget() {
        return beamTarget;
    }
}
