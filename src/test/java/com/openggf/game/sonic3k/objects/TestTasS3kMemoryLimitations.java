package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.*;
import com.openggf.level.resources.KosinskiModuleQueue;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** TAS 10042S/6655S/9155S: bounded native slot-read parity and the unchanged KosM limit.
 * Native oracle: locked-on 8b2cf3c, Obj_CutsceneButton/loc_65C04, Queue_Kos_Module.
 * No emulator RAM or dirty disassembly is used as an engine fixture. */
@RequiresRom(SonicGame.SONIC_3K)
class TestTasS3kMemoryLimitations {
    @BeforeEach void setup() { TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule()); }

    @AfterEach void cleanup() { Aiz2BossEndSequenceState.reset(); }

    @Test void selfOccupiedCutsceneSlotPressesButtonAfterRealKnucklesRetirement() {
        try (var reel = com.openggf.tests.reel.S3kTestReelPresentation.open()) {
        var camera = GameServices.camera(); camera.setX((short) 0x4A00);
        var services = new TestObjectServices().withCamera(camera);
        var manager = manager(camera, services);
        var button = new S3kCutsceneButtonObjectInstance(new ObjectSpawn(0x4B18,0x189,0x83,0,0,false,0));
        Aiz2BossEndSequenceState.reset();
        // Real production occupants fill the earlier allocator positions.
        for (int i = 0; i < 2; i++) manager.addDynamicObject(new S3kCutsceneButtonObjectInstance(
                new ObjectSpawn(0x4000, 0x100, 0x83, 0, 0, false, 0)));
        var knuckles = manager.createDynamicObject(CutsceneKnucklesAiz2Instance::createDefault);
        assertEquals(6, knuckles.getSlotIndex());
        knuckles.update(0, null); // Obj_CutsceneKnuckles writes its actual allocated slot.
        manager.removeDynamicObject(knuckles);
        manager.addDynamicObject(button); // ordinary allocator actually reuses the released slot
        var sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        sonic.setControlLocked(true);
        assertEquals(6, button.getSlotIndex());
        assertTrue(manager.getActiveObjects().contains(button));
        button.update(0, sonic); reel.afterObjects(List.of(button),0,1,0x4A78,0x119,Sonic3kObjectArtKeys.BUTTON);
        // Native retained _unkFAA4 reads the new occupant's x/y (dx=dy=0).
        assertTrue(Aiz2BossEndSequenceState.isButtonPressed(), "native raw slot alias presses the button");
        assertFalse(sonic.isControlLocked());
        Aiz2BossEndSequenceState.reset();
            }
    }

    @Test void unarmedSlotSixAndTypedRebindDoNotInventNativeReference() {
        var h = new ButtonHarness();
        var button = h.button(6, 0x4B18, 0x18D);
        button.update(0, h.sonic);
        assertFalse(Aiz2BossEndSequenceState.isButtonPressed());
        assertTrue(h.sonic.isControlLocked());
        var uninitialized = new CutsceneKnucklesAiz2Instance(
                new ObjectSpawn(0x4B18, 0x18D, 0, 4, 0, false, 0));
        h.manager.addDynamicObjectAtSlot(uninitialized, 7);
        Aiz2BossEndSequenceState.setActiveKnuckles(uninitialized); // restore reconciliation only
        button.update(1, h.sonic);
        assertFalse(Aiz2BossEndSequenceState.isButtonPressed(), "typed pointer alone is not native initialization");
    }

    @Test void ordinaryInitializedKnucklesStillPressesInRange() {
        try (var reel = com.openggf.tests.reel.S3kTestReelPresentation.open()) {
        var h = new ButtonHarness();
        var knuckles = h.arm(7);
        set(knuckles, "currentX", 0x4B18);
        set(knuckles, "currentY", 0x18D);
        var button = h.button(8, 0x4B18, 0x18D);
        button.update(1, h.sonic);
        assertTrue(Aiz2BossEndSequenceState.isButtonPressed());
        assertFalse(h.sonic.isControlLocked());
        reel.afterObjects(List.of(button), 0, 1, 0x4A78, 0x119, Sonic3kObjectArtKeys.BUTTON);
        }
    }

    @Test void reusedOtherOccupantProvidesCoordinatesWithoutTypeGate() {
        var h = new ButtonHarness();
        var knuckles = h.arm(9); // actual reference can name any allocated slot
        h.manager.removeDynamicObject(knuckles);
        var replacement = new Sonic3kMonitorObjectInstance(
                new ObjectSpawn(0x4B18, 0x18D, 1, 0, 0, false, 0));
        h.manager.addDynamicObjectAtSlot(replacement, 9);
        var consumer = h.button(10, 0x4B18, 0x18D);
        consumer.update(1, h.sonic);
        assertSame(replacement, NativeSstSlotAccess.currentOwner(h.manager, 9));
        assertTrue(Aiz2BossEndSequenceState.isButtonPressed());
        assertFalse(h.sonic.isControlLocked());
    }

    @Test void emptyRetiredSlotReadsClearedPositionWordsInsteadOfStaleActor() {
        var h = new ButtonHarness();
        var knuckles = h.arm(6);
        set(knuckles, "currentX", 0x4B18);
        set(knuckles, "currentY", 0x18D);
        h.manager.removeDynamicObject(knuckles);
        var distant = h.button(7, 0x4B18, 0x18D);
        distant.update(1, h.sonic);
        assertFalse(Aiz2BossEndSequenceState.isButtonPressed(), "stale actor coordinates cannot press");
        var origin = h.button(8, 0, 0);
        origin.update(1, h.sonic);
        assertTrue(Aiz2BossEndSequenceState.isButtonPressed(), "native Delete_Current_Sprite cleared x/y to zero");
    }

    @Test void slotAliasHonorsSignedWordRangeEdges() {
        for (int dx : new int[]{-25, -24, 23, 24}) {
            var h = new ButtonHarness();
            var knuckles = h.arm(6);
            h.manager.removeDynamicObject(knuckles);
            h.button(6, 0x4B18 + dx, 0x18D);
            h.button(7, 0x4B18, 0x18D).update(1, h.sonic);
            assertEquals(dx >= -24 && dx < 24, Aiz2BossEndSequenceState.isButtonPressed(), "x delta " + dx);
        }
        for (int dy : new int[]{-25, -24, 23, 24}) {
            var h = new ButtonHarness();
            var knuckles = h.arm(6);
            h.manager.removeDynamicObject(knuckles);
            h.button(6, 0x4B18, 0x18D + dy);
            h.button(7, 0x4B18, 0x18D).update(1, h.sonic);
            assertEquals(dy >= -24 && dy < 24, Aiz2BossEndSequenceState.isButtonPressed(), "y delta " + dy);
        }
    }

    @Test void rewindRetainsRawSlotAcrossRetirementReuseAndPress() {
        var h = new ButtonHarness();
        var actor = h.arm(6);
        var registry = new RewindRegistry();
        registry.register(h.manager.rewindSnapshottable());
        registry.register(new Aiz2BossEndSequenceStaticAdapter());
        registry.register(new RewindSnapshottable<PerObjectRewindSnapshot>() {
            public String key() { return "button-player"; }
            public PerObjectRewindSnapshot capture() { return h.sonic.captureRewindState(); }
            public void restore(PerObjectRewindSnapshot state) { h.sonic.restoreRewindState(state); }
        });
        var armed = registry.capture();
        h.manager.removeDynamicObject(actor);
        var empty = registry.capture();
        var button = h.button(6, 0x4B18, 0x18D);
        var reused = registry.capture();
        button.update(1, h.sonic);
        assertTrue(Aiz2BossEndSequenceState.isButtonPressed());
        var pressed = registry.capture();
        registry.restore(reused);
        Aiz2BossEndSequenceState.setActiveKnuckles(null); // real post-restore typed reconciliation
        assertFalse(Aiz2BossEndSequenceState.isButtonPressed());
        NativeSstSlotAccess.currentOwner(h.manager, 6).update(1, h.sonic);
        var replay = registry.capture();
        for (String key : pressed.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, pressed.get(key), replay.get(key)),
                    () -> key + ": " + RewindSnapshotDiff.diffKey(key, pressed.get(key), replay.get(key)));
        }
        registry.restore(empty);
        assertNull(NativeSstSlotAccess.currentOwner(h.manager, 6));
        h.button(6, 0x4B18, 0x18D).update(1, h.sonic);
        assertTrue(Aiz2BossEndSequenceState.isButtonPressed());
        registry.restore(armed);
        assertInstanceOf(CutsceneKnucklesAiz2Instance.class, NativeSstSlotAccess.currentOwner(h.manager, 6));
        h.manager.removeDynamicObject(NativeSstSlotAccess.currentOwner(h.manager, 6));
        h.button(6, 0x4B18, 0x18D).update(1, h.sonic);
        assertTrue(Aiz2BossEndSequenceState.isButtonPressed());
    }

    @Test void restoreBeforeActorInitializationDoesNotArmThroughTypedRebinding() {
        var h = new ButtonHarness();
        var actor = new CutsceneKnucklesAiz2Instance(
                new ObjectSpawn(0x4B18, 0x18D, 0, 4, 0, false, 0));
        h.manager.addDynamicObjectAtSlot(actor, 6);
        h.button(7, 0x4B18, 0x18D);
        var registry = new RewindRegistry();
        registry.register(h.manager.rewindSnapshottable());
        registry.register(new Aiz2BossEndSequenceStaticAdapter());
        var beforeInitialization = registry.capture();
        actor.update(0, h.sonic);
        assertEquals(6, Aiz2BossEndSequenceState.getCutsceneKnucklesSlot());
        registry.restore(beforeInitialization);
        var restoredActor = assertInstanceOf(CutsceneKnucklesAiz2Instance.class,
                NativeSstSlotAccess.currentOwner(h.manager, 6));
        Aiz2BossEndSequenceState.setActiveKnuckles(restoredActor);
        NativeSstSlotAccess.currentOwner(h.manager, 7).update(0, h.sonic);
        assertEquals(-1, Aiz2BossEndSequenceState.getCutsceneKnucklesSlot());
        assertFalse(Aiz2BossEndSequenceState.isButtonPressed());
        restoredActor.update(0, h.sonic);
        NativeSstSlotAccess.currentOwner(h.manager, 7).update(1, h.sonic);
        assertEquals(6, Aiz2BossEndSequenceState.getCutsceneKnucklesSlot());
        assertTrue(Aiz2BossEndSequenceState.isButtonPressed());
    }

    @Test void bossSequenceSetupDoesNotClearNativeReference() {
        var h = new ButtonHarness();
        var actor = h.arm(6);
        h.manager.removeDynamicObject(actor);
        h.manager.createDynamicObject(() ->
                new AizEndBossInstance(new ObjectSpawn(0x4800, 0x200, 0, 0, 0, false, 0)));
        h.button(6, 0x4B18, 0x18D).update(1, h.sonic);
        assertTrue(Aiz2BossEndSequenceState.isButtonPressed());
    }

    @Test void levelRuntimeResetClearsTheRawReference() {
        var h = new ButtonHarness();
        var actor = h.arm(6);
        h.manager.removeDynamicObject(actor);
        var button = h.button(6, 0x4B18, 0x18D);
        new com.openggf.game.sonic3k.Sonic3kLevelEventManager().resetState();
        button.update(1, h.sonic);
        assertFalse(Aiz2BossEndSequenceState.isButtonPressed());
        assertTrue(h.sonic.isControlLocked());
    }

    private static final class ButtonHarness {
        final ObjectManager manager;
        final TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        ButtonHarness() {
            Aiz2BossEndSequenceState.reset();
            var camera = GameServices.camera();
            camera.setX((short) 0x4A00);
            var services = new TestObjectServices().withCamera(camera);
            manager = manager(camera, services);
            sonic.setControlLocked(true);
        }
        CutsceneKnucklesAiz2Instance arm(int slot) {
            var actor = CutsceneKnucklesAiz2Instance.createDefault();
            manager.addDynamicObjectAtSlot(actor, slot);
            actor.update(0, sonic);
            return actor;
        }
        S3kCutsceneButtonObjectInstance button(int slot, int x, int y) {
            var button = new S3kCutsceneButtonObjectInstance(new ObjectSpawn(x, y - 4, 0x83, 0, 0, false, 0));
            manager.addDynamicObjectAtSlot(button, slot);
            return button;
        }
    }

    @Test void currentLimitationFifthKosMArchiveIsRejectedInsteadOfWritingAdjacentModeState() throws Exception {
        var queue = new KosinskiModuleQueue();
        var rom = TestEnvironment.currentRom();
        for (int i=0;i<4;i++) assertTrue(queue.enqueue(rom, 0x0D6A62, 0x520*0x20));
        assertEquals(4,queue.queuedArchiveCount());
        var before = queue.capture();
        // Queue_Kos_Module.findFreeSlot has no four-entry bound. Retail keeps
        // advancing by six bytes and writes beyond Kos_module_queue when full.
        assertFalse(queue.enqueue(rom, 0x0D6D84, 0x568*0x20), "confirmed bounded engine admission");
        assertEquals(before, queue.capture(), "failed admission does not corrupt queued work");
        queue.processNativeFrame(); queue.processNativeFrame();
        assertEquals(3,queue.queuedArchiveCount());
        assertTrue(queue.enqueue(rom,0x0D6D84,0x568*0x20),"free-slot control admits the same valid ROM archive");
    }

    private static ObjectManager manager(Camera camera, TestObjectServices services) {
        var manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                GameServices.graphics(), camera, services);
        services.withDirectObjectManager(manager); return manager;
    }
    private static void set(Object target,String name,int value) {
        try { var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.setInt(target,value); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
