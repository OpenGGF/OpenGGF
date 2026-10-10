package starpost.ruins;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import starpost.valley.Ground;

/**
 * Chamber generation without a ROM, on {@link TestKit}: every chamber 1-40 is built the same way
 * from the same seed, and an independent traversal (the real controller playing input programs)
 * reaches its exit, its elevator and its finds from the entry. The same check against Sonic 1's
 * own kits is in the design doc (section 16) as a scratch probe.
 */
class ChamberGenTest {
    private static final long SAVE_SEED = 0x5EEDL;

    private final TestKit kit = new TestKit(42, 36);
    private final ChamberGen gen = new ChamberGen((zone, act) -> kit);

    @Test
    void everyChamberIsTheSameAllDayAndItsExitIsReachable() {
        for (int n = 1; n <= RuinsRules.CHAMBERS; n++) {
            long seed = RuinsRules.chamberSeed(SAVE_SEED, 3, n);
            Chamber a = gen.generate(n, seed);
            Chamber b = gen.generate(n, seed);
            assertNotNull(a, "chamber " + n + " is built");
            assertArrayEquals(a.cells, b.cells, "chamber " + n + " blocks are the same on a second visit");
            assertEquals(a.things, b.things, "chamber " + n + " contents are the same");
            assertEquals(a.entryX, b.entryX);
            assertEquals(a.exitX, b.exitX);
            assertEquals(a.waterY, b.waterY);
            assertEquals(RuinsRules.band(n), a.band);

            Reach reach = new Reach(a);
            int entry = reach.spotAt(a.entryX, a.entryY);
            assertTrue(entry >= 0, "chamber " + n + " entry is a standing spot");
            assertFalse(reach.hazard(entry), "chamber " + n + " does not start in lava or on spikes");
            reach.explore(entry);
            if (n == RuinsRules.CHAMBERS) {
                assertTrue(a.exitX < 0, "nothing below the last chamber yet");
            } else {
                int exit = reach.spotAt(a.exitX, a.exitY);
                assertTrue(exit >= 0 && reach.reached(exit), "chamber " + n + " exit is reachable");
            }
            if (RuinsRules.landmark(n)) {
                int elevator = reach.spotAt(a.elevatorX, a.elevatorY);
                assertTrue(elevator >= 0 && reach.reached(elevator), "chamber " + n + " elevator is reachable");
                assertEquals(Landmarks.prizes(n), a.prizes, "chamber " + n + " holds its landmark's finds");
            } else {
                assertTrue(a.elevatorX < 0, "only every fifth chamber has an elevator");
            }
            for (Chamber.Thing t : a.things) {
                if (t.type() == Chamber.ROCK || t.type() == Chamber.MONITOR || t.type() == Chamber.SPRING
                        || t.type() == Chamber.BUBBLES) {
                    int spot = reach.spotAt(t.x(), t.y());
                    assertTrue(spot >= 0 && reach.reached(spot), "chamber " + n + " thing " + t + " is on reached ground");
                }
                if (t.type() == Chamber.RING) {
                    assertFalse(a.floor(t.x(), t.y()), "chamber " + n + " ring " + t + " is not buried");
                }
            }
        }
    }

    @Test
    void sonicArrivesOnLevelGroundWithNoLavaAStepAway() {
        int safe = 0;
        for (int day = 0; day < 3; day++) {
            for (int n = 1; n <= RuinsRules.CHAMBERS; n++) {
                Chamber c = gen.generate(n, RuinsRules.chamberSeed(SAVE_SEED, day, n));
                Reach reach = new Reach(c);
                int entry = reach.spotAt(c.entryX, c.entryY);
                assertTrue(entry >= 0, "chamber " + n + " has a standing spot at its entry");
                if (reach.footing(entry, ChamberGen.ARRIVAL_FOOTING)) {
                    safe++;
                }
            }
        }
        assertTrue(safe >= RuinsRules.CHAMBERS * 3 * 9 / 10, "nearly every arrival has footing: " + safe);
    }

    @Test
    void chambersChangeOvernight() {
        int changed = 0;
        for (int n = 1; n <= RuinsRules.CHAMBERS; n++) {
            Chamber today = gen.generate(n, RuinsRules.chamberSeed(SAVE_SEED, 3, n));
            Chamber tomorrow = gen.generate(n, RuinsRules.chamberSeed(SAVE_SEED, 4, n));
            if (!java.util.Arrays.equals(today.cells, tomorrow.cells) || !today.things.equals(tomorrow.things)) {
                changed++;
            }
        }
        assertTrue(changed >= 30, "most chambers differ the next morning: " + changed);
    }

    @Test
    void bandsUseTheirZonesAndLabyrinthFloods() {
        boolean flooded = false;
        for (int n = 16; n <= 30; n++) {
            Chamber c = gen.generate(n, RuinsRules.chamberSeed(SAVE_SEED, 0, n));
            assertEquals(3, c.zone, "Labyrinth Zone's kit");
            flooded |= c.waterY != Chamber.NO_WATER;
        }
        assertTrue(flooded, "the Labyrinth chambers have water");
        assertEquals(1, gen.generate(2, 7).zone);
        assertEquals(5, gen.generate(33, 7).zone);
    }

    /** One row: floor at 192 everywhere, a 120-pixel wall in the middle block. */
    private Chamber walled(boolean withSpring) {
        TestKit k = new TestKit(1, 4);
        Kit wallKit = new Kit() {
            final byte[] flat = TestKit.floor(192);
            final byte[] wall = TestKit.with(TestKit.floor(192), 100, 156, 72, 192, Chamber.SOLID);

            public int size() { return 256; }
            public int columns() { return 3; }
            public int rows() { return 1; }
            public int block(int c, int r) { return c == 1 ? 2 : 1; }
            public int blockCount() { return 3; }
            public byte[] solidity(int b) { return b == 2 ? wall : b == 1 ? flat : null; }
            public int[] area() { return new int[] {0, 0, 768, 256}; }
            public boolean opaque(int b, int x, int y) { return solidity(b) != null && solidity(b)[y * 256 + x] != 0; }
        };
        Chamber c = new Chamber(2, 1, 0, wallKit, 3, 1, new int[] {1, 2, 1});
        if (withSpring) {
            c.add(Chamber.SPRING, 320, 192, 0);
        }
        return c;
    }

    @Test
    void aWallTooHighToJumpStopsTheTraversalAndASpringClearsIt() {
        Chamber c = walled(false);
        Reach reach = new Reach(c);
        reach.explore(reach.spotAt(40, 192));
        assertFalse(reach.reached(reach.spotAt(600, 192)), "120 pixels is beyond Sonic's jump (about 96)");
        Chamber sprung = walled(true);
        Reach again = new Reach(sprung);
        again.explore(again.spotAt(40, 192));
        assertTrue(again.reached(again.spotAt(600, 192)), "a yellow spring at the wall's foot clears it");
    }

    @Test
    void lavaIsFoundWhereTheKitHasAFloorWithNoPictureAndTheRouteAvoidsIt() {
        Kit pool = new Kit() {
            public int size() { return 256; }
            public int columns() { return 1; }
            public int rows() { return 1; }
            public int block(int c, int r) { return TestKit.LAVA; }
            public int blockCount() { return kit.blockCount(); }
            public byte[] solidity(int b) { return kit.solidity(b); }
            public int[] area() { return new int[] {0, 0, 256, 256}; }
            public boolean opaque(int b, int x, int y) { return kit.opaque(b, x, y); }
        };
        Chamber c = new Chamber(3, 1, 0, pool, 1, 1, new int[] {TestKit.LAVA});
        c.findLava();
        assertEquals(1, c.lava.size());
        Chamber.Lava lava = c.lava.get(0);
        assertEquals(64, lava.x());
        assertEquals(128, lava.w());
        assertTrue(lava.contains(100, 200) && lava.contains(100, 238), "the pool reaches down to its bed");
        Reach reach = new Reach(c);
        assertTrue(reach.hazard(reach.spotAt(128, 200)), "standing on the lava is a hazard");
        assertFalse(reach.hazard(reach.spotAt(24, 192)));
    }

    @Test
    void theGenerationRunnerKeepsTheValleysBehaviourWithoutCeilingsOrWater() {
        GenerationRunner r = new GenerationRunner(100, 192);
        Ground flat = new Ground() {
            public boolean solid(int x, int y) { return y >= 192; }
            public int floorBelow(int x, int fromY) { return Math.max(192, fromY) == fromY && fromY > 192 ? fromY : 192; }
            public int left() { return 0; }
            public int right() { return 10_000; }
        };
        assertTrue(r.step(flat, false, false, false, true, true, 0), "a jump with no ceiling to check");
        assertEquals(-GenerationRunner.JUMP, r.ySpeed, 1e-6, "Sonic_Jump's $680");
        r.step(flat, false, false, false, false, true, 1);
        assertEquals(-GenerationRunner.JUMP + GenerationRunner.GRAVITY, r.ySpeed, 1e-6, "then dry gravity");
        int frames = 0;
        while (!r.onGround && frames++ < 200) {
            r.step(flat, false, false, false, false, true, frames);
        }
        assertTrue(frames < 70, "lands in about a second");
        GenerationRunner wet = new GenerationRunner(100, 192);
        wet.setUnderwater(true);
        wet.step(flat, false, false, false, true, true, 0);
        wet.step(flat, false, false, false, false, true, 1);
        assertEquals(-GenerationRunner.WATER_JUMP + GenerationRunner.WATER_GRAVITY, wet.ySpeed, 1e-6, "Sonic 1's water jump and gravity");
    }
}
