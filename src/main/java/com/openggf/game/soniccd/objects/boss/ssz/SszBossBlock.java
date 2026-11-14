package com.openggf.game.soniccd.objects.boss.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ReevaluatingSolidObject;

/**
 * A race gate block, object $2B ({@code BossBlockObject}, objects/r7/boss_block.asm:3-140), placed by the act 3
 * layout: a solid block whose subtype is its frame and size (byte_20EBB4). A nonzero layout {@code subtype_2} makes it
 * a moving gate piece: while {@code bossFlags} bit 7 is set (the race start and the finish, set by Metal Sonic) it moves
 * $20 pixels at 2 a frame, up with bit 7, as many times as the low nibble; between moves, unless bit 5, the frame
 * steps (up with bit 4: 0 to 4 to 5, and 16 pixels up; else down: 5 to 4 to 0, 16 down). The first piece to finish
 * clears {@code bossFlags} bit 7 (stopping any piece still moving) and, without bit 6, deletes itself.
 */
final class SszBossBlock extends SszBossPiece implements ReevaluatingSolidObject {
    static final int ID = 0x2B;

    /** obj.subtype_2: the move bits and count. */
    private int motion;
    /** var_2a: the Y the current move ends at, 0 between moves. */
    private int target;

    SszBossBlock(ObjectSpawn spawn) {
        super(spawn, "SszBossBlock");
    }

    @Override String sheet() { return SszBossArt.BLOCK; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        skipDraw = false;
        if (routine == 0) {                                     // BossBlockObject (:4-20)
            routine = 2;
            priority = 3;
            tileWord = 0x450C;
            mapFrame = getSpawn().subtype() & 0xFF;
            motion = subtype2() & 0xFF;
            resize();
        }
        if (motion != 0 && slide()) return;
        solidObject(ReevaluatingSolidObject.Entry.SOLID);       // SolidObject; contact clears the player's collide_type
    }

    /** sub_20EBC0 (objects/r7/boss_block.asm:49-140); true when the piece deleted itself. */
    private boolean slide() {
        SszBossEggman eggman = SszBossEggman.find(services().objectManager());
        if (eggman == null || (eggman.bossFlags() & 0x80) == 0) return false;
        if (target == 0) {
            int d0 = 0x20, d1 = 0x200;
            if ((motion & 0x80) != 0) {
                d0 = -d0;
                d1 = -d1;
            }
            ySpeed = d1;
            target = (yWord() + d0) & 0xFFFF;
        }
        addYLong((long) (short) ySpeed << 8);                   // loc_20EBF0
        if ((y & 0xFFFF) != target) return false;
        target = 0;                                             // loc_20EC08
        int count = (motion & 0xF) - 1;
        if (count == 0) {                                       // loc_20EC88
            eggman.setBossFlags(eggman.bossFlags() & ~0x80);
            if ((motion & 0x40) != 0) {
                motion = 0;
                return false;
            }
            skipDraw = true;
            deleteObject();                                     // addq.l #4,sp; jmp DeleteObject
            return true;
        }
        motion = (motion & 0xF0) + (count & 0xFF);
        if ((motion & 0x20) != 0) return false;
        if ((motion & 0x10) != 0) {
            mapFrame++;
            if (mapFrame == 1) mapFrame = 4;
            addY(-0x10);
        } else {
            mapFrame--;
            if (mapFrame == 3) mapFrame = 0;
            addY(0x10);
        }
        resize();
        return false;
    }

    /** byte_20EBB4: obj.width, obj.width_2 and obj.height for the frame. */
    private void resize() {
        CdStageImage image = stageImage();
        if (image == null || payload() == null) return;
        int[] size = SszBossCatalogue.blockSize(image, payload(), mapFrame & 0xFF);
        width = size[0];
        yRadius = size[1];
    }

    @Override protected boolean drawn() { return !skipDraw && routine != 0; }

    @Override public boolean isCustomOutOfRange(int cameraX) { return despawnOutOfRange(cameraX); }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new SszBossBlock(spawn);
    }
}
