package com.openggf.game.soniccd.audio;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLoader;

import java.util.Map;
import java.util.Objects;

/**
 * SMPS loader over the Sonic CD FM sound-effect bank.
 *
 * <p>The bank is the byte range MDINIT copies into Z80 RAM at boot (Mega Drive Init/Main.asm
 * {@code Start}:46-59, {@code FMSFXDriver}:133-143), held by {@link CdFmSoundAssets}. The driver
 * has a song index but no songs ({@code SongIndex}, FM/Data.asm:11) and no DAC: all music is CD
 * audio or PCM on the sub CPU. The empty DAC table exists only because the shared catalogue
 * path admits SMPS programs with a sample table.
 */
public final class CdFmSmpsLoader implements SmpsLoader {
    /** {@code FM_START} and the last index entry {@code FM_DF} (FM/_Sound IDs.inc:9-89). */
    public static final int FIRST_SFX = 0x90;
    public static final int LAST_SFX = 0xDF;

    private static final DacData NO_DAC = new DacData(Map.of(), Map.of());

    private final CdFmSoundAssets assets;
    private final byte[] bank;

    public CdFmSmpsLoader(CdFmSoundAssets assets) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.bank = assets.bank().copy();
    }

    @Override
    public AbstractSmpsData loadMusic(int musicId) {
        return null;
    }

    /**
     * {@code PlaySFX} reads {@code SFXIndex[id - FM_START]} (FM/_Driver.asm:803-810). IDs outside
     * the index have no program; the driver's own range check is {@code PlaySoundID}
     * (FM/_Driver.asm:774-783).
     */
    @Override
    public AbstractSmpsData loadSfx(int sfxId) {
        if (sfxId < FIRST_SFX || sfxId > LAST_SFX) {
            return null;
        }
        return new CdFmSfxData(bank, sfxId, assets.sfxHeaderAddress(sfxId));
    }

    @Override
    public AbstractSmpsData loadSfx(String sfxName) {
        return null;
    }

    @Override
    public DacData loadDacData() {
        return NO_DAC;
    }
}
