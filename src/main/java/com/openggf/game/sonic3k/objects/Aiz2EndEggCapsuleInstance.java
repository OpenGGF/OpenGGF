package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.EggPrisonAnimalInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Floating upside-down egg prison used by the AIZ2 post-boss cutscene.
 */
public class Aiz2EndEggCapsuleInstance extends AbstractS3kFloatingEndEggCapsuleInstance {
    public Aiz2EndEggCapsuleInstance(int initialX, int initialY) {
        super(initialX, initialY, "AIZ2EndEggCapsule");
    }

    private Aiz2EndEggCapsuleInstance(int initialX, int initialY, boolean routeInitPending) {
        super(initialX, initialY, "AIZ2EndEggCapsule", routeInitPending);
    }

    public static Aiz2EndEggCapsuleInstance createForCamera(int cameraX, int cameraY) {
        return new Aiz2EndEggCapsuleInstance(cameraX + X_OFFSET, cameraY + Y_START_OFFSET, true);
    }

    @Override
    protected AbstractObjectInstance createCapsuleAnimal(ObjectSpawn spawn, int delay, int artVariant, int index) {
        return new HighPriorityAnimal(spawn, delay, artVariant);
    }

    @Override
    protected void onParentOpen() {
        // ROM sub_865DE sets Ctrl_2_locked negative when the capsule parent
        // consumes the button child's trigger bit, not when the child is first
        // touched (sonic3k.asm:181556-181570,181739-181767).
        if (services().playerQuery().nativeP2OrNull() instanceof AbstractPlayableSprite sidekick
                && sidekick.getCpuController() != null) {
            sidekick.getCpuController().setController2SignedLocked(true);
        }
    }

    @Override
    protected void onResultsComplete() {
        Aiz2BossEndSequenceState.releaseEggCapsule();
        // ROM: The capsule stays visible while Sonic walks right and Knuckles
        // does his cutscene. It leaves only when camera scroll or zone transition
        // removes it from the active scene.
    }

    @Override
    protected void onEndingPoseLockClear() {
        // ROM Check_TailsEndPose clears Ctrl_2_locked when Tails is eligible for
        // the ending pose (sonic3k.asm:181919-181939).
        if (services().playerQuery().nativeP2OrNull() instanceof AbstractPlayableSprite sidekick
                && sidekick.getCpuController() != null) {
            sidekick.getCpuController().setController2SignedLocked(false);
        }
    }

    private static final class HighPriorityAnimal extends EggPrisonAnimalInstance {
        HighPriorityAnimal(ObjectSpawn spawn, int delay, int artVariant) {
            super(spawn, delay, artVariant);
        }

        @Override
        public boolean isHighPriority() {
            return true;
        }
    }
}
