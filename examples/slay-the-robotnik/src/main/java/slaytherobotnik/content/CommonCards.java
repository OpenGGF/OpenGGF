package slaytherobotnik.content;

import slaytherobotnik.core.CardColor;
import slaytherobotnik.core.CardDef;
import slaytherobotnik.core.CardHooks;
import slaytherobotnik.core.CardRarity;
import slaytherobotnik.core.CardTarget;
import slaytherobotnik.core.CardType;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.Keyword;
import slaytherobotnik.core.Powers;

/**
 * Cards that belong to no character: colourless cards (shops and events), Status cards
 * enemies shuffle into your deck, and Curses.
 */
public final class CommonCards {
    public static final String DIZZY = "status:dizzy";
    public static final String BURN = "status:burn";
    public static final String DENT = "status:dent";
    public static final String OIL_SLICK = "status:oil_slick";
    public static final String STATIC = "status:static";

    public static final String LOST_RINGS = "curse:lost_rings";
    public static final String SPRAINED_ANKLE = "curse:sprained_ankle";
    public static final String ROBOTNIKS_LAUGH = "curse:robotniks_laugh";
    public static final String SELF_DOUBT = "curse:self_doubt";
    public static final String RUST = "curse:rust";
    public static final String TANGLED = "curse:tangled";

    private CommonCards() {
    }

    public static void register(Catalog c) {
        statuses(c);
        curses(c);
        colorless(c);
    }

    // ------------------------------------------------------------------ statuses

    private static void statuses(Catalog c) {
        // Dazed.
        c.add(CardDef.builder(DIZZY, "Dizzy", CardColor.STATUS, CardType.STATUS, CardRarity.SPECIAL)
                .unplayable().keywords(Keyword.ETHEREAL)
                .text("Stars are circling your head.")
                .build());
        // Burn.
        c.add(CardDef.builder(BURN, "Burn", CardColor.STATUS, CardType.STATUS, CardRarity.SPECIAL)
                .unplayable().magic(2, 2)
                .text("At the end of your turn, take {M} damage.")
                .hooks(CardHooks.builder()
                        .onEndOfTurnInHand((combat, self) -> combat.damagePlayer(self.magic()))
                        .build())
                .build());
        // Wound.
        c.add(CardDef.builder(DENT, "Dent", CardColor.STATUS, CardType.STATUS, CardRarity.SPECIAL)
                .unplayable()
                .text("Something got bent.")
                .build());
        // Slimed.
        c.add(CardDef.builder(OIL_SLICK, "Oil Slick", CardColor.STATUS, CardType.STATUS, CardRarity.SPECIAL)
                .cost(1).target(CardTarget.NONE).keywords(Keyword.EXHAUST)
                .text("Wipe it off.")
                .build());
        // Void.
        c.add(CardDef.builder(STATIC, "Static", CardColor.STATUS, CardType.STATUS, CardRarity.SPECIAL)
                .unplayable().keywords(Keyword.ETHEREAL)
                .text("When drawn, lose 1 Energy.")
                .hooks(CardHooks.builder()
                        .onDraw((combat, self) -> combat.loseEnergy(1))
                        .build())
                .build());
    }

    // ------------------------------------------------------------------ curses

    private static CardDef.Builder curse(String id, String name) {
        return CardDef.builder(id, name, CardColor.CURSE, CardType.CURSE, CardRarity.CURSE).unplayable();
    }

    private static void curses(Catalog c) {
        // Regret.
        c.add(curse(LOST_RINGS, "Lost Rings")
                .text("At the end of your turn, lose 1 HP for each card in your hand.")
                .hooks(CardHooks.builder()
                        .onEndOfTurnInHand((combat, self) -> combat.loseHp(combat.player(),
                                combat.player().hand().size()))
                        .build())
                .build());
        // Injury.
        c.add(curse(SPRAINED_ANKLE, "Sprained Ankle")
                .text("Ouch.")
                .build());
        // Pain.
        c.add(curse(ROBOTNIKS_LAUGH, "Robotnik's Laugh")
                .text("While in your hand, lose 1 HP whenever you play a card.")
                .hooks(CardHooks.builder()
                        .onOtherCardPlayed((combat, self, played) -> combat.loseHp(combat.player(), 1))
                        .build())
                .build());
        // Doubt.
        c.add(curse(SELF_DOUBT, "Self-Doubt")
                .text("At the end of your turn, gain 1 *Weak*.")
                .hooks(CardHooks.builder()
                        .onEndOfTurnInHand((combat, self) -> combat.applyPower(null, combat.player(), Powers.weak(1)))
                        .build())
                .build());
        // Decay.
        c.add(curse(RUST, "Rust")
                .text("At the end of your turn, take 2 damage.")
                .hooks(CardHooks.builder()
                        .onEndOfTurnInHand((combat, self) -> combat.damagePlayer(2))
                        .build())
                .build());
        // Clumsy.
        c.add(curse(TANGLED, "Tangled")
                .keywords(Keyword.ETHEREAL)
                .text("Shoelaces everywhere.")
                .build());
    }

    // ------------------------------------------------------------------ colorless

    private static CardDef.Builder colorless(String id, String name, String type, String rarity) {
        return CardDef.builder(id, name, CardColor.COLORLESS, type, rarity);
    }

    private static void colorless(Catalog c) {
        // Finesse-like quick draw.
        c.add(colorless("colorless:speed_shoes", "Speed Shoes", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(0).target(CardTarget.SELF).magic(2, 1)
                .keywords(Keyword.EXHAUST)
                .text("Draw {M|card}.")
                .effect(p -> p.draw(p.magic()))
                .build());
        // Swift Strike / Flash of Steel family.
        c.add(colorless("colorless:flicky_flock", "Flicky Flock", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(1).target(CardTarget.RANDOM_ENEMY).damage(3, 1).combo(3, 0)
                .text("*Combo* {C}: Deal {D} damage to a random enemy.")
                .effect(p -> p.combo(p::attackRandom))
                .build());
        // Good Instincts.
        c.add(colorless("colorless:blue_shield", "Blue Shield", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(0).target(CardTarget.SELF).block(6, 3)
                .text("Gain {B} Block.")
                .effect(p -> p.gainBlock())
                .build());
        // Panacea.
        c.add(colorless("colorless:super_ring", "Super Ring", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(0).target(CardTarget.SELF).magic(1, 1)
                .keywords(Keyword.EXHAUST)
                .text("Gain {M} *Artifact*.")
                .effect(p -> p.applyToSelf(Powers.artifact(p.magic())))
                .build());
        // Dark Shackles.
        c.add(colorless("colorless:spring_trap", "Spring Trap", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(0).target(CardTarget.ENEMY).magic(9, 6)
                .keywords(Keyword.EXHAUST)
                .text("Enemy loses {M} *Strength* this turn.")
                .effect(p -> {
                    int before = p.target().amount(Powers.STRENGTH);
                    p.applyToTarget(Powers.strength(-p.magic()));
                    // Only give the Strength back if Artifact didn't block the loss.
                    if (p.target().amount(Powers.STRENGTH) < before) {
                        p.combat().applyPower(p.player(), p.target(), regainStrength(p.magic()));
                    }
                })
                .build());
        // Hand of Greed.
        c.add(colorless("colorless:ring_heist", "Ring Heist", CardType.ATTACK, CardRarity.RARE)
                .cost(2).damage(20, 5).magic(20, 5)
                .text("Deal {D} damage. If this kills a non-minion enemy, gain {M} rings.")
                .effect(p -> {
                    var target = p.target();
                    p.attack();
                    if (target != null && target.isDead() && !target.isMinion()) {
                        p.combat().gainRings(p.magic());
                    }
                })
                .build());
        // Apparition.
        c.add(colorless("colorless:invincibility", "Invincibility", CardType.SKILL, CardRarity.RARE)
                .cost(1).target(CardTarget.SELF)
                .keywords(Keyword.EXHAUST, Keyword.ETHEREAL).removeOnUpgrade(Keyword.ETHEREAL)
                .text("Gain 1 *Intangible*.")
                .effect(p -> p.applyToSelf(Powers.intangible(1)))
                .build());
        // Master of Strategy.
        c.add(colorless("colorless:master_plan", "Master Plan", CardType.SKILL, CardRarity.RARE)
                .cost(0).target(CardTarget.SELF).magic(3, 1)
                .keywords(Keyword.EXHAUST)
                .text("Draw {M|card}.")
                .effect(p -> p.draw(p.magic()))
                .build());
        // Bandage Up.
        c.add(colorless("colorless:chili_dog_combo", "Chili Dog Combo", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(0).target(CardTarget.SELF).magic(4, 2)
                .keywords(Keyword.EXHAUST)
                .text("Heal {M} HP.")
                .effect(p -> p.combat().healPlayer(p.magic()))
                .build());
        // Trip.
        c.add(colorless("colorless:banana_peel", "Banana Peel", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(0).target(CardTarget.ENEMY).magic(2, 0)
                .addOnUpgrade(Keyword.RETAIN)
                .text("Apply {M} *Vulnerable*.")
                .effect(p -> p.applyToTarget(Powers.vulnerable(p.magic())))
                .build());
    }

    private static slaytherobotnik.core.Power regainStrength(int amount) {
        return new slaytherobotnik.core.Power("colorless:spring_trap_regain", "Shaken", slaytherobotnik.core.Power.BUFF,
                amount) {
            @Override
            public void atRoundEnd(slaytherobotnik.core.Combat combat) {
                combat.applyPower(owner(), owner(), Powers.strength(amount()));
                combat.removePower(owner(), id());
            }

            @Override
            public String description() {
                return "Regains " + amount() + " Strength at the end of the round.";
            }
        };
    }
}
