package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;

import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Immutable finite original-USA PPZ2 Present non-player current-pattern value.
 * Source pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Palmtree Panic/Graphics Lists
 * (Act 2 Present).asm, all35 authentic PLC art outputs/36 distinct destination spans;
 * LoadShieldArt (Powerup/Main.asm:157-173) adds exactly36 tiles at A880..AD00;
 * shared Main.asm:599-614 and HUD and Points/Main.asm:197-716 select the direct writers.
 * Exact sorted union: eight disjoint spans,1633 tiles/52256 bytes. Complete output
 * lengths determine destinations; compressed blobs and reader lookahead are not output.
 * Sonic F000..F300 stays in its existing player-art owner. Unknown construction never
 * establishes pixels. This facade shares the existing package-private value kernel;
 * no art identity/bank, decoder, preload, source lease, clock, capture or World is added.
 */
public final class CdR12aCameraArtResidency {
    private static final int TILE_BYTES = 32;
    private CdR12aCameraArtResidency() { }

    /** Physical disjoint regions, not source identities; end addresses are exclusive. */
    public enum Region {
        AT_0000(0x0000, 0x6560),
        AT_6600(0x6600, 0x0200),
        AT_69C0(0x69C0, 0x3820),
        AT_A400(0xA400, 0x0900),
        AT_AD00(0xAD00, 0x07E0),
        AT_B500(0xB500, 0x0A40),
        AT_D000(0xD000, 0x0FC0),
        AT_F5C0(0xF5C0, 0x01C0);

        private final int destinationWord;
        private final int byteLength;
        Region(int destinationWord, int byteLength) {
            this.destinationWord = destinationWord; this.byteLength = byteLength;
        }
        public int destinationWord() { return destinationWord; }
        public int byteLength() { return byteLength; }
        public int tileCount() { return byteLength / TILE_BYTES; }
    }

    private static final CdPatternResidency.Profile PROFILE = new CdPatternResidency.Profile(
            Arrays.stream(Region.values()).map(r -> new CdPatternResidency.Span(
                    r.destinationWord, r.byteLength)).toArray(CdPatternResidency.Span[]::new));

    public enum Boundary { UNKNOWN_PATTERN, OUTSIDE_OWNED_WINDOW }

    public static final class OwnershipBoundary extends UnsupportedOperationException {
        private final Boundary boundary;
        private final int destinationWord;
        private OwnershipBoundary(Boundary boundary, int destinationWord) {
            super(boundary + " at native pattern $" + Integer.toHexString(destinationWord >>> 5)
                    + " (VRAM $" + Integer.toHexString(destinationWord) + ")");
            this.boundary = boundary; this.destinationWord = destinationWord;
        }
        public Boundary boundary() { return boundary; }
        public int nativePatternId() { return destinationWord >>> 5; }
        public int destinationWord() { return destinationWord; }
    }

    public static final class State {
        private final CdPatternResidency.Value value;
        private final RegionSnapshot[] regions;
        private State(CdPatternResidency.Value value, RegionSnapshot[] previous) {
            this.value = value;
            regions = new RegionSnapshot[Region.values().length];
            for (Region r : Region.values()) {
                int i = r.ordinal();
                regions[i] = previous != null && previous[i].window == value.window(i)
                        ? previous[i] : new RegionSnapshot(r, value.window(i));
            }
        }
        public RegionSnapshot region(Region region) {
            return regions[Objects.requireNonNull(region, "region").ordinal()];
        }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof State s && Arrays.equals(regions, s.regions);
        }
        @Override public int hashCode() { return Arrays.hashCode(regions); }
    }

    /** Read-only projection sharing the single immutable backing, with no restore/write operation. */
    public static final class RegionSnapshot {
        private final Region region;
        private final CdPatternResidency.Window window;
        private RegionSnapshot(Region region, CdPatternResidency.Window window) {
            this.region = region; this.window = window;
        }
        public Region region() { return region; }
        public CdAssetBytes knownMask() { return window.knownMask(); }
        public OptionalInt knownByte(int byteOffset) { return window.knownByte(byteOffset); }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof RegionSnapshot s
                    && region == s.region && window.equals(s.window);
        }
        @Override public int hashCode() { return window.hashWithTag(region); }
    }

    /** All owned bytes are UNKNOWN; internal canonical zero storage is never a pixel/default. */
    public static State unknown() { return new State(CdPatternResidency.unknown(PROFILE), null); }

    /**
     * Pure native-clear VALUE operation over this finite profile only. Root's actual
     * InitVDP publisher must establish and adopt the real clear; calling this rule is
     * not evidence of execution. Player art, plane values and gaps are not touched.
     */
    public static State clear(State before) {
        Objects.requireNonNull(before, "state");
        return new State(CdPatternResidency.clear(before.value), before.regions);
    }

    /** One complete32-byte commit; every other current value, mask and snapshot identity is retained. */
    public static State withTile(State state, int destinationWord, CdAssetBytes tile32) {
        Objects.requireNonNull(state, "state");
        try {
            return new State(CdPatternResidency.writeTile(state.value, destinationWord, tile32), state.regions);
        } catch (CdPatternResidency.Fault fault) {
            throw new OwnershipBoundary(Boundary.valueOf(fault.boundary().name()), fault.destinationWord());
        }
    }

    /** Final11-bit native pattern ID only, with attributes already removed by the presentation owner. */
    public static CdAssetBytes readTile(State state, int nativePatternId) {
        Objects.requireNonNull(state, "state");
        try {
            return CdPatternResidency.readTile(state.value, nativePatternId);
        } catch (CdPatternResidency.Fault fault) {
            throw new OwnershipBoundary(Boundary.valueOf(fault.boundary().name()), fault.destinationWord());
        }
    }
}
