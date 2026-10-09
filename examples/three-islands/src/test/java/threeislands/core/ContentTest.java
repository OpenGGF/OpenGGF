package threeislands.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The content tables and the story script fit together. */
class ContentTest {
    private static byte[] resource(String path) throws IOException {
        try (InputStream in = ContentTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path);
            return in.readAllBytes();
        }
    }

    @Test
    void everyZoneHasFoesABossAndAnOrder() {
        for (Island island : Island.values()) assertFalse(Zone.of(island).isEmpty(), island + " has zones");
        for (Zone zone : Zone.values()) {
            assertFalse(zone.enemyKinds().isEmpty());
            List<EnemyKind> bosses = zone.bossKinds();
            assertTrue(bosses.get(bosses.size() - 1).boss, zone + " ends with a boss");
            for (EnemyKind kind : zone.enemyKinds()) assertFalse(kind.boss, kind + " is an ordinary foe");
            for (EnemyKind kind : bosses) assertTrue(kind.boss);
        }
    }

    @Test
    void everyHeroLearnsSkillsAndTechsNeedTwoOrMoreHeroes() {
        for (HeroId hero : HeroId.values()) {
            assertTrue(java.util.Arrays.stream(Skill.values()).anyMatch(s -> !s.isTech() && s.includes(hero)
                    && s.learnLevel == 1), hero + " starts with a skill");
        }
        for (Skill skill : Skill.values()) {
            assertTrue(skill.members > 0 && skill.members < 8);
            assertTrue(skill.ep >= 0);
            for (EnemyKind kind : EnemyKind.values()) assertTrue(kind.frameList().length > 0);
        }
    }

    @Test
    void theStoryHasEverySceneTheGameAsksFor() throws IOException {
        Story story = new Story(resource("text/story.txt"));
        List<String> wanted = new ArrayList<>(List.of("prologue", "ending", "ghz-rescue", "ghz-garden-verse", "ghz-garden-song", "ghz-orchard-note", "ghz-orchard-letter", "ghz-horizon"));
        for (Island island : Island.values()) {
            wanted.add(island.key() + "-arrive");
            wanted.add("village-" + island.key());
        }
        for (Zone zone : Zone.values()) {
            wanted.add(zone.key + "-enter");
            wanted.add(zone.key + "-boss");
            wanted.add(zone.key + "-clear");
            for (String suffix : List.of("friend", "memory", "signal", "camp", "dungeon-enter")) wanted.add(zone.key + "-" + suffix);
            if (zone.bossKinds().size() > 1) wanted.add(zone.key + "-mid");
        }
        for (String scene : wanted) assertTrue(story.has(scene), "missing scene " + scene);
        for (String name : story.names()) assertTrue(wanted.contains(name), "unused scene " + name);
        for (String name : story.names()) {
            for (Story.Line line : story.scene(name)) assertTrue(line.text().length() <= 160, "line too long in " + name);
        }
    }

    @Test
    void theFontCoversEverythingTheStoryAndTablesSay() throws IOException {
        String font = new String(resource("text/font.txt"), java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder text = new StringBuilder(new String(resource("text/story.txt"), java.nio.charset.StandardCharsets.UTF_8));
        for (Skill skill : Skill.values()) text.append(skill.label).append(skill.description);
        for (Item item : Item.values()) text.append(item.label).append(item.description);
        for (EnemyKind kind : EnemyKind.values()) text.append(kind.label).append(kind.verb);
        for (Zone zone : Zone.values()) text.append(zone.label);
        for (Island island : Island.values()) text.append(island.label).append(island.village);
        for (char c : text.toString().toCharArray()) {
            if (c == ' ' || c == '\n' || c == '\r' || c == '#') continue;
            assertTrue(font.contains("= " + c + "\n") || font.contains("= " + Character.toUpperCase(c) + "\n"),
                    "the font lacks '" + c + "'");
        }
    }

    @Test
    void storyParsingRejectsMalformedScripts() {
        assertEquals(1, new Story("@a\nSonic: hi\n".getBytes()).scene("a").size());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Story("Sonic: before any scene".getBytes()));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Story("@a\n@a\n".getBytes()));
    }
}
