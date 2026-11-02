package com.openggf.stageplay.route;

import com.openggf.route.InputTape;
import com.openggf.route.RouteFiles;
import com.openggf.stageplay.StageplayDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * Stageplay's route front-end translation: frozen input tracks to and from route tapes, and the
 * document patch that installs one. Search and qualification are the shared planner's.
 */
public final class StageplayRoutes {
    private StageplayRoutes() {
    }

    /** The decoded rows of a frozen track, verified against its hash. */
    public static InputTape tape(StageplayDocument.FrozenInputTrack track) {
        InputTape tape = RouteFiles.tapeFromScript(String.join("\n", track.rows()));
        if (!tape.hash().sha256().equals(track.sha256())) {
            throw new IllegalArgumentException("frozen track " + track.id() + " rows do not match their SHA-256");
        }
        return tape;
    }

    /** A frozen track binding {@code actorId} to P1 for {@code tape}, starting at {@code startFrame}. */
    public static StageplayDocument.FrozenInputTrack track(String id, String actorId, int startFrame, InputTape tape) {
        var lines = new ArrayList<String>();
        for (String line : RouteFiles.scriptText(tape).split("\n")) {
            if (!line.isBlank() && !line.startsWith("#")) lines.add(line);
        }
        return new StageplayDocument.FrozenInputTrack(id, startFrame,
                List.of(new StageplayDocument.LaneBinding(actorId, StageplayDocument.ControllerLane.P1)),
                List.copyOf(lines), tape.hash().sha256());
    }

    /**
     * Installs {@code track} for its actor: commands of that actor inside the track's interval are
     * removed (rows, not commands, drive it there), and any frozen track of that actor overlapping
     * the interval is replaced. Everything else is unchanged.
     */
    public static StageplayDocument install(StageplayDocument document, StageplayDocument.FrozenInputTrack track) {
        String actor = track.lanes().getFirst().actorId();
        int start = track.startFrame();
        int end = track.endFrame();
        var puppets = document.puppets().stream().map(p -> {
            if (!p.actorId().equals(actor)) return p;
            var commands = p.commands().stream().filter(c -> c.frame() < start || c.frame() >= end).toList();
            return new StageplayDocument.PuppetTrack(p.actorId(), p.snapToFloor(), p.collisionPath(), commands);
        }).toList();
        var tracks = new ArrayList<StageplayDocument.FrozenInputTrack>();
        for (var existing : document.inputTracks()) {
            boolean overlaps = existing.binds(actor) && existing.startFrame() < end && start < existing.endFrame();
            if (!overlaps) tracks.add(existing);
        }
        tracks.add(track);
        tracks.sort(java.util.Comparator.comparingInt(StageplayDocument.FrozenInputTrack::startFrame));
        return document.withPuppets(puppets).withInputTracks(tracks);
    }
}
