package com.openggf.game.soniccd.objects.boss.ppz;

import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.boss.CdBossFragment;
import com.openggf.game.soniccd.objects.boss.CdBossLevelEvents;
import com.openggf.game.soniccd.objects.boss.CdCapsule;

import java.io.IOException;

/**
 * Palmtree Panic's boss fragment for R13C and R13D. Act 3's index (Palmtree Panic/Object Index (Act 3).asm:7-71;
 * ID = entry position + 1) puts the capsule at $15 and Eggman at $2A; the parts $2B-$32 are only allocated by
 * Eggman. The act 3 events are {@code LevEvents_PPZ3}.
 */
public final class PpzBossObjects implements CdBossFragment {
    /** ObjCapsule is entry $14 of the act 3 index (Palmtree Panic/Object Index (Act 3).asm:7-28). */
    static final int CAPSULE_ID = 0x15;

    @Override
    public void register(Registrar registrar) {
        registrar.object(PpzBossEggman.ID, "Palmtree Panic boss", (spawn, r) -> new PpzBossEggman(spawn));
        registrar.object(CAPSULE_ID, "Flower capsule", (spawn, r) -> new CdCapsule(spawn));
    }

    @Override
    public void registerArt(CdObjectArtProvider art) throws IOException {
        PpzBossArt.register(art);
        if (PpzBossCatalogue.offset(art.payload(), "eggmanMappings") >= 0) {
            CdCapsule.registerArt(art, PpzBossArt.bossVram(art));
        }
    }

    /**
     * LevEvents_PPZ3 (Level/_Events.asm:127-139): while {@code bossFlags} is clear, the bottom boundary target is
     * $310 and {@code CheckBossStart} locks the left boundary behind the player from X $D70 (the capsule's run).
     */
    @Override
    public void levelEvents(CdBossLevelEvents.Context context) {
        if (bossFlags(context)) return;
        context.camera().setMaxYTarget((short) 0x310);
        CdBossLevelEvents.checkBossStart(context, 0xD70, 0x310);
    }

    @Override public boolean hostsLevelEvents() { return true; }

    private static boolean bossFlags(CdBossLevelEvents.Context context) {
        if (context.objects() == null) return false;
        for (var object : context.objects().getActiveObjects()) {
            if (object instanceof PpzBossEggman eggman && !eggman.isDestroyed() && eggman.bossFlags()) return true;
        }
        return false;
    }
}
