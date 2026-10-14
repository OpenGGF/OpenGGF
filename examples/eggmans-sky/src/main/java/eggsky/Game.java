package eggsky;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneLevelKit;
import eggsky.art.Art;
import eggsky.core.Controls;
import eggsky.core.Rng;
import eggsky.core.Sound;
import eggsky.core.Voice;
import eggsky.core.VoiceLine;
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
import eggsky.world.GenerationKey;
import eggsky.world.GeneratedAssets;
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
    public final Voice voice = new Voice();
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
    private final GeneratedAssets generated = new GeneratedAssets();
    private boolean saveEligible = true;
    private boolean rejectionNoticed;
    /** Generation-owned orbit textures. Kept as a facade for modes and scene inspection. */
    public final java.util.LinkedHashMap<GenerationKey, eggsky.space.PlanetTexture> textureCache = generated.orbitTextures;

    /** The galaxy map's backdrop for the current galaxy, built once. */
    public com.openggf.mods.scene.SceneImage galaxyImage() { return generated.galaxyImage(galaxy.seed); }

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
        usableBiomes.clear();
        for (Biome b : biomes.available(art.games())) {
            if (art.levelKit(b.game(), b.zone(), b.act()) != null) usableBiomes.add(b);
        }
        sound.bind(ctx, 0);
        voice.enabled(!ctx.storage().read(SETTINGS_FILE).orElse("").lines()
                .anyMatch(line -> line.equals("voice=false")));
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
        toasts.update(mode != null && mode.live());
        if (player != null) voice.vitals(player, mode instanceof SurfaceMode, mode instanceof SpaceMode,
                mode != null && mode.live());
        voice.update(ctx.audio(), ticks);
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
        if (player != null && player.pinned != 0 && (mode instanceof SurfaceMode || mode instanceof SpaceMode)) {
            eggsky.ui.PinnedHud.draw(this, c);
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
        if ((colour >> 16 & 255) >= 240 && (colour >> 8 & 255) <= 128 && (colour & 255) <= 128) {
            toasts.warning(text, colour);
        } else {
            toasts.add(text, colour);
        }
    }

    public void banner(String title, String subtitle, int colour) {
        toasts.banner(title, subtitle, colour);
    }

    public void toast(VoiceLine line, String text, int colour) {
        toast(text, colour);
        voice.say(line);
    }

    public void banner(VoiceLine line, String title, String subtitle, int colour) {
        banner(title, subtitle, colour);
        voice.say(line);
    }

    public void toggleVoice() {
        voice.enabled(!voice.enabled());
        try {
            String other = ctx.storage().read(SETTINGS_FILE).orElse("").lines()
                    .filter(line -> !line.startsWith("voice=")).collect(java.util.stream.Collectors.joining("\n"));
            if (!ctx.storage().write(SETTINGS_FILE, other+(other.isEmpty() ? "" : "\n")+"voice="+voice.enabled()+"\n")) {
                toast("SETTINGS COULD NOT BE SAVED", 0xFFFF5050);
            }
        } catch (RuntimeException failed) {
            toast("SETTINGS COULD NOT BE SAVED", 0xFFFF5050);
        }
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
        return generated.planet();
    }

    /** Builds a planet of the current system (may take a few hundred milliseconds). */
    public Planet buildPlanet(int index) {
        StarSystem s = system();
        PlanetSpec spec = planetSpec(index);
        if (spec == null) return null;
        GenerationKey key = generationKey(s, spec);
        Planet cached = generated.find(key);
        if (cached != null) return cached;
        SceneLevelKit kit = art.levelKit(spec.biome.game(), spec.biome.zone(), spec.biome.act());
        if (kit == null) throw new IllegalStateException("Planet requires level kit: " + spec.biome.key());
        boolean shrine = player.shrineSystem == s.id && player.shrinePlanet == index
                && player.emeraldCount() < 7;
        return generated.remember(new Planet(spec, s, kit, art, shrine, key));
    }

    public GenerationKey generationKey(StarSystem system, PlanetSpec spec) {
        return new GenerationKey(galaxy.seed, galaxy.number, system.id, spec.index,
                spec.seed, spec.biome.key(), GenerationKey.SCHEMA);
    }

    private void resetGenerationCaches() {
        generated.clear();
        if (art != null) art.clearGeneration();
    }

    // ---------------------------------------------------------------- transitions

    /** Lands on planet {@code index}: a loading frame, then the surface descending from the sky. */
    public void land(int index, boolean fromSpace) {
        if (fromSpace) voice.say(VoiceLine.LANDING);
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
        voice.say(VoiceLine.DOCKING);
        save();
        setMode(new StationMode());
    }

    public void undock() {
        voice.say(VoiceLine.UNDOCKED);
        setMode(new SpaceMode(SpaceMode.ARRIVE_UNDOCK, -1));
    }

    /** A name the player gave, or the procedural one. */
    public String displayName(PlanetSpec spec) {
        String key = system().id + ":" + spec.index;
        return player.names.getOrDefault(key, spec.name);
    }

    // ---------------------------------------------------------------- saving

    public boolean hasStoredSave() {
        return ctx.storage().list().contains(SAVE_FILE);
    }

    public boolean hasSave() {
        var text = ctx.storage().read(SAVE_FILE);
        if (text.isEmpty()) {
            if (hasStoredSave()) rejectSave();
            return false;
        }
        boolean usable = decodeSave(text.get()) != null;
        if (!usable) rejectSave();
        return usable;
    }

    private record SavedExpedition(Player player, Galaxy galaxy) { }

    private SavedExpedition decodeSave(String text) {
        Player candidate = new Player(catalog);
        if (!candidate.decode(text)) return null;
        Galaxy nextGalaxy = new Galaxy(candidate.galaxySeed, candidate.galaxyNumber);
        nextGalaxy.start(); // Restore the home system's tutorial generation flag.
        StarSystem savedSystem = nextGalaxy.system(candidate.systemId);
        if (savedSystem == null || candidate.planet >= savedSystem.planetCount
                || candidate.beaconPlanet >= savedSystem.planetCount) return null;
        return new SavedExpedition(candidate, nextGalaxy);
    }

    private void rejectSave() {
        saveEligible = false;
        if (!rejectionNoticed) {
            toast("SAVE UNREADABLE: NEW EXPEDITION REPLACES IT", 0xFFFF5050);
            rejectionNoticed = true;
        }
    }

    public boolean save() {
        if (!saveEligible || player == null || galaxy == null) {
            return false;
        }
        try {
            if (!ctx.storage().write(SAVE_FILE, player.encode())) {
                toast(VoiceLine.SAVE_FAILED, "SAVE FAILED", 0xFFFF5050);
                return false;
            }
            return true;
        } catch (RuntimeException failed) {
            toast(VoiceLine.SAVE_FAILED, "SAVE FAILED", 0xFFFF5050);
            return false;
        }
    }

    /** Loads the saved expedition; false when there is none. */
    public boolean load() {
        var text = ctx.storage().read(SAVE_FILE);
        if (text.isEmpty()) {
            if (hasStoredSave()) rejectSave();
            return false;
        }
        SavedExpedition candidate = decodeSave(text.get());
        if (candidate == null) {
            rejectSave();
            return false;
        }
        // Publish only after the complete candidate and its identity are usable.
        player = candidate.player();
        galaxy = candidate.galaxy();
        saveEligible = true;
        rejectionNoticed = false;
        resetGenerationCaches();
        voice.reset();
        voice.say(VoiceLine.SYSTEMS_ONLINE);
        return true;
    }

    /** A brand-new expedition: crash-landed on the starting system's first planet. */
    public void newExpedition(long seed, int galaxyNumber, Player carry) {
        if (galaxyNumber < 1) throw new IllegalArgumentException("Galaxy number");
        saveEligible = true;
        rejectionNoticed = false;
        resetGenerationCaches();
        voice.reset();
        voice.say(VoiceLine.SYSTEMS_ONLINE);
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
        player.hull = carry != null ? player.maxHull() : player.maxHull() * 0.7f;
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
                    resetGenerationCaches();
                    galaxy = new Galaxy(p.length > 4 ? Long.parseLong(p[4]) : 7, 1);
                    player.galaxySeed = galaxy.seed;
                    player.galaxyNumber = galaxy.number;
                    player.systemId = galaxy.start().id;
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
            case "wanted" -> {
                if (mode instanceof SurfaceMode s) {
                    s.wanted = Float.parseFloat(p[1]);
                    return true;
                }
                return false;
            }
            case "storm" -> {
                if (mode instanceof SurfaceMode s) {
                    s.weather.forceStorm();
                    return true;
                }
                return false;
            }
            case "night" -> {
                if (mode instanceof SurfaceMode s) {
                    s.weather.dayPhase = 0.75f;
                    return true;
                }
                return false;
            }
            case "pirates" -> {
                if (mode instanceof SpaceMode s) {
                    s.debugPirates(this, p.length > 1 && p[1].equals("tornado"));
                    return true;
                }
                return false;
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
