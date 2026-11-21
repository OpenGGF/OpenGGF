package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.projector.ProjectorArt;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TrigLookupTable;

import java.util.Arrays;
import java.util.List;

/**
 * The shared orbit and single-landing hop routines, with stage-owned art and radii.
 * ObjAnimal (Level/Palmtree Panic/Objects/Animal/Main.asm:7-19) selects the subtype after
 * CheckAnimalPrescence (Level/Object Time Check.asm:52-81).
 */
@CameraRelativeDraw(CameraRelativeDraw.Use.ART_SELECTION)
public final class CdAnimal extends CdObject
        implements com.openggf.game.soniccd.objects.projector.ProjectorChild, com.openggf.level.objects.RewindRecreatable,
        ObjectVisualFacing.Source {
    private enum RoutineEnd { NONE, RANGE, DRAW, DRAW_AND_RANGE }

    private RoutineEnd routineEnd = RoutineEnd.NONE;
    private int projectorSlot = -1;
    private int projectorId;
    private int routine;
    private int originX;
    private int originY;
    private int angle;
    private int angleDelta;
    private int statusFlip;
    private int xLong;
    private int yLong;
    private int xVel;
    private int yVel;
    private int mapFrame;
    private int spriteFlip;
    /** AnimateObject's script bytes are stored in rewind-captured state. */
    private int[] animationState = {-1, 0, 0, 0, 0};

    public CdAnimal(ObjectSpawn spawn) {
        super(spawn, "Animal");
        statusFlip = spawn.renderFlags() & 3;
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

    /** The hologram routines' test of the projector (oVar3E, oVar3F; Palmtree Panic/Objects/Animal/Main.asm:90-94, 183-187). */
    private boolean projectorAlive() {
        if (com.openggf.game.soniccd.objects.projector.ProjectorChild.parentAlive(services(), projectorSlot, projectorId)) return true;
        setDestroyed(true);                                  // bne.w ObjAnimal_Destroy
        return false;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        routineEnd = RoutineEnd.NONE;
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
            ObjectLifetimeOps.deleteNoRespawn(this);
            return false;
        }
        var time = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        if (time != null && time.projectorDestroyed()) return true;
        routineEnd = RoutineEnd.RANGE;
        return false;
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
                flipHorizontally();
                if ((spawn.subtype() & 0x80) != 0) {
                    routine = 4;                             // .Holographic: oAnim 1, priority 3
                    priority = 3;
                    return;
                }
                CdAnimalArt art = releasedArt();
                if (art != null) tileWord = art.flyingTile();
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
                    flipHorizontally();
                }
                angle = next;
                CdAnimalArt art = releasedArt();
                if (art != null && art.flyingAnimations() >= 0) animate(art.flyingAnimations(), 0);
                routineEnd = RoutineEnd.DRAW_AND_RANGE;
            }
            case 4 -> {
                // ObjFlyingAnimal_Hologram (:86-104): the projector's slot must hold its ID and not be shot down; the animal
                // circles its start (cos >> 3, sin >> 4) at 4 a frame and turns round at 0 and $80, drawing the other frames.
                if (!projectorAlive()) return;
                move(3, 4);
                angle = (angle + 4) & 0xFF;
                if ((angle & 0x7F) == 0) flipHorizontally();     // beq.w ObjAnimal_XFlip: no animation or draw this turn
                else animateHologram(true);
            }
            default -> setDestroyed(true);
        }
    }

    /** ObjFlyingAnimal_Move (Main.asm:109-118): d2/d3 are the cosine and sine shifts. */
    private void move(int cosShift, int sinShift) {
        x = (originX + (TrigLookupTable.cosHex(angle) >> cosShift)) & 0xFFFF;
        y = (originY + (TrigLookupTable.sinHex(angle) >> sinShift)) & 0xFFFF;
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
                    flipHorizontally();
                    return;
                }
                CdAnimalArt art = releasedArt();
                if (art != null) {
                    yRadius = art.groundRadius();
                    tileWord = art.groundTile();
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
                x = xLong >>> 16;
                y = yLong >>> 16;
                int floor = ObjectTerrainUtils.checkFloorDist(services().levelManager(), x, y + yRadius).distance();
                if (floor < 0) {
                    y = (y + floor) & 0xFFFF;
                    yLong = (y << 16) | (yLong & 0xFFFF);
                    yVel = -0x40000;
                    xVel = -xVel;
                    flipHorizontally();
                }
                routineEnd = RoutineEnd.DRAW_AND_RANGE;
            }
            case 4 -> {
                // ObjGroundAnimal_Hologram (:182-190): animate and draw while the projector lives.
                if (projectorAlive()) animateHologram(false);
            }
            default -> setDestroyed(true);
        }
    }

    /** ObjAnimal_XFlip (Level/Palmtree Panic/Objects/Animal/Main.asm:201-204). */
    private void flipHorizontally() {
        spriteFlip ^= 1;
        statusFlip ^= 1;
    }

    private CdAnimalArt releasedArt() {
        var provider = artProvider();
        return provider == null ? null : provider.zoneArt(CdAnimalArt.class);
    }

    private void animateHologram(boolean flying) {
        CdObjectArtProvider provider = artProvider();
        ProjectorArt.Animals animals = provider == null ? null : provider.zoneArt(ProjectorArt.Animals.class);
        if (animals == null) return;
        tileWord = animals.tile();
        animate(animals.animations(flying), flying ? 1 : 0);
        routineEnd = RoutineEnd.DRAW;
    }

    private void animate(int table, int script) {
        CdAnimator animator = new CdAnimator(artProvider().image(), table, script);
        animator.restoreScriptState(animationState);
        animator.step();
        animationState = animator.scriptState();
        mapFrame = animator.mapFrame();
        spriteFlip = animator.flipBits() ^ statusFlip;
    }

    @Override
    protected boolean despawnsByCheckObjDespawn() {
        return routineEnd == RoutineEnd.RANGE || routineEnd == RoutineEnd.DRAW_AND_RANGE;
    }

    // DrawObject clears the retained on-screen bit before the range check:
    // ObjFlyingAnimal_Flying (Level/Palmtree Panic/Objects/Animal/Main.asm:76-81),
    // ObjGroundAnimal_Main (Level/Palmtree Panic/Objects/Animal/Main.asm:158-177),
    // AnimalObject_1_Routine2 (objects/r5/animal.asm:48-68), AnimalObject_0_Routine2 (objects/r5/animal.asm:140-159),
    // AnimalObject_1_Routine2 (objects/r7/animal.asm:46-66),
    // AnimalObject_1_Routine2 (objects/r8/animal.asm:35-55), AnimalObject_0_Routine2 (objects/r8/animal.asm:98-117).
    @Override protected boolean drawsBeforeDespawnCheck() { return true; }
    @Override protected boolean calledDrawObjectThisFrame() {
        return routineEnd == RoutineEnd.DRAW || routineEnd == RoutineEnd.DRAW_AND_RANGE;
    }

    @Override protected int despawnX() {
        return (spawn.subtype() & 0x7F) == 0 && routineEnd == RoutineEnd.DRAW_AND_RANGE ? originX : x;
    }

    int mapFrame() { return mapFrame; }
    boolean facesLeft() { return (spriteFlip & 1) != 0; }

    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        return (spriteFlip & 1) != 0 ? ObjectVisualFacing.Basis.RIGHT : ObjectVisualFacing.Basis.LEFT;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!calledDrawObjectThisFrame() || !renderFlagOnScreen() || isDestroyed()) return;
        boolean flying = (spawn.subtype() & 0x7F) == 0;
        String key;
        if (routineEnd == RoutineEnd.DRAW) {
            key = flying ? ProjectorArt.FLYING_ANIMAL : ProjectorArt.GROUND_ANIMAL;
        } else {
            CdAnimalArt art = releasedArt();
            if (art == null) return;
            key = art.sheet(flying, services().camera().getX());
        }
        drawFrame(key, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }

    @Override
    public String traceDebugDetails() {
        return "routine=" + routine + " phase=" + angle + " map=" + mapFrame + " flip=" + spriteFlip
                + " drawn=" + calledDrawObjectThisFrame() + " script=" + Arrays.toString(animationState);
    }
}
