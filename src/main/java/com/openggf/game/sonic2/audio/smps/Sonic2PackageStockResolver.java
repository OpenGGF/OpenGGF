package com.openggf.game.sonic2.audio.smps;

import com.openggf.audio.smps.ClassicPackageStockResolver;
import com.openggf.data.Rom;
import com.openggf.data.RomHeaderName;
import com.openggf.data.RomIdentity;
import com.openggf.game.sonic2.audio.Sonic2Music;

import java.io.IOException;
import java.util.Map;

/** Resolves Sonic 2 stock assets for native SMPS packages. */
public final class Sonic2PackageStockResolver extends ClassicPackageStockResolver<Sonic2SmpsLoader> {
    private Map<Integer, byte[]> envelopes;

    public Sonic2PackageStockResolver(Rom rom) {
        super(rom, 0x100000, RomIdentity.S2, RomHeaderName.S2,
                Sonic2Music.CREDITS.id, 38);
    }

    @Override
    protected Sonic2SmpsLoader createLoader(Rom rom) {
        return new Sonic2SmpsLoader(rom);
    }

    @Override
    protected byte[] envelope(Rom rom, Sonic2SmpsLoader loader, int index) throws IOException {
        if (envelopes == null) envelopes = Sonic2PackageEnvelopes.extract(rom);
        return envelopes.get(index);
    }
}
