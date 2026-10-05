package slaytherobotnik.core;

import java.util.List;

/**
 * A playable character: starting stats, deck and relic, and the colour of the card pool.
 * {@code color} is a {@link CardColor} constant.
 */
public record CharacterDef(
        String id,
        String name,
        String color,
        int maxHp,
        int startingRings,
        List<String> startingDeck,
        String startingRelic,
        String primaryStat,
        String blurb) {

    public static final int ENERGY_PER_TURN = 3;
}
