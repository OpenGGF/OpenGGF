package com.openggf.game.internal;

import com.openggf.game.GameModule;

/**
 * The level frame counter a fresh level load leaves. Zero for a host whose load ends before
 * its main loop's first counter increment; a host whose load also models that first
 * iteration (its increment and object pass) registers the counter that iteration left.
 */
public record LoadedLevelFrameCounter(int value) {
    public static int forHost(GameModule host) {
        LoadedLevelFrameCounter service = host == null ? null : host.getGameService(LoadedLevelFrameCounter.class);
        return service == null ? 0 : service.value();
    }
}
