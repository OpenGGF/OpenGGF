package com.openggf.game.rewind;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestRewindSnapshotDiffDynamicIdentity {
    @Test
    void distinctCapturedVisualsSharingFixedSlotCanRestoreInReverseOrder() {
        var insta = entry("InstaShield", 100, 1, false);
        var bubble = entry("BubbleShield", 100, 450, false);
        assertEquivalent(snapshot(insta, bubble), snapshot(bubble, insta));
    }

    @Test
    void capturedPlayerRoleRemainsComparedWithoutJavaIncarnationReferences() {
        var base = entry("Shield", 100, 1, false);
        var p1 = new DynamicObjectEntry(base.className(), base.spawn(), base.slotIndex(), base.state(), null,
                base.objectId(), base.ownerModId(), base.rewindableAuxiliary(),
                com.openggf.game.rewind.identity.PlayerRefId.mainPlayer());
        var p2 = new DynamicObjectEntry(base.className(), base.spawn(), base.slotIndex(), base.state(), null,
                base.objectId(), base.ownerModId(), base.rewindableAuxiliary(),
                com.openggf.game.rewind.identity.PlayerRefId.sidekick(0));
        assertEquivalent(snapshot(p1), snapshot(p1));
        assertDifferent(snapshot(p1), snapshot(p2), "playerOwnerId");
    }

    @Test
    void sameSlotMemberCannotBeDroppedOrDuplicated() {
        var insta = entry("InstaShield", 100, 1, false);
        var bubble = entry("BubbleShield", 100, 450, false);
        assertDifferent(snapshot(insta, bubble), snapshot(bubble), "missing in B");
        assertDifferent(snapshot(insta, bubble), snapshot(insta, bubble, bubble), "duplicate object identity");
        assertDifferent(snapshot(insta, insta), snapshot(insta, insta), "duplicate object identity");
    }

    @Test
    void stableIdentityDoesNotHideChangedSlotClassOrState() {
        var insta = entry("InstaShield", 100, 1, false);
        var bubble = entry("BubbleShield", 100, 450, false);
        var before = snapshot(insta, bubble);
        assertDifferent(before, snapshot(bubble, entry("InstaShield", 101, 1, false)), "slotIndex");
        assertDifferent(before, snapshot(bubble, entry("OtherShield", 100, 1, false)), "className");
        assertDifferent(before, snapshot(bubble, entry("InstaShield", 100, 1, true)), "destroyed");
        assertDifferent(before, snapshot(bubble, entry("InstaShield", 100, 2, false)), "missing in B");
    }

    @Test
    void legacyEntriesRetainUniqueSlotFallbackWithoutCollapsingDuplicates() {
        var first = new DynamicObjectEntry("Legacy", null, 50, null);
        var second = new DynamicObjectEntry("Legacy", null, 60, null);
        assertEquivalent(snapshot(first, second), snapshot(second, first));
        assertDifferent(snapshot(first, second), snapshot(first, first), "duplicate object identity");
    }

    @Test void hiddenSolidContactStateCannotBeOmittedFromWorldEquality() {
        var state=new ObjectManagerSnapshot.SolidContactState(1,List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        assertDifferent(withCaptured(snapshot(),state,null,null,null,null,null),snapshot(),"solidContactState");
    }
    @Test void capturedPlaneCrossingSideMustRemainCompared() {
        var spawn=new com.openggf.level.objects.ObjectSpawn(0x200,0x300,1,0,0,false,0);
        var state=new ObjectManagerSnapshot.PlaneSwitcherSnapshot(List.of(
                new ObjectManagerSnapshot.PlaneSwitcherEntry(spawn,1,true,List.of())));
        assertDifferent(withCaptured(snapshot(),null,state,null,null,null,null),snapshot(),"planeSwitchers");
    }
    @Test void capturedTouchBufferAndParityMustRemainCompared() {
        var state=new ObjectManagerSnapshot.TouchResponseOverlapState(new int[]{6},new int[0],true,List.of());
        assertDifferent(withCaptured(snapshot(),null,null,state,null,null,null),snapshot(),"touchResponseOverlap");
    }
    @Test void capturedNativeCollisionReadAndBuildListsMustRemainCompared() {
        var state=new ObjectManagerSnapshot.CollisionResponseState(List.of(ObjectRefId.dynamic(6,0,1)),
                List.of(),true,0,ObjectManagerSnapshot.CollisionBuildStage.PREVIOUS_READ_FROZEN);
        assertDifferent(withCaptured(snapshot(),null,null,null,state,null,null),snapshot(),"collisionResponseState");
    }
    @Test void capturedNextDynamicIdentityCounterMustRemainCompared() {
        assertDifferent(withCaptured(snapshot(),null,null,null,null,1,null),snapshot(),"dynamicObjectIdCounter");
    }
    @Test void capturedNativeExecutionCursorMustRemainCompared() {
        assertDifferent(withCaptured(snapshot(),null,null,null,null,null,6),snapshot(),"currentExecSlot");
    }
    @Test void onlyDerivedRenderDirtinessAndAllocatorPeakTelemetryMayDiffer() {
        var a=snapshot();
        var b=new ObjectManagerSnapshot(a.usedSlotsBits(),a.slots(),a.frameCounter(),a.vblaCounter(),a.currentExecSlot(),
                90,true,a.childSpawns(),a.dynamicObjects(),a.placement(),a.solidContactRiding(),a.solidContactState(),
                a.planeSwitchers(),a.touchResponseOverlap(),a.dynamicObjectIdCounter(),a.collisionResponseState());
        assertEquivalent(a,b);
    }
    @Test
    void equalNonCachedScalarCountersDoNotAppearAsDiagnosticDifferences() {
        var a = withCaptured(snapshot(), null, null, null, null, 200, 200);
        var changed = new ObjectManagerSnapshot.SolidContactState(1, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var b = withCaptured(a, changed, null, null, null, null, null);
        assertDifferent(a, b, "solidContactState");
        var differences = RewindSnapshotDiff.diffKey("object-manager", a, b);
        assertFalse(differences.stream().anyMatch(diff -> diff.contains("dynamicObjectIdCounter")
                || diff.contains("currentExecSlot")), differences.toString());
    }

    private static ObjectManagerSnapshot withCaptured(ObjectManagerSnapshot a,
            ObjectManagerSnapshot.SolidContactState solids,ObjectManagerSnapshot.PlaneSwitcherSnapshot planes,
            ObjectManagerSnapshot.TouchResponseOverlapState touch,ObjectManagerSnapshot.CollisionResponseState collision,
            Integer counter,Integer cursor) {
        return new ObjectManagerSnapshot(a.usedSlotsBits(),a.slots(),a.frameCounter(),a.vblaCounter(),
                cursor==null?a.currentExecSlot():cursor,a.peakSlotCount(),a.bucketsDirty(),a.childSpawns(),a.dynamicObjects(),
                a.placement(),a.solidContactRiding(),solids==null?a.solidContactState():solids,
                planes==null?a.planeSwitchers():planes,touch==null?a.touchResponseOverlap():touch,
                counter==null?a.dynamicObjectIdCounter():counter,collision==null?a.collisionResponseState():collision);
    }

    private static DynamicObjectEntry entry(String className, int slot, int id, boolean destroyed) {
        var state = new PerObjectRewindSnapshot(destroyed, false, false, 0, 0,
                0, 0, false, -1, false, true, slot, -1, null, null, null);
        // Identity is minted at creation; a later slot move does not change it.
        return new DynamicObjectEntry(className, null, slot, state, null,
                ObjectRefId.dynamic(100, 0, id));
    }

    private static ObjectManagerSnapshot snapshot(DynamicObjectEntry... entries) {
        return new ObjectManagerSnapshot(new long[0], List.of(), 0, 0, 0, 0,
                false, List.of(), List.of(entries), null);
    }

    private static void assertEquivalent(ObjectManagerSnapshot a, ObjectManagerSnapshot b) {
        assertTrue(RewindSnapshotDiff.keyEquals("object-manager", a, b));
        assertTrue(RewindSnapshotDiff.diffKey("object-manager", a, b).isEmpty());
    }

    private static void assertDifferent(ObjectManagerSnapshot a, ObjectManagerSnapshot b, String field) {
        assertFalse(RewindSnapshotDiff.keyEquals("object-manager", a, b));
        var differences = RewindSnapshotDiff.diffKey("object-manager", a, b);
        assertTrue(differences.stream().anyMatch(diff -> diff.contains(field)), differences.toString());
    }
}
