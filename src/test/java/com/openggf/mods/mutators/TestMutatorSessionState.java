package com.openggf.mods.mutators;

import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModFaultBoundary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.openggf.mods.mutators.MutatorScope.*;
import static org.junit.jupiter.api.Assertions.*;

class TestMutatorSessionState {
    @Test
    void slidersValidateLongArithmeticAtIntegerExtremesAndRequireReachableEndpoints() {
        MutatorOption high = new MutatorOption.IntegerSlider("high", "High", "", LIVE,
                Integer.MAX_VALUE - 25, Integer.MAX_VALUE - 100, Integer.MAX_VALUE, 25, "");
        high.validate(Integer.MAX_VALUE);
        high.validate(Integer.MAX_VALUE - 100);
        assertThrows(IllegalArgumentException.class, () -> high.validate(Integer.MAX_VALUE - 1));
        MutatorOption low = new MutatorOption.IntegerSlider("low", "Low", "", LIVE,
                Integer.MIN_VALUE + 25, Integer.MIN_VALUE, Integer.MIN_VALUE + 100, 25, "");
        low.validate(Integer.MIN_VALUE);
        low.validate(Integer.MIN_VALUE + 100);
        assertThrows(IllegalArgumentException.class, () -> low.validate(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> new MutatorOption.IntegerSlider(
                "wide", "Wide", "", LIVE, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, 1, ""));
        assertThrows(IllegalArgumentException.class, () -> new MutatorOption.IntegerSlider(
                "unaligned", "Unaligned", "", LIVE, 10, 0, 99, 2, ""));
    }

    @Test
    void configurationDetailWrapsWholeWordsIntoTwoCompactLinesAndEllipsizesOverflow() {
        // 320 px panels give 46 compact columns; creator help text gets two lines.
        assertEquals(List.of("Scale ordinary dry air acceleration; jump", "impulse and integration stay native."),
                MutatorConfigurationScreen.wrap(
                        "Scale ordinary dry air acceleration; jump impulse and integration stay native.", 46, 2));
        assertEquals(List.of("One line."), MutatorConfigurationScreen.wrap("  One line.  ", 46, 2));
        var overflow = MutatorConfigurationScreen.wrap("alpha beta gamma delta epsilon zeta eta", 11, 2);
        assertEquals(2, overflow.size());
        assertEquals("alpha beta", overflow.get(0));
        assertTrue(overflow.get(1).endsWith("...") && overflow.get(1).length() == 11, overflow.toString());
        assertEquals(List.of(""), MutatorConfigurationScreen.wrap("", 46, 2));
    }

    @Test
    void restartPreviewRejectsInvalidGraphWithoutFactoriesOrPublicationAndDoesNotConsumeLaunch() {
        AtomicInteger preparations = new AtomicInteger();
        MutatorSessionState state = session(1, List.of(gravity("gravity", LIVE, LIVE, LIVE, preparations)));
        state.requestEnabled("owner:gravity", true);
        state.requestOption("owner:gravity", "percent", 150);
        assertTrue(state.previewBoundary(LAUNCH, null).accepted());
        assertTrue(state.previewBoundary(LOAD, MutatorSessionState.LoadCause.FULL_RESTART).accepted());
        assertEquals(0, preparations.get());
        assertEquals(0, state.effective().revision());
        assertFalse(state.admitted().get("owner:gravity").enabled());
        assertTrue(state.boundary(LAUNCH).accepted());
        assertEquals(1, preparations.get());
        assertThrows(IllegalStateException.class, () -> state.previewBoundary(LAUNCH, null));
        MutatorSessionState invalid = session(2, List.of(
                gravity("base", LIVE, LIVE, LIVE, preparations), dependency("dependent", "base")));
        invalid.requestEnabled("owner:dependent", true);
        assertFalse(invalid.previewBoundary(LOAD, MutatorSessionState.LoadCause.FULL_RESTART).accepted());
        assertEquals(1, preparations.get());
        assertEquals(0, invalid.effective().revision());
    }

    @Test
    void inactiveIdentityDoesNotPrepareCallbacksAndTwoSessionsAreIndependent() {
        AtomicInteger preparations = new AtomicInteger();
        MutatorDefinition definition = gravity("gravity", LIVE, LIVE, LIVE, preparations);
        MutatorSessionState first = session(1, List.of(definition));
        MutatorSessionState second = session(2, List.of(definition));
        assertEquals(100, first.effective().gravityPercent());
        assertTrue(first.boundary(LAUNCH).accepted());
        first.requestOption("owner:gravity", "percent", 150);
        assertTrue(first.boundary(LIVE).accepted());
        assertEquals(150, first.admitted().get("owner:gravity").options().get("percent"));
        assertEquals(100, first.effective().gravityPercent());
        assertEquals(0, preparations.get());
        first.requestEnabled("owner:gravity", true);
        first.boundary(LIVE);
        assertEquals(150, first.effective().gravityPercent());
        assertEquals(100, second.effective().gravityPercent());
        assertEquals(1, preparations.get());
    }

    @Test
    void mixedEnableLiveDisableLoadOptionLaunchCannotLaunderInactiveOrToggledEdits() {
        AtomicInteger preparations = new AtomicInteger();
        MutatorDefinition definition = gravity("mixed", LIVE, LOAD, LAUNCH, preparations);
        MutatorSessionState state = session(1, List.of(definition));
        state.boundary(LAUNCH);
        state.requestOption("owner:mixed", "percent", 200);
        state.requestEnabled("owner:mixed", true);
        state.boundary(LIVE);
        assertEquals(100, state.effective().gravityPercent());
        assertEquals(100, state.admitted().get("owner:mixed").options().get("percent"));
        assertEquals(1, state.pending().size());
        state.requestEnabled("owner:mixed", false);
        state.boundary(LIVE);
        assertTrue(state.admitted().get("owner:mixed").enabled());
        assertEquals(2, state.pending().size());
        state.boundary(LOAD, MutatorSessionState.LoadCause.FULL_RESTART);
        assertFalse(state.admitted().get("owner:mixed").enabled());
        assertEquals(100, state.admitted().get("owner:mixed").options().get("percent"));
        state.requestEnabled("owner:mixed", true);
        state.boundary(LIVE);
        assertEquals(100, state.effective().gravityPercent());
        assertThrows(IllegalStateException.class, () -> state.boundary(LAUNCH));
        MutatorSessionState fresh = new MutatorSessionState(2, state.definitions(),
                Set.of(MutatorCapability.DRY_SONIC_GRAVITY), faults(new java.util.HashSet<>()), owner -> true, state.requested());
        fresh.boundary(LAUNCH);
        assertEquals(200, fresh.effective().gravityPercent());
        assertEquals(200, fresh.requested().get("owner:mixed").options().get("percent"));
    }

    @Test
    void loadCauseIsExplicitAndOnlyFullAssemblyCausesQualify() {
        MutatorSessionState state = session(1, List.of(gravity("gravity", LOAD, LOAD, LOAD, new AtomicInteger())));
        state.requestEnabled("owner:gravity", true);
        state.requestOption("owner:gravity", "percent", 150);
        assertThrows(IllegalArgumentException.class, () -> state.boundary(LOAD));
        for (MutatorSessionState.LoadCause cause : MutatorSessionState.LoadCause.values()) {
            if (!cause.qualifies()) {
                assertFalse(state.boundary(LOAD, cause).accepted());
                assertEquals(100, state.effective().gravityPercent());
            }
        }
        assertTrue(state.boundary(LOAD, MutatorSessionState.LoadCause.FULL_DEATH_RELOAD).accepted());
        assertEquals(150, state.effective().gravityPercent());
    }

    @Test
    void atomicGroupUsesItsOwnSafeBoundaryAndUndoClearsPending() {
        MutatorOption percent = slider(LIVE);
        MutatorOption second = new MutatorOption.Checkbox("extra", "Extra", "", LOAD, false);
        MutatorDefinition definition = new MutatorDefinition("grouped", "Grouped", "", 1,
                LIVE, LIVE, List.of(percent, second),
                List.of(new MutatorDefinition.AtomicGroup("pair", LOAD, Set.of("percent", "extra"))),
                Set.of(), Set.of(), Set.of(MutatorCapability.DRY_SONIC_GRAVITY),
                options -> List.of(new MutatorPolicy.DrySonicGravity(options.integer("percent"))));
        MutatorSessionState state = session(1, List.of(definition));
        state.requestEnabled("owner:grouped", true);
        state.boundary(LIVE);
        state.requestOption("owner:grouped", "percent", 150);
        state.requestOption("owner:grouped", "extra", true);
        state.boundary(LIVE);
        assertEquals(100, state.effective().gravityPercent());
        assertFalse((Boolean) state.admitted().get("owner:grouped").options().get("extra"));
        state.boundary(LOAD, MutatorSessionState.LoadCause.FULL_LEVEL_ASSEMBLY);
        assertEquals(150, state.effective().gravityPercent());
        assertTrue((Boolean) state.admitted().get("owner:grouped").options().get("extra"));
        state.requestOption("owner:grouped", "percent", 175);
        state.requestOption("owner:grouped", "percent", 150);
        assertTrue(state.pending().isEmpty());
    }

    @Test
    void invalidSchemaGroupsChoicesAndRangesAreRejectedWithoutMutatingPreferences() {
        assertThrows(IllegalArgumentException.class, () -> new MutatorOption.IntegerSlider(
                "x", "X", "", LIVE, 101, 25, 200, 25, "%"));
        assertThrows(IllegalArgumentException.class, () -> new MutatorOption.Choice(
                "x", "X", "", LIVE, "one", List.of("one", "one")));
        assertThrows(IllegalArgumentException.class, () -> new MutatorDefinition("bad", "Bad", "", 1,
                LIVE, LIVE, List.of(slider(LAUNCH), new MutatorOption.Checkbox("other", "Other", "", LIVE, false)),
                List.of(new MutatorDefinition.AtomicGroup("bad", LIVE, Set.of("percent", "other"))),
                Set.of(), Set.of(), Set.of(MutatorCapability.DRY_SONIC_GRAVITY), options -> List.of()));
        MutatorSessionState state = session(1, List.of(gravity("gravity", LIVE, LIVE, LIVE, new AtomicInteger())));
        var before = state.requested();
        assertThrows(IllegalArgumentException.class, () -> state.requestOption("owner:gravity", "percent", 201));
        assertThrows(IllegalArgumentException.class, () -> state.requestOption("owner:gravity", "percent", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> state.requestOption("owner:gravity", "missing", true));
        assertEquals(before, state.requested());
        assertThrows(UnsupportedOperationException.class, () -> state.requested().clear());
    }

    @Test
    void unsupportedCapabilityAndConflictPublishNoCandidate() {
        MutatorDefinition first = gravity("first", LIVE, LIVE, LIVE, new AtomicInteger());
        MutatorDefinition conflict = new MutatorDefinition("second", "Second", "", 1, LIVE, LIVE,
                List.of(slider(LIVE)), List.of(), Set.of(), Set.of("first"),
                Set.of(MutatorCapability.DRY_SONIC_GRAVITY), options -> List.of(new MutatorPolicy.DrySonicGravity(100)));
        MutatorSessionState unsupported = new MutatorSessionState(1, List.of(new OwnedMutator("owner", first)),
                Set.of(), faults(new java.util.HashSet<>()), owner -> true);
        unsupported.requestEnabled("owner:first", true);
        assertFalse(unsupported.boundary(LIVE).accepted());
        assertEquals(0, unsupported.effective().revision());
        MutatorSessionState state = session(2, List.of(first, conflict));
        state.requestEnabled("owner:first", true);
        state.requestEnabled("owner:second", true);
        assertFalse(state.boundary(LIVE).accepted());
        assertEquals(0, state.effective().revision());
        assertTrue(state.effective().policies().isEmpty());
    }

    @Test
    void prerequisiteEnableIsAtomicAndDisableClosureRespectsDependentScope() {
        MutatorDefinition base = gravity("base", LIVE, LIVE, LIVE, new AtomicInteger());
        MutatorDefinition dependent = new MutatorDefinition("dependent", "Dependent", "", 1, LIVE, LOAD,
                List.of(slider(LIVE)), List.of(), Set.of("base"), Set.of(),
                Set.of(MutatorCapability.DRY_SONIC_GRAVITY), options -> List.of(new MutatorPolicy.DrySonicGravity(100)));
        MutatorSessionState state = session(1, List.of(base, dependent));
        state.requestEnabled("owner:dependent", true);
        assertFalse(state.boundary(LIVE).accepted());
        state.requestEnabled("owner:base", true);
        assertTrue(state.boundary(LIVE).accepted());
        long revision = state.effective().revision();
        state.requestEnabled("owner:base", false);
        assertFalse(state.boundary(LIVE).accepted());
        assertEquals(revision, state.effective().revision());
        assertTrue(state.boundary(LOAD, MutatorSessionState.LoadCause.FULL_RESTART).accepted());
        assertFalse(state.admitted().get("owner:base").enabled());
        assertFalse(state.admitted().get("owner:dependent").enabled());
    }

    @Test
    void missingDependencyAndCyclesAreRejectedBeforeSessionCreation() {
        MutatorDefinition missing = dependency("one", "absent");
        assertThrows(IllegalArgumentException.class, () -> session(1, List.of(missing)));
        assertThrows(IllegalArgumentException.class, () -> session(1,
                List.of(dependency("one", "two"), dependency("two", "one"))));
    }

    @Test
    void rewindKeepsSavedPreferencesAndForwardReplayReproducesHistoricalPolicyEvents() {
        AtomicInteger preparations = new AtomicInteger();
        MutatorSessionState state = session(1, List.of(gravity("gravity", LIVE, LIVE, LIVE, preparations)));
        state.boundary(LAUNCH);
        state.onForwardTick();
        var stockAtTickOne = state.snapshot();
        state.requestEnabled("owner:gravity", true);
        state.requestOption("owner:gravity", "percent", 150);
        state.boundary(LIVE);
        long revision = state.effective().revision();
        state.onForwardTick();
        assertEquals(150, state.effective().gravityPercent());
        var activeAtTickTwo = state.snapshot();
        state.restore(stockAtTickOne);
        assertEquals(100, state.effective().gravityPercent());
        assertTrue(state.requested().get("owner:gravity").enabled());
        assertEquals(150, state.requested().get("owner:gravity").options().get("percent"));
        state.boundary(LIVE); // Resume without editing cannot republish saved future preferences.
        assertEquals(100, state.effective().gravityPercent());
        state.onForwardTick();
        assertEquals(revision, state.effective().revision());
        assertEquals(150, state.effective().gravityPercent());
        assertEquals(1, preparations.get(), "Replay consumes immutable publications, not creator callbacks");
        state.restore(activeAtTickTwo);
        assertEquals(150, state.effective().gravityPercent());
        assertEquals(2, state.snapshot().tick());
    }

    @Test
    void explicitEditAfterRestoreBranchesTimelineWithoutPromotingOtherSavedValues() {
        MutatorSessionState state = session(1, List.of(gravity("gravity", LIVE, LIVE, LIVE, new AtomicInteger())));
        state.onForwardTick();
        var before = state.snapshot();
        state.requestOption("owner:gravity", "percent", 200);
        state.requestEnabled("owner:gravity", true);
        state.boundary(LIVE);
        state.onForwardTick();
        state.restore(before);
        state.requestEnabled("owner:gravity", true); // Explicit activation branches; saved 200 is NOT an edit.
        state.boundary(LIVE);
        state.onForwardTick();
        assertEquals(100, state.effective().gravityPercent());
        assertEquals(200, state.requested().get("owner:gravity").options().get("percent"));
        state.requestOption("owner:gravity", "percent", 175);
        state.boundary(LIVE);
        state.onForwardTick();
        assertEquals(175, state.effective().gravityPercent());
    }

    @Test
    void deferredEditEventsReplayAndPreferenceSaveKeepsFutureChoicesAndOrphans() {
        MutatorSessionState state = session(1, List.of(gravity("gravity", LIVE, LIVE, LAUNCH, new AtomicInteger())));
        state.boundary(LAUNCH);
        state.onForwardTick();
        var before = state.snapshot();
        state.requestOption("owner:gravity", "percent", 200);
        state.boundary(LIVE);
        state.onForwardTick();
        assertEquals(100, state.effective().gravityPercent());
        assertEquals(200, state.snapshot().targets().get("owner:gravity").options().get("percent"));
        state.restore(before);
        assertEquals(100, state.snapshot().targets().get("owner:gravity").options().get("percent"));
        state.onForwardTick();
        assertEquals(200, state.snapshot().targets().get("owner:gravity").options().get("percent"));
        assertEquals(100, state.admitted().get("owner:gravity").options().get("percent"));
        state.restore(before);
        MutatorPreferences saved = new MutatorPreferences("s2", Map.of("old:orphan",
                new MutatorPreferences.Entry(7, true, Map.of("old", 42)))).withRequested(state);
        assertEquals(200, saved.entries().get("owner:gravity").options().get("percent"));
        assertEquals(42, saved.entries().get("old:orphan").options().get("old"));
        assertEquals(100, state.admitted().get("owner:gravity").options().get("percent"));
    }

    @Test
    void snapshotsRestoreAdmittedDeferredValuesAndRecreationUsesNoPresentPreference() {
        MutatorSessionState state = session(9, List.of(gravity("gravity", LIVE, LIVE, LOAD, new AtomicInteger())));
        state.requestEnabled("owner:gravity", true);
        state.requestOption("owner:gravity", "percent", 150);
        state.boundary(LOAD, MutatorSessionState.LoadCause.FULL_LEVEL_ASSEMBLY);
        var snapshot = state.snapshot();
        state.requestOption("owner:gravity", "percent", 200);
        state.boundary(LOAD, MutatorSessionState.LoadCause.FULL_RESTART);
        state.restore(snapshot);
        assertEquals(150, state.effective().gravityPercent());
        assertEquals(150, state.admitted().get("owner:gravity").options().get("percent"));
        assertEquals(200, state.requested().get("owner:gravity").options().get("percent"));
        MutatorSessionState recreated = session(9, List.of(gravity("gravity", LIVE, LIVE, LOAD, new AtomicInteger())));
        recreated.restore(snapshot);
        assertEquals(150, recreated.effective().gravityPercent());
        assertEquals(100, recreated.requested().get("owner:gravity").options().get("percent"));
        assertThrows(IllegalArgumentException.class, () -> session(10,
                List.of(gravity("gravity", LIVE, LIVE, LOAD, new AtomicInteger()))).restore(snapshot));
    }

    @Test
    void ownerFailurePublishesNothingAndQuarantineCannotBeUndoneByRewind() {
        Set<String> quarantined = new java.util.HashSet<>();
        MutatorDefinition good = gravity("good", LIVE, LIVE, LIVE, new AtomicInteger());
        MutatorDefinition bad = new MutatorDefinition("bad", "Bad", "", LIVE, LIVE, List.of(),
                Set.of(MutatorCapability.DRY_SONIC_GRAVITY), options -> { throw new IllegalArgumentException("failure"); });
        MutatorSessionState state = new MutatorSessionState(1,
                List.of(new OwnedMutator("owner", good), new OwnedMutator("broken", bad)),
                Set.of(MutatorCapability.DRY_SONIC_GRAVITY), faults(quarantined), owner -> !quarantined.contains(owner));
        var before = state.snapshot();
        state.requestEnabled("owner:good", true);
        state.requestEnabled("broken:bad", true);
        var failure = assertThrows(ModFaultBoundary.CallbackAborted.class, () -> state.boundary(LIVE));
        assertEquals("broken", failure.owner());
        assertEquals(Set.of("broken"), quarantined);
        assertFalse(state.admitted().get("owner:good").enabled());
        assertFalse(state.admitted().get("broken:bad").enabled());
        assertThrows(IllegalStateException.class, state::effective);
        assertThrows(IllegalStateException.class, () -> state.restore(before));
        MutatorSessionState sibling = new MutatorSessionState(2, List.of(new OwnedMutator("owner", good)),
                Set.of(MutatorCapability.DRY_SONIC_GRAVITY), faults(quarantined), owner -> !quarantined.contains(owner));
        sibling.requestEnabled("owner:good", true);
        sibling.boundary(LIVE);
        var active = sibling.snapshot();
        quarantined.add("owner");
        assertThrows(IllegalStateException.class, () -> sibling.restore(active));
    }

    @Test
    void undeclaredPolicyResultsAreOwnerFaultsAndStaleClosedSessionsCannotCommit() {
        MutatorDefinition wrong = new MutatorDefinition("wrong", "Wrong", "", LIVE, LIVE, List.of(),
                Set.of(MutatorCapability.DRY_SONIC_GRAVITY), options -> List.of(
                        new MutatorPolicy.PlayerStealth(true, true, MutatorPolicy.Target.LEADER, false)));
        MutatorSessionState wrongState = session(1, List.of(wrong));
        wrongState.requestEnabled("owner:wrong", true);
        assertThrows(ModFaultBoundary.CallbackAborted.class, () -> wrongState.boundary(LIVE));
        MutatorSessionState state = session(2, List.of(gravity("gravity", LIVE, LIVE, LIVE, new AtomicInteger())));
        assertThrows(IllegalStateException.class, () -> state.boundary(3, LIVE, null));
        assertEquals(0, state.effective().revision());
        state.close();
        assertTrue(state.isClosed());
        assertThrows(IllegalStateException.class, () -> state.boundary(LIVE));
        assertThrows(IllegalStateException.class, () -> state.requestEnabled("owner:gravity", true));
    }

    @Test
    void gravityCompositionMultipliesOnceClampsAndRetainsProvenance() {
        var effective = new MutatorSessionState.Effective(2, Map.of(
                "alpha:gravity", List.of(new MutatorPolicy.DrySonicGravity(125)),
                "beta:gravity", List.of(new MutatorPolicy.DrySonicGravity(125)),
                "gamma:stealth", List.of(new MutatorPolicy.PlayerStealth(true, false, MutatorPolicy.Target.LEADER, false))));
        assertEquals(156, effective.gravityPercent());
        assertEquals(3, effective.policies().size());
        assertEquals(1, effective.stealthPolicies().size());
        assertEquals(200, new MutatorSessionState.Effective(3, Map.of(
                "a:g", List.of(new MutatorPolicy.DrySonicGravity(200)),
                "b:g", List.of(new MutatorPolicy.DrySonicGravity(200)))).gravityPercent());
        assertEquals(25, new MutatorSessionState.Effective(3, Map.of(
                "a:g", List.of(new MutatorPolicy.DrySonicGravity(25)),
                "b:g", List.of(new MutatorPolicy.DrySonicGravity(25)))).gravityPercent());
    }

    @Test
    void resetDefaultsHonorsDeferredDisableAndOptionScopes() {
        MutatorSessionState state = session(1, List.of(gravity("gravity", LIVE, LOAD, LOAD, new AtomicInteger())));
        state.requestEnabled("owner:gravity", true);
        state.requestOption("owner:gravity", "percent", 150);
        state.boundary(LOAD, MutatorSessionState.LoadCause.FULL_LEVEL_ASSEMBLY);
        state.resetDefaults();
        state.boundary(LIVE);
        assertEquals(150, state.effective().gravityPercent());
        state.boundary(LOAD, MutatorSessionState.LoadCause.FULL_RESTART);
        assertEquals(100, state.effective().gravityPercent());
        assertTrue(state.pending().isEmpty());
    }

    private static MutatorOption.IntegerSlider slider(MutatorScope scope) {
        return new MutatorOption.IntegerSlider("percent", "Gravity", "Dry normal Sonic acceleration", scope, 100, 25, 200, 1, "%");
    }
    private static MutatorDefinition gravity(String id, MutatorScope enable, MutatorScope disable,
                                             MutatorScope option, AtomicInteger preparations) {
        return new MutatorDefinition(id, "Gravity", "", enable, disable, List.of(slider(option)),
                Set.of(MutatorCapability.DRY_SONIC_GRAVITY), values -> {
                    preparations.incrementAndGet();
                    return List.of(new MutatorPolicy.DrySonicGravity(values.integer("percent")));
                });
    }
    private static MutatorDefinition dependency(String id, String requires) {
        return new MutatorDefinition(id, "Dependency", "", 1, LIVE, LIVE, List.of(), List.of(),
                Set.of(requires), Set.of(), Set.of(MutatorCapability.DRY_SONIC_GRAVITY),
                values -> List.of(new MutatorPolicy.DrySonicGravity(100)));
    }
    private static MutatorSessionState session(long generation, List<MutatorDefinition> definitions) {
        return new MutatorSessionState(generation, definitions.stream().map(definition -> new OwnedMutator("owner", definition)).toList(),
                Set.of(MutatorCapability.DRY_SONIC_GRAVITY, MutatorCapability.PLAYER_STEALTH), faults(new java.util.HashSet<>()), owner -> true);
    }
    private static ModFaultBoundary faults(Set<String> quarantine) {
        return new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(), owners -> new ModStateSaveResult.Saved(),
                quarantine::addAll);
    }
}
