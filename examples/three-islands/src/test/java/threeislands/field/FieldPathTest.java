package threeislands.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The route finder on hand-made terrain. */
class FieldPathTest {
    /** Ground at {@code ground[x / 32]} (or none for -1); everything below it is solid. */
    private record Steps(int[] ground) implements Terrain {
        @Override
        public boolean solid(int x, int y) {
            int column = x / 32;
            if (x < 0 || column >= ground.length) return false;
            return ground[column] >= 0 && y >= ground[column];
        }

        @Override
        public int[] area() {
            return new int[] {0, 0, ground.length * 32, 1024};
        }
    }

    @Test
    void flatGroundIsWalkedAtFloorHeight() {
        FieldPath path = FieldPath.build(new Steps(new int[] {500, 500, 500, 500}), 0, 127, 480);
        for (int x = 0; x <= 127; x++) {
            assertEquals(500, path.floorAt(x));
            assertEquals(500, path.heightAt(x));
            assertFalse(path.airborneAt(x));
        }
    }

    @Test
    void stepsAreClimbedEarlyAndDropsAreEasedNeverBelowTheFloor() {
        FieldPath path = FieldPath.build(new Steps(new int[] {500, 500, 470, 470, 520, 520}), 0, 191, 480);
        assertEquals(470, path.floorAt(70));
        assertEquals(520, path.floorAt(140));
        for (int x = 0; x <= 191; x++) assertTrue(path.heightAt(x) <= path.floorAt(x), "never inside the ground at " + x);
        assertTrue(path.heightAt(60) < 500, "rises before the step");
        assertTrue(path.heightAt(130) < 520, "falls gradually after the ledge");
    }

    @Test
    void bottomlessPitsAreJumpedInAnArc() {
        FieldPath path = FieldPath.build(new Steps(new int[] {500, 500, -1, -1, 500, 500}), 0, 191, 480);
        assertTrue(path.airborneAt(96));
        assertTrue(path.heightAt(96) < 490, "the arc lifts over the pit");
        assertEquals(500, path.heightAt(10));
        assertEquals(500, path.heightAt(185));
    }

    @Test
    void tallWallsAndDeepDropsFindTheNearestRealFloor() {
        FieldPath path = FieldPath.build(new Steps(new int[] {800, 800, 200, 200, 900, 900}), 0, 191, 780);
        assertEquals(200, path.floorAt(80), "climbs a 600px wall rather than walking through it");
        assertEquals(900, path.floorAt(150), "drops 700px to the floor below");
        int air = 0;
        for (int x = 0; x <= 191; x++) if (path.airborneAt(x)) air++;
        assertTrue(air < 120, "big height changes are quick hops, not long glides: " + air);
    }

    @Test
    void theRouteStaysInsideTheRequestedSpan() {
        FieldPath path = FieldPath.build(new Steps(new int[] {500, 500, 500, 500}), 40, 5000, 480);
        assertEquals(40, path.startX());
        assertEquals(127, path.endX());
        assertEquals(500, path.heightAt(-100));
        assertEquals(500, path.heightAt(10_000));
    }
}
