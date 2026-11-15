package com.openggf.game.soniccd.objects.boss.qqz;

import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.boss.CdBossFragment;
import com.openggf.game.soniccd.objects.boss.CdBossLevelEvents;
import com.openggf.game.soniccd.objects.boss.CdCapsule;

import java.io.IOException;

/**
 * Quartz Quadrant's boss fragment for R53C and R53D. Act 3's index (r5/object_index_3_act3.asm:3-63; ID = entry
 * position + 1) puts the capsule at $15, the spike wall at $21 and the boss top at $33, which the layout places; the
 * other boss IDs ($20 sparks, $22 belt, $23 explosion, $32 Eggman, $34 booth, $35 back, $36 pipes, $37 dropper, $38
 * and $39 bombs) are only allocated by the boss. Both futures run the same code; the belt colours and the music after
 * the fight differ ({@code good_future}). The act 3 events are {@code LevEvents_QQZ3}.
 */
public final class QqzBossObjects implements CdBossFragment {
    /** CapsuleObject is entry $14 of the act 3 index (r5/object_index_3_act3.asm:3-24). */
    static final int CAPSULE_ID = 0x15;

    @Override
    public void register(Registrar registrar) {
        registrar.object(QqzBossTop.ID, "Quartz Quadrant boss", (spawn, r) -> new QqzBossTop(spawn));
        registrar.object(QqzSpikeWall.ID, "Boss spike wall", (spawn, r) -> new QqzSpikeWall(spawn));
        registrar.object(CAPSULE_ID, "Flower capsule", (spawn, r) -> new CdCapsule(spawn));
    }

    @Override
    public void registerArt(CdObjectArtProvider art) throws IOException {
        QqzBossArt.register(art);
        if (QqzBossCatalogue.offset(art.payload(), "boothMappings") >= 0) {
            CdCapsule.registerArt(art, QqzBossArt.bossVram(art));
        }
    }

    /**
     * LevEvents_QQZ3 (Level/_Events.asm:250-267, REGION=USA): {@code CheckBossStart} from X $E10 with bottom $1F8 (the
     * capsule's run); otherwise, while {@code bossFlags} is clear the bottom boundary target is $320, and while it is
     * set both the bottom boundary and its target are $1F8.
     */
    @Override
    public void levelEvents(CdBossLevelEvents.Context context) {
        if (CdBossLevelEvents.checkBossStart(context, 0xE10, 0x1F8)) return;
        if (bossFlags(context) == 0) {
            context.camera().setMaxYTarget((short) 0x320);
        } else {
            context.camera().setMaxY((short) 0x1F8);                // bottomBound and destBottomBound
        }
    }

    @Override public boolean hostsLevelEvents() { return true; }

    private static int bossFlags(CdBossLevelEvents.Context context) {
        if (context.objects() == null) return 0;
        for (var object : context.objects().getActiveObjects()) {
            if (object instanceof QqzBossTop top && !top.isDestroyed()) return top.bossFlags();
        }
        return 0;
    }
}
