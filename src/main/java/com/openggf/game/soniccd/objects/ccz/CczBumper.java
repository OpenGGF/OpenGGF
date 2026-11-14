package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdPoints;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Pinball bumper, object {@code $1D} ({@code ObjBumper}, Level/Collision Chaos/Objects/Bumper and Flipper/Main.asm:15-117;
 * the stages that assemble {@code objects/r3/bumper_flipper.asm} ({@code BumperObject}) run the same routine with renamed
 * fields). A $10 square, touch type {@code $D7} (tile {@code $4CE}, priority 1). The subtype gives a slide range:
 * bit 1 adds $40, bit 0 adds $20 (so 0, $20, $40 or $60 steps of a pixel; zero stands still); bit 7 slides along X
 * instead of Y, bit 6 picks the first direction. Each touch (the special-touch collision status) counts down five
 * prizes: 100 points and a 100-point sprite; plays sub-CPU command {@code $7F} on screen; and throws the player away
 * from the bumper at {@code -$700} along the angle from the player to the bumper ({@code CalcAngle}, {@code CalcSine}):
 * the in-air bit set, the push bit and the jump flag cleared; the object runs animation 1.
 *
 * <p>Not modelled: the per-object saved-flags counter ({@code savedObjFlags}+2, incremented per touch up to $8A,
 * Bumper and Flipper/Main.asm:82-90), which no hosted object reads. The same routine is Metallic Madness act 3's
 * {@code BumperObject} ({@code r8/object_index_3.asm:32}); that stage's tile and mappings come from its own catalogue.
 * The despawn is CheckObjDespawn2 on the starting X.
 */
@CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
public final class CczBumper extends CczObject implements TouchResponseProvider, TouchResponseListener,
        ObjectVisualFacing.NonDirectional {
    /** move.w #$7F,d0; jsr SubCPUCmd (Bumper and Flipper/Main.asm:78-81). */
    public static final int BUMP_COMMAND = 0x7F;

    private int baseX;               // oVar32
    private int range;               // oVar30
    private int steps;               // oVar2E
    private int velocity;            // oVar2A (long)
    private int prizes;              // oVar34
    private boolean touched;         // oColStatus

    public CczBumper(ObjectSpawn spawn) {
        super(spawn, "Bumper");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) init();
        main(vIntRunCount, player);
    }

    /** ObjBumper_Init (:20-50); the routine falls into ObjBumper_Main. */
    private void init() {
        routine = 2;
        tileWord = CczObjectArt.BUMPER_TILE;
        spriteFlip = 0;
        width = 0x10;
        yRadius = 0x10;
        priority = 1;
        colType = 0xD7;
        baseX = x;
        prizes = 5;
        int subtype = spawn.subtype();
        int d0 = 0;
        if ((subtype & 2) != 0) d0 += 0x40;
        if ((subtype & 1) != 0) d0 += 0x20;
        range = d0;
        steps = d0 >> 1;
        if (steps == 0) return;                                            // beq.s ObjBumper_Main
        velocity = 0x10000;
        if ((subtype & 0x40) == 0) velocity = -velocity;
    }

    /** ObjBumper_Main (:52-117). */
    private void main(int vIntRunCount, PlayableEntity player) {
        if (velocity != 0) {
            if ((spawn.subtype() & 0x80) != 0) addToX(velocity);           // tst.w oSubtype(a0); bmi.s .Move
            else addToY(velocity);
            steps = (steps - 1) & 0xFFFF;
            if (steps == 0) {
                steps = range;
                velocity = -velocity;
            }
        }
        if (touched) {
            touched = false;                                               // clr.b oColStatus(a0)
            if (prizes != 0) {
                prizes--;
                var game = services().gameState();
                if (game != null) game.addScore(0xA * 10);                 // moveq #$A,d0; bsr.w AddPoints
                var objects = services().objectManager();
                if (objects != null) objects.addDynamicObject(new CdPoints(new ObjectSpawn(x, y, 0x1C, 0x80, 0, false, 0)));
            }
            if (renderFlagOnScreen()) services().playMusic(BUMP_COMMAND);  // tst.b oSprFlags; bpl .NoSound
            if (player instanceof AbstractPlayableSprite sprite) throwPlayer(sprite);
            if (animator == null) animator = CczObjectArt.animator(provider(), "bumperAnimations", 0);
            if (animator != null) animator.anim = 1;                       // move.b #1,oAnim(a0)
        }
        if (animator == null) animator = CczObjectArt.animator(provider(), "bumperAnimations", 0);
        animate();
    }

    /** :83-106: CalcAngle of (bumper - player), CalcSine, speed {@code $F900} (-$700) times each, {@code asr.l #8}. */
    private void throwPlayer(AbstractPlayableSprite sprite) {
        int d1 = (short) (x - sprite.getCentreX());
        int d2 = (short) (y - sprite.getCentreY());
        int angle = TrigLookupTable.calcAngle((short) d1, (short) d2) & 0xFF;
        int sin = TrigLookupTable.sinHex(angle);
        int cos = TrigLookupTable.cosHex(angle);
        sprite.setXSpeed((short) ((cos * -0x700) >> 8));
        sprite.setYSpeed((short) ((sin * -0x700) >> 8));
        sprite.setAir(true);                                               // bset #1,oFlags(a1)
        sprite.setPushing(false);                                          // bclr #4 / #5,oFlags(a1)
        sprite.setJumping(false);                                          // clr.b oVar3C(a1)
    }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        touched = true;                                                    // loc_207502: addq.b #1,oColStatus(a1)
    }

    @Override public int getCollisionFlags() { return colType; }
    @Override public int getCollisionProperty() { return 0; }

    /** A {@code $C0}-class type only adds to the collision status (loc_207472, r3/player_object_collide.asm:233-249, 281-283). */
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return (getCollisionFlags() & 0xC0) == 0xC0; }
    @Override public TouchResponseProfile getTouchResponseProfile() { return TouchResponseProfile.fromProvider(this); }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multi) { return TouchResponseProfile.fromProvider(this, multi); }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return baseX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        drawSheet(CczObjectArt.BUMPER, mapFrame);
    }

    public int prizes() { return prizes; }
    public int steps() { return steps; }
    public int velocity() { return velocity; }
}
