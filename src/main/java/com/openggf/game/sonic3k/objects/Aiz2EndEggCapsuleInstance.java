package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.EggPrisonAnimalInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnCoordinateRewindRecreatable;
import com.openggf.game.PlayerCharacter;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Floating upside-down egg prison used by the AIZ2 post-boss cutscene.
 */
public class Aiz2EndEggCapsuleInstance extends AbstractS3kFloatingEndEggCapsuleInstance
        implements SpawnCoordinateRewindRecreatable {
    private static final int RESULTS_OWNER_TAILS_ENDING_POSE_ENTRY = 6;

    private boolean tailsEndingPoseApplied;
    private boolean tailsEndingPoseControllerReleasePending;
    private int resultsActiveWaitEntries;

    public Aiz2EndEggCapsuleInstance(int initialX, int initialY) {
        super(initialX, initialY, "AIZ2EndEggCapsule");
    }

    private Aiz2EndEggCapsuleInstance(int initialX, int initialY, boolean routeInitPending) {
        super(initialX, initialY, "AIZ2EndEggCapsule", routeInitPending);
    }

    private Aiz2EndEggCapsuleInstance() {
        this(0, 0);
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
    protected ObjectPlayerParticipationPolicy resultsLockParticipationPolicy() {
        // AIZ route 0 sub_868F8 applies Set_PlayerEndingPose to Player_1 when
        // results start; Player_2 is handled later by Check_TailsEndPose after
        // its own eligibility gate (sonic3k.asm:181900-181939).
        return ObjectPlayerParticipationPolicy.MAIN_ONLY_NATIVE;
    }

    @Override
    protected AbstractObjectInstance createResultsScreen() {
        return new Aiz2ResultsScreenObjectInstance(getPlayerCharacter(), services().currentAct());
    }

    @Override
    protected void onResultsActiveWait() {
        resultsActiveWaitEntries++;
        advanceTailsEndingPoseCheck(false);
    }

    @Override
    protected void onEndingPoseLockClear() {
        advanceTailsEndingPoseCheck(true);
        releasePendingTailsEndingPoseControllerLock();
    }

    private void advanceTailsEndingPoseCheck(boolean force) {
        if (releasePendingTailsEndingPoseControllerLock()) {
            return;
        }
        if (tailsEndingPoseApplied) {
            return;
        }
        if (!force && resultsActiveWaitEntries < RESULTS_OWNER_TAILS_ENDING_POSE_ENTRY) {
            return;
        }
        // ROM Check_TailsEndPose clears Ctrl_2_locked when Tails is eligible for
        // the ending pose, then latches parent $38 bit 7 so it runs once
        // (sonic3k.asm:181919-181939). Obj_EggCapsule routine $0C calls this
        // while Obj_LevelResults/_unkFAA8 is still active, before End_of_level_flag
        // is set on results exit (sonic3k.asm:181670-181672,62693-62705).
        if (services().playerQuery().nativeP2OrNull() instanceof AbstractPlayableSprite sidekick
                && sidekick.getCpuController() != null) {
            if (sidekick.isPreventTailsRespawn()
                    || sidekick.getAir()
                    || sidekick.getDead()) {
                return;
            }
            tailsEndingPoseApplied = true;
            tailsEndingPoseControllerReleasePending = true;
            lockForResults(sidekick);
        }
    }

    private boolean releasePendingTailsEndingPoseControllerLock() {
        if (!tailsEndingPoseControllerReleasePending) {
            return false;
        }
        if (services().playerQuery().nativeP2OrNull() instanceof AbstractPlayableSprite sidekick
                && sidekick.getCpuController() != null) {
            // The engine's capsule owner runs before the sidekick CPU sample in
            // this collapsed object path. Keep the Set_PlayerEndingPose object
            // control visible first, then expose the Ctrl_2_locked clear on the
            // next owner routine entry to match ROM Check_TailsEndPose ordering.
            sidekick.getCpuController().setController2SignedLocked(false);
            sidekick.getCpuController().mirrorRawController2LogicalForEndingPose();
            tailsEndingPoseControllerReleasePending = false;
            return true;
        }
        return false;
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

    private static final class Aiz2ResultsScreenObjectInstance extends S3kResultsScreenObjectInstance {
        Aiz2ResultsScreenObjectInstance(PlayerCharacter character, int act) {
            super(character, act);
        }

        @Override
        protected boolean shouldRestorePlayerControlsOnExit() {
            // ROM Obj_LevelResultsWait2 clears _unkFAA8 and deletes itself
            // (sonic3k.asm:62693-62705). The AIZ2 owner at loc_7D078 performs
            // Restore_PlayerControl/2 after Check_TailsEndPose observes that
            // flag clear (sonic3k.asm:166696-166703).
            return false;
        }
    }
}
