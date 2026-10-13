package com.openggf.mods;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.mods.ModCompatibilityDiagnostic.Level.OBSERVED_OVERLAP;
import static com.openggf.mods.ModCompatibilityDiagnostic.Level.SUSPECTED_INCOMPATIBILITY;
import static com.openggf.mods.ModCompatibilityDiagnostic.Level.VERIFIED_BLOCKER;
import static org.junit.jupiter.api.Assertions.*;

class TestModCompatibilityDiagnostics {
    @Test
    void participantIdentityIsNormalizedAndCollectionsAreDetached() {
        var participants = new ArrayList<>(List.of(" beta ", "alpha", "beta"));
        var evidence = new ArrayList<>(List.of("Both register the same music target"));
        var diagnostic = new ModCompatibilityDiagnostic(OBSERVED_OVERLAP, "TARGET_OVERLAP",
                "s1:music:0x81", participants, "The later override wins", evidence);

        participants.clear();
        evidence.clear();

        assertEquals(List.of("alpha", "beta"), diagnostic.participants());
        assertEquals(List.of("Both register the same music target"), diagnostic.evidence());
        assertThrows(UnsupportedOperationException.class, () -> diagnostic.participants().add("gamma"));
        assertThrows(UnsupportedOperationException.class, () -> diagnostic.evidence().clear());
    }

    @Test
    void diagnosticRejectsMissingIdentityAndRenderFields() {
        assertThrows(IllegalArgumentException.class, () -> diagnostic(OBSERVED_OVERLAP, "lowercase",
                "target", List.of("alpha"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> diagnostic(OBSERVED_OVERLAP, "OVERLAP",
                " ", List.of("alpha"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> diagnostic(OBSERVED_OVERLAP, "OVERLAP",
                "target", List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> diagnostic(OBSERVED_OVERLAP, "OVERLAP",
                "target", List.of("alpha", " "), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ModCompatibilityDiagnostic(OBSERVED_OVERLAP,
                "OVERLAP", "target", List.of("alpha"), " ", List.of()));
        assertThrows(NullPointerException.class, () -> new ModCompatibilityDiagnostic(null,
                "OVERLAP", "target", List.of("alpha"), "Summary", List.of()));
    }

    @Test
    void onlyVerifiedBlockersBlockPlay() {
        var overlap = diagnostic(OBSERVED_OVERLAP, "OVERLAP", "target", List.of("alpha"), List.of());
        var suspected = diagnostic(SUSPECTED_INCOMPATIBILITY, "SUSPECTED", "target", List.of("alpha"), List.of());
        var verified = diagnostic(VERIFIED_BLOCKER, "FAILED_LAUNCH", "target", List.of("alpha"), List.of());

        assertFalse(overlap.blocksPlay());
        assertFalse(suspected.blocksPlay());
        assertTrue(verified.blocksPlay());
        assertEquals(List.of(), overlap.blockedOwners());
        assertEquals(List.of(), suspected.blockedOwners());
        assertEquals(List.of("alpha"), verified.blockedOwners());
        assertFalse(overlap.blocksPlayFor("alpha"));
        assertFalse(suspected.blocksPlayFor("alpha"));
        assertTrue(verified.blocksPlayFor("alpha"));
    }

    @Test
    void healthyDependencyIsVisibleAsContextWithoutBeingBlocked() {
        var store = new ModRuntimeFindingStore();
        var unavailableDependent = new ModCompatibilityDiagnostic(VERIFIED_BLOCKER, "DEPENDENCY_UNAVAILABLE",
                "alpha:dependency:beta", List.of("alpha", "beta"), "Alpha cannot use the required beta version",
                List.of("Beta is healthy but its version does not satisfy alpha"), List.of("alpha"));
        store.upsertCompatibility("catalog", unavailableDependent);

        assertEquals(List.of(unavailableDependent), store.compatibilityFor("alpha"));
        assertEquals(List.of(unavailableDependent), store.compatibilityFor("beta"));
        assertTrue(store.compatibilityFor("alpha").getFirst().blocksPlayFor("alpha"));
        assertFalse(store.compatibilityFor("beta").getFirst().blocksPlayFor("beta"));
        assertFalse(unavailableDependent.blocksPlayFor("unrelated"));
    }

    @Test
    void blockedOwnersAreNormalizedDetachedAndImmutable() {
        var blockedOwners = new ArrayList<>(List.of(" gamma ", "alpha", "gamma"));
        var diagnostic = new ModCompatibilityDiagnostic(VERIFIED_BLOCKER, "FAILED_LAUNCH", "launch",
                List.of("gamma", "beta", "alpha"), "The selected scenes cannot initialize", List.of(), blockedOwners);
        blockedOwners.clear();

        assertEquals(List.of("alpha", "gamma"), diagnostic.blockedOwners());
        assertThrows(UnsupportedOperationException.class, () -> diagnostic.blockedOwners().add("beta"));
        assertTrue(diagnostic.blocksPlayFor("alpha"));
        assertTrue(diagnostic.blocksPlayFor("gamma"));
        assertFalse(diagnostic.blocksPlayFor("beta"));
    }

    @Test
    void blockedOwnersMustBeParticipantsAndOnlyVerifiedBlockersMayNameThem() {
        assertThrows(IllegalArgumentException.class, () -> new ModCompatibilityDiagnostic(VERIFIED_BLOCKER,
                "FAILED_LAUNCH", "launch", List.of("alpha", "beta"), "Launch failed", List.of(), List.of("gamma")));
        assertThrows(IllegalArgumentException.class, () -> new ModCompatibilityDiagnostic(VERIFIED_BLOCKER,
                "FAILED_LAUNCH", "launch", List.of("alpha", "beta"), "Launch failed", List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ModCompatibilityDiagnostic(VERIFIED_BLOCKER,
                "FAILED_LAUNCH", "launch", List.of("alpha", "beta"), "Launch failed", List.of(), List.of(" ")));
        assertThrows(IllegalArgumentException.class, () -> new ModCompatibilityDiagnostic(OBSERVED_OVERLAP,
                "TARGET_OVERLAP", "launch", List.of("alpha", "beta"), "Overlap observed", List.of(), List.of("alpha")));
        assertThrows(IllegalArgumentException.class, () -> new ModCompatibilityDiagnostic(SUSPECTED_INCOMPATIBILITY,
                "SUSPECTED", "launch", List.of("alpha", "beta"), "Failure suspected", List.of(), List.of("alpha")));
    }

    @Test
    void mergingIndependentEvidencePreservesTheExplicitBlockedOwnerSubset() {
        var store = new ModRuntimeFindingStore();
        var first = new ModCompatibilityDiagnostic(VERIFIED_BLOCKER, "DEPENDENCY_UNAVAILABLE", "alpha:dependency:beta",
                List.of("alpha", "beta"), "Alpha cannot launch", List.of("Catalog evidence"), List.of("alpha"));
        var second = new ModCompatibilityDiagnostic(VERIFIED_BLOCKER, "DEPENDENCY_UNAVAILABLE", "alpha:dependency:beta",
                List.of("beta", "alpha"), "Alpha cannot launch", List.of("Runtime evidence"), List.of("alpha"));
        store.replaceCompatibility("catalog", List.of(first));
        store.replaceCompatibility("runtime", List.of(second));

        assertEquals(1, store.compatibilitySnapshot().size());
        var merged = store.compatibilitySnapshot().getFirst();
        assertEquals(List.of("Catalog evidence", "Runtime evidence"), merged.evidence());
        assertEquals(List.of("alpha"), merged.blockedOwners());
        assertTrue(merged.blocksPlayFor("alpha"));
        assertFalse(merged.blocksPlayFor("beta"));
    }

    @Test
    void differentBlockedOwnerSubsetsHaveIndependentDiagnosticIdentities() {
        var store = new ModRuntimeFindingStore();
        var alphaBlocked = new ModCompatibilityDiagnostic(VERIFIED_BLOCKER, "FAILED_LAUNCH", "launch",
                List.of("alpha", "beta"), "Alpha cannot launch", List.of("Alpha evidence"), List.of("alpha"));
        var betaBlocked = new ModCompatibilityDiagnostic(VERIFIED_BLOCKER, "FAILED_LAUNCH", "launch",
                List.of("alpha", "beta"), "Beta cannot launch", List.of("Beta evidence"), List.of("beta"));
        store.upsertCompatibility("runtime", alphaBlocked);
        store.upsertCompatibility("runtime", betaBlocked);

        assertEquals(List.of(alphaBlocked, betaBlocked), store.compatibilitySnapshot());
    }

    @Test
    void independentTargetsLevelsAndParticipantSetsSurviveTheSameCode() {
        var store = new ModRuntimeFindingStore();
        var first = diagnostic(OBSERVED_OVERLAP, "TARGET_OVERLAP", "music:0x81",
                List.of("alpha", "beta"), List.of("First target"));
        var second = diagnostic(OBSERVED_OVERLAP, "TARGET_OVERLAP", "music:0x82",
                List.of("alpha", "beta"), List.of("Second target"));
        var otherPair = diagnostic(OBSERVED_OVERLAP, "TARGET_OVERLAP", "music:0x81",
                List.of("alpha", "gamma"), List.of("Other participants"));
        var suspected = diagnostic(SUSPECTED_INCOMPATIBILITY, "TARGET_OVERLAP", "music:0x81",
                List.of("alpha", "beta"), List.of("Runtime evidence"));

        store.replaceCompatibility("catalog", List.of(first, second, otherPair, suspected));
        var updated = diagnostic(OBSERVED_OVERLAP, "TARGET_OVERLAP", "music:0x81",
                List.of("beta", "alpha"), List.of("Updated evidence"));
        store.upsertCompatibility("catalog", updated);

        assertEquals(List.of(updated, second, otherPair, suspected), store.compatibilityFor("alpha"));
        assertEquals(List.of(updated, second, suspected), store.compatibilityFor("beta"));
        assertEquals(List.of(otherPair), store.compatibilityFor("gamma"));
        assertTrue(store.compatibilityFor("other").isEmpty());
    }

    @Test
    void producerReplacementAndOwnerFindingsRemainIndependent() {
        var store = new ModRuntimeFindingStore();
        var overlap = diagnostic(OBSERVED_OVERLAP, "TARGET_OVERLAP", "music:0x81",
                List.of("alpha", "beta"), List.of("Static claim"));
        var suspect = diagnostic(SUSPECTED_INCOMPATIBILITY, "CALLBACK_FAILED", "world:update",
                List.of("alpha"), List.of("Callback threw"));
        var finding = new ModFinding(ModFindingSeverity.WARNING, "AUDIO_FALLBACK", "Stock music was used", null);

        store.replaceCompatibility("catalog", List.of(overlap));
        store.replaceCompatibility("runtime", List.of(suspect));
        store.replaceOwner("alpha", List.of(finding));
        store.replaceOwner("alpha", List.of());
        assertEquals(List.of(overlap, suspect), store.compatibilitySnapshot());

        store.replaceOwner("alpha", List.of(finding));
        store.replaceCompatibility("catalog", List.of());
        assertEquals(List.of(suspect), store.compatibilitySnapshot());
        assertEquals(List.of(finding), store.findingsFor("alpha"));
    }

    @Test
    void identicalReportsMergeEvidenceWithoutLosingIndependentProducerOwnership() {
        var store = new ModRuntimeFindingStore();
        var catalog = diagnostic(OBSERVED_OVERLAP, "TARGET_OVERLAP", "music:0x81",
                List.of("alpha", "beta"), List.of("Catalog evidence", "Shared evidence", "Shared evidence"));
        var registration = diagnostic(OBSERVED_OVERLAP, "TARGET_OVERLAP", "music:0x81",
                List.of("beta", "alpha"), List.of("Shared evidence", "Registration evidence"));
        store.replaceCompatibility("catalog", List.of(catalog));
        store.replaceCompatibility("registration", List.of(registration));

        assertEquals(1, store.compatibilitySnapshot().size());
        assertEquals(List.of("Catalog evidence", "Shared evidence", "Registration evidence"),
                store.compatibilitySnapshot().getFirst().evidence());

        store.replaceCompatibility("catalog", List.of());
        assertEquals(List.of(registration), store.compatibilitySnapshot());
    }

    @Test
    void unchangedContextPreservesReportsAndChangedContextClearsOnlyCompatibility() {
        var store = new ModRuntimeFindingStore();
        var report = diagnostic(SUSPECTED_INCOMPATIBILITY, "CALLBACK_FAILED", "world:update",
                List.of("alpha"), List.of("Observed during this enabled set"));
        var finding = new ModFinding(ModFindingSeverity.WARNING, "AUDIO_FALLBACK", "Stock music was used", null);
        store.beginCompatibilityContext("alpha@digest-one");
        store.upsertCompatibility("runtime", report);
        store.replaceOwner("alpha", List.of(finding));

        store.beginCompatibilityContext("alpha@digest-one");
        assertEquals("alpha@digest-one", store.compatibilityContext());
        assertEquals(List.of(report), store.compatibilitySnapshot());

        store.beginCompatibilityContext("alpha@digest-two");
        assertEquals("alpha@digest-two", store.compatibilityContext());
        assertTrue(store.compatibilitySnapshot().isEmpty());
        assertEquals(List.of(finding), store.findingsFor("alpha"));
    }

    @Test
    void returnedSnapshotsAreImmutableAndDoNotChangeAfterLaterUpdates() {
        var store = new ModRuntimeFindingStore();
        var first = diagnostic(OBSERVED_OVERLAP, "TARGET_OVERLAP", "music:0x81",
                List.of("alpha", "beta"), List.of("Evidence"));
        store.upsertCompatibility("catalog", first);
        var ownerSnapshot = store.compatibilityFor("alpha");
        var fullSnapshot = store.compatibilitySnapshot();
        store.replaceCompatibility("catalog", List.of());

        assertEquals(List.of(first), ownerSnapshot);
        assertEquals(List.of(first), fullSnapshot);
        assertThrows(UnsupportedOperationException.class, ownerSnapshot::clear);
        assertThrows(UnsupportedOperationException.class, fullSnapshot::clear);
    }

    @Test
    void storeEvictsTheOldestNonBlockerBeforeARecordedBlocker() {
        var store = new ModRuntimeFindingStore();
        var blocker = diagnostic(VERIFIED_BLOCKER, "FAILED_LAUNCH", "launch",
                List.of("alpha"), List.of("The selected scene cannot initialize"));
        store.upsertCompatibility("runtime", blocker);
        for (int target = 0; target < 512; target++) {
            store.upsertCompatibility("catalog", diagnostic(OBSERVED_OVERLAP, "TARGET_OVERLAP",
                    "target:" + target, List.of("alpha", "beta"), List.of()));
        }

        assertEquals(512, store.compatibilitySnapshot().size());
        assertTrue(store.compatibilitySnapshot().contains(blocker));
        assertFalse(store.compatibilitySnapshot().stream().anyMatch(d -> d.target().equals("target:0")));
        assertTrue(store.compatibilitySnapshot().stream().anyMatch(d -> d.target().equals("target:511")));
    }

    @Test
    void blockerOnlyOverflowStillHasAnOldestFirstBound() {
        var store = new ModRuntimeFindingStore();
        for (int target = 0; target < 513; target++) {
            store.upsertCompatibility("runtime", diagnostic(VERIFIED_BLOCKER, "FAILED_LAUNCH",
                    "target:" + target, List.of("alpha"), List.of()));
        }

        assertEquals(512, store.compatibilitySnapshot().size());
        assertEquals("target:1", store.compatibilitySnapshot().getFirst().target());
        assertEquals("target:512", store.compatibilitySnapshot().getLast().target());
    }

    @Test
    void invalidReplacementCannotErasePreviouslyPublishedReports() {
        var store = new ModRuntimeFindingStore();
        var report = diagnostic(OBSERVED_OVERLAP, "TARGET_OVERLAP", "target",
                List.of("alpha", "beta"), List.of());
        store.replaceCompatibility("catalog", List.of(report));
        var invalid = new ArrayList<ModCompatibilityDiagnostic>();
        invalid.add(null);

        assertThrows(NullPointerException.class, () -> store.replaceCompatibility("catalog", invalid));
        assertThrows(IllegalArgumentException.class, () -> store.upsertCompatibility(" ", report));
        assertThrows(IllegalArgumentException.class, () -> store.beginCompatibilityContext(" "));
        assertEquals(List.of(report), store.compatibilitySnapshot());
    }

    private static ModCompatibilityDiagnostic diagnostic(ModCompatibilityDiagnostic.Level level,
                                                        String code, String target,
                                                        List<String> participants, List<String> evidence) {
        return new ModCompatibilityDiagnostic(level, code, target, participants, "Compatibility report", evidence);
    }
}
