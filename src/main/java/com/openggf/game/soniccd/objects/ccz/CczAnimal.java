package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * Collision Chaos' animal, object {@code $39} ({@code ObjCCAnimal}, Level/Collision Chaos/Objects/Animal/Main.asm:15-197; its own
 * routine, not the common {@code ObjAnimal}). {@code CheckAnimalPrescence} (Object Time Check.asm:52-81) runs first: a
 * non-holographic animal (subtype bit 7 clear) in the future is deleted unless {@code goodFuture}; in the past and present it
 * only runs CheckObjectDespawn until {@code projDestroyed}. Subtype bits 0-6 zero is a flying animal: it circles its starting
 * point (X + cos/2, Y + sin/2 of an angle that steps by 1 and turns, flipping, when it would pass $7F), animating
 * {@code Ani_CCAnimalFlying}, priority 1 with the tile's priority bit, despawning on its starting X. Otherwise a ground animal hops
 * (X +1.0 and Y -4.0 a frame, gravity $2000 a frame, frame 0 while rising and 1 after), and on every second landing turns
 * round. The tile word is {@code ObjCCAnimal_BaseTileList[act * 4 + timeZone]}. The holographic animals (subtype bit 7) follow
 * the Metal Sonic projector ({@code $38}, hosted by objects/projector) and delete themselves when its slot no longer holds
 * object $38 or it has been shot down.
 */
@CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
public final class CczAnimal extends CczObject
        implements ObjectVisualFacing.Source, com.openggf.game.soniccd.objects.projector.ProjectorChild,
        com.openggf.level.objects.RewindRecreatable {
    private int projectorSlot = -1;
    private int projectorId;

    private int baseX;               // oVar2A
    private int baseY;               // oVar2C
    private int phase;               // oVar2E
    private int phaseStep;           // oVar2F
    private int xVel;                // oVar2C (long) in the ground form
    private int yVel;                // oVar30 (long)

    public CczAnimal(ObjectSpawn spawn) {
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
        return new CczAnimal(ctx.spawn());
    }

    /** The hologram routines' test of the projector (oCCAnimalParent, oCCAnimalParentFlag; :99-104, 203-208). */
    private boolean projectorAlive() {
        if (com.openggf.game.soniccd.objects.projector.ProjectorChild.parentAlive(services(), projectorSlot, projectorId)) return true;
        setDestroyed(true);                                                // bne.w ObjCCAnimal_Destroy
        return false;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!checkPresence()) return;
        if ((spawn.subtype() & 0x7F) != 0) ground();
        else flying();
    }

    /** CheckAnimalPrescence (Object Time Check.asm:52-81); false when the routine does not run this frame. */
    private boolean checkPresence() {
        if ((byte) spawn.subtype() < 0) return true;
        var flags = flags();
        if (timeZone() >= 2) {
            if (flags.goodFuture) return true;
            setDestroyed(true);                                            // addq.l #4,sp; jmp DeleteObject
            return false;
        }
        var time = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        return time != null && time.projectorDestroyed();                  // else addq.l #4,sp; jmp CheckObjectDespawn
    }

    private void flying() {
        switch (routine) {
            case 0 -> {                                                    // ObjCCAnimal_Flying_Init (:46-70): ends with rts
                routine = 2;
                spriteFlip = 0;
                yRadius = 8;
                priority = 1;
                width = 8;
                baseX = x;
                baseY = y;
                flipHorizontally();                                        // ObjCCAnimal_XFlip
                tileWord = CczObjectArt.animalTile(provider());
                if ((byte) spawn.subtype() < 0) {
                    routine = 4;                                           // .Holographic: oAnim 1, priority 3
                    priority = 3;
                    return;
                }
                priority = 1;
                tileWord |= 0x8000;
                phase = 1;                                                 // move.w #$101,oCCAnimalPhase(a0)
                phaseStep = 1;
            }
            case 2 -> {                                                    // ObjCCAnimal_Flying_Orbit (:72-93)
                move(1, 1);
                int next = (phase + phaseStep) & 0xFF;
                if (((next - 1) & 0xFF) >= 0x7F) {                         // subq.b #1,d1; subi.b #$7F,d1; bcs .NoFlip
                    next = phase;
                    phaseStep = -phaseStep & 0xFF;
                    flipHorizontally();
                }
                phase = next;
                if (animator == null) animator = CczObjectArt.animator(provider(), "animalFlyingAnimations", 0);
                animate();
            }
            case 4 -> {                                                    // ObjCCAnimal_Flying_Hologram (:99-114)
                if (!projectorAlive()) return;
                move(3, 4);
                phase = (phase + 4) & 0xFF;
                if ((phase & 0x7F) == 0) {                                 // beq.w ObjCCAnimal_XFlip: no animation or draw this turn
                    flipHorizontally();
                    return;
                }
                if (animator == null) animator = CczObjectArt.animator(provider(), "animalFlyingAnimations", 1);
                animate();
            }
            default -> setDestroyed(true);
        }
    }

    /** ObjCCAnimal_MoveFlying (:110-120). */
    private void move(int cosShift, int sinShift) {
        x = (baseX + (TrigLookupTable.cosHex(phase) >> cosShift)) & 0xFFFF;
        y = (baseY + (TrigLookupTable.sinHex(phase) >> sinShift)) & 0xFFFF;
    }

    private void ground() {
        switch (routine) {
            case 0 -> {                                                    // ObjCCAnimal_Ground_Init (:129-145): ends with rts
                routine = 2;
                spriteFlip = 0;
                yRadius = 0xC;
                priority = 4;
                width = 8;
                tileWord = CczObjectArt.animalTile(provider());
                if ((byte) spawn.subtype() < 0) {
                    routine = 8;                                           // .Holographic
                    flipHorizontally();
                    return;
                }
                xVel = 0x10000;
                yVel = -0x40000;
            }
            case 2, 4 -> {                                                 // ObjCCAnimal_Ground_Bounce (:147-165)
                addToX(xVel);
                addToY(yVel);
                yVel += 0x2000;
                mapFrame = yVel < 0 ? 0 : 1;                               // smi d0; addq.b #1,d0
                int d1 = ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();
                if (d1 < 0) {
                    routine += 2;
                    y = (y + d1) & 0xFFFF;
                    yVel = -0x40000;
                }
            }
            case 6 -> {                                                    // ObjCCAnimal_Ground_Turn (:167-171)
                routine = 2;
                xVel = -xVel;
                flipHorizontally();
            }
            case 8 -> projectorAlive();                                    // ObjCCAnimal_Ground_Hologram (:203-211); Ani_CCAnimalGround has no catalogue anchor
            default -> setDestroyed(true);
        }
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    protected int despawnX() {
        return (spawn.subtype() & 0x7F) == 0 && routine != 0 ? baseX : x;
    }

    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        return (spriteFlip & 1) != 0 ? ObjectVisualFacing.Basis.RIGHT : ObjectVisualFacing.Basis.LEFT;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        CdObjectArtProvider provider = provider();
        if (provider == null) return;
        boolean flying = (spawn.subtype() & 0x7F) == 0;
        String key = CczObjectArt.key(provider, flying ? CczObjectArt.ANIMAL_FLYING : CczObjectArt.ANIMAL_GROUND,
                tileWord & 0x7FFF, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }

    public int phase() { return phase; }
    public int xVelocity() { return xVel; }
    public int yVelocity() { return yVel; }
}
