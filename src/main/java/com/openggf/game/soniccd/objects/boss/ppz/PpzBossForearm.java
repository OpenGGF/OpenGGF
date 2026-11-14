package com.openggf.game.soniccd.objects.boss.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * A forearm, object $31 ({@code BossForearmObject}, objects/r1/boss.asm:1543-1693): width $20, height 8, tile
 * $2359, mappings BossForearmSprites1 (front) or 2 (back), set by the spawner. It sits at (-$24 + reach, 0) from
 * its arm and tilts its Y offset one pixel a frame between -8 and 8: down when the player is below it or in the
 * fast stride ({@link #FAST}), up otherwise. Routines 6/8 retract/extend the reach by half a pixel, A extends to
 * 8 with frame 1, 4 drops it with an explosion every seventh frame.
 */
final class PpzBossForearm extends PpzBossPiece {
    PpzBossArm armPart;
    PpzBossBumper bumper;
    private boolean backSheet;

    PpzBossForearm(int x, int y, PpzBossArm armPart, boolean backSheet) {
        super(0x31, x, y, "PpzBossForearm");
        this.armPart = armPart;
        this.backSheet = backSheet;
    }

    private PpzBossForearm(ObjectSpawn spawn) {
        super(spawn, "PpzBossForearm");
    }

    @Override String sheet() { return backSheet ? PpzBossArt.FOREARM2 : PpzBossArt.FOREARM1; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {
                // BossForearmObject_0_Routine0 (objects/r1/boss.asm:1562-1568), falling into routine 2.
                width = 0x20;
                yRadius = 8;
                tileWord = PpzBossArt.PART_TILE;
                routine = 2;
                aim(player);
            }
            case 2 -> aim(player);
            case 4 -> drop();
            case 6 -> {
                // BossForearmObject_0_Routine6 (objects/r1/boss.asm:1603-1609).
                if (word(offsetX) > 0) offsetX -= 0x8000;
                aim(player);
            }
            case 8 -> {
                // BossForearmObject_0_Routine8 (objects/r1/boss.asm:1613-1619).
                if (word(offsetX) < 0x10) offsetX += 0x8000;
                aim(player);
            }
            case 0xA -> reach();
            default -> { }
        }
    }

    /** BossForearmObject_0_Routine2 (objects/r1/boss.asm:1570-1599). */
    private void aim(PlayableEntity player) {
        x = (armPart.getX() - 0x24 + word(offsetX)) & 0xFFFF;
        y = armPart.getY();
        boolean down = has(FAST) || (player != null && (short) player.getCentreY() > yWord());
        if (down) {
            if (word(offsetY) < 8) offsetY += 0x10000;
        } else if (word(offsetY) > -8) {
            offsetY -= 0x10000;
        }
        addY(word(offsetY));
    }

    /** BossForearmObject_0_RoutineA (objects/r1/boss.asm:1623-1649). */
    private void reach() {
        if (word(offsetX) < 8) offsetX += 0x8000;
        else mapFrame = 1;
        x = (armPart.getX() - 0x24 + word(offsetX)) & 0xFFFF;
        y = armPart.getY();
        if (word(offsetY) < 8) offsetY += 0x10000;
        addY(word(offsetY));
    }

    /** BossForearmObject_0_Routine4 (objects/r1/boss.asm:1653-1693). */
    private void drop() {
        if (!testAndSet(STARTED)) {
            phase = 1;
            angle = 0;
            stepX = 0;
            stepY = 0x10000;
            bumper.setRoutine(4);
        } else {
            armPart = null;                                         // links the fall no longer reads (rewind ids)
            bumper = null;
            stepX += -0x620;
            stepY += 0x1220;
        }
        if (fallAndCheck()) return;
        angle = (angle + 1) & 0xFF;
        if (angle % 7 == 0) explode(x, y);                      // sub_20C960 (objects/r1/boss.asm:1325-1336)
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new PpzBossForearm(spawn);
    }
}
