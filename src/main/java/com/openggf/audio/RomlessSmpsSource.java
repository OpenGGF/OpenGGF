package com.openggf.audio;

import com.openggf.audio.smps.SmpsLoader;

/**
 * Optional capability of a base audio profile whose SMPS programs do not come from a
 * cartridge ROM (Sonic CD's FM driver bank is read from the disc). The audio source is
 * installed from it when the game's data source has no ROM.
 */
public interface RomlessSmpsSource {
    /** Creates the loader over the profile's own media, or returns null while none is loaded. */
    SmpsLoader createRomlessSmpsLoader();
}
