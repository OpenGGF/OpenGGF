package com.openggf.game.rewind.coverage;

import com.openggf.game.sonic2.Sonic2SpecialStageProvider;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageManager;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageManager;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestStaticStateRewindCoverageBaseline {
    @Test
    void providerOwnedManagersHaveExplicitOwnersInsteadOfLexicalStaticCallCoverage() {
        assertEquals(Map.of(
                Sonic2SpecialStageManager.class.getName(), Sonic2SpecialStageProvider.class.getName(),
                Sonic3kSpecialStageManager.class.getName(), Sonic3kSpecialStageProvider.class.getName()),
                StaticStateRewindCoverageAnalyzer.providerOwners());
        Set<String> gaps = StaticStateRewindCoverageAnalyzer.analyzeGapKeys();
        for (String owner : StaticStateRewindCoverageAnalyzer.providerOwners().keySet()) {
            assertFalse(gaps.contains(owner + "#missingRewindAdapter"));
        }
    }

    @Test
    void resolvedExemptionsFailInsteadOfRemainingAvailableForRecurrence() {
        String stale = Sonic2SpecialStageManager.class.getName() + "#missingRewindAdapter";
        AssertionError failure = assertThrows(AssertionError.class,
                () -> TestStaticStateRewindCoverageGuard.assertBaselineMatches(Set.of(), Set.of(stale)));
        assertTrue(failure.getMessage().contains(stale));
    }

    @Test
    void aStaleExemptionCannotPayForANewMissingAdapter() {
        AssertionError failure = assertThrows(AssertionError.class,
                () -> TestStaticStateRewindCoverageGuard.assertBaselineMatches(
                        Set.of("new.Manager#missingRewindAdapter"), Set.of("old.Manager#missingRewindAdapter")));
        assertTrue(failure.getMessage().contains("new.Manager#missingRewindAdapter"));
    }
}
