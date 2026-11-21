package com.openggf.mods;

import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.data.Rom;
import com.openggf.data.RomFingerprint;
import com.openggf.data.RomHeaderName;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomIdentityTable;
import com.openggf.game.sonic1.audio.smps.Sonic1SmpsLoader;
import com.openggf.game.sonic2.audio.smps.Sonic2PackageEnvelopes;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsLoader;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/** Resolves raw native voices, PSG programs and decoded DAC against the launch ROM. */
final class ClassicPackageStockResolver implements SmpsPackageReader.StockResolver {
    private final Rom rom;
    private final boolean s1;
    private SmpsLoader loader;
    private DacData dac;
    private Map<Integer, byte[]> s2Envelopes;
    private boolean unknownImage;

    ClassicPackageStockResolver(Rom rom, boolean s1) {
        this.rom = rom;
        this.s1 = s1;
    }

    @Override
    public byte[] resolve(SmpsPackageReader.Locator locator) throws IOException {
        if (loader == null) initialize();
        int index = locator.index();
        return switch (locator.table()) {
            case "local-fm" -> localVoice(locator.songId(), index);
            case "psg" -> envelope(index);
            case "dac" -> dacSample(index);
            default -> throw new IOException("Unsupported classic SMPS stock table " + locator.table());
        };
    }

    @Override public boolean unknownImage() { return unknownImage; }

    private void initialize() throws IOException {
        if (rom == null || !rom.isOpen()) throw new IOException("No loaded base-game ROM");
        int size = s1 ? 0x80000 : 0x100000;
        if (rom.getSize() != size) throw new IOException("Unexpected base-game ROM size");
        var identity = s1 ? RomIdentity.S1 : RomIdentity.S2;
        unknownImage = RomIdentityTable.verify(identity, RomFingerprint.of(rom.readBytes(0, size))).isEmpty();
        if (unknownImage && (rom.read32BitAddr(0x100) != 0x53454741
                || RomHeaderName.of(rom.readDomesticName(), rom.readInternationalName())
                        != (s1 ? RomHeaderName.S1 : RomHeaderName.S2))) {
            throw new IOException("Cartridge header does not identify " + (s1 ? "s1" : "s2"));
        }
        loader = s1 ? new Sonic1SmpsLoader(rom) : new Sonic2SmpsLoader(rom);
    }

    private byte[] localVoice(int songId, int slot) {
        // getVoice() supplies playback order. The package contract hashes native bytes.
        var song = loader.loadMusic(songId);
        if (song == null || song.getVoicePtr() == 0) return null;
        // Uncompressed Get Emerald / Credits views include bytes past their final voice.
        if ((s1 && songId == 0x93 && slot >= 2) || (!s1 && songId == 0xbd && slot >= 38)) return null;
        byte[] bank = song.getData();
        int start = song.getVoicePtr() - song.getZ80StartAddress();
        int offset = start + 25 * slot;
        if (start < 0 || offset + 25 > bank.length) return null;
        return Arrays.copyOfRange(bank, offset, offset + 25);
    }

    private byte[] envelope(int index) throws IOException {
        if (loader instanceof Sonic1SmpsLoader sonic1) return sonic1.getPsgEnvelopes()[index - 1];
        if (s2Envelopes == null) s2Envelopes = Sonic2PackageEnvelopes.extract(rom);
        return s2Envelopes.get(index);
    }

    private byte[] dacSample(int note) {
        if (dac == null) dac = loader.loadDacData();
        return SmpsPackageReader.StockResolver.decodedDacSample(dac, note);
    }
}
