package com.openggf.game.sonic3k.events;

import com.openggf.game.rewind.schema.ZoneEventSchemaSidecar;
import com.openggf.game.sonic3k.objects.IczSnowboardIntroInstance;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
    void schemaCaptureIgnoresLiveSnowboardIntroReference() throws Exception {
        Sonic3kICZEvents events = new Sonic3kICZEvents();
        setSnowboardIntro(events, new IczSnowboardIntroInstance(new ObjectSpawn(
                IczSnowboardIntroInstance.INITIAL_SNOWBOARD_X,
                IczSnowboardIntroInstance.INITIAL_SNOWBOARD_Y,
                0, 0, 0, false,
                IczSnowboardIntroInstance.INITIAL_SNOWBOARD_Y)));

        assertDoesNotThrow(() -> ZoneEventSchemaSidecar.capture(events),
                "ICZ zone-event sidecar must not attempt to encode the live snowboard intro object reference");
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

    @Test
    void roundTripPreservesSeamlessTransitionOrdinalsAndPublicationFences()
            throws Exception {
        Sonic3kICZEvents original = new Sonic3kICZEvents();
        setLong(original, "act2TransitionChunkOrdinal", 3);
        setLong(original, "act2TransitionBlockOrdinal", 4);
        setLong(original, "act2TransitionArtOrdinal", 2);
        setLong(original, "act2TransitionHandoffId", 9);
        setBoolean(original, "act2TransitionDirectPublished", true);
        setBoolean(original, "act2TransitionArtPublished", false);

        Sonic3kICZEvents restored = new Sonic3kICZEvents();
        ZoneEventSchemaSidecar.restore(
                restored, ZoneEventSchemaSidecar.capture(original));

        assertEquals(3L, longField(restored, "act2TransitionChunkOrdinal"));
        assertEquals(4L, longField(restored, "act2TransitionBlockOrdinal"));
        assertEquals(2L, longField(restored, "act2TransitionArtOrdinal"));
        assertEquals(9L, longField(restored, "act2TransitionHandoffId"));
        assertTrue(booleanField(restored, "act2TransitionDirectPublished"));
        assertEquals(false,
                booleanField(restored, "act2TransitionArtPublished"));
    }

    private static void setSnowboardIntro(Sonic3kICZEvents events, IczSnowboardIntroInstance intro)
            throws ReflectiveOperationException {
        Field field = Sonic3kICZEvents.class.getDeclaredField("snowboardIntro");
        field.setAccessible(true);
        field.set(events, intro);
    }

    private static void setLong(Object target, String name, long value)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(target, value);
    }

    private static void setBoolean(Object target, String name, boolean value)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static long longField(Object target, String name)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getLong(target);
    }

    private static boolean booleanField(Object target, String name)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }
}
