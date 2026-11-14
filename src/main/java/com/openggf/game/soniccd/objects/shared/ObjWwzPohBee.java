package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Wacky Workbench's Poh-Bee ({@code PohBeeObject}, objects/r6/poh_bee.asm:3-239; the missile is
 * {@link ObjWwzPohMissile}). Subtype 0 flies at one pixel a frame, turns toward a player inside its
 * corridor and dives to shoot; any other subtype patrols at half speed. Metallic Madness's Poh-Bee
 * (objects/r8/poh_bee.asm) is another routine under the same name.
 */
public final class ObjWwzPohBee extends SharedBadnik {
    private int patrolSpeed;                 // obj.var_2c, 16.16 (:42-52)
    private int diveOffset = -8;             // obj.var_30 (:42)
    private int timer;                       // obj.var_2a
    private int returnDelay;                 // obj.var_32
    private boolean smallSprite;

    public ObjWwzPohBee(ObjectSpawn spawn) {
        super(spawn, "Poh-Bee");
    }

    @Override
    protected void run(PlayableEntity player) {
        switch (routine) {
            case 0 -> { init(); routine2(); routine4(player); }       // Routine0 falls into Routine2 and Routine4
            case 2 -> { routine2(); routine4(player); }
            case 4 -> routine4(player);
            case 6 -> { routine += 2; timer = 0x1E; patrolWait(); }   // Routine6 falls into Routine8
            case 8 -> patrolWait();
            case 0xA -> {                                              // PohBeeObject_1_RoutineA (:148-154)
                if (--timer < 0) routine = 2;
            }
            case 0xC -> { routine += 2; timer = 0x1E; dive(); }        // RoutineC falls into RoutineE
            case 0xE -> dive();
            case 0x10 -> fire();
            case 0x12 -> recover();
            default -> { }
        }
        // PohBeeObject: AnimateObject with the bee's own table (objects/r6/poh_bee.asm:3-14)
        animate();
    }

    /** PohBeeObject_1_Routine0 (:33-52). */
    private void init() {
        var payload = cdLevel().payload();
        routine += 2;
        priority = 1;
        width = 0x18;
        yRadius = 0x0C;
        tileWord = SharedObjectCatalogue.offset(payload, "wwzPohBeeTile");
        colType = 0x31;
        diveOffset = -8;
        smallSprite = getSpawn().subtype() != 0;                       // lea PohBeeSprites2 / move.l #-$8000
        patrolSpeed = smallSprite ? -0x8000 : -0x10000;
        animator = new CdAnimator(artProvider().image(), SharedObjectCatalogue.offset(payload, "wwzPohBeeAnims"), 0);
        initialised = true;
    }

    /** PohBeeObject_1_Routine2 (:54-62). */
    private void routine2() {
        routine += 2;
        timer = smallSprite ? 0x400 : 0x200;
    }

    /** PohBeeObject_1_Routine4 (:64-91). */
    private void routine4(PlayableEntity player) {
        addToX(patrolSpeed);
        if (!smallSprite) {
            if (returnDelay != 0) {
                returnDelay = (returnDelay - 1) & 0xFFFF;              // subq.w #1,obj.var_32(a0)
            } else if (player instanceof AbstractPlayableSprite sprite && checkTurnaround(sprite)) {
                routine = 0xC;                                         // move.b #$C,obj.routine(a0); rts
                return;
            }
        }
        if (--timer < 0) routine = 6;                                  // subq.w #1,obj.var_2a(a0); bpl.s
    }

    /**
     * PohBeeCheckTurnaround (:96-125): the player inside the corridor (Y within -$60 to $60, X within -$78
     * to $78) turns the bee to face it; returns true for "in the corridor".
     */
    private boolean checkTurnaround(AbstractPlayableSprite player) {
        int dy = (player.getCentreY() - y) & 0xFFFF;
        if (((dy + 0x60) & 0xFFFF) >= 0xC0) return false;              // subi.w #-$60 / subi.w #$C0,d0; bcc
        int dx = (player.getCentreX() - x) & 0xFFFF;
        boolean playerRight = (dx & 0x8000) == 0;                      // spl d1 after sub.w
        if (((dx + 0x78) & 0xFFFF) >= 0xF0) return false;
        boolean facingRight = (spriteFlip & 1) != 0;                   // btst #0,obj.sprite_flags(a0); sne d2
        if (facingRight != playerRight) turnAround();                  // eor.b d1,d2; beq
        return true;
    }

    private void turnAround() {
        patrolSpeed = -patrolSpeed;
        diveOffset = -diveOffset;
        flipHorizontally();
    }

    /** PohBeeObject_1_Routine8 (:133-144). */
    private void patrolWait() {
        if (--timer >= 0) return;
        routine += 2;
        timer = 0x1E;
        turnAround();
    }

    /** PohBeeObject_1_RoutineE (:162-177). */
    private void dive() {
        if (--timer >= 0) return;
        routine += 2;
        timer = 0x1E;
        animator.anim = 1;
        colType = 0x32;
        yRadius = 0x10;
        width = 0x10;
        x += diveOffset;
        y += 4;
    }

    /** PohBeeObject_1_Routine10 (:181-218): the missile, one FindObjSlot slot. */
    private void fire() {
        if (--timer >= 0) return;
        routine += 2;
        timer = 0x1E;
        var objects = services().objectManager();
        if (objects == null) return;
        boolean right = (spriteFlip & 1) != 0;
        objects.addDynamicObject(new ObjWwzPohMissile(x + (right ? 7 : -7), y + 0x17, getSpawn().objectId(), spriteFlip,
                right ? 0x20000 : -0x20000, tileWord));
        if (renderFlagOnScreen()) services().playSfx(0xA0);            // tst.b obj.sprite_flags(a0); bpl.s
    }

    /** PohBeeObject_1_Routine12 (:222-237). */
    private void recover() {
        if (--timer >= 0) return;
        routine = 2;
        returnDelay = 0x3C;
        animator.anim = 0;
        colType = 0x31;
        yRadius = 0x0C;
        width = 0x18;
        x -= diveOffset;
        y -= 4;
    }

    @Override
    protected String sheetKey() {
        return smallSprite ? SharedObjectArt.WWZ_POH_BEE_2 : SharedObjectArt.WWZ_POH_BEE_1;
    }
}
