package com.openggf.game.patch;

import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestGameplayTeamAvailability {

    @Test
    void unavailableMainCharacterIsReplacedWithStockSonic() {
        SaveSessionContext context = SaveSessionContext.forSlot("s2", 1,
                new SelectedTeam("knuckles", List.of()), 0, 0);

        SaveSessionContext sanitized = GameplayTeamAvailability.sanitizeForLaunch(
                context, "s2", List.of("sonic", "tails"));

        assertEquals("sonic", sanitized.selectedTeam().mainCharacter());
        assertEquals(1, sanitized.activeSlot().orElseThrow());
    }

    @Test
    void availableTeamPassesThroughUnchanged() {
        SaveSessionContext context = SaveSessionContext.forSlot("s2", 1,
                new SelectedTeam("knuckles", List.of("tails")), 0, 0);

        assertSame(context, GameplayTeamAvailability.sanitizeForLaunch(
                context, "s2", List.of("sonic", "tails", "knuckles")));
    }

    @Test
    void unavailableSidekicksAreDroppedWithoutRewritingOtherContextState() {
        SaveSessionContext context = SaveSessionContext.noSave("s2",
                new SelectedTeam("sonic", List.of("knuckles", "tails")), 3, 1);
        context.markClear();

        SaveSessionContext sanitized = GameplayTeamAvailability.sanitizeForLaunch(
                context, "s2", List.of("sonic", "tails"));

        assertEquals(List.of("tails"), sanitized.selectedTeam().sidekicks());
        assertEquals(3, sanitized.startZone());
        assertEquals(1, sanitized.startAct());
        assertEquals(true, sanitized.isClear());
    }
}
