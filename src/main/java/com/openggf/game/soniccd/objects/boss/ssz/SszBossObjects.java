package com.openggf.game.soniccd.objects.boss.ssz;

import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.boss.CdBossFragment;
import com.openggf.game.soniccd.objects.boss.CdBossLevelEvents;

import java.io.IOException;

/**
 * Stardust Speedway's boss fragment for R73C and R73D: the race against Metal Sonic. Act 3's index
 * (r7/object_index_3.asm:3-62; ID = entry position + 1) links the gate blocks ($2B), Eggman ($2C), the exhaust ($2D),
 * the laser ($2E), Metal Sonic ($2F), his shield ($30) and pieces ($31), the spikes ($32), Amy ($33), her hearts ($34)
 * and the laser's debris ($37). The layout places the blocks, Eggman, the spikes and Amy; Eggman, Metal Sonic, the
 * laser and Amy allocate the rest. The act 3 events are {@code R73Events}.
 */
public final class SszBossObjects implements CdBossFragment {
    @Override
    public void register(Registrar registrar) {
        registrar.object(SszBossBlock.ID, "Race gate block", (spawn, r) -> new SszBossBlock(spawn));
        registrar.object(SszBossEggman.ID, "Stardust Speedway boss", (spawn, r) -> new SszBossEggman(spawn));
        registrar.object(SszBossSpikes.ID, "Race spikes", (spawn, r) -> new SszBossSpikes(spawn));
        registrar.object(SszAmyRose.ID, "Amy Rose", (spawn, r) -> new SszAmyRose(spawn));
    }

    @Override
    public void registerArt(CdObjectArtProvider art) throws IOException {
        SszBossArt.register(art);
    }

    /**
     * R73Events (common/events.asm:395-427; LevEvents_SSZ3, Level/_Events.asm:407-435): the bottom boundary target is
     * $210, except from player X $930 to $DC0 (the climb to Eggman's start), where it is $120 and the boundary snaps
     * to it once within 4 pixels.
     */
    @Override
    public void levelEvents(CdBossLevelEvents.Context context) {
        int px = (short) context.player().getCentreX();
        var camera = context.camera();
        if (px < 0x930 || px >= 0xDC0) {
            camera.setMaxYTarget((short) 0x210);
            return;
        }
        camera.setMaxYTarget((short) 0x120);
        int distance = (short) (0x120 - camera.getMaxY());
        if (distance < 0) distance = -distance;
        if (distance < 4) camera.setMaxYCurrent((short) 0x120);
    }

    @Override public boolean hostsLevelEvents() { return true; }
}
