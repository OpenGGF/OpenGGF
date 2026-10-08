package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameStateManager;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.GameModule;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.save.SessionSaveRequests;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.sonic3k.S3kFullSaveGame;
import com.openggf.game.save.SaveReason;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TestS3kFullSaveGameBoundary {
    @Test
    void actTwoTallyClearsAllBitsAndRepeatsAfterStateRestore() {
        checkTally(0, 1, true);
    }

    @Test
    void skySanctuaryActOneTallyRunsFullSaveGame() {
        checkTally(0x0A, 0, true);
    }

    @Test
    void ordinaryActOneTallyPreservesCollection() {
        checkTally(0, 0, false);
    }

    @Test
    void fullSaveGameClearsWithMissingPersistenceAndNoSaveContexts() {
        try (var sessions = mockStatic(SessionManager.class)) {
            for (int kind = 0; kind < 3; kind++) {
                var state = new GameStateManager();
                state.markSpecialRingCollected(31);
                state.markSpecialRingCollected(0);
                WorldSession world = kind == 0 ? null : mock(WorldSession.class);
                if (kind == 2) {
                    when(world.getSaveSessionContext()).thenReturn(SaveSessionContext.noSave(
                            "s3k", new SelectedTeam("sonic", List.of("tails")), 0, 1));
                    when(world.getGameModule()).thenReturn(mock(GameModule.class));
                }
                sessions.when(SessionManager::getCurrentWorldSession).thenReturn(world);
                var services = new TestObjectServices() {
                    @Override public void requestSessionSave(SaveReason reason) {
                        SessionSaveRequests.requestCurrentSessionSave(reason);
                    }
                }.withGameState(state);
                S3kFullSaveGame.complete(services);
                assertEquals(0, state.capture().collectedSpecialRings());
            }
        }
    }

    @Test
    void persistenceOnlyRequestsAndLevelResetPreserveCollection() {
        var state = new GameStateManager();
        state.markSpecialRingCollected(31);
        state.markSpecialRingCollected(1);
        int mask = state.capture().collectedSpecialRings();
        state.resetForLevel();
        assertEquals(mask, state.capture().collectedSpecialRings());
        try (var sessions = mockStatic(SessionManager.class)) {
            sessions.when(SessionManager::getCurrentWorldSession).thenReturn(null);
            for (SaveReason reason : List.of(SaveReason.PROGRESSION_SAVE,
                    SaveReason.SPECIAL_STAGE_SAVE, SaveReason.LIVES_CONTINUES_SAVE)) {
                SessionSaveRequests.requestCurrentSessionSave(reason);
                assertEquals(mask, state.capture().collectedSpecialRings());
            }
        }
    }

    private void checkTally(int zone, int act, boolean fullSave) {
        var state = new GameStateManager();
        for (int bit = 0; bit < 32; bit++) state.markSpecialRingCollected(bit);
        var before = state.capture();
        var services = new RecordingServices(zone, state);
        var results = ObjectConstructionContext.withRewindActiveRestore(() ->
                ObjectConstructionContext.construct(services, () ->
                        new S3kResultsScreenObjectInstance(PlayerCharacter.SONIC_AND_TAILS, act,
                                0, 0, S3kSignpostInstance.ResultsChildTimingAdjustment.NONE, false, false)));
        results.setServices(services);
        var rewind = RewindCaptureContext.withIdentityTable(new RewindIdentityTable());
        var resultBefore = results.captureRewindState(rewind);
        // Actual zero-increment Results tally completion dispatch, not a load/reset seam.
        results.updateTally();
        assertEquals(fullSave ? 0 : -1, state.capture().collectedSpecialRings());
        assertEquals(fullSave ? List.of(SaveReason.PROGRESSION_SAVE) : List.of(), services.requests);
        if (fullSave) assertEquals(List.of(0), services.maskAtRequest);
        var completed = state.capture();
        state.restore(before);
        results.restoreRewindState(resultBefore, rewind);
        services.requests.clear();
        services.maskAtRequest.clear();
        results.updateTally();
        assertEquals(completed.collectedSpecialRings(), state.capture().collectedSpecialRings(),
                "captured mask and Results state replay completion");
        assertEquals(fullSave ? List.of(SaveReason.PROGRESSION_SAVE) : List.of(), services.requests);
    }

    private static final class RecordingServices extends TestObjectServices {
        private final int zone;
        private final List<SaveReason> requests = new ArrayList<>();
        private final List<Integer> maskAtRequest = new ArrayList<>();

        RecordingServices(int zone, GameStateManager state) {
            this.zone = zone;
            withGameState(state);
        }

        @Override public int romZoneId() { return zone; }
        @Override public void requestSessionSave(SaveReason reason) {
            requests.add(reason);
            maskAtRequest.add(gameState().capture().collectedSpecialRings());
        }
    }
}
