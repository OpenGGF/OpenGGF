package slaytherobotnik.content;

import slaytherobotnik.core.Catalog;

/**
 * Builds the complete catalog: every card, relic, potion, character, act, enemy and event.
 * Adding content means writing it in one of these classes and registering it here.
 */
public final class Content {
    private Content() {
    }

    public static Catalog build() {
        Catalog c = new Catalog();
        CommonCards.register(c);
        SonicCards.register(c);
        TailsCards.register(c);
        KnucklesCards.register(c);
        Relics.register(c);
        Potions.register(c);
        Characters.register(c);
        AngelIsland.register(c);
        Hydrocity.register(c);
        LaunchBase.register(c);
        SkySanctuary.register(c);
        Events.register(c);
        return c;
    }
}
