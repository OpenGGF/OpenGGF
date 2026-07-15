package com.openggf.mods.code;

import com.openggf.game.ZoneRegistry;
import com.openggf.game.dataselect.DataSelectHostProfile;
import com.openggf.game.dataselect.DataSelectPresentationProvider;
import com.openggf.game.sonic2.dataselect.S2SaveFinding;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Engine-internal capability for hosts whose additive zones require a data-select overlay.
 *
 * <p>This interface is intentionally outside the recursive {@code @ModApi} surface. Adapters
 * that do not implement it retain their inherited native profile and presentation unchanged.
 */
public interface ModZoneDataSelectDecorator {
    DataSelectHostProfile decorateHostProfile(
            DataSelectHostProfile inherited,
            Supplier<ZoneRegistry> effectiveZones,
            BiConsumer<String, S2SaveFinding> saveFindingSink);

    DataSelectPresentationProvider decoratePresentationProvider(
            DataSelectPresentationProvider inherited,
            DataSelectHostProfile effectiveHostProfile);
}
