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
import slaytherobotnik.core.RoomType;

/**
 * The final act: Sky Sanctuary. Like Slay the Spire's act 4 it is a short fixed path, a
 * Starpost, the Egg Robo's shop, an elite pair of Egg Robos, then Mecha Sonic. When Mecha
 * Sonic falls it seizes the Master Emerald and returns as Super Mecha Sonic, whose shield
 * caps the damage it takes each turn (the Corrupt Heart's Invincible).
 */
public final class SkySanctuary {
    public static final int ACT = 4;

    private SkySanctuary() {
    }

    public static void register(Catalog c) {
        c.addAct(new ActDef(ACT, "Final Act", "Sky Sanctuary",
                10, 0,              // ROM zone and act: Sky Sanctuary act 1
                0x15, 0x30, 0x18,   // music for the map (SSZ), Mecha Sonic (final boss theme), the Egg Robos (miniboss)
                "The ancient sanctuary above the clouds. Mecha Sonic waits at the summit.",
                List.of(RoomType.REST, RoomType.SHOP, RoomType.ELITE)));

        c.addEncounter(new EncounterDef("ssz:egg_robo_squad", "Egg Robo Squad", ACT, EncounterDef.ELITE, 1,
                hp -> List.of(new EggRobo(hp, true), new EggRobo(hp, false))));
        c.addEncounter(new EncounterDef("ssz:mecha_sonic", "Mecha Sonic", ACT, EncounterDef.BOSS, 1,
                hp -> List.of(new MechaSonic())));
    }

    /** Spire Shield and Spear: one guards and shields its partner, the other fires a laser that grows. */
    public static final class EggRobo extends Enemy {
        private final boolean shield;
        private int laser = 5;

        public EggRobo(Rng hp, boolean shield) {
            super(shield ? "ssz:egg_robo_guard" : "ssz:egg_robo_gunner", shield ? "Egg Robo (Guard)" : "Egg Robo (Gunner)",
                    shield ? 110 : 160);
            this.shield = shield;
            setArt("ssz:egg_robo");
        }

        @Override
        protected void onSpawn(Combat c) {
            c.applyPower(this, this, Powers.artifact(shield ? 1 : 2));
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (shield) {
                setMove(turnsTaken() % 3 == 2 ? Move.attack("smash", "Shield Bash", 34)
                        : turnsTaken() % 3 == 0 ? Move.of("fortify", "Fortify", IntentKind.DEFEND)
                        : Move.of("bash", "Bash", IntentKind.ATTACK_DEBUFF, 12, 1));
            } else {
                setMove(turnsTaken() % 3 == 2 ? Move.of("brace", "Recalibrate", IntentKind.BUFF)
                        : Move.multiAttack("laser", "Laser Volley", laser, turnsTaken() % 3 == 0 ? 2 : 3));
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            switch (move.id()) {
                case "fortify" -> {
                    for (Enemy e : c.activeEnemies()) {
                        c.gainBlock(e, 30, null);
                    }
                }
                case "bash" -> {
                    attackPlayer(c, move);
                    c.applyPower(this, c.player(), Powers.strength(-1));
                }
                case "brace" -> {
                    c.applyPower(this, this, Powers.strength(2));
                    laser += 2;
                    c.addCreatedCard(new Card(c.catalog().card(CommonCards.BURN)), "draw");
                }
                default -> attackPlayer(c, move);
            }
        }
    }

    /** The final boss, in two phases. */
    public static final class MechaSonic extends Enemy {
        private boolean superForm;
        private final Move spinDash = Move.attack("spin", "Spin Dash", 30);
        private final Move homing = Move.multiAttack("homing", "Homing Spikes", 3, 6);
        private final Move charge = Move.of("charge", "Charge Up", IntentKind.BUFF);
        private final Move transform = Move.of("transform", "Master Emerald", IntentKind.UNKNOWN);

        public MechaSonic() {
            super("ssz:mecha_sonic", "Mecha Sonic", 400);
        }

        @Override
        protected void onSpawn(Combat c) {
            c.applyPower(this, this, Powers.artifact(2));
            c.applyPower(this, this, overdrive());
        }

        @Override
        protected void onDeath(Combat c) {
            if (!superForm) {
                superForm = true;
                setHalfDead(true);
                reviveTo(1);
                for (Power p : powers()) {
                    c.removePower(this, p.id());
                }
                setMove(transform);
                c.events().add(new CombatEvent.Cue(this, "master_emerald"));
                c.events().add(new CombatEvent.Message("Mecha Sonic seizes the Master Emerald!"));
            }
        }

        @Override
        protected void chooseMove(Combat c, Rng ai) {
            if (halfDead()) {
                setMove(transform);
                return;
            }
            int phase = turnsTaken() % 3;
            if (superForm) {
                setMove(switch (phase) {
                    case 0 -> Move.multiAttack("super_homing", "Super Homing", 2, 12);
                    case 1 -> Move.attack("super_spin", "Super Spin Dash", 45);
                    default -> Move.of("glare", "Chaos Glare", IntentKind.STRONG_DEBUFF);
                });
            } else {
                setMove(phase == 0 ? homing : phase == 1 ? spinDash : charge);
            }
        }

        @Override
        protected void perform(Combat c, Move move) {
            switch (move.id()) {
                case "transform" -> {
                    setHalfDead(false);
                    reviveTo(400);
                    c.events().add(new CombatEvent.Healed(this, 400));
                    c.applyPower(this, this, Powers.strength(2));
                    c.applyPower(this, this, invincible(200));
                    setArt("ssz:super_mecha_sonic");
                }
                case "charge" -> {
                    c.applyPower(this, this, Powers.strength(2));
                    c.gainBlock(this, 20, null);
                }
                case "glare" -> {
                    c.applyPower(this, c.player(), Powers.vulnerable(2));
                    c.applyPower(this, c.player(), Powers.weak(2));
                    c.applyPower(this, c.player(), Powers.frail(2));
                    for (int i = 0; i < 2; i++) {
                        c.addCreatedCard(new Card(c.catalog().card(CommonCards.DIZZY)), "draw");
                    }
                }
                default -> attackPlayer(c, move);
            }
        }

        /** Beat of Death: each card played costs the player 1 HP. */
        private Power overdrive() {
            return new Power("ssz:overdrive", "Overdrive Engine", Power.BUFF, 1) {
                @Override
                public void onPlayerCardPlayed(Combat c, Card card) {
                    c.dealDamage(owner(), c.player(), amount(), slaytherobotnik.core.DamageType.THORNS);
                }

                @Override
                public String description() {
                    return "Whenever you play a card, you take " + amount() + " damage.";
                }
            };
        }

        /** Invincible: at most {@code cap} HP lost per round. */
        private Power invincible(int cap) {
            return new Power("ssz:invincible", "Super Shield", Power.BUFF, cap) {
                private int lostThisRound;

                @Override
                public int modifyHpLoss(Combat c, int hpLoss, Creature source, String type) {
                    int allowed = Math.max(0, amount() - lostThisRound);
                    int loss = Math.min(hpLoss, allowed);
                    lostThisRound += loss;
                    return loss;
                }

                @Override
                public void atRoundEnd(Combat c) {
                    lostThisRound = 0;
                }

                @Override
                public String description() {
                    return "Loses at most " + amount() + " HP each turn (" + Math.max(0, amount() - lostThisRound)
                            + " left this turn).";
                }
            };
        }
    }
}
