package com.openggf.game.rewind.schema;

import com.openggf.game.rewind.FieldKey;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.rewind.RewindCapturedReference;
import com.openggf.game.rewind.RewindDeferred;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.coverage.RewindCoverageAnalyzer;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.sonic3k.objects.badniks.MantisBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.SnaleBlasterBadnikInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tools.rewind.RewindFieldInventoryTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestRewindCapturedReference {
    private static final ObjectSpawn SPAWN = new ObjectSpawn(0, 0, 1, 0, 0, false, 0);
    private static final ObjectRefId PEER_ID = ObjectRefId.dynamic(7, 2, 101);

    @BeforeEach
    @AfterEach
    void clearSchemas() {
        RewindSchemaRegistry.clearForTest();
    }

    @Test
    void mantisOwnsBothReferenceDeclarationsWithoutCentralExceptions() throws Exception {
        Class<?> child = Class.forName(MantisBadnikInstance.class.getName() + "$MantisChild");
        for (Field field : List.of(MantisBadnikInstance.class.getDeclaredField("child"),
                child.getDeclaredField("parent"))) {
            assertFalse(DefaultObjectRewindPolicies.exactFieldPoliciesForAudit().containsKey(FieldKey.of(field)),
                    "the local declaration must replace the central exception");
            var selection = RewindSchemaRegistry.defaultObjectFieldSelectionForAudit(field.getDeclaringClass())
                    .orElseThrow();
            assertTrue(selection.compact(), "both real Mantis owners must select compact capture");
            assertTrue(selection.captures(field), "the actual Mantis projection must capture its declared link");
            RewindFieldPlan plan = RewindSchemaRegistry.defaultObjectSubclassSchemaFor(field.getDeclaringClass())
                    .capturedFields().stream().filter(candidate -> candidate.key().equals(FieldKey.of(field)))
                    .findFirst().orElseThrow(() -> new AssertionError("Mantis reference is not captured: " + field));
            assertTrue(plan.codec().requiresIdentityTable());
        }
        assertEquals(List.of("animIndex", "animTimer", "child", "initialized", "landingGuard", "state",
                        "waitPlaceholderRenderFlag", "waitingForOnscreen"),
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(MantisBadnikInstance.class).capturedFields()
                        .stream().filter(plan -> plan.field().getDeclaringClass() == MantisBadnikInstance.class)
                        .map(plan -> plan.field().getName()).toList());
    }

    @Test
    void localReferenceUsesTheExistingOrderedPayloadAndRestoresReplacementIdentity() {
        Supported source = new Supported();
        Supported oldPeer = new Supported();
        source.parent = oldPeer;
        AutomaticReference control = new AutomaticReference();
        control.peer = oldPeer;
        var blob = CompactFieldCapturer.captureDefaultObjectSubclassScalars(source, context(oldPeer));
        var controlBlob = CompactFieldCapturer.captureDefaultObjectSubclassScalars(control, context(oldPeer));
        assertEquals(List.of("alpha", "parent", "zulu"),
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(Supported.class).capturedFields()
                        .stream().map(plan -> plan.field().getName()).toList());
        assertArrayEquals(controlBlob.scalarData(), blob.scalarData(),
                "a local parent declaration must use the existing reference wire representation");
        assertArrayEquals(controlBlob.opaqueValues(), blob.opaqueValues());
        Supported replacement = new Supported();
        Supported restored = new Supported();
        CompactFieldCapturer.restoreDefaultObjectSubclassScalars(restored, blob, context(replacement));
        assertSame(replacement, restored.parent);
        assertNotSame(oldPeer, restored.parent);
        assertEquals(11, restored.alpha);
        assertEquals(22, restored.zulu);
    }

    @Test
    void localReferenceStillRequiresContextAndRegisteredIdentity() {
        Supported source = new Supported();
        source.parent = new Supported();
        assertThrows(IllegalStateException.class,
                () -> CompactFieldCapturer.captureDefaultObjectSubclassScalars(source));
        var failure = assertThrows(IllegalStateException.class,
                () -> CompactFieldCapturer.captureDefaultObjectSubclassScalars(source,
                        RewindCaptureContext.withIdentityTable(new RewindIdentityTable())));
        assertTrue(failure.getMessage().contains("no registered id for object reference"));
    }

    @Test
    void nullReferenceOverwritesAnExistingLinkWithoutIdentityContext() {
        Supported source = new Supported();
        var blob = CompactFieldCapturer.captureDefaultObjectSubclassScalars(source);
        Supported restored = new Supported();
        restored.parent = new Supported();
        CompactFieldCapturer.restoreDefaultObjectSubclassScalars(restored, blob);
        assertNull(restored.parent);
    }

    @Test
    void unsupportedConcreteSiblingKeepsScalarFallbackAndItsConstructorLink() {
        Supported peer = new Supported();
        Supported source = new Supported();
        source.parent = peer;
        Fallback fallback = new Fallback();
        fallback.parent = peer;
        assertTrue(CompactFieldCapturer.supportsDefaultObjectSubclassScalars(Supported.class));
        assertFalse(CompactFieldCapturer.supportsDefaultObjectSubclassScalars(Fallback.class));
        var compact = source.captureRewindState(context(peer));
        var scalar = fallback.captureRewindState(context(peer));
        assertNotNull(compact.compactGenericState());
        assertNull(scalar.compactGenericState());
        assertNotNull(scalar.genericState());
        assertEquals(List.of("alpha", "zulu", "labels"),
                scalar.genericState().keys().stream().map(FieldKey::fieldName).toList());
        Supported replacement = new Supported();
        Supported restored = new Supported();
        restored.restoreRewindState(compact, context(replacement));
        assertSame(replacement, restored.parent);
        Fallback restoredFallback = new Fallback();
        ObjectInstance constructorLink = new Supported();
        restoredFallback.parent = constructorLink;
        restoredFallback.restoreRewindState(scalar);
        assertSame(constructorLink, restoredFallback.parent);
        assertArrayEquals(new String[] {"captured"}, restoredFallback.labels);
    }

    @Test
    void allDiagnosticsReportTheMarkedReferenceDroppedByConcreteFallback() throws Exception {
        assertTrue(CompactFieldCapturer.supportsDefaultObjectSubclassScalars(Supported.class));
        assertFalse(CompactFieldCapturer.supportsDefaultObjectSubclassScalars(Fallback.class));
        assertEquals(List.of(), objectRefGaps(Supported.class));
        assertEquals(List.of("parent"), objectRefGaps(Fallback.class),
                "capture intent must not certify a scalar projection that omits the reference");
        assertEquals(List.of(), inventoryGaps(Supported.class));
        assertEquals(List.of(Fallback.class.getName() + "#parent : " + ObjectInstance.class.getName()),
                inventoryGaps(Fallback.class));
        List<RewindFieldDispositionAudit.Gap> gaps = new ArrayList<>();
        RewindFieldDispositionAudit.auditClass(Fallback.class, gaps);
        assertEquals(List.of(Fallback.class.getName() + "#parent"),
                gaps.stream().map(RewindFieldDispositionAudit.Gap::key).toList());
    }

    @Test
    void exclusionsWinAndUnannotatedFinalAndNamedReferencesStayExcluded() throws Exception {
        assertEquals(List.of("tick"), RewindSchemaRegistry.defaultObjectSubclassSchemaFor(Excluded.class)
                .capturedFields().stream().map(plan -> plan.field().getName()).toList());
        assertEquals(List.of("tick"), RewindSchemaRegistry.defaultObjectSubclassSchemaFor(Unannotated.class)
                .capturedFields().stream().map(plan -> plan.field().getName()).toList());
        assertEquals(RewindFieldPolicy.TRANSIENT,
                RewindSchemaRegistry.schemaFor(Excluded.class).fields().stream()
                        .filter(plan -> plan.field().getName().equals("javaTransient")).findFirst().orElseThrow().policy());
        for (String field : List.of("invalidStatic", "invalidTransient", "invalidFinalScratch")) {
            assertEquals(RewindFieldPolicy.TRANSIENT,
                    DefaultObjectRewindPolicies.policyFor(Excluded.class.getDeclaredField(field)),
                    "field-local exclusion must precede invalid-marker validation: " + field);
        }
        assertEquals(RewindFieldPolicy.DEFERRED,
                DefaultObjectRewindPolicies.policyFor(Excluded.class.getDeclaredField("invalidFinalDeferred")));
    }

    @Test
    void localMarkerDoesNotAuthorizeFinalOrNonObjectReferences() {
        assertThrows(IllegalArgumentException.class,
                () -> RewindSchemaRegistry.defaultObjectSubclassSchemaFor(InvalidFinal.class));
        assertThrows(IllegalArgumentException.class,
                () -> RewindSchemaRegistry.defaultObjectSubclassSchemaFor(InvalidType.class));
    }

    @Test
    void generalRegistrationsKeepTheirPrecedenceWithoutChangingOrdinarySelection() throws Exception {
        Field parent = References.class.getDeclaredField("parent");
        RewindPolicyRegistry.registerFieldPolicy(FieldKey.of(parent), RewindFieldPolicy.DEFERRED);
        assertEquals(RewindFieldPolicy.DEFERRED, RewindPolicyRegistry.policyForAudit(parent).orElseThrow());
        assertTrue(RewindSchemaRegistry.defaultObjectFieldSelectionForAudit(Supported.class)
                .orElseThrow().captures(parent));
        assertEquals(RewindFieldPolicy.DEFERRED, RewindSchemaRegistry.schemaFor(Supported.class).fields().stream()
                .filter(plan -> plan.key().equals(FieldKey.of(parent))).findFirst().orElseThrow().policy());
    }

    @Test
    void scalarMetadataRemainsLazyAndDoesNotSelectTheMarkedReference() {
        assertEquals(List.of("alpha", "zulu"),
                GenericFieldCapturer.defaultObjectSubclassCapturedFieldsForAudit(Supported.class)
                        .stream().map(Field::getName).toList());
        assertEquals(1, RewindSchemaRegistry.schemaFor(GeneralMarker.class).schemaId());
        assertEquals(2, RewindSchemaRegistry.defaultObjectSubclassSchemaFor(Supported.class).schemaId());
        assertEquals(3, RewindSchemaRegistry.schemaFor(SecondGeneralMarker.class).schemaId());
    }

    @Test
    void snaleBlasterStillRebuildsItsTransientAndFinalFamilyLinks() throws Exception {
        Field cover = SnaleBlasterBadnikInstance.class.getDeclaredField("cover");
        Class<?> coverChild = Class.forName(SnaleBlasterBadnikInstance.class.getName() + "$SnaleBlasterCoverChild");
        Field parent = coverChild.getDeclaredField("parent");
        assertEquals(RewindFieldPolicy.TRANSIENT, RewindPolicyRegistry.policyForAudit(cover).orElseThrow());
        assertFalse(RewindSchemaRegistry.defaultObjectFieldSelectionForAudit(SnaleBlasterBadnikInstance.class)
                .orElseThrow().captures(cover));
        assertFalse(RewindSchemaRegistry.defaultObjectFieldSelectionForAudit(coverChild).orElseThrow().captures(parent));
    }

    @SuppressWarnings("unchecked")
    private static List<String> objectRefGaps(Class<?> type) throws Exception {
        Method query = RewindCoverageAnalyzer.class.getDeclaredMethod("findUnIdObjectRefFields", String.class);
        query.setAccessible(true);
        return (List<String>) query.invoke(null, type.getName());
    }

    @SuppressWarnings("unchecked")
    private static List<String> inventoryGaps(Class<?> type) throws Exception {
        Method query = RewindFieldInventoryTool.class.getDeclaredMethod("unsupportedFieldsForClass", Class.class);
        query.setAccessible(true);
        return (List<String>) query.invoke(null, type);
    }

    private static RewindCaptureContext context(ObjectInstance peer) {
        RewindIdentityTable table = new RewindIdentityTable();
        table.registerObject(peer, PEER_ID);
        return RewindCaptureContext.withIdentityTable(table);
    }

    private abstract static class Actor extends AbstractObjectInstance {
        Actor() { super(SPAWN, "local-reference-fixture"); }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private abstract static class References extends Actor {
        int alpha = 11;
        @RewindCapturedReference(reason = "fixture graph parent") ObjectInstance parent;
        int zulu = 22;
    }
    private static final class Supported extends References { }
    private static final class Fallback extends References { String[] labels = {"captured"}; }
    private static final class AutomaticReference extends Actor {
        int alpha = 11;
        ObjectInstance peer;
        int zulu = 22;
    }
    private static final class Excluded extends Actor {
        @RewindCapturedReference(reason = "excluded") static ObjectInstance shared;
        @RewindCapturedReference(reason = "excluded") transient ObjectInstance javaTransient;
        @RewindCapturedReference(reason = "excluded") @RewindTransient(reason = "scratch") ObjectInstance scratch;
        @RewindCapturedReference(reason = "excluded") @RewindDeferred(reason = "separate owner") ObjectInstance deferred;
        @RewindCapturedReference(reason = "static exclusion wins over type") static String invalidStatic;
        @RewindCapturedReference(reason = "transient exclusion wins over type") transient String invalidTransient;
        @RewindCapturedReference(reason = "scratch exclusion wins over final")
        @RewindTransient(reason = "scratch") final ObjectInstance invalidFinalScratch = null;
        @RewindCapturedReference(reason = "deferral wins over final and type")
        @RewindDeferred(reason = "separate owner") final String invalidFinalDeferred = null;
        int tick;
    }
    private static final class Unannotated extends Actor {
        ObjectInstance parent;
        final ObjectInstance fixed = null;
        int tick;
    }
    private static final class InvalidFinal extends Actor {
        @RewindCapturedReference(reason = "invalid") final ObjectInstance parent = null;
    }
    private static final class InvalidType extends Actor {
        @RewindCapturedReference(reason = "invalid") String parent;
    }
    private static final class GeneralMarker { int value; }
    private static final class SecondGeneralMarker { int value; }
}
