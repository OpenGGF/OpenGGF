package slaytherobotnik.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The player inside a combat: HP and Block (shared with {@link Creature}) plus Energy and
 * the four card piles. The draw pile's top card is the last element of {@link #drawPile()}.
 */
public final class Player extends Creature {
    public static final int MAX_HAND = 10;

    private final CharacterDef character;
    private int energy;
    private int energyPerTurn;
    private final List<Card> hand = new ArrayList<>();
    private final List<Card> drawPile = new ArrayList<>();
    private final List<Card> discardPile = new ArrayList<>();
    private final List<Card> exhaustPile = new ArrayList<>();

    Player(CharacterDef character, int hp, int maxHp, int energyPerTurn) {
        super(character.name(), hp, maxHp);
        this.character = character;
        this.energyPerTurn = energyPerTurn;
    }

    @Override
    public boolean isPlayer() {
        return true;
    }

    public CharacterDef character() { return character; }
    public int energy() { return energy; }
    public int energyPerTurn() { return energyPerTurn; }
    public List<Card> hand() { return hand; }
    public List<Card> drawPile() { return drawPile; }
    public List<Card> discardPile() { return discardPile; }
    public List<Card> exhaustPile() { return exhaustPile; }

    void setEnergy(int value) {
        energy = Math.max(0, value);
    }

    void addEnergyPerTurn(int delta) {
        energyPerTurn += delta;
    }

    public int strength() { return amount(Powers.STRENGTH); }
    public int dexterity() { return amount(Powers.DEXTERITY); }
    public int focus() { return amount(Powers.FOCUS); }
}
