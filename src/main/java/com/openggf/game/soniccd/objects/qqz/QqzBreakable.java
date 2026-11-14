package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.player.CdSonicAnimation;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseResult;

/**
 * What the two Quartz Quadrant walls share. A wall is solid and carries special collision
 * ({@code $C0 | $2E} or {@code $C0 | $3C}); a rolling player that touches it adds one to
 * {@code oColStatus} ({@code loc_20726E}, src/r5/player_object_collide.asm:loc_207186 to loc_20726E,
 * 281-284: {@code cmpi.b #2,obj.anim_id(a0)}). The wall then breaks into flying pieces, the first of which
 * is the wall object itself, and the pieces fall under {@code $4000} gravity (16.16) until the player is
 * {@code $E0} or more above them.
 *
 * <p>Pieces are copies of the wall (objects/r5/large_break_wall.asm:loc_20EA98, 67-77) with routine 4; their
 * positions accumulate: a copy takes the wall's position after the first piece's offset, then adds its own.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
abstract class QqzBreakable extends QqzObject implements ReevaluatingSolidObject, TouchResponseListener {
    /** Set by the touch response: {@code oColStatus} of the wall (cleared with the hit). */
    private boolean hit;
    /** oVar2A and oVar2E: the piece's velocity as 16.16 values. */
    protected int pieceXVelocity;
    protected int pieceYVelocity;

    protected QqzBreakable(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    @Override public int solidWidth() { return width; }
    @Override public int solidYRadius() { return yRadius; }
    @Override public boolean solidRenderedOnScreen() { return renderFlagOnScreen(); }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        // loc_20726E: cmpi.b #2,obj.anim_id(a0); bne; addq.b #1,obj.collide_status(a1).
        if (colType != 0 && player.getAnimationId() == CdSonicAnimation.ROLL.id()) hit = true;
    }

    /** {@code tst.b obj.collide_status(a0)}. */
    protected final boolean wasHit() {
        return hit;
    }

    protected final void clearHit() {
        hit = false;
    }

    /** {@code jsr SolidObject; beq.s; jsr GetOffObject}: a top or bottom contact releases the player. */
    protected final void solidThenGetOff(PlayableEntity player) {
        if (solidObject(Entry.SOLID) && player != null) {
            ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), player, this);
        }
    }

    /**
     * Routine 4 of both walls (objects/r5/large_break_wall.asm:92-108; objects/r5/break_wall.asm:109-125): gravity, move by the
     * piece velocity, delete once {@code player.y - y <= -$E0} (signed, {@code ble.s}).
     */
    protected final void pieceStep(PlayableEntity player) {
        pieceYVelocity += 0x4000;
        addToX(pieceXVelocity);
        addToY(pieceYVelocity);
        PlayableEntity focus = player != null ? player : services().camera().getFocusedSprite();
        if (focus == null) return;
        int d0 = (short) (centreY(focus) - y);
        if (d0 <= -0xE0) {
            deleteObject();
            drawThisFrame = false;
        }
    }

    /** The piece-only state of a copy made by the wall. */
    protected final void initPiece(int tile, int frame, int flip, int x, int y, int xVelocity, int yVelocity) {
        this.routine = 4;
        this.tileWord = tile;
        this.mapFrame = frame;
        this.statusFlip = flip;
        this.spriteFlip = flip;
        this.x = x;
        this.y = y;
        this.pieceXVelocity = xVelocity;
        this.pieceYVelocity = yVelocity;
        this.initialised = true;
    }
}
