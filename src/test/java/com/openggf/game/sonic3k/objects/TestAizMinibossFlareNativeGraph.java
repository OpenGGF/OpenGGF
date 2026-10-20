package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Literal flare INIT/raw cursor/parent3 contract; component cameras, not cold-route products. */
class TestAizMinibossFlareNativeGraph {
    private static final int SCRIPT = 0x6913F;
    // Independent shipped-ROM bytes; production must read this window through its ROM service.
    private static final byte[] RAW = {7, 1, 7, 1, 8, 1, 9, 3, 10, 3, 11, 3, (byte) 0xF4};

    @BeforeEach void init() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void reset() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @Test void initDoesNotAnimateAndF4StillDrawsBeforeFollowingPhysicalClear() {
        var h = new Harness(320);
        h.addFlare(9, 7);
        // Init, two updates at 7, two at 8, four each at 9/A/B, then F4 still draws B.
        int[] expected = {7, 7, 7, 8, 8, 9, 9, 9, 9, 10, 10, 10, 10, 11, 11, 11, 11, 11};
        for (int frame = 0; frame < expected.length; frame++) {
            h.step(frame);
            assertNotNull(h.flare(9), "Go_Delete_Sprite only changes the code/status header");
            assertDraw(h, 9, expected[frame]);
        }
        h.step(18);
        assertNull(h.flare(9), "Delete_Current_Sprite clears the SST on its next dispatch");
    }

    @Test void flareInitPublishesNativePriorityAndDimensions() {
        var h = new Harness(320);
        h.addFlare(9, 7);
        h.step(0);
        assertEquals(4, h.flare(9).getPriorityBucket(), "ObjDat priority $200");
        assertEquals(8, h.flare(9).getOnScreenHalfWidth());
        assertEquals(8, h.flare(9).getOnScreenHalfHeight());
        assertFalse(h.flare(9) instanceof TouchResponseProvider, "the flare has no native touch publication");
    }

    @Test void retainedFirstFlareKeepsSecondNativePairInTheNextTwoFreeSlots() {
        var h = new Harness(320);
        // Declared occupied prefix isolates CreateChild1_Normal's AfterCurrent scan.
        for (int slot = 9; slot <= 16; slot++) h.addBlocker(slot);
        h.addFlare(17, 7);
        h.addBlocker(18);
        for (int frame = 0; frame < 16; frame++) h.step(frame);
        h.manager.addDynamicObjectAfterSlot(new AizMinibossBarrelShotFlareChild(h.barrel(8)), 8);
        h.manager.addDynamicObjectAfterSlot(new Blocker(new ObjectSpawn(0x3029, 0x334, 0, 0, 0, false, 0)), 8);
        assertNotNull(h.flare(17), "the first native flare has not reached F4 at the $10 subtype delay");
        assertNotNull(h.flare(19));
        assertInstanceOf(Blocker.class, NativeSstSlotAccess.currentOwner(h.manager, 20));
        h.step(16);
        h.step(17);
        assertNotNull(h.flare(17), "the F4 marker remains occupied through Draw_Sprite");
        h.step(18);
        assertNull(h.flare(17));
        h.manager.addDynamicObjectAfterSlot(new Blocker(new ObjectSpawn(0x3020, 0x330, 0, 0, 0, false, 0)), 7);
        assertInstanceOf(Blocker.class, NativeSstSlotAccess.currentOwner(h.manager, 17));
    }

    @Test void pureRecreationPreservesCapturedBirthMetadataWithoutANearestParent() {
        var h = new Harness(320);
        var flare = h.addFlare(9, 7);
        ObjectSpawn captured = flare.getSpawn();
        h.manager.removeDynamicObject(h.barrel(7));
        h.manager.removeDynamicObject(h.barrel(8));
        var recreated = flare.recreateForRewind(new RewindRecreateContext(captured, null, h.services));
        assertNotNull(recreated, "phase one only needs the captured spawn shell");
        assertEquals(captured, recreated.getSpawn());
    }

    @ParameterizedTest @ValueSource(ints = {320, 352, 400, 528, 800})
    void movingTwoBarrelGraphRecreatesBirthMetadataAndReplaysBothDirections(int width) {
        var h = new Harness(width);
        ObjectSpawn firstBirth = h.addFlare(9, 7).getSpawn();
        ObjectSpawn secondBirth = h.addFlare(10, 8).getSpawn();
        for (int frame = 0; frame <= 6; frame++) { h.moveBoss(frame); h.step(frame); }
        var rewind = h.manager.rewindSnapshottable();
        var before = rewind.capture();
        for (int frame = 7; frame <= 51; frame++) { h.moveBoss(frame); h.step(frame); }
        var after = rewind.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            rewind.restore(before);
            same(before, rewind.capture());
            assertEquals(firstBirth, h.flare(9).getSpawn());
            assertEquals(secondBirth, h.flare(10).getSpawn());
            assertNotEquals(h.flare(9).getX(), h.flare(10).getX(), "each child follows its own physical parent3");
            for (int frame = 7; frame <= 51; frame++) { h.moveBoss(frame); h.step(frame); }
            same(after, rewind.capture());
            rewind.restore(before);
            same(before, rewind.capture());
            rewind.restore(after);
            same(after, rewind.capture());
        }
    }

    @Test void clearedParentHeaderDoesNotRetireTheFlareAndRecreatesAcrossTheClear() {
        var h = new Harness(320);
        h.addFlare(9, 7);
        for (int frame = 0; frame <= 5; frame++) h.step(frame);
        var rewind = h.manager.rewindSnapshottable();
        var before = rewind.capture();
        ObjectLifetimeOps.deleteNoRespawn(h.barrel(7));
        h.step(6);
        assertNull(NativeSstSlotAccess.currentOwner(h.manager, 7));
        assertNotNull(h.flare(9), "Refresh_ChildPositionAdjusted never tests the parent status");
        assertEquals(0, h.flare(9).getX(), "cleared physical SST x_pos");
        assertEquals(4, h.flare(9).getY(), "cleared y_pos plus child_dy");
        var cleared = rewind.capture();
        for (int frame = 7; frame <= 51; frame++) h.step(frame);
        var after = rewind.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            rewind.restore(cleared);
            same(cleared, rewind.capture());
            for (int frame = 7; frame <= 51; frame++) h.step(frame);
            same(after, rewind.capture());
            rewind.restore(before);
            same(before, rewind.capture());
            ObjectLifetimeOps.deleteNoRespawn(h.barrel(7));
            h.step(6);
            same(cleared, rewind.capture());
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void liveBarrelRefreshCopiesBothFlipBitsAndSignedOffsets(int flags) {
        var h = new Harness(320);
        h.boss().getState().renderFlags = flags;
        h.addFlare(9, 7);
        h.step(0);
        assertEquals(0x3020, h.flare(9).getX());
        assertEquals(0x34C + ((flags & 2) == 0 ? -0x20 + 4 : 0x20 - 4), h.flare(9).getY());
        clearInvocations(h.renderer);
        h.flare(9).appendRenderCommands(List.of());
        verify(h.renderer).drawFrameIndex(7, h.flare(9).getX(), h.flare(9).getY(), (flags & 1) != 0, (flags & 2) != 0, 0);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void recycledParentReadsOnlyTheVerifiedCurrentHeaderAndSignedOffset(int flags) {
        var h = new Harness(320);
        h.addFlare(9, 7);
        h.step(0);
        h.manager.removeDynamicObject(h.barrel(7));
        h.manager.createDynamicObjectAtSlot(() -> new HeaderOwner(
                new ObjectSpawn(0x3040, 0x338, 0, 0, flags, false, 0)), 7);
        h.step(1);
        var flare = h.flare(9);
        assertNotNull(flare);
        assertEquals(0x3040, flare.getX(), "read the current physical owner, not the detached barrel");
        assertEquals(0x338 + ((flags & 2) == 0 ? 4 : -4), flare.getY());
        clearInvocations(h.renderer);
        flare.appendRenderCommands(List.of());
        verify(h.renderer).drawFrameIndex(7, flare.getX(), flare.getY(), (flags & 1) != 0, (flags & 2) != 0, 0);
    }

    @Test void unknownRecycledHeaderFailsBeforeAnimationOrPositionMutation() {
        var h = new Harness(320);
        var flare = h.addFlare(9, 7);
        h.step(0);
        int oldX = flare.getX(), oldY = flare.getY();
        h.manager.removeDynamicObject(h.barrel(7));
        h.addBlocker(7);
        assertThrows(IllegalStateException.class, () -> flare.update(1, h.main));
        assertEquals(oldX, flare.getX());
        assertEquals(oldY, flare.getY());
    }

    private static void assertDraw(Harness h, int slot, Integer frame) {
        clearInvocations(h.renderer);
        var flare = h.flare(slot);
        if (flare != null) flare.appendRenderCommands(List.of());
        if (frame == null) verifyNoInteractions(h.renderer);
        else verify(h.renderer).drawFrameIndex(frame, flare.getX(), flare.getY(), false, false, 0);
    }
    private static void same(Object expected, Object actual) {
        var diff = RewindSnapshotDiff.diffKey("object-manager", expected, actual);
        assertTrue(diff.isEmpty(), () -> diff.toString());
    }

    /** Inert cutscene checkpoint: the real barrel graph owns the child refresh/dispatch. */
    public static final class FrozenBoss extends AizMinibossCutsceneInstance {
        public FrozenBoss(ObjectSpawn spawn) {
            super(spawn);
            getState().x = spawn.x(); getState().y = spawn.y();
            getState().xFixed = spawn.x() << 16; getState().yFixed = spawn.y() << 16;
        }
        @Override public void update(int vIntRunCount, PlayableEntity player) {
            getState().lastUpdatedVIntRunCount = vIntRunCount;
        }
        @Override public FrozenBoss recreateForRewind(RewindRecreateContext ctx) { return new FrozenBoss(ctx.spawn()); }
        @Override public boolean isPersistent() { return true; }
    }
    public static class Blocker extends AbstractObjectInstance implements SpawnRewindRecreatable {
        public Blocker(ObjectSpawn spawn) { super(spawn, "DeclaredFlarePoolPressure"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public boolean isPersistent() { return true; }
        @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
    }
    /** Literal read-port vector; no unknown gameplay header fields are defaulted by production. */
    public static final class HeaderOwner extends Blocker implements NativeSstStateSource {
        public HeaderOwner(ObjectSpawn spawn) { super(spawn); }
        @Override public NativeSstState nativeSstState() {
            return new NativeSstState(getSpawn().renderFlags(), 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    static final class Harness {
        final Camera camera = mock(Camera.class);
        final TestablePlayableSprite main = new TestablePlayableSprite("sonic", (short) 0x3000, (short) 0x350);
        final PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        final TestObjectServices services;
        final ObjectManager manager;
        Harness(int width) {
            main.setCentreX((short) 0x3000); main.setCentreY((short) 0x350);
            when(camera.getX()).thenReturn((short) 0x2F00);
            when(camera.getXCopy()).thenReturn((short) 0x2F00);
            when(camera.getY()).thenReturn((short) 0x300);
            when(camera.getYCopy()).thenReturn((short) 0x300);
            when(camera.getWidth()).thenReturn((short) width);
            when(camera.getHeight()).thenReturn((short) 224);
            when(camera.getFocusedSprite()).thenReturn(main);
            var render = mock(ObjectRenderManager.class);
            when(render.getRenderer(com.openggf.game.sonic3k.Sonic3kObjectArtKeys.AIZ_MINIBOSS)).thenReturn(renderer);
            when(renderer.isReady()).thenReturn(true);
            var rom = mock(RomByteReader.class);
            when(rom.slice(SCRIPT, RAW.length)).thenReturn(RAW.clone());
            services = new TestObjectServices() {
                @Override public ObjectRenderManager renderManager() { return render; }
            }.withCamera(camera).withGameModule(new Sonic3kGameModule())
                    .withGraphicsManager(GraphicsManager.getInstance()).withRomReader(rom);
            manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            services.withDirectObjectManager(manager);
            manager.reset(0x2F00);
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            manager.createDynamicObjectAtSlot(() -> new FrozenBoss(
                    new ObjectSpawn(0x3020, 0x34C, 0x90, 0, 0, false, 0)), 6);
            for (int slot = 7; slot <= 8; slot++) {
                int index = slot - 7;
                var barrel = manager.createDynamicObjectAtSlot(() -> new AizMinibossFlameBarrelChild(boss(), index, true), slot);
            }
        }
        FrozenBoss boss() { return (FrozenBoss) NativeSstSlotAccess.currentOwner(manager, 6); }
        AizMinibossFlameBarrelChild barrel(int slot) { return (AizMinibossFlameBarrelChild) NativeSstSlotAccess.currentOwner(manager, slot); }
        AizMinibossBarrelShotFlareChild flare(int slot) {
            var owner = NativeSstSlotAccess.currentOwner(manager, slot);
            return owner instanceof AizMinibossBarrelShotFlareChild f ? f : null;
        }
        AizMinibossBarrelShotFlareChild addFlare(int slot, int parentSlot) {
            return manager.createDynamicObjectAtSlot(() -> new AizMinibossBarrelShotFlareChild(barrel(parentSlot)), slot);
        }
        void addBlocker(int slot) {
            manager.createDynamicObjectAtSlot(() -> new Blocker(new ObjectSpawn(0x3020, 0x330, 0, 0, 0, false, 0)), slot);
        }
        void moveBoss(int frame) {
            boss().getState().x = 0x3020 + frame * 2;
            boss().getState().xFixed = boss().getState().x << 16;
        }
        void step(int frame) { manager.update(0x2F00, main, List.of(), frame); manager.refreshPostCameraRenderState(); }
    }
}
