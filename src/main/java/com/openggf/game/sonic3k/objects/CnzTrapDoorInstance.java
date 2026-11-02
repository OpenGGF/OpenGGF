package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectVisualFacing;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0x44 - CNZ Trap Door ({@code Obj_CNZTrapDoor}).
 * <p>
 * ROM behavior:
 * <ul>
 *   <li>{@code Map_CNZTrapDoor} with {@code ArtTile_CNZMisc+$9F}</li>
 *   <li>{@code loc_31CCA} checks both players against the 64x32 trigger box in
 *       {@code sub_31CFA}, then calls {@code SolidObjectTop} every frame whatever the
 *       animation state, then {@code Animate_Sprite} (sonic3k.asm:67262-67273)</li>
 *   <li>Plays {@code sfx_TrapDoor} when the trigger box is entered</li>
 *   <li>Animates via {@code Ani_CNZTrapDoor} and stays open while the trigger box is occupied</li>
 * </ul>
 *
 * <p>Coordinates use the ROM x_pos/y_pos center semantics directly. The engine-side
 * object position therefore stays centered on the spawn point; no top-left conversion
 * is applied here.
 */
public class CnzTrapDoorInstance extends AbstractObjectInstance
        implements RewindRecreatable, SolidObjectProvider, ObjectVisualFacing.NonDirectional {

    /** ROM {@code move.w #$80,priority(a0)} at Obj_CNZTrapDoor init: bucket 1. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x80);

    // ROM: move.w #$20,d1 / move.w #9,d3 / jsr SolidObjectTop
    private static final SolidObjectParams SOLID_PARAMS =
            SolidObjectParams.of(0x20, 9, 9);

    private static final int FRAME_CLOSED = 0;
    private static final int FRAME_OPENING = 1;
    private static final int FRAME_OPEN = 2;

    // ROM: Ani_CNZTrapDoor uses 5-frame timing on the open sequence.
    private static final int OPEN_STEP_FRAMES = 5;

    private int renderFrame = FRAME_CLOSED;
    private int state = FRAME_CLOSED;
    private int stateTimer = 0;

    public CnzTrapDoorInstance(ObjectSpawn spawn) {
        super(spawn, "CNZTrapDoor");
    }

    @Override
    public CnzTrapDoorInstance recreateForRewind(RewindRecreateContext ctx) {
        return new CnzTrapDoorInstance(ctx.spawn());
    }

    @Override
    public SolidExecutionMode solidExecutionMode() {
        return SolidExecutionMode.MANUAL_CHECKPOINT;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        boolean triggerOccupied = isTriggerOccupied(playerEntity);

        for (PlayableEntity participant : services().playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED)) {
            triggerOccupied |= isTriggerOccupied(participant);
            if (triggerOccupied) {
                break;
            }
        }

        if (state == FRAME_CLOSED && triggerOccupied) {
            startOpening();
        }

        // loc_31CCA: move.w #$20,d1 / move.w #9,d3 / move.w x_pos(a0),d4 /
        // jsr SolidObjectTop, before Animate_Sprite and with no state test.
        checkpointAll();
        advanceAnimation(triggerOccupied);
    }

    private boolean isTriggerOccupied(PlayableEntity entity) {
        return entity instanceof AbstractPlayableSprite player && shouldTrigger(player);
    }

    private boolean shouldTrigger(AbstractPlayableSprite player) {
        if (player == null || player.isObjectControlled()) {
            return false;
        }

        // ROM trigger box from sub_31CFA (sonic3k.asm:67278-67293):
        //   x: objX - playerX + $20, unsigned compare against $40
        //   y: objY - playerY + $20, unsigned compare against $20
        // so the player's centre must be 1-$20 px below the hinge.
        int dx = player.getCentreX() - spawn.x();
        if (dx < -0x1F || dx > 0x20) {
            return false;
        }
        int dy = player.getCentreY() - spawn.y();
        return dy > 0 && dy <= 0x20;
    }

    private void startOpening() {
        state = FRAME_OPENING;
        renderFrame = FRAME_OPENING;
        stateTimer = OPEN_STEP_FRAMES;

        try {
            services().playSfx(Sonic3kSfx.TRAP_DOOR.id);
        } catch (Exception ignored) {
            // Audio is unavailable in some test setups.
        }
    }

    private void advanceAnimation(boolean triggerOccupied) {
        if (state == FRAME_CLOSED) {
            renderFrame = FRAME_CLOSED;
            return;
        }

        if (stateTimer > 0) {
            stateTimer--;
            return;
        }

        if (state == FRAME_OPENING) {
            state = FRAME_OPEN;
            renderFrame = FRAME_OPEN;
            stateTimer = OPEN_STEP_FRAMES;
            return;
        }

        if (triggerOccupied) {
            renderFrame = FRAME_OPEN;
            return;
        }

        state = FRAME_CLOSED;
        renderFrame = FRAME_CLOSED;
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public boolean rejectsZeroDistanceTopSolidLanding() {
        // SolidObjectTop's new-landing test at loc_1E45A accepts only the
        // overlap band [-$10,-1]: sub.w d1,d0 then cmpi.w #-$10,d0 / blo
        // returns on an exact d0=0 (sonic3k.asm:42044-42053).
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.CNZ_TRAP_DOOR);
        if (renderer != null && renderer.isReady()) {
            boolean hFlip = (spawn.renderFlags() & 0x01) != 0;
            boolean vFlip = (spawn.renderFlags() & 0x02) != 0;
            renderer.drawFrameIndex(renderFrame, spawn.x(), spawn.y(), hFlip, vFlip);
        }
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    int getRenderFrameForTest() {
        return renderFrame;
    }

    boolean isOpenForTest() {
        return state != FRAME_CLOSED;
    }
}
