package slaytherobotnik.core;

/**
 * A run-long item. Relics react to combat through the {@link CombatListener} hooks and to
 * the rest of the run through the {@code on...(RunState)} hooks below. A fresh instance is
 * created for each run (the catalog stores a factory), so a relic may keep its own state;
 * {@link #counter()} is saved with the run.
 *
 * <pre>{@code
 * new Relic("relic:spiked_gloves", "Spiked Gloves", RelicTier.STARTER) {
 *     @Override public void onVictory(Combat c) {
 *         flash(c);
 *         c.healPlayer(6);
 *     }
 *     @Override public String description() { return "At the end of combat, heal 6 HP."; }
 * }
 * }</pre>
 */
public abstract class Relic implements CombatListener {
    private final String id;
    private final String name;
    private final String tier;
    private String character;
    private int counter = -1;
    private boolean usedUp;

    /** {@code tier} is a {@link RelicTier} constant. */
    protected Relic(String id, String name, String tier) {
        this.id = id;
        this.name = name;
        this.tier = tier;
    }

    public abstract String description();

    /** Short flavour line shown under the description. */
    public String flavor() {
        return "";
    }

    public String id() { return id; }
    public String name() { return name; }
    public String tier() { return tier; }
    /** Character id this relic is restricted to, or {@code null} for any character. */
    public String character() { return character; }

    /** Restricts the relic to one character's reward pool. */
    public final Relic onlyFor(String characterId) {
        character = characterId;
        return this;
    }

    /** A number drawn on the relic icon (charges, a countdown); -1 hides it. Saved with the run. */
    public int counter() { return counter; }
    public void setCounter(int value) { counter = value; }
    /** A spent one-shot relic is drawn greyed out and does nothing. */
    public boolean usedUp() { return usedUp; }
    public void setUsedUp(boolean value) { usedUp = value; }

    /** Tells the combat screen to flash this relic's icon. */
    protected final void flash(Combat c) {
        c.events().add(new CombatEvent.RelicTriggered(this));
    }

    // ----- Run hooks -----

    /** Picked up (also called for the starting relic). */
    public void onEquip(RunState run) { }
    /** Entering any room; {@code roomType} is a {@link RoomType} constant. */
    public void onEnterRoom(RunState run, String roomType) { }
    /** Resting at a Starpost (rest site). */
    public void onRest(RunState run) { }
    /** Adds extra Starpost options ("Dig", "Lift"...). */
    public void addRestOptions(RunState run, java.util.List<RestOption> options) { }
    /** Return false to forbid resting (healing) at Starposts. */
    public boolean canRest(RunState run) { return true; }
    /** Adjusts how much a rest heals. */
    public int modifyRestHeal(RunState run, int amount) { return amount; }
    /** Adjusts every Egg Robo shop price. */
    public int modifyShopPrice(RunState run, int price) { return price; }
    /** Something was bought at the Egg Robo's shop. */
    public void onShopPurchase(RunState run) { }
    /** A card was added to the master deck. */
    public void onCardAddedToDeck(RunState run, Card card) { }
    /** Adjusts healing outside and inside combat. */
    public int modifyHeal(RunState run, int amount) { return amount; }
    /** Adjusts rings dropped by a fight. */
    public int modifyCombatRings(RunState run, int rings) { return rings; }
    /** Adds extra rewards after a fight (for example a second card choice). */
    public void addCombatRewards(RunState run, String roomType, java.util.List<Reward> rewards) { }
    /** Number of cards offered in each card reward. */
    public int modifyCardRewardSize(RunState run, int count) { return count; }
    /** Extra potion slots. */
    public int potionSlotBonus() { return 0; }
    /** Adjusts potion potency (doubling relics). */
    public int modifyPotionPotency(int potency) { return potency; }
    /** Extra Energy per turn (most boss relics). */
    public int energyBonus() { return 0; }
    /** Extra cards drawn at the start of each turn. */
    public int drawBonus() { return 0; }
    /**
     * Called when the player would die; return true to survive. The relic restores HP
     * itself (for example with {@link Combat#healPlayer}) and usually marks itself used up.
     */
    public boolean preventDeath(Combat c) { return false; }

    // ----- Combat-rule hooks (relics only) -----

    /** Block the player keeps when their turn starts (normally 0). */
    public int blockKeptAtTurnStart(Combat c, int block) { return 0; }
    /** Return true to keep the whole hand at the end of the turn. */
    public boolean retainsHand(Combat c) { return false; }
    /** Adjusts X for X-cost cards. */
    public int modifyXValue(Combat c, Card card, int x) { return x; }
    /** Adjusts the Weak multiplier (normally 0.75) for an attack by {@code attacker}. */
    public float modifyWeakMultiplier(Combat c, Creature attacker, float multiplier) { return multiplier; }

    @Override
    public String toString() {
        return name;
    }
}
