package slaytherobotnik.content;

import slaytherobotnik.core.Card;
import slaytherobotnik.core.CardColor;
import slaytherobotnik.core.CardDef;
import slaytherobotnik.core.CardHooks;
import slaytherobotnik.core.CardRarity;
import slaytherobotnik.core.CardTarget;
import slaytherobotnik.core.CardType;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.Combat;
import slaytherobotnik.core.Creature;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.core.Keyword;
import slaytherobotnik.core.Power;
import slaytherobotnik.core.Powers;

/**
 * Sonic's card pool (blue). Sonic hits fast and often: <b>Combo</b> cards repeat their
 * effect, and every point of <b>Focus</b> adds one more repetition. He softens enemies with
 * stacks of <b>Vulnerable</b> and burns through his deck with <b>Exhaust</b>.
 *
 * <p>Each card notes the Slay the Spire card it is balanced against, where there is one.
 * This file is the model for the other character pools: one {@code c.add(...)} per card,
 * grouped by rarity, with any card-specific power defined at the bottom.
 */
public final class SonicCards {
    public static final String COLOR = CardColor.BLUE;

    public static final String SPIN_ATTACK = "sonic:spin_attack";
    public static final String SIDE_STEP = "sonic:side_step";
    public static final String SPIN_DASH = "sonic:spin_dash";
    public static final String HOMING_ATTACK = "sonic:homing_attack";

    private SonicCards() {
    }

    public static void register(Catalog c) {
        basics(c);
        commons(c);
        uncommons(c);
        rares(c);
    }

    private static CardDef.Builder card(String id, String name, String type, String rarity) {
        return CardDef.builder(id, name, COLOR, type, rarity);
    }

    // ------------------------------------------------------------------ basic

    private static void basics(Catalog c) {
        // Strike.
        c.add(card(SPIN_ATTACK, "Spin Attack", CardType.ATTACK, CardRarity.BASIC)
                .cost(1).damage(6, 3)
                .text("Deal {D} damage.")
                .effect(p -> p.attack())
                .build());
        // Defend.
        c.add(card(SIDE_STEP, "Side Step", CardType.SKILL, CardRarity.BASIC)
                .cost(1).target(CardTarget.SELF).block(5, 3)
                .text("Gain {B} Block.")
                .effect(p -> p.gainBlock())
                .build());
        // Bash.
        c.add(card(SPIN_DASH, "Spin Dash", CardType.ATTACK, CardRarity.BASIC)
                .cost(2).damage(8, 2).magic(2, 1)
                .text("Deal {D} damage. Apply {M} *Vulnerable*.")
                .effect(p -> {
                    p.attack();
                    p.applyToTarget(Powers.vulnerable(p.magic()));
                })
                .build());
        // Sonic's signature starter: a two-hit Combo that grows with Focus.
        c.add(card(HOMING_ATTACK, "Homing Attack", CardType.ATTACK, CardRarity.BASIC)
                .cost(1).damage(3, 1).combo(2, 0)
                .text("*Combo* {C}: Deal {D} damage.")
                .effect(p -> p.combo(p::attack))
                .build());
    }

    // ------------------------------------------------------------------ common

    private static void commons(Catalog c) {
        // Pommel Strike.
        c.add(card("sonic:air_dash", "Air Dash", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).damage(7, 2).magic(1, 1)
                .text("Deal {D} damage. Draw {M|card}.")
                .effect(p -> {
                    p.attack();
                    p.draw(p.magic());
                })
                .build());
        // Iron Wave.
        c.add(card("sonic:slide_kick", "Slide Kick", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).damage(5, 2).block(5, 2)
                .text("Gain {B} Block. Deal {D} damage.")
                .effect(p -> {
                    p.gainBlock();
                    p.attack();
                })
                .build());
        // Sword Boomerang.
        c.add(card("sonic:bumper_bounce", "Bumper Bounce", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).target(CardTarget.RANDOM_ENEMY).damage(3, 0).combo(3, 1)
                .text("*Combo* {C}: Deal {D} damage to a random enemy.")
                .effect(p -> p.combo(p::attackRandom))
                .build());
        // Cleave, scaling with Focus.
        c.add(card("sonic:tornado_spin", "Tornado Spin", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).target(CardTarget.ALL_ENEMIES).damage(5, 2).combo(1, 0)
                .text("*Combo* {C}: Deal {D} damage to ALL enemies.")
                .effect(p -> p.combo(p::attackAll))
                .build());
        // Thunderclap.
        c.add(card("sonic:sonic_wind", "Sonic Wind", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).target(CardTarget.ALL_ENEMIES).damage(4, 3)
                .text("Deal {D} damage and apply 1 *Vulnerable* to ALL enemies.")
                .effect(p -> {
                    p.attackAll();
                    p.applyToAll(() -> Powers.vulnerable(1));
                })
                .build());
        c.add(card("sonic:too_slow", "Too Slow!", CardType.SKILL, CardRarity.COMMON)
                .cost(0).target(CardTarget.ENEMY).magic(2, 1)
                .keywords(Keyword.EXHAUST)
                .text("Apply {M} *Vulnerable*.")
                .effect(p -> p.applyToTarget(Powers.vulnerable(p.magic())))
                .build());
        c.add(card("sonic:wall_jump", "Wall Jump", CardType.SKILL, CardRarity.COMMON)
                .cost(1).target(CardTarget.SELF).block(3, 1).combo(2, 0)
                .text("*Combo* {C}: Gain {B} Block.")
                .effect(p -> p.combo(p::gainBlock))
                .build());
        c.add(card("sonic:insta_shield", "Insta-Shield", CardType.SKILL, CardRarity.COMMON)
                .cost(0).target(CardTarget.SELF).block(4, 3)
                .keywords(Keyword.EXHAUST)
                .text("Gain {B} Block.")
                .effect(p -> p.gainBlock())
                .build());
        // Dropkick, as a common with less damage.
        c.add(card("sonic:bounce_attack", "Bounce Attack", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).damage(6, 2)
                .text("Deal {D} damage. If the enemy is *Vulnerable*, gain 1 Energy.")
                .effect(p -> {
                    boolean vulnerable = p.target() != null && p.target().has(Powers.VULNERABLE);
                    p.attack();
                    if (vulnerable) {
                        p.gainEnergy(1);
                    }
                })
                .build());
        // Rewards holding it back: grows each turn it is Retained.
        c.add(card("sonic:drop_dash", "Drop Dash", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).damage(6, 3).magic(3, 1)
                .keywords(Keyword.RETAIN)
                .text("Deal {D} damage. Each time this is *Retained*, it deals {M} more damage this combat.")
                .hooks(CardHooks.builder()
                        .onRetain((combat, self) -> self.addBonusDamage(self.magic()))
                        .build())
                .effect(p -> p.attack())
                .build());
        // Seeing Red.
        c.add(card("sonic:chili_dog", "Chili Dog", CardType.SKILL, CardRarity.COMMON)
                .cost(1).upgradedCost(0).target(CardTarget.SELF)
                .keywords(Keyword.EXHAUST)
                .text("Gain 2 Energy.")
                .effect(p -> p.gainEnergy(2))
                .build());
        // Shrug It Off.
        c.add(card("sonic:spring", "Spring", CardType.SKILL, CardRarity.COMMON)
                .cost(1).target(CardTarget.SELF).block(8, 3)
                .text("Gain {B} Block. Draw 1 card.")
                .effect(p -> {
                    p.gainBlock();
                    p.draw(1);
                })
                .build());
        // Flex, for Focus.
        c.add(card("sonic:rev_up", "Rev Up", CardType.SKILL, CardRarity.COMMON)
                .cost(0).target(CardTarget.SELF).magic(1, 1)
                .text("Gain {M} *Focus* this turn.")
                .effect(p -> {
                    p.applyToSelf(Powers.focus(p.magic()));
                    p.applyToSelf(loseFocusAtEnd(p.magic()));
                })
                .build());
        c.add(card("sonic:spin_ball", "Spin Ball", CardType.ATTACK, CardRarity.COMMON)
                .cost(2).damage(6, 2).combo(2, 0)
                .text("*Combo* {C}: Deal {D} damage.")
                .effect(p -> p.combo(p::attack))
                .build());
        // Flash of Steel.
        c.add(card("sonic:jump_dash", "Jump Dash", CardType.ATTACK, CardRarity.COMMON)
                .cost(0).damage(3, 2)
                .text("Deal {D} damage. Draw 1 card.")
                .effect(p -> {
                    p.attack();
                    p.draw(1);
                })
                .build());
        // True Grit (upgraded form: the player chooses).
        c.add(card("sonic:skid_stop", "Skid Stop", CardType.SKILL, CardRarity.COMMON)
                .cost(1).target(CardTarget.SELF).block(7, 2)
                .text("Gain {B} Block. *Exhaust* a card from your hand.")
                .effect(p -> {
                    p.gainBlock();
                    p.exhaustChoice(1, false, null);
                })
                .build());
        // Pays off Vulnerable stacks.
        c.add(card("sonic:cheap_shot", "Cheap Shot", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).damage(4, 2).magic(2, 1)
                .text("Deal {D} damage. Deals {M} more for each *Vulnerable* on the enemy.")
                .damageFormula((combat, card, target) -> card.damage()
                        + (target == null ? 0 : card.magic() * target.amount(Powers.VULNERABLE)))
                .effect(p -> p.attack())
                .build());
    }

    // ------------------------------------------------------------------ uncommon

    private static void uncommons(Catalog c) {
        // Finisher: each other Attack this turn adds a repetition.
        c.add(card("sonic:light_speed_dash", "Light Speed Dash", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(1).damage(4, 1).combo(1, 1)
                .text("*Combo* {C}: Deal {D} damage. Repeats once more for each other Attack played this turn.")
                .effect(p -> p.repeat(p.combo() + Math.max(0, p.combat().attacksPlayedThisTurn() - 1), p::attack))
                .build());
        // Pummel.
        c.add(card("sonic:boost", "Boost", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(2).damage(5, 1).combo(3, 1)
                .text("*Combo* {C}: Deal {D} damage.")
                .effect(p -> p.combo(p::attack))
                .build());
        // Fire Shield dash: hits hard, burns a card.
        c.add(card("sonic:fire_dash", "Fire Dash", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(1).damage(10, 4)
                .text("Deal {D} damage. *Exhaust* a random card in your hand.")
                .effect(p -> {
                    p.attack();
                    var hand = p.player().hand();
                    if (!hand.isEmpty()) {
                        p.combat().exhaust(hand.get(p.combat().cardRng().nextInt(hand.size())));
                    }
                })
                .build());
        // Lightning Shield double jump.
        c.add(card("sonic:thunder_jump", "Thunder Jump", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(1).target(CardTarget.SELF).block(7, 3).damage(3, 1).combo(1, 0)
                .text("Gain {B} Block. *Combo* {C}: Deal {D} damage to a random enemy.")
                .effect(p -> {
                    p.gainBlock();
                    p.combo(p::attackRandom);
                })
                .build());
        // Bubble Shield bounce.
        c.add(card("sonic:bubble_bounce", "Bubble Bounce", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(1).damage(7, 3)
                .text("Deal {D} damage. Gain Block equal to the HP the enemy lost.")
                .effect(p -> {
                    Enemy target = p.target();
                    int before = target.hp();
                    p.attack();
                    int lost = before - target.hp();
                    if (lost > 0) {
                        p.combat().gainBlock(p.player(), lost, null);
                    }
                })
                .build());
        // Feel No Pain.
        c.add(card("sonic:peel_out", "Peel Out", CardType.POWER, CardRarity.UNCOMMON)
                .cost(1).target(CardTarget.SELF).magic(3, 1)
                .text("Whenever a card is *Exhausted*, gain {M} Block.")
                .effect(p -> p.applyToSelf(peelOut(p.magic())))
                .build());
        // Dark Embrace.
        c.add(card("sonic:gotta_juice", "Gotta Juice!", CardType.POWER, CardRarity.UNCOMMON)
                .cost(2).upgradedCost(1).target(CardTarget.SELF)
                .text("Whenever a card is *Exhausted*, draw 1 card.")
                .effect(p -> p.applyToSelf(gottaJuice(1)))
                .build());
        // Inflame, for Focus.
        c.add(card("sonic:way_past_cool", "Way Past Cool", CardType.POWER, CardRarity.UNCOMMON)
                .cost(1).upgradedCost(0).target(CardTarget.SELF)
                .text("Gain 1 *Focus*.")
                .effect(p -> p.applyToSelf(Powers.focus(1)))
                .build());
        // Second Wind.
        c.add(card("sonic:skid_turn", "Skid Turn", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(1).target(CardTarget.SELF).block(5, 2)
                .text("*Exhaust* all non-Attack cards in your hand. Gain {B} Block for each.")
                .effect(p -> {
                    for (Card card : java.util.List.copyOf(p.player().hand())) {
                        if (!card.type().equals(CardType.ATTACK)) {
                            p.combat().exhaust(card);
                            p.gainBlock();
                        }
                    }
                })
                .build());
        c.add(card("sonic:spindash_charge", "Spindash Charge", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(1).upgradedCost(0).target(CardTarget.SELF).magic(2, 0)
                .text("Your next *Combo* card this turn repeats {M} more times.")
                .effect(p -> p.applyToSelf(spindashCharge(p.magic())))
                .build());
        // Bouncing Flask's cousin: doubles a Vulnerable stack.
        c.add(card("sonic:super_peel_out", "Super Peel Out", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(2).damage(10, 4)
                .text("Deal {D} damage. Double the enemy's *Vulnerable*.")
                .effect(p -> {
                    p.attack();
                    Enemy target = p.target();
                    if (target != null && target.isActive() && target.has(Powers.VULNERABLE)) {
                        p.applyToTarget(Powers.vulnerable(target.amount(Powers.VULNERABLE)));
                    }
                })
                .build());
        // Burning Pact.
        c.add(card("sonic:afterburner", "Afterburner", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(1).target(CardTarget.SELF).magic(2, 1)
                .text("*Exhaust* a card. Draw {M|card}.")
                .effect(p -> p.exhaustChoice(1, false, chosen -> p.draw(p.magic())))
                .build());
        // Shockwave.
        c.add(card("sonic:show_off", "Show Off", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(2).target(CardTarget.ALL_ENEMIES).magic(3, 2)
                .keywords(Keyword.EXHAUST)
                .text("Apply {M} *Weak* and {M} *Vulnerable* to ALL enemies.")
                .effect(p -> {
                    p.applyToAll(() -> Powers.weak(p.magic()));
                    p.applyToAll(() -> Powers.vulnerable(p.magic()));
                })
                .build());
        // Stacks Vulnerable with every hit; Focus makes it snowball.
        c.add(card("sonic:twirl_attack", "Twirl Attack", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(1).damage(2, 1).combo(2, 0)
                .text("*Combo* {C}: Deal {D} damage and apply 1 *Vulnerable*.")
                .effect(p -> p.combo(() -> {
                    p.attack();
                    if (p.target() != null && p.target().isActive()) {
                        p.applyToTarget(Powers.vulnerable(1));
                    }
                }))
                .build());
        c.add(card("sonic:blue_tornado", "Blue Tornado", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(1).damage(9, 3)
                .text("Remove all of the enemy's Block. Deal {D} damage.")
                .effect(p -> {
                    if (p.target() != null) {
                        p.combat().removeBlock(p.target());
                    }
                    p.attack();
                })
                .build());
        // Backflip-like card flow for exhaust decks.
        c.add(card("sonic:rapid_fire", "Rapid Fire", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(1).target(CardTarget.RANDOM_ENEMY).damage(4, 2)
                .text("Deal {D} damage to a random enemy for each card *Exhausted* this combat (at least once).")
                .effect(p -> p.repeat(Math.max(1, p.combat().cardsExhaustedThisCombat()), p::attackRandom))
                .build());
        // Footwork-style defence that scales with Focus.
        c.add(card("sonic:dodge_roll", "Dodge Roll", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(2).target(CardTarget.SELF).block(4, 1).combo(3, 0)
                .text("*Combo* {C}: Gain {B} Block. Draw 1 card.")
                .effect(p -> {
                    p.combo(p::gainBlock);
                    p.draw(1);
                })
                .build());
    }

    // ------------------------------------------------------------------ rare

    private static void rares(Catalog c) {
        // Demon Form, for Focus.
        c.add(card("sonic:super_sonic", "Super Sonic", CardType.POWER, CardRarity.RARE)
                .cost(3).upgradedCost(2).target(CardTarget.SELF)
                .text("At the start of each turn, gain 1 *Focus*.")
                .effect(p -> p.applyToSelf(superSonic(1)))
                .build());
        // Whirlwind.
        c.add(card("sonic:spin_cyclone", "Spin Cyclone", CardType.ATTACK, CardRarity.RARE)
                .cost(CardDef.COST_X).target(CardTarget.ALL_ENEMIES).damage(5, 3)
                .text("*Combo* X: Deal {D} damage to ALL enemies.")
                .effect(p -> p.repeat(p.combat().comboCount(p.card(), p.x()), p::attackAll))
                .build());
        // After Image.
        c.add(card("sonic:speed_lines", "Speed Lines", CardType.POWER, CardRarity.RARE)
                .cost(1).target(CardTarget.SELF)
                .addOnUpgrade(Keyword.INNATE)
                .text("Whenever you play a card, gain 1 Block.")
                .effect(p -> p.applyToSelf(speedLines(1)))
                .build());
        // Limit Break.
        c.add(card("sonic:max_speed", "Max Speed", CardType.SKILL, CardRarity.RARE)
                .cost(1).target(CardTarget.SELF)
                .keywords(Keyword.EXHAUST).removeOnUpgrade(Keyword.EXHAUST)
                .text("Double your *Focus*.")
                .effect(p -> {
                    int focus = p.player().focus();
                    if (focus > 0) {
                        p.applyToSelf(Powers.focus(focus));
                    }
                })
                .build());
        c.add(card("sonic:light_speed_attack", "Light Speed Attack", CardType.ATTACK, CardRarity.RARE)
                .cost(2).damage(7, 2).combo(2, 1)
                .text("*Combo* {C}: Deal {D} damage. Apply 2 *Vulnerable*.")
                .effect(p -> {
                    p.combo(p::attack);
                    p.applyToTarget(Powers.vulnerable(2));
                })
                .build());
        // Blur.
        c.add(card("sonic:afterimage", "Afterimage", CardType.SKILL, CardRarity.RARE)
                .cost(1).target(CardTarget.SELF).block(8, 3)
                .text("Gain {B} Block. Block is not removed at the start of your next turn.")
                .effect(p -> {
                    p.gainBlock();
                    p.applyToSelf(keepBlockNextTurn());
                })
                .build());
        // Exhume.
        c.add(card("sonic:comeback", "Comeback", CardType.SKILL, CardRarity.RARE)
                .cost(1).upgradedCost(0).target(CardTarget.SELF)
                .keywords(Keyword.EXHAUST)
                .text("Put a card from your exhaust pile into your hand.")
                .effect(p -> p.combat().chooseFrom("Choose a card to return to your hand.",
                        () -> {
                            var list = new java.util.ArrayList<>(p.player().exhaustPile());
                            list.remove(p.card());
                            return list;
                        }, 1, 1, chosen -> chosen.forEach(p.combat()::moveToHand)))
                .build());
        // Corruption.
        c.add(card("sonic:boost_mode", "Boost Mode", CardType.POWER, CardRarity.RARE)
                .cost(3).upgradedCost(2).target(CardTarget.SELF)
                .text("Skills cost 0. Whenever you play a Skill, *Exhaust* it.")
                .effect(p -> p.applyToSelf(boostMode()))
                .build());
        // Paper Phrog as a Power.
        c.add(card("sonic:sharp_eye", "Sharp Eye", CardType.POWER, CardRarity.RARE)
                .cost(2).upgradedCost(1).target(CardTarget.SELF)
                .text("*Vulnerable* enemies take 75% more damage instead of 50%.")
                .effect(p -> p.applyToSelf(sharpEye()))
                .build());
    }

    // ------------------------------------------------------------------ powers

    static Power loseFocusAtEnd(int amount) {
        return new Power("sonic:focus_down", "Focus Down", Power.DEBUFF, amount) {
            @Override
            public void atTurnEnd(Combat c) {
                c.applyPower(owner(), owner(), Powers.focus(-amount()));
                c.removePower(owner(), id());
            }

            @Override
            public String description() {
                return "At the end of your turn, lose " + amount() + " Focus.";
            }
        };
    }

    static Power peelOut(int amount) {
        return new Power("sonic:peel_out", "Peel Out", Power.BUFF, amount) {
            @Override
            public void onCardExhausted(Combat c, Card card) {
                c.gainBlock(owner(), amount(), null);
            }

            @Override
            public String description() {
                return "Whenever a card is Exhausted, gain " + amount() + " Block.";
            }
        };
    }

    static Power gottaJuice(int amount) {
        return new Power("sonic:gotta_juice", "Gotta Juice!", Power.BUFF, amount) {
            @Override
            public void onCardExhausted(Combat c, Card card) {
                c.draw(amount());
            }

            @Override
            public String description() {
                return "Whenever a card is Exhausted, draw " + amount() + (amount() == 1 ? " card." : " cards.");
            }
        };
    }

    static Power spindashCharge(int amount) {
        return new Power("sonic:spindash_charge", "Spindash Charge", Power.BUFF, amount) {
            @Override
            public int modifyCombo(Combat c, Card card, int count) {
                return card.combo() > 0 || card.isXCost() ? count + amount() : count;
            }

            @Override
            public void afterCardPlayed(Combat c, Card card) {
                if (card.combo() > 0 || (card.isXCost() && card.color().equals(COLOR))) {
                    c.removePower(owner(), id());
                }
            }

            @Override
            public void atTurnEnd(Combat c) {
                c.removePower(owner(), id());
            }

            @Override
            public String description() {
                return "Your next Combo card this turn repeats " + amount() + " more times.";
            }
        };
    }

    static Power superSonic(int amount) {
        return new Power("sonic:super_sonic", "Super Sonic", Power.BUFF, amount) {
            @Override
            public void atTurnStart(Combat c) {
                c.applyPower(owner(), owner(), Powers.focus(amount()));
            }

            @Override
            public String description() {
                return "At the start of each turn, gain " + amount() + " Focus.";
            }
        };
    }

    static Power speedLines(int amount) {
        return new Power("sonic:speed_lines", "Speed Lines", Power.BUFF, amount) {
            @Override
            public void onCardPlayed(Combat c, Card card) {
                c.gainBlock(owner(), amount(), null);
            }

            @Override
            public String description() {
                return "Whenever you play a card, gain " + amount() + " Block.";
            }
        };
    }

    static Power keepBlockNextTurn() {
        return new Power("sonic:afterimage", "Afterimage", Power.BUFF, 1) {
            @Override
            public boolean retainsBlock(Combat c) {
                return true;
            }

            @Override
            public void atTurnStartPostDraw(Combat c) {
                c.removePower(owner(), id());
            }

            @Override
            public String description() {
                return "Block is not removed at the start of your next turn.";
            }
        };
    }

    static Power boostMode() {
        return new Power("sonic:boost_mode", "Boost Mode", Power.BUFF, 1) {
            private Card pendingExhaust;

            {
                nonStacking();
            }

            @Override
            public int modifyCost(Combat c, Card card, int cost) {
                return card.type().equals(CardType.SKILL) ? 0 : cost;
            }

            @Override
            public void onCardPlayed(Combat c, Card card) {
                if (card.type().equals(CardType.SKILL)) {
                    pendingExhaust = card;
                }
            }

            @Override
            public void afterCardPlayed(Combat c, Card card) {
                if (card == pendingExhaust) {
                    pendingExhaust = null;
                    if (c.player().discardPile().contains(card)) {
                        c.exhaust(card);
                    }
                }
            }

            @Override
            public String description() {
                return "Skills cost 0. Whenever you play a Skill, Exhaust it.";
            }
        };
    }

    static Power sharpEye() {
        return new Power("sonic:sharp_eye", "Sharp Eye", Power.BUFF, 1) {
            @Override
            public float modifyVulnerableMultiplier(Combat c, Creature target, float multiplier) {
                return target.isPlayer() ? multiplier : Math.max(multiplier, 1.75f);
            }

            @Override
            public String description() {
                return "Vulnerable enemies take 75% more damage instead of 50%.";
            }
        };
    }
}
