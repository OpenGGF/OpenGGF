package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.save.SaveReason;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnCoordinateRewindRecreatable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * AIZ2 post-boss controller for the Sonic/Tails route.
 *
 * <p>ROM reference: loc_694D4 onward.
 *
 * <p>Sequence:
 * <ol>
 *   <li>Wait for egg capsule release (results screen finished)</li>
 *   <li>Play level music, force Sonic right until X &ge; stop coordinate</li>
 *   <li>Stop Sonic, spawn cutscene Knuckles</li>
 *   <li>Wait for Knuckles to finish his laugh/jump/button sequence</li>
 *   <li>Bridge collapses, Sonic falls in hurt animation</li>
 *   <li>Transition to HCZ when Sonic falls past Y threshold</li>
 * </ol>
 */
public class Aiz2BossEndSequenceController extends AbstractObjectInstance
        implements SpawnCoordinateRewindRecreatable {

    // ROM: Camera_stored_max_X_pos = _unkFA84 + $158
    private static final int MAX_X_TARGET_OFFSET = 0x158;
    // ROM: loc_69526 — stop walking when x_pos >= _unkFA84 + $1F8
    private static final int PLAYER_STOP_X_OFFSET = 0x1F8;
    // ROM: loc_695A8 — transition when y_pos >= _unkFA86 + $1E6
    private static final int NEXT_LEVEL_Y_OFFSET = 0x1E6;
    // Restore_PlayerControl2 reaches a still-riding native P2 through the later
    // solid-support path before loc_69526 can expose P1's forced walk. Preserve
    // those two extra object entries from the live Status_OnObj state.
    private static final int POST_RESULTS_CONTROL_RESTORE_DELAY = 4;
    private static final int RIDING_SIDEKICK_CONTROL_RESTORE_DELAY = 6;
    private static final int POST_BUTTON_CAMERA_MAX_Y_TARGET = 0x1000;
    private static final int INC_LEVEL_END_Y_GRADUAL_STEP = 0x8000;
    private static final int AIRBORNE_CAMERA_TARGET_OFFSET = 0x80;

    // Non-final so the generic rewind field capturer reapplies them after a
    // generic recreate. The captured spawn x/y make these correct before reapply.
    private int arenaMaxX;
    private int arenaBaseY;
    private boolean initialized;
    private boolean postCapsuleSequenceStarted;
    private boolean knucklesSpawned;
    private boolean buttonHandled;
    private boolean transitionRequested;
    private boolean pendingLookUpInputAfterStop;
    private boolean pendingButtonControlRelease;
    private boolean postButtonMaxYReleaseActive;
    private int postButtonMaxYAccumulator;
    private int postResultsControlRestoreDelay = -1;
    private boolean postResultsMaxXActive;
    private int postResultsMaxXAccumulator;

    public Aiz2BossEndSequenceController(int arenaMaxX, int arenaBaseY) {
        super(new ObjectSpawn(arenaMaxX, arenaBaseY, Sonic3kObjectIds.EGG_CAPSULE, 0, 0, false, 0),
                "AIZ2BossEndSequence");
        this.arenaMaxX = arenaMaxX;
        this.arenaBaseY = arenaBaseY;
    }

    Aiz2BossEndSequenceController(ObjectSpawn spawn) {
        this(spawn.x(), spawn.y());
    }

    @Override
    public int getX() {
        return arenaMaxX;
    }

    @Override
    public int getY() {
        return arenaBaseY;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (!(playerEntity instanceof AbstractPlayableSprite player)) {
            return;
        }

        if (!initialized) {
            initialize(player);
        }

        // Wait for results screen to finish (egg capsule sets this flag)
        if (!Aiz2BossEndSequenceState.isEggCapsuleReleased()) {
            player.clearForcedInputMask();
            player.setForceInputRight(false);
            return;
        }

        if (postResultsControlRestoreDelay < 0) {
            postResultsControlRestoreDelay = hasRidingSidekick(player)
                    ? RIDING_SIDEKICK_CONTROL_RESTORE_DELAY
                    : POST_RESULTS_CONTROL_RESTORE_DELAY;
        }
        if (postResultsControlRestoreDelay > 0) {
            postResultsControlRestoreDelay--;
            holdEndingPose(player);
            if (postResultsControlRestoreDelay == 0) {
                // The later controller slot reaches loc_69526 after the player
                // slot but before its separately allocated camera-bound child.
                // Expose the logical Right word now so the next player pass
                // owns acceleration/animation without advancing that child.
                ObjectControlState.none().applyTo(player);
                forceRightLogicalInput(player);
            }
            return;
        }

        // Start post-capsule sequence (music + walk right)
        boolean startedPostCapsuleSequenceNow = !postCapsuleSequenceStarted;
        if (startedPostCapsuleSequenceNow) {
            startPostCapsuleSequence(player);
        }
        if (!startedPostCapsuleSequenceNow) {
            updatePostResultsCameraMaxX();
        }
        clearPositiveLockedSidekickLogicalWord(player);
        if (pendingLookUpInputAfterStop) {
            pendingLookUpInputAfterStop = false;
            player.setForceInputRight(false);
            player.clearForcedInputMask();
            player.setForcedInputMask(AbstractPlayableSprite.INPUT_UP);
        }

        if (pendingButtonControlRelease) {
            pendingButtonControlRelease = false;
            player.clearForcedInputMask();
            player.setForceInputRight(false);
            player.setControlLocked(false);
        }

        // Phase: Walk right until reaching stop coordinate
        if (!knucklesSpawned) {
            int stopX = arenaMaxX + PLAYER_STOP_X_OFFSET;
            if (player.getCentreX() < stopX) {
                // ROM: loc_69526 — force right until x_pos >= threshold
                player.setControlLocked(true);
                forceRightLogicalInput(player);
                setSidekickControlLocked(player, true);
                return;
            }

            // ROM: loc_69546 — Stop_Object and spawn Knuckles
            knucklesSpawned = true;
            player.setControlLocked(true);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setGSpeed((short) 0);
            // ROM loc_69546 only runs Stop_Object and advances the controller.
            // loc_69588 writes UP on the next object pass, after the next
            // player physics tick has consumed the previous RIGHT logical word.
            pendingLookUpInputAfterStop = true;
            setSidekickControlLocked(player, true);
            spawnDynamicObject(CutsceneKnucklesAiz2Instance.createDefault());
        }

        // Phase: Wait for button press (triggered by Knuckles animation)
        if (!buttonHandled && Aiz2BossEndSequenceState.isButtonPressed()) {
            buttonHandled = true;
            // Bridge collapses — release all player locks so the bridge's
            // ejectStandingPlayers() can set the hurt-fall state and the
            // animation system doesn't overwrite it.
            // The button occupies an earlier SST slot than this controller. It
            // clears Ctrl_1_locked after the player slot has already consumed
            // the controller's final UP word; loc_69588 observes that clear and
            // advances without writing another word. Preserve that last logical
            // input until the next engine player pass instead of letting the
            // shared cutscene latch erase it in this collapsed object update.
            pendingButtonControlRelease = true;
            services().camera().setMaxYTarget((short) POST_BUTTON_CAMERA_MAX_Y_TARGET);
            postButtonMaxYReleaseActive = true;
            postButtonMaxYAccumulator = 0;
        }
        // Phase: Wait for player to fall past Y threshold, then transition
        if (buttonHandled && !transitionRequested) {
            int transitionY = arenaBaseY + NEXT_LEVEL_Y_OFFSET;
            if ((player.getCentreY() & 0xFFFF) >= transitionY) {
                transitionRequested = true;
                // StartNewLevel is entered from this later object slot after
                // the player moved, but before the normal DeformLayers camera
                // pass. Preserve the camera target derived from the position
                // visible at the start of player physics; the transition load
                // will clear the temporary freeze with the fresh level state.
                Camera camera = services().camera();
                camera.setY((short) ((player.getPrePhysicsCentreY() & 0xFFFF)
                        - AIRBORNE_CAMERA_TARGET_OFFSET));
                camera.setFrozen(true);
                services().requestSessionSave(SaveReason.PROGRESSION_SAVE);
                services().requestZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0, true);
                // StartNewLevel stops the current object pass. The separately
                // allocated Obj_IncLevEndYGradual child is in a later slot, so
                // it cannot add its accumulator high word on the handoff frame.
                return;
            }
        }
        updatePostButtonCameraMaxYRelease();
    }

    private void initialize(AbstractPlayableSprite player) {
        initialized = true;
        Aiz2BossEndSequenceState.triggerBridgeDrop();
        player.clearForcedInputMask();
        player.setForceInputRight(false);
    }

    private void holdEndingPose(AbstractPlayableSprite player) {
        player.setControlLocked(true);
        player.clearForcedInputMask();
        player.setForceInputRight(false);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        ObjectControlState.nativeBit7FullControl().applyTo(player);
    }

    private boolean hasRidingSidekick(AbstractPlayableSprite player) {
        for (PlayableEntity sidekick : services().playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
            if (sidekick != player && sidekick instanceof AbstractPlayableSprite sprite
                    && sprite.isOnObject()) {
                return true;
            }
        }
        return false;
    }

    private void startPostCapsuleSequence(AbstractPlayableSprite player) {
        postCapsuleSequenceStarted = true;
        postResultsMaxXActive = true;
        // Child6_IncLevX has already reached the last fractional step before
        // the controller exposes forced-right movement.
        postResultsMaxXAccumulator = 0xC000;
        ObjectControlState.none().applyTo(player);
        player.setControlLocked(true);
        forceRightLogicalInput(player);
        restoreSidekickPostResultsControl(player);
        setSidekickControlLocked(player, true);
    }

    private void updatePostResultsCameraMaxX() {
        if (!postResultsMaxXActive) {
            return;
        }
        Camera camera = services().camera();
        postResultsMaxXAccumulator += 0x4000;
        int delta = (postResultsMaxXAccumulator >>> 16) & 0xFFFF;
        int target = arenaMaxX + MAX_X_TARGET_OFFSET;
        int next = (camera.getMaxX() & 0xFFFF) + delta;
        if (next >= target) {
            next = target;
            postResultsMaxXActive = false;
        }
        camera.setMaxX((short) next);
        camera.setMaxXTarget((short) next);
    }

    private void forceRightLogicalInput(AbstractPlayableSprite player) {
        // ROM writes Ctrl_1_logical while Ctrl_1_locked is set, so Sonic_RecordPos
        // stores the forced RIGHT word for Tails' delayed CPU replay.
        player.setForceInputRight(false);
        player.setForcedInputMask(AbstractPlayableSprite.INPUT_RIGHT);
        player.writeLogicalInputAndCurrentFollowerHistory(AbstractPlayableSprite.INPUT_RIGHT, false);
    }

    private void updatePostButtonCameraMaxYRelease() {
        if (!postButtonMaxYReleaseActive) {
            return;
        }

        Camera camera = services().camera();
        if (camera == null) {
            return;
        }

        postButtonMaxYAccumulator = (postButtonMaxYAccumulator + INC_LEVEL_END_Y_GRADUAL_STEP) & 0xFFFFFFFF;
        int yDelta = (postButtonMaxYAccumulator >>> 16) & 0xFFFF;
        int nextMaxY = (camera.getMaxY() & 0xFFFF) + yDelta;
        if (nextMaxY >= POST_BUTTON_CAMERA_MAX_Y_TARGET) {
            camera.setMaxY((short) POST_BUTTON_CAMERA_MAX_Y_TARGET);
            postButtonMaxYReleaseActive = false;
            return;
        }

        camera.setMaxY((short) nextMaxY);
        camera.setMaxYTarget((short) POST_BUTTON_CAMERA_MAX_Y_TARGET);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }

    private void setSidekickControlLocked(AbstractPlayableSprite player, boolean locked) {
        ObjectPlayerQuery query = new ObjectPlayerQuery(
                () -> player,
                () -> services().playerQuery().sidekicks());
        for (PlayableEntity sidekick : query.playersFor(
                ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
            if (sidekick == player) {
                continue;
            }
            if (sidekick instanceof AbstractPlayableSprite sprite) {
                sprite.setControlLocked(locked);
                if (!locked) {
                    sprite.clearForcedInputMask();
                }
            }
        }
    }

    private void clearPositiveLockedSidekickLogicalWord(AbstractPlayableSprite player) {
        ObjectPlayerQuery query = new ObjectPlayerQuery(
                () -> player,
                () -> services().playerQuery().sidekicks());
        for (PlayableEntity sidekick : query.playersFor(
                ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
            if (sidekick == player) {
                continue;
            }
            if (sidekick instanceof AbstractPlayableSprite sprite
                    && sprite.getCpuController() != null) {
                // ROM loc_863C0 runs after Player_2 and uses a positive
                // Ctrl_2_locked byte: CPU control still executes, then this
                // object clears Ctrl_2_logical before the frame is observed.
                sprite.getCpuController().clearController2LogicalLatch();
            }
        }
    }

    private void restoreSidekickPostResultsControl(AbstractPlayableSprite player) {
        ObjectPlayerQuery query = new ObjectPlayerQuery(
                () -> player,
                () -> services().playerQuery().sidekicks());
        for (PlayableEntity sidekick : query.playersFor(
                ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
            if (sidekick == player) {
                continue;
            }
            if (sidekick instanceof AbstractPlayableSprite sprite) {
                ObjectControlState.none().applyTo(sprite);
                sprite.setForcedAnimationId(-1);
            }
        }
    }
}
