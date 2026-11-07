package openggf.timeattack;
import com.openggf.mods.run.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TestSwapEventRecording {
    private static AttemptStartDescriptor competitionStart() {
        return new AttemptStartDescriptor("s2", 0, 0, "sonic", "fp", TrackKind.ACT,
                CompetitionRoomSettings.Mode.COMPETITION, MonitorRule.COMPETITION_ITEMS,
                com.openggf.game.session.GameplayRunPolicy.DeathRestart.FAST_FROM_CHECKPOINT, true);
    }
    @Test void timeAttackCannotCarryRaceEvents() {
        var recording = new AttemptInputRecording(new AttemptStartDescriptor("s2", 0, 0, "sonic", "fp"));
        assertThrows(IllegalArgumentException.class, () -> recording.appendEvent(0, new RaceEvent.SwapFallback(1)));
    }

    @Test void boundedEventsAndFrameCap() {
        var a = new AttemptInputRecording(competitionStart());
        for (int i = 0; i < AttemptInputRecording.MAX_EVENTS; i++) {
            a.appendEvent(i, new RaceEvent.SwapFallback(i)); a.appendFrame(0, false);
        }
        assertThrows(IllegalArgumentException.class, () -> a.appendEvent(64, new RaceEvent.SwapFallback(64)));
        for (int i = a.frameCount(); i < AttemptInputRecording.MAX_FRAMES; i++) a.appendFrame(0, false);
        assertThrows(IllegalStateException.class, () -> a.appendFrame(0, false));
        assertEquals(37800, a.frameCount());
    }
    @Test void eventRoundTripsAndPayloadChangesHash() throws Exception {
        var start = competitionStart();
        var a = new AttemptInputRecording(start);
        var target = new SwapTarget(100, 200, 256, -256, true, false, 2, false, 1, 75, 3);
        a.appendEvent(0, new RaceEvent.SwapApply(42, target));
        a.appendFrame(8, false);
        var replay = AttemptInputRecording.decodeChecked(a.encode());
        assertEquals(a.events(), replay.events());
        assertArrayEquals(a.sha256(), replay.sha256());
        var b = new AttemptInputRecording(start);
        b.appendEvent(0, new RaceEvent.SwapFallback(42));
        b.appendFrame(8, false);
        assertFalse(java.util.Arrays.equals(a.sha256(), b.sha256()));
        assertThrows(IllegalArgumentException.class, () -> a.appendEvent(0, new RaceEvent.SwapFallback(43)));
        assertThrows(java.io.IOException.class, () -> AttemptInputRecording.decodeChecked(new byte[]{1}));
    }
}
