package com.openggf.mods;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class TestEffectiveCatalogBuilder {
    @Test
    void compatibleEnabledPatchBecomesEffective() {
        ModDescriptor mod = descriptor("music", "s1", "1.0.0", ">=1.0.0 <2.0.0",
                List.of(), false, null, Map.of(), null, OptionalInt.empty(), List.of());
        ModState state = state(entry("music", true, 0));

        ModCatalog result = new EffectiveCatalogBuilder().build(List.of(mod), state);

        assertEquals(List.of("music"), effectiveIds(result));
        assertEquals(ModEligibility.Status.EFFECTIVE, result.eligibility().get("music").status());
    }

    @Test
    void apiRangeUsesPinnedOneOneCompatibilityWithoutImplicitLowerMajorAcceptance() {
        List<ModDescriptor> mods = List.of(
                patch("canonical", "s1", "1.0.0", ">=1.0.0 <2.0.0"),
                patch("exact-current", "s2", "1.0.0", "1.1.0"),
                patch("future", "s1", "1.0.0", ">=1.2.0 <2.0.0"),
                patch("old-major", "s3k", "1.0.0", "<1.0.0"));

        ModCatalog result = new EffectiveCatalogBuilder().build(
                mods, enabledState("canonical", "exact-current", "future", "old-major"));

        assertEquals(List.of("canonical", "exact-current"), effectiveIds(result));
        assertReason(result, "future", "ENGINE_API_INCOMPATIBLE");
        assertReason(result, "old-major", "ENGINE_API_INCOMPATIBLE");
    }

    @Test
    void phaseThreeActivatesStandaloneAndTrustedCodeButKeepsUntrustedCodeBlocked() {
        ModDescriptor standalone = descriptor("standalone", ModType.STANDALONE, null, "1.0.0", "*",
                List.of(), false, null, Map.of(), null, OptionalInt.empty(), List.of());
        ModDescriptor codeEntry = descriptor("code-entry", "s1", "1.0.0", "*",
                List.of(), false, "example.Mod", Map.of(), null, OptionalInt.empty(), List.of());
        ModDescriptor codeFile = descriptor("code-file", "s1", "1.0.0", "*",
                List.of(), true, null, Map.of(), null, OptionalInt.empty(), List.of());
        ModDescriptor trustedCode = descriptor("trusted-code", "s1", "1.0.0", "*",
                List.of(), true, "example.Mod", Map.of(), null, OptionalInt.empty(), List.of());
        ModDescriptor art = descriptor("art", "s1", "1.0.0", "*",
                List.of(), false, null, Map.of("stock", "art/file.bin"), null, OptionalInt.empty(), List.of());
        ModDescriptor insert = descriptor("insert", "s2", "1.0.0", "*",
                List.of(), false, null, Map.of(), "cpz2", OptionalInt.empty(), List.of());
        ModDescriptor windows = descriptor("windows", "s1", "1.0.0", "*",
                List.of(), false, null, Map.of(), null, OptionalInt.of(1), List.of());

        ModCatalog result = new EffectiveCatalogBuilder().build(
                List.of(standalone, codeEntry, codeFile, trustedCode, art, insert, windows),
                state(entry("standalone", true, 0), entry("code-entry", true, 1),
                        entry("code-file", true, 2),
                        new ModState.Entry("trusted-code", true, 3, true, trustedCode.sha256()),
                        entry("art", true, 4), entry("insert", true, 5), entry("windows", true, 6)));

        assertEquals(ModEligibility.Status.EFFECTIVE,
                result.eligibility().get("standalone").status());
        assertEquals(ModEligibility.Status.EFFECTIVE, result.eligibility().get("code-entry").status());
        assertReason(result, "code-file", "CODE_TRUST_REQUIRED");
        assertEquals("contains code — trust required (press accept twice)",
                reason(result, "code-file", "CODE_TRUST_REQUIRED").message());
        assertEquals(List.of("standalone", "code-entry", "trusted-code", "art", "insert", "windows"),
                effectiveIds(result));
    }

    @Test
    void changedJarHashRevokesCodeEligibilityWithoutAffectingDataOnlyMods() {
        ModDescriptor code = descriptor("code", "s1", "1.0.0", "*", List.of(), true,
                "example.Code", Map.of(), null, OptionalInt.empty(), List.of());
        ModDescriptor data = patch("data", "s1", "1.0.0", "*");
        ModState startup = state(new ModState.Entry("code", true, 0, true, "f".repeat(64)),
                new ModState.Entry("data", true, 1, false, null));

        ModCatalog result = new EffectiveCatalogBuilder().build(List.of(code, data), startup);

        assertReason(result, "code", "CODE_TRUST_REQUIRED");
        assertEquals(List.of("data"), effectiveIds(result));
    }

    @Test
    void insertAfterAcceptsOnlyCanonicalResultsDrivenAnchorsForTheTargetGame() {
        ModDescriptor accepted = descriptor("accepted", "s2", "1.0.0", "*",
                List.of(), false, null, Map.of(), "cpz2", OptionalInt.empty(), List.of());
        ModDescriptor wrongGame = descriptor("wrong-game", "s1", "1.0.0", "*",
                List.of(), false, null, Map.of(), "cpz2", OptionalInt.empty(), List.of());
        ModDescriptor eventChain = descriptor("event-chain", "s2", "1.0.0", "*",
                List.of(), false, null, Map.of(), "scz1", OptionalInt.empty(), List.of());
        ModDescriptor unknown = descriptor("unknown", "s2", "1.0.0", "*",
                List.of(), false, null, Map.of(), "nope1", OptionalInt.empty(), List.of());

        ModCatalog result = new EffectiveCatalogBuilder().build(
                List.of(accepted, wrongGame, eventChain, unknown),
                enabledState("accepted", "wrong-game", "event-chain", "unknown"));

        assertEquals(List.of("accepted"), effectiveIds(result));
        assertReason(result, "wrong-game", "INSERT_AFTER_STOCK_ANCHOR_INVALID");
        assertReason(result, "event-chain", "INSERT_AFTER_STOCK_ANCHOR_INVALID");
        assertReason(result, "unknown", "INSERT_AFTER_STOCK_ANCHOR_INVALID");
    }

    @Test
    void patternWindowBudgetUsesFinalEffectiveOrderAndCascadesWithoutBlockingLaterIndependentMods() {
        List<ModDescriptor> descriptors = new ArrayList<>();
        ModState.Entry[] entries = new ModState.Entry[11];
        for (int index = 0; index < 7; index++) {
            String id = "full-" + index;
            descriptors.add(descriptor(id, "s1", "1.0.0", "*", List.of(), false, null,
                    Map.of(), null, OptionalInt.of(16), List.of()));
            entries[index] = entry(id, true, index);
        }
        ModDescriptor tail = descriptor("tail", "s1", "1.0.0", "*", List.of(), false, null,
                Map.of(), null, OptionalInt.of(15), List.of());
        ModDescriptor overflow = descriptor("overflow", "s1", "1.0.0", "*", List.of(), false,
                null, Map.of(), null, OptionalInt.of(2), List.of());
        ModDescriptor dependent = withDependencies("dependent", "s1", dependency("overflow", "*"));
        ModDescriptor laterDefault = patch("later-default", "s1", "1.0.0", "*");
        descriptors.addAll(List.of(tail, overflow, dependent, laterDefault));
        entries[7] = entry("tail", true, 7);
        entries[8] = entry("overflow", true, 8);
        entries[9] = entry("dependent", true, 9);
        entries[10] = entry("later-default", true, 10);

        ModCatalog result = new EffectiveCatalogBuilder().build(descriptors, state(entries));

        List<String> expected = new ArrayList<>(
                java.util.stream.IntStream.range(0, 7).mapToObj(i -> "full-" + i).toList());
        expected.add("tail");
        expected.add("later-default");
        assertEquals(expected, effectiveIds(result));
        assertReason(result, "overflow", "PATTERN_WINDOW_BUDGET_EXCEEDED");
        assertTrue(reason(result, "overflow", "PATTERN_WINDOW_BUDGET_EXCEEDED")
                .message().contains("overflow"));
        assertReason(result, "dependent", "DEPENDENCY_BLOCKED");
        assertEquals(ModEligibility.Status.EFFECTIVE, result.eligibility().get("later-default").status());

        // Persisted order, not discovery order, decides which competing owner fits.
        ModDescriptor sixteen = descriptor("sixteen", "s1", "1.0.0", "*", List.of(), false,
                null, Map.of(), null, OptionalInt.of(16), List.of());
        ModDescriptor one = patch("one", "s1", "1.0.0", "*");
        List<ModDescriptor> base = java.util.stream.IntStream.range(0, 7)
                .mapToObj(i -> descriptor("base-" + i, "s1", "1.0.0", "*", List.of(), false,
                        null, Map.of(), null, OptionalInt.of(16), List.of())).toList();
        List<ModDescriptor> reordered = new ArrayList<>(base);
        reordered.add(sixteen);
        reordered.add(one);
        List<ModState.Entry> reorderedState = new ArrayList<>();
        for (int i = 0; i < 7; i++) reorderedState.add(entry("base-" + i, true, i));
        reorderedState.add(entry("one", true, 7));
        reorderedState.add(entry("sixteen", true, 8));
        ModCatalog ordered = new EffectiveCatalogBuilder().build(reordered,
                state(reorderedState.toArray(ModState.Entry[]::new)));
        assertTrue(effectiveIds(ordered).contains("one"));
        assertReason(ordered, "sixteen", "PATTERN_WINDOW_BUDGET_EXCEEDED");
    }

    @Test
    void warningsDoNotBlockButErrorsDoAndStaticFindingsRemainUnchanged() {
        ModFinding warning = finding(ModFindingSeverity.WARNING, "AUDIO_OVERRIDE_CONFLICT");
        ModFinding error = finding(ModFindingSeverity.ERROR, "AUDIO_MANIFEST_INVALID");
        ModDescriptor warned = descriptor("warned", "s1", "1.0.0", "*",
                List.of(), false, null, Map.of(), null, OptionalInt.empty(), List.of(warning));
        ModDescriptor invalid = descriptor("invalid", "s1", "1.0.0", "*",
                List.of(), false, null, Map.of(), null, OptionalInt.empty(), List.of(error));

        ModCatalog result = new EffectiveCatalogBuilder().build(
                List.of(warned, invalid), enabledState("warned", "invalid"));

        assertEquals(List.of("warned"), effectiveIds(result));
        assertReason(result, "invalid", "DESCRIPTOR_INVALID");
        assertEquals(List.of("AUDIO_MANIFEST_INVALID"), findingCodes(invalid));
        assertEquals(List.of("AUDIO_MANIFEST_INVALID"),
                findingCodes(resultDescriptor(result, "invalid")));
    }

    @Test
    void rebuildingFromACompletedCatalogIsIdempotentAndPreservesStaticFindings() {
        ModDescriptor effective = descriptor("effective", "s1", "1.0.0", "*",
                List.of(), false, null, Map.of(), null, OptionalInt.empty(), List.of(
                        finding(ModFindingSeverity.WARNING, "AUDIO_OVERRIDE_CONFLICT")));
        ModDescriptor blocked = withDependencies("blocked", "s1", dependency("absent", "*"));
        ModState startup = enabledState("effective", "blocked");

        ModCatalog first = new EffectiveCatalogBuilder().build(List.of(effective, blocked), startup);
        ModCatalog second = new EffectiveCatalogBuilder().build(first.scanned(), startup);

        assertEquals(effectiveIds(first), effectiveIds(second));
        assertEquals(first.eligibility(), second.eligibility());
        assertEquals(List.of("AUDIO_OVERRIDE_CONFLICT"), findingCodes(resultDescriptor(second, "effective")));
        assertTrue(findingCodes(resultDescriptor(second, "blocked")).isEmpty());
    }

    @Test
    void missingStateAndExplicitlyDisabledModsRemainScannedButNotEffective() {
        ModDescriptor newlyDiscovered = patch("new-mod", "s1", "1.0.0", "*");
        ModDescriptor disabled = patch("disabled", "s2", "1.0.0", "*");

        ModCatalog result = new EffectiveCatalogBuilder().build(
                List.of(newlyDiscovered, disabled), state(entry("disabled", false, 0)));

        assertEquals(2, result.scanned().size());
        assertTrue(effectiveIds(result).isEmpty());
        assertEquals(ModEligibility.Status.DISABLED, result.eligibility().get("new-mod").status());
        assertReason(result, "disabled", "DISABLED");
    }

    @Test
    void dependencyFailuresCoverMissingVersionWrongBaseDisabledAndTransitiveBlocking() {
        ModDescriptor base = patch("base", "s1", "1.2.0", "*");
        ModDescriptor disabledBase = patch("disabled-base", "s1", "1.0.0", "*");
        ModDescriptor missing = withDependencies("missing", "s1",
                dependency("absent", "*"));
        ModDescriptor wrongVersion = withDependencies("wrong-version", "s1",
                dependency("base", ">=2.0.0"));
        ModDescriptor wrongBase = withDependencies("wrong-base", "s2",
                dependency("base", "*"));
        ModDescriptor disabledDep = withDependencies("disabled-dep", "s1",
                dependency("disabled-base", "*"));
        ModDescriptor transitive = withDependencies("transitive", "s1",
                dependency("missing", "*"));

        ModCatalog result = new EffectiveCatalogBuilder().build(
                List.of(base, disabledBase, missing, wrongVersion, wrongBase, disabledDep, transitive),
                state(entry("base", true, 0), entry("disabled-base", false, 1),
                        entry("missing", true, 2), entry("wrong-version", true, 3),
                        entry("wrong-base", true, 4), entry("disabled-dep", true, 5),
                        entry("transitive", true, 6)));

        assertEquals(List.of("base"), effectiveIds(result));
        assertReason(result, "missing", "DEPENDENCY_MISSING");
        assertReason(result, "wrong-version", "DEPENDENCY_VERSION_INCOMPATIBLE");
        assertReason(result, "wrong-base", "DEPENDENCY_BASE_GAME_MISMATCH");
        assertReason(result, "disabled-dep", "DEPENDENCY_DISABLED");
        ModEligibility.Reason propagated = reason(result, "transitive", "DEPENDENCY_BLOCKED");
        assertTrue(propagated.message().contains("missing"));
        assertEquals(List.of("missing"), propagated.relatedModIds());
    }

    @Test
    void selfAndMultiNodeCyclesBlockEveryParticipantWithoutHanging() {
        ModDescriptor self = withDependencies("self", "s1", dependency("self", "*"));
        ModDescriptor alpha = withDependencies("alpha", "s1", dependency("beta", "*"));
        ModDescriptor beta = withDependencies("beta", "s1", dependency("gamma", "*"));
        ModDescriptor gamma = withDependencies("gamma", "s1", dependency("alpha", "*"));

        ModCatalog result = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> new EffectiveCatalogBuilder().build(List.of(self, alpha, beta, gamma),
                        enabledState("self", "alpha", "beta", "gamma")));

        assertEquals(List.of("self"), reason(result, "self", "DEPENDENCY_CYCLE").relatedModIds());
        for (String id : List.of("alpha", "beta", "gamma")) {
            assertEquals(List.of("alpha", "beta", "gamma"),
                    reason(result, id, "DEPENDENCY_CYCLE").relatedModIds());
        }
        assertTrue(effectiveIds(result).isEmpty());
    }

    @Test
    void stableTopologicalOrderPlacesDependenciesFirstAndPreservesIndependentStateOrder() {
        ModDescriptor dependent = withDependencies("dependent", "s1", dependency("base", "*"));
        ModDescriptor independentFirst = patch("independent-first", "s2", "1.0.0", "*");
        ModDescriptor base = patch("base", "s1", "1.0.0", "*");
        ModDescriptor independentLast = patch("independent-last", "s3k", "1.0.0", "*");

        ModCatalog result = new EffectiveCatalogBuilder().build(
                List.of(base, independentLast, dependent, independentFirst),
                state(entry("dependent", true, 0), entry("independent-first", true, 1),
                        entry("base", true, 2), entry("independent-last", true, 3)));

        assertEquals(List.of("independent-first", "base", "dependent", "independent-last"),
                effectiveIds(result));
    }

    @Test
    void invalidScanEntriesAreRetainedAndResultCollectionsAreImmutable() {
        InvalidModEntry invalid = new InvalidModEntry(Path.of("broken.jar"), List.of(
                finding(ModFindingSeverity.ERROR, "INVALID_JAR")));
        ModDescriptor valid = patch("valid", "s1", "1.0.0", "*");

        ModCatalog result = new EffectiveCatalogBuilder().build(
                List.of(invalid, valid), enabledState("valid"));

        assertSame(invalid, result.scanned().get(0));
        assertThrows(UnsupportedOperationException.class, () -> result.scanned().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> result.effective().orderedEnabled().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.eligibility().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> result.eligibility().get("valid").reasons().clear());
    }

    @Test
    void duplicateIdsFailClosedEvenWithoutScannerErrorFindings() {
        ModDescriptor first = patch("duplicate", "s1", "1.0.0", "*");
        ModDescriptor second = patch("duplicate", "s1", "1.1.0", "*");

        ModCatalog result = new EffectiveCatalogBuilder().build(
                List.of(first, second), enabledState("duplicate"));

        assertTrue(effectiveIds(result).isEmpty());
        assertReason(result, "duplicate", "DUPLICATE_MOD_ID");
    }

    @Test
    void dependenciesOnInvalidOrDuplicateOwnersAreBlockedRatherThanReportedMissing() {
        ModDescriptor invalid = descriptor("invalid-owner", "s1", "1.0.0", "*",
                List.of(), false, null, Map.of(), null, OptionalInt.empty(), List.of(
                        finding(ModFindingSeverity.ERROR, "AUDIO_MANIFEST_INVALID")));
        ModDescriptor invalidDependent = withDependencies("invalid-dependent", "s1",
                dependency("invalid-owner", "*"));
        ModDescriptor duplicateOne = patch("duplicate-owner", "s1", "1.0.0", "*");
        ModDescriptor duplicateTwo = patch("duplicate-owner", "s1", "1.1.0", "*");
        ModDescriptor duplicateDependent = withDependencies("duplicate-dependent", "s1",
                dependency("duplicate-owner", "*"));

        ModCatalog result = new EffectiveCatalogBuilder().build(
                List.of(invalid, invalidDependent, duplicateOne, duplicateTwo, duplicateDependent),
                enabledState("invalid-owner", "invalid-dependent", "duplicate-owner", "duplicate-dependent"));

        assertReason(result, "invalid-dependent", "DEPENDENCY_BLOCKED");
        assertReason(result, "duplicate-dependent", "DEPENDENCY_BLOCKED");
    }

    @Test
    void nearLimitAcyclicChainTerminatesWithoutUsingTheCallStack() {
        int count = 10_000;
        List<ModDescriptor> descriptors = new ArrayList<>(count);
        ModState.Entry[] entries = new ModState.Entry[count];
        for (int index = 0; index < count; index++) {
            String id = chainId(index);
            descriptors.add(index == 0 ? patch(id, "s1", "1.0.0", "*")
                    : withDependencies(id, "s1", dependency(chainId(index - 1), "*")));
            entries[index] = entry(id, true, count - index - 1);
        }
        Collections.reverse(descriptors);

        ModCatalog result = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> new EffectiveCatalogBuilder().build(descriptors, state(entries)));

        assertEquals(128, result.effective().orderedEnabled().size());
        assertEquals(chainId(0), effectiveIds(result).get(0));
        assertEquals(chainId(127), effectiveIds(result).get(127));
        assertReason(result, chainId(128), "PATTERN_WINDOW_BUDGET_EXCEEDED");
        assertReason(result, chainId(count - 1), "DEPENDENCY_BLOCKED");
    }

    @Test
    void structuralDependencyFailuresRemainBlockedWhenModIsDisabledOrNew() {
        ModDescriptor installed = patch("installed", "s1", "1.0.0", "*");
        ModDescriptor disabledMissing = withDependencies("disabled-missing", "s1",
                dependency("absent", "*"));
        ModDescriptor newWrongVersion = withDependencies("new-wrong-version", "s1",
                dependency("installed", ">=2.0.0"));
        ModDescriptor newValid = patch("new-valid", "s1", "1.0.0", "*");

        ModCatalog result = new EffectiveCatalogBuilder().build(
                List.of(installed, disabledMissing, newWrongVersion, newValid),
                state(entry("installed", true, 0), entry("disabled-missing", false, 1)));

        assertReason(result, "disabled-missing", "DEPENDENCY_MISSING");
        assertEquals(ModEligibility.Status.BLOCKED, result.eligibility().get("disabled-missing").status());
        assertReason(result, "new-wrong-version", "DEPENDENCY_VERSION_INCOMPATIBLE");
        assertEquals(ModEligibility.Status.BLOCKED, result.eligibility().get("new-wrong-version").status());
        assertEquals(ModEligibility.Status.DISABLED, result.eligibility().get("new-valid").status());
    }

    @Test
    void independentSccsHaveRepeatableEncounterOrder() {
        LinkedHashMap<String, ModDescriptor> descriptors = new LinkedHashMap<>();
        descriptors.put("alpha", withDependencies("alpha", "s1", dependency("beta", "*")));
        descriptors.put("beta", withDependencies("beta", "s1", dependency("alpha", "*")));
        descriptors.put("charlie", withDependencies("charlie", "s1", dependency("delta", "*")));
        descriptors.put("delta", withDependencies("delta", "s1", dependency("charlie", "*")));

        List<String> first = new ArrayList<>(new ModDependencyGraph(descriptors).cycleParticipants().keySet());
        List<String> second = new ArrayList<>(new ModDependencyGraph(descriptors).cycleParticipants().keySet());

        assertEquals(List.of("charlie", "delta", "alpha", "beta"), first);
        assertEquals(first, second);
    }

    @Test
    void nearLimitSingleCycleKeepsSccProcessingLinearAndSharesParticipantMetadata() {
        int count = 10_000;
        List<ModDescriptor> descriptors = new ArrayList<>(count);
        ModState.Entry[] entries = new ModState.Entry[count];
        for (int index = 0; index < count; index++) {
            String id = chainId(index);
            descriptors.add(withDependencies(id, "s1",
                    dependency(chainId((index + 1) % count), "*")));
            entries[index] = entry(id, true, index);
        }

        ModCatalog result = assertTimeoutPreemptively(Duration.ofSeconds(3),
                () -> new EffectiveCatalogBuilder().build(descriptors, state(entries)));

        assertTrue(result.effective().orderedEnabled().isEmpty());
        assertEquals(count, result.eligibility().size());
        ModEligibility.Reason first = reason(result, chainId(0), "DEPENDENCY_CYCLE");
        ModEligibility.Reason middle = reason(result, chainId(count / 2), "DEPENDENCY_CYCLE");
        ModEligibility.Reason last = reason(result, chainId(count - 1), "DEPENDENCY_CYCLE");
        assertEquals(count, first.relatedModIds().size());
        assertEquals(chainId(0), first.relatedModIds().get(0));
        assertEquals(chainId(count - 1), first.relatedModIds().get(count - 1));
        assertSame(first.relatedModIds(), middle.relatedModIds());
        assertSame(first.relatedModIds(), last.relatedModIds());
    }

    @Test
    void catalogEligibilityConstructorRejectsMalformedNullAndMismatchedEntries() {
        ModEligibility valid = new ModEligibility("valid", ModEligibility.Status.EFFECTIVE, List.of());
        assertDoesNotThrow(() -> new ModCatalog(List.of(), EffectiveModCatalog.EMPTY));
        assertDoesNotThrow(() -> new ModCatalog(List.of(), EffectiveModCatalog.EMPTY, Map.of("valid", valid)));
        assertThrows(IllegalArgumentException.class, () -> new ModCatalog(
                List.of(), EffectiveModCatalog.EMPTY, Map.of("INVALID", valid)));
        assertThrows(IllegalArgumentException.class, () -> new ModCatalog(
                List.of(), EffectiveModCatalog.EMPTY, Map.of("other", valid)));

        LinkedHashMap<String, ModEligibility> nullKey = new LinkedHashMap<>();
        nullKey.put(null, valid);
        assertThrows(NullPointerException.class,
                () -> new ModCatalog(List.of(), EffectiveModCatalog.EMPTY, nullKey));
        LinkedHashMap<String, ModEligibility> nullValue = new LinkedHashMap<>();
        nullValue.put("valid", null);
        assertThrows(NullPointerException.class,
                () -> new ModCatalog(List.of(), EffectiveModCatalog.EMPTY, nullValue));
    }

    private static ModDescriptor descriptor(String id, String baseGame, String version, String apiRange,
                                            List<ModDependency> dependencies, boolean containsCode,
                                            String entrypoint, Map<String, String> artOverrides,
                                            String insertAfter, OptionalInt patternWindows,
                                            List<ModFinding> findings) {
        ModManifest manifest = new ModManifest(1, id, id, SemanticVersion.parse(version),
                List.of("Author"), "Description", VersionRange.parse(apiRange), ModType.PATCH,
                baseGame, entrypoint, dependencies, Map.of(), artOverrides, insertAfter, patternWindows);
        return new ModDescriptor(Path.of(id + ".jar"), manifest, "0".repeat(64), containsCode, findings);
    }

    private static ModDescriptor descriptor(String id, ModType type, String baseGame,
                                            String version, String apiRange,
                                            List<ModDependency> dependencies, boolean containsCode,
                                            String entrypoint, Map<String, String> artOverrides,
                                            String insertAfter, OptionalInt patternWindows,
                                            List<ModFinding> findings) {
        ModManifest manifest = new ModManifest(1, id, id, SemanticVersion.parse(version),
                List.of("Author"), "Description", VersionRange.parse(apiRange), type,
                baseGame, entrypoint, dependencies, Map.of(), artOverrides, insertAfter, patternWindows);
        return new ModDescriptor(Path.of(id + ".jar"), manifest, "0".repeat(64), containsCode, findings);
    }

    private static ModDescriptor patch(String id, String baseGame, String version, String apiRange) {
        return descriptor(id, baseGame, version, apiRange, List.of(), false, null,
                Map.of(), null, OptionalInt.empty(), List.of());
    }

    private static ModDescriptor withDependencies(String id, String baseGame, ModDependency... dependencies) {
        return descriptor(id, baseGame, "1.0.0", "*", List.of(dependencies), false, null,
                Map.of(), null, OptionalInt.empty(), List.of());
    }

    private static ModDependency dependency(String id, String range) {
        return new ModDependency(id, VersionRange.parse(range));
    }

    private static ModFinding finding(ModFindingSeverity severity, String code) {
        return new ModFinding(severity, code, code, null);
    }

    private static String chainId(int index) {
        return "m" + String.format("%05d", index);
    }

    private static ModState enabledState(String... ids) {
        ModState.Entry[] entries = new ModState.Entry[ids.length];
        for (int index = 0; index < ids.length; index++) entries[index] = entry(ids[index], true, index);
        return state(entries);
    }

    private static ModState state(ModState.Entry... entries) {
        return new ModState(1, List.of(entries));
    }

    private static ModState.Entry entry(String id, boolean enabled, int order) {
        return new ModState.Entry(id, enabled, order);
    }

    private static List<String> effectiveIds(ModCatalog result) {
        return result.effective().orderedEnabled().stream()
                .map(descriptor -> descriptor.manifest().id()).toList();
    }

    private static void assertReason(ModCatalog result, String id, String code) {
        assertNotNull(reason(result, id, code));
    }

    private static ModEligibility.Reason reason(ModCatalog result,
                                                String id, String code) {
        return result.eligibility().get(id).reasons().stream()
                .filter(value -> value.code().equals(code)).findFirst().orElseThrow();
    }

    private static ModDescriptor resultDescriptor(ModCatalog result, String id) {
        return result.scanned().stream().filter(ModDescriptor.class::isInstance)
                .map(ModDescriptor.class::cast).filter(value -> value.manifest().id().equals(id))
                .findFirst().orElseThrow();
    }

    private static List<String> findingCodes(ModDescriptor descriptor) {
        return descriptor.findings().stream().map(ModFinding::code).toList();
    }
}
