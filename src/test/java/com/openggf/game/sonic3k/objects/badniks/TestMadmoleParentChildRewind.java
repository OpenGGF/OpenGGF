package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Madmole cap (Obj_Madmole) and body (loc_8D602) as separate SST objects:
 * slot layout, capture/restore of both objects and the parent3 busy-bit link,
 * and forward replay after restore.
 */
class TestMadmoleParentChildRewind {
    private static final ObjectSpawn CAP_SPAWN =
            new ObjectSpawn(0x3100, 0x0380, Sonic3kObjectIds.MADMOLE, 0, 0, false, 0x0380);

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0x3000, 0x0300, 0x3140, 0x03E0, 0);
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void bodyTakesItsOwnSlotAfterTheCapAndBothRewindAndReplay() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x3100, (short) 0x0380);
        Harness harness = Harness.create(player);
        ObjectManager objectManager = harness.objectManager;
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);
        MadmoleBadnikInstance cap = objectManager.createDynamicObject(() -> new MadmoleBadnikInstance(CAP_SPAWN));

        int frame = 0;
        cap.update(frame++, player);
        cap.update(frame++, player);
        cap.update(frame, player);
        MadmoleBadnikInstance.MadmoleBodyChild body = liveBody(objectManager);
        assertNotNull(body, "loc_8D5BE allocates the body as its own object");
        assertTrue(body.getSlotIndex() > cap.getSlotIndex(),
                "AllocateObjectAfterCurrent gives the body a slot after the cap: cap="
                        + cap.getSlotIndex() + " body=" + body.getSlotIndex());
        assertSame(cap, body.parentForTests());
        body.update(frame++, player);

        // Advance into the pause so the capture holds non-initial body and cap state.
        for (int i = 0; i < 0x25; i++) {
            step(cap, body, frame++, player);
        }
        assertEquals("PAUSING", body.getStateName());

        RewindRegistry registry = new RewindRegistry();
        registry.register(objectManager.rewindSnapshottable());
        CompositeSnapshot snapshot = registry.capture();
        int captureFrame = frame;
        List<String> expected = runForward(cap, body, captureFrame, player, 0x90);

        registry.restore(snapshot);

        MadmoleBadnikInstance restoredCap = onlyLive(objectManager, MadmoleBadnikInstance.class);
        MadmoleBadnikInstance.MadmoleBodyChild restoredBody = liveBody(objectManager);
        assertNotNull(restoredBody, "restore must recreate the body object");
        assertNotSame(body, restoredBody);
        assertSame(restoredCap, restoredBody.parentForTests(),
                "the recreated body must relink parent3 to the restored cap");
        assertTrue(restoredCap.isBodyBusy(), "the cap's $38 bit 1 must restore");
        assertEquals("PAUSING", restoredBody.getStateName());

        List<String> replay = runForward(restoredCap, restoredBody, captureFrame, player, 0x90);
        assertEquals(expected, replay, "forward replay after restore must match the original run");
    }

    private static List<String> runForward(MadmoleBadnikInstance cap, MadmoleBadnikInstance.MadmoleBodyChild body,
            int startFrame, TestablePlayableSprite player, int frames) {
        List<String> trace = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            step(cap, body, startFrame + i, player);
            trace.add(cap.getStateName() + ":" + cap.getTimer() + ":" + cap.isBodyBusy()
                    + "|" + body.getStateName() + ":" + body.getY() + ":" + body.getTimer()
                    + ":" + body.getMappingFrame() + ":" + body.isDeletePending() + ":" + body.isDestroyed());
        }
        return trace;
    }

    private static void step(MadmoleBadnikInstance cap, MadmoleBadnikInstance.MadmoleBodyChild body,
            int frame, TestablePlayableSprite player) {
        cap.update(frame, player);
        body.update(frame, player);
    }

    private static MadmoleBadnikInstance.MadmoleBodyChild liveBody(ObjectManager objectManager) {
        return objectManager.getActiveObjects().stream()
                .filter(MadmoleBadnikInstance.MadmoleBodyChild.class::isInstance)
                .map(MadmoleBadnikInstance.MadmoleBodyChild.class::cast)
                .filter(o -> !o.isDestroyed())
                .findFirst()
                .orElse(null);
    }

    private static <T extends ObjectInstance> T onlyLive(ObjectManager objectManager, Class<T> type) {
        List<T> matches = objectManager.getActiveObjects().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(object -> !object.isDestroyed())
                .toList();
        assertEquals(1, matches.size(), "restore must leave exactly one live " + type.getSimpleName());
        return matches.getFirst();
    }

    private static final class Harness {
        private final ObjectManager objectManager;

        private Harness(ObjectManager objectManager) {
            this.objectManager = objectManager;
        }

        static Harness create(AbstractPlayableSprite focusedPlayer) {
            TestCamera camera = new TestCamera();
            camera.setFocusedSprite(focusedPlayer);
            Services services = new Services(camera);
            services.withGameState(mock(GameStateManager.class));
            ObjectManager objectManager = new ObjectManager(
                    List.of(), null, 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            services.objectManager = objectManager;
            objectManager.reset(camera.getX());
            return new Harness(objectManager);
        }
    }

    private static final class Services extends TestObjectServices {
        private ObjectManager objectManager;
        private final Camera camera;

        private Services(Camera camera) {
            this.camera = camera;
        }

        @Override public ObjectManager objectManager() { return objectManager; }
        @Override public Camera camera() { return camera; }
        @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
    }

    private static final class TestCamera extends Camera {
        private AbstractPlayableSprite focusedSprite;

        @Override public void setFocusedSprite(AbstractPlayableSprite sprite) { focusedSprite = sprite; }
        @Override public AbstractPlayableSprite getFocusedSprite() { return focusedSprite; }
        @Override public short getX() { return 0x3000; }
        @Override public short getY() { return 0x0300; }
        @Override public short getWidth() { return 320; }
        @Override public short getHeight() { return 224; }
        @Override public boolean isVerticalWrapEnabled() { return false; }
    }
}
