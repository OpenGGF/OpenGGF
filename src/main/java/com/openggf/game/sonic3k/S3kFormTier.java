package com.openggf.game.sonic3k;

/**
 * S3K's character-specific powered-form presentation tier.
 *
 * <p>This is lifecycle state: once transformation starts, the selected tier is
 * retained until reversion rather than being inferred again from mutable
 * emerald progression during rendering or rewind restore.
 */
public enum S3kFormTier {
    NORMAL,
    SUPER,
    HYPER,
    SUPER_TAILS
}
