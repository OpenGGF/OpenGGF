package com.openggf.game.rewind.coverage;

import com.openggf.game.GameId;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TestRewindCoverageAnalyzer {

    /** Collects codec class names from the S3K per-game registry only. */
    private static Set<String> s3kCodecClassNames() {
        return new Sonic3kObjectRegistry().dynamicRewindCodecs().stream()
                .map(DynamicObjectRewindCodec::className)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Collects codec class names from all three per-game registries. */
    @SuppressWarnings("unused")
    private static Set<String> allGameCodecClassNames() {
        return Stream.of(
                new Sonic1ObjectRegistry().dynamicRewindCodecs(),
                new Sonic2ObjectRegistry().dynamicRewindCodecs(),
                new Sonic3kObjectRegistry().dynamicRewindCodecs()
        ).flatMap(List::stream)
         .map(DynamicObjectRewindCodec::className)
         .collect(Collectors.toUnmodifiableSet());
    }

    @Test
    void enumeratesSpawnableObjectsForS3k() {
        RewindCoverageReport report = RewindCoverageAnalyzer.analyze(GameId.S3K, s3kCodecClassNames());
        assertFalse(report.objects().isEmpty(), "must enumerate S3K spawnable objects");
        assertTrue(report.objects().stream()
                .anyMatch(o -> o.className().endsWith("AizLrzRockObjectInstance")),
                "AizLrzRock is a known S3K spawnable object");
    }

    @Test
    void flagsUncapturedFinalScalarField() {
        RewindCoverageReport report = RewindCoverageAnalyzer.analyze(GameId.S3K, s3kCodecClassNames());
        // AutomaticTunnelObjectInstance has `private final int subtype` (no @RewindTransient /
        // @RewindDeferred). It is a concrete AbstractObjectInstance subclass in the LBZ/S3K route.
        // GenericFieldCapturer skips it solely because it is final — this is the gap we detect.
        ObjectCoverage cov = report.objects().stream()
                .filter(o -> o.className().endsWith("AutomaticTunnelObjectInstance"))
                .findFirst().orElseThrow();
        assertTrue(cov.uncapturedFinalScalarFields().contains("subtype"),
                "final non-transient scalar must be reported as uncaptured");
    }

    @Test
    void dynamicObjectWithCodecHasRecreatePath() {
        RewindCoverageReport report = RewindCoverageAnalyzer.analyze(GameId.S3K, s3kCodecClassNames());
        ObjectCoverage cov = report.objects().stream()
                .filter(o -> o.className().endsWith("AizBattleshipInstance"))
                .findFirst().orElseThrow();
        assertTrue(cov.isDynamicSpawnable());
        assertTrue(cov.hasRecreatePath(), "battleship has a dynamic rewind codec on this branch");
    }

    /**
     * POSITIVE test (Task 4): CutsceneKnuxCnz2WallInstance holds a non-transient,
     * non-structural reference to an ObjectInstance ({@code owner} field, type
     * {@code ObjectInstance}). The field name is not in the structural-name set of
     * {@code DefaultObjectRewindPolicies}, so no policy suppresses it.
     * The analyzer must report it as an un-id'd object-ref gap.
     *
     * <p>This test is RED before Task 4 is implemented (unIdObjectRefFields always
     * returns an empty list) and GREEN after implementation.
     */
    @Test
    void flagsNonTransientObjectRefField() {
        RewindCoverageReport report = RewindCoverageAnalyzer.analyze(GameId.S3K, s3kCodecClassNames());
        // CutsceneKnuxCnz2WallInstance has `private final ObjectInstance owner`.
        // The field name "owner" is not in DefaultObjectRewindPolicies.STRUCTURAL_OBJECT_FIELD_NAMES,
        // the field is not @RewindTransient / @RewindDeferred, and there is no exact-field policy.
        // The type ObjectInstance is assignable to ObjectInstance. This must be flagged.
        ObjectCoverage cov = report.objects().stream()
                .filter(o -> o.className().endsWith("CutsceneKnuxCnz2WallInstance"))
                .findFirst().orElseThrow();
        assertTrue(cov.unIdObjectRefFields().contains("owner"),
                "non-transient, non-structural ObjectInstance ref field 'owner' must be reported as un-id'd object-ref gap");
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
        RewindCoverageReport report = RewindCoverageAnalyzer.analyze(GameId.S3K, s3kCodecClassNames());
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
        RewindCoverageReport report = RewindCoverageAnalyzer.analyze(GameId.S3K, s3kCodecClassNames());
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
    void enumerationIncludesRuntimeChildSpawnedClasses() {
        RewindCoverageReport report = RewindCoverageAnalyzer.analyze(GameId.S3K, s3kCodecClassNames());
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
        RewindCoverageReport report = RewindCoverageAnalyzer.analyze(GameId.S3K, s3kCodecClassNames());

        assertTrue(report.objects().stream()
                .anyMatch(o -> o.className().contains("HCZWaterDropObjectInstance$WaterDropChild")),
                "inner-class concrete child HCZWaterDropObjectInstance$WaterDropChild must be enumerated");

        assertTrue(report.objects().stream()
                .anyMatch(o -> o.className().contains("TurboSpikerBadnikInstance$TurboSpikerShellChild")),
                "inner-class concrete child TurboSpikerBadnikInstance$TurboSpikerShellChild must be enumerated");
    }
}

