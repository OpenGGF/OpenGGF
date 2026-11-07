package com.openggf.game.soniccd.objects.r4;

import java.util.Objects;

/** Private staging DATA for the historical 64-byte object ABI, not a memory bus or live actor.
 * No image, constructor, equal bytes or returned prefix grants authority to publish it.
 * Root must bind source/pool, current original invocation and full State/Environment before adoption.
 * Offset operations below are package-private implementation details of the finite R4 families. */
public final class CdR4MechanismImage {
    private final int wholeIndex;
    private final byte[] bytes;
    CdR4MechanismImage(int wholeIndex, byte[] bytes) {
        if (bytes.length != 64) throw new IllegalArgumentException("complete 64-byte actor required");
        this.wholeIndex = Objects.checkIndex(wholeIndex,128); this.bytes = bytes.clone();
    }
    public int wholeIndex() { return wholeIndex; }
    public int rawAddress() { return 0xFFFFD000 + wholeIndex * 64; }
    /** Adopt a shared helper's complete staged result; grants no live publication capability. */
    public void replaceHelperData(byte[] complete) {
        if (complete.length != 64) throw new IllegalArgumentException("complete helper image required");
        System.arraycopy(complete,0,bytes,0,64);
    }
    public byte[] bytes() { return bytes.clone(); }
    public int byteAt(int at) { return bytes[Objects.checkIndex(at,64)] & 255; }
    public int wordAt(int at) { return (byteAt(at)<<8)|byteAt(at+1); }
    public int longAt(int at) { return (wordAt(at)<<16)|wordAt(at+2); }
    int b(int at) { return byteAt(at); }
    int sb(int at) { return (byte)b(at); }
    int w(int at) { return wordAt(at); }
    int sw(int at) { return (short)w(at); }
    int l(int at) { return longAt(at); }
    void b(int at,int v) { bytes[Objects.checkIndex(at,64)] = (byte)v; }
    void w(int at,int v) { b(at,v>>>8); b(at+1,v); }
    void l(int at,int v) { w(at,v>>>16); w(at+2,v); }
    void ab(int at,int v) { b(at,b(at)+v); }
    void aw(int at,int v) { w(at,w(at)+v); }
    void al(int at,int v) { l(at,l(at)+v); }
    void copyFrom(CdR4MechanismImage other) { System.arraycopy(other.bytes,0,bytes,0,64); }
    void clear() { java.util.Arrays.fill(bytes,(byte)0); }
}
