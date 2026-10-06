package slaytherobotnik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import slaytherobotnik.core.Rng;
import slaytherobotnik.core.RoomType;
import slaytherobotnik.map.ActMap;
import slaytherobotnik.map.MapGenerator;
import slaytherobotnik.map.MapNode;

/** Slay the Spire's map rules hold for many seeds. */
class MapGeneratorTest {

    @Test
    void fixedFloorsAndPlacementRules() {
        for (long seed = 1; seed <= 300; seed++) {
            ActMap map = MapGenerator.generate(new Rng(seed));
            String drawing = "seed " + seed + "\n" + map.render();
            assertTrue(map.row(0).size() >= 2, drawing);
            for (MapNode n : map.usedNodes()) {
                String room = n.room();
                if (n.y() == 0) {
                    assertEquals(RoomType.MONSTER, room, drawing);
                } else if (n.y() == 8) {
                    assertEquals(RoomType.TREASURE, room, drawing);
                } else if (n.y() == ActMap.HEIGHT - 1) {
                    assertEquals(RoomType.REST, room, drawing);
                    assertTrue(n.connectsToBoss(), drawing);
                }
                if (n.y() < 5) {
                    assertFalse(room.equals(RoomType.ELITE) || room.equals(RoomType.REST), drawing);
                }
                if (n.y() == ActMap.HEIGHT - 2) {
                    assertFalse(room.equals(RoomType.REST), drawing);
                }
                if (n.y() < ActMap.HEIGHT - 1) {
                    assertFalse(n.children().isEmpty(), "dead end " + n + "\n" + drawing);
                }
                for (MapNode child : n.children()) {
                    assertEquals(n.y() + 1, child.y());
                    assertTrue(Math.abs(child.x() - n.x()) <= 1, drawing);
                }
                if (n.y() > 0) {
                    assertFalse(n.parents().isEmpty(), "unreachable " + n + "\n" + drawing);
                }
            }
        }
    }

    @Test
    void pathsNeverCross() {
        for (long seed = 1; seed <= 300; seed++) {
            ActMap map = MapGenerator.generate(new Rng(seed));
            for (int y = 0; y < ActMap.HEIGHT - 1; y++) {
                List<MapNode> row = map.row(y);
                for (MapNode a : row) {
                    for (MapNode b : row) {
                        if (a.x() >= b.x()) {
                            continue;
                        }
                        for (MapNode ac : a.children()) {
                            for (MapNode bc : b.children()) {
                                assertTrue(ac.x() <= bc.x(), "crossing at seed " + seed + "\n" + map.render());
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void pathsMeetAtTheTreasureAndTheLastStarpost() {
        int nodes = 0;
        int fights = 0;
        for (int seed = 0; seed < 200; seed++) {
            ActMap map = MapGenerator.generate(new Rng(seed));
            String drawing = "seed " + seed + "\n" + map.render();
            assertEquals(1, map.row(8).size(), "one treasure room\n" + drawing);
            assertEquals(1, map.row(ActMap.HEIGHT - 1).size(), "one Starpost before the boss\n" + drawing);
            assertEquals(ActMap.WIDTH / 2, map.row(8).get(0).x(), drawing);
            assertTrue(map.row(0).size() == 3, "three starts\n" + drawing);
            for (MapNode n : map.usedNodes()) {
                nodes++;
                if (n.room().equals(RoomType.MONSTER) || n.room().equals(RoomType.ELITE)) {
                    fights++;
                }
            }
        }
        // Six free paths gave 62 rooms (35 of them fights) a map over these seeds; three meeting paths
        // give about 34 (21 fights), so the map reads at a glance.
        assertTrue(nodes / 200.0 <= 36, "rooms per map " + nodes / 200.0);
        assertTrue(fights / 200.0 <= 23, "fights per map " + fights / 200.0);
    }

    @Test
    void sameSeedSameMap() {
        assertEquals(MapGenerator.generate(new Rng(42)).render(), MapGenerator.generate(new Rng(42)).render());
    }
}
