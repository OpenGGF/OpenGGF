package eggsky.game;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Everything about Dr. Eggman's expedition that survives a save: currencies, cargo, technology,
 * vital systems, where he is in the galaxy, what he has discovered and looted, the Chaos
 * Emeralds, missions and lifetime statistics. Saved as plain {@code key=value} lines; unknown
 * keys are ignored and missing ones keep their defaults, so saves stay compatible.
 */
public final class Player {
    public static final int BASE_SLOTS = 16;

    private final Catalog catalog;
    public long rings = 250;
    public int shards = 0;
    public final Inventory cargo;
    public final int[] tech = new int[Catalog.TECH_COUNT];

    // Vital systems (current values; maxima come from technology).
    public float hull = 100;
    public float shield = 100;
    public float life = 100;
    public float hazard = 100;
    public float jet = 100;
    public float launchFuel = 50;
    public float shipShield = 100;
    public float pulse = 100;

    // Where Eggman is.
    public long galaxySeed;
    public int galaxyNumber = 1;
    public long systemId;
    /** Planet index on a surface, or -1 in space. */
    public int planet = -1;
    public float surfaceX;
    public float surfaceY;
    public float spaceX;
    public float spaceY;
    public float spaceZ;
    public float spaceYaw;
    public float spacePitch;
    /** The planet and position of the last save beacon (respawn point), -1 for none. */
    public int beaconPlanet = -1;
    public float beaconX;
    public float beaconY;

    // Progress.
    public final Set<String> discovered = new LinkedHashSet<>();
    public final Set<String> uploaded = new LinkedHashSet<>();
    public final Set<String> looted = new LinkedHashSet<>();
    public final Set<Long> visitedSystems = new LinkedHashSet<>();
    public final Set<String> visitedPlanets = new LinkedHashSet<>();
    public final Map<String, String> names = new HashMap<>();
    /** Chaos Emeralds found, one bit each (7 for the set). */
    public int emeralds;
    /** Extra cargo slots from opened Egg Capsules. */
    public int bonusSlots;
    /** The system and planet holding the next emerald shrine, once a signal has revealed it. */
    public long shrineSystem = Long.MIN_VALUE;
    public int shrinePlanet = -1;
    public int tutorial;
    public String missions = "";
    public boolean coreReached;

    // Lifetime statistics.
    public int statWarps;
    public int statPlanets;
    public int statSpecies;
    public int statMined;
    public int statSonicRepelled;
    public int statDeaths;
    public long statRingsEarned;
    public int statPirates;
    public long playTicks;

    // Grave: cargo dropped at death, recoverable on that planet.
    public long graveSystem = Long.MIN_VALUE;
    public int gravePlanet = -1;
    public float graveX;
    public float graveY;
    public String graveCargo = "";

    public Player(Catalog catalog) {
        this.catalog = catalog;
        this.cargo = new Inventory(catalog, BASE_SLOTS);
    }

    public int level(int techId) {
        return tech[techId];
    }

    public float maxHull() {
        return 100 + 25 * tech[Catalog.T_HULL];
    }

    public float maxShield() {
        return tech[Catalog.T_SHIELD] == 0 ? 0 : 40 + 25 * tech[Catalog.T_SHIELD];
    }

    public float maxLife() {
        return 100 * (1 + 0.3f * tech[Catalog.T_LIFE]);
    }

    public float maxHazard() {
        return 100;
    }

    public float maxJet() {
        return 100 * (1 + 0.35f * tech[Catalog.T_JETS]);
    }

    public float maxShipShield() {
        return 100 * (1 + 0.4f * tech[Catalog.T_DEFLECTOR]);
    }

    public int slots() {
        return BASE_SLOTS + 4 * tech[Catalog.T_CARGO] + bonusSlots;
    }

    /** Hyperdrive range in light years. */
    public float warpRange() {
        return 180 + 120 * tech[Catalog.T_HYPERDRIVE];
    }

    public boolean canReach(int starClass) {
        return switch (starClass) {
            case eggsky.world.StarSystem.RED -> tech[Catalog.T_RED_DRIVE] > 0;
            case eggsky.world.StarSystem.GREEN -> tech[Catalog.T_GREEN_DRIVE] > 0;
            case eggsky.world.StarSystem.BLUE -> tech[Catalog.T_BLUE_DRIVE] > 0;
            default -> true;
        };
    }

    public int emeraldCount() {
        return Integer.bitCount(emeralds & 0x7F);
    }

    /** Refills every vital system (new expedition, respawn). */
    public void refill() {
        hull = maxHull();
        shield = maxShield();
        life = maxLife();
        hazard = maxHazard();
        jet = maxJet();
        shipShield = maxShipShield();
        pulse = 100;
    }

    // ---------------------------------------------------------------- saving

    public String encode() {
        StringBuilder sb = new StringBuilder();
        put(sb, "version", "1");
        put(sb, "rings", rings);
        put(sb, "shards", shards);
        put(sb, "cargo", cargo.encode());
        StringBuilder t = new StringBuilder();
        for (int i = 0; i < tech.length; i++) {
            t.append(i > 0 ? "," : "").append(tech[i]);
        }
        put(sb, "tech", t);
        put(sb, "hull", hull);
        put(sb, "shield", shield);
        put(sb, "life", life);
        put(sb, "hazard", hazard);
        put(sb, "jet", jet);
        put(sb, "launchFuel", launchFuel);
        put(sb, "shipShield", shipShield);
        put(sb, "galaxySeed", galaxySeed);
        put(sb, "galaxyNumber", galaxyNumber);
        put(sb, "system", systemId);
        put(sb, "planet", planet);
        put(sb, "surfaceX", surfaceX);
        put(sb, "surfaceY", surfaceY);
        put(sb, "spaceX", spaceX);
        put(sb, "spaceY", spaceY);
        put(sb, "spaceZ", spaceZ);
        put(sb, "spaceYaw", spaceYaw);
        put(sb, "spacePitch", spacePitch);
        put(sb, "beaconPlanet", beaconPlanet);
        put(sb, "beaconX", beaconX);
        put(sb, "beaconY", beaconY);
        put(sb, "discovered", String.join(";", discovered));
        put(sb, "uploaded", String.join(";", uploaded));
        put(sb, "looted", String.join(";", looted));
        StringBuilder v = new StringBuilder();
        for (long id : visitedSystems) {
            v.append(v.length() > 0 ? ";" : "").append(id);
        }
        put(sb, "visitedSystems", v);
        put(sb, "visitedPlanets", String.join(";", visitedPlanets));
        StringBuilder n = new StringBuilder();
        for (Map.Entry<String, String> e : names.entrySet()) {
            n.append(n.length() > 0 ? ";" : "").append(e.getKey()).append('>').append(e.getValue());
        }
        put(sb, "names", n);
        put(sb, "emeralds", emeralds);
        put(sb, "bonusSlots", bonusSlots);
        put(sb, "shrineSystem", shrineSystem);
        put(sb, "shrinePlanet", shrinePlanet);
        put(sb, "tutorial", tutorial);
        put(sb, "missions", missions);
        put(sb, "coreReached", coreReached);
        put(sb, "statWarps", statWarps);
        put(sb, "statPlanets", statPlanets);
        put(sb, "statSpecies", statSpecies);
        put(sb, "statMined", statMined);
        put(sb, "statSonicRepelled", statSonicRepelled);
        put(sb, "statDeaths", statDeaths);
        put(sb, "statRingsEarned", statRingsEarned);
        put(sb, "statPirates", statPirates);
        put(sb, "playTicks", playTicks);
        put(sb, "graveSystem", graveSystem);
        put(sb, "gravePlanet", gravePlanet);
        put(sb, "graveX", graveX);
        put(sb, "graveY", graveY);
        put(sb, "graveCargo", graveCargo);
        return sb.toString();
    }

    private static void put(StringBuilder sb, String key, Object value) {
        sb.append(key).append('=').append(String.valueOf(value).replace('\n', ' ')).append('\n');
    }

    public void decode(String text) {
        Map<String, String> m = new HashMap<>();
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                m.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        }
        rings = l(m, "rings", rings);
        shards = i(m, "shards", shards);
        bonusSlots = i(m, "bonusSlots", bonusSlots);
        String[] t = m.getOrDefault("tech", "").split(",");
        for (int k = 0; k < t.length && k < tech.length; k++) {
            try {
                tech[k] = Integer.parseInt(t[k].trim());
            } catch (NumberFormatException ignored) {
                tech[k] = 0;
            }
        }
        cargo.resize(slots());
        cargo.decode(m.get("cargo"));
        cargo.resize(slots());
        hull = f(m, "hull", hull);
        shield = f(m, "shield", shield);
        life = f(m, "life", life);
        hazard = f(m, "hazard", hazard);
        jet = f(m, "jet", jet);
        launchFuel = f(m, "launchFuel", launchFuel);
        shipShield = f(m, "shipShield", shipShield);
        galaxySeed = l(m, "galaxySeed", galaxySeed);
        galaxyNumber = i(m, "galaxyNumber", galaxyNumber);
        systemId = l(m, "system", systemId);
        planet = i(m, "planet", planet);
        surfaceX = f(m, "surfaceX", surfaceX);
        surfaceY = f(m, "surfaceY", surfaceY);
        spaceX = f(m, "spaceX", spaceX);
        spaceY = f(m, "spaceY", spaceY);
        spaceZ = f(m, "spaceZ", spaceZ);
        spaceYaw = f(m, "spaceYaw", spaceYaw);
        spacePitch = f(m, "spacePitch", spacePitch);
        beaconPlanet = i(m, "beaconPlanet", beaconPlanet);
        beaconX = f(m, "beaconX", beaconX);
        beaconY = f(m, "beaconY", beaconY);
        set(discovered, m.get("discovered"));
        set(uploaded, m.get("uploaded"));
        set(looted, m.get("looted"));
        visitedSystems.clear();
        for (String s : m.getOrDefault("visitedSystems", "").split(";")) {
            try {
                if (!s.isEmpty()) {
                    visitedSystems.add(Long.parseLong(s));
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        set(visitedPlanets, m.get("visitedPlanets"));
        names.clear();
        for (String s : m.getOrDefault("names", "").split(";")) {
            int gt = s.indexOf('>');
            if (gt > 0) {
                names.put(s.substring(0, gt), s.substring(gt + 1));
            }
        }
        emeralds = i(m, "emeralds", emeralds);
        shrineSystem = l(m, "shrineSystem", shrineSystem);
        shrinePlanet = i(m, "shrinePlanet", shrinePlanet);
        tutorial = i(m, "tutorial", tutorial);
        missions = m.getOrDefault("missions", missions);
        coreReached = Boolean.parseBoolean(m.getOrDefault("coreReached", "false"));
        statWarps = i(m, "statWarps", statWarps);
        statPlanets = i(m, "statPlanets", statPlanets);
        statSpecies = i(m, "statSpecies", statSpecies);
        statMined = i(m, "statMined", statMined);
        statSonicRepelled = i(m, "statSonicRepelled", statSonicRepelled);
        statDeaths = i(m, "statDeaths", statDeaths);
        statRingsEarned = l(m, "statRingsEarned", statRingsEarned);
        statPirates = i(m, "statPirates", statPirates);
        playTicks = l(m, "playTicks", playTicks);
        graveSystem = l(m, "graveSystem", graveSystem);
        gravePlanet = i(m, "gravePlanet", gravePlanet);
        graveX = f(m, "graveX", graveX);
        graveY = f(m, "graveY", graveY);
        graveCargo = m.getOrDefault("graveCargo", graveCargo);
    }

    private static void set(Set<String> target, String value) {
        target.clear();
        if (value != null) {
            for (String s : value.split(";")) {
                if (!s.isEmpty()) {
                    target.add(s);
                }
            }
        }
    }

    private static int i(Map<String, String> m, String k, int d) {
        try {
            return Integer.parseInt(m.get(k));
        } catch (RuntimeException e) {
            return d;
        }
    }

    private static long l(Map<String, String> m, String k, long d) {
        try {
            return Long.parseLong(m.get(k));
        } catch (RuntimeException e) {
            return d;
        }
    }

    private static float f(Map<String, String> m, String k, float d) {
        try {
            float v = Float.parseFloat(m.get(k));
            return Float.isFinite(v) ? v : d;
        } catch (RuntimeException e) {
            return d;
        }
    }

    public Catalog catalog() {
        return catalog;
    }
}
