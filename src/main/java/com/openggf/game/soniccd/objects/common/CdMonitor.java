package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.player.CdSonicAnimation;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Monitor, object $19 with subtype 0-7 ({@code ObjMonitor}, Level/_Objects/Monitor and Time
 * Post/Main.asm:269-409). The same source holds the time posts and the HUD time icon
 * (subtypes 8 and up, Monitor and Time Post/Main.asm:7-236); they belong to the time-travel lane and are not hosted here.
 *
 * <p>Routine 2 does nothing while the retained on-screen bit is clear (Monitor and Time Post/Main.asm:336-338); otherwise it
 * falls after a hit from below ({@code ObjMoveGrv}: old velocity, then +$38, capped at $1000
 * down, Object Functions.asm:42-90) or is solid, then animates Ani_Monitor (frame $10 is the
 * static). A break (routine 4) spawns the item $1A and an explosion $18 with FindObjSlot, marks
 * saved-flag bit 0 and shows frame $11. Player_TouchMonitor is Sonic 1's React_Monitor (Palmtree
 * Panic/Player Object Collision.asm:102-122). On a stage whose objects test layers (Metallic Madness) a layer-1 monitor
 * draws in front ({@code ori.b #$80,oTile; move.b #0,oPriority}, Monitor and Time Post/Main.asm:308-313) and
 * {@code ObjMonitor_Solid} is solid only for a player on the monitor's layer (:242-259). The time-stop gate on
 * animation (Monitor and Time Post/Main.asm:364-365) is not hosted (power-up lane).
 */
public final class CdMonitor extends CdObject
        implements TouchResponseProvider, TouchResponseListener, ReevaluatingSolidObject,
        ObjectVisualFacing.NonDirectional {
    /** .NotBroken: move.b #$40|6,oColType(a0) (Monitor and Time Post/Main.asm:329). */
    private static final int COLLISION_FLAGS = 0x46;
    /** ObjMonitor_Break: move.b #$11,oMapFrame(a0) (Monitor and Time Post/Main.asm:408). */
    static final int BROKEN_FRAME = 0x11;

    private int routine;
    private boolean falling;
    private int ySub;
    private int yVel;
    private boolean pendingBreak;
    private CdAnimator animator;
    private int mapFrame;

    public CdMonitor(ObjectSpawn spawn) {
        super(spawn, "Monitor");
        // ObjMonitor_Init: oYRadius $E, tile $5A8, priority 3, oWidth $F (Monitor and Time Post/Main.asm:302-317).
        this.yRadius = 0xE;
        this.width = 0xF;
        this.priority = 3;
        this.tileWord = CdObjectArtProvider.MONITOR_TILE;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) init();
        if (pendingBreak) {
            pendingBreak = false;
            breakOpen();
            return;
        }
        if (routine != 2 || !renderFlagOnScreen()) return;
        // ObjMonitor_Main: fall, or SolidObject while resting (Monitor and Time Post/Main.asm:336-357).
        if (falling) {
            fall();
        } else {
            if (onPlayerLayer()) solidObject(ReevaluatingSolidObject.Entry.SOLID);   // ObjMonitor_Solid (:242-259)
        }
        if (animator != null) {
            animator.step();
            mapFrame = animator.mapFrame();
        }
    }

    private void init() {
        routine = 2;
        if (frontLayerOfLayeredStage()) {                       // Monitor and Time Post/Main.asm:308-313
            tileWord |= 0x8000;
            priority = 0;
        }
        // bclr #7 of the saved flags lets the spawner place the monitor again (Monitor and Time Post/Main.asm:319-320); the broken
        // bit 0 keeps it broken (Monitor and Time Post/Main.asm:322-326), which the shared respawn state models as remembered.
        animator = animator(e -> e.monitorAnimations(), spawn.subtype());
    }

    /** Monitor and Time Post/Main.asm:343-350: ObjMoveGrv then ObjGetFloorDist. */
    private void fall() {
        int position = (y << 8) | ySub;
        position += yVel;
        yVel += 0x38;
        if (yVel >= 0 && yVel >= 0x1000) yVel = 0x1000;
        y = position >> 8;
        ySub = position & 0xFF;
        var floor = ObjectTerrainUtils.checkFloorDist(x, y, yRadius);
        if (floor.hasCollision()) {
            y += floor.distance();
            yVel = 0;
            falling = false;
        }
    }

    /** ObjMonitor_Break (Monitor and Time Post/Main.asm:381-409). */
    private void breakOpen() {
        routine = 8;
        services().playSfx(com.openggf.audio.GameSound.BADNIK_HIT); // FM_DESTROY (Monitor and Time Post/Main.asm:382)
        var objects = services().objectManager();
        if (objects != null) {
            objects.addDynamicObject(new CdMonitorItem(x, y, spawn.subtype(), 0));
            objects.addDynamicObject(new CdExplosion(x, y, 1, false, 0, 0));
        }
        ObjectLifetimeOps.markSpawnRemembered(objects, spawn);
        mapFrame = BROKEN_FRAME;
    }

    @Override public boolean shouldStayActiveWhenRemembered() { return true; }

    @Override
    public void onTouchResponse(PlayableEntity playerEntity, TouchResponseResult result, int frameCounter) {
        if (routine != 2 || !(playerEntity instanceof AbstractPlayableSprite player) || player.isCpuControlled()) return;
        if (player.getYSpeed() < 0) {
            // Player_TouchMonitor: from below, bounce when oY(a0)-$10 >= oY(a1) (Player Object Collision.asm:102-114).
            if (player.getCentreY() - 0x10 >= y) {
                player.setYSpeed((short) -player.getYSpeed());
                yVel = -0x180;
                falling = true;
            }
            return;
        }
        // .GoingDown: cmpi.b #2,oAnim(a0) (roll); neg.w oYVel(a0); addq.b #2,oRoutine(a1) (Monitor and Time Post/Main.asm:116-120).
        if (player.getAnimationId() != CdSonicAnimation.ROLL.id()) return;
        player.setYSpeed((short) -player.getYSpeed());
        routine = 4;
        pendingBreak = true;
    }

    @Override public int getCollisionFlags() { return routine == 2 ? COLLISION_FLAGS : 0; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public TouchResponseProfile getTouchResponseProfile() { return TouchResponseProfile.fromProvider(this); }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multi) { return TouchResponseProfile.fromProvider(this, multi); }

    /** SolidObject: cmpi.b #$19,oID(a0) lets a rolling player through (Object Solid.asm:329-332). */
    @Override public Role solidRole() { return Role.MONITOR; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    public int mapFrame() { return mapFrame; }
    boolean broken() { return routine >= 4; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        drawFrame(CdObjectArtProvider.MONITOR, mapFrame, false, false);
    }
}
