package com.openggf.game.sonic1.resources;

import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Sonic 1-owned coordinator for the one piece of PLC work whose timing the
 * ROM's own main loop decides: the {@code RunPLC} arming edge.
 *
 * <p>The submission is made at {@link HardwareServiceBoundary#PRE_MAIN_LOOP}
 * because that is the loop tail {@code RunPLC} sits in
 * (docs/s1disasm/sonic.asm:3032, after {@code ExecuteObjects} 3010,
 * {@code DeformLayers} 3025, {@code BuildSprites} 3028, {@code ObjPosLoad}
 * 3029 and {@code PaletteCycle} 3031). Submitting before the ledger is
 * serviced lets the same boundary prepare the job, so a recorded edge for
 * this row has a prepared, production-submitted job to release.
 */
public final class Sonic1RuntimeArtCoordinator implements RuntimeArtCoordinator {

    private final Sonic1PlcArmTiming armTiming;
    private final Supplier<Sonic1PlcService> plcService;

    public Sonic1RuntimeArtCoordinator(
            HardwareTimingService timing, Supplier<Sonic1PlcService> plcService) {
        this.armTiming = new Sonic1PlcArmTiming(
                Objects.requireNonNull(timing, "timing"));
        this.plcService = Objects.requireNonNull(plcService, "plcService");
    }

    public Sonic1PlcArmTiming armTiming() {
        return armTiming;
    }

    @Override
    public void beforeTimingService(HardwareServiceBoundary boundary) {
        if (boundary != HardwareServiceBoundary.PRE_MAIN_LOOP) {
            return;
        }
        Sonic1PlcService service = boundService();
        if (service != null) {
            service.submitArmableHead();
        }
    }

    @Override
    public void registerRewindAdapters(RewindRegistry registry) {
        Objects.requireNonNull(registry, "registry").register(armTiming);
    }

    @Override
    public void deregisterRewindAdapters(RewindRegistry registry) {
        Objects.requireNonNull(registry, "registry").deregister(Sonic1PlcArmTiming.REWIND_KEY);
    }

    @Override
    public void resetForMissingSnapshot() {
        armTiming.resetForMissingSnapshot();
    }

    private Sonic1PlcService boundService() {
        Sonic1PlcService service = plcService.get();
        if (service != null) {
            service.bindArmTiming(armTiming);
        }
        return service;
    }
}
