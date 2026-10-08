package com.openggf.tools.challenge;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Owns the all-ready/all-stepped barrier; each endpoint owns its native game clocks. */
public final class ChallengeHost implements AutoCloseable {
    public record Member(String id, String game, Path rom) {
        public Member {
            if (id == null || !id.matches("[a-z][a-z0-9-]{0,31}")
                    || !List.of("s1", "s2", "s3k").contains(game))
                throw new IllegalArgumentException("Invalid challenge member");
            Objects.requireNonNull(rom);
        }
    }
    private final List<Member> members;
    private final List<ProcessGameEndpoint> endpoints = new ArrayList<>();
    private final long generation;
    private volatile boolean prepared, started, closed, faulted;
    private boolean admitting;
    enum Commit { READY, START, TICK }
    private volatile long tick;
    private volatile List<ChallengeProtocol.Frame> committed = List.of();

    public ChallengeHost(List<Member> members, long generation) {
        if (members.isEmpty() || members.size() > 3 || generation <= 0)
            throw new IllegalArgumentException("Challenge requires one to three members");
        if (members.stream().map(Member::id).distinct().count() != members.size())
            throw new IllegalArgumentException("Duplicate member identity");
        this.members = List.copyOf(members);
        this.generation = generation;
    }
    public List<ChallengeProtocol.Frame> prepare() throws IOException {
        beginAdmission(Commit.READY);
        try {
            // Validate every member before the first process can acquire resources.
            for (Member member : members) ChallengeRoms.validate(member.game(), member.rom());
            List<CompletableFuture<ChallengeProtocol.Frame>> pending = new ArrayList<>();
            for (Member member : members) {
                synchronized (this) {
                    requireHealthy();
                    var endpoint = new ProcessGameEndpoint(member.game(), member.rom(), generation);
                    endpoints.add(endpoint);
                    pending.add(endpoint.prepare());
                }
            }
            var ready = collect(pending, Duration.ofSeconds(45));
            return commitTuple(ready, Commit.READY);
        } catch (IOException | RuntimeException e) {
            abort(e);
            throw e;
        } finally {
            endAdmission();
        }
    }
    public List<ChallengeProtocol.Frame> start() throws IOException {
        beginAdmission(Commit.START);
        try {
            return commitTuple(collect(endpoints.stream().map(ProcessGameEndpoint::start).toList(),
                                       Duration.ofSeconds(5)),
                    Commit.START);
        } catch (IOException | RuntimeException e) {
            abort(e);
            throw e;
        } finally {
            endAdmission();
        }
    }
    public List<ChallengeProtocol.Frame> step(int heldMask) throws IOException {
        if (heldMask < 0 || heldMask > 255)
            throw new IllegalArgumentException("Invalid common pad");
        beginAdmission(Commit.TICK);
        try {
            var tuple = collect(endpoints.stream().map(endpoint -> endpoint.step(heldMask)).toList(),
                    Duration.ofSeconds(5));
            return commitTuple(tuple, Commit.TICK);
        } catch (IOException | RuntimeException e) {
            abort(e);
            throw e;
        } finally {
            endAdmission();
        }
    }
    private synchronized void beginAdmission(Commit kind) {
        requireHealthy();
        if (admitting)
            throw new IllegalStateException("A challenge admission is already running");
        if (kind == Commit.READY && prepared || kind == Commit.START && (!prepared || started)
                || kind == Commit.TICK && !started)
            throw new IllegalStateException("Invalid challenge lifecycle admission");
        admitting = true;
    }
    private synchronized void endAdmission() {
        admitting = false;
    }
    synchronized List<ChallengeProtocol.Frame> commitTuple(List<ChallengeProtocol.Frame> tuple, Commit kind)
            throws IOException {
        requireHealthy();
        long expected = kind == Commit.TICK ? tick + 1 : 0;
        if (tuple.size() != members.size())
            throw new IOException("Incomplete challenge tuple");
        for (var frame : tuple) ProcessGameEndpoint.requireIdentity(frame, generation, expected);
        committed = List.copyOf(tuple);
        if (kind == Commit.READY)
            prepared = true;
        else if (kind == Commit.START)
            started = true;
        else
            tick++;
        return committed;
    }
    private List<ChallengeProtocol.Frame> collect(
            List<CompletableFuture<ChallengeProtocol.Frame>> pending, Duration timeout) throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        List<ChallengeProtocol.Frame> tuple = new ArrayList<>();
        for (var future : pending)
            tuple.add(ProcessGameEndpoint.await(
                    future, Duration.ofNanos(Math.max(1, deadline - System.nanoTime()))));
        return List.copyOf(tuple);
    }
    private void requireHealthy() {
        if (closed || faulted)
            throw new IllegalStateException("Challenge stopped");
    }
    private void abort(Throwable cause) {
        faulted = true;
        try {
            close();
        } catch (RuntimeException cleanup) {
            cause.addSuppressed(cleanup);
        }
    }
    public long tick() {
        return tick;
    }
    public List<ChallengeProtocol.Frame> committed() {
        return committed;
    }
    public synchronized List<Long> pids() {
        return endpoints.stream().map(ProcessGameEndpoint::pid).toList();
    }
    @Override
    public synchronized void close() {
        if (closed)
            return;
        closed = true;
        RuntimeException failure = null;
        for (var endpoint : endpoints) try {
                endpoint.close();
            } catch (RuntimeException e) {
                if (failure == null)
                    failure = e;
                else
                    failure.addSuppressed(e);
            }
        if (failure != null)
            throw failure;
    }
}
