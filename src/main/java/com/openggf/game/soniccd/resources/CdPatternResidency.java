package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;

import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Package-private immutable pattern-value format shared by strict stage facades.
 * A fixed profile owns only its named sparse intervals; this is not an address bus.
 * Values/masks confer no source, native-clear, service, clock or capture authority.
 */
final class CdPatternResidency {
    private static final int TILE_BYTES = 32;
    private CdPatternResidency() { }

    record Span(int destinationWord, int byteLength) {
        Span {
            if ((destinationWord & ~0xFFFF) != 0 || (destinationWord & 31) != 0
                    || byteLength <= 0 || byteLength > 0x10000 || (byteLength & 31) != 0
                    || destinationWord > 0x10000 - byteLength) {
                throw new IllegalArgumentException("Expected a finite aligned pattern span");
            }
        }
    }

    static final class Profile {
        private final Span[] spans;
        Profile(Span... spans) {
            Objects.requireNonNull(spans, "spans");
            if (spans.length == 0 || spans.length > 2048) {
                throw new IllegalArgumentException("Expected finite nonempty pattern spans");
            }
            this.spans = spans.clone();
            for (int i = 0; i < this.spans.length; i++) {
                Span a = Objects.requireNonNull(this.spans[i], "span");
                for (int j = 0; j < i; j++) {
                    Span b = this.spans[j];
                    if (a.destinationWord() < b.destinationWord() + b.byteLength()
                            && b.destinationWord() < a.destinationWord() + a.byteLength()) {
                        throw new IllegalArgumentException("Pattern spans must be disjoint");
                    }
                }
            }
        }
        int size() { return spans.length; }
        Span span(int index) { return spans[Objects.checkIndex(index, spans.length)]; }
        int locate(int destinationWord) {
            for (int i = 0; i < spans.length; i++) {
                Span s = spans[i];
                if (destinationWord >= s.destinationWord()
                        && destinationWord < s.destinationWord() + s.byteLength()) return i;
            }
            throw new Fault(Boundary.OUTSIDE_OWNED_WINDOW, destinationWord);
        }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof Profile p && Arrays.equals(spans, p.spans);
        }
        @Override public int hashCode() { return Arrays.hashCode(spans); }
    }

    /** One immutable backing allocation pair, shared by facade snapshots and aggregate values. */
    static final class Window {
        private final byte[] values;
        private final byte[] known;
        Window(int length, byte[] values, byte[] known) {
            if (values.length != length || known.length != length) {
                throw new IllegalArgumentException("Complete owned region and byte-known mask required");
            }
            this.values = values.clone();
            this.known = known.clone();
            for (int i = 0; i < this.known.length; i++) {
                if (this.known[i] == 0) this.values[i] = 0;
                else if (this.known[i] != 1) {
                    throw new IllegalArgumentException("Byte-known mask entries must be zero or one");
                }
            }
        }
        int length() { return values.length; }
        CdAssetBytes knownMask() { return CdAssetBytes.copyOf(known); }
        OptionalInt knownByte(int offset) {
            Objects.checkIndex(offset, values.length);
            return known[offset] == 0 ? OptionalInt.empty()
                    : OptionalInt.of(Byte.toUnsignedInt(values[offset]));
        }
        private Window withTile(int offset, byte[] tile) {
            byte[] updated = values.clone();
            byte[] flags = known.clone();
            System.arraycopy(tile, 0, updated, offset, TILE_BYTES);
            Arrays.fill(flags, offset, offset + TILE_BYTES, (byte) 1);
            return new Window(values.length, updated, flags);
        }
        private CdAssetBytes tile(int offset, int destinationWord) {
            for (int i = offset; i < offset + TILE_BYTES; i++) {
                if (known[i] == 0) throw new Fault(Boundary.UNKNOWN_PATTERN, destinationWord);
            }
            return CdAssetBytes.copyOf(Arrays.copyOfRange(values, offset, offset + TILE_BYTES));
        }
        int hashWithTag(Object tag) {
            int hash = 31 * tag.hashCode() + Arrays.hashCode(values);
            return 31 * hash + Arrays.hashCode(known);
        }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof Window w
                    && Arrays.equals(values, w.values) && Arrays.equals(known, w.known);
        }
        @Override public int hashCode() {
            return 31 * Arrays.hashCode(values) + Arrays.hashCode(known);
        }
    }

    /** Internal value, not a registered capture/lifecycle owner. Facades retain this backing only. */
    static final class Value {
        private final Profile profile;
        private final Window[] windows;
        Value(Profile profile, Window[] windows) {
            this.profile = Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(windows, "windows");
            if (windows.length != profile.size()) throw new IllegalArgumentException("Complete profile windows required");
            this.windows = windows.clone();
            for (int i = 0; i < windows.length; i++) {
                Window w = Objects.requireNonNull(this.windows[i], "window");
                if (w.length() != profile.span(i).byteLength()) {
                    throw new IllegalArgumentException("Window extent differs from fixed profile");
                }
            }
        }
        Window window(int index) { return windows[Objects.checkIndex(index, windows.length)]; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof Value v
                    && profile.equals(v.profile) && Arrays.equals(windows, v.windows);
        }
        @Override public int hashCode() { return 31 * profile.hashCode() + Arrays.hashCode(windows); }
    }

    enum Boundary { UNKNOWN_PATTERN, OUTSIDE_OWNED_WINDOW }
    static final class Fault extends UnsupportedOperationException {
        private final Boundary boundary;
        private final int destinationWord;
        private Fault(Boundary boundary, int destinationWord) {
            super(boundary + " at native pattern $" + Integer.toHexString(destinationWord >>> 5)
                    + " (VRAM $" + Integer.toHexString(destinationWord) + ")");
            this.boundary = boundary; this.destinationWord = destinationWord;
        }
        Boundary boundary() { return boundary; }
        int destinationWord() { return destinationWord; }
    }

    static Value unknown(Profile profile) {
        Objects.requireNonNull(profile, "profile");
        Window[] windows = new Window[profile.size()];
        for (int i = 0; i < windows.length; i++) {
            int length = profile.span(i).byteLength();
            windows[i] = new Window(length, new byte[length], new byte[length]);
        }
        return new Value(profile, windows);
    }

    /** Pure explicit transform only: the caller must establish the actual native clear. */
    static Value clear(Value before) {
        Objects.requireNonNull(before, "state");
        Window[] windows = new Window[before.profile.size()];
        for (int i = 0; i < windows.length; i++) {
            int length = before.profile.span(i).byteLength();
            byte[] known = new byte[length]; Arrays.fill(known, (byte) 1);
            windows[i] = new Window(length, new byte[length], known);
        }
        return new Value(before.profile, windows);
    }

    static Value writeTile(Value before, int destinationWord, CdAssetBytes tile32) {
        Objects.requireNonNull(before, "state");
        requireDestinationWord(destinationWord);
        byte[] tile = exactBytes(tile32, TILE_BYTES, "tile32");
        int index = before.profile.locate(destinationWord);
        Span span = before.profile.span(index);
        Window[] windows = before.windows.clone();
        windows[index] = windows[index].withTile(destinationWord - span.destinationWord(), tile);
        return new Value(before.profile, windows);
    }

    static CdAssetBytes readTile(Value state, int nativePatternId) {
        Objects.requireNonNull(state, "state");
        if ((nativePatternId & ~0x7FF) != 0) {
            throw new IllegalArgumentException("Expected a final 11-bit native pattern ID");
        }
        int destinationWord = nativePatternId << 5;
        int index = state.profile.locate(destinationWord);
        return state.window(index).tile(destinationWord - state.profile.span(index).destinationWord(), destinationWord);
    }

    private static void requireDestinationWord(int value) {
        if ((value & ~0xFFFF) != 0 || (value & (TILE_BYTES - 1)) != 0) {
            throw new IllegalArgumentException("Tile destination must be an unsigned word aligned to $20");
        }
    }
    private static byte[] exactBytes(CdAssetBytes bytes, int length, String name) {
        Objects.requireNonNull(bytes, name);
        if (bytes.length() != length) throw new IllegalArgumentException(name + " must contain exactly " + length + " bytes");
        return bytes.copy();
    }
}
