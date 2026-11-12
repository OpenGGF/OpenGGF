package com.openggf.game.sonic2.options;

import com.openggf.game.nativemenu.NativeTitleModeProvider;

/** One provider lifetime owns the retail options RAM across title/menu/gameplay visits. */
public final class Sonic2NativeModeProvider implements NativeTitleModeProvider {
    private final Sonic2OptionsState optionsState = new Sonic2OptionsState();
    private final Sonic2OptionsScreen optionsScreen;
    private boolean optionsOpened;
    private boolean competitionFromOptions;
    private com.openggf.game.GameStateManager entryProgress;
    private final boolean overseas;
    private final java.util.function.IntPredicate qualified;
    // LevelSelect2P_LevelOrder (s2.asm:11848-11887): EHZ, MCZ and CNZ by ROM zone id. Qualified
    // against cold native BizHawk 2P references; see docs/architecture/plans/2026-11-07-s2-competition-parity.md.
    private static final java.util.Set<Integer> QUALIFIED_ZONES = java.util.Set.of(0, 11, 12);
    private final com.openggf.game.nativemenu.NativeMenuScreen optionsVisit;

    public Sonic2NativeModeProvider() { this(true); }

    /** The host's overseas Graphics_Flags selects Tails; domestic selects Miles wording. */
    public Sonic2NativeModeProvider(boolean overseas) {
        this(overseas, QUALIFIED_ZONES::contains);
    }

    /** Package-private route qualification override, never a production activation setting. */
    Sonic2NativeModeProvider(boolean overseas, java.util.function.IntPredicate qualified) {
        this.overseas = overseas;
        this.qualified = java.util.Objects.requireNonNull(qualified);
        optionsScreen = new Sonic2OptionsScreen(optionsState, overseas);
        optionsVisit = new com.openggf.game.nativemenu.NativeMenuScreen() {
            public void initialize() { competitionFromOptions = false; optionsScreen.initialize(); }
            public void update(com.openggf.control.InputHandler input) { optionsScreen.update(input); }
            public void draw() { optionsScreen.draw(); }
            public void setClearColor() { optionsScreen.setClearColor(); }
            public void reset() { optionsScreen.reset(); }
            public Exit consumeExit() {
                var exit = optionsScreen.consumeExit();
                if (exit.destination() != Destination.NONE)
                    competitionFromOptions = exit.destination() == Destination.COMPETITION;
                return exit;
            }
        };
    }

    @Override
    public com.openggf.game.nativemenu.NativeMenuScreen optionsScreen() {
        optionsOpened = true;
        return optionsVisit;
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

    /** Graphics_Flags bit 7, shared by native menu wording and result name selection. */
    public boolean overseasGraphics() { return overseas; }

    public Sonic2OptionsState optionsState() { return optionsState; }

    /** True when at least one 2P level-select zone is qualified; the title then offers 2 PLAYER. */
    public boolean competitionAvailable() {
        return qualified.test(0) || qualified.test(11) || qualified.test(12);
    }

    @Override public com.openggf.game.nativemenu.NativeMenuScreen competitionScreen(
            com.openggf.game.nativemenu.NativeMenuHostContext context) {
        java.util.Objects.requireNonNull(context);
        if (!competitionAvailable()) return null;
        var liveProgress = com.openggf.game.GameServices.gameStateOrNull();
        if (liveProgress != null) entryProgress = liveProgress;
        java.util.Objects.requireNonNull(entryProgress, "native persistent entry progress owner");
        boolean fromOptions = competitionFromOptions;
        competitionFromOptions = false;
        try {
            return new com.openggf.game.sonic2.competition.Sonic2CompetitionVisit(context, optionsState,
                    overseas, fromOptions, qualified,
                    new com.openggf.game.sonic2.objects.Sonic2TeleportObjectFieldsAccess(), entryProgress);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException("Cannot construct native S2 competition visit", failure);
        }
    }
}
