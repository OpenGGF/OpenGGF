package com.openggf.game.sonic2.options;

import com.openggf.game.nativemenu.NativeTitleModeProvider;

/** One provider lifetime owns the retail options RAM across title/menu/gameplay visits. */
public final class Sonic2NativeModeProvider implements NativeTitleModeProvider {
    private final Sonic2OptionsState optionsState = new Sonic2OptionsState();
    private final Sonic2OptionsScreen optionsScreen;

    public Sonic2NativeModeProvider() { this(true); }

    /** The host's overseas Graphics_Flags selects Tails; domestic selects Miles wording. */
    public Sonic2NativeModeProvider(boolean overseas) {
        optionsScreen = new Sonic2OptionsScreen(optionsState, overseas);
    }

    @Override
    public Sonic2OptionsScreen optionsScreen() { return optionsScreen; }

    public Sonic2OptionsState optionsState() { return optionsState; }

    // competitionScreen intentionally inherits null until a real session/results owner is connected.
}
