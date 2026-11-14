package com.openggf.game.soniccd.objects.boss.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Eggman's laser, object $2E ({@code BossLaserObject}, objects/r7/boss.asm:1453-1620). Subtype 0 is a link of the
 * four-link beam (sub_20CFFA): each follows the one above it (the first Eggman) at its offset, mirrored by the one
 * above's facing, and copies its {@code var_2c} bits 5 (hurts) and 6 (shown); shown, it animates and hurts with
 * $BE while bit 5 is set. The last link spits an impact every 11 frames and a piece of falling debris ($37) every
 * 10. Subtype 1 is that impact: it follows the last link for 15 frames, then stays 15 more. Subtype 2 is the blast
 * where Metal Sonic breaks (sub_20D070), 30 frames. Every subtype is deleted when the object it follows is gone.
 */
final class SszBossLaser extends SszBossPiece {
    /** BossLaserObject is index entry $2D (r7/object_index_3.asm:3-49). */
    static final int ID = 0x2E;

    /** var_2e: the object followed (Eggman, the link above, the last link, or Metal Sonic). */
    SszBossPiece upLink;
    /** var_3a: Eggman, whose X speed the debris takes (the beam links only). */
    SszBossEggman eggmanOwner;
    /** var_36 / var_38: the offset from the object followed. */
    private int offsetX;
    private int offsetY;
    /** obj.subtype: 0 beam, 1 impact, 2 Metal Sonic's blast. */
    private int kind;
    /** obj.subtype_2: the last link (spits impacts and debris). */
    private boolean last;
    /** var_2a / var_2b bytes: the last link's impact and debris counts (the impacts' life count is var_2a too). */
    private int impactCount;
    private int debrisCount;

    SszBossLaser(SszBossEggman eggman, SszBossPiece above, int offsetX, int offsetY, boolean last) {
        super(ID, 0, 0, "SszBossLaser");
        this.eggmanOwner = eggman;
        this.upLink = above == null ? eggman : above;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.last = last;
    }

    private SszBossLaser(SszBossPiece followed, int kind, int offsetX, int offsetY, int x, int y) {
        super(ID, x, y, "SszBossLaser");
        this.upLink = followed;
        this.kind = kind;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    private SszBossLaser(ObjectSpawn spawn) {
        super(spawn, "SszBossLaser");
    }

    /** sub_20D04A (objects/r7/boss.asm:1993-2003): an impact following {@code link}, 2 forward and $10 below. */
    static SszBossLaser impact(SszBossLaser link) {
        return new SszBossLaser(link, 1, 2, 0x10, 0, 0);
    }

    /** sub_20D070 (objects/r7/boss.asm:2007-2021): a blast at Metal Sonic. */
    static SszBossLaser metalBlast(SszMetalSonic metal) {
        return new SszBossLaser(metal, 2, 0, 0, metal.getX(), metal.getY());
    }

    @Override String sheet() { return kind == 0 ? SszBossArt.LASER : SszBossArt.IMPACT; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        skipDraw = false;
        if (upLink == null || upLink.isDestroyed()) {           // tst.b (a2); beq.s: jmp DeleteObject
            upLink = null;
            skipDraw = true;
            deleteObject();
            return;
        }
        switch (routine) {
            case 0 -> init();
            case 2 -> beam();
            case 4 -> {                                         // BossLaserObject_0_Routine4 (:1589-1599)
                follow();
                countDown();
                if (step != 0) {
                    step = 0;
                    routine = 6;
                    impactCount = 0xF;
                }
                animateObject(table("laserImpactAnimations"));
            }
            case 6 -> {                                         // BossLaserObject_0_Routine6 (:1603-1607)
                countDown();
                if (step != 0) {
                    skipDraw = true;
                    deleteObject();
                    return;
                }
                animateObject(table("laserImpactAnimations"));
            }
            default -> { }
        }
    }

    /** BossLaserObject_0_Routine0 (objects/r7/boss.asm:1477-1509): no draw on this frame. */
    private void init() {
        skipDraw = true;
        if (kind == 0) {
            priority = 2;
            width = 8;
            yRadius = 0x10;
            tileWord = 0x2300;
            routine = 2;
            return;
        }
        priority = 1;
        width = 0x10;
        yRadius = 0x10;
        tileWord = 0x680;
        if (kind == 2) {
            routine = 6;
            impactCount = 0x1E;
        } else {
            routine = 4;
            impactCount = 0xF;
        }
    }

    /** BossLaserObject_0_Routine2 (objects/r7/boss.asm:1513-1545). */
    private void beam() {
        setCollision(0, 0);                                     // clr.w obj.collide_type(a0)
        if (upLink.has(SszBossEggman.LASER_HURTS)) setCollision(0xBE, 2);
        if (!follow()) {                                        // loc_20CC08: clr.w obj.var_2a(a0); rts
            impactCount = 0;
            debrisCount = 0;
            skipDraw = true;
            return;
        }
        if (last) {
            impactCount = (impactCount - 1) & 0xFF;             // subq.b #1,obj.var_2a(a0); bcc.s
            if (impactCount == 0xFF) {
                spawnAfter(impact(this));
                impactCount = 0xA;
            }
            debrisCount = (debrisCount - 1) & 0xFF;
            if (debrisCount == 0xFF) {
                spawnAfter(new SszBossDebris(this, eggmanOwner));   // sub_20D0A8
                debrisCount = 9;
            }
        }
        animateObject(table("laserAnimations"));
    }

    /** sub_20CC0E (objects/r7/boss.asm:1549-1585): position, facing and bits 5-6 from the object followed; true when shown. */
    private boolean follow() {
        setXWord(upLink.xWord());
        setYWord(upLink.yWord());
        addY(offsetY);
        if (upLink.facingLeft()) {
            status |= 1;
            addX(-offsetX);
        } else {
            status &= ~1;
            addX(offsetX);
        }
        clear(SszBossEggman.LASER_HURTS);
        if (upLink.has(SszBossEggman.LASER_HURTS)) set(SszBossEggman.LASER_HURTS);
        if (upLink.has(SszBossEggman.LASER_SHOWN)) {
            set(SszBossEggman.LASER_SHOWN);
            return true;
        }
        clear(SszBossEggman.LASER_SHOWN);
        return false;
    }

    /** MetalSonicObject_2_Routine4_0 on the byte var_2a: at zero, var_2d + 1. */
    private void countDown() {
        impactCount = (impactCount - 1) & 0xFF;
        if (impactCount == 0) step = (step + 1) & 0xFF;
    }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new SszBossLaser(spawn);
    }
}
