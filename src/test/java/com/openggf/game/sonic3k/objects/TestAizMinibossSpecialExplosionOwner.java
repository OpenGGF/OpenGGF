package com.openggf.game.sonic3k.objects;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameRng;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Literal swing-expiry callback and physical Special controller; not an AIZ cold-route certificate. */
class TestAizMinibossSpecialExplosionOwner {
    @BeforeEach void init() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void reset() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest @ValueSource(ints = {320, 352, 400, 528, 800})
    void actualSwingExpiryCallbackAdmitsARealOwnerBeforeItsNativeInit(int width) throws Exception {
        var h = harness(width);
        var rng = spy(new GameRng(GameRng.Flavour.S3K)); h.services.withRng(rng);
        createAtSwingExpiry(h);
        var owner = owner(h);
        assertEquals(19, owner.getSlotIndex(), "CreateChild1_Normal scans after the live parent past the declared occupied prefix");
        assertEquals(h.boss().getX(), owner.getX(), "birth copies parent XY before own Init");
        assertEquals(h.boss().getY(), owner.getY());
        h.step(0);
        assertEquals(0x2F00 + 160, owner.getX(), "native physical camera centre stays320/2 at every viewport");
        assertEquals(0x300 + 112, owner.getY());
        assertNull(NativeSstSlotAccess.currentOwner(h.manager, 20), "Init calls Obj_Wait with2, leaving1");
        verify(rng, never()).nextRaw();
    }

    @Test void firstAcceptedChildUsesLowAndHighRandomWordsAfterTheNativeTwoWaitPasses() throws Exception {
        var h = harness(320);
        var rng = mock(GameRng.class); when(rng.nextRaw()).thenReturn(0x12345678); h.services.withRng(rng);
        createAtSwingExpiry(h); owner(h);
        h.step(0); h.step(1);
        assertNull(NativeSstSlotAccess.currentOwner(h.manager, 20), "word2E zero is held");
        verify(rng, never()).nextRaw();
        h.step(2);
        var child = assertInstanceOf(S3kBossExplosionChild.class, NativeSstSlotAccess.currentOwner(h.manager, 20));
        assertEquals(0x2F00 + 160 + 0x78 - 0x80, child.getX());
        assertEquals(0x300 + 112 + 0x34 - 0x80, child.getY(), "SWAP selects the high word, not bits8..15");
        assertTrue(child.nativeInitSfxForTest());
        assertTrue(child.nativeInitSfxPlayedForTest(), "higher child slot owns its same-pass Init sound");
        verify(rng, times(1)).nextRaw();
    }

    @Test void initialAllocationFailureKeepsTheNativePreExitWaitWithoutRngOrAHiddenOwner() throws Exception {
        var h = harness(320);
        for (int slot = 19; slot < 94; slot++) h.addBlocker(slot);
        var before = h.manager.occupiedDynamicSlotIdsWithReservations();
        var rng = spy(new GameRng(GameRng.Flavour.S3K)); h.services.withRng(rng);
        createAtSwingExpiry(h); h.step(0); h.step(1); h.step(2);
        assertEquals(before, h.manager.occupiedDynamicSlotIdsWithReservations());
        verify(rng, never()).nextRaw();
    }

    @Test void controllerDoesNotBorrowParentLivenessAndRetainsItsTerminalSlotUntilNextDispatch() throws Exception {
        var h = harness(320);
        var rng = spy(new GameRng(GameRng.Flavour.S3K)); h.services.withRng(rng);
        createAtSwingExpiry(h); owner(h); h.step(0);
        h.boss().setDestroyed(true);
        h.manager.removeDynamicObject(h.boss());
        for (int frame = 1; frame <= 118; frame++) h.step(frame);
        assertNotNull(NativeSstSlotAccess.currentOwner(h.manager, 19), "Special enters independent Obj_Wait, not WaitForParent");
        verify(rng, times(39)).nextRaw();
        h.step(119);
        assertNotNull(NativeSstSlotAccess.currentOwner(h.manager, 19), "Go_Delete changes the operation on the40th callback");
        h.step(120);
        assertNull(NativeSstSlotAccess.currentOwner(h.manager, 19), "next Delete_Current dispatch clears its SST");
        verify(rng, times(39)).nextRaw();
    }

    @ParameterizedTest @ValueSource(ints = {320, 352, 400, 528, 800})
    void creationAndActiveOwnerRecreateWithTheRealRngAdapterForTwo45PassCycles(int width) throws Exception {
        var h = harness(width);
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        var rng = new GameRng(GameRng.Flavour.S3K, 0x12345678); h.services.withRng(rng);
        var rewind = h.manager.rewindSnapshottable();
        var before = rewind.capture(); var beforeRng = rng.capture();
        createAtSwingExpiry(h); owner(h); h.step(0);
        var initialized = rewind.capture(); var initializedRng = rng.capture();
        for (int frame = 1; frame <= 45; frame++) h.step(frame);
        var after = rewind.capture(); var afterRng = rng.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            rewind.restore(before); rng.restore(beforeRng); same(before, rewind.capture());
            createAtSwingExpiry(h); owner(h); h.step(0); same(initialized, rewind.capture());
            assertEquals(initializedRng, rng.capture());
            for (int frame = 1; frame <= 45; frame++) h.step(frame);
            same(after, rewind.capture()); assertEquals(afterRng, rng.capture());
            rewind.restore(initialized); rng.restore(initializedRng); same(initialized, rewind.capture());
            for (int frame = 1; frame <= 45; frame++) h.step(frame);
            same(after, rewind.capture()); assertEquals(afterRng, rng.capture());
            rewind.restore(after); rng.restore(afterRng); same(after, rewind.capture());
            rewind.restore(before); rng.restore(beforeRng); same(before, rewind.capture());
        }
    }

    @Test void failedBurstConsumesItsAttemptWithoutRngAndNextBurstKeepsTheNativeCadence() throws Exception {
        var h = harness(320);
        createAtSwingExpiry(h); owner(h);
        for (int slot = 20; slot < 94; slot++) h.addBlocker(slot);
        var rng = spy(new GameRng(GameRng.Flavour.S3K)); h.services.withRng(rng);
        h.step(0); h.step(1); h.step(2);
        verify(rng, never()).nextRaw();
        h.manager.removeDynamicObject(NativeSstSlotAccess.currentOwner(h.manager, 20));
        h.step(3); h.step(4);
        assertNull(NativeSstSlotAccess.currentOwner(h.manager, 20));
        h.step(5);
        assertInstanceOf(S3kBossExplosionChild.class, NativeSstSlotAccess.currentOwner(h.manager, 20));
        verify(rng, times(1)).nextRaw();
        // Exhaustion still marks at119, despite failed attempts in the finite pool.
        for (int frame = 6; frame <= 118; frame++) h.step(frame);
        assertNotNull(NativeSstSlotAccess.currentOwner(h.manager, 19));
        h.step(119); assertNotNull(NativeSstSlotAccess.currentOwner(h.manager, 19));
        h.step(120); assertNull(NativeSstSlotAccess.currentOwner(h.manager, 19));
    }

    @Test void pureRecreationPreservesEveryCapturedSpawnFieldWithoutAnyLiveParent() throws Exception {
        var h = harness(320); createAtSwingExpiry(h);
        var spawn = new ObjectSpawn(0x71, 0x1F3, 0, 2, 3, false, 0x61F3);
        var recreated = ((RewindRecreatable)owner(h)).recreateForRewind(
                new RewindRecreateContext(spawn, null, h.services));
        assertEquals(spawn, recreated.getSpawn());
    }

    @Test void nativeActClearRetiresTheIndependentOwnerAndRestoresAcrossTheClear() throws Exception {
        var h = harness(320); h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        createAtSwingExpiry(h); owner(h); h.step(0);
        var rewind = h.manager.rewindSnapshottable(); var before = rewind.capture();
        owner(h).onCarriedAcrossSeamlessTransition(-0x2F00, -0x80); h.step(1);
        assertNull(NativeSstSlotAccess.currentOwner(h.manager, 19));
        var cleared = rewind.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            rewind.restore(before); same(before, rewind.capture());
            owner(h).onCarriedAcrossSeamlessTransition(-0x2F00, -0x80); h.step(1);
            same(cleared, rewind.capture());
            rewind.restore(cleared); same(cleared, rewind.capture());
        }
    }

    private static TestAizMinibossFlareNativeGraph.Harness harness(int width) {
        var h = new TestAizMinibossFlareNativeGraph.Harness(width);
        // Different copy origin proves Obj_BossExplosionSpecial consumes physical camera words.
        when(h.camera.getXCopy()).thenReturn((short)0x2E00);
        when(h.camera.getYCopy()).thenReturn((short)0x200);
        var rom = mock(RomByteReader.class);
        byte[] raw = {0,0,0,1,1,1,2,2,3,3,4,4,5,4,(byte)0xF4};
        for (int i = 0; i < raw.length; i++) when(rom.readU8(Sonic3kConstants.ANI_RAW_BOSS_EXPLOSION_ADDR + i)).thenReturn(Byte.toUnsignedInt(raw[i]));
        h.services.withRomReader(rom);
        for (int slot = 9; slot <= 18; slot++) h.addBlocker(slot);
        return h;
    }
    private static void createAtSwingExpiry(TestAizMinibossFlareNativeGraph.Harness h) throws Exception {
        // Literal native callback boundary; no player/fixture clock or position writes.
        Method callback = AizMinibossCutsceneInstance.class.getDeclaredMethod("onSwingComplete");
        callback.setAccessible(true); callback.invoke(h.boss());
    }
    private static AbstractObjectInstance owner(TestAizMinibossFlareNativeGraph.Harness h) {
        var owner = NativeSstSlotAccess.currentOwner(h.manager, 19);
        assertNotNull(owner, "actual parent callback must create a physical Special controller, not a Java-only helper");
        return (AbstractObjectInstance)owner;
    }
    private static void same(Object expected, Object actual) {
        var diff = RewindSnapshotDiff.diffKey("object-manager", expected, actual);
        assertTrue(diff.isEmpty(), () -> diff.toString());
    }
}
