package com.openggf.game.resources;

import java.util.Map;
import java.util.Objects;

/** Immutable, module-supplied player-art transfer semantics for one session. */
public record PlayerArtTransferPlan(Map<String, PlayerArtBank> banks, TransferModel model) {
    public PlayerArtTransferPlan {
        banks = Map.copyOf(banks);
        Objects.requireNonNull(model, "model");
    }

    public enum TransferModel {
        /** Sonic_LoadGfx stages into RAM; a later V-int writes it. */
        STAGED_FOR_VINT,
        /** The DPLC queues DMA; ProcessDMAQueue retires it at V-blank. */
        QUEUED_FOR_VBLANK
    }

    /** Returns null when the loaded module does not audit this owner. */
    public PlayerArtBank bank(String owner) {
        return banks.get(owner);
    }
}
