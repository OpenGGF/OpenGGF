package com.openggf.game.sonic2.competition;

import com.openggf.audio.AudioManager;
import com.openggf.game.SpecialStageViewport;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.extension.ExtendWith(com.openggf.tests.SingletonResetExtension.class)
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitiveHalfpipeFactory {
    @Test void composesRealRomHalfpipeAndExposesItsRewindOwner() throws Exception {
        var graphics = new GraphicsManager(); graphics.initHeadless();
        var session = new Sonic2CompetitionSession();
        var adapter = Sonic2CompetitiveSpecialStageAdapter.create(session, TestEnvironment.currentRom(),
                graphics, SpecialStageViewport.nativeViewport(), AudioManager.createSilent(), () -> 123);
        assertTrue(adapter.supportsStage(6)); assertFalse(adapter.supportsStage(7));
        assertEquals(1, adapter.rewindAdapters().size());
        assertEquals("sonic2.competition.halfpipe", adapter.rewindAdapters().getFirst().key());
        session.updateMenu(com.openggf.control.LogicalInputSnapshot.ofPlayers(
                com.openggf.control.PlayerInputState.of(10,10,0,0,true,true),
                com.openggf.control.PlayerInputState.neutral()), true);
        adapter.load();
        var state = adapter.rewindAdapters().getFirst().capture();
        assertNotNull(state);
        adapter.reset();
    }
}
