package starpost.ruins;

import com.openggf.mods.scene.SceneLevelKit;

/** A Sonic 1 level kit as a {@link Kit}, with each block's collision read once and kept. */
public final class RomKit implements Kit {
    public final SceneLevelKit kit;
    private final byte[][] solidity;
    private final java.util.BitSet[] opaque;

    public RomKit(SceneLevelKit kit) {
        this.kit = kit;
        this.solidity = new byte[Math.max(1, kit.blockCount())][];
        this.opaque = new java.util.BitSet[solidity.length];
    }

    @Override
    public int size() {
        return kit.blockSize();
    }

    @Override
    public int columns() {
        return kit.columns();
    }

    @Override
    public int rows() {
        return kit.rows();
    }

    @Override
    public int block(int column, int row) {
        return kit.block(column, row);
    }

    @Override
    public int blockCount() {
        return kit.blockCount();
    }

    @Override
    public byte[] solidity(int block) {
        if (block <= 0 || block >= solidity.length) {
            return null;
        }
        if (solidity[block] == null) {
            solidity[block] = kit.blockSolidity(block);
        }
        return solidity[block];
    }

    @Override
    public int[] area() {
        return kit.playableArea();
    }

    @Override
    public boolean opaque(int block, int x, int y) {
        if (block <= 0 || block >= opaque.length) {
            return false;
        }
        if (opaque[block] == null) {
            int[] px = kit.blockImage(block).pixels();
            java.util.BitSet bits = new java.util.BitSet(px.length);
            for (int i = 0; i < px.length; i++) {
                if (px[i] >>> 24 != 0) {
                    bits.set(i);
                }
            }
            opaque[block] = bits;
        }
        int size = kit.blockSize();
        return opaque[block].get(y * size + x);
    }
}
