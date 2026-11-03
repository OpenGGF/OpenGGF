package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.objects.bosses.HczEndBossEggCapsuleInstance;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/** Keep the audited common ROM owners shared even when zone adapters change. */
class TestS3kSharedRoutineOwnership {
    @Test
    void fixedCapsulesShareOpeningWritesAndHczCannotForkTheUprightEffect() throws Exception {
        assertTrue(AbstractS3kUprightEggCapsuleInstance.class.isAssignableFrom(HczEndBossEggCapsuleInstance.class));
        assertTrue(AbstractS3kEggCapsuleInstance.class.isAssignableFrom(FbzEndEggCapsuleInstance.class));
        assertFalse(AbstractS3kUprightEggCapsuleInstance.class.isAssignableFrom(FbzEndEggCapsuleInstance.class),
                "FBZ retains its independent native child SST graph");
        assertTrue(Modifier.isFinal(AbstractS3kEggCapsuleInstance.class
                .getDeclaredMethod("initializeCapsuleOpening").getModifiers()));
        assertTrue(Modifier.isFinal(AbstractS3kUprightEggCapsuleInstance.class
                .getDeclaredMethod("openCapsule").getModifiers()));
    }

    @Test
    void fixedCapsuleBodiesAndNativeFbzChildrenRetainRomPlanePriority() throws Exception {
        var spawn = new com.openggf.level.objects.ObjectSpawn(100, 100, 0x2A, 0, 0, false, 0);
        var fbz = new FbzEndEggCapsuleInstance(spawn);
        for (var capsule : java.util.List.of(new CnzEggCapsuleInstance(spawn),
                new HczEndBossEggCapsuleInstance(100, 100), fbz)) {
            assertEquals(4, capsule.getPriorityBucket());
            assertTrue(capsule.isHighPriority());
            assertSame(AbstractS3kEggCapsuleInstance.class,
                    capsule.getClass().getMethod("getPriorityBucket").getDeclaringClass());
            assertSame(AbstractS3kEggCapsuleInstance.class,
                    capsule.getClass().getMethod("isHighPriority").getDeclaringClass());
        }
        var button = new FbzEndEggCapsuleButtonInstance(spawn, fbz);
        assertEquals(4, button.getPriorityBucket());
        assertTrue(button.isHighPriority());
        for (int part = 0; part < 5; part++) {
            var fragment = new FbzEndEggCapsuleFragmentInstance(new com.openggf.level.objects.ObjectSpawn(
                    100, 92, 0x2A, part << 1, 0, false, 0));
            assertEquals(3, fragment.getPriorityBucket());
            assertTrue(fragment.isHighPriority());
        }
    }

    @Test
    void cutsceneButtonZonesCannotForkTheCommonDispatchOrPresentation() throws Exception {
        for (var adapter : new Class<?>[]{S3kCutsceneButtonObjectInstance.class,
                Hcz2CutsceneButtonInstance.class, Cnz2CutsceneButtonInstance.class}) {
            assertTrue(AbstractS3kCutsceneButtonInstance.class.isAssignableFrom(adapter));
            assertSame(AbstractS3kCutsceneButtonInstance.class,
                    adapter.getMethod("update", int.class, com.openggf.game.PlayableEntity.class).getDeclaringClass());
            assertSame(AbstractS3kCutsceneButtonInstance.class, adapter.getMethod("getPriorityBucket").getDeclaringClass());
            assertSame(AbstractS3kCutsceneButtonInstance.class, adapter.getMethod("isHighPriority").getDeclaringClass());
        }
    }
}
