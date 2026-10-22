package com.openggf.level.objects;

/**
 * Internal, verified read/write projection of the WORD at physical SST offset $2C.
 * Implementations own the actual field encoding, including any overlapping aliases.
 * Real birth must import retained memory or execute a source-backed write before
 * publication; a Java field initializer is not evidence of cleared native memory.
 */
public interface NativeSstWord2CSource {
    int nativeWord2C();
    void writeNativeWord2C(int value);
}
