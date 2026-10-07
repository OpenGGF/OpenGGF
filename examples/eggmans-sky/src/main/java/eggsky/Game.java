package eggsky;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneLevelKit;
import eggsky.art.Art;
import eggsky.core.Controls;
import eggsky.core.Rng;
import eggsky.core.Sound;
import eggsky.game.Catalog;
import eggsky.game.Player;
import eggsky.space.SpaceMode;
import eggsky.station.StationMode;
import eggsky.surface.SurfaceMode;
import eggsky.ui.Font;
import eggsky.ui.LoadingMode;
import eggsky.ui.TitleMode;
import eggsky.ui.Toasts;
import eggsky.world.Biome;
import eggsky.world.Biomes;
import eggsky.world.Galaxy;
import eggsky.world.Planet;
import eggsky.world.PlanetSpec;
import eggsky.world.StarSystem;
import java.util.ArrayList;
import java.util.List;

/**
 * The whole game: shared services (input, art, sound, fonts, catalogue), the persistent
 * {@link Player}, the {@link Galaxy}, and the current {@link Mode}. Modes call back here to land,
 * launch, dock, warp, save and announce things.
 */
public final class Game {
    public static final String SAVE_FILE = "expedition.txt";
    public static final String SETTINGS_FILE = "settings.txt";

    public SceneContext ctx;
    public final Controls in = new Controls();
    public final Sound sound = new Sound();
    public final Font font = new Font();
    public final Catalog catalog = new Catalog();
    public final Biomes biomes = new Biomes();
    public final Toasts toasts = new Toasts();
    public Art art;
    public List<Biome> usableBiomes = new ArrayList<>();
    public Player player;
    public Galaxy galaxy;
    public long ticks;
    public int width = 400;
    public int height = 224;
    private Mode mode;
    private Mode pending;
    private Planet planet;
    /** Orbit textures of planets already seen, by system and planet. */
    public final java.util.LinkedHashMap<String, eggsky.space.PlanetTexture> textureCache =
            new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, eggsky.space.PlanetTexture> e) {
                    return size() > 24;
                }
            };
    /** The wanted level when Eggman last left a surface (heroes may follow him into orbit). */
    public float lastWanted;
    /** Screen shake strength (decays) and full-screen flash. */
    public float shake;
    public int flashColour;
    public int flashTicks;

    public void start(SceneContext ctx) {
        this.ctx = ctx;
        width = ctx.width();
        height = ctx.height();
        art = new Art(ctx);
        // Biomes whose game is supplied and whose act has a level kit.
        for (Biome b : biomes.available(art.games())) {
            try {
                if (art.rom(b.game()) != null && art.rom(b.game()).hasLevelKit(b.zone(), b.act())) {
                    usableBiomes.add(b);
                }
            } catch (RuntimeException skip) {
                // That act cannot be pictured on this build.
            }
        }
        sound.bind(ctx, 0);
        setMode(new TitleMode());
    }

    public Mode mode() {
        return mode;
    }

    /** Switches modes at the end of this update (or now, before the first). */
    public void setMode(Mode next) {
        if (mode == null) {
            mode = next;
            next.enter(this);
        } else {
            pending = next;
        }
    }

    public void update(SceneContext ctx) {
        this.ctx = ctx;
        ticks++;
        sound.bind(ctx, ticks);
        in.read(ctx);
        if (mode != null) {
            mode.update(this);
            if (mode.live() && player != null) {
                player.playTicks++;
            }
        }
        if (pending != null) {
            Mode old = mode;
            mode = pending;
            pending = null;
            if (old != null) {
                old.exit(this);
            }
            mode.enter(this);
        }
        toasts.update();
        shake *= 0.86f;
        if (shake < 0.2f) {
            shake = 0;
        }
        if (flashTicks > 0) {
            flashTicks--;
        }
        if (player != null && ticks % (60 * 120) == 0 && mode != null && mode.live()) {
            save();
        }
    }

    public void draw(SceneContext ctx, SceneCanvas c) {
        if (mode != null) {
            mode.draw(this, c);
        }
        toasts.draw(this, c);
        if (flashTicks > 0) {
            int a = Math.min(255, flashTicks * 24);
            c.fill(0, 0, width, height, (a << 24) | (flashColour & 0xFFFFFF));
        }
    }

    /** Screen shake offsets for this frame. */
    public float shakeX() {
        return shake <= 0 ? 0 : (float) Math.sin(ticks * 1.7) * shake;
    }

    public float shakeY() {
        return shake <= 0 ? 0 : (float) Math.cos(ticks * 2.3) * shake;
    }

    public void flash(int colour, int ticks) {
        flashColour = colour;
        flashTicks = Math.max(flashTicks, ticks);
    }

    public void toast(String text, int colour) {
        toasts.add(text, colour);
    }

    public void banner(String title, String subtitle, int colour) {
        toasts.banner(title, subtitle, colour);
    }

    // ---------------------------------------------------------------- the galaxy

    public StarSystem system() {
        StarSystem s = galaxy.system(player.systemId);
        if (s == null) {
            s = galaxy.start();
            player.systemId = s.id;
        }
        return s;
    }

    public PlanetSpec planetSpec(int index) {
        List<PlanetSpec> specs = system().planets(usableBiomes);
        return index >= 0 && index < specs.size() ? specs.get(index) : null;
    }

    /** The planet Eggman is on (or was last on), built on demand. */
    public Planet planet() {
        return planet;
    }

    /** Builds a planet of the current system (may take a few hundred milliseconds). */
    public Planet buildPlanet(int index) {
        StarSystem s = system();
        String key = s.id + ":" + index;
        if (planet != null && planet.key.equals(key)) {
            return planet;
        }
        PlanetSpec spec = planetSpec(index);
        if (spec == null) {
            return null;
        }
        SceneLevelKit kit = art.rom(spec.biome.game()).levelKit(spec.biome.zone(), spec.biome.act());
        boolean shrine = player.shrineSystem == s.id && player.shrinePlanet == index
                && player.emeraldCount() < 7;
        planet = new Planet(spec, s, kit, art, shrine);
        return planet;
    }

    // ---------------------------------------------------------------- transitions

    /** Lands on planet {@code index}: a loading frame, then the surface descending from the sky. */
    public void land(int index, boolean fromSpace) {
        PlanetSpec spec = planetSpec(index);
        String title = spec == null ? "" : displayName(spec);
        setMode(new LoadingMode("ENTERING ATMOSPHERE", title, () -> {
            Planet p = buildPlanet(index);
            player.planet = index;
            SurfaceMode surface = new SurfaceMode(p, fromSpace ? SurfaceMode.ARRIVE_DESCEND : SurfaceMode.ARRIVE_RESUME);
            return surface;
        }));
    }

    /** Leaves the surface for orbit around the current planet. */
    public void launch() {
        int from = player.planet;
        player.planet = -1;
        save();
        setMode(new SpaceMode(SpaceMode.ARRIVE_LAUNCH, from));
    }

    public void dock() {
        save();
        setMode(new StationMode());
    }

    public void undock() {
        setMode(new SpaceMode(SpaceMode.ARRIVE_UNDOCK, -1));
    }

    /** A name the player gave, or the procedural one. */
    public String displayName(PlanetSpec spec) {
        String key = system().id + ":" + spec.index;
        return player.names.getOrDefault(key, spec.name);
    }

    // ---------------------------------------------------------------- saving

    public boolean hasSave() {
        return ctx.storage().read(SAVE_FILE).isPresent();
    }

    public void save() {
        if (player == null || galaxy == null) {
            return;
        }
        try {
            ctx.storage().write(SAVE_FILE, player.encode());
        } catch (RuntimeException failed) {
            toast("SAVE FAILED", 0xFFFF5050);
        }
    }

    /** Loads the saved expedition; false when there is none. */
    public boolean load() {
        var text = ctx.storage().read(SAVE_FILE);
        if (text.isEmpty()) {
            return false;
        }
        player = new Player(catalog);
        player.decode(text.get());
        galaxy = new Galaxy(player.galaxySeed, player.galaxyNumber);
        if (galaxy.system(player.systemId) == null) {
            player.systemId = galaxy.start().id;
        }
        return true;
    }

    /** A brand-new expedition: crash-landed on the starting system's first planet. */
    public void newExpedition(long seed, int galaxyNumber, Player carry) {
        player = new Player(catalog);
        player.galaxySeed = seed;
        player.galaxyNumber = galaxyNumber;
        galaxy = new Galaxy(seed, galaxyNumber);
        StarSystem start = galaxy.start();
        player.systemId = start.id;
        player.visitedSystems.add(start.id);
        if (carry != null) {
            // A new galaxy keeps technology, currencies and statistics.
            System.arraycopy(carry.tech, 0, player.tech, 0, player.tech.length);
            player.rings = carry.rings;
            player.shards = carry.shards;
            player.statWarps = carry.statWarps;
            player.statPlanets = carry.statPlanets;
            player.statSpecies = carry.statSpecies;
            player.statMined = carry.statMined;
            player.statSonicRepelled = carry.statSonicRepelled;
            player.statDeaths = carry.statDeaths;
            player.statRingsEarned = carry.statRingsEarned;
            player.statPirates = carry.statPirates;
            player.playTicks = carry.playTicks;
            player.tutorial = 99;
        }
        player.cargo.resize(player.slots());
        player.refill();
        player.launchFuel = carry != null ? 100 : 0;
        player.hull = carry != null ? player.maxHull() : player.maxHull() * 0.45f;
        player.planet = 0;
        player.surfaceX = -1;
        save();
    }

    public long newSeed() {
        return Rng.mix(System.nanoTime() ^ ticks * 0x9E3779B97F4A7C15L);
    }

    // ---------------------------------------------------------------- debug

    /** Debug jumps for capture tools and tests; see {@link SkyScene#debugJump}. */
    public boolean debug(String command) {
        String[] p = command.split(":");
        switch (p[0]) {
            case "new" -> {
                long seed = p.length > 1 ? Long.parseLong(p[1]) : 42;
                newExpedition(seed, 1, null);
                land(0, true);
                return true;
            }
            case "planet" -> {
                if (player == null) {
                    newExpedition(p.length > 2 ? Long.parseLong(p[2]) : 42, 1, null);
                    player.tutorial = 99;
                    player.launchFuel = 100;
                }
                land(p.length > 1 ? Integer.parseInt(p[1]) : 0, true);
                return true;
            }
            case "biome" -> {
                // biome:<game>:<zone>:<act>[:seed] — a test planet of one act.
                if (player == null) {
                    newExpedition(42, 1, null);
                    player.tutorial = 99;
                }
                debugBiome = biomes.all().stream().filter(b -> b.game().equals(p[1])
                        && b.zone() == Integer.parseInt(p[2]) && b.act() == Integer.parseInt(p[3])).findFirst()
                        .orElse(null);
                if (debugBiome != null) {
                    usableBiomes = new ArrayList<>(List.of(debugBiome));
                    galaxy = new Galaxy(p.length > 4 ? Long.parseLong(p[4]) : 7, 1);
                    player.systemId = galaxy.start().id;
                    planet = null;
                    land(0, true);
                }
                return true;
            }
            case "space" -> {
                if (player == null) {
                    newExpedition(42, 1, null);
                    player.tutorial = 99;
                }
                player.planet = -1;
                setMode(new SpaceMode(SpaceMode.ARRIVE_LAUNCH, p.length > 1 ? Integer.parseInt(p[1]) : 0));
                return true;
            }
            case "station" -> {
                if (player == null) {
                    newExpedition(42, 1, null);
                    player.tutorial = 99;
                }
                setMode(new StationMode());
                return true;
            }
            case "galaxy" -> {
                if (player == null) {
                    newExpedition(42, 1, null);
                    player.tutorial = 99;
                }
                setMode(new eggsky.space.GalaxyMode(new SpaceMode(SpaceMode.ARRIVE_UNDOCK, -1)));
                return true;
            }
            case "rich" -> {
                if (player != null) {
                    player.rings += 1_000_000;
                    player.shards += 50_000;
                    for (int id : catalog.itemIds()) {
                        player.cargo.add(id, 50);
                    }
                }
                return true;
            }
            case "title" -> {
                setMode(new TitleMode());
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private Biome debugBiome;
}
