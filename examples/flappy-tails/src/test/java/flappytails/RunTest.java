package flappytails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openggf.mods.scene.SceneStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Whole flights, without a screen: the autopilot proves the course fair, and the rules hold. */
class RunTest {
    private static Run fly(Mode mode, long seed, int firstGate, int gates, List<Run.Event> seen) {
        Run run = new Run(mode, seed, firstGate, firstGate > 0);
        Autopilot pilot = new Autopilot();
        while (run.phase() != Run.Phase.OVER && run.score() < gates && run.ticks() < 100_000) {
            run.tick(pilot.decide(run));
            if (seen != null) seen.addAll(run.events());
        }
        return run;
    }

    @Test
    void theAutopilotClearsTwoLapsOnEveryCourseItTries() {
        for (long seed = 1; seed <= 12; seed++) {
            Run run = fly(Mode.CLASSIC, seed * 7919, 0, 100, null);
            assertEquals(100, run.score(), "seed " + seed + " crashed into " + run.crashCause());
        }
    }

    @Test
    void readyWaitsForTheFirstFlap() {
        Run run = new Run(Mode.CLASSIC, 1, 0, false);
        int scroll = run.course.scrollX();
        for (int i = 0; i < 300; i++) run.tick(false);
        assertEquals(Run.Phase.READY, run.phase());
        assertEquals(scroll, run.course.scrollX(), "the course waits");
        run.tick(true);
        assertEquals(Run.Phase.FLYING, run.phase());
    }

    @Test
    void neverFlappingHitsTheGroundAndEndsAClassicFlight() {
        Run run = new Run(Mode.CLASSIC, 1, 0, false);
        run.tick(true);
        while (run.phase() == Run.Phase.FLYING) run.tick(false);
        assertEquals(Run.Phase.CRASHED, run.phase());
        assertEquals("THE GROUND", run.crashCause());
        while (run.phase() != Run.Phase.OVER) run.tick(false);
    }

    @Test
    void inSonicRulesRingsAbsorbAHitAndScatter() {
        Run run = new Run(Mode.SONIC, 1, 0, false);
        run.tick(true);
        run.debugSetRings(40);
        List<Run.Event> seen = new ArrayList<>();
        while (run.phase() == Run.Phase.FLYING) {
            run.tick(false);
            seen.addAll(run.events());
        }
        assertEquals(Run.Phase.HURT, run.phase(), "the ground knocked Tails back instead of ending the run");
        assertEquals(0, run.rings());
        assertEquals(32, run.spills().size(), "Obj_Bouncing_Ring scatters at most 32");
        assertTrue(seen.stream().anyMatch(e -> e.type() == Run.Type.SFX && e.value() == Sounds.SFX_RING_LOSS));
        assertEquals(Run.INVULNERABLE_FRAMES, run.invulnerable());
        // The first pair fans up and out, mirrored: angle $88 at speed shift 2.
        Run.Spill a = run.spills().get(0);
        Run.Spill b = run.spills().get(1);
        assertTrue(a.yVel < -0x300 && b.yVel == a.yVel);
        assertEquals(a.xVel - run.speed(), -(b.xVel - run.speed()));
    }

    @Test
    void aLapOfTheTourTurnsTailsSuperAndHeSmashesPillars() {
        List<Run.Event> seen = new ArrayList<>();
        Run run = fly(Mode.CLASSIC, 99, 4 * Zone.GATES, 14, seen);
        assertTrue(seen.stream().anyMatch(e -> e.type() == Run.Type.SUPER), "fifty gates: Super Tails");
        assertTrue(seen.stream().anyMatch(e -> e.type() == Run.Type.MUSIC && e.value() == Sounds.MUS_INVINCIBLE));
        assertTrue(seen.stream().anyMatch(e -> e.type() == Run.Type.SMASH), "the autopilot aims for pillars while Super");
        assertFalse(run.phase() == Run.Phase.CRASHED || run.phase() == Run.Phase.OVER);
    }

    @Test
    void recordsKeepBestsAndRejectAFutureFormat() {
        Map<String, String> files = new HashMap<>();
        SceneStorage storage = new SceneStorage() {
            public Optional<String> read(String n) { return Optional.ofNullable(files.get(n)); }
            public boolean write(String n, String t) { files.put(n, t); return true; }
            public boolean delete(String n) { return files.remove(n) != null; }
            public List<String> list() { return List.copyOf(files.keySet()); }
        };
        Records records = Records.load(storage);
        Run run = fly(Mode.CLASSIC, 5, 0, 23, null);
        assertTrue(records.finish(run));
        records.save(storage);
        Records again = Records.load(storage);
        assertEquals(23, again.best(Mode.CLASSIC));
        assertEquals(2, again.furthestZone(), "gate 23 is in Hydrocity");
        assertFalse(again.finish(fly(Mode.CLASSIC, 6, 0, 3, null)), "a lower score is not a best");
        files.put(Records.FILE, "formatVersion=9\nbest.CLASSIC=999\n");
        assertEquals(0, Records.load(storage).best(Mode.CLASSIC), "an unknown format is not trusted");
    }
}
