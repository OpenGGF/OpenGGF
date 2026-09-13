package com.openggf.data;

/**
 * Identity of a logical ROM as the data layer serves it: the unit a game or
 * patch asks the {@link RomImageCatalogue} for, independent of which
 * user-supplied file (or files) contains its bytes.
 *
 * <p>This is the data-layer twin of the Mod API enum
 * {@code com.openggf.game.patch.LogicalRom}, which maps onto it one to one.
 * The catalogue lives below the runtime layers and must not depend on the
 * patch package, so the identity is declared here and the Mod API enum
 * converts to it.
 */
public enum RomIdentity {
    /** Sonic the Hedgehog (512 KiB), standalone or the piggyback half of an S&amp;K + Sonic 1 dump. */
    S1,
    /** Sonic the Hedgehog 2 (1 MiB), standalone or the piggyback half of an S&amp;K + Sonic 2 dump. */
    S2,
    /** Sonic the Hedgehog 3 (2 MiB), standalone or the upper half of a Sonic 3 &amp; Knuckles dump. */
    S3,
    /** Sonic &amp; Knuckles (2 MiB), standalone or the lower half of any lock-on dump. */
    SK,
    /** Sonic 3 &amp; Knuckles (4 MiB): a lock-on dump, or {@link #SK} followed by {@link #S3}. */
    S3K,
    /** The 256 KiB Knuckles in Sonic 2 chip, present only in an S&amp;K + Sonic 2 dump. */
    KIS2_CHIP,
    /** Knuckles in Sonic 2 (3.25 MiB): {@link #SK}, then {@link #S2}, then {@link #KIS2_CHIP}. */
    KIS2
}
