package com.openggf.game.sonic2.competition;

import com.openggf.control.*;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.extension.ExtendWith(com.openggf.tests.SingletonResetExtension.class)
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionGameResultsExit {
    @Test void realPlcQueueBlocksStartAndReplacementOwnerIsResolved() throws Exception {
        var session = new Sonic2CompetitionSession();
        var plc = new com.openggf.game.sonic2.resources.Sonic2PlcService(TestEnvironment.currentRom());
        plc.append(1);
        var owner = new java.util.concurrent.atomic.AtomicReference<>(plc);
        var screen = new Sonic2CompetitionScreen(session, mock(Sonic2CompetitionHost.class), null,
                TestEnvironment.currentRom(), owner::get);
        screen.initialize();
        var phase = Sonic2CompetitionSession.class.getDeclaredField("phase"); phase.setAccessible(true);
        phase.set(session, Sonic2CompetitionSession.Phase.GAME_RESULTS);
        var input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0,0,0,0,true,true), PlayerInputState.neutral()));
        screen.update(input);
        assertEquals(Sonic2CompetitionSession.Phase.GAME_RESULTS, session.phase());
        assertFalse(session.resultsPlcReady());
        owner.set(new com.openggf.game.sonic2.resources.Sonic2PlcService(TestEnvironment.currentRom()));
        screen.update(input);
        assertEquals(NativeMenuScreen.Destination.SEGA_SCREEN, screen.consumeExit().destination());
    }
    @Test void finalResultsStartRequestsSega() throws Exception {
        var session = new Sonic2CompetitionSession();
        var screen = new Sonic2CompetitionScreen(session, mock(Sonic2CompetitionHost.class), null,
                TestEnvironment.currentRom());
        screen.initialize();
        var phase = Sonic2CompetitionSession.class.getDeclaredField("phase"); phase.setAccessible(true);
        phase.set(session, Sonic2CompetitionSession.Phase.GAME_RESULTS);
        screen.resultsPlcReady();
        var input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0,0,0,0,true,true), PlayerInputState.neutral()));
        screen.update(input);
        assertEquals(NativeMenuScreen.Destination.SEGA_SCREEN, screen.consumeExit().destination());
        assertEquals(NativeMenuScreen.Exit.NONE, screen.consumeExit());
    }
}
