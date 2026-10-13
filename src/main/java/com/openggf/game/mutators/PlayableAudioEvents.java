package com.openggf.game.mutators;

import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.util.EngineCallerAccess;

/** Trusted movement/contact evidence, not creator events or inferred status-bit changes. */
public final class PlayableAudioEvents {
    private PlayableAudioEvents() { }
    public static void landed(AbstractPlayableSprite player) { publish(player, false); }
    public static void spindashReleased(AbstractPlayableSprite player) { publish(player, true); }
    private static void publish(AbstractPlayableSprite player, boolean release) {
        if (EngineCallerAccess.callerOutside(PlayableAudioEvents.class).getClassLoader() != PlayableAudioEvents.class.getClassLoader())
            throw new SecurityException("Native playable audio events belong to the engine");
        if (player == null) return;
        var level = player.currentLevelManagerIfAvailable();
        var runtime = level == null ? null : WorldSessionPolicyAccess.getService(level.getWorldSession(), AudioMutatorRuntime.class);
        if (runtime == null) return;
        // The runtime additionally requires this exact sprite in its armed world-owned roster.
        if (release) runtime.spindashReleased(player);
        else runtime.landed(player);
    }
}
