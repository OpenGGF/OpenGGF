package slaytherobotnik.content;

import slaytherobotnik.core.CardColor;
import slaytherobotnik.core.CardDef;
import slaytherobotnik.core.CardRarity;
import slaytherobotnik.core.CardTarget;
import slaytherobotnik.core.CardType;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.Keyword;
import slaytherobotnik.core.Powers;

/**
 * Tails' card pool (orange). Tails is the gadgeteer: he discards to fuel his tricks, builds
 * Block with <b>Dexterity</b>, and throws <b>Ring Bombs</b> — free, Exhausting attacks he
 * creates in bulk, the way the Silent uses Shivs.
 */
public final class TailsCards {
    public static final String COLOR = CardColor.ORANGE;

    public static final String TAIL_SWIPE = "tails:tail_swipe";
    public static final String TAIL_GUARD = "tails:tail_guard";
    public static final String TAIL_FLICK = "tails:tail_flick";
    public static final String TINKER = "tails:tinker";
    public static final String RING_BOMB = "tails:ring_bomb";

    private TailsCards() {
    }

    public static void register(Catalog c) {
        basics(c);
    }

    private static CardDef.Builder card(String id, String name, String type, String rarity) {
        return CardDef.builder(id, name, COLOR, type, rarity);
    }

    private static void basics(Catalog c) {
        // Strike.
        c.add(card(TAIL_SWIPE, "Tail Swipe", CardType.ATTACK, CardRarity.BASIC)
                .cost(1).damage(6, 3)
                .text("Deal {D} damage.")
                .effect(p -> p.attack())
                .build());
        // Defend.
        c.add(card(TAIL_GUARD, "Tail Guard", CardType.SKILL, CardRarity.BASIC)
                .cost(1).target(CardTarget.SELF).block(5, 3)
                .text("Gain {B} Block.")
                .effect(p -> p.gainBlock())
                .build());
        // Neutralize.
        c.add(card(TAIL_FLICK, "Tail Flick", CardType.ATTACK, CardRarity.BASIC)
                .cost(0).damage(3, 1).magic(1, 1)
                .text("Deal {D} damage. Apply {M} *Weak*.")
                .effect(p -> {
                    p.attack();
                    p.applyToTarget(Powers.weak(p.magic()));
                })
                .build());
        // Survivor.
        c.add(card(TINKER, "Tinker", CardType.SKILL, CardRarity.BASIC)
                .cost(1).target(CardTarget.SELF).block(8, 3)
                .text("Gain {B} Block. Discard 1 card.")
                .effect(p -> {
                    p.gainBlock();
                    p.discardChoice(1, null);
                })
                .build());
        // The Shiv: Tails' token card, created by his cards and relics.
        c.add(card(RING_BOMB, "Ring Bomb", CardType.ATTACK, CardRarity.SPECIAL)
                .cost(0).damage(4, 2)
                .keywords(Keyword.EXHAUST)
                .text("Deal {D} damage.")
                .effect(p -> p.attack())
                .build());
    }
}
