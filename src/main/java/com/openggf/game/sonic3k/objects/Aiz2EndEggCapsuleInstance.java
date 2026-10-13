package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.EggPrisonAnimalInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.SpawnCoordinateRewindRecreatable;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.RewindStateful;
import com.openggf.game.sonic3k.runtime.AizZoneRuntimeState;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Floating upside-down egg prison used by the AIZ2 post-boss cutscene.
 */
public class Aiz2EndEggCapsuleInstance extends AbstractS3kFloatingEndEggCapsuleInstance
        implements SpawnCoordinateRewindRecreatable {
    private static final int RESULTS_OWNER_TAILS_ENDING_POSE_ENTRY = 1;

    private boolean tailsEndingPoseApplied;
    private boolean tailsEndingPoseObjectControlLocked;
    private boolean tailsOpenControllerLockApplied;
    private int tailsOpenControllerLockDelay;
    private int resultsActiveWaitEntries;
    private boolean endingPoseControlsReleased;
    private AbstractPlayableSprite nativeP2EndingPoseOwner;
    private boolean nativeP2EndingPoseUsesExtensionState;
    private final Map<AbstractPlayableSprite, ExtensionEndingPoseState> extensionEndingPoseStates =
            new IdentityHashMap<>();

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
        // sub_865DE runs from the parent slot after the current player/CPU
        // dispatch. Publish its signed Ctrl_2 lock on the next capsule entry.
        tailsOpenControllerLockDelay = 1;
    }

    @Override
    protected void onBeforeCapsuleUpdate() {
        maintainEndingPoseParticipants();
        AizZoneRuntimeState aizState = aizRuntimeStateOrNull();
        if (aizState != null && aizState.tickTailsControlRelease()) {
            releaseTailsControlNow(true);
        }
        if (tailsOpenControllerLockDelay <= 0) {
            return;
        }
        tailsOpenControllerLockDelay--;
        if (tailsOpenControllerLockDelay > 0) {
            return;
        }
        if (services().playerQuery().nativeP2OrNull() instanceof AbstractPlayableSprite sidekick
                && sidekick.getCpuController() != null) {
            sidekick.getCpuController().setController2SignedLocked(true);
            tailsOpenControllerLockApplied = true;
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
    protected boolean defersButtonEligibilityCreatedByParentMotion() {
        // The route-8 parent and button child are distinct native dispatches.
        // Preserve that boundary when the parent's horizontal step is what
        // first moves the button range over a player.
        return true;
    }

    @Override
    protected boolean shouldStartResults(AbstractPlayableSprite player) {
        // sub_868F8 only rejects a dead/airborne/non-playable routine. It then
        // calls Set_PlayerEndingPose, which owns the velocity clears itself.
        return !player.getAir() && !player.getDead();
    }

    @Override
    protected AbstractObjectInstance createResultsScreen() {
        return new Aiz2ResultsScreenObjectInstance(getPlayerCharacter(), services().currentAct());
    }

    @Override
    protected void spawnResultsScreen() {
        // sub_86984 uses AllocateObject, not CreateChild6. The selected SST
        // determines whether Obj_LevelResults runs later in this scan.
        spawnFreeChild(this::createResultsScreen);
    }

    @Override
    protected void onResultsActiveWait() {
        resultsActiveWaitEntries++;
        advanceTailsEndingPoseCheck(false);
    }

    @Override
    protected void onEndingPoseLockClear() {
        advanceTailsEndingPoseCheck(true);
    }

    private void restoreNativePlayerControls() {
        for (var candidate : services().playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
            if (!(candidate instanceof AbstractPlayableSprite player)) {
                continue;
            }
            // ROM loc_7D078 calls Restore_PlayerControl/2 from this capsule
            // owner after both player animation slots have run. The routine
            // writes anim and prev_anim together as $0505, then clears the
            // animation frame/timer while leaving the displayed mapping intact
            // for this frame (sonic3k.asm:166696-166703,180361-180370).
            ObjectControlState.none().applyTo(player);
            player.setAir(false);
            player.setForcedAnimationId(-1);
            player.setAnimationId(Sonic3kAnimationIds.WAIT);
            player.getAnimationManager().publishPreviousAnimationId(
                    Sonic3kAnimationIds.WAIT.id());
            player.setAnimationFrameIndex(0);
            player.setAnimationTick(0);
        }
    }

    private void advanceTailsEndingPoseCheck(boolean force) {
        if (endingPoseControlsReleased) {
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
        if (!tailsEndingPoseApplied
                && nativeP2EndingPoseOwner != null
                && nativeP2EndingPoseOwner.getCpuController() != null
                && isEndingPoseEligible(nativeP2EndingPoseOwner)) {
            AbstractPlayableSprite sidekick = nativeP2EndingPoseOwner;
            tailsEndingPoseApplied = true;
            tailsEndingPoseObjectControlLocked = true;
            boolean wasAir = sidekick.getAir();
            boolean wasOnObject = sidekick.isOnObject();
            sidekick.getCpuController().setController2SignedLocked(false);
            // Check_TailsEndPose runs from the capsule's later SST slot. It
            // clears Ctrl_2_locked and tail-calls Set_PlayerEndingPose, but
            // neither routine writes Ctrl_2_logical; retain the word already
            // published by Player_2 this frame. The following Tails_Control
            // dispatch copies raw Ctrl_2 before object_control=$81 blocks CPU
            // steering (sonic3k.asm:181924-181944,181982-181992,
            // 26195-26212).
            lockForResults(sidekick);
            sidekick.setAir(wasAir);
            sidekick.setOnObject(wasOnObject);
        }
        for (PlayableEntity candidate : services().playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED)) {
            if (!(candidate instanceof AbstractPlayableSprite extension)
                    || extension == services().playerQuery().mainPlayerOrNull()
                    || extension == nativeP2EndingPoseOwner) {
                continue;
            }
            ExtensionEndingPoseState state = extensionEndingPoseStates.computeIfAbsent(
                    extension, ignored -> new ExtensionEndingPoseState());
            if (!state.applied && isEndingPoseEligible(extension)) {
                boolean wasAir = extension.getAir();
                boolean wasOnObject = extension.isOnObject();
                lockForResults(extension);
                extension.setAir(wasAir);
                extension.setOnObject(wasOnObject);
                state.applied = true;
            }
        }
    }

    private void releaseTailsControlNow(boolean restoreNativeP2) {
        if (restoreNativeP2) {
            restoreNativePlayerControls();
        }
        endingPoseControlsReleased = true;
        if (nativeP2EndingPoseOwner != null) {
            AbstractPlayableSprite sidekick = nativeP2EndingPoseOwner;
            if (sidekick.getCpuController() != null
                    && (restoreNativeP2 || tailsOpenControllerLockApplied || tailsEndingPoseApplied)) {
                sidekick.getCpuController().setController2SignedLocked(false);
                sidekick.getCpuController().mirrorRawController2LogicalForEndingPose();
            }
            if (restoreNativeP2 || tailsEndingPoseObjectControlLocked) {
                ObjectControlState.none().applyTo(sidekick);
                sidekick.setControlLocked(false);
            }
        }
        tailsOpenControllerLockApplied = false;
        tailsEndingPoseObjectControlLocked = false;
        for (Map.Entry<AbstractPlayableSprite, ExtensionEndingPoseState> entry
                : extensionEndingPoseStates.entrySet()) {
            releaseExtensionEndingPose(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void onUnload() {
        releaseTailsControlNow(false);
        extensionEndingPoseStates.clear();
    }

    private void maintainEndingPoseParticipants() {
        AbstractPlayableSprite currentNativeP2 = services().playerQuery().nativeP2OrNull()
                instanceof AbstractPlayableSprite sidekick ? sidekick : null;
        bindNativeP2EndingPose(currentNativeP2);
        if (nativeP2EndingPoseUsesExtensionState
                && nativeP2EndingPoseOwner != null
                && nativeP2EndingPoseOwner.getDead()) {
            ObjectControlState.none().applyTo(nativeP2EndingPoseOwner);
            nativeP2EndingPoseOwner.setControlLocked(false);
            tailsEndingPoseApplied = false;
            tailsEndingPoseObjectControlLocked = false;
        }
        List<PlayableEntity> participants = services().playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED);
        for (Map.Entry<AbstractPlayableSprite, ExtensionEndingPoseState> entry
                : new ArrayList<>(extensionEndingPoseStates.entrySet())) {
            if (!containsIdentity(participants, entry.getKey())) {
                releaseExtensionEndingPose(entry.getKey(), entry.getValue());
                extensionEndingPoseStates.remove(entry.getKey());
            } else if (entry.getKey().getDead()) {
                releaseExtensionEndingPose(entry.getKey(), entry.getValue());
            }
        }
    }

    private void bindNativeP2EndingPose(AbstractPlayableSprite current) {
        if (nativeP2EndingPoseOwner == current) {
            return;
        }
        if (nativeP2EndingPoseOwner != null
                && tailsOpenControllerLockApplied
                && nativeP2EndingPoseOwner.getCpuController() != null) {
            nativeP2EndingPoseOwner.getCpuController().setController2SignedLocked(false);
            tailsOpenControllerLockApplied = false;
        }
        if (nativeP2EndingPoseOwner != null && tailsEndingPoseApplied) {
            ExtensionEndingPoseState previous = extensionEndingPoseStates.computeIfAbsent(
                    nativeP2EndingPoseOwner, ignored -> new ExtensionEndingPoseState());
            previous.applied = tailsEndingPoseObjectControlLocked;
        }
        tailsEndingPoseApplied = false;
        tailsEndingPoseObjectControlLocked = false;
        nativeP2EndingPoseUsesExtensionState = false;
        if (current != null) {
            ExtensionEndingPoseState restored = extensionEndingPoseStates.remove(current);
            if (restored != null) {
                tailsEndingPoseApplied = restored.applied;
                tailsEndingPoseObjectControlLocked = restored.applied;
                nativeP2EndingPoseUsesExtensionState = true;
            }
        }
        nativeP2EndingPoseOwner = current;
    }

    private static boolean isEndingPoseEligible(AbstractPlayableSprite player) {
        return !player.isPreventTailsRespawn() && !player.getAir() && !player.getDead();
    }

    private static boolean containsIdentity(List<PlayableEntity> participants, PlayableEntity target) {
        for (PlayableEntity participant : participants) {
            if (participant == target) {
                return true;
            }
        }
        return false;
    }

    private static void releaseExtensionEndingPose(
            AbstractPlayableSprite extension, ExtensionEndingPoseState state) {
        if (!state.applied) {
            return;
        }
        ObjectControlState.none().applyTo(extension);
        extension.setControlLocked(false);
        state.applied = false;
    }

    private static final class ExtensionEndingPoseState
            implements RewindStateful<ExtensionEndingPoseState.Snapshot> {
        private boolean applied;

        @Override
        public Snapshot captureRewindStateValue() {
            return new Snapshot(applied);
        }

        @Override
        public void restoreRewindStateValue(Snapshot snapshot) {
            applied = snapshot.applied();
        }

        private record Snapshot(boolean applied) {
        }
    }

    private static final class HighPriorityAnimal extends EggPrisonAnimalInstance {
        HighPriorityAnimal(ObjectSpawn spawn, int delay, int artVariant) {
            super(spawn, delay, artVariant);
        }

        private HighPriorityAnimal(ObjectSpawn spawn) {
            this(spawn, 0, 0);
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

        private Aiz2ResultsScreenObjectInstance() {
            super(true);
        }

        @Override
        public Aiz2ResultsScreenObjectInstance recreateForRewind(RewindRecreateContext ctx) {
            return ObjectConstructionContext.construct(ctx.objectServices(),
                    Aiz2ResultsScreenObjectInstance::new);
        }

        @Override
        protected boolean shouldRestorePlayerControlsOnExit() {
            // ROM Obj_LevelResultsWait2 clears _unkFAA8 and deletes itself
            // (sonic3k.asm:62693-62705). AIZEndBoss_StartPostDefeatCutscene
            // owns Restore_PlayerControl/2 after its own SST dispatch observes
            // that flag clear (sonic3k.asm:138263-138276).
            return false;
        }

        @Override
        protected boolean shouldRestoreCameraBoundsOnExit(int zone, int act) {
            // Obj_LevelResultsWait2 clears the result flag and deletes its SST.
            // AIZEndBoss_StartPostDefeatCutscene retains the arena and creates
            // Obj_IncLevEndXGradual from the surviving boss slot; there is no
            // full-level camera restore (sonic3k.asm:62693-62705,138263-138276).
            return false;
        }

        @Override
        protected void onExitReady() {
            boolean returnsToHost = services().runPolicy().returnsToHostOnActCompletion();
            super.onExitReady();
            if (!returnsToHost) {
                // The final child decrements $30; Obj_LevelResultsWait2 reads
                // that zero from its own SST on the next scan, then clears
                // _unkFAA8 (sonic3k.asm:62685-62705). Publish here so the
                // surviving boss observes the release in native slot order.
                Aiz2BossEndSequenceState.releaseEggCapsule();
            }
        }

        // AIZEndBoss_StartPostDefeatCutscene owns Restore_PlayerControl/2.
        // The floating capsule's loc_866CC only calls Check_TailsEndPose and
        // continues swinging after _unkFAA8 clears (sonic3k.asm:138263-138276,
        // 181675-181679). Do not schedule the later restore from loc_7D078:
        // that is a different post-boss owner, not this AIZ capsule route.
    }

    private AizZoneRuntimeState aizRuntimeStateOrNull() {
        var registry = services().zoneRuntimeRegistry();
        return registry == null ? null
                : registry.currentAs(AizZoneRuntimeState.class).orElse(null);
    }
}
