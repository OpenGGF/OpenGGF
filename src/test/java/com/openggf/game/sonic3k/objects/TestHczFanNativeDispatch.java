package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import com.openggf.camera.Camera;
import com.openggf.graphics.GLCommand;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Native Obj_HCZCGZFan allocation/dispatch witnesses independent of a movie. */
class TestHczFanNativeDispatch {
    private static final int X = 0x1000;
    private static final int FAN_Y = 0x800;
    private static final int PLATFORM_Y = FAN_Y + 0x1C;

    @AfterEach
    void resetGraphics() {
        GraphicsManager.getInstance().resetState();
        AbstractObjectInstance.resetCameraBoundsForTests();
    }

    @Test
    void platformOwnsOriginalSlotAndInitializationDoesNotRunPlatformMain() {
        var h = harness();
        var platform = h.manager.createDynamicObjectAtSlot(
                () -> h.registry.create(spawn(0xA4)), 14);
        assertInstanceOf(SolidObjectProvider.class, platform,
                "Obj_HCZCGZFan changes a0 into the platform, not the allocated a1");
        assertEquals(1, h.manager.getActiveObjects().size(), "constructor is not native dispatch");
        var player = player(PLATFORM_Y + 0x40);
        platform.update(0, player);
        assertEquals(PLATFORM_Y, platform.getY());
        assertEquals(X, platform.getX(), "initializer returns before slide/latch/solid work");
        var fan = onlyFan(h.manager);
        assertTrue(fan.getSlotIndex() > ((AbstractObjectInstance) platform).getSlotIndex());
        assertEquals(FAN_Y, fan.getY());
        assertEquals(2, h.manager.getActiveObjects().size());
        platform.update(1, player);
        assertEquals(X + 8, platform.getX());
        assertEquals(platform.getX(), fan.getX());
        player.setCentreY((short) (PLATFORM_Y - 0x40));
        player.setYSpeed((short) 0x200);
        platform.update(2, player);
        assertEquals(X, platform.getX());
        assertEquals(PLATFORM_Y - 0x40, player.getCentreY(), "platform Main does not run fan Main");
        fan.update(2, player);
        assertEquals(0, player.getYSpeed(), "later initialized child executes Main without another init return");
        assertTrue(player.getCentreY() < PLATFORM_Y - 0x40);
    }

    @Test
    void rejectedAllocationRetriesAfterCapacityReturnsAndFreshRestoreReplaysRetry() {
        var h = harness();
        var platform = h.manager.createDynamicObjectAtSlot(
                () -> h.registry.create(spawn(0xA4)), 14);
        fillSlots(h.manager);
        var rewind = new RewindRegistry();
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        rewind.register(h.manager.rewindSnapshottable());
        CompositeSnapshot before = rewind.capture();
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) {
                rewind.restore(before);
                platform = h.manager.getActiveObjects().stream().filter(o -> o instanceof SolidObjectProvider)
                        .findFirst().orElseThrow();
            }
            platform.update(0, null);
            assertEquals(FAN_Y, platform.getY(), "failed AllocateObjectAfterCurrent leaves initializer installed");
            assertTrue(h.manager.activeObjectsOfType(HCZCGZFanObjectInstance.class).isEmpty());
            freeNoiseSlot(h.manager, 50);
            platform.update(1, null);
            assertEquals(PLATFORM_Y, platform.getY());
            assertEquals(50, onlyFan(h.manager).getSlotIndex());
            assertEquals(1, h.manager.activeObjectsOfType(HCZCGZFanObjectInstance.class).size());
        }
    }

    @Test
    void earlierPlatformReadsThresholdBeforeLaterFanLiftAndFreshRestoreReplaysOrder() {
        var h = harness();
        var platform = h.manager.createDynamicObjectAtSlot(
                () -> h.registry.create(spawn(0xA4)), 14);
        var lower = h.manager.createDynamicObjectAtSlot(() -> new HCZCGZFanObjectInstance(
                new ObjectSpawn(X, FAN_Y + 0x80, Sonic3kObjectIds.HCZ_CGZ_FAN, 0x16, 0, false, 0)), 28);
        lower.update(0, null);
        fillSlots(h.manager);
        freeNoiseSlot(h.manager, 50);
        platform.update(0, null);
        assertTrue(h.manager.activeObjectsOfType(HCZCGZFanObjectInstance.class).stream()
                .anyMatch(o -> o.getSlotIndex() == 50),
                "the later slot must execute fan Main, after the lower fan at slot28");
        var fan = onlyFanAt(h.manager, 50);
        fan.setLatchedOn(true);
        var rewind = new RewindRegistry();
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        rewind.register(h.manager.rewindSnapshottable());
        var before = rewind.capture();
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) rewind.restore(before);
            var player = player(PLATFORM_Y - 0x30);
            h.manager.update(X - 0xA0, player, List.of(), 1, false, false, false);
            assertEquals(PLATFORM_Y - 0x32, player.getCentreY(),
                    "only the lower fan lifts: platform sees exactly -$30 before slot28 moves P1");
            assertEquals(X, onlyFanAt(h.manager, 50).getX(), "exact threshold retains the latch");
        }
    }

    private static HCZCGZFanObjectInstance onlyFan(ObjectManager manager) {
        var fans = manager.activeObjectsOfType(HCZCGZFanObjectInstance.class);
        assertEquals(1, fans.size());
        return fans.getFirst();
    }
    private static HCZCGZFanObjectInstance onlyFanAt(ObjectManager manager, int slot) {
        return manager.activeObjectsOfType(HCZCGZFanObjectInstance.class).stream()
                .filter(o -> o.getSlotIndex() == slot).findFirst().orElseThrow();
    }
    private static ObjectSpawn spawn(int subtype) {
        return new ObjectSpawn(X, FAN_Y, Sonic3kObjectIds.HCZ_CGZ_FAN, subtype, 0, false, 0);
    }
    private static TestablePlayableSprite player(int y) {
        var p = new TestablePlayableSprite("sonic", (short) X, (short) y);
        p.setCentreX((short) X); p.setCentreY((short) y);
        return p;
    }
    private static void fillSlots(ObjectManager manager) {
        while (manager.createDynamicObjectWithReservedSlot(Noise::new) != null) { }
    }

    private static void freeNoiseSlot(ObjectManager manager, int slot) {
        var noise = manager.activeObjectsOfType(Noise.class).stream()
                .filter(o -> o.getSlotIndex() == slot).findFirst().orElseThrow();
        manager.removeDynamicObject(noise);
    }

    private static final class Noise extends AbstractObjectInstance implements RewindRecreatable {
        private Noise() { super(new ObjectSpawn(X, FAN_Y, 0, 0, 0, false, 0), "slot occupant"); }
        @Override public Noise recreateForRewind(RewindRecreateContext ctx) { return new Noise(); }
        @Override public void update(int clock, com.openggf.game.PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private record Harness(ObjectManager manager, Sonic3kObjectRegistry registry) { }
    private static Harness harness() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        com.openggf.game.OscillationManager.reset();
        GraphicsManager.getInstance().initHeadless();
        var camera = new Camera() {
            @Override public short getX() { return X - 0xA0; }
            @Override public short getY() { return FAN_Y - 0x80; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
        var services = new TestObjectServices().withCamera(camera);
        var registry = new Sonic3kObjectRegistry();
        var manager = new ObjectManager(List.of(), registry, 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        services.withDirectObjectManager(manager);
        return new Harness(manager, registry);
    }
}
