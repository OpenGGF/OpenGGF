package survivors;

import com.openggf.game.rewind.RewindSnapshottable;

/**
 * Module-owned state of the run in progress: it outlives each stage's level load. Holds the
 * upgrade levels, experience, the bounce combo, run statistics and the run's random stream,
 * plus the shop and emerald bonuses copied from the {@link Profile} when the run began (camp is
 * the only place to shop, so a run's bonuses never change underneath it). Captured for rewind.
 */
final class RunState implements RewindSnapshottable<RunState.Snapshot> {
    static final int STANDARD = 0, LONG = 1, ENDLESS = 2;
    static final int RING_SOUND_BATCH = 10, RING_SOUND_COOLDOWN = 12, RING_SOUND_QUIET = 30;
    static final int BASE_TOLL = 10;
    static final int BASE_COMBO_CAP = 6;
    /** Every run starts with these rings (four ring tolls) plus the shop's Ring Start. */
    static final int START_RINGS = 40;

    boolean active;
    int startStage, stage, act;
    int mode;
    int ringSoundRings, ringSoundFrame = -RING_SOUND_QUIET;
    int[] levels = new int[Upgrades.COUNT];
    int xp, level, pendingLevels;
    int rerolls, revives;
    int kills, ringsCollected, bestCombo, frames, stagesCleared, emeraldsWon;
    int carriedRings;
    int combo;
    /** Evolved weapons, by {@link Upgrades} evolution index. */
    int evolved;
    /** Eggman's Rules chosen for this run ({@link Rules} bits). */
    int rules;
    /** The upgrades this run may deal: core plus the profile's unlocks when it began. */
    int pool;
    /** True when Tails leads the run (his hover and magnet perks), false for Sonic. */
    boolean tails;
    /** Rings the run handed out for free (start, revives): never bankable by retiring. */
    int freeRings;
    int elitesDefeated, chestsOpened;
    /** Overdrive cards taken once the build is maxed: +6% damage each, without limit. */
    int overdrive;
    /** Badniks defeated this run per {@link Species} id, merged into the bestiary when banked. */
    int[] speciesKills = new int[Species.COUNT];
    /** Arena pressure seconds, mirrored by the stage each frame so the toll reads the same curve. */
    int pressure;
    /** Gameplay is frozen behind a menu (level-up, results, camp); badniks and shots hold still. */
    boolean paused;
    long rng;
    // Copied from the profile at the start of the run.
    int shopPower, shopRings, shopReroll, shopMagnet, shopGrowth, shopRevival, shopTalent, relics;
    int shopArmor, shopTreasure, shopHeadStart;

    /** Starts a new run at {@code stage}, act 0, with the profile's permanent bonuses. */
    void begin(int stage, Profile profile, long seed) {
        active = true;
        mode = profile.selectedMode();
        ringSoundRings = 0;
        ringSoundFrame = -RING_SOUND_QUIET;
        startStage = this.stage = stage;
        act = 0;
        levels = new int[Upgrades.COUNT];
        xp = 0;
        kills = ringsCollected = bestCombo = frames = stagesCleared = emeraldsWon = 0;
        combo = 0;
        evolved = 0;
        rules = profile.selectedRules();
        pool = profile.upgradePool();
        tails = false;
        elitesDefeated = chestsOpened = overdrive = 0;
        speciesKills = new int[Species.COUNT];
        pressure = 0;
        paused = false;
        rng = seed == 0 ? 0x5EED5EEDL : seed;
        shopPower = profile.shop[Profile.SHOP_POWER];
        shopRings = profile.shop[Profile.SHOP_RINGS];
        shopReroll = profile.shop[Profile.SHOP_REROLL];
        shopMagnet = profile.shop[Profile.SHOP_MAGNET];
        shopGrowth = profile.shop[Profile.SHOP_GROWTH];
        shopRevival = profile.shop[Profile.SHOP_REVIVAL];
        shopTalent = profile.shop[Profile.SHOP_TALENT];
        shopArmor = profile.shop[Profile.SHOP_ARMOR];
        shopTreasure = profile.shop[Profile.SHOP_TREASURE];
        shopHeadStart = profile.shop[Profile.SHOP_HEAD_START];
        relics = profile.emeralds;
        revives = shopRevival + (relic(6) ? 1 : 0);
        carriedRings = START_RINGS + 5 * shopRings;
        freeRings = carriedRings;
        // Catch-up: a run that starts further along the route gets four level-ups per skipped
        // stage (at most 24), banked as experience levels so the XP curve stays where it would be.
        // It is less than playing through (no chests, evolutions or carried rings).
        int catchUp = Math.min(24, 4 * stage);
        level = catchUp;
        pendingLevels = catchUp + (relic(0) ? 1 : 0) + shopHeadStart;
        rerolls = rerollsPerStage();
    }

    static String modeName(int mode) {
        return switch (mode) {
            case LONG -> "10 MINUTES";
            case ENDLESS -> "UNLIMITED";
            default -> "5 MINUTES";
        };
    }

    /** One immediate chime after quiet, then batches with at most five chimes per second.
     * Uses gameplay time so modal menus cannot consume the cooldown; captured with the run. */
    boolean ringSound(int value) {
        ringSoundRings = Math.min(RING_SOUND_BATCH, ringSoundRings + Math.min(RING_SOUND_BATCH, value));
        int elapsed = frames - ringSoundFrame;
        if (elapsed < RING_SOUND_QUIET
                && (elapsed < RING_SOUND_COOLDOWN || ringSoundRings < RING_SOUND_BATCH)) return false;
        ringSoundRings = 0;
        ringSoundFrame = frames;
        return true;
    }

    void end() {
        active = false;
        paused = false;
    }

    boolean rule(int rule) { return (rules & 1 << rule) != 0; }
    boolean evolvedWeapon(int weapon) {
        int evo = Upgrades.evolutionOf(weapon);
        return evo >= 0 && (evolved & 1 << evo) != 0;
    }
    /** A maxed weapon whose partner buff is owned, waiting for a chest. */
    boolean evolutionReady(int evo) {
        int base = Upgrades.evoBase(evo);
        return (evolved & 1 << evo) == 0 && level(base) >= Upgrades.maxLevel(base) && has(Upgrades.evoPartner(evo));
    }
    int readyEvolutions() {
        int count = 0;
        for (int evo = 0; evo < Upgrades.EVOLUTIONS; evo++) if (evolutionReady(evo)) count++;
        return count;
    }
    boolean inPool(int id) {
        if (id == Upgrades.MAGNET && rule(Rules.NO_MAGNET)) return false;
        return Upgrades.core(id) || (pool & 1 << id) != 0;
    }
    boolean relic(int emerald) { return (relics & 1 << emerald) != 0; }
    boolean allRelics() { return Integer.bitCount(relics & 0x7F) == Profile.EMERALDS; }

    int level(int upgrade) { return levels[upgrade]; }
    boolean has(int upgrade) { return levels[upgrade] > 0; }

    // ---- Derived stats ----
    double damageMultiplier() {
        return (1 + 0.25 * level(Upgrades.POWER)) * (1 + 0.03 * shopPower) * (allRelics() ? 1.25 : 1.0)
                * (1 + 0.06 * overdrive);
    }

    int stompDamage() {
        return 1 + level(Upgrades.SPRING_HEELS);
    }

    /** Damage bonus: +25% per chained bounce (Sonic +30%), capped. Ring rewards use a gentler fraction. */
    double comboMultiplier() {
        int cap = BASE_COMBO_CAP + (relic(2) ? 2 : 0);
        return Math.min(cap, 1 + (tails ? 0.25 : 0.30) * Math.max(0, combo - 1));
    }

    int toll() { return toll(0); }

    /** Large banks remain valuable, but no longer buy hundreds of mistakes. */
    int toll(int rings) {
        // The arena's pressure clock (already 75% in long mode) and route position: the same
        // curve as enemy hitpoints, so the toll never outruns what badniks are worth.
        double threat = Difficulty.tollThreat(stage, act, pressure) * (rule(Rules.HEAVY_TOLL) ? 1.5 : 1);
        int base = (int) Math.ceil(Math.max(BASE_TOLL, (rings + 11L) / 12) * threat);
        // Armor, plating and the red emerald stack, but never past 60%: hits must stay a threat.
        int protection = Math.min(60, 8 * level(Upgrades.ARMOR) + 2 * shopArmor + (relic(4) ? 15 : 0));
        return Math.max(5, (base * (100 - protection) + 99) / 100);
    }

    int magnetRadius() {
        if (rule(Rules.NO_MAGNET)) return 0;
        return Upgrades.magnetRadius(level(Upgrades.MAGNET), shopMagnet) + (tails ? 24 : 0);
    }

    double cooldownScale() {
        return 1 - 0.10 * level(Upgrades.HASTE);
    }

    double xpScale() {
        return (1 + 0.20 * level(Upgrades.GREED)) * (1 + 0.25 * level(Upgrades.SCHOLAR)) * (1 + 0.03 * shopGrowth);
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
        // Quick first weapons, then a steep curve: a full build arrives around the middle of the route.
        return 6 + 8 * level + 2 * level * level;
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

    int occupiedSlots(boolean weapons) {
        int count = 0;
        for (int id = 0; id < Upgrades.COUNT; id++)
            if (has(id) && Upgrades.weapon(id) == weapons) count++;
        return count;
    }

    boolean canUpgrade(int id) {
        return inPool(id) && level(id) < Upgrades.maxLevel(id)
                && (has(id) || occupiedSlots(Upgrades.weapon(id)) < Upgrades.SLOT_LIMIT);
    }

    int feverCharge() { return tails ? 12 : 10; }
    int hurtRecovery() { return Guard.INVULNERABLE_FRAMES + 6 * level(Upgrades.RECOVERY); }

    /** True when the equipped build has no eligible upgrades left. */
    boolean maxedOut() {
        for (int i = 0; i < Upgrades.COUNT; i++) if (canUpgrade(i)) return false;
        return true;
    }

    // ---- Deterministic random stream (xorshift64*), captured with the run for rewind. ----
    long nextLong() {
        long x = rng;
        x ^= x >>> 12;
        x ^= x << 25;
        x ^= x >>> 27;
        rng = x;
        return x * 0x2545F4914F6CDD1DL;
    }

    int nextInt(int bound) {
        return bound <= 1 ? 0 : (int) Long.remainderUnsigned(nextLong(), bound);
    }

    boolean chance(double p) {
        return (nextLong() >>> 11) * 0x1.0p-53 < p;
    }

    @Override public String key() { return "sonic-survivors:run"; }

    @Override public Snapshot capture() {
        return new Snapshot(active, startStage, stage, act, levels.clone(), xp, level, pendingLevels, rerolls, revives,
                kills, ringsCollected, bestCombo, frames, stagesCleared, emeraldsWon, carriedRings, combo, paused, rng, mode, ringSoundRings, ringSoundFrame,
                new int[]{shopPower, shopRings, shopReroll, shopMagnet, shopGrowth, shopRevival, shopTalent, relics},
                new int[]{evolved, rules, pool, tails ? 1 : 0, freeRings, elitesDefeated, chestsOpened, pressure, overdrive, shopArmor, shopTreasure, shopHeadStart},
                speciesKills.clone());
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
        rng = s.rng();
        mode = s.mode();
        ringSoundRings = s.ringSoundRings();
        ringSoundFrame = s.ringSoundFrame();
        int[] shop = s.shop();
        shopPower = shop[0];
        shopRings = shop[1];
        shopReroll = shop[2];
        shopMagnet = shop[3];
        shopGrowth = shop[4];
        shopRevival = shop[5];
        shopTalent = shop[6];
        relics = shop[7];
        int[] extra = s.extra();
        evolved = extra[0];
        rules = extra[1];
        pool = extra[2];
        tails = extra[3] != 0;
        freeRings = extra[4];
        elitesDefeated = extra[5];
        chestsOpened = extra[6];
        pressure = extra[7];
        overdrive = extra[8];
        shopArmor = extra[9];
        shopTreasure = extra[10];
        shopHeadStart = extra[11];
        speciesKills = s.speciesKills().clone();
    }

    @Override public void resetForMissingSnapshot() {
        // A rewind to before the module saw any run keeps whatever run is live.
    }

    record Snapshot(boolean active, int startStage, int stage, int act, int[] levels, int xp, int level,
                    int pendingLevels, int rerolls, int revives, int kills, int ringsCollected, int bestCombo,
                    int frames, int stagesCleared, int emeraldsWon, int carriedRings, int combo, boolean paused,
                    long rng, int mode, int ringSoundRings, int ringSoundFrame, int[] shop, int[] extra, int[] speciesKills) { }
}
