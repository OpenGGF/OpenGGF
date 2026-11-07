package openggf.racing.server.verifier;

import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.mods.run.*;
import openggf.timeattack.*;
import org.junit.jupiter.api.Test;
import com.openggf.tests.rules.*;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestAttemptReplayHarnessArena {
    @Test void nativeCountdownIsReplayedAndClockStartsOnGo() {
        var rom = Path.of(System.getProperty("s3k.rom.path","s3k.gen"));
        var recording = new AttemptInputRecording(new AttemptStartDescriptor("s3k",1,0,"sonic",
                AttemptReplayHarness.fingerprintForRom(rom),TrackKind.ARENA,
                CompetitionRoomSettings.Mode.COMPETITION,MonitorRule.OFF,
                GameplayRunPolicy.DeathRestart.FAST_FROM_CHECKPOINT,false));
        for (int i=0;i<300;i++) recording.appendFrame(0,false);
        var first = AttemptReplayHarness.replay(recording,rom);
        var second = AttemptReplayHarness.replay(recording,rom);
        assertNull(first.failureReason());
        assertEquals(first,second);
        assertEquals(300,first.framesSimulated());
        assertFalse(first.finished());
        assertEquals(MassStartRelease.ARENA_LEAD_FRAMES,first.firstInputFrame());
        assertTrue(first.lapFrames().isEmpty());
    }
}
