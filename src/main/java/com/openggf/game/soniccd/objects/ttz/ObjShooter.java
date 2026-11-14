package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * Shooter, object $42 in R42A ({@code ShooterObject}, objects/r4/shooter.asm:3-54): a
 * wall cannon (tile $4318, 8 wide, $20 tall, priority 3) that releases a missile every $1F frames
 * ({@code move.w #$1E} then a counter that runs to -1, loc_20F2A6 :29-50). Subtype 0 shoots left ({@code -$30000}
 * 16.16 a frame), any other subtype shoots right and flips; the missile is spawned at the barrel
 * ({@code x ± $20}, {@code y + 4}) as the same ID with subtype $FF ({@link ObjShooterMissile}). It ends in
 * CheckObjectDespawn.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjShooter extends TtzObject implements ObjectVisualFacing.NonDirectional {
    /** ShooterObject: move.l #-$30000,obj.var_2c(a0) (objects/r4/shooter.asm:16). */
    private static final int MISSILE_SPEED = -0x30000;

    private boolean initialised;
    private int timer;
    private int barrelX;
    private int velocity;
    private boolean facingRight;

    public ObjShooter(ObjectSpawn spawn) {
        super(spawn, "Shooter");
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialised) init();
        timer = (timer - 1) & 0xFFFF;                           // addi.w #-1,obj.var_2a(a0); bpl.s
        if ((timer & 0x8000) == 0) return;
        timer = 0x1E;
        ObjShooterMissile missile = new ObjShooterMissile(barrelX, y + 4, velocity, facingRight, priority + 1);
        spawnChild(missile);                                    // jsr SpawnObject; bne.s loc_20F31A
    }

    /** ShooterObject first frame (objects/r4/shooter.asm:6-27). */
    private void init() {
        initialised = true;
        priority = 3;
        width = 8;
        yRadius = 0x10;
        tileWord = TtzObjectArt.SHOOTER_TILE;
        velocity = MISSILE_SPEED;
        int offset = 0x20;
        if (spawn.subtype() != 0) {                             // bset #0 sprite flags and flags; neg.l; neg.w d6
            facingRight = true;
            velocity = -velocity;
            offset = -0x20;
        }
        barrelX = x + offset;                                   // loc_20F29C: obj.var_30 = x + d6
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        drawSheet(TtzObjectArt.SHOOTER, 0, facingRight, false);
    }
}
