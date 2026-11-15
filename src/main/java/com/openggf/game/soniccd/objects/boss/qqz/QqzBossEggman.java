package com.openggf.game.soniccd.objects.boss.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Eggman in the booth, object $32 ({@code EggmanObject}, objects/r5/boss.asm:3-85), allocated by the booth at its
 * position. Routine 0 allocates the bomb dropper ($80 left, $44 up). Routine 2 rides the booth's Y; once the booth's
 * map frame reaches 4 (its third sink) it loops pose 2 for $30 frames and pose 0 for $15. When the booth gives out it
 * moves to layer 3, takes pose 3, drops 6 pixels, scores 1000 and flies right $12000 a frame until X $E20, where it
 * is deleted. It animates only while its animation ID is non-zero; the booth and the dropper set its poses.
 */
final class QqzBossEggman extends QqzBossPiece {
    /** var_2e: the booth. ({@code var_30}, the dropper, is written by sub_20C304 and never read.) */
    QqzBossBooth booth;

    QqzBossEggman(int x, int y, QqzBossBooth booth) {
        super(0x32, x, y, "QqzBossEggman");
        this.booth = booth;
    }

    private QqzBossEggman(ObjectSpawn spawn) {
        super(spawn, "QqzBossEggman");
    }

    @Override String sheet() { return QqzBossArt.EGGMAN; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        skipDraw = false;
        switch (routine) {
            case 0 -> {
                init();
                ride();
            }
            case 2 -> ride();
            case 4 -> flee();
            default -> { }
        }
        if (skipDraw || isDestroyed()) return;
        // tst.b obj.anim_id(a0); beq.s: AnimateObject only for a non-zero animation.
        if (anim != 0) animate(QqzBossCatalogue.offset(payload(), "eggmanAnimations"));
    }

    /** EggmanObject_0_Routine0 (objects/r5/boss.asm:25-33). */
    private void init() {
        routine = 2;
        priority = 5;
        width = 0x10;
        yRadius = 0x10;
        tileWord = QqzBossArt.EGGMAN_TILE;
        // sub_20C304 (objects/r5/boss.asm:1671-1684): the dropper at X - $80, Y - $44.
        spawn(new QqzBombDropper(x - 0x80, y - 0x44, this));
    }

    /** EggmanObject_0_Routine2 (objects/r5/boss.asm:35-67). */
    private void ride() {
        if (has(BEATEN)) {
            beaten();
            flee();
            return;
        }
        if (booth == null) return;
        setYWord(booth.yWord());
        if ((byte) booth.mapFrame() < 4) return;                  // cmpi.b #4,obj.sprite_frame(a1); bge.s
        if (timer == 0) {
            setPose(2);                              // loc_20B390
            timer = 0x44;
            return;
        }
        timer = (timer - 1) & 0xFF;
        if (timer == 0x14) setPose(0);              // loc_20B39E
    }

    /** loc_20B3A6 (objects/r5/boss.asm:71-79). */
    private void beaten() {
        priority = 3;
        timer = 0;
        routine = 4;
        setPose(3);
        addY(6);
        var game = services().gameState();
        if (game != null) game.addScore(100 * 10);                  // moveq #$64,d0; jsr AddPoints (units of 10)
    }

    /** EggmanObject_0_Routine4 (objects/r5/boss.asm:81-85). */
    private void flee() {
        addXLong(0x12000);
        if (xWord() < 0xE20) return;
        deleteAndSkip();
        // The booth's var_30 now names a cleared slot; it never reads Eggman again (it gave out first).
        if (booth != null) booth.eggman = null;
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new QqzBossEggman(spawn);
    }
}
