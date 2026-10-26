package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotStageController;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.rings.RingManager;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotResources;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotStageState;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData;
import com.openggf.sprites.playable.Sonic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

class TestS3kSlotRingRewardObjectInstance {

    @Test
    void productionRenderConsumerUsesRetainedNativeAnimationCursorAndDeletionPass() {
        RingManager rings = mock(RingManager.class);
        var services = new TestObjectServices() {
            @Override public RingManager ringManager() { return rings; }
        };
        var reward = ring(S3kSlotRomTestData.resources(), services);
        reward.activate();

        for (int pass = 1; pass <= 45; pass++) {
            clearInvocations(rings);
            // Deliberately nonconsecutive V-int values: Animate_Sprite advances per object pass.
            reward.update(pass * 7, null);
            reward.appendRenderCommands(new ArrayList<>());
            if (pass < 26) {
                verify(rings).drawRingFrameAt(0x460, 0x430, ((pass - 1) / 2) % 4);
            } else if (pass < 45) {
                int offset = pass < 32 ? 1 : pass < 38 ? 2 : 3;
                verify(rings).drawSparkleAt(0x460, 0x430, offset);
                assertFalse(reward.isDestroyed());
            } else {
                verifyNoInteractions(rings);
                assertTrue(reward.isDestroyed());
            }
        }
        assertEquals(3, reward.getPriorityBucket());
        assertFalse(reward.isHighPriority());
    }

    @Test
    void changedRomScriptBytesReachTheActualRewardRenderConsumer() {
        byte[] bytes = S3kSlotRomTestData.image();
        bytes[0x1A994] = 0; // no waiting passes between sparkle publications
        bytes[0x1A996] = 7; // retained index 1 selects mapping 7 at the grant
        RingManager rings = mock(RingManager.class);
        var services = new TestObjectServices() {
            @Override public RingManager ringManager() { return rings; }
        };
        var reward = ring(S3kSlotResources.fromRom(RomByteReader.fromBytes(bytes)), services);
        reward.activate();
        for (int pass = 1; pass <= 26; pass++) reward.update(pass, null);
        reward.appendRenderCommands(new ArrayList<>());
        verify(rings).drawSparkleAt(0x460, 0x430, 3);
        for (int pass = 27; pass <= 30; pass++) reward.update(pass, null);
        assertTrue(reward.isDestroyed(), "ROM delay and $FC, rather than a fixed lifetime, retire the child");
    }

    @Test
    void bothChildrenPreserveNativeLongRoundingAndWordWrap() throws Exception {
        var controller = new S3kSlotStageController(S3kSlotStageState.bootstrap(S3kSlotRomTestData.resources()));
        // Independent SUB.L/ASR.L/SUB.L register vectors; low bits diverge from reversed addition.
        int[] passes = {1, 2, 3, 6, 12};
        int[] right = {0x04F60000, 0x04ECA000, 0x04E3D600, 0x04CCA125, 0x04A9C096};
        int[] left = {0x03CA0000, 0x03D36000, 0x03DC2A00, 0x03F35EDC, 0x04163F70};
        int[] wrap = {0xFFF20000, 0xFFF3E000, 0xFFF5A200, 0xFFFA462C, 0x00013FE5};
        for (boolean spike : new boolean[] {false, true}) {
            AbstractObjectInstance child = spike
                    ? new S3kSlotSpikeRewardObjectInstance(spawn(), controller)
                    : new S3kSlotRingRewardObjectInstance(spawn(), controller);
            child.setServices(new TestObjectServices());
            activate(child, 0x500, 0x3C0, 0x460, 0x460);
            int vector = 0;
            for (int pass = 1; pass <= 12; pass++) {
                child.update(pass, null);
                if (pass == passes[vector]) {
                    assertEquals(right[vector], field(child, "currentX32"));
                    assertEquals(left[vector], field(child, "currentY32"));
                    vector++;
                }
            }
            activate(child, 0xFFF0, 0xFFF0, 0x10, 0x10);
            vector = 0;
            for (int pass = 1; pass <= 12; pass++) {
                child.update(pass, null);
                if (pass == passes[vector]) {
                    assertEquals(wrap[vector], field(child, "currentX32"));
                    assertEquals(wrap[vector], field(child, "currentY32"));
                    vector++;
                }
            }
        }
    }

    @Test
    void restoredRewardReplaysCursorAndFractionalMotionThroughGrantAndDeletion() {
        var services = new TestObjectServices();
        var original = ring(S3kSlotRomTestData.resources(), services);
        original.activate(0x500, 0x3C0, 0x460, 0x430);
        for (int pass = 1; pass <= 23; pass++) original.update(pass, null);
        var beforeGrant = original.captureRewindState();
        List<Object> expected = new ArrayList<>();
        for (int pass = 24; pass <= 45; pass++) {
            original.update(pass, null);
            expected.add(original.captureRewindState());
        }
        var restored = ring(S3kSlotRomTestData.resources(), services);
        restored.restoreRewindState(beforeGrant);
        for (int pass = 24; pass <= 45; pass++) {
            restored.update(pass, null);
            assertEquals(expected.get(pass - 24), restored.captureRewindState());
        }
    }

    private static S3kSlotRingRewardObjectInstance ring(S3kSlotResources resources, TestObjectServices services) {
        var controller = new S3kSlotStageController(S3kSlotStageState.bootstrap(resources));
        controller.bootstrap();
        controller.onRewardSpawned();
        var reward = new S3kSlotRingRewardObjectInstance(spawn(), controller);
        reward.setServices(services);
        return reward;
    }

    private static ObjectSpawn spawn() { return new ObjectSpawn(0x460, 0x430, 0, 0, 0, false, 0); }

    private static int field(AbstractObjectInstance object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(object);
    }

    private static void activate(AbstractObjectInstance child, int x, int y, int tx, int ty) {
        if (child instanceof S3kSlotRingRewardObjectInstance ring) ring.activate(x, y, tx, ty);
        else ((S3kSlotSpikeRewardObjectInstance) child).activate(x, y, tx, ty);
    }

    /**
     * ROM Obj_SlotRing routine 0 -&gt; 1 (sonic3k.asm:35902-35927): the cage's active-reward
     * count ($30(a0), reached through this object's $2E(a0) pointer) is decremented with
     * {@code subq.w #1,(a1)} at the exact grant instant -- immediately before {@code GiveRing}
     * and the {@code addi.b #2,routine(a0)} bump into the cosmetic sparkle handler
     * (loc_1AA56) -- not when the sparkle visual later finishes. Decrementing only on full
     * object destruction (after the sparkle plays out) held the cage's active count high for
     * the extra sparkle-duration frames, which is exactly the S3K slots frame-332 regression
     * this test guards (S3kSlotBonusCageObjectInstance's release-angle-alignment wait missed
     * its window and delayed cage ejection by a full 16-frame rotation).
     */
    @Test
    void grantingRingDecrementsActiveCountImmediatelyNotAtSparkleEnd() {
        ObjectSpawn spawn = new ObjectSpawn(0x460, 0x430, 0x00, 0x00, 0x00, false, 0);
        S3kSlotStageController controller = new S3kSlotStageController(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotStageState.bootstrap(
                com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources()));
        controller.bootstrap();
        controller.onRewardSpawned();
        assertEquals(1, controller.activeRewardObjects());

        S3kSlotRingRewardObjectInstance reward = new S3kSlotRingRewardObjectInstance(spawn, controller);
        reward.setServices(new TestObjectServices());
        reward.activate(0x460, 0x430, 0x460, 0x430);

        Sonic player = new Sonic("sonic", (short) 0x460, (short) 0x430);

        // EXPIRY_FRAMES = 0x1A: tick until (and including) the grant frame.
        for (int i = 0; i < 0x1A; i++) {
            reward.tickSlotRuntime(i, player);
        }

        // The grant must have already fired and reported the cage's active-count
        // decrement, even though the object is still alive playing its sparkle.
        assertEquals(0, controller.activeRewardObjects());
        assertTrue(reward.isInSparkle());
        assertFalse(reward.isDestroyed());

        // Ticking through the remaining sparkle frames must NOT decrement again.
        for (int i = 0; i < 19; i++) {
            reward.tickSlotRuntime(0x1A + i, player);
        }
        assertEquals(0, controller.activeRewardObjects());
        assertTrue(reward.isDestroyed());
    }
}
