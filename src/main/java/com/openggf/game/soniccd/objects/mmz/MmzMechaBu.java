package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * Mecha-Bu, object $24 in every Metallic Madness index ({@code MechaBuObject}, objects/r8/mecha_bu.asm:3-178): a badnik that
 * drops to the floor, then patrols $199+1 frames each way following the floor, with a saw child ({@link MmzMechaBuSaw})
 * that swings in front of it. Collision $2A (killable). The routine ends in CheckObjectDespawn2 against its initial X.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class MmzMechaBu extends MmzBadnik {
    private int initialX;                        // oVar30
    private int speed = -0x5000;                 // oVar2C
    private int timer;                           // oVar2A
    private int soundCounter;                    // oVar32

    public MmzMechaBu(ObjectSpawn spawn) {
        super(spawn, "MechaBu");
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return initialX; }

    @Override
    protected void run(PlayableEntity player) {
        switch (routine) {
            case 0 -> { init(); fall(); }
            case 2 -> fall();
            case 4 -> patrol();
            default -> { }
        }
    }

    /** MechaBuObject_0_Routine0 (objects/r8/mecha_bu.asm:29-59). */
    private void init() {
        initialX = x;
        priority = 4;
        tileWord = MmzObjectArt.MECHA_BU_TILE;
        width = 0x18;
        yRadius = 0xE;
        colType = 0x2A;
        speed = -0x5000;
        routine += 2;
        initialised = true;
        var objects = services().objectManager();
        if (objects == null) return;
        MmzMechaBuSaw saw = new MmzMechaBuSaw(new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(),
                0, false, 0), this);       // the saw takes no layout flip bits (loc_20C4A6, :46-59)
        objects.addDynamicObjectAfterCurrent(saw);                 // jsr SpawnObjectAfter
        if (subtype2() != 0) {                                      // tst.b oSubtype2(a0); beq (loc_20C4A6, :46-59)
            flipHorizontally();
            saw.flipHorizontally();
        }
    }

    /** MechaBuObject_0_Routine2 (objects/r8/mecha_bu.asm:60-72): one pixel down until the floor. */
    private void fall() {
        addToY(0x10000);
        int d1 = floorDistance();
        if (d1 < 0) {
            y = (y + d1) & 0xFFFF;
            routine += 2;
        }
    }

    /** MechaBuObject_0_Routine4 (objects/r8/mecha_bu.asm:73-97). */
    private void patrol() {
        if (renderFlagOnScreen()) {
            soundCounter = (soundCounter + 1) & 0xFFFF;
            if ((soundCounter & 0x1F) == 0) services().playSfx(0xB1);   // move.w #$B1,d0; PlayFmSound
        }
        addToX(speed);
        y = (y + floorDistance()) & 0xFFFF;
        timer = (timer - 1) & 0xFFFF;
        if ((timer & 0x8000) != 0) {                                    // addi.w #-1; bpl.s
            timer = 0x199;
            speed = -speed;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        drawSheet(MmzObjectArt.MECHA_BU, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }
}
