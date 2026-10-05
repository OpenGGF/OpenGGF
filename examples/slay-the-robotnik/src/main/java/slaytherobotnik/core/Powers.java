package slaytherobotnik.core;

/**
 * The general powers every character and enemy can use. Character-specific powers live
 * with their cards in the content package.
 *
 * <p>Each factory returns a new instance, because a power belongs to one creature.
 */
public final class Powers {
    public static final String STRENGTH = "Strength";
    public static final String DEXTERITY = "Dexterity";
    public static final String FOCUS = "Focus";
    public static final String VULNERABLE = "Vulnerable";
    public static final String WEAK = "Weak";
    public static final String FRAIL = "Frail";
    public static final String ARTIFACT = "Artifact";
    public static final String THORNS = "Thorns";
    public static final String METALLICIZE = "Metallicize";
    public static final String PLATED_ARMOR = "PlatedArmor";
    public static final String REGEN = "Regen";
    public static final String INTANGIBLE = "Intangible";
    public static final String BARRICADE = "Barricade";
    public static final String RITUAL = "Ritual";
    public static final String ENERGIZED = "Energized";
    public static final String DRAW_NEXT_TURN = "DrawNextTurn";
    public static final String BLOCK_NEXT_TURN = "BlockNextTurn";
    public static final String NO_DRAW = "NoDraw";
    public static final String ENTANGLED = "Entangled";
    public static final String STRENGTH_DOWN_AT_END = "StrengthDownAtEnd";
    public static final String DEXTERITY_DOWN_AT_END = "DexterityDownAtEnd";

    private Powers() {
    }

    /** +1 damage per stack on every hit of every Attack. */
    public static Power strength(int amount) {
        return new Power(STRENGTH, "Strength", Power.BUFF, amount) {
            {
                allowNegative();
            }

            @Override
            public float modifyDamageDealt(Combat c, float damage, String type, Creature target) {
                return type.equals(DamageType.ATTACK) ? damage + amount() : damage;
            }

            @Override
            public String description() {
                return amount() >= 0
                        ? "Increases attack damage by " + amount() + "."
                        : "Decreases attack damage by " + -amount() + ".";
            }
        };
    }

    /** +1 Block per stack on every Block gain from cards. */
    public static Power dexterity(int amount) {
        return new Power(DEXTERITY, "Dexterity", Power.BUFF, amount) {
            {
                allowNegative();
            }

            @Override
            public float modifyBlock(Combat c, float block, Card card) {
                return block + amount();
            }

            @Override
            public String description() {
                return amount() >= 0
                        ? "Increases Block gained from cards by " + amount() + "."
                        : "Decreases Block gained from cards by " + -amount() + ".";
            }
        };
    }

    /** +1 repetition per stack on every Combo effect (Sonic's primary stat). */
    public static Power focus(int amount) {
        return new Power(FOCUS, "Focus", Power.BUFF, amount) {
            {
                allowNegative();
            }

            @Override
            public int modifyCombo(Combat c, Card card, int count) {
                return count + amount();
            }

            @Override
            public String description() {
                return amount() >= 0
                        ? "Combo effects repeat " + amount() + " more time" + (amount() == 1 ? "." : "s.")
                        : "Combo effects repeat " + -amount() + " fewer time" + (amount() == -1 ? "." : "s.");
            }
        };
    }

    /** Takes 50% more attack damage. */
    public static Power vulnerable(int turns) {
        return new Power(VULNERABLE, "Vulnerable", Power.DEBUFF, turns) {
            {
                turnBasedDuration();
            }

            @Override
            public int priority() {
                return 99;
            }

            @Override
            public float modifyDamageTaken(Combat c, float damage, String type, Creature source) {
                return type.equals(DamageType.ATTACK) ? damage * c.vulnerableMultiplier(owner()) : damage;
            }

            @Override
            public String description() {
                return "Takes 50% more damage from attacks for " + turns(amount()) + ".";
            }
        };
    }

    /** Deals 25% less attack damage. */
    public static Power weak(int turns) {
        return new Power(WEAK, "Weak", Power.DEBUFF, turns) {
            {
                turnBasedDuration();
            }

            @Override
            public int priority() {
                return 99;
            }

            @Override
            public float modifyDamageDealt(Combat c, float damage, String type, Creature target) {
                return type.equals(DamageType.ATTACK) ? damage * c.weakMultiplier(owner()) : damage;
            }

            @Override
            public String description() {
                return "Deals 25% less damage with attacks for " + turns(amount()) + ".";
            }
        };
    }

    /** Gains 25% less Block from cards. */
    public static Power frail(int turns) {
        return new Power(FRAIL, "Frail", Power.DEBUFF, turns) {
            {
                turnBasedDuration();
            }

            @Override
            public int priority() {
                return 99;
            }

            @Override
            public float modifyBlock(Combat c, float block, Card card) {
                return block * 0.75f;
            }

            @Override
            public String description() {
                return "Gains 25% less Block from cards for " + turns(amount()) + ".";
            }
        };
    }

    /** Negates the next debuff(s). */
    public static Power artifact(int amount) {
        return new Power(ARTIFACT, "Artifact", Power.BUFF, amount) {
            @Override
            public String description() {
                return "Negates the next " + plural(amount(), "debuff") + ".";
            }
        };
    }

    /** Attackers take damage back. */
    public static Power thorns(int amount) {
        return new Power(THORNS, "Thorns", Power.BUFF, amount) {
            @Override
            public void onAttacked(Combat c, Creature source, int damage, int hpLost, String type) {
                if (source != null && source != owner() && type.equals(DamageType.ATTACK)) {
                    c.dealDamage(owner(), source, amount(), DamageType.THORNS);
                }
            }

            @Override
            public String description() {
                return "When attacked, deals " + amount() + " damage back.";
            }
        };
    }

    /** Gains Block at the end of each of its turns. */
    public static Power metallicize(int amount) {
        return new Power(METALLICIZE, "Metallicize", Power.BUFF, amount) {
            @Override
            public void atTurnEnd(Combat c) {
                c.gainBlock(owner(), amount(), null);
            }

            @Override
            public String description() {
                return "At the end of its turn, gains " + amount() + " Block.";
            }
        };
    }

    /** Gains Block at the end of each turn; loses 1 stack whenever damaged. */
    public static Power platedArmor(int amount) {
        return new Power(PLATED_ARMOR, "Plated Armor", Power.BUFF, amount) {
            @Override
            public void atTurnEnd(Combat c) {
                c.gainBlock(owner(), amount(), null);
            }

            @Override
            public void onAttacked(Combat c, Creature source, int damage, int hpLost, String type) {
                if (hpLost > 0 && !type.equals(DamageType.HP_LOSS)) {
                    c.reducePower(owner(), PLATED_ARMOR, 1);
                }
            }

            @Override
            public String description() {
                return "At the end of its turn, gains " + amount()
                        + " Block. Receiving unblocked attack damage reduces this by 1.";
            }
        };
    }

    /** Heals at the end of each turn, then decreases by 1. */
    public static Power regen(int amount) {
        return new Power(REGEN, "Regen", Power.BUFF, amount) {
            @Override
            public void atTurnEnd(Combat c) {
                c.heal(owner(), amount());
                c.reducePower(owner(), REGEN, 1);
            }

            @Override
            public String description() {
                return "At the end of its turn, heals " + amount() + " HP and Regen decreases by 1.";
            }
        };
    }

    /** All damage and HP loss is reduced to 1. */
    public static Power intangible(int turns) {
        return new Power(INTANGIBLE, "Intangible", Power.BUFF, turns) {
            @Override
            public int modifyFinalDamageTaken(Combat c, int damage, String type, Creature source) {
                return Math.min(damage, 1);
            }

            @Override
            public void atTurnStart(Combat c) {
                // Lasts through the opponent's turn: it wears off when the owner's next turn starts.
                c.reducePower(owner(), INTANGIBLE, 1);
            }

            @Override
            public String description() {
                return "Reduces all damage taken to 1 until the start of its next turn.";
            }
        };
    }

    /** Block is not removed at the start of the turn. */
    public static Power barricade() {
        return new Power(BARRICADE, "Barricade", Power.BUFF, 1) {
            {
                nonStacking();
            }

            @Override
            public boolean retainsBlock(Combat c) {
                return true;
            }

            @Override
            public String description() {
                return "Block is not removed at the start of the turn.";
            }
        };
    }

    /** Gains Strength at the end of each of its turns. */
    public static Power ritual(int amount) {
        return new Power(RITUAL, "Ritual", Power.BUFF, amount) {
            @Override
            public void atTurnEnd(Combat c) {
                c.applyPower(owner(), owner(), strength(amount()));
            }

            @Override
            public String description() {
                return "At the end of its turn, gains " + amount() + " Strength.";
            }
        };
    }

    /** Extra Energy next turn. */
    public static Power energizedNextTurn(int amount) {
        return new Power(ENERGIZED, "Energized", Power.BUFF, amount) {
            @Override
            public void atTurnStart(Combat c) {
                c.gainEnergy(amount());
                c.removePower(owner(), ENERGIZED);
            }

            @Override
            public String description() {
                return "Gain " + amount() + " Energy next turn.";
            }
        };
    }

    /** Extra cards next turn. */
    public static Power drawNextTurn(int amount) {
        return new Power(DRAW_NEXT_TURN, "Draw Card", Power.BUFF, amount) {
            @Override
            public void atTurnStartPostDraw(Combat c) {
                c.draw(amount());
                c.removePower(owner(), DRAW_NEXT_TURN);
            }

            @Override
            public String description() {
                return "Draw " + plural(amount(), "additional card") + " next turn.";
            }
        };
    }

    /** Block at the start of next turn. */
    public static Power blockNextTurn(int amount) {
        return new Power(BLOCK_NEXT_TURN, "Next Turn Block", Power.BUFF, amount) {
            @Override
            public void atTurnStartPostDraw(Combat c) {
                c.gainBlock(owner(), amount(), null);
                c.removePower(owner(), BLOCK_NEXT_TURN);
            }

            @Override
            public String description() {
                return "Gain " + amount() + " Block next turn.";
            }
        };
    }

    /** Cannot draw more cards this turn. */
    public static Power noDraw() {
        return new Power(NO_DRAW, "No Draw", Power.DEBUFF, 1) {
            {
                nonStacking();
            }

            @Override
            public void atTurnEnd(Combat c) {
                c.removePower(owner(), NO_DRAW);
            }

            @Override
            public String description() {
                return "You may not draw any more cards this turn.";
            }
        };
    }

    /** Cannot play Attacks this turn. */
    public static Power entangled() {
        return new Power(ENTANGLED, "Entangled", Power.DEBUFF, 1) {
            {
                nonStacking();
            }

            @Override
            public String vetoCardPlay(Combat c, Card card) {
                return card.type().equals(CardType.ATTACK) ? "You are Entangled!" : null;
            }

            @Override
            public void atTurnEnd(Combat c) {
                c.removePower(owner(), ENTANGLED);
            }

            @Override
            public String description() {
                return "You may not play Attacks this turn.";
            }
        };
    }

    /** Temporary Strength: lose it again at the end of this turn (Flex). */
    public static Power loseStrengthAtEnd(int amount) {
        return new Power(STRENGTH_DOWN_AT_END, "Strength Down", Power.DEBUFF, amount) {
            @Override
            public void atTurnEnd(Combat c) {
                c.applyPower(owner(), owner(), strength(-amount()));
                c.removePower(owner(), STRENGTH_DOWN_AT_END);
            }

            @Override
            public String description() {
                return "At the end of your turn, lose " + amount() + " Strength.";
            }
        };
    }

    /** Temporary Dexterity: lose it again at the end of this turn. */
    public static Power loseDexterityAtEnd(int amount) {
        return new Power(DEXTERITY_DOWN_AT_END, "Dexterity Down", Power.DEBUFF, amount) {
            @Override
            public void atTurnEnd(Combat c) {
                c.applyPower(owner(), owner(), dexterity(-amount()));
                c.removePower(owner(), DEXTERITY_DOWN_AT_END);
            }

            @Override
            public String description() {
                return "At the end of your turn, lose " + amount() + " Dexterity.";
            }
        };
    }

    static String turns(int n) {
        return n == 1 ? "1 turn" : n + " turns";
    }

    static String plural(int n, String noun) {
        return n == 1 ? "1 " + noun : n + " " + noun + "s";
    }
}
