package com.openggf.game.resources;

/** One owner's player-art bank in the loaded ROM revision; negative RAM addresses mean absent. */
public record PlayerArtBank(
        int romArtBase,
        int vramDestination,
        int stagingRamAddress,
        int stagingByteLength,
        int convertedRamAddress) {
}
