package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.ObjectTerrainUtils;

import java.util.List;

/**
 * Falling spikes, object $32 in R41A ({@code FallSpikesObject}, objects/r4/fall_spikes.asm:3-190):
 * a solid $20 wide, $80 tall block (tile $3A0, priority 3) with a hurt tip ({@link ObjFallSpikesTip}, subtype
 * $FF, collision $B0, frame 1, $30 below it). Routine 2 waits until the player is under it
 * ({@code sub_20EA2E}: player Y in [y + $40, y + $C0) and X in [x - $10, x + $10)); it then accelerates
 * down by $2000 (16.16) a frame for 21 frames, rests for $1F, falls with gravity $3000 (above
 * {@code static_water_y}) or $1000 (below it) until {@code CheckBlockDown} (ObjGetFloorDist at
 * {@code (x, y + $40)}) reports contact, deletes its tip, and sinks a further 32 pixels. While on screen (or in
 * routine 0) it calls SolidObject every frame; it ends in CheckObjectDespawn.
 *
 * <p>The trigger writes {@code obj.y_speed} ($100) but the fall reads the long at {@code oVar2A}, which
 * starts at 0: the speed starts at 0 (shipped behaviour). The $100 stays in {@code oYVel} until landing,
 * where SolidObject's rider logic reads it.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjFallSpikes extends TtzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private int routine;
    private int yFixed;
    /** oVar2A: the 16.16 fall speed. oVar2E: the sink distance. oVar34: gravity. oVar32: a frame counter. */
    private int speed;
    private int sink;
    private int gravity;
    private int counter;
    private int yVel;
    private ObjFallSpikesTip tip;

    public ObjFallSpikes(ObjectSpawn spawn) {
        super(spawn, "FallSpikes");
        yFixed = y << 16;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override public int solidYVelocity() { return yVel; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine != 0 && !renderFlagOnScreen()) return;       // tst.b obj.sprite_flags(a0); bpl.s loc_20E958
        switch (routine) {
            case 0 -> init();
            case 2 -> wait(player);
            case 4 -> accelerate();
            case 6 -> rest();
            case 8 -> fall();
            case 10 -> sink();
            default -> { }
        }
        solidObject(Entry.SOLID);
    }

    /** FallSpikesObject_0_Routine0 (objects/r4/fall_spikes.asm:40-76). */
    private void init() {
        routine = 2;
        priority = 3;
        width = 0x10;
        yRadius = 0x40;
        tileWord = TtzObjectArt.FALL_SPIKES_TILE;
        gravity = y <= staticWaterY() ? 0x3000 : 0x1000;         // cmp.w static_water_y,d1; ble
        tip = new ObjFallSpikesTip(this);
        spawnChildAfter(tip);                                    // bne.s locret_20EA22
    }

    /** FallSpikesObject_0_Routine2 and sub_20EA2E (objects/r4/fall_spikes.asm:81-111). */
    private void wait(PlayableEntity player) {
        var p = mainPlayer();
        if (p == null) return;
        int dy = (p.getCentreY() - y - 0x40) & 0xFFFF;
        if (dy >= 0x80) return;                                  // bcc.s locret_20EA50
        int dx = (p.getCentreX() - x + 0x10) & 0xFFFF;
        if (dx >= 0x20) return;
        routine = 4;
        yVel = 0x100;
        counter = 0x14;
    }

    /** FallSpikesObject_0_Routine4 (objects/r4/fall_spikes.asm:113-125). */
    private void accelerate() {
        move(speed);
        speed += 0x2000;
        counter = (counter - 1) & 0xFFFF;
        if ((counter & 0x8000) != 0) {
            routine = 6;
            speed = 0;
            counter = 0x1E;
        }
    }

    /** FallSpikesObject_0_Routine6 (objects/r4/fall_spikes.asm:130-135). */
    private void rest() {
        counter = (counter - 1) & 0xFFFF;
        if ((counter & 0x8000) != 0) routine = 8;
    }

    /** FallSpikesObject_0_Routine8 (objects/r4/fall_spikes.asm:140-160). */
    private void fall() {
        move(speed);
        speed += gravity;
        int distance = ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();   // CheckBlockDown
        if (distance < 0) {
            routine = 10;
            y += distance;
            yFixed = (yFixed & 0xFFFF) | (y << 16);
            yVel = 0;
            sink = 0x200000;
            if (tip != null) tip.setDestroyed(true);             // DeleteOtherObject
        }
    }

    /** FallSpikesObject_0_RoutineA (objects/r4/fall_spikes.asm:164-172). */
    private void sink() {
        move(speed);
        sink -= speed;
        if (sink < 0) routine = 12;
    }

    /** add.l d0,obj.y(a0), and the same to the tip's oY. */
    private void move(int d0) {
        yFixed += d0;
        y = yFixed >> 16;
        if (tip != null && !tip.isDestroyed()) tip.follow(d0);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        drawSheet(TtzObjectArt.FALL_SPIKES, 0, false, false);
    }
}
