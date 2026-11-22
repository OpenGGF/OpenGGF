package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.objects.projector.ProjectorChild;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * Tidal Tempest's animal, ID $2F. AnimalObject (objects/r4/animal.asm:3-45) selects the
 * species with subtype bit 0 and the projector form with subtype bit 7.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class TtzAnimal extends TtzObject
        implements ObjectVisualFacing.NonDirectional, ProjectorChild, SpawnRewindRecreatable {
    private enum Motion { INITIALIZING, WALKING, HOLOGRAM }

    private Motion motion = Motion.INITIALIZING;
    private int originY;
    private int angle;
    private int xVel;
    private int angleStep;
    private boolean xFlip;
    private transient CdAnimator animator;
    private int[] animState = {-1, 0, 0, 0, 0};
    private boolean rangeOnly;
    private boolean drawThisFrame;
    private int projectorSlot = -1;
    private int projectorId;

    public TtzAnimal(ObjectSpawn spawn) {
        super(spawn, "Animal");
    }

    private boolean hologram() { return (spawn.subtype() & 0x80) != 0; }
    private boolean secondSpecies() { return (spawn.subtype() & 1) != 0; }

    @Override
    public void linkProjector(int parentSlot, int parentId) {
        projectorSlot = parentSlot;
        projectorId = parentId;
    }

    @Override public int projectorSlot() { return projectorSlot; }
    @Override public int projectorId() { return projectorId; }

    @Override protected boolean despawnsByCheckObjDespawn() { return !hologram() && (rangeOnly || drawThisFrame); }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        rangeOnly = false;
        drawThisFrame = false;
        if (!checkPresence()) return;
        switch (motion) {
            case INITIALIZING -> init();
            case WALKING -> walk();
            case HOLOGRAM -> orbit();
        }
    }

    /** CheckAnimalPrescence (common/time_check.asm:34-58); false when the routine does not run. */
    private boolean checkPresence() {
        if (hologram()) return true;
        var flags = flags();
        if (timeZone() >= 2) {
            if (flags.goodFuture) return true;
            setDestroyed(true);                              // addq.l #4,sp; jmp DeleteObject
            return false;
        }
        var time = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        if (time != null && time.projectorDestroyed()) return true;
        rangeOnly = true;
        return false;
    }

    /** AnimalObject (objects/r4/animal.asm:8-22) and loc_20E3E0 (objects/r4/animal.asm:24-56). */
    private void init() {
        priority = 5;
        width = 8;
        yRadius = 12;
        originY = y;
        var provider = artProvider();
        if (provider != null) {
            int table = TtzObjectCatalogue.offset(provider.payload(), "animalTiles");
            if (table >= 0) {
                tileWord = provider.image().u16(table + (act() * 4 + timeZone()) * 2);
                if (secondSpecies()) tileWord += 0x2000;
            }
        }
        if (hologram()) {
            motion = Motion.HOLOGRAM;
            xFlip = !secondSpecies();
            angleStep = secondSpecies() ? -0x200 : 0x200;
            angle = secondSpecies() ? 0x3000 : 0;
        } else {
            motion = Motion.WALKING;
            xVel = 0x10000;
            angleStep = 0x100;
        }
        animator = newAnimator();
    }

    private CdAnimator newAnimator() {
        return TtzObjectArt.animator(artProvider(), "animalAnimations", hologram() ? 0 : 1);
    }

    @Override
    protected void afterGenericRewindStateRestored(RewindCaptureContext context) {
        animator = motion == Motion.INITIALIZING ? null : newAnimator();
        if (animator != null) animator.restoreScriptState(animState);
    }

    private void orbit() {
        // loc_20E45A (objects/r4/animal.asm:61-69) reads the current occupant's ID and shutdown flag.
        if (!ProjectorChild.parentAlive(services(), projectorSlot, projectorId)) {
            setDestroyed(true);
            return;
        }
        // loc_20E472 (objects/r4/animal.asm:73-103) rounds both offsets before adding the orbit centre.
        int phase = (angle >> 8) & 0xFF;
        x = spawn.x() + ((TrigLookupTable.cosHex(phase) + 4) >> 3);
        y = originY + ((TrigLookupTable.sinHex(phase) + 8) >> 4);
        angle += angleStep;
        if ((angle & 0x7FFF) == 0) {
            xFlip = !xFlip;
            priority = priority == 5 ? 3 : 5;
        }
        animate();
    }

    /** loc_20E4EA (objects/r4/animal.asm:112-139). */
    private void walk() {
        x = (x + (xVel >> 16)) & 0xFFFF;
        y = (originY + ((TrigLookupTable.sinHex((angle >> 8) & 0xFF) + 4) >> 3)) & 0xFFFF;
        angle += angleStep;
        if (angle < 0 || angle >= 0x10000) {                 // bmi / cmpi.l #$10000 blt
            angleStep = -angleStep;
            xVel = -xVel;
            xFlip = !xFlip;
        }
        animate();
    }

    // loc_20E538 (objects/r4/animal.asm:135-139) draws before checking range.
    @Override protected boolean drawsBeforeDespawnCheck() { return true; }
    @Override protected boolean calledDrawObjectThisFrame() { return drawThisFrame; }

    /** loc_20E4DA (objects/r4/animal.asm:105-108) and loc_20E538 (objects/r4/animal.asm:135-139). */
    private void animate() {
        drawThisFrame = true;
        if (animator != null) {
            animator.step();
            animState = animator.scriptState();
        }
    }

    @Override
    public String traceDebugDetails() {
        return String.format("layer=%d frame=%d xFlip=%b", priority, animState[3], xFlip);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawThisFrame || animator == null || isDestroyed()) return;
        boolean hFlip = xFlip != ((animator.flipBits() & 1) != 0);
        drawSheet(secondSpecies() ? TtzObjectArt.ANIMAL2 : TtzObjectArt.ANIMAL1, animator.mapFrame(), hFlip, false);
    }
}
