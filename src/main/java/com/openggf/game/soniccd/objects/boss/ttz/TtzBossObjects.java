package com.openggf.game.soniccd.objects.boss.ttz;

import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.boss.CdBossFragment;
import com.openggf.game.soniccd.objects.boss.CdBossLevelEvents;
import com.openggf.game.soniccd.objects.boss.CdCapsule;

import java.io.IOException;

/**
 * Tidal Tempest's boss fragment for R43C and R43D. Act 3's index (r4/object_index_3.asm:3-80; ID = entry position
 * + 1) puts the capsule at $15, the boss door at $47, the chase Egg Mobile at $4A and Eggman at $4C, which the layout
 * places; the chase Eggman $4B, the Egg Mobile $4D, the missiles $4E and the bubbles $4F are only allocated. The act 3
 * events are {@code R43Events}.
 */
public final class TtzBossObjects implements CdBossFragment {
    /** CapsuleObject is entry $14 of the act 3 index (r4/object_index_3.asm:3-24). */
    static final int CAPSULE_ID = 0x15;

    @Override
    public void register(Registrar registrar) {
        registrar.object(TtzBossDoor.ID, "Boss door", (spawn, r) -> new TtzBossDoor(spawn));
        registrar.object(TtzChaseMobile.ID, "Tidal Tempest chase Egg Mobile", (spawn, r) -> new TtzChaseMobile(spawn));
        registrar.object(TtzBossEggman.ID, "Tidal Tempest boss", (spawn, r) -> new TtzBossEggman(spawn));
        registrar.object(CAPSULE_ID, "Flower capsule", (spawn, r) -> new CdCapsule(spawn));
    }

    @Override
    public void registerArt(CdObjectArtProvider art) throws IOException {
        TtzBossArt.register(art);
        if (TtzBossCatalogue.offset(art.payload(), "eggmanMappings") >= 0) {
            CdCapsule.registerArt(art, TtzBossArt.capsuleVram(art));
        }
    }

    /**
     * R43Events (common/events.asm:348-366): {@code CheckBossStart} from player X $AF8 with the bottom boundary $4C0;
     * before that, once {@code boss_flags} is set, the bottom boundary and its target are $4F0.
     */
    @Override
    public void levelEvents(CdBossLevelEvents.Context context) {
        if (CdBossLevelEvents.checkBossStart(context, 0xAF8, 0x4C0)) return;
        if (context.level() == null || context.level().flags().bossFlags == 0) return;
        context.camera().setMaxY((short) 0x4F0);                        // bottom_bound and target_bottom_bound
    }

    @Override public boolean hostsLevelEvents() { return true; }
}
