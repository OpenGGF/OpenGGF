package slaytherobotnik.core;

import java.util.Set;

/**
 * One copy of a card. The master deck holds {@code Card}s for the whole run; each combat
 * works on copies made with {@link #combatCopy()}, so temporary changes (a cost reduced
 * for this combat, damage grown while Retained) disappear when the fight ends. Effects
 * that permanently change a card ("upgrade it permanently", a counter that grows across
 * fights) go through {@link #master()}.
 *
 * <p>Cards are compared by identity: two Spin Attacks in a hand are different objects.
 */
public final class Card {
    private static final int UNSET = Integer.MIN_VALUE;

    private final CardDef def;
    private final Card master;
    private boolean upgraded;
    /** Counter that persists in the deck across combats and is saved with the run. */
    private int misc;

    // Combat-only state.
    private int costForCombat = UNSET;
    private int costForTurn = UNSET;
    private boolean freeToPlayOnce;
    private int bonusDamage;
    private int bonusBlock;
    private boolean retainThisTurn;

    public Card(CardDef def) {
        this(def, false);
    }

    public Card(CardDef def, boolean upgraded) {
        this(def, upgraded, null);
    }

    private Card(CardDef def, boolean upgraded, Card master) {
        this.def = def;
        this.upgraded = upgraded;
        this.master = master;
    }

    /** A combat copy linked back to this deck card. */
    public Card combatCopy() {
        Card copy = new Card(def, upgraded, this);
        copy.misc = misc;
        return copy;
    }

    /** An independent duplicate (for "add a copy of this card to your hand" effects). */
    public Card duplicate() {
        Card copy = new Card(def, upgraded, null);
        copy.misc = misc;
        copy.costForCombat = costForCombat;
        copy.bonusDamage = bonusDamage;
        copy.bonusBlock = bonusBlock;
        return copy;
    }

    public CardDef def() { return def; }
    public String id() { return def.id(); }
    public String name() { return upgraded ? def.name() + "+" : def.name(); }
    public String type() { return def.type(); }
    public String color() { return def.color(); }
    public String rarity() { return def.rarity(); }
    public String target() { return def.target(); }
    public boolean upgraded() { return upgraded; }
    /** The deck card this combat copy came from, or {@code null} for cards created in combat. */
    public Card master() { return master; }

    public boolean canUpgrade() {
        return def.upgradable() && !upgraded && !def.type().equals(CardType.CURSE) && !def.type().equals(CardType.STATUS);
    }

    public void upgrade() {
        if (canUpgrade()) {
            upgraded = true;
        }
    }

    /** Upgrades regardless of type (enemies upgrading the Burns they gave you). */
    void forceUpgrade() {
        upgraded = true;
    }

    public int damage() { return def.damage(upgraded) + bonusDamage; }
    public int block() { return def.block(upgraded) + bonusBlock; }
    public int magic() { return def.magic(upgraded); }
    public int combo() { return def.combo(upgraded); }
    public Set<String> keywords() { return def.keywords(upgraded); }
    public boolean has(String keyword) { return def.keywords(upgraded).contains(keyword) || (keyword.equals(Keyword.RETAIN) && retainThisTurn); }

    /** True for X-cost cards. */
    public boolean isXCost() { return def.cost(upgraded) == CardDef.COST_X; }

    /** Printed cost after combat/turn modifiers, before {@link CardHooks#costAdjuster()}; X and unplayable are negative markers. */
    public int baseCost() {
        int printed = def.cost(upgraded);
        if (printed < 0) {
            return printed;
        }
        if (costForTurn != UNSET) {
            return costForTurn;
        }
        if (costForCombat != UNSET) {
            return costForCombat;
        }
        return printed;
    }

    public boolean costModified() {
        int printed = def.cost(upgraded);
        return printed >= 0 && baseCost() != printed;
    }

    public void setCostForCombat(int cost) { costForCombat = Math.max(0, cost); costForTurn = UNSET; }
    public void setCostForTurn(int cost) { costForTurn = Math.max(0, cost); }
    public void clearTurnCost() { costForTurn = UNSET; }
    public boolean freeToPlayOnce() { return freeToPlayOnce; }
    public void setFreeToPlayOnce(boolean free) { freeToPlayOnce = free; }

    public int bonusDamage() { return bonusDamage; }
    public void addBonusDamage(int amount) { bonusDamage += amount; }
    public void addBonusBlock(int amount) { bonusBlock += amount; }
    public boolean retainThisTurn() { return retainThisTurn; }
    public void setRetainThisTurn(boolean retain) { retainThisTurn = retain; }

    public int misc() { return misc; }
    public void setMisc(int misc) { this.misc = misc; }

    @Override
    public String toString() {
        return name();
    }
}
