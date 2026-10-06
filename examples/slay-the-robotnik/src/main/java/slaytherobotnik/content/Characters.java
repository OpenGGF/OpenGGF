package slaytherobotnik.content;

import java.util.List;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.CharacterDef;

/** The three playable characters. */
public final class Characters {
    public static final String SONIC = "sonic";
    public static final String TAILS = "tails";
    public static final String KNUCKLES = "knuckles";

    private Characters() {
    }

    public static void register(Catalog c) {
        c.addCharacter(new CharacterDef(SONIC, "Sonic", SonicCards.COLOR, 72, 99,
                List.of(SonicCards.SPIN_ATTACK, SonicCards.SPIN_ATTACK, SonicCards.SPIN_ATTACK, SonicCards.SPIN_ATTACK,
                        SonicCards.SIDE_STEP, SonicCards.SIDE_STEP, SonicCards.SIDE_STEP, SonicCards.SIDE_STEP,
                        SonicCards.SPIN_DASH, SonicCards.HOMING_ATTACK),
                Relics.RED_SNEAKERS, "Focus",
                "The fastest thing alive. Chains Combo attacks, stacks Vulnerable and burns through his deck."));
        c.addCharacter(new CharacterDef(TAILS, "Tails", TailsCards.COLOR, 70, 99,
                List.of(TailsCards.TAIL_SWIPE, TailsCards.TAIL_SWIPE, TailsCards.TAIL_SWIPE, TailsCards.TAIL_SWIPE,
                        TailsCards.TAIL_SWIPE, TailsCards.TAIL_GUARD, TailsCards.TAIL_GUARD, TailsCards.TAIL_GUARD,
                        TailsCards.TAIL_GUARD, TailsCards.TAIL_GUARD, TailsCards.TAIL_FLICK, TailsCards.TINKER),
                Relics.TINKER_KIT, "Dexterity",
                "Genius inventor. Discards to fuel gadgets, throws Ring Bombs and out-blocks anything."));
        c.addCharacter(new CharacterDef(KNUCKLES, "Knuckles", KnucklesCards.COLOR, 80, 99,
                List.of(KnucklesCards.PUNCH, KnucklesCards.PUNCH, KnucklesCards.PUNCH, KnucklesCards.PUNCH,
                        KnucklesCards.PUNCH, KnucklesCards.GUARD, KnucklesCards.GUARD, KnucklesCards.GUARD,
                        KnucklesCards.GUARD, KnucklesCards.HAMMER_PUNCH),
                Relics.MASTER_EMERALD_SHARD, "Strength",
                "Guardian of the Master Emerald. Builds Strength and ends fights with enormous blows."));
    }
}
