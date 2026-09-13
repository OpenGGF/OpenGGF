package com.openggf.game.patch;

import com.openggf.data.RomIdentity;
import com.openggf.game.ModApi;

/**
 * Logical ROM identity a game or patch can require independently of the
 * physical image that supplies its bytes.
 *
 * <p>The ROM catalogue classifies every user-supplied image by size and
 * header and serves each logical ROM as a bounds-checked window of one image
 * or, for the lock-on address spaces, as a concatenation of windows drawn
 * from several images. No byte is ever synthesised: a logical ROM exists only
 * when user-supplied images contain all of it.
 */
@ModApi
public enum LogicalRom {
    /** Sonic the Hedgehog (512 KiB), standalone or the piggyback half of an S&amp;K + Sonic 1 dump. */
    S1(RomIdentity.S1),
    /** Sonic the Hedgehog 2 (1 MiB), standalone or the piggyback half of an S&amp;K + Sonic 2 dump. */
    S2(RomIdentity.S2),
    /** Sonic the Hedgehog 3 (2 MiB), standalone or the upper half of a Sonic 3 &amp; Knuckles dump. */
    S3(RomIdentity.S3),
    /** Sonic &amp; Knuckles (2 MiB), from a standalone image or the S&amp;K window of any lock-on dump. */
    SK(RomIdentity.SK),
    /** Sonic 3 &amp; Knuckles (4 MiB): a lock-on dump, or {@link #SK} followed by {@link #S3}. */
    S3K(RomIdentity.S3K),
    /** The 256 KiB Knuckles in Sonic 2 chip, present only in an S&amp;K + Sonic 2 dump. */
    KIS2_CHIP(RomIdentity.KIS2_CHIP),
    /** Knuckles in Sonic 2 (3.25 MiB): {@link #SK}, then {@link #S2}, then {@link #KIS2_CHIP}. */
    KIS2(RomIdentity.KIS2);

    private final RomIdentity identity;

    LogicalRom(RomIdentity identity) {
        this.identity = identity;
    }

    /**
     * The data-layer identity the ROM catalogue serves this logical ROM under.
     * Package-private on purpose: the catalogue identity is an engine detail,
     * not part of the Mod API surface.
     */
    RomIdentity identity() {
        return identity;
    }

    /** The logical ROM for a catalogue identity. */
    static LogicalRom of(RomIdentity identity) {
        for (LogicalRom rom : values()) {
            if (rom.identity == identity) {
                return rom;
            }
        }
        throw new IllegalArgumentException("No logical ROM for identity " + identity);
    }
}
