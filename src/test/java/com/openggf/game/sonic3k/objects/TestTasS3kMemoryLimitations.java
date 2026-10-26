package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.*;
import com.openggf.level.resources.KosinskiModuleQueue;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** TAS 10042S/6655S/9155S: exercised incompatibilities, NOT passing native parity.
 * Native oracle: locked-on 8b2cf3c, Obj_CutsceneButton/loc_65C04, Queue_Kos_Module.
 * No emulator RAM or dirty disassembly is used as an engine fixture. */
@RequiresRom(SonicGame.SONIC_3K)
class TestTasS3kMemoryLimitations {
    @BeforeEach void setup() { TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule()); }

    @Test void currentLimitationSelfOccupiedSlotSixCannotActAsCutsceneKnuckles() {
        try (var reel = com.openggf.tests.reel.S3kTestReelPresentation.open()) {
        var camera = GameServices.camera(); camera.setX((short) 0x4A00);
        var services = new TestObjectServices().withCamera(camera);
        var manager = manager(camera, services);
        var button = new S3kCutsceneButtonObjectInstance(new ObjectSpawn(0x4B18,0x189,0x83,0,0,false,0));
        manager.addDynamicObjectAtSlot(button, 6);
        var sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        sonic.setControlLocked(true);
        Aiz2BossEndSequenceState.reset();
        assertEquals(6, button.getSlotIndex());
        assertTrue(manager.getActiveObjects().contains(button));
        button.update(0, sonic); reel.afterObjects(List.of(button),0,1,0x4A78,0x119,Sonic3kObjectArtKeys.BUTTON);
        // Native _unkFAA4=$B180 reads the button's own x/y, so Check_InMyRange
        // succeeds (dx=dy=0) and loc_65C56 unlocks P1. Engine requires typed actor.
        assertFalse(Aiz2BossEndSequenceState.isButtonPressed(), "confirmed missing raw slot alias");
        assertTrue(sonic.isControlLocked());
        var knuckles = CutsceneKnucklesAiz2Instance.createDefault();
        set(knuckles, "currentX", 0x4B18); set(knuckles, "currentY", 0x189);
        Aiz2BossEndSequenceState.setActiveKnuckles(knuckles);
        button.update(1, sonic); reel.afterObjects(List.of(button),0,1,0x4A78,0x119,Sonic3kObjectArtKeys.BUTTON);
        assertTrue(Aiz2BossEndSequenceState.isButtonPressed(), "real actor is a discriminating positive control");
        assertFalse(sonic.isControlLocked());
        Aiz2BossEndSequenceState.reset();
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
