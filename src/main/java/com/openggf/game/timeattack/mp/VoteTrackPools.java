package com.openggf.game.timeattack.mp;

import com.openggf.game.timeattack.TimeAttackTrackCatalog;

import java.util.List;

/** Converts the curated engine catalog to engine-free protocol track keys. */
public final class VoteTrackPools {
    private VoteTrackPools() {
    }

    public static List<String> forGame(String gameId) {
        return TimeAttackTrackCatalog.tracksFor(gameId).stream()
                .map(track -> track.gameId() + ":" + track.zone() + ":" + track.act())
                .toList();
    }
}
