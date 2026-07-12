package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.camera.Camera;
import com.openggf.graphics.GraphicsManager;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import java.util.List;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.game.sonic3k.scroll.SwScrlFbz;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzOutdoorBgMotion {
    @Test
    void gradualSwingUsesRomInitialVelocityAndAcceleration() {
        FbzOutdoorBgMotionObjectInstance object = new FbzOutdoorBgMotionObjectInstance();
        assertEquals(0, object.advanceMotion());
        assertEquals(-1, object.advanceMotion());
        assertEquals(-1, object.advanceMotion());
        assertEquals(-0x2700, object.getSwingVelocity());
    }

    @Test
    void controllerPersistsAndIsRewindRecreatable() {
        FbzOutdoorBgMotionObjectInstance object = new FbzOutdoorBgMotionObjectInstance();
        assertInstanceOf(RewindRecreatable.class, object);
        for (int i = 0; i < 4096; i++) object.advanceMotion();
        assertFalse(object.isDestroyed());
    }

    @Test
    void objectPublishesBeforeSameFrameOutdoorDeformationConsumesBob() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(0);
        events.setBackgroundOutdoor(true);
        FbzZoneRuntimeState state = new FbzZoneRuntimeState(0, PlayerCharacter.SONIC_AND_TAILS, events);
        StubObjectServices services = new StubObjectServices();
        services.zoneRuntimeRegistry().install(state);
        FbzOutdoorBgMotionObjectInstance object = new FbzOutdoorBgMotionObjectInstance();
        object.setServices(services);
        object.update(0, null);
        object.update(1, null); // publishes -1 before the later deform phase

        SwScrlFbz deform = new SwScrlFbz(() -> state);
        deform.init(0, 0, 0);
        deform.update(new int[224], 0, 0, 1, 0);
        assertEquals(-1, state.outdoorBobOffset());
        assertEquals(0x15, deform.getVscrollFactorBG() & 0xFFFF);
    }

    @Test
    void objectGraphAndEventAllocationStateRestoreCoherently() {
        GraphicsManager.getInstance().initHeadless();
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = new Camera();
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
        };
        ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;
        manager.reset(0);
        FbzOutdoorBgMotionObjectInstance object = manager.createDynamicObject(FbzOutdoorBgMotionObjectInstance::new);
        for (int i = 0; i < 17; i++) object.advanceMotion();
        int velocity = object.getSwingVelocity();
        int position = object.getSwingPosition();
        boolean returning = object.isReturning();
        RewindRegistry rewind = new RewindRegistry();
        rewind.register(manager.rewindSnapshottable());
        CompositeSnapshot graph = rewind.capture();

        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(0);
        events.restoreOutdoorMotionAllocationState(true, true);
        FbzZoneRuntimeState runtime = new FbzZoneRuntimeState(0, PlayerCharacter.SONIC_ALONE, events);
        byte[] eventState = runtime.captureBytes();
        object.advanceMotion();
        manager.removeDynamicObject(object);
        events.restoreOutdoorMotionAllocationState(false, false);

        rewind.restore(graph);
        runtime.restoreBytes(eventState);
        FbzOutdoorBgMotionObjectInstance restored = manager.getActiveObjects().stream()
                .filter(FbzOutdoorBgMotionObjectInstance.class::isInstance)
                .map(FbzOutdoorBgMotionObjectInstance.class::cast).findFirst().orElseThrow();
        assertEquals(velocity, restored.getSwingVelocity());
        assertEquals(position, restored.getSwingPosition());
        assertEquals(returning, restored.isReturning());
        assertTrue(events.isOutdoorMotionAllocationAttempted());
        assertTrue(events.isOutdoorMotionSpawned());
    }
}
