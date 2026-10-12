package com.openggf.game.session;

/** Engine-only access to gameplay-scoped scheduled controller publication. */
public final class ScheduledPlaybackInputOps {
    private ScheduledPlaybackInputOps() { }

    public static void attach(GameplayModeContext context, Runnable publisher) {
        context.setScheduledPlaybackInputPublisher(publisher);
    }

    public static void detach(GameplayModeContext context, Runnable publisher) {
        context.clearScheduledPlaybackInputPublisher(publisher);
    }

    public static void publish(GameplayModeContext context) {
        context.publishScheduledPlaybackInput();
    }
}
