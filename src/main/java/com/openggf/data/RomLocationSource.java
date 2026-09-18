package com.openggf.data;

public enum RomLocationSource {
    /** The per-game configuration key named the file. */
    CONFIGURATION,
    /** A caller supplied the path directly. */
    EXPLICIT_OVERRIDE,
    /** The ROM catalogue chose the file by size, header and hash. */
    CATALOGUE
}
