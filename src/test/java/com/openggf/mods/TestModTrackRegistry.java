package com.openggf.mods;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class TestModTrackRegistry {
    @Test
    void registryIsNamespacedImmutableAndRejectsDuplicateTypedKeys() {
        ModAudioTrack one = track("one", "same");
        ModAudioTrack two = track("two", "same");
        ModTrackRegistry registry = new ModTrackRegistry(List.of(one, two));
        assertEquals(one, registry.find(one.key()).orElseThrow());
        assertEquals(two, registry.find(two.key()).orElseThrow());
        assertTrue(registry.find(new TrackKey("one", "missing")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ModTrackRegistry(List.of(one, one)));
        assertThrows(UnsupportedOperationException.class, () -> registry.tracks().clear());
    }

    private static ModAudioTrack track(String owner, String id) {
        return new ModAudioTrack(new TrackKey(owner, id), "audio/" + owner + ".ogg",
                false, 0, OptionalLong.empty(), 1, false);
    }
}
