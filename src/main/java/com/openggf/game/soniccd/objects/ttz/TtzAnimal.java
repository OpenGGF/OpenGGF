package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * Tidal Tempest's layout animal, object $2F ({@code AnimalObject}, objects/r4/animal.asm:3-153; every Tidal Tempest
 * ObjectIndex lists it at position 47). {@code CheckAnimalPrescence} (common/time_check.asm:34-58) runs first and
 * ends the frame for the caller: a non-holographic animal (subtype bit 7 clear) in a future time zone is deleted
 * unless {@code good_future} is set (the Tidal Tempest boss act's future acts place it with the flag clear, so it
 * frees its slot on the frame it is created); in the past or present it only runs CheckObjectDespawn until
 * {@code projector_destroyed}. A permitted animal bobs on the sine of an angle that advances $100 a frame while it
 * walks one pixel a frame, turning (X flip) when the angle leaves 0-$FFFF (loc_20E4EA-loc_20E524). A holographic
 * animal (subtype bit 7) follows the Metal Sonic projector, which Tidal Tempest does not assemble: it deletes on its
 * first following frame (loc_20E45A, the link is not a $52 object). The tile word is {@code word_20E5C4[act * 4 + timeZone]}
 * ($450 for every Tidal Tempest act and zone), plus $2000 and {@code AnimalSprites2} for an odd subtype
 * (animal.asm:11-30); {@code AnimalAnims} runs script 1 while it walks.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class TtzAnimal extends TtzObject implements ObjectVisualFacing.NonDirectional {
    private int routine;
    private int originY;       // obj.var_2e
    private int angle;         // obj.var_30, the long's byte at +2 is the sine angle
    private int xVel;          // obj.var_2a
    private int angleStep;     // obj.var_34
    private boolean xFlip;
    private com.openggf.game.soniccd.objects.common.CdAnimator animator;
    private int mapFrame;
    private boolean second;

    public TtzAnimal(ObjectSpawn spawn) {
        super(spawn, "Animal");
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!checkPresence()) return;
        switch (routine) {
            case 0 -> init();
            case 2 -> walk();
            default -> setDestroyed(true);                   // loc_20E45A: the projector link is not an object $52
        }
    }

    /** CheckAnimalPrescence (time_check.asm:34-58); false when the routine does not run. */
    private boolean checkPresence() {
        if ((spawn.subtype() & 0x80) != 0) return true;
        var flags = flags();
        if (timeZone() >= 2) {
            if (flags.goodFuture) return true;
            setDestroyed(true);                              // addq.l #4,sp; jmp DeleteObject
            return false;
        }
        var time = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        return time != null && time.projectorDestroyed();    // else addq.l #4,sp; jmp CheckObjectDespawn
    }

    /** AnimalObject routine 0 (animal.asm:8-60). */
    private void init() {
        priority = 5;
        width = 8;
        yRadius = 12;
        originY = y;
        if ((spawn.subtype() & 0x80) != 0) {
            routine = 0xFE;                                  // subq.b #2,obj.routine: a negative routine, the hologram
            xFlip = true;
            return;
        }
        routine = 2;                                         // loc_20E43E
        xVel = 0x10000;
        angleStep = 0x100;
        var provider = artProvider();
        second = (spawn.subtype() & 1) != 0;
        if (provider != null) {
            int table = TtzObjectCatalogue.offset(provider.payload(), "animalTiles");
            if (table >= 0) {
                tileWord = provider.image().u16(table + (act() * 4 + timeZone()) * 2);   // move.w (a1,d0.w),obj.sprite_tile(a0)
                if (second) tileWord += 0x2000;                                          // addi.w #$2000
            }
            animator = TtzObjectArt.animator(provider, "animalAnimations", 1);         // move.b #1,obj.anim_id(a0)
        }
    }

    /** loc_20E4EA-loc_20E538: x walks, y follows (sine + 4) >> 3 of the angle's byte. */
    private void walk() {
        x += xVel >> 16;
        y = originY + ((TrigLookupTable.sinHex((angle >> 8) & 0xFF) + 4) >> 3);
        angle += angleStep;
        if (angle < 0 || angle >= 0x10000) {                 // bmi / cmpi.l #$10000 blt
            angleStep = -angleStep;
            xVel = -xVel;
            xFlip = !xFlip;
        }
        if (animator != null) {                              // loc_20E538: AnimateObject
            animator.step();
            mapFrame = animator.mapFrame();
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine != 2 || animator == null) return;
        boolean hFlip = xFlip != ((animator.flipBits() & 1) != 0);
        drawSheet(second ? TtzObjectArt.ANIMAL2 : TtzObjectArt.ANIMAL1, mapFrame, hFlip, false);
    }
}
