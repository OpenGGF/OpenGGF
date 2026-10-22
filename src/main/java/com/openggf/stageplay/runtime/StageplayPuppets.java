package com.openggf.stageplay.runtime;

import com.openggf.sprites.playable.PlayablePuppet;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

import static com.openggf.stageplay.StageplayDocument.*;
import static com.openggf.stageplay.StageplayTimeline.*;

/** Session-owned native simulation. Seeking replays inputs silently; rendering never ticks a body. */
final class StageplayPuppets implements AutoCloseable {
    private static final int CHECKPOINT_INTERVAL = 120, MAX_CHECKPOINTS = 32, BLOCKED_FRAMES = 120;
    private static final double ARRIVAL_X = 4, ARRIVAL_Y = 8, STOP_SPEED = .25;

    @FunctionalInterface interface Factory {
        PlayablePuppet create(StageplayDocument document, SetSource source, Actor actor,
                             ActorKey anchor, PuppetTrack track);
    }

    private final Factory factory;
    private StageplayDocument document;
    private List<Performer> performers = List.of();
    private final TreeMap<Integer, List<Saved>> checkpoints = new TreeMap<>();
    private int shotStart = -1, cursor = -1;
    private java.util.Set<String> inactive = java.util.Set.of();

    StageplayPuppets(Factory factory) { this.factory = Objects.requireNonNull(factory); }

    Frame resolve(StageplayDocument document, Frame authored) {
        Objects.requireNonNull(document); Objects.requireNonNull(authored);
        int frame = authored.frame();
        if (frame < 0 || frame >= document.durationFrames()) throw new IllegalArgumentException("Invalid puppet frame");
        if (!document.equals(this.document)) { reset(); this.document = document; }
        if (document.puppets().isEmpty()) return authored;
        int start = 0;
        SetSource source = document.set();
        for (SceneChange change : document.sceneChanges()) {
            if (change.frame() > frame) break;
            start = change.frame(); source = change.set();
        }
        if (shotStart != start) beginShot(document, source, start);
        var checkpoint = checkpoints.floorEntry(frame);
        if (frame < cursor || (checkpoint != null && checkpoint.getKey() > cursor)) {
            restore(checkpoint.getValue()); cursor = checkpoint.getKey();
        }
        while (cursor < frame) {
            int tick = ++cursor;
            for (Performer performer : performers) advance(performer, tick);
            if ((tick - shotStart + 1) % CHECKPOINT_INTERVAL == 0) {
                checkpoints.put(tick, capture());
                while (checkpoints.size() > MAX_CHECKPOINTS)
                    checkpoints.remove(checkpoints.higherKey(shotStart - 1));
            }
        }
        var poses = new ArrayList<ActorPose>();
        var samples = new ArrayList<PuppetSample>();
        var byId = new HashMap<String, Performer>();
        performers.forEach(value -> byId.put(value.actor.id(), value));
        for (ActorPose pose : authored.actors()) {
            Performer performer = byId.get(pose.actorId());
            if (performer == null) {
                poses.add(pose);
                if (inactive.contains(pose.actorId())) {
                    var track = document.puppets().stream().filter(value -> value.actorId().equals(pose.actorId())).findFirst().orElseThrow();
                    samples.add(new PuppetSample(pose.actorId(), "", PuppetStatus.INACTIVE, 0, 0, 0, false, 0, track.collisionPath()));
                }
                continue;
            }
            var actual = performer.body.sample();
            poses.add(new ActorPose(pose.actorId(), pose.gameId(), pose.character(), actual.x(), actual.y(),
                    actual.animation(), performer.animationTick, actual.mappingFrame(), actual.flipX(),
                    pose.visible(), 1, actual.flipY()));
            var tail = actual.tail();
            samples.add(new PuppetSample(pose.actorId(), performer.goal == null ? "" : performer.goal.id(),
                    performer.status, actual.xSpeed() / 256.0, actual.ySpeed() / 256.0,
                    actual.groundSpeed() / 256.0, actual.airborne(), actual.angle() & 255,
                    performer.track.collisionPath(), tail == null ? null
                    : new PuppetAccessory(tail.mappingFrame(), tail.flipX(), tail.flipY())));
        }
        return new Frame(frame, poses, authored.camera(), authored.captions(), authored.set(), authored.transition(),
                authored.baseCamera(), authored.dialogue(), samples);
    }

    private void beginShot(StageplayDocument document, SetSource source, int start) {
        closePerformers(); checkpoints.clear(); shotStart = -1;
        var prepared = new ArrayList<Performer>();
        var absent = new java.util.HashSet<String>();
        int end = document.sceneChanges().stream().filter(change -> change.frame() > start)
                .mapToInt(SceneChange::frame).min().orElse(document.durationFrames());
        try {
            for (PuppetTrack track : document.puppets()) {
                Actor actor = document.actors().stream().filter(value -> value.id().equals(track.actorId())).findFirst().orElseThrow();
                ActorKey anchor = actor.keys().stream().filter(value -> value.frame() == start).findFirst().orElseThrow();
                boolean everVisible = actor.keys().stream().anyMatch(key -> key.frame() >= start && key.frame() < end && key.visible());
                if (!everVisible) { absent.add(actor.id()); continue; }
                var performer = new Performer(actor, track, factory.create(document, source, actor, anchor, track));
                while (performer.commandIndex < track.commands().size()
                        && track.commands().get(performer.commandIndex).frame() < start) performer.commandIndex++;
                performer.lastProgress = start;
                prepared.add(performer);
            }
        } catch (RuntimeException failure) {
            prepared.forEach(value -> value.body.close()); throw failure;
        }
        performers = List.copyOf(prepared); inactive = java.util.Set.copyOf(absent); shotStart = start; cursor = start - 1;
        checkpoints.put(cursor, capture());
    }

    private static void advance(Performer performer, int frame) {
        var commands = performer.track.commands();
        while (performer.commandIndex < commands.size() && commands.get(performer.commandIndex).frame() == frame) {
            PuppetCommand command = commands.get(performer.commandIndex++);
            if (command.kind() == PuppetCommandKind.JUMP) performer.jumpEnd = frame + command.holdFrames();
            else {
                performer.goal = command; performer.bestDistance = Double.POSITIVE_INFINITY;
                performer.lastProgress = frame;
            }
        }
        var before = performer.body.sample();
        int direction = direction(performer.goal, before);
        performer.body.tick(direction < 0, direction > 0, frame < performer.jumpEnd, frame);
        var after = performer.body.sample();
        performer.animationTick = after.animation() == before.animation() ? performer.animationTick + 1 : 0;
        PuppetCommand goal = performer.goal;
        boolean stopped = Math.abs(after.xSpeed() / 256.0) <= STOP_SPEED
                && Math.abs(after.groundSpeed() / 256.0) <= STOP_SPEED && after.onGround();
        if (goal == null || goal.kind() == PuppetCommandKind.STOP) {
            performer.status = stopped ? PuppetStatus.IDLE : PuppetStatus.STOPPING;
            return;
        }
        double dx = goal.x() - after.x(), dy = goal.y() - after.y();
        if (stopped && Math.abs(dx) <= ARRIVAL_X && Math.abs(dy) <= ARRIVAL_Y) {
            performer.status = PuppetStatus.ARRIVED; performer.lastProgress = frame; return;
        }
        double distance = StrictMath.hypot(dx, dy);
        if (distance < performer.bestDistance - .5) {
            performer.bestDistance = distance; performer.lastProgress = frame;
        }
        performer.status = frame - performer.lastProgress >= BLOCKED_FRAMES ? PuppetStatus.BLOCKED : PuppetStatus.MOVING;
    }

    /** Only ordinary directional inputs are produced: no speed, position or physics constants are assigned. */
    private static int direction(PuppetCommand goal, PlayablePuppet.Observation state) {
        double speed = state.xSpeed() / 256.0;
        double brake = Math.max(1.0 / 256, state.runDecel() / 256.0);
        if (goal == null || goal.kind() == PuppetCommandKind.STOP) return brakingInput(speed, brake);
        double dx = goal.x() - state.x();
        if (Math.abs(dx) <= ARRIVAL_X) return brakingInput(speed, brake);
        int toward = dx < 0 ? -1 : 1;
        if (speed * toward < 0) return toward;
        double stoppingDistance = speed * speed / (2 * brake) + Math.abs(speed) + ARRIVAL_X;
        if (Math.abs(dx) <= stoppingDistance) return brakingInput(speed, brake);
        double wanted = state.maxSpeed() / 256.0 * goal.urgency();
        return Math.abs(speed) >= wanted ? 0 : toward;
    }

    private static int brakingInput(double speed, double brake) {
        // Release below one native braking step so countersteering cannot manufacture a reversal.
        if (Math.abs(speed) <= Math.max(brake, STOP_SPEED)) return 0;
        return speed < 0 ? 1 : -1;
    }

    private List<Saved> capture() {
        return performers.stream().map(value -> new Saved(value.body.capture(), value.commandIndex, value.jumpEnd,
                value.goal, value.bestDistance, value.lastProgress, value.status, value.animationTick)).toList();
    }
    private void restore(List<Saved> states) {
        for (int index = 0; index < performers.size(); index++) {
            Performer value = performers.get(index); Saved state = states.get(index);
            value.body.restore(state.body()); value.commandIndex = state.commandIndex(); value.jumpEnd = state.jumpEnd();
            value.goal = state.goal(); value.bestDistance = state.bestDistance(); value.lastProgress = state.lastProgress();
            value.status = state.status(); value.animationTick = state.animationTick();
        }
    }
    void reset() { closePerformers(); checkpoints.clear(); document = null; inactive = java.util.Set.of(); shotStart = -1; cursor = -1; }
    private void closePerformers() { performers.forEach(value -> value.body.close()); performers = List.of(); }
    @Override public void close() { reset(); }

    private static final class Performer {
        final Actor actor; final PuppetTrack track; final PlayablePuppet body;
        int commandIndex, jumpEnd, lastProgress, animationTick;
        PuppetCommand goal;
        double bestDistance = Double.POSITIVE_INFINITY;
        PuppetStatus status = PuppetStatus.IDLE;
        Performer(Actor actor, PuppetTrack track, PlayablePuppet body) { this.actor = actor; this.track = track; this.body = body; }
    }
    private record Saved(PlayablePuppet.Snapshot body, int commandIndex, int jumpEnd, PuppetCommand goal,
                         double bestDistance, int lastProgress, PuppetStatus status, int animationTick) { }
}
