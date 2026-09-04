package com.openggf.tools.audio.parity;

import java.util.Map;

/**
 * Pinned first divergence, or {@code MATCH}, for every committed per-song run
 * window, keyed by the fixture's path under
 * {@code src/test/resources/audio/parity/s1/runs}.
 *
 * <p>Separate from {@link TestS1RunWindowAudioDriverOracle} so the pins read as
 * a table of measurements rather than as assertions buried in test code. Each
 * value is the one-line summary the oracle prints, and each is sourced from a
 * dated entry in docs/status/audio-frontier-log.md. A window that moves in
 * either direction fails until both this table and that log are updated, so a
 * frontier cannot move silently and a green window cannot regress unnoticed.
 */
final class S1RunWindowPins {
    static final Map<String, String> PINNED = Map.ofEntries();

    private S1RunWindowPins() {
    }
}
