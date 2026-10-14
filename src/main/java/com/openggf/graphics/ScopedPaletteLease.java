package com.openggf.graphics;

/** Private graphics residency metadata; no supported Mod API surface or captured gameplay values. */
record ScopedPaletteLease(Object token,int base,long generation) { }
