package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Stalactite, object $25 ({@code StalactiteObject}, objects/r5/stalactite.asm:3-9): a hanging spike that
 * falls when the player passes under it. Routine 0 sets size 8 by {@code $18}, priority 3 and the tile by
 * time zone: {@code $342}, or {@code $4342} (palette line 2) in the past and in a bad future (:20-40).
 * Routine 2 triggers when the player's Y is {@code [$18, $80)} below it and X within {@code [-8, 8)}
 * (sub_20DE9A, :54-66): collision becomes {@code $B9} (hurt). Routine 4 falls with {@code $4000} gravity
 * (16.16, :96-101) and is deleted a frame after touching the floor, or when off screen and not above the
 * player (:70-92). CheckObjectDespawn runs after every routine (:9). Shipped {@code FixBugs = 0}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjStalactite extends QqzObject {
    private int fallVelocity;      // oVar2A, 16.16
    private boolean hitFloor;      // oVar3F bit 7

    public ObjStalactite(ObjectSpawn spawn) {
        super(spawn, "Stalactite");
    }

    @Override protected String sheetKind() { return QqzObjectArt.STALACTITE; }

    @Override
    protected void run(PlayableEntity player) {
        if (standDownInBossAct()) return;
        switch (routine) {
            case 0 -> { init(); waiting(player); }           // Routine0 falls into Routine2
            case 2 -> waiting(player);
            case 4 -> falling(player);
            default -> { }
        }
    }

    /** StalactiteObject_0_Routine0 (objects/r5/stalactite.asm:20-40). */
    private void init() {
        routine += 2;
        priority = 3;
        xRadius = 8;
        width = 8;
        yRadius = 0x18;
        int tz = liveTimeZone();
        boolean plain = tz == 1 || (tz != 0 && liveGoodFuture());
        tileWord = plain ? QqzObjectArt.STALACTITE_TILE : QqzObjectArt.STALACTITE_TILE_PAST;
        initialised = true;
    }

    /** StalactiteObject_0_Routine2 (objects/r5/stalactite.asm:42-66). */
    private void waiting(PlayableEntity player) {
        if (player == null) return;
        if (!inWindow(centreY(player) - y - 0x18, 0, 0x68)) return;
        if (!inWindow(centreX(player) - x, 8, 0x10)) return;
        routine += 2;
        colType = 0xB9;
    }

    /** StalactiteObject_0_Routine4 (objects/r5/stalactite.asm:70-101). */
    private void falling(PlayableEntity player) {
        if (!onScreen() && player != null && (short) y >= (short) centreY(player)) {
            deleteObject();                                  // cmp.w; blt.s loc_20DED4; jmp DeleteObject
            drawThisFrame = false;
            return;
        }
        int d1 = floorDistance();                            // CheckBlockDown3 = ObjGetFloorDist
        boolean below = d1 < 0;                              // tst.w d1; smi d0
        if (!below && hitFloor) {                            // tst.b obj.var_3f(a0); bpl.s; jmp DeleteObject
            deleteObject();
            drawThisFrame = false;
            return;
        }
        hitFloor = below;                                    // loc_20DEFC: move.b d0,obj.var_3f(a0)
        addToY(fallVelocity);
        fallVelocity += 0x4000;
    }
}
