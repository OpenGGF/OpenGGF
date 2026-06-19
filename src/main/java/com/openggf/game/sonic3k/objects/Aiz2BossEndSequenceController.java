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
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.sprites.NativePositionOps;
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
public class Aiz2BossEndSequenceController extends AbstractObjectInstance implements RewindRecreatable {

    // ROM: Camera_stored_max_X_pos = _unkFA84 + $158
    private static final int MAX_X_TARGET_OFFSET = 0x158;
    // ROM: loc_69526 — stop walking when x_pos >= _unkFA84 + $1F8
    private static final int PLAYER_STOP_X_OFFSET = 0x1F8;
    // ROM: loc_695A8 — transition when y_pos >= _unkFA86 + $1E6
    private static final int NEXT_LEVEL_Y_OFFSET = 0x1E6;
    // Obj_LevelResults clears _unkFAA8 shortly after End_of_level_flag becomes
    // visible to this engine's collapsed object pass. Hold the ending pose for
    // that ROM wait before loc_694D4 restores control and loc_69526 forces right.
    private static final int POST_RESULTS_CONTROL_RESTORE_DELAY = 10;
    private static final short POST_RESULTS_INITIAL_WALK_SPEED = 0x000C;
    private static final int POST_BUTTON_CAMERA_MAX_Y_TARGET = 0x1000;
    private static final int INC_LEVEL_END_Y_GRADUAL_STEP = 0x8000;

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
    private boolean postButtonMaxYReleaseActive;
    private int postButtonMaxYAccumulator;
    private int postResultsControlRestoreDelay = -1;

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
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        ObjectSpawn spawn = ctx.spawn() != null
                ? ctx.spawn()
                : new ObjectSpawn(0, 0, Sonic3kObjectIds.EGG_CAPSULE, 0, 0, false, 0);
        return new Aiz2BossEndSequenceController(spawn.x(), spawn.y());
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
            postResultsControlRestoreDelay = POST_RESULTS_CONTROL_RESTORE_DELAY;
        }
        if (postResultsControlRestoreDelay > 0) {
            postResultsControlRestoreDelay--;
            holdEndingPose(player);
            return;
        }

        // Start post-capsule sequence (music + walk right)
        if (!postCapsuleSequenceStarted) {
            startPostCapsuleSequence(player);
        }
        if (pendingLookUpInputAfterStop) {
            pendingLookUpInputAfterStop = false;
            player.setForceInputRight(false);
            player.clearForcedInputMask();
            player.setForcedInputMask(AbstractPlayableSprite.INPUT_UP);
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
            player.clearForcedInputMask();
            player.setForceInputRight(false);
            player.setControlLocked(false);
            services().camera().setMaxYTarget((short) POST_BUTTON_CAMERA_MAX_Y_TARGET);
            postButtonMaxYReleaseActive = true;
            postButtonMaxYAccumulator = 0;
        }
        updatePostButtonCameraMaxYRelease();

        // Phase: Wait for player to fall past Y threshold, then transition
        if (buttonHandled && !transitionRequested) {
            int transitionY = arenaBaseY + NEXT_LEVEL_Y_OFFSET;
            if ((player.getCentreY() & 0xFFFF) >= transitionY) {
                transitionRequested = true;
                services().requestSessionSave(SaveReason.PROGRESSION_SAVE);
                services().requestZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0, true);
            }
        }
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
        holdSidekickEndingPose(player);
    }

    private void startPostCapsuleSequence(AbstractPlayableSprite player) {
        postCapsuleSequenceStarted = true;
        services().camera().setMaxXTarget((short) (arenaMaxX + MAX_X_TARGET_OFFSET));
        ObjectControlState.none().applyTo(player);
        player.setControlLocked(true);
        forceRightLogicalInput(player);
        applyFirstForcedRightWalkTick(player);
        restoreSidekickPostResultsControl(player);
        setSidekickControlLocked(player, true);
    }

    private void forceRightLogicalInput(AbstractPlayableSprite player) {
        // ROM writes Ctrl_1_logical while Ctrl_1_locked is set, so Sonic_RecordPos
        // stores the forced RIGHT word for Tails' delayed CPU replay.
        player.setForceInputRight(false);
        player.setForcedInputMask(AbstractPlayableSprite.INPUT_RIGHT);
        player.writeLogicalInputAndCurrentFollowerHistory(AbstractPlayableSprite.INPUT_RIGHT, false);
    }

    private void applyFirstForcedRightWalkTick(AbstractPlayableSprite player) {
        player.setXSpeed(POST_RESULTS_INITIAL_WALK_SPEED);
        player.setGSpeed(POST_RESULTS_INITIAL_WALK_SPEED);

        int advancedSubpixel = (player.getXSubpixelRaw() & 0xFFFF)
                + (((int) POST_RESULTS_INITIAL_WALK_SPEED) << 8);
        if (advancedSubpixel > 0xFFFF) {
            NativePositionOps.addXPosPreserveSubpixel(player, advancedSubpixel >>> 16);
        }
        player.setSubpixelRaw(advancedSubpixel & 0xFFFF, player.getYSubpixelRaw());
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

    private void holdSidekickEndingPose(AbstractPlayableSprite player) {
        ObjectPlayerQuery query = new ObjectPlayerQuery(
                () -> player,
                () -> services().playerQuery().sidekicks());
        for (PlayableEntity sidekick : query.playersFor(
                ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
            if (sidekick == player) {
                continue;
            }
            if (sidekick instanceof AbstractPlayableSprite sprite) {
                sprite.setControlLocked(true);
                sprite.setXSpeed((short) 0);
                sprite.setYSpeed((short) 0);
                sprite.setGSpeed((short) 0);
                ObjectControlState.nativeBit7FullControl().applyTo(sprite);
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
