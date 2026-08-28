package com.openggf.audio.presentation;

import com.openggf.audio.StreamedMusicPort;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Presentation-owned multiplexer for a launch-scoped streamed music port.
 *
 * <p>The external port exposes one mutable physical cursor, while the voice
 * registry can retain several logical music slots (base music, nested
 * overrides, pending music, and prepared snapshot replacements). Each
 * {@link Cursor} therefore owns an independent captured state. Switching the
 * physical port lazily saves the prior cursor and restores the requested one;
 * stopping an inactive cursor never touches the currently published voice.</p>
 *
 * <p>This session never closes the port. {@code AudioManager} owns the
 * transferred lease and first calls {@link #detach()} to retire every cursor,
 * then closes the port exactly once.</p>
 */
final class StreamedPresentationSession {
    private StreamedMusicPort port = StreamedMusicPort.EMPTY;
    private final Set<Cursor> cursors = Collections.newSetFromMap(
            new IdentityHashMap<>());
    private Cursor active;

    void attach(StreamedMusicPort replacement) {
        Objects.requireNonNull(replacement, "replacement");
        if (port != StreamedMusicPort.EMPTY || !cursors.isEmpty()) {
            throw new IllegalStateException(
                    "streamed presentation session is already attached");
        }
        port = replacement;
    }

    StreamedMusicPort port() {
        return port;
    }

    int trackedCursorCount() {
        return cursors.size();
    }

    boolean hasStockOverride(int musicId) {
        return port.hasStockOverride(musicId);
    }

    boolean hasTrack(StreamedMusicPort.TrackRef track) {
        return port.hasTrack(Objects.requireNonNull(track, "track"));
    }

    Optional<StreamedMusicPort.SfxPcm> sfxPcm(
            StreamedMusicPort.SfxRef sfx) {
        return port.sfxPcm(Objects.requireNonNull(sfx, "sfx"));
    }

    Cursor materializeStockOverride(int musicId) {
        if (!port.hasStockOverride(musicId)) {
            throw new IllegalStateException(
                    "no streamed override prepared for music " + musicId);
        }
        return materialize(() -> port.playStockOverride(musicId));
    }

    Cursor materializeTrack(StreamedMusicPort.TrackRef track) {
        Objects.requireNonNull(track, "track");
        if (!port.hasTrack(track)) {
            throw new IllegalStateException("no prepared streamed track " + track);
        }
        return materialize(() -> port.playTrack(track));
    }

    Cursor restore(StreamedMusicPort.State state, boolean stopped) {
        Objects.requireNonNull(state, "state");
        Cursor cursor = materialize(() -> {
            if (!port.restoreState(state)) {
                throw new IllegalStateException(
                        "installed streamed port cannot restore " + state.track());
            }
        });
        cursor.stopped = stopped;
        return cursor;
    }

    void detach() {
        if (port == StreamedMusicPort.EMPTY) {
            return;
        }
        if (port.hasSource()) {
            port.stop();
        }
        active = null;
        for (Cursor cursor : cursors) {
            cursor.retired = true;
            cursor.stopped = true;
        }
        cursors.clear();
        port = StreamedMusicPort.EMPTY;
    }

    private Cursor materialize(Runnable starter) {
        requireAttached();
        StreamedMusicPort.State priorPhysical = capturePhysical();
        Cursor priorActive = active;
        RuntimeException primary = null;
        Cursor created = null;
        try {
            starter.run();
            StreamedMusicPort.State initial = port.captureState()
                    .orElseThrow(() -> new IllegalStateException(
                            "streamed source produced no capturable state"));
            created = new Cursor(this, initial);
            cursors.add(created);
        } catch (RuntimeException failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                restorePhysical(priorPhysical);
                active = priorActive != null && !priorActive.complete
                        && !priorActive.stopped && !priorActive.retired
                        ? priorActive : null;
            } catch (RuntimeException restoreFailure) {
                active = null;
                if (created != null) {
                    created.retired = true;
                    created.stopped = true;
                    cursors.remove(created);
                }
                if (primary != null) {
                    primary.addSuppressed(restoreFailure);
                } else {
                    throw restoreFailure;
                }
            }
        }
        return created;
    }

    private StreamedMusicPort.State capturePhysical() {
        Optional<StreamedMusicPort.State> captured = port.captureState();
        if (active != null) {
            if (captured.isPresent()) {
                active.state = captured.get();
            } else {
                active.complete = true;
                active = null;
            }
        }
        return captured.orElse(null);
    }

    private void restorePhysical(StreamedMusicPort.State prior) {
        if (prior == null) {
            if (port.hasSource()) {
                port.stop();
            }
            return;
        }
        if (!port.restoreState(prior)) {
            throw new IllegalStateException(
                    "streamed port could not restore prior cursor "
                            + prior.track());
        }
    }

    private boolean activate(Cursor cursor) {
        requireOwned(cursor);
        if (cursor.retired || cursor.stopped || cursor.complete) {
            return false;
        }
        if (active == cursor) {
            if (!port.hasSource()) {
                cursor.complete = true;
                active = null;
                return false;
            }
            return true;
        }
        capturePhysical();
        if (!port.restoreState(cursor.state)) {
            throw new IllegalStateException(
                    "streamed port could not activate " + cursor.state.track());
        }
        active = cursor;
        return true;
    }

    private int mixInto(Cursor cursor, short[] output, int frames) {
        if (!activate(cursor)) {
            return 0;
        }
        int mixed = port.mixInto(output, frames);
        port.advanceFade();
        if (!port.hasSource()) {
            cursor.complete = true;
            active = null;
        }
        return mixed;
    }

    private boolean isComplete(Cursor cursor) {
        requireOwnedOrRetired(cursor);
        if (cursor.retired || cursor.stopped || cursor.complete) {
            return true;
        }
        if (active == cursor && !port.hasSource()) {
            cursor.complete = true;
            active = null;
            return true;
        }
        return false;
    }

    private void stop(Cursor cursor) {
        requireOwnedOrRetired(cursor);
        if (cursor.retired || cursor.stopped) {
            return;
        }
        if (active == cursor) {
            if (port.hasSource()) {
                port.stop();
            }
            active = null;
        }
        cursor.stopped = true;
    }

    private void retire(Cursor cursor) {
        requireOwnedOrRetired(cursor);
        if (cursor.retired) {
            return;
        }
        stop(cursor);
        cursor.retired = true;
        cursors.remove(cursor);
    }

    private StreamedMusicPort.State snapshot(Cursor cursor) {
        requireOwned(cursor);
        if (active == cursor && !cursor.stopped && !cursor.complete) {
            StreamedMusicPort.State captured = port.captureState()
                    .orElseThrow(() -> new IllegalStateException(
                            "streamed cursor has no source to capture"));
            cursor.state = captured;
        }
        return cursor.state;
    }

    private void fadeOut(Cursor cursor, int steps, int delay) {
        if (activate(cursor)) {
            port.fadeOut(steps, delay);
        }
    }

    private void fadeIn(Cursor cursor, int steps, int delay) {
        if (activate(cursor)) {
            port.fadeIn(steps, delay);
        }
    }

    private void setSpeedMultiplier(Cursor cursor, int multiplier) {
        if (activate(cursor)) {
            port.setSpeedMultiplier(multiplier);
        }
    }

    private boolean fadeActive(Cursor cursor) {
        return activate(cursor) && port.fadeActive();
    }

    private boolean fadeAtFullGain(Cursor cursor) {
        return activate(cursor) && port.fadeAtFullGain();
    }

    private void restoreMutation(Cursor cursor, StreamedMusicPort.State state,
            boolean stopped) {
        requireOwnedOrRetired(cursor);
        Objects.requireNonNull(state, "state");
        if (cursor.retired) {
            cursor.retired = false;
            cursors.add(cursor);
        }
        cursor.state = state;
        cursor.stopped = stopped;
        cursor.complete = false;
        if (active == cursor) {
            if (stopped) {
                if (port.hasSource()) {
                    port.stop();
                }
                active = null;
            } else if (!port.restoreState(state)) {
                active = null;
                throw new IllegalStateException(
                        "streamed port could not roll back " + state.track());
            }
        }
    }

    private void requireAttached() {
        if (port == StreamedMusicPort.EMPTY) {
            throw new IllegalStateException("no streamed presentation port installed");
        }
    }

    private void requireOwned(Cursor cursor) {
        requireOwnedOrRetired(cursor);
        if (cursor.retired || !cursors.contains(cursor)) {
            throw new IllegalStateException("streamed cursor is retired");
        }
    }

    private void requireOwnedOrRetired(Cursor cursor) {
        if (cursor == null || cursor.owner != this) {
            throw new IllegalArgumentException(
                    "streamed cursor belongs to another session");
        }
    }

    static final class Cursor {
        private final StreamedPresentationSession owner;
        private StreamedMusicPort.State state;
        private boolean stopped;
        private boolean complete;
        private boolean retired;

        private Cursor(StreamedPresentationSession owner,
                StreamedMusicPort.State state) {
            this.owner = owner;
            this.state = state;
        }

        int mixInto(short[] output, int frames) {
            return owner.mixInto(this, output, frames);
        }

        boolean isComplete() {
            return owner.isComplete(this);
        }

        void stop() {
            owner.stop(this);
        }

        void retire() {
            owner.retire(this);
        }

        StreamedMusicPort.State snapshot() {
            return owner.snapshot(this);
        }

        boolean stopped() {
            return stopped;
        }

        void fadeOut(int steps, int delay) {
            owner.fadeOut(this, steps, delay);
        }

        void fadeIn(int steps, int delay) {
            owner.fadeIn(this, steps, delay);
        }

        void setSpeedMultiplier(int multiplier) {
            owner.setSpeedMultiplier(this, multiplier);
        }

        boolean fadeActive() {
            return owner.fadeActive(this);
        }

        boolean fadeAtFullGain() {
            return owner.fadeAtFullGain(this);
        }

        void restoreMutation(StreamedMusicPort.State state, boolean stopped) {
            owner.restoreMutation(this, state, stopped);
        }
    }
}
