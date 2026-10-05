package slaytherobotnik.core;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable definition of a card: everything that is the same for every copy of it.
 *
 * <p>Cards are declared with {@link #builder}. A definition carries four numbers that the
 * card text can show and its effect can read, each with an upgrade delta:
 * <ul>
 *   <li>{@code {D}} damage per hit,</li>
 *   <li>{@code {B}} Block,</li>
 *   <li>{@code {M}} a "magic number" (stacks of a debuff, cards drawn, and so on),</li>
 *   <li>{@code {C}} the Combo count — how many times a Combo effect repeats before Focus.</li>
 * </ul>
 * Keywords (Exhaust, Retain, ...) are added to the text automatically, so an upgrade that
 * removes Exhaust also updates the description.
 *
 * <pre>{@code
 * CardDef.builder("sonic:spin_dash", "Spin Dash", CardColor.BLUE, CardType.ATTACK, CardRarity.BASIC)
 *         .cost(2).target(CardTarget.ENEMY)
 *         .damage(8, 2).magic(2, 1)
 *         .text("Deal {D} damage. Apply {M} Vulnerable.")
 *         .effect(p -> {
 *             p.attack();
 *             p.applyToTarget(Powers.vulnerable(p.magic()));
 *         })
 *         .build();
 * }</pre>
 */
public final class CardDef {
    /** Cost marker for X-cost cards, which spend all remaining Energy. */
    public static final int COST_X = -1;
    /** Cost marker for cards that can never be played (most Status and Curse cards). */
    public static final int COST_NONE = -2;

    private final String id;
    private final String name;
    private final String color;
    private final String type;
    private final String rarity;
    private final String target;
    private final int cost;
    private final int upgradedCost;
    private final int damage;
    private final int damageUp;
    private final int block;
    private final int blockUp;
    private final int magic;
    private final int magicUp;
    private final int combo;
    private final int comboUp;
    private final Set<String> keywords;
    private final Set<String> upgradedKeywords;
    private final String text;
    private final String upgradedText;
    private final CardEffect effect;
    private final CardHooks hooks;
    private final PlayCondition condition;
    private final DamageFormula damageFormula;
    private final boolean upgradable;
    private final String art;

    private CardDef(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.color = b.color;
        this.type = b.type;
        this.rarity = b.rarity;
        this.target = b.target;
        this.cost = b.cost;
        this.upgradedCost = b.upgradedCost == Integer.MIN_VALUE ? b.cost : b.upgradedCost;
        this.damage = b.damage;
        this.damageUp = b.damageUp;
        this.block = b.block;
        this.blockUp = b.blockUp;
        this.magic = b.magic;
        this.magicUp = b.magicUp;
        this.combo = b.combo;
        this.comboUp = b.comboUp;
        this.keywords = Set.copyOf(b.keywords);
        Set<String> up = new LinkedHashSet<>(b.keywords);
        up.addAll(b.addOnUpgrade);
        up.removeAll(b.removeOnUpgrade);
        this.upgradedKeywords = Set.copyOf(up);
        this.text = b.text;
        this.upgradedText = b.upgradedText == null ? b.text : b.upgradedText;
        this.effect = b.effect;
        this.hooks = b.hooks;
        this.condition = b.condition;
        this.damageFormula = b.damageFormula;
        this.upgradable = b.upgradable;
        this.art = b.art == null ? b.id : b.art;
    }

    public static Builder builder(String id, String name, String color, String type, String rarity) {
        return new Builder(id, name, color, type, rarity);
    }

    public String id() { return id; }
    public String name() { return name; }
    /** One of the {@link CardColor} constants. */
    public String color() { return color; }
    /** One of the {@link CardType} constants. */
    public String type() { return type; }
    /** One of the {@link CardRarity} constants. */
    public String rarity() { return rarity; }
    /** One of the {@link CardTarget} constants. */
    public String target() { return target; }
    public CardEffect effect() { return effect; }
    /** In-pile behaviour, or {@code null} when the card has none. */
    public CardHooks hooks() { return hooks; }
    /** Extra play requirement, or {@code null} when only Energy matters. */
    public PlayCondition condition() { return condition; }
    /** Custom per-hit base damage ("damage equal to your Block"), or {@code null} to use {@code {D}}. */
    public DamageFormula damageFormula() { return damageFormula; }
    public boolean upgradable() { return upgradable; }
    /** Key of the card illustration; defaults to the card id. */
    public String art() { return art; }

    public int cost(boolean upgraded) { return upgraded ? upgradedCost : cost; }
    public int damage(boolean upgraded) { return upgraded ? damage + damageUp : damage; }
    public int block(boolean upgraded) { return upgraded ? block + blockUp : block; }
    public int magic(boolean upgraded) { return upgraded ? magic + magicUp : magic; }
    public int combo(boolean upgraded) { return upgraded ? combo + comboUp : combo; }
    public Set<String> keywords(boolean upgraded) { return upgraded ? upgradedKeywords : keywords; }
    /** Raw text with placeholders; keywords are not included. */
    public String text(boolean upgraded) { return upgraded ? upgradedText : text; }

    @Override
    public String toString() {
        return id;
    }

    /** Fluent builder; see the class comment for an example. */
    public static final class Builder {
        private final String id;
        private final String name;
        private final String color;
        private final String type;
        private final String rarity;
        private String target = CardTarget.NONE;
        private int cost = 1;
        private int upgradedCost = Integer.MIN_VALUE;
        private int damage;
        private int damageUp;
        private int block;
        private int blockUp;
        private int magic;
        private int magicUp;
        private int combo;
        private int comboUp;
        private final Set<String> keywords = new LinkedHashSet<>();
        private final Set<String> addOnUpgrade = new LinkedHashSet<>();
        private final Set<String> removeOnUpgrade = new LinkedHashSet<>();
        private String text = "";
        private String upgradedText;
        private CardEffect effect = p -> { };
        private CardHooks hooks;
        private PlayCondition condition;
        private DamageFormula damageFormula;
        private boolean upgradable = true;
        private String art;

        private Builder(String id, String name, String color, String type, String rarity) {
            this.id = Objects.requireNonNull(id);
            this.name = Objects.requireNonNull(name);
            this.color = Objects.requireNonNull(color);
            this.type = Objects.requireNonNull(type);
            this.rarity = Objects.requireNonNull(rarity);
            if (type.equals(CardType.ATTACK)) {
                target = CardTarget.ENEMY;
            }
        }

        public Builder cost(int cost) { this.cost = cost; return this; }
        /** Cost after upgrading, when the upgrade changes it. */
        public Builder upgradedCost(int cost) { this.upgradedCost = cost; return this; }
        public Builder target(String target) { this.target = target; return this; }
        public Builder damage(int base, int upgradeDelta) { damage = base; damageUp = upgradeDelta; return this; }
        public Builder block(int base, int upgradeDelta) { block = base; blockUp = upgradeDelta; return this; }
        public Builder magic(int base, int upgradeDelta) { magic = base; magicUp = upgradeDelta; return this; }
        public Builder combo(int base, int upgradeDelta) { combo = base; comboUp = upgradeDelta; return this; }
        /** {@link Keyword} constants. */
        public Builder keywords(String... k) { keywords.addAll(Set.of(k)); return this; }
        public Builder addOnUpgrade(String... k) { addOnUpgrade.addAll(Set.of(k)); return this; }
        public Builder removeOnUpgrade(String... k) { removeOnUpgrade.addAll(Set.of(k)); return this; }
        public Builder text(String text) { this.text = text; return this; }
        /** Separate upgraded text, only needed when the upgrade changes wording, not just numbers. */
        public Builder upgradedText(String text) { this.upgradedText = text; return this; }
        public Builder effect(CardEffect effect) { this.effect = effect; return this; }
        public Builder hooks(CardHooks hooks) { this.hooks = hooks; return this; }
        public Builder condition(PlayCondition condition) { this.condition = condition; return this; }
        /** Computes per-hit base damage when it depends on the fight; {@code {D}} shows the result. */
        public Builder damageFormula(DamageFormula formula) { this.damageFormula = formula; return this; }
        public Builder notUpgradable() { this.upgradable = false; return this; }
        public Builder art(String art) { this.art = art; return this; }

        /** Status/curse convenience: unplayable, no cost shown. */
        public Builder unplayable() {
            this.cost = COST_NONE;
            this.keywords.add(Keyword.UNPLAYABLE);
            return this;
        }

        public CardDef build() {
            return new CardDef(this);
        }
    }

    /** Per-hit base damage computed from the fight; {@code target} may be null for previews. */
    @FunctionalInterface
    public interface DamageFormula {
        int baseDamage(Combat combat, Card card, Enemy target);
    }

    /** A condition that must hold for the card to be playable, beyond Energy. */
    @FunctionalInterface
    public interface PlayCondition {
        /** Returns {@code null} when playable, otherwise the reason shown to the player. */
        String blockedReason(Combat combat, Card card);
    }
}
