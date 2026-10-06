package slaytherobotnik;

import java.util.ArrayList;
import java.util.List;
import slaytherobotnik.content.Content;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.Combat;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.core.RoomType;
import slaytherobotnik.core.RunState;

/**
 * Test helpers: a run with a chosen deck and relics, and a started combat whose hand holds
 * exactly the cards a test names.
 */
final class Harness {
    final Catalog catalog = Content.build();
    final RunState run;

    Harness(String character) {
        run = RunState.start(catalog, catalog.character(character), 12345L);
    }

    /** Replaces the deck with the given card ids (append "+" for upgraded). */
    Harness deck(String... ids) {
        run.deck().clear();
        for (String id : ids) {
            boolean up = id.endsWith("+");
            run.deck().add(new Card(catalog.card(up ? id.substring(0, id.length() - 1) : id), up));
        }
        return this;
    }

    Harness noRelics() {
        run.relics().clear();
        return this;
    }

    Harness relic(String id) {
        run.obtainRelic(id);
        return this;
    }

    /** Starts a fight against the enemies; the opening hand is drawn from the deck as usual. */
    Combat fight(Enemy... enemies) {
        Combat c = new Combat(run, new ArrayList<>(List.of(enemies)), RoomType.MONSTER);
        c.start();
        return c;
    }

    /** Puts a fresh card into the hand (bypassing draw) and returns it. */
    static Card give(Combat c, String id) {
        Card card = new Card(c.catalog().card(id));
        c.player().hand().add(card);
        return card;
    }

    static Card giveUpgraded(Combat c, String id) {
        Card card = new Card(c.catalog().card(id), true);
        c.player().hand().add(card);
        return card;
    }

    /** Removes every card from hand and piles. */
    static void clearCards(Combat c) {
        c.player().hand().clear();
        c.player().drawPile().clear();
        c.player().discardPile().clear();
    }
}
