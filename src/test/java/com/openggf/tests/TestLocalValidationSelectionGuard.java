package com.openggf.tests;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Keep costly qualification products explicit while preserving actual local behavior witnesses. */
class TestLocalValidationSelectionGuard {
    @Test
    void expensiveProductsStayManualAndTheirBehaviorWitnessesStayRoutine() throws Exception {
        Map<String, String> contracts = Map.ofEntries(
                Map.entry("mods.code.TestSitarHeroCharts#sonic1CatalogueAllRolesAndDifficulties",
                        "sonic1RepresentativePartsAndAbsentRoles"),
                Map.entry("mods.code.TestSitarHeroCharts#sonic2CatalogueAllRolesAndDifficulties",
                        "sonic2RepresentativeNoiseAndAbsentPsg"),
                Map.entry("mods.code.TestSitarHeroCharts#sonic3kCatalogueAllAvailableRolesAndDifficulties",
                        "sonic3kRepresentativePartsAndAbsentPsg"),
                Map.entry("mods.code.TestSitarHeroS2SongCatalogue#shortProductionPreparationCorroboratesAllSelectedRolesAndAbsentPsg",
                        "representativeProductionPreparationsCoverPitchedNoiseAbsentAndTempoParts"),
                Map.entry("tests.TestS3kGumballPachinkoRewardMechanics#actualFiniteSupportedProductsReplayEveryMechanism",
                        "actualNativeContactAndNaturalLifecycleReplaysItsCompleteWorldTwice"),
                Map.entry("tests.TestS3kSanctuaryPedestalLifecycle#everySupportedDirectSceneOutcomeReturnsAndReplaysWithoutSaved2",
                        "nativeOutcomeOwnersReturnADeclaredDirectSceneWithoutInventingSaved2"),
                Map.entry("tests.TestS3kSanctuaryOriginReturnLifecycle#everySupportedOutcomeUsesTheRealPedestalResultsAndOriginOwners",
                        "nativeResultOutcomeOwnersReturnToTheActualSaved2Origin"),
                Map.entry("tests.TestS3kHpzSanctuaryLifecycleProduction#ceremonyUsesItsLiteralNativeResourcesAndReplaysBeforeActiveAndReleasedWorlds",
                        "representativeCeremoniesKeepLiteralResourcesAndAllWorldBoundaries"),
                Map.entry("tests.TestLrzSszCheckpointRoutes#everyPostPreservesConfiguredOwnersAcrossTwoDeaths",
                        "eachAuthoredPostRetainsPhysicalContactAndBothDeathBoundaries"),
                Map.entry("tests.TestSozCheckpointReloadProduction#otherPlacedPostsActivateAndReload",
                        "eachRemainingNativePostRetainsContactAndReload"));
        for (var contract : contracts.entrySet()) {
            String[] owner = contract.getKey().split("#");
            Class<?> type = Class.forName("com.openggf." + owner[0], false, getClass().getClassLoader());
            assertFalse(isAudit(type.getAnnotationsByType(Tag.class)), "mixed owners must retain local tests: " + type);
            assertTrue(isAudit(method(type, owner[1]).getAnnotationsByType(Tag.class)), contract.getKey());
            assertFalse(isAudit(method(type, contract.getValue()).getAnnotationsByType(Tag.class)),
                    "the real behavior witness must remain in smoke: " + contract.getValue());
        }
    }

    @Test
    void nativeCheckpointWitnessesCoverEveryPlacementWithoutConfigurationProducts() {
        var allLrz = TestLrzSszCheckpointRoutes.allProducts().map(row -> row.get()[0])
                .collect(Collectors.toSet());
        var nativeLrz = TestLrzSszCheckpointRoutes.nativePosts().toList();
        assertEquals(allLrz, nativeLrz.stream().map(row -> row.get()[0]).collect(Collectors.toSet()));
        assertEquals(allLrz.size(), nativeLrz.size(), "one actual death/reload witness for each authored post");

        var allSoz = TestSozCheckpointReloadProduction.remainingPosts().map(row ->
                Arrays.asList(Arrays.copyOf(row.get(), 4))).collect(Collectors.toSet());
        var nativeSoz = TestSozCheckpointReloadProduction.nativeRemainingPosts().toList();
        assertEquals(allSoz, nativeSoz.stream().map(row -> Arrays.asList(Arrays.copyOf(row.get(), 4)))
                .collect(Collectors.toSet()));
        assertEquals(allSoz.size(), nativeSoz.size(), "native placement coverage must not collapse to the first post");
    }

    @Test
    void checkpointDeathPilotsRetainAllDonorsCharactersAndRosterOwnership() {
        // Private providers keep their public test API small; inspect their declared rows without ROM boots.
        for (Class<?> type : List.of(TestFbzCheckpointRoutes.class, TestMhzCheckpointRoutes.class,
                TestDezCheckpointRoutes.class)) {
            try {
                Method provider = type.getDeclaredMethod("firstIncomingCases");
                provider.setAccessible(true);
                @SuppressWarnings("unchecked")
                var rows = ((java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments>)
                        provider.invoke(null)).map(org.junit.jupiter.params.provider.Arguments::get).toList();
                assertEquals(Set.of("off", "s1", "s2"), rows.stream().map(row -> row[5]).collect(Collectors.toSet()));
                assertEquals(Set.of(0, 1), rows.stream().map(row -> row[1]).collect(Collectors.toSet()));
                Set<String> teams = rows.stream().map(row -> row[0].toString()).collect(Collectors.toSet());
                assertTrue(teams.containsAll(Set.of("SONIC", "TAILS", "KNUCKLES", "SONIC_TAILS")), type.getName());
            } catch (ReflectiveOperationException error) {
                fail("Cannot inspect the actual checkpoint provider: " + type.getName(), error);
            }
        }
    }

    private static Method method(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods()).filter(method -> method.getName().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError("Missing selection contract: " + type.getName() + "#" + name));
    }

    private static boolean isAudit(Tag[] tags) {
        return Arrays.stream(tags).anyMatch(tag -> tag.value().equals("exhaustive-audit"));
    }
}
