package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.rewind.RewindStateful;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.level.objects.ObjectServices;
import java.io.IOException;
import java.io.UncheckedIOException;

/** loc_78592: Queue_Kos_Module then PLC_BossExplosion; the separate $2F wait remains native.
 * S3K Nemesis PLC application is synchronous in the current loader, so there is no pending
 * Nemesis FIFO to poll here. This does not claim native Nemesis service timing.
 */
final class LrzMinibossArtState implements RewindStateful<LrzMinibossArtState.Value> {
    record Value(long ordinal) { }
    private long ordinal = -1;

    void submit(ObjectServices services) {
        if (!(services.currentLevel() instanceof Sonic3kLevel level)) return;
        try {
            var rom = services.rom();
            var physical = services.kosinskiModuleQueue();
            if (physical != null) {
                Sonic3kPlcLoader.bindRuntimePatternDmaTarget(physical, services);
                physical.enqueue(rom, Sonic3kConstants.ART_KOSM_LRZ_MINIBOSS_ADDR,
                        Sonic3kConstants.ARTTILE_LRZ_MINIBOSS * 32);
            }
            try {
                ordinal = S3kRuntimeArtCoordinator.from(services).moduleQueue().queue(rom,
                        Sonic3kConstants.ART_KOSM_LRZ_MINIBOSS_ADDR,
                        Sonic3kConstants.ARTTILE_LRZ_MINIBOSS).ordinal();
            } catch (IllegalStateException unavailable) {
                // Bare object fixtures have no timing coordinator. Production does.
                if (!"runtime-art coordination is unavailable in these object services"
                        .equals(unavailable.getMessage())) throw unavailable;
            }
            // PLC_BossExplosion ($83D64): ArtNem_BossExplosion -> ArtTile_BossExplosion.
            var changed = Sonic3kPlcLoader.applyRawToLevel(java.util.List.of(
                    new Sonic3kPlcLoader.RawPlcEntry(0x500,
                            Sonic3kConstants.ART_NEM_BOSS_EXPLOSION_ADDR)), level, rom);
            Sonic3kPlcLoader.refreshAffectedRenderers(changed, services.levelManager());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    void service(ObjectServices services) {
        if (ordinal < 0) return;
        var queue = S3kRuntimeArtCoordinator.from(services).moduleQueue();
        var handle = services.hardwareTiming().pendingHandle(HardwareWorkKind.KOS_MODULE_QUEUE, ordinal)
                .orElseThrow();
        if (queue.isReady(handle)) {
            queue.claim(handle);
            ordinal = -1;
        }
    }

    @Override public Value captureRewindStateValue() { return new Value(ordinal); }
    @Override public void restoreRewindStateValue(Value value) { ordinal = value.ordinal(); }
}
