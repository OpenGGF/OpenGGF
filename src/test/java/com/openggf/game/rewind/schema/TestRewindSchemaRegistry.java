package com.openggf.game.rewind.schema;

import com.openggf.game.rewind.FieldKey;
import com.openggf.game.rewind.RewindDeferred;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic2.objects.badniks.GrabberBadnikInstance;
import com.openggf.game.sonic2.objects.FallingPillarObjectInstance;
import com.openggf.game.sonic2.objects.MCZRotPformsObjectInstance;
import com.openggf.game.sonic2.objects.SidewaysPformObjectInstance;
import com.openggf.game.sonic2.objects.SwingingPlatformObjectInstance;
import com.openggf.game.sonic3k.objects.ClamerObjectInstance;
import com.openggf.game.sonic3k.objects.Cnz2CutsceneButtonInstance;
import com.openggf.game.sonic3k.objects.CnzCannonInstance;
import com.openggf.game.sonic3k.objects.CnzCylinderInstance;
import com.openggf.game.sonic3k.objects.CnzWaterLevelCorkFloorInstance;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesCnz2AInstance;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesMhz1Instance;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesMhz1PeerInstance;
import com.openggf.game.sonic3k.objects.MGZPulleyObjectInstance;
import com.openggf.game.sonic3k.objects.Mhz1CutsceneButtonInstance;
import com.openggf.game.sonic3k.objects.MhzMushroomParachuteObjectInstance;
import com.openggf.game.sonic3k.objects.MhzStickyVineObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kMonitorObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.IczEndBossInstance;
import com.openggf.game.sonic3k.objects.badniks.DragonflyBadnikInstance.LinkedBodyChild;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRewindSchemaRegistry {
    @AfterEach
    void clearRegistry() {
        RewindSchemaRegistry.clearForTest();
    }

    @Test
    void sameClassReturnsSameSchemaAndId() {
        RewindClassSchema first = RewindSchemaRegistry.schemaFor(PolicyFixture.class);
        RewindClassSchema second = RewindSchemaRegistry.schemaFor(PolicyFixture.class);

        assertSame(first, second);
        assertEquals(first.schemaId(), second.schemaId());
    }

    @Test
    void clearForTestRestartsSchemaIds() {
        RewindClassSchema first = RewindSchemaRegistry.schemaFor(PolicyFixture.class);

        RewindSchemaRegistry.clearForTest();
        RewindClassSchema afterClear = RewindSchemaRegistry.schemaFor(PolicyFixture.class);

        assertEquals(1, first.schemaId());
        assertEquals(1, afterClear.schemaId());
    }

    @Test
    void fieldsAreOrderedSuperclassFirstThenDeclaredNameAndType() {
        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(ChildFixture.class);

        assertEquals(List.of(
                        new FieldKey(ParentFixture.class.getName(), "alphaParent"),
                        new FieldKey(ParentFixture.class.getName(), "zetaParent"),
                        new FieldKey(ChildFixture.class.getName(), "alphaChild"),
                        new FieldKey(ChildFixture.class.getName(), "betaChild"),
                        new FieldKey(ChildFixture.class.getName(), "zetaChild")),
                schema.capturedFields().stream().map(RewindFieldPlan::key).toList());
    }

    @Test
    void classifiesCapturedStructuralTransientAndDeferredFields() {
        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(PolicyFixture.class);

        assertPolicy(schema, "capturedInt", RewindFieldPolicy.CAPTURED);
        assertPolicy(schema, "finalStructuralInt", RewindFieldPolicy.STRUCTURAL);
        assertPolicy(schema, "staticInt", RewindFieldPolicy.TRANSIENT);
        assertPolicy(schema, "javaTransientInt", RewindFieldPolicy.TRANSIENT);
        assertPolicy(schema, "rewindTransientInt", RewindFieldPolicy.TRANSIENT);
        assertPolicy(schema, "deferredInt", RewindFieldPolicy.DEFERRED);

        assertEquals(List.of(new FieldKey(PolicyFixture.class.getName(), "capturedInt")),
                schema.capturedFields().stream().map(RewindFieldPlan::key).toList());
        assertTrue(schema.capturedFields().stream().allMatch(RewindFieldPlan::captured));
    }

    @Test
    void unsupportedMutableObjectFieldAppearsInUnsupportedFields() {
        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(UnsupportedFixture.class);

        assertEquals(List.of(new FieldKey(UnsupportedFixture.class.getName(), "mutableObject")),
                schema.unsupportedFields().stream().map(RewindFieldPlan::key).toList());
        assertPolicy(schema, "mutableObject", RewindFieldPolicy.UNSUPPORTED);
    }

    @Test
    void capturedFieldsAreMadeAccessible() {
        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(PrivateFieldFixture.class);
        PrivateFieldFixture fixture = new PrivateFieldFixture();

        assertTrue(schema.capturedFields().getFirst().field().canAccess(fixture));
    }

    @Test
    void defaultObjectSubclassSchemaCapturesDeferredPlayerReferences() {
        RewindClassSchema cannonSchema = RewindSchemaRegistry.defaultObjectSubclassSchemaFor(CnzCannonInstance.class);
        RewindClassSchema cylinderSchema = RewindSchemaRegistry.defaultObjectSubclassSchemaFor(CnzCylinderInstance.class);
        RewindClassSchema pulleySchema = RewindSchemaRegistry.defaultObjectSubclassSchemaFor(MGZPulleyObjectInstance.class);

        assertPolicy(cannonSchema, "capturedPlayer", RewindFieldPolicy.CAPTURED);
        assertPolicy(cannonSchema, "releasedPlayer", RewindFieldPolicy.CAPTURED);
        assertPolicy(cylinderSchema, "releasedJumpSolidSkipPlayer", RewindFieldPolicy.CAPTURED);
        assertPolicy(pulleySchema, "grabbedPlayers", RewindFieldPolicy.CAPTURED);
    }

    @Test
    void exactDefaultObjectPolicyOverridesFinalStructuralListFallback() {
        RewindClassSchema schema =
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(MCZRotPformsObjectInstance.class);

        assertPolicy(schema, "children", RewindFieldPolicy.CAPTURED);
        assertTrue(schema.unsupportedFields().isEmpty(),
                "MCZ rotating-platform compact schema must not fall back: "
                        + schema.unsupportedFields().stream().map(RewindFieldPlan::key).toList());
    }

    @Test
    void exactDefaultObjectPolicyCapturesSidewaysPlatformLink() {
        RewindClassSchema schema =
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(SidewaysPformObjectInstance.class);

        assertPolicy(schema, "linkedPlatform", RewindFieldPolicy.CAPTURED);
        assertTrue(schema.unsupportedFields().isEmpty(),
                "Sideways platform compact schema must capture the sibling link without fallback: "
                        + schema.unsupportedFields().stream().map(RewindFieldPlan::key).toList());
    }

    @Test
    void exactDefaultObjectPolicyCapturesFallingPillarChildLink() {
        RewindClassSchema schema =
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(FallingPillarObjectInstance.class);

        assertPolicy(schema, "childInstance", RewindFieldPolicy.CAPTURED);
        assertTrue(schema.unsupportedFields().isEmpty(),
                "Falling Pillar compact schema must capture the child link without fallback: "
                        + schema.unsupportedFields().stream().map(RewindFieldPlan::key).toList());
    }

    @Test
    void exactDefaultObjectPolicyCapturesSwingingPlatformDisplayChildLink() {
        RewindClassSchema schema =
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(SwingingPlatformObjectInstance.class);

        assertPolicy(schema, "displayChild", RewindFieldPolicy.CAPTURED);
        assertTrue(schema.unsupportedFields().isEmpty(),
                "Swinging Platform compact schema must capture the display child link without fallback: "
                        + schema.unsupportedFields().stream().map(RewindFieldPlan::key).toList());
    }

    @Test
    void exactDefaultObjectPolicyCapturesS3kMonitorContentsSlot() {
        RewindClassSchema schema =
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(Sonic3kMonitorObjectInstance.class);

        assertPolicy(schema, "monitorContentsSlot", RewindFieldPolicy.CAPTURED);
        assertTrue(schema.unsupportedFields().isEmpty(),
                "S3K monitor compact schema must capture the contents slot without fallback: "
                        + schema.unsupportedFields().stream().map(RewindFieldPlan::key).toList());
    }

    @Test
    void exactDefaultObjectPolicyCapturesCnz2CutsceneButtonFlashLink() {
        RewindClassSchema schema =
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(Cnz2CutsceneButtonInstance.class);

        assertPolicy(schema, "spawnedFlash", RewindFieldPolicy.CAPTURED);
        assertTrue(schema.unsupportedFields().isEmpty(),
                "CNZ2 cutscene button compact schema must capture the spawned flash link without fallback: "
                        + schema.unsupportedFields().stream().map(RewindFieldPlan::key).toList());
    }

    @Test
    void exactDefaultObjectPolicyCapturesCnzWaterLevelCorkFloorChildLink() {
        RewindClassSchema schema =
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(CnzWaterLevelCorkFloorInstance.class);

        assertPolicy(schema, "corkFloor", RewindFieldPolicy.CAPTURED);
        assertTrue(schema.unsupportedFields().isEmpty(),
                "CNZ water-level cork helper compact schema must capture the cork-floor link without fallback: "
                        + schema.unsupportedFields().stream().map(RewindFieldPlan::key).toList());
    }

    @Test
    void exactDefaultObjectPolicyCapturesCutsceneKnucklesCnz2BlockingWallLink() {
        RewindClassSchema schema =
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(CutsceneKnucklesCnz2AInstance.class);

        assertPolicy(schema, "blockingWall", RewindFieldPolicy.CAPTURED);
        assertTrue(schema.unsupportedFields().isEmpty(),
                "CNZ2 Knuckles cutscene compact schema must capture the blocking wall link without fallback: "
                        + schema.unsupportedFields().stream().map(RewindFieldPlan::key).toList());
    }

    @Test
    void exactDefaultObjectPolicyCapturesClamerSpringChildSlot() {
        RewindClassSchema schema =
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(ClamerObjectInstance.class);

        assertPolicy(schema, "springChildSlot", RewindFieldPolicy.CAPTURED);
        assertTrue(schema.unsupportedFields().isEmpty(),
                "Clamer compact schema must capture springChildSlot without fallback: "
                        + schema.unsupportedFields().stream().map(RewindFieldPlan::key).toList());
    }

    @Test
    void exactDefaultObjectPolicyCapturesDragonflyLinkedBodyFollowAnchor() {
        RewindClassSchema schema =
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(LinkedBodyChild.class);

        assertPolicy(schema, "parent", RewindFieldPolicy.CAPTURED);
        assertPolicy(schema, "followAnchor", RewindFieldPolicy.CAPTURED);
        assertTrue(schema.unsupportedFields().isEmpty(),
                "Dragonfly linked body compact schema must capture parent/follow-anchor links without fallback: "
                        + schema.unsupportedFields().stream().map(RewindFieldPlan::key).toList());
    }

    @Test
    void exactDefaultObjectPolicyCapturesIczEndBossSnowdustEmitterLink() {
        RewindClassSchema schema =
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(IczEndBossInstance.class);

        assertPolicy(schema, "bossSnowdustEmitter", RewindFieldPolicy.CAPTURED);
        assertTrue(schema.unsupportedFields().isEmpty(),
                "ICZ end boss compact schema must capture the snowdust emitter link without fallback: "
                        + schema.unsupportedFields().stream().map(RewindFieldPlan::key).toList());
    }

    @Test
    void exactDefaultObjectPolicyCapturesPlayerReferenceLinks() {
        RewindClassSchema grabberSchema =
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(GrabberBadnikInstance.class);
        RewindClassSchema parachuteSchema =
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(MhzMushroomParachuteObjectInstance.class);
        RewindClassSchema stickyVineSchema =
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(MhzStickyVineObjectInstance.class);

        assertPolicy(grabberSchema, "grabbedPlayer", RewindFieldPolicy.CAPTURED);
        assertPolicy(grabberSchema, "pendingGrabPlayer", RewindFieldPolicy.CAPTURED);
        assertPolicy(parachuteSchema, "grabbedPlayer", RewindFieldPolicy.CAPTURED);
        assertPolicy(parachuteSchema, "nativeP2GrabbedPlayer", RewindFieldPolicy.CAPTURED);
        assertPolicy(stickyVineSchema, "capturedPlayer", RewindFieldPolicy.CAPTURED);
        assertTrue(grabberSchema.unsupportedFields().isEmpty(),
                "Grabber compact schema must capture player refs without fallback: "
                        + grabberSchema.unsupportedFields().stream().map(RewindFieldPlan::key).toList());
        assertTrue(parachuteSchema.unsupportedFields().isEmpty(),
                "MHZ mushroom parachute compact schema must capture player refs without fallback: "
                        + parachuteSchema.unsupportedFields().stream().map(RewindFieldPlan::key).toList());
        assertTrue(stickyVineSchema.unsupportedFields().isEmpty(),
                "MHZ sticky vine compact schema must capture player refs without fallback: "
                        + stickyVineSchema.unsupportedFields().stream().map(RewindFieldPlan::key).toList());
    }

    @Test
    void exactDefaultObjectPolicyCapturesMhz1CutsceneButtonKnucklesLinks() {
        RewindClassSchema buttonSchema =
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(Mhz1CutsceneButtonInstance.class);
        RewindClassSchema knucklesSchema =
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(CutsceneKnucklesMhz1Instance.class);
        RewindClassSchema peerSchema =
                RewindSchemaRegistry.defaultObjectSubclassSchemaFor(CutsceneKnucklesMhz1PeerInstance.class);

        assertPolicy(buttonSchema, "spawnedKnuckles", RewindFieldPolicy.CAPTURED);
        assertPolicy(knucklesSchema, "parentButton", RewindFieldPolicy.CAPTURED);
        assertPolicy(peerSchema, "parent", RewindFieldPolicy.CAPTURED);
        assertTrue(buttonSchema.unsupportedFields().isEmpty(),
                "MHZ1 cutscene button compact schema must capture spawned Knuckles without fallback: "
                        + buttonSchema.unsupportedFields().stream().map(RewindFieldPlan::key).toList());
        assertTrue(knucklesSchema.unsupportedFields().isEmpty(),
                "MHZ1 cutscene Knuckles compact schema must capture the parent button without fallback: "
                        + knucklesSchema.unsupportedFields().stream().map(RewindFieldPlan::key).toList());
        assertTrue(peerSchema.unsupportedFields().isEmpty(),
                "MHZ1 cutscene peer compact schema must capture the parent Knuckles actor without fallback: "
                        + peerSchema.unsupportedFields().stream().map(RewindFieldPlan::key).toList());
    }

    private static void assertPolicy(RewindClassSchema schema, String fieldName, RewindFieldPolicy policy) {
        RewindFieldPlan plan = schema.fields().stream()
                .filter(field -> field.key().fieldName().equals(fieldName))
                .findFirst()
                .orElseThrow();
        assertEquals(policy, plan.policy());
    }

    private static class ParentFixture {
        int zetaParent;
        int alphaParent;
    }

    private static class ChildFixture extends ParentFixture {
        int zetaChild;
        int betaChild;
        int alphaChild;
    }

    private static class PolicyFixture {
        static int staticInt;
        transient int javaTransientInt;
        int capturedInt;
        final int finalStructuralInt = 1;
        @RewindTransient(reason = "test")
        int rewindTransientInt;
        @RewindDeferred(reason = "test")
        int deferredInt;
    }

    private static class MutableObject {
        int value;
    }

    private static class UnsupportedFixture {
        MutableObject mutableObject = new MutableObject();
    }

    private static class PrivateFieldFixture {
        private int privateCapturedInt;
    }
}
