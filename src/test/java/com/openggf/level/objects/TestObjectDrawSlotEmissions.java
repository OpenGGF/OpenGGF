package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.render.SpriteSlotEmission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        var entries = List.of(entry(80), entry(30), nativeEntry(60, 3), nativeEntry(70, 2),
                nativeEntry(40, 1), nativeEntry(70, 0));
        graphics.beginSpriteSatCollection();
        draw(entries, List.of(new SpriteSlotEmission(50, 2, 0, () -> observed.add(50)),
                new SpriteSlotEmission(70, 2, 0, () -> observed.add(-70))));
        graphics.endSpriteSatCollectionAndReplay();
        assertEquals(List.of(70, 40, 70, 60, 30, 50, 80), observed);
    }

    @Test void queuedCurrentOccupantSuppressesItsInferredLegacyCopyWithoutDroppingRepeatedCalls() throws Exception {
        var entries = List.of(entry(70), entry(30), nativeEntry(70, 2), nativeEntry(40, 1), nativeEntry(70, 0));
        graphics.beginSpriteSatCollection();
        draw(entries, List.of());
        graphics.endSpriteSatCollectionAndReplay();
        assertEquals(List.of(70, 40, 70, 30), observed);
        observed.clear();
        draw(entries, List.of());
        assertEquals(List.of(30, 70, 40, 70), observed);
    }

    @Test void queuedBucketRetainsTheSlotWhenCurrentOccupantInfersADifferentBucket() throws Exception {
        graphics.beginSpriteSatCollection();
        draw(2, List.of(nativeEntry(50, 0)), List.of());
        draw(3, List.of(entry(80), entry(50)), List.of());
        graphics.endSpriteSatCollectionAndReplay();
        assertEquals(List.of(50, 80), observed, "the admitted bucket owns the slot across the whole table");
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
