package com.openggf.tools.challenge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/** The declared prototype supports the three identity-pinned shipped ROMs only. */
public final class ChallengeRoms {
    private static final Map<String, String> SHA1 = Map.of("s1", "69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b",
            "s2", "8bca5dcef1af3e00098666fd892dc1c2a76333f9", "s3k",
            "cfbf98c36c776677290a872547ac47c53d2761d6");
    private ChallengeRoms() {}
    public static void validate(String game, Path path) throws IOException {
        if (!SHA1.containsKey(game))
            throw new IllegalArgumentException("Unsupported game");
        if (!Files.isRegularFile(path) || Files.size(path) > 8 * 1024 * 1024)
            throw new IOException("Missing or invalid " + game.toUpperCase() + " ROM: " + path);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            if (!SHA1.get(game).equals(HexFormat.of().formatHex(digest.digest())))
                throw new IOException("Wrong revision for " + game.toUpperCase()
                        + "; use the documented World REV01 / locked-on ROM");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
