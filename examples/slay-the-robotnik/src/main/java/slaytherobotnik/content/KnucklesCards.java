package slaytherobotnik.content;

import slaytherobotnik.core.CardColor;
import slaytherobotnik.core.CardDef;
import slaytherobotnik.core.CardRarity;
import slaytherobotnik.core.CardTarget;
import slaytherobotnik.core.CardType;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.Powers;

/**
 * Knuckles' card pool (red). Knuckles hits hard and builds <b>Strength</b>: few cards, big
 * numbers, and a willingness to take a punch to throw one.
 */
public final class KnucklesCards {
    public static final String COLOR = CardColor.RED;

    public static final String PUNCH = "knuckles:punch";
    public static final String GUARD = "knuckles:guard";
    public static final String HAMMER_PUNCH = "knuckles:hammer_punch";

    private KnucklesCards() {
    }

    public static void register(Catalog c) {
        basics(c);
    }

    private static CardDef.Builder card(String id, String name, String type, String rarity) {
        return CardDef.builder(id, name, COLOR, type, rarity);
    }

    private static void basics(Catalog c) {
        // Strike.
        c.add(card(PUNCH, "Punch", CardType.ATTACK, CardRarity.BASIC)
                .cost(1).damage(6, 3)
                .text("Deal {D} damage.")
                .effect(p -> p.attack())
                .build());
        // Defend.
        c.add(card(GUARD, "Guard", CardType.SKILL, CardRarity.BASIC)
                .cost(1).target(CardTarget.SELF).block(5, 3)
                .text("Gain {B} Block.")
                .effect(p -> p.gainBlock())
                .build());
        // Bash, trading Vulnerable for Weak: Knuckles hits hard and blunts the counterattack.
        c.add(card(HAMMER_PUNCH, "Hammer Punch", CardType.ATTACK, CardRarity.BASIC)
                .cost(2).damage(10, 3).magic(2, 1)
                .text("Deal {D} damage. Apply {M} *Weak*.")
                .effect(p -> {
                    p.attack();
                    p.applyToTarget(Powers.weak(p.magic()));
                })
                .build());
    }
}
