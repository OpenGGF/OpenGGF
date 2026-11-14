package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Quartz Quadrant's spikes ({@code SpikesObject}, objects/r5/spikes.asm:18-252), a different routine
 * from the Level-dialect spikes ({@code ObjSpikes}, common lane) under the same name: subtype bits 0-1
 * pick up, down, right or left ({@code SpikesProperties}: oWidth, oYRadius, collision type, :61-65),
 * bit 2 makes them slide in and out by 8 a frame between offsets 0 and $20 with 60-frame rests
 * ({@code SpikesCheckMove}, :222-246). Upward spikes hurt a player standing on them; the others
 * hurt by touch ($83 down, $82 sideways). Tidal Tempest's (objects/r4/spikes.asm) and Stardust
 * Speedway's (objects/r7/spikes.asm) are further routines and are not this class.
 *
 * <p>The attachment ({@code spikes.attach}, :24-36) is written by no layout path; the spikes
 * follow it only when another object sets it, which no hosted object does.
 */
public final class ObjQqzSpikes extends CdObject implements ReevaluatingSolidObject, TouchResponseProvider,
        ObjectVisualFacing.NonDirectional {
    /** SpikesCheckMove: offset steps of 8 up to $20, 60-frame rests (objects/r5/spikes.asm:222-246). */
    private static final int STEP = 8, MAX_OFFSET = 0x20, REST = 60;

    private boolean initialised;
    private int type;
    private int originX;
    private int originY;
    private int collisionType;
    private int timer;
    private int offset;
    private boolean outwards;

    public ObjQqzSpikes(ObjectSpawn spawn) {
        super(spawn, "Spikes");
        originX = spawn.x();
        originY = spawn.y();
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialised && !init()) return;
        move();
        if (type != 0) {
            solidObject(ReevaluatingSolidObject.Entry.SOLID);   // SpikesMain (objects/r5/spikes.asm:94-99)
            return;
        }
        // .Upwards: SolidObject; beq; btst #3,obj.flags(a0); warping; invincible (:101-110).
        if (!solidObject(ReevaluatingSolidObject.Entry.SOLID) || !(player instanceof AbstractPlayableSprite sprite)
                || !stoodOn(sprite) || sprite.getInvincibleFrames() > 0) {
            return;
        }
        // cmpi.b #4,obj.routine(a0); bcc; tst.w obj.var_30(a0); bne (:114-117).
        if (sprite.isHurt() || sprite.getDead() || sprite.getInvulnerableFrames() > 0) return;
        // obj.y.l -= y_speed << 8, then HurtPlayer with the spikes as the source (:119-126).
        NativePositionOps.addYPos16_16(sprite, -(sprite.getYSpeed() << 8));
        boolean hadRings = sprite.getRingCount() > 0;
        if (hadRings && !sprite.hasShield()) services().spawnLostRings(sprite, vIntRunCount);
        sprite.applyHurtOrDeath(x, true, hadRings);
    }

    /** SpikesInit (objects/r5/spikes.asm:69-90). */
    private boolean init() {
        var level = cdLevel();
        var provider = artProvider();
        if (level == null || provider == null) return false;
        var payload = level.payload();
        priority = 3;
        tileWord = SharedObjectCatalogue.offset(payload, "qqzSpikesTile");
        type = getSpawn().subtype() & 3;
        // add.b d0,d0; add.b d1,d0; lea SpikesProperties(pc,d0.w),a1: three bytes per type.
        int properties = SharedObjectCatalogue.offset(payload, "qqzSpikesProperties") + type * 3;
        width = provider.image().u8(properties);
        yRadius = provider.image().u8(properties + 1);
        collisionType = provider.image().u8(properties + 2);
        initialised = true;
        return true;
    }

    /** SpikesMove and its four movements (objects/r5/spikes.asm:146-210). */
    private void move() {
        if ((getSpawn().subtype() & 4) == 0) return;
        checkMove();
        switch (type) {
            case 0 -> y = originY + offset;
            case 1 -> y = originY - offset;
            case 2 -> x = originX - offset;
            default -> x = originX + offset;
        }
    }

    /** SpikesCheckMove (objects/r5/spikes.asm:222-246). */
    private void checkMove() {
        if (timer != 0 && --timer != 0) return;
        if (outwards) {
            offset -= STEP;
            if (offset >= 0) return;                     // subq.b #8; bcc.s .End
            offset = 0;
            outwards = false;
            timer = REST;
        } else {
            offset += STEP;
            if (offset < MAX_OFFSET) return;             // cmpi.b #$20; bcs.s .End
            offset = MAX_OFFSET;
            outwards = true;
            timer = REST;
        }
    }

    @Override public int getCollisionFlags() { return initialised ? collisionType : 0; }
    @Override public int getCollisionProperty() { return 0; }

    /** .CheckDespawn: the origin X's chunk against the camera's, $280, no on-screen test (:38-49). */
    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return originX; }

    /** andi.w #$FF80 on both; camera - $80; cmpi.w #$280,d0; bhi .Despawn (objects/r5/spikes.asm:38-47). */
    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        int chunk = originX & 0xFF80;
        int cameraChunk = (cameraX - 0x80) & 0xFF80;
        return ((chunk - cameraChunk) & 0xFFFF) > 0x280;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialised) drawFrame(SharedObjectArt.QQZ_SPIKES, type, hFlip(), vFlip());
    }

    public int offset() { return offset; }
}
