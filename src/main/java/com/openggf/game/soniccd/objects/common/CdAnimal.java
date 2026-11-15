package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * Animal, object $24 ({@code ObjAnimal}, Palmtree Panic/Objects/Animal/Main.asm:7-217; the Wacky
 * Workbench copy is the same routine). {@code CheckAnimalPrescence} (Object Time Check.asm:52-81)
 * runs first: a non-holographic animal (subtype bit 7 clear) in the future is deleted unless
 * {@code goodFuture} is set; in the past or present it only runs CheckObjDespawn until
 * {@code projDestroyed}. Subtype bits 0-6 zero is a flying animal, otherwise a ground animal.
 *
 * <p>Flying: it circles its origin, {@code x0 + cos/2}, {@code y0 + sin/2} of an angle that steps
 * by a delta of 1 and turns back (with an X flip) when it would pass $7F, animating Ani_FlyingAnimal
 * and despawning on its initial X. Ground: it hops, X +1.0 and Y -4.0 a frame with $2000 gravity,
 * frame 0 while rising, and on the floor turns round. Holographic animals (subtype bit 7) follow
 * the Metal Sonic projector (oVar3E, hosted by objects/projector): they delete themselves when its slot no
 * longer holds its ID or it has been shot down, and otherwise circle (flying) or stay (ground). The animal mappings and base tiles are per-zone data not in the catalogue:
 * nothing is drawn.
 */
public final class CdAnimal extends CdObject
        implements com.openggf.game.soniccd.objects.projector.ProjectorChild, com.openggf.level.objects.RewindRecreatable {
    private int projectorSlot = -1;
    private int projectorId;
    private int routine;
    private int originX;
    private int originY;
    private int angle;
    private int angleDelta;
    private boolean xFlip;
    private int xLong;
    private int yLong;
    private int xVel;
    private int yVel;
    private int mapFrame;

    public CdAnimal(ObjectSpawn spawn) {
        super(spawn, "Animal");
    }

    @Override
    public void linkProjector(int parentSlot, int parentId) {
        this.projectorSlot = parentSlot;
        this.projectorId = parentId;
    }

    @Override public int projectorSlot() { return projectorSlot; }

    @Override public int projectorId() { return projectorId; }

    /** A hologram animal is created at runtime by the projector, so a rewind restore rebuilds it from its spawn. */
    @Override
    public com.openggf.level.objects.AbstractObjectInstance recreateForRewind(com.openggf.level.objects.RewindRecreateContext ctx) {
        return new CdAnimal(ctx.spawn());
    }

    /** The hologram routines' test of the projector (oVar3E, oVar3F; Palmtree Panic/Objects/Animal/Main.asm:77-82, 170-175). */
    private boolean projectorAlive() {
        if (com.openggf.game.soniccd.objects.projector.ProjectorChild.parentAlive(services(), projectorSlot, projectorId)) return true;
        setDestroyed(true);                                  // bne.w ObjAnimal_Destroy
        return false;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!checkPresence()) return;
        if ((spawn.subtype() & 0x7F) != 0) ground();
        else flying();
    }

    /** CheckAnimalPrescence (Object Time Check.asm:52-81); false when the routine does not run. */
    private boolean checkPresence() {
        if ((spawn.subtype() & 0x80) != 0) return true;
        var flags = flags();
        if (timeZone() >= 2) {
            if (flags.goodFuture) return true;
            setDestroyed(true);                              // addq.l #4,sp; jmp DeleteObject
            return false;
        }
        var time = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        return time != null && time.projectorDestroyed();    // else addq.l #4,sp; jmp CheckObjDespawn
    }

    private void flying() {
        switch (routine) {
            case 0 -> {
                // ObjFlyingAnimal_Init (Main.asm:34-56): radii/priority/width $08,$08,$04,$08; XFlip.
                routine = 2;
                yRadius = 8;
                width = 8;
                priority = 4;
                originX = x;
                originY = y;
                xFlip = !xFlip;
                if ((spawn.subtype() & 0x80) != 0) {
                    routine = 4;                             // .Holographic: oAnim 1, priority 3
                    priority = 3;
                    return;
                }
                tileWord |= 0x8000;
                angle = 1;                                   // move.w #$101,oVar2E(a0)
                angleDelta = 1;
            }
            case 2 -> {
                // ObjFlyingAnimal_Flying (Main.asm:61-81).
                move(1, 1);
                int next = (angle + angleDelta) & 0xFF;
                if (((next - 1) & 0xFF) >= 0x7F) {          // subq.b #1; subi.b #$7F; bcs .NoFlip
                    next = angle;
                    angleDelta = -angleDelta & 0xFF;
                    xFlip = !xFlip;
                }
                angle = next;
            }
            case 4 -> {
                // ObjFlyingAnimal_Hologram (:77-91): the projector's slot must hold its ID and not be shot down; the animal
                // circles its start (cos >> 3, sin >> 4) at 4 a frame and turns round at 0 and $80, drawing the other frames.
                if (!projectorAlive()) return;
                move(3, 4);
                angle = (angle + 4) & 0xFF;
                if ((angle & 0x7F) == 0) xFlip = !xFlip;     // beq.w ObjAnimal_XFlip: no animation or draw this turn
            }
            default -> setDestroyed(true);
        }
    }

    /** ObjFlyingAnimal_Move (Main.asm:109-118): d2/d3 are the cosine and sine shifts. */
    private void move(int cosShift, int sinShift) {
        x = originX + (TrigLookupTable.cosHex(angle) >> cosShift);
        y = originY + (TrigLookupTable.sinHex(angle) >> sinShift);
    }

    private void ground() {
        switch (routine) {
            case 0 -> {
                // ObjGroundAnimal_Init (Main.asm:136-153).
                routine = 2;
                yRadius = 8;
                width = 8;
                priority = 4;
                originX = x;
                if ((spawn.subtype() & 0x80) != 0) {
                    routine = 4;
                    xFlip = !xFlip;
                    return;
                }
                xLong = x << 16;
                yLong = y << 16;
                xVel = 0x10000;
                yVel = -0x40000;
            }
            case 2 -> {
                // ObjGroundAnimal_Main (Main.asm:158-177).
                xLong += xVel;
                yLong += yVel;
                yVel += 0x2000;
                mapFrame = yVel < 0 ? 0 : 1;                 // smi d0; addq.b #1,d0
                x = xLong >> 16;
                y = yLong >> 16;
                var floor = ObjectTerrainUtils.checkFloorDist(x, y, yRadius);
                if (floor.hasCollision() && floor.distance() < 0) {
                    y += floor.distance();
                    yLong = (y << 16) | (yLong & 0xFFFF);
                    yVel = -0x40000;
                    xVel = -xVel;
                    xFlip = !xFlip;
                }
            }
            case 4 -> projectorAlive();                      // ObjGroundAnimal_Hologram (:170-178): animate and draw while the projector lives
            default -> setDestroyed(true);
        }
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return (spawn.subtype() & 0x7F) == 0 && routine != 0 ? originX : x; }

    int mapFrame() { return mapFrame; }
    boolean facesLeft() { return xFlip; }

    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
