package survivors;

import com.openggf.mods.state.VersionedSettings;
import com.openggf.mods.ModStorage;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Permanent progress kept between runs in owner-scoped {@code profile.txt}: the ring
 * bank and the shop levels it buys, the Chaos Emeralds won from zone bosses, the furthest zone a
 * run may start from, and lifetime records. Saved as plain {@code key=value} lines whenever it
 * changes; an unreadable file starts a fresh profile rather than failing the game.
 */
final class Profile {
    // Shop items, bought with banked rings in camp.
    static final int SHOP_POWER = 0, SHOP_RINGS = 1, SHOP_REROLL = 2, SHOP_MAGNET = 3, SHOP_GROWTH = 4,
            SHOP_REVIVAL = 5, SHOP_TALENT = 6, SHOP_ARMOR = 7, SHOP_TREASURE = 8, SHOP_HEAD_START = 9;
    static final int SHOP_COUNT = 10;
    /** Zone bosses EHZ to OOZ each award one emerald, once. */
    static final int EMERALDS = 7;

    private final ModStorage storage;
    private boolean loaded;
    private boolean writable = true;
    private boolean dirty;
    private int retryFrames;
    static final int FORMAT_VERSION = 1;
    /** Durable allocation cursor; current-graph snapshots carry these positive identities. */
    private long issuedRuns;
    private final RunIds startedRuns = new RunIds();
    private final RunIds settledRuns = new RunIds();
    /** Shop scale version: 2 is the small-increment shop. Older saves convert once on load. */
    static final int SHOP_VERSION = 2;
    int shopVersion;
    int bank;
    final int[] shop = new int[SHOP_COUNT];
    int emeralds;
    /** Highest stage index a run may start from (0 = Emerald Hill only). */
    int unlocked;
    int mode;
    int runs, wins, bestStages, bestKills, totalKills, bestCombo, bankedTotal;
    int totalElites, bestLevel;
    /** Eggman's Rules selected in camp ({@link Rules} bits), applied to each new run. */
    int rules;
    /** Evolutions ever performed, by {@link Upgrades} evolution index. */
    int evolutionsSeen;
    /** Unlocks already announced, so each milestone is celebrated once. */
    int announcedUnlocks;
    /** Badniks defeated per {@link Species} id: the bestiary. */
    final int[] speciesKills = new int[Species.COUNT];
    /** Longest unlimited survival per route stage, in seconds. */
    final int[] bestUnlimited = new int[Stages.COUNT];

    Profile(ModStorage storage) { this.storage = java.util.Objects.requireNonNull(storage); }

    /** Reserve before the graph's first capture, never from ordinary level initialization. */
    long reserveRun() {
        issuedRuns = nextIdentity();
        // Reservation is not progress. Publish its cursor together with the first
        // counted start or another real profile edit, preserving absent/legacy files.
        return issuedRuns;
    }

    /** Only a deliberate fresh-run command creates a successor; replay reuses that command. */
    long successor(long previous) {
        requireRun(previous);
        // One graph has one captured run lineage. The same captured fresh-run command
        // has the same child; a later real command advances from that child in turn.
        if (previous >= Long.MAX_VALUE - 1) throw new IllegalStateException("Run identities exhausted");
        long next = previous + 1;
        if (next > issuedRuns) {
            issuedRuns = next;
        } else retrySave();
        return next;
    }

    void startRun(long id) {
        requireRun(id);
        if (startedRuns.add(id)) {
            runs++;
            save();
        } else retrySave();
    }

    record Settlement(int banked, int unlocks) { }

    /** Award and its settled identity are one atomic profile generation, outside rollback. */
    Settlement settle(RunState run, int banked, boolean won, int unlimitedStage, int seconds) {
        requireRun(run.id);
        if (!startedRuns.contains(run.id)) throw new IllegalStateException("Run was not started");
        if (!settledRuns.add(run.id)) {
            retrySave();
            return new Settlement(0, 0);
        }
        int pool = upgradePool(run.kills, run.bestCombo, run.level + 1, run.elitesDefeated, 0, banked);
        int fresh = pool & ~announcedUnlocks;
        announcedUnlocks |= pool;
        bank += banked;
        bankedTotal += banked;
        totalKills += run.kills;
        totalElites += run.elitesDefeated;
        for (int i = 0; i < Species.COUNT; i++) speciesKills[i] += run.speciesKills[i];
        bestKills = Math.max(bestKills, run.kills);
        bestCombo = Math.max(bestCombo, run.bestCombo);
        bestStages = Math.max(bestStages, run.stagesCleared);
        bestLevel = Math.max(bestLevel, run.level + 1);
        if (unlimitedStage >= 0) bestUnlimited[unlimitedStage] = Math.max(bestUnlimited[unlimitedStage], seconds);
        if (won) wins++;
        save();
        return new Settlement(banked, fresh);
    }

    private long nextIdentity() {
        if (issuedRuns >= Long.MAX_VALUE - 1) throw new IllegalStateException("Run identities exhausted");
        return issuedRuns + 1;
    }

    private void requireRun(long id) {
        if (id <= 0 || id > issuedRuns) throw new IllegalArgumentException("Unknown run identity");
    }

    /** Non-rewound retry pacing; held menus still give a failed generation another chance. */
    void tickPersistence() {
        if (dirty && --retryFrames <= 0) save();
    }

    private void retrySave() { if (dirty) save(); }

    /** Compact exact intervals retain unpaid gaps and support older seeks after newer awards. */
    private static final class RunIds {
        private final TreeMap<Long, Long> ranges = new TreeMap<>();
        boolean contains(long id) {
            var before = ranges.floorEntry(id);
            return before != null && before.getValue() >= id;
        }
        boolean add(long id) {
            if (contains(id)) return false;
            addRange(id, id);
            return true;
        }
        void addRange(long first, long last) {
            var before = ranges.floorEntry(first);
            if (before != null && before.getValue() >= first - 1) {
                first = before.getKey();
                last = Math.max(last, before.getValue());
                ranges.remove(before.getKey());
            }
            var after = ranges.ceilingEntry(first);
            while (after != null && after.getKey() <= last + 1) {
                last = Math.max(last, after.getValue());
                ranges.remove(after.getKey());
                after = ranges.ceilingEntry(first);
            }
            ranges.put(first, last);
        }
        String encode() {
            var out = new StringBuilder();
            ranges.forEach((first, last) -> {
                if (!out.isEmpty()) out.append(',');
                out.append(first).append(':').append(last);
            });
            return out.toString();
        }
        void decode(String text, long issued) {
            if (text == null) throw new IllegalArgumentException("Missing run ledger");
            if (text.isEmpty()) return;
            try {
                for (String range : text.split(",", -1)) {
                    String[] ends = range.split(":", -1);
                    if (ends.length != 2) throw new IllegalArgumentException("Invalid run ledger");
                    long first = Long.parseLong(ends[0]), last = Long.parseLong(ends[1]);
                    if (first <= 0 || last < first || last > issued) throw new IllegalArgumentException("Invalid run interval");
                    addRange(first, last);
                }
            } catch (NumberFormatException bad) { throw new IllegalArgumentException("Invalid run ledger", bad); }
        }
    }


    static String shopName(int item) {
        return switch (item) {
            case SHOP_POWER -> "POWER UP";
            case SHOP_RINGS -> "RING START";
            case SHOP_REROLL -> "REROLL";
            case SHOP_MAGNET -> "MAGNET";
            case SHOP_GROWTH -> "GROWTH";
            case SHOP_REVIVAL -> "REVIVAL";
            case SHOP_ARMOR -> "ARMOR PLATING";
            case SHOP_TREASURE -> "TREASURE HUNTER";
            case SHOP_HEAD_START -> "HEAD START";
            default -> "TALENT";
        };
    }

    /** The per-level effect, shown beside each item. */
    static String shopEffect(int item) {
        return switch (item) {
            case SHOP_POWER -> "+3% DAMAGE";
            case SHOP_RINGS -> "+5 STARTING RINGS";
            case SHOP_REROLL -> "+1 REROLL EACH ZONE";
            case SHOP_MAGNET -> "+4PX RING PULL";
            case SHOP_GROWTH -> "+3% XP";
            case SHOP_REVIVAL -> "+1 REVIVE PER RUN";
            case SHOP_ARMOR -> "HIT TOLL -2%";
            case SHOP_TREASURE -> "+1 BOSS CHEST PRIZE";
            case SHOP_HEAD_START -> "+1 LEVEL UP AT START";
            default -> "4 CARDS PER LEVEL UP";
        };
    }

    /** Small increments with a long tail: most items run to 99, priced to grow by 15% a level. */
    static int shopMax(int item) {
        return switch (item) {
            case SHOP_POWER, SHOP_GROWTH -> 99;
            case SHOP_RINGS -> 40;
            case SHOP_MAGNET -> 25;
            case SHOP_ARMOR -> 15;
            case SHOP_HEAD_START -> 10;
            case SHOP_REROLL, SHOP_TREASURE -> 3;
            case SHOP_REVIVAL -> 2;
            default -> 1;
        };
    }

    static int shopCost(int item, int level) {
        double base, growth;
        switch (item) {
            case SHOP_POWER -> { base = 50; growth = 1.15; }
            case SHOP_GROWTH -> { base = 60; growth = 1.15; }
            case SHOP_RINGS -> { base = 30; growth = 1.15; }
            case SHOP_MAGNET -> { base = 40; growth = 1.15; }
            case SHOP_ARMOR -> { base = 80; growth = 1.18; }
            case SHOP_HEAD_START -> { base = 150; growth = 1.4; }
            case SHOP_REROLL -> { base = 200; growth = 3; }
            case SHOP_REVIVAL -> { base = 500; growth = 3; }
            case SHOP_TREASURE -> { base = 400; growth = 3; }
            default -> { base = 1500; growth = 1; }
        }
        double cost = base * Math.pow(growth, level);
        return (int) Math.min(Integer.MAX_VALUE / 2, Math.round(cost / 5) * 5);
    }

    /** What the emerald from stage {@code index}'s boss grants on every later run. */
    static String emeraldEffect(int index) {
        return switch (index) {
            case 0 -> "A FREE LEVEL UP AT THE START";
            case 1 -> "+1 REROLL EACH ZONE";
            case 2 -> "COMBO MULTIPLIER CAP +2X";
            case 3 -> "+1 RING FROM EVERY BADNIK";
            case 4 -> "HITS COST 15% FEWER RINGS";
            case 5 -> "A SHIELD AT EVERY ZONE START";
            default -> "+1 REVIVE PER RUN";
        };
    }

    static String emeraldName(int index) {
        return switch (index) {
            case 0 -> "GREEN";
            case 1 -> "YELLOW";
            case 2 -> "BLUE";
            case 3 -> "PINK";
            case 4 -> "RED";
            case 5 -> "GREY";
            default -> "CYAN";
        };
    }

    boolean hasEmerald(int index) { return (emeralds & 1 << index) != 0; }
    int emeraldCount() { return Integer.bitCount(emeralds & 0x7F); }
    boolean allEmeralds() { return emeraldCount() == EMERALDS; }

    /** Old profiles already record a first clear in their route unlocks or emeralds. */
    boolean extendedModesUnlocked() { return unlocked > 0 || emeralds != 0 || wins > 0; }

    int selectedMode() { return extendedModesUnlocked() ? mode : RunState.STANDARD; }

    boolean cycleMode() {
        if (!extendedModesUnlocked()) return false;
        mode = (mode + 1) % 3;
        save();
        return true;
    }

    /** Rules unlock with the longer modes, after a first boss clear. */
    int selectedRules() { return extendedModesUnlocked() ? rules & (1 << Rules.COUNT) - 1 : 0; }

    boolean toggleRule(int rule) {
        if (!extendedModesUnlocked()) return false;
        rules ^= 1 << rule;
        save();
        return true;
    }

    /**
     * Upgrade unlocks earned so far ({@link Upgrades} id bits), including progress the run in
     * hand has made but not yet banked. Milestones read lifetime records, so existing profiles
     * keep everything they have already achieved.
     */
    int upgradePool(int runKills, int runCombo, int runLevel, int runElites, int runUnlocked, int runBanked) {
        int kills = totalKills + runKills, combo = Math.max(bestCombo, runCombo), level = Math.max(bestLevel, runLevel);
        int elites = totalElites + runElites, cleared = Math.max(unlocked, runUnlocked), banked = bankedTotal + runBanked;
        int mask = 0;
        if (kills >= 300) mask |= 1 << Upgrades.CHAIN_ZAP;
        if (cleared >= 1) mask |= 1 << Upgrades.FLICKIES;
        if (combo >= 15) mask |= 1 << Upgrades.HOMING_DASH;
        if (cleared >= 2) mask |= 1 << Upgrades.GROUND_POUND;
        if (combo >= 25) mask |= 1 << Upgrades.COMBO_KEEPER;
        if (cleared >= 3) mask |= 1 << Upgrades.BARRIER;
        if (kills >= 1500) mask |= 1 << Upgrades.TWIN_LANCE;
        if (cleared >= 5) mask |= 1 << Upgrades.METEOR;
        if (cleared >= 4) mask |= 1 << Upgrades.PULSE;
        if (elites >= 20) mask |= 1 << Upgrades.REACH;
        if (banked >= 1500) mask |= 1 << Upgrades.RECOVERY;
        if (level >= 20) mask |= 1 << Upgrades.SCHOLAR;
        return mask;
    }

    int upgradePool() { return upgradePool(0, 0, 0, 0, 0, 0); }

    /** Settled run counters already belong to the lifetime totals, even during an older seek. */
    int upgradePool(RunState run, int runUnlocked, int runBanked) {
        boolean counted = settledRuns.contains(run.id);
        return upgradePool(counted ? 0 : run.kills, run.bestCombo, run.level + 1,
                counted ? 0 : run.elitesDefeated, runUnlocked, counted ? 0 : runBanked);
    }

    /** Claims unlocks not yet announced; returns them (id bits). */
    int claimNewUnlocks(int pool) {
        int fresh = pool & ~announcedUnlocks;
        announcedUnlocks |= pool;
        if (fresh != 0) save();
        return fresh;
    }

    int unlockedUpgrades() {
        int count = 0, pool = upgradePool();
        for (int id = 0; id < Upgrades.COUNT; id++) if (Upgrades.core(id) || (pool & 1 << id) != 0) count++;
        return count;
    }

    boolean canBuy(int item) {
        return shop[item] < shopMax(item) && bank >= shopCost(item, shop[item]);
    }

    boolean buy(int item) {
        if (!canBuy(item)) return false;
        bank -= shopCost(item, shop[item]);
        shop[item]++;
        save();
        return true;
    }

    Profile load() {
        if (loaded) return this;
        loaded = true;
        var text = storage.read("profile.txt");
        if (text.isEmpty()) return this;
        try {
            var settings = VersionedSettings.parse(text.orElseThrow());
            int format = settings.version();
            if (format != 0 && format != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported profile version");
            if (format == FORMAT_VERSION) {
                try { issuedRuns = Long.parseLong(settings.entries().getOrDefault("runIssued", "-1")); }
                catch (NumberFormatException bad) { throw new IllegalArgumentException("Invalid run allocation cursor", bad); }
                if (issuedRuns < 0 || issuedRuns == Long.MAX_VALUE) throw new IllegalArgumentException("Invalid run allocation cursor");
                startedRuns.decode(settings.entries().get("runStarted"), issuedRuns);
                settledRuns.decode(settings.entries().get("runSettled"), issuedRuns);
                // A bundle application replaces Profile and RunState together. RunState rejects
                // snapshots from the prior graph before restoring simulation. Only this actual
                // graph boundary closes its old gaps; level loads never compact live ledgers.
                if (issuedRuns > 0) {
                    startedRuns.addRange(1, issuedRuns);
                    settledRuns.addRange(1, issuedRuns);
                }
            }
            // Unversioned/format-zero profiles migrate explicitly; their lifetime totals stay intact.
            for (var entry : settings.entries().entrySet()) {
                String key = entry.getKey();
                int value;
                try { value = Integer.parseInt(entry.getValue()); }
                catch (NumberFormatException bad) { continue; }
                switch (key) {
                    case "mode" -> mode = value >= RunState.STANDARD && value <= RunState.ENDLESS
                            ? value : RunState.STANDARD;
                    case "bank" -> bank = Math.max(0, value);
                    case "emeralds" -> emeralds = value & 0x7F;
                    case "unlocked" -> unlocked = Math.max(0, Math.min(Stages.COUNT - 1, value));
                    case "runs" -> runs = value;
                    case "wins" -> wins = value;
                    case "bestStages" -> bestStages = value;
                    case "bestKills" -> bestKills = value;
                    case "totalKills" -> totalKills = value;
                    case "bestCombo" -> bestCombo = value;
                    case "bankedTotal" -> bankedTotal = value;
                    case "totalElites" -> totalElites = Math.max(0, value);
                    case "bestLevel" -> bestLevel = Math.max(0, value);
                    case "rules" -> rules = value & (1 << Rules.COUNT) - 1;
                    case "evolutionsSeen" -> evolutionsSeen = value & (1 << Upgrades.EVOLUTIONS) - 1;
                    case "announcedUnlocks" -> announcedUnlocks = value;
                    case "shopVersion" -> shopVersion = value;
                    default -> {
                        if (key.startsWith("kills.") || key.startsWith("unlimited.")) {
                            try {
                                int[] target = key.startsWith("kills.") ? speciesKills : bestUnlimited;
                                int index = Integer.parseInt(key.substring(key.indexOf('.') + 1));
                                if (index >= 0 && index < target.length) target[index] = Math.max(0, value);
                            } catch (NumberFormatException ignored) { }
                        }
                        if (key.startsWith("shop.")) {
                            try {
                                int item = Integer.parseInt(key.substring(5));
                                if (item >= 0 && item < SHOP_COUNT) shop[item] = Math.max(0, Math.min(shopMax(item), value));
                            } catch (NumberFormatException ignored) { }
                        }
                    }
                }
            }
        } catch (IllegalArgumentException unsupportedFormat) {
            writable = false;
            issuedRuns = 0;
            startedRuns.ranges.clear();
            settledRuns.ranges.clear();
            Logger.getLogger(Profile.class.getName()).log(Level.WARNING, "Unsupported owned Survivors profile format", unsupportedFormat);
        } catch (RuntimeException e) {
            Logger.getLogger(Profile.class.getName()).log(Level.WARNING, "Could not read owned Survivors profile", e);
        }
        if (shopVersion < SHOP_VERSION) convertShop();
        return this;
    }

    /**
     * The first shop sold +10% damage, +10 rings, +16 px pull and +10% XP a level. Keep what an
     * older save paid for by converting to the same total on the small-increment scale.
     */
    void convertShop() {
        shop[SHOP_POWER] = Math.min(shopMax(SHOP_POWER), (shop[SHOP_POWER] * 10 + 2) / 3);
        shop[SHOP_RINGS] = Math.min(shopMax(SHOP_RINGS), shop[SHOP_RINGS] * 2);
        shop[SHOP_MAGNET] = Math.min(shopMax(SHOP_MAGNET), shop[SHOP_MAGNET] * 4);
        shop[SHOP_GROWTH] = Math.min(shopMax(SHOP_GROWTH), (shop[SHOP_GROWTH] * 10 + 2) / 3);
        shopVersion = SHOP_VERSION;
    }

    boolean save() {
        if (!writable) return false;
        dirty = true;
        retryFrames = 60;
        var out = new StringBuilder();
        out.append("formatVersion=").append(FORMAT_VERSION).append('\n');
        out.append("runIssued=").append(issuedRuns).append('\n');
        out.append("runStarted=").append(startedRuns.encode()).append('\n');
        out.append("runSettled=").append(settledRuns.encode()).append('\n');
        out.append("mode=").append(mode).append('\n');
        out.append("bank=").append(bank).append('\n');
        out.append("emeralds=").append(emeralds).append('\n');
        out.append("unlocked=").append(unlocked).append('\n');
        out.append("runs=").append(runs).append('\n');
        out.append("wins=").append(wins).append('\n');
        out.append("bestStages=").append(bestStages).append('\n');
        out.append("bestKills=").append(bestKills).append('\n');
        out.append("totalKills=").append(totalKills).append('\n');
        out.append("bestCombo=").append(bestCombo).append('\n');
        out.append("bankedTotal=").append(bankedTotal).append('\n');
        out.append("shopVersion=").append(SHOP_VERSION).append('\n');
        out.append("totalElites=").append(totalElites).append('\n');
        out.append("bestLevel=").append(bestLevel).append('\n');
        out.append("rules=").append(rules).append('\n');
        out.append("evolutionsSeen=").append(evolutionsSeen).append('\n');
        out.append("announcedUnlocks=").append(announcedUnlocks).append('\n');
        for (int i = 0; i < speciesKills.length; i++) {
            if (speciesKills[i] > 0) out.append("kills.").append(i).append('=').append(speciesKills[i]).append('\n');
        }
        for (int i = 0; i < bestUnlimited.length; i++) {
            if (bestUnlimited[i] > 0) out.append("unlimited.").append(i).append('=').append(bestUnlimited[i]).append('\n');
        }
        for (int i = 0; i < SHOP_COUNT; i++) out.append("shop.").append(i).append('=').append(shop[i]).append('\n');
        try {
            if (storage.write("profile.txt", VersionedSettings.parse(out.toString()).serialize())) {
                dirty = false;
                return true;
            }
            Logger.getLogger(Profile.class.getName()).warning("Could not save owned Survivors profile");
        } catch (RuntimeException e) {
            Logger.getLogger(Profile.class.getName()).log(Level.WARNING, "Could not save owned Survivors profile", e);
        }
        return false;
    }
}
