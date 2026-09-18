package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ROM {@code Obj_LevelIntro_PlayerRun} (sonic3k.asm:89940-89966), placed in dynamic slot 2 by
 * {@code SpawnLevelMainSprites} loc_6986 for Hidden Palace ({@code $1601}), Death Egg 1
 * ({@code $B00}), and Knuckles in Carnival Night 1 ({@code $300}) and Lava Reef 1 ({@code $900}).
 *
 * <p>On its first execution it stores {@code Player_1 x_pos + $B0} and {@code y_pos}, then (falling
 * straight into {@code loc_44A26}) every frame locks {@code Ctrl_1}, writes a held Right into
 * {@code Ctrl_1_logical}, and forces the camera to track the stored point. Once
 * {@code x_pos + $10 >= $30(a0)} (unsigned) it clears both control locks and deletes itself.
 */
public final class LevelIntroPlayerRunInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private static final int RUN_DISTANCE = 0xB0;
    private static final int RELEASE_MARGIN = 0x10;

    private boolean initialized;
    /** {@code $30(a0)}. */
    private int targetX;
    /** {@code $32(a0)}. */
    private int targetY;

    public LevelIntroPlayerRunInstance(ObjectSpawn spawn) {
        super(spawn, "LevelIntroPlayerRun");
    }

    /**
     * Runs the object's init against Player_1's start location. The ROM executes it in the
     * load-time Process_Sprites pass directly after SpawnLevelMainSprites placed the player there.
     */
    public void captureStartTarget(int playerX, int playerY) {
        initialized = true;
        targetX = (playerX + RUN_DISTANCE) & 0xFFFF;
        targetY = playerY & 0xFFFF;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (!(services().playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite leader)) {
            return;
        }
        if (!initialized) {
            captureStartTarget(leader.getCentreX(), leader.getCentreY());
        }
        // loc_44A26: Ctrl_1_locked = 1, Ctrl_1_logical = button_right_mask<<8 (held, no press).
        leader.setControlLocked(true);
        leader.setForcedInputMask(AbstractPlayableSprite.INPUT_RIGHT);
        services().camera().requestForcedScroll(targetX, targetY);
        if (((leader.getCentreX() + RELEASE_MARGIN) & 0xFFFF) < targetX) {
            return;
        }
        // loc_44A56: clear Ctrl_1_locked and Ctrl_2_locked, then delete.
        leader.setControlLocked(false);
        leader.clearForcedInputMask();
        for (PlayableEntity entity : services().playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
            if (entity != leader && entity instanceof AbstractPlayableSprite sprite) {
                sprite.setControlLocked(false);
            }
        }
        setDestroyed(true);
    }

    @Override public boolean isPersistent() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Controller object only.
    }
}
