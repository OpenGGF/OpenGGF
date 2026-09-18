package com.openggf.data;

import com.openggf.configuration.SonicConfiguration;

/** The three stock games and the configuration key / logical ROM each one boots from. */
public enum RomGame {
    S1(SonicConfiguration.SONIC_1_ROM, RomIdentity.S1),
    S2(SonicConfiguration.SONIC_2_ROM, RomIdentity.S2),
    S3K(SonicConfiguration.SONIC_3K_ROM, RomIdentity.S3K);

    private final SonicConfiguration configurationKey;
    private final RomIdentity identity;

    RomGame(SonicConfiguration configurationKey, RomIdentity identity) {
        this.configurationKey = configurationKey;
        this.identity = identity;
    }

    /** The per-game key whose value is an explicit image override. */
    public SonicConfiguration configurationKey() {
        return configurationKey;
    }

    /** The logical ROM the game boots from. */
    public RomIdentity identity() {
        return identity;
    }
}
