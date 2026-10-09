package threeislands.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything a save file holds: the heroes, who has joined, the rings, the item bag, the Chaos
 * Emeralds, which zones are cleared, which story scenes have played, the current island and
 * the random generator.
 */
public final class Progress {
    public static final int ALL_EMERALDS = 0x7F;
    public static final int MAX_RINGS = 9999;
    public static final int MAX_ITEMS = 99;

    private final Hero[] heroes = new Hero[HeroId.values().length];
    private int joined = HeroId.SONIC.bit();
    private int rings = 40;
    private final int[] items = new int[Item.values().length];
    private int emeralds;
    private int cleared;
    private int island;
    private final Set<String> scenes = new LinkedHashSet<>();
    public final Rng rng;
    private long playTicks;
    private int resumeZone = -1;
    private int resumeX;
    private boolean finished;
    private boolean resumeDungeon;

    public Progress(long seed) {
        for (HeroId id : HeroId.values()) heroes[id.ordinal()] = new Hero(id);
        rng = new Rng(seed);
        items[Item.SUPER_RING.ordinal()] = 5;
        items[Item.BLUE_SPHERE.ordinal()] = 3;
    }

    public Hero hero(HeroId id) { return heroes[id.ordinal()]; }
    public boolean hasJoined(HeroId id) { return (joined & id.bit()) != 0; }
    public int joinedMask() { return joined; }

    /** The active party in formation order. */
    public List<Hero> party() {
        List<Hero> out = new ArrayList<>();
        for (HeroId id : HeroId.values()) if (hasJoined(id)) out.add(hero(id));
        return out;
    }

    /** A hero joins at the party's highest level, fully restored. */
    public void join(HeroId id) {
        if (hasJoined(id)) return;
        int level = 1;
        for (Hero hero : party()) level = Math.max(level, hero.level());
        joined |= id.bit();
        hero(id).setLevel(level);
    }

    public int rings() { return rings; }
    public void addRings(int amount) { rings = Math.max(0, Math.min(MAX_RINGS, rings + amount)); }

    public boolean spendRings(int amount) {
        if (amount < 0 || rings < amount) return false;
        rings -= amount;
        return true;
    }

    public int count(Item item) { return items[item.ordinal()]; }

    public void addItem(Item item, int amount) {
        items[item.ordinal()] = Math.max(0, Math.min(MAX_ITEMS, items[item.ordinal()] + amount));
    }

    public boolean takeItem(Item item) {
        if (items[item.ordinal()] <= 0) return false;
        items[item.ordinal()]--;
        return true;
    }

    public int emeralds() { return emeralds; }
    public int emeraldCount() { return Integer.bitCount(emeralds); }
    public void addEmerald(int index) { if (index >= 0 && index < 7) emeralds |= 1 << index; }
    public boolean allEmeralds() { return emeralds == ALL_EMERALDS; }

    public boolean isCleared(Zone zone) { return (cleared & zone.bit()) != 0; }
    public void clear(Zone zone) { cleared |= zone.bit(); }

    public void completeChapter(Zone zone) {
        if (isCleared(zone)) return;
        clear(zone);
        // Story milestones carry progression even when the player avoids patrols and side stories.
        for (Hero hero : party()) if (hero.level() < zone.level + 1) hero.setLevel(zone.level + 1);
        addItem(Item.SUPER_RING, 2);
        addItem(Item.BLUE_SPHERE, 1);
    }
    public int clearedMask() { return cleared; }

    /** A zone is open once every earlier zone of its island is cleared. */
    public boolean isOpen(Zone zone) {
        for (Zone other : Zone.of(zone.island())) {
            if (other == zone) return true;
            if (!isCleared(other)) return false;
        }
        return false;
    }

    public boolean islandComplete(Island which) {
        for (Zone zone : Zone.of(which)) if (!isCleared(zone)) return false;
        return true;
    }

    public Island island() { return Island.values()[island]; }
    public void setIsland(Island value) { island = value.ordinal(); }

    public boolean seen(String scene) { return scenes.contains(scene); }
    public void markSeen(String scene) { scenes.add(scene); }
    public Set<String> seenScenes() { return Set.copyOf(scenes); }

    /** The zone and position of the last Starpost touched, or -1 when resuming on the map. */
    public int resumeZone() { return resumeZone; }
    public int resumeX() { return resumeX; }
    public boolean resumeDungeon() { return resumeDungeon; }
    public void setResumeDungeon(boolean value) { resumeDungeon = value && resumeZone >= 0; }

    public void setResume(Zone zone, int x) {
        resumeDungeon = false;
        resumeZone = zone == null ? -1 : zone.ordinal();
        resumeX = zone == null ? 0 : Math.max(0, x);
    }

    /** True once the ending has played. */
    public boolean finished() { return finished; }
    public void setFinished(boolean value) { finished = value; }

    public long playTicks() { return playTicks; }
    public void tick() { playTicks++; }

    /** Rest at a village or Starpost: everyone is fully restored. */
    public void restAll() {
        for (Hero hero : party()) hero.restore();
    }

    /** After a won battle fallen heroes get back up with 1 HP. */
    public void reviveFallen() {
        for (Hero hero : party()) if (hero.hp() <= 0) hero.setHp(1);
    }

    /** A breathing space after victory; exploration should not become a healing-item tax. */
    public void recoverAfterBattle() {
        for (Hero hero : party()) {
            hero.setHp(Math.max(hero.hp(), hero.maxHp() * 3 / 5));
            hero.setEp(hero.ep() + Math.max(2, hero.maxEp() / 5));
        }
    }

    /** Highest level in the party. */
    public int partyLevel() {
        int level = 1;
        for (Hero hero : party()) level = Math.max(level, hero.level());
        return level;
    }

    // ---- Used by SaveCodec only ----

    void loadState(int joinedMask, int savedRings, int savedEmeralds, int savedCleared, int savedIsland,
            long savedTicks) {
        joined = (joinedMask & 7) | HeroId.SONIC.bit();
        rings = Math.max(0, Math.min(MAX_RINGS, savedRings));
        emeralds = savedEmeralds & ALL_EMERALDS;
        cleared = savedCleared & ((1 << Zone.values().length) - 1);
        island = Math.max(0, Math.min(Island.values().length - 1, savedIsland));
        playTicks = Math.max(0, savedTicks);
    }

    void loadResume(int zone, int x, boolean done) {
        resumeZone = zone >= 0 && zone < Zone.values().length ? zone : -1;
        resumeX = Math.max(0, x);
        finished = done;
    }

    void setItemCount(Item item, int count) {
        items[item.ordinal()] = Math.max(0, Math.min(MAX_ITEMS, count));
    }

    void clearScenes() {
        scenes.clear();
    }
}
