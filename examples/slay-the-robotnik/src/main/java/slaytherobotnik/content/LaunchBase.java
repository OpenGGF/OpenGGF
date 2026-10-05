package slaytherobotnik.content;

import java.util.List;
import slaytherobotnik.core.ActDef;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.CardType;
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
 * Act 3: Launch Base Zone, where the Death Egg waits on the pad. The badniks play Slay the
 * Spire's act 3 roles: Snale Blasters reform like Darklings until all are down, the Orbinaut
 * grows like an Orb Walker, Ribot twins buff and debuff in tandem, the Corkey laser charges
 * like a Transient, and Flybot767s bomb in flocks. The bosses are Big Arm and the Beam
 * Rocket that guards the launch.
 */
public final class LaunchBase {
    public static final int ACT = 3;

    private LaunchBase() {
    }

    public static void register(Catalog c) {
        // S3K zone 6 (LBZ): act 1 art (the Death Egg on its pad), act 2 music 0x0E; miniboss 0x18; boss 0x19.
        c.addAct(new ActDef(ACT, "Act 3", "Launch Base", 6, 0, 0x0E, 0x19, 0x18,
                "Robotnik's launch site. The Death Egg is fuelled and ready.", null));

        c.addEncounter(new EncounterDef("lbz:snale_trio", "Snale Blasters", ACT, EncounterDef.WEAK, 1,
                hp -> List.of(new SnaleBlaster(hp), new SnaleBlaster(hp), new SnaleBlaster(hp))));
        c.addEncounter(new EncounterDef("lbz:orbinaut", "Orbinaut", ACT, EncounterDef.WEAK, 1,
                hp -> List.of(new Orbinaut(hp))));
        c.addEncounter(new EncounterDef("lbz:flybots", "Flybot Flight", ACT, EncounterDef.WEAK, 1,
                hp -> List.of(new Flybot(hp), new Flybot(hp), new Flybot(hp))));

        c.addEncounter(new EncounterDef("lbz:ribot_twins", "Ribot Twins", ACT, EncounterDef.STRONG, 2,
                hp -> List.of(new Ribot(hp, true), new Ribot(hp, false))));
        c.addEncounter(new EncounterDef("lbz:corkey", "Corkey Turret", ACT, EncounterDef.STRONG, 2,
                hp -> List.of(new Corkey(hp))));
        c.addEncounter(new EncounterDef("lbz:orbinaut_escort", "Orbinaut Escort", ACT, EncounterDef.STRONG, 2,
                hp -> List.of(new Flybot(hp), new Orbinaut(hp), new Flybot(hp))));
        c.addEncounter(new EncounterDef("lbz:snale_squad", "Snale Squad", ACT, EncounterDef.STRONG, 1,
                hp -> List.of(new SnaleBlaster(hp), new SnaleBlaster(hp), new SnaleBlaster(hp), new SnaleBlaster(hp))));
        c.addEncounter(new EncounterDef("lbz:laser_grid", "Laser Grid", ACT, EncounterDef.STRONG, 1,
                hp -> List.of(new Corkey(hp), new Ribot(hp, true))));

        c.addEncounter(new EncounterDef("lbz:ribot_commander", "Ribot Commander", ACT, EncounterDef.ELITE, 1,
                hp -> List.of(new RibotCommander(hp))));
        c.addEncounter(new EncounterDef("lbz:star_orbinaut", "Star Orbinaut", ACT, EncounterDef.ELITE, 1,
                hp -> List.of(new StarOrbinaut(hp))));
        c.addEncounter(new EncounterDef("lbz:snale_mother", "Snale Mother", ACT, EncounterDef.ELITE, 1,
                hp -> List.of(new SnaleMother(hp))));

        c.addEncounter(new EncounterDef("lbz:big_arm", "Big Arm", ACT, EncounterDef.BOSS, 1,
                hp -> List.of(new BigArm())));
        c.addEncounter(new EncounterDef("lbz:beam_rocket", "Beam Rocket", ACT, EncounterDef.BOSS, 1,
                hp -> List.of(new BeamRocket())));
    }

    // ================================================================== badniks

    /** Darkling. Fires from its shell; when destroyed it reforms in two turns unless every Snale is down. */
    public static final class SnaleBlaster extends Enemy {
        private final Move shot = Move.attack("shot", "Blast", 9);
        private final Move volley = Move.multiAttack("volley", "Volley", 3, 2);
        private final Move shell = Move.of("shell", "Retreat", IntentKind.DEFEND);
        private final Move reforming = Move.of("reform", "Reforming", IntentKind.UNKNOWN);
        private int down;

        public SnaleBlaster(Rng hp) {
            super("lbz:snale_blaster", "Snale Blaster", hp.range(48, 56));
        }

        @Override
        protected void onDeath(Combat c) {
            for (Enemy e : c.activeEnemies()) {
                if (e != this && e instanceof SnaleBlaster other && other.down == 0) {
                    // Another Snale still stands: this one pulls into its shell to reform.
                    reviveTo(1);
                    setHalfDead(true);
                    down = 1;
                    setMove(reforming);
                    c.events().add(new CombatEvent.Cue(this, "shell"));
                    return;
                }
            }
            for (Enemy e : c.enemies()) {
                if (e instanceof SnaleBlaster other && other.down > 0) {
                    other.setHalfDead(false);
                    other.finish(c);
                }
            }
        }

        void finish(Combat c) {
            down = 0;
            c.loseHp(this, hp());
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (down > 0) {
                setMove(reforming);
                return;
            }
            int roll = ai.nextInt(100);
            if (roll < 40) {
                setMove(lastMove("shot") ? volley : shot);
            } else if (roll < 70) {
                setMove(lastTwoMoves("volley") ? shot : volley);
            } else {
                setMove(lastMove("shell") ? shot : shell);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            switch (move.id()) {
                case "reform" -> {
                    down++;
                    if (down > 2) {
                        down = 0;
                        setHalfDead(false);
                        reviveTo(maxHp() / 2);
                        c.events().add(new CombatEvent.Healed(this, maxHp() / 2));
                    }
                }
                case "shell" -> c.gainBlock(this, 12, null);
                default -> attackPlayer(c, move);
            }
        }
    }

    /** Orb Walker. Its orbiting spikes grow every turn; lasers leave Burns. */
    public static final class Orbinaut extends Enemy {
        private final Move laser = Move.of("laser", "Spike Laser", IntentKind.ATTACK_DEBUFF, 10, 1);
        private final Move claw = Move.attack("claw", "Orbit Strike", 15);

        public Orbinaut(Rng hp) {
            super("lbz:orbinaut", "Orbinaut", hp.range(90, 96));
        }

        @Override
        protected void onSpawn(Combat c) {
            c.applyPower(this, this, Powers.ritual(3));
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            setMove(ai.nextInt(100) < 40 ? (lastTwoMoves("laser") ? claw : laser) : (lastTwoMoves("claw") ? laser : claw));
        }

        @Override
        protected void perform(Combat c, Move move) {
            attackPlayer(c, move);
            if (move == laser) {
                c.addCreatedCard(new Card(c.catalog().card(CommonCards.BURN)), "discard");
                c.addCreatedCard(new Card(c.catalog().card(CommonCards.BURN)), "draw");
            }
        }
    }

    /** A flying bomber; Byrd-like but explosive. */
    public static final class Flybot extends Enemy {
        private final Move bomb = Move.of("bomb", "Bomb Drop", IntentKind.ATTACK_DEBUFF, 7, 1);
        private final Move dive = Move.multiAttack("dive", "Strafe", 2, 4);

        public Flybot(Rng hp) {
            super("lbz:flybot", "Flybot767", hp.range(30, 36));
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            setMove(lastMove("bomb") ? dive : (ai.chance(0.6) ? bomb : dive));
        }

        @Override
        protected void perform(Combat c, Move move) {
            attackPlayer(c, move);
            if (move == bomb) {
                c.applyPower(this, c.player(), Powers.vulnerable(1));
            }
        }
    }

    /** Donu or Deca, small: twin robots, one strengthens the pair, one floods your deck with Static. */
    public static final class Ribot extends Enemy {
        private final boolean leader;
        private final Move punch = Move.multiAttack("punch", "Twin Punch", 6, 2);
        private final Move special;

        public Ribot(Rng hp, boolean leader) {
            super("lbz:ribot", leader ? "Ribot (Red)" : "Ribot (Blue)", hp.range(62, 66));
            this.leader = leader;
            special = leader ? Move.of("boost", "Power Link", IntentKind.BUFF)
                    : Move.of("jam", "Jamming Signal", IntentKind.DEBUFF);
        }

        @Override
        protected void onSpawn(Combat c) {
            c.applyPower(this, this, Powers.artifact(1));
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            boolean specialTurn = (turnsTaken() % 2 == 0) == leader;
            setMove(specialTurn ? special : punch);
        }

        @Override
        protected void perform(Combat c, Move move) {
            switch (move.id()) {
                case "boost" -> {
                    for (Enemy e : c.activeEnemies()) {
                        c.applyPower(this, e, Powers.strength(2));
                    }
                }
                case "jam" -> {
                    for (int i = 0; i < 2; i++) {
                        c.addCreatedCard(new Card(c.catalog().card(CommonCards.STATIC)), "draw");
                    }
                }
                default -> attackPlayer(c, move);
            }
        }
    }

    /** Transient-like: charges up, then fires a beam that grows each time. */
    public static final class Corkey extends Enemy {
        private int beam = 20;
        private final Move charge = Move.of("charge", "Charging", IntentKind.DEFEND);

        public Corkey(Rng hp) {
            super("lbz:corkey", "Corkey", hp.range(110, 118));
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (lastMove("beam") || turnsTaken() == 0) {
                setMove(charge);
            } else {
                setMove(Move.attack("beam", "Ceiling Laser", beam));
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            if (move == charge) {
                c.gainBlock(this, 15, null);
            } else {
                attackPlayer(c, move);
                beam += 10;
            }
        }
    }

    // ================================================================== elites

    /** Giant Head. Counts down while slowing you; then a huge strike that grows each turn. */
    public static final class RibotCommander extends Enemy {
        private int countdown = 4;
        private int smash = 30;

        public RibotCommander(Rng hp) {
            super("lbz:ribot_commander", "Ribot Commander", hp.range(500, 520));
            setArt("lbz:ribot");
        }

        @Override
        protected void onSpawn(Combat c) {
            c.applyPower(this, this, slowPower());
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (countdown > 1) {
                countdown--;
                setMove(ai.chance(0.5) ? Move.multiAttack("count", "Count Down (" + countdown + ")", 13, 1)
                        : Move.of("glare", "Target Lock", IntentKind.DEBUFF));
            } else {
                setMove(Move.attack("smash", "Hydraulic Smash", smash));
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            switch (move.id()) {
                case "glare" -> c.applyPower(this, c.player(), Powers.weak(1));
                case "smash" -> {
                    attackPlayer(c, move);
                    smash += 5;
                }
                default -> attackPlayer(c, move);
            }
        }
    }

    /** Nemesis. Intangible every other turn; adds Burns; scythes hard. */
    public static final class StarOrbinaut extends Enemy {
        private final Move orbit = Move.multiAttack("orbit", "Star Orbit", 6, 3);
        private final Move scythe = Move.attack("scythe", "Spike Scythe", 45);
        private final Move debuff = Move.of("burns", "Hot Stars", IntentKind.DEBUFF);

        public StarOrbinaut(Rng hp) {
            super("lbz:star_orbinaut", "Star Orbinaut", hp.range(185, 190));
            setArt("lbz:orbinaut");
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (lastMove("scythe")) {
                setMove(ai.chance(0.5) ? orbit : debuff);
            } else {
                int roll = ai.nextInt(100);
                setMove(roll < 30 && !lastMove("orbit") ? orbit : roll < 65 ? scythe : debuff);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            if (move == debuff) {
                for (int i = 0; i < 3; i++) {
                    c.addCreatedCard(new Card(c.catalog().card(CommonCards.BURN)), "discard");
                }
            } else {
                attackPlayer(c, move);
            }
            // Like Nemesis, it turns intangible every other turn (through your next turn).
            if (turnsTaken() % 2 == 1 && !has(Powers.INTANGIBLE)) {
                c.applyPower(this, this, Powers.intangible(1));
            }
        }
    }

    /** Reptomancer. Summons Snale Blasters and bites hard. */
    public static final class SnaleMother extends Enemy {
        private final Move summon = Move.of("summon", "Brood", IntentKind.UNKNOWN);
        private final Move bite = Move.attack("bite", "Shell Crush", 30);
        private final Move spit = Move.multiAttack("spit", "Pellet Spray", 13, 2);

        public SnaleMother(Rng hp) {
            super("lbz:snale_mother", "Snale Mother", hp.range(190, 200));
            setArt("lbz:snale_blaster");
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            int children = c.activeEnemies().size() - 1;
            if (turnsTaken() == 0 || (children < 2 && !lastMove("summon") && ai.chance(0.4))) {
                setMove(summon);
            } else {
                setMove(ai.chance(0.5) ? bite : spit);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            if (move == summon) {
                for (int i = 0; i < 2; i++) {
                    Enemy child = new Snalelet(c.aiRng());
                    c.spawnEnemy(child, i == 0 ? 0 : -1);
                }
            } else {
                attackPlayer(c, move);
            }
        }

        @Override
        protected void onDeath(Combat c) {
            for (Enemy e : c.activeEnemies()) {
                if (e instanceof Snalelet s) {
                    s.flee(c);
                }
            }
        }
    }

    /** Reptomancer's dagger: strikes hard once, then explodes. */
    public static final class Snalelet extends Enemy {
        private final Move stab = Move.attack("stab", "Ram", 9);
        private final Move pop = Move.attack("pop", "Burst", 25);

        public Snalelet(Rng hp) {
            super("lbz:snalelet", "Snale Hatchling", hp.range(20, 25));
            setArt("lbz:snale_blaster");
            markMinion();
        }

        void flee(Combat c) {
            escape(c);
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            setMove(turnsTaken() == 0 ? stab : pop);
        }

        @Override
        protected void perform(Combat c, Move move) {
            attackPlayer(c, move);
            if (move == pop) {
                c.loseHp(this, hp());
            }
        }
    }

    // ================================================================== bosses

    /** Time Eater. Grabs you after every 12 cards you play, ending your turn; heals once when low. */
    public static final class BigArm extends Enemy {
        private final Move pinch = Move.multiAttack("pinch", "Pincer Barrage", 7, 3);
        private final Move slam = Move.of("slam", "Arm Slam", IntentKind.ATTACK_DEBUFF, 26, 1);
        private final Move ripple = Move.of("ripple", "Shockwave", IntentKind.DEFEND_DEBUFF);
        private final Move haste = Move.of("haste", "Emergency Repairs", IntentKind.BUFF);
        private boolean repaired;

        public BigArm() {
            super("lbz:big_arm", "Big Arm", 456);
        }

        @Override
        protected void onSpawn(Combat c) {
            c.applyPower(this, this, grabPower());
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (!repaired && hp() * 2 < maxHp()) {
                repaired = true;
                setMove(haste);
                return;
            }
            int roll = ai.nextInt(100);
            if (roll < 45) {
                setMove(lastTwoMoves("pinch") ? slam : pinch);
            } else if (roll < 80) {
                setMove(lastMove("slam") ? pinch : slam);
            } else {
                setMove(lastMove("ripple") ? pinch : ripple);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            switch (move.id()) {
                case "slam" -> {
                    attackPlayer(c, move);
                    c.applyPower(this, c.player(), Powers.frail(1));
                }
                case "ripple" -> {
                    c.gainBlock(this, 20, null);
                    c.applyPower(this, c.player(), Powers.vulnerable(1));
                    c.applyPower(this, c.player(), Powers.weak(1));
                }
                case "haste" -> {
                    for (Power p : powers()) {
                        if (p.isDebuff()) {
                            c.removePower(this, p.id());
                        }
                    }
                    c.heal(this, maxHp() / 2 - hp());
                }
                default -> attackPlayer(c, move);
            }
        }

        private Power grabPower() {
            return new Power("lbz:grab", "Grabber", Power.BUFF, 0) {
                {
                    allowNegative();
                }

                @Override
                public void onPlayerCardPlayed(Combat combat, Card card) {
                    addAmount(1);
                    if (amount() >= 12) {
                        setAmount(0);
                        combat.events().add(new CombatEvent.Cue(owner(), "grab"));
                        combat.events().add(new CombatEvent.Message("Big Arm grabs you!"));
                        combat.applyPower(owner(), owner(), Powers.strength(2));
                        combat.endTurnAfterCard();
                    }
                }

                @Override
                public String description() {
                    return "Every 12 cards you play, it grabs you: your turn ends and it gains 2 Strength. ("
                            + amount() + "/12)";
                }
            };
        }
    }

    /** Awakened One-like. A rocket turret that thrives on Powers, then launches into a second phase. */
    public static final class BeamRocket extends Enemy {
        private final Move beam = Move.attack("beam", "Beam Cannon", 20);
        private final Move barrage = Move.multiAttack("barrage", "Rocket Barrage", 6, 4);
        private final Move ignite = Move.of("ignite", "Ignition", IntentKind.UNKNOWN);
        private final Move scorch = Move.of("scorch", "Exhaust Plume", IntentKind.ATTACK_DEBUFF, 10, 3);
        private boolean secondStage;

        public BeamRocket() {
            super("lbz:beam_rocket", "Beam Rocket", 300);
        }

        @Override
        protected void onSpawn(Combat c) {
            c.applyPower(this, this, Powers.regen(10));
            c.applyPower(this, this, powerSurge());
        }

        @Override
        protected void onDeath(Combat c) {
            if (!secondStage) {
                secondStage = true;
                setHalfDead(true);
                reviveTo(1);
                setMove(ignite);
                for (Power p : powers()) {
                    c.removePower(this, p.id());
                }
                // Its lower column segment blows off, as when the ROM boss loses a segment.
                setArt("lbz:beam_rocket_core");
                c.events().add(new CombatEvent.Cue(this, "second_stage"));
            }
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (halfDead()) {
                setMove(ignite);
                return;
            }
            if (secondStage) {
                setMove(lastMove("scorch") ? barrage : scorch);
            } else {
                setMove(lastTwoMoves("beam") || ai.chance(0.4) ? barrage : beam);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            if (move == ignite) {
                setHalfDead(false);
                reviveTo(maxHp());
                c.events().add(new CombatEvent.Healed(this, maxHp()));
                c.applyPower(this, this, Powers.metallicize(8));
                return;
            }
            attackPlayer(c, move);
            if (move == scorch) {
                c.addCreatedCard(new Card(c.catalog().card(CommonCards.BURN)), "discard");
            }
        }

        private Power powerSurge() {
            return new Power("lbz:power_surge", "Power Surge", Power.BUFF, 2) {
                @Override
                public void onPlayerCardPlayed(Combat combat, Card card) {
                    if (card.type().equals(CardType.POWER)) {
                        combat.applyPower(owner(), owner(), Powers.strength(amount()));
                    }
                }

                @Override
                public String description() {
                    return "Whenever you play a Power, gains " + amount() + " Strength.";
                }
            };
        }
    }

    /** Giant Head's Slow: each card you play makes its attacks hit 10% harder this turn. */
    static Power slowPower() {
        return new Power("lbz:slow", "Overclock", Power.BUFF, 0) {
            {
                allowNegative();
            }

            @Override
            public void onPlayerCardPlayed(Combat c, Card card) {
                addAmount(1);
            }

            @Override
            public float modifyDamageTaken(Combat c, float damage, String type, slaytherobotnik.core.Creature source) {
                return damage;
            }

            @Override
            public float modifyDamageDealt(Combat c, float damage, String type, slaytherobotnik.core.Creature target) {
                return type.equals(slaytherobotnik.core.DamageType.ATTACK) ? damage * (1f + amount() * 0.1f) : damage;
            }

            @Override
            public void atRoundEnd(Combat c) {
                setAmount(0);
            }

            @Override
            public String description() {
                return "Its attacks deal 10% more damage for each card you played this turn (" + amount() + ").";
            }
        };
    }
}
