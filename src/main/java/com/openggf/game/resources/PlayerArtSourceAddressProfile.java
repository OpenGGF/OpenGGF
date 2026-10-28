package com.openggf.game.resources;

import com.openggf.game.GameId;

/** Native source-bank addresses for production player-art submission diagnostics. */
@FunctionalInterface
public interface PlayerArtSourceAddressProfile {
    int romAddress(GameId gameId, String owner, int canonicalAddress);
}
