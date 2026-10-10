package threeislands.field;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import threeislands.core.Progress;
import threeislands.core.SaveCodec;
import threeislands.core.Zone;

class ExpeditionTest {
    static void solve(Field field, Progress progress) {
        if (field.puzzle.rule == MechanismPuzzle.Rule.ROTATE) {
            for (int index = 0; index < field.puzzle.labels.length; index++) {
                int control = index;
                var spot = field.spots.stream().filter(s -> s.id.endsWith("-switch-" + control)).findFirst().orElseThrow();
                while (!field.puzzleOpen() && field.puzzle.facing(control) != field.puzzle.target(control)) field.mechanism(progress, spot);
            }
            assertTrue(field.puzzleOpen(), field.zone.toString());
            return;
        }
        int[] order = switch (field.puzzle.rule) {
            case RESTORE, ROTATE -> java.util.stream.IntStream.range(0, field.puzzle.labels.length).toArray();
            case CIRCUIT -> new int[] {0, 2};
            case SEQUENCE -> field.puzzle.labels.length == 4 ? new int[] {2,0,3,1}
                    : field.puzzle.labels.length == 3 ? new int[] {2,0,1} : new int[] {1,0};
        };
        for (int index : order) field.mechanism(progress, field.spots.stream()
                .filter(s -> s.id.endsWith("-switch-" + index)).findFirst().orElseThrow());
        assertTrue(field.puzzleOpen(), field.zone.toString());
    }
    static Set<String> reach(Field field) {
        Set<String> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>(); queue.add(new int[] {88,336}); seen.add("88:336");
        while (!queue.isEmpty()) {
            int[] at = queue.removeFirst();
            for (int[] d : new int[][] {{8,0},{-8,0},{0,8},{0,-8}}) {
                int x = at[0] + d[0], y = at[1] + d[1];
                if (field.walkable(x,y) && seen.add(x + ":" + y)) queue.add(new int[] {x,y});
            }
        }
        return seen;
    }
    @Test void exteriorMechanismsAreReachableAndActuallyGateTheFinalDistrict() {
        Set<String> signatures = new HashSet<>();
        for (Zone zone : Zone.values()) {
            Field f = new Field(zone, null); if (f.layout == null) continue;
            assertTrue(signatures.add(f.layout.route.toString()), "authored distinct routes");
            Progress p = new Progress(1);
            Set<String> before = reach(f);
            for (var spot : f.spots) if (spot.kind == Field.Kind.MECHANISM || spot.kind == Field.Kind.DUNGEON)
                assertTrue(before.contains((int) spot.homeX + ":" + (int) spot.homeY), zone + ": " + spot.id);
            var relay = f.spots.stream().filter(s -> s.kind == Field.Kind.DISCOVERY).findFirst().orElseThrow();
            assertFalse(before.contains((int) relay.homeX + ":" + (int) relay.homeY), zone + ": no bypass");
            solve(f, p);
            Set<String> after = reach(f);
            assertTrue(after.contains((int) relay.homeX + ":" + (int) relay.homeY), zone.toString());
            Field loaded = new Field(zone, null); loaded.restore(SaveCodec.decode(SaveCodec.encode(p)));
            assertTrue(loaded.puzzleOpen());
            loaded.resumeAtCamp(2); assertTrue(loaded.walkable(loaded.x(), loaded.y()));
            assertEquals(Field.Kind.STARPOST, loaded.nearby(0).kind);
            assertFalse(f.walkable(f.entrance().homeX, f.entrance().homeY - 48), "facade is physical");
        }
    }
    @Test void mistakesAreRetryableAndSolvedPuzzlesSurviveReloadWithoutRepeatRewards() {
        for (Zone zone : Zone.values()) {
            Field f = new Field(zone, null, Dungeon.of(zone)); Progress p = new Progress(1);
            var controls = f.spots.stream().filter(s -> s.kind == Field.Kind.MECHANISM).toList();
            if (f.puzzle.rule == MechanismPuzzle.Rule.SEQUENCE) {
                f.mechanism(p, controls.get(0)); assertFalse(f.puzzleOpen());
            } else if (f.puzzle.rule == MechanismPuzzle.Rule.ROTATE) {
                for (int turn = 0; turn < 4; turn++) f.mechanism(p, controls.get(0));
                assertEquals(0, f.puzzle.facing(0), "a full turn returns the dial");
            } else if (f.puzzle.rule == MechanismPuzzle.Rule.CIRCUIT) {
                f.mechanism(p, controls.get(1)); f.mechanism(p, controls.get(1));
                assertFalse(f.puzzleOpen(), "turning twice reverses the control");
            }
            solve(f, p);
            String saved = SaveCodec.encode(p);
            for (var control : controls) f.mechanism(p, control);
            assertEquals(saved, SaveCodec.encode(p));
            Field loaded = new Field(zone, null, Dungeon.of(zone)); loaded.restore(SaveCodec.decode(saved));
            assertTrue(loaded.puzzleOpen());
        }
    }
    @Test void legacyRelayAndCompletedDungeonSavesKeepTheirReturnRoutes() {
        for (Zone zone : Zone.values()) {
            Progress old = new Progress(1);
            old.markSeen(zone.key + "-field-signal");
            Field outside = new Field(zone, null); outside.restore(old);
            if (outside.layout != null) for (var link : outside.layout.passages) if (link.lock().equals("route"))
                assertFalse(outside.sealed((link.from().x() + link.to().x()) / 2, (link.from().y() + link.to().y()) / 2));
            old.markSeen(zone.key + "-field-dungeon-guard-0");
            old.markSeen(zone.key + "-field-dungeon-guard-1");
            old.markSeen(zone.key + "-field-dungeon-goal");
            Field inside = new Field(zone, null, Dungeon.of(zone)); inside.restore(old);
            assertTrue(inside.relicReady());
            var goal = inside.layout.route.getLast();
            assertTrue(reach(inside).contains(goal.x() + ":" + goal.y()));
        }
    }

    @Test void authoredLinksAreAxisAlignedAndRoomsNeverOverlap() {
        for (Zone z : Zone.values()) for (boolean inside : new boolean[] {false,true}) {
            if (!inside && z == Zone.GREEN_HILL) continue;
            AreaLayout a = inside ? Dungeon.of(z).layout() : AreaLayout.outside(z);
            assertEquals(a.rooms().size(), new HashSet<>(a.rooms()).size(), z + ": duplicate rooms");
            for (var link : a.passages) assertTrue(link.from().x() == link.to().x() || link.from().y() == link.to().y(), z + ": diagonal passage");
            for (var room : a.rooms()) assertTrue(room.x() >= 64 && room.y() >= 64 && room.x() < a.width && room.y() < a.height);
        }
    }

    @Test void everyAreaHidesOneReachableAnimalAndEveryInteriorOneTreasureRoom() {
        java.util.Set<threeislands.core.Gear> treasures = new HashSet<>();
        for (Zone zone : Zone.values()) {
            Field outside = new Field(zone, null);
            var animal = outside.spots.stream().filter(s -> s.kind == Field.Kind.ANIMAL).toList();
            assertEquals(1, animal.size(), zone.toString());
            Progress p = new Progress(1);
            if (outside.layout != null) solve(outside, p);
            Set<String> open = reach(outside);
            int ax = (int) animal.get(0).homeX / 8 * 8, ay = (int) animal.get(0).homeY / 8 * 8;
            assertTrue(outside.walkable(animal.get(0).homeX, animal.get(0).homeY), zone + " animal stands on ground");
            assertTrue(open.stream().anyMatch(k -> { String[] xy = k.split(":");
                    return Math.hypot(Integer.parseInt(xy[0]) - animal.get(0).homeX, Integer.parseInt(xy[1]) - animal.get(0).homeY) < 24; }),
                    zone + " animal reachable at " + ax + "," + ay);
            Field inside = new Field(zone, null, Dungeon.of(zone));
            var chest = inside.spots.stream().filter(s -> s.kind == Field.Kind.CHEST).findFirst().orElseThrow();
            assertTrue(treasures.add(chest.gear), "each treasure is unique");
            assertEquals(threeislands.core.Gear.values()[0].shop, 0);
            assertTrue(chest.gear.shop < 0, "treasure is never sold");
            for (var spot : inside.spots) if (spot.kind == Field.Kind.MECHANISM)
                assertFalse(spot.homeX == chest.homeX && spot.homeY == chest.homeY, zone + ": treasure room is its own room");
        }
    }

    @Test void everyRotatingDialsClueNamesItsTargetDirection() {
        for (Zone zone : Zone.values()) for (boolean inside : new boolean[] {false, true}) {
            MechanismPuzzle puzzle = MechanismPuzzle.of(zone, inside);
            if (puzzle.rule != MechanismPuzzle.Rule.ROTATE) continue;
            String clue = puzzle.instructions().toLowerCase(java.util.Locale.ROOT);
            for (int i = 0; i < puzzle.labels.length; i++) {
                String direction = MechanismPuzzle.direction(puzzle.target(i));
                assertTrue(clue.contains(direction), zone + " clue names " + direction);
            }
        }
    }
}
