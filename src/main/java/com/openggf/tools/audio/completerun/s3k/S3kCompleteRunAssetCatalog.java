package com.openggf.tools.audio.completerun.s3k;

import com.openggf.tools.audio.completerun.s3k.S3kCompleteRunStateNormalizer.Asset;
import com.openggf.tools.audio.completerun.s3k.S3kCompleteRunStateNormalizer.RomPointer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Source-exact address catalog for the shipped locked-on S3K sound driver. */
public final class S3kCompleteRunAssetCatalog {
    public static final String ROM_SHA1 = "cfbf98c36c776677290a872547ac47c53d2761d6";
    private static final long ROM_BYTES = 4L * 1024 * 1024;
    private static final int Z80_ROM_WINDOW = 0x8000;

    private final Map<String, Asset> assets;
    private final Map<Integer, Bank> musicBanks;
    private final Bank sfxBank;

    private S3kCompleteRunAssetCatalog() {
        // sonic3k.lst, built from sonic3k.asm with fix_sndbugs=0:
        // Snd_Bank1_Start=$E4104, Snd_Bank2_Start=$E8000,
        // lock-on S3 music=$2C8000..$2DFFFF, and SndBank=$F8000.
        LinkedHashMap<String, Asset> values = new LinkedHashMap<>();
        add(values, "music.bank.1c", 0x0e4104, 0x0e8000);
        add(values, "music.bank.1d", 0x0e8000, 0x0f0000);
        add(values, "music.bank.59", 0x2c8000, 0x2d0000);
        add(values, "music.bank.5a", 0x2d0000, 0x2d8000);
        add(values, "music.bank.5b", 0x2d8000, 0x2e0000);
        add(values, "sfx.bank.1f", 0x0f8000, 0x100000);
        // The first compressed driver image is installed at Z80 $0000..$12FF.
        // This logical address space is needed for zTrackInitPos normalization.
        add(values, "z80.driver", 0x0000, 0x1300);
        // Z80_SoundDriverData is installed at $1300 and the source guard at
        // Z80 Sound Driver.asm:5305-5307 requires its tables to end by $1C00.
        add(values, "z80.driver-data", 0x1300, 0x1c00);
        assets = Map.copyOf(values);
        musicBanks = Map.of(
                0x1c, new Bank(0x0e0000, values.get("music.bank.1c")),
                0x1d, new Bank(0x0e8000, values.get("music.bank.1d")),
                0x59, new Bank(0x2c8000, values.get("music.bank.59")),
                0x5a, new Bank(0x2d0000, values.get("music.bank.5a")),
                0x5b, new Bank(0x2d8000, values.get("music.bank.5b")));
        sfxBank = new Bank(0x0f8000, values.get("sfx.bank.1f"));
    }

    public static S3kCompleteRunAssetCatalog load(Path rom) throws IOException {
        Objects.requireNonNull(rom, "locked-on ROM path");
        if (Files.size(rom) != ROM_BYTES || !ROM_SHA1.equals(sha1(rom))) {
            throw new IllegalArgumentException("S3K audio catalog requires the pinned locked-on ROM");
        }
        return new S3kCompleteRunAssetCatalog();
    }

    public Map<String, Asset> assets() {
        return assets;
    }

    RomPointer musicPointer(int bankByte, int z80Pointer) {
        Bank bank = musicBanks.get(bankByte);
        if (bank == null) {
            throw new IllegalArgumentException("unknown shipped S3K music bank byte: " + bankByte);
        }
        return bank.resolve(z80Pointer);
    }

    RomPointer sfxPointer(int z80Pointer) {
        return sfxBank.resolve(z80Pointer);
    }

    RomPointer musicOrDriverDataPointer(int bankByte, int z80Pointer) {
        return isDriverData(z80Pointer) ? driverDataPointer(z80Pointer)
                : musicPointer(bankByte, z80Pointer);
    }

    RomPointer sfxOrDriverDataPointer(int z80Pointer) {
        return isDriverData(z80Pointer) ? driverDataPointer(z80Pointer) : sfxPointer(z80Pointer);
    }

    RomPointer driverPointer(int z80Pointer) {
        Asset driver = assets.get("z80.driver");
        if (z80Pointer <= 0 || z80Pointer >= driver.romEndExclusive()) {
            throw new IllegalArgumentException("S3K Z80 driver pointer is outside $0001..$12FF");
        }
        return new RomPointer(driver.key(), z80Pointer);
    }

    private RomPointer driverDataPointer(int z80Pointer) {
        Asset data = assets.get("z80.driver-data");
        if (z80Pointer < data.romBase() || z80Pointer >= data.romEndExclusive()) {
            throw new IllegalArgumentException("S3K Z80 driver-data pointer is outside $1300..$1BFF");
        }
        return new RomPointer(data.key(), z80Pointer);
    }

    private boolean isDriverData(int pointer) {
        Asset data = assets.get("z80.driver-data");
        return pointer >= data.romBase() && pointer < data.romEndExclusive();
    }

    private static void add(Map<String, Asset> target, String key, long start, long end) {
        target.put(key, new Asset(key, start, end));
    }

    private static String sha1(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("JVM is missing SHA-1", error);
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            for (int count; (count = input.read(buffer)) >= 0; ) {
                if (count > 0) digest.update(buffer, 0, count);
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private record Bank(long windowBase, Asset asset) {
        private RomPointer resolve(int pointer) {
            if (pointer < Z80_ROM_WINDOW || pointer > 0xffff) {
                throw new IllegalArgumentException("S3K banked pointer is outside the Z80 ROM window");
            }
            long absolute = windowBase + pointer - Z80_ROM_WINDOW;
            if (absolute < asset.romBase() || absolute >= asset.romEndExclusive()) {
                throw new IllegalArgumentException("S3K pointer is outside its source-exact sound asset range");
            }
            return new RomPointer(asset.key(), absolute);
        }
    }
}
