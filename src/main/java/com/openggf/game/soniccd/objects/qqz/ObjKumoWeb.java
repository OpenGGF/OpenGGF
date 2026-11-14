package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Kumo Kumo's web, object $23 with subtype {@code $FF} (loc_20DA4C, objects/r5/kumo_kumo.asm:187-234):
 * collision {@code $F7} (special {@code $37}: any touch adds to {@code oColStatus}, src/r5/
 * r5/player_object_collide.asm:loc_207186, 252-255), size {@code $C}/{@code 8}, priority 1, flying at
 * {@code $30000} 16.16 away from the spider. A touch while the player is neither hurt nor dead nor
 * invulnerable catches the player for 120 frames: the web takes the player's position and clears the
 * player's X and ground speed every frame (:196-229), then deletes itself. Otherwise it animates
 * ({@code KumoWebAnims}) and runs CheckObjectDespawn. The slot's {@code oSprFlags} are the spider's
 * (flip and the on-screen bit), kept here as the flip only.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjKumoWeb extends QqzObject implements TouchResponseListener {
    private final int velocity;
    private boolean touched;
    private int caughtTimer;       // oVar2A
    private PlayableEntity caught; // oVar2C

    ObjKumoWeb(int x, int y, int flip, int parentTileWord, int velocity) {
        super(new ObjectSpawn(x, y, 0x23, 0xFF, flip, false, 0), "Kumo Kumo web");
        this.velocity = velocity;
        this.priority = 1;
        this.tileWord = parentTileWord;                       // move.w obj.sprite_tile(a0),obj.sprite_tile(a1)
        this.xRadius = 0xC;
        this.width = 0xC;
        this.yRadius = 8;
        this.colType = 0xF7;
        this.animColumn = "kumoWebAnimations";
        this.initialised = true;
    }

    @Override protected String sheetKind() { return QqzObjectArt.KUMO_WEB; }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        touched = true;                                       // loc_207226: addq.b #1,obj.collide_status(a1)
    }

    @Override
    protected void run(PlayableEntity player) {
        if (caughtTimer == 0) {
            if (touched) {
                touched = false;                              // clr.b obj.collide_status(a0)
                if (player != null && !hurtOrDeadOrInvulnerable(player)) {
                    caught = player;                          // loc_20DA82
                    caughtTimer = 0x78;
                } else {
                    flyOn();                                  // loc_20DA78
                    return;
                }
            } else {
                flyOn();
                return;
            }
        }
        if (--caughtTimer == 0) {                             // loc_20DA8C
            deleteObject();
            drawThisFrame = false;
            return;
        }
        x = caught.getCentreX() & 0xFFFF;                     // loc_20DA98
        y = caught.getCentreY() & 0xFFFF;
        caught.setXSpeed((short) 0);
        caught.setGSpeed((short) 0);
        animate();
    }

    /** loc_20DA78: {@code add.l d0,obj.x(a0)}, then the shared animate and draw. */
    private void flyOn() {
        addToX(velocity);
        animate();
    }

    /** Routine 4 (hurt) or 6 (dead), or a non-zero {@code oPlayerHurt} (objects/r5/kumo_kumo.asm:196-205). */
    private static boolean hurtOrDeadOrInvulnerable(PlayableEntity player) {
        if (player.getDead()) return true;
        return player instanceof AbstractPlayableSprite sprite
                && (sprite.isHurt() || sprite.getInvulnerableFrames() != 0);
    }
}
