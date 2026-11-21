package com.openggf.audio.smps;

import com.openggf.data.Rom;
import com.openggf.data.RomFingerprint;
import com.openggf.data.RomHeaderName;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomIdentityTable;

import java.io.IOException;
import java.util.Arrays;

/** Resolves raw native voices, PSG programs and decoded DAC against the launch ROM. */
public abstract class ClassicPackageStockResolver<L extends SmpsLoader> implements PackageStockResolver {
    private final Rom rom;
    private final int size;
    private final RomIdentity identity;
    private final RomHeaderName headerName;
    private final int limitedSong;
    private final int limitedSongVoiceCount;
    private L loader;
    private DacData dac;
    private boolean unknownImage;

    protected ClassicPackageStockResolver(Rom rom, int size, RomIdentity identity, RomHeaderName headerName,
                                          int limitedSong, int limitedSongVoiceCount) {
        this.rom = rom;
        this.size = size;
        this.identity = identity;
        this.headerName = headerName;
        this.limitedSong = limitedSong;
        this.limitedSongVoiceCount = limitedSongVoiceCount;
    }

    protected abstract L createLoader(Rom rom) throws IOException;

    protected abstract byte[] envelope(Rom rom, L loader, int index) throws IOException;

    @Override
    public final byte[] resolve(Locator locator) throws IOException {
        if (loader == null) initialize();
        int index = locator.index();
        return switch (locator.table()) {
            case "local-fm" -> localVoice(locator.songId(), index);
            case "psg" -> envelope(rom, loader, index);
            case "dac" -> dacSample(index);
            default -> throw new IOException("Unsupported classic SMPS stock table " + locator.table());
        };
    }

    @Override public boolean unknownImage() { return unknownImage; }

    private void initialize() throws IOException {
        if (rom == null || !rom.isOpen()) throw new IOException("No loaded base-game ROM");
        if (rom.getSize() != size) throw new IOException("Unexpected base-game ROM size");
        unknownImage = RomIdentityTable.verify(identity, RomFingerprint.of(rom.readBytes(0, size))).isEmpty();
        if (unknownImage && (rom.read32BitAddr(0x100) != 0x53454741
                || RomHeaderName.of(rom.readDomesticName(), rom.readInternationalName()) != headerName)) {
            throw new IOException("Cartridge header does not identify " + identity);
        }
        loader = createLoader(rom);
    }

    private byte[] localVoice(int songId, int slot) {
        // getVoice() supplies playback order. The package contract hashes native bytes.
        var song = loader.loadMusic(songId);
        if (song == null || song.getVoicePtr() == 0) return null;
        // Uncompressed Get Emerald / Credits views include bytes past their final voice.
        if (songId == limitedSong && slot >= limitedSongVoiceCount) return null;
        byte[] bank = song.getData();
        int start = song.getVoicePtr() - song.getZ80StartAddress();
        int offset = start + 25 * slot;
        if (start < 0 || offset + 25 > bank.length) return null;
        return Arrays.copyOfRange(bank, offset, offset + 25);
    }

    private byte[] dacSample(int note) {
        if (dac == null) dac = loader.loadDacData();
        return PackageStockResolver.decodedDacSample(dac, note);
    }
}
