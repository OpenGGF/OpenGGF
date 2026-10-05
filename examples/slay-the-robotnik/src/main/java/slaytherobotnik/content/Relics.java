package slaytherobotnik.content;

import java.util.ArrayList;
import java.util.List;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.CardType;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.Combat;
import slaytherobotnik.core.Creature;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.core.Powers;
import slaytherobotnik.core.Relic;
import slaytherobotnik.core.RelicTier;
import slaytherobotnik.core.RestOption;
import slaytherobotnik.core.Reward;
import slaytherobotnik.core.RoomType;
import slaytherobotnik.core.RunRngs;
import slaytherobotnik.core.RunState;

/**
 * Relics, grouped by tier. Each is an anonymous {@link Relic} overriding the hooks it
 * needs; the catalog stores the factory so every run gets fresh instances. The comment above
 * each names the Slay the Spire relic it mirrors.
 */
public final class Relics {
    public static final String RED_SNEAKERS = "relic:red_sneakers";
    public static final String TINKER_KIT = "relic:tinker_kit";
    public static final String MASTER_EMERALD_SHARD = "relic:master_emerald_shard";
    public static final String CIRCLET = "relic:circlet";
    public static final String EMERALD_IDOL = "relic:emerald_idol";
    public static final String MUSHROOM_CAP = "relic:mushroom_cap";
    public static final String ANCIENT_TABLET = "relic:ancient_tablet";
    public static final String COLLECTORS_BADGE = "relic:collectors_badge";

    private Relics() {
    }

    public static void register(Catalog c) {
        starters(c);
        eventRelics(c);
        commons(c);
        uncommons(c);
        rares(c);
        bosses(c);
        shop(c);
    }

    // ------------------------------------------------------------------ starter

    private static void starters(Catalog c) {
        c.addRelic(() -> new Relic(RED_SNEAKERS, "Red Sneakers", RelicTier.STARTER) {
            private int turnUsed = -1;

            @Override
            public int modifyCombo(Combat combat, Card card, int count) {
                return turnUsed != combat.turn() ? count + 1 : count;
            }

            @Override
            public void afterCardPlayed(Combat combat, Card card) {
                if (card.combo() > 0 && turnUsed != combat.turn()) {
                    turnUsed = combat.turn();
                    flash(combat);
                }
            }

            @Override
            public void atBattleStart(Combat combat) {
                turnUsed = -1;
            }

            @Override
            public String description() {
                return "The first Combo card you play each turn repeats 1 more time.";
            }

            @Override
            public String flavor() {
                return "Sonic's trademark kicks. Built for one thing.";
            }
        }.onlyFor(Characters.SONIC));

        c.addRelic(() -> new Relic(TINKER_KIT, "Tinker Kit", RelicTier.STARTER) {
            @Override
            public void atBattleStart(Combat combat) {
                flash(combat);
                combat.createInHand(TailsCards.RING_BOMB, false);
                combat.createInHand(TailsCards.RING_BOMB, false);
            }

            @Override
            public String description() {
                return "At the start of each combat, add 2 Ring Bombs to your hand.";
            }

            @Override
            public String flavor() {
                return "Screwdrivers, wire, and a suspicious number of rings.";
            }
        }.onlyFor(Characters.TAILS));

        c.addRelic(() -> new Relic(MASTER_EMERALD_SHARD, "Master Emerald Shard", RelicTier.STARTER) {
            @Override
            public void onVictory(Combat combat) {
                flash(combat);
                combat.healPlayer(6);
            }

            @Override
            public String description() {
                return "At the end of combat, heal 6 HP.";
            }

            @Override
            public String flavor() {
                return "A sliver of the great gem. It hums when its guardian fights.";
            }
        }.onlyFor(Characters.KNUCKLES));

        // What empty relic pools give.
        c.addRelic(() -> new Relic(CIRCLET, "Gold Ring", RelicTier.EVENT) {
            @Override
            public String description() {
                return "Just a ring. A very, very shiny ring.";
            }
        });
    }

    // ------------------------------------------------------------------ event

    /** Relics only events give (Slay the Spire's Event tier). */
    private static void eventRelics(Catalog c) {
        // Golden Idol.
        c.addRelic(() -> new Relic(EMERALD_IDOL, "Emerald Idol", RelicTier.EVENT) {
            @Override
            public int modifyCombatRings(RunState run, int rings) {
                return rings + rings / 4;
            }

            @Override
            public String description() {
                return "Enemies drop 25% more rings.";
            }

            @Override
            public String flavor() {
                return "Taken from the altar in the ruins. The ruins did not take it well.";
            }
        });
        // Odd Mushroom, as a small defensive boon.
        c.addRelic(() -> new Relic(MUSHROOM_CAP, "Mushroom Cap", RelicTier.EVENT) {
            @Override
            public void atBattleStart(Combat combat) {
                flash(combat);
                combat.applyPower(combat.player(), combat.player(), Powers.artifact(1));
            }

            @Override
            public String description() {
                return "At the start of each combat, gain 1 *Artifact*.";
            }

            @Override
            public String flavor() {
                return "Bouncy. Smells faintly of Mushroom Hill.";
            }
        });
        // Enchiridion.
        c.addRelic(() -> new Relic(ANCIENT_TABLET, "Ancient Tablet", RelicTier.EVENT) {
            @Override
            public void atTurnStartPostDraw(Combat combat) {
                if (combat.turn() != 1) {
                    return;
                }
                List<slaytherobotnik.core.CardDef> powers = new ArrayList<>();
                String color = combat.run().character().color();
                for (String rarity : new String[] {slaytherobotnik.core.CardRarity.COMMON,
                        slaytherobotnik.core.CardRarity.UNCOMMON, slaytherobotnik.core.CardRarity.RARE}) {
                    for (var def : combat.catalog().cards(color, rarity)) {
                        if (def.type().equals(CardType.POWER)) {
                            powers.add(def);
                        }
                    }
                }
                if (!powers.isEmpty()) {
                    flash(combat);
                    Card card = new Card(powers.get(combat.cardRng().nextInt(powers.size())));
                    card.setCostForTurn(0);
                    combat.addCreatedCard(card, "hand");
                }
            }

            @Override
            public String description() {
                return "At the start of each combat, add a random Power card to your hand. It costs 0 this turn.";
            }

            @Override
            public String flavor() {
                return "Echidna carvings of a power older than the island.";
            }
        });
        // Question Card, as N'loth's thanks.
        c.addRelic(() -> new Relic(COLLECTORS_BADGE, "Collector's Badge", RelicTier.EVENT) {
            @Override
            public int modifyCardRewardSize(RunState run, int count) {
                return count + 1;
            }

            @Override
            public String description() {
                return "Card rewards offer 1 additional card.";
            }

            @Override
            public String flavor() {
                return "Proof that you traded with the strangest robot on the island.";
            }
        });
    }

    // ------------------------------------------------------------------ common

    private static void commons(Catalog c) {
        // Anchor.
        c.addRelic(() -> new Relic("relic:blue_shield", "Blue Shield", RelicTier.COMMON) {
            @Override
            public void atBattleStart(Combat combat) {
                flash(combat);
                combat.gainBlock(combat.player(), 10, null);
            }

            @Override
            public String description() {
                return "Start each combat with 10 Block.";
            }
        });
        // Bag of Marbles.
        c.addRelic(() -> new Relic("relic:spike_ball", "Spike Ball", RelicTier.COMMON) {
            @Override
            public void atBattleStart(Combat combat) {
                flash(combat);
                for (Enemy e : combat.activeEnemies()) {
                    combat.applyPower(combat.player(), e, Powers.vulnerable(1));
                }
            }

            @Override
            public String description() {
                return "At the start of each combat, apply 1 Vulnerable to ALL enemies.";
            }
        });
        // Bag of Preparation.
        c.addRelic(() -> new Relic("relic:starting_line", "Starting Line", RelicTier.COMMON) {
            @Override
            public void atTurnStartPostDraw(Combat combat) {
                if (combat.turn() == 1) {
                    flash(combat);
                    combat.draw(2);
                }
            }

            @Override
            public String description() {
                return "At the start of each combat, draw 2 additional cards.";
            }
        });
        // Bronze Scales.
        c.addRelic(() -> new Relic("relic:spiked_shell", "Spiked Shell", RelicTier.COMMON) {
            @Override
            public void atBattleStart(Combat combat) {
                combat.applyPower(combat.player(), combat.player(), Powers.thorns(3));
            }

            @Override
            public String description() {
                return "Start each combat with 3 Thorns.";
            }
        });
        // Centennial Puzzle.
        c.addRelic(() -> new Relic("relic:ring_alarm", "Ring Alarm", RelicTier.COMMON) {
            private boolean used;

            @Override
            public void atBattleStart(Combat combat) {
                used = false;
            }

            @Override
            public void onHpLost(Combat combat, int amount) {
                if (!used) {
                    used = true;
                    flash(combat);
                    combat.draw(3);
                }
            }

            @Override
            public String description() {
                return "The first time you lose HP each combat, draw 3 cards.";
            }
        });
        // Happy Flower.
        c.addRelic(() -> new Relic("relic:lucky_flower", "Lucky Flower", RelicTier.COMMON) {
            @Override
            public void onEquip(RunState run) {
                setCounter(0);
            }

            @Override
            public void atTurnStart(Combat combat) {
                setCounter(counter() + 1);
                if (counter() >= 3) {
                    setCounter(0);
                    flash(combat);
                    combat.gainEnergy(1);
                }
            }

            @Override
            public String description() {
                return "Every 3 turns, gain 1 Energy.";
            }
        });
        // Lantern.
        c.addRelic(() -> new Relic("relic:lamppost", "Lamppost", RelicTier.COMMON) {
            @Override
            public void atTurnStart(Combat combat) {
                if (combat.turn() == 1) {
                    flash(combat);
                    combat.gainEnergy(1);
                }
            }

            @Override
            public String description() {
                return "Gain 1 Energy on the first turn of each combat.";
            }
        });
        // Vajra.
        c.addRelic(() -> new Relic("relic:power_bracelet", "Power Bracelet", RelicTier.COMMON) {
            @Override
            public void atBattleStart(Combat combat) {
                combat.applyPower(combat.player(), combat.player(), Powers.strength(1));
            }

            @Override
            public String description() {
                return "At the start of each combat, gain 1 Strength.";
            }
        });
        // Oddly Smooth Stone.
        c.addRelic(() -> new Relic("relic:grip_gloves", "Grip Gloves", RelicTier.COMMON) {
            @Override
            public void atBattleStart(Combat combat) {
                combat.applyPower(combat.player(), combat.player(), Powers.dexterity(1));
            }

            @Override
            public String description() {
                return "At the start of each combat, gain 1 Dexterity.";
            }
        });
        // Strawberry.
        c.addRelic(() -> new Relic("relic:energy_capsule", "Energy Capsule", RelicTier.COMMON) {
            @Override
            public void onEquip(RunState run) {
                run.gainMaxHp(7);
            }

            @Override
            public String description() {
                return "Raise your Max HP by 7.";
            }
        });
        // Pen Nib.
        c.addRelic(() -> new Relic("relic:pinball_flipper", "Pinball Flipper", RelicTier.COMMON) {
            private boolean doubling;

            @Override
            public void onEquip(RunState run) {
                setCounter(0);
            }

            @Override
            public void onCardPlayed(Combat combat, Card card) {
                if (card.type().equals(CardType.ATTACK)) {
                    setCounter(counter() + 1);
                    if (counter() >= 10) {
                        setCounter(0);
                        doubling = true;
                        flash(combat);
                    }
                }
            }

            @Override
            public float modifyDamageDealt(Combat combat, float damage, String type, Creature target) {
                return doubling && type.equals(slaytherobotnik.core.DamageType.ATTACK) ? damage * 2 : damage;
            }

            @Override
            public void afterCardPlayed(Combat combat, Card card) {
                doubling = false;
            }

            @Override
            public String description() {
                return "Every 10th Attack you play deals double damage.";
            }
        });
        // Orichalcum.
        c.addRelic(() -> new Relic("relic:emergency_shield", "Emergency Shield", RelicTier.COMMON) {
            @Override
            public void atTurnEnd(Combat combat) {
                if (combat.player().block() == 0) {
                    flash(combat);
                    combat.gainBlock(combat.player(), 6, null);
                }
            }

            @Override
            public String description() {
                return "If you end your turn without Block, gain 6 Block.";
            }
        });
        // Potion Belt.
        c.addRelic(() -> new Relic("relic:item_box", "Item Box", RelicTier.COMMON) {
            @Override
            public int potionSlotBonus() {
                return 2;
            }

            @Override
            public String description() {
                return "Gain 2 potion slots.";
            }
        });
        // Regal Pillow.
        c.addRelic(() -> new Relic("relic:comfy_cushion", "Comfy Cushion", RelicTier.COMMON) {
            @Override
            public int modifyRestHeal(RunState run, int amount) {
                return amount + 15;
            }

            @Override
            public String description() {
                return "Resting at a Starpost heals an additional 15 HP.";
            }
        });
        // Toy Ornithopter.
        c.addRelic(() -> new Relic("relic:toy_tornado", "Toy Tornado", RelicTier.COMMON) {
            @Override
            public void onPotionUsed(Combat combat, slaytherobotnik.core.PotionDef potion) {
                flash(combat);
                combat.healPlayer(5);
            }

            @Override
            public String description() {
                return "Whenever you use a potion, heal 5 HP.";
            }
        });
        // Ceramic Fish.
        c.addRelic(() -> new Relic("relic:ring_magnet", "Ring Magnet", RelicTier.COMMON) {
            @Override
            public void onCardAddedToDeck(RunState run, Card card) {
                run.gainRings(9);
            }

            @Override
            public String description() {
                return "Whenever you add a card to your deck, gain 9 rings.";
            }
        });
        // Whetstone.
        c.addRelic(() -> new Relic("relic:tune_up_kit", "Tune-Up Kit", RelicTier.COMMON) {
            @Override
            public void onEquip(RunState run) {
                upgradeRandom(run, CardType.ATTACK, 2);
            }

            @Override
            public String description() {
                return "Upon pickup, upgrade 2 random Attacks.";
            }
        });
        // War Paint.
        c.addRelic(() -> new Relic("relic:tool_belt", "Tool Belt", RelicTier.COMMON) {
            @Override
            public void onEquip(RunState run) {
                upgradeRandom(run, CardType.SKILL, 2);
            }

            @Override
            public String description() {
                return "Upon pickup, upgrade 2 random Skills.";
            }
        });
        // Blood Vial.
        c.addRelic(() -> new Relic("relic:energy_drink", "Energy Drink", RelicTier.COMMON) {
            @Override
            public void atBattleStart(Combat combat) {
                flash(combat);
                combat.healPlayer(2);
            }

            @Override
            public String description() {
                return "At the start of each combat, heal 2 HP.";
            }
        });
    }

    // ------------------------------------------------------------------ uncommon

    private static void uncommons(Catalog c) {
        // Shuriken.
        c.addRelic(() -> new Relic("relic:power_sneakers", "Power Sneakers", RelicTier.UNCOMMON) {
            private int attacks;

            @Override
            public void atTurnStart(Combat combat) {
                attacks = 0;
            }

            @Override
            public void afterCardPlayed(Combat combat, Card card) {
                if (card.type().equals(CardType.ATTACK) && ++attacks % 3 == 0) {
                    flash(combat);
                    combat.applyPower(combat.player(), combat.player(), Powers.strength(1));
                }
            }

            @Override
            public String description() {
                return "Every time you play 3 Attacks in a single turn, gain 1 Strength.";
            }
        });
        // Kunai.
        c.addRelic(() -> new Relic("relic:wrist_guards", "Wrist Guards", RelicTier.UNCOMMON) {
            private int attacks;

            @Override
            public void atTurnStart(Combat combat) {
                attacks = 0;
            }

            @Override
            public void afterCardPlayed(Combat combat, Card card) {
                if (card.type().equals(CardType.ATTACK) && ++attacks % 3 == 0) {
                    flash(combat);
                    combat.applyPower(combat.player(), combat.player(), Powers.dexterity(1));
                }
            }

            @Override
            public String description() {
                return "Every time you play 3 Attacks in a single turn, gain 1 Dexterity.";
            }
        });
        // A Focus relic in the same family.
        c.addRelic(() -> new Relic("relic:rhythm_badge", "Rhythm Badge", RelicTier.UNCOMMON) {
            private int skills;

            @Override
            public void atTurnStart(Combat combat) {
                skills = 0;
            }

            @Override
            public void afterCardPlayed(Combat combat, Card card) {
                if (card.type().equals(CardType.SKILL) && ++skills % 3 == 0) {
                    flash(combat);
                    combat.applyPower(combat.player(), combat.player(), Powers.focus(1));
                }
            }

            @Override
            public String description() {
                return "Every time you play 3 Skills in a single turn, gain 1 Focus.";
            }
        });
        // Ornamental Fan.
        c.addRelic(() -> new Relic("relic:spin_fan", "Spin Fan", RelicTier.UNCOMMON) {
            private int attacks;

            @Override
            public void atTurnStart(Combat combat) {
                attacks = 0;
            }

            @Override
            public void afterCardPlayed(Combat combat, Card card) {
                if (card.type().equals(CardType.ATTACK) && ++attacks % 3 == 0) {
                    flash(combat);
                    combat.gainBlock(combat.player(), 4, null);
                }
            }

            @Override
            public String description() {
                return "Every time you play 3 Attacks in a single turn, gain 4 Block.";
            }
        });
        // Gremlin Horn.
        c.addRelic(() -> new Relic("relic:flicky_rescue", "Flicky Rescue", RelicTier.UNCOMMON) {
            @Override
            public void onEnemyDeath(Combat combat, Enemy enemy) {
                if (!combat.isOver()) {
                    flash(combat);
                    combat.gainEnergy(1);
                    combat.draw(1);
                }
            }

            @Override
            public String description() {
                return "Whenever an enemy is destroyed, gain 1 Energy and draw 1 card. A Flicky flies free.";
            }
        });
        // Meat on the Bone.
        c.addRelic(() -> new Relic("relic:chili_dog_stand", "Chili Dog Stand", RelicTier.UNCOMMON) {
            @Override
            public void onVictory(Combat combat) {
                if (combat.player().hp() * 2 <= combat.player().maxHp()) {
                    flash(combat);
                    combat.healPlayer(12);
                }
            }

            @Override
            public String description() {
                return "If your HP is at or below 50% at the end of combat, heal 12 HP.";
            }
        });
        // Horn Cleat.
        c.addRelic(() -> new Relic("relic:turbo_ring", "Turbo Ring", RelicTier.UNCOMMON) {
            @Override
            public void atTurnStart(Combat combat) {
                if (combat.turn() == 2) {
                    flash(combat);
                    combat.gainBlock(combat.player(), 14, null);
                }
            }

            @Override
            public String description() {
                return "At the start of your 2nd turn, gain 14 Block.";
            }
        });
        // Paper Phrog.
        c.addRelic(() -> new Relic("relic:magnifying_glass", "Magnifying Glass", RelicTier.UNCOMMON) {
            @Override
            public float modifyVulnerableMultiplier(Combat combat, Creature target, float multiplier) {
                return target.isPlayer() ? multiplier : Math.max(multiplier, 1.75f);
            }

            @Override
            public String description() {
                return "Enemies with Vulnerable take 75% more damage rather than 50%.";
            }
        });
        // Letter Opener.
        c.addRelic(() -> new Relic("relic:gadget_wrench", "Gadget Wrench", RelicTier.UNCOMMON) {
            private int skills;

            @Override
            public void atTurnStart(Combat combat) {
                skills = 0;
            }

            @Override
            public void afterCardPlayed(Combat combat, Card card) {
                if (card.type().equals(CardType.SKILL) && ++skills % 3 == 0) {
                    flash(combat);
                    for (Enemy e : combat.activeEnemies()) {
                        combat.dealDamage(combat.player(), e, 5, slaytherobotnik.core.DamageType.THORNS);
                    }
                }
            }

            @Override
            public String description() {
                return "Every time you play 3 Skills in a single turn, deal 5 damage to ALL enemies.";
            }
        });
        // Pantograph.
        c.addRelic(() -> new Relic("relic:big_ring", "Big Ring", RelicTier.UNCOMMON) {
            @Override
            public void onEnterRoom(RunState run, String roomType) {
                if (roomType.equals(RoomType.BOSS)) {
                    run.heal(25);
                }
            }

            @Override
            public String description() {
                return "At the start of boss fights, heal 25 HP.";
            }
        });
    }

    // ------------------------------------------------------------------ rare

    private static void rares(Catalog c) {
        // Lizard Tail.
        c.addRelic(() -> new Relic("relic:extra_life", "Extra Life", RelicTier.RARE) {
            @Override
            public boolean preventDeath(Combat combat) {
                setUsedUp(true);
                combat.revivePlayer(combat.player().maxHp() / 2);
                return true;
            }

            @Override
            public String description() {
                return "When you would die, heal to 50% of your Max HP instead (works once).";
            }
        });
        // Unceasing Top.
        c.addRelic(() -> new Relic("relic:spinning_top", "Spinning Top", RelicTier.RARE) {
            @Override
            public void afterCardPlayed(Combat combat, Card card) {
                if (combat.player().hand().isEmpty() && !combat.isOver()) {
                    flash(combat);
                    combat.draw(1);
                }
            }

            @Override
            public String description() {
                return "Whenever you have no cards in hand during your turn, draw a card.";
            }
        });
        // Tungsten Rod.
        c.addRelic(() -> new Relic("relic:metal_plating", "Metal Plating", RelicTier.RARE) {
            @Override
            public int modifyHpLoss(Combat combat, int hpLoss, Creature source, String type) {
                return Math.max(0, hpLoss - 1);
            }

            @Override
            public String description() {
                return "Whenever you would lose HP, lose 1 less.";
            }
        });
        // Captain's Wheel.
        c.addRelic(() -> new Relic("relic:tornado_wheel", "Tornado Wheel", RelicTier.RARE) {
            @Override
            public void atTurnStart(Combat combat) {
                if (combat.turn() == 3) {
                    flash(combat);
                    combat.gainBlock(combat.player(), 18, null);
                }
            }

            @Override
            public String description() {
                return "At the start of your 3rd turn, gain 18 Block.";
            }
        });
        // Girya-style rest option, Knuckles flavoured: Shovel.
        c.addRelic(() -> new Relic("relic:shovel_claw", "Shovel Claw", RelicTier.RARE) {
            @Override
            public void addRestOptions(RunState run, List<RestOption> options) {
                options.add(new RestOption("dig", "Dig", "Obtain a random relic.", true, () -> {
                    String id = run.takeRelicFromPool(run.rollRelicTier(run.rngs().stream(RunRngs.RELICS)));
                    if (id != null) {
                        run.obtainRelic(id);
                    }
                }));
            }

            @Override
            public String description() {
                return "You can Dig at Starposts to obtain a random relic.";
            }
        });
        // Ice Cream.
        c.addRelic(() -> new Relic("relic:thermos", "Thermos", RelicTier.RARE) {
            private int leftover;

            @Override
            public void atTurnEnd(Combat combat) {
                leftover = combat.player().energy();
            }

            @Override
            public void atTurnStart(Combat combat) {
                if (combat.turn() > 1 && leftover > 0) {
                    combat.gainEnergy(leftover);
                    leftover = 0;
                }
            }

            @Override
            public void atBattleStart(Combat combat) {
                leftover = 0;
            }

            @Override
            public String description() {
                return "Energy is now conserved between turns.";
            }
        });
    }

    // ------------------------------------------------------------------ boss

    private static void bosses(Catalog c) {
        // Philosopher's Stone.
        c.addRelic(() -> new Relic("relic:chaos_emerald", "Chaos Emerald", RelicTier.BOSS) {
            @Override
            public int energyBonus() {
                return 1;
            }

            @Override
            public void atBattleStart(Combat combat) {
                for (Enemy e : combat.activeEnemies()) {
                    combat.applyPower(null, e, Powers.strength(1));
                }
            }

            @Override
            public String description() {
                return "Gain 1 Energy at the start of each turn. ALL enemies start with 1 Strength.";
            }
        });
        // Coffee Dripper.
        c.addRelic(() -> new Relic("relic:overclocked_engine", "Overclocked Engine", RelicTier.BOSS) {
            @Override
            public int energyBonus() {
                return 1;
            }

            @Override
            public boolean canRest(RunState run) {
                return false;
            }

            @Override
            public String description() {
                return "Gain 1 Energy at the start of each turn. You can no longer Rest at Starposts.";
            }
        });
        // Fusion Hammer.
        c.addRelic(() -> new Relic("relic:piko_hammer", "Piko Piko Hammer", RelicTier.BOSS) {
            @Override
            public int energyBonus() {
                return 1;
            }

            @Override
            public void addRestOptions(RunState run, List<RestOption> options) {
                options.replaceAll(o -> o.id().equals("smith")
                        ? new RestOption(o.id(), o.label(), "The hammer is too heavy to tinker with.", false, o.action())
                        : o);
            }

            @Override
            public String description() {
                return "Gain 1 Energy at the start of each turn. You can no longer Tune Up at Starposts.";
            }
        });
        // Busted Crown.
        c.addRelic(() -> new Relic("relic:cracked_crown", "Cracked Crown", RelicTier.BOSS) {
            @Override
            public int energyBonus() {
                return 1;
            }

            @Override
            public int modifyCardRewardSize(RunState run, int count) {
                return Math.max(1, count - 2);
            }

            @Override
            public String description() {
                return "Gain 1 Energy at the start of each turn. Card rewards offer 2 fewer cards.";
            }
        });
        // Snecko Eye, simplified.
        c.addRelic(() -> new Relic("relic:mystery_monitor", "Mystery Monitor", RelicTier.BOSS) {
            @Override
            public int drawBonus() {
                return 2;
            }

            @Override
            public void onCardDrawn(Combat combat, Card card) {
                if (card.baseCost() >= 0) {
                    card.setCostForCombat(combat.miscRng().range(0, 3));
                }
            }

            @Override
            public String description() {
                return "Draw 2 additional cards each turn. Drawn cards have their cost randomized (0-3).";
            }
        });
        // Black Star.
        c.addRelic(() -> new Relic("relic:star_emblem", "Star Emblem", RelicTier.BOSS) {
            @Override
            public void addCombatRewards(RunState run, String roomType, List<Reward> rewards) {
                if (roomType.equals(RoomType.ELITE)) {
                    String id = run.takeRelicFromPool(run.rollRelicTier(run.rngs().stream(RunRngs.RELICS)));
                    if (id != null) {
                        rewards.add(new Reward.RelicReward(id));
                    }
                }
            }

            @Override
            public String description() {
                return "Elites drop an additional relic when defeated.";
            }
        });
        // Character boss upgrades.
        c.addRelic(() -> new Relic("relic:light_speed_shoes", "Light Speed Shoes", RelicTier.BOSS) {
            @Override
            public void onEquip(RunState run) {
                run.relics().removeIf(r -> r.id().equals(RED_SNEAKERS));
            }

            @Override
            public int modifyCombo(Combat combat, Card card, int count) {
                return combat.comboCardsPlayedThisTurn() <= 2 ? count + 1 : count;
            }

            @Override
            public String description() {
                return "Replaces Red Sneakers. The first 2 Combo cards you play each turn repeat 1 more time.";
            }
        }.onlyFor(Characters.SONIC));
        c.addRelic(() -> new Relic("relic:workshop", "Mobile Workshop", RelicTier.BOSS) {
            @Override
            public void onEquip(RunState run) {
                run.relics().removeIf(r -> r.id().equals(TINKER_KIT));
            }

            @Override
            public void atBattleStart(Combat combat) {
                flash(combat);
                for (int i = 0; i < 3; i++) {
                    combat.createInHand(TailsCards.RING_BOMB, true);
                }
            }

            @Override
            public String description() {
                return "Replaces Tinker Kit. At the start of each combat, add 3 upgraded Ring Bombs to your hand.";
            }
        }.onlyFor(Characters.TAILS));
        c.addRelic(() -> new Relic("relic:master_emerald", "Master Emerald", RelicTier.BOSS) {
            @Override
            public void onEquip(RunState run) {
                run.relics().removeIf(r -> r.id().equals(MASTER_EMERALD_SHARD));
            }

            @Override
            public void onVictory(Combat combat) {
                flash(combat);
                combat.healPlayer(12);
            }

            @Override
            public String description() {
                return "Replaces Master Emerald Shard. At the end of combat, heal 12 HP.";
            }
        }.onlyFor(Characters.KNUCKLES));
    }

    // ------------------------------------------------------------------ shop

    private static void shop(Catalog c) {
        // Membership Card.
        c.addRelic(() -> new Relic("relic:vip_card", "Egg Robo VIP Card", RelicTier.SHOP) {
            @Override
            public int modifyShopPrice(RunState run, int price) {
                return price / 2;
            }

            @Override
            public String description() {
                return "50% discount on all of the Egg Robo's goods. \"Don't tell Robotnik, ya hear?!\"";
            }
        });
        // Orange Pellets, simplified to Weak/Vulnerable/Frail cleanse on turn start.
        c.addRelic(() -> new Relic("relic:clean_oil", "Clean Oil", RelicTier.SHOP) {
            @Override
            public void atTurnStart(Combat combat) {
                boolean any = false;
                for (String id : new String[] {Powers.WEAK, Powers.VULNERABLE, Powers.FRAIL}) {
                    if (combat.player().has(id)) {
                        combat.removePower(combat.player(), id);
                        any = true;
                    }
                }
                if (any) {
                    flash(combat);
                }
            }

            @Override
            public String description() {
                return "At the start of your turn, remove Weak, Vulnerable and Frail from yourself.";
            }
        });
        // Strange Spoon-ish: card draw.
        c.addRelic(() -> new Relic("relic:spare_parts_bin", "Spare Parts Bin", RelicTier.SHOP) {
            @Override
            public void onCardExhausted(Combat combat, Card card) {
                if (!combat.isOver() && combat.phase().equals(Combat.PLAYER_TURN)) {
                    var gen = new slaytherobotnik.run.RewardGenerator(combat.run());
                    var rarity = gen.rollRarity(RoomType.MONSTER, combat.cardRng());
                    var def = gen.randomCardOf(combat.run().character().color(), rarity, List.of(), combat.cardRng());
                    if (def != null) {
                        flash(combat);
                        combat.addCreatedCard(new Card(def), "hand");
                    }
                }
            }

            @Override
            public String description() {
                return "Whenever you Exhaust a card, add a random card of your colour to your hand.";
            }
        });
    }

    private static void upgradeRandom(RunState run, String type, int count) {
        List<Card> pool = new ArrayList<>();
        for (Card card : run.upgradableCards()) {
            if (card.type().equals(type)) {
                pool.add(card);
            }
        }
        run.rngs().stream(RunRngs.RELICS).shuffle(pool);
        for (int i = 0; i < count && i < pool.size(); i++) {
            run.upgradeCard(pool.get(i));
        }
    }
}
