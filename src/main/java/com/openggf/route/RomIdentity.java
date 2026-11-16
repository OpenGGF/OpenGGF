package com.openggf.route;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * The ROM a route runs on, by hash. A manifest never names a path: a local resolver supplies
 * the file and must match this identity before boot.
 */
public record RomIdentity(String game, long crc32, String sha1) {
    public RomIdentity {
        Objects.requireNonNull(game, "game");
        sha1 = Objects.requireNonNull(sha1, "sha1").toUpperCase(Locale.ROOT);
        if (!sha1.matches("[0-9A-F]{40}")) throw new IllegalArgumentException("not a SHA-1: " + sha1);
        if (crc32 < 0 || crc32 > 0xFFFFFFFFL) throw new IllegalArgumentException("not a CRC32: " + crc32);
    }

    /** The supported cartridge dumps, or the configured verified hosted source. */
    public static RomIdentity standard(String game) {
        var id = com.openggf.game.GameId.fromCode(game);
        var host = com.openggf.game.GameModuleRegistry.hostedMediaGames().contains(id)
                ? com.openggf.game.GameModuleRegistry.hostedMedia(id) : null;
        if (host != null) {
            try { return ofFile(game, host.configuredPath(com.openggf.game.GameServices.configuration())); }
            catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }
        return switch (game) {
            case "s1" -> new RomIdentity("s1", 0xAFE05EEEL, "69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B");
            case "s2" -> new RomIdentity("s2", 0x7B905383L, "8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9");
            case "s3k" -> new RomIdentity("s3k", 0x63522553L, "CFBF98C36C776677290A872547AC47C53D2761D6");
            default -> throw new IllegalArgumentException("no standard ROM for game " + game);
        };
    }

    /** Hashes cartridge bytes or the hosted pipeline's verified complete-content identity. */
    public static RomIdentity ofFile(String game, Path rom) throws IOException {
        var id = com.openggf.game.GameId.fromCode(game);
        var host = com.openggf.game.GameModuleRegistry.hostedMediaGames().contains(id)
                ? com.openggf.game.GameModuleRegistry.hostedMedia(id) : null;
        if (host != null) {
            var source = host.openMedia(rom);
            try { return ofContentIdentity(game, source.identity()); }
            finally { if (source instanceof java.io.Closeable closeable) closeable.close(); }
        }
        var crc = new CRC32();
        MessageDigest sha1;
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        try (InputStream in = Files.newInputStream(rom)) {
            byte[] buffer = new byte[1 << 16];
            int n;
            while ((n = in.read(buffer)) > 0) {
                crc.update(buffer, 0, n);
                sha1.update(buffer, 0, n);
            }
        }
        return new RomIdentity(game, crc.getValue(), HexFormat.of().formatHex(sha1.digest()));
    }

    /** Hosted sources bind verified normalized content, including all disc tracks, not container bytes. */
    private static RomIdentity ofContentIdentity(String game, String identity) {
        byte[] bytes = ("route-source/v1\0" + identity).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var crc = new CRC32(); crc.update(bytes);
        try { return new RomIdentity(game, crc.getValue(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public boolean matches(RomIdentity other) {
        return game.equals(other.game) && crc32 == other.crc32 && sha1.equals(other.sha1);
    }

    public String crc32Hex() {
        return String.format(Locale.ROOT, "%08X", crc32);
    }
}
