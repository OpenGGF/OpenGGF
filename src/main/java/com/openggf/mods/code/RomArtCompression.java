package com.openggf.mods.code;

import com.openggf.game.ModApi;

/** Compression of a ROM-resident art block referenced by a {@link RomArtRequest}. */
@ModApi
public enum RomArtCompression {
    NEMESIS,
    KOSINSKI,
    UNCOMPRESSED
}
