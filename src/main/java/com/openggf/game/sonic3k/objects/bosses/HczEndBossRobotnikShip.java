package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.mutators.ObjectHeadRole;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;

import java.util.List;
import java.util.Objects;

/**
 * HCZ's subtype-5 Obj_RobotnikShip2: an independent SST object, not a boss-lifetime
 * component. Head and flame are actual after-current allocations with their own
 * dispatch, drawing and deletion (sonic3k.asm:136365-136487).
 *
 * <p>Native boss geyser/Boss_flag terminal lifetime remains a separate implementation gap.
 */
public class HczEndBossRobotnikShip extends AbstractObjectInstance implements RewindRecreatable {
    private static final int ROUTINE_INIT = 0;
    private static final int ROUTINE_MAIN = 2;
    private static final int ROUTINE_WAIT_DEFEAT = 4;
    private static final int ROUTINE_READY = 6;
    private static final int ROUTINE_ESCAPE = 8;
    private static final int SHIP_FRAME_IDLE = 5;
    private static final int SHIP_FRAME_ESCAPE = 5;
    private static final int CHILD_OFFSET_Y = 0x0C;
    private static final int READY_TARGET_OFFSET = 0x40;
    private static final int READY_RISE_VEL = -0x100;
    private static final int ESCAPE_X_VEL = 0x300;
    private static final int ESCAPE_TIMER = 0x100;

    /** Exact parent3 while routines still consume it; retired at WAIT -> READY. */
    private HczEndBossInstance boss;
    private int routine;
    private int currentX;
    private int currentY;
    private int xFixed;
    private int yFixed;
    private int xVel;
    private int yVel;
    private int timer;
    private int shipFrame = SHIP_FRAME_IDLE;
    private boolean shipFacingRight;
    private boolean shipFlipY;
    /** Native $38, including Go_Delete_Sprite_2's bit 4. */
    private int control;
    private boolean highPriority;
    private boolean visible;
    private boolean pendingDelete;

    public HczEndBossRobotnikShip(HczEndBossInstance boss) {
        this(new ObjectSpawn(boss.getX(), (boss.getY() + CHILD_OFFSET_Y) & 0xFFFF, 0, 5, 0, false, 0));
        this.boss = Objects.requireNonNull(boss, "boss");
    }

    /** Pure shell: exact parent3 hydrates after all captured object IDs exist. */
    private HczEndBossRobotnikShip(ObjectSpawn spawn) {
        super(spawn, "HCZEndBossRobotnikShip");
        currentX = spawn.x();
        currentY = spawn.y();
        xFixed = currentX << 16;
        yFixed = currentY << 16;
    }

    @Override
    public HczEndBossRobotnikShip recreateForRewind(RewindRecreateContext context) {
        return new HczEndBossRobotnikShip(context.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        visible = false;
        if (pendingDelete) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        switch (routine) {
            case ROUTINE_INIT -> updateInit();
            case ROUTINE_MAIN -> updateMain();
            case ROUTINE_WAIT_DEFEAT -> updateWaitDefeat();
            case ROUTINE_READY -> updateReady();
            case ROUTINE_ESCAPE -> updateEscape();
            default -> throw new IllegalStateException("Invalid HCZ ship routine " + routine);
        }
        // Obj_RobotnikShip2 returns through Draw_Sprite even after Go_Delete_Sprite_2.
        visible = true;
        updateDynamicSpawn(currentX, currentY);
    }

    private HczEndBossInstance requireBoss() {
        return Objects.requireNonNull(boss, "HCZ ship missing exact parent3 before escape");
    }

    private void refreshFromBoss() {
        HczEndBossInstance owner = requireBoss();
        currentX = owner.getState().x;
        shipFacingRight = owner.isFacingRight();
        shipFlipY = (owner.getState().renderFlags & 2) != 0;
        currentY = (owner.getState().y + (shipFlipY ? -CHILD_OFFSET_Y : CHILD_OFFSET_Y)) & 0xFFFF;
        xFixed = currentX << 16;
        yFixed = currentY << 16;
    }

    private void updateInit() {
        // CreateChild1_Normal already wrote the initial offset. Ship INIT does
        // not call Refresh_ChildPositionAdjusted; MAIN first copies live flips.
        shipFrame = SHIP_FRAME_IDLE;
        // Child_SyncDraw follows attributes. HCZ's modeled normal boss inlet has
        // $38.bit6 clear: its owning routines write bits 1/3/4, not bit 6.
        // Do not substitute status.bit6 or render_flags.bit6 for that missing input.
        control &= ~0x40;
        highPriority = requireBoss().isHighPriority();
        spawnChild(() -> new HczEndBossRobotnikHead(this));
        routine = ROUTINE_MAIN; // Allocation failure does not retry.
    }

    private void updateMain() {
        refreshFromBoss();
        if (requireBoss().getState().defeated) {
            routine = ROUTINE_WAIT_DEFEAT;
            // Obj_RobotnikShipMain attempts subtype 4 once, even if the SST is full.
            spawnChild(() -> new HczEndBossShipExplosionController(this));
        }
    }

    private void updateWaitDefeat() {
        if (requireBoss().getState().routine >= 16) { // Existing HCZ flee / $38.bit4 inlet.
            shipFrame = SHIP_FRAME_ESCAPE;
            xVel = 0;
            yVel = READY_RISE_VEL;
            routine = ROUTINE_READY;
            // READY/ESCAPE never read native parent3 again.
            boss = null;
        }
    }

    private void updateReady() {
        // Unsigned cmp.w precedes subq.w; the fractional word is untouched.
        int targetY = (services().camera().getY() + READY_TARGET_OFFSET) & 0xFFFF;
        if ((currentY & 0xFFFF) > targetY) {
            currentY = (currentY - 1) & 0xFFFF;
            yFixed = (currentY << 16) | (yFixed & 0xFFFF);
            return;
        }
        shipFacingRight = true;
        xVel = ESCAPE_X_VEL;
        yVel = 0;
        timer = ESCAPE_TIMER;
        routine = ROUTINE_ESCAPE;
        spawnChild(() -> new HczEndBossRobotnikFlame(this));
    }

    private void updateEscape() {
        xFixed += xVel << 8;
        currentX = xFixed >> 16;
        timer = (short) (timer - 1);
        if (timer < 0) {
            // Dispatch 257 selects Delete_Current_Sprite for the NEXT dispatch.
            // It still draws this pass; later Head/Flame slots consume bit 4.
            control |= 0x30;
            pendingDelete = true;
        }
    }

    HczEndBossInstance bossForHeadInit() { return requireBoss(); }
    boolean deleteSelected() { return (control & 0x10) != 0; }
    boolean facingRight() { return shipFacingRight; }
    boolean flippedY() { return shipFlipY; }
    int nativeControl() { return control; }
    int xVelocity() { return xVel; }

    @Override public int getX() { return currentX; }
    @Override public int getY() { return currentY; }
    public void setPosition(int x, int y) { currentX = x; currentY = y; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x280); }
    @Override public boolean isHighPriority() { return highPriority; }
    @Override public ObjectHeadRole headPresentationRole() { return ObjectHeadRole.BOSS; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible || isDestroyed()) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndexForcedPriority(shipFrame, currentX, currentY,
                    shipFacingRight, shipFlipY, 0, highPriority);
        }
    }
}
