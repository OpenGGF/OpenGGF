package com.openggf.mods.mutators;

import com.openggf.game.mutators.GameplayMutatorPolicy;
import com.openggf.game.mutators.LevelMutatorPolicy;
import com.openggf.game.mutators.MonitorContent;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModFaultBoundary;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.openggf.mods.mutators.MutatorScope.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises admission/composition/history rather than creator-reported configuration alone. */
class TestMutatorExpandedPolicies {
    private static final Set<MutatorCapability> ALL = EnumSet.allOf(MutatorCapability.class);

    @Test
    void disabledCatalogueRemainsStockWithoutPreparingCreatorPolicies() {
        var prepared = new AtomicInteger();
        var session = session(List.of(definition("rings", LOAD, MutatorCapability.NO_RINGS, prepared,
                new MutatorPolicy.NoRings()), definition("speed", LIVE, MutatorCapability.GAMEPLAY_SPEED, prepared,
                new MutatorPolicy.GameplaySpeed(25, true))));
        assertTrue(session.boundary(LAUNCH).accepted());
        assertEquals(LevelMutatorPolicy.STOCK, session.effective().levelPolicy(ALL));
        assertEquals(GameplayMutatorPolicy.STOCK, session.effective().gameplayPolicy(ALL));
        assertEquals(0, prepared.get());
    }

    @Test
    void loadFiltersDoNotLeakThroughResumeAndRewindReplaysHistoricalPolicyWithoutFactories() {
        var prepared = new AtomicInteger();
        var session = session(List.of(definition("rings", LOAD, MutatorCapability.NO_RINGS, prepared,
                new MutatorPolicy.NoRings()), definition("special", LIVE, MutatorCapability.NO_SPECIAL_STAGES, prepared,
                new MutatorPolicy.NoSpecialStages())));
        session.boundary(LAUNCH);
        session.onForwardTick();
        var before = session.snapshot();
        session.requestEnabled("owner:rings", true);
        session.requestEnabled("owner:special", true);
        assertTrue(session.boundary(LIVE).accepted());
        assertFalse(session.effective().levelPolicy(ALL).noRings());
        assertTrue(session.effective().levelPolicy(ALL).noSpecialStages());
        assertEquals(1, prepared.get());
        session.onForwardTick();
        assertTrue(session.boundary(LOAD, MutatorSessionState.LoadCause.FULL_RESTART).accepted());
        session.onForwardTick();
        var expected = session.effective().levelPolicy(ALL);
        assertTrue(expected.noRings());
        assertEquals(2, prepared.get());
        session.restore(before);
        assertEquals(LevelMutatorPolicy.STOCK, session.effective().levelPolicy(ALL));
        assertTrue(session.requested().get("owner:rings").enabled(), "Saved preferences are not rewound");
        session.onForwardTick();
        assertFalse(session.effective().levelPolicy(ALL).noRings());
        session.onForwardTick();
        assertEquals(expected, session.effective().levelPolicy(ALL));
        assertEquals(2, prepared.get(), "Historical replay never re-invokes creator preparation");
    }

    @Test
    void independentOwnersComposeDenyUnionCapsAndPercentagesOnceWithinHostBounds() {
        var alpha = List.<MutatorPolicy>of(new MutatorPolicy.Ringfall(50, 20),
                new MutatorPolicy.DefeatKnockback(150, 0xA00), new MutatorPolicy.GameplaySpeed(50, false),
                new MutatorPolicy.MonitorFilter(Set.of(MonitorContent.RINGS, MonitorContent.LIFE)),
                new MutatorPolicy.BigHead(150, MutatorPolicy.Target.LEADER));
        var beta = List.<MutatorPolicy>of(new MutatorPolicy.Ringfall(50, 7),
                new MutatorPolicy.DefeatKnockback(200, 0x400), new MutatorPolicy.GameplaySpeed(125, true),
                new MutatorPolicy.MonitorFilter(Set.of(MonitorContent.RINGS, MonitorContent.FIRE_SHIELD)),
                new MutatorPolicy.BigHead(150, MutatorPolicy.Target.ALL_TEAM));
        var first = new MutatorSessionState.Effective(5, Map.of("alpha:effects", alpha, "beta:effects", beta));
        var reversed = new LinkedHashMap<String, List<MutatorPolicy>>();
        reversed.put("beta:effects", beta); reversed.put("alpha:effects", alpha);
        var second = new MutatorSessionState.Effective(5, reversed);
        assertEquals(new GameplayMutatorPolicy(25, 7, 300, 0x400, 62, true), first.gameplayPolicy(ALL));
        assertEquals(first.gameplayPolicy(ALL), second.gameplayPolicy(ALL));
        assertEquals(Set.of(MonitorContent.RINGS, MonitorContent.LIFE, MonitorContent.FIRE_SHIELD),
                first.levelPolicy(ALL).removedMonitorContents());
        assertEquals(first.levelPolicy(ALL), second.levelPolicy(ALL));
        assertEquals(200, first.headScalePercent(true));
        assertEquals(150, first.headScalePercent(false));
        assertEquals(LevelMutatorPolicy.STOCK, first.levelPolicy(Set.of()));
        assertEquals(GameplayMutatorPolicy.STOCK, first.gameplayPolicy(Set.of()));
    }

    @Test
    void placementCapabilitiesRejectUnsafeToggleAndOptionScopesAtRegistration() {
        var liveCheckbox = new MutatorOption.Checkbox("rings", "Rings", "", LIVE, true);
        assertThrows(IllegalArgumentException.class, () -> new MutatorDefinition("filter", "Filter", "", LIVE, LIVE,
                List.of(liveCheckbox), Set.of(MutatorCapability.MONITOR_FILTER),
                options -> List.of(new MutatorPolicy.MonitorFilter(Set.of(MonitorContent.RINGS)))));
        assertThrows(IllegalArgumentException.class, () -> new MutatorDefinition("filter", "Filter", "", LOAD, LOAD,
                List.of(liveCheckbox), Set.of(MutatorCapability.MONITOR_FILTER),
                options -> List.of(new MutatorPolicy.MonitorFilter(Set.of(MonitorContent.RINGS)))));
    }

    private static MutatorDefinition definition(String key, MutatorScope scope, MutatorCapability capability,
                                                AtomicInteger preparations, MutatorPolicy policy) {
        return new MutatorDefinition(key, key, "", scope, scope, List.of(), Set.of(capability), options -> {
            preparations.incrementAndGet(); return List.of(policy);
        });
    }

    private static MutatorSessionState session(List<MutatorDefinition> definitions) {
        var faults = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        return new MutatorSessionState(1, definitions.stream().map(value -> new OwnedMutator("owner", value)).toList(),
                ALL, faults, owner -> true);
    }
}
