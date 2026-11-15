package com.openggf.game.soniccd.objects.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

/**
 * Piston, object $20 ({@code ObjPiston}, Level/Wacky Workbench/Objects/Piston/Main.asm:16-149): a
 * top-solid $20-wide, $28-tall block that rises eight pixels a frame to 80, waits 60 frames,
 * sinks one pixel a frame to 0 and waits 60 frames. While raised above $20 it is not solid and pushes
 * the player off (:74-86). Init allocates two more objects with the same ID and subtype -1 at
 * 32 left and right: full-solid, zero-width, invisible (frame 1 has no pieces) sides that follow its
 * Y and delete themselves when it is gone (:138-149).
 */
public final class ObjPiston extends WwzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private static final int INIT = 0, MAIN = 2;
    /** {@code move.b #80,oPistonOff(a0)} (:114). */
    private static final int MAX_OFFSET = 80;
    /** {@code move.b #60,oPistonTime(a0)} (:105, :116). */
    private static final int WAIT = 60;
    /** {@code cmpi.b #$21,oPistonOff(a0)} (:80). */
    private static final int SOLID_BELOW = 0x21;

    private final boolean side;
    /** A side's {@code obj.var_34}: the column's slot ({@code move.w a0,obj.var_34(a1)}, objects/r6/piston.asm:106). */
    private final int parentSlot;
    private int pistonX;
    private int pistonY;
    private int offset;
    private int time;
    private int dir;

    public ObjPiston(ObjectSpawn spawn) {
        super(spawn, "Piston");
        this.side = false;
        this.parentSlot = -1;
    }

    private ObjPiston(ObjectSpawn spawn, ObjPiston parent) {
        super(spawn, "PistonSide");
        this.side = true;
        this.parentSlot = parent.getSlotIndex();
        spriteFlip = 0;
        yRadius = 40;                                         // ObjPiston_SetupSolidSide (:123-134)
        mapFrame = 1;
        priority = 0;
        width = 0;                                            // oWidth is never written for the side
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        var sprite = sprite(player);
        if (side) {
            solidSide(sprite);
            return;
        }
        switch (routine) {
            case INIT -> {
                init();
                main(sprite);
            }
            default -> main(sprite);
        }
    }

    /** ObjPiston_Init (:42-61): the two sides, left first, each from FindObjSlot. */
    private void init() {
        routine = MAIN;
        priority = 0;
        tileWord = WwzObjectArt.PISTON_TILE;
        yRadius = 40;
        width = 32;
        pistonX = x;
        pistonY = y;
        var objects = services().objectManager();
        if (objects == null) return;
        objects.addDynamicObject(new ObjPiston(new ObjectSpawn((x - 32) & 0xFFFF, y, 0x20, 0xFF, 0, false, 0), this));
        objects.addDynamicObject(new ObjPiston(new ObjectSpawn((x + 32) & 0xFFFF, y, 0x20, 0xFF, 0, false, 0), this));
    }

    /** ObjPiston_Main (:65-86). */
    private void main(com.openggf.sprites.playable.AbstractPlayableSprite player) {
        move();
        y = (pistonY - offset) & 0xFFFF;
        if (dir != 0 && time == 0) {
            topSolid(player);
        } else if (offset < SOLID_BELOW) {
            topSolid(player);
        } else if (player != null) {
            ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), player, this);
        }
    }

    private void topSolid(com.openggf.sprites.playable.AbstractPlayableSprite player) {
        if (player != null) solidObject(Entry.TOP);
    }

    /** ObjPiston_Move (:90-119). */
    private void move() {
        if (time != 0) {
            time = (time - 1) & 0xFF;
            if (time != 0) return;
        }
        if (dir == 0) {
            offset = (offset + 8) & 0xFF;
            if (offset < MAX_OFFSET) return;
            offset = MAX_OFFSET;
            dir = 1;
            time = WAIT;
        } else {
            boolean borrow = offset == 0;
            offset = (offset - 1) & 0xFF;
            if (!borrow) return;
            offset = 0;
            dir = 0;
            time = WAIT;
        }
    }

    /**
     * ObjPiston_SolidSide (:138-149; PistonChildObject, objects/r6/piston.asm:118-122): the side reads the ID in its
     * column's slot; once the column has despawned (CheckObjDespawn) or been deleted the side deletes itself, in the
     * same pass when its slot is the later one, and an object with ID $20 that has taken the slot since is followed.
     */
    private void solidSide(com.openggf.sprites.playable.AbstractPlayableSprite player) {
        if (!slotHoldsId(parentSlot, 0x20)) {
            setDestroyed(true);                               // cmpi.b #$20,oID(a1) / bne.s .Delete
            return;
        }
        if (slotOccupant(parentSlot) instanceof ObjPiston column) y = column.y;   // move.w oY(a1),oY(a0)
        if (player != null) solidObject(Entry.SOLID);
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return !side; }

    @Override
    protected String sheetKey() {
        return WwzObjectArt.key(WwzObjectArt.PISTON);
    }

    public int offset() { return offset; }
}
