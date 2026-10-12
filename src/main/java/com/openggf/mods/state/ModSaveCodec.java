package com.openggf.mods.state;

/**
 * A creator's text format, with no storage or ownership authority. Decode returns VALID,
 * CORRUPT or INCOMPATIBLE; unexpected programming failures propagate to the caller's
 * normal mod fault boundary. Encode must produce a value this same codec can validate.
 */
@com.openggf.game.ModApi
public interface ModSaveCodec<T> {
    ModSaveLoadResult<T> decode(String text);
    String encode(T value);
}
