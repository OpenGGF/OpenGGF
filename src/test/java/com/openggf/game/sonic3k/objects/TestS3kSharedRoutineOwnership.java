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
