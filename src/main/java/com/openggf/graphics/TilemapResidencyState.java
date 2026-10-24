package com.openggf.graphics;

/** Internal snapshots for temporary cinematic GPU residency. */
final class TilemapResidencyState {
    private TilemapResidencyState() { }

    record ForegroundResidencyState(TilemapTexture texture, TilemapTexture windowTexture,
            byte[] data, int width, int height, boolean dirty, ForegroundWindow window,
            ForegroundScrollSplit split, int[] uploadedWindow) { }

    record BackgroundResidencyState(TilemapTexture backgroundTexture, byte[] backgroundData, byte[] backgroundColumnUploadData, byte[] backgroundRowUploadData, int backgroundWidthTiles, int backgroundHeightTiles, boolean backgroundDirty, boolean backgroundIncrementalDirty, int backgroundRingBaseXTiles, int backgroundRingBaseYTiles, int backgroundPendingSourceColumn, int backgroundPendingDestinationColumn, int backgroundPendingColumnCount, int backgroundPendingSourceRow, int backgroundPendingDestinationRow, int backgroundPendingRowCount, int backgroundRenderRingBaseXOverride, int backgroundRenderRingBaseYOverride, int backgroundContentGeneration, int backgroundRenderGenerationOverride, float bgVdpWrapHeight) { }

}
