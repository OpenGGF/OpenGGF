package com.openggf.game.rewind.coverage;

import com.openggf.game.GameId;
import com.openggf.level.objects.ObjectInstance;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestRewindCoverageAnalyzer {

    @Test
    void enumeratesSpawnableObjectsForS3k() {
        RewindCoverageReport report = RewindCoverageAnalyzer.analyze(GameId.S3K, Set.of());
        assertFalse(report.objects().isEmpty(), "must enumerate S3K spawnable objects");
        assertTrue(report.objects().stream()
                .anyMatch(o -> o.className().endsWith("AizLrzRockObjectInstance")),
                "AizLrzRock is a known S3K spawnable object");
    }

    @Test
    void flagsFinalScalarFieldWithNoCapturePolicy() throws Exception {
        Field field = FinalScalarFixture.class.getDeclaredField("phase");
        assertTrue(isUncapturedFinalScalar(field),
                "final non-transient scalar must be reported as uncaptured");
    }

    @Test
    void automaticTunnelSubtypeIsNoLongerAFinalScalarGap() {
        RewindCoverageReport report = RewindCoverageAnalyzer.analyze(GameId.S3K, Set.of());
        ObjectCoverage cov = report.objects().stream()
                .filter(o -> o.className().endsWith("AutomaticTunnelObjectInstance"))
                .findFirst().orElseThrow();
        assertFalse(cov.uncapturedFinalScalarFields().contains("subtype"),
                "AutomaticTunnel subtype is mutable/restored now and must not remain a stale final-scalar fixture");
    }

    @Test
    void dynamicObjectWithRewindRecreatableHasRecreatePath() {
        RewindCoverageReport report = RewindCoverageAnalyzer.analyze(GameId.S3K, Set.of());
        ObjectCoverage cov = report.objects().stream()
                .filter(o -> o.className().endsWith("AizBattleshipInstance"))
                .findFirst().orElseThrow();
        assertTrue(cov.isDynamicSpawnable());
        assertTrue(cov.hasRecreatePath(), "battleship has a generic recreate path on this branch");
    }

    /**
     * POSITIVE test (Task 4): an object reference with no transient/deferred/captured
     * policy is an un-id'd object-ref gap.
     */
    @Test
    void flagsNonTransientObjectRefField() throws Exception {
        Field field = ObjectRefFixture.class.getDeclaredField("target");
        assertTrue(isUnIdObjectRef(field),
                "non-transient, non-policy ObjectInstance ref field must be reported as an un-id'd gap");
    }

    @Test
    void capturedCutsceneParentButtonIsNoLongerFlagged() {
        RewindCoverageReport report = RewindCoverageAnalyzer.analyze(GameId.S3K, Set.of());
        ObjectCoverage cov = report.objects().stream()
                .filter(o -> o.className().endsWith("CutsceneKnucklesMhz1Instance"))
                .findFirst().orElseThrow();
        assertFalse(cov.unIdObjectRefFields().contains("parentButton"),
                "parentButton now has an exact captured policy and must not remain a stale object-ref fixture");
    }

    /**
     * NEGATIVE test (Task 4): MhzEndBossHitProxyChild holds only one ObjectInstance ref
     * ({@code parent}), which is annotated {@code @RewindTransient}. The analyzer must
     * NOT flag it as an un-id'd object-ref gap.
     *
     * <p>This test is trivially GREEN before Task 4 (empty list), and must remain GREEN
     * after implementation because the transient annotation suppresses the gap.
     */
    @Test
    void transientObjectRefFieldIsNotFlagged() {
        RewindCoverageReport report = RewindCoverageAnalyzer.analyze(GameId.S3K, Set.of());
        // MhzEndBossHitProxyChild has only `@RewindTransient private final MhzEndBossInstance parent`.
        // No other ObjectInstance-typed fields exist on this class (x and y are primitives).
        ObjectCoverage cov = report.objects().stream()
                .filter(o -> o.className().endsWith("MhzEndBossHitProxyChild"))
                .findFirst().orElseThrow();
        assertTrue(cov.unIdObjectRefFields().isEmpty(),
                "ObjectInstance ref annotated @RewindTransient must NOT be reported as an un-id'd gap");
    }

    /**
     * NEGATIVE test (Task 4): AizEndBossArmChild holds an {@code AizEndBossInstance boss}
     * field that, while not annotated {@code @RewindTransient}, is classified structural by
     * {@code DefaultObjectRewindPolicies} (field name "boss" is in the structural-name set).
     * The analyzer must NOT flag it as an un-id'd object-ref gap.
     */
    @Test
    void structuralObjectRefFieldIsNotFlagged() {
        RewindCoverageReport report = RewindCoverageAnalyzer.analyze(GameId.S3K, Set.of());
        // AizEndBossArmChild has `private final AizEndBossInstance boss`.
        // The name "boss" is in DefaultObjectRewindPolicies.STRUCTURAL_OBJECT_FIELD_NAMES,
        // causing policyForAudit to return TRANSIENT — so it must NOT be in unIdObjectRefFields.
        ObjectCoverage cov = report.objects().stream()
                .filter(o -> o.className().endsWith("AizEndBossArmChild"))
                .findFirst().orElseThrow();
        assertFalse(cov.unIdObjectRefFields().contains("boss"),
                "ObjectInstance ref classified structural by policy must NOT be reported as un-id'd gap");
    }

    @Test
    void exactCapturedObjectRefFieldIsNotFlagged() {
        RewindCoverageReport report = RewindCoverageAnalyzer.analyze(GameId.S3K, Set.of());
        // SpikerTopSpikeChild.parent is an exact CAPTURED field policy. The object-ref
        // codec captures/restores it by ObjectRefId, so it must not be reported as an un-id'd gap.
        ObjectCoverage cov = report.objects().stream()
                .filter(o -> o.className().endsWith("SpikerBadnikInstance$SpikerTopSpikeChild"))
                .findFirst().orElseThrow();
        assertFalse(cov.unIdObjectRefFields().contains("parent"),
                "ObjectInstance ref classified CAPTURED by exact policy must NOT be reported as un-id'd gap");
    }

    @Test
    void enumerationIncludesRuntimeChildSpawnedClasses() {
        RewindCoverageReport report = RewindCoverageAnalyzer.analyze(GameId.S3K, Set.of());
        assertTrue(report.objects().stream()
                .anyMatch(o -> o.className().endsWith("AizShipBombInstance")),
                "child-spawned classes must be enumerated by the classpath scan");
        assertTrue(report.objects().stream()
                .anyMatch(o -> o.className().endsWith("AizEndBossArmChild")),
                "boss-child classes must be enumerated by the classpath scan");
    }

    /**
     * RED→GREEN: inner-class concrete AbstractObjectInstance subclasses must be
     * enumerated by the classpath scan.
     *
     * <p>{@code HCZWaterDropObjectInstance$WaterDropChild} is declared as a
     * {@code private static class} inside {@code HCZWaterDropObjectInstance.java}.
     * Before the fix, the scan only emits one entry per {@code .java} file (the outer
     * class), so the inner child is completely invisible to coverage analysis.
     * After the fix, {@code getDeclaredClasses()} enumeration on the outer class yields
     * the inner child as a separate entry with binary name {@code Outer$Inner}.
     *
     * <p>Similarly, {@code TurboSpikerBadnikInstance$TurboSpikerShellChild} is a
     * gameplay-critical hazard child (implements {@code TouchResponseProvider}) that
     * must appear in coverage so it is not silently dropped on rewind.
     */
    @Test
    void enumerationIncludesInnerClassObjectChildren() {
        RewindCoverageReport report = RewindCoverageAnalyzer.analyze(GameId.S3K, Set.of());

        assertTrue(report.objects().stream()
                .anyMatch(o -> o.className().contains("HCZWaterDropObjectInstance$WaterDropChild")),
                "inner-class concrete child HCZWaterDropObjectInstance$WaterDropChild must be enumerated");

        assertTrue(report.objects().stream()
                .anyMatch(o -> o.className().contains("TurboSpikerBadnikInstance$TurboSpikerShellChild")),
                "inner-class concrete child TurboSpikerBadnikInstance$TurboSpikerShellChild must be enumerated");
    }

    private static boolean isUncapturedFinalScalar(Field field) throws Exception {
        Method method = RewindCoverageAnalyzer.class.getDeclaredMethod("isUncapturedFinalScalar", Field.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, field);
    }

    private static boolean isUnIdObjectRef(Field field) throws Exception {
        Method method = RewindCoverageAnalyzer.class.getDeclaredMethod("isUnIdObjectRef", Field.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, field);
    }

    private static final class FinalScalarFixture {
        @SuppressWarnings("unused")
        private final int phase = 1;
    }

    private static final class ObjectRefFixture {
        @SuppressWarnings("unused")
        private ObjectInstance target;
    }
}
