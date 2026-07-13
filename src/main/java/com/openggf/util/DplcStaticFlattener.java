package com.openggf.util;

import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.render.TileLoadRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Flattens DPLC-driven sprite mapping frames onto a full source pattern array.
 *
 * <p>In the ROM, DPLCs specify which subset of tiles from the full art set to load
 * into VRAM for each animation frame. Mapping tile indices are relative to the
 * DPLC-loaded VRAM position (0-based). Without remapping, the mappings reference
 * the wrong tiles in a full art array.
 *
 * <p>For each mapping frame, the corresponding DPLC frame lists tile load requests.
 * These requests define a contiguous VRAM layout:
 * <pre>
 *   VRAM slot 0..N-1  = request[0].startTile .. startTile+count-1
 *   VRAM slot N..N+M-1 = request[1].startTile .. startTile+count-1
 *   ...
 * </pre>
 * The mapping's tileIndex is an index into this virtual VRAM, which is remapped
 * to the actual source tile index in the full art array.
 */
public final class DplcStaticFlattener {

    private static final Logger LOG = Logger.getLogger(DplcStaticFlattener.class.getName());

    private DplcStaticFlattener() {}

    /**
     * Remaps mapping tile indices through DPLC data.
     *
     * @param mappings   original mapping frames with VRAM-relative tile indices
     * @param dplcFrames DPLC frames (one per mapping frame), or null/empty to skip
     * @return remapped mapping frames with absolute tile indices into the art array
     */
    public static List<SpriteMappingFrame> applyDplcRemap(
            List<SpriteMappingFrame> mappings, List<SpriteDplcFrame> dplcFrames) {
        if (dplcFrames == null || dplcFrames.isEmpty()) {
            LOG.warning("No DPLC frames available for remapping — tile indices may be wrong");
            return mappings;
        }

        List<SpriteMappingFrame> remapped = new ArrayList<>(mappings.size());
        for (int i = 0; i < mappings.size(); i++) {
            SpriteMappingFrame frame = mappings.get(i);

            if (i >= dplcFrames.size()) {
                // No corresponding DPLC frame — keep original
                remapped.add(frame);
                continue;
            }

            // Build VRAM-slot → source-tile remap table from DPLC requests
            SpriteDplcFrame dplc = dplcFrames.get(i);
            int totalSlots = 0;
            for (TileLoadRequest req : dplc.requests()) {
                totalSlots += req.count();
            }

            int[] vramToSource = new int[totalSlots];
            int slot = 0;
            for (TileLoadRequest req : dplc.requests()) {
                for (int t = 0; t < req.count(); t++) {
                    vramToSource[slot++] = req.startTile() + t;
                }
            }

            // Remap each piece's tileIndex through the DPLC table.
            // Multi-tile pieces (e.g., 4x4 = 16 tiles) use tileIndex + offset for
            // each tile. If the DPLC-remapped tiles are contiguous, we keep the piece
            // as-is. If they span non-contiguous DPLC requests, we must split into
            // individual 1x1 sub-pieces with correct remapped tile indices.
            List<SpriteMappingPiece> remappedPieces = new ArrayList<>(frame.pieces().size());
            for (SpriteMappingPiece piece : frame.pieces()) {
                int tileIdx = piece.tileIndex();
                int wTiles = piece.widthTiles();
                int hTiles = piece.heightTiles();
                int tileCount = wTiles * hTiles;

                if (tileIdx < 0 || tileIdx >= vramToSource.length) {
                    LOG.fine("DPLC remap: frame " + i + " piece tileIndex " + tileIdx +
                            " exceeds VRAM slots (" + vramToSource.length + ")");
                    remappedPieces.add(piece);
                    continue;
                }

                int remappedBase = vramToSource[tileIdx];

                // Check if all tiles in this piece are contiguous after remapping
                boolean contiguous = true;
                for (int t = 1; t < tileCount; t++) {
                    int vramSlot = tileIdx + t;
                    if (vramSlot >= vramToSource.length ||
                            vramToSource[vramSlot] != remappedBase + t) {
                        contiguous = false;
                        break;
                    }
                }

                if (contiguous) {
                    // All tiles map contiguously — just remap the base index
                    remappedPieces.add(new SpriteMappingPiece(
                            piece.xOffset(), piece.yOffset(),
                            wTiles, hTiles,
                            remappedBase, piece.hFlip(), piece.vFlip(),
                            piece.paletteIndex(), piece.priority()));
                } else {
                    // Non-contiguous — split into 1x1 sub-pieces.
                    // VDP uses column-major ordering: tileOffset = tx * heightTiles + ty
                    for (int tx = 0; tx < wTiles; tx++) {
                        for (int ty = 0; ty < hTiles; ty++) {
                            int tileOffset = tx * hTiles + ty;
                            int vramSlot = tileIdx + tileOffset;
                            int remappedTile = (vramSlot < vramToSource.length)
                                    ? vramToSource[vramSlot] : tileIdx + tileOffset;
                            int xOff = piece.xOffset() + tx * 8;
                            int yOff = piece.yOffset() + ty * 8;
                            remappedPieces.add(new SpriteMappingPiece(
                                    xOff, yOff, 1, 1,
                                    remappedTile, piece.hFlip(), piece.vFlip(),
                                    piece.paletteIndex(), piece.priority()));
                        }
                    }
                }
            }
            remapped.add(new SpriteMappingFrame(remappedPieces));
        }

        LOG.fine("Applied DPLC remap to " + remapped.size() + " mapping frames");
        return remapped;
    }
}
