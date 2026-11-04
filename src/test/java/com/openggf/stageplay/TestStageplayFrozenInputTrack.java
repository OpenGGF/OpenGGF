package com.openggf.stageplay;

import com.openggf.route.InputTape;
import com.openggf.route.PadRow;
import com.openggf.route.PhysicalButton;
import com.openggf.route.PlayerSlot;
import com.openggf.route.RouteFiles;
import com.openggf.stageplay.route.StageplayRoutes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

/** Frozen input tracks: lossless rows in the document, exclusive with commands, one track per actor interval. */
class TestStageplayFrozenInputTrack {
    private static StageplayDocument scene(List<PuppetCommand> commands) {
        return StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224).withDurationFrames(120)
                .withActors(List.of(new Actor("hero", "Hero", "s2", "sonic",
                        List.of(new ActorKey(0, 100, 200, 0, -1, Facing.RIGHT, true, 1, Interpolation.HOLD)))))
                .withPuppets(List.of(new PuppetTrack("hero", false, 0, commands)));
    }

    @Test
    void everyPhysicalRowRoundTripsThroughTheTrackAndTheDocumentCodec() throws Exception {
        var rows = new InputTape.Builder();
        for (int mask = 0; mask < 64; mask++) rows.add(new PadRow(mask * 4 % 256, 0));
        rows.add(PadRow.of(PlayerSlot.P1, PhysicalButton.START, PhysicalButton.C));
        InputTape tape = rows.build();
        var track = StageplayRoutes.track("rows", "hero", 10, tape);
        assertEquals(10 + tape.size(), track.endFrame());
        var document = StageplayRoutes.install(scene(List.of()), track);
        var decoded = StageplayStore.decode(StageplayStore.encode(document));
        assertEquals(document, decoded);
        assertEquals(tape, StageplayRoutes.tape(decoded.inputTracks().getFirst()));
    }

    @Test
    void installingRowsRemovesThatActorsCommandsInsideTheIntervalOnly() {
        var before = new PuppetCommand("before", 2, PuppetCommandKind.STOP, 0, 0, 0, 0);
        var inside = new PuppetCommand("inside", 20, PuppetCommandKind.STOP, 0, 0, 0, 0);
        var after = new PuppetCommand("after", 90, PuppetCommandKind.STOP, 0, 0, 0, 0);
        var track = StageplayRoutes.track("rows", "hero", 10, RouteFiles.tapeFromScript("30 R"));
        var document = StageplayRoutes.install(scene(List.of(before, inside, after)), track);
        assertEquals(List.of(before, after), document.puppets().getFirst().commands());
        var replacement = StageplayRoutes.track("rows2", "hero", 20, RouteFiles.tapeFromScript("5 L"));
        assertEquals(List.of(replacement), StageplayRoutes.install(document, replacement).inputTracks(),
                "an overlapping track of the same actor is replaced");
    }

    @Test
    void commandsAndRowsCannotDriveOneActorInOneInterval() {
        var inside = new PuppetCommand("inside", 20, PuppetCommandKind.STOP, 0, 0, 0, 0);
        var track = StageplayRoutes.track("rows", "hero", 10, RouteFiles.tapeFromScript("30 R"));
        assertThrows(IllegalArgumentException.class, () -> scene(List.of(inside)).withInputTracks(List.of(track)));
        var overlapping = StageplayRoutes.track("rows2", "hero", 30, RouteFiles.tapeFromScript("30 R"));
        assertThrows(IllegalArgumentException.class, () -> scene(List.of()).withInputTracks(List.of(track, overlapping)));
        var tooLong = StageplayRoutes.track("rows3", "hero", 100, RouteFiles.tapeFromScript("30 R"));
        assertThrows(IllegalArgumentException.class, () -> scene(List.of()).withInputTracks(List.of(tooLong)));
        var tampered = new FrozenInputTrack("rows4", 0, track.lanes(), List.of("30 L"), track.sha256());
        assertThrows(IllegalArgumentException.class, () -> StageplayRoutes.tape(tampered));
    }
}
