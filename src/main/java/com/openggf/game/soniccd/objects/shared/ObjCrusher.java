package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.player.CdSonicAnimation;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Metallic Madness's crusher ({@code CrusherObject}, objects/r8/crusher.asm:3-209): a solid block
 * moving on a sine wave about its placed Y. Wacky Workbench's crusher (objects/r6/crusher.asm) is
 * another routine under the same name and is not hosted here.
 *
 * <p>Subtype: bits 4-5 the start phase, bit 6 the amplitude, bits 0-1 the form. Forms 0 and 2 are
 * a 64x64 block ({@code CrusherObject_1_Routine0}, :78-87, phase step from bits 2-3); form 1 is a
 * 64x192 column ({@code CrusherObject_1_Routine2}, :91-107, step from bit 2) that holds at phases
 * $100 and $300 for a pause from bit 3 and, at phase $210, launches a player it touches upward
 * (bit 7 picks -$1000 or -$A00, :182-203). The phase, amplitude, step and pause tables are the
 * stage's own ({@link SharedObjectCatalogue}): bit 3's pause index reads the byte after the
 * two-entry table, the first byte of {@code CrusherObject_0_Routine2} ({@code byte_20BB76}, :104-113;
 * {@code FixBugs = 0}). Form 3 jumps into its own jump table ({@code off_20BAFE}, :53-61) and
 * is not supported.
 *
 * <p>Solidity: while rising the routine calls SolidObject before moving, otherwise after
 * (:118-121, :207-211); the Y velocity it tests is the last move times $100 (:124, :171-176).
 */
public final class ObjCrusher extends CdObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** Phase in 1/4 angle units: andi.w #$3FF (objects/r8/crusher.asm:162). */
    private static final int PHASE_MASK = 0x3FF;
    /** loc_20BBBA: the column holds at phases $100 and $300 (objects/r8/crusher.asm:142-146). */
    private static final int HOLD_PHASE_1 = 0x100, HOLD_PHASE_2 = 0x300;
    /** cmpi.w #$210,d0: the launch phase (objects/r8/crusher.asm:182-184). */
    private static final int LAUNCH_PHASE = 0x210;

    private boolean initialised;
    private int form;
    private int baseY;
    private int phase;
    private int amplitude;
    private int step;
    private int pause;
    private int pauseTimer;
    private boolean holding;
    private int ySpeed;
    private int mapFrame;
    private int launchSpeed;

    public ObjCrusher(ObjectSpawn spawn) {
        super(spawn, "Crusher");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialised) {
            if (!init()) return;
            return;                                    // routine 0 ends with the form's setup (:19-54, :87, :107)
        }
        move(player);
    }

    /** CrusherObject_0_Routine0 (objects/r8/crusher.asm:19-54) and the form setup (:78-107). */
    private boolean init() {
        var level = cdLevel();
        var provider = artProvider();
        if (level == null || provider == null) return false;
        var payload = level.payload();
        var image = provider.image();
        int subtype = getSpawn().subtype() & 0xFF;
        priority = 3;
        tileWord = SharedObjectCatalogue.offset(payload, "crusherTile");
        baseY = y;
        ySpeed = 0;
        phase = image.u16(SharedObjectCatalogue.offset(payload, "crusherPhases") + ((subtype & 0x30) >> 3));
        amplitude = (short) image.u16(SharedObjectCatalogue.offset(payload, "crusherAmplitudes") + ((subtype & 0x40) >> 5));
        y = sineY();
        form = subtype & 3;
        int steps = SharedObjectCatalogue.offset(payload, "crusherSteps");
        if (form == 1) {
            mapFrame = 1;
            width = 0x20;
            yRadius = 0x60;
            step = image.u16(steps + ((subtype & 4) >> 1));
            // CrusherObject_1_Routine2: ext.w d1; andi.w #8,d1; lsr.w #2,d1; the pause byte at d1 (:91-105).
            pause = image.u8(SharedObjectCatalogue.offset(payload, "crusherPauses") + ((subtype & 8) >> 2));
            holding = false;
        } else {
            mapFrame = 0;
            width = 0x20;
            yRadius = 0x20;
            step = image.u16(steps + ((subtype & 0xC) >> 1));
        }
        // loc_20BC38: the launch speed by bit 7, -$1000 or -$A00 (:192-198); the table word_20BC78 (:218-220).
        launchSpeed = (subtype & 0x80) != 0 ? -0xA00 : -0x1000;
        initialised = true;
        return true;
    }

    /** var_30 + (sin(phase >> 2) * amplitude) >> 8, low word (muls.w; lsr.l #8; add.w, :40-48, :164-169). */
    private int sineY() {
        int product = TrigLookupTable.sinHex((phase >> 2) & 0xFF) * amplitude;
        return (short) ((product >>> 8) + baseY);
    }

    /** CrusherObject_0_Routine2 (objects/r8/crusher.asm:117-211). */
    private void move(PlayableEntity player) {
        if (ySpeed < 0) solidObject(ReevaluatingSolidObject.Entry.SOLID);   // tst.w obj.y_speed; bpl.s
        int oldY = y;
        int newY;
        if (form == 1 && holding) {
            newY = y;
            if (--pauseTimer < 0) {                   // subq.b #1,obj.var_3a; bcc.w (:133-137)
                pauseTimer &= 0xFF;
                holding = false;
                newY = advance();
            }
        } else if (form == 1 && (phase == HOLD_PHASE_1 || phase == HOLD_PHASE_2)) {
            holding = true;                           // bset #7,obj.var_3e; var_3a = var_38 (:151-155)
            pauseTimer = pause;
            newY = y;
        } else {
            newY = advance();
        }
        y = newY;
        // swap d0; sub.l (sp)+,d0; asr.l #8,d0 (:171-176): the move in 1/256 pixels, as a word.
        ySpeed = (short) ((newY - oldY) << 8);
        if (form == 1 && phase == LAUNCH_PHASE) {
            if (solidObject(ReevaluatingSolidObject.Entry.SOLID) && player instanceof AbstractPlayableSprite sprite) {
                launch(sprite);
            }
            return;
        }
        if (ySpeed >= 0) solidObject(ReevaluatingSolidObject.Entry.SOLID); // tst.w obj.y_speed; bmi.s (:207-211)
    }

    /** loc_20BBDE: phase += step, masked; then the sine position (:159-169). */
    private int advance() {
        phase = (phase + step) & PHASE_MASK;
        return sineY();
    }

    /** loc_20BC38 (objects/r8/crusher.asm:192-203). */
    private void launch(AbstractPlayableSprite sprite) {
        sprite.setYSpeed((short) launchSpeed);
        sprite.setAir(true);                          // bset #1,obj.flags(a1)
        sprite.setOnObject(false);                    // bclr #3,obj.flags(a1)
        sprite.setAnimationId(CdSonicAnimation.SPRING); // move.b #$10,obj.anim_id(a1)
        clearStoodOn();                               // bclr #3,obj.flags(a0)
    }

    /** {@code obj.y_speed}, read by SolidObject for a rider (Object Solid.asm). */
    @Override public int solidYVelocity() { return ySpeed; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialised) drawFrame(SharedObjectArt.CRUSHER, mapFrame, hFlip(), vFlip());
    }

    public int phase() { return phase; }
    public int ySpeedWord() { return ySpeed; }
}
