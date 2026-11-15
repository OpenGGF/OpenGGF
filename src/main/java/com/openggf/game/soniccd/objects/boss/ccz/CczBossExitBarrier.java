package com.openggf.game.soniccd.objects.boss.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ReevaluatingSolidObject;

/**
 * An exit barrier, object $3F ({@code ExitBarrierObject}, objects/r3/boss.asm:1217-1284; index entry $3E,
 * r3/object_index_3.asm:66): a $48-wide top-solid platform that slides out ({@code ExitBarrierAnims}) until its
 * frame reaches the target, 7 for a full barrier (the machine's four steps up the arena, routine 0) or 5 for the
 * partial one the entry barrier leaves behind the player (routine 6). While extending and after, it is top-solid
 * ({@code TopSolidObject}) whenever the last frame drew it on screen. It is never deleted.
 */
final class CczBossExitBarrier extends CczBossPiece implements ReevaluatingSolidObject {
    /** ExitBarrierObject is index entry $3E (r3/object_index_3.asm:3-66). */
    static final int ID = 0x3F;

    /** {@code exit_barrier.target_frame} (var_2a). */
    int targetFrame;
    /** {@code boss_flags} as the machine wrote it with this barrier's spawn (BossMachine_TrySpawnFinalBarrier), or 0. */
    int bossFlags;

    /** A barrier at ({@code x}, {@code y}); {@code partial} starts it in routine 6 ({@code ExitBarrier_InitPartial}). */
    CczBossExitBarrier(int x, int y, boolean partial) {
        super(ID, x, y, "CczBossExitBarrier");
        if (partial) routine = 6;                                   // move.b #6,obj.routine(a1) (objects/r3/boss.asm:151)
    }

    private CczBossExitBarrier(ObjectSpawn spawn) {
        super(spawn, "CczBossExitBarrier");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {
                targetFrame = 7;                                    // ExitBarrier_InitFull
                initCommon();
                extend();
            }
            case 6 -> {
                targetFrame = 5;                                    // ExitBarrier_InitPartial
                routine = 0;
                initCommon();
                extend();
            }
            case 2 -> extend();
            default -> solid();                                     // cmpi.b #4,obj.routine(a0); beq.w ExitBarrier_Solid
        }
    }

    /** ExitBarrier_InitCommon (objects/r3/boss.asm:1245-1252). */
    private void initCommon() {
        routine += 2;
        priority = 3;
        tileWord = CczBossArt.EXIT_TILE;
        width = 0x48;
        yRadius = 0x10;
    }

    /** ExitBarrier_Extend (objects/r3/boss.asm:1254-1265). */
    private void extend() {
        routine += animate(CczBossCatalogue.offset(payload(), "exitBarrierAnimations"));
        if (mapFrame == targetFrame) routine += 2;
        solid();
    }

    /** ExitBarrier_Solid (objects/r3/boss.asm:1267-1274). */
    private void solid() {
        if (renderFlagOnScreen()) solidObject(Entry.TOP);
    }

    @Override protected boolean drawn() { return true; }

    @Override String sheet() { return CczBossArt.EXIT; }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new CczBossExitBarrier(spawn);
    }
}
