package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.events.S3kAizEventWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * One of the two AIZ2 battleship propellers.
 *
 * <p>ROM: Obj_BattleshipPropeller (docs/skdisasm/sonic3k.asm:105431-105455).
 * Obj_AIZBattleship's init allocates the front ($2E = $3FBC) and back
 * ($2E = $3DCC) propeller with {@code AllocateObjectAfterCurrent}
 * (:105349-105364), so both take real SST slots after the ship and run in
 * the ship's first pass. Each pass the propeller deletes itself once
 * {@code Events_routine_fg == $C} (AIZ2SE_EndRefresh), otherwise it runs
 * {@code Animate_Sprite} with Ani_AIZShipPropeller ({@code 2,0,1,2,3,$FF}:
 * every mapping frame is held for three passes),
 * {@code Translate_Camera2ObjPosition} (:105540-105555) from the ship's
 * secondary camera, and {@code Draw_Sprite}.
 */
public class AizBattleshipPropellerInstance extends AbstractObjectInstance implements RewindRecreatable {
    /** ROM: move.w #$A71,$30(a0) -- propeller Y in secondary-camera space. */
    static final int SECONDARY_Y = 0x0A71;
    /** ROM: front propeller $2E, written before the first allocation. */
    static final int FRONT_SECONDARY_X = 0x3FBC;
    /** ROM: back propeller $2E, written after the first allocation. */
    static final int BACK_SECONDARY_X = 0x3DCC;
    /** ROM: Ani_AIZShipPropeller delay byte. */
    private static final int ANIM_DELAY = 2;
    /** ROM: Ani_AIZShipPropeller frames before the $FF restart code. */
    private static final int[] ANIM_FRAMES = {0, 1, 2, 3};

    /** Ship whose secondary camera ({@code _unkEE98}/{@code _unkEE9C}) the propeller follows. */
    private final AizBattleshipInstance sourceShip;
    /** ROM $2E: X in the ship's secondary-camera space. Non-final for rewind field reapply. */
    private int secondaryX;
    /** ROM anim_frame_timer (starts at 0, so the first Animate_Sprite loads frame 0). */
    private int animFrameTimer;
    /** ROM anim_frame. */
    private int animFrame;
    /** ROM mapping_frame. */
    private int mappingFrame;
    /** ROM x_pos/y_pos written by Translate_Camera2ObjPosition. */
    private int xPos;
    private int yPos;

    public AizBattleshipPropellerInstance(ObjectSpawn spawn, AizBattleshipInstance sourceShip, int secondaryX) {
        super(spawn, "AIZBattleshipPropeller");
        this.sourceShip = sourceShip;
        this.secondaryX = secondaryX;
        this.xPos = spawn != null ? spawn.x() : 0;
        this.yPos = spawn != null ? spawn.y() : 0;
    }

    /** Headless/rewind probe constructor; captured scalar state is reapplied afterwards. */
    AizBattleshipPropellerInstance(ObjectSpawn spawn) {
        this(spawn, null, FRONT_SECONDARY_X);
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        if (ctx == null || ctx.spawn() == null) {
            return null;
        }
        AizBattleshipInstance liveShip = null;
        if (ctx.objectServices() != null && ctx.objectServices().objectManager() != null) {
            for (ObjectInstance instance : ctx.objectServices().objectManager().getActiveObjects()) {
                if (instance instanceof AizBattleshipInstance ship && !ship.isDestroyed()) {
                    liveShip = ship;
                    break;
                }
            }
        }
        // A propeller captured on the ship's exit pass outlives the ship by one
        // pass; with no live ship it deletes on its next entry before translating.
        return new AizBattleshipPropellerInstance(ctx.spawn(), liveShip, secondaryX);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (isDestroyed()) {
            return;
        }
        // ROM Obj_BattleshipPropellerMain: cmpi.w #$C,(Events_routine_fg).w /
        // bne.s loc_50466 / jmp Delete_Current_Sprite (:105441-105445).
        if (S3kAizEventWriteSupport.isAiz2ShipEndRefreshActive(services())) {
            setDestroyed(true);
            return;
        }
        animate();
        translateFromShipCamera();
    }

    private void animate() {
        // ROM Animate_Sprite (:36237-36276): subq.b #1,anim_frame_timer / bcc.s
        // keeps the frame; on borrow it reloads the delay and steps the script.
        animFrameTimer = (animFrameTimer - 1) & 0xFF;
        if (animFrameTimer != 0xFF) {
            return;
        }
        animFrameTimer = ANIM_DELAY;
        if (animFrame >= ANIM_FRAMES.length) {
            animFrame = 0; // $FF: restart from the first frame
        }
        mappingFrame = ANIM_FRAMES[animFrame];
        animFrame++;
    }

    private void translateFromShipCamera() {
        if (sourceShip == null || services().camera() == null) {
            return;
        }
        // ROM Translate_Camera2ObjPosition: y = $30 - _unkEE9C + Camera_Y_pos_copy,
        // x = $2E - _unkEE98 + Camera_X_pos_copy.
        xPos = secondaryX - sourceShip.getSecondaryCameraX() + services().camera().getX();
        yPos = SECONDARY_Y - sourceShip.getSecondaryCameraY() + services().camera().getY();
    }

    /** True while the battleship split-render overlay owns this propeller's draw. */
    boolean drawnByBattleshipOverlay() {
        return sourceShip != null && !sourceShip.isDestroyed();
    }

    /**
     * Draws the propeller over the battleship strip; called by the AIZ2
     * battleship overlay so the sprite keeps its place above the hull.
     */
    public void renderOverBattleship(ObjectRenderManager renderManager) {
        if (isDestroyed() || !drawnByBattleshipOverlay()) {
            return;
        }
        draw(renderManager);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // On the ship's exit pass the overlay stops, but the ROM propeller still
        // reaches Draw_Sprite once before Events_routine_fg reaches $C.
        if (isDestroyed() || drawnByBattleshipOverlay()) {
            return;
        }
        draw(services().renderManager());
    }

    private void draw(ObjectRenderManager renderManager) {
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.AIZ2_SHIP_PROPELLER);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, xPos, yPos, false, false);
    }

    @Override
    public int getX() {
        return xPos;
    }

    @Override
    public int getY() {
        return yPos;
    }

    /**
     * Obj_BattleshipPropellerMain has no range or on-screen delete: the back
     * propeller starts $254 pixels left of the screen and lives until
     * Events_routine_fg reaches $C.
     */
    @Override
    public boolean isPersistent() {
        return !isDestroyed();
    }

    /** ROM: move.w #$80,priority(a0). */
    @Override
    public int getPriorityBucket() {
        return 1;
    }

    @Override
    public boolean isHighPriority() {
        return false;
    }

    int mappingFrameForTest() {
        return mappingFrame;
    }

    int secondaryXForTest() {
        return secondaryX;
    }

    AizBattleshipInstance sourceShipForTest() {
        return sourceShip;
    }
}
