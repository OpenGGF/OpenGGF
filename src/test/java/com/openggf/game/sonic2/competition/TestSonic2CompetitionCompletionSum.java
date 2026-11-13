package com.openggf.game.sonic2.competition;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic2CompetitionCompletionSum {
    @SuppressWarnings("unchecked")
    static List<Sonic2CompetitionResults.Wins> ledger(Sonic2CompetitionSession session) throws Exception {
        var field = Sonic2CompetitionSession.class.getDeclaredField("ledger"); field.setAccessible(true);
        return (List<Sonic2CompetitionResults.Wins>) field.get(session);
    }
    @Test void unsignedSumCarriesGameOverWordsInsteadOfCheckingPresence() throws Exception {
        var session = new Sonic2CompetitionSession();
        var rows = ledger(session);
        java.util.Collections.fill(rows, new Sonic2CompetitionResults.Wins(1, 0));
        assertTrue(session.allResultsCompleted());
        rows.set(0, new Sonic2CompetitionResults.Wins(254, 255));
        assertFalse(session.completed(0));
        assertFalse(session.allResultsCompleted());
        rows.set(0, new Sonic2CompetitionResults.Wins(255, 254));
        assertFalse(session.allResultsCompleted());
        // The ROM does not reject a game-over sentinel itself: without a carry it passes.
        java.util.Collections.fill(rows, new Sonic2CompetitionResults.Wins(0, 0));
        rows.set(0, new Sonic2CompetitionResults.Wins(254, 255));
        assertTrue(session.allResultsCompleted());
        rows.set(1, null);
        assertFalse(session.allResultsCompleted());
    }
}
