package com.openggf.level.objects.authored;

import com.openggf.game.ModApi;

/** Level-side owner-bound routing to scenes registered through the public Stageplay playback API. */
@ModApi
@FunctionalInterface
public interface AuthoredStageplayService {
    /** Returns false while the requested player is busy; unsupported playback modes fail explicitly. */
    boolean play(String resource, boolean blocking);
}
