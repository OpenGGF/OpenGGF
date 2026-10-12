package com.openggf.mods;

import java.util.List;
import java.util.Optional;

/**
 * Bounded files private to a verified mod owner, shared by its scenes and gameplay modules.
 *
 * <p>Text and binary files share one namespace: names are 1-64 characters of
 * {@code a-z 0-9 . _ -} and may not start with {@code .}; every method throws
 * {@link IllegalArgumentException} for any other name. A text file holds at most
 * {@link #MAX_TEXT_BYTES} of UTF-8 and a binary file at most {@link #MAX_BINARY_BYTES}.
 * Writes replace the whole file atomically, so a crash never leaves half a file.
 */
@com.openggf.game.ModApi
public interface ModStorage {
    /** Largest UTF-8 encoding {@link #write} accepts and {@link #read} returns: 1 MiB. */
    int MAX_TEXT_BYTES = 1 << 20;

    /** Largest byte array {@link #writeBytes} accepts and {@link #readBytes} returns: 4 MiB. */
    int MAX_BINARY_BYTES = 4 << 20;

    /** The file's text, or empty when it does not exist, is over the text cap, or cannot be read. */
    Optional<String> read(String name);

    /** Replaces the file; returns false when it could not be written or the text is over the cap. */
    boolean write(String name, String text);

    /**
     * A fresh copy of the file's bytes, or empty when it does not exist, is over
     * {@link #MAX_BINARY_BYTES}, or cannot be read.
     */
    Optional<byte[]> readBytes(String name);

    /**
     * Atomically replaces the file with {@code bytes}; returns false when it could not be
     * written or {@code bytes} is longer than {@link #MAX_BINARY_BYTES}, leaving any previous
     * file untouched. The array is not retained.
     *
     * @throws NullPointerException when {@code bytes} is null
     */
    boolean writeBytes(String name, byte[] bytes);

    /** Deletes the file; returns true if it existed. */
    boolean delete(String name);

    /** Names of the files that exist, sorted. */
    List<String> list();
}
