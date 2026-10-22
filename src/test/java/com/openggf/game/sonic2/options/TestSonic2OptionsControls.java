package com.openggf.game.sonic2.options;

import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.nativemenu.NativeMenuScreen;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.sprites.playable.AbstractPlayableSprite.*;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic2OptionsControls {
    @Test
    void bothControllersOrPressEdgesInRomOrderWithoutHeldRepeat() {
        var state = new Sonic2OptionsState();
        var sounds = new ArrayList<Integer>();
        state.update(sample(INPUT_UP, 0, false, INPUT_RIGHT, 0, false), sounds::add);
        assertEquals(2, state.selectedRow());
        assertEquals(1, state.soundTestValue(), "P1 row navigation precedes P2 choice changes");
        var held = PlayerInputState.of(INPUT_DOWN | INPUT_RIGHT, 0, InputActionMasks.ACTION_A, 0, true, false);
        for (int frame = 0; frame < 90; frame++) {
            assertEquals(NativeMenuScreen.Exit.NONE,
                    state.update(LogicalInputSnapshot.ofPlayers(held, held), sounds::add));
        }
        assertEquals(2, state.selectedRow());
        assertEquals(1, state.soundTestValue());
        assertTrue(sounds.isEmpty());
        state.update(sample(INPUT_UP, 0, false, INPUT_DOWN, 0, false), sounds::add);
        assertEquals(2, state.selectedRow(), "Up then Down cancel even at wrap boundary");
        state.update(sample(INPUT_LEFT, 0, false, INPUT_RIGHT, 0, false), sounds::add);
        assertEquals(1, state.soundTestValue());
    }

    @Test
    void choicesWrapAtRetailBoundsAndProduceAllThreeLaunchTeams() {
        var state = new Sonic2OptionsState();
        List<GameplayLaunchTeam> teams = List.of(
                new GameplayLaunchTeam(CharacterKey.SONIC, List.of(CharacterKey.TAILS)),
                new GameplayLaunchTeam(CharacterKey.SONIC, List.of()),
                new GameplayLaunchTeam(CharacterKey.TAILS, List.of()));
        for (int i = 0; i < teams.size(); i++) {
            var exit = state.update(sample(0, 0, false, 0, 0, true), ignored -> fail());
            assertEquals(NativeMenuScreen.Destination.LEVEL, exit.destination());
            assertEquals(teams.get(i), exit.team());
            assertFalse(exit.resetProgress());
            press(state, INPUT_RIGHT, 0);
        }
        assertEquals(0, state.playerOption());
        press(state, INPUT_LEFT, 0);
        assertEquals(2, state.playerOption());
        press(state, INPUT_DOWN, 0);
        press(state, INPUT_LEFT, 0);
        assertEquals(1, state.twoPlayerItems());
        press(state, INPUT_RIGHT, 0);
        assertEquals(0, state.twoPlayerItems());
        press(state, INPUT_DOWN, 0);
        press(state, INPUT_LEFT, 0);
        assertEquals(0x7F, state.soundTestValue());
        press(state, INPUT_RIGHT, 0);
        assertEquals(0, state.soundTestValue());
    }

    @Test
    void shippedAAddsTenHexAndClampsEveryRowIncludingSoundOverflow() {
        var state = new Sonic2OptionsState();
        press(state, INPUT_RIGHT, 0);
        press(state, 0, InputActionMasks.ACTION_A);
        assertEquals(0, state.playerOption());
        press(state, INPUT_DOWN | INPUT_RIGHT, 0);
        assertEquals(1, state.twoPlayerItems());
        press(state, 0, InputActionMasks.ACTION_A);
        assertEquals(0, state.twoPlayerItems());
        press(state, INPUT_DOWN, InputActionMasks.ACTION_A);
        assertEquals(0x10, state.soundTestValue());
        for (int i = 0; i < 6; i++) {
            press(state, 0, InputActionMasks.ACTION_A);
        }
        assertEquals(0x70, state.soundTestValue());
        press(state, INPUT_RIGHT, 0);
        press(state, 0, InputActionMasks.ACTION_A);
        assertEquals(0, state.soundTestValue(), "$71+$10 clamps to $00 rather than wrapping to $01");
    }

    @Test
    void bcOnlyPlaysSoundRowAndObservesChoiceBeforeStartExit() {
        var state = new Sonic2OptionsState();
        var sounds = new ArrayList<Integer>();
        state.update(sample(0, InputActionMasks.ACTION_B | InputActionMasks.ACTION_C, false, 0, 0, false), sounds::add);
        assertTrue(sounds.isEmpty());
        var exit = state.update(sample(INPUT_UP | INPUT_LEFT, InputActionMasks.ACTION_B, false,
                0, InputActionMasks.ACTION_C, true), sounds::add);
        assertEquals(List.of(0xFF), sounds, "Both B/C edges issue one native music mailbox request");
        assertEquals(NativeMenuScreen.Destination.TITLE_SCREEN, exit.destination());
        assertNull(exit.team());
        assertFalse(exit.resetProgress(), "Sound-row Start means SEGA restart without clearing progress");
    }

    @Test
    void competitionExitHasNoCampaignCoopTeamAndMenuEntryPreservesChoices() {
        var state = new Sonic2OptionsState();
        press(state, INPUT_RIGHT, 0);
        press(state, INPUT_DOWN | INPUT_RIGHT, 0);
        var exit = state.update(sample(0, 0, true, 0, 0, false), ignored -> fail());
        assertEquals(NativeMenuScreen.Destination.COMPETITION, exit.destination());
        assertNull(exit.team(), "The competition session owns both human participants");
        assertFalse(exit.resetProgress());
        press(state, INPUT_DOWN | INPUT_RIGHT, 0);
        state.enter();
        assertEquals(0, state.selectedRow());
        assertEquals(1, state.playerOption());
        assertEquals(1, state.twoPlayerItems());
        assertEquals(1, state.soundTestValue());
        var provider = new Sonic2NativeModeProvider();
        assertSame(provider.optionsScreen(), provider.optionsScreen());
        assertSame(provider.optionsState(), provider.optionsState());
        assertNull(provider.competitionScreen());
    }

    static void press(Sonic2OptionsState state, int directions, int actions) {
        state.update(sample(directions, actions, false, 0, 0, false), ignored -> {});
    }

    static LogicalInputSnapshot sample(int d1, int a1, boolean s1, int d2, int a2, boolean s2) {
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(d1, d1, a1, a1, s1, s1),
                PlayerInputState.of(d2, d2, a2, a2, s2, s2));
    }
}
