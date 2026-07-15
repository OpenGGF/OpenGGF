package com.openggf.game.session;

import com.openggf.game.GameplayInputFilter;

import java.util.Objects;

/**
 * Engine-internal access to the gameplay-scoped input transform.
 *
 * <p>This bridge deliberately remains outside the annotated mod surface. Mod
 * code contributes a filter through {@code GameplayPolicyProvider}; only the
 * engine's destination-loading path may install it into a live session.</p>
 */
public final class GameplayInputFilterAccess {
    private GameplayInputFilterAccess() {
    }

    public static void install(GameplayModeContext context, GameplayInputFilter filter) {
        Objects.requireNonNull(context, "context").installGameplayInputFilter(filter);
    }

    public static GameplayInputFilter current(GameplayModeContext context) {
        return context != null
                ? context.currentGameplayInputFilter()
                : GameplayInputFilter.IDENTITY;
    }

    public static GameplayInputFilter currentSessionFilter() {
        return current(SessionManager.getCurrentGameplayMode());
    }
}
