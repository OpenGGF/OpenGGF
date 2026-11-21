package com.openggf.game.sonic3k.audio.smps;

import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.PackageStockResolver;
import com.openggf.data.Rom;
import com.openggf.data.RomFingerprint;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomIdentityTable;
import com.openggf.game.sonic3k.audio.Sonic3kAudioResourceProfile;

import java.io.IOException;
import java.util.Arrays;

/** Launch-scoped lookup against the base game's pinned cartridge view. */
public final class Sonic3kPackageStockResolver implements PackageStockResolver {
    private final Rom rom;
    private Sonic3kAudioResourceProfile profile;
    private Sonic3kSmpsLoader loader;
    private DacData dac;
    private boolean unknownImage;

    public Sonic3kPackageStockResolver(Rom rom) {
        this.rom = rom;
    }

    @Override
    public byte[] resolve(Locator locator) throws IOException {
        if (rom == null || !rom.isOpen()) throw new IOException("No loaded base-game ROM");
        if (loader == null) {
            try {
                profile = Sonic3kAudioResourceProfile.fromRom(rom);
            } catch (IllegalArgumentException unsupported) {
                throw new IOException(unsupported.getMessage(), unsupported);
            }
            RomIdentity identity = switch (profile) {
                case LOCKED_ON -> RomIdentity.S3K;
                case SONIC_3 -> RomIdentity.S3;
                case SONIC_KNUCKLES -> RomIdentity.SK;
            };
            byte[] image = rom.readBytes(0, Math.toIntExact(rom.getSize()));
            unknownImage = RomIdentityTable.verify(identity, RomFingerprint.of(image)).isEmpty();
            if (unknownImage && profile == Sonic3kAudioResourceProfile.LOCKED_ON) {
                unknownImage = RomIdentityTable.verify(RomIdentity.SK, RomFingerprint.of(image, 0, 0x200000)).isEmpty()
                        || RomIdentityTable.verify(RomIdentity.S3, RomFingerprint.of(image, 0x200000, 0x200000)).isEmpty();
            }
            try {
                loader = new Sonic3kSmpsLoader(rom, profile);
            } catch (IllegalStateException unreadable) {
                throw new IOException(unreadable.getMessage(), unreadable);
            }
        }
        int index = locator.index();
        return switch (locator.table()) {
            case "global-fm" -> sliceVoice(loader.getGlobalVoiceData(), index * 25);
            case "local-fm" -> localVoice(locator.songId(), index);
            case "psg" -> loader.getPsgEnvelopes().get(index);
            case "mod" -> loader.getPlaybackModEnvelopes().get(index);
            case "dac" -> dacSample(index);
            default -> throw new IOException("Unsupported S3K stock table " + locator.table());
        };
    }

    @Override public boolean unknownImage() { return unknownImage; }

    private byte[] localVoice(int songId, int slot) {
        if (profile == Sonic3kAudioResourceProfile.SONIC_3) {
            songId = profile.musicId(songId & 255);
        } else if (profile == Sonic3kAudioResourceProfile.SONIC_KNUCKLES && songId >= 0x100) {
            return null;
        }
        if (!(loader.loadMusic(songId) instanceof Sonic3kSmpsData data)) return null;
        int pointer = data.getVoicePtr();
        if (pointer == 0) return null;
        return sliceVoice(data.getBankData(), pointer - data.getBankZ80Base() + slot * 25);
    }

    private static byte[] sliceVoice(byte[] bytes, int start) {
        if (bytes == null || start < 0 || start + 25 > bytes.length) return null;
        return Arrays.copyOfRange(bytes, start, start + 25);
    }

    private byte[] dacSample(int note) throws IOException {
        if (dac == null) {
            try {
                dac = loader.loadDacData();
            } catch (IllegalStateException unreadable) {
                throw new IOException(unreadable.getMessage(), unreadable);
            }
        }
        return PackageStockResolver.decodedDacSample(dac, note);
    }
}
