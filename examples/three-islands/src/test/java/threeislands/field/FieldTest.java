package threeislands.field;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;
import threeislands.core.Progress;
import threeislands.core.SaveCodec;
import threeislands.core.Zone;

/** Exploration topology and persistence, independent of battle scenery and engine APIs. */
class FieldTest {
    private Field field() { return new Field(Zone.GREEN_HILL, null); }

    @Test
    void diagonalMovementHasTheSameSpeedAndFollowersRememberBothAxes() {
        Field straight = field(), diagonal = field();
        straight.setPosition(240, 336);
        diagonal.setPosition(240, 336);
        assertTrue(straight.followerX(1) < straight.x(), "companions must not pile up at spawn");
        assertTrue(straight.followerX(2) < straight.followerX(1));
        straight.step(1, 0, false, 0);
        diagonal.step(1, 1, false, 0);
        assertEquals(straight.distance(), diagonal.distance(), 0.00001);
        for (int i = 0; i < 30; i++) diagonal.step(1, 1, false, i);
        assertTrue(diagonal.followerX(1) < diagonal.x());
        assertTrue(diagonal.followerY(1) < diagonal.y());
        assertTrue(diagonal.followerX(2) < diagonal.followerX(1));
    }

    @Test
    void waterBlocksWalkingButTheBridgeAllowsCrossingOnceItsGuardIsBeaten() {
        Field field = field();
        // Green Hill's river runs down columns 31-33; its log bridge is on row 30.
        assertTrue(field.water(32 * 32 + 16, 10 * 32 + 16));
        assertFalse(field.walkable(32 * 32 + 16, 10 * 32 + 16));
        field.setPosition(29 * 32 + 16, 30 * 32 + 16);
        for (int i = 0; i < 90; i++) field.step(1, 0, true, i);
        assertTrue(field.x() < 32 * 32, "the Crabmeat on the bridge bars the way");
        Progress progress = new Progress(1);
        field.complete(progress, field.spots.stream().filter(s -> s.id.equals("foe-1")).findFirst().orElseThrow());
        for (int i = 0; i < 90; i++) field.step(1, 0, true, i);
        assertTrue(field.x() > 34 * 32, "the bridge crosses the river");
    }

    @Test
    void everyLandmarkCanBeReachedWithoutTouchingAnOrdinaryEnemy() {
        for (Zone zone : Zone.values()) {
            Field f = new Field(zone, null);
            // Authored maps deliberately put badniks in narrow passes; MapTest covers them.
            if (f.map != null) continue;
            if (f.layout != null) ExpeditionTest.solve(f, new Progress(1));
            // Flood fill all safely walkable eight-pixel cells, allowing for patrol movement.
            boolean[][] seen = new boolean[f.width() / 8][f.height() / 8];
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            queue.add(new int[] {11, 42}); seen[11][42] = true;
            while (!queue.isEmpty()) {
                int[] point = queue.removeFirst();
                for (int[] d : new int[][] {{1,0},{-1,0},{0,1},{0,-1}}) {
                    int x = point[0] + d[0], y = point[1] + d[1];
                    if (x < 0 || y < 0 || x >= seen.length || y >= seen[0].length || seen[x][y] || !f.walkable(x * 8, y * 8)) continue;
                    boolean safe = true;
                    for (Field.Spot spot : f.spots) if (spot.kind == Field.Kind.ENCOUNTER
                            && Math.hypot(spot.homeX - x * 8, spot.homeY - y * 8) < Field.TOUCH + 16) safe = false;
                    if (safe) { seen[x][y] = true; queue.add(new int[] {x,y}); }
                }
            }
            for (Field.Spot spot : f.spots) if (spot.kind != Field.Kind.ENCOUNTER && !spot.id.equals("orchard-letter")) {
                assertTrue(f.walkable(spot.homeX, spot.homeY), zone + ": landmark on solid ground " + spot.id);
                assertTrue(seen[(int) spot.homeX / 8][(int) spot.homeY / 8], zone + ": reachable " + spot.id);
            }
        }
    }

    @Test
    void passingAtADifferentDepthAvoidsBattleAndInteractionsNeedConfirmation() {
        Field field = new Field(Zone.ANGEL_ISLAND, null);
        Field.Spot enemy = field.spots.stream().filter(s -> s.kind == Field.Kind.ENCOUNTER).findFirst().orElseThrow();
        field.setPosition(enemy.homeX, enemy.homeY + 60);
        assertNull(field.step(0, 0, false, 0));
        field.setPosition(enemy.x(0), enemy.homeY);
        assertSame(enemy, field.step(0, 0, false, 0));
        field.setPosition(144, 336);
        assertNull(field.step(0, 0, false, 0), "camps do not interrupt on contact");
        assertEquals(Field.Kind.STARPOST, field.nearby(0).kind);
    }

    @Test
    void discoveriesWorkInEitherOrderAndTheGuardianCannotBeSkipped() {
        for (boolean reverse : new boolean[] {false, true}) {
            Field field = new Field(Zone.ANGEL_ISLAND, null);
            Progress progress = new Progress(1);
            var clues = field.spots.stream().filter(s -> (s.kind == Field.Kind.DISCOVERY || s.kind == Field.Kind.DUNGEON)).toList();
            assertFalse(field.bossReady());
            field.complete(progress, clues.get(reverse ? 1 : 0));
            assertEquals(1, field.discoveries());
            field.complete(progress, clues.get(reverse ? 0 : 1));
            assertFalse(field.bossReady(), "the Flame Craft still guards Knuckles");
            field.complete(progress, field.spots.stream().filter(s -> s.kind == Field.Kind.MIDBOSS).findFirst().orElseThrow());
            assertTrue(field.bossReady());
        }
    }

    @Test
    void completedContentSurvivesSavingAndCampsRemainUsable() {
        Field field = field();
        Progress progress = new Progress(42);
        for (Field.Spot spot : field.spots) field.complete(progress, spot);
        Progress loaded = SaveCodec.decode(SaveCodec.encode(progress));
        Field resumed = field(); resumed.restore(loaded);
        for (Field.Spot spot : resumed.spots) assertTrue(spot.done, spot.id);
        resumed.resumeAtCamp(1);
        assertEquals(Field.Kind.STARPOST, resumed.nearby(0).kind);
        resumed.resumeAtCamp(2);
        Field.Spot sanctuary = resumed.spots.stream().filter(s -> s.id.equals("sanctuary")).findFirst().orElseThrow();
        assertEquals(sanctuary.homeX, resumed.x()); assertEquals(sanctuary.homeY + 24, resumed.y());
        resumed.resumeAtCamp(5280);
        assertEquals(144, resumed.x(), "old side-scrolling checkpoints migrate to camp");
        assertEquals(2, resumed.discoveries());
    }

    @Test
    void escapeDoesNotRetriggerABattleAndKeepsThePartyOnLand() {
        Field field = new Field(Zone.ANGEL_ISLAND, null);
        Field.Spot enemy = field.spots.stream().filter(s -> s.kind == Field.Kind.ENCOUNTER).findFirst().orElseThrow();
        field.setPosition(enemy.homeX, enemy.homeY);
        field.retreat(enemy);
        assertTrue(field.walkable(field.x(), field.y()));
        assertNull(field.step(0, 0, false, 0));
        assertFalse(enemy.done);
    }
    @Test
    void everyAnchorNeedsItsDungeonAndRelayAndEveryTrailNeedsItsBoss() {
        for (Zone zone : Zone.values()) {
            Progress progress = new Progress(1);
            Field field = new Field(zone, null);
            assertFalse(field.relayReady());
            assertFalse(field.anchorExposed());
            assertFalse(field.bossReady());
            progress.markSeen(zone.key + "-field-memory");
            field.restore(progress);
            assertTrue(field.relayReady());
            assertFalse(field.anchorExposed());
            progress.markSeen(zone.key + "-field-signal");
            field.restore(SaveCodec.decode(SaveCodec.encode(progress)));
            assertTrue(field.anchorExposed());
            for (Field.Spot spot : field.spots) if (spot.kind == Field.Kind.MIDBOSS) field.complete(progress, spot);
            assertTrue(field.bossReady());
            if (zone != Zone.DEATH_EGG) {
                for (int y = 88; y <= field.height() - 48; y++) {
                    assertFalse(field.walkable(field.exitX(), y), "no edge bypass: " + zone);
                }
                field.setPosition(field.exitX() - 20, 336);
                for (int i = 0; i < 50; i++) field.step(1, 0, true, i);
                assertTrue(field.x() < field.exitX() - 8, "running cannot cross a seal");
            }
            progress.clear(zone);
            field.restore(SaveCodec.decode(SaveCodec.encode(progress)));
            assertTrue(field.walkable(field.exitX(), 336));
            assertTrue(field.walkable(56, 336), "backtracking stays open");
            // Existing saves keep their cleared trails even without newly required discoveries.
            Progress legacy = new Progress(1); legacy.clear(zone); field.restore(legacy);
            assertTrue(field.bossReady());
            assertTrue(field.walkable(field.exitX(), 336));
        }
    }


    @Test
    void fleeingFromABlockerLeavesThePassBarred() {
        Field field = field();
        Field.Spot blocker = field.spots.stream().filter(s -> s.blocking).findFirst().orElseThrow();
        field.setPosition(blocker.homeX - 64, blocker.homeY);
        Field.Spot touched = null;
        for (int i = 0; i < 80 && touched == null; i++) touched = field.step(1, 0, false, i);
        assertSame(blocker, touched, "walking down the pass meets the badnik");
        field.retreat(blocker);
        assertTrue(field.x() < blocker.homeX - Field.TOUCH, "the party steps back the way it came");
        for (int i = 0; i < 200; i++) field.step(1, 0, true, i);
        assertTrue(field.x() < blocker.homeX, "the grace period cannot be used to slip past");
    }
}
