package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.shared.SharedAttachedSpring;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * Collision Chaos' block, object $21 ({@code ObjBlock}, Level/Collision Chaos/Objects/Block/Main.asm:31-182 with every
 * {@code CC_LEGACY_BLOCK_ABI} branch taken: the USA build assembles the recovered layout). A solid $20 square (tile
 * {@code $433E}, priority 3). In the past it stands still; elsewhere a subtype other than 1 loops through four motion records
 * from the stage file (frame count, signed acceleration, 8.8 velocity): the legacy byte-sized record index, and the early
 * return after a record is loaded, mean the block moves from the frame after. A subtype of 2 or more carries a spring on top
 * (the next free slot, {@code SpawnObjectAfter}); the colour comes from subtype bit 0. The despawn is the object's own chunk
 * test on the base X with no on-screen exemption, then {@code DespawnObjectR3} (:36-50).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class CczBlock extends CczObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    static final int TILE = 0x433E;

    private int baseX;               // oVar32
    private int position;            // oVar36 (8.8 velocity accumulator, also the X speed)
    private int velocity;            // oVar38
    private int timer;               // oVar3A
    private int direction;           // oVar3B
    private int xVel;                // oXVel

    public CczBlock(ObjectSpawn spawn) {
        super(spawn, "Block");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            if (!init()) return;
        }
        solidObject(Entry.SOLID);                                     // ObjBlock_Solid (:186-190)
        if (timeZone() == 0) return;                                  // tst.b timeZone; beq.s .End (past: still)
        checkMotion();
    }

    /** ObjBlock_Init (:67-92); falls into ObjBlock_Update. */
    private boolean init() {
        routine = 2;
        tileWord = TILE;
        priority = 3;
        width = 0x10;
        yRadius = 0x10;
        baseX = x;
        if ((spawn.subtype() & 0xFF) < 2) return true;               // cmpi.b #2,oSubtype; bcs.s ObjBlock_Update
        var objects = services().objectManager();
        if (objects == null) return true;
        ObjectSpawn spring = new ObjectSpawn(x, y, 0x0A, ((spawn.subtype() & 1) << 1), 0, false, 0);
        SharedAttachedSpring child = new SharedAttachedSpring(spring, this, 0, (byte) 0xEA);   // oVar39 = $EA
        objects.addDynamicObjectAfterCurrent(child);                  // jsr SpawnObjectAfter (legacy)
        if (child.isDestroyed()) {                                    // bne: jmp DeleteObject
            setDestroyed(true);
            return false;
        }
        return true;
    }

    /** .CheckMotion and .Move (:109-171, legacy). */
    private void checkMotion() {
        if ((spawn.subtype() & 0xFF) == 1) return;                    // cmpi.b #1,oSubtype; beq.s .End
        while (true) {
            if (timer == 0) {
                var provider = artProvider();
                if (provider == null) return;
                int table = CczObjectCatalogue.offset(provider.payload(), "blockMotionData");
                int record = table + ((direction * 4) & 0xFF);       // add.b d0,d0 twice (legacy byte index)
                var image = provider.image();
                timer = image.u8(record);
                velocity = (byte) image.u8(record + 1);               // ext.w
                position = image.u16(record + 2);
                return;                                               // legacy .End: rts
            }
            velocity = (short) velocity;
            position = (position + velocity) & 0xFFFF;
            xVel = position;                                          // move.w d0,oXVel(a0)
            moveX(position);                                          // ext.l; asl.l #8; add.l d0,oX(a0)
            timer = (timer - 1) & 0xFF;
            if (timer != 0) return;                                   // beq.s .AdvanceDirection; rts
            direction = (direction + 1) & 3;
        }
    }

    @Override public int solidWidth() { return width; }
    @Override public int solidYRadius() { return yRadius; }
    @Override public int solidXVelocity() { return xVel; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return baseX; }

    /** The object's own chunk test on the base X, with no on-screen exemption; DespawnObjectR3 unloads it (:36-50). */
    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return outsideChunkRange(baseX, cameraX);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        CdObjectArtProvider provider = provider();
        if (provider == null) return;
        String key = CczObjectArt.key(provider, CczObjectArt.BLOCK, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, 0, false, false);
    }

    public int timer() { return timer; }
    public int direction() { return direction; }
}
