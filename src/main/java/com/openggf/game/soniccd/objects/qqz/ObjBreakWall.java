package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Break wall, object $32 ({@code BreakWallObject}, objects/r5/break_wall.asm:3-8): a solid wall 16 wide and
 * 48 tall (xRadius/oWidth 8, yRadius {@code $18}, priority 1, tile {@code $44A7}, collision {@code $FC}).
 * The mapping frame is {@code subtype & 1}; subtype 2 is mirrored (:18-34). A rolling player that touches it
 * ({@link QqzBreakable}) clears its collision, runs SolidObject (and GetOffObject on a top or bottom
 * contact, :36-52), plays sound {@code $B0}, halves the player's X speed and turns the wall into three
 * pieces, frames {@code byte_20F8C2} (2, 3, 4 or, for an odd subtype, 5, 5, 5), thrown left, or right when
 * the player's speed is negative ({@code dword_20F8C8}, :129-153). CheckObjectDespawn runs only while the
 * wall stands (:53-57). Shipped {@code FixBugs = 0}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjBreakWall extends QqzBreakable {
    /** byte_20F8BC read as words (objects/r5/break_wall.asm:133-139): Y offsets $FFF0, 0, $10. */
    private static final int[] Y_OFFSETS = {-0x10, 0, 0x10};
    /** byte_20F8C2 (objects/r5/break_wall.asm:141-147). */
    private static final int[] FRAMES_EVEN = {2, 3, 4};
    private static final int[] FRAMES_ODD = {5, 5, 5};
    /** dword_20F8C8 (objects/r5/break_wall.asm:149-154): X velocity then Y velocity per piece, 16.16. */
    private static final int[][] VELOCITIES = {{-0x2F684, -0x148B0}, {-0x3DA12, 0}, {-0x2F684, 0x148B0}};

    public ObjBreakWall(ObjectSpawn spawn) {
        super(spawn, "Break wall");
    }

    private ObjBreakWall(ObjectSpawn spawn, int tile, int frame, int flip, int x, int y, int xv, int yv) {
        super(spawn, "Break wall piece");
        priority = 1;
        width = 8;
        xRadius = 8;
        yRadius = 8;
        initPiece(tile, frame, flip, x, y, xv, yv);
    }

    @Override protected String sheetKind() { return QqzObjectArt.BREAK_WALL; }

    @Override protected boolean despawnsByCheckObjDespawn() { return routine <= 2; }

    @Override
    protected void run(PlayableEntity player) {
        if (standDownInBossAct()) return;
        switch (routine) {
            case 0 -> { init(); wall(player); }              // Routine0 falls into Routine2
            case 2 -> wall(player);
            case 4 -> pieceStep(player);
            default -> { }
        }
    }

    /** BreakWallObject_0_Routine0 (objects/r5/break_wall.asm:18-34). */
    private void init() {
        routine += 2;
        priority = 1;
        width = 8;
        xRadius = 8;
        yRadius = 0x18;
        colType = 0xFC;
        tileWord = QqzObjectArt.BREAK_WALL_TILE;
        mapFrame = spawn.subtype() & 1;
        if (spawn.subtype() == 2) {
            spriteFlip |= 1;                                  // bset #0,obj.sprite_flags(a0)
            statusFlip |= 1;                                  // bset #0,obj.flags(a0)
        }
        initialised = true;
    }

    /** BreakWallObject_0_Routine2 (objects/r5/break_wall.asm:36-59). */
    private void wall(PlayableEntity player) {
        if (!wasHit()) {
            solidObject(Entry.SOLID);                         // loc_20F794
            return;
        }
        colType = 0;                                          // clr.w obj.collide_type(a0): type and status
        clearHit();
        routine += 2;
        solidThenGetOff(player);
        breakApart(player);
    }

    /** loc_20F7AA to loc_20F83A (objects/r5/break_wall.asm:61-107). */
    private void breakApart(PlayableEntity player) {
        services().playSfx(0xB0);
        int[] frames = (spawn.subtype() & 1) == 0 ? FRAMES_EVEN : FRAMES_ODD;   // btst #0,obj.subtype(a0)
        if (player != null) player.setXSpeed((short) (player.getXSpeed() >> 1)); // asr obj.x_speed(a3)
        boolean toRight = player != null && player.getXSpeed() < 0;             // tst.w obj.x_speed(a3); bpl.s
        xRadius = 8;
        width = 8;
        yRadius = 8;
        mapFrame = frames[0];
        y = (y + Y_OFFSETS[0]) & 0xFFFF;
        pieceXVelocity = toRight ? -VELOCITIES[0][0] : VELOCITIES[0][0];
        pieceYVelocity = VELOCITIES[0][1];
        var objects = services().objectManager();
        for (int i = 1; i < 3 && objects != null; i++) {
            ObjectSpawn copy = new ObjectSpawn(x, y, 0x32, spawn.subtype(), spriteFlip, false, 0);
            ObjBreakWall piece = new ObjBreakWall(copy, tileWord, frames[i], spriteFlip, x,
                    (y + Y_OFFSETS[i]) & 0xFFFF,
                    toRight ? -VELOCITIES[i][0] : VELOCITIES[i][0], VELOCITIES[i][1]);
            objects.addDynamicObject(piece);
            if (piece.isDestroyed()) break;
        }
        pieceStep(player);                                    // falls into BreakWallObject_0_Routine4
    }
}
