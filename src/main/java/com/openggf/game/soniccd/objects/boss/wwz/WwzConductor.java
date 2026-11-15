package com.openggf.game.soniccd.objects.boss.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * A conductor, object $3E ({@code ConductorObject}, objects/r6/boss.asm:1385-1588), placed by the act 3 layout at
 * both arena walls, one per floor ({@code subtype_2} 5, 6, 7 bottom to top; r6/stage_object_maps_3.asm). It hurts a
 * player within 8 pixels of its Y while {@code bossFight}'s bit for its floor is set (ConductorCheckPlayerDamage).
 * <ul>
 *   <li>Subtype 0 (bottom left): tracks the player's approach, setting {@code bossFlags} bits 6 and 5 from X $930 and
 *       $9B0 (clearing 7 and 5 first), and from X $A60 slides down 64 pixels and holds the player right of X $A00.</li>
 *   <li>Subtype 1 (top right): holds the player left of X $B80 until Eggman's defeat sends it up 96 pixels.</li>
 *   <li>Subtype $80/$81: second sprite set, damage only, not solid.</li>
 * </ul>
 * Subtypes 0 and 1 are solid at an offset; {@code btst #0,obj.subtype(a0); beq.w *+4} branches to the next instruction,
 * so both run the solid call ({@code FixBugs = 0}).
 */
final class WwzConductor extends WwzBossPiece implements ReevaluatingSolidObject {
    static final int ID = 0x3E;
    /** var_34: the travel limit; var_36 / var_38: the solid box offset. */
    private int travel;
    private int solidDx;
    private int solidDy;

    WwzConductor(ObjectSpawn spawn) {
        super(spawn, "WwzConductor");
    }

    private int subtype() { return getSpawn().subtype() & 0xFF; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AbstractPlayableSprite sonic = sonic(player);
        switch (routine) {
            case 0 -> init();
            case 2 -> trackPlayer(sonic);
            case 4 -> {                                      // ConductorClampPlayerRight (objects/r6/boss.asm:1555-1559)
                if (sonic != null && (short) sonic.getCentreX() > 0xB80) {
                    NativePositionOps.writeXPosPreserveSubpixel(sonic, 0xB80);
                }
                checkPlayerDamage(sonic);
            }
            case 6 -> {                                      // ConductorClampPlayerLeft (:1536-1540)
                if (sonic != null && (short) sonic.getCentreX() < 0xA00) {
                    NativePositionOps.writeXPosPreserveSubpixel(sonic, 0xA00);
                }
                checkPlayerDamage(sonic);
            }
            case 8 -> moveVertical();
            case 0xA -> checkPlayerDamage(sonic);
            default -> { }
        }
        if ((subtype() & 0x80) != 0) return;                 // tst.b obj.subtype(a0); bmi.s ConductorDraw
        addX(solidDx);                                       // ConductorSolidCollision (:1394-1404)
        addY(solidDy);
        solidObject(Entry.SOLID);
        addX(-solidDx);
        addY(-solidDy);
    }

    /** ConductorInit / ConductorInitLower / ConductorInitAlternate (objects/r6/boss.asm:1422-1463). */
    private void init() {
        priority = 2;
        tileWord = WwzBossArt.CONDUCTOR_TILE;
        mapFrame = subtype() & 0xF;
        width = 8;
        if ((subtype() & 0x80) != 0) {
            yRadius = 0x10;
            routine = 0xA;
            return;
        }
        if ((subtype() & 1) != 0) {
            yVel = -0x200;
            routine = 4;
            travel = (short) 0xA000;
            solidDx = 0x14;
            solidDy = -0x1A;
            yRadius = 0x50;
            return;
        }
        yRadius = 0x10;
        yVel = 0x200;
        routine = 2;
        travel = 0x4000;
        solidDx = -0x14;
        solidDy = -0xA;
    }

    /** ConductorTrackPlayer / ConductorFinishTracking (objects/r6/boss.asm:1466-1489). */
    private void trackPlayer(AbstractPlayableSprite player) {
        WwzBossRam ram = ram();
        ram.flagBit(objects(), 7, false);
        ram.flagBit(objects(), 5, false);
        int px = player == null ? 0 : (short) player.getCentreX();
        if (px >= 0x930) ram.flagBit(objects(), 6, true);
        if (px >= 0x9B0) ram.flagBit(objects(), 5, true);
        if (px >= 0xA60) routine = 8;
    }

    /** ConductorMoveVertical and its reversals (objects/r6/boss.asm:1482-1515). */
    private void moveVertical() {
        integratePosition();
        timer = (short) (timer + yVel);
        if (travel < 0) {
            if (travel < (short) timer) return;              // ConductorCheckVerticalReverse
            routine = 0xA;                                   // ConductorSwitchToAlternate
        } else {
            if (travel > (short) timer) return;
            routine = 6;                                     // ConductorReverseVertical
        }
        width = 9;                                           // ConductorSetCollisionSize / ConductorSetLargeCollision
        yRadius = (subtype() & 1) != 0 ? 0x10 : 0x20;
    }

    /** ConductorCheckPlayerDamage / ConductorCheckPlayerDistance (objects/r6/boss.asm:1556-1584). */
    private void checkPlayerDamage(AbstractPlayableSprite player) {
        if ((ram().fight() & (1 << (subtype2() & 7))) == 0 || player == null) return;
        int dy = (short) (y - player.getCentreY());
        if (dy < 0) dy = -dy;
        if (dy >= 8 || player.getInvulnerableFrames() != 0 || player.getDead() || ram().playerKilled()) return;
        hurtPlayer(player);
    }

    @Override String sheet() { return (subtype() & 0x80) != 0 ? WwzBossArt.CONDUCTOR2 : WwzBossArt.CONDUCTOR1; }

    @Override public int solidWidth() { return width; }

    @Override public int solidYRadius() { return yRadius; }

    @Override public boolean solidRenderedOnScreen() { return renderFlagOnScreen(); }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new WwzConductor(spawn);
    }
}
