package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real SST-loop coverage for locked-on AllocateObjectAfterCurrent elevator behavior. */
class TestFbzElevatorObjectManagerIntegration {
    @BeforeEach
    void initGraphics() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x4000, 0x1000, 0);
    }

    @AfterEach
    void resetGraphics() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void exhaustedAfterCurrentSlotsDoNotRetryUntilTheNextNinetySixFrameCadence() {
        Harness harness = harness(0x1000);
        FbzElevatorObjectInstance controller = controller(harness.manager(), 40, 0x0F, 0);
        List<DummyObject> pressure = new ArrayList<>();
        for (int slot = 41; slot < 94; slot++) {
            DummyObject dummy = new DummyObject(spawn(0, 0));
            pressure.add(dummy);
            harness.manager().addDynamicObjectAtSlot(dummy, slot);
        }

        harness.manager().update(0x1000, null, null, 1);
        assertEquals(0x5F, controller.spawnTimer());
        assertTrue(harness.manager().activeObjectsOfType(FbzElevatorObjectInstance.Car.class).isEmpty());

        harness.manager().removeDynamicObject(pressure.getFirst());
        for (int frame = 0; frame < 95; frame++) harness.manager().update(0x1000, null, null, frame + 2);
        assertTrue(harness.manager().activeObjectsOfType(FbzElevatorObjectInstance.Car.class).isEmpty(),
                "loc_3CA20 resets $30 before failed allocation and must not retry early");

        harness.manager().update(0x1000, null, null, 97);
        FbzElevatorObjectInstance.Car car = harness.manager()
                .activeObjectsOfType(FbzElevatorObjectInstance.Car.class).getFirst();
        assertEquals(41, car.getSlotIndex());
        assertEquals(0x0F * 8 - 1, car.travelTimer());
        assertEquals(0x06FF, car.getCentreY(),
                "the newly allocated higher SST executes later in this same frame");
    }

    @Test
    void spriteOnScreenTestRemovesExpiredCarOnlyAfterItsFinalMovement() {
        Harness harness = harness(0);
        FbzElevatorObjectInstance.Car car = new FbzElevatorObjectInstance.Car(0, 0x700, 1, 1);
        harness.manager().addDynamicObjectAtSlot(car, 40);

        harness.manager().update(0, null, null, 1);
        assertEquals(0x701, car.getCentreY());
        assertFalse(car.isDestroyed());

        harness.manager().update(0, null, null, 2);
        assertEquals(0x702, car.getCentreY());
        assertTrue(harness.manager().activeObjectsOfType(FbzElevatorObjectInstance.Car.class).isEmpty());
    }

    @Test
    void forcedRecreationRoundTripsIndependentControllerAndCarSstWords() {
        Harness harness = harness(0x1000);
        harness.manager().setRewindInPlaceRestoreEnabledForTest(false);
        FbzElevatorObjectInstance originalController = controller(harness.manager(), 40, 0x24, 1);
        harness.manager().update(0x1000, null, null, 1);
        FbzElevatorObjectInstance.Car originalCar = harness.manager()
                .activeObjectsOfType(FbzElevatorObjectInstance.Car.class).getFirst();
        assertEquals(1, originalCar.yVelocity());

        int capturedControllerTimer = originalController.spawnTimer();
        int capturedCarTimer = originalCar.travelTimer();
        int capturedCarX = originalCar.getCentreX();
        int capturedCarY = originalCar.getCentreY();
        int capturedCarSlot = originalCar.getSlotIndex();
        RewindRegistry registry = new RewindRegistry();
        registry.register(harness.manager().rewindSnapshottable());
        CompositeSnapshot snapshot = registry.capture();

        for (int frame = 0; frame < 8; frame++) harness.manager().update(0x1000, null, null, frame + 2);
        registry.restore(snapshot);

        FbzElevatorObjectInstance restoredController = harness.manager()
                .activeObjectsOfType(FbzElevatorObjectInstance.class).getFirst();
        FbzElevatorObjectInstance.Car restoredCar = harness.manager()
                .activeObjectsOfType(FbzElevatorObjectInstance.Car.class).getFirst();
        assertNotSame(originalController, restoredController);
        assertNotSame(originalCar, restoredCar);
        assertEquals(capturedControllerTimer, restoredController.spawnTimer());
        assertEquals(capturedCarSlot, restoredCar.getSlotIndex());
        assertEquals(capturedCarTimer, restoredCar.travelTimer());
        assertEquals(1, restoredCar.yVelocity());
        assertEquals(capturedCarX, restoredCar.getCentreX());
        assertEquals(capturedCarY, restoredCar.getCentreY());
    }

    private static FbzElevatorObjectInstance controller(
            ObjectManager manager, int slot, int subtype, int renderFlags) {
        FbzElevatorObjectInstance controller = new FbzElevatorObjectInstance(new ObjectSpawn(
                0x1000, 0x0700, Sonic3kObjectIds.FBZ_ELEVATOR,
                subtype, renderFlags, false, 0));
        manager.addDynamicObjectAtSlot(controller, slot);
        return controller;
    }

    private static ObjectSpawn spawn(int subtype, int renderFlags) {
        return new ObjectSpawn(0x1000, 0x0700, 0,
                subtype, renderFlags, false, 0);
    }

    private static Harness harness(int cameraX) {
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = new Camera() {
            @Override public short getX() { return (short) cameraX; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
            @Override public ObjectPlayerQuery playerQuery() {
                return new ObjectPlayerQuery(() -> null, List::of);
            }
        };
        ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;
        manager.reset(0);
        return new Harness(manager);
    }

    private record Harness(ObjectManager manager) { }

    private static final class DummyObject extends AbstractObjectInstance {
        private DummyObject(ObjectSpawn spawn) { super(spawn, "FBZElevatorSlotPressure"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
