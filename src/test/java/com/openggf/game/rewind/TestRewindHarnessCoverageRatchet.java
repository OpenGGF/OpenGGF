package com.openggf.game.rewind;

import com.openggf.game.rewind.coverage.ObjectClasspathScan;
import com.openggf.game.rewind.RewindRoundTripHarness.RoundTripSweepResult;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage ratchet: the number of classes that PASS the round-trip harness
 * must never drop below {@link #RATCHET_FLOOR}.
 *
 * <h2>Intent</h2>
 * This test makes the uplift work resumable and regression-safe: once a class
 * is made headlessly-probeable (by harness extension, constructor changes, or
 * added codec), it stays green. The floor only ever moves UP.
 *
 * <h2>How to raise the floor</h2>
 * Implement harness extensions or construction strategies until the sweep reports
 * a higher probed count, verify all gate tests green, then raise
 * {@link #RATCHET_FLOOR} to the new value (with a comment recording the date and
 * what changed).
 *
 * <h2>Bucket categorization</h2>
 * For each of the 783 spawnable concrete object classes, the probe result is one of:
 * <ul>
 *   <li>{@code no-codec} – no dynamic rewind codec registered; class is immediately
 *       {@code Unprobed("no dynamic recreate path")}. These objects are correctly
 *       dropped on restore; testing them would produce a spurious count-mismatch.</li>
 *   <li>{@code no-probe-ctor} – has a codec but the harness could not construct the
     *       object headlessly (all supported constructor strategies failed with
 *       {@code NoSuchMethodError} or a thrown exception). Common: child objects
 *       whose only constructors take a parent instance or non-ObjectSpawn arguments.</li>
 *   <li>{@code parent-dependent} – has a codec, constructed successfully, but
 *       {@code recreate()} returned {@code null} in isolation because it re-links a
 *       live parent object not present in the standalone ObjectManager. Distinct from
 *       {@code no-probe-ctor}.</li>
 *   <li>{@code other-failure} – has a codec but construction or the round-trip threw
 *       for a reason other than the above two.</li>
 *   <li>{@code passed} – full round-trip succeeded (counted against the ratchet).</li>
 *   <li>{@code hard-failure} – {@code CountMismatch} or {@code ScalarMismatch}; these
 *       are genuine bugs, surfaced as hard assertion failures by the parametrized gate
 *       in {@code TestEveryObjectRewindRoundTrip}.</li>
 * </ul>
 */
public class TestRewindHarnessCoverageRatchet {

    /**
     * Minimum probed count that must never regress.
     *
     * <p>History:
     * <ul>
     *   <li>2026-06-18: initial baseline = 20 (first sweep, 783 total classes;
     *       547 no-codec, 212 no-probe-ctor, 4 parent-dependent)</li>
     *   <li>2026-06-18: raised to 31 after adding (ObjectSpawn,boolean) strategy (S5)
     *       and (ObjectSpawn,ParentType) parent-construct strategy (S6); S6 promoted
     *       19 from no-probe-ctor: 6 to passed, 13 newly exposed parent-dependent.</li>
     *   <li>2026-06-18: raised to 37 after adding PARENT_SEED_TABLE +
     *       PARENT_SPAWN_OBJECT_IDS + tryRoundTripWithSeededParent for S3K CNZ/Gumball/
     *       SpikedLog/CutsceneKnuckles and S2 Turtloid, Buzzer, ARZBoss, CNZBoss families.
     *       Parents that spawn children in ctor (Balkiry, CPZBoss) excluded as honest
     *       ceiling — they need a live session (6 classes remain parent-dependent).</li>
     *   <li>2026-06-19: raised to 38 after Phase-2 codec-deletion batch 3 moved
     *       SmallMetalPformChild, CnzLightsFlashChild, and HCZWaterDropChild onto
     *       RewindRecreatable generic recreate without losing round-trip coverage.</li>
     *   <li>2026-06-19: raised to 39 after Phase-2 codec-deletion batch 7 moved
     *       HczEndBossInstance onto RewindRecreatable generic recreate and supplied
     *       deterministic harness configuration for its character-dependent constructor.</li>
 *   <li>2026-06-19: raised to 40 after exact-spawn codecs began recreating
 *       inside the restore-time ObjectConstructionContext, allowing AizEndBossInstance
 *       to use constructor-time ObjectServices under the harness.</li>
 *   <li>2026-06-19: raised to 43 after adding (ObjectSpawn,ObjectServices,int)
 *       harness construction with inert render services, then deleting the S1/S2/S3K
 *       points popup dynamic codecs.</li>
 *   <li>2026-06-19: raised to 47 after adding RewindRecreatable-only primitive
 *       constructor probes and deleting four AIZ2 self-contained transient child
 *       codecs.</li>
 *   <li>2026-06-19: raised to 53 after session-level verification deleted six
 *       self-contained S3K transient/effect codecs.</li>
 *   <li>2026-06-19: raised to 56 after session-level verification deleted three
 *       S3K release-sequence/effect dynamic codecs.</li>
 *   <li>2026-06-19: raised to 59 after session-level verification deleted three
 *       S3K self-contained transient/countdown/badnik-child dynamic codecs.</li>
 *   <li>2026-06-19: raised to 63 after session-level verification deleted four
 *       S3K self-contained exact-spawn dynamic codecs.</li>
 *   <li>2026-06-19: raised to 65 after session-level verification deleted two
 *       S3K session-level/no-ref exact-spawn dynamic codecs.</li>
 *   <li>2026-06-19: raised to 68 after session-level verification deleted three
 *       S3K exact-spawn end egg capsule dynamic codecs.</li>
 *   <li>2026-06-19: raised to 76 after session-level verification deleted the
 *       remaining S3K end egg capsule codecs, three private S3K badnik projectile
 *       codecs, and three no-ref S3K exact-spawn dynamic codecs.</li>
 *   <li>2026-06-19: raised to 79 after session-level verification deleted three
 *       S3K no-ref nested transient child dynamic codecs.</li>
 * </ul>
     *
     * <p>Floor only moves UP. When raising: update this comment, run the full
     * gate suite, confirm probed count >= new floor before committing.
     */
    static final int RATCHET_FLOOR = 79;

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    /**
     * Sweeps all spawnable object classes, counts the Passed results, and
     * asserts the count is at or above the ratchet floor.
     *
     * <p>Also prints a bucket summary (no-codec / no-probe-ctor / parent-dependent /
     * other / passed) so the honest ceiling is visible without needing a separate
     * analysis run.
     */
    @Test
    void probedCountMeetsRatchetFloor() {
        Path srcMain = ObjectClasspathScan.findSourceRoot();
        if (srcMain == null) {
            System.out.println(
                    "[ratchet] Not in source checkout — skipping coverage ratchet check.");
            return;
        }

        List<ObjectClasspathScan.SourceClass> classes;
        try {
            classes = ObjectClasspathScan.findConcreteObjectInstances(srcMain);
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan object classes", e);
        }

        int total = classes.size();
        int passed = 0;
        int noCodec = 0;
        int noProbeCtor = 0;
        int parentDependent = 0;
        int otherFailure = 0;
        int countMismatch = 0;
        int scalarMismatch = 0;
        List<String> passedNames = new ArrayList<>();
        List<String> noProbCtorNames = new ArrayList<>();
        List<String> parentDependentNames = new ArrayList<>();

        for (ObjectClasspathScan.SourceClass sc : classes) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(sc.fqn());
            switch (result) {
                case RoundTripSweepResult.Passed p -> {
                    passed++;
                    passedNames.add(sc.fqn());
                }
                case RoundTripSweepResult.Unprobed u -> {
                    String reason = u.reason();
                    if (reason.startsWith("no dynamic recreate path")) {
                        noCodec++;
                    } else if (reason.startsWith("parent-dependent")) {
                        parentDependent++;
                        parentDependentNames.add(sc.fqn() + ": " + reason);
                    } else if (reason.contains("NoSuchMethodError")
                            || reason.contains("No probe-compatible constructor")) {
                        noProbeCtor++;
                        noProbCtorNames.add(sc.fqn());
                    } else {
                        otherFailure++;
                    }
                }
                case RoundTripSweepResult.CountMismatch cm -> countMismatch++;
                case RoundTripSweepResult.ScalarMismatch sm -> scalarMismatch++;
            }
        }

        System.out.println("[ratchet] total=" + total
                + " passed=" + passed
                + " no-codec=" + noCodec
                + " no-probe-ctor=" + noProbeCtor
                + " parent-dependent=" + parentDependent
                + " other-failure=" + otherFailure
                + " count-mismatch=" + countMismatch
                + " scalar-mismatch=" + scalarMismatch);

        if (!passedNames.isEmpty()) {
            System.out.println("[ratchet] passed classes (" + passedNames.size() + "):");
            passedNames.stream().sorted().forEach(n -> System.out.println("  + " + n));
        }

        if (!noProbCtorNames.isEmpty()) {
            System.out.println("[ratchet] no-probe-ctor classes (sample, first 20):");
            noProbCtorNames.stream().limit(20).forEach(n -> System.out.println("  ? " + n));
        }

        if (!parentDependentNames.isEmpty()) {
            System.out.println("[ratchet] parent-dependent classes (" + parentDependentNames.size() + "):");
            parentDependentNames.forEach(n -> System.out.println("  P " + n));
        }

        assertTrue(passed >= RATCHET_FLOOR,
                "Probed (Passed) count " + passed + " is below ratchet floor " + RATCHET_FLOOR
                        + ". Coverage regressed — check for removed codecs or harness construction"
                        + " strategy changes. Raise RATCHET_FLOOR only by improving coverage, never"
                        + " to paper over a regression. Floor = " + RATCHET_FLOOR + ".");
    }
}
