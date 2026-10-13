package com.openggf;

import com.openggf.mods.*;
import com.openggf.mods.code.ModClassLoaderFactory;
import com.openggf.mods.code.ModFaultBoundary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TestModCompatibilityRuntime {
    @TempDir Path temp;

    @Test
    void actualAudioPublicationReportsTheHealthyWinnerAndExpiresAtTitle() throws Exception {
        var a = descriptor("a", List.of(), Map.of(1, "theme"));
        var b = descriptor("b", List.of(), Map.of(1, "theme"));
        var catalog = new ModCatalog(List.of(a, b), new EffectiveModCatalog(List.of(a, b)));
        var tracks = List.of(track("a"), track("b"));
        var registry = new ModTrackRegistry(tracks.stream().map(track -> new ModAudioTrack(track.key(),
                "audio/theme.wav", false, 0, OptionalLong.empty(), 1, false)).toList());
        var audio = new PreparedAudioSession(tracks, List.of(), Set.of());
        var findings = new ModRuntimeFindingStore();
        try (var subsystem = new ModSubsystem(catalog, findings, (rate, game) ->
                SessionExternalContentView.fromPreparedMusic(PreparedModMusic.build(
                        catalog.effective(), registry, audio, rate), game))) {
            subsystem.beginNormalSession(48000, "s2");

            assertEquals(new TrackKey("b", "theme"), subsystem.sessionView().musicResolver()
                    .resolveStockOverride("s2", 1).orElseThrow().track().key());
            var overlap = findings.compatibilityFor("a").getFirst();
            assertEquals(ModCompatibilityDiagnostic.Level.OBSERVED_OVERLAP, overlap.level());
            assertEquals(List.of("a", "b"), overlap.participants());
            assertEquals("s2/audio:1", overlap.target());
            assertFalse(overlap.blocksPlay());
            assertFalse(audio.isClosed());

            subsystem.returnToTitle();

            assertTrue(findings.compatibilityFor("a").isEmpty());
            assertTrue(audio.isClosed());
        }
    }

    @Test
    void failedAudioPreparationCannotAppearAsAnObservedOverlap() throws Exception {
        var a = descriptor("a", List.of(), Map.of(1, "theme"));
        var b = descriptor("b", List.of(), Map.of(1, "theme"));
        var catalog = new ModCatalog(List.of(a, b), new EffectiveModCatalog(List.of(a, b)));
        var registry = new ModTrackRegistry(List.of(new ModAudioTrack(new TrackKey("a", "theme"),
                "audio/theme.wav", false, 0, OptionalLong.empty(), 1, false)));
        var audio = new PreparedAudioSession(List.of(track("a")), List.of(), Set.of("b"));
        var findings = new ModRuntimeFindingStore();
        try (var subsystem = new ModSubsystem(catalog, findings, (rate, game) ->
                SessionExternalContentView.fromPreparedMusic(PreparedModMusic.build(
                        catalog.effective(), registry, audio, rate), game))) {
            subsystem.beginNormalSession(48000, "s2");

            assertEquals(new TrackKey("a", "theme"), subsystem.sessionView().musicResolver()
                    .resolveStockOverride("s2", 1).orElseThrow().track().key());
            assertTrue(findings.compatibilitySnapshot().isEmpty());
        }
    }

    @Test
    void presentationInvalidationRetiresAudioEvidenceWithoutErasingCatalogEvidence() throws Exception {
        var a = descriptor("a", List.of(), Map.of(1, "theme"));
        var b = descriptor("b", List.of(), Map.of(1, "theme"));
        var catalog = new ModCatalog(List.of(a, b), new EffectiveModCatalog(List.of(a, b)));
        var tracks = List.of(track("a"), track("b"));
        var registry = new ModTrackRegistry(tracks.stream().map(track -> new ModAudioTrack(track.key(),
                "audio/theme.wav", false, 0, OptionalLong.empty(), 1, false)).toList());
        var audio = new PreparedAudioSession(tracks, List.of(), Set.of());
        var findings = new ModRuntimeFindingStore();
        try (var subsystem = new ModSubsystem(catalog, findings, (rate, game) ->
                SessionExternalContentView.fromPreparedMusic(PreparedModMusic.build(
                        catalog.effective(), registry, audio, rate), game))) {
            var declared = new ModCompatibilityDiagnostic(ModCompatibilityDiagnostic.Level.SUSPECTED_INCOMPATIBILITY,
                    "MOD_DECLARED_CONFLICT", "composition", List.of("a", "b"), "Author declaration", List.of("Unverified"));
            findings.upsertCompatibility("catalog", declared);
            subsystem.beginNormalSession(48000, "s2");
            assertTrue(findings.compatibilityFor("a").stream()
                    .anyMatch(report -> report.level() == ModCompatibilityDiagnostic.Level.OBSERVED_OVERLAP));

            subsystem.invalidateSessionPresentation();

            assertEquals(List.of(declared), findings.compatibilitySnapshot());
            assertTrue(audio.isClosed());
        }
    }

    @Test
    void callbackFailureRetainsAttributionAndContextWithoutBlockingTheActiveCombination() throws Exception {
        var a = retainedDescriptor(descriptor("a", List.of()));
        var b = retainedDescriptor(descriptor("b", List.of()));
        var catalog = new ModCatalog(List.of(a, b), new EffectiveModCatalog(List.of(a, b)));
        var findings = new ModRuntimeFindingStore();
        try (var subsystem = new ModSubsystem(catalog, findings, (rate, game) -> SessionExternalContentView.EMPTY);
             var runtime = new ModClassLoaderFactory(getClass().getClassLoader()).create(catalog.effective(), Set.of())) {
            var boundary = subsystem.createFaultBoundary(runtime);
            runtime.installFaultBoundary(boundary);
            runtime.newRegistrationPlan();
            assertEquals(Set.of("a", "b"), runtime.registrationPlans().keySet());
            subsystem.beginNormalSession(48000, "s2");

            var failure = assertThrows(ModFaultBoundary.CallbackAborted.class,
                    () -> boundary.run("a", () -> { throw new IllegalStateException("callback failed"); }));

            assertEquals("a", failure.owner());
            assertEquals(Set.of("a"), failure.disabledOwners());
            assertTrue(boundary.isOwnerAvailable("b"));
            assertFalse(boundary.isOwnerAvailable("a"));
            var diagnostic = findings.compatibilityFor("b").stream()
                    .filter(report -> report.code().equals("MOD_CALLBACK_INTERACTION_UNKNOWN")).findFirst().orElseThrow();
            assertEquals(ModCompatibilityDiagnostic.Level.SUSPECTED_INCOMPATIBILITY, diagnostic.level());
            assertFalse(diagnostic.blocksPlay());
            assertEquals(List.of("a", "b"), diagnostic.participants());
            assertTrue(diagnostic.evidence().stream().anyMatch(line -> line.contains("Failure owner: a")));
            assertTrue(diagnostic.evidence().stream().anyMatch(line -> line.contains("s2")));
            assertTrue(findings.findingsFor("a").stream().anyMatch(finding -> finding.code().equals("MOD_CALLBACK_FAILED")));
        }
    }

    @Test
    void hardDependencyBlockIsVisibleAtBootAndHealthyContentIsStillAvailable() throws Exception {
        var a = descriptor("a", List.of(new ModDependency("b", VersionRange.parse("*"))));
        var b = descriptor("b", List.of(new ModDependency("a", VersionRange.parse("*"))));
        var healthy = descriptor("healthy", List.of());
        var state = new ModState(1, List.of(new ModState.Entry("a", true, 0),
                new ModState.Entry("b", true, 1), new ModState.Entry("healthy", true, 2)));
        var catalog = new EffectiveCatalogBuilder().build(List.of(a, b, healthy), state);
        var findings = new ModRuntimeFindingStore();
        try (var subsystem = new ModSubsystem(catalog, findings, (rate, game) -> SessionExternalContentView.EMPTY)) {
            assertTrue(findings.compatibilityFor("a").stream().anyMatch(ModCompatibilityDiagnostic::blocksPlay));
            assertTrue(findings.compatibilityFor("b").stream().anyMatch(ModCompatibilityDiagnostic::blocksPlay));
            assertTrue(findings.compatibilityFor("healthy").isEmpty());
            assertEquals(List.of("healthy"), subsystem.processCatalog().effective().orderedEnabled().stream()
                    .map(descriptor -> descriptor.manifest().id()).toList());
        }
    }

    @Test
    void anExplicitEmptyStartupSelectionDoesNotAccuseAnUnenabledPack() throws Exception {
        var a = descriptor("a", List.of(new ModDependency("missing", VersionRange.parse("*"))));
        var catalog = new EffectiveCatalogBuilder().build(List.of(a), ModState.EMPTY);
        var editor = PendingModStateEditor.readOnly(ModState.EMPTY, catalog.scanned());
        var findings = new ModRuntimeFindingStore();
        try (var subsystem = new ModSubsystem(catalog, editor, findings,
                (rate, game) -> SessionExternalContentView.EMPTY)) {
            assertEquals(ModEligibility.Status.BLOCKED, subsystem.processCatalog().eligibility().get("a").status());
            assertTrue(findings.compatibilitySnapshot().isEmpty());
            assertFalse(editor.pendingState().entries().stream().anyMatch(ModState.Entry::enabled));
        }
    }

    @Test
    void requiredRegistrationFailureProducesAVerifiedContentBlockWithoutAccusingOtherMods() throws Exception {
        var a = descriptor("a", List.of());
        var b = descriptor("b", List.of());
        var catalog = new ModCatalog(List.of(a, b), new EffectiveModCatalog(List.of(a, b)));
        var findings = new ModRuntimeFindingStore();
        try (var subsystem = new ModSubsystem(catalog, findings, (rate, game) -> SessionExternalContentView.EMPTY)) {
            subsystem.recordRegistrationFailures(Map.of("a", new IllegalStateException("failed transaction")));

            assertTrue(findings.compatibilityFor("a").stream().anyMatch(ModCompatibilityDiagnostic::blocksPlay));
            assertTrue(findings.compatibilityFor("b").isEmpty());
        }
    }

    @Test
    void actualPatchRejectionBlocksOnlyRetiredContentAndMakesNoPairCausalityClaim() throws Exception {
        var a = descriptor("a", List.of());
        var b = descriptor("b", List.of());
        var catalog = new ModCatalog(List.of(a, b), new EffectiveModCatalog(List.of(a, b)));
        var findings = new ModRuntimeFindingStore();
        try (var subsystem = new ModSubsystem(catalog, findings, (rate, game) -> SessionExternalContentView.EMPTY)) {
            subsystem.recordOwnerFailures(Set.of("a"), new IllegalStateException("failed apply"), "MOD_PATCH_APPLY_FAILED");

            var report = findings.compatibilityFor("a").getFirst();
            assertTrue(report.blocksPlayFor("a"));
            assertEquals(List.of("a"), report.participants());
            assertTrue(findings.compatibilityFor("b").isEmpty());
            assertTrue(report.evidence().stream().anyMatch(line -> line.contains("without identifying an incompatible pair")));
        }
    }

    private ModDescriptor descriptor(String id, List<ModDependency> dependencies) throws Exception {
        return descriptor(id, dependencies, Map.of());
    }

    private ModDescriptor descriptor(String id, List<ModDependency> dependencies,
                                     Map<Integer, String> audioOverrides) throws Exception {
        var path = temp.resolve(id + ".jar");
        try (var jar = new JarOutputStream(Files.newOutputStream(path))) { }
        var sha256 = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        var manifest = new ModManifest(1, id, id, SemanticVersion.parse("1.0.0"), List.of("Author"),
                "Test", VersionRange.parse(">=0.7.0 <0.8.0"), ModType.PATCH, "s2", null,
                dependencies, audioOverrides, Map.of(), null, OptionalInt.empty());
        return new ModDescriptor(path, manifest, sha256, false, List.of());
    }

    private static PreparedTrack track(String owner) {
        return new PreparedTrack(new TrackKey(owner, "theme"),
                PcmData.takeOwnership(48000, 2, new short[] {1, 2, 3, 4}), 0, 0, 1, false, "a".repeat(64));
    }

    private ModDescriptor retainedDescriptor(ModDescriptor descriptor) throws Exception {
        var snapshot = com.openggf.io.ModAssetRoot.jar(temp, descriptor.jarPath(),
                com.openggf.io.ModInputLimits.production(), Files.size(descriptor.jarPath()));
        return new ModDescriptor(descriptor.jarPath(), descriptor.manifest(), descriptor.sha256(),
                descriptor.containsCode(), descriptor.findings(), snapshot);
    }
}
