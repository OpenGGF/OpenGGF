package com.openggf.game.sonic2.options;

import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.function.IntConsumer;

/** Retail option RAM, retained by the native-mode provider rather than durable configuration. */
public final class Sonic2OptionsState {
    private int selectedRow;
    private int playerOption;
    private int twoPlayerItems;
    private int soundTestValue;

    public int selectedRow() { return selectedRow; }
    public int playerOption() { return playerOption; }
    /** Zero selects all items; one selects teleport monitors only. */
    public int twoPlayerItems() { return twoPlayerItems; }
    public int soundTestValue() { return soundTestValue; }

    public GameplayLaunchTeam launchTeam() {
        return switch (playerOption) {
            case 0 -> new GameplayLaunchTeam(CharacterKey.SONIC, List.of(CharacterKey.TAILS));
            case 1 -> new GameplayLaunchTeam(CharacterKey.SONIC, List.of());
            case 2 -> new GameplayLaunchTeam(CharacterKey.TAILS, List.of());
            default -> throw new IllegalStateException("Invalid Player_option: " + playerOption);
        };
    }

    void enter() {
        selectedRow = 0;
    }

    /** OptionScreen_Controls (s2.asm:12174-12243), followed by OptionScreen_Select. */
    NativeMenuScreen.Exit update(LogicalInputSnapshot input, IntConsumer playNativeSound) {
        int directions = input.player1().pressedMask() | input.player2().pressedMask();
        int actions = input.player1().actionPressedMask() | input.player2().actionPressedMask();
        // The ROM tests each bit sequentially, including opposing directions in the same sample.
        if ((directions & AbstractPlayableSprite.INPUT_UP) != 0) {
            selectedRow = (selectedRow + 2) % 3;
        }
        if ((directions & AbstractPlayableSprite.INPUT_DOWN) != 0) {
            selectedRow = (selectedRow + 1) % 3;
        }
        int maximum = switch (selectedRow) { case 0 -> 2; case 1 -> 1; default -> 0x7F; };
        int value = switch (selectedRow) {
            case 0 -> playerOption;
            case 1 -> twoPlayerItems;
            default -> soundTestValue;
        };
        if ((directions & AbstractPlayableSprite.INPUT_LEFT) != 0) {
            value = value == 0 ? maximum : value - 1;
        }
        if ((directions & AbstractPlayableSprite.INPUT_RIGHT) != 0) {
            value = value == maximum ? 0 : value + 1;
        }
        // fixBugs=0: A adds $10 on EVERY row and resets to zero above its bound.
        // The fixed branch restricts A to sound test and masks with $7F instead.
        if ((actions & InputActionMasks.ACTION_A) != 0) {
            value += 0x10;
            if (value > maximum) {
                value = 0;
            }
        }
        switch (selectedRow) {
            case 0 -> playerOption = value;
            case 1 -> twoPlayerItems = value;
            default -> soundTestValue = value;
        }
        if (selectedRow == 2 && (actions & (InputActionMasks.ACTION_B | InputActionMasks.ACTION_C)) != 0) {
            playNativeSound.accept(soundTestValue + 0x80);
        }
        if (!input.player1().startPressed() && !input.player2().startPressed()) {
            return NativeMenuScreen.Exit.NONE;
        }
        // OptionScreen_Select (s2.asm:12106-12167). Shipped fixBugs=0 retains emeralds.
        return switch (selectedRow) {
            case 0 -> new NativeMenuScreen.Exit(NativeMenuScreen.Destination.LEVEL, launchTeam(), false);
            // Competition owns its human participants; a campaign team cannot express that session.
            case 1 -> new NativeMenuScreen.Exit(NativeMenuScreen.Destination.COMPETITION, null, false);
            default -> new NativeMenuScreen.Exit(NativeMenuScreen.Destination.TITLE_SCREEN, null, false);
        };
    }
}
