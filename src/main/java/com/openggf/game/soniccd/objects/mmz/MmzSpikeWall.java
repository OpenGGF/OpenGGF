package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProvider;

import java.util.List;

/**
 * Spike wall, object $2B in the R82x and R83x indexes ({@code SpikeWallObject}, objects/r8/spike_wall.asm:3-61): a
 * hurting wall that creeps sideways at $5000 (16.16) for $19A frames, then reverses and flips. Collision type
 * $B1 while {@code stage_layer} is 0, none on the other layer. It is never solid; the routine ends in
 * CheckObjectDespawn2 against its initial X.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class MmzSpikeWall extends MmzObject implements TouchResponseProvider, ObjectVisualFacing.NonDirectional {
    /** move.b #$B1,oVar33 (SpikeWallObject_0_Routine0, objects/r8/spike_wall.asm:17-26): the collision type that applies on layer 0. */
    private static final int HURT_TYPE = 0xB1;
    /** move.l #$5000,oVar2C (SpikeWallObject_0_Routine0, :17-26). */
    private static final int INITIAL_SPEED = 0x5000;
    /** move.w #$199,oVar2A (loc_20E766, :35-43). */
    private static final int PERIOD = 0x199;

    private final int initialX;                  // oVar30
    private boolean initialised;
    private int xFixed;                          // oX.w:oXSub.w
    private int speed = INITIAL_SPEED;           // oVar2C
    private int timer;                           // oVar2A
    private int collisionType;
    private boolean flipStatus;                  // oFlags bit 0
    private CdAnimator animator;
    private int mapFrame;

    public MmzSpikeWall(ObjectSpawn spawn) {
        super(spawn, "SpikeWall");
        this.initialX = spawn.x();
        this.xFixed = spawn.x() << 16;
        this.flipStatus = hFlip();
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialised) {
            // SpikeWallObject_0_Routine0 (objects/r8/spike_wall.asm:17-26): tile $8440, then $4C100110 over
            // oYRadius, oXRadius, oPriority and oWidth.
            initialised = true;
            tileWord = MmzObjectArt.SPIKE_WALL_TILE;
            yRadius = 0x4C;
            priority = 1;
            width = 0x10;
        }
        // SpikeWallObject_0_Routine2 (:27-43).
        xFixed += speed;
        x = xFixed >> 16;
        collisionType = stageLayer() != 0 ? 0 : HURT_TYPE;
        if (--timer < 0) {
            timer = PERIOD;
            speed = -speed;
            flipStatus = !flipStatus;            // bchg #0,oSprFlags and #0,oFlags
        }
        if (animator == null) animator = mmzAnimator("spikeWallAnimations", 0);
        if (animator != null) {
            animator.step();
            mapFrame = animator.mapFrame();
        }
    }

    @Override public int getCollisionFlags() { return collisionType; }
    @Override public int getCollisionProperty() { return 0; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return initialX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        // AnimateObject XORs the script's flip bits into the status flip bits (Object Animate.asm:14-94).
        int flips = (animator == null ? 0 : animator.flipBits()) ^ (flipStatus ? 1 : 0) ^ (vFlip() ? 2 : 0);
        drawSheet(MmzObjectArt.SPIKE_WALL, mapFrame, (flips & 1) != 0, (flips & 2) != 0);
    }
}
