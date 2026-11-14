package com.openggf.game.soniccd.objects.boss.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Eggman in the machine's cockpit, object $33 ({@code EggmanObject}, objects/r3/boss.asm:720-816; index entry $32,
 * r3/object_index_3.asm:54), allocated by the machine $30 px above it. He takes the machine's Y every frame
 * (routine 2). A hit throws him up at -$5.8 px a frame, falling back under $3000 gravity until he lands in the
 * machine again (routine 4); after the third hit he flies up 4 px a frame to $40 above the machine (routine 6),
 * then right 4 px a frame for $230 px and is deleted (routine 8). He animates ({@code EggmanAnims}) only while the
 * last frame's {@code DrawObject} put him on screen ({@code tst.b obj.sprite_flags(a0); bpl.s .Draw}).
 */
final class CczBossEggman extends CczBossPiece {
    /** EggmanObject is index entry $32 (r3/object_index_3.asm:3-54). */
    static final int ID = 0x33;

    /** {@code eggman.parent_link} (var_34): the machine. */
    CczBossMachine machine;
    /** {@code eggman.target} (var_2e): the initial Y, then the escape's height, then its end X. */
    int target;
    /** {@code eggman.y_speed} (var_30): 16.16. */
    int ySpeed;
    private boolean deleted;

    CczBossEggman(int x, int y, CczBossMachine machine) {
        super(ID, x, y, "CczBossEggman");
        this.machine = machine;
    }

    private CczBossEggman(ObjectSpawn spawn) {
        super(spawn, "CczBossEggman");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> init();
            case 2 -> followMachine();
            case 4 -> reboundToMachine();
            case 6 -> escapeUp();
            case 8 -> escapeRight();
            default -> { }
        }
        if (deleted) return;                                        // addq.l #4,sp; jmp DeleteObject
        if (renderFlagOnScreen()) animate(CczBossCatalogue.offset(payload(), "eggmanAnimations"));
    }

    /** Eggman_Init (objects/r3/boss.asm:744-752), falling into Eggman_FollowBoss. */
    private void init() {
        routine = 2;
        priority = 6;
        width = 0x10;
        yRadius = 0x10;
        tileWord = CczBossArt.BOSS_TILE;
        target = y;
        followMachine();
    }

    /** Eggman_FollowBoss (objects/r3/boss.asm:754-757). */
    private void followMachine() {
        if (machine != null) setYWord(machine.yWord());
    }

    /** Eggman_ReboundToBoss (objects/r3/boss.asm:761-784). */
    private void reboundToMachine() {
        addYLong(ySpeed);
        ySpeed += 0x3000;                                           // addi.l #$3000,eggman.y_speed(a0)
        if (machine == null) return;
        int landing = machine.yWord();
        if (landing > yWord()) return;                              // cmp.w obj.y(a0),d0; bgt.s .End
        setYWord(landing);
        routine = 2;
        if (machine.phase == 2) {
            // .PrepareFinalPhase: the machine shows frame 2; Eggman's tile and animation 3.
            machine.setMapFrame(2);
            tileWord = CczBossArt.ESCAPE_TILE;
            setAnimWord(0x300);
            return;
        }
        setAnimWord(1);                                             // move.w #1,obj.anim_id(a0): anim 0, restart
    }

    /** Eggman_EscapeUp (objects/r3/boss.asm:788-801). */
    private void escapeUp() {
        addYLong(-0x40000);
        if (target < yWord()) return;                               // cmp.w obj.y(a0),d0; blt.s .End
        setAnimWord(0x400);
        priority = 3;
        setYWord(target);
        routine = 8;
        target = (short) (xWord() + 0x230);
    }

    /** Eggman_EscapeRight (objects/r3/boss.asm:805-816). */
    private void escapeRight() {
        addXLong(0x40000);
        if (target > xWord()) return;                               // cmp.w obj.x(a0),d0; bgt.s .End
        deleted = true;
        deleteObject();
    }

    /** {@code move.w #n,obj.anim_id}: the animation byte and the previous-animation byte together. */
    void setAnimWord(int word) {
        anim = (word >> 8) & 0xFF;
        prevAnim = word & 0xFF;
    }

    /** {@code move.b #n,obj.anim_id(a1)}: the animation byte alone. */
    void setAnim(int value) { anim = value & 0xFF; }

    /** The machine's writes for the last hit (BossMachine_HitPhase_Defeated, objects/r3/boss.asm:436-443). */
    void startEscape(int machineY) {
        routine = 6;
        target = (short) (machineY - 0x40);
        tileWord = CczBossArt.ESCAPE_TILE;
        setAnimWord(0x300);
    }

    /** The machine's writes for a hit (BossMachine_HitPhase_RaiseUpperChild, objects/r3/boss.asm:426-429). */
    void throwUp() {
        routine = 4;
        setAnim(1);
        ySpeed = -0x58000;
    }

    @Override String sheet() {
        return tileWord == CczBossArt.ESCAPE_TILE ? CczBossArt.EGGMAN_ESCAPE : CczBossArt.EGGMAN;
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new CczBossEggman(spawn);
    }
}
