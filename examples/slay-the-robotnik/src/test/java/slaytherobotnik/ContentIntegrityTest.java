package slaytherobotnik;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import slaytherobotnik.content.Content;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.CardDef;
import slaytherobotnik.core.Cards;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.CharacterDef;
import slaytherobotnik.core.Combat;
import slaytherobotnik.core.EncounterDef;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.core.PotionDef;
import slaytherobotnik.core.Relic;
import slaytherobotnik.core.Rng;
import slaytherobotnik.core.RoomType;
import slaytherobotnik.core.RunState;

/** Every piece of content is well formed: texts resolve, relics describe themselves, enemies act. */
class ContentIntegrityTest {
    private final Catalog catalog = Content.build();

    @Test
    void cardTextsHaveNoUnresolvedPlaceholders() {
        for (CardDef def : catalog.allCards()) {
            for (boolean up : new boolean[] {false, true}) {
                String text = Cards.plainText(new Card(def, up));
                assertFalse(text.contains("{") || text.contains("}") || text.contains("*"),
                        def.id() + " -> " + text);
                assertFalse(text.isBlank(), def.id());
            }
        }
    }

    @Test
    void upgradesChangeSomething() {
        for (CardDef def : catalog.allCards()) {
            if (!def.upgradable() || def.type().equals("Status") || def.type().equals("Curse")) {
                continue;
            }
            boolean changes = def.cost(true) != def.cost(false) || def.damage(true) != def.damage(false)
                    || def.block(true) != def.block(false) || def.magic(true) != def.magic(false)
                    || def.combo(true) != def.combo(false) || !def.keywords(true).equals(def.keywords(false))
                    || !def.text(true).equals(def.text(false));
            assertTrue(changes, def.id() + " upgrade does nothing");
        }
    }

    @Test
    void relicsAndPotionsDescribeThemselves() {
        for (Catalog.RelicEntry entry : catalog.allRelics()) {
            Relic relic = entry.factory().get();
            assertFalse(relic.description().isBlank(), relic.id());
        }
        for (PotionDef potion : catalog.allPotions()) {
            assertFalse(potion.describe(potion.potency()).contains("{"), potion.id());
        }
    }

    @Test
    void charactersHaveValidStartingKits() {
        for (CharacterDef character : catalog.characters()) {
            assertTrue(catalog.hasRelic(character.startingRelic()), character.id());
            for (String id : character.startingDeck()) {
                assertTrue(catalog.hasCard(id), id);
            }
        }
    }

    @Test
    void everyEncounterSpawnsAndPicksAnIntent() {
        for (int act = 1; act <= catalog.actCount(); act++) {
            for (String pool : new String[] {EncounterDef.WEAK, EncounterDef.STRONG, EncounterDef.ELITE,
                    EncounterDef.BOSS}) {
                for (EncounterDef encounter : catalog.encounters(act, pool)) {
                    RunState run = RunState.start(catalog, catalog.characters().get(0), 7L);
                    var enemies = new ArrayList<>(encounter.spawner().spawn(new Rng(1)));
                    assertFalse(enemies.isEmpty(), encounter.id());
                    Combat combat = new Combat(run, enemies, RoomType.MONSTER);
                    combat.start();
                    for (Enemy e : combat.activeEnemies()) {
                        assertTrue(e.hp() > 0, encounter.id());
                        assertNotNull(e.nextMove(), encounter.id() + " " + e.id());
                    }
                }
            }
        }
    }
}
