package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProvider;

import java.util.List;

/**
 * Stardust Speedway's spike ball ({@code SpikeBallObject}, objects/r7/spike_ball.asm:3-102): a hurt
 * ball that moves along a fixed velocity for {@code var_30} frames, rests {@code var_2e} frames, moves
 * back for {@code var_2c} frames and rests again. Subtype and time zone pick the record of
 * {@code word_20E506} (X and Y speed in 8.8, the two durations); the rest comes from
 * {@code word_20E536} by time zone. It is drawn every frame and ends in {@code CheckObjectDespawn2}
 * with the starting X. Tidal Tempest's spike ball (objects/r4/spike_ball.asm) is another routine.
 */
public final class ObjSpikeBall extends CdObject implements TouchResponseProvider, ObjectVisualFacing.NonDirectional {
    /** move.b #$B5,obj.collide_type(a0): category $80 (hurt), size $35 (objects/r7/spike_ball.asm:25). */
    private static final int COLLISION = 0xB5;

    private boolean initialised;
    /** obj.routine: 2 moving, 4 resting (SpikeBallObject, :14-17). */
    private int routine;
    private int originX;
    /** obj.x and obj.y as 16.16 (add.l d0,obj.x(a0), :60-61). */
    private int xFixed;
    private int yFixed;
    /** obj.var_32 and obj.var_36: the 16.16 speeds (:50-53); var_2c/var_2e/var_30 the counters (:36, :54-55). */
    private int xSpeed;
    private int ySpeed;
    private int returnFrames;
    private int pauseFrames;
    private int timer;

    public ObjSpikeBall(ObjectSpawn spawn) {
        super(spawn, "Spike ball");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialised && !init()) return;
        if (routine == 2) {
            move();
        } else {
            rest();
        }
    }

    /** SpikeBallObject_0_Routine0 (:21-55); it falls into Routine2 on the same frame. */
    private boolean init() {
        var level = cdLevel();
        var provider = artProvider();
        if (level == null || provider == null) return false;
        var payload = level.payload();
        priority = 3;
        width = 0x10;
        yRadius = 0x10;
        tileWord = SharedObjectCatalogue.offset(payload, "spikeBallTile");
        originX = x;                                         // move.w obj.x(a0),obj.var_2a(a0) (:31)
        int zone = timeZone();
        var image = provider.image();
        pauseFrames = image.u16(SharedObjectCatalogue.offset(payload, "spikeBallPauses") + zone * 2);
        // SpikeBallObject_0_Routine0: time_zone*16 + subtype*8 into the move table (objects/r7/spike_ball.asm:37-45).
        int record = SharedObjectCatalogue.offset(payload, "spikeBallMoves") + zone * 16 + (getSpawn().subtype() & 0xFF) * 8;
        xSpeed = image.u16(record) << 8;                     // lsl.l #8,d0 on the zero-extended word (:46-53)
        ySpeed = image.u16(record + 2) << 8;
        returnFrames = image.u16(record + 4);
        timer = image.u16(record + 6);
        xFixed = x << 16;
        yFixed = y << 16;
        routine = 2;
        initialised = true;
        return true;
    }

    /** SpikeBallObject_0_Routine2 (:57-71). */
    private void move() {
        xFixed += xSpeed;
        yFixed += ySpeed;
        x = xFixed >>> 16;
        y = yFixed >>> 16;
        timer = (timer - 1) & 0xFFFF;
        if (timer != 0) return;
        routine = 4;
        timer = pauseFrames;
        xSpeed = -xSpeed;
        ySpeed = -ySpeed;
    }

    /** SpikeBallObject_0_Routine4 (:75-82). */
    private void rest() {
        timer = (timer - 1) & 0xFFFF;
        if (timer != 0) return;
        routine = 2;
        timer = returnFrames;
    }

    /** SpikeBallObject: the starting X into CheckObjectDespawn (objects/r7/spike_ball.asm:3-10; common/object_spawn.asm:316-329). */
    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return originX; }

    @Override public int getCollisionFlags() { return initialised ? COLLISION : 0; }
    @Override public int getCollisionProperty() { return 0; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialised) drawFrame(SharedObjectArt.SPIKE_BALL, 0, hFlip(), vFlip());
    }

    public int originX() { return originX; }
}
