package com.openggf;

import com.openggf.game.GameMode;
import com.openggf.game.LevelInputOverlay;
import com.openggf.graphics.FadeManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A chosen restart or hub return must not let native play run behind its own fade-out. */
class TestGameLoopConfigurationCommands {
    private final FadeManager fade = new FadeManager();
    private final List<String> runs = new ArrayList<>();

    @Test
    void restartAndHubHoldPlayOnlyWhileTheirLevelFadeRuns() {
        for (var command : List.of(LevelInputOverlay.Command.FULL_RESTART, LevelInputOverlay.Command.RETURN_TO_HUB)) {
            var commands = new GameLoopConfigurationCommands();
            assertTrue(commands.handle(overlay(command), () -> fade, () -> runs.add("admit"),
                    () -> runs.add("hub"), () -> runs.add("restart")));
            assertTrue(commands.holdsTransition(GameMode.LEVEL, () -> true), command + " holds behind its fade");
            assertFalse(commands.holdsTransition(GameMode.LEVEL, () -> false),
                    "a finished or cancelled fade releases play");
            assertFalse(commands.holdsTransition(GameMode.LEVEL, () -> true), "release is not re-armed");
        }
        assertEquals(List.of("admit", "restart", "admit", "hub"), runs);
    }

    @Test
    void loadOwnerAndModeChangesReleaseWhileResumeNeverHolds() {
        var commands = new GameLoopConfigurationCommands();
        commands.handle(overlay(LevelInputOverlay.Command.FULL_RESTART), () -> fade, () -> { }, () -> { }, () -> { });
        commands.releaseTransition();
        assertFalse(commands.holdsTransition(GameMode.LEVEL, () -> true), "restart load releases before fade-in");

        commands.handle(overlay(LevelInputOverlay.Command.RETURN_TO_HUB), () -> fade, () -> { }, () -> { }, () -> { });
        assertFalse(commands.holdsTransition(GameMode.MASTER_TITLE_SCREEN, () -> true), "never holds outside a level");
        assertFalse(commands.holdsTransition(GameMode.LEVEL, () -> true));

        commands.handle(overlay(LevelInputOverlay.Command.FULL_RESTART), () -> fade, () -> { }, () -> { }, () -> { });
        commands.clear();
        assertFalse(commands.holdsTransition(GameMode.LEVEL, () -> true), "gameplay context changes clear the hold");

        commands.handle(overlay(LevelInputOverlay.Command.RESUME), () -> fade, () -> { }, () -> { }, () -> { });
        assertFalse(commands.holdsTransition(GameMode.LEVEL, () -> true), "Resume returns play immediately");
    }

    private static LevelInputOverlay overlay(LevelInputOverlay.Command command) {
        return new LevelInputOverlay() {
            private LevelInputOverlay.Command next = command;
            @Override public boolean handleInput(com.openggf.control.InputHandler input) { return false; }
            @Override public LevelInputOverlay.Command consumeCommand() {
                var current = next;
                next = LevelInputOverlay.Command.NONE;
                return current;
            }
        };
    }
}
