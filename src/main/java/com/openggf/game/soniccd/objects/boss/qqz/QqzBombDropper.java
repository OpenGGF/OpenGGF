package com.openggf.game.soniccd.objects.boss.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * The bomb dropper on the booth's arm, object $37 ({@code BombDropperObject}, objects/r5/boss.asm:589-755), allocated
 * by Eggman. Once the booth leaves routine 2 it slides right $1E000 a frame to X $DC0 and waits 180 frames (deleted
 * once the booth's wear is $D2 or less); then FM $CB, Eggman's pose frame 1, a large bomb $18 below, and it slides left
 * $C000 a frame to X $DA0 and on until the player is at or right of it or it reaches X $CE0. There FM $C4 and Eggman's
 * frame 0; 30 frames later it lets the bomb go (Eggman's frame 1), 90 frames after that Eggman's frame 0 and it slides
 * back right. Eggman's frame is written only while Eggman's animation ID is 0.
 */
final class QqzBombDropper extends QqzBossPiece {
    /** FM IDs: $CB bomb out, $C4 stop (objects/r5/boss.asm:660, 698). */
    static final int FM_BOMB = 0xCB, FM_STOP = 0xC4;

    /** var_2e: Eggman. */
    QqzBossEggman eggman;

    QqzBombDropper(int x, int y, QqzBossEggman eggman) {
        super(0x37, x, y, "QqzBombDropper");
        this.eggman = eggman;
    }

    private QqzBombDropper(ObjectSpawn spawn) {
        super(spawn, "QqzBombDropper");
    }

    @Override String sheet() { return QqzBossArt.DROPPER; }

    private QqzBossBooth booth() {
        return eggman == null ? null : eggman.booth;              // movea.w obj.var_2e(a0),a1; movea.w $2E(a1),a1
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        skipDraw = false;
        switch (routine) {
            case 0 -> {
                init();
                waitForFight();
            }
            case 2 -> waitForFight();
            case 4 -> slideOut();
            case 6 -> waitToDrop();
            case 8 -> slideBack();
            case 0xA -> seekPlayer(player);
            case 0xC -> release();
            default -> { }
        }
        if (skipDraw) return;
        animate(QqzBossCatalogue.offset(payload(), "dropperAnimations"));   // AnimateObject, then DrawObject
    }

    /** BombDropperObject_0_Routine0 (objects/r5/boss.asm:611-618). */
    private void init() {
        routine = 2;
        priority = 4;
        width = 0x10;
        yRadius = 0xC;
        tileWord = QqzBossArt.EGGMAN_TILE;
    }

    /** BombDropperObject_0_Routine2 (objects/r5/boss.asm:620-630). */
    private void waitForFight() {
        QqzBossBooth booth = booth();
        if (booth != null && booth.routine() == 2) return;
        routine = 4;
        slideOut();
    }

    /** BombDropperObject_0_Routine4 (objects/r5/boss.asm:632-642). */
    private void slideOut() {
        if (xWord() >= 0xDC0) {
            routine = 6;
            return;
        }
        addXLong(0x1E000);
    }

    /** BombDropperObject_0_Routine6 and loc_20B984 (objects/r5/boss.asm:646-667). */
    private void waitToDrop() {
        QqzBossBooth booth = booth();
        if (booth != null && (short) booth.wear <= 0xD2) {
            deleteAndSkip();
            releaseBombs();
            return;
        }
        timer = (timer + 1) & 0xFF;
        if (timer != 0xB4) return;
        timer = 0;
        fmSound(FM_BOMB);
        routine = 8;
        eggmanFrame(1);
        // sub_20C386 (objects/r5/boss.asm:1717-1728): the large bomb $18 below; its var_2e is this.
        spawn(new QqzLargeBomb(x, y + 0x18, this));
        setPose(1);
    }

    /** BombDropperObject_0_Routine8 (objects/r5/boss.asm:671-681). */
    private void slideBack() {
        addXLong(-0xC000);
        if (xWord() <= 0xDA0) routine = 0xA;
    }

    /** BombDropperObject_0_RoutineA (objects/r5/boss.asm:685-702). */
    private void seekPlayer(PlayableEntity player) {
        int px = player == null ? 0 : (short) player.getCentreX();
        if (px < xWord() && xWord() > 0xCE0) {
            addXLong(-0xC000);
            return;
        }
        fmSound(FM_STOP);
        eggmanFrame(0);
        routine = 0xC;
    }

    /** BombDropperObject_0_RoutineC (objects/r5/boss.asm:706-729). */
    private void release() {
        timer = (timer + 1) & 0xFF;
        if (timer == 0x1E) {
            eggmanFrame(1);                                         // loc_20BA1E
            set(RELEASE);
            setPose(0);
        } else if (timer == 0x78) {
            timer = 0;                                              // loc_20BA0E
            routine = 4;
            eggmanFrame(0);
        }
    }

    /**
     * The bombs still in flight keep this slot in their {@code var_2e}; once it is cleared their {@code btst #2} on it
     * reads 0, which a null link models ({@link QqzBomb#dropperBeaten}). No bomb is attached when the dropper is
     * deleted (it waits in routine 6 with the last bomb released).
     */
    private void releaseBombs() {
        var objects = services().objectManager();
        if (objects == null) return;
        for (var object : objects.getActiveObjects()) {
            if (object instanceof QqzBomb bomb && bomb.dropper == this) bomb.dropper = null;
        }
    }

    /** loc_20BA30 / loc_20BA48 (objects/r5/boss.asm:733-755): Eggman's pose 0 at {@code frame}, while its anim is 0. */
    private void eggmanFrame(int frame) {
        if (eggman == null || eggman.anim() != 0) return;
        eggman.setPose(0);
        eggman.setMapFrame(frame);
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new QqzBombDropper(spawn);
    }
}
