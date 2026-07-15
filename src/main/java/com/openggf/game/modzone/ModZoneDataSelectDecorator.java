package com.openggf.game.modzone;

import com.openggf.game.ZoneRegistry;
import com.openggf.game.dataselect.DataSelectHostProfile;
import com.openggf.game.dataselect.DataSelectPresentationProvider;
import com.openggf.game.dataselect.ModZoneSaveFinding;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Internal host capability for adapting data select around additive zones. */
public interface ModZoneDataSelectDecorator {
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
