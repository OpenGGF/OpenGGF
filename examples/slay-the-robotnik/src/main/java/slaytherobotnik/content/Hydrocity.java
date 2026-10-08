package slaytherobotnik.content;

import java.util.List;
import slaytherobotnik.core.ActDef;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.Combat;
import slaytherobotnik.core.CombatEvent;
import slaytherobotnik.core.Creature;
import slaytherobotnik.core.EncounterDef;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.core.IntentKind;
import slaytherobotnik.core.Move;
import slaytherobotnik.core.Power;
import slaytherobotnik.core.Powers;
import slaytherobotnik.core.Rng;

/**
 * Act 2: Hydrocity Zone. Its badniks take the roles of Slay the Spire's act 2 monsters:
 * Jawz swarm like Byrds, the Blastoid turret guards like a Spheric Guardian, the Turbo
 * Spiker hides in a plated shell like a Shelled Parasite, the Mega Chopper latches on like
 * a Snake Plant, and the Buggernaut mother keeps hatching her brood. The bosses are the
 * act 1 miniboss Big Shaker and Robotnik's Screw Mobile.
 */
public final class Hydrocity {
    public static final int ACT = 2;

    private Hydrocity() {
    }

    public static void register(Catalog c) {
        c.addAct(new ActDef(ACT, "Act 2", "Hydrocity",
                1, 0,               // ROM zone and act: Hydrocity act 1
                0x03, 0x19, 0x18,   // music for the map (HCZ1), the boss (boss theme), elites (miniboss theme)
                "Flooded ruins, roaring pumps and a lot of very hungry fish.", null));

        c.addEncounter(new EncounterDef("hcz:jawz_school", "Jawz School", ACT, EncounterDef.WEAK, 1,
                hp -> List.of(new Jawz(hp), new Jawz(hp), new Jawz(hp))));
        c.addEncounter(new EncounterDef("hcz:blastoid", "Blastoid", ACT, EncounterDef.WEAK, 1,
                hp -> List.of(new Blastoid(hp))));
        c.addEncounter(new EncounterDef("hcz:turbo_spiker", "Turbo Spiker", ACT, EncounterDef.WEAK, 1,
                hp -> List.of(new TurboSpiker(hp))));
        c.addEncounter(new EncounterDef("hcz:pointdexter_pair", "Pointdexters", ACT, EncounterDef.WEAK, 1,
                hp -> List.of(new Pointdexter(hp), new Pointdexter(hp))));

        c.addEncounter(new EncounterDef("hcz:mega_chopper", "Mega Chopper", ACT, EncounterDef.STRONG, 2,
                hp -> List.of(new MegaChopper(hp))));
        c.addEncounter(new EncounterDef("hcz:buggernaut_brood", "Buggernaut Brood", ACT, EncounterDef.STRONG, 2,
                hp -> List.of(new BabyBuggernaut(hp), new Buggernaut(hp, false), new BabyBuggernaut(hp))));
        c.addEncounter(new EncounterDef("hcz:turret_and_jawz", "Turret Patrol", ACT, EncounterDef.STRONG, 2,
                hp -> List.of(new Jawz(hp), new Blastoid(hp))));
        c.addEncounter(new EncounterDef("hcz:spiker_and_puffer", "Spiky Reef", ACT, EncounterDef.STRONG, 2,
                hp -> List.of(new TurboSpiker(hp), new Pointdexter(hp))));
        c.addEncounter(new EncounterDef("hcz:jawz_frenzy", "Jawz Frenzy", ACT, EncounterDef.STRONG, 1,
                hp -> List.of(new Jawz(hp), new Jawz(hp), new Jawz(hp), new Jawz(hp))));
        c.addEncounter(new EncounterDef("hcz:chopper_and_turret", "Deep Water", ACT, EncounterDef.STRONG, 1,
                hp -> List.of(new Blastoid(hp), new MegaChopper(hp))));

        c.addEncounter(new EncounterDef("hcz:feeding_frenzy", "Feeding Frenzy", ACT, EncounterDef.ELITE, 1,
                hp -> List.of(new FrenzyChopper(hp))));
        c.addEncounter(new EncounterDef("hcz:buggernaut_queen", "Buggernaut Queen", ACT, EncounterDef.ELITE, 1,
                hp -> List.of(new BabyBuggernaut(hp), new Buggernaut(hp, true), new BabyBuggernaut(hp))));
        c.addEncounter(new EncounterDef("hcz:pump_station", "Pump Station", ACT, EncounterDef.ELITE, 1,
                hp -> List.of(new Blastoid(hp), new TurboSpiker(hp), new Blastoid(hp))));

        c.addEncounter(new EncounterDef("hcz:big_shaker", "Big Shaker", ACT, EncounterDef.BOSS, 1,
                hp -> List.of(new BigShaker())));
        c.addEncounter(new EncounterDef("hcz:screw_mobile", "Screw Mobile", ACT, EncounterDef.BOSS, 1,
                hp -> List.of(new ScrewMobile())));
    }

    // ================================================================== badniks

    /** Byrd. Pecks in a flurry; swoops; after taking enough hits it is stunned and grounded for a turn. */
    public static final class Jawz extends Enemy {
        private final Move chomp = Move.multiAttack("chomp", "Nibble", 1, 5);
        private final Move dart = Move.attack("dart", "Torpedo", 12);
        private final Move circle = Move.of("circle", "Circle", IntentKind.BUFF);
        private final Move stunned = Move.of("stunned", "Belly Up", IntentKind.STUN);
        private boolean dazed;

        public Jawz(Rng hp) {
            super("hcz:jawz", "Jawz", hp.range(25, 31));
        }

        @Override
        protected void onSpawn(Combat c) {
            Jawz self = this;
            c.applyPower(this, this, new Power("hcz:dart", "Darting", Power.BUFF, 3) {
                @Override
                public void onAttacked(Combat combat, Creature source, int damage, int hpLost, String type) {
                    if (hpLost > 0 && !self.dazed && type.equals(slaytherobotnik.core.DamageType.ATTACK)) {
                        addAmount(-1);
                        if (amount() <= 0) {
                            self.dazed = true;
                            setAmount(3);
                            self.setMove(self.stunned);
                            combat.events().add(new CombatEvent.Cue(self, "stunned"));
                        }
                    }
                }

                @Override
                public String description() {
                    return "After " + amount() + " more attacks it is stunned for a turn.";
                }
            });
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (dazed) {
                setMove(stunned);
                return;
            }
            int roll = ai.nextInt(100);
            if (roll < 50) {
                setMove(lastTwoMoves("chomp") ? dart : chomp);
            } else if (roll < 80) {
                setMove(lastMove("dart") ? chomp : dart);
            } else {
                setMove(lastMove("circle") ? chomp : circle);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            switch (move.id()) {
                case "chomp", "dart" -> attackPlayer(c, move);
                case "circle" -> c.applyPower(this, this, Powers.strength(1));
                default -> dazed = false;
            }
        }
    }

    /** Spheric Guardian. A turret that starts behind 40 Block and never drops it. */
    public static final class Blastoid extends Enemy {
        private final Move jets = Move.multiAttack("jets", "Water Jets", 10, 2);
        private final Move harden = Move.of("harden", "Harden", IntentKind.DEFEND);
        private final Move cannon = Move.of("cannon", "Cannon", IntentKind.ATTACK_DEBUFF, 10, 1);
        private final Move brace = Move.of("brace", "Brace", IntentKind.ATTACK_DEFEND, 10, 1);

        public Blastoid(Rng hp) {
            super("hcz:blastoid", "Blastoid", 20);
        }

        @Override
        protected void onSpawn(Combat c) {
            c.applyPower(this, this, Powers.barricade());
            c.applyPower(this, this, Powers.artifact(3));
            c.gainBlock(this, 40, null);
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            switch (turnsTaken()) {
                case 0 -> setMove(harden);
                case 1 -> setMove(cannon);
                default -> setMove(lastMove("jets") ? brace : jets);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            switch (move.id()) {
                case "harden" -> c.gainBlock(this, 25, null);
                case "cannon" -> {
                    attackPlayer(c, move);
                    c.applyPower(this, c.player(), Powers.frail(5));
                }
                case "brace" -> {
                    attackPlayer(c, move);
                    c.gainBlock(this, 15, null);
                }
                default -> attackPlayer(c, move);
            }
        }
    }

    /** Shelled Parasite. Plated Armor shell; stunned when the shell breaks. */
    public static final class TurboSpiker extends Enemy {
        private final Move spin = Move.multiAttack("spin", "Shell Spin", 6, 2);
        private final Move launch = Move.of("launch", "Spike Launch", IntentKind.ATTACK_DEBUFF, 18, 1);
        private final Move drain = Move.of("drain", "Clamp", IntentKind.ATTACK_BUFF, 10, 1);
        private final Move stunned = Move.of("stunned", "Shell Cracked", IntentKind.STUN);
        private boolean broken;

        public TurboSpiker(Rng hp) {
            super("hcz:turbo_spiker", "Turbo Spiker", hp.range(68, 72));
        }

        @Override
        protected void onSpawn(Combat c) {
            c.applyPower(this, this, Powers.platedArmor(14));
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (!broken && !has(Powers.PLATED_ARMOR) && turnsTaken() > 0) {
                broken = true;
                setMove(stunned);
                return;
            }
            if (turnsTaken() == 0) {
                setMove(ai.nextBoolean() ? launch : spin);
                return;
            }
            int roll = ai.nextInt(100);
            if (roll < 20) {
                setMove(lastMove("launch") ? spin : launch);
            } else if (roll < 60) {
                setMove(lastTwoMoves("spin") ? drain : spin);
            } else {
                setMove(lastTwoMoves("drain") ? spin : drain);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            switch (move.id()) {
                case "launch" -> {
                    attackPlayer(c, move);
                    c.applyPower(this, c.player(), Powers.frail(2));
                }
                case "drain" -> {
                    int before = c.player().hp();
                    attackPlayer(c, move);
                    c.heal(this, Math.max(0, before - c.player().hp()));
                }
                case "stunned" -> { }
                default -> attackPlayer(c, move);
            }
        }
    }

    /** A puffer that inflates more spikes each turn. */
    public static final class Pointdexter extends Enemy {
        private final Move inflate = Move.of("inflate", "Inflate", IntentKind.BUFF);
        private final Move poke = Move.attack("poke", "Spine Poke", 7);

        public Pointdexter(Rng hp) {
            super("hcz:pointdexter", "Pointdexter", hp.range(42, 48));
        }

        @Override
        protected void onSpawn(Combat c) {
            c.applyPower(this, this, Powers.thorns(3));
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (amount(Powers.THORNS) >= 9 || lastMove("inflate")) {
                setMove(poke);
            } else {
                setMove(ai.chance(0.5) ? inflate : poke);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            if (move == inflate) {
                c.applyPower(this, this, Powers.thorns(2));
            } else {
                attackPlayer(c, move);
            }
        }
    }

    /** Snake Plant. Latches on with triple chomps; slippery skin hardens each time it is hit. */
    public static final class MegaChopper extends Enemy {
        private final Move chomp = Move.multiAttack("chomp", "Chomp Chomp Chomp", 7, 3);
        private final Move latch = Move.of("latch", "Latch On", IntentKind.STRONG_DEBUFF);

        public MegaChopper(Rng hp) {
            super("hcz:mega_chopper", "Mega Chopper", hp.range(75, 79));
        }

        @Override
        protected void onSpawn(Combat c) {
            c.applyPower(this, this, slippery(3));
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (ai.nextInt(100) < 65) {
                setMove(lastTwoMoves("chomp") ? latch : chomp);
            } else {
                setMove(lastMove("latch") ? chomp : latch);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            if (move == latch) {
                c.applyPower(this, c.player(), Powers.weak(2));
                c.applyPower(this, c.player(), Powers.frail(2));
            } else {
                attackPlayer(c, move);
            }
        }
    }

    /** The Buggernaut mother: summons babies; the queen variant is an elite that rallies them. */
    public static final class Buggernaut extends Enemy {
        private final boolean queen;
        private final Move hatch = Move.of("hatch", "Hatch", IntentKind.UNKNOWN);
        private final Move rally = Move.of("rally", "Rally the Brood", IntentKind.DEFEND_BUFF);
        private final Move sting;

        public Buggernaut(Rng hp, boolean queen) {
            super(queen ? "hcz:buggernaut_queen" : "hcz:buggernaut", queen ? "Buggernaut Queen" : "Buggernaut",
                    queen ? hp.range(140, 148) : hp.range(38, 42));
            this.queen = queen;
            sting = Move.multiAttack("sting", "Sting", queen ? 6 : 4, queen ? 3 : 2);
            setArt("hcz:buggernaut");
        }

        private int babies(Combat c) {
            int n = 0;
            for (Enemy e : c.activeEnemies()) {
                if (e instanceof BabyBuggernaut) {
                    n++;
                }
            }
            return n;
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            int babies = babies(c);
            if (babies < 2 && !lastMove("hatch") && ai.nextInt(100) < 75) {
                setMove(hatch);
            } else if (queen && babies > 0 && !lastMove("rally")) {
                setMove(ai.chance(0.5) ? rally : sting);
            } else {
                setMove(sting);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            switch (move.id()) {
                case "hatch" -> {
                    int index = c.enemies().indexOf(this);
                    for (int i = 0; i < (queen ? 2 : 1) && babies(c) < 3; i++) {
                        BabyBuggernaut baby = new BabyBuggernaut(c.aiRng());
                        c.spawnEnemy(baby, i == 0 ? index : index + 2);
                    }
                }
                case "rally" -> {
                    for (Enemy e : c.activeEnemies()) {
                        c.applyPower(this, e, Powers.strength(3));
                        c.gainBlock(e, 6, null);
                    }
                }
                default -> attackPlayer(c, move);
            }
        }

        @Override
        protected void onDeath(Combat c) {
            if (queen) {
                // The brood scatters when the queen falls (Gremlin Leader's minions flee).
                for (Enemy e : c.activeEnemies()) {
                    if (e instanceof BabyBuggernaut baby) {
                        baby.flee(c);
                    }
                }
            }
        }
    }

    /** A Buggernaut hatchling. */
    public static final class BabyBuggernaut extends Enemy {
        private final Move nip;

        public BabyBuggernaut(Rng hp) {
            super("hcz:baby_buggernaut", "Baby Buggernaut", hp.range(10, 13));
            nip = Move.attack("nip", "Nip", hp.range(4, 5));
            markMinion();
        }

        void flee(Combat c) {
            escape(c);
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            setMove(nip);
        }

        @Override
        protected void perform(Combat c, Move move) {
            attackPlayer(c, move);
        }
    }

    // ================================================================== elites

    /** Book of Stabbing. Chomps once more every turn; unblocked bites leave a Dent. */
    public static final class FrenzyChopper extends Enemy {
        private int chomps = 1;
        private final Move bigBite = Move.attack("bite", "Big Bite", 21);

        public FrenzyChopper(Rng hp) {
            super("hcz:frenzy_chopper", "Frenzied Mega Chopper", hp.range(160, 164));
            setArt("hcz:mega_chopper");
        }

        @Override
        protected void onSpawn(Combat c) {
            Enemy self = this;
            c.applyPower(this, this, new Power("hcz:painful_bites", "Painful Bites", Power.BUFF, 1) {
                @Override
                public void onAttack(Combat combat, Creature target, int damage, int hpLost, String type) {
                    if (hpLost > 0 && target.isPlayer()) {
                        combat.addCreatedCard(new Card(combat.catalog().card(CommonCards.DENT)), "discard");
                    }
                }

                @Override
                public boolean showsAmount() {
                    return false;
                }

                @Override
                public String description() {
                    return "Whenever it deals unblocked damage, shuffle a Dent into your discard pile.";
                }
            });
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (ai.nextInt(100) < 15 && !lastMove("bite")) {
                setMove(bigBite);
            } else {
                chomps++;
                setMove(Move.multiAttack("frenzy", "Frenzy", 6, chomps));
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            attackPlayer(c, move);
        }
    }

    // ================================================================== bosses

    /** The Champ. Hammers and taunts; at half HP it throws off its debuffs and goes into overdrive. */
    public static final class BigShaker extends Enemy {
        private final Move blades = Move.multiAttack("blades", "Propeller Blades", 6, 3);
        private final Move slam = Move.of("slam", "Pump Slam", IntentKind.ATTACK_DEBUFF, 12, 1);
        private final Move whirlpool = Move.of("whirlpool", "Whirlpool", IntentKind.DEBUFF);
        private final Move charge = Move.of("charge", "Build Pressure", IntentKind.DEFEND_BUFF);
        private final Move heavy = Move.attack("heavy", "Torrent", 16);
        private final Move overdrive = Move.of("overdrive", "Overdrive", IntentKind.BUFF);
        private final Move execute = Move.multiAttack("execute", "Maelstrom", 10, 2);
        private boolean enraged;
        private int cycle;

        public BigShaker() {
            super("hcz:big_shaker", "Big Shaker", 420);
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (!enraged && hp() * 2 <= maxHp()) {
                enraged = true;
                setMove(overdrive);
                return;
            }
            if (enraged) {
                setMove(lastMove("execute") || lastMove("overdrive") ? (ai.chance(0.5) ? heavy : blades) : execute);
                return;
            }
            cycle++;
            if (cycle % 4 == 0) {
                setMove(charge);
            } else if (cycle % 4 == 2) {
                setMove(ai.chance(0.5) ? whirlpool : slam);
            } else {
                setMove(ai.chance(0.5) ? heavy : blades);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            switch (move.id()) {
                case "slam" -> {
                    attackPlayer(c, move);
                    c.applyPower(this, c.player(), Powers.frail(2));
                }
                case "whirlpool" -> {
                    c.applyPower(this, c.player(), Powers.weak(2));
                    c.applyPower(this, c.player(), Powers.vulnerable(2));
                    for (int i = 0; i < 2; i++) {
                        c.addCreatedCard(new Card(c.catalog().card(CommonCards.DIZZY)), "draw");
                    }
                }
                case "charge" -> {
                    c.applyPower(this, this, Powers.metallicize(5));
                    c.gainBlock(this, 15, null);
                }
                case "overdrive" -> {
                    for (Power p : powers()) {
                        if (p.isDebuff()) {
                            c.removePower(this, p.id());
                        }
                    }
                    c.applyPower(this, this, Powers.strength(6));
                }
                default -> attackPlayer(c, move);
            }
        }
    }

    /** The Collector. Drops depth charges (minions that explode), dives, and floods you with debuffs. */
    public static final class ScrewMobile extends Enemy {
        private final Move mines = Move.of("mines", "Depth Charges", IntentKind.UNKNOWN);
        private final Move dive = Move.attack("dive", "Drill Dive", 18);
        private final Move overclock = Move.of("overclock", "Overclock", IntentKind.DEFEND_BUFF);
        private final Move flood = Move.of("flood", "Flood the Chamber", IntentKind.STRONG_DEBUFF);
        private boolean flooded;

        public ScrewMobile() {
            super("hcz:screw_mobile", "Screw Mobile", 300);
        }

        private int mineCount(Combat c) {
            int n = 0;
            for (Enemy e : c.activeEnemies()) {
                if (e instanceof DepthCharge) {
                    n++;
                }
            }
            return n;
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (turnsTaken() == 0) {
                setMove(mines);
            } else if (!flooded && turnsTaken() >= 3) {
                flooded = true;
                setMove(flood);
            } else if (mineCount(c) == 0 && !lastMove("mines") && ai.chance(0.7)) {
                setMove(mines);
            } else {
                setMove(lastMove("overclock") || ai.chance(0.6) ? dive : overclock);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            switch (move.id()) {
                case "mines" -> {
                    c.spawnEnemy(new DepthCharge(), 0);
                    c.spawnEnemy(new DepthCharge(), -1);
                }
                case "overclock" -> {
                    for (Enemy e : c.activeEnemies()) {
                        c.applyPower(this, e, Powers.strength(3));
                    }
                    c.gainBlock(this, 15, null);
                }
                case "flood" -> {
                    c.applyPower(this, c.player(), Powers.weak(3));
                    c.applyPower(this, c.player(), Powers.vulnerable(3));
                    c.applyPower(this, c.player(), Powers.frail(3));
                }
                default -> attackPlayer(c, move);
            }
        }

        @Override
        protected void onDeath(Combat c) {
            for (Enemy e : c.activeEnemies()) {
                if (e instanceof DepthCharge mine) {
                    mine.fizzle(c);
                }
            }
        }
    }

    /** A floating mine: counts down and explodes. */
    public static final class DepthCharge extends Enemy {
        private final Move tick = Move.of("tick", "Ticking", IntentKind.UNKNOWN);
        private final Move boom = Move.attack("boom", "Explode", 12);

        public DepthCharge() {
            super("hcz:depth_charge", "Depth Charge", 18);
            markMinion();
        }

        void fizzle(Combat c) {
            escape(c);
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            setMove(turnsTaken() >= 1 ? boom : tick);
        }

        @Override
        protected void perform(Combat c, Move move) {
            if (move == boom) {
                attackPlayer(c, move);
                c.loseHp(this, hp());
            }
        }
    }

    /** Snake Plant's Malleable: Block whenever hit, growing each time this turn. */
    static Power slippery(int amount) {
        return new Power("hcz:slippery", "Slippery", Power.BUFF, amount) {
            private int bonus;

            @Override
            public void onAttacked(Combat c, Creature source, int damage, int hpLost, String type) {
                if (hpLost > 0 && type.equals(slaytherobotnik.core.DamageType.ATTACK) && !owner().isDead()) {
                    c.gainBlock(owner(), amount() + bonus, null);
                    bonus++;
                }
            }

            @Override
            public void atRoundEnd(Combat c) {
                bonus = 0;
            }

            @Override
            public String description() {
                return "Whenever it loses HP to an attack, gains " + amount()
                        + " Block, plus 1 more for each time already this round.";
            }
        };
    }
}
