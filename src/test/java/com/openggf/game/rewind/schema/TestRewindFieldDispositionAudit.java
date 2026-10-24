package com.openggf.game.rewind.schema;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.GenericRewindEligibility;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic3k.objects.LbzMinibossInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRewindFieldDispositionAudit {
    @Test
    void lbzPanelHolderIsCapturedWithoutDisablingTheDefaultOwnerPath() {
        assertTrue(GenericRewindEligibility.usesDefaultObjectSubclassCapture(LbzMinibossInstance.class));
        assertTrue(GenericRewindEligibility.usesCompactDefaultSubclassCapture(LbzMinibossInstance.class));
        assertTrue(RewindSchemaRegistry.defaultObjectSubclassSchemaFor(LbzMinibossInstance.class)
                .capturedFields().stream().anyMatch(plan -> plan.field().getName().equals("panels")),
                "panels must ride the actual compact capture route, not a transient/deferred policy");
        List<RewindFieldDispositionAudit.Gap> gaps = new ArrayList<>();
        RewindFieldDispositionAudit.auditClass(LbzMinibossInstance.class, gaps);
        assertEquals(List.of(), gaps, "all existing LBZ parent fields retain their actual dispositions");
    }

    @Test
    void unresolvedMutableStateIsStillReportedRatherThanClassifiedAsStructural() {
        assertEquals(Set.of(key("uncapturedChildren"), key("uncapturedHelper")), fixtureGaps().keySet());
    }

    @Test
    void resolvedBaselineKeyMustBeRemovedBeforeItCanHideARecurrence() {
        String closedKey = "resolved.Owner#riderState";
        AssertionError failure = assertThrows(AssertionError.class,
                () -> TestRewindFieldDispositionGuard.assertBaselineMatches(Map.of(), Set.of(closedKey)));
        assertTrue(failure.getMessage().contains(closedKey), failure::getMessage);
    }

    @Test
    void reintroducedClosedFieldFailsAfterItsBaselineEntryWasPruned() {
        Map<String, String> actualGaps = fixtureGaps();
        AssertionError failure = assertThrows(AssertionError.class,
                () -> TestRewindFieldDispositionGuard.assertBaselineMatches(actualGaps, Set.of()));
        assertTrue(failure.getMessage().contains(key("uncapturedChildren")), failure::getMessage);
        assertTrue(failure.getMessage().contains(key("uncapturedHelper")), failure::getMessage);
    }

    @Test
    void aClosedKeyDoesNotProvideCreditForADifferentNewGap() {
        AssertionError failure = assertThrows(AssertionError.class,
                () -> TestRewindFieldDispositionGuard.assertBaselineMatches(
                        fixtureGaps(), Set.of("resolved.Owner#riderState")));
        assertTrue(failure.getMessage().contains(key("uncapturedChildren")), failure::getMessage);
    }

    private static String key(String field) {
        return UncapturedState.class.getName() + "#" + field;
    }

    private static Map<String, String> fixtureGaps() {
        List<RewindFieldDispositionAudit.Gap> gaps = new ArrayList<>();
        RewindFieldDispositionAudit.auditClass(UncapturedState.class, gaps);
        Map<String, String> current = new TreeMap<>();
        gaps.forEach(gap -> current.put(gap.key(), gap.detail()));
        return current;
    }

    private static final class UncapturedState extends AbstractObjectInstance {
        private final List<ObjectInstance> uncapturedChildren = new ArrayList<>();
        // Unlike ObjectInstance links, an arbitrary mutable helper has no compact codec.
        private Object uncapturedHelper;
        @RewindTransient(reason = "test fixture for explicitly excluded per-update scratch")
        private final Map<PlayableEntity, Integer> transientScratch = new IdentityHashMap<>();
        private final int structuralSize = 4;
        private int capturedTimer;

        private UncapturedState(ObjectSpawn spawn) {
            super(spawn, "FieldDispositionAuditFixture");
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {}

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {}
    }
}
