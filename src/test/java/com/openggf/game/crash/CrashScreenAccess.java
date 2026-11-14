package com.openggf.game.crash;

/** Test access to the crash page's choices without driving window input. */
public final class CrashScreenAccess {
    private CrashScreenAccess() { }

    public static void returnToGames(CrashScreen screen) { screen.run(CrashScreen.Choice.RETURN); }
}
