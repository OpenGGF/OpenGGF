package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;

import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Pure immutable sparse-known current pattern bytes at $6400..$6500 and $6E00..$A400.
 * There is one value at each owned physical address, regardless of which actor
 * or producer previously used it. The sole tile-read input is a final native
 * 11-bit pattern ID; SAT attributes must already have been removed by its owner.
 *
 * <p>Sources at scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * PLC_Cam1_Full (src/Level/Palmtree Panic/Data (Act 1 Present).asm:85-100),
 * PLC_Cam2_Incr (:183-190), and their named Nemesis art headers. Mosqui70 at
 * $7400 contains Pata50 at $7600. Anton22 occupies $8120. Plant28 at $8480
 * and Ramp63 at $8820 surround one tile of unspecified ingress at $8800;
 * Tamabboh59 at $8500 overwrites portions of both and that intervening tile.
 * NemPCD_WriteRowToVDP (src/Level/Functions (General).asm:495-510) writes
 * four bytes per row; DecompPLC_Main (:752-758) completes eight rows per tile.
 * This owner exposes complete-tile commits, not mid-instruction device state.
 * PLC_Cam1_Full (:85-100) places Boulder19 at $6E80; PLC_Cam4_Full
 * (:138-153) and PLC_Cam4_Incr (:209-222) relocate Anton22 to $6E00,
 * overwriting eighteen Boulder tiles while retaining its final $70C0 tile.
 * These are physical aliases in one value, never separate producer banks.
 * PLC_Std (:54-57) owns Spikes8 at $6400; its $8008 header ends at $6500.
 * The intervening $6500..$6E00 gap remains unowned.</p>
 *
 * <p>Supplied bytes carry no asset authentication or cold/title history claim.
 * No decoder, PLC queue, section selection, clock, graphics, object bank, or
 * lifecycle is performed here. The eight disjoint regions own 440 physical
 * tiles, without certifying that their bytes are known. Both legacy factories
 * leave all five added regions UNKNOWN until explicit complete-tile writes.
 * Player $F000, powerup $A880 and Spring $A400 remain outside this half-open
 * envelope. Camera writes outside ownership stop at a typed boundary.
 * The caller retains each returned tile commit before attempting the next one;
 * this class neither batches away a reached boundary nor rolls back a prefix.</p>
 */
public final class CdR11aCameraArtResidency {
    private static final int TILE_BYTES = 32;

    private CdR11aCameraArtResidency() { }

    /**
     * Disjoint physical regions, not producer banks. All ends are exclusive.
     * The three historical names, ordinals and extents stay fixed; complements
     * are appended rather than ordered by address.
     */
    public enum Region {
        AT_7400(0x7400, 0x8C0),
        AT_8120(0x8120, 0x2C0),
        AT_8480(0x8480, 0xB80),
        AT_6E00(0x6E00, 0x600),
        AT_7CC0(0x7CC0, 0x460),
        AT_83E0(0x83E0, 0xA0),
        AT_9000(0x9000, 0x1400),
        AT_6400(0x6400, 0x100);

        private final int destinationWord;
        private final int byteLength;

        Region(int destinationWord, int byteLength) {
            this.destinationWord = destinationWord;
            this.byteLength = byteLength;
        }

        public int destinationWord() { return destinationWord; }
        public int byteLength() { return byteLength; }
        public int tileCount() { return byteLength / TILE_BYTES; }
    }

    public enum Boundary {
        UNKNOWN_PATTERN,
        OUTSIDE_OWNED_WINDOW
    }

    /** A valid native address has no owned or known tile; no substitute is returned. */
    public static final class OwnershipBoundary extends UnsupportedOperationException {
        private final Boundary boundary;
        private final int destinationWord;

        private OwnershipBoundary(Boundary boundary, int destinationWord) {
            super(boundary + " at native pattern $" + Integer.toHexString(destinationWord >>> 5)
                    + " (VRAM $" + Integer.toHexString(destinationWord) + ")");
            this.boundary = boundary;
            this.destinationWord = destinationWord;
        }

        public Boundary boundary() { return boundary; }
        public int nativePatternId() { return destinationWord >>> 5; }
        public int destinationWord() { return destinationWord; }
    }

    /**
     * Complete immutable owned value. An aggregate owner may retain this exact
     * value for capture/restore; no service, upload or other restore effect exists.
     * Equality and hash include bytes and byte-known masks at the fixed windows.
     */
    public static final class State {
        private final RegionSnapshot[] regions;

        private State(RegionSnapshot[] regions) {
            this.regions = regions.clone();
        }

        public RegionSnapshot region(Region region) {
            return regions[Objects.requireNonNull(region, "region").ordinal()];
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof State state && Arrays.equals(regions, state.regions);
        }

        @Override
        public int hashCode() { return Arrays.hashCode(regions); }
    }

    /**
     * Immutable diagnostic projection within State, with no standalone restore
     * operation. Unknown backing bytes are canonical zero internally and cannot
     * be read through this projection or the native tile reader. A mask is one
     * byte per region byte: exactly 0 for unknown, exactly 1 for supplied/written.
     */
    public static final class RegionSnapshot {
        private final Region region;
        private final byte[] values;
        private final byte[] known;

        private RegionSnapshot(Region region, byte[] values, byte[] known) {
            this.region = Objects.requireNonNull(region, "region");
            if (values.length != region.byteLength || known.length != region.byteLength) {
                throw new IllegalArgumentException("Complete owned region and byte-known mask required");
            }
            this.values = values.clone();
            this.known = known.clone();
            for (int i = 0; i < this.known.length; i++) {
                if (this.known[i] == 0) {
                    this.values[i] = 0; // Canonical storage only; never a certified RAM byte.
                } else if (this.known[i] != 1) {
                    throw new IllegalArgumentException("Byte-known mask entries must be zero or one");
                }
            }
        }

        public Region region() { return region; }

        /** Immutable flags only; this does not export unknown RAM values. */
        public CdAssetBytes knownMask() { return CdAssetBytes.copyOf(known); }

        /** Returns an unsigned supplied byte, or no value for unknown ingress. */
        public OptionalInt knownByte(int byteOffset) {
            Objects.checkIndex(byteOffset, region.byteLength);
            return known[byteOffset] == 0 ? OptionalInt.empty()
                    : OptionalInt.of(Byte.toUnsignedInt(values[byteOffset]));
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof RegionSnapshot snapshot
                    && region == snapshot.region && Arrays.equals(values, snapshot.values)
                    && Arrays.equals(known, snapshot.known);
        }

        @Override
        public int hashCode() {
            int hash = 31 * region.hashCode() + Arrays.hashCode(values);
            return 31 * hash + Arrays.hashCode(known);
        }
    }

    /**
     * Explicit DEV source preload, not native cold entry or restart history.
     * Mosqui2240, Plant896 and Ramp2016 must be the caller's current exact bytes.
     * Anton's entire $2C0 window, the $8800 gap and all five added
     * regions remain UNKNOWN. No Boulder or other added art is implicitly seeded.
     */
    public static State devSeed(CdAssetBytes mosqui2240, CdAssetBytes plant896, CdAssetBytes ramp2016) {
        byte[] mosqui = exactBytes(mosqui2240, 2240, "mosqui2240");
        byte[] plant = exactBytes(plant896, 896, "plant896");
        byte[] ramp = exactBytes(ramp2016, 2016, "ramp2016");
        byte[] combined = new byte[0xB80];
        byte[] combinedKnown = new byte[0xB80];
        System.arraycopy(plant, 0, combined, 0, 896);
        System.arraycopy(ramp, 0, combined, 0x3A0, 2016);
        Arrays.fill(combinedKnown, 0, 0x380, (byte) 1);
        Arrays.fill(combinedKnown, 0x3A0, 0xB80, (byte) 1);
        return new State(new RegionSnapshot[]{
                new RegionSnapshot(Region.AT_7400, mosqui, allKnown(0x8C0)),
                new RegionSnapshot(Region.AT_8120, new byte[0x2C0], new byte[0x2C0]),
                new RegionSnapshot(Region.AT_8480, combined, combinedKnown),
                new RegionSnapshot(Region.AT_6E00, new byte[0x600], new byte[0x600]),
                new RegionSnapshot(Region.AT_7CC0, new byte[0x460], new byte[0x460]),
                new RegionSnapshot(Region.AT_83E0, new byte[0xA0], new byte[0xA0]),
                new RegionSnapshot(Region.AT_9000, new byte[0x1400], new byte[0x1400]),
                new RegionSnapshot(Region.AT_6400, new byte[0x100], new byte[0x100])
        });
    }

    /**
     * Explicit known ingress for the three historical windows only. All five
     * complementary regions remain UNKNOWN; this is not a certificate for all
     * 440 physical tiles. Its existence establishes no authenticity or native
     * entry provenance; no default zero/cold/restart factory is supplied.
     */
    public static State knownSeed(CdAssetBytes at7400, CdAssetBytes at8120, CdAssetBytes at8480) {
        byte[] first = exactBytes(at7400, 0x8C0, "at7400");
        byte[] second = exactBytes(at8120, 0x2C0, "at8120");
        byte[] third = exactBytes(at8480, 0xB80, "at8480");
        return new State(new RegionSnapshot[]{
                new RegionSnapshot(Region.AT_7400, first, allKnown(0x8C0)),
                new RegionSnapshot(Region.AT_8120, second, allKnown(0x2C0)),
                new RegionSnapshot(Region.AT_8480, third, allKnown(0xB80)),
                new RegionSnapshot(Region.AT_6E00, new byte[0x600], new byte[0x600]),
                new RegionSnapshot(Region.AT_7CC0, new byte[0x460], new byte[0x460]),
                new RegionSnapshot(Region.AT_83E0, new byte[0xA0], new byte[0xA0]),
                new RegionSnapshot(Region.AT_9000, new byte[0x1400], new byte[0x1400]),
                new RegionSnapshot(Region.AT_6400, new byte[0x100], new byte[0x100])
        });
    }

    /**
     * Commits exactly one decoded tile, marking its bytes known. Every other
     * byte and known flag is retained. The address is an unsigned native word
     * containing a VRAM byte destination, aligned to $20, with no wrapping.
     * A valid but unowned destination throws before returning any new state.
     */
    public static State writeTile(State state, int destinationWord, CdAssetBytes tile32) {
        Objects.requireNonNull(state, "state");
        requireDestinationWord(destinationWord);
        byte[] tile = exactBytes(tile32, TILE_BYTES, "tile32");
        RegionSnapshot previous = ownedRegion(state, destinationWord);
        int offset = destinationWord - previous.region.destinationWord;
        byte[] updated = previous.values.clone();
        byte[] known = previous.known.clone();
        System.arraycopy(tile, 0, updated, offset, TILE_BYTES);
        Arrays.fill(known, offset, offset + TILE_BYTES, (byte) 1);
        RegionSnapshot[] regions = state.regions.clone();
        regions[previous.region.ordinal()] = new RegionSnapshot(previous.region, updated, known);
        return new State(regions);
    }

    /**
     * Resolves only the final 11-bit native pattern ID against this State's
     * actual current bytes. Unknown or outside-owned tiles never become pixels.
     */
    public static CdAssetBytes readTile(State state, int nativePatternId) {
        Objects.requireNonNull(state, "state");
        if ((nativePatternId & ~0x7FF) != 0) {
            throw new IllegalArgumentException("Expected a final 11-bit native pattern ID");
        }
        int destinationWord = nativePatternId << 5;
        RegionSnapshot snapshot = ownedRegion(state, destinationWord);
        int offset = destinationWord - snapshot.region.destinationWord;
        for (int i = offset; i < offset + TILE_BYTES; i++) {
            if (snapshot.known[i] == 0) {
                throw new OwnershipBoundary(Boundary.UNKNOWN_PATTERN, destinationWord);
            }
        }
        return CdAssetBytes.copyOf(Arrays.copyOfRange(snapshot.values, offset, offset + TILE_BYTES));
    }

    private static RegionSnapshot ownedRegion(State state, int destinationWord) {
        for (Region region : Region.values()) {
            if (destinationWord >= region.destinationWord
                    && destinationWord < region.destinationWord + region.byteLength) {
                return state.region(region);
            }
        }
        throw new OwnershipBoundary(Boundary.OUTSIDE_OWNED_WINDOW, destinationWord);
    }

    private static void requireDestinationWord(int value) {
        if ((value & ~0xFFFF) != 0 || (value & (TILE_BYTES - 1)) != 0) {
            throw new IllegalArgumentException("Tile destination must be an unsigned word aligned to $20");
        }
    }

    private static byte[] exactBytes(CdAssetBytes bytes, int length, String name) {
        Objects.requireNonNull(bytes, name);
        if (bytes.length() != length) {
            throw new IllegalArgumentException(name + " must contain exactly " + length + " bytes");
        }
        return bytes.copy();
    }

    private static byte[] allKnown(int length) {
        byte[] known = new byte[length];
        Arrays.fill(known, (byte) 1);
        return known;
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
