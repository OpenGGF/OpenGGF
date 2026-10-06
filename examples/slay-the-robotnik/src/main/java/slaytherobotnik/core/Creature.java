package slaytherobotnik.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Anything with HP, Block and powers: the player or an enemy. */
public abstract class Creature {
    private final String name;
    protected int hp;
    protected int maxHp;
    protected int block;
    private final Map<String, Power> powers = new LinkedHashMap<>();

    protected Creature(String name, int hp, int maxHp) {
        this.name = name;
        this.hp = hp;
        this.maxHp = maxHp;
    }

    public String name() { return name; }
    public int hp() { return hp; }
    public int maxHp() { return maxHp; }
    public int block() { return block; }
    public boolean isDead() { return hp <= 0; }
    public abstract boolean isPlayer();

    /** Powers in the order they were first applied. */
    public List<Power> powers() {
        return new ArrayList<>(powers.values());
    }

    public Power power(String id) {
        return powers.get(id);
    }

    public boolean has(String id) {
        return powers.containsKey(id);
    }

    /** The amount of a power, or 0 when absent. */
    public int amount(String id) {
        Power p = powers.get(id);
        return p == null ? 0 : p.amount();
    }

    void putPower(Power power) {
        powers.put(power.id(), power);
    }

    void removePower(String id) {
        powers.remove(id);
    }

    void setHp(int value) {
        hp = Math.max(0, Math.min(maxHp, value));
    }

    void setBlock(int value) {
        block = Math.max(0, value);
    }

    void setMaxHp(int value) {
        maxHp = Math.max(1, value);
        hp = Math.min(hp, maxHp);
    }

    @Override
    public String toString() {
        return name + " " + hp + "/" + maxHp + (block > 0 ? " [" + block + "]" : "");
    }
}
