package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Wacky Workbench's crusher, object $26 in acts 2 and 3 ({@code CrusherObject}, objects/r6/crusher.asm:3-165; Metallic Madness's is
 * another routine, {@link ObjCrusher}). A full-solid $60 wide, $A0 high block that waits for the player to come within $C0 pixels
 * vertically and $88 horizontally to its left (routine 2), extends for 128 frames by two motion records read from the stage
 * (routine 4: $40 frames at +8 then $40 at -8), waits until the player is $88 or more to its right (routine 6) and retracts by the other
 * table (routine 8). {@code clr.w obj.var_3a} also clears the record index {@code var_3b}, so each cycle starts at record 0.
 */
public final class ObjWwzCrusher extends CdObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** {@code cmpi.w #$C0,d0} / {@code cmpi.w #$88,d0} (objects/r6/crusher.asm:41-60, 77-95). */
    private static final int RANGE_Y = 0xC0, RANGE_X = 0x88;

    private int routine;
    private int ySub;
    private int ySpeed;               // obj.y_speed
    private int timer;                // obj.var_3a (byte)
    private int record;               // obj.var_3b (byte)
    private boolean retracting;       // obj.var_3c
    private int acceleration;         // obj.var_3e

    public ObjWwzCrusher(ObjectSpawn spawn) {
        super(spawn, "Crusher");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AbstractPlayableSprite sprite = player instanceof AbstractPlayableSprite s ? s : null;
        if (routine == 0 && !init()) return;
        switch (routine) {
            case 2 -> waiting(sprite);
            case 4 -> {
                retracting = false;
                updateMotion();
                if (record == 2) routine += 2;
            }
            case 6 -> extended(sprite);
            case 8 -> {
                retracting = true;
                updateMotion();
                if (record == 2) routine = 2;
            }
            default -> { }
        }
        if (sprite != null) solidObject(Entry.SOLID);                          // CrusherSolidCollision (:23-29)
    }

    /** CrusherObject_0_Routine0 (:31-39); it falls into Routine2. */
    private boolean init() {
        var level = cdLevel();
        if (level == null || artProvider() == null) return false;
        routine = 2;
        priority = 3;
        tileWord = SharedObjectCatalogue.offset(level.payload(), "wwzCrusherTile");
        yRadius = 0x50;
        width = 0x30;
        return true;
    }

    /** CrusherObject_0_Routine2 (:41-60): the player in range to the left starts the extension. */
    private void waiting(AbstractPlayableSprite player) {
        if (player == null || !verticalInRange(player)) return;
        int dx = ((x & 0xFFFF) - (player.getCentreX() & 0xFFFF)) & 0xFFFF;
        if ((x & 0xFFFF) < (player.getCentreX() & 0xFFFF) || dx >= RANGE_X) return;   // bcs / cmpi.w #$88; bcc
        retracting = false;
        timer = 0;
        record = 0;                                                         // clr.w obj.var_3a: var_3a and var_3b
        routine += 2;
    }

    /** CrusherObject_0_Routine6 (:77-98): wait until the player is $88 or more to the right. */
    private void extended(AbstractPlayableSprite player) {
        if (player == null || !verticalInRange(player)) return;
        int px = player.getCentreX() & 0xFFFF;
        if (px < (x & 0xFFFF)) return;                                      // bcs
        if (((px - (x & 0xFFFF)) & 0xFFFF) < RANGE_X) return;               // cmpi.w #$88; bcs
        timer = 0;
        record = 0;
        routine += 2;
    }

    private boolean verticalInRange(AbstractPlayableSprite player) {
        int dy = ((y & 0xFFFF) - (player.getCentreY() & 0xFFFF)) & 0xFFFF;
        if ((y & 0xFFFF) < (player.getCentreY() & 0xFFFF)) dy = (-dy) & 0xFFFF;   // sub.w; bcc; neg.w
        return dy < RANGE_Y;
    }

    /** CrusherUpdateMotion (:112-149): load the next record when the step has run out, else move. */
    private void updateMotion() {
        if (timer == 0) {
            var level = cdLevel();
            var image = artProvider().image();
            int base = SharedObjectCatalogue.offset(level.payload(), retracting ? "wwzCrusherRetracting" : "wwzCrusherExtending")
                    + record * 4;
            timer = image.u8(base);
            acceleration = (byte) image.u8(base + 1);                       // move.b (a2)+,d0; ext.w d0
            return;
        }
        int position = ((y & 0xFFFF) << 16 | ySub) + ((short) ySpeed << 8);
        y = position >>> 16;
        ySub = position & 0xFFFF;
        ySpeed = (ySpeed + acceleration) & 0xFFFF;
        timer = (timer - 1) & 0xFF;
        if (timer == 0) record = (record + 1) & 0xFF;
    }

    @Override public int solidYVelocity() { return (short) ySpeed; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine != 0) drawFrame(SharedObjectArt.WWZ_CRUSHER, 0, hFlip(), vFlip());
    }
}
