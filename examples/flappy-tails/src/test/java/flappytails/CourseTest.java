package flappytails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Courses are reproducible, stay on screen and never ask for a climb flight cannot make. */
class CourseTest {
    private static List<Course.Gate> layOut(long seed, int firstGate, int gates) {
        Course course = new Course();
        course.reset(seed, firstGate);
        List<Course.Gate> all = new ArrayList<>();
        while (all.size() < gates) {
            for (Course.Gate gate : course.gates()) {
                if (all.isEmpty() || gate.number > all.get(all.size() - 1).number) all.add(gate);
            }
            course.advance(0x400);
        }
        return all;
    }

    @Test
    void theSameSeedLaysTheSameCourse() {
        List<Course.Gate> a = layOut(42, 0, 60);
        List<Course.Gate> b = layOut(42, 0, 60);
        for (int i = 0; i < 60; i++) {
            assertEquals(a.get(i).centre, b.get(i).centre);
            assertEquals(a.get(i).x, b.get(i).x);
        }
    }

    @Test
    void gapsStayInsideTheScreenAndChangeWithinFlightsReach() {
        for (long seed = 1; seed <= 25; seed++) {
            List<Course.Gate> gates = layOut(seed * 31, 0, 120);
            for (int i = 0; i < gates.size(); i++) {
                Course.Gate gate = gates.get(i);
                assertTrue(gate.top() >= Course.TOP_MARGIN, "gap top on screen");
                assertTrue(gate.bottom() <= Course.GROUND_Y - 16, "gap bottom above the ground");
                if (i > 0 && gate.number % Zone.GATES != 0) {
                    assertTrue(Math.abs(gate.centre - gates.get(i - 1).centre) <= 40, "change within reach");
                }
            }
        }
    }

    @Test
    void zonesAreTenGatesApartWithOpenSkyBetween() {
        assertEquals(Course.START, Course.gateX(0));
        assertEquals(Course.SPACING, Course.gateX(1) - Course.gateX(0));
        assertEquals(Course.SPACING + Course.BREATHER, Course.gateX(Zone.GATES) - Course.gateX(Zone.GATES - 1));
        assertEquals(2, Zone.tourIndex(25));
        assertEquals(1, Zone.lap(Zone.GATES * Zone.TOUR_LENGTH));
    }

    @Test
    void laterLapsAreFasterAndTighterButNeverClosed() {
        Zone first = Zone.tour().get(4);
        Zone later = first.forLap(9);
        assertTrue(later.speed() > first.speed());
        assertTrue(later.gap() >= 72);
    }
}
