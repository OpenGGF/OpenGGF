package survivors;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.mods.state.SnapshotRandom;

/**
 * Module-owned state of the run in progress: it outlives each stage's level load. Holds the
 * upgrade levels, experience, the bounce combo, run statistics and the run's random stream,
 * plus the shop and emerald bonuses copied from the {@link Profile} when the run began (camp is
 * the only place to shop, so a run's bonuses never change underneath it). Captured for rewind.
 */
final class RunState implements RewindSnapshottable<RunState.Snapshot> {
    static final int BASE_TOLL = 10;
    static final int BASE_COMBO_CAP = 6;
    /** Every run starts with these rings (three ring tolls) plus the shop's Ring Start. */
    static final int START_RINGS = 30;

    boolean active;
    int startStage, stage, act;
    int[] levels = new int[Upgrades.COUNT];
    int xp, level, pendingLevels;
    int rerolls, revives;
    int kills, ringsCollected, bestCombo, frames, stagesCleared, emeraldsWon;
    int carriedRings;
    int combo;
    /** Gameplay is frozen behind a menu (level-up, results, camp); badniks and shots hold still. */
    boolean paused;
    private final SnapshotRandom random = new SnapshotRandom(0);
    // Copied from the profile at the start of the run.
    int shopPower, shopRings, shopReroll, shopMagnet, shopGrowth, shopRevival, shopTalent, relics;

    /** Starts a new run at {@code stage}, act 0, with the profile's permanent bonuses. */
    void begin(int stage, Profile profile, long seed) {
        active = true;
        startStage = this.stage = stage;
        act = 0;
        levels = new int[Upgrades.COUNT];
        xp = 0;
        kills = ringsCollected = bestCombo = frames = stagesCleared = emeraldsWon = 0;
        combo = 0;
        paused = false;
        random.reset(seed == 0 ? 0x5EED5EEDL : seed);
        shopPower = profile.shop[Profile.SHOP_POWER];
        shopRings = profile.shop[Profile.SHOP_RINGS];
        shopReroll = profile.shop[Profile.SHOP_REROLL];
        shopMagnet = profile.shop[Profile.SHOP_MAGNET];
        shopGrowth = profile.shop[Profile.SHOP_GROWTH];
        shopRevival = profile.shop[Profile.SHOP_REVIVAL];
        shopTalent = profile.shop[Profile.SHOP_TALENT];
        relics = profile.emeralds;
        revives = shopRevival + (relic(6) ? 1 : 0);
        carriedRings = START_RINGS + 10 * shopRings;
        // Catch-up: a run that starts further along the route gets two level-ups per skipped
        // stage (at most ten), banked as experience levels so the XP curve stays where it would be.
        int catchUp = Math.min(10, 2 * stage);
        level = catchUp;
        pendingLevels = catchUp + (relic(0) ? 1 : 0);
        rerolls = rerollsPerStage();
    }

    void end() {
        active = false;
        paused = false;
    }

    boolean relic(int emerald) { return (relics & 1 << emerald) != 0; }
    boolean allRelics() { return Integer.bitCount(relics & 0x7F) == Profile.EMERALDS; }

    int level(int upgrade) { return levels[upgrade]; }
    boolean has(int upgrade) { return levels[upgrade] > 0; }

    // ---- Derived stats ----
    double damageMultiplier() {
        return (1 + 0.25 * level(Upgrades.POWER)) * (1 + 0.10 * shopPower) * (allRelics() ? 1.25 : 1.0);
    }

    int stompDamage() {
        return 1 + level(Upgrades.SPRING_HEELS);
    }

    /** Combo multiplier on damage and ring drops: +25% per chained bounce, capped. */
    double comboMultiplier() {
        int cap = BASE_COMBO_CAP + (relic(2) ? 2 : 0);
        return Math.min(cap, 1 + 0.25 * Math.max(0, combo - 1));
    }

    int toll() {
        return Math.max(2, BASE_TOLL - 2 * level(Upgrades.ARMOR) - (relic(4) ? 3 : 0));
    }

    int magnetRadius() {
        return Upgrades.magnetRadius(level(Upgrades.MAGNET), shopMagnet);
    }

    double cooldownScale() {
        return 1 - 0.10 * level(Upgrades.HASTE);
    }

    double xpScale() {
        return (1 + 0.20 * level(Upgrades.GREED)) * (1 + 0.10 * shopGrowth);
    }

    int rerollsPerStage() {
        return 1 + shopReroll + (relic(1) ? 1 : 0);
    }

    int cardCount() {
        return shopTalent > 0 ? 4 : 3;
    }

    /** Bounce rebound speed (1/256 px per frame). */
    int bounceSpeed() {
        return 0x580 + 0x80 * level(Upgrades.SPRING_HEELS);
    }

    /** Experience needed to go from {@code level} to the next. */
    static int xpToNext(int level) {
        return 5 + 3 * level + level * level / 3;
    }

    /** Adds experience; returns how many level-ups it earned. */
    int gainXp(int amount) {
        xp += amount;
        int earned = 0;
        while (xp >= xpToNext(level)) {
            xp -= xpToNext(level);
            level++;
            earned++;
        }
        pendingLevels += earned;
        return earned;
    }

    /** True when every upgrade is at its maximum level. */
    boolean maxedOut() {
        for (int i = 0; i < Upgrades.COUNT; i++) if (levels[i] < Upgrades.maxLevel(i)) return false;
        return true;
    }

    // ---- Deterministic random stream (xorshift64*), captured with the run for rewind. ----
    long nextLong() { return random.nextLong(); }

    int nextInt(int bound) { return random.nextInt(bound); }

    boolean chance(double p) {
        return (nextLong() >>> 11) * 0x1.0p-53 < p;
    }

    @Override public String key() { return "sonic-survivors:run"; }

    @Override public Snapshot capture() {
        return new Snapshot(active, startStage, stage, act, levels.clone(), xp, level, pendingLevels, rerolls, revives,
                kills, ringsCollected, bestCombo, frames, stagesCleared, emeraldsWon, carriedRings, combo, paused, random.snapshot(),
                new int[]{shopPower, shopRings, shopReroll, shopMagnet, shopGrowth, shopRevival, shopTalent, relics});
    }

    @Override public void restore(Snapshot s) {
        active = s.active();
        startStage = s.startStage();
        stage = s.stage();
        act = s.act();
        levels = s.levels().clone();
        xp = s.xp();
        level = s.level();
        pendingLevels = s.pendingLevels();
        rerolls = s.rerolls();
        revives = s.revives();
        kills = s.kills();
        ringsCollected = s.ringsCollected();
        bestCombo = s.bestCombo();
        frames = s.frames();
        stagesCleared = s.stagesCleared();
        emeraldsWon = s.emeraldsWon();
        carriedRings = s.carriedRings();
        combo = s.combo();
        paused = s.paused();
        random.restore(s.rng());
        int[] shop = s.shop();
        shopPower = shop[0];
        shopRings = shop[1];
        shopReroll = shop[2];
        shopMagnet = shop[3];
        shopGrowth = shop[4];
        shopRevival = shop[5];
        shopTalent = shop[6];
        relics = shop[7];
    }

    @Override public void resetForMissingSnapshot() {
        // A rewind to before the module saw any run keeps whatever run is live.
    }

    record Snapshot(boolean active, int startStage, int stage, int act, int[] levels, int xp, int level,
                    int pendingLevels, int rerolls, int revives, int kills, int ringsCollected, int bestCombo,
                    int frames, int stagesCleared, int emeraldsWon, int carriedRings, int combo, boolean paused,
                    long rng, int[] shop) { }
}
