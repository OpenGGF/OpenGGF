package com.openggf.tools.rewind;

import com.openggf.game.rewind.GenericRewindEligibility;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindDeferred;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindFieldPolicy;
import com.openggf.game.rewind.schema.RewindPolicyRegistry;
import com.openggf.game.rewind.schema.RewindSchemaRegistry;
import com.openggf.graphics.GLCommand;
import com.openggf.game.sonic1.objects.Sonic1TryAgainEggmanObjectInstance;
import com.openggf.level.Pattern;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.rings.RingSpawn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRewindFieldInventoryTool {
    @BeforeEach
    @AfterEach
    void clearPoliciesAndSchemas() {
        RewindSchemaRegistry.clearForTest();
    }

    @Test
    void ordinaryInventoryReportsAnInheritedReferenceDroppedByConcreteFallback() {
        RewindPolicyRegistry.registerDeclaredTypePolicy(ObjectInstance.class, RewindFieldPolicy.CAPTURED);
        assertTrue(CompactFieldCapturer.supportsDefaultObjectSubclassScalars(SupportedActor.class));
        assertFalse(CompactFieldCapturer.supportsDefaultObjectSubclassScalars(FallbackActor.class));
        assertEquals(List.of(), RewindFieldInventoryTool.unsupportedFieldsForClass(SupportedActor.class));
        assertEquals(List.of(FallbackActor.class.getName() + "#peer : " + ObjectInstance.class.getName()),
                RewindFieldInventoryTool.unsupportedFieldsForClass(FallbackActor.class));
    }

    @Test
    void ordinaryInventoryIncludesTheDefaultBadnikRoute() {
        RewindPolicyRegistry.registerDeclaredTypePolicy(ObjectInstance.class, RewindFieldPolicy.CAPTURED);
        assertFalse(GenericRewindEligibility.usesDefaultObjectSubclassCapture(SupportedBadnik.class));
        assertTrue(GenericRewindEligibility.usesDefaultBadnikSubclassCapture(SupportedBadnik.class));
        assertTrue(CompactFieldCapturer.supportsDefaultObjectSubclassScalars(SupportedBadnik.class));
        assertFalse(CompactFieldCapturer.supportsDefaultObjectSubclassScalars(FallbackBadnik.class));
        assertEquals(List.of(), RewindFieldInventoryTool.unsupportedFieldsForClass(SupportedBadnik.class));
        assertEquals(List.of(FallbackBadnik.class.getName() + "#peer : " + ObjectInstance.class.getName()),
                RewindFieldInventoryTool.unsupportedFieldsForClass(FallbackBadnik.class));
    }

    @Test
    void currentActorExclusionDoesNotCertifyAnnotationRemoval() {
        RewindPolicyRegistry.registerDeclaredTypePolicy(ObjectInstance.class, RewindFieldPolicy.TRANSIENT);
        assertTrue(CompactFieldCapturer.supportsDefaultObjectSubclassScalars(AnnotatedActor.class));
        assertTrue(CompactFieldCapturer.supportsDefaultObjectSubclassScalars(SupportedActor.class),
                "the unannotated ordinary actor captures its reference despite the general type policy");
        List<RewindFieldInventoryTool.RedundantTransientAnnotation> advice =
                RewindFieldInventoryTool.redundantTransientAnnotationsForClasses(
                        List.of(AnnotatedActor.class, AnnotationDensityFixture.class));
        assertEquals(List.of(AnnotationDensityFixture.class.getName() + "#renderer"),
                advice.stream().map(RewindFieldInventoryTool.RedundantTransientAnnotation::field).toList());
        assertEquals(1, RewindFieldInventoryTool.annotationDensityForClasses(List.of(AnnotatedActor.class))
                .byClass().get(AnnotatedActor.class.getName()).transientCount());
    }

    @Test
    void actorAnnotationAdviceDoesNotInferManualOrInheritedCapture() {
        assertFalse(GenericRewindEligibility.usesDefaultObjectSubclassCapture(ManualActor.class));
        assertTrue(RewindFieldInventoryTool.unsupportedFieldsForClass(ManualActor.class).isEmpty());
        assertEquals(List.of(),
                RewindFieldInventoryTool.redundantTransientAnnotationsForClasses(
                        List.of(ManualActor.class, AnnotatedActorBase.class))
                        .stream().map(RewindFieldInventoryTool.RedundantTransientAnnotation::field).toList());
    }

    @Test
    void ordinaryInventoryRecognizesFixedServiceAndSupplierHandles() {
        assertEquals(List.of(), RewindFieldInventoryTool.unsupportedFieldsForClass(StructuralActor.class));
    }

    @Test
    void ordinaryInventoryReportsReplaceableDescriptorsAndUnsupportedMutableValues() {
        RewindPolicyRegistry.registerDeclaredTypePolicy(java.util.BitSet.class, RewindFieldPolicy.TRANSIENT);
        assertEquals(List.of(
                UncapturedActor.class.getName() + "#bits : java.util.BitSet",
                UncapturedActor.class.getName() + "#descriptor : com.openggf.level.PatternDesc",
                UncapturedActor.class.getName() + "#handler : "
                        + UncapturedActor.StatefulHandler.class.getName()),
                RewindFieldInventoryTool.unsupportedFieldsForClass(UncapturedActor.class));
    }

    @Test
    void productionInventoryDoesNotMisclassifyStructuralHandles() throws Exception {
        List<String> unsupported = RewindFieldInventoryTool.unsupportedFields();
        for (String key : List.of(
                "com.openggf.level.objects.ExplosionObjectInstance#renderManager",
                "com.openggf.game.sonic2.objects.bosses.Sonic2EHZBossInstance#hitHandler",
                "com.openggf.game.sonic3k.objects.IczBigSnowPileInstance#events",
                "com.openggf.game.sonic3k.objects.GumballItemObjectInstance#parentYSupplier",
                "com.openggf.game.sonic3k.objects.Mgz2LevelCollapseSolidInstance#deleteSupplier")) {
            assertTrue(unsupported.stream().noneMatch(line -> line.startsWith(key + " : ")),
                    () -> "structural handle reported unsupported: " + key);
        }
    }

    private static final class StructuralActor extends ActorReferences {
        private final ObjectRenderManager renderManager = null;
        private com.openggf.level.render.PatternSpriteRenderer renderer;
        private com.openggf.sprites.render.PlayerSpriteRenderer playerRenderer;
        private final java.util.function.BooleanSupplier deleteSupplier = null;
        private final java.util.function.IntSupplier parentYSupplier = null;
        private final StatelessHandler handler = null;
        private final com.openggf.game.sonic3k.events.Sonic3kICZEvents events = null;

        private class StatelessHandler { }
    }

    private static final class UncapturedActor extends ActorReferences {
        private java.util.BitSet bits;
        private com.openggf.level.PatternDesc descriptor;
        private final StatefulHandler handler = null;

        private class StatefulHandler { private int timer; }
    }

    private abstract static class AnnotatedActorBase extends ActorReferences {
        @RewindTransient(reason = "subclasses choose their own capture projection")
        private ObjectRenderManager renderer;
    }

    private abstract static class ActorReferences extends AbstractObjectInstance {
        private ObjectInstance peer;

        private ActorReferences() {
            super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "inventory fixture");
        }

        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class SupportedActor extends ActorReferences { }
    private static final class FallbackActor extends ActorReferences { private String[] labels; }

    private static final class AnnotatedActor extends AbstractObjectInstance {
        @RewindTransient(reason = "annotation removal may change the whole ordinary selection")
        private ObjectInstance peer;
        private int timer;

        private AnnotatedActor() {
            super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "annotated inventory fixture");
        }

        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class ManualActor extends ActorReferences {
        @RewindTransient(reason = "covered by the general renderer policy")
        private ObjectRenderManager renderer;

        @Override public PerObjectRewindSnapshot captureRewindState() {
            return super.captureRewindState();
        }
    }

    private abstract static class BadnikReferences extends AbstractBadnikInstance {
        private ObjectInstance peer;

        private BadnikReferences() {
            super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "inventory badnik fixture");
        }

        @Override protected void updateMovement(int vIntRunCount, PlayableEntity player) { }
        @Override protected int getCollisionSizeIndex() { return 0; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class SupportedBadnik extends BadnikReferences { }
    private static final class FallbackBadnik extends BadnikReferences { private String[] labels; }

    private static class DeferredStructuralFixture {
        @RewindDeferred(reason = "fixture structural dependency")
        private final Object structural = new Object();
    }

    private static class CentralPolicyFixture {
        private ObjectRenderManager renderer;
    }

    private static class ConstructorMetadataFixture {
        private final String name = "fixture";
        private final int baseX = 0x120;
        private final SubpixelMotion.State motionState =
                new SubpixelMotion.State(0, 0, 0, 0, 0x100, 0);
    }

    private static class StructuralArtFixture {
        private final Pattern blank = new Pattern();
        private final Pattern[] tiles = {new Pattern(), new Pattern()};
    }

    private static class StructuralCallbackFixture {
        private Sonic1TryAgainEggmanObjectInstance.Sonic1CreditsTextRendererRef textRenderer;
    }

    private static class StructuralListFixture {
        private final List<SpriteMappingPiece> pieces = List.of();
        private final List<RingSpawn> ringSpawns = List.of(new RingSpawn(1, 2));
    }

    private static class AnnotationDensityFixture {
        @RewindTransient(reason = "covered by default declared-type policy")
        private ObjectRenderManager renderer;

        @RewindTransient(reason = "fixture unsupported field")
        private Object structural;

        @RewindDeferred(reason = "fixture deferred dependency")
        private Object deferredStructural;
    }

    private static final class DefaultObjectCaptureFixture extends AbstractObjectInstance {
        private final int subtype = 3;
        private final boolean flipped = true;
        private final SolidObjectParams solidObjectParams = new SolidObjectParams(16, 8, 9);
        private int phase;
        private boolean armed;

        private DefaultObjectCaptureFixture() {
            super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "DefaultObjectCaptureFixture");
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // no-op fixture
        }
    }

    @Test
    void unsupportedInventorySkipsDeferredFields() {
        List<String> unsupported = RewindFieldInventoryTool.unsupportedFieldsForClass(DeferredStructuralFixture.class);

        assertTrue(unsupported.isEmpty());
    }

    @Test
    void unsupportedInventorySkipsCentralPolicyFields() {
        List<String> unsupported = RewindFieldInventoryTool.unsupportedFieldsForClass(CentralPolicyFixture.class);

        assertTrue(unsupported.isEmpty(), String.join(System.lineSeparator(), unsupported));
    }

    @Test
    void unsupportedInventorySkipsSchemaClassifiedFinalMetadata() {
        List<String> unsupported = RewindFieldInventoryTool.unsupportedFieldsForClass(ConstructorMetadataFixture.class);

        assertTrue(unsupported.isEmpty(), String.join(System.lineSeparator(), unsupported));
    }

    @Test
    void unsupportedInventorySkipsStructuralArtFields() {
        List<String> unsupported = RewindFieldInventoryTool.unsupportedFieldsForClass(StructuralArtFixture.class);

        assertTrue(unsupported.isEmpty(), String.join(System.lineSeparator(), unsupported));
    }

    @Test
    void unsupportedInventorySkipsStructuralRendererCallbacks() {
        List<String> unsupported = RewindFieldInventoryTool.unsupportedFieldsForClass(StructuralCallbackFixture.class);

        assertTrue(unsupported.isEmpty(), String.join(System.lineSeparator(), unsupported));
    }

    @Test
    void unsupportedInventorySkipsFinalStructuralLists() {
        List<String> unsupported = RewindFieldInventoryTool.unsupportedFieldsForClass(StructuralListFixture.class);

        assertTrue(unsupported.isEmpty(), String.join(System.lineSeparator(), unsupported));
    }

    @Test
    void unsupportedInventorySkipsDefaultObjectCapturedFields() {
        List<String> unsupported = RewindFieldInventoryTool.unsupportedFieldsForClass(DefaultObjectCaptureFixture.class);

        assertTrue(unsupported.isEmpty(), String.join(System.lineSeparator(), unsupported));
    }

    @Test
    void objectRolloutCandidatesAreDefaultCapturedObjects() throws Exception {
        List<String> candidates = RewindFieldInventoryTool.objectRolloutCandidates();

        assertFalse(candidates.isEmpty());
        for (String candidate : candidates) {
            String className = candidate.substring(0, candidate.indexOf(" : "));
            Class<?> type = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            assertTrue(GenericRewindEligibility.usesDefaultObjectSubclassCapture(type), candidate);
        }
    }

    @Test
    void annotationDensityCountsAnnotationsByClassDeclaredTypeAndPackage() {
        RewindFieldInventoryTool.AnnotationDensity density =
                RewindFieldInventoryTool.annotationDensityForClasses(List.of(AnnotationDensityFixture.class));

        RewindFieldInventoryTool.AnnotationCounts classCounts =
                density.byClass().get(AnnotationDensityFixture.class.getName());
        assertEquals(2, classCounts.transientCount());
        assertEquals(1, classCounts.deferredCount());

        RewindFieldInventoryTool.AnnotationCounts rendererTypeCounts =
                density.byDeclaredType().get(ObjectRenderManager.class.getName());
        assertEquals(1, rendererTypeCounts.transientCount());
        assertEquals(0, rendererTypeCounts.deferredCount());

        RewindFieldInventoryTool.AnnotationCounts objectTypeCounts =
                density.byDeclaredType().get(Object.class.getName());
        assertEquals(1, objectTypeCounts.transientCount());
        assertEquals(1, objectTypeCounts.deferredCount());

        RewindFieldInventoryTool.AnnotationCounts packageCounts =
                density.byPackage().get(AnnotationDensityFixture.class.getPackageName());
        assertEquals(2, packageCounts.transientCount());
        assertEquals(1, packageCounts.deferredCount());
    }

    @Test
    void redundantTransientAnnotationsReportOnlyMatchingInferredTransientPolicy() {
        List<RewindFieldInventoryTool.RedundantTransientAnnotation> redundant =
                RewindFieldInventoryTool.redundantTransientAnnotationsForClasses(
                        List.of(AnnotationDensityFixture.class));

        assertEquals(1, redundant.size());
        RewindFieldInventoryTool.RedundantTransientAnnotation annotation = redundant.get(0);
        assertEquals(AnnotationDensityFixture.class.getName() + "#renderer", annotation.field());
        assertEquals(ObjectRenderManager.class.getName(), annotation.declaredType());
    }

    @Test
    void coverageModeRendersSummary() {
        String out = RewindFieldInventoryTool.renderCoverageReport();
        assertTrue(out.contains("coverage:"), "must include a coverage summary line");
        assertTrue(out.contains("gaps"), "must report gap count");
    }

}
