package com.openggf.game.soniccd.objects.boss.mmz;

import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.boss.CdBossFragment;
import com.openggf.game.soniccd.objects.boss.CdBossLevelEvents;
import com.openggf.game.soniccd.zones.mmz.MmzStageSignals;

import java.io.IOException;

/**
 * Metallic Madness's boss fragment for R83C and R83D. Act 3's index (r8/object_index_3.asm:3-69; ID = entry position
 * + 1) puts the door at $29, the Hotaru at $39, the boss door at $3D, Eggman at $3F, the panels at $40, the Egg
 * Mobile at $41, the sparks at $42 and Amy at $43. The layout places the door ($29), the Hotaru intro ($39, subtype
 * $FE) and the boss door ($3D); every other part is allocated by them. The act 3 events are
 * {@code r8/events.asm} sub_203570.
 *
 * <p>The door ($29) is a zone object ({@code objects/mmz/MmzDoor}, registered by the zone fragment for every stage). Act 3 also links the Collision
 * Chaos bumper at $1D (objects/r3/bumper_flipper.asm, R8 Shared.asm:175-177); the arena has none, and
 * {@code CczBumper} draws Collision Chaos's sheet and tile, so it is not registered here (reported).
 */
public final class MmzBossObjects implements CdBossFragment {
    @Override
    public void register(Registrar registrar) {
        registrar.object(MmzBossHotaru.ID, "Hotaru", (spawn, r) -> switch (spawn.subtype() & 0xFF) {
            case MmzBossHotaru.INTRO -> new MmzBossHotaru(spawn);
            case MmzBossHotaru.SHOT -> new MmzBossFireflyShot(spawn);
            case MmzBossHotaru.TRAIL -> new MmzBossFireflyTrail(spawn);
            default -> new MmzBossFirefly(spawn);
        });
        registrar.object(MmzBossDoor.ID, "Boss door", (spawn, r) -> new MmzBossDoor(spawn));
    }

    @Override
    public void registerArt(CdObjectArtProvider art) throws IOException {
        MmzBossArt.register(art);
    }

    /**
     * sub_203570 (r8/events.asm:66-80), the act 3 branch of {@code StageEvents}: while {@code boss_flags} is clear the
     * bottom boundary's target is $310; once set, the boundary and its target are $10C.
     */
    @Override
    public void levelEvents(CdBossLevelEvents.Context context) {
        MmzStageSignals signals = MmzStageSignals.of(com.openggf.game.GameServices.module(), context.level());
        if (signals == null || signals.bossFlags() == 0) {
            context.camera().setMaxYTarget((short) 0x310);
            return;
        }
        context.camera().setMaxY((short) 0x10C);
    }

    @Override public boolean hostsLevelEvents() { return true; }
}
