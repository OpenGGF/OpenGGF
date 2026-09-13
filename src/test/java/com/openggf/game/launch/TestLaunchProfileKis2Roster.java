package com.openggf.game.launch;

import com.openggf.game.launch.LaunchProfile.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.openggf.game.MasterTitleScreen.GameEntry.SONIC_2;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Knuckles in Sonic 2 ships Knuckles alone (KiS2 {@code Level_SetPlayerMode}
 * forces Sonic Alone, {@code ObjPtr_Tails = ObjNull}). Entering the
 * patch-backed main selects that roster; the sidekick row stays editable.
 */
class TestLaunchProfileKis2Roster {

    private static final List<String> PATCH = List.of("knuckles");

    @Test
    void enteringThePatchBackedMainDropsTheStockSidekick() {
        LaunchProfile stock = LaunchProfile.stockFor(SONIC_2);
        assertEquals("sonic", stock.mainCharacter());
        assertEquals("tails", stock.sidekick());

        LaunchProfile tails = stock.withNext(Row.MAIN_CHARACTER, SONIC_2, PATCH);
        assertEquals("tails", tails.mainCharacter());
        assertEquals("tails", tails.sidekick(), "stock mains keep the stock sidekick");

        LaunchProfile knuckles = tails.withNext(Row.MAIN_CHARACTER, SONIC_2, PATCH);
        assertEquals("knuckles", knuckles.mainCharacter());
        assertEquals("none", knuckles.sidekick());
        assertEquals(true, knuckles.isCharacterPairStandard(SONIC_2, PATCH));
    }

    @Test
    void sidekickRowRemainsEditableAfterEnteringKnuckles() {
        LaunchProfile knuckles = LaunchProfile.stockFor(SONIC_2)
                .withPrevious(Row.MAIN_CHARACTER, SONIC_2, PATCH);
        assertEquals("knuckles", knuckles.mainCharacter());
        assertEquals("none", knuckles.sidekick());

        // S2 sidekick order is [tails, none, sonic]: previous of none is tails.
        LaunchProfile withTails = knuckles.withPrevious(Row.SIDEKICK, SONIC_2, PATCH);
        assertEquals("knuckles", withTails.mainCharacter());
        assertEquals("tails", withTails.sidekick());
        assertEquals(false, withTails.isCharacterPairStandard(SONIC_2, PATCH));
    }

    @Test
    void leavingKnucklesKeepsWhateverSidekickWasChosen() {
        LaunchProfile knuckles = LaunchProfile.stockFor(SONIC_2)
                .withPrevious(Row.MAIN_CHARACTER, SONIC_2, PATCH)
                .withPrevious(Row.SIDEKICK, SONIC_2, PATCH);
        assertEquals("tails", knuckles.sidekick());
        LaunchProfile back = knuckles.withNext(Row.MAIN_CHARACTER, SONIC_2, PATCH);
        assertEquals("sonic", back.mainCharacter());
        assertEquals("tails", back.sidekick());
    }
}
