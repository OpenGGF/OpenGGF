package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Large break wall, object $2B ({@code LargeBreakWallObject}, objects/r5/large_break_wall.asm:3-10): a
 * solid wall 32 pixels wide and 96 tall (xRadius/oWidth {@code $10}, yRadius {@code $30}, priority 4, tile
 * {@code $446B}, collision {@code $EE}). A rolling player that touches it breaks it
 * ({@link QqzBreakable}): the player's X and ground speed are halved ({@code asr}, :32-36), SolidObject runs
 * once more (and GetOffObject on a top or bottom contact, :38-44), sound {@code $B0} plays, collision is
 * cleared and the wall becomes eight pieces ({@code unk_20EBA2}, {@code word_20EBAA}, {@code dword_20EBCA}
 * :116-142); the first piece is the wall and, like each copy that gets a slot, moves the same frame.
 * If a slot is missing the remaining pieces are not made. CheckObjectDespawn runs after every routine
 * (:9), pieces included. $2B is {@code ObjNull} in the Act 3 index, so the wall deletes itself there.
 * Shipped {@code FixBugs = 0}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjLargeBreakWall extends QqzBreakable {
    /** unk_20EBA2 (objects/r5/large_break_wall.asm:116-124). */
    private static final int[] FRAMES = {2, 5, 3, 5, 4, 5, 7, 5};
    /** word_20EBAA (objects/r5/large_break_wall.asm:126-134): X and Y offsets per piece. */
    private static final int[][] OFFSETS = {{-8, -0x18}, {0x10, 0}, {0, 0x10}, {0x10, 0x10},
            {0, 0x20}, {0x10, 0x20}, {0, 0x30}, {0x10, 0x30}};
    /** dword_20EBCA (objects/r5/large_break_wall.asm:136-144): X and Y velocity per piece, 16.16. */
    private static final int[][] VELOCITIES = {{-0x12D2D, -0x20000}, {0x12D2D, -0x20000}, {-0x28000, -0x18000},
            {0x28000, -0x18000}, {-0x50000, 0x8000}, {0x50000, 0x8000}, {-0x28000, 0x10000}, {0x28000, 0x10000}};

    public ObjLargeBreakWall(ObjectSpawn spawn) {
        super(spawn, "Large break wall");
    }

    private ObjLargeBreakWall(ObjectSpawn spawn, int tile, int frame, int flip, int x, int y, int xv, int yv) {
        super(spawn, "Large break wall piece");
        priority = 4;
        width = 0x10;
        xRadius = 0x10;
        yRadius = 0xC;
        initPiece(tile, frame, flip, x, y, xv, yv);
    }

    @Override protected String sheetKind() { return QqzObjectArt.LARGE_BREAK_WALL; }

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

    /** LargeBreakWallObject_0_Routine0 (objects/r5/large_break_wall.asm:20-29). */
    private void init() {
        routine += 2;
        priority = 4;
        width = 0x10;
        xRadius = 0x10;
        yRadius = 0x30;
        colType = 0xEE;
        tileWord = QqzObjectArt.LARGE_BREAK_WALL_TILE;
        initialised = true;
    }

    /** LargeBreakWallObject_0_Routine2 (objects/r5/large_break_wall.asm:31-50). */
    private void wall(PlayableEntity player) {
        if (!wasHit()) {
            solidObject(Entry.SOLID);                         // loc_20EA6A: jmp SolidObject
            return;
        }
        clearHit();
        if (player != null) {
            player.setXSpeed((short) (player.getXSpeed() >> 1));      // asr obj.x_speed(a1)
            player.setGSpeed((short) (player.getGSpeed() >> 1));      // asr obj.ground_speed(a1)
        }
        solidThenGetOff(player);
        breakApart(player);
    }

    /** loc_20EA74 to loc_20EAD0 (objects/r5/large_break_wall.asm:53-92). */
    private void breakApart(PlayableEntity player) {
        services().playSfx(0xB0);
        routine += 2;
        colType = 0;
        xRadius = 0x10;
        width = 0x10;
        yRadius = 0xC;
        x = (x + OFFSETS[0][0]) & 0xFFFF;
        y = (y + OFFSETS[0][1]) & 0xFFFF;
        pieceXVelocity = VELOCITIES[0][0];
        pieceYVelocity = VELOCITIES[0][1];
        mapFrame = FRAMES[0];
        var objects = services().objectManager();
        for (int i = 1; i < 8 && objects != null; i++) {
            // jsr SpawnObject (FindObjSlot); bne.s loc_20EA98's exit: Routine4 for this piece.
            ObjectSpawn copy = new ObjectSpawn(x, y, 0x2B, spawn.subtype(), spriteFlip, false, 0);
            ObjLargeBreakWall piece = new ObjLargeBreakWall(copy, tileWord, FRAMES[i], spriteFlip,
                    (x + OFFSETS[i][0]) & 0xFFFF, (y + OFFSETS[i][1]) & 0xFFFF,
                    VELOCITIES[i][0], VELOCITIES[i][1]);
            objects.addDynamicObject(piece);
            if (piece.isDestroyed()) break;
        }
        pieceStep(player);                                    // falls into LargeBreakWallObject_0_Routine4
    }
}
