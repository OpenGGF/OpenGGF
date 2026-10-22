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
        var entries = List.of(entry(60), entry(70), entry(40), entry(70));
        graphics.beginSpriteSatCollection();
        draw(entries, List.of(new SpriteSlotEmission(50, 2, 0, () -> observed.add(50))));
        graphics.endSpriteSatCollectionAndReplay();
        assertEquals(List.of(50, 70, 40, 70, 60), observed,
                "legacy external anchoring may insert an entry but cannot reorder or deduplicate queued pointers");
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

    private void draw(List<ObjectRenderParts.RenderEntry> entries, List<SpriteSlotEmission> external) throws Exception {
        Method method = ObjectManager.class.getDeclaredMethod("drawBucketInstancesWithPriority",
                List.class, int.class, GraphicsManager.class, List.class);
        method.setAccessible(true);
        method.invoke(manager, entries, 2, graphics, external);
    }
}
