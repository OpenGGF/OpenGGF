package com.openggf.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestZoneProgressionPlan {

    private static final int MTZ = 7;
    private static final int SCZ = 8;
    private static final int MOD_11 = 11;
    private static final int MOD_12 = 12;

    @Test
    void linearPlanAdvancesAcrossVariableActCountsAndThenCredits() {
        int[] actCounts = {2, 3, 1, 4};
        ZoneProgressionPlan.ZoneTopology topology = topology(actCounts);

        for (int zone = 0; zone < actCounts.length; zone++) {
            for (int act = 0; act < actCounts[zone]; act++) {
                ZoneProgressionPlan.ProgressionResult expected = legacyNext(actCounts, zone, act);
                assertEquals(expected, ZoneProgressionPlan.LINEAR.next(topology, zone, act),
                        "Legacy LINEAR mismatch at zone=" + zone + ", act=" + act);
            }
        }
    }

    @Test
    void oneInsertionRedirectsMtzToModAndRejoinsScz() {
        ZoneProgressionPlan.ZoneTopology topology = sonic2WithMods(1);
        ZoneProgressionPlan plan = ZoneProgressionPlan.builder(topology)
                .insertAfter(MTZ, MOD_11)
                .build();

        assertEquals(new ZoneProgressionPlan.Successor(MOD_11, 0), plan.next(topology, MTZ, 2));
        assertEquals(new ZoneProgressionPlan.Successor(SCZ, 0), plan.next(topology, MOD_11, 0));
    }

    @Test
    void sameAnchorInsertionsChainInCallerProvidedEffectiveDependencyOrderThenRejoinScz() {
        ZoneProgressionPlan.ZoneTopology topology = sonic2WithMods(2);
        // Task 12 supplies contributions in frozen effective/dependency order.
        // The plan deliberately preserves that caller-provided order.
        ZoneProgressionPlan plan = ZoneProgressionPlan.builder(topology)
                .insertAfter(MTZ, MOD_11)
                .insertAfter(MTZ, MOD_12)
                .build();

        assertEquals(new ZoneProgressionPlan.Successor(MOD_11, 0), plan.next(topology, MTZ, 2));
        assertEquals(new ZoneProgressionPlan.Successor(MOD_12, 0), plan.next(topology, MOD_11, 0));
        assertEquals(new ZoneProgressionPlan.Successor(SCZ, 0), plan.next(topology, MOD_12, 0));
    }

    @Test
    void disablingFirstContributionReallocatesRemainingModToFirstAppendedIndex() {
        ZoneProgressionPlan.ZoneTopology rebuiltTopology = sonic2WithMods(1);
        int reallocatedSecondMod = MOD_11;
        ZoneProgressionPlan onlySecond = ZoneProgressionPlan.builder(rebuiltTopology)
                .insertAfter(MTZ, reallocatedSecondMod)
                .build();

        assertEquals(13, sonic2WithMods(2).zoneCount(), "Pre-disable topology had two appended zones");
        assertEquals(MOD_11, rebuiltTopology.zoneCount() - 1,
                "Remaining mod is reallocated to index 11");
        assertEquals(new ZoneProgressionPlan.Successor(reallocatedSecondMod, 0),
                onlySecond.next(rebuiltTopology, MTZ, 2));
        assertEquals(new ZoneProgressionPlan.Successor(SCZ, 0),
                onlySecond.next(rebuiltTopology, reallocatedSecondMod, 0));
    }

    @Test
    void disablingSecondContributionRebuildsWithoutItsFormerIndex() {
        ZoneProgressionPlan.ZoneTopology rebuiltTopology = sonic2WithMods(1);
        ZoneProgressionPlan onlyFirst = ZoneProgressionPlan.builder(rebuiltTopology)
                .insertAfter(MTZ, MOD_11)
                .build();

        assertEquals(12, rebuiltTopology.zoneCount());
        assertEquals(new ZoneProgressionPlan.Successor(MOD_11, 0),
                onlyFirst.next(rebuiltTopology, MTZ, 2));
        assertEquals(new ZoneProgressionPlan.Successor(SCZ, 0),
                onlyFirst.next(rebuiltTopology, MOD_11, 0));
    }

    @Test
    void disablingAllContributionsRebuildsStockTopologyWithoutDanglingRedirects() {
        ZoneProgressionPlan.ZoneTopology rebuiltTopology = sonic2WithMods(0);
        assertEquals(new ZoneProgressionPlan.Successor(SCZ, 0),
                ZoneProgressionPlan.builder(rebuiltTopology).build().next(rebuiltTopology, MTZ, 2));
        assertEquals(11, rebuiltTopology.zoneCount());
    }

    @Test
    void duplicateInsertedZoneIdentityIsRejected() {
        ZoneProgressionPlan.ZoneTopology topology = sonic2WithMods(1);
        ZoneProgressionPlan.Builder builder = ZoneProgressionPlan.builder(topology)
                .insertAfter(6, MOD_11);

        assertThrows(IllegalArgumentException.class, () -> builder.insertAfter(MTZ, MOD_11));
    }

    @Test
    void eventChainedAnchorIsRejected() {
        ZoneProgressionPlan.ZoneTopology topology = ZoneProgressionPlan.ZoneTopology.of(List.of(
                new ZoneProgressionPlan.ZoneMetadata(2, ZoneProgressionPlan.Completion.RESULTS_DRIVEN),
                new ZoneProgressionPlan.ZoneMetadata(1, ZoneProgressionPlan.Completion.EVENT_CHAINED),
                new ZoneProgressionPlan.ZoneMetadata(1, ZoneProgressionPlan.Completion.TERMINAL),
                new ZoneProgressionPlan.ZoneMetadata(1, ZoneProgressionPlan.Completion.RESULTS_DRIVEN)));

        assertThrows(IllegalArgumentException.class,
                () -> ZoneProgressionPlan.builder(topology).insertAfter(1, 3));
    }

    @Test
    void appendedEventChainedZoneCannotJoinAResultsDrivenInsertionChain() {
        ZoneProgressionPlan.ZoneTopology topology = sonic2WithAppendedCompletion(
                ZoneProgressionPlan.Completion.EVENT_CHAINED);

        assertThrows(IllegalArgumentException.class,
                () -> ZoneProgressionPlan.builder(topology).insertAfter(MTZ, MOD_11));
    }

    @Test
    void appendedTerminalZoneCannotJoinAResultsDrivenInsertionChain() {
        ZoneProgressionPlan.ZoneTopology topology = sonic2WithAppendedCompletion(
                ZoneProgressionPlan.Completion.TERMINAL);

        assertThrows(IllegalArgumentException.class,
                () -> ZoneProgressionPlan.builder(topology).insertAfter(MTZ, MOD_11));
    }

    @Test
    void stockZoneCannotMasqueradeAsAnInsertedModZoneAcrossIndependentAnchors() {
        ZoneProgressionPlan.ZoneTopology topology = sonic2WithMods(0);

        assertThrows(IllegalArgumentException.class,
                () -> ZoneProgressionPlan.builder(topology).insertAfter(6, SCZ));
    }

    @Test
    void insertedZoneCannotLaterBecomeAnAnchor() {
        ZoneProgressionPlan.ZoneTopology topology = sonic2WithMods(2);
        ZoneProgressionPlan.Builder builder = ZoneProgressionPlan.builder(topology)
                .insertAfter(MTZ, MOD_11);

        assertThrows(IllegalArgumentException.class, () -> builder.insertAfter(MOD_11, MOD_12));
    }

    @Test
    void anExistingAnchorCannotLaterBecomeAnInsertedZone() {
        ZoneProgressionPlan.ZoneTopology topology = sonic2WithMods(2);
        ZoneProgressionPlan.Builder builder = ZoneProgressionPlan.builder(topology)
                .insertAfter(6, MOD_12);

        assertThrows(IllegalArgumentException.class, () -> builder.insertAfter(MTZ, 6));
    }

    @Test
    void planCompatibilityIncludesCompletionMetadataNotOnlyShape() {
        ZoneProgressionPlan.ZoneTopology terminal = ZoneProgressionPlan.ZoneTopology.of(List.of(
                new ZoneProgressionPlan.ZoneMetadata(1, ZoneProgressionPlan.Completion.RESULTS_DRIVEN),
                new ZoneProgressionPlan.ZoneMetadata(1, ZoneProgressionPlan.Completion.TERMINAL)));
        ZoneProgressionPlan.ZoneTopology resultsDriven = ZoneProgressionPlan.ZoneTopology.of(List.of(
                new ZoneProgressionPlan.ZoneMetadata(1, ZoneProgressionPlan.Completion.EVENT_CHAINED),
                new ZoneProgressionPlan.ZoneMetadata(1, ZoneProgressionPlan.Completion.TERMINAL)));
        ZoneProgressionPlan plan = ZoneProgressionPlan.builder(terminal).build();

        assertThrows(IllegalArgumentException.class, () -> plan.requireCompatible(resultsDriven));
    }

    @Test
    void topologyDefensivelyCopiesItsRegistrySnapshot() {
        var metadata = new java.util.ArrayList<>(List.of(
                new ZoneProgressionPlan.ZoneMetadata(2, ZoneProgressionPlan.Completion.RESULTS_DRIVEN)));
        ZoneProgressionPlan.ZoneTopology topology = ZoneProgressionPlan.ZoneTopology.of(metadata);

        metadata.clear();

        assertEquals(1, topology.zoneCount());
        assertEquals(2, topology.actCount(0));
    }

    private static ZoneProgressionPlan.ZoneTopology topology(int... acts) {
        return ZoneProgressionPlan.ZoneTopology.linear(acts);
    }

    private static ZoneProgressionPlan.ProgressionResult legacyNext(int[] actCounts, int zone, int act) {
        if (act + 1 < actCounts[zone]) {
            return new ZoneProgressionPlan.Successor(zone, act + 1);
        }
        if (zone + 1 < actCounts.length) {
            return new ZoneProgressionPlan.Successor(zone + 1, 0);
        }
        return ZoneProgressionPlan.Credits.INSTANCE;
    }

    private static ZoneProgressionPlan.ZoneTopology sonic2WithMods(int modCount) {
        if (modCount < 0 || modCount > 2) {
            throw new IllegalArgumentException("Test fixture supports zero through two mods");
        }
        int[] acts = java.util.Arrays.copyOf(
                new int[]{2, 2, 2, 2, 2, 2, 2, 3, 1, 1, 1, 1, 1}, 11 + modCount);
        java.util.ArrayList<ZoneProgressionPlan.ZoneMetadata> zones = new java.util.ArrayList<>();
        for (int zone = 0; zone < acts.length; zone++) {
            ZoneProgressionPlan.Completion completion = zone == 10
                    ? ZoneProgressionPlan.Completion.TERMINAL
                    : ZoneProgressionPlan.Completion.RESULTS_DRIVEN;
            zones.add(new ZoneProgressionPlan.ZoneMetadata(acts[zone], completion));
        }
        return ZoneProgressionPlan.ZoneTopology.of(zones);
    }

    private static ZoneProgressionPlan.ZoneTopology sonic2WithAppendedCompletion(
            ZoneProgressionPlan.Completion appendedCompletion) {
        java.util.ArrayList<ZoneProgressionPlan.ZoneMetadata> zones = new java.util.ArrayList<>();
        for (int zone = 0; zone < 12; zone++) {
            ZoneProgressionPlan.Completion completion = zone == 10
                    ? ZoneProgressionPlan.Completion.TERMINAL
                    : zone == MOD_11 ? appendedCompletion : ZoneProgressionPlan.Completion.RESULTS_DRIVEN;
            int actCount = zone == MTZ ? 3 : zone < 7 ? 2 : 1;
            zones.add(new ZoneProgressionPlan.ZoneMetadata(actCount, completion));
        }
        return ZoneProgressionPlan.ZoneTopology.of(zones);
    }
}
