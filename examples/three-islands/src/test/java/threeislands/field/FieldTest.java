package threeislands.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import threeislands.core.Zone;

/** Things on the path, walking, contact and the boss gate. */
class FieldTest {
    private static FieldPath flat(int length) {
        Terrain terrain = new Terrain() {
            @Override
            public boolean solid(int x, int y) {
                return y >= 600;
            }

            @Override
            public int[] area() {
                return new int[] {0, 0, length, 1024};
            }
        };
        return FieldPath.build(terrain, 0, length - 1, 580);
    }

    @Test
    void layoutIsFixedPerZoneAndEndsWithTheBoss() {
        Field a = new Field(Zone.CHEMICAL_PLANT, flat(9000));
        Field b = new Field(Zone.CHEMICAL_PLANT, flat(9000));
        assertEquals(a.spots.size(), b.spots.size());
        for (int i = 0; i < a.spots.size(); i++) {
            assertEquals(a.spots.get(i).homeX, b.spots.get(i).homeX);
            assertEquals(a.spots.get(i).group, b.spots.get(i).group);
        }
        Field.Spot last = a.spots.get(a.spots.size() - 1);
        assertEquals(Field.Kind.BOSS, last.kind);
        assertTrue(a.spots.stream().filter(s -> s.kind == Field.Kind.STARPOST).count() == 2);
        assertTrue(a.spots.stream().filter(s -> s.kind == Field.Kind.ENCOUNTER).count() >= 6);
        assertTrue(a.spots.stream().filter(s -> s.kind == Field.Kind.MONITOR).count() >= 3);
        Field dez = new Field(Zone.DEATH_EGG, flat(12000));
        assertEquals(1, dez.spots.stream().filter(s -> s.kind == Field.Kind.MIDBOSS).count());
    }

    @Test
    void walkingTouchesThingsInOrderAndTheBossBarsTheWay() {
        Field field = new Field(Zone.GREEN_HILL, flat(6000));
        List<Field.Spot> touched = new java.util.ArrayList<>();
        long tick = 0;
        for (int i = 0; i < 20000 && touched.size() < field.spots.size(); i++) {
            Field.Spot spot = field.step(1, true, tick++);
            if (spot != null) {
                touched.add(spot);
                spot.done = true;
            }
        }
        assertEquals(field.spots.size(), touched.size(), "every spot is reached");
        assertEquals(Field.Kind.BOSS, touched.get(touched.size() - 1).kind);
        assertTrue(field.x() <= field.boss().homeX + Field.TOUCH);
    }

    @Test
    void theBossCannotBePassedUntilBeaten() {
        Field field = new Field(Zone.GREEN_HILL, flat(6000));
        field.skipTo(field.boss().homeX - 30);
        for (int i = 0; i < 100; i++) {
            Field.Spot spot = field.step(1, true, i);
            if (spot != null) {
                assertEquals(Field.Kind.BOSS, spot.kind);
                break;
            }
        }
        assertTrue(field.x() <= field.boss().homeX);
    }

    @Test
    void escapingABattleStepsBackAndIgnoresBadniksBriefly() {
        Field field = new Field(Zone.EMERALD_HILL, flat(8000));
        Field.Spot first = null;
        for (int i = 0; i < 5000 && first == null; i++) {
            Field.Spot spot = field.step(1, false, 0);
            if (spot != null && spot.kind == Field.Kind.ENCOUNTER) first = spot;
            else if (spot != null) spot.done = true;
        }
        assertNotNull(first);
        field.retreat(first);
        assertTrue(Math.abs(field.x() - first.homeX) > Field.TOUCH);
        assertNull(field.step(1, false, 0), "grace period");
        assertFalse(first.done);
    }

    @Test
    void followersTrailTheLeader() {
        Field field = new Field(Zone.EMERALD_HILL, flat(8000));
        double start = field.x();
        for (int i = 0; i < 40; i++) field.step(1, false, 0);
        assertTrue(field.followerX(1) < field.x());
        assertTrue(field.followerX(2) < field.followerX(1));
        assertTrue(field.followerX(2) >= start);
    }
}
