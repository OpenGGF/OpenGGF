package openggf.racing.server.verifier;

import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.mods.run.*;
import com.openggf.net.ghost.GhostFrameCodec;
import com.openggf.tests.rules.*;
import openggf.timeattack.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestAttemptReplayHarnessCheckpoint {
    @Test void inputOnlyCompetitionDeathReplaysToLiveGhostHash(@TempDir Path root) throws Exception {
        var rom=Path.of(System.getProperty("sonic2.rom.path","s2.gen"));
        var recording=new AttemptInputRecording(new AttemptStartDescriptor("s2",1,1,"sonic",
                AttemptReplayHarness.fingerprintForRom(rom),TrackKind.ACT,CompetitionRoomSettings.Mode.COMPETITION,
                MonitorRule.OFF,GameplayRunPolicy.DeathRestart.FAST_FROM_CHECKPOINT,false));
        for(int i=0;i<1800;i++) recording.appendFrame(8,false);
        var live=TestAttemptReplayHarness.captureLive(recording,rom,root);
        assertTrue(live.stream().anyMatch(f->f.respawnReset()),"natural input-only route must include a death reload");
        var replay=AttemptReplayHarness.replay(recording,rom);
        assertNull(replay.failureReason());
        assertEquals(live.size(),replay.framesSimulated());
        var hash=java.security.MessageDigest.getInstance("SHA-256");
        byte[] bytes=new byte[GhostFrameCodec.BYTES];
        for(var f:live){GhostFrameCodec.encode(f,bytes,0);hash.update(bytes);}
        assertEquals(java.util.HexFormat.of().formatHex(hash.digest()),replay.ghostStreamHashHex());
        assertEquals(0,replay.firstInputFrame());
    }
}
