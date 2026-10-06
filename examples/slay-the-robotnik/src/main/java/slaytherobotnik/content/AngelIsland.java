package slaytherobotnik.content;

import java.util.List;
import slaytherobotnik.core.ActDef;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.Combat;
import slaytherobotnik.core.CombatEvent;
import slaytherobotnik.core.EncounterDef;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.core.IntentKind;
import slaytherobotnik.core.Move;
import slaytherobotnik.core.Power;
import slaytherobotnik.core.Powers;
import slaytherobotnik.core.Rng;

/**
 * Act 1: Angel Island Zone. The badniks are Rhinobot, Monkey Dude, Bloominator and
 * Caterkiller Jr.; the elites are oversized versions; the bosses are the Fire Breath
 * miniboss and Robotnik's Flame Craft. Every enemy notes the Slay the Spire monster whose
 * numbers and AI it borrows, so balance knowledge carries over.
 */
public final class AngelIsland {
    public static final int ACT = 1;

    private AngelIsland() {
    }

    public static void register(Catalog c) {
        c.addAct(new ActDef(ACT, "Act 1", "Angel Island",
                0, 0,               // ROM zone and act: Angel Island act 1
                0x01, 0x19, 0x18,   // music for the map (AIZ1), the boss (boss theme), elites (miniboss theme)
                "The floating island, burning under Robotnik's bombers.", null));

        c.addEncounter(new EncounterDef("aiz:rhinobot", "Rhinobot", ACT, EncounterDef.WEAK, 1,
                hp -> List.of(new Rhinobot(hp))));
        c.addEncounter(new EncounterDef("aiz:monkey_pair", "Monkey Dudes", ACT, EncounterDef.WEAK, 1,
                hp -> List.of(new MonkeyDude(hp), new MonkeyDude(hp))));
        c.addEncounter(new EncounterDef("aiz:caterkiller_pair", "Caterkiller Jr. Pair", ACT, EncounterDef.WEAK, 1,
                hp -> List.of(new CaterkillerJr(hp, false), new CaterkillerJr(hp, false))));
        c.addEncounter(new EncounterDef("aiz:bloominator", "Bloominator", ACT, EncounterDef.WEAK, 1,
                hp -> List.of(new Bloominator(hp))));

        c.addEncounter(new EncounterDef("aiz:big_caterkiller", "Caterkiller Jr. (Large)", ACT, EncounterDef.STRONG, 2,
                hp -> List.of(new BigCaterkiller(hp))));
        c.addEncounter(new EncounterDef("aiz:monkey_troupe", "Monkey Troupe", ACT, EncounterDef.STRONG, 2,
                hp -> List.of(new MonkeyDude(hp), new MonkeyDude(hp), new MonkeyDude(hp))));
        c.addEncounter(new EncounterDef("aiz:wildlife", "Jungle Wildlife", ACT, EncounterDef.STRONG, 2,
                hp -> List.of(new Bloominator(hp), new Rhinobot(hp))));
        c.addEncounter(new EncounterDef("aiz:flower_bed", "Flower Bed", ACT, EncounterDef.STRONG, 2,
                hp -> List.of(new Bloominator(hp), new Bloominator(hp))));
        c.addEncounter(new EncounterDef("aiz:patrol", "Badnik Patrol", ACT, EncounterDef.STRONG, 2,
                hp -> List.of(new MonkeyDude(hp), new Rhinobot(hp))));
        c.addEncounter(new EncounterDef("aiz:caterkillers", "Caterkiller Nest", ACT, EncounterDef.STRONG, 1,
                hp -> List.of(new CaterkillerJr(hp, false), new CaterkillerJr(hp, true), new CaterkillerJr(hp, false))));

        c.addEncounter(new EncounterDef("aiz:mega_rhinobot", "Mega Rhinobot", ACT, EncounterDef.ELITE, 1,
                hp -> List.of(new MegaRhinobot(hp))));
        c.addEncounter(new EncounterDef("aiz:bloominator_grove", "Bloominator Grove", ACT, EncounterDef.ELITE, 1,
                hp -> List.of(new GroveBloominator(hp, false), new GroveBloominator(hp, true),
                        new GroveBloominator(hp, false))));
        c.addEncounter(new EncounterDef("aiz:caterkiller_sr", "Caterkiller Sr.", ACT, EncounterDef.ELITE, 1,
                hp -> List.of(new CaterkillerSr(hp))));

        c.addEncounter(new EncounterDef("aiz:fire_breath", "Fire Breath", ACT, EncounterDef.BOSS, 1,
                hp -> List.of(new FireBreath())));
        c.addEncounter(new EncounterDef("aiz:flame_craft", "Flame Craft", ACT, EncounterDef.BOSS, 1,
                hp -> List.of(new FlameCraft())));
    }

    // ================================================================== normal badniks

    /** Plays Slay the Spire's Jaw Worm: charges, skids for Block, revs up for Strength. */
    public static final class Rhinobot extends Enemy {
        private final Move charge = Move.attack("charge", "Charge", 11);
        private final Move skid = Move.of("skid", "Skid Turn", IntentKind.ATTACK_DEFEND, 7, 1);
        private final Move revUp = Move.of("rev", "Rev Up", IntentKind.DEFEND_BUFF);

        public Rhinobot(Rng hp) {
            super("aiz:rhinobot", "Rhinobot", hp.range(40, 44));
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (turnsTaken() == 0) {
                setMove(charge);
                return;
            }
            // Slay the Spire's Jaw Worm table: 25% charge, 30% skid, 45% rev up, with a reroll when a
            // move would repeat too often. The odds inside each reroll are the original's.
            int roll = ai.nextInt(100);
            if (roll < 25) {
                setMove(lastMove("charge") ? (ai.chance(0.5625) ? revUp : skid) : charge);
            } else if (roll < 55) {
                setMove(lastTwoMoves("skid") ? (ai.chance(0.357) ? charge : revUp) : skid);
            } else {
                setMove(lastMove("rev") ? (ai.chance(0.416) ? charge : skid) : revUp);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            switch (move.id()) {
                case "charge" -> attackPlayer(c, move);
                case "skid" -> {
                    attackPlayer(c, move);
                    c.gainBlock(this, 5, null);
                }
                default -> {
                    c.applyPower(this, this, Powers.strength(3));
                    c.gainBlock(this, 6, null);
                }
            }
        }
    }

    /** Louse. Throws coconuts and pumps itself up; curls up for Block the first time it's hit. */
    public static final class MonkeyDude extends Enemy {
        private final Move toss;
        private final Move hoot = Move.of("hoot", "Hoot", IntentKind.BUFF);
        private final int curlAmount;

        public MonkeyDude(Rng hp) {
            super("aiz:monkey_dude", "Monkey Dude", hp.range(10, 15));
            toss = Move.attack("toss", "Coconut Toss", hp.range(5, 7));
            curlAmount = hp.range(3, 7);
        }

        @Override
        protected void onSpawn(Combat c) {
            c.applyPower(this, this, climbUp(curlAmount));
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            int roll = ai.nextInt(100);
            if (roll < 25) {
                setMove(lastTwoMoves("hoot") ? toss : hoot);
            } else {
                setMove(lastTwoMoves("toss") ? hoot : toss);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            if (move == toss) {
                attackPlayer(c, move);
            } else {
                c.applyPower(this, this, Powers.strength(3));
            }
        }
    }

    /** Fungi Beast. Shoots spike seeds, blooms for Strength, and bursts into spores when destroyed. */
    public static final class Bloominator extends Enemy {
        private final Move spikeShot = Move.attack("shot", "Spike Shot", 6);
        private final Move bloom = Move.of("bloom", "Bloom", IntentKind.BUFF);

        public Bloominator(Rng hp) {
            super("aiz:bloominator", "Bloominator", hp.range(22, 28));
        }

        @Override
        protected void onSpawn(Combat c) {
            c.applyPower(this, this, sporeCloud(2));
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            int roll = ai.nextInt(100);
            if (roll < 60) {
                setMove(lastTwoMoves("shot") ? bloom : spikeShot);
            } else {
                setMove(lastMove("bloom") ? spikeShot : bloom);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            if (move == spikeShot) {
                attackPlayer(c, move);
            } else {
                c.applyPower(this, this, Powers.strength(3));
            }
        }
    }

    /** Small/medium slimes. {@code oily} ones spit Oil Slick instead of biting harder. */
    public static final class CaterkillerJr extends Enemy {
        private final boolean oily;
        private final Move bite;
        private final Move spit = Move.of("spit", "Oil Spit", IntentKind.ATTACK_DEBUFF, 7, 1);
        private final Move curl = Move.of("curl", "Curl", IntentKind.DEBUFF);

        public CaterkillerJr(Rng hp, boolean oily) {
            this(hp.range(oily ? 28 : 13, oily ? 32 : 17), oily);
        }

        CaterkillerJr(int hp, boolean oily) {
            super("aiz:caterkiller_jr", "Caterkiller Jr.", hp);
            this.oily = oily;
            bite = Move.attack("bite", "Bite", oily ? 10 : 5);
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            int roll = ai.nextInt(100);
            if (oily && roll < 30 && !lastTwoMoves("spit")) {
                setMove(spit);
            } else if (roll < 65) {
                setMove(lastTwoMoves("bite") ? curl : bite);
            } else {
                setMove(lastMove("curl") ? bite : curl);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            switch (move.id()) {
                case "bite" -> attackPlayer(c, move);
                case "spit" -> {
                    attackPlayer(c, move);
                    c.addCreatedCard(new Card(c.catalog().card(CommonCards.OIL_SLICK)), "discard");
                }
                default -> c.applyPower(this, c.player(), Powers.weak(1));
            }
        }
    }

    /** Large slime: splits into two Caterkiller Jr. at half HP. */
    public static final class BigCaterkiller extends Enemy {
        private final Move lash = Move.attack("lash", "Lash", 16);
        private final Move spit = Move.of("spit", "Oil Spit", IntentKind.ATTACK_DEBUFF, 11, 1);
        private final Move coil = Move.of("coil", "Coil", IntentKind.DEBUFF);
        private final Move split = Move.of("split", "Split", IntentKind.UNKNOWN);
        private boolean splitting;

        public BigCaterkiller(Rng hp) {
            super("aiz:big_caterkiller", "Caterkiller Jr. (Large)", hp.range(65, 69));
        }

        @Override
        protected void onSpawn(Combat c) {
            Enemy self = this;
            c.applyPower(this, this, new Power("aiz:split", "Split", Power.BUFF, 1) {
                @Override
                public void onHpLost(Combat combat, int amount) {
                    if (!splitting && self.hp() > 0 && self.hp() * 2 <= self.maxHp()) {
                        splitting = true;
                        setMove(split);
                    }
                }

                @Override
                public boolean showsAmount() {
                    return false;
                }

                @Override
                public String description() {
                    return "When reduced to half HP or less, splits into two smaller Caterkillers.";
                }
            });
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (splitting) {
                setMove(split);
                return;
            }
            int roll = ai.nextInt(100);
            if (roll < 40) {
                setMove(lastTwoMoves("spit") ? lash : spit);
            } else if (roll < 70) {
                setMove(lastTwoMoves("lash") ? spit : lash);
            } else {
                setMove(lastMove("coil") ? spit : coil);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            switch (move.id()) {
                case "split" -> {
                    int index = c.enemies().indexOf(this);
                    int hpEach = hp();
                    vanish(c, "split");
                    c.spawnEnemy(new CaterkillerJr(hpEach, true), index + 1);
                    c.spawnEnemy(new CaterkillerJr(hpEach, true), index + 2);
                }
                case "lash" -> attackPlayer(c, move);
                case "spit" -> {
                    attackPlayer(c, move);
                    for (int i = 0; i < 2; i++) {
                        c.addCreatedCard(new Card(c.catalog().card(CommonCards.OIL_SLICK)), "discard");
                    }
                }
                default -> c.applyPower(this, c.player(), Powers.weak(2));
            }
        }
    }

    // ================================================================== elites

    /** Gremlin Nob. Enraged by Skills. */
    public static final class MegaRhinobot extends Enemy {
        private final Move bellow = Move.of("bellow", "Engine Roar", IntentKind.BUFF);
        private final Move rush = Move.attack("rush", "Rush", 14);
        private final Move gore = Move.of("gore", "Gore", IntentKind.ATTACK_DEBUFF, 6, 1);

        public MegaRhinobot(Rng hp) {
            super("aiz:mega_rhinobot", "Mega Rhinobot", hp.range(82, 86));
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (turnsTaken() == 0) {
                setMove(bellow);
                return;
            }
            int roll = ai.nextInt(100);
            if (roll < 33) {
                setMove(gore);
            } else {
                setMove(lastTwoMoves("rush") ? gore : rush);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            switch (move.id()) {
                case "bellow" -> c.applyPower(this, this, enrage(2));
                case "rush" -> attackPlayer(c, move);
                default -> {
                    attackPlayer(c, move);
                    c.applyPower(this, c.player(), Powers.vulnerable(2));
                }
            }
        }
    }

    /** Sentry. Alternates scattering pollen (Dizzy) and a seed barrage. */
    public static final class GroveBloominator extends Enemy {
        private final Move pollen = Move.of("pollen", "Pollen Burst", IntentKind.DEBUFF);
        private final Move barrage = Move.attack("barrage", "Seed Barrage", 9);
        private final boolean startsWithBarrage;

        public GroveBloominator(Rng hp, boolean startsWithBarrage) {
            super("aiz:grove_bloominator", "Bloominator", hp.range(38, 42));
            this.startsWithBarrage = startsWithBarrage;
            setArt("aiz:bloominator");
        }

        @Override
        protected void onSpawn(Combat c) {
            c.applyPower(this, this, Powers.artifact(1));
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (turnsTaken() == 0) {
                setMove(startsWithBarrage ? barrage : pollen);
            } else {
                setMove(lastMove("barrage") ? pollen : barrage);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            if (move == barrage) {
                attackPlayer(c, move);
            } else {
                for (int i = 0; i < 2; i++) {
                    c.addCreatedCard(new Card(c.catalog().card(CommonCards.DIZZY)), "discard");
                }
            }
        }
    }

    /** Lagavulin. Sleeps coiled in armour, then lashes and siphons your stats. */
    public static final class CaterkillerSr extends Enemy {
        private final Move sleep = Move.of("sleep", "Asleep", IntentKind.SLEEP);
        private final Move stunned = Move.of("stunned", "Stunned", IntentKind.STUN);
        private final Move lash = Move.attack("lash", "Lash", 18);
        private final Move siphon = Move.of("siphon", "Siphon", IntentKind.STRONG_DEBUFF);
        private boolean awake;
        private boolean wokenByDamage;
        private int idleTurns;

        public CaterkillerSr(Rng hp) {
            super("aiz:caterkiller_sr", "Caterkiller Sr.", hp.range(109, 111));
        }

        @Override
        protected void onSpawn(Combat c) {
            c.applyPower(this, this, Powers.metallicize(8));
            c.gainBlock(this, 8, null);
            CaterkillerSr self = this;
            c.applyPower(this, this, new Power("aiz:asleep", "Asleep", Power.BUFF, 1) {
                @Override
                public void onHpLost(Combat combat, int amount) {
                    if (!self.awake) {
                        self.wake(combat, true);
                    }
                }

                @Override
                public boolean showsAmount() {
                    return false;
                }

                @Override
                public String description() {
                    return "Asleep. Wakes up after 3 turns or when it loses HP.";
                }
            });
        }

        void wake(Combat c, boolean byDamage) {
            awake = true;
            wokenByDamage = byDamage;
            c.removePower(this, "aiz:asleep");
            c.removePower(this, Powers.METALLICIZE);
            c.events().add(new CombatEvent.Cue(this, "wake"));
            if (byDamage) {
                setMove(stunned);
            }
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (!awake) {
                setMove(sleep);
                return;
            }
            if (wokenByDamage && lastMove("sleep")) {
                setMove(stunned);
                return;
            }
            int attacksSinceSiphon = 0;
            var history = history();
            for (int i = history.size() - 1; i >= 0 && history.get(i).equals("lash"); i--) {
                attacksSinceSiphon++;
            }
            setMove(attacksSinceSiphon >= 2 ? siphon : lash);
        }

        @Override
        protected void perform(Combat c, Move move) {
            switch (move.id()) {
                case "sleep" -> {
                    if (++idleTurns >= 3) {
                        wake(c, false);
                    }
                }
                case "stunned" -> wokenByDamage = false;
                case "lash" -> attackPlayer(c, move);
                default -> {
                    c.applyPower(this, c.player(), Powers.strength(-1));
                    c.applyPower(this, c.player(), Powers.dexterity(-1));
                }
            }
        }
    }

    // ================================================================== bosses

    /** Hexaghost. Ignites, unleashes a firestorm scaled to your HP, then cycles scorch/charge/stoke/inferno. */
    public static final class FireBreath extends Enemy {
        private final Move ignite = Move.of("ignite", "Ignition", IntentKind.UNKNOWN);
        private final Move scorch = Move.of("scorch", "Scorch", IntentKind.ATTACK_DEBUFF, 6, 1);
        private final Move charge = Move.multiAttack("charge", "Flame Charge", 5, 2);
        private final Move stoke = Move.of("stoke", "Stoke the Furnace", IntentKind.DEFEND_BUFF);
        private final Move inferno = Move.of("inferno", "Inferno", IntentKind.ATTACK_DEBUFF, 2, 6);
        private final String[] cycle = {"scorch", "charge", "scorch", "stoke", "charge", "scorch", "inferno"};
        private int cycleIndex;
        private boolean burnsUpgraded;

        public FireBreath() {
            super("aiz:fire_breath", "Fire Breath", 250);
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (turnsTaken() == 0) {
                setMove(ignite);
                return;
            }
            if (turnsTaken() == 1) {
                int perHit = c.player().hp() / 12 + 1;
                setMove(Move.multiAttack("firestorm", "Firestorm", perHit, 6));
                return;
            }
            String next = cycle[cycleIndex % cycle.length];
            cycleIndex++;
            setMove(switch (next) {
                case "scorch" -> scorch;
                case "charge" -> charge;
                case "stoke" -> stoke;
                default -> inferno;
            });
        }

        @Override
        protected void perform(Combat c, Move move) {
            switch (move.id()) {
                case "ignite" -> c.events().add(new CombatEvent.Cue(this, "ignite"));
                case "firestorm", "charge" -> attackPlayer(c, move);
                case "scorch" -> {
                    attackPlayer(c, move);
                    addBurns(c, 1);
                }
                case "stoke" -> {
                    c.applyPower(this, this, Powers.strength(2));
                    c.gainBlock(this, 12, null);
                }
                default -> {
                    attackPlayer(c, move);
                    addBurns(c, 3);
                    if (!burnsUpgraded) {
                        burnsUpgraded = true;
                        for (Card card : c.allCombatCards()) {
                            if (card.id().equals(CommonCards.BURN)) {
                                c.upgradeForCombat(card);
                            }
                        }
                    }
                }
            }
        }

        private void addBurns(Combat c, int count) {
            for (int i = 0; i < count; i++) {
                c.addCreatedCard(new Card(c.catalog().card(CommonCards.BURN), burnsUpgraded), "discard");
            }
        }
    }

    /** The Guardian. Bombards you until damaged enough, then hovers with a red-hot hull. */
    public static final class FlameCraft extends Enemy {
        private final Move chargeUp = Move.of("charge", "Prime Bombs", IntentKind.DEFEND);
        private final Move bombDrop = Move.attack("bomb", "Bomb Drop", 32);
        private final Move napalm = Move.of("napalm", "Napalm Spray", IntentKind.STRONG_DEBUFF);
        private final Move sweep = Move.multiAttack("sweep", "Flame Sweep", 5, 4);
        private final Move hover = Move.of("hover", "Hover Mode", IntentKind.BUFF);
        private final Move dive = Move.attack("dive", "Dive Bomb", 9);
        private final Move burners = Move.of("burners", "Twin Burners", IntentKind.ATTACK_BUFF, 8, 2);
        private final String[] offense = {"charge", "bomb", "napalm", "sweep"};
        private int offenseIndex;
        private boolean defensive;
        private int threshold = 30;

        public FlameCraft() {
            super("aiz:flame_craft", "Flame Craft", 240);
        }

        @Override
        protected void onSpawn(Combat c) {
            c.applyPower(this, this, modeShift(threshold));
        }

        private Power modeShift(int amount) {
            FlameCraft self = this;
            return new Power("aiz:mode_shift", "Mode Shift", Power.BUFF, amount) {
                @Override
                public void onHpLost(Combat combat, int lost) {
                    if (self.defensive || self.isDead()) {
                        return;
                    }
                    addAmount(-lost);
                    if (amount() <= 0) {
                        self.enterHover(combat);
                    }
                }

                @Override
                public String description() {
                    return "After taking " + amount() + " more damage, switches to Hover Mode.";
                }
            };
        }

        void enterHover(Combat c) {
            defensive = true;
            c.removePower(this, "aiz:mode_shift");
            c.events().add(new CombatEvent.Cue(this, "hover"));
            setMove(hover);
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (defensive) {
                if (lastMove("hover")) {
                    setMove(dive);
                } else if (lastMove("dive")) {
                    setMove(burners);
                } else {
                    setMove(hover);
                }
                return;
            }
            String next = offense[offenseIndex % offense.length];
            offenseIndex++;
            setMove(switch (next) {
                case "charge" -> chargeUp;
                case "bomb" -> bombDrop;
                case "napalm" -> napalm;
                default -> sweep;
            });
        }

        @Override
        protected void perform(Combat c, Move move) {
            switch (move.id()) {
                case "charge" -> c.gainBlock(this, 9, null);
                case "bomb", "sweep", "dive" -> attackPlayer(c, move);
                case "napalm" -> {
                    c.applyPower(this, c.player(), Powers.weak(2));
                    c.applyPower(this, c.player(), Powers.vulnerable(2));
                }
                case "hover" -> {
                    c.gainBlock(this, 20, null);
                    c.applyPower(this, this, Powers.thorns(3));
                }
                default -> {
                    attackPlayer(c, move);
                    c.removePower(this, Powers.THORNS);
                    defensive = false;
                    threshold += 10;
                    c.applyPower(this, this, modeShift(threshold));
                }
            }
        }
    }

    // ================================================================== powers

    /** Louse's Curl Up: Block the first time it takes attack damage. */
    static Power climbUp(int amount) {
        return new Power("aiz:climb", "Tree Climb", Power.BUFF, amount) {
            @Override
            public void onAttacked(Combat c, slaytherobotnik.core.Creature source, int damage, int hpLost,
                    String type) {
                if (hpLost > 0 && owner().hp() > 0) {
                    c.gainBlock(owner(), amount(), null);
                    c.removePower(owner(), id());
                }
            }

            @Override
            public String description() {
                return "The first time it is hit, scrambles up a tree and gains " + amount() + " Block.";
            }
        };
    }

    /** Fungi Beast's Spore Cloud. */
    static Power sporeCloud(int amount) {
        return new Power("aiz:spore_cloud", "Spore Cloud", Power.BUFF, amount) {
            @Override
            public void onDeath(Combat c) {
                c.applyPower(owner(), c.player(), Powers.vulnerable(amount()));
            }

            @Override
            public String description() {
                return "When destroyed, applies " + amount() + " Vulnerable to you.";
            }
        };
    }

    /** Gremlin Nob's Enrage. */
    static Power enrage(int amount) {
        return new Power("aiz:enrage", "Enrage", Power.BUFF, amount) {
            @Override
            public void onPlayerCardPlayed(Combat c, Card card) {
                if (card.type().equals(slaytherobotnik.core.CardType.SKILL)) {
                    c.applyPower(owner(), owner(), Powers.strength(amount()));
                }
            }

            @Override
            public String description() {
                return "Whenever you play a Skill, gains " + amount() + " Strength.";
            }
        };
    }
}
