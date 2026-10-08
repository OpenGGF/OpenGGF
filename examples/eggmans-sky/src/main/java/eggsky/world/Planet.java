package eggsky.world;

import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelKit;
import com.openggf.mods.scene.SceneSprite;
import eggsky.art.Art;
import eggsky.art.FaunaDef;
import eggsky.art.PixelArt;
import eggsky.core.Colour;
import eggsky.core.Recolor;
import eggsky.core.Rng;
import eggsky.game.Catalog;
import java.util.ArrayList;
import java.util.List;

/**
 * A landed-on planet: its remixed terrain, recoloured background, the species that live there
 * and where every deposit, plant and point of interest stands. Built from the {@link PlanetSpec}
 * and the source act's {@link SceneLevelKit} when Eggman descends.
 */
public final class Planet {
    // Placement kinds.
    public static final int P_FLORA = 0;
    public static final int P_MINERAL = 1;
    public static final int P_CRYSTAL = 2;
    public static final int P_OXYGEN = 3;
    public static final int P_SODIUM = 4;
    public static final int P_SPECIAL = 5;
    public static final int P_MONITOR = 6;
    public static final int P_CAPSULE = 7;
    public static final int P_STARPOST = 8;
    public static final int P_RUINS = 9;
    public static final int P_GIANT_RING = 10;
    public static final int P_SHRINE = 11;
    public static final int P_WRECK = 12;
    public static final int P_RINGS = 13;
    public static final int P_COBALT = 14;
    public static final int P_GOLD = 15;
    public static final int P_FAUNA = 16;

    /** Something placed on the terrain at generation. {@code id} is stable for loot tracking. */
    public record Placement(int kind, float x, float y, int variant, String id) {
    }

    public final PlanetSpec spec;
    public final StarSystem system;
    public final String key;
    public final SceneLevelKit kit;
    public final Terrain terrain;
    public final SceneImage backdrop;
    public final List<SceneBackdrop.Band> bands;
    public final int skyTop;
    /** Rows of the backdrop with content (below them it is one flat colour). */
    public final int contentHeight;
    /** The typical height of the top floor, where the backdrop's bottom is anchored. */
    public final int groundLevel;
    public final int skyBottom;
    public final int[] palette;
    public final List<Species> fauna = new ArrayList<>();
    public final List<Species> flora = new ArrayList<>();
    public final List<Species> minerals = new ArrayList<>();
    public final List<Placement> placements = new ArrayList<>();
    /** Crystal sprites by placement variant: 0 di-hydrogen, 1 star metal, 2 cobalt, 3 storm. */
    public final SceneSprite[] crystals = new SceneSprite[4];
    public final SceneSprite oxygenPlant;
    public final SceneSprite sodiumPlant;
    public final SceneSprite specialPlant;
    public final SceneSprite goldVein;
    public final int specialColour;

    public Planet(PlanetSpec spec, StarSystem system, SceneLevelKit kit, Art art, boolean shrineHere) {
        this.spec = spec;
        this.system = system;
        this.key = system.id + ":" + spec.index;
        this.kit = kit;
        int size = kit.blockSize();
        int columns = Math.max(16, spec.columns * 128 / size);
        TerrainGen.Layout layout = TerrainGen.generate(kit, Rng.hash(spec.seed, 0x5445525AL), columns);
        this.terrain = new Terrain(kit, layout, spec.ground);
        // The zone's background in the planet's sky colours, cut into parallax bands.
        SceneBackdrop source = spec.biome.plainSky() ? proceduralSky(spec) : kit.backdrop();
        this.backdrop = spec.sky.apply(source.image());
        this.bands = parallaxBands(source);
        this.contentHeight = contentHeight(backdrop);
        this.skyTop = averageRow(backdrop, 0);
        this.skyBottom = averageRow(backdrop, contentHeight - 1);
        int[] tops = new int[64];
        int n = 0;
        for (int i = 0; i < tops.length; i++) {
            int floor = terrain.topFloor(i * terrain.width() / (float) tops.length);
            if (floor >= 0) {
                tops[n++] = floor;
            }
        }
        java.util.Arrays.sort(tops, 0, n);
        this.groundLevel = n == 0 ? terrain.height() / 2 : tops[n / 2];
        int[] pal = kit.palette();
        this.palette = pal;
        Rng rng = new Rng(Rng.hash(spec.seed, 0x53504543L));
        buildFauna(art, rng.fork(1));
        buildFlora(rng.fork(2));
        buildMinerals(art, rng.fork(3));
        Rng c = rng.fork(4);
        crystals[0] = PixelArt.plant(PixelArt.SHAPE_CRYSTAL, c.nextLong(), 0xFF4890FF, 0, 0, 0.9f);
        crystals[1] = PixelArt.plant(PixelArt.SHAPE_CRYSTAL, c.nextLong(), itemColour(spec.starMetal), 0, 0, 1.1f);
        crystals[2] = PixelArt.plant(PixelArt.SHAPE_CRYSTAL, c.nextLong(), 0xFF2050C0, 0, 0, 0.8f);
        crystals[3] = PixelArt.plant(PixelArt.SHAPE_CRYSTAL, c.nextLong(), 0xFFC8F4FF, 0, 0, 1.2f);
        oxygenPlant = PixelArt.plant(PixelArt.SHAPE_BULB, c.nextLong(), 0xFFE03030, 0xFF308030, 0, 1f);
        sodiumPlant = PixelArt.plant(PixelArt.SHAPE_FLOWER, c.nextLong(), 0xFFF0C020, 0xFFFFF0A0, 0, 1f);
        specialColour = itemColour(spec.special);
        specialPlant = PixelArt.plant(spec.special == Catalog.FROST_CRYSTAL ? PixelArt.SHAPE_CRYSTAL
                : PixelArt.SHAPE_BULB, c.nextLong(), specialColour, 0xFF406040, 0, 1f);
        goldVein = PixelArt.boulder(c.nextLong(), 0xFF7C6C5C, 0xFFFFD040, 1f);
        place(new Rng(Rng.hash(spec.seed, 0x504C4143L)), shrineHere);
    }

    private static int itemColour(int id) {
        return switch (id) {
            case Catalog.COPPER -> 0xFFE08840;
            case Catalog.CADMIUM -> 0xFFE03030;
            case Catalog.EMERIL -> 0xFF30E080;
            case Catalog.INDIUM -> 0xFF30C0FF;
            case Catalog.SOLANIUM -> 0xFFFF7040;
            case Catalog.FROST_CRYSTAL -> 0xFFA0E0FF;
            case Catalog.FUNGAL_MOULD -> 0xFF90C030;
            case Catalog.GAMMA_ROOT -> 0xFFC0FF40;
            case Catalog.STAR_BULB -> 0xFFFF60C0;
            default -> 0xFF48C048;
        };
    }

    /**
     * A procedural sky for zones whose detached background art is incomplete: a gradient with a
     * far and a near skyline shaped by the climate (dunes, snowy peaks, factory towers, rolling
     * hills or alien spires), recoloured later with the rest of the sky.
     */
    private static SceneBackdrop proceduralSky(PlanetSpec spec) {
        int w = 512;
        int h = 256;
        int[] px = new int[w * h];
        Rng rng = new Rng(Rng.hash(spec.seed, 0x534B59L));
        int climate = spec.biome.climate();
        int top;
        int horizon;
        int far;
        int near;
        int style;
        switch (climate) {
            case Biome.CLIMATE_HOT, Biome.CLIMATE_BARREN -> {
                top = 0xFF3060C0; horizon = 0xFFF0C080; far = 0xFFC08850; near = 0xFF905830; style = 0;
            }
            case Biome.CLIMATE_COLD -> {
                top = 0xFF4070C0; horizon = 0xFFD0E8FF; far = 0xFF8098C8; near = 0xFF506890; style = 1;
            }
            case Biome.CLIMATE_TOXIC -> {
                top = 0xFF204020; horizon = 0xFFA8D060; far = 0xFF406848; near = 0xFF283830; style = 2;
            }
            case Biome.CLIMATE_RADIOACTIVE, Biome.CLIMATE_DEAD -> {
                top = 0xFF302040; horizon = 0xFFE0A050; far = 0xFF604858; near = 0xFF302838; style = 2;
            }
            case Biome.CLIMATE_EXOTIC -> {
                top = 0xFF301860; horizon = 0xFFF080C0; far = 0xFF8040A0; near = 0xFF502070; style = 3;
            }
            default -> {
                top = 0xFF2858D0; horizon = 0xFFB0E0FF; far = 0xFF60A070; near = 0xFF307040; style = 4;
            }
        }
        long n1 = rng.nextLong();
        long n2 = rng.nextLong();
        int[] farLine = new int[w];
        int[] nearLine = new int[w];
        for (int x = 0; x < w; x++) {
            float u = x / (float) w;
            float a = Rng.noise2Wrapped(n1, u * 6, 0, 6);
            float b = Rng.noise2Wrapped(n1 + 1, u * 17, 0, 17);
            float c2 = Rng.noise2Wrapped(n2, u * 4, 0, 4);
            switch (style) {
                case 1 -> {
                    // Jagged peaks.
                    farLine[x] = (int) (130 + 40 * Math.abs(a) * -1 + 30 * b - 20 * Math.abs(Rng.noise2Wrapped(n1 + 2, u * 30, 0, 30)));
                    nearLine[x] = (int) (190 + 20 * c2);
                }
                case 2 -> {
                    // Towers and chimneys.
                    int block = (int) (u * 48);
                    long hsh = Rng.mix(n1 + block);
                    farLine[x] = 120 + (int) (hsh & 63) - ((hsh >>> 8) % 5 == 0 ? 30 : 0);
                    int nb = (int) (u * 24);
                    nearLine[x] = 175 + (int) (Rng.mix(n2 + nb) & 31);
                }
                case 3 -> {
                    // Alien spires.
                    float spire = (float) Math.pow(Math.abs(Rng.noise2Wrapped(n1 + 3, u * 40, 0, 40)), 3);
                    farLine[x] = (int) (150 + 20 * a - 90 * spire);
                    nearLine[x] = (int) (195 + 15 * c2);
                }
                default -> {
                    farLine[x] = (int) (150 + 30 * a + 10 * b);
                    nearLine[x] = (int) (190 + 25 * c2);
                }
            }
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int c = Colour.lerp(top, horizon, Math.min(1, y / 170f));
                if (y >= farLine[x]) {
                    c = Colour.lerp(far, Colour.scale(far, 0.8f), (y - farLine[x]) / 60f);
                    if (style == 1 && y < farLine[x] + 6) {
                        c = 0xFFF0F8FF;
                    }
                    if (style == 2 && (x + y) % 7 == 0 && y > farLine[x] + 4 && Rng.mix(x * 31L + y) % 9 == 0) {
                        c = 0xFFFFE070;
                    }
                }
                if (y >= nearLine[x]) {
                    c = Colour.lerp(near, Colour.scale(near, 0.7f), (y - nearLine[x]) / 60f);
                }
                if (style == 4 && y < 110 && Rng.noise2Wrapped(n2 + 5, x / 64f, y / 24f, 8) > 0.35f) {
                    c = Colour.lerp(c, 0xFFFFFFFF, 0.7f);
                }
                if (((x * 7 + y * 13) % 97 == 0) && y < farLine[x] && style != 4) {
                    c = Colour.lerp(c, 0xFFFFFFFF, 0.25f);
                }
                px[y * w + x] = Colour.genesis(c);
            }
        }
        return new SceneBackdrop(new SceneImage(w, h, px), List.of(new SceneBackdrop.Band(0, h, 0.2, 0)));
    }

    /** Splits a single-band background into rows that scroll faster toward the bottom. */
    private static List<SceneBackdrop.Band> parallaxBands(SceneBackdrop source) {
        if (source.bands().size() > 1) {
            return source.bands();
        }
        List<SceneBackdrop.Band> out = new ArrayList<>();
        int h = source.image().height();
        int step = 8;
        for (int top = 0; top < h; top += step) {
            int bh = Math.min(step, h - top);
            float t = (top + bh / 2f) / h;
            double speed = 0.06 + 0.42 * t * t;
            out.add(new SceneBackdrop.Band(top, bh, speed, 0));
        }
        return out;
    }

    /** The backdrop's height without the flat fill some zones leave below their scenery. */
    private static int contentHeight(SceneImage image) {
        int h = image.height();
        int w = image.width();
        while (h > 32) {
            int first = image.pixel(0, h - 1);
            boolean flat = true;
            for (int x = 1; x < w && flat; x += 2) {
                flat = image.pixel(x, h - 1) == first;
            }
            if (!flat) {
                break;
            }
            h--;
        }
        return h;
    }

    private static int averageRow(SceneImage image, int row) {
        long r = 0;
        long g = 0;
        long b = 0;
        int n = 0;
        for (int x = 0; x < image.width(); x += 4) {
            int p = image.pixel(x, row);
            if ((p >>> 24) == 0) {
                continue;
            }
            r += Colour.r(p);
            g += Colour.g(p);
            b += Colour.b(p);
            n++;
        }
        if (n == 0) {
            return 0xFF000000;
        }
        return Colour.rgb((int) (r / n), (int) (g / n), (int) (b / n));
    }

    private void buildFauna(Art art, Rng rng) {
        if (spec.faunaSpecies <= 0) {
            return;
        }
        List<FaunaDef> bodies = art.fauna.forGame(spec.biome.game());
        List<FaunaDef> natives = new ArrayList<>();
        List<FaunaDef> robots = new ArrayList<>();
        List<FaunaDef> animals = new ArrayList<>();
        for (FaunaDef d : bodies) {
            if (d.animal()) {
                animals.add(d);
            } else {
                robots.add(d);
                if (d.nativeTo(spec.biome.zone())) {
                    natives.add(d);
                }
            }
        }
        int animalCount = spec.dead ? 0 : Math.min(animals.size(), 1 + (spec.biome.lush() >= 2 ? 1 : 0));
        for (int i = 0; i < spec.faunaSpecies; i++) {
            FaunaDef body;
            if (i < animalCount && !animals.isEmpty()) {
                body = animals.get(rng.nextInt(animals.size()));
            } else if (!natives.isEmpty() && rng.chance(0.6)) {
                body = natives.get(rng.nextInt(natives.size()));
            } else if (!robots.isEmpty()) {
                body = robots.get(rng.nextInt(robots.size()));
            } else {
                continue;
            }
            long seed = rng.nextLong();
            Species s = new Species(key + ":F" + i, Species.FAUNA, Names.species(seed, body.name()), seed);
            s.body = body;
            float extra = rng.chance(0.5) ? 0 : rng.range(-50f, 50f);
            s.recolor = new Recolor(spec.ground.hueShift() + extra, rng.range(0.9f, 1.2f), 1, 0, 0);
            float roll = rng.nextFloat();
            s.scale = roll < 0.08f ? rng.range(1.8f, 2.4f) : roll < 0.3f ? rng.range(1.25f, 1.6f) : rng.range(0.8f, 1.2f);
            if (body.animal()) {
                s.temperament = rng.chance(0.6) ? Species.SKITTISH : Species.PASSIVE;
            } else {
                int aggression = spec.biome.hazardous() ? 2 : 1;
                int pick = rng.range(0, 2 + aggression);
                s.temperament = Math.min(Species.PREDATOR, Math.max(Species.SKITTISH, pick - 1));
            }
            s.health = Math.round((body.animal() ? 3 : 6 + rng.range(0, 6)) * s.scale);
            s.speed = rng.range(0.5f, 1.4f);
            s.diet = body.animal() ? pick(rng, "Herbivore,Omnivore,Seed eater,Nectar feeder")
                    : pick(rng, "Scrap eater,Lithovore,Battery grazer,Absorbs sunlight,Carnivore,Oil drinker");
            s.note = pick(rng, "Hums when content,Leaks sparks,Recently escaped a capsule,Afraid of moustaches,"
                    + "Builds nests of bolts,Sleeps upside down,Hoards rings,Hates blue hedgehogs,"
                    + "Squeaks in morse code,Migrates at dusk,Older than the planet's moon,Responds to whistling");
            s.heightM = rng.range(0.3f, 1.4f) * s.scale;
            s.weightKg = rng.range(8f, 90f) * s.scale * s.scale;
            fauna.add(s);
        }
    }

    private void buildFlora(Rng rng) {
        int[] shapes = switch (spec.biome.flora()) {
            case Biome.FLORA_FUNGAL -> new int[] {PixelArt.SHAPE_MUSHROOM, PixelArt.SHAPE_BULB, PixelArt.SHAPE_BUSH,
                    PixelArt.SHAPE_MUSHROOM};
            case Biome.FLORA_CRYSTAL -> new int[] {PixelArt.SHAPE_CRYSTAL, PixelArt.SHAPE_STALK, PixelArt.SHAPE_BULB};
            case Biome.FLORA_DESERT -> new int[] {PixelArt.SHAPE_CACTUS, PixelArt.SHAPE_BUSH, PixelArt.SHAPE_CACTUS};
            case Biome.FLORA_FROST -> new int[] {PixelArt.SHAPE_PINE, PixelArt.SHAPE_CRYSTAL, PixelArt.SHAPE_BUSH};
            case Biome.FLORA_TECH -> new int[] {PixelArt.SHAPE_STALK, PixelArt.SHAPE_BULB, PixelArt.SHAPE_CRYSTAL};
            case Biome.FLORA_CORAL -> new int[] {PixelArt.SHAPE_CORAL, PixelArt.SHAPE_FLOWER, PixelArt.SHAPE_BUSH};
            case Biome.FLORA_EMBER -> new int[] {PixelArt.SHAPE_TREE, PixelArt.SHAPE_BULB, PixelArt.SHAPE_CRYSTAL};
            default -> new int[] {PixelArt.SHAPE_TREE, PixelArt.SHAPE_BUSH, PixelArt.SHAPE_FLOWER, PixelArt.SHAPE_TREE};
        };
        float baseHue = switch (spec.biome.flora()) {
            case Biome.FLORA_EMBER -> 15;
            case Biome.FLORA_DESERT -> 90;
            case Biome.FLORA_FROST -> 170;
            case Biome.FLORA_CORAL -> 330;
            case Biome.FLORA_CRYSTAL -> 280;
            case Biome.FLORA_TECH -> 200;
            default -> 115;
        } + spec.ground.hueShift();
        for (int i = 0; i < spec.floraSpecies; i++) {
            long seed = rng.nextLong();
            int shape = shapes[i % shapes.length];
            Species s = new Species(key + ":P" + i, Species.FLORA, Names.plant(seed), seed);
            int leaf = Colour.fromHsv(baseHue + rng.range(-35f, 35f), rng.range(0.55f, 0.85f), rng.range(0.6f, 0.85f), 255);
            int accent = Colour.fromHsv(rng.range(0f, 360f), 0.8f, 0.95f, 255);
            int trunk = spec.biome.flora() == Biome.FLORA_EMBER ? 0xFF303030
                    : Colour.fromHsv(25 + rng.range(-10f, 10f), 0.55f, 0.45f, 255);
            float scale = shape == PixelArt.SHAPE_TREE || shape == PixelArt.SHAPE_PINE ? rng.range(0.9f, 1.3f)
                    : rng.range(0.8f, 1.15f);
            s.sprites = new SceneSprite[3];
            for (int v = 0; v < 3; v++) {
                s.sprites[v] = PixelArt.plant(shape, seed + v * 101, leaf, accent, trunk, scale * (0.85f + 0.15f * v));
            }
            s.colour = leaf;
            s.yield = rng.chance(0.25) ? spec.special : Catalog.CARBON;
            s.yieldCount = rng.range(8, 20);
            flora.add(s);
        }
    }

    private void buildMinerals(Art art, Rng rng) {
        int count = rng.range(2, 3);
        int rockColour = Colour.fromHsv(30 + spec.ground.hueShift() * 0.5f + rng.range(-20f, 20f), rng.range(0.1f, 0.35f),
                rng.range(0.5f, 0.7f), 255);
        for (int i = 0; i < count; i++) {
            long seed = rng.nextLong();
            Species s = new Species(key + ":M" + i, Species.MINERAL, Names.mineral(seed), seed);
            int vein = i == 0 ? 0xFFB4B4C4 : i == 1 ? itemColour(spec.starMetal) : 0xFFFFD040;
            s.sprites = new SceneSprite[3];
            for (int v = 0; v < 3; v++) {
                SceneSprite rom = v < 2 ? art.frame("rock", v + 1) : null;
                s.sprites[v] = rom != null && i == 0
                        ? art.recolored(rom, new Recolor(spec.ground.hueShift() + rng.range(-20f, 20f), 0.8f, 0.9f, 0, 0),
                        key + ":rock" + v)
                        : PixelArt.boulder(seed + v, Colour.scale(rockColour, 0.9f + 0.1f * v), vein, 0.9f + 0.25f * v);
            }
            s.colour = vein;
            s.yield = i == 0 ? Catalog.FERRITE : i == 1 ? spec.starMetal : Catalog.SILVER;
            s.yieldCount = i == 0 ? rng.range(20, 40) : rng.range(10, 24);
            minerals.add(s);
        }
    }

    private static String pick(Rng rng, String options) {
        String[] parts = options.split(",");
        return parts[rng.nextInt(parts.length)];
    }

    /** Lays out deposits, plants and points of interest along the floors. */
    private void place(Rng rng, boolean shrineHere) {
        int w = terrain.width();
        int lush = spec.dead ? 0 : spec.paradise ? 3 : spec.biome.lush();
        int n = 0;
        for (int x = 24; x < w; x += rng.range(18, 46)) {
            int[] floors = terrain.floors(x, 40);
            if (floors.length == 0) {
                continue;
            }
            int y = floors[0];
            boolean cave = false;
            if (floors.length > 1 && rng.chance(0.35)) {
                y = floors[rng.nextInt(floors.length)];
                cave = y != floors[0];
            }
            String id = key + "#" + (n++);
            int roll = rng.nextInt(100);
            if (cave) {
                if (roll < 30) {
                    placements.add(new Placement(P_COBALT, x, y, 2, id));
                } else if (roll < 55) {
                    placements.add(new Placement(P_MINERAL, x, y, rng.nextInt(minerals.size()), id));
                } else if (roll < 63) {
                    placements.add(new Placement(P_GOLD, x, y, 0, id));
                } else if (roll < 70 && !flora.isEmpty()) {
                    placements.add(new Placement(P_FLORA, x, y, rng.nextInt(flora.size()), id));
                } else if (roll < 74) {
                    placements.add(new Placement(P_MONITOR, x, y, rng.nextInt(6), id));
                }
                continue;
            }
            int floraChance = 18 + lush * 14;
            if (roll < floraChance && !flora.isEmpty()) {
                placements.add(new Placement(P_FLORA, x, y, rng.nextInt(flora.size()), id));
            } else if (roll < floraChance + 14) {
                placements.add(new Placement(P_MINERAL, x, y, rng.nextInt(minerals.size()), id));
            } else if (roll < floraChance + 20) {
                placements.add(new Placement(P_CRYSTAL, x, y, rng.chance(0.6) ? 0 : 1, id));
            } else if (roll < floraChance + 25 && !spec.dead) {
                placements.add(new Placement(P_OXYGEN, x, y, 0, id));
            } else if (roll < floraChance + 30) {
                placements.add(new Placement(P_SODIUM, x, y, 0, id));
            } else if (roll < floraChance + 34) {
                placements.add(new Placement(P_SPECIAL, x, y, 0, id));
            } else if (roll < floraChance + 38) {
                placements.add(new Placement(P_RINGS, x, y, rng.range(3, 8), id));
            } else if (roll < floraChance + 40) {
                placements.add(new Placement(P_MONITOR, x, y, rng.nextInt(6), id));
            } else if (roll < floraChance + 41) {
                placements.add(new Placement(P_GOLD, x, y, 0, id));
            }
        }
        // Points of interest, spread around the circumference.
        int pois = 4 + rng.nextInt(3);
        for (int i = 0; i < pois; i++) {
            float x = (i + rng.range(0.1f, 0.9f)) * w / pois;
            int y = terrain.topFloor(x);
            if (y < 0) {
                continue;
            }
            int kind = switch (i % 5) {
                case 0 -> P_STARPOST;
                case 1 -> P_RUINS;
                case 2 -> P_CAPSULE;
                case 3 -> rng.chance(0.5) ? P_WRECK : P_RUINS;
                default -> rng.chance(0.35) ? P_GIANT_RING : P_STARPOST;
            };
            placements.add(new Placement(kind, x, y, 0, key + "#poi" + i));
        }
        if (shrineHere) {
            float x = w * rng.range(0.3f, 0.7f);
            int y = terrain.topFloor(x);
            placements.add(new Placement(P_SHRINE, x, Math.max(0, y), 0, key + "#shrine"));
        }
        // Creatures roam from their spawn points.
        if (!fauna.isEmpty()) {
            int herds = 10 + lush * 6;
            for (int i = 0; i < herds; i++) {
                float x = rng.range(0f, w);
                int y = terrain.topFloor(x);
                if (y < 0) {
                    continue;
                }
                placements.add(new Placement(P_FAUNA, x, y, rng.nextInt(fauna.size()), key + "#f" + i));
            }
        }
        // Launch fuel must remain obtainable independently of the sparse random crystal
        // roll. Add exposed deposits throughout the circumference (three crystals yield
        // at least 42 di-hydrogen). Separate IDs and no RNG consumption preserve existing
        // placements and loot identities when an expedition made by an older build loads.
        for (int sector = 0; sector < w; sector += 512) {
            int placed = 0;
            for (int x = sector + 24; x < Math.min(w, sector + 512) && placed < 3; x += 32) {
                int y = terrain.topFloor(x);
                if (y < 40) {
                    continue;
                }
                placements.add(new Placement(P_CRYSTAL, x, y, 0, key + "#fuel" + sector + ":" + placed));
                placed++;
            }
        }
    }

    /** All species on the planet. */
    public List<Species> allSpecies() {
        List<Species> out = new ArrayList<>(fauna);
        out.addAll(flora);
        out.addAll(minerals);
        return out;
    }

    public Species species(String id) {
        for (Species s : allSpecies()) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }
}
