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
 *   <li>2026-06-19: raised to 83 after session-level verification deleted four
 *       S2 self-contained projectile/effect dynamic codecs.</li>
 *   <li>2026-06-19: raised to 86 after deleting three S2 scalar-only
 *       child projectile/hazard dynamic codecs.</li>
 *   <li>2026-06-19: raised to 88 after deleting two S2 scalar-only
 *       boss hazard dynamic codecs.</li>
 *   <li>2026-06-19: raised to 90 after deleting two S2 no-object-ref
 *       dynamic codecs.</li>
 *   <li>2026-06-19: raised to 91 after deleting the S2
 *       BossExplosionObjectInstance dynamic codec.</li>
 *   <li>2026-06-19: raised to 92 after deleting the S2
 *       BadnikProjectileInstance dynamic codec.</li>
 *   <li>2026-06-19: raised to 93 after deleting the S2
 *       CPZBossFallingPart dynamic codec.</li>
 *   <li>2026-06-19: raised to 94 after deleting the S2
 *       SpikyBlockSpikeInstance dynamic codec.</li>
 *   <li>2026-06-19: raised to 95 after deleting the S2
 *       MonitorContentsObjectInstance dynamic codec.</li>
 *   <li>2026-06-19: raised to 96 after deleting the S2
 *       BombPrizeObjectInstance dynamic codec.</li>
 *   <li>2026-06-19: raised to 99 after deleting three S2 scalar/no-reference
 *       session-only codecs for ResultsScreenObjectInstance, RingPrizeObjectInstance,
 *       and MTZBossLaser.</li>
 *   <li>2026-06-19: raised to 102 after deleting three S1 scalar/no-reference
 *       projectile codecs for BombShrapnel, Cannonball, and NewtronMissile.</li>
 *   <li>2026-06-19: raised to 105 after deleting three S1 scalar/no-reference
 *       projectile/effect codecs for BuzzBomberMissile, BuzzBomberMissileDissolve,
 *       and CrabmeatProjectile.</li>
 *   <li>2026-06-19: raised to 106 after deleting the S1
 *       ResultsScreen dynamic codec.</li>
 *   <li>2026-06-19: raised to 107 after deleting the S3K ICZ end-boss
 *       escape-ship dynamic codec.</li>
 *   <li>2026-06-19: raised to 111 after deleting six S1 scalar/no-reference
 *       exact-spawn/session-only codecs; EndingEmeralds, ExplosionItem,
 *       MonitorPowerUp, and Ring are now headlessly probeable, while
 *       CollapsingFloor and SpikedBallChain remain honest no-probe session-tail
 *       classes.</li>
 *   <li>2026-06-19: raised to 112 after deleting the S1 BossExplosionObjectInstance
 *       codec and S3K MhzEndBossPaletteFadeController codec; palette fade is now
 *       headlessly probeable through RewindRecreatable.</li>
 *   <li>2026-06-19: raised to 113 after deleting the S3K CorkeyShotChild
 *       codec; generic recreate uses a placeholder script and compact restore
 *       reapplies the captured int[] script.</li>
 *   <li>2026-06-19: raised to 114 after deleting the S3K
 *       MhzEndBossDefeatFragmentChild codec; compact restore now reapplies the
 *       parent-derived subtype/xVel state.</li>
 *   <li>2026-06-19: raised to 115 after deleting the S3K Madmole
 *       SideDrillChild codec; generic recreate derives facingLeft from the
 *       captured spawn render flag.</li>
 *   <li>2026-06-19: raised to 116 after deleting the S3K
 *       S3kResultsScreenObjectInstance codec; compact restore reapplies
 *       constructor state and required playerRef identity resolution.</li>
 *   <li>2026-06-19: raised to 117 after deleting the S3K
 *       MhzEndBossRobotnikShipFlameInstance codec and extending the harness
 *       parent-seed retry path to cover capture-time required object refs and
 *       single-argument parent constructors.</li>
 *   <li>2026-06-19: raised to 118 after deleting the S3K
 *       Mgz2ResultsScreenObjectInstance codec; generic recreate now preserves
 *       the concrete MGZ2 results subclass.</li>
 *   <li>2026-06-19: raised to 119 after deleting the S2 DEZ
 *       BarrierWall codec; generic recreate now relinks Eggman's structural
 *       barrierWall back-reference.</li>
 *   <li>2026-06-20: raised to 120 after deleting the S3K Buggernaut
 *       baby codec; generic recreate now relinks the transient live
 *       Buggernaut parent.</li>
 *   <li>2026-06-20: raised to 123 after deleting the S3K Orbinaut orb,
 *       Ribot active child, and Star Pointer orbiting point codecs; generic
 *       recreate now relinks their transient live badnik parents.</li>
 *   <li>2026-06-21: raised to 133 after the dynamic codec inventory reached
 *       zero and the parent-dependent bucket was split into graph-covered
 *       families versus explicit session-tail work.</li>
 *   <li>2026-06-21: raised to 144 after seeding the remaining parent-linked
 *       other-failure tail in the round-trip harness, including S2 HTZ boss
 *       children, S3K badnik/cutscene/miniboss children, and the SS-entry
 *       flash child.</li>
 *   <li>2026-06-22: raised to 147 after adding generic recreate coverage for
 *       the S3K invisible block and invisible hurt block family.</li>
 *   <li>2026-06-22: raised to 148 after adding generic recreate coverage for
 *       the S2 invisible block.</li>
 *   <li>2026-06-22: raised to 149 after adding generic recreate coverage for
 *       the S1 invisible barrier.</li>
 *   <li>2026-06-22: raised to 153 after adding generic recreate coverage for
 *       the first S1 badnik head batch.</li>
 *   <li>2026-06-22: raised to 158 after adding generic recreate coverage for
 *       the second S1 badnik head batch.</li>
 * </ul>
     *
     * <p>Floor only moves UP. When raising: update this comment, run the full
     * gate suite, confirm probed count >= new floor before committing.
     */
    static final int RATCHET_FLOOR = 158;

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
