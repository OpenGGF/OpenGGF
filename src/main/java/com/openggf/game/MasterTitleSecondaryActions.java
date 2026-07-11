package com.openggf.game;

import com.openggf.control.LogicalInputSnapshot;

import java.util.Objects;

/**
 * Input state for the master title's secondary action row.
 *
 * <p>The row deliberately does not consume horizontal navigation. The master
 * title therefore retains its stock game-selection behavior while this focus
 * marker is active.
 */
public final class MasterTitleSecondaryActions {

    public enum Result {
        NOT_CONSUMED,
        CONSUMED,
        OPEN_MODS
    }

    private boolean modsFocused;
    private boolean downPressed;
    private boolean upPressed;
    private boolean acceptPressed;
    private boolean backPressed;
    private boolean shortcutPressed;

    public Result update(LogicalInputSnapshot input, boolean modsShortcutPressed) {
        Objects.requireNonNull(input, "input");

        boolean downEdge = input.menuDown() && !downPressed;
        boolean upEdge = input.menuUp() && !upPressed;
        boolean acceptEdge = input.menuAccept() && !acceptPressed;
        boolean backEdge = input.menuBack() && !backPressed;
        boolean shortcutEdge = modsShortcutPressed && !shortcutPressed;

        downPressed = input.menuDown();
        upPressed = input.menuUp();
        acceptPressed = input.menuAccept();
        backPressed = input.menuBack();
        shortcutPressed = modsShortcutPressed;

        if (shortcutEdge) {
            return Result.OPEN_MODS;
        }

        if (!modsFocused) {
            if (downEdge) {
                modsFocused = true;
                return Result.CONSUMED;
            }
            return Result.NOT_CONSUMED;
        }

        if (upEdge || backEdge) {
            modsFocused = false;
            return Result.CONSUMED;
        }
        if (acceptEdge) {
            return Result.OPEN_MODS;
        }

        // While focused, held accept/back must not fall through to game confirm.
        if (input.menuAccept() || input.menuBack()) {
            return Result.CONSUMED;
        }
        return Result.NOT_CONSUMED;
    }

    public boolean isModsFocused() {
        return modsFocused;
    }
}
