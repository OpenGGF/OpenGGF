package threeislands.field;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import threeislands.core.Progress;
import threeislands.core.Zone;

/** The hand-authored areas: forced encounters, gates and enough distance to feel like a journey. */
class MapTest {
    private static final List<Zone> MAPPED = List.of(Zone.GREEN_HILL, Zone.STAR_LIGHT);

    /** Beats every blocker and opens every optional or route gate (puzzles solved in their rules). */
    static void openEverything(Field field, Progress progress) {
        for (var spot : field.spots) if (spot.blocking) field.complete(progress, spot);
        if (field.spots.stream().anyMatch(s -> s.id.equals("route-switch-0"))) ExpeditionTest.solve(field, progress);
        for (var spot : field.spots) if (spot.id.startsWith("sluice-") && !spot.done) field.mechanism(progress, spot);
    }

    /** Eight-pixel flood fill from the western trail. */
    static boolean[][] flood(Field field) {
        boolean[][] seen = new boolean[field.width() / 8 + 1][field.height() / 8 + 1];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {11, 42}); seen[11][42] = true;
        while (!queue.isEmpty()) {
            int[] p = queue.removeFirst();
            for (int[] d : new int[][] {{1,0},{-1,0},{0,1},{0,-1}}) {
                int x = p[0] + d[0], y = p[1] + d[1];
                if (x < 0 || y < 0 || x >= seen.length || y >= seen[0].length || seen[x][y] || !field.walkable(x * 8, y * 8)) continue;
                seen[x][y] = true; queue.add(new int[] {x, y});
            }
        }
        return seen;
    }

    static boolean reached(boolean[][] seen, double x, double y) {
        return seen[(int) x / 8][(int) y / 8];
    }

    private static int count(boolean[][] seen) {
        int n = 0;
        for (boolean[] column : seen) for (boolean cell : column) if (cell) n++;
        return n;
    }

    /** Walking distance in map cells between two points, with every gate open. */
    private static int cells(Field field, double fromX, double fromY, double toX, double toY) {
        int fx = (int) fromX / 32, fy = (int) fromY / 32, tx = (int) toX / 32, ty = (int) toY / 32;
        Map<Integer, Integer> dist = new HashMap<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {fx, fy}); dist.put(fy * 1000 + fx, 0);
        while (!queue.isEmpty()) {
            int[] p = queue.removeFirst();
            if (p[0] == tx && p[1] == ty) return dist.get(ty * 1000 + tx);
            for (int[] d : new int[][] {{1,0},{-1,0},{0,1},{0,-1}}) {
                int x = p[0] + d[0], y = p[1] + d[1];
                if (dist.containsKey(y * 1000 + x) || !field.walkable(x * 32 + 16, y * 32 + 16)) continue;
                dist.put(y * 1000 + x, dist.get(p[1] * 1000 + p[0]) + 1); queue.add(new int[] {x, y});
            }
        }
        return -1;
    }

    private static Field.Spot spot(Field field, String id) {
        return field.spots.stream().filter(s -> s.id.equals(id)).findFirst().orElseThrow();
    }

    @Test
    void theRouteCannotBePassedWithoutFightingAndEveryBlockerGuardsSomething() {
        for (Zone zone : MAPPED) {
            Field field = new Field(zone, null);
            Progress progress = new Progress(1);
            var relay = spot(field, "signal");
            assertFalse(reached(flood(field), relay.homeX, relay.homeY), zone + ": badniks bar the relay");
            var blockers = field.spots.stream().filter(s -> s.blocking).toList();
            assertTrue(blockers.size() >= 5, zone + " has several forced fights");
            openEverything(field, progress);
            int all = count(flood(field));
            assertTrue(reached(flood(field), relay.homeX, relay.homeY), zone + ": relay reachable once they fall");
            for (var blocker : blockers) {
                blocker.done = false;
                assertTrue(count(flood(field)) < all, zone + ": " + blocker.id + " stands in a real pass");
                blocker.done = true;
            }
            for (var s : field.spots) {
                if (s.blocking) continue;
                assertTrue(reached(flood(field), s.homeX, s.homeY), zone + ": reachable " + s.id);
            }
        }
    }

    @Test
    void theRequiredRouteIsALongWalk() {
        for (Zone zone : MAPPED) {
            Field field = new Field(zone, null);
            openEverything(field, new Progress(1));
            double x = 88, y = 336;
            int total = 0;
            List<String> stops = zone == Zone.GREEN_HILL ? List.of("memory", "signal", "boss")
                    : List.of("route-switch-0", "route-switch-1", "memory", "signal", "boss");
            for (String id : stops) {
                var stop = spot(field, id);
                double ty = stop.homeY + (stop.kind == Field.Kind.DUNGEON || stop.kind == Field.Kind.BOSS ? 32 : 0);
                int leg = cells(field, x, y, stop.homeX, ty);
                assertTrue(leg > 0, zone + " reaches " + id);
                total += leg;
                x = stop.homeX; y = ty;
            }
            System.out.println(zone + " required route: " + total + " cells");
            assertTrue(total >= 140, zone + " required route is only " + total + " cells");
        }
    }

    @Test
    void starLightsSkywalkGateNeedsBothFeeders() {
        Field field = new Field(Zone.STAR_LIGHT, null);
        Progress progress = new Progress(1);
        for (var s : field.spots) if (s.blocking) field.complete(progress, s);
        assertFalse(field.routeOpen());
        assertFalse(field.walkable(51 * 32 + 16, 10 * 32 + 16), "the gate is closed");
        field.mechanism(progress, spot(field, "route-switch-0"));
        assertFalse(field.walkable(51 * 32 + 16, 10 * 32 + 16));
        field.mechanism(progress, spot(field, "route-switch-1"));
        assertTrue(field.walkable(51 * 32 + 16, 10 * 32 + 16), "both feeders power the skywalk");
    }

    @Test
    void markersSitOnGroundAndCampsHaveRoomToStand() {
        for (Zone zone : MAPPED) {
            Field field = new Field(zone, null);
            openEverything(field, new Progress(1));
            for (var s : field.spots) {
                if (s.blocking) continue;
                assertTrue(field.walkable(s.homeX, s.homeY), zone + ": " + s.id + " on ground");
                if (s.kind == Field.Kind.STARPOST) assertTrue(field.walkable(s.homeX, s.homeY + 24), zone + ": " + s.id);
            }
            assertTrue(field.walkable(88, 336), "western trail start");
            assertTrue(field.walkable(56, 336), "western exit");
        }
    }

    @Test
    void theMidRouteStarpostIsAResumePoint() {
        for (Zone zone : MAPPED) {
            Progress progress = new Progress(1);
            progress.setResume(zone, 3);
            Progress loaded = threeislands.core.SaveCodec.decode(threeislands.core.SaveCodec.encode(progress));
            Field field = new Field(zone, null);
            field.restore(loaded);
            field.resumeAtCamp(loaded.resumeX());
            var waypoint = spot(field, "waypoint");
            assertEquals(waypoint.homeX, field.x());
            assertEquals(waypoint.homeY + 24, field.y());
        }
    }

    @Test
    void pressingIntoAWallBesideATrailSlidesIntoIt() {
        Field field = new Field(Zone.GREEN_HILL, null);
        // Row 10 is the village trail; the forest trail turns north up column 19 (x 608-639).
        for (var s : field.spots) if (s.blocking) s.done = true;
        field.setPosition(596, 336);
        for (int i = 0; i < 60; i++) field.step(0, -1, false, i);
        assertTrue(field.y() < 300, "slid onto the northern trail without exact alignment");
        assertTrue(field.x() >= 608 && field.x() < 640);
    }
}
