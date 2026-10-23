package com.openggf.mods.run;

import com.openggf.game.ModApi;
import com.openggf.net.identity.PlayerAppearance;

/** Commands a host may issue to its running run. They take effect at the next safe boundary. */
@ModApi
public interface RunHandle {
    /** Reloads the run's starting act from its beginning, clearing star-post progress. */
    void retry();

    /** Ends the run and returns to the host. */
    void leave();

    /**
     * Spectator camera after the act is complete: while {@code active}, the camera stops following
     * the player and pans by {@code dx}/{@code dy} pixels within the level bounds; clearing it
     * restores normal following. Ignored until the act's completion signal has been raised, so it
     * can never move the camera during play.
     */
    void spectate(boolean active, int dx, int dy);

    /**
     * Draws the run's own character in {@code appearance} from the next frame on: the
     * character's palette line with the look's entries applied (each only while the live colour
     * is still the stock one, so Super/Hyper cycles still show). Only that sprite changes; rings,
     * the HUD and objects keep their colours. {@link PlayerAppearance#STOCK} restores the stock
     * palette. Presentation only: the simulation is unchanged. The engine's handle implements
     * this; the default ignores the request.
     */
    default void setPlayerAppearance(PlayerAppearance appearance) { }

    /**
     * While {@code held}, the run's characters see a released controller (no buttons, no Start)
     * but the level keeps running, unlike holding steps in {@link RunHost#admitStep}: a menu
     * drawn over the run owns the controller meanwhile. Releasing hands the controller back from
     * the next step. The engine's handle implements this; the default ignores the request.
     */
    default void holdControls(boolean held) { }

    /**
     * While {@code covered}, a menu your scene draws over the run ({@code SceneGameplay.launchBehind})
     * covers the level: the engine stops drawing the stock HUD and ghost nameplates, which would
     * otherwise read through a translucent menu. The level, its sprites and the ghosts keep
     * drawing. Presentation only. The engine's handle implements this; the default ignores it.
     */
    default void setLevelCovered(boolean covered) { }

    /**
     * Shows your run's own clock in the stock HUD's TIME, in frames (60 per second), instead of
     * the act's timer; a negative value shows the act's timer again. Set it after each step, for
     * example from {@code RunHost.afterStep}, and the HUD shows it that frame. Presentation only:
     * the act's timer, its time-over and its warning flash are unchanged. The engine's handle
     * implements this; the default ignores it.
     */
    default void setHudTime(int frames) { }

    /**
     * While {@code shown}, the stock HUD's TIME adds hundredths of a second (0:00.00), as Sonic
     * CD's does, in the game's own HUD digits. Players can also turn this on for every game in the
     * display settings. Presentation only. The engine's handle implements this; the default
     * ignores it.
     */
    default void setHudTimeHundredths(boolean shown) { }

    /** True until the run has ended. */
    boolean isActive();
}
