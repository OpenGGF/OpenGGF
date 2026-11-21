package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdAnimator;
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
    private boolean rangeOnly;
    private boolean drawThisFrame;
    private int projectorSlot = -1;
    private int projectorId;
    private final CdAnimator.StageRewind rewindAnimation =
            new CdAnimator.StageRewind(() -> provider().image(), () -> animator, restored -> animator = restored);

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
        rangeOnly = false;
        drawThisFrame = false;
        if (!checkPresence()) return;
        if ((spawn.subtype() & 0x7F) != 0) ground();
        else flying();
    }

    /** CheckAnimalPrescence (Object Time Check.asm:52-81); false when the routine does not run this frame. */
    private boolean checkPresence() {
        if (hologram()) return true;
        var flags = flags();
        if (timeZone() >= 2) {
            if (flags.goodFuture) return true;
            setDestroyed(true);                                            // addq.l #4,sp; jmp DeleteObject
            return false;
        }
        var time = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        if (time != null && time.projectorDestroyed()) return true;
        rangeOnly = true;
        return false;
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
                if (hologram()) {
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
                drawThisFrame = true;
            }
            case 4 -> {                                                    // ObjCCAnimal_Flying_Hologram (:99-114)
                if (!projectorAlive()) return;
                move(3, 4);
                phase = (phase + 4) & 0xFF;
                if ((phase & 0x7F) == 0) {                                 // beq.w ObjCCAnimal_XFlip: no animation or draw this turn
                    flipHorizontally();
                    return;
                }
                animateHologram("animalFlyingAnimations", 1);
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
                if (hologram()) {
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
                int d1 = ObjectTerrainUtils.checkFloorDist(services().levelManager(), x, y + yRadius).distance();
                if (d1 < 0) {
                    routine += 2;
                    y = (y + d1) & 0xFFFF;
                    yVel = -0x40000;
                }
                drawThisFrame = true;
            }
            case 6 -> {                                                    // ObjCCAnimal_Ground_Turn (:167-171)
                routine = 2;
                xVel = -xVel;
                flipHorizontally();
                drawThisFrame = true;
            }
            case 8 -> {                                                    // ObjCCAnimal_Ground_Hologram (Level/Collision Chaos/Objects/Animal/Main.asm:203-211)
                if (!projectorAlive()) return;
                animateHologram("animalGroundAnimations", 0);
            }
            default -> setDestroyed(true);
        }
    }

    private void animateHologram(String table, int script) {
        if (animator == null) animator = CczObjectArt.animator(provider(), table, script);
        animate();
        drawThisFrame = animator != null;
    }

    @Override
    protected boolean despawnsByCheckObjDespawn() {
        // ObjCCAnimal_Flying_Hologram (Level/Collision Chaos/Objects/Animal/Main.asm:106-121) and
        // ObjCCAnimal_Ground_Hologram (Level/Collision Chaos/Objects/Animal/Main.asm:203-211) retire with the projector.
        return !hologram() && (rangeOnly || drawThisFrame);
    }

    @Override
    protected boolean calledDrawObjectThisFrame() {
        return drawThisFrame;
    }

    // ObjCCAnimal_Ground_Bounce (Level/Collision Chaos/Objects/Animal/Main.asm:173-191) draws before checking range.
    @Override protected boolean drawsBeforeDespawnCheck() { return true; }

    private boolean hologram() {
        return (spawn.subtype() & 0x80) != 0;
    }

    @Override
    protected int despawnX() {
        return (spawn.subtype() & 0x7F) == 0 && drawThisFrame ? baseX : x;
    }

    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        return (spriteFlip & 1) != 0 ? ObjectVisualFacing.Basis.RIGHT : ObjectVisualFacing.Basis.LEFT;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawThisFrame || isDestroyed()) return;
        CdObjectArtProvider provider = provider();
        if (provider == null) return;
        boolean flying = (spawn.subtype() & 0x7F) == 0;
        String sheet;
        if (hologram()) {
            sheet = flying ? CczObjectArt.FLYING_HOLOGRAM : CczObjectArt.GROUND_HOLOGRAM;
        } else {
            sheet = flying ? CczObjectArt.ANIMAL_FLYING : CczObjectArt.ANIMAL_GROUND;
        }
        String key = CczObjectArt.key(provider, sheet, tileWord & 0x7FFF, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }

    public int phase() { return phase; }
    public int xVelocity() { return xVel; }
    public int yVelocity() { return yVel; }
}
