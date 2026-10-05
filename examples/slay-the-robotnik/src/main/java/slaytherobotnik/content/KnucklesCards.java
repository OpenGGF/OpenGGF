package slaytherobotnik.content;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.CardColor;
import slaytherobotnik.core.CardDef;
import slaytherobotnik.core.CardHooks;
import slaytherobotnik.core.CardRarity;
import slaytherobotnik.core.CardTarget;
import slaytherobotnik.core.CardType;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.ChoiceRequest;
import slaytherobotnik.core.Combat;
import slaytherobotnik.core.Creature;
import slaytherobotnik.core.DamageType;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.core.IntentKind;
import slaytherobotnik.core.Keyword;
import slaytherobotnik.core.Play;
import slaytherobotnik.core.Power;
import slaytherobotnik.core.Powers;

/**
 * Knuckles' card pool (red). Knuckles hits hard and builds <b>Strength</b>: few cards, big
 * numbers, and a willingness to take a punch to throw one. His cards multiply Strength
 * (Haymaker, multi-hit flurries), trade HP for power (Bare Knuckle, Battle Scars), turn a
 * wall of Block into damage (Shoulder Charge, Dig In, Eternal Vigil), and dig through his
 * deck like the treasure hunter he is.
 *
 * <p>Each card notes the Slay the Spire card it is balanced against, where there is one;
 * the pool mirrors the Ironclad's numbers. The layout follows {@link SonicCards}.
 */
public final class KnucklesCards {
    public static final String COLOR = CardColor.RED;

    public static final String PUNCH = "knuckles:punch";
    public static final String GUARD = "knuckles:guard";
    public static final String HAMMER_PUNCH = "knuckles:hammer_punch";

    /** Cards whose name contains this word count for Signature Punch. */
    public static final String PUNCH_WORD = "Punch";

    private KnucklesCards() {
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

    // ------------------------------------------------------------------ common

    private static void commons(Catalog c) {
        // Heavy Blade: Strength counts once in the damage pipeline, the formula adds the rest.
        c.add(card("knuckles:haymaker", "Haymaker", CardType.ATTACK, CardRarity.COMMON)
                .cost(2).damage(14, 0).magic(3, 2)
                .text("Deal {D} damage. *Strength* affects this card {M} times.")
                .damageFormula((combat, card, target) -> card.damage()
                        + (card.magic() - 1) * combat.player().strength())
                .effect(p -> p.attack())
                .build());
        // Twin Strike.
        c.add(card("knuckles:one_two_punch", "One-Two Punch", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).damage(5, 2)
                .text("Deal {D} damage twice.")
                .effect(p -> p.repeat(2, p::attack))
                .build());
        // Cleave: S3K Knuckles smashes the rock walls Sonic has to go around.
        c.add(card("knuckles:rock_smash", "Rock Smash", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).target(CardTarget.ALL_ENEMIES).damage(8, 3)
                .text("Deal {D} damage to ALL enemies.")
                .effect(p -> p.attackAll())
                .build());
        // Thunderclap, with Knuckles' Weak in place of Vulnerable.
        c.add(card("knuckles:ground_pound", "Ground Pound", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).target(CardTarget.ALL_ENEMIES).damage(4, 3)
                .text("Deal {D} damage and apply 1 *Weak* to ALL enemies.")
                .effect(p -> {
                    p.attackAll();
                    p.applyToAll(() -> Powers.weak(1));
                })
                .build());
        // Sword Boomerang.
        c.add(card("knuckles:claw_swipes", "Claw Swipes", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).target(CardTarget.RANDOM_ENEMY).damage(3, 0).magic(3, 1)
                .text("Deal {D} damage to a random enemy {M} times.")
                .effect(p -> p.repeat(p.magic(), p::attackRandom))
                .build());
        // Body Slam.
        c.add(card("knuckles:shoulder_charge", "Shoulder Charge", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).upgradedCost(0)
                .text("Deal damage equal to your Block.")
                .damageFormula((combat, card, target) -> combat.player().block())
                .effect(p -> p.attack())
                .build());
        // Clash.
        c.add(card("knuckles:fisticuffs", "Fisticuffs", CardType.ATTACK, CardRarity.COMMON)
                .cost(0).damage(14, 4)
                .text("Can only be played if every card in your hand is an Attack. Deal {D} damage.")
                .condition((combat, card) -> {
                    for (Card other : combat.player().hand()) {
                        if (!other.type().equals(CardType.ATTACK)) {
                            return "Every card in your hand must be an Attack.";
                        }
                    }
                    return null;
                })
                .effect(p -> p.attack())
                .build());
        // Anger: Knuckles' temper feeds itself.
        c.add(card("knuckles:hothead", "Hothead", CardType.ATTACK, CardRarity.COMMON)
                .cost(0).damage(6, 2)
                .text("Deal {D} damage. Add a copy of this card to your discard pile.")
                .effect(p -> {
                    p.attack();
                    p.combat().addCreatedCard(p.card().duplicate(), Play.TO_DISCARD);
                })
                .build());
        // Wild Strike.
        c.add(card("knuckles:wild_swing", "Wild Swing", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).damage(12, 5)
                .text("Deal {D} damage. Shuffle a *Dent* into your draw pile.")
                .effect(p -> {
                    p.attack();
                    p.combat().addCreatedCard(new Card(p.combat().catalog().card(CommonCards.DENT)),
                            Play.TO_DRAW_PILE);
                })
                .build());
        // Perfected Strike: counts every card named "... Punch", itself included.
        c.add(card("knuckles:signature_punch", "Signature Punch", CardType.ATTACK, CardRarity.COMMON)
                .cost(2).damage(6, 0).magic(2, 1)
                .text("Deal {D} damage. Deals {M} more damage for each of your cards containing \"Punch\".")
                .damageFormula((combat, card, target) -> card.damage() + card.magic() * punchCards(combat, card))
                .effect(p -> p.attack())
                .build());
        // Pommel Strike.
        c.add(card("knuckles:jab", "Jab", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).damage(9, 1).magic(1, 1)
                .text("Deal {D} damage. Draw {M|card}.")
                .effect(p -> {
                    p.attack();
                    p.draw(p.magic());
                })
                .build());
        // Flex.
        c.add(card("knuckles:knuckle_crack", "Knuckle Crack", CardType.SKILL, CardRarity.COMMON)
                .cost(0).target(CardTarget.SELF).magic(2, 2)
                .text("Gain {M} *Strength*. At the end of your turn, lose {M} *Strength*.")
                .effect(p -> {
                    p.applyToSelf(Powers.strength(p.magic()));
                    p.applyToSelf(Powers.loseStrengthAtEnd(p.magic()));
                })
                .build());
        // Shrug It Off.
        c.add(card("knuckles:glide", "Glide", CardType.SKILL, CardRarity.COMMON)
                .cost(1).target(CardTarget.SELF).block(8, 3)
                .text("Gain {B} Block. Draw 1 card.")
                .effect(p -> {
                    p.gainBlock();
                    p.draw(1);
                })
                .build());
        // Armaments.
        c.add(card("knuckles:knuckle_up", "Knuckle Up", CardType.SKILL, CardRarity.COMMON)
                .cost(1).target(CardTarget.SELF).block(5, 0)
                .text("Gain {B} Block. Upgrade a card in your hand for the rest of combat.")
                .upgradedText("Gain {B} Block. Upgrade ALL cards in your hand for the rest of combat.")
                .effect(p -> {
                    p.gainBlock();
                    if (p.upgraded()) {
                        for (Card card : upgradable(p.player().hand())) {
                            p.combat().upgradeForCombat(card);
                        }
                    } else {
                        p.combat().requestChoice(new ChoiceRequest("Choose a card to upgrade.",
                                () -> upgradable(p.player().hand()), 1, 1, true,
                                chosen -> chosen.forEach(p.combat()::upgradeForCombat)));
                    }
                })
                .build());
        // Headbutt as a Skill: climb back up and try the same route again.
        c.add(card("knuckles:wall_climb", "Wall Climb", CardType.SKILL, CardRarity.COMMON)
                .cost(1).target(CardTarget.SELF).block(6, 3)
                .text("Gain {B} Block. Put a card from your discard pile on top of your draw pile.")
                .effect(p -> {
                    p.gainBlock();
                    p.combat().chooseFrom("Choose a card to put on top of your draw pile.", () -> {
                        List<Card> list = new ArrayList<>(p.player().discardPile());
                        list.remove(p.card());
                        return list;
                    }, 1, 1, chosen -> {
                        for (Card card : chosen) {
                            if (p.player().discardPile().remove(card)) {
                                p.player().drawPile().add(card);
                            }
                        }
                    });
                })
                .build());
        // Secret Weapon / Seek: dig up exactly the card you need.
        c.add(card("knuckles:treasure_hunt", "Treasure Hunt", CardType.SKILL, CardRarity.COMMON)
                .cost(1).upgradedCost(0).target(CardTarget.SELF)
                .keywords(Keyword.EXHAUST)
                .text("Put a card from your draw pile into your hand.")
                .effect(p -> p.combat().chooseFrom("Choose a card to put into your hand.", () -> {
                    // Sorted, so the choice does not reveal the draw order.
                    List<Card> list = new ArrayList<>(p.player().drawPile());
                    list.sort(Comparator.comparing(Card::name));
                    return list;
                }, 1, 1, chosen -> chosen.forEach(p.combat()::moveToHand)))
                .build());
    }

    // ------------------------------------------------------------------ uncommon

    private static void uncommons(Catalog c) {
        // Uppercut: Knuckles' rising spin from Sonic Adventure 2.
        c.add(card("knuckles:spiral_upper", "Spiral Upper", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(2).damage(13, 0).magic(1, 1)
                .text("Deal {D} damage. Apply {M} *Weak*. Apply {M} *Vulnerable*.")
                .effect(p -> {
                    p.attack();
                    p.applyToTarget(Powers.weak(p.magic()));
                    p.applyToTarget(Powers.vulnerable(p.magic()));
                })
                .build());
        // Hemokinesis.
        c.add(card("knuckles:bare_knuckle", "Bare Knuckle", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(1).damage(15, 5)
                .text("Lose 2 HP. Deal {D} damage.")
                .effect(p -> {
                    p.combat().loseHp(p.player(), 2);
                    p.attack();
                })
                .build());
        // Rampage.
        c.add(card("knuckles:mounting_fury", "Mounting Fury", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(1).damage(8, 0).magic(5, 3)
                .text("Deal {D} damage. Increase this card's damage by {M} this combat.")
                .effect(p -> {
                    p.attack();
                    p.card().addBonusDamage(p.magic());
                })
                .build());
        // Pummel: every hit carries Strength.
        c.add(card("knuckles:knuckle_barrage", "Knuckle Barrage", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(1).damage(2, 0).magic(4, 1)
                .keywords(Keyword.EXHAUST)
                .text("Deal {D} damage {M} times.")
                .effect(p -> p.repeat(p.magic(), p::attack))
                .build());
        // Blood for Blood, counting HP lost rather than hits taken.
        c.add(card("knuckles:payback", "Payback", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(4).upgradedCost(3).damage(18, 4)
                .text("Costs 1 less for every 5 HP you have lost this combat. Deal {D} damage.")
                .hooks(CardHooks.builder()
                        .costAdjuster((combat, self, cost) -> cost - combat.hpLostThisCombat() / 5)
                        .build())
                .effect(p -> p.attack())
                .build());
        // Carnage: the Sonic Adventure 2 dive, all or nothing.
        c.add(card("knuckles:drill_claw", "Drill Claw", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(2).damage(20, 8)
                .keywords(Keyword.ETHEREAL)
                .text("Deal {D} damage.")
                .effect(p -> p.attack())
                .build());
        // Bloodletting.
        c.add(card("knuckles:tough_it_out", "Tough It Out", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(0).target(CardTarget.SELF).magic(2, 1)
                .text("Lose 3 HP. Gain {M} Energy.")
                .effect(p -> {
                    p.combat().loseHp(p.player(), 3);
                    p.gainEnergy(p.magic());
                })
                .build());
        // Flame Barrier.
        c.add(card("knuckles:spiked_knuckles", "Spiked Knuckles", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(2).target(CardTarget.SELF).block(12, 4).magic(4, 2)
                .text("Gain {B} Block. Whenever you are attacked this turn, deal {M} damage back.")
                .effect(p -> {
                    p.gainBlock();
                    p.applyToSelf(spikedKnuckles(p.magic()));
                })
                .build());
        // Entrench. Doubling is not a card Block gain, so Dexterity and Frail do not apply.
        c.add(card("knuckles:dig_in", "Dig In", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(2).upgradedCost(1).target(CardTarget.SELF)
                .text("Double your Block.")
                .effect(p -> p.combat().gainBlock(p.player(), p.player().block(), null))
                .build());
        // Power Through.
        c.add(card("knuckles:take_the_hit", "Take the Hit", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(1).target(CardTarget.SELF).block(15, 5)
                .text("Add 2 *Dents* to your hand. Gain {B} Block.")
                .effect(p -> {
                    p.addToHand(CommonCards.DENT, 2, false);
                    p.gainBlock();
                })
                .build());
        // Spot Weakness.
        c.add(card("knuckles:sizing_up", "Sizing Up", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(1).target(CardTarget.ENEMY).magic(3, 1)
                .text("If the enemy intends to attack, gain {M} *Strength*.")
                .effect(p -> {
                    Enemy target = p.target();
                    if (target != null && target.nextMove() != null
                            && IntentKind.isAttack(target.nextMove().intent())) {
                        p.applyToSelf(Powers.strength(p.magic()));
                    }
                })
                .build());
        // Disarm.
        c.add(card("knuckles:knock_loose", "Knock Loose", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(1).target(CardTarget.ENEMY).magic(2, 1)
                .keywords(Keyword.EXHAUST)
                .text("Enemy loses {M} *Strength*.")
                .effect(p -> p.applyToTarget(Powers.strength(-p.magic())))
                .build());
        // Battle Trance: the Master Emerald radar points straight at what he needs.
        c.add(card("knuckles:emerald_radar", "Emerald Radar", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(0).target(CardTarget.SELF).magic(3, 1)
                .text("Draw {M|card}. You cannot draw additional cards this turn.")
                .effect(p -> {
                    p.draw(p.magic());
                    p.applyToSelf(Powers.noDraw());
                })
                .build());
        // Inflame.
        c.add(card("knuckles:echidna_pride", "Echidna Pride", CardType.POWER, CardRarity.UNCOMMON)
                .cost(1).target(CardTarget.SELF).magic(2, 1)
                .text("Gain {M} *Strength*.")
                .effect(p -> p.applyToSelf(Powers.strength(p.magic())))
                .build());
        // Rupture: any HP lost on your own turn (your cards, Fired Up, a Burn) counts.
        c.add(card("knuckles:battle_scars", "Battle Scars", CardType.POWER, CardRarity.UNCOMMON)
                .cost(1).target(CardTarget.SELF).magic(1, 1)
                .text("Whenever you lose HP during your turn, gain {M} *Strength*.")
                .effect(p -> p.applyToSelf(battleScars(p.magic())))
                .build());
        // Metallicize: the guardian never leaves his post.
        c.add(card("knuckles:guardians_duty", "Guardian's Duty", CardType.POWER, CardRarity.UNCOMMON)
                .cost(1).target(CardTarget.SELF).magic(3, 1)
                .text("At the end of your turn, gain {M} Block.")
                .effect(p -> p.applyToSelf(Powers.metallicize(p.magic())))
                .build());
        // Combust.
        c.add(card("knuckles:fired_up", "Fired Up", CardType.POWER, CardRarity.UNCOMMON)
                .cost(1).target(CardTarget.SELF).magic(5, 2)
                .text("At the end of your turn, lose 1 HP and deal {M} damage to ALL enemies.")
                .effect(p -> p.applyToSelf(firedUp(p.magic())))
                .build());
    }

    // ------------------------------------------------------------------ rare

    private static void rares(Catalog c) {
        // Demon Form, for Strength.
        c.add(card("knuckles:hyper_knuckles", "Hyper Knuckles", CardType.POWER, CardRarity.RARE)
                .cost(3).target(CardTarget.SELF).magic(2, 1)
                .text("At the start of each turn, gain {M} *Strength*.")
                .effect(p -> p.applyToSelf(hyperKnuckles(p.magic())))
                .build());
        // Bludgeon. Sonic Adventure's "Maximum Heat Knuckles Attack", shortened to fit the banner.
        c.add(card("knuckles:maximum_heat", "Maximum Heat", CardType.ATTACK, CardRarity.RARE)
                .cost(3).damage(32, 10)
                .text("Deal {D} damage.")
                .effect(p -> p.attack())
                .build());
        // Feed.
        c.add(card("knuckles:hunters_trophy", "Hunter's Trophy", CardType.ATTACK, CardRarity.RARE)
                .cost(1).damage(10, 2).magic(3, 1)
                .keywords(Keyword.EXHAUST)
                .text("Deal {D} damage. If this kills a non-minion enemy, raise your Max HP by {M}.")
                .effect(p -> {
                    Enemy target = p.target();
                    p.attack();
                    if (target != null && target.isDead() && !target.halfDead() && !target.isMinion()) {
                        p.combat().gainMaxHp(p.magic());
                    }
                })
                .build());
        // Reaper: the echidna clan's old fury, drawn from every enemy at once.
        c.add(card("knuckles:ancestral_fury", "Ancestral Fury", CardType.ATTACK, CardRarity.RARE)
                .cost(2).target(CardTarget.ALL_ENEMIES).damage(4, 1)
                .keywords(Keyword.EXHAUST)
                .text("Deal {D} damage to ALL enemies. Heal HP equal to unblocked damage.")
                .effect(p -> {
                    int healed = 0;
                    for (Enemy e : p.combat().activeEnemies()) {
                        int before = e.hp();
                        p.attack(e);
                        healed += Math.max(0, before - e.hp());
                    }
                    p.combat().healPlayer(healed);
                })
                .build());
        // Barricade: the Master Emerald's guardian holds his ground.
        c.add(card("knuckles:eternal_vigil", "Eternal Vigil", CardType.POWER, CardRarity.RARE)
                .cost(3).upgradedCost(2).target(CardTarget.SELF)
                .text("Block is not removed at the start of your turn.")
                .effect(p -> p.applyToSelf(Powers.barricade()))
                .build());
        // Impervious.
        c.add(card("knuckles:unbreakable", "Unbreakable", CardType.SKILL, CardRarity.RARE)
                .cost(2).target(CardTarget.SELF).block(30, 10)
                .keywords(Keyword.EXHAUST)
                .text("Gain {B} Block.")
                .effect(p -> p.gainBlock())
                .build());
        // Limit Break. Like Slay the Spire, negative Strength doubles too.
        c.add(card("knuckles:boiling_point", "Boiling Point", CardType.SKILL, CardRarity.RARE)
                .cost(1).target(CardTarget.SELF)
                .keywords(Keyword.EXHAUST).removeOnUpgrade(Keyword.EXHAUST)
                .text("Double your *Strength*.")
                .effect(p -> {
                    int strength = p.player().strength();
                    if (strength != 0) {
                        p.applyToSelf(Powers.strength(strength));
                    }
                })
                .build());
        // Offering.
        c.add(card("knuckles:altar_offering", "Altar Offering", CardType.SKILL, CardRarity.RARE)
                .cost(0).target(CardTarget.SELF).magic(3, 2)
                .keywords(Keyword.EXHAUST)
                .text("Lose 6 HP. Gain 2 Energy. Draw {M|card}.")
                .effect(p -> {
                    p.combat().loseHp(p.player(), 6);
                    p.gainEnergy(2);
                    p.draw(p.magic());
                })
                .build());
        // Berserk: Robotnik fooled him again, and now he's furious and careless.
        c.add(card("knuckles:tricked_again", "Tricked Again", CardType.POWER, CardRarity.RARE)
                .cost(0).target(CardTarget.SELF).magic(2, -1)
                .text("Gain {M} *Vulnerable*. At the start of your turn, gain 1 Energy.")
                .effect(p -> {
                    p.applyToSelf(Powers.vulnerable(p.magic()));
                    p.applyToSelf(trickedAgain(1));
                })
                .build());
    }

    // ------------------------------------------------------------------ helpers

    /** Cards in the hand, draw pile and discard pile named "... Punch", counting {@code self} even mid-play. */
    static int punchCards(Combat combat, Card self) {
        List<Card> cards = new ArrayList<>(combat.player().hand());
        cards.addAll(combat.player().drawPile());
        cards.addAll(combat.player().discardPile());
        int count = 0;
        boolean selfCounted = false;
        for (Card card : cards) {
            if (card.def().name().contains(PUNCH_WORD)) {
                count++;
            }
            selfCounted |= card == self;
        }
        return selfCounted ? count : count + 1;
    }

    static List<Card> upgradable(List<Card> cards) {
        List<Card> list = new ArrayList<>();
        for (Card card : cards) {
            if (card.canUpgrade()) {
                list.add(card);
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ powers

    static Power spikedKnuckles(int amount) {
        return new Power("knuckles:spiked_knuckles", "Spiked Knuckles", Power.BUFF, amount) {
            @Override
            public void onAttacked(Combat c, Creature source, int damage, int hpLost, String type) {
                if (source != null && source != owner() && type.equals(DamageType.ATTACK)) {
                    c.dealDamage(owner(), source, amount(), DamageType.THORNS);
                }
            }

            @Override
            public void atTurnStart(Combat c) {
                c.removePower(owner(), id());
            }

            @Override
            public String description() {
                return "Whenever you are attacked this turn, deal " + amount() + " damage back.";
            }
        };
    }

    static Power battleScars(int amount) {
        return new Power("knuckles:battle_scars", "Battle Scars", Power.BUFF, amount) {
            @Override
            public void onHpLost(Combat c, int lost) {
                if (c.phase().equals(Combat.PLAYER_TURN)) {
                    c.applyPower(owner(), owner(), Powers.strength(amount()));
                }
            }

            @Override
            public String description() {
                return "Whenever you lose HP during your turn, gain " + amount() + " Strength.";
            }
        };
    }

    static Power firedUp(int amount) {
        return new Power("knuckles:fired_up", "Fired Up", Power.BUFF, amount) {
            @Override
            public void atTurnEnd(Combat c) {
                c.loseHp(owner(), 1);
                for (Enemy e : c.activeEnemies()) {
                    c.dealDamage(owner(), e, amount(), DamageType.THORNS);
                }
            }

            @Override
            public String description() {
                return "At the end of your turn, lose 1 HP and deal " + amount() + " damage to ALL enemies.";
            }
        };
    }

    static Power hyperKnuckles(int amount) {
        return new Power("knuckles:hyper_knuckles", "Hyper Knuckles", Power.BUFF, amount) {
            @Override
            public void atTurnStart(Combat c) {
                c.applyPower(owner(), owner(), Powers.strength(amount()));
            }

            @Override
            public String description() {
                return "At the start of each turn, gain " + amount() + " Strength.";
            }
        };
    }

    static Power trickedAgain(int amount) {
        return new Power("knuckles:tricked_again", "Tricked Again", Power.BUFF, amount) {
            @Override
            public void atTurnStart(Combat c) {
                c.gainEnergy(amount());
            }

            @Override
            public String description() {
                return "At the start of your turn, gain " + amount() + " Energy.";
            }
        };
    }
}
