package com.openggf.game.soniccd.objects.boss.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseResult;

/**
 * The boss machine, object $35 ({@code BossMachineObject}, objects/r6/boss.asm:671-711), allocated by Eggman. It
 * follows Eggman $10 pixels higher and carries the machine's collision type $3D (written by Eggman); after the defeat
 * Eggman moves it down $10 and on to routine 4, the broken machine: frame 1, tile $2393, width $40, Y radius $10 and
 * solid for the player.
 *
 * <p>Touch: type $3D, status 2, an ordinary enemy type (PlayerObjectCollideEnemy, src/r6/player_object_collide.asm:133-153):
 * a rolling player rebounds at half speed, the type clears and the status drops by one.
 */
final class WwzBossMachine extends WwzBossPiece implements ReevaluatingSolidObject, TouchResponseAttackable {
    static final int ID = 0x35;
    // var_2e: Eggman.
    WwzBossEggman eggman;

    WwzBossMachine(WwzBossEggman eggman) {
        super(ID, 0, 0, "WwzBossMachine");
        this.eggman = eggman;
    }

    private WwzBossMachine(ObjectSpawn spawn) {
        super(spawn, "WwzBossMachine");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {                                      // BossMachineInit (objects/r6/boss.asm:706-715)
                routine = 2;
                priority = 5;
                width = 0x40;
                yRadius = 0x30;
                tileWord = WwzBossArt.PART_TILE;
            }
            case 2 -> {                                      // BossMachineFollowParent (:718-724)
                if (eggman == null) return;
                setXWord(eggman.xWord());
                setYWord(eggman.yWord() - 0x10);
            }
            case 4 -> {                                      // BossMachineSolidCollision (:727-735)
                mapFrame = 1;
                width = 0x40;
                yRadius = 0x10;
                tileWord = WwzBossArt.BROKEN_TILE;
                solidObject(Entry.SOLID);
            }
            default -> { }
        }
    }

    /** Player_TouchEnemy with a nonzero status: clear the type, drop the status (the bounce is the shared one). */
    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        setCollision(0, (colStatus - 1) & 0xFF);
    }

    @Override String sheet() { return routine >= 4 ? WwzBossArt.BROKEN_MACHINE : WwzBossArt.MACHINE; }

    @Override public int solidWidth() { return width; }

    @Override public int solidYRadius() { return yRadius; }

    @Override public boolean solidRenderedOnScreen() { return renderFlagOnScreen(); }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new WwzBossMachine(spawn);
    }
}
