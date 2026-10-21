package paradise.net;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

/** Fixed-size, one-use socket challenge. Authenticates the guest invitation, not the plaintext transport. */
final class GolfInvite {
    static final int TIMEOUT_MILLIS = 2000;
    private static final int MAGIC = 0x50474931; // PGI1, independent admission version
    private static final String DOMAIN = "OpenGGF/Putt/guest/admission/v1";
    private GolfInvite() { }
    static byte[] secret() { byte[] value = new byte[16]; new SecureRandom().nextBytes(value); return value; }
    static String code(byte[] secret) { return HexFormat.of().formatHex(secret); }
    static byte[] parse(String code) {
        if (!code.matches("[0-9a-fA-F]{32}")) throw new IllegalArgumentException("invite must be 32 hexadecimal digits");
        return HexFormat.of().parseHex(code);
    }
    static byte[] proof(byte[] secret, byte[] nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update(DOMAIN.getBytes(StandardCharsets.US_ASCII)); mac.update((byte)GolfCodec.SCHEMA); return mac.doFinal(nonce);
        } catch (GeneralSecurityException impossible) { throw new IllegalStateException("HMAC-SHA-256 unavailable", impossible); }
    }
    static boolean host(Socket socket, byte[] secret) throws IOException {
        socket.setSoTimeout(TIMEOUT_MILLIS);
        long deadline = System.nanoTime() + TIMEOUT_MILLIS * 1_000_000L;
        byte[] nonce = new byte[32]; new SecureRandom().nextBytes(nonce);
        var out = new DataOutputStream(socket.getOutputStream());
        out.writeInt(MAGIC); out.writeByte(GolfCodec.SCHEMA); out.write(nonce); out.flush();
        byte[] supplied = read(socket, 32, deadline);
        // This socket consumes exactly one proof. No retry/nonce reuse, including failed proofs.
        boolean valid = MessageDigest.isEqual(proof(secret, nonce), supplied);
        if (valid) { out.writeByte(1); out.flush(); socket.setSoTimeout(0); }
        return valid;
    }
    static void join(Socket socket, byte[] secret) throws IOException {
        socket.setSoTimeout(TIMEOUT_MILLIS);
        long deadline = System.nanoTime() + TIMEOUT_MILLIS * 1_000_000L;
        var in = new DataInputStream(new ByteArrayInputStream(read(socket, 37, deadline)));
        if (in.readInt() != MAGIC || in.readUnsignedByte() != GolfCodec.SCHEMA) throw new IOException("invalid invite challenge");
        byte[] nonce = new byte[32]; in.readFully(nonce);
        socket.getOutputStream().write(proof(secret, nonce)); socket.getOutputStream().flush();
        if (read(socket, 1, deadline)[0] != 1) throw new IOException("invite refused");
        socket.setSoTimeout(0);
    }
    private static byte[] read(Socket socket, int length, long deadline) throws IOException {
        byte[] value = new byte[length]; int offset = 0;
        while (offset < length) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new java.net.SocketTimeoutException("invite deadline");
            socket.setSoTimeout((int)Math.max(1, (remaining + 999_999L) / 1_000_000L));
            int read = socket.getInputStream().read(value, offset, length - offset);
            if (read < 0) throw new EOFException("incomplete invite proof");
            offset += read;
        }
        return value;
    }
}
