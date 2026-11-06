package com.openggf.game.sonic1.objects.badniks;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic1.Sonic1RomProfile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_1)
class TestSonic1RetailCaterkiller {
    @Test
    void partialBodyAdmissionSurvivesOnlyInRevisionZero() throws Exception {
        var reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        com.openggf.level.objects.ObjectManager[] managerRef = {null};
        var services = new StubObjectServices() {
            @Override public RomByteReader romReader() { return reader; }
            @Override public com.openggf.level.objects.ObjectManager objectManager() { return managerRef[0]; }
        };
        var manager = new com.openggf.level.objects.ObjectManager(java.util.List.of(), null,
                0, null, null, null, com.openggf.game.GameServices.camera(), services);
        managerRef[0] = manager;
        var head = manager.createDynamicObject(() -> new Sonic1CaterkillerBadnikInstance(
                new ObjectSpawn(100, 100, 0x78, 0, 0, false, 0)));
        manager.reserveAllButNFreeSlots(2);
        var method = Sonic1CaterkillerBadnikInstance.class.getDeclaredMethod("spawnBodySegments");
        method.setAccessible(true);
        method.invoke(head);
        boolean revision00 = Sonic1RomProfile.isRev00(reader);
        assertEquals(!revision00, head.isDestroyed());
        var children = manager.activeObjectsOfType(Sonic1CaterkillerBodyInstance.class);
        // The manager's typed query includes allocated entries until the normal
        // destroyed-dynamic retirement pass. REV01 has latched both children
        // for retirement here; REV00 keeps the successfully allocated chain.
        assertEquals(2, children.size());
        for (var child : children) {
            assertEquals(!revision00, child.isDestroyed());
            assertTrue(child.getSlotIndex() > head.getSlotIndex());
        }
        var cleanup = com.openggf.level.objects.ObjectManager.class.getDeclaredMethod("cleanupDestroyedDynamicObjects");
        cleanup.setAccessible(true);
        cleanup.invoke(manager);
        assertEquals(revision00 ? 2 : 0,
                manager.activeObjectsOfType(Sonic1CaterkillerBodyInstance.class).size());
    }

    @Test
    void bodyTurnRestoresTheOldPositionOnlyInTheOriginalRevision() throws Exception {
        var reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        var services = new StubObjectServices() {
            @Override public RomByteReader romReader() { return reader; }
        };
        var head = new Sonic1CaterkillerBadnikInstance(new ObjectSpawn(100, 100, 0x78, 0, 0, false, 0));
        var parent = new CaterkillerParentState() {
            @Override public int getSecondaryState() { return 1; }
            @Override public int getInertia() { return 0; }
            @Override public int getXVelocity() { return -0xC0; }
            @Override public int getAnimControl() { return 0; }
            @Override public int readRingBuffer(int index) { return 0x80; }
            @Override public void writeRingBuffer(int index, int value) { }
        };
        var body = new Sonic1CaterkillerBodyInstance(head, parent, 100, 100, true, false, 0, 4);
        body.setServices(services);
        var field = Sonic1CaterkillerBodyInstance.class.getDeclaredField("motion");
        field.setAccessible(true);
        var motion = (SubpixelMotion.State) field.get(body);
        // moveX stores eight fractional bits; the effective byte corresponds to
        // the ROM subpixel word's high byte (one quarter pixel here).
        motion.xSub = 0x40;
        body.update(1, null);
        boolean revision00 = Sonic1RomProfile.isRev00(reader);
        assertEquals(revision00 ? 100 : 99, body.getX());
        assertEquals(revision00 ? 0x40 : 0x80, motion.xSub & 0xFF);
        assertEquals(0x80, body.readRingBuffer(4) & 0xFF, "turn marker propagates to the next segment");
    }
}
