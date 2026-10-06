package slaytherobotnik.core;

/**
 * A buff or debuff on a creature (Strength, Vulnerable, a Power card's lasting effect...).
 *
 * <p>Applying a power the creature already has adds the amounts together. Subclasses
 * override the {@link CombatListener} hooks they need and {@link #description()}:
 *
 * <pre>{@code
 * new Power("sonic:peel_out", "Peel Out", Power.BUFF, 3) {
 *     @Override public void onCardExhausted(Combat c, Card card) {
 *         c.gainBlock(owner(), amount(), null);
 *     }
 *     @Override public String description() {
 *         return "Whenever a card is Exhausted, gain " + amount() + " Block.";
 *     }
 * }
 * }</pre>
 *
 * <p>Powers only live inside a combat; runs are saved between rooms, never mid-fight.
 */
public abstract class Power implements CombatListener {
    /** Constructor flag: the power helps its owner. */
    public static final boolean BUFF = false;
    /** Constructor flag: the power hinders its owner. Artifact blocks debuffs. */
    public static final boolean DEBUFF = true;

    private final String id;
    private final String name;
    private final boolean debuff;
    private Creature owner;
    private int amount;
    private boolean turnBased;
    private boolean canGoNegative;
    private boolean stacks = true;
    private boolean justApplied;

    /** {@code debuff} is {@link #BUFF} or {@link #DEBUFF}. */
    protected Power(String id, String name, boolean debuff, int amount) {
        this.id = id;
        this.name = name;
        this.debuff = debuff;
        this.amount = amount;
    }

    /** Text for the power's tooltip, using the current amount. */
    public abstract String description();

    public String id() { return id; }
    public String name() { return name; }
    public Creature owner() { return owner; }
    public int amount() { return amount; }
    public boolean turnBased() { return turnBased; }
    public boolean canGoNegative() { return canGoNegative; }
    public boolean stacks() { return stacks; }

    /** A debuff, or a stat (Strength, Dexterity, Focus) applied as a negative amount. */
    public boolean isDebuff() {
        return debuff || (canGoNegative && amount < 0);
    }

    /** Ticks down by one at the end of each round and disappears at zero (Vulnerable, Weak, Frail). */
    protected final Power turnBasedDuration() {
        turnBased = true;
        return this;
    }

    /** May drop below zero instead of being removed (Strength, Dexterity, Focus). */
    protected final Power allowNegative() {
        canGoNegative = true;
        return this;
    }

    /** Re-applying does not add amounts (on/off powers such as Barricade). */
    protected final Power nonStacking() {
        stacks = false;
        return this;
    }

    void attach(Creature owner) {
        this.owner = owner;
    }

    void setJustApplied(boolean value) {
        justApplied = value;
    }

    boolean justApplied() {
        return justApplied;
    }

    /** Changes the amount; the combat removes the power when it reaches zero. */
    public void addAmount(int delta) {
        amount += delta;
    }

    public void setAmount(int value) {
        amount = value;
    }

    /**
     * Order in which damage and Block modifiers apply: lower first. Additive stats such as
     * Strength use the default 5; multipliers such as Weak and Vulnerable use 99 so they
     * scale the already-adjusted value, matching Slay the Spire.
     */
    public int priority() {
        return 5;
    }

    /** Whether the amount is shown on the icon. */
    public boolean showsAmount() {
        return stacks;
    }

    @Override
    public String toString() {
        return name + "(" + amount + ")";
    }
}
