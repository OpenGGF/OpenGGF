package com.openggf.game.soniccd.scene;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.resources.CdObjectPresentation;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.render.SpritePieceRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Immutable geometry/pixel projection of an already selected WARP scene picture.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Time Warp Cutscene/Main.asm:
 * Start:145-154, VInterrupt:249-287, DrawObject:406-481 and DrawTilemapH64:939-954.
 * This helper never executes those routines: scene admission, objects, RNG, clocks, controller,
 * palette selection and SAT production remain with their source owners.
 *
 * <p>The caller owns a reserved101-pattern window in its existing exclusive picture range.
 * Base validation establishes arithmetic bounds only; it allocates no atlas space or residency.
 * Captured pattern versions restore the selected pixels when that same logical range is reused.
 * Frames are screen-relative; the existing renderer adds its supplied camera back at draw time.
 * Disabled display returns no geometry without sampling an unknown initial CRAM value. The caller
 * owns the actual selected backdrop and palette upload after its old-picture FIFO has drained.
 * No R11A terrain, scroll capture, mutable current-art bank or GL handle is retained here.
 */
public final class CdWarpPresentation {
    private static final int TILE_COUNT = 101;
    private static final int WIDTH = 320;
    private static final int HEIGHT = 224;

    private CdWarpPresentation() { }

    /** The selected physical64x32 name table, at zero horizontal scroll and wrapped selected VScroll. */
    public static SpritePresentation.Frame background(CdWarpAssets assets, CdWarpScene.State selected, int logicalBase) {
        check(assets, selected, logicalBase);
        if (!selected.displayEnabled()) return SpritePresentation.Frame.empty();
        var plane = Objects.requireNonNull(assets.backgroundPlane(), "selected WARP plane");
        if (plane.length() != 64 * 32 * 2) throw new IllegalArgumentException("WARP plane requires4096 bytes");
        int selectedScroll = selected.displayedVscrollBgWord();
        if ((selectedScroll & ~0xFFFF) != 0) throw new IllegalArgumentException("Selected WARP VScroll is a WORD");
        int scroll = selectedScroll & 0xFF, firstRow = scroll >>> 3, offset = scroll & 7;
        int rows = (HEIGHT + offset + 7) / 8;
        var picture = new Picture(assets, logicalBase);
        for (int row = 0; row < rows; row++) {
            int sourceRow = (firstRow + row) & 31;
            for (int column = 0; column < WIDTH / 8; column++) {
                int word = plane.unsignedWord((sourceRow * 64 + (column & 63)) * 2);
                picture.tile(word & 0x7FF, (word >>> 13) & 3, (word & 0x800) != 0,
                        (word & 0x1000) != 0, (word & 0x8000) != 0,
                        column * 8, row * 8 - offset, false);
            }
        }
        return picture.finish();
    }

    /**
     * Expand only the completed selected active SAT prefix. Reverse native entry order once into
     * the painter convention consumed by drawNative; never sort, deduplicate or replay the links.
     * X/Y use the native9/10-bit displayed coordinates minus128. No viewport/scanline admission,
     * horizontal-mask protocol, interlace or general VDP emulation is performed by this projection.
     */
    public static SpritePresentation.Frame sprites(CdWarpAssets assets, CdWarpScene.State selected, int logicalBase) {
        check(assets, selected, logicalBase);
        if (!selected.displayEnabled()) return SpritePresentation.Frame.empty();
        int count = selected.displayedSpriteCount();
        if (count < 0 || count > 80) throw new IllegalArgumentException("Selected WARP SAT count must be0..80");
        var sat = Objects.requireNonNull(selected.displayedSprites(), "selected WARP SAT");
        if (sat.length() != 640) throw new IllegalArgumentException("Selected WARP SAT requires640 bytes");
        if (count == 0) return SpritePresentation.Frame.empty();
        var picture = new Picture(assets, logicalBase);
        for (int index = count - 1; index >= 0; index--) {
            int at = index * 8;
            int y = (sat.unsignedWord(at) & 0x3FF) - 128;
            int x = (sat.unsignedWord(at + 6) & 0x1FF) - 128;
            int size = sat.unsignedByte(at + 2), word = sat.unsignedWord(at + 4);
            if (size > 15) throw new IllegalArgumentException("WARP selected sprite size has unowned high bits");
            int width = ((size >>> 2) & 3) + 1, height = (size & 3) + 1;
            boolean priority = (word & 0x8000) != 0;
            var piece = new SpritePieceRenderer.PreparedPiece(x, y, width, height, word & 0x7FF,
                    word & 0x7FF, (word >>> 13) & 3, (word & 0x800) != 0, (word & 0x1000) != 0,
                    priority, false, 0, height);
            SpritePieceRenderer.renderPreparedPiece(piece, (physical, hFlip, vFlip, palette, tileX, tileY) ->
                    picture.tile(physical & 0x7FF, palette, hFlip, vFlip, priority, tileX, tileY, true));
        }
        return picture.finish();
    }

    private static void check(CdWarpAssets assets, CdWarpScene.State selected, int base) {
        Objects.requireNonNull(assets, "finite WARP assets"); Objects.requireNonNull(selected, "selected WARP scene");
        if (base < 0 || (long) base + TILE_COUNT - 1 > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Logical WARP picture window requires101 nonnegative bounded IDs");
    }

    /** Invocation-local builder only; no live producer, callback or resource survives the return. */
    private static final class Picture {
        private final CdAssetBytes art;
        private final int base;
        private final ArrayList<SpritePresentation.Tile> tiles = new ArrayList<>();
        private final HashMap<Integer, SpritePresentation.PatternVersion> versions = new HashMap<>();
        private Picture(CdWarpAssets assets, int base) {
            art = Objects.requireNonNull(assets.art(), "finite WARP pixels"); this.base = base;
            if (art.length() != TILE_COUNT * 32) throw new IllegalArgumentException("WARP art requires3232 genuine bytes");
        }
        private void tile(int nativeId, int palette, boolean hFlip, boolean vFlip, boolean priority,
                          int x, int y, boolean sprite) {
            if (nativeId < 0 || nativeId >= TILE_COUNT)
                throw new IllegalArgumentException("Selected native WARP pattern outside101-source extent: " + nativeId);
            int logical = base + nativeId;
            versions.computeIfAbsent(logical, ignored -> CdObjectPresentation.patternVersion(art.slice(nativeId * 32, 32)));
            tiles.add(new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT, logical, palette,
                    hFlip, vFlip, priority, x, y, 8, 8, sprite, 0, false, 1));
        }
        private SpritePresentation.Frame finish() { return new SpritePresentation.Frame(tiles, List.of(), versions); }
    }
}
