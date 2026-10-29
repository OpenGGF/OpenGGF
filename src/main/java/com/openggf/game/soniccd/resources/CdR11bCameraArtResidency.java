package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;

import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Immutable finite PPZ1 Past non-player current-pattern value, not a producer bank.
 * Source pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Palmtree Panic/Data (Act 1 Past).asm
 * PLC_Level/PLC_Std:47-84 and camera/result/signpost lists; Main.asm:LoadLifeIcon:599-614.
 * Exact reviewed complete-source and HUD interval union: thirteen disjoint spans,1542 tiles.
 * Sonic F000..F300 stays in its existing player-art owner. Neither unknown() nor
 * these format descriptors decode, preload, open assets or execute a native clear.
 * Actual entry/source, once-only producers and the existing capture lifetime remain caller-owned.
 */
public final class CdR11bCameraArtResidency {
    private static final int TILE_BYTES = 32;
    private CdR11bCameraArtResidency() { }

    /** Physical disjoint regions, not source identities; end addresses are exclusive. */
    public enum Region {
        AT_0000(0x0000, 0x5D40),
        AT_6400(0x6400, 0x460),
        AT_68C0(0x68C0, 0x260),
        AT_6B40(0x6B40, 0x3620),
        AT_A400(0xA400, 0x480),
        AT_AD00(0xAD00, 0x360),
        AT_B420(0xB420, 0x80),
        AT_B500(0xB500, 0xA40),
        AT_D000(0xD000, 0x960),
        AT_DAE0(0xDAE0, 0x4E0),
        AT_F5C0(0xF5C0, 0x1C0),
        AT_B060(0xB060, 0x3C0),
        AT_B4A0(0xB4A0, 0x40);

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
     * Explicit pure whole-profile zero clear. Caller must prove the actual native InitVDP clear.
     * This method grants no execution/source authority and does not touch player art, planes or gaps.
     */
    public static State clear(State before) {
        Objects.requireNonNull(before, "state");
        return new State(CdPatternResidency.clear(before.value), before.regions);
    }

    /** One complete32-byte commit; every other current value, mask and snapshot identity is retained. */
    public static State writeTile(State state, int destinationWord, CdAssetBytes tile32) {
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
