package slaytherobotnik.content;

import java.util.List;
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
import slaytherobotnik.core.DamageType;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.core.Keyword;
import slaytherobotnik.core.Power;
import slaytherobotnik.core.Powers;

/**
 * Tails' card pool (orange). Tails is the gadgeteer: he discards to fuel his tricks, builds
 * Block with <b>Dexterity</b>, and throws <b>Ring Bombs</b> — free, Exhausting attacks he
 * creates in bulk, the way the Silent uses Shivs. Cards that trigger when discarded, draw-and-
 * discard engines and Energy tricks make up the rest; his gadgets are named after the items
 * of <i>Tails Adventure</i> and his machines (the Tornado, the Sea Fox, the Cyclone).
 *
 * <p>Each card notes the Slay the Spire card it is balanced against, where there is one.
 * The layout follows {@link SonicCards}: one {@code c.add(...)} per card, grouped by rarity,
 * with card-specific powers at the bottom.
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

    // ------------------------------------------------------------------ common

    private static void commons(Catalog c) {
        // Blade Dance.
        c.add(card("tails:bomb_bag", "Bomb Bag", CardType.SKILL, CardRarity.COMMON)
                .cost(1).target(CardTarget.SELF).magic(3, 1)
                .text("Add {M|Ring Bomb} to your hand.")
                .effect(p -> p.addToHand(RING_BOMB, p.magic(), false))
                .build());
        // Cloak and Dagger: the decoy ring from Sonic Heroes, with a bomb inside.
        c.add(card("tails:dummy_ring", "Dummy Ring", CardType.SKILL, CardRarity.COMMON)
                .cost(1).target(CardTarget.SELF).block(6, 0).magic(1, 1)
                .text("Gain {B} Block. Add {M|Ring Bomb} to your hand.")
                .effect(p -> {
                    p.gainBlock();
                    p.addToHand(RING_BOMB, p.magic(), false);
                })
                .build());
        // Dagger Throw.
        c.add(card("tails:wrench_throw", "Wrench Throw", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).damage(9, 3)
                .text("Deal {D} damage. Draw 1 card. Discard 1 card.")
                .effect(p -> {
                    p.attack();
                    p.draw(1);
                    p.discardChoice(1, null);
                })
                .build());
        // Quick Slash.
        c.add(card("tails:twin_tail_strike", "Twin Tail Strike", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).damage(8, 4)
                .text("Deal {D} damage. Draw 1 card.")
                .effect(p -> {
                    p.attack();
                    p.draw(1);
                })
                .build());
        // Flying Knee: a strafing run in the Tornado.
        c.add(card("tails:tornado_flyby", "Tornado Flyby", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).damage(8, 3)
                .text("Deal {D} damage. Next turn, gain 1 Energy.")
                .effect(p -> {
                    p.attack();
                    p.applyToSelf(Powers.energizedNextTurn(1));
                })
                .build());
        // Dagger Spray: Sonic Heroes' flying-formation shot.
        c.add(card("tails:thunder_shoot", "Thunder Shoot", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).target(CardTarget.ALL_ENEMIES).damage(4, 2)
                .text("Deal {D} damage to ALL enemies twice.")
                .effect(p -> p.repeat(2, p::attackAll))
                .build());
        // Backflip.
        c.add(card("tails:hover", "Hover", CardType.SKILL, CardRarity.COMMON)
                .cost(1).target(CardTarget.SELF).block(5, 3)
                .text("Gain {B} Block. Draw 2 cards.")
                .effect(p -> {
                    p.gainBlock();
                    p.draw(2);
                })
                .build());
        // Dodge and Roll: the next-turn Block includes Dexterity, as in Slay the Spire.
        c.add(card("tails:propeller_flight", "Propeller Flight", CardType.SKILL, CardRarity.COMMON)
                .cost(1).target(CardTarget.SELF).block(4, 2)
                .text("Gain {B} Block. Next turn, gain {B} Block.")
                .effect(p -> {
                    int block = p.combat().calculateBlock(p.player(), p.block(), p.card());
                    p.gainBlock();
                    p.applyToSelf(Powers.blockNextTurn(block));
                })
                .build());
        // Acrobatics.
        c.add(card("tails:rummage", "Rummage", CardType.SKILL, CardRarity.COMMON)
                .cost(1).target(CardTarget.SELF).magic(3, 1)
                .text("Draw {M|card}. Discard 1 card.")
                .effect(p -> {
                    p.draw(p.magic());
                    p.discardChoice(1, null);
                })
                .build());
        // Prepared.
        c.add(card("tails:recalibrate", "Recalibrate", CardType.SKILL, CardRarity.COMMON)
                .cost(0).target(CardTarget.SELF).magic(1, 1)
                .text("Draw {M|card}. Discard {M|card}.")
                .effect(p -> {
                    p.draw(p.magic());
                    p.discardChoice(p.magic(), null);
                })
                .build());
        // Deflect.
        c.add(card("tails:duck_and_cover", "Duck and Cover", CardType.SKILL, CardRarity.COMMON)
                .cost(0).target(CardTarget.SELF).block(4, 3)
                .text("Gain {B} Block.")
                .effect(p -> p.gainBlock())
                .build());
        // Slice: the Hammer from Tails Adventure.
        c.add(card("tails:hammer_tap", "Hammer Tap", CardType.ATTACK, CardRarity.COMMON)
                .cost(0).damage(6, 3)
                .text("Deal {D} damage.")
                .effect(p -> p.attack())
                .build());
        // Sneaky Strike.
        c.add(card("tails:booby_trap", "Booby Trap", CardType.ATTACK, CardRarity.COMMON)
                .cost(2).damage(12, 4)
                .text("Deal {D} damage. If you discarded a card this turn, gain 2 Energy.")
                .effect(p -> {
                    p.attack();
                    if (p.combat().cardsDiscardedThisTurn() > 0) {
                        p.gainEnergy(2);
                    }
                })
                .build());
        // Sucker Punch.
        c.add(card("tails:spark_shot", "Spark Shot", CardType.ATTACK, CardRarity.COMMON)
                .cost(1).damage(7, 2).magic(1, 1)
                .text("Deal {D} damage. Apply {M} *Weak*.")
                .effect(p -> {
                    p.attack();
                    p.applyToTarget(Powers.weak(p.magic()));
                })
                .build());
        // Outmaneuver.
        c.add(card("tails:flight_plan", "Flight Plan", CardType.SKILL, CardRarity.COMMON)
                .cost(1).target(CardTarget.SELF).magic(2, 1)
                .text("Next turn, gain {M} Energy.")
                .effect(p -> p.applyToSelf(Powers.energizedNextTurn(p.magic())))
                .build());
        // Piercing Wail. Artifact blocks the Strength loss, and then nothing is given back.
        c.add(card("tails:signal_jammer", "Signal Jammer", CardType.SKILL, CardRarity.COMMON)
                .cost(1).target(CardTarget.ALL_ENEMIES).magic(6, 2)
                .keywords(Keyword.EXHAUST)
                .text("ALL enemies lose {M} Strength this turn.")
                .effect(p -> {
                    for (Enemy e : p.combat().activeEnemies()) {
                        boolean shielded = e.has(Powers.ARTIFACT);
                        p.combat().applyPower(p.player(), e, Powers.strength(-p.magic()));
                        if (!shielded) {
                            p.combat().applyPower(p.player(), e, jammed(p.magic()));
                        }
                    }
                })
                .build());
    }

    // ------------------------------------------------------------------ uncommon

    private static void uncommons(Catalog c) {
        // Accuracy.
        c.add(card("tails:blast_radius", "Blast Radius", CardType.POWER, CardRarity.UNCOMMON)
                .cost(1).target(CardTarget.SELF).magic(4, 2)
                .text("Ring Bombs deal {M} additional damage.")
                .effect(p -> p.applyToSelf(blastRadius(p.magic())))
                .build());
        // Infinite Blades.
        c.add(card("tails:remote_robot", "Remote Robot", CardType.POWER, CardRarity.UNCOMMON)
                .cost(1).target(CardTarget.SELF)
                .addOnUpgrade(Keyword.INNATE)
                .text("At the start of your turn, add a Ring Bomb to your hand.")
                .effect(p -> p.applyToSelf(remoteRobot(1)))
                .build());
        // Reflex.
        c.add(card("tails:junk_drawer", "Junk Drawer", CardType.SKILL, CardRarity.UNCOMMON)
                .unplayable().magic(2, 1)
                .text("When you discard this card, draw {M|card}.")
                .hooks(CardHooks.builder()
                        .onManualDiscard((combat, self) -> combat.draw(self.magic()))
                        .build())
                .build());
        // Tactician.
        c.add(card("tails:battery_pack", "Battery Pack", CardType.SKILL, CardRarity.UNCOMMON)
                .unplayable().magic(1, 1)
                .text("When you discard this card, gain {M} Energy.")
                .hooks(CardHooks.builder()
                        .onManualDiscard((combat, self) -> combat.gainEnergy(self.magic()))
                        .build())
                .build());
        // Calculated Gamble.
        c.add(card("tails:trial_and_error", "Trial and Error", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(0).target(CardTarget.SELF)
                .keywords(Keyword.EXHAUST).removeOnUpgrade(Keyword.EXHAUST)
                .text("Discard your hand, then draw that many cards.")
                .effect(p -> p.draw(discardHand(p.combat()).size()))
                .build());
        // Concentrate.
        c.add(card("tails:brainstorm", "Brainstorm", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(0).target(CardTarget.SELF).magic(3, -1)
                .text("Discard {M|card}. Gain 2 Energy.")
                .effect(p -> p.discardChoice(p.magic(), chosen -> p.gainEnergy(2)))
                .build());
        // Eviscerate.
        c.add(card("tails:napalm_bomb", "Napalm Bomb", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(3).damage(7, 2)
                .text("Costs 1 less Energy for each card discarded this turn. Deal {D} damage 3 times.")
                .hooks(CardHooks.builder()
                        .costAdjuster((combat, self, cost) -> cost - combat.cardsDiscardedThisTurn())
                        .build())
                .effect(p -> p.repeat(3, p::attack))
                .build());
        // Finisher: Ring Bombs count, so a handful of bombs sets it off.
        c.add(card("tails:chain_reaction", "Chain Reaction", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(1).damage(6, 2)
                .text("Deal {D} damage for each other Attack played this turn.")
                .effect(p -> p.repeat(Math.max(0, p.combat().attacksPlayedThisTurn() - 1), p::attack))
                .build());
        // Leg Sweep.
        c.add(card("tails:smoke_bomb", "Smoke Bomb", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(2).target(CardTarget.ENEMY).block(11, 3).magic(2, 1)
                .text("Apply {M} *Weak*. Gain {B} Block.")
                .effect(p -> {
                    p.applyToTarget(Powers.weak(p.magic()));
                    p.gainBlock();
                })
                .build());
        // Footwork.
        c.add(card("tails:aerobatics", "Aerobatics", CardType.POWER, CardRarity.UNCOMMON)
                .cost(1).target(CardTarget.SELF).magic(2, 1)
                .text("Gain {M} *Dexterity*.")
                .effect(p -> p.applyToSelf(Powers.dexterity(p.magic())))
                .build());
        // Caltrops: the Mines from Tails Adventure.
        c.add(card("tails:minefield", "Minefield", CardType.POWER, CardRarity.UNCOMMON)
                .cost(1).target(CardTarget.SELF).magic(3, 2)
                .text("Whenever you are attacked, deal {M} damage back.")
                .effect(p -> p.applyToSelf(Powers.thorns(p.magic())))
                .build());
        // Dash.
        c.add(card("tails:rocket_booster", "Rocket Booster", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(2).damage(10, 3).block(10, 3)
                .text("Gain {B} Block. Deal {D} damage.")
                .effect(p -> {
                    p.gainBlock();
                    p.attack();
                })
                .build());
        // Heel Hook.
        c.add(card("tails:weak_spot", "Weak Spot", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(1).damage(5, 3)
                .text("Deal {D} damage. If the enemy is *Weak*, gain 1 Energy and draw 1 card.")
                .effect(p -> {
                    boolean weak = p.target() != null && p.target().has(Powers.WEAK);
                    p.attack();
                    if (weak) {
                        p.gainEnergy(1);
                        p.draw(1);
                    }
                })
                .build());
        // Predator: a Sea Fox missile.
        c.add(card("tails:anti_air_missile", "Anti-Air Missile", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(2).damage(15, 5)
                .text("Deal {D} damage. Next turn, draw 2 additional cards.")
                .effect(p -> {
                    p.attack();
                    p.applyToSelf(Powers.drawNextTurn(2));
                })
                .build());
        // Skewer.
        c.add(card("tails:energy_cannon", "Energy Cannon", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(CardDef.COST_X).damage(7, 3)
                .text("Deal {D} damage X times.")
                .effect(p -> p.repeat(p.x(), p::attack))
                .build());
        // Equilibrium: circling the Tornado while the plan comes together.
        c.add(card("tails:holding_pattern", "Holding Pattern", CardType.SKILL, CardRarity.UNCOMMON)
                .cost(2).target(CardTarget.SELF).block(13, 3)
                .text("Gain {B} Block. *Retain* your hand this turn.")
                .effect(p -> {
                    p.gainBlock();
                    p.applyToSelf(holdingPattern());
                })
                .build());
        // Perfected Strike, counting the Ring Bombs thrown this combat.
        c.add(card("tails:large_bomb", "Large Bomb", CardType.ATTACK, CardRarity.UNCOMMON)
                .cost(2).damage(10, 0).magic(2, 1)
                .text("Deal {D} damage. Deals {M} additional damage for each Ring Bomb played this combat.")
                .damageFormula((combat, card, target) -> card.damage() + card.magic() * ringBombsPlayed(combat))
                .effect(p -> p.attack())
                .build());
    }

    // ------------------------------------------------------------------ rare

    private static void rares(Catalog c) {
        // A Thousand Cuts: Super Tails' Flickies peck at everything.
        c.add(card("tails:super_tails", "Super Tails", CardType.POWER, CardRarity.RARE)
                .cost(2).target(CardTarget.SELF).magic(1, 1)
                .text("Whenever you play a card, deal {M} damage to ALL enemies.")
                .effect(p -> p.applyToSelf(superTails(p.magic())))
                .build());
        // Adrenaline.
        c.add(card("tails:overdrive", "Overdrive", CardType.SKILL, CardRarity.RARE)
                .cost(0).target(CardTarget.SELF).magic(1, 1)
                .keywords(Keyword.EXHAUST)
                .text("Gain {M} Energy. Draw 2 cards.")
                .effect(p -> {
                    p.gainEnergy(p.magic());
                    p.draw(2);
                })
                .build());
        // Tools of the Trade.
        c.add(card("tails:workbench", "Workbench", CardType.POWER, CardRarity.RARE)
                .cost(1).upgradedCost(0).target(CardTarget.SELF)
                .text("At the start of your turn, draw 1 card and discard 1 card.")
                .effect(p -> p.applyToSelf(workbench(1)))
                .build());
        // Storm of Steel.
        c.add(card("tails:bomb_barrage", "Bomb Barrage", CardType.SKILL, CardRarity.RARE)
                .cost(1).target(CardTarget.SELF)
                .text("Discard your hand. Add 1 Ring Bomb to your hand for each card discarded.")
                .upgradedText("Discard your hand. Add 1 Ring Bomb+ to your hand for each card discarded.")
                .effect(p -> p.addToHand(RING_BOMB, discardHand(p.combat()).size(), p.upgraded()))
                .build());
        // Die Die Die.
        c.add(card("tails:proton_bomb", "Proton Bomb", CardType.ATTACK, CardRarity.RARE)
                .cost(1).target(CardTarget.ALL_ENEMIES).damage(13, 4)
                .keywords(Keyword.EXHAUST)
                .text("Deal {D} damage to ALL enemies.")
                .effect(p -> p.attackAll())
                .build());
        // Unload: the Cyclone empties everything it is carrying.
        c.add(card("tails:cyclone_volley", "Cyclone Volley", CardType.ATTACK, CardRarity.RARE)
                .cost(1).damage(14, 4)
                .text("Deal {D} damage. Discard all non-Attack cards in your hand.")
                .effect(p -> {
                    p.attack();
                    for (Card card : List.copyOf(p.player().hand())) {
                        if (!card.type().equals(CardType.ATTACK)) {
                            p.combat().discard(card);
                        }
                    }
                })
                .build());
        // Phantasmal Killer: the Cyclone's lock-on laser.
        c.add(card("tails:lock_on", "Lock-On", CardType.SKILL, CardRarity.RARE)
                .cost(1).upgradedCost(0).target(CardTarget.SELF)
                .text("Next turn, your Attacks deal double damage.")
                .effect(p -> p.applyToSelf(lockOn(1)))
                .build());
        // Malaise.
        c.add(card("tails:system_hack", "System Hack", CardType.SKILL, CardRarity.RARE)
                .cost(CardDef.COST_X).target(CardTarget.ENEMY).magic(0, 1)
                .keywords(Keyword.EXHAUST)
                .text("Enemy loses X Strength. Apply X *Weak*.")
                .upgradedText("Enemy loses X+1 Strength. Apply X+1 *Weak*.")
                .effect(p -> {
                    int amount = p.x() + p.magic();
                    if (amount > 0) {
                        p.applyToTarget(Powers.strength(-amount));
                        p.applyToTarget(Powers.weak(amount));
                    }
                })
                .build());
        // Envenom's slot: every discard becomes a bomb instead of poison.
        c.add(card("tails:recycler", "Recycler", CardType.POWER, CardRarity.RARE)
                .cost(2).upgradedCost(1).target(CardTarget.SELF)
                .text("Whenever you discard a card, add a Ring Bomb to your hand.")
                .effect(p -> p.applyToSelf(recycler(1)))
                .build());
    }

    // ------------------------------------------------------------------ helpers

    /** Discards every card in the hand (each counts as a discard) and returns them. */
    static List<Card> discardHand(Combat c) {
        List<Card> hand = List.copyOf(c.player().hand());
        for (Card card : hand) {
            c.discard(card);
        }
        return hand;
    }

    /** Ring Bombs (upgraded or not) played so far this combat. */
    static int ringBombsPlayed(Combat c) {
        int count = 0;
        for (Card card : c.playedThisCombat()) {
            if (card.id().equals(RING_BOMB)) {
                count++;
            }
        }
        return count;
    }

    private static String ringBombs(int n) {
        return n == 1 ? "a Ring Bomb" : n + " Ring Bombs";
    }

    private static String cards(int n) {
        return n == 1 ? "1 card" : n + " cards";
    }

    // ------------------------------------------------------------------ powers

    static Power jammed(int amount) {
        return new Power("tails:jammed", "Jammed", Power.DEBUFF, amount) {
            @Override
            public void atTurnEnd(Combat c) {
                c.applyPower(owner(), owner(), Powers.strength(amount()));
                c.removePower(owner(), id());
            }

            @Override
            public String description() {
                return "At the end of its turn, regains " + amount() + " Strength.";
            }
        };
    }

    static Power blastRadius(int amount) {
        return new Power("tails:blast_radius", "Blast Radius", Power.BUFF, amount) {
            @Override
            public float modifyDamageDealt(Combat c, float damage, String type, Creature target) {
                Card card = c.cardBeingPlayed();
                return type.equals(DamageType.ATTACK) && card != null && card.id().equals(RING_BOMB)
                        ? damage + amount() : damage;
            }

            @Override
            public String description() {
                return "Ring Bombs deal " + amount() + " additional damage.";
            }
        };
    }

    static Power remoteRobot(int amount) {
        return new Power("tails:remote_robot", "Remote Robot", Power.BUFF, amount) {
            @Override
            public void atTurnStart(Combat c) {
                for (int i = 0; i < amount(); i++) {
                    c.createInHand(RING_BOMB, false);
                }
            }

            @Override
            public String description() {
                return "At the start of your turn, add " + ringBombs(amount()) + " to your hand.";
            }
        };
    }

    static Power holdingPattern() {
        return new Power("tails:holding_pattern", "Holding Pattern", Power.BUFF, 1) {
            {
                nonStacking();
            }

            @Override
            public void atTurnEnd(Combat c) {
                // As Equilibrium: Ethereal cards still Exhaust.
                for (Card card : c.player().hand()) {
                    if (!card.has(Keyword.ETHEREAL)) {
                        card.setRetainThisTurn(true);
                    }
                }
                c.removePower(owner(), id());
            }

            @Override
            public String description() {
                return "Retain your hand this turn.";
            }
        };
    }

    static Power superTails(int amount) {
        return new Power("tails:super_tails", "Super Tails", Power.BUFF, amount) {
            @Override
            public void onCardPlayed(Combat c, Card card) {
                for (Enemy e : c.activeEnemies()) {
                    c.dealDamage(owner(), e, amount(), DamageType.THORNS);
                }
            }

            @Override
            public String description() {
                return "Whenever you play a card, deal " + amount() + " damage to ALL enemies.";
            }
        };
    }

    static Power workbench(int amount) {
        return new Power("tails:workbench", "Workbench", Power.BUFF, amount) {
            @Override
            public void atTurnStartPostDraw(Combat c) {
                c.draw(amount());
                c.chooseFromHand("Discard " + cards(amount()) + ".", amount(), false,
                        chosen -> chosen.forEach(c::discard));
            }

            @Override
            public String description() {
                return "At the start of your turn, draw " + cards(amount()) + " and discard "
                        + cards(amount()) + ".";
            }
        };
    }

    static Power lockOn(int turns) {
        return new Power("tails:lock_on", "Lock-On", Power.BUFF, turns) {
            @Override
            public void atTurnStart(Combat c) {
                c.applyPower(owner(), owner(), doubleDamage());
                c.reducePower(owner(), id(), 1);
            }

            @Override
            public String description() {
                return amount() == 1
                        ? "Next turn, your Attacks deal double damage."
                        : "For your next " + amount() + " turns, your Attacks deal double damage.";
            }
        };
    }

    static Power doubleDamage() {
        return new Power("tails:double_damage", "Double Damage", Power.BUFF, 1) {
            {
                nonStacking();
            }

            /** After additive bonuses such as Strength, alongside the Weak multiplier. */
            @Override
            public int priority() {
                return 50;
            }

            @Override
            public float modifyDamageDealt(Combat c, float damage, String type, Creature target) {
                return type.equals(DamageType.ATTACK) ? damage * 2 : damage;
            }

            @Override
            public void atTurnEnd(Combat c) {
                c.removePower(owner(), id());
            }

            @Override
            public String description() {
                return "This turn, your Attacks deal double damage.";
            }
        };
    }

    static Power recycler(int amount) {
        return new Power("tails:recycler", "Recycler", Power.BUFF, amount) {
            @Override
            public void onCardDiscarded(Combat c, Card card) {
                for (int i = 0; i < amount(); i++) {
                    c.createInHand(RING_BOMB, false);
                }
            }

            @Override
            public String description() {
                return "Whenever you discard a card, add " + ringBombs(amount()) + " to your hand.";
            }
        };
    }
}
