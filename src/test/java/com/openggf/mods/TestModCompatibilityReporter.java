package com.openggf.mods;

import com.openggf.mods.code.ModContributionReport;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static com.openggf.mods.ModCompatibilityDiagnostic.Level.*;
import static org.junit.jupiter.api.Assertions.*;

class TestModCompatibilityReporter {
    @Test
    void publishedSingletonOverlapNamesTheWinnerWithoutCondemningAdditiveContributions() {
        var reporter = new ModCompatibilityReporter(catalog(List.of(mod("a"), mod("b"))));
        var report = new ModContributionReport(List.of(
                new ModContributionReport.Target("s2", "startup-scene", "b", List.of("a"),
                        "later application wins", "owner recovery", "b:scene", List.of("a:scene")),
                new ModContributionReport.Target("s2", "object:a:box", null, List.of("a", "b"),
                        "owner-scoped additive", "owner recovery")));

        var diagnostics = reporter.preparedContributions(report);

        assertEquals(1, diagnostics.size());
        var overlap = diagnostics.getFirst();
        assertEquals(OBSERVED_OVERLAP, overlap.level());
        assertFalse(overlap.blocksPlay());
        assertEquals(List.of("a", "b"), overlap.participants());
        assertEquals("s2/startup-scene", overlap.target());
        assertTrue(overlap.summary().contains("b"));
        assertTrue(overlap.evidence().stream().anyMatch(line -> line.contains("b:scene")));
        assertTrue(overlap.evidence().stream().anyMatch(line -> line.contains("a:scene")));
        assertTrue(overlap.evidence().stream().anyMatch(line -> line.contains("preparation")));
    }

    @Test
    void audioEvidenceUsesSuccessfulPublicationForOnlyTheReceivingGame() {
        var reporter = new ModCompatibilityReporter(catalog(List.of(mod("a"), mod("b"))));
        var targets = List.of(
                new PreparedModMusic.OverrideTarget("s2", 1, new TrackKey("b", "theme"),
                        List.of(new TrackKey("a", "theme")), "later prepared owner wins", "failed owner excluded"),
                new PreparedModMusic.OverrideTarget("s1", 1, new TrackKey("b", "theme"),
                        List.of(new TrackKey("a", "theme")), "later prepared owner wins", "failed owner excluded"),
                new PreparedModMusic.OverrideTarget("s2", 2, new TrackKey("a", "solo"),
                        List.of(), "later prepared owner wins", "failed owner excluded"));

        var diagnostics = reporter.audioOverrides(targets, "s2");

        assertEquals(1, diagnostics.size());
        assertEquals("s2/audio:1", diagnostics.getFirst().target());
        assertEquals(OBSERVED_OVERLAP, diagnostics.getFirst().level());
        assertEquals(List.of("a", "b"), diagnostics.getFirst().participants());
        assertTrue(diagnostics.getFirst().evidence().stream().anyMatch(line -> line.contains("b:theme")));
    }

    @Test
    void authorDeclarationIsAdvisoryAndAbsentOrDisabledNeighborsProduceNoReport() {
        var a = mod("a", new ModCompositionMetadata(List.of(), List.of(), List.of("b", "absent"), Set.of()));
        var b = mod("b");
        var reporter = new ModCompatibilityReporter(catalog(List.of(a, b)));

        var declared = reporter.catalogDiagnostics();

        assertEquals(1, declared.size());
        assertEquals(SUSPECTED_INCOMPATIBILITY, declared.getFirst().level());
        assertEquals(List.of("a", "b"), declared.getFirst().participants());
        assertFalse(declared.getFirst().blocksPlay());
        assertTrue(new ModCompatibilityReporter(new ModCatalog(List.of(a, b),
                new EffectiveModCatalog(List.of(a)))).catalogDiagnostics().isEmpty());
    }

    @Test
    void hardDependencyCycleIsVerifiedAndIndependentContentStaysEffective() {
        var a = mod("a");
        var b = mod("b");
        var healthy = mod("healthy");
        var reason = new ModEligibility.Reason("DEPENDENCY_CYCLE", "Dependency cycle: a -> b", List.of("a", "b"));
        var catalog = new ModCatalog(List.of(a, b, healthy), new EffectiveModCatalog(List.of(healthy)), Map.of(
                "a", new ModEligibility("a", ModEligibility.Status.BLOCKED, List.of(reason)),
                "b", new ModEligibility("b", ModEligibility.Status.BLOCKED, List.of(reason))));

        var reports = new ModCompatibilityReporter(catalog).catalogDiagnostics();

        assertFalse(reports.isEmpty());
        assertTrue(reports.stream().allMatch(ModCompatibilityDiagnostic::blocksPlay));
        assertTrue(reports.stream().allMatch(report -> report.participants().equals(List.of("a", "b"))));
        assertFalse(reports.stream().anyMatch(report -> report.participants().contains("healthy")));
        assertEquals(List.of(healthy), catalog.effective().orderedEnabled());
    }

    @Test
    void healthyDependencyIsContextForTheBlockButOnlyItsDependentCannotPlay() {
        var a = mod("a");
        var b = mod("b");
        var reason = new ModEligibility.Reason("DEPENDENCY_VERSION_INCOMPATIBLE", "b 1.0.0 does not satisfy >=2.0.0", List.of("b"));
        var catalog = new ModCatalog(List.of(a, b), new EffectiveModCatalog(List.of(b)), Map.of(
                "a", new ModEligibility("a", ModEligibility.Status.BLOCKED, List.of(reason)),
                "b", new ModEligibility("b", ModEligibility.Status.EFFECTIVE, List.of())));

        var blocker = new ModCompatibilityReporter(catalog).catalogDiagnostics().getFirst();

        assertEquals(List.of("a", "b"), blocker.participants());
        assertTrue(blocker.blocksPlayFor("a"));
        assertFalse(blocker.blocksPlayFor("b"));
        assertEquals(List.of("a"), blocker.blockedOwners());
    }

    @Test
    void requiredDependentFailureNamesOnlyTheActualDependencyRelationship() {
        var a = mod("a");
        var dependentManifest = new ModManifest(1, "dependent", "Dependent", SemanticVersion.parse("1.0.0"),
                List.of("Author"), "Test", VersionRange.parse(">=0.7.0 <0.8.0"), ModType.PATCH,
                "s2", null, List.of(new ModDependency("a", VersionRange.parse("*"))), Map.of(), Map.of(),
                null, OptionalInt.empty());
        var dependent = new ModDescriptor(Path.of("dependent.jar"), dependentManifest, "c".repeat(64), false, List.of());
        var reporter = new ModCompatibilityReporter(catalog(List.of(a, dependent, mod("unrelated"))));
        var reports = reporter.registrationFailures(Map.of(
                "a", new IllegalStateException("failed registration"),
                "dependent", new com.openggf.mods.code.ModRegistrationException("dependent", "Dependency registration failed: a")));

        assertEquals(2, reports.size());
        assertTrue(reports.stream().allMatch(ModCompatibilityDiagnostic::blocksPlay));
        assertTrue(reports.stream().anyMatch(report -> report.participants().equals(List.of("a", "dependent"))));
        assertFalse(reports.stream().anyMatch(report -> report.participants().contains("unrelated")));
    }

    @Test
    void anUnretainedRuntimeOwnerHasAVerifiedRequirementFailureWithoutBlockingHealthyOwners() {
        var reporter = new ModCompatibilityReporter(catalog(List.of(mod("a"), mod("healthy"))));

        var report = reporter.runtimeRejections(Map.of("a", new com.openggf.mods.code.ModRuntime.Rejection(
                com.openggf.mods.code.ModRuntime.RejectionReason.HASH_MISMATCH,
                "catalog SHA-256 differs from immutable snapshot"))).getFirst();

        assertEquals(List.of("a"), report.participants());
        assertTrue(report.blocksPlayFor("a"));
        assertFalse(report.blocksPlayFor("healthy"));
        assertTrue(report.evidence().stream().anyMatch(line -> line.contains("HASH_MISMATCH")));
    }

    @Test
    void standaloneFailureDoesNotAccuseSharedStockDecorators() {
        var solo = scopedMod("solo", ModType.STANDALONE, null);
        var shared = scopedMod("shared", ModType.PATCH, "any");
        var nativePatch = mod("native-patch");
        var otherSolo = scopedMod("other-solo", ModType.STANDALONE, null);
        var reporter = new ModCompatibilityReporter(catalog(List.of(solo, shared, nativePatch, otherSolo)));

        var report = reporter.callbackFailure("solo", new IllegalStateException(),
                List.of("solo", "shared", "native-patch", "other-solo"), "solo");

        assertEquals(List.of("solo"), report.participants());
        assertFalse(report.blocksPlay());
    }

    @Test
    void stockFailureKeepsSharedDecoratorsDespiteAStandaloneCodeCollision() {
        var nativePatch = mod("native-patch");
        var shared = scopedMod("shared", ModType.PATCH, "any");
        var standalone = scopedMod("s2", ModType.STANDALONE, null);
        var reporter = new ModCompatibilityReporter(catalog(List.of(nativePatch, shared, standalone)));

        var report = reporter.callbackFailure("native-patch", new IllegalStateException(),
                List.of("native-patch", "shared", "s2"), "s2");

        assertEquals(List.of("native-patch", "shared"), report.participants());
        assertFalse(report.blocksPlay());
    }

    @Test
    void contextIdentityChangesWithEffectiveOrderAndArtifactButNotDiscoveryOrder() {
        var a = mod("a");
        var b = mod("b");
        var forward = new ModCompatibilityReporter(catalog(List.of(a, b))).contextFingerprint();
        assertEquals(forward, new ModCompatibilityReporter(new ModCatalog(List.of(b, a),
                new EffectiveModCatalog(List.of(a, b)))).contextFingerprint());
        assertNotEquals(forward, new ModCompatibilityReporter(catalog(List.of(b, a))).contextFingerprint());
        var changed = new ModDescriptor(a.jarPath(), a.manifest(), "d".repeat(64), false, List.of());
        assertNotEquals(forward, new ModCompatibilityReporter(catalog(List.of(changed, b))).contextFingerprint());
    }

    static ModCatalog catalog(List<ModDescriptor> descriptors) {
        return new ModCatalog(List.copyOf(descriptors), new EffectiveModCatalog(descriptors));
    }

    static ModDescriptor mod(String id) { return mod(id, ModCompositionMetadata.EMPTY); }

    static ModDescriptor mod(String id, ModCompositionMetadata composition) {
        var manifest = new ModManifest(1, id, id, SemanticVersion.parse("1.0.0"), List.of("Author"),
                "Test", VersionRange.parse(">=0.7.0 <0.8.0"), ModType.PATCH, "s2", null,
                List.of(), Map.of(), Map.of(), null, OptionalInt.empty(), composition);
        return new ModDescriptor(Path.of(id + ".jar"), manifest, "a".repeat(64), false, List.of());
    }

    private static ModDescriptor scopedMod(String id, ModType type, String baseGame) {
        var manifest = new ModManifest(1, id, id, SemanticVersion.parse("1.0.0"), List.of("Author"),
                "Test", VersionRange.parse(">=0.7.0 <0.8.0"), type, baseGame, "example.Entry",
                List.of(), Map.of(), Map.of(), null, OptionalInt.empty());
        return new ModDescriptor(Path.of(id + ".jar"), manifest, "b".repeat(64), true, List.of());
    }
}
