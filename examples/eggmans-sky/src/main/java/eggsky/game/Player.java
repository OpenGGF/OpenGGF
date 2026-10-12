package eggsky.game;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Everything about Dr. Eggman's expedition that survives a save: currencies, cargo, technology,
 * vital systems, where he is in the galaxy, what he has discovered and looted, the Chaos
 * Emeralds, missions and lifetime statistics. Saved as plain {@code key=value} lines; unknown
 * keys are ignored and optional legacy fields keep defaults. Unsupported or malformed saves
 * are rejected before publishing any state.
 */
public final class Player {
    public static final int BASE_SLOTS = 16;
    /** Capsule rewards stop granting cargo after 24 slots (SurfaceMode.openCapsule). */
    public static final int MAX_BONUS_SLOTS = 24;
    private static final int MAX_SAVE_BYTES = 1048576;

    public static int maxSlots(Catalog catalog) {
        return BASE_SLOTS + 4 * catalog.tech(Catalog.T_CARGO).max() + MAX_BONUS_SLOTS;
    }

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
    /** Positive recipe output ID, or negative technology ID minus one. */
    public int pinned;
    public final Map<Integer, Integer> reserves = new HashMap<>();
    // Session-only navigation memory; never carried between expeditions.
    public int menuTab;
    public final int[] menuRows = new int[7];
    public final int[] menuItems = new int[7];
    public boolean menuOpened;
    public int stationTab;
    public final int[] stationRows = new int[6];
    public final int[] stationItems = new int[6];

    public int available(int item) {
        return Math.max(0, cargo.count(item) - reserves.getOrDefault(item, 0));
    }

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
        put(sb, "pinned", pinned);
        StringBuilder reserved = new StringBuilder();
        reserves.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                reserved.append(e.getKey()).append(':').append(e.getValue()).append(';'));
        put(sb, "reserves", reserved);
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

    /** Returns false without changing this player for an unsupported or malformed save. */
    public boolean decode(String text) {
        if (text == null || text.length() > MAX_SAVE_BYTES
                || text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_SAVE_BYTES) return false;
        try {
            Map<String, String> values = new HashMap<>();
            for (String line : text.split("\n")) {
                if (line.isBlank()) continue;
                int eq = line.indexOf('=');
                if (eq <= 0 || values.putIfAbsent(line.substring(0, eq).trim(), line.substring(eq + 1).trim()) != null)
                    return false;
            }
            for (String required : new String[] {"version", "galaxySeed", "galaxyNumber", "system", "planet", "tech", "cargo"})
                if (!values.containsKey(required)) return false;
            if (!values.get("version").equals("1")) return false;
            Player candidate = new Player(catalog);
            candidate.read(values);
            candidate.validate();
            copyFrom(candidate);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private void read(Map<String, String> m) {
        rings = l(m, "rings", rings);
        shards = i(m, "shards", shards);
        bonusSlots = i(m, "bonusSlots", bonusSlots);
        String[] t = m.get("tech").split(",", -1);
        if (t.length != tech.length) throw new IllegalArgumentException("Technology count");
        for (int k = 0; k < tech.length; k++) {
            tech[k] = Integer.parseInt(t[k].trim());
            if (tech[k] < 0 || tech[k] > catalog.tech(k).max()) throw new IllegalArgumentException("Technology level");
        }
        if (bonusSlots < 0 || bonusSlots > MAX_BONUS_SLOTS) throw new IllegalArgumentException("Capsule slots");
        // Technology and capsule limits prove this arithmetic and allocation are bounded.
        int capacity = Math.addExact(BASE_SLOTS, Math.addExact(Math.multiplyExact(4, tech[Catalog.T_CARGO]), bonusSlots));
        if (!Inventory.validEncoding(catalog, m.get("cargo"), capacity)) throw new IllegalArgumentException("Cargo");
        cargo.resize(capacity);
        cargo.decode(m.get("cargo"));
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
            if (!s.isEmpty() && !visitedSystems.add(Long.parseLong(s))) throw new IllegalArgumentException("Duplicate system visit");
        }
        set(visitedPlanets, m.get("visitedPlanets"));
        names.clear();
        for (String name : m.getOrDefault("names", "").split(";")) {
            if (name.isEmpty()) continue;
            int gt = name.indexOf('>');
            if (gt <= 0 || names.putIfAbsent(name.substring(0, gt), name.substring(gt + 1)) != null)
                throw new IllegalArgumentException("Discovery names");
        }
        emeralds = i(m, "emeralds", emeralds);
        shrineSystem = l(m, "shrineSystem", shrineSystem);
        shrinePlanet = i(m, "shrinePlanet", shrinePlanet);
        tutorial = i(m, "tutorial", tutorial);
        pinned = i(m, "pinned", 0);
        if (pinned < -Catalog.TECH_COUNT || pinned > 0
                && catalog.recipes().stream().noneMatch(r -> r.output() == pinned)) {
            pinned = 0;
        }
        reserves.clear();
        for (String entry : m.getOrDefault("reserves", "").split(";")) {
            if (entry.isEmpty()) continue;
            String[] pair = entry.split(":", -1);
            if (pair.length != 2) throw new IllegalArgumentException("Cargo reserve");
            int id = Integer.parseInt(pair[0]), amount = Integer.parseInt(pair[1]);
            Catalog.Item definition = catalog.item(id);
            // Reserve Stock records held cargo; no earned reserve can exceed all available slots.
            if (definition == null || amount <= 0 || amount > maxSlots(catalog) * definition.stack()
                    || reserves.putIfAbsent(id, amount) != null) throw new IllegalArgumentException("Cargo reserve");
        }
        missions = m.getOrDefault("missions", missions);
        String core = m.getOrDefault("coreReached", "false");
        if (!core.equals("true") && !core.equals("false")) throw new IllegalArgumentException("Core state");
        coreReached = Boolean.parseBoolean(core);
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
                    if (!target.add(s)) throw new IllegalArgumentException("Duplicate progress entry");
                }
            }
        }
    }

    private void validate() {
        if (galaxyNumber < 1 || planet < -1 || planet > 4 || beaconPlanet < -1 || beaconPlanet > 4
                || gravePlanet < -1 || gravePlanet > 4 || shrinePlanet < -1 || shrinePlanet > 4
                || emeralds < 0 || emeralds > 127 || tutorial < 0 || rings < 0 || shards < 0
                || statWarps < 0 || statPlanets < 0 || statSpecies < 0 || statMined < 0
                || statSonicRepelled < 0 || statDeaths < 0 || statRingsEarned < 0 || statPirates < 0 || playTicks < 0
                || hull < 0 || shield < 0 || life < 0 || hazard < 0 || jet < 0 || launchFuel < 0 || shipShield < 0
                || !Inventory.validEncoding(catalog, graveCargo, maxSlots(catalog)))
            throw new IllegalArgumentException("Expedition state");
    }

    private static int i(Map<String, String> m, String k, int d) {
        return m.containsKey(k) ? Integer.parseInt(m.get(k)) : d;
    }

    private static long l(Map<String, String> m, String k, long d) {
        return m.containsKey(k) ? Long.parseLong(m.get(k)) : d;
    }

    private static float f(Map<String, String> m, String k, float d) {
        float value = m.containsKey(k) ? Float.parseFloat(m.get(k)) : d;
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite " + k);
        return value;
    }

    private void copyFrom(Player candidate) {
        cargo.replaceWith(candidate.cargo);
        System.arraycopy(candidate.tech, 0, tech, 0, tech.length);
        System.arraycopy(candidate.menuRows, 0, menuRows, 0, menuRows.length);
        System.arraycopy(candidate.menuItems, 0, menuItems, 0, menuItems.length);
        System.arraycopy(candidate.stationRows, 0, stationRows, 0, stationRows.length);
        System.arraycopy(candidate.stationItems, 0, stationItems, 0, stationItems.length);
        rings = candidate.rings;
        shards = candidate.shards;
        hull = candidate.hull;
        shield = candidate.shield;
        life = candidate.life;
        hazard = candidate.hazard;
        jet = candidate.jet;
        launchFuel = candidate.launchFuel;
        shipShield = candidate.shipShield;
        pulse = candidate.pulse;
        galaxySeed = candidate.galaxySeed;
        galaxyNumber = candidate.galaxyNumber;
        systemId = candidate.systemId;
        planet = candidate.planet;
        surfaceX = candidate.surfaceX;
        surfaceY = candidate.surfaceY;
        spaceX = candidate.spaceX;
        spaceY = candidate.spaceY;
        spaceZ = candidate.spaceZ;
        spaceYaw = candidate.spaceYaw;
        spacePitch = candidate.spacePitch;
        beaconPlanet = candidate.beaconPlanet;
        beaconX = candidate.beaconX;
        beaconY = candidate.beaconY;
        emeralds = candidate.emeralds;
        bonusSlots = candidate.bonusSlots;
        shrineSystem = candidate.shrineSystem;
        shrinePlanet = candidate.shrinePlanet;
        tutorial = candidate.tutorial;
        pinned = candidate.pinned;
        menuTab = candidate.menuTab;
        menuOpened = candidate.menuOpened;
        stationTab = candidate.stationTab;
        missions = candidate.missions;
        coreReached = candidate.coreReached;
        statWarps = candidate.statWarps;
        statPlanets = candidate.statPlanets;
        statSpecies = candidate.statSpecies;
        statMined = candidate.statMined;
        statSonicRepelled = candidate.statSonicRepelled;
        statDeaths = candidate.statDeaths;
        statRingsEarned = candidate.statRingsEarned;
        statPirates = candidate.statPirates;
        playTicks = candidate.playTicks;
        graveSystem = candidate.graveSystem;
        gravePlanet = candidate.gravePlanet;
        graveX = candidate.graveX;
        graveY = candidate.graveY;
        graveCargo = candidate.graveCargo;
        discovered.clear();
        discovered.addAll(candidate.discovered);
        uploaded.clear();
        uploaded.addAll(candidate.uploaded);
        looted.clear();
        looted.addAll(candidate.looted);
        visitedSystems.clear();
        visitedSystems.addAll(candidate.visitedSystems);
        visitedPlanets.clear();
        visitedPlanets.addAll(candidate.visitedPlanets);
        names.clear();
        names.putAll(candidate.names);
        reserves.clear();
        reserves.putAll(candidate.reserves);
    }

    public Catalog catalog() {
        return catalog;
    }
}
