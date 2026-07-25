package com.openggf.audio.presentation;

import java.util.Objects;

public record SmpsAssetKey(String gameId, Route route, int sfxId, String sfxName) {
    public enum Route {
        BASE_ID,
        BASE_NAME,
        DONOR_ID,
        FALLBACK_NAME
    }

    public SmpsAssetKey {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(route, "route");
    }
}
