package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.render.SpriteSlotEmission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/** Render traversal must never turn explicit DisplaySprite FIFO back into SST sort order. */
class TestObjectDrawSlotEmissions {
    private GraphicsManager graphics;
    private ObjectManager manager;
    private final List<Integer> observed = new ArrayList<>();

    @BeforeEach void setUp() {
        graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
        Camera camera = mock(Camera.class);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        manager = new ObjectManager(List.of(), null, 0, null, null,
                graphics, camera, new StubObjectServices());
    }

    @AfterEach void tearDown() { graphics.resetState(); }

    @Test void nativeTraversalRetainsRepeatedAndInvertedSubmissionOrder() throws Exception {
        // A cached painter list reverses the actual FIFO [70, 40, 70, 60].
        var entries = List.of(nativeEntry(60, 3), nativeEntry(70, 2), nativeEntry(40, 1), nativeEntry(70, 0));
        graphics.beginSpriteSatCollection();
        draw(entries, List.of(new SpriteSlotEmission(50, 2, 0, () -> observed.add(50))));
        graphics.endSpriteSatCollectionAndReplay();
        assertEquals(List.of(70, 40, 70, 60, 50), observed,
                "legacy external anchoring may insert an entry but cannot reorder or deduplicate queued pointers");
    }

    @Test void legacyExternalAnchorsStayInLegacySuffixAndQueuedSlotsAreNotDrawnTwice() throws Exception {
        register(80, 2);
        register(30, 2);
        var seventy = register(70, 2);
        var forty = register(40, 2);
        var sixty = register(60, 2);
        admit(2, seventy, forty, seventy, sixty);
        var ledger = NativeSpriteSubmissionAccess.admissions(manager);
        assertEquals(List.of(new NativeSpriteSubmissionAccess.Admission(70, 2),
                new NativeSpriteSubmissionAccess.Admission(40, 2),
                new NativeSpriteSubmissionAccess.Admission(70, 2),
                new NativeSpriteSubmissionAccess.Admission(60, 2)), ledger);
        graphics.beginSpriteSatCollection();
        ObjectDrawInternalAccess.drawBucket(manager, 2, graphics,
                List.of(new SpriteSlotEmission(50, 2, 0, () -> observed.add(50)),
                new SpriteSlotEmission(70, 2, 0, () -> observed.add(-70))));
        graphics.endSpriteSatCollectionAndReplay();
        assertEquals(List.of(70, 40, 70, 60, 30, 50, 80), observed);
        assertEquals(ledger, NativeSpriteSubmissionAccess.admissions(manager));
    }

    @Test void queuedCurrentOccupantSuppressesItsInferredLegacyCopyWithoutDroppingRepeatedCalls() throws Exception {
        var seventy = register(70, 2);
        var forty = register(40, 2);
        register(30, 2);
        admit(2, seventy, forty, seventy);
        var ledger = NativeSpriteSubmissionAccess.admissions(manager);
        assertEquals(List.of(new NativeSpriteSubmissionAccess.Admission(70, 2),
                new NativeSpriteSubmissionAccess.Admission(40, 2),
                new NativeSpriteSubmissionAccess.Admission(70, 2)), ledger);
        graphics.beginSpriteSatCollection();
        ObjectDrawInternalAccess.drawBucket(manager, 2, graphics, List.of());
        graphics.endSpriteSatCollectionAndReplay();
        assertEquals(List.of(70, 40, 70, 30), observed);
        observed.clear();
        ObjectDrawInternalAccess.drawBucket(manager, 2, graphics, List.of());
        assertEquals(List.of(30, 70, 40, 70), observed);
        assertEquals(ledger, NativeSpriteSubmissionAccess.admissions(manager));
    }

    @Test void queuedBucketRetainsTheSlotWhenCurrentOccupantInfersADifferentBucket() throws Exception {
        var fifty = register(50, 2);
        register(80, 3);
        admit(2, fifty);
        fifty.bucket = 3;
        assertEquals(3, fifty.getPriorityBucket());
        var ledger = NativeSpriteSubmissionAccess.admissions(manager);
        assertEquals(List.of(new NativeSpriteSubmissionAccess.Admission(50, 2)), ledger);
        graphics.beginSpriteSatCollection();
        ObjectDrawInternalAccess.drawBucket(manager, 2, graphics, List.of());
        ObjectDrawInternalAccess.drawBucket(manager, 3, graphics, List.of());
        graphics.endSpriteSatCollectionAndReplay();
        assertEquals(List.of(50, 80), observed, "the admitted bucket owns the slot across the whole table");
        assertEquals(ledger, NativeSpriteSubmissionAccess.admissions(manager));
    }

    @Test void legacySlotBridgePreservesOppositePainterAndSatOrder() throws Exception {
        var entries = List.of(entry(60), entry(40));
        var external = List.of(new SpriteSlotEmission(50, 2, 0, () -> observed.add(50)));
        draw(entries, external);
        assertEquals(List.of(60, 50, 40), observed);
        observed.clear();
        graphics.beginSpriteSatCollection();
        draw(entries, external);
        graphics.endSpriteSatCollectionAndReplay();
        assertEquals(List.of(40, 50, 60), observed);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void wholePriorityTraversalRetainsPublicBucketOverrideDispatch(boolean high) {
        var custom = new ObjectManager(List.of(), null, 0, null, null,
                graphics, mock(Camera.class), new StubObjectServices()) {
            @Override public void drawPriorityBucket(int bucket, boolean highPriority) {
                observed.add((highPriority ? 100 : 0) + bucket);
            }
        };
        if (high) custom.drawHighPriority();
        else custom.drawLowPriority();
        int offset = high ? 100 : 0;
        assertEquals(List.of(offset + 7, offset + 6, offset + 5, offset + 4,
                offset + 3, offset + 2, offset + 1, offset), observed);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void callbackCreatedObjectIsVisibleInTheNextBucketOfTheSameTraversal(boolean high) {
        var later = new AbstractObjectInstance(new ObjectSpawn(128, 128, 1, 0, 0, false, 0), "later bucket") {
            @Override public int getPriorityBucket() { return 6; }
            @Override public boolean isHighPriority() { return high; }
            @Override public void appendRenderCommands(List<GLCommand> commands) { observed.add(6); }
        };
        var first = new AbstractObjectInstance(new ObjectSpawn(128, 128, 1, 0, 0, false, 0), "first bucket") {
            @Override public int getPriorityBucket() { return 7; }
            @Override public boolean isHighPriority() { return high; }
            @Override public void appendRenderCommands(List<GLCommand> commands) {
                observed.add(7);
                manager.addDynamicObject(later);
            }
        };
        manager.addDynamicObject(first);
        if (high) manager.drawHighPriority();
        else manager.drawLowPriority();
        assertEquals(List.of(7, 6), observed,
                "a callback invalidation must be observed before the following priority bucket");
    }

    @Test void externalFailureRestoresOwnedWrapWhileMaskChangesUseSuppliedGraphics() {
        GraphicsManager owner = mock(GraphicsManager.class);
        GraphicsManager supplied = mock(GraphicsManager.class);
        Camera camera = mock(Camera.class);
        when(camera.isVerticalWrapEnabled()).thenReturn(true);
        when(camera.getVerticalWrapRange()).thenReturn(2048);
        when(camera.getY()).thenReturn((short) 128);
        var separate = new ObjectManager(List.of(), null, 0, null, null,
                owner, camera, new StubObjectServices());
        var failure = new IllegalStateException("emission failure");
        assertEquals(failure, assertThrows(IllegalStateException.class,
                () -> ObjectDrawInternalAccess.drawBucket(separate, 2, supplied,
                        List.of(new SpriteSlotEmission(50, 2, 2, () -> { throw failure; })))));
        verify(owner).enableVerticalWrapAdjust(2048, (short) 128);
        verify(owner).disableVerticalWrapAdjust();
        verify(supplied).flushPatternBatch();
        verify(supplied).setCurrentSpriteTileOcclusionPaletteMask(2);
        verify(supplied).beginPatternBatch();
        verify(owner, never()).setCurrentSpriteTileOcclusionPaletteMask(anyInt());
        verify(supplied, never()).disableVerticalWrapAdjust();
        verify(supplied, never()).enableVerticalWrapAdjust(anyInt(), anyInt());
    }

    private RegisteredSlotProbe register(int slot, int bucket) {
        var owner = new RegisteredSlotProbe(bucket);
        manager.addDynamicObjectAtSlot(owner, slot);
        assertEquals(slot, owner.getSlotIndex());
        assertSame(owner, manager.currentNativeSstOwner(slot));
        return owner;
    }

    private void admit(int bucket, RegisteredSlotProbe... owners) {
        for (var owner : owners) {
            assertSame(owner, manager.currentNativeSstOwner(owner.getSlotIndex()));
            assertTrue(NativeSpriteSubmissionAccess.submit(manager, owner.getSlotIndex(), bucket));
        }
    }

    private final class RegisteredSlotProbe extends AbstractObjectInstance {
        private int bucket;
        private RegisteredSlotProbe(int bucket) {
            super(new ObjectSpawn(128, 128, 1, 0, 0, false, 0), "registered queue probe");
            this.bucket = bucket;
        }
        @Override public int getPriorityBucket() { return bucket; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { observed.add(getSlotIndex()); }
    }

    private ObjectRenderParts.RenderEntry entry(int slot) {
        var object = new AbstractObjectInstance(new ObjectSpawn(128, 128, 1, 0, 0, false, 0), "queue probe") {
            @Override public void appendRenderCommands(List<GLCommand> commands) { observed.add(slot); }
        };
        return new ObjectRenderParts.RenderEntry(object, false, slot);
    }

    private ObjectRenderParts.RenderEntry nativeEntry(int slot, int ordinal) {
        var legacy = entry(slot);
        return new ObjectRenderParts.RenderEntry(legacy.instance(), false, slot, true, ordinal);
    }

    private void draw(List<ObjectRenderParts.RenderEntry> entries, List<SpriteSlotEmission> external) throws Exception {
        draw(2, entries, external);
    }

    private void draw(int bucket, List<ObjectRenderParts.RenderEntry> entries, List<SpriteSlotEmission> external) throws Exception {
        var field = ObjectManager.class.getDeclaredField("unifiedBuckets");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ObjectRenderParts.RenderEntry>[] buckets = (List<ObjectRenderParts.RenderEntry>[]) field.get(manager);
        buckets[bucket].clear();
        buckets[bucket].addAll(entries);
        Method method = ObjectManager.class.getDeclaredMethod("drawBucketInstancesWithPriority",
                List.class, int.class, GraphicsManager.class, List.class);
        method.setAccessible(true);
        method.invoke(manager, entries, bucket, graphics, external);
    }
}
