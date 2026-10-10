package starpost.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Skills: levels from experience, professions branching at 5 and 10, prices, and saves. */
class SkillsTest {
    private final Catalog catalog = new Catalog();

    @Test
    void levelsFollowTheThresholdsAndAreAnnouncedOnce() {
        Skills skills = new Skills();
        skills.add(Skills.FARMING, 99);
        assertEquals(0, skills.level(Skills.FARMING));
        skills.add(Skills.FARMING, 1);
        assertEquals(1, skills.level(Skills.FARMING));
        skills.add(Skills.FARMING, 2050);
        assertEquals(5, skills.level(Skills.FARMING));
        assertEquals(5, skills.pendingLevels().size(), "levels 1 to 5 to announce");
        skills.announce(Skills.FARMING, 5, "ringgrower");
        assertTrue(skills.pendingLevels().isEmpty());
        assertArrayEquals(new String[] {"supergrower", "artisan"}, skills.choices(Skills.FARMING, 10),
                "the level-10 pair follows the level-5 choice");
    }

    @Test
    void professionsChangePricesOnlyForTheirKind() {
        Game game = new Game(catalog, 1);
        Skills skills = new Skills();
        game.sections.add(skills);
        Item radish = catalog.item("ring_radish"), leek = catalog.item("totem_leek");
        assertEquals(radish.price(), game.sellPrice(radish));
        skills.add(Skills.FARMING, 3000);
        skills.announce(Skills.FARMING, 5, "ringgrower");
        assertEquals(Math.round(radish.price() * 1.1f), game.sellPrice(radish));
        assertEquals(leek.price(), game.sellPrice(leek));
    }

    @Test
    void savesRoundTripAndRejectInventedProfessions() {
        Skills skills = new Skills();
        skills.add(Skills.BOPPING, 2500);
        skills.announce(Skills.BOPPING, 5, "drop_dash");
        Map<String, String> out = new HashMap<>();
        skills.save(out);
        out.put("professions", out.get("professions") + ",god_mode");
        out.put("xp.1", "99999999,10");
        Skills loaded = new Skills();
        loaded.load(out, catalog);
        assertEquals(skills.xp(Skills.BOPPING), loaded.xp(Skills.BOPPING));
        assertTrue(loaded.has("drop_dash"));
        assertFalse(loaded.has("god_mode"));
        assertEquals(10, loaded.level(Skills.RANGING), "clamped to the cap, still level 10");
    }
}
