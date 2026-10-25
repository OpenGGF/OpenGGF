package com.openggf.mods.scene.host;

import com.openggf.mods.scene.SceneLaunch;

/**
 * Engine side of a startup scene's navigation into gameplay and other sessions. Each
 * {@code prepare} method validates the request and returns the transition to run when the
 * scene exits, or null to refuse it. Engine-internal.
 */
public interface SceneNavigator {
    /** The fade-out-and-start transition for a stock act, or null when it cannot start. */
    Runnable prepareStockAct(int zone, int act);

    /** The running stock session, or null outside one. */
    SceneLaunch currentLaunch();

    /** The fade-out-and-relaunch transition, or null when the game is unavailable. */
    Runnable prepareRelaunch(SceneLaunch launch);
}
