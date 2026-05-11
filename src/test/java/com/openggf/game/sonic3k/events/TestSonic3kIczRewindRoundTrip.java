package com.openggf.game.sonic3k.events;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

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
    void rewindStateBytesMatchesWriteOutput() {
        Sonic3kICZEvents events = new Sonic3kICZEvents();
        ByteBuffer buf = ByteBuffer.allocate(Sonic3kICZEvents.rewindStateBytes());
        events.writeRewindState(buf);

        assertEquals(Sonic3kICZEvents.rewindStateBytes(), buf.position(),
                "writeRewindState must produce exactly rewindStateBytes() bytes");
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

        ByteBuffer write = ByteBuffer.allocate(Sonic3kICZEvents.rewindStateBytes());
        original.writeRewindState(write);
        write.flip();
        restored.readRewindState(write);

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

        ByteBuffer first = ByteBuffer.allocate(Sonic3kICZEvents.rewindStateBytes());
        original.writeRewindState(first);

        Sonic3kICZEvents restored = new Sonic3kICZEvents();
        ByteBuffer readBack = first.duplicate();
        readBack.flip();
        restored.readRewindState(readBack);

        ByteBuffer second = ByteBuffer.allocate(Sonic3kICZEvents.rewindStateBytes());
        restored.writeRewindState(second);

        assertArrayEquals(first.array(), second.array(),
                "captured bytes must be identical after a write→read→write cycle");
    }

    @Test
    void rewindStateSizeIsAtLeastTwentyFour() {
        // Locked-in contract: any future refactor that drops state must also
        // either match the byte count or update the constant deliberately.
        assertTrue(Sonic3kICZEvents.rewindStateBytes() >= 24,
                "ICZ rewind state must encode at minimum 4 booleans + 5 ints");
    }
}
