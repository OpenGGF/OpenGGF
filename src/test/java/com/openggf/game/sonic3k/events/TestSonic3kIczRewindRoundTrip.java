package com.openggf.game.sonic3k.events;

import com.openggf.game.rewind.schema.ZoneEventSchemaSidecar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the ICZ event-state inclusion in
 * {@link com.openggf.game.sonic3k.Sonic3kLevelEventManager} extra-state snapshots.
 *
 * <p>Before this fix, {@code captureExtra()} silently dropped {@link Sonic3kICZEvents}
 * state; rewinding inside ICZ reset the indoor palette cycle, snow physics, and
 * background routine to their post-{@code init()} defaults.
 */
class TestSonic3kIczRewindRoundTrip {

    @Test
    void schemaCaptureProducesNonEmptyPayload() {
        Sonic3kICZEvents events = new Sonic3kICZEvents();
        byte[] payload = ZoneEventSchemaSidecar.capture(events);

        assertTrue(payload.length >= 25,
                "ICZ schema must encode at minimum the legacy 5 booleans + 5 ints");
    }

    @Test
    void roundTripPreservesPubliclyObservableState() {
        Sonic3kICZEvents original = new Sonic3kICZEvents();
        original.setEventsFg5(true);
        original.setIndoorPaletteCyclingActive(false);

        Sonic3kICZEvents restored = new Sonic3kICZEvents();
        // Ensure the restored instance starts with different values where it matters,
        // so a no-op read would produce a different state than the original.
        restored.setEventsFg5(false);
        restored.setIndoorPaletteCyclingActive(true);

        // Sanity: the two instances differ before the round-trip.
        assertNotEquals(original.isEventsFg5(), restored.isEventsFg5());
        assertNotEquals(original.isIndoorPaletteCyclingActive(),
                restored.isIndoorPaletteCyclingActive());

        ZoneEventSchemaSidecar.restore(restored, ZoneEventSchemaSidecar.capture(original));

        assertEquals(original.isEventsFg5(), restored.isEventsFg5(),
                "eventsFg5 must round-trip through capture/restore");
        assertEquals(original.isIndoorPaletteCyclingActive(),
                restored.isIndoorPaletteCyclingActive(),
                "indoorPaletteCyclingActive must round-trip through capture/restore");
        assertEquals(original.getIcz1BackgroundRoutine(),
                restored.getIcz1BackgroundRoutine(),
                "backgroundRoutine must round-trip through capture/restore");
        assertEquals(original.getIcz1BigSnowOffset(),
                restored.getIcz1BigSnowOffset(),
                "bigSnowOffset must round-trip through capture/restore");
    }

    @Test
    void capturedBytesAreStableAcrossRoundTrip() {
        // Stronger property: writing a state, reading into a fresh instance, and
        // writing that instance again must produce the identical byte sequence.
        // This covers private fields without exposing getters.
        Sonic3kICZEvents original = new Sonic3kICZEvents();
        original.setEventsFg5(true);
        original.setIndoorPaletteCyclingActive(false);

        byte[] first = ZoneEventSchemaSidecar.capture(original);

        Sonic3kICZEvents restored = new Sonic3kICZEvents();
        ZoneEventSchemaSidecar.restore(restored, first);

        byte[] second = ZoneEventSchemaSidecar.capture(restored);

        assertArrayEquals(first, second,
                "captured bytes must be identical after a schema capture-restore-capture cycle");
    }
}
