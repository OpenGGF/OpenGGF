package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Introductory spike barrier and its three flying shard children, object $30 in Act 1 Present
 * ({@code IntroSpikesObject}, objects/r3/intro.asm:536-756; the USA build assembles this recovered copy, not
 * {@code Level/Collision Chaos/Objects/Intro Spikes/Main.asm}). Subtype 0 is the short barrier, subtype 1 the
 * hurting solid barrier, subtypes 2 and 3 the two tall barrier frames (collision {@code $86}). Each barrier except
 * subtype 1 is solid until Metal Sonic ($31) overlaps it: it then breaks (routine 4, frame 4/8/$D), emits three
 * shards (frames {@code d1+d2}, motion records 1-3) and the sound {@code $A3}. The shatter falls through into
 * {@code IntroSpikesSetMotion}, so the broken parent also flies off with motion record 0.
 *
 * <p>The hurting barrier calls SolidObject and, on a top contact with the player standing on it, hurts the player
 * unless time warping, invincible, already hurt or past routine 4 (:562-590), after moving the player back by its Y
 * velocity (:576-584) as Sonic 1's spikes do. Time Attack mode (which deletes the object, :540-541) is not hosted
 * in-level.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class CczIntroSpikes extends CczObject implements ReevaluatingSolidObject, TouchResponseProvider,
        ObjectVisualFacing.NonDirectional {
    /** IntroSpikesMotion: X and Y velocity of shard {@code var_3c} (:730-735). */
    private static final int[][] MOTION = {{0x200, -0x200}, {-0x100, -0x400}, {-0x100, -0x200}, {0x200, -0x400}};
    /** move.w #$A3,d0; jsr PlayFmSound (:702-703). */
    public static final int SHATTER_SOUND = 0xA3;

    private final int shardType;
    private int xVel;
    private int yVel;
    private int gravity;
    private int lifetime;

    public CczIntroSpikes(ObjectSpawn spawn) {
        this(spawn, 0, -1);
    }

    /** A shard child: {@code var_3c} is {@code shardType}, {@code obj.sprite_frame} is {@code frame} (:690-696). */
    CczIntroSpikes(ObjectSpawn spawn, int shardType, int frame) {
        super(spawn, "Intro spikes");
        this.shardType = shardType;
        if (frame >= 0) this.mapFrame = frame;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0 && CczStage.standDownFromIntro(this, services())) return;
        switch (routine) {
            case 0 -> {
                if (init()) main(vIntRunCount, player);
            }
            case 2 -> main(vIntRunCount, player);
            default -> shard();
        }
    }

    /** IntroSpikesInit (:595-625); false where the routine ends the frame itself. */
    private boolean init() {
        var level = cdLevel();
        if (level != null && CczIntroState.of(level).amyCaptured()) {      // tst.b amy_captured; bne IntroSpikesDelete
            setDestroyed(true);
            return false;
        }
        routine = 2;
        priority = 3;
        tileWord = CczObjectArt.INTRO_SPIKES_TILE;
        width = 0x12;
        yRadius = 8;
        int subtype = spawn.subtype();
        if (subtype != 1) {
            yRadius = 0x20;
            if (subtype != 0) {
                mapFrame = 9;
                colType = 0x86;
                width = 0xC;
                if (subtype != 2) mapFrame = 0xE;
            }
        }
        if (shardType == 0) return true;                                   // tst.b var_3c; beq IntroSpikesMain
        colType = 0;
        routine = 4;
        setMotion();
        shard();
        return false;
    }

    /** IntroSpikesMain (:627-678). */
    private void main(int vIntRunCount, PlayableEntity player) {
        int subtype = spawn.subtype();
        if (subtype == 1) {
            solidHurt(vIntRunCount, player);
            return;
        }
        var objects = services().objectManager();
        if (objects != null) {
            for (ObjectInstance other : objects.getActiveObjects()) {
                if (other instanceof CczMetalSonic metal && !metal.isDestroyed() && overlaps(metal)) {
                    routine = 4;
                    spawnShards();
                    if (player != null) ObjectManagerInternalAccess.releaseFromReevaluatingSolid(objects, player, this);
                    return;                                                // jmp GetOffObject
                }
            }
        }
        solidObject(Entry.SOLID);                                          // IntroSpikesSolid (:554-560)
    }

    /** .FoundMetalSonic (:659-678): the two ranges around the spikes' width and height plus $10. */
    private boolean overlaps(CczMetalSonic metal) {
        int d1 = (byte) width + 0x10;
        int d0 = (short) (metal.xPosition() - x + d1);
        if (d0 < 0 || d0 >= 2 * d1) return false;                          // bmi; cmp.w d2,d0; bcc
        d1 = (byte) yRadius + 0x10;
        d0 = (short) (metal.yPosition() - y + d1);
        return d0 >= 0 && d0 < 2 * d1;
    }

    /** IntroSpikesSolidHurt (:562-590). */
    private void solidHurt(int vIntRunCount, PlayableEntity player) {
        if (!solidObject(Entry.SOLID) || !(player instanceof AbstractPlayableSprite sprite) || !stoodOn(sprite)) return;
        if (sprite.getInvincibleFrames() > 0) return;                      // tst.b invincible; tst.b warping is not hosted
        if (sprite.isHurt() || sprite.getDead() || sprite.getInvulnerableFrames() > 0) return;
        NativePositionOps.addYPos16_16(sprite, -(sprite.getYSpeed() << 8));    // :576-584
        boolean hadRings = sprite.getRingCount() > 0;
        if (hadRings && !sprite.hasShield()) services().spawnLostRings(sprite, vIntRunCount);
        sprite.applyHurtOrDeath(x, true, hadRings);
    }

    /** IntroSpikesSpawnShards (:680-715): three shards, the sound, then IntroSpikesSetMotion for this object. */
    private void spawnShards() {
        int base;
        int firstFrame;
        int subtype = spawn.subtype();
        if (subtype == 0) {
            mapFrame = 4;
            firstFrame = 0;
        } else if (subtype == 2) {
            mapFrame = 8;
            firstFrame = 4;
        } else {
            mapFrame = 0xD;
            firstFrame = 9;
        }
        var objects = services().objectManager();
        for (int d1 = 3; d1 >= 1; d1--) {
            if (objects == null) break;
            objects.addDynamicObject(new CczIntroSpikes(new ObjectSpawn(x, y, 0x30, 0, 0, false, 0), d1, d1 + firstFrame));
        }
        if (renderFlagOnScreen()) services().playSfx(SHATTER_SOUND);       // btst #7,obj.sprite_flags(a0); FM_A3
        setMotion();
    }

    /** IntroSpikesSetMotion (:717-728). */
    private void setMotion() {
        int[] motion = MOTION[shardType & 3];
        xVel = motion[0];
        yVel = motion[1];
        gravity = 0x60;
        lifetime = 0x78;
    }

    /** IntroSpikesShard (:738-746): IntroMoveXY, gravity, lifetime. */
    private void shard() {
        moveX(xVel);
        moveY(yVel);
        yVel = (short) (yVel + gravity);
        lifetime = (lifetime - 1) & 0xFF;
        if (lifetime == 0) setDestroyed(true);                             // subq.b #1; beq IntroSpikesDelete
    }

    @Override public int getCollisionFlags() { return colType; }
    @Override public int getCollisionProperty() { return 0; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        CdObjectArtProvider provider = provider();
        if (provider == null) return;
        String key = CczObjectArt.key(provider, CczObjectArt.INTRO_SPIKES, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }
}
