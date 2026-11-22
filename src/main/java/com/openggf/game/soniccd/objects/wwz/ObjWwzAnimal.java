package com.openggf.game.soniccd.objects.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.projector.ProjectorChild;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TrigLookupTable;

/**
 * Wacky Workbench animal $29. Bits 0-6 select flying or ground and bit 7 bypasses the
 * layout animal's presence check for projector children (AnimalObject, objects/r6/animal.asm:3-11;
 * CheckAnimalPrescence, common/time_check.asm:34-53).
 */
public final class ObjWwzAnimal extends WwzObject implements ProjectorChild, RewindRecreatable,
        ObjectVisualFacing.Source {
    private final boolean flying;
    private final boolean hologram;
    private int projectorSlot = -1;
    private int projectorId;
    private int originX;
    private int originY;
    private int phase;
    private int phaseStep;
    private int xVelocity;
    private int yVelocity;
    private int statusFlip;
    private int[] scriptState = {-1, 0, 0, 0, 0};
    private boolean drawThisFrame;
    private boolean rangeOnly;

    public ObjWwzAnimal(ObjectSpawn spawn) {
        super(spawn, "Animal");
        flying = (spawn.subtype() & 0x7F) == 0;
        hologram = (spawn.subtype() & 0x80) != 0;
        statusFlip = spawn.renderFlags() & 3;
    }

    @Override
    public ObjWwzAnimal recreateForRewind(RewindRecreateContext context) {
        return new ObjWwzAnimal(context.spawn());
    }

    @Override
    public void linkProjector(int parentSlot, int parentId) {
        projectorSlot = parentSlot;
        projectorId = parentId;
    }

    @Override public int projectorSlot() { return projectorSlot; }
    @Override public int projectorId() { return projectorId; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        drawThisFrame = false;
        rangeOnly = false;
        if (!present()) return;
        if (routine == 0) {
            initialize();
        } else if (hologram) {
            updateHologram();
        } else if (flying) {
            updateFlight();
        } else {
            updateHop();
        }
    }

    private boolean present() {
        if (hologram) return true;
        if (timeZone() >= 2) {
            if (goodFuture()) return true;
            setDestroyed(true);
            return false;
        }
        CdTimeTravel time = CdTimeTravel.of(services().gameModule());
        if (time != null && time.projectorDestroyed()) return true;
        rangeOnly = true;
        return false;
    }

    private void initialize() {
        // AnimalObject_1_Routine0 (objects/r6/animal.asm:23-45) and AnimalObject_0_Routine0
        // (objects/r6/animal.asm:123-139).
        originX = x;
        originY = y;
        spriteFlip = 0;
        yRadius = 8;
        width = 8;
        tileWord = WwzObjectArt.animalTile(artProvider(), timeZone());
        if (flying) {
            flipHorizontally();
            priority = hologram ? 3 : 1;
            if (hologram) {
                routine = 4;
            } else {
                routine = 2;
                tileWord |= 0x8000;
                phase = 1;
                phaseStep = 1;
            }
        } else {
            priority = 4;
            routine = hologram ? 8 : 2;
            xVelocity = 0x10000;
            yVelocity = -0x40000;
        }
    }

    private void updateFlight() {
        // AnimalObject_1_Routine2 (objects/r6/animal.asm:49-69). Retain the endpoint phase for
        // the turn, so the next pass draws at that endpoint a second time.
        orbit(1, 1);
        int next = phase + phaseStep;
        if (next < 1 || next > 0x7F) {
            phaseStep = -phaseStep;
            flipHorizontally();
        } else {
            phase = next;
        }
        animate(0);
    }

    private void updateHop() {
        // AnimalObject_0_Routine2 (objects/r6/animal.asm:143-161) and
        // AnimalObject_0_Routine6 (objects/r6/animal.asm:165-169).
        if (routine == 6) {
            routine = 2;
            xVelocity = -xVelocity;
            flipHorizontally();
        } else {
            addToX(xVelocity);
            addToY(yVelocity);
            yVelocity += 0x2000;
            mapFrame = yVelocity < 0 ? 0 : 1;
            int floor = ObjectTerrainUtils.checkFloorDist(services().levelManager(), x, y + yRadius).distance();
            if (floor < 0) {
                routine += 2;
                y = (y + floor) & 0xFFFF;
                yVelocity = -0x40000;
            }
        }
        drawThisFrame = true;
    }

    private void updateHologram() {
        // AnimalObject_1_Routine4 (objects/r6/animal.asm:73-88) and
        // AnimalObject_0_Routine8 (objects/r6/animal.asm:173-181).
        if (!ProjectorChild.parentAlive(services(), projectorSlot, projectorId)) {
            setDestroyed(true);
            return;
        }
        if (flying) {
            orbit(3, 4);
            phase = (phase + 4) & 0xFF;
            if ((phase & 0x7F) == 0) {
                flipHorizontally();
                return;
            }
        }
        animate(flying ? 1 : 0);
    }

    private void orbit(int xShift, int yShift) {
        // AnimalCalculatePosition (objects/r6/animal.asm:92-101).
        x = (originX + (TrigLookupTable.cosHex(phase) >> xShift)) & 0xFFFF;
        y = (originY + (TrigLookupTable.sinHex(phase) >> yShift)) & 0xFFFF;
    }

    private void animate(int animation) {
        String column = flying ? "animalFlyingAnimations" : "animalGroundAnimations";
        var animator = WwzObjectArt.animator(artProvider(), column, animation);
        animator.restoreScriptState(scriptState);
        animator.step();
        scriptState = animator.scriptState();
        mapFrame = animator.mapFrame();
        spriteFlip = animator.flipBits() ^ statusFlip;
        drawThisFrame = true;
    }

    private void flipHorizontally() {
        // AnimalTogglePriority (objects/r6/animal.asm:190-193) toggles the two X-flip bits.
        spriteFlip ^= 1;
        statusFlip ^= 1;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return !hologram && (rangeOnly || drawThisFrame); }
    @Override protected boolean drawsBeforeDespawnCheck() { return true; }
    @Override protected boolean calledDrawObjectThisFrame() { return drawThisFrame; }
    @Override protected int despawnX() { return flying && drawThisFrame ? originX : x; }

    @Override
    protected String sheetKey() {
        return drawThisFrame && renderFlagOnScreen() && !isDestroyed() ? WwzObjectArt.animalKey(flying, hologram) : null;
    }

    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        return (spriteFlip & 1) != 0 ? ObjectVisualFacing.Basis.RIGHT : ObjectVisualFacing.Basis.LEFT;
    }
}
