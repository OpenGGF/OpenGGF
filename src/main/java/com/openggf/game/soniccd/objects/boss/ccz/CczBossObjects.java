package com.openggf.game.soniccd.objects.boss.ccz;

import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.boss.CdBossFragment;
import com.openggf.game.soniccd.objects.boss.CdBossLevelEvents;
import com.openggf.game.soniccd.objects.boss.CdCapsule;

import java.io.IOException;

/**
 * Collision Chaos's boss fragment for R33C and R33D. Act 3's index (r3/object_index_3.asm:3-66; ID = entry position
 * + 1) puts the capsule at $15, the entry barrier at $30, the machine at $31, its gear $32 and Eggman $33, the bomb
 * launcher $3D, the bomb $3E and the exit barrier $3F; the layout places the entry barrier, the machine and the
 * capsule, the others are allocated by them. The drain $36 is the zone's. The act 3 events are {@code R33Events}.
 */
public final class CczBossObjects implements CdBossFragment {
    /** CapsuleObject is index entry $14 (r3/object_index_3.asm:3-24). */
    static final int CAPSULE_ID = 0x15;
    /** R33Events: move.w #$510,target_bottom_bound (common/events.asm:153-157). */
    static final int BOTTOM = 0x510;
    /** R33EventsSetBossBounds (common/events.asm:161-163): move.w #$60,d1 before the branch to the bounds routine. */
    static final int BOSS_BOTTOM = 0x60;

    @Override
    public void register(Registrar registrar) {
        registrar.object(CczBossEntryBarrier.ID, "Collision Chaos boss entry barrier", (spawn, r) -> new CczBossEntryBarrier(spawn));
        registrar.object(CczBossMachine.ID, "Collision Chaos boss", (spawn, r) -> new CczBossMachine(spawn));
        registrar.object(CAPSULE_ID, "Flower capsule", (spawn, r) -> new CdCapsule(spawn));
    }

    @Override
    public void registerArt(CdObjectArtProvider art) throws IOException {
        CczBossArt.register(art);
        if (CczBossArt.stage(art.payload())) CdCapsule.registerArt(art, CczBossArt.escapeVram(art));
    }

    /**
     * R33Events (common/events.asm:153-163; R3Events dispatches act 3 to it, :131-143): while {@code boss_flags} is
     * clear the bottom boundary target is $510; once set, {@code SetBossBounds} with $60 (no start check: R33 branches
     * to SetBossBounds itself, which {@link CdBossLevelEvents#checkBossStart} reaches for any player X when the start
     * X is $8000, the lowest signed word).
     */
    @Override
    public void levelEvents(CdBossLevelEvents.Context context) {
        if (bossFlags(context) == 0) {
            context.camera().setMaxYTarget((short) BOTTOM);
            return;
        }
        CdBossLevelEvents.checkBossStart(context, -0x8000, BOSS_BOTTOM);
    }

    @Override public boolean hostsLevelEvents() { return true; }

    private static int bossFlags(CdBossLevelEvents.Context context) {
        if (context.objects() == null) return 0;
        for (var object : context.objects().getActiveObjects()) {
            if (object.isDestroyed()) continue;
            if (object instanceof CczBossMachine machine && machine.bossFlags() != 0) return machine.bossFlags();
            if (object instanceof CczBossExitBarrier barrier && barrier.bossFlags != 0) return barrier.bossFlags;
        }
        return 0;
    }
}
