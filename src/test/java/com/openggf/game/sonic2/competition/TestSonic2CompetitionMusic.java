package com.openggf.game.sonic2.competition;

import com.openggf.audio.AudioManager;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.extension.ExtendWith(com.openggf.tests.SingletonResetExtension.class)
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionMusic {
    @Test void nativePlaylistUsesSparseEngineIdsAndResultsCompareSurvivesRewind() throws Exception {
        var rom = TestEnvironment.currentRom();
        var session = new Sonic2CompetitionSession();
        var audio = mock(AudioManager.class);
        session.playSelectMusic(audio);
        verify(audio).playMusic(0x89);
        assertEquals(0x91, Sonic2Music.OPTIONS.nativeId);
        int[] zones = {0, 11, 12}, nativeIds = {0x8C, 0x83, 0x88}, engineIds = {0x91, 0x85, 0x80};
        for (int i = 0; i < 3; i++) {
            clearInvocations(audio);
            assertEquals(nativeIds[i], rom.readByte(0x3EB2 + zones[i]) & 255);
            session.playLevelMusic(rom, zones[i], audio);
            verify(audio).playMusic(engineIds[i]);
            assertEquals(nativeIds[i], session.levelMusic());
        }
        assertEquals(0x92, Sonic2Music.SPECIAL_STAGE.nativeId);
        assertEquals(0x88, Sonic2CompetitionSession.engineMusic(0x92));
        clearInvocations(audio);
        session.playResultsMusic(audio);
        assertEquals(0x81, session.levelMusic());
        verify(audio).playMusic(0x92);
        var saved = session.capture(); session.reset(); session.restore(saved);
        session.playResultsMusic(audio);
        verify(audio, times(1)).playMusic(0x92);
    }
    @Test void nativeResultsIdentitySuppressesRestartAfterRestore() {
        var session = new Sonic2CompetitionSession();
        var audio = mock(AudioManager.class);
        session.playResultsMusic(audio);
        var saved = session.capture(); session.reset(); session.restore(saved);
        session.playResultsMusic(audio);
        verify(audio, times(1)).playMusic(0x92);
        assertEquals(0x81, session.levelMusic());
    }
    @Test void completedSelectionSubmitsNativeErrorWithoutStartingLevel() throws Exception {
        var audio = com.openggf.game.GameServices.audio();
        audio.setBackend(new com.openggf.audio.NullAudioBackend());
        var events = new java.util.ArrayList<com.openggf.game.sonic2.audio.Sonic2SoundRequestService.Event>();
        var profile = new com.openggf.game.sonic2.audio.Sonic2AudioProfile(events::add);
        audio.setRom(TestEnvironment.currentRom()); audio.setAudioProfile(profile); audio.setSoundMap(profile.getSoundMap());
        var session = new Sonic2CompetitionSession();
        var screen = new Sonic2CompetitionScreen(session, mock(Sonic2CompetitionHost.class), null, TestEnvironment.currentRom());
        screen.initialize();
        var rows = TestSonic2CompetitionCompletionSum.ledger(session);
        rows.set(0, new Sonic2CompetitionResults.Wins(1, 0)); rows.set(1, new Sonic2CompetitionResults.Wins(1, 0));
        var input = new com.openggf.control.InputHandler();
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(
                com.openggf.control.PlayerInputState.of(0,0,0,0,true,true), com.openggf.control.PlayerInputState.neutral()));
        events.clear(); screen.update(input);
        assertEquals(Sonic2CompetitionSession.Phase.SELECT, session.phase());
        audio.presentFrame(com.openggf.audio.presentation.PresentationMode.FORWARD);
        assertTrue(events.stream().filter(com.openggf.game.sonic2.audio.Sonic2SoundRequestService.Transfer.class::isInstance)
                .map(com.openggf.game.sonic2.audio.Sonic2SoundRequestService.Transfer.class::cast)
                .anyMatch(event -> event.rawRequestId() == 0xED), events.toString());
    }
    @Test void titleClearsButRetailOptionsRetainsEmeraldsThroughActReset() {
        var source = new com.openggf.game.GameStateManager();
        source.restoreSaveProgress(3, 0, java.util.List.of(0,1,2,3,4,5,6), java.util.List.of());
        var session = new Sonic2CompetitionSession();
        session.initializeEntry(source, true);
        assertEquals(7, session.gameState(0).getCollectedChaosEmeraldIndices().size());
        session.playerState(0).resetAct();
        assertEquals(7, session.gameState(0).getCollectedChaosEmeraldIndices().size());
        session.initializeEntry(source, false);
        assertTrue(session.gameState(0).getCollectedChaosEmeraldIndices().isEmpty());
    }
}
