package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.RewindStateful;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.level.objects.ObjectServices;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Obj_DEZMiniboss's Load_PLC $7B and Queue_Kos_Module; no synthetic readiness delay. */
final class DezMinibossArtState implements RewindStateful<DezMinibossArtState.Value> {
    record Value(long ordinal) { }
    private long ordinal = -1;

    void submit(ObjectServices services) {
        try {
            var rom = services.rom();
            if (services.currentLevel() instanceof Sonic3kLevel level) {
                var plc = Sonic3kPlcLoader.parsePlc(rom, 0x7B);
                var changed = Sonic3kPlcLoader.applyToLevel(plc, level, rom);
                Sonic3kPlcLoader.refreshAffectedRenderers(changed, services.levelManager());
            }
            var physical = services.kosinskiModuleQueue();
            if (physical != null) {
                Sonic3kPlcLoader.bindRuntimePatternDmaTarget(physical, services);
                physical.enqueue(rom, Sonic3kConstants.ART_KOSM_DEZ_MINIBOSS_ADDR,
                        Sonic3kConstants.ARTTILE_DEZ_MINIBOSS * 32);
            }
            try {
                ordinal = S3kRuntimeArtCoordinator.from(services).moduleQueue().queue(rom,
                        Sonic3kConstants.ART_KOSM_DEZ_MINIBOSS_ADDR,
                        Sonic3kConstants.ARTTILE_DEZ_MINIBOSS).ordinal();
            } catch (IllegalStateException unavailable) {
                // Bare object fixtures have no timing coordinator. Production does.
                if (!"runtime-art coordination is unavailable in these object services"
                        .equals(unavailable.getMessage())) throw unavailable;
            }
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
