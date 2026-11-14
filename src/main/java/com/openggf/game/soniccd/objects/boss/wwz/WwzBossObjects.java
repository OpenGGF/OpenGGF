package com.openggf.game.soniccd.objects.boss.wwz;

import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.boss.CdBossFragment;
import com.openggf.game.soniccd.objects.boss.CdBossLevelEvents;
import com.openggf.game.soniccd.objects.boss.CdCapsule;
import com.openggf.camera.Camera;

import java.io.IOException;

/**
 * Wacky Workbench's boss fragment for R63C and R63D. Act 3's index (r6/object_index_3.asm:3-69; ID = entry position
 * + 1) puts the capsule at $15, Eggman at $34 and the conductors at $3E; the machine $35, spikes $36, exhaust $37,
 * falling spikes $38, debris $39, floor pieces $3D, smoke $3F and electricity $40 are only allocated by the boss. The
 * act 3 layout places no Eggman statue ($2B; r6/stage_object_maps_3.asm), so the zone's statue class is not needed
 * here. The level events are {@code LevEvents_WWZ}.
 */
public final class WwzBossObjects implements CdBossFragment {
    /** CapsuleObject is entry $14 of r6/object_index_3.asm:3-25. */
    static final int CAPSULE_ID = 0x15;

    @Override
    public void register(Registrar registrar) {
        registrar.object(WwzBossEggman.ID, "Wacky Workbench boss", (spawn, r) -> new WwzBossEggman(spawn));
        registrar.object(WwzConductor.ID, "Boss conductor", (spawn, r) -> new WwzConductor(spawn));
        registrar.object(CAPSULE_ID, "Flower capsule", (spawn, r) -> new CdCapsule(spawn));
    }

    @Override
    public void registerArt(CdObjectArtProvider art) throws IOException {
        WwzBossArt.register(art);
        if (WwzBossCatalogue.offset(art.payload(), "eggmanMappings") >= 0) {
            CdCapsule.registerArt(art, WwzBossArt.bossVram(art));
        }
    }

    /**
     * LevEvents_WWZ (Level/_Events.asm:179-224; common/events.asm:167-212): with {@code bossFlags} bit 4 clear the bottom
     * target is $710. In the arena {@code CheckBossStart} locks the left boundary behind the player from X $BA0 (the
     * capsule's run) with the bottom at $1D0; before that the bottom follows the player's floor ($1D0 at or above Y
     * $298, $3D0 at or above $498, else $5D0) and snaps when within 2 pixels.
     */
    @Override
    public void levelEvents(CdBossLevelEvents.Context context) {
        Camera camera = context.camera();
        if ((bossFlags(context) & 0x10) == 0) {
            camera.setMaxYTarget((short) 0x710);
            return;
        }
        if (CdBossLevelEvents.checkBossStart(context, 0xBA0, 0x1D0)) return;
        int py = (short) context.player().getCentreY();
        int bottom = py <= 0x298 ? 0x1D0 : py <= 0x498 ? 0x3D0 : 0x5D0;
        camera.setMaxYTarget((short) bottom);
        int distance = (short) (bottom - camera.getMaxY());
        if (distance < 0) distance = -distance;
        if (distance <= 2) camera.setMaxYCurrent((short) bottom);
    }

    @Override public boolean hostsLevelEvents() { return true; }

    private static int bossFlags(CdBossLevelEvents.Context context) {
        if (context.objects() == null) return 0;
        for (var object : context.objects().getActiveObjects()) {
            if (object instanceof WwzBossPiece piece && !piece.isDestroyed()) {
                var art = piece.artProviderForEvents();
                if (art != null) {
                    WwzBossRam ram = art.zoneArt(WwzBossRam.class);
                    return ram == null ? 0 : ram.flags();
                }
            }
        }
        return 0;
    }
}
