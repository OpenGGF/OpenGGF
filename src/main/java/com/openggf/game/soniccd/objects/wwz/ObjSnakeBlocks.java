package com.openggf.game.soniccd.objects.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Snake blocks, object $2A ({@code ObjSnakeBlocks}, Level/Wacky Workbench/Objects/Snake Blocks/Main.asm:15-314).
 * The placed object (subtype 0 to 4, the path set) is the first block: it picks the next path of its set
 * (four per set, cycling) and spawns a moving copy of itself, which slides $20 pixels (64 frames at 0.5
 * pixel a frame) in the path's first direction (0 up, 1 right, 2 down, 3 left), waits, and spawns the next copy
 * with the path advanced by one byte, until the path's $FF; the last copy then slides back, each earlier
 * copy follows when its successor finishes, and every copy deletes itself after its return. All blocks
 * are full-solid, $20 square, without a range check; the controller keeps the range check and
 * re-arms the cycle ({@code oSnakeSpawn}) when the last copy has been spawned.
 *
 * <p>The path sets are in the stage file (Paths: a word per subtype, each a word per path, each the path's direction
 * bytes); a copy takes the spawner's first {@code oSnakePath} ($2C) bytes - position and subpixels, flags,
 * tile, map, radii, subtypes - changes the subtype to -1, raises the priority by one and starts at routine 0.
 * A subtype-2 set also gives the copy {@code oYVel = +-1} as a rider's vertical reference (:153-164).
 */
public final class ObjSnakeBlocks extends WwzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private static final int MAIN = 2;
    private static final int[][] DIRECTIONS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    /** {@code move.l #$8000,...} (:135, :140, :145, :150). */
    private static final int STEP = 0x8000;

    private final boolean controllerBlock;
    /** oSnakeParent's word: the placed block's slot ({@code move.w a0,oSnakeParent(a0)}, Main.asm:47), copied to each copy. */
    private int controllerSlot = -1;
    /** oSnakePrev: the slot of the block that spawned this copy ({@code move.w a0,oSnakePrev(a1)}, Main.asm:308). */
    private int prevSlot = -1;
    private int path;                           // oSnakePath: a stage-file offset
    private int pathIndex;                      // oSnakeIndex
    private boolean spawnPending;               // oSnakeSpawn
    private int time;
    private int xVel;
    private int yVelStep;                       // the long oSnakeYVel (oVar3C)
    private int xStep;                          // the long oSnakeXVel (oVar38)
    private int solidYVel;                      // oSnakeSolidYVel and the word oYVel
    private int objectYVel;
    private int copiedSubtype2;                // oSubtype2, copied from the spawner
    private boolean initialised;

    public ObjSnakeBlocks(ObjectSpawn spawn) {
        super(spawn, "SnakeBlocks");
        this.controllerBlock = true;
    }

    /** ObjSnakeBlocks_Spawn's copy (:288-314). */
    private ObjSnakeBlocks(ObjSnakeBlocks spawner, int nextPath) {
        super(new ObjectSpawn(spawner.x, spawner.y, 0x2A, 0xFF, spawner.getSpawn().renderFlags(), false,
                0), "SnakeBlock");
        this.controllerBlock = false;
        this.controllerSlot = spawner.controllerSlot;
        this.prevSlot = spawner.getSlotIndex();
        this.path = nextPath;
        this.xSub = spawner.xSub;
        this.ySub = spawner.ySub;
        this.spriteFlip = spawner.spriteFlip;
        this.priority = spawner.priority + 1;
        this.tileWord = spawner.tileWord;
        this.width = spawner.width;
        this.yRadius = spawner.yRadius;
        this.copiedSubtype2 = spawner.blockSubtype2();
        copyDrawState(spawner);                                      // the copied bytes include oSprFlags
        this.initialised = true;
        this.routine = 0;
    }

    /** {@code oSubtype2}: the layout's for the placed block, the copied one for the others. */
    private int blockSubtype2() {
        return controllerBlock ? subtype2() : copiedSubtype2;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AbstractPlayableSprite sprite = sprite(player);
        if (controllerBlock) {
            controller(sprite);
        } else {
            block(sprite);
        }
    }

    // ---- The placed block (:15-78) -----------------------------------------------------------

    private void controller(AbstractPlayableSprite player) {
        if (routine == 0) {
            routine = MAIN;
            priority = 1;
            width = 16;
            yRadius = 16;
            tileWord = WwzObjectArt.SNAKE_BLOCKS_TILE;
            spawnPending = true;                                     // st oSnakeSpawn(a0)
            controllerSlot = getSlotIndex();                         // move.w a0,oSnakeParent(a0)
            pathIndex = 0;
            initialised = true;
        }
        if (spawnPending) {                                          // ObjSnakeMain_Main (:53-75)
            spawnPending = false;
            var provider = artProvider();
            if (provider != null) {
                var image = provider.image();
                int paths = WwzObjectCatalogue.offset(provider.payload(), "snakePaths");
                int set = paths + (short) image.u16(paths + getSpawn().subtype() * 2);
                path = set + (short) image.u16(set + pathIndex);
                pathIndex += 2;
                if (pathIndex >= 8) pathIndex = 0;
                if (!spawnCopy(this)) {
                    setDestroyed(true);                              // bsr Spawn / beq.s .End / jmp DeleteObject
                    return;
                }
            }
        }
        if (player != null) solidObject(Entry.SOLID);
    }

    /** ObjSnakeBlocks_Spawn (:288-314): true when a copy was allocated. */
    private boolean spawnCopy(ObjSnakeBlocks spawner) {
        var provider = artProvider();
        var objects = services().objectManager();
        if (provider == null || objects == null) return false;
        if ((provider.image().u8(spawner.path) & 0x80) != 0) return false;   // tst.b (a6)+ / bmi.s .End
        objects.addDynamicObject(new ObjSnakeBlocks(spawner, spawner.path + 1));
        return true;
    }

    // ---- A moving copy (:93-285) -------------------------------------------------------------

    private void block(AbstractPlayableSprite player) {
        ObjSnakeBlocks controller = controller();
        if (controller == null || blockSubtype2() != controller.blockSubtype2()) {   // cmpi.b #$2A,oID(a1) / cmp.b oSubtype2
            setDestroyed(true);
            return;
        }
        switch (routine) {
            case 0 -> { init(controller); move(); }
            case 2 -> move();
            // ObjSnakeSub_StartWait, _StartMoveBack and _StartWait2 have no rts: each falls into the routine after it
            // (:183-212, :218-237, :241-271), so the same frame runs both.
            case 4 -> { startWait(controller); waitThenSpawn(controller); }
            case 6 -> waitThenSpawn(controller);
            case 8 -> { }
            case 0xA -> { startMoveBack(); moveBack(); }
            case 0xC -> moveBack();
            case 0xE -> { startWait(controller); waitThenReleaseBlock(); }
            case 0x10 -> waitThenReleaseBlock();
            case 0x12 -> {
                if (player != null && solidObject(Entry.SOLID)) {
                    ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), player, this);
                }
                setDestroyed(true);                                  // addq.l #4,sp / DeleteObject
                return;
            }
            default -> { }
        }
        if (player != null) solidObject(Entry.SOLID);
    }

    /** ObjSnakeSub_Init (:126-164). */
    private void init(ObjSnakeBlocks controller) {
        routine = 2;
        time = 63;
        xStep = 0;
        yVelStep = 0;
        int direction = provider().image().u8(path - 1);
        if (direction <= 3) {
            xStep = DIRECTIONS[direction][0] * STEP;
            yVelStep = DIRECTIONS[direction][1] * STEP;
        }
        if (controller.getSpawn().subtype() == 2) {
            solidYVel = (yVelStep >> 16) >= 0 ? 1 : -1;             // tst.w oSnakeYVel(a0) (the high word)
            objectYVel = solidYVel;
        }
    }

    /** ObjSnakeSub_Move (:168-179). */
    private void move() {
        addToX(xStep);
        addToY(yVelStep);
        time = (time - 1) & 0xFFFF;
        if ((time & 0x8000) != 0) routine += 2;
    }

    /** ObjSnakeSub_StartWait / StartWait2 (:183-193, :241-251). */
    private void startWait(ObjSnakeBlocks controller) {
        routine += 2;
        objectYVel = 0;
        time = controller.getSpawn().subtype() == 2 ? 0 : 30;
    }

    /** ObjSnakeSub_Wait (:197-209): spawn the next copy when the time is out, else go on to the return. */
    private void waitThenSpawn(ObjSnakeBlocks controller) {
        time = (time - 1) & 0xFFFF;
        if ((time & 0x8000) == 0) return;
        routine += 2;
        if (spawnCopy(this)) return;                                 // bsr Spawn / beq.s .End
        routine += 2;
        controller.spawnPending = true;                              // st oSnakeSpawn(a1)
    }

    /** ObjSnakeSub_StartMoveBack (:218-222). */
    private void startMoveBack() {
        routine += 2;
        time = 63;
        objectYVel = -solidYVel;
    }

    /** ObjSnakeSub_MoveBack (:226-237). */
    private void moveBack() {
        setXPosition(xPosition() - xStep);
        setYPosition(yPosition() - yVelStep);
        time = (time - 1) & 0xFFFF;
        if ((time & 0x8000) != 0) routine += 2;
    }

    /** ObjSnakeSub_Wait2 (:255-268): the previous copy is released when this one is back. */
    private void waitThenReleaseBlock() {
        time = (time - 1) & 0xFFFF;
        if ((time & 0x8000) == 0) return;
        ObjSnakeBlocks before = previous();
        if (before != null && !before.controllerBlock) before.routine += 2;   // tst.b oSubtype(a1) / bpl.s
        routine += 2;
    }

    private com.openggf.game.soniccd.objects.art.CdObjectArtProvider provider() {
        return artProvider();
    }

    /**
     * {@code movea.w oSnakeParent(a0),a1; cmpi.b #$2A,oID(a1)} (Main.asm:94-96): the object in the placed block's slot,
     * while that slot holds ID $2A; once the placed block has despawned or been deleted the copies delete themselves.
     */
    private ObjSnakeBlocks controller() {
        if (controllerBlock) return this;
        if (!slotHoldsId(controllerSlot, 0x2A)) return null;
        return slotOccupant(controllerSlot) instanceof ObjSnakeBlocks block ? block : null;
    }

    /**
     * {@code movea.w oSnakePrev(a0),a1} (Main.asm:259): whatever is in the slot of the block that spawned this one,
     * without an ID test; only a snake block there can take the routine step that follows.
     */
    private ObjSnakeBlocks previous() {
        return slotOccupant(prevSlot) instanceof ObjSnakeBlocks block ? block : null;
    }

    @Override public int solidYVelocity() { return controllerBlock ? 0 : objectYVel; }

    @Override protected boolean despawnsByCheckObjDespawn() { return controllerBlock; }

    @Override
    protected String sheetKey() {
        return initialised ? WwzObjectArt.key(WwzObjectArt.SNAKE_BLOCKS) : null;
    }

    public int pathOffset() { return path; }
}
