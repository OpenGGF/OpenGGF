package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Bumper platform, object $2A ({@code ObjBumperPlatform}, Level/Collision Chaos/Objects/Bumper Platform/Main.asm:15-147): a
 * $20 wide, $10 high platform (tile {@code $435E}, priority 1) with touch type {@code $E7} that slides back and forth
 * ({@code oVar2E} steps of a 16.16 velocity of one pixel a frame, reversed after {@code oVar30} steps): along X when
 * subtype bit 7 is set (range $C0, first leg $60), along Y otherwise (range $A0, first leg $50); subtype bit 6 picks the
 * first direction. A touch (the object's collision status) plays {@code FM_B5} on screen and throws the player: a side
 * hit within 8 pixels vertically only zeroes his X speed; otherwise straight up or down at $700 when within $18 of the
 * centre horizontally, else diagonally at $4F3 each way, with the in-air bit set and the push and jump bits cleared.
 *
 * <p>The despawn is CheckObjDespawn2 on the base X (the original X).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class CczBumperPlatform extends CczObject implements TouchResponseProvider, TouchResponseListener,
        ObjectVisualFacing.NonDirectional {
    /** move.w #$B5,d0; jsr PlayFMSound (:61-63). */
    public static final int BUMP_SOUND = 0xB5;

    private int baseX;               // oVar3E
    private int range;               // oVar30
    private int steps;               // oVar2E
    private int velocity;            // oVar2A (long)
    private boolean touched;         // oColStatus

    public CczBumperPlatform(ObjectSpawn spawn) {
        super(spawn, "Bumper platform");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) init();
        main(player);
    }

    /** ObjBumperPlatform_Init (:33-57); the routine falls into ObjBumperPlatform_Main. */
    private void init() {
        baseX = x;
        routine = 2;
        tileWord = CczObjectArt.BUMPER_PLATFORM_TILE;
        spriteFlip = 0;
        width = 0x20;
        yRadius = 0x10;
        priority = 1;
        colType = 0xE7;
        int subtype = spawn.subtype();
        range = 0xC0;
        steps = 0x60;
        if ((subtype & 0x80) == 0) {
            range = 0xA0;
            steps = 0x50;
        }
        velocity = 0x10000;
        if ((subtype & 0x40) == 0) velocity = -velocity;
    }

    /** ObjBumperPlatform_Main (:59-132). */
    private void main(PlayableEntity player) {
        if (touched) {
            touched = false;                                               // clr.b oColStatus(a0)
            if (renderFlagOnScreen()) services().playSfx(BUMP_SOUND);
            if (player instanceof AbstractPlayableSprite sprite) bump(sprite);
        }
        if ((spawn.subtype() & 0x80) != 0) addToX(velocity);               // lea oX(a0),a1 when bit 7 is set
        else addToY(velocity);
        steps = (steps - 1) & 0xFFFF;
        if (steps == 0) {
            steps = range;
            velocity = -velocity;
        }
    }

    private void bump(AbstractPlayableSprite sprite) {
        int d5 = (short) (sprite.getCentreX() - x);
        int d6 = (short) (sprite.getCentreY() - y);
        if (((d6 + 8) & 0xFFFF) < 0x10) {                                  // subi #-8; subi #$10; bcc.s .CheckHorizontal
            sprite.setXSpeed((short) 0);
            return;
        }
        if (((d5 + 0x18) & 0xFFFF) < 0x30) {                               // vertical throw
            sprite.setYSpeed((short) (d6 < 0 ? -0x700 : 0x700));
        } else {                                                           // .LaunchDiagonal
            sprite.setXSpeed((short) (d5 < 0 ? -0x4F3 : 0x4F3));
            sprite.setYSpeed((short) (d6 < 0 ? -0x4F3 : 0x4F3));
        }
        sprite.setAir(true);                                               // bset #1,oFlags(a1)
        sprite.setPushing(false);                                          // bclr #4 / #5,oFlags(a1)
    }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        touched = true;
    }

    @Override public int getCollisionFlags() { return colType; }
    @Override public int getCollisionProperty() { return 0; }

    /**
     * A {@code $C0}-class type is the native special touch: type {@code $27} only adds to the collision status
     * ({@code loc_207472} -> {@code loc_207502}, r3/player_object_collide.asm:358-359), never a hurt or boss bounce.
     */
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return (getCollisionFlags() & 0xC0) == 0xC0; }
    @Override public TouchResponseProfile getTouchResponseProfile() { return TouchResponseProfile.fromProvider(this); }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multi) { return TouchResponseProfile.fromProvider(this, multi); }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return baseX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        CdObjectArtProvider provider = provider();
        if (provider == null) return;
        String key = CczObjectArt.key(provider, CczObjectArt.BUMPER_PLATFORM, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, 0, false, false);
    }

    public int steps() { return steps; }
    public int velocity() { return velocity; }
}
