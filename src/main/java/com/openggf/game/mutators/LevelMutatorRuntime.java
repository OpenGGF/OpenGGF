package com.openggf.game.mutators;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.WorldSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Trusted world-owned entry reservations. A late toggle cannot revoke captured players. */
public final class LevelMutatorRuntime implements RewindSnapshottable<LevelMutatorRuntime.Snapshot> {
    private static final AtomicLong GENERATIONS = new AtomicLong();
    private final long generation = GENERATIONS.incrementAndGet();
    private final WorldSession world;
    private long ordinal;
    private final Map<Long, StageEntryKind> permits = new HashMap<>();
    private StageEntryKind publishing;
    private long resultsPermit;
    private boolean retired;

    public LevelMutatorRuntime(WorldSession world) { this.world = Objects.requireNonNull(world); }

    public long tryAdmit(StageEntryKind kind) {
        Objects.requireNonNull(kind);
        if (retired || !LevelMutatorPolicyAccess.entryAllowed(world, kind) || permits.size() >= 128) return 0;
        if (++ordinal > 0xFFFF_FFFFL) throw new IllegalStateException("Entry permit ordinal exhausted");
        long permit = (generation << 32) | ordinal;
        permits.put(permit, kind);
        return permit;
    }

    public boolean publish(StageEntryKind kind, long permit, Runnable publication) {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(publication);
        if (retired || publishing != null || permits.get(permit) != kind) return false;
        permits.remove(permit);
        if (resultsPermit == permit) resultsPermit = 0;
        publishing = kind;
        try { publication.run(); }
        finally { publishing = null; }
        return true;
    }

    /** Only the synchronous publication owner can bypass a newer denial. */
    public boolean isPublishing(StageEntryKind kind) { return !retired && kind != null && publishing == kind; }
    public void discard(long permit) {
        permits.remove(permit);
        if (resultsPermit == permit) resultsPermit = 0;
    }
    public void clear() { permits.clear(); resultsPermit = 0; }

    /** Lifetime retirement cannot be undone by a rewind snapshot. */
    public void retire() { retired = true; clear(); }
    public void holdResultsPermit(long permit) {
        if (!retired && permits.get(permit) == StageEntryKind.SPECIAL) resultsPermit = permit;
    }
    public long resultsPermit() { return resultsPermit; }
    @Override public String key() { return "level-mutator-runtime"; }
    @Override public Snapshot capture() { return new Snapshot(generation, ordinal, permits, resultsPermit); }
    @Override public void restore(Snapshot snapshot) {
        if (retired) throw new IllegalStateException("Entry runtime belongs to a retired world");
        if (snapshot.generation() != generation) throw new IllegalArgumentException("Entry snapshot belongs to another world");
        ordinal = snapshot.ordinal(); permits.clear(); permits.putAll(snapshot.permits()); publishing = null; resultsPermit = snapshot.resultsPermit();
    }
    public record Snapshot(long generation, long ordinal, Map<Long, StageEntryKind> permits, long resultsPermit) {
        public Snapshot { permits = Map.copyOf(permits); }
    }
}
