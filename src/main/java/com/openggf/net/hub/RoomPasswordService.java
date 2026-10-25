package com.openggf.net.hub;

import com.openggf.net.protocol.RoomPassword;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.function.Function;

/** Process-wide bounded password work, independent of room and broker event loops. */
final class RoomPasswordService {
    static final int ITERATIONS = 600_000;
    static final RoomPasswordService SHARED = new RoomPasswordService(new ThreadPoolExecutor(
            2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8),
            Thread.ofPlatform().daemon().name("room-password-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy()));

    static final class Verifier implements AutoCloseable {
        private final byte[] salt;
        private final byte[] hash;
        private boolean closed;

        Verifier(byte[] salt, byte[] hash) { this.salt = salt; this.hash = hash; }

        synchronized byte[][] copy() {
            if (closed) throw new IllegalStateException("room credential unavailable");
            return new byte[][]{salt.clone(), hash.clone()};
        }

        @Override
        public synchronized void close() {
            closed = true;
            Arrays.fill(salt, (byte)0);
            Arrays.fill(hash, (byte)0);
        }

        @Override
        public String toString() { return "RoomVerifier[redacted]"; }
    }

    private final Executor executor;

    RoomPasswordService(Executor executor) { this.executor = executor; }

    CompletableFuture<Verifier> prepare(RoomPassword password) {
        return submit(password, characters -> {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            return new Verifier(salt, derive(characters, salt));
        });
    }

    CompletableFuture<Boolean> verify(RoomPassword password, Verifier verifier) {
        byte[][] expected = verifier.copy();
        CompletableFuture<Boolean> result = submit(password, characters -> {
            byte[] actual = derive(characters, expected[0]);
            try { return MessageDigest.isEqual(expected[1], actual); }
            finally { Arrays.fill(actual, (byte)0); }
        });
        result.whenComplete((value, failure) -> {
            Arrays.fill(expected[0], (byte)0);
            Arrays.fill(expected[1], (byte)0);
        });
        return result;
    }

    private <T> CompletableFuture<T> submit(RoomPassword password, Function<char[], T> work) {
        char[] characters = password.copyCharacters();
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try { result.complete(work.apply(characters)); }
                catch (RuntimeException failure) {
                    result.completeExceptionally(new IllegalStateException("room credential work failed"));
                } finally { Arrays.fill(characters, '\0'); }
            });
        } catch (RejectedExecutionException overloaded) {
            Arrays.fill(characters, '\0');
            result.completeExceptionally(new IllegalStateException("room credential service busy"));
        }
        return result;
    }

    private static byte[] derive(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("room credential service unavailable");
        } finally { spec.clearPassword(); }
    }
}
