package eggsky.world;

import eggsky.core.Colour;
import eggsky.core.Recolor;
import eggsky.core.Rng;
import eggsky.game.Catalog;

/**
 * Everything decided about a planet before landing: which act its terrain comes from, its
 * colours, climate, weather, how dangerous its sentinels are, what grows and what can be mined.
 * Derived entirely from the system seed and the planet's index, so it never needs saving.
 */
public final class PlanetSpec {
    public final long seed;
    public final int index;
    public final String name;
    public final Biome biome;
    public final Recolor ground;
    public final Recolor sky;
    /** 0 calm, 1 normal, 2 aggressive, 3 frenzied. */
    public final int sentinels;
    /** 0 rare storms .. 3 constant extreme weather. */
    public final int storms;
    /** Terrain columns to generate. */
    public final int columns;
    /** Minutes in a day-night cycle. */
    public final float dayMinutes;
    public final int faunaSpecies;
    public final int floraSpecies;
    /** The star metal and the biome's special plant. */
    public final int starMetal;
    public final int special;
    /** A ringed planet in orbit view. */
    public final boolean ringed;
    public final int ringColour;
    /** The planet's main colour in space (for the galaxy map and orbit sphere tint). */
    public final int signature;
    public final float gravity;
    public final boolean paradise;
    public final boolean dead;

    public PlanetSpec(long systemSeed, int index, int starClass, java.util.List<Biome> biomes) {
        this.seed = Rng.hash(systemSeed, 0x504C414EL, index);
        this.index = index;
        Rng rng = new Rng(seed);
        this.biome = pickBiome(rng, starClass, biomes);
        this.name = Names.planet(Rng.hash(seed, 1), "", index);
        boolean natural = rng.chance(0.3);
        float hue = natural ? 0 : rng.range(-180f, 180f);
        float sat = natural ? 1f : rng.range(0.8f, 1.3f);
        float bri = rng.range(0.92f, 1.08f);
        int tint = 0;
        float tintAmount = 0;
        if (!natural && rng.chance(0.25)) {
            tint = Colour.fromHsv(rng.range(0f, 360f), 0.8f, 0.9f, 255);
            tintAmount = rng.range(0.08f, 0.2f);
        }
        this.ground = new Recolor(hue, sat, bri, tint, tintAmount);
        float skyHue = rng.chance(0.5) ? hue : hue + rng.range(-120f, 120f);
        this.sky = new Recolor(skyHue, sat, bri, tint, tintAmount);
        this.dead = biome.climate() == Biome.CLIMATE_DEAD || biome.climate() == Biome.CLIMATE_BARREN && rng.chance(0.5);
        this.paradise = !dead && !biome.hazardous() && rng.chance(0.15);
        int base = dead ? 0 : rng.range(0, 2);
        if (biome.hazardous()) {
            base++;
        }
        this.sentinels = Math.min(3, Math.max(0, base + (rng.chance(0.1) ? 1 : 0)));
        this.storms = paradise ? 0 : Math.min(3, rng.range(0, 2) + (biome.hazardous() ? 1 : 0));
        this.columns = rng.range(44, 72);
        this.dayMinutes = rng.range(6f, 12f);
        int lush = paradise ? 3 : biome.lush();
        this.faunaSpecies = dead ? 0 : Math.max(1, lush + rng.range(1, 3));
        this.floraSpecies = dead ? 1 : Math.max(2, lush + rng.range(1, 3));
        this.starMetal = switch (starClass) {
            case StarSystem.RED -> Catalog.CADMIUM;
            case StarSystem.GREEN -> Catalog.EMERIL;
            case StarSystem.BLUE -> Catalog.INDIUM;
            default -> Catalog.COPPER;
        };
        this.special = switch (biome.climate()) {
            case Biome.CLIMATE_HOT -> Catalog.SOLANIUM;
            case Biome.CLIMATE_COLD -> Catalog.FROST_CRYSTAL;
            case Biome.CLIMATE_TOXIC -> Catalog.FUNGAL_MOULD;
            case Biome.CLIMATE_RADIOACTIVE -> Catalog.GAMMA_ROOT;
            case Biome.CLIMATE_EXOTIC -> Catalog.STAR_BULB;
            default -> Catalog.CARBON;
        };
        this.ringed = rng.chance(0.3);
        this.ringColour = Colour.fromHsv(rng.range(0f, 360f), rng.range(0.1f, 0.5f), 0.85f, 255);
        this.signature = Colour.fromHsv(climateHue(biome.climate()) + hue, 0.65f, 0.85f, 255);
        this.gravity = rng.range(0.8f, 1.25f);
    }

    private static float climateHue(int climate) {
        return switch (climate) {
            case Biome.CLIMATE_HOT -> 20;
            case Biome.CLIMATE_COLD -> 200;
            case Biome.CLIMATE_TOXIC -> 80;
            case Biome.CLIMATE_RADIOACTIVE -> 60;
            case Biome.CLIMATE_EXOTIC -> 300;
            case Biome.CLIMATE_DEAD, Biome.CLIMATE_BARREN -> 30;
            default -> 120;
        };
    }

    private static Biome pickBiome(Rng rng, int starClass, java.util.List<Biome> biomes) {
        int[] weights = new int[biomes.size()];
        for (int i = 0; i < weights.length; i++) {
            Biome b = biomes.get(i);
            int w = 10;
            int c = b.climate();
            if (starClass == StarSystem.RED && (c == Biome.CLIMATE_HOT || c == Biome.CLIMATE_BARREN)) {
                w += 12;
            }
            if (starClass == StarSystem.GREEN && (c == Biome.CLIMATE_LUSH || c == Biome.CLIMATE_TOXIC)) {
                w += 12;
            }
            if (starClass == StarSystem.BLUE && (c == Biome.CLIMATE_EXOTIC || c == Biome.CLIMATE_COLD)) {
                w += 12;
            }
            if (starClass == StarSystem.YELLOW && (c == Biome.CLIMATE_LUSH || c == Biome.CLIMATE_TEMPERATE)) {
                w += 6;
            }
            weights[i] = w;
        }
        return biomes.get(Math.max(0, rng.weighted(weights)));
    }

    /** One line for scanners and the galaxy map: "Frozen · Aggressive sentinels". */
    public String summary() {
        String climate = paradise ? "Paradise" : dead ? "Dead" : Biome.climateName(biome.climate());
        return climate + " " + biome.title();
    }

    public String sentinelName() {
        return switch (sentinels) {
            case 0 -> "Few heroes";
            case 1 -> "Hero patrols";
            case 2 -> "Aggressive heroes";
            default -> "FRENZIED HEROES";
        };
    }

    public String stormName() {
        if (storms == 0) {
            return "Calm";
        }
        return (storms >= 3 ? "Extreme " : storms == 2 ? "Frequent " : "Occasional ")
                + Biome.weatherName(biome.weather()).toLowerCase();
    }
}
