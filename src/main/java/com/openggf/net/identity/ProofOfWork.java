package com.openggf.net.identity;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Hashcash-style SHA-256 proof of work over payload followed by a LE64 nonce. */
public final class ProofOfWork {
    private ProofOfWork() {
    }

    public static boolean meetsDifficulty(byte[] sha256, int difficultyBits) {
        if (sha256 == null || difficultyBits < 0 || difficultyBits > sha256.length * 8) {
            return false;
        }
        int fullBytes = difficultyBits / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (sha256[i] != 0) {
                return false;
            }
        }
        int remainingBits = difficultyBits % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainingBits);
        return (sha256[fullBytes] & mask) == 0;
    }

    public static long solve(byte[] payload, int difficultyBits) {
        return solve(payload, difficultyBits, Long.MAX_VALUE);
    }

    /** Bounded client work; cancellation is checked inside the actual hashing loop. */
    public static long solve(byte[] payload, int difficultyBits, long deadlineNanos) {
        requireDifficulty(difficultyBits);
        if (payload == null) {
            throw new IllegalArgumentException("proof payload is required");
        }
        MessageDigest digest = sha256();
        byte[] nonceBytes = new byte[Long.BYTES];
        byte[] hash = new byte[32];
        for (long nonce = 0; ; nonce++) {
            if (Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException("proof solve interrupted");
            if (deadlineNanos != Long.MAX_VALUE && System.nanoTime() - deadlineNanos >= 0)
                throw new java.util.concurrent.CancellationException("proof solve deadline exceeded");
            putLittleEndian(nonceBytes, nonce);
            digest.update(payload);
            digest.update(nonceBytes);
            try {
                digest.digest(hash, 0, hash.length);
            } catch (DigestException impossible) {
                throw new IllegalStateException(impossible);
            }
            if (meetsDifficulty(hash, difficultyBits)) {
                return nonce;
            }
            if (nonce == Long.MAX_VALUE) {
                throw new IllegalStateException("proof-of-work nonce space exhausted");
            }
        }
    }

    public static boolean verify(byte[] payload, long nonce, int difficultyBits) {
        if (payload == null || difficultyBits < 0 || difficultyBits > 256) {
            return false;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(payload);
            digest.update(ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN)
                    .putLong(nonce).array());
            return meetsDifficulty(digest.digest(), difficultyBits);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void requireDifficulty(int difficultyBits) {
        if (difficultyBits < 0 || difficultyBits > 256) {
            throw new IllegalArgumentException("difficulty must be between 0 and 256 bits");
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void putLittleEndian(byte[] bytes, long value) {
        for (int index = 0; index < Long.BYTES; index++) {
            bytes[index] = (byte) (value >>> (index * Byte.SIZE));
        }
    }
}
