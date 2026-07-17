package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFbzAct2CameraResizeWorker {
    @Test
    void workersPublishAbsoluteFixedPointBoundsWithoutDoubleCounting() {
        RecordingServices services = new RecordingServices();
        services.camera.setX((short) 0x00A2);
        services.camera.setY((short) 0x0540);
        services.camera.setXCopy((short) 0x00A0);
        services.camera.setYCopy((short) 0x053E);
        services.camera.setMaxX((short) 0x3100);
        services.camera.setMinY((short) 0x0500);
        services.camera.setMaxY((short) 0x0540);
        services.camera.setMinXTarget((short) 0x2D10);
        services.camera.setMaxXTarget((short) 0x6200);
        services.camera.setMinYTarget((short) 0x0520);
        services.camera.setMaxYTarget((short) 0x0B00);

        FbzAct2CameraResizeWorker maxX = worker(FbzAct2CameraResizeWorker.MAX_X, services);
        FbzAct2CameraResizeWorker minY = worker(FbzAct2CameraResizeWorker.MIN_Y, services);
        FbzAct2CameraResizeWorker maxY = worker(FbzAct2CameraResizeWorker.MAX_Y, services);
        for (int i = 0; i < 4; i++) {
            maxX.update(i, null);
            minY.update(i, null);
        }
        for (int i = 0; i < 2; i++) maxY.update(i, null);

        assertEquals(0x3101, services.camera.getMaxX() & 0xFFFF);
        assertEquals(0x04FF, services.camera.getMinY() & 0xFFFF);
        assertEquals(0x0541, services.camera.getMaxY() & 0xFFFF);
        assertEquals(0x00A2, services.camera.getX() & 0xFFFF);
        assertEquals(0x0540, services.camera.getY() & 0xFFFF);
        assertEquals(0x00A0, services.camera.getXCopy() & 0xFFFF,
                "Change_Act2Sizes must not overwrite the last ScreenEvents X copy");
        assertEquals(0x053E, services.camera.getYCopy() & 0xFFFF,
                "Change_Act2Sizes must not overwrite the last ScreenEvents Y copy");
        assertEquals(0x2D10, services.camera.getMinXTarget() & 0xFFFF);
        assertEquals(0x6200, services.camera.getMaxXTarget() & 0xFFFF);
        assertEquals(0x0520, services.camera.getMinYTarget() & 0xFFFF);
        assertEquals(0x0B00, services.camera.getMaxYTarget() & 0xFFFF);
    }

    @Test
    void cumulativeHighWordUsesRomOddArithmeticAndPreservesTargets() {
        RecordingServices services = new RecordingServices();
        services.camera.setMaxX((short) 0x3100);
        services.camera.setMaxXTarget((short) 0x6200);
        FbzAct2CameraResizeWorker maxX = worker(FbzAct2CameraResizeWorker.MAX_X, services);

        for (int i = 0; i < 3; i++) {
            maxX.update(i, null);
            assertEquals(0x3100, services.camera.getMaxX() & 0xFFFF,
                    "quarter-pixel accumulator must not move the word before update 4");
        }
        maxX.update(3, null);
        assertEquals(0x3101, services.camera.getMaxX() & 0xFFFF);
        for (int i = 4; i < 8; i++) maxX.update(i, null);

        assertEquals(0x3106, services.camera.getMaxX() & 0xFFFF,
                "ROM reapplies the cumulative high word; this is intentionally non-linear");
        assertEquals(0x6200, services.camera.getMaxXTarget() & 0xFFFF,
                "gradual workers write only Camera_max_X_pos");
    }

    @Test
    void workerStopsAtPublishedStoredTargetRatherThanHardcodedLevelSize() {
        RecordingServices services = new RecordingServices();
        services.camera.setMaxX((short) 0x3100);
        FbzAct2CameraResizeWorker maxX = new FbzAct2CameraResizeWorker(
                FbzAct2CameraResizeWorker.MAX_X, 0x3102);
        maxX.setServices(services);

        for (int i = 0; i < 8 && !maxX.isDestroyed(); i++) maxX.update(i, null);

        assertEquals(0x3102, services.camera.getMaxX() & 0xFFFF);
        assertTrue(maxX.isDestroyed());
    }

    @Test
    void spawnRecreationRetainsEveryWorkerIdentity() {
        for (int boundary = FbzAct2CameraResizeWorker.MAX_X;
             boundary <= FbzAct2CameraResizeWorker.MAX_Y; boundary++) {
            FbzAct2CameraResizeWorker original = new FbzAct2CameraResizeWorker(boundary);
            FbzAct2CameraResizeWorker recreated = new FbzAct2CameraResizeWorker(original.getSpawn());
            assertEquals(boundary, recreated.getSpawn().subtype());
        }
    }

    @Test
    void act2SizeWorkerTableStopsAtFirstAllocationFailure() throws Exception {
        AllocationServices services = new AllocationServices();
        Camera camera = new Camera();
        services.camera = camera;
        services.level = org.mockito.Mockito.mock(Level.class);
        org.mockito.Mockito.when(services.level.getMaxX()).thenReturn(0x6000);
        org.mockito.Mockito.when(services.level.getMinY()).thenReturn(0);
        org.mockito.Mockito.when(services.level.getMaxY()).thenReturn(0x0B00);
        ObjectManager manager = new ObjectManager(List.of(), new S3kNoOpRegistry(), 0,
                null, null, GraphicsManager.getInstance(), camera, services);
        services.manager = manager;

        FbzMinibossInstance owner = new FbzMinibossInstance(
                new ObjectSpawn(0x3000, 0x500, 0x90, 0, 0, false, 0));
        manager.addDynamicObjectAtSlot(owner, 4);
        manager.reserveAllButNFreeSlots(2);

        Method start = FbzMinibossInstance.class.getDeclaredMethod("startAct2Sizes");
        start.setAccessible(true);
        start.invoke(owner);

        List<FbzAct2CameraResizeWorker> workers = manager.activeObjectsOfType(
                FbzAct2CameraResizeWorker.class);
        assertEquals(2, workers.size(), "CreateChild1 must stop the table on the first failed allocation");
        assertEquals(List.of(FbzAct2CameraResizeWorker.MAX_X, FbzAct2CameraResizeWorker.MIN_Y),
                workers.stream().map(worker -> worker.getSpawn().subtype()).toList(),
                "allocation pressure may create only the successful ordered table prefix");
    }

    private static FbzAct2CameraResizeWorker worker(int boundary, RecordingServices services) {
        FbzAct2CameraResizeWorker worker = new FbzAct2CameraResizeWorker(boundary);
        worker.setServices(services);
        return worker;
    }

    private static final class RecordingServices extends TestObjectServices {
        private final Camera camera = new Camera();
        @Override public Camera camera() { return camera; }
    }

    private static final class AllocationServices extends TestObjectServices {
        private ObjectManager manager;
        private Camera camera;
        private Level level;

        @Override public ObjectManager objectManager() { return manager; }
        @Override public Camera camera() { return camera; }
        @Override public Level currentLevel() { return level; }
    }

    private static final class S3kNoOpRegistry implements ObjectRegistry {
        @Override public ObjectInstance create(ObjectSpawn spawn) { return null; }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
        @Override public String getPrimaryName(int objectId) { return "noop"; }
        @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
    }
}
