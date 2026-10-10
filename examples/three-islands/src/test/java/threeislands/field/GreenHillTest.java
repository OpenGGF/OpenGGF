package threeislands.field;

import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;
import threeislands.core.*;
import static org.junit.jupiter.api.Assertions.*;

class GreenHillTest {
    private Field.Spot spot(Field field, String id) {
        return field.spots.stream().filter(s -> s.id.equals(id)).findFirst().orElseThrow();
    }
    private void turn(Field field, Progress progress, String id) {
        field.mechanism(progress, spot(field, id));
    }
    private boolean reachable(Field field, int tx, int ty) {
        boolean[][] visited = new boolean[field.width() / 8][field.height() / 8];
        var queue = new ArrayDeque<int[]>();
        queue.add(new int[] {11, 42}); visited[11][42] = true;
        while (!queue.isEmpty()) {
            var p = queue.removeFirst();
            if (p[0] == tx / 8 && p[1] == ty / 8) return true;
            for (var d : new int[][] {{0,1},{0,-1},{1,0},{-1,0}}) {
                int x = p[0] + d[0], y = p[1] + d[1];
                if (x < 0 || y < 0 || x >= visited.length || y >= visited[0].length || visited[x][y]
                        || !field.walkable(x * 8, y * 8)) continue;
                visited[x][y] = true; queue.add(new int[] {x,y});
            }
        }
        return false;
    }
    private void beatBlockers(Field field, Progress progress) {
        for (var spot : field.spots) if (spot.blocking) field.complete(progress, spot);
    }
    private boolean reachable(Field field, Field.Spot spot) {
        return reachable(field, (int) spot.homeX, (int) spot.homeY);
    }
    @Test void bothSluicesAreNeededAndEitherOrderSurvivesReload() {
        for (boolean reverse : new boolean[] {false, true}) {
            Field field = new Field(Zone.GREEN_HILL, null);
            Progress progress = new Progress(7);
            beatBlockers(field, progress);
            var letter = spot(field, "orchard-letter");
            assertFalse(reachable(field, letter));
            assertFalse(reachable(field, field.exitX(), 336), "the anchor seals onward travel");
            assertTrue(reachable(field, spot(field, "sluice-west")));
            assertTrue(reachable(field, spot(field, "sluice-east")));
            turn(field, progress, reverse ? "sluice-east" : "sluice-west");
            assertFalse(reachable(field, letter));
            progress = SaveCodec.decode(SaveCodec.encode(progress));
            field = new Field(Zone.GREEN_HILL, null); field.restore(progress);
            turn(field, progress, reverse ? "sluice-west" : "sluice-east");
            assertTrue(reachable(field, letter));
            field.setPosition(letter.homeX, letter.homeY);
            assertEquals("orchard-letter", field.nearby(0).id);
            assertTrue(reachable(field, 144, 360), "crossing is reversible");
            Field restored = new Field(Zone.GREEN_HILL, null);
            restored.restore(SaveCodec.decode(SaveCodec.encode(progress)));
            assertTrue(restored.orchardOpen());
            assertFalse(restored.water(51 * 32 + 16, 29 * 32 + 16), "rendered crossing agrees with collision");
        }
    }
    @Test void wrongBellPhraseCanBeRetriedAndTheRewardIsOnlyGivenOnce() {
        Field field = new Field(Zone.GREEN_HILL, null);
        Progress progress = new Progress(7);
        int before = progress.count(Item.LIGHTNING_SHIELD);
        turn(field, progress, "bell-dusk");
        turn(field, progress, "bell-noon");
        assertFalse(field.bellsOpen());
        assertEquals(before, progress.count(Item.LIGHTNING_SHIELD));
        for (String id : new String[] {"bell-dusk", "bell-dawn", "bell-noon"}) turn(field, progress, id);
        assertTrue(field.bellsOpen());
        assertEquals(before + 1, progress.count(Item.LIGHTNING_SHIELD));
        assertTrue(progress.seen("ghz-garden-song"));
        progress = SaveCodec.decode(SaveCodec.encode(progress));
        Field restored = new Field(Zone.GREEN_HILL, null); restored.restore(progress);
        for (String id : new String[] {"bell-dusk", "bell-dawn", "bell-noon"}) turn(restored, progress, id);
        assertEquals(before + 1, progress.count(Item.LIGHTNING_SHIELD));
        assertTrue(restored.bellsOpen());
        assertFalse(restored.bossReady(), "garden puzzles do not substitute for the anchor relay");
        assertEquals(0, restored.discoveries(), "puzzles do not alter chapter discovery catch-up levels");
    }
    @Test void coastAndAuthoredExpeditionsRetainReachableLandmarks() {
        Field field = new Field(Zone.GREEN_HILL, null);
        assertTrue(field.width() * field.height() > Field.WIDTH * Field.HEIGHT * 2);
        beatBlockers(field, new Progress(1));
        assertTrue(reachable(field, spot(field, "horizon")));
        assertTrue(reachable(field, spot(field, "bell-noon")));
        assertTrue(reachable(field, spot(field, "cache-c")));
        for (Zone zone : Zone.values()) {
            Field shrine = new Field(zone, null, Dungeon.of(zone));
            assertEquals(shrine.layout.width, shrine.width());
            assertEquals(shrine.layout.height, shrine.height());
            if (zone != Zone.GREEN_HILL) assertTrue(new Field(zone, null).width() > Field.WIDTH);
        }
    }
}
