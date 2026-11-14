package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * The outermost piece of Stardust Speedway's swing ({@link ObjSwing}; {@code obj.var_3c == obj.var_2f},
 * objects/r7/swing.asm:92-95, 119-130): sprite frame 1, collision type $A9 (hurt) and a solid body
 * whose velocity is its movement this frame, so a rider is carried. {@code sub_20CE9A} (:20-46) calls
 * SolidObject with a height of 8, then $C, and lifts a player in contact above it to sit on top.
 */
public final class ObjSwingEnd extends ObjSwing implements ReevaluatingSolidObject, TouchResponseProvider {
    /** move.b #$A9,obj.collide_type(a1): category $80 (hurt), size $29 (:95). */
    private static final int COLLISION = 0xA9;

    private int xSpeed;
    private int ySpeed;

    ObjSwingEnd(ObjectSpawn spawn, int link, int links, int anchorX, int anchorY) {
        super(spawn, link, links, anchorX, anchorY, 1);
    }

    @Override
    void afterSwing(PlayableEntity player, int oldX, int oldY) {
        // asr.l #8 of the 16.16 difference: the movement in 8.8, stored as words (:122-129).
        xSpeed = (short) ((x - oldX) << 8);
        ySpeed = (short) ((y - oldY) << 8);
        // sub_20CE9A (:20-46): height 8, then $C; any contact lifts the player onto the top.
        yRadius = 8;
        boolean contact = solidObject(ReevaluatingSolidObject.Entry.SOLID);
        if (!contact) {
            yRadius = 0xC;
            contact = solidObject(ReevaluatingSolidObject.Entry.SOLID);
        }
        if (contact) {
            yRadius = 8;
            if (player instanceof AbstractPlayableSprite sprite && (y & 0xFFFF) >= (sprite.getCentreY() & 0xFFFF)) {
                // y - height - player height + 2 (:31-42)
                NativePositionOps.writeYPosPreserveSubpixel(sprite, y - yRadius - sprite.getYRadius() + 2);
            }
        }
        yRadius = 8;
    }

    @Override public int solidXVelocity() { return xSpeed; }
    @Override public int solidYVelocity() { return ySpeed; }

    @Override public int getCollisionFlags() { return initialised ? COLLISION : 0; }
    @Override public int getCollisionProperty() { return 0; }
}
