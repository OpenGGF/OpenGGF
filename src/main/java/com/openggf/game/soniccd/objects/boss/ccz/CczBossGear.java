package com.openggf.game.soniccd.objects.boss.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * The gear beside an entry barrier or the machine, object $32 ({@code GearObject}, objects/r3/boss.asm:820-847;
 * the ID is its index entry's position plus one, r3/object_index_3.asm:53). Its host allocates it: the entry
 * barrier $30 px to its left, the machine $40 px to its left. Every frame it takes its host's Y and, while the host's
 * {@link #MOVING} bit is set, animates ({@code GearAnims}); once its own {@link #DONE} bit is set (the host's
 * explosions ended) it neither moves nor draws. The machine sets its animation (1 while tracking the player, 0 when
 * the fight starts).
 */
final class CczBossGear extends CczBossPiece {
    /** GearObject is index entry $31 (r3/object_index_3.asm:3-53). */
    static final int ID = 0x32;

    /** {@code gear.parent_link} (var_34): the entry barrier or the machine. */
    CczBossPiece host;

    CczBossGear(int x, int y, CczBossPiece host) {
        super(ID, x, y, "CczBossGear");
        this.host = host;
    }

    private CczBossGear(ObjectSpawn spawn) {
        super(spawn, "CczBossGear");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            // GearObject (objects/r3/boss.asm:821-829).
            routine += 2;
            priority = 3;
            width = 0x10;
            yRadius = 0x10;
            tileWord = CczBossArt.BOSS_TILE;
        }
        if (has(DONE)) {                                            // btst #0,gear.flags(a0); bne.s .End: no draw
            hidden = true;
            return;
        }
        hidden = false;
        if (host != null) {
            y = host.yWord() & 0xFFFF;                                           // move.w obj.y(a1),obj.y(a0) (word)
            if (host.has(MOVING)) animate(CczBossCatalogue.offset(payload(), "gearAnimations"));
        }
    }

    private boolean hidden;

    @Override protected boolean drawn() { return !hidden; }

    /** {@code move.b #n,$1C(a1)}: the machine's write to the gear's {@code anim_id}. */
    void setAnim(int value) { anim = value & 0xFF; }

    @Override String sheet() { return CczBossArt.GEAR; }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new CczBossGear(spawn);
    }
}
