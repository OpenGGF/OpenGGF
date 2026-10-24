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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRewindFieldDispositionAudit {
    @Test
    void s1RingRecipeUsesDefaultCaptureWithoutAnExclusion() {
        Class<?> owner = com.openggf.game.sonic1.objects.Sonic1RingInstance.class;
        assertTrue(GenericRewindEligibility.usesDefaultObjectSubclassCapture(owner));
        assertTrue(GenericRewindEligibility.usesCompactDefaultSubclassCapture(owner));
        Set<String> captured = RewindSchemaRegistry.defaultObjectSubclassSchemaFor(owner).capturedFields()
                .stream().map(plan -> plan.field().getName()).collect(java.util.stream.Collectors.toSet());
        assertTrue(captured.containsAll(Set.of("ringSpawn", "childRingSpawns")), captured::toString);
        List<RewindFieldDispositionAudit.Gap> gaps = new ArrayList<>();
        RewindFieldDispositionAudit.auditClass(owner, gaps);
        assertEquals(List.of(), gaps);
    }

    @Test
    void lightningSparkArtValuesRideTheActualDefaultCapturePath() throws ReflectiveOperationException {
        Class<?> owner = com.openggf.game.sonic3k.objects.LightningSparkObjectInstance.class;
        assertTrue(GenericRewindEligibility.usesDefaultObjectSubclassCapture(owner));
        assertTrue(GenericRewindEligibility.usesCompactDefaultSubclassCapture(owner));
        assertTrue(RewindSchemaRegistry.defaultObjectSubclassSchemaFor(owner).capturedFields().stream()
                .anyMatch(plan -> plan.field().getName().equals("sparkArtState")),
                "pixels/scripts must be captured by the owner-local holder, without excluding the owner");
        Class<?> helper = owner.getDeclaredField("sparkArtState").getType();
        assertTrue(com.openggf.game.rewind.RewindStateful.class.isAssignableFrom(helper));
        assertTrue(Modifier.isPrivate(helper.getModifiers()) && Modifier.isStatic(helper.getModifiers())
                && Modifier.isFinal(helper.getModifiers()) && !helper.isAnonymousClass(),
                "the named helper must own its values without an enclosing-object reference");
        Set<String> artFields = Set.of("animSet", "sparkTiles", "tilesCached");
        assertEquals(artFields, Arrays.stream(helper.getDeclaredFields()).map(field -> field.getName())
                .collect(Collectors.toSet()));
        assertTrue(Arrays.stream(owner.getDeclaredFields()).noneMatch(field -> artFields.contains(field.getName())),
                "the owner must not retain art sideband fields");
        assertTrue(Arrays.stream(helper.getDeclaredFields()).allMatch(field ->
                !Modifier.isTransient(field.getModifiers()) && field.getDeclaredAnnotations().length == 0),
                "the helper captures values directly, without replacement exclusions");
        assertTrue(Arrays.stream(owner.getDeclaredFields()).allMatch(field -> field.getDeclaredAnnotations().length == 0));
        List<RewindFieldDispositionAudit.Gap> gaps = new ArrayList<>();
        RewindFieldDispositionAudit.auditClass(owner, gaps);
        assertEquals(List.of(), gaps);
    }

    @Test
    void wallExplosionHolderRemainsCapturedByTheDefaultOwnerPath() {
        Class<?> owner = com.openggf.game.sonic3k.objects.LbzMinibossBoxKnuxInstance.class;
        assertTrue(com.openggf.game.rewind.GenericRewindEligibility.usesDefaultObjectSubclassCapture(owner));
        boolean captured = CompactFieldCapturer.supportsDefaultObjectSubclassScalars(owner)
                ? RewindSchemaRegistry.defaultObjectSubclassSchemaFor(owner).capturedFields().stream()
                        .anyMatch(plan -> plan.field().getName().equals("wallExplosions"))
                : com.openggf.game.rewind.GenericFieldCapturer.defaultObjectSubclassCapturedFieldsForAudit(owner)
                        .stream().anyMatch(field -> field.getName().equals("wallExplosions"));
        assertTrue(captured, "wallExplosions must be captured on the path this owner actually uses");
        List<RewindFieldDispositionAudit.Gap> gaps = new ArrayList<>();
        RewindFieldDispositionAudit.auditClass(owner, gaps);
        assertEquals(List.of(), gaps, "the holder must be captured, without excluding the owner from the audit");
    }

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
