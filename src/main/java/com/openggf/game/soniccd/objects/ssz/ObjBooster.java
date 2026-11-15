package com.openggf.game.soniccd.objects.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Booster, object $08 in acts 1 and 2 ({@code BoosterObject}, objects/r7/launch.asm:930-1080; object IDs are
 * the {@code ObjectIndex} slot plus one, as in the trace's {@code object_type}): a flat pad that throws a grounded
 * player sideways. Routine 2 waits for a grounded, living player within ({@code $20}, {@code $10}) of its centre
 * (sub_20C33A, :1021-1032, which jumps to the overlap test loc_20BFA4, :655-693); on a hit (loc_20C352, :1036-1080) it clears the player's {@code oVar2C}
 * control bits, spawns a copy of itself with subtype 1 (the falling piece, drifting {@code +-$200}) and the sparks
 * ($09, six pixels up and sixteen to the side the player is thrown from, flipped when the player is on the right),
 * plays {@code FM_CE}, sets the player's speed to {@code +-$1000} with the same ground speed and facing, and slides
 * itself {@code +-$800} for six frames and back (routine 4, :970-991).
 *
 * <p>The falling piece (subtype 1) shows frame 0, starts at {@code oYVel = -$800} in routine 6, which accelerates
 * by {@code $40} per frame and is deleted once it is {@code $E8} below the camera's top (:956-1000). The routine
 * ends in {@code CheckObjectDespawn} (:936) and draws every frame.
 *
 * <p>Not modelled: {@code debug_mode} (:1010) and the player's {@code oVar2C} byte (:1037), which the shared
 * player does not hold. When a spawn finds no free slot the matching writes are skipped (bounded Java admission);
 * the ROM would write through the failed search's pointer.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjBooster extends SszObject implements ObjectVisualFacing.NonDirectional {
    /** {@code move.w #$CE,d0; jsr PlayFmSound} (:1053-1054). */
    private static final int FM_CE = 0xCE;
    /** {@code d2}, {@code d3} of sub_20C33A (:1030-1031). */
    private static final int HALF_WIDTH = 0x20;
    private static final int HALF_HEIGHT = 0x10;
    /** Debris: {@code move.w #-$800,oYVel}, {@code addi.w #$40,oYVel}, {@code addi.w #$E8,d0} (:959, :994, :997). */
    private static final int DEBRIS_LAUNCH = -0x800;
    private static final int GRAVITY = 0x40;
    private static final int DEBRIS_BOTTOM = 0xE8;
    /** {@code move.b #6,obj.var_2a(a0)}; {@code move.w #$800,obj.x_speed(a0)} (:1057, :1066). */
    private static final int SLIDE_FRAMES = 6;
    private static final int SLIDE_SPEED = 0x800;
    /** The falling piece's drift: {@code move.w #$200,obj.x_speed(a3)} (:1063, :1075). */
    private static final int PIECE_DRIFT = 0x200;

    /** oVar2C: the rest X (:1056). */
    private int restX;
    /** oVar2A: the slide frames left (:1057). */
    private int slideFrames;

    public ObjBooster(ObjectSpawn spawn) {
        super(spawn, "Booster");
    }

    @Override protected String sheetKind() { return SszObjectArt.LAUNCH; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) init();
        switch (routine) {
            case 2 -> detect(player);
            case 4 -> slide();
            case 6 -> fall();
            default -> { }
        }
    }

    /** BoosterObject_0_Routine0 (:948-961). */
    private void init() {
        routine += 2;
        tileWord = SszObjectArt.LAUNCH_TILE;
        width = 0x20;                                        // move.b #$20,obj.width_2(a0)
        yRadius = 0xC;                                       // move.b #$C,obj.height(a0)
        mapFrame = 2;
        initialised = true;
        if (spawn.subtype() != 0) {
            mapFrame = 0;
            yVel = DEBRIS_LAUNCH;
            routine = 6;
        }
    }

    /** loc_20C326 (:1009-1017) and loc_20C352 (:1036-1080). */
    private void detect(PlayableEntity entity) {
        if (!(entity instanceof AbstractPlayableSprite player)) return;
        // sub_20C33A: btst #1,obj.flags(a2): an airborne player never triggers it (:1022-1025).
        if (player.getAir()) return;
        if (!SszPlayerOverlap.touches(player, x, y, HALF_WIDTH, HALF_HEIGHT)) return;
        boolean playerRight = x < (player.getCentreX() & 0xFFFF);    // cmp.w obj.x(a2),d0; bcc.s loc_20C3E6 when not
        var objects = services().objectManager();
        ObjBooster piece = null;
        if (objects != null) {
            // jsr SpawnObject; id 8 (this routine) with subtype 1: the falling piece (:1038-1043). Its oXVel is set below.
            piece = new ObjBooster(new ObjectSpawn(x, y, 8, 1, 0, false, 0));
            objects.addDynamicObject(piece);
            if (piece.isDestroyed()) piece = null;
            // jsr SpawnObject; id 9, the sparks, six pixels up (:1045-1050); its flip bit and X shift follow below.
            ObjSparks sparks = new ObjSparks(new ObjectSpawn(x + (playerRight ? 0x10 : -0x10), y - 6, 9, 0,
                    playerRight ? 1 : 0, false, 0));
            objects.addDynamicObject(sparks);
        }
        services().playSfx(FM_CE);                           // loc_20C396 (:1053-1054)
        routine += 2;
        restX = x;
        slideFrames = SLIDE_FRAMES;
        if (playerRight) {                                   // the player is thrown left (:1058-1068)
            player.setXSpeed((short) -0x1000);
            player.setGSpeed((short) -0x1000);
            player.setDirection(Direction.LEFT);             // bset #0,obj.flags(a2)
            if (piece != null) piece.xVel = PIECE_DRIFT;     // move.w #$200,obj.x_speed(a3)
            xVel = SLIDE_SPEED;
        } else {                                             // loc_20C3E6 (:1072-1080)
            player.setXSpeed((short) 0x1000);
            player.setGSpeed((short) 0x1000);
            player.setDirection(Direction.RIGHT);            // bclr #0,obj.flags(a2)
            if (piece != null) piece.xVel = -PIECE_DRIFT;    // move.w #-$200,$10(a3)
            xVel = -SLIDE_SPEED;
        }
    }

    /** BoosterObject_0_Routine4 (:970-991). */
    private void slide() {
        if (slideFrames != 0) {
            moveObject();
            if (--slideFrames == 0) xVel = -xVel;            // subq.b #1,obj.var_2a; bne; neg.w obj.x_speed
            return;
        }
        moveObject();                                        // loc_20C2EE (:983-989)
        if (x == restX) routine -= 2;
    }

    /** BoosterObject_0_Routine6 (:993-1000). */
    private void fall() {
        yVel = (short) (yVel + GRAVITY);
        moveObject();
        int limit = (cameraY() + DEBRIS_BOTTOM) & 0xFFFF;
        if (limit < y) setDestroyed(true);                   // cmp.w obj.y(a0),d0; bcc.s locret; jmp DeleteObject
    }

    public int slideFramesLeft() { return slideFrames; }
}
