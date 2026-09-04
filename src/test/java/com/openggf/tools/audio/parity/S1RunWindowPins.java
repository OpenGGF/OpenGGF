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
 *
 * <p>A red window is published deliberately. A window with its frontier
 * recorded is coverage: it pins where the engine stops agreeing, so the next
 * change either moves that point or is visible as a regression.
 */
final class S1RunWindowPins {
    static final Map<String, String> PINNED = Map.ofEntries(
            // Title-screen song, power-on through its fade into GHZ1. Green end
            // to end since the driver-command dispatch point moved to the ROM's
            // own position, past the fade step and before the track walk
            // (docs/status/audio-frontier-log.md, 2026-09-04).
            Map.entry("s1-complete-run/w000-id8A.jsonl.gz", "MATCH"),
            // Act-clear jingle. Agrees for 359 ticks and then diverges on the
            // tempo timeout at the tick where the ROM serviced its driver twice
            // in one recorded invocation; a replay that services once per tick
            // cannot express that. Measured, not inferred: a driver-pass counter
            // found exactly one such invocation in 6,419 frames, at this tick.
            Map.entry("s1-complete-run/w002-id8E.jsonl.gz",
                    "GLOBAL_STATE_MISMATCH tick 360 role GLOBAL field tempo_timeout"
                            + " reference 1 against engine 2"),
            // The one-invocation GHZ window between the act-clear jingle and the
            // next act's music. Short, and worth keeping precisely because a
            // window boundary landing one invocation apart is the case a
            // per-song contract is most likely to get wrong.
            Map.entry("s1-complete-run/w003-id81.jsonl.gz", "MATCH"));

    private S1RunWindowPins() {
    }
}
