package com.openggf.game.mode;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.GameplayModeContext;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/** Engine-owned native-player custody. Creator quarantine cannot bypass its cleanup. */
public final class DirectedPlayerLease implements RewindSnapshottable<DirectedPlayerLease.Snapshot> {
    private final GameplayModeContext context;
    // Fault authority is monotonic outside historical state. Weak keys retain no obsolete history.
    private final Set<CourseControl.DirectedPlayerState> revoked = Collections.newSetFromMap(new WeakHashMap<>());
    private CourseControl.DirectedPlayerState entry;
    private CourseControl.DirectedPlayerState rejectedRestore;

    public DirectedPlayerLease(GameplayModeContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    public void begin(CourseControl.DirectedPlayerState value) {
        if (entry != null && !CourseControl.isDirectedPlayerVisit(context, entry)) entry = null;
        if (entry != null) throw new IllegalStateException("Native main player is already borrowed");
        if (revoked.contains(value)) throw new IllegalStateException("Faulted native direction cannot be resumed");
        entry = Objects.requireNonNull(value, "entry");
    }

    public boolean owns(CourseControl.DirectedPlayerState value) { return value != null && entry == value; }

    public void complete(CourseControl.DirectedPlayerState value) {
        if (entry == value) entry = null;
    }

    /** Cleanup bypasses creator callbacks and never applies an entry to a different level visit. */
    public void abort() {
        var borrowed = entry;
        entry = null; // Clear first, including when restoration itself raises a callback failure.
        if (borrowed != null) {
            revoked.add(borrowed);
            CourseControl.restoreDirectedPlayerAfterFailure(context, borrowed);
        }
    }

    public record Snapshot(CourseControl.DirectedPlayerState entry) { }

    @Override public String key() { return "directed-player-lease"; }
    @Override public Snapshot capture() { return new Snapshot(entry); }

    @Override public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        rejectedRestore = revoked.contains(snapshot.entry()) ? snapshot.entry() : null;
        entry = rejectedRestore == null ? snapshot.entry() : null;
    }

    /** Runs after native sprite/camera restoration, so an old faulted snapshot cannot relock them. */
    public void afterRestore() {
        var rejected = rejectedRestore;
        rejectedRestore = null;
        if (rejected != null) CourseControl.restoreDirectedPlayerAfterFailure(context, rejected);
    }

    @Override public void resetForMissingSnapshot() {
        entry = null;
        rejectedRestore = null;
    }
}
