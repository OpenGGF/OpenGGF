package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage.SwapTarget;
import com.openggf.net.protocol.ControlMessage;
import java.util.*;
import java.util.function.*;

/** Room-thread serialised swap authority. Targets are exclusively validated hub observations. */
public final class HostTeleportSwap {
    public record Observation(int slot, int frame, long seenAt, boolean hurt, double progress, SwapTarget target) { }
    public record Decision(int requester, int attemptId, int requestFrame, long hubMillis, Observation requesterState,
                           List<Observation> candidates, long swapId, ControlMessage.TeleportSwap command,
                           Map<Integer, ControlMessage.TeleportOutcome> outcomes, boolean tainted) {
        public Decision { candidates = List.copyOf(candidates); outcomes = Map.copyOf(outcomes); }
    }
    private final List<Decision> log = new ArrayList<>();
    private final Map<Integer, Long> pending = new HashMap<>();
    private final Map<Integer, Integer> cooldown = new HashMap<>(), requests = new HashMap<>();
    private record Deferred(int slot, int attempt, int frame, long round, long expiresAt, int cap) { }
    private final Map<Integer, Deferred> deferred = new LinkedHashMap<>();
    private static final int MAX_RECORDED_EVENTS = 64;
    private final Map<Integer, Integer> recordedEvents = new HashMap<>();
    private long nextId;
    public static List<Decision> logFor(RoomHost room) { return room.swapLog(); }
    public List<Decision> log() { return List.copyOf(log); }
    void clear() { log.clear(); pending.clear(); cooldown.clear(); requests.clear(); deferred.clear(); recordedEvents.clear(); }
    ControlMessage request(int slot, int attempt, int frame, long round, long now, int cap,
                           Collection<Observation> observations, BiConsumer<Integer, ControlMessage> send) {
        if (deferred.containsKey(slot)) throw new IllegalArgumentException("duplicate queued swap request");
        int sourceCap = Math.min(cap, MAX_RECORDED_EVENTS);
        if (requests.getOrDefault(slot, 0) >= sourceCap || recordedEvents.getOrDefault(slot, 0) >= MAX_RECORDED_EVENTS)
            throw new IllegalArgumentException("swap source or recording cap");
        Observation self = observations.stream().filter(o -> o.slot() == slot).findFirst().orElse(null);
        if (self == null) {
            if (frame < 0 || frame > GhostStreamValidator.MAX_FRAME_LEAD)
                throw new IllegalArgumentException("swap request without plausible startup frame");
            deferred.put(slot, new Deferred(slot, attempt, frame, round, now + 250, cap));
            return null;
        }
        if (frame < self.frame() - 180 || frame > self.frame() + 12)
            throw new IllegalArgumentException("swap request without plausible fresh stream");
        if (pending.containsKey(slot) || frame > self.frame()) {
            if (deferred.putIfAbsent(slot, new Deferred(slot, attempt, frame, round,
                    now + (pending.containsKey(slot) ? 5000 : 250), cap)) != null)
                throw new IllegalArgumentException("duplicate queued swap request");
            return null;
        }
        if (self.hurt() || now - self.seenAt() > 500) {
            requests.merge(slot, 1, Integer::sum);
            return noPartner(slot, attempt, frame, now, self, List.of(), send);
        }
        if (!eligible(self, now)) throw new IllegalArgumentException("swap cooldown or stale stream");
        requests.merge(slot, 1, Integer::sum);
        List<Observation> candidates = observations.stream().filter(o -> o.slot() != slot && eligible(o, now) && !deferred.containsKey(o.slot()) && incomingCapacity(o.slot(), sourceCap)).toList();
        Comparator<Observation> order = Comparator.comparingInt((Observation o) -> o.progress() > self.progress() ? 0 : 1)
                .thenComparingDouble(o -> Math.abs(o.progress() - self.progress())).thenComparingInt(Observation::slot);
        Observation partner = candidates.stream().min(order).orElse(null);
        if (partner == null) return noPartner(slot, attempt, frame, now, self, candidates, send);
        var command = new ControlMessage.TeleportSwap(++nextId, round, slot, partner.slot(),
                self.target(), partner.target(), self.frame(), partner.frame());
        recordedEvents.merge(slot, 1, Integer::sum);
        recordedEvents.merge(partner.slot(), 1, Integer::sum);
        pending.put(slot, nextId); pending.put(partner.slot(), nextId);
        log.add(new Decision(slot, attempt, frame, now, self, candidates, command.swapId(), command, Map.of(), false));
        send.accept(slot, command); send.accept(partner.slot(), command); return command;
    }
    private ControlMessage.TeleportNoPartner noPartner(int slot, int attempt, int frame, long now,
            Observation self, List<Observation> candidates, BiConsumer<Integer, ControlMessage> send) {
        var reply = new ControlMessage.TeleportNoPartner(++nextId, attempt, frame);
        recordedEvents.merge(slot, 1, Integer::sum);
        pending.put(slot, reply.swapId());
        log.add(new Decision(slot, attempt, frame, now, self, candidates, reply.swapId(), null, Map.of(), false));
        send.accept(slot, reply);
        return reply;
    }
    void onPlayerLeft(int slot) { deferred.remove(slot); }
    void drain(long now, Collection<Observation> observations, BiConsumer<Integer, ControlMessage> send,
               BiConsumer<Integer, String> violation) {
        for (Deferred d : List.copyOf(deferred.values())) {
            Observation self = observations.stream().filter(o -> o.slot() == d.slot()).findFirst().orElse(null);
            if (now > d.expiresAt()) {
                deferred.remove(d.slot()); violation.accept(d.slot(), "queued swap expired or participant left");
            } else if (self != null && !pending.containsKey(d.slot())
                    && self.frame() >= d.frame() && self.frame() >= cooldown.getOrDefault(d.slot(), Integer.MIN_VALUE)) {
                deferred.remove(d.slot());
                try { request(d.slot(), d.attempt(), d.frame(), d.round(), now, d.cap(), observations, send); }
                catch (IllegalArgumentException invalid) { violation.accept(d.slot(), invalid.getMessage()); }
            }
        }
    }
    // Recording v2 permits 64 events, including partner swaps and solo fallbacks.
    // Reserve capacity for the recipient's remaining native pickup requests.
    private boolean incomingCapacity(int slot, int sourceCap) {
        return recordedEvents.getOrDefault(slot, 0)
                + Math.max(0, sourceCap - requests.getOrDefault(slot, 0)) < MAX_RECORDED_EVENTS;
    }
    private boolean eligible(Observation o, long now) {
        return o != null && !o.hurt() && now - o.seenAt() <= 500 && !pending.containsKey(o.slot())
                && o.frame() >= cooldown.getOrDefault(o.slot(), Integer.MIN_VALUE);
    }
    void outcome(int slot, ControlMessage.TeleportOutcome outcome) {
        if (!Objects.equals(pending.get(slot), outcome.swapId())) throw new IllegalArgumentException("unknown swap outcome");
        for (int i = 0; i < log.size(); i++) {
            Decision d = log.get(i); var c = d.command();
            if (d.swapId() != outcome.swapId()) continue;
            int first = c == null ? d.requestFrame() : c.slotA() == slot ? c.sentAtFrameA() : c.sentAtFrameB();
            if (outcome.applyFrame() < first || outcome.applyFrame() > first + 180
                    || !("APPLIED".equals(outcome.outcome()) && c != null || "FALLBACK".equals(outcome.outcome())))
                throw new IllegalArgumentException("invalid swap outcome window");
            var outcomes = new HashMap<>(d.outcomes()); outcomes.put(slot, outcome);
            log.set(i, new Decision(d.requester(), d.attemptId(), d.requestFrame(), d.hubMillis(), d.requesterState(), d.candidates(), d.swapId(), c, outcomes, d.tainted()));
            pending.remove(slot);
            if ("APPLIED".equals(outcome.outcome())) cooldown.put(slot, outcome.applyFrame() + 64);
            return;
        }
    }
    void taint(int source) {
        for (int i = 0; i < log.size(); i++) {
            Decision d = log.get(i); var c = d.command();
            if (c != null && (c.slotA() == source || c.slotB() == source))
                log.set(i, new Decision(d.requester(), d.attemptId(), d.requestFrame(), d.hubMillis(), d.requesterState(), d.candidates(), d.swapId(), c, d.outcomes(), true));
        }
    }
}
