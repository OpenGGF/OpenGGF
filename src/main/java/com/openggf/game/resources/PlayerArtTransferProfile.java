package com.openggf.game.resources;

/** Production module capability for a player bank converted into contiguous RAM before DMA. */
public interface PlayerArtTransferProfile {
    /** Returns the converted bank for this owner, or null for the ordinary ROM-source path. */
    ConvertedBank convertedPlayerArtBank(String owner);

    /** Immutable ROM routine parameters; original DPLC requests still select renderer tiles. */
    record ConvertedBank(int ramAddress, int vramDestination) {
        public ConvertedBank {
            if (ramAddress < 0 || ramAddress > 0xFFFFFF
                    || vramDestination < 0 || vramDestination > 0xFFFF) {
                throw new IllegalArgumentException("invalid converted player art bank");
            }
        }
    }
}
