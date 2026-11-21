package com.openggf.game.sonic1.audio.smps;

import com.openggf.audio.smps.ClassicPackageStockResolver;
import com.openggf.data.Rom;
import com.openggf.data.RomHeaderName;
import com.openggf.data.RomIdentity;
import com.openggf.game.sonic1.audio.Sonic1Music;

import java.io.IOException;

/** Resolves Sonic 1 stock assets for native SMPS packages. */
public final class Sonic1PackageStockResolver extends ClassicPackageStockResolver<Sonic1SmpsLoader> {
    public Sonic1PackageStockResolver(Rom rom) {
        super(rom, 0x80000, RomIdentity.S1, RomHeaderName.S1,
                Sonic1Music.CHAOS_EMERALD.id, 2);
    }

    @Override
    protected Sonic1SmpsLoader createLoader(Rom rom) throws IOException {
        try {
            return new Sonic1SmpsLoader(rom);
        } catch (IllegalStateException unreadable) {
            throw new IOException(unreadable.getMessage(), unreadable);
        }
    }

    @Override
    protected byte[] envelope(Rom rom, Sonic1SmpsLoader loader, int index) {
        byte[][] envelopes = loader.getPsgEnvelopes();
        return index > 0 && index <= envelopes.length ? envelopes[index - 1] : null;
    }
}
