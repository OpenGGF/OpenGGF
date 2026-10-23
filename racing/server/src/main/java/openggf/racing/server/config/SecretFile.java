package openggf.racing.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Bounded, fail-closed loading for operator-mounted authentication secrets. */
public final class SecretFile {
    private static final int MAX_BYTES = 16 * 1024;

    private SecretFile() { }

    public static String read(Path path) throws IOException {
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) {
            throw new IOException("secret file exceeds " + MAX_BYTES + " bytes: " + path);
        }
        String token;
        try {
            token = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes))
                    .toString().strip();
        } catch (CharacterCodingException malformed) {
            throw new IOException("secret file must contain UTF-8 text: " + path);
        }
        try {
            return requireToken(token);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("secret file must contain a nonempty printable ASCII token: " + path);
        }
    }

    /** Reject invalid HTTP header values before JDK exceptions can echo the secret. */
    public static String requireToken(String token) {
        if (token == null || token.isEmpty() || token.length() > MAX_BYTES) {
            throw new IllegalArgumentException("authentication token must contain 1 to "
                    + MAX_BYTES + " printable non-space ASCII characters");
        }
        for (int i = 0; i < token.length(); i++) {
            char value = token.charAt(i);
            if (value < 0x21 || value > 0x7e) {
                throw new IllegalArgumentException(
                        "authentication token must contain only printable non-space ASCII characters");
            }
        }
        return token;
    }
}
