package threeislands.field;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;
import threeislands.core.Progress;
import threeislands.core.SaveCodec;
import threeislands.core.Zone;

class DungeonTest {
    private static boolean reachable(Field f, double targetX, double targetY) {
        boolean[][] seen = new boolean[f.width() / 8 + 1][f.height() / 8 + 1];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {11,42}); seen[11][42] = true;
        while (!queue.isEmpty()) {
            int[] p = queue.removeFirst();
            if (Math.hypot(p[0] * 8 - targetX, p[1] * 8 - targetY) <= 8) return true;
            for (int[] d : new int[][] {{1,0},{-1,0},{0,1},{0,-1}}) {
                int x = p[0] + d[0], y = p[1] + d[1];
                if (x < 0 || y < 0 || x >= seen.length || y >= seen[0].length || seen[x][y] || !f.walkable(x * 8, y * 8)) continue;
                seen[x][y] = true; queue.add(new int[] {x,y});
            }
        }
        return false;
    }

    @Test
    void everyInteriorRequiresBothGatesAndAllRoomsConnectAfterVictory() {
        for (Zone zone : Zone.values()) {
            Field f = new Field(zone, null, Dungeon.of(zone));
            Progress p = new Progress(1);
            var guards = f.spots.stream().filter(s -> s.kind == Field.Kind.GUARDIAN).toList();
            var goal = f.spots.stream().filter(s -> s.kind == Field.Kind.RELIC).findFirst().orElseThrow();
            assertTrue(reachable(f, guards.get(0).homeX, guards.get(0).homeY), zone.toString());
            assertFalse(reachable(f, goal.homeX, goal.homeY));
            f.complete(p, goal);
            assertFalse(goal.done, "no rescue through a locked gate");
            f.complete(p, guards.get(0));
            assertTrue(reachable(f, guards.get(1).homeX, guards.get(1).homeY));
            assertFalse(reachable(f, goal.homeX, goal.homeY));
            f.complete(p, guards.get(1));
            assertFalse(reachable(f, goal.homeX, goal.homeY), "sentries alone cannot solve the mechanism");
            for (var control : f.spots) if (control.kind == Field.Kind.MECHANISM)
                assertTrue(reachable(f, control.homeX, control.homeY), zone + ": control reachable before puzzle");
            ExpeditionTest.solve(f, p);
            for (var spot : f.spots) assertTrue(reachable(f, spot.homeX, spot.homeY), zone + ": " + spot.id);
            f.complete(p, goal);
            assertTrue(f.dungeonComplete());
            assertFalse(p.isCleared(zone), "dungeon does not award the outdoor boss/emerald");
            assertTrue(p.seen(zone.key + "-field-memory"));
        }
    }

    @Test
    void outdoorBossAndLegacyDiscoveryNeverClearGuardsOrRemoveEntrances() {
        for (Zone zone : Zone.values()) {
            Progress p = new Progress(1);
            p.clear(zone); p.markSeen(zone.key + "-field-memory");
            Field outside = new Field(zone, null); outside.restore(p); outside.setPosition(outside.entrance().homeX, outside.entrance().homeY + 24);
            assertEquals(Field.Kind.DUNGEON, outside.nearby(0).kind, "old discovery remains enterable");
            Field room = new Field(zone, null, Dungeon.of(zone)); room.restore(p);
            assertFalse(room.guardDefeated(0)); assertFalse(room.guardDefeated(1));
            assertFalse(room.dungeonComplete());
        }
    }

    @Test
    void partialDungeonProgressAndInteriorResumeSurviveSaveRoundTrip() {
        Progress p = new Progress(1);
        Field f = new Field(Zone.GREEN_HILL, null, Dungeon.of(Zone.GREEN_HILL));
        var guards = f.spots.stream().filter(s -> s.kind == Field.Kind.GUARDIAN).toList();
        f.retreat(guards.get(0));
        assertFalse(guards.get(0).done, "fleeing opens no gate");
        f.complete(p, guards.get(0));
        p.setResume(Zone.GREEN_HILL, 1); p.setResumeDungeon(true);
        Progress loaded = SaveCodec.decode(SaveCodec.encode(p));
        Field resumed = new Field(Zone.GREEN_HILL, null, Dungeon.of(Zone.GREEN_HILL)); resumed.restore(loaded);
        assertTrue(loaded.resumeDungeon());
        assertTrue(resumed.guardDefeated(0)); assertFalse(resumed.guardDefeated(1));
        loaded.setResume(Zone.GREEN_HILL, 1);
        assertFalse(loaded.resumeDungeon(), "leaving or reaching an outdoor camp clears the interior checkpoint");
        assertFalse(SaveCodec.decode(SaveCodec.encode(p).replace("dungeon=1\n", "")).resumeDungeon(), "old saves load outdoors");
    }
}
