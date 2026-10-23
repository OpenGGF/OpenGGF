package com.openggf.game.sonic2.options;

import com.openggf.game.nativemenu.NativeTitleModeProvider;

/** One provider lifetime owns the retail options RAM across title/menu/gameplay visits. */
public final class Sonic2NativeModeProvider implements NativeTitleModeProvider {
    private final Sonic2OptionsState optionsState = new Sonic2OptionsState();
    private final Sonic2OptionsScreen optionsScreen;
    private boolean optionsOpened;

    public Sonic2NativeModeProvider() { this(true); }

    /** The host's overseas Graphics_Flags selects Tails; domestic selects Miles wording. */
    public Sonic2NativeModeProvider(boolean overseas) {
        optionsScreen = new Sonic2OptionsScreen(optionsState, overseas);
    }

    @Override
    public Sonic2OptionsScreen optionsScreen() {
        optionsOpened = true;
        return optionsScreen;
    }

    @Override
    public com.openggf.game.nativemenu.NativeMenuScreen.Exit singlePlayerTitleExit() {
        if (!optionsOpened) return com.openggf.game.nativemenu.NativeMenuScreen.Exit.NONE;
        // TitleScreen's one-player branch clears emerald progress; the direct
        // OptionScreen_Select path deliberately retains it (shipped fixBugs=0).
        return new com.openggf.game.nativemenu.NativeMenuScreen.Exit(
                com.openggf.game.nativemenu.NativeMenuScreen.Destination.LEVEL,
                optionsState.launchTeam(), true);
    }

    public Sonic2OptionsState optionsState() { return optionsState; }

    // competitionScreen intentionally inherits null until a real session/results owner is connected.
}
