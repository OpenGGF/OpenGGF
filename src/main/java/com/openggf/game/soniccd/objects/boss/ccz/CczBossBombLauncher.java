package com.openggf.game.soniccd.objects.boss.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * The bomb launcher under the machine, object $3D ({@code BombLauncherObject}, objects/r3/boss.asm:851-967; index
 * entry $3C, r3/object_index_3.asm:64). It sits $20 px below the machine and is deleted at the machine's first hit.
 * Before the first barrier, while the player is above Y $400, every 120th frame it drops a bomb $3E $C px to one
 * side and 8 px down, with a random X speed below $400 ({@code Random}) and Y speed $300, alternating sides (left
 * first), and plays its four-step firing frames ({@code BombLauncher_FiringFrames}) two frames each.
 */
final class CczBossBombLauncher extends CczBossPiece {
    /** BombLauncherObject is index entry $3C (r3/object_index_3.asm:3-64). */
    static final int ID = 0x3D;
    /** cmpi.w #$78,bomb_launcher.fire_timer(a0) (objects/r3/boss.asm:893). */
    static final int FIRE_INTERVAL = 0x78;

    /** {@code bomb_launcher.parent_link} (var_34). */
    CczBossMachine machine;
    /** {@code bomb_launcher.fire_timer} (var_2a, word). */
    int fireTimer;
    /** {@code obj.anim_index}: the firing step; 4 keeps the next shot on the right. */
    int step;
    /** {@code obj.anim_timer}. */
    int stepTimer;

    CczBossBombLauncher(int x, CczBossMachine machine) {
        super(ID, x, 0, "CczBossBombLauncher");
        this.machine = machine;
    }

    private CczBossBombLauncher(ObjectSpawn spawn) {
        super(spawn, "CczBossBombLauncher");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (machine == null) return;
        setYWord(machine.yWord() + 0x20);                           // move.w obj.y(a2),obj.y(a0); addi.w #$20
        if (machine.phase != 0) {                                   // tst.b boss.phase(a2); bne.s .Delete
            machine = null;
            deleteObject();
            return;
        }
        switch (routine) {
            case 0 -> {
                // BombLauncher_Init (objects/r3/boss.asm:877-885), falling into BombLauncher_WaitToFire.
                routine += 2;
                priority = 4;
                width = 0x18;
                yRadius = 0x10;
                tileWord = CczBossArt.BOMB_TILE;
                waitToFire(player);
            }
            case 2 -> waitToFire(player);
            case 4 -> animateFiring();
            default -> { }
        }
    }

    /** BombLauncher_WaitToFire (objects/r3/boss.asm:887-928). */
    private void waitToFire(PlayableEntity player) {
        if (player == null || (short) player.getCentreY() >= 0x400) return;
        if (machine.attackCount != 0) return;
        fireTimer = (fireTimer + 1) & 0xFFFF;
        if ((short) fireTimer < FIRE_INTERVAL) return;
        // .Launch.
        fireTimer = 0;
        routine = 4;
        CczBossBomb bomb = new CczBossBomb(x, (y + 8) & 0xFFFF);
        if (!spawnFirst(bomb)) return;                              // bsr.w SpawnObject; bne.w .LaunchEnd: no Random
        int speed = (services().rng().nextRaw() & 0x7FFF) % 0x400;   // jsr Random; andi.l #$7FFF; divs.w #$400; swap
        int offset = 0xC;
        if (step == 0) {                                            // tst.b obj.anim_index(a0); bne.s .SetVelocity
            speed = -speed;
            offset = -offset;
        }
        bomb.launch(offset, speed, 0x300);
    }

    /** BombLauncher_AnimateFiring (objects/r3/boss.asm:932-959). */
    private void animateFiring() {
        stepTimer = (stepTimer - 1) & 0xFF;
        if ((byte) stepTimer > 0) return;                           // subq.b #1; bgt.s .End
        int next = (step + 1) & 0xFF;
        if (next == 4) {
            routine = 2;                                            // .ReturnToWaiting
        } else if (next == 8) {
            next = 0;                                               // .RestartSequence
            routine = 2;
        } else {
            stepTimer = 2;
        }
        step = next;
        CdStageImage image = stageImage();
        CdStagePayload payload = payload();
        if (image != null && payload != null) mapFrame = CczBossCatalogue.launcherFrame(image, payload, step);
    }

    @Override protected boolean drawn() { return machine != null; }

    @Override String sheet() { return CczBossArt.LAUNCHER; }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new CczBossBombLauncher(spawn);
    }
}
