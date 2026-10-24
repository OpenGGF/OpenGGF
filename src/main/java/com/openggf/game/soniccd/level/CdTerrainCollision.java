package com.openggf.game.soniccd.level;

import java.util.Objects;

/**
 * R11A-only native collision placement over immutable terrain, with the querying
 * actor's oSprFlags byte. This selects original words; it does not solve distances,
 * select solidity sides, mutate actors, or use drawing descriptors.
 *
 * Source: scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * GetLevelBlock (Level/Collision Floor.asm:467-666), PP branch only.
 * The supplied terrain's existing provenance is unchanged by this lookup.
 */
public final class CdTerrainCollision {
    // R11A LevelDataIndex binds LevelChunks to original Word RAM $210000.
    // CdR11aAsset.LEVEL_CHUNKS is file +$10000; CdR11aLoadPlan.decodeLevelIndex
    // checks the actual native pointer. Its zero low word matters to all .w ops.
    private static final int LEVEL_CHUNKS_ADDRESS = 0x210000;

    /**
     * Native placement metadata, including the final low word of d1. For a raw
     * zero layout byte, native code skips the local offset and returns the base.
     * effectiveChunkId is the selection before subq.b; flagged edges can be 0/128.
     */
    public record Address(int layoutOffset, int layoutByte, int effectiveChunkId, int chunkByteOffset) {
        /** Native diagnostic address, never a ROM or MMD file offset. */
        public int nativeAddress() { return LEVEL_CHUNKS_ADDRESS | chunkByteOffset; }
    }

    public sealed interface Placement permits Blank, Available, Unavailable {
        Address address();
    }

    /** Computed pointer equals LevelChunks; no original word was dereferenced. */
    public record Blank(Address address) implements Placement {
        public Blank { Objects.requireNonNull(address, "address"); }
    }

    /**
     * Original native word, even if its material or drawing data is unavailable.
     * Placement does not read collision-ID bytes. The native consumer first tests
     * word & $7FF and its selected solidity bit, then resolves the collision ID
     * from the same terrain's bounded map. Adjacent resident bytes remain unknown.
     */
    public record Available(Address address, CdR11aTerrain.BlockReference word) implements Placement {
        public Available {
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(word, "word");
        }
    }

    public enum UnavailableReason {
        /** Native move.w would use an odd address; never read it as a Java byte pair. */
        ODD_NATIVE_WORD_ADDRESS,
        /** No complete word in the supplied chunk bytes; resident tail is unknown. */
        OUTSIDE_SUPPLIED_CHUNKS
    }

    /** No original word is asserted when native addressing lacks usable backing. */
    public record Unavailable(Address address, UnavailableReason reason) implements Placement {
        public Unavailable {
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(reason, "reason");
        }
    }

    private final CdR11aTerrain terrain;

    public CdTerrainCollision(CdR11aTerrain terrain) {
        this.terrain = Objects.requireNonNull(terrain, "terrain");
    }

    /**
     * Coordinates use their low 16 bits, and actorSprFlags is the querying actor's
     * native byte (only bit 6 matters in PP). No state or actor flags are written.
     * The full $800 layout RAM is addressed, including BG aliases and cleared cells.
     */
    public Placement lookup(int x, int y, int actorSprFlags) {
        int layoutByte = terrain.layout().ram().unsignedByte(layoutOffset(x, y));
        Address address = addressOf(x, y, layoutByte, actorSprFlags);

        // FindLevelFloor (Collision Floor.asm:685-688) and FindLevelWall (:874-877)
        // compare d1 itself with LevelChunks, even for nonzero layout chunk 1.
        if (address.nativeAddress() == LEVEL_CHUNKS_ADDRESS) {
            return new Blank(address);
        }
        int offset = address.chunkByteOffset();
        if ((offset & 1) != 0) {
            return new Unavailable(address, UnavailableReason.ODD_NATIVE_WORD_ADDRESS);
        }
        if (offset > terrain.chunks().length() - 2) {
            return new Unavailable(address, UnavailableReason.OUTSIDE_SUPPLIED_CHUNKS);
        }

        var word = new CdR11aTerrain.BlockReference(terrain.chunks().unsignedWord(offset));
        return new Available(address, word);
    }

    private static int layoutOffset(int x, int y) {
        return (((y & 0xFFFF) >>> 1) & 0x780) + (((x & 0xFFFF) >>> 8) & 0x7F);
    }

    /**
     * Pure register-address step used by lookup. Kept separate from dereferencing:
     * CdR11aTerrain's layout admission excludes some native byte edge patterns.
     */
    static Address addressOf(int x, int y, int layoutByte, int actorSprFlags) {
        if ((layoutByte & ~0xFF) != 0) {
            throw new IllegalArgumentException("Not a native layout byte");
        }
        int xWord = x & 0xFFFF;
        int yWord = y & 0xFFFF;
        int layoutOffset = layoutOffset(xWord, yWord);
        if (layoutByte == 0) {
            return new Address(layoutOffset, layoutByte, 0, 0);
        }

        // d1 starts as $00210000. All following operations change only its low
        // word/byte; retain the high word through the final movea.l, without carry.
        int d1 = layoutByte;
        boolean flagged = (layoutByte & 0x80) != 0;
        if (flagged) {
            d1 &= 0x7F; // .LoopChunk (Collision Floor.asm:519-520).
            // PP reaches .NotMMZ2 (:643-650); other zones' writes are not PP policy.
            if ((actorSprFlags & 0x40) != 0) {
                d1 = (d1 + 1) & 0xFFFF; // addq.w, then compare the incremented ID.
                if (d1 == 0x29) { d1 = 0x51; }
            }
        }
        int effectiveChunkId = d1;

        d1 = (d1 & 0xFF00) | ((d1 - 1) & 0xFF); // subq.b, not a word decrement.
        if (!flagged) {
            d1 = (byte) d1 & 0xFFFF; // Ordinary .NotWWZ ext.w (:501-503).
        }
        // Flagged .GetBlock (:654-664) deliberately omits ext.w. In particular,
        // flagged ID zero rotates $00FF to $FE01, not sign-extended $FFFF.
        d1 = ((d1 >>> 7) | (d1 << 9)) & 0xFFFF; // ror.w #7
        int d0 = (yWord + yWord) & 0xFFFF; // add.w d0,d0
        d0 &= 0x1E0;
        d1 = (d1 + d0) & 0xFFFF;
        d0 = (xWord >>> 3) & 0x1E;
        d1 = (d1 + d0) & 0xFFFF;
        return new Address(layoutOffset, layoutByte, effectiveChunkId, d1);
    }
}
