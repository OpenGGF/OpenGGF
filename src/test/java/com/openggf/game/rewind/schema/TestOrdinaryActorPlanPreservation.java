package com.openggf.game.rewind.schema;

import com.openggf.game.rewind.FieldKey;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.rewind.GenericRewindEligibility;
import com.openggf.game.rewind.RewindStateful;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Preserves ordinary actor selection and payload contracts while its planner changes ownership. */
class TestOrdinaryActorPlanPreservation {
    private static final ObjectSpawn SPAWN = new ObjectSpawn(0x100, 0x120, 1, 0, 0, false, 0);
    private static final ObjectRefId FIRST_ID = ObjectRefId.dynamic(12, 3, 101);
    private static final ObjectRefId SECOND_ID = ObjectRefId.dynamic(13, 4, 102);

    @BeforeEach
    void beginWithEmptySchemaRegistry() {
        RewindSchemaRegistry.clearForTest();
    }

    @AfterEach
    void clearSchemaRegistry() {
        RewindSchemaRegistry.clearForTest();
    }

    @Test
    void scalarMetadataQueriesCannotChangeCaptureAccessibility() {
        SupportedActor actor = new SupportedActor();
        GenericFieldCapturer.captureObjectSubclassScalars(actor);
        CompactFieldCapturer.captureDefaultObjectSubclassScalars(actor);
        List<Field> inspected = RewindSchemaRegistry.deriveDefaultObjectSubclassScalarFields(actor.getClass());
        try {
            inspected.forEach(field -> field.setAccessible(false));
            assertDoesNotThrow(() -> GenericFieldCapturer.captureObjectSubclassScalars(actor),
                    "metadata queries must not expose the scalar capture cache's reflection handles");
            assertDoesNotThrow(() -> CompactFieldCapturer.captureDefaultObjectSubclassScalars(actor),
                    "metadata queries must not expose the compact capture plan's reflection handles");
        } finally {
            inspected.forEach(field -> field.setAccessible(true));
        }
    }

    @Test
    void directScalarSelectionDoesNotAllocateEitherSchemaKindOrConstructAnActor() {
        List<FieldKey> expected = List.of(
                key(MetadataOnlyActor.class, "labels"), key(MetadataOnlyActor.class, "tick"));
        assertEquals(expected, scalarKeys(MetadataOnlyActor.class));
        assertEquals(expected, scalarKeys(MetadataOnlyActor.class));
        assertEquals(1, RewindSchemaRegistry.schemaFor(FirstGeneralMarker.class).schemaId(),
                "a legacy scalar query must not allocate an ordinary or general schema id");

        assertEquals(expected, scalarKeys(MetadataOnlyActor.class));
        RewindClassSchema ordinary =
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(MetadataOnlyActor.class);
        assertEquals(2, ordinary.schemaId());
        assertEquals(List.of(key(MetadataOnlyActor.class, "labels")),
                ordinary.unsupportedFields().stream().map(RewindFieldPlan::key).toList());
        assertEquals(3, RewindSchemaRegistry.schemaFor(SecondGeneralMarker.class).schemaId());
    }

    @Test
    void unsupportedConcreteSiblingUsesScalarFallbackWithoutChangingTheSupportedGraph() throws Exception {
        Field labels = UnsupportedActor.class.getDeclaredField("labels");
        assertTrue(GenericFieldCapturer.isCapturedByDefaultObjectScalarPolicy(labels));
        assertTrue(RewindCodecs.codecFor(labels).isEmpty(),
                "the fallback fixture must actually be unsupported by the compact codec set");
        assertTrue(GenericRewindEligibility.usesDefaultObjectSubclassCapture(SupportedActor.class));
        assertTrue(GenericRewindEligibility.usesDefaultObjectSubclassCapture(UnsupportedActor.class));
        assertTrue(GenericRewindEligibility.usesCompactDefaultSubclassCapture(SupportedActor.class));
        assertFalse(GenericRewindEligibility.usesCompactDefaultSubclassCapture(UnsupportedActor.class));

        SupportedActor priorPeer = new SupportedActor();
        SupportedActor supported = new SupportedActor();
        supported.peer = priorPeer;
        UnsupportedActor unsupported = new UnsupportedActor();
        unsupported.peer = priorPeer;
        RewindCaptureContext captured = context(supported, priorPeer);
        var compactState = supported.captureRewindState(captured);
        var scalarState = unsupported.captureRewindState(captured);
        assertNotNull(compactState.compactGenericState());
        assertNull(compactState.genericState());
        assertNull(scalarState.compactGenericState());
        assertNotNull(scalarState.genericState());
        assertEquals(List.of(key(GraphState.class, "alpha"), key(GraphState.class, "omega"),
                        key(GraphState.class, "samples"), key(UnsupportedActor.class, "labels")),
                scalarState.genericState().keys());
        assertEquals(List.of(key(UnsupportedActor.class, "labels")),
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(UnsupportedActor.class)
                        .unsupportedFields().stream().map(RewindFieldPlan::key).toList());

        supported.samples[0] = -1;
        unsupported.samples[0] = -1;
        unsupported.labels[0] = "mutated after capture";
        SupportedActor restoredPeer = new SupportedActor();
        SupportedActor restoredSupported = new SupportedActor();
        restoredSupported.restoreRewindState(compactState, context(restoredSupported, restoredPeer));
        assertSame(restoredPeer, restoredSupported.peer);
        assertNotSame(priorPeer, restoredSupported.peer);
        assertArrayEquals(new int[] {7, 9}, restoredSupported.samples);

        UnsupportedActor restoredUnsupported = new UnsupportedActor();
        SupportedActor constructorLink = new SupportedActor();
        restoredUnsupported.peer = constructorLink;
        restoredUnsupported.labels = new String[] {"divergent"};
        restoredUnsupported.restoreRewindState(scalarState, RewindCaptureContext.none());
        assertSame(constructorLink, restoredUnsupported.peer,
                "scalar fallback must not restore a compact-only graph edge");
        assertArrayEquals(new int[] {7, 9}, restoredUnsupported.samples);
        assertArrayEquals(new String[] {"captured"}, restoredUnsupported.labels);
        assertEquals(11, restoredUnsupported.alpha);
        assertEquals(22, restoredUnsupported.omega);
    }

    @Test
    void orderedDescriptorAndPayloadRetainCrossReferencesWhenActorsRestoreInReverseOrder() {
        SupportedActor first = new SupportedActor();
        SupportedActor second = new SupportedActor();
        first.peer = second;
        second.peer = first;
        first.alpha = 0x01020304;
        first.omega = 0x11223344;
        first.samples = new int[] {0x55667788, -1};
        second.alpha = 31;
        second.omega = 47;
        second.samples = new int[] {3, 1, 4};
        RewindCaptureContext captured = context(first, second);
        var firstState = first.captureRewindState(captured);
        var secondState = second.captureRewindState(captured);
        RewindObjectStateBlob firstBlob = firstState.compactGenericState();
        assertNotNull(firstBlob);
        assertNotNull(secondState.compactGenericState());
        RewindClassSchema schema =
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(SupportedActor.class);
        assertEquals(List.of(key(GraphState.class, "alpha"), key(GraphState.class, "omega"),
                        key(GraphState.class, "peer"), key(GraphState.class, "samples")),
                schema.capturedFields().stream().map(RewindFieldPlan::key).toList());
        assertTrue(schema.unsupportedFields().isEmpty());
        assertEquals(0, firstBlob.opaqueValues().length);
        assertGraphPayload(firstBlob, first.alpha, first.omega, SECOND_ID, first.samples);

        SupportedActor restoredFirst = new SupportedActor();
        SupportedActor restoredSecond = new SupportedActor();
        RewindCaptureContext restored = context(restoredFirst, restoredSecond);
        // All identities are published before either field restore, as in the actor lifecycle.
        restoredSecond.restoreRewindState(secondState, restored);
        assertSame(restoredFirst, restoredSecond.peer);
        restoredFirst.restoreRewindState(firstState, restored);
        assertSame(restoredSecond, restoredFirst.peer);
        assertNotSame(first, restoredSecond.peer);
        assertNotSame(second, restoredFirst.peer);
        assertArrayEquals(new int[] {0x55667788, -1}, restoredFirst.samples);
        assertArrayEquals(new int[] {3, 1, 4}, restoredSecond.samples);
        assertEquals(firstBlob, restoredFirst.captureRewindState(restored).compactGenericState());
        assertEquals(secondState.compactGenericState(),
                restoredSecond.captureRewindState(restored).compactGenericState());
    }

    @Test
    void restoreCallbacksObserveTheSameOrderedScalarAndReferenceWrites() {
        SupportedActor oldPeer = new SupportedActor();
        OrderedActor original = new OrderedActor(new ArrayList<>(), oldPeer);
        original.alpha = 11;
        original.omega = 22;
        original.peer = oldPeer;
        var state = original.captureRewindState(context(original, oldPeer));
        assertNotNull(state.compactGenericState());
        assertEquals(List.of(key(OrderedActor.class, "alpha"), key(OrderedActor.class, "bravo"),
                        key(OrderedActor.class, "omega"), key(OrderedActor.class, "peer"),
                        key(OrderedActor.class, "zulu")),
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(OrderedActor.class)
                        .capturedFields().stream().map(RewindFieldPlan::key).toList());

        SupportedActor newPeer = new SupportedActor();
        List<String> observations = new ArrayList<>();
        OrderedActor restored = new OrderedActor(observations, newPeer);
        restored.alpha = -10;
        restored.omega = -20;
        RestoreProbe retainedBravo = restored.bravo;
        RestoreProbe retainedZulu = restored.zulu;
        restored.restoreRewindState(state, context(restored, newPeer));
        assertEquals(List.of("bravo:11:-20:false", "zulu:11:22:true"), observations,
                "helper callbacks must see preceding fields restored and later fields untouched");
        assertSame(retainedBravo, restored.bravo);
        assertSame(retainedZulu, restored.zulu);
        assertSame(newPeer, restored.peer);
    }

    private static void assertGraphPayload(RewindObjectStateBlob blob, int alpha, int omega,
                                           ObjectRefId peerId, int[] samples) {
        RewindStateBuffer.Reader bytes = blob.scalarReader();
        assertEquals(alpha, bytes.readInt());
        assertEquals(omega, bytes.readInt());
        assertTrue(bytes.readBoolean());
        assertEquals(peerId.kind().ordinal(), bytes.readInt());
        assertEquals(peerId.slotIndex(), bytes.readInt());
        assertEquals(peerId.generation(), bytes.readInt());
        assertEquals(peerId.spawnId(), bytes.readInt());
        assertEquals(peerId.dynamicId(), bytes.readInt());
        assertEquals(samples.length, bytes.readInt());
        for (int sample : samples) assertEquals(sample, bytes.readInt());
        assertEquals(0, bytes.remaining());
    }

    private static RewindCaptureContext context(ObjectInstance first, ObjectInstance second) {
        RewindIdentityTable table = new RewindIdentityTable();
        table.registerObject(first, FIRST_ID);
        table.registerObject(second, SECOND_ID);
        return RewindCaptureContext.withIdentityTable(table);
    }

    private static List<FieldKey> scalarKeys(Class<?> type) {
        return GenericFieldCapturer.defaultObjectSubclassCapturedFieldsForAudit(type)
                .stream().map(FieldKey::of).toList();
    }

    private static FieldKey key(Class<?> owner, String field) {
        return new FieldKey(owner.getName(), field);
    }

    private abstract static class GraphState extends AbstractObjectInstance {
        int alpha = 11;
        int omega = 22;
        ObjectInstance peer;
        int[] samples = {7, 9};

        GraphState() { super(SPAWN, "ordinary-plan-state"); }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class SupportedActor extends GraphState { }

    private static final class UnsupportedActor extends GraphState {
        String[] labels = {"captured"};
    }

    private static final class MetadataOnlyActor extends AbstractObjectInstance {
        String[] labels;
        int tick;

        private MetadataOnlyActor() {
            super(SPAWN, "metadata-only");
            throw new AssertionError("field selection must not construct an actor");
        }

        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class FirstGeneralMarker { int value; }
    private static final class SecondGeneralMarker { int value; }

    private static final class OrderedActor extends AbstractObjectInstance {
        int alpha;
        final RestoreProbe bravo;
        int omega;
        ObjectInstance peer;
        final RestoreProbe zulu;

        OrderedActor(List<String> observations, ObjectInstance expectedPeer) {
            super(SPAWN, "ordered-plan-state");
            bravo = new RestoreProbe(this, "bravo", observations, expectedPeer);
            zulu = new RestoreProbe(this, "zulu", observations, expectedPeer);
        }

        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class RestoreProbe implements RewindStateful<Integer> {
        private final OrderedActor owner;
        private final String label;
        private final List<String> observations;
        private final ObjectInstance expectedPeer;

        RestoreProbe(OrderedActor owner, String label, List<String> observations,
                     ObjectInstance expectedPeer) {
            this.owner = owner;
            this.label = label;
            this.observations = observations;
            this.expectedPeer = expectedPeer;
        }

        @Override public Integer captureRewindStateValue() { return 1; }
        @Override public void restoreRewindStateValue(Integer value) {
            observations.add(label + ":" + owner.alpha + ":" + owner.omega
                    + ":" + (owner.peer == expectedPeer));
        }
    }
}
