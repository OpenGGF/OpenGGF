package com.openggf.game.modzone;

import com.openggf.game.ModApi;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.dataselect.DataSelectHostProfile;
import com.openggf.game.dataselect.DataSelectPresentationProvider;
import com.openggf.game.dataselect.ModZoneSaveFinding;
import com.openggf.level.Level;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Host capability for validating and constructing additive zones. */
@ModApi
public interface ModZoneAdapter {
    void validate(String ownerModId, ModZoneLevelData level);

    Level load(String ownerModId, ModZoneLevelData level) throws IOException;

    ModZoneRuntimeProfile runtimeProfile(String ownerModId, ModZoneLevelData level);

    default boolean isUnsupported() { return false; }

    default DataSelectHostProfile decorateHostProfile(
            DataSelectHostProfile inherited,
            Supplier<ZoneRegistry> effectiveZones,
            BiConsumer<String, ModZoneSaveFinding> saveFindingSink) {
        return inherited;
    }

    default DataSelectPresentationProvider decoratePresentationProvider(
            DataSelectPresentationProvider inherited,
            DataSelectHostProfile effectiveHostProfile) {
        return inherited;
    }
}
